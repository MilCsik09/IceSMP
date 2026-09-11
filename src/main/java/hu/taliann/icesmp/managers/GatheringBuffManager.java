package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gathering buff windows: time-limited, server-wide
 * bonus periods that reward the everyday grind without touching the economy —
 * only raw materials and XP, never currency. Each window is one of a handful of
 * flavours; while it is open the {@link hu.taliann.icesmp.listeners.GatheringBuffListener}
 * applies the matching bonus (extra ore/crop drops, a better fishing haul, or an
 * XP multiplier).
 *
 * <p>The tick runs on the global region scheduler; it only flips flags and
 * broadcasts, so it needs no region hop.
 */
public final class GatheringBuffManager {

    /** The buff flavours; each is individually toggleable in config. */
    public enum GatheringBuff {
        MINING_RUSH,
        HARVEST_HOUR,
        FISHING_FRENZY,
        XP_HOUR;

        String configKey() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;

    public record Window(java.util.UUID instanceId, GatheringBuff buff, long expiresAt, boolean sandbox) {
        public Window { java.util.Objects.requireNonNull(instanceId); java.util.Objects.requireNonNull(buff); if (expiresAt < 1) throw new IllegalArgumentException("Invalid event expiry"); }
        public boolean rewardsEnabled(long now) { return !sandbox && now < expiresAt; }
    }
    private volatile Window window;
    public Window activeWindow() { return window; }
    public java.util.Set<GatheringBuff> available() {
        final java.util.Set<GatheringBuff> enabled = java.util.EnumSet.noneOf(GatheringBuff.class);
        if (configManager.getBoolean("gathering-buffs.enabled", true)) for (GatheringBuff buff : GatheringBuff.values())
            if (configManager.getBoolean("gathering-buffs.types." + buff.configKey(), true)) enabled.add(buff);
        return java.util.Set.copyOf(enabled);
    }
    /** Native lifecycle admission; instance identity and reward policy publish atomically. */
    public synchronized boolean startControlled(java.util.UUID instance, GatheringBuff buff, boolean sandbox) {
        if (!Bukkit.isGlobalTickThread()) throw new IllegalStateException("Event lifecycle requires global owner");
        if (window != null || !available().contains(buff)) return false;
        start(buff, java.util.Objects.requireNonNull(instance), sandbox); return true;
    }
    public synchronized boolean stopExpected(java.util.UUID instance, boolean sandboxOnly) {
        if (!Bukkit.isGlobalTickThread()) throw new IllegalStateException("Event lifecycle requires global owner");
        final Window current = window;
        if (current == null || !current.instanceId().equals(instance) || sandboxOnly && !current.sandbox()) return false;
        end(); return true;
    }
    private volatile long nextAttemptAt;

    public GatheringBuffManager(final JavaPlugin plugin, final ConfigManager configManager,
                                final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.nextAttemptAt = System.currentTimeMillis() + intervalMillis();
    }

    /** The currently-open buff, or null if none. */
    public GatheringBuff getActive() {
        final Window current = window; return current == null ? null : current.buff();
    }

    /** Milliseconds left in the current buff window, or -1 when none is open. */
    public long getRemainingMillis() {
        final Window current = window; return current != null ? Math.max(0L, current.expiresAt() - System.currentTimeMillis()) : -1L;
    }

    /** XP multiplier to apply right now (1.0 unless an XP hour is open). */
    public double xpMultiplier() {
        final Window current = window;
        return current != null && current.rewardsEnabled(System.currentTimeMillis()) && current.buff() == GatheringBuff.XP_HOUR
                ? Math.max(1.0D, configManager.getDouble("gathering-buffs.xp-multiplier", 2.0D))
                : 1.0D;
    }

    /** Chance (0–1) for a bonus drop while the given buff is open, else 0. */
    public double bonusDropChance(final GatheringBuff buff) {
        final Window current = window;
        if (current == null || !current.rewardsEnabled(System.currentTimeMillis()) || current.buff() != buff) {
            return 0.0D;
        }
        final double percent = Math.max(0.0D, Math.min(100.0D,
                configManager.getDouble("gathering-buffs.bonus-drop-chance-percent", 50.0D)));
        return percent / 100.0D;
    }

    /** Periodic driver on the global world-events tick. */
    public synchronized void tick() {
        if (!configManager.getBoolean("gathering-buffs.enabled", true)) {
            if (window != null) {
                end();
            }
            return;
        }

        final long now = System.currentTimeMillis();
        if (window != null) {
            if (now >= window.expiresAt()) {
                end();
            }
            return;
        }

        if (now >= nextAttemptAt) {
            nextAttemptAt = now + intervalMillis();
            // Üres (vagy majdnem üres) szerveren ne nyíljon buff-ablak a senkinek.
            if (org.bukkit.Bukkit.getOnlinePlayers().size()
                    < Math.max(0, configManager.getInt("gathering-buffs.min-online-players", 1))) {
                return;
            }
            // Évszak-szorzó (nyáron sűrűbb a gyűjtögető-láz — season-modifiers.<evszak>.gathering).
            final SeasonalModifierService seasonalRef = seasonalModifiers;
            final double seasonalMult = seasonalRef == null ? 1.0D : seasonalRef.chanceMultiplier("gathering");
            final double chance = Math.max(0.0D, Math.min(100.0D,
                    configManager.getDouble("gathering-buffs.chance-percent", 40.0D) * seasonalMult));
            if (ThreadLocalRandom.current().nextDouble(100.0D) < chance) {
                start(pickRandomEnabled());
            }
        }
    }

    /** B19: az évszak-szorzó bekötése. */
    private volatile SeasonalModifierService seasonalModifiers;

    public void setSeasonalModifiers(final SeasonalModifierService seasonalModifiers) {
        this.seasonalModifiers = seasonalModifiers;
    }

    /** Hungarian label of the active buff window (menu display), or null when none. */
    public String describeActive() {
        final GatheringBuff current = getActive();
        if (current == null) {
            return null;
        }
        return switch (current) {
            case MINING_RUSH -> "Bányász-láz";
            case HARVEST_HOUR -> "Termés-óra";
            case FISHING_FRENZY -> "Horgász-láz";
            case XP_HOUR -> "Tapasztalat-óra";
        };
    }

    /** Admin override: opens a random enabled buff now. Returns false if none are enabled or one is open. */
    public synchronized boolean forceRandom() {
        if (window != null) {
            return false;
        }
        final GatheringBuff buff = pickRandomEnabled();
        if (buff == null) {
            return false;
        }
        start(buff);
        return true;
    }

    private GatheringBuff pickRandomEnabled() {
        final List<GatheringBuff> enabled = new ArrayList<>();
        for (final GatheringBuff buff : GatheringBuff.values()) {
            if (configManager.getBoolean("gathering-buffs.types." + buff.configKey(), true)) {
                enabled.add(buff);
            }
        }
        return enabled.isEmpty() ? null : enabled.get(ThreadLocalRandom.current().nextInt(enabled.size()));
    }

    private void start(final GatheringBuff buff) { start(buff, java.util.UUID.randomUUID(), false); }
    private void start(final GatheringBuff buff, final java.util.UUID instance, final boolean sandbox) {
        if (buff == null) {
            return;
        }
        window = new Window(instance, buff, System.currentTimeMillis() + (sandbox ? 120_000 : durationMillis()), sandbox);
        if (sandbox) return;
        final long minutes = Math.max(1L, durationMillis() / 60_000L);
        Bukkit.getServer().broadcast(messageManager.getMessage(
                startKey(buff), startDefault(buff),
                java.util.Map.of("minutes", String.valueOf(minutes))));
    }

    private void end() {
        final Window previous = window; window = null;
        final GatheringBuff ended = previous == null ? null : previous.buff();
        if (ended != null && !previous.sandbox()) {
            Bukkit.getServer().broadcast(messageManager.getMessage(endKey(ended), endDefault(ended)));
        }
    }

    private static String startKey(final GatheringBuff buff) {
        return "gathering-" + buff.configKey() + "-start";
    }

    private static String endKey(final GatheringBuff buff) {
        return "gathering-" + buff.configKey() + "-end";
    }

    private static String startDefault(final GatheringBuff buff) {
        return switch (buff) {
            case MINING_RUSH -> "&e⛏ BÁNYÁSZ-LÁZ! A következő {minutes} percben az érc-blokkok bónusz nyersanyagot adhatnak.";
            case HARVEST_HOUR -> "&a🌾 TERMÉS-ÓRA! A következő {minutes} percben a beérett termés bónusz hozamot adhat.";
            case FISHING_FRENZY -> "&b🎣 HORGÁSZ-LÁZ! A következő {minutes} percben nagyobb a víz zsákmánya.";
            case XP_HOUR -> "&d✦ TAPASZTALAT-ÓRA! A következő {minutes} percben több XP-t kapsz mindenből.";
        };
    }

    private static String endDefault(final GatheringBuff buff) {
        return switch (buff) {
            case MINING_RUSH -> "&7⛏ A bányász-láz alábbhagyott.";
            case HARVEST_HOUR -> "&7🌾 A termés-óra véget ért.";
            case FISHING_FRENZY -> "&7🎣 A halászati láz elcsendesedett.";
            case XP_HOUR -> "&7✦ A tapasztalat-óra lejárt.";
        };
    }

    private long intervalMillis() {
        return Math.max(1L, configManager.getLong("gathering-buffs.interval-minutes", 60L)) * 60_000L;
    }

    private long durationMillis() {
        return Math.max(1L, configManager.getLong("gathering-buffs.duration-minutes", 15L)) * 60_000L;
    }
}
