package hu.taliann.icesmp.pve;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable ability registry entry; runtime owns telegraph, cast and cleanup. */
public record MobAbilityDefinition(String abilityId, Kind kind, long cooldownTicks,
                                   long telegraphTicks, long recoveryTicks,
                                   double radius, double power, int maxSummons,
                                   TargetRule targetRule, boolean interruptible,
                                   Set<MobRank> eligibleRanks,
                                   Set<MobArchetype> eligibleArchetypes,
                                   Map<String, Double> tuning) {
    public enum Kind {
        LUNGE,
        GROUND_SLAM,
        PROJECTILE_BURST,
        SHIELD,
        HEAL_PULSE,
        SUMMON,
        CLEAVE,
        POISON_CLOUD,
        DELAYED_RUNE,
        RETREAT,
        ALLY_BUFF
    }

    public enum TargetRule {
        SELF,
        CURRENT_TARGET,
        NEAREST_PLAYER;

        public static TargetRule parse(final String raw) {
            if (raw == null || raw.isBlank()) return CURRENT_TARGET;
            return valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        }
    }

    public MobAbilityDefinition {
        abilityId = id(abilityId, "ability id");
        kind = Objects.requireNonNull(kind, "kind");
        if (cooldownTicks < 10L || cooldownTicks > 12_000L
                || telegraphTicks < 0L || telegraphTicks > 200L
                || recoveryTicks < 0L || recoveryTicks > 1_200L
                || !Double.isFinite(radius) || radius < 0.5D || radius > 32.0D
                || !Double.isFinite(power) || power < 0.0D || power > 100.0D
                || maxSummons < 0 || maxSummons > 8) {
            throw new IllegalArgumentException("invalid mob ability bounds: " + abilityId);
        }
        if (dangerous(kind) && telegraphTicks < 10L) {
            throw new IllegalArgumentException("dangerous ability requires a readable telegraph");
        }
        targetRule = Objects.requireNonNull(targetRule, "targetRule");
        eligibleRanks = eligibleRanks == null ? Set.of() : Set.copyOf(eligibleRanks);
        eligibleArchetypes = eligibleArchetypes == null ? Set.of() : Set.copyOf(eligibleArchetypes);
        final LinkedHashMap<String, Double> values = new LinkedHashMap<>();
        if (tuning != null) tuning.forEach((key, value) -> {
            final double safe = value == null ? Double.NaN : value;
            if (!Double.isFinite(safe)) throw new IllegalArgumentException("invalid ability tuning");
            values.put(id(key, "ability tuning key"), safe);
        });
        if (values.size() > 32) throw new IllegalArgumentException("too many ability tuning values");
        tuning = Map.copyOf(values);
    }

    /** Source-compatible constructor for focused fixtures and older authored adapters. */
    public MobAbilityDefinition(final String abilityId, final Kind kind, final long cooldownTicks,
                                final long telegraphTicks, final double radius, final double power,
                                final int maxSummons, final Map<String, Double> tuning) {
        this(abilityId, kind, cooldownTicks, telegraphTicks, 0L, radius, power, maxSummons,
                TargetRule.CURRENT_TARGET, false, Set.of(), Set.of(), tuning);
    }

    public boolean eligible(final MobRank rank, final MobArchetype archetype) {
        return (eligibleRanks.isEmpty() || eligibleRanks.contains(rank))
                && (eligibleArchetypes.isEmpty() || (archetype != null && eligibleArchetypes.contains(archetype)));
    }

    public static Kind parseKind(final String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("ability kind required");
        return Kind.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }

    public static boolean dangerous(final Kind kind) {
        return kind == Kind.LUNGE || kind == Kind.GROUND_SLAM
                || kind == Kind.PROJECTILE_BURST || kind == Kind.SUMMON
                || kind == Kind.CLEAVE || kind == Kind.POISON_CLOUD
                || kind == Kind.DELAYED_RUNE;
    }

    static String id(final String raw, final String field) {
        final String normalized = Objects.requireNonNull(raw, field).trim()
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        if (normalized.isBlank() || normalized.length() > 96) {
            throw new IllegalArgumentException(field + " invalid");
        }
        return normalized;
    }
}
