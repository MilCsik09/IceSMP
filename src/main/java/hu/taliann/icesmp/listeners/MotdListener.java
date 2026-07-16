package hu.taliann.icesmp.listeners;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import hu.taliann.icesmp.managers.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.List;

/**
 * Natív szerverlista-MOTD (a MiniMOTD plugin kiváltása): MiniMessage-formázott,
 * IDŐALAPON rotálódó variánsok ({@code motd.rotation-seconds}) + {online}/{max}
 * tokenek + opcionális max-player felülírás. A ping-event async szálon fut —
 * a handler csak a volatile config-fát és a rendszeridőt olvassa, entitást nem
 * érint, így szálbiztos.
 */
public final class MotdListener implements Listener {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final ConfigManager configManager;

    public MotdListener(final ConfigManager configManager) {
        this.configManager = configManager;
    }

    @EventHandler
    public void onPing(final PaperServerListPingEvent event) {
        if (!configManager.getBoolean("motd.enabled", true)) {
            return;
        }
        final ConfigurationSection variants = configManager.getConfiguration() == null ? null
                : configManager.getConfiguration().getConfigurationSection("motd.variants");
        if (variants != null) {
            final List<String> keys = List.copyOf(variants.getKeys(false));
            if (!keys.isEmpty()) {
                final long rotationMillis = Math.max(2L, configManager.getLong("motd.rotation-seconds", 10L)) * 1000L;
                final ConfigurationSection variant = variants.getConfigurationSection(
                        keys.get((int) ((System.currentTimeMillis() / rotationMillis) % keys.size())));
                if (variant != null) {
                    event.motd(render(variant.getString("line1", ""))
                            .append(Component.newline())
                            .append(render(variant.getString("line2", ""))));
                }
            }
        }
        final int maxOverride = configManager.getInt("motd.max-players-override", -1);
        if (maxOverride > 0) {
            event.setMaxPlayers(maxOverride);
        }
    }

    private Component render(final String line) {
        return MINI.deserialize(line
                .replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("{max}", String.valueOf(Bukkit.getMaxPlayers())));
    }
}
