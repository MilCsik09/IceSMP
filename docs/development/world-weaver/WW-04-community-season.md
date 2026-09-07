# WW-04 community and personal season admission

Internal developer checkpoint on `feature/world-weaver-ww04-pve-faction-integrity`,
PR #158, stacked on WW-03 #157. Thematic parent:
`c16481c82bdb3fdb11ce86780eb254836ab366d0`. Production admission remains disabled.

## Native mutation routes

CommunityGoalManager now admits immutable COMMUNITY_GOAL provenance under its existing
contribution monitor before changing counters, source receipts or completion outbox.
Both ordinary and once-only APIs check the owner's current context plus the original
kill/block/item-entity context. Invalid amounts, unavailable owner/policy, recipient
quarantine and over-cap provenance refuse without changing these structures. The quest
listener forwards its captured sources at all existing community ingress points.

Personal community qualification carries the same combined source set through
SeasonManager into PlayerProfileSeasonParticipationStore's existing FACTION section
mutation. SEASON_CREDIT admission is checked at the generation-fenced reward WAL route,
including retries. Compatibility callers receive recipient admission; the dedicated
context overload preserves causal sources supplied by each native producer. Expected
quarantine refusal does not become a storage-error log. No new progression authority,
receipt identity, raw YAML mutation or Bukkit event is introduced.

The community completion outbox and its real treasury/season grant IDs remain canonical.
An already durable completion continues its existing idempotent payout recovery even
under later contributor quarantine. Best-effort online buffs resolve UUID on owner,
recheck membership and recipient/world/location eligibility, and carry the actual
completion UUID as their native event source. Each effect is admitted separately;
announcement delivery uses the global region scheduler. Buff duration conversion cannot
overflow an int.

## Race corrections

Activity category/day is captured before queuing; last-active never moves backwards
when an older activity arrives later. A previous-season request cannot overwrite an
already recorded newer season. The section's existing daily category receipt remains
bounded to 512 entries and preserves its deduplication behavior.

Loaded participation projections carry the canonical profile revision as read metadata,
without adding it to persisted activity payloads. Both startup and write callbacks merge
by that revision. Equal-generation contradictory snapshots fail closed. Projection
consumers also compare canonical current membership and join identity, so a late old
callback after membership cleanup cannot qualify the retired membership. Accepted
activity survives later quarantine and ordinary reload; new tainted activity is refused.

## Verification and release evidence

The full Java 21 main/regression set compiles. The new normal `check` dependency
`weaverSeasonRewardRegressionTest` passes 67 assertions using the real YAML/profile authority and manually
queued storage to check six source kinds, late quarantine, unchanged disk bytes and
revisions on refusal, channel/recipient identity, native daily deduplication, reload,
accepted activity preservation, membership changes, stale season writes, last-active
ordering and reverse-generation callbacks. Native community/buff source contracts
supplement those executable profile tests.

Four architecture checks, the authored PvE audit, consistency (0 FAIL/WARN), and
profile authority guard/self-test (683 findings, zero unknown/stale/invalid/transition)
pass. Coverage remains 324 authorities, 55 domains and 51 blockers. Existing community and
season authorities were re-audited; no domain status was waived. Exact head `08bf674800bf9dc0964bcd4418cfc76627ba944e`, tree
`abcdfcb727dd429afcd26746ca900427febd14ca`, run `34143563091`: native
Paper `101810614464` / Folia `101810614331` and artifact Paper `101810614891` /
Folia `101810614468` passed. Verification `101810614222` passed the 67-assertion
season suite and failed only inherited `trashSpriteAssetAudit`. Connected-player
community completion, membership-change and buff tests remain required.
The preceding challenge head's Paper/Folia native and artifact jobs passed; its full
verification failed only the inherited damaged Trash source PNG audit.

## Remaining implementation

Other season producers still need their original event/victim/spatial contexts wired
through this new final profile gate; their compatibility routes currently check the
recipient only. Shared season points, raid/war/duel/spy budgets and payouts need their
own canonical source-aware transactions. The existing community store still performs
its synchronized completion persistence through its native API; an asynchronous
canonical mutation route is required before exposing it to WorldWeaver execution.
The full Event provider and native item/damage influence propagation remain open.
These profile/source tests do not establish universal reward integrity, complete
community runtime evidence, zero blockers, merge readiness or production readiness.
