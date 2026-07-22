package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hadi-ablak (gameplay-audit 2. lelet): időzített ablak, amely alatt a RED↔BLUE
 * ölés NEM bűn — szentesített hadicselekmény, ami liga-pontot ér ("war" forrás).
 * A hétköznapi bűn-rendszer így nem darálja a hadviselő frakciók játékosait a
 * DARK-ba, a háborúnak mégis megvan a maga ideje és tétje. A vérdíj-rendszer a
 * hadviselő felek közt az ablak alatt szünetel (a hadijog felülírja), minden más
 * páros (NEUTRAL/DARK érintett) kill a normál bűn-úton marad.
 *
 * <p>Ütemezés: {@code factions.war-window.schedule} sorai "DAYOFWEEK HH:MM-HH:MM"
 * formában (pl. "SATURDAY 18:00-20:00"), szerver-helyi idő szerint. Admin-parancs
 * (/faction war start [perc]) soron kívül is nyithat. Farm-fék: ölésenként
 * per-pár cooldown + napi pont-plafon ölőnként. Minden kulcs élőben olvasódik.
 */
public final class WarWindowManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final SeasonManager seasonManager;
    private final NamespacedKey warDayKey;
    private final NamespacedKey warPointsKey;

    /** Admin-nyitott ablak vége (0 = nincs kényszerített ablak). */
    private volatile long forcedUntil;
    /** Az előző tick állapota — a nyit/zár broadcast átmenet-figyeléséhez. */
    private volatile boolean lastActive;
    /** Farm-fék: "killer:victim" pár → az utolsó jóváírt ölés időbélyege. */
    private final Map<String, Long> pairCooldowns = new ConcurrentHashMap<>();

    private volatile CombatTagManager combatTagManagerRef;

    public void setCombatTagManager(final CombatTagManager combatTagManager) {
        this.combatTagManagerRef = combatTagManager;
    }

    public WarWindowManager(final JavaPlugin plugin, final ConfigManager configManager,
                            final MessageManager messageManager, final SeasonManager seasonManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.seasonManager = seasonManager;
        this.warDayKey = new NamespacedKey(plugin, "war_day");
        this.warPointsKey = new NamespacedKey(plugin, "war_points_today");
    }

    public boolean isEnabled() {
        return configManager.getBoolean("factions.war-window.enabled", true);
    }

    /** Nyitva-e most a hadi-ablak (menetrend VAGY admin-nyitás). */
    public boolean isActive() {
        if (!isEnabled()) {
            return false;
        }
        if (System.currentTimeMillis() < forcedUntil) {
            return true;
        }
        return scheduleActive(LocalDateTime.now());
    }

    /** A menetrend-sorok kiértékelése ("DAYOFWEEK HH:MM-HH:MM", szerver-helyi idő). */
    private boolean scheduleActive(final LocalDateTime now) {
        for (final String entry : configManager.getStringList("factions.war-window.schedule")) {
            try {
                final String[] parts = entry.trim().split("\\s+");
                if (parts.length != 2) {
                    continue;
                }
                if (DayOfWeek.valueOf(parts[0].toUpperCase(Locale.ROOT)) != now.getDayOfWeek()) {
                    continue;
                }
                final String[] range = parts[1].split("-");
                if (range.length != 2) {
                    continue;
                }
                final int nowMinutes = now.getHour() * 60 + now.getMinute();
                if (nowMinutes >= parseMinutes(range[0]) && nowMinutes < parseMinutes(range[1])) {
                    return true;
                }
            } catch (final RuntimeException ignored) {
                // Hibás menetrend-sor — kihagyjuk (a többi sor még élhet).
            }
        }
        return false;
    }

    private static int parseMinutes(final String hhmm) {
        final String[] parts = hhmm.split(":");
        return Integer.parseInt(parts[0].trim()) * 60 + (parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0);
    }

    /** Nyit/zár átmenet broadcastja — a world-events global tick hívja. */
    public void tick() {
        final boolean active = isActive();
        if (active == lastActive) {
            return;
        }
        lastActive = active;
        if (active) {
            Bukkit.getServer().broadcast(messageManager.getMessage(
                    "war-window-opened",
                    "<dark_red>⚔ HADI-ABLAK NYÍLT! A Láng és a Fagy közt most nem bűn az ölés — "
                            + "a hadicselekmények liga-pontot érnek. Fegyverbe!</dark_red>"));
        } else {
            Bukkit.getServer().broadcast(messageManager.getMessage(
                    "war-window-closed",
                    "<gray>🕊 A hadi-ablak bezárult — a fegyverszünet visszaáll, az ölés újra bűn.</gray>"));
        }
    }

    /** Admin: soron kívüli ablak-nyitás. false, ha már nyitva van. */
    public boolean forceStart(final long minutes) {
        if (isActive()) {
            return false;
        }
        forcedUntil = System.currentTimeMillis() + Math.max(1L, minutes) * 60_000L;
        return true;
    }

    /** Admin: a kényszerített ablak azonnali zárása. false, ha nem admin-ablak fut. */
    public boolean forceEnd() {
        if (System.currentTimeMillis() >= forcedUntil) {
            return false;
        }
        forcedUntil = 0L;
        return true;
    }

    /** A menetrend szerinti KÖVETKEZŐ nyitás percekben, vagy -1 (státusz-kijelzés). */
    public long minutesUntilNextWindow() {
        final LocalDateTime now = LocalDateTime.now();
        for (long offset = 0; offset < 7L * 24L * 60L; offset += 5L) {
            if (scheduleActive(now.plusMinutes(offset))) {
                return offset;
            }
        }
        return -1L;
    }

    /**
     * Szentesített hadi-ölés-e a páros: nyitott ablak alatt RED↔BLUE (ellenkező
     * oldali) ölés. Minden más páros a normál bűn-úton marad.
     */
    public boolean isSanctionedWarKill(final FactionType killerFaction, final FactionType victimFaction) {
        if (!isActive() || killerFaction == victimFaction) {
            return false;
        }
        final boolean killerBelligerent = killerFaction == FactionType.RED || killerFaction == FactionType.BLUE;
        final boolean victimBelligerent = victimFaction == FactionType.RED || victimFaction == FactionType.BLUE;
        return killerBelligerent && victimBelligerent;
    }

    /**
     * Hadi-ölés elszámolása: liga-pont a "war" forráson, farm-fékekkel (per-pár
     * cooldown + napi plafon ölőnként). A HÍVÓ felelőssége, hogy a killer SAJÁT
     * régió-szálán fusson (PDC-írás) — minta: SinListener scheduler-hop.
     */
    public void handleWarKill(final Player killer, final java.util.UUID victimId, final FactionType killerFaction) {
        final long now = System.currentTimeMillis();
        // Per-pár cooldown: ugyanazt az áldozatot csak ritkán ér pontra váltani.
        final long pairCooldownMillis = Math.max(0L, configManager.getLong(
                "factions.war-window.per-victim-cooldown-minutes", 30L)) * 60_000L;
        final String pairKey = killer.getUniqueId() + ":" + victimId;
        if (pairCooldowns.size() > 512) {
            pairCooldowns.values().removeIf(stamp -> now - stamp > pairCooldownMillis);
        }
        final Long lastCounted = pairCooldowns.get(pairKey);
        final boolean pairFresh = lastCounted == null || now - lastCounted >= pairCooldownMillis;

        // Win-trade fék: pont csak TÉNYLEGES harc után jár — a pár első kontaktusától
        // min-fight-seconds-nek el kell telnie (az egy-ütéses "váltott ölés" nem harc).
        final CombatTagManager tags = this.combatTagManagerRef;
        if (tags != null && tags.isEnabled()
                && tags.pairFightSeconds(killer.getUniqueId(), victimId) < tags.minFightSeconds()) {
            killer.sendMessage(messageManager.getMessage(
                    "war-kill-too-quick",
                    "<gray>⚔ Szentesített hadi-ölés — de valódi összecsapás nélkül liga-pont nem jár.</gray>"));
            return;
        }

        // Napi pont-plafon (PDC nap-kulcs minta, a killer saját szálán).
        final long today = java.time.LocalDate.now().toEpochDay();
        final var pdc = killer.getPersistentDataContainer();
        if (pdc.getOrDefault(warDayKey, PersistentDataType.LONG, -1L) != today) {
            pdc.set(warDayKey, PersistentDataType.LONG, today);
            pdc.set(warPointsKey, PersistentDataType.INTEGER, 0);
        }
        final int cap = Math.max(0, configManager.getInt("factions.war-window.daily-point-cap", 5));
        final int counted = pdc.getOrDefault(warPointsKey, PersistentDataType.INTEGER, 0);

        if (pairFresh && counted < cap) {
            pairCooldowns.put(pairKey, now);
            pdc.set(warPointsKey, PersistentDataType.INTEGER, counted + 1);
            seasonManager.addPoints(killerFaction, Math.max(0,
                    configManager.getInt("factions.war-window.points-per-kill", 1)), "war");
            killer.sendMessage(messageManager.getMessage(
                    "war-kill-scored",
                    "<dark_red>⚔ Hadi-ölés jóváírva a(z) {faction} oldalán — liga-pont a háborúért! <gray>({count}/{cap} ma)</gray></dark_red>",
                    Map.of("faction", killerFaction.getDisplayName(),
                            "count", String.valueOf(counted + 1), "cap", String.valueOf(cap))));
        } else {
            killer.sendMessage(messageManager.getMessage(
                    "war-kill-unscored",
                    "<gray>⚔ Szentesített hadi-ölés — bűnöd nincs, de pont most nem jár "
                            + "(napi plafon vagy friss áldozat).</gray>"));
        }
    }
}
