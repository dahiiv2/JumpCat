package com.jumpcat.core.game.skywars;

import com.jumpcat.core.JumpCatPlugin;
import com.jumpcat.core.game.GameController;
import com.jumpcat.core.teams.TeamManager;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.io.File;

public class SkyWarsController implements GameController {
    public static SkyWarsController CURRENT;

    private final JumpCatPlugin plugin;
    private final TeamManager teams;
    private final SkyWarsConfig config;
    private final SkyWarsManager manager;

    private boolean running = false;
    private int roundIndex = 0;
    private String currentWorldName = null;
    private boolean endingRound = false;
    private com.jumpcat.core.border.SoftBorder softBorder = null;

    private final Map<String, Set<UUID>> aliveByTeam = new HashMap<>();
    private final Set<UUID> aliveAll = new HashSet<>();

    public SkyWarsController(JumpCatPlugin plugin, TeamManager teams) {
        this.plugin = plugin;
        this.teams = teams;
        this.config = new SkyWarsConfig();
        this.manager = new SkyWarsManager(plugin);
    }

    @Override
    public String getId() { return "skywars"; }

    @Override
    public String getDisplayName() { return ChatColor.AQUA + "SkyWars" + ChatColor.WHITE; }

    @Override
    public void prepare(CommandSender initiator) {
        if (running) { initiator.sendMessage(ChatColor.RED + "SkyWars already running."); return; }
        initiator.sendMessage(ChatColor.AQUA + "Preparing SkyWars...");
        try { manager.ensureTemplateWorld(); } catch (Throwable ignored) {}
        // Clean old clones with prefix "skywars_r"
        try {
            File root = new File(".");
            File[] dirs = root.listFiles((f) -> f.isDirectory() && f.getName().startsWith("skywars_r"));
            if (dirs != null) for (File d : dirs) try { World w = Bukkit.getWorld(d.getName()); if (w != null) Bukkit.unloadWorld(w, false); deleteRecursive(d); } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
        initiator.sendMessage(ChatColor.GREEN + "Ready.");
    }

    @Override
    public void start(CommandSender initiator) {
        if (running) { initiator.sendMessage(ChatColor.RED + "SkyWars already running."); return; }
        running = true;
        CURRENT = this;
        roundIndex = 0;
        beginRound(initiator);
    }

    @Override
    public void stop(CommandSender initiator) {
        boolean wasRunning = running;
        running = false;
        CURRENT = null;
        // Stop soft border if active
        try { if (softBorder != null) softBorder.stop(); } catch (Throwable ignored) {} finally { softBorder = null; }
        // Teleport/reset everyone out of any SkyWars round worlds, then unload/delete them
        org.bukkit.Location lobby = null;
        try { var lw = plugin.getLobbyManager().getLobbyWorld(); if (lw != null) lobby = lw.getSpawnLocation(); } catch (Throwable ignored) {}
        if (lobby == null) lobby = Bukkit.getWorlds().get(0).getSpawnLocation();
        for (World w : Bukkit.getWorlds()) {
            if (!w.getName().startsWith("skywars_r")) continue;
            for (Player p : w.getPlayers()) {
                try {
                    p.getInventory().clear();
                    p.getInventory().setArmorContents(null);
                    p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
                    p.setHealth(20.0);
                    p.setFoodLevel(20);
                    p.setSaturation(20);
                    p.setLevel(0);
                    p.setExp(0f);
                    p.setTotalExperience(0);
                    p.setGameMode(GameMode.ADVENTURE);
                    p.teleport(lobby);
                } catch (Throwable ignored) {}
            }
            try { manager.unloadAndDelete(w.getName()); } catch (Throwable ignored) {}
        }
        currentWorldName = null;
        try { plugin.getSidebarManager().setGameStatus(getDisplayName(), "-", false); } catch (Throwable ignored) {}
    }

    @Override
    public String status() {
        if (!running) return ChatColor.RED + "Idle";
        return ChatColor.GREEN + "Round " + (roundIndex+1) + "/" + config.getRounds();
    }

    private void beginRound(CommandSender initiator) {
        if (!running) return;
        endingRound = false;
        String dest = "skywars_r" + (roundIndex+1);
        boolean cloned = manager.cloneTemplate(dest);
        if (!cloned) { initiator.sendMessage(ChatColor.RED + "Failed to clone template world '" + config.getTemplateWorld() + "'."); stop(initiator); return; }
        World w = manager.loadWorld(dest);
        if (w == null) { initiator.sendMessage(ChatColor.RED + "Failed to load world '" + dest + "'."); stop(initiator); return; }
        this.currentWorldName = dest;
        Location center = new Location(w, 0.5, 65, 0.5);
        // Gamerules/perf
        try { w.setGameRule(GameRule.NATURAL_REGENERATION, true); } catch (Throwable ignored) {}
        try { w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false); } catch (Throwable ignored) {}
        try { w.setGameRule(GameRule.DO_WEATHER_CYCLE, false); } catch (Throwable ignored) {}
        try { w.setGameRule(GameRule.DO_MOB_SPAWNING, false); } catch (Throwable ignored) {}
        try { w.setGameRule(GameRule.RANDOM_TICK_SPEED, 0); } catch (Throwable ignored) {}
        try { w.setPVP(false); } catch (Throwable ignored) {}
        w.setTime(6000);

        Bukkit.broadcastMessage(ChatColor.AQUA + "SkyWars Round " + (roundIndex+1) + ChatColor.WHITE + " starting...");
        try { plugin.getSidebarManager().setGameStatus(getDisplayName(), (roundIndex+1) + "/" + config.getRounds(), true); } catch (Throwable ignored) {}
        aliveByTeam.clear(); aliveAll.clear();

        // Build participants by non-empty teams (cap 12)
        List<String> teamKeys = new ArrayList<>();
        for (String key : teams.listTeamKeys()) if (!teams.getTeamMembers(key).isEmpty()) teamKeys.add(key);
        // Cap at 12 teams
        if (teamKeys.size() > 12) {
            Bukkit.broadcastMessage(ChatColor.YELLOW + "SkyWars supports max 12 teams per round; extra teams won't join this match.");
            teamKeys = teamKeys.subList(0, 12);
        }
        // Shuffle spawns
        List<SkyWarsConfig.Spawn> spawns = new ArrayList<>(config.getSpawnPoints());
        Collections.shuffle(spawns, new Random());

        int used = Math.min(teamKeys.size(), spawns.size());
        for (int i = 0; i < used; i++) {
            String key = teamKeys.get(i);
            SkyWarsConfig.Spawn s = spawns.get(i);
            Location base = new Location(w, s.x, s.y, s.z, s.yaw, s.pitch);
            for (String name : teams.getTeamMembers(key)) {
                Player p = Bukkit.getPlayerExact(name);
                if (p == null) continue;
                // Preload chunk
                try { w.getChunkAt(base.getBlockX() >> 4, base.getBlockZ() >> 4).load(true); } catch (Throwable ignored) {}
                // Teleport and kit
                try { p.teleport(base); } catch (Throwable ignored) {}
                try { p.getInventory().clear(); p.getInventory().setArmorContents(null); } catch (Throwable ignored) {}
                giveKit(p, key);
                p.setHealth(20.0); p.setFoodLevel(20); p.setSaturation(20);
                p.setGameMode(GameMode.SURVIVAL);
                aliveAll.add(p.getUniqueId());
                aliveByTeam.computeIfAbsent(key, k -> new HashSet<>()).add(p.getUniqueId());
            }
        }

        // Compute start border to cover all spawns comfortably
        double maxR = 10.0;
        for (SkyWarsConfig.Spawn s : spawns) {
            double r = Math.hypot(s.x - center.getX(), s.z - center.getZ());
            if (r > maxR) maxR = r;
        }
        int startDiameter = (int)Math.ceil(maxR * 2 + 30);
        manager.setupBorder(w, center, startDiameter);
        // Make vanilla border non-intrusive (very large), we will enforce a custom soft border
        try { w.getWorldBorder().setCenter(center); w.getWorldBorder().setSize(60000000); } catch (Throwable ignored) {}

        // Pre-start freeze (5s) then Grace → enable PVP; start shrinks 30s after grace ends
        final int preStart = 5;
        final int grace = config.getGraceSeconds();
        // Freeze players during pre-start
        try {
            for (Player p : w.getPlayers()) p.setWalkSpeed(0.0f);
        } catch (Throwable ignored) {}
        // Unfreeze after pre-start
        new BukkitRunnable(){ @Override public void run(){
            try { for (Player p : w.getPlayers()) p.setWalkSpeed(0.2f); } catch (Throwable ignored) {}
        } }.runTaskLater(plugin, preStart * 20L);
        // Schedule grace end after pre-start + grace
        new BukkitRunnable(){ @Override public void run(){ try { w.setPVP(true); } catch (Throwable ignored) {}
            // Delay soft border shrink by 30 seconds after grace
            new BukkitRunnable(){ @Override public void run(){ if (!running || !w.getName().equals(currentWorldName)) return; startShrinks(w, center, startDiameter); } }.runTaskLater(plugin, 30L * 20L);
        } }.runTaskLater(plugin, (preStart + grace) * 20L);

        // Action bar: pre-start countdown, then grace countdown; after grace show time left, border radius, safe Y band
        new BukkitRunnable(){ int t=0; @Override public void run(){ if (!running || !w.getName().equals(currentWorldName)) { cancel(); return; }
            int hard = config.getRoundHardCapSeconds();
            int elapsed = t;
            if (elapsed < preStart) {
                int left = preStart - elapsed;
                String msg = ChatColor.GOLD + "Starting in: " + ChatColor.WHITE + left + "s";
                for (Player p : w.getPlayers()) { try { p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new net.md_5.bungee.api.chat.TextComponent(msg)); } catch (Throwable ignored) {} }
                t += 1; return;
            }
            int after = elapsed - preStart;
            int remaining = Math.max(0, hard - after);
            String msg;
            if (after < grace) {
                int left = grace - after;
                msg = ChatColor.GREEN + "Grace: " + ChatColor.WHITE + left + "s";
            } else {
                // Safe Y band from [40..120] to [55..75] by fullShrinkSeconds, starting 30s after grace
                int full = config.getFullShrinkSeconds();
                int sinceGrace = after - grace;
                int pre = Math.max(0, 30 - Math.max(0, sinceGrace)); // remaining pre-shrink seconds
                int sinceShrink = Math.max(0, sinceGrace - 30);
                double f = Math.min(1.0, (double)sinceShrink / Math.max(1, full));
                int bandBot = (int)Math.round(40 + (55 - 40) * f);
                int bandTop = (int)Math.round(120 + (75 - 120) * f);
                // Soft border: compute current radius from our schedule
                double startRadius = 200.0;
                // SkyWars: stop 30s before hard cap at 10 diameter (radius 5)
                double endRadius = 5.0;
                int durationSeconds = Math.max(1, hard - grace - 60);
                double bradius;
                if (sinceGrace < 30) {
                    bradius = startRadius;
                } else {
                    double f2 = Math.min(1.0, (double)sinceShrink / Math.max(1, durationSeconds));
                    bradius = startRadius + (endRadius - startRadius) * f2;
                }
                String timer = ChatColor.AQUA + String.format("%d:%02d", remaining / 60, remaining % 60);
                String border = ChatColor.GRAY + " | " + ChatColor.WHITE + "Radius: " + ChatColor.YELLOW + (int)Math.round(bradius);
                String safe = ChatColor.GRAY + " | " + ChatColor.WHITE + "Safe Y: " + ChatColor.GOLD + bandBot + ChatColor.WHITE + "-" + ChatColor.GOLD + bandTop;
                String preMsg = sinceGrace < 30 ? ChatColor.GRAY + " | " + ChatColor.WHITE + "Shrink in: " + ChatColor.YELLOW + (30 - Math.max(0, sinceGrace)) + "s" : "";
                msg = timer + border + safe + preMsg;
            }
            for (Player p : w.getPlayers()) {
                try { p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new net.md_5.bungee.api.chat.TextComponent(msg)); } catch (Throwable ignored) {}
            }
            t += 1; if (t > hard + 5 + preStart) cancel();
        } }.runTaskTimer(plugin, 20L, 20L);

        // Hard cap: award win points to all survivors, then end the round (starts counting after pre-start)
        new BukkitRunnable(){ int t=0; @Override public void run(){ if (!running || !w.getName().equals(currentWorldName)) { cancel(); return; } if (t++ >= (preStart + config.getRoundHardCapSeconds())) {
            for (UUID id : new HashSet<>(aliveAll)) {
                try { plugin.getPointsService().addPoints(id, 200); } catch (Throwable ignored) {}
                Player p = Bukkit.getPlayer(id);
                if (p != null) try { p.sendMessage(ChatColor.AQUA + "+200 Points (survived)"); } catch (Throwable ignored) {}
            }
            try { Bukkit.broadcastMessage(ChatColor.YELLOW + "Round ended. Win points awarded to all survivors."); } catch (Throwable ignored) {}
            endRound(true);
            cancel();
        } } }.runTaskTimer(plugin, 20L, 20L);
    }

    private void startShrinks(World w, Location center, int startDiameter) {
        final double start = startDiameter; // kept for Y-band schedule reference
        final double end = Math.max(1, config.getFinalBorderDiameter());
        final int full = config.getFullShrinkSeconds() * 20;
        final int startBot = 40;
        final int startTop = 120;
        final int endBot = 55;
        final int endTop = 75;

        // Start custom soft border (damage-only, particles) and run it to end 30s before hard cap
        try {
            int durationSeconds = Math.max(1, config.getRoundHardCapSeconds() - config.getGraceSeconds() - 60);
            double startRadius = 200.0; // start at 200 radius (400 diameter)
            double endRadius = 5.0; // stop at radius 5 (10 diameter) 30s before hard cap and hold until end
            softBorder = new com.jumpcat.core.border.SoftBorder(
                    plugin,
                    w,
                    center,
                    startRadius,
                    endRadius,
                    durationSeconds * 20,
                    10, // enforce every 10 ticks
                    20, // particles every second
                    2.0, // baseDps: 1 heart/sec
                    4.0, // maxDps: 2 hearts/sec
                    5.0, // reach max at 5 blocks outside
                    true // show particles
            );
            softBorder.start();
        } catch (Throwable ignored) {}

        // Vertical band shrink and damage tick (every second)
        new BukkitRunnable(){ int t=0; @Override public void run(){ if (!running || !w.getName().equals(currentWorldName)) { cancel(); return; }
            double f = Math.min(1.0, (double)t/full);
            int bandBot = (int)Math.round(startBot + (endBot - startBot) * f);
            int bandTop = (int)Math.round(startTop + (endTop - startTop) * f);
            // Damage anyone outside [bandBot, bandTop] if PVP enabled
            boolean pvp = w.getPVP();
            if (pvp) {
                for (Player p : w.getPlayers()) {
                    double y = p.getLocation().getY();
                    if (y < bandBot || y > bandTop) {
                        try { p.damage(2.0); } catch (Throwable ignored) {}
                    }
                }
            }
            t+=20; if (t >= full) cancel();
        } }.runTaskTimer(plugin, 0L, 20L);

        // After full shrink, keep enforcing [55,75] until round ends
        new BukkitRunnable(){ @Override public void run(){ if (!running || !w.getName().equals(currentWorldName)) { cancel(); return; }
            boolean pvp = w.getPVP();
            for (Player p : w.getPlayers()) {
                double y = p.getLocation().getY();
                if (pvp && (y < 55 || y > 75)) {
                    try { p.damage(2.0); } catch (Throwable ignored) {}
                }
            }
        } }.runTaskTimer(plugin, config.getFullShrinkSeconds() * 20L + 20L, 20L);
    }

    private void giveKit(Player p, String teamKey) {
        Material terracotta = teamTerracotta(teamKey);
        // Armor
        try { p.getInventory().setChestplate(new ItemStack(Material.IRON_CHESTPLATE)); } catch (Throwable ignored) {}
        // Hotbar: 0 sword,1 pick,2 terracotta,3 steak
        p.getInventory().setItem(0, new ItemStack(Material.STONE_SWORD));
        p.getInventory().setItem(1, new ItemStack(Material.IRON_PICKAXE));
        p.getInventory().setItem(2, new ItemStack(terracotta, 64));
        p.getInventory().setItem(3, new ItemStack(Material.COOKED_BEEF, 16));
    }

    private Material teamTerracotta(String key) {
        ChatColor c = teams.getTeamColor(key);
        if (c == ChatColor.RED) return Material.RED_TERRACOTTA;
        if (c == ChatColor.BLUE) return Material.BLUE_TERRACOTTA;
        if (c == ChatColor.GREEN) return Material.GREEN_TERRACOTTA;
        if (c == ChatColor.YELLOW) return Material.YELLOW_TERRACOTTA;
        if (c == ChatColor.GOLD) return Material.ORANGE_TERRACOTTA;
        if (c == ChatColor.AQUA) return Material.CYAN_TERRACOTTA;
        if (c == ChatColor.LIGHT_PURPLE) return Material.MAGENTA_TERRACOTTA;
        if (c == ChatColor.WHITE) return Material.WHITE_TERRACOTTA;
        if (c == ChatColor.BLACK) return Material.BLACK_TERRACOTTA;
        if (c == ChatColor.DARK_GRAY || c == ChatColor.GRAY) return Material.GRAY_TERRACOTTA;
        if (c == ChatColor.DARK_BLUE) return Material.BLUE_TERRACOTTA;
        if (c == ChatColor.DARK_GREEN) return Material.GREEN_TERRACOTTA;
        return Material.WHITE_TERRACOTTA;
    }

    public void onPlayerDeath(UUID victimId) {
        onPlayerDeath(victimId, null);
    }

    public void onPlayerDeath(UUID victimId, UUID killerId) {
        if (!running) return;
        // Kill points
        if (killerId != null) {
            try {
                plugin.getPointsService().addPoints(killerId, 75);
                Player kp = Bukkit.getPlayer(killerId);
                if (kp != null) kp.sendMessage(ChatColor.AQUA + "+75 Points (kill)");
            } catch (Throwable ignored) {}
        }
        // Survival drip: +7 to all remaining alive except victim
        for (UUID id : new HashSet<>(aliveAll)) {
            if (!id.equals(victimId)) {
                try {
                    plugin.getPointsService().addPoints(id, 7);
                    Player rp = Bukkit.getPlayer(id);
                    if (rp != null) rp.sendMessage(ChatColor.AQUA + "+7 Points (survival)");
                } catch (Throwable ignored) {}
            }
        }
        // Remove victim
        aliveAll.remove(victimId);
        for (Set<UUID> set : aliveByTeam.values()) set.remove(victimId);
        // Check elimination
        String lastTeam = null; int aliveTeams = 0;
        for (Map.Entry<String, Set<UUID>> e : aliveByTeam.entrySet()) {
            if (!e.getValue().isEmpty()) { aliveTeams++; lastTeam = e.getKey(); }
        }
        if (aliveTeams <= 1) {
            if (lastTeam != null) {
                // Win points to surviving winners
                for (UUID id : new HashSet<>(aliveByTeam.getOrDefault(lastTeam, Collections.emptySet()))) {
                    try {
                        plugin.getPointsService().addPoints(id, 200);
                        Player wp = Bukkit.getPlayer(id);
                        if (wp != null) wp.sendMessage(ChatColor.AQUA + "+200 Points (team victory)");
                    } catch (Throwable ignored) {}
                }
                try {
                    String label = teams.getTeamColor(lastTeam) + "" + ChatColor.BOLD + teams.getTeamLabel(lastTeam);
                    String title = label + ChatColor.RESET + ChatColor.WHITE + " wins!";
                    String subtitle = ChatColor.YELLOW + "SkyWars";
                    for (Player pp : Bukkit.getOnlinePlayers()) { try { pp.sendTitle(title, subtitle, 0, 60, 10); } catch (Throwable ignored) {} }
                } catch (Throwable ignored) {}
            }
            endRound(false);
        }
    }

    private void endRound(boolean byTimer) {
        if (!running) return;
        if (endingRound) return;
        endingRound = true;
        // Delay a bit to let titles show
        new BukkitRunnable(){ @Override public void run(){
            roundIndex++;
            String toUnload = currentWorldName;
            currentWorldName = null;
            // Stop soft border if active
            try { if (softBorder != null) softBorder.stop(); } catch (Throwable ignored) {} finally { softBorder = null; }
            // Teleport and reset players out of any SkyWars round world
            World lobby = plugin.getLobbyManager().getLobbyWorld();
            org.bukkit.Location lobbySpawn = lobby != null ? lobby.getSpawnLocation() : Bukkit.getWorlds().get(0).getSpawnLocation();
            for (World ww : Bukkit.getWorlds()) {
                if (!ww.getName().startsWith("skywars_r")) continue;
                for (Player p : ww.getPlayers()) {
                    try {
                        p.getInventory().clear();
                        p.getInventory().setArmorContents(null);
                        p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
                        p.setHealth(20.0);
                        p.setFoodLevel(20);
                        p.setSaturation(20);
                        p.setLevel(0);
                        p.setExp(0f);
                        p.setTotalExperience(0);
                        p.setGameMode(GameMode.ADVENTURE);
                        p.teleport(lobbySpawn);
                    } catch (Throwable ignored) {}
                }
            }
            try { manager.unloadAndDelete(toUnload); } catch (Throwable ignored) {}

            if (roundIndex < config.getRounds()) {
                Bukkit.broadcastMessage(ChatColor.YELLOW + "Next SkyWars round starting in 10s...");
                new BukkitRunnable(){ @Override public void run(){ if (!running) return; beginRound(Bukkit.getConsoleSender()); } }.runTaskLater(plugin, 200L);
            } else {
                running = false; CURRENT = null;
                try { Bukkit.broadcastMessage(ChatColor.AQUA + "SkyWars series complete!"); } catch (Throwable ignored) {}
                try { showTeamStandings(); } catch (Throwable ignored) {}
                try { plugin.getSidebarManager().setGameStatus(getDisplayName(), "-", false); } catch (Throwable ignored) {}
            }
        } }.runTaskLater(plugin, 40L);
    }

    private void showTeamStandings() {
        // Sum points per team (offline names resolved) and print with team colors/labels
        Map<String, Integer> teamTotals = new LinkedHashMap<>();
        for (String key : teams.listTeamKeys()) {
            int sum = 0;
            for (String name : teams.getTeamMembers(key)) {
                UUID id = Bukkit.getOfflinePlayer(name).getUniqueId();
                sum += plugin.getPointsService().getPoints(id);
            }
            if (sum > 0) teamTotals.put(key, sum);
        }
        List<Map.Entry<String,Integer>> rows = new ArrayList<>(teamTotals.entrySet());
        rows.sort((a,b) -> Integer.compare(b.getValue(), a.getValue()));
        if (!rows.isEmpty()) {
            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage(ChatColor.AQUA + "Team Standings:");
            int i = 1;
            for (Map.Entry<String,Integer> e : rows) {
                String label = teams.getTeamColor(e.getKey()) + "" + ChatColor.BOLD + teams.getTeamLabel(e.getKey()) + ChatColor.RESET + ChatColor.WHITE;
                Bukkit.broadcastMessage(ChatColor.YELLOW + "#"+i+" " + label + ChatColor.GRAY + " | " + ChatColor.WHITE + "Points: " + ChatColor.AQUA + e.getValue());
                i++;
            }
            Bukkit.broadcastMessage("");
        }
    }

    private void deleteRecursive(File f) {
        if (!f.exists()) return;
        File[] list = f.listFiles();
        if (list != null) for (File c : list) deleteRecursive(c);
        try { java.nio.file.Files.deleteIfExists(f.toPath()); } catch (Exception ignored) {}
    }
}
