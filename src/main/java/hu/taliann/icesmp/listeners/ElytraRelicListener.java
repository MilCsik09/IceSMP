package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.RelicManager;
import hu.taliann.icesmp.relics.RelicDefinition;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.Set;

/**
 * The four faction elytra relics (ideas.md "4 frakció – 4 elytra relikvia"):
 * - phoenix_wing (RED): fire/lava immunity while worn; falls end in a flame burst
 * - frost_wing (BLUE): freeze immunity; taking flight freezes nearby enemies
 * - wander_wind (NEUTRAL): no fall damage; taking flight grants a wind boost
 * - bone_wing (DARK): wither immunity; night flight turns the wearer into a shade
 * Effects only apply to the relic's owner wearing it in the chest slot AND
 * belonging to the wing's faction.
 */
public final class ElytraRelicListener implements Listener {

    private static final Map<String, FactionType> WING_FACTIONS = Map.of(
            "phoenix_wing", FactionType.RED,
            "frost_wing", FactionType.BLUE,
            "wander_wind", FactionType.NEUTRAL,
            "bone_wing", FactionType.DARK
    );

    private static final Set<EntityDamageEvent.DamageCause> FIRE_CAUSES = Set.of(
            EntityDamageEvent.DamageCause.FIRE,
            EntityDamageEvent.DamageCause.FIRE_TICK,
            EntityDamageEvent.DamageCause.LAVA,
            EntityDamageEvent.DamageCause.HOT_FLOOR
    );

    private final RelicManager relicManager;
    private final FactionManager factionManager;
    private final MessageManager messageManager;

    public ElytraRelicListener(final RelicManager relicManager, final FactionManager factionManager,
                               final MessageManager messageManager) {
        this.relicManager = relicManager;
        this.factionManager = factionManager;
        this.messageManager = messageManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleGlide(final EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player) || !event.isGliding()) {
            return;
        }

        final String wingId = resolveWornWing(player, true);
        if (wingId == null) {
            return;
        }

        switch (wingId) {
            case "phoenix_wing" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 60 * 20, 0, false, false, true));
                player.getWorld().spawnParticle(Particle.FLAME, player.getLocation(), 25, 0.4D, 0.4D, 0.4D, 0.02D);
            }
            case "frost_wing" -> {
                for (final Entity nearby : player.getWorld().getNearbyEntities(player.getLocation(), 6.0D, 6.0D, 6.0D)) {
                    if (nearby instanceof LivingEntity living && nearby != player) {
                        living.setFreezeTicks(Math.max(living.getFreezeTicks(), 200));
                        living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 4 * 20, 1, false, true, true));
                    }
                }
                player.getWorld().spawnParticle(Particle.SNOWFLAKE, player.getLocation(), 40, 3.0D, 1.0D, 3.0D, 0.02D);
                player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8F, 0.6F);
            }
            case "wander_wind" -> {
                player.setVelocity(player.getLocation().getDirection().multiply(1.5D));
                player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 25, 0.5D, 0.5D, 0.5D, 0.05D);
                player.playSound(player.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 0.8F, 1.2F);
            }
            case "bone_wing" -> {
                if (isNight(player)) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 10 * 20, 0, false, false, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 10 * 20, 0, false, false, true));
                    player.getWorld().spawnParticle(Particle.SOUL, player.getLocation(), 30, 0.5D, 0.5D, 0.5D, 0.03D);
                }
            }
            default -> {
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(final EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        final String wingId = resolveWornWing(player, false);
        if (wingId == null) {
            return;
        }

        switch (wingId) {
            case "phoenix_wing" -> {
                if (FIRE_CAUSES.contains(event.getCause())) {
                    event.setCancelled(true);
                    return;
                }
                if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                    event.setCancelled(true);
                    // Phoenix landing: a burst of flame ignites nearby enemies.
                    for (final Entity nearby : player.getWorld().getNearbyEntities(player.getLocation(), 4.0D, 2.0D, 4.0D)) {
                        if (nearby instanceof LivingEntity living && nearby != player) {
                            living.setFireTicks(Math.max(living.getFireTicks(), 60));
                        }
                    }
                    player.getWorld().spawnParticle(Particle.FLAME, player.getLocation(), 60, 2.0D, 0.5D, 2.0D, 0.05D);
                    player.getWorld().playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.0F, 0.8F);
                }
            }
            case "frost_wing" -> {
                if (event.getCause() == EntityDamageEvent.DamageCause.FREEZE) {
                    event.setCancelled(true);
                }
            }
            case "wander_wind" -> {
                if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                    event.setCancelled(true);
                }
            }
            case "bone_wing" -> {
                if (event.getCause() == EntityDamageEvent.DamageCause.WITHER) {
                    event.setCancelled(true);
                }
            }
            default -> {
            }
        }
    }

    /**
     * Identifies the worn elytra relic if (and only if) the wearer may benefit
     * from it: relic owner + matching faction.
     *
     * @param player the player
     * @param notifyOnMismatch whether to warn when the wearer fails the requirements
     * @return the wing relic id, or null
     */
    private String resolveWornWing(final Player player, final boolean notifyOnMismatch) {
        final ItemStack chestplate = player.getInventory().getChestplate();
        final RelicDefinition definition = relicManager.identify(chestplate);
        if (definition == null) {
            return null;
        }

        final FactionType wingFaction = WING_FACTIONS.get(definition.id().toLowerCase(java.util.Locale.ROOT));
        if (wingFaction == null) {
            return null;
        }

        if (!relicManager.canUse(player, chestplate)
                || factionManager.getFaction(player.getUniqueId()) != wingFaction) {
            if (notifyOnMismatch) {
                player.sendMessage(messageManager.getMessage(
                        "relic.wing-rejected",
                        "<red>A szárny nem ismer el gazdájának — csak a(z) {faction} frakció hű tagját szolgálja.</red>",
                        Map.of("faction", wingFaction.getDisplayName())
                ));
            }
            return null;
        }

        return definition.id().toLowerCase(java.util.Locale.ROOT);
    }

    private boolean isNight(final Player player) {
        final long time = player.getWorld().getTime();
        return time >= 13000L && time <= 23000L;
    }
}
