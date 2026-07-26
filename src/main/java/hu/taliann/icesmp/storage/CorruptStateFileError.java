package hu.taliann.icesmp.storage;

import java.io.File;

/**
 * Unrecoverable startup failure raised when a persistent state file cannot be parsed.
 *
 * <p>This deliberately extends {@link Error}, not {@link RuntimeException}: the core's generic
 * per-store recovery catches runtime exceptions so an unrelated manager bug does not cascade,
 * but continuing after a corrupt authoritative state file would start the server with missing
 * claims, ownerships, balances or moderation state. That is a fail-open security boundary, so
 * plugin enable must abort instead of silently substituting an empty configuration.
 */
public final class CorruptStateFileError extends Error {

    private final File file;

    public CorruptStateFileError(final File file, final Throwable cause) {
        super("Sérült kritikus állapotfájl: " + file.getAbsolutePath(), cause);
        this.file = file;
    }

    public File file() {
        return file;
    }
}
