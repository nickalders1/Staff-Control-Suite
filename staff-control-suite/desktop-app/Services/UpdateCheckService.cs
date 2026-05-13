using System.Net.Http;
using System.Reflection;
using System.Text.Json;

namespace StaffControlSuite.Services;

public static class UpdateCheckService
{
    // Paste the raw URL of your public GitHub Gist here after creating it.
    // See instructions in build-and-sign.ps1 for how to set this up.
    private const string ManifestUrl = "https://gist.githubusercontent.com/nickalders1/69f959e7e7cf6c6f23bc688c07ebd06a/raw/c958cd73282c845c28014721298d4cd12be5fa53/version.json";

    public record UpdateInfo(bool HasUpdate, string LatestVersion, string DownloadUrl);

    public static async Task<UpdateInfo> CheckAsync()
    {
        if (ManifestUrl == "https://gist.githubusercontent.com/nickalders1/69f959e7e7cf6c6f23bc688c07ebd06a/raw/c958cd73282c845c28014721298d4cd12be5fa53/version.json")
            return new UpdateInfo(false, "", "");

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
