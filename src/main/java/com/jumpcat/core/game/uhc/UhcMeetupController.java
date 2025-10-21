package com.jumpcat.core.game.uhc;

import com.jumpcat.core.game.GameController;
import com.jumpcat.core.points.PointsService;
import com.jumpcat.core.teams.TeamManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class UhcMeetupController implements GameController {
    public static volatile UhcMeetupController CURRENT = null;
    private final Plugin plugin;
    private final TeamManager teams;
    private final PointsService points;
    private final UhcMeetupConfig config;
    private final UhcMeetupManager manager;

    private boolean running = false;
    private int roundIndex = 0; // 0 or 1
    private String currentWorldName = null;
    private BukkitRunnable activeTask;
    private com.jumpcat.core.border.SoftBorder softBorder = null;
    private final java.util.Map<String, java.util.Set<java.util.UUID>> aliveByTeam = new java.util.HashMap<>();
    private final java.util.Set<java.util.UUID> aliveAll = new java.util.HashSet<>();
    private final java.util.List<java.util.UUID> carryOver = new java.util.ArrayList<>();

    public UhcMeetupController(Plugin plugin, TeamManager teams, PointsService points, UhcMeetupConfig config, UhcMeetupManager manager) {
        this.plugin = plugin;
        this.teams = teams;
        this.points = points;
        this.config = config;
        this.manager = manager;
    }

    @Override
    public String getId() { return "uhcmeetup"; }

    @Override
    public String getDisplayName() { return "UHC Meetup"; }

    @Override
    public void prepare(CommandSender initiator) {
        config.load();
        manager.cleanupStaleClones();
        initiator.sendMessage(ChatColor.YELLOW + "UHC config loaded. Template: " + ChatColor.AQUA + config.templateWorld);
    }

    @Override
    public void start(CommandSender initiator) {
        if (running) { initiator.sendMessage(ChatColor.RED + "UHC Meetup already running."); return; }
        config.load();
        manager.cleanupStaleClones();
        running = true;
        CURRENT = this;
        roundIndex = 0;
        beginRound(initiator);
    }

    private void beginRound(CommandSender initiator) {
        if (!running) return;
        String dest = "uhc_meetup_r" + (roundIndex+1);
        boolean cloned = manager.cloneTemplate(dest);
        if (!cloned) { initiator.sendMessage(ChatColor.RED + "Failed to clone template world '" + config.templateWorld + "'."); stop(initiator); return; }
        World w = manager.loadWorld(dest);
        if (w == null) { initiator.sendMessage(ChatColor.RED + "Failed to load world '" + dest + "'."); stop(initiator); return; }
        this.currentWorldName = dest;
        var center = w.getSpawnLocation();
        try { w.setGameRule(org.bukkit.GameRule.NATURAL_REGENERATION, false); } catch (Throwable ignored) {}
        try { w.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false); } catch (Throwable ignored) {}
        try { w.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false); } catch (Throwable ignored) {}
        try { w.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false); } catch (Throwable ignored) {}
        try { w.setGameRule(org.bukkit.GameRule.RANDOM_TICK_SPEED, 0); } catch (Throwable ignored) {}
        int teamCount = 0; for (String key : teams.listTeamKeys()) { if (!teams.getTeamMembers(key).isEmpty()) teamCount++; }
        // Choose scatter radius first, then set border diameter to enclose it (account for -10 margin in scatter formula)
        int desiredScatterRadius = config.borderBase + config.borderPerTeam * teamCount; // adds per team
        int diameter = Math.max(config.finalDiameter, 2 * (desiredScatterRadius + 10));
        manager.setupBorder(w, center, diameter);
        // Make vanilla border non-intrusive; SoftBorder will enforce
        try { w.getWorldBorder().setCenter(center); w.getWorldBorder().setSize(60000000); } catch (Throwable ignored) {}
        Bukkit.broadcastMessage(ChatColor.AQUA + "UHC Meetup Round " + (roundIndex+1) + ChatColor.WHITE + " starting...");
        try { ((com.jumpcat.core.JumpCatPlugin)plugin).getSidebarManager().setGameStatus(getDisplayName(), (roundIndex+1) + "/2", true); } catch (Throwable ignored) {}
        aliveByTeam.clear(); aliveAll.clear();
        // Scatter participants by team: teams placed around a circle, members near team center
        java.util.Random rnd = new java.util.Random();
        int radius = diameter / 2 - 10; // equals desiredScatterRadius
        // Build participants set: prefer carryOver list (players from previous round world), else team membership
        java.util.Map<String, java.util.List<org.bukkit.entity.Player>> participantsByTeam = new java.util.HashMap<>();
        if (!carryOver.isEmpty()) {
            for (java.util.UUID id : new java.util.ArrayList<>(carryOver)) {
                org.bukkit.entity.Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOnline()) {
                    String key = teams.getPlayerTeamKey(p).orElse("solo");
                    participantsByTeam.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(p);
                }
            }
            carryOver.clear();
        } else {
            for (String key : teams.listTeamKeys()) {
                for (String name : teams.getTeamMembers(key)) {
                    var p = Bukkit.getPlayerExact(name);
                    if (p != null && p.isOnline()) {
                        participantsByTeam.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(p);
                    }
                }
            }
        }
        java.util.List<String> teamKeys = new java.util.ArrayList<>(participantsByTeam.keySet());
        int tcount = Math.max(1, teamKeys.size());
        for (int ti = 0; ti < teamKeys.size(); ti++) {
            String key = teamKeys.get(ti);
            double angle = (2 * Math.PI * ti) / tcount;
            int baseX = center.getBlockX() + (int)Math.round(Math.cos(angle) * radius);
            int baseZ = center.getBlockZ() + (int)Math.round(Math.sin(angle) * radius);
            int baseY = w.getHighestBlockYAt(baseX, baseZ) + 1;
            org.bukkit.Location teamBase = new org.bukkit.Location(w, baseX + 0.5, baseY, baseZ + 0.5);
            for (org.bukkit.entity.Player p : participantsByTeam.getOrDefault(key, java.util.Collections.emptyList())) {
                    double ox = (rnd.nextDouble() - 0.5) * 12.0;
                    double oz = (rnd.nextDouble() - 0.5) * 12.0;
                    int tx = (int)Math.round(teamBase.getX() + ox);
                    int tz = (int)Math.round(teamBase.getZ() + oz);
                    // Preload chunk before querying highest block / teleport to avoid sync gen spikes
                    try { w.getChunkAt(tx >> 4, tz >> 4).load(true); } catch (Throwable ignored) {}
                    int ty = w.getHighestBlockYAt(tx, tz) + 1;
                    try { w.getChunkAt((int)Math.floor((tx + 0.5) / 16.0), (int)Math.floor((tz + 0.5) / 16.0)).load(true); } catch (Throwable ignored) {}
                    try { p.teleport(new org.bukkit.Location(w, tx + 0.5, ty, tz + 0.5)); } catch (Throwable ignored) {}
                    try { p.getInventory().clear(); p.getInventory().setArmorContents(null); } catch (Throwable ignored) {}
                    p.setHealth(20.0); p.setFoodLevel(20); p.setSaturation(20);
                    p.setGameMode(org.bukkit.GameMode.SURVIVAL);
                    aliveAll.add(p.getUniqueId());
                    aliveByTeam.computeIfAbsent(key, k -> new java.util.HashSet<>()).add(p.getUniqueId());
            }
        }
        // Give kits immediately (players have items during grace)
        giveKits(w);
        // Grace period: disable PvP for graceSeconds, set initial ceiling at start value
        try { w.setPVP(false); } catch (Throwable ignored) {}
        UhcMeetupListener.setCeilingY(config.yCeilStart);
        Bukkit.broadcastMessage(ChatColor.YELLOW + "Grace: " + config.graceSeconds + "s. PvP disabled.");
        // Schedule grace end → enable PvP and start shrink schedulers
        new BukkitRunnable(){ @Override public void run(){ if (!running) return; try { w.setPVP(true);} catch(Throwable ignored){}
            Bukkit.broadcastMessage(ChatColor.GREEN + "Grace ended. PvP enabled!");
            startShrinks(w, center, diameter);
        } }.runTaskLater(plugin, config.graceSeconds * 20L);
    }

    private void startShrinks(World w, org.bukkit.Location center, int startDiameter) {
        // Custom soft border (damage-only + particles) over shrinkSeconds
        final double start = startDiameter;
        final double end = Math.max(1, config.finalDiameter);
        final int totalTicks = Math.max(1, config.shrinkSeconds) * 20;
        final int ceilTicks = Math.max(1, config.yCeilShrinkSeconds) * 20;
        try {
            double startRadius = Math.max(1.0, start / 2.0);
            double endRadius = Math.max(1.0, end / 2.0);
            softBorder = new com.jumpcat.core.border.SoftBorder(
                    plugin,
                    w,
                    center,
                    startRadius,
                    endRadius,
                    totalTicks,
                    10, // enforce every 10 ticks
                    20, // particles every second
                    2.0, // baseDps: 1 heart/sec
                    4.0, // maxDps: 2 hearts/sec
                    5.0, // reach max at 5 blocks outside
                    true // show particles
            );
            softBorder.start();
        } catch (Throwable ignored) {}
        // Schedule Y-ceiling shrink (to 80) then hold last minute
        final int ceilStart = config.yCeilStart;
        final int ceilEnd = config.yCeilEnd;
        new BukkitRunnable(){ int t=0; @Override public void run(){ if (!running) { cancel(); return; } if (t <= ceilTicks) {
            double f = Math.min(1.0, (double)t/ceilTicks); int y = (int)Math.round(ceilStart + (ceilEnd - ceilStart) * f); UhcMeetupListener.setCeilingY(y);
        } else { UhcMeetupListener.setCeilingY(ceilEnd); }
        t+=20; if (t >= totalTicks) { cancel(); roundTimerFinish(); } } }.runTaskTimer(plugin, 0L, 20L);

        // Action bar: show Y-cap, soft border radius, and remaining time every second
        new BukkitRunnable(){ int remaining = Math.max(0, config.shrinkSeconds); @Override public void run(){
            if (!running || w == null || !w.isChunkLoaded(w.getSpawnLocation().getBlockX()>>4, w.getSpawnLocation().getBlockZ()>>4)) { if (!running) cancel(); }
            if (!running) { cancel(); return; }
            int ycap = UhcMeetupListener.getCeilingY();
            // Compute current radius from schedule
            double startRadius = Math.max(1.0, start / 2.0);
            double endRadius = Math.max(1.0, end / 2.0);
            int elapsed = Math.max(0, config.shrinkSeconds - Math.max(0, remaining));
            double f = Math.min(1.0, (double)elapsed / Math.max(1, config.shrinkSeconds));
            double radiusNow = startRadius + (endRadius - startRadius) * f;
            int r = Math.max(0, remaining);
            int mm = r / 60; int ss = r % 60;
            String msg = ChatColor.AQUA + "Y " + ycap + ChatColor.WHITE + " | " + ChatColor.GOLD + "Radius " + (int)Math.round(radiusNow) + ChatColor.WHITE + " | " + ChatColor.GREEN + String.format("%d:%02d", mm, ss);
            for (org.bukkit.entity.Player p : w.getPlayers()) {
                try { p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new net.md_5.bungee.api.chat.TextComponent(msg)); } catch (Throwable ignored) {}
            }
            remaining--; if (remaining < 0) { cancel(); }
        } }.runTaskTimer(plugin, 0L, 20L);
    }

    private void roundTimerFinish() {
        // End of round timer fallback; elimination logic will also call endRound when one team remains
        // Timer expired: award win points to every surviving player and announce
        for (java.util.UUID id : new java.util.HashSet<>(aliveAll)) {
            points.addPoints(id, config.scoreWinAlive);
            var p = Bukkit.getPlayer(id);
            if (p != null) try { p.sendMessage(ChatColor.GOLD + "+" + config.scoreWinAlive + " Win Points (survived)"); } catch (Throwable ignored) {}
        }
        try { Bukkit.broadcastMessage(ChatColor.GOLD + "Win points awarded to all survivors."); } catch (Throwable ignored) {}
        endRound(false);
    }

    private void giveKits(World w) {
        java.util.Map<String, Integer> teamIndex = new java.util.HashMap<>();
        for (org.bukkit.entity.Player p : w.getPlayers()) {
            // Determine team key and rotation index
            String teamKey = teams.getPlayerTeamKey(p).orElse("solo");
            int idx = teamIndex.getOrDefault(teamKey, 0);
            teamIndex.put(teamKey, (idx + 1) % 4);

            // Armor: full iron Prot I
            org.bukkit.inventory.ItemStack helm = new org.bukkit.inventory.ItemStack(org.bukkit.Material.IRON_HELMET);
            org.bukkit.inventory.ItemStack chest = new org.bukkit.inventory.ItemStack(org.bukkit.Material.IRON_CHESTPLATE);
            org.bukkit.inventory.ItemStack legs = new org.bukkit.inventory.ItemStack(org.bukkit.Material.IRON_LEGGINGS);
            org.bukkit.inventory.ItemStack boots = new org.bukkit.inventory.ItemStack(org.bukkit.Material.IRON_BOOTS);
            try { helm.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION, 1); } catch (Throwable ignored) {}
            try { chest.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION, 1); } catch (Throwable ignored) {}
            try { legs.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION, 1); } catch (Throwable ignored) {}
            try { boots.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION, 1); } catch (Throwable ignored) {}

            // Diamond piece rotation: 0:helm,1:chest,2:legs,3:boots
            switch (idx) {
                case 0: helm = new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND_HELMET); helm.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION, 1); break;
                case 1: chest = new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND_CHESTPLATE); chest.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION, 1); break;
                case 2: legs = new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND_LEGGINGS); legs.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION, 1); break;
                case 3: boots = new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND_BOOTS); boots.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION, 1); break;
            }

            // Sword Sharp II
            org.bukkit.inventory.ItemStack sword = new org.bukkit.inventory.ItemStack(org.bukkit.Material.IRON_SWORD);
            try { sword.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.SHARPNESS, 2); } catch (Throwable ignored) {}
            // Bow (no power) + 20 arrows
            org.bukkit.inventory.ItemStack bow = new org.bukkit.inventory.ItemStack(org.bukkit.Material.BOW);
            org.bukkit.inventory.ItemStack arrows = new org.bukkit.inventory.ItemStack(org.bukkit.Material.ARROW, 20);
            // Consumables
            org.bukkit.inventory.ItemStack gaps = new org.bukkit.inventory.ItemStack(org.bukkit.Material.GOLDEN_APPLE, 6);
            org.bukkit.inventory.ItemStack steak = new org.bukkit.inventory.ItemStack(org.bukkit.Material.COOKED_BEEF, 16);
            // Tools
            org.bukkit.inventory.ItemStack dpick = new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND_PICKAXE);
            org.bukkit.inventory.ItemStack daxe = new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND_AXE);
            // Blocks/utilities
            org.bukkit.inventory.ItemStack planks = new org.bukkit.inventory.ItemStack(org.bukkit.Material.OAK_PLANKS, 64);
            org.bukkit.inventory.ItemStack tables = new org.bukkit.inventory.ItemStack(org.bukkit.Material.CRAFTING_TABLE, 16);
            org.bukkit.inventory.ItemStack apples = new org.bukkit.inventory.ItemStack(org.bukkit.Material.APPLE, 16);
            org.bukkit.inventory.ItemStack enchantTable = new org.bukkit.inventory.ItemStack(org.bukkit.Material.ENCHANTING_TABLE, 1);
            org.bukkit.inventory.ItemStack lapis = new org.bukkit.inventory.ItemStack(org.bukkit.Material.LAPIS_LAZULI, 16);
            org.bukkit.inventory.ItemStack anvil = new org.bukkit.inventory.ItemStack(org.bukkit.Material.ANVIL, 1);

            // Apply to player
            org.bukkit.inventory.PlayerInventory inv = p.getInventory();
            inv.clear(); inv.setArmorContents(null);
            inv.setHelmet(helm); inv.setChestplate(chest); inv.setLeggings(legs); inv.setBoots(boots);
            inv.addItem(sword, bow, arrows, gaps, steak, dpick, daxe, planks, tables, apples, enchantTable, anvil, lapis);
        }
    }

    private void endRound(boolean aborted) {
        if (!running) return;
        try { if (softBorder != null) softBorder.stop(); } catch (Throwable ignored) {} finally { softBorder = null; }
        String prevWorld = currentWorldName;
        if (roundIndex == 1) {
            // Final cleanup after Round 2: show team standings (points), then teleport to lobby and unload
            try { showTeamStandings(); } catch (Throwable ignored) {}
            final String toUnloadFinal = prevWorld;
            new BukkitRunnable(){ @Override public void run(){
                org.bukkit.Location lobby = null;
                try { var lw = Bukkit.getWorld("lobby"); if (lw != null) lobby = lw.getSpawnLocation(); } catch (Throwable ignored) {}
                if (lobby == null) lobby = Bukkit.getWorlds().get(0).getSpawnLocation();
                if (toUnloadFinal != null) {
                    try {
                        org.bukkit.World w2 = org.bukkit.Bukkit.getWorld(toUnloadFinal);
                        if (w2 != null) {
                            for (org.bukkit.entity.Player p : w2.getPlayers()) {
                                try {
                                    p.getInventory().clear(); p.getInventory().setArmorContents(null); p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
                                    p.setHealth(20.0); p.setFoodLevel(20); p.setSaturation(20); p.setLevel(0); p.setExp(0f); p.setTotalExperience(0);
                                    p.setGameMode(org.bukkit.GameMode.ADVENTURE); p.teleport(lobby);
                                } catch (Throwable ignored) {}
                            }
                        }
                    } catch (Throwable ignored) {}
                    try { manager.unloadAndDelete(toUnloadFinal); } catch (Throwable ignored) {}
                }
                currentWorldName = null;
                running = false;
                CURRENT = null;
                try { ((com.jumpcat.core.JumpCatPlugin)plugin).getSidebarManager().setGameStatus(getDisplayName(), "-", false); } catch (Throwable ignored) {}
            } }.runTaskLater(plugin, 100L); // 5s delay
            return;
        }
        if (aborted) { running = false; Bukkit.broadcastMessage(ChatColor.YELLOW + "UHC Meetup aborted."); return; }
        if (roundIndex == 0) {
            // Schedule Round 2 without sending players to lobby; beginRound will teleport participants to the new world
            final String toUnload = prevWorld;
            roundIndex = 1;
            try { if (softBorder != null) softBorder.stop(); } catch (Throwable ignored) {} finally { softBorder = null; }
            // Capture participants currently in previous round world to carry over
            try {
                if (toUnload != null) {
                    org.bukkit.World pw = org.bukkit.Bukkit.getWorld(toUnload);
                    carryOver.clear();
                    if (pw != null) {
                        for (org.bukkit.entity.Player p : pw.getPlayers()) {
                            carryOver.add(p.getUniqueId());
                        }
                    }
                }
            } catch (Throwable ignored) {}
            Bukkit.broadcastMessage(ChatColor.YELLOW + "Round 2 starting in 10s...");
            // Single delayed task to avoid flaky countdown issues
            new BukkitRunnable(){ @Override public void run(){ if (!running) return; beginRound(Bukkit.getConsoleSender());
                // After scatter happens, unload previous world a few seconds later to avoid overlapping IO with scatter
                if (toUnload != null) new BukkitRunnable(){ @Override public void run(){ try { manager.unloadAndDelete(toUnload); } catch (Throwable ignored) {} } }.runTaskLater(plugin, 100L);
            } }.runTaskLater(plugin, 10L * 20L);
        } else {
            running = false;
            CURRENT = null;
            try { if (softBorder != null) softBorder.stop(); } catch (Throwable ignored) {} finally { softBorder = null; }
            try { ((com.jumpcat.core.JumpCatPlugin)plugin).getSidebarManager().setGameStatus(getDisplayName(), "-", false); } catch (Throwable ignored) {}
        }
    }

    // Called by listener when a participant dies or logs out
    public void onPlayerDeath(java.util.UUID victimId, java.util.UUID killerId) {
        if (!running) return;
        // Award killer points (message handled in listener for context-specific suffix)
        if (killerId != null) {
            points.addPoints(killerId, config.scoreKill);
        }
        // Survival drip: +7 to all alive excluding victim
        for (java.util.UUID id : new java.util.HashSet<>(aliveAll)) {
            if (!id.equals(victimId)) points.addPoints(id, config.scoreSurvivalPerDeath);
        }
        // Remove victim from alive sets
        aliveAll.remove(victimId);
        for (java.util.Set<java.util.UUID> set : aliveByTeam.values()) set.remove(victimId);
        // Check elimination: if only one team has alive players, award win to them and end round early
        String lastTeam = null; int aliveTeams = 0;
        for (java.util.Map.Entry<String, java.util.Set<java.util.UUID>> e : aliveByTeam.entrySet()) {
            if (!e.getValue().isEmpty()) { aliveTeams++; lastTeam = e.getKey(); }
        }
        if (aliveTeams <= 1) {
            if (lastTeam != null) {
                for (java.util.UUID id : new java.util.HashSet<>(aliveByTeam.getOrDefault(lastTeam, java.util.Collections.emptySet()))) {
                    points.addPoints(id, config.scoreWinAlive);
                    var p = Bukkit.getPlayer(id);
                    if (p != null) try { p.sendMessage(ChatColor.GOLD + "+" + config.scoreWinAlive + " Win Points (team victory)"); } catch (Throwable ignored) {}
                }
                try {
                    String label = teams.getTeamColor(lastTeam) + "" + ChatColor.BOLD + teams.getTeamLabel(lastTeam);
                    String title = label + ChatColor.RESET + ChatColor.WHITE + " wins!";
                    String subtitle = ChatColor.YELLOW + "UHC Meetup";
                    for (org.bukkit.entity.Player pp : Bukkit.getOnlinePlayers()) {
                        try { pp.sendTitle(title, subtitle, 0, 60, 10); } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
            }
            endRound(false);
        }
    }

    @Override
    public void stop(CommandSender initiator) {
        boolean wasRunning = running;
        running = false;
        try { if (softBorder != null) softBorder.stop(); } catch (Throwable ignored) {} finally { softBorder = null; }
        try { if (activeTask != null) activeTask.cancel(); } catch (Throwable ignored) {}
        // Teleport/reset everyone out of any UHC round worlds, then unload/delete them
        org.bukkit.Location lobby = null;
        try { var lw = Bukkit.getWorld("lobby"); if (lw != null) lobby = lw.getSpawnLocation(); } catch (Throwable ignored) {}
        if (lobby == null) lobby = Bukkit.getWorlds().get(0).getSpawnLocation();
        for (org.bukkit.World w : Bukkit.getWorlds()) {
            if (!w.getName().startsWith("uhc_meetup_r")) continue;
            for (org.bukkit.entity.Player p : w.getPlayers()) {
                try {
                    p.getInventory().clear(); p.getInventory().setArmorContents(null); p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
                    p.setHealth(20.0); p.setFoodLevel(20); p.setSaturation(20); p.setLevel(0); p.setExp(0f); p.setTotalExperience(0);
                    p.setGameMode(org.bukkit.GameMode.ADVENTURE); p.teleport(lobby);
                } catch (Throwable ignored) {}
            }
            try { manager.unloadAndDelete(w.getName()); } catch (Throwable ignored) {}
        }
        currentWorldName = null;
        initiator.sendMessage(ChatColor.YELLOW + (wasRunning ? "UHC Meetup stopped." : "UHC Meetup was not running."));
        try { ((com.jumpcat.core.JumpCatPlugin)plugin).getSidebarManager().setGameStatus(getDisplayName(), "-", false); } catch (Throwable ignored) {}
    }

    @Override
    public String status() {
        if (!running) return "Idle";
        return "Running round " + (roundIndex+1);
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
}
