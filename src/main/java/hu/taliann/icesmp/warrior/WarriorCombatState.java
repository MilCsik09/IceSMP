package hu.taliann.icesmp.warrior;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-player transient Warrior combat state.
 *
 * <p>This is intentionally concrete: it models only the Warrior's Csatatempó,
 * Berserker Vérőrület/Kimerülés and Guardian Őrség/Eskütárs state. Durable
 * class/spec/doctrine/mastery state remains in PlayerProfile.</p>
 */
public final class WarriorCombatState {

    public enum TempoTier {
        RENDEZETT,
        HEVES,
        TULCSORDULO
    }

    private int battleTempo;
    private long tempoLastCombatAt;
    private long tempoLastDecayAt;
    private UUID lastTarget;
    private long lastTargetSwitchAt;

    private int bloodFrenzy;
    private int exhaustion;
    private double exhaustionRemainder;
    private long berserkerUpdatedAt;
    private long overdriveUntil;
    private long aftermathUntil;

    private int guard;
    private UUID oathTargetId;
    private String oathTargetLabel = "";

    public synchronized int battleTempo(final long now, final long decayDelayMillis,
                                        final double decayPerSecond) {
        decayTempo(now, decayDelayMillis, decayPerSecond);
        return battleTempo;
    }

    public synchronized TempoTier tempoTier(final long now, final int heatedThreshold,
                                            final int overflowingThreshold,
                                            final long decayDelayMillis,
                                            final double decayPerSecond) {
        final int value = battleTempo(now, decayDelayMillis, decayPerSecond);
        if (value >= overflowingThreshold) return TempoTier.TULCSORDULO;
        if (value >= heatedThreshold) return TempoTier.HEVES;
        return TempoTier.RENDEZETT;
    }

    public synchronized int addTempoFromAttack(final UUID targetId,
                                               final int attackGain,
                                               final int targetSwitchBonus,
                                               final long switchCooldownMillis,
                                               final long now,
                                               final long decayDelayMillis,
                                               final double decayPerSecond) {
        Objects.requireNonNull(targetId, "targetId");
        decayTempo(now, decayDelayMillis, decayPerSecond);
        int gain = Math.max(0, attackGain);
        if (lastTarget != null && !lastTarget.equals(targetId)
                && now - lastTargetSwitchAt >= Math.max(0L, switchCooldownMillis)) {
            gain = Math.addExact(gain, Math.max(0, targetSwitchBonus));
            lastTargetSwitchAt = now;
        }
        lastTarget = targetId;
        battleTempo = clampPercent(Math.addExact(battleTempo, gain));
        touchTempo(now);
        return battleTempo;
    }

    public synchronized int addTempo(final int amount, final long now,
                                     final long decayDelayMillis,
                                     final double decayPerSecond) {
        decayTempo(now, decayDelayMillis, decayPerSecond);
        battleTempo = clampPercent(Math.addExact(battleTempo, Math.max(0, amount)));
        touchTempo(now);
        return battleTempo;
    }

    public synchronized int finishTempo(final int retainedTempo) {
        battleTempo = clampPercent(retainedTempo);
        lastTarget = null;
        return battleTempo;
    }

    public synchronized void refreshBerserker(final long now,
                                              final int highFuryThreshold,
                                              final double exhaustionPerSecondHigh,
                                              final double exhaustionRecoveryPerSecond,
                                              final double overdriveExhaustionPerSecond,
                                              final long aftermathMillis,
                                              final int overdriveEndExhaustion) {
        if (berserkerUpdatedAt <= 0L) {
            berserkerUpdatedAt = now;
            return;
        }
        long cursor = berserkerUpdatedAt;
        if (now <= cursor) return;

        if (overdriveUntil > cursor) {
            final long activeUntil = Math.min(now, overdriveUntil);
            applyExhaustionRate(activeUntil - cursor,
                    Math.max(0.0D, overdriveExhaustionPerSecond));
            cursor = activeUntil;
        }

        if (overdriveUntil > 0L && now >= overdriveUntil) {
            final long endedAt = overdriveUntil;
            overdriveUntil = 0L;
            aftermathUntil = Math.max(aftermathUntil,
                    endedAt + Math.max(0L, aftermathMillis));
            exhaustion = clampPercent(exhaustion + Math.max(0, overdriveEndExhaustion));
            if (exhaustion == 100) exhaustionRemainder = 0.0D;
        }

        if (now > cursor) {
            final double rate = bloodFrenzy >= highFuryThreshold
                    ? Math.max(0.0D, exhaustionPerSecondHigh)
                    : -Math.max(0.0D, exhaustionRecoveryPerSecond);
            applyExhaustionRate(now - cursor, rate);
        }
        berserkerUpdatedAt = now;
    }

    public synchronized int addBloodFrenzy(final int amount, final long now,
                                           final int highFuryThreshold,
                                           final double exhaustionPerSecondHigh,
                                           final double exhaustionRecoveryPerSecond,
                                           final double overdriveExhaustionPerSecond,
                                           final long aftermathMillis,
                                           final int overdriveEndExhaustion) {
        refreshBerserker(now, highFuryThreshold, exhaustionPerSecondHigh,
                exhaustionRecoveryPerSecond, overdriveExhaustionPerSecond,
                aftermathMillis, overdriveEndExhaustion);
        bloodFrenzy = clampPercent(bloodFrenzy + Math.max(0, amount));
        return bloodFrenzy;
    }

    public synchronized int bloodFrenzy(final long now,
                                        final int highFuryThreshold,
                                        final double exhaustionPerSecondHigh,
                                        final double exhaustionRecoveryPerSecond,
                                        final double overdriveExhaustionPerSecond,
                                        final long aftermathMillis,
                                        final int overdriveEndExhaustion) {
        refreshBerserker(now, highFuryThreshold, exhaustionPerSecondHigh,
                exhaustionRecoveryPerSecond, overdriveExhaustionPerSecond,
                aftermathMillis, overdriveEndExhaustion);
        return bloodFrenzy;
    }

    public synchronized int exhaustion(final long now,
                                       final int highFuryThreshold,
                                       final double exhaustionPerSecondHigh,
                                       final double exhaustionRecoveryPerSecond,
                                       final double overdriveExhaustionPerSecond,
                                       final long aftermathMillis,
                                       final int overdriveEndExhaustion) {
        refreshBerserker(now, highFuryThreshold, exhaustionPerSecondHigh,
                exhaustionRecoveryPerSecond, overdriveExhaustionPerSecond,
                aftermathMillis, overdriveEndExhaustion);
        return exhaustion;
    }

    public synchronized boolean startOverdrive(final long now,
                                               final int highFuryThreshold,
                                               final int maximumStartingExhaustion,
                                               final long durationMillis,
                                               final double exhaustionPerSecondHigh,
                                               final double exhaustionRecoveryPerSecond,
                                               final double overdriveExhaustionPerSecond,
                                               final long aftermathMillis,
                                               final int overdriveEndExhaustion) {
        refreshBerserker(now, highFuryThreshold, exhaustionPerSecondHigh,
                exhaustionRecoveryPerSecond, overdriveExhaustionPerSecond,
                aftermathMillis, overdriveEndExhaustion);
        if (bloodFrenzy < highFuryThreshold || exhaustion >= maximumStartingExhaustion
                || aftermathUntil > now || overdriveUntil > now) {
            return false;
        }
        overdriveUntil = now + Math.max(1L, durationMillis);
        return true;
    }

    public synchronized boolean overdriveActive(final long now) {
        return overdriveUntil > now;
    }

    public synchronized boolean aftermathActive(final long now) {
        return aftermathUntil > now;
    }

    public synchronized void safeDump(final int furySpend, final int exhaustionReduction) {
        bloodFrenzy = clampPercent(bloodFrenzy - Math.max(0, furySpend));
        exhaustion = clampPercent(exhaustion - Math.max(0, exhaustionReduction));
        exhaustionRemainder = 0.0D;
        overdriveUntil = 0L;
    }

    public synchronized void forceMaximumExhaustion() {
        exhaustion = 100;
        exhaustionRemainder = 0.0D;
        bloodFrenzy = 0;
        overdriveUntil = 0L;
    }

    public synchronized int guard() {
        return guard;
    }

    public synchronized int addGuard(final int amount) {
        guard = clampPercent(guard + Math.max(0, amount));
        return guard;
    }

    public synchronized boolean spendGuard(final int amount) {
        final int cost = Math.max(0, amount);
        if (guard < cost) return false;
        guard -= cost;
        return true;
    }

    public synchronized void setOathTarget(final UUID targetId, final String label) {
        oathTargetId = Objects.requireNonNull(targetId, "targetId");
        oathTargetLabel = label == null ? "" : label.trim();
    }

    public synchronized Optional<UUID> oathTargetId() {
        return Optional.ofNullable(oathTargetId);
    }

    public synchronized String oathTargetLabel() {
        return oathTargetLabel;
    }

    public synchronized void clearOathTarget() {
        oathTargetId = null;
        oathTargetLabel = "";
    }

    /**
     * Spec switch cleanup. Csatatempó is class-common and deliberately survives;
     * spec-local state must not cross Berserker ↔ Guardian.
     */
    public synchronized void clearSpecializationState() {
        bloodFrenzy = 0;
        exhaustion = 0;
        exhaustionRemainder = 0.0D;
        berserkerUpdatedAt = 0L;
        overdriveUntil = 0L;
        aftermathUntil = 0L;
        guard = 0;
        clearOathTarget();
    }

    /** Death/logout/admin reset cleanup. */
    public synchronized void clearAll() {
        battleTempo = 0;
        tempoLastCombatAt = 0L;
        tempoLastDecayAt = 0L;
        lastTarget = null;
        lastTargetSwitchAt = 0L;
        clearSpecializationState();
    }

    private void applyExhaustionRate(final long elapsedMillis, final double ratePerSecond) {
        if (elapsedMillis <= 0L || ratePerSecond == 0.0D) return;
        final double exact = exhaustionRemainder
                + elapsedMillis / 1000.0D * ratePerSecond;
        final int whole = exact >= 0.0D
                ? (int) Math.floor(exact)
                : (int) Math.ceil(exact);
        if (whole == 0) {
            exhaustionRemainder = exact;
            return;
        }
        exhaustion = clampPercent(exhaustion + whole);
        exhaustionRemainder = exact - whole;
        if ((exhaustion == 100 && exhaustionRemainder > 0.0D)
                || (exhaustion == 0 && exhaustionRemainder < 0.0D)) {
            exhaustionRemainder = 0.0D;
        }
    }

    private void decayTempo(final long now, final long decayDelayMillis,
                            final double decayPerSecond) {
        if (battleTempo <= 0 || tempoLastCombatAt <= 0L || decayPerSecond <= 0.0D) {
            if (tempoLastDecayAt <= 0L) tempoLastDecayAt = now;
            return;
        }
        final long decayStartsAt = tempoLastCombatAt + Math.max(0L, decayDelayMillis);
        if (now <= decayStartsAt) return;
        final long from = Math.max(decayStartsAt, tempoLastDecayAt);
        if (now <= from) return;
        final int decay = (int) Math.floor((now - from) / 1000.0D * decayPerSecond);
        if (decay > 0) {
            battleTempo = clampPercent(battleTempo - decay);
            tempoLastDecayAt = now;
        }
    }

    private void touchTempo(final long now) {
        tempoLastCombatAt = now;
        tempoLastDecayAt = now;
    }

    private static int clampPercent(final int value) {
        return Math.max(0, Math.min(100, value));
    }
}
