package hu.taliann.icesmp.moderation;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Race-safe publication lease for repeating scheduler handles. Retirement may happen before the
 * scheduler call returns; a handle published afterwards is cancelled instead of becoming stale.
 */
public final class TaskLease<T> {
    private static final Object UNPUBLISHED = new Object();
    private static final Object RETIRED = new Object();

    private final AtomicReference<Object> state = new AtomicReference<>(UNPUBLISHED);
    private final Consumer<T> canceller;

    public TaskLease(final Consumer<T> canceller) {
        this.canceller = Objects.requireNonNull(canceller, "canceller");
    }

    public boolean publish(final T handle) {
        if (handle == null) {
            retire();
            return false;
        }
        if (state.compareAndSet(UNPUBLISHED, handle)) {
            return true;
        }
        canceller.accept(handle);
        return false;
    }

    public boolean retire() {
        final Object previous = state.getAndSet(RETIRED);
        if (previous == RETIRED) {
            return false;
        }
        if (previous != UNPUBLISHED) {
            @SuppressWarnings("unchecked") final T handle = (T) previous;
            canceller.accept(handle);
        }
        return true;
    }

    public boolean isPublished() {
        final Object current = state.get();
        return current != UNPUBLISHED && current != RETIRED;
    }

    public boolean isRetired() {
        return state.get() == RETIRED;
    }
}
