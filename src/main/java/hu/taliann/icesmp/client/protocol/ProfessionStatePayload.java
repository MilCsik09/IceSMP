package hu.taliann.icesmp.client.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * PROFESSION_STATE: a szakma-áttekintő (ProfessionGUI + /profession info + /szakmacel)
 * display-projekciója. A teljes nyolc-szakmás roster utazik (a tanulható, de még nem
 * aktív főszakmák is — a vanilla GUI is mutatja őket), szintekkel, XP-bontással,
 * rang-névvel, recept-/tervrajz-számlálókkal és a céh heti közös céljával. A
 * {@code specOptions} csak a játékos aktívan gyakorolt szakmáinak specializációit
 * hordozza — más szakmák spec-fája kimarad. Recept-katalógus tétel-szinten nem része
 * a payloadnak (külön modul, protokoll-limit); csak darabszámok utaznak.
 */
public record ProfessionStatePayload(
        String specializationId, String specializationName,
        List<Entry> professions, List<SpecOption> specOptions) {

    /**
     * Egy szakma sora. {@code xpForNextLevel} a szintlépéshez szükséges teljes költség
     * (0 = max szint); a {@code weeklyGoal} 0, ha a szakmának nincs heti célja. A {@code slotTaken}
     * a nem aktív főszakmán jelzi, hogy a kategória-slot már foglalt (a tanulás így
     * elutasításra kerülne).
     */
    public record Entry(String professionId, String displayName, String category,
                        String categoryLabel, boolean active, boolean slotTaken,
                        int level, int maxLevel, String rankName,
                        long xpIntoLevel, long xpForNextLevel,
                        long weeklyProgress, long weeklyGoal,
                        int recipeTotal, int blueprintKnown, int blueprintTotal) {
    }

    /** Választható szakma-specializáció a játékos aktív szakmáihoz. */
    public record SpecOption(String specializationId, String displayName,
                             String parentProfessionId, int requiredLevel, boolean selectable) {
    }

    public ProfessionStatePayload {
        professions = List.copyOf(professions);
        specOptions = List.copyOf(specOptions);
    }

    public byte[] encode() {
        return ClientMessageCodec.encodePayload(out -> {
            ClientMessageCodec.writeString(out, specializationId);
            ClientMessageCodec.writeString(out, specializationName);
            writeCount(out, professions.size());
            for (final Entry entry : professions) {
                ClientMessageCodec.writeString(out, entry.professionId());
                ClientMessageCodec.writeString(out, entry.displayName());
                ClientMessageCodec.writeString(out, entry.category());
                ClientMessageCodec.writeString(out, entry.categoryLabel());
                out.writeBoolean(entry.active());
                out.writeBoolean(entry.slotTaken());
                out.writeInt(entry.level());
                out.writeInt(entry.maxLevel());
                ClientMessageCodec.writeString(out, entry.rankName());
                out.writeLong(entry.xpIntoLevel());
                out.writeLong(entry.xpForNextLevel());
                out.writeLong(entry.weeklyProgress());
                out.writeLong(entry.weeklyGoal());
                out.writeInt(entry.recipeTotal());
                out.writeInt(entry.blueprintKnown());
                out.writeInt(entry.blueprintTotal());
            }
            writeCount(out, specOptions.size());
            for (final SpecOption option : specOptions) {
                ClientMessageCodec.writeString(out, option.specializationId());
                ClientMessageCodec.writeString(out, option.displayName());
                ClientMessageCodec.writeString(out, option.parentProfessionId());
                out.writeInt(option.requiredLevel());
                out.writeBoolean(option.selectable());
            }
        });
    }

    public static ProfessionStatePayload decode(final byte[] payload) throws ClientProtocolException {
        return ClientMessageCodec.decodePayload(payload, in -> {
            final String specId = ClientMessageCodec.readString(in);
            final String specName = ClientMessageCodec.readString(in);
            final int professionCount = readCount(in);
            final List<Entry> professions = new ArrayList<>(professionCount);
            for (int i = 0; i < professionCount; i++) {
                professions.add(new Entry(
                        ClientMessageCodec.readString(in),
                        ClientMessageCodec.readString(in),
                        ClientMessageCodec.readString(in),
                        ClientMessageCodec.readString(in),
                        in.readBoolean(),
                        in.readBoolean(),
                        in.readInt(),
                        in.readInt(),
                        ClientMessageCodec.readString(in),
                        in.readLong(),
                        in.readLong(),
                        in.readLong(),
                        in.readLong(),
                        in.readInt(),
                        in.readInt(),
                        in.readInt()));
            }
            final int optionCount = readCount(in);
            final List<SpecOption> options = new ArrayList<>(optionCount);
            for (int i = 0; i < optionCount; i++) {
                options.add(new SpecOption(
                        ClientMessageCodec.readString(in),
                        ClientMessageCodec.readString(in),
                        ClientMessageCodec.readString(in),
                        in.readInt(),
                        in.readBoolean()));
            }
            return new ProfessionStatePayload(specId, specName, professions, options);
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
