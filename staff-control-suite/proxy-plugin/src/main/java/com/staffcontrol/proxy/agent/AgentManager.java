package com.staffcontrol.proxy.agent;

import com.google.gson.JsonObject;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AgentManager {

    private final ConcurrentHashMap<String, AgentConnection> connections = new ConcurrentHashMap<>();

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
            AgentConnection agent = opt.get();
            JsonObject msg = new JsonObject();
            msg.addProperty("type", "console.command");
            JsonObject payload = new JsonObject();
            payload.addProperty("command", command);
            msg.add("payload", payload);
            agent.send(msg);
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
