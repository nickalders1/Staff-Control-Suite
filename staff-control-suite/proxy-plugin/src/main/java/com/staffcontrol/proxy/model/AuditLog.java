package com.staffcontrol.proxy.model;

import com.google.gson.JsonObject;

public class AuditLog {

    private long id;
    private Long userId;
    private String username;
    private String action;
    private String target;
    private String details;
    private String ipAddress;
    private long timestamp;

    public AuditLog() {}

    public AuditLog(long id, Long userId, String username, String action, String target,
                    String details, String ipAddress, long timestamp) {
        this.id = id;
        this.userId = userId;
        this.username = username;
        this.action = action;
        this.target = target;
        this.details = details;
        this.ipAddress = ipAddress;
        this.timestamp = timestamp;
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);
        if (userId != null) obj.addProperty("userId", userId);
        obj.addProperty("username", username);
        obj.addProperty("action", action);
        if (target != null) obj.addProperty("target", target);
        if (details != null) obj.addProperty("details", details);
        if (ipAddress != null) obj.addProperty("ipAddress", ipAddress);
        obj.addProperty("timestamp", timestamp);
        return obj;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
