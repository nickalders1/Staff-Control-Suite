package com.staffcontrol.agent.listeners;

import com.staffcontrol.agent.StaffControlAgent;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class MuteListener implements Listener {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneId.of("UTC"));

    private final StaffControlAgent plugin;

    public MuteListener(StaffControlAgent plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncChatEvent event) {
        String name = event.getPlayer().getName();
        if (!plugin.getMuteManager().isMuted(name)) return;

        event.setCancelled(true);

        long expiresAt = plugin.getMuteManager().getMuteExpiry(name);
        Component msg;
        if (expiresAt > 0) {
            String dateStr = DATE_FMT.format(Instant.ofEpochMilli(expiresAt));
            msg = Component.text("You are muted until " + dateStr + ".", NamedTextColor.RED);
        } else {
            msg = Component.text("You are permanently muted.", NamedTextColor.RED);
        }
        event.getPlayer().sendMessage(msg);
    }
}
