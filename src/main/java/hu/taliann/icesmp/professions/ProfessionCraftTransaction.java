package hu.taliann.icesmp.professions;

import hu.taliann.icesmp.items.UniqueMaterialFactory;
import hu.taliann.icesmp.utils.PlainIngredients;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;
import java.util.Map;

/**
 * Owner-thread inventory transaction for profession processing/crafting.
 *
 * <p>Preview/preflight and commit use the same immutable {@link ProfessionEffectiveCraftPlan} and
 * the same storage simulation. Missing input or output capacity therefore cannot disagree between
 * GUI/click admission and the actual mutation. Persistence failure restores the exact pre-craft
 * storage snapshot and persists that rollback; no world-drop fallback exists.</p>
 */
public final class ProfessionCraftTransaction {

    public enum Status {
        APPLIED,
        MISSING_INGREDIENTS,
        INVENTORY_FULL,
        INVALID_BATCH,
        PERSISTENCE_FAILED
    }

    public record Result(Status status, int batches) {
        public boolean applied() { return status == Status.APPLIED; }
    }

    private record Prepared(Status status, ItemStack[] after) { }

    private final UniqueMaterialFactory uniqueMaterials;

    public ProfessionCraftTransaction(final UniqueMaterialFactory uniqueMaterials) {
        this.uniqueMaterials = java.util.Objects.requireNonNull(uniqueMaterials, "uniqueMaterials");
    }

    /** Side-effect-free owner-thread admission using the exact execution simulation. */
    public Result preflight(final Player player, final ProfessionEffectiveCraftPlan plan,
                            final List<ItemStack> rawPerCraftOutputs) {
        java.util.Objects.requireNonNull(player, "player");
        java.util.Objects.requireNonNull(plan, "plan");
        final Prepared prepared = prepare(player.getInventory().getStorageContents(), plan, rawPerCraftOutputs);
        return new Result(prepared.status(), prepared.status() == Status.APPLIED ? plan.batches() : 0);
    }

    /** Paper-runtime seam: same simulation as player preflight without mutating a live inventory. */
    public Result preflightStorage(final ItemStack[] storage,
                                   final ProfessionEffectiveCraftPlan plan,
                                   final List<ItemStack> rawPerCraftOutputs) {
        java.util.Objects.requireNonNull(plan, "plan");
        final Prepared prepared = prepare(storage, plan, rawPerCraftOutputs);
        return new Result(prepared.status(), prepared.status() == Status.APPLIED ? plan.batches() : 0);
    }

    public Result apply(final Player player, final ProfessionEffectiveCraftPlan plan,
                        final List<ItemStack> rawPerCraftOutputs) {
        java.util.Objects.requireNonNull(player, "player");
        java.util.Objects.requireNonNull(plan, "plan");
        final PlayerInventory inventory = player.getInventory();
        final ItemStack[] before = cloneContents(inventory.getStorageContents());
        final Prepared prepared = prepare(before, plan, rawPerCraftOutputs);
        if (prepared.status() != Status.APPLIED) {
            return new Result(prepared.status(), 0);
        }

        try {
            inventory.setStorageContents(cloneContents(prepared.after()));
            player.saveData();
            return new Result(Status.APPLIED, plan.batches());
        } catch (final RuntimeException persistenceFailure) {
            try {
                inventory.setStorageContents(cloneContents(before));
                player.saveData();
            } catch (final RuntimeException rollbackFailure) {
                persistenceFailure.addSuppressed(rollbackFailure);
                throw new IllegalStateException(
                        "profession craft persistence rollback failed", persistenceFailure);
            }
            return new Result(Status.PERSISTENCE_FAILED, 0);
        }
    }

    private Prepared prepare(final ItemStack[] source,
                             final ProfessionEffectiveCraftPlan plan,
                             final List<ItemStack> rawPerCraftOutputs) {
        if (plan.batches() < 1 || plan.batches() > 64) {
            return new Prepared(Status.INVALID_BATCH, new ItemStack[0]);
        }
        final List<ItemStack> outputs = plan.effectiveOutputs(rawPerCraftOutputs);
        if (outputs.isEmpty()) {
            return new Prepared(Status.INVALID_BATCH, new ItemStack[0]);
        }
        final ItemStack[] working = cloneContents(source == null ? new ItemStack[0] : source);
        for (final Map.Entry<Material, Integer> entry : plan.materialInputs().entrySet()) {
            if (!consumePlain(working, entry.getKey(), entry.getValue())) {
                return new Prepared(Status.MISSING_INGREDIENTS, working);
            }
        }
        for (final Map.Entry<String, Integer> entry : plan.uniqueInputs().entrySet()) {
            if (!consumeUnique(working, entry.getKey(), entry.getValue())) {
                return new Prepared(Status.MISSING_INGREDIENTS, working);
            }
        }
        for (final ItemStack output : outputs) {
            if (!insert(working, output.clone())) {
                return new Prepared(Status.INVENTORY_FULL, working);
            }
        }
        return new Prepared(Status.APPLIED, working);
    }

    private boolean consumePlain(final ItemStack[] contents, final Material material,
                                 final int amount) {
        int available = 0;
        for (final ItemStack item : contents) {
            if (PlainIngredients.matches(item, material, uniqueMaterials)) available += item.getAmount();
        }
        if (available < amount) return false;

        int remaining = amount;
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            final ItemStack item = contents[slot];
            if (!PlainIngredients.matches(item, material, uniqueMaterials)) continue;
            final int take = Math.min(remaining, item.getAmount());
            final int left = item.getAmount() - take;
            contents[slot] = left <= 0 ? null : withAmount(item, left);
            remaining -= take;
        }
        return remaining == 0;
    }

    private boolean consumeUnique(final ItemStack[] contents, final String id, final int amount) {
        int available = 0;
        for (final ItemStack item : contents) {
            if (item != null && id.equals(uniqueMaterials.idOf(item))) available += item.getAmount();
        }
        if (available < amount) return false;

        int remaining = amount;
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            final ItemStack item = contents[slot];
            if (item == null || !id.equals(uniqueMaterials.idOf(item))) continue;
            final int take = Math.min(remaining, item.getAmount());
            final int left = item.getAmount() - take;
            contents[slot] = left <= 0 ? null : withAmount(item, left);
            remaining -= take;
        }
        return remaining == 0;
    }

    private static ItemStack withAmount(final ItemStack source, final int amount) {
        final ItemStack clone = source.clone();
        clone.setAmount(amount);
        return clone;
    }

    private static ItemStack[] cloneContents(final ItemStack[] source) {
        final ItemStack[] result = new ItemStack[source == null ? 0 : source.length];
        if (source == null) return result;
        for (int slot = 0; slot < source.length; slot++) {
            result[slot] = source[slot] == null ? null : source[slot].clone();
        }
        return result;
    }

    private static boolean insert(final ItemStack[] contents, final ItemStack output) {
        int remaining = output.getAmount();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            final ItemStack current = contents[slot];
            if (current == null || !current.isSimilar(output)) continue;
            final int room = Math.max(0,
                    Math.min(current.getMaxStackSize(), output.getMaxStackSize()) - current.getAmount());
            if (room <= 0) continue;
            final int moved = Math.min(room, remaining);
            contents[slot] = withAmount(current, current.getAmount() + moved);
            remaining -= moved;
        }
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            if (contents[slot] != null) continue;
            final int moved = Math.min(output.getMaxStackSize(), remaining);
            contents[slot] = withAmount(output, moved);
            remaining -= moved;
        }
        return remaining == 0;
    }
}
