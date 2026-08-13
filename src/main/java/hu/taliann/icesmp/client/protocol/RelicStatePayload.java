package hu.taliann.icesmp.client.protocol;

/**
 * RELIC_STATE: a SAJÁT játékos class-relic aktivációjának display-projekciója
 * (a {@code ClassRelicActivation} döntési rekord tükre + megjelenítési név).
 * Üres {@code relicId} = a kaszthoz nincs kötött class-relic. A {@code dormantReason}
 * gépi kód (NONE = aktív); a kliens lokalizálja. Szándékosan csak saját-játékos
 * state: más viselők a külön RELIC_ATTACHMENT_STATE csatornán utaznak. Nyers
 * katalógus-konfiguráció (award-számok, resonance-lista) nem utazik — csak a
 * feloldott aktiváció-eredmény. Az {@code awakeningRemainingMillis} a küldés
 * pillanatában hátralévő Awakening-cooldown (0 = kész vagy nincs konfigurálva);
 * a kliens a fogadás idejétől interpolál, órák szinkronjára nem támaszkodunk.
 */
public record RelicStatePayload(
        String relicId, String displayName, String classId, String activeSpecializationId,
        boolean basePowerActive, String resonanceId, boolean resonanceActive,
        boolean awakeningConfigured, String dormantReason, long awakeningRemainingMillis) {

    /**
     * Dedupe-szignatúra a push-hoz: a tick-enként fogyó awakening-maradék 0/1-re
     * normalizálva — futó cooldown alatt nem generál forgalmat, a kliens a fogadás
     * idejétől interpolál (az ability-kit mintája).
     */
    public RelicStatePayload changeSignature() {
        return new RelicStatePayload(relicId, displayName, classId, activeSpecializationId,
                basePowerActive, resonanceId, resonanceActive, awakeningConfigured, dormantReason,
                awakeningRemainingMillis > 0L ? 1L : 0L);
    }

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
            out.writeLong(awakeningRemainingMillis);
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
                ClientMessageCodec.readString(in),
                in.readLong()));
    }
}
