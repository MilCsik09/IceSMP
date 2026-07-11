package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Abundance (Bőség-idő) world event — the calm, restorative counterpart to the
 * blood moon (ROADMAP "élőbb világ"). While the window is open, crops grow faster,
 * animals occasionally bear twins, fewer hostile mobs spawn naturally, and a gentle
 * regeneration pulse washes over everyone online. It never touches terrain and adds
 * no currency — only comfort and a little extra farm/food yield.
 *
 * <p>The multipliers/chances are read by the
 * {@link hu.taliann.icesmp.listeners.AbundanceListener}; the manager only flips the
 * window and pulses the heal from the global tick (each player heal hops to its own
 * region thread).
 */
public final class AbundanceManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;

    private volatile boolean active;
    private volatile long activeUntil;
    private volatile long nextAttemptAt;

    public AbundanceManager(final JavaPlugin plugin, final ConfigManager configManager,
                            final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.nextAttemptAt = System.currentTimeMillis() + intervalMillis();
    }

    /** Whether the abundance window is currently open (read by the listener). */
    public boolean isActive() {
        return active;
    }

    /** Chance (0–1) for a crop to gain an extra growth stage while active, else 0. */
    public double cropBoostChance() {
        return active ? clampChance(configManager.getDouble("abundance.crop-boost-chance-percent", 40.0D)) : 0.0D;
    }

    /** Chance (0–1) for an animal to bear an extra baby while active, else 0. */
    public double twinChance() {
        return active ? clampChance(configManager.getDouble("abundance.twin-chance-percent", 25.0D)) : 0.0D;
    }

    /** Chance (0–1) to suppress a natural hostile spawn while active, else 0. */
    public double calmChance() {
        return active ? clampChance(configManager.getDouble("abundance.calm-spawn-chance-percent", 35.0D)) : 0.0D;
    }

    /** Periodic driver on the global world-events tick. */
    public void tick() {
        if (!configManager.getBoolean("abundance.enabled", true)) {
            if (active) {
                end();
            }
            return;
        }

        final long now = System.currentTimeMillis();
        if (active) {
            if (now >= activeUntil) {
                end();
            } else {
                pulseHeal();
            }
            return;
        }

        if (now >= nextAttemptAt) {
            nextAttemptAt = now + intervalMillis();
            final double chance = clampChance(configManager.getDouble("abundance.chance-percent", 45.0D)) * 100.0D;
            if (ThreadLocalRandom.current().nextDouble(100.0D) < chance) {
                start();
            }
        }
    }

    /** Admin override: opens the abundance window now. Returns false if one is already open. */
    public boolean forceStart() {
        if (active) {
            return false;
        }
        start();
        return true;
    }

    private void start() {
        active = true;
        activeUntil = System.currentTimeMillis() + durationMillis();
        Bukkit.getServer().broadcast(messageManager.getMessage(
                "abundance-start",
                "&a🌱 BŐSÉG-IDŐ! A következő {minutes} percben a termés virul, az állatok szaporák, a szörnyek csendesebbek — egy pillanatnyi béke.",
                java.util.Map.of("minutes", String.valueOf(Math.max(1L, durationMillis() / 60_000L)))));
        pulseHeal();
    }

    private void end() {
        active = false;
        Bukkit.getServer().broadcast(messageManager.getMessage(
                "abundance-end", "&7🌱 A Bőség-idő elmúlt — a világ visszatér a rendes kerékvágásba."));
    }

    /** A short, gentle regeneration for everyone online (a restorative night). */
    private void pulseHeal() {
        final int seconds = Math.max(3, configManager.getInt("abundance.regen-seconds", 8));
        for (final Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            player.getScheduler().run(plugin, task ->
                    player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, seconds * 20, 0, true, false, true)),
                    null);
        }
    }

    private static double clampChance(final double percent) {
        return Math.max(0.0D, Math.min(100.0D, percent)) / 100.0D;
    }

    private long intervalMillis() {
        return Math.max(1L, configManager.getLong("abundance.interval-minutes", 80L)) * 60_000L;
    }

    private long durationMillis() {
        return Math.max(1L, configManager.getLong("abundance.duration-minutes", 15L)) * 60_000L;
    }
}
