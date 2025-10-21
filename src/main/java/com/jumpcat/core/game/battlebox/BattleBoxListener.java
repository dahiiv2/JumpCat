package com.jumpcat.core.game.battlebox;

import com.jumpcat.core.points.PointsService;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Objects;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BattleBoxListener implements Listener {
    private final BattleBoxManager manager;
    private final BattleBoxController controller;
    private final PointsService points;
    private final org.bukkit.plugin.Plugin plugin;
    // Track last damager per victim with timestamp to award kill on quit
    private static class LastHit { UUID attacker; long when; LastHit(UUID a, long w){ attacker=a; when=w; } }
    private final Map<UUID, LastHit> lastDamager = new ConcurrentHashMap<>();
    // Track cumulative recent damage dealt to a victim by attackers for assist awards
    private static class HitAgg { double sum; long last; HitAgg(double s,long l){sum=s;last=l;} }
    private final Map<UUID, Map<UUID, HitAgg>> damageDealt = new ConcurrentHashMap<>(); // victim -> (attacker -> agg)

    public BattleBoxListener(BattleBoxManager manager, BattleBoxController controller, PointsService points, org.bukkit.plugin.Plugin plugin) {
        this.manager = manager;
        this.controller = controller;
        this.points = points;
        this.plugin = plugin;
    }

    private boolean inBBWorld(World w) {
        World bb = manager.getWorld();
        return bb != null && Objects.equals(bb.getName(), w.getName());
    }

    private boolean isPrepFreeze(Player p) {
        // Single-runtime mode
        BattleBoxRuntime rt = controller.getCurrentRuntime();
        if (rt != null) return !rt.live && rt.isParticipant(p.getUniqueId());
        // Series mode: check any active runtime containing this player
        for (BattleBoxRuntime r : controller.getActiveRuntimes()) {
            if (r.isParticipant(p.getUniqueId())) return !r.live;
        }
        return false;
    }

    private boolean isLivePvpAllowed(Player a, Player b, Location where) {
        // Single-runtime mode
        BattleBoxRuntime rt = controller.getCurrentRuntime();
        if (rt != null) {
            if (!rt.live) return false;
            if (!rt.isParticipant(a.getUniqueId()) || !rt.isParticipant(b.getUniqueId())) return false;
            if (rt.sideOf(a.getUniqueId()) == null || rt.sideOf(b.getUniqueId()) == null) return false;
            if (rt.sideOf(a.getUniqueId()).equals(rt.sideOf(b.getUniqueId()))) return false; // same team
            return true;
        }
        // Series mode: both must belong to the same live runtime and opposing sides
        BattleBoxRuntime ra = null, rb = null;
        for (BattleBoxRuntime r : controller.getActiveRuntimes()) {
            if (r.isParticipant(a.getUniqueId())) ra = r;
            if (r.isParticipant(b.getUniqueId())) rb = r;
        }
        if (ra == null || rb == null || ra != rb) return false;
        if (!ra.live) return false;
        if (ra.sideOf(a.getUniqueId()) == null || ra.sideOf(b.getUniqueId()) == null) return false;
        if (Objects.equals(ra.sideOf(a.getUniqueId()), ra.sideOf(b.getUniqueId()))) return false;
        return true;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!inBBWorld(p.getWorld())) return;
        if (isPrepFreeze(p)) {
            if (e.getFrom().getBlockX() != e.getTo().getBlockX() ||
                e.getFrom().getBlockY() != e.getTo().getBlockY() ||
                e.getFrom().getBlockZ() != e.getTo().getBlockZ()) {
                e.setTo(e.getFrom());
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player victim = (Player) e.getEntity();
        if (!inBBWorld(victim.getWorld())) return;

        // Handle player vs player damage
        if (e.getDamager() instanceof Player) {
            Player attacker = (Player) e.getDamager();
            if (isPrepFreeze(attacker) || isPrepFreeze(victim)) { e.setCancelled(true); return; }

            if (!isLivePvpAllowed(attacker, victim, victim.getLocation())) {
                e.setCancelled(true);
            } else {
                // Record last damager if the hit is allowed
                lastDamager.put(victim.getUniqueId(), new LastHit(attacker.getUniqueId(), System.currentTimeMillis()));
                // Accumulate damage for assists (use final damage)
                double dmg = e.getFinalDamage();
                Map<UUID, HitAgg> m = damageDealt.computeIfAbsent(victim.getUniqueId(), k -> new ConcurrentHashMap<>());
                m.merge(attacker.getUniqueId(), new HitAgg(dmg, System.currentTimeMillis()), (oldV, newV) -> { oldV.sum += newV.sum; oldV.last = newV.last; return oldV; });
            }
        }
        // Handle projectile damage (arrows, tridents, snowballs, etc.)
        else if (e.getDamager() instanceof org.bukkit.entity.Projectile) {
            org.bukkit.entity.Projectile projectile = (org.bukkit.entity.Projectile) e.getDamager();
            if (projectile.getShooter() instanceof Player) {
                Player shooter = (Player) projectile.getShooter();
                if (isPrepFreeze(shooter) || isPrepFreeze(victim)) { e.setCancelled(true); return; }

                if (!isLivePvpAllowed(shooter, victim, victim.getLocation())) {
                    e.setCancelled(true);
                } else {
                    // Record last damager if the hit is allowed
                    lastDamager.put(victim.getUniqueId(), new LastHit(shooter.getUniqueId(), System.currentTimeMillis()));
                    // Accumulate damage for assists (use final damage)
                    double dmg = e.getFinalDamage();
                    Map<UUID, HitAgg> m = damageDealt.computeIfAbsent(victim.getUniqueId(), k -> new ConcurrentHashMap<>());
                    m.merge(shooter.getUniqueId(), new HitAgg(dmg, System.currentTimeMillis()), (oldV, newV) -> { oldV.sum += newV.sum; oldV.last = newV.last; return oldV; });
                }
            }
        }
    }

    // Track fishing rod hooks for kill attribution
    @EventHandler
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_ENTITY) return;
        if (!(e.getCaught() instanceof Player)) return;
        
        Player fisher = e.getPlayer();
        Player hooked = (Player) e.getCaught();
        if (!inBBWorld(fisher.getWorld()) || !inBBWorld(hooked.getWorld())) return;
        
        // Check prep freeze and PvP validation (same as damage)
        if (isPrepFreeze(fisher) || isPrepFreeze(hooked)) return;
        if (!isLivePvpAllowed(fisher, hooked, hooked.getLocation())) return;
        
        // Record the fisher as the last damager for kill attribution
        lastDamager.put(hooked.getUniqueId(), new LastHit(fisher.getUniqueId(), System.currentTimeMillis()));
    }

    @EventHandler
    public void onAnyDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        if (!inBBWorld(p.getWorld())) return;
        if (isPrepFreeze(p)) { e.setCancelled(true); }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (!inBBWorld(e.getPlayer().getWorld())) return;
        Player p = e.getPlayer();
        // Only allow breaking when the game is NOT running and the player is OP in CREATIVE (for arena building)
        boolean gameActive = controller.isRunning() || controller.isSeriesRunning();
        boolean allowed = !gameActive && p.isOp() && p.getGameMode() == GameMode.CREATIVE;
        if (!allowed) e.setCancelled(true);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (!inBBWorld(e.getPlayer().getWorld())) return;
        Player p = e.getPlayer();
        // Only allow placing when the game is NOT running and the player is OP in CREATIVE (for arena building)
        boolean gameActive = controller.isRunning() || controller.isSeriesRunning();
        boolean allowed = !gameActive && p.isOp() && p.getGameMode() == GameMode.CREATIVE;
        if (!allowed) e.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (!inBBWorld(e.getPlayer().getWorld())) return;
        // Disallow drops for everyone in battle_box world
        e.setCancelled(true);
    }

    @EventHandler
    public void onPickup(PlayerAttemptPickupItemEvent e) {
        if (!inBBWorld(e.getPlayer().getWorld())) return;
        // Disallow pickups for everyone in battle_box world to avoid clutter
        e.setCancelled(true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        if (!inBBWorld(victim.getWorld())) return;
        BattleBoxRuntime rt = controller.getCurrentRuntime();
        if (rt == null) {
            // series: find player's runtime
            for (BattleBoxRuntime r : controller.getActiveRuntimes()) { if (r.isParticipant(victim.getUniqueId())) { rt = r; break; } }
        }
        if (rt == null || !rt.isParticipant(victim.getUniqueId())) return;

        if (victim.getKiller() != null) {
            points.addPoints(victim.getKiller().getUniqueId(), 75);
            try { victim.getKiller().sendMessage(org.bukkit.ChatColor.AQUA + "+75 Points"); } catch (Throwable ignored) {}
        }
        // Prevent item drops in the world
        e.getDrops().clear();
        rt.markDead(victim.getUniqueId());
        // Award assists (>= 8.0 total damage) to others except killer
        UUID killerId = victim.getKiller() != null ? victim.getKiller().getUniqueId() : null;
        Map<UUID, HitAgg> contrib = damageDealt.remove(victim.getUniqueId());
        if (contrib != null) {
            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, HitAgg> entry : contrib.entrySet()) {
                UUID attackerId = entry.getKey();
                if (killerId != null && killerId.equals(attackerId)) continue;
                HitAgg agg = entry.getValue();
                if (agg.sum >= 8.0 && now - agg.last <= 12_000L) {
                    points.addPoints(attackerId, 25);
                    Player ap = victim.getServer().getPlayer(attackerId);
                    if (ap != null) {
                        try { ap.sendMessage(org.bukkit.ChatColor.AQUA + "+25 Points (assist)"); } catch (Throwable ignored) {}
                    }
                }
            }
        }
        final BattleBoxRuntime frt = rt;
        new BukkitRunnable() { @Override public void run() {
            victim.setGameMode(GameMode.SPECTATOR);
            if (frt.arena != null && frt.arena.spectatorSpawn != null) {
                victim.teleport(frt.arena.spectatorSpawn);
            }
        }}.runTask(plugin);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player victim = e.getPlayer();
        if (!inBBWorld(victim.getWorld())) return;
        // Resolve runtime (single or series)
        BattleBoxRuntime rt = controller.getCurrentRuntime();
        if (rt == null) {
            for (BattleBoxRuntime r : controller.getActiveRuntimes()) { if (r.isParticipant(victim.getUniqueId())) { rt = r; break; } }
        }
        if (rt == null || !rt.isParticipant(victim.getUniqueId())) return;
        // Treat as death (no killer, no +75), move to spectator
        rt.markDead(victim.getUniqueId());
        // If we have a recent last damager (within 10s), award them the kill points
        LastHit lh = lastDamager.get(victim.getUniqueId());
        if (lh != null && System.currentTimeMillis() - lh.when <= 10_000L) {
            points.addPoints(lh.attacker, 75);
            Player killer = victim.getServer().getPlayer(lh.attacker);
            if (killer != null) {
                try { killer.sendMessage(org.bukkit.ChatColor.AQUA + "+75 Points (opponent disconnected)"); } catch (Throwable ignored) {}
            }
        }
        // Award assists to others except credited killer on quit
        UUID creditedKiller = (lh != null && System.currentTimeMillis() - lh.when <= 10_000L) ? lh.attacker : null;
        Map<UUID, HitAgg> contrib2 = damageDealt.remove(victim.getUniqueId());
        if (contrib2 != null) {
            long now2 = System.currentTimeMillis();
            for (Map.Entry<UUID, HitAgg> entry : contrib2.entrySet()) {
                UUID attackerId = entry.getKey();
                if (creditedKiller != null && creditedKiller.equals(attackerId)) continue;
                HitAgg agg = entry.getValue();
                if (agg.sum >= 8.0 && now2 - agg.last <= 12_000L) {
                    points.addPoints(attackerId, 25);
                    Player ap = victim.getServer().getPlayer(attackerId);
                    if (ap != null) {
                        try { ap.sendMessage(org.bukkit.ChatColor.AQUA + "+25 Points (assist)"); } catch (Throwable ignored) {}
                    }
                }
            }
        }
        final BattleBoxRuntime frt2 = rt;
        new BukkitRunnable() { @Override public void run() {
            try {
                victim.setGameMode(GameMode.SPECTATOR);
                if (frt2.arena != null && frt2.arena.spectatorSpawn != null) {
                    victim.teleport(frt2.arena.spectatorSpawn);
                }
            } catch (Throwable ignored) {}
        }}.runTask(plugin);
    }
}
