package com.staffcontrol.proxy.auth;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.staffcontrol.proxy.config.ProxyConfig;
import com.staffcontrol.proxy.database.DatabaseManager;

import java.sql.SQLException;
import java.util.*;
import java.util.logging.Logger;

public class AuthManager {

    private static final Logger log = Logger.getLogger(AuthManager.class.getName());

    private final DatabaseManager database;
    private final ProxyConfig config;

    public AuthManager(DatabaseManager database, ProxyConfig config) {
        this.database = database;
        this.config = config;
    }

    public LoginResult login(String username, String password, String ipAddress) {
        try {
            Map<String, Object> user = database.getUserByUsername(username);
            if (user == null) {
                return LoginResult.failure("INVALID_CREDENTIALS");
            }

            int isActive = getInt(user, "is_active");
            if (isActive == 0) {
                return LoginResult.failure("ACCOUNT_DISABLED");
            }

            String storedHash = getString(user, "password_hash");
            if (!verifyPassword(password, storedHash)) {
                return LoginResult.failure("INVALID_CREDENTIALS");
            }

            long userId = getLong(user, "id");
            String uname = getString(user, "username");

            // Generate token
            String token = UUID.randomUUID().toString().replace("-", "")
                    + UUID.randomUUID().toString().replace("-", "");

            long now = System.currentTimeMillis();
            long expiresAt = now + ((long) config.getSessionExpiryHours() * 3600L * 1000L);

            database.createSession(userId, token, ipAddress, now, expiresAt);
            database.deleteExpiredSessions();

            // Get role
            long roleId = database.getUserRoleId(userId);
            String roleName = "";
            List<String> permissions = new ArrayList<>();

            if (roleId >= 0) {
                Map<String, Object> role = database.getRoleById(roleId);
                if (role != null) {
                    roleName = getString(role, "name");
                }
                permissions = database.getPermissionsForRole(roleId);
            }

            return LoginResult.success(token, userId, uname, roleName, permissions);

        } catch (SQLException e) {
            return LoginResult.failure("DATABASE_ERROR");
        }
    }

    public Map<String, Object> validateToken(String token) {
        try {
            Map<String, Object> sessionData = database.validateSession(token);
            if (sessionData == null) return null;

            long userId = getLong(sessionData, "id");
            long roleId = database.getUserRoleId(userId);
            List<String> permissions = new ArrayList<>();
            String roleName = "";

            if (roleId >= 0) {
                Map<String, Object> role = database.getRoleById(roleId);
                if (role != null) {
                    roleName = getString(role, "name");
                }
                permissions = database.getPermissionsForRole(roleId);
            }

            sessionData.put("role_id", roleId);
            sessionData.put("role_name", roleName);
            sessionData.put("permissions", permissions);

            return sessionData;
        } catch (SQLException e) {
            return null;
        }
    }

    public void logout(String token) {
        try {
            database.deleteSession(token);
        } catch (SQLException e) {
            // ignore
        }
    }

    public boolean hasPermission(long userId, String node) {
        try {
            return database.userHasPermission(userId, node);
        } catch (SQLException e) {
            return false;
        }
    }

    public long createUser(String username, String plainPassword, long roleId) throws SQLException {
        String hash = hashPassword(plainPassword);
        long now = System.currentTimeMillis();
        long userId = database.createUser(username, hash, now);
        if (userId >= 0 && roleId >= 0) {
            database.setUserRole(userId, roleId);
        }
        return userId;
    }

    public void changePassword(long userId, String newPlain) throws SQLException {
        String hash = hashPassword(newPlain);
        long now = System.currentTimeMillis();
        database.updateUserPassword(userId, hash, now);
    }

    public boolean ownerExists() {
        try {
            Map<String, Object> ownerRole = database.getRoleByName("owner");
            if (ownerRole == null) return false;
            long ownerRoleId = getLong(ownerRole, "id");

            List<Map<String, Object>> users = database.getAllUsers();
            for (Map<String, Object> user : users) {
                Object roleIdObj = user.get("role_id");
                if (roleIdObj instanceof Number) {
                    long roleId = ((Number) roleIdObj).longValue();
                    if (roleId == ownerRoleId) return true;
                }
            }
            return false;
        } catch (SQLException e) {
            return false;
        }
    }

    public LoginResult createOwner(String username, String plainPassword) {
        try {
            Map<String, Object> ownerRole = database.getRoleByName("owner");
            if (ownerRole == null) {
                return LoginResult.failure("OWNER_ROLE_NOT_FOUND");
            }
            long ownerRoleId = getLong(ownerRole, "id");

            long userId = createUser(username, plainPassword, ownerRoleId);
            if (userId < 0) {
                return LoginResult.failure("USER_CREATION_FAILED");
            }

            return login(username, plainPassword, "localhost");

        } catch (SQLException e) {
            log.severe("[AuthManager] createOwner SQL error: " + e.getMessage() + " | SQLState: " + e.getSQLState());
            return LoginResult.failure("DATABASE_ERROR: " + e.getMessage());
        }
    }

    public String hashPassword(String plain) {
        return BCrypt.withDefaults().hashToString(12, plain.toCharArray());
    }

    public boolean verifyPassword(String plain, String hash) {
        BCrypt.Result result = BCrypt.verifyer().verify(plain.toCharArray(), hash);
        return result.verified;
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : null;
    }

    private long getLong(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).longValue();
        return 0L;
    }

    private int getInt(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        return 0;
    }
}
