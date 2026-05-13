package com.staffcontrol.proxy.agent;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.staffcontrol.proxy.model.ServerInfo;
import org.java_websocket.WebSocket;

public class AgentConnection {

    private static final Gson GSON = new Gson();

    private String serverId;
    private String serverName;
    private String serverType;
    private String host;
    private int port;
    private static final int PROTOCOL_VERSION = 1;

    private String version;
    private final WebSocket connection;
    private boolean registered;
    private volatile long lastHeartbeatAt;
    private ServerInfo serverInfo;
    private volatile long latencyMs = 0;
    private int reconnectAttempts = 0;
    private String lastError = null;
    private long connectTime;

    public AgentConnection(WebSocket conn) {
        this.connection = conn;
        this.registered = false;
        this.serverInfo = new ServerInfo();
        this.lastHeartbeatAt = System.currentTimeMillis();
        this.connectTime = System.currentTimeMillis();
    }

    public void send(String json) {
        if (connection != null && connection.isOpen()) {
            connection.send(json);
        }
    }

    public void send(JsonObject json) {
        send(GSON.toJson(json));
    }

    public boolean isConnected() {
        return connection != null && connection.isOpen();
    }

    public void updateFromHeartbeat(double tps, double mspt, int onlinePlayers,
                                     int maxPlayers, String version, long timestamp) {
        this.lastHeartbeatAt = timestamp;
        if (version != null) this.version = version;
        if (serverInfo == null) serverInfo = new ServerInfo();
        serverInfo.setServerId(serverId);
        serverInfo.setServerName(serverName);
        serverInfo.setServerType(serverType);
        serverInfo.setHost(host);
        serverInfo.setPort(port);
        serverInfo.setOnline(true);
        serverInfo.setTps(tps);
        serverInfo.setMspt(mspt);
        serverInfo.setPlayerCount(onlinePlayers);
        serverInfo.setMaxPlayers(maxPlayers);
        serverInfo.setLastHeartbeat(timestamp);
    }

    public boolean isHeartbeatTimedOut(long timeoutMs) {
        return registered && (System.currentTimeMillis() - lastHeartbeatAt) > timeoutMs;
    }

    public String getServerId() { return serverId; }
    public void setServerId(String serverId) {
        this.serverId = serverId;
        if (serverInfo != null) serverInfo.setServerId(serverId);
    }

    public String getServerName() { return serverName; }
    public void setServerName(String serverName) {
        this.serverName = serverName;
        if (serverInfo != null) serverInfo.setServerName(serverName);
    }

    public String getServerType() { return serverType; }
    public void setServerType(String serverType) {
        this.serverType = serverType;
        if (serverInfo != null) serverInfo.setServerType(serverType);
    }

    public String getHost() { return host; }
    public void setHost(String host) {
        this.host = host;
        if (serverInfo != null) serverInfo.setHost(host);
    }

    public int getPort() { return port; }
    public void setPort(int port) {
        this.port = port;
        if (serverInfo != null) serverInfo.setPort(port);
    }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public WebSocket getConnection() { return connection; }

    public boolean isRegistered() { return registered; }
    public void setRegistered(boolean registered) { this.registered = registered; }

    public long getLastHeartbeatAt() { return lastHeartbeatAt; }
    public void setLastHeartbeatAt(long lastHeartbeatAt) { this.lastHeartbeatAt = lastHeartbeatAt; }

    public ServerInfo getServerInfo() { return serverInfo; }
    public void setServerInfo(ServerInfo serverInfo) { this.serverInfo = serverInfo; }

    public int getProtocolVersion() { return PROTOCOL_VERSION; }

    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }

    public int getReconnectAttempts() { return reconnectAttempts; }
    public void setReconnectAttempts(int reconnectAttempts) { this.reconnectAttempts = reconnectAttempts; }
    public void incrementReconnectAttempts() { this.reconnectAttempts++; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    public long getConnectTime() { return connectTime; }
    public void setConnectTime(long connectTime) { this.connectTime = connectTime; }
}
