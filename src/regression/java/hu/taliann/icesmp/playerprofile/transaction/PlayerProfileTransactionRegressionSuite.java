package hu.taliann.icesmp.playerprofile.transaction;

import hu.taliann.icesmp.playerprofile.domain.*;
import hu.taliann.icesmp.playerprofile.domain.section.*;
import hu.taliann.icesmp.playerprofile.persistence.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PlayerProfileTransactionRegressionSuite {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000001201");
    private static final Instant NOW = Instant.parse("2026-08-03T14:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static int assertions;

    private PlayerProfileTransactionRegressionSuite() { }

    public static void main(String[] args) throws Exception {
        crossSectionCommitAndIdempotency();
        invalidPlansAndStaleRevision();
        parallelSectionCas();
        crashBeforeManifestRollsBack();
        crashAfterManifestFinalizes();
        System.out.println("PlayerProfile transaction regression suite passed. assertions=" + assertions);
    }

    private static void crossSectionCommitAndIdempotency() throws Exception {
        Path root = Files.createTempDirectory("pp-tx-success-");
        YamlPlayerProfileRepository repo = repository(root, YamlPlayerProfileRepository.FaultInjector.none());
        try {
            join(repo.load(PLAYER));
            YamlPlayerProfileTransactionManager tx = new YamlPlayerProfileTransactionManager(repo, CLOCK);
            String result = join(tx.execute(PLAYER,
                    p -> rewardPlan(p, "quest-reward-1", "quest-1-100", 100)));
            check(result.equals("credited"), "transaction result");
            PlayerProfileSnapshot committed = join(repo.load(PLAYER));
            check(committed.profileRevision() == 1, "one global generation");
            check(committed.economy().revision() == 1, "economy revision");
            check(committed.quests().revision() == 1, "quest revision");
            check(committed.operations().revision() == 1, "operation revision");
            check(committed.economy().value().wallets().get("coins") == 100L, "wallet credited");
            check(committed.quests().value().completed().contains("quest-1"), "quest completed");
            PlayerProfileOperation receipt = committed.operations().value().operations()
                    .get("quest-reward-1");
            check(receipt != null && receipt.status() == PlayerProfileOperation.Status.COMMITTED,
                    "committed receipt");

            String replay = join(tx.execute(PLAYER,
                    p -> rewardPlan(p, "quest-reward-1", "quest-1-100", 100)));
            check(replay.equals("credited"), "idempotent replay result");
            PlayerProfileSnapshot unchanged = join(repo.load(PLAYER));
            check(unchanged.profileRevision() == 1, "replay does not commit again");
            check(unchanged.economy().value().wallets().get("coins") == 100L,
                    "replay does not double credit");

            expectStage(IllegalStateException.class, tx.execute(PLAYER,
                    p -> rewardPlan(p, "quest-reward-1", "different-fingerprint", 100)));
        } finally {
            shutdown(repo);
        }
    }

    private static void invalidPlansAndStaleRevision() throws Exception {
        Path root = Files.createTempDirectory("pp-tx-invalid-");
        YamlPlayerProfileRepository repo = repository(root, YamlPlayerProfileRepository.FaultInjector.none());
        try {
            PlayerProfileSnapshot p = join(repo.load(PLAYER));
            EconomySection economy = withCoins(p.economy().value(), 1);
            PlayerProfileTransactionManager.SectionUpdate update =
                    new PlayerProfileTransactionManager.SectionUpdate(
                            ProfileSectionId.ECONOMY, p.economy().revision(), economy);
            expect(IllegalArgumentException.class, () ->
                    new PlayerProfileTransactionManager.TransactionPlan<>(
                            "duplicate", "test", "fingerprint", List.of(update, update), "x"));
            expect(IllegalArgumentException.class, () ->
                    new PlayerProfileTransactionManager.TransactionPlan<>(
                            "empty", "test", "fingerprint", List.of(), "x"));

            YamlPlayerProfileTransactionManager tx = new YamlPlayerProfileTransactionManager(repo, CLOCK);
            expectStage(IllegalStateException.class, tx.execute(PLAYER, snapshot ->
                    new PlayerProfileTransactionManager.TransactionPlan<>(
                            "stale", "test", "stale-fingerprint",
                            List.of(new PlayerProfileTransactionManager.SectionUpdate(
                                    ProfileSectionId.ECONOMY,
                                    snapshot.economy().revision() + 1,
                                    withCoins(snapshot.economy().value(), 1))), "x")));
        } finally {
            shutdown(repo);
        }
    }

    private static void parallelSectionCas() throws Exception {
        Path root = Files.createTempDirectory("pp-tx-parallel-");
        YamlPlayerProfileRepository repo = repository(root, YamlPlayerProfileRepository.FaultInjector.none());
        try {
            PlayerProfileSnapshot p = join(repo.load(PLAYER));
            ProfileSectionSnapshot<EconomySection> economy = new ProfileSectionSnapshot<>(
                    ProfileSectionId.ECONOMY, 1, 1, NOW,
                    withCoins(p.economy().value(), 10), SectionHealth.healthy());
            ProfileSectionSnapshot<QuestSection> quests = new ProfileSectionSnapshot<>(
                    ProfileSectionId.QUESTS, 1, 1, NOW,
                    completedQuest(p.quests().value()), SectionHealth.healthy());
            CompletableFuture<PlayerProfileRepository.SectionSaveResult> first = repo.saveSection(
                    PLAYER, ProfileSectionId.ECONOMY, 0, economy).toCompletableFuture();
            CompletableFuture<PlayerProfileRepository.SectionSaveResult> second = repo.saveSection(
                    PLAYER, ProfileSectionId.QUESTS, 0, quests).toCompletableFuture();
            check(first.join().status() == PlayerProfileRepository.SectionSaveResult.Status.COMMITTED,
                    "economy parallel commit");
            check(second.join().status() == PlayerProfileRepository.SectionSaveResult.Status.COMMITTED,
                    "quest parallel commit");
            PlayerProfileSnapshot both = join(repo.load(PLAYER));
            check(both.profileRevision() == 2, "different sections independently commit");

            ProfileSectionSnapshot<EconomySection> a = new ProfileSectionSnapshot<>(
                    ProfileSectionId.ECONOMY, 1, 2, NOW,
                    withCoins(both.economy().value(), 11), SectionHealth.healthy());
            ProfileSectionSnapshot<EconomySection> b = new ProfileSectionSnapshot<>(
                    ProfileSectionId.ECONOMY, 1, 2, NOW,
                    withCoins(both.economy().value(), 12), SectionHealth.healthy());
            CompletableFuture<PlayerProfileRepository.SectionSaveResult> sameA = repo.saveSection(
                    PLAYER, ProfileSectionId.ECONOMY, 1, a).toCompletableFuture();
            CompletableFuture<PlayerProfileRepository.SectionSaveResult> sameB = repo.saveSection(
                    PLAYER, ProfileSectionId.ECONOMY, 1, b).toCompletableFuture();
            Set<PlayerProfileRepository.SectionSaveResult.Status> statuses = Set.of(
                    sameA.join().status(), sameB.join().status());
            check(statuses.contains(PlayerProfileRepository.SectionSaveResult.Status.COMMITTED),
                    "one same-section writer commits");
            check(statuses.contains(PlayerProfileRepository.SectionSaveResult.Status.STALE_REVISION),
                    "one same-section writer is stale");
        } finally {
            shutdown(repo);
        }
    }

    private static void crashBeforeManifestRollsBack() throws Exception {
        Path root = Files.createTempDirectory("pp-tx-before-manifest-");
        AtomicBoolean once = new AtomicBoolean();
        YamlPlayerProfileRepository.FaultInjector fault = new YamlPlayerProfileRepository.FaultInjector() {
            @Override public void afterSectionsMovedBeforeManifest(UUID id, Set<ProfileSectionId> changed)
                    throws IOException {
                if (once.compareAndSet(false, true)) throw new IOException("crash-before-manifest");
            }
            @Override public void afterManifestMovedBeforeCleanup(UUID id, Set<ProfileSectionId> changed) { }
        };
        YamlPlayerProfileRepository crashed = repository(root, fault);
        join(crashed.load(PLAYER));
        YamlPlayerProfileTransactionManager tx = new YamlPlayerProfileTransactionManager(crashed, CLOCK);
        expectStage(IOException.class, tx.execute(PLAYER,
                p -> rewardPlan(p, "crash-before", "before-fingerprint", 50)));
        shutdown(crashed);

        YamlPlayerProfileRepository recovered = repository(root,
                YamlPlayerProfileRepository.FaultInjector.none());
        try {
            PlayerProfileSnapshot p = join(recovered.load(PLAYER));
            check(p.profileRevision() == 0, "pre-manifest crash rolled back generation");
            check(p.economy().value().wallets().isEmpty(), "pre-manifest wallet rolled back");
            check(!p.operations().value().operations().containsKey("crash-before"),
                    "pre-manifest receipt rolled back");
            check(walEmpty(root.resolve(PLAYER.toString()).resolve("wal")), "rollback WAL cleaned");
        } finally {
            shutdown(recovered);
        }
    }

    private static void crashAfterManifestFinalizes() throws Exception {
        Path root = Files.createTempDirectory("pp-tx-after-manifest-");
        AtomicBoolean once = new AtomicBoolean();
        YamlPlayerProfileRepository.FaultInjector fault = new YamlPlayerProfileRepository.FaultInjector() {
            @Override public void afterSectionsMovedBeforeManifest(UUID id, Set<ProfileSectionId> changed) { }
            @Override public void afterManifestMovedBeforeCleanup(UUID id, Set<ProfileSectionId> changed)
                    throws IOException {
                if (once.compareAndSet(false, true)) throw new IOException("crash-after-manifest");
            }
        };
        YamlPlayerProfileRepository crashed = repository(root, fault);
        join(crashed.load(PLAYER));
        YamlPlayerProfileTransactionManager tx = new YamlPlayerProfileTransactionManager(crashed, CLOCK);
        expectStage(IOException.class, tx.execute(PLAYER,
                p -> rewardPlan(p, "crash-after", "after-fingerprint", 75)));
        shutdown(crashed);

        YamlPlayerProfileRepository recovered = repository(root,
                YamlPlayerProfileRepository.FaultInjector.none());
        try {
            PlayerProfileSnapshot p = join(recovered.load(PLAYER));
            check(p.profileRevision() == 1, "post-manifest commit retained");
            check(p.economy().value().wallets().get("coins") == 75L,
                    "post-manifest wallet retained");
            check(p.operations().value().operations().containsKey("crash-after"),
                    "post-manifest receipt retained");
            check(walEmpty(root.resolve(PLAYER.toString()).resolve("wal")), "finalized WAL cleaned");
            YamlPlayerProfileTransactionManager replayManager =
                    new YamlPlayerProfileTransactionManager(recovered, CLOCK);
            check(join(replayManager.execute(PLAYER,
                            current -> rewardPlan(current, "crash-after", "after-fingerprint", 75)))
                            .equals("credited"),
                    "post-manifest retry idempotent");
            check(join(recovered.load(PLAYER)).profileRevision() == 1,
                    "post-manifest retry does not duplicate");
        } finally {
            shutdown(recovered);
        }
    }

    private static PlayerProfileTransactionManager.TransactionPlan<String> rewardPlan(
            PlayerProfileSnapshot p, String operationId, String fingerprint, long amount) {
        EconomySection economy = withCoins(p.economy().value(), amount);
        QuestSection quests = completedQuest(p.quests().value());
        return new PlayerProfileTransactionManager.TransactionPlan<>(
                operationId, "quest-reward", fingerprint,
                List.of(
                        new PlayerProfileTransactionManager.SectionUpdate(
                                ProfileSectionId.ECONOMY, p.economy().revision(), economy),
                        new PlayerProfileTransactionManager.SectionUpdate(
                                ProfileSectionId.QUESTS, p.quests().revision(), quests)),
                "credited");
    }

    private static EconomySection withCoins(EconomySection current, long amount) {
        Map<String, Long> wallets = new LinkedHashMap<>(current.wallets());
        wallets.put("coins", Math.addExact(wallets.getOrDefault("coins", 0L), amount));
        return new EconomySection(wallets, current.bankBalance(), current.debts(),
                current.pendingRewards(), current.operationReceipts(), current.extensions());
    }

    private static QuestSection completedQuest(QuestSection current) {
        Set<String> completed = new LinkedHashSet<>(current.completed());
        completed.add("quest-1");
        Set<String> receipts = new LinkedHashSet<>(current.rewardReceipts());
        receipts.add("quest-1-reward");
        return new QuestSection(current.active(), completed, receipts, current.cooldowns(),
                current.communityContributions(), current.claimableRewards(), current.extensions());
    }

    private static boolean walEmpty(Path wal) throws IOException {
        if (!Files.isDirectory(wal)) return true;
        try (var entries = Files.list(wal)) { return entries.findAny().isEmpty(); }
    }

    private static YamlPlayerProfileRepository repository(
            Path root, YamlPlayerProfileRepository.FaultInjector fault) {
        return new YamlPlayerProfileRepository(root, CLOCK,
                Executors.newVirtualThreadPerTaskExecutor(), fault);
    }

    private static <T> T join(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    private static void shutdown(YamlPlayerProfileRepository repo) {
        join(repo.shutdown(Duration.ofSeconds(5)));
    }

    private static void expectStage(Class<? extends Throwable> expected, CompletionStage<?> stage) {
        assertions++;
        try {
            stage.toCompletableFuture().join();
            throw new AssertionError("Expected " + expected.getSimpleName());
        } catch (CompletionException failure) {
            Throwable cause = failure.getCause();
            if (!expected.isInstance(cause)) {
                throw new AssertionError("Expected " + expected.getSimpleName()
                        + " but got " + cause, cause);
            }
        }
    }

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }

    private static void expect(Class<? extends Throwable> expected, Throwing action) {
        assertions++;
        try {
            action.run();
            throw new AssertionError("Expected " + expected.getSimpleName());
        } catch (Throwable failure) {
            if (!expected.isInstance(failure)) {
                throw new AssertionError("Expected " + expected.getSimpleName()
                        + " but got " + failure, failure);
            }
        }
    }

    @FunctionalInterface private interface Throwing { void run() throws Exception; }
}
