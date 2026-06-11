package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.commands.faction.FactionJoinSubcommand;
import hu.taliann.icesmp.commands.faction.FactionLeaveSubcommand;
import hu.taliann.icesmp.commands.faction.FactionSetSubcommand;
import hu.taliann.icesmp.commands.faction.FactionSubcommand;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.MetelytepoManager;
import hu.taliann.icesmp.utils.MessageManager;
import hu.taliann.icesmp.utils.TextUtil;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FactionCommand implements BasicCommand {

    private final Map<String, FactionSubcommand> subcommands = new LinkedHashMap<>();
    private final MessageManager messageManager;

    public FactionCommand(final FactionManager factionManager, final MetelytepoManager metelytepoManager,
                          final MessageManager messageManager) {
        this.messageManager = messageManager;
        register(new FactionJoinSubcommand(factionManager, metelytepoManager, messageManager));
        register(new FactionLeaveSubcommand(factionManager, messageManager));
        register(new FactionSetSubcommand(factionManager, metelytepoManager, messageManager));
    }

    private void register(final FactionSubcommand subcommand) {
        subcommands.put(subcommand.name().toLowerCase(), subcommand);
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();

        if (args.length == 0) {
            sendHelp(sender);
            return;
        }

        final FactionSubcommand subcommand = subcommands.get(args[0].toLowerCase());
        if (subcommand == null) {
            sender.sendMessage(messageManager.get("messages.faction-unknown-subcommand", "&cIsmeretlen alparancs: &f%s", args[0]));
            sendHelp(sender);
            return;
        }

        final String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        subcommand.execute(sender, subArgs);
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();

        if (args.length == 0) {
            return subcommands.keySet().stream().toList();
        }

        if (args.length == 1) {
            return subcommands.keySet().stream()
                    .filter(name -> name.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        final FactionSubcommand subcommand = subcommands.get(args[0].toLowerCase());
        if (subcommand == null) {
            return List.of();
        }

        final String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        return subcommand.tabComplete(sender, subArgs);
    }

    private void sendHelp(final CommandSender sender) {
        final String header = messageManager.get("messages.faction-help-header", "&6/faction &7- elérhető parancsok:");
        sender.sendMessage(header);
        for (final FactionSubcommand subcommand : subcommands.values()) {
            sender.sendMessage(messageManager.get(
                    "messages.faction-help-" + subcommand.name(),
                    TextUtil.color("&e" + subcommand.usage() + " &7- " + subcommand.description())
            ));
        }
    }
}

