package hu.taliann.icesmp.trash;

import hu.taliann.icesmp.session.PlayerStateCleanup;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Version-pinned single-packet bridge for a display-only held-slot copy on Paper 1.21.11.
 * Reflection keeps NMS out of the compile/runtime linkage boundary and fails closed on drift.
 */
public final class TooltipPacketBridge_1_21_11
        implements ArchaeologyTooltipBridge, PlayerStateCleanup {

    private static final int PLAYER_INVENTORY_CONTAINER = 0;
    private static final int OFFHAND_MENU_SLOT = 45;
    private static final long OVERLAY_TICKS = 1_200L;

    private final JavaPlugin plugin;
    private final TrashItemFactory items;
    private final ConcurrentMap<UUID, Overlay> overlays = new ConcurrentHashMap<>();
    private final Access access;

    public TooltipPacketBridge_1_21_11(final JavaPlugin plugin, final TrashItemFactory items) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.items = Objects.requireNonNull(items, "items");
        this.access = Access.probe();
    }

    @Override
    public boolean available() {
        return access != null;
    }

    @Override
    public boolean show(final Player player, final ItemStack canonicalSnapshot,
                        final List<String> observations) {
        return show(player, org.bukkit.inventory.EquipmentSlot.OFF_HAND, canonicalSnapshot, observations);
    }

    @Override
    public boolean show(final Player player, final org.bukkit.inventory.EquipmentSlot inspectedHand,
                        final ItemStack canonicalSnapshot, final List<String> observations) {
        Objects.requireNonNull(player, "player");
        final int menuSlot = inspectedHand == org.bukkit.inventory.EquipmentSlot.OFF_HAND
                ? OFFHAND_MENU_SLOT : 36 + player.getInventory().getHeldItemSlot();
        Objects.requireNonNull(canonicalSnapshot, "canonicalSnapshot");
        if (access == null || observations == null || observations.isEmpty()) return false;
        final ItemStack display = canonicalSnapshot.clone();
        if (items.isKnownItem(display)) items.refreshPresentation(display);
        final ItemMeta meta = display.getItemMeta();
        final List<Component> lore = new ArrayList<>();
        if (meta.lore() != null) lore.addAll(Objects.requireNonNull(meta.lore()));
        lore.add(Component.empty());
        lore.add(Component.text("Régészeti megfigyelések", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        observations.stream().limit(8).forEach(line -> lore.add(
                Component.text("• " + line, NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.lore(lore);
        display.setItemMeta(meta);
        if (!sendDisplay(player, display, menuSlot)) return false;
        final Overlay overlay = new Overlay(menuSlot);
        final Overlay previous = overlays.put(player.getUniqueId(), overlay);
        if (previous != null) previous.cancel();
        try {
            final io.papermc.paper.threadedregions.scheduler.ScheduledTask expiry =
                    player.getScheduler().runDelayed(plugin, ignored -> {
                        if (overlays.remove(player.getUniqueId(), overlay)) sendCanonical(player, overlay.menuSlot);
                    }, () -> overlays.remove(player.getUniqueId(), overlay), OVERLAY_TICKS);
            overlay.setTask(expiry);
            if (expiry == null && overlays.remove(player.getUniqueId(), overlay)) {
                sendCanonical(player, overlay.menuSlot);
                return false;
            }
        } catch (final RuntimeException rejected) {
            if (overlays.remove(player.getUniqueId(), overlay)) sendCanonical(player, overlay.menuSlot);
            return false;
        }
        return true;
    }

    @Override
    public void clear(final Player player) {
        Objects.requireNonNull(player, "player");
        final Overlay overlay = overlays.remove(player.getUniqueId());
        if (overlay != null) {
            overlay.cancel();
            sendCanonical(player, overlay.menuSlot);
        }
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        final Overlay overlay = overlays.remove(playerId);
        if (overlay != null) overlay.cancel();
    }

    @Override
    public void shutdown() {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            final Overlay overlay = overlays.remove(player.getUniqueId());
            if (overlay == null) continue;
            overlay.cancel();
            player.getScheduler().run(plugin, ignored -> sendCanonical(player, overlay.menuSlot), null);
        }
        overlays.clear();
    }

    public boolean hasOverlay(final UUID playerId, final int inventorySlot) {
        final Overlay overlay = overlays.get(playerId);
        return overlay != null && overlay.menuSlot == (inventorySlot == 40 ? OFFHAND_MENU_SLOT : 36 + inventorySlot);
    }

    static boolean projectInventorySlot(final Player player, final int slot, final ItemStack display) {
        if (slot != 40 && (slot < 0 || slot > 8)) return false;
        return sendInventoryProjection(player, display, slot == 40 ? OFFHAND_MENU_SLOT : 36 + slot);
    }

    private void sendCanonical(final Player player, final int menuSlot) {
        final ItemStack current = menuSlot == OFFHAND_MENU_SLOT ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItem(menuSlot - 36);
        sendDisplay(player, current == null ? new ItemStack(org.bukkit.Material.AIR) : current.clone(), menuSlot);
    }

    private boolean sendDisplay(final Player player, final ItemStack display, final int menuSlot) {
        return sendInventoryProjection(player, display, menuSlot);
    }

    static boolean projectHand(final Player player, final org.bukkit.inventory.EquipmentSlot hand,
                               final ItemStack display) {
        return sendInventoryProjection(player, display, hand == org.bukkit.inventory.EquipmentSlot.OFF_HAND
                ? OFFHAND_MENU_SLOT : 36 + player.getInventory().getHeldItemSlot());
    }

    private static final class ProjectionAccess { private static final Access VALUE = Access.probe(); }

    private static boolean sendInventoryProjection(final Player player, final ItemStack display, final int menuSlot) {
        final Access access = ProjectionAccess.VALUE;
        if (access == null || !player.isOnline()) return false;
        try {
            final Object handle = access.getHandle().invoke(player);
            final Object menu = access.inventoryMenu().get(handle);
            final int stateId = ((Number) access.getStateId().invoke(menu)).intValue();
            final Object nmsItem = access.asNmsCopy().invoke(null, display);
            final Object packet = access.packetConstructor().newInstance(
                    PLAYER_INVENTORY_CONTAINER, stateId, menuSlot, nmsItem);
            final Object connection = access.connection().get(handle);
            access.send().invoke(connection, packet);
            return true;
        } catch (final ReflectiveOperationException | RuntimeException rejected) {
            return false;
        }
    }

    private record Access(Method getHandle, Method asNmsCopy, Field inventoryMenu,
                          Method getStateId, Field connection,
                          Constructor<?> packetConstructor, Method send) {

        private static Access probe() {
            try {
                final Class<?> craftPlayer = Class.forName(
                        "org.bukkit.craftbukkit.entity.CraftPlayer");
                final Class<?> serverPlayer = Class.forName(
                        "net.minecraft.server.level.ServerPlayer");
                final Class<?> craftItem = Class.forName(
                        "org.bukkit.craftbukkit.inventory.CraftItemStack");
                final Class<?> nmsItem = Class.forName("net.minecraft.world.item.ItemStack");
                final Class<?> menuType = Class.forName(
                        "net.minecraft.world.inventory.AbstractContainerMenu");
                final Class<?> packetType = Class.forName(
                        "net.minecraft.network.protocol.Packet");
                final Class<?> setSlot = Class.forName(
                        "net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket");
                final Class<?> connectionType = Class.forName(
                        "net.minecraft.server.network.ServerGamePacketListenerImpl");
                final Method send = java.util.Arrays.stream(connectionType.getMethods())
                        .filter(method -> method.getName().equals("send")
                                && method.getParameterCount() == 1
                                && packetType.isAssignableFrom(method.getParameterTypes()[0]))
                        .findFirst().orElseThrow();
                return new Access(craftPlayer.getMethod("getHandle"),
                        craftItem.getMethod("asNMSCopy", ItemStack.class),
                        serverPlayer.getField("inventoryMenu"), menuType.getMethod("getStateId"),
                        serverPlayer.getField("connection"),
                        setSlot.getConstructor(int.class, int.class, int.class, nmsItem), send);
            } catch (final ReflectiveOperationException | RuntimeException unavailable) {
                return null;
            }
        }
    }

    private static final class Overlay {
        private io.papermc.paper.threadedregions.scheduler.ScheduledTask task;
        private boolean cancelled;

        private final int menuSlot;

        private Overlay(final int menuSlot) { this.menuSlot = menuSlot; }

        private synchronized void setTask(
                final io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduled) {
            if (cancelled && scheduled != null) scheduled.cancel();
            else task = scheduled;
        }

        private synchronized void cancel() {
            cancelled = true;
            final io.papermc.paper.threadedregions.scheduler.ScheduledTask current = task;
            if (current != null) current.cancel();
        }
    }
}
