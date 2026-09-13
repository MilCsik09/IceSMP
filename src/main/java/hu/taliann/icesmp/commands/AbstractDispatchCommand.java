package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Base for the registry-style commands (currency, job, faction). It owns the subcommand map and
 * the shared dispatch / tab-complete / help logic, so each concrete command shrinks to a
 * constructor that registers its {@link Subcommand}s and supplies its name (used to build the
 * {@code messages.<name>-*} keys) and a help-header default.
 *
 * <p>Message keys preserved exactly: {@code messages.<name>-unknown-subcommand},
 * {@code messages.<name>-help-header}, {@code messages.<name>-help-<sub>}.
 */
public abstract class AbstractDispatchCommand implements BasicCommand {

    protected final MessageManager messageManager;
    private final Map<String, Subcommand> subcommands = new LinkedHashMap<>();
    private final String commandName;
    private final String helpHeaderDefault;

    protected AbstractDispatchCommand(final MessageManager messageManager, final String commandName,
                                      final String helpHeaderDefault) {
        this.messageManager = messageManager;
        this.commandName = commandName;
        this.helpHeaderDefault = helpHeaderDefault;
    }

    protected final void register(final Subcommand subcommand) {
        subcommands.put(subcommand.name().toLowerCase(Locale.ROOT), subcommand);
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();

        if (args.length == 0) {
            sendHelp(sender);
            return;
        }

        final Subcommand subcommand = subcommands.get(args[0].toLowerCase(Locale.ROOT));
        if (subcommand == null) {
            sender.sendMessage(messageManager.get("messages." + commandName + "-unknown-subcommand",
                    "&cIsmeretlen alparancs: &f%s", args[0]));
            sendHelp(sender);
            return;
        }

        if (!subcommand.isVisibleTo(sender)) {
            sender.sendMessage(messageManager.get("messages.permission-denied",
                    "&cNincs jogosultságod erre a parancsra."));
            return;
        }

        subcommand.execute(sender, Arrays.copyOfRange(args, 1, args.length));
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();

        if (args.length == 0) {
            return subcommands.values().stream()
                    .filter(subcommand -> subcommand.isVisibleTo(sender))
                    .map(Subcommand::name).toList();
        }

        // Paper nem adja át a lezáró szóköz utáni üres szót, ezért a subcommand-név pozícióját
        // csak akkor tekintjük "még gépelés alatt"-nak, ha args[0] nem egyezik pontosan egy
        // ismert alparanccsal — egyébként (args.length==1, pontos egyezés) már a subcommand
        // saját tabComplete()-jét kell hívni üres maradék-args-szal (első alparancs-argumentum).
        final String first = args[0].toLowerCase(Locale.ROOT);
        final Subcommand subcommand = subcommands.get(first);

        if (args.length == 1 && subcommand == null) {
            return subcommands.keySet().stream()
                    .filter(name -> name.startsWith(first))
                    .filter(name -> subcommands.get(name).isVisibleTo(sender))
                    .toList();
        }

        if (subcommand == null || !subcommand.isVisibleTo(sender)) {
            return List.of();
        }

        return subcommand.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
    }

    private void sendHelp(final CommandSender sender) {
        sender.sendMessage(messageManager.get("messages." + commandName + "-help-header", helpHeaderDefault));
        for (final Subcommand subcommand : subcommands.values()) {
            if (!subcommand.isVisibleTo(sender)) continue;
            sender.sendMessage(messageManager.get(
                    "messages." + commandName + "-help-" + subcommand.name(),
                    "&e" + subcommand.usage() + " &7- " + subcommand.description()));
        }
    }
}
