package hu.taliann.icesmp.client.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * PARTY_STATE: a party-frame strukturált display-projekciója — ugyanaz az adatkör,
 * amit a vanilla HUD party-sorai mutatnak (👑/tag-jelölés, név, életerő), csak
 * szöveg helyett mezőkben. Az életerő fél-szívekre kvantált ({@code ceil(hp/2)} —
 * a vanilla sor is így jelenít), így a regen-tickek nem generálnak forgalmat.
 * Üres taglista = a játékos nincs partyban. A {@code healthKnown} false, ha a tag
 * adata az adott tickben nem volt olvasható (régió-átmenet) — a vanilla sor ilyenkor
 * „…”-t mutat. Party-mutáció (invite/kick/…) nem része a protokollnak: a /party
 * parancs marad az egyetlen út.
 */
public record PartyStatePayload(int maxSize, List<Member> members) {

    /** Egy party-tag sora; offline tagnál a név üres lehet és healthKnown=false. */
    public record Member(UUID playerId, String name, boolean leader, boolean online,
                         boolean healthKnown, int healthHalves, int maxHealthHalves) {
    }

    public PartyStatePayload {
        members = List.copyOf(members);
    }

    public byte[] encode() {
        return ClientMessageCodec.encodePayload(out -> {
            out.writeInt(maxSize);
            writeCount(out, members.size());
            for (final Member member : members) {
                out.writeLong(member.playerId().getMostSignificantBits());
                out.writeLong(member.playerId().getLeastSignificantBits());
                ClientMessageCodec.writeString(out, member.name());
                out.writeBoolean(member.leader());
                out.writeBoolean(member.online());
                out.writeBoolean(member.healthKnown());
                out.writeInt(member.healthHalves());
                out.writeInt(member.maxHealthHalves());
            }
        });
    }

    public static PartyStatePayload decode(final byte[] payload) throws ClientProtocolException {
        return ClientMessageCodec.decodePayload(payload, in -> {
            final int maxSize = in.readInt();
            final int count = readCount(in);
            final List<Member> members = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                members.add(new Member(
                        new UUID(in.readLong(), in.readLong()),
                        ClientMessageCodec.readString(in),
                        in.readBoolean(),
                        in.readBoolean(),
                        in.readBoolean(),
                        in.readInt(),
                        in.readInt()));
            }
            return new PartyStatePayload(maxSize, members);
        });
    }

    private static void writeCount(final DataOutputStream out, final int count) throws IOException {
        if (count > ClientProtocol.MAX_LIST_ELEMENTS) {
            throw new IOException("list exceeds protocol limit");
        }
        out.writeInt(count);
    }

    private static int readCount(final DataInputStream in) throws IOException, ClientProtocolException {
        final int count = in.readInt();
        if (count < 0 || count > ClientProtocol.MAX_LIST_ELEMENTS) {
            throw new ClientProtocolException("list length out of bounds: " + count);
        }
        return count;
    }
}
