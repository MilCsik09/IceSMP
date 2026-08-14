package hu.taliann.icesmp.client.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * PROFILE_STATE: a /profile GUI fejlécével és egyenleg-nézetével azonos tartalmú,
 * read-only karakter-projekció. Kizárólag display-adat: revision/CAS, operation-receipt,
 * moderációs mező és rejtett quest-state SOHA nem kerülhet bele (PlayerProfile
 * authority-szabály). Az üres string „nincs”-ként renderelendő a kliensen.
 */
public record ProfileStatePayload(
        String playerName, String faction, String factionId,
        String className, int classLevel, int classMaxLevel, String classSpecName,
        String gatheringProfession, int gatheringLevel,
        String craftingProfession, int craftingLevel, String professionSpecName,
        boolean sinner, int classTalentPoints, int professionTalentPoints,
        List<Balance> balances, Stats stats,
        int achievementsEarned, int achievementsTotal) {

    /** Egy valuta-sor; az {@code amount} a szerveren formázott megjelenítési string. */
    public record Balance(String label, String amount) {
    }

    /** Életre szóló publikus számlálók — azonosak a StatsManager leaderboard-projekciójával. */
    public record Stats(int kills, int deaths, int mobKills, int spellCasts,
                        int questsCompleted, int raidKills) {
    }

    public ProfileStatePayload {
        balances = List.copyOf(balances);
    }

    public byte[] encode() {
        return ClientMessageCodec.encodePayload(out -> {
            ClientMessageCodec.writeString(out, playerName);
            ClientMessageCodec.writeString(out, faction);
            ClientMessageCodec.writeString(out, factionId);
            ClientMessageCodec.writeString(out, className);
            out.writeInt(classLevel);
            out.writeInt(classMaxLevel);
            ClientMessageCodec.writeString(out, classSpecName);
            ClientMessageCodec.writeString(out, gatheringProfession);
            out.writeInt(gatheringLevel);
            ClientMessageCodec.writeString(out, craftingProfession);
            out.writeInt(craftingLevel);
            ClientMessageCodec.writeString(out, professionSpecName);
            out.writeBoolean(sinner);
            out.writeInt(classTalentPoints);
            out.writeInt(professionTalentPoints);
            writeCount(out, balances.size());
            for (final Balance balance : balances) {
                ClientMessageCodec.writeString(out, balance.label());
                ClientMessageCodec.writeString(out, balance.amount());
            }
            out.writeInt(stats.kills());
            out.writeInt(stats.deaths());
            out.writeInt(stats.mobKills());
            out.writeInt(stats.spellCasts());
            out.writeInt(stats.questsCompleted());
            out.writeInt(stats.raidKills());
            out.writeInt(achievementsEarned);
            out.writeInt(achievementsTotal);
        });
    }

    public static ProfileStatePayload decode(final byte[] payload) throws ClientProtocolException {
        return ClientMessageCodec.decodePayload(payload, in -> {
            final String playerName = ClientMessageCodec.readString(in);
            final String faction = ClientMessageCodec.readString(in);
            final String factionId = ClientMessageCodec.readString(in);
            final String className = ClientMessageCodec.readString(in);
            final int classLevel = in.readInt();
            final int classMaxLevel = in.readInt();
            final String classSpecName = ClientMessageCodec.readString(in);
            final String gatheringProfession = ClientMessageCodec.readString(in);
            final int gatheringLevel = in.readInt();
            final String craftingProfession = ClientMessageCodec.readString(in);
            final int craftingLevel = in.readInt();
            final String professionSpecName = ClientMessageCodec.readString(in);
            final boolean sinner = in.readBoolean();
            final int classTalentPoints = in.readInt();
            final int professionTalentPoints = in.readInt();
            final int balanceCount = readCount(in);
            final List<Balance> balances = new ArrayList<>(balanceCount);
            for (int i = 0; i < balanceCount; i++) {
                balances.add(new Balance(
                        ClientMessageCodec.readString(in),
                        ClientMessageCodec.readString(in)));
            }
            final Stats stats = new Stats(in.readInt(), in.readInt(), in.readInt(),
                    in.readInt(), in.readInt(), in.readInt());
            return new ProfileStatePayload(playerName, faction, factionId, className, classLevel,
                    classMaxLevel, classSpecName, gatheringProfession, gatheringLevel,
                    craftingProfession, craftingLevel, professionSpecName, sinner,
                    classTalentPoints, professionTalentPoints, balances, stats,
                    in.readInt(), in.readInt());
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
