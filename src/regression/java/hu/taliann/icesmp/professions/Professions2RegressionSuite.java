package hu.taliann.icesmp.professions;

import java.util.UUID;

/** Deterministic Professions 2.0 quality contracts that need no Bukkit server. */
public final class Professions2RegressionSuite {
    public static void main(final String[] args) {
        masterworkRetryIsDeterministicAndBounded();
        masterworkEligibilityDoesNotChangeTemplateIdentity();
        System.out.println("Professions2RegressionSuite: OK");
    }

    private static void masterworkRetryIsDeterministicAndBounded() {
        final UUID operation = UUID.fromString("4df076f4-529c-4b3c-a1ad-242c55d9988e");
        final var tuning = new ProfessionCraftQualityPolicy.Tuning(
                0.10D, 0.003D, 0.20D, 0.05D, 0.08D, 0.02D, 0.003D, 0.18D);
        final var first = ProfessionCraftQualityPolicy.decide(operation, 50, true, true, tuning);
        final var retry = ProfessionCraftQualityPolicy.decide(operation, 50, true, true, tuning);
        require(first.equals(retry), "same operation must preserve Masterwork decision");
        require(first.masterworkChance() <= 0.18D, "Masterwork cannot become guaranteed");
        require(Double.compare(first.qualitySource().getAsDouble(), retry.qualitySource().getAsDouble()) == 0,
                "retry quality source must be deterministic");
        require(first.minimumQuality() < 1.0D, "profession skill must not guarantee perfect quality");
    }

    private static void masterworkEligibilityDoesNotChangeTemplateIdentity() {
        final UUID operation = UUID.fromString("3d46e53f-644d-409e-83d4-054d9479919d");
        final var tuning = new ProfessionCraftQualityPolicy.Tuning(
                0.10D, 0.003D, 0.20D, 0.05D, 0.08D, 0.02D, 0.003D, 0.18D);
        final var decision = ProfessionCraftQualityPolicy.decide(operation, 30, false, true, tuning);
        require(decision.operationId().equals(operation), "Masterwork is instance metadata on the same operation/item identity");
    }

    private static void require(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
