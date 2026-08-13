package hu.taliann.icesmp.client.protocol;

/**
 * RELIC_STATE: a SAJÁT játékos class-relic aktivációjának display-projekciója
 * (a {@code ClassRelicActivation} döntési rekord tükre + megjelenítési név).
 * Üres {@code relicId} = a kaszthoz nincs kötött class-relic. A {@code dormantReason}
 * gépi kód (NONE = aktív); a kliens lokalizálja. Szándékosan csak saját-játékos
 * state: más viselők attachment-broadcastja külön kézbesítési infrastruktúrát
 * igényelne, az egy későbbi fázis. Nyers katalógus-konfiguráció (award-számok,
 * resonance-lista) nem utazik — csak a feloldott aktiváció-eredmény.
 */
public record RelicStatePayload(
        String relicId, String displayName, String classId, String activeSpecializationId,
        boolean basePowerActive, String resonanceId, boolean resonanceActive,
        boolean awakeningConfigured, String dormantReason) {

    public byte[] encode() {
        return ClientMessageCodec.encodePayload(out -> {
            ClientMessageCodec.writeString(out, relicId);
            ClientMessageCodec.writeString(out, displayName);
            ClientMessageCodec.writeString(out, classId);
            ClientMessageCodec.writeString(out, activeSpecializationId);
            out.writeBoolean(basePowerActive);
            ClientMessageCodec.writeString(out, resonanceId);
            out.writeBoolean(resonanceActive);
            out.writeBoolean(awakeningConfigured);
            ClientMessageCodec.writeString(out, dormantReason);
        });
    }

    public static RelicStatePayload decode(final byte[] payload) throws ClientProtocolException {
        return ClientMessageCodec.decodePayload(payload, in -> new RelicStatePayload(
                ClientMessageCodec.readString(in),
                ClientMessageCodec.readString(in),
                ClientMessageCodec.readString(in),
                ClientMessageCodec.readString(in),
                in.readBoolean(),
                ClientMessageCodec.readString(in),
                in.readBoolean(),
                in.readBoolean(),
                ClientMessageCodec.readString(in)));
    }
}
