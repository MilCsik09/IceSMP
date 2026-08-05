package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.persistence.YamlPlayerProfileRepository;
import hu.taliann.icesmp.playerprofile.transaction.YamlPlayerProfileTransactionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.UUID;

/** Achievement, bestiary, reward-receipt and hidden-spot CAS regressions. */
public final class PlayerProfileAchievementStoreRegressionSuite {
    private static int assertions;
    private PlayerProfileAchievementStoreRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        final Path root = Files.createTempDirectory("player-profile-achievement-");
        final YamlPlayerProfileRepository repository = new YamlPlayerProfileRepository(root);
        final YamlPlayerProfileTransactionManager transactions =
                new YamlPlayerProfileTransactionManager(repository);
        final PlayerProfileService service = new PlayerProfileService(repository, transactions);
        final PlayerProfileAuthority authority = PlayerProfileAuthority.install(
                service, repository, transactions);
        try {
            final UUID player = UUID.fromString("00000000-0000-0000-0000-000000001089");
            repository.loadSnapshot(player).toCompletableFuture().join();
            final PlayerProfileAchievementStore store = new PlayerProfileAchievementStore();

            check(store.unlock(player, "first_steps").toCompletableFuture().join(),
                    "first unlock committed");
            check(!store.unlock(player, "first_steps").toCompletableFuture().join(),
                    "duplicate unlock rejected");
            check(store.isUnlocked(player, "first_steps"), "unlock readable");
            check(store.reserveReward(player, "achievement:first_steps")
                    .toCompletableFuture().join(), "first reward receipt committed");
            check(!store.reserveReward(player, "achievement:first_steps")
                    .toCompletableFuture().join(), "duplicate reward receipt rejected");

            final var first = store.recordBestiary(player, "mobs", "zombie")
                    .toCompletableFuture().join();
            final var duplicate = store.recordBestiary(player, "mobs", "zombie")
                    .toCompletableFuture().join();
            final var second = store.recordBestiary(player, "mobs", "skeleton")
                    .toCompletableFuture().join();
            check(first.created() && first.categoryCount() == 1, "first bestiary record");
            check(!duplicate.created() && duplicate.categoryCount() == 1,
                    "duplicate bestiary record rejected");
            check(second.created() && second.categoryCount() == 2,
                    "second bestiary count");
            check(store.bestiaryEntries(player, "mobs")
                    .equals(java.util.Set.of("zombie", "skeleton")),
                    "bestiary entries readable");

            check(store.markHiddenSpotVisited(player, "frozen_cave")
                    .toCompletableFuture().join(), "hidden spot first visit");
            check(!store.markHiddenSpotVisited(player, "frozen_cave")
                    .toCompletableFuture().join(), "hidden spot duplicate rejected");

            repository.invalidate(player);
            repository.loadSnapshot(player).toCompletableFuture().join();
            check(store.isUnlocked(player, "first_steps"), "unlock restart durable");
            check(store.rewardReserved(player, "achievement:first_steps"),
                    "reward receipt restart durable");
            check(store.hasVisitedHiddenSpot(player, "frozen_cave"),
                    "hidden spot restart durable");
            check(store.bestiaryEntries(player, "mobs").size() == 2,
                    "bestiary restart durable");
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
        System.out.println("PlayerProfile achievement regression suite passed. assertions=" + assertions);
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
