package hu.taliann.icesmp.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Inventory holder for the command-menu hub and its sub-menus (/menu). Each menu
 * is identified by its {@link Menu} type, scoped to an owner, and carries a
 * slot → action map so the listener can stay generic: an action is either
 * "MENU:&lt;type&gt;" (open a sub-menu), "RUN:&lt;command&gt;" (run a command then
 * refresh), "OPEN:&lt;command&gt;" (run a command that opens its own GUI) or "CLOSE".
 */
public final class CommandMenuHolder implements InventoryHolder {

    public enum Menu { MAIN, FACTION, BANK, EVENTS, RELIC, SOULS, LEADERBOARD, ACHIEVEMENTS, PARTY, CLAIM, BOUNTY, ADMIN }

    private final Menu menu;
    private final UUID ownerUuid;
    private final Map<Integer, String> actions = new HashMap<>();
    private Inventory inventory;

    public CommandMenuHolder(final Menu menu, final UUID ownerUuid) {
        this.menu = menu;
        this.ownerUuid = ownerUuid;
    }

    public Menu getMenu() {
        return menu;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public Map<Integer, String> getActions() {
        return actions;
    }

    public void bind(final int slot, final String action) {
        actions.put(slot, action);
    }

    @Override
    public @NonNull Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("CommandMenuHolder inventory has not been set yet");
        }
        return inventory;
    }

    public void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }
}
