package hu.taliann.icesmp.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Owner-scoped, read-only crate list/preview holder. */
public final class CrateBrowserHolder implements InventoryHolder {

    public enum View {
        LIST,
        PREVIEW
    }

    private final UUID ownerId;
    private final View view;
    private final String crateId;
    private final Map<Integer, String> actions = new HashMap<>();
    private Inventory inventory;

    public CrateBrowserHolder(final UUID ownerId, final View view, final String crateId) {
        this.ownerId = ownerId;
        this.view = view;
        this.crateId = crateId;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public View view() {
        return view;
    }

    public String crateId() {
        return crateId;
    }

    public void bind(final int slot, final String action) {
        actions.put(slot, action);
    }

    public String actionAt(final int slot) {
        return actions.get(slot);
    }

    public void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("CrateBrowserHolder inventory is not initialized");
        }
        return inventory;
    }
}
