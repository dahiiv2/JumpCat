package com.jumpcat.core.teams;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class TeamChatListener implements Listener {
    private final TeamManager teams;

    public TeamChatListener(TeamManager teams) {
        this.teams = teams;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        boolean tc = teams.isTeamChat(p);

        var teamKeyOpt = teams.getPlayerTeamKey(p);
        if (tc) {
            // If toggled but not in a team, auto-disable and fall back to normal formatting
            if (teamKeyOpt.isEmpty()) {
                teams.toggleTeamChat(p); // turn off
            } else {
                // Team chat: send only to teammates, with green [TEAM] prefix and white message
                String playerName = ChatColor.WHITE + p.getName();
                String msg = ChatColor.WHITE + e.getMessage();
                String formatted = ChatColor.GREEN + "[TEAM] " + playerName + ChatColor.GRAY + " » " + msg;
                e.setCancelled(true);
                // deliver to team members only
                String key = teamKeyOpt.get();
                for (String entry : teams.getTeamMembers(key)) {
                    Player target = p.getServer().getPlayerExact(entry);
                    if (target != null) target.sendMessage(formatted);
                }
                return;
            }
        }

        // Normal chat formatting: TEAM player » message
        // TEAM label colored & bold, player white, message gray (non-op) or white (op)
        String label = teamKeyOpt.map(teams::getTeamLabel).orElse("");
        ChatColor color = teamKeyOpt.map(teams::getTeamColor).orElse(ChatColor.GRAY);
        String teamPrefix = label.isEmpty() ? "" : ("" + color + ChatColor.BOLD + label + ChatColor.RESET + ChatColor.WHITE + " ");
        ChatColor msgColor = p.isOp() ? ChatColor.WHITE : ChatColor.GRAY;
        String newFormat = teamPrefix + ChatColor.WHITE + p.getName() + ChatColor.GRAY + " » " + msgColor + "%2$s";
        e.setFormat(newFormat);
    }
}
