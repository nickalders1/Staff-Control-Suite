package com.staffcontrol.proxy.api;

import org.java_websocket.WebSocket;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ClientSession {

    private final long userId;
    private final String username;
    private final String roleName;
    private final Set<String> permissions;
    private final String token;
    private final Set<String> subscribedServers;
    private final WebSocket connection;
    private final String ipAddress;

    public ClientSession(long userId, String username, String roleName, Set<String> permissions,
                         String token, WebSocket conn, String ipAddress) {
        this.userId = userId;
        this.username = username;
        this.roleName = roleName;
        this.permissions = permissions;
        this.token = token;
        this.connection = conn;
        this.ipAddress = ipAddress;
        this.subscribedServers = ConcurrentHashMap.newKeySet();
    }

    public boolean isOwner() {
        return "owner".equals(roleName);
    }

    public boolean hasPermission(String node) {
        return isOwner() || (permissions != null && permissions.contains(node));
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
    public String getRoleName() { return roleName; }
    public Set<String> getPermissions() { return permissions; }
    public String getToken() { return token; }
    public Set<String> getSubscribedServers() { return subscribedServers; }
    public WebSocket getConnection() { return connection; }
    public String getIpAddress() { return ipAddress; }
}
