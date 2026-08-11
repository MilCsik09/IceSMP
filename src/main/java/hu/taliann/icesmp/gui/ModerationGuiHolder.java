package hu.taliann.icesmp.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Holder for the native moderation admin menu. */
public final class ModerationGuiHolder implements InventoryHolder {
    public enum Page { PLAYERS, PLAYER }

    public enum Action { PREVIOUS_PAGE, NEXT_PAGE, BACK, CLOSE }

    public record PlayerTarget(UUID uniqueId, String name) {
        public PlayerTarget {
            Objects.requireNonNull(uniqueId, "uniqueId");
            Objects.requireNonNull(name, "name");
        }
    }

    private final UUID ownerId;
    private final Page page;
    private final UUID targetId;
    private final String targetName;
    private final int listPage;
    private final Map<Integer, Action> actions = new HashMap<>();
    private final Map<Integer, PlayerTarget> players = new HashMap<>();
    private Inventory inventory;

    public ModerationGuiHolder(final UUID ownerId, final Page page, final UUID targetId,
                               final String targetName, final int listPage) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.page = Objects.requireNonNull(page, "page");
        this.targetId = targetId;
        this.targetName = targetName;
        this.listPage = Math.max(0, listPage);
    }

    public UUID ownerId() { return ownerId; }
    public Page page() { return page; }
    public UUID targetId() { return targetId; }
    public String targetName() { return targetName; }
    public int listPage() { return listPage; }

    public void bindAction(final int slot, final Action action) {
        actions.put(slot, Objects.requireNonNull(action, "action"));
    }

    public Action actionAt(final int slot) {
        return actions.get(slot);
    }

    public void bindPlayer(final int slot, final UUID uniqueId, final String name) {
        players.put(slot, new PlayerTarget(uniqueId, name));
    }

    public PlayerTarget playerAt(final int slot) {
        return players.get(slot);
    }

    public void setInventory(final Inventory inventory) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
    }

    @Override
    public Inventory getInventory() {
        return Objects.requireNonNull(inventory, "inventory has not been initialized");
    }
}
