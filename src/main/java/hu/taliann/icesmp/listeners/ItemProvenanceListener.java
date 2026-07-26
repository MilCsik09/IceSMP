package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.utils.ItemProvenance;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * A játékos inventoryából a földre került tárgyak megjelölése, hogy a felvételük ne számítson
 * gyűjtés-progressznek (a részleteket lásd {@link ItemProvenance}).
 *
 * <p>Két út van, amin a saját tárgy a földre kerülhet: a kézi dobás és a halál. A kézi dobásnál
 * megvan az entitás, a halálnál nem: a {@code PlayerDeathEvent} csak {@code ItemStack}-listát ad, a
 * szerver az entitásokat utána spawnolja. Ezért a halál-dropokat a KÖVETKEZŐ tickben, a halál helye
 * körül, régió-lokálisan jelöljük meg.
 *
 * <p>Folia: a drop-event a játékos, a halál-event a meghaló entitás régió-szálán fut; a
 * késleltetett jelölés a JÁTÉKOS entitás-schedulerén, tehát a szkennelt item-entitások a saját
 * régiójukban vannak.
 */
public final class ItemProvenanceListener implements Listener {

    /** A halál-dropok a halál pozíciójában jelennek meg; ez a sugár bőven elég rájuk. */
    private static final double DEATH_DROP_RADIUS = 4.0D;

    private final JavaPlugin plugin;

    public ItemProvenanceListener(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** MONITOR: csak megfigyelünk — cancel-elt dobásnál nincs entitás, amit jelölni kellene. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDrop(final PlayerDropItemEvent event) {
        ItemProvenance.markPlayerDropped(event.getItemDrop());
    }

    /**
     * A halál-dropok a PlayerDeathEvent LEFUTÁSA UTÁN kerülnek a világba, ezért egy tickkel
     * később jelölünk. A keep-inventory eset nem gond: akkor nincs mit megjelölni.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(final PlayerDeathEvent event) {
        if (event.getKeepInventory() || event.getDrops().isEmpty()) {
            return;
        }
        final org.bukkit.entity.Player player = event.getEntity();
        player.getScheduler().runDelayed(plugin, task -> {
            for (final org.bukkit.entity.Entity nearby
                    : player.getWorld().getNearbyEntities(player.getLocation(),
                    DEATH_DROP_RADIUS, DEATH_DROP_RADIUS, DEATH_DROP_RADIUS)) {
                if (nearby instanceof Item item) {
                    ItemProvenance.markPlayerDropped(item);
                }
            }
        }, null, 1L);
    }
}
