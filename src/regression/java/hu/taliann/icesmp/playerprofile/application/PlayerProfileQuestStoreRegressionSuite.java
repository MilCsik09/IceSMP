package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.persistence.YamlPlayerProfileRepository;
import hu.taliann.icesmp.playerprofile.transaction.YamlPlayerProfileTransactionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.UUID;

/** Main quest lifecycle, reward receipt and restart regressions. */
public final class PlayerProfileQuestStoreRegressionSuite {
    private static int assertions;

    private PlayerProfileQuestStoreRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        final Path root = Files.createTempDirectory("player-profile-quest-store-");
        final YamlPlayerProfileRepository repository = new YamlPlayerProfileRepository(root);
        final YamlPlayerProfileTransactionManager transactions =
                new YamlPlayerProfileTransactionManager(repository);
        final PlayerProfileService service = new PlayerProfileService(repository, transactions);
        final PlayerProfileAuthority authority = PlayerProfileAuthority.install(
                service, repository, transactions);
        try {
            final UUID player = UUID.fromString("00000000-0000-0000-0000-000000001092");
            repository.loadSnapshot(player).toCompletableFuture().join();
            final PlayerProfileQuestStore store = new PlayerProfileQuestStore();

            check(store.accept(player, "story_one").toCompletableFuture().join(),
                    "first acceptance committed");
            check(!store.accept(player, "story_one").toCompletableFuture().join(),
                    "duplicate acceptance rejected");
            check(store.active(player).contains("story_one"), "active list updated");

            check(store.incrementProgress(player, "story_one", 0, 2, 5)
                    .toCompletableFuture().join() == 2, "progress incremented");
            check(store.incrementProgress(player, "story_one", 0, 10, 5)
                    .toCompletableFuture().join() == 5, "progress capped");
            check(store.setProgress(player, "story_one", 1, 3)
                    .toCompletableFuture().join() == 3, "second objective stored");
            check(store.progress(player, "story_one", 0) == 5,
                    "first objective readable");
            check(store.progress(player, "story_one", 1) == 3,
                    "second objective readable");

            final long completedAt = 1_700_000_000_000L;
            final long season = 123_456L;
            final var receipt = store.complete(player, "story_one", completedAt, season)
                    .toCompletableFuture().join();
            check(receipt.committed(), "completion committed");
            check(!receipt.receiptId().isBlank(), "claimable receipt created");
            check(!store.active(player).contains("story_one"), "active state removed");
            check(store.completed(player).contains("story_one"), "completed state added");
            check(store.lastCompletedAt(player, "story_one") == completedAt,
                    "completion cooldown persisted");
            check(store.completedSeason(player, "story_one") == season,
                    "completion season persisted");
            check(store.pendingRewards(player).contains(receipt.receiptId()),
                    "reward remains claimable");

            final long revision = repository.cached(player).orElseThrow().quests().revision();
            final var replay = store.complete(player, "story_one", completedAt + 1, season)
                    .toCompletableFuture().join();
            check(!replay.committed(), "completion replay rejected");
            check(repository.cached(player).orElseThrow().quests().revision() == revision,
                    "completion replay no-op");

            check(store.settleReward(player, receipt.receiptId())
                    .toCompletableFuture().join(), "reward settled");
            check(!store.settleReward(player, receipt.receiptId())
                    .toCompletableFuture().join(), "reward settlement replay rejected");
            check(store.pendingRewards(player).isEmpty(), "claimable receipt removed");

            check(store.accept(player, "repeatable").toCompletableFuture().join(),
                    "repeatable accepted");
            final var pending = store.complete(player, "repeatable", completedAt + 10, season)
                    .toCompletableFuture().join();
            check(pending.committed(), "pending reward completion committed");
            repository.invalidate(player);
            repository.loadSnapshot(player).toCompletableFuture().join();
            check(store.pendingRewards(player).contains(pending.receiptId()),
                    "pending receipt restart durable");
            check("repeatable".equals(store.questFromReceipt(pending.receiptId())),
                    "receipt resolves quest id");
            check(store.completed(player).contains("story_one")
                    && store.completed(player).contains("repeatable"),
                    "completed quests restart durable");

            check(store.accept(player, "abandon_me").toCompletableFuture().join(),
                    "abandon quest accepted");
            store.setProgress(player, "abandon_me", 0, 7).toCompletableFuture().join();
            check(store.abandon(player, "abandon_me").toCompletableFuture().join(),
                    "abandon committed");
            check(!store.abandon(player, "abandon_me").toCompletableFuture().join(),
                    "abandon replay rejected");
            check(!store.active(player).contains("abandon_me"),
                    "abandoned progress removed");

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
        System.out.println("PlayerProfile quest store regression suite passed. assertions="
                + assertions);
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
