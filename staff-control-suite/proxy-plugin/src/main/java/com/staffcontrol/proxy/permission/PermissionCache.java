package com.staffcontrol.proxy.permission;

import com.staffcontrol.proxy.database.DatabaseManager;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PermissionCache {

    private static final long TTL_MS = 5 * 60 * 1000L;

    private final DatabaseManager database;
    private final ConcurrentHashMap<Long, CachedEntry> cache = new ConcurrentHashMap<>();

    public PermissionCache(DatabaseManager database) {
        this.database = database;
    }

    public Set<String> getPermissions(long userId) {
        CachedEntry entry = cache.get(userId);
        if (entry != null && !entry.isExpired()) {
            return entry.permissions;
        }
        try {
            long roleId = database.getUserRoleId(userId);
            List<String> perms = roleId >= 0 ? database.getPermissionsForRole(roleId) : Collections.emptyList();
            Set<String> permSet = Collections.unmodifiableSet(new HashSet<>(perms));
            cache.put(userId, new CachedEntry(permSet));
            return permSet;
        } catch (SQLException e) {
            return Collections.emptySet();
        }
    }

    public void invalidate(long userId) {
        cache.remove(userId);
    }

    public void invalidateAll() {
        cache.clear();
    }

    private static final class CachedEntry {
        final Set<String> permissions;
        final long createdAt;

        CachedEntry(Set<String> permissions) {
            this.permissions = permissions;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > TTL_MS;
        }
    }
}
