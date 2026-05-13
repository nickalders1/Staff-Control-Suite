using System.Net.Http;
using System.Reflection;
using System.Text.Json;

namespace StaffControlSuite.Services;

public static class UpdateCheckService
{
    // TODO: change to your actual GitHub username/repo after pushing
    private const string GitHubRepo = "Odsko/staff-control-suite";
    private const string ApiUrl     = $"https://api.github.com/repos/{GitHubRepo}/releases/latest";

    public record UpdateInfo(bool HasUpdate, string LatestVersion, string ReleaseUrl);

    public static async Task<UpdateInfo> CheckAsync()
    {
        try
        {
            using var client = new HttpClient();
            client.Timeout = TimeSpan.FromSeconds(8);
            client.DefaultRequestHeaders.UserAgent.ParseAdd("StaffControlSuite-UpdateCheck");

            var json   = await client.GetStringAsync(ApiUrl);
            var doc    = JsonDocument.Parse(json);
            var tag    = doc.RootElement.GetProperty("tag_name").GetString() ?? "";
            var url    = doc.RootElement.GetProperty("html_url").GetString()  ?? "";
            var latest = tag.TrimStart('v');

            var current   = GetCurrentVersion();
            var hasUpdate = Version.TryParse(latest, out var l)
                         && Version.TryParse(current, out var c)
                         && l > c;

            return new UpdateInfo(hasUpdate, latest, url);
        }
        catch
        {
            // Network unavailable or GitHub down — silently skip
            return new UpdateInfo(false, "", "");
        }
    }

    public static string GetCurrentVersion()
    {
        var v = Assembly.GetExecutingAssembly().GetName().Version;
        return v != null ? $"{v.Major}.{v.Minor}.{v.Build}" : "0.0.0";
    }
}
