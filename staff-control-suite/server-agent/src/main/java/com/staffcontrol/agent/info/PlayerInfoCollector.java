package com.staffcontrol.agent.info;

import com.google.gson.JsonObject;
import com.staffcontrol.agent.StaffControlAgent;
import com.staffcontrol.agent.config.AgentConfig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Collects per-player information and tracks session timing for join/quit events.
 * Registers as a Bukkit {@link Listener} to receive player lifecycle events.
 */
public class PlayerInfoCollector implements Listener {

    private final AgentConfig config;
    private final StaffControlAgent plugin;

    /**
     * Stores the first time (epoch ms) each UUID was seen on this server.
     */
    private final Map<UUID, Long> firstJoinedCache = new ConcurrentHashMap<>();

    /**
     * Stores the epoch ms when each currently-online player joined this session.
     */
    private final Map<UUID, Long> sessionStartCache = new ConcurrentHashMap<>();

    /**
     * Accumulated playtime in milliseconds for each UUID across all previous sessions.
     */
    private final Map<UUID, Long> playtimeCache = new ConcurrentHashMap<>();

    public PlayerInfoCollector(AgentConfig config, StaffControlAgent plugin) {
        this.config = config;
        this.plugin = plugin;
        // Register this class as an event listener
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // -------------------------------------------------------------------------
    // Bukkit event handlers
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Record first join only if this player has never been seen before
        firstJoinedCache.putIfAbsent(uuid, now);

        // Record start of this session
        sessionStartCache.put(uuid, now);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Accumulate session duration into playtimeCache
        Long sessionStart = sessionStartCache.remove(uuid);
        if (sessionStart != null) {
            long sessionDuration = now - sessionStart;
            playtimeCache.merge(uuid, sessionDuration, Long::sum);
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns a list of {@link JsonObject}s representing every currently online player.
     */
    public List<JsonObject> getOnlinePlayers() {
        List<JsonObject> players = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            players.add(buildPlayerJson(player));
        }
        return players;
    }

    /**
     * Builds a PlayerInfo JSON object for the given player.
     *
     * @param player the online player
     * @return a JsonObject conforming to the PlayerInfo schema
     */
    public JsonObject buildPlayerJson(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Retrieve cached values, defaulting to current time if not yet tracked
        long firstJoined = firstJoinedCache.getOrDefault(uuid, now);
        long lastJoined = sessionStartCache.getOrDefault(uuid, now);

        // Accumulated playtime (previous sessions) + current session so far
        long accumulated = playtimeCache.getOrDefault(uuid, 0L);
        Long sessionStart = sessionStartCache.get(uuid);
        long currentSessionMs = (sessionStart != null) ? (now - sessionStart) : 0L;
        long totalPlaytimeSeconds = (accumulated + currentSessionMs) / 1000L;

        JsonObject obj = new JsonObject();
        obj.addProperty("uuid", uuid.toString());
        obj.addProperty("name", player.getName());
        obj.addProperty("serverId", config.getServerId());
        obj.addProperty("world", player.getWorld().getName());
        obj.addProperty("gamemode", player.getGameMode().name());
        obj.addProperty("health", player.getHealth());
        obj.addProperty("foodLevel", player.getFoodLevel());
        obj.addProperty("ping", player.getPing());
        obj.addProperty("isOnline", true);
        obj.addProperty("firstJoined", firstJoined);
        obj.addProperty("lastJoined", lastJoined);
        obj.addProperty("playtimeSeconds", totalPlaytimeSeconds);
        return obj;
    }
}
