using System.Windows;
using System.Windows.Media;
using MaterialDesignThemes.Wpf;

namespace StaffControlSuite.Services;

public static class AppThemeService
{
    public static void Apply(string themeMode, string primaryAccent, string? secondaryAccent = null)
    {
        Application.Current.Dispatcher.Invoke(() =>
        {
            ApplyMaterialTheme(themeMode, primaryAccent, secondaryAccent);
            UpdateAccentBrushes(primaryAccent, secondaryAccent);
            ApplyColorScheme(AppSettings.Current);
        });
    }

    public static void Apply(AppSettings settings)
    {
        Application.Current.Dispatcher.Invoke(() =>
        {
            var secondary = string.IsNullOrWhiteSpace(settings.SecondaryAccentColor)
                ? null : settings.SecondaryAccentColor;
            ApplyMaterialTheme(settings.ThemeMode, settings.AccentColor, secondary);
            UpdateAccentBrushes(settings.AccentColor, secondary);
            ApplyColorScheme(settings);
        });
    }

    private static void ApplyMaterialTheme(string themeMode, string primary, string? secondary)
    {
        try
        {
            var helper = new PaletteHelper();
            var theme  = helper.GetTheme();

            var baseTheme = themeMode switch
            {
                "Light"  => BaseTheme.Light,
                "System" => BaseTheme.Inherit,
                _        => BaseTheme.Dark
            };
            theme.SetBaseTheme(baseTheme);

            if (TryParseColor(primary, out var primaryColor))
                theme.SetPrimaryColor(primaryColor);

            if (!string.IsNullOrWhiteSpace(secondary) && TryParseColor(secondary!, out var secondaryColor))
                theme.SetSecondaryColor(secondaryColor);

            helper.SetTheme(theme);
        }
        catch { }
    }

    private static void UpdateAccentBrushes(string primary, string? secondary)
    {
        try
        {
            if (!TryParseColor(primary, out var primaryColor)) return;

            Application.Current.Resources["AccentBrush"] = new SolidColorBrush(primaryColor);
            // Also sync InfoBrush to accent so accent-colored UI elements update
            Application.Current.Resources["InfoBrush"] = new SolidColorBrush(primaryColor);

            string direction = AppSettings.Current?.GradientDirection ?? "LeftRight";

            Brush navHighlight;
            if (!string.IsNullOrWhiteSpace(secondary) && TryParseColor(secondary!, out var secondaryColor))
            {
                navHighlight = MakeGradient(
                    Color.FromArgb(80, primaryColor.R,   primaryColor.G,   primaryColor.B),
                    Color.FromArgb(80, secondaryColor.R, secondaryColor.G, secondaryColor.B),
                    direction);

                Application.Current.Resources["AccentGradientBrush"] = MakeGradient(
                    primaryColor, secondaryColor, direction);
            }
            else
            {
                navHighlight = new SolidColorBrush(
                    Color.FromArgb(80, primaryColor.R, primaryColor.G, primaryColor.B));
                Application.Current.Resources["AccentGradientBrush"] =
                    new SolidColorBrush(primaryColor);
            }

            Application.Current.Resources["NavHighlightBrush"] = navHighlight;
        }
        catch { }
    }

    private static void ApplyColorScheme(AppSettings s)
    {
        SetBrush("AppBackgroundBrush",   s.AppBackground,      "#1e1e2e");
        SetBrush("SidebarBgBrush",       s.SidebarBg,          "#181825");
        SetBrush("CardBgBrush",          s.CardBg,             "#313244");
        SetBrush("TableBgBrush",         s.TableBg,            "#181825");
        SetBrush("TableRowBgBrush",      s.TableRowBg,         "#1e1e2e");
        SetBrush("TableAltRowBgBrush",   s.TableAltRowBg,      "#252535");
        SetBrush("TableHoverBgBrush",    s.TableHoverBg,       "#2a2a3f");
        SetBrush("AppBorderBrush",       s.AppBorderColor,     "#313244");
        SetBrush("TextPrimaryBrush",     s.TextPrimaryColor,   "#cdd6f4");
        SetBrush("TextSecondaryBrush",   s.TextSecondaryColor, "#6c7086");
        SetBrush("SuccessBrush",         s.SuccessColor,       "#a6e3a1");
        SetBrush("WarningBrush",         s.WarningColor,       "#f9e2af");
        SetBrush("DangerBrush",          s.DangerColor,        "#f38ba8");
        // InfoBrush is synced with AccentBrush in UpdateAccentBrushes; skip here
    }

    private static void SetBrush(string key, string? hex, string fallback)
    {
        var h = string.IsNullOrWhiteSpace(hex) ? fallback : hex;
        if (TryParseColor(h, out var c))
            Application.Current.Resources[key] = new SolidColorBrush(c);
        else if (TryParseColor(fallback, out var fc))
            Application.Current.Resources[key] = new SolidColorBrush(fc);
    }

    private static LinearGradientBrush MakeGradient(Color c1, Color c2, string direction)
    {
        var (start, end) = direction switch
        {
            "TopBottom" => (new Point(0.5, 0.0), new Point(0.5, 1.0)),
            "Diagonal"  => (new Point(0.0, 0.0), new Point(1.0, 1.0)),
            _           => (new Point(0.0, 0.5), new Point(1.0, 0.5))
        };
        return new LinearGradientBrush(
            new GradientStopCollection { new GradientStop(c1, 0.0), new GradientStop(c2, 1.0) },
            start, end);
    }

    private static bool TryParseColor(string hex, out Color color)
    {
        try
        {
            color = (Color)ColorConverter.ConvertFromString(hex);
            return true;
        }
        catch
        {
            color = Colors.Transparent;
            return false;
        }
    }
}
