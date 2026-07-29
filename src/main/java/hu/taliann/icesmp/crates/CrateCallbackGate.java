package hu.taliann.icesmp.crates;

import java.util.concurrent.atomic.AtomicReference;

/** Single-winner gate for scheduler task and rejection/retirement callbacks. */
public final class CrateCallbackGate {

    public enum Winner {
        NONE,
        TASK,
        REJECTED
    }

    private final AtomicReference<Winner> winner = new AtomicReference<>(Winner.NONE);

    public boolean runTask(final Runnable action) {
        return run(Winner.TASK, action);
    }

    public boolean runRejected(final Runnable action) {
        return run(Winner.REJECTED, action);
    }

    public Winner winner() {
        return winner.get();
    }

    private boolean run(final Winner candidate, final Runnable action) {
        if (!winner.compareAndSet(Winner.NONE, candidate)) {
            return false;
        }
        action.run();
        return true;
    }
}
