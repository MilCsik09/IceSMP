# WW-03 — Durable state, projection and conditional Undo

Internal developer implementation evidence. This phase is in progress.

## Exact base

- Repository: `MilCsik09/IceSMP`
- Branch: `feature/world-weaver-ww03-durable`
- Immediate base: `feature/world-weaver-ww02-kernel`, PR #156
- Base commit: `16847efabbef6213baaf3c24a48ed343d9173ede`
- Base tree: `e3bfe3743ac68c9076b3d752787726bb68080fca`
- Cumulative gameplay pin: #155, `004c12abf6e896b1931a695f980d4841bbf785e9`
- Stack: #152 → #155 → #153 → #154 → #156 → this phase.

## Journal and recovery checkpoint

| Requirement | Implemented evidence | Remaining boundary |
|---|---|---|
| Durable PREPARED | `WeaverJournal.prepare`, single bounded IO executor, acknowledgement after shared `YamlStore.saveAtomic` | Normal mutation executor still rejects journal-required actions pending durable stage/compensation integration and gameplay reward consumers |
| APPLIED → audit → COMMITTED | State is durable before idempotent audit write; final state clears pending audit; CAS revision on transitions | Receipt, projection, influence and pending audit now publish together in schema 2; gameplay effect consumers remain provider work |
| Persistence | Strict schema 2 codec plus schema 1 one-shot migration; explicit SubjectRef/value/request/receipt/recovery/effect fields; real YAML round-trip test | Production migration and full populated runtime restart remain evidence gates |
| Storage failure | Writer closes after write error, including ambiguous acknowledgement; published state never advances before write success | In-flight gameplay compensation is part of the next execution integration |
| Audit | Idempotent operation/outcome key, 10,000-entry oldest-first cap, bounded identity-only summaries; hidden payload never logged | Undo/conflict/canonical compensation audit routes remain to integrate |
| Recovery authority | Separate operation-bound `RecoveryContext` token; cannot be used as interactive primary session authority | Compensation execution needs its own constrained operation context |
| Crash reconciliation | BEFORE/APPLIED/PARTIAL matrix; no prepare call and no stage replay; quarantined providers can assess recovery | Provider-specific recovery assessments must accompany their actual mutation surfaces |
| Unavailable subject | Pending entity/chunk/world classification; 30-day unresolved status without deletion; entity/player/chunk/world availability listeners | Native unload/migration timing needs real server/client evidence |
| Load race | One bounded reattempt when a load event overtakes the unavailable snapshot; no chunk force-load | Continued event bursts remain pending until a later availability event |
| Startup/shutdown | Constructed in WorldWeaverRuntime; async strict load + reconciliation precede kernel admission; serialized writer drains on close | Checkpoint c63ed527 has successful Paper/Folia readiness markers; new effect-lifecycle head needs its own evidence |

The existing opt-in DEV runtime probe now accepts bounded readiness predicates.
This phase adds the WorldWeaver journal readiness predicate, and Paper/Folia CI
requires its marker. It grants no actor token, issuance, permission or mutation.

Integer payloads normalize to the existing canonical integral representation
before entering immutable Weaver values/recovery payloads. This prevents YAML's
Integer/Long implementation choice from changing value or receipt identity after
restart. It does not alter domain values or canonical content.

A serialized file is capped at 2,000,000 UTF-8 bytes, below parser limits. The
normative entry caps remain upper bounds (8 active operations, 2,048 receipts).
Capacity refuses further admission; this checkpoint does not silently delete
unresolved operations or claim that unlimited receipt history is retained. Safe
receipt retention with projection/Undo dependency protection remains to integrate.

## Verification

- Full Java 21 main and regression compile against all 49 real CI dependencies;
  no stubs/exclusions; three inherited warnings.
- `WeaverPersistenceRegressionSuite`: PREPARED/APPLIED/audit/COMMITTED order,
  eight before/after write-failure boundaries, sticky failure closure, CAS conflict,
  audit idempotence, strict schema/unknown fields, integral normalization and real
  YAML/fsync round trip.
- `WeaverCrashRecoveryRegressionSuite`: complete observed-state matrix, no replay,
  recovery through provider quarantine, idempotent re-entry, pending entity load
  and the load-event/snapshot race.
- Existing ten Weaver and three DEV suites remain required; both new suites join
  the normal Gradle check graph.
- The four static architecture checks and source coverage audit remain required.

The immediate base has exact remote evidence: run `34064234093`, verification
`101570117812`, Paper `101570117814`, Folia `101570117652`. Full compilation and
all ten Weaver suites passed; the sole failed full-build task was inherited
`trashSpriteAssetAudit`. Resource-pack `34064234060` and Trash `34064234062`
workflows passed. These base probes do not prove this phase's new journal.

## Atomic effect checkpoint

`WeaverJournalState` publishes operation/receipt, planned influence, applied influence,
projection and sequence as one immutable generation. The derived influence index
is built on the serialized storage executor and published with the same generation;
reward lookups perform no live Bukkit reads and do not rebuild the index on a
region thread. Spatial evidence uses bounded chunk buckets and exact AREA geometry.

| Requirement | Implementation evidence | Practical limit |
|---|---|---|
| Pre-mutation quarantine | `prepare(record, intent)` durably marks the SANDBOX subject plus bounded explicit sources; no invented applied timestamp | The actual durable mutation executor must await this acknowledgement before entering an owner stage |
| Atomic effects | `WeaverEffectReducer.applied` publishes receipt/projection/influence together; four write boundaries tested before and after acknowledgement | Existing interactive executor remains closed to journal-required actions |
| No washing | Entity/item/event influence remains monotonic through compensation, sever and later LIVE_GM; player/world/spatial tails last at least five minutes | Canonical reward producers and propagation listeners still need integration |
| Uncertain effects | PREPARED and receipt-less NEEDS_REVIEW retain source denial; observed BEFORE/ABORTED clears unused intent | No manual force-release route exists |
| Projection consumers | Optional adapter contribution freezes with action/type manifests; store refuses a field without a compatible named consumer | Current regression consumer is a fixture; no PvE/Faction/Territory/Trash gameplay consumer is claimed here |
| Precedence | Highest sequence scalar, capability-specific latest member, expiry and sever recompute remaining effective values | No canonical PDC/registry write-back port |
| Lifecycle | Startup, artifact-session invalidation, global maintenance and shutdown enqueue expiry/session cleanup; unresolved effects stay protected | Native populated-state lifecycle/client evidence remains required |
| Capacity | 256 session / 1,024 persistent / 32 per subject, monotonic sequence; preparation accounts for pending capacity and influence reservations | File byte cap can refuse earlier; dependency-aware receipt retention remains open |
| Recovery | Persistent/session observed-APPLIED recovery requires explicit effect evidence; missing evidence becomes NEEDS_REVIEW | Actual provider assessments must prove their own runtime state |
| Persistence | Typed effects and all influence scopes round-trip through schema 2; raw native objects are rejected | Unsupported legacy persistent/session state fails closed pending explicit migration |

### Storage adaptation required by the real Bukkit parser

The new real-YAML regression exposed that Bukkit expands dotted map keys such as
`fixture.rank` into configuration paths. It also recognizes special serialization
map keys. Neither interpretation is permitted for a typed Weaver payload. The
storage adapter now uses a versioned envelope with canonical UTF-8/base64url **map
keys**, recursively preserving ordinary YAML scalar/list/map values. Only the
storage adapter decodes keys after strict tracked loading; shared `YamlStore`
fsync/atomic replacement remains the sole write primitive. Fresh detached maps
also avoid object-identity alias reuse. This changes no domain type or Java-object
serialization contract. The regression includes dotted keys, nested lists and a
literal `==` key as data.

Plain schema 1 files retain a bounded one-shot migration path. Unresolved SANDBOX
operations recover planned quarantine; receipts recover their subject influence.
Legacy persistent/session operations lacking effect state fail closed rather than
inventing a projection. Schema 2 validates origin links, subject evidence,
projection sequences, registered types and capacity on every load/publication.

### Checkpoint verification

- `WeaverProjectionRegressionSuite`: adapter consumer registration, no undeclared
  consumer/field read, scalar/member precedence, session/expiry/sever, sequence
  conflict, all three projection caps, YAML/fsync effect round trip and restart.
- `WeaverInfluencePersistenceRegressionSuite`: 156 durable PREPARED source/channel
  denials, all six scopes, player/entity identity alias, spatial bounds, monotonic
  compensation/LIVE_GM, five-minute tail, unresolved/aborted outcomes, eight
  before/after crash boundaries, schema migration and observed effect recovery.
- Both suites are normal Gradle `check` dependencies. These are infrastructure
  tests; they do not prove zero leakage through existing gameplay reward producers.
- The prior checkpoint's exact remote run `34065353114` compiled all sources and
  passed all 12 then-existing Weaver suites. Paper job `101573029345` and Folia job
  `101573029207` passed with the journal readiness marker and clean shutdown.
  Resource pack `34065353063` and Trash `34065353147` passed. The full verification
  job's remaining failure was inherited `trashSpriteAssetAudit`.
- Docs workflow `34065353109` additionally exposed an exact `resolve(UUID operation)`
  false positive in the PlayerProfile authority classifier. The reviewed allowlist
  now classifies only that signature as a global operation aggregate; source-wide
  exclusions and classifier relaxation were not introduced. The authority audit
  reports zero unknown/stale/invalid/transition rows; its targeted gate passes.

## Durable stage execution checkpoint

`WorldWeaverProvider.prepareEffects` contributes a typed intent and immutable
result-to-effect factory. The kernel passes it through the generic execution
coordinator; the existing cosmetic path cannot execute a journal-required action.
The production composition keeps the gameplay-integrity readiness predicate
closed until canonical reward producers are gated. Physical issuance stays disabled.
The conditional gate is exercised open and closed in an isolated regression.

`WeaverDurableExecutionCoordinator` awaits durable PREPARED before entering any
stage, renews the primary session on the actor owner before every target owner,
validates immutable stage/receipt results and performs the atomic APPLIED/audit/
COMMITTED sequence. Duplicate prepare rejection cannot settle another operation;
only an acknowledgement belonging to this execution attempt enables its cleanup.

Compensation uses a separate, expiring, operation-and-stage-bound authority. It
cannot become an interactive `WeaverAuthorityToken`, and the provider's owner-local
compensation checks the observed fingerprint against the acknowledged stage result.
Acknowledged stages compensate in reverse order, including after actor logout.
The first failed/conflicting compensation stops the reverse chain. Completed and
uncertain effects retain quarantine and NEEDS_REVIEW after partial failure; no
successful full receipt or fabricated applied influence is created for a failed plan.
A wholly unstarted operation can abort and release its unused intent.

A started owner timeout/shutdown skips compensation while a late effect could
still occur. Late completion cannot advance the journal, create a success receipt
or release source denial. Storage failure leaves durable evidence for startup
reconciliation; it does not trigger unjournaled retries.

`WeaverDurableExecutionRegressionSuite` covers the PREPARED barrier, exact successful
commit, failed-write no-effect guarantee, duplicate committed and PREPARED IDs,
reverse compensation after logout, external drift, started/unstarted timeout,
actual late future completion, bounded concurrent admission and the closed/open
integrity readiness gate. It joins the normal Gradle check graph. Native populated
stage failure/crash and compensation tests remain required provider/runtime evidence.

The prior atomic-effect head `3602a360b674192c1e53b518ecdacea14da10140` now has exact
remote evidence: run `34067431815`, verification `101578561843`, Paper `101578561668`,
Folia `101578561578`. Full Java compilation and all 14 Weaver suites passed; Paper
and Folia passed readiness and clean shutdown. The sole failed full-build task was
inherited `trashSpriteAssetAudit`. Docs `34067431800`, resource-pack `34067431817`
and Trash `34067431842` workflows all passed. This is the preceding head's evidence,
not populated interactive mutation proof for the new executor.

## Conditional Undo checkpoint

`WeaverUndoCoordinator` resolves a durable COMMITTED receipt, verifies its operation
revision and a fresh owner snapshot, and invokes the owning provider's `prepareUndo`
inside the provider circuit breaker. The resulting ordinary action uses the same
parameter, arming, execution, effect, audit and recovery routes. A receipt-only inverse
descriptor need not be listed as a standalone action by discovery; explicit discovery
blocks still apply, and prepareUndo validates the fresh target and domain state. No force path exists.
The generic history screen merges bounded durable/session receipts, exposes typed
before/after facts and shows both receipt and operation status. Undo parameters are
locked to the receipt. DESTRUCTIVE/CANONICAL confirmations require a second fresh
view, and every execution attempt consumes arming before asynchronous capture.

`WeaverUndoClaim` reserves the original receipt/revision at durable PREPARED. A second
pending attempt cannot execute or abort that reservation. At APPLIED, effect changes,
the original receipt's UNDONE status and its incremented operation revision publish
in one durable state. The original audit is retained; the compensating operation
adds its own UNDONE audit. Failed or ambiguous execution cannot silently reopen the
receipt. Recovery never turns a previously consumed receipt back into an available
one; reversing a later effect requires its own observed-state decision/action.

State schema 3 also records exact added/removed projection before-images and ended
influence evidence. Compensation restores a removed projection only while its ID
remains absent, and removes an added projection only while its exact value remains
unchanged. A replacement is CONFLICT. Restoring active influence preserves any
longer quarantine tail. Schema 1/2 migration preserves existing effects but marks
unavailable historical deltas explicitly; it cannot fabricate compensation evidence.

### Minimal Subject adaptation

The normative ITEM_SLOT ref includes its expected revision and byte fingerprint.
After a canonical item mutation, capturing that old ref correctly rejects STALE_SLOT.
Likewise a LOCATION creation can have an ENTITY as its inverse target. Reusing the
original subject unconditionally would make these current domain inverses unreachable.
An optional provider-owned `weaver:subject_ref@1` in receipt.after, with capability
`weaver.undo_target`, therefore names the exact inverse target. The generic codec
requires one unambiguous declaration, matching provider/key/facet ownership and valid
stable Subject data. Absence retains the original subject. Capture, operation claims
and restart validation all use that same declared target; item identity/revision/
fingerprint fences remain intact. This is descriptor data, with no named-provider
branch and no live state in the receipt.

`WeaverUndoRegressionSuite` covers fresh-snapshot conflict, the provider Undo route,
one-use revisions, preserved audit, eight before/after crash boundaries, competing
reservations, real YAML claim/delta persistence, exact sever compensation, external
replacement conflict, schema 2 migration, changed ITEM_SLOT revision, created ENTITY
identity, and rejected ambiguous/foreign target evidence. It is a normal Gradle check
dependency. These isolated fixtures do not prove current gameplay provider inverses. Full Java 21
main/regression compilation, all 16 Weaver and three DEV suites, four architecture
checks, source inventory (286 authorities, 51 implementation blockers), the 650-row
PlayerProfile authority gate and consistency (zero FAIL/WARN) passed locally.

The prior durable-stage head `8e42181c860959df4a2ad38cb822b44dac21673b` has exact remote
evidence: run `34068303124`; Paper `101580886238` and Folia `101580886321` succeeded.
Verification `101580886301` compiled all sources and passed the durable execution
suite; the sole failed task was inherited `trashSpriteAssetAudit`. Docs `34068302939`,
resource pack `34068302941` and Trash `34068302943` succeeded. These clean-store probes
are not populated Undo/crash/client evidence for the new checkpoint.

## Bounded AREA checkpoint

`WeaverAreaEngine` calculates at most nine chunks without accessing Bukkit, then
collects on each chunk's region. `FoliaWeaverAreaAccess` verifies loaded blocks and,
separately, `Chunk.isEntitiesLoaded()` before calling `getEntities()`; that method
would otherwise force-load entity data ([Paper 1.21.11 API](https://jd.papermc.io/paper/1.21.11/org/bukkit/Chunk.html#getEntities())).
Entity arrays above 4,096 entries reject the scan before iteration. Entity ownership
is checked before any state/identity read. Only stable refs leave the collection
stage; snapshots are reacquired on each child's owner, with at most 16 concurrent
subtasks. Unavailable chunks, retired entities and targets that left the shape have
explicit bounded skip evidence. Duplicate migration sightings are deduplicated.

The existing five shapes share this engine. Descriptor limits can tighten the
4,096-block / 128-entity / 9-chunk / 9-region caps. Chunk count conservatively bounds
region count; a region merge cannot enlarge admission. A block collection may cover
4,096 positions, but the normative token budget still applies: cost is at least
`1 + ceil(targets / 16)`. It is never clamped to make an oversized action affordable.
The existing 20-token bucket therefore rejects a block batch whose computed cost
exceeds its available budget. This preserves both separate design limits.

The generic confirmation and execution routes recollect targets and compare a
stable fingerprint of shape, membership, child revisions and skip evidence. A
changed target set cannot inherit old confirmation. The provider contributes one
owner-bound stage per child through `prepareArea`; the engine requires compensation
for every journaled child, reacquires its snapshot before mutation and rejects drift.
A final pure aggregation stage supplies the receipt's complete after fingerprint.
Child stages currently execute sequentially through the ordinary durable coordinator
(concurrency 1, within the maximum 16); collection/snapshot fanout uses the bounded
continuation runner. Partial failure compensates acknowledged children in reverse
order; external drift stops compensation and retains NEEDS_REVIEW/quarantine.

One PREPARED operation includes the AREA and all selected entity/player sources.
The internal intent envelope allows 129 entries solely to accommodate the 128-child
cap plus the parent scope; target/action caps do not increase. Normal APPLIED state
publishes all corresponding influence together. Destructive/canonical AREA remains
forbidden. Child projection materialization still needs the later adapter work.

`prepareAreaUndo` receives the same fresh immutable selection and uses the normal
Undo claim/execution route. Recovery stores reserved `weaver.area` evidence inside
the bounded operation payload before mutation. Providers cannot supply/replace this
reserved evidence. Recovery captures only those original targets under the separate
operation authority and invokes `assessAreaRecovery`; it does not rescan for newly
arrived entities, prepare a new action or replay stages. Missing original entities
remain pending until their load event. Missing block chunks similarly remain pending;
newly loaded originally skipped chunks are not enrolled. The payload's existing
64-KiB/value caps can reject an excessively large recovery plan before mutation.

`WeaverAreaExecutionRegressionSuite` covers nine owner tasks, 16 concurrent admissions,
128/129 entity rejection, 4,096/4,095 block limits, synchronous continuation stack
safety, failure draining, deterministic fingerprints, duplicate/moved/retired targets,
aggregate receipts, child compensation/conflict, authority revocation, close-before-
read, AREA intent bounds, durable recovery evidence, pending child load, no rescan,
and AREA Undo through the provider and journal. It is a normal Gradle check dependency.
Actual loaded-region migration, populated server crash and client confirmation remain
native/human evidence gates; these fixtures do not establish production Folia safety.

Local verification: full Java 21 main/regression compilation (49 real dependencies,
zero errors, three inherited warnings), 17 Weaver plus three DEV suites (20 passed),
four architecture checks, the 650-row PlayerProfile authority gate, source inventory
(288 authorities, 51 implementation blockers, zero inventory errors), and consistency
(zero FAIL/WARN). The native AREA implementation still requires populated runtime
and human/client evidence before a Folia production verdict.

The preceding Undo head `14ed612f811b03c5aef7a8c615dd35eb618b867a` has exact remote
evidence: run `34071177556`; Paper `101588692136` and Folia `101588692240` succeeded
with readiness and clean shutdown. Verification `101588692299` compiled all sources
and passed the Undo suite; the sole failed task was inherited `trashSpriteAssetAudit`.
Resource-pack workflow `34071177569` succeeded. These are preceding-head clean-store
probes, not a populated AREA acceptance result.

## Action revision dependency checkpoint

The native snapshot includes location, fire/freeze ticks, health and world time.
Hashing every inspect fact for every action made unrelated movement/time changes
invalidate confirmations and inverses. `ActionDescriptor.revisionScope` now declares
an optional schema-versioned set of relevant fact keys. `WeaverRevisionScope` hashes
those typed payloads and the complete stable SubjectRef deterministically, retaining
all facts for inspection. Missing dependencies reject the action; schema/type,
identity, item-slot revision/fingerprint and selected-field changes remain conflicts.
The default retains the full existing fingerprint byte-for-byte. Existing receipts
are not reinterpreted or migrated to a weaker revision rule.

The generic fresh-action and recovery routes apply descriptor scopes to immutable
owner-captured snapshots. AREA collection, child admission, compensation and recovery
apply the same child scope before aggregating membership/revisions. No scope reads
live Bukkit data or names a gameplay provider. Canonical transaction adapters must
include the revisions/fingerprints required by their own domain API; declaring a
scope cannot override a domain conflict or bypass conditional Undo. Providers must
produce after fingerprints using the corresponding scope. Current descriptors keep
the full default until their actual dependency choice is implemented and tested.

`WeaverRevisionRegressionSuite` proves unchanged selected state survives unrelated
movement, while selected-field drift, type/scope schema changes and different subject
or ITEM_SLOT identity fail comparison. It also tests idempotence, retained inspect
facts, default compatibility and a scoped receipt going through ordinary provider
Undo and durable execution after movement, with relevant drift still rejected.
It is a normal Gradle check dependency. Full Java 21 main/regression compilation,
all 18 Weaver and three DEV suites (21 passed), four architecture checks, the
650-row PlayerProfile gate, source inventory and consistency passed locally.

The preceding AREA head `b8a8ff05d25cf866d481ca2a0a8d0a6c1d6bd638` has exact remote
evidence: run `34073063138`; Paper `101593893738` and Folia `101593893813` succeeded
with readiness/shutdown. Verification `101593893661` compiled all sources and passed
the new AREA execution suite; only inherited `trashSpriteAssetAudit` failed.
Resource-pack `34073063036` and Trash `34073063016` workflows succeeded. These probes
still establish clean-store startup, not populated gameplay manipulation.

## Protected retention checkpoint

`WeaverJournalRetention` rotates only settled, unreferenced WorldWeaver operation/
receipt records. Pending audit, PREPARED/APPLIED/NEEDS_REVIEW, influence intents,
active projections, active influence and reward quarantine pin their origin. Undo
claims and projection/influence before-images retain their dependency chain. The
oldest eligible leaf rotates first; a parent can rotate only after its referencing
children are gone. An incoming Undo protects its original receipt before admission.
Entity/item/event SANDBOX taint is monotonic and is never discarded to make space.

Retention and the next PREPARED record publish in one atomic state generation.
Neither a refused admission nor a failed write exposes a pruned-only generation.
The independent bounded audit survives receipt rotation and rejects duplicate
operation IDs while that audit evidence is retained. This is WorldWeaver history
retention, not deletion of a subsystem's immutable canonical history.

The YAML store preflights its existing 2,000,000-byte cap on the IO authority before
writing. Expected byte-capacity refusal does not poison the writer; real or
ambiguous IO failure still closes it. Eligible history rotates in bounded batches
when bytes fill before entry caps. If all records are protected, admission fails
closed. The 2,048 receipt / 2,056 operation limits remain upper bounds rather than
a promise that every maximum-sized payload fits. The audit rotates oldest-first
at 10,000 entries or earlier at its byte cap, preserving the newly acknowledged
entry even if the clock moved backwards. Batched byte rotation avoids serializing
a near-capacity YAML document once per individual removed row.

`WeaverRetentionRegressionSuite` covers full 2,048-receipt admission, atomic
before/after-write failure and restart, retained-audit replay denial, unresolved
and pending-audit protection, expired player versus monotonic entity influence,
active projection and Undo/before-image dependencies, incoming Undo protection,
recoverable capacity rejection, actual YAML byte-cap preflight without IO, and
both audit limits with backwards timestamps. It is a normal Gradle check dependency.
Full Java 21 main/regression compilation passed with zero errors and three inherited
warnings; all 19 Weaver and three DEV suites passed (22 total). Four architecture
checks, the 650-row PlayerProfile authority gate, the inventory (289 authorities,
55 domains, 51 implementation blockers, zero audit errors), and consistency
(zero FAIL/WARN) passed locally.

The preceding revision-scope head `cc07e7c7186ebf176be506f992a398aa93cb389a` has exact
remote evidence: verification run `34073935305`; Paper job `101596331418` and Folia
job `101596331331` succeeded with readiness and clean shutdown. Verification job
`101596331164` compiled main/regression sources and passed the revision suite; its
only failed task was inherited `trashSpriteAssetAudit`. Docs `34073935329`, resource
pack `34073935301`, and Trash hardening `34073935326` workflows succeeded. These are
clean-store probes, not populated mutation/crash acceptance for the current change.

## AREA projection scope and capacity checkpoint

The existing journal allowed projection effects only on `operation.subject`, which
is the AREA ref for fanout. That prevented an adapter from publishing projections
on selected children. `WeaverAreaRecoveryEvidence` schema 2 now persists each
selected child's action-scoped before fingerprint along with the existing stable
membership. Schema 1 remains readable with explicitly absent child revisions;
recovery never fabricates them or grants legacy data permission to add child effects.

`WeaverOperationScope` resolves only the original subject and children with durable
revision evidence. Projection additions must match the appropriate child fingerprint;
removals must belong to that acknowledged scope and provider. Journal validation,
effect before-images, compensation and YAML reload apply the same relation. The
projection-consumer registry maps fanout actions to actual child kinds while still
requiring each emitted field/type to have a registered consumer. A provider may
publish a consumer for its compatible subset of the fanout kind envelope; this does
not authorize unsupported selected children or supply a gameplay implementation.

`PreparedEffects` can declare bounded per-target projection reservations. The normal
durable executor records them under a reserved recovery key before PREPARED; provider
payloads cannot spoof that key. Defaults reserve one projection for the original
subject, or one per selected AREA child. Explicit reservations can bundle up to 32
per subject and 128 per effect commit. Requested targets must be in the durable scope.
ONE_SHOT actions cannot add projections. Pending PREPARED and unresolved NEEDS_REVIEW
operations retain reservations; actual plus pending totals enforce both lifetime and
per-subject caps before any owner stage. Applied effect counts cannot exceed the
acknowledged reservation. AREA influence intent includes every selected entity/player
and the parent spatial scope before mutation. Existing payload byte bounds can reject
large plans before execution; target caps do not override storage/rate limits.

`WeaverProjectionScopeRegressionSuite` proves 128 child projection materialization,
129 parent/child influence targets, foreign-child and wrong-revision refusal, exact
child sever/compensation, actual YAML round trip, 256 SESSION reservations, unresolved
reservation retention, 32 per-subject reservations, effect overrun rejection, legacy
schema behavior and reserved-key rejection. Eight before/after-write crash boundaries
retain complete child quarantine and atomic receipt/effects. A guarded AREA plan also
runs through the real durable coordinator and consumer registry with automatically
persisted child reservations. This is a fixture consumer, not a native PvE acceptance.

Full Java 21 main/regression compilation passed with zero errors and three inherited
warnings. All 20 Weaver and three DEV suites passed (23); four architecture checks,
the 650-row profile gate, source inventory (290 authorities, 55 domains, 51 remaining
implementation blockers, zero inventory errors), and consistency (zero FAIL/WARN)
passed locally. New suite is a normal Gradle check dependency.

Preceding retention head `b330799e6161823030afbacac0754844ab573f90`: run `34075073677`;
Paper `101599481669` and Folia `101599481667` succeeded with readiness/clean shutdown.
Verification `101599481578` compiled both source sets and passed retention regressions;
only inherited `trashSpriteAssetAudit` failed. Resource-pack `34075073731` and Trash
hardening `34075073678` succeeded. These are clean-store probes, not populated AREA
projection or gameplay recovery evidence.

## Remaining WW-03 implementation
- Native provider mutation/compensation and exact late-effect reconciliation evidence;
  the generic durable execution path is implemented behind the closed integrity gate.
- Actual projection consumers and provider effect materialization; the generic registry/store foundation is implemented.
- Provider-specific canonical compensating history and native Undo evidence; the generic durable Undo path is implemented.
- Provider-specific revision dependencies and native mutation/Undo evidence; the generic descriptor scope route is implemented.
- Native AREA provider effects/recovery and client evidence; bounded generic collection, guarded children, Undo and recovery routes are implemented.
- Real crash/restart/disable evidence for those integrated paths.

No universal coverage, reward-leak closure, merge readiness or production-ready
verdict is asserted. Physical issuance remains disabled. The phase is not done
merely because the journal and recovery foundation compile.
