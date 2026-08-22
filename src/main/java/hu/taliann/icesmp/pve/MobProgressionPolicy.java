package hu.taliann.icesmp.pve;

import java.util.Objects;

/** Pure hybrid level and independent HP/damage curve authority. */
public final class MobProgressionPolicy {
    public enum Source { ENCOUNTER_OVERRIDE, AUTHORED_LOCATION, MOB_TEMPLATE, WILDERNESS }

    public record Tuning(int normalMaximum, int hardCap, int authoredBossCap,
                         double healthPerLevel, double damagePerLevel,
                         double maximumHealthMultiplier, double maximumDamageMultiplier) {
        public Tuning {
            if (normalMaximum < 1 || hardCap < normalMaximum || authoredBossCap < hardCap
                    || authoredBossCap > 1000
                    || !bounded(healthPerLevel, 0.0D, 1.0D)
                    || !bounded(damagePerLevel, 0.0D, 0.25D)
                    || !bounded(maximumHealthMultiplier, 1.0D, 100.0D)
                    || !bounded(maximumDamageMultiplier, 1.0D, 20.0D)) {
                throw new IllegalArgumentException("invalid mob progression tuning");
            }
        }

        public static Tuning defaults() {
            return new Tuning(50, 70, 200, 0.08D, 0.025D, 8.0D, 3.0D);
        }
    }

    public record Context(Integer encounterOverride, Integer authoredLocationLevel,
                          Integer templateLevel, int wildernessLevel, int territoryBonus,
                          int biomeBonus, int depthBonus, int eventBonus,
                          boolean authoredMayExceedHardCap) {
        public Context {
            wildernessLevel = Math.max(1, wildernessLevel);
            territoryBonus = nonNegative(territoryBonus);
            biomeBonus = nonNegative(biomeBonus);
            depthBonus = nonNegative(depthBonus);
            eventBonus = nonNegative(eventBonus);
        }
    }

    public record Resolution(int level, Source source, int baseLevel, int appliedBonus,
                             boolean capped) { }

    public record ScaledStats(double maximumHealth, double attackDamage,
                              double healthMultiplier, double damageMultiplier) { }

    private MobProgressionPolicy() { }

    public static Resolution resolve(final Context context, final Tuning tuning) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(tuning, "tuning");
        final int base;
        final int bonus;
        final Source source;
        if (positive(context.encounterOverride())) {
            base = context.encounterOverride();
            bonus = 0;
            source = Source.ENCOUNTER_OVERRIDE;
        } else if (positive(context.authoredLocationLevel())) {
            base = context.authoredLocationLevel();
            bonus = context.eventBonus();
            source = Source.AUTHORED_LOCATION;
        } else if (positive(context.templateLevel())) {
            base = context.templateLevel();
            bonus = safeSum(context.territoryBonus(), context.biomeBonus(),
                    context.depthBonus(), context.eventBonus());
            source = Source.MOB_TEMPLATE;
        } else {
            base = Math.min(tuning.normalMaximum(), context.wildernessLevel());
            bonus = safeSum(context.territoryBonus(), context.biomeBonus(),
                    context.depthBonus(), context.eventBonus());
            source = Source.WILDERNESS;
        }
        final int raw = safeSum(base, bonus);
        final int cap = context.authoredMayExceedHardCap()
                && source == Source.ENCOUNTER_OVERRIDE ? tuning.authoredBossCap() : tuning.hardCap();
        final int level = Math.max(1, Math.min(cap, raw));
        return new Resolution(level, source, base, bonus, level != raw);
    }

    public static ScaledStats scale(final double baseHealth, final double baseDamage,
                                    final int level, final double rankHealthMultiplier,
                                    final double rankDamageMultiplier, final Tuning tuning) {
        Objects.requireNonNull(tuning, "tuning");
        if (!bounded(baseHealth, 0.01D, 1_000_000.0D)
                || !bounded(baseDamage, 0.0D, 100_000.0D)
                || !bounded(rankHealthMultiplier, 0.1D, 50.0D)
                || !bounded(rankDamageMultiplier, 0.0D, 20.0D)) {
            throw new IllegalArgumentException("invalid mob base stats");
        }
        final int boundedLevel = Math.max(1, Math.min(tuning.authoredBossCap(), level));
        final double healthCurve = Math.min(tuning.maximumHealthMultiplier(),
                1.0D + ((boundedLevel - 1L) * tuning.healthPerLevel()));
        final double damageCurve = Math.min(tuning.maximumDamageMultiplier(),
                1.0D + ((boundedLevel - 1L) * tuning.damagePerLevel()));
        final double healthMultiplier = healthCurve * rankHealthMultiplier;
        final double damageMultiplier = damageCurve * rankDamageMultiplier;
        return new ScaledStats(baseHealth * healthMultiplier, baseDamage * damageMultiplier,
                healthMultiplier, damageMultiplier);
    }

    public static int wildernessLevel(final double distance, final double blocksPerLevel,
                                      final int normalMaximum) {
        if (!Double.isFinite(distance) || !Double.isFinite(blocksPerLevel)
                || distance < 0.0D || blocksPerLevel <= 0.0D || normalMaximum < 1) {
            throw new IllegalArgumentException("invalid wilderness scaling input");
        }
        return Math.min(normalMaximum, 1 + (int) Math.floor(distance / blocksPerLevel));
    }

    public static int depthBonus(final int blockY, final int startY,
                                 final int blocksPerLevel, final int maximumBonus) {
        if (blocksPerLevel < 1 || maximumBonus < 0) throw new IllegalArgumentException("invalid depth tuning");
        if (blockY >= startY) return 0;
        return Math.min(maximumBonus, 1 + ((startY - blockY - 1) / blocksPerLevel));
    }

    private static boolean positive(final Integer value) {
        return value != null && value > 0;
    }

    private static int nonNegative(final int value) {
        if (value < 0) throw new IllegalArgumentException("negative difficulty bonus");
        return value;
    }

    private static int safeSum(final int... values) {
        long result = 0L;
        for (final int value : values) result += value;
        return (int) Math.min(Integer.MAX_VALUE, result);
    }

    private static boolean bounded(final double value, final double minimum, final double maximum) {
        return Double.isFinite(value) && value >= minimum && value <= maximum;
    }
}
