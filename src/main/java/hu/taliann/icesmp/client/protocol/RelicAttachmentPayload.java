package hu.taliann.icesmp.client.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * RELIC_ATTACHMENT_STATE: a néző közelében tartózkodó, AKTÍV class-relicet viselő
 * játékosok listája (attachment-broadcast). Csak megjelenítési tények utaznak
 * (viselő-UUID, relic-id + név, rezonancia-jelzés) — a kliens a saját világában
 * a UUID alapján oldja fel az entitást, nevet a szerver nem küld. A lista a
 * szerveren rádiusz-szűrt és determinisztikusan rendezett; alvó (dormant) viselő
 * nem kerül a vezetékre. A tényleges attachment-renderer későbbi fázis — ez a
 * kézbesítési infrastruktúra.
 */
public record RelicAttachmentPayload(List<Wearer> wearers) {

    /** Egy közeli aktív viselő; a rezonancia-jelzés a későbbi FX-tierezéshez. */
    public record Wearer(UUID playerId, String relicId, String displayName,
                         boolean resonanceActive) {
    }

    public RelicAttachmentPayload {
        wearers = List.copyOf(wearers);
    }

    public byte[] encode() {
        return ClientMessageCodec.encodePayload(out -> {
            writeCount(out, wearers.size());
            for (final Wearer wearer : wearers) {
                out.writeLong(wearer.playerId().getMostSignificantBits());
                out.writeLong(wearer.playerId().getLeastSignificantBits());
                ClientMessageCodec.writeString(out, wearer.relicId());
                ClientMessageCodec.writeString(out, wearer.displayName());
                out.writeBoolean(wearer.resonanceActive());
            }
        });
    }

    public static RelicAttachmentPayload decode(final byte[] payload) throws ClientProtocolException {
        return ClientMessageCodec.decodePayload(payload, in -> {
            final int count = readCount(in);
            final List<Wearer> wearers = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                wearers.add(new Wearer(
                        new UUID(in.readLong(), in.readLong()),
                        ClientMessageCodec.readString(in),
                        ClientMessageCodec.readString(in),
                        in.readBoolean()));
            }
            return new RelicAttachmentPayload(wearers);
        });
    }

    private static void writeCount(final DataOutputStream out, final int count) throws IOException {
        if (count > ClientProtocol.MAX_LIST_ELEMENTS) {
            throw new IOException("list exceeds protocol limit");
        }
        out.writeInt(count);
    }

    private static int readCount(final DataInputStream in) throws IOException, ClientProtocolException {
        final int count = in.readInt();
        if (count < 0 || count > ClientProtocol.MAX_LIST_ELEMENTS) {
            throw new ClientProtocolException("list length out of bounds: " + count);
        }
        return count;
    }
}
