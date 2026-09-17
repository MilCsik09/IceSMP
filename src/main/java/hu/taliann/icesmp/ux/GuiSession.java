package hu.taliann.icesmp.ux;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Per-open inventory state and component ownership boundary. */
public final class GuiSession implements InventoryHolder {
    private final UUID playerId;
    private final String guiId;
    private final Map<String, Object> state = new LinkedHashMap<>();
    private final Map<Integer, GuiComponent> components = new LinkedHashMap<>();
    private Inventory inventory;
    private int currentPage;
    private boolean closed;

    public GuiSession(final UUID playerId, final String guiId) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        if (guiId == null || guiId.isBlank()) throw new IllegalArgumentException("gui id required");
        this.guiId = guiId;
    }

    public UUID playerId() { return playerId; }
    public String guiId() { return guiId; }
    public Map<String, Object> state() { return Collections.unmodifiableMap(state); }
    public int currentPage() { return currentPage; }
    public void currentPage(final int page) {
        ensureOpen();
        currentPage = Math.max(0, page);
    }
    public Inventory inventory() { return inventory; }
    public GuiComponent component(final int slot) { return components.get(slot); }
    public boolean closed() { return closed; }

    public void putState(final String key, final Object value) {
        ensureOpen();
        if (key == null || key.isBlank()) throw new IllegalArgumentException("state key required");
        if (value == null) state.remove(key); else state.put(key, value);
    }

    public Object removeState(final String key) {
        ensureOpen();
        return key == null ? null : state.remove(key);
    }

    public <T> T state(final String key, final Class<T> type) {
        Objects.requireNonNull(type, "type");
        final Object value = state.get(key);
        return type.isInstance(value) ? type.cast(value) : null;
    }

    public void bind(final Inventory inventory, final Iterable<GuiComponent> entries) {
        ensureOpen();
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        components.clear();
        if (entries != null) for (final GuiComponent component : entries)
            if (component != null && component.slot() >= 0 && component.slot() < inventory.getSize())
                components.put(component.slot(), component);
    }

    public void rerender() {
        ensureOpen();
        if (inventory == null) return;
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, null);
        for (final GuiComponent component : components.values()) {
            if (component.visible(this)) inventory.setItem(component.slot(), component.render(this));
        }
    }

    public void close() {
        if (closed) return;
        closed = true;
        components.clear();
        state.clear();
        inventory = null;
    }

    @Override
    public Inventory getInventory() {
        if (inventory == null) throw new IllegalStateException("GUI session is not bound or already closed");
        return inventory;
    }

    public Player player() {
        return Bukkit.getPlayer(playerId);
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("GUI session is closed");
    }
}
