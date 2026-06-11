package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.FactionManager;
import org.bukkit.entity.AbstractSkeleton;
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
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Passive faction bonuses:
 * RED is immune to fire-based damage, BLUE is immune to freezing and gets slower hunger,
 * NEUTRAL turns invisible while sneaking and is ignored by non-hostile mobs,
 * DARK is immune to wither damage and is ignored by the undead.
 */
public final class FactionPassiveListener implements Listener {

    private static final Set<EntityDamageEvent.DamageCause> FIRE_CAUSES = EnumSet.of(
            EntityDamageEvent.DamageCause.FIRE,
            EntityDamageEvent.DamageCause.FIRE_TICK,
            EntityDamageEvent.DamageCause.LAVA,
            EntityDamageEvent.DamageCause.HOT_FLOOR
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
                if (event.getCause() == EntityDamageEvent.DamageCause.FREEZE) {
                    event.setCancelled(true);
                }
            }
            case DARK -> {
                if (event.getCause() == EntityDamageEvent.DamageCause.WITHER) {
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
    public void onPlayerToggleSneak(final PlayerToggleSneakEvent event) {
        if (!isEnabled()) {
            return;
        }

        final Player player = event.getPlayer();
        if (factionManager.getFaction(player.getUniqueId()) != FactionType.NEUTRAL) {
            return;
        }

        if (event.isSneaking()) {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
        } else {
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTarget(final EntityTargetLivingEntityEvent event) {
        if (!isEnabled() || !(event.getTarget() instanceof Player player)) {
            return;
        }

        final FactionType faction = factionManager.getFaction(player.getUniqueId());
        if (faction == FactionType.NEUTRAL && !(event.getEntity() instanceof Monster)) {
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
