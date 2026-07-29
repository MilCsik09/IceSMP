package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.managers.ModerationManager;
import hu.taliann.icesmp.moderation.PaperEntityTaskSubmission;
import hu.taliann.icesmp.moderation.PunishmentRecord;
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

/** Issues warn/kick/mute/ban actions through the one authoritative moderation service. */
public final class ModerationActionCommand implements BasicCommand {

    private final JavaPlugin plugin;
    private final ModerationManager manager;
    private final MessageManager messages;
    private final PunishmentType configuredType;
    private final String permission;
    private final String label;

    public ModerationActionCommand(final JavaPlugin plugin, final ModerationManager manager,
                                   final MessageManager messages, final PunishmentType type,
                                   final String permission, final String label) {
        this.plugin = plugin;
        this.manager = manager;
        this.messages = messages;
        this.configuredType = type;
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
        if (args.length < minimumArguments()) {
            sender.sendMessage(messages.get("moderation.action-usage", "&cHasználat: %s", usage()));
            return;
        }
        final ModerationCommandSupport.Target target = ModerationCommandSupport.resolveTarget(manager, args[0])
                .orElse(null);
        if (target == null) {
            sender.sendMessage(messages.get("moderation.player-unknown",
                    "&cIsmeretlen játékos: &f%s", args[0]));
            return;
        }
        if (configuredType == PunishmentType.KICK && target.online() == null) {
            sender.sendMessage(messages.get("moderation.player-offline", "&cA játékos nincs online: &f%s", target.name()));
            return;
        }

        final ParsedAction parsed = parse(args, target.id());
        if (parsed == null) {
            sender.sendMessage(messages.get("moderation.duration-invalid",
                    "&cÉrvénytelen időtartam. Használj például: 30m, 2h, 7d."));
            return;
        }
        manager.issueAsync(parsed.type(), target.id(), target.name(),
                ModerationCommandSupport.administratorId(sender), sender.getName(), parsed.reason(),
                parsed.durationMillis(), result -> complete(sender, target, parsed, result));
    }

    private ParsedAction parse(final String[] args, final java.util.UUID targetId) {
        PunishmentType type = configuredType;
        Long duration = null;
        int reasonStart = 1;
        if (configuredType == PunishmentType.TEMPORARY_BAN) {
            duration = ModerationCommandSupport.parseDurationMillis(args[1]);
            if (duration == null || duration == 0L) {
                return null;
            }
            reasonStart = 2;
        } else if (configuredType == PunishmentType.MUTE) {
            if (args.length >= 2) {
                final Long parsed = ModerationCommandSupport.parseDurationMillis(args[1]);
                if (parsed != null) {
                    reasonStart = 2;
                    if (parsed == 0L) {
                        type = PunishmentType.MUTE;
                    } else {
                        type = PunishmentType.TEMPORARY_MUTE;
                        duration = parsed;
                    }
                } else if (ModerationCommandSupport.looksLikeDurationToken(args[1])) {
                    return null;
                } else {
                    final long escalation = Math.multiplyExact(manager.escalationMinutes(targetId), 60_000L);
                    type = PunishmentType.TEMPORARY_MUTE;
                    duration = escalation;
                }
            } else {
                final long escalation = Math.multiplyExact(manager.escalationMinutes(targetId), 60_000L);
                type = PunishmentType.TEMPORARY_MUTE;
                duration = escalation;
            }
        }
        final String reason = args.length > reasonStart
                ? String.join(" ", Arrays.copyOfRange(args, reasonStart, args.length))
                : messages.get("moderation.default-reason", "Nincs megadva");
        return new ParsedAction(type, duration, reason);
    }

    private void complete(final CommandSender sender, final ModerationCommandSupport.Target target,
                          final ParsedAction action,
                          final ModerationManager.OperationResult<PunishmentRecord> result) {
        if (!result.successful()) {
            ModerationCommandSupport.send(plugin, sender, messages.get("moderation.action-failed",
                    "&cA büntetés nem menthető: &f%s", safeFailure(result.failure())));
            return;
        }
        final PunishmentRecord record = result.value();
        ModerationCommandSupport.send(plugin, sender, messages.get("moderation.action-success",
                "&a%s rögzítve: &f%s &7(%s)&a. Azonosító: &f%s",
                typeName(record.type()), target.name(),
                ModerationCommandSupport.durationText(action.durationMillis()), record.id()));

        if (record.type() == PunishmentType.KICK) {
            // A kick a parancs idején létező sessionre vonatkozik. Egy közben újracsatlakozott
            // sessiont nem szabad a régi kick tranzakcióval kirúgni.
            final Player originalSession = target.online();
            if (originalSession != null) {
                PaperEntityTaskSubmission.run(plugin, originalSession.getScheduler(), () -> {
                    if (originalSession.isOnline()) {
                        kick(originalSession, record);
                    }
                }, () -> { });
            }
            return;
        }

        // A durable save async útja alatt a korábban feloldott Player objektum kiléphet és egy új
        // session érkezhet. Ban/mute/warning mellékhatást ezért UUID alapján, a commit UTÁN oldunk fel.
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            final Player current = Bukkit.getPlayer(target.id());
            if (current == null) {
                return;
            }
            PaperEntityTaskSubmission.run(plugin, current.getScheduler(),
                    () -> applyCurrentSessionEffect(current, action, record), () -> { });
        });
    }

    private void applyCurrentSessionEffect(final Player current, final ParsedAction action,
                                           final PunishmentRecord record) {
        if (!current.isOnline()) {
            return;
        }
        if (record.type().family() == PunishmentType.Family.BAN) {
            final boolean stillActive = manager.activeBan(current.getUniqueId())
                    .map(active -> active.id().equals(record.id()))
                    .orElse(false);
            if (stillActive) {
                kick(current, record);
            }
        } else if (record.type().family() == PunishmentType.Family.MUTE) {
            final boolean stillActive = manager.activeMute(current.getUniqueId())
                    .map(active -> active.id().equals(record.id()))
                    .orElse(false);
            if (stillActive) {
                current.sendMessage(messages.get("moderation.mute-notify",
                        "&cNémítva lettél. &7Időtartam: &f%s&7, ok: &f%s",
                        ModerationCommandSupport.durationText(action.durationMillis()), record.reason()));
            }
        } else if (record.type() == PunishmentType.WARNING) {
            current.sendMessage(messages.get("moderation.warning-notify",
                    "&eFigyelmeztetést kaptál. &7Ok: &f%s", record.reason()));
        }
    }

    private void kick(final Player player, final PunishmentRecord record) {
        player.kick(messages.getComponent("moderation.disconnect",
                "&c%s\n&7Ok: &f%s", typeName(record.type()), record.reason()));
    }

    private int minimumArguments() {
        return configuredType == PunishmentType.TEMPORARY_BAN ? 2 : 1;
    }

    private String usage() {
        return switch (configuredType) {
            case WARNING -> "/warn <játékos> [ok]";
            case KICK -> "/kick <játékos> [ok]";
            case MUTE -> "/mute <játékos> [30m|2h|7d|végleges] [ok]";
            case BAN -> "/ban <játékos> [ok]";
            case TEMPORARY_BAN -> "/tempban <játékos> <30m|2h|7d> [ok]";
            default -> "/" + label;
        };
    }

    private static String typeName(final PunishmentType type) {
        return switch (type) {
            case WARNING -> "Figyelmeztetés";
            case KICK -> "Kirúgás";
            case MUTE -> "Végleges némítás";
            case TEMPORARY_MUTE -> "Ideiglenes némítás";
            case BAN -> "Végleges kitiltás";
            case TEMPORARY_BAN -> "Ideiglenes kitiltás";
            case UNMUTE -> "Némítás feloldása";
            case UNBAN -> "Kitiltás feloldása";
        };
    }

    private static String safeFailure(final Throwable failure) {
        if (failure == null || failure.getMessage() == null || failure.getMessage().isBlank()) {
            return failure == null ? "ismeretlen hiba" : failure.getClass().getSimpleName();
        }
        return failure.getMessage();
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack source,
                                                final @NonNull String[] args) {
        if (!source.getSender().hasPermission(permission)) {
            return List.of();
        }
        if (args.length <= 1) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return ModerationCommandSupport.visibleOnlineNames(source.getSender(), prefix);
        }
        if ((configuredType == PunishmentType.MUTE || configuredType == PunishmentType.TEMPORARY_BAN)
                && args.length == 2) {
            final String prefix = args[1].toLowerCase(Locale.ROOT);
            return List.of("30m", "2h", "7d", "végleges").stream()
                    .filter(value -> value.startsWith(prefix)).toList();
        }
        return List.of();
    }

    private record ParsedAction(PunishmentType type, Long durationMillis, String reason) {
    }
}
