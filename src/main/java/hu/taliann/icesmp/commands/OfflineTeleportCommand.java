package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.managers.ModerationManager;
import hu.taliann.icesmp.moderation.LastKnownLocation;
import hu.taliann.icesmp.moderation.PaperEntityTaskSubmission;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;

/** Folia-safe teleport to a durable last logout location; it never loads a world or chunk synchronously. */
public final class OfflineTeleportCommand implements BasicCommand {
    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final ModerationManager manager;
    private final MessageManager messages;
    private final String permission;

    public OfflineTeleportCommand(final org.bukkit.plugin.java.JavaPlugin plugin, final ModerationManager manager, final MessageManager messages,
                                  final String permission) {
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
        if (args.length < 1) {
            player.sendMessage(messages.get("moderation.offlinetp-usage", "&cHasználat: /offlinetp <játékos>"));
            return;
        }
        final ModerationCommandSupport.Target target = ModerationCommandSupport.resolveTarget(manager, args[0])
                .orElse(null);
        final LastKnownLocation saved = target == null ? null : manager.lastKnownLocation(target.id()).orElse(null);
        if (target == null || saved == null) {
            player.sendMessage(messages.get("moderation.offlinetp-missing", "&cNincs mentett kijelentkezési hely."));
            return;
        }
        World world = Bukkit.getWorld(saved.worldId());
        if (world == null) {
            final World byName = Bukkit.getWorld(saved.worldName());
            if (byName != null && byName.getUID().equals(saved.worldId())) {
                world = byName;
            }
        }
        if (world == null) {
            player.sendMessage(messages.get("moderation.offlinetp-world-missing",
                    "&cA mentett világ nincs betöltve vagy azonosítója megváltozott: &f%s", saved.worldName()));
            return;
        }
        final Location destination = new Location(world, saved.x(), saved.y(), saved.z(), saved.yaw(), saved.pitch());
        player.teleportAsync(destination).whenComplete((success, failure) ->
                PaperEntityTaskSubmission.run(plugin, player.getScheduler(), () -> player.sendMessage(
                        failure == null && Boolean.TRUE.equals(success)
                                ? messages.get("moderation.offlinetp-success", "&aTeleportálva: &f%s", target.name())
                                : messages.get("moderation.offlinetp-failed", "&cA teleportálás nem sikerült.")),
                        () -> { }));
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack source,
                                                final @NonNull String[] args) {
        return List.of();
    }
}
