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

## Remaining WW-03 implementation
- Durable mutation execution, reverse-stage compensation and started-timeout
  reconciliation without allowing a late side effect to be reported aborted.
- Actual projection consumers and provider effect materialization; the generic registry/store foundation is implemented.
- Conditional Undo through normal durable execution with original receipt update
  and canonical compensating history.
- Bounded AREA collection/fanout.
- Receipt retention protected by unresolved operation/projection/Undo references.
- Real crash/restart/disable evidence for those integrated paths.

No universal coverage, reward-leak closure, merge readiness or production-ready
verdict is asserted. Physical issuance remains disabled. The phase is not done
merely because the journal and recovery foundation compile.
