using System.IO;
using System.Text.Json;

namespace StaffControlSuite.Services;

public class AppSettings
{
    private static readonly string SettingsDir =
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "StaffControlSuite");

    private static readonly string SettingsFile = Path.Combine(SettingsDir, "settings.json");

    public static AppSettings Current { get; private set; } = new();

    public string ProxyHost { get; set; } = "localhost";
    public int ProxyPort { get; set; } = 8080;
    public string? LastToken { get; set; }

    public string SidebarTitle { get; set; } = "";
    public string SidebarSubtitle { get; set; } = "";
    public string LogoImagePath { get; set; } = "";
    public string AccentColor { get; set; } = "#89b4fa";
    public string SecondaryAccentColor { get; set; } = "";
    public bool CompactMode { get; set; } = false;
    public string ThemeMode { get; set; } = "Dark";

    // ── Deep color customization ──────────────────────────────
    public string AppBackground      { get; set; } = "#1e1e2e";
    public string SidebarBg          { get; set; } = "#181825";
    public string CardBg             { get; set; } = "#313244";
    public string TableBg            { get; set; } = "#181825";
    public string TableRowBg         { get; set; } = "#1e1e2e";
    public string TableAltRowBg      { get; set; } = "#252535";
    public string TableHoverBg       { get; set; } = "#2a2a3f";
    public string AppBorderColor     { get; set; } = "#313244";
    public string TextPrimaryColor   { get; set; } = "#cdd6f4";
    public string TextSecondaryColor { get; set; } = "#6c7086";
    public string SuccessColor       { get; set; } = "#a6e3a1";
    public string WarningColor       { get; set; } = "#f9e2af";
    public string DangerColor        { get; set; } = "#f38ba8";
    public string InfoColor          { get; set; } = "#89b4fa";
    public bool   GradientEnabled    { get; set; } = false;
    public string GradientDirection  { get; set; } = "LeftRight";

    public static string SettingsDirectory =>
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "StaffControlSuite");

    public static void Load()
    {
        try
        {
            if (File.Exists(SettingsFile))
            {
                var json = File.ReadAllText(SettingsFile);
                var loaded = JsonSerializer.Deserialize<AppSettings>(json,
                    new JsonSerializerOptions { PropertyNameCaseInsensitive = true });
                if (loaded != null)
                    Current = loaded;
            }
        }
        catch
        {
            Current = new AppSettings();
        }
    }

    public static void Save()
    {
        try
        {
            Directory.CreateDirectory(SettingsDir);
            var json = JsonSerializer.Serialize(Current,
                new JsonSerializerOptions { WriteIndented = true });
            File.WriteAllText(SettingsFile, json);
        }
        catch
        {
            // Ignore save errors silently
        }
    }
}
