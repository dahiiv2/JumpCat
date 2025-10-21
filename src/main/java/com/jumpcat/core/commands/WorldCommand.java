package com.jumpcat.core.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class WorldCommand implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Usage:");
            sender.sendMessage(ChatColor.GRAY + "/world list");
            sender.sendMessage(ChatColor.GRAY + "/world goto <world> [x y z]");
            sender.sendMessage(ChatColor.GRAY + "/world setspawn [world]");
            sender.sendMessage(ChatColor.GRAY + "/world load <world>");
            sender.sendMessage(ChatColor.GRAY + "/world create <world> [void]");
            sender.sendMessage(ChatColor.GRAY + "/world unload <world> [save]");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list": {
                List<String> loaded = Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toList());
                sender.sendMessage(ChatColor.YELLOW + "Loaded worlds:" + ChatColor.AQUA + " " + String.join(ChatColor.WHITE + ", " + ChatColor.AQUA, loaded));
                // Also list folders that look like worlds in server root
                File root = new File(".");
                File[] dirs = root.listFiles((f) -> f.isDirectory() && new File(f, "level.dat").exists());
                if (dirs != null) {
                    List<String> folderNames = Arrays.stream(dirs).map(File::getName).collect(Collectors.toList());
                    sender.sendMessage(ChatColor.YELLOW + "World folders:" + ChatColor.GRAY + " " + String.join(ChatColor.WHITE + ", " + ChatColor.GRAY, folderNames));
                }
                return true;
            }
            case "goto": {
                if (!(sender instanceof Player)) { sender.sendMessage(ChatColor.RED + "Players only."); return true; }
                if (args.length < 2) { sender.sendMessage(ChatColor.RED + "Usage: /world goto <world> [x y z]"); return true; }
                String name = args[1];
                World w = Bukkit.getWorld(name);
                if (w == null) {
                    // Try to load it if folder exists
                    File f = new File(name);
                    if (f.exists() && f.isDirectory()) {
                        w = Bukkit.createWorld(new WorldCreator(name));
                    }
                }
                if (w == null) { sender.sendMessage(ChatColor.RED + "World not found: " + name); return true; }
                Player p = (Player) sender;
                Location dest;
                if (args.length >= 5) {
                    try {
                        double x = Double.parseDouble(args[2]);
                        double y = Double.parseDouble(args[3]);
                        double z = Double.parseDouble(args[4]);
                        dest = new Location(w, x, y, z);
                    } catch (NumberFormatException ex) {
                        sender.sendMessage(ChatColor.RED + "Invalid coordinates.");
                        return true;
                    }
                } else {
                    dest = w.getSpawnLocation();
                }
                p.teleport(dest);
                sender.sendMessage(ChatColor.GREEN + "Teleported to " + ChatColor.AQUA + name + ChatColor.GRAY + " @ " + ChatColor.WHITE + dest.getBlockX()+","+dest.getBlockY()+","+dest.getBlockZ());
                return true;
            }
            case "setspawn": {
                if (!(sender instanceof Player)) { sender.sendMessage(ChatColor.RED + "Players only."); return true; }
                Player p = (Player) sender;
                World w = p.getWorld();
                if (args.length >= 2) {
                    World maybe = Bukkit.getWorld(args[1]);
                    if (maybe != null) w = maybe; else { sender.sendMessage(ChatColor.RED + "World not found: " + args[1]); return true; }
                }
                Location l = p.getLocation();
                boolean ok = w.setSpawnLocation(l);
                sender.sendMessage((ok?ChatColor.GREEN:ChatColor.RED) + "Set spawn for " + ChatColor.AQUA + w.getName() + ChatColor.GRAY + " @ " + ChatColor.WHITE + l.getBlockX()+","+l.getBlockY()+","+l.getBlockZ());
                return true;
            }
            case "load": {
                if (args.length < 2) { sender.sendMessage(ChatColor.RED + "Usage: /world load <world>"); return true; }
                String name = args[1];
                if (Bukkit.getWorld(name) != null) { sender.sendMessage(ChatColor.YELLOW + "World already loaded: " + name); return true; }
                File f = new File(name);
                if (!f.exists() || !f.isDirectory()) { sender.sendMessage(ChatColor.RED + "No world folder: " + name); return true; }
                World w = Bukkit.createWorld(new WorldCreator(name));
                if (w == null) sender.sendMessage(ChatColor.RED + "Failed to load world: " + name); else sender.sendMessage(ChatColor.GREEN + "Loaded world: " + ChatColor.AQUA + name);
                return true;
            }
            case "create": {
                if (args.length < 2) { sender.sendMessage(ChatColor.RED + "Usage: /world create <world> [void]"); return true; }
                String name = args[1];
                if (Bukkit.getWorld(name) != null) { sender.sendMessage(ChatColor.YELLOW + "World already loaded: " + name); return true; }
                if (new File(name).exists()) { sender.sendMessage(ChatColor.RED + "Folder already exists: " + name + ChatColor.GRAY + ". Use /world load " + name); return true; }
                WorldCreator wc = new WorldCreator(name);
                boolean makeVoid = args.length >= 3 && args[2].equalsIgnoreCase("void");
                if (makeVoid) {
                    try { wc.generator(new com.jumpcat.core.lobby.VoidChunkGenerator()); } catch (Throwable ignored) {}
                }
                World w = Bukkit.createWorld(wc);
                if (w == null) { sender.sendMessage(ChatColor.RED + "Failed to create world: " + name); return true; }
                sender.sendMessage(ChatColor.GREEN + "Created world: " + ChatColor.AQUA + name + ChatColor.GRAY + (makeVoid?" (void)":""));
                return true;
            }
            case "unload": {
                if (args.length < 2) { sender.sendMessage(ChatColor.RED + "Usage: /world unload <world> [save]"); return true; }
                String name = args[1];
                boolean save = args.length >= 3 && Boolean.parseBoolean(args[2]);
                World w = Bukkit.getWorld(name);
                if (w == null) { sender.sendMessage(ChatColor.RED + "World not loaded: " + name); return true; }
                boolean ok = Bukkit.unloadWorld(w, save);
                sender.sendMessage((ok?ChatColor.GREEN:ChatColor.RED) + (ok?"Unloaded":"Failed to unload") + " world: " + ChatColor.AQUA + name);
                return true;
            }
            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand. Use /world for help.");
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> empty = new ArrayList<>();
        if (!sender.hasPermission("jumpcat.world")) return empty;
        if (args.length == 1) {
            return Arrays.asList("list", "goto", "setspawn", "load", "unload");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("goto") || args[0].equalsIgnoreCase("load") || args[0].equalsIgnoreCase("unload") || args[0].equalsIgnoreCase("setspawn"))) {
            List<String> loaded = Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toList());
            // Also suggest folders with level.dat
            File root = new File(".");
            File[] dirs = root.listFiles((f) -> f.isDirectory() && new File(f, "level.dat").exists());
            if (dirs != null) {
                for (File d : dirs) loaded.add(d.getName());
            }
            return loaded;
        }
        return empty;
    }
}
