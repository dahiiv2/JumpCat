package com.jumpcat.core.holo;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HologramCommand implements CommandExecutor {
    private final HologramManager holos;

    public HologramCommand(HologramManager holos) {
        this.holos = holos;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "/holo set <id> <player|team> <lines>");
            sender.sendMessage(ChatColor.YELLOW + "/holo clear <id>");
            sender.sendMessage(ChatColor.YELLOW + "/holo reload");
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "set": {
                if (!(sender instanceof Player)) { sender.sendMessage(ChatColor.RED + "Players only."); return true; }
                if (!sender.hasPermission("jumpcat.holo.set")) { sender.sendMessage(ChatColor.RED + "No permission."); return true; }
                if (args.length < 4) { sender.sendMessage(ChatColor.RED + "Usage: /holo set <id> <player|team> <lines>"); return true; }
                String id = args[1];
                String typeStr = args[2].toUpperCase();
                HologramManager.Type type;
                try { type = HologramManager.Type.valueOf(typeStr); } catch (IllegalArgumentException ex) { sender.sendMessage(ChatColor.RED + "Type must be player or team"); return true; }
                int lines;
                try { lines = Integer.parseInt(args[3]); } catch (NumberFormatException ex) { sender.sendMessage(ChatColor.RED + "Lines must be a number"); return true; }
                Location here = ((Player) sender).getLocation();
                holos.set(id, type, here, lines);
                sender.sendMessage(ChatColor.GREEN + "Hologram '"+id+"' set at your location.");
                return true;
            }
            case "clear": {
                if (!sender.hasPermission("jumpcat.holo.clear")) { sender.sendMessage(ChatColor.RED + "No permission."); return true; }
                if (args.length < 2) { sender.sendMessage(ChatColor.RED + "Usage: /holo clear <id>"); return true; }
                String id = args[1];
                holos.clear(id);
                sender.sendMessage(ChatColor.GREEN + "Hologram '"+id+"' cleared.");
                return true;
            }
            case "reload": {
                if (!sender.hasPermission("jumpcat.holo.reload")) { sender.sendMessage(ChatColor.RED + "No permission."); return true; }
                holos.reload();
                sender.sendMessage(ChatColor.GREEN + "Holograms reloaded.");
                return true;
            }
            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand.");
                return true;
        }
    }
}
