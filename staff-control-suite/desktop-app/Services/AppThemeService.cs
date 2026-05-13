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
            UpdateBrushResources(primaryAccent, secondaryAccent);
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
        catch { /* fallback: keep existing theme */ }
    }

    private static void UpdateBrushResources(string primary, string? secondary)
    {
        try
        {
            if (!TryParseColor(primary, out var primaryColor)) return;

            Application.Current.Resources["AccentBrush"] = new SolidColorBrush(primaryColor);

            Brush navHighlight;
            if (!string.IsNullOrWhiteSpace(secondary) && TryParseColor(secondary!, out var secondaryColor))
            {
                navHighlight = new LinearGradientBrush(
                    new GradientStopCollection
                    {
                        new GradientStop(Color.FromArgb(80, primaryColor.R,   primaryColor.G,   primaryColor.B),   0.0),
                        new GradientStop(Color.FromArgb(80, secondaryColor.R, secondaryColor.G, secondaryColor.B), 1.0),
                    },
                    new Point(0, 0.5), new Point(1, 0.5));

                Application.Current.Resources["AccentGradientBrush"] = new LinearGradientBrush(
                    new GradientStopCollection
                    {
                        new GradientStop(primaryColor,   0.0),
                        new GradientStop(secondaryColor, 1.0),
                    },
                    new Point(0, 0.5), new Point(1, 0.5));
            }
            else
            {
                navHighlight = new SolidColorBrush(Color.FromArgb(80, primaryColor.R, primaryColor.G, primaryColor.B));
                Application.Current.Resources["AccentGradientBrush"] = new SolidColorBrush(primaryColor);
            }

            Application.Current.Resources["NavHighlightBrush"] = navHighlight;
        }
        catch { }
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
