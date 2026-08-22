package hu.taliann.icesmp.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class ItemForgeHolder implements InventoryHolder {
    private final UUID ownerId;
    private final String lockedStat;
    private final boolean amplifier;
    private final boolean stability;
    private final int targetSlot;
    private final int runeSocket;
    private Inventory inventory;

    public ItemForgeHolder(final UUID ownerId, final String lockedStat,
                           final boolean amplifier, final boolean stability,
                           final int targetSlot, final int runeSocket) {
        this.ownerId = ownerId;
        this.lockedStat = lockedStat == null ? "" : lockedStat;
        this.amplifier = amplifier;
        this.stability = stability;
        this.targetSlot = targetSlot;
        this.runeSocket = runeSocket;
    }

    public UUID ownerId() { return ownerId; }
    public String lockedStat() { return lockedStat; }
    public boolean amplifier() { return amplifier; }
    public boolean stability() { return stability; }
    public int targetSlot() { return targetSlot; }
    public int runeSocket() { return runeSocket; }
    public void inventory(final Inventory inventory) { this.inventory = inventory; }
    @Override public Inventory getInventory() { return inventory; }
}
