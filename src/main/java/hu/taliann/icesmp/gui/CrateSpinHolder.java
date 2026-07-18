package hu.taliann.icesmp.gui;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Holder for the crate-spin reveal GUI. The animation itself lives in
 * {@link CrateSpinGUI} as a chain of {@code runDelayed} calls on the opening player's
 * own entity scheduler; this holder just carries the owner-UUID (for the click-guard in
 * {@link hu.taliann.icesmp.listeners.CrateSpinGUIListener}) and the volatile
 * {@code cancelled} flag the listener flips on close/logout so the next chained step
 * knows to stop touching the (possibly already-gone) inventory.
 */
public final class CrateSpinHolder implements InventoryHolder {

    private final UUID ownerUuid;
    private Inventory inventory;
    private volatile boolean cancelled;

    public CrateSpinHolder(final UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    /** Stops the spin chain — checked by {@link CrateSpinGUI} before every step. */
    public void cancel() {
        this.cancelled = true;
    }

    @Override
    public Inventory getInventory() {
        return inventory == null ? Bukkit.createInventory(this, 27) : inventory;
    }
}
