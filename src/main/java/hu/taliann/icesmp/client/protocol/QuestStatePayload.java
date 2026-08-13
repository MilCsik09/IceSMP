package hu.taliann.icesmp.client.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * QUEST_STATE: a vanilla questlog öt fülének display-projekciója (Aktív/Kész/
 * Megbízások/Elérhető/Teljesített) + a követett quest. Minden lista a szerveren már
 * láthatóság-szűrt ({@code isVisible} — HIDDEN quest és riddle-progressz sosem kerül
 * a vezetékre: a riddle-questnél a progressText maga a szerver-oldali „???”
 * placeholder). A fülönkénti lista a protokoll-limiten csonkolódik; a {@code *Total}
 * mezők a teljes darabszámot hordozzák, hogy a kliens jelezhesse a levágást (a teljes
 * lista a vanilla /quest log felületen mindig elérhető). Reward-előnézet szándékosan
 * nincs — a vanilla felület sem mutat ilyet.
 */
public record QuestStatePayload(
        String trackedQuestId,
        List<ActiveEntry> active,
        List<HintEntry> ready, int readyTotal,
        List<HintEntry> board, int boardTotal,
        List<HintEntry> available, int availableTotal,
        List<HintEntry> completed, int completedTotal) {

    /** Aktív quest: kész magyar progressz-szöveggel (riddle esetén szerveroldali „???”). */
    public record ActiveEntry(String questId, String title, String category,
                              String description, String progressText, boolean ready) {
    }

    /** Nem-aktív fülek sora: cím + fül-függő hint (leadás/felvétel helye, vagy üres). */
    public record HintEntry(String questId, String title, String category,
                            String description, String hint) {
    }

    public QuestStatePayload {
        active = List.copyOf(active);
        ready = List.copyOf(ready);
        board = List.copyOf(board);
        available = List.copyOf(available);
        completed = List.copyOf(completed);
    }

    public byte[] encode() {
        return ClientMessageCodec.encodePayload(out -> {
            ClientMessageCodec.writeString(out, trackedQuestId);
            writeCount(out, active.size());
            for (final ActiveEntry entry : active) {
                ClientMessageCodec.writeString(out, entry.questId());
                ClientMessageCodec.writeString(out, entry.title());
                ClientMessageCodec.writeString(out, entry.category());
                ClientMessageCodec.writeString(out, entry.description());
                ClientMessageCodec.writeString(out, entry.progressText());
                out.writeBoolean(entry.ready());
            }
            writeHintList(out, ready, readyTotal);
            writeHintList(out, board, boardTotal);
            writeHintList(out, available, availableTotal);
            writeHintList(out, completed, completedTotal);
        });
    }

    private static void writeHintList(final DataOutputStream out, final List<HintEntry> entries,
                                      final int total) throws IOException {
        writeCount(out, entries.size());
        for (final HintEntry entry : entries) {
            ClientMessageCodec.writeString(out, entry.questId());
            ClientMessageCodec.writeString(out, entry.title());
            ClientMessageCodec.writeString(out, entry.category());
            ClientMessageCodec.writeString(out, entry.description());
            ClientMessageCodec.writeString(out, entry.hint());
        }
        out.writeInt(total);
    }

    public static QuestStatePayload decode(final byte[] payload) throws ClientProtocolException {
        return ClientMessageCodec.decodePayload(payload, in -> {
            final String tracked = ClientMessageCodec.readString(in);
            final int activeCount = readCount(in);
            final List<ActiveEntry> active = new ArrayList<>(activeCount);
            for (int i = 0; i < activeCount; i++) {
                active.add(new ActiveEntry(
                        ClientMessageCodec.readString(in),
                        ClientMessageCodec.readString(in),
                        ClientMessageCodec.readString(in),
                        ClientMessageCodec.readString(in),
                        ClientMessageCodec.readString(in),
                        in.readBoolean()));
            }
            final List<HintEntry> ready = readHintList(in);
            final int readyTotal = in.readInt();
            final List<HintEntry> board = readHintList(in);
            final int boardTotal = in.readInt();
            final List<HintEntry> available = readHintList(in);
            final int availableTotal = in.readInt();
            final List<HintEntry> completed = readHintList(in);
            final int completedTotal = in.readInt();
            return new QuestStatePayload(tracked, active, ready, readyTotal, board, boardTotal,
                    available, availableTotal, completed, completedTotal);
        });
    }

    private static List<HintEntry> readHintList(final DataInputStream in)
            throws IOException, ClientProtocolException {
        final int count = readCount(in);
        final List<HintEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new HintEntry(
                    ClientMessageCodec.readString(in),
                    ClientMessageCodec.readString(in),
                    ClientMessageCodec.readString(in),
                    ClientMessageCodec.readString(in),
                    ClientMessageCodec.readString(in)));
        }
        return entries;
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
