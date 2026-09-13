package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.security.HiddenDevAuthority;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Minted by the kernel after owner-thread artifact/session checks; providers cannot construct tokens. */
public final class WeaverAuthorityToken {
    private final UUID actor;
    private final UUID session;
    private final long expiresAtNanos;
    private final BooleanSupplier sessionValid;
    private final LongSupplier monotonicClock;
    private volatile boolean revoked;
    WeaverAuthorityToken(final UUID actor, final UUID session, final long expiresAtNanos,
                         final BooleanSupplier sessionValid, final LongSupplier monotonicClock) {
        this.actor = java.util.Objects.requireNonNull(actor); this.session = java.util.Objects.requireNonNull(session);
        this.expiresAtNanos = expiresAtNanos; this.sessionValid = java.util.Objects.requireNonNull(sessionValid);
        this.monotonicClock = java.util.Objects.requireNonNull(monotonicClock);
        if (!HiddenDevAuthority.isDeveloper(actor)) throw new SecurityException("Primary developer required");
    }
    public UUID actor() { return actor; }
    public UUID session() { return session; }
    public void requireValid() {
        if (revoked || !HiddenDevAuthority.isDeveloper(actor) || monotonicClock.getAsLong() - expiresAtNanos >= 0 || !sessionValid.getAsBoolean()) {
            throw new SecurityException("WorldWeaver authority expired or revoked");
        }
    }
    void revoke() { revoked = true; }
}
