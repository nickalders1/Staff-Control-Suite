using CommunityToolkit.Mvvm.ComponentModel;

namespace StaffControlSuite.Models;

public class AuditLog : ObservableObject
{
    private long _id;
    private long _userId;
    private string _username = "";
    private string _action = "";
    private string _target = "";
    private string _details = "";
    private string _ipAddress = "";
    private long _timestamp;

    public long Id
    {
        get => _id;
        set => SetProperty(ref _id, value);
    }

    public long UserId
    {
        get => _userId;
        set => SetProperty(ref _userId, value);
    }

    public string Username
    {
        get => _username;
        set => SetProperty(ref _username, value);
    }

    public string Action
    {
        get => _action;
        set => SetProperty(ref _action, value);
    }

    public string Target
    {
        get => _target;
        set => SetProperty(ref _target, value);
    }

    public string Details
    {
        get => _details;
        set => SetProperty(ref _details, value);
    }

    public string IpAddress
    {
        get => _ipAddress;
        set => SetProperty(ref _ipAddress, value);
    }

    public long Timestamp
    {
        get => _timestamp;
        set
        {
            if (SetProperty(ref _timestamp, value))
                OnPropertyChanged(nameof(TimestampDisplay));
        }
    }

    public string TimestampDisplay => Timestamp > 0
        ? DateTimeOffset.FromUnixTimeMilliseconds(Timestamp).LocalDateTime.ToString("yyyy-MM-dd HH:mm:ss")
        : "Unknown";
}
