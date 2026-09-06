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
| Durable PREPARED | `WeaverJournal.prepare`, single bounded IO executor, acknowledgement after shared `YamlStore.saveAtomic` | Normal mutation executor still rejects journal-required actions pending effect/influence atomic publication |
| APPLIED → audit → COMMITTED | State is durable before idempotent audit write; final state clears pending audit; CAS revision on transitions | Projection/influence effects are not yet part of that state snapshot |
| Persistence | Strict schema 1 codec; explicit SubjectRef/value/request/receipt/recovery fields; real YAML round-trip test | Production migration and full populated runtime restart remain evidence gates |
| Storage failure | Writer closes after write error, including ambiguous acknowledgement; published state never advances before write success | In-flight gameplay compensation is part of the next execution integration |
| Audit | Idempotent operation/outcome key, 10,000-entry oldest-first cap, bounded identity-only summaries; hidden payload never logged | Undo/conflict/canonical compensation audit routes remain to integrate |
| Recovery authority | Separate operation-bound `RecoveryContext` token; cannot be used as interactive primary session authority | Compensation execution needs its own constrained operation context |
| Crash reconciliation | BEFORE/APPLIED/PARTIAL matrix; no prepare call and no stage replay; quarantined providers can assess recovery | Provider-specific recovery assessments must accompany their actual mutation surfaces |
| Unavailable subject | Pending entity/chunk/world classification; 30-day unresolved status without deletion; entity/player/chunk/world availability listeners | Native unload/migration timing needs real server/client evidence |
| Load race | One bounded reattempt when a load event overtakes the unavailable snapshot; no chunk force-load | Continued event bursts remain pending until a later availability event |
| Startup/shutdown | Constructed in WorldWeaverRuntime; async strict load + reconciliation precede kernel admission; serialized writer drains on close | Exact new-head Paper/Folia evidence is required |

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

## Remaining WW-03 implementation

- Atomic APPLIED publication of receipt/projection/influence effects.
- Durable mutation execution, reverse-stage compensation and started-timeout
  reconciliation without allowing a late side effect to be reported aborted.
- Explicit projection consumer registry, precedence and restart-safe state.
- Conditional Undo through normal durable execution with original receipt update
  and canonical compensating history.
- Bounded AREA collection/fanout.
- Receipt retention protected by unresolved operation/projection/Undo references.
- Real crash/restart/disable evidence for those integrated paths.

No universal coverage, reward-leak closure, merge readiness or production-ready
verdict is asserted. Physical issuance remains disabled. The phase is not done
merely because the journal and recovery foundation compile.
