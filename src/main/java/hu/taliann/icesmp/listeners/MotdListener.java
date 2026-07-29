package hu.taliann.icesmp.listeners;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import hu.taliann.icesmp.managers.BloodMoonManager;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.SeasonManager;
import hu.taliann.icesmp.managers.VanishManager;
import hu.taliann.icesmp.managers.WorldBossManager;
import hu.taliann.icesmp.motd.MotdSelector;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.CachedServerIcon;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
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

    private final JavaPlugin plugin;
    private final Logger logger;
    private final ConfigManager configManager;
    private final BloodMoonManager bloodMoonManager;
    private final WorldBossManager worldBossManager;
    private final SeasonManager seasonManager;
    private final VanishManager vanishManager;
    private final File iconDirectory;
    private final AtomicLong generation = new AtomicLong();
    private final AtomicBoolean iconApplyWarningLogged = new AtomicBoolean();

    private volatile Snapshot snapshot = Snapshot.disabled();
    private volatile Map<String, CachedServerIcon> icons = Map.of();
    private volatile List<String> iconIds = List.of();

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
        this.iconDirectory = new File(plugin.getDataFolder(), "icons");
        extractBundledIcons();
        reload();
    }

    /** Rebuilds the immutable config snapshot and starts a generation-gated asynchronous icon load. */
    public void reload() {
        final long requestedGeneration = generation.incrementAndGet();
        iconApplyWarningLogged.set(false);
        icons = Map.of();
        iconIds = List.of();

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
            logger.info("A natív MOTD ki van kapcsolva.");
            return;
        }
        scheduleIconLoad(parsed, requestedGeneration);
    }

    /** Invalidates queued reload generations and drops all cached presentation state on disable. */
    public void shutdown() {
        generation.incrementAndGet();
        snapshot = Snapshot.disabled();
        icons = Map.of();
        iconIds = List.of();
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
        final String selectedIcon = switch (current.iconMode()) {
            case NONE -> null;
            case DEFAULT -> current.defaultIcon();
            case VARIANT -> selected.icon() == null ? current.defaultIcon() : selected.icon();
            case RANDOM -> {
                final List<String> currentIds = iconIds;
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
        final CachedServerIcon icon = icons.get(selectedIcon);
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
        final boolean enabled = root.getBoolean("enabled", true);
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
        final boolean excludeVanished = root.getBoolean("exclude-vanished-from-online-count", true);

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
            if (season != null && season.getBoolean("enabled", true)) {
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
        if (section != null && section.getBoolean("enabled", true)) {
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
        try {
            MINI.deserialize(value.replace("{online}", "0").replace("{max}", "0"));
        } catch (final RuntimeException exception) {
            throw new IllegalArgumentException(path + ": hibás strict MiniMessage: " + exception.getMessage());
        }
    }

    private void scheduleIconLoad(final Snapshot requested, final long requestedGeneration) {
        try {
            plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                final Map<String, BufferedImage> decoded = decodeIcons(requested);
                if (generation.get() != requestedGeneration) {
                    return;
                }
                plugin.getServer().getGlobalRegionScheduler().run(plugin, scheduledTask ->
                        publishIcons(decoded, requested, requestedGeneration));
            });
        } catch (final RuntimeException exception) {
            logger.warning("A MOTD ikonkészlet async betöltése nem indítható; a default szerverikon marad: "
                    + exception.getMessage());
        }
    }

    private Map<String, BufferedImage> decodeIcons(final Snapshot requested) {
        final File[] listed = iconDirectory.listFiles((directory, name) ->
                name.toLowerCase(Locale.ROOT).endsWith(".png"));
        if (listed == null || listed.length == 0) {
            return Map.of();
        }
        final List<File> files = new ArrayList<>(List.of(listed));
        files.sort(Comparator.comparing(File::getName));
        if (files.size() > requested.maxIconFiles()) {
            logger.warning("Túl sok MOTD ikon van az icons/ mappában (" + files.size() + "); csak az első "
                    + requested.maxIconFiles() + " kerül betöltésre.");
            files.subList(requested.maxIconFiles(), files.size()).clear();
        }

        final Map<String, BufferedImage> decoded = new LinkedHashMap<>();
        for (final File file : files) {
            final Path path = file.toPath();
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                logger.warning("MOTD ikon kihagyva, mert nem normál fájl: " + file.getName());
                continue;
            }
            final String rawId = file.getName().substring(0, file.getName().length() - 4);
            final String id;
            try {
                id = requireIconId(rawId, "icons/" + file.getName());
            } catch (final IllegalArgumentException exception) {
                logger.warning(exception.getMessage());
                continue;
            }
            if (decoded.containsKey(id)) {
                logger.warning("Duplikált normalizált MOTD ikon-ID kihagyva: " + file.getName());
                continue;
            }
            try {
                final long size = Files.size(path);
                if (size <= 0L || size > requested.maxIconBytes()) {
                    throw new IOException("fájlméret " + size + " bájt; limit " + requested.maxIconBytes());
                }
                decoded.put(id, readValidatedPng(path));
            } catch (final Exception exception) {
                logger.warning("MOTD ikon kihagyva (" + file.getName() + "): " + exception.getMessage());
            }
        }
        return Map.copyOf(decoded);
    }

    private BufferedImage readValidatedPng(final Path path) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
            if (input == null) {
                throw new IOException("nem nyitható képfájlként");
            }
            final Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("ismeretlen vagy sérült PNG");
            }
            final ImageReader reader = readers.next();
            try {
                if (!"png".equalsIgnoreCase(reader.getFormatName())) {
                    throw new IOException("a fájl nem PNG");
                }
                reader.setInput(input, true, true);
                if (reader.getWidth(0) != 64 || reader.getHeight(0) != 64) {
                    throw new IOException("az ikon mérete nem pontosan 64×64");
                }
                final BufferedImage image = reader.read(0);
                if (image == null || image.getWidth() != 64 || image.getHeight() != 64) {
                    throw new IOException("a 64×64 PNG nem dekódolható");
                }
                return image;
            } finally {
                reader.dispose();
            }
        }
    }

    private void publishIcons(final Map<String, BufferedImage> decoded, final Snapshot requested,
                              final long requestedGeneration) {
        if (generation.get() != requestedGeneration) {
            return;
        }
        final Map<String, CachedServerIcon> cached = new HashMap<>();
        for (final Map.Entry<String, BufferedImage> entry : decoded.entrySet()) {
            try {
                cached.put(entry.getKey(), Bukkit.loadServerIcon(entry.getValue()));
            } catch (final Exception exception) {
                logger.warning("MOTD ikon nem alakítható Bukkit cache-é (" + entry.getKey() + "): "
                        + exception.getMessage());
            }
        }
        if (generation.get() != requestedGeneration) {
            return;
        }
        final Map<String, CachedServerIcon> immutable = Map.copyOf(cached);
        icons = immutable;
        iconIds = immutable.keySet().stream().sorted().toList();
        warnMissingConfiguredIcons(requested, immutable.keySet());
        logger.info("MOTD ikonkészlet betöltve: " + immutable.size() + " érvényes 64×64 PNG.");
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

    private void extractBundledIcons() {
        if (!iconDirectory.exists() && !iconDirectory.mkdirs()) {
            logger.warning("Az icons/ könyvtár nem hozható létre; a default szerverikon marad.");
            return;
        }
        for (final String id : BUNDLED_ICONS) {
            final File destination = new File(iconDirectory, id + ".png");
            if (destination.exists()) {
                continue;
            }
            try {
                plugin.saveResource("icons/" + id + ".png", false);
            } catch (final IllegalArgumentException exception) {
                logger.warning("A beépített MOTD ikon nem csomagolható ki (" + id + "): "
                        + exception.getMessage());
            }
        }
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

    private static long readWholeNumber(final ConfigurationSection section, final String key,
                                        final long fallback, final long minimum, final long maximum) {
        final Object raw = section.get(key);
        if (raw == null) {
            return fallback;
        }
        final double number;
        if (raw instanceof Number numeric) {
            number = numeric.doubleValue();
        } else {
            try {
                number = Double.parseDouble(raw.toString().trim());
            } catch (final NumberFormatException exception) {
                throw new IllegalArgumentException(section.getCurrentPath() + "." + key + ": nem szám");
            }
        }
        if (!Double.isFinite(number) || Math.rint(number) != number) {
            throw new IllegalArgumentException(section.getCurrentPath() + "." + key
                    + ": csak véges egész szám lehet");
        }
        if (number < minimum || number > maximum) {
            throw new IllegalArgumentException(section.getCurrentPath() + "." + key + ": tartományon kívüli érték ("
                    + minimum + ".." + maximum + ")");
        }
        return (long) number;
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
