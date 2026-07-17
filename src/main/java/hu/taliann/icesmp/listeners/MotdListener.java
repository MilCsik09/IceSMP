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
    private final hu.taliann.icesmp.managers.BloodMoonManager bloodMoonManager;
    private final hu.taliann.icesmp.managers.WorldBossManager worldBossManager;
    private final hu.taliann.icesmp.managers.SeasonManager seasonManager;

    /**
     * IDEAS A59: a data-mappa {@code icons/*.png} fájljaiból betöltött szerverlista-ikonok
     * (64×64). Az IO/dekódolás egyszer, a konstruktorban (enable, fő szál) fut; a ping-event
     * async szálán már csak a cache-elt referencia állítódik.
     */
    private final java.util.Map<String, org.bukkit.util.CachedServerIcon> icons = new java.util.HashMap<>();

    public MotdListener(final org.bukkit.plugin.java.JavaPlugin plugin, final ConfigManager configManager,
                        final hu.taliann.icesmp.managers.BloodMoonManager bloodMoonManager,
                        final hu.taliann.icesmp.managers.WorldBossManager worldBossManager,
                        final hu.taliann.icesmp.managers.SeasonManager seasonManager) {
        this.configManager = configManager;
        this.bloodMoonManager = bloodMoonManager;
        this.worldBossManager = worldBossManager;
        this.seasonManager = seasonManager;
        final java.io.File iconDir = new java.io.File(plugin.getDataFolder(), "icons");
        final java.io.File[] files = iconDir.listFiles((dir, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".png"));
        if (files != null) {
            for (final java.io.File file : files) {
                try {
                    icons.put(file.getName().substring(0, file.getName().length() - 4),
                            Bukkit.loadServerIcon(file));
                } catch (final Exception exception) {
                    plugin.getLogger().warning("Szerverlista-ikon nem tölthető be (" + file.getName() + "): "
                            + exception.getMessage());
                }
            }
            if (!icons.isEmpty()) {
                plugin.getLogger().info("Szerverlista-ikonok betöltve: " + icons.keySet());
            }
        }
    }

    @EventHandler
    public void onPing(final PaperServerListPingEvent event) {
        if (!configManager.getBoolean("motd.enabled", true)) {
            return;
        }
        // IDEAS A50: aktív világesemény felülírja a normál rotációt (élő "kirakat").
        // Az esemény-getterek volatile állapot-olvasások (a HUD-tick is más szálról hívja őket),
        // így az async ping-szálról is biztonságosak.
        final ConfigurationSection eventVariant = activeEventVariant();
        if (eventVariant != null) {
            event.motd(render(eventVariant.getString("line1", ""))
                    .append(Component.newline())
                    .append(render(eventVariant.getString("line2", ""))));
            applyIcon(event, eventVariant.getString("icon", null));
            applyMaxPlayers(event);
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
                    applyIcon(event, variant.getString("icon", null));
                }
            }
        }
        applyMaxPlayers(event);
    }

    /** IDEAS A59: variánshoz rendelt ikon beállítása (icons/<név>.png), ha be van töltve. */
    private void applyIcon(final PaperServerListPingEvent event, final String iconName) {
        if (iconName == null || iconName.isBlank()) {
            return;
        }
        final org.bukkit.util.CachedServerIcon icon = icons.get(iconName);
        if (icon != null) {
            try {
                event.setServerIcon(icon);
            } catch (final Exception ignored) {
                // Néhány ping-implementáció elutasíthatja — a default ikon marad.
            }
        }
    }

    private void applyMaxPlayers(final PaperServerListPingEvent event) {
        final int maxOverride = configManager.getInt("motd.max-players-override", -1);
        if (maxOverride > 0) {
            event.setMaxPlayers(maxOverride);
        }
    }

    /** Az első illeszkedő esemény-variáns config-szekciója, vagy null (normál rotáció). */
    private ConfigurationSection activeEventVariant() {
        final org.bukkit.configuration.file.FileConfiguration cfg = configManager.getConfiguration();
        if (cfg == null) {
            return null;
        }
        if (bloodMoonManager != null && bloodMoonManager.isActive()) {
            final ConfigurationSection section = cfg.getConfigurationSection("motd.event-variants.blood-moon");
            if (section != null) {
                return section;
            }
        }
        if (worldBossManager != null && worldBossManager.isBossActive()) {
            final ConfigurationSection section = cfg.getConfigurationSection("motd.event-variants.world-boss");
            if (section != null) {
                return section;
            }
        }
        if (seasonManager != null) {
            final long daysLeft = (seasonManager.getSeasonEndMillis() - System.currentTimeMillis())
                    / (24L * 60L * 60L * 1000L);
            final int threshold = cfg.getInt("motd.event-variants.season-end.days-before", 3);
            if (daysLeft >= 0 && daysLeft <= threshold) {
                return cfg.getConfigurationSection("motd.event-variants.season-end");
            }
        }
        return null;
    }

    private Component render(final String line) {
        return MINI.deserialize(line
                .replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("{max}", String.valueOf(Bukkit.getMaxPlayers())));
    }
}
