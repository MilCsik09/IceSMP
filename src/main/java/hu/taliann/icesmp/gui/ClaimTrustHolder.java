package hu.taliann.icesmp.gui;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Holder for {@link ClaimTrustGUI}: carries the owning player's UUID plus two
 * slot maps — trusted-player heads (click = revoke) and nearby-player heads
 * (click = grant) — so the listener never has to re-derive who a slot belongs to.
 */
public final class ClaimTrustHolder implements InventoryHolder {

    private final UUID ownerUuid;
    private final Map<Integer, UUID> trustedSlots = new HashMap<>();
    private final Map<Integer, UUID> nearbySlots = new HashMap<>();
    private Inventory inventory;

    public ClaimTrustHolder(final UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void mapTrusted(final int slot, final UUID targetId) {
        trustedSlots.put(slot, targetId);
    }

    public UUID getTrustedAt(final int slot) {
        return trustedSlots.get(slot);
    }

    public void mapNearby(final int slot, final UUID targetId) {
        nearbySlots.put(slot, targetId);
    }

    public UUID getNearbyAt(final int slot) {
        return nearbySlots.get(slot);
    }

    public void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory == null ? Bukkit.createInventory(this, 9) : inventory;
    }
}
