package hu.taliann.icesmp.dev.weaver.execution;

import hu.taliann.icesmp.dev.weaver.WeaverAuthorityToken;
import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.subject.SubjectSnapshot;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Journal-required actions cannot enter the non-durable execution path. */
public final class WeaverExecutionCoordinator {
    private final WeaverOwnerRouter router;
    private final WeaverTypeRegistry types;
    private final WeaverDurableExecutionCoordinator durable;
    private final java.util.function.BooleanSupplier durableReady;
    private final AtomicBoolean inFlight = new AtomicBoolean();
    private volatile boolean closed;
    public WeaverExecutionCoordinator(final WeaverOwnerRouter router, final WeaverTypeRegistry types) {
        this.router = Objects.requireNonNull(router); this.types = Objects.requireNonNull(types); durable = null; durableReady = () -> false;
    }
    public WeaverExecutionCoordinator(final WeaverOwnerRouter router, final WeaverTypeRegistry types,
            final hu.taliann.icesmp.dev.weaver.persistence.WeaverJournal journal, final java.util.function.BooleanSupplier integrityReady) {
        this.router = Objects.requireNonNull(router); this.types = Objects.requireNonNull(types); Objects.requireNonNull(integrityReady);
        durable = new WeaverDurableExecutionCoordinator(router, journal, types); durableReady = () -> journal.ready() && integrityReady.getAsBoolean();
    }
    public boolean available(final ActionDescriptor descriptor, final Lifetime lifetime) {
        return !closed && (descriptor.requiresJournal() ? durable != null && durableReady.getAsBoolean() : lifetime == Lifetime.ONE_SHOT);
    }
    public CompletionStage<WeaverReceipt> execute(final String providerId, final ProviderContext context, final SubjectSnapshot snapshot,
            final ActionRequest request, final PreparedAction prepared, final Optional<PreparedEffects> effects, final Supplier<WeaverAuthorityToken> actorGuard) {
        return execute(providerId, context, snapshot, request, prepared, effects, Optional.empty(), actorGuard);
    }
    public CompletionStage<WeaverReceipt> execute(final String providerId, final ProviderContext context, final SubjectSnapshot snapshot,
            final ActionRequest request, final PreparedAction prepared, final Optional<PreparedEffects> effects,
            final Optional<hu.taliann.icesmp.dev.weaver.persistence.WeaverUndoClaim> undoClaim, final Supplier<WeaverAuthorityToken> actorGuard) {
        if (undoClaim.isPresent() && !prepared.descriptor().requiresJournal()) return CompletableFuture.failedFuture(new WeaverDomainRejection("UNDO_ACTION_UNAVAILABLE"));
        if (!prepared.descriptor().requiresJournal()) return execute(providerId, context, snapshot, prepared, actorGuard);
        if (!available(prepared.descriptor(), context.lifetime()) || effects.isEmpty()) return CompletableFuture.failedFuture(new WeaverDomainRejection("DURABLE_EXECUTION_UNAVAILABLE"));
        return durable.execute(providerId, context, snapshot, request, prepared, effects.get(), undoClaim, actorGuard);
    }
    public CompletionStage<WeaverReceipt> execute(final String providerId, final ProviderContext context, final SubjectSnapshot snapshot,
                                                  final PreparedAction prepared, final Supplier<WeaverAuthorityToken> actorGuard) {
        context.authority().requireValid();
        if (prepared.descriptor().requiresJournal() || !available(prepared.descriptor(), context.lifetime())) return CompletableFuture.failedFuture(new WeaverDomainRejection("DURABLE_EXECUTION_UNAVAILABLE"));
        if (!prepared.subject().equals(snapshot.ref()) || !prepared.expectedBeforeFingerprint().equals(snapshot.revisionFingerprint())
                || !prepared.descriptor().subjects().contains(snapshot.ref().kind()) || !prepared.descriptor().lifetimes().contains(context.lifetime())
                || !prepared.descriptor().integrityModes().contains(context.integrityMode())) return CompletableFuture.failedFuture(new WeaverDomainRejection("INVALID_EXECUTION_PLAN"));
        if (!inFlight.compareAndSet(false, true)) return CompletableFuture.failedFuture(new WeaverDomainRejection("EXECUTION_BUSY"));
        try {
            CompletionStage<List<StageResult>> chain = CompletableFuture.completedFuture(List.of());
            for (final ExecutionStage stage : prepared.stages()) {
                chain = chain.thenCompose(previous -> router.submit(new ActorOwner(), context.authority().actor(), Duration.ofSeconds(5), () -> {
                    if (closed) throw new WeaverDomainRejection("EXECUTION_CLOSED");
                    final WeaverAuthorityToken renewed = Objects.requireNonNull(actorGuard.get()); renewed.requireValid();
                    if (!renewed.actor().equals(context.authority().actor()) || !renewed.session().equals(context.authority().session())) throw new SecurityException("Execution authority changed");
                    return CompletableFuture.completedFuture(renewed);
                }).thenCompose(authority -> router.submit(stage.owner(), authority.actor(), Duration.ofMillis(stage.timeoutMillis()), () -> {
                    if (closed) throw new WeaverDomainRejection("EXECUTION_CLOSED");
                    authority.requireValid();
                    return stage.apply().execute(new ExecutionContext(authority, snapshot, previous), stage.payload());
                })).thenApply(result -> {
                    Objects.requireNonNull(result); result.facts().values().forEach(types::validate);
                    final List<StageResult> next = new ArrayList<>(previous); next.add(result); return List.copyOf(next);
                }));
            }
            return chain.thenApply(results -> {
                final WeaverReceipt receipt = Objects.requireNonNull(prepared.receiptFactory().create(prepared, results, System.currentTimeMillis()));
                if (!receipt.providerId().equals(providerId) || !receipt.operationId().equals(prepared.operationId())
                        || !receipt.actionId().equals(prepared.descriptor().id()) || !receipt.subject().equals(snapshot.ref())
                        || receipt.risk() != prepared.descriptor().risk() || receipt.lifetime() != context.lifetime()
                        || receipt.integrityMode() != context.integrityMode() || receipt.status() != ReceiptStatus.COMMITTED
                        || !receipt.beforeFingerprint().equals(snapshot.revisionFingerprint())
                        || !receipt.afterFingerprint().equals(results.getLast().afterFingerprint())
                        || receipt.undo().isPresent() != prepared.descriptor().undoable()) throw new IllegalArgumentException("Provider receipt manifest violation");
                receipt.before().values().forEach(types::validate); receipt.after().values().forEach(types::validate);
                return receipt;
            }).whenComplete((ignored, failure) -> inFlight.set(false));
        } catch (final RuntimeException failure) {
            inFlight.set(false); return CompletableFuture.failedFuture(failure);
        }
    }
    public void close() { closed = true; if (durable != null) durable.close(); }
}
