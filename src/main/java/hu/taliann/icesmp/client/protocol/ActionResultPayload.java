package hu.taliann.icesmp.client.protocol;

/**
 * Gépi action-válasz; a kérés-korrelációt az envelope requestId-je hordozza. A
 * {@code result} a {@link ClientProtocol} RESULT_* kódjainak egyike, a {@code reason}
 * opcionális gépi részlet (pl. COOLDOWN, COST, SLOT) — megjelenítési szöveggé a
 * kliens lokalizációja alakítja.
 */
public record ActionResultPayload(int actionType, String result, String reason) {

    public byte[] encode() {
        return ClientMessageCodec.encodePayload(out -> {
            out.writeInt(actionType);
            ClientMessageCodec.writeString(out, result);
            ClientMessageCodec.writeString(out, reason);
        });
    }

    public static ActionResultPayload decode(final byte[] payload) throws ClientProtocolException {
        return ClientMessageCodec.decodePayload(payload, in -> new ActionResultPayload(
                in.readInt(),
                ClientMessageCodec.readString(in),
                ClientMessageCodec.readString(in)));
    }
}
