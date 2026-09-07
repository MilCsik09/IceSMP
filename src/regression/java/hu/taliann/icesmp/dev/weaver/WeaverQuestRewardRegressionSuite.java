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

/** Real quest WAL admission and readiness: optimistic UI state never authorizes an entitlement. */
public final class WeaverQuestRewardRegressionSuite {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000972");
    private static final RewardSource SOURCE = new RewardSource.Entity(UUID.randomUUID());
    private static final Set<RewardSource> TAINTED = new HashSet<>();
    private static final AtomicBoolean QUARANTINED = new AtomicBoolean();
    private static int assertions;

    public static void main(String[] args) throws Exception {
        try (var binding = GameplayRewardGate.install(context -> QUARANTINED.get()
                || context.sources().stream().anyMatch(TAINTED::contains)
                ? RewardDecision.deny("QUARANTINED") : RewardDecision.allow())) {
            queuedAdmissions();
            durableReadinessAndAcceptedRecovery();
            queuedProfileRetirement();
            completionCrashBoundaries();
        }
        unboundRefusal();
        nativeContracts();
        System.out.println("Quest reward admission passed: " + assertions
                + " assertions; queued provenance, durable objective readiness, drift refusal and accepted entitlement recovery.");
    }

    private static void queuedAdmissions() throws Exception {
        final Path root = Files.createTempDirectory("weaver-quest-admit-");
        final List<RewardSource> sources = List.of(SOURCE, new RewardSource.Player(UUID.randomUUID()),
                new RewardSource.Item(UUID.randomUUID()), new RewardSource.Event("fixture", UUID.randomUUID()),
                new RewardSource.World(UUID.randomUUID()), new RewardSource.Location(UUID.randomUUID(), 1, 64, 2));
        try (var h = new Harness(root, YamlPlayerProfileRepository.FaultInjector.none())) {
            h.load(); h.finish(h.store.accept(PLAYER, "active"));
            h.finish(h.store.setProgress(PLAYER, "active", 0, 1));
            final var disk = bytes(root); int index = 0;
            for (var source : sources) {
                final String quest = "fixture_" + index++;
                var accept = h.store.accept(PLAYER, quest, "npc:fixture", context(RewardChannel.QUEST_PROGRESS, source));
                var discover = h.store.discover(PLAYER, quest, "npc:fixture", context(RewardChannel.DISCOVERY, source));
                var set = h.store.setProgress(PLAYER, "active", 1, 1, context(RewardChannel.QUEST_PROGRESS, source));
                var increment = h.store.incrementProgress(PLAYER, "active", 1, 1, 1, context(RewardChannel.QUEST_PROGRESS, source));
                var complete = h.store.completeIfReady(PLAYER, "active", 100, 0, Map.of(0, 1), context(RewardChannel.QUEST_REWARD, source));
                TAINTED.add(source);
                h.denied(accept); h.denied(discover); h.denied(set); h.denied(increment); h.denied(complete);
                check(bytes(root).equals(disk), "denied source wrote quest state, receipt, cooldown or profile revision");
                TAINTED.clear();
            }
            var recipient = h.store.complete(PLAYER, "active", 100, 0, context(RewardChannel.QUEST_REWARD, SOURCE));
            QUARANTINED.set(true); h.denied(recipient); QUARANTINED.set(false);
            check(bytes(root).equals(disk), "trusted native completion bypassed recipient quarantine");
            expect(IllegalArgumentException.class, () -> h.store.accept(PLAYER, "wrong", "", context(RewardChannel.QUEST_REWARD, SOURCE)));
            expect(IllegalArgumentException.class, () -> h.store.discover(PLAYER, "wrong", "", context(RewardChannel.QUEST_PROGRESS, SOURCE)));
            expect(IllegalArgumentException.class, () -> h.store.setProgress(PLAYER, "active", 0, 2, context(RewardChannel.DISCOVERY, SOURCE)));
            expect(IllegalArgumentException.class, () -> h.store.incrementProgress(PLAYER, "active", 0, 1, 2,
                    new RewardContext(RewardChannel.QUEST_PROGRESS, UUID.randomUUID(), List.of(SOURCE))));
            expect(IllegalArgumentException.class, () -> h.store.completeIfReady(PLAYER, "active", 100, 0, Map.of(), context(RewardChannel.QUEST_REWARD, SOURCE)));
            expect(IllegalArgumentException.class, () -> h.store.completeIfReady(PLAYER, "active", 100, 0, Map.of(-1, 1), context(RewardChannel.QUEST_REWARD, SOURCE)));
            expect(IllegalArgumentException.class, () -> h.store.completeIfReady(PLAYER, "active", 100, 0, Map.of(0, 0), context(RewardChannel.QUEST_REWARD, SOURCE)));
            final Map<Integer, Integer> excess = new HashMap<>();
            for (int n = 0; n < 513; n++) excess.put(n, 1);
            expect(IllegalArgumentException.class, () -> h.store.completeIfReady(PLAYER, "active", 100, 0, excess, context(RewardChannel.QUEST_REWARD, SOURCE)));
        } finally { TAINTED.clear(); QUARANTINED.set(false); delete(root); }
    }

    private static void durableReadinessAndAcceptedRecovery() throws Exception {
        final Path root = Files.createTempDirectory("weaver-quest-ready-");
        try (var h = new Harness(root, YamlPlayerProfileRepository.FaultInjector.none())) {
            h.load(); h.finish(h.store.accept(PLAYER, "active")); final var empty = bytes(root);
            // The UI can already show completion, but failed ingress never reached the canonical objectives.
            var increment = h.store.incrementProgress(PLAYER, "active", 0, 1, 1, context(RewardChannel.QUEST_PROGRESS, SOURCE));
            TAINTED.add(SOURCE); h.denied(increment); TAINTED.clear();
            check(!h.finish(h.complete()).committed() && bytes(root).equals(empty), "failed progress generated an entitlement from optimistic readiness");
            h.finish(h.store.incrementProgress(PLAYER, "active", 0, 1, 1));
            check(!h.finish(h.complete()).committed(), "one ready objective bypassed the other requirement");
            h.finish(h.store.setProgress(PLAYER, "active", 1, 2));
            // Change the canonical profile before queued completion reaches its generation-fenced save.
            final var reset = h.store.setProgress(PLAYER, "active", 1, 0);
            final var queued = h.complete();
            h.finish(reset); check(!h.finish(queued).committed(), "stale ready snapshot overwrote external objective drift");
            check(h.store.active(PLAYER).contains("active") && h.store.pendingRewards(PLAYER).isEmpty(), "drift refusal consumed the active quest");
            h.finish(h.store.setProgress(PLAYER, "active", 1, 2));
            final long before = h.repository.cached(PLAYER).orElseThrow().profileRevision();
            final var accepted = h.finish(h.complete());
            check(accepted.committed() && h.repository.cached(PLAYER).orElseThrow().profileRevision() == before + 1,
                    "completion and entitlement did not share one WAL generation");
            final String receipt = accepted.receiptId(); final var acceptedBytes = bytes(root);
            QUARANTINED.set(true);
            check(!h.finish(h.complete()).committed() && bytes(root).equals(acceptedBytes), "quarantine changed an accepted completion on replay");
            h.repository.invalidate(PLAYER); h.load();
            check(h.store.pendingRewards(PLAYER).contains(receipt), "accepted entitlement did not survive reload");
            final Set<String> components = Set.of("item:0:0", "currency:neutral:0");
            check(h.finish(h.store.prepareRewardComponents(PLAYER, receipt, components)), "quarantine revoked preparation of earned physical delivery");
            expect(IllegalStateException.class, () -> h.finish(h.store.settleReward(PLAYER, receipt)));
            check(h.finish(h.store.markRewardComponentsDelivered(PLAYER, receipt, components)), "earned delivery could not acknowledge canonical witnesses");
            check(h.finish(h.store.settleReward(PLAYER, receipt)), "earned reward could not settle under later quarantine");
            final var settled = bytes(root);
            check(!h.finish(h.store.settleReward(PLAYER, receipt)) && bytes(root).equals(settled), "settlement replay changed a completed receipt");
            expect(IllegalStateException.class, () -> h.finish(h.store.prepareRewardComponents(PLAYER, "missing|100", components)));
        } finally { TAINTED.clear(); QUARANTINED.set(false); delete(root); }
    }

    private static void queuedProfileRetirement() throws Exception {
        final Path root = Files.createTempDirectory("weaver-quest-retirement-");
        try (var h = new Harness(root, YamlPlayerProfileRepository.FaultInjector.none())) {
            h.load(); h.finish(h.store.accept(PLAYER, "active"));
            final var queue = new hu.taliann.icesmp.quest.QuestMutationQueue();
            final var admitted = queue.submit(PLAYER, () -> h.store.setProgress(PLAYER, "active", 0, 1).thenApply(ignored -> null));
            final var unentered = queue.submit(PLAYER, () -> { throw new AssertionError("retired quest completion entered"); });
            final var retired = queue.retire(PLAYER);
            expect(RejectedExecutionException.class, () -> unentered.toCompletableFuture().join());
            check(!retired.toCompletableFuture().isDone(), "profile queue retired before the real WAL acknowledgement");
            h.finish(admitted); retired.toCompletableFuture().join();
            check(h.store.progress(PLAYER, "active", 0) == 1 && h.store.pendingRewards(PLAYER).isEmpty(),
                    "retirement lost an entered progress write or synthesized queued entitlement");
            h.repository.invalidate(PLAYER); h.load();
            check(h.store.active(PLAYER).contains("active") && h.store.progress(PLAYER, "active", 0) == 1,
                    "retired progress did not survive canonical reload");
            h.finish(h.store.setProgress(PLAYER, "active", 1, 2));
            final var accepted = queue.submit(PLAYER, () -> h.complete().thenApply(ignored -> null));
            final var closing = queue.close();
            check(!closing.toCompletableFuture().isDone(), "shutdown acknowledged before the real completion WAL");
            h.finish(accepted); closing.toCompletableFuture().join();
            check(h.store.completed(PLAYER).contains("active") && h.store.pendingRewards(PLAYER).size() == 1,
                    "shutdown discarded or replayed an entered canonical completion");
            final var disk = bytes(root);
            expect(RejectedExecutionException.class, () -> queue.submit(PLAYER,
                    () -> h.store.accept(PLAYER, "late").thenApply(ignored -> null)).toCompletableFuture().join());
            check(bytes(root).equals(disk), "post-shutdown work reached profile persistence");
        } finally { delete(root); }
    }

    private static void completionCrashBoundaries() throws Exception {
        for (boolean afterManifest : List.of(false, true)) {
            final Path root = Files.createTempDirectory("weaver-quest-crash-"); final AtomicBoolean armed = new AtomicBoolean();
            final var fault = new YamlPlayerProfileRepository.FaultInjector() {
                public void afterSectionsMovedBeforeManifest(UUID id, Set<ProfileSectionId> sections) throws IOException {
                    if (!afterManifest && armed.getAndSet(false)) throw new IOException("injected before manifest");
                }
                public void afterManifestMovedBeforeCleanup(UUID id, Set<ProfileSectionId> sections) throws IOException {
                    if (afterManifest && armed.getAndSet(false)) throw new IOException("injected after manifest");
                }
            };
            try {
                try (var h = new Harness(root, fault)) {
                    h.load(); h.finish(h.store.accept(PLAYER, "active"));
                    h.finish(h.store.setProgress(PLAYER, "active", 0, 1)); h.finish(h.store.setProgress(PLAYER, "active", 1, 2));
                    armed.set(true); expect(IOException.class, () -> h.finish(h.complete()));
                }
                QUARANTINED.set(true);
                try (var h = new Harness(root, YamlPlayerProfileRepository.FaultInjector.none())) {
                    h.load();
                    check(h.store.completed(PLAYER).contains("active") == afterManifest, "completion violated the canonical manifest crash boundary");
                    check(!h.store.pendingRewards(PLAYER).isEmpty() == afterManifest, "crash split completion from reward entitlement");
                    check(h.store.active(PLAYER).contains("active") != afterManifest, "crash lost or duplicated active quest state");
                    if (!afterManifest) h.denied(h.complete());
                }
            } finally { QUARANTINED.set(false); delete(root); }
        }
    }

    private static void unboundRefusal() throws Exception {
        final Path root = Files.createTempDirectory("weaver-quest-unbound-");
        try (var h = new Harness(root, YamlPlayerProfileRepository.FaultInjector.none())) {
            h.load(); final var disk = bytes(root); h.denied(h.store.accept(PLAYER, "unbound"));
            h.denied(h.store.discover(PLAYER, "unbound", "npc:fixture"));
            check(bytes(root).equals(disk), "unbound policy allowed new quest state");
        } finally { delete(root); }
    }

    private static void nativeContracts() throws Exception {
        final String base = "src/main/java/hu/taliann/icesmp/";
        final String manager = Files.readString(Path.of(base + "managers/QuestManager.java"));
        final String listener = Files.readString(Path.of(base + "listeners/QuestProgressListener.java"));
        check(manager.contains("questStore.completeIfReady(playerId, id, completedAt, seasonId, requirements, reward)"), "native completion bypassed durable readiness");
        check(manager.contains("jobManager.addXpToJobResultV2(player, classXp, \"quest-xp:\" + receiptId)")
                && manager.contains("case REJECTED -> CompletableFuture.<Void>failedFuture(new RewardEligibilityDeniedException())"),
                "refused class XP could falsely settle its accepted quest entitlement");
        check(!manager.contains("previous.handle((value, failure) -> null)"), "failed progress still authorizes queued dependent work");
        check(manager.contains("questStore.incrementProgress(playerId, id, index, amount, target, reward)")
                && manager.contains("questStore.setProgress(playerId, id, index, value, reward)"), "native progress dropped its captured lineage before WAL admission");
        check(listener.contains("kill.rewardContext(RewardChannel.QUEST_PROGRESS)")
                && listener.contains("BukkitRewardSources.causal(event.getMother())")
                && listener.contains("BukkitRewardSources.causal(event.getFather())")
                && listener.contains("BukkitRewardSources.block(to.getBlock())"), "native activity dropped kill, breeding or destination provenance");
        check(listener.contains("Bukkit.isOwnedByCurrentRegion(owned)") && listener.contains("if (!Bukkit.isOwnedByCurrentRegion(to)) return;"), "native source routing lost its owner fences");
    }

    private static RewardContext context(RewardChannel channel, RewardSource source) { return new RewardContext(channel, PLAYER, List.of(source)); }
    private static final class Harness implements AutoCloseable {
        final ManualExecutor io = new ManualExecutor(); final YamlPlayerProfileRepository repository;
        final PlayerProfileQuestStore store = new PlayerProfileQuestStore(); final PlayerProfileAuthority authority;
        Harness(Path root, YamlPlayerProfileRepository.FaultInjector fault) {
            repository = new YamlPlayerProfileRepository(root, Clock.systemUTC(), io, fault);
            final var transactions = new YamlPlayerProfileTransactionManager(repository);
            authority = PlayerProfileAuthority.install(new PlayerProfileService(repository, transactions), repository, transactions);
        }
        void load() { finish(repository.load(PLAYER)); }
        CompletionStage<PlayerProfileQuestStore.CompletionReceipt> complete() {
            return store.completeIfReady(PLAYER, "active", 100, 0, Map.of(0, 1, 1, 2), context(RewardChannel.QUEST_REWARD, SOURCE));
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
