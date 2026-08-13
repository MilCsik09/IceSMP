package hu.taliann.icesmp.client;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Egy bejelentkezett Enhanced kliens élő session-állapota. Nem durable: quit/kick/
 * reconnect/disable eldobja; a kliens reconnect után új kézfogással új generation-t kap.
 *
 * <p>A sequence-számlálók atomikusak, mert a plugin-message callback és az admin-
 * diagnosztika eltérő szálakról olvashat (Folia: nincs közös főszál).</p>
 */
public final class ClientSession {

    private final UUID playerId;
    private final String clientVersion;
    private final int protocolVersion;
    private final long generation;
    private final Set<ClientCapability> capabilities;
    private final long connectedAtMillis;

    private final AtomicLong lastInboundSequence = new AtomicLong(0L);
    private final AtomicLong outboundSequence = new AtomicLong(0L);
    private final AtomicLong inboundAccepted = new AtomicLong(0L);
    private final AtomicLong inboundDropped = new AtomicLong(0L);
    private volatile long lastInboundAtMillis;

    public ClientSession(final UUID playerId, final String clientVersion, final int protocolVersion,
                         final long generation, final Set<ClientCapability> capabilities,
                         final long connectedAtMillis) {
        this.playerId = playerId;
        this.clientVersion = clientVersion;
        this.protocolVersion = protocolVersion;
        this.generation = generation;
        this.capabilities = capabilities.isEmpty()
                ? Set.of()
                : Set.copyOf(capabilities);
        this.connectedAtMillis = connectedAtMillis;
        this.lastInboundAtMillis = connectedAtMillis;
    }

    /**
     * Session-en belüli szigorú monotonitás: csak növekvő sequence fogadható el, így a
     * duplikált/visszajátszott csomag tartalmi validáció előtt kiesik.
     */
    public boolean acceptInbound(final long sequence, final long nowMillis) {
        while (true) {
            final long previous = lastInboundSequence.get();
            if (sequence <= previous) {
                inboundDropped.incrementAndGet();
                return false;
            }
            if (lastInboundSequence.compareAndSet(previous, sequence)) {
                lastInboundAtMillis = nowMillis;
                inboundAccepted.incrementAndGet();
                return true;
            }
        }
    }

    public long nextOutboundSequence() {
        return outboundSequence.incrementAndGet();
    }

    public UUID playerId() {
        return playerId;
    }

    public String clientVersion() {
        return clientVersion;
    }

    public int protocolVersion() {
        return protocolVersion;
    }

    public long generation() {
        return generation;
    }

    public Set<ClientCapability> capabilities() {
        return capabilities;
    }

    public long connectedAtMillis() {
        return connectedAtMillis;
    }

    public long lastInboundAtMillis() {
        return lastInboundAtMillis;
    }

    public long inboundAccepted() {
        return inboundAccepted.get();
    }

    public long inboundDropped() {
        return inboundDropped.get();
    }
}
