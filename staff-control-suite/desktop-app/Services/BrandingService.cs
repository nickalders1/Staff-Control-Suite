using System.IO;
using System.Text.Json;
using StaffControlSuite.Protocol;

namespace StaffControlSuite.Services;

public class BrandingService
{
    /// <summary>
    /// Fetches branding from the proxy. Throws on network/protocol errors so callers
    /// can surface failures in the UI. Logo-file errors are silently skipped (non-critical).
    /// </summary>
    public async Task FetchFromProxyAsync()
    {
        var result = await App.WebSocketService.SendRequestAsync(
            MessageTypes.BrandingGet, null, App.AuthService.SessionToken);

        if (result.ValueKind == JsonValueKind.Undefined) return;

        if (result.TryGetProperty("appTitle", out var t) && !string.IsNullOrWhiteSpace(t.GetString()))
            AppSettings.Current.SidebarTitle = t.GetString()!;

        if (result.TryGetProperty("networkName", out var nn) && !string.IsNullOrWhiteSpace(nn.GetString()))
            AppSettings.Current.SidebarSubtitle = nn.GetString()!;

        if (result.TryGetProperty("accentColor", out var ac) && !string.IsNullOrWhiteSpace(ac.GetString()))
            AppSettings.Current.AccentColor = ac.GetString()!;

        if (result.TryGetProperty("logoBase64", out var lb64) &&
            result.TryGetProperty("logoMimeType", out var lmt))
        {
            try
            {
                var base64 = lb64.GetString();
                var mime   = lmt.GetString() ?? "";
                if (!string.IsNullOrEmpty(base64))
                {
                    string ext      = mime.Contains("png") ? "png" : mime.Contains("jpeg") ? "jpg" : "png";
                    string logoPath = Path.Combine(AppSettings.SettingsDirectory, $"logo.{ext}");
                    Directory.CreateDirectory(AppSettings.SettingsDirectory);
                    File.WriteAllBytes(logoPath, Convert.FromBase64String(base64));
                    AppSettings.Current.LogoImagePath = logoPath;
                }
            }
            catch { /* logo is non-critical, silently skip */ }
        }

        AppSettings.Save();
    }
}
