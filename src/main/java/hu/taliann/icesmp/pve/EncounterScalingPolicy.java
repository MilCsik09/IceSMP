package hu.taliann.icesmp.pve;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Stable encounter-start snapshot with diminishing party scaling. */
public final class EncounterScalingPolicy {
    public record Tuning(double playerCoefficient, double playerExponent,
                         double maximumHealthMultiplier, double damagePerDoubling,
                         double maximumDamageMultiplier, double combatPowerInfluence) {
        public Tuning {
            if (!bounded(playerCoefficient, 0.0D, 2.0D)
                    || !bounded(playerExponent, 0.1D, 1.0D)
                    || !bounded(maximumHealthMultiplier, 1.0D, 30.0D)
                    || !bounded(damagePerDoubling, 0.0D, 0.25D)
                    || !bounded(maximumDamageMultiplier, 1.0D, 3.0D)
                    || !bounded(combatPowerInfluence, 0.0D, 0.5D)) {
                throw new IllegalArgumentException("invalid encounter scaling tuning");
            }
        }

        public static Tuning defaults() {
            return new Tuning(0.65D, 0.8D, 12.0D, 0.04D, 1.18D, 0.20D);
        }
    }

    public record Snapshot(UUID encounterId, int encounterTier, Set<UUID> participants,
                           double averageCombatPower, double healthMultiplier,
                           double damageMultiplier, double mechanicRateMultiplier,
                           long createdAt) {
        public Snapshot {
            Objects.requireNonNull(encounterId, "encounterId");
            participants = Set.copyOf(participants);
            if (participants.isEmpty() || participants.size() > 128 || encounterTier < 1
                    || createdAt < 0L) throw new IllegalArgumentException("invalid encounter snapshot");
        }
    }

    private EncounterScalingPolicy() { }

    public static Snapshot snapshot(final UUID encounterId, final int encounterTier,
                                    final Set<UUID> participants,
                                    final double averageCombatPower,
                                    final double tierReferencePower,
                                    final long createdAt, final Tuning tuning) {
        Objects.requireNonNull(tuning, "tuning");
        final LinkedHashSet<UUID> boundedParticipants = new LinkedHashSet<>(
                Objects.requireNonNull(participants, "participants"));
        if (boundedParticipants.isEmpty() || boundedParticipants.size() > 128
                || boundedParticipants.contains(null)) {
            throw new IllegalArgumentException("invalid encounter participants");
        }
        final int count = boundedParticipants.size();
        final double countHealth = 1.0D + tuning.playerCoefficient()
                * Math.pow(Math.max(0, count - 1), tuning.playerExponent());
        final double reference = Math.max(1.0D, tierReferencePower);
        final double ratio = Math.max(0.25D, Math.min(4.0D,
                (Double.isFinite(averageCombatPower) ? averageCombatPower : reference) / reference));
        final double powerAdjustment = 1.0D + Math.max(-tuning.combatPowerInfluence(),
                Math.min(tuning.combatPowerInfluence(), (ratio - 1.0D) * tuning.combatPowerInfluence()));
        final double health = Math.min(tuning.maximumHealthMultiplier(), countHealth * powerAdjustment);
        final double damage = Math.min(tuning.maximumDamageMultiplier(),
                1.0D + tuning.damagePerDoubling() * (Math.log(count) / Math.log(2.0D)));
        final double mechanics = Math.min(1.5D, 1.0D + 0.08D * Math.log1p(count - 1));
        return new Snapshot(encounterId, Math.max(1, encounterTier), boundedParticipants,
                Math.max(0.0D, averageCombatPower), health, damage, mechanics, createdAt);
    }

    private static boolean bounded(final double value, final double minimum, final double maximum) {
        return Double.isFinite(value) && value >= minimum && value <= maximum;
    }
}
