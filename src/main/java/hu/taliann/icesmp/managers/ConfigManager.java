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
 * Provides centralized access to configuration with fallback defaults.
 */
public final class ConfigManager {

    /** Bundled per-subsystem config files under config/ (extracted on first run).
     * AUDIT-JAVÍTÁS: a lista korábban 6 fájlt kihagyott (item-rarity, loot, motd,
     * profession-materials, profession-recipes, tablist) — friss telepítésen ezek sosem
     * csomagolódtak ki, így a rájuk épülő rendszerek némán a kód-defaultokra estek
     * (pl. NULLA szakma-recept). Új fájl hozzáadásakor ide is fel KELL venni. */
    private static final String[] CONFIG_FILES = {
            "general", "economy", "factions", "classes", "spells", "spells-balance",
            "professions", "quests", "world", "relics", "pets", "crafting", "crates", "afk", "moderation",
            "item-rarity", "loot", "motd", "profession-materials", "profession-recipes", "sit", "tablist", "dev-items"
    };

    private final JavaPlugin plugin;
    // volatile: load()/reload() runs from the (admin command) thread that fires /icesmp reload, while
    // every manager reads this reference from arbitrary region threads — publish the reload safely.
    private volatile FileConfiguration configuration;
    private volatile Set<String> overridePaths = Set.of();

    public ConfigManager(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads configuration from the per-subsystem files in {@code config/} plus the optional
     * {@code config.yml} override, merging them into one keyspace so the existing
     * {@code getX("subsystem.key")} paths keep working unchanged. The per-subsystem files are the
     * defaults; {@code config.yml} is loaded LAST so an admin can override any key there.
     */
    public synchronized void load() {
        final YamlConfiguration merged = new YamlConfiguration();

        // Per-subsystem defaults: config/<subsystem>.yml. Extract and merge only the supported
        // CONFIG_FILES allowlist; unrelated backups and editor files must never enter runtime config.
        final File dir = new File(plugin.getDataFolder(), "config");
        dir.mkdirs();
        for (final String name : CONFIG_FILES) {
            if (!new File(dir, name + ".yml").exists()) {
                plugin.saveResource("config/" + name + ".yml", false);
            }
        }
        // Csak az allowlist töltődik be: egy bemásolt backup/szerkesztői mentés (.yml) különben
        // észrevétlenül felülírna élő kulcsokat az alfabetikus merge-sorrend szerint.
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

        // Optional main config.yml override (loaded last so its keys win).
        plugin.reloadConfig();
        this.overridePaths = plugin.getConfig().getKeys(true).stream()
                .filter(key -> !plugin.getConfig().isConfigurationSection(key))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
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
     * Az ingame override-írás EGYETLEN, szerializált útja (GUI-kattintás + /icesmp config
     * set|unset — Folián mindkettő a hívó játékos régió-szálán fut, akár egyszerre többen).
     * A plugin megosztott {@code getConfig()} objektumán a set+save+reload hármas
     * szinkronizáció nélkül elveszíthetné az egyik admin módosítását (a reloadConfig a
     * lemezről cseréli le a memóriabeli, még mentetlen példányt), ezért:
     * (1) friss lemez-állapot betöltése, (2) set, (3) mentés, (4) merge-újratöltés —
     * egyetlen monitor alatt. {@code value == null} = a kulcs törlése (unset).
     *
     * @return unsetnél true, ha a kulcs létezett; setnél mindig true
     */
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

    /** Returns null if not yet loaded. */
    public FileConfiguration getConfiguration() {
        return configuration;
    }

    /** Whether the merged live configuration contains an explicit value at this path. */
    public boolean contains(final String path) {
        return configuration != null && configuration.isSet(path);
    }

    /** Whether config.yml itself explicitly overrides this path (not merely a bundled default). */
    public boolean hasOverride(final String path) {
        return overridePaths.contains(path);
    }

    public String getString(final String path, final String fallback) {
        if (configuration == null) {
            return fallback;
        }
        return configuration.getString(path, fallback);
    }

    public int getInt(final String path, final int fallback) {
        if (configuration == null) {
            return fallback;
        }
        return configuration.getInt(path, fallback);
    }

    public long getLong(final String path, final long fallback) {
        if (configuration == null) {
            return fallback;
        }
        return configuration.getLong(path, fallback);
    }

    public double getDouble(final String path, final double fallback) {
        if (configuration == null) {
            return fallback;
        }
        return configuration.getDouble(path, fallback);
    }

    public boolean getBoolean(final String path, final boolean fallback) {
        if (configuration == null) {
            return fallback;
        }
        return configuration.getBoolean(path, fallback);
    }

    public List<String> getStringList(final String path) {
        if (configuration == null) {
            return List.of();
        }
        return configuration.getStringList(path);
    }

    public List<Double> getDoubleList(final String path) {
        if (configuration == null) {
            return List.of();
        }
        return configuration.getDoubleList(path);
    }
}
