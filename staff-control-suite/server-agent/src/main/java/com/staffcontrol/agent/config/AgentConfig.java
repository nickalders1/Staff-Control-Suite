package com.staffcontrol.agent.config;

import com.staffcontrol.agent.StaffControlAgent;

/**
 * Loads and exposes all configuration values from config.yml.
 */
public class AgentConfig {

    private final String serverId;
    private final String serverName;
    private final String serverType;
    private final String proxyHost;
    private final int proxyAgentPort;
    private final String agentToken;
    private final int reconnectDelaySeconds;
    private final int heartbeatIntervalSeconds;

    public AgentConfig(StaffControlAgent plugin) {
        serverId = plugin.getConfig().getString("serverId", "survival");
        serverName = plugin.getConfig().getString("serverName", "Survival");
        serverType = plugin.getConfig().getString("serverType", "survival");
        proxyHost = plugin.getConfig().getString("proxyHost", "localhost");
        proxyAgentPort = plugin.getConfig().getInt("proxyAgentPort", 8081);
        agentToken = plugin.getConfig().getString("agentToken", "CHANGE_ME_AGENT");
        reconnectDelaySeconds = plugin.getConfig().getInt("reconnectDelaySeconds", 5);
        heartbeatIntervalSeconds = plugin.getConfig().getInt("heartbeatIntervalSeconds", 10);
    }

    public String getServerId() {
        return serverId;
    }

    public String getServerName() {
        return serverName;
    }

    public String getServerType() {
        return serverType;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public int getProxyAgentPort() {
        return proxyAgentPort;
    }

    public String getAgentToken() {
        return agentToken;
    }

    public int getReconnectDelaySeconds() {
        return reconnectDelaySeconds;
    }

    public int getHeartbeatIntervalSeconds() {
        return heartbeatIntervalSeconds;
    }
}
