package hu.taliann.icesmp.client.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * TALENT_STATE: a két talent-pool (kaszt/szakma) display-projekciója. A vanilla
 * talent-GUI-val egyezően KIZÁRÓLAG a játékos aktuális kasztjához/szakmájához
 * releváns (isAvailable-szűrt) talentek utaznak — a teljes katalógus nemcsak a
 * protokoll-limitet sértené, más kasztok fáját is felfedné. A {@code perRank} a
 * rangonkénti hatásmérték; az {@code effect} stabil mechanika-kulcs, a megjelenítési
 * címke kliensoldali lokalizáció.
 */
public record TalentStatePayload(
        int classAvailablePoints, int classSpentPoints,
        int professionAvailablePoints, int professionSpentPoints,
        List<Entry> classTalents, List<Entry> professionTalents) {

    /** Egy talent-sor; {@code unlocked} = a fa-gate (szülő/kizárás/küszöb) is teljesül. */
    public record Entry(String talentId, String displayName, int tier, int rank, int maxRank,
                        String effect, double perRank, boolean unlocked,
                        String requiresTalentId, List<String> excludes, int requiresSpent,
                        boolean grantsActive) {

        public Entry {
            excludes = List.copyOf(excludes);
        }
    }

    public TalentStatePayload {
        classTalents = List.copyOf(classTalents);
        professionTalents = List.copyOf(professionTalents);
    }

    public byte[] encode() {
        return ClientMessageCodec.encodePayload(out -> {
            out.writeInt(classAvailablePoints);
            out.writeInt(classSpentPoints);
            out.writeInt(professionAvailablePoints);
            out.writeInt(professionSpentPoints);
            writeEntries(out, classTalents);
            writeEntries(out, professionTalents);
        });
    }

    private static void writeEntries(final DataOutputStream out, final List<Entry> entries)
            throws IOException {
        if (entries.size() > ClientProtocol.MAX_LIST_ELEMENTS) {
            throw new IOException("list exceeds protocol limit");
        }
        out.writeInt(entries.size());
        for (final Entry entry : entries) {
            ClientMessageCodec.writeString(out, entry.talentId());
            ClientMessageCodec.writeString(out, entry.displayName());
            out.writeInt(entry.tier());
            out.writeInt(entry.rank());
            out.writeInt(entry.maxRank());
            ClientMessageCodec.writeString(out, entry.effect());
            out.writeDouble(entry.perRank());
            out.writeBoolean(entry.unlocked());
            ClientMessageCodec.writeString(out, entry.requiresTalentId());
            ClientMessageCodec.writeStringList(out, entry.excludes());
            out.writeInt(entry.requiresSpent());
            out.writeBoolean(entry.grantsActive());
        }
    }

    public static TalentStatePayload decode(final byte[] payload) throws ClientProtocolException {
        return ClientMessageCodec.decodePayload(payload, in -> new TalentStatePayload(
                in.readInt(), in.readInt(), in.readInt(), in.readInt(),
                readEntries(in), readEntries(in)));
    }

    private static List<Entry> readEntries(final DataInputStream in)
            throws IOException, ClientProtocolException {
        final int count = in.readInt();
        if (count < 0 || count > ClientProtocol.MAX_LIST_ELEMENTS) {
            throw new ClientProtocolException("list length out of bounds: " + count);
        }
        final List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new Entry(
                    ClientMessageCodec.readString(in),
                    ClientMessageCodec.readString(in),
                    in.readInt(),
                    in.readInt(),
                    in.readInt(),
                    ClientMessageCodec.readString(in),
                    in.readDouble(),
                    in.readBoolean(),
                    ClientMessageCodec.readString(in),
                    ClientMessageCodec.readStringList(in),
                    in.readInt(),
                    in.readBoolean()));
        }
        return entries;
    }
}
