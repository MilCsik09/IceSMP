package hu.taliann.icesmp.shaman;

/**
 * Per-player transient Sámán combat state.
 *
 * <p>Not a clone of the earlier slices: Elemi plays one Overload charge fed by
 * totem-resonant casts, Erősítő plays a rhythm-built Maelstrom with alternating
 * Fegyveráldás sides, and Hullámhívó plays one signed tide (Dagály ↔ Apály).
 * Live totem identity stays in TotemManager; durable class/spec/doctrine/mastery
 * state remains in PlayerProfile.</p>
 */
public final class ShamanCombatState {

    public enum BlessingSide {
        VIHAR,
        FOLD
    }

    private int overload;

    private long lastMeleeHitAt;
    private BlessingSide blessingSide = BlessingSide.VIHAR;
    private int maelstrom;

    private int tide;

    // ===== Elemi: Overload =====

    /** A totem-resonant cast charges the Overload up to the threshold. */
    public synchronized int chargeOverload(final int amount, final int threshold) {
        overload = Math.min(Math.max(2, threshold), overload + Math.max(0, amount));
        return overload;
    }

    public synchronized boolean isOverloadArmed(final int threshold) {
        return overload >= Math.max(2, threshold);
    }

    /** Spends the armed Overload; the retained value supports the level-50 doctrine. */
    public synchronized void consumeOverload(final int retainedCharge) {
        overload = Math.max(0, Math.min(overload, retainedCharge));
    }

    public synchronized int overload() {
        return overload;
    }

    // ===== Erősítő: Fegyveráldás-ritmus + Maelstrom =====

    /**
     * Records a melee hit. A hit inside the rhythm window alternates the blessing side and earns
     * the rhythm bonus; hits outside the window only earn the base amount and reset the rhythm.
     * Returns the earned Maelstrom.
     */
    public synchronized int recordMeleeHit(final long now, final long windowMinMillis,
                                           final long windowMaxMillis, final int baseGain,
                                           final int rhythmBonus) {
        final long elapsed = lastMeleeHitAt == 0L ? -1L : now - lastMeleeHitAt;
        final boolean inRhythm = elapsed >= Math.max(0L, windowMinMillis)
                && elapsed <= Math.max(windowMinMillis, windowMaxMillis);
        int gain = Math.max(0, baseGain);
        if (inRhythm) {
            gain += Math.max(0, rhythmBonus);
            blessingSide = blessingSide == BlessingSide.VIHAR
                    ? BlessingSide.FOLD : BlessingSide.VIHAR;
        }
        lastMeleeHitAt = now;
        maelstrom = clampPercent(maelstrom + gain);
        return gain;
    }

    public synchronized BlessingSide blessingSide() {
        return blessingSide;
    }

    public synchronized int maelstrom() {
        return maelstrom;
    }

    public synchronized boolean spendMaelstrom(final int amount) {
        final int cost = Math.max(0, amount);
        if (maelstrom < cost) return false;
        maelstrom -= cost;
        return true;
    }

    /** Capstone vent: spends everything; the retained value supports the level-50 doctrine. */
    public synchronized int ventMaelstrom(final int retained) {
        final int spent = maelstrom;
        maelstrom = Math.max(0, Math.min(maelstrom, retained));
        return spent - maelstrom;
    }

    // ===== Hullámhívó: Dagály ↔ Apály =====

    /** Positive pushes toward Dagály, negative toward Apály; bounded at ±100. */
    public synchronized int pushTide(final int delta) {
        tide = Math.max(-100, Math.min(100, tide + delta));
        return tide;
    }

    public synchronized int tide() {
        return tide;
    }

    public synchronized boolean isHighTide(final int threshold) {
        return tide >= Math.max(1, threshold);
    }

    public synchronized boolean isLowTide(final int threshold) {
        return tide <= -Math.max(1, threshold);
    }

    /**
     * Consumes the reached tide for the empowered heal and flows back toward the middle,
     * keeping the configured fraction of the momentum.
     */
    public synchronized void consumeTide(final int retainedPercent) {
        final int keep = Math.max(0, Math.min(100, retainedPercent));
        tide = tide * keep / 100;
    }

    /** Spec switch cleanup: no shaman state is class-common enough to survive it. */
    public synchronized void clearSpecializationState() {
        overload = 0;
        lastMeleeHitAt = 0L;
        blessingSide = BlessingSide.VIHAR;
        maelstrom = 0;
        tide = 0;
    }

    /** Death/logout/admin reset cleanup. */
    public synchronized void clearAll() {
        clearSpecializationState();
    }

    private static int clampPercent(final int value) {
        return Math.max(0, Math.min(100, value));
    }
}
