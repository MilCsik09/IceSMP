package hu.taliann.icesmp.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Holder az admin config-menühöz (K: "ingame config menü"). A szokásos GUI-minta:
 * a holder hordozza az owner-UUID-t, az aktuális lapot (null = főmenü, egyébként
 * kategória-id) és a slot→akció kötéseket. Akciók: {@code CAT:<id>},
 * {@code TOGGLE:<kulcs>}, {@code NUM:<kulcs>}, {@code CYCLE:<kulcs>},
 * {@code BACK}, {@code CLOSE}.
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

    /** Az aktuális kategória-id, vagy null a főmenün. */
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
