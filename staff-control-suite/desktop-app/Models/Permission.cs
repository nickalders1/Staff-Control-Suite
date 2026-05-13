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
        new("servers.view",    "View Servers"),
        new("servers.manage",  "Manage Servers"),
        new("console.view",    "View Console"),
        new("console.command", "Send Console Commands"),
        new("players.view",    "View Players"),
        new("players.manage",  "Manage Players"),
        new("users.view",      "View Users"),
        new("users.manage",    "Manage Users"),
        new("roles.view",      "View Roles"),
        new("roles.manage",    "Manage Roles"),
        new("settings.view",   "View Settings"),
        new("settings.manage", "Manage Settings"),
        new("audit.view",      "View Audit Logs"),
        new("punishments.view",   "View Punishments"),
        new("punishments.manage", "Manage Punishments"),
    };
}
