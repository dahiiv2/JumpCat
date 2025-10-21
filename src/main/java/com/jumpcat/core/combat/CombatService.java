package com.jumpcat.core.combat;

import com.jumpcat.core.game.battlebox.BattleBoxController;
import com.jumpcat.core.game.skywars.SkyWarsController;
import com.jumpcat.core.game.uhc.UhcMeetupController;
import com.jumpcat.core.scoreboard.SidebarManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class CombatService implements Listener {
    private final SidebarManager sidebar;
    private static CombatService INSTANCE;

    public CombatService(SidebarManager sidebar) {
        this.sidebar = sidebar;
        INSTANCE = this;
    }

    private boolean anyGameRunning() {
        return (SkyWarsController.CURRENT != null) ||
               (UhcMeetupController.CURRENT != null) ||
               (BattleBoxController.CURRENT != null);
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
        // Only count as death if in a game world and not spectator
        try {
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
            String wn = p.getWorld().getName();
            boolean inGameWorld = wn.startsWith("skywars_r") || wn.startsWith("uhc_meetup_r") || wn.equals("battle_box");
            if (!inGameWorld) return;
            sidebar.incDeaths(p.getUniqueId());
        } catch (Throwable ignored) {}
        sidebar.updateAll();
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
