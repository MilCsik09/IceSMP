package hu.taliann.icesmp.dev.weaver.gui;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jspecify.annotations.NonNull;

/** The native inventory handle is owner-thread-local and never enters persisted or execution data. */
public final class WorldWeaverHolder implements InventoryHolder {
    private final UUID sessionId;
    private final long viewRevision;
    private final WeaverViewKind viewKind;
    private Inventory inventory;
    public WorldWeaverHolder(final UUID sessionId, final long revision, final WeaverViewKind kind) {
        this.sessionId = java.util.Objects.requireNonNull(sessionId); viewRevision = revision; viewKind = java.util.Objects.requireNonNull(kind);
        if (revision < 1) throw new IllegalArgumentException("Invalid view revision");
    }
    public UUID sessionId() { return sessionId; }
    public long viewRevision() { return viewRevision; }
    public WeaverViewKind viewKind() { return viewKind; }
    public void attach(final Inventory inventory) {
        if (this.inventory != null) throw new IllegalStateException("Inventory already attached");
        this.inventory = java.util.Objects.requireNonNull(inventory);
    }
    @Override public @NonNull Inventory getInventory() {
        if (inventory == null) throw new IllegalStateException("Inventory not attached");
        return inventory;
    }
}
