package hu.taliann.icesmp.storage;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/** Shared strict-load and durable-write primitive for plugin state. */
public final class YamlStore {

    /** Paths whose last load failed. Writes remain forbidden until a later successful parse. */
    private static final Set<String> loadFailed = ConcurrentHashMap.newKeySet();
    /** Cross-store transaction files whose write failure must stop the current gameplay call. */
    private static final Set<String> criticalWritePaths = ConcurrentHashMap.newKeySet();
    /** Sticky circuit: cleared only by a successful controlled load of the same path. */
    private static final Set<String> writeFailed = ConcurrentHashMap.newKeySet();
    /** Log a refused write once per path so autosave cannot flood the console. */
    private static final Map<String, Boolean> refusalLogged = new ConcurrentHashMap<>();

    private YamlStore() {
    }

    /** Marks a file as participating in a transaction whose caller must never continue on failure. */
    public static void registerCriticalWrite(final File file) {
        if (file != null) {
            criticalWritePaths.add(key(file));
        }
    }

    /** Whether any critical transaction file has failed since the last controlled successful load. */
    public static boolean hasCriticalWriteFailure() {
        for (final String failed : writeFailed) {
            if (criticalWritePaths.contains(failed)) {
                return true;
            }
        }
        return false;
    }

    /** Loads an authoritative state file with strict parse handling. */
    public static YamlConfiguration loadTracked(final File file, final Logger logger) {
        final String path = key(file);
        final YamlConfiguration yaml = new YamlConfiguration();
        if (!file.exists()) {
            clearFailure(path);
            return yaml;
        }
        try {
            yaml.loadFromString(Files.readString(file.toPath()));
            clearFailure(path);
            return yaml;
        } catch (final InvalidConfigurationException | IOException | RuntimeException failure) {
            failCorrupt(file, logger, "A YAML nem olvasható vagy nem értelmezhető", failure);
            throw new AssertionError("unreachable");
        }
    }

    /**
     * Converts a parseable but semantically unsafe state/journal into the same fail-closed state as
     * a syntactically corrupt YAML file. The original bytes are retained for manual recovery.
     */
    public static void failCorrupt(final File file, final Logger logger, final String reason) {
        failCorrupt(file, logger, reason, new InvalidConfigurationException(reason));
    }

    private static void failCorrupt(final File file, final Logger logger, final String reason,
                                    final Throwable cause) {
        final String path = key(file);
        loadFailed.add(path);
        writeFailed.add(path);
        logger.severe("SÉRÜLT kritikus állapotfájl: " + file.getName() + " — " + reason
                + (cause.getMessage() == null || cause.getMessage().equals(reason)
                ? "" : " (" + cause.getMessage() + ")"));
        quarantine(file, logger);
        logger.severe("Az IceSMP indulása MEGSZAKAD. A(z) " + file.getName()
                + " mentése letiltva marad; javítsd a fájlt vagy állítsd vissza a karanténból, "
                + "majd indítsd újra a szervert.");
        throw new CorruptStateFileError(file, cause);
    }

    private static void clearFailure(final String path) {
        loadFailed.remove(path);
        writeFailed.remove(path);
        refusalLogged.remove(path);
    }

    /** Creates a byte-preserving quarantine copy for operator recovery. */
    private static void quarantine(final File file, final Logger logger) {
        if (!file.exists()) {
            return;
        }
        final File parent = file.getParentFile();
        final File copy = new File(parent, file.getName() + ".corrupt-" + Instant.now().toEpochMilli());
        try {
            Files.copy(file.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING);
            forceFile(copy);
            forceDirectory(parent);
            logger.severe("Karantén-másolat: " + copy.getName());
        } catch (final IOException copyFailure) {
            logger.severe("A karantén-másolat NEM készült el (" + copy.getName() + "): "
                    + copyFailure.getMessage());
        }
    }

    /** Whether the latest load of this path failed and writes are therefore forbidden. */
    public static boolean isLoadFailed(final File file) {
        return loadFailed.contains(key(file));
    }

    /**
     * Writes a complete file, fsyncs the temporary content, atomically replaces the target and
     * fsyncs the parent directory. Only an unsupported atomic move falls back to a plain replace;
     * permission, ENOSPC and other real failures are never disguised as an atomic-move limitation.
     */
    public static void saveAtomic(final File file, final YamlConfiguration yaml) throws IOException {
        final String path = key(file);
        if (loadFailed.contains(path)) {
            final IOException failure = new IOException("Mentés MEGTAGADVA: " + file.getName()
                    + " betöltése sérült volt — az adat felülírása adatvesztés lenne.");
            if (refusalLogged.putIfAbsent(path, Boolean.TRUE) == null) {
                Logger.getLogger("IceSMP").severe(failure.getMessage());
            }
            throwWriteFailure(file, failure);
            return;
        }

        final File parent = file.getAbsoluteFile().getParentFile();
        final File tempFile = new File(parent, file.getName() + "." + System.nanoTime() + ".tmp");
        IOException failure = null;
        try {
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            yaml.save(tempFile);
            forceFile(tempFile);
            try {
                Files.move(tempFile.toPath(), file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (final AtomicMoveNotSupportedException unsupported) {
                Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            forceDirectory(parent);
        } catch (final IOException writeFailure) {
            failure = writeFailure;
            writeFailed.add(path);
            throwWriteFailure(file, writeFailure);
        } finally {
            try {
                Files.deleteIfExists(tempFile.toPath());
            } catch (final IOException cleanupFailure) {
                if (failure != null) {
                    failure.addSuppressed(cleanupFailure);
                } else {
                    writeFailed.add(path);
                    throwWriteFailure(file, cleanupFailure);
                }
            }
        }
    }

    private static void throwWriteFailure(final File file, final IOException failure) throws IOException {
        if (criticalWritePaths.contains(key(file))) {
            throw new CriticalPersistenceWriteError(file, failure);
        }
        throw failure;
    }

    private static void forceFile(final File file) throws IOException {
        try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static void forceDirectory(final File directory) throws IOException {
        if (directory == null) {
            return;
        }
        try (FileChannel channel = FileChannel.open(directory.toPath(), StandardOpenOption.READ)) {
            channel.force(true);
        } catch (final AccessDeniedException | UnsupportedOperationException unsupported) {
            // Windows and some custom providers do not expose fsync-capable directory channels.
            // Other I/O failures (including ENOSPC/EIO) must propagate: the rename durability
            // would otherwise be reported as proven when the directory metadata was not forced.
        }
    }

    private static String key(final File file) {
        return file.getAbsoluteFile().toPath().normalize().toString();
    }
}
