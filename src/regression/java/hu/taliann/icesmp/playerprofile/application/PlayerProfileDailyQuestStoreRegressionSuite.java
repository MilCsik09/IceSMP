package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.persistence.YamlPlayerProfileRepository;
import hu.taliann.icesmp.playerprofile.transaction.YamlPlayerProfileTransactionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.UUID;

/** Daily/weekly period, streak, completion and receipt regressions. */
public final class PlayerProfileDailyQuestStoreRegressionSuite {
    private static int assertions;
    private PlayerProfileDailyQuestStoreRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        final Path root = Files.createTempDirectory("player-profile-daily-quest-");
        final YamlPlayerProfileRepository repository = new YamlPlayerProfileRepository(root);
        final YamlPlayerProfileTransactionManager transactions =
                new YamlPlayerProfileTransactionManager(repository);
        final PlayerProfileService service = new PlayerProfileService(repository, transactions);
        final PlayerProfileAuthority authority = PlayerProfileAuthority.install(
                service, repository, transactions);
        try {
            final UUID player = UUID.fromString("00000000-0000-0000-0000-000000001090");
            repository.loadSnapshot(player).toCompletableFuture().join();
            final PlayerProfileDailyQuestStore store = new PlayerProfileDailyQuestStore();

            check(store.state(player, false, 100L).progress() == 0,
                    "greenfield daily progress");
            check(store.advanceDaily(player, 100L, 2).toCompletableFuture().join().progress() == 1,
                    "daily first progress");
            final var completed = store.advanceDaily(player, 100L, 2)
                    .toCompletableFuture().join();
            check(completed.completedNow() && completed.streak() == 1,
                    "daily completion and first streak");
            final long revision = repository.cached(player).orElseThrow().quests().revision();
            check(!store.advanceDaily(player, 100L, 2).toCompletableFuture().join().completedNow(),
                    "daily duplicate completion rejected");
            check(repository.cached(player).orElseThrow().quests().revision() == revision,
                    "daily duplicate is no-op");
            check(store.state(player, false, 101L).progress() == 0,
                    "new day reads fresh state");
            store.advanceDaily(player, 101L, 1).toCompletableFuture().join();
            check(store.streak(player) == 2, "consecutive day extends streak");
            store.advanceDaily(player, 103L, 1).toCompletableFuture().join();
            check(store.streak(player) == 1, "day gap resets streak");

            check(store.advanceWeekly(player, 20L, 2).toCompletableFuture().join().progress() == 1,
                    "weekly first progress");
            check(store.advanceWeekly(player, 20L, 2).toCompletableFuture().join().completedNow(),
                    "weekly completion");
            check(!store.advanceWeekly(player, 20L, 2).toCompletableFuture().join().completedNow(),
                    "weekly duplicate rejected");
            check(store.state(player, true, 21L).progress() == 0,
                    "new week reads fresh state");

            repository.invalidate(player);
            repository.loadSnapshot(player).toCompletableFuture().join();
            check(store.streak(player) == 1, "streak restart durable");
            check(store.state(player, true, 20L).done(), "weekly receipt restart durable");
            check(service.shutdown(Duration.ofSeconds(5)).toCompletableFuture().join().drained(),
                    "repository drained");
        } finally {
            authority.uninstall();
            try (var paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (final Exception ignored) { }
                });
            }
        }
        System.out.println("PlayerProfile daily quest regression suite passed. assertions=" + assertions);
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
