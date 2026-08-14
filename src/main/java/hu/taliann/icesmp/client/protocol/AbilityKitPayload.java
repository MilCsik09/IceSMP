package hu.taliann.icesmp.client.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * ABILITY_KIT_STATE: a játékos futásidőben számolt aktív kitjének display-projekciója
 * az ability barhoz. A slot-index a lista pozíciója (1-alapú a vezetéken kívül is így
 * hivatkozzuk) — a kliens CAST_SLOT kérése erre a sorrendre mutat vissza. A
 * {@code cooldownRemainingMs} a küldés pillanatában érvényes maradék; a kliens ebből
 * lokálisan interpolál (readyAt = érkezés + remaining), a vizuális timer sosem authority.
 */
public record AbilityKitPayload(List<Entry> entries) {

    /** Egy ability-bar slot; {@code costText} üres, ha a spell ingyenes. */
    public record Entry(String spellId, String name, String costText,
                        long cooldownTotalMs, long cooldownRemainingMs, boolean selected) {
    }

    public AbilityKitPayload {
        entries = List.copyOf(entries);
    }

    public byte[] encode() {
        return ClientMessageCodec.encodePayload(out -> {
            writeCount(out, entries.size());
            for (final Entry entry : entries) {
                ClientMessageCodec.writeString(out, entry.spellId());
                ClientMessageCodec.writeString(out, entry.name());
                ClientMessageCodec.writeString(out, entry.costText());
                out.writeLong(entry.cooldownTotalMs());
                out.writeLong(entry.cooldownRemainingMs());
                out.writeBoolean(entry.selected());
            }
        });
    }

    public static AbilityKitPayload decode(final byte[] payload) throws ClientProtocolException {
        return ClientMessageCodec.decodePayload(payload, in -> {
            final int count = readCount(in);
            final List<Entry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                entries.add(new Entry(
                        ClientMessageCodec.readString(in),
                        ClientMessageCodec.readString(in),
                        ClientMessageCodec.readString(in),
                        in.readLong(),
                        in.readLong(),
                        in.readBoolean()));
            }
            return new AbilityKitPayload(entries);
        });
    }

    /**
     * A change-detektálás alapja: a folyamatosan csökkenő maradék-cooldownt 0/1-re
     * normalizálja, így a dedupe csak összetétel-, kiválasztás- vagy
     * cooldown-állapotváltásra (indul/lejár) jelez — a vezetékre nem megy másodpercenkénti
     * timer-frissítés (a kliens interpolál).
     */
    public AbilityKitPayload changeSignature() {
        final List<Entry> normalized = new ArrayList<>(entries.size());
        for (final Entry entry : entries) {
            normalized.add(new Entry(entry.spellId(), entry.name(), entry.costText(),
                    entry.cooldownTotalMs(), entry.cooldownRemainingMs() > 0L ? 1L : 0L,
                    entry.selected()));
        }
        return new AbilityKitPayload(normalized);
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
