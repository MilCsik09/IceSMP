package hu.taliann.icesmp.gui;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Holder for {@link PetGUI}: carries the owner's UUID and the slot → action map
 * (including the release-confirmation generation) so the listener only dispatches and never
 * re-derives what a slot does.
 */
public final class PetGUIHolder implements InventoryHolder {

    public enum Mode { ROSTER, RELEASE_CONFIRM }

    private final UUID ownerUuid;
    private final Mode mode;
    private final UUID companionId;
    private final Map<Integer, String> actions = new HashMap<>();
    private Inventory inventory;

    public PetGUIHolder(final UUID ownerUuid) {
        this(ownerUuid, Mode.ROSTER, null);
    }

    public PetGUIHolder(final UUID ownerUuid, final Mode mode, final UUID companionId) {
        this.ownerUuid = ownerUuid;
        this.mode = mode == null ? Mode.ROSTER : mode;
        this.companionId = companionId;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public Mode getMode() { return mode; }

    public UUID getCompanionId() { return companionId; }

    public void mapAction(final int slot, final String action) {
        actions.put(slot, action);
    }

    public String getActionAt(final int slot) {
        return actions.get(slot);
    }

    public void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory == null ? Bukkit.createInventory(this, 54) : inventory;
    }
}
