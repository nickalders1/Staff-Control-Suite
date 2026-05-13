using System.Diagnostics;
using System.Windows;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using StaffControlSuite.Protocol;
using StaffControlSuite.Services;

namespace StaffControlSuite.ViewModels;

public partial class ShellViewModel : ObservableObject
{
    [ObservableProperty] private string _currentViewTitle = "Overview";
    [ObservableProperty] private string _sidebarTitle    = "Staff Control";
    [ObservableProperty] private string _sidebarSubtitle = "";

    [ObservableProperty] private bool   _updateAvailable  = false;
    [ObservableProperty] private string _latestVersion    = "";
    [ObservableProperty] private string _releaseUrl       = "";

    public string CurrentVersion => UpdateCheckService.GetCurrentVersion();

    public string Username      => App.AuthService.CurrentUser?.Username ?? "Unknown";
    public string UserRole      => App.AuthService.CurrentUser?.RoleName ?? "";
    public string UserInitial   => Username.Length > 0 ? Username[0].ToString().ToUpper() : "?";
    public string ConnectedServer => $"{AppSettings.Current.ProxyHost}:{AppSettings.Current.ProxyPort}";

    public bool CanViewServers     => App.AuthService.HasPermission("servers.view")     || App.AuthService.IsOwner();
    public bool CanViewConsole     => App.AuthService.HasPermission("console.view")     || App.AuthService.IsOwner();
    public bool CanViewPlayers     => App.AuthService.HasPermission("players.view")     || App.AuthService.IsOwner();
    public bool CanViewUsers       => App.AuthService.HasPermission("users.view")       || App.AuthService.HasPermission("roles.view") || App.AuthService.IsOwner();
    public bool CanViewSettings    => App.AuthService.HasPermission("settings.view")    || App.AuthService.IsOwner();
    public bool CanViewAudit       => App.AuthService.HasPermission("audit.view")       || App.AuthService.IsOwner();
    public bool CanViewModeration  => App.AuthService.HasPermission("moderation.view")  || App.AuthService.IsOwner();

    public ShellViewModel()
    {
        RefreshBranding();
        _ = InitializeAsync();
    }

    private async Task InitializeAsync()
    {
        try
        {
            await App.BrandingService.FetchFromProxyAsync();
        }
        catch { /* branding fetch on startup is best-effort */ }

        await Application.Current.Dispatcher.InvokeAsync(RefreshBranding);

        var update = await UpdateCheckService.CheckAsync();
        if (update.HasUpdate)
        {
            await Application.Current.Dispatcher.InvokeAsync(() =>
            {
                LatestVersion   = update.LatestVersion;
                ReleaseUrl      = update.DownloadUrl;
                UpdateAvailable = true;
            });
        }
    }

    [RelayCommand]
    private void OpenReleasePage()
    {
        if (!string.IsNullOrEmpty(ReleaseUrl))
            Process.Start(new ProcessStartInfo(ReleaseUrl) { UseShellExecute = true });
    }

    public void RefreshBranding()
    {
        SidebarTitle = string.IsNullOrWhiteSpace(AppSettings.Current.SidebarTitle)
            ? "Staff Control"
            : AppSettings.Current.SidebarTitle;

        SidebarSubtitle = string.IsNullOrWhiteSpace(AppSettings.Current.SidebarSubtitle)
            ? ConnectedServer
            : AppSettings.Current.SidebarSubtitle;
    }

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
                    App.AuthService.SessionToken);
            }
        }
        catch { }
        finally
        {
            App.AuthService.ClearSession();
            await App.WebSocketService.DisconnectAsync();
            App.NavigationService.ShowLogin();
        }
    }
}
