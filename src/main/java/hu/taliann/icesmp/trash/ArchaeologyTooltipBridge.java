package hu.taliann.icesmp.trash;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Narrow player-only display-copy seam; canonical inventory state is never accepted for mutation. */
public interface ArchaeologyTooltipBridge {

    boolean available();

    boolean show(Player player, ItemStack canonicalSnapshot, List<String> observations);

    void clear(Player player);

    void clearPlayerState(java.util.UUID playerId);

    void shutdown();
}
