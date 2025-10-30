package com.jumpcat.core.game.skywars;

import com.jumpcat.core.lobby.VoidChunkGenerator;
import org.bukkit.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class SkyWarsManager {
    private final JavaPlugin plugin;
    private String templateWorld = "skywars_template";

    public SkyWarsManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void ensureTemplateWorld() {
        World w = Bukkit.getWorld(templateWorld);
        if (w == null) {
            WorldCreator wc = new WorldCreator(templateWorld);
            wc.environment(World.Environment.NORMAL);
            wc.type(WorldType.NORMAL);
            wc.generator(new VoidChunkGenerator());
            w = Bukkit.createWorld(wc);
            if (w != null) {
                // No platform for SkyWars template; spawns are in the cloned round worlds
                w.setSpawnLocation(new Location(w, 0, 65, 0));
            }
        }
        if (w != null) {
            try { w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false); } catch (Throwable ignored) {}
            try { w.setGameRule(GameRule.DO_WEATHER_CYCLE, false); } catch (Throwable ignored) {}
            try { w.setGameRule(GameRule.DO_MOB_SPAWNING, false); } catch (Throwable ignored) {}
            try { w.setGameRule(GameRule.RANDOM_TICK_SPEED, 0); } catch (Throwable ignored) {}
            try { w.setPVP(false); } catch (Throwable ignored) {}
            w.setTime(6000);
        }
    }

    private void buildSpawnPlatform(World world, Location center) {
        int y = center.getBlockY();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                world.getBlockAt(center.getBlockX()+x, y, center.getBlockZ()+z).setType(Material.SMOOTH_STONE, false);
            }
        }
    }

    public World getTemplateWorld() {
        return Bukkit.getWorld(templateWorld);
    }

    public void setTemplateWorld(String name) {
        if (name != null && !name.isEmpty()) this.templateWorld = name;
    }

    public Location getTemplateSpawn() {
        World w = getTemplateWorld();
        if (w == null) return null;
        return new Location(w, 0.5, 65, 0.5);
    }

    // Clone/load/unload utilities (mirror UHC manager behavior)
    public boolean cloneTemplate(String destName) {
        File src = new File(templateWorld);
        File dst = new File(destName);
        if (!src.exists() || !src.isDirectory()) return false;
        if (dst.exists()) deleteRecursive(dst);
        boolean ok = copyRecursive(src, dst);
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
