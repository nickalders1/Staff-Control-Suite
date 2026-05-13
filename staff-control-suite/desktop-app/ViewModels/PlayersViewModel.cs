using System.Collections.ObjectModel;
using System.Windows;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using MaterialDesignThemes.Wpf;
using StaffControlSuite.Models;
using StaffControlSuite.Protocol;
using StaffControlSuite.Services;
using StaffControlSuite.Views.Dialogs;

namespace StaffControlSuite.ViewModels;

public partial class PlayersViewModel : ObservableObject
{
    private readonly PlayerCacheService _playerCache;
    private List<PlayerInfo> _snapshot = new();

    public ObservableCollection<PlayerInfo> Players { get; } = new();
    public ObservableCollection<string> ServerFilter { get; } = new();

    [ObservableProperty] private bool _isLoading;
    [ObservableProperty] private int _filteredCount;
    [ObservableProperty] private int _networkTotal;

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
        _playerCache = App.PlayerCacheService;

        // React to real-time changes by refreshing the view from cache
        App.WebSocketService.PlayerJoinReceived  += (_, _) => RefreshFromCache();
        App.WebSocketService.PlayerLeftReceived  += (_, _) => RefreshFromCache();
        App.WebSocketService.PlayerUpdateReceived += (_, _) => RefreshFromCache();
    }

    [RelayCommand]
    public async Task LoadPlayersAsync()
    {
        IsLoading = true;
        try
        {
            // Request a fresh list from the proxy — the PlayerCacheService will get updated
            // automatically via the event.player.update push event that the proxy sends.
            // We also do an explicit request so the cache is warm on first load.
            var result = await App.WebSocketService.SendRequestAsync(
                MessageTypes.PlayersList, null, App.AuthService.SessionToken);

            await Application.Current.Dispatcher.InvokeAsync(() =>
            {
                _playerCache.Clear();

                var arr = result.TryGetProperty("players", out var pa) ? pa : default;
                if (arr.ValueKind == System.Text.Json.JsonValueKind.Array)
                {
                    foreach (var p in arr.EnumerateArray())
                    {
                        var player = ParsePlayer(p);
                        // Inject directly into cache via join event path
                        App.WebSocketService.InjectPlayerJoin(player);
                    }
                }
            });

            RefreshFromCache();
        }
        catch { }
        finally { IsLoading = false; }
    }

    private void RefreshFromCache()
    {
        Application.Current.Dispatcher.InvokeAsync(() =>
        {
            _snapshot = _playerCache.GetAll().ToList();
            NetworkTotal = _snapshot.Count;

            var serverIds = _snapshot.Select(p => p.ServerId)
                                     .Where(s => !string.IsNullOrEmpty(s))
                                     .Distinct()
                                     .OrderBy(s => s)
                                     .ToList();

            // Rebuild server filter list preserving selection
            var previous = SelectedServerFilter;
            ServerFilter.Clear();
            ServerFilter.Add("All Servers");
            foreach (var sid in serverIds) ServerFilter.Add(sid);

            SelectedServerFilter = ServerFilter.Contains(previous ?? "") ? previous : "All Servers";

            ApplyFilter();
        });
    }

    private void ApplyFilter()
    {
        Players.Clear();
        var filtered = _snapshot.AsEnumerable();

        if (!string.IsNullOrWhiteSpace(SearchText))
            filtered = filtered.Where(p => p.Name.Contains(SearchText, StringComparison.OrdinalIgnoreCase));

        if (SelectedServerFilter != null && SelectedServerFilter != "All Servers")
            filtered = filtered.Where(p => p.ServerId == SelectedServerFilter);

        foreach (var p in filtered.OrderBy(p => p.Name))
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
