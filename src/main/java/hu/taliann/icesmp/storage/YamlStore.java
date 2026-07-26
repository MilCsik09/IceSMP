package hu.taliann.icesmp.storage;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * The single, shared YAML persistence primitive for the whole plugin.
 *
 * <p>Every manager writes its data file through {@link #saveAtomic(File, YamlConfiguration)} so the
 * (previously copy-pasted) safe-write logic lives in exactly one place: the config is written to a
 * temp file and then atomically renamed over the target. This guarantees a crash or a concurrent
 * write can never leave a half-written/truncated data file behind.
 *
 * <p>Kept as a tiny static utility (rather than a base class) so adopting it is a one-line change in
 * each manager and never disturbs their constructors, fields or load logic.
 *
 * <p><b>Sérült fájl elleni védelem:</b> a {@code hu.taliann.icesmp.storage.YamlStore.loadTracked(File, org.bukkit.Bukkit.getLogger())}
 * FAIL-OPEN — hibás YAML-nál nem dob kivételt, csak naplóz, és ÜRES konfigurációt ad vissza. Így a
 * manager üres alapállapottal indult, a következő autosave pedig szabályos, de üres fájlt írt a
 * kézzel még javítható adat FÖLÉ: teljes játékos-, gazdasági vagy világállapot veszhetett el, miközben
 * a szerver látszólag rendben elindult. Ezért:
 * <ul>
 *   <li>az állapotfájlokat a {@link #loadTracked(File, Logger)} olvassa, ami VALÓDI parse-hibát
 *       észlel ({@code loadFromString}), karantén-másolatot készít és megjelöli a fájlt;</li>
 *   <li>a {@link #saveAtomic} MEGTAGADJA a megjelölt fájl írását, tehát a sérült, de meglévő adat
 *       nem íródhat felül — az operátornak van mit visszaállítania.</li>
 * </ul>
 */
public final class YamlStore {

    /**
     * Azok az állapotfájlok, amiknek a betöltése PARSE-HIBÁRA futott. A mentés ezekre tiltott.
     * Statikus, mert a store-ok is statikusan hívják a primitívet, és a tiltásnak a teljes
     * futásra kell érvényesnek lennie (egyetlen sikeres újratöltés oldja fel).
     */
    private static final Set<String> loadFailed = ConcurrentHashMap.newKeySet();
    /** Fájlonként egyszer naplózunk elutasított mentést — különben az autosave elárasztja a logot. */
    private static final Map<String, Boolean> refusalLogged = new ConcurrentHashMap<>();

    private YamlStore() {
    }

    /**
     * Állapotfájl betöltése VALÓDI parse-hiba-észleléssel.
     *
     * <p>Sikeres parse esetén a fájl feloldódik a tiltás alól (kézi javítás után újratöltéssel
     * visszaáll a normál működés). Hibás YAML esetén karantén-másolat készül
     * ({@code <név>.corrupt-<időbélyeg>}), a fájl megjelölődik, és ÜRES konfiguráció tér vissza —
     * a hívó meglévő logikája változatlanul fut, de a {@link #saveAtomic} már nem írja felül.
     *
     * @return a betöltött konfiguráció, vagy üres konfiguráció parse-hiba esetén
     */
    public static YamlConfiguration loadTracked(final File file, final Logger logger) {
        final String key = file.getAbsolutePath();
        final YamlConfiguration yaml = new YamlConfiguration();
        if (!file.exists()) {
            loadFailed.remove(key);
            return yaml;
        }
        try {
            yaml.loadFromString(Files.readString(file.toPath()));
            loadFailed.remove(key);
            refusalLogged.remove(key);
            return yaml;
        } catch (final InvalidConfigurationException | IOException | RuntimeException failure) {
            loadFailed.add(key);
            logger.severe("SÉRÜLT állapotfájl: " + file.getName() + " — " + failure.getMessage());
            quarantine(file, logger);
            logger.severe("A(z) " + file.getName() + " MENTÉSE LETILTVA, hogy a meglévő adat ne "
                    + "íródjon felül. Javítsd a fájlt (vagy állítsd vissza a karantén-másolatból), "
                    + "majd /icesmp reload vagy újraindítás.");
            return new YamlConfiguration();
        }
    }

    /** Karantén-másolat a sérült fájlról, hogy a kézi javításhoz legyen mit elővenni. */
    private static void quarantine(final File file, final Logger logger) {
        final File copy = new File(file.getParentFile(),
                file.getName() + ".corrupt-" + Instant.now().toEpochMilli());
        try {
            Files.copy(file.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logger.severe("Karantén-másolat: " + copy.getName());
        } catch (final IOException copyFailure) {
            logger.severe("A karantén-másolat NEM készült el (" + copy.getName() + "): "
                    + copyFailure.getMessage());
        }
    }

    /** Betöltés-hibás állapotfájl-e (a mentés ezért tiltott). */
    public static boolean isLoadFailed(final File file) {
        return loadFailed.contains(file.getAbsolutePath());
    }

    /**
     * Atomically writes {@code yaml} to {@code file}: it is written to a UNIQUE temp file and then
     * atomically renamed over the target (with a plain-replace fallback on filesystems that do not
     * support atomic moves). The unique temp name means concurrent callers on the same file — some
     * managers' {@code save()} is not synchronised — never clobber a shared temp; each writes its
     * own and the last rename wins with a complete, valid file. The temp is always cleaned up.
     *
     * @throws IOException if the write fails — callers keep their existing try/catch logging
     */
    public static void saveAtomic(final File file, final YamlConfiguration yaml) throws IOException {
        if (loadFailed.contains(file.getAbsolutePath())) {
            // A betöltés parse-hibára futott, tehát a memóriabeli állapot ÜRES vagy részleges.
            // A mentés itt véglegesen elvinné a lemezen még megvan adatot.
            if (refusalLogged.putIfAbsent(file.getAbsolutePath(), Boolean.TRUE) == null) {
                // Bukkit-független logger: a primitív a szerver-életciklustól akkor is működik,
                // ha a Bukkit.server még/már nincs beállítva (shutdown-mentés, teszt).
                Logger.getLogger("IceSMP").severe("Mentés MEGTAGADVA: " + file.getName()
                        + " betöltése sérült volt — az adat felülírása adatvesztés lenne.");
            }
            return;
        }
        final File tempFile = new File(file.getParentFile(),
                file.getName() + "." + System.nanoTime() + ".tmp");
        try {
            yaml.save(tempFile);
            try {
                Files.move(tempFile.toPath(), file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (final IOException atomicFailure) {
                // Filesystem may not support atomic moves; fall back to a plain replace.
                Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            // No-op when the move succeeded (temp already gone); removes a partial temp on failure.
            Files.deleteIfExists(tempFile.toPath());
        }
    }
}
