using System.Text.Json;
using CommunityToolkit.Mvvm.ComponentModel;

namespace StaffControlSuite.Models;

public partial class Punishment : ObservableObject
{
    [ObservableProperty] private long   _id;
    [ObservableProperty] private string _targetUuid         = "";
    [ObservableProperty] private string _targetName         = "";
    [ObservableProperty] private string _actionType         = "";
    [ObservableProperty] private string _reason             = "";
    [ObservableProperty] private long   _durationSeconds;
    [ObservableProperty] private long   _createdAt;
    [ObservableProperty] private long   _expiresAt;
    [ObservableProperty] private long   _createdByUserId;
    [ObservableProperty] private string _createdByUsername  = "";
    [ObservableProperty] private string _targetServer       = "global";
    [ObservableProperty] private string _evidence           = "";
    [ObservableProperty] private bool   _active;
    [ObservableProperty] private long   _revokedAt;
    [ObservableProperty] private long   _revokedByUserId;
    [ObservableProperty] private string _revokeReason       = "";

    // Computed display properties
    public string StatusDisplay    => Active ? "Active" : (RevokedAt > 0 ? "Revoked" : "Expired");
    public string DurationDisplay  => FormatDuration(DurationSeconds);
    public string CreatedAtDisplay => CreatedAt > 0
        ? DateTimeOffset.FromUnixTimeMilliseconds(CreatedAt).LocalDateTime.ToString("yyyy-MM-dd HH:mm")
        : "";
    public string ExpiresDisplay   => ExpiresAt <= 0 ? "Never"
        : DateTimeOffset.FromUnixTimeMilliseconds(ExpiresAt).LocalDateTime.ToString("yyyy-MM-dd HH:mm");
    public bool   IsPermanent      => DurationSeconds <= 0 &&
        ActionType is "BAN" or "TEMP_BAN" or "IP_BAN" or "TEMP_IP_BAN" or "MUTE" or "TEMP_MUTE";

    public static string FormatDuration(long seconds)
    {
        if (seconds <= 0) return "Permanent";
        var ts = TimeSpan.FromSeconds(seconds);
        if (ts.TotalDays  >= 1) return $"{(int)ts.TotalDays}d {ts.Hours}h";
        if (ts.TotalHours >= 1) return $"{(int)ts.TotalHours}h {ts.Minutes}m";
        return $"{(int)ts.TotalMinutes}m";
    }

    public static Punishment FromJson(JsonElement e) => new()
    {
        Id                = e.TryGetProperty("id",                out var id)   ? id.GetInt64()         : 0,
        TargetUuid        = e.TryGetProperty("targetUuid",        out var tu)   ? tu.GetString()   ?? "" : "",
        TargetName        = e.TryGetProperty("targetName",        out var tn)   ? tn.GetString()   ?? "" : "",
        ActionType        = e.TryGetProperty("actionType",        out var at)   ? at.GetString()   ?? "" : "",
        Reason            = e.TryGetProperty("reason",            out var r)    ? r.GetString()    ?? "" : "",
        DurationSeconds   = e.TryGetProperty("durationSeconds",   out var ds)   ? ds.GetInt64()         : 0,
        CreatedAt         = e.TryGetProperty("createdAt",         out var ca)   ? ca.GetInt64()         : 0,
        ExpiresAt         = e.TryGetProperty("expiresAt",         out var ea)   ? ea.GetInt64()         : 0,
        CreatedByUserId   = e.TryGetProperty("createdByUserId",   out var cbui) ? cbui.GetInt64()       : 0,
        CreatedByUsername = e.TryGetProperty("createdByUsername", out var cbu)  ? cbu.GetString()  ?? "" : "",
        TargetServer      = e.TryGetProperty("targetServer",      out var ts2)  ? ts2.GetString()  ?? "global" : "global",
        Evidence          = e.TryGetProperty("evidence",          out var ev)   ? ev.GetString()   ?? "" : "",
        Active            = e.TryGetProperty("active",            out var ac)   && ac.GetBoolean(),
        RevokedAt         = e.TryGetProperty("revokedAt",         out var ra)   ? ra.GetInt64()         : 0,
        RevokedByUserId   = e.TryGetProperty("revokedByUserId",   out var rbui) ? rbui.GetInt64()       : 0,
        RevokeReason      = e.TryGetProperty("revokeReason",      out var rr)   ? rr.GetString()   ?? "" : "",
    };
}
