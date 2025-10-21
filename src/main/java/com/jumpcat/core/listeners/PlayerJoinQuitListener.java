package com.jumpcat.core.listeners;

import com.jumpcat.core.scoreboard.SidebarManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerJoinQuitListener implements Listener {
    private final SidebarManager sidebar;

    public PlayerJoinQuitListener(SidebarManager sidebar) {
        this.sidebar = sidebar;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        sidebar.show(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // No explicit cleanup needed with per-player scoreboards
    }
}
