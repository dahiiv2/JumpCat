package com.jumpcat.core.commands;

import com.jumpcat.core.JumpCatPlugin;
import com.jumpcat.core.points.PointsService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class LeaderboardCommand implements CommandExecutor {
    private final JumpCatPlugin plugin;

    public LeaderboardCommand(JumpCatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage(ChatColor.YELLOW + "Players only."); return true; }
        Player p = (Player) sender;
        int page = 1;
        if (args.length >= 1) {
            try { page = Math.max(1, Integer.parseInt(args[0])); } catch (NumberFormatException ignored) {}
        }
        openPage(p, page);
        return true;
    }

    public void openPage(Player p, int page) {
        PointsService ps = plugin.getPointsService();
        if (ps == null) { p.sendMessage(ChatColor.RED + "Points service unavailable."); return; }
        Map<UUID, Integer> top = ps.top(1000); // fetch a lot; we paginate locally
        List<Map.Entry<UUID,Integer>> rows = new ArrayList<>(top.entrySet());
        int perPage = 45; // 5 rows for entries
        int total = rows.size();
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) perPage));
        if (page > totalPages) page = totalPages;
        int start = (page - 1) * perPage;
        int end = Math.min(start + perPage, total);
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_AQUA + "Leaderboard (Page " + page + "/" + totalPages + ")");
        for (int i = start, slot = 0; i < end; i++, slot++) {
            Map.Entry<UUID,Integer> e = rows.get(i);
            UUID id = e.getKey(); int pts = e.getValue();
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            String name = op != null && op.getName() != null ? op.getName() : id.toString().substring(0, 8);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            try {
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                if (op != null) meta.setOwningPlayer(op);
                meta.setDisplayName(ChatColor.YELLOW + "#" + (i+1) + ChatColor.WHITE + " " + name);
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Points: " + ChatColor.AQUA + pts);
                meta.setLore(lore);
                head.setItemMeta(meta);
            } catch (Throwable ignored) {}
            inv.setItem(slot, head);
        }
        // Controls: Prev (45), Next (53)
        ItemStack prev = new ItemStack(Material.ARROW);
        org.bukkit.inventory.meta.ItemMeta pm = prev.getItemMeta(); pm.setDisplayName(ChatColor.YELLOW + "Previous Page"); prev.setItemMeta(pm);
        ItemStack next = new ItemStack(Material.ARROW);
        org.bukkit.inventory.meta.ItemMeta nm = next.getItemMeta(); nm.setDisplayName(ChatColor.YELLOW + "Next Page"); next.setItemMeta(nm);
        if (page > 1) inv.setItem(45, prev);
        if (page < totalPages) inv.setItem(53, next);
        p.openInventory(inv);
    }
}
