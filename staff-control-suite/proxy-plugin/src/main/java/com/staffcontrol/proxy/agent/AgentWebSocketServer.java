package com.staffcontrol.proxy.agent;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.staffcontrol.proxy.api.AppWebSocketServer;
import com.staffcontrol.proxy.config.ProxyConfig;
import com.staffcontrol.proxy.database.DatabaseManager;
import com.staffcontrol.proxy.model.PlayerInfo;
import com.staffcontrol.proxy.model.ServerInfo;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class AgentWebSocketServer extends WebSocketServer {

    private final ProxyConfig config;
    private final AgentManager agentManager;
    private final AppWebSocketServer appServer;
    private final DatabaseManager database;
    private final Logger logger;

    private final ConcurrentHashMap<WebSocket, String> connectionToServerId = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();

    public AgentWebSocketServer(ProxyConfig config, AgentManager agentManager,
                                 AppWebSocketServer appServer, DatabaseManager database,
                                 Logger logger) {
        super(new InetSocketAddress(config.getAgentPort()));
        this.config = config;
        this.agentManager = agentManager;
        this.appServer = appServer;
        this.database = database;
        this.logger = logger;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        logger.info("[AgentWS] New agent connection from " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String serverId = connectionToServerId.remove(conn);
        if (serverId != null) {
            logger.info("[AgentWS] Agent disconnected: " + serverId + " (code=" + code + ")");
            agentManager.removeAgent(serverId);

            // Broadcast offline status to all app clients
            appServer.broadcastServerStatus(serverId, false, 0, 0.0, 0.0, System.currentTimeMillis());

            // Set all players on this server to offline in the cache
            try {
                database.setAllPlayersOfflineForServer(serverId, System.currentTimeMillis());
            } catch (SQLException e) {
                logger.warning("[AgentWS] Failed to set players offline for server " + serverId + ": " + e.getMessage());
            }

            // Broadcast empty player list for the server
            appServer.broadcastPlayerUpdate(serverId, new ArrayList<>());
        } else {
            logger.info("[AgentWS] Unregistered agent connection closed (code=" + code + ")");
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        JsonObject json;
        try {
            json = JsonParser.parseString(message).getAsJsonObject();
        } catch (Exception e) {
            logger.warning("[AgentWS] Failed to parse message: " + e.getMessage());
            return;
        }

        String type = json.has("type") ? json.get("type").getAsString() : null;
        if (type == null) {
            logger.warning("[AgentWS] Message missing 'type' field");
            return;
        }

        switch (type) {
            case "agent.register"  -> handleAgentRegister(conn, json);
            case "agent.heartbeat" -> handleAgentHeartbeat(conn, json);
            case "console.output"  -> handleConsoleOutput(conn, json);
            case "players.update"  -> handlePlayersUpdate(conn, json);
            default -> logger.warning("[AgentWS] Unknown message type from agent: " + type);
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        String remote = conn != null ? conn.getRemoteSocketAddress().toString() : "unknown";
        logger.warning("[AgentWS] Error from " + remote + ": " + ex.getMessage());
    }

    @Override
    public void onStart() {
        logger.info("[AgentWS] Agent WebSocket server started on port " + config.getAgentPort());
    }

    // ---- Message Handlers ----

    private void handleAgentRegister(WebSocket conn, JsonObject json) {
        String agentToken = json.has("agentToken") ? json.get("agentToken").getAsString() : null;
        JsonObject payload = json.has("payload") && json.get("payload").isJsonObject()
                ? json.getAsJsonObject("payload") : new JsonObject();

        String serverId   = getString(payload, "serverId");
        String serverName = getString(payload, "serverName");
        String serverType = getString(payload, "serverType");
        String host       = getString(payload, "host");
        int port          = payload.has("port") ? payload.get("port").getAsInt() : 0;

        if (serverId == null || agentToken == null) {
            sendRegistrationResult(conn, false, "serverId and agentToken are required");
            return;
        }

        // Validate token — check against global agent token OR server-specific token in DB
        boolean tokenValid = false;

        // Check global token first
        if (agentToken.equals(config.getAgentToken())) {
            tokenValid = true;
        }

        // Check server-specific token from DB
        if (!tokenValid) {
            try {
                Map<String, Object> serverRow = database.getServer(serverId);
                if (serverRow != null) {
                    String storedToken = getString(serverRow, "agent_token");
                    if (agentToken.equals(storedToken)) {
                        tokenValid = true;
                    }
                }
            } catch (SQLException e) {
                logger.warning("[AgentWS] DB error validating agent token: " + e.getMessage());
            }
        }

        if (!tokenValid) {
            sendRegistrationResult(conn, false, "Invalid agent token");
            logger.warning("[AgentWS] Agent registration rejected for serverId=" + serverId + " (invalid token)");
            return;
        }

        // If server doesn't exist in DB yet, it was registered via the app (servers.add) — look it up
        Map<String, Object> serverRow = null;
        try {
            serverRow = database.getServer(serverId);
        } catch (SQLException e) {
            logger.warning("[AgentWS] DB error fetching server: " + e.getMessage());
        }

        // Build AgentConnection
        AgentConnection agentConn = new AgentConnection(conn);
        agentConn.setServerId(serverId);
        agentConn.setServerName(serverName != null ? serverName
                : (serverRow != null ? getString(serverRow, "server_name") : serverId));
        agentConn.setServerType(serverType != null ? serverType
                : (serverRow != null ? getString(serverRow, "server_type") : "generic"));
        agentConn.setHost(host);
        agentConn.setPort(port);
        agentConn.setRegistered(true);

        // Initialise serverInfo skeleton
        ServerInfo si = new ServerInfo();
        si.setServerId(serverId);
        si.setServerName(agentConn.getServerName());
        si.setServerType(agentConn.getServerType());
        si.setHost(host);
        si.setPort(port);
        si.setOnline(true);
        si.setLastHeartbeat(System.currentTimeMillis());
        agentConn.setServerInfo(si);

        agentManager.registerAgent(serverId, agentConn);
        connectionToServerId.put(conn, serverId);

        sendRegistrationResult(conn, true, "Registered successfully");
        logger.info("[AgentWS] Agent registered: " + serverId + " (" + agentConn.getServerName() + ")");

        // Broadcast online status to all app clients
        appServer.broadcastServerStatus(serverId, true, 0, 20.0, 0.0, System.currentTimeMillis());
    }

    private void handleAgentHeartbeat(WebSocket conn, JsonObject json) {
        String serverId = json.has("serverId") ? json.get("serverId").getAsString() : null;
        if (serverId == null) serverId = connectionToServerId.get(conn);
        if (serverId == null) return;

        JsonObject payload = json.has("payload") && json.get("payload").isJsonObject()
                ? json.getAsJsonObject("payload") : new JsonObject();

        double tps          = payload.has("tps")           ? payload.get("tps").getAsDouble()          : 20.0;
        double mspt         = payload.has("mspt")          ? payload.get("mspt").getAsDouble()          : 0.0;
        int onlinePlayers   = payload.has("onlinePlayers") ? payload.get("onlinePlayers").getAsInt()    : 0;
        int maxPlayers      = payload.has("maxPlayers")    ? payload.get("maxPlayers").getAsInt()       : 0;
        long timestamp      = System.currentTimeMillis();

        agentManager.getAgent(serverId).ifPresent(agent ->
            agent.updateFromHeartbeat(tps, mspt, onlinePlayers, maxPlayers, timestamp)
        );

        appServer.broadcastServerStatus(serverId, true, onlinePlayers, tps, mspt, timestamp);
    }

    private void handleConsoleOutput(WebSocket conn, JsonObject json) {
        String serverId = json.has("serverId") ? json.get("serverId").getAsString() : null;
        if (serverId == null) serverId = connectionToServerId.get(conn);
        if (serverId == null) return;

        JsonObject payload = json.has("payload") && json.get("payload").isJsonObject()
                ? json.getAsJsonObject("payload") : new JsonObject();

        String line       = getString(payload, "line");
        long timestamp    = payload.has("timestamp") ? payload.get("timestamp").getAsLong() : System.currentTimeMillis();

        if (line == null) return;

        appServer.broadcastConsoleOutput(serverId, line, timestamp);
    }

    private void handlePlayersUpdate(WebSocket conn, JsonObject json) {
        String serverId = json.has("serverId") ? json.get("serverId").getAsString() : null;
        if (serverId == null) serverId = connectionToServerId.get(conn);
        if (serverId == null) return;

        JsonObject payload = json.has("payload") && json.get("payload").isJsonObject()
                ? json.getAsJsonObject("payload") : new JsonObject();

        JsonArray playersArray = payload.has("players") && payload.get("players").isJsonArray()
                ? payload.getAsJsonArray("players") : new JsonArray();

        List<PlayerInfo> players = new ArrayList<>();
        List<String> receivedUuids = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (JsonElement el : playersArray) {
            if (!el.isJsonObject()) continue;
            JsonObject p = el.getAsJsonObject();

            String uuid     = getString(p, "uuid");
            String name     = getString(p, "name");
            String world    = getString(p, "world");
            String gamemode = getString(p, "gamemode");
            double health   = p.has("health")    ? p.get("health").getAsDouble()    : 20.0;
            int foodLevel   = p.has("foodLevel") ? p.get("foodLevel").getAsInt()    : 20;
            int ping        = p.has("ping")      ? p.get("ping").getAsInt()         : 0;

            if (uuid == null || name == null) continue;

            receivedUuids.add(uuid);

            // Upsert into player cache
            try {
                database.upsertPlayer(uuid, name, serverId, true, now);
                database.setFirstJoined(uuid, now);
            } catch (SQLException e) {
                logger.warning("[AgentWS] Failed to upsert player " + uuid + ": " + e.getMessage());
            }

            PlayerInfo pi = new PlayerInfo(uuid, name, serverId, world, gamemode,
                    health, foodLevel, ping, true, 0L, now, 0L);
            players.add(pi);
        }

        // Set players that were previously online on this server but not in the update to offline
        try {
            List<Map<String, Object>> currentOnline = database.getPlayersByServer(serverId);
            for (Map<String, Object> row : currentOnline) {
                String uuid = getString(row, "uuid");
                if (uuid != null && !receivedUuids.contains(uuid)) {
                    database.setPlayerOffline(uuid, now);
                }
            }
        } catch (SQLException e) {
            logger.warning("[AgentWS] Failed to update offline players for server " + serverId + ": " + e.getMessage());
        }

        appServer.broadcastPlayerUpdate(serverId, players);
    }

    // ---- Utility ----

    private void sendRegistrationResult(WebSocket conn, boolean success, String message) {
        JsonObject resp = new JsonObject();
        resp.addProperty("type", "agent.registered");
        resp.addProperty("success", success);
        resp.addProperty("message", message);
        if (conn.isOpen()) {
            conn.send(gson.toJson(resp));
        }
    }

    private String getString(JsonObject obj, String key) {
        if (obj != null && obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return null;
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : null;
    }
}
