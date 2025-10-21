package com.jumpcat.core.listeners;

import com.jumpcat.core.JumpCatPlugin;
import com.jumpcat.core.commands.LeaderboardCommand;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class LeaderboardListener implements Listener {
    private final LeaderboardCommand command;

    public LeaderboardListener(LeaderboardCommand command) {
        this.command = command;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory inv = e.getInventory();
        if (inv == null) return;
        String title = e.getView().getTitle();
        if (title == null || !ChatColor.stripColor(title).startsWith("Leaderboard (Page ")) return;
        e.setCancelled(true);
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null) return;
        Material type = clicked.getType();
        HumanEntity he = e.getWhoClicked();
        if (!(he instanceof Player)) return;
        Player p = (Player) he;
        try {
            String plain = ChatColor.stripColor(title);
            int open = plain.indexOf("(Page ");
            int slash = plain.indexOf('/', open);
            int close = plain.indexOf(')', slash);
            int page = Integer.parseInt(plain.substring(open + 6, slash).replace("Page ", "").trim());
            int total = Integer.parseInt(plain.substring(slash + 1, close).trim());
            if (type == Material.ARROW) {
                String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
                if ("Previous Page".equalsIgnoreCase(name) && page > 1) {
                    command.openPage(p, page - 1);
                } else if ("Next Page".equalsIgnoreCase(name) && page < total) {
                    command.openPage(p, page + 1);
                }
            }
        } catch (Throwable ignored) {}
    }
}
