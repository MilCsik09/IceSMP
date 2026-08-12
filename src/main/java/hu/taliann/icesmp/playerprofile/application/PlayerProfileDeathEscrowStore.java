package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.LifecycleSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** CAS-backed death-to-respawn item escrow. */
public final class PlayerProfileDeathEscrowStore {
    private static final String BATCHES_KEY = "death-escrow.batches";
    private static final String SETTLED_KEY = "death-escrow.settled";
    private static final String LEGACY_ITEMS_KEY = "death-escrow.items";
    private static final String LEGACY_CREATED_AT_KEY = "death-escrow.created-at";
    private static final int MAX_ITEMS = 32;
    private static final int MAX_SETTLED_RECEIPTS = 128;

    public record Batch(String receiptId, List<String> encodedItems, long createdAt) {
        public Batch {
            receiptId = requireReceipt(receiptId);
            encodedItems = checkedItems(encodedItems);
            if (createdAt < 0L) throw new IllegalArgumentException("negative escrow timestamp");
        }
    }

    public record DepositResult(boolean applied, int pendingItems) { }

    public enum SettleStatus { SETTLED, ALREADY_SETTLED, NOT_FOUND }

    public CompletionStage<DepositResult> deposit(final UUID playerId,
                                                   final String receiptId,
                                                   final List<String> encodedItems,
                                                   final long now) {
        Objects.requireNonNull(playerId, "playerId");
        final Batch requested = new Batch(receiptId, encodedItems, now);
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.LIFECYCLE, LifecycleSection.class, current -> {
                    final Map<String, Batch> batches = readBatches(current);
                    final Map<String, Long> settled = readSettled(current);
                    if (settled.containsKey(requested.receiptId())) {
                        return PlayerProfileService.ConditionalMutation.unchanged(
                                new DepositResult(false, itemCount(batches)));
                    }
                    final Batch existing = batches.get(requested.receiptId());
                    if (existing != null) {
                        if (!existing.encodedItems().equals(requested.encodedItems())) {
                            throw new IllegalStateException(
                                    "death escrow receipt reused with different payload");
                        }
                        return PlayerProfileService.ConditionalMutation.unchanged(
                                new DepositResult(false, itemCount(batches)));
                    }
                    final LinkedHashMap<String, Batch> nextBatches = new LinkedHashMap<>(batches);
                    nextBatches.put(requested.receiptId(), requested);
                    final int total = itemCount(nextBatches);
                    if (total > MAX_ITEMS) throw new IllegalStateException("death escrow limit exceeded");
                    return PlayerProfileService.ConditionalMutation.changed(
                            withEscrow(current, nextBatches, settled),
                            new DepositResult(true, total));
                });
    }

    public CompletionStage<List<Batch>> pending(final UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return PlayerProfileAuthority.current().repository().loadSnapshot(playerId)
                .thenApply(snapshot -> readBatches(snapshot.lifecycle().value()).values().stream()
                        .sorted(Comparator.comparingLong(Batch::createdAt)
                                .thenComparing(Batch::receiptId))
                        .toList());
    }

    public CompletionStage<SettleStatus> settle(final UUID playerId,
                                                final String receiptId,
                                                final long now) {
        Objects.requireNonNull(playerId, "playerId");
        final String receipt = requireReceipt(receiptId);
        if (now < 0L) throw new IllegalArgumentException("negative settlement timestamp");
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.LIFECYCLE, LifecycleSection.class, current -> {
                    final Map<String, Batch> batches = readBatches(current);
                    final Map<String, Long> settled = readSettled(current);
                    if (settled.containsKey(receipt)) {
                        return PlayerProfileService.ConditionalMutation.unchanged(
                                SettleStatus.ALREADY_SETTLED);
                    }
                    if (!batches.containsKey(receipt)) {
                        return PlayerProfileService.ConditionalMutation.unchanged(
                                SettleStatus.NOT_FOUND);
                    }
                    final LinkedHashMap<String, Batch> nextBatches = new LinkedHashMap<>(batches);
                    nextBatches.remove(receipt);
                    final LinkedHashMap<String, Long> nextSettled = new LinkedHashMap<>(settled);
                    nextSettled.put(receipt, now);
                    while (nextSettled.size() > MAX_SETTLED_RECEIPTS) {
                        final String oldest = nextSettled.entrySet().stream()
                                .min(Map.Entry.<String, Long>comparingByValue()
                                        .thenComparing(Map.Entry::getKey))
                                .orElseThrow().getKey();
                        nextSettled.remove(oldest);
                    }
                    return PlayerProfileService.ConditionalMutation.changed(
                            withEscrow(current, nextBatches, nextSettled), SettleStatus.SETTLED);
                });
    }

    public static Map<String, Batch> readBatches(final LifecycleSection section) {
        final LinkedHashMap<String, Batch> batches = new LinkedHashMap<>();
        final Object raw = section.extensions().get(BATCHES_KEY);
        if (raw != null) {
            if (!(raw instanceof Map<?, ?> map)) {
                throw new IllegalStateException("Invalid death escrow batches: " + raw);
            }
            for (final Map.Entry<?, ?> entry : map.entrySet()) {
                final String receipt = requireReceipt(String.valueOf(entry.getKey()));
                if (!(entry.getValue() instanceof Map<?, ?> value)) {
                    throw new IllegalStateException("Invalid death escrow batch: " + entry.getValue());
                }
                batches.put(receipt, new Batch(receipt,
                        readItemList(value.get("items")), number(value.get("created-at"))));
            }
        }
        final List<String> legacy = readItemList(section.extensions().get(LEGACY_ITEMS_KEY));
        if (!legacy.isEmpty()) {
            final long createdAt = optionalNumber(
                    section.extensions().get(LEGACY_CREATED_AT_KEY), 0L);
            batches.putIfAbsent("legacy-" + createdAt, new Batch(
                    "legacy-" + createdAt, legacy, createdAt));
        }
        return Map.copyOf(batches);
    }

    private static Map<String, Long> readSettled(final LifecycleSection section) {
        final Object raw = section.extensions().get(SETTLED_KEY);
        if (raw == null) return Map.of();
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalStateException("Invalid death escrow settled receipts: " + raw);
        }
        final LinkedHashMap<String, Long> settled = new LinkedHashMap<>();
        for (final Map.Entry<?, ?> entry : map.entrySet()) {
            settled.put(requireReceipt(String.valueOf(entry.getKey())), number(entry.getValue()));
        }
        return Map.copyOf(settled);
    }

    private static LifecycleSection withEscrow(final LifecycleSection base,
                                               final Map<String, Batch> batches,
                                               final Map<String, Long> settled) {
        final Map<String, Object> extensions = new LinkedHashMap<>(base.extensions());
        extensions.remove(LEGACY_ITEMS_KEY);
        extensions.remove(LEGACY_CREATED_AT_KEY);
        if (batches.isEmpty()) {
            extensions.remove(BATCHES_KEY);
        } else {
            final LinkedHashMap<String, Object> encoded = new LinkedHashMap<>();
            batches.forEach((receipt, batch) -> encoded.put(receipt, Map.of(
                    "created-at", batch.createdAt(), "items", batch.encodedItems())));
            extensions.put(BATCHES_KEY, encoded);
        }
        if (settled.isEmpty()) extensions.remove(SETTLED_KEY);
        else extensions.put(SETTLED_KEY, settled);
        return new LifecycleSection(base.status(), base.profileCreatedAt(), base.updatedAt(),
                base.lastJoinAt(), base.lastQuitAt(), base.sessionGeneration(), extensions);
    }

    private static int itemCount(final Map<String, Batch> batches) {
        return batches.values().stream().mapToInt(batch -> batch.encodedItems().size()).sum();
    }

    private static List<String> checkedItems(final List<String> items) {
        Objects.requireNonNull(items, "encodedItems");
        if (items.isEmpty()) throw new IllegalArgumentException("empty escrow deposit");
        final ArrayList<String> copy = new ArrayList<>(items.size());
        for (final String item : items) {
            if (item == null || item.isBlank()) {
                throw new IllegalArgumentException("blank escrow item payload");
            }
            copy.add(item);
        }
        return List.copyOf(copy);
    }

    private static List<String> readItemList(final Object raw) {
        if (raw == null) return List.of();
        if (!(raw instanceof List<?> list)) {
            throw new IllegalStateException("Invalid death escrow item list: " + raw);
        }
        final ArrayList<String> items = new ArrayList<>(list.size());
        for (final Object value : list) {
            if (!(value instanceof String item) || item.isBlank()) {
                throw new IllegalStateException("Invalid death escrow item: " + value);
            }
            items.add(item);
        }
        return List.copyOf(items);
    }

    private static String requireReceipt(final String receipt) {
        if (receipt == null || receipt.isBlank() || receipt.length() > 128
                || !receipt.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException("invalid death escrow receipt");
        }
        return receipt;
    }

    private static long number(final Object value) {
        if (!(value instanceof Number number) || number.longValue() < 0L) {
            throw new IllegalStateException("Invalid death escrow timestamp: " + value);
        }
        return number.longValue();
    }

    private static long optionalNumber(final Object value, final long fallback) {
        return value == null ? fallback : number(value);
    }
}
