using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using StaffControlSuite.Protocol;

namespace StaffControlSuite.ViewModels;

public partial class ShellViewModel : ObservableObject
{
    [ObservableProperty] private string _currentViewTitle = "Overview";

    public string Username => App.AuthService.CurrentUser?.Username ?? "Unknown";
    public string UserRole => App.AuthService.CurrentUser?.RoleName ?? "";
    public string UserInitial => Username.Length > 0 ? Username[0].ToString().ToUpper() : "?";

    public string ConnectedServer => $"{StaffControlSuite.Services.AppSettings.Current.ProxyHost}:{StaffControlSuite.Services.AppSettings.Current.ProxyPort}";

    public bool CanViewServers  => App.AuthService.HasPermission("servers.view")  || App.AuthService.IsOwner();
    public bool CanViewConsole  => App.AuthService.HasPermission("console.view")  || App.AuthService.IsOwner();
    public bool CanViewPlayers  => App.AuthService.HasPermission("players.view")  || App.AuthService.IsOwner();
    public bool CanViewUsers    => App.AuthService.HasPermission("users.view")    || App.AuthService.HasPermission("roles.view") || App.AuthService.IsOwner();
    public bool CanViewSettings => App.AuthService.HasPermission("settings.view") || App.AuthService.IsOwner();
    public bool CanViewAudit    => App.AuthService.HasPermission("audit.view")    || App.AuthService.IsOwner();

    [RelayCommand]
    private async Task LogoutAsync()
    {
        try
        {
            if (App.WebSocketService.IsConnected && App.AuthService.SessionToken != null)
            {
                await App.WebSocketService.SendRequestAsync(
                    MessageTypes.AuthLogout,
                    null,
                    App.AuthService.SessionToken
                );
            }
        }
        catch { /* Ignore logout errors */ }
        finally
        {
            App.AuthService.ClearSession();
            await App.WebSocketService.DisconnectAsync();
            App.NavigationService.ShowLogin();
        }
    }
}
