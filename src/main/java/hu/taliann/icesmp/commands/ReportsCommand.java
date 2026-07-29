package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.managers.ReportManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * /reports — admin bejelentés-kezelés, {@code icesmp.admin.moderation} mögé kapuzva:
 * (arg nélkül) nyitott bejelentések listája; {@code resolve <id>} lezárás; {@code all} az utolsó
 * 20 bejelentés (lezártak is). A játékos-oldal a {@link ReportCommand} ({@code /report}).
 */
public final class ReportsCommand implements BasicCommand {

    private static final String PERMISSION = "icesmp.admin.moderation";

    private static final String RESOLVE = "resolve";
    private static final String ALL = "all";
    private static final int ALL_LIMIT = 20;

    private final ReportManager reportManager;
    private final MessageManager messageManager;

    public ReportsCommand(final ReportManager reportManager, final MessageManager messageManager) {
        this.reportManager = reportManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(messageManager.get("permission-denied", "&cNincs jogod ehhez a parancshoz."));
            return;
        }

        if (args.length == 0) {
            listOpen(sender);
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case RESOLVE -> handleResolve(sender, args);
            case ALL -> listAll(sender);
            default -> listOpen(sender);
        }
    }

    private void listOpen(final CommandSender sender) {
        final List<ReportManager.Report> open = reportManager.openReports();
        if (open.isEmpty()) {
            sender.sendMessage(messageManager.get("reports-none-open", "&7Nincs nyitott bejelentés."));
            return;
        }

        sender.sendMessage(messageManager.get("reports-header",
                "&c[Bejelentések] &7Nyitott bejelentések (&f%d&7):", reportManager.openCount()));
        for (final ReportManager.Report report : open) {
            sender.sendMessage(formatEntry(report));
        }
    }

    private void listAll(final CommandSender sender) {
        final List<ReportManager.Report> all = reportManager.allReportsSorted();
        if (all.isEmpty()) {
            sender.sendMessage(messageManager.get("reports-none-any", "&7Még nem érkezett bejelentés."));
            return;
        }

        sender.sendMessage(messageManager.get("reports-all-header",
                "&c[Bejelentések] &7Utolsó legfeljebb %d bejelentés:", ALL_LIMIT));
        all.stream().limit(ALL_LIMIT).forEach(report -> sender.sendMessage(formatEntry(report)));
    }

    private void handleResolve(final CommandSender sender, final String[] args) {
        if (args.length < 2) {
            sender.sendMessage(messageManager.get("reports-resolve-usage", "&cHasználat: /reports resolve <id>"));
            return;
        }

        final int id;
        try {
            id = Integer.parseInt(args[1]);
        } catch (final NumberFormatException exception) {
            sender.sendMessage(messageManager.get("reports-invalid-id", "&cÉrvénytelen bejelentés-azonosító: &f%s", args[1]));
            return;
        }

        if (reportManager.resolve(id, sender.getName())) {
            sender.sendMessage(messageManager.get("reports-resolve-success", "&aBejelentés lezárva: &f#%d", id));
        } else {
            sender.sendMessage(messageManager.get("reports-resolve-not-found",
                    "&cNincs ilyen azonosítójú nyitott bejelentés: &f#%d", id));
        }
    }

    private String formatEntry(final ReportManager.Report report) {
        final String age = formatRelativeTime(report.createdAtMillis());
        if (report.resolved()) {
            return messageManager.get("reports-entry-resolved",
                    "&8#%d &7%s &8-> &7%s &8(%s, lezárva: %s)&7: %s",
                    report.id(), report.reporterName(), report.targetName(), age,
                    report.resolvedBy() == null ? "?" : report.resolvedBy(), report.reason());
        }
        return messageManager.get("reports-entry-open",
                "&c#%d &f%s &8-> &f%s &8(%s)&7: %s",
                report.id(), report.reporterName(), report.targetName(), age, report.reason());
    }

    /** A short Hungarian relative-time label ("most", "5 perce", "2 órája", "3 napja"). */
    private static String formatRelativeTime(final long thenMillis) {
        final long diffSeconds = Math.max(0L, (System.currentTimeMillis() - thenMillis) / 1000L);
        if (diffSeconds < 60L) {
            return "most";
        }
        final long minutes = diffSeconds / 60L;
        if (minutes < 60L) {
            return minutes + " perce";
        }
        final long hours = minutes / 60L;
        if (hours < 24L) {
            return hours + " órája";
        }
        final long days = hours / 24L;
        return days + " napja";
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        if (!commandSourceStack.getSender().hasPermission(PERMISSION)) {
            return List.of();
        }
        if (args.length <= 1) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return List.of(RESOLVE, ALL).stream().filter(option -> option.startsWith(prefix)).toList();
        }

        if (args.length == 2 && RESOLVE.equalsIgnoreCase(args[0])) {
            final String prefix = args[1];
            return reportManager.openReports().stream()
                    .map(report -> String.valueOf(report.id()))
                    .filter(id -> id.startsWith(prefix))
                    .toList();
        }

        return List.of();
    }
}
