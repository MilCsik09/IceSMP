# WW-04 bounded native quest queue

Internal developer checkpoint. Branch `feature/world-weaver-ww04-pve-faction-integrity`,
PR #158; thematic parent `f8356e5a88224c3697b7636902c1e9f9f62a65bd`.
The phase remains stacked on WW-03 #157. Gameplay enable gates remain closed.

## Implementation

The existing QuestManager now uses `QuestMutationQueue` instead of its unbounded
future-tail map. This is an execution queue, not another quest/profile authority.
It stores UUIDs and continuation functions; the manager's profile continuations retain
immutable arguments, not live Bukkit objects. Fixed limits are 256 active player lanes,
64 admitted entries per player and 4096 total entries. Rejected capacity does not run a
supplier. Different player lanes progress independently. No supplier or completion
callback is invoked while holding the queue monitor. Synchronous success/failure,
reentrant submit/retire and malformed stages are handled without recursive map mutation.

A failed entry rejects its queued dependents. Logout retires the player's lane and
rejects work not yet entered; already entered domain work keeps its acknowledgement.
Its profile/WAL transaction is never blindly cancelled or replayed. The drained lane
can accept later independent activity. The public CompletionStage cannot cancel the
underlying domain write through a caller's future copy. Conditional mirror removal
prevents an older failure/retirement callback from clearing a newer speculative mirror.
The manager uses 64 fixed lock stripes instead of retaining a per-player lock map.

Core shutdown closes quest admission before native shutdown preparation. The returned
barrier is continuation-based and does not block an owner. Already entered profile
writes remain owned/drained by the canonical profile authority. The queue does not
pretend a held write completed. Closing rejects every unentered entry, refuses new work,
and acknowledges drain only once all entered work has returned. No timeout fabricates
success or deletes a durable operation.

Logout can discard an unentered auto-completion after its objective write committed.
Reconnect reward recovery therefore re-assesses ready AUTO quests through the existing
source policy and guarded canonical readiness/completion path. It does not manufacture
a receipt, bypass quarantine, or auto-complete a quest requiring explicit turn-in.

## Verification

Full Java 21 main/regression compilation passes with three inherited warnings.
All 14 selected queue, quest, profile, integrity, Folia ownership, bootstrap, onboarding,
Prologue and lifecycle suites pass. New `questMutationQueueRegressionTest` is a normal
Gradle check dependency and passes 1083 assertions, including 30 rounds of actual
four-thread admission contention. Tests hold native acknowledgements, fill every cap,
reject queued dependent work, retire from a completion callback, close with two active
players, cancel a returned future copy, and drain a full synchronous 64-entry lane.

The real YAML-backed quest admission suite now passes 169 assertions. Its new retirement
case commits an already entered objective after logout, verifies it after reload,
rejects an unentered completion, then closes with a real completion WAL in flight.
That entered completion retains exactly one pending entitlement; post-shutdown work
cannot change disk bytes. These tests supplement the previous 154-assertion ingress,
readiness, external-drift and manifest-boundary evidence.

Four architecture checks, authored PvE audit, coverage, consistency (0 FAIL/WARN), and
profile authority guard/self-test pass. The guard reports 679 findings with no unknown,
stale, invalid or transition findings. The package/source inventory was updated to
1236 main Java files and 11 quest-package files. The initial package-count drift finding
was fixed. Coverage stays at 323 authorities / 55 domains / 51 implementation blockers.

Exact parent CI is recorded in `WW-04-quest-rewards.md`: quest admission and native
Paper/Folia/artifact probes passed; verification failed only the inherited Trash source
PNG audit. Fresh CI for this queue checkpoint is required. Native clean boot/stop does
not demonstrate populated player queues, disconnect/reconnect or inventory delivery.
Those connected-player gates remain required, alongside the already tracked canonical
item hand-in/outbox, source-lineage and other domain integrations. No full phase,
zero-leak, merge-ready or production-ready verdict is asserted.

## Exact queue checkpoint CI

Head `23abf8680e912f9616b14f683294ba29595a8c8a`, tree
`9e9ebfc3b65fe1906ca532ef6fd568115c1e0636`, run
[34138819267](https://github.com/MilCsik09/IceSMP/actions/runs/34138819267):
native Paper 101795915481 / Folia 101795915467 and artifact Paper 101795915625 /
Folia 101795915246 passed. Verification 101795915527 passed queue 1083 and quest
169 assertions; only inherited `trashSpriteAssetAudit` failed. Resource pack
34138819282 and Trash hardening 34138819308 passed. Native probe scope remains
PvE/artifact/offline-profile settlement, not populated player quest sessions.
