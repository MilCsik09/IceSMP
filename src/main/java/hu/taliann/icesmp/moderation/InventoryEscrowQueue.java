package hu.taliann.icesmp.moderation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/** Thread-safe, count-preserving queue for reconnect-restorable invsee items. */
public final class InventoryEscrowQueue<T> {
    private final UnaryOperator<T> copier;
    private final Map<UUID, List<T>> queued = new HashMap<>();

    public InventoryEscrowQueue(final UnaryOperator<T> copier) {
        this.copier = Objects.requireNonNull(copier, "copier");
    }

    public synchronized void replace(final Map<UUID, ? extends List<T>> snapshot) {
        queued.clear();
        if (snapshot == null) {
            return;
        }
        snapshot.forEach((playerId, items) -> {
            if (playerId == null || items == null || items.isEmpty()) {
                throw new IllegalArgumentException("escrow snapshot contains an empty entry");
            }
            queued.put(playerId, copyList(items));
        });
    }

    public synchronized Map<UUID, List<T>> snapshot() {
        final Map<UUID, List<T>> copy = new LinkedHashMap<>();
        queued.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> copy.put(entry.getKey(), copyList(entry.getValue())));
        return Collections.unmodifiableMap(copy);
    }

    public synchronized void add(final UUID playerId, final T item) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(item, "item");
        final List<T> copy = new ArrayList<>(queued.getOrDefault(playerId, List.of()));
        copy.add(copier.apply(item));
        queued.put(playerId, List.copyOf(copy));
    }

    public synchronized T claimFirst(final UUID playerId) {
        return claimMatching(playerId, ignored -> true);
    }

    public synchronized T claimMatching(final UUID playerId, final Predicate<T> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        final List<T> current = queued.get(playerId);
        if (current == null || current.isEmpty()) {
            return null;
        }
        final List<T> copy = new ArrayList<>(current);
        for (int index = 0; index < copy.size(); index++) {
            final T candidate = copy.get(index);
            if (!predicate.test(candidate)) {
                continue;
            }
            copy.remove(index);
            if (copy.isEmpty()) {
                queued.remove(playerId);
            } else {
                queued.put(playerId, List.copyOf(copy));
            }
            return copier.apply(candidate);
        }
        return null;
    }

    public synchronized void restoreFirst(final UUID playerId, final T item) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(item, "item");
        final List<T> copy = new ArrayList<>();
        copy.add(copier.apply(item));
        copy.addAll(queued.getOrDefault(playerId, List.of()));
        queued.put(playerId, List.copyOf(copy));
    }

    public synchronized int itemCount(final UUID playerId) {
        return queued.getOrDefault(playerId, List.of()).size();
    }

    private List<T> copyList(final List<T> source) {
        final List<T> copy = new ArrayList<>(source.size());
        for (final T item : source) {
            copy.add(copier.apply(Objects.requireNonNull(item, "escrow item")));
        }
        return List.copyOf(copy);
    }
}
