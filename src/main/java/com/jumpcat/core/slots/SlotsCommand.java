package com.jumpcat.core.slots;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class SlotsCommand implements CommandExecutor {
    private final SlotsManager slots;

    public SlotsCommand(SlotsManager slots) {
        this.slots = slots;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("jumpcat.slots")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        if (args.length == 0) {
            int cap = slots.getCap();
            String capStr = cap > 0 ? String.valueOf(cap) : "off";
            sender.sendMessage(ChatColor.YELLOW + "Current slots cap: " + ChatColor.AQUA + capStr);
            sender.sendMessage(ChatColor.GRAY + "Usage: /slots <number|off>");
            return true;
        }
        String arg = args[0];
        if (arg.equalsIgnoreCase("off")) {
            slots.setCap(-1);
            sender.sendMessage(ChatColor.GREEN + "Slots cap disabled. Using server default.");
            return true;
        }
        try {
            int v = Integer.parseInt(arg);
            if (v < 1) { sender.sendMessage(ChatColor.RED + "Slots must be >= 1"); return true; }
            slots.setCap(v);
            sender.sendMessage(ChatColor.GREEN + "Slots cap set to " + ChatColor.AQUA + v);
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "Invalid number. Usage: /slots <number|off>");
        }
        return true;
    }
}
