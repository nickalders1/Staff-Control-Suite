-- Staff Control Suite — Database Schema
-- Database: SQLite (default) / MariaDB/MySQL (v0.2)
-- Generated for: proxy-plugin

-- ============================================================
-- Core tables
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    username    TEXT    NOT NULL UNIQUE,
    password_hash TEXT  NOT NULL,
    is_active   INTEGER NOT NULL DEFAULT 1,
    created_at  INTEGER NOT NULL,
    updated_at  INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS roles (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    name          TEXT    NOT NULL UNIQUE,
    display_name  TEXT    NOT NULL,
    is_system_role INTEGER NOT NULL DEFAULT 0,
    created_at    INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS permissions (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    node         TEXT    NOT NULL UNIQUE,
    display_name TEXT    NOT NULL
);

-- ============================================================
-- Join tables
-- ============================================================

CREATE TABLE IF NOT EXISTS user_roles (
    user_id  INTEGER NOT NULL,
    role_id  INTEGER NOT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id       INTEGER NOT NULL,
    permission_id INTEGER NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    FOREIGN KEY (role_id)       REFERENCES roles(id)       ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
);

-- ============================================================
-- Server registry
-- ============================================================

CREATE TABLE IF NOT EXISTS servers (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    server_id   TEXT    NOT NULL UNIQUE,
    server_name TEXT    NOT NULL,
    server_type TEXT    NOT NULL DEFAULT 'generic',
    host        TEXT,
    port        INTEGER,
    agent_token TEXT    NOT NULL,
    is_active   INTEGER NOT NULL DEFAULT 1,
    created_at  INTEGER NOT NULL,
    updated_at  INTEGER NOT NULL
);

-- ============================================================
-- Authentication
-- ============================================================

CREATE TABLE IF NOT EXISTS sessions (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id     INTEGER NOT NULL,
    token       TEXT    NOT NULL UNIQUE,
    ip_address  TEXT,
    created_at  INTEGER NOT NULL,
    expires_at  INTEGER NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_sessions_token      ON sessions(token);
CREATE INDEX IF NOT EXISTS idx_sessions_expires_at ON sessions(expires_at);

-- ============================================================
-- Audit & logging
-- ============================================================

CREATE TABLE IF NOT EXISTS audit_logs (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id    INTEGER,
    username   TEXT,
    action     TEXT    NOT NULL,
    target     TEXT,
    details    TEXT,
    ip_address TEXT,
    timestamp  INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_timestamp ON audit_logs(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_user_id   ON audit_logs(user_id);

CREATE TABLE IF NOT EXISTS command_logs (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id   INTEGER NOT NULL,
    username  TEXT    NOT NULL,
    server_id TEXT    NOT NULL,
    command   TEXT    NOT NULL,
    timestamp INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_command_logs_timestamp ON command_logs(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_command_logs_user_id   ON command_logs(user_id);

-- ============================================================
-- Player cache (updated by agents)
-- ============================================================

CREATE TABLE IF NOT EXISTS player_cache (
    uuid            TEXT    PRIMARY KEY,
    name            TEXT    NOT NULL,
    server_id       TEXT,
    is_online       INTEGER NOT NULL DEFAULT 0,
    last_seen       INTEGER,
    first_joined    INTEGER,
    playtime_seconds INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_player_cache_name      ON player_cache(name);
CREATE INDEX IF NOT EXISTS idx_player_cache_server_id ON player_cache(server_id);
CREATE INDEX IF NOT EXISTS idx_player_cache_is_online ON player_cache(is_online);

-- ============================================================
-- Punishments (placeholder for v0.2)
-- ============================================================

CREATE TABLE IF NOT EXISTS punishment_history (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    uuid       TEXT    NOT NULL,
    type       TEXT    NOT NULL,   -- ban, mute, kick, warn
    reason     TEXT,
    issued_by  TEXT,
    issued_at  INTEGER NOT NULL,
    expires_at INTEGER,            -- null = permanent
    is_active  INTEGER NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_punishments_uuid      ON punishment_history(uuid);
CREATE INDEX IF NOT EXISTS idx_punishments_is_active ON punishment_history(is_active);

-- ============================================================
-- Playtime history (placeholder for v0.2)
-- ============================================================

CREATE TABLE IF NOT EXISTS playtime_history (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    uuid             TEXT    NOT NULL,
    server_id        TEXT    NOT NULL,
    session_start    INTEGER NOT NULL,
    session_end      INTEGER,
    duration_seconds INTEGER
);

CREATE INDEX IF NOT EXISTS idx_playtime_uuid      ON playtime_history(uuid);
CREATE INDEX IF NOT EXISTS idx_playtime_server_id ON playtime_history(server_id);

-- ============================================================
-- Seed data: permissions
-- ============================================================

INSERT OR IGNORE INTO permissions (node, display_name) VALUES
    ('console.view',                'View Console'),
    ('console.command',             'Execute Console Commands'),
    ('players.view',                'View Players'),
    ('players.details',             'View Player Details'),
    ('players.punishments.view',    'View Punishments'),
    ('players.punishments.create',  'Issue Punishments'),
    ('servers.view',                'View Servers'),
    ('servers.manage',              'Manage Servers'),
    ('users.view',                  'View Users'),
    ('users.manage',                'Manage Users'),
    ('roles.view',                  'View Roles'),
    ('roles.manage',                'Manage Roles'),
    ('settings.view',               'View Settings'),
    ('settings.manage',             'Manage Settings'),
    ('audit.view',                  'View Audit Logs');

-- ============================================================
-- Seed data: default roles
-- ============================================================

INSERT OR IGNORE INTO roles (name, display_name, is_system_role, created_at) VALUES
    ('owner', 'Owner', 1, strftime('%s', 'now') * 1000),
    ('admin', 'Admin', 1, strftime('%s', 'now') * 1000),
    ('staff', 'Staff', 0, strftime('%s', 'now') * 1000);

-- Owner: all permissions
INSERT OR IGNORE INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r, permissions p WHERE r.name = 'owner';

-- Admin: all permissions except roles.manage and users.manage (adjust as needed)
INSERT OR IGNORE INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r, permissions p
    WHERE r.name = 'admin'
    AND p.node NOT IN ('roles.manage');

-- Staff: basic view permissions
INSERT OR IGNORE INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r, permissions p
    WHERE r.name = 'staff'
    AND p.node IN ('console.view', 'players.view', 'players.details', 'servers.view');
