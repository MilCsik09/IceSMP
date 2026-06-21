package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.utils.MessageManager;
import hu.taliann.icesmp.utils.TextUtil;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * World bosses (ideas.md "Világ-bossok"): periodically a boss-grade guardian
 * spawns near a random adventurer. Slaying it rewards the killer's faction
 * treasury, grants league points and buffs the slayer. The spawn attempt is
 * rolled on the global world-events tick, but the actual entity spawn runs on
 * the owning region's scheduler (Folia-correct); the despawn timer uses the
 * boss's per-entity scheduler.
 */
public final class WorldBossManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final FactionManager factionManager;
    private final FactionTreasuryManager treasuryManager;
    private final SeasonManager seasonManager;
    private final NamespacedKey worldBossKey;

    private volatile long activeBossUntil;
    private volatile long nextAttemptAt;
    private volatile java.util.UUID activeBossId;

    public WorldBossManager(final JavaPlugin plugin, final ConfigManager configManager,
                            final MessageManager messageManager, final FactionManager factionManager,
                            final FactionTreasuryManager treasuryManager, final SeasonManager seasonManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.factionManager = factionManager;
        this.treasuryManager = treasuryManager;
        this.seasonManager = seasonManager;
        this.worldBossKey = new NamespacedKey(plugin, "world_boss");
    }

    public boolean isWorldBoss(final Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer().getOrDefault(worldBossKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
    }

    /** Whether a world boss is currently alive (for HUD / boss-bar display). */
    public boolean isBossActive() {
        return activeBossUntil > System.currentTimeMillis();
    }

    /**
     * Despawns the active world boss on plugin disable so the persistent, buffed
     * boss does not survive a reload as an unmanaged orphan (and a fresh boss can
     * spawn cleanly next start). Best-effort direct removal.
     */
    public void shutdown() {
        activeBossUntil = 0L;
        nextAttemptAt = 0L;
        final java.util.UUID id = activeBossId;
        activeBossId = null;
        if (id == null) {
            return;
        }
        final Entity boss = Bukkit.getEntity(id);
        if (boss != null && boss.isValid()) {
            try {
                boss.remove();
            } catch (final Exception ignored) {
                // Region/thread unavailable during shutdown — leave it; it is at worst a stray mob.
            }
        }
    }

    /** Periodic spawn attempt on the global world-events tick. */
    public void tick() {
        if (!configManager.getBoolean("world-events.world-boss.enabled", true)) {
            return;
        }

        final long now = System.currentTimeMillis();
        if (now < nextAttemptAt || now < activeBossUntil) {
            return;
        }

        final long intervalMinutes = Math.max(1L, configManager.getLong("world-events.world-boss.check-interval-minutes", 90L));
        nextAttemptAt = now + (intervalMinutes * 60_000L);

        final double chancePercent = Math.max(0.0D, Math.min(100.0D,
                configManager.getDouble("world-events.world-boss.chance-percent", 35.0D)));
        if (ThreadLocalRandom.current().nextDouble(100.0D) >= chancePercent) {
            return;
        }

        final List<? extends Player> online = List.copyOf(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) {
            return;
        }

        triggerSpawnNear(online.get(ThreadLocalRandom.current().nextInt(online.size())));
    }

    /**
     * Admin override: spawns a world boss immediately near the given anchor
     * (or a random online player if {@code anchor} is null). Safe to call from a
     * command; pass the issuing admin as anchor so the location read is region-local.
     *
     * @param anchor preferred anchor player (may be null)
     * @return true if a boss spawn was scheduled (false if one is already active or nobody is online)
     */
    public boolean forceSpawn(final Player anchor) {
        if (isBossActive()) {
            return false;
        }

        Player target = anchor;
        if (target == null) {
            final List<? extends Player> online = List.copyOf(Bukkit.getOnlinePlayers());
            if (online.isEmpty()) {
                return false;
            }
            target = online.get(ThreadLocalRandom.current().nextInt(online.size()));
        }

        triggerSpawnNear(target);
        return true;
    }

    private void triggerSpawnNear(final Player anchor) {
        // Folia: read the anchor's location on its OWN region thread first (it may be in a
        // different region than the caller), then hop to the spawn location's region.
        anchor.getScheduler().run(plugin, task -> {
            final double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0D);
            final double distance = 24.0D + ThreadLocalRandom.current().nextDouble(16.0D);
            final Location approx = anchor.getLocation().clone().add(
                    Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance);

            final long lifetimeMinutes = Math.max(1L, configManager.getLong("world-events.world-boss.lifetime-minutes", 20L));
            // activeBossUntil is set inside spawnBoss only AFTER the spawn is confirmed, so a bad
            // entity-type config never leaves the manager reporting a phantom active boss.
            plugin.getServer().getRegionScheduler().run(plugin, approx, spawnTask -> spawnBoss(approx, lifetimeMinutes));
        }, null);
    }

    private void spawnBoss(final Location approx, final long lifetimeMinutes) {
        final int highestY = approx.getWorld().getHighestBlockYAt(approx.getBlockX(), approx.getBlockZ());
        final Location spawnLocation = new Location(approx.getWorld(), approx.getBlockX() + 0.5D,
                highestY + 1.0D, approx.getBlockZ() + 0.5D);

        EntityType bossType;
        try {
            bossType = EntityType.valueOf(configManager.getString("world-events.world-boss.entity-type", "RAVAGER").toUpperCase(java.util.Locale.ROOT));
        } catch (final IllegalArgumentException exception) {
            bossType = EntityType.RAVAGER;
        }

        final Class<? extends Entity> entityClass = bossType.getEntityClass();
        if (entityClass == null || !Mob.class.isAssignableFrom(entityClass)) {
            plugin.getLogger().warning("Configured world boss entity-type is not a mob; skipping spawn.");
            return;
        }

        final Mob boss = (Mob) spawnLocation.getWorld().spawn(spawnLocation, entityClass.asSubclass(Mob.class));
        activeBossId = boss.getUniqueId();
        activeBossUntil = System.currentTimeMillis() + (lifetimeMinutes * 60_000L);
        boss.getPersistentDataContainer().set(worldBossKey, PersistentDataType.BYTE, (byte) 1);
        boss.setPersistent(true);
        boss.setRemoveWhenFarAway(false);
        boss.setGlowing(true);
        boss.customName(LegacyComponentSerializer.legacySection().deserialize(TextUtil.color(
                configManager.getString("world-events.world-boss.display-name", "&4&l☠ A Gyűrűk Őre &c[Világboss]"))));
        boss.setCustomNameVisible(true);

        final double health = Math.max(20.0D, configManager.getDouble("world-events.world-boss.health", 300.0D));
        final AttributeInstance maxHealth = boss.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(health);
            boss.setHealth(health);
        }

        final double damageMultiplier = Math.max(1.0D, configManager.getDouble("world-events.world-boss.damage-multiplier", 2.0D));
        final AttributeInstance attackDamage = boss.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attackDamage != null) {
            attackDamage.setBaseValue(attackDamage.getBaseValue() * damageMultiplier);
        }

        spawnLocation.getWorld().spawnParticle(Particle.FLASH, spawnLocation, 3);
        spawnLocation.getWorld().playSound(spawnLocation, Sound.ENTITY_RAVAGER_ROAR, 2.0F, 0.6F);

        Bukkit.getServer().broadcast(messageManager.getMessage(
                "world-boss-spawned",
                "<dark_red>👹 Világboss jelent meg: <white>{x}, {z}</white> környékén! Aki legyőzi, frakciója dicsőséget és kincset nyer.</dark_red>",
                Map.of(
                        "x", String.valueOf(spawnLocation.getBlockX()),
                        "z", String.valueOf(spawnLocation.getBlockZ())
                )
        ));

        // Per-entity despawn timer (retires automatically if the boss dies first).
        boss.getScheduler().runDelayed(plugin, task -> {
            if (boss.isValid()) {
                boss.remove();
                activeBossUntil = 0L;
                Bukkit.getServer().broadcast(messageManager.getMessage(
                        "world-boss-despawned",
                        "<gray>👹 A világboss elvonult — senki sem merte legyőzni.</gray>"
                ));
            }
        }, null, lifetimeMinutes * 60L * 20L);
    }

    /**
     * Pays out the boss kill: treasury reward + league points for the killer's
     * faction and a temporary buff for the slayer. Called by WorldBossListener.
     *
     * @param boss the slain boss
     * @param killer the slayer
     */
    public void handleBossDeath(final LivingEntity boss, final Player killer) {
        activeBossUntil = 0L;

        final FactionType faction = factionManager.getFaction(killer.getUniqueId());
        final double reward = Math.max(0.0D, configManager.getDouble("world-events.world-boss.treasury-reward", 300.0D));
        if (reward > 0.0D) {
            treasuryManager.deposit(faction, reward);
        }

        seasonManager.addPoints(faction, Math.max(0, configManager.getInt("world-events.world-boss.season-points", 10)));

        final int buffMinutes = Math.max(1, configManager.getInt("world-events.world-boss.buff-minutes", 10));
        // Folia: the death event runs on the boss's region; buff the killer on their own region thread.
        final int buffTicks = buffMinutes * 60 * 20;
        killer.getScheduler().run(plugin, task -> {
            killer.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, buffTicks, 0, false, true, true));
            killer.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, buffTicks, 0, false, true, true));
        }, null);

        Bukkit.getServer().broadcast(messageManager.getMessage(
                "world-boss-slain",
                "<gold>⚔ {player} legyőzte a világbosst! A(z) {faction} kasszája <white>{reward}</white> kincset és <white>{points}</white> liga-pontot nyert!</gold>",
                Map.of(
                        "player", killer.getName(),
                        "faction", faction.getDisplayName(),
                        "reward", String.valueOf(reward),
                        "points", String.valueOf(configManager.getInt("world-events.world-boss.season-points", 10))
                )
        ));
    }
}
