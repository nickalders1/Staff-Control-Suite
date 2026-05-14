package com.staffcontrol.proxy;

import com.google.inject.Inject;
import com.staffcontrol.proxy.agent.AgentManager;
import com.staffcontrol.proxy.agent.AgentWebSocketServer;
import com.staffcontrol.proxy.api.AppMessageHandler;
import com.staffcontrol.proxy.api.AppWebSocketServer;
import com.staffcontrol.proxy.audit.AuditLogger;
import com.staffcontrol.proxy.auth.AuthManager;
import com.staffcontrol.proxy.config.ProxyConfig;
import com.staffcontrol.proxy.database.DatabaseManager;
import com.staffcontrol.proxy.moderation.ModerationManager;
import com.staffcontrol.proxy.permission.PermissionCache;
import com.staffcontrol.proxy.player.PlayerManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;

import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@Plugin(
    id = "staffcontrolproxy",
    name = "Staff Control Proxy",
    version = "1.1.3",
    description = "Central gateway for Staff Control Suite",
    authors = {"StaffControl"}
)
public class StaffControlProxy {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private ProxyConfig config;
    private DatabaseManager database;
    private AuthManager authManager;
    private AgentManager agentManager;
    private AuditLogger auditLogger;
    private PlayerManager playerManager;
    private PermissionCache permissionCache;
    private ModerationManager moderationManager;
    private AppWebSocketServer appWebSocketServer;
    private AgentWebSocketServer agentWebSocketServer;
    private ScheduledExecutorService expirationScheduler;

    @Inject
    public StaffControlProxy(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info("=== Staff Control Proxy v1.1.3 starting ===");

        config = new ProxyConfig(dataDirectory);
        try {
            config.load();
            logger.info("Configuration loaded. API port=" + config.getApiPort()
                    + ", Agent port=" + config.getAgentPort());
        } catch (Exception e) {
            logger.severe("Failed to load configuration: " + e.getMessage());
            return;
        }

        database = new DatabaseManager(dataDirectory, config);
        try {
            database.initialize();
            logger.info("Database initialised successfully.");
        } catch (Exception e) {
            logger.severe("Failed to initialise database: " + e.getMessage());
            return;
        }

        // Seed default punishment presets (no-op if already seeded)
        try {
            database.seedDefaultPresets();
            logger.info("Punishment presets ready.");
        } catch (Exception e) {
            logger.warning("Failed to seed default presets: " + e.getMessage());
        }

        authManager      = new AuthManager(database, config);
        auditLogger      = new AuditLogger(database);
        playerManager    = new PlayerManager(database, logger);
        permissionCache  = new PermissionCache(database);
        moderationManager = new ModerationManager(database, logger);

        agentManager = new AgentManager();
        agentManager.setLogger(logger);

        appWebSocketServer = new AppWebSocketServer(
                config, database, authManager, agentManager, auditLogger, logger);

        AppMessageHandler messageHandler = new AppMessageHandler(
                database, authManager, agentManager, auditLogger,
                appWebSocketServer, playerManager, permissionCache, config, logger,
                moderationManager, server);
        appWebSocketServer.setMessageHandler(messageHandler);

        agentWebSocketServer = new AgentWebSocketServer(
                config, agentManager, appWebSocketServer, database, playerManager, logger);

        agentManager.setTimeoutHandler(agent -> {
            String serverId = agent.getServerId();
            logger.warning("[StaffControl] Agent timed out: " + serverId);
            auditLogger.logAgentTimeout(serverId);
            agentManager.removeAgent(serverId);
            java.util.List<String> removed = playerManager.agentDisconnected(serverId);
            for (String uuid : removed) {
                appWebSocketServer.broadcastPlayerLeft(uuid, serverId);
            }
            appWebSocketServer.broadcastServerStatus(serverId, false, 0, 0.0, 0.0, System.currentTimeMillis());
        });

        try {
            appWebSocketServer.start();
            logger.info("App WebSocket server started on port " + config.getApiPort());
        } catch (Exception e) {
            logger.severe("Failed to start App WebSocket server: " + e.getMessage());
        }

        try {
            agentWebSocketServer.start();
            logger.info("Agent WebSocket server started on port " + config.getAgentPort());
        } catch (Exception e) {
            logger.severe("Failed to start Agent WebSocket server: " + e.getMessage());
        }

        agentManager.startTimeoutMonitor();
        logger.info("Agent heartbeat monitor started (timeout=30s, check=15s).");

        expirationScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "StaffControl-PunishmentExpiry");
            t.setDaemon(true);
            return t;
        });
        expirationScheduler.scheduleAtFixedRate(() -> {
            int expired = moderationManager.expireOldPunishments();
            if (expired > 0) logger.info("[StaffControl] Expired " + expired + " punishment(s).");
        }, 60, 60, TimeUnit.SECONDS);
        logger.info("Punishment expiration scheduler started (interval=60s).");

        logger.info("=== Staff Control Proxy started successfully ===");
        if (!authManager.ownerExists()) {
            logger.info("No owner account detected. Connect via the app to complete setup.");
        }
    }

    @Subscribe
    public void onPlayerPostLogin(PostLoginEvent event) {
        if (moderationManager == null) return;
        var player = event.getPlayer();
        String uuid   = player.getUniqueId().toString();
        String name   = player.getUsername();
        String ip     = player.getRemoteAddress().getAddress().getHostAddress();
        String ipHash = ModerationManager.hashIp(ip);

        String banMessage = moderationManager.checkBanOnLogin(uuid, name, ipHash);
        if (banMessage != null) {
            player.disconnect(Component.text(banMessage));
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("=== Staff Control Proxy shutting down ===");

        agentManager.shutdown();
        if (expirationScheduler != null) expirationScheduler.shutdownNow();

        if (appWebSocketServer != null) {
            try { appWebSocketServer.stop(1000); } catch (Exception ignored) {}
        }
        if (agentWebSocketServer != null) {
            try { agentWebSocketServer.stop(1000); } catch (Exception ignored) {}
        }
        if (database != null) {
            database.close();
            logger.info("Database connection closed.");
        }

        logger.info("=== Staff Control Proxy shut down ===");
    }

    public ProxyServer getServer()                       { return server; }
    public Logger getLogger()                            { return logger; }
    public Path getDataDirectory()                       { return dataDirectory; }
    public ProxyConfig getConfig()                       { return config; }
    public DatabaseManager getDatabase()                 { return database; }
    public AuthManager getAuthManager()                  { return authManager; }
    public AgentManager getAgentManager()               { return agentManager; }
    public AuditLogger getAuditLogger()                  { return auditLogger; }
    public PlayerManager getPlayerManager()              { return playerManager; }
    public PermissionCache getPermissionCache()          { return permissionCache; }
    public ModerationManager getModerationManager()      { return moderationManager; }
    public AppWebSocketServer getAppWebSocketServer()    { return appWebSocketServer; }
    public AgentWebSocketServer getAgentWebSocketServer(){ return agentWebSocketServer; }
}
