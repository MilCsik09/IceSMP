package hu.taliann.icesmp.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.UUID;

/**
 * Világesemények mulandó (nem perzisztens) entitásainak eltávolítása — a világesemény-managerek
 * UUID-val követik a maguk mobjait/display-eit, és a despawn Folia-helyes elvégzése minden
 * hívási helyen ugyanaz a néhány sor volt.
 *
 * <p><b>Miért két metódus?</b> Futás közben az eltávolítás az entitás SAJÁT régió-szálán kell
 * történjen ({@link #removeById}). Leállításkor viszont a schedulerek már állnak — ott a hop
 * maga dobna, ezért a {@link #removeAllOnShutdown} közvetlenül próbál, best-effort módon
 * (a nem perzisztens entitások az újraindítást amúgy sem élik túl, tehát a kósza mob a
 * legrosszabb kimenet).
 */
public final class TransientEntities {

    private TransientEntities() {
    }

    /**
     * Egy entitás eltávolítása UUID alapján, az entitás saját régió-szálán (Folia-szabály).
     * Futás közbeni használatra; {@code null} id esetén no-op.
     */
    public static void removeById(final Plugin plugin, final UUID id) {
        if (plugin == null || id == null) {
            return;
        }
        try {
            final Entity entity = Bukkit.getEntity(id);
            if (entity != null && entity.isValid()) {
                entity.getScheduler().run(plugin, task -> entity.remove(), null);
            }
        } catch (final Exception ignored) {
            // A régió/scheduler nem elérhető (pl. világ-unload, leállás) — a mulandó entitás
            // az újraindítást nem éli túl, ezért a némán elmaradó despawn elfogadható.
        }
    }

    /**
     * Leállításkori despawn: a készlet minden entitását megpróbálja eltávolítani (hop NÉLKÜL,
     * mert a schedulerek már állnak), majd üríti a készletet. Egy hibázó entitás nem szakítja
     * meg a többi despawnját.
     */
    public static void removeAllOnShutdown(final Collection<UUID> ids) {
        if (ids == null) {
            return;
        }
        for (final UUID id : ids) {
            removeOnShutdown(id);
        }
        ids.clear();
    }

    /**
     * Él-e még a követett entitás.
     *
     * <p><b>Fail-open:</b> ha a régió épp nem elérhető (a lekérdezés dob), ÉLŐNEK tekintjük —
     * különben egy átmeneti régió-hiba miatt a manager idő előtt „elveszettnek" hinné az
     * eseményét és újat indítana, miközben a régi entitás ott áll a világban.
     *
     * @return false csak akkor, ha az id null, vagy az entitás bizonyítottan nincs/nem érvényes
     */
    public static boolean isAlive(final UUID id) {
        if (id == null) {
            return false;
        }
        try {
            final Entity entity = Bukkit.getEntity(id);
            return entity != null && entity.isValid();
        } catch (final Exception ignored) {
            return true;
        }
    }

    /** Egyetlen entitás leállításkori, hop nélküli despawnja (best effort). */
    public static void removeOnShutdown(final UUID id) {
        if (id == null) {
            return;
        }
        try {
            final Entity entity = Bukkit.getEntity(id);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        } catch (final Exception ignored) {
            // Leállítás közben a régió már nem elérhető — legrosszabb esetben kósza mob marad.
        }
    }
}
