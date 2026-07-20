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
    /**
     * A roll-ablak vége éjfél (18000): a lastRolledDay-őr miatt éjszakánként úgyis csak
     * egyszer sorsolunk, a széles ablak viszont garantálja, hogy a világesemény-tick
     * (world-events.check-interval-seconds, akár többperces érték) sose ugorja át az
     * ablakot — a korábbi 14400-as vég 70 mp-nél nagyobb intervallumnál kimaradó
     * vérholdakat okozott (audit-hiba).
     */
    private static final long ROLL_WINDOW_END_TICK = 18000L;
    private static final long DAWN_TICK = 12500L;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;

    private volatile boolean active;
    private volatile long lastRolledDay = -1L;
    // > 0 while an admin-forced blood moon is running; it ends on a timer rather than at dawn.
    private volatile long forcedEndAtMillis = 0L;
    /** B33: setter-injected finálé-eszkaláció (null = nincs finálé-szorzó). */
    private volatile SeasonFinaleManager seasonFinale;

    /** B33: a szezonzáró-eszkaláció bekötése (a finálé-manager később épül a DI-sorrendben). */
    public void setSeasonFinale(final SeasonFinaleManager seasonFinale) {
        this.seasonFinale = seasonFinale;
    }

    /** B19: az évszak-szorzó bekötése. */
    private volatile SeasonalModifierService seasonalModifiers;

    public void setSeasonalModifiers(final SeasonalModifierService seasonalModifiers) {
        this.seasonalModifiers = seasonalModifiers;
    }

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
     * Milliseconds left in the current blood moon, or -1 when inactive OR when a
     * natural (non-forced) blood moon is running: those end at dawn (world-time
     * bound, checked in {@link #tick()}) rather than at a stored millisecond
     * deadline, so no remaining-time estimate is available for them.
     *
     * @return remaining milliseconds, or -1 if unknown/inactive
     */
    public long getRemainingMillis() {
        return active && forcedEndAtMillis > 0L ? Math.max(0L, forcedEndAtMillis - System.currentTimeMillis()) : -1L;
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
            // A mid-event disable must still end the active window, or the mob-level and
            // soul-drop buffs stay stuck on until restart (sibling managers do the same).
            if (active) {
                endBloodMoon();
            }
            return;
        }

        final World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (world == null) {
            return;
        }

        final long time = world.getTime();
        if (active) {
            // Forced blood moons run on a timer; natural ones end at dawn.
            final boolean expired = forcedEndAtMillis > 0L
                    ? System.currentTimeMillis() >= forcedEndAtMillis
                    : time < DAWN_TICK;
            if (expired) {
                endBloodMoon();
            }
            return;
        }

        final long day = world.getFullTime() / 24000L;
        if (time < NIGHT_START_TICK || time > ROLL_WINDOW_END_TICK || day == lastRolledDay) {
            return;
        }

        lastRolledDay = day;
        // B33: a végítélet-hét alatt sűrűbb a vérhold (napi eszkalációs szorzó);
        // B19: télen (évszak-szorzó) amúgy is gyakoribb.
        final SeasonFinaleManager finaleRef = seasonFinale;
        final double finaleMult = finaleRef == null ? 1.0D : finaleRef.eventChanceMultiplier();
        final SeasonalModifierService seasonalRef = seasonalModifiers;
        final double seasonalMult = seasonalRef == null ? 1.0D : seasonalRef.chanceMultiplier("blood-moon");
        final double chancePercent = Math.max(0.0D, Math.min(100.0D,
                configManager.getDouble("world-events.blood-moon.chance-percent", 15.0D) * finaleMult * seasonalMult));
        if (ThreadLocalRandom.current().nextDouble(100.0D) >= chancePercent) {
            return;
        }

        forcedEndAtMillis = 0L;
        startBloodMoon();
    }

    /**
     * Admin override: starts a blood moon immediately for a configured duration
     * (independent of the day-night cycle). Safe to call from any thread.
     *
     * @return true if a blood moon was started (false if one is already running)
     */
    public boolean forceStart() {
        if (active) {
            return false;
        }
        final long minutes = Math.max(1L, configManager.getLong("world-events.blood-moon.force-duration-minutes", 10L));
        forcedEndAtMillis = System.currentTimeMillis() + (minutes * 60_000L);
        // A vérhold éjszakai esemény: admin-indításnál nappal is behozzuk az estét, hogy
        // a hangulat stimmeljen. A nap-éj ciklust Folián a globális régió birtokolja,
        // ezért az időállítás oda ütemeződik (a parancs a játékos régió-szálán fut).
        if (configManager.getBoolean("world-events.blood-moon.force-night", true)) {
            plugin.getServer().getGlobalRegionScheduler().run(plugin, task -> {
                final World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
                if (world != null && (world.getTime() < NIGHT_START_TICK || world.getTime() > 23000L)) {
                    world.setTime(NIGHT_START_TICK);
                }
            });
        }
        startBloodMoon();
        return true;
    }

    /**
     * Admin override: ends the current blood moon immediately.
     *
     * @return true if a blood moon was active and is now ended
     */
    public boolean forceEnd() {
        if (!active) {
            return false;
        }
        endBloodMoon();
        return true;
    }

    private void startBloodMoon() {
        active = true;
        plugin.getLogger().info("Blood moon night started.");
        Bukkit.getServer().broadcast(messageManager.getMessage(
                "blood-moon-started",
                "<dark_red>🌕 VÉRHOLD! Ma éjjel a szörnyek erősebbek (+{bonus} szint), de a lelkük is értékesebb...</dark_red>",
                java.util.Map.of("bonus", String.valueOf(Math.max(0,
                        configManager.getInt("world-events.blood-moon.bonus-mob-levels", 2))))
        ));
        // Folia: play each player's sound on their own region thread (getLocation must be region-local).
        for (final Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(plugin, task -> player.playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.7F, 0.5F), null);
        }
    }

    private void endBloodMoon() {
        active = false;
        forcedEndAtMillis = 0L;
        Bukkit.getServer().broadcast(messageManager.getMessage(
                "blood-moon-ended",
                "<gray>🌙 A vérhold lenyugodott — a világ fellélegzik.</gray>"
        ));
    }
}
