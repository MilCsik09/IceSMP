package hu.taliann.icesmp.client;

import hu.taliann.icesmp.client.protocol.ClientHello;
import hu.taliann.icesmp.client.protocol.ClientProtocol;

import java.util.EnumSet;
import java.util.Set;

/**
 * Tiszta (Bukkit-mentes) kézfogás-negotiáció, dependency-free regresszióval fedve.
 *
 * <p>Szabályok: a közös protokoll a kliens- és szerver-tartomány metszetének maximuma;
 * üres metszet nem kick, hanem elutasítás (a kliens vanilla fallbackra esik). A
 * capability-eredmény a kliens által hirdetett ÉS a szerver által engedélyezett halmaz
 * metszete — ismeretlen vezetéknév csendben kimarad.</p>
 */
public final class ClientHandshake {

    /** A szerver pillanatnyi (élő configból képzett) kézfogás-politikája. */
    public record ServerPolicy(int protocolMin, int protocolMax, Set<ClientCapability> enabledCapabilities) {
        public ServerPolicy {
            enabledCapabilities = enabledCapabilities.isEmpty()
                    ? Set.of()
                    : Set.copyOf(enabledCapabilities);
        }
    }

    public sealed interface Result {
        record Accepted(int selectedProtocol, Set<ClientCapability> capabilities) implements Result {
        }

        record Rejected(String reasonCode) implements Result {
        }
    }

    private ClientHandshake() {
    }

    public static Result negotiate(final ClientHello hello, final ServerPolicy policy) {
        if (hello.clientVersion() == null || hello.clientVersion().isBlank()
                || hello.protocolMin() < 1 || hello.protocolMax() < hello.protocolMin()) {
            return new Result.Rejected(ClientProtocol.REJECT_INVALID_HELLO);
        }
        final int serverMin = Math.max(policy.protocolMin(), ClientProtocol.PROTOCOL_MIN);
        final int serverMax = Math.min(policy.protocolMax(), ClientProtocol.PROTOCOL_MAX);
        final int commonMin = Math.max(serverMin, hello.protocolMin());
        final int commonMax = Math.min(serverMax, hello.protocolMax());
        if (serverMin > serverMax || commonMin > commonMax) {
            return new Result.Rejected(ClientProtocol.REJECT_PROTOCOL_INCOMPATIBLE);
        }
        final Set<ClientCapability> granted = EnumSet.noneOf(ClientCapability.class);
        for (final String wireName : hello.capabilities()) {
            ClientCapability.fromWire(wireName)
                    .filter(policy.enabledCapabilities()::contains)
                    .ifPresent(granted::add);
        }
        return new Result.Accepted(commonMax, granted);
    }
}
