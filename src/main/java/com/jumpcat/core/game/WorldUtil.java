package com.jumpcat.core.game;

import org.bukkit.World;

public final class WorldUtil {
    private WorldUtil() {}

    public static boolean anyGameRunning() {
        return com.jumpcat.core.game.skywars.SkyWarsController.CURRENT != null
            || com.jumpcat.core.game.uhc.UhcMeetupController.CURRENT != null
            || com.jumpcat.core.game.battlebox.BattleBoxController.CURRENT != null
            || com.jumpcat.core.game.tntrun.TntRunController.CURRENT != null;
    }

    public static boolean isSkyWars(World w) { return nameStartsWith(w, "skywars_r"); }
    public static boolean isUhc(World w) { return nameStartsWith(w, "uhc_meetup_r"); }
    public static boolean isTntRun(World w) { return nameStartsWith(w, "tntrun_r"); }
    public static boolean isBattleBox(World w) { return nameEquals(w, "battle_box"); }

    public static boolean isAnyGameWorld(World w) {
        return isSkyWars(w) || isUhc(w) || isTntRun(w) || isBattleBox(w);
    }

    private static boolean nameStartsWith(World w, String prefix) {
        if (w == null) return false; String n = w.getName(); return n != null && n.startsWith(prefix);
    }
    private static boolean nameEquals(World w, String name) {
        if (w == null) return false; String n = w.getName(); return n != null && n.equalsIgnoreCase(name);
    }
}
