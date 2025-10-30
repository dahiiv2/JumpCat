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
        try {
            boolean sw = com.jumpcat.core.game.skywars.SkyWarsController.CURRENT != null && com.jumpcat.core.game.skywars.SkyWarsController.CURRENT.isRunning();
            boolean uhc = com.jumpcat.core.game.uhc.UhcMeetupController.CURRENT != null && com.jumpcat.core.game.uhc.UhcMeetupController.CURRENT.isRunning();
            boolean bb = com.jumpcat.core.game.battlebox.BattleBoxController.CURRENT != null && (com.jumpcat.core.game.battlebox.BattleBoxController.CURRENT.isRunning() || com.jumpcat.core.game.battlebox.BattleBoxController.CURRENT.isSeriesRunning());
            boolean tntr = com.jumpcat.core.game.tntrun.TntRunController.CURRENT != null && com.jumpcat.core.game.tntrun.TntRunController.CURRENT.isRunning();
            boolean gameRunning = sw || uhc || bb || tntr;
            if (gameRunning) {
                try { e.getPlayer().getInventory().clear(); e.getPlayer().getInventory().setArmorContents(null); } catch (Throwable ignored) {}
                e.getPlayer().setGameMode(org.bukkit.GameMode.SPECTATOR);
                try { e.getPlayer().sendMessage(org.bukkit.ChatColor.YELLOW + "Game in progress. You are spectating."); } catch (Throwable ignored) {}
                return;
            }
            // No game running: reset and send to lobby spawn
            org.bukkit.entity.Player p = e.getPlayer();
            try { p.getInventory().clear(); p.getInventory().setArmorContents(null); } catch (Throwable ignored) {}
            try { p.getActivePotionEffects().forEach(ef -> p.removePotionEffect(ef.getType())); } catch (Throwable ignored) {}
            try { p.setHealth(20.0); p.setFoodLevel(20); p.setSaturation(20); p.setLevel(0); p.setExp(0f); p.setTotalExperience(0); } catch (Throwable ignored) {}
            p.setGameMode(org.bukkit.GameMode.ADVENTURE);
            org.bukkit.Location lobby = null;
            try { lobby = plugin.getLobbyManager().getLobbySpawn(); } catch (Throwable ignored) {}
            if (lobby == null) {
                try { lobby = org.bukkit.Bukkit.getWorlds().get(0).getSpawnLocation(); } catch (Throwable ignored) {}
            }
            if (lobby != null) {
                try { lobby.getWorld().getChunkAt(lobby).load(true); } catch (Throwable ignored) {}
                try { p.teleport(lobby); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // No explicit cleanup needed with per-player scoreboards
    }
}
