package com.jumpcat.core.listeners;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

public class KillFeedbackListener implements Listener {
    private final Plugin plugin;

    public KillFeedbackListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer != null) {
            // 1s on-kill title with quick fade out, plus a satisfying sound
            String title = ChatColor.RED + "\u2694 " + ChatColor.WHITE + victim.getName();
            try {
                killer.sendTitle(title, "", 0, 20, 10);
            } catch (Throwable ignored) { }
            try {
                killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
            } catch (Throwable ignored) { }
        }
        // Global instant respawn for the victim to avoid death screen
        new BukkitRunnable() { @Override public void run() {
            try { victim.spigot().respawn(); } catch (Throwable ignored) { }
        }}.runTask(plugin);
    }
}
