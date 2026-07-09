package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.integration.LuckPermsBridge;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.HudManager;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.UUID;

/**
 * Native chat formatter (replaces the external LuckPermsChatFormatterFolia
 * plugin): renders chat as {@code [LP-prefix] Name [LP-suffix]: message} with the
 * name coloured by the speaker's faction — a decoration no generic formatter
 * plugin can add. The LuckPerms prefix/suffix come through the reflective
 * {@link LuckPermsBridge} (empty without LP), the faction colour from the
 * thread-safe {@link HudManager.HudSnapshot} — important, because
 * {@link AsyncChatEvent} runs OFF the region threads, where live player/PDC
 * reads are not allowed.
 */
public final class ChatFormatListener implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final ConfigManager configManager;
    private final HudManager hudManager;

    public ChatFormatListener(final ConfigManager configManager, final HudManager hudManager) {
        this.configManager = configManager;
        this.hudManager = hudManager;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onChat(final AsyncChatEvent event) {
        if (!configManager.getBoolean("chat.format-enabled", true)) {
            return;
        }
        final UUID speakerId = event.getPlayer().getUniqueId();
        // LP prefixes conventionally use '&', but some setups store '§' — normalise so
        // the legacy-ampersand parser renders both instead of leaking raw § glyphs.
        final String rawPrefix = LuckPermsBridge.prefix(speakerId).replace('\u00a7', '&');
        final String rawSuffix = LuckPermsBridge.suffix(speakerId).replace('\u00a7', '&');
        final NamedTextColor nameColor = configManager.getBoolean("chat.name-faction-color", true)
                ? factionColor(speakerId) : NamedTextColor.WHITE;

        event.renderer(ChatRenderer.viewerUnaware((final Player source, final Component sourceDisplayName, final Component message) ->
                Component.empty()
                        .append(rawPrefix.isEmpty() ? Component.empty() : LEGACY.deserialize(rawPrefix))
                        .append(Component.text(source.getName(), nameColor))
                        .append(rawSuffix.isEmpty() ? Component.empty() : LEGACY.deserialize(rawSuffix))
                        .append(Component.text(": ", NamedTextColor.GRAY))
                        .append(message.colorIfAbsent(NamedTextColor.WHITE))));
    }

    /** The speaker's faction colour from the thread-safe HUD snapshot (async-safe). */
    private NamedTextColor factionColor(final UUID playerId) {
        final HudManager.HudSnapshot snapshot = hudManager.snapshot(playerId);
        if (snapshot == null) {
            return NamedTextColor.WHITE;
        }
        return switch (snapshot.factionId()) {
            case "RED" -> NamedTextColor.RED;
            case "BLUE" -> NamedTextColor.BLUE;
            case "NEUTRAL" -> NamedTextColor.GRAY;
            case "DARK" -> NamedTextColor.DARK_GRAY;
            default -> NamedTextColor.WHITE;
        };
    }
}
