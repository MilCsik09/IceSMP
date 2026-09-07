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

## Journal-backed combat projection checkpoint

Based on `4e5889294755e3acee6c792964c6a92639a60901`. This supersedes the
canonical-fallback-only limitation above. Composition supplies each provider with
generic type, read-only projection and owner scheduler ports. `PvEWeaverProvider`
binds `PvEMobProjectionSource` into the existing `MobAbilityRuntime`. The artifact,
kernel and GUI still contain no PvE branches.

| Action / field | Effective consumer | Canonical consumers unchanged |
| --- | --- | --- |
| `pve.add_ability` / `pve.ability_add` | Native eligible kit selection, then the existing cast/cooldown lifecycle | Loot, Bestiary, quest identity, reward level, provenance |
| `pve.remove_ability` / `pve.ability_remove` | Latest add/remove operation per ability; suppression recomputes the kit | Same canonical identities |
| `pve.override_rank` / `pve.rank_override` | Rank kit, technique budget, eligibility and combat boss classification | Canonical scaling rank/level and rewards |
| `pve.override_archetype` / `pve.archetype_override` | Ability eligibility; explicit override wins over template archetype default | Canonical archetype and reward identity |
| `pve.apply_template_projection` / `pve.template_override` | Template ability kit and behavior; rank remains independently selected | Canonical template PDC, loot, Bestiary and provenance |
| Conditional `pve.sever_projection` | Removes the exact projection; recomputes effective state | Original receipt/audit retained; entity taint remains monotonic |

All five projection actions support SESSION and PERSISTENT lifetime, typed catalog
input, and SANDBOX/LIVE_GM modes through the existing generic authority/arming gates.
Ability import checks effective rank/archetype eligibility. The most recent explicit
ability additions have selection priority, so an added ability is not silently lost
behind the existing normal-rank technique cap. Rank/archetype/template/ability import
descriptors are discovered generically. Sever is the receipt's conditional Undo route;
the later direct projection picker/clear UX is not claimed here.

The owner stage checks a fresh canonical combat revision and projection fingerprint.
It does not mutate a native entity. The only projection mutation is the durable
APPLIED publication, with influence and receipt in the same generation. A second
projection fingerprint check runs inside the serialized journal writer: intervening
projection drift/expiry cannot slip through the owner-to-storage continuation window.
The fingerprint includes ordered projection identities, typed values and lifetime.
Canonical definition revisions encode map/set members deterministically.

Conditional Undo checks the observed fingerprint and original receipt revision,
then severs only its own projection. External canonical/projection drift conflicts.
Recovery observes PREPARED as unpublished and aborts it; APPLIED must match the
observed effective fingerprint before audit completion. No stage is replayed.
Unloaded entities remain pending until load. Existing `EntitiesLoadEvent` handling
reattaches through the canonical combat runtime; no chunk is force-loaded.

The generic publication dispatcher wakes at most 16 targets per maintenance pass,
retains immutable references only, detects startup/add/remove/session cleanup changes,
coalesces concurrent publications, and retries failed consumers without starving other
providers. Its acknowledgement cache is capped at 2560 targets. Read failures use the
provider circuit breaker; unavailable/quarantined combat input supplies no custom
abilities, preserving canonical identity. Recovery/cleanup can still run while a
provider is quarantined. The journal retains reward quarantine evidence.

Verification: full Java 21 main/regression compilation; 30 local Weaver/DEV/PvE
suites PASS; four architecture checks, PlayerProfile guard/self-test (656 findings,
zero unknown/stale/invalid/transition), coverage audit (296 authorities, 55 domains,
51 blockers, zero errors), consistency (zero FAIL/WARN), and whitespace checks pass.
Both new suites are Gradle `check` dependencies. The actual provider regression adds
new registry content after freeze, exports it from a boss snapshot, imports its typed
Thread into a compatible mob, commits through the real journal and selects it through
the actual native rank-cap policy. It also covers template/member precedence, rejected
ineligible import, canonical identity, conditional Undo, immutable audit, session
cleanup, stale snapshots, the storage publication race and eight before/after-write
crash boundaries with unloaded entity recovery. Dispatcher tests cover 320 targets,
publication races and a failing provider beside a healthy one.

The preceding port head has exact remote evidence: build `34078372942`; Paper
`101608819282` and Folia `101608819266` succeeded. Verification `101608819164`
compiled both source sets and passed the combat port suite; its only failed task
was the inherited `trashSpriteAssetAudit`. Resource pack `34078372931`, Trash
`34078372925`, and docs inventory `34078372935` succeeded. This new projection
checkpoint still needs its own CI and populated Paper/Folia execution evidence.

## Reward ingress checkpoint

The neutral policy is now installed in Core with an exclusive lifecycle binding.
Shared kill eligibility carries immutable victim/causal/player/world/spatial context
and rechecks at owner continuations. Vanilla/custom/dungeon output, Warden XP, party
XP beneficiaries and specific class/pet/Bestiary/quest/community/currency kill ingress
paths are wired. See [the call-site matrix](WW-04-reward-ingress.md) for exact routes
and remaining canonical settlement/propagation work. This does not close the WW-00
reward finding or enable physical issuance/durable gameplay admission.

## Remaining phase requirements

- PvE force-ability/context/stat consumers, direct projection picker/clear controls,
  AREA compatibility and populated native execution evidence. Multi-ability catalog
  selection into Thread still needs the generic WW-07 UX.
- Faction membership/context projections through passive/targeting consumers, canonical
  expected-revision membership/crime/Whisperer APIs and their post-commit hooks.
- Influence propagation and neutral eligibility gates at every audited gameplay
  reward/progression producer; default durable admission remains closed until coverage.
- Source/channel failure, entity unload/load, player logout, plugin shutdown and
  populated Paper/Folia crash/recovery evidence. Client playtest remains required.

Physical artifact issuance and durable gameplay admission remain disabled. No merge,
release, universal capability, reward-leak closure or production-readiness verdict is
asserted by this checkpoint.


## Serialized profile reward admission

The checkpoint after `920fb92151a4ae347ec5bfc64e46f41dcdc4d49c` carries class/pet
reward provenance into the existing profile repository's serialized WAL admission,
with explicit denial distinct from persistence failure. Existing accepted receipts
survive later quarantine and restart. It also gates Bingulus progress/delivery and
pet ritual drops across owner continuations. Evidence and remaining producer routes
are in `WW-04-reward-ingress.md`. The 67-suite local run is green; native populated
reward evidence and full integrity closure remain required. Coverage: 306 authorities,
55 domains, 51 blockers. Artifact issuance and durable gameplay admission remain off.


## Stored content/provider isolation checkpoint

Based on `2ec371dd590867b69a5785503800b7955f74b210`.

Finding: journal decode, startup validation and every subsequent publication invoked
current provider/type/catalog validation on all retained projections and effect
before-images. Removing one old ability or provider could therefore block journal
load, unrelated operations and the neutral reward policy's entire influence view.

The durable codec now validates only the bounded storage envelope: namespaced schema
identity, frozen YAML-safe payload (32 KiB per value), typed metadata, exact journal
keys/schema, operation/receipt links, projection origin/scope/revisions and influence
relationships remain enforced. Provider code never executes during durable decode or
historical re-encoding. Unavailable value schemas stay opaque history; their existence
in a journal grants no capability or permission to import/activate them.

New action parameters, stage facts, receipt before/after and Undo parameters retain
current type validation in the generic admission/execution/recovery paths. New
projections must still pass the registered consumer manifest before APPLIED. Runtime
`JournalProjectionSource` validates the complete current provider projection manifest
before selecting a consumer's fields. A removed action/field/schema/content cannot
silently disappear into canonical fallback. Expected unavailable content refuses that
read; unexpected provider/codec failures enter the existing bounded circuit breaker.

No projection, effect delta, receipt, influence or accepted history is discarded on
missing content. Unrelated providers can continue reading and writing through the same
journal. Reintroduced registry content is usable without rebuilding the provider;
three unexpected faults still require the existing explicit restart recovery path.
Unloaded subjects remain pending under the existing recovery coordinator, without
force loading. There is no raw domain data repair or registry rewrite.

This corrects the earlier WW-03 assumption that every persisted projection must have
a currently installed consumer at startup. The invariant is retained at activation:
no effect is consumed without a valid current manifest. Storage availability and
current content availability are separate so that unavailable historical content
cannot erase or disable reward quarantine. The normative design file is unchanged.

`WeaverStoredContentIsolationRegressionSuite` uses actual YAML storage and journal:
removed content, an absent provider/type, an exploding codec, a removed consumer
field, healthy provider publication, receipt/Undo preservation, influence retention,
content reintroduction and circuit-breaker restart are covered. Unknown journal
fields/schema remain rejected. The original projection test now explicitly checks
opaque load plus blocked consumption and retained quarantine. All 33 relevant
Weaver/provider, artifact and PvE suites pass. Full Java 21 compile passes against
49 real dependencies with three inherited warnings; four architecture checks,
PlayerProfile guard (658 findings, zero unknown/stale/invalid/transition), consistency
(zero FAIL/WARN) and coverage (306 authorities, 55 domains, 51 blockers) pass.

Preceding exact head `2ec371dd590867b69a5785503800b7955f74b210` CI: verification run
`34082608924`; Paper `101620629737` and Folia `101620629721` PASS. Verification
`101620629507` compiled and passed real profile admission regression; its only failed
task is inherited `trashSpriteAssetAudit`. Resource pack `34082608949` and Trash
hardening `34082608953` PASS. New-head CI and populated Paper/Folia/client evidence
remain required. No full WW-04, integrity closure or production readiness is claimed.

## Faction projection and native policy checkpoint

Base: `feature/world-weaver-ww04-pve-faction-integrity` at
`e0f701afd89f2b600165dc914c81faa8f1c75b4e`. This checkpoint stays in stacked PR #158,
whose immediate dependency is WW-03 #157 at `db8d8f906bb829dfa410793033545b2a7e40237f`.
Exact resulting head/tree and CI are recorded in the PR body after publication.

`FactionWeaverProvider` registers through the existing provider composition root.
The artifact, kernel, runtime coordinator and generic GUI need no Faction branching.
The provider contributes owner-thread immutable canonical/effective inspect,
registry-backed faction/context catalogs, two typed Thread exports/imports, membership
projection, add/remove context and explicit clear actions. Multiple simultaneous
contexts still require the WW-07 export picker. `CROWN_CURSE` is excluded from both
the codec/catalog and native projection port; unreviewed future context enums remain
canonical by default.

| Projection | Effective consumer | Canonical consumers excluded |
| --- | --- | --- |
| Player membership | `FactionManager.getEffectiveMembership`, consumed by `FactionPassiveListener` damage, environment, healing, exhaustion, wither and target policy | Membership/history, tax, treasury, season, quest, territory ownership, HUD identity, spawn identity and signature-food identity |
| Six whitelisted entity contexts | `FactionMobContextResolver` effective target/truce/neutral classification and passive combat exclusions | Canonical event membership/markers, reward identity and Crown Curse lifecycle |

Native policy uses the existing `FactionPassivePolicy` and retaliation service.
It reads effective projections on its next policy decision; immediate reselection of
an already active target is not claimed. Canonical spawn/projectile marker producers
continue reading canonical contexts, so a projected context cannot become a durable
canonical marker. Membership consumer faults grant no faction passive; context faults
force the existing explicit-combat decision and cannot grant a truce. No second
hostility authority, domain PDC write or natural-history spoof is introduced.

Owner stages validate current canonical/projection fingerprints. APPLIED publishes the
projection and influence atomically in the existing journal; no pre-publication native
mutation occurs. Session cleanup removes the projection while preserving influence
quarantine. Conditional receipt Undo severs only the acknowledged projection; external
membership/context/projection drift returns CONFLICT. Explicit clear preserves all
receipts/history and influence. Persistent unavailable entities/players remain pending
until load; recovery assesses observed state and never replays the mutation.

`FactionWeaverProjectionRegressionSuite` passes 101 assertions using the real provider,
projection source, canonical passive policy, journal, YAML codec, Undo and recovery:
RED fire multiplier, DARK ambient truce exclusion, typed Thread compatibility,
canonical identity preservation, Crown prohibition, ordered add/remove, clear,
quarantine, Undo/drift, stale owner capture, session cleanup and eight before/after
write crash boundaries with unavailable-player recovery. All 41 relevant suites pass,
including existing faction, Whisperer, profile faction, Weaver, PvE and artifact suites.
Full Java 21 source compilation passes against 49 real dependencies with only three
inherited warnings. Coverage inventory is 311 authorities, 55 domains, 51 blockers;
this is an inventory check, not universal capability acceptance.

Preceding exact head `e0f701afd89f2b600165dc914c81faa8f1c75b4e` CI: verification run
`34083313247`, Paper job `101622569798` and Folia job `101622570049` succeeded.
Verification `101622570088` compiled and passed stored-content isolation regression;
its sole failed task is inherited `trashSpriteAssetAudit`. Resource-pack run
`34083313256`, Trash hardening `34083313222` and docs `34083313227` succeeded.
These native probes prove clean-store readiness and shutdown only. New-head CI,
populated native projection/reward/crash probes and client interactions remain gates.

Remaining WW-04 scope includes conditional canonical faction transactions, scripted
target/peace controls, complete passive consumer review, PvE force/refresh and complete
reward/causal propagation. The WW-00 Faction domain remains DEFERRED_BLOCKER. Artifact
issuance and durable gameplay admission remain off. This checkpoint does not claim
WW-04 completion, reward leak closure, merge readiness or production readiness.

Final local preflight: consistency zero FAIL/WARN, four architecture tests pass and
PlayerProfile authority guard/self-test pass (667 findings, zero unknown/stale/invalid/
transition). Five exact read-only projection `resolve(UUID, ...)` signatures/calls
needed reviewed RUNTIME overrides because the existing file-path heuristic also
matches method names; the heuristic and fail-closed default are unchanged.

## Conditional faction profile transaction checkpoint

Base/head dependency: `c0170b3ddd598ea83da5408d8a711dc0ab7ca183` on WW-04 #158.
`PlayerProfileFactionStore` now exposes typed immutable membership revision views,
explicit adjustment requests and observed BEFORE/APPLIED/CONFLICT assessment. The
existing `PlayerProfileTransactionManager` and repository commit membership, logical
history and the operation receipt in the same multi-section WAL. Exact faction
section revision, expected membership and operation identity are required. This is
an explicit domain adjustment, not a paid switch: wallet, switch counters, reputation,
crime/exile/oath and other faction axes are preserved. DARK still requires the
canonical exile and oath predicates. Removal retains historical identity. A reverse
adjustment appends logical history and retains the original receipt; it cannot erase
history or overwrite external revision drift, including membership ABA.

Finding corrected: a valid authority at stage entry could expire while the profile
transaction waited for storage. `TransactionPlan` carries a non-persistent, thread-safe
admission callback to the existing repository. It executes under the final profile
lock, after CAS validation and before the first WAL write. Queued logout, expiry,
revocation or callback failure writes no mutation, operation receipt or revision.
Existing accepted receipt/WAL replay does not re-run admission. Existing trusted
domain transaction constructors keep their prior behavior. No world callback or
Bukkit handle enters this persistence API.

`WeaverFactionAdjustmentRegressionSuite` uses the actual authority, service,
transaction manager and YAML repository with a controlled executor and existing WAL
fault hooks. Its 77 assertions cover queued session loss, expired/revoked tokens,
accepted replay, identity collision, DARK policy, compensating/removal history,
independent faction-axis preservation, exact revision/ABA, concurrent CAS winner,
failed admission and before/after-manifest restart assessment. Initial test fixtures
were corrected to account for cached snapshot reads and storage-normalized timestamp
precision; the final assertions verify exact disk bytes plus all mutation values and
revisions. No production validation was relaxed.

This checkpoint adds the safe profile primitive. The provider's canonical action is
not yet exposed: existing membership publication also removes guild/political roles
and invokes runtime cleanup. Durable observed completion of these post-commit effects
must be integrated before claiming a complete canonical membership route. Existing
projection actions and the generic frontend remain unchanged. No WW-04 closure or
production enable is claimed.

Exact prior head CI (`c0170b3ddd598ea83da5408d8a711dc0ab7ca183`): run `34085265831`;
Paper `101627971196` and Folia `101627971023` succeeded. Verification `101627971246`
compiled main/regression sources and passed the 101-assertion Faction projection
suite; its only failed task is inherited `trashSpriteAssetAudit`. Resource pack
`34085265750` and Trash hardening `34085265755` succeeded. Docs run `34085265887`
was still running at observation. Clean-store native probes are not populated
canonical mutation or crash evidence.

Final local verification: full Java 21 main/regression compile passes (49 real
dependencies, three inherited warnings); all 58 Weaver, faction and PlayerProfile
suites pass. Four architecture tests, authority guard/self-test (669 findings, zero
unknown/stale/invalid/transition), coverage (311 authorities, 55 domains, 51 blockers,
zero audit errors) and consistency (zero FAIL/WARN) pass. New regression is a normal
Gradle check dependency. Exact new-head CI remains required after publication.

## Faction adjustment outbox retention checkpoint

Base: `099b00fa6cf1bf6e72824f775adc776255fcbafc`, same WW-04 #158 branch.
The canonical adjustment receipt now carries bounded, versioned domain-effect
metadata in the existing operation ledger. Membership, logical history, operation
identity and pending-effect intent commit atomically. Typed reconstruction verifies
request identity/fingerprint; an acknowledgement changes only operation metadata,
uses the profile CAS and requires the exact applied faction revision. It never
rewrites membership. Duplicate acknowledgement is a no-op; external drift is
CONFLICT and keeps pending evidence. Further explicit adjustments refuse while an
older domain cleanup remains unresolved.

Both existing ledger writers now preserve PREPARED receipts and committed receipts
with unfinished effects. Unknown effect states also remain pinned. At 512 unfinished
receipts the next transaction/prepare fails before any effect; completing an outbox
makes its bounded slot evictable. An evicted receipt is never guessed as evidence of
application. Existing transaction constructors still produce the same empty metadata
and trusted admission behavior. This is a canonical operation outbox, not a second
membership or WorldWeaver authority.

The faction adjustment suite now passes 113 assertions, adding atomic outbox/restart
reconstruction, pending overlap refusal, idempotent acknowledgement, both operation
ledger retention paths, unknown state pinning and completion drift. Existing profile
transaction regression passes 50 assertions, including 512 committed pending-effect
receipts as well as 512 PREPARED receipts. All 58 Weaver/Faction/PlayerProfile suites
pass. Full Java 21 compile, four architecture tests, authority guard/self-test (669,
zero unknown/stale/invalid/transition), coverage (311 authorities, 55 domains, 51
blockers, zero errors) and consistency (zero FAIL/WARN) pass.

Exact prior head `099b00fa6cf1bf6e72824f775adc776255fcbafc` CI: run `34086019371`;
Paper `101630098491` and Folia `101630098628` succeeded. Verification `101630098639`
compiled and passed the 77-assertion adjustment test; sole failed task remains inherited
`trashSpriteAssetAudit`. Resource pack `34086019406` and Trash `34086019331` succeeded;
docs `34086019349` were still running when observed. New-head and populated native
operation evidence remain required.

The native post-commit integration is still being completed. Review identified
membership-bound guild/vote/raid admission races, Council save error suppression and
Whisperer cleanup performing a second asynchronous faction revision. Those routes
must be corrected and acknowledged before the provider exposes canonical membership.
The new outbox supplies durable evidence for that work; it does not claim the native
cleanup already runs. Artifact issuance and gameplay admission remain disabled.

## Native membership cleanup consistency checkpoint

Base: `b2eda52fcf3a802d54aed0f18328b8ba43e7e92f`, WW-04 #158.
Native membership publication schedules Whisperer cleanup. Previously this could
commit a second faction revision after the acknowledged membership transaction,
making the operation's own post-commit cleanup look like external drift. The
Whisperer authority now exposes its canonical membership reconciliation as a pure
section transformation. The existing faction assignment/removal paths compose it
before their single WAL commit, including normal DARK join and explicit adjustment.
Civil membership preserves the role; DARK/guest uses the same canonical cleanup rule
as before. Crime/exile/oath and unrelated section fields stay intact. Later native
cleanup is a verified no-op and cannot invalidate the acknowledged revision.

Council save previously logged IOException and returned normally, which could let a
durable cleanup coordinator acknowledge a failed write. It now propagates the
failure, matching the other canonical political/guild stores. No private map or raw
YAML mutation is introduced by the provider.

The real faction profile regression now passes 170 assertions. Four extra native and
adjustment routes prove membership/Whisperer atomicity, independent legal axes,
post-commit cleanup idempotence and exact operation observation. All 58 relevant
suites pass. Full Java 21 compile, four architecture checks, authority guard (669,
zero unknown/stale/invalid/transition), coverage (311 authorities, 55 domains, 51
blockers, zero errors) and consistency (zero FAIL/WARN) pass. A populated native
Council filesystem-failure probe remains required; compilation and profile tests do
not claim that evidence.

Prior exact head `b2eda52fcf3a802d54aed0f18328b8ba43e7e92f`: CI run `34086789649`;
Paper `101632247594` and Folia `101632247727` succeeded. Verification `101632247779`
compiled and passed the 113-assertion faction outbox test; its only failed task is
inherited `trashSpriteAssetAudit`. Resource pack `34086789632` and Trash
`34086789655` succeeded. New-head CI remains required. Native role-admission fencing
and durable cleanup coordinator/provider action integration continue; this checkpoint
does not mark those surfaces or WW-04 complete.

## Native membership admission and cleanup continuation checkpoint

Base: `23ec613c541af7b374d89ab35cf994e0d8818edc`, WW-04 #158.
`FactionManager` now owns a bounded transition runtime over its existing profile
store. Claiming a transition runs under the canonical Guild, Council, King and Raid
admission locks, in that order, before the asynchronous profile write is queued.
A guild creation/cost operation already holding its lock finishes before the claim;
new affected role admissions cannot race past the claim. The runtime keeps at most
128 in-flight/paused leases, each tied to an exact operation UUID. Duplicate rejected
calls cannot pause, release or steal the original running lease.

The facade uses the existing membership-change hook and Guild reconciliation, then
explicitly flushes Guild/Council/King snapshots before acknowledging the canonical
outbox. A failed write retains pending evidence. Retry flushes the domain snapshots
even when an earlier attempt changed memory before failing to persist it. Recovery
refreshes the real profile/WAL without first discarding the active cached read view;
it assesses BEFORE/APPLIED/CONFLICT and never resubmits membership mutation. Lost
acknowledgement of already completed cleanup does not run cleanup again. A fresh
conflict with no matching unfinished outbox releases only the transient admission
lease; a true partial cleanup keeps its durable evidence and remains blocked.

| Native surface | Admission/cleanup behavior |
| --- | --- |
| Guild create/accept and member capabilities | Existing synchronized admission; pending membership grants no guild access. Canonical reconciliation has a private canonical lookup so cleanup still reaches the stored membership. |
| Council votes | Membership checks and vote publication share the existing Council lock. |
| King votes/crowning | Final membership check under election lock. Rejected crowning returns a result; admin command and election broadcast do not report false success. |
| Raid participation | Final membership eligibility under the existing Raid lock. |
| Faction benefits/paired membership | Pending transition grants no membership-dependent eligibility; canonical identity getters remain separate. |
| Whisperer | Post-commit reconciliation reads canonical membership, so the temporary eligibility fence cannot erase a valid civil role. |
| Spy disguise | Pending activation has an exact operation UUID; membership is rechecked on owner callback. Activation and cleanup share a lock, and an old callback/removal cannot resurrect an invalidated activation or remove a newer active disguise. |

Faction reads now prefer the current immutable profile cache over the rebuildable
all-owner mirror. The domain runtime uses UUIDs/immutable profile values and is
entered through profile/IO authority; GUI/artifact/kernel dispatch is unchanged.
This checkpoint does not yet connect autonomous startup outbox draining or expose
the provider's canonical action. Those remain the next integration steps. A true
external drift with unfinished side effects retains an explicit review requirement;
it is never force-undone or silently discarded.

Verification: full Java 21 compile passes against 49 real dependencies with the same
three inherited warnings. The 58-suite run passed 57 and identified an existing
source-order test that compared unrelated methods across the entire FactionManager
file. Its original durable-setter ordering condition is retained and scoped to the
actual setter. The final seven affected Faction/runtime suites all pass, including
the 369-assertion real profile/outbox/runtime test. That test covers pre-WAL authority
loss, duplicate mutation/recovery ownership, failed cleanup retry, before/after
membership WAL and cleanup-acknowledgement loss, drift and bounded leases. Four
architecture checks, authority guard/self-test (671 findings, zero unknown/stale/
invalid/transition), coverage (313 authorities, 55 domains, 51 blockers, zero errors)
and consistency (zero FAIL/WARN) pass. Native client concurrency, LibsDisguises callback
and populated domain filesystem-failure probes remain explicit evidence gates; the
profile fixture is not presented as a live Guild/Spy client test.

Prior exact head `23ec613c541af7b374d89ab35cf994e0d8818edc`: CI run `34087191108`;
Paper `101633373097` and Folia `101633373192` succeeded. Verification `101633372946`
compiled and passed the 170-assertion test; its only failed task remains inherited
`trashSpriteAssetAudit`. Resource pack `34087191038`, Trash `34087191036` and docs
`34087191042` succeeded. New-head CI remains required. Artifact issuance, gameplay
admission and production enable remain off; no full WW-04/coverage/release verdict.
