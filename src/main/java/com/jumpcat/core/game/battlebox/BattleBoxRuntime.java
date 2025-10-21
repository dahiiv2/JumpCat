package com.jumpcat.core.game.battlebox;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.*;

public class BattleBoxRuntime {
    public final World world;
    public final BattleBoxManager.Arena arena;
    private final Map<UUID, Character> side = new HashMap<>(); // 'A' or 'B'
    private final Set<UUID> alive = new HashSet<>();
    public boolean live = false; // false during countdown/prep

    public BattleBoxRuntime(World world, BattleBoxManager.Arena arena) {
        this.world = world;
        this.arena = arena;
    }

    public void addPlayer(UUID id, char s) {
        side.put(id, s);
        alive.add(id);
    }

    public boolean isParticipant(UUID id) { return side.containsKey(id); }
    public boolean isAlive(UUID id) { return alive.contains(id); }
    public Character sideOf(UUID id) { return side.get(id); }

    public void markDead(UUID id) { alive.remove(id); }

    public boolean inArena(Location loc) {
        if (loc == null || arena == null || arena.pos1 == null || arena.pos2 == null) return false;
        if (!loc.getWorld().equals(world)) return false;
        double minX = Math.min(arena.pos1.getX(), arena.pos2.getX());
        double maxX = Math.max(arena.pos1.getX(), arena.pos2.getX());
        double minZ = Math.min(arena.pos1.getZ(), arena.pos2.getZ());
        double maxZ = Math.max(arena.pos1.getZ(), arena.pos2.getZ());
        double x = loc.getX();
        double z = loc.getZ();
        // Ignore strict Y bounds to avoid false negatives when players are above the floor level
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public boolean teamEliminated(char team) {
        for (Map.Entry<UUID, Character> e : side.entrySet()) {
            if (e.getValue() == team && alive.contains(e.getKey())) return false;
        }
        return true;
    }

    public Set<UUID> teamMembers(char team) {
        Set<UUID> s = new HashSet<>();
        for (Map.Entry<UUID, Character> e : side.entrySet()) if (e.getValue() == team) s.add(e.getKey());
        return s;
    }
}
