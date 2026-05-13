namespace StaffControlSuite.Models;

public class Permission
{
    public string Node { get; set; } = "";
    public string DisplayName { get; set; } = "";

    public Permission() { }

    public Permission(string node, string displayName)
    {
        Node = node;
        DisplayName = displayName;
    }

    public static IReadOnlyList<Permission> AllPermissions { get; } = new List<Permission>
    {
        // Servers
        new("servers.view",    "View Servers"),
        new("servers.manage",  "Manage Servers"),
        // Console
        new("console.view",    "View Console"),
        new("console.command", "Send Console Commands"),
        // Players
        new("players.view",              "View Players"),
        new("players.details",           "View Player Details"),
        new("players.punishments.view",  "View Player Punishments"),
        new("players.punishments.create","Create Player Punishments"),
        // Users & Roles
        new("users.view",      "View Users"),
        new("users.manage",    "Manage Users"),
        new("roles.view",      "View Roles"),
        new("roles.manage",    "Manage Roles"),
        // Settings & Audit
        new("settings.view",   "View Settings"),
        new("settings.manage", "Manage Settings"),
        new("audit.view",      "View Audit Logs"),
        // Moderation
        new("moderation.view",              "View Moderation Records"),
        new("moderation.warn",              "Issue Warnings"),
        new("moderation.mute",              "Mute Players"),
        new("moderation.unmute",            "Unmute Players"),
        new("moderation.kick",              "Kick Players"),
        new("moderation.ban",               "Permanently Ban Players"),
        new("moderation.tempban",           "Temporarily Ban Players"),
        new("moderation.unban",             "Revoke Bans"),
        new("moderation.ipban",             "IP-Ban Players"),
        new("moderation.notes.view",        "View Player Notes"),
        new("moderation.notes.create",      "Create Player Notes"),
        new("moderation.presets.view",      "View Punishment Presets"),
        new("moderation.presets.manage",    "Manage Punishment Presets"),
        new("moderation.override_duration", "Override Punishment Duration"),
        new("moderation.override_reason",   "Override Punishment Reason"),
        new("moderation.exempt",            "Exempt from Punishment"),
        new("moderation.view_ip",           "View IP Hashes"),
    };
}
