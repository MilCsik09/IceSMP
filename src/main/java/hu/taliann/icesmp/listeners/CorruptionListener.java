package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.CorruptionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

/**
 * H2 — a rontás-góc eseményei: a korrupt mob halála a tisztítás-számlálót növeli;
 * a mag SHIFT+jobb katt-ra törhető meg (elég kill után); a magot kézzel kibányászni
 * nem lehet (a rituálé része a megtörés). Minden ág régió-lokális (Folia).
 */
public final class CorruptionListener implements Listener {

    private final CorruptionManager corruptionManager;

    public CorruptionListener(final CorruptionManager corruptionManager) {
        this.corruptionManager = corruptionManager;
    }

    @EventHandler
    public void onCorruptMobDeath(final EntityDeathEvent event) {
        final hu.taliann.icesmp.utils.MobKillUtil.KillContext kill =
                hu.taliann.icesmp.utils.MobKillUtil.eligibleTrackingKill(event.getEntity());
        if (corruptionManager.forgetCorruptMob(event.getEntity().getUniqueId()) && kill != null) {
            corruptionManager.recordPurgeKill(kill.killerId());
        }
    }

    @EventHandler
    public void onEntitiesLoad(final EntitiesLoadEvent event) {
        corruptionManager.handleLoadedEntities(event.getEntities());
    }

    @EventHandler(ignoreCancelled = true)
    public void onCoreInteract(final PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || !event.getAction().isRightClick()) {
            return;
        }
        final Player player = event.getPlayer();
        if (!player.isSneaking() || !corruptionManager.isCoreBlock(event.getClickedBlock())) {
            return;
        }
        event.setCancelled(true);
        corruptionManager.tryCleanse(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCoreBreak(final BlockBreakEvent event) {
        // A mag nem bányászható ki — a megtörés rituáléja a SHIFT+jobb katt (elég kill után).
        if (corruptionManager.isCoreBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }
}
