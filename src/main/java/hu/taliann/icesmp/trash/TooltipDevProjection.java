package hu.taliann.icesmp.trash;

import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * Hidden DEV-only presentation adapter for the version-pinned tooltip packet bridge.
 *
 * <p>The caller supplies a display clone. This adapter never mutates the player's canonical
 * inventory state and deliberately exposes only the currently selected main-hand projection.</p>
 */
public final class TooltipDevProjection {

    private TooltipDevProjection() {
    }

    public static boolean projectMainHand(final Player player, final ItemStack display) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(display, "display");
        return TooltipPacketBridge_1_21_11.projectHand(player, EquipmentSlot.HAND, display);
    }
}
