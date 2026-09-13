package hu.taliann.icesmp.dev.weaver.execution;

import hu.taliann.icesmp.dev.weaver.api.WeaverDomainRejection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/** A timed-out queued task cannot start later; started timeouts are explicitly ambiguous. */
public final class OwnerTaskAdmission<T> {
    private enum State { WAITING, STARTED, FINISHED }
    private State state = State.WAITING;
    private final CompletableFuture<T> result = new CompletableFuture<>();
    public CompletionStage<T> result() { return result.minimalCompletionStage(); }
    public void run(final Supplier<CompletionStage<T>> task) {
        synchronized (this) { if (state != State.WAITING) return; state = State.STARTED; }
        try {
            java.util.Objects.requireNonNull(task.get()).whenComplete((value, failure) -> finish(value, failure));
        } catch (final RuntimeException | LinkageError failure) { finish(null, failure); }
    }
    private void finish(final T value, final Throwable failure) {
        synchronized (this) { if (state == State.FINISHED) return; state = State.FINISHED; }
        if (failure == null) result.complete(value); else result.completeExceptionally(failure);
    }
    public void unavailable() {
        synchronized (this) { if (state != State.WAITING) return; state = State.FINISHED; }
        result.completeExceptionally(new WeaverDomainRejection("OWNER_UNAVAILABLE"));
    }
    public void timeout() {
        final boolean started;
        synchronized (this) { if (state == State.FINISHED) return; started = state == State.STARTED; state = State.FINISHED; }
        result.completeExceptionally(new WeaverDomainRejection(started ? "OWNER_TIMEOUT_STARTED" : "OWNER_TIMEOUT_UNSTARTED"));
    }
    public void shutdown() {
        final boolean started;
        synchronized (this) { if (state == State.FINISHED) return; started = state == State.STARTED; state = State.FINISHED; }
        result.completeExceptionally(new WeaverDomainRejection(started ? "OWNER_SHUTDOWN_STARTED" : "OWNER_SHUTDOWN_UNSTARTED"));
    }
}
