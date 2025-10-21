package com.jumpcat.core.commands;

import com.jumpcat.core.JumpCatPlugin;
import com.jumpcat.core.points.PointsService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.OfflinePlayer;
import java.util.Map;
import java.util.UUID;

public class JumpCatCommand implements CommandExecutor {
    private final JumpCatPlugin plugin;

    public JumpCatCommand(JumpCatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.AQUA + "JumpCat core is loaded.");
            return true;
        }
        // legacy subcommands removed; use /leaderboard and /points instead
        if (args[0].equalsIgnoreCase("hello")) {
            if (sender instanceof Player) {
                Player p = (Player) sender;
                p.sendMessage(ChatColor.GREEN + "Hello, " + p.getName() + "! :3");
            } else {
                sender.sendMessage("Hello from JumpCat!");
            }
            return true;
        }
        sender.sendMessage(ChatColor.RED + "Unknown subcommand. Try /" + label + " hello");
        return true;
    }
}
