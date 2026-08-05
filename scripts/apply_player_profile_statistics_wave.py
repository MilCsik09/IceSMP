#!/usr/bin/env python3
"""Move player counters and leaderboard snapshots from leaderboard.yml to PlayerProfile."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def write_store() -> None:
    path = ROOT / "src/main/java/hu/taliann/icesmp/playerprofile/application/PlayerProfileStatisticsStore.java"
    path.write_text('''package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.domain.PlayerProfileSnapshot;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.StatisticsSection;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** CAS-backed durable player statistics and explicitly derived leaderboard snapshots. */
public final class PlayerProfileStatisticsStore {
    public static final String RAID_KILLS = "raid-kills";
    public static final String KILLS = "kills";
    public static final String DEATHS = "deaths";
    public static final String MOB_KILLS = "mob-kills";
    public static final String SPELL_CASTS = "spell-casts";
    public static final String QUESTS_COMPLETED = "quests-completed";
    private static final String LEVEL_SNAPSHOT = "leaderboard.level-snapshot";
    private static final String WEALTH_MILLI_SNAPSHOT = "leaderboard.wealth-milli-snapshot";
    private static final long SCALE = 1_000L;

    public long read(final UUID playerId, final String key) {
        return section(playerId).lifetime().getOrDefault(validateKey(key), 0L);
    }

    public CompletionStage<Long> increment(final UUID playerId, final String key) {
        final String normalized = validateKey(key);
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.STATISTICS, StatisticsSection.class, current -> {
                    final long before = current.lifetime().getOrDefault(normalized, 0L);
                    final long after = Math.addExact(before, 1L);
                    final LinkedHashMap<String, Long> lifetime = new LinkedHashMap<>(current.lifetime());
                    lifetime.put(normalized, after);
                    final StatisticsSection next = new StatisticsSection(lifetime,
                            current.season(), current.claimedMilestones(), current.extensions());
                    return PlayerProfileService.ConditionalMutation.changed(next, after);
                });
    }

    public CompletionStage<LeaderboardSnapshot> snapshot(final UUID playerId,
                                                          final int level,
                                                          final double wealth) {
        if (level < 0 || !Double.isFinite(wealth) || wealth < 0.0D) {
            throw new IllegalArgumentException("invalid leaderboard snapshot");
        }
        final long wealthMilli = Math.round(wealth * SCALE);
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.STATISTICS, StatisticsSection.class, current -> {
                    final long oldLevel = current.lifetime().getOrDefault(LEVEL_SNAPSHOT, 0L);
                    final long oldWealth = current.lifetime().getOrDefault(WEALTH_MILLI_SNAPSHOT, 0L);
                    final LeaderboardSnapshot result = new LeaderboardSnapshot(level, wealthMilli);
                    if (oldLevel == level && oldWealth == wealthMilli) {
                        return PlayerProfileService.ConditionalMutation.unchanged(result);
                    }
                    final LinkedHashMap<String, Long> lifetime = new LinkedHashMap<>(current.lifetime());
                    lifetime.put(LEVEL_SNAPSHOT, (long) level);
                    lifetime.put(WEALTH_MILLI_SNAPSHOT, wealthMilli);
                    final StatisticsSection next = new StatisticsSection(lifetime,
                            current.season(), current.claimedMilestones(), current.extensions());
                    return PlayerProfileService.ConditionalMutation.changed(next, result);
                });
    }

    public LeaderboardSnapshot leaderboard(final PlayerProfileSnapshot profile) {
        final Map<String, Long> values = profile.statistics().value().lifetime();
        return new LeaderboardSnapshot(
                Math.toIntExact(values.getOrDefault(LEVEL_SNAPSHOT, 0L)),
                values.getOrDefault(WEALTH_MILLI_SNAPSHOT, 0L));
    }

    public CounterSnapshot counters(final PlayerProfileSnapshot profile) {
        final Map<String, Long> values = profile.statistics().value().lifetime();
        return new CounterSnapshot(value(values, RAID_KILLS), value(values, KILLS),
                value(values, DEATHS), value(values, MOB_KILLS),
                value(values, SPELL_CASTS), value(values, QUESTS_COMPLETED));
    }

    private StatisticsSection section(final UUID playerId) {
        return PlayerProfileAuthority.current().requireSection(
                Objects.requireNonNull(playerId, "playerId"),
                ProfileSectionId.STATISTICS, StatisticsSection.class);
    }

    private static String validateKey(final String key) {
        if (!java.util.Set.of(RAID_KILLS, KILLS, DEATHS, MOB_KILLS,
                SPELL_CASTS, QUESTS_COMPLETED).contains(key)) {
            throw new IllegalArgumentException("unsupported statistics key: " + key);
        }
        return key;
    }

    private static int value(final Map<String, Long> source, final String key) {
        return Math.toIntExact(source.getOrDefault(key, 0L));
    }

    public record LeaderboardSnapshot(int level, long wealthMilli) {
        public LeaderboardSnapshot {
            if (level < 0 || wealthMilli < 0L) {
                throw new IllegalArgumentException("negative leaderboard snapshot");
            }
        }
        public double wealth() { return wealthMilli / (double) SCALE; }
    }

    public record CounterSnapshot(int raidKills, int kills, int deaths,
                                  int mobKills, int spellCasts, int questsCompleted) {
        public CounterSnapshot {
            if (raidKills < 0 || kills < 0 || deaths < 0 || mobKills < 0
                    || spellCasts < 0 || questsCompleted < 0) {
                throw new IllegalArgumentException("negative counter snapshot");
            }
        }
    }
}
''', encoding="utf-8")


def write_manager() -> None:
    path = ROOT / "src/main/java/hu/taliann/icesmp/managers/StatsManager.java"
    path.write_text('''package hu.taliann.icesmp.managers;

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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** PlayerProfile-backed counters with a rebuildable leaderboard projection. */
public final class StatsManager implements PersistentStore {

    public record Entry(UUID uuid, String name, int level, double wealth, int raidKills) { }
    public enum Category { LEVEL, WEALTH, RAID_KILLS }

    private record Derived(String name, int level, double wealth, int raidKills) { }

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
        return Math.toIntExact(store.read(playerId, PlayerProfileStatisticsStore.RAID_KILLS));
    }

    public void recordRaidKill(final Player player) {
        if (player != null) increment(player.getUniqueId(), PlayerProfileStatisticsStore.RAID_KILLS);
    }

    public void recordKill(final UUID playerId) { increment(playerId, PlayerProfileStatisticsStore.KILLS); }
    public void recordDeath(final UUID playerId) { increment(playerId, PlayerProfileStatisticsStore.DEATHS); }
    public void recordMobKill(final UUID playerId) { increment(playerId, PlayerProfileStatisticsStore.MOB_KILLS); }
    public void recordSpellCast(final UUID playerId) { increment(playerId, PlayerProfileStatisticsStore.SPELL_CASTS); }
    public void recordQuestComplete(final UUID playerId) { increment(playerId, PlayerProfileStatisticsStore.QUESTS_COMPLETED); }

    public int getKills(final UUID playerId) { return count(playerId, PlayerProfileStatisticsStore.KILLS); }
    public int getDeaths(final UUID playerId) { return count(playerId, PlayerProfileStatisticsStore.DEATHS); }
    public int getMobKills(final UUID playerId) { return count(playerId, PlayerProfileStatisticsStore.MOB_KILLS); }
    public int getSpellCasts(final UUID playerId) { return count(playerId, PlayerProfileStatisticsStore.SPELL_CASTS); }
    public int getQuestsCompleted(final UUID playerId) { return count(playerId, PlayerProfileStatisticsStore.QUESTS_COMPLETED); }

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

    private int count(final UUID playerId, final String key) {
        return playerId == null ? 0 : Math.toIntExact(store.read(playerId, key));
    }

    private void refresh(final UUID playerId) {
        PlayerProfileAuthority.current().repository().find(playerId)
                .thenAccept(optional -> optional.ifPresent(profile -> {
                    final var board = store.leaderboard(profile);
                    final var counters = store.counters(profile);
                    final String name = profile.identity().value().lastKnownName();
                    leaderboard.put(playerId, new Derived(name, board.level(),
                            board.wealth(), counters.raidKills()));
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
''', encoding="utf-8")


def write_regression() -> None:
    path = ROOT / "src/regression/java/hu/taliann/icesmp/playerprofile/application/PlayerProfileStatisticsStoreRegressionSuite.java"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text('''package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.persistence.YamlPlayerProfileRepository;
import hu.taliann.icesmp.playerprofile.transaction.YamlPlayerProfileTransactionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.UUID;

/** Counter CAS and leaderboard snapshot regressions. */
public final class PlayerProfileStatisticsStoreRegressionSuite {
    private static int assertions;

    private PlayerProfileStatisticsStoreRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        final Path root = Files.createTempDirectory("player-profile-statistics-");
        final YamlPlayerProfileRepository repository = new YamlPlayerProfileRepository(root);
        final YamlPlayerProfileTransactionManager transactions =
                new YamlPlayerProfileTransactionManager(repository);
        final PlayerProfileService service = new PlayerProfileService(repository, transactions);
        final PlayerProfileAuthority authority = PlayerProfileAuthority.install(
                service, repository, transactions);
        try {
            final UUID player = UUID.fromString("00000000-0000-0000-0000-000000001087");
            repository.loadSnapshot(player).toCompletableFuture().join();
            final PlayerProfileStatisticsStore store = new PlayerProfileStatisticsStore();
            check(store.increment(player, PlayerProfileStatisticsStore.KILLS)
                    .toCompletableFuture().join() == 1L, "first increment");
            check(store.increment(player, PlayerProfileStatisticsStore.KILLS)
                    .toCompletableFuture().join() == 2L, "second increment");
            store.increment(player, PlayerProfileStatisticsStore.RAID_KILLS)
                    .toCompletableFuture().join();
            final var first = store.snapshot(player, 17, 125.75D)
                    .toCompletableFuture().join();
            check(first.level() == 17 && first.wealthMilli() == 125_750L,
                    "snapshot scaled exactly");
            final long revision = repository.cached(player).orElseThrow()
                    .statistics().revision();
            store.snapshot(player, 17, 125.75D).toCompletableFuture().join();
            check(repository.cached(player).orElseThrow().statistics().revision() == revision,
                    "identical snapshot is no-op");
            repository.invalidate(player);
            final var durable = repository.loadSnapshot(player).toCompletableFuture().join();
            check(store.counters(durable).kills() == 2, "counter restart durable");
            check(store.counters(durable).raidKills() == 1, "raid restart durable");
            check(store.leaderboard(durable).level() == 17, "level restart durable");
            check(Math.abs(store.leaderboard(durable).wealth() - 125.75D) < 0.0001D,
                    "wealth restart durable");
            expect(IllegalArgumentException.class, () -> store.increment(player, "unknown"));
            check(service.shutdown(Duration.ofSeconds(5)).toCompletableFuture().join().drained(),
                    "repository drained");
        } finally {
            authority.uninstall();
            try (var paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); }
                    catch (final Exception ignored) { }
                });
            }
        }
        System.out.println("PlayerProfile statistics regression suite passed. assertions=" + assertions);
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void expect(final Class<? extends Throwable> expected,
                               final Throwing action) {
        assertions++;
        try {
            action.run();
            throw new AssertionError("Expected " + expected.getSimpleName());
        } catch (final Throwable failure) {
            if (!expected.isInstance(failure)) {
                throw new AssertionError("Expected " + expected.getSimpleName()
                        + " but got " + failure, failure);
            }
        }
    }

    @FunctionalInterface private interface Throwing { void run() throws Exception; }
}
''', encoding="utf-8")


def patch_gradle() -> None:
    path = ROOT / "build.gradle.kts"
    text = path.read_text(encoding="utf-8")
    task = '''val playerProfileStatisticsRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs PlayerProfile statistics CAS and leaderboard snapshot regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.playerprofile.application.PlayerProfileStatisticsStoreRegressionSuite")
}

'''
    anchor = 'val playerProfileYamlRegressionTest by tasks.registering(JavaExec::class) {'
    if task not in text:
        if text.count(anchor) != 1:
            raise RuntimeError(f"statistics task anchor count={text.count(anchor)}")
        text = text.replace(anchor, task + anchor, 1)
    old = '''        playerProfileRepositoryEnumerationRegressionTest, playerProfileYamlRegressionTest,
        playerProfileTransactionRegressionTest,
'''
    new = '''        playerProfileRepositoryEnumerationRegressionTest, playerProfileStatisticsRegressionTest,
        playerProfileYamlRegressionTest, playerProfileTransactionRegressionTest,
'''
    if new not in text:
        if text.count(old) != 1:
            raise RuntimeError(f"statistics dependency anchor count={text.count(old)}")
        text = text.replace(old, new, 1)
    path.write_text(text, encoding="utf-8")


def main() -> int:
    write_store()
    write_manager()
    write_regression()
    patch_gradle()
    print("PlayerProfile statistics authority wave applied.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
