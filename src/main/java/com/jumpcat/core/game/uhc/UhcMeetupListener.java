package com.jumpcat.core.game.uhc;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Particle;
import org.bukkit.ChatColor;

import java.util.HashSet;
import java.util.Set;

public class UhcMeetupListener implements Listener {
    private final UhcMeetupConfig cfg;
    private final Set<Material> whitelist = new HashSet<>();
    private static volatile int currentCeilY = Integer.MAX_VALUE;
    private static volatile String activeWorldPrefix = "uhc_meetup_r";
    private final java.util.Map<java.util.UUID, Long> lastCeilDamage = new java.util.HashMap<>();
    // Combat tracking (reuse BB approach)
    private static class LastHit { java.util.UUID attacker; long when; LastHit(java.util.UUID a,long w){ attacker=a; when=w; } }
    private final java.util.Map<java.util.UUID, LastHit> lastDamager = new java.util.concurrent.ConcurrentHashMap<>();
    private static class HitAgg { double sum; long last; HitAgg(double s,long l){ sum=s; last=l; } }
    private final java.util.Map<java.util.UUID, java.util.Map<java.util.UUID, HitAgg>> damageDealt = new java.util.concurrent.ConcurrentHashMap<>(); // victim -> (attacker -> agg)
    private final java.util.Map<java.util.UUID, String> lastDeathWorld = new java.util.concurrent.ConcurrentHashMap<>();

    public UhcMeetupListener(UhcMeetupConfig cfg) {
        this.cfg = cfg;
        for (String key : cfg.whitelistBlocks) {
            try { whitelist.add(Material.valueOf(key)); } catch (IllegalArgumentException ignored) {}
        }
    }
    // Golden Head consume: grant 8s Regeneration II
    @EventHandler
    public void onConsume(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        if (!isUhcWorld(p.getWorld())) return;
        ItemStack it = e.getItem();
        if (it == null || it.getType() != Material.GOLDEN_APPLE) return;
        try {
            ItemMeta meta = it.getItemMeta();
            if (meta != null) {
                boolean isHead = false;
                String name = meta.getDisplayName();
                if (name != null && name.equals(ChatColor.GOLD + "Golden Head")) isHead = true;
                if (!isHead && meta.getLore() != null) {
                    for (String line : meta.getLore()) { if (line != null && line.contains("UHC Meetup")) { isHead = true; break; } }
                }
                if (isHead) {
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION, 8*20, 1, true, true));
                }
            }
        } catch (Throwable ignored) {}
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        String wname = lastDeathWorld.remove(p.getUniqueId());
        if (wname == null) return;
        org.bukkit.World w = p.getServer().getWorld(wname);
        if (w == null) return;
        if (!isUhcWorld(w)) return;
        // Force respawn location to UHC world spawn and spectator mode
        try { e.setRespawnLocation(w.getSpawnLocation()); } catch (Throwable ignored) {}
        try { p.setGameMode(org.bukkit.GameMode.SPECTATOR); } catch (Throwable ignored) {}
        // Ensure final teleport after respawn tick
        try { new org.bukkit.scheduler.BukkitRunnable(){ @Override public void run(){ try { p.teleport(w.getSpawnLocation()); p.setGameMode(org.bukkit.GameMode.SPECTATOR);} catch (Throwable ignored) {} } }.runTask(com.jumpcat.core.JumpCatPlugin.getPlugin(com.jumpcat.core.JumpCatPlugin.class)); } catch (Throwable ignored) {}
    }

    public static void setCeilingY(int y) { currentCeilY = y; }
    public static int getCeilingY() { return currentCeilY; }

    private boolean isUhcWorld(World w) {
        if (w == null) return false;
        String n = w.getName();
        return n.startsWith("uhc_meetup_r");
    }

    // Only allow placing whitelisted blocks in UHC worlds
    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (!isUhcWorld(e.getBlock().getWorld())) return;
        if (!whitelist.contains(e.getBlockPlaced().getType())) {
            e.setCancelled(true);
        }
    }

    // Only allow breaking whitelisted blocks in UHC worlds
    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (!isUhcWorld(e.getBlock().getWorld())) return;
        if (!whitelist.contains(e.getBlock().getType())) {
            e.setCancelled(true);
        }
    }

    // Prevent hostile mobs spawning in UHC worlds
    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent e) {
        if (!isUhcWorld(e.getLocation().getWorld())) return;
        if (e.getEntity() instanceof Monster) {
            e.setCancelled(true);
        }
        // Periodic tick to damage players above ceiling even when not moving (only when world PvP is enabled)
        try {
            new org.bukkit.scheduler.BukkitRunnable(){ @Override public void run(){
                try {
                    for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                        World w = p.getWorld();
                        if (!isUhcWorld(w)) continue;
                        try { if (!w.getPVP()) continue; } catch (Throwable ignored) {}
                        int ceil = currentCeilY;
                        if (p.getLocation().getY() > ceil) {
                            try { w.spawnParticle(Particle.CLOUD, p.getLocation().getX(), ceil + 0.2, p.getLocation().getZ(), 6, 0.6, 0.0, 0.6, 0.0); } catch (Throwable ignored) {}
                            long now = System.currentTimeMillis();
                            Long last = lastCeilDamage.get(p.getUniqueId());
                            if (last == null || now - last >= 700) {
                                lastCeilDamage.put(p.getUniqueId(), now);
                                try { p.damage(1.0); } catch (Throwable ignored) {}
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            } }.runTaskTimer(com.jumpcat.core.JumpCatPlugin.getPlugin(com.jumpcat.core.JumpCatPlugin.class), 0, 20);
        } catch (Throwable ignored) {}
    }

    // Damage players above the current Y ceiling and show particles
    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        World w = p.getWorld();
        if (!isUhcWorld(w)) return;
        try { if (!w.getPVP()) return; } catch (Throwable ignored) {}
        int ceil = currentCeilY;
        if (p.getLocation().getY() > ceil) {
            // spawn a light particle ring at ceiling near player XZ
            try { w.spawnParticle(Particle.CLOUD, p.getLocation().getX(), ceil + 0.2, p.getLocation().getZ(), 6, 0.6, 0.0, 0.6, 0.0); } catch (Throwable ignored) {}
            long now = System.currentTimeMillis();
            Long last = lastCeilDamage.get(p.getUniqueId());
            if (last == null || now - last >= 700) {
                lastCeilDamage.put(p.getUniqueId(), now);
                try { p.damage(1.0); } catch (Throwable ignored) {}
            }
        }
    }

    // Prevent Power on bows via enchanting table
    @EventHandler
    public void onEnchant(EnchantItemEvent e) {
        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.BOW) return;
        if (e.getEnchantsToAdd().containsKey(Enchantment.POWER)) {
            e.getEnchantsToAdd().remove(Enchantment.POWER);
        }
    }

    // Prevent Power on bows via anvil (book or item combine)
    @EventHandler
    public void onAnvil(PrepareAnvilEvent e) {
        ItemStack result = e.getResult();
        if (result == null || result.getType() != Material.BOW) return;
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;
        if (meta.hasEnchant(Enchantment.POWER)) {
            meta.removeEnchant(Enchantment.POWER);
            result.setItemMeta(meta);
            e.setResult(result);
        }
    }

    // Track damage for last-hitter and assists
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player) || !(e.getDamager() instanceof Player)) return;
        Player victim = (Player) e.getEntity();
        Player attacker = (Player) e.getDamager();
        if (!isUhcWorld(victim.getWorld())) return;
        if (e.isCancelled()) return;
        lastDamager.put(victim.getUniqueId(), new LastHit(attacker.getUniqueId(), System.currentTimeMillis()));
        double dmg = e.getFinalDamage();
        java.util.Map<java.util.UUID, HitAgg> m = damageDealt.computeIfAbsent(victim.getUniqueId(), k -> new java.util.concurrent.ConcurrentHashMap<>());
        m.merge(attacker.getUniqueId(), new HitAgg(dmg, System.currentTimeMillis()), (oldV, newV) -> { oldV.sum += newV.sum; oldV.last = newV.last; return oldV; });
    }

    // Cancel all damage when world PVP is disabled (grace/post-win)
    @EventHandler
    public void onAnyDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        if (!isUhcWorld(p.getWorld())) return;
        try { if (!p.getWorld().getPVP()) { e.setCancelled(true); return; } } catch (Throwable ignored) {}
    }

    // Handle death: scoring via controller and custom drops
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        if (!isUhcWorld(victim.getWorld())) return;
        // Remember world for respawn handling
        try { lastDeathWorld.put(victim.getUniqueId(), victim.getWorld().getName()); } catch (Throwable ignored) {}
        java.util.UUID killerId = victim.getKiller() != null ? victim.getKiller().getUniqueId() : null;
        if (UhcMeetupController.CURRENT != null) {
            UhcMeetupController.CURRENT.onPlayerDeath(victim.getUniqueId(), killerId);
        }
        // Keep natural drops and add custom UHC drops
        try {
            ItemStack head = new ItemStack(Material.GOLDEN_APPLE, 1);
            try {
                ItemMeta meta = head.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.GOLD + "Golden Head");
                    java.util.List<String> lore = new java.util.ArrayList<>();
                    lore.add(ChatColor.GRAY + "UHC Meetup");
                    meta.setLore(lore);
                    head.setItemMeta(meta);
                }
            } catch (Throwable ignored) {}
            e.getDrops().add(head); // Named+Lored so it won't stack with normal gapples
            e.getDrops().add(new ItemStack(Material.DIAMOND, 1));
            e.getDrops().add(new ItemStack(Material.GOLD_INGOT, 4));
            e.getDrops().add(new ItemStack(Material.EXPERIENCE_BOTTLE, 8));
            e.getDrops().add(new ItemStack(Material.ARROW, 8));
        } catch (Throwable ignored) {}
        // Force spectator in-UHC world and move to spawn shortly after death processing
        try { new org.bukkit.scheduler.BukkitRunnable(){ @Override public void run(){ try { victim.setGameMode(org.bukkit.GameMode.SPECTATOR); victim.teleport(victim.getWorld().getSpawnLocation()); } catch (Throwable ignored) {} } }.runTask(com.jumpcat.core.JumpCatPlugin.getPlugin(com.jumpcat.core.JumpCatPlugin.class)); } catch (Throwable ignored) {}
        // Award assists (>= 8.0 damage within 12s) to others except killer
        java.util.UUID creditedKiller = killerId;
        java.util.Map<java.util.UUID, HitAgg> contrib = damageDealt.remove(victim.getUniqueId());
        if (contrib != null) {
            long now = System.currentTimeMillis();
            for (java.util.Map.Entry<java.util.UUID, HitAgg> entry : contrib.entrySet()) {
                java.util.UUID attackerId = entry.getKey();
                if (creditedKiller != null && creditedKiller.equals(attackerId)) continue;
                HitAgg agg = entry.getValue();
                if (agg.sum >= 8.0 && now - agg.last <= 12_000L) {
                    // +25 assist, mirroring BB
                    try { com.jumpcat.core.JumpCatPlugin.getPlugin(com.jumpcat.core.JumpCatPlugin.class).getServer().getPlayer(attackerId).sendMessage(ChatColor.AQUA + "+25 Points (assist)"); } catch (Throwable ignored) {}
                    try { com.jumpcat.core.JumpCatPlugin.getPlugin(com.jumpcat.core.JumpCatPlugin.class).getPointsService().addPoints(attackerId, 25); } catch (Throwable ignored) {}
                }
            }
        }
    }

    // Quit = death (may credit last hitter if within 10s)
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player victim = e.getPlayer();
        if (!isUhcWorld(victim.getWorld())) return;
        java.util.UUID killerId = null;
        LastHit lh = lastDamager.get(victim.getUniqueId());
        if (lh != null && System.currentTimeMillis() - lh.when <= 10_000L) killerId = lh.attacker;
        if (UhcMeetupController.CURRENT != null) {
            UhcMeetupController.CURRENT.onPlayerDeath(victim.getUniqueId(), killerId);
        }
        // Award assists except credited killer
        java.util.UUID creditedKiller = killerId;
        java.util.Map<java.util.UUID, HitAgg> contrib = damageDealt.remove(victim.getUniqueId());
        if (contrib != null) {
            long now = System.currentTimeMillis();
            for (java.util.Map.Entry<java.util.UUID, HitAgg> entry : contrib.entrySet()) {
                java.util.UUID attackerId = entry.getKey();
                if (creditedKiller != null && creditedKiller.equals(attackerId)) continue;
                HitAgg agg = entry.getValue();
                if (agg.sum >= 8.0 && now - agg.last <= 12_000L) {
                    try { com.jumpcat.core.JumpCatPlugin.getPlugin(com.jumpcat.core.JumpCatPlugin.class).getServer().getPlayer(attackerId).sendMessage(ChatColor.AQUA + "+25 Points (assist)"); } catch (Throwable ignored) {}
                    try { com.jumpcat.core.JumpCatPlugin.getPlugin(com.jumpcat.core.JumpCatPlugin.class).getPointsService().addPoints(attackerId, 25); } catch (Throwable ignored) {}
                }
            }
        }
    }
}
