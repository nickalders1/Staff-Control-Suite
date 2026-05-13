using System.Collections.ObjectModel;
using System.Text.Json;
using System.Windows;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using MaterialDesignThemes.Wpf;
using StaffControlSuite.Models;
using StaffControlSuite.Protocol;
using StaffControlSuite.Views.Dialogs;

namespace StaffControlSuite.ViewModels;

public partial class ServersViewModel : ObservableObject
{
    public ObservableCollection<ServerInfo> Servers { get; } = new();

    [ObservableProperty] private bool _isLoading;
    [ObservableProperty] private string _errorMessage = "";

    public bool CanManage => App.AuthService.HasPermission("servers.manage") || App.AuthService.IsOwner();

    public ServersViewModel()
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
            }
        });
    }

    [RelayCommand]
    public async Task LoadServersAsync()
    {
        IsLoading = true;
        ErrorMessage = "";
        try
        {
            var result = await App.WebSocketService.SendRequestAsync(
                MessageTypes.ServersList, null, App.AuthService.SessionToken);

            await Application.Current.Dispatcher.InvokeAsync(() =>
            {
                Servers.Clear();
                var arr = result.ValueKind == JsonValueKind.Array ? result
                    : result.TryGetProperty("servers", out var sa) ? sa : default;
                if (arr.ValueKind == JsonValueKind.Array)
                {
                    foreach (var s in arr.EnumerateArray())
                        Servers.Add(ParseServerInfo(s));
                }
            });
        }
        catch (Exception ex)
        {
            ErrorMessage = ex.Message;
        }
        finally
        {
            IsLoading = false;
        }
    }

    [RelayCommand]
    private async Task AddServerAsync()
    {
        var dialog = new ServerEditDialog();
        var result = await DialogHost.Show(dialog, "RootDialogHost");
        if (result is ServerInfo newServer)
        {
            IsLoading = true;
            try
            {
                await App.WebSocketService.SendRequestAsync(
                    MessageTypes.ServersAdd,
                    new
                    {
                        serverId = newServer.ServerId,
                        serverName = newServer.ServerName,
                        serverType = newServer.ServerType,
                        host = newServer.Host,
                        port = newServer.Port
                    },
                    App.AuthService.SessionToken);
                await LoadServersAsync();
            }
            catch (Exception ex)
            {
                ErrorMessage = ex.Message;
            }
            finally
            {
                IsLoading = false;
            }
        }
    }

    [RelayCommand]
    private async Task RemoveServerAsync(string? serverId)
    {
        if (string.IsNullOrEmpty(serverId)) return;

        var confirm = await DialogHost.Show(
            new ConfirmDialog($"Remove server '{serverId}'?", "This action cannot be undone."),
            "RootDialogHost");

        if (confirm is true)
        {
            IsLoading = true;
            try
            {
                await App.WebSocketService.SendRequestAsync(
                    MessageTypes.ServersRemove,
                    new { serverId },
                    App.AuthService.SessionToken);
                await LoadServersAsync();
            }
            catch (Exception ex)
            {
                ErrorMessage = ex.Message;
            }
            finally
            {
                IsLoading = false;
            }
        }
    }

    [RelayCommand]
    private async Task EditServerAsync(ServerInfo? server)
    {
        if (server == null) return;

        var dialog = new ServerEditDialog(server);
        var result = await DialogHost.Show(dialog, "RootDialogHost");
        if (result is ServerInfo updated)
        {
            IsLoading = true;
            try
            {
                await App.WebSocketService.SendRequestAsync(
                    MessageTypes.ServersUpdate,
                    new
                    {
                        serverId = updated.ServerId,
                        serverName = updated.ServerName,
                        serverType = updated.ServerType,
                        host = updated.Host,
                        port = updated.Port
                    },
                    App.AuthService.SessionToken);
                await LoadServersAsync();
            }
            catch (Exception ex)
            {
                ErrorMessage = ex.Message;
            }
            finally
            {
                IsLoading = false;
            }
        }
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
        };
    }
}
