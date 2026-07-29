package hu.taliann.icesmp.gui;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Objects;
import java.util.UUID;

/** Holder for one live online inventory inspection session. */
public final class InvseeHolder implements InventoryHolder {

    public enum View { MAIN, ENDER }
    public enum Mode { READ_ONLY, EDIT }

    private final UUID sessionId;
    private final UUID targetId;
    private final String targetName;
    private final View view;
    private final Mode mode;
    private Inventory inventory;

    public InvseeHolder(final UUID sessionId, final UUID targetId, final String targetName,
                        final View view, final Mode mode) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.targetId = Objects.requireNonNull(targetId, "targetId");
        this.targetName = Objects.requireNonNull(targetName, "targetName");
        this.view = Objects.requireNonNull(view, "view");
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    public UUID sessionId() { return sessionId; }
    public UUID targetId() { return targetId; }
    public String targetName() { return targetName; }
    public View view() { return view; }
    public Mode mode() { return mode; }

    public void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory == null ? Bukkit.createInventory(this, 9) : inventory;
    }
}
