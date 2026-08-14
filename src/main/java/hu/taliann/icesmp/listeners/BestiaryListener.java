package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.gui.BestiaryGUI;
import hu.taliann.icesmp.gui.BestiaryHolder;
import hu.taliann.icesmp.managers.BestiaryManager;
import hu.taliann.icesmp.managers.ProfessionRecipeCatalog;
import hu.taliann.icesmp.managers.StatsManager;
import hu.taliann.icesmp.managers.TerritoryManager;
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
 * B21 — bestiárium-hookok: mob-faj első elejtése + boss-ARCHETÍPUS (a gyilkos
 * PDC-írása a gyilkos régió-szálára ütemezve), valamint a csak olvasható lapozó
 * GUI kattintás-útvonalai. A recept- és territórium-hook a
 * ProfessionRecipeBookListener/TerritoryListener setter-injektált hívása.
 */
public final class BestiaryListener implements Listener {

    private final BestiaryManager bestiaryManager;
    private final WorldBossManager worldBossManager;
    private final StatsManager statsManager;
    private final ProfessionRecipeCatalog recipeCatalog;
    private final TerritoryManager territoryManager;

    public BestiaryListener(final BestiaryManager bestiaryManager,
                            final WorldBossManager worldBossManager,
                            final StatsManager statsManager,
                            final ProfessionRecipeCatalog recipeCatalog,
                            final TerritoryManager territoryManager) {
        this.bestiaryManager = bestiaryManager;
        this.worldBossManager = worldBossManager;
        this.statsManager = statsManager;
        this.recipeCatalog = recipeCatalog;
        this.territoryManager = territoryManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(final EntityDeathEvent event) {
        final hu.taliann.icesmp.utils.MobKillUtil.KillContext kill =
                hu.taliann.icesmp.utils.MobKillUtil.eligibleTrackingKill(event.getEntity());
        if (kill == null) {
            return;
        }
        // Folia: a halál a MOB régió-szálán fut — a killer profilját a killer
        // SAJÁT schedulerén írjuk (távolsági/AoE killnél más régióban lehet).
        if (worldBossManager != null && worldBossManager.isWorldBoss(event.getEntity())) {
            // Az ARCHETÍPUS a lajstrom-kulcs: két azonos vanilla-fajú boss külön bejegyzés.
            final String archetype = worldBossManager.archetypeId(event.getEntity());
            kill.runOnKiller(bestiaryManager.plugin(), killer ->
                    bestiaryManager.record(killer, BestiaryManager.Category.BOSSES, archetype));
            return;
        }
        if (event.getEntity() instanceof Monster) {
            // A ritka variáns ÖNÁLLÓ lajstrom-bejegyzés (pl. albino_zombie).
            final String entry = BestiaryManager.entryId(event.getEntity());
            kill.runOnKiller(bestiaryManager.plugin(), killer ->
                    bestiaryManager.record(killer, BestiaryManager.Category.MOBS, entry));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(final InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof BestiaryHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getInventory()) {
            return;
        }
        final int slot = event.getSlot();
        if (holder.category() == null) {
            final BestiaryManager.Category target = BestiaryGUI.MAIN_SLOTS.get(slot);
            if (target != null) {
                BestiaryGUI.openCategory(player, target, 0, bestiaryManager, statsManager,
                        recipeCatalog, territoryManager);
            }
            return;
        }
        switch (slot) {
            case BestiaryGUI.SLOT_BACK -> BestiaryGUI.openMain(player, bestiaryManager,
                    recipeCatalog, territoryManager);
            case BestiaryGUI.SLOT_PREV -> BestiaryGUI.openCategory(player, holder.category(),
                    holder.page() - 1, bestiaryManager, statsManager, recipeCatalog, territoryManager);
            case BestiaryGUI.SLOT_NEXT -> BestiaryGUI.openCategory(player, holder.category(),
                    holder.page() + 1, bestiaryManager, statsManager, recipeCatalog, territoryManager);
            default -> {
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(final InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof BestiaryHolder) {
            event.setCancelled(true);
        }
    }
}
