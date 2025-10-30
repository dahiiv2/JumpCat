package com.jumpcat.core.game.skywars;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public class SkyWarsMapsConfig {
    public static class Spawn {
        public final double x,y,z; public final float yaw,pitch;
        public Spawn(double x,double y,double z,float yaw,float pitch){ this.x=x; this.y=y; this.z=z; this.yaw=yaw; this.pitch=pitch; }
    }
    public static class Template {
        public final String name; public final List<Spawn> spawns;
        public Template(String name, List<Spawn> spawns){ this.name=name; this.spawns=spawns; }
    }

    private final JavaPlugin plugin;
    private final List<Template> templates = new ArrayList<>();
    private int lastIndex = -1;
    private final java.util.List<Integer> rotationBag = new java.util.ArrayList<>();

    public SkyWarsMapsConfig(JavaPlugin plugin){ this.plugin = plugin; load(); }

    public void load() {
        templates.clear();
        File dir = plugin.getDataFolder(); if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, "skywars.yml");
        if (!file.exists()) {
            // Build default config with two templates
            YamlConfiguration out = new YamlConfiguration();
            java.util.List<java.util.Map<String,Object>> defs = new java.util.ArrayList<>();
            // Template 1: explicit 12 spawns at radius ~125 (preserve original corners)
            {
                java.util.Map<String,Object> t1 = new java.util.LinkedHashMap<>();
                t1.put("name", "skywars_template");
                java.util.List<java.util.Map<String,Object>> sp = new java.util.ArrayList<>();
                double y = 65.0;
                double[][] pts = new double[][]{{125,0},{108,63},{63,108},{0,125},{-63,108},{-108,63},{-125,0},{-108,-63},{-63,-108},{0,-125},{63,-108},{108,-63}};
                for (double[] p : pts) {
                    double x = p[0], z = p[1];
                    float yaw = (float) Math.toDegrees(Math.atan2(-z, -x));
                    java.util.Map<String,Object> m = new java.util.LinkedHashMap<>();
                    m.put("x", x + 0.5); m.put("y", y); m.put("z", z + 0.5); m.put("yaw", (double)yaw); m.put("pitch", 0.0);
                    sp.add(m);
                }
                t1.put("spawns", sp);
                defs.add(t1);
            }
            // Template 2: generated ring at radius 150, y 65
            {
                java.util.Map<String,Object> t2 = new java.util.LinkedHashMap<>();
                t2.put("name", "skywars_template_2");
                t2.put("spawnRadius", 150);
                t2.put("y", 65);
                defs.add(t2);
            }
            out.set("templates", defs);
            try { out.save(file); } catch (Exception ignored) {}
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        List<?> list = cfg.getList("templates");
        if (list != null) {
            for (Object o : list) {
                if (!(o instanceof Map)) continue;
                Map<?,?> m = (Map<?,?>) o;
                String name = String.valueOf(m.get("name"));
                List<Spawn> sp = new ArrayList<>();
                Object spawns = m.get("spawns");
                if (spawns instanceof List) {
                    for (Object so : (List<?>) spawns) {
                        if (!(so instanceof Map)) continue;
                        Map<?,?> sm = (Map<?,?>) so;
                        double x = toD(sm.get("x")); double y = toD(sm.get("y")); double z = toD(sm.get("z"));
                        float yaw = (float) getD(sm, "yaw", 0.0); float pitch = (float) getD(sm, "pitch", 0.0);
                        sp.add(new Spawn(x,y,z,yaw,pitch));
                    }
                } else {
                    // Generate 12 evenly spaced spawns if spawnRadius present
                    double radius = getD(m, "spawnRadius", 125.0);
                    double y = getD(m, "y", 65.0);
                    double angleOffset = Math.toRadians(getD(m, "angleOffsetDeg", 0.0));
                    for (int i = 0; i < 12; i++) {
                        double ang = angleOffset + i * Math.PI / 6.0; // 30 deg
                        double x = Math.cos(ang) * radius + 0.5;
                        double z = Math.sin(ang) * radius + 0.5;
                        float yaw = (float) Math.toDegrees(Math.atan2(-Math.sin(ang), -Math.cos(ang)));
                        sp.add(new Spawn(x, y, z, yaw, 0f));
                    }
                }
                templates.add(new Template(name, sp));
            }
        }
        // Default if none provided: keep current hardcoded ring for skywars_template
        if (templates.isEmpty()) {
            List<Spawn> def = new ArrayList<>();
            double y = 65.0; double[][] pts = new double[][]{{125,0},{108,63},{63,108},{0,125},{-63,108},{-108,63},{-125,0},{-108,-63},{-63,-108},{0,-125},{63,-108},{108,-63}};
            for (double[] p : pts) {
                double x = p[0], z = p[1]; float yaw = (float) Math.toDegrees(Math.atan2(-z, -x));
                def.add(new Spawn(x+0.5, y, z+0.5, yaw, 0f));
            }
            templates.add(new Template("skywars_template", def));
        }
    }

    private static double toD(Object o){ if (o == null) return 0; if (o instanceof Number) return ((Number)o).doubleValue(); try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return 0; } }
    private static double getD(Map<?,?> m, String key, double def){ Object o = (m != null) ? m.get(key) : null; return (o == null) ? def : toD(o); }

    public Template pickNextTemplate(Random rnd) {
        if (templates.isEmpty()) return null;
        if (templates.size() == 1) return templates.get(0);
        if (rnd == null) rnd = new Random();
        // Refill bag when empty
        if (rotationBag.isEmpty()) {
            rotationBag.clear();
            for (int i = 0; i < templates.size(); i++) rotationBag.add(i);
            java.util.Collections.shuffle(rotationBag, rnd);
            // Avoid immediate repeat across cycles
            if (lastIndex >= 0 && templates.size() > 1 && rotationBag.get(0) == lastIndex) {
                // move first to end
                int first = rotationBag.remove(0);
                rotationBag.add(first);
            }
        }
        int idx = rotationBag.remove(0);
        lastIndex = idx;
        return templates.get(idx);
    }

    public List<com.jumpcat.core.game.skywars.SkyWarsConfig.Spawn> getSpawnsFor(String name) {
        for (Template t : templates) if (t.name.equals(name)) {
            return toControllerSpawns(t.spawns);
        }
        return toControllerSpawns(templates.get(0).spawns);
    }

    private static List<SkyWarsConfig.Spawn> toControllerSpawns(List<Spawn> list){
        List<SkyWarsConfig.Spawn> out = new ArrayList<>();
        for (Spawn s : list) out.add(new SkyWarsConfig.Spawn(s.x, s.y, s.z, s.yaw, s.pitch));
        return out;
    }

    public List<String> getTemplateNames(){ List<String> names = new ArrayList<>(); for (Template t : templates) names.add(t.name); return names; }
}
