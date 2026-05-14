package com.staffcontrol.agent.proxy;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.staffcontrol.agent.StaffControlAgent;
import com.staffcontrol.agent.config.AgentConfig;
import org.bukkit.Bukkit;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class ProxyClient extends WebSocketClient {

    private final AgentConfig config;
    private final StaffControlAgent plugin;
    private final Gson gson;
    private volatile boolean reconnecting;
    private final ScheduledExecutorService reconnectExecutor;
    private final AtomicBoolean registered;

    public ProxyClient(AgentConfig config, StaffControlAgent plugin) throws URISyntaxException {
        super(new URI("ws://" + config.getProxyHost() + ":" + config.getProxyAgentPort()));
        this.config = config;
        this.plugin = plugin;
        this.gson = new Gson();
        this.reconnecting = false;
        this.reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "StaffControlAgent-Reconnect");
            t.setDaemon(true);
            return t;
        });
        this.registered = new AtomicBoolean(false);
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        registered.set(false);
        reconnecting = false;
        plugin.getLogger().info("WebSocket connection opened to proxy. Sending registration...");
        sendRegister();
    }

    @Override
    public void onMessage(String message) {
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            String type = json.has("type") ? json.get("type").getAsString() : "";

            switch (type) {
                case "agent.registered": {
                    boolean success = json.has("success") && json.get("success").getAsBoolean();
                    String msg = json.has("message") ? json.get("message").getAsString() : "";
                    if (success) {
                        registered.set(true);
                        plugin.getLogger().info("Registered with proxy as " + config.getServerId() + ": " + msg);
                        sendHeartbeat();
                        sendPlayersUpdate(plugin.getPlayerSyncManager().getOnlinePlayers());
                    } else {
                        plugin.getLogger().warning("Proxy rejected registration: " + msg);
                    }
                    break;
                }

                case "console.command": {
                    if (json.has("payload")) {
                        JsonObject payload = json.getAsJsonObject("payload");
                        String command = payload.has("command") ? payload.get("command").getAsString() : null;
                        if (command != null && !command.isBlank()) {
                            plugin.getLogger().info("Received remote command from proxy: " + command);
                            final String finalCommand = command;
                            Bukkit.getScheduler().runTask(plugin, () ->
                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand));
                        }
                    }
                    break;
                }

                case "server.info.request": {
                    sendHeartbeat();
                    sendPlayersUpdate(plugin.getPlayerSyncManager().getOnlinePlayers());
                    break;
                }

                case "moderation.mute": {
                    if (json.has("payload")) {
                        JsonObject p = json.getAsJsonObject("payload");
                        String playerName = p.has("playerName") ? p.get("playerName").getAsString() : null;
                        long expiresAt    = p.has("expiresAt")  ? p.get("expiresAt").getAsLong()    : 0L;
                        if (playerName != null) plugin.getMuteManager().addMute(playerName, expiresAt);
                    }
                    break;
                }

                case "moderation.unmute": {
                    if (json.has("payload")) {
                        JsonObject p = json.getAsJsonObject("payload");
                        String playerName = p.has("playerName") ? p.get("playerName").getAsString() : null;
                        if (playerName != null) plugin.getMuteManager().removeMute(playerName);
                    }
                    break;
                }

                case "moderation.warn": {
                    if (json.has("payload")) {
                        JsonObject p = json.getAsJsonObject("payload");
                        String playerName = p.has("playerName") ? p.get("playerName").getAsString() : null;
                        String reason     = p.has("reason")     ? p.get("reason").getAsString()     : "";
                        if (playerName != null) {
                            final String name = playerName;
                            final String msg  = reason;
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                org.bukkit.entity.Player target = Bukkit.getPlayerExact(name);
                                if (target != null) {
                                    target.sendMessage(
                                        net.kyori.adventure.text.Component.text(
                                            "⚠ You have been warned by staff. Reason: " + msg,
                                            net.kyori.adventure.text.format.NamedTextColor.YELLOW));
                                }
                            });
                        }
                    }
                    break;
                }

                default:
                    plugin.getLogger().fine("Received unhandled message type from proxy: " + type);
                    break;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error processing proxy message: " + e.getMessage(), e);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        registered.set(false);
        plugin.getLogger().warning("Disconnected from proxy: " + reason
                + " (code=" + code + ", remote=" + remote + ")");
        scheduleReconnect();
    }

    @Override
    public void onError(Exception ex) {
        plugin.getLogger().warning("WebSocket error: " + ex.getMessage());
    }

    // ---- Outbound senders ----

    public void sendRegister() {
        JsonObject root = new JsonObject();
        root.addProperty("type", "agent.register");
        root.addProperty("agentToken", config.getAgentToken());

        JsonObject payload = new JsonObject();
        payload.addProperty("serverId", config.getServerId());
        payload.addProperty("serverName", config.getServerName());
        payload.addProperty("serverType", config.getServerType());
        payload.addProperty("host", config.getProxyHost());
        payload.addProperty("port", Bukkit.getPort());
        payload.addProperty("version", Bukkit.getMinecraftVersion());

        root.add("payload", payload);
        send(root);
    }

    public void sendHeartbeat() {
        if (!isOpen()) return;
        try {
            JsonObject root = new JsonObject();
            root.addProperty("type", "agent.heartbeat");
            root.addProperty("serverId", config.getServerId());
            root.add("payload", plugin.getServerInfoCollector().buildHeartbeatPayload());
            send(root);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send heartbeat: " + e.getMessage());
        }
    }

    public void sendConsoleOutput(String line) {
        if (!registered.get() || !isOpen()) return;
        try {
            JsonObject root = new JsonObject();
            root.addProperty("type", "console.output");
            root.addProperty("serverId", config.getServerId());

            JsonObject payload = new JsonObject();
            payload.addProperty("line", line);
            payload.addProperty("timestamp", System.currentTimeMillis());

            root.add("payload", payload);
            send(root);
        } catch (Exception ignored) {}
    }

    public void sendPlayersUpdate(List<JsonObject> players) {
        if (!registered.get() || !isOpen()) return;
        try {
            JsonObject root = new JsonObject();
            root.addProperty("type", "players.update");
            root.addProperty("serverId", config.getServerId());

            JsonArray arr = new JsonArray();
            for (JsonObject p : players) arr.add(p);

            JsonObject payload = new JsonObject();
            payload.add("players", arr);
            root.add("payload", payload);
            send(root);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send players update: " + e.getMessage());
        }
    }

    public void sendPlayerJoin(JsonObject playerData) {
        if (!registered.get() || !isOpen()) return;
        try {
            JsonObject root = new JsonObject();
            root.addProperty("type", "agent.player.join");
            root.addProperty("serverId", config.getServerId());
            root.add("payload", playerData);
            send(root);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send player join: " + e.getMessage());
        }
    }

    public void sendPlayerLeave(String uuid, long playtimeSeconds) {
        if (!registered.get() || !isOpen()) return;
        try {
            JsonObject root = new JsonObject();
            root.addProperty("type", "agent.player.leave");
            root.addProperty("serverId", config.getServerId());

            JsonObject payload = new JsonObject();
            payload.addProperty("uuid", uuid);
            payload.addProperty("playtimeSeconds", playtimeSeconds);

            root.add("payload", payload);
            send(root);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send player leave: " + e.getMessage());
        }
    }

    // ---- Reconnect ----

    private void scheduleReconnect() {
        if (reconnecting) return;
        reconnecting = true;
        int delay = config.getReconnectDelaySeconds();
        plugin.getLogger().info("Scheduling reconnect to proxy in " + delay + " seconds...");
        reconnectExecutor.schedule(() -> {
            try {
                plugin.getLogger().info("Attempting to reconnect to proxy...");
                reconnect();
            } catch (Exception e) {
                plugin.getLogger().warning("Reconnect attempt failed: " + e.getMessage());
                reconnecting = false;
                scheduleReconnect();
            }
        }, delay, TimeUnit.SECONDS);
    }

    public void disconnect() {
        reconnecting = true;
        registered.set(false);
        try {
            if (isOpen()) close();
        } catch (Exception ignored) {}
        reconnectExecutor.shutdownNow();
    }

    private void send(JsonObject json) {
        try {
            send(gson.toJson(json));
        } catch (Exception ignored) {}
    }

    public boolean isRegistered() { return registered.get(); }
}
