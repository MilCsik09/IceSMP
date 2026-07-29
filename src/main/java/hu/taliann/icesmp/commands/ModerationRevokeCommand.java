package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.managers.ModerationManager;
import hu.taliann.icesmp.moderation.PaperEntityTaskSubmission;
import hu.taliann.icesmp.moderation.PunishmentLedger;
import hu.taliann.icesmp.moderation.PunishmentType;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** Unmutes or unbans using the same ledger and durable revocation audit. */
public final class ModerationRevokeCommand implements BasicCommand {

    private final JavaPlugin plugin;
    private final ModerationManager manager;
    private final MessageManager messages;
    private final PunishmentType.Family family;
    private final String permission;
    private final String label;

    public ModerationRevokeCommand(final JavaPlugin plugin, final ModerationManager manager,
                                   final MessageManager messages, final PunishmentType.Family family,
                                   final String permission, final String label) {
        this.plugin = plugin;
        this.manager = manager;
        this.messages = messages;
        this.family = family;
        this.permission = permission;
        this.label = label;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack source, final @NonNull String[] args) {
        final CommandSender sender = source.getSender();
        if (!sender.hasPermission(permission)) {
            sender.sendMessage(messages.get("moderation.permission-denied", "&cNincs jogod ehhez a parancshoz."));
            return;
        }
        if (args.length < 1) {
            sender.sendMessage(messages.get("moderation.revoke-usage", "&cHasználat: /%s <játékos> [ok]", label));
            return;
        }
        final ModerationCommandSupport.Target target = ModerationCommandSupport.resolveTarget(manager, args[0])
                .orElse(null);
        if (target == null) {
            sender.sendMessage(messages.get("moderation.player-unknown", "&cIsmeretlen játékos: &f%s", args[0]));
            return;
        }
        final String reason = args.length > 1
                ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : messages.get("moderation.default-revoke-reason", "Adminisztrátori feloldás");
        manager.revokeAsync(family, target.id(), target.name(),
                ModerationCommandSupport.administratorId(sender), sender.getName(), reason,
                result -> complete(sender, target, result));
    }

    private void complete(final CommandSender sender, final ModerationCommandSupport.Target target,
                          final ModerationManager.OperationResult<PunishmentLedger.RevocationResult> result) {
        if (!result.successful()) {
            ModerationCommandSupport.send(plugin, sender, messages.get("moderation.revoke-failed",
                    "&cNincs feloldható aktív büntetés: &f%s", target.name()));
            return;
        }
        ModerationCommandSupport.send(plugin, sender, messages.get("moderation.revoke-success",
                "&a%s feloldva: &f%s&a. Műveletazonosító: &f%s",
                family == PunishmentType.Family.MUTE ? "Némítás" : "Kitiltás",
                target.name(), result.value().action().id()));
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            final Player current = Bukkit.getPlayer(target.id());
            if (current != null) {
                PaperEntityTaskSubmission.run(plugin, current.getScheduler(), () -> current.sendMessage(messages.get(
                        "moderation.revoke-notify", "&aA(z) %s büntetésed feloldásra került.",
                        family == PunishmentType.Family.MUTE ? "némítás" : "kitiltás")), () -> { });
            }
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
