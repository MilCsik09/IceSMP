package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.FactionManager;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zoglin;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Passive faction bonuses, balanced so each faction's perk is roughly one tier of usefulness:
 * RED masters heat (immune to fire/lava/hot-floor — its Nether domain), BLUE masters cold & water
 * (immune to freezing AND drowning, plus slower hunger — its ocean/snow domain), NEUTRAL is the
 * sure-footed wanderer (no fall damage, and ignored by non-hostile mobs and endermen), and DARK is
 * the cursed one (immune to wither and ignored by the undead — intentionally the strongest PvE perk,
 * offset by the permanent sinner mark its members carry).
 *
 * <p>The Neutral sneak-invisibility was removed for balance (it was too strong in PvP) and replaced
 * with fall-damage immunity, a milder, non-combat utility.
 */
public final class FactionPassiveListener implements Listener {

    private static final Set<EntityDamageEvent.DamageCause> FIRE_CAUSES = EnumSet.of(
            EntityDamageEvent.DamageCause.FIRE,
            EntityDamageEvent.DamageCause.FIRE_TICK,
            EntityDamageEvent.DamageCause.LAVA,
            EntityDamageEvent.DamageCause.HOT_FLOOR
    );

    // Blue's "frozen waters" domain: immune to both the cold (freeze) and the deep (drowning).
    private static final Set<EntityDamageEvent.DamageCause> COLD_WATER_CAUSES = EnumSet.of(
            EntityDamageEvent.DamageCause.FREEZE,
            EntityDamageEvent.DamageCause.DROWNING
    );

    private final FactionManager factionManager;
    private final ConfigManager configManager;

    public FactionPassiveListener(final FactionManager factionManager, final ConfigManager configManager) {
        this.factionManager = factionManager;
        this.configManager = configManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(final EntityDamageEvent event) {
        if (!isEnabled() || !(event.getEntity() instanceof Player player)) {
            return;
        }

        final FactionType faction = factionManager.getFaction(player.getUniqueId());
        switch (faction) {
            case RED -> {
                if (FIRE_CAUSES.contains(event.getCause())) {
                    event.setCancelled(true);
                }
            }
            case BLUE -> {
                if (COLD_WATER_CAUSES.contains(event.getCause())) {
                    event.setCancelled(true);
                }
            }
            case DARK -> {
                if (event.getCause() == EntityDamageEvent.DamageCause.WITHER) {
                    event.setCancelled(true);
                }
            }
            case NEUTRAL -> {
                // Sure-footed wanderer: no fall damage (replaces the old sneak-invisibility).
                if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                    event.setCancelled(true);
                }
            }
            default -> {
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFoodLevelChange(final FoodLevelChangeEvent event) {
        if (!isEnabled() || !(event.getEntity() instanceof Player player)) {
            return;
        }

        if (event.getFoodLevel() >= player.getFoodLevel()) {
            return;
        }

        if (factionManager.getFaction(player.getUniqueId()) != FactionType.BLUE) {
            return;
        }

        final double slowChance = Math.max(0.0D, Math.min(1.0D,
                configManager.getDouble("factions.passives.blue-hunger-slow-chance", 0.5D)));
        if (ThreadLocalRandom.current().nextDouble() < slowChance) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTarget(final EntityTargetLivingEntityEvent event) {
        if (!isEnabled() || !(event.getTarget() instanceof Player player)) {
            return;
        }

        final FactionType faction = factionManager.getFaction(player.getUniqueId());
        // Neutral wanderer: non-hostile mobs ignore it, and endermen never aggro from a look
        // (endermen are Monsters, so they're called out explicitly).
        if (faction == FactionType.NEUTRAL
                && (!(event.getEntity() instanceof Monster) || event.getEntity() instanceof Enderman)) {
            event.setCancelled(true);
            return;
        }

        if (faction == FactionType.DARK && isUndead(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    private boolean isUndead(final Object entity) {
        return entity instanceof Zombie
                || entity instanceof AbstractSkeleton
                || entity instanceof Phantom
                || entity instanceof Zoglin;
    }

    private boolean isEnabled() {
        return configManager.getBoolean("factions.passives.enabled", true);
    }
}
