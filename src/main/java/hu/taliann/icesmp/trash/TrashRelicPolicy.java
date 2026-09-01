package hu.taliann.icesmp.trash;

/** Pure transaction and bounded-work decisions shared by the Phase E runtime/tests. */
final class TrashRelicPolicy {

    private TrashRelicPolicy() { }

    static boolean consumptionCommitted(final boolean sameStackStillInHand,
                                        final int amountBefore, final int amountAfter) {
        if (amountBefore < 1 || amountAfter < 0) return false;
        return !sameStackStillInHand || amountAfter < amountBefore;
    }

    static boolean mayTrackProjectile(final boolean projectileWallActive,
                                      final int trackedProjectiles, final int maximum) {
        return projectileWallActive && maximum > 0
                && trackedProjectiles >= 0 && trackedProjectiles < maximum;
    }
}
