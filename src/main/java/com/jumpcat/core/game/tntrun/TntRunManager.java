package com.jumpcat.core.game.tntrun;

import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.File;

public class TntRunManager {
    private final org.bukkit.plugin.Plugin plugin;
    private final TntRunConfig config;

    public TntRunManager(org.bukkit.plugin.Plugin plugin, TntRunConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public boolean cloneTemplate(String dest) {
        try {
            String src = config.templateWorld;
            File srcDir = new File(src);
            if (!srcDir.exists()) return false;
            File dstDir = new File(dest);
            if (dstDir.exists()) deleteRecursive(dstDir);
            copyRecursive(srcDir, dstDir);
            // remove session/uid files
            new File(dest, "uid.dat").delete();
            new File(dest, "session.lock").delete();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public World loadWorld(String name) {
        try { return Bukkit.createWorld(new org.bukkit.WorldCreator(name)); } catch (Throwable ignored) {}
        return Bukkit.getWorld(name);
    }

    public void unloadAndDelete(String name) {
        try { World w = Bukkit.getWorld(name); if (w != null) Bukkit.unloadWorld(w, false); } catch (Throwable ignored) {}
        try { deleteRecursive(new File(name)); } catch (Throwable ignored) {}
    }

    private void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        File[] list = f.listFiles();
        if (list != null) for (File c : list) deleteRecursive(c);
        try { java.nio.file.Files.deleteIfExists(f.toPath()); } catch (Exception ignored) {}
    }

    private void copyRecursive(File src, File dst) throws java.io.IOException {
        if (src.isDirectory()) {
            if (!dst.exists()) dst.mkdirs();
            File[] children = src.listFiles();
            if (children != null) for (File c : children) copyRecursive(c, new File(dst, c.getName()));
        } else {
            java.nio.file.Files.copy(src.toPath(), dst.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void cleanupStaleClones() {
        File root = new File(".");
        File[] list = root.listFiles();
        if (list == null) return;
        for (File f : list) {
            String n = f.getName();
            if (n.startsWith("tntrun_r")) {
                try {
                    org.bukkit.World w = Bukkit.getWorld(n);
                    if (w != null) Bukkit.unloadWorld(w, false);
                } catch (Throwable ignored) {}
                deleteRecursive(f);
            }
        }
    }
}
