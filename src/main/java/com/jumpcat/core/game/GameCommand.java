package com.jumpcat.core.game;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import com.jumpcat.core.game.WorldUtil;

public class GameCommand implements CommandExecutor {
    private final org.bukkit.plugin.Plugin plugin;
    private final GameRegistry registry;

    public GameCommand(org.bukkit.plugin.Plugin plugin, GameRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "OP only.");
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "list": {
                sender.sendMessage(ChatColor.AQUA + "Games:");
                for (GameController c : registry.list()) {
                    sender.sendMessage(ChatColor.YELLOW + "- " + c.getId() + ChatColor.WHITE + " (" + c.getDisplayName() + ")");
                }
                return true;
            }
            case "start": {
                if (args.length < 2) { sender.sendMessage(ChatColor.YELLOW + "Usage: /"+label+" start <gameId>"); return true; }
                GameController c = registry.get(args[1]);
                if (c == null) { sender.sendMessage(ChatColor.RED + "Unknown game: " + args[1]); return true; }
                c.prepare(sender);
                // Announce game start with a short description
                try {
                    String id = c.getId().toLowerCase();
                    String name = c.getDisplayName();
                    String desc;
                    switch (id) {
                        case "uhcmeetup":
                            desc = ChatColor.GRAY + "Fast-paced UHC: two rounds, shrinking border, last team alive wins."; break;
                        case "battlebox":
                            desc = ChatColor.GRAY + "Team arena duels in short rounds. Eliminate the other team to win."; break;
                        case "skywars":
                            desc = ChatColor.GRAY + "Fight other teams in floating islands with a shrinking border."; break;
                        case "tntrun":
                            desc = ChatColor.GRAY + "Running over land breaks it. Be the last player standing to win."; break;
                        default:
                            desc = ChatColor.GRAY + "A JumpCat mini-game. Good luck!"; break;
                    }
                    org.bukkit.Bukkit.broadcastMessage("");
                    org.bukkit.Bukkit.broadcastMessage(ChatColor.GOLD + "» " + ChatColor.YELLOW + "Game starting..." );
                    org.bukkit.Bukkit.broadcastMessage(ChatColor.AQUA + name + ChatColor.WHITE + " (" + ChatColor.GRAY + id + ChatColor.WHITE + ")");
                    org.bukkit.Bukkit.broadcastMessage(desc);
                    org.bukkit.Bukkit.broadcastMessage("");
                } catch (Throwable ignored) {}
                c.start(sender);
                sender.sendMessage(ChatColor.GREEN + "Started game: " + c.getId());
                return true;
            }
            case "stop": {
                if (args.length < 2) { sender.sendMessage(ChatColor.YELLOW + "Usage: /"+label+" stop <gameId>"); return true; }
                GameController c = registry.get(args[1]);
                if (c == null) { sender.sendMessage(ChatColor.RED + "Unknown game: " + args[1]); return true; }
                c.stop(sender);
                // Fallback: ensure all players in any game world are reset and sent to lobby
                try { globalCleanup(); } catch (Throwable ignored) {}
                sender.sendMessage(ChatColor.YELLOW + "Stopped game: " + c.getId());
                return true;
            }
            case "status": {
                if (args.length < 2) { sender.sendMessage(ChatColor.YELLOW + "Usage: /"+label+" status <gameId>"); return true; }
                GameController c = registry.get(args[1]);
                if (c == null) { sender.sendMessage(ChatColor.RED + "Unknown game: " + args[1]); return true; }
                sender.sendMessage(ChatColor.AQUA + c.getDisplayName() + ChatColor.WHITE + ": " + c.status());
                return true;
            }
            default:
                sendHelp(sender, label);
                return true;
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.AQUA + "Game commands:");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " list" + ChatColor.WHITE + " - List games");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " start <id>" + ChatColor.WHITE + " - Start a game");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " stop <id>" + ChatColor.WHITE + " - Stop a game");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " status <id>" + ChatColor.WHITE + " - Show game status");
    }

    private void globalCleanup() {
        org.bukkit.Location lobby = null;
        try {
            if (plugin instanceof com.jumpcat.core.JumpCatPlugin) {
                var lm = ((com.jumpcat.core.JumpCatPlugin) plugin).getLobbyManager();
                if (lm != null) lobby = lm.getLobbySpawn();
            }
        } catch (Throwable ignored) {}
        if (lobby == null) {
            try { var lw = org.bukkit.Bukkit.getWorld("lobby"); if (lw != null) lobby = lw.getSpawnLocation(); } catch (Throwable ignored) {}
        }
        if (lobby == null) lobby = org.bukkit.Bukkit.getWorlds().get(0).getSpawnLocation();
        for (org.bukkit.World w : org.bukkit.Bukkit.getWorlds()) {
            if (!com.jumpcat.core.game.WorldUtil.isAnyGameWorld(w)) continue;
            for (org.bukkit.entity.Player p : w.getPlayers()) {
                try {
                    p.getInventory().clear();
                    p.getInventory().setArmorContents(null);
                    p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
                    p.setHealth(20.0);
                    p.setFoodLevel(20);
                    p.setSaturation(20);
                    p.setLevel(0);
                    p.setExp(0f);
                    p.setTotalExperience(0);
                    p.setGameMode(org.bukkit.GameMode.ADVENTURE);
                    p.teleport(lobby);
                } catch (Throwable ignored) {}
            }
        }
    }
}
