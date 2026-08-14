package hu.taliann.icesmp.client;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * UUID → élő kliens-session. Nem durable, tisztán in-memory; a lifecycle-takarítást a
 * bridge végzi a központi quit/kick/disable úton, hogy a map ne szivárogjon.
 *
 * <p>A generation-forrás plugin-életciklusonként monoton: reconnectkor az új session
 * garantáltan nagyobb generation-t kap, így a régi session nevében késve érkező csomag
 * generation-eltérésen esik ki.</p>
 */
public final class ClientSessionRegistry {

    private final ConcurrentHashMap<UUID, ClientSession> sessions = new ConcurrentHashMap<>();
    private final AtomicLong generationSource = new AtomicLong(0L);

    public long nextGeneration() {
        return generationSource.incrementAndGet();
    }

    /** Reconnectnél az előző session-t lecseréli — egy játékosnak legfeljebb egy session-je van. */
    public void register(final ClientSession session) {
        sessions.put(session.playerId(), session);
    }

    public Optional<ClientSession> find(final UUID playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }

    public void invalidate(final UUID playerId) {
        sessions.remove(playerId);
    }

    public void clear() {
        sessions.clear();
    }

    public int size() {
        return sessions.size();
    }

    public List<ClientSession> snapshot() {
        return List.copyOf(sessions.values());
    }
}
