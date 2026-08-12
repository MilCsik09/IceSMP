package hu.taliann.icesmp.spells;

/**
 * Typed result of one cast attempt. Only {@link #SUCCESS} commits gameplay
 * state, cost and cooldown. Preparation and all failed/no-op outcomes remain
 * transaction-neutral.
 */
public enum CastOutcome {
    SUCCESS(true),
    NO_TARGET(false),
    INVALID_TARGET(false),
    NO_EFFECT(false),
    PREPARING(false),
    CANCELLED(false),
    INTERRUPTED(false),
    FAILED(false);

    private final boolean commitsCast;

    CastOutcome(final boolean commitsCast) {
        this.commitsCast = commitsCast;
    }

    public boolean commitsCast() {
        return commitsCast;
    }

    public boolean consumesCost() {
        return commitsCast;
    }

    public boolean startsCooldown() {
        return commitsCast;
    }

    public boolean effectApplied() {
        return commitsCast;
    }
}
