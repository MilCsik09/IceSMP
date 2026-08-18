package hu.taliann.icesmp.professions;

import hu.taliann.icesmp.items.UniqueMaterialFactory;
import hu.taliann.icesmp.managers.ProfessionRecipeCatalog;
import hu.taliann.icesmp.utils.PlainIngredients;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Owner-thread inventory transaction for profession processing/crafting.
 * All input removal and output placement is planned against a clone first; full inventory or
 * missing input leaves the live inventory untouched. No fallback world-drop is used.
 */
public final class ProfessionCraftTransaction {

    public enum Status { APPLIED, MISSING_INGREDIENTS, INVENTORY_FULL, INVALID_BATCH }
    public record Result(Status status, int batches) {
        public boolean applied() { return status == Status.APPLIED; }
    }

    private final UniqueMaterialFactory uniqueMaterials;

    public ProfessionCraftTransaction(final UniqueMaterialFactory uniqueMaterials) {
        this.uniqueMaterials = java.util.Objects.requireNonNull(uniqueMaterials, "uniqueMaterials");
    }

    public Result apply(final Player player, final ProfessionRecipeCatalog.Recipe recipe,
                        final int batches, final List<ItemStack> outputs) {
        java.util.Objects.requireNonNull(player, "player");
        java.util.Objects.requireNonNull(recipe, "recipe");
        if (batches < 1 || batches > 64 || outputs == null || outputs.isEmpty()) {
            return new Result(Status.INVALID_BATCH, 0);
        }
        final PlayerInventory inventory = player.getInventory();
        final ItemStack[] live = inventory.getStorageContents();
        final ItemStack[] working = new ItemStack[live.length];
        for (int i = 0; i < live.length; i++) {
            working[i] = live[i] == null ? null : live[i].clone();
        }
        for (final Map.Entry<Material, Integer> entry : recipe.ingredients().entrySet()) {
            final long requested = (long) entry.getValue() * batches;
            if (requested > Integer.MAX_VALUE || !consumePlain(working, entry.getKey(), (int) requested)) {
                return new Result(Status.MISSING_INGREDIENTS, 0);
            }
        }
        for (final Map.Entry<String, Integer> entry : recipe.uniqueIngredients().entrySet()) {
            final long requested = (long) entry.getValue() * batches;
            if (requested > Integer.MAX_VALUE || !consumeUnique(working, entry.getKey(), (int) requested)) {
                return new Result(Status.MISSING_INGREDIENTS, 0);
            }
        }
        for (final ItemStack raw : outputs) {
            if (raw == null || raw.getType().isAir() || raw.getAmount() <= 0) {
                return new Result(Status.INVALID_BATCH, 0);
            }
            final ItemStack output = raw.clone();
            if (!insert(working, output)) {
                return new Result(Status.INVENTORY_FULL, 0);
            }
        }
        inventory.setStorageContents(working);
        return new Result(Status.APPLIED, batches);
    }

    private boolean consumePlain(final ItemStack[] contents, final Material material, final int amount) {
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

    private static boolean insert(final ItemStack[] contents, final ItemStack output) {
        int remaining = output.getAmount();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            final ItemStack current = contents[slot];
            if (current == null || !current.isSimilar(output)) continue;
            final int room = Math.max(0, Math.min(current.getMaxStackSize(), output.getMaxStackSize()) - current.getAmount());
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
