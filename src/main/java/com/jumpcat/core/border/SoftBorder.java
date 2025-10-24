package com.jumpcat.core.border;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class SoftBorder {
    private final Plugin plugin;
    private final World world;
    private final Location center;
    private final double startRadius;
    private final double endRadius;
    private final int durationTicks;
    private final int enforcePeriod; // ticks
    private final int particlePeriod; // ticks
    private final double baseDps; // health per second (2.0 = 1 heart/sec)
    private final double maxDps;  // health per second when far outside
    private final double maxOutsideForMaxDps; // blocks outside for maxDps
    private final boolean showParticles;

    private BukkitRunnable enforceTask;
    private BukkitRunnable particleTask;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private static final Particle.DustOptions BORDER_DUST = new Particle.DustOptions(Color.RED, 1.0f);
    private final java.util.Map<java.util.UUID, org.bukkit.boss.BossBar> bars = new java.util.HashMap<>();

    public SoftBorder(Plugin plugin, World world, Location center,
                      double startRadius, double endRadius, int durationTicks,
                      int enforcePeriod, int particlePeriod,
                      double baseDps, double maxDps, double maxOutsideForMaxDps,
                      boolean showParticles) {
        this.plugin = plugin;
        this.world = world;
        this.center = center.clone();
        this.startRadius = Math.max(1.0, startRadius);
        this.endRadius = Math.max(1.0, endRadius);
        this.durationTicks = Math.max(1, durationTicks);
        this.enforcePeriod = Math.max(1, enforcePeriod);
        this.particlePeriod = Math.max(1, particlePeriod);
        this.baseDps = Math.max(0.0, baseDps);
        this.maxDps = Math.max(this.baseDps, maxDps);
        this.maxOutsideForMaxDps = Math.max(0.1, maxOutsideForMaxDps);
        this.showParticles = showParticles;
    }

    private double radiusAt(int t) {
        double f = Math.min(1.0, Math.max(0.0, (double) t / durationTicks));
        return startRadius + (endRadius - startRadius) * f;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) return;
        // Enforcement task (damage-only)
        enforceTask = new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                // Stop if world unloaded or plugin disabled
                if (!plugin.isEnabled() || Bukkit.getWorld(world.getUID()) == null) { cancel(); return; }
                if (t > durationTicks) t = durationTicks; // hold at end
                double radius = radiusAt(t);
                double seconds = enforcePeriod / 20.0;
                for (Player p : world.getPlayers()) {
                    Location loc = p.getLocation();
                    double dx = loc.getX() - center.getX();
                    double dz = loc.getZ() - center.getZ();
                    double dist = Math.sqrt(dx*dx + dz*dz);
                    double outside = dist - radius;
                    if (outside > 0.0) {
                        double factor = Math.min(1.0, outside / maxOutsideForMaxDps);
                        double dps = baseDps + (maxDps - baseDps) * factor;
                        double damage = dps * seconds;
                        try { p.damage(damage); } catch (Throwable ignored) {}
                    }
                }
                t += enforcePeriod;
            }
        };
        enforceTask.runTaskTimer(plugin, 0L, enforcePeriod);

        if (showParticles) {
            particleTask = new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    if (!plugin.isEnabled() || Bukkit.getWorld(world.getUID()) == null) { cancel(); return; }
                    if (t > durationTicks) t = durationTicks; // hold at end
                    double radius = radiusAt(t);
                    // Per-player: only if within ~25 blocks of the border ring, and only send to that player
                    for (Player p : world.getPlayers()) {
                        Location loc = p.getLocation();
                        double dx = loc.getX() - center.getX();
                        double dz = loc.getZ() - center.getZ();
                        double dist = Math.sqrt(dx*dx + dz*dz);
                        double delta = Math.abs(dist - radius);
                        // BossBar indicator within 30 blocks of the ring
                        try {
                            double near = 30.0;
                            if (delta <= near) {
                                org.bukkit.boss.BossBar bar = bars.get(p.getUniqueId());
                                String title = org.bukkit.ChatColor.RED + "Border: " + org.bukkit.ChatColor.WHITE + (int)Math.round(delta) + "m";
                                if (bar == null) {
                                    bar = org.bukkit.Bukkit.createBossBar(title, org.bukkit.boss.BarColor.RED, org.bukkit.boss.BarStyle.SEGMENTED_10);
                                    bar.setProgress(Math.max(0.0, Math.min(1.0, 1.0 - (delta / near))));
                                    bar.addPlayer(p);
                                    bars.put(p.getUniqueId(), bar);
                                } else {
                                    bar.setTitle(title);
                                    bar.setProgress(Math.max(0.0, Math.min(1.0, 1.0 - (delta / near))));
                                    if (!bar.getPlayers().contains(p)) bar.addPlayer(p);
                                }
                                bar.setVisible(true);
                            } else {
                                org.bukkit.boss.BossBar bar = bars.remove(p.getUniqueId());
                                if (bar != null) { bar.removeAll(); }
                            }
                        } catch (Throwable ignored) {}
                        if (delta > 20.0) continue;
                        if (dist < 1e-6) continue; // avoid NaN / zero division at exact center
                        double scale = radius / dist;
                        double x = center.getX() + dx * scale;
                        double z = center.getZ() + dz * scale;
                        double y = Math.max(50.0, Math.min(130.0, loc.getY() + 0.5));
                        try {
                            org.bukkit.Particle pr = org.bukkit.Particle.valueOf("REDSTONE");
                            // Draw a much wider arc along the tangent with five vertical rows for very clear visibility
                            // Tangent unit vector: (-dz/dist, dx/dist)
                            double tx = -dz / dist, tz = dx / dist;
                            double[] offs = new double[]{ -8.0, -6.0, -4.0, -2.0, 0.0, 2.0, 4.0, 6.0, 8.0 }; // much wider arc
                            double y2 = Math.max(50.0, Math.min(130.0, y + 1.0));
                            double y3 = Math.max(50.0, Math.min(130.0, y + 2.0));
                            double y4 = Math.max(50.0, Math.min(130.0, y + 3.0));
                            double y5 = Math.max(50.0, Math.min(130.0, y + 4.0));
                            for (double o : offs) {
                                double px = x + tx * o;
                                double pz = z + tz * o;
                                p.spawnParticle(pr, px, y,  pz, 1, 0.0, 0.0, 0.0, 0.0, BORDER_DUST);
                                p.spawnParticle(pr, px, y2, pz, 1, 0.0, 0.0, 0.0, 0.0, BORDER_DUST);
                                p.spawnParticle(pr, px, y3, pz, 1, 0.0, 0.0, 0.0, 0.0, BORDER_DUST);
                                p.spawnParticle(pr, px, y4, pz, 1, 0.0, 0.0, 0.0, 0.0, BORDER_DUST);
                                p.spawnParticle(pr, px, y5, pz, 1, 0.0, 0.0, 0.0, 0.0, BORDER_DUST);
                            }
                        } catch (Throwable ignored) {}
                    }
                    t += particlePeriod;
                }
            };
            particleTask.runTaskTimer(plugin, 0L, Math.max(5, particlePeriod)); // at most every 5 ticks
        }
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        try { if (enforceTask != null) enforceTask.cancel(); } catch (Throwable ignored) {}
        try { if (particleTask != null) particleTask.cancel(); } catch (Throwable ignored) {}
        // Cleanup any active bossbars
        try { for (org.bukkit.boss.BossBar b : new java.util.ArrayList<>(bars.values())) { try { b.removeAll(); } catch (Throwable ignored) {} } } catch (Throwable ignored) {}
        try { bars.clear(); } catch (Throwable ignored) {}
    }

    
}
