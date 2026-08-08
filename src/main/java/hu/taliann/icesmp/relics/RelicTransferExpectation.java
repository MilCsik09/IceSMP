package hu.taliann.icesmp.relics;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Scoped call-site proof for a PvP relic transfer.
 *
 * <p>The death listener owns the authoritative victim identity. RelicManager historically
 * re-read the mutable central owner immediately before calling the store; that turns a stale
 * physical copy into an ownership overwrite if the central owner already changed. This guard
 * carries the caller-proven victim UUID through that legacy boundary so the store can compare it
 * under its own write lock. The value is strictly thread-local and always cleared in finally.</p>
 */
public final class RelicTransferExpectation {

    private static final ThreadLocal<UUID> EXPECTED_OWNER = new ThreadLocal<>();

    private RelicTransferExpectation() { }

    public static <T> T withExpectedOwner(final UUID expectedOwner, final Supplier<T> action) {
        Objects.requireNonNull(expectedOwner, "expectedOwner");
        Objects.requireNonNull(action, "action");
        if (EXPECTED_OWNER.get() != null) {
            throw new IllegalStateException("nested relic transfer expectation");
        }
        EXPECTED_OWNER.set(expectedOwner);
        try {
            return action.get();
        } finally {
            EXPECTED_OWNER.remove();
        }
    }

    public static void withExpectedOwner(final UUID expectedOwner, final Runnable action) {
        withExpectedOwner(expectedOwner, () -> {
            action.run();
            return null;
        });
    }

    static UUID currentExpectedOwner() {
        return EXPECTED_OWNER.get();
    }
}
