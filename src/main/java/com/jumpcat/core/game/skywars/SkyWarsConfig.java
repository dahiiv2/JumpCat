package com.jumpcat.core.game.skywars;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal SkyWars config with 12 stub spawnpoints and round settings.
 * Replace spawnpoints later with your real coordinates.
 */
public class SkyWarsConfig {
    public static final String DEFAULT_TEMPLATE_WORLD = "skywars_template";

    private String templateWorld = DEFAULT_TEMPLATE_WORLD;
    private int rounds = 3;
    private int graceSeconds = 10;

    // Timers (in seconds)
    private int roundHardCapSeconds = 4 * 60 + 30;     // 4:30
    private int fullShrinkSeconds = 200;  // hard - grace(10) - 60

    // Border / ceiling targets
    private int finalBorderDiameter = 5; // 5x5
    private int ceilingStartY = 80;      // top cap starts at 80
    private int ceilingEndY = 60;        // and floor rises to 60

    /**
     * 12 spawnpoints at radius 125 around (0,65,0): (±125,0), (±108,±63), (±63,±108), (0,±125).
     * Each faces the center.
     */
    public List<Spawn> getSpawnPoints() {
        List<Spawn> list = new ArrayList<>();
        double y = 65.0;
        double[][] pts = new double[][]{
                {125, 0}, {108, 63}, {63, 108}, {0, 125},
                {-63, 108}, {-108, 63}, {-125, 0}, {-108, -63},
                {-63, -108}, {0, -125}, {63, -108}, {108, -63}
        };
        for (double[] p : pts) {
            double x = p[0];
            double z = p[1];
            float yaw = (float) (Math.toDegrees(Math.atan2(-z, -x))); // face center
            list.add(new Spawn(x + 0.5, y, z + 0.5, yaw, 0f));
        }
        return list;
    }

    public String getTemplateWorld() { return templateWorld; }
    public int getRounds() { return rounds; }
    public int getGraceSeconds() { return graceSeconds; }
    public int getRoundHardCapSeconds() { return roundHardCapSeconds; }
    public int getFullShrinkSeconds() { return fullShrinkSeconds; }
    public int getFinalBorderDiameter() { return finalBorderDiameter; }
    public int getCeilingStartY() { return ceilingStartY; }
    public int getCeilingEndY() { return ceilingEndY; }

    public static class Spawn {
        public final double x, y, z;
        public final float yaw, pitch;
        public Spawn(double x, double y, double z, float yaw, float pitch) {
            this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch;
        }
    }
}
