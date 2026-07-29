# AFK scope decision

**Status:** final product decision — rewarded AFK zones are cancelled and out of scope.

## Retained behaviour

The existing simple global AFK system remains in IceSMP. This includes the global `/afk` flow, automatic inactivity detection, tab-list indication and its existing lifecycle and exploit guards.

## Explicitly cancelled scope

- Rewarding AFK zones will not be implemented.
- `AxAFKZone` and `AxAPI` are not part of the deployment plan.
- There is no AFK-zone configuration, data or player-state migration.
- Pull request #46 must remain unmerged.
- No zone, reward-zone, persistence or selection-service change from the rejected #46 branch may be reintroduced or extracted into another scope.

## Deployment consequence

This decision does not remove or replace the simple global AFK system. It only rejects the separate rewarded-zone product scope. No `AxAFKZone` or `AxAPI` plugin JAR is required for the retained global AFK behaviour.
