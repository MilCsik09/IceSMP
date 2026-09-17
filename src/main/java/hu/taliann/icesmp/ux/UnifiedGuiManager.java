package hu.taliann.icesmp.ux;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Common GUI lifecycle and inventory-security boundary. All slots in a managed top inventory are
 * server-owned; every click/drag route is cancelled before the component is invoked.
 */
public final class UnifiedGuiManager implements Listener {

    private final JavaPlugin plugin;
    private final ConcurrentMap<UUID, GuiSession> sessions = new ConcurrentHashMap<>();

    public UnifiedGuiManager(final JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public GuiSession open(final Player player, final String guiId, final Component title,
                           final int rows, final Collection<GuiComponent> components) {
        if (player == null || rows < 1 || rows > 6) throw new IllegalArgumentException("invalid GUI");
        clear(player);
        final GuiSession session = new GuiSession(player.getUniqueId(), guiId);
        final Inventory inventory = Bukkit.createInventory(session, rows * 9, title);
        session.bind(inventory, components);
        session.rerender();
        sessions.put(player.getUniqueId(), session);
        player.openInventory(inventory);
        return session;
    }

    public GuiSession active(final UUID playerId) {
        return sessions.get(playerId);
    }

    public void rerender(final Player player) {
        if (player == null) return;
        final GuiSession session = sessions.get(player.getUniqueId());
        if (session != null) session.rerender();
    }

    public void clear(final Player player) {
        if (player == null) return;
        final GuiSession session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        if (player.getOpenInventory().getTopInventory().getHolder() == session) player.closeInventory();
        session.close();
    }

    public void clearPlayerState(final UUID playerId) {
        final GuiSession session = sessions.remove(playerId);
        if (session != null) session.close();
    }

    public void shutdown() {
        for (final Player player : List.copyOf(Bukkit.getOnlinePlayers())) clear(player);
        sessions.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(final InventoryClickEvent event) {
        final Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof GuiSession session)) return;
        event.setCancelled(true);
        if (!session.playerId().equals(event.getWhoClicked().getUniqueId())) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= top.getSize()) return;
        final GuiComponent component = session.component(event.getRawSlot());
        if (component != null && component.visible(session) && component.enabled(session))
            component.onInteraction(session, event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(final InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GuiSession)) return;
        for (final int slot : event.getRawSlots()) {
            if (slot < event.getView().getTopInventory().getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(final InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiSession session)) return;
        sessions.remove(session.playerId(), session);
        session.close();
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        clearPlayerState(event.getPlayer().getUniqueId());
    }
}
