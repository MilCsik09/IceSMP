package hu.taliann.icesmp.professions;

import hu.taliann.icesmp.managers.ConfigManager;

import java.util.SplittableRandom;
import java.util.UUID;
import java.util.function.DoubleSupplier;

/** Deterministic per-operation quality/Masterwork policy: retrying the same operation cannot reroll. */
public final class ProfessionCraftQualityPolicy {

    private static final long QUALITY_SALT = 0x4f1bbcdc6a9d37a1L;
    private static final long MASTERWORK_SALT = 0x71e398b5d9c34f27L;

    public record Tuning(double baseFloor, double qualityPerLevel, double maxLevelContribution,
                         double blueprintBonus, double masterworkQualityBonus,
                         double masterworkBaseChance, double masterworkChancePerLevel,
                         double maximumMasterworkChance) {
        public Tuning {
            validate01(baseFloor, "baseFloor");
            validate01(qualityPerLevel, "qualityPerLevel");
            validate01(maxLevelContribution, "maxLevelContribution");
            validate01(blueprintBonus, "blueprintBonus");
            validate01(masterworkQualityBonus, "masterworkQualityBonus");
            validate01(masterworkBaseChance, "masterworkBaseChance");
            validate01(masterworkChancePerLevel, "masterworkChancePerLevel");
            validate01(maximumMasterworkChance, "maximumMasterworkChance");
        }
    }

    public record Decision(UUID operationId, boolean masterwork, double minimumQuality,
                           double masterworkChance, long deterministicSeed) {
        public Decision {
            java.util.Objects.requireNonNull(operationId, "operationId");
            validate01(minimumQuality, "minimumQuality");
            validate01(masterworkChance, "masterworkChance");
        }

        public DoubleSupplier qualitySource() {
            final SplittableRandom random = new SplittableRandom(deterministicSeed ^ QUALITY_SALT);
            return random::nextDouble;
        }
    }

    private ProfessionCraftQualityPolicy() { }

    public static Tuning from(final ConfigManager config) {
        return new Tuning(
                clamp01(config.getDouble("itemization.crafting.base-minimum-quality", 0.10D)),
                clamp01(config.getDouble("itemization.crafting.quality-per-profession-level", 0.003D)),
                clamp01(config.getDouble("itemization.crafting.maximum-level-contribution", 0.20D)),
                clamp01(config.getDouble("itemization.crafting.blueprint-quality-bonus", 0.05D)),
                clamp01(config.getDouble("itemization.crafting.masterwork-quality-bonus", 0.08D)),
                clamp01(config.getDouble("professions.masterwork.base-chance", 0.02D)),
                clamp01(config.getDouble("professions.masterwork.chance-per-level", 0.003D)),
                clamp01(config.getDouble("professions.masterwork.maximum-chance", 0.18D)));
    }

    public static Decision decide(final UUID operationId, final int professionLevel,
                                  final boolean blueprint, final boolean masterworkEligible,
                                  final Tuning tuning) {
        java.util.Objects.requireNonNull(operationId, "operationId");
        java.util.Objects.requireNonNull(tuning, "tuning");
        final int level = Math.max(0, Math.min(50, professionLevel));
        final double chance = masterworkEligible
                ? Math.min(tuning.maximumMasterworkChance(),
                    tuning.masterworkBaseChance() + level * tuning.masterworkChancePerLevel())
                : 0.0D;
        final long seed = mix(operationId.getMostSignificantBits(), operationId.getLeastSignificantBits());
        final boolean masterwork = chance > 0.0D
                && new SplittableRandom(seed ^ MASTERWORK_SALT).nextDouble() < chance;
        final double levelContribution = Math.min(tuning.maxLevelContribution(),
                level * tuning.qualityPerLevel());
        final double floor = clamp01(tuning.baseFloor() + levelContribution
                + (blueprint ? tuning.blueprintBonus() : 0.0D)
                + (masterwork ? tuning.masterworkQualityBonus() : 0.0D));
        return new Decision(operationId, masterwork, floor, chance, seed);
    }

    private static long mix(final long a, final long b) {
        long z = a ^ Long.rotateLeft(b, 29) ^ 0x9e3779b97f4a7c15L;
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    private static double clamp01(final double value) {
        if (!Double.isFinite(value)) return 0.0D;
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static void validate01(final double value, final String field) {
        if (!Double.isFinite(value) || value < 0.0D || value > 1.0D) {
            throw new IllegalArgumentException(field + " must be in [0,1]");
        }
    }
}
