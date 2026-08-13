package hu.taliann.icesmp.client.protocol;

/**
 * Kézfogás-elutasítás. Nem kick: az inkompatibilis kliens vanilla fallbackra esik
 * vissza, a szerverre továbbra is felférhet. A {@code reasonCode} gépi olvasásra
 * szolgál ({@link ClientProtocol#REJECT_INVALID_HELLO} stb.); a megjelenített szöveg
 * kliensoldali lokalizáció dolga.
 */
public record ProtocolReject(
        String reasonCode,
        int serverProtocolMin,
        int serverProtocolMax) {

    public byte[] encode() {
        return ClientMessageCodec.encodePayload(out -> {
            ClientMessageCodec.writeString(out, reasonCode);
            out.writeInt(serverProtocolMin);
            out.writeInt(serverProtocolMax);
        });
    }

    public static ProtocolReject decode(final byte[] payload) throws ClientProtocolException {
        return ClientMessageCodec.decodePayload(payload, in -> new ProtocolReject(
                ClientMessageCodec.readString(in),
                in.readInt(),
                in.readInt()));
    }
}
