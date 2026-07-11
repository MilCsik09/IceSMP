package hu.taliann.icesmp.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Inventory holder for the profession recipe-book GUI ({@code /profession recipes}). Maps each
 * filled slot to a recipe id, and remembers the viewed page so navigation can re-open the same view.
 */
public final class ProfessionRecipeHolder implements InventoryHolder {

    private final UUID ownerId;
    private final Map<Integer, String> slotRecipes = new HashMap<>();
    private int page;
    private Inventory inventory;

    public ProfessionRecipeHolder(final UUID ownerId, final int page) {
        this.ownerId = ownerId;
        this.page = page;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public int getPage() {
        return page;
    }

    public void setPage(final int page) {
        this.page = page;
    }

    public void map(final int slot, final String recipeId) {
        slotRecipes.put(slot, recipeId);
    }

    public String recipeAt(final int slot) {
        return slotRecipes.get(slot);
    }

    public void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
