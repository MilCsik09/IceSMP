package hu.taliann.icesmp.client;

import hu.taliann.icesmp.client.protocol.ClientHello;
import hu.taliann.icesmp.client.protocol.ClientMessageCodec;
import hu.taliann.icesmp.client.protocol.ClientProtocol;
import hu.taliann.icesmp.client.protocol.ClientProtocolException;
import hu.taliann.icesmp.client.protocol.MessageEnvelope;
import hu.taliann.icesmp.client.protocol.ProtocolReject;
import hu.taliann.icesmp.client.protocol.ServerHello;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Dependency-free regressziók a Client Bridge tiszta rétegeire: envelope/payload codec
 * (roundtrip + fail-closed hibautak), kézfogás-negotiáció, session-registry lifecycle,
 * sequence-monotonitás és rate limiter. Bukkit-osztályt nem tölt be.
 */
public final class ClientProtocolRegressionSuite {

    private ClientProtocolRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        envelopeRoundtrip();
        payloadRoundtrips();
        malformedEnvelopeRejected();
        malformedPayloadRejected();
        handshakeNegotiation();
        capabilityNegotiation();
        sessionRegistryLifecycle();
        sequenceMonotonicity();
        rateLimiterWindows();
        System.out.println("Client protocol regression suite passed.");
    }

    private static void envelopeRoundtrip() throws Exception {
        final UUID requestId = new UUID(0x1234L, 0x5678L);
        final byte[] payload = {1, 2, 3, 4, 5};
        final MessageEnvelope original = new MessageEnvelope(1, ClientProtocol.MSG_PING, 42L, 7L, requestId, payload);
        final MessageEnvelope decoded = ClientMessageCodec.decodeEnvelope(ClientMessageCodec.encodeEnvelope(original));
        check(decoded.protocolVersion() == 1, "protocol version roundtrip");
        check(decoded.messageType() == ClientProtocol.MSG_PING, "message type roundtrip");
        check(decoded.sessionGeneration() == 42L, "generation roundtrip");
        check(decoded.sequence() == 7L, "sequence roundtrip");
        check(requestId.equals(decoded.requestId()), "requestId roundtrip");
        check(Arrays.equals(payload, decoded.payload()), "payload roundtrip");

        final MessageEnvelope empty = new MessageEnvelope(1, ClientProtocol.MSG_PONG, 1L, 1L, null, null);
        final MessageEnvelope emptyDecoded = ClientMessageCodec.decodeEnvelope(ClientMessageCodec.encodeEnvelope(empty));
        check(emptyDecoded.payload().length == 0, "empty payload roundtrip");
        check(MessageEnvelope.NO_REQUEST.equals(emptyDecoded.requestId()), "null requestId normalized");
    }

    private static void payloadRoundtrips() throws Exception {
        final ClientHello hello = new ClientHello("1.0.0-teszt", 1, 3,
                List.of("NATIVE_HUD", "KEYBIND_CAST", "ISMERETLEN_KEPESSEG"));
        final ClientHello helloBack = ClientHello.decode(hello.encode());
        check(hello.equals(helloBack), "ClientHello roundtrip");

        final ServerHello serverHello = new ServerHello("2.6.0", 1, 17, List.of("NATIVE_HUD"));
        check(serverHello.equals(ServerHello.decode(serverHello.encode())), "ServerHello roundtrip");

        final ProtocolReject reject = new ProtocolReject(ClientProtocol.REJECT_PROTOCOL_INCOMPATIBLE, 1, 1);
        check(reject.equals(ProtocolReject.decode(reject.encode())), "ProtocolReject roundtrip");
    }

    private static void malformedEnvelopeRejected() {
        final byte[] valid = ClientMessageCodec.encodeEnvelope(
                new MessageEnvelope(1, ClientProtocol.MSG_PING, 1L, 1L, MessageEnvelope.NO_REQUEST, new byte[8]));

        expectProtocolFailure("truncated envelope", Arrays.copyOf(valid, valid.length - 3));
        expectProtocolFailure("empty packet", new byte[0]);
        expectProtocolFailure("oversized packet", new byte[ClientProtocol.MAX_PACKET_BYTES + 1]);

        final byte[] badMagic = valid.clone();
        badMagic[0] = (byte) 0xFF;
        expectProtocolFailure("bad magic", badMagic);

        // A payload-hosszmező meghamisítása: a deklarált hossz nagyobb, mint a tényleges tartalom.
        final byte[] lyingLength = valid.clone();
        lyingLength[valid.length - 8 - 4] = 0x7F;
        expectProtocolFailure("lying payload length", lyingLength);

        final byte[] trailing = Arrays.copyOf(valid, valid.length + 2);
        expectProtocolFailure("trailing bytes", trailing);
    }

    private static void malformedPayloadRejected() {
        try {
            ClientHello.decode(new byte[] {0, 0, 0, 5, 'a'});
            throw new AssertionError("truncated ClientHello accepted");
        } catch (final ClientProtocolException expected) {
            // fail closed
        }
        try {
            // Hamis string-hossz a protokoll-limit fölött: allokáció előtt kell elbuknia.
            ClientHello.decode(new byte[] {0x7F, 0x7F, 0x7F, 0x7F});
            throw new AssertionError("oversized string length accepted");
        } catch (final ClientProtocolException expected) {
            // fail closed
        }
        try {
            final ClientHello valid = new ClientHello("1.0.0", 1, 1, List.of());
            final byte[] withTrailing = Arrays.copyOf(valid.encode(), valid.encode().length + 1);
            ClientHello.decode(withTrailing);
            throw new AssertionError("trailing payload bytes accepted");
        } catch (final ClientProtocolException expected) {
            // fail closed
        }
    }

    private static void handshakeNegotiation() {
        final ClientHandshake.ServerPolicy policy = new ClientHandshake.ServerPolicy(1, 1, Set.of());

        final ClientHandshake.Result overlap = ClientHandshake.negotiate(
                new ClientHello("1.0.0", 1, 3, List.of()), policy);
        check(overlap instanceof ClientHandshake.Result.Accepted accepted && accepted.selectedProtocol() == 1,
                "overlapping range selects common maximum");

        final ClientHandshake.Result disjoint = ClientHandshake.negotiate(
                new ClientHello("9.9.9", 5, 8, List.of()), policy);
        check(disjoint instanceof ClientHandshake.Result.Rejected rejected
                        && ClientProtocol.REJECT_PROTOCOL_INCOMPATIBLE.equals(rejected.reasonCode()),
                "disjoint range rejected as incompatible");

        final ClientHandshake.Result inverted = ClientHandshake.negotiate(
                new ClientHello("1.0.0", 3, 1, List.of()), policy);
        check(inverted instanceof ClientHandshake.Result.Rejected rejected
                        && ClientProtocol.REJECT_INVALID_HELLO.equals(rejected.reasonCode()),
                "inverted client range rejected as invalid");

        final ClientHandshake.Result blankVersion = ClientHandshake.negotiate(
                new ClientHello("  ", 1, 1, List.of()), policy);
        check(blankVersion instanceof ClientHandshake.Result.Rejected rejected
                        && ClientProtocol.REJECT_INVALID_HELLO.equals(rejected.reasonCode()),
                "blank client version rejected as invalid");

        // A config tágabb ablakot hirdethet, mint amit a kód beszél — a kód-tartomány győz.
        final ClientHandshake.Result configWiderThanCode = ClientHandshake.negotiate(
                new ClientHello("1.0.0", 1, 99, List.of()),
                new ClientHandshake.ServerPolicy(1, 99, Set.of()));
        check(configWiderThanCode instanceof ClientHandshake.Result.Accepted accepted
                        && accepted.selectedProtocol() == ClientProtocol.PROTOCOL_MAX,
                "config window clamped to code-supported range");
    }

    private static void capabilityNegotiation() {
        final Set<ClientCapability> serverEnabled = EnumSet.of(ClientCapability.NATIVE_HUD, ClientCapability.KEYBIND_CAST);
        final ClientHandshake.Result result = ClientHandshake.negotiate(
                new ClientHello("1.0.0", 1, 1,
                        List.of("NATIVE_HUD", "RELIC_RENDER_V1", "TELJESEN_ISMERETLEN")),
                new ClientHandshake.ServerPolicy(1, 1, serverEnabled));
        check(result instanceof ClientHandshake.Result.Accepted, "capability handshake accepted");
        final Set<ClientCapability> granted = ((ClientHandshake.Result.Accepted) result).capabilities();
        check(granted.equals(Set.of(ClientCapability.NATIVE_HUD)),
                "granted = advertised AND server-enabled; unknown wire name ignored");
        check(ClientCapability.fromWire("TELJESEN_ISMERETLEN").isEmpty(), "unknown capability maps to empty");
        check(ClientCapability.NATIVE_HUD.configKey().equals("native-hud"), "config key derivation");
        check(ClientCapability.RELIC_RENDER_V1.configKey().equals("relic-render-v1"), "config key derivation with digit");
    }

    private static void sessionRegistryLifecycle() {
        final ClientSessionRegistry registry = new ClientSessionRegistry();
        final UUID player = UUID.randomUUID();

        final ClientSession first = new ClientSession(player, "1.0.0", 1,
                registry.nextGeneration(), Set.of(), 1000L);
        registry.register(first);
        check(registry.find(player).orElseThrow() == first, "registered session is findable");
        check(registry.size() == 1, "registry size after register");

        // Reconnect: az új session nagyobb generation-t kap és lecseréli a régit.
        final ClientSession second = new ClientSession(player, "1.0.1", 1,
                registry.nextGeneration(), Set.of(ClientCapability.NATIVE_HUD), 2000L);
        registry.register(second);
        check(registry.size() == 1, "reconnect replaces, not duplicates");
        check(registry.find(player).orElseThrow().generation() > first.generation(),
                "reconnect generation strictly increases");

        registry.invalidate(player);
        check(registry.find(player).isEmpty(), "invalidate removes the session");

        registry.register(second);
        registry.clear();
        check(registry.size() == 0, "clear empties the registry");
    }

    private static void sequenceMonotonicity() {
        final ClientSession session = new ClientSession(UUID.randomUUID(), "1.0.0", 1, 1L, Set.of(), 0L);
        check(session.acceptInbound(1L, 10L), "first sequence accepted");
        check(session.acceptInbound(5L, 20L), "gap in sequence accepted (only monotonicity enforced)");
        check(!session.acceptInbound(5L, 30L), "duplicate sequence rejected");
        check(!session.acceptInbound(3L, 40L), "stale sequence rejected");
        check(session.acceptInbound(6L, 50L), "next sequence accepted after rejects");
        check(session.inboundAccepted() == 3L, "accepted counter");
        check(session.inboundDropped() == 2L, "dropped counter");
        check(session.lastInboundAtMillis() == 50L, "last inbound timestamp follows accepted packet");

        check(session.nextOutboundSequence() == 1L, "outbound sequence starts at 1");
        check(session.nextOutboundSequence() == 2L, "outbound sequence increments");
    }

    private static void rateLimiterWindows() {
        final ClientRateLimiter limiter = new ClientRateLimiter();
        final UUID player = UUID.randomUUID();

        for (int i = 0; i < 5; i++) {
            check(limiter.tryAcquire(player, ClientRateLimiter.Category.CONTROL, 5, 1000L, 100L),
                    "packet " + i + " within limit accepted");
        }
        check(!limiter.tryAcquire(player, ClientRateLimiter.Category.CONTROL, 5, 1000L, 999L),
                "limit exceeded inside window rejected");
        check(limiter.tryAcquire(player, ClientRateLimiter.Category.CONTROL, 5, 1000L, 1100L),
                "new window admits again");

        // A kategóriák függetlenek: a CONTROL-terhelés nem fogyasztja a RESYNC-keretet.
        check(limiter.tryAcquire(player, ClientRateLimiter.Category.RESYNC, 1, 2000L, 999L),
                "resync category independent of control");
        check(!limiter.tryAcquire(player, ClientRateLimiter.Category.RESYNC, 1, 2000L, 1500L),
                "resync cooldown enforced");
        check(limiter.tryAcquire(player, ClientRateLimiter.Category.RESYNC, 1, 2000L, 3100L),
                "resync admitted after cooldown");

        check(!limiter.tryAcquire(player, ClientRateLimiter.Category.CONTROL, 0, 1000L, 5000L),
                "zero limit always rejects");

        limiter.clearPlayer(player);
        check(limiter.tryAcquire(player, ClientRateLimiter.Category.RESYNC, 1, 2000L, 3200L),
                "clearPlayer resets windows");
    }

    private static void expectProtocolFailure(final String label, final byte[] wire) {
        try {
            ClientMessageCodec.decodeEnvelope(wire);
            throw new AssertionError(label + ": malformed input accepted");
        } catch (final ClientProtocolException expected) {
            // fail closed
        }
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
