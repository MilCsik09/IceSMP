package hu.taliann.icesmp.gui;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Inventory holder for the quest log GUI. Maps each filled slot to a quest id
 * and the action a click performs there (accept an available quest / abandon an
 * active one), and remembers the viewed page and tab so navigation can re-open
 * the same view.
 */
public final class QuestLogHolder implements InventoryHolder {

    /** What a click on a mapped slot does. */
    public enum Action { ACCEPT, ABANDON }

    /** Which quests the tab shows. */
    public enum Tab { ACTIVE, AVAILABLE, COMPLETED }

    private final UUID ownerUuid;
    private final Map<Integer, String> slotQuests = new HashMap<>();
    private final Map<Integer, Action> slotActions = new HashMap<>();
    private Inventory inventory;
    private int page;
    private Tab tab = Tab.ACTIVE;

    public QuestLogHolder(final UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public int getPage() {
        return page;
    }

    public void setPage(final int page) {
        this.page = page;
    }

    public Tab getTab() {
        return tab;
    }

    public void setTab(final Tab tab) {
        this.tab = tab;
    }

    public void mapSlot(final int slot, final String questId, final Action action) {
        slotQuests.put(slot, questId);
        slotActions.put(slot, action);
    }

    public String getQuestAt(final int slot) {
        return slotQuests.get(slot);
    }

    public Action getActionAt(final int slot) {
        return slotActions.get(slot);
    }

    public void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory == null ? Bukkit.createInventory(this, 9) : inventory;
    }
}
