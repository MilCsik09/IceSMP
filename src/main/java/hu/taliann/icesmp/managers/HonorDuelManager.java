package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.FactionType;
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

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final SinManager sinManager;
    private final FactionManager factionManager;
    private final SeasonManager seasonManager;
    /** kihívott -> kihívó (függő felkérés). */
    private final Map<UUID, UUID> pending = new ConcurrentHashMap<>();
    /** kihívott -> a felkérés lejárata (a spam/néma-felülírás fékje). */
    private final Map<UUID, Long> pendingExpiry = new ConcurrentHashMap<>();
    /** résztvevő -> ellenfél (mindkét irányban felvéve). */
    private final Map<UUID, UUID> active = new ConcurrentHashMap<>();
    private final Map<UUID, Long> endsAt = new ConcurrentHashMap<>();
    private final NamespacedKey weekKey;
    private final NamespacedKey countKey;

    public HonorDuelManager(final JavaPlugin plugin, final ConfigManager configManager,
                            final SinManager sinManager, final FactionManager factionManager,
                            final SeasonManager seasonManager) {
        this.plugin = plugin;
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
        // Élő, még le nem járt felkérést nem írunk felül némán (spam-fék).
        final long now = System.currentTimeMillis();
        final Long existing = pendingExpiry.get(target.getUniqueId());
        if (existing != null && existing > now) {
            return "duel-pending";
        }
        // Egy kihívónak egyszerre csak EGY kimenő felkérése lehet — különben két
        // közel-egyidejű elfogadás felülírná egymást az active-mapben, és az első
        // célpont egy már nem élő párbajra hivatkozva ölhetne bűn-mentesen.
        if (pending.containsValue(challenger.getUniqueId())) {
            return "duel-pending";
        }
        pending.put(target.getUniqueId(), challenger.getUniqueId());
        pendingExpiry.put(target.getUniqueId(), now
                + Math.max(10, configManager.getInt("honor-duel.challenge-expiry-seconds", 60)) * 1000L);
        return null;
    }

    /** Elfogadás; hibakulcs vagy null. Indul a párbaj-ablak. */
    public String accept(final Player target) {
        final UUID challengerId = pending.remove(target.getUniqueId());
        final Long expiry = pendingExpiry.remove(target.getUniqueId());
        final Player challenger = challengerId == null ? null : Bukkit.getPlayer(challengerId);
        if (challenger == null || expiry == null || expiry < System.currentTimeMillis()) {
            return "duel-no-challenge";
        }
        // Az elfogadás pillanatában is friss állapotot követelünk: ha a kihívó időközben
        // már aktív párbajban áll, ez a pár nem jöhet létre (invariáns-védelem).
        if (active.containsKey(challenger.getUniqueId()) || active.containsKey(target.getUniqueId())) {
            return "duel-busy";
        }
        final long week = System.currentTimeMillis() / (7L * 86_400_000L);
        // A limit-számláló elfogadáskor fogy (a fel nem vett kihívás nem számít bele).
        // Folia: az accept az ELFOGADÓ szálán fut — a kihívó PDC-jét a SAJÁT
        // régió-szálán írjuk (cross-region PDC-írás tilos).
        challenger.getScheduler().run(plugin, task -> {
            challenger.getPersistentDataContainer().set(weekKey, PersistentDataType.LONG, week);
            final int used = challenger.getPersistentDataContainer()
                    .getOrDefault(countKey, PersistentDataType.INTEGER, 0);
            challenger.getPersistentDataContainer().set(countKey, PersistentDataType.INTEGER, used + 1);
        }, null);
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
        // Aszimmetrikus liga: a párbaj-győzelem liga-pontot ér ("duel" forrás), de CSAK
        // KÜLÖNBÖZŐ frakciójú felek közt — az azonos-frakciós "baráti bemutató"
        // (megrendezett pont-farm) nem ér pontot, a bűn-törlés viszont ott is jár.
        final FactionType killerFaction = factionManager.getFaction(killer.getUniqueId());
        final FactionType victimFaction = factionManager.getFaction(victim.getUniqueId());
        if (killerFaction != null && killerFaction != victimFaction) {
            seasonManager.addPoints(killerFaction,
                    Math.max(0, configManager.getInt("honor-duel.season-points", 2)), "duel");
        }
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
        pendingExpiry.remove(playerId);
        // A kilépő KIMENŐ kihívása: a target-kulcsú párt (pending + expiry) együtt takarítjuk.
        pending.entrySet().removeIf(entry -> {
            if (playerId.equals(entry.getValue())) {
                pendingExpiry.remove(entry.getKey());
                return true;
            }
            return false;
        });
        clearPair(playerId);
    }
}
