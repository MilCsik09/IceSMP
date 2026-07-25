package hu.taliann.icesmp.utils;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A világesemény-managerek közös ütemező-váza: „lejárt-e a próbálkozási időzítő", „átment-e a
 * sorsolás", és a spawn-lánc idejére szóló <b>rezerváció</b> egy helyen.
 *
 * <p><b>Miért kell a rezerváció?</b> Egy esemény indítása több régió-szálon hopol (horgony
 * szála → cél-hely régiója), ezért az „aktív vagyok" állapot csak a lánc VÉGÉN áll be.
 * Addig a periodikus tick és az admin force-parancs egymástól függetlenül elindíthatja
 * ugyanazt az eseményt — a rezerváció ezt zárja ki. A rezerváció <b>időalapú és
 * öngyógyuló</b>: ha a lánc menet közben elhal (világ-unload, hibázó hop), a grace
 * lejártával a rendszer magától újra próbálkozhat, nem ragad be véglegesen.
 *
 * <p><b>Folia:</b> a tick a globális szálon, a force a hívó parancs szálán fut, ezért minden
 * állapot atomi ({@link AtomicLong}) — a rezervációt {@code compareAndSet} nyeri meg, így
 * két szál közül biztosan csak az egyik indít.
 */
public final class PeriodicChanceEvent {

    /** A következő sorsolás legkorábbi ideje (millis). */
    private final AtomicLong nextAttemptAt = new AtomicLong();

    /** Eddig fut egy indítási lánc — addig se tick, se force nem indíthat újat. */
    private final AtomicLong reservedUntil = new AtomicLong();

    /** Fut-e épp indítási lánc (a rezerváció még nem járt le). */
    public boolean isReserved() {
        return System.currentTimeMillis() < reservedUntil.get();
    }

    /**
     * Periodikus indítási kísérlet: lejárt-e az időzítő, és átment-e a sorsolás.
     *
     * <p>A hívó MINDIG csak akkor indítsa el az eseményt, ha ez {@code true} — a rezervációt
     * ez a metódus foglalja le, tehát a visszatérés után a lánc kizárólagosan a hívóé.
     *
     * @param intervalMillis a következő kísérletig eltelő idő (az időzítő akkor is tolódik,
     *                       ha a sorsolás nem jön be — a sűrű újrapróbálkozás fék)
     * @param chancePercent  0-100; ennyi eséllyel indul az esemény, ha az időzítő lejárt
     * @param reserveMillis  ennyi ideig tartja a rezervációt a spawn-láncnak (öngyógyulás)
     * @return true, ha MOST kell elindítani az eseményt (és a rezervációt megnyertük)
     */
    public boolean tryAttempt(final long intervalMillis, final double chancePercent, final long reserveMillis) {
        final long now = System.currentTimeMillis();
        if (now < reservedUntil.get() || now < nextAttemptAt.get()) {
            return false;
        }
        nextAttemptAt.set(now + Math.max(1L, intervalMillis));
        final double chance = Math.max(0.0D, Math.min(100.0D, chancePercent));
        if (ThreadLocalRandom.current().nextDouble(100.0D) >= chance) {
            return false;
        }
        return reserve(now, reserveMillis);
    }

    /**
     * Admin-kényszerítés (sorsolás és időzítő nélkül): csak a rezervációt kéri.
     *
     * @return true, ha a lánc elindítható (nem fut másik indítás)
     */
    public boolean tryForce(final long reserveMillis) {
        return reserve(System.currentTimeMillis(), reserveMillis);
    }

    /**
     * A rezerváció elengedése — akkor hívd, ha a lánc HAMARABB zárult, mint a grace
     * (pl. a spawn-guard elutasította a helyet), így nem kell kivárni a lejáratot.
     */
    public void release() {
        reservedUntil.set(0L);
    }

    /** A következő sorsolás elodázása (pl. „üres a szerver, ne próbáljuk sűrűn"). */
    public void delayNextAttempt(final long millis) {
        nextAttemptAt.set(System.currentTimeMillis() + Math.max(0L, millis));
    }

    /** Azonnali következő kísérlet engedélyezése (reload/újrahangolás után). */
    public void resetTimer() {
        nextAttemptAt.set(0L);
    }

    private boolean reserve(final long now, final long reserveMillis) {
        final long current = reservedUntil.get();
        if (now < current) {
            return false;
        }
        // CAS: a tick és a force közül csak az egyik nyerheti meg a láncot.
        return reservedUntil.compareAndSet(current, now + Math.max(1L, reserveMillis));
    }
}
