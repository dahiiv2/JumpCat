package com.jumpcat.core.game.battlebox;

import com.jumpcat.core.lobby.VoidChunkGenerator;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class BattleBoxManager {
    private final JavaPlugin plugin;
    private final String worldName = "battle_box";
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    public BattleBoxManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void ensureWorld() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            WorldCreator wc = new WorldCreator(worldName);
            wc.generator(new VoidChunkGenerator());
            wc.environment(World.Environment.NORMAL);
            wc.type(WorldType.NORMAL);
            world = Bukkit.createWorld(wc);
        }
        if (world != null) {
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setTime(6000);
        }
        loadArenas();
    }

    public World getWorld() {
        return Bukkit.getWorld(worldName);
    }

    public String getWorldName() {
        return worldName;
    }

    // Arena API
    public static class Arena {
        public final String id;
        public Location pos1;
        public Location pos2;
        public Location spawnA;
        public Location spawnB;
        public Location spectatorSpawn;

        public Arena(String id) { this.id = id; }
        public boolean isConfigured() {
            return pos1 != null && pos2 != null && spawnA != null && spawnB != null && spectatorSpawn != null;
        }
    }

    public Arena createArena(String id) {
        String key = id.toLowerCase(Locale.ROOT);
        Arena a = arenas.get(key);
        if (a == null) { a = new Arena(key); arenas.put(key, a); }
        saveArenas();
        return a;
    }

    public Arena getArena(String id) { return id == null ? null : arenas.get(id.toLowerCase(Locale.ROOT)); }

    public Collection<Arena> listArenas() { return Collections.unmodifiableCollection(arenas.values()); }

    public void setPos1(String id, Location loc) { ensure(id).pos1 = cloneInWorld(loc); saveArenas(); }
    public void setPos2(String id, Location loc) { ensure(id).pos2 = cloneInWorld(loc); saveArenas(); }
    public void setSpawn(String id, char which, Location loc) {
        Arena a = ensure(id);
        Location c = cloneInWorld(loc);
        if (which == 'A') a.spawnA = c; else if (which == 'B') a.spawnB = c; else a.spectatorSpawn = c;
        saveArenas();
    }

    private Arena ensure(String id) { return createArena(id); }

    private Location cloneInWorld(Location src) {
        if (src == null) return null;
        World w = getWorld();
        if (w == null) return null;
        return new Location(w, src.getX(), src.getY(), src.getZ(), src.getYaw(), src.getPitch());
    }

    // Persistence
    private void loadArenas() {
        arenas.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("battlebox.arenas");
        if (root == null) return;
        World w = getWorld();
        for (String id : root.getKeys(false)) {
            Arena a = new Arena(id);
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            a.pos1 = readLoc(w, s.getConfigurationSection("pos1"));
            a.pos2 = readLoc(w, s.getConfigurationSection("pos2"));
            a.spawnA = readLoc(w, s.getConfigurationSection("spawnA"));
            a.spawnB = readLoc(w, s.getConfigurationSection("spawnB"));
            a.spectatorSpawn = readLoc(w, s.getConfigurationSection("spec"));
            arenas.put(id, a);
        }
    }

    private void saveArenas() {
        ConfigurationSection root = plugin.getConfig().createSection("battlebox.arenas");
        for (Arena a : arenas.values()) {
            ConfigurationSection s = root.createSection(a.id);
            writeLoc(s.createSection("pos1"), a.pos1);
            writeLoc(s.createSection("pos2"), a.pos2);
            writeLoc(s.createSection("spawnA"), a.spawnA);
            writeLoc(s.createSection("spawnB"), a.spawnB);
            writeLoc(s.createSection("spec"), a.spectatorSpawn);
        }
        plugin.saveConfig();
    }

    private Location readLoc(World w, ConfigurationSection s) {
        if (w == null || s == null) return null;
        if (!s.contains("x")) return null;
        double x = s.getDouble("x"), y = s.getDouble("y"), z = s.getDouble("z");
        float yaw = (float) s.getDouble("yaw", 0.0);
        float pitch = (float) s.getDouble("pitch", 0.0);
        return new Location(w, x, y, z, yaw, pitch);
    }

    private void writeLoc(ConfigurationSection s, Location l) {
        if (s == null || l == null) return;
        s.set("x", l.getX());
        s.set("y", l.getY());
        s.set("z", l.getZ());
        s.set("yaw", l.getYaw());
        s.set("pitch", l.getPitch());
    }
}
