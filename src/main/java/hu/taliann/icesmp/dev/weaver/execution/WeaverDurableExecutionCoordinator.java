package hu.taliann.icesmp.dev.weaver.execution;

import hu.taliann.icesmp.dev.weaver.WeaverAuthorityToken;
import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.subject.SubjectSnapshot;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Only acknowledged PREPARED operations can enter stages; uncertain or partially executed failures retain quarantine. */
public final class WeaverDurableExecutionCoordinator {
    private final WeaverOwnerRouter router;
    private final WeaverJournal journal;
    private final WeaverTypeRegistry types;
    private final AtomicBoolean inFlight = new AtomicBoolean();
    private volatile boolean closed;
    public WeaverDurableExecutionCoordinator(final WeaverOwnerRouter router, final WeaverJournal journal, final WeaverTypeRegistry types) {
        this.router = Objects.requireNonNull(router); this.journal = Objects.requireNonNull(journal); this.types = Objects.requireNonNull(types);
    }
    public CompletionStage<WeaverReceipt> execute(final String providerId, final ProviderContext context, final SubjectSnapshot snapshot,
            final ActionRequest request, final PreparedAction prepared, final PreparedEffects effects, final Supplier<WeaverAuthorityToken> actorGuard) {
        return execute(providerId, context, snapshot, request, prepared, effects, Optional.empty(), actorGuard);
    }
    public CompletionStage<WeaverReceipt> execute(final String providerId, final ProviderContext context, final SubjectSnapshot snapshot,
            final ActionRequest request, final PreparedAction prepared, final PreparedEffects effects, final Optional<WeaverUndoClaim> undoClaim, final Supplier<WeaverAuthorityToken> actorGuard) {
        context.authority().requireValid();
        if (closed || !journal.ready()) return CompletableFuture.failedFuture(new WeaverDomainRejection("DURABLE_EXECUTION_UNAVAILABLE"));
        if (!prepared.descriptor().requiresJournal() || !request.actionId().equals(prepared.descriptor().id()) || request.integrityMode() != context.integrityMode()
                || request.lifetime() != context.lifetime() || !prepared.descriptor().lifetimes().contains(request.lifetime())
                || !prepared.descriptor().integrityModes().contains(request.integrityMode()) || !prepared.descriptor().subjects().contains(snapshot.ref().kind())
                || !prepared.subject().equals(snapshot.ref()) || !prepared.expectedBeforeFingerprint().equals(snapshot.revisionFingerprint())) {
            return CompletableFuture.failedFuture(new WeaverDomainRejection("INVALID_EXECUTION_PLAN"));
        }
        final Set<String> parameters = new HashSet<>();
        for (final ActionParameter parameter : prepared.descriptor().parameters()) {
            parameters.add(parameter.id()); final WeaverValue value = request.parameters().get(parameter.id());
            if (value == null && parameter.required()) return CompletableFuture.failedFuture(new WeaverDomainRejection("INVALID_PARAMETERS"));
            if (value != null) parameter.validate(value, types).requireValid();
        }
        if (!parameters.containsAll(request.parameters().keySet())) return CompletableFuture.failedFuture(new WeaverDomainRejection("INVALID_PARAMETERS"));
        if (!inFlight.compareAndSet(false, true)) return CompletableFuture.failedFuture(new WeaverDomainRejection("EXECUTION_BUSY"));
        final long now = System.currentTimeMillis();
        final WeaverOperationRecord operation;
        try {
            operation = new WeaverOperationRecord(prepared.operationId(), context.authority().actor(), providerId, request, snapshot.ref(), snapshot.revisionFingerprint(),
                    Optional.empty(), prepared.recoveryPayload(), OperationStatus.PREPARED, 0, now, now, Optional.empty(), false, undoClaim);
        } catch (final RuntimeException failure) { inFlight.set(false); return CompletableFuture.failedFuture(failure); }
        final List<StageResult> results = new ArrayList<>(); final AtomicBoolean entered = new AtomicBoolean(), preparedAcknowledged = new AtomicBoolean();
        final CompletionStage<WeaverReceipt> execution = journal.prepare(operation, effects.intent()).thenCompose(ignored -> {
            preparedAcknowledged.set(true);
            CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
            for (final ExecutionStage stage : prepared.stages()) {
                chain = chain.thenCompose(done -> renew(context, actorGuard).thenCompose(authority -> router.submit(stage.owner(), authority.actor(), Duration.ofMillis(stage.timeoutMillis()), () -> {
                    if (closed) throw new WeaverDomainRejection("EXECUTION_CLOSED"); authority.requireValid(); entered.set(true);
                    return stage.apply().execute(new ExecutionContext(authority, snapshot, List.copyOf(results)), stage.payload());
                })).thenAccept(result -> {
                    Objects.requireNonNull(result); result.facts().values().forEach(types::validate); results.add(result);
                }));
            }
            return chain.thenCompose(done -> {
                final WeaverReceipt receipt = prepared.receiptFactory().create(prepared, List.copyOf(results), System.currentTimeMillis());
                validateReceipt(operation, prepared, receipt, results);
                final WeaverEffectCommit committed = Objects.requireNonNull(effects.factory().create(prepared, List.copyOf(results), receipt, Math.addExact(journal.snapshot().projectionSequence(), 1)));
                return journal.applied(operation.operationId(), 0, receipt, committed, System.currentTimeMillis())
                        .thenCompose(applied -> journal.finishAudit(applied.operationId(), applied.revision())).thenApply(applied -> receipt);
            });
        });
        return execution.handle((receipt, failure) -> failure == null ? CompletableFuture.completedFuture(receipt)
                : (preparedAcknowledged.get() ? failed(operation, prepared, snapshot, List.copyOf(results), entered.get(), failure) : CompletableFuture.<Void>completedFuture(null)).thenCompose(ignored -> CompletableFuture.<WeaverReceipt>failedFuture(root(failure))))
                .thenCompose(value -> value).whenComplete((ignored, failure) -> inFlight.set(false));
    }
    private CompletionStage<WeaverAuthorityToken> renew(final ProviderContext context, final Supplier<WeaverAuthorityToken> guard) {
        return router.submit(new ActorOwner(), context.authority().actor(), Duration.ofSeconds(5), () -> {
            if (closed) throw new WeaverDomainRejection("EXECUTION_CLOSED");
            final WeaverAuthorityToken renewed = Objects.requireNonNull(guard.get()); renewed.requireValid();
            if (!renewed.actor().equals(context.authority().actor()) || !renewed.session().equals(context.authority().session())) throw new SecurityException("Execution authority changed");
            return CompletableFuture.completedFuture(renewed);
        });
    }
    private void validateReceipt(final WeaverOperationRecord operation, final PreparedAction prepared, final WeaverReceipt receipt, final List<StageResult> results) {
        Objects.requireNonNull(receipt);
        if (!receipt.operationId().equals(operation.operationId()) || !receipt.providerId().equals(operation.providerId()) || !receipt.actionId().equals(operation.request().actionId())
                || !receipt.subject().equals(operation.subject()) || receipt.risk() != prepared.descriptor().risk() || receipt.lifetime() != operation.request().lifetime()
                || receipt.integrityMode() != operation.request().integrityMode() || receipt.status() != ReceiptStatus.COMMITTED || receipt.createdAt() < operation.preparedAt()
                || !receipt.beforeFingerprint().equals(operation.beforeFingerprint()) || !receipt.afterFingerprint().equals(results.getLast().afterFingerprint())
                || receipt.undo().isPresent() != prepared.descriptor().undoable()) throw new IllegalArgumentException("Provider receipt manifest violation");
        receipt.before().values().forEach(types::validate); receipt.after().values().forEach(types::validate);
    }
    private CompletionStage<Void> failed(final WeaverOperationRecord operation, final PreparedAction prepared, final SubjectSnapshot snapshot,
            final List<StageResult> results, final boolean entered, final Throwable failure) {
        final WeaverOperationRecord current = journal.snapshot().operations().get(operation.operationId());
        if (current == null || !journal.ready() || current.status() != OperationStatus.PREPARED) return CompletableFuture.completedFuture(null);
        final Throwable cause = root(failure);
        final boolean ambiguous = cause instanceof WeaverDomainRejection rejection && (rejection.code().equals("OWNER_TIMEOUT_STARTED") || rejection.code().equals("OWNER_SHUTDOWN_STARTED"));
        CompletionStage<Void> cleanup = CompletableFuture.completedFuture(null);
        if (!ambiguous && !closed) for (int i = results.size() - 1; i >= 0; i--) {
            final ExecutionStage stage = prepared.stages().get(i); final StageResult result = results.get(i);
            if (stage.compensate().isEmpty()) continue;
            cleanup = cleanup.thenCompose(ignored -> {
                final AtomicBoolean active = new AtomicBoolean(true);
                final var authority = new WeaverCompensationAuthority(current, stage.id(), result.afterFingerprint(),
                        () -> journal.ready() ? journal.snapshot().operations().get(operation.operationId()) : null, () -> active.get() && !closed);
                return router.submit(stage.owner(), operation.actorId(), Duration.ofMillis(stage.timeoutMillis()), () -> stage.compensate().orElseThrow()
                        .execute(new CompensationContext(authority, stage.id(), snapshot, result), stage.payload()))
                        .<Void>thenApply(value -> { value.facts().values().forEach(types::validate); return null; }).whenComplete((value, error) -> active.set(false));
            });
        }
        return cleanup.handle((ignored, compensationFailure) -> null).thenCompose(ignored -> journal.resolve(operation.operationId(), current.revision(), entered || ambiguous ? OperationStatus.NEEDS_REVIEW : OperationStatus.ABORTED, System.currentTimeMillis()))
                .thenCompose(changed -> journal.finishAudit(changed.operationId(), changed.revision())).thenApply(ignored -> null);
    }
    private static Throwable root(final Throwable failure) {
        Throwable root = failure; while (root instanceof CompletionException && root.getCause() != null) root = root.getCause(); return root;
    }
    public void close() { closed = true; }
}
