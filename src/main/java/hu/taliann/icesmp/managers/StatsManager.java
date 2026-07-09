package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.storage.PersistentStore;

import hu.taliann.icesmp.storage.YamlStore;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent player stats for the leaderboards (ROADMAP phase 7): best class
 * level, total wealth and raid kills per player. Level/wealth are snapshotted on
 * a periodic tick (Folia-safe, on each player's region thread); raid kills are
 * incremented from the kill event. Stored in leaderboard.yml.
 */
public final class StatsManager implements PersistentStore {

    /** A read-only leaderboard row. */
    public record Entry(UUID uuid, String name, int level, double wealth, int raidKills) { }

    public enum Category { LEVEL, WEALTH, RAID_KILLS }

    private static final class Stat {
        private String name = "?";
        private int level;
        private double wealth;
        private int raidKills;
    }

    private final JavaPlugin plugin;
    private final JobManager jobManager;
    private final CurrencyManager currencyManager;
    private final File storageFile;
    private final ConcurrentHashMap<UUID, Stat> stats = new ConcurrentHashMap<>();

    public StatsManager(final JavaPlugin plugin, final JobManager jobManager, final CurrencyManager currencyManager) {
        this.plugin = plugin;
        this.jobManager = jobManager;
        this.currencyManager = currencyManager;
        this.storageFile = new File(plugin.getDataFolder(), "leaderboard.yml");
        plugin.getDataFolder().mkdirs();
    }

    public void load() {
        stats.clear();
        if (!storageFile.exists()) {
            return;
        }

        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile);
        final ConfigurationSection section = yaml.getConfigurationSection("players");
        if (section == null) {
            return;
        }

        for (final String key : section.getKeys(false)) {
            try {
                final UUID id = UUID.fromString(key);
                final Stat stat = new Stat();
                stat.name = section.getString(key + ".name", "?");
                stat.level = section.getInt(key + ".level", 0);
                stat.wealth = section.getDouble(key + ".wealth", 0.0D);
                stat.raidKills = section.getInt(key + ".raid-kills", 0);
                stats.put(id, stat);
            } catch (final IllegalArgumentException ignored) {
                // Skip malformed UUID keys.
            }
        }
    }

    public void save() {
        try {
            final YamlConfiguration yaml = new YamlConfiguration();
            for (final var entry : stats.entrySet()) {
                final String base = "players." + entry.getKey();
                final Stat stat = entry.getValue();
                yaml.set(base + ".name", stat.name);
                yaml.set(base + ".level", stat.level);
                yaml.set(base + ".wealth", stat.wealth);
                yaml.set(base + ".raid-kills", stat.raidKills);
            }
            YamlStore.saveAtomic(storageFile, yaml);
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save leaderboard.yml: " + exception.getMessage());
        }
    }

    /** Records the player's current name, level and wealth (call on their region thread). */
    public void recordSnapshot(final Player player) {
        if (player == null) {
            return;
        }
        final Stat stat = stats.computeIfAbsent(player.getUniqueId(), key -> new Stat());
        stat.name = player.getName();
        stat.level = jobManager.getPrimaryLevel(player);
        stat.wealth = currencyManager.getBalance(player);
    }

    /** The player's recorded raid-kill count (0 if none). */
    public int getRaidKills(final UUID playerId) {
        final Stat stat = stats.get(playerId);
        return stat == null ? 0 : stat.raidKills;
    }

    /** Increments the player's raid-kill counter. */
    public void recordRaidKill(final Player player) {
        if (player == null) {
            return;
        }
        final Stat stat = stats.computeIfAbsent(player.getUniqueId(), key -> new Stat());
        stat.name = player.getName();
        stat.raidKills++;
    }

    /** Periodic snapshot of every online player (each on its own region thread). */
    public void tick() {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(plugin, task -> recordSnapshot(player), null);
        }
    }

    /**
     * Top players for a category, highest first.
     *
     * @param category the ranking category
     * @param limit max rows
     * @return ordered leaderboard rows
     */
    public List<Entry> top(final Category category, final int limit) {
        final Comparator<Stat> comparator = switch (category) {
            case LEVEL -> Comparator.comparingInt((Stat s) -> s.level);
            case WEALTH -> Comparator.comparingDouble((Stat s) -> s.wealth);
            case RAID_KILLS -> Comparator.comparingInt((Stat s) -> s.raidKills);
        };

        final List<Entry> rows = new ArrayList<>();
        stats.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(comparator.reversed()))
                .limit(Math.max(1, limit))
                .forEach(e -> rows.add(new Entry(e.getKey(), e.getValue().name, e.getValue().level,
                        e.getValue().wealth, e.getValue().raidKills)));
        return rows;
    }
}
