package hu.taliann.icesmp.dev.weaver.integrity;

import hu.taliann.icesmp.dev.weaver.persistence.WeaverJournal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/** Sixteen owner observations per pulse; unknown/offline consumers retain active durable quarantine. */
public final class WeaverInfluenceDispatcher implements AutoCloseable {
    private final WeaverJournal journal;
    private final Function<WeaverInfluenceRecord, CompletionStage<InfluenceObservation>> observer;
    private final AtomicBoolean inFlight = new AtomicBoolean();
    private volatile boolean closed;
    private int cursor;
    public WeaverInfluenceDispatcher(WeaverJournal journal, Function<WeaverInfluenceRecord, CompletionStage<InfluenceObservation>> observer) {
        this.journal = Objects.requireNonNull(journal); this.observer = Objects.requireNonNull(observer);
    }
    public CompletionStage<Void> pulse() {
        if (closed || !journal.ready() || !inFlight.compareAndSet(false, true)) return CompletableFuture.completedFuture(null);
        final var records = journal.snapshot().influences().values().stream().filter(value -> value.active() && value.observedLifetime().isPresent())
                .sorted(Comparator.comparing(WeaverInfluenceRecord::id)).toList();
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        if (!records.isEmpty()) {
            cursor = Math.floorMod(cursor, records.size());
            for (int index = 0; index < Math.min(16, records.size()); index++) {
                final var record = records.get((cursor + index) % records.size());
                chain = chain.thenCompose(ignored -> observe(record));
            }
            cursor += Math.min(16, records.size());
        }
        return chain.whenComplete((ignored, failure) -> inFlight.set(false));
    }
    private CompletionStage<Void> observe(WeaverInfluenceRecord record) {
        if (closed || !journal.ready()) return CompletableFuture.completedFuture(null);
        final CompletionStage<InfluenceObservation> observation;
        try { observation = Objects.requireNonNull(observer.apply(record)); }
        catch (RuntimeException | LinkageError failure) { return CompletableFuture.completedFuture(null); }
        return observation.thenCompose(result -> {
            if (closed || result.status() != InfluenceObservation.Status.ENDED) { result.close(); return CompletableFuture.<Void>completedFuture(null); }
            return journal.endObservedInfluence(record, result.fence().orElseThrow()).handle((ended, failure) -> { result.close(); return (Void) null; });
        }).exceptionally(failure -> null);
    }
    @Override public void close() { closed = true; }
}
