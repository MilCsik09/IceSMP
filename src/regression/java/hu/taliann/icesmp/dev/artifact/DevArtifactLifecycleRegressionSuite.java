package hu.taliann.icesmp.dev.artifact;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class DevArtifactLifecycleRegressionSuite {
    private static final UUID OWNER = new UUID(1, 1);
    private static final UUID INSTANCE = new UUID(2, 2);

    public static void main(final String[] args) {
        fixedOwnerIgnoresConfiguration();
        unissuedAndForgedMarkersAreRejected();
        snapshotsCannotBeChangedByCaller();
        publicationWaitsForDurability();
        failureClosesMutationAndPreservesBeforeState();
        staleRevisionAndConcurrentMutationAreRejected();
        continuationCanStartNextMutation();
        serializedArtifactsPreserveEachOther();
        shutdownDrainsAcceptedMutationAndRefusesNewWork();
        executorRejectionFailsClosed();
        modelStatePrecedence();
        System.out.println("DEV artifact lifecycle regression suite passed.");
    }

    private static DevArtifactState initial() {
        return new DevArtifactState(OWNER, INSTANCE, false, 0, Map.of("progress-millis", 123L));
    }

    private static void fixedOwnerIgnoresConfiguration() {
        check(new FixedArtifactOwner(OWNER).resolve((key, fallback) -> {
            throw new AssertionError("Fixed developer authority consulted operator configuration");
        }).equals(OWNER), "fixed owner changed");
        final UUID configured = new UUID(3, 3);
        check(new ConfiguredArtifactOwner("owner", OWNER).resolve((key, fallback) -> configured.toString())
                .equals(configured), "configurable artifact owner was lost");
        rejects(() -> new ConfiguredArtifactOwner("owner", OWNER).resolve((key, fallback) -> "invalid"));
    }

    private static void unissuedAndForgedMarkersAreRejected() {
        final DevArtifactState before = initial();
        check(!before.matches(OWNER, OWNER, INSTANCE), "PDC markers issued an unissued artifact");
        final DevArtifactState issued = before.next(OWNER, INSTANCE, true, before.behaviorState());
        check(issued.matches(OWNER, OWNER, INSTANCE), "issued owner/instance rejected");
        check(!issued.matches(new UUID(4, 4), OWNER, INSTANCE), "another actor accepted");
        check(!issued.matches(OWNER, new UUID(4, 4), INSTANCE), "forged owner accepted");
        check(!issued.matches(OWNER, OWNER, new UUID(4, 4)), "forged instance accepted");
        check(!issued.matches(OWNER, OWNER, null), "missing instance accepted");
    }

    private static void snapshotsCannotBeChangedByCaller() {
        final List<Object> list = new ArrayList<>(List.of("original"));
        final Map<String, Object> mutable = new LinkedHashMap<>(Map.of("fields", list));
        final DevArtifactState state = new DevArtifactState(OWNER, INSTANCE, true, 1, mutable);
        list.set(0, "changed"); mutable.clear();
        check(state.behaviorState().equals(Map.of("fields", List.of("original"))), "mutable snapshot alias");
        rejects(() -> state.behaviorState().put("injected", 1));
        rejects(() -> ArtifactStateValue.freeze(Map.of("live-object", new Object())));
        rejects(() -> ArtifactStateValue.freeze(Map.of("number", Double.NaN)));
        rejects(() -> ArtifactStateValue.freeze(Map.of("one", "a".repeat(600_000), "two", "b".repeat(600_000))));
        Map<String, Object> deep = Map.of("leaf", true);
        for (int i = 0; i < 20; i++) deep = Map.of("nested", deep);
        final Map<String, Object> tooDeep = deep;
        rejects(() -> ArtifactStateValue.freeze(tooDeep));
    }

    private static void publicationWaitsForDurability() {
        final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        final List<Map<String, DevArtifactState>> disk = new ArrayList<>();
        final DevArtifactLedger ledger = new DevArtifactLedger(Map.of("artifact", initial()), queue::add, disk::add);
        final var future = ledger.commit("artifact", 0,
                s -> s.next(s.owner(), s.instanceId(), true, s.behaviorState())).toCompletableFuture();
        check(!future.isDone() && !ledger.state("artifact").issued() && disk.isEmpty(), "issued before durable write");
        queue.remove().run();
        check(future.isDone() && !future.isCompletedExceptionally() && ledger.state("artifact").issued(), "not published");
        check(disk.getFirst().get("artifact").equals(ledger.state("artifact")), "disk/runtime mismatch");
    }

    private static void failureClosesMutationAndPreservesBeforeState() {
        final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        final DevArtifactLedger ledger = new DevArtifactLedger(Map.of("artifact", initial()), queue::add,
                state -> { throw new IOException("injected fsync failure"); });
        final var future = ledger.commit("artifact", 0, s -> s.next(OWNER, INSTANCE, true, Map.of())).toCompletableFuture();
        queue.remove().run();
        check(future.isCompletedExceptionally() && !ledger.healthy(), "failed write did not close store");
        check(ledger.state("artifact").equals(initial()), "failed candidate published");
        check(!ledger.pending("artifact"), "failed write retained pending gate");
        check(ledger.save(false).toCompletableFuture().isCompletedExceptionally(), "save reset failed authority");
    }

    private static void staleRevisionAndConcurrentMutationAreRejected() {
        final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        final DevArtifactLedger ledger = new DevArtifactLedger(Map.of("artifact", initial()), queue::add, s -> {});
        check(ledger.updateVolatile("artifact", 0, Map.of("progress-millis", 124L)), "progress update refused");
        check(ledger.commit("artifact", 0, s -> s.next(OWNER, INSTANCE, true, Map.of()))
                .toCompletableFuture().isCompletedExceptionally(), "stale revision accepted");
        ledger.commit("artifact", 1, s -> s.next(OWNER, INSTANCE, true, s.behaviorState()));
        check(!ledger.updateVolatile("artifact", 1, Map.of()), "pending durable candidate overwritten");
        check(ledger.commit("artifact", 1, s -> s.next(OWNER, INSTANCE, true, Map.of()))
                .toCompletableFuture().isCompletedExceptionally(), "concurrent duplicate issuance accepted");
        queue.remove().run();
        check(ledger.state("artifact").behaviorState().get("progress-millis").equals(124L), "progress lost");
    }

    private static void continuationCanStartNextMutation() {
        final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        final DevArtifactLedger ledger = new DevArtifactLedger(Map.of("artifact", initial()), queue::add, s -> {});
        final CompletableFuture<DevArtifactState> completion = ledger.commit("artifact", 0,
                s -> s.next(OWNER, INSTANCE, true, s.behaviorState()))
                .thenCompose(s -> ledger.commit("artifact", s.revision(),
                        next -> next.next(OWNER, INSTANCE, true, Map.of("continued", true)))).toCompletableFuture();
        queue.remove().run();
        check(ledger.pending("artifact") && queue.size() == 1, "completion retained or cleared another operation's gate");
        queue.remove().run();
        check(completion.isDone() && !completion.isCompletedExceptionally(), "continuation failed");
        check(ledger.state("artifact").revision() == 2, "continuation revision lost");
    }

    private static void serializedArtifactsPreserveEachOther() {
        final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        final List<Map<String, DevArtifactState>> disk = new ArrayList<>();
        final DevArtifactLedger ledger = new DevArtifactLedger(Map.of("first", initial(), "second", initial()),
                queue::add, disk::add);
        ledger.commit("first", 0, s -> s.next(OWNER, UUID.randomUUID(), true, s.behaviorState()));
        ledger.commit("second", 0, s -> s.next(OWNER, UUID.randomUUID(), true, s.behaviorState()));
        queue.remove().run(); queue.remove().run();
        check(disk.getLast().values().stream().allMatch(DevArtifactState::issued), "second file write lost first artifact");
        check(disk.getLast().equals(ledger.snapshot()), "multi-artifact persisted snapshot drift");
    }

    private static void shutdownDrainsAcceptedMutationAndRefusesNewWork() {
        final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        final List<Map<String, DevArtifactState>> disk = new ArrayList<>();
        final DevArtifactLedger ledger = new DevArtifactLedger(Map.of("artifact", initial()), queue::add, disk::add);
        ledger.commit("artifact", 0, s -> s.next(OWNER, INSTANCE, true, s.behaviorState()));
        final var close = ledger.save(true).toCompletableFuture();
        check(!ledger.updateVolatile("artifact", 0, Map.of()), "shutdown accepted transient mutation");
        check(ledger.commit("artifact", 0, s -> s.next(OWNER, INSTANCE, true, Map.of()))
                .toCompletableFuture().isCompletedExceptionally(), "shutdown accepted issuance");
        queue.remove().run(); queue.remove().run();
        check(close.isDone() && !close.isCompletedExceptionally(), "shutdown drain failed");
        check(disk.getLast().get("artifact").issued(), "shutdown lost accepted durable mutation");
    }

    private static void executorRejectionFailsClosed() {
        final DevArtifactLedger ledger = new DevArtifactLedger(Map.of("artifact", initial()),
                task -> { throw new java.util.concurrent.RejectedExecutionException(); }, s -> {});
        check(ledger.commit("artifact", 0, s -> s.next(OWNER, INSTANCE, true, Map.of()))
                .toCompletableFuture().isCompletedExceptionally(), "rejected queue accepted mutation");
        check(!ledger.healthy() && !ledger.pending("artifact") && !ledger.state("artifact").issued(), "rejection leaked state");
    }

    private static void modelStatePrecedence() {
        for (int mask = 0; mask < 8; mask++) {
            final var state = DevArtifactPresentation.state((mask & 1) != 0, (mask & 2) != 0, (mask & 4) != 0);
            final String expected = mask >= 4 ? "CANON_ARMED" : mask >= 2 ? "THREAD_HELD"
                    : mask == 1 ? "SUBJECT_LOCKED" : "IDLE";
            check(state.name().equals(expected), "model state priority drift");
        }
    }

    private static void rejects(final Runnable action) {
        try { action.run(); } catch (final RuntimeException expected) { return; }
        throw new AssertionError("Invalid mutation/value was accepted");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
