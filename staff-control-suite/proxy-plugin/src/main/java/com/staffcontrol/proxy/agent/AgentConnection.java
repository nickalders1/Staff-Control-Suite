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
    private final WebSocket connection;
    private boolean registered;
    private ServerInfo serverInfo;

    public AgentConnection(WebSocket conn) {
        this.connection = conn;
        this.registered = false;
        this.serverInfo = new ServerInfo();
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
                                     int maxPlayers, long timestamp) {
        if (serverInfo == null) {
            serverInfo = new ServerInfo();
        }
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

    public WebSocket getConnection() { return connection; }

    public boolean isRegistered() { return registered; }
    public void setRegistered(boolean registered) { this.registered = registered; }

    public ServerInfo getServerInfo() { return serverInfo; }
    public void setServerInfo(ServerInfo serverInfo) { this.serverInfo = serverInfo; }
}
