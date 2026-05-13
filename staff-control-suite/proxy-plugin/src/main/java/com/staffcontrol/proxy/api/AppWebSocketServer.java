package com.staffcontrol.proxy.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.staffcontrol.proxy.agent.AgentManager;
import com.staffcontrol.proxy.audit.AuditLogger;
import com.staffcontrol.proxy.auth.AuthManager;
import com.staffcontrol.proxy.config.ProxyConfig;
import com.staffcontrol.proxy.database.DatabaseManager;
import com.staffcontrol.proxy.model.PlayerInfo;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class AppWebSocketServer extends WebSocketServer {

    private final ProxyConfig config;
    private final DatabaseManager database;
    private final AuthManager authManager;
    private final AgentManager agentManager;
    private final AuditLogger auditLogger;
    private final Logger logger;

    private final ConcurrentHashMap<WebSocket, ClientSession> clientSessions = new ConcurrentHashMap<>();
    private AppMessageHandler messageHandler;
    private final Gson gson = new Gson();

    public AppWebSocketServer(ProxyConfig config, DatabaseManager database, AuthManager authManager,
                               AgentManager agentManager, AuditLogger auditLogger, Logger logger) {
        super(new InetSocketAddress(config.getApiPort()));
        this.config = config;
        this.database = database;
        this.authManager = authManager;
        this.agentManager = agentManager;
        this.auditLogger = auditLogger;
        this.logger = logger;
    }

    public void setMessageHandler(AppMessageHandler handler) {
        this.messageHandler = handler;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String remoteIp = conn.getRemoteSocketAddress().getAddress().getHostAddress();
        List<String> allowedIps = config.getAllowedIps();

        if (!allowedIps.isEmpty() && !allowedIps.contains(remoteIp)) {
            logger.warning("[AppWS] Rejected connection from " + remoteIp + " (not in allowedIps)");
            JsonObject rejection = new JsonObject();
            rejection.addProperty("type", "error");
            rejection.addProperty("message", "IP not allowed");
            conn.send(gson.toJson(rejection));
            conn.close();
            return;
        }

        logger.info("[AppWS] New connection from " + remoteIp);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        ClientSession session = clientSessions.remove(conn);
        if (session != null) {
            logger.info("[AppWS] Client disconnected: " + session.getUsername() + " (code=" + code + ")");
        } else {
            logger.info("[AppWS] Unauthenticated connection closed (code=" + code + ")");
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        JsonObject json;
        try {
            json = JsonParser.parseString(message).getAsJsonObject();
        } catch (Exception e) {
            sendError(conn, null, "PARSE_ERROR", "Invalid JSON");
            return;
        }

        String type = json.has("type") ? json.get("type").getAsString() : null;
        String requestId = json.has("requestId") && !json.get("requestId").isJsonNull()
                ? json.get("requestId").getAsString() : null;

        if (type == null) {
            sendError(conn, requestId, "MISSING_TYPE", "Message type is required");
            return;
        }

        ClientSession session = clientSessions.get(conn);

        if (messageHandler != null) {
            messageHandler.handle(conn, json, session);
        } else {
            sendError(conn, requestId, "SERVER_ERROR", "Message handler not initialized");
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        String remote = conn != null ? conn.getRemoteSocketAddress().toString() : "unknown";
        logger.warning("[AppWS] Error from " + remote + ": " + ex.getMessage());
    }

    @Override
    public void onStart() {
        logger.info("[AppWS] App WebSocket server started on port " + config.getApiPort());
    }

    public void broadcastConsoleOutput(String serverId, String line, long timestamp) {
        JsonObject event = new JsonObject();
        event.addProperty("type", "event.console.line");
        JsonObject payload = new JsonObject();
        payload.addProperty("serverId", serverId);
        payload.addProperty("line", line);
        payload.addProperty("timestamp", timestamp);
        event.add("payload", payload);
        String json = gson.toJson(event);

        for (Map.Entry<WebSocket, ClientSession> entry : clientSessions.entrySet()) {
            ClientSession session = entry.getValue();
            if (session.isSubscribed(serverId) && session.hasPermission("console.view")) {
                WebSocket ws = entry.getKey();
                if (ws.isOpen()) {
                    ws.send(json);
                }
            }
        }
    }

    public void broadcastServerStatus(String serverId, boolean online, int playerCount,
                                       double tps, double mspt, long lastHeartbeat) {
        JsonObject event = new JsonObject();
        event.addProperty("type", "event.server.status");
        JsonObject payload = new JsonObject();
        payload.addProperty("serverId", serverId);
        payload.addProperty("online", online);
        payload.addProperty("playerCount", playerCount);
        payload.addProperty("tps", tps);
        payload.addProperty("mspt", mspt);
        payload.addProperty("lastHeartbeat", lastHeartbeat);
        event.add("payload", payload);
        String json = gson.toJson(event);

        for (Map.Entry<WebSocket, ClientSession> entry : clientSessions.entrySet()) {
            ClientSession session = entry.getValue();
            if (session.hasPermission("servers.view")) {
                WebSocket ws = entry.getKey();
                if (ws.isOpen()) {
                    ws.send(json);
                }
            }
        }
    }

    public void broadcastPlayerUpdate(String serverId, List<PlayerInfo> players) {
        JsonObject event = new JsonObject();
        event.addProperty("type", "event.player.update");
        JsonObject payload = new JsonObject();
        payload.addProperty("serverId", serverId);
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        for (PlayerInfo p : players) {
            arr.add(p.toJson());
        }
        payload.add("players", arr);
        event.add("payload", payload);
        String json = gson.toJson(event);

        for (Map.Entry<WebSocket, ClientSession> entry : clientSessions.entrySet()) {
            ClientSession session = entry.getValue();
            if (session.hasPermission("players.view")) {
                WebSocket ws = entry.getKey();
                if (ws.isOpen()) {
                    ws.send(json);
                }
            }
        }
    }

    public void sendResponse(WebSocket ws, String requestId, JsonObject payload) {
        JsonObject response = new JsonObject();
        response.addProperty("type", "response");
        if (requestId != null) response.addProperty("requestId", requestId);
        response.addProperty("success", true);
        response.add("payload", payload);
        if (ws.isOpen()) {
            ws.send(gson.toJson(response));
        }
    }

    public void sendError(WebSocket ws, String requestId, String errorCode, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("type", "response");
        if (requestId != null) response.addProperty("requestId", requestId);
        response.addProperty("success", false);
        response.addProperty("error", errorCode);
        response.addProperty("message", message);
        if (ws != null && ws.isOpen()) {
            ws.send(gson.toJson(response));
        }
    }

    public ConcurrentHashMap<WebSocket, ClientSession> getClientSessions() {
        return clientSessions;
    }

    public void putSession(WebSocket ws, ClientSession session) {
        clientSessions.put(ws, session);
    }

    public void removeSession(WebSocket ws) {
        clientSessions.remove(ws);
    }
}
