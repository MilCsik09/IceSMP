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

        subcommand.execute(sender, Arrays.copyOfRange(args, 1, args.length));
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();

        if (args.length == 0) {
            return subcommands.keySet().stream().toList();
        }

        if (args.length == 1) {
            return subcommands.keySet().stream()
                    .filter(name -> name.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }

        final Subcommand subcommand = subcommands.get(args[0].toLowerCase(Locale.ROOT));
        if (subcommand == null) {
            return List.of();
        }

        return subcommand.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
    }

    private void sendHelp(final CommandSender sender) {
        sender.sendMessage(messageManager.get("messages." + commandName + "-help-header", helpHeaderDefault));
        for (final Subcommand subcommand : subcommands.values()) {
            sender.sendMessage(messageManager.get(
                    "messages." + commandName + "-help-" + subcommand.name(),
                    "&e" + subcommand.usage() + " &7- " + subcommand.description()));
        }
    }
}
