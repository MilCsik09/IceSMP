package hu.taliann.icesmp.storage;

import java.io.File;

/**
 * Fatal write failure for a file that participates in a cross-store transaction.
 *
 * <p>The error deliberately bypasses local {@code IOException} catch blocks. Those callers were
 * written for ordinary autosave failures and may otherwise continue to deliver an item or report
 * success after the durable commit failed. The global persistence circuit remains open until all
 * affected files are loaded successfully during a controlled reload/restart.
 */
public final class CriticalPersistenceWriteError extends Error {

    private static final long serialVersionUID = 1L;

    private final File file;

    public CriticalPersistenceWriteError(final File file, final Throwable cause) {
        super("Kritikus perzisztencia-írás sikertelen: " + file.getAbsolutePath(), cause);
        this.file = file;
    }

    public File file() {
        return file;
    }
}
