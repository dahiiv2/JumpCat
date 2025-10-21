package com.jumpcat.core.holo;

import com.jumpcat.core.points.PointsService;
import com.jumpcat.core.teams.TeamManager;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class HologramManager {
    public enum Type { PLAYER, TEAM }

    public static class HoloConfig {
        public String id;
        public Type type;
        public String world;
        public double x, y, z;
        public float yaw, pitch;
        public int lines;
        public String title;
        public String rowFormat;
        public String footer;
        public int refreshSeconds;
    }

    public void spawnAll() {
        // Remove previous tagged displays and spawn current configs
        cleanupOrphans();
        for (String id : configs.keySet()) respawn(id);
    }

    public void shutdown() {
        for (String id : new ArrayList<>(live.keySet())) despawn(id);
        cleanupOrphans();
    }

    private final JavaPlugin plugin;
    private final PointsService points;
    private final TeamManager teams;
    private final Map<String, HoloConfig> configs = new LinkedHashMap<>();
    private final Map<String, UUID> live = new HashMap<>(); // id -> entity uuid
    private final NamespacedKey HOLO_KEY;

    public HologramManager(JavaPlugin plugin, PointsService points, TeamManager teams) {
        this.plugin = plugin;
        this.points = points;
        this.teams = teams;
        this.HOLO_KEY = new NamespacedKey(plugin, "holo_id");
    }

    public void load() {
        configs.clear();
        File file = new File(plugin.getDataFolder(), "holograms.yml");
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        if (cfg.isConfigurationSection("holos")) {
            for (String id : cfg.getConfigurationSection("holos").getKeys(false)) {
                HoloConfig hc = new HoloConfig();
                hc.id = id;
                hc.type = Type.valueOf(cfg.getString("holos."+id+".type", "PLAYER").toUpperCase());
                hc.world = cfg.getString("holos."+id+".world", "lobby");
                hc.x = cfg.getDouble("holos."+id+".x");
                hc.y = cfg.getDouble("holos."+id+".y");
                hc.z = cfg.getDouble("holos."+id+".z");
                hc.yaw = (float) cfg.getDouble("holos."+id+".yaw", 0.0);
                hc.pitch = (float) cfg.getDouble("holos."+id+".pitch", 0.0);
                hc.lines = cfg.getInt("holos."+id+".lines", 10);
                hc.title = cfg.getString("holos."+id+".title", hc.type==Type.PLAYER?"Top Players":"Top Teams");
                hc.rowFormat = cfg.getString(
                        "holos."+id+".rowFormat",
                        hc.type==Type.PLAYER?"%prefix#%rank %name - %points":"#%rank %teamcolor%team%reset - %points"
                );
                hc.footer = cfg.getString("holos."+id+".footer", "");
                hc.refreshSeconds = cfg.getInt("holos."+id+".refreshSeconds", 10);
                configs.put(id, hc);
            }
        }
    }

    public void save() {
        File dir = plugin.getDataFolder();
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, "holograms.yml");
        FileConfiguration cfg = new YamlConfiguration();
        for (HoloConfig hc : configs.values()) {
            String p = "holos."+hc.id+".";
            cfg.set(p+"type", hc.type.toString());
            cfg.set(p+"world", hc.world);
            cfg.set(p+"x", hc.x); cfg.set(p+"y", hc.y); cfg.set(p+"z", hc.z);
            cfg.set(p+"yaw", hc.yaw); cfg.set(p+"pitch", hc.pitch);
            cfg.set(p+"lines", hc.lines);
            cfg.set(p+"title", hc.title);
            cfg.set(p+"rowFormat", hc.rowFormat);
            cfg.set(p+"footer", hc.footer);
            cfg.set(p+"refreshSeconds", hc.refreshSeconds);
        }
        try { cfg.save(file); } catch (IOException ignored) { }
    }

    public void set(String id, Type type, Location loc, int lines) {
        HoloConfig hc = new HoloConfig();
        hc.id = id;
        hc.type = type;
        hc.world = loc.getWorld().getName();
        hc.x = loc.getX(); hc.y = loc.getY(); hc.z = loc.getZ();
        hc.yaw = loc.getYaw(); hc.pitch = loc.getPitch();
        hc.lines = lines;
        hc.title = type==Type.PLAYER?"Top Players":"Top Teams";
        hc.rowFormat = type==Type.PLAYER?"%prefix#%rank %name - %points":"#%rank %teamcolor%team%reset - %points";
        hc.footer = "";
        hc.refreshSeconds = 10;
        configs.put(id, hc);
        save();
        respawn(id);
    }

    public void clear(String id) {
        despawn(id);
        configs.remove(id);
        save();
    }

    public void reload() {
        // Despawn all
        for (String id : new ArrayList<>(live.keySet())) despawn(id);
        // Also remove any leftover tagged entities from a previous run
        cleanupOrphans();
        load();
        // Respawn all
        for (String id : configs.keySet()) respawn(id);
    }

    public void startScheduler() {
        // One repeating task to refresh all holos according to their per-holo intervals
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (String id : configs.keySet()) update(id);
        }, 20L, 20L);
    }

    private void respawn(String id) {
        HoloConfig hc = configs.get(id);
        if (hc == null) return;
        World w = Bukkit.getWorld(hc.world);
        if (w == null) return;
        Location loc = new Location(w, hc.x, hc.y, hc.z, hc.yaw, hc.pitch);
        despawn(id);
        TextDisplay td = w.spawn(loc, TextDisplay.class, e -> {
            e.setBillboard(Display.Billboard.CENTER);
            e.setSeeThrough(true);
            e.setShadowed(false);
            e.setAlignment(TextDisplay.TextAlignment.CENTER);
            e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            e.text(Component.text("Loading..."));
        });
        try {
            PersistentDataContainer pdc = td.getPersistentDataContainer();
            pdc.set(HOLO_KEY, PersistentDataType.STRING, id);
        } catch (Throwable ignored) {}
        live.put(id, td.getUniqueId());
        update(id);
    }

    private void despawn(String id) {
        UUID uid = live.remove(id);
        if (uid == null) return;
        for (World w : Bukkit.getWorlds()) {
            var e = w.getEntity(uid);
            if (e != null) { e.remove(); break; }
        }
    }

    private void update(String id) {
        HoloConfig hc = configs.get(id);
        if (hc == null) return;
        UUID uid = live.get(id);
        if (uid == null) { respawn(id); return; }
        TextDisplay td = null;
        for (World w : Bukkit.getWorlds()) { var e = w.getEntity(uid); if (e instanceof TextDisplay) { td = (TextDisplay)e; break; } }
        if (td == null) { respawn(id); return; }

        long now = System.currentTimeMillis();
        // We refresh every tick and rely on per-holo interval by computing whether it's time to update content
        String key = "_next_"+id;
        Long next = nextUpdate.get(id);
        if (next != null && now < next) return;
        nextUpdate.put(id, now + (hc.refreshSeconds * 1000L));

        List<String> out = new ArrayList<>();
        if (hc.title != null && !hc.title.isEmpty()) out.add(ChatColor.GOLD + "" + ChatColor.BOLD + hc.title + ChatColor.RESET);
        if (hc.type == Type.PLAYER) {
            Map<UUID, Integer> top = points.top(hc.lines);
            int rank = 1;
            for (Map.Entry<UUID, Integer> e : top.entrySet()) {
                UUID playerId = e.getKey();
                String name = Optional.ofNullable(Bukkit.getOfflinePlayer(playerId).getName()).orElse(playerId.toString().substring(0, 8));
                String prefix = getPlayerTeamPrefix(name);
                String formatted = hc.rowFormat
                        .replace("%prefix", prefix)
                        .replace("%rank", String.valueOf(rank))
                        .replace("%name", name)
                        .replace("%points", String.valueOf(e.getValue()));
                out.add(ChatColor.RESET + "" + ChatColor.WHITE + formatted);
                rank++;
            }
        } else {
            List<TeamRow> teamsTop = topTeamsData(hc.lines);
            int rank = 1;
            for (TeamRow tr : teamsTop) {
                String formatted = hc.rowFormat
                        .replace("%teamcolor", tr.color)
                        .replace("%rank", String.valueOf(rank))
                        .replace("%team", tr.label)
                        .replace("%points", String.valueOf(tr.points))
                        .replace("%reset", ChatColor.WHITE.toString());
                out.add(ChatColor.WHITE + formatted);
                rank++;
            }
        }
        if (hc.footer != null && !hc.footer.isEmpty()) out.add(hc.footer);
        td.text(Component.text(String.join("\n", out)));
    }

    private final Map<String, Long> nextUpdate = new HashMap<>();

    private void cleanupOrphans() {
        try {
            for (World w : Bukkit.getWorlds()) {
                for (org.bukkit.entity.Entity e : w.getEntitiesByClass(TextDisplay.class)) {
                    try {
                        PersistentDataContainer pdc = e.getPersistentDataContainer();
                        if (pdc.has(HOLO_KEY, PersistentDataType.STRING)) {
                            String hid = pdc.get(HOLO_KEY, PersistentDataType.STRING);
                            // Remove all tagged holos; will be respawned from configs if needed
                            e.remove();
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }

    private String getPlayerTeamPrefix(String playerName) {
        // Find team key by checking membership lists (names)
        for (String key : teams.listTeamKeys()) {
            if (teams.getTeamMembers(key).contains(playerName)) {
                ChatColor c = teams.getTeamColor(key);
                String label = teams.getTeamLabel(key);
                return c + "" + ChatColor.BOLD + label + " " + ChatColor.WHITE;
            }
        }
        return "";
    }

    private static class TeamRow { String color; String label; int points; TeamRow(String c,String l,int p){color=c;label=l;points=p;} }

    private List<TeamRow> topTeamsData(int limit) {
        Map<String, Integer> sums = new LinkedHashMap<>();
        for (String teamKey : teams.listTeamKeys()) {
            int total = teams.getTeamMembers(teamKey).stream()
                    .map(name -> Bukkit.getOfflinePlayer(name))
                    .filter(Objects::nonNull)
                    .map(OfflinePlayer::getUniqueId)
                    .mapToInt(points::getPoints)
                    .sum();
            sums.put(teamKey, total);
        }
        return sums.entrySet().stream()
                .sorted((a,b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .map(e -> new TeamRow(teams.getTeamColor(e.getKey())+""+ChatColor.BOLD, teams.getTeamLabel(e.getKey()), e.getValue()))
                .collect(Collectors.toList());
    }
}
