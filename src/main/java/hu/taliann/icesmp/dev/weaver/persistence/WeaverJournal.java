package hu.taliann.icesmp.dev.weaver.persistence;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.integrity.*;
import hu.taliann.icesmp.integrity.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Acknowledgement is published only after fsync; any storage failure closes the writer until restart. */
public final class WeaverJournal {
    @FunctionalInterface private interface IoTask<T> { T run() throws Exception; }
    private final WeaverJournalStorage storage;
    private final java.util.function.Consumer<hu.taliann.icesmp.dev.weaver.projection.WeaverProjection> projectionValidator;
    private final java.util.function.LongSupplier effectClock;
    private final java.util.function.Function<GameplayEffectContext, WeaverValue> lifetimeResolver;
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
        this(storage, projectionValidator, System::currentTimeMillis);
    }
    public WeaverJournal(final WeaverJournalStorage storage, final java.util.function.Consumer<hu.taliann.icesmp.dev.weaver.projection.WeaverProjection> projectionValidator,
            final java.util.function.LongSupplier effectClock) {
        this(storage, projectionValidator, effectClock, context -> { throw new WeaverDomainRejection("LIFETIME_CONSUMER_UNAVAILABLE"); });
    }
    public WeaverJournal(final WeaverJournalStorage storage, final java.util.function.Consumer<hu.taliann.icesmp.dev.weaver.projection.WeaverProjection> projectionValidator,
            final java.util.function.LongSupplier effectClock, final java.util.function.Function<GameplayEffectContext, WeaverValue> lifetimeResolver) {
        this.storage = Objects.requireNonNull(storage); this.projectionValidator = Objects.requireNonNull(projectionValidator);
        this.effectClock = Objects.requireNonNull(effectClock);
        this.lifetimeResolver = Objects.requireNonNull(lifetimeResolver);
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
            // Historical projections remain durable even if their provider/content is unavailable.
            // WeaverJournalState verifies origin, scope and influence; runtime consumers fail closed.
            state = loaded; publication = new Publication(loaded, new hu.taliann.icesmp.dev.weaver.integrity.WeaverInfluenceIndex(loaded)); audit = loadedAudit; ready = true; return null;
        });
    }
    public boolean ready() { return ready && !failed && !closing; }
    public WeaverJournalState snapshot() { return publication.state(); }
    public hu.taliann.icesmp.dev.weaver.integrity.WeaverInfluenceIndex influenceIndex() { return publication.influence(); }
    /** Native effects await durable target lineage. This route can only strengthen quarantine, never grant a reward. */
    public CompletionStage<GameplayEffectPermit> prepareDerivedEffect(final GameplayEffectContext context) {
        Objects.requireNonNull(context);
        if (!ready()) return CompletableFuture.completedFuture(GameplayEffectPermit.denied());
        final long observedAt = effectClock.getAsLong();
        final var captured = publication.influence().trace(context.sources(), observedAt);
        if (captured.uncertain() || captured.origins().size() > 128) return CompletableFuture.completedFuture(GameplayEffectPermit.denied());
        // Clean gameplay needs no journal queue/write. The one-use permit still rechecks source and lifecycle admission.
        if (captured.clean()) return CompletableFuture.completedFuture(effectPermit(context, Set.of(), Math.addExact(observedAt, 5000), Optional.empty()));
        // A tick-based lingering effect can outlive a wall-clock estimate during lag/logout.
        // Monotonic targets are safe; temporary targets need an observed-lifetime consumer first.
        final boolean needsObserver = (context.durationMillis() > 0 || context.lifetime().isPresent())
                && context.targets().stream().anyMatch(target -> !WeaverEffectReducer.propagationTarget(target).monotonic());
        return submit(true, () -> {
            final long now = effectClock.getAsLong();
            final var current = publication.influence().trace(context.sources(), now);
            if (current.uncertain()) return GameplayEffectPermit.denied();
            final Set<DeveloperInfluence> origins = new HashSet<>(captured.origins()); origins.addAll(current.origins());
            final Optional<WeaverValue> lifetime = needsObserver ? Optional.of(Objects.requireNonNull(lifetimeResolver.apply(context))) : Optional.empty();
            // Five seconds for the owner continuation, effect duration, then at least five minutes after its end.
            final long admissionUntil = Math.addExact(Math.max(now, observedAt), 5000);
            final long until = Math.addExact(Math.addExact(admissionUntil, context.durationMillis()), PlayerQuarantine.MINIMUM_TAIL_MILLIS);
            final var next = WeaverEffectReducer.propagated(state, origins, context.targets(), until, lifetime);
            if (next != state) publish(next);
            return effectPermit(context, Set.copyOf(origins), admissionUntil, lifetime);
        }).exceptionally(unavailable -> GameplayEffectPermit.denied());
    }
    private GameplayEffectPermit effectPermit(final GameplayEffectContext context, final Set<DeveloperInfluence> admitted, final long admissionUntil,
            final Optional<WeaverValue> observedLifetime) {
        final long issued = System.nanoTime();
        return GameplayEffectPermit.guarded(() -> {
            if (!ready() || System.nanoTime() - issued >= TimeUnit.SECONDS.toNanos(5)) return false;
            final long now = effectClock.getAsLong(); if (now < 0 || now > admissionUntil) return false;
            final Publication current = publication;
            final var source = current.influence().trace(context.sources(), now);
            if (source.uncertain() || !admitted.containsAll(source.origins())) return false;
            for (final DeveloperInfluence origin : admitted) for (final RewardSource target : context.targets()) {
                final var exact = WeaverEffectReducer.propagationTarget(target);
                final var lifetime = exact.monotonic() ? Optional.<WeaverValue>empty() : observedLifetime;
                final var evidence = current.state().influences().get(WeaverEffectReducer.propagatedId(origin, exact, lifetime));
                if (evidence == null || !evidence.influence().equals(origin) || !evidence.target().equals(exact) || !evidence.quarantines(now)
                        || !evidence.observedLifetime().equals(lifetime) || lifetime.isPresent() && !evidence.active()) return false;
            }
            return ready();
        });
    }
    public CompletionStage<Boolean> endObservedInfluence(final WeaverInfluenceRecord expected, final GameplayEffectGate.ObservationFence fence) {
        Objects.requireNonNull(expected); Objects.requireNonNull(fence);
        return submit(true, () -> {
            if (!expected.active() || expected.observedLifetime().isEmpty() || !fence.activeFor(expected.target().source())
                    || !expected.equals(state.influences().get(expected.id()))) return false;
            final Map<UUID, WeaverInfluenceRecord> influences = new HashMap<>(state.influences());
            influences.put(expected.id(), expected.ended(Math.max(effectClock.getAsLong(), expected.influence().appliedAt())));
            publish(new WeaverJournalState(Math.addExact(state.revision(), 1), state.operations(), state.receipts(), state.projectionSequence(),
                    state.intents(), state.projections(), influences, state.effectDeltas()));
            return true;
        });
    }
    public CompletionStage<WeaverOperationRecord> prepare(final WeaverOperationRecord prepared) {
        return prepare(prepared, hu.taliann.icesmp.dev.weaver.integrity.WeaverEffectIntent.none());
    }
    public CompletionStage<WeaverOperationRecord> prepare(final WeaverOperationRecord prepared, final hu.taliann.icesmp.dev.weaver.integrity.WeaverEffectIntent intent) {
        if (prepared.status() != OperationStatus.PREPARED || prepared.revision() != 0) return CompletableFuture.failedFuture(new WeaverDomainRejection("INVALID_PREPARED_RECORD"));
        return submit(true, () -> {
            if (state.operations().containsKey(prepared.operationId()) || audit.values().stream().anyMatch(entry -> entry.operationId().equals(prepared.operationId()))) throw new WeaverDomainRejection("DUPLICATE_OPERATION");
            final var effectiveIntent = WeaverOperationScope.intent(prepared, intent);
            final Set<UUID> protectedHistory = new HashSet<>();
            prepared.undoClaim().ifPresent(claim -> { final WeaverReceipt original = state.receipts().get(claim.receiptId()); if (original != null) protectedHistory.add(original.operationId()); });
            WeaverJournalState admitted = WeaverJournalRetention.trim(state, System.currentTimeMillis(), WeaverJournalState.MAX_RECEIPTS - 1, WeaverJournalState.MAX_OPERATIONS - 1, protectedHistory);
            final long active = state.operations().values().stream().filter(operation -> operation.status() == OperationStatus.PREPARED || operation.status() == OperationStatus.APPLIED).count();
            if (active >= 8 || admitted.operations().size() >= WeaverJournalState.MAX_OPERATIONS || admitted.receipts().size() >= WeaverJournalState.MAX_RECEIPTS) {
                throw new WeaverDomainRejection("JOURNAL_CAPACITY");
            }
            final long reservedInfluences = admitted.intents().values().stream().mapToLong(value -> value.targets().size()).sum();
            if (admitted.influences().size() + reservedInfluences + effectiveIntent.targets().size() > WeaverJournalState.MAX_INFLUENCES) throw new WeaverDomainRejection("INFLUENCE_CAPACITY");
            if (prepared.request().lifetime() != Lifetime.ONE_SHOT) {
                final var requested = WeaverOperationScope.reservations(prepared);
                final var pending = state.operations().values().stream().filter(operation -> (operation.status() == OperationStatus.PREPARED
                        || operation.status() == OperationStatus.NEEDS_REVIEW && operation.receipt().isEmpty()) && operation.request().lifetime() != Lifetime.ONE_SHOT).toList();
                final Map<hu.taliann.icesmp.dev.weaver.subject.SubjectRef, Integer> subjectCounts = new HashMap<>();
                state.projections().values().forEach(projection -> subjectCounts.merge(projection.subject(), 1, Integer::sum));
                pending.forEach(operation -> WeaverOperationScope.reservations(operation).forEach((ref, count) -> subjectCounts.merge(ref, count, Integer::sum)));
                final long lifetimeCount = state.projections().values().stream().filter(projection -> projection.lifetime() == prepared.request().lifetime()).count()
                        + pending.stream().filter(operation -> operation.request().lifetime() == prepared.request().lifetime())
                                .mapToInt(operation -> WeaverOperationScope.reservations(operation).values().stream().mapToInt(Integer::intValue).sum()).sum();
                if (requested.entrySet().stream().anyMatch(entry -> subjectCounts.getOrDefault(entry.getKey(), 0) + entry.getValue() > 32)
                        || lifetimeCount + requested.values().stream().mapToInt(Integer::intValue).sum() > (prepared.request().lifetime() == Lifetime.SESSION ? 256 : 1024)) throw new WeaverDomainRejection("PROJECTION_CAPACITY");
            }
            while (true) {
                final WeaverJournalState next = WeaverEffectReducer.prepared(admitted, prepared, effectiveIntent);
                try { storage.validateStateCapacity(next); publish(next); return prepared; }
                catch (final WeaverDomainRejection rejected) {
                    if (!rejected.code().equals("JOURNAL_BYTE_CAPACITY")) throw rejected;
                    final int batch = Math.max(16, admitted.operations().size() / 8);
                    final WeaverJournalState trimmed = WeaverJournalRetention.trim(admitted, System.currentTimeMillis(), Math.max(0, admitted.receipts().size() - batch), Math.max(0, admitted.operations().size() - batch), protectedHistory);
                    if (trimmed == admitted) throw rejected;
                    admitted = trimmed;
                }
            }
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
                case APPLIED -> before.undoClaim().isPresent() ? AuditOutcome.UNDONE : AuditOutcome.COMMITTED;
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
                    final String oldest = next.values().stream().filter(value -> !value.key().equals(entry.key()))
                            .min(Comparator.comparingLong(WeaverAuditEntry::createdAt).thenComparing(WeaverAuditEntry::key)).orElseThrow().key();
                    next.remove(oldest);
                }
                while (true) {
                    try { storage.validateAuditCapacity(Map.copyOf(next)); break; }
                    catch (final WeaverDomainRejection rejected) {
                        if (!rejected.code().equals("JOURNAL_BYTE_CAPACITY") || next.size() == 1) throw rejected;
                        final List<String> oldest = next.values().stream().filter(value -> !value.key().equals(entry.key()))
                                .sorted(Comparator.comparingLong(WeaverAuditEntry::createdAt).thenComparing(WeaverAuditEntry::key))
                                .limit(Math.max(1, next.size() / 8)).map(WeaverAuditEntry::key).toList();
                        oldest.forEach(next::remove);
                    }
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
                fingerprint, before.recoveryPayload(), status, Math.addExact(before.revision(), 1), before.preparedAt(), Math.max(before.updatedAt(), now), receipt, audit, before.undoClaim());
    }
    private void publish(final WeaverJournalState next) {
        storage.validateStateCapacity(next);
        // New projections were validated at APPLIED. Revalidating historical entries here would
        // let removed content disable unrelated writes, cleanup, audit and influence publication.
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
