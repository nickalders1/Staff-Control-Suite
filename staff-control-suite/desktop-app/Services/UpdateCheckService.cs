using System.Net.Http;
using System.Reflection;
using System.Text.Json;

namespace StaffControlSuite.Services;

public static class UpdateCheckService
{
    // Always-latest raw URL — no commit hash so every Gist update is picked up immediately.
    private const string ManifestUrl = "https://gist.githubusercontent.com/nickalders1/69f959e7e7cf6c6f23bc688c07ebd06a/raw/version.json";

    public record UpdateInfo(bool HasUpdate, string LatestVersion, string DownloadUrl);

    public static async Task<UpdateInfo> CheckAsync()
    {

        try
        {
            using var client = new HttpClient();
            client.Timeout = TimeSpan.FromSeconds(8);
            client.DefaultRequestHeaders.UserAgent.ParseAdd("StaffControlSuite-UpdateCheck");

            var json    = await client.GetStringAsync(ManifestUrl);
            var doc     = JsonDocument.Parse(json);
            var latest  = doc.RootElement.GetProperty("version").GetString()     ?? "";
            var url     = doc.RootElement.GetProperty("downloadUrl").GetString() ?? "";

            var current   = GetCurrentVersion();
            var hasUpdate = Version.TryParse(latest, out var l)
                         && Version.TryParse(current, out var c)
                         && l > c;

            return new UpdateInfo(hasUpdate, latest, url);
        }
        catch
        {
            // Network unavailable or manifest unreachable — silently skip
            return new UpdateInfo(false, "", "");
        }
    }

    public static string GetCurrentVersion()
    {
        var v = Assembly.GetExecutingAssembly().GetName().Version;
        return v != null ? $"{v.Major}.{v.Minor}.{v.Build}" : "0.0.0";
    }
}
