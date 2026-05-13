package com.staffcontrol.proxy.database;

import com.staffcontrol.proxy.config.ProxyConfig;

import java.nio.file.Path;
import java.sql.*;
import java.util.*;

public class DatabaseManager {

    private final Path dataDirectory;
    private final ProxyConfig config;
    private Connection connection;

    public DatabaseManager(Path dataDirectory, ProxyConfig config) {
        this.dataDirectory = dataDirectory;
        this.config = config;
    }

    public synchronized void initialize() throws Exception {
        String dbPath = dataDirectory.resolve(config.getDatabasePath()).toString();
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        connection.createStatement().execute("PRAGMA journal_mode=WAL");
        connection.createStatement().execute("PRAGMA foreign_keys=ON");
        createTables();
        seedPermissions();
        seedDefaultRoles();
    }

    private void createTables() throws SQLException {
        Statement stmt = connection.createStatement();

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL,
                is_active INTEGER NOT NULL DEFAULT 1,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )""");

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS roles (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                display_name TEXT NOT NULL,
                is_system_role INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )""");

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS permissions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                node TEXT NOT NULL UNIQUE,
                display_name TEXT NOT NULL
            )""");

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS user_roles (
                user_id INTEGER NOT NULL,
                role_id INTEGER NOT NULL,
                PRIMARY KEY (user_id, role_id),
                FOREIGN KEY (user_id) REFERENCES users(id),
                FOREIGN KEY (role_id) REFERENCES roles(id)
            )""");

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS role_permissions (
                role_id INTEGER NOT NULL,
                permission_id INTEGER NOT NULL,
                PRIMARY KEY (role_id, permission_id),
                FOREIGN KEY (role_id) REFERENCES roles(id),
                FOREIGN KEY (permission_id) REFERENCES permissions(id)
            )""");

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS servers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                server_id TEXT NOT NULL UNIQUE,
                server_name TEXT NOT NULL,
                server_type TEXT NOT NULL DEFAULT 'generic',
                host TEXT,
                port INTEGER,
                agent_token TEXT NOT NULL,
                is_active INTEGER NOT NULL DEFAULT 1,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )""");

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                token TEXT NOT NULL UNIQUE,
                ip_address TEXT,
                created_at INTEGER NOT NULL,
                expires_at INTEGER NOT NULL,
                FOREIGN KEY (user_id) REFERENCES users(id)
            )""");

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS audit_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER,
                username TEXT,
                action TEXT NOT NULL,
                target TEXT,
                details TEXT,
                ip_address TEXT,
                timestamp INTEGER NOT NULL
            )""");

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS command_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                username TEXT NOT NULL,
                server_id TEXT NOT NULL,
                command TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )""");

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS player_cache (
                uuid TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                server_id TEXT,
                is_online INTEGER NOT NULL DEFAULT 0,
                last_seen INTEGER,
                first_joined INTEGER,
                playtime_seconds INTEGER NOT NULL DEFAULT 0
            )""");

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS punishment_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT NOT NULL,
                type TEXT NOT NULL,
                reason TEXT,
                issued_by TEXT,
                issued_at INTEGER NOT NULL,
                expires_at INTEGER,
                is_active INTEGER NOT NULL DEFAULT 1
            )""");

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS playtime_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT NOT NULL,
                server_id TEXT NOT NULL,
                session_start INTEGER NOT NULL,
                session_end INTEGER,
                duration_seconds INTEGER
            )""");

        stmt.close();
    }

    private void seedPermissions() throws SQLException {
        String[] permissions = {
            "console.view", "View server console",
            "console.command", "Execute console commands",
            "players.view", "View player list",
            "players.details", "View player details",
            "players.punishments.view", "View player punishments",
            "players.punishments.create", "Create player punishments",
            "servers.view", "View servers",
            "servers.manage", "Manage servers",
            "users.view", "View users",
            "users.manage", "Manage users",
            "roles.view", "View roles",
            "roles.manage", "Manage roles",
            "settings.view", "View settings",
            "settings.manage", "Manage settings",
            "audit.view", "View audit logs"
        };

        PreparedStatement ps = connection.prepareStatement(
            "INSERT OR IGNORE INTO permissions (node, display_name) VALUES (?, ?)"
        );

        for (int i = 0; i < permissions.length; i += 2) {
            ps.setString(1, permissions[i]);
            ps.setString(2, permissions[i + 1]);
            ps.addBatch();
        }
        ps.executeBatch();
        ps.close();
    }

    private void seedDefaultRoles() throws SQLException {
        long now = System.currentTimeMillis();

        // Create owner role
        PreparedStatement ps = connection.prepareStatement(
            "INSERT OR IGNORE INTO roles (name, display_name, is_system_role, created_at) VALUES (?, ?, ?, ?)"
        );
        ps.setString(1, "owner");
        ps.setString(2, "Owner");
        ps.setInt(3, 1);
        ps.setLong(4, now);
        ps.execute();

        ps.setString(1, "admin");
        ps.setString(2, "Admin");
        ps.setInt(3, 1);
        ps.setLong(4, now);
        ps.execute();

        ps.setString(1, "staff");
        ps.setString(2, "Staff");
        ps.setInt(3, 0);
        ps.setLong(4, now);
        ps.execute();
        ps.close();

        // Assign all permissions to owner
        Map<String, Object> ownerRole = getRoleByName("owner");
        if (ownerRole != null) {
            long ownerId = getLong(ownerRole, "id");
            List<Map<String, Object>> allPerms = getAllPermissions();
            List<String> allNodes = new ArrayList<>();
            for (Map<String, Object> perm : allPerms) {
                allNodes.add(getString(perm, "node"));
            }
            // Only set if not already set
            List<String> currentOwnerPerms = getPermissionsForRole(ownerId);
            if (currentOwnerPerms.isEmpty()) {
                setPermissionsForRole(ownerId, allNodes);
            }
        }

        // Assign all permissions except roles.manage to admin
        Map<String, Object> adminRole = getRoleByName("admin");
        if (adminRole != null) {
            long adminId = getLong(adminRole, "id");
            List<String> currentAdminPerms = getPermissionsForRole(adminId);
            if (currentAdminPerms.isEmpty()) {
                List<Map<String, Object>> allPerms = getAllPermissions();
                List<String> adminNodes = new ArrayList<>();
                for (Map<String, Object> perm : allPerms) {
                    String node = getString(perm, "node");
                    if (!"roles.manage".equals(node) && !"users.manage".equals(node)) {
                        adminNodes.add(node);
                    }
                }
                setPermissionsForRole(adminId, adminNodes);
            }
        }

        // Assign limited permissions to staff
        Map<String, Object> staffRole = getRoleByName("staff");
        if (staffRole != null) {
            long staffId = getLong(staffRole, "id");
            List<String> currentStaffPerms = getPermissionsForRole(staffId);
            if (currentStaffPerms.isEmpty()) {
                setPermissionsForRole(staffId, Arrays.asList(
                    "console.view", "players.view", "players.details", "servers.view"
                ));
            }
        }
    }

    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            // ignore
        }
    }

    // ---- User Methods ----

    public synchronized long createUser(String username, String passwordHash, long now) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
            "INSERT INTO users (username, password_hash, is_active, created_at, updated_at) VALUES (?, ?, 1, ?, ?)",
            Statement.RETURN_GENERATED_KEYS
        );
        ps.setString(1, username);
        ps.setString(2, passwordHash);
        ps.setLong(3, now);
        ps.setLong(4, now);
        ps.executeUpdate();
        ResultSet rs = ps.getGeneratedKeys();
        long id = rs.next() ? rs.getLong(1) : -1L;
        rs.close();
        ps.close();
        return id;
    }

    public synchronized Map<String, Object> getUserByUsername(String username) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
            "SELECT * FROM users WHERE username = ?"
        );
        ps.setString(1, username);
        ResultSet rs = ps.executeQuery();
        Map<String, Object> result = rs.next() ? resultSetToMap(rs) : null;
        rs.close();
        ps.close();
        return result;
    }

    public synchronized Map<String, Object> getUserById(long id) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
            "SELECT * FROM users WHERE id = ?"
        );
        ps.setLong(1, id);
        ResultSet rs = ps.executeQuery();
        Map<String, Object> result = rs.next() ? resultSetToMap(rs) : null;
        rs.close();
        ps.close();
        return result;
    }

    public synchronized List<Map<String, Object>> getAllUsers() throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
            "SELECT u.*, r.id as role_id, r.name as role_name, r.display_name as role_display_name " +
            "FROM users u LEFT JOIN user_roles ur ON u.id = ur.user_id " +
            "LEFT JOIN roles r ON ur.role_id = r.id"
        );
        ResultSet rs = ps.executeQuery();
        List<Map<String, Object>> results = new ArrayList<>();
        while (rs.next()) {
            results.add(resultSetToMap(rs));
        }
        rs.close();
        ps.close();
        return results;
    }

    public synchronized void updateUser(long id, String username, boolean isActive, long now) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
            "UPDATE users SET username = ?, is_active = ?, updated_at = ? WHERE id = ?"
        );
        ps.setString(1, username);
        ps.setInt(2, isActive ? 1 : 0);
        ps.setLong(3, now);
        ps.setLong(4, id);
        ps.executeUpdate();
        ps.close();
    }

    public synchronized void updateUserPassword(long id, String passwordHash, long now) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
            "UPDATE users SET password_hash = ?, updated_at = ? WHERE id = ?"
        );
        ps.setString(1, passwordHash);
        ps.setLong(2, now);
        ps.setLong(3, id);
        ps.executeUpdate();
        ps.close();
    }

    public synchronized void deleteUser(long id) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("DELETE FROM user_roles WHERE user_id = ?");
        ps.setLong(1, id);
        ps.executeUpdate();
        ps.close();

        ps = connection.prepareStatement("DELETE FROM sessions WHERE user_id = ?");
        ps.setLong(1, id);
        ps.executeUpdate();
        ps.close();

        ps = connection.prepareStatement("DELETE FROM users WHERE id = ?");
        ps.setLong(1, id);
        ps.executeUpdate();
        ps.close();
    }

    public synchronized void setUserRole(long userId, long roleId) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("DELETE FROM user_roles WHERE user_id = ?");
        ps.setLong(1, userId);
        ps.executeUpdate();
        ps.close();

        ps = connection.prepareStatement("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)");
        ps.setLong(1, userId);
        ps.setLong(2, roleId);
        ps.executeUpdate();
        ps.close();
    }

    public synchronized long getUserRoleId(long userId) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
            "SELECT role_id FROM user_roles WHERE user_id = ?"
        );
        ps.setLong(1, userId);
        ResultSet rs = ps.executeQuery();
        long result = rs.next() ? rs.getLong("role_id") : -1L;
        rs.close();
        ps.close();
        return result;
    }

    // ---- Role Methods ----

    public synchronized long createRole(String name, String displayName, boolean isSystemRole, long now) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
            "INSERT INTO roles (name, display_name, is_system_role, created_at) VALUES (?, ?, ?, ?)",
            Statement.RETURN_GENERATED_KEYS
        );
        ps.setString(1, name);
        ps.setString(2, displayName);
        ps.setInt(3, isSystemRole ? 1 : 0);
        ps.setLong(4, now);
        ps.executeUpdate();
        ResultSet rs = ps.getGeneratedKeys();
        long id = rs.next() ? rs.getLong(1) : -1L;
        rs.close();
        ps.close();
        return id;
    }

    public synchronized Map<String, Object> getRoleById(long id) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("SELECT * FROM roles WHERE id = ?");
        ps.setLong(1, id);
        ResultSet rs = ps.executeQuery();
        Map<String, Object> result = rs.next() ? resultSetToMap(rs) : null;
        rs.close();
        ps.close();
        return result;
    }

    public synchronized Map<String, Object> getRoleByName(String name) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("SELECT * FROM roles WHERE name = ?");
        ps.setString(1, name);
        ResultSet rs = ps.executeQuery();
        Map<String, Object> result = rs.next() ? resultSetToMap(rs) : null;
        rs.close();
        ps.close();
        return result;
    }

    public synchronized List<Map<String, Object>> getAllRoles() throws SQLException {
        PreparedStatement ps = connection.prepareStatement("SELECT * FROM roles ORDER BY id ASC");
        ResultSet rs = ps.executeQuery();
        List<Map<String, Object>> results = new ArrayList<>();
        while (rs.next()) {
            results.add(resultSetToMap(rs));
        }
        rs.close();
        ps.close();
        return results;
    }

    public synchronized void updateRole(long id, String displayName) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
            "UPDATE roles SET display_name = ? WHERE id = ?"
        );
        ps.setString(1, displayName);
        ps.setLong(2, id);
        ps.executeUpdate();
        ps.close();
    }

    public synchronized void deleteRole(long id) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("DELETE FROM role_permissions WHERE role_id = ?");
        ps.setLong(1, id);
        ps.executeUpdate();
        ps.close();

        ps = connection.prepareStatement("DELETE FROM user_roles WHERE role_id = ?");
        ps.setLong(1, id);
        ps.executeUpdate();
        ps.close();

        ps = connection.prepareStatement("DELETE FROM roles WHERE id = ?");
        ps.setLong(1, id);
        ps.executeUpdate();
        ps.close();
    }

    // ---- Permission Methods ----

    public synchronized List<Map<String, Object>> getAllPermissions() throws SQLException {
        PreparedStatement ps = connection.prepareStatement("SELECT * FROM permissions ORDER BY id ASC");
        ResultSet rs = ps.executeQuery();
        List<Map<String, Object>> results = new ArrayList<>();
        while (rs.next()) {
            results.add(resultSetToMap(rs));
        }
        rs.close();
        ps.close();
        return results;
    }

    public synchronized Map<String, Object> getPermissionByNode(String node) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("SELECT * FROM permissions WHERE node = ?");
        ps.setString(1, node);
        ResultSet rs = ps.executeQuery();
        Map<String, Object> result = rs.next() ? resultSetToMap(rs) : null;
        rs.close();
        ps.close();
        return result;
    }

    public synchronized List<String> getPermissionsForRole(long roleId) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
            "SELECT p.node FROM permissions p " +
            "JOIN role_permissions rp ON p.id = rp.permission_id " +
            "WHERE rp.role_id = ?"
        );
        ps.setLong(1, roleId);
        ResultSet rs = ps.executeQuery();
        List<String> results = new ArrayList<>();
        while (rs.next()) {
            results.add(rs.getString("node"));
        }
        rs.close();
        ps.close();
        return results;
    }

    public synchronized void setPermissionsForRole(long roleId, List<String> nodes) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("DELETE FROM role_permissions WHERE role_id = ?");
        ps.setLong(1, roleId);
        ps.executeUpdate();
        ps.close();

        if (nodes == null || nodes.isEmpty()) return;

        ps = connection.prepareStatement(
            "INSERT OR IGNORE INTO role_permissions (role_id, permission_id) " +
            "SELECT ?, id FROM permissions WHERE node = ?"
        );
        for (String node : nodes) {
            ps.setLong(1, roleId);
            ps.setString(2, node);
            ps.addBatch();
        }
        ps.executeBatch();
        ps.close();
    }

    public synchronized boolean userHasPermission(long userId, String node) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
            "SELECT 1 FROM users u " +
            "JOIN user_roles ur ON u.id = ur.user_id " +
            "JOIN role_permissions rp ON ur.role_id = rp.role_id " +
            "JOIN permissions p ON rp.permission_id = p.id " +
            "WHERE u.id = ? AND p.node = ?"
        );
        ps.setLong(1, userId);
        ps.setString(2, node);
        ResultSet rs = ps.executeQuery();
        boolean result = rs.next();
        rs.close();
        ps.close();
        return result;
    }

    // ---- Server Methods ----

    public synchronized long addServer(String serverId, String serverName, String serverType,
                                        String agentToken, long now) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
            "INSERT INTO servers (server_id, server_name, server_type, agent_token, is_active, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, 1, ?, ?)",
            Statement.RETURN_GENERATED_KEYS
        );
        ps.setString(1, serverId);
        ps.setString(2, serverName);
        ps.setString(3, serverType);
        ps.setString(4, agentToken);
        ps.setLong(5, now);
        ps.setLong(6, now);
        ps.executeUpdate();
        ResultSet rs = ps.getGeneratedKeys();
        long id = rs.next() ? rs.getLong(1) : -1L;
        rs.close();
        ps.close();
        return id;
    }

    public synchronized Map<String, Object> getServer(String serverId) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("SELECT * FROM servers WHERE server_id = ?");
        ps.setString(1, serverId);
        ResultSet rs = ps.executeQuery();
        Map<String, Object> result = rs.next() ? resultSetToMap(rs) : null;
        rs.close();
        ps.close();
        return result;
    }

    public synchronized List<Map<String, Object>> getAllServers() throws SQLException {
        PreparedStatement ps = connection.prepareStatement("SELECT * FROM servers ORDER BY id ASC");
        ResultSet rs = ps.executeQuery();
        List<Map<String, Object>> results = new ArrayList<>();
        while (rs.next()) {
            results.add(resultSetToMap(rs));
        }
        rs.close();
        ps.close();
        return results;
    }

    public synchronized void updateServer(long id, String serverId, String serverName, String serverType, long now) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
            "UPDATE servers SET server_id = ?, server_name = ?, server_type = ?, updated_at = ? WHERE id = ?"
        );
        ps.setString(1, serverId);
        ps.setString(2, serverName);
        ps.setString(3, serverType);
        ps.setLong(4, now);
        ps.setLong(5, id);
        ps.executeUpdate();
        ps.close();
    }

    public synchronized void updateServerToken(String serverId, String newToken, long now) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
            "UPDATE servers SET agent_token = ?, updated_at = ? WHERE server_id = ?"
        );
        ps.setString(1, newToken);
        ps.setLong(2, now);
        ps.setString(3, serverId);
        ps.executeUpdate();
        ps.close();
    }

    public synchronized void deleteServer(String serverId) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("DELETE FROM servers WHERE server_id = ?");
        ps.setString(1, serverId);
        ps.executeUpdate();
        ps.close();
    }

    // ---- Session Methods ----

    public synchronized void createSession(long userId, String token, String ipAddress,
                                            long now, long expiresAt) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
            "INSERT INTO sessions (user_id, token, ip_address, created_at, expires_at) VALUES (?, ?, ?, ?, ?)"
        );
        ps.setLong(1, userId);
        ps.setString(2, token);
        ps.setString(3, ipAddress);
        ps.setLong(4, now);
        ps.setLong(5, expiresAt);
        ps.executeUpdate();
        ps.close();
    }

    public synchronized Map<String, Object> validateSession(String token) throws SQLException {
        long now = System.currentTimeMillis();
        PreparedStatement ps = connection.prepareStatement(
            "SELECT u.id, u.username, u.is_active, s.expires_at, s.ip_address " +
            "FROM sessions s JOIN users u ON s.user_id = u.id " +
            "WHERE s.token = ? AND s.expires_at > ? AND u.is_active = 1"
        );
        ps.setString(1, token);
        ps.setLong(2, now);
        ResultSet rs = ps.executeQuery();
        Map<String, Object> result = rs.next() ? resultSetToMap(rs) : null;
        rs.close();
        ps.close();
        return result;
    }

    public synchronized void deleteSession(String token) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("DELETE FROM sessions WHERE token = ?");
        ps.setString(1, token);
        ps.executeUpdate();
        ps.close();
    }

    public synchronized void deleteExpiredSessions() throws SQLException {
        long now = System.currentTimeMillis();
        PreparedStatement ps = connection.prepareStatement("DELETE FROM sessions WHERE expires_at <= ?");
        ps.setLong(1, now);
        ps.executeUpdate();
        ps.close();
    }

    public synchronized long getUserIdFromToken(String token) throws SQLException {
        Map<String, Object> session = validateSession(token);
        if (session == null) return -1L;
        Object id = session.get("id");
        if (id instanceof Number) return ((Number) id).longValue();
        return -1L;
    }

    // ---- Audit/Command Log Methods ----

    public synchronized void logAudit(Long userId, String username, String action, String target,
                                       String details, String ipAddress) throws SQLException {
        long now = System.currentTimeMillis();
        PreparedStatement ps = connection.prepareStatement(
            "INSERT INTO audit_logs (user_id, username, action, target, details, ip_address, timestamp) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)"
        );
        if (userId != null) ps.setLong(1, userId); else ps.setNull(1, Types.INTEGER);
        ps.setString(2, username);
        ps.setString(3, action);
        ps.setString(4, target);
        ps.setString(5, details);
        ps.setString(6, ipAddress);
        ps.setLong(7, now);
        ps.executeUpdate();
        ps.close();
    }

    public synchronized List<Map<String, Object>> getAuditLogs(int page, int limit) throws SQLException {
        int offset = (page - 1) * limit;
        PreparedStatement ps = connection.prepareStatement(
            "SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT ? OFFSET ?"
        );
        ps.setInt(1, limit);
        ps.setInt(2, offset);
        ResultSet rs = ps.executeQuery();
        List<Map<String, Object>> results = new ArrayList<>();
        while (rs.next()) {
            results.add(resultSetToMap(rs));
        }
        rs.close();
        ps.close();
        return results;
    }

    public synchronized int getAuditLogsTotal() throws SQLException {
        PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) as total FROM audit_logs");
        ResultSet rs = ps.executeQuery();
        int total = rs.next() ? rs.getInt("total") : 0;
        rs.close();
        ps.close();
        return total;
    }

    public synchronized void logCommand(long userId, String username, String serverId, String command) throws SQLException {
        long now = System.currentTimeMillis();
        PreparedStatement ps = connection.prepareStatement(
            "INSERT INTO command_logs (user_id, username, server_id, command, timestamp) VALUES (?, ?, ?, ?, ?)"
        );
        ps.setLong(1, userId);
        ps.setString(2, username);
        ps.setString(3, serverId);
        ps.setString(4, command);
        ps.setLong(5, now);
        ps.executeUpdate();
        ps.close();
    }

    // ---- Player Cache Methods ----

    public synchronized void upsertPlayer(String uuid, String name, String serverId,
                                           boolean isOnline, long now) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
            "INSERT INTO player_cache (uuid, name, server_id, is_online, last_seen) VALUES (?, ?, ?, ?, ?) " +
            "ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, server_id = excluded.server_id, " +
            "is_online = excluded.is_online, last_seen = excluded.last_seen"
        );
        ps.setString(1, uuid);
        ps.setString(2, name);
        ps.setString(3, serverId);
        ps.setInt(4, isOnline ? 1 : 0);
        ps.setLong(5, now);
        ps.executeUpdate();
        ps.close();
    }

    public synchronized void setPlayerOffline(String uuid, long now) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
            "UPDATE player_cache SET is_online = 0, last_seen = ?, server_id = NULL WHERE uuid = ?"
        );
        ps.setLong(1, now);
        ps.setString(2, uuid);
        ps.executeUpdate();
        ps.close();
    }

    public synchronized Map<String, Object> getPlayer(String uuid) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("SELECT * FROM player_cache WHERE uuid = ?");
        ps.setString(1, uuid);
        ResultSet rs = ps.executeQuery();
        Map<String, Object> result = rs.next() ? resultSetToMap(rs) : null;
        rs.close();
        ps.close();
        return result;
    }

    public synchronized List<Map<String, Object>> getAllOnlinePlayers() throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
            "SELECT * FROM player_cache WHERE is_online = 1"
        );
        ResultSet rs = ps.executeQuery();
        List<Map<String, Object>> results = new ArrayList<>();
        while (rs.next()) {
            results.add(resultSetToMap(rs));
        }
        rs.close();
        ps.close();
        return results;
    }

    public synchronized List<Map<String, Object>> getPlayersByServer(String serverId) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
            "SELECT * FROM player_cache WHERE server_id = ? AND is_online = 1"
        );
        ps.setString(1, serverId);
        ResultSet rs = ps.executeQuery();
        List<Map<String, Object>> results = new ArrayList<>();
        while (rs.next()) {
            results.add(resultSetToMap(rs));
        }
        rs.close();
        ps.close();
        return results;
    }

    public synchronized void updatePlayerPlaytime(String uuid, long additionalSeconds) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
            "UPDATE player_cache SET playtime_seconds = playtime_seconds + ? WHERE uuid = ?"
        );
        ps.setLong(1, additionalSeconds);
        ps.setString(2, uuid);
        ps.executeUpdate();
        ps.close();
    }

    public synchronized void setFirstJoined(String uuid, long timestamp) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
            "UPDATE player_cache SET first_joined = ? WHERE uuid = ? AND first_joined IS NULL"
        );
        ps.setLong(1, timestamp);
        ps.setString(2, uuid);
        ps.executeUpdate();
        ps.close();
    }

    public synchronized void setAllPlayersOfflineForServer(String serverId, long now) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
            "UPDATE player_cache SET is_online = 0, last_seen = ?, server_id = NULL WHERE server_id = ?"
        );
        ps.setLong(1, now);
        ps.setString(2, serverId);
        ps.executeUpdate();
        ps.close();
    }

    // ---- Utility ----

    private Map<String, Object> resultSetToMap(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int cols = meta.getColumnCount();
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 1; i <= cols; i++) {
            map.put(meta.getColumnLabel(i), rs.getObject(i));
        }
        return map;
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
}
