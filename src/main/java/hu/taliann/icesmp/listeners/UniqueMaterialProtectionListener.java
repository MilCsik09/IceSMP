package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.items.UniqueMaterialFactory;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Locks unique profession materials out of every ordinary vanilla use: they are PDC-tagged on top
 * of vanilla base items (some of which are edible, placeable, craftable or fuel), so without this
 * they could be eaten, placed or consumed in recipes. They may ONLY be used as ingredients in the
 * plugin's own profession recipe book. Blocks crafting, smithing, furnace fuel, eating and placing.
 */
public final class UniqueMaterialProtectionListener implements Listener {

    private final UniqueMaterialFactory uniqueMaterials;

    public UniqueMaterialProtectionListener(final UniqueMaterialFactory uniqueMaterials) {
        this.uniqueMaterials = uniqueMaterials;
    }

    private boolean isUnique(final ItemStack item) {
        return uniqueMaterials.idOf(item) != null;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareCraft(final PrepareItemCraftEvent event) {
        for (final ItemStack item : event.getInventory().getMatrix()) {
            if (isUnique(item)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareSmithing(final PrepareSmithingEvent event) {
        for (final ItemStack item : event.getInventory().getContents()) {
            if (isUnique(item)) {
                event.setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnaceBurn(final FurnaceBurnEvent event) {
        if (isUnique(event.getFuel())) {
            event.setCancelled(true);
        }
    }

    /** Smelt-INPUT tiltás: a unique a vanília alap-itemmé olvadna (pl. borostyán → aranyrúd). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnaceSmelt(final org.bukkit.event.inventory.FurnaceSmeltEvent event) {
        if (isUnique(event.getSource())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(final PlayerItemConsumeEvent event) {
        if (isUnique(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(final BlockPlaceEvent event) {
        if (isUnique(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }
}
