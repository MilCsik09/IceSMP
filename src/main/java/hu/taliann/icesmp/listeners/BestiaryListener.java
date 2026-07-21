package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.gui.BestiaryHolder;
import hu.taliann.icesmp.managers.BestiaryManager;
import hu.taliann.icesmp.managers.WorldBossManager;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * B21 — bestiárium-hookok: mob-faj első elejtése + boss-archetípus (a kill a
 * gyilkos régió-szálán fut, a PDC-írás ott biztonságos), valamint a csak
 * olvasható áttekintő GUI kattintás-tiltása. A recept- és territórium-hook a
 * ProfessionRecipeBookListener/TerritoryListener setter-injektált hívása.
 */
public final class BestiaryListener implements Listener {

    private final BestiaryManager bestiaryManager;
    private final WorldBossManager worldBossManager;

    public BestiaryListener(final BestiaryManager bestiaryManager, final WorldBossManager worldBossManager) {
        this.bestiaryManager = bestiaryManager;
        this.worldBossManager = worldBossManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(final EntityDeathEvent event) {
        final Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        // Folia: a halál a MOB régió-szálán fut — a killer PDC-jét (record) a killer
        // SAJÁT schedulerén írjuk (távolsági/AoE killnél más régióban lehet).
        if (worldBossManager != null && worldBossManager.isWorldBoss(event.getEntity())) {
            final String bossType = event.getEntity().getType().name();
            killer.getScheduler().run(bestiaryManager.plugin(), task ->
                    bestiaryManager.record(killer, BestiaryManager.Category.BOSSES, bossType), null);
            return;
        }
        if (event.getEntity() instanceof Monster) {
            // H14 — a ritka variáns ÖNÁLLÓ lajstrom-bejegyzés (pl. albino_zombie).
            final String variant = hu.taliann.icesmp.managers.MobScalingManager.rareVariantOf(event.getEntity());
            final String entry = (variant == null ? "" : variant + "_") + event.getEntity().getType().name();
            killer.getScheduler().run(bestiaryManager.plugin(), task ->
                    bestiaryManager.record(killer, BestiaryManager.Category.MOBS, entry), null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(final InventoryClickEvent event) {
        if (event.getInventory().getHolder(false) instanceof BestiaryHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(final InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof BestiaryHolder) {
            event.setCancelled(true);
        }
    }
}
