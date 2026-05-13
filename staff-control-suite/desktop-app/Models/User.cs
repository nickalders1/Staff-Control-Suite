using CommunityToolkit.Mvvm.ComponentModel;

namespace StaffControlSuite.Models;

public class User : ObservableObject
{
    private long _id;
    private string _username = "";
    private long _roleId;
    private string _roleName = "";
    private List<string> _permissions = new();
    private bool _isActive;
    private long _createdAt;

    public long Id
    {
        get => _id;
        set => SetProperty(ref _id, value);
    }

    public string Username
    {
        get => _username;
        set => SetProperty(ref _username, value);
    }

    public long RoleId
    {
        get => _roleId;
        set => SetProperty(ref _roleId, value);
    }

    public string RoleName
    {
        get => _roleName;
        set => SetProperty(ref _roleName, value);
    }

    public List<string> Permissions
    {
        get => _permissions;
        set => SetProperty(ref _permissions, value);
    }

    public bool IsActive
    {
        get => _isActive;
        set => SetProperty(ref _isActive, value);
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

    public string CreatedAtDisplay => CreatedAt > 0
        ? DateTimeOffset.FromUnixTimeMilliseconds(CreatedAt).LocalDateTime.ToString("yyyy-MM-dd")
        : "Unknown";

    public string StatusDisplay => IsActive ? "Active" : "Inactive";

    public bool HasPermission(string node) => Permissions.Contains(node);
}
