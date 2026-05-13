package com.staffcontrol.agent;

import com.staffcontrol.agent.config.AgentConfig;
import com.staffcontrol.agent.console.ConsoleCapture;
import com.staffcontrol.agent.info.PlayerInfoCollector;
import com.staffcontrol.agent.info.ServerInfoCollector;
import com.staffcontrol.agent.proxy.ProxyClient;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Main plugin class for StaffControlAgent.
 * Connects to the Velocity proxy's AgentWebSocket server, streams console output,
 * sends heartbeats with TPS/player data, and executes commands received from the proxy.
 */
public class StaffControlAgent extends JavaPlugin {

    private AgentConfig agentConfig;
    private ProxyClient proxyClient;
    private ConsoleCapture consoleCapture;
    private ServerInfoCollector serverInfoCollector;
    private PlayerInfoCollector playerInfoCollector;
    private BukkitTask heartbeatTask;

    @Override
    public void onEnable() {
        // 1. Save default config if not present
        saveDefaultConfig();

        // 2. Load agent configuration
        agentConfig = new AgentConfig(this);

        // 3. Create info collectors
        serverInfoCollector = new ServerInfoCollector(this);
        playerInfoCollector = new PlayerInfoCollector(agentConfig, this);

        // 4. Create proxy client and connect asynchronously
        try {
            proxyClient = new ProxyClient(agentConfig, this);
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                try {
                    proxyClient.connect();
                } catch (Exception e) {
                    getLogger().warning("Failed to connect to proxy: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            getLogger().severe("Failed to create proxy client: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 5. Start heartbeat scheduler
        long heartbeatTicks = (long) agentConfig.getHeartbeatIntervalSeconds() * 20L;
        heartbeatTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (proxyClient != null) {
                proxyClient.sendHeartbeat();
                proxyClient.sendPlayersUpdate(playerInfoCollector.getOnlinePlayers());
            }
        }, heartbeatTicks, heartbeatTicks);

        // 6. Start console capture
        consoleCapture = new ConsoleCapture(proxyClient);
        consoleCapture.startCapture();

        // 7. Log startup
        getLogger().info("StaffControlAgent enabled, connecting to proxy at "
                + agentConfig.getProxyHost() + ":" + agentConfig.getProxyAgentPort());
    }

    @Override
    public void onDisable() {
        // 1. Stop console capture
        if (consoleCapture != null) {
            consoleCapture.stopCapture();
        }

        // 2. Cancel heartbeat task
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
            heartbeatTask = null;
        }

        // 3. Disconnect from proxy
        if (proxyClient != null) {
            proxyClient.disconnect();
        }

        getLogger().info("StaffControlAgent disabled.");
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public AgentConfig getAgentConfig() {
        return agentConfig;
    }

    public ProxyClient getProxyClient() {
        return proxyClient;
    }

    public ConsoleCapture getConsoleCapture() {
        return consoleCapture;
    }

    public ServerInfoCollector getServerInfoCollector() {
        return serverInfoCollector;
    }

    public PlayerInfoCollector getPlayerInfoCollector() {
        return playerInfoCollector;
    }

    public BukkitTask getHeartbeatTask() {
        return heartbeatTask;
    }
}
