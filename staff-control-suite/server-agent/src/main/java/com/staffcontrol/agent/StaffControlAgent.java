package com.staffcontrol.agent;

import com.staffcontrol.agent.config.AgentConfig;
import com.staffcontrol.agent.console.ConsoleCapture;
import com.staffcontrol.agent.info.ServerInfoCollector;
import com.staffcontrol.agent.proxy.ProxyClient;
import com.staffcontrol.agent.sync.PlayerSyncManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class StaffControlAgent extends JavaPlugin {

    private AgentConfig agentConfig;
    private ProxyClient proxyClient;
    private ConsoleCapture consoleCapture;
    private ServerInfoCollector serverInfoCollector;
    private PlayerSyncManager playerSyncManager;
    private BukkitTask heartbeatTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        agentConfig = new AgentConfig(this);
        serverInfoCollector = new ServerInfoCollector(this);

        playerSyncManager = new PlayerSyncManager(agentConfig, this);

        try {
            proxyClient = new ProxyClient(agentConfig, this);
            playerSyncManager.setProxyClient(proxyClient);

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

        long heartbeatTicks = (long) agentConfig.getHeartbeatIntervalSeconds() * 20L;
        heartbeatTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (proxyClient != null) {
                proxyClient.sendHeartbeat();
                proxyClient.sendPlayersUpdate(playerSyncManager.getOnlinePlayers());
            }
        }, heartbeatTicks, heartbeatTicks);

        consoleCapture = new ConsoleCapture(proxyClient);
        consoleCapture.startCapture();

        getLogger().info("StaffControlAgent v1.1.0 enabled, connecting to proxy at "
                + agentConfig.getProxyHost() + ":" + agentConfig.getProxyAgentPort());
    }

    @Override
    public void onDisable() {
        if (consoleCapture != null) consoleCapture.stopCapture();
        if (heartbeatTask != null) { heartbeatTask.cancel(); heartbeatTask = null; }
        if (proxyClient != null) proxyClient.disconnect();
        getLogger().info("StaffControlAgent disabled.");
    }

    public AgentConfig getAgentConfig()              { return agentConfig; }
    public ProxyClient getProxyClient()              { return proxyClient; }
    public ConsoleCapture getConsoleCapture()        { return consoleCapture; }
    public ServerInfoCollector getServerInfoCollector() { return serverInfoCollector; }
    public PlayerSyncManager getPlayerSyncManager() { return playerSyncManager; }
    public BukkitTask getHeartbeatTask()             { return heartbeatTask; }
}
