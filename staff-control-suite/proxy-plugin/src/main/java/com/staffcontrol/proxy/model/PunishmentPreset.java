package com.staffcontrol.proxy.model;

import com.google.gson.JsonObject;

import java.util.Map;

public class PunishmentPreset {

    private long id;
    private String category;
    private String name;
    private String description;
    private String actionType;
    private long durationSeconds;
    private int severity;
    private boolean stackable;
    private boolean bypassCap;
    private boolean requiresIpBan;
    private boolean enabled;

    public PunishmentPreset() {}

    public static PunishmentPreset fromRow(Map<String, Object> row) {
        PunishmentPreset p = new PunishmentPreset();
        p.id              = getLong(row, "id");
        p.category        = getString(row, "category");
        p.name            = getString(row, "name");
        p.description     = getString(row, "description");
        p.actionType      = getString(row, "action_type");
        p.durationSeconds = getLong(row, "duration_seconds");
        p.severity        = getInt(row, "severity");
        Object stackObj   = row.get("stackable");
        p.stackable       = stackObj instanceof Number ? ((Number) stackObj).intValue() == 1 : true;
        Object capObj     = row.get("bypass_cap");
        p.bypassCap       = capObj instanceof Number ? ((Number) capObj).intValue() == 1 : false;
        Object ipObj      = row.get("requires_ip_ban");
        p.requiresIpBan   = ipObj instanceof Number ? ((Number) ipObj).intValue() == 1 : false;
        Object enObj      = row.get("enabled");
        p.enabled         = enObj instanceof Number ? ((Number) enObj).intValue() == 1 : true;
        return p;
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id",              id);
        obj.addProperty("category",        category != null ? category : "");
        obj.addProperty("name",            name != null ? name : "");
        obj.addProperty("description",     description != null ? description : "");
        obj.addProperty("actionType",      actionType != null ? actionType : "");
        obj.addProperty("durationSeconds", durationSeconds);
        obj.addProperty("severity",        severity);
        obj.addProperty("stackable",       stackable);
        obj.addProperty("bypassCap",       bypassCap);
        obj.addProperty("requiresIpBan",   requiresIpBan);
        obj.addProperty("enabled",         enabled);
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

    private static int getInt(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val instanceof Number ? ((Number) val).intValue() : 0;
    }

    // ---- Getters & Setters ----

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public long getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(long durationSeconds) { this.durationSeconds = durationSeconds; }

    public int getSeverity() { return severity; }
    public void setSeverity(int severity) { this.severity = severity; }

    public boolean isStackable() { return stackable; }
    public void setStackable(boolean stackable) { this.stackable = stackable; }

    public boolean isBypassCap() { return bypassCap; }
    public void setBypassCap(boolean bypassCap) { this.bypassCap = bypassCap; }

    public boolean isRequiresIpBan() { return requiresIpBan; }
    public void setRequiresIpBan(boolean requiresIpBan) { this.requiresIpBan = requiresIpBan; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
