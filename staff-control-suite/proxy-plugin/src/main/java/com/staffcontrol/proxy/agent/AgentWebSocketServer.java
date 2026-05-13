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
import com.staffcontrol.proxy.player.PlayerManager;
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
    private final PlayerManager playerManager;
    private final Logger logger;

    private final ConcurrentHashMap<WebSocket, String> connectionToServerId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> reconnectCounts = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();

    public AgentWebSocketServer(ProxyConfig config, AgentManager agentManager,
                                 AppWebSocketServer appServer, DatabaseManager database,
                                 PlayerManager playerManager, Logger logger) {
        super(new InetSocketAddress(config.getAgentPort()));
        this.config = config;
        this.agentManager = agentManager;
        this.appServer = appServer;
        this.database = database;
        this.playerManager = playerManager;
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
            String lastError = (code != 1000 && reason != null && !reason.isBlank()) ? reason : null;
            agentManager.removeAgent(serverId);

            List<String> removedPlayers = playerManager.agentDisconnected(serverId);
            for (String uuid : removedPlayers) {
                appServer.broadcastPlayerLeft(uuid, serverId);
            }

            long now = System.currentTimeMillis();
            appServer.broadcastServerStatus(serverId, false, 0, 0.0, 0.0, now);
            appServer.broadcastAgentStatus(serverId, false, "OFFLINE", null, 0, 0, 0, 0,
                    reconnectCounts.getOrDefault(serverId, 0), lastError);
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
        if (type == null) return;

        switch (type) {
            case "agent.register"    -> handleAgentRegister(conn, json);
            case "agent.heartbeat"   -> handleAgentHeartbeat(conn, json);
            case "agent.player.join" -> handlePlayerJoin(conn, json);
            case "agent.player.leave"-> handlePlayerLeave(conn, json);
            case "console.output"    -> handleConsoleOutput(conn, json);
            case "players.update"    -> handlePlayersUpdate(conn, json);
            default -> logger.fine("[AgentWS] Unknown message type from agent: " + type);
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
        String version    = getString(payload, "version");
        int port          = payload.has("port") ? payload.get("port").getAsInt() : 0;

        if (serverId == null || agentToken == null) {
            sendRegistrationResult(conn, false, "serverId and agentToken are required");
            return;
        }

        boolean tokenValid = agentToken.equals(config.getAgentToken());
        if (!tokenValid) {
            try {
                Map<String, Object> serverRow = database.getServer(serverId);
                if (serverRow != null) {
                    String storedToken = getString(serverRow, "agent_token");
                    tokenValid = agentToken.equals(storedToken);
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

        Map<String, Object> serverRow = null;
        try { serverRow = database.getServer(serverId); } catch (SQLException ignored) {}

        AgentConnection agentConn = new AgentConnection(conn);
        agentConn.setServerId(serverId);
        agentConn.setServerName(serverName != null ? serverName
                : (serverRow != null ? getString(serverRow, "server_name") : serverId));
        agentConn.setServerType(serverType != null ? serverType
                : (serverRow != null ? getString(serverRow, "server_type") : "generic"));
        agentConn.setHost(host);
        agentConn.setPort(port);
        if (version != null) agentConn.setVersion(version);
        agentConn.setRegistered(true);

        ServerInfo si = new ServerInfo();
        si.setServerId(serverId);
        si.setServerName(agentConn.getServerName());
        si.setServerType(agentConn.getServerType());
        si.setHost(host);
        si.setPort(port);
        si.setOnline(true);
        si.setLastHeartbeat(System.currentTimeMillis());
        agentConn.setServerInfo(si);

        // Track reconnect attempts — first connection = 0, each re-registration increments
        int attempts = reconnectCounts.compute(serverId, (k, v) -> v == null ? 0 : v + 1);
        agentConn.setReconnectAttempts(attempts);
        agentConn.setConnectTime(System.currentTimeMillis());

        agentManager.registerAgent(serverId, agentConn);
        connectionToServerId.put(conn, serverId);

        sendRegistrationResult(conn, true, "Registered successfully");
        logger.info("[AgentWS] Agent registered: " + serverId + " (" + agentConn.getServerName() + ")"
                + (version != null ? " v" + version : ""));

        long now = System.currentTimeMillis();
        appServer.broadcastServerStatus(serverId, true, 0, 20.0, 0.0, now);
        appServer.broadcastAgentStatus(serverId, true, "ONLINE", agentConn.getVersion(),
                agentConn.getProtocolVersion(), now, 0, agentConn.getConnectTime(), attempts, null);
    }

    private void handleAgentHeartbeat(WebSocket conn, JsonObject json) {
        String serverId = resolveServerId(conn, json);
        if (serverId == null) return;

        JsonObject payload = json.has("payload") && json.get("payload").isJsonObject()
                ? json.getAsJsonObject("payload") : new JsonObject();

        double tps        = payload.has("tps")           ? payload.get("tps").getAsDouble()       : 20.0;
        double mspt       = payload.has("mspt")          ? payload.get("mspt").getAsDouble()       : 0.0;
        int onlinePlayers = payload.has("onlinePlayers") ? payload.get("onlinePlayers").getAsInt() : 0;
        int maxPlayers    = payload.has("maxPlayers")    ? payload.get("maxPlayers").getAsInt()    : 0;
        String version    = getString(payload, "version");
        long now          = System.currentTimeMillis();
        long sentAt       = payload.has("sentAt") ? payload.get("sentAt").getAsLong() : 0L;
        long latency      = sentAt > 0 ? Math.max(0, now - sentAt) : 0L;

        agentManager.getAgent(serverId).ifPresent(agent -> {
            agent.updateFromHeartbeat(tps, mspt, onlinePlayers, maxPlayers, version, now);
            if (latency > 0) agent.setLatencyMs(latency);
        });

        appServer.broadcastServerStatus(serverId, true, onlinePlayers, tps, mspt, now);

        agentManager.getAgent(serverId).ifPresent(agent ->
            appServer.broadcastAgentStatus(serverId, true, "ONLINE", agent.getVersion(),
                    agent.getProtocolVersion(), now, agent.getLatencyMs(),
                    agent.getConnectTime(), agent.getReconnectAttempts(), null)
        );
    }

    private void handlePlayerJoin(WebSocket conn, JsonObject json) {
        String serverId = resolveServerId(conn, json);
        if (serverId == null) return;

        JsonObject payload = json.has("payload") && json.get("payload").isJsonObject()
                ? json.getAsJsonObject("payload") : new JsonObject();

        PlayerInfo player = parsePlayerInfo(payload, serverId, System.currentTimeMillis());
        if (player.getUuid() == null || player.getName() == null) return;

        String previousServer = playerManager.playerJoined(player);

        if (previousServer != null) {
            appServer.broadcastPlayerSwitched(player, previousServer);
            logger.fine("[AgentWS] Player " + player.getName() + " switched from " + previousServer + " to " + serverId);
        } else {
            appServer.broadcastPlayerJoined(player);
            logger.fine("[AgentWS] Player " + player.getName() + " joined network on " + serverId);
        }
    }

    private void handlePlayerLeave(WebSocket conn, JsonObject json) {
        String serverId = resolveServerId(conn, json);
        if (serverId == null) return;

        JsonObject payload = json.has("payload") && json.get("payload").isJsonObject()
                ? json.getAsJsonObject("payload") : new JsonObject();

        String uuid = getString(payload, "uuid");
        if (uuid == null) return;

        long playtimeSeconds = payload.has("playtimeSeconds") ? payload.get("playtimeSeconds").getAsLong() : 0L;

        boolean removed = playerManager.playerLeft(uuid, serverId, playtimeSeconds);
        if (removed) {
            appServer.broadcastPlayerLeft(uuid, serverId);
            logger.fine("[AgentWS] Player " + uuid + " left " + serverId);
        }
    }

    private void handleConsoleOutput(WebSocket conn, JsonObject json) {
        String serverId = resolveServerId(conn, json);
        if (serverId == null) return;

        JsonObject payload = json.has("payload") && json.get("payload").isJsonObject()
                ? json.getAsJsonObject("payload") : new JsonObject();

        String line    = getString(payload, "line");
        long timestamp = payload.has("timestamp") ? payload.get("timestamp").getAsLong() : System.currentTimeMillis();
        if (line == null) return;

        appServer.broadcastConsoleOutput(serverId, line, timestamp);
    }

    private void handlePlayersUpdate(WebSocket conn, JsonObject json) {
        String serverId = resolveServerId(conn, json);
        if (serverId == null) return;

        JsonObject payload = json.has("payload") && json.get("payload").isJsonObject()
                ? json.getAsJsonObject("payload") : new JsonObject();

        JsonArray playersArray = payload.has("players") && payload.get("players").isJsonArray()
                ? payload.getAsJsonArray("players") : new JsonArray();

        long now = System.currentTimeMillis();
        List<PlayerInfo> players = new ArrayList<>();

        for (JsonElement el : playersArray) {
            if (!el.isJsonObject()) continue;
            PlayerInfo pi = parsePlayerInfo(el.getAsJsonObject(), serverId, now);
            if (pi.getUuid() != null && pi.getName() != null) {
                players.add(pi);
            }
        }

        List<String> removedUuids = playerManager.reconcileServerPlayers(serverId, players);
        for (String uuid : removedUuids) {
            appServer.broadcastPlayerLeft(uuid, serverId);
        }

        appServer.broadcastPlayerUpdate(serverId, players);
    }

    // ---- Utility ----

    private PlayerInfo parsePlayerInfo(JsonObject p, String serverId, long now) {
        PlayerInfo pi = new PlayerInfo();
        pi.setUuid(getString(p, "uuid"));
        pi.setName(getString(p, "name"));
        pi.setServerId(serverId);
        pi.setWorld(getString(p, "world"));
        pi.setGamemode(getString(p, "gamemode"));
        pi.setHealth(p.has("health") ? p.get("health").getAsDouble() : 20.0);
        pi.setFoodLevel(p.has("foodLevel") ? p.get("foodLevel").getAsInt() : 20);
        pi.setPing(p.has("ping") ? p.get("ping").getAsInt() : 0);
        pi.setOnline(true);
        pi.setFirstJoined(p.has("firstJoined") ? p.get("firstJoined").getAsLong() : now);
        pi.setLastJoined(p.has("lastJoined") ? p.get("lastJoined").getAsLong() : now);
        pi.setPlaytimeSeconds(p.has("playtimeSeconds") ? p.get("playtimeSeconds").getAsLong() : 0L);
        return pi;
    }

    private String resolveServerId(WebSocket conn, JsonObject json) {
        String serverId = json.has("serverId") ? json.get("serverId").getAsString() : null;
        if (serverId == null) serverId = connectionToServerId.get(conn);
        return serverId;
    }

    private void sendRegistrationResult(WebSocket conn, boolean success, String message) {
        JsonObject resp = new JsonObject();
        resp.addProperty("type", "agent.registered");
        resp.addProperty("success", success);
        resp.addProperty("message", message);
        if (conn.isOpen()) conn.send(gson.toJson(resp));
    }

    private String getString(JsonObject obj, String key) {
        return (obj != null && obj.has(key) && !obj.get(key).isJsonNull())
                ? obj.get(key).getAsString() : null;
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : null;
    }
}
