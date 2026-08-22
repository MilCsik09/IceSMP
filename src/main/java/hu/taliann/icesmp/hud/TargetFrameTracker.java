package hu.taliann.icesmp.hud;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded immutable target snapshots shared between entity-owner producers and viewer HUD reads. */
public final class TargetFrameTracker {
    public static final int MAX_VIEWERS = 2048;
    public static final double MIN_RANGE = 3.0D;
    public static final double MAX_RANGE = 64.0D;

    public record Snapshot(UUID targetId, UUID worldId, String templateId, String targetName,
                           TargetHudState.Kind kind, TargetHudState.Rank rank, int level,
                           String mobStatus, double health, double maximumHealth,
                           String className, String resourceName, int resource,
                           int resourceMaximum, long observedAt) {
        public Snapshot {
            Objects.requireNonNull(targetId, "targetId");
            Objects.requireNonNull(worldId, "worldId");
            templateId = Objects.requireNonNullElse(templateId, "").trim();
            targetName = Objects.requireNonNullElse(targetName, "Célpont");
            kind = kind == null ? TargetHudState.Kind.HOSTILE : kind;
            rank = rank == null ? TargetHudState.Rank.NORMAL : rank;
            level = Math.max(0, level);
            mobStatus = Objects.requireNonNullElse(mobStatus, "");
            maximumHealth = positive(maximumHealth, 20.0D);
            health = Math.max(0.0D, Math.min(maximumHealth, finite(health, 0.0D)));
            className = Objects.requireNonNullElse(className, "");
            resourceName = Objects.requireNonNullElse(resourceName, "");
            resourceMaximum = Math.max(0, resourceMaximum);
            resource = Math.max(0, Math.min(resourceMaximum, resource));
            if (observedAt < 0L) throw new IllegalArgumentException("negative target observation time");
        }

        public boolean player() { return kind == TargetHudState.Kind.PLAYER; }
    }

    private record Selection(long generation, UUID targetId) { }

    private final AtomicLong generations = new AtomicLong();
    private final Map<UUID, Selection> selections = new ConcurrentHashMap<>();
    private final Map<UUID, Snapshot> snapshots = new ConcurrentHashMap<>();

    public long begin(final UUID viewerId, final UUID targetId) {
        Objects.requireNonNull(viewerId, "viewerId");
        Objects.requireNonNull(targetId, "targetId");
        final Selection current = selections.get(viewerId);
        if (current != null && targetId.equals(current.targetId())) return current.generation();
        if (current == null && selections.size() >= MAX_VIEWERS) return -1L;
        final long generation = generations.incrementAndGet();
        selections.put(viewerId, new Selection(generation, targetId));
        snapshots.remove(viewerId);
        return generation;
    }

    public long beginSample(final UUID viewerId) {
        Objects.requireNonNull(viewerId, "viewerId");
        if (!selections.containsKey(viewerId) && selections.size() >= MAX_VIEWERS) return -1L;
        final long generation = generations.incrementAndGet();
        selections.put(viewerId, new Selection(generation, null));
        snapshots.remove(viewerId);
        return generation;
    }

    public boolean publish(final UUID viewerId, final long generation, final Snapshot snapshot) {
        if (viewerId == null || generation < 0L || snapshot == null) return false;
        final Selection selected = selections.get(viewerId);
        if (selected == null || selected.generation() != generation
                || (selected.targetId() != null && !selected.targetId().equals(snapshot.targetId()))) {
            return false;
        }
        if (selected.targetId() == null) {
            if (!selections.replace(viewerId, selected,
                    new Selection(generation, snapshot.targetId()))) return false;
        }
        snapshots.put(viewerId, snapshot);
        return true;
    }

    public Snapshot current(final UUID viewerId, final UUID viewerWorldId,
                            final long now, final long maximumAgeMillis) {
        if (viewerId == null || viewerWorldId == null || now < 0L || maximumAgeMillis < 1L) return null;
        final Snapshot snapshot = snapshots.get(viewerId);
        if (snapshot == null) return null;
        if (!viewerWorldId.equals(snapshot.worldId())
                || now - snapshot.observedAt() > maximumAgeMillis
                || now < snapshot.observedAt()) {
            clear(viewerId);
            return null;
        }
        return snapshot;
    }

    public void clear(final UUID viewerId) {
        if (viewerId == null) return;
        generations.incrementAndGet();
        selections.remove(viewerId);
        snapshots.remove(viewerId);
    }

    public void clearIfSelected(final UUID viewerId, final long generation) {
        if (viewerId == null || generation < 0L) return;
        final Selection selected = selections.get(viewerId);
        if (selected != null && selected.generation() == generation) clear(viewerId);
    }

    public void invalidateTarget(final UUID targetId) {
        if (targetId == null) return;
        for (final Map.Entry<UUID, Selection> entry : selections.entrySet()) {
            if (targetId.equals(entry.getValue().targetId())) clear(entry.getKey());
        }
    }

    public int size() { return snapshots.size(); }

    public static double boundedRange(final double configured) {
        if (!Double.isFinite(configured)) return 24.0D;
        return Math.max(MIN_RANGE, Math.min(MAX_RANGE, configured));
    }

    public static boolean withinRange(final double distanceSquared, final double range) {
        final double bounded = boundedRange(range);
        return Double.isFinite(distanceSquared) && distanceSquared >= 0.0D
                && distanceSquared <= bounded * bounded;
    }

    private static double positive(final double value, final double fallback) {
        final double safe = finite(value, fallback);
        return safe > 0.0D ? safe : fallback;
    }

    private static double finite(final double value, final double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }
}
