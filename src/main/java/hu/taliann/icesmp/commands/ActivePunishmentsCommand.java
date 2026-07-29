package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.managers.ModerationManager;
import hu.taliann.icesmp.moderation.PunishmentRecord;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;

/** Lists active restrictions globally or for one known player. */
public final class ActivePunishmentsCommand implements BasicCommand {

    private final ModerationManager manager;
    private final MessageManager messages;
    private final String permission;

    public ActivePunishmentsCommand(final ModerationManager manager, final MessageManager messages,
                                    final String permission) {
        this.manager = manager;
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
        final List<PunishmentRecord> records;
        if (args.length >= 1) {
            final ModerationCommandSupport.Target target = ModerationCommandSupport.resolveTarget(manager, args[0])
                    .orElse(null);
            if (target == null) {
                sender.sendMessage(messages.get("moderation.player-unknown", "&cIsmeretlen játékos: &f%s", args[0]));
                return;
            }
            records = manager.history(target.id()).stream().filter(record -> record.isLogicallyActive(System.currentTimeMillis())).toList();
        } else {
            records = manager.activePunishments();
        }
        if (records.isEmpty()) {
            sender.sendMessage(messages.get("moderation.active-empty", "&7Nincs aktív büntetés."));
            return;
        }
        sender.sendMessage(messages.get("moderation.active-header", "&6Aktív büntetések: &f%d", records.size()));
        for (final PunishmentRecord record : records) {
            final String remaining = record.expiresAtMillis() == null ? "végleges"
                    : ModerationManager.formatRemaining(record.expiresAtMillis());
            sender.sendMessage(messages.get("moderation.active-line", "&7- &f%s &8| &f%s &8| &7%s &8| &f%s",
                    record.targetName(), record.type().name(), remaining, record.reason()));
        }
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack source,
                                                final @NonNull String[] args) {
        if (!source.getSender().hasPermission(permission) || args.length > 1) {
            return List.of();
        }
        final String prefix = args.length == 0 ? "" : args[0].toLowerCase(java.util.Locale.ROOT);
        return ModerationCommandSupport.visibleOnlineNames(source.getSender(), prefix);
    }
}
