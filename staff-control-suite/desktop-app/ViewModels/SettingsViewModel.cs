using System.Collections.ObjectModel;
using System.Windows.Media;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using StaffControlSuite.Protocol;
using StaffControlSuite.Services;
using Color = System.Windows.Media.Color;

namespace StaffControlSuite.ViewModels;

public partial class SettingsViewModel : ObservableObject
{
    // ── Connection ─────────────────────────────────────────────
    [ObservableProperty] private string _proxyHost = AppSettings.Current.ProxyHost;
    [ObservableProperty] private int    _proxyPort = AppSettings.Current.ProxyPort;

    // ── Appearance ─────────────────────────────────────────────
    [ObservableProperty] private string _appSidebarTitle    = AppSettings.Current.SidebarTitle;
    [ObservableProperty] private string _appSidebarSubtitle = AppSettings.Current.SidebarSubtitle;
    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(AccentColorBrush))]
    private string _appAccentColor = AppSettings.Current.AccentColor;
    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(SecondaryAccentColorBrush))]
    private string _appSecondaryAccentColor = AppSettings.Current.SecondaryAccentColor;
    [ObservableProperty] private string _appThemeMode       = AppSettings.Current.ThemeMode;
    [ObservableProperty] private bool   _compactMode        = AppSettings.Current.CompactMode;
    [ObservableProperty] private string _logoImagePath      = AppSettings.Current.LogoImagePath;
    [ObservableProperty] private bool   _isFetchingBranding;

    public SolidColorBrush AccentColorBrush
    {
        get
        {
            try { return new SolidColorBrush((Color)ColorConverter.ConvertFromString(AppAccentColor)); }
            catch { return new SolidColorBrush(Colors.Transparent); }
        }
    }

    public SolidColorBrush SecondaryAccentColorBrush
    {
        get
        {
            if (string.IsNullOrWhiteSpace(AppSecondaryAccentColor))
                return new SolidColorBrush(Colors.Transparent);
            try { return new SolidColorBrush((Color)ColorConverter.ConvertFromString(AppSecondaryAccentColor)); }
            catch { return new SolidColorBrush(Colors.Transparent); }
        }
    }

    public ObservableCollection<string> ThemeModes { get; } = new() { "Dark", "Light" };

    // ── Feedback message ───────────────────────────────────────
    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasSaveMessage))]
    private string _saveMessage = "";
    public bool HasSaveMessage => !string.IsNullOrEmpty(SaveMessage);

    // ── Session info ───────────────────────────────────────────
    public string Username       => App.AuthService.CurrentUser?.Username ?? "Unknown";
    public string UserRole       => App.AuthService.CurrentUser?.RoleName ?? "Unknown";
    public int    PermissionCount => App.AuthService.CurrentUser?.Permissions.Count ?? 0;

    public string MaskedToken
    {
        get
        {
            var token = App.AuthService.SessionToken;
            if (string.IsNullOrEmpty(token) || token.Length < 8) return "••••••••";
            return token[..4] + "••••••••" + token[^4..];
        }
    }

    // ── Commands ───────────────────────────────────────────────
    [RelayCommand]
    private void SaveConnection()
    {
        AppSettings.Current.ProxyHost = ProxyHost;
        AppSettings.Current.ProxyPort = ProxyPort;
        AppSettings.Save();
        ShowMessage("Connection settings saved.");
    }

    [RelayCommand]
    private void SaveAppearance()
    {
        AppSettings.Current.SidebarTitle          = AppSidebarTitle;
        AppSettings.Current.SidebarSubtitle       = AppSidebarSubtitle;
        AppSettings.Current.AccentColor           = AppAccentColor;
        AppSettings.Current.SecondaryAccentColor  = AppSecondaryAccentColor;
        AppSettings.Current.ThemeMode             = AppThemeMode;
        AppSettings.Current.CompactMode           = CompactMode;
        AppSettings.Save();

        var secondary = string.IsNullOrWhiteSpace(AppSecondaryAccentColor) ? null : AppSecondaryAccentColor;
        AppThemeService.Apply(AppThemeMode, AppAccentColor, secondary);
        App.NavigationService.RefreshShellBranding();
        ShowMessage("Appearance saved.");
    }

    [RelayCommand]
    private async Task FetchBrandingFromProxyAsync()
    {
        IsFetchingBranding = true;
        try
        {
            await App.BrandingService.FetchFromProxyAsync();
            // Refresh local fields from updated settings
            AppSidebarTitle           = AppSettings.Current.SidebarTitle;
            AppSidebarSubtitle        = AppSettings.Current.SidebarSubtitle;
            AppAccentColor            = AppSettings.Current.AccentColor;
            LogoImagePath             = AppSettings.Current.LogoImagePath;
            var secondary = string.IsNullOrWhiteSpace(AppSettings.Current.SecondaryAccentColor)
                ? null : AppSettings.Current.SecondaryAccentColor;
            AppThemeService.Apply(AppSettings.Current.ThemeMode, AppSettings.Current.AccentColor, secondary);
            App.NavigationService.RefreshShellBranding();
            ShowMessage("Branding fetched from proxy.");
        }
        catch (Exception ex)
        {
            ShowMessage($"Failed: {ex.Message}");
        }
        finally
        {
            IsFetchingBranding = false;
        }
    }

    [RelayCommand]
    private async Task LogoutAsync()
    {
        try
        {
            if (App.WebSocketService.IsConnected && App.AuthService.SessionToken != null)
            {
                await App.WebSocketService.SendRequestAsync(
                    MessageTypes.AuthLogout, null, App.AuthService.SessionToken);
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

    private void ShowMessage(string msg)
    {
        SaveMessage = msg;
        _ = Task.Delay(3000).ContinueWith(_ =>
            System.Windows.Application.Current.Dispatcher.Invoke(() => SaveMessage = ""));
    }
}
