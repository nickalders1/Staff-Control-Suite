package com.staffcontrol.proxy.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.staffcontrol.proxy.agent.AgentConnection;
import com.staffcontrol.proxy.agent.AgentManager;
import com.staffcontrol.proxy.audit.AuditLogger;
import com.staffcontrol.proxy.auth.AuthManager;
import com.staffcontrol.proxy.auth.LoginResult;
import com.staffcontrol.proxy.config.ProxyConfig;
import com.staffcontrol.proxy.database.DatabaseManager;
import com.staffcontrol.proxy.model.AuditLog;
import com.staffcontrol.proxy.model.PlayerInfo;
import com.staffcontrol.proxy.model.Role;
import com.staffcontrol.proxy.model.ServerInfo;
import com.staffcontrol.proxy.model.User;
import com.staffcontrol.proxy.model.PlayerNote;
import com.staffcontrol.proxy.model.Punishment;
import com.staffcontrol.proxy.model.PunishmentPreset;
import com.staffcontrol.proxy.moderation.ModerationManager;
import com.staffcontrol.proxy.permission.PermissionCache;
import com.staffcontrol.proxy.player.PlayerManager;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import org.java_websocket.WebSocket;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Base64;
import java.util.logging.Logger;

public class AppMessageHandler {

    private final DatabaseManager database;
    private final AuthManager authManager;
    private final AgentManager agentManager;
    private final AuditLogger auditLogger;
    private final AppWebSocketServer wsServer;
    private final PlayerManager playerManager;
    private final PermissionCache permissionCache;
    private final ProxyConfig config;
    private final Logger logger;
    private final ModerationManager moderationManager;
    private final ProxyServer proxyServer;

    private static final DateTimeFormatter BAN_DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneId.of("UTC"));

    public AppMessageHandler(DatabaseManager database, AuthManager authManager,
                              AgentManager agentManager, AuditLogger auditLogger,
                              AppWebSocketServer wsServer, PlayerManager playerManager,
                              PermissionCache permissionCache, ProxyConfig config, Logger logger,
                              ModerationManager moderationManager, ProxyServer proxyServer) {
        this.database = database;
        this.authManager = authManager;
        this.agentManager = agentManager;
        this.auditLogger = auditLogger;
        this.wsServer = wsServer;
        this.playerManager = playerManager;
        this.permissionCache = permissionCache;
        this.config = config;
        this.logger = logger;
        this.moderationManager = moderationManager;
        this.proxyServer = proxyServer;
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
                case "setup.check"         -> handleSetupCheck(ws, requestId, payload, session);
                case "setup.create"        -> handleSetupCreate(ws, requestId, payload, session);
                case "auth.login"          -> handleAuthLogin(ws, requestId, payload, session);
                case "auth.logout"         -> handleAuthLogout(ws, requestId, payload, session);
                case "servers.list"        -> handleServersList(ws, requestId, payload, session);
                case "servers.add"         -> handleServersAdd(ws, requestId, payload, session);
                case "servers.remove"      -> handleServersRemove(ws, requestId, payload, session);
                case "servers.update"      -> handleServersUpdate(ws, requestId, payload, session);
                case "console.subscribe"   -> handleConsoleSubscribe(ws, requestId, payload, session);
                case "console.unsubscribe" -> handleConsoleUnsubscribe(ws, requestId, payload, session);
                case "console.command"     -> handleConsoleCommand(ws, requestId, payload, session);
                case "players.list"        -> handlePlayersList(ws, requestId, payload, session);
                case "players.history"     -> handlePlayersHistory(ws, requestId, payload, session);
                case "players.details"     -> handlePlayersDetails(ws, requestId, payload, session);
                case "users.list"          -> handleUsersList(ws, requestId, payload, session);
                case "users.create"        -> handleUsersCreate(ws, requestId, payload, session);
                case "users.update"        -> handleUsersUpdate(ws, requestId, payload, session);
                case "users.delete"        -> handleUsersDelete(ws, requestId, payload, session);
                case "roles.list"          -> handleRolesList(ws, requestId, payload, session);
                case "roles.create"        -> handleRolesCreate(ws, requestId, payload, session);
                case "roles.update"        -> handleRolesUpdate(ws, requestId, payload, session);
                case "roles.delete"        -> handleRolesDelete(ws, requestId, payload, session);
                case "audit.list"          -> handleAuditList(ws, requestId, payload, session);
                case "agents.list"         -> handleAgentsList(ws, requestId, payload, session);
                case "branding.get"                -> handleBrandingGet(ws, requestId, payload, session);
                case "moderation.punish"           -> handleModerationPunish(ws, requestId, payload, session);
                case "moderation.history"          -> handleModerationHistory(ws, requestId, payload, session);
                case "moderation.active"           -> handleModerationActive(ws, requestId, payload, session);
                case "moderation.revoke"           -> handleModerationRevoke(ws, requestId, payload, session);
                case "moderation.notes.list"       -> handleModerationNotesList(ws, requestId, payload, session);
                case "moderation.notes.create"     -> handleModerationNotesCreate(ws, requestId, payload, session);
                case "moderation.presets.list"     -> handleModerationPresetsList(ws, requestId, payload, session);
                case "moderation.presets.create"   -> handleModerationPresetsCreate(ws, requestId, payload, session);
                case "moderation.presets.update"   -> handleModerationPresetsUpdate(ws, requestId, payload, session);
                case "moderation.presets.delete"   -> handleModerationPresetsDelete(ws, requestId, payload, session);
                case "moderation.calculate"        -> handleModerationCalculate(ws, requestId, payload, session);
                case "moderation.dashboard"        -> handleModerationDashboard(ws, requestId, payload, session);
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
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "Username is required"); return;
        }
        if (username.length() < 3 || username.length() > 32) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "Username must be 3-32 characters"); return;
        }
        if (!username.matches("[a-zA-Z0-9_]+")) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "Username may only contain letters, numbers, and underscores"); return;
        }
        if (password == null || password.length() < 8) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "Password must be at least 8 characters"); return;
        }

        LoginResult result = authManager.createOwner(username, password);
        if (!result.isSuccess()) {
            wsServer.sendError(ws, requestId, "SETUP_FAILED", result.getErrorMessage()); return;
        }

        String ip = ws.getRemoteSocketAddress().getAddress().getHostAddress();
        Set<String> perms = new HashSet<>(result.getPermissions());
        ClientSession newSession = new ClientSession(result.getUserId(), result.getUsername(), result.getRoleName(), perms, result.getToken(), ws, ip);
        wsServer.putSession(ws, newSession);
        auditLogger.log(result.getUserId(), result.getUsername(), "SETUP_CREATE", null, "Initial setup completed", ip);

        JsonObject resp = new JsonObject();
        resp.addProperty("token", result.getToken());
        resp.add("user", buildUserJson(result.getUserId(), result.getUsername(), result.getRoleName(), result.getPermissions()));
        wsServer.sendResponse(ws, requestId, resp);
    }

    // ---- Auth ----

    private void handleAuthLogin(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        String username = getString(payload, "username");
        String password = getString(payload, "password");
        String ip = ws.getRemoteSocketAddress().getAddress().getHostAddress();

        if (username == null || password == null) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "Username and password are required"); return;
        }

        LoginResult result = authManager.login(username, password, ip);
        if (!result.isSuccess()) {
            auditLogger.logFailedLogin(username, ip);
            String msg = "ACCOUNT_DISABLED".equals(result.getErrorMessage())
                    ? "Your account has been disabled" : "Invalid username or password";
            wsServer.sendError(ws, requestId, result.getErrorMessage(), msg);
            return;
        }

        auditLogger.logLogin(result.getUserId(), result.getUsername(), ip);
        Set<String> perms = new HashSet<>(result.getPermissions());
        ClientSession newSession = new ClientSession(result.getUserId(), result.getUsername(), result.getRoleName(), perms, result.getToken(), ws, ip);
        wsServer.putSession(ws, newSession);

        JsonObject resp = new JsonObject();
        resp.addProperty("token", result.getToken());
        resp.add("user", buildUserJson(result.getUserId(), result.getUsername(), result.getRoleName(), result.getPermissions()));
        wsServer.sendResponse(ws, requestId, resp);
    }

    private void handleAuthLogout(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (session == null) { wsServer.sendError(ws, requestId, "NOT_AUTHENTICATED", "Not authenticated"); return; }
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
                agentManager.getAgent(info.getServerId()).ifPresent(agent -> {
                    if (agent.isRegistered() && agent.isConnected()) {
                        ServerInfo live = agent.getServerInfo();
                        if (live != null) {
                            info.setOnline(true);
                            info.setTps(live.getTps());
                            info.setMspt(live.getMspt());
                            info.setPlayerCount(live.getPlayerCount());
                            info.setMaxPlayers(live.getMaxPlayers());
                            info.setLastHeartbeat(live.getLastHeartbeat());
                        }
                    }
                });
                servers.add(info.toJson());
            }
            JsonObject resp = new JsonObject();
            resp.add("servers", servers);
            resp.addProperty("networkPlayerCount", playerManager.getAllOnline().size());
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
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "serverId must be 2-32 alphanumeric/dash/underscore characters"); return;
        }
        if (serverName == null || serverName.isBlank()) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "serverName is required"); return;
        }
        if (serverType == null || serverType.isBlank()) serverType = "generic";

        String agentToken = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        try {
            database.addServer(serverId, serverName, serverType, agentToken, now);
            Map<String, Object> row = database.getServer(serverId);
            ServerInfo info = ServerInfo.fromDatabase(row);
            String ip = ws.getRemoteSocketAddress().getAddress().getHostAddress();
            auditLogger.logServerAction(session.getUserId(), session.getUsername(), "SERVER_ADD", serverId, ip);

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
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "serverId is required"); return;
        }

        try {
            agentManager.getAgent(serverId).ifPresent(agent -> {
                if (agent.isConnected()) agent.getConnection().close();
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
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "serverId is required"); return;
        }

        try {
            Map<String, Object> existing = database.getServer(serverId);
            if (existing == null) { wsServer.sendError(ws, requestId, "NOT_FOUND", "Server not found"); return; }

            long id = getLong(existing, "id");
            String newName = serverName != null ? serverName : getString(existing, "server_name");
            String newType = serverType != null ? serverType : getString(existing, "server_type");
            database.updateServer(id, serverId, newName, newType, System.currentTimeMillis());

            ServerInfo info = ServerInfo.fromDatabase(database.getServer(serverId));
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
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "serverId is required"); return;
        }
        session.subscribe(serverId);
        wsServer.sendResponse(ws, requestId, new JsonObject());
    }

    private void handleConsoleUnsubscribe(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (session == null) { wsServer.sendError(ws, requestId, "NOT_AUTHENTICATED", "Not authenticated"); return; }
        String serverId = getString(payload, "serverId");
        if (serverId != null) session.unsubscribe(serverId);
        wsServer.sendResponse(ws, requestId, new JsonObject());
    }

    private void handleConsoleCommand(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (!requireAuth(ws, requestId, session)) return;
        if (!requirePermission(ws, requestId, session, "console.command")) return;

        String serverId = getString(payload, "serverId");
        String command = getString(payload, "command");

        if (serverId == null || serverId.isBlank()) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "serverId is required"); return;
        }
        if (command == null || command.isBlank()) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "command is required"); return;
        }
        if (!agentManager.isAgentOnline(serverId)) {
            wsServer.sendError(ws, requestId, "SERVER_OFFLINE", "The server is offline or the agent is not connected"); return;
        }

        boolean sent = agentManager.sendCommand(serverId, command);
        if (!sent) { wsServer.sendError(ws, requestId, "SERVER_OFFLINE", "Failed to send command — server offline"); return; }

        String ip = ws.getRemoteSocketAddress().getAddress().getHostAddress();
        auditLogger.logCommand(session.getUserId(), session.getUsername(), serverId, command, ip);
        wsServer.sendResponse(ws, requestId, new JsonObject());
    }

    // ---- Players ----

    private void handlePlayersList(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (!requireAuth(ws, requestId, session)) return;
        if (!requirePermission(ws, requestId, session, "players.view")) return;

        String filterServerId = getString(payload, "serverId");
        JsonArray players = new JsonArray();

        Collection<PlayerInfo> online = filterServerId != null
                ? playerManager.getOnlineByServer(filterServerId)
                : playerManager.getAllOnline();

        for (PlayerInfo p : online) players.add(p.toJson());

        JsonObject resp = new JsonObject();
        resp.add("players", players);
        resp.addProperty("total", players.size());
        wsServer.sendResponse(ws, requestId, resp);
    }

    private void handlePlayersHistory(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (!requireAuth(ws, requestId, session)) return;
        if (!requirePermission(ws, requestId, session, "players.view")) return;

        int page = 1, limit = 200;
        String search = getString(payload, "search");
        if (payload.has("page"))  try { page  = payload.get("page").getAsInt();  } catch (Exception ignored) {}
        if (payload.has("limit")) try { limit = payload.get("limit").getAsInt(); } catch (Exception ignored) {}
        if (limit > 500) limit = 500;

        try {
            List<Map<String, Object>> rows = database.getAllPlayersHistory(page, limit, search);
            JsonArray players = new JsonArray();
            for (Map<String, Object> row : rows) players.add(PlayerInfo.fromDatabase(row).toJson());

            JsonObject resp = new JsonObject();
            resp.add("players", players);
            resp.addProperty("total", players.size());
            wsServer.sendResponse(ws, requestId, resp);
        } catch (SQLException e) {
            logger.severe("[AppMH] players.history DB error: " + e.getMessage());
            wsServer.sendError(ws, requestId, "DATABASE_ERROR", "Failed to retrieve player history");
        }
    }

    private void handlePlayersDetails(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (!requireAuth(ws, requestId, session)) return;
        if (!requirePermission(ws, requestId, session, "players.details")) return;

        String uuid = getString(payload, "uuid");
        if (uuid == null || uuid.isBlank()) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "uuid is required"); return;
        }

        PlayerInfo live = playerManager.getOnlinePlayer(uuid);
        if (live != null) {
            JsonObject resp = new JsonObject();
            resp.add("player", live.toJson());
            wsServer.sendResponse(ws, requestId, resp);
            return;
        }

        try {
            Map<String, Object> row = database.getPlayer(uuid);
            if (row == null) { wsServer.sendError(ws, requestId, "NOT_FOUND", "Player not found"); return; }
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
                users.add(new User(userId, username, null, roleId, roleName, perms, isActive, createdAt).toJson());
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
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "Username must be 3-32 characters"); return;
        }
        if (!username.matches("[a-zA-Z0-9_]+")) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "Username may only contain letters, numbers, and underscores"); return;
        }
        if (password == null || password.length() < 8) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "Password must be at least 8 characters"); return;
        }
        if (roleId <= 0) { wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "Valid roleId is required"); return; }

        try {
            Map<String, Object> targetRole = database.getRoleById(roleId);
            if (targetRole == null) { wsServer.sendError(ws, requestId, "NOT_FOUND", "Role not found"); return; }
            String targetRoleName = getString(targetRole, "name");
            if ("owner".equals(targetRoleName) && !session.isOwner()) {
                wsServer.sendError(ws, requestId, "FORBIDDEN", "Only owners can create owner accounts"); return;
            }

            long newUserId = authManager.createUser(username, password, roleId);
            if (newUserId < 0) { wsServer.sendError(ws, requestId, "CREATE_FAILED", "Failed to create user"); return; }

            List<String> perms = database.getPermissionsForRole(roleId);
            long now = System.currentTimeMillis();
            User u = new User(newUserId, username, null, roleId, getString(targetRole, "name"), perms, true, now);
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

        if (targetUserId <= 0) { wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "Valid userId is required"); return; }

        try {
            Map<String, Object> existingUser = database.getUserById(targetUserId);
            if (existingUser == null) { wsServer.sendError(ws, requestId, "NOT_FOUND", "User not found"); return; }

            long targetRoleId = database.getUserRoleId(targetUserId);
            Map<String, Object> targetCurrentRole = targetRoleId > 0 ? database.getRoleById(targetRoleId) : null;
            boolean targetIsOwner = targetCurrentRole != null && "owner".equals(getString(targetCurrentRole, "name"));

            if (targetIsOwner && !session.isOwner()) {
                wsServer.sendError(ws, requestId, "FORBIDDEN", "Only owners can modify owner accounts"); return;
            }
            if (roleId > 0) {
                Map<String, Object> newRole = database.getRoleById(roleId);
                if (newRole != null && "owner".equals(getString(newRole, "name")) && !session.isOwner()) {
                    wsServer.sendError(ws, requestId, "FORBIDDEN", "Only owners can assign owner role"); return;
                }
            }

            String newUsername = username != null ? username : getString(existingUser, "username");
            boolean newActive = isActive != null ? isActive : (getInt(existingUser, "is_active") == 1);
            long now = System.currentTimeMillis();

            database.updateUser(targetUserId, newUsername, newActive, now);
            if (roleId > 0) database.setUserRole(targetUserId, roleId);

            permissionCache.invalidate(targetUserId);

            long effectiveRoleId = roleId > 0 ? roleId : targetRoleId;
            Map<String, Object> role = effectiveRoleId > 0 ? database.getRoleById(effectiveRoleId) : null;
            String roleName = role != null ? getString(role, "name") : "";
            List<String> perms = effectiveRoleId > 0 ? database.getPermissionsForRole(effectiveRoleId) : new ArrayList<>();

            User u = new User(targetUserId, newUsername, null, effectiveRoleId, roleName, perms, newActive, getLong(existingUser, "created_at"));
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
        if (targetUserId <= 0) { wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "Valid userId is required"); return; }
        if (targetUserId == session.getUserId()) {
            wsServer.sendError(ws, requestId, "FORBIDDEN", "You cannot delete your own account"); return;
        }

        try {
            Map<String, Object> existingUser = database.getUserById(targetUserId);
            if (existingUser == null) { wsServer.sendError(ws, requestId, "NOT_FOUND", "User not found"); return; }

            long targetRoleId = database.getUserRoleId(targetUserId);
            Map<String, Object> targetRole = targetRoleId > 0 ? database.getRoleById(targetRoleId) : null;
            if (targetRole != null && "owner".equals(getString(targetRole, "name")) && !session.isOwner()) {
                wsServer.sendError(ws, requestId, "FORBIDDEN", "Only owners can delete owner accounts"); return;
            }

            String targetUsername = getString(existingUser, "username");
            database.deleteUser(targetUserId);
            permissionCache.invalidate(targetUserId);

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
                roles.add(new Role(roleId, getString(row, "name"), getString(row, "display_name"),
                        perms, getInt(row, "is_system_role") == 1, getLong(row, "created_at")).toJson());
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
        if (!session.isOwner()) { wsServer.sendError(ws, requestId, "FORBIDDEN", "Only owners can create roles"); return; }

        String name = getString(payload, "name");
        String displayName = getString(payload, "displayName");

        if (name == null || name.isBlank() || name.length() < 2 || name.length() > 32) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "Role name must be 2-32 characters"); return;
        }
        if (!name.matches("[a-zA-Z0-9_]+")) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "Role name may only contain letters, numbers, and underscores"); return;
        }
        if (displayName == null || displayName.isBlank()) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "Display name is required"); return;
        }

        List<String> permissions = new ArrayList<>();
        if (payload.has("permissions") && payload.get("permissions").isJsonArray()) {
            for (JsonElement el : payload.getAsJsonArray("permissions")) permissions.add(el.getAsString());
        }

        try {
            long now = System.currentTimeMillis();
            long roleId = database.createRole(name, displayName, false, now);
            if (roleId < 0) { wsServer.sendError(ws, requestId, "CREATE_FAILED", "Failed to create role"); return; }
            if (!permissions.isEmpty()) database.setPermissionsForRole(roleId, permissions);

            String ip = ws.getRemoteSocketAddress().getAddress().getHostAddress();
            auditLogger.logRoleAction(session.getUserId(), session.getUsername(), "ROLE_CREATE", name, ip);

            JsonObject resp = new JsonObject();
            resp.add("role", new Role(roleId, name, displayName, permissions, false, now).toJson());
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
        if (roleId <= 0) { wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "Valid roleId is required"); return; }

        try {
            Map<String, Object> existingRole = database.getRoleById(roleId);
            if (existingRole == null) { wsServer.sendError(ws, requestId, "NOT_FOUND", "Role not found"); return; }

            boolean isSystemRole = getInt(existingRole, "is_system_role") == 1;
            if (isSystemRole && !session.isOwner()) {
                wsServer.sendError(ws, requestId, "FORBIDDEN", "Only owners can modify system roles"); return;
            }

            String newDisplayName = displayName != null ? displayName : getString(existingRole, "display_name");
            database.updateRole(roleId, newDisplayName);

            if (payload.has("permissions") && payload.get("permissions").isJsonArray()) {
                List<String> permissions = new ArrayList<>();
                for (JsonElement el : payload.getAsJsonArray("permissions")) permissions.add(el.getAsString());
                database.setPermissionsForRole(roleId, permissions);
                permissionCache.invalidateAll();
            }

            List<String> currentPerms = database.getPermissionsForRole(roleId);
            String ip = ws.getRemoteSocketAddress().getAddress().getHostAddress();
            auditLogger.logRoleAction(session.getUserId(), session.getUsername(), "ROLE_UPDATE", getString(existingRole, "name"), ip);

            JsonObject resp = new JsonObject();
            resp.add("role", new Role(roleId, getString(existingRole, "name"), newDisplayName,
                    currentPerms, isSystemRole, getLong(existingRole, "created_at")).toJson());
            wsServer.sendResponse(ws, requestId, resp);
        } catch (SQLException e) {
            wsServer.sendError(ws, requestId, "DATABASE_ERROR", "Failed to update role");
        }
    }

    private void handleRolesDelete(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (!requireAuth(ws, requestId, session)) return;
        if (!requirePermission(ws, requestId, session, "roles.manage")) return;
        if (!session.isOwner()) { wsServer.sendError(ws, requestId, "FORBIDDEN", "Only owners can delete roles"); return; }

        long roleId = getLongFromJson(payload, "roleId");
        if (roleId <= 0) { wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "Valid roleId is required"); return; }

        try {
            Map<String, Object> existingRole = database.getRoleById(roleId);
            if (existingRole == null) { wsServer.sendError(ws, requestId, "NOT_FOUND", "Role not found"); return; }
            if (getInt(existingRole, "is_system_role") == 1) {
                wsServer.sendError(ws, requestId, "FORBIDDEN", "Cannot delete system roles"); return;
            }

            String roleName = getString(existingRole, "name");
            database.deleteRole(roleId);
            permissionCache.invalidateAll();

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
        int limit = payload.has("pageSize") ? payload.get("pageSize").getAsInt()
                  : payload.has("limit")    ? payload.get("limit").getAsInt() : 50;
        if (page < 1) page = 1;
        if (limit < 1 || limit > 200) limit = 50;

        String action   = getString(payload, "action");
        String username = getString(payload, "username");
        String search   = getString(payload, "search");

        try {
            List<Map<String, Object>> rows = database.getAuditLogs(page, limit, action, username, search);
            int total      = database.getAuditLogsTotal(action, username, search);
            int totalPages = (int) Math.max(1, Math.ceil((double) total / limit));
            JsonArray logs = new JsonArray();
            for (Map<String, Object> row : rows) {
                Object userIdObj = row.get("user_id");
                Long userId = userIdObj instanceof Number ? ((Number) userIdObj).longValue() : null;
                logs.add(new AuditLog(getLong(row, "id"), userId, getString(row, "username"),
                        getString(row, "action"), getString(row, "target"),
                        getString(row, "details"), getString(row, "ip_address"),
                        getLong(row, "timestamp")).toJson());
            }
            JsonObject resp = new JsonObject();
            resp.add("logs", logs);
            resp.addProperty("total", total);
            resp.addProperty("totalPages", totalPages);
            wsServer.sendResponse(ws, requestId, resp);
        } catch (SQLException e) {
            wsServer.sendError(ws, requestId, "DATABASE_ERROR", "Failed to retrieve audit logs");
        }
    }

    private void handleAgentsList(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (!requireAuth(ws, requestId, session)) return;
        if (!requirePermission(ws, requestId, session, "servers.view")) return;

        long now = System.currentTimeMillis();
        JsonArray agents = new JsonArray();

        try {
            List<Map<String, Object>> servers = database.getAllServers();
            for (Map<String, Object> serverRow : servers) {
                String serverId = getString(serverRow, "server_id");
                JsonObject agentJson = new JsonObject();
                agentJson.addProperty("serverId",   serverId);
                agentJson.addProperty("serverName", getString(serverRow, "server_name"));
                agentJson.addProperty("serverType", getString(serverRow, "server_type"));

                Optional<AgentConnection> opt = agentManager.getAgent(serverId);
                if (opt.isPresent() && opt.get().isRegistered() && opt.get().isConnected()) {
                    AgentConnection agent = opt.get();
                    agentJson.addProperty("connected",        true);
                    agentJson.addProperty("version",          agent.getVersion() != null ? agent.getVersion() : "");
                    agentJson.addProperty("protocolVersion",  agent.getProtocolVersion());
                    agentJson.addProperty("lastHeartbeatAt",  agent.getLastHeartbeatAt());
                    agentJson.addProperty("heartbeatAgeMs",   now - agent.getLastHeartbeatAt());
                    agentJson.addProperty("latencyMs",        agent.getLatencyMs());
                    agentJson.addProperty("connectTime",      agent.getConnectTime());
                    agentJson.addProperty("reconnectAttempts",agent.getReconnectAttempts());
                    agentJson.addProperty("status",           "ONLINE");
                    agentJson.addProperty("lastError",        agent.getLastError() != null ? agent.getLastError() : "");
                } else {
                    agentJson.addProperty("connected",        false);
                    agentJson.addProperty("version",          "");
                    agentJson.addProperty("protocolVersion",  0);
                    agentJson.addProperty("lastHeartbeatAt",  0L);
                    agentJson.addProperty("heartbeatAgeMs",   0L);
                    agentJson.addProperty("latencyMs",        0L);
                    agentJson.addProperty("connectTime",      0L);
                    agentJson.addProperty("reconnectAttempts",0);
                    agentJson.addProperty("status",           "OFFLINE");
                    agentJson.addProperty("lastError",        "");
                }
                agents.add(agentJson);
            }
        } catch (SQLException e) {
            wsServer.sendError(ws, requestId, "DATABASE_ERROR", "Failed to retrieve agent statuses");
            return;
        }

        JsonObject resp = new JsonObject();
        resp.add("agents", agents);
        resp.addProperty("timestamp", now);
        wsServer.sendResponse(ws, requestId, resp);
    }

    private void handleBrandingGet(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        JsonObject branding = new JsonObject();
        branding.addProperty("appTitle",     config.getBrandingAppTitle());
        branding.addProperty("networkName",  config.getBrandingNetworkName());
        branding.addProperty("accentColor",  config.getBrandingAccentColor());

        String logoFile = config.getBrandingLogoFile();
        if (logoFile != null && !logoFile.isBlank()) {
            // Restrict to filename only — no path traversal
            String safeName = Path.of(logoFile).getFileName().toString();
            Path logoPath = config.getDataDirectory().resolve(safeName);
            String ext = safeName.contains(".") ? safeName.substring(safeName.lastIndexOf('.') + 1).toLowerCase() : "";
            if (Files.exists(logoPath) && (ext.equals("png") || ext.equals("jpg") || ext.equals("jpeg"))) {
                try {
                    byte[] bytes = Files.readAllBytes(logoPath);
                    branding.addProperty("logoBase64",  Base64.getEncoder().encodeToString(bytes));
                    branding.addProperty("logoMimeType", "image/" + (ext.equals("jpg") ? "jpeg" : ext));
                } catch (IOException ignored) {}
            }
        }

        wsServer.sendResponse(ws, requestId, branding);
    }

    // ---- Moderation ----

    private void handleModerationPunish(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (!requireAuth(ws, requestId, session)) return;

        String actionType = getString(payload, "actionType");
        if (actionType == null || actionType.isBlank()) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "actionType is required"); return;
        }

        // Permission check per action type
        String requiredPermission = switch (actionType) {
            case "WARN"                    -> "moderation.warn";
            case "MUTE", "TEMP_MUTE"       -> "moderation.mute";
            case "UNMUTE"                  -> "moderation.unmute";
            case "KICK"                    -> "moderation.kick";
            case "BAN"                     -> "moderation.ban";
            case "TEMP_BAN"                -> "moderation.tempban";
            case "UNBAN"                   -> "moderation.unban";
            case "IP_BAN", "TEMP_IP_BAN"   -> "moderation.ipban";
            case "UNBAN_IP"                -> "moderation.unban";
            case "NOTE", "STAFF_NOTE"      -> "moderation.notes.create";
            default -> null;
        };
        if (requiredPermission == null) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "Unknown actionType: " + actionType); return;
        }
        if (!requirePermission(ws, requestId, session, requiredPermission)) return;

        String targetName = getString(payload, "targetName");
        if (targetName == null || targetName.isBlank()) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "targetName is required"); return;
        }

        String reason = getString(payload, "reason");
        if (reason == null || reason.isBlank()) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "reason is required"); return;
        }

        String targetUuid     = getString(payload, "targetUuid");
        String targetServer   = getString(payload, "targetServer");
        String evidence       = getString(payload, "evidence");

        // Only store IP hash if session has moderation.view_ip
        String targetIpHash = null;
        if (session.hasPermission("moderation.view_ip")) {
            String rawIp = getString(payload, "targetIp");
            if (rawIp != null && !rawIp.isBlank()) {
                targetIpHash = ModerationManager.hashIp(rawIp);
            }
        }

        long durationSeconds = 0;
        if (payload.has("durationSeconds") && !payload.get("durationSeconds").isJsonNull()) {
            try { durationSeconds = payload.get("durationSeconds").getAsLong(); } catch (Exception ignored) {}
        }

        long now       = System.currentTimeMillis();
        long expiresAt = durationSeconds > 0 ? now + (durationSeconds * 1000L) : 0L;

        // Collect preset IDs
        List<Long> presetIds = new ArrayList<>();
        if (payload.has("presetIds") && payload.get("presetIds").isJsonArray()) {
            for (JsonElement el : payload.getAsJsonArray("presetIds")) {
                try { presetIds.add(el.getAsLong()); } catch (Exception ignored) {}
            }
        }

        try {
            long punishmentId = moderationManager.createPunishment(
                    targetUuid, targetName, targetIpHash,
                    actionType, reason, durationSeconds, expiresAt,
                    session.getUserId(), session.getUsername(),
                    targetServer != null ? targetServer : "global",
                    evidence != null ? evidence : "");

            // Link preset IDs
            if (!presetIds.isEmpty()) {
                for (long presetId : presetIds) {
                    try { database.linkPunishmentPreset(punishmentId, presetId); } catch (SQLException ignored) {}
                }
            }

            // KICK — send kick command through agents
            if ("KICK".equals(actionType)) {
                String kickCmd = "kick " + targetName + " " + reason;
                if (targetServer != null && !targetServer.isBlank() && !"global".equals(targetServer)) {
                    agentManager.sendCommand(targetServer, kickCmd);
                } else {
                    for (AgentConnection agent : agentManager.getAllAgents()) {
                        if (agent.isConnected() && agent.isRegistered())
                            agentManager.sendCommand(agent.getServerId(), kickCmd);
                    }
                }
            }

            // BAN / TEMP_BAN / IP_BAN — disconnect via Velocity with proper ban screen
            if ("BAN".equals(actionType) || "TEMP_BAN".equals(actionType)
                    || "IP_BAN".equals(actionType) || "TEMP_IP_BAN".equals(actionType)) {
                String banMsg = buildBanMessage(actionType, reason, expiresAt);
                Component disconnectComponent = Component.text(banMsg);
                proxyServer.getPlayer(targetName)
                        .ifPresent(p -> p.disconnect(disconnectComponent));
            }

            // WARN — notify player in-game if online
            if ("WARN".equals(actionType)) {
                agentManager.broadcastWarn(targetName, reason);
            }

            // MUTE / TEMP_MUTE — notify agents to block chat
            if ("MUTE".equals(actionType) || "TEMP_MUTE".equals(actionType)) {
                agentManager.broadcastMute(targetName, reason, expiresAt);
            }

            // Audit log
            String auditAction = switch (actionType) {
                case "WARN"                  -> AuditLogger.MODERATION_WARN;
                case "MUTE", "TEMP_MUTE"     -> AuditLogger.MODERATION_MUTE;
                case "UNMUTE"                -> AuditLogger.MODERATION_UNMUTE;
                case "KICK"                  -> AuditLogger.MODERATION_KICK;
                case "BAN"                   -> AuditLogger.MODERATION_BAN;
                case "TEMP_BAN"              -> AuditLogger.MODERATION_TEMPBAN;
                case "UNBAN"                 -> AuditLogger.MODERATION_UNBAN;
                case "IP_BAN", "TEMP_IP_BAN" -> AuditLogger.MODERATION_IPBAN;
                case "UNBAN_IP"              -> AuditLogger.MODERATION_UNBAN_IP;
                case "NOTE", "STAFF_NOTE"    -> AuditLogger.MODERATION_NOTE_CREATE;
                default                      -> "MODERATION_" + actionType;
            };
            auditLogger.logModerationAction(session.getUserId(), session.getUsername(),
                    auditAction, targetName,
                    "Reason: " + reason + (durationSeconds > 0 ? " | Duration: " + durationSeconds + "s" : ""),
                    session.getIpAddress());

            // Fetch and return the created punishment
            Map<String, Object> created = database.getPunishmentById(punishmentId);
            Punishment p = Punishment.fromRow(created);
            JsonObject punJson = p.toJson(session.hasPermission("moderation.view_ip"));

            // Broadcast to all moderation.view clients
            wsServer.broadcastPunishmentEvent("event.punishment.created", punJson.deepCopy());

            JsonObject resp = new JsonObject();
            resp.add("punishment", punJson);
            wsServer.sendResponse(ws, requestId, resp);

        } catch (SQLException e) {
            logger.severe("[AppMH] moderation.punish DB error: " + e.getMessage());
            wsServer.sendError(ws, requestId, "DATABASE_ERROR", "Failed to create punishment");
        }
    }

    private void handleModerationHistory(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (!requireAuth(ws, requestId, session)) return;
        if (!requirePermission(ws, requestId, session, "moderation.view")) return;

        String targetName = getString(payload, "targetName");
        if (targetName == null || targetName.isBlank()) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "targetName is required"); return;
        }

        int page  = 1;
        int limit = 20;
        if (payload.has("page")  && !payload.get("page").isJsonNull())  try { page  = payload.get("page").getAsInt();  } catch (Exception ignored) {}
        if (payload.has("limit") && !payload.get("limit").isJsonNull()) try { limit = payload.get("limit").getAsInt(); } catch (Exception ignored) {}
        if (page  < 1)   page  = 1;
        if (limit < 1)   limit = 1;
        if (limit > 100) limit = 100;

        boolean showIp = session.hasPermission("moderation.view_ip");

        try {
            List<Map<String, Object>> rows = database.getPunishmentHistory(targetName, page, limit);
            int total = database.getPunishmentHistoryTotal(targetName);
            int totalPages = (int) Math.ceil((double) total / limit);

            JsonArray historyArr = new JsonArray();
            for (Map<String, Object> row : rows) {
                historyArr.add(Punishment.fromRow(row).toJson(showIp));
            }

            JsonObject resp = new JsonObject();
            resp.add("history", historyArr);
            resp.addProperty("total", total);
            resp.addProperty("totalPages", totalPages);
            wsServer.sendResponse(ws, requestId, resp);
        } catch (SQLException e) {
            logger.severe("[AppMH] moderation.history DB error: " + e.getMessage());
            wsServer.sendError(ws, requestId, "DATABASE_ERROR", "Failed to retrieve punishment history");
        }
    }

    private void handleModerationActive(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (!requireAuth(ws, requestId, session)) return;
        if (!requirePermission(ws, requestId, session, "moderation.view")) return;

        boolean showIp = session.hasPermission("moderation.view_ip");
        String targetName = getString(payload, "targetName");

        try {
            List<Map<String, Object>> rows;
            if (targetName != null && !targetName.isBlank()) {
                rows = database.getActivePunishments(targetName);
            } else {
                rows = database.getAllActivePunishments();
            }

            JsonArray arr = new JsonArray();
            for (Map<String, Object> row : rows) {
                arr.add(Punishment.fromRow(row).toJson(showIp));
            }

            JsonObject resp = new JsonObject();
            resp.add("punishments", arr);
            wsServer.sendResponse(ws, requestId, resp);
        } catch (SQLException e) {
            logger.severe("[AppMH] moderation.active DB error: " + e.getMessage());
            wsServer.sendError(ws, requestId, "DATABASE_ERROR", "Failed to retrieve active punishments");
        }
    }

    private void handleModerationRevoke(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (!requireAuth(ws, requestId, session)) return;

        long punishmentId = getLongFromJson(payload, "punishmentId");
        if (punishmentId <= 0) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "punishmentId is required"); return;
        }

        String revokeReason = getString(payload, "revokeReason");
        if (revokeReason == null || revokeReason.isBlank()) revokeReason = "No reason provided";

        try {
            Map<String, Object> existing = database.getPunishmentById(punishmentId);
            if (existing == null) {
                wsServer.sendError(ws, requestId, "NOT_FOUND", "Punishment not found"); return;
            }

            String actionType = getString(existing, "action_type");

            // Permission check based on what's being revoked
            String requiredPerm;
            if (actionType != null && (actionType.contains("BAN") || actionType.contains("IP_BAN"))) {
                requiredPerm = "moderation.unban";
            } else if (actionType != null && actionType.contains("MUTE")) {
                requiredPerm = "moderation.unmute";
            } else {
                requiredPerm = "moderation.unban";
            }
            if (!requirePermission(ws, requestId, session, requiredPerm)) return;

            boolean revoked = moderationManager.revokePunishment(
                    punishmentId, session.getUserId(), session.getUsername(), revokeReason);

            if (!revoked) {
                wsServer.sendError(ws, requestId, "ALREADY_REVOKED", "Punishment is not active or does not exist"); return;
            }

            String auditAction = (actionType != null && actionType.contains("MUTE"))
                    ? AuditLogger.MODERATION_UNMUTE : AuditLogger.MODERATION_UNBAN;
            String targetName = getString(existing, "target_name");
            auditLogger.logModerationAction(session.getUserId(), session.getUsername(),
                    auditAction, targetName,
                    "Revoked punishment #" + punishmentId + " | Reason: " + revokeReason,
                    session.getIpAddress());

            // Notify agents to lift an in-game mute
            if (actionType != null && actionType.contains("MUTE")) {
                agentManager.broadcastUnmute(targetName);
            }

            // Broadcast revocation event
            JsonObject eventPayload = new JsonObject();
            eventPayload.addProperty("punishmentId", punishmentId);
            eventPayload.addProperty("revokedBy", session.getUsername());
            eventPayload.addProperty("revokeReason", revokeReason);
            wsServer.broadcastPunishmentEvent("event.punishment.revoked", eventPayload);

            wsServer.sendResponse(ws, requestId, new JsonObject());
        } catch (SQLException e) {
            logger.severe("[AppMH] moderation.revoke DB error: " + e.getMessage());
            wsServer.sendError(ws, requestId, "DATABASE_ERROR", "Failed to revoke punishment");
        }
    }

    private void handleModerationNotesList(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (!requireAuth(ws, requestId, session)) return;
        if (!requirePermission(ws, requestId, session, "moderation.notes.view")) return;

        String targetName = getString(payload, "targetName");
        if (targetName == null || targetName.isBlank()) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "targetName is required"); return;
        }

        try {
            List<Map<String, Object>> rows = database.getNotes(targetName);
            JsonArray arr = new JsonArray();
            for (Map<String, Object> row : rows) {
                arr.add(PlayerNote.fromRow(row).toJson());
            }
            JsonObject resp = new JsonObject();
            resp.add("notes", arr);
            wsServer.sendResponse(ws, requestId, resp);
        } catch (SQLException e) {
            logger.severe("[AppMH] moderation.notes.list DB error: " + e.getMessage());
            wsServer.sendError(ws, requestId, "DATABASE_ERROR", "Failed to retrieve notes");
        }
    }

    private void handleModerationNotesCreate(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (!requireAuth(ws, requestId, session)) return;
        if (!requirePermission(ws, requestId, session, "moderation.notes.create")) return;

        String targetName = getString(payload, "targetName");
        String targetUuid = getString(payload, "targetUuid");
        String note       = getString(payload, "note");
        String visibility = getString(payload, "visibility");

        if (targetName == null || targetName.isBlank()) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "targetName is required"); return;
        }
        if (note == null || note.isBlank()) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "note is required"); return;
        }
        if (visibility == null || visibility.isBlank()) visibility = "staff";

        try {
            long noteId = database.createNote(targetUuid, targetName, note,
                    session.getUserId(), session.getUsername(), visibility);

            auditLogger.logModerationAction(session.getUserId(), session.getUsername(),
                    AuditLogger.MODERATION_NOTE_CREATE, targetName,
                    "Note created (visibility=" + visibility + ")",
                    session.getIpAddress());

            // Re-fetch to return complete record
            List<Map<String, Object>> notes = database.getNotes(targetName);
            PlayerNote created = null;
            for (Map<String, Object> row : notes) {
                Object rid = row.get("id");
                if (rid instanceof Number && ((Number) rid).longValue() == noteId) {
                    created = PlayerNote.fromRow(row);
                    break;
                }
            }

            JsonObject resp = new JsonObject();
            resp.add("note", created != null ? created.toJson() : new JsonObject());
            wsServer.sendResponse(ws, requestId, resp);
        } catch (SQLException e) {
            logger.severe("[AppMH] moderation.notes.create DB error: " + e.getMessage());
            wsServer.sendError(ws, requestId, "DATABASE_ERROR", "Failed to create note");
        }
    }

    private void handleModerationPresetsList(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (!requireAuth(ws, requestId, session)) return;
        if (!requirePermission(ws, requestId, session, "moderation.view")) return;

        try {
            List<Map<String, Object>> rows = database.getPresets();
            JsonArray arr = new JsonArray();
            for (Map<String, Object> row : rows) {
                arr.add(PunishmentPreset.fromRow(row).toJson());
            }
            JsonObject resp = new JsonObject();
            resp.add("presets", arr);
            wsServer.sendResponse(ws, requestId, resp);
        } catch (SQLException e) {
            logger.severe("[AppMH] moderation.presets.list DB error: " + e.getMessage());
            wsServer.sendError(ws, requestId, "DATABASE_ERROR", "Failed to retrieve presets");
        }
    }

    private void handleModerationPresetsCreate(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (!requireAuth(ws, requestId, session)) return;
        if (!requirePermission(ws, requestId, session, "moderation.presets.manage")) return;

        String category    = getString(payload, "category");
        String name        = getString(payload, "name");
        String description = getString(payload, "description");
        String actionType  = getString(payload, "actionType");

        if (category == null || category.isBlank()) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "category is required"); return;
        }
        if (name == null || name.isBlank()) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "name is required"); return;
        }
        if (actionType == null || actionType.isBlank()) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "actionType is required"); return;
        }

        long durationSeconds = 0;
        int  severity        = 1;
        boolean stackable    = true;
        boolean bypassCap    = false;
        boolean requiresIpBan = false;

        if (payload.has("durationSeconds") && !payload.get("durationSeconds").isJsonNull())
            try { durationSeconds = payload.get("durationSeconds").getAsLong(); } catch (Exception ignored) {}
        if (payload.has("severity") && !payload.get("severity").isJsonNull())
            try { severity = payload.get("severity").getAsInt(); } catch (Exception ignored) {}
        if (payload.has("stackable") && !payload.get("stackable").isJsonNull())
            try { stackable = payload.get("stackable").getAsBoolean(); } catch (Exception ignored) {}
        if (payload.has("bypassCap") && !payload.get("bypassCap").isJsonNull())
            try { bypassCap = payload.get("bypassCap").getAsBoolean(); } catch (Exception ignored) {}
        if (payload.has("requiresIpBan") && !payload.get("requiresIpBan").isJsonNull())
            try { requiresIpBan = payload.get("requiresIpBan").getAsBoolean(); } catch (Exception ignored) {}

        try {
            long presetId = database.createPreset(category, name, description, actionType,
                    durationSeconds, severity, stackable, bypassCap, requiresIpBan);

            auditLogger.logModerationAction(session.getUserId(), session.getUsername(),
                    AuditLogger.MODERATION_PRESET_CREATE, name, "Category: " + category, session.getIpAddress());

            Map<String, Object> row = database.getPresetById(presetId);
            JsonObject resp = new JsonObject();
            resp.add("preset", row != null ? PunishmentPreset.fromRow(row).toJson() : new JsonObject());
            wsServer.sendResponse(ws, requestId, resp);
        } catch (SQLException e) {
            logger.severe("[AppMH] moderation.presets.create DB error: " + e.getMessage());
            wsServer.sendError(ws, requestId, "DATABASE_ERROR", "Failed to create preset");
        }
    }

    private void handleModerationPresetsUpdate(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (!requireAuth(ws, requestId, session)) return;
        if (!requirePermission(ws, requestId, session, "moderation.presets.manage")) return;

        long id = getLongFromJson(payload, "id");
        if (id <= 0) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "id is required"); return;
        }

        String category    = getString(payload, "category");
        String name        = getString(payload, "name");
        String description = getString(payload, "description");
        String actionType  = getString(payload, "actionType");

        if (category == null || category.isBlank() || name == null || name.isBlank() || actionType == null || actionType.isBlank()) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "category, name, and actionType are required"); return;
        }

        long durationSeconds = 0;
        int  severity        = 1;
        boolean stackable    = true;
        boolean bypassCap    = false;
        boolean requiresIpBan = false;
        boolean enabled       = true;

        if (payload.has("durationSeconds") && !payload.get("durationSeconds").isJsonNull())
            try { durationSeconds = payload.get("durationSeconds").getAsLong(); } catch (Exception ignored) {}
        if (payload.has("severity") && !payload.get("severity").isJsonNull())
            try { severity = payload.get("severity").getAsInt(); } catch (Exception ignored) {}
        if (payload.has("stackable") && !payload.get("stackable").isJsonNull())
            try { stackable = payload.get("stackable").getAsBoolean(); } catch (Exception ignored) {}
        if (payload.has("bypassCap") && !payload.get("bypassCap").isJsonNull())
            try { bypassCap = payload.get("bypassCap").getAsBoolean(); } catch (Exception ignored) {}
        if (payload.has("requiresIpBan") && !payload.get("requiresIpBan").isJsonNull())
            try { requiresIpBan = payload.get("requiresIpBan").getAsBoolean(); } catch (Exception ignored) {}
        if (payload.has("enabled") && !payload.get("enabled").isJsonNull())
            try { enabled = payload.get("enabled").getAsBoolean(); } catch (Exception ignored) {}

        try {
            boolean updated = database.updatePreset(id, category, name, description, actionType,
                    durationSeconds, severity, stackable, bypassCap, requiresIpBan, enabled);

            if (!updated) {
                wsServer.sendError(ws, requestId, "NOT_FOUND", "Preset not found"); return;
            }

            auditLogger.logModerationAction(session.getUserId(), session.getUsername(),
                    AuditLogger.MODERATION_PRESET_UPDATE, name, "ID: " + id, session.getIpAddress());

            Map<String, Object> row = database.getPresetById(id);
            JsonObject resp = new JsonObject();
            resp.add("preset", row != null ? PunishmentPreset.fromRow(row).toJson() : new JsonObject());
            wsServer.sendResponse(ws, requestId, resp);
        } catch (SQLException e) {
            logger.severe("[AppMH] moderation.presets.update DB error: " + e.getMessage());
            wsServer.sendError(ws, requestId, "DATABASE_ERROR", "Failed to update preset");
        }
    }

    private void handleModerationPresetsDelete(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (!requireAuth(ws, requestId, session)) return;
        if (!requirePermission(ws, requestId, session, "moderation.presets.manage")) return;

        long id = getLongFromJson(payload, "id");
        if (id <= 0) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "id is required"); return;
        }

        try {
            boolean deleted = database.deletePreset(id);
            if (!deleted) {
                wsServer.sendError(ws, requestId, "NOT_FOUND", "Preset not found"); return;
            }

            auditLogger.logModerationAction(session.getUserId(), session.getUsername(),
                    AuditLogger.MODERATION_PRESET_DELETE, "preset#" + id, "ID: " + id, session.getIpAddress());

            wsServer.sendResponse(ws, requestId, new JsonObject());
        } catch (SQLException e) {
            logger.severe("[AppMH] moderation.presets.delete DB error: " + e.getMessage());
            wsServer.sendError(ws, requestId, "DATABASE_ERROR", "Failed to delete preset");
        }
    }

    private void handleModerationCalculate(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (!requireAuth(ws, requestId, session)) return;

        if (!payload.has("presetIds") || !payload.get("presetIds").isJsonArray()) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "presetIds array is required"); return;
        }

        List<Long> presetIds = new ArrayList<>();
        for (JsonElement el : payload.getAsJsonArray("presetIds")) {
            try { presetIds.add(el.getAsLong()); } catch (Exception ignored) {}
        }

        if (presetIds.isEmpty()) {
            wsServer.sendError(ws, requestId, "VALIDATION_ERROR", "presetIds must not be empty"); return;
        }

        try {
            List<Map<String, Object>> presetRows = new ArrayList<>();
            for (long pid : presetIds) {
                Map<String, Object> row = database.getPresetById(pid);
                if (row != null) presetRows.add(row);
            }

            long totalDuration    = 0;
            boolean permanent     = false;
            boolean requiresIpBan = false;
            boolean hasCapBypass  = false;
            String  resultAction  = "WARN";
            int     highestSeverity = 0;

            JsonArray presetsArr = new JsonArray();

            for (Map<String, Object> row : presetRows) {
                PunishmentPreset preset = PunishmentPreset.fromRow(row);
                presetsArr.add(preset.toJson());

                long dur      = preset.getDurationSeconds();
                String action = preset.getActionType();
                int sev       = preset.getSeverity();

                // Determine the most severe action type
                if (sev > highestSeverity) {
                    highestSeverity = sev;
                    resultAction    = action;
                }

                // Permanent BAN overrides everything
                if (("BAN".equals(action) || "IP_BAN".equals(action)) && dur == 0) {
                    permanent = true;
                }

                if (preset.isBypassCap() && ("BAN".equals(action) || "TEMP_BAN".equals(action) || "IP_BAN".equals(action) || "TEMP_IP_BAN".equals(action))) {
                    hasCapBypass = true;
                }

                if (preset.isRequiresIpBan()) requiresIpBan = true;

                if (!permanent && preset.isStackable()) totalDuration += dur;
            }

            // Cap at 30 days unless bypass
            final long MAX_SECONDS = 2_592_000L;
            if (!permanent && !hasCapBypass && totalDuration > MAX_SECONDS) {
                totalDuration = MAX_SECONDS;
            }

            JsonObject resp = new JsonObject();
            resp.addProperty("actionType",      resultAction);
            resp.addProperty("durationSeconds", permanent ? 0 : totalDuration);
            resp.addProperty("requiresIpBan",   requiresIpBan);
            resp.addProperty("permanent",        permanent);
            resp.add("presets", presetsArr);
            wsServer.sendResponse(ws, requestId, resp);
        } catch (SQLException e) {
            logger.severe("[AppMH] moderation.calculate DB error: " + e.getMessage());
            wsServer.sendError(ws, requestId, "DATABASE_ERROR", "Failed to calculate punishment");
        }
    }

    private void handleModerationDashboard(WebSocket ws, String requestId, JsonObject payload, ClientSession session) {
        if (!requireAuth(ws, requestId, session)) return;
        if (!requirePermission(ws, requestId, session, "moderation.view")) return;

        boolean showIp = session.hasPermission("moderation.view_ip");

        try {
            List<Map<String, Object>> recentRows = database.getRecentPunishments(10);
            JsonArray recentArr = new JsonArray();
            for (Map<String, Object> row : recentRows) {
                recentArr.add(Punishment.fromRow(row).toJson(showIp));
            }

            // Active ban count: BAN + TEMP_BAN
            int activeBans  = database.getActivePunishmentCount("BAN")
                            + database.getActivePunishmentCount("TEMP_BAN")
                            + database.getActivePunishmentCount("IP_BAN")
                            + database.getActivePunishmentCount("TEMP_IP_BAN");
            int activeMutes = database.getActivePunishmentCount("MUTE")
                            + database.getActivePunishmentCount("TEMP_MUTE");
            int today       = database.getPunishmentsToday();

            JsonObject resp = new JsonObject();
            resp.add("recentPunishments", recentArr);
            resp.addProperty("activeBans",          activeBans);
            resp.addProperty("activeMutes",         activeMutes);
            resp.addProperty("punishmentsToday",    today);
            wsServer.sendResponse(ws, requestId, resp);
        } catch (SQLException e) {
            logger.severe("[AppMH] moderation.dashboard DB error: " + e.getMessage());
            wsServer.sendError(ws, requestId, "DATABASE_ERROR", "Failed to retrieve dashboard data");
        }
    }

    // ---- Helpers ----

    private boolean requireAuth(WebSocket ws, String requestId, ClientSession session) {
        if (session == null) { wsServer.sendError(ws, requestId, "NOT_AUTHENTICATED", "You must be logged in"); return false; }
        return true;
    }

    private boolean requirePermission(WebSocket ws, String requestId, ClientSession session, String node) {
        if (!session.hasPermission(node)) {
            wsServer.sendError(ws, requestId, "FORBIDDEN", "You do not have permission: " + node); return false;
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
        if (permissions != null) for (String p : permissions) permsArray.add(p);
        obj.add("permissions", permsArray);
        return obj;
    }

    private String getString(JsonObject obj, String key) {
        return (obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsString() : null;
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
        return val instanceof Number ? ((Number) val).longValue() : 0L;
    }

    private int getInt(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val instanceof Number ? ((Number) val).intValue() : 0;
    }

    private String buildBanMessage(String actionType, String reason, long expiresAt) {
        StringBuilder msg = new StringBuilder();
        if ("IP_BAN".equals(actionType) || "TEMP_IP_BAN".equals(actionType)) {
            msg.append("You are IP-banned from this network.\n");
        } else {
            msg.append("You are banned from this network.\n");
        }
        msg.append("Reason: ").append(reason != null ? reason : "No reason provided");
        if (expiresAt > 0) {
            msg.append("\nExpires: ").append(BAN_DATE_FMT.format(Instant.ofEpochMilli(expiresAt)));
        } else {
            msg.append("\nDuration: Permanent");
        }
        return msg.toString();
    }
}
