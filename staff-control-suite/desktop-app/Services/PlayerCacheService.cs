using System.Collections.ObjectModel;
using System.Windows;
using StaffControlSuite.Models;

namespace StaffControlSuite.Services;

public sealed class PlayerCacheService
{
    private readonly Dictionary<string, PlayerInfo> _cache = new();
    private readonly object _lock = new();

    public ObservableCollection<PlayerInfo> OnlinePlayers { get; } = new();

    public PlayerCacheService(WebSocketService ws)
    {
        ws.PlayerJoinReceived   += OnPlayerJoin;
        ws.PlayerLeftReceived   += OnPlayerLeft;
        ws.PlayerUpdateReceived += OnPlayerUpdate;
    }

    private void OnPlayerJoin(PlayerInfo player, string? previousServer)
    {
        lock (_lock)
        {
            _cache[player.Uuid] = player;
        }

        Application.Current.Dispatcher.InvokeAsync(() =>
        {
            var existing = OnlinePlayers.FirstOrDefault(p => p.Uuid == player.Uuid);
            if (existing != null)
            {
                var idx = OnlinePlayers.IndexOf(existing);
                OnlinePlayers[idx] = player;
            }
            else
            {
                OnlinePlayers.Add(player);
            }
        });
    }

    private void OnPlayerLeft(string uuid, string serverId)
    {
        lock (_lock)
        {
            _cache.Remove(uuid);
        }

        Application.Current.Dispatcher.InvokeAsync(() =>
        {
            var existing = OnlinePlayers.FirstOrDefault(p => p.Uuid == uuid);
            if (existing != null)
                OnlinePlayers.Remove(existing);
        });
    }

    private void OnPlayerUpdate(string serverId, List<PlayerInfo> players)
    {
        lock (_lock)
        {
            foreach (var p in players)
                _cache[p.Uuid] = p;
        }

        Application.Current.Dispatcher.InvokeAsync(() =>
        {
            foreach (var updated in players)
            {
                var existing = OnlinePlayers.FirstOrDefault(p => p.Uuid == updated.Uuid);
                if (existing != null)
                {
                    var idx = OnlinePlayers.IndexOf(existing);
                    OnlinePlayers[idx] = updated;
                }
                else
                {
                    OnlinePlayers.Add(updated);
                }
            }

            // Remove players reported offline for this server that aren't in the update
            var updatedUuids = players.Select(p => p.Uuid).ToHashSet();
            var toRemove = OnlinePlayers
                .Where(p => p.ServerId == serverId && !updatedUuids.Contains(p.Uuid))
                .ToList();
            foreach (var p in toRemove)
                OnlinePlayers.Remove(p);
        });
    }

    public IReadOnlyList<PlayerInfo> GetAll()
    {
        lock (_lock) { return _cache.Values.ToList(); }
    }

    public IReadOnlyList<PlayerInfo> GetByServer(string serverId)
    {
        lock (_lock)
        {
            return _cache.Values.Where(p => p.ServerId == serverId).ToList();
        }
    }

    public PlayerInfo? GetByUuid(string uuid)
    {
        lock (_lock)
        {
            _cache.TryGetValue(uuid, out var p);
            return p;
        }
    }

    public void Clear()
    {
        lock (_lock) { _cache.Clear(); }
        Application.Current.Dispatcher.InvokeAsync(() => OnlinePlayers.Clear());
    }
}
