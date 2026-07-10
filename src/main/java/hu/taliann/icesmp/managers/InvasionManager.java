package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Invasion events (ROADMAP phase 9): periodically (or on admin command) a wave
 * of scaled monsters spawns around a random player — a dangerous swarm that
 * rewards via the normal scaled-mob XP and soulstone drops. The spawn runs on
 * the target region's thread (Folia-safe).
 */
public final class InvasionManager {

    /**
     * Horde compositions. Each wave picks one at random; its regular mobs are drawn from the
     * {@code pool}, and a single scaled {@code miniBoss} (a tougher, named champion) leads it.
     */
    private enum Horde {
        UNDEAD_TIDE("Élőhalott Áradat",
                new EntityType[]{EntityType.ZOMBIE, EntityType.HUSK}, EntityType.WITHER_SKELETON),
        BONE_LEGION("Csontlégió",
                new EntityType[]{EntityType.SKELETON, EntityType.STRAY}, EntityType.WITHER_SKELETON),
        SPIDER_NEST("Pókfészek",
                new EntityType[]{EntityType.SPIDER, EntityType.CAVE_SPIDER}, EntityType.SPIDER),
        CHAOS_HORDE("Káosz-horda",
                new EntityType[]{EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.PILLAGER},
                EntityType.RAVAGER),
        NETHER_RAID("Alvilági Roham",
                new EntityType[]{EntityType.PIGLIN, EntityType.ZOMBIFIED_PIGLIN}, EntityType.PIGLIN_BRUTE),
        ILLAGER_WARBAND("Zsivány Hadtest",
                new EntityType[]{EntityType.PILLAGER, EntityType.VINDICATOR}, EntityType.RAVAGER),
        WITCH_COVEN("Boszorkány Gyülekezet",
                new EntityType[]{EntityType.WITCH, EntityType.VEX}, EntityType.EVOKER),
        BLAZING_HOST("Lángoló Sereg",
                new EntityType[]{EntityType.BLAZE, EntityType.MAGMA_CUBE}, EntityType.PIGLIN_BRUTE);

        private final String displayName;
        private final EntityType[] pool;
        private final EntityType miniBoss;

        Horde(final String displayName, final EntityType[] pool, final EntityType miniBoss) {
            this.displayName = displayName;
            this.pool = pool;
            this.miniBoss = miniBoss;
        }

        EntityType randomMob() {
            return pool[ThreadLocalRandom.current().nextInt(pool.length)];
        }
    }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MobScalingManager mobScalingManager;
    private final MessageManager messageManager;

    private volatile long nextAttemptAt;
    /** UUIDs of currently-spawned invasion mobs, pruned each wave; despawned on shutdown. */
    private final Set<UUID> activeMobs = ConcurrentHashMap.newKeySet();

    public InvasionManager(final JavaPlugin plugin, final ConfigManager configManager,
                           final MobScalingManager mobScalingManager, final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.mobScalingManager = mobScalingManager;
        this.messageManager = messageManager;
    }

    /** Periodic attempt on the global world-events tick. */
    public void tick() {
        if (!configManager.getBoolean("world-events.invasion.enabled", true)) {
            return;
        }

        final long now = System.currentTimeMillis();
        if (now < nextAttemptAt) {
            return;
        }
        final long intervalMinutes = Math.max(1L, configManager.getLong("world-events.invasion.check-interval-minutes", 75L));
        nextAttemptAt = now + (intervalMinutes * 60_000L);

        final double chancePercent = Math.max(0.0D, Math.min(100.0D,
                configManager.getDouble("world-events.invasion.chance-percent", 30.0D)));
        if (ThreadLocalRandom.current().nextDouble(100.0D) >= chancePercent) {
            return;
        }

        final List<? extends Player> online = List.copyOf(Bukkit.getOnlinePlayers());
        if (!online.isEmpty()) {
            triggerNear(online.get(ThreadLocalRandom.current().nextInt(online.size())));
        }
    }

    /**
     * Admin override: launches an invasion near the given anchor (or a random
     * online player if null).
     *
     * @param anchor preferred anchor (issuing admin), may be null
     * @return true if an invasion was launched
     */
    public boolean forceStart(final Player anchor) {
        Player target = anchor;
        if (target == null) {
            final List<? extends Player> online = List.copyOf(Bukkit.getOnlinePlayers());
            if (online.isEmpty()) {
                return false;
            }
            target = online.get(ThreadLocalRandom.current().nextInt(online.size()));
        }
        triggerNear(target);
        return true;
    }

    private void triggerNear(final Player anchor) {
        // Folia: read the anchor's location on its OWN region thread first, then hop to that
        // location's region to spawn (the caller may run on the global or another region thread).
        anchor.getScheduler().run(plugin, task -> {
            final Location center = anchor.getLocation().clone();
            plugin.getServer().getRegionScheduler().run(plugin, center, spawnTask -> spawnWave(center));
        }, null);
    }

    /**
     * Despawns any still-living invasion mobs on plugin disable so a wave does not
     * survive a reload as orphaned, glowing, leveled mobs. Best-effort direct removal.
     */

    /** Whether the entity is a live invasion-horde mob (killing one must never count as sin). */
    public boolean isInvasionMob(final UUID entityId) {
        return entityId != null && activeMobs.contains(entityId);
    }

    public void shutdown() {
        nextAttemptAt = 0L;
        for (final UUID id : activeMobs) {
            final Entity entity = Bukkit.getEntity(id);
            if (entity != null && entity.isValid()) {
                try {
                    entity.remove();
                } catch (final Exception ignored) {
                    // Region/thread unavailable during shutdown — leave it.
                }
            }
        }
        activeMobs.clear();
    }

    private void spawnWave(final Location center) {
        if (center.getWorld() == null) {
            return;
        }

        // Keep the tracked set bounded: drop UUIDs whose mobs are already dead/gone.
        activeMobs.removeIf(id -> {
            try {
                final Entity existing = Bukkit.getEntity(id);
                return existing == null || !existing.isValid();
            } catch (final Exception exception) {
                return false;
            }
        });

        // Pick a random horde composition for this wave (variety).
        final Horde horde = Horde.values()[ThreadLocalRandom.current().nextInt(Horde.values().length)];
        final int count = Math.max(1, configManager.getInt("world-events.invasion.mob-count", 8));
        final int level = Math.max(1, configManager.getInt("world-events.invasion.mob-level", 4));
        final double radius = Math.max(2.0D, configManager.getDouble("world-events.invasion.radius", 8.0D));

        int spawned = 0;
        for (int i = 0; i < count; i++) {
            final double angle = (Math.PI * 2.0D / count) * i;
            final int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * radius);
            final int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * radius);
            if (spawnAt(topOf(center.getWorld(), x, z), horde.randomMob(), level) != null) {
                spawned++;
            }
        }

        // The horde's champion: a tougher, named mini-boss at the centre (final-wave feel).
        final int bossBonus = Math.max(0, configManager.getInt("world-events.invasion.mini-boss-level-bonus", 6));
        final Mob champion = spawnAt(topOf(center.getWorld(), center.getBlockX(), center.getBlockZ()),
                horde.miniBoss, level + bossBonus);
        if (champion != null) {
            champion.customName(net.kyori.adventure.text.Component.text(
                    "☠ " + horde.displayName + " Bajnoka",
                    net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE));
            champion.setCustomNameVisible(true);
            startChampionTick(champion);
        }

        if (spawned > 0 || champion != null) {
            Bukkit.getServer().broadcast(messageManager.getMessage(
                    "invasion-started",
                    "<dark_red>⚔ INVÁZIÓ — {horde}! Egy szörnyhorda tört be a vidékre, élükön egy bajnokkal — vigyázz!</dark_red>",
                    Map.of("horde", horde.displayName)
            ));
        }
    }

    /** Highest safe spawn spot above the given column. */
    private Location topOf(final org.bukkit.World world, final int x, final int z) {
        final int y = world.getHighestBlockYAt(x, z) + 1;
        return new Location(world, x + 0.5D, y, z + 0.5D);
    }

    /** Spawns a glowing, level-scaled invasion mob; returns null if the type is not a mob. */
    private Mob spawnAt(final Location spot, final EntityType type, final int level) {
        final Class<? extends Entity> entityClass = type.getEntityClass();
        if (entityClass == null || !Mob.class.isAssignableFrom(entityClass)) {
            return null;
        }
        final Mob mob = (Mob) spot.getWorld().spawn(spot, entityClass.asSubclass(Mob.class));
        mob.setGlowing(true);
        mob.setRemoveWhenFarAway(false);
        mobScalingManager.forceLevel(mob, level);
        activeMobs.add(mob.getUniqueId());
        return mob;
    }

    /**
     * Gives the horde champion a telegraphed ground-slam every ~6s so it plays like a mini-boss, not
     * just a tanky mob. Runs on the champion's own region scheduler (Folia-safe; touched players are
     * region-local nearby), and auto-cancels when the champion dies.
     */
    private void startChampionTick(final Mob champion) {
        final double damage = Math.max(1.0D, configManager.getDouble("world-events.invasion.champion-slam-damage", 5.0D));
        champion.getScheduler().runAtFixedRate(plugin, task -> {
            if (!champion.isValid()) {
                task.cancel();
                return;
            }
            final Location center = champion.getLocation().clone();
            final org.bukkit.World world = champion.getWorld();
            world.spawnParticle(org.bukkit.Particle.ANGRY_VILLAGER, center.clone().add(0.0D, 1.0D, 0.0D), 24, 3.0D, 0.3D, 3.0D, 0.02D);
            world.playSound(center, org.bukkit.Sound.ENTITY_RAVAGER_ROAR, 1.2F, 0.8F);
            champion.getScheduler().runDelayed(plugin, t -> {
                if (!champion.isValid()) {
                    return;
                }
                world.spawnParticle(org.bukkit.Particle.FLASH, center.clone().add(0.0D, 1.0D, 0.0D), 2);
                for (final Entity nearby : champion.getNearbyEntities(4.0D, 4.0D, 4.0D)) {
                    if (nearby instanceof Player player
                            && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)) {
                        player.damage(damage, champion);
                        final org.bukkit.util.Vector kb = player.getLocation().toVector().subtract(center.toVector());
                        if (kb.lengthSquared() > 0.0D) {
                            player.setVelocity(kb.normalize().setY(0.5D).multiply(0.7D));
                        }
                    }
                }
            }, null, 25L);
        }, null, 120L, 120L);
    }
}
