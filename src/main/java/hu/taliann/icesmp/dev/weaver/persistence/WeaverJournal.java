package hu.taliann.icesmp.dev.weaver.persistence;

import hu.taliann.icesmp.dev.weaver.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Acknowledgement is published only after fsync; any storage failure closes the writer until restart. */
public final class WeaverJournal {
    @FunctionalInterface private interface IoTask<T> { T run() throws Exception; }
    private final WeaverJournalStorage storage;
    private final java.util.function.Consumer<hu.taliann.icesmp.dev.weaver.projection.WeaverProjection> projectionValidator;
    private final ThreadPoolExecutor io;
    private final CompletableFuture<Void> closed = new CompletableFuture<>();
    private final AtomicBoolean loading = new AtomicBoolean();
    private record Publication(WeaverJournalState state, hu.taliann.icesmp.dev.weaver.integrity.WeaverInfluenceIndex influence) { }
    private WeaverJournalState state = WeaverJournalState.empty();
    private volatile Publication publication = new Publication(state, new hu.taliann.icesmp.dev.weaver.integrity.WeaverInfluenceIndex(state));
    private Map<String, WeaverAuditEntry> audit = Map.of();
    private volatile boolean ready;
    private volatile boolean failed;
    private volatile boolean closing;
    public WeaverJournal(final WeaverJournalStorage storage) {
        this(storage, projection -> { throw new WeaverDomainRejection("PROJECTION_WITHOUT_CONSUMER"); });
    }
    public WeaverJournal(final WeaverJournalStorage storage, final java.util.function.Consumer<hu.taliann.icesmp.dev.weaver.projection.WeaverProjection> projectionValidator) {
        this.storage = Objects.requireNonNull(storage); this.projectionValidator = Objects.requireNonNull(projectionValidator);
        io = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(128), task -> {
            final Thread thread = new Thread(task, "IceSMP-internal-journal-io"); thread.setDaemon(true); return thread;
        }) {
            @Override protected void terminated() { if (failed) closed.completeExceptionally(new WeaverDomainRejection("JOURNAL_UNAVAILABLE")); else closed.complete(null); }
        };
    }
    public CompletionStage<Void> load() {
        if (!loading.compareAndSet(false, true)) return CompletableFuture.failedFuture(new WeaverDomainRejection("JOURNAL_ALREADY_LOADED"));
        return submit(false, () -> {
            final WeaverJournalState loaded = checkedIo(storage::readState); final Map<String, WeaverAuditEntry> loadedAudit = Map.copyOf(checkedIo(storage::readAudit));
            if (loadedAudit.size() > 10_000) throw new IllegalArgumentException("Audit capacity exceeded");
            loaded.projections().values().forEach(projectionValidator);
            state = loaded; publication = new Publication(loaded, new hu.taliann.icesmp.dev.weaver.integrity.WeaverInfluenceIndex(loaded)); audit = loadedAudit; ready = true; return null;
        });
    }
    public boolean ready() { return ready && !failed && !closing; }
    public WeaverJournalState snapshot() { return publication.state(); }
    public hu.taliann.icesmp.dev.weaver.integrity.WeaverInfluenceIndex influenceIndex() { return publication.influence(); }
    public CompletionStage<WeaverOperationRecord> prepare(final WeaverOperationRecord prepared) {
        return prepare(prepared, hu.taliann.icesmp.dev.weaver.integrity.WeaverEffectIntent.none());
    }
    public CompletionStage<WeaverOperationRecord> prepare(final WeaverOperationRecord prepared, final hu.taliann.icesmp.dev.weaver.integrity.WeaverEffectIntent intent) {
        if (prepared.status() != OperationStatus.PREPARED || prepared.revision() != 0) return CompletableFuture.failedFuture(new WeaverDomainRejection("INVALID_PREPARED_RECORD"));
        return submit(true, () -> {
            if (state.operations().containsKey(prepared.operationId())) throw new WeaverDomainRejection("DUPLICATE_OPERATION");
            final long active = state.operations().values().stream().filter(operation -> operation.status() == OperationStatus.PREPARED || operation.status() == OperationStatus.APPLIED).count();
            if (active >= 8 || state.operations().size() >= WeaverJournalState.MAX_OPERATIONS || state.receipts().size() >= WeaverJournalState.MAX_RECEIPTS) {
                throw new WeaverDomainRejection("JOURNAL_CAPACITY");
            }
            final long reservedInfluences = state.intents().values().stream().mapToLong(value -> value.targets().size()).sum();
            if (state.influences().size() + reservedInfluences + intent.targets().size() + 1 > WeaverJournalState.MAX_INFLUENCES) throw new WeaverDomainRejection("INFLUENCE_CAPACITY");
            if (prepared.request().lifetime() != Lifetime.ONE_SHOT) {
                final var pending = state.operations().values().stream().filter(operation -> operation.status() == OperationStatus.PREPARED && operation.request().lifetime() != Lifetime.ONE_SHOT).toList();
                final long subjectCount = state.projections().values().stream().filter(projection -> projection.subject().equals(prepared.subject())).count()
                        + pending.stream().filter(operation -> operation.subject().equals(prepared.subject())).count();
                final long lifetimeCount = state.projections().values().stream().filter(projection -> projection.lifetime() == prepared.request().lifetime()).count()
                        + pending.stream().filter(operation -> operation.request().lifetime() == prepared.request().lifetime()).count();
                if (subjectCount >= 32 || lifetimeCount >= (prepared.request().lifetime() == Lifetime.SESSION ? 256 : 1024)) throw new WeaverDomainRejection("PROJECTION_CAPACITY");
            }
            publish(WeaverEffectReducer.prepared(state, prepared, intent)); return prepared;
        });
    }
    public CompletionStage<WeaverOperationRecord> applied(final UUID id, final long revision, final WeaverReceipt receipt, final long now) {
        return applied(id, revision, receipt, WeaverEffectCommit.none(), now);
    }
    public CompletionStage<WeaverOperationRecord> applied(final UUID id, final long revision, final WeaverReceipt receipt, final WeaverEffectCommit effects, final long now) {
        return submit(true, () -> {
            final WeaverOperationRecord before = expected(id, revision);
            if (before.status() != OperationStatus.PREPARED) throw new WeaverDomainRejection("INVALID_OPERATION_TRANSITION");
            final WeaverOperationRecord after = changed(before, OperationStatus.APPLIED, Optional.of(receipt.afterFingerprint()), Optional.of(receipt), true, now);
            effects.projections().forEach(projectionValidator);
            publish(WeaverEffectReducer.applied(state, after, effects)); return after;
        });
    }
    public CompletionStage<WeaverOperationRecord> resolve(final UUID id, final long revision, final OperationStatus outcome, final long now) {
        return submit(true, () -> {
            final WeaverOperationRecord before = expected(id, revision);
            final boolean legal = outcome == OperationStatus.ABORTED && before.status() == OperationStatus.PREPARED
                    || outcome == OperationStatus.COMPENSATED && before.status() == OperationStatus.APPLIED
                    || outcome == OperationStatus.NEEDS_REVIEW && (before.status() == OperationStatus.PREPARED || before.status() == OperationStatus.APPLIED);
            if (!legal) throw new WeaverDomainRejection("INVALID_OPERATION_TRANSITION");
            final WeaverOperationRecord after = changed(before, outcome, before.afterFingerprint(), before.receipt(), true, now);
            publish(WeaverEffectReducer.resolved(state, after)); return after;
        });
    }
    public CompletionStage<Integer> expireProjections(final long now, final boolean clearSession) {
        if (now < 0) return CompletableFuture.failedFuture(new WeaverDomainRejection("INVALID_EFFECT_TIME"));
        return submit(true, () -> {
            final WeaverJournalState next = WeaverEffectReducer.expired(state, now, clearSession);
            final int removed = state.projections().size() - next.projections().size();
            if (removed != 0) publish(next); return removed;
        });
    }
    public CompletionStage<WeaverOperationRecord> finishAudit(final UUID id, final long revision) {
        return submit(true, () -> {
            final WeaverOperationRecord before = expected(id, revision);
            if (!before.pendingAudit()) return before;
            final AuditOutcome outcome = switch (before.status()) {
                case APPLIED -> AuditOutcome.COMMITTED;
                case ABORTED -> AuditOutcome.ABORTED;
                case COMPENSATED -> AuditOutcome.COMPENSATED;
                case NEEDS_REVIEW -> AuditOutcome.NEEDS_REVIEW;
                default -> throw new WeaverDomainRejection("INVALID_AUDIT_TRANSITION");
            };
            final WeaverAuditEntry entry = new WeaverAuditEntry(before.operationId(), before.actorId(), before.providerId(), before.request().actionId(),
                    before.request().integrityMode(), outcome, before.updatedAt());
            final WeaverAuditEntry existing = audit.get(entry.key());
            if (existing != null && !existing.equals(entry)) throw new WeaverDomainRejection("AUDIT_CONFLICT");
            if (existing == null) {
                final Map<String, WeaverAuditEntry> next = new HashMap<>(audit); next.put(entry.key(), entry);
                if (next.size() > 10_000) {
                    final String oldest = next.values().stream().min(Comparator.comparingLong(WeaverAuditEntry::createdAt).thenComparing(WeaverAuditEntry::key)).orElseThrow().key();
                    next.remove(oldest);
                }
                checkedIo(() -> { storage.writeAudit(Map.copyOf(next)); return null; }); audit = Map.copyOf(next);
            }
            final OperationStatus status = before.status() == OperationStatus.APPLIED ? OperationStatus.COMMITTED : before.status();
            final WeaverOperationRecord after = changed(before, status, before.afterFingerprint(), before.receipt(), false, before.updatedAt());
            publish(state.replace(after)); return after;
        });
    }
    private WeaverOperationRecord expected(final UUID id, final long revision) {
        final WeaverOperationRecord record = state.operations().get(id);
        if (record == null || record.revision() != revision) throw new WeaverDomainRejection("JOURNAL_CONFLICT");
        return record;
    }
    private static WeaverOperationRecord changed(final WeaverOperationRecord before, final OperationStatus status,
            final Optional<String> fingerprint, final Optional<WeaverReceipt> receipt, final boolean audit, final long now) {
        return new WeaverOperationRecord(before.operationId(), before.actorId(), before.providerId(), before.request(), before.subject(), before.beforeFingerprint(),
                fingerprint, before.recoveryPayload(), status, Math.addExact(before.revision(), 1), before.preparedAt(), Math.max(before.updatedAt(), now), receipt, audit);
    }
    private void publish(final WeaverJournalState next) {
        final Publication nextPublication = new Publication(next, new hu.taliann.icesmp.dev.weaver.integrity.WeaverInfluenceIndex(next));
        checkedIo(() -> { storage.writeState(next); return null; }); state = next; publication = nextPublication;
    }
    private <T> T checkedIo(final IoTask<T> action) {
        try { return action.run(); }
        catch (final Throwable failure) { failed = true; ready = false; throw new WeaverDomainRejection("JOURNAL_UNAVAILABLE"); }
    }
    private synchronized <T> CompletionStage<T> submit(final boolean needsReady, final IoTask<T> action) {
        final CompletableFuture<T> result = new CompletableFuture<>();
        if (closing || failed || needsReady && !ready) return CompletableFuture.failedFuture(new WeaverDomainRejection("JOURNAL_UNAVAILABLE"));
        try {
            io.execute(() -> {
                if (failed) { result.completeExceptionally(new WeaverDomainRejection("JOURNAL_UNAVAILABLE")); return; }
                try { result.complete(action.run()); }
                catch (final WeaverDomainRejection refusal) { result.completeExceptionally(refusal); }
                catch (final RuntimeException invalid) { result.completeExceptionally(new WeaverDomainRejection("INVALID_JOURNAL_DATA")); }
                catch (final Throwable failure) { failed = true; ready = false; result.completeExceptionally(new WeaverDomainRejection("JOURNAL_UNAVAILABLE")); }
            });
        } catch (final RejectedExecutionException full) { result.completeExceptionally(new WeaverDomainRejection("JOURNAL_QUEUE_FULL")); }
        return result.minimalCompletionStage();
    }
    public synchronized CompletionStage<Void> close() { closing = true; ready = false; io.shutdown(); return closed.minimalCompletionStage(); }
}
