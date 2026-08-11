package hu.taliann.icesmp.evoker;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-player transient Sárkányidéző combat state.
 *
 * <p>Deliberately not a Warrior clone: there is no persistent class meter. The class core is a
 * seconds-scale Felerősítés charge, Perzselés tracks a single red/blue alternation counter and
 * Megőrzés tracks the Visszhang window plus one narrow, heal-only Időlenyomat (recorded health,
 * nothing else — never inventory, position, quests, items or currency). Durable
 * class/spec/doctrine/mastery state remains in PlayerProfile.</p>
 */
public final class EvokerCombatState {

    public enum EssenceColor {
        VOROS,
        KEK
    }

    private String chargingSpellId = "";
    private long chargeStartedAt;

    private EssenceColor lastEssenceColor;
    private int resonance;

    private long echoArmedUntil;
    private double imprintHealth;
    private long imprintExpiresAt;
    private UUID markedAllyId;
    private String markedAllyLabel = "";

    // ===== Felerősítés (class core) =====

    /** Begins (or restarts) a charge for one concrete spell. Any previous charge is replaced. */
    public synchronized void startCharge(final String spellId, final long now) {
        chargingSpellId = normalize(spellId);
        chargeStartedAt = now;
    }

    public synchronized boolean isCharging(final String spellId, final long now,
                                           final long fizzleMillis) {
        if (chargingSpellId.isEmpty() || !chargingSpellId.equals(normalize(spellId))) return false;
        if (now - chargeStartedAt > Math.max(0L, fizzleMillis)) {
            clearCharge();
            return false;
        }
        return true;
    }

    public synchronized String chargingSpellId() {
        return chargingSpellId;
    }

    /**
     * Release rank for the active charge: 1 immediately, 2/3 after the configured hold times.
     * Returns 0 when no live charge exists for the spell.
     */
    public synchronized int releaseRank(final String spellId, final long now,
                                        final long rank2HoldMillis, final long rank3HoldMillis,
                                        final long fizzleMillis) {
        if (!isCharging(spellId, now, fizzleMillis)) return 0;
        final long held = now - chargeStartedAt;
        if (held >= Math.max(rank2HoldMillis, rank3HoldMillis)) return 3;
        if (held >= Math.min(rank2HoldMillis, rank3HoldMillis)) return 2;
        return 1;
    }

    public synchronized void clearCharge() {
        chargingSpellId = "";
        chargeStartedAt = 0L;
    }

    // ===== Perzselés: Vörös–Kék Eszencia =====

    /**
     * Records one essence-typed cast. Alternating colors build resonance up to the burst
     * threshold; repeating a color restarts the alternation at one.
     */
    public synchronized int recordEssenceCast(final EssenceColor color, final int burstThreshold) {
        Objects.requireNonNull(color, "color");
        final int cap = Math.max(2, burstThreshold);
        resonance = lastEssenceColor != null && lastEssenceColor != color
                ? Math.min(cap, resonance + 1)
                : 1;
        lastEssenceColor = color;
        return resonance;
    }

    public synchronized boolean isBurstArmed(final int burstThreshold) {
        return resonance >= Math.max(2, burstThreshold);
    }

    /** Spends the armed burst; the retained value supports the level-50 doctrine variant. */
    public synchronized void consumeBurst(final int retainedResonance) {
        resonance = Math.max(0, Math.min(resonance, retainedResonance));
        if (resonance == 0) lastEssenceColor = null;
    }

    public synchronized int resonance() {
        return resonance;
    }

    public synchronized Optional<EssenceColor> lastEssenceColor() {
        return Optional.ofNullable(lastEssenceColor);
    }

    // ===== Megőrzés: Visszhang =====

    public synchronized void armEcho(final long now, final long windowMillis) {
        echoArmedUntil = now + Math.max(1L, windowMillis);
    }

    public synchronized boolean isEchoArmed(final long now) {
        return echoArmedUntil > now;
    }

    /** Returns true exactly once per armed window: the next prepared heal consumes it. */
    public synchronized boolean consumeEcho(final long now) {
        if (echoArmedUntil <= now) return false;
        echoArmedUntil = 0L;
        return true;
    }

    // ===== Megőrzés: Időlenyomat (heal-only, single-use, window-bounded) =====

    public synchronized void recordImprint(final double health, final long now,
                                           final long windowMillis) {
        imprintHealth = Math.max(0.0D, health);
        imprintExpiresAt = now + Math.max(1L, windowMillis);
    }

    public synchronized boolean isImprintAlive(final long now) {
        return imprintExpiresAt > now && imprintHealth > 0.0D;
    }

    /**
     * Consumes the imprint and returns the restore target: never above the recorded health,
     * never above current + cap, never below current. A zero return means no heal.
     */
    public synchronized double consumeImprintRestore(final long now, final double currentHealth,
                                                     final double maximumRestoreGain) {
        if (!isImprintAlive(now)) return 0.0D;
        final double recorded = imprintHealth;
        imprintHealth = 0.0D;
        imprintExpiresAt = 0L;
        final double target = Math.min(recorded,
                currentHealth + Math.max(0.0D, maximumRestoreGain));
        return target > currentHealth ? target : 0.0D;
    }

    public synchronized long imprintRemainingMillis(final long now) {
        return isImprintAlive(now) ? imprintExpiresAt - now : 0L;
    }

    // ===== Megőrzés: jelölt szövetséges =====

    public synchronized void setMarkedAlly(final UUID allyId, final String label) {
        markedAllyId = Objects.requireNonNull(allyId, "allyId");
        markedAllyLabel = label == null ? "" : label.trim();
    }

    public synchronized Optional<UUID> markedAllyId() {
        return Optional.ofNullable(markedAllyId);
    }

    public synchronized String markedAllyLabel() {
        return markedAllyLabel;
    }

    public synchronized void clearMarkedAlly() {
        markedAllyId = null;
        markedAllyLabel = "";
    }

    /**
     * Spec switch cleanup. A live Felerősítés charge must not survive a loadout switch either:
     * carrying a held charge across a spec change would be a free pre-loaded burst.
     */
    public synchronized void clearSpecializationState() {
        clearCharge();
        lastEssenceColor = null;
        resonance = 0;
        echoArmedUntil = 0L;
        imprintHealth = 0.0D;
        imprintExpiresAt = 0L;
        clearMarkedAlly();
    }

    /** Death/logout/admin reset cleanup. */
    public synchronized void clearAll() {
        clearSpecializationState();
    }

    private static String normalize(final String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
