package hu.taliann.icesmp.prologue;

/** Monotonic encounter timeout whose budget does not advance while paused. */
final class ProloguePauseClock {
    private long remainingMillis;
    private long lastRunningAtMillis;
    private boolean paused;

    ProloguePauseClock(final long durationMillis, final long nowMillis) {
        if (durationMillis <= 0L) throw new IllegalArgumentException("durationMillis must be positive");
        remainingMillis = durationMillis;
        lastRunningAtMillis = nowMillis;
    }

    synchronized void pause(final long nowMillis) {
        if (paused) return;
        consume(nowMillis);
        paused = true;
    }

    synchronized void resume(final long nowMillis) {
        if (!paused) return;
        paused = false;
        lastRunningAtMillis = nowMillis;
    }

    synchronized long remainingMillis(final long nowMillis) {
        if (!paused) consume(nowMillis);
        return remainingMillis;
    }

    synchronized boolean expired(final long nowMillis) {
        return remainingMillis(nowMillis) <= 0L;
    }

    synchronized boolean paused() {
        return paused;
    }

    private void consume(final long nowMillis) {
        final long elapsed = Math.max(0L, nowMillis - lastRunningAtMillis);
        remainingMillis = Math.max(0L, remainingMillis - elapsed);
        lastRunningAtMillis = nowMillis;
    }
}
