package hu.taliann.icesmp.territory;

import hu.taliann.icesmp.data.Territory;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Stable native revision fingerprint; independent of WorldWeaver and Java object serialization. */
public final class TerritoryRevision {
    private TerritoryRevision() { }

    public static String fingerprint(final Territory territory) {
        Objects.requireNonNull(territory);
        try {
            final var bytes = new ByteArrayOutputStream();
            try (final var output = new DataOutputStream(bytes)) {
                output.writeInt(1);
                output.writeUTF(territory.id()); output.writeUTF(territory.faction().name());
                output.writeUTF(territory.name()); output.writeUTF(territory.type().name()); output.writeUTF(territory.world());
                output.writeInt(territory.x()); output.writeInt(territory.z()); output.writeInt(territory.radius());
                output.writeInt(territory.minY()); output.writeInt(territory.maxY());
                final var polygon = territory.polygon(); output.writeInt(polygon.size());
                for (final int[] point : polygon) { output.writeInt(point[0]); output.writeInt(point[1]); }
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (final IOException | NoSuchAlgorithmException impossible) {
            throw new IllegalArgumentException("Territory cannot be fingerprinted", impossible);
        }
    }
}
