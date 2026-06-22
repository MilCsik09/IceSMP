package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.storage.PersistentStore;

import hu.taliann.icesmp.storage.YamlStore;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Seasonal league (ideas.md "Szezonális liga"): factions earn points from raid
 * victories and world boss kills over a configurable season. When the season
 * ends, the leading faction is crowned champion and its treasury receives the
 * season reward; points reset and a new season begins. State persists to
 * season.yml; expiry is checked on the global world-events tick.
 */
public final class SeasonManager implements PersistentStore {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final FactionTreasuryManager treasuryManager;
    private final File storageFile;
    private final Map<FactionType, Integer> points = new ConcurrentHashMap<>();

    private volatile long seasonStart = System.currentTimeMillis();

    public SeasonManager(final JavaPlugin plugin, final ConfigManager configManager,
                         final MessageManager messageManager, final FactionTreasuryManager treasuryManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.treasuryManager = treasuryManager;
        this.storageFile = new File(plugin.getDataFolder(), "season.yml");
        plugin.getDataFolder().mkdirs();
    }

    public void load() {
        points.clear();
        seasonStart = System.currentTimeMillis();

        if (!storageFile.exists()) {
            save();
            return;
        }

        try {
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile);
            seasonStart = yaml.getLong("season.start", System.currentTimeMillis());
            final ConfigurationSection pointsSection = yaml.getConfigurationSection("season.points");
            if (pointsSection != null) {
                for (final String factionKey : pointsSection.getKeys(false)) {
                    final FactionType faction = FactionType.fromInput(factionKey);
                    if (faction != null) {
                        points.put(faction, Math.max(0, pointsSection.getInt(factionKey, 0)));
                    }
                }
            }
        } catch (final Exception exception) {
            plugin.getLogger().severe("Failed to load season.yml: " + exception.getMessage());
        }
    }

    public void save() {
        try {
            final YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("season.start", seasonStart);
            for (final Map.Entry<FactionType, Integer> entry : points.entrySet()) {
                yaml.set("season.points." + entry.getKey().name(), entry.getValue());
            }

            YamlStore.saveAtomic(storageFile, yaml);
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save season.yml: " + exception.getMessage());
        }
    }

    public int getPoints(final FactionType faction) {
        return faction == null ? 0 : points.getOrDefault(faction, 0);
    }

    public long getSeasonEndMillis() {
        final long lengthDays = Math.max(1L, configManager.getLong("world-events.season.length-days", 60L));
        return seasonStart + (lengthDays * 24L * 60L * 60L * 1000L);
    }

    /**
     * Awards league points to a faction (raid victory, world boss kill...).
     *
     * @param faction the scoring faction
     * @param amount the points
     */
    public void addPoints(final FactionType faction, final int amount) {
        if (faction == null || amount <= 0
                || !configManager.getBoolean("world-events.season.enabled", true)) {
            return;
        }

        points.merge(faction, amount, Integer::sum);
        save();
    }

    /** Periodic check on the global world-events tick: closes expired seasons. */
    public void tick() {
        if (!configManager.getBoolean("world-events.season.enabled", true)
                || System.currentTimeMillis() < getSeasonEndMillis()) {
            return;
        }

        FactionType champion = null;
        int best = 0;
        boolean tie = false;
        for (final Map.Entry<FactionType, Integer> entry : points.entrySet()) {
            if (entry.getValue() > best) {
                champion = entry.getKey();
                best = entry.getValue();
                tie = false;
            } else if (entry.getValue() == best && best > 0) {
                tie = true;
            }
        }

        if (champion == null || tie || best <= 0) {
            Bukkit.getServer().broadcast(messageManager.getMessage(
                    "season-ended-no-champion",
                    "<gold>🏁 A szezon véget ért bajnok nélkül — új szezon kezdődik!</gold>"
            ));
        } else {
            final double reward = Math.max(0.0D, configManager.getDouble("world-events.season.treasury-reward", 1000.0D));
            if (reward > 0.0D) {
                treasuryManager.deposit(champion, reward);
            }

            Bukkit.getServer().broadcast(messageManager.getMessage(
                    "season-ended",
                    "<gold>🏆 A szezon bajnoka: <white>{champion}</white> ({points} pont)! A frakciókassza <white>{reward}</white> jutalmat kap. Új szezon kezdődik!</gold>",
                    Map.of(
                            "champion", champion.getDisplayName(),
                            "points", String.valueOf(best),
                            "reward", String.valueOf(reward)
                    )
            ));
        }

        points.clear();
        seasonStart = System.currentTimeMillis();
        save();
    }
}
