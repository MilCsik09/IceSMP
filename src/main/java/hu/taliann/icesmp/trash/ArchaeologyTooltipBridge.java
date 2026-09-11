package hu.taliann.icesmp.trash;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Narrow player-only display-copy seam; canonical inventory state is never accepted for mutation. */
public interface ArchaeologyTooltipBridge {

    boolean available();

    boolean show(Player player, ItemStack canonicalSnapshot, List<String> observations);

    default boolean show(Player player, org.bukkit.inventory.EquipmentSlot inspectedHand,
                         ItemStack canonicalSnapshot, List<String> observations) {
        return inspectedHand == org.bukkit.inventory.EquipmentSlot.OFF_HAND
                && show(player, canonicalSnapshot, observations);
    }

    void clear(Player player);

    void clearPlayerState(java.util.UUID playerId);

    void shutdown();
}
