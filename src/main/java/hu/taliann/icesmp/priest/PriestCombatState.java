package hu.taliann.icesmp.priest;

/**
 * Per-player transient Pap combat state.
 *
 * <p>The Litánia is the class layer: a chosen prayer whose matching deeds count verses, and at
 * the required verse count the prayer is recited once — a discrete, repeatable payoff, not a
 * decaying meter. Fegyelem carries the Engesztelés window, its shield web and an explicit
 * non-reentrant conversion guard so a heal can never feed itself. Csontpap carries the Velő pool
 * that condenses into Osszárium charges through controlled sacrifice. Árnyék carries the Őrület
 * meter around its Küszöb. Durable state remains in PlayerProfile.</p>
 */
public final class PriestCombatState {

    /** The three prayers a priest may take up. */
    public enum Litany {
        VIGASZ,
        OSTOR,
        CSEND
    }

    private Litany litany;
    private int verses;
    private long recitedUntil;

    private long atonementUntil;
    private int shield;
    private boolean converting;

    private int marrow;
    private int ossuary;

    private int madness;
    private long madnessLastGainAt;
    private long madnessLastDecayAt;

    // ===== Litánia (class core) =====

    public synchronized void chooseLitany(final Litany chosen) {
        if (chosen == null) return;
        litany = chosen;
        verses = 0;
    }

    public synchronized Litany litanyOrDefault(final Litany fallback) {
        return litany == null ? fallback : litany;
    }

    public synchronized int addVerse(final int required) {
        verses = Math.max(0, Math.min(Math.max(1, required), verses + 1));
        return verses;
    }

    public synchronized int verses() {
        return verses;
    }

    /**
     * Reciting consumes the whole prayer: only a full verse count pays, and the counter starts
     * over afterwards, so the payoff is re-earned rather than held.
     */
    public synchronized boolean recite(final int required, final long now,
                                       final long windowMillis) {
        if (verses < Math.max(1, required)) return false;
        verses = 0;
        recitedUntil = now + Math.max(1L, windowMillis);
        return true;
    }

    public synchronized boolean isRecited(final long now) {
        return recitedUntil > now;
    }

    // ===== Fegyelem: Engesztelés + pajzsháló =====

    public synchronized void armAtonement(final long now, final long windowMillis) {
        atonementUntil = now + Math.max(1L, windowMillis);
    }

    public synchronized boolean isAtonementActive(final long now) {
        return atonementUntil > now;
    }

    /**
     * Explicit non-reentrant guard: a conversion may never start inside another one, so a heal
     * produced by Engesztelés can never be converted again. Always paired with
     * {@link #endConversion()} in a finally block.
     */
    public synchronized boolean beginConversion() {
        if (converting) return false;
        converting = true;
        return true;
    }

    public synchronized void endConversion() {
        converting = false;
    }

    public synchronized boolean isConverting() {
        return converting;
    }

    public synchronized int addShield(final int amount, final int cap) {
        shield = Math.max(0, Math.min(Math.max(0, cap), shield + Math.max(0, amount)));
        return shield;
    }

    public synchronized int shield() {
        return shield;
    }

    /** Absorbs what it can and reports the absorbed amount; the pool never goes negative. */
    public synchronized int absorb(final int amount) {
        final int absorbed = Math.max(0, Math.min(shield, Math.max(0, amount)));
        shield -= absorbed;
        return absorbed;
    }

    // ===== Csontpap: Velő + Osszárium =====

    public synchronized int addMarrow(final int amount, final int maximum) {
        marrow = Math.max(0, Math.min(Math.max(1, maximum), marrow + Math.max(0, amount)));
        return marrow;
    }

    public synchronized int marrow() {
        return marrow;
    }

    /** Condenses one Osszárium charge when enough Velő stands; the Velő is spent for it. */
    public synchronized boolean condenseOssuary(final int threshold, final int maximum) {
        final int cost = Math.max(1, threshold);
        if (marrow < cost || ossuary >= Math.max(1, maximum)) return false;
        marrow -= cost;
        ossuary++;
        return true;
    }

    public synchronized int ossuary() {
        return ossuary;
    }

    public synchronized boolean consumeOssuary() {
        if (ossuary <= 0) return false;
        ossuary--;
        return true;
    }

    // ===== Árnyék: Őrület + Küszöb =====

    public synchronized int addMadness(final int amount, final long now,
                                       final long decayDelayMillis,
                                       final double decayPerSecond) {
        decayMadness(now, decayDelayMillis, decayPerSecond);
        madness = clampPercent(madness + Math.max(0, amount));
        madnessLastGainAt = now;
        madnessLastDecayAt = now;
        return madness;
    }

    public synchronized int madness(final long now, final long decayDelayMillis,
                                    final double decayPerSecond) {
        decayMadness(now, decayDelayMillis, decayPerSecond);
        return madness;
    }

    public synchronized boolean isBeyondThreshold(final int threshold, final long now,
                                                  final long decayDelayMillis,
                                                  final double decayPerSecond) {
        return madness(now, decayDelayMillis, decayPerSecond) >= Math.max(1, threshold);
    }

    /** Deliberate vent (dispersion or a doctrine effect). Returns the vented amount. */
    public synchronized int ventMadness(final int amount) {
        final int vented = Math.max(0, Math.min(amount, madness));
        madness -= vented;
        return vented;
    }

    /** Spec switch cleanup: the chosen prayer survives, every progress and pool does not. */
    public synchronized void clearSpecializationState() {
        verses = 0;
        recitedUntil = 0L;
        atonementUntil = 0L;
        shield = 0;
        converting = false;
        marrow = 0;
        ossuary = 0;
        madness = 0;
        madnessLastGainAt = 0L;
        madnessLastDecayAt = 0L;
    }

    /** Death/logout/admin reset cleanup: the prayer choice returns to the spec default too. */
    public synchronized void clearAll() {
        clearSpecializationState();
        litany = null;
    }

    private void decayMadness(final long now, final long decayDelayMillis,
                              final double decayPerSecond) {
        if (madness <= 0 || madnessLastGainAt <= 0L || decayPerSecond <= 0.0D) return;
        final long decayStartsAt = madnessLastGainAt + Math.max(0L, decayDelayMillis);
        if (now <= decayStartsAt) return;
        final long from = Math.max(decayStartsAt, madnessLastDecayAt);
        if (now <= from) return;
        final int decay = (int) Math.floor((now - from) / 1000.0D * decayPerSecond);
        if (decay > 0) {
            madness = clampPercent(madness - decay);
            madnessLastDecayAt = now;
        }
    }

    private static int clampPercent(final int value) {
        return Math.max(0, Math.min(100, value));
    }
}
