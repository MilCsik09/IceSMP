package hu.taliann.icesmp.factions;

import java.util.UUID;

/** Effective passive membership; canonical citizenship, history and economy never read this port. */
@FunctionalInterface
public interface FactionMembershipProjectionSource {
    FactionMembership resolve(UUID playerId, FactionMembership canonical);
    static FactionMembershipProjectionSource canonical() { return (id, membership) -> membership; }
}
