package hu.taliann.icesmp.crates;

import java.util.concurrent.atomic.AtomicReference;

/** Atomic lifecycle for one crate opening. Finalize and rollback can never both win. */
public final class CrateOpeningLifecycle {

    public enum State {
        RESERVED,
        PERSISTED,
        GRANTING,
        COMPLETED,
        ROLLED_BACK,
        FAILED_PARTIAL
    }

    private final AtomicReference<State> state = new AtomicReference<>(State.RESERVED);

    public State state() {
        return state.get();
    }

    public boolean markPersisted() {
        return state.compareAndSet(State.RESERVED, State.PERSISTED);
    }

    public boolean claimGrant() {
        return state.compareAndSet(State.PERSISTED, State.GRANTING);
    }

    public boolean complete() {
        return state.compareAndSet(State.GRANTING, State.COMPLETED);
    }

    public boolean failPartial() {
        return state.compareAndSet(State.GRANTING, State.FAILED_PARTIAL);
    }

    /** Only work that has not started granting may be rolled back automatically. */
    public boolean rollbackBeforeGrant() {
        while (true) {
            final State current = state.get();
            if (current != State.RESERVED && current != State.PERSISTED) {
                return false;
            }
            if (state.compareAndSet(current, State.ROLLED_BACK)) {
                return true;
            }
        }
    }

    /** Used after a durable key refund has been committed. */
    public boolean finishCompensatedRollback() {
        return state.compareAndSet(State.GRANTING, State.ROLLED_BACK);
    }
}
