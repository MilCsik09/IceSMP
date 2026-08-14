package hu.taliann.icesmp.client.protocol;

/**
 * CAST_SLOT kérés: a futásidőben számolt aktív kit 1-alapú slot-indexe. Szándékosan
 * NEM spell-id — a kliens csak pozíciót kérhet, a spell-feloldás és minden validáció
 * a szerveré (a tetszőleges spell-id-s kérés jogosultság-kerülő út lenne).
 */
public record CastSlotPayload(int slot) {

    public byte[] encode() {
        return ClientMessageCodec.encodePayload(out -> out.writeInt(slot));
    }

    public static CastSlotPayload decode(final byte[] payload) throws ClientProtocolException {
        return ClientMessageCodec.decodePayload(payload, in -> new CastSlotPayload(in.readInt()));
    }
}
