package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Scheduled RED↔BLUE war window with durable per-player daily scoring budget. */
public final class WarWindowManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final SeasonManager seasonManager;
    private volatile long forcedUntil;
    private volatile boolean lastActive;
    private long windowSequence;
    private long activeWindowId;
    /** Runtime-only anti-win-trade pair throttle. */
    private final Map<String, Long> pairCooldowns = new ConcurrentHashMap<>();
    private volatile CombatTagManager combatTagManagerRef;

    public WarWindowManager(final JavaPlugin plugin, final ConfigManager configManager,
                            final MessageManager messageManager,
                            final SeasonManager seasonManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.seasonManager = seasonManager;
    }

    public void setCombatTagManager(final CombatTagManager combatTagManager) {
        this.combatTagManagerRef = combatTagManager;
    }

    public boolean isEnabled() {
        return configManager.getBoolean("factions.war-window.enabled", true);
    }

    public boolean isActive() {
        if (!isEnabled()) return false;
        if (System.currentTimeMillis() < forcedUntil) return true;
        return scheduleActive(LocalDateTime.now());
    }

    private boolean scheduleActive(final LocalDateTime now) {
        for (final String entry : configManager.getStringList("factions.war-window.schedule")) {
            try {
                final String[] parts = entry.trim().split("\\s+");
                if (parts.length != 2
                        || DayOfWeek.valueOf(parts[0].toUpperCase(Locale.ROOT))
                        != now.getDayOfWeek()) continue;
                final String[] range = parts[1].split("-");
                if (range.length != 2) continue;
                final int current = now.getHour() * 60 + now.getMinute();
                if (current >= parseMinutes(range[0]) && current < parseMinutes(range[1]))
                    return true;
            } catch (final RuntimeException ignored) { }
        }
        return false;
    }

    private static int parseMinutes(final String hhmm) {
        final String[] parts = hhmm.split(":");
        return Integer.parseInt(parts[0].trim()) * 60
                + (parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0);
    }

    public void tick() {
        final boolean active = isActive();
        refreshRewardWindow(active);
        if (active == lastActive) return;
        lastActive = active;
        Bukkit.getServer().broadcast(messageManager.getMessage(
                active ? "war-window-opened" : "war-window-closed",
                active
                        ? "<dark_red>⚔ HADI-ABLAK NYÍLT! A Láng és a Fagy közt most nem bűn az ölés — a hadicselekmények liga-pontot érnek. Fegyverbe!</dark_red>"
                        : "<gray>🕊 A hadi-ablak bezárult — a fegyverszünet visszaáll, az ölés újra bűn.</gray>"));
    }

    public boolean forceStart(final long minutes) {
        if (isActive()) return false;
        forcedUntil = System.currentTimeMillis() + Math.max(1L, minutes) * 60_000L;
        refreshRewardWindow(true);
        return true;
    }

    public boolean forceEnd() {
        if (System.currentTimeMillis() >= forcedUntil) return false;
        forcedUntil = 0L;
        refreshRewardWindow(isActive());
        return true;
    }

    public long minutesUntilNextWindow() {
        final LocalDateTime now = LocalDateTime.now();
        for (long offset = 0; offset < 7L * 24L * 60L; offset += 5L)
            if (scheduleActive(now.plusMinutes(offset))) return offset;
        return -1L;
    }

    public synchronized long rewardWindowToken() {
        refreshRewardWindow(isActive());
        return activeWindowId;
    }

    public synchronized boolean isCurrentRewardWindow(final long token) {
        refreshRewardWindow(isActive());
        return token > 0L && token == activeWindowId;
    }

    private synchronized void refreshRewardWindow(final boolean active) {
        if (!active) activeWindowId = 0L;
        else if (activeWindowId == 0L) {
            windowSequence = windowSequence == Long.MAX_VALUE ? 1L : windowSequence + 1L;
            activeWindowId = windowSequence;
        }
    }

    public boolean isSanctionedWarKill(final FactionType killerFaction,
                                       final FactionType victimFaction) {
        if (!isActive() || killerFaction == victimFaction) return false;
        final boolean killerBelligerent = killerFaction == FactionType.RED
                || killerFaction == FactionType.BLUE;
        final boolean victimBelligerent = victimFaction == FactionType.RED
                || victimFaction == FactionType.BLUE;
        return killerBelligerent && victimBelligerent;
    }

    public void handleWarKill(final Player killer, final java.util.UUID victimId,
                              final FactionType killerFaction) {
        final long now = System.currentTimeMillis();
        final long pairCooldownMillis = Math.max(0L, configManager.getLong(
                "factions.war-window.per-victim-cooldown-minutes", 30L)) * 60_000L;
        final String pairKey = killer.getUniqueId() + ":" + victimId;
        if (pairCooldowns.size() > 512)
            pairCooldowns.values().removeIf(stamp -> now - stamp > pairCooldownMillis);
        final Long lastCounted = pairCooldowns.get(pairKey);
        final boolean pairFresh = lastCounted == null
                || now - lastCounted >= pairCooldownMillis;

        final CombatTagManager tags = combatTagManagerRef;
        if (tags != null && tags.isEnabled()
                && tags.pairFightSeconds(killer.getUniqueId(), victimId)
                < tags.minFightSeconds()) {
            killer.sendMessage(messageManager.getMessage("war-kill-too-quick",
                    "<gray>⚔ Szentesített hadi-ölés — de valódi összecsapás nélkül liga-pont nem jár.</gray>"));
            return;
        }

        final int cap = Math.max(0,
                configManager.getInt("factions.war-window.daily-point-cap", 5));
        final long counted = hu.taliann.icesmp.utils.DailyBudget
                .spentTodayOnOwnThread(killer, "war_points");
        final boolean budgetAllowed = pairFresh
                && hu.taliann.icesmp.utils.DailyBudget.tryConsumeOnOwnThread(
                        killer, "war_points", cap, 1L);
        if (budgetAllowed) {
            pairCooldowns.put(pairKey, now);
            seasonManager.addPoints(killerFaction, Math.max(0,
                    configManager.getInt("factions.war-window.points-per-kill", 1)), "war");
            killer.sendMessage(messageManager.getMessage("war-kill-scored",
                    "<dark_red>⚔ Hadi-ölés jóváírva a(z) {faction} oldalán — liga-pont a háborúért! <gray>({count}/{cap} ma)</gray></dark_red>",
                    Map.of("faction", killerFaction.getDisplayName(),
                            "count", Long.toString(counted + 1L),
                            "cap", Integer.toString(cap))));
        } else {
            killer.sendMessage(messageManager.getMessage("war-kill-unscored",
                    "<gray>⚔ Szentesített hadi-ölés — bűnöd nincs, de pont most nem jár (napi plafon vagy friss áldozat).</gray>"));
        }
    }
}
