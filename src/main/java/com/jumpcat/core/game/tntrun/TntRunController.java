package com.jumpcat.core.game.tntrun;

import com.jumpcat.core.game.GameController;
import com.jumpcat.core.teams.TeamManager;
import com.jumpcat.core.points.PointsService;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class TntRunController implements GameController {
    public static TntRunController CURRENT;

    private final org.bukkit.plugin.Plugin plugin;
    private final TeamManager teams;
    private final PointsService points;
    private final TntRunConfig config;
    private final TntRunManager manager;

    private boolean running = false;
    private boolean live = false;
    private int roundIndex = 0;
    private String currentWorldName = null;
    private String pendingUnloadWorld = null;

    private final Set<UUID> aliveAll = new HashSet<>();
    private final Map<String, Set<UUID>> aliveByTeam = new HashMap<>();
    private int initialParticipants = 0;

    public TntRunController(org.bukkit.plugin.Plugin plugin, TeamManager teams, PointsService points, TntRunConfig cfg, TntRunManager mgr) {
        this.plugin = plugin;
        this.teams = teams;
        this.points = points;
        this.config = cfg;
        this.manager = mgr;
    }

    @Override
    public String getId() { return "tntrun"; }

    @Override
    public String getDisplayName() { return ChatColor.AQUA + "TNT Run" + ChatColor.WHITE; }

    @Override
    public void prepare(CommandSender initiator) { initiator.sendMessage(ChatColor.YELLOW + "TNT Run ready: template '" + config.templateWorld + "'."); }

    @Override
    public void start(CommandSender initiator) {
        if (running) { initiator.sendMessage(ChatColor.RED + "TNT Run already running."); return; }
        try { manager.cleanupStaleClones(); } catch (Throwable ignored) {}
        running = true; CURRENT = this; roundIndex = 0;
        beginRound(initiator);
    }

    @Override
    public void stop(CommandSender initiator) {
        running = false; CURRENT = null;
        World w = currentWorldName != null ? Bukkit.getWorld(currentWorldName) : null;
        Location lobby = null;
        try { var lm = ((com.jumpcat.core.JumpCatPlugin)plugin).getLobbyManager(); if (lm != null) lobby = lm.getLobbySpawn(); } catch (Throwable ignored) {}
        if (lobby == null) { try { var lw = Bukkit.getWorld("lobby"); if (lw != null) lobby = lw.getSpawnLocation(); } catch (Throwable ignored) {} }
        if (lobby == null) lobby = Bukkit.getWorlds().get(0).getSpawnLocation();
        if (w != null) for (Player p : w.getPlayers()) { try { p.setCollidable(true); } catch (Throwable ignored) {} safeReset(p); p.teleport(lobby); }
        if (currentWorldName != null) manager.unloadAndDelete(currentWorldName);
        currentWorldName = null;
        try { ((com.jumpcat.core.JumpCatPlugin)plugin).getSidebarManager().setGameStatus(getDisplayName(), "-", false); } catch (Throwable ignored) {}
    }

    @Override
    public String status() { return running ? ChatColor.GREEN + ("Round " + (roundIndex+1) + "/" + config.rounds) : ChatColor.RED + "Idle"; }

    private void beginRound(CommandSender initiator) {
        if (!running) return;
        String dest = "tntrun_r" + (roundIndex+1);
        boolean ok = manager.cloneTemplate(dest);
        if (!ok) { initiator.sendMessage(ChatColor.RED + "Failed to clone template world '" + config.templateWorld + "'."); stop(initiator); return; }
        World w = manager.loadWorld(dest);
        if (w == null) { initiator.sendMessage(ChatColor.RED + "Failed to load world '" + dest + "'."); stop(initiator); return; }
        // Mark previous world to unload AFTER we move players
        String prevWorld = currentWorldName;
        pendingUnloadWorld = prevWorld;
        currentWorldName = dest;
        // Gamerules
        try { w.setGameRule(GameRule.DO_MOB_SPAWNING, false); } catch (Throwable ignored) {}
        try { w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false); } catch (Throwable ignored) {}
        try { w.setGameRule(GameRule.DO_WEATHER_CYCLE, false); } catch (Throwable ignored) {}
        try { w.setPVP(false); } catch (Throwable ignored) {}
        w.setTime(6000);

        // Build participants: all players on teams (like BB)
        aliveAll.clear(); aliveByTeam.clear();
        for (String key : teams.listTeamKeys()) {
            for (String name : teams.getTeamMembers(key)) {
                Player p = Bukkit.getPlayerExact(name);
                if (p == null) continue;
                aliveAll.add(p.getUniqueId());
                aliveByTeam.computeIfAbsent(key, k -> new HashSet<>()).add(p.getUniqueId());
            }
        }
        initialParticipants = aliveAll.size();
        // Teleport everyone in this set to the arena spawn and prep
        Location spawn = new Location(w, 0.5, 110.0, 0.5, 0f, 0f);
        for (UUID id : new HashSet<>(aliveAll)) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) { aliveAll.remove(id); continue; }
            try { w.getChunkAt(spawn).load(true); } catch (Throwable ignored) {}
            try { p.teleport(spawn); } catch (Throwable ignored) {}
            prepareParticipant(p);
        }
        // Aggressively disable collision for all players in this TNT Run world - set it multiple times to ensure it sticks
        try { 
            for (Player p : w.getPlayers()) { 
                p.setCollidable(false);
                // Also set it in a delayed task to catch any resets
                new org.bukkit.scheduler.BukkitRunnable(){ @Override public void run(){ 
                    if (running && p.isOnline() && p.getWorld().getName().equals(currentWorldName)) {
                        try { p.setCollidable(false); } catch (Throwable ignored) {}
                    }
                } }.runTaskLater(plugin, 5L);
            } 
        } catch (Throwable ignored) {}
        // Capture initial participant count AFTER filtering offline players
        initialParticipants = aliveAll.size();

        resetPercentiles();
        Bukkit.broadcastMessage(ChatColor.AQUA + "TNT Run Round " + (roundIndex+1) + ChatColor.WHITE + " starting...");
        try { ((com.jumpcat.core.JumpCatPlugin)plugin).getSidebarManager().setGameStatus(getDisplayName(), (roundIndex+1) + "/" + config.rounds, true); } catch (Throwable ignored) {}

        // 5s grace: do not break blocks until live
        live = false;
        // Reset listener state for new round
        try { com.jumpcat.core.game.tntrun.TntRunListener.resetForNewRound(); } catch (Throwable ignored) {}
        new BukkitRunnable(){ @Override public void run(){ live = true; startLive(); } }.runTaskLater(plugin, config.graceSeconds * 20L);

        // Action bar handled by listener (Blocks N | Alive A)
    }

    private void startLive() {
        // Give feather to everyone
        World w = Bukkit.getWorld(currentWorldName);
        if (w == null) return;
        for (Player p : w.getPlayers()) {
            // Ensure Adventure to prevent block glitching on TNT
            try { p.setGameMode(GameMode.ADVENTURE); } catch (Throwable ignored) {}
            // Aggressively re-disable collision after gamemode change - ALWAYS disable
            try { p.setCollidable(false); } catch (Throwable ignored) {}
            // Also set it delayed to catch any resets
            final org.bukkit.entity.Player fp = p;
            final String worldName = currentWorldName;
            new org.bukkit.scheduler.BukkitRunnable(){ @Override public void run(){ 
                if (fp.isOnline() && fp.getWorld().getName().equals(worldName)) {
                    try { fp.setCollidable(false); } catch (Throwable ignored) {}
                }
            } }.runTaskLater(plugin, 5L);
            try { p.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.FEATHER, 1)); } catch (Throwable ignored) {}
        }
        // Now that everyone is in the new world, unload the previous one if set
        if (pendingUnloadWorld != null && !pendingUnloadWorld.equals(currentWorldName)) {
            try { manager.unloadAndDelete(pendingUnloadWorld); } catch (Throwable ignored) {}
            pendingUnloadWorld = null;
        }
        // Kick off static footprint processing for players who stand still
        try { com.jumpcat.core.game.tntrun.TntRunListener.startForRound(plugin, this); } catch (Throwable ignored) {}
    }

    private void prepareParticipant(Player p) {
        p.setGameMode(GameMode.SURVIVAL);
        // Safeguard: re-disable collision after gamemode change - ALWAYS disable for TNT Run worlds
        if (currentWorldName != null && p.getWorld().getName().equals(currentWorldName)) {
            try { p.setCollidable(false); } catch (Throwable ignored) {}
        }
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.setSaturation(20);
        p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
        try { p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.NIGHT_VISION, 20*60*15, 0, true, false)); } catch (Throwable ignored) {}
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
    }

    public boolean isRunning() { return running; }
    public String currentWorld() { return currentWorldName; }
    public boolean isLive() { return live; }

    public boolean isAlive(UUID id) { return aliveAll.contains(id); }

    public void onEliminated(UUID victimId) {
        aliveAll.remove(victimId);
        for (Set<UUID> set : aliveByTeam.values()) set.remove(victimId);
        // Broadcast elimination message (match SkyWars format: teamLabel + name » died)
        try {
            org.bukkit.entity.Player victim = Bukkit.getPlayer(victimId);
            String name = victim != null ? victim.getName() : (Bukkit.getOfflinePlayer(victimId).getName());
            String victimTeamKey = (victim != null) ? teams.getPlayerTeamKey(victim).orElse("") : "";
            String victimLabel = victimTeamKey.isEmpty() ? "" : ("" + teams.getTeamColor(victimTeamKey) + ChatColor.BOLD + teams.getTeamLabel(victimTeamKey) + ChatColor.RESET + ChatColor.WHITE + " ");
            String victimName = ChatColor.WHITE + name;
            Bukkit.broadcastMessage(victimLabel + victimName + ChatColor.GRAY + " » " + ChatColor.RED + "died");
        } catch (Throwable ignored) {}
        // Survival drip +7 to others
        for (UUID id : new HashSet<>(aliveAll)) {
            points.addPoints(id, config.pointsSurvivalDrip);
            org.bukkit.entity.Player rp = Bukkit.getPlayer(id);
            if (rp != null) try { rp.sendMessage(ChatColor.AQUA + "+" + config.pointsSurvivalDrip + " Points (survival)" ); } catch (Throwable ignored) {}
        }
        // Check victory
        String lastTeam = null; int aliveTeams = 0;
        for (Map.Entry<String, Set<UUID>> e : aliveByTeam.entrySet()) {
            if (!e.getValue().isEmpty()) { aliveTeams++; lastTeam = e.getKey(); }
        }
        if (aliveTeams <= 1) {
            if (lastTeam != null) {
                for (UUID id : new HashSet<>(aliveByTeam.getOrDefault(lastTeam, Collections.emptySet()))) {
                    points.addPoints(id, config.pointsWinPerPlayer);
                    org.bukkit.entity.Player wp = Bukkit.getPlayer(id);
                    if (wp != null) try { wp.sendMessage(ChatColor.AQUA + "+" + config.pointsWinPerPlayer + " Points (team victory)"); } catch (Throwable ignored) {}
                }
                try { Bukkit.broadcastMessage(ChatColor.YELLOW + "Winner: " + teams.getTeamColor(lastTeam) + "" + ChatColor.BOLD + teams.getTeamLabel(lastTeam) + ChatColor.RESET + ChatColor.WHITE); } catch (Throwable ignored) {}
                // Put everyone to spectator and show title
                try { if (currentWorldName != null) { org.bukkit.World w = org.bukkit.Bukkit.getWorld(currentWorldName); if (w != null) { w.setPVP(false); for (org.bukkit.entity.Player p : w.getPlayers()) { try { p.setGameMode(GameMode.SPECTATOR); } catch (Throwable ignored) {} try { p.setCollidable(false); } catch (Throwable ignored) {} } } } } catch (Throwable ignored) {}
                try {
                    String label = teams.getTeamColor(lastTeam) + "" + ChatColor.BOLD + teams.getTeamLabel(lastTeam);
                    String title = label + ChatColor.RESET + ChatColor.WHITE + " wins!";
                    String subtitle = ChatColor.YELLOW + "TNT Run";
                    for (org.bukkit.entity.Player pp : Bukkit.getOnlinePlayers()) { try { pp.sendTitle(title, subtitle, 0, 60, 10); } catch (Throwable ignored) {} }
                } catch (Throwable ignored) {}
            }
            endRound();
        } else {
            // Percentile bonuses once per threshold
            maybeAwardPercentiles();
        }
    }

    private boolean awarded50 = false;
    private boolean awarded25 = false;
    private boolean awarded10 = false;

    private void resetPercentiles() { awarded50 = awarded25 = awarded10 = false; }

    private void maybeAwardPercentiles() {
        int total = initialParticipants;
        int alive = aliveAll.size();
        if (total <= 0) return;
        int alive50 = (int) Math.ceil(total * 0.50);
        int alive25 = (int) Math.ceil(total * 0.25);
        int alive10 = (int) Math.ceil(total * 0.10);
        if (!awarded50 && alive <= alive50) {
            for (UUID id : new HashSet<>(aliveAll)) { points.addPoints(id, config.bonus50); org.bukkit.entity.Player p = Bukkit.getPlayer(id); if (p != null) try { p.sendMessage(ChatColor.AQUA + "+" + config.bonus50 + " Points (Top 50%)"); } catch (Throwable ignored) {} }
            try { Bukkit.broadcastMessage(ChatColor.GREEN + "Top 50% reached: " + ChatColor.AQUA + "+" + config.bonus50 + ChatColor.WHITE + " to all survivors."); } catch (Throwable ignored) {}
            awarded50 = true;
        }
        if (!awarded25 && alive <= alive25) {
            for (UUID id : new HashSet<>(aliveAll)) { points.addPoints(id, config.bonus25); org.bukkit.entity.Player p = Bukkit.getPlayer(id); if (p != null) try { p.sendMessage(ChatColor.AQUA + "+" + config.bonus25 + " Points (Top 25%)"); } catch (Throwable ignored) {} }
            try { Bukkit.broadcastMessage(ChatColor.GREEN + "Top 25% reached: " + ChatColor.AQUA + "+" + config.bonus25 + ChatColor.WHITE + " to all survivors."); } catch (Throwable ignored) {}
            awarded25 = true;
        }
        if (!awarded10 && alive <= alive10) {
            for (UUID id : new HashSet<>(aliveAll)) { points.addPoints(id, config.bonus10); org.bukkit.entity.Player p = Bukkit.getPlayer(id); if (p != null) try { p.sendMessage(ChatColor.AQUA + "+" + config.bonus10 + " Points (Top 10%)"); } catch (Throwable ignored) {} }
            try { Bukkit.broadcastMessage(ChatColor.GREEN + "Top 10% reached: " + ChatColor.AQUA + "+" + config.bonus10 + ChatColor.WHITE + " to all survivors."); } catch (Throwable ignored) {}
            awarded10 = true;
        }
    }

    private void endRound() {
        if (!running) return;
        // Prepare for next round: keep players in current world; unloading handled after next round teleports
        World w = Bukkit.getWorld(currentWorldName);
        if (w != null) for (Player p : w.getPlayers()) { safeReset(p); /* keep in place until next round teleports */ }
        // mark current for unload after next round goes live
        pendingUnloadWorld = currentWorldName;
        currentWorldName = null; // will be set by beginRound
        roundIndex++;
        if (roundIndex < config.rounds) {
            Bukkit.broadcastMessage(ChatColor.YELLOW + "Next TNT Run round in 5s...");
            new BukkitRunnable(){ @Override public void run(){ if (running) beginRound(Bukkit.getConsoleSender()); } }.runTaskLater(plugin, 100L);
        } else {
            // Final cleanup after last round
            String worldToFinish = pendingUnloadWorld != null ? pendingUnloadWorld : currentWorldName;
            // Show team standings (points totals) like UHC
            try { showTeamStandings(); } catch (Throwable ignored) {}
            final String toUnloadFinal = worldToFinish;
            new BukkitRunnable(){ @Override public void run(){
                org.bukkit.Location lobby = null;
                try { var lm = ((com.jumpcat.core.JumpCatPlugin)plugin).getLobbyManager(); if (lm != null) lobby = lm.getLobbySpawn(); } catch (Throwable ignored) {}
                if (lobby == null) { try { var lw = Bukkit.getWorld("lobby"); if (lw != null) lobby = lw.getSpawnLocation(); } catch (Throwable ignored) {} }
                if (lobby == null) lobby = Bukkit.getWorlds().get(0).getSpawnLocation();
                if (toUnloadFinal != null) {
                    try {
                        org.bukkit.World w2 = org.bukkit.Bukkit.getWorld(toUnloadFinal);
                        if (w2 != null) {
                            for (org.bukkit.entity.Player p : w2.getPlayers()) {
                                try {
                                    p.setCollidable(true);
                                    p.getInventory().clear(); p.getInventory().setArmorContents(null); p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
                                    p.setHealth(20.0); p.setFoodLevel(20); p.setSaturation(20); p.setLevel(0); p.setExp(0f); p.setTotalExperience(0);
                                    p.setGameMode(GameMode.ADVENTURE);
                                    // Note: collision re-enabled above since player is leaving TNT Run world
                                    p.teleport(lobby);
                                } catch (Throwable ignored) {}
                            }
                        }
                    } catch (Throwable ignored) {}
                    try { manager.unloadAndDelete(toUnloadFinal); } catch (Throwable ignored) {}
                }
                currentWorldName = null;
                running = false; CURRENT = null;
                try { ((com.jumpcat.core.JumpCatPlugin)plugin).getSidebarManager().setGameStatus(getDisplayName(), "-", false); } catch (Throwable ignored) {}
                Bukkit.broadcastMessage(ChatColor.AQUA + "TNT Run series complete!");
            } }.runTaskLater(plugin, 100L);
        }
    }

    private void showTeamStandings() {
        java.util.Map<String, Integer> teamTotals = new java.util.LinkedHashMap<>();
        for (String key : teams.listTeamKeys()) {
            int sum = 0;
            for (String name : teams.getTeamMembers(key)) {
                java.util.UUID id = Bukkit.getOfflinePlayer(name).getUniqueId();
                sum += points.getPoints(id);
            }
            if (sum > 0) teamTotals.put(key, sum);
        }
        java.util.List<java.util.Map.Entry<String,Integer>> rows = new java.util.ArrayList<>(teamTotals.entrySet());
        rows.sort((a,b) -> Integer.compare(b.getValue(), a.getValue()));
        if (!rows.isEmpty()) {
            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage(ChatColor.AQUA + "Team Standings:");
            int i = 1;
            for (java.util.Map.Entry<String,Integer> e : rows) {
                String label = teams.getTeamColor(e.getKey()) + "" + ChatColor.BOLD + teams.getTeamLabel(e.getKey()) + ChatColor.RESET + ChatColor.WHITE;
                Bukkit.broadcastMessage(ChatColor.YELLOW + "#"+i+" " + label + ChatColor.GRAY + " | " + ChatColor.WHITE + "Points: " + ChatColor.AQUA + e.getValue());
                i++;
            }
            Bukkit.broadcastMessage("");
        }
    }

    private void safeReset(Player p) {
        try {
            p.getInventory().clear();
            p.getInventory().setArmorContents(null);
            p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
            p.setGameMode(GameMode.ADVENTURE);
            // Aggressively re-disable collision after gamemode change - ALWAYS disable for TNT Run worlds
            if (currentWorldName != null && p.getWorld().getName().equals(currentWorldName)) {
                try { p.setCollidable(false); } catch (Throwable ignored) {}
                // Also set it delayed to catch any resets
                final org.bukkit.entity.Player fp = p;
                final String worldName = currentWorldName;
                new org.bukkit.scheduler.BukkitRunnable(){ @Override public void run(){ 
                    if (fp.isOnline() && fp.getWorld().getName().equals(worldName)) {
                        try { fp.setCollidable(false); } catch (Throwable ignored) {}
                    }
                } }.runTaskLater(plugin, 5L);
            }
            p.setHealth(20.0);
            p.setFoodLevel(20);
            p.setSaturation(20);
            p.setLevel(0);
            p.setExp(0f);
            p.setTotalExperience(0);
        } catch (Throwable ignored) {}
    }
}
