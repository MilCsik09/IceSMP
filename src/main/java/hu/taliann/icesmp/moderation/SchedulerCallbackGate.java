package hu.taliann.icesmp.moderation;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Single-winner gate for a scheduler task and its retirement/rejection fallback. Paper may invoke
 * a retirement callback while submission is still returning; the fallback must never run twice or
 * after the task body has already started.
 */
public final class SchedulerCallbackGate {
    public enum Winner { NONE, TASK, REJECTED }

    private static final int NONE = 0;
    private static final int TASK = 1;
    private static final int REJECTED = 2;

    private final AtomicInteger winner = new AtomicInteger(NONE);

    public boolean runTask(final Runnable action) {
        return runOnce(TASK, action);
    }

    public boolean runRejected(final Runnable action) {
        return runOnce(REJECTED, action);
    }

    public Winner winner() {
        return switch (winner.get()) {
            case NONE -> Winner.NONE;
            case TASK -> Winner.TASK;
            case REJECTED -> Winner.REJECTED;
            default -> throw new IllegalStateException("unknown scheduler callback winner");
        };
    }

    private boolean runOnce(final int state, final Runnable action) {
        if (!winner.compareAndSet(NONE, state)) {
            return false;
        }
        if (action != null) {
            action.run();
        }
        return true;
    }
}
