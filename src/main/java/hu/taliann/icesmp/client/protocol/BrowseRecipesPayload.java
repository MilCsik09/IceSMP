package hu.taliann.icesmp.client.protocol;

/**
 * BROWSE_RECIPES query: a kliens egy szakma egy lapját kéri a recept-katalógusból.
 * Pull-modell — a 437 elemes katalógus nem fér a push-protokoll lista-limitjébe, a
 * válasz ({@code RECIPE_PAGE}) requestId-korrelált és a kérés pillanatának
 * inventory-állapotával számol have/need értékeket. A lap-méretet a szerver dönti el.
 */
public record BrowseRecipesPayload(String professionId, int page) {

    public byte[] encode() {
        return ClientMessageCodec.encodePayload(out -> {
            ClientMessageCodec.writeString(out, professionId);
            out.writeInt(page);
        });
    }

    public static BrowseRecipesPayload decode(final byte[] payload) throws ClientProtocolException {
        return ClientMessageCodec.decodePayload(payload, in -> new BrowseRecipesPayload(
                ClientMessageCodec.readString(in), in.readInt()));
    }
}
