package hu.taliann.icesmp.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Napi keret (anti-farm plafon) közös implementációja: nap-index számítás, számláló-léptetés,
 * plafon-vizsgálat és a régi bejegyzések söprése egy helyen.
 *
 * <p><b>Két tároló, mert a Folia rákényszerít.</b> A keret-számláló ott íródik, ahol a jutalom
 * születik, és ez nem mindig a játékos saját régió-szála:
 * <ul>
 *   <li>{@link InMemory} — kereszt-entitás eventekhez (mob-halál: az event a MOB szálán fut, a
 *       gyilkos PDC-je egy MÁSIK entitás, oda innen nem írhatunk). Újraindításkor nullázódik.</li>
 *   <li>{@link #tryConsumeOnOwnThread} — a játékos PDC-jében él, ezért újraindítás-biztos, de
 *       KIZÁRÓLAG a játékos saját régió-szálán hívható (horgászat, bolti vásárlás, parancs).</li>
 * </ul>
 *
 * <p>Mindkét ág <b>ellenőriz, majd könyvel</b>: az elutasított kísérlet NEM növeli a számlálót
 * (a régi, listenerenkénti implementációk egy része előbb könyvelt, ezért egyetlen túllépés
 * után a nap hátralévő részében a beleférő kisebb összegeket is elutasította).
 */
public final class DailyBudget {

    private DailyBudget() {
    }

    /** A mai nap indexe (UTC-napok az epoch óta) — a keretek erre a bucketre nullázódnak. */
    public static long dayIndex() {
        return System.currentTimeMillis() / 86_400_000L;
    }

    /**
     * A játékos PDC-jében tartott napi keret.
     *
     * <p><b>Folia:</b> a hívás a JÁTÉKOS saját régió-szálán kell történjen (a PDC-írás
     * entitás-mutáció). Kereszt-entitás eventben ({@code EntityDeathEvent}) használd az
     * {@link InMemory} tárolót helyette.
     *
     * @param budgetId a keret azonosítója (PDC-kulcs-előtag lesz, pl. {@code "windfall"})
     * @param cap      a napi plafon; {@code <= 0} = korlátlan (ilyenkor nem is könyvel)
     * @return true, ha az összeg belefért (és le is könyveltük)
     */
    public static boolean tryConsumeOnOwnThread(final Player player, final String budgetId,
                                                final double cap, final long amount) {
        if (player == null) {
            return false;
        }
        if (cap <= 0.0D) {
            return true;
        }
        final NamespacedKey dayKey = key(budgetId, "day");
        final NamespacedKey sumKey = key(budgetId, "sum");
        if (dayKey == null || sumKey == null) {
            return true; // Érvénytelen keret-id: fail-open, nem néma jutalom-elvonás.
        }
        final long today = dayIndex();
        final long storedDay = player.getPersistentDataContainer()
                .getOrDefault(dayKey, PersistentDataType.LONG, -1L);
        final long spent = storedDay == today
                ? player.getPersistentDataContainer().getOrDefault(sumKey, PersistentDataType.LONG, 0L)
                : 0L;
        if (spent + amount > (long) cap) {
            return false;
        }
        player.getPersistentDataContainer().set(dayKey, PersistentDataType.LONG, today);
        player.getPersistentDataContainer().set(sumKey, PersistentDataType.LONG, spent + amount);
        return true;
    }

    /** A játékos mai felhasznált kerete (0, ha ma még nem költött) — ugyanaz a szál-megkötés. */
    public static long spentTodayOnOwnThread(final Player player, final String budgetId) {
        final NamespacedKey dayKey = key(budgetId, "day");
        final NamespacedKey sumKey = key(budgetId, "sum");
        if (player == null || dayKey == null || sumKey == null) {
            return 0L;
        }
        final long storedDay = player.getPersistentDataContainer()
                .getOrDefault(dayKey, PersistentDataType.LONG, -1L);
        return storedDay == dayIndex()
                ? player.getPersistentDataContainer().getOrDefault(sumKey, PersistentDataType.LONG, 0L)
                : 0L;
    }

    private static NamespacedKey key(final String budgetId, final String suffix) {
        if (budgetId == null || budgetId.isBlank()) {
            return null;
        }
        final String sanitized = budgetId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        return NamespacedKey.fromString("icesmp:budget_" + sanitized + "_" + suffix);
    }

    /**
     * Memóriában élő napi keret — bármely régió-szálról hívható (ConcurrentHashMap), ezért
     * kereszt-entitás eventekben (mob-halál) ez az EGYETLEN Folia-biztos választás.
     * Újraindításkor nullázódik; anti-farm plafonként ez elfogadott kompromisszum.
     *
     * @param <K> a keret kulcsa (általában a játékos UUID-je; összetett keretnél pl. „uuid|pálya")
     */
    public static final class InMemory<K> {

        /** kulcs -> {nap-index, mai összeg} */
        private final Map<K, long[]> entries = new ConcurrentHashMap<>();
        private final int sweepAbove;

        /** @param sweepAbove ennyi bejegyzés fölött söpri a más-napi sorokat (memória-fék) */
        public InMemory(final int sweepAbove) {
            this.sweepAbove = Math.max(16, sweepAbove);
        }

        /**
         * @param cap napi plafon; {@code <= 0} = korlátlan
         * @return true, ha az összeg belefért (és le is könyveltük)
         */
        public boolean tryConsume(final K budgetKey, final long amount, final double cap) {
            if (budgetKey == null) {
                return false;
            }
            if (cap <= 0.0D) {
                return true;
            }
            final long today = dayIndex();
            if (entries.size() > sweepAbove) {
                entries.values().removeIf(entry -> entry[0] != today);
            }
            final long limit = (long) cap;
            // A döntés a compute-on BELÜL születik: a bin-lock miatt az ellenőrzés és a
            // könyvelés atomi, így két régió-szál nem csúszhat át egyszerre a plafonon.
            final boolean[] allowed = {false};
            entries.compute(budgetKey, (key, old) -> {
                final long spent = old != null && old[0] == today ? old[1] : 0L;
                if (spent + amount > limit) {
                    allowed[0] = false;
                    return new long[]{today, spent};
                }
                allowed[0] = true;
                return new long[]{today, spent + amount};
            });
            return allowed[0];
        }

        /** @return a kulcs mai felhasznált kerete */
        public long spentToday(final K budgetKey) {
            final long[] entry = budgetKey == null ? null : entries.get(budgetKey);
            return entry != null && entry[0] == dayIndex() ? entry[1] : 0L;
        }
    }
}
