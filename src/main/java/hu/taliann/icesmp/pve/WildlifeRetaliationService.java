package hu.taliann.icesmp.pve;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.utils.ParticleUtil;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Event-driven, Folia-safe passive-wildlife retaliation. It never promotes rank or awards loot. */
public final class WildlifeRetaliationService implements Listener {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final NamespacedKey temperamentKey;
    private final NamespacedKey cooldownKey;

    public WildlifeRetaliationService(final JavaPlugin plugin, final ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.temperamentKey = new NamespacedKey(plugin, "wildlife_temperament");
        this.cooldownKey = new NamespacedKey(plugin, "wildlife_retaliation_ready");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProvoked(final EntityDamageByEntityEvent event) {
        if (!config.getBoolean("wildlife-retaliation.enabled", true)
                || !(event.getEntity() instanceof Animals animal)) return;
        final Player player = responsiblePlayer(event.getDamager());
        if (player == null || !eligible(animal)) return;
        final String species = animal.getType().name().toLowerCase(Locale.ROOT);
        final String path = "wildlife-retaliation.species." + species;
        if (!config.getBoolean(path + ".enabled", false)) return;
        final WildlifeRetaliationPolicy.Temperament temperament = temperament(animal, path);
        final WildlifeRetaliationPolicy.Chances chances = retaliationChances(path);
        if (!WildlifeRetaliationPolicy.retaliates(temperament, chances,
                ThreadLocalRandom.current().nextDouble())) return;
        if (!claimCooldown(animal, path)) return;
        scheduleRetaliation(animal, player, path, false);
        if (config.getBoolean("wildlife-retaliation.herd-assist.enabled", true)) {
            assistHerd(animal, player, path);
        }
    }

    private Player responsiblePlayer(final Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile) {
            final ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) return player;
        }
        return null;
    }

    private boolean eligible(final Animals animal) {
        if (!animal.isValid() || animal.isDead()) return false;
        if (animal instanceof Ageable ageable && !ageable.isAdult()) return false;
        return !(animal instanceof Tameable tameable) || !tameable.isTamed();
    }

    private WildlifeRetaliationPolicy.Temperament temperament(final Animals animal,
                                                                final String path) {
        final String stored = animal.getPersistentDataContainer().get(
                temperamentKey, PersistentDataType.STRING);
        if (stored != null) {
            try {
                return WildlifeRetaliationPolicy.Temperament.valueOf(stored);
            } catch (final IllegalArgumentException ignored) {
                animal.getPersistentDataContainer().remove(temperamentKey);
            }
        }
        int timid = boundedWeight(path + ".temperament.timid", 45);
        int defensive = boundedWeight(path + ".temperament.defensive", 40);
        int aggressive = boundedWeight(path + ".temperament.aggressive", 15);
        if (timid + defensive + aggressive == 0) {
            timid = 45;
            defensive = 40;
            aggressive = 15;
        }
        final WildlifeRetaliationPolicy.Weights weights = new WildlifeRetaliationPolicy.Weights(
                timid, defensive, aggressive);
        final WildlifeRetaliationPolicy.Temperament selected =
                WildlifeRetaliationPolicy.stableTemperament(animal.getUniqueId(), weights);
        animal.getPersistentDataContainer().set(temperamentKey,
                PersistentDataType.STRING, selected.name());
        return selected;
    }

    private boolean claimCooldown(final Animals animal, final String path) {
        final long now = animal.getWorld().getFullTime();
        final long ready = animal.getPersistentDataContainer().getOrDefault(
                cooldownKey, PersistentDataType.LONG, 0L);
        if (now < ready) return false;
        final long cooldown = Math.max(20L, Math.min(2_400L,
                config.getLong(path + ".cooldown-ticks", 160L)));
        animal.getPersistentDataContainer().set(cooldownKey,
                PersistentDataType.LONG, now + cooldown);
        return true;
    }

    private void scheduleRetaliation(final Animals animal, final Player player,
                                     final String path, final boolean herdAssist) {
        final long warning = Math.max(10L, Math.min(80L,
                config.getLong("wildlife-retaliation.warning-ticks", 20L)));
        ParticleUtil.spawn(animal.getWorld(), Particle.ANGRY_VILLAGER,
                animal.getLocation().add(0.0D, 0.8D, 0.0D), 4, .25D, .2D, .25D, .01D);
        animal.getWorld().playSound(animal.getLocation(), Sound.ENTITY_GOAT_PREPARE_RAM, .7F, 1.2F);
        CombatTelemetry.record("wildlife_warning", animal.getType().name());
        try {
            animal.getScheduler().runDelayed(plugin, task -> {
                if (!eligible(animal)) return;
                final Location source = animal.getLocation().clone();
                final String species = animal.getType().name();
                player.getScheduler().run(plugin,
                        hit -> applyHit(source, species, player, path, herdAssist), null);
            }, null, warning);
        } catch (final RuntimeException ignored) {
            // A rejected owner task leaves only the harmless warning and no retained state.
        }
    }

    private void applyHit(final Location source, final String species, final Player player,
                          final String path, final boolean herdAssist) {
        if (!player.isOnline() || source.getWorld() != player.getWorld()) return;
        final double range = Math.max(1.5D, Math.min(8.0D,
                config.getDouble(path + ".retaliation-range", 4.0D)));
        if (source.distanceSquared(player.getLocation()) > range * range) {
            CombatTelemetry.record("wildlife_evaded", species);
            return;
        }
        final double damage = Math.max(0.0D, Math.min(8.0D,
                config.getDouble(path + ".damage", 2.0D))) * (herdAssist ? .75D : 1.0D);
        if (damage > 0.0D) player.damage(damage);
        final Vector away = player.getLocation().toVector().subtract(source.toVector());
        if (away.lengthSquared() > .01D) {
            final double knockback = Math.max(0.0D, Math.min(1.5D,
                    config.getDouble(path + ".knockback", .55D)));
            player.setVelocity(away.normalize().multiply(knockback).setY(.32D));
        }
        CombatTelemetry.record("wildlife_hit", species);
    }

    private void assistHerd(final Animals source, final Player player, final String path) {
        final double radius = Math.max(1.0D, Math.min(12.0D,
                config.getDouble("wildlife-retaliation.herd-assist.radius", 6.0D)));
        final int maximum = Math.max(0, Math.min(6,
                config.getInt("wildlife-retaliation.herd-assist.maximum-allies", 2)));
        final WildlifeRetaliationPolicy.Chances chances = retaliationChances(path);
        int accepted = 0;
        for (final Entity nearby : source.getNearbyEntities(radius, radius, radius)) {
            if (!(nearby instanceof Animals ally) || ally == source
                    || ally.getType() != source.getType() || !Bukkit.isOwnedByCurrentRegion(ally)
                    || !eligible(ally)) continue;
            final WildlifeRetaliationPolicy.Temperament temperament = temperament(ally, path);
            if (!WildlifeRetaliationPolicy.retaliates(temperament, chances,
                    ThreadLocalRandom.current().nextDouble()) || !claimCooldown(ally, path)) continue;
            scheduleRetaliation(ally, player, path, true);
            if (++accepted >= maximum) break;
        }
    }

    private WildlifeRetaliationPolicy.Chances retaliationChances(final String path) {
        return new WildlifeRetaliationPolicy.Chances(
                boundedPercent(path + ".retaliation-percent.timid", 10.0D),
                boundedPercent(path + ".retaliation-percent.defensive", 70.0D),
                boundedPercent(path + ".retaliation-percent.aggressive", 100.0D));
    }

    private int boundedWeight(final String path, final int fallback) {
        return Math.max(0, Math.min(10_000, config.getInt(path, fallback)));
    }

    private double boundedPercent(final String path, final double fallback) {
        final double value = config.getDouble(path, fallback);
        return Double.isFinite(value) ? Math.max(0.0D, Math.min(100.0D, value)) : fallback;
    }
}
