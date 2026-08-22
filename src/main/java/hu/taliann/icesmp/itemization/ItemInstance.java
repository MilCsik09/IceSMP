package hu.taliann.icesmp.itemization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record ItemInstance(
        UUID itemId,
        int schemaVersion,
        String templateId,
        int templateVersion,
        int itemLevel,
        Map<String, Roll> rolls,
        List<String> runes,
        AscensionState ascension,
        Origin origin,
        Set<ItemState> states,
        long mutationRevision,
        List<ItemHistoryEvent> history,
        MutationState mutation
) {

    public static final int CURRENT_SCHEMA = 2;
    public static final int MIN_READABLE_SCHEMA = 1;
    public static final int MAX_HISTORY = 32;
    public static final int MAX_OPERATION_RECEIPTS = 16;

    public record Roll(double value, double quality) {
        public Roll {
            if (!Double.isFinite(value) || !Double.isFinite(quality)
                    || quality < 0.0D || quality > 1.0D) {
                throw new IllegalArgumentException("invalid item roll");
            }
        }
    }

    public record AscensionState(String stageId, int stageIndex) {
        public AscensionState {
            stageId = stageId == null || stageId.isBlank() ? "base" : ItemStatCatalog.normalizeId(stageId);
            if (stageIndex < 0 || stageIndex > 16) {
                throw new IllegalArgumentException("invalid ascension stage index");
            }
        }

        public static AscensionState base() {
            return new AscensionState("base", 0);
        }
    }

    public record Origin(String sourceTag, String sourceId, UUID crafterId,
                         String crafterNameSnapshot, String professionId,
                         String creationLocation, boolean masterwork, long createdAt) {
        public Origin {
            sourceTag = ItemStatCatalog.normalizeId(sourceTag);
            sourceId = sourceId == null || sourceId.isBlank() ? sourceTag : ItemStatCatalog.normalizeId(sourceId);
            crafterNameSnapshot = boundedOptional(crafterNameSnapshot, 64, "crafter name snapshot");
            professionId = professionId == null || professionId.isBlank()
                    ? "" : ItemStatCatalog.normalizeId(professionId);
            creationLocation = creationLocation == null ? "" : creationLocation.trim();
            if (creationLocation.length() > 160) {
                throw new IllegalArgumentException("item creation location is too long");
            }
            if (createdAt < 0L) throw new IllegalArgumentException("negative item creation timestamp");
        }

        public Origin(final String sourceTag, final String sourceId, final UUID crafterId,
                      final String creationLocation, final long createdAt) {
            this(sourceTag, sourceId, crafterId, "", "", creationLocation, false, createdAt);
        }
    }

    public record MutationState(int rerollCount, int rerollCostStep,
                                List<UUID> recentOperationReceipts) {
        public MutationState {
            if (rerollCount < 0 || rerollCount > 1_000_000
                    || rerollCostStep < 0 || rerollCostStep > 1_000_000
                    || rerollCostStep > rerollCount) {
                throw new IllegalArgumentException("invalid reroll counters");
            }
            recentOperationReceipts = receipts(recentOperationReceipts);
        }

        public static MutationState fresh() {
            return new MutationState(0, 0, List.of());
        }

        public boolean hasReceipt(final UUID operationId) {
            return operationId != null && recentOperationReceipts.contains(operationId);
        }

        public MutationState afterReroll(final UUID operationId, final boolean stabilitySeal) {
            return new MutationState(Math.addExact(rerollCount, 1),
                    stabilitySeal ? rerollCostStep : Math.addExact(rerollCostStep, 1),
                    appendReceipt(recentOperationReceipts, operationId));
        }

        public MutationState afterMutation(final UUID operationId) {
            return new MutationState(rerollCount, rerollCostStep,
                    appendReceipt(recentOperationReceipts, operationId));
        }
    }

    /** Backward-compatible source constructor; new instances receive an empty mutation ledger. */
    public ItemInstance(final UUID itemId, final int schemaVersion, final String templateId,
                        final int templateVersion, final int itemLevel,
                        final Map<String, Roll> rolls, final List<String> runes,
                        final AscensionState ascension, final Origin origin,
                        final Set<ItemState> states, final long mutationRevision,
                        final List<ItemHistoryEvent> history) {
        this(itemId, schemaVersion, templateId, templateVersion, itemLevel, rolls, runes,
                ascension, origin, states, mutationRevision, history, MutationState.fresh());
    }

    public ItemInstance {
        Objects.requireNonNull(itemId, "itemId");
        if (schemaVersion < MIN_READABLE_SCHEMA || schemaVersion > CURRENT_SCHEMA) {
            throw new IllegalArgumentException("unsupported item instance schema: " + schemaVersion);
        }
        templateId = ItemStatCatalog.normalizeId(templateId);
        if (templateVersion < 1 || itemLevel < 1 || itemLevel > 1000) {
            throw new IllegalArgumentException("invalid item template version or item level");
        }
        rolls = rolls(rolls);
        runes = runes(runes);
        ascension = ascension == null ? AscensionState.base() : ascension;
        Objects.requireNonNull(origin, "origin");
        states = states == null || states.isEmpty()
                ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(states));
        if (mutationRevision < 0L) throw new IllegalArgumentException("negative item mutation revision");
        history = history == null ? List.of() : List.copyOf(history);
        if (history.size() > MAX_HISTORY) throw new IllegalArgumentException("item history exceeds limit");
        mutation = mutation == null ? MutationState.fresh() : mutation;
    }

    public ItemInstance appendHistory(final ItemHistoryEvent event) {
        final ArrayList<ItemHistoryEvent> next = new ArrayList<>(history);
        next.add(Objects.requireNonNull(event, "event"));
        while (next.size() > MAX_HISTORY) next.remove(0);
        return new ItemInstance(itemId, schemaVersion, templateId, templateVersion, itemLevel,
                rolls, runes, ascension, origin, states, Math.addExact(mutationRevision, 1L), next, mutation);
    }

    public ItemInstance addRune(final String runeId, final long occurredAt) {
        final String normalized = ItemStatCatalog.normalizeId(runeId);
        if (runes.size() >= 2) throw new IllegalStateException("item rune capacity is full");
        if (runes.contains(normalized)) throw new IllegalArgumentException("item already contains this rune");
        final ArrayList<String> nextRunes = new ArrayList<>(runes);
        nextRunes.add(normalized);
        return new ItemInstance(itemId, schemaVersion, templateId, templateVersion, itemLevel,
                rolls, nextRunes, ascension, origin, states, mutationRevision, history, mutation)
                .appendHistory(new ItemHistoryEvent(ItemHistoryEvent.Type.RUNE_CHANGED,
                        occurredAt, normalized));
    }

    public ItemInstance withMutation(final Map<String, Roll> nextRolls,
                                     final List<String> nextRunes,
                                     final AscensionState nextAscension,
                                     final int nextItemLevel,
                                     final MutationState nextMutation,
                                     final ItemHistoryEvent event) {
        return new ItemInstance(itemId, CURRENT_SCHEMA, templateId, templateVersion,
                nextItemLevel, nextRolls, nextRunes, nextAscension, origin, states,
                mutationRevision, history, nextMutation).appendHistory(event);
    }

    private static Map<String, Roll> rolls(final Map<String, Roll> source) {
        if (source == null || source.isEmpty()) return Map.of();
        if (source.size() > 32) throw new IllegalArgumentException("item rolls exceed limit");
        final LinkedHashMap<String, Roll> result = new LinkedHashMap<>();
        source.forEach((raw, roll) -> {
            final String id = ItemStatCatalog.require(raw).id();
            if (roll == null) throw new IllegalArgumentException("missing item roll: " + raw);
            if (result.putIfAbsent(id, roll) != null) throw new IllegalArgumentException("duplicate item roll: " + id);
        });
        return Collections.unmodifiableMap(result);
    }

    private static List<String> runes(final List<String> source) {
        if (source == null || source.isEmpty()) return List.of();
        if (source.size() > 2) throw new IllegalArgumentException("item supports at most two runes");
        final ArrayList<String> result = new ArrayList<>();
        for (final String rune : source) {
            final String normalized = ItemStatCatalog.normalizeId(rune);
            if (result.contains(normalized)) throw new IllegalArgumentException("duplicate item rune");
            result.add(normalized);
        }
        return List.copyOf(result);
    }

    private static List<UUID> receipts(final List<UUID> source) {
        if (source == null || source.isEmpty()) return List.of();
        if (source.size() > MAX_OPERATION_RECEIPTS) {
            throw new IllegalArgumentException("item operation receipt history exceeds limit");
        }
        final ArrayList<UUID> result = new ArrayList<>();
        for (final UUID receipt : source) {
            Objects.requireNonNull(receipt, "operation receipt");
            if (result.contains(receipt)) throw new IllegalArgumentException("duplicate operation receipt");
            result.add(receipt);
        }
        return List.copyOf(result);
    }

    private static List<UUID> appendReceipt(final List<UUID> source, final UUID operationId) {
        Objects.requireNonNull(operationId, "operationId");
        if (source.contains(operationId)) return source;
        final ArrayList<UUID> next = new ArrayList<>(source);
        next.add(operationId);
        while (next.size() > MAX_OPERATION_RECEIPTS) next.remove(0);
        return List.copyOf(next);
    }

    private static String boundedOptional(final String raw, final int maxLength, final String label) {
        if (raw == null || raw.isBlank()) return "";
        final String value = raw.trim();
        if (value.length() > maxLength) throw new IllegalArgumentException(label + " is too long");
        return value;
    }
}
