package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.integrity.*;
import hu.taliann.icesmp.playerprofile.application.*;
import hu.taliann.icesmp.playerprofile.domain.*;
import hu.taliann.icesmp.playerprofile.persistence.YamlPlayerProfileRepository;
import hu.taliann.icesmp.playerprofile.transaction.YamlPlayerProfileTransactionManager;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Real statistics admission and atomic quest receipt/statistics settlement, without Bukkit fixtures. */
public final class WeaverStatisticsRewardRegressionSuite {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000973");
    private static final RewardSource SOURCE = new RewardSource.Entity(UUID.randomUUID());
    private static final Set<RewardSource> TAINTED = new HashSet<>();
    private static final AtomicBoolean QUARANTINED = new AtomicBoolean();
    private static int assertions;

    public static void main(String[] args) throws Exception {
        try (var binding = GameplayRewardGate.install(context -> QUARANTINED.get()
                || context.sources().stream().anyMatch(TAINTED::contains)
                ? RewardDecision.deny("QUARANTINED") : RewardDecision.allow())) {
            queuedAdmissions(); atomicSettlement(); settlementCrashBoundaries(); contradictoryReceipt();
        }
        unboundRefusal(); nativeContracts();
        System.out.println("Statistics reward admission passed: " + assertions
                + " assertions; metric provenance, immutable first kill and atomic earned quest/statistics settlement.");
    }

    private static void queuedAdmissions() throws Exception {
        final Path root = Files.createTempDirectory("weaver-stats-admit-");
        final List<RewardSource> sources = List.of(SOURCE, new RewardSource.Player(UUID.randomUUID()),
                new RewardSource.Item(UUID.randomUUID()), new RewardSource.Event("fixture", UUID.randomUUID()),
                new RewardSource.World(UUID.randomUUID()), new RewardSource.Location(UUID.randomUUID(), 1, 64, 2));
        try (var h = new Harness(root, YamlPlayerProfileRepository.FaultInjector.none())) {
            h.load(); final var disk = bytes(root);
            for (var source : sources) {
                final RewardContext reward = context(RewardChannel.TRACKING_PROGRESS, source);
                final List<CompletionStage<?>> writes = new ArrayList<>();
                for (String key : List.of("raid-kills", "kills", "deaths", "mob-kills", "spell-casts", "quests-completed"))
                    writes.add(h.store.increment(PLAYER, key, reward));
                writes.add(h.store.recordMobKill(PLAYER, "zombie", 100, reward));
                writes.add(h.store.snapshot(PLAYER, 10, 100, reward));
                TAINTED.add(source); for (var write : writes) h.denied(write); TAINTED.clear();
                check(bytes(root).equals(disk), "denied metric source wrote counter, first-kill time, leaderboard or revision");
            }
            var recipient = h.store.increment(PLAYER, "kills", context(RewardChannel.TRACKING_PROGRESS, SOURCE));
            QUARANTINED.set(true); h.denied(recipient); QUARANTINED.set(false);
            check(bytes(root).equals(disk), "recipient quarantine bypassed the final statistics WAL gate");
            expect(IllegalArgumentException.class, () -> h.store.increment(PLAYER, "kills", context(RewardChannel.ACHIEVEMENT, SOURCE)));
            expect(IllegalArgumentException.class, () -> h.store.recordMobKill(PLAYER, "zombie", 0));
            expect(IllegalArgumentException.class, () -> h.store.snapshot(PLAYER, 1, 1,
                    new RewardContext(RewardChannel.TRACKING_PROGRESS, UUID.randomUUID(), List.of(SOURCE))));
            h.finish(h.store.recordMobKill(PLAYER, "zombie", 100)); h.finish(h.store.recordMobKill(PLAYER, "zombie", 200));
            check(h.store.speciesKills(PLAYER, "zombie") == 2 && h.store.speciesFirstKillAt(PLAYER, "zombie") == 100
                    && h.store.read(PLAYER, "mob-kills") == 2, "clean species/total/first-kill semantics changed");
        } finally { TAINTED.clear(); QUARANTINED.set(false); delete(root); }
    }

    private static void atomicSettlement() throws Exception {
        final Path root = Files.createTempDirectory("weaver-stats-settle-");
        try (var h = new Harness(root, YamlPlayerProfileRepository.FaultInjector.none())) {
            h.load(); h.finish(h.quests.accept(PLAYER, "earned")); final String receipt = h.finish(h.earn()).receiptId();
            check(h.store.read(PLAYER, "quests-completed") == 0, "quest statistic counted before earned reward settlement");
            h.finish(h.quests.prepareRewardComponents(PLAYER, receipt, Set.of("item:0:0")));
            final var prepared = bytes(root);
            expect(IllegalStateException.class, () -> h.finish(h.quests.settleReward(PLAYER, receipt)));
            check(bytes(root).equals(prepared), "unfinished physical component partially settled its statistic");
            h.finish(h.quests.markRewardComponentsDelivered(PLAYER, receipt, Set.of("item:0:0")));
            QUARANTINED.set(true); final long before = h.repository.cached(PLAYER).orElseThrow().profileRevision();
            final var a = h.quests.settleReward(PLAYER, receipt); final var b = h.quests.settleReward(PLAYER, receipt);
            boolean first = h.finish(a), second = h.finish(b);
            check(first != second && h.store.read(PLAYER, "quests-completed") == 1, "concurrent settlement duplicated or lost its statistic");
            check(h.repository.cached(PLAYER).orElseThrow().profileRevision() == before + 1
                    && h.quests.pendingRewards(PLAYER).isEmpty(), "settlement/statistic were split across WAL generations");
            final var settled = bytes(root); check(!h.finish(h.quests.settleReward(PLAYER, receipt)) && bytes(root).equals(settled),
                    "settlement replay wrote a new statistic, operation or profile revision");
            expect(IllegalStateException.class, () -> h.finish(h.quests.settleReward(PLAYER, "missing|100")));
            h.denied(h.store.increment(PLAYER, "quests-completed"));
            check(bytes(root).equals(settled), "canonical entitlement exception leaked to arbitrary metric increment");
        } finally { QUARANTINED.set(false); delete(root); }
    }

    private static void settlementCrashBoundaries() throws Exception {
        for (boolean afterManifest : List.of(false, true)) {
            final Path root = Files.createTempDirectory("weaver-stats-crash-"); final AtomicBoolean armed = new AtomicBoolean();
            final var fault = new YamlPlayerProfileRepository.FaultInjector() {
                public void afterSectionsMovedBeforeManifest(UUID id, Set<ProfileSectionId> sections) throws IOException {
                    if (!afterManifest && armed.getAndSet(false)) throw new IOException("injected before settlement manifest");
                }
                public void afterManifestMovedBeforeCleanup(UUID id, Set<ProfileSectionId> sections) throws IOException {
                    if (afterManifest && armed.getAndSet(false)) throw new IOException("injected after settlement manifest");
                }
            };
            String receipt;
            try {
                try (var h = new Harness(root, fault)) {
                    h.load(); h.finish(h.quests.accept(PLAYER, "earned")); receipt = h.finish(h.earn()).receiptId();
                    armed.set(true); final String accepted = receipt;
                    expect(IOException.class, () -> h.finish(h.quests.settleReward(PLAYER, accepted)));
                }
                QUARANTINED.set(true);
                try (var h = new Harness(root, YamlPlayerProfileRepository.FaultInjector.none())) {
                    h.load(); check(h.store.read(PLAYER, "quests-completed") == (afterManifest ? 1 : 0), "statistic violated canonical manifest recovery");
                    check(h.quests.read(PLAYER).settledRewards().contains(receipt) == afterManifest
                            && h.quests.pendingRewards(PLAYER).contains(receipt) != afterManifest, "crash split the quest receipt and statistic");
                    check(h.finish(h.quests.settleReward(PLAYER, receipt)) != afterManifest && h.store.read(PLAYER, "quests-completed") == 1,
                            "accepted restart settlement duplicated or lost its earned statistic");
                }
            } finally { QUARANTINED.set(false); delete(root); }
        }
    }

    private static void contradictoryReceipt() throws Exception {
        final Path root = Files.createTempDirectory("weaver-stats-conflict-");
        try (var h = new Harness(root, YamlPlayerProfileRepository.FaultInjector.none())) {
            h.load(); h.finish(h.quests.accept(PLAYER, "earned")); final String receipt = h.finish(h.earn()).receiptId();
            final var accepted = h.repository.cached(PLAYER).orElseThrow().quests().value();
            h.finish(h.quests.settleReward(PLAYER, receipt));
            // Fault fixture uses the canonical profile API to simulate external contradictory state,
            // not a forged gameplay event or a private map/PDC edit.
            h.finish(h.authority.mutateSection(PLAYER, ProfileSectionId.QUESTS,
                    hu.taliann.icesmp.playerprofile.domain.section.QuestSection.class, current -> accepted));
            final var disk = bytes(root);
            expect(IllegalStateException.class, () -> h.finish(h.quests.settleReward(PLAYER, receipt)));
            check(h.store.read(PLAYER, "quests-completed") == 1 && bytes(root).equals(disk), "contradictory pending receipt replayed a committed settlement");
        } finally { delete(root); }
    }

    private static void unboundRefusal() throws Exception {
        final Path root = Files.createTempDirectory("weaver-stats-unbound-");
        try (var h = new Harness(root, YamlPlayerProfileRepository.FaultInjector.none())) {
            h.load(); final var disk = bytes(root);
            h.denied(h.store.increment(PLAYER, "kills")); h.denied(h.store.recordMobKill(PLAYER, "zombie", 100));
            h.denied(h.store.snapshot(PLAYER, 1, 1)); check(bytes(root).equals(disk), "unbound policy admitted statistics");
        } finally { delete(root); }
    }

    private static void nativeContracts() throws Exception {
        final String base = "src/main/java/hu/taliann/icesmp/";
        final String combat = Files.readString(Path.of(base + "listeners/StatsCombatListener.java"));
        final String catalyst = Files.readString(Path.of(base + "listeners/AbilityCatalystListener.java"));
        final String quests = Files.readString(Path.of(base + "managers/QuestManager.java"));
        final String stats = Files.readString(Path.of(base + "managers/StatsManager.java"));
        check(combat.contains("kill.rewardContext(RewardChannel.TRACKING_PROGRESS)")
                && combat.contains("source.forRecipient(victimId)"), "native combat discarded metric source lineage");
        check(catalyst.indexOf("activitySources = captureActivitySources(player)") < catalyst.indexOf("outcome = selected.cast(")
                && catalyst.contains("RewardChannel.QUEST_PROGRESS, playerId, activitySources"), "spell activity captured provenance after its native world effect");
        check(!quests.contains("statsManager.recordQuestComplete("), "quest UI still increments statistics outside canonical settlement");
        check(stats.contains("store.snapshot(playerId,") && stats.contains("Bukkit.isOwnedByCurrentRegion(owned)"),
                "leaderboard snapshot retained a live player across profile IO");
    }

    private static RewardContext context(RewardChannel channel, RewardSource source) { return new RewardContext(channel, PLAYER, List.of(source)); }
    private static final class Harness implements AutoCloseable {
        final ManualExecutor io = new ManualExecutor(); final YamlPlayerProfileRepository repository;
        final PlayerProfileStatisticsStore store = new PlayerProfileStatisticsStore();
        final PlayerProfileQuestStore quests = new PlayerProfileQuestStore(); final PlayerProfileAuthority authority;
        Harness(Path root, YamlPlayerProfileRepository.FaultInjector fault) {
            repository = new YamlPlayerProfileRepository(root, Clock.systemUTC(), io, fault);
            final var transactions = new YamlPlayerProfileTransactionManager(repository);
            authority = PlayerProfileAuthority.install(new PlayerProfileService(repository, transactions), repository, transactions);
        }
        void load() { finish(repository.load(PLAYER)); }
        CompletionStage<PlayerProfileQuestStore.CompletionReceipt> earn() {
            return quests.complete(PLAYER, "earned", 100, 0);
        }
        <T> T finish(CompletionStage<T> stage) {
            for (int n = 0; !stage.toCompletableFuture().isDone() && n < 100; n++) io.one();
            check(stage.toCompletableFuture().isDone(), "bounded storage drain failed"); return stage.toCompletableFuture().join();
        }
        void denied(CompletionStage<?> stage) { expect(RewardEligibilityDeniedException.class, () -> finish(stage)); }
        public void close() throws Exception {
            authority.uninstall(); check(repository.shutdown(Duration.ofSeconds(1)).toCompletableFuture().get(5, TimeUnit.SECONDS).drained(), "storage shutdown did not drain");
        }
    }
    private static final class ManualExecutor extends AbstractExecutorService {
        final ArrayDeque<Runnable> queue = new ArrayDeque<>(); boolean stopped;
        public void execute(Runnable command) { if (stopped) throw new RejectedExecutionException(); queue.add(command); }
        void one() { final var next = queue.poll(); if (next == null) throw new AssertionError("no storage task"); next.run(); }
        public void shutdown() { stopped = true; }
        public List<Runnable> shutdownNow() { stopped = true; final var rest = List.copyOf(queue); queue.clear(); return rest; }
        public boolean isShutdown() { return stopped; }
        public boolean isTerminated() { return stopped && queue.isEmpty(); }
        public boolean awaitTermination(long timeout, TimeUnit unit) { return isTerminated(); }
    }
    private static Map<String, String> bytes(Path root) throws Exception {
        final Map<String, String> result = new TreeMap<>();
        try (var files = Files.walk(root)) {
            for (var p : files.filter(Files::isRegularFile).toList()) result.put(root.relativize(p).toString(),
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p))));
        }
        return result;
    }
    private static void expect(Class<? extends Throwable> type, Runnable action) {
        assertions++; try { action.run(); throw new AssertionError("Expected " + type.getSimpleName()); }
        catch (Throwable failure) {
            while (failure instanceof CompletionException && failure.getCause() != null) failure = failure.getCause();
            if (!type.isInstance(failure)) throw new AssertionError("Expected " + type.getSimpleName() + ", got " + failure, failure);
        }
    }
    private static void delete(Path root) throws IOException { try (var paths = Files.walk(root)) { for (var p : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p); } }
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
}
