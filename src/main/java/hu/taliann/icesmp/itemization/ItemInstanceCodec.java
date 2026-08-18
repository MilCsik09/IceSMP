package hu.taliann.icesmp.itemization;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ItemInstanceCodec {

    private static final int MAGIC = 0x49435332;
    private static final int MAX_PAYLOAD_BYTES = 65_536;

    private ItemInstanceCodec() {
    }

    public static String encode(final ItemInstance instance) {
        try {
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(MAGIC);
                out.writeInt(instance.schemaVersion());
                writeUuid(out, instance.itemId());
                out.writeUTF(instance.templateId());
                out.writeInt(instance.templateVersion());
                out.writeInt(instance.itemLevel());
                out.writeInt(instance.rolls().size());
                for (final Map.Entry<String, ItemInstance.Roll> entry : instance.rolls().entrySet()) {
                    out.writeUTF(entry.getKey());
                    out.writeDouble(entry.getValue().value());
                    out.writeDouble(entry.getValue().quality());
                }
                out.writeInt(instance.runes().size());
                for (final String rune : instance.runes()) out.writeUTF(rune);
                out.writeUTF(instance.ascension().stageId());
                out.writeInt(instance.ascension().stageIndex());
                out.writeUTF(instance.origin().sourceTag());
                out.writeUTF(instance.origin().sourceId());
                out.writeBoolean(instance.origin().crafterId() != null);
                if (instance.origin().crafterId() != null) writeUuid(out, instance.origin().crafterId());
                out.writeUTF(instance.origin().creationLocation());
                out.writeLong(instance.origin().createdAt());
                out.writeInt(instance.states().size());
                for (final ItemState state : instance.states()) out.writeUTF(state.name());
                out.writeLong(instance.mutationRevision());
                out.writeInt(instance.history().size());
                for (final ItemHistoryEvent event : instance.history()) {
                    out.writeUTF(event.type().name());
                    out.writeLong(event.occurredAt());
                    out.writeUTF(event.detail());
                }
                if (instance.schemaVersion() >= 2) {
                    out.writeUTF(instance.origin().crafterNameSnapshot());
                    out.writeUTF(instance.origin().professionId());
                    out.writeBoolean(instance.origin().masterwork());
                    out.writeInt(instance.mutation().rerollCount());
                    out.writeInt(instance.mutation().rerollCostStep());
                    out.writeInt(instance.mutation().recentOperationReceipts().size());
                    for (final UUID receipt : instance.mutation().recentOperationReceipts()) {
                        writeUuid(out, receipt);
                    }
                }
            }
            if (bytes.size() > MAX_PAYLOAD_BYTES) throw new IllegalArgumentException("item payload exceeds limit");
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
        } catch (final IOException impossible) {
            throw new IllegalStateException("cannot encode item instance", impossible);
        }
    }

    public static ItemInstance decode(final String encoded) {
        if (encoded == null || encoded.isBlank()) throw new IllegalArgumentException("item payload is required");
        final byte[] bytes;
        try {
            bytes = Base64.getUrlDecoder().decode(encoded);
        } catch (final IllegalArgumentException malformed) {
            throw new IllegalArgumentException("invalid item payload encoding", malformed);
        }
        if (bytes.length == 0 || bytes.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("invalid item payload size");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC) throw new IllegalArgumentException("invalid item payload magic");
            final int schema = in.readInt();
            final UUID itemId = readUuid(in);
            final String templateId = in.readUTF();
            final int templateVersion = in.readInt();
            final int itemLevel = in.readInt();
            final LinkedHashMap<String, ItemInstance.Roll> rolls = new LinkedHashMap<>();
            final int rollCount = boundedCount(in.readInt(), 32, "roll");
            for (int i = 0; i < rollCount; i++) {
                if (rolls.putIfAbsent(in.readUTF(), new ItemInstance.Roll(in.readDouble(), in.readDouble())) != null) {
                    throw new IllegalArgumentException("duplicate item roll in payload");
                }
            }
            final int runeCount = boundedCount(in.readInt(), 2, "rune");
            final ArrayList<String> runes = new ArrayList<>();
            for (int i = 0; i < runeCount; i++) runes.add(in.readUTF());
            final ItemInstance.AscensionState ascension = new ItemInstance.AscensionState(
                    in.readUTF(), in.readInt());
            final String sourceTag = in.readUTF();
            final String sourceId = in.readUTF();
            final UUID crafter = in.readBoolean() ? readUuid(in) : null;
            final String creationLocation = in.readUTF();
            final long createdAt = in.readLong();
            final int stateCount = boundedCount(in.readInt(), ItemState.values().length, "state");
            final EnumSet<ItemState> states = EnumSet.noneOf(ItemState.class);
            for (int i = 0; i < stateCount; i++) {
                if (!states.add(ItemState.valueOf(in.readUTF()))) {
                    throw new IllegalArgumentException("duplicate item state in payload");
                }
            }
            final long revision = in.readLong();
            final int historyCount = boundedCount(in.readInt(), ItemInstance.MAX_HISTORY, "history");
            final ArrayList<ItemHistoryEvent> history = new ArrayList<>();
            for (int i = 0; i < historyCount; i++) {
                history.add(new ItemHistoryEvent(ItemHistoryEvent.Type.valueOf(in.readUTF()),
                        in.readLong(), in.readUTF()));
            }
            String crafterName = "";
            String profession = "";
            boolean masterwork = false;
            ItemInstance.MutationState mutation = ItemInstance.MutationState.fresh();
            if (schema >= 2) {
                crafterName = in.readUTF();
                profession = in.readUTF();
                masterwork = in.readBoolean();
                final int rerollCount = in.readInt();
                final int rerollCostStep = in.readInt();
                final int receiptCount = boundedCount(in.readInt(),
                        ItemInstance.MAX_OPERATION_RECEIPTS, "operation receipt");
                final ArrayList<UUID> receipts = new ArrayList<>();
                for (int i = 0; i < receiptCount; i++) receipts.add(readUuid(in));
                mutation = new ItemInstance.MutationState(rerollCount, rerollCostStep, receipts);
            }
            if (in.available() != 0) throw new IllegalArgumentException("trailing data in item payload");
            return new ItemInstance(itemId, schema, templateId, templateVersion, itemLevel,
                    rolls, runes, ascension,
                    new ItemInstance.Origin(sourceTag, sourceId, crafter, crafterName, profession,
                            creationLocation, masterwork, createdAt),
                    states.isEmpty() ? Set.of() : states, revision, history, mutation);
        } catch (final IOException | RuntimeException malformed) {
            if (malformed instanceof IllegalArgumentException illegal) throw illegal;
            throw new IllegalArgumentException("invalid item payload", malformed);
        }
    }

    private static int boundedCount(final int count, final int max, final String label) {
        if (count < 0 || count > max) throw new IllegalArgumentException("invalid " + label + " count");
        return count;
    }

    private static void writeUuid(final DataOutputStream out, final UUID value) throws IOException {
        out.writeLong(value.getMostSignificantBits());
        out.writeLong(value.getLeastSignificantBits());
    }

    private static UUID readUuid(final DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }
}
