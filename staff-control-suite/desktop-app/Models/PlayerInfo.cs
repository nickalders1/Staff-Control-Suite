using CommunityToolkit.Mvvm.ComponentModel;

namespace StaffControlSuite.Models;

public class PlayerInfo : ObservableObject
{
    private string _uuid = "";
    private string _name = "";
    private string _serverId = "";
    private string _world = "";
    private string _gamemode = "";
    private double _health;
    private int _foodLevel;
    private int _ping;
    private bool _isOnline;
    private long _firstJoined;
    private long _lastJoined;
    private long _playtimeSeconds;

    public string Uuid
    {
        get => _uuid;
        set => SetProperty(ref _uuid, value);
    }

    public string Name
    {
        get => _name;
        set => SetProperty(ref _name, value);
    }

    public string ServerId
    {
        get => _serverId;
        set => SetProperty(ref _serverId, value);
    }

    public string World
    {
        get => _world;
        set => SetProperty(ref _world, value);
    }

    public string Gamemode
    {
        get => _gamemode;
        set => SetProperty(ref _gamemode, value);
    }

    public double Health
    {
        get => _health;
        set => SetProperty(ref _health, value);
    }

    public int FoodLevel
    {
        get => _foodLevel;
        set => SetProperty(ref _foodLevel, value);
    }

    public int Ping
    {
        get => _ping;
        set
        {
            if (SetProperty(ref _ping, value))
                OnPropertyChanged(nameof(PingDisplay));
        }
    }

    public bool IsOnline
    {
        get => _isOnline;
        set => SetProperty(ref _isOnline, value);
    }

    public long FirstJoined
    {
        get => _firstJoined;
        set => SetProperty(ref _firstJoined, value);
    }

    public long LastJoined
    {
        get => _lastJoined;
        set
        {
            if (SetProperty(ref _lastJoined, value))
                OnPropertyChanged(nameof(LastJoinedDisplay));
        }
    }

    public long PlaytimeSeconds
    {
        get => _playtimeSeconds;
        set
        {
            if (SetProperty(ref _playtimeSeconds, value))
                OnPropertyChanged(nameof(PlaytimeDisplay));
        }
    }

    public string LastJoinedDisplay => LastJoined > 0
        ? DateTimeOffset.FromUnixTimeMilliseconds(LastJoined).LocalDateTime.ToString("yyyy-MM-dd HH:mm")
        : "Never";

    public string PlaytimeDisplay
    {
        get
        {
            var totalMinutes = PlaytimeSeconds / 60;
            var hours = totalMinutes / 60;
            var minutes = totalMinutes % 60;
            return $"{hours}h {minutes}m";
        }
    }

    public string PingDisplay => $"{Ping}ms";
}
