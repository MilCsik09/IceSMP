package hu.taliann.icesmp.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jspecify.annotations.NonNull;

import java.util.UUID;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Inventory holder for the character SpecHolder menu, scoped to its owner so a
 * player cannot manipulate another player's open menu.
 */
public final class SpecHolder implements InventoryHolder {

    public enum Mode {
        CLASS_PROGRESS, PROFESSION, MECHANIC_SETUP, DOCTRINE_DETAIL,
        CAPSTONE_DETAIL, ARTIFACT_DETAIL, CONFIRM_CLASS_RESPEC
    }

    public enum ActionType {
        BACK, OPEN_CLASS_PROGRESS, OPEN_PROFESSION, OPEN_COMPANION, OPEN_MECHANIC_SETUP,
        OPEN_DOCTRINE_DETAIL, OPEN_CAPSTONE_DETAIL, OPEN_ARTIFACT_DETAIL,
        CHOOSE_PALADIN_OATH, CHOOSE_PRIEST_LITANY, SELECT_CLASS_SPEC, SELECT_PROFESSION_SPEC,
        SWITCH_LOADOUT, CHOOSE_DOCTRINE, REQUEST_CLASS_RESPEC, CONFIRM_CLASS_RESPEC,
        CANCEL_CLASS_RESPEC, RESPEC_PROFESSION
    }

    public record Action(ActionType type, String value, int level) {
        public Action {
            if (type == null) throw new IllegalArgumentException("Action type is required");
            value = value == null ? "" : value;
        }

        public static Action of(final ActionType type) {
            return new Action(type, "", 0);
        }
    }

    private final UUID ownerUuid;
    private final Mode mode;
    private final Map<Integer, Action> actions = new HashMap<>();
    private Inventory inventory;

    public SpecHolder(final UUID ownerUuid) {
        this(ownerUuid, Mode.CLASS_PROGRESS);
    }

    public SpecHolder(final UUID ownerUuid, final Mode mode) {
        this.ownerUuid = ownerUuid;
        this.mode = mode == null ? Mode.CLASS_PROGRESS : mode;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public Mode getMode() {
        return mode;
    }

    public void bind(final int slot, final Action action) {
        actions.put(slot, action);
    }

    public Action actionAt(final int slot) {
        return actions.get(slot);
    }

    public Map<Integer, Action> actions() {
        return Collections.unmodifiableMap(actions);
    }

    @Override
    public @NonNull Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("SpecHolder inventory has not been set yet");
        }
        return inventory;
    }

    public void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }
}
