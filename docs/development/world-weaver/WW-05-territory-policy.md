# WW-05 Territory native policy seam — internal developer evidence

Branch: `feature/world-weaver-ww05-trash-territory`. Exact immediate base: `feature/world-weaver-ww04-pve-faction-integrity`, `ac6e46eb96a6d4cd8b00e778afb775acdeb2ae4c`. Cumulative gameplay base: #155 `feature/faction-crime-whisperer-rework`, `2a78eb6071db063740ce1f8e6889b03d9741dbb0`. Earlier phases and their audit fixes are preserved; production remains disabled.

## Implemented native seam

`TerritoryProtectionPolicy` evaluates detached facts and produces an immutable decision and ordered trace. `TerritoryProtectionService` uses it for actual build/interact, player/unattributed combat, ownerless terrain, explosion and fire decisions. Its inspection methods use the same evaluator. The read-only, bind-once `TerritoryRuleProjectionSource` port does not mutate territory storage/index, faction ownership or claims.

Hard decision precedence is explicit: DOOM entry grace, combat-tag vulnerability, applicable canonical admin/builder bypass, and native PvP raid-participant override precede rule projections. Projection availability failures are denied after these hard decisions. Foreign actor permissions are not read: uncertain permission capture denies ambiguous protected actions instead of inventing a bypass decision. Native notices resolve the player UUID on its owner callback and retain no Player handle in the continuation.

The previous `denyPvp` DOOM-grace route had no caller. Native damage and harmful splash/cloud consumers now pass victim lifecycle state through `denyPlayerDamage`; an attacker in the DOOM zone forfeits its own grace. Foreign non-player damage sources and unavailable potion recipients fail closed before live reads. This is conservative owner-unavailable behaviour; populated cross-region tests remain required.

An explicit projection denial or unavailable authority cannot be bypassed by temporary terrain destruction/regeneration: the shared decision exposes that constraint to block-break, explosion and entity-change-block paths. Canonical regeneration eligibility remains native when no explicit/uncertain denial applies. Explosion blocks and entity-change-block accesses also guard owner availability before live state reads.

## Verification and exact limits

Java 21 full main/regression compilation passes (three inherited deprecation warnings). `TerritoryProtectionPolicyRegressionSuite` passes 419 assertions across the canonical matrix, hard lifecycle precedence for every overlay/availability combination, foreign-owner uncertainty, provider failure, regeneration denial and immutable trace. Existing TerritoryCapital, FactionPassive and FactionPassiveHardening suites pass. The new suite is part of the Gradle check dependency set.

This checkpoint supplies the shared native consumer seam. It does not yet supply TerritoryWeaverProvider, its journal-backed projection adapter, catalogs/Thread, canonical conditional transactions, or the Trash adapter. No domain becomes FULL_PROVIDER; coverage remains 51 blockers. Native live Paper/Folia damage/potion/regen and connected-client inspection evidence are required. Unit/source checks do not close those gates. No production enable, merge or WW-05 completion is claimed.

Additional local gates: coverage inventory 327 authorities / 55 domains / 51 blockers / zero audit errors; consistency zero FAIL/WARN; profile guard 685 classified / zero unknown, stale, invalid or transition entries. The world-UUID projection interface is explicitly classified as runtime, not a player-profile mutation.

## Canonical transaction checkpoint

The next WW-05 change builds on exact policy head `8be262e05a29f8f26d53416fe62fafa43b580825` (tree `8fc3ac3abf0d5148c9f4f1ed317a1605e38a84cf`). Its WorldWeaver verification run `34149084596`, resource-pack run `34149084643`, Trash hardening run `34149084595`, and docs inventory run `34149084614` all succeeded. Those runs validate that published policy checkpoint; they do not constitute connected Territory interaction evidence or evidence for the following untested-on-CI commit.

`TerritoryManager.adjustConditionally` now owns bounded native rename/type/owner/Y-band transactions. Expected canonical fingerprints reject external drift; the real operation UUID, request fingerprint and before/after fingerprints are persisted in the same atomic YAML replacement as the zone. Exact retries return the existing acknowledgement without replaying the mutation, including after later unrelated native changes. A capital collision is rejected explicitly, without silently modifying a second territory. Compensation uses a new operation and preserves earlier history.

All native territory mutations prepare a detached immutable zone/spawn/chunk-index snapshot, persist it, then publish it through one volatile reference. Failed or uncertain writes retain the last acknowledged live snapshot and fence further writes until disk reload assessment. Failure after actual replacement is recovered from the persisted native receipt; failure before replacement retains the prior disk state. Polygon constructor/accessor copies close the mutable-array alias that could otherwise alter geometry without a canonical transaction.

The native ledger caps at 4096 entries and refuses further mutations at capacity without eviction. Startup rejects malformed, coercible, noncanonical-UUID or contradictory receipts and oversized ledgers. The provider must execute this storage API on its I/O executor after durable PREPARED; this native seam does not itself grant developer authority or implement that provider. Legacy command scheduling is not claimed to be converted by this change.

Verification: full Java 21 main/regression compile; native real-YAML `TerritoryCanonicalMutationRegressionSuite` **83 assertions**, including before/after-replacement failures, immediate pre-write admission denial, eight concurrent writers, immutable publication, receipt restart/replay, compensation conflict, malformed ledger and capacity. Registered in Gradle `check`. Existing TerritoryProtectionPolicy (419), TerritoryCapital, FactionPassive, FactionPassiveHardening and FactionRework suites passed in this worktree before the final strict receipt checks. Profile authority guard: 686 classified, zero unknown/stale/invalid/transition findings. Coverage inventory: 332 authorities, 55 domains, 51 blockers; no FULL_PROVIDER claim. The current source set is 1244 main Java files.

Remaining phase work: journal-backed Territory provider/consumer binding, dynamic catalogs and Thread, Trash adapter, connected Paper/Folia protection and transaction evidence. The earlier “no canonical conditional transactions” limitation applies to the published first policy checkpoint, superseded only for the native seam by this change. Production remains disabled.
