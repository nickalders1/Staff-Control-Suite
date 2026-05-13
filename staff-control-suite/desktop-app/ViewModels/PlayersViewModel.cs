using System.Collections.ObjectModel;
using System.Text.Json;
using System.Windows;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using StaffControlSuite.Models;
using StaffControlSuite.Protocol;

namespace StaffControlSuite.ViewModels;

public partial class PlayersViewModel : ObservableObject
{
    private List<PlayerInfo> _allPlayers = new();

    public ObservableCollection<PlayerInfo> Players { get; } = new();
    public ObservableCollection<string> ServerFilter { get; } = new();

    [ObservableProperty] private bool _isLoading;
    [ObservableProperty] private int _filteredCount;

    private string _searchText = "";
    public string SearchText
    {
        get => _searchText;
        set
        {
            if (SetProperty(ref _searchText, value))
                ApplyFilter();
        }
    }

    private string? _selectedServerFilter;
    public string? SelectedServerFilter
    {
        get => _selectedServerFilter;
        set
        {
            if (SetProperty(ref _selectedServerFilter, value))
                ApplyFilter();
        }
    }

    public PlayersViewModel()
    {
        App.WebSocketService.PlayerUpdateReceived += OnPlayerUpdate;
    }

    private void OnPlayerUpdate(string serverId, List<PlayerInfo> players)
    {
        Application.Current.Dispatcher.InvokeAsync(() =>
        {
            _allPlayers.RemoveAll(p => p.ServerId == serverId);
            _allPlayers.AddRange(players);
            ApplyFilter();
        });
    }

    [RelayCommand]
    public async Task LoadPlayersAsync()
    {
        IsLoading = true;
        try
        {
            var result = await App.WebSocketService.SendRequestAsync(
                MessageTypes.PlayersList, null, App.AuthService.SessionToken);

            await Application.Current.Dispatcher.InvokeAsync(() =>
            {
                _allPlayers.Clear();
                ServerFilter.Clear();
                ServerFilter.Add("All Servers");

                var arr = result.ValueKind == JsonValueKind.Array ? result
                    : result.TryGetProperty("players", out var pa) ? pa : default;

                if (arr.ValueKind == JsonValueKind.Array)
                {
                    var serverIds = new HashSet<string>();
                    foreach (var p in arr.EnumerateArray())
                    {
                        var player = ParsePlayer(p);
                        _allPlayers.Add(player);
                        if (!string.IsNullOrEmpty(player.ServerId))
                            serverIds.Add(player.ServerId);
                    }
                    foreach (var sid in serverIds.OrderBy(s => s))
                        ServerFilter.Add(sid);
                }

                SelectedServerFilter = "All Servers";
                ApplyFilter();
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

    private void ApplyFilter()
    {
        Players.Clear();
        var filtered = _allPlayers.AsEnumerable();

        if (!string.IsNullOrWhiteSpace(SearchText))
            filtered = filtered.Where(p => p.Name.Contains(SearchText, StringComparison.OrdinalIgnoreCase));

        if (SelectedServerFilter != null && SelectedServerFilter != "All Servers")
            filtered = filtered.Where(p => p.ServerId == SelectedServerFilter);

        foreach (var p in filtered.OrderByDescending(p => p.IsOnline).ThenBy(p => p.Name))
            Players.Add(p);

        FilteredCount = Players.Count;
    }

    private static PlayerInfo ParsePlayer(JsonElement p)
    {
        return new PlayerInfo
        {
            Uuid = p.TryGetProperty("uuid", out var u) ? u.GetString() ?? "" : "",
            Name = p.TryGetProperty("name", out var n) ? n.GetString() ?? "" : "",
            ServerId = p.TryGetProperty("serverId", out var sid) ? sid.GetString() ?? "" : "",
            World = p.TryGetProperty("world", out var w) ? w.GetString() ?? "" : "",
            Gamemode = p.TryGetProperty("gamemode", out var gm) ? gm.GetString() ?? "" : "",
            Health = p.TryGetProperty("health", out var h) ? h.GetDouble() : 20.0,
            FoodLevel = p.TryGetProperty("foodLevel", out var fl) ? fl.GetInt32() : 20,
            Ping = p.TryGetProperty("ping", out var ping) ? ping.GetInt32() : 0,
            IsOnline = p.TryGetProperty("isOnline", out var io) && io.GetBoolean(),
            FirstJoined = p.TryGetProperty("firstJoined", out var fj) ? fj.GetInt64() : 0L,
            LastJoined = p.TryGetProperty("lastJoined", out var lj) ? lj.GetInt64() : 0L,
            PlaytimeSeconds = p.TryGetProperty("playtimeSeconds", out var pt) ? pt.GetInt64() : 0L,
        };
    }
}
