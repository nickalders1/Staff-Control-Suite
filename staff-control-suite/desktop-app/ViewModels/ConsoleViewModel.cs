using System.Collections.ObjectModel;
using System.Text.Json;
using System.Windows;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using StaffControlSuite.Models;
using StaffControlSuite.Protocol;
using StaffControlSuite.Services;

namespace StaffControlSuite.ViewModels;

public partial class ConsoleViewModel : ObservableObject
{
    private readonly ConsoleLogService _logService;

    public ObservableCollection<string> ConsoleLines { get; } = new();
    public ObservableCollection<ServerInfo> AvailableServers { get; } = new();

    [ObservableProperty] private bool _isLoading;
    [ObservableProperty] private string _commandText = "";
    [ObservableProperty] private bool _autoScroll = true;

    private ServerInfo? _selectedServer;
    public ServerInfo? SelectedServer
    {
        get => _selectedServer;
        set
        {
            var old = _selectedServer;
            if (SetProperty(ref _selectedServer, value))
                _ = OnServerSelectedAsync(old, value);
        }
    }

    public bool HasCommandPermission =>
        App.AuthService.HasPermission("console.command") || App.AuthService.IsOwner();

    public ConsoleViewModel()
    {
        _logService = App.ConsoleLogService;
        _logService.LineAdded += OnLineAdded;
    }

    private void OnLineAdded(string serverId, ConsoleEntry entry)
    {
        if (SelectedServer?.ServerId != serverId) return;

        Application.Current.Dispatcher.InvokeAsync(() =>
        {
            ConsoleLines.Add(entry.Formatted);
            while (ConsoleLines.Count > 10_000)
                ConsoleLines.RemoveAt(0);
        });
    }

    public async Task LoadServersAsync()
    {
        IsLoading = true;
        try
        {
            var result = await App.WebSocketService.SendRequestAsync(
                MessageTypes.ServersList, null, App.AuthService.SessionToken);

            await Application.Current.Dispatcher.InvokeAsync(() =>
            {
                AvailableServers.Clear();
                var arr = result.ValueKind == System.Text.Json.JsonValueKind.Array ? result
                    : result.TryGetProperty("servers", out var sa) ? sa : default;
                if (arr.ValueKind == System.Text.Json.JsonValueKind.Array)
                {
                    foreach (var s in arr.EnumerateArray())
                    {
                        AvailableServers.Add(new ServerInfo
                        {
                            ServerId   = s.TryGetProperty("serverId",   out var sid) ? sid.GetString() ?? "" : "",
                            ServerName = s.TryGetProperty("serverName", out var sn)  ? sn.GetString()  ?? "" : "",
                        });
                    }
                }

                // Restore previously selected server if possible, else pick first
                var previousId = _selectedServer?.ServerId;
                var match = previousId != null
                    ? AvailableServers.FirstOrDefault(s => s.ServerId == previousId)
                    : null;

                if (match != null)
                    SelectedServer = match;          // same server — don't clear console
                else if (AvailableServers.Count > 0)
                    SelectedServer = AvailableServers[0];
            });
        }
        catch { }
        finally { IsLoading = false; }
    }

    private async Task OnServerSelectedAsync(ServerInfo? oldServer, ServerInfo? newServer)
    {
        // Unsubscribe old server from live streaming
        if (oldServer != null && (newServer == null || oldServer.ServerId != newServer.ServerId))
        {
            try
            {
                await App.WebSocketService.SendRequestAsync(
                    MessageTypes.ConsoleUnsubscribe,
                    new { serverId = oldServer.ServerId },
                    App.AuthService.SessionToken);
            }
            catch { }
        }

        // Load persisted buffer for new server (history survives page navigation)
        ConsoleLines.Clear();
        if (newServer != null)
        {
            var buffered = _logService.GetBuffer(newServer.ServerId);
            foreach (var entry in buffered)
                ConsoleLines.Add(entry.Formatted);

            try
            {
                await App.WebSocketService.SendRequestAsync(
                    MessageTypes.ConsoleSubscribe,
                    new { serverId = newServer.ServerId },
                    App.AuthService.SessionToken);
            }
            catch (Exception ex)
            {
                Application.Current.Dispatcher.Invoke(() =>
                    ConsoleLines.Add($"[ERROR] Failed to subscribe: {ex.Message}"));
            }
        }
    }

    [RelayCommand]
    private async Task SendCommandAsync()
    {
        if (SelectedServer == null || string.IsNullOrWhiteSpace(CommandText)) return;
        var cmd = CommandText.Trim();
        CommandText = "";
        try
        {
            await App.WebSocketService.SendRequestAsync(
                MessageTypes.ConsoleCommand,
                new { serverId = SelectedServer.ServerId, command = cmd },
                App.AuthService.SessionToken);
        }
        catch (Exception ex)
        {
            Application.Current.Dispatcher.Invoke(() =>
                ConsoleLines.Add($"[ERROR] {ex.Message}"));
        }
    }

    [RelayCommand]
    private void ClearConsole()
    {
        if (SelectedServer != null)
            _logService.ClearBuffer(SelectedServer.ServerId);
        ConsoleLines.Clear();
    }
}
