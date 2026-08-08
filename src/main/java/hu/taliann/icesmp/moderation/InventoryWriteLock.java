package hu.taliann.icesmp.moderation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Dependency-free single-writer lock for live inventory inspection.
 *
 * <p>There may be any number of readers, but at most one writer for a target.
 * A viewer can own at most one target lease at a time.</p>
 */
public final class InventoryWriteLock {

    private final Map<UUID, UUID> writerByTarget = new HashMap<>();
    private final Map<UUID, UUID> targetByWriter = new HashMap<>();

    public synchronized boolean acquire(final UUID viewerId, final UUID targetId) {
        if (viewerId == null || targetId == null) {
            return false;
        }
        releaseViewer(viewerId);
        final UUID current = writerByTarget.get(targetId);
        if (current != null) {
            return current.equals(viewerId);
        }
        writerByTarget.put(targetId, viewerId);
        targetByWriter.put(viewerId, targetId);
        return true;
    }

    public synchronized void releaseViewer(final UUID viewerId) {
        if (viewerId == null) {
            return;
        }
        final UUID targetId = targetByWriter.remove(viewerId);
        if (targetId != null) {
            writerByTarget.remove(targetId, viewerId);
        }
    }

    /** Releases only the exact lease, so a stale close cannot unlock a newer target. */
    public synchronized void release(final UUID viewerId, final UUID targetId) {
        if (viewerId == null || targetId == null
                || !targetId.equals(targetByWriter.get(viewerId))
                || !viewerId.equals(writerByTarget.get(targetId))) {
            return;
        }
        targetByWriter.remove(viewerId, targetId);
        writerByTarget.remove(targetId, viewerId);
    }

    public synchronized void releaseTarget(final UUID targetId) {
        if (targetId == null) {
            return;
        }
        final UUID viewerId = writerByTarget.remove(targetId);
        if (viewerId != null) {
            targetByWriter.remove(viewerId, targetId);
        }
    }

    public synchronized boolean holds(final UUID viewerId, final UUID targetId) {
        return viewerId != null && targetId != null
                && viewerId.equals(writerByTarget.get(targetId))
                && targetId.equals(targetByWriter.get(viewerId));
    }

    public synchronized Optional<UUID> writer(final UUID targetId) {
        return Optional.ofNullable(writerByTarget.get(targetId));
    }

    public synchronized int activeWriters() {
        return writerByTarget.size();
    }

    public synchronized void clear() {
        writerByTarget.clear();
        targetByWriter.clear();
    }
}
