package com.staffcontrol.proxy.api;

import org.java_websocket.WebSocket;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ClientSession {

    private final long userId;
    private final String username;
    private final Set<String> permissions;
    private final String token;
    private final Set<String> subscribedServers;
    private final WebSocket connection;
    private final String ipAddress;

    // All 15 known permission nodes — owning all means owner-level
    private static final Set<String> ALL_PERMISSIONS = Set.of(
        "console.view", "console.command",
        "players.view", "players.details",
        "players.punishments.view", "players.punishments.create",
        "servers.view", "servers.manage",
        "users.view", "users.manage",
        "roles.view", "roles.manage",
        "settings.view", "settings.manage",
        "audit.view"
    );

    public ClientSession(long userId, String username, Set<String> permissions,
                         String token, WebSocket conn, String ipAddress) {
        this.userId = userId;
        this.username = username;
        this.permissions = permissions;
        this.token = token;
        this.connection = conn;
        this.ipAddress = ipAddress;
        this.subscribedServers = ConcurrentHashMap.newKeySet();
    }

    public boolean hasPermission(String node) {
        return permissions != null && permissions.contains(node);
    }

    public boolean isOwner() {
        return permissions != null && permissions.containsAll(ALL_PERMISSIONS);
    }

    public void subscribe(String serverId) {
        subscribedServers.add(serverId);
    }

    public void unsubscribe(String serverId) {
        subscribedServers.remove(serverId);
    }

    public boolean isSubscribed(String serverId) {
        return subscribedServers.contains(serverId);
    }

    public long getUserId() { return userId; }
    public String getUsername() { return username; }
    public Set<String> getPermissions() { return permissions; }
    public String getToken() { return token; }
    public Set<String> getSubscribedServers() { return subscribedServers; }
    public WebSocket getConnection() { return connection; }
    public String getIpAddress() { return ipAddress; }
}
