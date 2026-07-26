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
 * safe-write logic lives in exactly one place: the config is written to a unique temp file and
 * then atomically renamed over the target.
 *
 * <p><b>Critical state contract:</b> a malformed persistent state file is never replaced by an
 * empty in-memory configuration. {@link #loadTracked(File, Logger)} quarantines the bytes, marks
 * the path unhealthy and throws {@link CorruptStateFileError}. The error intentionally escapes
 * the core's per-store {@code RuntimeException} recovery and aborts plugin enable; running without
 * claims, ownerships, balances or moderation state would be a fail-open security boundary.
 */
public final class YamlStore {

    /** Paths whose last load failed. Writes remain forbidden until a later successful parse. */
    private static final Set<String> loadFailed = ConcurrentHashMap.newKeySet();
    /** Log a refused write once per path so autosave cannot flood the console. */
    private static final Map<String, Boolean> refusalLogged = new ConcurrentHashMap<>();

    private YamlStore() {
    }

    /**
     * Loads an authoritative state file with strict parse handling.
     *
     * <p>A successful parse clears a previous unhealthy marker. A parse or read failure creates a
     * timestamped quarantine copy, keeps writes blocked and raises a fatal startup error. Returning
     * an empty configuration here is forbidden because many managers clear their maps before load.
     *
     * @return the parsed configuration; an absent file is a healthy empty configuration
     * @throws CorruptStateFileError when existing bytes cannot be read or parsed
     */
    public static YamlConfiguration loadTracked(final File file, final Logger logger) {
        final String key = file.getAbsolutePath();
        final YamlConfiguration yaml = new YamlConfiguration();
        if (!file.exists()) {
            loadFailed.remove(key);
            refusalLogged.remove(key);
            return yaml;
        }
        try {
            yaml.loadFromString(Files.readString(file.toPath()));
            loadFailed.remove(key);
            refusalLogged.remove(key);
            return yaml;
        } catch (final InvalidConfigurationException | IOException | RuntimeException failure) {
            loadFailed.add(key);
            logger.severe("SÉRÜLT kritikus állapotfájl: " + file.getName() + " — " + failure.getMessage());
            quarantine(file, logger);
            logger.severe("Az IceSMP indulása MEGSZAKAD. A(z) " + file.getName()
                    + " mentése letiltva marad; javítsd a fájlt vagy állítsd vissza a karanténból, "
                    + "majd indítsd újra a szervert.");
            throw new CorruptStateFileError(file, failure);
        }
    }

    /** Creates a byte-preserving quarantine copy for operator recovery. */
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

    /** Whether the latest load of this path failed and writes are therefore forbidden. */
    public static boolean isLoadFailed(final File file) {
        return loadFailed.contains(file.getAbsolutePath());
    }

    /**
     * Atomically writes {@code yaml} to {@code file}.
     *
     * <p>The method never reports success when persistence was refused. A load-failed path throws
     * {@link IOException}, allowing transaction callers to keep their WAL/commit witness intact.
     *
     * @throws IOException if the write fails or the path is blocked after a failed load
     */
    public static void saveAtomic(final File file, final YamlConfiguration yaml) throws IOException {
        final String key = file.getAbsolutePath();
        if (loadFailed.contains(key)) {
            final String message = "Mentés MEGTAGADVA: " + file.getName()
                    + " betöltése sérült volt — az adat felülírása adatvesztés lenne.";
            if (refusalLogged.putIfAbsent(key, Boolean.TRUE) == null) {
                Logger.getLogger("IceSMP").severe(message);
            }
            throw new IOException(message);
        }

        final File parent = file.getParentFile();
        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }
        final File tempFile = new File(parent,
                file.getName() + "." + System.nanoTime() + ".tmp");
        try {
            yaml.save(tempFile);
            try {
                Files.move(tempFile.toPath(), file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (final IOException atomicFailure) {
                Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tempFile.toPath());
        }
    }
}
