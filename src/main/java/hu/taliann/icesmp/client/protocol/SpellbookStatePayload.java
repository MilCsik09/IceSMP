package hu.taliann.icesmp.client.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * SPELLBOOK_STATE: a vanilla spellbook-GUI-val azonos, rendezett kaszt+spec
 * spell-katalógus display-projekciója. Csak a játékos által amúgy is látható adat
 * utazik (a vanilla GUI a zárolt spelleket is mutatja a szint-követelménnyel); a
 * leírás-sorok kész magyar megjelenítési szövegek. Nem tick-cadence: kézfogáskor,
 * resynckor és releváns változáskor (unlock/kedvenc/kiválasztás/kit) megy ki.
 */
public record SpellbookStatePayload(List<Entry> entries) {

    /** Egy spellbook-sor; {@code selected}/{@code inActiveKit} csak feloldott spellen lehet igaz. */
    public record Entry(String spellId, String name, List<String> description,
                        int requiredLevel, boolean unlocked, boolean favorite,
                        boolean selected, boolean inActiveKit, int masteryRank,
                        String costText, int cooldownSeconds) {

        public Entry {
            description = List.copyOf(description);
        }
    }

    public SpellbookStatePayload {
        entries = List.copyOf(entries);
    }

    public byte[] encode() {
        return ClientMessageCodec.encodePayload(out -> {
            writeCount(out, entries.size());
            for (final Entry entry : entries) {
                ClientMessageCodec.writeString(out, entry.spellId());
                ClientMessageCodec.writeString(out, entry.name());
                ClientMessageCodec.writeStringList(out, entry.description());
                out.writeInt(entry.requiredLevel());
                out.writeBoolean(entry.unlocked());
                out.writeBoolean(entry.favorite());
                out.writeBoolean(entry.selected());
                out.writeBoolean(entry.inActiveKit());
                out.writeInt(entry.masteryRank());
                ClientMessageCodec.writeString(out, entry.costText());
                out.writeInt(entry.cooldownSeconds());
            }
        });
    }

    public static SpellbookStatePayload decode(final byte[] payload) throws ClientProtocolException {
        return ClientMessageCodec.decodePayload(payload, in -> {
            final int count = readCount(in);
            final List<Entry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                entries.add(new Entry(
                        ClientMessageCodec.readString(in),
                        ClientMessageCodec.readString(in),
                        ClientMessageCodec.readStringList(in),
                        in.readInt(),
                        in.readBoolean(),
                        in.readBoolean(),
                        in.readBoolean(),
                        in.readBoolean(),
                        in.readInt(),
                        ClientMessageCodec.readString(in),
                        in.readInt()));
            }
            return new SpellbookStatePayload(entries);
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
