package com.jumpcat.core;

import com.jumpcat.core.listeners.PlayerJoinQuitListener;
import com.jumpcat.core.combat.CombatService;
import com.jumpcat.core.listeners.KillFeedbackListener;
import com.jumpcat.core.scoreboard.SidebarManager;
import com.jumpcat.core.teams.TeamManager;
import com.jumpcat.core.teams.TeamCommand;
import com.jumpcat.core.teams.TeamChatListener;
import com.jumpcat.core.teams.TeamChatToggleCommand;
import com.jumpcat.core.teams.TeamDeathListener;
import com.jumpcat.core.teams.TeamTabCompleter;
import com.jumpcat.core.lobby.LobbyListener;
import com.jumpcat.core.lobby.LobbyManager;
import com.jumpcat.core.game.GameRegistry;
import com.jumpcat.core.game.GameCommand;
import com.jumpcat.core.game.battlebox.BattleBoxManager;
import com.jumpcat.core.game.battlebox.BattleBoxController;
import com.jumpcat.core.game.battlebox.BattleBoxListener;
import com.jumpcat.core.game.battlebox.BattleBoxAdminCommand;
import com.jumpcat.core.points.PointsService;
import com.jumpcat.core.holo.HologramManager;
import com.jumpcat.core.holo.HologramCommand;
import com.jumpcat.core.commands.LeaderboardCommand;
import com.jumpcat.core.listeners.LeaderboardListener;
import com.jumpcat.core.commands.PointsCommand;
import com.jumpcat.core.listeners.MotdListener;
import com.jumpcat.core.slots.SlotsManager;
import com.jumpcat.core.slots.SlotsCommand;
import com.jumpcat.core.listeners.SlotsLoginListener;
import com.jumpcat.core.commands.WorldCommand;
import com.jumpcat.core.game.uhc.UhcMeetupConfig;
import com.jumpcat.core.game.uhc.UhcMeetupManager;
import com.jumpcat.core.game.uhc.UhcMeetupController;
import com.jumpcat.core.game.uhc.UhcMeetupListener;
import com.jumpcat.core.game.tntrun.TntRunConfig;
import com.jumpcat.core.game.tntrun.TntRunManager;
import com.jumpcat.core.game.tntrun.TntRunController;
import com.jumpcat.core.game.tntrun.TntRunListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class JumpCatPlugin extends JavaPlugin {
    private SidebarManager sidebarManager;
    private TeamManager teamManager;
    private LobbyManager lobbyManager;
    private GameRegistry gameRegistry;
    private BattleBoxManager battleBoxManager;
    private PointsService pointsService;
    private HologramManager hologramManager;
    private SlotsManager slotsManager;

    @Override
    public void onEnable() {
        this.sidebarManager = new SidebarManager(this);
        this.teamManager = new TeamManager(this.sidebarManager.getScoreboard());
        this.teamManager.ensureDefaultTeams();
        this.sidebarManager.setTeamManager(this.teamManager);
        this.lobbyManager = new LobbyManager(this);
        this.lobbyManager.ensureLobbyWorld();
        this.battleBoxManager = new BattleBoxManager(this);
        this.battleBoxManager.ensureWorld();
        this.pointsService = new PointsService();
        this.pointsService.load(this);
        this.sidebarManager.setPointsService(this.pointsService);
        this.slotsManager = new SlotsManager(this);
        this.slotsManager.load();
        this.hologramManager = new HologramManager(this, this.pointsService, this.teamManager);
        this.hologramManager.load();
        this.hologramManager.spawnAll();
        this.hologramManager.startScheduler();
        this.gameRegistry = new GameRegistry();
        this.gameRegistry.register("battlebox", new BattleBoxController(this, battleBoxManager, teamManager, pointsService));
        // SkyWars registration
        com.jumpcat.core.game.skywars.SkyWarsController skywars = new com.jumpcat.core.game.skywars.SkyWarsController(this, this.teamManager);
        this.gameRegistry.register("skywars", skywars);
        // UHC Meetup registration
        UhcMeetupConfig uhcCfg = new UhcMeetupConfig(this);
        uhcCfg.load();
        UhcMeetupManager uhcMgr = new UhcMeetupManager(this, uhcCfg);
        this.gameRegistry.register("uhcmeetup", new UhcMeetupController(this, teamManager, pointsService, uhcCfg, uhcMgr));
        // TNT Run registration
        TntRunConfig tntrCfg = new TntRunConfig();
        TntRunManager tntrMgr = new TntRunManager(this, tntrCfg);
        TntRunController tntr = new TntRunController(this, this.teamManager, this.pointsService, tntrCfg, tntrMgr);
        this.gameRegistry.register("tntrun", tntr);
        // UHC listener (block whitelist, powerless bows, etc.)
        getServer().getPluginManager().registerEvents(new UhcMeetupListener(uhcCfg), this);
        // Register listeners
        getServer().getPluginManager().registerEvents(new PlayerJoinQuitListener(this.sidebarManager, this), this);
        getServer().getPluginManager().registerEvents(new TeamChatListener(this.teamManager), this);
        getServer().getPluginManager().registerEvents(new TeamDeathListener(this.teamManager), this);
        getServer().getPluginManager().registerEvents(new LobbyListener(this.lobbyManager), this);
        getServer().getPluginManager().registerEvents(new BattleBoxListener(this.battleBoxManager, (BattleBoxController) this.gameRegistry.get("battlebox"), this.pointsService, this), this);
        // SkyWars listener
        getServer().getPluginManager().registerEvents(new com.jumpcat.core.game.skywars.SkyWarsListener(skywars, this.teamManager), this);
        // TNT Run listener
        getServer().getPluginManager().registerEvents(new TntRunListener(tntr, tntrCfg), this);
        getServer().getPluginManager().registerEvents(new KillFeedbackListener(this), this);
        // Centralized combat tracker for kills/deaths → sidebar K/D (active only when a game runs)
        getServer().getPluginManager().registerEvents(new CombatService(this.sidebarManager), this);
        // Dynamic MOTD with active game status (from sidebar) and dynamic slots cap
        getServer().getPluginManager().registerEvents(new MotdListener(this, (BattleBoxController) this.gameRegistry.get("battlebox"), this.slotsManager, this.sidebarManager), this);
        // Enforce slots cap at login
        getServer().getPluginManager().registerEvents(new SlotsLoginListener(this.slotsManager), this);

        // Periodic sidebar refresh
        getServer().getScheduler().runTaskTimer(this, () -> sidebarManager.updateAll(), 20L, 20L);

        // Show sidebar for players already online (e.g., plugin reload/dev environment)
        for (org.bukkit.entity.Player p : getServer().getOnlinePlayers()) {
            try { sidebarManager.show(p); } catch (Throwable ignored) {}
        }

		// Ensure a neutral team exists for unassigned players (no global collision override)
		try {
			org.bukkit.scoreboard.Scoreboard sb = getServer().getScoreboardManager().getMainScoreboard();
			org.bukkit.scoreboard.Team neutral = sb.getTeam("jc_neutral");
			if (neutral == null) neutral = sb.registerNewTeam("jc_neutral");
			try { neutral.setAllowFriendlyFire(false); } catch (Throwable ignored) {}
			try { neutral.setPrefix(""); } catch (Throwable ignored) {}
			for (org.bukkit.entity.Player p : getServer().getOnlinePlayers()) {
				boolean inAny = false;
				for (org.bukkit.scoreboard.Team t : sb.getTeams()) { if (t.hasEntry(p.getName())) { inAny = true; break; } }
				if (!inAny) neutral.addEntry(p.getName());
			}
		} catch (Throwable ignored) {}

        PluginCommand cmd = getCommand("jumpcat");
        if (cmd != null) {
            cmd.setExecutor(new com.jumpcat.core.commands.JumpCatCommand(this));
        }

        PluginCommand bbCmd = getCommand("bb");
        if (bbCmd != null) {
            BattleBoxAdminCommand bb = new BattleBoxAdminCommand(this.battleBoxManager);
            bbCmd.setExecutor(bb);
            bbCmd.setTabCompleter(bb);
        }

        PluginCommand gameCmd = getCommand("game");
        if (gameCmd != null) {
            gameCmd.setExecutor(new GameCommand(this, this.gameRegistry));
        }

        PluginCommand teamCmd = getCommand("team");
        if (teamCmd != null) {
            teamCmd.setExecutor(new TeamCommand(this.teamManager, this.pointsService));
            teamCmd.setTabCompleter(new TeamTabCompleter(this.teamManager));
        }

        PluginCommand tcCmd = getCommand("teamchat");
        if (tcCmd != null) {
            tcCmd.setExecutor(new TeamChatToggleCommand(this.teamManager));
            tcCmd.setTabCompleter(new TeamTabCompleter(this.teamManager));
            // alias /tc is defined in plugin.yml
        }
        PluginCommand lbCmd = getCommand("leaderboard");
        if (lbCmd != null) {
            com.jumpcat.core.commands.LeaderboardCommand lc = new com.jumpcat.core.commands.LeaderboardCommand(this);
            lbCmd.setExecutor(lc);
            getServer().getPluginManager().registerEvents(new com.jumpcat.core.listeners.LeaderboardListener(lc), this);
        }
        PluginCommand holoCmd = getCommand("holo");
        if (holoCmd != null) {
            holoCmd.setExecutor(new HologramCommand(this.hologramManager));
        }
        PluginCommand ptsCmd = getCommand("points");
        if (ptsCmd != null) {
            ptsCmd.setExecutor(new com.jumpcat.core.commands.PointsCommand(this));
        }
        PluginCommand slotsCmd = getCommand("slots");
        if (slotsCmd != null) {
            slotsCmd.setExecutor(new SlotsCommand(this.slotsManager));
        }
        PluginCommand worldCmd = getCommand("world");
        if (worldCmd != null) {
            WorldCommand wc = new WorldCommand();
            worldCmd.setExecutor(wc);
            worldCmd.setTabCompleter(wc);
        }

        PluginCommand eventCmd = getCommand("event");
        if (eventCmd != null) {
            eventCmd.setExecutor(new com.jumpcat.core.commands.EventCommand(this, this.teamManager, this.pointsService, this.sidebarManager, this.lobbyManager));
        }

        // Private messaging commands: /msg and /r
        org.bukkit.command.PluginCommand msgCmd = getCommand("msg");
        if (msgCmd != null) {
            com.jumpcat.core.commands.MsgCommand mc = new com.jumpcat.core.commands.MsgCommand();
            msgCmd.setExecutor(mc);
            msgCmd.setTabCompleter(mc);
        }
        org.bukkit.command.PluginCommand rCmd = getCommand("r");
        if (rCmd != null) {
            com.jumpcat.core.commands.MsgCommand mc = new com.jumpcat.core.commands.MsgCommand();
            rCmd.setExecutor(mc);
        }
    }

    @Override
    public void onDisable() {
        if (this.pointsService != null) {
            this.pointsService.save(this);
        }
        if (this.hologramManager != null) {
            this.hologramManager.shutdown();
        }
    }

    public LobbyManager getLobbyManager() {
        return lobbyManager;
    }

    public PointsService getPointsService() {
        return pointsService;
    }

    public SidebarManager getSidebarManager() {
        return sidebarManager;
    }
}
