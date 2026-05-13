using System.Text.Json;

namespace StaffControlSuite.Models;

public class PlayerNote
{
    public long   Id                  { get; set; }
    public string TargetUuid          { get; set; } = "";
    public string TargetName          { get; set; } = "";
    public string Note                { get; set; } = "";
    public long   CreatedAt           { get; set; }
    public long   CreatedByUserId     { get; set; }
    public string CreatedByUsername   { get; set; } = "";
    public string Visibility          { get; set; } = "staff";

    public string CreatedAtDisplay => CreatedAt > 0
        ? DateTimeOffset.FromUnixTimeMilliseconds(CreatedAt).LocalDateTime.ToString("yyyy-MM-dd HH:mm")
        : "";

    public static PlayerNote FromJson(JsonElement e) => new()
    {
        Id                = e.TryGetProperty("id",                out var id)   ? id.GetInt64()        : 0,
        TargetUuid        = e.TryGetProperty("targetUuid",        out var tu)   ? tu.GetString()  ?? "" : "",
        TargetName        = e.TryGetProperty("targetName",        out var tn)   ? tn.GetString()  ?? "" : "",
        Note              = e.TryGetProperty("note",              out var no)   ? no.GetString()  ?? "" : "",
        CreatedAt         = e.TryGetProperty("createdAt",         out var ca)   ? ca.GetInt64()        : 0,
        CreatedByUserId   = e.TryGetProperty("createdByUserId",   out var cbui) ? cbui.GetInt64()      : 0,
        CreatedByUsername = e.TryGetProperty("createdByUsername", out var cbu)  ? cbu.GetString() ?? "" : "",
        Visibility        = e.TryGetProperty("visibility",        out var vis)  ? vis.GetString() ?? "staff" : "staff",
    };
}
