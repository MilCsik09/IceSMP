package hu.taliann.icesmp.client.protocol;

/**
 * Spell-célú action-kérés (SELECT_SPELL, TOGGLE_FAVORITE) payloadja. A spell-id itt
 * megengedett kliens-bemenet, mert mindkét action csak a játékos SAJÁT
 * spellbook-állapotát érinti, és a szerver a meglévő, validált use-case-eken fut
 * (aktív-kit-tagság, illetve cappelt kedvenc-váltás) — jogosultság-kerülésre nem ad utat.
 */
public record SpellActionPayload(String spellId) {

    public byte[] encode() {
        return ClientMessageCodec.encodePayload(out -> ClientMessageCodec.writeString(out, spellId));
    }

    public static SpellActionPayload decode(final byte[] payload) throws ClientProtocolException {
        return ClientMessageCodec.decodePayload(payload,
                in -> new SpellActionPayload(ClientMessageCodec.readString(in)));
    }
}
