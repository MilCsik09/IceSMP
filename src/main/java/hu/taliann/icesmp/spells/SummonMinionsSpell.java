package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.MinionManager;
import hu.taliann.icesmp.managers.TalentManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.BiConsumer;

/**
 * Generic summoning spell for temporary combat minions (zombie horde, bone
 * archers, panda guard, wild pack...). Minions are owner-tagged through the
 * MinionManager so they never turn on their summoner, aimed at the caster's
 * ray-traced target (or the nearest hostile mob as fallback), and despawn
 * after their lifespan via the per-entity scheduler — which runs on the
 * entity's owning region thread and auto-retires if the minion dies early
 * (Folia-correct).
 */
public final class SummonMinionsSpell extends BaseSpell {

    private static final double TARGET_RANGE = 16.0D;
    private static final double FALLBACK_SCAN_RADIUS = 12.0D;
    private static final double SUMMON_RING_RADIUS = 1.6D;

    private final JavaPlugin plugin;
    private final MinionManager minionManager;
    private final ConfigManager configManager;
    private final TalentManager talentManager;
    private final Class<? extends Mob> entityClass;
    private final int count;
    private final int lifespanTicks;
    private final BiConsumer<Mob, Player> customizer;
    private final Particle particle;
    private final Sound sound;
    private final float soundPitch;
    private final String activatedMessage;

    public SummonMinionsSpell(final JavaPlugin plugin, final MinionManager minionManager,
                              final ConfigManager configManager, final TalentManager talentManager,
                              final MessageManager messageManager, final String id, final String defaultName,
                              final int cooldown, final SpellCostType costType, final int costAmount,
                              final Class<? extends Mob> entityClass, final int count, final int lifespanSeconds,
                              final BiConsumer<Mob, Player> customizer, final Particle particle,
                              final Sound sound, final float soundPitch, final String activatedMessage) {
        super(messageManager, id, defaultName, cooldown, costType, costAmount);
        this.plugin = plugin;
        this.minionManager = minionManager;
        this.configManager = configManager;
        this.talentManager = talentManager;
        this.entityClass = entityClass;
        this.count = Math.max(1, count);
        this.lifespanTicks = Math.max(20, lifespanSeconds * 20);
        this.customizer = customizer;
        this.particle = particle;
        this.sound = sound;
        this.soundPitch = soundPitch;
        this.activatedMessage = activatedMessage;
    }

    @Override
    public boolean canCast(final Player player) {
        // Summon cap (spam protection): block if the player has no free minion slot.
        return remainingSlots(player) > 0;
    }

    @Override
    public void execute(final Player player) {
        final LivingEntity target = resolveTarget(player);
        final Location center = player.getLocation();

        final int toSummon = Math.min(balanceInt("count", count), remainingSlots(player));
        final int lifespan = balanceInt("duration-ticks", lifespanTicks);
        // Class "max-health" talents also empower the owner's minions.
        final double bonusHealth = Math.max(0.0D, talentManager.getEffectTotal(player, "max-health")
                * Math.max(0.0D, configManager.getDouble("pets.talent-health-share", 0.5D)));

        for (int index = 0; index < toSummon; index++) {
            final double angle = (2.0D * Math.PI * index) / toSummon;
            final Location spawnLocation = center.clone().add(
                    Math.cos(angle) * SUMMON_RING_RADIUS,
                    0.0D,
                    Math.sin(angle) * SUMMON_RING_RADIUS
            );

            final Mob minion = player.getWorld().spawn(spawnLocation, entityClass);
            minion.setPersistent(false);
            minionManager.tag(minion, player.getUniqueId());
            if (customizer != null) {
                customizer.accept(minion, player);
            }

            if (bonusHealth > 0.0D) {
                final AttributeInstance maxHealth = minion.getAttribute(Attribute.MAX_HEALTH);
                if (maxHealth != null) {
                    maxHealth.setBaseValue(maxHealth.getBaseValue() + bonusHealth);
                    minion.setHealth(maxHealth.getValue());
                }
            }

            if (target != null) {
                minion.setTarget(target);
            }

            // Per-entity scheduler: runs on the minion's region thread and is
            // retired automatically if the minion dies before the lifespan ends.
            minion.getScheduler().runDelayed(plugin, task -> minion.remove(), null, lifespan);
            player.getWorld().spawnParticle(particle, spawnLocation.clone().add(0.0D, 1.0D, 0.0D), 15, 0.3D, 0.5D, 0.3D, 0.02D);
        }

        player.playSound(center, sound, 1.0F, soundPitch);
        player.sendMessage(resolveMessage("spell." + getId() + ".activated", activatedMessage));
    }

    private int remainingSlots(final Player player) {
        final int maxActive = Math.max(1, configManager.getInt("pets.max-active", 8));
        return maxActive - minionManager.countActive(player.getUniqueId());
    }

    private LivingEntity resolveTarget(final Player player) {
        final LivingEntity rayTarget = SpellTargetingUtil.rayTraceLivingEntity(player, balance("range", TARGET_RANGE));
        if (rayTarget != null && !minionManager.isOwnedBy(rayTarget, player.getUniqueId())) {
            return rayTarget;
        }

        LivingEntity nearest = null;
        double nearestDistanceSquared = Double.MAX_VALUE;
        final double fallbackScanRadius = balance("fallback-scan-radius", FALLBACK_SCAN_RADIUS);
        for (final Entity entity : player.getWorld().getNearbyEntities(player.getLocation(),
                fallbackScanRadius, fallbackScanRadius, fallbackScanRadius)) {
            if (!(entity instanceof Monster monster) || minionManager.isMinion(monster)) {
                continue;
            }

            final double distanceSquared = monster.getLocation().distanceSquared(player.getLocation());
            if (distanceSquared < nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
                nearest = monster;
            }
        }

        return nearest;
    }
}
