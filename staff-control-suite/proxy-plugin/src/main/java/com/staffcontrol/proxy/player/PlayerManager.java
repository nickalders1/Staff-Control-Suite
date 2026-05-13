package com.staffcontrol.proxy.player;

import com.staffcontrol.proxy.database.DatabaseManager;
import com.staffcontrol.proxy.model.PlayerInfo;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class PlayerManager {

    private final ConcurrentHashMap<String, PlayerInfo> onlinePlayers = new ConcurrentHashMap<>();
    private final DatabaseManager database;
    private final Logger logger;

    public PlayerManager(DatabaseManager database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    public PlayerInfo getOnlinePlayer(String uuid) {
        return onlinePlayers.get(uuid);
    }

    public boolean isOnline(String uuid) {
        return onlinePlayers.containsKey(uuid);
    }

    public Collection<PlayerInfo> getAllOnline() {
        return Collections.unmodifiableCollection(onlinePlayers.values());
    }

    public List<PlayerInfo> getOnlineByServer(String serverId) {
        List<PlayerInfo> result = new ArrayList<>();
        for (PlayerInfo p : onlinePlayers.values()) {
            if (serverId.equals(p.getServerId())) result.add(p);
        }
        return result;
    }

    public int getNetworkPlayerCount() {
        return onlinePlayers.size();
    }

    /**
     * Called when an agent reports a player joined its server.
     * Returns null if this is a fresh network join, or the previous serverId if it's a server switch.
     */
    public String playerJoined(PlayerInfo player) {
        String previousServer = null;
        PlayerInfo existing = onlinePlayers.get(player.getUuid());
        if (existing != null && !existing.getServerId().equals(player.getServerId())) {
            previousServer = existing.getServerId();
        }
        onlinePlayers.put(player.getUuid(), player);

        try {
            database.upsertPlayerFull(player, System.currentTimeMillis());
        } catch (SQLException e) {
            logger.warning("[PlayerManager] DB error on playerJoined for " + player.getUuid() + ": " + e.getMessage());
        }
        return previousServer;
    }

    /**
     * Called when an agent reports a player left.
     * Ignored if the player has already joined a different server (server-switch race condition).
     */
    public boolean playerLeft(String uuid, String fromServerId, long playtimeSeconds) {
        PlayerInfo current = onlinePlayers.get(uuid);
        if (current != null && !fromServerId.equals(current.getServerId())) {
            return false;
        }
        onlinePlayers.remove(uuid);

        try {
            database.setPlayerOfflineFull(uuid, playtimeSeconds, System.currentTimeMillis());
        } catch (SQLException e) {
            logger.warning("[PlayerManager] DB error on playerLeft for " + uuid + ": " + e.getMessage());
        }
        return true;
    }

    /**
     * Reconciles the full player list for a server from a periodic heartbeat.
     * Players present in the in-memory cache for that server but absent from the reported list
     * are considered gone and returned so callers can broadcast leave events.
     */
    public List<String> reconcileServerPlayers(String serverId, List<PlayerInfo> reportedPlayers) {
        Set<String> reportedUuids = new HashSet<>();
        for (PlayerInfo p : reportedPlayers) {
            reportedUuids.add(p.getUuid());
            onlinePlayers.put(p.getUuid(), p);
        }

        List<String> removedUuids = new ArrayList<>();
        for (Iterator<Map.Entry<String, PlayerInfo>> it = onlinePlayers.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, PlayerInfo> entry = it.next();
            if (serverId.equals(entry.getValue().getServerId()) && !reportedUuids.contains(entry.getKey())) {
                removedUuids.add(entry.getKey());
                it.remove();
            }
        }

        if (!removedUuids.isEmpty()) {
            try {
                database.setAllPlayersOfflineForServer(serverId, System.currentTimeMillis());
                for (PlayerInfo p : reportedPlayers) {
                    database.upsertPlayerFull(p, System.currentTimeMillis());
                }
            } catch (SQLException e) {
                logger.warning("[PlayerManager] DB error reconciling players for " + serverId + ": " + e.getMessage());
            }
        }
        return removedUuids;
    }

    /**
     * Called when an agent disconnects — removes all its players from the online cache.
     */
    public List<String> agentDisconnected(String serverId) {
        List<String> removedUuids = new ArrayList<>();
        for (Iterator<Map.Entry<String, PlayerInfo>> it = onlinePlayers.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, PlayerInfo> entry = it.next();
            if (serverId.equals(entry.getValue().getServerId())) {
                removedUuids.add(entry.getKey());
                it.remove();
            }
        }

        if (!removedUuids.isEmpty()) {
            try {
                database.setAllPlayersOfflineForServer(serverId, System.currentTimeMillis());
            } catch (SQLException e) {
                logger.warning("[PlayerManager] DB error on agentDisconnected for " + serverId + ": " + e.getMessage());
            }
        }
        return removedUuids;
    }
}
