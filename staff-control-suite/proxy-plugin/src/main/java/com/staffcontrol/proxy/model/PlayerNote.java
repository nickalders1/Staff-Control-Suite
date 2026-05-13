package com.staffcontrol.proxy.model;

import com.google.gson.JsonObject;

import java.util.Map;

public class PlayerNote {

    private long id;
    private String targetUuid;
    private String targetName;
    private String note;
    private long createdAt;
    private long createdByUserId;
    private String createdByUsername;
    private String visibility;

    public PlayerNote() {}

    public static PlayerNote fromRow(Map<String, Object> row) {
        PlayerNote n = new PlayerNote();
        n.id                 = getLong(row, "id");
        n.targetUuid         = getString(row, "target_uuid");
        n.targetName         = getString(row, "target_name");
        n.note               = getString(row, "note");
        n.createdAt          = getLong(row, "created_at");
        n.createdByUserId    = getLong(row, "created_by_user_id");
        n.createdByUsername  = getString(row, "created_by_username");
        n.visibility         = getString(row, "visibility");
        return n;
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id",                id);
        obj.addProperty("targetUuid",        targetUuid != null ? targetUuid : "");
        obj.addProperty("targetName",        targetName != null ? targetName : "");
        obj.addProperty("note",              note != null ? note : "");
        obj.addProperty("createdAt",         createdAt);
        obj.addProperty("createdByUserId",   createdByUserId);
        obj.addProperty("createdByUsername", createdByUsername != null ? createdByUsername : "");
        obj.addProperty("visibility",        visibility != null ? visibility : "staff");
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

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(long createdByUserId) { this.createdByUserId = createdByUserId; }

    public String getCreatedByUsername() { return createdByUsername; }
    public void setCreatedByUsername(String createdByUsername) { this.createdByUsername = createdByUsername; }

    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
}
