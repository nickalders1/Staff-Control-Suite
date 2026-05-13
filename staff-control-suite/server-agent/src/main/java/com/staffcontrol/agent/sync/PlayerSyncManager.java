package com.staffcontrol.agent.sync;

import com.google.gson.JsonObject;
import com.staffcontrol.agent.StaffControlAgent;
import com.staffcontrol.agent.config.AgentConfig;
import com.staffcontrol.agent.proxy.ProxyClient;
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

public class PlayerSyncManager implements Listener {

    private final AgentConfig config;
    private final StaffControlAgent plugin;
    private ProxyClient proxyClient;

    private final ConcurrentHashMap<UUID, Long> firstJoinedCache  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> sessionStartCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> playtimeCache     = new ConcurrentHashMap<>();

    public PlayerSyncManager(AgentConfig config, StaffControlAgent plugin) {
        this.config = config;
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void setProxyClient(ProxyClient client) {
        this.proxyClient = client;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        firstJoinedCache.putIfAbsent(uuid, now);
        sessionStartCache.put(uuid, now);

        if (proxyClient != null) {
            proxyClient.sendPlayerJoin(buildPlayerJson(player, now));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        Long sessionStart = sessionStartCache.remove(uuid);
        long sessionDurationMs = sessionStart != null ? (now - sessionStart) : 0L;
        playtimeCache.merge(uuid, sessionDurationMs, Long::sum);

        long totalPlaytimeSeconds = playtimeCache.getOrDefault(uuid, 0L) / 1000L;

        if (proxyClient != null) {
            proxyClient.sendPlayerLeave(uuid.toString(), totalPlaytimeSeconds);
        }
    }

    public List<JsonObject> getOnlinePlayers() {
        List<JsonObject> players = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            players.add(buildPlayerJson(player, now));
        }
        return players;
    }

    public JsonObject buildPlayerJson(Player player, long now) {
        UUID uuid = player.getUniqueId();
        long firstJoined  = firstJoinedCache.getOrDefault(uuid, now);
        long sessionStart = sessionStartCache.getOrDefault(uuid, now);
        long accumulated  = playtimeCache.getOrDefault(uuid, 0L);
        long totalPlaytimeSeconds = (accumulated + (now - sessionStart)) / 1000L;

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
        obj.addProperty("lastJoined", sessionStart);
        obj.addProperty("playtimeSeconds", totalPlaytimeSeconds);
        return obj;
    }
}
