package hu.taliann.icesmp.client.protocol;

/**
 * BOSS_STATE: a világboss-encounter strukturált display-projekciója — ugyanaz az
 * adatkör, amit a vanilla megosztott boss-bar mutat (☠ + HP%), kiegészítve a boss
 * plain nevével, archetípus-kulcsával és a második-fázis (dühöngés) jelzéssel,
 * amelyeket a szerver eddig is broadcastolt szövegként. A HP egész százalékra
 * kvantált (a vanilla bar felbontása), így a sebzés-tickek nem árasztják el a
 * vezetéket. {@code active=false} = nincs élő világboss (a többi mező üres/0).
 * A vanilla bar globális broadcast — a frame köre is az; kazamata mini-bossnak
 * nincs vanilla felülete, ezért a frame-ben sem szerepel (display-paritás).
 */
public record BossStatePayload(boolean active, String archetypeId, String displayName,
                               int healthPercent, boolean enraged) {

    public byte[] encode() {
        return ClientMessageCodec.encodePayload(out -> {
            out.writeBoolean(active);
            ClientMessageCodec.writeString(out, archetypeId);
            ClientMessageCodec.writeString(out, displayName);
            out.writeInt(healthPercent);
            out.writeBoolean(enraged);
        });
    }

    public static BossStatePayload decode(final byte[] payload) throws ClientProtocolException {
        return ClientMessageCodec.decodePayload(payload, in -> new BossStatePayload(
                in.readBoolean(),
                ClientMessageCodec.readString(in),
                ClientMessageCodec.readString(in),
                in.readInt(),
                in.readBoolean()));
    }
}
