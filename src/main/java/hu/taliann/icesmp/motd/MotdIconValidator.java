package hu.taliann.icesmp.motd;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.Set;

/** Opens and validates one server-list icon without following a link between validation and decode. */
public final class MotdIconValidator {

    private static final Set<OpenOption> READ_NOFOLLOW = Set.of(
            StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);

    private MotdIconValidator() {
    }

    public static BufferedImage readValidatedPng(final Path path, final long maxBytes) throws IOException {
        if (maxBytes <= 0L) {
            throw new IllegalArgumentException("Az ikon fájlméret-limitje csak pozitív lehet.");
        }
        try (SeekableByteChannel channel = java.nio.file.Files.newByteChannel(path, READ_NOFOLLOW)) {
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
    }
}
