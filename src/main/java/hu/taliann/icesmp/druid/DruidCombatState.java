package hu.taliann.icesmp.druid;

import java.util.UUID;

/**
 * Per-player transient Druida combat state.
 *
 * <p>Természeti Erő is the class layer: nature casts build harmony that a shapeshift
 * (the existing form system) releases as a season blessing. Vadőr tracks combo points
 * and the Szagnyom prey mark; Holdjós tracks the Nap↔Hold balance and the Eclipse
 * window; Védelmező tracks bark layers and the Gyökérháló window; Helyreállító tracks
 * planted seeds that only count once ripened. Durable state remains in PlayerProfile.</p>
 */
public final class DruidCombatState {

    private int harmony;
    private long harmonyLastGainAt;
    private long harmonyLastDecayAt;
    private long autumnWindowUntil;

    private int combo;
    private UUID scentTarget;
    private long scentUntil;

    private int balance;
    private long eclipseUntil;

    private int barkLayers;
    private long rootsUntil;

    private final long[] seedPlantedAt = new long[5];

    // ===== Természeti Erő + Évszak (class core) =====

    public synchronized int addHarmony(final int amount, final long now,
                                       final long decayDelayMillis,
                                       final double decayPerSecond) {
        decayHarmony(now, decayDelayMillis, decayPerSecond);
        harmony = clampPercent(harmony + Math.max(0, amount));
        harmonyLastGainAt = now;
        harmonyLastDecayAt = now;
        return harmony;
    }

    public synchronized int harmony(final long now, final long decayDelayMillis,
                                    final double decayPerSecond) {
        decayHarmony(now, decayDelayMillis, decayPerSecond);
        return harmony;
    }

    /** A shapeshift releases everything at once, but only above the threshold. */
    public synchronized int releaseHarmony(final int threshold) {
        if (harmony < Math.max(1, threshold)) return 0;
        final int released = harmony;
        harmony = 0;
        return released;
    }

    public synchronized void armAutumnWindow(final long now, final long windowMillis) {
        autumnWindowUntil = now + Math.max(1L, windowMillis);
    }

    public synchronized boolean isAutumnWindowArmed(final long now) {
        return autumnWindowUntil > now;
    }

    // ===== Vadőr: kombópont + Szagnyom =====

    public synchronized int addCombo(final int amount, final int maximum) {
        combo = Math.max(0, Math.min(Math.max(1, maximum), combo + Math.max(0, amount)));
        return combo;
    }

    public synchronized int combo() {
        return combo;
    }

    /** A finisher spends every point at once. Returns the spent count. */
    public synchronized int spendAllCombo() {
        final int spent = combo;
        combo = 0;
        return spent;
    }

    public synchronized void markScent(final UUID targetId, final long now,
                                       final long windowMillis) {
        if (targetId == null) return;
        scentTarget = targetId;
        scentUntil = now + Math.max(1L, windowMillis);
    }

    public synchronized boolean isScentLive(final long now) {
        return scentTarget != null && scentUntil > now;
    }

    public synchronized UUID scentTarget(final long now) {
        return isScentLive(now) ? scentTarget : null;
    }

    // ===== Holdjós: Nap↔Hold mérleg + Eclipse =====

    /** Positive delta leans toward Nap, negative toward Hold; bounded at ±100. */
    public synchronized int shiftBalance(final int delta) {
        balance = Math.max(-100, Math.min(100, balance + delta));
        return balance;
    }

    public synchronized int balance() {
        return balance;
    }

    public synchronized void resetBalance(final int startValue) {
        balance = Math.max(-100, Math.min(100, startValue));
    }

    public synchronized void armEclipse(final long now, final long windowMillis) {
        eclipseUntil = now + Math.max(1L, windowMillis);
    }

    public synchronized boolean isEclipseArmed(final long now) {
        return eclipseUntil > now;
    }

    // ===== Védelmező: Kéregrétegek + Gyökérháló =====

    public synchronized int addBarkLayer(final int maximum) {
        barkLayers = Math.max(0, Math.min(Math.max(1, maximum), barkLayers + 1));
        return barkLayers;
    }

    public synchronized int barkLayers() {
        return barkLayers;
    }

    /** A heavy hit cracks one layer. Returns whether a layer was consumed. */
    public synchronized boolean crackBarkLayer() {
        if (barkLayers <= 0) return false;
        barkLayers--;
        return true;
    }

    public synchronized void armRoots(final long now, final long windowMillis) {
        rootsUntil = now + Math.max(1L, windowMillis);
    }

    public synchronized boolean isRootsArmed(final long now) {
        return rootsUntil > now;
    }

    // ===== Helyreállító: Mag → érés → Virágzás =====

    /** Plants one seed if a slot is free within the maximum. Expired seeds free their slot. */
    public synchronized boolean plantSeed(final long now, final int maximum,
                                          final long expiryMillis) {
        final int cap = Math.max(1, Math.min(seedPlantedAt.length, maximum));
        int occupied = 0;
        int freeSlot = -1;
        for (int i = 0; i < seedPlantedAt.length; i++) {
            if (seedPlantedAt[i] > 0L && now < seedPlantedAt[i] + Math.max(1L, expiryMillis)) {
                occupied++;
            } else if (freeSlot < 0) {
                freeSlot = i;
                seedPlantedAt[i] = 0L;
            } else {
                seedPlantedAt[i] = 0L;
            }
        }
        if (occupied >= cap || freeSlot < 0) return false;
        seedPlantedAt[freeSlot] = now;
        return true;
    }

    public synchronized int seedCount(final long now, final long expiryMillis) {
        int count = 0;
        for (final long plantedAt : seedPlantedAt) {
            if (plantedAt > 0L && now < plantedAt + Math.max(1L, expiryMillis)) count++;
        }
        return count;
    }

    public synchronized int ripeSeedCount(final long now, final long ripenMillis,
                                          final long expiryMillis) {
        int count = 0;
        for (final long plantedAt : seedPlantedAt) {
            if (isRipe(plantedAt, now, ripenMillis, expiryMillis)) count++;
        }
        return count;
    }

    /** A bloom harvests only the ripe seeds; unripe ones keep maturing. */
    public synchronized int collectRipeSeeds(final long now, final long ripenMillis,
                                             final long expiryMillis) {
        int collected = 0;
        for (int i = 0; i < seedPlantedAt.length; i++) {
            if (isRipe(seedPlantedAt[i], now, ripenMillis, expiryMillis)) {
                seedPlantedAt[i] = 0L;
                collected++;
            }
        }
        return collected;
    }

    /** Spec switch cleanup: no druid state survives it, including the class harmony. */
    public synchronized void clearSpecializationState() {
        harmony = 0;
        harmonyLastGainAt = 0L;
        harmonyLastDecayAt = 0L;
        autumnWindowUntil = 0L;
        combo = 0;
        scentTarget = null;
        scentUntil = 0L;
        balance = 0;
        eclipseUntil = 0L;
        barkLayers = 0;
        rootsUntil = 0L;
        java.util.Arrays.fill(seedPlantedAt, 0L);
    }

    /** Death/logout/admin reset cleanup. */
    public synchronized void clearAll() {
        clearSpecializationState();
    }

    private static boolean isRipe(final long plantedAt, final long now,
                                  final long ripenMillis, final long expiryMillis) {
        return plantedAt > 0L
                && now >= plantedAt + Math.max(1L, ripenMillis)
                && now < plantedAt + Math.max(1L, expiryMillis);
    }

    private void decayHarmony(final long now, final long decayDelayMillis,
                              final double decayPerSecond) {
        if (harmony <= 0 || harmonyLastGainAt <= 0L || decayPerSecond <= 0.0D) return;
        final long decayStartsAt = harmonyLastGainAt + Math.max(0L, decayDelayMillis);
        if (now <= decayStartsAt) return;
        final long from = Math.max(decayStartsAt, harmonyLastDecayAt);
        if (now <= from) return;
        final int decay = (int) Math.floor((now - from) / 1000.0D * decayPerSecond);
        if (decay > 0) {
            harmony = clampPercent(harmony - decay);
            harmonyLastDecayAt = now;
        }
    }

    private static int clampPercent(final int value) {
        return Math.max(0, Math.min(100, value));
    }
}
