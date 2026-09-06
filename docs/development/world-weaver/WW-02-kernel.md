# WW-02 — Kernel vertical slice (in progress)

Internal developer evidence. This document does not announce a gameplay feature.

Repository: `MilCsik09/IceSMP`.
Cumulative gameplay base: `feature/trash-production-hardening` at
`a335b3b5acaea66772534e51527a1c9233a85d1a` (PR #152).
Immediate stacked base: `feature/world-weaver-ww01-artifact` at
`b1476ac774f5b11c12aca0bedfe5b135f3b35f41` (PR #154).
Branch: `feature/world-weaver-ww02-kernel`.

WW-02 is not complete. The code-defined artifact remains disabled. The independent
WW-00 matrix still has 51 implementation blockers across 55 audited domains.
The new registry declarations do not close those domain requirements.

## Implemented foundation

| Design requirement | Source evidence | Verification boundary |
|---|---|---|
| Seven immutable SubjectRef kinds | `dev/weaver/subject/*Ref`, `SubjectKeyCodec` | Explicit versioned canonical data; all kinds round-trip; malformed/unknown fields rejected |
| YAML-safe typed values | `WeaverValue`, `CanonicalValueBytes`, `WeaverTypeRegistry`, `ScalarTypeCodec` | Bounds, immutable copies, integer normalization, unknown schema, capability mismatch, all normative core type registrations |
| Five AREA shapes | `RadiusArea`, `CuboidArea`, `CylinderArea`, `PolygonPrismArea`, `TerritoryArea` | Geometry and chunk caps only; live collection/fanout remains open |
| Dynamic catalogs | `RegistryValueCatalog` | A canonical `MobAbilityDefinition` fixture published after catalog construction appears without core/GUI edits; actual production PvE registry wiring remains WW-04 |
| Provider descriptors | `WorldWeaverProvider`, `WorldWeaverProviderRegistry`, `ProviderContribution` | Duplicate/cross-reference/type/parameter/recovery/integrity policy rejection |
| New subsystem discovery | `WeaverFacetView` | Added fixture provider produces facet, inspect data and action through the shared view model; in-game GUI evidence remains open |
| Coverage registry | `WorldWeaverCoverageRegistry`, `ProviderCoverage` | Multiple domains per adapter; omitted/duplicate/deferred domains and empty full-provider evidence fail closed |
| Primary authority/session/arming | `WeaverAuthorityToken`, `WorldWeaverSessionManager`, `WeaverArming` | Fixed primary UUID, stale views, one-attempt/expiring grants, foreign sessions, logout/disable clearing |
| Thread Case / recent selections | `WeaverThreadCase`, `WeaverSelectionStore` | Session-only immutable values; 12 Thread / 32 recent bounds |
| Failure isolation and rate limit | `ProviderCircuitBreaker`, `WeaverRateLimiter` | Three unexpected failures in 60 seconds quarantine only the failing provider; expected domain rejection is separate; errors expose bounded codes |
| Owner routing | `SubjectRoute`, `FoliaWeaverOwnerRouter`, `OwnerTaskAdmission` | Entity/player scheduler, loaded region scheduler, global scheduler, bounded serialized IO; queued timeout/retirement/disable prevents late start; started timeout explicitly ambiguous |
| Live snapshot implementation | `SubjectSnapshotFactory`, `WeaverItemSlots` | Compiles against the real Folia 1.21.11 API; managed item identity read uses `ItemIdentityService.inspect`; full item fingerprint and revision rechecked; live server evidence remains open |
| Durable-operation contracts | `PreparedAction`, `ExecutionStage`, `WeaverOperationRecord`, `RecoveryAssessment`, `WeaverReceipt` | Immutable bounded contract only; durable storage, reconciliation and execution are not implemented by these records |

Paths in the table are relative to `src/main/java/hu/taliann/icesmp/` (classes without
a package prefix are inside `dev/weaver`, `api`, `subject`, `execution`, or `persistence`).

`WeaverSnapshotContributor` is an optional provider adapter hook executed on the
subject owner before pure discovery. This preserves the immutable snapshot
invariant while allowing future subsystem facts without editing the snapshot
factory or generic frontend. It is not a provider-specific GUI or alternate state
authority. Captured facts must carry the owning provider prefix and valid types.

## Verification

Seven focused suites are registered as normal Gradle `check` dependencies:

- `WeaverTypeCompatibilityRegressionSuite`
- `WeaverDynamicCatalogRegressionSuite`
- `WeaverAreaRegressionSuite`
- `WeaverContractRegressionSuite`
- `WorldWeaverAuthorityRegressionSuite`
- `WorldWeaverCoverageRegressionSuite`
- `WeaverFoliaOwnershipRegressionSuite`

Local preflight uses the full main and regression source sets, Java 21, and all 49
real compile dependency jars exported by repository CI. No Bukkit/API stubs or
source exclusions are used. Exact command output is retained under `build/`.
This preflight does not replace the remote Gradle build or live server tests.

The immediate WW-01 base was separately verified on Paper and Folia 1.21.11 by
workflow run `34057813343` (jobs `101552810534` and `101552810471`, both successful).
Those probes exercise the DEV artifact lifecycle; they do not prove the new
WW-02 snapshot/router code or connected-client interaction.

## Open implementation and evidence gates

The actual kernel/GUI/controller, initial Minecraft actions, execution coordinator
and reward influence basics are still WW-02 work. Journal, reconciliation,
persistent projection and conditional undo remain WW-03. The operation records
must never be mistaken for a working durable engine.

Interactive authority tokens are not recovery tokens. Offline recovery and
compensation need a constrained operation-bound recovery authority in WW-03;
they must not counterfeit an interactive primary session or bypass physical
artifact checks for new mutations.

Connected-client stale GUI, forgery, logout/death, cross-region handoff, entity or
chunk unload and disable behavior require live evidence after kernel integration.
Pure admission tests prove only scheduling admission/timeout decisions.

The inherited malformed Trash sprite source PNGs remain a strict full-build
asset gate. This phase neither modifies them nor weakens that gate. No production,
merge-ready, reward-integrity or universal-coverage verdict is asserted.
