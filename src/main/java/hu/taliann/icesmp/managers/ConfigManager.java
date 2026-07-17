package hu.taliann.icesmp.managers;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

/**
 * Manager for loading and accessing configuration values.
 * Provides centralized access to configuration with fallback defaults.
 */
public final class ConfigManager {

    /** Bundled per-subsystem config files under config/ (extracted on first run). */
    private static final String[] CONFIG_FILES = {
            "general", "economy", "factions", "classes", "spells", "spells-balance",
            "professions", "quests", "world", "relics", "pets", "crafting", "crates", "afk", "moderation"
    };

    private final JavaPlugin plugin;
    // volatile: load()/reload() runs from the (admin command) thread that fires /icesmp reload, while
    // every manager reads this reference from arbitrary region threads — publish the reload safely.
    private volatile FileConfiguration configuration;

    /**
     * Constructs a new ConfigManager.
     *
     * @param plugin the plugin instance
     */
    public ConfigManager(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads configuration from the per-subsystem files in {@code config/} plus the optional
     * {@code config.yml} override, merging them into one keyspace so the existing
     * {@code getX("subsystem.key")} paths keep working unchanged. The per-subsystem files are the
     * defaults; {@code config.yml} is loaded LAST so an admin can override any key there.
     */
    public void load() {
        final YamlConfiguration merged = new YamlConfiguration();

        // Per-subsystem defaults: config/<subsystem>.yml. Extract the bundled set on first run,
        // then merge every .yml present (deterministic order).
        final File dir = new File(plugin.getDataFolder(), "config");
        dir.mkdirs();
        for (final String name : CONFIG_FILES) {
            if (!new File(dir, name + ".yml").exists()) {
                plugin.saveResource("config/" + name + ".yml", false);
            }
        }
        final File[] files = dir.listFiles((directory, fileName) -> fileName.endsWith(".yml"));
        if (files != null) {
            java.util.Arrays.sort(files);
            for (final File file : files) {
                mergeInto(merged, YamlConfiguration.loadConfiguration(file));
            }
        }

        // Optional main config.yml override (loaded last so its keys win).
        plugin.reloadConfig();
        mergeInto(merged, plugin.getConfig());

        this.configuration = merged;
    }

    /** Copies every leaf (non-section) key from {@code source} into {@code target}. */
    private void mergeInto(final YamlConfiguration target, final ConfigurationSection source) {
        for (final String key : source.getKeys(true)) {
            if (!source.isConfigurationSection(key)) {
                target.set(key, source.get(key));
            }
        }
    }

    public void reload() {
        load();
    }

    /**
     * Gets the raw configuration object.
     *
     * @return the FileConfiguration, or null if not loaded
     */
    public FileConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Gets a string configuration value with fallback.
     *
     * @param path the configuration path
     * @param fallback the fallback value if not found
     * @return the configuration value or fallback
     */
    public String getString(final String path, final String fallback) {
        if (configuration == null) {
            return fallback;
        }
        return configuration.getString(path, fallback);
    }

    /**
     * Gets an integer configuration value with fallback.
     *
     * @param path the configuration path
     * @param fallback the fallback value if not found
     * @return the configuration value or fallback
     */
    public int getInt(final String path, final int fallback) {
        if (configuration == null) {
            return fallback;
        }
        return configuration.getInt(path, fallback);
    }

    /**
     * Gets a long configuration value with fallback.
     *
     * @param path the configuration path
     * @param fallback the fallback value if not found
     * @return the configuration value or fallback
     */
    public long getLong(final String path, final long fallback) {
        if (configuration == null) {
            return fallback;
        }
        return configuration.getLong(path, fallback);
    }

    /**
     * Gets a double configuration value with fallback.
     *
     * @param path the configuration path
     * @param fallback the fallback value if not found
     * @return the configuration value or fallback
     */
    public double getDouble(final String path, final double fallback) {
        if (configuration == null) {
            return fallback;
        }
        return configuration.getDouble(path, fallback);
    }

    /**
     * Gets a boolean configuration value with fallback.
     *
     * @param path the configuration path
     * @param fallback the fallback value if not found
     * @return the configuration value or fallback
     */
    public boolean getBoolean(final String path, final boolean fallback) {
        if (configuration == null) {
            return fallback;
        }
        return configuration.getBoolean(path, fallback);
    }

    /**
     * Gets a list of strings from configuration.
     *
     * @param path the configuration path
     * @return the string list, or empty list if not found
     */
    public List<String> getStringList(final String path) {
        if (configuration == null) {
            return List.of();
        }
        return configuration.getStringList(path);
    }
}


