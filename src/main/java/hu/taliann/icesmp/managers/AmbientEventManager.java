package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ambient world events (ROADMAP "élőbb világ"): frequent, small, atmospheric
 * happenings that make the world feel alive without touching balance or the
 * economy. Each firing picks one <em>enabled</em> flavour at random and either
 * broadcasts + paints client-side particles, hands out a short cosmetic buff,
 * or spawns a small herd of passive animals (an item faucet, never currency).
 *
 * <p>The tick runs on the global region scheduler; every player- or
 * location-touching effect hops to the owning region thread (Folia-safe).
 */
public final class AmbientEventManager {

    /** The atmospheric flavours; each is individually toggleable in config. */
    private enum Ambient {
        AURORA,
        FALLING_STAR,
        FOG_ROLL,
        SPECTRAL_WANDERERS,
        ANIMAL_MIGRATION,
        FIREFLIES;

        String configKey() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    /** Passive animals a migration herd is drawn from. */
    private static final EntityType[] HERD = {
            EntityType.COW, EntityType.SHEEP, EntityType.PIG, EntityType.CHICKEN, EntityType.HORSE
    };

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;

    private volatile long nextAttemptAt;

    public AmbientEventManager(final JavaPlugin plugin, final ConfigManager configManager,
                               final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.nextAttemptAt = System.currentTimeMillis() + intervalMillis();
    }

    /** Periodic attempt on the global world-events tick. */
    public void tick() {
        if (!configManager.getBoolean("ambient-events.enabled", true)) {
            return;
        }

        final long now = System.currentTimeMillis();
        if (now < nextAttemptAt) {
            return;
        }
        nextAttemptAt = now + intervalMillis();

        final double chance = Math.max(0.0D, Math.min(100.0D,
                configManager.getDouble("ambient-events.chance-percent", 55.0D)));
        if (ThreadLocalRandom.current().nextDouble(100.0D) >= chance) {
            return;
        }

        final List<Ambient> enabled = new ArrayList<>();
        for (final Ambient ambient : Ambient.values()) {
            if (configManager.getBoolean("ambient-events.types." + ambient.configKey(), true)) {
                enabled.add(ambient);
            }
        }
        if (enabled.isEmpty()) {
            return;
        }
        fire(enabled.get(ThreadLocalRandom.current().nextInt(enabled.size())));
    }

    /** Admin override: fires a random enabled ambient event now. Returns false if none are enabled. */
    public boolean forceRandom() {
        final List<Ambient> enabled = new ArrayList<>();
        for (final Ambient ambient : Ambient.values()) {
            if (configManager.getBoolean("ambient-events.types." + ambient.configKey(), true)) {
                enabled.add(ambient);
            }
        }
        if (enabled.isEmpty()) {
            return false;
        }
        fire(enabled.get(ThreadLocalRandom.current().nextInt(enabled.size())));
        return true;
    }

    private void fire(final Ambient ambient) {
        switch (ambient) {
            case AURORA -> aurora();
            case FALLING_STAR -> fallingStar();
            case FOG_ROLL -> skyEffect("ambient-fog", Particle.CLOUD, Sound.WEATHER_RAIN, 0.9F);
            case SPECTRAL_WANDERERS -> skyEffect("ambient-spectral", Particle.SOUL, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 0.7F);
            case FIREFLIES -> skyEffect("ambient-fireflies", Particle.END_ROD, Sound.BLOCK_BEEHIVE_WORK, 0.5F);
            case ANIMAL_MIGRATION -> animalMigration();
        }
    }

    /** Aurora: broadcast + shimmering sky particles and a brief, purely-cosmetic Night Vision. */
    private void aurora() {
        Bukkit.getServer().broadcast(messageManager.getMessage(
                "ambient-aurora", "&b🌌 Északi fény ragyog fel az égen — a világ egy pillanatra elcsendesedik."));
        final int seconds = Math.max(5, configManager.getInt("ambient-events.aurora-nightvision-seconds", 45));
        for (final Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            player.getScheduler().run(plugin, task -> {
                final Location base = player.getLocation().clone().add(0.0D, 6.0D, 0.0D);
                player.spawnParticle(Particle.END_ROD, base, 40, 6.0D, 2.0D, 6.0D, 0.01D);
                player.spawnParticle(Particle.SOUL_FIRE_FLAME, base, 20, 6.0D, 2.0D, 6.0D, 0.0D);
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, seconds * 20, 0, true, false, true));
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6F, 1.2F);
            }, null);
        }
    }

    /** Falling star: broadcast with coordinates near a random player + a streaking particle trail. */
    private void fallingStar() {
        final List<? extends Player> online = List.copyOf(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) {
            return;
        }
        final Player anchor = online.get(ThreadLocalRandom.current().nextInt(online.size()));
        anchor.getScheduler().run(plugin, task -> {
            final Location where = anchor.getLocation().clone();
            Bukkit.getServer().broadcast(messageManager.getMessage(
                    "ambient-falling-star",
                    "&e☄ Hulló csillag hasít át az égbolton a(z) {world} felett ({x}, {z})!",
                    Map.of(
                            "world", where.getWorld() == null ? "?" : where.getWorld().getName(),
                            "x", String.valueOf(where.getBlockX()),
                            "z", String.valueOf(where.getBlockZ())
                    )
            ));
            // A short streak of sparks overhead, visible to nearby players.
            final World world = where.getWorld();
            if (world != null) {
                final Location high = where.clone().add(0.0D, 18.0D, 0.0D);
                world.spawnParticle(Particle.FIREWORK, high, 60, 4.0D, 6.0D, 4.0D, 0.05D);
                world.spawnParticle(Particle.END_ROD, high, 30, 3.0D, 5.0D, 3.0D, 0.02D);
                world.playSound(where, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.0F, 1.4F);
            }
        }, null);
    }

    /** Shared cosmetic sky/atmosphere effect: broadcast + per-player local particles and a soft sound. */
    private void skyEffect(final String messageKey, final Particle particle, final Sound sound, final float volume) {
        Bukkit.getServer().broadcast(messageManager.getMessage(messageKey, defaultFor(messageKey)));
        for (final Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            player.getScheduler().run(plugin, task -> {
                final Location base = player.getLocation().clone().add(0.0D, 3.0D, 0.0D);
                player.spawnParticle(particle, base, 30, 5.0D, 2.5D, 5.0D, 0.01D);
                player.playSound(player.getLocation(), sound, volume, 1.0F);
            }, null);
        }
    }

    /** Animal migration: a small herd of passive animals wanders in near a random player. */
    private void animalMigration() {
        final List<? extends Player> online = List.copyOf(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) {
            return;
        }
        final Player anchor = online.get(ThreadLocalRandom.current().nextInt(online.size()));
        final int count = Math.max(1, configManager.getInt("ambient-events.migration-herd-size", 5));
        anchor.getScheduler().run(plugin, task -> {
            final Location center = anchor.getLocation().clone();
            plugin.getServer().getRegionScheduler().run(plugin, center, spawn -> spawnHerd(center, count));
        }, null);
    }

    private void spawnHerd(final Location center, final int count) {
        final World world = center.getWorld();
        if (world == null) {
            return;
        }
        final EntityType type = HERD[ThreadLocalRandom.current().nextInt(HERD.length)];
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            final double dx = ThreadLocalRandom.current().nextDouble(-6.0D, 6.0D);
            final double dz = ThreadLocalRandom.current().nextDouble(-6.0D, 6.0D);
            final int x = center.getBlockX() + (int) Math.round(dx);
            final int z = center.getBlockZ() + (int) Math.round(dz);
            final int y = world.getHighestBlockYAt(x, z) + 1;
            final Location spot = new Location(world, x + 0.5D, y, z + 0.5D);
            try {
                world.spawnEntity(spot, type);
                spawned++;
            } catch (final IllegalArgumentException ignored) {
                // Non-spawnable type in this context — skip.
            }
        }
        if (spawned > 0) {
            world.spawnParticle(Particle.HAPPY_VILLAGER, center.clone().add(0.0D, 1.0D, 0.0D), 12, 3.0D, 1.0D, 3.0D, 0.0D);
            Bukkit.getServer().broadcast(messageManager.getMessage(
                    "ambient-migration", "&a🐾 Vándorló állatcsorda kelt át a vidéken — élelem az éber telepeseknek."));
        }
    }

    private String defaultFor(final String messageKey) {
        return switch (messageKey) {
            case "ambient-fog" -> "&7🌫 Sűrű köd ereszkedik a tájra — óvatosan az utakon.";
            case "ambient-spectral" -> "&8👻 Bolyongó szellemek suhannak át a vidéken az éj leple alatt.";
            case "ambient-fireflies" -> "&e✨ Szentjánosbogarak raja gyúl fel a szürkületben.";
            default -> "&7Valami megmozdul a világban…";
        };
    }

    private long intervalMillis() {
        return Math.max(1L, configManager.getLong("ambient-events.interval-minutes", 15L)) * 60_000L;
    }
}
