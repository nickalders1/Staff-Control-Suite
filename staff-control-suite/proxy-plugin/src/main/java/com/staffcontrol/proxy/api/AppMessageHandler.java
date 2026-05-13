package com.staffcontrol.proxy.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.staffcontrol.proxy.agent.AgentConnection;
import com.staffcontrol.proxy.agent.AgentManager;
import com.staffcontrol.proxy.audit.AuditLogger;
import com.staffcontrol.proxy.auth.AuthManager;
import com.staffcontrol.proxy.auth.LoginResult;
import com.staffcontrol.proxy.database.DatabaseManager;
import com.staffcontrol.proxy.model.AuditLog;
import com.staffcontrol.proxy.model.PlayerInfo;
import com.staffcontrol.proxy.model.Role;
import com.staffcontrol.proxy.model.ServerInfo;
import com.staffcontrol.proxy.model.User;
import org.java_websocket.WebSocket;

import java.sql.SQLException;
import java.util.*;
import java.util.logging.Logger;

public class AppMessageHandler {

    private final DatabaseManager database;
    private final AuthManager authManager;
    private final AgentManager agentManager;
    private final AuditLogger auditLogger;
    private final AppWebSocketServer wsServer;
    private final Logger logger;
    public AppMessageHandler(DatabaseManager database, AuthManager authManager,
                              AgentManager agentManager, AuditLogger auditLogger,
                              AppWebSocketServer wsServer, Logger logger) {
        this.database = database;
        this.authManager = authManager;
        this.agentManager = agentManager;
        this.auditLogger = auditLogger;
        this.wsServer = wsServer;
        this.logger = logger;
    }

    public void handle(WebSocket ws, JsonObject message, ClientSession session) {
        String type = message.has("type") ? message.get("type").getAsString() : null;
        String requestId = message.has("requestId") && !message.get("requestId").isJsonNull()
                ? message.get("requestId").getAsString() : null;
        JsonObject payload = message.has("payload") && message.get("payload").isJsonObject()
                ? message.getAsJsonObject("payload") : new JsonObject();

        if (type == null) {
            wsServer.sendError(ws, requestId, "MISSING_TYPE", "Message type is required");
            return;
        }

        try {
            switch (type) {
                case "setup.check"        -> handleSetupCheck(ws, requestId, payload, session);
                case "setup.create"       -> handleSetupCreate(ws, requestId, payload, session);
                case "auth.login"         -> handleAuthLogin(ws, requestId, payload, session);
                case "auth.logout"        -> handleAuthLogout(ws, requestId, payload, session);
                case "servers.list"       -> handleServersList(ws, requestId, payload, session);
                case "servers.add"        -> handleServersAdd(ws, requestId, payload, session);
                case "servers.remove"     -> handleServersRemove(ws, requestId, payload, session);
                case "servers.update"     -> handleServersUpdate(ws, requestId, payload, session);
                case "console.subscribe"  -> handleConsoleSubscribe(ws, requestId, payload, session);
                case "console.unsubscribe"-> handleConsoleUnsubscribe(ws, requestId, payload, session);
                case "console.command"    -> handleConsoleCommand(ws, requestId, payload, session);
                case "players.list"       -> handlePlayersList(ws, requestId, payload, session);
                case "players.details"    -> handlePlayersDetails(ws, requestId, payload, session);
                case "users.list"         -> handleUsersList(ws, requestId, payload, session);
                case "users.create"       -> handleUsersCreate(ws, requestId, payload, session);
                case "users.update"       -> handleUsersUpdate(ws, requestId, payload, session);
                case "users.delete"       -> handleUsersDelete(ws, requestId, payload, session);
                case "roles.list"         -> handleRolesList(ws, requestId, payload, session);
                case "roles.create"       -> handleRolesCreate(ws, requestId, payload, session);
                case "roles.update"       -> handleRolesUpdate(ws, requestId, payload, session);
                case "roles.delete"       -> handleRolesDelete(ws, requestId, payload, session);
                case "audit.list"         -> handleAuditList(ws, requestId, payload, session);
                default -> wsServer.sendError(ws, requestId, "UNKNOWN_TYPE", "Unknown message type: " + type);
            }
        } catch (Exception e) {
            logger.severe("[AppMH] Unhandled error processing " + type + ": " + e.getMessage());
            wsServer.sendError(ws, requestId, "INTERNAL_ERROR", "An internal error occurred");
        }
    }

    // ---- Setup ----

    private void handleSetupCheck(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        JsonObject resp = new JsonObject();
        resp.addProperty("needsSetup", !authManager.ownerExists());
        wsServer.sendResponse(ws, requestId, resp);
    }

    private void handleSetupCreate(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (authManager.ownerExists()) {
            wsServer.sendError(ws, requestId, "SETUP_ALREADY_DONE", "Setup has already been completed");
            return;
        }

        String username = getString(payload, "username");
        String password = getString(payload, "password");

        if (username == null || username.isBlank()) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "Username is required");
            return;
        }
        if (username.length() < 3 || username.length() > 32) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "Username must be 3-32 characters");
            return;
        }
        if (!username.matches("[a-zA-Z0-9_]+")) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "Username may only contain letters, numbers, and underscores");
            return;
        }
        if (password == null || password.length() < 8) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "Password must be at least 8 characters");
            return;
        }

        LoginResult result = authManager.createOwner(username, password);
        if (!result.isSuccess()) {
            wsServer.sendError(ws, requestId, "SETUP_FAILED", result.getErrorMessage());
            return;
        }

        // Create client session
        Set<String> perms = new HashSet<>(result.getPermissions());
        String ip = ws.getRemoteSocketAddress().getAddress().getHostAddress();
        ClientSession newSession = new ClientSession(result.getUserId(), result.getUsername(), perms, result.getToken(), ws, ip);
        wsServer.putSession(ws, newSession);

        auditLogger.log(result.getUserId(), result.getUsername(), "SETUP_CREATE", null, "Initial setup completed", ip);

        JsonObject userObj = buildUserJson(result.getUserId(), result.getUsername(), result.getRoleName(), result.getPermissions());
        JsonObject resp = new JsonObject();
        resp.addProperty("token", result.getToken());
        resp.add("user", userObj);
        wsServer.sendResponse(ws, requestId, resp);
    }

    // ---- Auth ----

    private void handleAuthLogin(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        String username = getString(payload, "username");
        String password = getString(payload, "password");
        String ip = ws.getRemoteSocketAddress().getAddress().getHostAddress();

        if (username == null || password == null) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "Username and password are required");
            return;
        }

        LoginResult result = authManager.login(username, password, ip);

        if (!result.isSuccess()) {
            auditLogger.logFailedLogin(username, ip);
            String errorCode = result.getErrorMessage();
            String msg = switch (errorCode) {
                case "ACCOUNT_DISABLED" -> "Your account has been disabled";
                default -> "Invalid username or password";
            };
            wsServer.sendError(ws, requestId, errorCode, msg);
            return;
        }

        auditLogger.logLogin(result.getUserId(), result.getUsername(), ip);

        Set<String> perms = new HashSet<>(result.getPermissions());
        ClientSession newSession = new ClientSession(result.getUserId(), result.getUsername(), perms, result.getToken(), ws, ip);
        wsServer.putSession(ws, newSession);

        JsonObject userObj = buildUserJson(result.getUserId(), result.getUsername(), result.getRoleName(), result.getPermissions());
        JsonObject resp = new JsonObject();
        resp.addProperty("token", result.getToken());
        resp.add("user", userObj);
        wsServer.sendResponse(ws, requestId, resp);
    }

    private void handleAuthLogout(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (session == null) {
            wsServer.sendError(ws, requestId, "NOT_AUTHENTICATED", "Not authenticated");
            return;
        }

        String ip = ws.getRemoteSocketAddress().getAddress().getHostAddress();
        auditLogger.log(session.getUserId(), session.getUsername(), "AUTH_LOGOUT", null, null, ip);

        authManager.logout(session.getToken());
        wsServer.removeSession(ws);

        wsServer.sendResponse(ws, requestId, new JsonObject());
    }

    // ---- Servers ----

    private void handleServersList(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (!requireAuth(ws, requestId, session)) return;
        if (!requirePermission(ws, requestId, session, "servers.view")) return;

        try {
            List<Map<String, Object>> rows = database.getAllServers();
            JsonArray servers = new JsonArray();
            for (Map<String, Object> row : rows) {
                ServerInfo info = ServerInfo.fromDatabase(row);
                Optional<AgentConnection> agent = agentManager.getAgent(info.getServerId());
                if (agent.isPresent() && agent.get().isRegistered() && agent.get().isConnected()) {
                    AgentConnection ac = agent.get();
                    ServerInfo live = ac.getServerInfo();
                    if (live != null) {
                        info.setOnline(true);
                        info.setTps(live.getTps());
                        info.setMspt(live.getMspt());
                        info.setPlayerCount(live.getPlayerCount());
                        info.setMaxPlayers(live.getMaxPlayers());
                        info.setLastHeartbeat(live.getLastHeartbeat());
                    }
                }
                servers.add(info.toJson());
            }
            JsonObject resp = new JsonObject();
            resp.add("servers", servers);
            wsServer.sendResponse(ws, requestId, resp);
        } catch (SQLException e) {
            wsServer.sendError(ws, requestId, "DATABASE_ERROR", "Failed to retrieve servers");
        }
    }

    private void handleServersAdd(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (!requireAuth(ws, requestId, session)) return;
        if (!requirePermission(ws, requestId, session, "servers.manage")) return;

        String serverId = getString(payload, "serverId");
        String serverName = getString(payload, "serverName");
        String serverType = getString(payload, "serverType");

        if (serverId == null || !serverId.matches("[a-zA-Z0-9_\\-]{2,32}")) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "serverId must be 2-32 alphanumeric/dash/underscore characters");
            return;
        }
        if (serverName == null || serverName.isBlank()) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "serverName is required");
            return;
        }
        if (serverType == null || serverType.isBlank()) {
            serverType = "generic";
        }

        String agentToken = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        try {
            database.addServer(serverId, serverName, serverType, agentToken, now);
            Map<String, Object> row = database.getServer(serverId);
            ServerInfo info = ServerInfo.fromDatabase(row);

            String ip = ws.getRemoteSocketAddress().getAddress().getHostAddress();
            auditLogger.logServerAction(session.getUserId(), session.getUsername(), "SERVER_ADD", serverId, ip);

            // Include agentToken in response for display purposes
            JsonObject serverJson = info.toJson();
            serverJson.addProperty("agentToken", agentToken);

            JsonObject resp = new JsonObject();
            resp.add("server", serverJson);
            wsServer.sendResponse(ws, requestId, resp);
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                wsServer.sendError(ws, requestId, "SERVER_EXISTS", "A server with that ID already exists");
            } else {
                wsServer.sendError(ws, requestId, "DATABASE_ERROR", "Failed to add server");
            }
        }
    }

    private void handleServersRemove(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (!requireAuth(ws, requestId, session)) return;
        if (!requirePermission(ws, requestId, session, "servers.manage")) return;

        String serverId = getString(payload, "serverId");
        if (serverId == null || serverId.isBlank()) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "serverId is required");
            return;
        }

        try {
            // Disconnect agent if connected
            agentManager.getAgent(serverId).ifPresent(agent -> {
                if (agent.isConnected()) {
                    agent.getConnection().close();
                }
                agentManager.removeAgent(serverId);
            });

            database.deleteServer(serverId);

            String ip = ws.getRemoteSocketAddress().getAddress().getHostAddress();
            auditLogger.logServerAction(session.getUserId(), session.getUsername(), "SERVER_REMOVE", serverId, ip);

            wsServer.sendResponse(ws, requestId, new JsonObject());
        } catch (SQLException e) {
            wsServer.sendError(ws, requestId, "DATABASE_ERROR", "Failed to remove server");
        }
    }

    private void handleServersUpdate(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (!requireAuth(ws, requestId, session)) return;
        if (!requirePermission(ws, requestId, session, "servers.manage")) return;

        String serverId = getString(payload, "serverId");
        String serverName = getString(payload, "serverName");
        String serverType = getString(payload, "serverType");

        if (serverId == null || serverId.isBlank()) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "serverId is required");
            return;
        }

        try {
            Map<String, Object> existing = database.getServer(serverId);
            if (existing == null) {
                wsServer.sendError(ws, requestId, "NOT_FOUND", "Server not found");
                return;
            }

            long id = getLong(existing, "id");
            String newName = serverName != null ? serverName : getString(existing, "server_name");
            String newType = serverType != null ? serverType : getString(existing, "server_type");
            long now = System.currentTimeMillis();

            database.updateServer(id, serverId, newName, newType, now);

            Map<String, Object> updated = database.getServer(serverId);
            ServerInfo info = ServerInfo.fromDatabase(updated);

            String ip = ws.getRemoteSocketAddress().getAddress().getHostAddress();
            auditLogger.logServerAction(session.getUserId(), session.getUsername(), "SERVER_UPDATE", serverId, ip);

            JsonObject resp = new JsonObject();
            resp.add("server", info.toJson());
            wsServer.sendResponse(ws, requestId, resp);
        } catch (SQLException e) {
            wsServer.sendError(ws, requestId, "DATABASE_ERROR", "Failed to update server");
        }
    }

    // ---- Console ----

    private void handleConsoleSubscribe(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (!requireAuth(ws, requestId, session)) return;
        if (!requirePermission(ws, requestId, session, "console.view")) return;

        String serverId = getString(payload, "serverId");
        if (serverId == null || serverId.isBlank()) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "serverId is required");
            return;
        }

        session.subscribe(serverId);
        wsServer.sendResponse(ws, requestId, new JsonObject());
    }

    private void handleConsoleUnsubscribe(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (session == null) {
            wsServer.sendError(ws, requestId, "NOT_AUTHENTICATED", "Not authenticated");
            return;
        }

        String serverId = getString(payload, "serverId");
        if (serverId != null) {
            session.unsubscribe(serverId);
        }
        wsServer.sendResponse(ws, requestId, new JsonObject());
    }

    private void handleConsoleCommand(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (!requireAuth(ws, requestId, session)) return;
        if (!requirePermission(ws, requestId, session, "console.command")) return;

        String serverId = getString(payload, "serverId");
        String command = getString(payload, "command");

        if (serverId == null || serverId.isBlank()) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "serverId is required");
            return;
        }
        if (command == null || command.isBlank()) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "command is required");
            return;
        }

        if (!agentManager.isAgentOnline(serverId)) {
            wsServer.sendError(ws, requestId, "SERVER_OFFLINE", "The server is offline or the agent is not connected");
            return;
        }

        boolean sent = agentManager.sendCommand(serverId, command);
        if (!sent) {
            wsServer.sendError(ws, requestId, "SERVER_OFFLINE", "Failed to send command — server offline");
            return;
        }

        String ip = ws.getRemoteSocketAddress().getAddress().getHostAddress();
        auditLogger.logCommand(session.getUserId(), session.getUsername(), serverId, command, ip);

        wsServer.sendResponse(ws, requestId, new JsonObject());
    }

    // ---- Players ----

    private void handlePlayersList(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (!requireAuth(ws, requestId, session)) return;
        if (!requirePermission(ws, requestId, session, "players.view")) return;

        try {
            List<Map<String, Object>> rows = database.getAllOnlinePlayers();
            JsonArray players = new JsonArray();
            for (Map<String, Object> row : rows) {
                players.add(PlayerInfo.fromDatabase(row).toJson());
            }
            JsonObject resp = new JsonObject();
            resp.add("players", players);
            wsServer.sendResponse(ws, requestId, resp);
        } catch (SQLException e) {
            wsServer.sendError(ws, requestId, "DATABASE_ERROR", "Failed to retrieve players");
        }
    }

    private void handlePlayersDetails(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (!requireAuth(ws, requestId, session)) return;
        if (!requirePermission(ws, requestId, session, "players.details")) return;

        String uuid = getString(payload, "uuid");
        if (uuid == null || uuid.isBlank()) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "uuid is required");
            return;
        }

        try {
            Map<String, Object> row = database.getPlayer(uuid);
            if (row == null) {
                wsServer.sendError(ws, requestId, "NOT_FOUND", "Player not found");
                return;
            }
            JsonObject resp = new JsonObject();
            resp.add("player", PlayerInfo.fromDatabase(row).toJson());
            wsServer.sendResponse(ws, requestId, resp);
        } catch (SQLException e) {
            wsServer.sendError(ws, requestId, "DATABASE_ERROR", "Failed to retrieve player");
        }
    }

    // ---- Users ----

    private void handleUsersList(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (!requireAuth(ws, requestId, session)) return;
        if (!requirePermission(ws, requestId, session, "users.view")) return;

        try {
            List<Map<String, Object>> rows = database.getAllUsers();
            JsonArray users = new JsonArray();
            for (Map<String, Object> row : rows) {
                long userId = getLong(row, "id");
                long roleId = getLong(row, "role_id");
                String username = getString(row, "username");
                String roleName = getString(row, "role_name");
                boolean isActive = getInt(row, "is_active") == 1;
                long createdAt = getLong(row, "created_at");

                List<String> perms = roleId > 0 ? database.getPermissionsForRole(roleId) : new ArrayList<>();
                User u = new User(userId, username, null, roleId, roleName, perms, isActive, createdAt);
                users.add(u.toJson());
            }
            JsonObject resp = new JsonObject();
            resp.add("users", users);
            wsServer.sendResponse(ws, requestId, resp);
        } catch (SQLException e) {
            wsServer.sendError(ws, requestId, "DATABASE_ERROR", "Failed to retrieve users");
        }
    }

    private void handleUsersCreate(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (!requireAuth(ws, requestId, session)) return;
        if (!requirePermission(ws, requestId, session, "users.manage")) return;

        String username = getString(payload, "username");
        String password = getString(payload, "password");
        long roleId = getLongFromJson(payload, "roleId");

        if (username == null || username.isBlank() || username.length() < 3 || username.length() > 32) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "Username must be 3-32 characters");
            return;
        }
        if (!username.matches("[a-zA-Z0-9_]+")) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "Username may only contain letters, numbers, and underscores");
            return;
        }
        if (password == null || password.length() < 8) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "Password must be at least 8 characters");
            return;
        }
        if (roleId <= 0) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "Valid roleId is required");
            return;
        }

        try {
            // Prevent creating owner-level user unless session is owner
            Map<String, Object> targetRole = database.getRoleById(roleId);
            if (targetRole == null) {
                wsServer.sendError(ws, requestId, "NOT_FOUND", "Role not found");
                return;
            }
            String targetRoleName = getString(targetRole, "name");
            if ("owner".equals(targetRoleName) && !session.isOwner()) {
                wsServer.sendError(ws, requestId, "FORBIDDEN", "Only owners can create owner accounts");
                return;
            }

            long newUserId = authManager.createUser(username, password, roleId);
            if (newUserId < 0) {
                wsServer.sendError(ws, requestId, "CREATE_FAILED", "Failed to create user");
                return;
            }

            List<String> perms = database.getPermissionsForRole(roleId);
            String roleName = getString(targetRole, "name");
            long now = System.currentTimeMillis();
            User u = new User(newUserId, username, null, roleId, roleName, perms, true, now);

            String ip = ws.getRemoteSocketAddress().getAddress().getHostAddress();
            auditLogger.logUserAction(session.getUserId(), session.getUsername(), "USER_CREATE", username, ip);

            JsonObject resp = new JsonObject();
            resp.add("user", u.toJson());
            wsServer.sendResponse(ws, requestId, resp);
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                wsServer.sendError(ws, requestId, "USERNAME_TAKEN", "That username is already taken");
            } else {
                wsServer.sendError(ws, requestId, "DATABASE_ERROR", "Failed to create user");
            }
        }
    }

    private void handleUsersUpdate(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (!requireAuth(ws, requestId, session)) return;
        if (!requirePermission(ws, requestId, session, "users.manage")) return;

        long targetUserId = getLongFromJson(payload, "userId");
        String username = getString(payload, "username");
        long roleId = getLongFromJson(payload, "roleId");
        Boolean isActive = payload.has("isActive") ? payload.get("isActive").getAsBoolean() : null;

        if (targetUserId <= 0) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "Valid userId is required");
            return;
        }

        try {
            Map<String, Object> existingUser = database.getUserById(targetUserId);
            if (existingUser == null) {
                wsServer.sendError(ws, requestId, "NOT_FOUND", "User not found");
                return;
            }

            // Check if target is owner — only owner can change owner's role
            long targetRoleId = database.getUserRoleId(targetUserId);
            Map<String, Object> targetCurrentRole = targetRoleId > 0 ? database.getRoleById(targetRoleId) : null;
            boolean targetIsOwner = targetCurrentRole != null && "owner".equals(getString(targetCurrentRole, "name"));

            if (targetIsOwner && !session.isOwner()) {
                wsServer.sendError(ws, requestId, "FORBIDDEN", "Only owners can modify owner accounts");
                return;
            }

            // If changing role to owner, only owner can do it
            if (roleId > 0) {
                Map<String, Object> newRole = database.getRoleById(roleId);
                if (newRole != null && "owner".equals(getString(newRole, "name")) && !session.isOwner()) {
                    wsServer.sendError(ws, requestId, "FORBIDDEN", "Only owners can assign owner role");
                    return;
                }
            }

            String newUsername = username != null ? username : getString(existingUser, "username");
            boolean newActive = isActive != null ? isActive : (getInt(existingUser, "is_active") == 1);
            long now = System.currentTimeMillis();

            database.updateUser(targetUserId, newUsername, newActive, now);

            if (roleId > 0) {
                database.setUserRole(targetUserId, roleId);
            }

            long effectiveRoleId = roleId > 0 ? roleId : targetRoleId;
            Map<String, Object> role = effectiveRoleId > 0 ? database.getRoleById(effectiveRoleId) : null;
            String roleName = role != null ? getString(role, "name") : "";
            List<String> perms = effectiveRoleId > 0 ? database.getPermissionsForRole(effectiveRoleId) : new ArrayList<>();
            long createdAt = getLong(existingUser, "created_at");

            User u = new User(targetUserId, newUsername, null, effectiveRoleId, roleName, perms, newActive, createdAt);

            String ip = ws.getRemoteSocketAddress().getAddress().getHostAddress();
            auditLogger.logUserAction(session.getUserId(), session.getUsername(), "USER_UPDATE", newUsername, ip);

            JsonObject resp = new JsonObject();
            resp.add("user", u.toJson());
            wsServer.sendResponse(ws, requestId, resp);
        } catch (SQLException e) {
            wsServer.sendError(ws, requestId, "DATABASE_ERROR", "Failed to update user");
        }
    }

    private void handleUsersDelete(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (!requireAuth(ws, requestId, session)) return;
        if (!requirePermission(ws, requestId, session, "users.manage")) return;

        long targetUserId = getLongFromJson(payload, "userId");
        if (targetUserId <= 0) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "Valid userId is required");
            return;
        }

        // Cannot delete yourself
        if (targetUserId == session.getUserId()) {
            wsServer.sendError(ws, requestId, "FORBIDDEN", "You cannot delete your own account");
            return;
        }

        try {
            Map<String, Object> existingUser = database.getUserById(targetUserId);
            if (existingUser == null) {
                wsServer.sendError(ws, requestId, "NOT_FOUND", "User not found");
                return;
            }

            long targetRoleId = database.getUserRoleId(targetUserId);
            Map<String, Object> targetRole = targetRoleId > 0 ? database.getRoleById(targetRoleId) : null;
            boolean targetIsOwner = targetRole != null && "owner".equals(getString(targetRole, "name"));

            if (targetIsOwner && !session.isOwner()) {
                wsServer.sendError(ws, requestId, "FORBIDDEN", "Only owners can delete owner accounts");
                return;
            }

            String targetUsername = getString(existingUser, "username");
            database.deleteUser(targetUserId);

            String ip = ws.getRemoteSocketAddress().getAddress().getHostAddress();
            auditLogger.logUserAction(session.getUserId(), session.getUsername(), "USER_DELETE", targetUsername, ip);

            wsServer.sendResponse(ws, requestId, new JsonObject());
        } catch (SQLException e) {
            wsServer.sendError(ws, requestId, "DATABASE_ERROR", "Failed to delete user");
        }
    }

    // ---- Roles ----

    private void handleRolesList(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (!requireAuth(ws, requestId, session)) return;
        if (!requirePermission(ws, requestId, session, "roles.view")) return;

        try {
            List<Map<String, Object>> rows = database.getAllRoles();
            JsonArray roles = new JsonArray();
            for (Map<String, Object> row : rows) {
                long roleId = getLong(row, "id");
                List<String> perms = database.getPermissionsForRole(roleId);
                Role r = new Role(
                    roleId,
                    getString(row, "name"),
                    getString(row, "display_name"),
                    perms,
                    getInt(row, "is_system_role") == 1,
                    getLong(row, "created_at")
                );
                roles.add(r.toJson());
            }
            JsonObject resp = new JsonObject();
            resp.add("roles", roles);
            wsServer.sendResponse(ws, requestId, resp);
        } catch (SQLException e) {
            wsServer.sendError(ws, requestId, "DATABASE_ERROR", "Failed to retrieve roles");
        }
    }

    private void handleRolesCreate(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (!requireAuth(ws, requestId, session)) return;
        if (!requirePermission(ws, requestId, session, "roles.manage")) return;
        if (!session.isOwner()) {
            wsServer.sendError(ws, requestId, "FORBIDDEN", "Only owners can create roles");
            return;
        }

        String name = getString(payload, "name");
        String displayName = getString(payload, "displayName");

        if (name == null || name.isBlank() || name.length() < 2 || name.length() > 32) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "Role name must be 2-32 characters");
            return;
        }
        if (!name.matches("[a-zA-Z0-9_]+")) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "Role name may only contain letters, numbers, and underscores");
            return;
        }
        if (displayName == null || displayName.isBlank()) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "Display name is required");
            return;
        }

        List<String> permissions = new ArrayList<>();
        if (payload.has("permissions") && payload.get("permissions").isJsonArray()) {
            for (JsonElement el : payload.getAsJsonArray("permissions")) {
                permissions.add(el.getAsString());
            }
        }

        try {
            long now = System.currentTimeMillis();
            long roleId = database.createRole(name, displayName, false, now);
            if (roleId < 0) {
                wsServer.sendError(ws, requestId, "CREATE_FAILED", "Failed to create role");
                return;
            }
            if (!permissions.isEmpty()) {
                database.setPermissionsForRole(roleId, permissions);
            }

            Role r = new Role(roleId, name, displayName, permissions, false, now);

            String ip = ws.getRemoteSocketAddress().getAddress().getHostAddress();
            auditLogger.logRoleAction(session.getUserId(), session.getUsername(), "ROLE_CREATE", name, ip);

            JsonObject resp = new JsonObject();
            resp.add("role", r.toJson());
            wsServer.sendResponse(ws, requestId, resp);
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                wsServer.sendError(ws, requestId, "ROLE_EXISTS", "A role with that name already exists");
            } else {
                wsServer.sendError(ws, requestId, "DATABASE_ERROR", "Failed to create role");
            }
        }
    }

    private void handleRolesUpdate(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (!requireAuth(ws, requestId, session)) return;
        if (!requirePermission(ws, requestId, session, "roles.manage")) return;

        long roleId = getLongFromJson(payload, "roleId");
        String displayName = getString(payload, "displayName");

        if (roleId <= 0) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "Valid roleId is required");
            return;
        }

        try {
            Map<String, Object> existingRole = database.getRoleById(roleId);
            if (existingRole == null) {
                wsServer.sendError(ws, requestId, "NOT_FOUND", "Role not found");
                return;
            }

            boolean isSystemRole = getInt(existingRole, "is_system_role") == 1;
            // Only owner can update system role permissions
            if (isSystemRole && !session.isOwner()) {
                wsServer.sendError(ws, requestId, "FORBIDDEN", "Only owners can modify system roles");
                return;
            }

            String newDisplayName = displayName != null ? displayName : getString(existingRole, "display_name");
            database.updateRole(roleId, newDisplayName);

            List<String> permissions = null;
            if (payload.has("permissions") && payload.get("permissions").isJsonArray()) {
                permissions = new ArrayList<>();
                for (JsonElement el : payload.getAsJsonArray("permissions")) {
                    permissions.add(el.getAsString());
                }
                database.setPermissionsForRole(roleId, permissions);
            }

            List<String> currentPerms = database.getPermissionsForRole(roleId);
            Role r = new Role(
                roleId,
                getString(existingRole, "name"),
                newDisplayName,
                currentPerms,
                isSystemRole,
                getLong(existingRole, "created_at")
            );

            String ip = ws.getRemoteSocketAddress().getAddress().getHostAddress();
            auditLogger.logRoleAction(session.getUserId(), session.getUsername(), "ROLE_UPDATE", getString(existingRole, "name"), ip);

            JsonObject resp = new JsonObject();
            resp.add("role", r.toJson());
            wsServer.sendResponse(ws, requestId, resp);
        } catch (SQLException e) {
            wsServer.sendError(ws, requestId, "DATABASE_ERROR", "Failed to update role");
        }
    }

    private void handleRolesDelete(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (!requireAuth(ws, requestId, session)) return;
        if (!requirePermission(ws, requestId, session, "roles.manage")) return;
        if (!session.isOwner()) {
            wsServer.sendError(ws, requestId, "FORBIDDEN", "Only owners can delete roles");
            return;
        }

        long roleId = getLongFromJson(payload, "roleId");
        if (roleId <= 0) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "Valid roleId is required");
            return;
        }

        try {
            Map<String, Object> existingRole = database.getRoleById(roleId);
            if (existingRole == null) {
                wsServer.sendError(ws, requestId, "NOT_FOUND", "Role not found");
                return;
            }

            if (getInt(existingRole, "is_system_role") == 1) {
                wsServer.sendError(ws, requestId, "FORBIDDEN", "Cannot delete system roles");
                return;
            }

            String roleName = getString(existingRole, "name");
            database.deleteRole(roleId);

            String ip = ws.getRemoteSocketAddress().getAddress().getHostAddress();
            auditLogger.logRoleAction(session.getUserId(), session.getUsername(), "ROLE_DELETE", roleName, ip);

            wsServer.sendResponse(ws, requestId, new JsonObject());
        } catch (SQLException e) {
            wsServer.sendError(ws, requestId, "DATABASE_ERROR", "Failed to delete role");
        }
    }

    // ---- Audit ----

    private void handleAuditList(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (!requireAuth(ws, requestId, session)) return;
        if (!requirePermission(ws, requestId, session, "audit.view")) return;

        int page = payload.has("page") ? payload.get("page").getAsInt() : 1;
        int limit = payload.has("limit") ? payload.get("limit").getAsInt() : 50;
        if (page < 1) page = 1;
        if (limit < 1 || limit > 200) limit = 50;

        try {
            List<Map<String, Object>> rows = database.getAuditLogs(page, limit);
            int total = database.getAuditLogsTotal();

            JsonArray logs = new JsonArray();
            for (Map<String, Object> row : rows) {
                Object userIdObj = row.get("user_id");
                Long userId = userIdObj instanceof Number ? ((Number) userIdObj).longValue() : null;
                AuditLog log = new AuditLog(
                    getLong(row, "id"),
                    userId,
                    getString(row, "username"),
                    getString(row, "action"),
                    getString(row, "target"),
                    getString(row, "details"),
                    getString(row, "ip_address"),
                    getLong(row, "timestamp")
                );
                logs.add(log.toJson());
            }

            JsonObject resp = new JsonObject();
            resp.add("logs", logs);
            resp.addProperty("total", total);
            wsServer.sendResponse(ws, requestId, resp);
        } catch (SQLException e) {
            wsServer.sendError(ws, requestId, "DATABASE_ERROR", "Failed to retrieve audit logs");
        }
    }

    // ---- Helpers ----

    private boolean requireAuth(WebSocket ws, String requestId, ClientSession session) {
        if (session == null) {
            wsServer.sendError(ws, requestId, "NOT_AUTHENTICATED", "You must be logged in");
            return false;
        }
        return true;
    }

    private boolean requirePermission(WebSocket ws, String requestId, ClientSession session, String node) {
        if (!session.hasPermission(node)) {
            wsServer.sendError(ws, requestId, "FORBIDDEN", "You do not have permission: " + node);
            return false;
        }
        return true;
    }

    private JsonObject buildUserJson(long userId, String username, String roleName, List<String> permissions) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", userId);
        obj.addProperty("username", username);
        obj.addProperty("roleName", roleName != null ? roleName : "");
        obj.addProperty("isActive", true);
        JsonArray permsArray = new JsonArray();
        if (permissions != null) {
            for (String p : permissions) permsArray.add(p);
        }
        obj.add("permissions", permsArray);
        return obj;
    }

    private String getString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return null;
    }

    private long getLongFromJson(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            try { return obj.get(key).getAsLong(); } catch (Exception e) { return -1L; }
        }
        return -1L;
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : null;
    }

    private long getLong(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).longValue();
        return 0L;
    }

    private int getInt(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        return 0;
    }
}
