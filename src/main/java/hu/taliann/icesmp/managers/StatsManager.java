package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.playerprofile.application.PlayerProfileAuthority;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileStatisticsStore;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.storage.PersistentStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** PlayerProfile-backed counters with a rebuildable leaderboard projection. */
public final class StatsManager implements PersistentStore {

    public record Entry(UUID uuid, String name, int level, double wealth, int raidKills) { }
    public enum Category { LEVEL, WEALTH, RAID_KILLS }

    private record Derived(String name, int level, double wealth, int raidKills,
                           int kills, int deaths, int mobKills,
                           int spellCasts, int questsCompleted) { }

    private final JavaPlugin plugin;
    private final JobManager jobManager;
    private final CurrencyManager currencyManager;
    private final PlayerProfileStatisticsStore store = new PlayerProfileStatisticsStore();
    private final ConcurrentHashMap<UUID, Derived> leaderboard = new ConcurrentHashMap<>();
    private volatile AutoCloseable subscription;

    public StatsManager(final JavaPlugin plugin, final JobManager jobManager,
                        final CurrencyManager currencyManager) {
        this.plugin = Objects.requireNonNull(plugin);
        this.jobManager = Objects.requireNonNull(jobManager);
        this.currencyManager = Objects.requireNonNull(currencyManager);
    }

    /** Builds only a derived projection; no second persistent store is loaded. */
    @Override public void load() {
        if (subscription == null) {
            subscription = PlayerProfileAuthority.current().service().subscribe(
                    (playerId, revision, changed) -> {
                        if (changed.contains(ProfileSectionId.IDENTITY)
                                || changed.contains(ProfileSectionId.STATISTICS)) {
                            refresh(playerId);
                        }
                    });
        }
        PlayerProfileAuthority.current().repository().listPlayerIds()
                .thenAccept(ids -> ids.forEach(this::refresh))
                .exceptionally(failure -> {
                    plugin.getLogger().severe("PlayerProfile leaderboard rebuild failed: "
                            + failure.getMessage());
                    return null;
                });
    }

    /** Every mutation is already durable in PlayerProfile. */
    @Override public void save() { }

    public void recordSnapshot(final Player player) {
        if (player == null) return;
        store.snapshot(player.getUniqueId(), jobManager.getPrimaryLevel(player),
                        currencyManager.getTotalBalance(player))
                .whenComplete((snapshot, failure) -> {
                    if (failure != null) logFailure("leaderboard snapshot", player.getUniqueId(), failure);
                });
    }

    public int getRaidKills(final UUID playerId) {
        final Derived state = leaderboard.get(playerId);
        return state == null ? 0 : state.raidKills();
    }

    public void recordRaidKill(final Player player) {
        if (player != null) increment(player.getUniqueId(), PlayerProfileStatisticsStore.RAID_KILLS);
    }

    public void recordKill(final UUID playerId) { increment(playerId, PlayerProfileStatisticsStore.KILLS); }
    public void recordDeath(final UUID playerId) { increment(playerId, PlayerProfileStatisticsStore.DEATHS); }
    public void recordMobKill(final UUID playerId) { increment(playerId, PlayerProfileStatisticsStore.MOB_KILLS); }
    public void recordSpellCast(final UUID playerId) { increment(playerId, PlayerProfileStatisticsStore.SPELL_CASTS); }
    public void recordQuestComplete(final UUID playerId) { increment(playerId, PlayerProfileStatisticsStore.QUESTS_COMPLETED); }

    public int getKills(final UUID playerId) { return projected(playerId, Derived::kills); }
    public int getDeaths(final UUID playerId) { return projected(playerId, Derived::deaths); }
    public int getMobKills(final UUID playerId) { return projected(playerId, Derived::mobKills); }
    public int getSpellCasts(final UUID playerId) { return projected(playerId, Derived::spellCasts); }
    public int getQuestsCompleted(final UUID playerId) { return projected(playerId, Derived::questsCompleted); }

    public UUID findPlayerIdByName(final String name) {
        if (name == null || name.isBlank()) return null;
        return leaderboard.entrySet().stream()
                .filter(entry -> entry.getValue().name().equalsIgnoreCase(name))
                .map(Map.Entry::getKey).findFirst().orElse(null);
    }

    public String getStoredName(final UUID playerId, final String fallback) {
        final Derived state = leaderboard.get(playerId);
        return state == null || state.name().isBlank() ? fallback : state.name();
    }

    public void tick() {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(plugin, task -> recordSnapshot(player), null);
        }
    }

    public List<Entry> top(final Category category, final int limit) {
        final Comparator<Derived> comparator = switch (category) {
            case LEVEL -> Comparator.comparingInt(Derived::level);
            case WEALTH -> Comparator.comparingDouble(Derived::wealth);
            case RAID_KILLS -> Comparator.comparingInt(Derived::raidKills);
        };
        final List<Entry> rows = new ArrayList<>();
        leaderboard.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(comparator.reversed()))
                .limit(Math.max(1, limit))
                .forEach(entry -> rows.add(new Entry(entry.getKey(), entry.getValue().name(),
                        entry.getValue().level(), entry.getValue().wealth(),
                        entry.getValue().raidKills())));
        return rows;
    }

    private void increment(final UUID playerId, final String key) {
        if (playerId == null) return;
        store.increment(playerId, key).whenComplete((value, failure) -> {
            if (failure != null) logFailure(key, playerId, failure);
        });
    }

    private int projected(final UUID playerId,
                          final java.util.function.ToIntFunction<Derived> value) {
        if (playerId == null) return 0;
        final Derived state = leaderboard.get(playerId);
        return state == null ? 0 : value.applyAsInt(state);
    }

    private void refresh(final UUID playerId) {
        PlayerProfileAuthority.current().repository().find(playerId)
                .thenAccept(optional -> optional.ifPresent(profile -> {
                    final var board = store.leaderboard(profile);
                    final var counters = store.counters(profile);
                    final String name = profile.identity().value().lastKnownName();
                    leaderboard.put(playerId, new Derived(name, board.level(), board.wealth(),
                            counters.raidKills(), counters.kills(), counters.deaths(),
                            counters.mobKills(), counters.spellCasts(), counters.questsCompleted()));
                })).exceptionally(failure -> {
                    logFailure("leaderboard refresh", playerId, failure);
                    return null;
                });
    }

    private void logFailure(final String operation, final UUID playerId,
                            final Throwable failure) {
        plugin.getLogger().severe("PlayerProfile statistics " + operation + " failed for "
                + playerId + ": " + failure.getMessage());
    }
}
