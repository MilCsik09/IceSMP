package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.session.PlayerStateCleanup;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * G6 — becsület-párbaj: a bűnös játékos elégtételt ajánlhat — beleegyezéses
 * párbaj, és ha a BŰNÖS nyer, egy bűnpontja letörlődik. Heti limit (PDC) fékezi
 * az alt-exploitot; a párbaj-ölés nem termel bűnt és nem fizet vérdíjat (a
 * SinListener kérdezi le a kill pillanatában). Állapot: volatilis mapek
 * (kihívás + aktív pár + lejárat), kilépéskor takarítva. Folia: minden hívás
 * esemény-/parancs-szálról jön; a mapek konkurrens szerkezetek.
 */
public final class HonorDuelManager implements PlayerStateCleanup {

    private final ConfigManager configManager;
    private final SinManager sinManager;
    private final FactionManager factionManager;
    private final SeasonManager seasonManager;
    /** kihívott -> kihívó (függő felkérés). */
    private final Map<UUID, UUID> pending = new ConcurrentHashMap<>();
    /** résztvevő -> ellenfél (mindkét irányban felvéve). */
    private final Map<UUID, UUID> active = new ConcurrentHashMap<>();
    private final Map<UUID, Long> endsAt = new ConcurrentHashMap<>();
    private final NamespacedKey weekKey;
    private final NamespacedKey countKey;

    public HonorDuelManager(final JavaPlugin plugin, final ConfigManager configManager,
                            final SinManager sinManager, final FactionManager factionManager,
                            final SeasonManager seasonManager) {
        this.configManager = configManager;
        this.sinManager = sinManager;
        this.factionManager = factionManager;
        this.seasonManager = seasonManager;
        this.weekKey = new NamespacedKey(plugin, "honor_duel_week");
        this.countKey = new NamespacedKey(plugin, "honor_duel_count");
    }

    /** Kihívás; hibakulcs vagy null. Csak bűnös hívhat, heti limittel. */
    public String challenge(final Player challenger, final Player target) {
        if (!configManager.getBoolean("honor-duel.enabled", true)) {
            return "duel-disabled";
        }
        if (sinManager.getSinCount(challenger) <= 0) {
            return "duel-not-sinner";
        }
        if (challenger.getUniqueId().equals(target.getUniqueId())) {
            return "duel-self";
        }
        if (active.containsKey(challenger.getUniqueId()) || active.containsKey(target.getUniqueId())) {
            return "duel-busy";
        }
        final long week = System.currentTimeMillis() / (7L * 86_400_000L);
        final long storedWeek = challenger.getPersistentDataContainer()
                .getOrDefault(weekKey, PersistentDataType.LONG, -1L);
        final int used = storedWeek == week ? challenger.getPersistentDataContainer()
                .getOrDefault(countKey, PersistentDataType.INTEGER, 0) : 0;
        if (used >= Math.max(1, configManager.getInt("honor-duel.weekly-limit", 2))) {
            return "duel-limit";
        }
        pending.put(target.getUniqueId(), challenger.getUniqueId());
        return null;
    }

    /** Elfogadás; hibakulcs vagy null. Indul a párbaj-ablak. */
    public String accept(final Player target) {
        final UUID challengerId = pending.remove(target.getUniqueId());
        final Player challenger = challengerId == null ? null : Bukkit.getPlayer(challengerId);
        if (challenger == null) {
            return "duel-no-challenge";
        }
        final long week = System.currentTimeMillis() / (7L * 86_400_000L);
        // A limit-számláló elfogadáskor fogy (a fel nem vett kihívás nem számít bele).
        challenger.getPersistentDataContainer().set(weekKey, PersistentDataType.LONG, week);
        final int used = challenger.getPersistentDataContainer()
                .getOrDefault(countKey, PersistentDataType.INTEGER, 0);
        challenger.getPersistentDataContainer().set(countKey, PersistentDataType.INTEGER, used + 1);
        final long end = System.currentTimeMillis()
                + Math.max(30, configManager.getInt("honor-duel.window-seconds", 180)) * 1000L;
        active.put(challenger.getUniqueId(), target.getUniqueId());
        active.put(target.getUniqueId(), challenger.getUniqueId());
        endsAt.put(challenger.getUniqueId(), end);
        endsAt.put(target.getUniqueId(), end);
        return null;
    }

    public boolean declined(final Player target) {
        return pending.remove(target.getUniqueId()) != null;
    }

    /** Élő párbaj-pár-e a kettő (lejárat-ellenőrzéssel). */
    public boolean isDuelPair(final UUID a, final UUID b) {
        final Long end = endsAt.get(a);
        if (end == null || System.currentTimeMillis() > end) {
            clearPair(a);
            return false;
        }
        return b.equals(active.get(a));
    }

    /**
     * Párbaj-ölés lezárása (a SinListener hívja a kill szálán): ha a győztes
     * bűnös, egy bűnpontja letörlődik. Igaz = ez párbaj-kill volt (nincs bűn/vérdíj).
     */
    public boolean settleKill(final Player killer, final Player victim) {
        if (!isDuelPair(killer.getUniqueId(), victim.getUniqueId())) {
            return false;
        }
        clearPair(killer.getUniqueId());
        if (sinManager.getSinCount(killer) > 0) {
            sinManager.reduceSin(killer, 1);
        }
        // Aszimmetrikus liga: a párbaj-győzelem liga-pontot ér ("duel" forrás — a
        // kitaszított becsület-visszaszerzés a DARK identitás-útja a súlymátrixban).
        seasonManager.addPoints(factionManager.getFaction(killer.getUniqueId()),
                Math.max(0, configManager.getInt("honor-duel.season-points", 2)), "duel");
        return true;
    }

    private void clearPair(final UUID any) {
        final UUID other = active.remove(any);
        endsAt.remove(any);
        if (other != null) {
            active.remove(other);
            endsAt.remove(other);
        }
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        pending.remove(playerId);
        pending.values().remove(playerId);
        clearPair(playerId);
    }
}
