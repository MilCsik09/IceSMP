# WW-04 — PvE, Faction and reward integrity

Internal developer implementation evidence. Work in progress; this phase is not
accepted merely because its first provider checkpoint is registered.

## Exact base

- Repository: `MilCsik09/IceSMP`.
- Branch: `feature/world-weaver-ww04-pve-faction-integrity`.
- Immediate dependency/base: WW-03 #157, `feature/world-weaver-ww03-durable`.
- Exact base commit: `db8d8f906bb829dfa410793033545b2a7e40237f`.
- Exact base tree: `c3145ade252ac4d8468891f9d457894dad037e59`.
- Latest cumulative gameplay branch: #155 `feature/faction-crime-whisperer-rework`,
  `004c12abf6e896b1931a695f980d4841bbf785e9`, containing #152
  `a335b3b5acaea66772534e51527a1c9233a85d1a`.
- Open PRs rechecked before creating this phase on 2026-09-07: #155 remained the
  latest cumulative gameplay head; #153/#154/#156/#157 form the WorldWeaver stack.
  The unrelated messaging #145 is a sibling, not a newer cumulative gameplay head.

## Registered PvE catalog / inspection checkpoint

`IceSMPCore` now registers `PvEWeaverProvider` through the existing generic provider
factory list. No artifact, kernel, GUI or generic runtime code changes are required
to display this new subsystem's facet/catalogs/exports. The adapter receives actual
`MobAbilityRegistry`, `MobTemplateRegistry`, `MobScalingManager` and `MobAbilityRuntime`.
Each catalog request and typed reference validation consults the canonical registry's
current immutable publication; the provider has no copied list of ability/template IDs.

| Surface | Canonical read route | Published capability |
| --- | --- | --- |
| Ability catalog | `MobAbilityRegistry.all` | `icesmp:pve_ability_ref@1`, `pve.ability` |
| Template catalog | `MobTemplateRegistry.all` | `icesmp:pve_template_ref@1`, `pve.template` |
| Rank catalog | `MobRank.values` | `icesmp:pve_rank@1`, `pve.rank` |
| Archetype catalog | `MobArchetype.values` | `icesmp:pve_archetype@1`, `pve.archetype` |
| Native mob snapshot | Owner-checked canonical scaling/runtime getters | Rank, level, optional archetype/template; template resistance/weakness/source/behavior/stat profile and authored rank kit; active abilities, affixes and cast flags |
| Typed exports | Fresh immutable snapshot + current type validation | Rank, archetype, template; direct ability export for a single active ability |

The snapshot hook resolves only the selected UUID, checks region ownership before
reading validity or mob state, and returns detached typed values. AREA exposes only
registry inspection at this checkpoint. Discovery, inspect, catalog and export never
call the native snapshot function. Unknown/removed content cannot validate as a current
canonical reference. No raw PDC write, canonical registry mutation or extra mob
lifecycle is introduced. Multiple-ability selection still requires the later generic
catalog/Thread UX; this checkpoint does not silently choose an arbitrary boss ability.

The provider's registered `pve.catalog_inspection` manifest describes this available
surface. It does not close the independent WW-00 `pve` domain, which remains
`DEFERRED_BLOCKER`. No `INSPECT_ONLY_BY_DESIGN` exemption is claimed. There are still
51 current domain implementation blockers.

## Verification

`PvEWeaverCatalogRegressionSuite` constructs the actual adapter with immutable
canonical-port fixtures, freezes the registry, then publishes a 65th `MobAbilityDefinition`.
The existing catalog discovers it without rebuilding the provider or generic frontend.
The test verifies generic facet/catalog/export discovery, immutable pagination,
owner-capture isolation, type/capability separation, export into Thread Case, and
stale-content refusal after removal. It is a normal Gradle check dependency.

This is evidence for dynamic discovery/export through the real adapter. Full dynamic
acceptance A still needs compatible gameplay import and native registry/load evidence;
this checkpoint does not claim that acceptance is complete. Dynamic subsystem fixture
acceptance B continues to run in the existing generic provider suite.

Full Java 21 main/regression compilation passed against 49 real dependency jars:
zero errors, three inherited warnings. All 21 Weaver/provider and three DEV suites
passed (24). Four architecture checks, the 650-row PlayerProfile authority gate,
coverage inventory (291 authorities, 55 domains, 51 blockers, zero audit errors), and
repository consistency (zero FAIL/WARN) passed locally. CI for the new head is required.

The exact WW-03 base has remote evidence: run `34075806433`; Paper job
`101601537162` and Folia job `101601537327` succeeded with readiness and clean
shutdown. Verification `101601537332` compiled both source sets and passed projection
scope regressions; only the inherited `trashSpriteAssetAudit` failed. Resource-pack
`34075806464` and Trash hardening `34075806436` succeeded. These are preceding-head
clean-store probes, not populated PvE/Faction manipulation evidence.

## Combat projection port checkpoint

Based on catalog checkpoint `5d8db789c6c43a8dc820a0c73ddb74b533af41df`.
`MobAbilityRuntime` now consumes the immutable `CanonicalMobProfile` through
`MobRuntimeProjectionSource` and receives `EffectiveMobProjection`. The effective
record contains combat kit, rank, archetype, template and behavior; it has no level,
loot band, Bestiary identity, provenance or reward receipt mutation route. Canonical
fallback is currently the only bound source. Journal-backed provider publication is
still required before any gameplay projection can be claimed as implemented.

The existing runtime selects eligible canonical ability definitions from that
effective kit, preserves rank technique caps and reaction counterplay, and uses its
effective rank for combat boss classification. Reconciliation keeps cooldowns,
consumed health thresholds, paused state and combat context. A changed profile
invalidates an in-flight cast epoch and gives recovery time; delayed execution checks
again before firing. No parallel scheduler or mob lifecycle is introduced. This
port does not yet project health/attributes/resistances.

`MobRuntimeProjectionRegressionSuite` exercises the actual runtime selection policy
for all seven rank caps, eligibility, reaction behavior, immutable canonical inputs,
dynamic definition lookup and malformed/cap-exceeding input. It runs under Gradle
`check`. Full Java 21 compilation and 28 local Weaver/DEV/PvE suites pass. Four
architecture checks, the PlayerProfile guard and its self-test (653 findings, zero
unknown/stale/invalid/transition), and consistency pass. Inventory: 292 authorities,
55 domains, 51 blockers, zero audit errors. Two exact PlayerProfile allowlist entries
identify the immutable mob UUID projection port as runtime state, without weakening
the scanner or adding a player persistence exception.

Finding fixed: observed AREA recovery still had a provider-level parent-only
projection target check after WW-03 added acknowledged child reservations. Recovery
now verifies `WeaverOperationScope.matches`, including the acknowledged child
fingerprint. The scope suite recovers the original two child projections through
the actual recovery coordinator; foreign/unacknowledged targets remain rejected.

The preceding catalog head has remote evidence: build `34076501511`, Paper
`101603551368` and Folia `101603551547` succeeded. Verification `101603551497`
compiled both source sets and passed the catalog suite; its only failed task was
the inherited `trashSpriteAssetAudit`. Resource pack `34076501612` and Trash
hardening `34076501482` succeeded. These are clean-store boot/shutdown probes,
not populated projection evidence. This checkpoint requires its own remote CI.

## Remaining phase requirements

- Actual effective mob projection port and combat consumers, with canonical loot and
  Bestiary identity preserved; full PvE actions, imports, conditional Undo/recovery,
  AREA compatibility and native execution evidence.
- Faction membership/context projections through passive/targeting consumers, canonical
  expected-revision membership/crime/Whisperer APIs and their post-commit hooks.
- Influence propagation and neutral eligibility gates at every audited gameplay
  reward/progression producer; default durable admission remains closed until coverage.
- Source/channel failure, entity unload/load, player logout, plugin shutdown and
  populated Paper/Folia crash/recovery evidence. Client playtest remains required.

Physical artifact issuance and durable gameplay admission remain disabled. No merge,
release, universal capability, reward-leak closure or production-readiness verdict is
asserted by this checkpoint.
