package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.core.Permissions;
import hu.taliann.icesmp.gui.InvseeHolder;
import hu.taliann.icesmp.managers.InvseeManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** Opens a live, owner-scheduler-safe online inventory or ender-chest inspection session. */
public final class InvseeCommand implements BasicCommand {
    private final InvseeManager manager;
    private final MessageManager messages;

    public InvseeCommand(final InvseeManager manager, final MessageManager messages) {
        this.manager = manager;
        this.messages = messages;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack source, final @NonNull String[] args) {
        final CommandSender sender = source.getSender();
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(messages.get("moderation.player-only", "&cEzt csak játékos használhatja."));
            return;
        }
        if (args.length < 1) {
            viewer.sendMessage(messages.get("moderation.invsee-usage",
                    "&cHasználat: /invsee <online játékos> [read|edit] [main|ender]"));
            return;
        }
        final InvseeHolder.Mode mode = args.length >= 2 && "edit".equalsIgnoreCase(args[1])
                ? InvseeHolder.Mode.EDIT : InvseeHolder.Mode.READ_ONLY;
        final String required = mode == InvseeHolder.Mode.EDIT
                ? Permissions.MODERATION_INVENTORY_EDIT : Permissions.MODERATION_INVENTORY_READ;
        if (!viewer.hasPermission(required)) {
            viewer.sendMessage(messages.get("moderation.permission-denied", "&cNincs jogod ehhez a nézethez."));
            return;
        }
        final Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            viewer.sendMessage(messages.get("moderation.player-offline", "&cA játékos nincs online: &f%s", args[0]));
            return;
        }
        final InvseeHolder.View view = args.length >= 3 && "ender".equalsIgnoreCase(args[2])
                ? InvseeHolder.View.ENDER : InvseeHolder.View.MAIN;
        manager.open(viewer, target, mode, view);
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack source,
                                                final @NonNull String[] args) {
        final CommandSender sender = source.getSender();
        if (!sender.hasPermission(Permissions.MODERATION_INVENTORY_READ)
                && !sender.hasPermission(Permissions.MODERATION_INVENTORY_EDIT)) {
            return List.of();
        }
        if (args.length <= 1) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        if (args.length == 2) {
            final String prefix = args[1].toLowerCase(Locale.ROOT);
            final List<String> options = sender.hasPermission(Permissions.MODERATION_INVENTORY_EDIT)
                    ? List.of("read", "edit") : List.of("read");
            return options.stream().filter(value -> value.startsWith(prefix)).toList();
        }
        if (args.length == 3) {
            final String prefix = args[2].toLowerCase(Locale.ROOT);
            return List.of("main", "ender").stream().filter(value -> value.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
