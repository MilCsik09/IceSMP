package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.managers.BestiaryManager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

/**
 * B21 — a bestiárium holderje. A nézet-állapotot (főoldal vagy kategória+oldal) a holder
 * hordozza, a kattintás-értelmezés a BestiaryListener dolga; a GUI végig csak olvasható.
 */
public final class BestiaryHolder implements InventoryHolder {

    private final UUID ownerUuid;
    private final BestiaryManager.Category category;
    private final int page;
    private Inventory inventory;

    /** Főoldal-holder. */
    public BestiaryHolder(final UUID ownerUuid) {
        this(ownerUuid, null, 0);
    }

    /** Kategória-lapozó holder. */
    public BestiaryHolder(final UUID ownerUuid, final BestiaryManager.Category category,
                          final int page) {
        this.ownerUuid = ownerUuid;
        this.category = category;
        this.page = Math.max(0, page);
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    /** {@code null} = főoldal. */
    public BestiaryManager.Category category() {
        return category;
    }

    public int page() {
        return page;
    }

    public void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NonNull Inventory getInventory() {
        return inventory;
    }
}
