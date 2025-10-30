package com.jumpcat.core.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public class ModerationCommand implements CommandExecutor, TabCompleter {
    // Global ban/mute maps (in-memory for demo, not persistent)
    public static final Map<UUID, BanInfo> bans = new HashMap<>();
    public static final Map<UUID, MuteInfo> mutes = new HashMap<>();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }
        if (args.length < 2) {
            sendUsage(sender, label);
            return true;
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        String targetName = args[1];

        UUID targetId = getUUID(targetName);
        if (targetId == null) {
            sender.sendMessage(ChatColor.RED + "Unknown/offline player: " + targetName);
            return true;
        }

        switch (subcommand) {
            case "ban": {
                long expiresAt = Long.MAX_VALUE;
                String durationStr = (args.length >= 3) ? args[2].toLowerCase() : "perm";
                if (!"perm".equals(durationStr)) {
                    expiresAt = System.currentTimeMillis() + parseDuration(durationStr);
                }
                String reason = (args.length >= 4) ? join(args, 3) : "No reason provided";
                bans.put(targetId, new BanInfo(expiresAt, reason));
                Player online = Bukkit.getPlayer(targetId);
                if (online != null)
                    online.kickPlayer(ChatColor.RED + "You are banned! Reason: " + reason);
                sender.sendMessage(ChatColor.GREEN + "Banned " + targetName + ".");
                break;
            }
            case "mute": {
                long expiresAt = Long.MAX_VALUE;
                String durationStr = (args.length >= 3) ? args[2].toLowerCase() : "perm";
                if (!"perm".equals(durationStr)) {
                    expiresAt = System.currentTimeMillis() + parseDuration(durationStr);
                }
                String reason = (args.length >= 4) ? join(args, 3) : "No reason provided";
                mutes.put(targetId, new MuteInfo(expiresAt, reason));
                sender.sendMessage(ChatColor.GREEN + "Muted " + targetName + ".");
                break;
            }
            case "kick": {
                Player online = Bukkit.getPlayer(targetId);
                String reason = (args.length >= 3) ? join(args, 2) : "Kicked by an operator.";
                if (online != null) {
                    online.kickPlayer(ChatColor.RED + reason);
                    sender.sendMessage(ChatColor.GREEN + "Kicked " + targetName + ".");
                } else {
                    sender.sendMessage(ChatColor.RED + "Player must be online to kick.");
                }
                break;
            }
            case "unban": {
                if (bans.containsKey(targetId)) {
                    bans.remove(targetId);
                    sender.sendMessage(ChatColor.GREEN + "Unbanned " + targetName + ".");
                } else {
                    sender.sendMessage(ChatColor.YELLOW + targetName + " is not banned.");
                }
                break;
            }
            case "unmute": {
                if (mutes.containsKey(targetId)) {
                    mutes.remove(targetId);
                    sender.sendMessage(ChatColor.GREEN + "Unmuted " + targetName + ".");
                } else {
                    sender.sendMessage(ChatColor.YELLOW + targetName + " is not muted.");
                }
                break;
            }
            default:
                sendUsage(sender, label);
        }
        return true;
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.YELLOW + "Usage:");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " ban <player> [duration|perm] [reason]");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " mute <player> [duration|perm] [reason]");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " kick <player> [reason]");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " unban <player>");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " unmute <player>");
    }

    // Duration parser (30m, 1h, 1d, perm)
    public static long parseDuration(String input) {
        try {
            if (input.endsWith("d"))
                return Long.parseLong(input.replace("d", "")) * 24L * 60 * 60 * 1000;
            if (input.endsWith("h"))
                return Long.parseLong(input.replace("h", "")) * 60 * 60 * 1000;
            if (input.endsWith("m"))
                return Long.parseLong(input.replace("m", "")) * 60 * 1000;
            if (input.endsWith("s"))
                return Long.parseLong(input.replace("s", "")) * 1000;
        } catch (Exception ignored) {}
        return 60*60*1000; // default 1 hour
    }

    private UUID getUUID(String name) {
        try {
            OfflinePlayer op = Bukkit.getOfflinePlayer(name);
            return op.getUniqueId();
        } catch (Exception e) { return null; }
    }
    private String join(String[] arr, int from) {
        if (from >= arr.length) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < arr.length; i++) {
            if (i > from) sb.append(' ');
            sb.append(arr[i]);
        }
        return sb.toString();
    }
    // Support tab completion for subcommands
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) return Arrays.asList("ban", "mute", "kick", "unban", "unmute");
        return Collections.emptyList();
    }
    // Info classes
    public static class BanInfo {
        public final long expiresAt;
        public final String reason;
        public BanInfo(long expiresAt, String reason) {
            this.expiresAt = expiresAt;
            this.reason = reason;
        }
    }
    public static class MuteInfo {
        public final long expiresAt;
        public final String reason;
        public MuteInfo(long expiresAt, String reason) {
            this.expiresAt = expiresAt;
            this.reason = reason;
        }
    }
}
