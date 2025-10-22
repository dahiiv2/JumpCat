package com.jumpcat.core.border;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

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
                    // Render a highly visible vertical ring: dense columns from Y=50 to Y=120
                    int points = 144; // higher angular density
                    double yStart = 50.0;
                    double yEnd = 120.0;
                    int ySteps = 36; // ~2 block spacing across the column
                    for (int i = 0; i < points; i++) {
                        double ang = (2 * Math.PI * i) / points;
                        double x = center.getX() + radius * Math.cos(ang);
                        double z = center.getZ() + radius * Math.sin(ang);
                        for (int s = 0; s < ySteps; s++) {
                            double yy = yStart + (yEnd - yStart) * (s / (double)(ySteps - 1));
                            // END_ROD is bright and visible; spawn 2 per step for clarity
                            try { world.spawnParticle(Particle.END_ROD, x, yy, z, 2, 0.0, 0.0, 0.0, 0.0, null, true); } catch (Throwable ignored) {}
                        }
                    }
                    t += particlePeriod;
                }
            };
            particleTask.runTaskTimer(plugin, 0L, particlePeriod);
        }
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        try { if (enforceTask != null) enforceTask.cancel(); } catch (Throwable ignored) {}
        try { if (particleTask != null) particleTask.cancel(); } catch (Throwable ignored) {}
    }
}
