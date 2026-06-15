package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Blood moon nights (ideas.md "Vérhold-éjszaka"): rarely, a night turns into a
 * blood moon — every scaled mob spawns with bonus levels and soulstone drops
 * multiply. The tick runs on the global region scheduler, which owns the
 * day-night cycle on Folia, so reading world time here is thread-correct.
 */
public final class BloodMoonManager {

    private static final long NIGHT_START_TICK = 13000L;
    private static final long ROLL_WINDOW_END_TICK = 14400L;
    private static final long DAWN_TICK = 12500L;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;

    private volatile boolean active;
    private volatile long lastRolledDay = -1L;

    public BloodMoonManager(final JavaPlugin plugin, final ConfigManager configManager,
                            final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
    }

    public boolean isActive() {
        return active;
    }

    /**
     * Bonus mob levels applied by the MobScalingManager while the blood moon lasts.
     *
     * @return the extra levels (0 when inactive)
     */
    public int getBonusMobLevels() {
        return active ? Math.max(0, configManager.getInt("world-events.blood-moon.bonus-mob-levels", 2)) : 0;
    }

    /**
     * Soulstone drop chance multiplier while the blood moon lasts.
     *
     * @return the multiplier (1.0 when inactive)
     */
    public double getSoulDropMultiplier() {
        return active ? Math.max(1.0D, configManager.getDouble("world-events.blood-moon.soul-drop-multiplier", 2.0D)) : 1.0D;
    }

    /** Periodic tick on the global region scheduler. */
    public void tick() {
        if (!configManager.getBoolean("world-events.blood-moon.enabled", true)) {
            return;
        }

        final World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (world == null) {
            return;
        }

        final long time = world.getTime();
        if (active && time < DAWN_TICK) {
            active = false;
            Bukkit.getServer().broadcast(messageManager.getMessage(
                    "blood-moon-ended",
                    "<gray>🌙 A vérhold lenyugodott — a világ fellélegzik.</gray>"
            ));
            return;
        }

        if (active) {
            return;
        }

        final long day = world.getFullTime() / 24000L;
        if (time < NIGHT_START_TICK || time > ROLL_WINDOW_END_TICK || day == lastRolledDay) {
            return;
        }

        lastRolledDay = day;
        final double chancePercent = Math.max(0.0D, Math.min(100.0D,
                configManager.getDouble("world-events.blood-moon.chance-percent", 15.0D)));
        if (ThreadLocalRandom.current().nextDouble(100.0D) >= chancePercent) {
            return;
        }

        active = true;
        plugin.getLogger().info("Blood moon night started.");
        Bukkit.getServer().broadcast(messageManager.getMessage(
                "blood-moon-started",
                "<dark_red>🌕 VÉRHOLD! Ma éjjel a szörnyek erősebbek (+{bonus} szint), de a lelkük is értékesebb...</dark_red>",
                java.util.Map.of("bonus", String.valueOf(Math.max(0,
                        configManager.getInt("world-events.blood-moon.bonus-mob-levels", 2))))
        ));
        // Folia: startBloodMoon runs on the global region scheduler, so play each
        // player's sound on their own region thread (getLocation must be region-local).
        for (final Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(plugin, task -> player.playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.7F, 0.5F), null);
        }
    }
}
