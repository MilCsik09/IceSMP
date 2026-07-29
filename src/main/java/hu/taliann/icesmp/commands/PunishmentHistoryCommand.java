package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.managers.ModerationManager;
import hu.taliann.icesmp.moderation.PunishmentRecord;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** Paginated complete punishment history; never truncates the authoritative ledger. */
public final class PunishmentHistoryCommand implements BasicCommand {

    private static final int PAGE_SIZE = 8;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());
    private final ModerationManager manager;
    private final MessageManager messages;
    private final String permission;

    public PunishmentHistoryCommand(final ModerationManager manager, final MessageManager messages,
                                    final String permission) {
        this.manager = manager;
        this.messages = messages;
        this.permission = permission;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack source, final @NonNull String[] args) {
        final CommandSender sender = source.getSender();
        if (!sender.hasPermission(permission)) {
            sender.sendMessage(messages.get("moderation.permission-denied", "&cNincs jogod ehhez a parancshoz."));
            return;
        }
        if (args.length < 1) {
            sender.sendMessage(messages.get("moderation.history-usage", "&cHasználat: /history <játékos> [oldal]"));
            return;
        }
        final ModerationCommandSupport.Target target = ModerationCommandSupport.resolveTarget(manager, args[0])
                .orElse(null);
        if (target == null) {
            sender.sendMessage(messages.get("moderation.player-unknown", "&cIsmeretlen játékos: &f%s", args[0]));
            return;
        }
        final int requested = args.length >= 2 ? parsePositive(args[1]) : 1;
        final List<PunishmentRecord> history = manager.history(target.id());
        if (history.isEmpty()) {
            sender.sendMessage(messages.get("moderation.history-empty", "&7Nincs büntetési előzmény: &f%s", target.name()));
            return;
        }
        final int pages = Math.max(1, (history.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        final int page = Math.min(pages, Math.max(1, requested));
        sender.sendMessage(messages.get("moderation.history-header",
                "&6Büntetési előzmény: &f%s &7(%d/%d)", target.name(), page, pages));
        final int from = (page - 1) * PAGE_SIZE;
        for (final PunishmentRecord record : history.subList(from, Math.min(history.size(), from + PAGE_SIZE))) {
            sender.sendMessage(messages.get("moderation.history-line",
                    "&7- &f%s &8| &7%s &8| &f%s &8| &7%s &8| &f%s",
                    record.type().name(), DATE.format(Instant.ofEpochMilli(record.createdAtMillis())),
                    record.state().name(), record.administratorName(),
                    record.reason().isBlank() ? "nincs ok" : record.reason()));
        }
    }

    private static int parsePositive(final String value) {
        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (final NumberFormatException ignored) {
            return 1;
        }
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack source,
                                                final @NonNull String[] args) {
        if (!source.getSender().hasPermission(permission)) {
            return List.of();
        }
        if (args.length <= 1) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return ModerationCommandSupport.visibleOnlineNames(source.getSender(), prefix);
        }
        return List.of();
    }
}
