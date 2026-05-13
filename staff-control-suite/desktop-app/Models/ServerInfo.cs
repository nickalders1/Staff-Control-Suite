using CommunityToolkit.Mvvm.ComponentModel;

namespace StaffControlSuite.Models;

public class ServerInfo : ObservableObject
{
    private string _serverId = "";
    private string _serverName = "";
    private string _serverType = "";
    private string _host = "";
    private int _port;
    private bool _isOnline;
    private int _playerCount;
    private int _maxPlayers;
    private double _tps;
    private double _mspt;
    private long _lastHeartbeat;
    private string _minecraftVersion = "";
    private string _paperVersion = "";
    private int _pluginCount;
    private int _loadedChunks;

    public string ServerId
    {
        get => _serverId;
        set => SetProperty(ref _serverId, value);
    }

    public string ServerName
    {
        get => _serverName;
        set => SetProperty(ref _serverName, value);
    }

    public string ServerType
    {
        get => _serverType;
        set => SetProperty(ref _serverType, value);
    }

    public string Host
    {
        get => _host;
        set => SetProperty(ref _host, value);
    }

    public int Port
    {
        get => _port;
        set => SetProperty(ref _port, value);
    }

    public bool IsOnline
    {
        get => _isOnline;
        set
        {
            if (SetProperty(ref _isOnline, value))
                OnPropertyChanged(nameof(StatusDisplay));
        }
    }

    public int PlayerCount
    {
        get => _playerCount;
        set
        {
            if (SetProperty(ref _playerCount, value))
                OnPropertyChanged(nameof(PlayerDisplay));
        }
    }

    public int MaxPlayers
    {
        get => _maxPlayers;
        set
        {
            if (SetProperty(ref _maxPlayers, value))
                OnPropertyChanged(nameof(PlayerDisplay));
        }
    }

    public double Tps
    {
        get => _tps;
        set
        {
            if (SetProperty(ref _tps, value))
                OnPropertyChanged(nameof(TpsDisplay));
        }
    }

    public double Mspt
    {
        get => _mspt;
        set => SetProperty(ref _mspt, value);
    }

    public long LastHeartbeat
    {
        get => _lastHeartbeat;
        set
        {
            if (SetProperty(ref _lastHeartbeat, value))
                OnPropertyChanged(nameof(LastHeartbeatDisplay));
        }
    }

    public string MinecraftVersion
    {
        get => _minecraftVersion;
        set => SetProperty(ref _minecraftVersion, value);
    }

    public string PaperVersion
    {
        get => _paperVersion;
        set => SetProperty(ref _paperVersion, value);
    }

    public int PluginCount
    {
        get => _pluginCount;
        set => SetProperty(ref _pluginCount, value);
    }

    public int LoadedChunks
    {
        get => _loadedChunks;
        set => SetProperty(ref _loadedChunks, value);
    }

    public string StatusDisplay => IsOnline ? "Online" : "Offline";

    public string TpsDisplay => $"{Tps:F1} TPS";

    public string PlayerDisplay => $"{PlayerCount}/{MaxPlayers}";

    public string LastHeartbeatDisplay => LastHeartbeat > 0
        ? DateTimeOffset.FromUnixTimeMilliseconds(LastHeartbeat).LocalDateTime.ToString("HH:mm:ss")
        : "Never";
}
