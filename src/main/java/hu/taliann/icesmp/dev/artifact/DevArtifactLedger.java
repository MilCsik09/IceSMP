package hu.taliann.icesmp.dev.artifact;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;

/** The shared manager's storage helper; identities publish only after their serialized write succeeds. */
public final class DevArtifactLedger {
    @FunctionalInterface
    public interface Writer {
        void write(Map<String, DevArtifactState> snapshot) throws Exception;
    }

    private final Executor io;
    private final Writer writer;
    private final Map<String, DevArtifactState> states;
    private final Set<String> pending = new HashSet<>();
    private boolean healthy = true;
    private boolean closed;

    public DevArtifactLedger(final Map<String, DevArtifactState> loaded, final Executor serializedIo,
                              final Writer writer) {
        states = new LinkedHashMap<>(loaded);
        states.forEach((key, value) -> {
            if (!key.matches("[a-z0-9_]{1,64}")) throw new IllegalArgumentException("Invalid artifact id");
            Objects.requireNonNull(value);
        });
        this.io = Objects.requireNonNull(serializedIo);
        this.writer = Objects.requireNonNull(writer);
    }

    public synchronized Map<String, DevArtifactState> snapshot() { return Map.copyOf(states); }
    public synchronized DevArtifactState state(final String id) { return states.get(id); }
    public synchronized boolean healthy() { return healthy; }
    public synchronized boolean pending(final String id) { return pending.contains(id); }

    public synchronized boolean updateVolatile(final String id, final long revision,
                                               final Map<String, Object> behavior) {
        final DevArtifactState before = states.get(id);
        if (!healthy || closed || pending.contains(id) || before == null || before.revision() != revision) return false;
        states.put(id, before.next(before.owner(), before.instanceId(), before.issued(), behavior));
        return true;
    }

    public synchronized CompletionStage<DevArtifactState> commit(final String id, final long expectedRevision,
                                                    final UnaryOperator<DevArtifactState> mutation) {
        final CompletableFuture<DevArtifactState> result = new CompletableFuture<>();
        final DevArtifactState before;
        final DevArtifactState candidate;
        synchronized (this) {
            before = states.get(id);
            if (!healthy || closed || pending.contains(id) || before == null || before.revision() != expectedRevision) {
                return CompletableFuture.failedFuture(new IllegalStateException("Artifact state conflict or unavailable"));
            }
            candidate = Objects.requireNonNull(mutation.apply(before));
            if (candidate.revision() != Math.incrementExact(before.revision())) {
                return CompletableFuture.failedFuture(new IllegalArgumentException("Artifact revision must advance once"));
            }
            pending.add(id);
        }
        try {
            io.execute(() -> {
                try {
                    final Map<String, DevArtifactState> snapshot;
                    synchronized (this) {
                        if (!healthy || states.get(id) != before) throw new IllegalStateException("Artifact state drift");
                        snapshot = new LinkedHashMap<>(states);
                        snapshot.put(id, candidate);
                    }
                    writer.write(Map.copyOf(snapshot));
                    synchronized (this) {
                        states.put(id, candidate);
                        pending.remove(id);
                    }
                    result.complete(candidate);
                } catch (final Throwable failure) {
                    synchronized (this) { healthy = false; pending.remove(id); }
                    result.completeExceptionally(failure);
                    if (failure instanceof Error error) throw error;
                }
            });
        } catch (final RuntimeException rejected) {
            synchronized (this) { healthy = false; pending.remove(id); }
            result.completeExceptionally(rejected);
        }
        return result;
    }

    public synchronized CompletionStage<Void> save(final boolean close) {
        final CompletableFuture<Void> result = new CompletableFuture<>();
        synchronized (this) {
            if (!healthy || closed) return CompletableFuture.failedFuture(new IllegalStateException("Artifact store unavailable"));
            if (close) closed = true;
        }
        try {
            io.execute(() -> {
                try {
                    synchronized (this) {
                        if (!healthy) throw new IllegalStateException("Artifact store failed before save");
                    }
                    writer.write(snapshot());
                    result.complete(null);
                } catch (final Throwable failure) {
                    synchronized (this) { healthy = false; }
                    result.completeExceptionally(failure);
                    if (failure instanceof Error error) throw error;
                }
            });
        } catch (final RuntimeException rejected) {
            synchronized (this) { healthy = false; }
            result.completeExceptionally(rejected);
        }
        return result;
    }
}
