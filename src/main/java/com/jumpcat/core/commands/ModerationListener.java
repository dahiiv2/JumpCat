package com.jumpcat.core.commands;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerLoginEvent;

import java.util.UUID;

public class ModerationListener implements Listener {
    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        ModerationCommand.BanInfo info = ModerationCommand.bans.get(uuid);
        if (info != null) {
            long now = System.currentTimeMillis();
            if (now < info.expiresAt) {
                String remaining = info.expiresAt == Long.MAX_VALUE ? "permanently" : formatDuration(info.expiresAt - now);
                e.disallow(PlayerLoginEvent.Result.KICK_BANNED,
                    ChatColor.RED + "You are banned!\nReason: " + info.reason + "\nExpires: " + remaining);
            } else {
                ModerationCommand.bans.remove(uuid); // Ban expired
            }
        }
    }
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        ModerationCommand.MuteInfo info = ModerationCommand.mutes.get(uuid);
        if (info != null) {
            long now = System.currentTimeMillis();
            if (now < info.expiresAt) {
                String remaining = info.expiresAt == Long.MAX_VALUE ? "permanently" : formatDuration(info.expiresAt - now);
                e.setCancelled(true);
                e.getPlayer().sendMessage(ChatColor.RED + "You are muted!\nReason: " + info.reason + "\nExpires: " + remaining);
            } else {
                ModerationCommand.mutes.remove(uuid); // Mute expired
            }
        }
    }
    private String formatDuration(long millis) {
        long totalSeconds = millis / 1000;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.length() == 0) sb.append(seconds).append("s");
        return sb.toString().trim();
    }
}
