package hu.taliann.icesmp.managers;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * Manager for loading and accessing configuration values.
 * Provides centralized access to a single atomically published configuration generation.
 */
public final class ConfigManager {

    /**
     * One immutable publication unit. The contained Bukkit configuration is built privately and is
     * never mutated after publication; the override set belongs to the exact same generation.
     */
    public record ConfigSnapshot(FileConfiguration configuration,
                                 Set<String> overridePaths,
                                 long generation) {
        public ConfigSnapshot {
            overridePaths = overridePaths == null ? Set.of() : Set.copyOf(overridePaths);
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
            "general", "economy", "factions", "classes", "spells", "spells-balance",
            "professions", "quests", "world", "relics", "pets", "crafting", "crates", "afk", "moderation",
            "item-rarity", "loot", "motd", "profession-materials", "profession-recipes", "sit", "tablist", "dev-items"
    };

    private final JavaPlugin plugin;
    /** All readers observe either the complete old generation or the complete new generation. */
    private volatile ConfigSnapshot liveSnapshot = new ConfigSnapshot(null, Set.of(), 0L);

    public ConfigManager(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads the packaged subsystem files and the optional config.yml override, then publishes the
     * merged tree and its override-index with one volatile reference replacement.
     */
    public synchronized void load() {
        final YamlConfiguration merged = new YamlConfiguration();
        final File dir = new File(plugin.getDataFolder(), "config");
        dir.mkdirs();
        for (final String name : CONFIG_FILES) {
            if (!new File(dir, name + ".yml").exists()) {
                plugin.saveResource("config/" + name + ".yml", false);
            }
        }
        for (final String name : CONFIG_FILES) {
            final File file = new File(dir, name + ".yml");
            if (file.exists()) {
                mergeInto(merged, YamlConfiguration.loadConfiguration(file));
            }
        }
        final File[] files = dir.listFiles((directory, fileName) -> fileName.endsWith(".yml"));
        if (files != null) {
            for (final File file : files) {
                final String base = file.getName().substring(0, file.getName().length() - 4);
                if (java.util.Arrays.stream(CONFIG_FILES).noneMatch(base::equals)) {
                    plugin.getLogger().warning("Ismeretlen config-fájl kihagyva a merge-ből: config/"
                            + file.getName() + " (csak a CONFIG_FILES lista töltődik be)");
                }
            }
        }

        plugin.reloadConfig();
        final Set<String> overridePaths = plugin.getConfig().getKeys(true).stream()
                .filter(key -> !plugin.getConfig().isConfigurationSection(key))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        mergeInto(merged, plugin.getConfig());

        final long previousGeneration = liveSnapshot.generation();
        final long nextGeneration = previousGeneration == Long.MAX_VALUE
                ? Long.MAX_VALUE : previousGeneration + 1L;
        liveSnapshot = new ConfigSnapshot(merged, overridePaths, nextGeneration);
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

    public ConfigSnapshot snapshot() {
        return liveSnapshot;
    }

    /** Returns null if not yet loaded. */
    public FileConfiguration getConfiguration() {
        return liveSnapshot.configuration();
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
