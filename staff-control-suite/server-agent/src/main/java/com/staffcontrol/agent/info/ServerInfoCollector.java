package com.staffcontrol.agent.info;

import com.google.gson.JsonObject;
import com.staffcontrol.agent.StaffControlAgent;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Collects various server metrics from the Paper API and exposes them
 * for use in heartbeat messages.
 */
public class ServerInfoCollector {

    private final StaffControlAgent plugin;

    public ServerInfoCollector(StaffControlAgent plugin) {
        this.plugin = plugin;
    }

    /**
     * Returns the 1-minute average TPS from Paper's Server.getTPS().
     * Falls back to 20.0 if the array is unavailable.
     */
    public double getTPS() {
        try {
            double[] tpsArray = Bukkit.getServer().getTPS();
            return (tpsArray != null && tpsArray.length > 0) ? tpsArray[0] : 20.0;
        } catch (Exception e) {
            return 20.0;
        }
    }

    /**
     * Returns the average tick time in milliseconds from Paper's Server.getAverageTickTime().
     * Falls back to 0.0 if not available.
     */
    public double getMSPT() {
        try {
            return Bukkit.getServer().getAverageTickTime();
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * Returns the number of currently online players.
     */
    public int getOnlinePlayers() {
        return Bukkit.getOnlinePlayers().size();
    }

    /**
     * Returns the server's configured maximum player count.
     */
    public int getMaxPlayers() {
        return Bukkit.getMaxPlayers();
    }

    /**
     * Returns the full Minecraft version string (e.g. "git-Paper-196 (MC: 1.20.4)").
     */
    public String getMinecraftVersion() {
        return Bukkit.getVersion();
    }

    /**
     * Returns the Bukkit API version string (e.g. "1.20.4-R0.1-SNAPSHOT").
     */
    public String getPaperVersion() {
        return Bukkit.getBukkitVersion();
    }

    /**
     * Returns the number of plugins currently loaded on the server.
     */
    public int getPluginCount() {
        return Bukkit.getPluginManager().getPlugins().length;
    }

    /**
     * Returns the total number of loaded chunks across all worlds.
     */
    public int getLoadedChunks() {
        int total = 0;
        for (World world : Bukkit.getWorlds()) {
            total += world.getLoadedChunks().length;
        }
        return total;
    }

    /**
     * Returns a list of all loaded world names.
     */
    public List<String> getWorldNames() {
        return Bukkit.getWorlds()
                .stream()
                .map(World::getName)
                .collect(Collectors.toList());
    }

    /**
     * Builds the heartbeat payload JSON object.
     *
     * @return JsonObject with tps, mspt, onlinePlayers, maxPlayers
     */
    public JsonObject buildHeartbeatPayload() {
        JsonObject payload = new JsonObject();
        payload.addProperty("tps", getTPS());
        payload.addProperty("mspt", getMSPT());
        payload.addProperty("onlinePlayers", getOnlinePlayers());
        payload.addProperty("maxPlayers", getMaxPlayers());
        return payload;
    }
}
