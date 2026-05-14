using System.Text.Json;

namespace StaffControlSuite.Protocol;

public static class MessageTypes
{
    public const string SetupCheck = "setup.check";
    public const string SetupCreate = "setup.create";
    public const string AuthLogin = "auth.login";
    public const string AuthLogout = "auth.logout";
    public const string ServersList = "servers.list";
    public const string ServersAdd = "servers.add";
    public const string ServersRemove = "servers.remove";
    public const string ServersUpdate = "servers.update";
    public const string ConsoleSubscribe = "console.subscribe";
    public const string ConsoleUnsubscribe = "console.unsubscribe";
    public const string ConsoleCommand = "console.command";
    public const string PlayersList = "players.list";
    public const string PlayersHistory = "players.history";
    public const string PlayersDetails = "players.details";
    public const string UsersList = "users.list";
    public const string UsersCreate = "users.create";
    public const string UsersUpdate = "users.update";
    public const string UsersDelete = "users.delete";
    public const string RolesList = "roles.list";
    public const string RolesCreate = "roles.create";
    public const string RolesUpdate = "roles.update";
    public const string RolesDelete = "roles.delete";
    public const string AuditList = "audit.list";
    public const string AgentsList = "agents.list";
    public const string BrandingGet = "branding.get";

    public const string EventConsoleLine = "event.console.line";
    public const string EventServerStatus = "event.server.status";
    public const string EventPlayerUpdate = "event.player.update";
    public const string EventAgentStatus = "event.agent.status";
    public const string Response = "response";

    public const string ModerationPunish        = "moderation.punish";
    public const string ModerationHistory       = "moderation.history";
    public const string ModerationActive        = "moderation.active";
    public const string ModerationRevoke        = "moderation.revoke";
    public const string ModerationNotesList     = "moderation.notes.list";
    public const string ModerationNotesCreate   = "moderation.notes.create";
    public const string ModerationPresetsList   = "moderation.presets.list";
    public const string ModerationPresetsCreate = "moderation.presets.create";
    public const string ModerationPresetsUpdate = "moderation.presets.update";
    public const string ModerationPresetsDelete = "moderation.presets.delete";
    public const string ModerationCalculate     = "moderation.calculate";
    public const string ModerationDashboard     = "moderation.dashboard";
    public const string EventPunishmentCreated  = "event.punishment.created";
    public const string EventPunishmentRevoked  = "event.punishment.revoked";
}

public class WsMessage
{
    public string Type { get; set; } = "";
    public string? RequestId { get; set; }
    public string? Token { get; set; }
    public JsonElement? Payload { get; set; }
    public bool Success { get; set; }
    public string? Error { get; set; }
    public string? Message { get; set; }
}
