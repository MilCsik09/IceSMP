package hu.taliann.icesmp.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * Sends the IceSMP resource pack through Paper's additive resource-pack API.
 *
 * <p>{@link Player#addResourcePack(UUID, String, byte[], Component, boolean)} is deliberately used
 * instead of the legacy setResourcePack API so IceSMP does not replace packs supplied by another
 * plugin or by the server configuration.</p>
 */
public final class ResourcePackListener implements Listener {

    private static final String CONFIG_ROOT = "resource-pack.";

    private final JavaPlugin plugin;

    public ResourcePackListener(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(final PlayerJoinEvent event) {
        send(event.getPlayer());
    }

    /** Sends the currently configured pack to a player, or does nothing when disabled/misconfigured. */
    public void send(final Player player) {
        final FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean(CONFIG_ROOT + "enabled", true)) {
            return;
        }

        final String url = config.getString(CONFIG_ROOT + "url", "").trim();
        final String sha1 = config.getString(CONFIG_ROOT + "sha1", "").trim().toLowerCase(Locale.ROOT);
        if (url.isEmpty() || sha1.isEmpty()) {
            plugin.getLogger().warning("A resource pack nincs kiküldve: hiányzik a resource-pack.url vagy resource-pack.sha1.");
            return;
        }

        final byte[] hash;
        try {
            hash = HexFormat.of().parseHex(sha1);
        } catch (final IllegalArgumentException exception) {
            plugin.getLogger().warning("Érvénytelen resource-pack.sha1 érték (40 hex karakter szükséges): " + sha1);
            return;
        }
        if (hash.length != 20) {
            plugin.getLogger().warning("Érvénytelen resource-pack.sha1 hossz (SHA-1 szükséges): " + sha1);
            return;
        }

        final String configuredId = config.getString(CONFIG_ROOT + "id", "").trim();
        final UUID packId;
        try {
            packId = configuredId.isEmpty()
                    ? UUID.nameUUIDFromBytes(("icesmp-resource-pack:" + url).getBytes(StandardCharsets.UTF_8))
                    : UUID.fromString(configuredId);
        } catch (final IllegalArgumentException exception) {
            plugin.getLogger().warning("Érvénytelen resource-pack.id UUID: " + configuredId);
            return;
        }

        final String rawPrompt = config.getString(CONFIG_ROOT + "prompt",
                "&bAz IceSMP egyedi modelljeihez és felületéhez szükséges resource pack.");
        final Component prompt = LegacyComponentSerializer.legacyAmpersand().deserialize(rawPrompt);
        final boolean required = config.getBoolean(CONFIG_ROOT + "required", true);

        player.addResourcePack(packId, url, hash, prompt, required);
    }
}
