package hu.taliann.icesmp.client.protocol;

/**
 * PURCHASE_TALENT kérés: pool-jelölő + talent-id. A talent-id itt megengedett
 * kliens-bemenet, mert a szerveroldali use-case ({@code TalentManager.spendPoint})
 * a CAS-védett tranzakción belül minden requirementet, fa-gate-et és pont-fedezetet
 * újravalidál — ismeretlen vagy jogosulatlan id egyszerűen elutasításra fut.
 */
public record TalentActionPayload(boolean classPool, String talentId) {

    public byte[] encode() {
        return ClientMessageCodec.encodePayload(out -> {
            out.writeBoolean(classPool);
            ClientMessageCodec.writeString(out, talentId);
        });
    }

    public static TalentActionPayload decode(final byte[] payload) throws ClientProtocolException {
        return ClientMessageCodec.decodePayload(payload, in -> new TalentActionPayload(
                in.readBoolean(),
                ClientMessageCodec.readString(in)));
    }
}
