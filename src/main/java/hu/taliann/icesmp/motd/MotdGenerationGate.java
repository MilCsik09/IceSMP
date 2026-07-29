package hu.taliann.icesmp.motd;

import hu.taliann.icesmp.moderation.SchedulerCallbackGate;

import java.util.concurrent.atomic.AtomicLong;

/** Generation fence for asynchronous MOTD reload work and its scheduler rejection path. */
public final class MotdGenerationGate {
    private final AtomicLong generation = new AtomicLong();

    public synchronized long nextGeneration() {
        return generation.incrementAndGet();
    }

    public synchronized long invalidate() {
        return generation.incrementAndGet();
    }

    public boolean isCurrent(final long expected) {
        return generation.get() == expected;
    }

    public boolean publishIfCurrent(final long expected, final Runnable publisher) {
        if (!isCurrent(expected)) {
            return false;
        }
        synchronized (this) {
            if (!isCurrent(expected)) {
                return false;
            }
            publisher.run();
            return true;
        }
    }

    public Attempt newAttempt(final long expected) {
        return new Attempt(this, expected);
    }

    public static final class Attempt {
        private final MotdGenerationGate generations;
        private final long expected;
        private final SchedulerCallbackGate callbacks = new SchedulerCallbackGate();

        private Attempt(final MotdGenerationGate generations, final long expected) {
            this.generations = generations;
            this.expected = expected;
        }

        public boolean runCurrent(final Runnable action) {
            return callbacks.runTask(() -> {
                if (generations.isCurrent(expected)) {
                    action.run();
                }
            });
        }

        public boolean rejectCurrent(final Runnable action) {
            return callbacks.runRejected(() -> {
                if (generations.isCurrent(expected)) {
                    action.run();
                }
            });
        }

        public SchedulerCallbackGate.Winner winner() {
            return callbacks.winner();
        }
    }
}
