package hu.taliann.icesmp.gui;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Holder for the spellbook GUI: identifies the owning player, the current page,
 * and which slot maps to which (unlocked, selectable) spell id.
 */
public final class SpellbookHolder implements InventoryHolder {

    private final UUID ownerUuid;
    private final Map<Integer, String> slotSpells = new HashMap<>();
    private Inventory inventory;
    private int page;

    public SpellbookHolder(final UUID ownerUuid) {
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

    public void mapSlot(final int slot, final String spellId) {
        slotSpells.put(slot, spellId);
    }

    public String getSpellAt(final int slot) {
        return slotSpells.get(slot);
    }

    public void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory == null ? Bukkit.createInventory(this, 9) : inventory;
    }
}
