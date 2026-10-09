package org.drappula.arcadeHub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.drappula.arcadeApi.ArcadeAPIProvider;
import org.drappula.arcadeApi.events.MatchStateChangeEvent;
import org.drappula.arcadeApi.message.Messages;
import org.drappula.arcadeApi.systems.game.Game;
import org.drappula.arcadeApi.systems.game.IMatch;
import org.drappula.arcadeApi.systems.game.IParticipant;
import org.drappula.arcadeApi.systems.game.MatchState;
import org.drappula.arcadeApi.systems.queue.JoinResult;

/** Cross-game features on top of ArcadeCore's public API: playlist requeue and lobby game vote. */
public final class ArcadeHub extends JavaPlugin implements Listener {
    private static ArcadeHub instance;

    public static ArcadeHub get() {
        return instance;
    }

    /** Players who opted into the playlist (in-memory; resets on restart). */
    final Set<UUID> playlist = new HashSet<UUID>();
    /** Lobby votes: player -> game id. */
    final Map<UUID, String> votes = new HashMap<UUID, String>();
    private boolean voteRunning;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        HubCommand command = new HubCommand(this);
        getCommand("hub").setExecutor(command);
        getCommand("hub").setTabCompleter(command);
    }

    // ---- playlist ----

    @EventHandler
    public void onMatchStateChange(MatchStateChangeEvent event) {
        if (event.getNewState() != MatchState.ENDED) return;
        IMatch match = event.getMatch();
        Game next = nextGame(match.getGame().getId());
        if (next == null) return;

        // Capture now: the core may clear participants once ENDED handlers finish.
        Set<UUID> ids = new LinkedHashSet<UUID>();
        for (IParticipant p : match.getParticipants()) ids.add(p.getPlayer().getUniqueId());
        for (IParticipant p : match.getEliminatedParticipants()) ids.add(p.getPlayer().getUniqueId());
        ids.retainAll(playlist);
        if (ids.isEmpty()) return;

        long delay = Math.max(1, getConfig().getLong("requeue-delay-ticks", 20));
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {
            @Override
            public void run() {
                requeue(ids, next);
            }
        }, delay);
    }

    private Game nextGame(String currentId) {
        List<String> order = getConfig().getStringList("playlist");
        int i = order.indexOf(currentId);
        if (i < 0 || order.isEmpty()) return null;
        org.drappula.arcadeApi.systems.game.IGameManager games = ArcadeAPIProvider.get().getGameManager();
        // Skip unregistered ids; give up after one full lap.
        for (int step = 1; step <= order.size(); step++) {
            Game g = games.getGame(order.get((i + step) % order.size()));
            if (g != null && g.isEnabled()) return g;
        }
        return null;
    }

    private void requeue(Set<UUID> ids, Game game) {
        org.drappula.arcadeApi.systems.queue.IQueueManager queue = ArcadeAPIProvider.get().getQueueManager();
        org.drappula.arcadeApi.systems.game.IMatchManager matches = ArcadeAPIProvider.get().getMatchManager();
        List<Player> ready = new ArrayList<Player>();
        for (UUID id : ids) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline() && playlist.contains(id) && !queue.isQueued(p) && !matches.getMatch(p).isPresent()) {
                ready.add(p);
            }
        }
        if (ready.isEmpty()) return;
        JoinResult result = queue.joinQueue(ready, game);
        for (Player p : ready) {
            Messages.chat(p, result == JoinResult.SUCCESS
                    ? "<green>Playlist: queued for <white><game></white>. /hub playlist off to stop."
                    : "<red>Playlist could not queue you for <game>: <reason>",
                    "game", game.getDisplayName(), "reason", result.name().toLowerCase(Locale.ROOT).replace('_', ' '));
        }
    }

    // ---- vote ----

    /** Registers a vote; the first vote opens the window. Returns false if the player is busy. */
    boolean vote(Player player, Game game) {
        org.drappula.arcadeApi.ArcadeAPI api = ArcadeAPIProvider.get();
        if (api.getQueueManager().isQueued(player) || api.getMatchManager().getMatch(player).isPresent()) {
            return false;
        }
        votes.put(player.getUniqueId(), game.getId());
        if (!voteRunning) {
            voteRunning = true;
            long secs = Math.max(1, getConfig().getLong("vote.window-seconds", 15));
            Map<String, String> placeholders = new HashMap<String, String>();
            placeholders.put("game", "[game]");
            placeholders.put("secs", String.valueOf(secs));
            String text = org.drappula.arcadeApi.message.LegacyText.translate(
                    "<gold>Game vote started! <white>/hub vote <game></white> within <secs>s.", placeholders);
            for (Player online : Bukkit.getOnlinePlayers()) online.sendMessage(text);
            Bukkit.getScheduler().runTaskLater(this, new Runnable() {
                @Override
                public void run() {
                    closeVote();
                }
            }, secs * 20L);
        }
        return true;
    }

    private void closeVote() {
        Map<String, Integer> tally = new HashMap<String, Integer>();
        for (String voted : votes.values()) {
            Integer count = tally.get(voted);
            tally.put(voted, count == null ? 1 : count + 1);
        }
        List<UUID> voters = new ArrayList<UUID>(votes.keySet());
        votes.clear();
        voteRunning = false;
        if (tally.isEmpty()) return;

        int best = 0;
        for (int count : tally.values()) best = Math.max(best, count);
        List<String> winners = new ArrayList<String>();
        for (Map.Entry<String, Integer> entry : tally.entrySet()) {
            if (entry.getValue() == best) winners.add(entry.getKey());
        }
        Game game = ArcadeAPIProvider.get().getGameManager().getGame(winners.get(new Random().nextInt(winners.size())));
        if (game == null) return;

        org.drappula.arcadeApi.systems.queue.IQueueManager queue = ArcadeAPIProvider.get().getQueueManager();
        List<Player> players = new ArrayList<Player>();
        for (UUID id : voters) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline() && !queue.isQueued(p)) players.add(p);
        }
        if (players.isEmpty()) return;
        JoinResult result = queue.joinQueue(players, game);
        for (Player p : players) {
            Messages.chat(p, result == JoinResult.SUCCESS
                    ? "<green>Vote won: <white><game></white>. You are queued."
                    : "<red>Vote won by <game> but queueing failed: <reason>",
                    "game", game.getDisplayName(), "reason", result.name().toLowerCase(Locale.ROOT).replace('_', ' '));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        votes.remove(event.getPlayer().getUniqueId());
        playlist.remove(event.getPlayer().getUniqueId());
    }
}
