package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.managers.ModerationManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;

/** Durable per-admin SocialSpy toggle. */
public final class SocialSpyCommand implements BasicCommand {
    private final JavaPlugin plugin;
    private final ModerationManager manager;
    private final MessageManager messages;
    private final String permission;

    public SocialSpyCommand(final JavaPlugin plugin, final ModerationManager manager,
                            final MessageManager messages, final String permission) {
        this.plugin = plugin;
        this.manager = manager;
        this.messages = messages;
        this.permission = permission;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack source, final @NonNull String[] args) {
        final CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("moderation.player-only", "&cEzt csak játékos használhatja."));
            return;
        }
        if (!player.hasPermission(permission)) {
            player.sendMessage(messages.get("moderation.permission-denied", "&cNincs jogod ehhez a parancshoz."));
            return;
        }
        final boolean enable = !manager.isSocialSpyEnabled(player.getUniqueId());
        manager.setSocialSpyAsync(player.getUniqueId(), player.getName(), enable, result -> {
            if (!result.successful()) {
                ModerationCommandSupport.send(plugin, player, messages.get("moderation.setting-save-failed",
                        "&cA beállítás nem menthető."));
                return;
            }
            ModerationCommandSupport.send(plugin, player, messages.get("moderation.socialspy-state",
                    "&aSocialSpy: &f%s", enable ? "bekapcsolva" : "kikapcsolva"));
        });
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack source,
                                                final @NonNull String[] args) {
        return List.of();
    }
}
