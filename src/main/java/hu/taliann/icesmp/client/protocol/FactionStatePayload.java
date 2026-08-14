package hu.taliann.icesmp.client.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * FACTION_STATE: a saját frakció display-projekciója — az az adatkör, amit a
 * /menu frakció-fejléce, a /faction king|treasury|raid status|war és az /events
 * szezon-állása mutat. {@code hasFaction=false} = Menedék-vendég (a frakció-mezők
 * üresek; a szezon-blokk akkor is utazik, mert publikus broadcast-adat). A
 * PlayerProfile-internals (membership-history, receipts, váltás-számlálók) nem
 * kerülnek a vezetékre. Frakció-mutáció (join/leave) szándékosan NEM protokoll-action:
 * a csatlakozás forrás-kötött (csak a Menedék fővárosában validált), egy
 * kliens-csomag hely-authority bypass lenne.
 */
public record FactionStatePayload(
        boolean hasFaction, String factionCode, String factionLabel, String factionFullName,
        String treasuryBalance, double taxRatePercent,
        boolean kingExcluded, String kingName, List<Tally> kingTally,
        int seasonNumber, int seasonDay, int seasonRemainingDays, boolean grandFinale,
        List<SeasonRow> seasonRows,
        boolean raidActive, boolean raidWarmup, String raidTerritoryName,
        String raidAttackerLabel, String raidDefenderLabel,
        int raidAttackerPoints, int raidDefenderPoints,
        int raidAttackerFighters, int raidDefenderFighters, int raidMaxPerSide,
        int raidRemainingMinutes,
        boolean warWindowActive, int warWindowMinutesUntilNext) {

    /** Egy király-szavazat sor (jelölt plain neve + szavazatszám). */
    public record Tally(String candidateName, int votes) {
    }

    /** Szezon-állás sor — mind a négy frakcióra publikus. */
    public record SeasonRow(String factionCode, String factionLabel, int points) {
    }

    public FactionStatePayload {
        kingTally = List.copyOf(kingTally);
        seasonRows = List.copyOf(seasonRows);
    }

    public byte[] encode() {
        return ClientMessageCodec.encodePayload(out -> {
            out.writeBoolean(hasFaction);
            ClientMessageCodec.writeString(out, factionCode);
            ClientMessageCodec.writeString(out, factionLabel);
            ClientMessageCodec.writeString(out, factionFullName);
            ClientMessageCodec.writeString(out, treasuryBalance);
            out.writeDouble(taxRatePercent);
            out.writeBoolean(kingExcluded);
            ClientMessageCodec.writeString(out, kingName);
            writeCount(out, kingTally.size());
            for (final Tally tally : kingTally) {
                ClientMessageCodec.writeString(out, tally.candidateName());
                out.writeInt(tally.votes());
            }
            out.writeInt(seasonNumber);
            out.writeInt(seasonDay);
            out.writeInt(seasonRemainingDays);
            out.writeBoolean(grandFinale);
            writeCount(out, seasonRows.size());
            for (final SeasonRow row : seasonRows) {
                ClientMessageCodec.writeString(out, row.factionCode());
                ClientMessageCodec.writeString(out, row.factionLabel());
                out.writeInt(row.points());
            }
            out.writeBoolean(raidActive);
            out.writeBoolean(raidWarmup);
            ClientMessageCodec.writeString(out, raidTerritoryName);
            ClientMessageCodec.writeString(out, raidAttackerLabel);
            ClientMessageCodec.writeString(out, raidDefenderLabel);
            out.writeInt(raidAttackerPoints);
            out.writeInt(raidDefenderPoints);
            out.writeInt(raidAttackerFighters);
            out.writeInt(raidDefenderFighters);
            out.writeInt(raidMaxPerSide);
            out.writeInt(raidRemainingMinutes);
            out.writeBoolean(warWindowActive);
            out.writeInt(warWindowMinutesUntilNext);
        });
    }

    public static FactionStatePayload decode(final byte[] payload) throws ClientProtocolException {
        return ClientMessageCodec.decodePayload(payload, in -> {
            final boolean hasFaction = in.readBoolean();
            final String factionCode = ClientMessageCodec.readString(in);
            final String factionLabel = ClientMessageCodec.readString(in);
            final String factionFullName = ClientMessageCodec.readString(in);
            final String treasuryBalance = ClientMessageCodec.readString(in);
            final double taxRatePercent = in.readDouble();
            final boolean kingExcluded = in.readBoolean();
            final String kingName = ClientMessageCodec.readString(in);
            final int tallyCount = readCount(in);
            final List<Tally> kingTally = new ArrayList<>(tallyCount);
            for (int i = 0; i < tallyCount; i++) {
                kingTally.add(new Tally(ClientMessageCodec.readString(in), in.readInt()));
            }
            final int seasonNumber = in.readInt();
            final int seasonDay = in.readInt();
            final int seasonRemainingDays = in.readInt();
            final boolean grandFinale = in.readBoolean();
            final int rowCount = readCount(in);
            final List<SeasonRow> seasonRows = new ArrayList<>(rowCount);
            for (int i = 0; i < rowCount; i++) {
                seasonRows.add(new SeasonRow(ClientMessageCodec.readString(in),
                        ClientMessageCodec.readString(in), in.readInt()));
            }
            return new FactionStatePayload(hasFaction, factionCode, factionLabel, factionFullName,
                    treasuryBalance, taxRatePercent, kingExcluded, kingName, kingTally,
                    seasonNumber, seasonDay, seasonRemainingDays, grandFinale, seasonRows,
                    in.readBoolean(), in.readBoolean(), ClientMessageCodec.readString(in),
                    ClientMessageCodec.readString(in), ClientMessageCodec.readString(in),
                    in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt(),
                    in.readInt(), in.readBoolean(), in.readInt());
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
