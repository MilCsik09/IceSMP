package hu.taliann.icesmp.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Player-owned item provenance stored on the stack itself.
 *
 * <p>The old implementation marked only the ground entity. That marker disappeared on every
 * inventory/container/craft/place transition and the death listener guessed ownership by scanning
 * every nearby item one tick later. The scan contaminated unrelated drops. Stack PDC is copied into
 * the actual death/drop entity by Bukkit, so ownership is now attached directly to the exact drops.
 *
 * <p>Collection progress no longer depends on pickup events; this marker remains as a fail-closed
 * guard for any future pickup consumer and for diagnostics.
 */
public final class ItemProvenance {

    private static final NamespacedKey PLAYER_DROPPED =
            new NamespacedKey("icesmp", "player_dropped");

    private ItemProvenance() {
    }

    /** Marks both the entity and its stack as player-derived. */
    public static void markPlayerDropped(final Item item) {
        if (item == null) {
            return;
        }
        item.getPersistentDataContainer().set(
                PLAYER_DROPPED, PersistentDataType.BYTE, (byte) 1);
        final ItemStack stack = item.getItemStack();
        markPlayerDropped(stack);
        item.setItemStack(stack);
    }

    /** Marks the exact stack before Bukkit creates a death-drop entity from it. */
    public static void markPlayerDropped(final ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        final var meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(
                PLAYER_DROPPED, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
    }

    public static boolean isPlayerDropped(final Item item) {
        return item != null && (item.getPersistentDataContainer().getOrDefault(
                PLAYER_DROPPED, PersistentDataType.BYTE, (byte) 0) == (byte) 1
                || isPlayerDropped(item.getItemStack()));
    }

    public static boolean isPlayerDropped(final ItemStack stack) {
        return stack != null && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().getOrDefault(
                PLAYER_DROPPED, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
    }
}
