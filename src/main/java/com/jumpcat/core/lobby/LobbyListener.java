package com.jumpcat.core.lobby;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class LobbyListener implements Listener {
    private final LobbyManager lobby;

    public LobbyListener(LobbyManager lobby) {
        this.lobby = lobby;
    }

    private boolean inLobbyWorld(Player p) {
        World lw = lobby.getLobbyWorld();
        return lw != null && p.getWorld().equals(lw);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        World lw = lobby.getLobbyWorld();
        if (lw == null) return;
        Player p = e.getPlayer();
        // If player joins into an active game world or is already spectator (set by game join logic), do not override
        try {
            String wn = p.getWorld() != null ? p.getWorld().getName() : "";
            if (p.getGameMode() == GameMode.SPECTATOR) return;
            if (wn.startsWith("skywars_r") || wn.startsWith("uhc_meetup_r") || wn.equalsIgnoreCase("battle_box")) return;
        } catch (Throwable ignored) {}
        Location spawn = lobby.getLobbySpawn();
        if (spawn != null) p.teleport(spawn, PlayerTeleportEvent.TeleportCause.PLUGIN);
        p.setGameMode(GameMode.ADVENTURE);
        p.setFoodLevel(20);
        p.setSaturation(20);
        p.setHealth(Math.min(p.getHealth(), 20));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        World lw = lobby.getLobbyWorld();
        if (lw == null) return;
        if (!e.isBedSpawn()) {
            Location spawn = lobby.getLobbySpawn();
            if (spawn != null) e.setRespawnLocation(spawn);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (!inLobbyWorld(e.getPlayer())) return;
        Player p = e.getPlayer();
        if (p.isOp()) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (!inLobbyWorld(e.getPlayer())) return;
        Player p = e.getPlayer();
        if (p.isOp()) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (inLobbyWorld(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        if (!inLobbyWorld(p)) return;
        if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
            Location spawn = lobby.getLobbySpawn();
            if (spawn != null) {
                e.setCancelled(true);
                p.teleport(spawn, PlayerTeleportEvent.TeleportCause.PLUGIN);
            }
            return;
        }
        e.setCancelled(true);
    }

    @EventHandler
    public void onPvP(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player) || !(e.getDamager() instanceof Player)) return;
        Player victim = (Player) e.getEntity();
        if (inLobbyWorld(victim)) e.setCancelled(true);
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        if (inLobbyWorld(p)) e.setCancelled(true);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!inLobbyWorld(p)) return;
        Location to = e.getTo();
        if (to != null && to.getY() < 1) {
            Location spawn = lobby.getLobbySpawn();
            if (spawn != null) p.teleport(spawn, PlayerTeleportEvent.TeleportCause.PLUGIN);
        }
    }
}
