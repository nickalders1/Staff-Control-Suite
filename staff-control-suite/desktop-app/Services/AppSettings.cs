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
