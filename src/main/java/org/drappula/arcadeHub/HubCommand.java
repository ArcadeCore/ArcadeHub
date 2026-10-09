package org.drappula.arcadeHub;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.drappula.arcadeApi.ArcadeAPIProvider;
import org.drappula.arcadeApi.database.GameStats;
import org.drappula.arcadeApi.message.Messages;
import org.drappula.arcadeApi.systems.game.Game;

/** {@code /hub}. A plain Bukkit executor so it works on every server version. */
public final class HubCommand implements CommandExecutor, TabCompleter {
    private static final List<String> ROOT = Arrays.asList("top", "stats", "playlist", "vote");

    private final ArcadeHub hub;

    public HubCommand(ArcadeHub hub) {
        this.hub = hub;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "top":
                top(sender, args.length > 1 ? args[1] : null);
                break;
            case "stats":
                stats(sender);
                break;
            case "playlist":
                if (args.length > 1 && args[1].equalsIgnoreCase("on")) playlist(sender, true);
                else if (args.length > 1 && args[1].equalsIgnoreCase("off")) playlist(sender, false);
                else usage(sender);
                break;
            case "vote":
                if (args.length > 1) vote(sender, args[1]);
                else usage(sender);
                break;
            default:
                usage(sender);
        }
        return true;
    }

    private static void usage(CommandSender sender) {
        Messages.chat(sender, "<aqua><b>Arcade Hub</b></aqua> <gray>- /hub top [game] | stats | playlist on|off | vote [game]");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<String>();
        if (args.length == 1) {
            options.addAll(ROOT);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("top") || sub.equals("vote")) {
                for (Game game : ArcadeAPIProvider.get().getGameManager().getRegisteredGames()) options.add(game.getId());
            } else if (sub.equals("playlist")) {
                options.addAll(Arrays.asList("on", "off"));
            }
        }
        List<String> out = new ArrayList<String>();
        if (args.length == 0) return out;
        for (String option : options) {
            if (option.startsWith(args[args.length - 1].toLowerCase(Locale.ROOT))) out.add(option);
        }
        return out;
    }

    private static void top(CommandSender sender, String gameId) {
        org.drappula.arcadeApi.ArcadeAPI api = ArcadeAPIProvider.get();
        try {
            List<GameStats> rows = gameId == null
                    ? api.getStatsManager().getTopOverall(10)
                    : api.getStatsManager().getTopPlayers(gameId, 10);
            if (rows.isEmpty()) {
                Messages.chat(sender, "<gray>No stats yet.");
                return;
            }
            Messages.chat(sender, "<gold><b>Top <scope></b>", "scope", gameId == null ? "overall" : gameId);
            int rank = 1;
            for (GameStats s : rows) {
                Messages.chat(sender, "<yellow>#<rank> <white><name> <gray><line>",
                        "rank", String.valueOf(rank++), "name", name(s.getUuid()), "line", line(s));
            }
        } catch (SQLException e) {
            ArcadeHub.get().getLogger().log(Level.SEVERE, "top failed", e);
            Messages.chat(sender, "<red>Could not read stats.");
        }
    }

    private static void stats(CommandSender sender) {
        if (!(sender instanceof Player)) {
            Messages.chat(sender, "<red>Players only.");
            return;
        }
        Player player = (Player) sender;
        org.drappula.arcadeApi.ArcadeAPI api = ArcadeAPIProvider.get();
        Messages.chat(player, "<gold><b>Your stats");
        boolean any = false;
        try {
            for (Game g : api.getGameManager().getRegisteredGames()) {
                Optional<GameStats> s = api.getStatsManager().getStats(player.getUniqueId(), g.getId());
                if (!s.isPresent()) continue;
                any = true;
                Messages.chat(player, "<yellow><game> <gray><line>", "game", g.getDisplayName(), "line", line(s.get()));
            }
        } catch (SQLException e) {
            ArcadeHub.get().getLogger().log(Level.SEVERE, "stats failed", e);
            Messages.chat(player, "<red>Could not read stats.");
            return;
        }
        if (!any) Messages.chat(player, "<gray>Nothing recorded yet.");
    }

    /** "3W/1L, 12 pts [kills=4 survival_seconds=90]". Plain text: it is passed as a placeholder value. */
    private static String line(GameStats s) {
        StringBuilder sb = new StringBuilder();
        sb.append(s.getWins()).append("W/").append(s.getLosses()).append("L, ").append(s.getPoints()).append(" pts");
        if (!s.getOther().isEmpty()) {
            sb.append(" [");
            boolean first = true;
            for (Map.Entry<String, Integer> e : s.getOther().entrySet()) {
                if (!first) sb.append(' ');
                sb.append(e.getKey()).append('=').append(e.getValue());
                first = false;
            }
            sb.append(']');
        }
        return sb.toString();
    }

    private static String name(UUID id) {
        try {
            Optional<org.drappula.arcadeApi.database.UserData> user = ArcadeAPIProvider.get().getUserData(id);
            return user.isPresent() ? user.get().getUsername() : id.toString().substring(0, 8);
        } catch (SQLException e) {
            return id.toString().substring(0, 8);
        }
    }

    private void playlist(CommandSender sender, boolean on) {
        if (!(sender instanceof Player)) {
            Messages.chat(sender, "<red>Players only.");
            return;
        }
        UUID id = ((Player) sender).getUniqueId();
        if (on) hub.playlist.add(id);
        else hub.playlist.remove(id);
        Messages.chat(sender, on ? "<green>Playlist on: you'll be queued into the next game after each match."
                : "<yellow>Playlist off.");
    }

    private void vote(CommandSender sender, String gameId) {
        if (!(sender instanceof Player)) {
            Messages.chat(sender, "<red>Players only.");
            return;
        }
        Game game = ArcadeAPIProvider.get().getGameManager().getGame(gameId);
        if (game == null || !game.isEnabled()) {
            Messages.chat(sender, "<red>Unknown or disabled game.");
            return;
        }
        if (!hub.vote((Player) sender, game)) {
            Messages.chat(sender, "<red>You can only vote from the lobby (not queued or in a match).");
            return;
        }
        Messages.chat(sender, "<green>Voted for <game>.", "game", game.getDisplayName());
    }
}
