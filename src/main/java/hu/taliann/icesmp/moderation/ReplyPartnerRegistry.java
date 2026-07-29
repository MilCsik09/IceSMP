package hu.taliann.icesmp.moderation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Session-generation fenced, atomically bidirectional /reply partner registry. */
public final class ReplyPartnerRegistry {
    public record Session(UUID playerId, long generation) {
        public Session {
            if (playerId == null || generation <= 0L) {
                throw new IllegalArgumentException("reply session requires a player and positive generation");
            }
        }
    }

    private record Link(UUID partnerId, long ownerGeneration, long partnerGeneration) { }

    private final Map<UUID, Long> sessions = new HashMap<>();
    private final Map<UUID, Link> links = new HashMap<>();
    private long nextGeneration;

    public synchronized Session openSession(final UUID playerId) {
        if (playerId == null) {
            throw new IllegalArgumentException("playerId cannot be null");
        }
        unlink(playerId);
        final long generation = nextGeneration = Math.incrementExact(nextGeneration);
        sessions.put(playerId, generation);
        return new Session(playerId, generation);
    }

    public synchronized Optional<Session> capture(final UUID playerId) {
        final Long generation = sessions.get(playerId);
        return generation == null ? Optional.empty() : Optional.of(new Session(playerId, generation));
    }

    public synchronized boolean linkIfCurrent(final Session first, final Session second) {
        if (first == null || second == null || first.playerId().equals(second.playerId())
                || !isCurrent(first) || !isCurrent(second)) {
            return false;
        }
        unlink(first.playerId());
        unlink(second.playerId());
        links.put(first.playerId(), new Link(second.playerId(), first.generation(), second.generation()));
        links.put(second.playerId(), new Link(first.playerId(), second.generation(), first.generation()));
        return true;
    }

    public synchronized Optional<UUID> partner(final UUID playerId) {
        final Link link = links.get(playerId);
        final Long ownerGeneration = sessions.get(playerId);
        final Long partnerGeneration = link == null ? null : sessions.get(link.partnerId());
        if (link == null || ownerGeneration == null || partnerGeneration == null
                || ownerGeneration != link.ownerGeneration()
                || partnerGeneration != link.partnerGeneration()) {
            unlink(playerId);
            return Optional.empty();
        }
        return Optional.of(link.partnerId());
    }

    public synchronized void closeSession(final UUID playerId) {
        sessions.remove(playerId);
        unlink(playerId);
    }

    public synchronized boolean isCurrent(final Session session) {
        return session != null && sessions.getOrDefault(session.playerId(), -1L) == session.generation();
    }

    private void unlink(final UUID playerId) {
        final Link removed = links.remove(playerId);
        if (removed != null) {
            final Link reverse = links.get(removed.partnerId());
            if (reverse != null && reverse.partnerId().equals(playerId)) {
                links.remove(removed.partnerId());
            }
        }
    }
}
