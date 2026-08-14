package hu.taliann.icesmp.client.protocol;

/**
 * TRACK_QUEST kérés: a követett quest beállítása (üres id = követés törlése). Ez az
 * egyetlen quest-mutáció, amely kliensről engedett: nincs NPC/hely-kötése, a szerver
 * pedig csak aktív questre engedi. Accept/turn-in kliens-actionként TILOS — azok
 * forrás-authorityját (NPC-kattintás, territórium, esemény) a kliens nem
 * helyettesítheti.
 */
public record QuestTrackPayload(String questId) {

    public byte[] encode() {
        return ClientMessageCodec.encodePayload(out -> ClientMessageCodec.writeString(out, questId));
    }

    public static QuestTrackPayload decode(final byte[] payload) throws ClientProtocolException {
        return ClientMessageCodec.decodePayload(payload,
                in -> new QuestTrackPayload(ClientMessageCodec.readString(in)));
    }
}
