package hu.taliann.icesmp.dev.weaver.execution;

import java.util.function.LongSupplier;

public final class WeaverRateLimiter {
    private final LongSupplier clock;
    private double tokens = 20;
    private long last;
    public WeaverRateLimiter(final LongSupplier monotonicMillis) { clock = java.util.Objects.requireNonNull(monotonicMillis); last = clock.getAsLong(); }
    public synchronized boolean tryAcquire(final int cost) {
        if (cost < 1 || cost > 10) throw new IllegalArgumentException("Action rate cost outside 1..10");
        return acquire(cost);
    }
    public synchronized boolean tryAcquireArea(final int targets, final int descriptorCost) {
        return acquire(areaCost(targets, descriptorCost));
    }
    private boolean acquire(final int cost) {
        final long now = clock.getAsLong();
        if (now > last) { tokens = Math.min(20, tokens + (now - last) / 500.0); last = now; }
        if (tokens < cost) return false;
        tokens -= cost; return true;
    }
    public static int areaCost(final int targets, final int descriptorCost) {
        if (targets < 0 || targets > 4096 || descriptorCost < 1 || descriptorCost > 10) throw new IllegalArgumentException("Invalid AREA rate request");
        return Math.max(descriptorCost, 1 + (targets + 15) / 16);
    }
}
