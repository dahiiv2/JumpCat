package com.jumpcat.core.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public class MsgCommand implements CommandExecutor, TabCompleter {
    // Tracks the last conversation partner for reply. Stored bidirectionally.
    private static final Map<UUID, UUID> lastConversation = new HashMap<>();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("msg") || name.equals("tell") || name.equals("whisper") || name.equals("w") || name.equals("m")) {
            return handleMsg(sender, args);
        } else if (name.equals("r")) {
            return handleReply(sender, args);
        }
        return false;
    }

    private boolean handleMsg(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use private messages.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /msg <player> <message>");
            return true;
        }
        Player from = (Player) sender;
        Player to = Bukkit.getPlayerExact(args[0]);
        if (to == null || !to.isOnline()) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
            return true;
        }
        if (to.getUniqueId().equals(from.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "You cannot message yourself.");
            return true;
        }
        String message = join(args, 1);
        deliver(from, to, message);
        return true;
    }

    private boolean handleReply(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use private messages.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /r <message>");
            return true;
        }
        Player from = (Player) sender;
        UUID last = lastConversation.get(from.getUniqueId());
        if (last == null) {
            from.sendMessage(ChatColor.RED + "No one to reply to.");
            return true;
        }
        Player to = Bukkit.getPlayer(last);
        if (to == null || !to.isOnline()) {
            from.sendMessage(ChatColor.RED + "That player is no longer online.");
            return true;
        }
        String message = join(args, 0);
        deliver(from, to, message);
        return true;
    }

    private void deliver(Player from, Player to, String message) {
        String toMsg = ChatColor.GRAY + "[" + ChatColor.AQUA + "From" + ChatColor.GRAY + "] " + ChatColor.YELLOW + from.getName() + ChatColor.GRAY + ": " + ChatColor.WHITE + message;
        String fromMsg = ChatColor.GRAY + "[" + ChatColor.AQUA + "To" + ChatColor.GRAY + "] " + ChatColor.YELLOW + to.getName() + ChatColor.GRAY + ": " + ChatColor.WHITE + message;
        try { to.sendMessage(toMsg); } catch (Throwable ignored) {}
        try { from.sendMessage(fromMsg); } catch (Throwable ignored) {}
        // Update last conversation both ways
        lastConversation.put(from.getUniqueId(), to.getUniqueId());
        lastConversation.put(to.getUniqueId(), from.getUniqueId());
    }

    private String join(String[] parts, int startIdx) {
        StringBuilder sb = new StringBuilder();
        for (int i = startIdx; i < parts.length; i++) {
            if (i > startIdx) sb.append(' ');
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("msg") || name.equals("tell") || name.equals("whisper") || name.equals("w") || name.equals("m")) {
            if (args.length == 1) {
                String prefix = args[0].toLowerCase(Locale.ROOT);
                List<String> out = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (sender instanceof Player && ((Player) sender).getUniqueId().equals(p.getUniqueId())) continue;
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(p.getName());
                }
                return out;
            }
        }
        return Collections.emptyList();
    }
}


