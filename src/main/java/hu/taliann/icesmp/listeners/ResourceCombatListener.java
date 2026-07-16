package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ResourceManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Harci erőforrás-töltés (IDEAS A3): minden bevitt ütés a {@link ResourceManager#onDamageDealt}
 * felé jelez, ami a düh-típusú kaszt-profiloknál ({@code combat-gain-per-hit}) tölti a tárat és
 * frissíti a harc-időbélyeget (az {@code idle-decay} kapuja).
 *
 * <p>Folia-megjegyzés: az event a MEGÜTÖTT entitás régió-szálán fut; a lövedék lövője elvben másik
 * régióban is lehet, ezért a damager Player-példányát NEM érintjük (se PDC, se inventory) — csak a
 * UUID-ját adjuk tovább, az {@code onDamageDealt} pedig kizárólag konkurens map-eket ír, így
 * bármely régió-szálról biztonságos.
 */
public final class ResourceCombatListener implements Listener {

    private final ResourceManager resourceManager;

    public ResourceCombatListener(final ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(final EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        final Player damager = resolveDamager(event);
        if (damager == null || damager == event.getEntity() || event.getFinalDamage() <= 0.0D) {
            return;
        }
        resourceManager.onDamageDealt(damager.getUniqueId());
        // A megütött JÁTÉKOS is harcba kerül (HUD harc-fókusz + düh-decay kapu).
        if (event.getEntity() instanceof Player victim) {
            resourceManager.onDamageTaken(victim.getUniqueId());
        }
    }

    private Player resolveDamager(final EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}
