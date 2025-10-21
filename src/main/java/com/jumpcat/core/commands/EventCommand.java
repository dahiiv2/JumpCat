package com.jumpcat.core.commands;

import com.jumpcat.core.JumpCatPlugin;
import com.jumpcat.core.lobby.LobbyManager;
import com.jumpcat.core.points.PointsService;
import com.jumpcat.core.scoreboard.SidebarManager;
import com.jumpcat.core.teams.TeamManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class EventCommand implements CommandExecutor {
    private final JumpCatPlugin plugin;
    private final TeamManager teams;
    private final PointsService points;
    private final SidebarManager sidebar;
    private final LobbyManager lobby;

    public EventCommand(JumpCatPlugin plugin, TeamManager teams, PointsService points, SidebarManager sidebar, LobbyManager lobby) {
        this.plugin = plugin;
        this.teams = teams;
        this.points = points;
        this.sidebar = sidebar;
        this.lobby = lobby;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("end")) {
            sender.sendMessage(ChatColor.YELLOW + "/event end" + ChatColor.GRAY + " – end the event, congratulate winners, then clear points/KD/teams after 30s");
            return true;
        }
        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }
        // Determine winning team by total points
        String winningKey = null;
        int winningPoints = -1;
        for (String key : teams.listTeamKeys()) {
            int sum = 0;
            for (String name : teams.getTeamMembers(key)) {
                try {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(name);
                    if (op != null && op.getUniqueId() != null) sum += points.getPoints(op.getUniqueId());
                } catch (Throwable ignored) {}
            }
            if (sum > winningPoints) { winningPoints = sum; winningKey = key; }
        }
        // Teleport everyone to lobby spawn and congratulate
        Location spawn = lobby.getLobbySpawn();
        if (spawn != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                try { p.teleport(spawn); } catch (Throwable ignored) {}
                try { p.setGameMode(org.bukkit.GameMode.ADVENTURE); } catch (Throwable ignored) {}
            }
        }
        // Broadcast announcement + titles
        String headline;
        if (winningKey != null) {
            ChatColor c = teams.getTeamColor(winningKey);
            String labelTxt = teams.getTeamLabel(winningKey);
            headline = ChatColor.WHITE + "Event Winner: " + c + ChatColor.BOLD.toString() + labelTxt + ChatColor.RESET + ChatColor.WHITE + " (" + winningPoints + " pts)";
        } else {
            headline = ChatColor.WHITE + "Event Ended";
        }
        Bukkit.broadcastMessage(ChatColor.GOLD + "====================");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "Event concluded!");
        Bukkit.broadcastMessage(headline);
        Bukkit.broadcastMessage(ChatColor.GRAY + "Clearing points, K/D, and teams in 30 seconds...");
        Bukkit.broadcastMessage(ChatColor.GOLD + "====================");
        for (Player p : Bukkit.getOnlinePlayers()) {
            try { p.sendTitle(ChatColor.GOLD + "Event Concluded", winningKey != null ? ChatColor.WHITE + "Winners: " + teams.getTeamColor(winningKey) + teams.getTeamLabel(winningKey) : "", 10, 60, 20); } catch (Throwable ignored) {}
            try { p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.1f); } catch (Throwable ignored) {}
        }
        // Schedule clearing after 30s
        new BukkitRunnable(){ @Override public void run(){
            try { points.clearAll(); } catch (Throwable ignored) {}
            try { sidebar.resetStats(); sidebar.updateAll(); } catch (Throwable ignored) {}
            try { teams.clearAll(); } catch (Throwable ignored) {}
            Bukkit.broadcastMessage(ChatColor.GRAY + "Event data cleared.");
        } }.runTaskLater(plugin, 30L * 20L);
        return true;
    }
}
