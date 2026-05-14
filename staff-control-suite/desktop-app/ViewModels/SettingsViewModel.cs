using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.IO;
using System.Windows.Media;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.Win32;
using StaffControlSuite.Protocol;
using StaffControlSuite.Services;
using Color = System.Windows.Media.Color;

namespace StaffControlSuite.ViewModels;

// ── Color entry model ──────────────────────────────────────────────────────
public partial class ThemeColorEntry : ObservableObject
{
    public string Label { get; }

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(Swatch))]
    [NotifyPropertyChangedFor(nameof(IsValid))]
    private string _value;

    public SolidColorBrush Swatch
    {
        get
        {
            try { return new SolidColorBrush((Color)ColorConverter.ConvertFromString(_value)); }
            catch { return new SolidColorBrush(Color.FromRgb(60, 20, 20)); }
        }
    }

    public bool IsValid
    {
        get
        {
            if (string.IsNullOrWhiteSpace(_value)) return false;
            try { ColorConverter.ConvertFromString(_value); return true; }
            catch { return false; }
        }
    }

    public ThemeColorEntry(string label, string initialValue)
    {
        Label  = label;
        _value = string.IsNullOrWhiteSpace(initialValue) ? "#000000" : initialValue;
    }
}

// ── SettingsViewModel ──────────────────────────────────────────────────────
public partial class SettingsViewModel : ObservableObject
{
    // ── Connection ──────────────────────────────────────────
    [ObservableProperty] private string _proxyHost = AppSettings.Current.ProxyHost;
    [ObservableProperty] private int    _proxyPort = AppSettings.Current.ProxyPort;

    // ── Branding ────────────────────────────────────────────
    [ObservableProperty] private string _appSidebarTitle    = AppSettings.Current.SidebarTitle;
    [ObservableProperty] private string _appSidebarSubtitle = AppSettings.Current.SidebarSubtitle;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasLogoPreview))]
    private string _logoImagePath = AppSettings.Current.LogoImagePath;

    [ObservableProperty] private bool _isFetchingBranding;

    public bool HasLogoPreview =>
        !string.IsNullOrWhiteSpace(LogoImagePath) && File.Exists(LogoImagePath);

    // ── Accent colors ────────────────────────────────────────
    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(AccentColorBrush))]
    private string _appAccentColor = AppSettings.Current.AccentColor;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(SecondaryAccentColorBrush))]
    private string _appSecondaryAccentColor = AppSettings.Current.SecondaryAccentColor;

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

    // ── Theme mode ───────────────────────────────────────────
    [ObservableProperty] private string _appThemeMode = AppSettings.Current.ThemeMode;
    [ObservableProperty] private bool   _compactMode  = AppSettings.Current.CompactMode;
    public ObservableCollection<string> ThemeModes { get; } = new() { "Dark", "Light" };

    // ── Gradient ─────────────────────────────────────────────
    [ObservableProperty] private bool   _gradientEnabled   = AppSettings.Current.GradientEnabled;
    [ObservableProperty] private string _gradientDirection = AppSettings.Current.GradientDirection;
    public List<string> GradientDirections { get; } = new() { "LeftRight", "TopBottom", "Diagonal" };

    // ── Deep color collections ───────────────────────────────
    public List<ThemeColorEntry> BackgroundColors { get; }
    public List<ThemeColorEntry> TextColors       { get; }
    public List<ThemeColorEntry> StatusColors     { get; }

    // ── Permission: only owner / settings.manage can deep-edit ──
    public bool CanCustomizeTheme =>
        App.AuthService.IsOwner() || App.AuthService.HasPermission("settings.manage");

    // ── Feedback ─────────────────────────────────────────────
    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasSaveMessage))]
    private string _saveMessage = "";
    public bool HasSaveMessage => !string.IsNullOrEmpty(SaveMessage);

    // ── Session info ─────────────────────────────────────────
    public string Username        => App.AuthService.CurrentUser?.Username ?? "Unknown";
    public string UserRole        => App.AuthService.CurrentUser?.RoleName ?? "Unknown";
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

    // ── Constructor ───────────────────────────────────────────
    public SettingsViewModel()
    {
        var s = AppSettings.Current;
        BackgroundColors = new List<ThemeColorEntry>
        {
            new("App Background",  s.AppBackground),
            new("Sidebar",         s.SidebarBg),
            new("Card",            s.CardBg),
            new("Table",           s.TableBg),
            new("Table Row",       s.TableRowBg),
            new("Table Alt Row",   s.TableAltRowBg),
            new("Table Hover",     s.TableHoverBg),
            new("Border",          s.AppBorderColor),
        };
        TextColors = new List<ThemeColorEntry>
        {
            new("Primary Text",   s.TextPrimaryColor),
            new("Secondary Text", s.TextSecondaryColor),
        };
        StatusColors = new List<ThemeColorEntry>
        {
            new("Success", s.SuccessColor),
            new("Warning", s.WarningColor),
            new("Danger",  s.DangerColor),
            new("Info",    s.InfoColor),
        };
    }

    // ── Commands ──────────────────────────────────────────────

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
        AppSettings.Current.SidebarTitle         = AppSidebarTitle;
        AppSettings.Current.SidebarSubtitle      = AppSidebarSubtitle;
        AppSettings.Current.AccentColor          = AppAccentColor;
        AppSettings.Current.SecondaryAccentColor = GradientEnabled ? AppSecondaryAccentColor : "";
        AppSettings.Current.ThemeMode            = AppThemeMode;
        AppSettings.Current.CompactMode          = CompactMode;
        AppSettings.Current.GradientEnabled      = GradientEnabled;
        AppSettings.Current.GradientDirection    = GradientDirection;

        if (CanCustomizeTheme)
        {
            AppSettings.Current.AppBackground      = BackgroundColors[0].Value;
            AppSettings.Current.SidebarBg          = BackgroundColors[1].Value;
            AppSettings.Current.CardBg             = BackgroundColors[2].Value;
            AppSettings.Current.TableBg            = BackgroundColors[3].Value;
            AppSettings.Current.TableRowBg         = BackgroundColors[4].Value;
            AppSettings.Current.TableAltRowBg      = BackgroundColors[5].Value;
            AppSettings.Current.TableHoverBg       = BackgroundColors[6].Value;
            AppSettings.Current.AppBorderColor     = BackgroundColors[7].Value;
            AppSettings.Current.TextPrimaryColor   = TextColors[0].Value;
            AppSettings.Current.TextSecondaryColor = TextColors[1].Value;
            AppSettings.Current.SuccessColor       = StatusColors[0].Value;
            AppSettings.Current.WarningColor       = StatusColors[1].Value;
            AppSettings.Current.DangerColor        = StatusColors[2].Value;
            AppSettings.Current.InfoColor          = StatusColors[3].Value;
        }

        AppSettings.Save();
        AppThemeService.Apply(AppSettings.Current);
        App.NavigationService.RefreshShellBranding();
        ShowMessage("Appearance saved and applied.");
    }

    [RelayCommand]
    private void ResetAppearance()
    {
        var def = new AppSettings();
        AppAccentColor          = def.AccentColor;
        AppSecondaryAccentColor = def.SecondaryAccentColor;
        GradientEnabled         = def.GradientEnabled;
        GradientDirection       = def.GradientDirection;
        AppThemeMode            = def.ThemeMode;

        if (CanCustomizeTheme)
        {
            BackgroundColors[0].Value = def.AppBackground;
            BackgroundColors[1].Value = def.SidebarBg;
            BackgroundColors[2].Value = def.CardBg;
            BackgroundColors[3].Value = def.TableBg;
            BackgroundColors[4].Value = def.TableRowBg;
            BackgroundColors[5].Value = def.TableAltRowBg;
            BackgroundColors[6].Value = def.TableHoverBg;
            BackgroundColors[7].Value = def.AppBorderColor;
            TextColors[0].Value       = def.TextPrimaryColor;
            TextColors[1].Value       = def.TextSecondaryColor;
            StatusColors[0].Value     = def.SuccessColor;
            StatusColors[1].Value     = def.WarningColor;
            StatusColors[2].Value     = def.DangerColor;
            StatusColors[3].Value     = def.InfoColor;
        }
    }

    [RelayCommand]
    private async Task FetchBrandingFromProxyAsync()
    {
        IsFetchingBranding = true;
        try
        {
            await App.BrandingService.FetchFromProxyAsync();
            AppSidebarTitle    = AppSettings.Current.SidebarTitle;
            AppSidebarSubtitle = AppSettings.Current.SidebarSubtitle;
            AppAccentColor     = AppSettings.Current.AccentColor;
            LogoImagePath      = AppSettings.Current.LogoImagePath;
            AppThemeService.Apply(AppSettings.Current);
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
                await App.WebSocketService.SendRequestAsync(
                    MessageTypes.AuthLogout, null, App.AuthService.SessionToken);
        }
        catch { }
        finally
        {
            App.AuthService.ClearSession();
            await App.WebSocketService.DisconnectAsync();
            App.NavigationService.ShowLogin();
        }
    }

    [RelayCommand]
    private void ImportLogo()
    {
        var dialog = new OpenFileDialog
        {
            Title  = "Select Logo Image",
            Filter = "Image Files (*.png;*.jpg;*.jpeg)|*.png;*.jpg;*.jpeg",
        };
        if (dialog.ShowDialog() != true) return;
        try
        {
            var ext  = Path.GetExtension(dialog.FileName).ToLowerInvariant();
            var dest = Path.Combine(AppSettings.SettingsDirectory, "logo" + ext);
            Directory.CreateDirectory(AppSettings.SettingsDirectory);
            File.Copy(dialog.FileName, dest, overwrite: true);
            LogoImagePath                  = dest;
            AppSettings.Current.LogoImagePath = dest;
            AppSettings.Save();
            App.NavigationService.RefreshShellBranding();
            ShowMessage("Logo imported and applied.");
        }
        catch (Exception ex)
        {
            ShowMessage($"Failed to import logo: {ex.Message}");
        }
    }

    [RelayCommand]
    private void ClearLogo()
    {
        LogoImagePath                  = "";
        AppSettings.Current.LogoImagePath = "";
        AppSettings.Save();
        App.NavigationService.RefreshShellBranding();
        ShowMessage("Logo cleared.");
    }

    private void ShowMessage(string msg)
    {
        SaveMessage = msg;
        _ = Task.Delay(3000).ContinueWith(_ =>
            System.Windows.Application.Current.Dispatcher.Invoke(() => SaveMessage = ""));
    }
}
