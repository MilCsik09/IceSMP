package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.managers.ModerationManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** {@code /unmute <név>} — némítás feloldása (online és offline célra is). */
public final class UnmuteCommand implements BasicCommand {

    private static final String PERMISSION = hu.taliann.icesmp.core.Permissions.MODERATION;

    private final JavaPlugin plugin;
    private final ModerationManager moderationManager;
    private final MessageManager messageManager;

    public UnmuteCommand(final JavaPlugin plugin, final ModerationManager moderationManager,
                          final MessageManager messageManager) {
        this.plugin = plugin;
        this.moderationManager = moderationManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(messageManager.get("moderation.permission-denied", "&cNincs jogod ehhez a parancshoz."));
            return;
        }

        if (args.length < 1) {
            sender.sendMessage(messageManager.get("moderation.unmute-usage", "&cHasználat: /unmute <név>"));
            return;
        }

        // Never Bukkit.getOfflinePlayer(name): unknown-name lookup fires a blocking Mojang call.
        OfflinePlayer target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            target = Bukkit.getOfflinePlayerIfCached(args[0]);
        }
        if (target == null) {
            sender.sendMessage(messageManager.get("moderation.player-unknown",
                    "&cIsmeretlen játékos (még sosem járt a szerveren): &f%s", args[0]));
            return;
        }

        if (!moderationManager.isMuted(target.getUniqueId())) {
            sender.sendMessage(messageManager.get("moderation.unmute-not-muted", "&7%s nincs némítva.", target.getName()));
            return;
        }

        moderationManager.unmute(target.getUniqueId());
        sender.sendMessage(messageManager.get("moderation.unmute-success", "&aNémítás feloldva: &f%s", target.getName()));

        if (target.getPlayer() instanceof Player onlineTarget) {
            onlineTarget.getScheduler().run(plugin, task -> onlineTarget.sendMessage(
                    messageManager.get("moderation.unmute-notify", "&aA némításod feloldásra került.")), null);
        }
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
