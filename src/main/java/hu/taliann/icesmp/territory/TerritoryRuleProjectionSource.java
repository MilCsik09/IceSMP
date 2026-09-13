package hu.taliann.icesmp.territory;

import java.util.UUID;

/** Read-only effective rule port. Canonical territory storage and ownership never consume it. */
@FunctionalInterface
public interface TerritoryRuleProjectionSource {
    TerritoryProtectionPolicy.Overlay resolve(UUID worldId, String territoryId, TerritoryProtectionPolicy.Rule rule);
}
