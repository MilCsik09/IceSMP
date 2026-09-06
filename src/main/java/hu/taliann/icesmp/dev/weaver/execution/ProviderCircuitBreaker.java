package hu.taliann.icesmp.dev.weaver.execution;

import hu.taliann.icesmp.dev.weaver.api.WeaverDomainRejection;
import java.util.ArrayDeque;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Three unexpected failures in sixty seconds quarantine a provider until restart. */
public final class ProviderCircuitBreaker {
    private final ArrayDeque<Long> failures = new ArrayDeque<>(3);
    private final LongSupplier clock;
    private boolean quarantined;
    public ProviderCircuitBreaker(final LongSupplier monotonicMillis) { clock = java.util.Objects.requireNonNull(monotonicMillis); }
    public synchronized boolean quarantined() { return quarantined; }
    public synchronized int recentFailures() { expire(clock.getAsLong()); return failures.size(); }
    private void expire(final long now) { while (!failures.isEmpty() && now - failures.getFirst() >= 60_000) failures.removeFirst(); }
    private synchronized void failed() {
        if (quarantined) return;
        final long now = clock.getAsLong(); expire(now); failures.addLast(now);
        if (failures.size() >= 3) quarantined = true;
    }
    public <T> T call(final Supplier<T> action, final boolean recovery) {
        synchronized (this) { if (quarantined && !recovery) throw new WeaverDomainRejection("PROVIDER_QUARANTINED"); }
        try { return action.get(); }
        catch (final WeaverDomainRejection domain) { throw domain; }
        catch (final RuntimeException | LinkageError failure) {
            failed();
            throw new WeaverDomainRejection("PROVIDER_ERROR");
        }
    }
}
