package hu.taliann.icesmp.dev.weaver.execution;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/** Continuations drain iteratively even when every task completes synchronously; failure drains already admitted work. */
public final class WeaverBoundedTasks {
    private WeaverBoundedTasks() { }
    public static <I, O> CompletionStage<List<O>> map(final List<I> inputs, final int concurrency, final Function<I, CompletionStage<O>> task) {
        if (inputs.size() > 4096 || concurrency < 1 || concurrency > 16) throw new IllegalArgumentException("Bounded task limits");
        final Run<I, O> run = new Run<>(List.copyOf(inputs), concurrency, Objects.requireNonNull(task)); run.drain(); return run.result.minimalCompletionStage();
    }
    private static final class Run<I, O> {
        final List<I> inputs; final int concurrency; final Function<I, CompletionStage<O>> task;
        final List<O> values; final CompletableFuture<List<O>> result = new CompletableFuture<>(); final AtomicInteger work = new AtomicInteger();
        int cursor; int active; Throwable failure;
        Run(final List<I> inputs, final int concurrency, final Function<I, CompletionStage<O>> task) {
            this.inputs = inputs; this.concurrency = concurrency; this.task = task; values = new ArrayList<>(Collections.nCopies(inputs.size(), null));
        }
        void drain() {
            if (work.getAndIncrement() != 0) return;
            do {
                while (true) {
                    final int index;
                    synchronized (this) {
                        if (failure != null || active >= concurrency || cursor == inputs.size()) break;
                        index = cursor++; active++;
                    }
                    try { Objects.requireNonNull(task.apply(inputs.get(index))).whenComplete((value, error) -> done(index, value, error)); }
                    catch (final RuntimeException | LinkageError error) { done(index, null, error); }
                }
                final Throwable error; final List<O> completed;
                synchronized (this) {
                    error = active == 0 ? failure : null;
                    completed = active == 0 && cursor == inputs.size() && failure == null ? List.copyOf(values) : null;
                }
                if (error != null) result.completeExceptionally(error); else if (completed != null) result.complete(completed);
            } while (work.decrementAndGet() != 0);
        }
        void done(final int index, final O value, final Throwable error) {
            synchronized (this) {
                active--;
                if (error != null && failure == null) failure = error;
                else if (error == null && value == null && failure == null) failure = new NullPointerException("Bounded task result");
                if (error == null) values.set(index, value);
            }
            drain();
        }
    }
}
