package hu.taliann.icesmp.client.protocol;

/**
 * FX_EVENT: tranziens presentation-esemény (telegráf-jelzés, arming-visszajelzés).
 * Fire-and-forget: nem state — resync nem ismétli, dedupe nincs, az elveszett vagy
 * kihagyott esemény gameplay-t nem érinthet (a vanilla telegráf-partikula/hang minden
 * kliensnek változatlanul megy, az FX csak kiegészítő réteg). Pozicionált eseménynél
 * a {@code durationTicks} a telegráf hossza (a kliens ennyi ideig tartja képen);
 * nem-pozicionáltnál a koordináták/rádiusz 0.
 */
public record FxEventPayload(String fxId, boolean positional,
                             double x, double y, double z,
                             double radius, int durationTicks) {

    public byte[] encode() {
        return ClientMessageCodec.encodePayload(out -> {
            ClientMessageCodec.writeString(out, fxId);
            out.writeBoolean(positional);
            out.writeDouble(x);
            out.writeDouble(y);
            out.writeDouble(z);
            out.writeDouble(radius);
            out.writeInt(durationTicks);
        });
    }

    public static FxEventPayload decode(final byte[] payload) throws ClientProtocolException {
        return ClientMessageCodec.decodePayload(payload, in -> new FxEventPayload(
                ClientMessageCodec.readString(in),
                in.readBoolean(),
                in.readDouble(),
                in.readDouble(),
                in.readDouble(),
                in.readDouble(),
                in.readInt()));
    }
}
