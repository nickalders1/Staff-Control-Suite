using CommunityToolkit.Mvvm.ComponentModel;

namespace StaffControlSuite.Models;

public partial class AgentStatus : ObservableObject
{
    [ObservableProperty] private string _serverId = "";
    [ObservableProperty] private string _serverName = "";
    [ObservableProperty] private string _serverType = "";
    [ObservableProperty] private bool _connected;
    [ObservableProperty] private string _version = "";
    [ObservableProperty] private int _protocolVersion;
    [ObservableProperty] private long _lastHeartbeatAt;
    [ObservableProperty] private long _latencyMs;
    [ObservableProperty] private long _connectTime;
    [ObservableProperty] private int _reconnectAttempts;
    [ObservableProperty] private string _status = "OFFLINE";
    [ObservableProperty] private string _lastError = "";
    [ObservableProperty] private string _heartbeatAgeDisplay = "Never";

    public string LatencyDisplay => LatencyMs > 0 ? $"{LatencyMs}ms" : "-";
    public string VersionDisplay => string.IsNullOrEmpty(Version) ? "-" : Version;
    public string ReconnectsDisplay => ReconnectAttempts > 0 ? ReconnectAttempts.ToString() : "-";

    public void RefreshTimeDisplays()
    {
        HeartbeatAgeDisplay = ComputeHeartbeatAge();
        OnPropertyChanged(nameof(LatencyDisplay));
    }

    private string ComputeHeartbeatAge()
    {
        if (LastHeartbeatAt <= 0) return "Never";
        var ageMs = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() - LastHeartbeatAt;
        if (ageMs < 1000) return "<1s ago";
        if (ageMs < 60_000) return $"{ageMs / 1000}s ago";
        if (ageMs < 3_600_000) return $"{ageMs / 60_000}m ago";
        return $"{ageMs / 3_600_000}h ago";
    }
}
