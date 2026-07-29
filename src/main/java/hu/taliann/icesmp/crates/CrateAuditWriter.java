package hu.taliann.icesmp.crates;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Single-writer append/rotation boundary for crate audit records. */
public final class CrateAuditWriter {

    private final Path file;
    private final long rotateBytes;
    private final Object lock = new Object();

    public CrateAuditWriter(final Path file, final long rotateBytes) {
        if (file == null || rotateBytes <= 0L) {
            throw new IllegalArgumentException("Invalid audit writer configuration");
        }
        this.file = file;
        this.rotateBytes = rotateBytes;
    }

    public void append(final String line) throws IOException {
        final byte[] bytes = (line == null ? "" : line).getBytes(StandardCharsets.UTF_8);
        synchronized (lock) {
            final Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            final long current = Files.exists(file) ? Files.size(file) : 0L;
            if (current > 0L && current + bytes.length > rotateBytes) {
                final Path rotated = file.resolveSibling(file.getFileName() + ".1");
                Files.move(file, rotated, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.write(file, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }
}
