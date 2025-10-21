package com.jumpcat.core.listeners;

import com.jumpcat.core.slots.SlotsManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

public class SlotsLoginListener implements Listener {
    private final SlotsManager slots;

    public SlotsLoginListener(SlotsManager slots) {
        this.slots = slots;
    }

    @EventHandler
    public void onLogin(PlayerLoginEvent e) {
        if (slots == null) return;
        int cap = slots.getCap();
        if (cap <= 0) return;
        if (e.getPlayer().hasPermission("jumpcat.slots.bypass")) return;
        int online = Bukkit.getOnlinePlayers().size();
        // Paper fires this before player is fully added; be conservative
        if (online >= cap) {
            e.disallow(PlayerLoginEvent.Result.KICK_FULL, ChatColor.RED + "Server is full (" + cap + ")" + ChatColor.GRAY + "\nTry again soon!");
        }
    }
}
