using StaffControlSuite.Models;

namespace StaffControlSuite.Services;

public class AuthService
{
    public bool IsAuthenticated => CurrentUser != null;
    public User? CurrentUser { get; private set; }
    public string? SessionToken { get; private set; }

    public bool HasPermission(string node)
    {
        return CurrentUser?.HasPermission(node) ?? false;
    }

    public void SetSession(string token, User user)
    {
        SessionToken = token;
        CurrentUser = user;
        AppSettings.Current.LastToken = token;
        AppSettings.Save();
    }

    public void ClearSession()
    {
        SessionToken = null;
        CurrentUser = null;
        AppSettings.Current.LastToken = null;
        AppSettings.Save();
    }

    public bool IsOwner() => CurrentUser?.RoleName?.ToLower() == "owner";
}
