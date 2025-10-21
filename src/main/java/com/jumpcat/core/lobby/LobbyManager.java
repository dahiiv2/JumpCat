package com.jumpcat.core.lobby;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

public class LobbyManager {
    private final JavaPlugin plugin;
    private String worldName = "lobby";

    public LobbyManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void ensureLobbyWorld() {
        FileConfiguration cfg = plugin.getConfig();
        this.worldName = cfg.getString("lobby.world", "lobby");

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            WorldCreator wc = new WorldCreator(worldName);
            wc.generator(new VoidChunkGenerator());
            wc.environment(World.Environment.NORMAL);
            wc.type(WorldType.NORMAL);
            world = Bukkit.createWorld(wc);
            if (world != null) {
                buildSpawnPlatform(world, new Location(world, 0, 64, 0));
                world.setSpawnLocation(new Location(world, 0, 65, 0));
            }
        }
        if (world != null) {
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setTime(6000);
        }
    }

    private void buildSpawnPlatform(World world, Location center) {
        int y = center.getBlockY();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                Block b = world.getBlockAt(center.getBlockX()+x, y, center.getBlockZ()+z);
                b.setType(Material.SMOOTH_STONE, false);
            }
        }
    }

    public World getLobbyWorld() {
        return Bukkit.getWorld(worldName);
    }

    public Location getLobbySpawn() {
        World w = getLobbyWorld();
        if (w == null) return null;
        return new Location(w, 0.5, 65, 0.5);
    }

    public void setLobbySpawn(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        plugin.getConfig().set("lobby.world", loc.getWorld().getName());
        plugin.saveConfig();
        loc.getWorld().setSpawnLocation(new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
    }
}
