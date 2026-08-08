package hu.taliann.icesmp.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Holder az admin config-menühöz. A holder hordozza az owner-UUID-t, az aktuális
 * nézet/kategória azonosítóját és a slot→akció kötéseket. Az akciók a régi
 * {@code CAT:/TOGGLE:/NUM:/CYCLE:} készlet mellett az üzemeltetési, advanced,
 * crate- és strukturált reward-editor navigációs azonosítóit is fogadhatják.
 */
public final class ConfigMenuHolder implements InventoryHolder {

    private final UUID ownerId;
    private final String category;
    private final Map<Integer, String> actions = new HashMap<>();
    private Inventory inventory;

    public ConfigMenuHolder(final UUID ownerId, final String category) {
        this.ownerId = ownerId;
        this.category = category;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    /** Az aktuális nézet/kategória-id, vagy null a főmenün. */
    public String getCategory() {
        return category;
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
        return inventory;
    }
}
