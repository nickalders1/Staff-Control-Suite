using System.Collections.ObjectModel;
using System.Text.Json;
using System.Windows;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using StaffControlSuite.Models;
using StaffControlSuite.Protocol;

namespace StaffControlSuite.ViewModels;

public partial class OverviewViewModel : ObservableObject
{
    public ObservableCollection<ServerInfo> Servers { get; } = new();
    public ObservableCollection<AuditLog> RecentActivity { get; } = new();

    [ObservableProperty] private bool _isLoading;
    [ObservableProperty] private int _totalServers;
    [ObservableProperty] private int _onlineServers;
    [ObservableProperty] private int _totalPlayers;
    [ObservableProperty] private double _averageTps;
    [ObservableProperty] private bool _noServers;
    [ObservableProperty] private bool _noRecentActivity;

    public bool CanViewAudit => App.AuthService.HasPermission("audit.view") || App.AuthService.IsOwner();

    public OverviewViewModel()
    {
        App.WebSocketService.ServerStatusChanged += OnServerStatusChanged;
    }

    private void OnServerStatusChanged(string serverId, bool online, int playerCount, double tps, double mspt, long lastHeartbeat)
    {
        Application.Current.Dispatcher.InvokeAsync(() =>
        {
            var server = Servers.FirstOrDefault(s => s.ServerId == serverId);
            if (server != null)
            {
                server.IsOnline = online;
                server.PlayerCount = playerCount;
                server.Tps = tps;
                server.Mspt = mspt;
                server.LastHeartbeat = lastHeartbeat;
                UpdateStats();
            }
        });
    }

    [RelayCommand]
    public async Task LoadAsync()
    {
        IsLoading = true;
        try
        {
            var token = App.AuthService.SessionToken;

            // Load servers
            var serversResult = await App.WebSocketService.SendRequestAsync(
                MessageTypes.ServersList, null, token);

            await Application.Current.Dispatcher.InvokeAsync(() =>
            {
                Servers.Clear();
                if (serversResult.ValueKind == JsonValueKind.Array)
                {
                    foreach (var s in serversResult.EnumerateArray())
                        Servers.Add(ParseServerInfo(s));
                }
                else if (serversResult.TryGetProperty("servers", out var serversArr))
                {
                    foreach (var s in serversArr.EnumerateArray())
                        Servers.Add(ParseServerInfo(s));
                }
                UpdateStats();
                NoServers = Servers.Count == 0;
            });

            // Load audit logs if permitted
            if (CanViewAudit)
            {
                var auditResult = await App.WebSocketService.SendRequestAsync(
                    MessageTypes.AuditList,
                    new { page = 1, pageSize = 5 },
                    token);

                await Application.Current.Dispatcher.InvokeAsync(() =>
                {
                    RecentActivity.Clear();
                    var logsArr = auditResult.ValueKind == JsonValueKind.Array
                        ? auditResult
                        : auditResult.TryGetProperty("logs", out var la) ? la : default;

                    if (logsArr.ValueKind == JsonValueKind.Array)
                    {
                        foreach (var log in logsArr.EnumerateArray())
                            RecentActivity.Add(ParseAuditLog(log));
                    }
                    NoRecentActivity = RecentActivity.Count == 0;
                });
            }
        }
        catch
        {
            // If load fails, show empty state
            NoServers = Servers.Count == 0;
        }
        finally
        {
            IsLoading = false;
        }
    }

    private void UpdateStats()
    {
        TotalServers = Servers.Count;
        OnlineServers = Servers.Count(s => s.IsOnline);
        TotalPlayers = Servers.Where(s => s.IsOnline).Sum(s => s.PlayerCount);
        var onlineWithTps = Servers.Where(s => s.IsOnline && s.Tps > 0).ToList();
        AverageTps = onlineWithTps.Count > 0 ? onlineWithTps.Average(s => s.Tps) : 0;
    }

    private static ServerInfo ParseServerInfo(JsonElement s)
    {
        return new ServerInfo
        {
            ServerId = s.TryGetProperty("serverId", out var sid) ? sid.GetString() ?? "" : "",
            ServerName = s.TryGetProperty("serverName", out var sn) ? sn.GetString() ?? "" : "",
            ServerType = s.TryGetProperty("serverType", out var st) ? st.GetString() ?? "" : "",
            Host = s.TryGetProperty("host", out var h) ? h.GetString() ?? "" : "",
            Port = s.TryGetProperty("port", out var p) ? p.GetInt32() : 0,
            IsOnline = s.TryGetProperty("isOnline", out var io) && io.GetBoolean(),
            PlayerCount = s.TryGetProperty("playerCount", out var pc) ? pc.GetInt32() : 0,
            MaxPlayers = s.TryGetProperty("maxPlayers", out var mp) ? mp.GetInt32() : 0,
            Tps = s.TryGetProperty("tps", out var tps) ? tps.GetDouble() : 0.0,
            Mspt = s.TryGetProperty("mspt", out var mspt) ? mspt.GetDouble() : 0.0,
            LastHeartbeat = s.TryGetProperty("lastHeartbeat", out var lh) ? lh.GetInt64() : 0L,
            MinecraftVersion = s.TryGetProperty("minecraftVersion", out var mv) ? mv.GetString() ?? "" : "",
            PaperVersion = s.TryGetProperty("paperVersion", out var pv) ? pv.GetString() ?? "" : "",
            PluginCount = s.TryGetProperty("pluginCount", out var plc) ? plc.GetInt32() : 0,
            LoadedChunks = s.TryGetProperty("loadedChunks", out var lc) ? lc.GetInt32() : 0,
        };
    }

    private static AuditLog ParseAuditLog(JsonElement log)
    {
        return new AuditLog
        {
            Id = log.TryGetProperty("id", out var id) ? id.GetInt64() : 0,
            UserId = log.TryGetProperty("userId", out var uid) ? uid.GetInt64() : 0,
            Username = log.TryGetProperty("username", out var un) ? un.GetString() ?? "" : "",
            Action = log.TryGetProperty("action", out var a) ? a.GetString() ?? "" : "",
            Target = log.TryGetProperty("target", out var t) ? t.GetString() ?? "" : "",
            Details = log.TryGetProperty("details", out var d) ? d.GetString() ?? "" : "",
            IpAddress = log.TryGetProperty("ipAddress", out var ip) ? ip.GetString() ?? "" : "",
            Timestamp = log.TryGetProperty("timestamp", out var ts) ? ts.GetInt64() : 0L,
        };
    }
}
