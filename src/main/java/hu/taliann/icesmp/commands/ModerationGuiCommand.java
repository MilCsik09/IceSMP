package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.gui.ModerationGUI;
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

/** Opens the native moderation GUI; all action buttons delegate back to the command service. */
public final class ModerationGuiCommand implements BasicCommand {
    private final MessageManager messages;
    private final String permission;

    public ModerationGuiCommand(final MessageManager messages, final String permission) {
        this.messages = messages;
        this.permission = permission;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack source, final @NonNull String[] args) {
        final CommandSender sender = source.getSender();
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(messages.get("moderation.player-only", "&cEzt csak játékos használhatja."));
            return;
        }
        if (!viewer.hasPermission(permission)) {
            viewer.sendMessage(messages.get("moderation.permission-denied", "&cNincs jogod ehhez a GUI-hoz."));
            return;
        }
        if (args.length >= 1) {
            final Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null || (!target.getUniqueId().equals(viewer.getUniqueId()) && !viewer.canSee(target))) {
                viewer.sendMessage(messages.get("moderation.player-offline", "&cA játékos nincs online: &f%s", args[0]));
                return;
            }
            ModerationGUI.openPlayer(viewer, target, messages);
        } else {
            ModerationGUI.openPlayers(viewer, messages);
        }
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack source,
                                                final @NonNull String[] args) {
        if (!(source.getSender() instanceof Player viewer)
                || !viewer.hasPermission(permission) || args.length > 1) {
            return List.of();
        }
        final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .filter(target -> target.getUniqueId().equals(viewer.getUniqueId()) || viewer.canSee(target))
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
