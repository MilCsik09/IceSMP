package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.ModerationManager;
import hu.taliann.icesmp.moderation.ModerationSpamGuard;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.List;
import java.util.Locale;

/**
 * Natív moderáció (a SModeration plugin chat-részének kiváltása): némítás + chat-szűrő
 * + spam-fék, egyetlen listenerben.
 *
 * <p>{@link AsyncChatEvent} a Paper ASYNC chat-szálán fut, NEM a régió-szálakon — itt kizárólag a
 * {@link ModerationManager} szálbiztos állapotának olvasása/cancel/a küldőnek szóló üzenet
 * megengedett, MÁSIK entitás bármilyen érintése (PDC/inventory, sendMessage) tilos
 * (ld. CLAUDE.md Folia-szabályok). {@code sender.sendMessage(...)} a saját küldőre irányul,
 * ez biztonságos.</p>
 *
 * <p>{@link EventPriority#LOWEST}-en fut — korábban, mint a chat-formázó {@link ChatFormatListener}
 * ({@code LOW}) — így a formázó már a némítás/spam által esetleg cancel-elt, illetve a
 * chat-szűrő által csillagozott üzenetet látja. A cenzúrázás csak a Component PLAIN-TEXT
 * tartalmát cseréli (a formázást elveszíti), de ez nem gond: a {@code ChatFormatListener} a saját
 * {@code ChatRenderer}-ében amúgy is újraépíti a teljes Component-et (LP prefix/suffix + szín +
 * a {@code message} paraméterként kapott — itt már szűrt — szöveg).</p>
 */
public final class ChatModerationListener implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final List<String> DEFAULT_BLOCKED_COMMANDS = List.of("msg", "w", "tell", "me", "r");

    private final ConfigManager configManager;
    private final ModerationManager moderationManager;
    private final MessageManager messageManager;

    public ChatModerationListener(final ConfigManager configManager, final ModerationManager moderationManager,
                                  final MessageManager messageManager) {
        this.configManager = configManager;
        this.moderationManager = moderationManager;
        this.messageManager = messageManager;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onChat(final AsyncChatEvent event) {
        if (!configManager.getBoolean("moderation.enabled", true)) {
            return;
        }
        final Player sender = event.getPlayer();
        final String plain = PLAIN.serialize(event.message());

        final ModerationManager.MuteEntry mute = moderationManager.muteInfo(sender.getUniqueId());
        if (mute != null) {
            event.setCancelled(true);
            sender.sendMessage(muteMessage(mute));
            moderationManager.logChatEvent("MUTED", sender, plain);
            return;
        }

        if (ModerationSpamGuard.evaluate(moderationManager,
                () -> moderationManager.isSpam(sender.getUniqueId(), plain))) {
            event.setCancelled(true);
            sender.sendMessage(messageManager.get("moderation.spam-blocked",
                    "&cTúl gyorsan/ismételten küldesz üzenetet — várj egy kicsit."));
            moderationManager.logChatEvent("SPAM", sender, plain);
            return;
        }

        final String filtered = moderationManager.filter(plain);
        if (filtered == null) {
            event.setCancelled(true);
            sender.sendMessage(messageManager.get("moderation.filter-blocked",
                    "&cAz üzeneted tiltott szót tartalmazott, ezért nem lett elküldve."));
            moderationManager.logChatEvent("BLOCK", sender, plain);
            return;
        }
        if (!filtered.equals(plain)) {
            event.message(Component.text(filtered));
            moderationManager.logChatEvent("CENSOR", sender, plain);
        }
    }

    /** A némított játékos ne kerülhesse meg a chatet privát-üzenet-jellegű parancsokkal. */
    @EventHandler(ignoreCancelled = true)
    public void onCommandPreprocess(final PlayerCommandPreprocessEvent event) {
        if (!configManager.getBoolean("moderation.enabled", true)) {
            return;
        }
        final Player player = event.getPlayer();
        final ModerationManager.MuteEntry mute = moderationManager.muteInfo(player.getUniqueId());
        if (mute == null) {
            return;
        }

        final String label = commandLabel(event.getMessage());
        final List<String> configured = configManager.getStringList("moderation.muted-blocked-commands");
        final List<String> blocked = configured.isEmpty() ? DEFAULT_BLOCKED_COMMANDS : configured;

        for (final String blockedLabel : blocked) {
            if (blockedLabel.equalsIgnoreCase(label)) {
                event.setCancelled(true);
                player.sendMessage(muteMessage(mute));
                return;
            }
        }
    }

    /** A "/plugin:cmd args..." alakból is a puszta parancs-labelt adja vissza, '/' és névtér nélkül. */
    private static String commandLabel(final String rawMessage) {
        final int spaceIndex = rawMessage.indexOf(' ');
        final String withoutArgs = (spaceIndex < 0 ? rawMessage : rawMessage.substring(0, spaceIndex)).substring(1);
        final int colonIndex = withoutArgs.indexOf(':');
        return (colonIndex < 0 ? withoutArgs : withoutArgs.substring(colonIndex + 1)).toLowerCase(Locale.ROOT);
    }

    private String muteMessage(final ModerationManager.MuteEntry mute) {
        final String remaining = mute.isPermanent() ? "véglegesen" : ModerationManager.formatRemaining(mute.untilMillis());
        final String reason = mute.reason().isBlank() ? "nincs megadva" : mute.reason();
        return messageManager.get("moderation.mute-info",
                "&cNémítva vagy. &7Hátralévő idő: &f%s&7, ok: &f%s", remaining, reason);
    }
}
