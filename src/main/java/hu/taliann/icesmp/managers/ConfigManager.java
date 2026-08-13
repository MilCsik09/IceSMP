package hu.taliann.icesmp.managers;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Centralized, atomically-published configuration with packaged-default fallback and
 * optimistic-concurrency admin writes.
 */
public final class ConfigManager {

    /** One immutable publication unit: packaged/deployed defaults, effective values and provenance. */
    public record ConfigSnapshot(FileConfiguration configuration,
                                 FileConfiguration baseConfiguration,
                                 Set<String> overridePaths,
                                 long generation,
                                 String sourceFingerprint) {
        public ConfigSnapshot {
            overridePaths = overridePaths == null ? Set.of() : Set.copyOf(overridePaths);
            sourceFingerprint = sourceFingerprint == null ? "" : sourceFingerprint;
        }

        /** Compatibility constructor retained for tests/consumers that only need the effective tree. */
        public ConfigSnapshot(final FileConfiguration configuration, final Set<String> overridePaths,
                              final long generation) {
            this(configuration, configuration, overridePaths, generation, "");
        }

        public boolean isSet(final String path) {
            return configuration != null && configuration.isSet(path);
        }

        public boolean isOverridden(final String path) {
            return overridePaths.contains(path);
        }

        public Object baseValue(final String path) {
            return baseConfiguration == null ? null : baseConfiguration.get(path);
        }
    }

    public enum BatchApplyResult { APPLIED, STALE, NO_CHANGES }

    /** Bundled per-subsystem config files under config/. */
    private static final String[] CONFIG_FILES = {
            "general", "economy", "factions", "block-regen", "classes", "class-gameplay", "spells", "spells-balance",
            "professions", "quests", "world", "event-spawn-safety", "relics", "pets", "crafting", "crates",
            "afk", "moderation", "item-rarity", "loot", "motd", "profession-materials",
            "profession-recipes", "sit", "tablist", "dev-items", "client"
    };

    private final JavaPlugin plugin;
    private volatile ConfigSnapshot liveSnapshot = new ConfigSnapshot(null, null, Set.of(), 0L, "");

    public ConfigManager(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads packaged defaults first, then deployed subsystem files, then config.yml overrides.
     * This keeps newly introduced keys available on older installations while preserving every
     * explicit deployed value and the optimistic-concurrency fingerprint of the override file.
     */
    public synchronized void load() {
        final YamlConfiguration base = loadBaseConfiguration();
        final YamlConfiguration effective = new YamlConfiguration();
        mergeInto(effective, base);

        plugin.reloadConfig();
        final Set<String> overridePaths = new HashSet<>();
        for (final String key : plugin.getConfig().getKeys(true)) {
            if (!plugin.getConfig().isConfigurationSection(key)) {
                overridePaths.add(key);
            }
        }
        mergeInto(effective, plugin.getConfig());

        final long previousGeneration = liveSnapshot.generation();
        final long nextGeneration = previousGeneration == Long.MAX_VALUE
                ? Long.MAX_VALUE : previousGeneration + 1L;
        liveSnapshot = new ConfigSnapshot(effective, base, overridePaths,
                nextGeneration, overrideFingerprint());
    }

    private YamlConfiguration loadBaseConfiguration() {
        final YamlConfiguration base = new YamlConfiguration();
        final File dir = new File(plugin.getDataFolder(), "config");
        dir.mkdirs();
        for (final String name : CONFIG_FILES) {
            mergePackagedDefaults(base, name);
            if (!new File(dir, name + ".yml").exists()) {
                plugin.saveResource("config/" + name + ".yml", false);
            }
        }
        for (final String name : CONFIG_FILES) {
            final File file = new File(dir, name + ".yml");
            if (file.exists()) {
                mergeInto(base, YamlConfiguration.loadConfiguration(file));
            }
        }
        final File[] files = dir.listFiles((directory, fileName) -> fileName.endsWith(".yml"));
        if (files != null) {
            for (final File file : files) {
                final String baseName = file.getName().substring(0, file.getName().length() - 4);
                if (java.util.Arrays.stream(CONFIG_FILES).noneMatch(baseName::equals)) {
                    plugin.getLogger().warning("Ismeretlen config-fájl kihagyva a merge-ből: config/"
                            + file.getName() + " (csak a CONFIG_FILES lista töltődik be)");
                }
            }
        }
        return base;
    }

    private void mergePackagedDefaults(final YamlConfiguration target, final String name) {
        try (InputStream input = plugin.getResource("config/" + name + ".yml")) {
            if (input == null) {
                plugin.getLogger().warning("Hiányzó csomagolt config: config/" + name + ".yml");
                return;
            }
            mergeInto(target, YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8)));
        } catch (final Exception failure) {
            plugin.getLogger().warning("Csomagolt config nem olvasható (" + name + "): " + failure);
        }
    }

    private static void mergeInto(final YamlConfiguration target, final ConfigurationSection source) {
        for (final String key : source.getKeys(true)) {
            if (!source.isConfigurationSection(key)) {
                target.set(key, source.get(key));
            }
        }
    }

    public void reload() {
        load();
    }

    /** Serialized single-key compatibility path used by admin commands and focused adapters. */
    public synchronized boolean applyOverride(final String key, final Object value) {
        final ConfigSnapshot snapshot = liveSnapshot;
        return applyOverridesIfUnchanged(snapshot.generation(), snapshot.sourceFingerprint(),
                java.util.Collections.singletonMap(key, value)) == BatchApplyResult.APPLIED;
    }

    /** Removes only the config.yml override; subsystem/package defaults become authoritative. */
    public boolean resetOverride(final String key) {
        return applyOverride(key, null);
    }

    /**
     * Applies one GUI transaction with compare-and-set semantics. Both the published generation and
     * the on-disk config.yml fingerprint must still match the editor's opening snapshot, otherwise
     * a second admin or an external file edit wins and this stale transaction is rejected.
     */
    public synchronized BatchApplyResult applyOverridesIfUnchanged(final long expectedGeneration,
                                                                    final String expectedFingerprint,
                                                                    final Map<String, Object> changes) {
        if (changes == null || changes.isEmpty()) {
            return BatchApplyResult.NO_CHANGES;
        }
        if (liveSnapshot.generation() != expectedGeneration
                || !java.util.Objects.equals(expectedFingerprint, overrideFingerprint())) {
            return BatchApplyResult.STALE;
        }
        plugin.reloadConfig();
        boolean changed = false;
        for (final Map.Entry<String, Object> entry : new LinkedHashMap<>(changes).entrySet()) {
            final String path = entry.getKey();
            final Object value = entry.getValue();
            final Object previous = plugin.getConfig().get(path);
            if (!java.util.Objects.equals(previous, value)
                    || plugin.getConfig().isSet(path) != (value != null)) {
                plugin.getConfig().set(path, value);
                changed = true;
            }
        }
        if (!changed) {
            return BatchApplyResult.NO_CHANGES;
        }
        plugin.saveConfig();
        load();
        return BatchApplyResult.APPLIED;
    }

    private String overrideFingerprint() {
        final File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.exists()) {
            return "MISSING";
        }
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(file.toPath()));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (final Exception failure) {
            plugin.getLogger().warning("config.yml fingerprint failed; conservative stale marker used: " + failure);
            return "ERROR:" + file.length() + ':' + file.lastModified();
        }
    }

    public ConfigSnapshot snapshot() { return liveSnapshot; }
    public FileConfiguration getConfiguration() { return liveSnapshot.configuration(); }
    public boolean contains(final String path) { return liveSnapshot.isSet(path); }
    public boolean hasOverride(final String path) { return liveSnapshot.isOverridden(path); }
    public Object getBaseValue(final String path) { return liveSnapshot.baseValue(path); }

    public String getBaseString(final String path, final String fallback) {
        final FileConfiguration base = liveSnapshot.baseConfiguration();
        return base == null ? fallback : base.getString(path, fallback);
    }

    public double getBaseDouble(final String path, final double fallback) {
        final FileConfiguration base = liveSnapshot.baseConfiguration();
        return base == null ? fallback : base.getDouble(path, fallback);
    }

    public boolean getBaseBoolean(final String path, final boolean fallback) {
        final FileConfiguration base = liveSnapshot.baseConfiguration();
        return base == null ? fallback : base.getBoolean(path, fallback);
    }

    public String getString(final String path, final String fallback) {
        final FileConfiguration configuration = liveSnapshot.configuration();
        return configuration == null ? fallback : configuration.getString(path, fallback);
    }
    public int getInt(final String path, final int fallback) {
        final FileConfiguration configuration = liveSnapshot.configuration();
        return configuration == null ? fallback : configuration.getInt(path, fallback);
    }
    public long getLong(final String path, final long fallback) {
        final FileConfiguration configuration = liveSnapshot.configuration();
        return configuration == null ? fallback : configuration.getLong(path, fallback);
    }
    public double getDouble(final String path, final double fallback) {
        final FileConfiguration configuration = liveSnapshot.configuration();
        return configuration == null ? fallback : configuration.getDouble(path, fallback);
    }
    public boolean getBoolean(final String path, final boolean fallback) {
        final FileConfiguration configuration = liveSnapshot.configuration();
        return configuration == null ? fallback : configuration.getBoolean(path, fallback);
    }
    public List<String> getStringList(final String path) {
        final FileConfiguration configuration = liveSnapshot.configuration();
        return configuration == null ? List.of() : configuration.getStringList(path);
    }
    public List<Double> getDoubleList(final String path) {
        final FileConfiguration configuration = liveSnapshot.configuration();
        return configuration == null ? List.of() : configuration.getDoubleList(path);
    }
}
