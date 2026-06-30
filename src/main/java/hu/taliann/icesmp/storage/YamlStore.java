package hu.taliann.icesmp.storage;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

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
 */
public final class YamlStore {

    private YamlStore() {
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
