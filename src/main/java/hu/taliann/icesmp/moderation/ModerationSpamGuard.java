package hu.taliann.icesmp.moderation;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Serializes one player's spam decisions across async chat and entity-scheduler message routes.
 *
 * <p>The moderation manager keeps the last timestamp and message in two separate concurrent maps.
 * Individual map operations are thread-safe, but the compound read/decide/write sequence is not.
 * A fixed stripe table provides one shared critical section for equal UUIDs without retaining player
 * identities or globally serializing unrelated players.</p>
 */
public final class ModerationSpamGuard {
    private static final Object[] STRIPES = new Object[64];

    static {
        for (int index = 0; index < STRIPES.length; index++) {
            STRIPES[index] = new Object();
        }
    }

    private ModerationSpamGuard() {
    }

    public static <T> T evaluate(final UUID playerId, final Supplier<T> decision) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(decision, "decision");
        final Object stripe = STRIPES[playerId.hashCode() & (STRIPES.length - 1)];
        synchronized (stripe) {
            return decision.get();
        }
    }
}
