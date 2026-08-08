package hu.taliann.icesmp.classrelic;

/**
 * A világ-egyedi Awakening durable cooldown-szabálya. A cooldown a RELIC-kel utazik
 * (relic-id → awakening-ready-at abszolút időbélyeg, a világ-szintű relic-store-ban),
 * ezért gazdacserénél nem nullázódik és restartot is túlél. A rövid proc/belső
 * cooldownok maradhatnak runtime-állapotok — ez a policy csak a nagy cooldown authority.
 */
public final class AwakeningCooldownPolicy {

    private AwakeningCooldownPolicy() {
    }

    public static boolean ready(final long nowMillis, final long readyAtMillis) {
        return nowMillis >= readyAtMillis;
    }

    public static long nextReadyAt(final long nowMillis, final long cooldownSeconds) {
        if (cooldownSeconds < 0L) {
            throw new IllegalArgumentException("awakening cooldown cannot be negative");
        }
        return Math.addExact(nowMillis, Math.multiplyExact(cooldownSeconds, 1000L));
    }

    public static long remainingMillis(final long nowMillis, final long readyAtMillis) {
        return Math.max(0L, readyAtMillis - nowMillis);
    }
}
