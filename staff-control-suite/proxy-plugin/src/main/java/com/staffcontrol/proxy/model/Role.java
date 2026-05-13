package com.staffcontrol.proxy.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

public class Role {

    private long id;
    private String name;
    private String displayName;
    private List<String> permissions;
    private boolean isSystemRole;
    private long createdAt;

    public Role() {}

    public Role(long id, String name, String displayName, List<String> permissions,
                boolean isSystemRole, long createdAt) {
        this.id = id;
        this.name = name;
        this.displayName = displayName;
        this.permissions = permissions;
        this.isSystemRole = isSystemRole;
        this.createdAt = createdAt;
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);
        obj.addProperty("name", name);
        obj.addProperty("displayName", displayName);
        obj.addProperty("isSystemRole", isSystemRole);
        obj.addProperty("createdAt", createdAt);
        JsonArray permsArray = new JsonArray();
        if (permissions != null) {
            for (String perm : permissions) {
                permsArray.add(perm);
            }
        }
        obj.add("permissions", permsArray);
        return obj;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public List<String> getPermissions() { return permissions; }
    public void setPermissions(List<String> permissions) { this.permissions = permissions; }

    public boolean isSystemRole() { return isSystemRole; }
    public void setSystemRole(boolean systemRole) { isSystemRole = systemRole; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
