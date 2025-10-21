package com.jumpcat.core.game.uhc;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class UhcMeetupManager {
    private final JavaPlugin plugin;
    private final UhcMeetupConfig config;

    public UhcMeetupManager(JavaPlugin plugin, UhcMeetupConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void cleanupStaleClones() {
        try {
            File root = new File(".");
            File[] dirs = root.listFiles((f) -> f.isDirectory() && (f.getName().startsWith("uhc_meetup_r1") || f.getName().startsWith("uhc_meetup_r2")));
            if (dirs != null) {
                for (File d : dirs) {
                    try {
                        World w = Bukkit.getWorld(d.getName());
                        if (w != null) Bukkit.unloadWorld(w, false);
                        deleteRecursive(d);
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }

    public boolean cloneTemplate(String destName) {
        File src = new File(config.templateWorld);
        File dst = new File(destName);
        if (!src.exists() || !src.isDirectory()) return false;
        if (dst.exists()) deleteRecursive(dst);
        boolean ok = copyRecursive(src, dst);
        // Ensure new world gets a fresh UUID and no leftover locks
        try { Files.deleteIfExists(new File(dst, "uid.dat").toPath()); } catch (IOException ignored) {}
        try { Files.deleteIfExists(new File(dst, "session.lock").toPath()); } catch (IOException ignored) {}
        return ok;
    }

    public World loadWorld(String name) {
        return Bukkit.createWorld(new WorldCreator(name));
    }

    public void unloadAndDelete(String name) {
        try {
            World w = Bukkit.getWorld(name);
            if (w != null) Bukkit.unloadWorld(w, true);
        } catch (Throwable ignored) {}
        deleteRecursive(new File(name));
    }

    public void setupBorder(World w, Location center, int startDiameter) {
        try {
            WorldBorder wb = w.getWorldBorder();
            wb.setCenter(center);
            wb.setSize(startDiameter);
        } catch (Throwable ignored) {}
    }

    private boolean copyRecursive(File src, File dst) {
        try {
            Files.walk(src.toPath()).forEach(path -> {
                try {
                    File target = new File(dst, src.toPath().relativize(path).toString());
                    if (path.toFile().isDirectory()) {
                        target.mkdirs();
                    } else {
                        target.getParentFile().mkdirs();
                        Files.copy(path, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException ignored) {}
            });
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void deleteRecursive(File f) {
        if (!f.exists()) return;
        File[] list = f.listFiles();
        if (list != null) {
            for (File c : list) deleteRecursive(c);
        }
        try { Files.deleteIfExists(f.toPath()); } catch (IOException ignored) {}
    }
}
