package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.core.Permissions;
import hu.taliann.icesmp.managers.ModerationManager;
import hu.taliann.icesmp.moderation.ModerationSpamGuard;
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
import java.util.UUID;

/** Native /msg, /tell, /w and /reply route with exact SocialSpy result reporting. */
public final class PrivateMessageCommand implements BasicCommand {

    private final JavaPlugin plugin;
    private final ModerationManager manager;
    private final MessageManager messages;
    private final String channel;
    private final boolean reply;
    private final String permission;

    public PrivateMessageCommand(final JavaPlugin plugin, final ModerationManager manager,
                                 final MessageManager messages, final String channel,
                                 final boolean reply, final String permission) {
        this.plugin = plugin;
        this.manager = manager;
        this.messages = messages;
        this.channel = channel;
        this.reply = reply;
        this.permission = permission;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack source, final @NonNull String[] args) {
        final CommandSender rawSender = source.getSender();
        if (!(rawSender instanceof Player sender)) {
            rawSender.sendMessage(messages.get("moderation.player-only", "&cEzt csak játékos használhatja."));
            return;
        }
        if (!sender.hasPermission(permission)) {
            sender.sendMessage(messages.get("moderation.permission-denied", "&cNincs jogod ehhez a parancshoz."));
            return;
        }

        // Capture immutable identity on the sender's entity thread. Scheduler callbacks below never
        // inspect the sender entity from the recipient's region.
        final UUID senderId = sender.getUniqueId();
        final String senderName = sender.getName();
        final Player recipient;
        final String requestedRecipientName;
        final String rawMessage;
        if (reply) {
            if (args.length < 1) {
                sender.sendMessage(messages.get("moderation.reply-usage", "&cHasználat: /reply <üzenet>"));
                return;
            }
            final UUID targetId = manager.replyTarget(senderId).orElse(null);
            recipient = targetId == null ? null : Bukkit.getPlayer(targetId);
            requestedRecipientName = recipient == null ? "?" : recipient.getName();
            rawMessage = String.join(" ", args);
        } else {
            if (args.length < 2) {
                sender.sendMessage(messages.get("moderation.msg-usage", "&cHasználat: /%s <játékos> <üzenet>", channel));
                return;
            }
            requestedRecipientName = args[0];
            recipient = Bukkit.getPlayerExact(args[0]);
            rawMessage = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        }
        if (recipient == null || (!recipient.getUniqueId().equals(senderId) && !sender.canSee(recipient))) {
            sender.sendMessage(messages.get("moderation.message-target-offline", "&cA címzett nincs online."));
            notifySpy(senderId, senderName, null, requestedRecipientName, rawMessage, "TARGET_OFFLINE");
            return;
        }

        final UUID recipientId = recipient.getUniqueId();
        final String recipientName = recipient.getName();
        if (recipientId.equals(senderId)) {
            sender.sendMessage(messages.get("moderation.message-self", "&cMagadnak nem küldhetsz privát üzenetet."));
            return;
        }
        final ModerationManager.PrivateMessageDecision decision = ModerationSpamGuard.evaluate(senderId,
                () -> manager.evaluatePrivateMessage(senderId, rawMessage));
        if (decision.status() != ModerationManager.PrivateMessageStatus.DELIVERED) {
            sender.sendMessage(blockedMessage(decision));
            notifySpy(senderId, senderName, recipientId, recipientName, rawMessage, decision.status().name());
            manager.logChatEvent("PM_" + decision.status(), senderId, senderName, rawMessage);
            return;
        }

        // The recipient owns delivery. Only after that scheduler task actually runs do we acknowledge
        // success and establish /reply state; a retired target task cannot fake delivery.
        recipient.getScheduler().run(plugin, task -> {
            if (!recipient.isOnline()) {
                reportUndelivered(senderId, senderName, recipientId, recipientName,
                        decision.message(), "TARGET_OFFLINE");
                return;
            }
            recipient.sendMessage(messages.get("moderation.message-received", "&8[&d%s &7→ &dte&8] &f%s",
                    senderName, decision.message()));
            manager.setReplyPartners(senderId, recipientId);
            ModerationCommandSupport.send(plugin, senderId, messages.get("moderation.message-sent",
                    "&8[&dte &7→ &d%s&8] &f%s", recipientName, decision.message()));
            notifySpy(senderId, senderName, recipientId, recipientName, decision.message(), "DELIVERED");
            manager.logChatEvent("PM_DELIVERED", senderId, senderName,
                    recipientName + ": " + decision.message());
        }, () -> reportUndelivered(senderId, senderName, recipientId, recipientName,
                decision.message(), "TARGET_RETIRED"));
    }

    private void reportUndelivered(final UUID senderId, final String senderName,
                                   final UUID recipientId, final String recipientName,
                                   final String message, final String status) {
        ModerationCommandSupport.send(plugin, senderId, messages.get("moderation.message-target-offline",
                "&cA címzett kilépett az üzenet kézbesítése előtt."));
        notifySpy(senderId, senderName, recipientId, recipientName, message, status);
    }

    private String blockedMessage(final ModerationManager.PrivateMessageDecision decision) {
        return switch (decision.status()) {
            case BLOCKED_MUTED -> messages.get("moderation.mute-info", "&cNémítva vagy, ezért nem küldhetsz üzenetet.");
            case BLOCKED_SPAM -> messages.get("moderation.spam-blocked", "&cTúl gyorsan küldesz üzenetet.");
            case BLOCKED_FILTER -> messages.get("moderation.filter-blocked", "&cAz üzenet tiltott szót tartalmaz.");
            case DELIVERED -> "";
        };
    }

    private void notifySpy(final UUID senderId, final String senderName,
                           final UUID recipientId, final String recipientName,
                           final String message, final String status) {
        final String rendered = messages.get("moderation.socialspy-line",
                "&8[SPY:%s:%s] &7%s → %s: &f%s", channel, status, senderName, recipientName, message);
        final java.util.Set<UUID> recipients = manager.socialSpyRecipients();
        Bukkit.getGlobalRegionScheduler().run(plugin, globalTask -> {
            for (final UUID spyId : recipients) {
                if (spyId.equals(senderId) || spyId.equals(recipientId)) {
                    continue;
                }
                final Player spy = Bukkit.getPlayer(spyId);
                if (spy != null) {
                    spy.getScheduler().run(plugin, entityTask -> {
                        if (spy.isOnline() && spy.hasPermission(Permissions.MODERATION_SOCIALSPY)) {
                            spy.sendMessage(rendered);
                        }
                    }, null);
                }
            }
        });
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack source,
                                                final @NonNull String[] args) {
        if (reply || !(source.getSender() instanceof Player sender)
                || !sender.hasPermission(permission) || args.length > 1) {
            return List.of();
        }
        final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .filter(target -> target.getUniqueId().equals(sender.getUniqueId()) || sender.canSee(target))
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
