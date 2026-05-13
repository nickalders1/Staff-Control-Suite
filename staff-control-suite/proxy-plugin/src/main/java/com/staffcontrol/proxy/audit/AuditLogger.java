package com.staffcontrol.proxy.audit;

import com.staffcontrol.proxy.database.DatabaseManager;

import java.sql.SQLException;

public class AuditLogger {

    private final DatabaseManager database;

    public AuditLogger(DatabaseManager database) {
        this.database = database;
    }

    public void log(Long userId, String username, String action, String target,
                    String details, String ipAddress) {
        try {
            database.logAudit(userId, username, action, target, details, ipAddress);
        } catch (SQLException e) {
            // Silently fail — audit logging should not crash the server
        }
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
        } catch (SQLException e) {
            // ignore
        }
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
}
