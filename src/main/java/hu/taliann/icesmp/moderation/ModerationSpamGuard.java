package hu.taliann.icesmp.moderation;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Serializes spam decisions that share one {@code ModerationManager} instance.
 *
 * <p>The manager intentionally keeps the last timestamp and message in two separate concurrent
 * maps. Individual map operations are thread-safe, but the compound read/decide/write sequence is
 * not. Public chat runs on Paper's async chat executor while private messages run on player entity
 * schedulers, so both routes must enter the same critical section before consulting that state.</p>
 */
public final class ModerationSpamGuard {
    private ModerationSpamGuard() {
    }

    public static <T> T evaluate(final Object owner, final Supplier<T> decision) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(decision, "decision");
        synchronized (owner) {
            return decision.get();
        }
    }
}
