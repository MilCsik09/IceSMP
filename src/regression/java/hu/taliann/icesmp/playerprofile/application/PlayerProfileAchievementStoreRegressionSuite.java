package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.persistence.YamlPlayerProfileRepository;
import hu.taliann.icesmp.playerprofile.transaction.YamlPlayerProfileTransactionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.UUID;

/** Achievement, bestiary, recoverable reward-receipt and hidden-spot CAS regressions. */
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

            final var xpReward = new PlayerProfileAchievementStore.PendingReward(
                    "achievement:first_steps",
                    PlayerProfileAchievementStore.RewardKind.CLASS_XP, 250L, "");
            final var reserved = store.reserveReward(player, xpReward).toCompletableFuture().join();
            check(reserved.pending() && reserved.created(), "first reward becomes pending");
            check(store.rewardReserved(player, xpReward.receiptId()), "pending receipt readable");
            check(!store.rewardSettled(player, xpReward.receiptId()),
                    "reservation alone is not delivery");
            final var replay = store.reserveReward(player, xpReward).toCompletableFuture().join();
            check(replay.pending() && !replay.created(), "same pending receipt replays");
            expect(IllegalStateException.class, () -> store.reserveReward(player,
                    new PlayerProfileAchievementStore.PendingReward(xpReward.receiptId(),
                            PlayerProfileAchievementStore.RewardKind.CLASS_XP, 251L, ""))
                    .toCompletableFuture().join());

            // Crash/restart after reservation but before external reward execution.
            repository.invalidate(player);
            repository.loadSnapshot(player).toCompletableFuture().join();
            check(store.pendingReward(player, xpReward.receiptId()).orElseThrow().equals(xpReward),
                    "pending reward restart durable");
            check(store.pendingRewards(player).size() == 1,
                    "pending reward enumerates for reconnect recovery");
            check(store.settleReward(player, xpReward).toCompletableFuture().join(),
                    "delivered reward settles atomically");
            check(store.rewardSettled(player, xpReward.receiptId()), "settled receipt readable");
            check(store.pendingReward(player, xpReward.receiptId()).isEmpty(),
                    "settled reward leaves pending ledger");
            check(!store.settleReward(player, xpReward).toCompletableFuture().join(),
                    "duplicate settle is idempotent");
            final var settledReplay = store.reserveReward(player, xpReward).toCompletableFuture().join();
            check(settledReplay.state() == PlayerProfileAchievementStore.RewardState.SETTLED,
                    "settled reward cannot reserve again");

            final var currencyReward = new PlayerProfileAchievementStore.PendingReward(
                    "achievement:wealthy",
                    PlayerProfileAchievementStore.RewardKind.CURRENCY, 75L, "neutral");
            check(store.reserveReward(player, currencyReward).toCompletableFuture().join().created(),
                    "currency payload pending");
            repository.invalidate(player);
            repository.loadSnapshot(player).toCompletableFuture().join();
            check(store.pendingReward(player, currencyReward.receiptId()).orElseThrow()
                    .equals(currencyReward), "currency/currency-id payload restart durable");
            expect(IllegalStateException.class, () -> store.reserveReward(player,
                    new PlayerProfileAchievementStore.PendingReward(currencyReward.receiptId(),
                            PlayerProfileAchievementStore.RewardKind.CURRENCY, 75L, "red"))
                    .toCompletableFuture().join());
            check(store.settleReward(player, currencyReward).toCompletableFuture().join(),
                    "currency reward settles");

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
            check(store.rewardSettled(player, "achievement:first_steps"),
                    "settled reward restart durable");
            check(store.rewardSettled(player, "achievement:wealthy"),
                    "settled currency reward restart durable");
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

    private static void expect(final Class<? extends Throwable> expected,
                               final Throwing action) {
        assertions++;
        try {
            action.run();
            throw new AssertionError("Expected " + expected.getSimpleName());
        } catch (final Throwable failure) {
            final Throwable root = unwrap(failure);
            if (!expected.isInstance(root)) {
                throw new AssertionError("Expected " + expected.getSimpleName()
                        + " but got " + root, root);
            }
        }
    }

    private static Throwable unwrap(final Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current;
    }

    @FunctionalInterface private interface Throwing { void run() throws Exception; }
}
