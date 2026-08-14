package hu.taliann.icesmp.client.protocol;

/**
 * SELECT_PROFESSION / SELECT_PROFESSION_SPEC intent: a kliens csak azonosítót kér,
 * minden feltétel (üres slot, szakma- és szintkapu, egyszeri spec-választás) a
 * szerver meglévő use-case-eiben validálódik.
 */
public record ProfessionActionPayload(String id) {

    public byte[] encode() {
        return ClientMessageCodec.encodePayload(out -> ClientMessageCodec.writeString(out, id));
    }

    public static ProfessionActionPayload decode(final byte[] payload) throws ClientProtocolException {
        return ClientMessageCodec.decodePayload(payload,
                in -> new ProfessionActionPayload(ClientMessageCodec.readString(in)));
    }
}
