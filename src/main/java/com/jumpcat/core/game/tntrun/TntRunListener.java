package com.jumpcat.core.game.tntrun;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TntRunListener implements Listener {
    private final TntRunController controller;
    private final TntRunConfig config;

    private static class BlockKey { final String w; final int x,y,z; BlockKey(String w,int x,int y,int z){this.w=w;this.x=x;this.y=y;this.z=z;} public boolean equals(Object o){ if(!(o instanceof BlockKey))return false; BlockKey b=(BlockKey)o; return x==b.x&&y==b.y&&z==b.z&&java.util.Objects.equals(w,b.w);} public int hashCode(){ return java.util.Objects.hash(w,x,y,z);} }
    private static final Set<BlockKey> scheduled = ConcurrentHashMap.newKeySet();

    private static class Counters { int eaten=0; int charges=0; long lastJumpTick=0; }
    private static final Map<UUID, Counters> stats = new ConcurrentHashMap<>();
    private static org.bukkit.scheduler.BukkitTask tickTask;

    public TntRunListener(TntRunController ctrl, TntRunConfig cfg) {
        this.controller = ctrl; this.config = cfg;
    }

    private boolean isPowder(Material m) {
        return m != null && m.name().endsWith("_CONCRETE_POWDER");
    }

    private boolean inTntRun(World w) { return TntRunController.CURRENT != null && w != null && w.getName() != null && w.getName().startsWith("tntrun_r"); }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        World w = p.getWorld();
        if (!inTntRun(w)) return;
        if (!controller.isRunning() || !w.getName().equals(controller.currentWorld())) return;
        if (!controller.isLive()) return; // grace: no decay yet
        if (!controller.isAlive(p.getUniqueId())) return;
        if (e.getTo() == null) return;
        if (e.getTo().getY() < config.eliminationY) {
            controller.onEliminated(p.getUniqueId());
            try { p.setGameMode(GameMode.SPECTATOR); p.teleport(w.getSpawnLocation()); } catch (Throwable ignored) {}
            return;
        }
        processFootprint(p, e.getTo());
        // Always show action bar: Blocks and Alive
        Counters cNow = stats.computeIfAbsent(p.getUniqueId(), k -> new Counters());
        String msg = ChatColor.GOLD + "Blocks " + ChatColor.WHITE + cNow.eaten + ChatColor.GRAY + " | " + ChatColor.WHITE + "Alive " + ChatColor.GREEN + getAliveCount();
        try { p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new net.md_5.bungee.api.chat.TextComponent(msg)); } catch (Throwable ignored) {}
    }

    private void processFootprint(Player p, org.bukkit.Location to) {
        World w = p.getWorld();
        int by = to.getBlockY() - 1; if (by < w.getMinHeight()) return;
        // Remove blocks under player's footprint corners to prevent edge standing
        java.util.Set<Long> done = new java.util.HashSet<>();
        double[] offs = new double[]{-0.3, 0.3};
        for (double ox : offs) {
            for (double oz : offs) {
                int bx = (int) Math.floor(to.getX() + ox);
                int bz = (int) Math.floor(to.getZ() + oz);
                long key = (((long)bx) << 32) ^ (bz & 0xffffffffL);
                if (done.add(key)) scheduleRemoval(p, w, bx, by, bz);
            }
        }
        // Also remove the primary center block for safety
        scheduleRemoval(p, w, to.getBlockX(), by, to.getBlockZ());
    }

    private void scheduleRemoval(Player p, World w, int bx, int by, int bz) {
        BlockKey key = new BlockKey(w.getName(), bx, by, bz);
        if (!scheduled.add(key)) return;
        org.bukkit.block.Block b = w.getBlockAt(bx, by, bz);
        if (b.getType() == Material.AIR) { scheduled.remove(key); return; }
        if (!isPowder(b.getType())) { scheduled.remove(key); return; }
        boolean countEaten = true;
        Bukkit.getScheduler().runTaskLater(com.jumpcat.core.JumpCatPlugin.getPlugin(com.jumpcat.core.JumpCatPlugin.class), () -> {
            try {
                org.bukkit.block.Block bb = w.getBlockAt(bx, by, bz);
                if (bb.getType() != Material.AIR) {
                    bb.setType(Material.AIR, false);
                    try { org.bukkit.block.Block below = w.getBlockAt(bx, by-1, bz); if (below.getType() == Material.TNT) below.setType(Material.AIR, false); } catch (Throwable ignored) {}
                    if (countEaten && controller.isAlive(p.getUniqueId())) {
                        Counters c = stats.computeIfAbsent(p.getUniqueId(), k -> new Counters());
                        c.eaten++;
                        if (c.eaten % config.blocksPerCharge == 0) { c.charges++; giveOrUpdateFeather(p, c.charges); }
                    }
                }
            } finally {
                scheduled.remove(key);
            }
        }, config.decayDelayTicks);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        World w = p.getWorld();
        if (!inTntRun(w)) return;
        ItemStack it = p.getInventory().getItemInMainHand();
        if (it == null || it.getType() != Material.FEATHER) return;
        try { if (p.hasCooldown(Material.FEATHER)) return; } catch (Throwable ignored) {}
        Counters c = stats.computeIfAbsent(p.getUniqueId(), k -> new Counters());
        long tick = Bukkit.getCurrentTick();
        if (c.charges <= 0) return;
        int minCooldown = Math.max(4, config.doubleJumpCooldownTicks); // enforce >= 0.2s debounce
        if (tick - c.lastJumpTick < minCooldown) return;
        // consume one charge
        c.charges--; c.lastJumpTick = tick;
        try { p.setCooldown(Material.FEATHER, minCooldown); } catch (Throwable ignored) {}
        // subtract blocks used and refresh action bar
        c.eaten = Math.max(0, c.eaten - config.blocksPerCharge);
        giveOrUpdateFeather(p, c.charges);
        // launch based on aim: looking straight up -> mostly vertical
        org.bukkit.util.Vector dir = p.getLocation().getDirection();
        double upFrac = Math.max(0.0, dir.getY()); // 0 (horizontal/down) .. 1 (straight up)
        org.bukkit.util.Vector horiz = dir.clone(); horiz.setY(0);
        if (horiz.lengthSquared() > 1e-6) horiz.normalize(); else horiz = new org.bukkit.util.Vector(0,0,1);
        double v = config.jumpVerticalStrong * (0.2 + 0.8 * upFrac); // stronger vertical when looking up
        double h = config.jumpHorizontalStrong * (1.0 - 0.9 * upFrac); // near-zero horizontal when looking up
        // Boost overall distance by ~30%
        v *= 1.3;
        h *= 1.3;
        org.bukkit.util.Vector vec = horiz.multiply(h).setY(v);
        try { p.setVelocity(vec); } catch (Throwable ignored) {}
        try { p.getWorld().playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1.2f); } catch (Throwable ignored) {}
        // show updated action bar immediately
        String msg = ChatColor.GOLD + "Blocks " + ChatColor.WHITE + c.eaten + ChatColor.GRAY + " | " + ChatColor.WHITE + "Alive " + ChatColor.GREEN + getAliveCount();
        try { p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new net.md_5.bungee.api.chat.TextComponent(msg)); } catch (Throwable ignored) {}
    }

    private void giveOrUpdateFeather(Player p, int charges) {
        // Ensure feather present and rename with charges
        boolean found = false;
        for (ItemStack s : p.getInventory().getContents()) {
            if (s != null && s.getType() == Material.FEATHER) { found = true; try { var m = s.getItemMeta(); if (m != null) { m.setDisplayName(ChatColor.AQUA + "Double Jump (100 blocks)"); s.setItemMeta(m); } } catch (Throwable ignored) {} break; }
        }
        if (!found) {
            ItemStack f = new ItemStack(Material.FEATHER, 1);
            try { var m = f.getItemMeta(); if (m != null) { m.setDisplayName(ChatColor.AQUA + "Double Jump (100 blocks)"); f.setItemMeta(m); } } catch (Throwable ignored) {}
            try { p.getInventory().addItem(f); } catch (Throwable ignored) {}
        }
    }

    @EventHandler
    public void onAnyDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        if (!inTntRun(p.getWorld())) return;
        // cancel all generic damage (no PvP in this mode, falls are handled via Y threshold)
        e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (!inTntRun(p.getWorld())) return;
        controller.onEliminated(p.getUniqueId());
    }

    // World rules: disallow place/break, drop/pickup
    @EventHandler
    public void onBreak(BlockBreakEvent e) { if (inTntRun(e.getBlock().getWorld())) e.setCancelled(true); }
    @EventHandler
    public void onPlace(BlockPlaceEvent e) { if (inTntRun(e.getBlock().getWorld())) e.setCancelled(true); }
    @EventHandler
    public void onDrop(PlayerDropItemEvent e) { if (inTntRun(e.getPlayer().getWorld())) e.setCancelled(true); }
    @EventHandler
    public void onPickup(PlayerAttemptPickupItemEvent e) { if (inTntRun(e.getPlayer().getWorld())) e.setCancelled(true); }

    private int getAliveCount() {
        // Iterate online players in current world and count alive according to controller
        World w = Bukkit.getWorld(controller.currentWorld());
        if (w == null) return 0;
        int c = 0; for (Player pp : w.getPlayers()) if (controller.isAlive(pp.getUniqueId())) c++;
        return c;
    }

    // Round lifecycle integration
    public static void resetForNewRound() {
        if (tickTask != null) { try { tickTask.cancel(); } catch (Throwable ignored) {} tickTask = null; }
        scheduled.clear();
        stats.clear();
    }

    public static void startForRound(org.bukkit.plugin.Plugin plugin, TntRunController ctrl) {
        if (tickTask != null) { try { tickTask.cancel(); } catch (Throwable ignored) {} }
        tickTask = org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                if (!ctrl.isRunning() || !ctrl.isLive()) return;
                World w = org.bukkit.Bukkit.getWorld(ctrl.currentWorld());
                if (w == null) return;
                for (Player p : w.getPlayers()) {
                    if (!ctrl.isAlive(p.getUniqueId())) continue;
                    processStaticFootprint(ctrl, p);
                }
            } catch (Throwable ignored) {}
        }, 2L, 2L);
    }

    private static void processStaticFootprint(TntRunController ctrl, Player p) {
        // Helper for tick task: reuse same neighbor heuristic
        TntRunListener inst = new TntRunListener(ctrl, new TntRunConfig()); // transient for method access; won't use fields beyond static
        inst.processFootprint(p, p.getLocation());
        Counters cNow = stats.computeIfAbsent(p.getUniqueId(), k -> new Counters());
        String msg = ChatColor.GOLD + "Blocks " + ChatColor.WHITE + cNow.eaten + ChatColor.GRAY + " | " + ChatColor.WHITE + "Alive " + ChatColor.GREEN + inst.getAliveCount();
        try { p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new net.md_5.bungee.api.chat.TextComponent(msg)); } catch (Throwable ignored) {}
    }
}
