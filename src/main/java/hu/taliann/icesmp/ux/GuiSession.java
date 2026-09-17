package hu.taliann.icesmp.ux;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Per-open inventory state and component ownership boundary. */
public final class GuiSession implements InventoryHolder {
    private final UUID playerId;
    private final String guiId;
    private final Map<String, Object> state = new LinkedHashMap<>();
    private final Map<Integer, GuiComponent> components = new LinkedHashMap<>();
    private Inventory inventory;
    private int currentPage;

    public GuiSession(final UUID playerId, final String guiId) {
        this.playerId = playerId;
        this.guiId = guiId;
    }

    public UUID playerId() { return playerId; }
    public String guiId() { return guiId; }
    public Map<String, Object> state() { return Collections.unmodifiableMap(state); }
    public int currentPage() { return currentPage; }
    public void currentPage(final int page) { currentPage = Math.max(0, page); }
    public Inventory inventory() { return inventory; }
    public GuiComponent component(final int slot) { return components.get(slot); }
    public void bind(final Inventory inventory, final Iterable<GuiComponent> entries) {
        this.inventory = inventory;
        components.clear();
        if (entries != null) for (final GuiComponent component : entries)
            if (component != null && component.slot() >= 0 && component.slot() < inventory.getSize())
                components.put(component.slot(), component);
    }

    public void rerender() {
        if (inventory == null) return;
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, null);
        for (GuiComponent component : components.values()) {
            if (component.visible(this)) inventory.setItem(component.slot(), component.render(this));
        }
    }

    public void close() {
        components.clear();
        state.clear();
        inventory = null;
    }

    @Override
    public Inventory getInventory() {
        return inventory == null ? Bukkit.createInventory(this, 9) : inventory;
    }

    public Player player() {
        return Bukkit.getPlayer(playerId);
    }
}
