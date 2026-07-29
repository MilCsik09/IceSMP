package hu.taliann.icesmp.motd;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Secure no-follow directory traversal and bounded decoding for server-list PNG icons. */
public final class MotdIconValidator {

    private static final Set<OpenOption> READ_NOFOLLOW = Set.of(
            StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);

    public record DecodedIcon(String fileName, BufferedImage image) {
    }

    public record ScanResult(List<DecodedIcon> icons, List<String> warnings, int discoveredPngFiles) {
        public ScanResult {
            icons = List.copyOf(icons);
            warnings = List.copyOf(warnings);
        }
    }

    private MotdIconValidator() {
    }

    /**
     * Lists and opens direct PNG children through one secure directory handle. Root/intermediate/file
     * symlinks cannot be followed, and replacement after listing is harmless because decoding uses
     * the already opened file descriptor.
     */
    public static ScanResult scanPngDirectory(final Path approvedRoot, final Path relativeDirectory,
                                              final int maxFiles, final long maxBytes) throws IOException {
        if (maxFiles <= 0) {
            throw new IllegalArgumentException("Az ikon darabszám-limitje csak pozitív lehet.");
        }
        validateMaxBytes(maxBytes);
        final Path safeDirectory = requireSafeRelative(relativeDirectory, "ikonkönyvtár");
        try (ApprovedRoot root = openApprovedRoot(approvedRoot)) {
            final List<SecureDirectoryStream<Path>> opened = new ArrayList<>();
            try {
                final SecureDirectoryStream<Path> directory = descend(root.stream(), safeDirectory, opened);
                final List<Path> names = new ArrayList<>();
                for (final Path entry : directory) {
                    final Path name = entry.getFileName();
                    if (name != null && name.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".png")) {
                        names.add(name);
                    }
                }
                names.sort(Comparator.comparing(Path::toString));
                final int discovered = names.size();
                final List<String> warnings = new ArrayList<>();
                if (names.size() > maxFiles) {
                    warnings.add("túl sok PNG ikon (" + names.size() + "); csak az első " + maxFiles + " kerül betöltésre");
                    names.subList(maxFiles, names.size()).clear();
                }

                final List<DecodedIcon> decoded = new ArrayList<>();
                for (final Path name : names) {
                    try (SeekableByteChannel channel = directory.newByteChannel(name, READ_NOFOLLOW)) {
                        decoded.add(new DecodedIcon(name.toString(), readValidatedPng(channel, maxBytes)));
                    } catch (final IOException | RuntimeException exception) {
                        warnings.add(name + ": " + safeMessage(exception));
                    }
                }
                return new ScanResult(decoded, warnings, discovered);
            } finally {
                closeReverse(opened);
            }
        }
    }

    /** Writes bundled files only through a securely opened icon directory; existing entries are never replaced. */
    public static void writeFilesIfMissing(final Path approvedRoot, final Path relativeDirectory,
                                           final Map<String, byte[]> files) throws IOException {
        final Path safeDirectory = requireSafeRelative(relativeDirectory, "ikonkönyvtár");
        final Path absoluteDirectory = approvedRoot.toAbsolutePath().normalize().resolve(safeDirectory);
        if (!Files.exists(absoluteDirectory, LinkOption.NOFOLLOW_LINKS)) {
            try {
                Files.createDirectory(absoluteDirectory);
            } catch (final FileAlreadyExistsException ignored) {
                // A secure no-follow open below decides whether the concurrently created entry is acceptable.
            }
        }
        try (ApprovedRoot root = openApprovedRoot(approvedRoot)) {
            final List<SecureDirectoryStream<Path>> opened = new ArrayList<>();
            try {
                final SecureDirectoryStream<Path> directory = descend(root.stream(), safeDirectory, opened);
                for (final Map.Entry<String, byte[]> entry : files.entrySet()) {
                    final Path name = requireSafeRelative(Path.of(entry.getKey()), "beépített ikon");
                    if (name.getNameCount() != 1) {
                        throw new IOException("beépített ikon: alkönyvtár nem engedélyezett");
                    }
                    try (SeekableByteChannel channel = directory.newByteChannel(name,
                            Set.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW,
                                    LinkOption.NOFOLLOW_LINKS))) {
                        final ByteBuffer buffer = ByteBuffer.wrap(entry.getValue());
                        while (buffer.hasRemaining()) {
                            channel.write(buffer);
                        }
                    } catch (final FileAlreadyExistsException ignored) {
                        // Existing operator-provided files are authoritative and are validated during the scan.
                    }
                }
            } finally {
                closeReverse(opened);
            }
        }
    }

    /** Securely opens one relative icon, including no-follow traversal of every parent component. */
    public static BufferedImage readValidatedPng(final Path approvedRoot, final Path relativeFile,
                                                 final long maxBytes) throws IOException {
        validateMaxBytes(maxBytes);
        final Path safeFile = requireSafeRelative(relativeFile, "ikonfájl");
        final Path parent = safeFile.getParent();
        final Path name = safeFile.getFileName();
        if (name == null) {
            throw new IOException("hiányzó ikonfájlnév");
        }
        try (ApprovedRoot root = openApprovedRoot(approvedRoot)) {
            final List<SecureDirectoryStream<Path>> opened = new ArrayList<>();
            try {
                final SecureDirectoryStream<Path> directory = parent == null
                        ? root.stream() : descend(root.stream(), parent, opened);
                try (SeekableByteChannel channel = directory.newByteChannel(name, READ_NOFOLLOW)) {
                    return readValidatedPng(channel, maxBytes);
                }
            } finally {
                closeReverse(opened);
            }
        }
    }

    /** Compatibility overload for an already approved direct parent directory. */
    public static BufferedImage readValidatedPng(final Path path, final long maxBytes) throws IOException {
        final Path absolute = path.toAbsolutePath().normalize();
        final Path parent = absolute.getParent();
        if (parent == null || absolute.getFileName() == null) {
            throw new IOException("érvénytelen ikonútvonal");
        }
        return readValidatedPng(parent, absolute.getFileName(), maxBytes);
    }

    private static BufferedImage readValidatedPng(final SeekableByteChannel channel,
                                                  final long maxBytes) throws IOException {
        final long size = channel.size();
        if (size <= 0L || size > maxBytes) {
            throw new IOException("fájlméret " + size + " bájt; limit " + maxBytes);
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(Channels.newInputStream(channel))) {
            if (input == null) {
                throw new IOException("nem nyitható képfájlként");
            }
            final Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("ismeretlen vagy sérült PNG");
            }
            final ImageReader reader = readers.next();
            try {
                if (!"png".equalsIgnoreCase(reader.getFormatName())) {
                    throw new IOException("a fájl nem PNG");
                }
                reader.setInput(input, true, true);
                if (reader.getWidth(0) != 64 || reader.getHeight(0) != 64) {
                    throw new IOException("az ikon mérete nem pontosan 64×64");
                }
                final BufferedImage image = reader.read(0);
                if (image == null || image.getWidth() != 64 || image.getHeight() != 64) {
                    throw new IOException("a 64×64 PNG nem dekódolható");
                }
                return image;
            } finally {
                reader.dispose();
            }
        }
    }

    /** Opens every component of the approved absolute root without following symbolic links. */
    private static ApprovedRoot openApprovedRoot(final Path approvedRoot) throws IOException {
        if (approvedRoot == null) {
            throw new IOException("hiányzó jóváhagyott adatkönyvtár");
        }
        final Path absolute = approvedRoot.toAbsolutePath().normalize();
        final Path filesystemRoot = absolute.getRoot();
        if (filesystemRoot == null) {
            throw new IOException("az adatkönyvtárnak abszolút útvonalra kell feloldódnia");
        }

        final List<SecureDirectoryStream<Path>> opened = new ArrayList<>();
        DirectoryStream<Path> rootRaw = null;
        try {
            rootRaw = Files.newDirectoryStream(filesystemRoot);
            SecureDirectoryStream<Path> current = requireSecure(rootRaw);
            opened.add(current);
            for (final Path component : absolute) {
                final DirectoryStream<Path> nextRaw = current.newDirectoryStream(component, LinkOption.NOFOLLOW_LINKS);
                try {
                    final SecureDirectoryStream<Path> next = requireSecure(nextRaw);
                    opened.add(next);
                    current = next;
                } catch (final IOException exception) {
                    nextRaw.close();
                    throw exception;
                }
            }
            return new ApprovedRoot(current, opened);
        } catch (final IOException | RuntimeException exception) {
            if (opened.isEmpty() && rootRaw != null) {
                rootRaw.close();
            } else {
                closeReverse(opened);
            }
            throw exception;
        }
    }

    private static SecureDirectoryStream<Path> descend(final SecureDirectoryStream<Path> root,
                                                       final Path relativeDirectory,
                                                       final List<SecureDirectoryStream<Path>> opened)
            throws IOException {
        SecureDirectoryStream<Path> current = root;
        for (final Path component : relativeDirectory) {
            final DirectoryStream<Path> nextRaw = current.newDirectoryStream(component, LinkOption.NOFOLLOW_LINKS);
            try {
                final SecureDirectoryStream<Path> next = requireSecure(nextRaw);
                opened.add(next);
                current = next;
            } catch (final IOException exception) {
                nextRaw.close();
                throw exception;
            }
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private static SecureDirectoryStream<Path> requireSecure(final DirectoryStream<Path> stream) throws IOException {
        if (!(stream instanceof SecureDirectoryStream<?> secure)) {
            throw new IOException("a fájlrendszer nem támogat biztonságos no-follow könyvtárkezelést");
        }
        return (SecureDirectoryStream<Path>) secure;
    }

    private static Path requireSafeRelative(final Path path, final String label) throws IOException {
        if (path == null || path.isAbsolute() || path.getNameCount() == 0) {
            throw new IOException(label + ": csak relatív útvonal engedélyezett");
        }
        final Path normalized = path.normalize();
        if (!normalized.equals(path) || normalized.startsWith("..")) {
            throw new IOException(label + ": a jóváhagyott adatkönyvtáron kívüli útvonal tiltott");
        }
        for (final Path component : normalized) {
            final String value = component.toString();
            if (value.isBlank() || ".".equals(value) || "..".equals(value)) {
                throw new IOException(label + ": érvénytelen útvonalkomponens");
            }
        }
        return normalized;
    }

    private static void validateMaxBytes(final long maxBytes) {
        if (maxBytes <= 0L) {
            throw new IllegalArgumentException("Az ikon fájlméret-limitje csak pozitív lehet.");
        }
    }

    private static void closeReverse(final List<SecureDirectoryStream<Path>> opened) throws IOException {
        IOException failure = null;
        for (int index = opened.size() - 1; index >= 0; index--) {
            try {
                opened.get(index).close();
            } catch (final IOException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private record ApprovedRoot(SecureDirectoryStream<Path> stream,
                                List<SecureDirectoryStream<Path>> opened) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            closeReverse(opened);
        }
    }

    private static String safeMessage(final Throwable failure) {
        final String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
