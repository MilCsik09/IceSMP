package hu.taliann.icesmp.demonhunter;

/**
 * Per-player transient Démonvadász combat state.
 *
 * <p>Kárhozat-terhelés is the class layer: demonic casts build load whose high band trades
 * power for readable fragility. Tombolás tracks a lightweight Lélektöredék counter (never
 * item entities) with a single Momentum window; Bosszú tracks the Fájdalom pool and at most
 * two concurrently armed Sigils. Durable state remains in PlayerProfile.</p>
 */
public final class DemonHunterCombatState {

    public enum LoadBand {
        STABIL,
        ATHEVULT,
        TULTERHELT
    }

    private int load;
    private long loadLastGainAt;
    private long loadLastDecayAt;

    private int fragments;
    private int momentumCharges;
    private long momentumExpiresAt;

    private int pain;
    private final long[] sigilArmedUntil = new long[2];

    // ===== Kárhozat-terhelés (class core) =====

    public synchronized int addLoad(final int amount, final long now,
                                    final long decayDelayMillis,
                                    final double decayPerSecond) {
        decayLoad(now, decayDelayMillis, decayPerSecond);
        load = clampPercent(load + Math.max(0, amount));
        loadLastGainAt = now;
        loadLastDecayAt = now;
        return load;
    }

    public synchronized int load(final long now, final long decayDelayMillis,
                                 final double decayPerSecond) {
        decayLoad(now, decayDelayMillis, decayPerSecond);
        return load;
    }

    public synchronized LoadBand loadBand(final long now, final int heatedThreshold,
                                          final int overloadThreshold,
                                          final long decayDelayMillis,
                                          final double decayPerSecond) {
        final int value = load(now, decayDelayMillis, decayPerSecond);
        if (value >= Math.max(heatedThreshold, overloadThreshold)) return LoadBand.TULTERHELT;
        if (value >= Math.min(heatedThreshold, overloadThreshold)) return LoadBand.ATHEVULT;
        return LoadBand.STABIL;
    }

    /** Controlled vent (consume_magic or a doctrine effect). Returns the vented amount. */
    public synchronized int ventLoad(final int amount) {
        final int vented = Math.max(0, Math.min(amount, load));
        load -= vented;
        return vented;
    }

    // ===== Tombolás: Lélektöredék + Momentum =====

    public synchronized int addFragments(final int amount, final int maximum) {
        fragments = Math.max(0, Math.min(Math.max(1, maximum), fragments + Math.max(0, amount)));
        return fragments;
    }

    public synchronized int fragments() {
        return fragments;
    }

    /** A mobility cast collects every fragment at once. Returns the collected count. */
    public synchronized int collectFragments() {
        final int collected = fragments;
        fragments = 0;
        return collected;
    }

    public synchronized void armMomentum(final int charges, final long now,
                                         final long windowMillis) {
        momentumCharges = Math.max(1, charges);
        momentumExpiresAt = now + Math.max(1L, windowMillis);
    }

    public synchronized boolean isMomentumArmed(final long now) {
        return momentumCharges > 0 && momentumExpiresAt > now;
    }

    /** Each empowered cast consumes one Momentum charge. */
    public synchronized boolean consumeMomentum(final long now) {
        if (!isMomentumArmed(now)) {
            momentumCharges = 0;
            return false;
        }
        momentumCharges--;
        return true;
    }

    // ===== Bosszú: Fájdalom + Sigilek =====

    public synchronized int addPain(final int amount) {
        pain = clampPercent(pain + Math.max(0, amount));
        return pain;
    }

    public synchronized boolean spendPain(final int amount) {
        final int cost = Math.max(0, amount);
        if (pain < cost) return false;
        pain -= cost;
        return true;
    }

    public synchronized int pain() {
        return pain;
    }

    /** At most two Sigils may be armed concurrently; an expired slot is reused. */
    public synchronized boolean armSigil(final long now, final long durationMillis) {
        for (int i = 0; i < sigilArmedUntil.length; i++) {
            if (sigilArmedUntil[i] <= now) {
                sigilArmedUntil[i] = now + Math.max(1L, durationMillis);
                return true;
            }
        }
        return false;
    }

    public synchronized int armedSigils(final long now) {
        int count = 0;
        for (final long until : sigilArmedUntil) {
            if (until > now) count++;
        }
        return count;
    }

    /** Spec switch cleanup: no demon hunter state survives it, including the class load. */
    public synchronized void clearSpecializationState() {
        load = 0;
        loadLastGainAt = 0L;
        loadLastDecayAt = 0L;
        fragments = 0;
        momentumCharges = 0;
        momentumExpiresAt = 0L;
        pain = 0;
        sigilArmedUntil[0] = 0L;
        sigilArmedUntil[1] = 0L;
    }

    /** Death/logout/admin reset cleanup. */
    public synchronized void clearAll() {
        clearSpecializationState();
    }

    private void decayLoad(final long now, final long decayDelayMillis,
                           final double decayPerSecond) {
        if (load <= 0 || loadLastGainAt <= 0L || decayPerSecond <= 0.0D) return;
        final long decayStartsAt = loadLastGainAt + Math.max(0L, decayDelayMillis);
        if (now <= decayStartsAt) return;
        final long from = Math.max(decayStartsAt, loadLastDecayAt);
        if (now <= from) return;
        final int decay = (int) Math.floor((now - from) / 1000.0D * decayPerSecond);
        if (decay > 0) {
            load = clampPercent(load - decay);
            loadLastDecayAt = now;
        }
    }

    private static int clampPercent(final int value) {
        return Math.max(0, Math.min(100, value));
    }
}
