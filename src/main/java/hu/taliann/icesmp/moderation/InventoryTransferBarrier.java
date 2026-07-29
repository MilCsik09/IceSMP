package hu.taliann.icesmp.moderation;

import java.util.concurrent.TimeUnit;

/** Admission/drain barrier for live inventory transfers during reload/disable lifecycle. */
public final class InventoryTransferBarrier {
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
            throw new IllegalStateException("inventory transfer reservation underflow");
        }
        if (inFlight == 0) {
            notifyAll();
        }
    }

    public synchronized void close() {
        accepting = false;
    }

    public boolean awaitDrained(final long timeoutMillis) {
        if (timeoutMillis < 0L) {
            throw new IllegalArgumentException("timeoutMillis must be non-negative");
        }
        final long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        final long started = System.nanoTime();
        synchronized (this) {
            while (inFlight > 0) {
                final long remaining = timeoutNanos - (System.nanoTime() - started);
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
}
