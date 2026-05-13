using System.Text.Json;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using StaffControlSuite.Models;
using StaffControlSuite.Protocol;

namespace StaffControlSuite.ViewModels;

public partial class PlayerDetailsViewModel : ObservableObject
{
    private readonly string _uuid;

    [ObservableProperty] private PlayerInfo? _player;
    [ObservableProperty] private bool _isLoading;

    public string FirstJoinedDisplay => Player?.FirstJoined > 0
        ? DateTimeOffset.FromUnixTimeMilliseconds(Player.FirstJoined).LocalDateTime.ToString("yyyy-MM-dd HH:mm")
        : "Unknown";

    public PlayerDetailsViewModel(string uuid)
    {
        _uuid = uuid;
        Player = new PlayerInfo { Uuid = uuid, Name = "Loading..." };
    }

    [RelayCommand]
    public async Task LoadPlayerAsync()
    {
        IsLoading = true;
        try
        {
            var result = await App.WebSocketService.SendRequestAsync(
                MessageTypes.PlayersDetails,
                new { uuid = _uuid },
                App.AuthService.SessionToken);

            var playerEl = result.ValueKind == JsonValueKind.Object &&
                           result.TryGetProperty("player", out var pe) ? pe : result;

            Player = new PlayerInfo
            {
                Uuid = playerEl.TryGetProperty("uuid", out var u) ? u.GetString() ?? "" : "",
                Name = playerEl.TryGetProperty("name", out var n) ? n.GetString() ?? "" : "",
                ServerId = playerEl.TryGetProperty("serverId", out var sid) ? sid.GetString() ?? "" : "",
                World = playerEl.TryGetProperty("world", out var w) ? w.GetString() ?? "" : "",
                Gamemode = playerEl.TryGetProperty("gamemode", out var gm) ? gm.GetString() ?? "" : "",
                Health = playerEl.TryGetProperty("health", out var h) ? h.GetDouble() : 20.0,
                FoodLevel = playerEl.TryGetProperty("foodLevel", out var fl) ? fl.GetInt32() : 20,
                Ping = playerEl.TryGetProperty("ping", out var ping) ? ping.GetInt32() : 0,
                IsOnline = playerEl.TryGetProperty("isOnline", out var io) && io.GetBoolean(),
                FirstJoined = playerEl.TryGetProperty("firstJoined", out var fj) ? fj.GetInt64() : 0L,
                LastJoined = playerEl.TryGetProperty("lastJoined", out var lj) ? lj.GetInt64() : 0L,
                PlaytimeSeconds = playerEl.TryGetProperty("playtimeSeconds", out var pt) ? pt.GetInt64() : 0L,
            };

            OnPropertyChanged(nameof(FirstJoinedDisplay));
        }
        catch
        {
            // Keep current data
        }
        finally
        {
            IsLoading = false;
        }
    }

    [RelayCommand]
    private void Back()
    {
        App.NavigationService.NavigateShell("Players");
    }
}
