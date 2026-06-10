package hu.taliann.icesmp.managers;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Manager for loading and accessing configuration values.
 * Provides centralized access to configuration with fallback defaults.
 */
public final class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration configuration;

    /**
     * Constructs a new ConfigManager.
     *
     * @param plugin the plugin instance
     */
    public ConfigManager(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads the configuration from disk.
     */
    public void load() {
        plugin.reloadConfig();
        configuration = plugin.getConfig();
        configuration.options().copyDefaults(true);
        plugin.saveConfig();
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


