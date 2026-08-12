package hu.taliann.icesmp.classspec.application;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Thread-safe UUID-only bidirectional index for transient caster-to-target links. */
public final class TargetRegistry {
    private final Map<UUID, Set<UUID>> targetsByOwner = new HashMap<>();
    private final Map<UUID, Set<UUID>> ownersByTarget = new HashMap<>();

    public synchronized void link(final UUID ownerId, final UUID targetId) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(targetId, "targetId");
        targetsByOwner.computeIfAbsent(ownerId, ignored -> new HashSet<>()).add(targetId);
        ownersByTarget.computeIfAbsent(targetId, ignored -> new HashSet<>()).add(ownerId);
    }

    public synchronized void unlink(final UUID ownerId, final UUID targetId) {
        if (ownerId == null || targetId == null) return;
        remove(targetsByOwner, ownerId, targetId);
        remove(ownersByTarget, targetId, ownerId);
    }

    public synchronized Set<UUID> unlinkOwner(final UUID ownerId) {
        if (ownerId == null) return Set.of();
        final Set<UUID> targets = targetsByOwner.remove(ownerId);
        if (targets == null) return Set.of();
        final Set<UUID> snapshot = Set.copyOf(targets);
        for (final UUID targetId : snapshot) remove(ownersByTarget, targetId, ownerId);
        return snapshot;
    }

    public synchronized Set<UUID> unlinkTarget(final UUID targetId) {
        if (targetId == null) return Set.of();
        final Set<UUID> owners = ownersByTarget.remove(targetId);
        if (owners == null) return Set.of();
        final Set<UUID> snapshot = Set.copyOf(owners);
        for (final UUID ownerId : snapshot) remove(targetsByOwner, ownerId, targetId);
        return snapshot;
    }

    public synchronized Set<UUID> ownersOf(final UUID targetId) {
        final Set<UUID> owners = ownersByTarget.get(targetId);
        return owners == null ? Set.of() : Set.copyOf(owners);
    }

    public synchronized Set<UUID> targetsOf(final UUID ownerId) {
        final Set<UUID> targets = targetsByOwner.get(ownerId);
        return targets == null ? Set.of() : Set.copyOf(targets);
    }

    public synchronized void clear() {
        targetsByOwner.clear();
        ownersByTarget.clear();
    }

    private static void remove(final Map<UUID, Set<UUID>> index,
                               final UUID key, final UUID value) {
        final Set<UUID> values = index.get(key);
        if (values == null) return;
        values.remove(value);
        if (values.isEmpty()) index.remove(key);
    }
}
