using System.Collections.ObjectModel;
using System.Windows;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using MaterialDesignThemes.Wpf;
using StaffControlSuite.Models;
using StaffControlSuite.Protocol;
using StaffControlSuite.Views.Dialogs;

namespace StaffControlSuite.ViewModels;

public partial class PlayersViewModel : ObservableObject
{
    private readonly List<PlayerInfo> _snapshot = new();

    public ObservableCollection<PlayerInfo> Players { get; } = new();
    public ObservableCollection<string> ServerFilter { get; } = new();

    [ObservableProperty] private bool _isLoading;
    [ObservableProperty] private int  _filteredCount;
    [ObservableProperty] private int  _networkTotal;

    public bool CanPunish => App.AuthService.HasPermission("players.punishments.create") || App.AuthService.IsOwner();

    private string _searchText = "";
    public string SearchText
    {
        get => _searchText;
        set { if (SetProperty(ref _searchText, value)) ApplyFilter(); }
    }

    private string? _selectedServerFilter;
    public string? SelectedServerFilter
    {
        get => _selectedServerFilter;
        set { if (SetProperty(ref _selectedServerFilter, value)) ApplyFilter(); }
    }

    public PlayersViewModel()
    {
        App.WebSocketService.PlayerJoinReceived   += OnPlayerJoin;
        App.WebSocketService.PlayerLeftReceived   += OnPlayerLeft;
        App.WebSocketService.PlayerUpdateReceived += OnPlayerUpdate;
    }

    [RelayCommand]
    public async Task LoadPlayersAsync()
    {
        IsLoading = true;
        try
        {
            var result = await App.WebSocketService.SendRequestAsync(
                MessageTypes.PlayersHistory, null, App.AuthService.SessionToken);

            await Application.Current.Dispatcher.InvokeAsync(() =>
            {
                _snapshot.Clear();
                var arr = result.TryGetProperty("players", out var pa) ? pa : default;
                if (arr.ValueKind == System.Text.Json.JsonValueKind.Array)
                    foreach (var p in arr.EnumerateArray())
                        _snapshot.Add(ParsePlayer(p));

                NetworkTotal = _snapshot.Count;
                RebuildServerFilter();
                ApplyFilter();
            });
        }
        catch { }
        finally { IsLoading = false; }
    }

    private void OnPlayerJoin(PlayerInfo player, string? previousServerId)
    {
        Application.Current.Dispatcher.InvokeAsync(() =>
        {
            var existing = _snapshot.FirstOrDefault(p => p.Uuid == player.Uuid);
            if (existing != null)
            {
                existing.IsOnline       = true;
                existing.Name           = player.Name;
                existing.ServerId       = player.ServerId;
                existing.World          = player.World;
                existing.Gamemode       = player.Gamemode;
                existing.Health         = player.Health;
                existing.FoodLevel      = player.FoodLevel;
                existing.Ping           = player.Ping;
                existing.LastJoined     = player.LastJoined;
                existing.PlaytimeSeconds = player.PlaytimeSeconds;
            }
            else
            {
                _snapshot.Add(player);
                NetworkTotal = _snapshot.Count;
            }
            RebuildServerFilter();
            ApplyFilter();
        });
    }

    private void OnPlayerLeft(string uuid, string serverId)
    {
        Application.Current.Dispatcher.InvokeAsync(() =>
        {
            var player = _snapshot.FirstOrDefault(p => p.Uuid == uuid);
            if (player != null)
            {
                player.IsOnline = false;
                player.ServerId = "";
            }
            RebuildServerFilter();
            ApplyFilter();
        });
    }

    private void OnPlayerUpdate(string serverId, List<PlayerInfo> players)
    {
        Application.Current.Dispatcher.InvokeAsync(() =>
        {
            foreach (var updated in players)
            {
                var existing = _snapshot.FirstOrDefault(p => p.Uuid == updated.Uuid);
                if (existing == null) continue;
                existing.IsOnline  = updated.IsOnline;
                existing.ServerId  = updated.ServerId;
                existing.World     = updated.World;
                existing.Gamemode  = updated.Gamemode;
                existing.Health    = updated.Health;
                existing.FoodLevel = updated.FoodLevel;
                existing.Ping      = updated.Ping;
                existing.PlaytimeSeconds = updated.PlaytimeSeconds;
            }
            ApplyFilter();
        });
    }

    private void RebuildServerFilter()
    {
        var previous = SelectedServerFilter;
        var serverIds = _snapshot
            .Where(p => p.IsOnline && !string.IsNullOrEmpty(p.ServerId))
            .Select(p => p.ServerId)
            .Distinct()
            .OrderBy(s => s)
            .ToList();

        ServerFilter.Clear();
        ServerFilter.Add("All Servers");
        foreach (var sid in serverIds) ServerFilter.Add(sid);

        SelectedServerFilter = ServerFilter.Contains(previous ?? "") ? previous : "All Servers";
    }

    private void ApplyFilter()
    {
        Players.Clear();
        var filtered = _snapshot.AsEnumerable();

        if (!string.IsNullOrWhiteSpace(SearchText))
            filtered = filtered.Where(p => p.Name.Contains(SearchText, StringComparison.OrdinalIgnoreCase));

        if (SelectedServerFilter != null && SelectedServerFilter != "All Servers")
            filtered = filtered.Where(p => p.ServerId == SelectedServerFilter);

        foreach (var p in filtered.OrderByDescending(p => p.IsOnline).ThenBy(p => p.Name))
            Players.Add(p);

        FilteredCount = Players.Count;
    }

    [RelayCommand]
    private async Task PunishPlayerAsync(PlayerInfo? player)
    {
        if (player == null) return;
        var dialog = new CreatePunishmentDialog(player.Name);
        await DialogHost.Show(dialog, "RootDialogHost");
    }

    private static PlayerInfo ParsePlayer(System.Text.Json.JsonElement p) => new()
    {
        Uuid            = p.TryGetProperty("uuid",            out var u)   ? u.GetString()   ?? "" : "",
        Name            = p.TryGetProperty("name",            out var n)   ? n.GetString()   ?? "" : "",
        ServerId        = p.TryGetProperty("serverId",        out var sid) ? sid.GetString() ?? "" : "",
        World           = p.TryGetProperty("world",           out var w)   ? w.GetString()   ?? "" : "",
        Gamemode        = p.TryGetProperty("gamemode",        out var gm)  ? gm.GetString()  ?? "" : "",
        Health          = p.TryGetProperty("health",          out var h)   ? h.GetDouble()        : 20.0,
        FoodLevel       = p.TryGetProperty("foodLevel",       out var fl)  ? fl.GetInt32()        : 20,
        Ping            = p.TryGetProperty("ping",            out var pg)  ? pg.GetInt32()        : 0,
        IsOnline        = p.TryGetProperty("isOnline",        out var io)  && io.GetBoolean(),
        FirstJoined     = p.TryGetProperty("firstJoined",     out var fj)  ? fj.GetInt64()        : 0L,
        LastJoined      = p.TryGetProperty("lastJoined",      out var lj)  ? lj.GetInt64()        : 0L,
        PlaytimeSeconds = p.TryGetProperty("playtimeSeconds", out var pt)  ? pt.GetInt64()        : 0L,
    };
}
