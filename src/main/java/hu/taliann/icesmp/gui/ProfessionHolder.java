package hu.taliann.icesmp.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

/**
 * Inventory holder for the character ProfessionHolder menu, scoped to its owner so a
 * player cannot manipulate another player's open menu.
 */
public final class ProfessionHolder implements InventoryHolder {

    private final UUID ownerUuid;
    private Inventory inventory;

    public ProfessionHolder(final UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    @Override
    public @NonNull Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("ProfessionHolder inventory has not been set yet");
        }
        return inventory;
    }

    public void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }
}
