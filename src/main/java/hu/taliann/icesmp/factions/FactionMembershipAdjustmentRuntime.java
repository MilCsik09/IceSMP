package hu.taliann.icesmp.factions;

import hu.taliann.icesmp.playerprofile.application.PlayerProfileAuthority;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileFactionStore;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileFactionStore.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** FactionManager-owned continuation; callers enter on profile/IO authority, never a world thread. */
public final class FactionMembershipAdjustmentRuntime {
    private final PlayerProfileFactionStore store;
    private final Consumer<Runnable> admissionBarrier;
    private final Consumer<UUID> durableCleanup;
    private final FactionMembershipTransitionGate gate = new FactionMembershipTransitionGate();
    private final Map<UUID, MembershipAdjustment> requests = new ConcurrentHashMap<>();
    private final AtomicBoolean sweeping = new AtomicBoolean();
    private Iterator<UUID> durableOwners = Collections.emptyIterator();
    private UUID transientCursor = new UUID(0, 0);
    private volatile boolean closed;

    public FactionMembershipAdjustmentRuntime(final PlayerProfileFactionStore store,
            final Consumer<Runnable> admissionBarrier, final Consumer<UUID> durableCleanup) {
        this.store = Objects.requireNonNull(store);
        this.admissionBarrier = Objects.requireNonNull(admissionBarrier);
        this.durableCleanup = Objects.requireNonNull(durableCleanup);
    }

    public boolean pending(final UUID playerId) {
        if (playerId == null) return true;
        if (gate.pending(playerId)) return true;
        try { return !store.pendingAdjustmentEffects(playerId).isEmpty(); }
        catch (final RuntimeException | LinkageError unavailable) { return true; }
    }

    public CompletionStage<AdjustmentResult> adjust(final UUID playerId, final MembershipAdjustment request,
                                                   final Runnable commitAdmission) {
        Objects.requireNonNull(playerId); Objects.requireNonNull(request); Objects.requireNonNull(commitAdmission);
        final boolean[] claimed = {false};
        try {
            claim(playerId, request, false, claimed);
            return store.adjustMembership(playerId, request, () -> { requireOpen(); commitAdmission.run(); })
                    .thenCompose(result -> completeApplied(playerId, request).thenApply(ignored -> result))
                    .whenComplete((result, failure) -> settled(playerId, request.operationId(), failure));
        } catch (final RuntimeException | LinkageError failure) {
            if (claimed[0]) gate.pause(playerId, request.operationId());
            return CompletableFuture.failedFuture(failure);
        }
    }

    /** Recovery observes the profile transaction; it never resubmits the membership mutation. */
    public CompletionStage<AdjustmentObservation> reconcile(final UUID playerId, final MembershipAdjustment request) {
        Objects.requireNonNull(playerId); Objects.requireNonNull(request);
        final boolean[] claimed = {false};
        try {
            claim(playerId, request, true, claimed);
            return PlayerProfileAuthority.current().repository().refreshSnapshot(playerId).thenCompose(snapshot -> {
                final AdjustmentObservation observed = store.observeAdjustment(playerId, request);
                if (observed == AdjustmentObservation.BEFORE) return CompletableFuture.completedFuture(observed);
                if (observed != AdjustmentObservation.APPLIED) {
                    // A conflicting, unapplied or already settled transaction owns no unfinished role cleanup.
                    if (store.pendingAdjustmentEffects(playerId).stream().noneMatch(p -> p.operationId().equals(request.operationId()))) {
                        release(playerId, request.operationId());
                    }
                    return CompletableFuture.failedFuture(new Rejected("CONFLICT"));
                }
                return completeApplied(playerId, request).thenApply(ignored -> AdjustmentObservation.APPLIED);
            }).whenComplete((result, failure) -> settled(playerId, request.operationId(), failure));
        } catch (final RuntimeException | LinkageError failure) {
            if (claimed[0]) gate.pause(playerId, request.operationId());
            return CompletableFuture.failedFuture(failure);
        }
    }

    private CompletionStage<Void> completeApplied(final UUID playerId, final MembershipAdjustment request) {
        requireOpen();
        if (store.observeAdjustment(playerId, request) != AdjustmentObservation.APPLIED) {
            return CompletableFuture.failedFuture(new Rejected("CONFLICT"));
        }
        if (store.adjustmentEffectsCompleted(playerId, request)) return CompletableFuture.completedFuture(null);
        durableCleanup.accept(playerId);
        return store.completeAdjustmentEffects(playerId, request).thenApply(ignored -> null);
    }

    private void claim(final UUID playerId, final MembershipAdjustment request, final boolean recovery, final boolean[] claimed) {
        requireOpen();
        final UUID operationId = request.operationId();
        final boolean[] invoked = {false};
        final Thread caller = Thread.currentThread();
        admissionBarrier.accept(() -> {
            if (Thread.currentThread() != caller || invoked[0]) throw new Rejected("INVALID_ADMISSION_BARRIER");
            invoked[0] = true;
            requireOpen();
            if (!(recovery ? gate.resume(playerId, operationId) : gate.claim(playerId, operationId))) {
                throw new Rejected("FACTION_TRANSITION_BUSY");
            }
            claimed[0] = true;
            requests.put(playerId, request);
        });
        if (!invoked[0]) throw new Rejected("INVALID_ADMISSION_BARRIER");
    }

    private void settled(final UUID playerId, final UUID operationId, final Throwable failure) {
        if (failure == null) release(playerId, operationId);
        else gate.pause(playerId, operationId);
    }

    private void release(final UUID playerId, final UUID operationId) {
        // Remove evidence before releasing admission; a replacement request must not be removed.
        requests.computeIfPresent(playerId, (id, request) -> request.operationId().equals(operationId) ? null : request);
        gate.release(playerId, operationId);
    }

    /** Async maintenance admits eight paused leases and eight durable owners, one continuation at a time. */
    public CompletionStage<Void> pulse() {
        if (closed || !sweeping.compareAndSet(false, true)) return CompletableFuture.completedFuture(null);
        try {
            final UUID cursor = transientCursor;
            final var paused = requests.entrySet().stream()
                    .filter(e -> gate.paused(e.getKey(), e.getValue().operationId()))
                    .sorted(Comparator.<Map.Entry<UUID, MembershipAdjustment>, Boolean>comparing(e -> e.getKey().compareTo(cursor) <= 0)
                            .thenComparing(Map.Entry::getKey)).limit(8).toList();
            CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
            for (final var entry : paused) {
                transientCursor = entry.getKey();
                chain = chain.thenCompose(ignored -> reconcile(entry.getKey(), entry.getValue()).handle((result, failure) -> null));
            }
            return chain.thenCompose(ignored -> {
                if (closed) return CompletableFuture.completedFuture(null);
                final CompletionStage<Void> enumeration = durableOwners.hasNext() ? CompletableFuture.completedFuture(null)
                        : PlayerProfileAuthority.current().repository().listPlayerIds().thenAccept(ids -> durableOwners = ids.iterator());
                return enumeration.thenCompose(nothing -> sweepDurableOwners());
            }).whenComplete((result, failure) -> sweeping.set(false));
        } catch (final RuntimeException | LinkageError failure) {
            sweeping.set(false); return CompletableFuture.failedFuture(failure);
        }
    }

    private CompletionStage<Void> sweepDurableOwners() {
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        for (int i = 0; i < 8 && durableOwners.hasNext(); i++) {
            final UUID id = durableOwners.next();
            chain = chain.thenCompose(ignored -> {
                if (closed) return CompletableFuture.completedFuture(null);
                return PlayerProfileAuthority.current().repository().find(id).thenCompose(snapshot -> {
                    if (snapshot.isEmpty() || closed) return CompletableFuture.completedFuture(null);
                    final var pending = store.pendingAdjustmentEffects(id);
                    // Multiple unfinished transitions require review; never guess cleanup ordering.
                    return pending.size() == 1 ? reconcile(id, pending.getFirst()).thenApply(value -> (Void) null)
                            : CompletableFuture.<Void>completedFuture(null);
                }).handle((value, failure) -> null);
            });
        }
        return chain;
    }

    public void close() { closed = true; }
    private void requireOpen() { if (closed) throw new Rejected("FACTION_RUNTIME_CLOSED"); }

    public static final class Rejected extends IllegalStateException {
        private final String code;
        public Rejected(final String code) { super(code); this.code = code; }
        public String code() { return code; }
    }
}
