package com.jumpcat.core.listeners;

import com.jumpcat.core.game.battlebox.BattleBoxController;
import com.jumpcat.core.scoreboard.SidebarManager;
import com.jumpcat.core.slots.SlotsManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import com.destroystokyo.paper.event.server.PaperServerListPingEvent;

public class MotdListener implements Listener {
    private final Plugin plugin;
    private final BattleBoxController battleBox;
    private final SidebarManager sidebar;
    private final SlotsManager slots;

    public MotdListener(Plugin plugin, BattleBoxController battleBox, SlotsManager slots, SidebarManager sidebar) {
        this.plugin = plugin;
        this.battleBox = battleBox;
        this.slots = slots;
        this.sidebar = sidebar;
    }

    @EventHandler
    public void onPing(PaperServerListPingEvent e) {
        // Line 1: brand
        String line1 = ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Jump" + ChatColor.AQUA + ChatColor.BOLD + "Cat" + ChatColor.RESET;
        // Line 2: dynamic status
        int online = Bukkit.getOnlinePlayers().size();
        int configured = (slots != null && slots.getCap() > 0) ? slots.getCap() : -1;
        int max = configured > 0 ? configured : e.getMaxPlayers();
        if (configured > 0) e.setMaxPlayers(configured);
        String status;
        if (sidebar != null && sidebar.isGameRunning()) {
            status = ChatColor.GOLD + sidebar.getCurrentGameName() + ChatColor.GRAY + " | Round " + ChatColor.WHITE + sidebar.getCurrentRoundLabel();
        } else if (battleBox != null) {
            int arenas = battleBox.getActiveRuntimes() != null ? battleBox.getActiveRuntimes().size() : 0;
            boolean running = battleBox.isRunning();
            boolean series = battleBox.isSeriesRunning();
            if (series) status = ChatColor.GOLD + "Battle Box series live: " + ChatColor.WHITE + arenas + ChatColor.GRAY + " arena(s)";
            else if (running) status = ChatColor.GOLD + "Battle Box live" + ChatColor.GRAY + " (single)";
            else status = ChatColor.GRAY + "Battle Box: " + ChatColor.WHITE + "idle";
        } else {
            status = ChatColor.GRAY + "Games: " + ChatColor.WHITE + "idle";
        }
        String players = ChatColor.GRAY + " | Players: " + ChatColor.WHITE + online + "/" + max;
        String line2 = status + players;
        e.setMotd(line1 + "\n" + line2);
    }
}
