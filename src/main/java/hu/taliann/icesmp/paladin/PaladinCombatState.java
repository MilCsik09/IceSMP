package hu.taliann.icesmp.paladin;

import java.util.EnumSet;
import java.util.Set;

/**
 * Per-player transient Paplovag combat state.
 *
 * <p>Meggyőződés is the class layer: acting in the chosen Eskü's role builds conviction with
 * lazy decay. Megtorló tracks the three Ítélet-jelek toward a Verdict, Oltalmazó tracks one
 * Pajzstöltet charge. The Eskü itself is a session-scoped direction choice defaulting to the
 * active specialization's role; durable class/spec state remains in PlayerProfile.</p>
 */
public final class PaladinCombatState {

    public enum Oath {
        IRGALOM,
        ITELET,
        OLTALMAZAS
    }

    public enum JudgmentMark {
        BUN,
        DAC,
        KARHOZAT
    }

    private Oath chosenOath;
    private int conviction;
    private long convictionLastGainAt;
    private long convictionLastDecayAt;

    private final Set<JudgmentMark> marks = EnumSet.noneOf(JudgmentMark.class);
    private long marksLastLitAt;

    private int shieldCharge;

    // ===== Eskü + Meggyőződés (class core) =====

    /** Explicit session choice; null falls back to the active specialization's role. */
    public synchronized void chooseOath(final Oath oath) {
        chosenOath = oath;
    }

    public synchronized Oath oathOrDefault(final Oath specDefault) {
        return chosenOath != null ? chosenOath : specDefault;
    }

    public synchronized int addConviction(final int amount, final long now,
                                          final long decayDelayMillis,
                                          final double decayPerSecond) {
        decayConviction(now, decayDelayMillis, decayPerSecond);
        conviction = clampPercent(conviction + Math.max(0, amount));
        convictionLastGainAt = now;
        convictionLastDecayAt = now;
        return conviction;
    }

    public synchronized int conviction(final long now, final long decayDelayMillis,
                                       final double decayPerSecond) {
        decayConviction(now, decayDelayMillis, decayPerSecond);
        return conviction;
    }

    // ===== Megtorló: Ítélet-jelek =====

    /** Lights one mark; a stale window clears the old set first. Returns the lit count. */
    public synchronized int lightMark(final JudgmentMark mark, final long now,
                                      final long windowMillis) {
        if (!marks.isEmpty() && now - marksLastLitAt > Math.max(1L, windowMillis)) {
            marks.clear();
        }
        marks.add(mark);
        marksLastLitAt = now;
        return marks.size();
    }

    public synchronized boolean isVerdictArmed(final long now, final long windowMillis) {
        if (!marks.isEmpty() && now - marksLastLitAt > Math.max(1L, windowMillis)) {
            marks.clear();
        }
        return marks.size() == JudgmentMark.values().length;
    }

    /** The Verdict consumes all three marks at once. */
    public synchronized boolean consumeVerdict(final long now, final long windowMillis) {
        if (!isVerdictArmed(now, windowMillis)) return false;
        marks.clear();
        return true;
    }

    public synchronized int markCount(final long now, final long windowMillis) {
        if (!marks.isEmpty() && now - marksLastLitAt > Math.max(1L, windowMillis)) {
            marks.clear();
        }
        return marks.size();
    }

    // ===== Oltalmazó: Pajzstöltet =====

    public synchronized int addShieldCharge(final int amount) {
        shieldCharge = clampPercent(shieldCharge + Math.max(0, amount));
        return shieldCharge;
    }

    public synchronized boolean spendShieldCharge(final int amount) {
        final int cost = Math.max(0, amount);
        if (shieldCharge < cost) return false;
        shieldCharge -= cost;
        return true;
    }

    public synchronized int shieldCharge() {
        return shieldCharge;
    }

    /** Spec switch cleanup; the session Eskü choice deliberately survives (class-level identity). */
    public synchronized void clearSpecializationState() {
        conviction = 0;
        convictionLastGainAt = 0L;
        convictionLastDecayAt = 0L;
        marks.clear();
        marksLastLitAt = 0L;
        shieldCharge = 0;
    }

    /** Death/logout/admin reset cleanup. */
    public synchronized void clearAll() {
        clearSpecializationState();
        chosenOath = null;
    }

    private void decayConviction(final long now, final long decayDelayMillis,
                                 final double decayPerSecond) {
        if (conviction <= 0 || convictionLastGainAt <= 0L || decayPerSecond <= 0.0D) return;
        final long decayStartsAt = convictionLastGainAt + Math.max(0L, decayDelayMillis);
        if (now <= decayStartsAt) return;
        final long from = Math.max(decayStartsAt, convictionLastDecayAt);
        if (now <= from) return;
        final int decay = (int) Math.floor((now - from) / 1000.0D * decayPerSecond);
        if (decay > 0) {
            conviction = clampPercent(conviction - decay);
            convictionLastDecayAt = now;
        }
    }

    private static int clampPercent(final int value) {
        return Math.max(0, Math.min(100, value));
    }
}
