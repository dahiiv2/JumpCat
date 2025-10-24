package com.jumpcat.core.listeners;

import com.jumpcat.core.JumpCatPlugin;
import com.jumpcat.core.scoreboard.SidebarManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerJoinQuitListener implements Listener {
    private final SidebarManager sidebar;
    private final JumpCatPlugin plugin;

    public PlayerJoinQuitListener(SidebarManager sidebar, JumpCatPlugin plugin) {
        this.sidebar = sidebar;
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        sidebar.show(e.getPlayer());
        // If any game is running, joiners should be spectators regardless of world
        try {
            boolean gameRunning =
                    (com.jumpcat.core.game.skywars.SkyWarsController.CURRENT != null)
                    || (com.jumpcat.core.game.uhc.UhcMeetupController.CURRENT != null)
                    || (com.jumpcat.core.game.battlebox.BattleBoxController.CURRENT != null
                            && (com.jumpcat.core.game.battlebox.BattleBoxController.CURRENT.isRunning()
                                || com.jumpcat.core.game.battlebox.BattleBoxController.CURRENT.isSeriesRunning()))
                    || (com.jumpcat.core.game.tntrun.TntRunController.CURRENT != null
                            && com.jumpcat.core.game.tntrun.TntRunController.CURRENT.isRunning());
            if (gameRunning) {
                try { e.getPlayer().getInventory().clear(); e.getPlayer().getInventory().setArmorContents(null); } catch (Throwable ignored) {}
                e.getPlayer().setGameMode(org.bukkit.GameMode.SPECTATOR);
                try { e.getPlayer().sendMessage(org.bukkit.ChatColor.YELLOW + "Game in progress. You are spectating."); } catch (Throwable ignored) {}
                return;
            }
            // Fallback: if they logged directly into a game world by name
            String wn = e.getPlayer().getWorld() != null ? e.getPlayer().getWorld().getName() : "";
            if (wn.startsWith("uhc_meetup_r") || wn.startsWith("skywars_r") || wn.startsWith("tntrun_r") || wn.equalsIgnoreCase("battle_box")) {
                e.getPlayer().setGameMode(org.bukkit.GameMode.SPECTATOR);
            }
            // Battle Box series or match: if player logged into battle_box, keep them spectator
            // (Runtime flags live in controller/listeners; world name check above handles teleport-in scenarios.)
        } catch (Throwable ignored) {}
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // No explicit cleanup needed with per-player scoreboards
    }
}
