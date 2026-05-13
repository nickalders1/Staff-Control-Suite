package com.staffcontrol.proxy.moderation;

import com.staffcontrol.proxy.database.DatabaseManager;

import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.logging.Logger;

public class ModerationManager {

    private static final DateTimeFormatter BAN_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneId.of("UTC"));

    private final DatabaseManager database;
    private final Logger logger;

    public ModerationManager(DatabaseManager database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    /**
     * Checks whether a player is banned or IP-banned at login time.
     *
     * @return null if allowed to join, or a human-readable denial message if banned.
     */
    public String checkBanOnLogin(String uuid, String name, String ipHash) {
        try {
            // Check UUID/name ban
            Map<String, Object> banRecord = database.getActiveBanRecord(uuid, name);
            if (banRecord != null) {
                return buildBanMessage(banRecord);
            }

            // Check IP ban
            if (ipHash != null && !ipHash.isEmpty()) {
                Map<String, Object> ipBanRecord = database.getActiveIpBanRecord(ipHash);
                if (ipBanRecord != null) {
                    return buildBanMessage(ipBanRecord);
                }
            }
        } catch (SQLException e) {
            logger.warning("[ModerationManager] Error checking ban on login for " + name + ": " + e.getMessage());
        }
        return null;
    }

    private String buildBanMessage(Map<String, Object> record) {
        String reason = getStr(record, "reason");
        long expiresAt = getLong(record, "expires_at");
        String actionType = getStr(record, "action_type");

        StringBuilder msg = new StringBuilder();

        if ("IP_BAN".equals(actionType) || "TEMP_IP_BAN".equals(actionType)) {
            msg.append("You are IP-banned from this server.\n");
        } else {
            msg.append("You are banned from this server.\n");
        }

        msg.append("Reason: ").append(reason != null ? reason : "No reason provided").append("\n");

        if (expiresAt > 0) {
            String dateStr = BAN_DATE_FORMAT.format(Instant.ofEpochMilli(expiresAt));
            msg.append("Expires: ").append(dateStr);
        } else {
            msg.append("Duration: Permanent");
        }

        return msg.toString();
    }

    /**
     * Checks whether a player is currently muted.
     *
     * @return null if not muted, or the mute record map if muted.
     */
    public Map<String, Object> checkMuteStatus(String uuid, String name) {
        try {
            return database.getActiveMuteRecord(uuid, name);
        } catch (SQLException e) {
            logger.warning("[ModerationManager] Error checking mute for " + name + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Marks all expired punishments as inactive.
     *
     * @return number of punishments expired.
     */
    public int expireOldPunishments() {
        try {
            return database.expireOldPunishments();
        } catch (SQLException e) {
            logger.warning("[ModerationManager] Error expiring old punishments: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Creates a new punishment record and returns its new ID.
     */
    public long createPunishment(String targetUuid, String targetName, String targetIpHash,
            String actionType, String reason, long durationSeconds, long expiresAt,
            long createdByUserId, String createdByUsername, String targetServer,
            String evidence) throws SQLException {
        return database.createPunishment(
                targetUuid, targetName, targetIpHash,
                actionType, reason, durationSeconds, expiresAt,
                createdByUserId, createdByUsername, targetServer, evidence);
    }

    /**
     * Revokes a punishment by ID.
     *
     * @return true if successfully revoked, false if not found or already revoked.
     */
    public boolean revokePunishment(long punishmentId, long revokedByUserId,
            String revokedByUsername, String revokeReason) throws SQLException {
        return database.revokePunishment(punishmentId, revokedByUserId, revokedByUsername, revokeReason);
    }

    private String getStr(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : null;
    }

    private long getLong(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val instanceof Number ? ((Number) val).longValue() : 0L;
    }
}
