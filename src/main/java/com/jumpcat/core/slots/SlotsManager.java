package com.jumpcat.core.slots;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class SlotsManager {
    private final JavaPlugin plugin;
    private int cap = -1; // -1 means use server default

    public SlotsManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File dir = plugin.getDataFolder();
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, "settings.yml");
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        this.cap = cfg.getInt("slots.cap", -1);
    }

    public void save() {
        File dir = plugin.getDataFolder();
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, "settings.yml");
        FileConfiguration cfg = new YamlConfiguration();
        cfg.set("slots.cap", this.cap);
        try { cfg.save(file); } catch (IOException ignored) { }
    }

    public int getCap() { return cap; }

    public void setCap(int cap) {
        this.cap = cap;
        save();
    }
}
