package hu.taliann.icesmp.gui;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Inventory holder for a faction shop GUI: remembers the owning player, the
 * shop NPC name (so a refresh re-reads the same shop), and maps each filled
 * slot to its shop item index.
 */
public final class ShopHolder implements InventoryHolder {

    private final UUID ownerUuid;
    private final String npcName;
    private final Map<Integer, Integer> slotItems = new HashMap<>();
    private Inventory inventory;

    public ShopHolder(final UUID ownerUuid, final String npcName) {
        this.ownerUuid = ownerUuid;
        this.npcName = npcName;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getNpcName() {
        return npcName;
    }

    public void mapSlot(final int slot, final int itemIndex) {
        slotItems.put(slot, itemIndex);
    }

    /** The shop item index at a slot, or -1 if the slot is not a purchasable item. */
    public int getItemIndexAt(final int slot) {
        return slotItems.getOrDefault(slot, -1);
    }

    public void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory == null ? Bukkit.createInventory(this, 9) : inventory;
    }
}
