package com.staffcontrol.proxy.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

public class User {

    private long id;
    private String username;
    private String passwordHash;
    private long roleId;
    private String roleName;
    private List<String> permissions;
    private boolean isActive;
    private long createdAt;

    public User() {}

    public User(long id, String username, String passwordHash, long roleId, String roleName,
                List<String> permissions, boolean isActive, long createdAt) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.roleId = roleId;
        this.roleName = roleName;
        this.permissions = permissions;
        this.isActive = isActive;
        this.createdAt = createdAt;
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);
        obj.addProperty("username", username);
        obj.addProperty("roleId", roleId);
        obj.addProperty("roleName", roleName != null ? roleName : "");
        obj.addProperty("isActive", isActive);
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

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public long getRoleId() { return roleId; }
    public void setRoleId(long roleId) { this.roleId = roleId; }

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }

    public List<String> getPermissions() { return permissions; }
    public void setPermissions(List<String> permissions) { this.permissions = permissions; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
