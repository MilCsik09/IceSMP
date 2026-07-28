package hu.taliann.icesmp.managers;

/** Pure reward-interval gate used by the Folia-owned AFK player tick. */
public final class AfkRewardClock {

    public record Advance(long remainderMillis, boolean rewardDue) { }

    private AfkRewardClock() {
    }

    /**
     * Advances one player once. Even after a very late tick only one reward cycle becomes due;
     * the exact interval remainder is retained so reloads and zone ticks cannot duplicate payout.
     */
    public static Advance advance(final long previousMillis, final long deltaMillis,
                                  final long intervalMillis) {
        if (previousMillis < 0L || deltaMillis < 0L || intervalMillis <= 0L) {
            throw new IllegalArgumentException("Az AFK reward clock értékei nem lehetnek negatívak.");
        }
        final long normalizedPrevious = previousMillis % intervalMillis;
        final long total = deltaMillis > Long.MAX_VALUE - normalizedPrevious
                ? Long.MAX_VALUE : normalizedPrevious + deltaMillis;
        return total < intervalMillis
                ? new Advance(total, false)
                : new Advance(total % intervalMillis, true);
    }
}
