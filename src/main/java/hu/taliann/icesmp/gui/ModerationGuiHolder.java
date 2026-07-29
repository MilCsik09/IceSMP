package hu.taliann.icesmp.gui;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/** Holder for the native moderation admin menu. */
public final class ModerationGuiHolder implements InventoryHolder {
    public enum Page { PLAYERS, PLAYER }

    private final Page page;
    private final UUID targetId;
    private final String targetName;
    private Inventory inventory;

    public ModerationGuiHolder(final Page page, final UUID targetId, final String targetName) {
        this.page = page;
        this.targetId = targetId;
        this.targetName = targetName;
    }

    public Page page() { return page; }
    public UUID targetId() { return targetId; }
    public String targetName() { return targetName; }
    public void setInventory(final Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() {
        return inventory == null ? Bukkit.createInventory(this, 9) : inventory;
    }
}
