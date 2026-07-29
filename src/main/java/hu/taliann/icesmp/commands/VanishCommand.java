package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.managers.ModerationManager;
import hu.taliann.icesmp.managers.VanishManager;
import hu.taliann.icesmp.moderation.PaperEntityTaskSubmission;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** Persistent vanish toggle for self or an online target. */
public final class VanishCommand implements BasicCommand {
    private final JavaPlugin plugin;
    private final ModerationManager moderationManager;
    private final VanishManager vanishManager;
    private final MessageManager messages;
    private final String permission;

    public VanishCommand(final JavaPlugin plugin, final ModerationManager moderationManager,
                         final VanishManager vanishManager, final MessageManager messages,
                         final String permission) {
        this.plugin = plugin;
        this.moderationManager = moderationManager;
        this.vanishManager = vanishManager;
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
        final Player target;
        if (args.length >= 1) {
            target = Bukkit.getPlayerExact(args[0]);
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(messages.get("moderation.vanish-usage", "&cHasználat: /vanish <online játékos>"));
            return;
        }
        if (target == null) {
            sender.sendMessage(messages.get("moderation.player-offline", "&cA játékos nincs online: &f%s", args[0]));
            return;
        }
        final java.util.UUID targetId = target.getUniqueId();
        final String targetName = target.getName();
        final boolean enabled = !moderationManager.isVanished(targetId);
        moderationManager.setVanishedAsync(targetId, targetName, enabled, result -> {
            if (!result.successful()) {
                ModerationCommandSupport.send(plugin, sender, messages.get("moderation.setting-save-failed",
                        "&cA vanish állapot nem menthető."));
                return;
            }
            // The persistence callback runs on the async scheduler. Resolve the current session and
            // start all visibility/entity work from the global scheduler instead of carrying the
            // pre-save Player instance across a disconnect/reconnect race.
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                vanishManager.refreshSubject(targetId);
                final Player current = Bukkit.getPlayer(targetId);
                if (current != null) {
                    vanishManager.refreshViewer(current);
                    PaperEntityTaskSubmission.run(plugin, current.getScheduler(), () -> current.sendMessage(messages.get(
                            "moderation.vanish-self", "&7Vanish: &f%s",
                            enabled ? "bekapcsolva" : "kikapcsolva")), () -> { });
                }
            });
            ModerationCommandSupport.send(plugin, sender, messages.get("moderation.vanish-state",
                    "&aVanish állapot mentve: &f%s &7— &f%s", targetName,
                    enabled ? "bekapcsolva" : "kikapcsolva"));
        });
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack source,
                                                final @NonNull String[] args) {
        if (!source.getSender().hasPermission(permission) || args.length > 1) {
            return List.of();
        }
        final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        return ModerationCommandSupport.visibleOnlineNames(source.getSender(), prefix);
    }
}
