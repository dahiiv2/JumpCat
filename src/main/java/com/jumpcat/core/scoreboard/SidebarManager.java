package com.jumpcat.core.scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import java.util.ArrayList;
import java.util.List;
import com.jumpcat.core.points.PointsService;
import com.jumpcat.core.teams.TeamManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SidebarManager {
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Map<UUID, List<String>> lastEntries = new HashMap<>();
    private final Map<UUID, Integer> kills = new HashMap<>();
    private final Map<UUID, Integer> deaths = new HashMap<>();
    private PointsService pointsService;
    private TeamManager teamManager;
    private volatile String currentGame = "-";
    private volatile String currentRound = "-";
    private volatile boolean gameRunning = false;

    public SidebarManager(Object plugin) {
        // lazy per-player creation
    }

    public void show(Player p) {
        Scoreboard sb = boards.computeIfAbsent(p.getUniqueId(), k -> {
            Scoreboard s = Bukkit.getScoreboardManager().getNewScoreboard();
            createObjective(s);
            createBelowNameHealth(s);
            // Clone teams so tab prefixes/colors work on this board
            if (teamManager != null) try { teamManager.applyToScoreboard(s); } catch (Throwable ignored) {}
            return s;
        });
        p.setScoreboard(sb);
        update(p);
    }

    public void updateAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            update(p);
        }
    }

    public void update(Player p) {
        Scoreboard sb = boards.computeIfAbsent(p.getUniqueId(), k -> {
            Scoreboard s = Bukkit.getScoreboardManager().getNewScoreboard();
            createObjective(s);
            createBelowNameHealth(s);
            if (teamManager != null) try { teamManager.applyToScoreboard(s); } catch (Throwable ignored) {}
            return s;
        });
        // Ensure the player is actually using our scoreboard
        try { p.setScoreboard(sb); } catch (Throwable ignored) {}
        // Always mirror latest team assignments/colors into this board so TAB/nametag stay correct
        if (teamManager != null) try { teamManager.applyToScoreboard(sb); } catch (Throwable ignored) {}
        Objective obj = sb.getObjective("jumpcat");
        if (obj == null) {
            obj = createObjective(sb);
        }
        // Title: JumpCat, bold light orange gradient
        Component title = mm.deserialize("<gradient:#FFD199:#FF9E40><b>JumpCat</b></gradient>");
        try {
            obj.displayName(title);
        } catch (NoSuchMethodError ignored) {
            // Fallback: legacy if running older API
            obj.setDisplayName("§6§lJumpCat");
        }

        // Clear previous sidebar entries only (avoid touching other objectives like health)
        List<String> prev = lastEntries.computeIfAbsent(p.getUniqueId(), k -> new ArrayList<>());
        for (String entry : prev) {
            sb.resetScores(entry);
        }
        prev.clear();

        // Data: rank and players not in spectator
        String rank = "-";
        int playersActive = (int) Bukkit.getOnlinePlayers().stream()
                .filter(pl -> pl.getGameMode() != GameMode.SPECTATOR)
                .count();
        int myPoints = 0;
        if (pointsService != null) {
            try {
                myPoints = pointsService.getPoints(p.getUniqueId());
                int r = pointsService.getRank(p.getUniqueId());
                if (r > 0) rank = "#" + r; else rank = "-";
            } catch (Throwable ignored) { }
        }
        int myKills = kills.getOrDefault(p.getUniqueId(), 0);
        int myDeaths = deaths.getOrDefault(p.getUniqueId(), 0);

        // Build lines top->bottom using descending scores to control order
        int score = 12;
        prev.add(setLine(sb, obj, score--, ""));
        prev.add(setLine(sb, obj, score--, "§fPoints: §b" + myPoints));
        prev.add(setLine(sb, obj, score--, "§fRank: §e" + rank));
        prev.add(setLine(sb, obj, score--, ""));
        prev.add(setLine(sb, obj, score--, "§fPlayers: §a" + playersActive));
        prev.add(setLine(sb, obj, score--, ""));
        String gameLine = "§fGame: " + (gameRunning ? "§a" + currentGame : "§7-" );
        String roundLine = "§fRound: §e" + (gameRunning ? currentRound : "-");
        prev.add(setLine(sb, obj, score--, gameLine));
        prev.add(setLine(sb, obj, score--, roundLine));
        prev.add(setLine(sb, obj, score--, ""));
        // Session stats (reset each game start)
        prev.add(setLine(sb, obj, score--, "§fKills: §a" + myKills));
        prev.add(setLine(sb, obj, score--, "§fDeaths: §c" + myDeaths));
    }

    public Scoreboard getScoreboard() { return Bukkit.getScoreboardManager().getMainScoreboard(); }

    private Objective createObjective(Scoreboard sb) {
        Objective obj = sb.getObjective("jumpcat");
        if (obj == null) {
            try {
                obj = sb.registerNewObjective("jumpcat", "dummy", Component.text("JumpCat"));
            } catch (NoSuchMethodError e) {
                obj = sb.registerNewObjective("jumpcat", "dummy");
                obj.setDisplayName("§6§lJumpCat");
            }
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        return obj;
    }

    private void createBelowNameHealth(Scoreboard sb) {
        Objective health = sb.getObjective("jc_health");
        if (health == null) {
            try {
                health = sb.registerNewObjective("jc_health", "health", Component.text("§c❤"));
            } catch (NoSuchMethodError e) {
                health = sb.registerNewObjective("jc_health", "health");
                health.setDisplayName("§c❤");
            }
            health.setDisplaySlot(DisplaySlot.BELOW_NAME);
        }
    }

    private String setLine(Scoreboard sb, Objective obj, int scoreValue, String text) {
        // Ensure each line entry is unique; use text directly if unique, otherwise append invisible color codes
        String entry = text;
        while (sb.getEntries().contains(entry)) {
            entry = entry + "§r";
        }
        Score score = obj.getScore(entry);
        score.setScore(scoreValue);
        return entry;
    }

    public void setPointsService(PointsService ps) {
        this.pointsService = ps;
    }

    public void hide(Player p) {
        // Optional cleanup hook; Bukkit handles scoreboard GC, but we can drop references
        lastEntries.remove(p.getUniqueId());
        boards.remove(p.getUniqueId());
    }

    public void setTeamManager(TeamManager tm) {
        this.teamManager = tm;
    }

    public void setGameStatus(String gameName, String round, boolean running) {
        if (gameName != null) this.currentGame = gameName;
        if (round != null) this.currentRound = round;
        this.gameRunning = running;
        updateAll();
    }

    public boolean isGameRunning() { return gameRunning; }
    public String getCurrentGameName() { return currentGame; }
    public String getCurrentRoundLabel() { return currentRound; }

    public void incKills(UUID id) { if (id != null) kills.put(id, kills.getOrDefault(id, 0) + 1); }
    public void incDeaths(UUID id) { if (id != null) deaths.put(id, deaths.getOrDefault(id, 0) + 1); }
    public void resetStats() { kills.clear(); deaths.clear(); }
}
