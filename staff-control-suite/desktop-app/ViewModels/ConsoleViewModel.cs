using System.Collections.ObjectModel;
using System.Text.Json;
using System.Windows;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using StaffControlSuite.Models;
using StaffControlSuite.Protocol;

namespace StaffControlSuite.ViewModels;

public partial class ConsoleViewModel : ObservableObject
{
    private const int MaxConsoleLines = 1000;

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
            {
                _ = OnServerSelectedAsync(old, value);
            }
        }
    }

    public bool HasCommandPermission =>
        App.AuthService.HasPermission("console.command") || App.AuthService.IsOwner();

    public ConsoleViewModel()
    {
        App.WebSocketService.ConsoleLineReceived += OnConsoleLine;
    }

    private void OnConsoleLine(string serverId, string line, long timestamp)
    {
        Application.Current.Dispatcher.InvokeAsync(() =>
        {
            if (SelectedServer?.ServerId != serverId) return;

            ConsoleLines.Add($"[{DateTimeOffset.FromUnixTimeMilliseconds(timestamp > 0 ? timestamp : DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()).LocalDateTime:HH:mm:ss}] {line}");

            while (ConsoleLines.Count > MaxConsoleLines)
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
                var arr = result.ValueKind == JsonValueKind.Array ? result
                    : result.TryGetProperty("servers", out var sa) ? sa : default;
                if (arr.ValueKind == JsonValueKind.Array)
                {
                    foreach (var s in arr.EnumerateArray())
                    {
                        AvailableServers.Add(new ServerInfo
                        {
                            ServerId = s.TryGetProperty("serverId", out var sid) ? sid.GetString() ?? "" : "",
                            ServerName = s.TryGetProperty("serverName", out var sn) ? sn.GetString() ?? "" : "",
                        });
                    }
                }
                if (AvailableServers.Count > 0)
                    SelectedServer = AvailableServers[0];
            });
        }
        catch
        {
            // Silently handle
        }
        finally
        {
            IsLoading = false;
        }
    }

    private async Task OnServerSelectedAsync(ServerInfo? oldServer, ServerInfo? newServer)
    {
        ConsoleLines.Clear();

        if (oldServer != null)
        {
            try
            {
                await App.WebSocketService.SendRequestAsync(
                    MessageTypes.ConsoleUnsubscribe,
                    new { serverId = oldServer.ServerId },
                    App.AuthService.SessionToken);
            }
            catch { /* ignore */ }
        }

        if (newServer != null)
        {
            try
            {
                await App.WebSocketService.SendRequestAsync(
                    MessageTypes.ConsoleSubscribe,
                    new { serverId = newServer.ServerId },
                    App.AuthService.SessionToken);
            }
            catch (Exception ex)
            {
                ConsoleLines.Add($"[ERROR] Failed to subscribe: {ex.Message}");
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
            Application.Current.Dispatcher.InvokeAsync(() =>
            {
                ConsoleLines.Add($"[ERROR] {ex.Message}");
            });
        }
    }

    [RelayCommand]
    private void ClearConsole()
    {
        ConsoleLines.Clear();
    }
}
