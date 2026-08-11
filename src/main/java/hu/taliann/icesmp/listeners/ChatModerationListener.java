package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.gui.ConfigChatInputGate;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.ModerationManager;
import hu.taliann.icesmp.moderation.ModerationSpamGuard;
import hu.taliann.icesmp.moderation.PaperEntityTaskSubmission;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Natív moderáció (a SModeration plugin chat-részének kiváltása): némítás + chat-szűrő
 * + spam-fék, egyetlen listenerben.
 *
 * <p>{@link AsyncChatEvent} a Paper ASYNC chat-szálán fut, NEM a régió-szálakon — itt kizárólag a
 * {@link ModerationManager} szálbiztos állapotának olvasása és az event cancel/message mutációja
 * megengedett. Élő Bukkit-entitásra irányuló üzenetküldés és naplózási adatkinyerés viszont
 * kizárólag a küldő entity schedulerén történik; az async úton csak az immutable UUID/név snapshot
 * halad tovább.</p>
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

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final ModerationManager moderationManager;
    private final MessageManager messageManager;

    public ChatModerationListener(final JavaPlugin plugin, final ConfigManager configManager,
                                  final ModerationManager moderationManager,
                                  final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.moderationManager = moderationManager;
        this.messageManager = messageManager;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onChat(final AsyncChatEvent event) {
        final Player sender = event.getPlayer();
        final UUID senderId = sender.getUniqueId();
        // A config-editor chatje privát admin-input. Nem szabad cenzúrázni, spamként
        // blokkolni vagy naplózni — különben éppen a tiltott szavak listája nem lenne
        // szerkeszthető. A ConfigMenuGUIListener ugyanebben a LOWEST fázisban elnyeli.
        if (ConfigChatInputGate.isOpen(senderId)) {
            return;
        }
        if (!configManager.getBoolean("moderation.enabled", true)) {
            return;
        }
        final String senderName = sender.getName();
        final String plain = PLAIN.serialize(event.message());

        final ModerationManager.MuteEntry mute = moderationManager.muteInfo(senderId);
        if (mute != null) {
            event.setCancelled(true);
            notifySender(sender, muteMessage(mute));
            moderationManager.logChatEvent("MUTED", senderId, senderName, plain);
            return;
        }

        if (ModerationSpamGuard.evaluate(senderId,
                () -> moderationManager.isSpam(senderId, plain))) {
            event.setCancelled(true);
            notifySender(sender, messageManager.get("moderation.spam-blocked",
                    "&cTúl gyorsan/ismételten küldesz üzenetet — várj egy kicsit."));
            moderationManager.logChatEvent("SPAM", senderId, senderName, plain);
            return;
        }

        final String filtered = moderationManager.filter(plain);
        if (filtered == null) {
            event.setCancelled(true);
            notifySender(sender, messageManager.get("moderation.filter-blocked",
                    "&cAz üzeneted tiltott szót tartalmazott, ezért nem lett elküldve."));
            moderationManager.logChatEvent("BLOCK", senderId, senderName, plain);
            return;
        }
        if (!filtered.equals(plain)) {
            event.message(Component.text(filtered));
            moderationManager.logChatEvent("CENSOR", senderId, senderName, plain);
        }
    }

    private void notifySender(final Player sender, final String message) {
        PaperEntityTaskSubmission.run(plugin, sender.getScheduler(), () -> {
            if (sender.isOnline()) {
                sender.sendMessage(message);
            }
        }, () -> { });
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
