package com.staffcontrol.proxy.agent;

import com.google.gson.JsonObject;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class AgentManager {

    private static final long HEARTBEAT_TIMEOUT_MS = 30_000L;
    private static final long MONITOR_INTERVAL_S = 15L;

    private final ConcurrentHashMap<String, AgentConnection> connections = new ConcurrentHashMap<>();
    private ScheduledExecutorService monitorExecutor;
    private Consumer<AgentConnection> timeoutHandler;
    private Logger logger;

    public void setTimeoutHandler(Consumer<AgentConnection> handler) {
        this.timeoutHandler = handler;
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    public void startTimeoutMonitor() {
        monitorExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "StaffControl-AgentMonitor");
            t.setDaemon(true);
            return t;
        });
        monitorExecutor.scheduleAtFixedRate(this::checkHeartbeatTimeouts,
                MONITOR_INTERVAL_S, MONITOR_INTERVAL_S, TimeUnit.SECONDS);
    }

    public void shutdown() {
        if (monitorExecutor != null) {
            monitorExecutor.shutdownNow();
        }
    }

    private void checkHeartbeatTimeouts() {
        for (AgentConnection agent : connections.values()) {
            if (agent.isRegistered() && agent.isHeartbeatTimedOut(HEARTBEAT_TIMEOUT_MS)) {
                String serverId = agent.getServerId();
                if (logger != null) {
                    logger.warning("[AgentManager] Heartbeat timeout for agent: " + serverId
                            + " (last heartbeat " + ((System.currentTimeMillis() - agent.getLastHeartbeatAt()) / 1000) + "s ago)");
                }
                connections.remove(serverId);
                if (timeoutHandler != null) {
                    try {
                        timeoutHandler.accept(agent);
                    } catch (Exception e) {
                        if (logger != null) logger.warning("[AgentManager] Error in timeout handler: " + e.getMessage());
                    }
                }
            }
        }
    }

    public void registerAgent(String serverId, AgentConnection conn) {
        connections.put(serverId, conn);
    }

    public void removeAgent(String serverId) {
        connections.remove(serverId);
    }

    public Optional<AgentConnection> getAgent(String serverId) {
        return Optional.ofNullable(connections.get(serverId));
    }

    public Collection<AgentConnection> getAllAgents() {
        return Collections.unmodifiableCollection(connections.values());
    }

    public boolean sendCommand(String serverId, String command) {
        Optional<AgentConnection> opt = getAgent(serverId);
        if (opt.isPresent() && opt.get().isConnected()) {
            JsonObject msg = new JsonObject();
            msg.addProperty("type", "console.command");
            JsonObject payload = new JsonObject();
            payload.addProperty("command", command);
            msg.add("payload", payload);
            opt.get().send(msg);
            return true;
        }
        return false;
    }

    public boolean isAgentOnline(String serverId) {
        Optional<AgentConnection> opt = getAgent(serverId);
        return opt.isPresent() && opt.get().isConnected() && opt.get().isRegistered();
    }

    public Set<String> getOnlineServerIds() {
        Set<String> online = new HashSet<>();
        for (Map.Entry<String, AgentConnection> entry : connections.entrySet()) {
            if (entry.getValue().isConnected() && entry.getValue().isRegistered()) {
                online.add(entry.getKey());
            }
        }
        return online;
    }
}
