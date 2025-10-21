package com.jumpcat.core.points;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.io.File;
import java.io.IOException;

public class PointsService {
    private final Map<UUID, Integer> points = new ConcurrentHashMap<>();

    public void addPoints(UUID playerId, int amount) {
        if (amount == 0) return;
        points.merge(playerId, amount, Integer::sum);
    }

    public void addPoints(Collection<UUID> playerIds, int amountEach) {
        for (UUID id : playerIds) addPoints(id, amountEach);
    }

    public int getPoints(UUID playerId) {
        return points.getOrDefault(playerId, 0);
    }

    public Map<UUID, Integer> top(int limit) {
        return points.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a,b)->a, LinkedHashMap::new));
    }

    public List<String> topDisplay(int limit) {
        Map<UUID, Integer> m = top(limit);
        List<String> rows = new ArrayList<>();
        int i = 1;
        for (Map.Entry<UUID, Integer> e : m.entrySet()) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(e.getKey());
            rows.add("#"+i+" " + (op != null ? op.getName() : e.getKey()) + ": " + e.getValue());
            i++;
        }
        return rows;
    }

    // Persistence: points.yml in plugin data folder
    public void load(JavaPlugin plugin) {
        File dir = plugin.getDataFolder();
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, "points.yml");
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        points.clear();
        if (cfg.isConfigurationSection("points")) {
            for (String key : cfg.getConfigurationSection("points").getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    int val = cfg.getInt("points."+key, 0);
                    points.put(id, val);
                } catch (IllegalArgumentException ignored) { }
            }
        }
    }

    public void save(JavaPlugin plugin) {
        File dir = plugin.getDataFolder();
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, "points.yml");
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID,Integer> e : points.entrySet()) {
            cfg.set("points."+e.getKey(), e.getValue());
        }
        try { cfg.save(file); } catch (IOException ignored) { }
    }

    public void clearAll() {
        points.clear();
    }

    public int getRank(UUID playerId) {
        // 1-based rank by descending points; if player has no points and not present, return 0
        if (points.isEmpty()) return 0;
        List<Map.Entry<UUID,Integer>> sorted = points.entrySet().stream()
                .sorted((a,b) -> Integer.compare(b.getValue(), a.getValue()))
                .collect(Collectors.toList());
        int idx = 1;
        for (Map.Entry<UUID,Integer> e : sorted) {
            if (e.getKey().equals(playerId)) return idx;
            idx++;
        }
        // If not found in map (no points), rank not defined
        return 0;
    }
}
