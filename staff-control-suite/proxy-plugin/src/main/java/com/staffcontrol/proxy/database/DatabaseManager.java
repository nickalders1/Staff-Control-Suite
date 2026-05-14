package com.staffcontrol.proxy.database;

import com.staffcontrol.proxy.config.ProxyConfig;
import com.staffcontrol.proxy.model.PlayerInfo;

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
        Class.forName("org.sqlite.JDBC");
        String dbPath = dataDirectory.resolve(config.getDatabasePath()).toString();
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA foreign_keys=ON");
            stmt.execute("PRAGMA busy_timeout=10000");
        }
        createTables();
        migrateSchema();
        seedPermissions();
        seedDefaultRoles();
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
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
                    playtime_seconds INTEGER NOT NULL DEFAULT 0,
                    ping INTEGER DEFAULT 0,
                    health REAL DEFAULT 20.0,
                    food_level INTEGER DEFAULT 20,
                    gamemode TEXT,
                    world TEXT,
                    last_joined_at INTEGER,
                    last_disconnect_at INTEGER
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_sessions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid TEXT NOT NULL,
                    server_id TEXT NOT NULL,
                    session_start INTEGER NOT NULL,
                    session_end INTEGER,
                    duration_seconds INTEGER,
                    FOREIGN KEY (player_uuid) REFERENCES player_cache(uuid)
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_server_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid TEXT NOT NULL,
                    server_id TEXT NOT NULL,
                    joined_at INTEGER NOT NULL,
                    left_at INTEGER,
                    FOREIGN KEY (player_uuid) REFERENCES player_cache(uuid)
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

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_player_cache_online ON player_cache(is_online)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_player_sessions_uuid ON player_sessions(player_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_audit_logs_timestamp ON audit_logs(timestamp DESC)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sessions_token ON sessions(token)");
        }
    }

    private void migrateSchema() {
        String[] migrations = {
            "ALTER TABLE player_cache ADD COLUMN ping INTEGER DEFAULT 0",
            "ALTER TABLE player_cache ADD COLUMN health REAL DEFAULT 20.0",
            "ALTER TABLE player_cache ADD COLUMN food_level INTEGER DEFAULT 20",
            "ALTER TABLE player_cache ADD COLUMN gamemode TEXT",
            "ALTER TABLE player_cache ADD COLUMN world TEXT",
            "ALTER TABLE player_cache ADD COLUMN last_joined_at INTEGER",
            "ALTER TABLE player_cache ADD COLUMN last_disconnect_at INTEGER",
            "ALTER TABLE servers ADD COLUMN last_heartbeat_at INTEGER",
            "ALTER TABLE servers ADD COLUMN version TEXT",
            "ALTER TABLE punishments ADD COLUMN revoked_by_username TEXT DEFAULT ''"
        };
        for (String sql : migrations) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(sql);
            } catch (SQLException ignored) {
                // Column already exists — safe to ignore
            }
        }

        // Moderation tables
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS punishments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    target_uuid TEXT,
                    target_name TEXT NOT NULL,
                    target_ip_hash TEXT,
                    action_type TEXT NOT NULL,
                    reason TEXT NOT NULL,
                    duration_seconds INTEGER DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    expires_at INTEGER DEFAULT 0,
                    created_by_user_id INTEGER NOT NULL,
                    created_by_username TEXT NOT NULL,
                    target_server TEXT DEFAULT 'global',
                    evidence TEXT DEFAULT '',
                    active INTEGER DEFAULT 1,
                    revoked_at INTEGER DEFAULT 0,
                    revoked_by_user_id INTEGER DEFAULT 0,
                    revoked_by_username TEXT DEFAULT '',
                    revoke_reason TEXT DEFAULT ''
                )""");
        } catch (SQLException ignored) {}

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_punishments_target_uuid ON punishments(target_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_punishments_target_name ON punishments(target_name)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_punishments_active ON punishments(active)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_punishments_action_type ON punishments(action_type)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_punishments_expires_at ON punishments(expires_at)");
        } catch (SQLException ignored) {}

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS punishment_presets (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    category TEXT NOT NULL,
                    name TEXT NOT NULL,
                    description TEXT DEFAULT '',
                    action_type TEXT NOT NULL,
                    duration_seconds INTEGER DEFAULT 0,
                    severity INTEGER DEFAULT 1,
                    stackable INTEGER DEFAULT 1,
                    bypass_cap INTEGER DEFAULT 0,
                    requires_ip_ban INTEGER DEFAULT 0,
                    enabled INTEGER DEFAULT 1
                )""");
        } catch (SQLException ignored) {}

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS punishment_preset_links (
                    punishment_id INTEGER NOT NULL,
                    preset_id INTEGER NOT NULL,
                    PRIMARY KEY (punishment_id, preset_id)
                )""");
        } catch (SQLException ignored) {}

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_notes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    target_uuid TEXT,
                    target_name TEXT NOT NULL,
                    note TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    created_by_user_id INTEGER NOT NULL,
                    created_by_username TEXT NOT NULL,
                    visibility TEXT DEFAULT 'staff'
                )""");
        } catch (SQLException ignored) {}

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_warnings (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    target_uuid TEXT,
                    target_name TEXT NOT NULL,
                    reason TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    created_by_user_id INTEGER NOT NULL,
                    created_by_username TEXT NOT NULL,
                    active INTEGER DEFAULT 1
                )""");
        } catch (SQLException ignored) {}
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
            "audit.view", "View audit logs",
            "moderation.view", "View moderation records",
            "moderation.warn", "Issue warnings to players",
            "moderation.mute", "Mute players",
            "moderation.unmute", "Unmute players",
            "moderation.kick", "Kick players",
            "moderation.ban", "Permanently ban players",
            "moderation.tempban", "Temporarily ban players",
            "moderation.unban", "Revoke player bans",
            "moderation.ipban", "IP-ban players",
            "moderation.notes.view", "View player notes",
            "moderation.notes.create", "Create player notes",
            "moderation.presets.view", "View punishment presets",
            "moderation.presets.manage", "Manage punishment presets",
            "moderation.override_duration", "Override punishment duration limits",
            "moderation.override_reason", "Override punishment reason",
            "moderation.exempt", "Exempt from being punished",
            "moderation.view_ip", "View player IP hashes in moderation records"
        };

        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO permissions (node, display_name) VALUES (?, ?)")) {
            for (int i = 0; i < permissions.length; i += 2) {
                ps.setString(1, permissions[i]);
                ps.setString(2, permissions[i + 1]);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void seedDefaultRoles() throws SQLException {
        long now = System.currentTimeMillis();

        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO roles (name, display_name, is_system_role, created_at) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, "owner"); ps.setString(2, "Owner"); ps.setInt(3, 1); ps.setLong(4, now); ps.execute();
            ps.setString(1, "admin"); ps.setString(2, "Admin"); ps.setInt(3, 1); ps.setLong(4, now); ps.execute();
            ps.setString(1, "staff"); ps.setString(2, "Staff"); ps.setInt(3, 0); ps.setLong(4, now); ps.execute();
        }

        Map<String, Object> ownerRole = getRoleByName("owner");
        if (ownerRole != null) {
            long ownerId = getLong(ownerRole, "id");
            if (getPermissionsForRole(ownerId).isEmpty()) {
                List<Map<String, Object>> allPerms = getAllPermissions();
                List<String> nodes = new ArrayList<>();
                for (Map<String, Object> p : allPerms) nodes.add(getString(p, "node"));
                setPermissionsForRole(ownerId, nodes);
            }
        }

        Map<String, Object> adminRole = getRoleByName("admin");
        if (adminRole != null) {
            long adminId = getLong(adminRole, "id");
            if (getPermissionsForRole(adminId).isEmpty()) {
                List<Map<String, Object>> allPerms = getAllPermissions();
                List<String> adminNodes = new ArrayList<>();
                for (Map<String, Object> p : allPerms) {
                    String node = getString(p, "node");
                    if (!"roles.manage".equals(node) && !"users.manage".equals(node)) adminNodes.add(node);
                }
                setPermissionsForRole(adminId, adminNodes);
            }
        }

        Map<String, Object> staffRole = getRoleByName("staff");
        if (staffRole != null) {
            long staffId = getLong(staffRole, "id");
            if (getPermissionsForRole(staffId).isEmpty()) {
                setPermissionsForRole(staffId, Arrays.asList(
                    "console.view", "players.view", "players.details", "servers.view"
                ));
            }
        }
    }

    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException ignored) {}
    }

    // ---- User Methods ----

    public synchronized long createUser(String username, String passwordHash, long now) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO users (username, password_hash, is_active, created_at, updated_at) VALUES (?, ?, 1, ?, ?)")) {
            ps.setString(1, username); ps.setString(2, passwordHash);
            ps.setLong(3, now); ps.setLong(4, now);
            ps.executeUpdate();
        }
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT last_insert_rowid()")) {
            return rs.next() ? rs.getLong(1) : -1L;
        }
    }

    public synchronized Map<String, Object> getUserByUsername(String username) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? resultSetToMap(rs) : null;
            }
        }
    }

    public synchronized Map<String, Object> getUserById(long id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM users WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? resultSetToMap(rs) : null;
            }
        }
    }

    public synchronized List<Map<String, Object>> getAllUsers() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT u.*, r.id as role_id, r.name as role_name, r.display_name as role_display_name " +
                "FROM users u LEFT JOIN user_roles ur ON u.id = ur.user_id " +
                "LEFT JOIN roles r ON ur.role_id = r.id")) {
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) results.add(resultSetToMap(rs));
                return results;
            }
        }
    }

    public synchronized void updateUser(long id, String username, boolean isActive, long now) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE users SET username = ?, is_active = ?, updated_at = ? WHERE id = ?")) {
            ps.setString(1, username); ps.setInt(2, isActive ? 1 : 0);
            ps.setLong(3, now); ps.setLong(4, id);
            ps.executeUpdate();
        }
    }

    public synchronized void updateUserPassword(long id, String passwordHash, long now) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE users SET password_hash = ?, updated_at = ? WHERE id = ?")) {
            ps.setString(1, passwordHash); ps.setLong(2, now); ps.setLong(3, id);
            ps.executeUpdate();
        }
    }

    public synchronized void deleteUser(long id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM user_roles WHERE user_id = ?")) {
            ps.setLong(1, id); ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM sessions WHERE user_id = ?")) {
            ps.setLong(1, id); ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM users WHERE id = ?")) {
            ps.setLong(1, id); ps.executeUpdate();
        }
    }

    public synchronized void setUserRole(long userId, long roleId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM user_roles WHERE user_id = ?")) {
            ps.setLong(1, userId); ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)")) {
            ps.setLong(1, userId); ps.setLong(2, roleId); ps.executeUpdate();
        }
    }

    public synchronized long getUserRoleId(long userId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT role_id FROM user_roles WHERE user_id = ?")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong("role_id") : -1L;
            }
        }
    }

    // ---- Role Methods ----

    public synchronized long createRole(String name, String displayName, boolean isSystemRole, long now) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO roles (name, display_name, is_system_role, created_at) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, name); ps.setString(2, displayName);
            ps.setInt(3, isSystemRole ? 1 : 0); ps.setLong(4, now);
            ps.executeUpdate();
        }
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT last_insert_rowid()")) {
            return rs.next() ? rs.getLong(1) : -1L;
        }
    }

    public synchronized Map<String, Object> getRoleById(long id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM roles WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? resultSetToMap(rs) : null;
            }
        }
    }

    public synchronized Map<String, Object> getRoleByName(String name) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM roles WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? resultSetToMap(rs) : null;
            }
        }
    }

    public synchronized List<Map<String, Object>> getAllRoles() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM roles ORDER BY id ASC")) {
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) results.add(resultSetToMap(rs));
                return results;
            }
        }
    }

    public synchronized void updateRole(long id, String displayName) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE roles SET display_name = ? WHERE id = ?")) {
            ps.setString(1, displayName); ps.setLong(2, id); ps.executeUpdate();
        }
    }

    public synchronized void deleteRole(long id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM role_permissions WHERE role_id = ?")) {
            ps.setLong(1, id); ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM user_roles WHERE role_id = ?")) {
            ps.setLong(1, id); ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM roles WHERE id = ?")) {
            ps.setLong(1, id); ps.executeUpdate();
        }
    }

    // ---- Permission Methods ----

    public synchronized List<Map<String, Object>> getAllPermissions() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM permissions ORDER BY id ASC")) {
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) results.add(resultSetToMap(rs));
                return results;
            }
        }
    }

    public synchronized Map<String, Object> getPermissionByNode(String node) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM permissions WHERE node = ?")) {
            ps.setString(1, node);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? resultSetToMap(rs) : null;
            }
        }
    }

    public synchronized List<String> getPermissionsForRole(long roleId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT p.node FROM permissions p JOIN role_permissions rp ON p.id = rp.permission_id WHERE rp.role_id = ?")) {
            ps.setLong(1, roleId);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> results = new ArrayList<>();
                while (rs.next()) results.add(rs.getString("node"));
                return results;
            }
        }
    }

    public synchronized void setPermissionsForRole(long roleId, List<String> nodes) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM role_permissions WHERE role_id = ?")) {
            ps.setLong(1, roleId); ps.executeUpdate();
        }
        if (nodes == null || nodes.isEmpty()) return;
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO role_permissions (role_id, permission_id) SELECT ?, id FROM permissions WHERE node = ?")) {
            for (String node : nodes) {
                ps.setLong(1, roleId); ps.setString(2, node); ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public synchronized boolean userHasPermission(long userId, String node) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM users u JOIN user_roles ur ON u.id = ur.user_id " +
                "JOIN role_permissions rp ON ur.role_id = rp.role_id " +
                "JOIN permissions p ON rp.permission_id = p.id WHERE u.id = ? AND p.node = ?")) {
            ps.setLong(1, userId); ps.setString(2, node);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    // ---- Server Methods ----

    public synchronized long addServer(String serverId, String serverName, String serverType,
                                        String agentToken, long now) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO servers (server_id, server_name, server_type, agent_token, is_active, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 1, ?, ?)")) {
            ps.setString(1, serverId); ps.setString(2, serverName); ps.setString(3, serverType);
            ps.setString(4, agentToken); ps.setLong(5, now); ps.setLong(6, now);
            ps.executeUpdate();
        }
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT last_insert_rowid()")) {
            return rs.next() ? rs.getLong(1) : -1L;
        }
    }

    public synchronized Map<String, Object> getServer(String serverId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM servers WHERE server_id = ?")) {
            ps.setString(1, serverId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? resultSetToMap(rs) : null;
            }
        }
    }

    public synchronized List<Map<String, Object>> getAllServers() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM servers ORDER BY id ASC")) {
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) results.add(resultSetToMap(rs));
                return results;
            }
        }
    }

    public synchronized void updateServer(long id, String serverId, String serverName, String serverType, long now) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE servers SET server_id = ?, server_name = ?, server_type = ?, updated_at = ? WHERE id = ?")) {
            ps.setString(1, serverId); ps.setString(2, serverName); ps.setString(3, serverType);
            ps.setLong(4, now); ps.setLong(5, id);
            ps.executeUpdate();
        }
    }

    public synchronized void updateServerToken(String serverId, String newToken, long now) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE servers SET agent_token = ?, updated_at = ? WHERE server_id = ?")) {
            ps.setString(1, newToken); ps.setLong(2, now); ps.setString(3, serverId);
            ps.executeUpdate();
        }
    }

    public synchronized void deleteServer(String serverId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM servers WHERE server_id = ?")) {
            ps.setString(1, serverId); ps.executeUpdate();
        }
    }

    // ---- Session Methods ----

    public synchronized void createSession(long userId, String token, String ipAddress,
                                            long now, long expiresAt) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO sessions (user_id, token, ip_address, created_at, expires_at) VALUES (?, ?, ?, ?, ?)")) {
            ps.setLong(1, userId); ps.setString(2, token); ps.setString(3, ipAddress);
            ps.setLong(4, now); ps.setLong(5, expiresAt);
            ps.executeUpdate();
        }
    }

    public synchronized Map<String, Object> validateSession(String token) throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT u.id, u.username, u.is_active, s.expires_at, s.ip_address " +
                "FROM sessions s JOIN users u ON s.user_id = u.id " +
                "WHERE s.token = ? AND s.expires_at > ? AND u.is_active = 1")) {
            ps.setString(1, token); ps.setLong(2, now);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? resultSetToMap(rs) : null;
            }
        }
    }

    public synchronized void deleteSession(String token) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM sessions WHERE token = ?")) {
            ps.setString(1, token); ps.executeUpdate();
        }
    }

    public synchronized void deleteExpiredSessions() throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM sessions WHERE expires_at <= ?")) {
            ps.setLong(1, now); ps.executeUpdate();
        }
    }

    public synchronized long getUserIdFromToken(String token) throws SQLException {
        Map<String, Object> session = validateSession(token);
        if (session == null) return -1L;
        Object id = session.get("id");
        return id instanceof Number ? ((Number) id).longValue() : -1L;
    }

    // ---- Audit/Command Log Methods ----

    public synchronized void logAudit(Long userId, String username, String action, String target,
                                       String details, String ipAddress) throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO audit_logs (user_id, username, action, target, details, ip_address, timestamp) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            if (userId != null) ps.setLong(1, userId); else ps.setNull(1, Types.INTEGER);
            ps.setString(2, username); ps.setString(3, action); ps.setString(4, target);
            ps.setString(5, details); ps.setString(6, ipAddress); ps.setLong(7, now);
            ps.executeUpdate();
        }
    }

    public synchronized List<Map<String, Object>> getAuditLogs(int page, int limit) throws SQLException {
        return getAuditLogs(page, limit, null, null, null);
    }

    public synchronized List<Map<String, Object>> getAuditLogs(int page, int limit,
            String action, String username, String search) throws SQLException {
        int offset = (page - 1) * limit;
        StringBuilder sb = new StringBuilder("SELECT * FROM audit_logs WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (action != null && !action.isEmpty()) {
            sb.append(" AND action = ?");
            params.add(action);
        }
        if (username != null && !username.isEmpty()) {
            sb.append(" AND username LIKE ?");
            params.add("%" + username + "%");
        }
        if (search != null && !search.isEmpty()) {
            sb.append(" AND (action LIKE ? OR username LIKE ? OR target LIKE ? OR details LIKE ? OR ip_address LIKE ?)");
            String q = "%" + search + "%";
            params.add(q); params.add(q); params.add(q); params.add(q); params.add(q);
        }

        sb.append(" ORDER BY timestamp DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        try (PreparedStatement ps = connection.prepareStatement(sb.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof Integer) ps.setInt(i + 1, (int) p);
                else ps.setString(i + 1, (String) p);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) results.add(resultSetToMap(rs));
                return results;
            }
        }
    }

    public synchronized int getAuditLogsTotal() throws SQLException {
        return getAuditLogsTotal(null, null, null);
    }

    public synchronized int getAuditLogsTotal(String action, String username, String search) throws SQLException {
        StringBuilder sb = new StringBuilder("SELECT COUNT(*) as total FROM audit_logs WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (action != null && !action.isEmpty()) {
            sb.append(" AND action = ?");
            params.add(action);
        }
        if (username != null && !username.isEmpty()) {
            sb.append(" AND username LIKE ?");
            params.add("%" + username + "%");
        }
        if (search != null && !search.isEmpty()) {
            sb.append(" AND (action LIKE ? OR username LIKE ? OR target LIKE ? OR details LIKE ? OR ip_address LIKE ?)");
            String q = "%" + search + "%";
            params.add(q); params.add(q); params.add(q); params.add(q); params.add(q);
        }

        try (PreparedStatement ps = connection.prepareStatement(sb.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof Integer) ps.setInt(i + 1, (int) p);
                else ps.setString(i + 1, (String) p);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("total") : 0;
            }
        }
    }

    public synchronized void logCommand(long userId, String username, String serverId, String command) throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO command_logs (user_id, username, server_id, command, timestamp) VALUES (?, ?, ?, ?, ?)")) {
            ps.setLong(1, userId); ps.setString(2, username); ps.setString(3, serverId);
            ps.setString(4, command); ps.setLong(5, now);
            ps.executeUpdate();
        }
    }

    // ---- Player Cache Methods ----

    public synchronized void upsertPlayerFull(PlayerInfo player, long now) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO player_cache (uuid, name, server_id, is_online, last_seen, first_joined, " +
                "playtime_seconds, ping, health, food_level, gamemode, world, last_joined_at) " +
                "VALUES (?, ?, ?, 1, ?, COALESCE((SELECT first_joined FROM player_cache WHERE uuid = ?), ?), " +
                "COALESCE((SELECT playtime_seconds FROM player_cache WHERE uuid = ?), 0), ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET " +
                "name = excluded.name, server_id = excluded.server_id, is_online = 1, last_seen = excluded.last_seen, " +
                "first_joined = COALESCE(first_joined, excluded.first_joined), " +
                "ping = excluded.ping, health = excluded.health, food_level = excluded.food_level, " +
                "gamemode = excluded.gamemode, world = excluded.world, last_joined_at = excluded.last_joined_at")) {
            ps.setString(1, player.getUuid());
            ps.setString(2, player.getName());
            ps.setString(3, player.getServerId());
            ps.setLong(4, now);
            ps.setString(5, player.getUuid());
            ps.setLong(6, player.getFirstJoined() > 0 ? player.getFirstJoined() : now);
            ps.setString(7, player.getUuid());
            ps.setInt(8, player.getPing());
            ps.setDouble(9, player.getHealth());
            ps.setInt(10, player.getFoodLevel());
            ps.setString(11, player.getGamemode());
            ps.setString(12, player.getWorld());
            ps.setLong(13, player.getLastJoined() > 0 ? player.getLastJoined() : now);
            ps.executeUpdate();
        }
    }

    public synchronized void upsertPlayer(String uuid, String name, String serverId,
                                           boolean isOnline, long now) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO player_cache (uuid, name, server_id, is_online, last_seen) VALUES (?, ?, ?, ?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, server_id = excluded.server_id, " +
                "is_online = excluded.is_online, last_seen = excluded.last_seen")) {
            ps.setString(1, uuid); ps.setString(2, name); ps.setString(3, serverId);
            ps.setInt(4, isOnline ? 1 : 0); ps.setLong(5, now);
            ps.executeUpdate();
        }
    }

    public synchronized void setPlayerOfflineFull(String uuid, long additionalPlaytimeSeconds, long now) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE player_cache SET is_online = 0, server_id = NULL, last_seen = ?, " +
                "last_disconnect_at = ?, playtime_seconds = playtime_seconds + ? WHERE uuid = ?")) {
            ps.setLong(1, now); ps.setLong(2, now);
            ps.setLong(3, additionalPlaytimeSeconds); ps.setString(4, uuid);
            ps.executeUpdate();
        }
    }

    public synchronized void setPlayerOffline(String uuid, long now) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE player_cache SET is_online = 0, last_seen = ?, server_id = NULL WHERE uuid = ?")) {
            ps.setLong(1, now); ps.setString(2, uuid);
            ps.executeUpdate();
        }
    }

    public synchronized Map<String, Object> getPlayer(String uuid) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM player_cache WHERE uuid = ?")) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? resultSetToMap(rs) : null;
            }
        }
    }

    public synchronized List<Map<String, Object>> getAllPlayersHistory(int page, int limit, String search) throws SQLException {
        boolean hasSearch = search != null && !search.isBlank();
        String sql = "SELECT * FROM player_cache"
                   + (hasSearch ? " WHERE name LIKE ?" : "")
                   + " ORDER BY is_online DESC, last_seen DESC LIMIT ? OFFSET ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int idx = 1;
            if (hasSearch) ps.setString(idx++, "%" + search + "%");
            ps.setInt(idx++, limit);
            ps.setInt(idx, (page - 1) * limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) results.add(resultSetToMap(rs));
                return results;
            }
        }
    }

    public synchronized int getAllPlayersHistoryTotal(String search) throws SQLException {
        boolean hasSearch = search != null && !search.isBlank();
        String sql = "SELECT COUNT(*) FROM player_cache" + (hasSearch ? " WHERE name LIKE ?" : "");
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (hasSearch) ps.setString(1, "%" + search + "%");
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public synchronized List<Map<String, Object>> getAllOnlinePlayers() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM player_cache WHERE is_online = 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) results.add(resultSetToMap(rs));
                return results;
            }
        }
    }

    public synchronized List<Map<String, Object>> getPlayersByServer(String serverId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM player_cache WHERE server_id = ? AND is_online = 1")) {
            ps.setString(1, serverId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) results.add(resultSetToMap(rs));
                return results;
            }
        }
    }

    public synchronized void updatePlayerPlaytime(String uuid, long additionalSeconds) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE player_cache SET playtime_seconds = playtime_seconds + ? WHERE uuid = ?")) {
            ps.setLong(1, additionalSeconds); ps.setString(2, uuid);
            ps.executeUpdate();
        }
    }

    public synchronized void setFirstJoined(String uuid, long timestamp) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE player_cache SET first_joined = ? WHERE uuid = ? AND first_joined IS NULL")) {
            ps.setLong(1, timestamp); ps.setString(2, uuid);
            ps.executeUpdate();
        }
    }

    public synchronized void setAllPlayersOfflineForServer(String serverId, long now) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE player_cache SET is_online = 0, last_seen = ?, server_id = NULL WHERE server_id = ?")) {
            ps.setLong(1, now); ps.setString(2, serverId);
            ps.executeUpdate();
        }
    }

    // ---- Punishment Methods ----

    public synchronized long createPunishment(String targetUuid, String targetName, String targetIpHash,
            String actionType, String reason, long durationSeconds, long expiresAt,
            long createdByUserId, String createdByUsername, String targetServer,
            String evidence) throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO punishments (target_uuid, target_name, target_ip_hash, action_type, reason, " +
                "duration_seconds, created_at, expires_at, created_by_user_id, created_by_username, " +
                "target_server, evidence, active) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)")) {
            ps.setString(1, targetUuid);
            ps.setString(2, targetName);
            ps.setString(3, targetIpHash);
            ps.setString(4, actionType);
            ps.setString(5, reason);
            ps.setLong(6, durationSeconds);
            ps.setLong(7, now);
            ps.setLong(8, expiresAt);
            ps.setLong(9, createdByUserId);
            ps.setString(10, createdByUsername);
            ps.setString(11, targetServer != null ? targetServer : "global");
            ps.setString(12, evidence != null ? evidence : "");
            ps.executeUpdate();
        }
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT last_insert_rowid()")) {
            return rs.next() ? rs.getLong(1) : -1L;
        }
    }

    public synchronized List<Map<String, Object>> getPunishmentHistory(String targetName, int page, int limit) throws SQLException {
        int offset = (page - 1) * limit;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM punishments WHERE target_name = ? COLLATE NOCASE " +
                "ORDER BY created_at DESC LIMIT ? OFFSET ?")) {
            ps.setString(1, targetName);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) results.add(resultSetToMap(rs));
                return results;
            }
        }
    }

    public synchronized int getPunishmentHistoryTotal(String targetName) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) as total FROM punishments WHERE target_name = ? COLLATE NOCASE")) {
            ps.setString(1, targetName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("total") : 0;
            }
        }
    }

    public synchronized List<Map<String, Object>> getActivePunishments(String targetName) throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM punishments WHERE target_name = ? COLLATE NOCASE AND active = 1 " +
                "AND (expires_at = 0 OR expires_at > ?) ORDER BY created_at DESC")) {
            ps.setString(1, targetName);
            ps.setLong(2, now);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) results.add(resultSetToMap(rs));
                return results;
            }
        }
    }

    public synchronized List<Map<String, Object>> getAllActivePunishments() throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM punishments WHERE active = 1 AND (expires_at = 0 OR expires_at > ?) " +
                "ORDER BY created_at DESC")) {
            ps.setLong(1, now);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) results.add(resultSetToMap(rs));
                return results;
            }
        }
    }

    public synchronized boolean hasActiveBan(String targetUuid, String targetName) throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM punishments WHERE active = 1 " +
                "AND (action_type = 'BAN' OR action_type = 'TEMP_BAN') " +
                "AND (expires_at = 0 OR expires_at > ?) " +
                "AND (target_uuid = ? OR target_name = ? COLLATE NOCASE) LIMIT 1")) {
            ps.setLong(1, now);
            ps.setString(2, targetUuid != null ? targetUuid : "");
            ps.setString(3, targetName);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public synchronized boolean hasActiveIpBan(String ipHash) throws SQLException {
        if (ipHash == null || ipHash.isEmpty()) return false;
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM punishments WHERE active = 1 " +
                "AND (action_type = 'IP_BAN' OR action_type = 'TEMP_IP_BAN') " +
                "AND (expires_at = 0 OR expires_at > ?) " +
                "AND target_ip_hash = ? LIMIT 1")) {
            ps.setLong(1, now);
            ps.setString(2, ipHash);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public synchronized boolean hasActiveMute(String targetUuid, String targetName) throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM punishments WHERE active = 1 " +
                "AND (action_type = 'MUTE' OR action_type = 'TEMP_MUTE') " +
                "AND (expires_at = 0 OR expires_at > ?) " +
                "AND (target_uuid = ? OR target_name = ? COLLATE NOCASE) LIMIT 1")) {
            ps.setLong(1, now);
            ps.setString(2, targetUuid != null ? targetUuid : "");
            ps.setString(3, targetName);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public synchronized Map<String, Object> getActiveBanRecord(String targetUuid, String targetName) throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM punishments WHERE active = 1 " +
                "AND (action_type = 'BAN' OR action_type = 'TEMP_BAN') " +
                "AND (expires_at = 0 OR expires_at > ?) " +
                "AND (target_uuid = ? OR target_name = ? COLLATE NOCASE) " +
                "ORDER BY created_at DESC LIMIT 1")) {
            ps.setLong(1, now);
            ps.setString(2, targetUuid != null ? targetUuid : "");
            ps.setString(3, targetName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? resultSetToMap(rs) : null;
            }
        }
    }

    public synchronized Map<String, Object> getActiveIpBanRecord(String ipHash) throws SQLException {
        if (ipHash == null || ipHash.isEmpty()) return null;
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM punishments WHERE active = 1 " +
                "AND (action_type = 'IP_BAN' OR action_type = 'TEMP_IP_BAN') " +
                "AND (expires_at = 0 OR expires_at > ?) " +
                "AND target_ip_hash = ? ORDER BY created_at DESC LIMIT 1")) {
            ps.setLong(1, now);
            ps.setString(2, ipHash);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? resultSetToMap(rs) : null;
            }
        }
    }

    public synchronized List<Map<String, Object>> getAllActiveMutes() throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM punishments WHERE active = 1 " +
                "AND (action_type = 'MUTE' OR action_type = 'TEMP_MUTE') " +
                "AND (expires_at = 0 OR expires_at > ?)")) {
            ps.setLong(1, now);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) results.add(resultSetToMap(rs));
                return results;
            }
        }
    }

    public synchronized Map<String, Object> getActiveMuteRecord(String targetUuid, String targetName) throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM punishments WHERE active = 1 " +
                "AND (action_type = 'MUTE' OR action_type = 'TEMP_MUTE') " +
                "AND (expires_at = 0 OR expires_at > ?) " +
                "AND (target_uuid = ? OR target_name = ? COLLATE NOCASE) " +
                "ORDER BY created_at DESC LIMIT 1")) {
            ps.setLong(1, now);
            ps.setString(2, targetUuid != null ? targetUuid : "");
            ps.setString(3, targetName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? resultSetToMap(rs) : null;
            }
        }
    }

    public synchronized boolean revokePunishment(long punishmentId, long revokedByUserId,
            String revokedByUsername, String revokeReason) throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE punishments SET active = 0, revoked_at = ?, revoked_by_user_id = ?, " +
                "revoked_by_username = ?, revoke_reason = ? WHERE id = ? AND active = 1")) {
            ps.setLong(1, now);
            ps.setLong(2, revokedByUserId);
            ps.setString(3, revokedByUsername != null ? revokedByUsername : "");
            ps.setString(4, revokeReason != null ? revokeReason : "");
            ps.setLong(5, punishmentId);
            return ps.executeUpdate() > 0;
        }
    }

    public synchronized List<Map<String, Object>> getRecentPunishments(int limit) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM punishments ORDER BY created_at DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) results.add(resultSetToMap(rs));
                return results;
            }
        }
    }

    public synchronized int getActivePunishmentCount(String actionType) throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) as total FROM punishments WHERE action_type = ? AND active = 1 " +
                "AND (expires_at = 0 OR expires_at > ?)")) {
            ps.setString(1, actionType);
            ps.setLong(2, now);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("total") : 0;
            }
        }
    }

    public synchronized int getPunishmentsToday() throws SQLException {
        long startOfDay = System.currentTimeMillis() - (System.currentTimeMillis() % 86400000L);
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) as total FROM punishments WHERE created_at >= ?")) {
            ps.setLong(1, startOfDay);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("total") : 0;
            }
        }
    }

    public synchronized Map<String, Object> getPunishmentById(long id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM punishments WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? resultSetToMap(rs) : null;
            }
        }
    }

    public synchronized int expireOldPunishments() throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE punishments SET active = 0 WHERE active = 1 AND expires_at > 0 AND expires_at < ?")) {
            ps.setLong(1, now);
            return ps.executeUpdate();
        }
    }

    // ---- Preset Methods ----

    public synchronized List<Map<String, Object>> getPresets() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM punishment_presets ORDER BY category, severity, id")) {
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) results.add(resultSetToMap(rs));
                return results;
            }
        }
    }

    public synchronized void linkPunishmentPreset(long punishmentId, long presetId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO punishment_preset_links(punishment_id, preset_id) VALUES(?, ?)")) {
            ps.setLong(1, punishmentId);
            ps.setLong(2, presetId);
            ps.executeUpdate();
        }
    }

    public synchronized Map<String, Object> getPresetById(long id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM punishment_presets WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? resultSetToMap(rs) : null;
            }
        }
    }

    public synchronized long createPreset(String category, String name, String description,
            String actionType, long durationSeconds, int severity,
            boolean stackable, boolean bypassCap, boolean requiresIpBan) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO punishment_presets (category, name, description, action_type, duration_seconds, " +
                "severity, stackable, bypass_cap, requires_ip_ban, enabled) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1)")) {
            ps.setString(1, category);
            ps.setString(2, name);
            ps.setString(3, description != null ? description : "");
            ps.setString(4, actionType);
            ps.setLong(5, durationSeconds);
            ps.setInt(6, severity);
            ps.setInt(7, stackable ? 1 : 0);
            ps.setInt(8, bypassCap ? 1 : 0);
            ps.setInt(9, requiresIpBan ? 1 : 0);
            ps.executeUpdate();
        }
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT last_insert_rowid()")) {
            return rs.next() ? rs.getLong(1) : -1L;
        }
    }

    public synchronized boolean updatePreset(long id, String category, String name, String description,
            String actionType, long durationSeconds, int severity,
            boolean stackable, boolean bypassCap, boolean requiresIpBan, boolean enabled) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE punishment_presets SET category = ?, name = ?, description = ?, action_type = ?, " +
                "duration_seconds = ?, severity = ?, stackable = ?, bypass_cap = ?, requires_ip_ban = ?, " +
                "enabled = ? WHERE id = ?")) {
            ps.setString(1, category);
            ps.setString(2, name);
            ps.setString(3, description != null ? description : "");
            ps.setString(4, actionType);
            ps.setLong(5, durationSeconds);
            ps.setInt(6, severity);
            ps.setInt(7, stackable ? 1 : 0);
            ps.setInt(8, bypassCap ? 1 : 0);
            ps.setInt(9, requiresIpBan ? 1 : 0);
            ps.setInt(10, enabled ? 1 : 0);
            ps.setLong(11, id);
            return ps.executeUpdate() > 0;
        }
    }

    public synchronized boolean deletePreset(long id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM punishment_presets WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    // ---- Notes Methods ----

    public synchronized List<Map<String, Object>> getNotes(String targetName) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM player_notes WHERE target_name = ? COLLATE NOCASE ORDER BY created_at DESC")) {
            ps.setString(1, targetName);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                while (rs.next()) results.add(resultSetToMap(rs));
                return results;
            }
        }
    }

    public synchronized long createNote(String targetUuid, String targetName, String note,
            long createdByUserId, String createdByUsername, String visibility) throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO player_notes (target_uuid, target_name, note, created_at, " +
                "created_by_user_id, created_by_username, visibility) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, targetUuid);
            ps.setString(2, targetName);
            ps.setString(3, note);
            ps.setLong(4, now);
            ps.setLong(5, createdByUserId);
            ps.setString(6, createdByUsername);
            ps.setString(7, visibility != null ? visibility : "staff");
            ps.executeUpdate();
        }
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT last_insert_rowid()")) {
            return rs.next() ? rs.getLong(1) : -1L;
        }
    }

    // ---- Preset Seeding ----

    public synchronized void seedDefaultPresets() throws SQLException {
        try (PreparedStatement check = connection.prepareStatement(
                "SELECT COUNT(*) as cnt FROM punishment_presets")) {
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next() && rs.getInt("cnt") > 0) return; // Already seeded
            }
        }

        // category, name, description, action_type, duration_seconds, severity, stackable, bypass_cap, requires_ip_ban
        Object[][] presets = {
            // ---- CHAT ----
            {"CHAT", "Spam (Minor)", "First offense spamming in chat", "WARN", 0L, 1, true, false, false},
            {"CHAT", "Spam (Moderate)", "Repeated spamming in chat", "TEMP_MUTE", 3600L, 2, true, false, false},
            {"CHAT", "Spam (Severe)", "Persistent spam after warnings", "TEMP_MUTE", 86400L, 3, true, false, false},
            {"CHAT", "Spam (Extreme)", "Extreme spam/bot-like behavior", "TEMP_BAN", 604800L, 4, true, false, false},
            {"CHAT", "Caps Spam", "Excessive use of capital letters", "WARN", 0L, 1, true, false, false},
            {"CHAT", "Toxic Behavior", "Toxic or aggressive behavior towards other players", "TEMP_MUTE", 7200L, 2, true, false, false},
            {"CHAT", "Discrimination", "Racist, sexist, or other discriminatory language", "TEMP_BAN", 604800L, 4, true, false, false},
            {"CHAT", "Advertising", "Advertising other servers or services", "TEMP_BAN", 259200L, 3, true, false, false},
            {"CHAT", "Doxxing", "Sharing real-life personal information of other players", "BAN", 0L, 5, false, true, false},
            // ---- GAMEPLAY ----
            {"GAMEPLAY", "Bug Abuse", "Exploiting a server or game bug for personal gain", "TEMP_BAN", 259200L, 3, true, false, false},
            {"GAMEPLAY", "Griefing", "Intentional destruction of other players' builds", "TEMP_BAN", 604800L, 3, true, false, false},
            {"GAMEPLAY", "Scamming", "Scamming another player out of in-game items or currency", "TEMP_BAN", 604800L, 3, true, false, false},
            {"GAMEPLAY", "Combat Logging", "Logging out during PvP to avoid death", "WARN", 0L, 1, true, false, false},
            {"GAMEPLAY", "Spawn Killing", "Repeatedly killing players at spawn", "TEMP_BAN", 86400L, 2, true, false, false},
            // ---- CHEATS ----
            {"CHEATS", "KillAura", "Using KillAura or similar combat hacks", "TEMP_BAN", 2592000L, 4, false, false, false},
            {"CHEATS", "Fly Hacks", "Using unauthorized flight modifications", "TEMP_BAN", 1209600L, 4, false, false, false},
            {"CHEATS", "Reach Hacks", "Using extended reach modifications", "TEMP_BAN", 1209600L, 3, false, false, false},
            {"CHEATS", "Speed Hacks", "Using speed modifications beyond server limits", "TEMP_BAN", 1209600L, 3, false, false, false},
            {"CHEATS", "Anti-Knockback", "Using anti-knockback modifications", "TEMP_BAN", 604800L, 3, false, false, false},
            {"CHEATS", "X-Ray", "Using X-Ray texture packs or mods to locate ores/players", "TEMP_BAN", 604800L, 3, false, false, false},
            {"CHEATS", "AutoClicker", "Using an auto-clicker for unfair advantage", "WARN", 0L, 2, true, false, false},
            {"CHEATS", "Baritone / Pathfinder", "Using Baritone or similar automated mining/movement bots", "TEMP_BAN", 604800L, 3, false, false, false},
            {"CHEATS", "Nuker", "Using a block-breaking hack to destroy large areas", "TEMP_BAN", 1209600L, 4, false, false, false},
            {"CHEATS", "Duping", "Exploiting item or currency duplication bugs", "BAN", 0L, 5, false, true, false},
            {"CHEATS", "Crash Exploits", "Using exploits to crash the server or other clients", "BAN", 0L, 5, false, true, false},
            {"CHEATS", "Bot Attack", "Coordinating bot attacks against the server", "BAN", 0L, 5, false, true, true}
        };

        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO punishment_presets (category, name, description, action_type, duration_seconds, " +
                "severity, stackable, bypass_cap, requires_ip_ban, enabled) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1)")) {
            for (Object[] p : presets) {
                ps.setString(1, (String) p[0]);
                ps.setString(2, (String) p[1]);
                ps.setString(3, (String) p[2]);
                ps.setString(4, (String) p[3]);
                ps.setLong(5, (Long) p[4]);
                ps.setInt(6, (Integer) p[5]);
                ps.setInt(7, ((Boolean) p[6]) ? 1 : 0);
                ps.setInt(8, ((Boolean) p[7]) ? 1 : 0);
                ps.setInt(9, ((Boolean) p[8]) ? 1 : 0);
                ps.addBatch();
            }
            ps.executeBatch();
        }
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
