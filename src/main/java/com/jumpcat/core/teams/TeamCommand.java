package com.jumpcat.core.teams;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.jumpcat.core.points.PointsService;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;

public class TeamCommand implements CommandExecutor {
    private final TeamManager teams;
    private final PointsService points;

    public TeamCommand(TeamManager teams) { this(teams, null); }

    public TeamCommand(TeamManager teams, PointsService points) {
        this.teams = teams;
        this.points = points;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "list": {
                sender.sendMessage("");
                java.util.List<String> keys = new java.util.ArrayList<>();
                for (String k : teams.listTeamKeys()) keys.add(k);
                keys.sort((a,b) -> {
                    int pa = 0, pb = 0;
                    if (points != null) {
                        for (String name : teams.getTeamMembers(a)) pa += points.getPoints(Bukkit.getOfflinePlayer(name).getUniqueId());
                        for (String name : teams.getTeamMembers(b)) pb += points.getPoints(Bukkit.getOfflinePlayer(name).getUniqueId());
                    }
                    int cmp = Integer.compare(pb, pa);
                    if (cmp != 0) return cmp;
                    return a.compareToIgnoreCase(b);
                });
                for (String k : keys) {
                    List<String> members = teams.getTeamMembers(k);
                    StringJoiner sj = new StringJoiner(", ");
                    for (String m : members) sj.add(m);
                    ChatColor color = teams.getTeamColor(k);
                    String teamLabel = teams.getTeamLabel(k);
                    int teamPoints = 0;
                    if (points != null) {
                        for (String name : members) {
                            UUID id = Bukkit.getOfflinePlayer(name).getUniqueId();
                            teamPoints += points.getPoints(id);
                        }
                    }
                    sender.sendMessage(color + ChatColor.BOLD.toString() + teamLabel + ChatColor.RESET + ChatColor.WHITE + " (" + members.size() + ")" + ChatColor.GRAY + ": " + ChatColor.WHITE + sj + ChatColor.GRAY + " | " + ChatColor.WHITE + "Points: " + ChatColor.AQUA + teamPoints);
                }
                sender.sendMessage("");
                return true;
            }
            case "join": {
                if (!sender.isOp()) { sender.sendMessage(ChatColor.RED + "OP only."); return true; }
                if (!(sender instanceof Player)) { sender.sendMessage("Players only."); return true; }
                Player p = (Player) sender;
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Specify a team. Try /" + label + " list");
                    return true;
                }
                String key = args[1];
                boolean ok = teams.joinTeam(p, key);
                if (!ok) {
                    sender.sendMessage(ChatColor.RED + "No such team: " + key);
                    return true;
                }
                sender.sendMessage(ChatColor.GREEN + "Joined team " + key + ".");
                return true;
            }
            case "leave": {
                if (!sender.isOp()) { sender.sendMessage(ChatColor.RED + "OP only."); return true; }
                if (!(sender instanceof Player)) { sender.sendMessage("Players only."); return true; }
                Player p = (Player) sender;
                teams.leaveTeam(p);
                sender.sendMessage(ChatColor.GREEN + "Left your team.");
                return true;
            }
            case "add": {
                if (!sender.isOp()) { sender.sendMessage(ChatColor.RED + "OP only."); return true; }
                if (args.length < 3) { sender.sendMessage(ChatColor.YELLOW + "Usage: /"+label+" add <player> <team>"); return true; }
                String playerName = args[1];
                String key = args[2];
                org.bukkit.entity.Player target = org.bukkit.Bukkit.getPlayerExact(playerName);
                if (target == null || !target.isOnline()) {
                    sender.sendMessage(ChatColor.RED + "Player must be online: " + playerName);
                    return true;
                }
                boolean ok = teams.addPlayerNameToTeam(target.getName(), key);
                if (!ok) {
                    sender.sendMessage(ChatColor.RED + "No such team: " + key);
                } else {
                    ChatColor color = teams.getTeamColor(key);
                    String labelName = teams.getTeamLabel(key);
                    sender.sendMessage(ChatColor.GREEN + "Added " + ChatColor.WHITE + target.getName() + ChatColor.GREEN + " to " + color + ChatColor.BOLD + labelName);
                }
                return true;
            }
            case "remove": {
                if (!sender.isOp()) { sender.sendMessage(ChatColor.RED + "OP only."); return true; }
                if (args.length < 3) { sender.sendMessage(ChatColor.YELLOW + "Usage: /"+label+" remove <player> <team>"); return true; }
                String playerName = args[1]; String key = args[2];
                boolean ok = teams.removePlayerNameFromTeam(playerName, key);
                sender.sendMessage(ok ? ChatColor.GREEN+"Removed "+playerName+" from "+key : ChatColor.RED+"Failed: not in that team or bad team");
                return true;
            }
            case "create": {
                if (!sender.isOp()) { sender.sendMessage(ChatColor.RED + "OP only."); return true; }
                if (args.length < 3) { sender.sendMessage(ChatColor.YELLOW + "Usage: /"+label+" create <team> <player1,player2,...>"); return true; }
                String key = args[1];
                String[] names = args[2].split(",");
                java.util.List<String> added = new java.util.ArrayList<>();
                java.util.List<String> skipped = new java.util.ArrayList<>();
                for (String raw : names) {
                    String n = raw.trim();
                    org.bukkit.entity.Player pl = org.bukkit.Bukkit.getPlayerExact(n);
                    if (pl != null && pl.isOnline()) {
                        teams.addPlayerNameToTeam(pl.getName(), key);
                        added.add(pl.getName());
                    } else {
                        skipped.add(n);
                    }
                }
                ChatColor color = teams.getTeamColor(key);
                String labelName = teams.getTeamLabel(key);
                sender.sendMessage(ChatColor.GREEN + "Assigned to " + color + ChatColor.BOLD + labelName + ChatColor.RESET + ChatColor.WHITE + ": " + String.join(", ", added));
                if (!skipped.isEmpty()) sender.sendMessage(ChatColor.YELLOW + "Skipped (offline/not found): " + ChatColor.WHITE + String.join(", ", skipped));
                return true;
            }
            case "disband": {
                if (!sender.isOp()) { sender.sendMessage(ChatColor.RED + "OP only."); return true; }
                if (args.length < 2) { sender.sendMessage(ChatColor.YELLOW + "Usage: /"+label+" disband <team>"); return true; }
                String key = args[1];
                boolean ok = teams.clearTeam(key);
                sender.sendMessage(ok ? ChatColor.GREEN+"Cleared team "+key : ChatColor.RED+"No such team");
                return true;
            }
            case "clear": {
                if (!sender.isOp()) { sender.sendMessage(ChatColor.RED + "OP only."); return true; }
                teams.clearAll();
                sender.sendMessage(ChatColor.GREEN + "All teams cleared.");
                return true;
            }
            case "random": {
                if (!sender.isOp()) { sender.sendMessage(ChatColor.RED + "OP only."); return true; }
                if (args.length < 2) { sender.sendMessage(ChatColor.YELLOW + "Usage: /"+label+" random <teamCount>"); return true; }
                int count;
                try { count = Integer.parseInt(args[1]); } catch (NumberFormatException ex) { sender.sendMessage(ChatColor.RED+"Not a number."); return true; }
                List<Player> pool = new ArrayList<>();
                for (Player pl : Bukkit.getOnlinePlayers()) if (pl.getGameMode() != GameMode.SPECTATOR) pool.add(pl);
                if (count <= 0) { sender.sendMessage(ChatColor.RED+"Team count must be > 0"); return true; }
                if (pool.isEmpty()) { sender.sendMessage(ChatColor.RED+"No eligible players online."); return true; }
                var result = teams.randomizeIntoTeams(count, pool);
                sender.sendMessage(ChatColor.YELLOW+"Assigned "+pool.size()+" players across "+result.size()+" teams.");
                for (var e : result.entrySet()) {
                    String teamKey = e.getKey();
                    ChatColor color = teams.getTeamColor(teamKey);
                    String labelName = teams.getTeamLabel(teamKey);
                    StringJoiner sj = new StringJoiner(", "); for (String n : e.getValue()) sj.add(n);
                    sender.sendMessage(color + ChatColor.BOLD.toString() + labelName + ChatColor.RESET + ChatColor.WHITE + ": " + sj);
                }
                return true;
            }
            default:
                sendHelp(sender, label);
                return true;
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        boolean op = sender.isOp();
        sender.sendMessage(ChatColor.AQUA + "Team commands:");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " list" + ChatColor.WHITE + " - List teams and members");
        sender.sendMessage(ChatColor.YELLOW + "/tc" + ChatColor.WHITE + " - Toggle team chat");
        if (op) {
            sender.sendMessage(ChatColor.GOLD + "Admin:");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " join <team>" + ChatColor.WHITE + " - Join a team");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " leave" + ChatColor.WHITE + " - Leave your team");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " add <player> <team>" + ChatColor.WHITE + " - Add player to team");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " remove <player> <team>" + ChatColor.WHITE + " - Remove player from team");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " create <team> <p1,p2,...>" + ChatColor.WHITE + " - Create/assign team");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " disband <team>" + ChatColor.WHITE + " - Clear a team");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " clear" + ChatColor.WHITE + " - Clear all teams");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " random <count>" + ChatColor.WHITE + " - Randomly assign non-spectators");
        }
    }
}
