package com.jumpcat.core.teams;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Optional;

public class TeamDeathListener implements Listener {
    private final TeamManager teams;

    public TeamDeathListener(TeamManager teams) {
        this.teams = teams;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();

        String victimTeamKey = teams.getPlayerTeamKey(victim).orElse("");
        String victimLabel = victimTeamKey.isEmpty() ? "" : ("" + teams.getTeamColor(victimTeamKey) + ChatColor.BOLD + teams.getTeamLabel(victimTeamKey) + ChatColor.RESET + ChatColor.WHITE + " ");

        String victimName = ChatColor.WHITE + victim.getName();

        if (killer != null) {
            String killerTeamKey = teams.getPlayerTeamKey(killer).orElse("");
            String killerLabel = killerTeamKey.isEmpty() ? "" : ("" + teams.getTeamColor(killerTeamKey) + ChatColor.BOLD + teams.getTeamLabel(killerTeamKey) + ChatColor.RESET + ChatColor.WHITE + " ");
            String killerName = ChatColor.WHITE + killer.getName();
            e.setDeathMessage(victimLabel + victimName + ChatColor.GRAY + " » " + ChatColor.RED + "killed by" + ChatColor.GRAY + " » " + killerLabel + killerName);
        } else {
            e.setDeathMessage(victimLabel + victimName + ChatColor.GRAY + " » " + ChatColor.RED + "died");
        }
    }
}
