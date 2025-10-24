package com.jumpcat.core.combat;

import com.jumpcat.core.game.WorldUtil;
import com.jumpcat.core.scoreboard.SidebarManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class CombatService implements Listener {
    private final SidebarManager sidebar;
    private static CombatService INSTANCE;

    public CombatService(SidebarManager sidebar) {
        this.sidebar = sidebar;
        INSTANCE = this;
    }

    private boolean anyGameRunning() { return WorldUtil.anyGameRunning(); }
    private boolean inAnyGameWorld(org.bukkit.World w) { return WorldUtil.isAnyGameWorld(w); }

    // Global weapon tuning: nerf axes and bows across all modes
    @EventHandler
    public void onGlobalDamage(EntityDamageByEntityEvent e) {
        if (!anyGameRunning()) return;
        if (!(e.getEntity() instanceof Player)) return;
        org.bukkit.World w = e.getEntity().getWorld();
        if (!inAnyGameWorld(w)) return;

        // Projectile (arrow, crossbow) nerf
        if (e.getDamager() instanceof org.bukkit.entity.Projectile) {
            org.bukkit.entity.Projectile proj = (org.bukkit.entity.Projectile) e.getDamager();
            if (proj instanceof org.bukkit.entity.AbstractArrow) {
                double base = e.getDamage();
                e.setDamage(base * 0.70); // 30% reduction
            }
            return;
        }

        // Melee axe nerf
        if (e.getDamager() instanceof Player) {
            Player attacker = (Player) e.getDamager();
            Material m = attacker.getInventory().getItemInMainHand() != null ? attacker.getInventory().getItemInMainHand().getType() : null;
            if (m != null) {
                String name = m.name();
                if (name.endsWith("_AXE")) {
                    double base = e.getDamage();
                    e.setDamage(base * 0.85); // 15% reduction
                }
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        if (!anyGameRunning()) return;
        Player victim = e.getEntity();
        if (victim != null) sidebar.incDeaths(victim.getUniqueId());
        Player killer = victim.getKiller();
        if (killer != null && killer != victim) sidebar.incKills(killer.getUniqueId());
        sidebar.updateAll();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (!anyGameRunning()) return;
        Player p = e.getPlayer();
        if (p == null) return;
        // Defer K/D handling to per-game listeners to avoid double counts
        try {
            String wn = p.getWorld().getName();
            boolean inGameWorld = wn.startsWith("skywars_r") || wn.startsWith("uhc_meetup_r") || wn.equals("battle_box");
            if (inGameWorld) return;
        } catch (Throwable ignored) {}
    }

    // Allow other listeners (e.g., SkyWars void/logout) to update K/D centrally
    public static void recordElimination(java.util.UUID victimId, java.util.UUID killerId) {
        CombatService svc = INSTANCE;
        if (svc == null) return;
        if (!svc.anyGameRunning()) return;
        if (victimId != null) svc.sidebar.incDeaths(victimId);
        if (killerId != null && killerId != victimId) svc.sidebar.incKills(killerId);
        svc.sidebar.updateAll();
    }
}
