# JumpCat

A custom Minecraft mini-game server plugin built on the [Paper](https://papermc.io/) API. JumpCat is a self-contained server core that ships four fully playable mini-games, a persistent points economy, team management, per-player HUD scoreboards, holographic leaderboards, and a moderation suite — all wired together without any external plugin dependencies.

---

## Table of Contents

- [Features at a Glance](#features-at-a-glance)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Games](#games)
  - [Battle Box](#battle-box)
  - [SkyWars](#skywars)
  - [UHC Meetup](#uhc-meetup)
  - [TNT Run](#tnt-run)
- [Core Systems](#core-systems)
  - [Team System](#team-system)
  - [Points & Leaderboard](#points--leaderboard)
  - [Sidebar / HUD](#sidebar--hud)
  - [Hologram Leaderboards](#hologram-leaderboards)
  - [Lobby](#lobby)
  - [Soft Border](#soft-border)
  - [Moderation](#moderation)
  - [Private Messaging](#private-messaging)
  - [World Management](#world-management)
  - [Dynamic MOTD & Slots Cap](#dynamic-motd--slots-cap)
- [Commands Reference](#commands-reference)
- [Building & Installation](#building--installation)

---

## Features at a Glance

| Category | Highlights |
|---|---|
| **Mini-games** | Battle Box, SkyWars, UHC Meetup, TNT Run |
| **Tournament** | Automated round-robin scheduling across multiple Battle Box arenas |
| **Teams** | 12 colour-coded teams, friendly-fire off, team-chat toggle |
| **Economy** | Persistent per-player points, paginated leaderboard |
| **HUD** | Per-player sidebar showing kills, deaths, rank, team & live game status |
| **Holograms** | Floating text-display leaderboard signs (player or team rankings) |
| **World Cloning** | Worlds are copied from template on game start and deleted on game end |
| **Soft Border** | Particle-wall border with configurable DPS that shrinks over time |
| **Moderation** | In-memory ban/mute/kick with optional duration (e.g. `1h`, `7d`) |
| **Misc** | Dynamic MOTD, configurable slots cap, private messaging, advancement suppression |

---

## Tech Stack

| Tool | Version | Role |
|---|---|---|
| Java | 21 | Language |
| [Paper API](https://papermc.io/) | 1.21 | Server plugin API |
| [Kyori Adventure / MiniMessage](https://docs.advntr.net/) | 4.17.0 | Rich-text formatting |
| Gradle (Kotlin DSL) | — | Build system |

No runtime database or external plugins are required. Points are persisted to a YAML file inside the plugin data folder.

---

## Project Structure

```
src/main/java/com/jumpcat/core/
├── JumpCatPlugin.java            # Plugin entry point — wires all systems together
├── border/
│   └── SoftBorder.java           # Particle damage border (shrinks over time)
├── combat/
│   └── CombatService.java        # Kill/death tracking → sidebar K/D
├── commands/
│   ├── EventCommand.java         # End-of-event winner announcement + reset
│   ├── JumpCatCommand.java       # /jc admin utility
│   ├── LeaderboardCommand.java   # /leaderboard [page]
│   ├── ModerationCommand.java    # /ban /mute /kick /unban /unmute
│   ├── ModerationListener.java   # Enforces bans/mutes on join/chat
│   ├── MsgCommand.java           # /msg /r private messaging
│   ├── PointsCommand.java        # /points admin management
│   └── WorldCommand.java         # /world load/unload/goto/setspawn/list
├── game/
│   ├── GameCommand.java          # /game list|start|stop|status
│   ├── GameController.java       # Interface implemented by every mini-game
│   ├── GameRegistry.java         # Registry keying game ID → controller
│   ├── WorldUtil.java            # Recursive world copy / delete helpers
│   ├── battlebox/                # Battle Box game
│   ├── skywars/                  # SkyWars game
│   ├── tntrun/                   # TNT Run game
│   └── uhc/                      # UHC Meetup game
├── holo/
│   ├── HologramCommand.java      # /holo set|clear|reload
│   └── HologramManager.java      # Spawns / updates TextDisplay entities
├── listeners/
│   ├── KillFeedbackListener.java # Chat notification on player kill
│   ├── LeaderboardListener.java  # Inventory-GUI leaderboard interaction
│   ├── MotdListener.java         # Dynamic MOTD with game status
│   ├── PlayerJoinQuitListener.java # Show sidebar on join, cleanup on quit
│   └── SlotsLoginListener.java   # Reject connections when slots cap is hit
├── lobby/
│   ├── LobbyListener.java        # Void-fall protection, join teleport
│   ├── LobbyManager.java         # Creates/loads the void lobby world
│   └── VoidChunkGenerator.java   # Empty chunk generator for the lobby
├── points/
│   └── PointsService.java        # Thread-safe points store + YAML persistence
├── scoreboard/
│   └── SidebarManager.java       # Per-player Paper scoreboard sidebar
├── slots/
│   ├── SlotsCommand.java         # /slots <number|off>
│   └── SlotsManager.java         # Configurable max-player cap
└── teams/
    ├── TeamChatListener.java     # Routes team-chat messages
    ├── TeamChatToggleCommand.java # /teamchat (toggle) + /tc alias
    ├── TeamCommand.java          # /team join|leave|list
    ├── TeamDeathListener.java    # Clears team membership on death (if configured)
    ├── TeamManager.java          # Scoreboard-backed team store
    └── TeamTabCompleter.java     # Tab completion for /team
```

---

## Games

All games share the same lifecycle interface (`GameController`) and are managed through the `/game` command.

```java
public interface GameController {
    String getId();
    String getDisplayName();
    void prepare(CommandSender initiator);
    void start(CommandSender initiator);
    void stop(CommandSender initiator);
    String status();
}
```

### Battle Box

Players are sorted into colour-coded teams and compete across multiple arenas simultaneously. A round-robin tournament scheduler automatically pairs every team against every other team with no arena used twice per pair, then awards points to the winning side.

**Key mechanics**
- Multiple arenas can run in parallel within a single series
- Round-robin bracket is generated automatically from the live team pool
- Each arena has configurable `pos1`/`pos2` bounds and per-team spawn points
- Players receive a kit on spawn; items are wiped on round end
- Points awarded to winning team; sidebar updates live

**Admin setup**
```
/bb arena create <id>
/bb arena setpos1 <id>
/bb arena setpos2 <id>
/bb arena setspawn <id> <team>
/bb arena list
```

---

### SkyWars

Team-based SkyWars where players start on sky islands. Each round uses a freshly cloned copy of the template world that is created on start and deleted on end.

**Key mechanics**
- World template is copied at round start (`skywars_r<N>`) and deleted when the round ends
- A `SoftBorder` shrinks from a configurable outer radius to the centre, dealing increasing damage to players outside it (displayed as a particle wall)
- Players are frozen for a configurable pre-start window before the round goes live
- Last team standing wins; survivors carry into the next round
- Random loot chests can be seeded into the template world

---

### UHC Meetup

Ultra-Hardcore style meetup: natural health regeneration is disabled, the world border shrinks to force encounters, and the game ends when only one team remains.

**Key mechanics**
- Clones a template world per round (`uhc_meetup_r<N>`)
- Grace period at start: a particle border marks the initial safe zone while a task ceiling caps max health gains
- Border shrinks from start radius → 1 block over a configurable duration, applying DPS to anyone outside
- A configurable block whitelist restricts which blocks can be broken/placed
- Powerless bows (zero-damage) can be toggled for the grace period
- Health displayed above player names via a `BELOW_NAME` objective
- Two rounds can run back-to-back with surviving players carrying over

---

### TNT Run

Players run across a multi-layer platform that collapses beneath their feet. The last player (or team) standing wins.

**Key mechanics**
- World cloned from a configurable template at round start
- Blocks beneath each player's feet are removed on a tick-rate basis (`TntRunListener`)
- Players who fall out of the world are eliminated
- Multiple rounds with configurable count; points awarded to the winning team
- World is unloaded and deleted between rounds

---

## Core Systems

### Team System

Twelve colour-coded teams are pre-registered on plugin load. Teams are backed by Bukkit `Scoreboard` teams so colours and prefixes appear in TAB list and above player names with no extra client-side mod needed.

| Key | Colour |
|---|---|
| `red` | Red |
| `blue` | Blue |
| `green` | Green |
| `yellow` | Yellow |
| `purple` | Dark Purple |
| `orange` | Gold |
| `aqua` | Aqua |
| `pink` | Light Purple |
| `silver` | Gray |
| `gray` | Dark Gray |
| `black` | Black |
| `white` | White |

Players join with `/team join <team>`, and a per-player scoreboard is cloned so every client sees correct prefix colouring regardless of their own scoreboard state.

Team chat is toggled with `/tc`. While enabled, chat messages are intercepted and delivered only to team members.

---

### Points & Leaderboard

`PointsService` maintains a thread-safe `ConcurrentHashMap<UUID, Integer>`. Points are added by each game on round end and persisted to `plugins/JumpCat/points.yml` on plugin disable.

- `/leaderboard [page]` shows a paginated top-10 list in chat
- `/points clear [save]` lets admins reset the board
- Rankings are also visible through the hologram system (see below)

---

### Sidebar / HUD

Each player receives their own `Scoreboard` with a sidebar objective managed by `SidebarManager`. It refreshes every second and displays:

- Server/event name header
- Current game name and round
- Player's team
- Kill / Death counts (tracked by `CombatService`)
- Player's current rank by points

The "below name" health objective is also registered on each player's board, so ❤ values float beneath every player's name tag.

---

### Hologram Leaderboards

`HologramManager` spawns Paper `TextDisplay` entities at configured world coordinates and updates them on a configurable interval. Two hologram types are supported:

- **PLAYER** – shows top-N players by individual points
- **TEAM** – shows top-N teams by aggregated member points

Config is stored in `plugins/JumpCat/holograms.yml`. Holograms survive reloads and are cleaned up on shutdown.

```
/holo set <id> <type> <lines> [title]   # create or update
/holo clear <id>                        # remove
/holo reload                            # reload all from config
```

---

### Lobby

A void world (`lobby`) is created automatically if it does not exist, using a custom empty chunk generator. Players are teleported here on join, on `/lobby`, and at the end of each game round.

`LobbyListener` cancels fall damage in the void and teleports players back to spawn if they fall too far.

---

### Soft Border

`SoftBorder` is a reusable particle-effect border used by both SkyWars and UHC Meetup. It:

1. Renders a ring of red dust particles at the current boundary radius every N ticks
2. Applies configurable base DPS to any player outside the border, scaling up to a configurable max DPS the further they stray
3. Linearly interpolates the radius from `startRadius` → `endRadius` over the given duration

The border runs on Bukkit scheduler tasks and stops cleanly when the game ends.

---

### Moderation

`ModerationCommand` provides ban, mute, kick, unban, and unmute as sub-commands (all aliased from the root `/moderation` command). Bans and mutes are held in static in-memory maps and enforced by `ModerationListener` on every login and chat event.

Duration format: a number followed by `s`, `m`, `h`, or `d` (e.g. `30m`, `7d`). Omit to apply a permanent action.

```
/ban   <player> [duration] [reason]
/mute  <player> [duration] [reason]
/kick  <player> [reason]
/unban <player>
/unmute <player>
```

---

### Private Messaging

`MsgCommand` handles `/msg <player> <message>` and `/r <message>` (reply). Last-conversation state is stored per-sender UUID so `/r` always replies to the correct recipient.

---

### World Management

`WorldCommand` lets operators manage worlds at runtime without restarting the server:

```
/world list              # list all loaded worlds
/world goto <world>      # teleport to a world's spawn
/world setspawn <world>  # set a world's spawn to your current position
/world load <name>       # load (or create) a world by folder name
/world unload <name>     # save & unload a world
```

---

### Dynamic MOTD & Slots Cap

`MotdListener` intercepts server ping events and injects the active game's display name and current round into the MOTD description line. `SlotsManager` stores a configurable slot cap that overrides the server's `max-players` value; `SlotsLoginListener` rejects connections that would exceed it.

```
/slots <number>   # set the cap
/slots off        # remove the cap (use server default)
```

---

## Commands Reference

| Command | Aliases | Description | Permission |
|---|---|---|---|
| `/jumpcat` | `/jc` | JumpCat admin utility | OP |
| `/team join\|leave\|list [team]` | — | Manage team membership | All |
| `/teamchat` | `/tc` | Toggle team-only chat | All |
| `/game list\|start\|stop\|status [id]` | — | Start / stop mini-games | OP |
| `/bb arena <create\|setpos1\|setpos2\|setspawn\|list>` | — | Battle Box arena setup | OP |
| `/lobby` | — | Teleport to the lobby | All |
| `/leaderboard [page]` | `/lb` | View points leaderboard | All |
| `/holo set\|clear\|reload` | — | Manage holograms | OP |
| `/points clear [save]` | — | Clear points data | OP |
| `/slots <number\|off>` | — | Set server slot cap | OP |
| `/world list\|goto\|setspawn\|load\|unload` | — | Runtime world tools | OP |
| `/event end` | — | Announce winner & reset | OP |
| `/msg <player> <message>` | `/tell` `/whisper` `/w` `/m` | Private message | All |
| `/r <message>` | — | Reply to last PM | All |
| `/ban <player> [duration] [reason]` | — | Ban a player | OP |
| `/mute <player> [duration] [reason]` | — | Mute a player | OP |
| `/kick <player> [reason]` | — | Kick a player | OP |
| `/unban <player>` | — | Remove a ban | OP |
| `/unmute <player>` | — | Remove a mute | OP |

---

## Building & Installation

**Requirements:** JDK 21, internet access for Gradle to fetch dependencies.

```bash
# Clone the repository
git clone https://github.com/dahiiv2/JumpCat.git
cd JumpCat

# Build the plugin JAR
./gradlew build
# Output: build/libs/JumpCat-0.1.0.jar
```

1. Copy `build/libs/JumpCat-0.1.0.jar` into your Paper 1.21 server's `plugins/` folder.
2. Start (or restart) the server.
3. The plugin will auto-create a `lobby` void world and register all 12 teams on first run.
4. Use `/bb arena create <id>` to set up Battle Box arenas before running that game.
5. Place template worlds in the server root folder named `skywars_template`, `uhc_meetup_template`, and `tntrun_template` for the respective games.
