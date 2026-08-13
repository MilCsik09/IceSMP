package hu.taliann.icesmp.client;

import hu.taliann.icesmp.classspec.integration.ClassHudMetric;
import hu.taliann.icesmp.classspec.integration.ClassHudSlot;
import hu.taliann.icesmp.classspec.integration.ClassHudState;
import hu.taliann.icesmp.client.projection.ClientHudProjector;
import hu.taliann.icesmp.client.protocol.AbilityKitPayload;
import hu.taliann.icesmp.client.protocol.ActionResultPayload;
import hu.taliann.icesmp.client.protocol.CastSlotPayload;
import hu.taliann.icesmp.client.protocol.ClientHello;
import hu.taliann.icesmp.client.protocol.ClientMessageCodec;
import hu.taliann.icesmp.client.protocol.HudStatePayload;
import hu.taliann.icesmp.managers.HudManager;
import hu.taliann.icesmp.client.protocol.ClientProtocol;
import hu.taliann.icesmp.client.protocol.ClientProtocolException;
import hu.taliann.icesmp.client.protocol.MessageEnvelope;
import hu.taliann.icesmp.client.protocol.ProfileStatePayload;
import hu.taliann.icesmp.client.protocol.ProtocolReject;
import hu.taliann.icesmp.client.protocol.ServerHello;
import hu.taliann.icesmp.client.protocol.SpellActionPayload;
import hu.taliann.icesmp.client.protocol.SpellbookStatePayload;

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
        envelopesAreImmutable();
        payloadRoundtrips();
        hudStateRoundtrip();
        hudStateLimits();
        hudProjectorMapping();
        abilityKitAndActionPayloads();
        spellbookPayloads();
        profilePayload();
        malformedEnvelopeRejected();
        malformedPayloadRejected();
        handshakeNegotiation();
        capabilityNegotiation();
        sessionRegistryLifecycle();
        sequenceMonotonicity();
        rateLimiterWindows();
        System.out.println("Client protocol regression suite passed.");
    }

    private static void envelopesAreImmutable() {
        final byte[] original = {1, 2, 3};
        final MessageEnvelope envelope = new MessageEnvelope(
                1, ClientProtocol.MSG_PING, 1L, 1L, MessageEnvelope.NO_REQUEST, original);
        original[0] = 9;
        check(envelope.payload()[0] == 1, "constructor input may not mutate an envelope");
        final byte[] exposed = envelope.payload();
        exposed[1] = 9;
        check(envelope.payload()[1] == 2, "payload accessor may not expose mutable envelope state");
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

    private static HudStatePayload sampleHudState() {
        return new HudStatePayload(
                "Perinfernicitas", "RED", "vörös", "#FF5544", "#803322",
                "Halállovag", 42, "1 234,5", true, 87, 120, 72, "Rúnaerő",
                "Vérhold", List.of("Társ: Alva (92%)"),
                List.of(new HudStatePayload.Currency("ember", "Parázs", "12", true),
                        new HudStatePayload.Currency("shard", "Szilánk", "3", false)),
                new HudStatePayload.ClassHud("death_knight", "frost", "Fagy",
                        "Rúnakör", "Fagyjegyek", "harcban", "Fagyproc!", 2, 3,
                        List.of("rune_circle"),
                        List.of(new HudStatePayload.Metric("runic_power", "Rúnaerő", "87",
                                87.0D, 120.0D, "ok"),
                                new HudStatePayload.Metric("frost_marks", "Jegyek", "2/5",
                                        2.0D, 5.0D, "building")),
                        List.of(new HudStatePayload.Slot("rune_1", "rune", "ready", 100, "Vér"),
                                new HudStatePayload.Slot("rune_2", "rune", "recharge", 40, "Fagy"))));
    }

    private static void hudStateRoundtrip() throws Exception {
        final HudStatePayload state = sampleHudState();
        check(state.equals(HudStatePayload.decode(state.encode())), "HudStatePayload roundtrip");

        // Üres/kaszt nélküli állapot: a null classHud kanonikus üres ClassHud-dá normalizálódik.
        final HudStatePayload empty = new HudStatePayload("Menedék vendége", "", "", "", "",
                "nincs", 0, "0", false, 0, 0, 0, "", "", List.of(), List.of(), null);
        final HudStatePayload emptyBack = HudStatePayload.decode(empty.encode());
        check(empty.equals(emptyBack), "empty HudStatePayload roundtrip");
        check(emptyBack.classHud().classId().isEmpty(), "null classHud normalized to empty");
    }

    private static void hudStateLimits() {
        final List<HudStatePayload.Metric> tooMany = new java.util.ArrayList<>();
        for (int i = 0; i <= ClientProtocol.MAX_LIST_ELEMENTS; i++) {
            tooMany.add(new HudStatePayload.Metric("m" + i, "", "", 0.0D, 0.0D, ""));
        }
        final HudStatePayload oversized = new HudStatePayload("f", "", "", "", "", "c", 1, "0",
                true, 0, 0, 0, "", "", List.of(), List.of(),
                new HudStatePayload.ClassHud("id", "", "", "", "", "", "", 0, 0,
                        List.of(), tooMany, List.of()));
        try {
            oversized.encode();
            throw new AssertionError("oversized metric list accepted");
        } catch (final IllegalArgumentException expected) {
            // fail closed a kódolás előtt
        }
    }

    private static void hudProjectorMapping() {
        final ClassHudState classHud = new ClassHudState("death_knight", "frost", "Fagy",
                "Rúnakör", "Fagyjegyek", "harcban", "Fagyproc!", 2, 3,
                List.of("rune_circle"),
                List.of(ClassHudMetric.value("runic_power", "Rúnaerő", "87", 87.0D, 120.0D, "ok")),
                ClassHudSlot.charges("rune", "rune", "Rúna", 2, 3));
        final HudManager.HudSnapshot snapshot = new HudManager.HudSnapshot(
                "Perinfernicitas", "RED", "vörös", "#FF5544", "#803322",
                "Halállovag", 42, "1 234,5", true, 87, 120, 72, "Rúnaerő", "|||||",
                "Vérhold", List.of("Társ: Alva (92%)"),
                List.of(new HudManager.HudCurrency("ember", "Parázs", "12", true)),
                classHud);
        final HudStatePayload projected = ClientHudProjector.project(snapshot);
        check("Perinfernicitas".equals(projected.faction()) && "RED".equals(projected.factionId()),
                "projector faction mapping");
        check(projected.classLevel() == 42 && projected.resource() == 87
                && projected.resourceMax() == 120 && projected.resourcePercent() == 72,
                "projector numeric mapping");
        check("death_knight".equals(projected.classHud().classId())
                && projected.classHud().metrics().size() == 1
                && projected.classHud().metrics().get(0).maximum() == 120.0D
                && projected.classHud().slots().size() == 3,
                "projector class HUD mapping");
        check(projected.currencies().size() == 1
                && projected.currencies().get(0).primary(), "projector currency mapping");

        // A vanilla-only render-műtermék (resourceBar) nem utazik: a natív kliens a
        // resource/resourceMax párból rajzol.
        final HudStatePayload roundtripped;
        try {
            roundtripped = HudStatePayload.decode(projected.encode());
        } catch (final ClientProtocolException unexpected) {
            throw new AssertionError("projected HUD state must decode", unexpected);
        }
        check(projected.equals(roundtripped), "projected HUD state roundtrip");
    }

    private static void abilityKitAndActionPayloads() throws Exception {
        final CastSlotPayload castSlot = new CastSlotPayload(3);
        check(castSlot.equals(CastSlotPayload.decode(castSlot.encode())), "CastSlotPayload roundtrip");

        final ActionResultPayload result = new ActionResultPayload(
                ClientProtocol.MSG_CAST_SLOT, ClientProtocol.RESULT_NOT_READY, "ON_COOLDOWN");
        check(result.equals(ActionResultPayload.decode(result.encode())), "ActionResultPayload roundtrip");

        final AbilityKitPayload kit = new AbilityKitPayload(List.of(
                new AbilityKitPayload.Entry("frost_strike", "Fagycsapás", "30 Rúnaerő",
                        8000L, 4000L, true),
                new AbilityKitPayload.Entry("obliterate", "Eltörlés", "", 12000L, 0L, false)));
        check(kit.equals(AbilityKitPayload.decode(kit.encode())), "AbilityKitPayload roundtrip");

        // A change-signature a fogyó maradékra invariáns, a cooldown-állapotváltásra nem:
        // így a vezetékre nem megy másodpercenkénti timer-frissítés, de az indulás/lejárás igen.
        final AbilityKitPayload sameButTicking = new AbilityKitPayload(List.of(
                new AbilityKitPayload.Entry("frost_strike", "Fagycsapás", "30 Rúnaerő",
                        8000L, 900L, true),
                new AbilityKitPayload.Entry("obliterate", "Eltörlés", "", 12000L, 0L, false)));
        check(java.util.Arrays.equals(kit.changeSignature().encode(),
                sameButTicking.changeSignature().encode()), "ticking cooldown does not change signature");
        final AbilityKitPayload expired = new AbilityKitPayload(List.of(
                new AbilityKitPayload.Entry("frost_strike", "Fagycsapás", "30 Rúnaerő",
                        8000L, 0L, true),
                new AbilityKitPayload.Entry("obliterate", "Eltörlés", "", 12000L, 0L, false)));
        check(!java.util.Arrays.equals(kit.changeSignature().encode(),
                expired.changeSignature().encode()), "cooldown expiry changes signature");

        final List<AbilityKitPayload.Entry> tooMany = new java.util.ArrayList<>();
        for (int i = 0; i <= ClientProtocol.MAX_LIST_ELEMENTS; i++) {
            tooMany.add(new AbilityKitPayload.Entry("s" + i, "", "", 0L, 0L, false));
        }
        try {
            new AbilityKitPayload(tooMany).encode();
            throw new AssertionError("oversized kit accepted");
        } catch (final IllegalArgumentException expected) {
            // fail closed a kódolás előtt
        }
    }

    private static void spellbookPayloads() throws Exception {
        final SpellActionPayload action = new SpellActionPayload("frost_strike");
        check(action.equals(SpellActionPayload.decode(action.encode())), "SpellActionPayload roundtrip");

        final SpellbookStatePayload spellbook = new SpellbookStatePayload(List.of(
                new SpellbookStatePayload.Entry("frost_strike", "Fagycsapás",
                        List.of("Cél: célzott lény (hatótáv 12)", "Sebzés: 6"),
                        5, true, true, true, true, 2, "30 Rúnaerő", 8),
                new SpellbookStatePayload.Entry("army_of_the_dead", "Holtak serege",
                        List.of("Cél: önmagad"), 40, false, false, false, false, 0, "", 300)));
        check(spellbook.equals(SpellbookStatePayload.decode(spellbook.encode())),
                "SpellbookStatePayload roundtrip");

        final List<SpellbookStatePayload.Entry> tooMany = new java.util.ArrayList<>();
        for (int i = 0; i <= ClientProtocol.MAX_LIST_ELEMENTS; i++) {
            tooMany.add(new SpellbookStatePayload.Entry("s" + i, "", List.of(),
                    0, false, false, false, false, 0, "", 0));
        }
        try {
            new SpellbookStatePayload(tooMany).encode();
            throw new AssertionError("oversized spellbook accepted");
        } catch (final IllegalArgumentException expected) {
            // fail closed a kódolás előtt
        }
    }

    private static void profilePayload() throws Exception {
        final ProfileStatePayload profile = new ProfileStatePayload(
                "Alva", "Perinfernicitas (A Vörös Láng)", "RED",
                "Halállovag", 42, 60, "Fagy",
                "Bányász", 17, "Kovács", 9, "Fegyverkovács",
                false, 3, 1,
                List.of(new ProfileStatePayload.Balance("Parázs", "1 234,5"),
                        new ProfileStatePayload.Balance("Csepp", "0")),
                new ProfileStatePayload.Stats(12, 4, 3456, 789, 21, 5),
                14, 40);
        check(profile.equals(ProfileStatePayload.decode(profile.encode())),
                "ProfileStatePayload roundtrip");

        // Kaszt/frakció nélküli friss játékos: üres stringek utaznak, a kliens "nincs"-et renderel.
        final ProfileStatePayload fresh = new ProfileStatePayload(
                "Uj_Jatekos", "Menedék vendége", "", "", 0, 60, "", "", 0, "", 0, "",
                false, 0, 0, List.of(), new ProfileStatePayload.Stats(0, 0, 0, 0, 0, 0), 0, 40);
        check(fresh.equals(ProfileStatePayload.decode(fresh.encode())),
                "fresh ProfileStatePayload roundtrip");
    }

    private static void malformedEnvelopeRejected() {
        final byte[] valid = ClientMessageCodec.encodeEnvelope(
                new MessageEnvelope(1, ClientProtocol.MSG_PING, 1L, 1L, MessageEnvelope.NO_REQUEST, new byte[8]));

        expectProtocolFailure("truncated envelope", Arrays.copyOf(valid, valid.length - 3));
        expectProtocolFailure("empty packet", new byte[0]);
        expectProtocolFailure("oversized packet", new byte[ClientProtocol.MAX_PACKET_BYTES + 1]);

        try {
            ClientMessageCodec.encodeEnvelope(new MessageEnvelope(1, ClientProtocol.MSG_PING, 1L, 1L,
                    MessageEnvelope.NO_REQUEST, new byte[ClientProtocol.MAX_PAYLOAD_BYTES + 1]));
            throw new AssertionError("oversized outbound payload accepted");
        } catch (final IllegalArgumentException expected) {
            // fail closed before constructing an oversized wire packet
        }

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
            ClientHello.decode(new byte[] {0, 0, 0, 2, (byte) 0xC3, 0x28});
            throw new AssertionError("malformed UTF-8 accepted");
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
        check(!session.acceptInbound(2, session.generation(), 7L, 60L),
                "non-negotiated protocol must be rejected before sequence publication");
        check(!session.acceptInbound(session.protocolVersion(), session.generation() + 1L, 7L, 60L),
                "stale generation must be rejected before sequence publication");
        check(session.acceptInbound(session.protocolVersion(), session.generation(), 7L, 60L),
                "valid envelope must remain acceptable after rejected metadata");
        check(session.inboundAccepted() == 4L && session.inboundDropped() == 4L,
                "metadata rejects must update counters without consuming sequence");

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
