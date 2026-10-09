# ArcadeHub

Cross-game features for an ArcadeCore server. It only uses the public `ArcadeAPI`, so it has no
game-specific code and works with any game registered in ArcadeCore (FFA, BedrockPillars, or your own).

- Leaderboards and personal stats across games.
- Playlist: after a match ends, opted-in players are requeued into the next game in a configured order.
- Lobby vote: players vote for a game and the winner is queued for everyone who voted.

## Supported versions

One jar runs on Minecraft 1.8 to 26.3 (Spigot, Paper and forks). It is compiled to Java 8 bytecode against
spigot-api 1.8.8, so the server's Java version only has to be whatever that Minecraft version needs.

Boot and command smoke tests were run on Paper 1.8.8, 1.12.2, 1.16.5, 1.20.4, 1.21.4 and 26.3 together with
ArcadeCore, ArcadeBedrockPillars and ArcadeFFA. /hub top was exercised after a bot-driven FFA match on 1.8.8, 1.12.2, 1.16.5, 1.20.4, 1.21.4 and 26.1.2. /hub vote and /hub playlist have not been exercised by bots.

## Install

1. Install ArcadeCore (`plugin.yml` declares `depend: [ArcadeCore]`, so ArcadeHub will not load without it).
2. Drop the ArcadeHub jar into `plugins/` next to it and restart.

Build: `./gradlew build` from this directory. It needs ArcadeCore checked out at `../ArcadeCore` (included
build) and builds against `org.drappula:ArcadeAPI:1.1.0`. Do not run Gradle in this project and another
Arcade project at the same time, they share the included ArcadeCore build directory.

## Commands

Single command `/hub` (plain Bukkit executor, tab completion included).

| Command | What it does |
| --- | --- |
| `/hub top [game]` | Top 10 players overall, or for one game id. Console can use it. |
| `/hub stats` | Your own stats for every registered game you have a record in. Players only. |
| `/hub playlist on\|off` | Opt in or out of the playlist. Players only. |
| `/hub vote <game>` | Vote for a game. Players only, and only from the lobby (not queued, not in a match). |

Stats lines look like `3W/1L, 12 pts [kills=4 survival_seconds=90]`; the bracket part is whatever custom stats
the games record.

How the playlist works: when a match reaches `ENDED`, players from that match who ran `/hub playlist on` are
queued for the next game in `playlist` after `requeue-delay-ticks`. Ids that are not registered or are
disabled are skipped. The opt-in list is kept in memory only, so it resets on restart and when a player quits.

How the vote works: the first vote opens a window of `vote.window-seconds` and broadcasts a notice. When it
closes, the game with the most votes wins (ties are broken randomly) and all voters who are still online and
not already queued are queued for it. Votes are dropped when a player quits.

## Config

`plugins/ArcadeHub/config.yml`:

```yaml
# Games played in order by /hub playlist (ids as registered with ArcadeCore).
playlist: [ffa, bedrock-pillars]
# Ticks to wait after a match ends before requeueing (core must return players to lobby first).
requeue-delay-ticks: 20
vote:
  window-seconds: 15
```

| Key | Default | Meaning |
| --- | --- | --- |
| `playlist` | `[ffa, bedrock-pillars]` | Game ids in play order. After the last one it wraps to the first. A match of a game not in this list does not trigger requeue. |
| `requeue-delay-ticks` | `20` | Delay before requeueing. Values below 1 are treated as 1. ArcadeCore needs a few seconds to tear down a match and return players to the lobby; a player still in a match is rejected with `ALREADY_IN_MATCH`, so raise this if requeue fails. |
| `vote.window-seconds` | `15` | Length of the vote window. Values below 1 are treated as 1. |

## Version notes

- Output is chat only (through `org.drappula.arcadeApi.message.Messages`), so ArcadeHub itself has no
  version-specific degradation. Titles, action bars and the boss bar fallback are ArcadeCore and game concerns.
- Text uses the MiniMessage subset translated by ArcadeAPI, not Adventure, so it renders the same on all versions.
- This build is compiled against ArcadeAPI 1.1.0 and is meant to be used with the matching ArcadeCore from the
  same branch. ArcadeAPI 1.1.0 is a breaking change from 1.0.0 (`IMatch.broadcast` now takes
  `String... placeholders`), so keep ArcadeCore and all addons on the same side of it.
