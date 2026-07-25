package hu.taliann.icesmp.utils;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A játékosok játékmódjának konkurens tükre, UUID szerint.
 *
 * <p><b>Miért kell:</b> a jutalom-előszűrő ({@link MobKillUtil}) az ÁLDOZAT régió-szálán fut
 * (EntityDeathEvent), a gyilkos viszont másik régió-szálhoz tartozhat. A
 * {@code killer.getGameMode()} közvetlen olvasása így MÁSIK entitás érintése lenne egy idegen
 * szálról — és az előszűrő döntése nem odázható el, mert a listenerek a halál-eventben, helyben
 * döntik el, esik-e jutalom. A tükör feloldja az ellentmondást: az írás MINDIG a játékos saját
 * szálán történik (join / játékmód-váltás event), az olvasás pedig konkurens map-ből, bármely
 * szálról — entitás-érintés nélkül.
 *
 * <p><b>Fail-open:</b> ha nincs bejegyzés (pl. a játékos a plugin betöltése előtt lépett be),
 * a {@link #isSurvival} igazat ad — inkább fizessünk egy jutalmat, mint hogy némán elvonjuk.
 * Ugyanez a politika, mint a hiányzó AFK-adatnál.
 */
public final class GameModeCache {

    private static final Map<UUID, GameMode> MODES = new ConcurrentHashMap<>();

    private GameModeCache() {
    }

    /** Frissítés a játékos SAJÁT szálán (join, játékmód-váltás). */
    public static void update(final UUID playerId, final GameMode mode) {
        if (playerId != null && mode != null) {
            MODES.put(playerId, mode);
        }
    }

    /** Frissítés a játékos SAJÁT szálán — az event-handlerek kényelmi alakja. */
    public static void update(final Player player) {
        if (player != null) {
            update(player.getUniqueId(), player.getGameMode());
        }
    }

    /** Kilépéskor takarítandó, hogy a map ne szivárogjon. */
    public static void remove(final UUID playerId) {
        MODES.remove(playerId);
    }

    /**
     * Survival-e a játékos — BÁRMELY régió-szálról biztonságos.
     *
     * @return true, ha survival, vagy ha nincs adat (fail-open)
     */
    public static boolean isSurvival(final UUID playerId) {
        final GameMode mode = MODES.get(playerId);
        return mode == null || mode == GameMode.SURVIVAL;
    }
}
