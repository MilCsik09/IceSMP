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
 * Version-pinned single-packet bridge for a display-only offhand copy on Paper 1.21.11.
 * Reflection keeps NMS out of the compile/runtime linkage boundary and fails closed on drift.
 */
public final class TooltipPacketBridge_1_21_11
        implements ArchaeologyTooltipBridge, PlayerStateCleanup {

    private static final int PLAYER_INVENTORY_CONTAINER = 0;
    private static final int OFFHAND_MENU_SLOT = 45;
    private static final long OVERLAY_TICKS = 1_200L;

    private final JavaPlugin plugin;
    private final TrashItemFactory items;
    private final ConcurrentMap<UUID, UUID> overlays = new ConcurrentHashMap<>();
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
        Objects.requireNonNull(player, "player");
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
        if (!sendDisplay(player, display)) return false;
        final UUID token = UUID.randomUUID();
        overlays.put(player.getUniqueId(), token);
        try {
            final io.papermc.paper.threadedregions.scheduler.ScheduledTask expiry =
                    player.getScheduler().runDelayed(plugin, ignored -> {
                        if (overlays.remove(player.getUniqueId(), token)) sendCanonical(player);
                    }, () -> overlays.remove(player.getUniqueId(), token), OVERLAY_TICKS);
            if (expiry == null && overlays.remove(player.getUniqueId(), token)) {
                sendCanonical(player);
                return false;
            }
        } catch (final RuntimeException rejected) {
            if (overlays.remove(player.getUniqueId(), token)) sendCanonical(player);
            return false;
        }
        return true;
    }

    @Override
    public void clear(final Player player) {
        Objects.requireNonNull(player, "player");
        if (overlays.remove(player.getUniqueId()) != null) sendCanonical(player);
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        overlays.remove(playerId);
    }

    @Override
    public void shutdown() {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            if (overlays.remove(player.getUniqueId()) == null) continue;
            player.getScheduler().run(plugin, ignored -> sendCanonical(player), null);
        }
        overlays.clear();
    }

    private void sendCanonical(final Player player) {
        sendDisplay(player, player.getInventory().getItemInOffHand().clone());
    }

    private boolean sendDisplay(final Player player, final ItemStack display) {
        if (access == null || !player.isOnline()) return false;
        try {
            final Object handle = access.getHandle().invoke(player);
            final Object menu = access.inventoryMenu().get(handle);
            final int stateId = ((Number) access.getStateId().invoke(menu)).intValue();
            final Object nmsItem = access.asNmsCopy().invoke(null, display);
            final Object packet = access.packetConstructor().newInstance(
                    PLAYER_INVENTORY_CONTAINER, stateId, OFFHAND_MENU_SLOT, nmsItem);
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
}
