package com.staffcontrol.proxy.audit;

import com.staffcontrol.proxy.database.DatabaseManager;

import java.sql.SQLException;

public class AuditLogger {

    // Moderation action constants
    public static final String MODERATION_WARN           = "MODERATION_WARN";
    public static final String MODERATION_MUTE           = "MODERATION_MUTE";
    public static final String MODERATION_UNMUTE         = "MODERATION_UNMUTE";
    public static final String MODERATION_KICK           = "MODERATION_KICK";
    public static final String MODERATION_BAN            = "MODERATION_BAN";
    public static final String MODERATION_TEMPBAN        = "MODERATION_TEMPBAN";
    public static final String MODERATION_UNBAN          = "MODERATION_UNBAN";
    public static final String MODERATION_IPBAN          = "MODERATION_IPBAN";
    public static final String MODERATION_UNBAN_IP       = "MODERATION_UNBAN_IP";
    public static final String MODERATION_NOTE_CREATE    = "MODERATION_NOTE_CREATE";
    public static final String MODERATION_PRESET_CREATE  = "MODERATION_PRESET_CREATE";
    public static final String MODERATION_PRESET_UPDATE  = "MODERATION_PRESET_UPDATE";
    public static final String MODERATION_PRESET_DELETE  = "MODERATION_PRESET_DELETE";

    private final DatabaseManager database;

    public AuditLogger(DatabaseManager database) {
        this.database = database;
    }

    public void log(Long userId, String username, String action, String target,
                    String details, String ipAddress) {
        try {
            database.logAudit(userId, username, action, target, details, ipAddress);
        } catch (SQLException ignored) {}
    }

    public void logLogin(long userId, String username, String ipAddress) {
        log(userId, username, "AUTH_LOGIN", null, "Successful login", ipAddress);
    }

    public void logFailedLogin(String username, String ipAddress) {
        log(null, username, "AUTH_LOGIN_FAILED", null, "Failed login attempt", ipAddress);
    }

    public void logCommand(long userId, String username, String serverId,
                           String command, String ipAddress) {
        log(userId, username, "CONSOLE_COMMAND", serverId, "Command: " + command, ipAddress);
        try {
            database.logCommand(userId, username, serverId, command);
        } catch (SQLException ignored) {}
    }

    public void logServerAction(long userId, String username, String action,
                                String serverId, String ipAddress) {
        log(userId, username, action, serverId, null, ipAddress);
    }

    public void logUserAction(long userId, String username, String action,
                              String targetUser, String ipAddress) {
        log(userId, username, action, targetUser, null, ipAddress);
    }

    public void logRoleAction(long userId, String username, String action,
                              String roleName, String ipAddress) {
        log(userId, username, action, roleName, null, ipAddress);
    }

    public void logModerationAction(long userId, String username, String action,
                                    String targetName, String details, String ipAddress) {
        log(userId, username, action, targetName, details, ipAddress);
    }

    public void logAgentConnect(String serverId) {
        log(null, "system", "AGENT_CONNECT", serverId, null, null);
    }

    public void logAgentDisconnect(String serverId, String reason) {
        log(null, "system", "AGENT_DISCONNECT", serverId, reason, null);
    }

    public void logAgentTimeout(String serverId) {
        log(null, "system", "AGENT_TIMEOUT", serverId, "No heartbeat received within timeout period", null);
    }
}
