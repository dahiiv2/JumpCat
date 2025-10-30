package com.jumpcat.core.game.battlebox;

import com.jumpcat.core.game.GameController;
import com.jumpcat.core.teams.TeamManager;
import com.jumpcat.core.points.PointsService;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Item;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;
import net.kyori.adventure.text.Component;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;

import java.util.*;

public class BattleBoxController implements GameController {
    public static volatile BattleBoxController CURRENT = null;
    private final org.bukkit.plugin.Plugin plugin;
    private final BattleBoxManager manager;
    private final TeamManager teams;
    private final PointsService points;
    private boolean running = false;
    private BattleBoxRuntime currentRuntime;
    // Series (round-robin) state
    private boolean seriesRunning = false;
    private final Map<String, BattleBoxRuntime> activeRuntimes = new LinkedHashMap<>(); // arena.id -> rt
    private List<List<String[]>> rounds = new ArrayList<>(); // each round: list of pair [teamA, teamB]
    private int roundIndex = -1;
    private BukkitRunnable roundTimer;
    private BukkitRunnable prepTimer;
    // Track arena usage per team within a series to avoid repeats
    private final Map<String, Set<String>> seriesArenaHistory = new HashMap<>(); // teamKey -> arenaIds used

    public BattleBoxController(org.bukkit.plugin.Plugin plugin, BattleBoxManager manager, TeamManager teams, PointsService points) {
        this.plugin = plugin;
        this.manager = manager;
        this.teams = teams;
        this.points = points;
    }

    // ========== Round Robin Series (multi-arena synchronized) ==========
    public void startSeries(CommandSender initiator) {
        if (running || seriesRunning) { initiator.sendMessage(ChatColor.RED + "Battle Box already running."); return; }
        // Build team pool
        List<String> pool = new ArrayList<>();
        for (String key : teams.listTeamKeys()) {
            if (!teams.getTeamMembers(key).isEmpty()) pool.add(key);
        }
        if (pool.size() < 2) { initiator.sendMessage(ChatColor.RED + "Need at least two non-empty teams."); return; }
        // Collect configured arenas
        List<BattleBoxManager.Arena> arenas = new ArrayList<>();
        for (BattleBoxManager.Arena a : manager.listArenas()) if (a.isConfigured()) arenas.add(a);
        if (arenas.isEmpty()) { initiator.sendMessage(ChatColor.RED + "No configured Battle Box arenas."); return; }
        // Build schedule and start series
        buildRoundRobin(pool);
        if (rounds.isEmpty()) { initiator.sendMessage(ChatColor.RED + "Unable to build schedule."); return; }
        seriesRunning = true;
        CURRENT = this;
        roundIndex = -1;
        seriesArenaHistory.clear();
        initiator.sendMessage(ChatColor.YELLOW + "Starting Battle Box round-robin with " + pool.size() + " teams across up to " + arenas.size() + " arenas.");
        nextRound(arenas);
    }

    private void buildRoundRobin(List<String> teamKeys) {
        rounds.clear();
        List<String> list = new ArrayList<>(teamKeys);
        if (list.size() % 2 == 1) list.add("BYE");
        int n = list.size();
        int roundsCount = n - 1;
        int half = n / 2;
        for (int r = 0; r < roundsCount; r++) {
            List<String[]> matches = new ArrayList<>();
            for (int i = 0; i < half; i++) {
                String a = list.get(i);
                String b = list.get(n - 1 - i);
                if (!"BYE".equals(a) && !"BYE".equals(b)) matches.add(new String[]{a, b});
            }
            rounds.add(matches);
            // rotate (keep first fixed)
            String last = list.remove(n - 1);
            list.add(1, last);
        }
    }

    private void nextRound(List<BattleBoxManager.Arena> arenas) {
        roundIndex++;
        try { ((com.jumpcat.core.JumpCatPlugin)plugin).getSidebarManager().setGameStatus(getDisplayName(), (roundIndex+1) + "/" + rounds.size(), true); } catch (Throwable ignored) {}
        if (roundIndex >= rounds.size()) {
            seriesRunning = false;
            Bukkit.broadcastMessage(ChatColor.AQUA + "Battle Box series complete!");
            // TP everyone in battle_box back to lobby spawn (fallback: battle_box spawn) in ADVENTURE
            org.bukkit.World bb = manager.getWorld();
            org.bukkit.Location dest = null;
            var lobbyW = Bukkit.getWorld("lobby");
            if (lobbyW != null) dest = lobbyW.getSpawnLocation();
            if (dest == null && bb != null) dest = bb.getSpawnLocation();
            if (bb != null && dest != null) {
                for (Player p : bb.getPlayers()) {
                    try { p.getInventory().clear(); p.getInventory().setArmorContents(null); p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType())); } catch (Throwable ignored) {}
                    p.setHealth(20.0);
                    p.setFoodLevel(20);
                    p.setSaturation(20);
                    p.setLevel(0);
                    p.setExp(0f);
                    p.setTotalExperience(0);
                    p.setGameMode(GameMode.ADVENTURE);
                    p.teleport(dest);
                }
            }
            showTeamStandings();
            try { ((com.jumpcat.core.JumpCatPlugin)plugin).getSidebarManager().setGameStatus(getDisplayName(), "-", false); } catch (Throwable ignored) {}
            CURRENT = null;
            return;
        }
        // Assign matches to arenas up to capacity (rotate to avoid repeats)
        activeRuntimes.clear();
        var world = manager.getWorld();
        List<String[]> matches = rounds.get(roundIndex);
        // Prepare arena list per match preferring arenas not yet used by either team this series
        Set<String> usedThisRound = new HashSet<>();
        for (String[] m : matches) {
            BattleBoxManager.Arena arena = pickArenaForMatch(arenas, m[0], m[1], usedThisRound);
            if (arena == null) break;
            usedThisRound.add(arena.id);
            BattleBoxRuntime rt = new BattleBoxRuntime(world, arena);
            String teamA = m[0], teamB = m[1];
            for (String name : teams.getTeamMembers(teamA)) { Player p = Bukkit.getPlayerExact(name); if (p != null && p.isOnline()) rt.addPlayer(p.getUniqueId(), 'A'); }
            for (String name : teams.getTeamMembers(teamB)) { Player p = Bukkit.getPlayerExact(name); if (p != null && p.isOnline()) rt.addPlayer(p.getUniqueId(), 'B'); }
            if (rt.teamMembers('A').isEmpty() || rt.teamMembers('B').isEmpty()) continue;
            activeRuntimes.put(arena.id, rt);
            // record arena usage
            seriesArenaHistory.computeIfAbsent(teamA, k -> new HashSet<>()).add(arena.id);
            seriesArenaHistory.computeIfAbsent(teamB, k -> new HashSet<>()).add(arena.id);
            for (UUID id : rt.teamMembers('A')) { Player p = Bukkit.getPlayer(id); if (p != null) { if (arena.spawnA != null) p.teleport(arena.spawnA); prepareParticipant(p); } }
            for (UUID id : rt.teamMembers('B')) { Player p = Bukkit.getPlayer(id); if (p != null) { if (arena.spawnB != null) p.teleport(arena.spawnB); prepareParticipant(p); } }
            Bukkit.broadcastMessage(ChatColor.AQUA + "Round " + (roundIndex+1) + ": " + ChatColor.YELLOW + teamLabel(teamA) + ChatColor.WHITE + " vs " + ChatColor.YELLOW + teamLabel(teamB) + ChatColor.GRAY + " @" + arena.id);
        }
        // Put all non-fighting players currently in battle_box into spectator at world spawn for this round
        if (world != null) {
            Set<UUID> participants = new HashSet<>();
            for (BattleBoxRuntime r : activeRuntimes.values()) participants.addAll(union(r.teamMembers('A'), r.teamMembers('B')));
            for (Player p : world.getPlayers()) {
                if (!participants.contains(p.getUniqueId())) {
                    p.setGameMode(GameMode.SPECTATOR);
                    try { p.teleport(world.getSpawnLocation()); } catch (Throwable ignored) {}
                }
            }
            // Clear arrows between rounds from everyone in the battle_box world
            for (Player p : world.getPlayers()) {
                removeAllOf(p, Material.ARROW);
            }
            // Also clear ground arrows (items and projectile arrows)
            clearWorldArrows(world);
        }
        if (activeRuntimes.isEmpty()) { nextRound(arenas); return; }
        // 10s prep titles to all participants
        prepTimer = new BukkitRunnable() {
            int t = 10;
            @Override public void run() {
                if (!seriesRunning) { cancel(); return; }
                for (BattleBoxRuntime rt : activeRuntimes.values()) {
                    for (UUID id : union(rt.teamMembers('A'), rt.teamMembers('B'))) {
                        Player p = Bukkit.getPlayer(id);
                        if (p != null) p.sendTitle(ChatColor.GOLD + "Starting in " + t, ChatColor.YELLOW + "Round " + (roundIndex+1), 0, 20, 0);
                    }
                }
                if (t-- <= 0) { cancel(); beginRoundLive(arenas); }
            }
        }; prepTimer.runTaskTimer(plugin, 0L, 20L);
    }

    private void beginRoundLive(List<BattleBoxManager.Arena> arenas) {
        for (BattleBoxRuntime rt : activeRuntimes.values()) {
            rt.live = true;
            // Generate a single random kit for the whole round and give to all participants in this runtime
            RoundKit kit = generateRoundKit();
            for (UUID id : union(rt.teamMembers('A'), rt.teamMembers('B'))) { Player p = Bukkit.getPlayer(id); if (p != null) applyKit(p, kit); }
        }
        roundTimer = new BukkitRunnable() {
            int ticks = 85;
            @Override public void run() {
                if (!seriesRunning) { cancel(); return; }
                int seconds = Math.max(0, ticks);
                int mm = seconds / 60; int ss = seconds % 60; String time = String.format("%d:%02d", mm, ss);
                for (BattleBoxRuntime rt : activeRuntimes.values()) {
                    for (UUID id : union(rt.teamMembers('A'), rt.teamMembers('B'))) { Player p = Bukkit.getPlayer(id); if (p != null) p.sendActionBar(Component.text(ChatColor.AQUA + "Time: " + ChatColor.WHITE + time)); }
                }
                // End matches that have elimination
                for (BattleBoxRuntime rt : new ArrayList<>(activeRuntimes.values())) {
                    boolean aOut = rt.teamEliminated('A');
                    boolean bOut = rt.teamEliminated('B');
                    if (aOut || bOut) {
                        char winner = aOut && !bOut ? 'B' : (!aOut && bOut ? 'A' : 'X');
                        endMatchInSeries(rt, winner, false);
                        activeRuntimes.values().remove(rt);
                    }
                }
                // Early finish: if no matches left, move to intermission
                if (activeRuntimes.isEmpty()) {
                    cancel();
                    new BukkitRunnable(){ int d=5; @Override public void run(){ if (!seriesRunning){cancel();return;} if (d--<=0){ cancel(); nextRound(arenas); } } }.runTaskTimer(plugin, 20L, 20L);
                    return;
                }
                if (ticks-- <= 0) {
                    cancel();
                    for (BattleBoxRuntime rt : new ArrayList<>(activeRuntimes.values())) endMatchInSeries(rt, 'X', true);
                    activeRuntimes.clear();
                    new BukkitRunnable(){ int d=5; @Override public void run(){ if (!seriesRunning){cancel();return;} if (d--<=0){ cancel(); nextRound(arenas); } } }.runTaskTimer(plugin, 20L, 20L);
                }
            }
        }; roundTimer.runTaskTimer(plugin, 20L, 20L);
    }

    private void endMatchInSeries(BattleBoxRuntime rt, char winnerSide, boolean timeout) {
        if (!timeout && (winnerSide == 'A' || winnerSide == 'B')) {
            Set<UUID> winners = winnerSide == 'A' ? rt.teamMembers('A') : rt.teamMembers('B');
            for (UUID id : winners) points.addPoints(id, 100);
            String winTeam = (winnerSide == 'A') ? labelForSide(rt, 'A') : labelForSide(rt, 'B');
            Bukkit.broadcastMessage(ChatColor.GREEN + "Winner: " + winTeam + ChatColor.WHITE + " (+100 each)");
        } else if (timeout) {
            Bukkit.broadcastMessage(ChatColor.YELLOW + "Match ended (timer): no win bonus.");
        }
        for (UUID id : union(rt.teamMembers('A'), rt.teamMembers('B'))) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                try { p.getInventory().clear(); p.getInventory().setArmorContents(null); p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType())); } catch (Throwable ignored) {}
                p.setGameMode(GameMode.SPECTATOR);
                if (rt.arena != null && rt.arena.spectatorSpawn != null) p.teleport(rt.arena.spectatorSpawn);
            }
        }
    }

    private String labelForSide(BattleBoxRuntime rt, char side) {
        String label = side == 'A' ? "A" : "B";
        Set<UUID> ids = side == 'A' ? rt.teamMembers('A') : rt.teamMembers('B');
        for (UUID id : ids) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                String key = teams.getPlayerTeamKey(p).orElse(null);
                if (key != null) return ChatColor.YELLOW + teamLabel(key);
            }
        }
        return ChatColor.YELLOW + label;
    }

    @Override
    public String getId() { return "battlebox"; }

    @Override
    public String getDisplayName() { return "Battle Box"; }

    @Override
    public void prepare(CommandSender initiator) {
        manager.ensureWorld();
        initiator.sendMessage(ChatColor.YELLOW + "Battle Box world ready.");
    }

    @Override
    public void start(CommandSender initiator) {
        if (running || seriesRunning) { initiator.sendMessage(ChatColor.RED + "Battle Box already running."); return; }
        // Auto-detect: if we have 3+ non-empty teams OR 2+ arenas, run the round-robin series
        int nonEmptyCount = 0;
        for (String key : teams.listTeamKeys()) {
            if (!teams.getTeamMembers(key).isEmpty()) nonEmptyCount++;
            if (nonEmptyCount >= 3) break;
        }
        int configuredArenas = 0; for (BattleBoxManager.Arena a : manager.listArenas()) if (a.isConfigured()) configuredArenas++;
        if (nonEmptyCount >= 3 || configuredArenas >= 2) { startSeries(initiator); return; }
        // Pick first configured arena
        BattleBoxManager.Arena arena = null;
        for (BattleBoxManager.Arena a : manager.listArenas()) { if (a.isConfigured()) { arena = a; break; } }
        if (arena == null) { initiator.sendMessage(ChatColor.RED + "No configured Battle Box arenas."); return; }

        // Pick first two non-empty teams
        List<String> nonEmpty = new ArrayList<>();
        for (String key : teams.listTeamKeys()) {
            if (!teams.getTeamMembers(key).isEmpty()) nonEmpty.add(key);
            if (nonEmpty.size() >= 2) break;
        }
        if (nonEmpty.size() < 2) { initiator.sendMessage(ChatColor.RED + "Need at least two non-empty teams."); return; }

        String teamA = nonEmpty.get(0);
        String teamB = nonEmpty.get(1);

        var world = manager.getWorld();
        if (world == null) { initiator.sendMessage(ChatColor.RED + "battle_box world not loaded."); return; }
        BattleBoxRuntime rt = new BattleBoxRuntime(world, arena);

        // Collect participants from current online players in those teams
        for (String name : teams.getTeamMembers(teamA)) {
            Player p = Bukkit.getPlayerExact(name);
            if (p != null && p.isOnline()) rt.addPlayer(p.getUniqueId(), 'A');
        }
        for (String name : teams.getTeamMembers(teamB)) {
            Player p = Bukkit.getPlayerExact(name);
            if (p != null && p.isOnline()) rt.addPlayer(p.getUniqueId(), 'B');
        }
        if (rt.teamMembers('A').isEmpty() || rt.teamMembers('B').isEmpty()) { initiator.sendMessage(ChatColor.RED + "Both teams must have online players."); return; }

        this.currentRuntime = rt;
        running = true;
        CURRENT = this;
        try { ((com.jumpcat.core.JumpCatPlugin)plugin).getSidebarManager().setGameStatus(getDisplayName(), "1/1", true); } catch (Throwable ignored) {}

        // Teleport participants to spawns and set initial state (freeze period)
        for (UUID id : rt.teamMembers('A')) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                if (arena.spawnA != null) p.teleport(arena.spawnA);
                prepareParticipant(p);
            }
        }
        for (UUID id : rt.teamMembers('B')) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                if (arena.spawnB != null) p.teleport(arena.spawnB);
                prepareParticipant(p);
            }
        }

        Bukkit.broadcastMessage(ChatColor.AQUA + "Battle Box: " + ChatColor.YELLOW + teamLabel(teamA) + ChatColor.WHITE + " vs " + ChatColor.YELLOW + teamLabel(teamB));
        startCountdownAndRun(initiator, rt, teamA, teamB);
    }

    @Override
    public void stop(CommandSender initiator) {
        // Stop both single-match and series flows safely
        boolean wasRunning = running || seriesRunning;
        running = false;
        if (seriesRunning) {
            seriesRunning = false;
            try { if (prepTimer != null) prepTimer.cancel(); } catch (Throwable ignored) {}
            try { if (roundTimer != null) roundTimer.cancel(); } catch (Throwable ignored) {}
            activeRuntimes.clear();
        }
        currentRuntime = null;
        CURRENT = null;
        // Teleport everyone in battle_box to lobby spawn (fallback: battle_box spawn) in ADVENTURE
        org.bukkit.World bb = manager.getWorld();
        org.bukkit.Location dest = null;
        var lobbyW = Bukkit.getWorld("lobby");
        if (lobbyW != null) dest = lobbyW.getSpawnLocation();
        if (dest == null && bb != null) dest = bb.getSpawnLocation();
        if (bb != null && dest != null) {
            for (Player p : bb.getPlayers()) { p.setGameMode(GameMode.ADVENTURE); p.teleport(dest); }
        }
        // Show standings as if the game ended
        showTeamStandings();
        try { ((com.jumpcat.core.JumpCatPlugin)plugin).getSidebarManager().setGameStatus(getDisplayName(), "-", false); } catch (Throwable ignored) {}
        initiator.sendMessage(ChatColor.YELLOW + (wasRunning ? "Battle Box stopped." : "Battle Box was not running."));
    }

    @Override
    public String status() {
        return running ? "Running" : "Idle";
    }

    // Runtime getters/setters for listeners
    public BattleBoxRuntime getCurrentRuntime() { return currentRuntime; }
    public void setCurrentRuntime(BattleBoxRuntime rt) { this.currentRuntime = rt; }
    public boolean isRunning() { return running; }
    public boolean isSeriesRunning() { return seriesRunning; }
    public java.util.Collection<BattleBoxRuntime> getActiveRuntimes() { return activeRuntimes.values(); }

    // Helpers
    private void prepareParticipant(Player p) {
        p.setGameMode(GameMode.SURVIVAL);
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.setSaturation(20);
        p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
        // Clear inventory now; kit given at LIVE start
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
    }

    // Random kit generation (same kit for all players each round)
    private static class RoundKit {
        ItemStack[] armor; // 0: boots,1: leggings,2: chest,3: helm as per Bukkit order
        List<ItemStack> contents;
    }

    private RoundKit generateRoundKit() {
        Random r = new Random();
        RoundKit k = new RoundKit();
        // Armor pool: LEATHER/CHAINMAIL/IRON; remove netherite
        Material[] helms = {Material.LEATHER_HELMET, Material.CHAINMAIL_HELMET, Material.IRON_HELMET};
        Material[] chests = {Material.LEATHER_CHESTPLATE, Material.CHAINMAIL_CHESTPLATE, Material.IRON_CHESTPLATE};
        Material[] legs = {Material.LEATHER_LEGGINGS, Material.CHAINMAIL_LEGGINGS, Material.IRON_LEGGINGS};
        Material[] boots = {Material.LEATHER_BOOTS, Material.CHAINMAIL_BOOTS, Material.IRON_BOOTS};
        int tier = weighted(r, new int[]{2,3,5}); // 0..2
        ItemStack helm = new ItemStack(helms[tier]);
        ItemStack chest = new ItemStack(chests[tier]);
        ItemStack leg = new ItemStack(legs[tier]);
        ItemStack boot = new ItemStack(boots[tier]);
        // Enchants chance
        maybeEnchantArmor(r, helm); maybeEnchantArmor(r, chest); maybeEnchantArmor(r, leg); maybeEnchantArmor(r, boot);
        k.armor = new ItemStack[]{boot, leg, chest, helm};

        // Weapons: always include a sword; 30% chance to also include an axe (secondary)
        Material[] swords = {Material.STONE_SWORD, Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD};
        Material[] axes = {Material.STONE_AXE, Material.IRON_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE};
        int meleeTier = weighted(r, new int[]{3,5,2,1});
        ItemStack sword = new ItemStack(swords[meleeTier]);
        maybeEnchantWeapon(r, sword);

        List<ItemStack> contents = new ArrayList<>();
        contents.add(sword);
        if (r.nextInt(100) < 30) {
            ItemStack axe = new ItemStack(axes[meleeTier]);
            maybeEnchantWeapon(r, axe);
            contents.add(axe);
        }
        // Bow 60% chance
        if (r.nextInt(100) < 60) {
            ItemStack bow = new ItemStack(Material.BOW);
            maybeEnchantBow(r, bow);
            contents.add(bow);
            contents.add(new ItemStack(Material.ARROW, 4 + r.nextInt(5))); // 4-8
        }
        // Gaps 1-4
        contents.add(new ItemStack(Material.GOLDEN_APPLE, 1 + r.nextInt(4)));
        // Food
        contents.add(new ItemStack(Material.COOKED_BEEF, 8 + r.nextInt(9))); // 8-16
        // Small utilities: 10% Wind Charge, 10% Splash Harming II
        if (r.nextInt(100) < 10) {
            try { contents.add(new ItemStack(Material.valueOf("WIND_CHARGE"), 1)); } catch (Throwable ignored) {}
        }
        if (r.nextInt(100) < 10) {
            try {
                ItemStack pot = new ItemStack(Material.SPLASH_POTION, 1);
                PotionMeta pm = (PotionMeta) pot.getItemMeta();
                boolean added = false;
                if (pm != null) {
                    try { pm.setBasePotionType(PotionType.HARMING); added = true; } catch (Throwable ignored) {}
                    if (added) {
                        pot.setItemMeta(pm);
                    }
                }
                if (added) contents.add(pot); // only add if set to Harming I successfully
            } catch (Throwable ignored) {}
        }

        k.contents = contents;
        return k;
    }

    private int weighted(Random r, int[] weights) {
        int sum = 0; for (int w : weights) sum += w;
        int x = r.nextInt(sum);
        int acc = 0;
        for (int i = 0; i < weights.length; i++) { acc += weights[i]; if (x < acc) return i; }
        return weights.length - 1;
    }

    private void maybeEnchantArmor(Random r, ItemStack item) {
        if (r.nextInt(100) < 60) item.addUnsafeEnchantment(Enchantment.PROTECTION, 1 + r.nextInt(2)); // Prot I-II
        if (r.nextInt(100) < 40) item.addUnsafeEnchantment(Enchantment.UNBREAKING, 1 + r.nextInt(2)); // Unbreaking I-II
    }

    private void maybeEnchantWeapon(Random r, ItemStack item) {
        if (r.nextInt(100) < 70) item.addUnsafeEnchantment(Enchantment.SHARPNESS, 1 + r.nextInt(2)); // Sharp I-II
    }

    private void maybeEnchantBow(Random r, ItemStack bow) {
        if (r.nextInt(100) < 60) bow.addUnsafeEnchantment(Enchantment.POWER, 1); // Power I
    }

    private void applyKit(Player p, RoundKit kit) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setArmorContents(null);
        inv.setArmorContents(kit.armor);
        for (ItemStack it : kit.contents) inv.addItem(it.clone());
        try { p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 20*180, 0, true, false)); } catch (Throwable ignored) {}
    }

    // Utility: remove all items of a specific material from player inventory (main + offhand)
    private void removeAllOf(Player p, Material mat) {
        try {
            PlayerInventory inv = p.getInventory();
            for (ItemStack it : inv.getContents()) {
                if (it != null && it.getType() == mat) it.setAmount(0);
            }
            ItemStack off = inv.getItemInOffHand();
            if (off != null && off.getType() == mat) inv.setItemInOffHand(null);
        } catch (Throwable ignored) {}
    }

    // Utility: remove arrow entities from the world (item drops and stuck arrows)
    private void clearWorldArrows(org.bukkit.World world) {
        try {
            for (org.bukkit.entity.Entity e : world.getEntities()) {
                if (e instanceof Item) {
                    Item it = (Item) e;
                    if (it.getItemStack() != null && it.getItemStack().getType() == Material.ARROW) {
                        e.remove();
                    }
                } else if (e instanceof AbstractArrow) {
                    e.remove();
                }
            }
        } catch (Throwable ignored) {}
    }

    private void startCountdownAndRun(CommandSender initiator, BattleBoxRuntime rt, String teamA, String teamB) {
        // 10s countdown freeze (listener uses rt.live=false to block movement/damage)
        // Disable player collision during freeze
        try {
            for (UUID id : union(rt.teamMembers('A'), rt.teamMembers('B'))) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) p.setCollidable(false);
            }
        } catch (Throwable ignored) {}
        new BukkitRunnable() {
            int t = 10;
            @Override public void run() {
                if (!running || currentRuntime != rt) { cancel(); return; }
                for (UUID id : union(rt.teamMembers('A'), rt.teamMembers('B'))) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null) p.sendTitle(ChatColor.GOLD + "Starting in " + t, ChatColor.YELLOW + teamLabel(teamA) + ChatColor.WHITE + " vs " + ChatColor.YELLOW + teamLabel(teamB), 0, 20, 0);
                }
                if (t-- <= 0) {
                    cancel();
                    beginLive(rt, teamA, teamB);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void beginLive(BattleBoxRuntime rt, String teamA, String teamB) {
        rt.live = true;
        // Re-enable collision when the round goes live
        try {
            for (UUID id : union(rt.teamMembers('A'), rt.teamMembers('B'))) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) p.setCollidable(true);
            }
        } catch (Throwable ignored) {}
        // Generate a single random kit for all players in this match
        RoundKit kit = generateRoundKit();
        for (UUID id : union(rt.teamMembers('A'), rt.teamMembers('B'))) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) applyKit(p, kit);
        }
        // 1:25 = 85 seconds timer
        new BukkitRunnable() {
            int ticks = 85;
            @Override public void run() {
                if (!running || currentRuntime != rt) { cancel(); return; }
                // End if a team is eliminated
                boolean aOut = rt.teamEliminated('A');
                boolean bOut = rt.teamEliminated('B');
                if (aOut || bOut) {
                    cancel();
                    char winner = aOut && !bOut ? 'B' : (!aOut && bOut ? 'A' : 'X');
                    endRound(rt, winner, teamA, teamB, false);
                    return;
                }
                // Show time remaining to participants
                int seconds = Math.max(0, ticks);
                int mm = seconds / 60; int ss = seconds % 60;
                String time = String.format("%d:%02d", mm, ss);
                for (UUID id : union(rt.teamMembers('A'), rt.teamMembers('B'))) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null) p.sendActionBar(Component.text(ChatColor.AQUA + "Time: " + ChatColor.WHITE + time));
                }
                if (ticks-- <= 0) {
                    cancel();
                    endRound(rt, 'X', teamA, teamB, true);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void endRound(BattleBoxRuntime rt, char winnerSide, String teamA, String teamB, boolean timeout) {
        // Award +200 per winner member if we have a winner and not timeout
        if (!timeout && (winnerSide == 'A' || winnerSide == 'B')) {
            Set<UUID> winners = winnerSide == 'A' ? rt.teamMembers('A') : rt.teamMembers('B');
            for (UUID id : winners) points.addPoints(id, 100);
            Bukkit.broadcastMessage(ChatColor.GREEN + "Winner: " + ChatColor.YELLOW + (winnerSide=='A'?teamLabel(teamA):teamLabel(teamB)) + ChatColor.WHITE + " (+100 each)");
        } else {
            Bukkit.broadcastMessage(ChatColor.YELLOW + "Round ended" + (timeout?" (timer)":"" ) + ": no win bonus.");
        }
        // Reset runtime
        currentRuntime = null;
        running = false;
        CURRENT = null;
        // Send everyone in battle_box world to lobby spawn (fallback: battle_box spawn) so they don't linger in arena
        org.bukkit.Location dest = null;
        var lobbyW = Bukkit.getWorld("lobby");
        if (lobbyW != null) dest = lobbyW.getSpawnLocation();
        if (dest == null && rt.world != null) dest = rt.world.getSpawnLocation();
        if (dest != null && rt.world != null) {
            for (Player p : rt.world.getPlayers()) {
                try { p.getInventory().clear(); p.getInventory().setArmorContents(null); p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType())); } catch (Throwable ignored) {}
                p.setHealth(20.0);
                p.setFoodLevel(20);
                p.setSaturation(20);
                p.setLevel(0);
                p.setExp(0f);
                p.setTotalExperience(0);
                p.setGameMode(GameMode.ADVENTURE);
                p.teleport(dest);
            }
        }
        // TEMP: Show team standings after this match until full series scheduler is added
        showTeamStandings();
        try { ((com.jumpcat.core.JumpCatPlugin)plugin).getSidebarManager().setGameStatus(getDisplayName(), "-", false); } catch (Throwable ignored) {}
    }

    private void showTeamStandings() {
        // Sum points per team (offline names resolved) and print with team colors/labels
        Map<String, Integer> teamTotals = new LinkedHashMap<>();
        for (String key : teams.listTeamKeys()) {
            int sum = 0;
            for (String name : teams.getTeamMembers(key)) {
                UUID id = Bukkit.getOfflinePlayer(name).getUniqueId();
                sum += points.getPoints(id);
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

    // Pick an arena preferring ones not used by either team in this series, fallback to any available
    private BattleBoxManager.Arena pickArenaForMatch(List<BattleBoxManager.Arena> arenas, String teamA, String teamB, Set<String> usedThisRound) {
        Set<String> usedA = seriesArenaHistory.getOrDefault(teamA, Collections.emptySet());
        Set<String> usedB = seriesArenaHistory.getOrDefault(teamB, Collections.emptySet());
        // First pass: arenas unused by both teams
        for (BattleBoxManager.Arena a : arenas) {
            if (!a.isConfigured()) continue;
            if (usedThisRound.contains(a.id)) continue;
            if (!usedA.contains(a.id) && !usedB.contains(a.id)) return a;
        }
        // Second pass: least overlap (unused by one)
        for (BattleBoxManager.Arena a : arenas) {
            if (!a.isConfigured()) continue;
            if (usedThisRound.contains(a.id)) continue;
            if (!usedA.contains(a.id) || !usedB.contains(a.id)) return a;
        }
        // Fallback: first configured
        for (BattleBoxManager.Arena a : arenas) if (a.isConfigured() && !usedThisRound.contains(a.id)) return a;
        return null;
    }

    private Set<UUID> union(Set<UUID> a, Set<UUID> b) {
        Set<UUID> s = new HashSet<>(a);
        s.addAll(b);
        return s;
    }

    private String teamLabel(String key) { return teams.getTeamLabel(key); }
}
