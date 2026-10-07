package org.drappula.arcadeHub;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.drappula.arcadeApi.ArcadeAPIProvider;
import org.drappula.arcadeApi.database.GameStats;
import org.drappula.arcadeApi.systems.game.Game;

public final class HubCommand {
    private HubCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> get(ArcadeHub hub) {
        var gameArg = Commands.argument("game", StringArgumentType.word()).suggests((ctx, b) -> {
            ArcadeAPIProvider.get().getGameManager().getRegisteredGames().forEach(g -> b.suggest(g.getId()));
            return b.buildFuture();
        });
        return Commands.literal("hub")
                .executes(ctx -> msg(ctx, "<aqua><b>Arcade Hub</b></aqua> <gray>- /hub top [game] | stats | playlist on|off | vote <game>"))
                .then(Commands.literal("top")
                        .executes(ctx -> top(ctx, null))
                        .then(Commands.argument("game", StringArgumentType.word())
                                .executes(ctx -> top(ctx, StringArgumentType.getString(ctx, "game")))))
                .then(Commands.literal("stats").executes(HubCommand::stats))
                .then(Commands.literal("playlist")
                        .then(Commands.literal("on").executes(ctx -> playlist(ctx, hub, true)))
                        .then(Commands.literal("off").executes(ctx -> playlist(ctx, hub, false))))
                .then(Commands.literal("vote").then(gameArg.executes(ctx -> vote(ctx, hub))))
                .build();
    }

    private static int msg(CommandContext<CommandSourceStack> ctx, String text) {
        ctx.getSource().getSender().sendRichMessage(text);
        return Command.SINGLE_SUCCESS;
    }

    private static int top(CommandContext<CommandSourceStack> ctx, String gameId) {
        CommandSender sender = ctx.getSource().getSender();
        var api = ArcadeAPIProvider.get();
        try {
            List<GameStats> rows = gameId == null
                    ? api.getStatsManager().getTopOverall(10)
                    : api.getStatsManager().getTopPlayers(gameId, 10);
            if (rows.isEmpty()) return msg(ctx, "<gray>No stats yet.");
            sender.sendRichMessage("<gold><b>Top " + (gameId == null ? "overall" : gameId) + "</b>");
            int rank = 1;
            for (GameStats s : rows) {
                sender.sendRichMessage("<yellow>#" + rank++ + " <white>" + name(s.getUuid())
                        + " <gray>" + s.getWins() + "W/" + s.getLosses() + "L, " + s.getPoints() + " pts"
                        + extras(s));
            }
        } catch (SQLException e) {
            ArcadeHub.get().getSLF4JLogger().error("top failed", e);
            return msg(ctx, "<red>Could not read stats.");
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int stats(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) return msg(ctx, "<red>Players only.");
        var api = ArcadeAPIProvider.get();
        player.sendRichMessage("<gold><b>Your stats");
        boolean any = false;
        try {
            for (Game g : api.getGameManager().getRegisteredGames()) {
                var s = api.getStatsManager().getStats(player.getUniqueId(), g.getId());
                if (s.isEmpty()) continue;
                any = true;
                player.sendRichMessage("<yellow>" + g.getDisplayName() + " <gray>" + s.get().getWins() + "W/"
                        + s.get().getLosses() + "L, " + s.get().getPoints() + " pts" + extras(s.get()));
            }
        } catch (SQLException e) {
            ArcadeHub.get().getSLF4JLogger().error("stats failed", e);
            return msg(ctx, "<red>Could not read stats.");
        }
        if (!any) player.sendRichMessage("<gray>Nothing recorded yet.");
        return Command.SINGLE_SUCCESS;
    }

    private static String extras(GameStats s) {
        if (s.getOther().isEmpty()) return "";
        StringBuilder sb = new StringBuilder(" <dark_gray>[");
        s.getOther().forEach((k, v) -> sb.append(k).append('=').append(v).append(' '));
        return sb.toString().trim() + "]";
    }

    private static String name(UUID id) {
        try {
            return ArcadeAPIProvider.get().getUserData(id).map(u -> u.getUsername()).orElse(id.toString().substring(0, 8));
        } catch (SQLException e) {
            return id.toString().substring(0, 8);
        }
    }

    private static int playlist(CommandContext<CommandSourceStack> ctx, ArcadeHub hub, boolean on) {
        if (!(ctx.getSource().getSender() instanceof Player player)) return msg(ctx, "<red>Players only.");
        if (on) hub.playlist.add(player.getUniqueId());
        else hub.playlist.remove(player.getUniqueId());
        return msg(ctx, on ? "<green>Playlist on: you'll be queued into the next game after each match."
                : "<yellow>Playlist off.");
    }

    private static int vote(CommandContext<CommandSourceStack> ctx, ArcadeHub hub) {
        if (!(ctx.getSource().getSender() instanceof Player player)) return msg(ctx, "<red>Players only.");
        Game game = ArcadeAPIProvider.get().getGameManager().getGame(StringArgumentType.getString(ctx, "game"));
        if (game == null || !game.isEnabled()) return msg(ctx, "<red>Unknown or disabled game.");
        if (!hub.vote(player, game)) return msg(ctx, "<red>You can only vote from the lobby (not queued or in a match).");
        return msg(ctx, "<green>Voted for " + game.getDisplayName() + ".");
    }
}
