package com.jumpcat.core.commands;

import com.jumpcat.core.JumpCatPlugin;
import com.jumpcat.core.points.PointsService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class PointsCommand implements CommandExecutor {
    private final JumpCatPlugin plugin;

    public PointsCommand(JumpCatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) { sender.sendMessage(ChatColor.RED + "OP only."); return true; }
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " clear [save]");
            return true;
        }
        if (args[0].equalsIgnoreCase("clear")) {
            PointsService ps = plugin.getPointsService();
            if (ps == null) { sender.sendMessage(ChatColor.RED + "Points service unavailable."); return true; }
            ps.clearAll();
            if (args.length >= 2 && args[1].equalsIgnoreCase("save")) {
                ps.save(plugin);
            }
            sender.sendMessage(ChatColor.YELLOW + "All points cleared" + (args.length>=2 && args[1].equalsIgnoreCase("save")?" and saved.":"."));
            return true;
        }
        sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " clear [save]");
        return true;
    }
}
