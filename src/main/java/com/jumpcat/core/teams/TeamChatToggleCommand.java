package com.jumpcat.core.teams;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TeamChatToggleCommand implements CommandExecutor {
    private final TeamManager teams;

    public TeamChatToggleCommand(TeamManager teams) {
        this.teams = teams;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        Player p = (Player) sender;
        // If not in a team, force off and inform
        if (teams.getPlayerTeamKey(p).isEmpty()) {
            if (teams.isTeamChat(p)) teams.toggleTeamChat(p); // ensure off
            p.sendMessage(ChatColor.RED + "You are not in a team. Team chat is off.");
            return true;
        }
        boolean enabled = teams.toggleTeamChat(p);
        p.sendMessage(enabled ? ChatColor.GREEN + "Team chat: ON" : ChatColor.YELLOW + "Team chat: OFF");
        return true;
    }
}
