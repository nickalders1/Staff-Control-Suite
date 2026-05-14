package com.staffcontrol.proxy.model;

import com.google.gson.JsonObject;

import java.util.Map;

public class Punishment {

    private long id;
    private String targetUuid;
    private String targetName;
    private String targetIpHash;
    private String actionType;
    private String reason;
    private long durationSeconds;
    private long createdAt;
    private long expiresAt;
    private long createdByUserId;
    private String createdByUsername;
    private String targetServer;
    private String evidence;
    private boolean active;
    private long revokedAt;
    private long revokedByUserId;
    private String revokedByUsername;
    private String revokeReason;

    public Punishment() {}

    public static Punishment fromRow(Map<String, Object> row) {
        Punishment p = new Punishment();
        p.id               = getLong(row, "id");
        p.targetUuid       = getString(row, "target_uuid");
        p.targetName       = getString(row, "target_name");
        p.targetIpHash     = getString(row, "target_ip_hash");
        p.actionType       = getString(row, "action_type");
        p.reason           = getString(row, "reason");
        p.durationSeconds  = getLong(row, "duration_seconds");
        p.createdAt        = getLong(row, "created_at");
        p.expiresAt        = getLong(row, "expires_at");
        p.createdByUserId  = getLong(row, "created_by_user_id");
        p.createdByUsername = getString(row, "created_by_username");
        p.targetServer     = getString(row, "target_server");
        p.evidence         = getString(row, "evidence");
        Object activeObj   = row.get("active");
        p.active           = activeObj instanceof Number ? ((Number) activeObj).intValue() == 1 : false;
        p.revokedAt          = getLong(row, "revoked_at");
        p.revokedByUserId    = getLong(row, "revoked_by_user_id");
        p.revokedByUsername  = getString(row, "revoked_by_username");
        p.revokeReason       = getString(row, "revoke_reason");
        return p;
    }

    public JsonObject toJson() {
        return toJson(true);
    }

    public JsonObject toJson(boolean includeIpHash) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id",                 id);
        obj.addProperty("targetUuid",         targetUuid != null ? targetUuid : "");
        obj.addProperty("targetName",         targetName != null ? targetName : "");
        if (includeIpHash) {
            obj.addProperty("targetIpHash",   targetIpHash != null ? targetIpHash : "");
        }
        obj.addProperty("actionType",         actionType != null ? actionType : "");
        obj.addProperty("reason",             reason != null ? reason : "");
        obj.addProperty("durationSeconds",    durationSeconds);
        obj.addProperty("createdAt",          createdAt);
        obj.addProperty("expiresAt",          expiresAt);
        obj.addProperty("createdByUserId",    createdByUserId);
        obj.addProperty("createdByUsername",  createdByUsername != null ? createdByUsername : "");
        obj.addProperty("targetServer",       targetServer != null ? targetServer : "global");
        obj.addProperty("evidence",           evidence != null ? evidence : "");
        obj.addProperty("active",             active);
        obj.addProperty("revokedAt",           revokedAt);
        obj.addProperty("revokedByUserId",     revokedByUserId);
        obj.addProperty("revokedByUsername",   revokedByUsername != null ? revokedByUsername : "");
        obj.addProperty("revokeReason",        revokeReason != null ? revokeReason : "");
        return obj;
    }

    private static String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : null;
    }

    private static long getLong(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val instanceof Number ? ((Number) val).longValue() : 0L;
    }

    // ---- Getters & Setters ----

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getTargetUuid() { return targetUuid; }
    public void setTargetUuid(String targetUuid) { this.targetUuid = targetUuid; }

    public String getTargetName() { return targetName; }
    public void setTargetName(String targetName) { this.targetName = targetName; }

    public String getTargetIpHash() { return targetIpHash; }
    public void setTargetIpHash(String targetIpHash) { this.targetIpHash = targetIpHash; }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public long getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(long durationSeconds) { this.durationSeconds = durationSeconds; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }

    public long getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(long createdByUserId) { this.createdByUserId = createdByUserId; }

    public String getCreatedByUsername() { return createdByUsername; }
    public void setCreatedByUsername(String createdByUsername) { this.createdByUsername = createdByUsername; }

    public String getTargetServer() { return targetServer; }
    public void setTargetServer(String targetServer) { this.targetServer = targetServer; }

    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public long getRevokedAt() { return revokedAt; }
    public void setRevokedAt(long revokedAt) { this.revokedAt = revokedAt; }

    public long getRevokedByUserId() { return revokedByUserId; }
    public void setRevokedByUserId(long revokedByUserId) { this.revokedByUserId = revokedByUserId; }

    public String getRevokedByUsername() { return revokedByUsername; }
    public void setRevokedByUsername(String revokedByUsername) { this.revokedByUsername = revokedByUsername; }

    public String getRevokeReason() { return revokeReason; }
    public void setRevokeReason(String revokeReason) { this.revokeReason = revokeReason; }
}
