package hu.taliann.icesmp.gui;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MarketHolder implements InventoryHolder {

    private final UUID ownerUuid;
    private final Map<Integer, UUID> slotListings = new HashMap<>();
    private Inventory inventory;
    private int page;

    public MarketHolder(final UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public int getPage() {
        return page;
    }

    public void setPage(final int page) {
        this.page = page;
    }

    public void mapSlot(final int slot, final UUID listingId) {
        slotListings.put(slot, listingId);
    }

    public UUID getListingAt(final int slot) {
        return slotListings.get(slot);
    }

    public void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory == null ? Bukkit.createInventory(this, 9) : inventory;
    }
}
