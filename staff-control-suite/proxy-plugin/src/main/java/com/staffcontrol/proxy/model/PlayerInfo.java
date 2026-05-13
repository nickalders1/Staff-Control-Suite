package com.staffcontrol.proxy.model;

import com.google.gson.JsonObject;

import java.util.Map;

public class PlayerInfo {

    private String uuid;
    private String name;
    private String serverId;
    private String world;
    private String gamemode;
    private double health;
    private int foodLevel;
    private int ping;
    private boolean isOnline;
    private long firstJoined;
    private long lastJoined;
    private long playtimeSeconds;

    public PlayerInfo() {}

    public PlayerInfo(String uuid, String name, String serverId, String world, String gamemode,
                      double health, int foodLevel, int ping, boolean isOnline,
                      long firstJoined, long lastJoined, long playtimeSeconds) {
        this.uuid = uuid;
        this.name = name;
        this.serverId = serverId;
        this.world = world;
        this.gamemode = gamemode;
        this.health = health;
        this.foodLevel = foodLevel;
        this.ping = ping;
        this.isOnline = isOnline;
        this.firstJoined = firstJoined;
        this.lastJoined = lastJoined;
        this.playtimeSeconds = playtimeSeconds;
    }

    public static PlayerInfo fromDatabase(Map<String, Object> row) {
        PlayerInfo p = new PlayerInfo();
        p.uuid = getString(row, "uuid");
        p.name = getString(row, "name");
        p.serverId = getString(row, "server_id");
        p.isOnline = getInt(row, "is_online") == 1;
        p.firstJoined = getLong(row, "first_joined");
        p.lastJoined = getLong(row, "last_seen");
        p.playtimeSeconds = getLong(row, "playtime_seconds");
        p.world = null;
        p.gamemode = null;
        p.health = 20.0;
        p.foodLevel = 20;
        p.ping = 0;
        return p;
    }

    private static String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : null;
    }

    private static int getInt(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        return 0;
    }

    private static long getLong(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).longValue();
        return 0L;
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("uuid", uuid);
        obj.addProperty("name", name);
        obj.addProperty("serverId", serverId);
        if (world != null) obj.addProperty("world", world);
        if (gamemode != null) obj.addProperty("gamemode", gamemode);
        obj.addProperty("health", health);
        obj.addProperty("foodLevel", foodLevel);
        obj.addProperty("ping", ping);
        obj.addProperty("isOnline", isOnline);
        obj.addProperty("firstJoined", firstJoined);
        obj.addProperty("lastJoined", lastJoined);
        obj.addProperty("playtimeSeconds", playtimeSeconds);
        return obj;
    }

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getServerId() { return serverId; }
    public void setServerId(String serverId) { this.serverId = serverId; }

    public String getWorld() { return world; }
    public void setWorld(String world) { this.world = world; }

    public String getGamemode() { return gamemode; }
    public void setGamemode(String gamemode) { this.gamemode = gamemode; }

    public double getHealth() { return health; }
    public void setHealth(double health) { this.health = health; }

    public int getFoodLevel() { return foodLevel; }
    public void setFoodLevel(int foodLevel) { this.foodLevel = foodLevel; }

    public int getPing() { return ping; }
    public void setPing(int ping) { this.ping = ping; }

    public boolean isOnline() { return isOnline; }
    public void setOnline(boolean online) { isOnline = online; }

    public long getFirstJoined() { return firstJoined; }
    public void setFirstJoined(long firstJoined) { this.firstJoined = firstJoined; }

    public long getLastJoined() { return lastJoined; }
    public void setLastJoined(long lastJoined) { this.lastJoined = lastJoined; }

    public long getPlaytimeSeconds() { return playtimeSeconds; }
    public void setPlaytimeSeconds(long playtimeSeconds) { this.playtimeSeconds = playtimeSeconds; }
}
