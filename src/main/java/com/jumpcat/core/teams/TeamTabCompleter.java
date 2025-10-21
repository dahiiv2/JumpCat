package com.jumpcat.core.teams;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class TeamTabCompleter implements TabCompleter {
    private final TeamManager teams;

    public TeamTabCompleter(TeamManager teams) {
        this.teams = teams;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("teamchat")) {
            // /tc has no args
            return Collections.emptyList();
        }
        if (!name.equals("team")) return Collections.emptyList();

        boolean op = sender.isOp();

        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            subs.add("list");
            if (op) {
                subs.addAll(Arrays.asList("join", "leave", "add", "remove", "create", "disband", "clear", "random"));
            }
            return filter(subs, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list":
                return Collections.emptyList();
            case "join":
                if (!op) return Collections.emptyList();
                if (args.length == 2) return filter(new ArrayList<>(teams.listTeamKeys()), args[1]);
                return Collections.emptyList();
            case "leave":
                return Collections.emptyList();
            case "add":
            case "remove": {
                if (!op) return Collections.emptyList();
                if (args.length == 2) {
                    // suggest online player names
                    List<String> players = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
                    return filter(players, args[1]);
                }
                if (args.length == 3) {
                    return filter(new ArrayList<>(teams.listTeamKeys()), args[2]);
                }
                return Collections.emptyList();
            }
            case "create": {
                if (!op) return Collections.emptyList();
                if (args.length == 2) {
                    return filter(new ArrayList<>(teams.listTeamKeys()), args[1]);
                }
                if (args.length == 3) {
                    // suggest single player; user may build comma list manually
                    List<String> players = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
                    return filter(players, args[2]);
                }
                return Collections.emptyList();
            }
            case "disband": {
                if (!op) return Collections.emptyList();
                if (args.length == 2) return filter(new ArrayList<>(teams.listTeamKeys()), args[1]);
                return Collections.emptyList();
            }
            case "clear":
                return Collections.emptyList();
            case "random": {
                if (!op) return Collections.emptyList();
                if (args.length == 2) return filter(Arrays.asList("2","3","4","6","8","10","12"), args[1]);
                return Collections.emptyList();
            }
            default:
                return Collections.emptyList();
        }
    }

    private List<String> filter(List<String> options, String token) {
        String t = token == null ? "" : token.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(t))
                .sorted()
                .collect(Collectors.toList());
    }
}
