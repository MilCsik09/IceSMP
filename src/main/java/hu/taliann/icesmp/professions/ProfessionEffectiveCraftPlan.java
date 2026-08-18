package hu.taliann.icesmp.professions;

import hu.taliann.icesmp.managers.ProfessionRecipeCatalog;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable authority shared by profession preview and execution.
 *
 * <p>Specialization rounding is intentionally performed per crafted batch and only then scaled by
 * the requested batch count. This makes batching an ergonomic operation, not an economic bonus:
 * crafting N items separately and crafting the same N items as one batch has the same input and
 * yield arithmetic.</p>
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
        materialInputs = Map.copyOf(materialInputs);
        uniqueInputs = Map.copyOf(uniqueInputs);
    }

    public static ProfessionEffectiveCraftPlan of(
            final ProfessionRecipeCatalog.Recipe recipe,
            final ProfessionSpecializationEconomyPolicy.Effect specialization,
            final int batches) {
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
            final int perBatch,
            final ProfessionSpecializationEconomyPolicy.Effect specialization,
            final int batches) {
        if (perBatch < 1 || batches < 1 || batches > 64) {
            throw new IllegalArgumentException("invalid craft-plan amount/batch");
        }
        final long oneBatch = specialization.adjustInput(perBatch);
        final long total = Math.multiplyExact(oneBatch, batches);
        if (total > Integer.MAX_VALUE) throw new IllegalArgumentException("craft-plan input overflow");
        return (int) total;
    }

    public boolean hasIngredients(final Map<Material, Integer> materialAvailable,
                                  final Map<String, Integer> uniqueAvailable) {
        for (final Map.Entry<Material, Integer> entry : materialInputs.entrySet()) {
            if (materialAvailable.getOrDefault(entry.getKey(), 0) < entry.getValue()) return false;
        }
        for (final Map.Entry<String, Integer> entry : uniqueInputs.entrySet()) {
            if (uniqueAvailable.getOrDefault(entry.getKey(), 0) < entry.getValue()) return false;
        }
        return true;
    }

    public static int maxCraftableBatches(
            final ProfessionRecipeCatalog.Recipe recipe,
            final ProfessionSpecializationEconomyPolicy.Effect specialization,
            final int limit,
            final Map<Material, Integer> materialAvailable,
            final Map<String, Integer> uniqueAvailable) {
        final int bounded = Math.max(1, Math.min(64, limit));
        int result = 0;
        for (int candidate = 1; candidate <= bounded; candidate++) {
            if (!of(recipe, specialization, candidate).hasIngredients(materialAvailable, uniqueAvailable)) break;
            result = candidate;
        }
        return result;
    }

    /**
     * Apply yield independently to every raw per-batch output. Pooling all outputs before floor()
     * would make larger batches economically better than repeated single crafts.
     */
    public List<ItemStack> effectiveOutputs(final List<ItemStack> rawPerBatchOutputs) {
        if (rawPerBatchOutputs == null || rawPerBatchOutputs.size() != batches) return List.of();
        final ArrayList<ItemStack> result = new ArrayList<>(rawPerBatchOutputs.size());
        for (final ItemStack raw : rawPerBatchOutputs) {
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
