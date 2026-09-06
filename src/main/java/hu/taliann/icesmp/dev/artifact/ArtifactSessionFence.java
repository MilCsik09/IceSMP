package hu.taliann.icesmp.dev.artifact;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** A retired continuation may never acquire a later login's authority. */
public final class ArtifactSessionFence {
    private final Map<UUID, Long> sessions = new HashMap<>();
    private long sequence;

    public synchronized long ensure(final UUID owner) {
        final Long current = sessions.get(owner);
        if (current != null) return current;
        if (sessions.size() >= 32) throw new IllegalStateException("Artifact owner session capacity reached");
        sequence = Math.incrementExact(sequence);
        sessions.put(owner, sequence);
        return sequence;
    }
    public synchronized Long current(final UUID owner) { return sessions.get(owner); }
    public synchronized boolean matches(final UUID owner, final long session) {
        return Long.valueOf(session).equals(sessions.get(owner));
    }
    public synchronized void close(final UUID owner) { sessions.remove(owner); }
    public synchronized void clear() { sessions.clear(); }
}
