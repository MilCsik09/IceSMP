package hu.taliann.icesmp.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * InventoryHolder for the admin quest-builder GUI. Carries the owner, the quest
 * being edited (null while the objective picker is choosing a type for a brand
 * new quest), the current view and the slot→action bindings the listener
 * dispatches on. Actions are plain strings: FIELD:&lt;mező&gt;, TOGGLE:&lt;mező&gt;,
 * MODE, NEWOBJ, TYPE:&lt;feladat&gt;, DELETE, CLOSE.
 */
public final class QuestBuilderHolder implements InventoryHolder {

    public enum View { EDITOR, TYPE_PICKER }

    private final UUID ownerId;
    private final String questId;
    private final String newQuestId;
    private final View view;
    private final Map<Integer, String> actions = new HashMap<>();
    private boolean deleteArmed;
    private Inventory inventory;

    /**
     * @param ownerId the viewing admin
     * @param questId the edited custom quest id (null in the new-quest picker)
     * @param newQuestId the id the new quest will be created under (picker only)
     * @param view which screen this inventory shows
     */
    public QuestBuilderHolder(final UUID ownerId, final String questId, final String newQuestId, final View view) {
        this.ownerId = ownerId;
        this.questId = questId;
        this.newQuestId = newQuestId;
        this.view = view;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getQuestId() {
        return questId;
    }

    public String getNewQuestId() {
        return newQuestId;
    }

    public View getView() {
        return view;
    }

    public void putAction(final int slot, final String action) {
        actions.put(slot, action);
    }

    public String getAction(final int slot) {
        return actions.get(slot);
    }

    public boolean isDeleteArmed() {
        return deleteArmed;
    }

    public void setDeleteArmed(final boolean deleteArmed) {
        this.deleteArmed = deleteArmed;
    }

    public void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
