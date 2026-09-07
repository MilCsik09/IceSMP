package hu.taliann.icesmp.factions;

import hu.taliann.icesmp.playerprofile.application.PlayerProfileAuthority;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileFactionStore;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileFactionStore.*;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/** FactionManager-owned continuation; callers enter on profile/IO authority, never a world thread. */
public final class FactionMembershipAdjustmentRuntime {
    private final PlayerProfileFactionStore store;
    private final Consumer<Runnable> admissionBarrier;
    private final Consumer<UUID> durableCleanup;
    private final FactionMembershipTransitionGate gate = new FactionMembershipTransitionGate();

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
            claim(playerId, request.operationId(), false, claimed);
            return store.adjustMembership(playerId, request, commitAdmission)
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
            claim(playerId, request.operationId(), true, claimed);
            return PlayerProfileAuthority.current().repository().refreshSnapshot(playerId).thenCompose(snapshot -> {
                final AdjustmentObservation observed = store.observeAdjustment(playerId, request);
                if (observed == AdjustmentObservation.BEFORE) return CompletableFuture.completedFuture(observed);
                if (observed != AdjustmentObservation.APPLIED) {
                    // A conflicting, unapplied or already settled transaction owns no unfinished role cleanup.
                    if (store.pendingAdjustmentEffects(playerId).stream().noneMatch(p -> p.operationId().equals(request.operationId()))) {
                        gate.release(playerId, request.operationId());
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
        if (store.observeAdjustment(playerId, request) != AdjustmentObservation.APPLIED) {
            return CompletableFuture.failedFuture(new Rejected("CONFLICT"));
        }
        if (store.adjustmentEffectsCompleted(playerId, request)) return CompletableFuture.completedFuture(null);
        durableCleanup.accept(playerId);
        return store.completeAdjustmentEffects(playerId, request).thenApply(ignored -> null);
    }

    private void claim(final UUID playerId, final UUID operationId, final boolean recovery, final boolean[] claimed) {
        final boolean[] invoked = {false};
        final Thread caller = Thread.currentThread();
        admissionBarrier.accept(() -> {
            if (Thread.currentThread() != caller || invoked[0]) throw new Rejected("INVALID_ADMISSION_BARRIER");
            invoked[0] = true;
            if (!(recovery ? gate.resume(playerId, operationId) : gate.claim(playerId, operationId))) {
                throw new Rejected("FACTION_TRANSITION_BUSY");
            }
            claimed[0] = true;
        });
        if (!invoked[0]) throw new Rejected("INVALID_ADMISSION_BARRIER");
    }

    private void settled(final UUID playerId, final UUID operationId, final Throwable failure) {
        if (failure == null) gate.release(playerId, operationId);
        else gate.pause(playerId, operationId);
    }

    public static final class Rejected extends IllegalStateException {
        private final String code;
        public Rejected(final String code) { super(code); this.code = code; }
        public String code() { return code; }
    }
}
