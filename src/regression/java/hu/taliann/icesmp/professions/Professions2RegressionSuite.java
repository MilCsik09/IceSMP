package hu.taliann.icesmp.professions;

import hu.taliann.icesmp.data.ProfessionSpecializationType;
import hu.taliann.icesmp.itemization.ArmorFamily;
import hu.taliann.icesmp.itemization.ItemSalvageService;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

/** Deterministic Professions 2.0 contracts that need no live Bukkit server. */
public final class Professions2RegressionSuite {
    public static void main(final String[] args) {
        masterworkRetryIsDeterministicAndBounded();
        masterworkEligibilityDoesNotChangeTemplateIdentity();
        familySalvageMaterialIdsAreStableAndDistinct();
        professionSpecializationRolesAreCompleteAndDiverse();
        System.out.println("Professions2RegressionSuite: OK");
    }

    private static void masterworkRetryIsDeterministicAndBounded() {
        final UUID operation = UUID.fromString("4df076f4-529c-4b3c-a1ad-242c55d9988e");
        final var tuning = new ProfessionCraftQualityPolicy.Tuning(
                0.10D, 0.003D, 0.20D, 0.05D, 0.08D,
                0.02D, 0.003D, 0.18D);
        final var first = ProfessionCraftQualityPolicy.decide(
                operation, 50, true, true, tuning);
        final var retry = ProfessionCraftQualityPolicy.decide(
                operation, 50, true, true, tuning);
        require(first.equals(retry), "same operation must preserve Masterwork decision");
        require(first.masterworkChance() <= 0.18D,
                "Masterwork cannot become guaranteed");
        require(Double.compare(first.qualitySource().getAsDouble(),
                        retry.qualitySource().getAsDouble()) == 0,
                "retry quality source must be deterministic");
        require(first.minimumQuality() < 1.0D,
                "profession skill must not guarantee perfect quality");
    }

    private static void masterworkEligibilityDoesNotChangeTemplateIdentity() {
        final UUID operation = UUID.fromString("3d46e53f-644d-409e-83d4-054d9479919d");
        final var tuning = new ProfessionCraftQualityPolicy.Tuning(
                0.10D, 0.003D, 0.20D, 0.05D, 0.08D,
                0.02D, 0.003D, 0.18D);
        final var decision = ProfessionCraftQualityPolicy.decide(
                operation, 30, false, true, tuning);
        require(decision.operationId().equals(operation),
                "Masterwork is instance metadata on the same operation/item identity");
    }

    private static void familySalvageMaterialIdsAreStableAndDistinct() {
        final Map<ArmorFamily, String> expected = Map.of(
                ArmorFamily.CLOTH, "szovet_foszlany",
                ArmorFamily.LEATHER, "bor_hulladek",
                ArmorFamily.MAIL, "lanc_toredek",
                ArmorFamily.PLATE, "femhulladek");
        expected.forEach((family, id) -> require(
                id.equals(ItemSalvageService.familyScrapId(family)),
                "wrong family salvage material for " + family));
        require(expected.values().stream().distinct().count() == ArmorFamily.values().length,
                "each ArmorFamily must keep a distinct salvage material identity");
    }

    private static void professionSpecializationRolesAreCompleteAndDiverse() {
        require(ProfessionSpecializationType.values().length == 16,
                "unexpected profession specialization roster drift");
        for (final ProfessionSpecializationType specialization
                : ProfessionSpecializationType.values()) {
            require(ProfessionSpecializationEconomyPolicy.roleOf(specialization)
                            != ProfessionSpecializationEconomyPolicy.Role.NONE,
                    "profession specialization has no economic role: " + specialization);
        }
        final long roles = Arrays.stream(ProfessionSpecializationType.values())
                .map(ProfessionSpecializationEconomyPolicy::roleOf).distinct().count();
        require(roles >= 6, "profession specializations collapsed into one mandatory role");
        require(ProfessionSpecializationEconomyPolicy.roleOf(ProfessionSpecializationType.PROSPECTOR)
                        != ProfessionSpecializationEconomyPolicy.roleOf(ProfessionSpecializationType.EXCAVATOR),
                "miner specializations must offer different economic roles");
        require(ProfessionSpecializationEconomyPolicy.roleOf(ProfessionSpecializationType.POTION_MASTER)
                        != ProfessionSpecializationEconomyPolicy.roleOf(ProfessionSpecializationType.TRANSMUTER),
                "alchemist specializations must offer different economic roles");
        require(ProfessionSpecializationEconomyPolicy.roleOf(ProfessionSpecializationType.RUNEKEEPER)
                        != ProfessionSpecializationEconomyPolicy.roleOf(ProfessionSpecializationType.ARCANIST),
                "enchanter specializations must offer different economic roles");
    }

    private static void require(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
