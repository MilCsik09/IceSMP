package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.managers.ReportManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * /report &lt;név&gt; &lt;ok...&gt; — natív játékos-bejelentő. A célnak nem kell
 * online lennie; az indoklásnak legalább 3 szóból kell állnia, hogy legyen tartalma. Az admin
 * oldal a {@link ReportsCommand} ({@code /reports}).
 */
public final class ReportCommand implements BasicCommand {

    private static final int MIN_REASON_WORDS = 3;

    private final ReportManager reportManager;
    private final MessageManager messageManager;

    public ReportCommand(final ReportManager reportManager, final MessageManager messageManager) {
        this.reportManager = reportManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(messageManager.get("report-usage",
                    "&cHasználat: /report <név> <ok — legalább 3 szóban>"));
            return;
        }

        final String targetName = args[0];
        if (targetName.equalsIgnoreCase(player.getName())) {
            player.sendMessage(messageManager.get("report-self", "&cSaját magadat nem jelentheted be."));
            return;
        }

        final String[] reasonWords = Arrays.copyOfRange(args, 1, args.length);
        if (reasonWords.length < MIN_REASON_WORDS) {
            player.sendMessage(messageManager.get("report-reason-too-short",
                    "&cAz indoklás legyen legalább &f3 szó&c, hogy legyen tartalma."));
            return;
        }

        // Strip '&' from the free-text reason so a player can't inject color codes into the
        // message every online admin (and later /reports) will see it rendered inside.
        final String reason = String.join(" ", reasonWords).replace('&', ' ').trim();

        final String errorKey = reportManager.fileReport(player, targetName, reason);
        if (errorKey != null) {
            player.sendMessage(messageManager.get(errorKey, defaultErrorFor(errorKey)));
            return;
        }

        player.sendMessage(messageManager.get("report-success",
                "&aBejelentésed rögzítve — köszönjük, egy admin hamarosan megnézi."));
    }

    private String defaultErrorFor(final String errorKey) {
        return switch (errorKey) {
            case "report-rate-limited" -> "&cVárj még, mielőtt újra bejelentést küldenél (max. 1 percenként).";
            default -> "&cA bejelentés nem sikerült.";
        };
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        if (args.length <= 1) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
