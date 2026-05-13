using CommunityToolkit.Mvvm.ComponentModel;

namespace StaffControlSuite.Models;

public class Role : ObservableObject
{
    private long _id;
    private string _name = "";
    private string _displayName = "";
    private List<string> _permissions = new();
    private bool _isSystemRole;
    private long _createdAt;

    public long Id
    {
        get => _id;
        set => SetProperty(ref _id, value);
    }

    public string Name
    {
        get => _name;
        set => SetProperty(ref _name, value);
    }

    public string DisplayName
    {
        get => _displayName;
        set => SetProperty(ref _displayName, value);
    }

    public List<string> Permissions
    {
        get => _permissions;
        set
        {
            if (SetProperty(ref _permissions, value))
                OnPropertyChanged(nameof(PermissionCount));
        }
    }

    public bool IsSystemRole
    {
        get => _isSystemRole;
        set => SetProperty(ref _isSystemRole, value);
    }

    public long CreatedAt
    {
        get => _createdAt;
        set
        {
            if (SetProperty(ref _createdAt, value))
                OnPropertyChanged(nameof(CreatedAtDisplay));
        }
    }

    public int PermissionCount => Permissions.Count;

    public string CreatedAtDisplay => CreatedAt > 0
        ? DateTimeOffset.FromUnixTimeMilliseconds(CreatedAt).LocalDateTime.ToString("yyyy-MM-dd")
        : "Unknown";
}
