package com.jumpcat.core.teams;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.*;

public class TeamManager {
    private final Scoreboard scoreboard;
    private final Map<String, Team> teamsByKey = new LinkedHashMap<>();
    private final List<String> defaultOrder = new ArrayList<>();
    private final Set<UUID> teamChatToggled = new HashSet<>();
    private final Map<String, ChatColor> colorByKey = new HashMap<>();
    private final Map<String, String> labelByKey = new HashMap<>();

    public TeamManager(Scoreboard scoreboard) {
        this.scoreboard = scoreboard;
    }

    public void ensureDefaultTeams() {
        // 12 default teams with color + bold prefix label
        addTeam("red", ChatColor.RED, "RED");
        addTeam("blue", ChatColor.BLUE, "BLUE");
        addTeam("green", ChatColor.GREEN, "GREEN");
        addTeam("yellow", ChatColor.YELLOW, "YELLOW");
        addTeam("purple", ChatColor.DARK_PURPLE, "PURPLE");
        addTeam("orange", ChatColor.GOLD, "ORANGE");
        addTeam("aqua", ChatColor.AQUA, "AQUA");
        addTeam("pink", ChatColor.LIGHT_PURPLE, "PINK");
        addTeam("silver", ChatColor.GRAY, "SILVER");
        addTeam("gray", ChatColor.DARK_GRAY, "GRAY");
        addTeam("black", ChatColor.BLACK, "BLACK");
        addTeam("white", ChatColor.WHITE, "WHITE");
    }

    private void addTeam(String key, ChatColor color, String label) {
        String id = "jc_" + key;
        Team team = scoreboard.getTeam(id);
        if (team == null) {
            team = scoreboard.registerNewTeam(id);
        }
        team.setAllowFriendlyFire(false);
        // Force name color to white so only prefix is colored
        applyColor(team, ChatColor.WHITE);
        try { team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS); } catch (Throwable ignored) {}
        // Prefix: (color, bold) LABEL + space + white name
        String prefix = "" + color + ChatColor.BOLD + label + " " + ChatColor.WHITE;
        team.setPrefix(prefix);
        teamsByKey.put(key.toLowerCase(Locale.ROOT), team);
        defaultOrder.add(key.toLowerCase(Locale.ROOT));
        colorByKey.put(key.toLowerCase(Locale.ROOT), color);
        labelByKey.put(key.toLowerCase(Locale.ROOT), label);
    }

    public Collection<String> listTeamKeys() {
        return Collections.unmodifiableCollection(teamsByKey.keySet());
    }

    public boolean joinTeam(Player p, String key) {
        Team team = teamsByKey.get(key.toLowerCase(Locale.ROOT));
        if (team == null) return false;
        leaveTeam(p);
        team.addEntry(p.getName());
        return true;
    }

    public void leaveTeam(Player p) {
        for (Team t : teamsByKey.values()) {
            if (t.hasEntry(p.getName())) {
                t.removeEntry(p.getName());
            }
        }
    }

    public Optional<Team> getPlayerTeam(Player p) {
        for (Team t : teamsByKey.values()) {
            if (t.hasEntry(p.getName())) return Optional.of(t);
        }
        return Optional.empty();
    }

    public Optional<String> getPlayerTeamKey(Player p) {
        for (Map.Entry<String, Team> e : teamsByKey.entrySet()) {
            if (e.getValue().hasEntry(p.getName())) return Optional.of(e.getKey());
        }
        return Optional.empty();
    }

    public String getPrefix(Player p) {
        return getPlayerTeam(p).map(Team::getPrefix).orElse("");
    }

    // Admin operations
    public void clearAll() {
        for (Team t : teamsByKey.values()) {
            for (String entry : new ArrayList<>(t.getEntries())) {
                t.removeEntry(entry);
            }
        }
    }

    public boolean addPlayerNameToTeam(String playerName, String key) {
        Team team = teamsByKey.get(key.toLowerCase(Locale.ROOT));
        if (team == null) return false;
        // remove from other teams first
        for (Team t : teamsByKey.values()) t.removeEntry(playerName);
        team.addEntry(playerName);
        return true;
    }

    public boolean removePlayerNameFromTeam(String playerName, String key) {
        Team team = teamsByKey.get(key.toLowerCase(Locale.ROOT));
        if (team == null) return false;
        if (team.hasEntry(playerName)) {
            team.removeEntry(playerName);
            return true;
        }
        return false;
    }

    public boolean clearTeam(String key) {
        Team team = teamsByKey.get(key.toLowerCase(Locale.ROOT));
        if (team == null) return false;
        for (String entry : new ArrayList<>(team.getEntries())) team.removeEntry(entry);
        return true;
    }

    public List<String> getTeamMembers(String key) {
        Team team = teamsByKey.get(key.toLowerCase(Locale.ROOT));
        if (team == null) return Collections.emptyList();
        return new ArrayList<>(team.getEntries());
    }

    public void assignPlayersToTeam(String key, Collection<Player> players) {
        for (Player p : players) addPlayerNameToTeam(p.getName(), key);
    }

    // Clone all JumpCat teams into the provided scoreboard (id, prefix, entries)
    public void applyToScoreboard(Scoreboard target) {
        for (Map.Entry<String, Team> e : teamsByKey.entrySet()) {
            String key = e.getKey();
            Team source = e.getValue();
            String id = source.getName(); // e.g., jc_red
            Team t = target.getTeam(id);
            if (t == null) t = target.registerNewTeam(id);
            t.setAllowFriendlyFire(false);
            t.setPrefix(source.getPrefix());
            // Force white name color on cloned boards too
            applyColor(t, ChatColor.WHITE);
            try { t.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS); } catch (Throwable ignored) {}
            // Clear then re-add entries
            for (String entry : new ArrayList<>(t.getEntries())) t.removeEntry(entry);
            for (String entry : source.getEntries()) t.addEntry(entry);
        }
    }

    private void applyColor(Team t, ChatColor bukkitColor) {
        try {
            NamedTextColor named = toNamed(bukkitColor);
            if (named != null) {
                // Paper 1.21 API
                t.color(named);
                return;
            }
        } catch (Throwable ignored) {}
        try {
            // Fallback for implementations exposing setColor(ChatColor)
            t.setColor(bukkitColor);
        } catch (Throwable ignored) { }
    }

    private NamedTextColor toNamed(ChatColor c) {
        if (c == null) return null;
        switch (c) {
            case BLACK: return NamedTextColor.BLACK;
            case DARK_BLUE: return NamedTextColor.DARK_BLUE;
            case DARK_GREEN: return NamedTextColor.DARK_GREEN;
            case DARK_AQUA: return NamedTextColor.DARK_AQUA;
            case DARK_RED: return NamedTextColor.DARK_RED;
            case DARK_PURPLE: return NamedTextColor.DARK_PURPLE;
            case GOLD: return NamedTextColor.GOLD;
            case GRAY: return NamedTextColor.GRAY;
            case DARK_GRAY: return NamedTextColor.DARK_GRAY;
            case BLUE: return NamedTextColor.BLUE;
            case GREEN: return NamedTextColor.GREEN;
            case AQUA: return NamedTextColor.AQUA;
            case RED: return NamedTextColor.RED;
            case LIGHT_PURPLE: return NamedTextColor.LIGHT_PURPLE;
            case YELLOW: return NamedTextColor.YELLOW;
            case WHITE: return NamedTextColor.WHITE;
            default: return null;
        }
    }

    public Map<String, List<String>> randomizeIntoTeams(int teamCount, Collection<Player> players) {
        List<Player> pool = new ArrayList<>(players);
        Collections.shuffle(pool);
        List<String> chosenKeys = new ArrayList<>();
        for (int i = 0; i < teamCount && i < defaultOrder.size(); i++) {
            chosenKeys.add(defaultOrder.get(i));
            clearTeam(defaultOrder.get(i));
        }
        int i = 0;
        for (Player p : pool) {
            String key = chosenKeys.get(i % chosenKeys.size());
            addPlayerNameToTeam(p.getName(), key);
            i++;
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String k : chosenKeys) out.put(k, getTeamMembers(k));
        return out;
    }

    // Team chat toggle
    public boolean toggleTeamChat(Player p) {
        UUID id = p.getUniqueId();
        if (teamChatToggled.contains(id)) {
            teamChatToggled.remove(id);
            return false;
        } else {
            teamChatToggled.add(id);
            return true;
        }
    }

    public boolean isTeamChat(Player p) {
        return teamChatToggled.contains(p.getUniqueId());
    }

    public ChatColor getTeamColor(String key) {
        return colorByKey.getOrDefault(key.toLowerCase(Locale.ROOT), ChatColor.WHITE);
    }

    public String getTeamLabel(String key) {
        return labelByKey.getOrDefault(key.toLowerCase(Locale.ROOT), key.toUpperCase(Locale.ROOT));
    }
}
