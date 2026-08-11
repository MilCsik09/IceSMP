package hu.taliann.icesmp.listeners;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import hu.taliann.icesmp.managers.BloodMoonManager;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.SeasonManager;
import hu.taliann.icesmp.managers.VanishManager;
import hu.taliann.icesmp.managers.WorldBossManager;
import hu.taliann.icesmp.motd.MotdGenerationGate;
import hu.taliann.icesmp.motd.MotdIconValidator;
import hu.taliann.icesmp.motd.MotdSelector;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.CachedServerIcon;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Immutable-snapshot server-list presentation. The async ping handler reads only volatile snapshots,
 * thread-safe event state and the VanishManager's UUID cache; icon IO/decoding never runs on a
 * region thread and Bukkit icon creation is handed back to the global-region scheduler.
 */
public final class MotdListener implements Listener {

    private static final MiniMessage MINI = MiniMessage.builder().strict(true).build();
    private static final Pattern VARIANT_ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
    private static final Pattern ICON_ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
    private static final List<String> BUNDLED_ICONS = List.of(
            "frost", "war", "book", "whisper", "blood_moon", "world_boss", "season_end");
    private static final long ICON_RANDOM_SALT = 0x46A4_934B_7D1E_9C31L;
    private static final Path ICON_DIRECTORY = Path.of("icons");

    private final JavaPlugin plugin;
    private final Logger logger;
    private final ConfigManager configManager;
    private final BloodMoonManager bloodMoonManager;
    private final WorldBossManager worldBossManager;
    private final SeasonManager seasonManager;
    private final VanishManager vanishManager;
    private final Path dataDirectory;
    private final MotdGenerationGate generations = new MotdGenerationGate();
    private final AtomicBoolean iconApplyWarningLogged = new AtomicBoolean();

    private volatile Snapshot snapshot = Snapshot.disabled();
    private volatile IconCache iconCache = IconCache.empty();

    public MotdListener(final JavaPlugin plugin, final ConfigManager configManager,
                        final BloodMoonManager bloodMoonManager, final WorldBossManager worldBossManager,
                        final SeasonManager seasonManager, final VanishManager vanishManager) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configManager = configManager;
        this.bloodMoonManager = bloodMoonManager;
        this.worldBossManager = worldBossManager;
        this.seasonManager = seasonManager;
        this.vanishManager = vanishManager;
        this.dataDirectory = plugin.getDataFolder().toPath();
        reload();
    }

    /** Rebuilds the immutable config snapshot and starts a generation-gated asynchronous icon load. */
    public void reload() {
        final long requestedGeneration = generations.nextGeneration();
        iconApplyWarningLogged.set(false);
        iconCache = IconCache.empty();

        final Snapshot parsed;
        try {
            parsed = parseSnapshot();
        } catch (final IllegalArgumentException exception) {
            snapshot = Snapshot.disabled();
            logger.severe("A natív MOTD biztonságosan letiltva: " + exception.getMessage());
            return;
        }
        snapshot = parsed;
        if (!parsed.enabled()) {
            hu.taliann.icesmp.utils.StartupLog.info(logger, configManager, "A natív MOTD ki van kapcsolva.");
            return;
        }
        scheduleIconLoad(parsed, requestedGeneration);
    }

    /** Invalidates queued reload generations and drops all cached presentation state on disable. */
    public void shutdown() {
        generations.invalidate();
        snapshot = Snapshot.disabled();
        iconCache = IconCache.empty();
    }

    @EventHandler
    public void onPing(final PaperServerListPingEvent event) {
        final Snapshot current = snapshot;
        if (!current.enabled()) {
            return;
        }

        final long now = System.currentTimeMillis();
        final Variant selected = selectVariant(current, now);
        if (selected == null) {
            return;
        }

        final int effectiveMax = current.maxPlayersOverride() > 0
                ? current.maxPlayersOverride() : event.getMaxPlayers();
        final int effectiveOnline = current.excludeVanishedFromOnlineCount() && vanishManager != null
                ? vanishManager.onlineCountExcludingVanished() : event.getNumPlayers();

        try {
            event.motd(render(selected.line1(), effectiveOnline, effectiveMax)
                    .append(Component.newline())
                    .append(render(selected.line2(), effectiveOnline, effectiveMax)));
        } catch (final RuntimeException exception) {
            // Config is parsed strictly on reload. This is a defensive last gate against runtime parser drift.
            logger.warning("MOTD renderelési hiba; az aktuális ping az alap szerver-MOTD-t kapja: "
                    + exception.getMessage());
            return;
        }

        if (current.maxPlayersOverride() > 0) {
            event.setMaxPlayers(current.maxPlayersOverride());
        }
        applyIcon(event, current, selected, now);
    }

    private Variant selectVariant(final Snapshot current, final long now) {
        final MotdSelector.ActiveEvent activeEvent = MotdSelector.selectEvent(
                bloodMoonManager != null && bloodMoonManager.isActive(),
                worldBossManager != null && worldBossManager.isBossActive(),
                seasonManager == null ? Long.MIN_VALUE : seasonManager.getSeasonEndMillis(),
                now,
                current.seasonEndThresholdMillis());
        final Variant eventVariant = current.eventVariants().get(activeEvent);
        if (eventVariant != null) {
            return eventVariant;
        }
        final List<Variant> variants = current.variants();
        if (variants.isEmpty()) {
            return null;
        }
        return variants.get(MotdSelector.selectIndex(current.mode(), variants.size(), now,
                current.rotationMillis(), current.randomSeed()));
    }

    private Component render(final String raw, final int online, final int max) {
        return MINI.deserialize(raw
                .replace("{online}", Integer.toString(online))
                .replace("{max}", Integer.toString(max)));
    }

    private void applyIcon(final PaperServerListPingEvent event, final Snapshot current,
                           final Variant selected, final long now) {
        final IconCache currentCache = iconCache;
        final String selectedIcon = switch (current.iconMode()) {
            case NONE -> null;
            case DEFAULT -> current.defaultIcon();
            case VARIANT -> selected.icon() == null ? current.defaultIcon() : selected.icon();
            case RANDOM -> {
                final List<String> currentIds = currentCache.ids();
                if (currentIds.isEmpty()) {
                    yield current.defaultIcon();
                }
                final int index = MotdSelector.selectIndex(MotdSelector.Mode.RANDOM, currentIds.size(), now,
                        current.rotationMillis(), current.randomSeed() ^ ICON_RANDOM_SALT);
                yield currentIds.get(index);
            }
        };
        if (selectedIcon == null) {
            return;
        }
        final CachedServerIcon icon = currentCache.icons().get(selectedIcon);
        if (icon == null) {
            return;
        }
        try {
            event.setServerIcon(icon);
        } catch (final Exception exception) {
            if (iconApplyWarningLogged.compareAndSet(false, true)) {
                logger.warning("A szerverlista-ikon nem alkalmazható; a default szerverikon marad: "
                        + exception.getMessage());
            }
        }
    }

    private Snapshot parseSnapshot() {
        final FileConfiguration configuration = configManager.getConfiguration();
        if (configuration == null) {
            throw new IllegalArgumentException("a konfiguráció még nincs betöltve");
        }
        final ConfigurationSection root = configuration.getConfigurationSection("motd");
        if (root == null) {
            throw new IllegalArgumentException("hiányzik a motd configszekció");
        }
        final boolean enabled = readBoolean(root, "enabled", true);
        if (!enabled) {
            return Snapshot.disabled();
        }

        final MotdSelector.Mode mode = MotdSelector.Mode.parse(root.getString("selection-mode", "TIME"));
        final long rotationSeconds = readWholeNumber(root, "rotation-seconds", 10L, 2L, 86_400L);
        final long randomSeed = readWholeNumber(root, "random-seed", 0x1CE5_4D50L,
                Long.MIN_VALUE, Long.MAX_VALUE);
        final int maxPlayersOverride = (int) readWholeNumber(root, "max-players-override", -1L,
                -1L, 1_000_000L);
        if (maxPlayersOverride == 0) {
            throw new IllegalArgumentException("motd.max-players-override: 0 nem használható; -1 vagy pozitív érték kell");
        }
        final boolean excludeVanished = readBoolean(root, "exclude-vanished-from-online-count", true);

        final ConfigurationSection iconSection = requireSection(root, "icons");
        final IconMode iconMode = IconMode.parse(iconSection.getString("mode", "VARIANT"));
        final String defaultIcon = optionalIconId(iconSection.getString("default", "frost"),
                "motd.icons.default");
        final int maxIconFiles = (int) readWholeNumber(iconSection, "max-files", 64L, 1L, 64L);
        final long maxIconBytes = readWholeNumber(iconSection, "max-file-bytes", 1_048_576L,
                1_024L, 1_048_576L);

        final ConfigurationSection variantsSection = requireSection(root, "variants");
        final List<String> variantKeys = new ArrayList<>(variantsSection.getKeys(false));
        if (variantKeys.isEmpty()) {
            throw new IllegalArgumentException("motd.variants: legalább egy normál variáns szükséges");
        }
        if (variantKeys.size() > 64) {
            throw new IllegalArgumentException("motd.variants: legfeljebb 64 variáns engedélyezett");
        }
        final Set<String> normalizedIds = new HashSet<>();
        final List<Variant> variants = new ArrayList<>();
        for (final String key : variantKeys) {
            final String normalized = normalizeVariantId(key);
            if (!normalizedIds.add(normalized)) {
                throw new IllegalArgumentException("duplikált normalizált MOTD variáns-ID: " + key);
            }
            variants.add(parseVariant(requireSection(variantsSection, key), normalized,
                    "motd.variants." + key));
        }

        final Map<MotdSelector.ActiveEvent, Variant> eventVariants = new LinkedHashMap<>();
        long seasonThreshold = Duration.ofDays(3L).toMillis();
        final ConfigurationSection events = root.getConfigurationSection("event-variants");
        if (events != null) {
            putEventVariant(events, "blood-moon", MotdSelector.ActiveEvent.BLOOD_MOON, eventVariants);
            putEventVariant(events, "world-boss", MotdSelector.ActiveEvent.WORLD_BOSS, eventVariants);
            final ConfigurationSection season = events.getConfigurationSection("season-end");
            if (season != null && readBoolean(season, "enabled", true)) {
                final long daysBefore = readWholeNumber(season, "days-before", 3L, 0L, 3_650L);
                seasonThreshold = Math.multiplyExact(daysBefore, Duration.ofDays(1L).toMillis());
                eventVariants.put(MotdSelector.ActiveEvent.SEASON_END,
                        parseVariant(season, "season-end", "motd.event-variants.season-end"));
            }
        }

        return new Snapshot(true, mode, Math.multiplyExact(rotationSeconds, 1_000L), randomSeed,
                maxPlayersOverride, excludeVanished, iconMode, defaultIcon, maxIconFiles, maxIconBytes,
                List.copyOf(variants), Map.copyOf(eventVariants), seasonThreshold);
    }

    private void putEventVariant(final ConfigurationSection events, final String key,
                                 final MotdSelector.ActiveEvent event,
                                 final Map<MotdSelector.ActiveEvent, Variant> target) {
        final ConfigurationSection section = events.getConfigurationSection(key);
        if (section != null && readBoolean(section, "enabled", true)) {
            target.put(event, parseVariant(section, key, "motd.event-variants." + key));
        }
    }

    private Variant parseVariant(final ConfigurationSection section, final String id, final String path) {
        final String line1 = requiredText(section, "line1", path + ".line1");
        final String line2 = requiredText(section, "line2", path + ".line2");
        validateMiniMessage(line1, path + ".line1");
        validateMiniMessage(line2, path + ".line2");
        final String icon = optionalIconId(section.getString("icon", null), path + ".icon");
        return new Variant(id, line1, line2, icon);
    }

    private void validateMiniMessage(final String value, final String path) {
        MotdSelector.validatePlaceholders(value, path);
        try {
            MINI.deserialize(value.replace("{online}", "0").replace("{max}", "0"));
        } catch (final RuntimeException exception) {
            throw new IllegalArgumentException(path + ": hibás strict MiniMessage: " + exception.getMessage());
        }
    }

    private void scheduleIconLoad(final Snapshot requested, final long requestedGeneration) {
        final MotdGenerationGate.Attempt asyncAttempt = generations.newAttempt(requestedGeneration);
        try {
            final ScheduledTask submitted = plugin.getServer().getAsyncScheduler().runNow(plugin, task ->
                    asyncAttempt.runCurrent(() -> {
                        final Map<String, BufferedImage> decoded = decodeIcons(requested);
                        final MotdGenerationGate.Attempt publishAttempt = generations.newAttempt(requestedGeneration);
                        try {
                            final ScheduledTask publishTask = plugin.getServer().getGlobalRegionScheduler().run(
                                    plugin, scheduledTask -> publishAttempt.runCurrent(() ->
                                            publishIcons(decoded, requested, requestedGeneration)));
                            if (publishTask == null) {
                                publishAttempt.rejectCurrent(() -> warnIconPublishRejected("null scheduler handle"));
                            }
                        } catch (final RuntimeException exception) {
                            publishAttempt.rejectCurrent(() -> warnIconPublishRejected(safeMessage(exception)));
                        }
                    }));
            if (submitted == null) {
                asyncAttempt.rejectCurrent(() -> logger.warning(
                        "A MOTD ikonkészlet async betöltése nem indítható; a default szerverikon marad: null scheduler handle"));
            }
        } catch (final RuntimeException exception) {
            asyncAttempt.rejectCurrent(() -> logger.warning(
                    "A MOTD ikonkészlet async betöltése nem indítható; a default szerverikon marad: "
                            + safeMessage(exception)));
        }
    }

    private Map<String, BufferedImage> decodeIcons(final Snapshot requested) {
        try {
            extractBundledIcons();
            final MotdIconValidator.ScanResult scan = MotdIconValidator.scanPngDirectory(
                    dataDirectory, ICON_DIRECTORY, requested.maxIconFiles(), requested.maxIconBytes());
            for (final String warning : scan.warnings()) {
                logger.warning("MOTD ikon kihagyva: " + warning);
            }
            final Map<String, BufferedImage> decoded = new LinkedHashMap<>();
            for (final MotdIconValidator.DecodedIcon icon : scan.icons()) {
                final String fileName = icon.fileName();
                final String rawId = fileName.substring(0, fileName.length() - 4);
                final String id;
                try {
                    id = requireIconId(rawId, "icons/" + fileName);
                } catch (final IllegalArgumentException exception) {
                    logger.warning(exception.getMessage());
                    continue;
                }
                if (decoded.putIfAbsent(id, icon.image()) != null) {
                    logger.warning("Duplikált normalizált MOTD ikon-ID kihagyva: " + fileName);
                }
            }
            return Map.copyOf(decoded);
        } catch (final Exception exception) {
            logger.warning("A MOTD ikonkönyvtár fail-closed kihagyva; a default szerverikon marad: "
                    + safeMessage(exception));
            return Map.of();
        }
    }

    private void publishIcons(final Map<String, BufferedImage> decoded, final Snapshot requested,
                              final long requestedGeneration) {
        if (!generations.isCurrent(requestedGeneration)) {
            return;
        }
        final Map<String, CachedServerIcon> cached = new HashMap<>();
        for (final Map.Entry<String, BufferedImage> entry : decoded.entrySet()) {
            try {
                cached.put(entry.getKey(), Bukkit.loadServerIcon(entry.getValue()));
            } catch (final Exception exception) {
                logger.warning("MOTD ikon nem alakítható Bukkit cache-é (" + entry.getKey() + "): "
                        + safeMessage(exception));
            }
        }
        final Map<String, CachedServerIcon> immutable = Map.copyOf(cached);
        final IconCache published = new IconCache(immutable, immutable.keySet().stream().sorted().toList());
        generations.publishIfCurrent(requestedGeneration, () -> {
            iconCache = published;
            warnMissingConfiguredIcons(requested, immutable.keySet());
            hu.taliann.icesmp.utils.StartupLog.info(logger, configManager, "MOTD ikonkészlet betöltve: " + immutable.size() + " érvényes 64×64 PNG.");
        });
    }

    private void warnIconPublishRejected(final String detail) {
        logger.warning("A MOTD ikonok global-region publikálása nem indítható; "
                + "a default szerverikon marad: " + detail);
    }

    private void warnMissingConfiguredIcons(final Snapshot requested, final Set<String> available) {
        final Set<String> configured = new HashSet<>();
        if (requested.defaultIcon() != null) {
            configured.add(requested.defaultIcon());
        }
        requested.variants().stream().map(Variant::icon).filter(java.util.Objects::nonNull).forEach(configured::add);
        requested.eventVariants().values().stream().map(Variant::icon)
                .filter(java.util.Objects::nonNull).forEach(configured::add);
        configured.removeAll(available);
        if (!configured.isEmpty()) {
            logger.warning("A MOTD config nem betöltött ikonokra hivatkozik: " + configured);
        }
    }

    private void extractBundledIcons() throws java.io.IOException {
        final Map<String, byte[]> bundled = new LinkedHashMap<>();
        for (final String id : BUNDLED_ICONS) {
            try (InputStream input = plugin.getResource("icons/" + id + ".png")) {
                if (input == null) {
                    throw new java.io.IOException("hiányzó beépített ikon: " + id);
                }
                bundled.put(id + ".png", input.readAllBytes());
            }
        }
        MotdIconValidator.writeFilesIfMissing(dataDirectory, ICON_DIRECTORY, bundled);
    }

    private static String safeMessage(final Throwable failure) {
        final String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private static ConfigurationSection requireSection(final ConfigurationSection parent, final String path) {
        final ConfigurationSection section = parent.getConfigurationSection(path);
        if (section == null) {
            throw new IllegalArgumentException("hiányzó configszekció: " + parent.getCurrentPath() + "." + path);
        }
        return section;
    }

    private static String requiredText(final ConfigurationSection section, final String key, final String path) {
        final String value = section.getString(key, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(path + ": az érték nem lehet üres");
        }
        if (value.length() > 1_024) {
            throw new IllegalArgumentException(path + ": legfeljebb 1024 karakter lehet");
        }
        return value;
    }

    private static boolean readBoolean(final ConfigurationSection section, final String key,
                                       final boolean fallback) {
        return MotdSelector.parseBoolean(section.get(key), fallback,
                section.getCurrentPath() + "." + key);
    }

    private static long readWholeNumber(final ConfigurationSection section, final String key,
                                        final long fallback, final long minimum, final long maximum) {
        return MotdSelector.parseWholeNumber(section.get(key), fallback, minimum, maximum,
                section.getCurrentPath() + "." + key);
    }

    private static String normalizeVariantId(final String value) {
        if (value == null) {
            throw new IllegalArgumentException("A MOTD variáns-ID nem lehet null.");
        }
        final String normalized = value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        if (!VARIANT_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Hibás MOTD variáns-ID: " + value);
        }
        return normalized;
    }

    private static String optionalIconId(final String value, final String path) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requireIconId(value, path);
    }

    private static String requireIconId(final String value, final String path) {
        final String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!ICON_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException(path + ": hibás ikon-ID: " + value);
        }
        return normalized;
    }

    private enum IconMode {
        NONE,
        DEFAULT,
        VARIANT,
        RANDOM;

        private static IconMode parse(final String value) {
            if (value == null) {
                throw new IllegalArgumentException("motd.icons.mode: hiányzó érték");
            }
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (final IllegalArgumentException ignored) {
                throw new IllegalArgumentException("motd.icons.mode: ismeretlen mód: " + value);
            }
        }
    }

    private record IconCache(Map<String, CachedServerIcon> icons, List<String> ids) {
        private static IconCache empty() {
            return new IconCache(Map.of(), List.of());
        }
    }

    private record Variant(String id, String line1, String line2, String icon) {
    }

    private record Snapshot(boolean enabled, MotdSelector.Mode mode, long rotationMillis, long randomSeed,
                            int maxPlayersOverride, boolean excludeVanishedFromOnlineCount,
                            IconMode iconMode, String defaultIcon, int maxIconFiles, long maxIconBytes,
                            List<Variant> variants, Map<MotdSelector.ActiveEvent, Variant> eventVariants,
                            long seasonEndThresholdMillis) {
        private static Snapshot disabled() {
            return new Snapshot(false, MotdSelector.Mode.TIME, 10_000L, 0L, -1, true,
                    IconMode.NONE, null, 64, 1_048_576L, List.of(), Map.of(), 0L);
        }
    }
}
