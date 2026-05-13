package com.staffcontrol.proxy.auth;

import java.util.List;

public class LoginResult {

    private final boolean success;
    private final String token;
    private final long userId;
    private final String username;
    private final String roleName;
    private final List<String> permissions;
    private final String errorMessage;

    private LoginResult(boolean success, String token, long userId, String username,
                        String roleName, List<String> permissions, String errorMessage) {
        this.success = success;
        this.token = token;
        this.userId = userId;
        this.username = username;
        this.roleName = roleName;
        this.permissions = permissions;
        this.errorMessage = errorMessage;
    }

    public static LoginResult success(String token, long userId, String username,
                                       String roleName, List<String> permissions) {
        return new LoginResult(true, token, userId, username, roleName, permissions, null);
    }

    public static LoginResult failure(String errorMessage) {
        return new LoginResult(false, null, -1L, null, null, null, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getToken() {
        return token;
    }

    public long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getRoleName() {
        return roleName;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
