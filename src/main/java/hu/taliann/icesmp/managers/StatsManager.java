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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Persistent player stats for the leaderboards: best class
 * level, total wealth and raid kills per player. Level/wealth are snapshotted on
 * a periodic tick (Folia-safe, on each player's region thread); raid kills are
 * incremented from the kill event. Stored in leaderboard.yml.
 *
 * <p>This also tracks the {@code /stats} profile counters (kills,
 * deaths, mob kills, spell casts, quests completed). Those counters are
 * incremented from arbitrary region threads (e.g. a kill recorded from the
 * victim's death-event thread), so they use {@link AtomicInteger} fields
 * rather than plain {@code int}s.
 */
public final class StatsManager implements PersistentStore {

    /** A read-only leaderboard row. */
    public record Entry(UUID uuid, String name, int level, double wealth, int raidKills) { }

    public enum Category { LEVEL, WEALTH, RAID_KILLS }

    private static final class Stat {
        // Minden mezőt több régió-szál ír/olvas (ranglista-frissítés, raid-ölés, /stats):
        // a ranglista-mezők volatile-ok (csak felülírás), a SZÁMLÁLÓK atomiak — a raidKills
        // korábban sima int++ volt, ami konkurens raid-ölésnél elveszíthetett ölést.
        private volatile String name = "?";
        private volatile int level;
        private volatile double wealth;
        private final AtomicInteger raidKills = new AtomicInteger();
        // /stats profile counters — written from arbitrary region threads.
        private final AtomicInteger kills = new AtomicInteger();
        private final AtomicInteger deaths = new AtomicInteger();
        private final AtomicInteger mobKills = new AtomicInteger();
        private final AtomicInteger spellCasts = new AtomicInteger();
        private final AtomicInteger questsCompleted = new AtomicInteger();
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

        final YamlConfiguration yaml = hu.taliann.icesmp.storage.YamlStore.loadTracked(storageFile, plugin.getLogger());
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
                stat.raidKills.set(section.getInt(key + ".raid-kills", 0));
                stat.kills.set(section.getInt(key + ".kills", 0));
                stat.deaths.set(section.getInt(key + ".deaths", 0));
                stat.mobKills.set(section.getInt(key + ".mob-kills", 0));
                stat.spellCasts.set(section.getInt(key + ".spell-casts", 0));
                stat.questsCompleted.set(section.getInt(key + ".quests-completed", 0));
                stats.put(id, stat);
            } catch (final IllegalArgumentException ignored) {
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
                yaml.set(base + ".raid-kills", stat.raidKills.get());
                yaml.set(base + ".kills", stat.kills.get());
                yaml.set(base + ".deaths", stat.deaths.get());
                yaml.set(base + ".mob-kills", stat.mobKills.get());
                yaml.set(base + ".spell-casts", stat.spellCasts.get());
                yaml.set(base + ".quests-completed", stat.questsCompleted.get());
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
        stat.wealth = currencyManager.getTotalBalance(player);
    }

    /** The player's recorded raid-kill count (0 if none). */
    public int getRaidKills(final UUID playerId) {
        final Stat stat = stats.get(playerId);
        return stat == null ? 0 : stat.raidKills.get();
    }

    public void recordRaidKill(final Player player) {
        if (player == null) {
            return;
        }
        final Stat stat = stats.computeIfAbsent(player.getUniqueId(), key -> new Stat());
        stat.name = player.getName();
        stat.raidKills.incrementAndGet();
    }

    // ===== /stats profil-számlálók =====
    //
    // Ezek a metódusok KIZÁRÓLAG konkurens (atomikus) map-műveletek — nem
    // olvasnak/írnak semmilyen entitást, ezért bármely régió-szálról hívhatók
    // scheduler-hop nélkül (pl. az áldozat szálán a gyilkos UUID-jére).

    /** Increments the player's (player-kill) counter. Hot-path-safe, no entity access. */
    public void recordKill(final UUID playerId) {
        if (playerId == null) {
            return;
        }
        stats.computeIfAbsent(playerId, key -> new Stat()).kills.incrementAndGet();
    }

    /** Increments the player's death counter. Hot-path-safe, no entity access. */
    public void recordDeath(final UUID playerId) {
        if (playerId == null) {
            return;
        }
        stats.computeIfAbsent(playerId, key -> new Stat()).deaths.incrementAndGet();
    }

    /** Increments the player's mob-kill counter. Hot-path-safe, no entity access. */
    public void recordMobKill(final UUID playerId) {
        if (playerId == null) {
            return;
        }
        stats.computeIfAbsent(playerId, key -> new Stat()).mobKills.incrementAndGet();
    }

    /** Increments the player's spell-cast counter. Hot-path-safe, no entity access. */
    public void recordSpellCast(final UUID playerId) {
        if (playerId == null) {
            return;
        }
        stats.computeIfAbsent(playerId, key -> new Stat()).spellCasts.incrementAndGet();
    }

    /** Increments the player's completed-quest counter. Hot-path-safe, no entity access. */
    public void recordQuestComplete(final UUID playerId) {
        if (playerId == null) {
            return;
        }
        stats.computeIfAbsent(playerId, key -> new Stat()).questsCompleted.incrementAndGet();
    }

    /** The player's recorded player-kill count (0 if none). */
    public int getKills(final UUID playerId) {
        final Stat stat = stats.get(playerId);
        return stat == null ? 0 : stat.kills.get();
    }

    /** The player's recorded death count (0 if none). */
    public int getDeaths(final UUID playerId) {
        final Stat stat = stats.get(playerId);
        return stat == null ? 0 : stat.deaths.get();
    }

    /** The player's recorded mob-kill count (0 if none). */
    public int getMobKills(final UUID playerId) {
        final Stat stat = stats.get(playerId);
        return stat == null ? 0 : stat.mobKills.get();
    }

    /** The player's recorded spell-cast count (0 if none). */
    public int getSpellCasts(final UUID playerId) {
        final Stat stat = stats.get(playerId);
        return stat == null ? 0 : stat.spellCasts.get();
    }

    /** The player's recorded completed-quest count (0 if none). */
    public int getQuestsCompleted(final UUID playerId) {
        final Stat stat = stats.get(playerId);
        return stat == null ? 0 : stat.questsCompleted.get();
    }

    /**
     * Resolves a stored player id by name for offline lookups (e.g. {@code /stats <név>}
     * for a player who is not currently online). Case-insensitive; returns null if the
     * name was never recorded in the leaderboard store.
     */
    public UUID findPlayerIdByName(final String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (final var entry : stats.entrySet()) {
            if (entry.getValue().name.equalsIgnoreCase(name)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** The stored display name for a player, or {@code fallback} if none is recorded. */
    public String getStoredName(final UUID playerId, final String fallback) {
        final Stat stat = stats.get(playerId);
        return stat == null || stat.name == null || "?".equals(stat.name) ? fallback : stat.name;
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
            case RAID_KILLS -> Comparator.comparingInt((Stat s) -> s.raidKills.get());
        };

        final List<Entry> rows = new ArrayList<>();
        stats.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(comparator.reversed()))
                .limit(Math.max(1, limit))
                .forEach(e -> rows.add(new Entry(e.getKey(), e.getValue().name, e.getValue().level,
                        e.getValue().wealth, e.getValue().raidKills.get())));
        return rows;
    }
}
