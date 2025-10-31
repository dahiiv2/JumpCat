package com.jumpcat.core.game.skywars;

import com.jumpcat.core.teams.TeamManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.entity.Creeper;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.Location;
import org.bukkit.Bukkit;

import java.util.UUID;

public class SkyWarsListener implements Listener {
    private final SkyWarsController controller;
    private final TeamManager teams;
    // Combat tracking similar to UHC
    private static class LastHit { java.util.UUID attacker; long when; LastHit(java.util.UUID a,long w){ attacker=a; when=w; } }
    private final java.util.Map<java.util.UUID, LastHit> lastDamager = new java.util.concurrent.ConcurrentHashMap<>();
    private static class HitAgg { double sum; long last; HitAgg(double s,long l){ sum=s; last=l; } }
    private final java.util.Map<java.util.UUID, java.util.Map<java.util.UUID, HitAgg>> damageDealt = new java.util.concurrent.ConcurrentHashMap<>(); // victim -> (attacker -> agg)

    // Helper for guaranteed spawn-egg creeper owner
    private final java.util.Map<Location, UUID> pendingEggSpawns = new java.util.HashMap<>();
    private final java.util.Map<Location, Long> pendingEggTimes = new java.util.HashMap<>();

    public SkyWarsListener(SkyWarsController controller, TeamManager teams) {
        this.controller = controller;
        this.teams = teams;
    }

    private boolean inSkywars(World w) {
        if (w == null) return false;
        String n = w.getName();
        return n != null && n.startsWith("skywars_r");
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (!inSkywars(p.getWorld())) return;
        // Items drop by default; just mark elimination
        try { p.setGameMode(org.bukkit.GameMode.SPECTATOR); } catch (Throwable ignored) {}
        java.util.UUID killerId = p.getKiller() != null ? p.getKiller().getUniqueId() : null;
        if (SkyWarsController.CURRENT != null) {
            SkyWarsController.CURRENT.onPlayerDeath(p.getUniqueId(), killerId);
        }
        // Award assists (>= 8 dmg within 12s) to others except credited killer
        java.util.UUID creditedKiller = killerId;
        java.util.Map<java.util.UUID, HitAgg> contrib = damageDealt.remove(p.getUniqueId());
        if (contrib != null) {
            long now = System.currentTimeMillis();
            for (java.util.Map.Entry<java.util.UUID, HitAgg> entry : contrib.entrySet()) {
                java.util.UUID attackerId = entry.getKey();
                if (creditedKiller != null && creditedKiller.equals(attackerId)) continue;
                HitAgg agg = entry.getValue();
                if (agg.sum >= 8.0 && now - agg.last <= 12_000L) {
                    try { p.getServer().getPlayer(attackerId).sendMessage(org.bukkit.ChatColor.AQUA + "+25 Points (assist)"); } catch (Throwable ignored) {}
                    try { com.jumpcat.core.JumpCatPlugin.getPlugin(com.jumpcat.core.JumpCatPlugin.class).getPointsService().addPoints(attackerId, 25); } catch (Throwable ignored) {}
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (!inSkywars(p.getWorld())) return;
        // Clear any pre-start freeze effects to avoid lingering after quitting
        try { p.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS); } catch (Throwable ignored) {}
        try { p.removePotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST); } catch (Throwable ignored) {}
        try { p.setAllowFlight(false); } catch (Throwable ignored) {}
        java.util.UUID killerId = null;
        LastHit lh = lastDamager.get(p.getUniqueId());
        if (lh != null && System.currentTimeMillis() - lh.when <= 10_000L) killerId = lh.attacker;
        if (SkyWarsController.CURRENT != null) {
            SkyWarsController.CURRENT.onPlayerDeath(p.getUniqueId(), killerId);
        }
        // Broadcast logout elimination message (SkyWars-specific path)
        try {
            String victimTeamKey = teams.getPlayerTeamKey(p).orElse("");
            String victimLabel = victimTeamKey.isEmpty() ? "" : ("" + teams.getTeamColor(victimTeamKey) + ChatColor.BOLD + teams.getTeamLabel(victimTeamKey) + ChatColor.RESET + ChatColor.WHITE + " ");
            String victimName = ChatColor.WHITE + p.getName();
            if (killerId != null) {
                Player killer = p.getServer().getPlayer(killerId);
                if (killer != null) {
                    String killerTeamKey = teams.getPlayerTeamKey(killer).orElse("");
                    String killerLabel = killerTeamKey.isEmpty() ? "" : ("" + teams.getTeamColor(killerTeamKey) + ChatColor.BOLD + teams.getTeamLabel(killerTeamKey) + ChatColor.RESET + ChatColor.WHITE + " ");
                    String killerName = ChatColor.WHITE + killer.getName();
                    p.getServer().broadcastMessage(victimLabel + victimName + ChatColor.GRAY + " » " + ChatColor.RED + "killed by" + ChatColor.GRAY + " » " + killerLabel + killerName + ChatColor.DARK_GRAY + " (logout)");
                }
            } else {
                p.getServer().broadcastMessage(victimLabel + victimName + ChatColor.GRAY + " » " + ChatColor.RED + "died" + ChatColor.DARK_GRAY + " (logout)");
            }
        } catch (Throwable ignored) {}
        // Update K/D sidebar centrally for logout (no PlayerDeathEvent)
        try { com.jumpcat.core.combat.CombatService.recordElimination(p.getUniqueId(), killerId); } catch (Throwable ignored) {}
        // Award assists except credited killer
        java.util.UUID creditedKiller = killerId;
        java.util.Map<java.util.UUID, HitAgg> contrib = damageDealt.remove(p.getUniqueId());
        if (contrib != null) {
            long now = System.currentTimeMillis();
            for (java.util.Map.Entry<java.util.UUID, HitAgg> entry : contrib.entrySet()) {
                java.util.UUID attackerId = entry.getKey();
                if (creditedKiller != null && creditedKiller.equals(attackerId)) continue;
                HitAgg agg = entry.getValue();
                if (agg.sum >= 8.0 && now - agg.last <= 12_000L) {
                    try { p.getServer().getPlayer(attackerId).sendMessage(org.bukkit.ChatColor.AQUA + "+25 Points (assist)"); } catch (Throwable ignored) {}
                    try { com.jumpcat.core.JumpCatPlugin.getPlugin(com.jumpcat.core.JumpCatPlugin.class).getPointsService().addPoints(attackerId, 25); } catch (Throwable ignored) {}
                }
            }
        }
    }

    @EventHandler
    public void onBlockDrop(BlockDropItemEvent e) {
        if (!inSkywars(e.getBlock().getWorld())) return;
        // Suppress all block drops in SkyWars
        e.setCancelled(true);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (!inSkywars(e.getBlock().getWorld())) return;
        // Suppress vanilla drops
        try { e.setDropItems(false); } catch (Throwable ignored) {}
        // Custom drops for specific blocks
        Material bt = e.getBlock().getType();
        org.bukkit.Location loc = e.getBlock().getLocation().add(0.5, 0.0, 0.5);
        org.bukkit.World w = e.getBlock().getWorld();
        if (bt == Material.DIAMOND_ORE || bt == Material.DEEPSLATE_DIAMOND_ORE) {
            try { w.dropItemNaturally(loc, new ItemStack(Material.DIAMOND, 1)); } catch (Throwable ignored) {}
        } else if (bt == Material.IRON_BLOCK) {
            try { w.dropItemNaturally(loc, new ItemStack(Material.IRON_BLOCK, 1)); } catch (Throwable ignored) {}
        } else if (bt == Material.GOLD_BLOCK) {
            try { w.dropItemNaturally(loc, new ItemStack(Material.GOLD_BLOCK, 1)); } catch (Throwable ignored) {}
        } else if (bt == Material.CRAFTING_TABLE) {
            try { w.dropItemNaturally(loc, new ItemStack(Material.CRAFTING_TABLE, 1)); } catch (Throwable ignored) {}
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (!inSkywars(p.getWorld())) return;
        ItemStack inHand = e.getItemInHand();
        if (inHand == null) return;
        org.bukkit.inventory.EquipmentSlot hand = null;
        try { hand = e.getHand(); } catch (Throwable ignored) {}
        // TNT: prime on place instead of placing a block
        if (inHand.getType() == Material.TNT) {
            e.setCancelled(true);
            // Consume one TNT unless creative
            if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                int amt = inHand.getAmount();
                if (amt > 1) { inHand.setAmount(amt - 1); }
                else { inHand = new ItemStack(Material.AIR); }
                // write back to the same hand
                if (hand == org.bukkit.inventory.EquipmentSlot.OFF_HAND) { p.getInventory().setItemInOffHand(inHand); }
                else { p.getInventory().setItemInMainHand(inHand); }
            }
            org.bukkit.Location l = e.getBlockPlaced().getLocation().add(0.5, 0.0, 0.5);
            try {
                org.bukkit.entity.TNTPrimed t = p.getWorld().spawn(l, org.bukkit.entity.TNTPrimed.class, primed -> {
                    try { primed.setFuseTicks(40); } catch (Throwable ignored) {}
                    try { primed.setSource(p); } catch (Throwable ignored) {}
                });
            } catch (Throwable ignored) {}
            return;
        }
        // Infinite terracotta: refill same hand to 64 (survival only)
        Material m = inHand.getType();
        if (!isTerracotta(m)) return;
        if (p.getGameMode() == org.bukkit.GameMode.SURVIVAL) {
            // schedule 1 tick later to avoid fighting with vanilla stack decrement visuals
            final org.bukkit.inventory.EquipmentSlot usedHand = hand;
            final Material usedMat = m;
            try { new org.bukkit.scheduler.BukkitRunnable(){ @Override public void run(){ try {
                        if (usedHand == org.bukkit.inventory.EquipmentSlot.OFF_HAND) { p.getInventory().setItemInOffHand(new ItemStack(usedMat, 64)); }
                        else { p.getInventory().setItemInMainHand(new ItemStack(usedMat, 64)); }
                        p.updateInventory();
                    } catch (Throwable ignored) {} } }.runTask(com.jumpcat.core.JumpCatPlugin.getPlugin(com.jumpcat.core.JumpCatPlugin.class)); } catch (Throwable ignored) {}
        }
    }

    private boolean isTerracotta(Material m) {
        String n = m.name();
        return n.endsWith("_TERRACOTTA") || n.equals("TERRACOTTA");
    }

    // Helper: Set metadata for entity ownership
    private void setSkywarsSpawner(org.bukkit.entity.Entity entity, Player owner) {
        try { entity.setMetadata("skywars_spawner", new FixedMetadataValue(com.jumpcat.core.JumpCatPlugin.getPlugin(com.jumpcat.core.JumpCatPlugin.class), owner.getUniqueId().toString())); } catch (Throwable ignored) {}
    }
    private UUID getSkywarsSpawner(org.bukkit.entity.Entity entity) {
        try {
            if (entity.hasMetadata("skywars_spawner")) {
                for (MetadataValue v : entity.getMetadata("skywars_spawner")) {
                    if (v.getOwningPlugin().getName().equals("JumpCat")) {
                        return UUID.fromString(v.asString());
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    // 1. TNT Minecart: track placer when spawned by player
    @EventHandler(ignoreCancelled = true)
    public void onTntMinecartPlace(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof ExplosiveMinecart)) return;
        if (!inSkywars(e.getRightClicked().getWorld())) return;
        setSkywarsSpawner(e.getRightClicked(), e.getPlayer());
    }

    @EventHandler
    public void onPlayerUseSpawnEgg(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getItem() == null || event.getItem().getType() != Material.CREEPER_SPAWN_EGG) return;
        if (!inSkywars(event.getPlayer().getWorld())) return;
        Location loc;
        if (event.getClickedBlock() != null) {
            loc = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation();
        } else {
            loc = event.getPlayer().getLocation();
        }
        // We allow a small location fuzz since the spawn may be +/-1 block
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++)
                    pendingEggSpawns.put(loc.clone().add(dx, dy, dz), event.getPlayer().getUniqueId());
        pendingEggTimes.put(loc, System.currentTimeMillis());
    }

    @EventHandler
    public void onCreeperEgg(org.bukkit.event.entity.CreatureSpawnEvent e) {
        if (e.getEntity() instanceof Creeper) {
            Creeper creeper = (Creeper) e.getEntity();
            String reason = "";
            try { reason = String.valueOf(e.getSpawnReason()); } catch (Throwable ignored) {}
            boolean egg = reason.equals("SPAWN_EGG") || reason.equals("SPAWNER_EGG") || reason.equals("EGG");
            if (inSkywars(creeper.getWorld()) && egg) {
                // Guarantee: check pendingEggSpawns for matching location (within tick)
                Location cl = creeper.getLocation().getBlock().getLocation();
                UUID owner = null;
                for (int dx = -1; dx <= 1 && owner == null; dx++)
                    for (int dy = -1; dy <= 1 && owner == null; dy++)
                        for (int dz = -1; dz <= 1 && owner == null; dz++) {
                            Location test = cl.clone().add(dx, dy, dz);
                            if (pendingEggSpawns.containsKey(test) && System.currentTimeMillis() - pendingEggTimes.getOrDefault(test, 0L) < 1000L) {//1s fudge
                                owner = pendingEggSpawns.get(test);
                                pendingEggSpawns.remove(test);
                                pendingEggTimes.remove(test);
                            }
                        }
                if (owner != null) setSkywarsSpawner(creeper, Bukkit.getPlayer(owner));
            }
        }
    }

    // 3. KILL ATTRIBUTION
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player victim = (Player) e.getEntity();
        if (!inSkywars(victim.getWorld())) return;
        if (e.isCancelled()) return;

        // Existing player vs player & projectile logic:
        if (e.getDamager() instanceof Player) {
            Player attacker = (Player) e.getDamager();
            lastDamager.put(victim.getUniqueId(), new LastHit(attacker.getUniqueId(), System.currentTimeMillis()));
            double dmg = e.getFinalDamage();
            java.util.Map<java.util.UUID, HitAgg> m = damageDealt.computeIfAbsent(victim.getUniqueId(), k -> new java.util.concurrent.ConcurrentHashMap<>());
            m.merge(attacker.getUniqueId(), new HitAgg(dmg, System.currentTimeMillis()), (oldV, newV) -> { oldV.sum += newV.sum; oldV.last = newV.last; return oldV; });
        } else if (e.getDamager() instanceof org.bukkit.entity.Projectile) {
            org.bukkit.entity.Projectile projectile = (org.bukkit.entity.Projectile) e.getDamager();
            if (projectile.getShooter() instanceof Player) {
                Player shooter = (Player) projectile.getShooter();
                lastDamager.put(victim.getUniqueId(), new LastHit(shooter.getUniqueId(), System.currentTimeMillis()));
                double dmg = e.getFinalDamage();
                java.util.Map<java.util.UUID, HitAgg> m = damageDealt.computeIfAbsent(victim.getUniqueId(), k -> new java.util.concurrent.ConcurrentHashMap<>());
                m.merge(shooter.getUniqueId(), new HitAgg(dmg, System.currentTimeMillis()), (oldV, newV) -> { oldV.sum += newV.sum; oldV.last = newV.last; return oldV; });
            }
        // TNTPrimed: credit source if a player (but not for self-kills)
        } else if (e.getDamager() instanceof TNTPrimed) {
            TNTPrimed tnt = (TNTPrimed) e.getDamager();
            if (tnt.getSource() instanceof Player) {
                Player explosiveOwner = (Player) tnt.getSource();
                // Don't give kill credit for self-kills, but allow damage
                if (!explosiveOwner.getUniqueId().equals(victim.getUniqueId())) {
                    lastDamager.put(victim.getUniqueId(), new LastHit(explosiveOwner.getUniqueId(), System.currentTimeMillis()));
                    double dmg = e.getFinalDamage();
                    java.util.Map<java.util.UUID, HitAgg> m = damageDealt.computeIfAbsent(victim.getUniqueId(), k -> new java.util.concurrent.ConcurrentHashMap<>());
                    m.merge(explosiveOwner.getUniqueId(), new HitAgg(dmg, System.currentTimeMillis()), (oldV, newV) -> { oldV.sum += newV.sum; oldV.last = newV.last; return oldV; });
                }
            }
        // TNTMinecart: use metadata for source (but not for self-kills)
        } else if (e.getDamager() instanceof ExplosiveMinecart) {
            ExplosiveMinecart cart = (ExplosiveMinecart) e.getDamager();
            UUID placerId = getSkywarsSpawner(cart);
            if (placerId != null && !placerId.equals(victim.getUniqueId())) {
                // Don't give kill credit for self-kills, but allow damage
                lastDamager.put(victim.getUniqueId(), new LastHit(placerId, System.currentTimeMillis()));
                double dmg = e.getFinalDamage();
                java.util.Map<java.util.UUID, HitAgg> m = damageDealt.computeIfAbsent(victim.getUniqueId(), k -> new java.util.concurrent.ConcurrentHashMap<>());
                m.merge(placerId, new HitAgg(dmg, System.currentTimeMillis()), (oldV, newV) -> { oldV.sum += newV.sum; oldV.last = newV.last; return oldV; });
            }
        // Creeper: use metadata for source (only for those spawned by egg, but not for self-kills)
        } else if (e.getDamager() instanceof Creeper) {
            Creeper creeper = (Creeper) e.getDamager();
            UUID placerId = getSkywarsSpawner(creeper);
            if (placerId != null && !placerId.equals(victim.getUniqueId())) {
                // Don't give kill credit for self-kills, but allow damage
                lastDamager.put(victim.getUniqueId(), new LastHit(placerId, System.currentTimeMillis()));
                double dmg = e.getFinalDamage();
                java.util.Map<java.util.UUID, HitAgg> m = damageDealt.computeIfAbsent(victim.getUniqueId(), k -> new java.util.concurrent.ConcurrentHashMap<>());
                m.merge(placerId, new HitAgg(dmg, System.currentTimeMillis()), (oldV, newV) -> { oldV.sum += newV.sum; oldV.last = newV.last; return oldV; });
            }
        }
    }

    // Custom: Stronger TNT and Creeper damage (NEEDS TESTING)
    @EventHandler
    public void onTntAndCreeperDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        if (!inSkywars(e.getEntity().getWorld())) return;
        if (e.getDamager() instanceof TNTPrimed || e.getDamager() instanceof Creeper) {
            // Just scale by 1.2x, no minimum floor.
            double base = e.getDamage();
            double scaled = base * 1.3;
             // ALTERNATIVE : double scaled = Math.max(base * 2.2, 14.0) 
             // TNT / Creepers do at least 14 damage points (7 hearts), or if the damage would be more, does the higher value
            e.setDamage(scaled);
        }
    }

    // Track fishing rod hooks for kill attribution
    @EventHandler
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_ENTITY) return;
        if (!(e.getCaught() instanceof Player)) return;
        
        Player fisher = e.getPlayer();
        Player hooked = (Player) e.getCaught();
        if (!inSkywars(fisher.getWorld()) || !inSkywars(hooked.getWorld())) return;
        
        // Record the fisher as the last damager for kill attribution
        lastDamager.put(hooked.getUniqueId(), new LastHit(fisher.getUniqueId(), System.currentTimeMillis()));
    }

    @EventHandler
    public void onAnyDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        if (!inSkywars(p.getWorld())) return;
        try { if (!p.getWorld().getPVP()) { e.setCancelled(true); return; } } catch (Throwable ignored) {}
    }

    // Prevent enabling flight during pre-start; we allow allowFlight=true to avoid kicks but block actual flying
    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        if (!inSkywars(p.getWorld())) return;
        try {
            if (SkyWarsController.CURRENT != null && SkyWarsController.CURRENT.isPreStartActive(p.getWorld())) {
                e.setCancelled(true);
                try { p.setFlying(false); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    // Void kill: eliminate immediately when passing below Y=0
    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!inSkywars(p.getWorld())) return;
        // Pre-start freeze is handled via potion effects in the controller; no movement cancellation here
        if (e.getTo() == null) return;
        if (e.getTo().getY() >= 0.0) return;
        // Determine killer by last hit within 10s (but not self-kills)
        java.util.UUID killerId = null;
        LastHit lh = lastDamager.get(p.getUniqueId());
        if (lh != null && System.currentTimeMillis() - lh.when <= 10_000L && !lh.attacker.equals(p.getUniqueId())) {
            killerId = lh.attacker;
        }
        // Route through Bukkit death pipeline so general listeners handle feedback/messages
        if (killerId != null) {
            org.bukkit.entity.Player killer = p.getServer().getPlayer(killerId);
            if (killer != null) { try { p.damage(1000.0, killer); return; } catch (Throwable ignored) {} }
        }
        try { p.damage(1000.0); return; } catch (Throwable ignored) {}
        // Award assists (>=8 dmg within 12s) to others except credited killer
        java.util.UUID creditedKiller = killerId;
        java.util.Map<java.util.UUID, HitAgg> contrib = damageDealt.remove(p.getUniqueId());
        if (contrib != null) {
            long now = System.currentTimeMillis();
            for (java.util.Map.Entry<java.util.UUID, HitAgg> entry : contrib.entrySet()) {
                java.util.UUID attackerId = entry.getKey();
                if (creditedKiller != null && creditedKiller.equals(attackerId)) continue;
                HitAgg agg = entry.getValue();
                if (agg.sum >= 8.0 && now - agg.last <= 12_000L) {
                    try { p.getServer().getPlayer(attackerId).sendMessage(org.bukkit.ChatColor.AQUA + "+25 Points (assist)"); } catch (Throwable ignored) {}
                    try { com.jumpcat.core.JumpCatPlugin.getPlugin(com.jumpcat.core.JumpCatPlugin.class).getPointsService().addPoints(attackerId, 25); } catch (Throwable ignored) {}
                }
            }
        }
        // Clear last damager to avoid double crediting
        lastDamager.remove(p.getUniqueId());
    }
}
