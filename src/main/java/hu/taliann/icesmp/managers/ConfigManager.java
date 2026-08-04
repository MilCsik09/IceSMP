package hu.taliann.icesmp.managers;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * Manager for loading and accessing configuration values.
 * Provides centralized access to a single atomically published configuration generation.
 */
public final class ConfigManager {

    /**
     * One immutable publication unit. The contained Bukkit configurations are built privately and
     * are never mutated after publication; the override set belongs to the exact same generation.
     * {@code baseConfiguration} is the merged config/ directory before config.yml overrides, so the
     * admin GUI can remove an override and show the value it will genuinely fall back to.
     */
    public record ConfigSnapshot(FileConfiguration configuration,
                                 FileConfiguration baseConfiguration,
                                 Set<String> overridePaths,
                                 long generation) {
        public ConfigSnapshot {
            overridePaths = overridePaths == null ? Set.of() : Set.copyOf(overridePaths);
        }

        /** Backward-compatible constructor used by focused tests and pure policy adapters. */
        public ConfigSnapshot(final FileConfiguration configuration,
                              final Set<String> overridePaths,
                              final long generation) {
            this(configuration, configuration, overridePaths, generation);
        }

        public boolean isSet(final String path) {
            return configuration != null && configuration.isSet(path);
        }

        public boolean isOverridden(final String path) {
            return overridePaths.contains(path);
        }
    }

    /** Bundled per-subsystem config files under config/. */
    private static final String[] CONFIG_FILES = {
            "general", "economy", "factions", "block-regen", "classes", "spells", "spells-balance",
            "professions", "quests", "world", "relics", "pets", "crafting", "crates", "afk", "moderation",
            "item-rarity", "loot", "motd", "profession-materials", "profession-recipes", "sit", "tablist", "dev-items"
    };

    private final JavaPlugin plugin;
    /** All readers observe either the complete old generation or the complete new generation. */
    private volatile ConfigSnapshot liveSnapshot =
            new ConfigSnapshot(null, null, Set.of(), 0L);

    public ConfigManager(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads packaged defaults, deployed subsystem files and the optional config.yml override, then
     * publishes all three layers as one generation. Packaged defaults are merged first so newly
     * introduced keys exist on older servers even when saveResource(..., false) keeps their old
     * data-folder YAML; explicit data-folder values still win over the package.
     */
    public synchronized void load() {
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

        final YamlConfiguration merged = new YamlConfiguration();
        mergeInto(merged, base);

        plugin.reloadConfig();
        final Set<String> overridePaths = plugin.getConfig().getKeys(true).stream()
                .filter(key -> !plugin.getConfig().isConfigurationSection(key))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        mergeInto(merged, plugin.getConfig());

        final long previousGeneration = liveSnapshot.generation();
        final long nextGeneration = previousGeneration == Long.MAX_VALUE
                ? Long.MAX_VALUE : previousGeneration + 1L;
        liveSnapshot = new ConfigSnapshot(merged, base, overridePaths, nextGeneration);
    }

    private void mergePackagedDefaults(final YamlConfiguration target, final String name) {
        try (InputStream input = plugin.getResource("config/" + name + ".yml")) {
            if (input == null) {
                plugin.getLogger().warning("Hiányzó csomagolt config: config/" + name + ".yml");
                return;
            }
            final InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
            mergeInto(target, YamlConfiguration.loadConfiguration(reader));
        } catch (final Exception failure) {
            plugin.getLogger().warning("Csomagolt config nem olvasható (" + name + "): " + failure);
        }
    }

    private static void mergeInto(final YamlConfiguration target,
                                  final ConfigurationSection source) {
        for (final String key : source.getKeys(true)) {
            if (!source.isConfigurationSection(key)) {
                target.set(key, source.get(key));
            }
        }
    }

    public void reload() {
        load();
    }

    /** Serialized config.yml override mutation followed by one new atomic generation. */
    public synchronized boolean applyOverride(final String key, final Object value) {
        plugin.reloadConfig();
        final boolean existed = plugin.getConfig().isSet(key);
        if (value == null && !existed) {
            return false;
        }
        plugin.getConfig().set(key, value);
        plugin.saveConfig();
        load();
        return true;
    }

    /** Removes only the config.yml override; the subsystem config value becomes authoritative. */
    public boolean resetOverride(final String key) {
        return applyOverride(key, null);
    }

    public ConfigSnapshot snapshot() {
        return liveSnapshot;
    }

    /** Returns null if not yet loaded. */
    public FileConfiguration getConfiguration() {
        return liveSnapshot.configuration();
    }

    /** Returns the merged config/ value before config.yml overrides, or null when absent. */
    public Object getBaseValue(final String path) {
        final FileConfiguration base = liveSnapshot.baseConfiguration();
        return base == null ? null : base.get(path);
    }

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

    public boolean contains(final String path) {
        return liveSnapshot.isSet(path);
    }

    public boolean hasOverride(final String path) {
        return liveSnapshot.isOverridden(path);
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
