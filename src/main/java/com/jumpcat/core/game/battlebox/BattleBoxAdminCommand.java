package com.jumpcat.core.game.battlebox;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class BattleBoxAdminCommand implements CommandExecutor, TabCompleter {
    private final BattleBoxManager manager;

    public BattleBoxAdminCommand(BattleBoxManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) { sender.sendMessage(ChatColor.RED + "OP only."); return true; }
        if (args.length == 0) { help(sender, label); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "arena": {
                if (args.length < 2) { help(sender, label); return true; }
                String action = args[1].toLowerCase(Locale.ROOT);
                if (action.equals("list")) {
                    sender.sendMessage(ChatColor.AQUA + "Battle Box arenas:");
                    for (BattleBoxManager.Arena a : manager.listArenas()) {
                        sender.sendMessage(ChatColor.YELLOW + "- " + a.id + ChatColor.WHITE + (a.isConfigured() ? " (ok)" : " (incomplete)"));
                    }
                    return true;
                }
                if (args.length < 3) { sender.sendMessage(ChatColor.YELLOW + "Usage: /"+label+" arena "+action+" <id>"); return true; }
                String id = args[2];
                if (action.equals("create")) {
                    manager.createArena(id);
                    sender.sendMessage(ChatColor.GREEN + "Arena created: " + id);
                    return true;
                }
                if (!(sender instanceof Player)) { sender.sendMessage("Players only for position/spawn."); return true; }
                Player p = (Player) sender;
                switch (action) {
                    case "setpos1":
                        manager.setPos1(id, p.getLocation());
                        sender.sendMessage(ChatColor.GREEN + "Set pos1 for arena " + id);
                        return true;
                    case "setpos2":
                        manager.setPos2(id, p.getLocation());
                        sender.sendMessage(ChatColor.GREEN + "Set pos2 for arena " + id);
                        return true;
                    case "setspawn":
                        if (args.length < 4) { sender.sendMessage(ChatColor.YELLOW + "Usage: /"+label+" arena setspawn <id> <A|B|spec>"); return true; }
                        String which = args[3].toUpperCase(Locale.ROOT);
                        char w = which.startsWith("A") ? 'A' : (which.startsWith("B") ? 'B' : 'S');
                        manager.setSpawn(id, w, p.getLocation());
                        sender.sendMessage(ChatColor.GREEN + "Set spawn " + which + " for arena " + id);
                        return true;
                    default:
                        help(sender, label); return true;
                }
            }
            case "world": {
                if (!(sender instanceof Player)) { sender.sendMessage("Players only."); return true; }
                Player p = (Player) sender;
                var w = manager.getWorld();
                if (w == null) { sender.sendMessage(ChatColor.RED + "battle_box world not found."); return true; }
                p.teleport(w.getSpawnLocation());
                sender.sendMessage(ChatColor.YELLOW + "Teleported to battle_box world.");
                return true;
            }
            case "tparena": {
                if (!(sender instanceof Player)) { sender.sendMessage("Players only."); return true; }
                if (args.length < 2) { sender.sendMessage(ChatColor.YELLOW + "Usage: /"+label+" tparena <id>"); return true; }
                var a = manager.getArena(args[1]);
                if (a == null || a.spectatorSpawn == null) { sender.sendMessage(ChatColor.RED + "Unknown arena or spectator spawn not set."); return true; }
                ((Player) sender).teleport(a.spectatorSpawn);
                sender.sendMessage(ChatColor.YELLOW + "Teleported to arena " + args[1] + " spectator spawn.");
                return true;
            }
            default:
                help(sender, label); return true;
        }
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.AQUA + "Battle Box admin:");
        sender.sendMessage(ChatColor.YELLOW + "/"+label+" arena list" + ChatColor.WHITE + " - List arenas");
        sender.sendMessage(ChatColor.YELLOW + "/"+label+" arena create <id>" + ChatColor.WHITE + " - Create arena");
        sender.sendMessage(ChatColor.YELLOW + "/"+label+" arena setpos1 <id>" + ChatColor.WHITE + " - Set pos1 at your location");
        sender.sendMessage(ChatColor.YELLOW + "/"+label+" arena setpos2 <id>" + ChatColor.WHITE + " - Set pos2 at your location");
        sender.sendMessage(ChatColor.YELLOW + "/"+label+" arena setspawn <id> <A|B|spec>" + ChatColor.WHITE + " - Set team/spectator spawn at your location");
        sender.sendMessage(ChatColor.YELLOW + "/"+label+" world" + ChatColor.WHITE + " - Teleport to battle_box world spawn");
        sender.sendMessage(ChatColor.YELLOW + "/"+label+" tparena <id>" + ChatColor.WHITE + " - Teleport to an arena spectator spawn");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.isOp()) return List.of();
        if (args.length == 1) return filter(List.of("arena","world","tparena"), args[0]);
        if (!args[0].equalsIgnoreCase("arena")) return List.of();
        if (args.length == 2) return filter(Arrays.asList("list","create","setpos1","setpos2","setspawn"), args[1]);
        if (args.length == 3) return filter(new ArrayList<>(manager.listArenas().stream().map(a->a.id).toList()), args[2]);
        if (args.length == 4 && args[1].equalsIgnoreCase("setspawn")) return filter(Arrays.asList("A","B","spec"), args[3]);
        return List.of();
    }

    private List<String> filter(List<String> options, String token) {
        String t = token == null ? "" : token.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String s : options) if (s.toLowerCase(Locale.ROOT).startsWith(t)) out.add(s);
        return out;
    }
}
