package hu.taliann.icesmp.moderation;

import java.util.concurrent.TimeUnit;

/**
 * Admission and shutdown barrier for durable moderation mutations.
 * Closing is immediate and irreversible; already admitted work may drain before the final save.
 */
public final class ModerationMutationGate {
    private int inFlight;
    private boolean accepting = true;

    public synchronized boolean reserve() {
        if (!accepting) {
            return false;
        }
        inFlight++;
        return true;
    }

    public synchronized void release() {
        inFlight--;
        if (inFlight < 0) {
            throw new IllegalStateException("moderation mutation reservation underflow");
        }
        if (inFlight == 0) {
            notifyAll();
        }
    }

    /** Prevents every later reservation without waiting for admitted work. Idempotent. */
    public synchronized void close() {
        accepting = false;
    }

    /** Closes admission and waits for already admitted work to drain. */
    public boolean closeAndAwait(final long timeoutMillis) {
        if (timeoutMillis < 0L) {
            throw new IllegalArgumentException("timeoutMillis must be non-negative");
        }
        final long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        final long started = System.nanoTime();
        synchronized (this) {
            accepting = false;
            while (inFlight > 0) {
                final long elapsed = System.nanoTime() - started;
                final long remaining = timeoutNanos - elapsed;
                if (remaining <= 0L) {
                    return false;
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(this, remaining);
                } catch (final InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return true;
        }
    }

    synchronized int inFlightForTest() {
        return inFlight;
    }
}
