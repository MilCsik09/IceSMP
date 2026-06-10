package hu.taliann.icesmp.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jspecify.annotations.NonNull;

public final class ProfileHolder implements InventoryHolder {

    private Inventory inventory;

    @Override
    public @NonNull Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("ProfileHolder inventory has not been set yet");
        }
        return inventory;
    }

    public void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }
}

