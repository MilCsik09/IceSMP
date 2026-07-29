package hu.taliann.icesmp.moderation;

import java.util.Objects;

/** Dependency-free single-winner adapter for nullable scheduler submissions. */
public final class EntityTaskSubmission {

    @FunctionalInterface
    public interface Submission {
        Object submit(Runnable task, Runnable retired);
    }

    private EntityTaskSubmission() {
    }

    /**
     * Submits one task and makes task/fallback mutually exclusive. A thrown submit, a null handle,
     * or the retirement callback all route to the same exactly-once fallback.
     */
    public static boolean submit(final Submission submission, final Runnable task,
                                 final Runnable rejected) {
        Objects.requireNonNull(submission, "submission");
        Objects.requireNonNull(task, "task");
        final SchedulerCallbackGate gate = new SchedulerCallbackGate();
        final Runnable guardedTask = () -> gate.runTask(task);
        final Runnable guardedRejected = () -> gate.runRejected(rejected);
        final Object handle;
        try {
            handle = submission.submit(guardedTask, guardedRejected);
        } catch (final RuntimeException failure) {
            guardedRejected.run();
            return false;
        }
        if (handle == null) {
            guardedRejected.run();
        }
        return gate.winner() == SchedulerCallbackGate.Winner.TASK
                || (handle != null && gate.winner() == SchedulerCallbackGate.Winner.NONE);
    }
}
