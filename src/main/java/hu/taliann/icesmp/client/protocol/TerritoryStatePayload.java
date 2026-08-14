package hu.taliann.icesmp.client.protocol;

/**
 * TERRITORY_STATE: a néző aktuális territórium-zónájának display-projekciója — az az
 * adatkör, amit a vanilla határátlépés-actionbar és a /territory info mutat (zóna-név,
 * típus, tulajdonos frakció), tartós overlay-ként, plusz az AKTUÁLIS zónán futó raid
 * állása (a megosztott raid-bar adatköre a zónára szűkítve). {@code inTerritory=false}
 * = Vadon (a többi zóna-mező üres). A raid-pontok a capture-tick ütemében változnak,
 * a bájt-dedupe így nem churn-öl. Zóna-geometria (poligon) szándékosan nem utazik —
 * térkép-overlay külön fázis lenne. A határátlépés-actionbar vanilla úton marad
 * (múló értesítés, nem azonos felület a tartós overlay-jel).
 */
public record TerritoryStatePayload(
        boolean inTerritory, String territoryId, String name, String typeId,
        String factionCode, String factionLabel,
        boolean raidActive, String raidAttackerLabel, String raidDefenderLabel,
        int raidAttackerPoints, int raidDefenderPoints) {

    public byte[] encode() {
        return ClientMessageCodec.encodePayload(out -> {
            out.writeBoolean(inTerritory);
            ClientMessageCodec.writeString(out, territoryId);
            ClientMessageCodec.writeString(out, name);
            ClientMessageCodec.writeString(out, typeId);
            ClientMessageCodec.writeString(out, factionCode);
            ClientMessageCodec.writeString(out, factionLabel);
            out.writeBoolean(raidActive);
            ClientMessageCodec.writeString(out, raidAttackerLabel);
            ClientMessageCodec.writeString(out, raidDefenderLabel);
            out.writeInt(raidAttackerPoints);
            out.writeInt(raidDefenderPoints);
        });
    }

    public static TerritoryStatePayload decode(final byte[] payload) throws ClientProtocolException {
        return ClientMessageCodec.decodePayload(payload, in -> new TerritoryStatePayload(
                in.readBoolean(),
                ClientMessageCodec.readString(in),
                ClientMessageCodec.readString(in),
                ClientMessageCodec.readString(in),
                ClientMessageCodec.readString(in),
                ClientMessageCodec.readString(in),
                in.readBoolean(),
                ClientMessageCodec.readString(in),
                ClientMessageCodec.readString(in),
                in.readInt(),
                in.readInt()));
    }
}
