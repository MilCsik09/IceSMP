# WW-02 — Kernel vertical slice (in progress)

Internal developer evidence. This document does not announce a gameplay feature.

Repository: `MilCsik09/IceSMP`.
Cumulative gameplay base: `feature/trash-production-hardening` at
`a335b3b5acaea66772534e51527a1c9233a85d1a` (PR #152).
Immediate stacked base: `feature/world-weaver-ww01-artifact` at
`8863279711c3e8d174033cbcf6ed86721ed27050` (PR #154).
The initial phase base was `b1476ac774f5b11c12aca0bedfe5b135f3b35f41`.
Current cumulative gameplay base is #155, `feature/faction-crime-whisperer-rework`
at `004c12abf6e896b1931a695f980d4841bbf785e9`; the original #152 base remains
recorded above.
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

Ten focused suites are registered as normal Gradle `check` dependencies:

- `WeaverTypeCompatibilityRegressionSuite`
- `WeaverDynamicCatalogRegressionSuite`
- `WeaverAreaRegressionSuite`
- `WeaverContractRegressionSuite`
- `WorldWeaverAuthorityRegressionSuite`
- `WorldWeaverCoverageRegressionSuite`
- `WeaverFoliaOwnershipRegressionSuite`
- `WeaverExecutionRegressionSuite`
- `WeaverGUIRegressionSuite`
- `WeaverRewardIntegrityRegressionSuite`

Local preflight uses the full main and regression source sets, Java 21, and all 49
real compile dependency jars exported by repository CI. No Bukkit/API stubs or
source exclusions are used. Exact command output is retained under `build/`.
This preflight does not replace the remote Gradle build or live server tests.

The immediate WW-01 base was separately verified on Paper and Folia 1.21.11 by
workflow run `34057813343` (jobs `101552810534` and `101552810471`, both successful).
Those probes exercise the DEV artifact lifecycle; they do not prove the new
WW-02 snapshot/router code or connected-client interaction.

## Open implementation and evidence gates

The kernel/GUI, recipient-only initial Minecraft action, non-durable execution
coordinator and neutral reward-policy foundation are now implemented. Full
Minecraft mutation, durable influence and real reward-flow wiring remain open.
Journal, reconciliation, persistent projection and conditional undo remain
WW-03; the operation records are not a working durable engine.

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

## First checkpoint remote evidence and cumulative refresh

Checkpoint `e01a424cf35c6523381c0522685b80ce0e3e49e1` was published in draft
PR #156. WorldWeaver workflow `34060525809`, verification job `101560119003`,
compiled both full Java source sets and passed all seven new Weaver suites.
The sole failed Gradle task was the inherited `trashSpriteAssetAudit` source-PNG
gate. Resource-pack validation and Trash production hardening workflows passed.
Paper `101560119327` and Folia `101560119156` DEV lifecycle probes also passed;
those remain artifact/store probes, not WW-02 kernel/client evidence.

The phase then inherits refreshed WW-01 through a non-rewriting merge, retaining
all of #155 and the re-audited WW-00 domain routes. AGENTS/CLAUDE conflicts were
only source-count metadata. No Java conflict resolution or gameplay override was
needed. Exact refreshed-head CI is a separate evidence requirement.

## Kernel integration checkpoint

`WorldWeaverRuntime` is constructed by IceSMPCore after its canonical services and
receives provider factories from the IceSMP composition root. It contains no
named subsystem adapter. Registration is validated after content/store load and
before listeners admit interaction. A future subsystem adds its adapter and
composition registration without changing the artifact/kernel/GUI/runtime.

The existing DevItemManager supplies a one-time generic behavior binding. The
WorldWeaver behavior delegates interactions and propagates unavailability,
recovery and shutdown into the kernel; no second artifact identity authority is
created. Issuance remains disabled by the existing code-defined flag.

| Integrated path | Evidence / remaining boundary |
|---|---|
| Subject and Facet GUI | Owner-routed fresh snapshot; descriptor-only facet/action/catalog/export entries; all clicks cancel and require exact session/view revision |
| Parameter input | Bounded primitive parser, compatible Thread/Subject selection, current canonical catalog resolve; native Minecraft dialog avoids chat/command logging |
| Confirmation | Fresh snapshot before preview and before execution; stale fingerprint rejects; new draft UUID after edits; queued execution tokens also bind the view revision |
| Quick Apply | Type/capability + provider validation discovers importers; one complete importer opens confirmation, multiple importers open a generic choice; no auto-execution |
| Thread Case / Recent | Bounded immutable session state, F and shift-F interaction routes |
| Receipts | Last 64 one-shot receipts in the session; this does not replace the later durable receipt store |
| Non-durable execution | Continuation chain with actor revalidation before each owner stage; bounded owner timeouts; one operation in flight; manifest-checked receipt |
| Provider isolation | Synchronous manifest failures and asynchronous stage errors feed the same bounded circuit breaker; expected domain/authority refusals stay separate |
| Initial Minecraft provider | Native snapshots, canonical material/gamemode catalogs, location export and recipient-only particle/sound feedback; full vanilla manipulation remains DEFERRED_BLOCKER |
| Reward foundation | Neutral `RewardEligibilityPolicy`/context/source/channel contract, deny-wins composition, fail-closed unavailable evidence and immutable developer influence/quarantine state |
| Quarantine timing | Active player influence never expires by its deadline alone; ending it begins at least another five minutes; LIVE_GM does not erase SANDBOX origin |

The initial adapter's registered surface declaration is not proof that the
Minecraft domain is universally covered. The independent WW-00 matrix retains
its full manipulation blocker. All MUTATING+, persistent or influence-bearing
actions require the durable engine and are rejected by the current execution
admission gate. No gameplay producer calls the new reward policy yet; the
156 channel/source test denials prove policy behavior, not end-to-end reward
quarantine. Current admitted feedback affects only the primary recipient's client
and is declared SAFE / ONE_SHOT / IntegrityImpact.NONE.

The static architecture suite checks frontend independence, no provider-owned
Weaver GUI, public-doc secrecy, forbidden scheduler/future/reflection patterns
and live-handle fields in subject/persistence code. Negative fixtures prove the
checks reject concrete regressions. These checks supplement live evidence; they
are not a proof of all Folia ownership paths.

The native InventoryHolder retains only its three immutable identity fields plus
a transient owner-thread inventory handle, as required by Bukkit's holder API.
That native handle never enters session snapshots, journal payloads, Threads,
Imprints or execution stages. The kernel retains immutable view data separately.

Human/server gates still include native dialog cancellation, stale views after
recovery/relog, forged/duplicate items, GUI interaction during cross-region
handoff, plugin disable during an admitted operation, client model state and
particles/sound. The code remains disabled until the later integrity, durable
recovery and complete capability gates have evidence.

## Integration preflight and findings

Full Java 21 main + regression compilation passed using the 49 real CI dependency
jars, with no excluded source and three inherited deprecation warnings. All ten
Weaver suites and three DEV artifact/reward suites passed. Consistency reports
0 FAIL / 0 WARN; the four static architecture tests pass. Resource-pack validation
passes for 3,537 client files (1,947 JSON/MCMeta, 1,588 PNG and 80 equipment assets).

Catalog page and resolve responses now validate type, provider/facet provenance,
requested offset/limit and continuation bounds inside the provider circuit
breaker. The regression injects invalid metadata, pagination and value provenance
and verifies quarantine, while an expected missing registry value does not count
as a provider failure. View clipping preserves Unicode code points and bounds
titles as well as rows.

Refreshed parent `05b6d5f3694f049bc50b84b6a45de47a2f7d631b` has successful Paper
`101561997502` and Folia `101561997509` DEV probes in run `34061219404`. Full
Java compilation and its seven Weaver suites passed in verification job
`101561997402`; two tasks failed: the inherited malformed Trash source-PNG gate
and a cumulative config-audit mismatch. The latter first validates the exact
major-event extension, removes it from observed drift, then incorrectly requires
it in the expanded allowlist inherited from #155. Its correction is a separate
commit; no gameplay settings or acceptance thresholds need changing.

No exact remote CI or connected-client evidence is asserted for this new kernel
integration until its published commit is tested. Its full-state fingerprint is
conservative: movement, combat and world-clock drift can invalidate confirmation.
Action-specific state assessment must be supplied with actual manipulating
providers; the initial recipient-only feedback does not prove that later surface.
