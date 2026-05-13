package com.staffcontrol.proxy.model;

import com.google.gson.JsonObject;

import java.util.Map;

public class ServerInfo {

    private String serverId;
    private String serverName;
    private String serverType;
    private String host;
    private int port;
    private boolean isOnline;
    private int playerCount;
    private int maxPlayers;
    private double tps;
    private double mspt;
    private long lastHeartbeat;
    private String minecraftVersion;
    private String paperVersion;
    private int pluginCount;
    private int loadedChunks;
    private String agentToken;

    public ServerInfo() {}

    public ServerInfo(String serverId, String serverName, String serverType, String host, int port,
                      boolean isOnline, int playerCount, int maxPlayers, double tps, double mspt,
                      long lastHeartbeat, String minecraftVersion, String paperVersion,
                      int pluginCount, int loadedChunks, String agentToken) {
        this.serverId = serverId;
        this.serverName = serverName;
        this.serverType = serverType;
        this.host = host;
        this.port = port;
        this.isOnline = isOnline;
        this.playerCount = playerCount;
        this.maxPlayers = maxPlayers;
        this.tps = tps;
        this.mspt = mspt;
        this.lastHeartbeat = lastHeartbeat;
        this.minecraftVersion = minecraftVersion;
        this.paperVersion = paperVersion;
        this.pluginCount = pluginCount;
        this.loadedChunks = loadedChunks;
        this.agentToken = agentToken;
    }

    public static ServerInfo fromDatabase(Map<String, Object> row) {
        ServerInfo s = new ServerInfo();
        s.serverId = getString(row, "server_id");
        s.serverName = getString(row, "server_name");
        s.serverType = getString(row, "server_type");
        s.host = getString(row, "host");
        Object portObj = row.get("port");
        s.port = portObj instanceof Number ? ((Number) portObj).intValue() : 0;
        s.agentToken = getString(row, "agent_token");
        s.isOnline = false;
        s.playerCount = 0;
        s.maxPlayers = 0;
        s.tps = 0.0;
        s.mspt = 0.0;
        s.lastHeartbeat = 0L;
        return s;
    }

    private static String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : null;
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("serverId", serverId);
        obj.addProperty("serverName", serverName);
        obj.addProperty("serverType", serverType);
        obj.addProperty("host", host);
        obj.addProperty("port", port);
        obj.addProperty("isOnline", isOnline);
        obj.addProperty("playerCount", playerCount);
        obj.addProperty("maxPlayers", maxPlayers);
        obj.addProperty("tps", tps);
        obj.addProperty("mspt", mspt);
        obj.addProperty("lastHeartbeat", lastHeartbeat);
        if (minecraftVersion != null) obj.addProperty("minecraftVersion", minecraftVersion);
        if (paperVersion != null) obj.addProperty("paperVersion", paperVersion);
        obj.addProperty("pluginCount", pluginCount);
        obj.addProperty("loadedChunks", loadedChunks);
        return obj;
    }

    public String getServerId() { return serverId; }
    public void setServerId(String serverId) { this.serverId = serverId; }

    public String getServerName() { return serverName; }
    public void setServerName(String serverName) { this.serverName = serverName; }

    public String getServerType() { return serverType; }
    public void setServerType(String serverType) { this.serverType = serverType; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public boolean isOnline() { return isOnline; }
    public void setOnline(boolean online) { isOnline = online; }

    public int getPlayerCount() { return playerCount; }
    public void setPlayerCount(int playerCount) { this.playerCount = playerCount; }

    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }

    public double getTps() { return tps; }
    public void setTps(double tps) { this.tps = tps; }

    public double getMspt() { return mspt; }
    public void setMspt(double mspt) { this.mspt = mspt; }

    public long getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(long lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }

    public String getMinecraftVersion() { return minecraftVersion; }
    public void setMinecraftVersion(String minecraftVersion) { this.minecraftVersion = minecraftVersion; }

    public String getPaperVersion() { return paperVersion; }
    public void setPaperVersion(String paperVersion) { this.paperVersion = paperVersion; }

    public int getPluginCount() { return pluginCount; }
    public void setPluginCount(int pluginCount) { this.pluginCount = pluginCount; }

    public int getLoadedChunks() { return loadedChunks; }
    public void setLoadedChunks(int loadedChunks) { this.loadedChunks = loadedChunks; }

    public String getAgentToken() { return agentToken; }
    public void setAgentToken(String agentToken) { this.agentToken = agentToken; }
}
