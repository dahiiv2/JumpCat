package com.jumpcat.core.game.uhc;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class UhcMeetupConfig {
    public String templateWorld;
    public boolean centerAtSpawn;
    public int graceSeconds;
    public int shrinkSeconds;
    public int borderBase;
    public int borderPerTeam;
    public int finalDiameter;
    public int yCeilStart;
    public int yCeilEnd;
    public int yCeilShrinkSeconds;
    public int yCeilHoldFinalSeconds;
    public int scoreKill;
    public int scoreSurvivalPerDeath;
    public int scoreWinAlive;
    public Set<String> whitelistBlocks = new HashSet<>();

    private final JavaPlugin plugin;

    public UhcMeetupConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File dir = plugin.getDataFolder();
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, "uhc.yml");
        FileConfiguration cfg;
        if (file.exists()) cfg = YamlConfiguration.loadConfiguration(file); else cfg = new YamlConfiguration();
        this.templateWorld = cfg.getString("templateWorld", "uhc_meetup");
        this.centerAtSpawn = cfg.getBoolean("centerAtSpawn", true);
        this.graceSeconds = cfg.getInt("graceSeconds", 10);
        this.shrinkSeconds = cfg.getInt("shrinkSeconds", 270);
        this.borderBase = cfg.getInt("borderBase", 50);
        this.borderPerTeam = cfg.getInt("borderPerTeam", 25);
        this.finalDiameter = cfg.getInt("finalDiameter", 3);
        this.yCeilStart = cfg.getInt("yCeilStart", 150);
        this.yCeilEnd = cfg.getInt("yCeilEnd", 80);
        this.yCeilShrinkSeconds = cfg.getInt("yCeilShrinkSeconds", 210);
        this.yCeilHoldFinalSeconds = cfg.getInt("yCeilHoldFinalSeconds", 60);
        this.scoreKill = cfg.getInt("scoring.kill", 75);
        this.scoreSurvivalPerDeath = cfg.getInt("scoring.survivalPerDeath", 7);
        this.scoreWinAlive = cfg.getInt("scoring.winAlive", 200);
        this.whitelistBlocks.clear();
        this.whitelistBlocks.addAll(cfg.getStringList("whitelistBlocks"));
        if (this.whitelistBlocks.isEmpty()) {
            this.whitelistBlocks.add("OAK_PLANKS");
            this.whitelistBlocks.add("CRAFTING_TABLE");
            this.whitelistBlocks.add("ENCHANTING_TABLE");
            this.whitelistBlocks.add("ANVIL");
            this.whitelistBlocks.add("COBWEB");
            this.whitelistBlocks.add("SHORT_GRASS");
            this.whitelistBlocks.add("TALL_GRASS");
            this.whitelistBlocks.add("OBSIDIAN");
        } else {
            // Backfill required blocks if missing
            if (!this.whitelistBlocks.contains("COBWEB")) this.whitelistBlocks.add("COBWEB");
        }
        // Always save config to ensure it's up-to-date with code defaults (only if file doesn't exist, or update missing keys)
        YamlConfiguration out = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        // Update missing keys with current values (preserves manual edits, adds missing defaults)
        if (!out.contains("templateWorld")) out.set("templateWorld", this.templateWorld);
        if (!out.contains("centerAtSpawn")) out.set("centerAtSpawn", this.centerAtSpawn);
        if (!out.contains("graceSeconds")) out.set("graceSeconds", this.graceSeconds);
        if (!out.contains("shrinkSeconds")) out.set("shrinkSeconds", this.shrinkSeconds);
        if (!out.contains("borderBase")) out.set("borderBase", this.borderBase);
        if (!out.contains("borderPerTeam")) out.set("borderPerTeam", this.borderPerTeam);
        if (!out.contains("finalDiameter")) out.set("finalDiameter", this.finalDiameter);
        if (!out.contains("yCeilStart")) out.set("yCeilStart", this.yCeilStart);
        if (!out.contains("yCeilEnd")) out.set("yCeilEnd", this.yCeilEnd);
        if (!out.contains("yCeilShrinkSeconds")) out.set("yCeilShrinkSeconds", this.yCeilShrinkSeconds);
        if (!out.contains("yCeilHoldFinalSeconds")) out.set("yCeilHoldFinalSeconds", this.yCeilHoldFinalSeconds);
        if (!out.contains("scoring.kill")) out.set("scoring.kill", this.scoreKill);
        if (!out.contains("scoring.survivalPerDeath")) out.set("scoring.survivalPerDeath", this.scoreSurvivalPerDeath);
        if (!out.contains("scoring.winAlive")) out.set("scoring.winAlive", this.scoreWinAlive);
        if (!out.contains("whitelistBlocks")) out.set("whitelistBlocks", this.whitelistBlocks.stream().toList());
        try { out.save(file); } catch (IOException ignored) {}
    }
}
