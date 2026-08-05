package hu.taliann.icesmp.playerprofile.application;

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
