package hu.taliann.icesmp.professions;

import hu.taliann.icesmp.data.ProfessionSpecializationType;
import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.itemization.ArmorFamily;
import hu.taliann.icesmp.itemization.ItemSalvageService;
import hu.taliann.icesmp.managers.ProfessionRecipeCatalog;
import org.bukkit.Material;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Deterministic Professions 2.0 contracts that need no live Bukkit server. */
public final class Professions2RegressionSuite {
    public static void main(final String[] args) {
        masterworkRetryIsDeterministicAndBounded();
        masterworkEligibilityDoesNotChangeTemplateIdentity();
        familySalvageMaterialIdsAreStableAndDistinct();
        professionSpecializationRolesAreCompleteAndDiverse();
        effectiveCraftPlanHasNoBatchRoundingArbitrage();
        specializationPlanExamplesMatchPreviewContract();
        blueprintRecoveryMatrixIsExact();
        System.out.println("Professions2RegressionSuite: OK");
    }

    private static void masterworkRetryIsDeterministicAndBounded() {
        final UUID operation = UUID.fromString("4df076f4-529c-4b3c-a1ad-242c55d9988e");
        final var tuning = new ProfessionCraftQualityPolicy.Tuning(
                0.10D, 0.003D, 0.20D, 0.05D, 0.08D,
                0.02D, 0.003D, 0.18D);
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
                0.10D, 0.003D, 0.20D, 0.05D, 0.08D,
                0.02D, 0.003D, 0.18D);
        final var decision = ProfessionCraftQualityPolicy.decide(operation, 30, false, true, tuning);
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
        for (final ProfessionSpecializationType specialization : ProfessionSpecializationType.values()) {
            require(ProfessionSpecializationEconomyPolicy.roleOf(specialization)
                            != ProfessionSpecializationEconomyPolicy.Role.NONE,
                    "profession specialization has no economic role: " + specialization);
        }
        final long roles = Arrays.stream(ProfessionSpecializationType.values())
                .map(ProfessionSpecializationEconomyPolicy::roleOf).distinct().count();
        require(roles >= 6, "profession specializations collapsed into one mandatory role");
    }

    private static void effectiveCraftPlanHasNoBatchRoundingArbitrage() {
        final var efficiency = effect(
                ProfessionSpecializationEconomyPolicy.Role.EQUIPMENT_EXPERTISE, 0.90D, 1.0D);
        final var recipe = recipe(1, Map.of(Material.IRON_INGOT, 16), Map.of());
        require(input(recipe, efficiency, 1) == 15,
                "16 base input at 10% efficiency must become 15 for one craft");
        require(input(recipe, efficiency, 2) == 30,
                "2x batch must equal two independently rounded 1x crafts");
        require(input(recipe, efficiency, 5) == 75,
                "5x batch must equal five independently rounded 1x crafts");
        require(input(recipe, efficiency, 10) == 150,
                "maximum regression batch must not gain a bulk rounding discount");

        final var one = ProfessionEffectiveCraftPlan.of(recipe, efficiency, 1);
        require(one.hasIngredients(Map.of(Material.IRON_INGOT, 15), Map.of()),
                "15 material must satisfy the effective one-craft plan");
        require(!one.hasIngredients(Map.of(Material.IRON_INGOT, 14), Map.of()),
                "14 material must fail the same plan");
    }

    private static void specializationPlanExamplesMatchPreviewContract() {
        for (final var role : List.of(
                ProfessionSpecializationEconomyPolicy.Role.PROCESSING_EFFICIENCY,
                ProfessionSpecializationEconomyPolicy.Role.CONSUMABLE_EFFICIENCY,
                ProfessionSpecializationEconomyPolicy.Role.BLUEPRINT_EFFICIENCY,
                ProfessionSpecializationEconomyPolicy.Role.EQUIPMENT_EXPERTISE,
                ProfessionSpecializationEconomyPolicy.Role.SERVICE_EXPERTISE)) {
            final var plan = ProfessionEffectiveCraftPlan.of(
                    recipe(1, Map.of(Material.IRON_INGOT, 16), Map.of("regression_unique", 16)),
                    effect(role, 0.90D, 1.0D), 5);
            require(plan.materialInputs().get(Material.IRON_INGOT) == 75,
                    role + " must use per-craft input rounding for plain material");
            require(plan.uniqueInputs().get("regression_unique") == 75,
                    role + " must use the same input rounding for unique material");
        }

        for (final var role : List.of(
                ProfessionSpecializationEconomyPolicy.Role.PROCESSING_YIELD,
                ProfessionSpecializationEconomyPolicy.Role.CONSUMABLE_YIELD)) {
            final var plan = ProfessionEffectiveCraftPlan.of(
                    recipe(16, Map.of(Material.IRON_INGOT, 1), Map.of()),
                    effect(role, 1.0D, 1.10D), 5);
            require(plan.effectiveOutputAmount(16) == 85,
                    role + " 5x yield must equal five independently rounded 17-output crafts");
        }
    }

    private static void blueprintRecoveryMatrixIsExact() {
        require(BlueprintRecoveryPolicy.decide(false, false)
                        == BlueprintRecoveryPolicy.Decision.ROLLBACK_UNTOUCHED,
                "PREPARED before reservation rolls back untouched");
        require(BlueprintRecoveryPolicy.decide(false, true)
                        == BlueprintRecoveryPolicy.Decision.RELEASE_AND_ROLLBACK,
                "reserved but not learned releases the exact blueprint");
        require(BlueprintRecoveryPolicy.decide(true, true)
                        == BlueprintRecoveryPolicy.Decision.CONSUME_AND_COMMIT,
                "learned plus reservation consumes exactly one and commits");
        require(BlueprintRecoveryPolicy.decide(true, false)
                        == BlueprintRecoveryPolicy.Decision.COMMIT_CONSUMED,
                "learned without reservation means consumption already crossed durability boundary");
    }

    private static int input(final ProfessionRecipeCatalog.Recipe recipe,
                             final ProfessionSpecializationEconomyPolicy.Effect effect,
                             final int batches) {
        return ProfessionEffectiveCraftPlan.of(recipe, effect, batches)
                .materialInputs().get(Material.IRON_INGOT);
    }

    private static ProfessionSpecializationEconomyPolicy.Effect effect(
            final ProfessionSpecializationEconomyPolicy.Role role,
            final double input, final double output) {
        return new ProfessionSpecializationEconomyPolicy.Effect(role, "regression", input, output);
    }

    private static ProfessionRecipeCatalog.Recipe recipe(
            final int resultAmount,
            final Map<Material, Integer> materials,
            final Map<String, Integer> unique) {
        return new ProfessionRecipeCatalog.Recipe(
                "regression", ProfessionType.ARMORER, 1, false,
                "Regression", "processing", Material.IRON_INGOT, resultAmount,
                null, null, materials, unique, List.of(), null, null,
                false, null, "processing", null, false);
    }

    private static void require(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
