package hu.taliann.icesmp.professions;

import hu.taliann.icesmp.managers.ProfessionRecipeCatalog;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable authority shared by profession preview, admission and execution.
 *
 * <p>Input and output specialization rounding is intentionally performed for one craft first and
 * only then multiplied by the requested batch count. Therefore N individual crafts and one N-size
 * batch have identical economic arithmetic; batching is an ergonomic operation, never a rounding
 * arbitrage source.</p>
 */
public record ProfessionEffectiveCraftPlan(
        ProfessionRecipeCatalog.Recipe recipe,
        ProfessionSpecializationEconomyPolicy.Effect specialization,
        int batches,
        Map<Material, Integer> materialInputs,
        Map<String, Integer> uniqueInputs) {

    public ProfessionEffectiveCraftPlan {
        recipe = java.util.Objects.requireNonNull(recipe, "recipe");
        specialization = specialization == null
                ? ProfessionSpecializationEconomyPolicy.Effect.none() : specialization;
        if (batches < 1 || batches > 64) throw new IllegalArgumentException("batches must be 1..64");
        materialInputs = Map.copyOf(materialInputs == null ? Map.of() : materialInputs);
        uniqueInputs = Map.copyOf(uniqueInputs == null ? Map.of() : uniqueInputs);
    }

    public static ProfessionEffectiveCraftPlan of(
            final ProfessionRecipeCatalog.Recipe recipe,
            final ProfessionSpecializationEconomyPolicy.Effect specialization,
            final int batches) {
        java.util.Objects.requireNonNull(recipe, "recipe");
        final ProfessionSpecializationEconomyPolicy.Effect effect = specialization == null
                ? ProfessionSpecializationEconomyPolicy.Effect.none() : specialization;
        final LinkedHashMap<Material, Integer> materials = new LinkedHashMap<>();
        recipe.ingredients().forEach((material, amount) ->
                materials.put(material, effectiveInput(amount, effect, batches)));
        final LinkedHashMap<String, Integer> unique = new LinkedHashMap<>();
        recipe.uniqueIngredients().forEach((id, amount) ->
                unique.put(id, effectiveInput(amount, effect, batches)));
        return new ProfessionEffectiveCraftPlan(recipe, effect, batches, materials, unique);
    }

    public static int effectiveInput(
            final int perCraft,
            final ProfessionSpecializationEconomyPolicy.Effect specialization,
            final int batches) {
        if (perCraft < 1 || batches < 1 || batches > 64) {
            throw new IllegalArgumentException("invalid craft-plan amount/batch");
        }
        final ProfessionSpecializationEconomyPolicy.Effect effect = specialization == null
                ? ProfessionSpecializationEconomyPolicy.Effect.none() : specialization;
        final long oneCraft = effect.adjustInput(perCraft);
        final long total = Math.multiplyExact(oneCraft, batches);
        if (total > Integer.MAX_VALUE) throw new IllegalArgumentException("craft-plan input overflow");
        return (int) total;
    }

    /** Effective amount shown by preview and produced by execution for one result stack per craft. */
    public int effectiveOutputAmount(final int rawPerCraft) {
        if (rawPerCraft < 1) throw new IllegalArgumentException("invalid craft output amount");
        long oneCraft = rawPerCraft;
        if (specialization.outputMultiplier() > 1.0D
                && recipe.templateId() == null && recipe.affixTier() == null) {
            final long bonus = (long) Math.floor(rawPerCraft * (specialization.outputMultiplier() - 1.0D));
            oneCraft = Math.addExact(oneCraft, Math.max(0L, bonus));
        }
        final long total = Math.multiplyExact(oneCraft, batches);
        if (total > Integer.MAX_VALUE) throw new IllegalArgumentException("craft-plan output overflow");
        return (int) total;
    }

    public boolean hasIngredients(final Map<Material, Integer> materialAvailable,
                                  final Map<String, Integer> uniqueAvailable) {
        final Map<Material, Integer> materials = materialAvailable == null ? Map.of() : materialAvailable;
        final Map<String, Integer> unique = uniqueAvailable == null ? Map.of() : uniqueAvailable;
        for (final Map.Entry<Material, Integer> entry : materialInputs.entrySet()) {
            if (materials.getOrDefault(entry.getKey(), 0) < entry.getValue()) return false;
        }
        for (final Map.Entry<String, Integer> entry : uniqueInputs.entrySet()) {
            if (unique.getOrDefault(entry.getKey(), 0) < entry.getValue()) return false;
        }
        return true;
    }

    /**
     * Apply yield independently to every raw per-craft output. Pooling all outputs before floor()
     * would make larger batches economically different from repeated single crafts.
     */
    public List<ItemStack> effectiveOutputs(final List<ItemStack> rawPerCraftOutputs) {
        if (rawPerCraftOutputs == null || rawPerCraftOutputs.size() != batches) return List.of();
        final ArrayList<ItemStack> result = new ArrayList<>(rawPerCraftOutputs.size());
        for (final ItemStack raw : rawPerCraftOutputs) {
            if (raw == null || raw.getType().isAir() || raw.getAmount() <= 0) return List.of();
            final List<ItemStack> adjusted = specialization.adjustOutputs(recipe, List.of(raw));
            if (adjusted == null || adjusted.size() != 1 || adjusted.getFirst() == null
                    || adjusted.getFirst().getType().isAir() || adjusted.getFirst().getAmount() <= 0) {
                return List.of();
            }
            result.add(adjusted.getFirst().clone());
        }
        return List.copyOf(result);
    }
}
