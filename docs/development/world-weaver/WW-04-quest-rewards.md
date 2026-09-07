# WW-04 quest reward admission

Internal developer checkpoint on `feature/world-weaver-ww04-pve-faction-integrity`,
PR #158, stacked on #157. Parent `727d09d4521a7b29dc28e48d7967cf1e0c47442c`.
Artifact issuance and durable gameplay manipulation remain disabled.

## Canonical routes and resolved findings

`PlayerProfileQuestStore` now admits acceptance, discovery, objective SET/increment
and completion through the existing generation-fenced reward CAS. Immutable context
is channel/recipient checked and retained until the repository's final WAL admission,
including its stale-section/profile-generation retries. Compatibility entrypoints
retain recipient-only admission; they no longer implicitly bypass an unbound policy.
Tracking, abandonment and recovery of already earned entitlements remain native domain
operations. This does not introduce a second quest, receipt or reward authority.

The native manager's objective routes capture current player/world/location context.
Explicit context overloads preserve original mob kill, caught/picked-up entity, block,
bucket/furnace/enchantment, villager, breeding parents and biome destination provenance
from the listener into the profile write. Cross-owner player-kill, breeding and taming
routes retain UUID plus immutable source lists and resolve the player on its owner.
Unavailable foreign sources refuse credit. Biome lookup checks destination ownership.
Inventory item UUID/prototype lineage is not yet supplied by these actor-only routes.

The manager previously used optimistic objective state to queue completion and swallowed
an earlier queue failure. That could authorize a reward after the corresponding progress
write was rejected. Queued dependent work now inherits failure, invalidates its mirror
and performs no later mutation. A placeholder future is installed before subscribing,
so synchronous completion does not recursively mutate ConcurrentHashMap.compute.
Most critically, `completeIfReady` checks the canonical objective counts in the same
CAS that removes the active quest and creates its entitlement. It accepts an immutable
map of 1..512 positive requirements. An intervening canonical reset is re-assessed on
CAS retry and returns an uncommitted result; it cannot use stale GUI readiness.
Only the existing source-authorized ADMIN turn-in uses the trusted completion override,
and that route still passes reward admission. This is not a WorldWeaver admin permission.

New completion and reward-settlement callbacks carry UUIDs across profile IO, then
resolve owned online players. Retired/null schedulers finish their continuation while
leaving any undelivered canonical receipt recoverable. UI failures do not erase the
receipt. Class XP now inspects the native result: rejected/deferred XP cannot silently
settle a quest reward. Canonical operation IDs and physical component witnesses remain
unchanged. Later quarantine neither removes a pending entitlement nor rewrites history.

## Verification

Full Java 21 main/regression compilation passes with three inherited warnings.
All 42 selected profile, Weaver integrity/ownership, profession, knowledge, native PvE,
Prologue/lifecycle and quest suites pass. The new normal Gradle check dependency
`weaverQuestRewardRegressionTest` passes 154 assertions using the real profile YAML/WAL:

- queued entity/player/item/event/world/spatial and recipient admission refusal;
- unchanged disk bytes, revisions, cooldowns and receipts after rejection;
- recipient/channel and completion requirement validation/caps;
- failed objective ingress cannot generate optimistic completion;
- all durable objectives are required, including after a concurrent canonical reset;
- completion plus entitlement share one WAL generation;
- both sides of the manifest crash boundary retain their native rollback/commit meaning;
- accepted pending physical components survive reload/later quarantine;
- PREPARED components block settlement, unknown receipts refuse, settled replay is inert.

Existing quest-store 424, framework-v2 462 and quest/item-integrity 344 assertions pass.
The framework's old exact call-text check was updated to require the context-carrying
native traversal; its specialization/source-policy checks remain active. Standalone
profile fixtures explicitly bind a neutral policy, rather than changing fail-closed
production defaults. Source contracts supplement the real storage tests; they do not
prove native connected-player queue, inventory or cross-region behavior.

Four architecture checks, authored PvE audit, coverage, consistency (0 FAIL/WARN), and
profile authority guard/self-test pass. The guard reports 680 findings with zero unknown,
stale, invalid or transition findings. Inventory remains 323 authorities, 55 domains,
51 implementation blockers and 1235 main Java files. Fresh exact-head CI is required.

The parent native knowledge evidence is recorded in `WW-04-knowledge-rewards.md`:
Paper/Folia offline-profile settlement and artifact probes passed on exact parent
727d09d. Parent verification failed only inherited Trash source PNG validation. Those
results do not prove this new quest change or connected-player quest behavior.

## Active integration gates

This checkpoint closes quest profile admission and durable readiness, not WW-04 or the
Knowledge provider. Remaining work includes full item/prototype input lineage, original
raid/parkour/event-instance context, final community/statistic producer admission, native
connected-player/cross-region/logout/disable queue evidence, and bounded native queue
pressure/recovery proof. The existing NPC item hand-in removes items before its async
progress commit; it needs the canonical item transaction/escrow integration before that
route is production-ready. Quest physical delivery still requires immutable pending
reward payloads and item owner/restart hardening. The native cleanse-sins/spell/spec,
chain/stats/guild follow-ups need their domain receipt/transaction closure. These are
active scope obligations, not exceptions to the final zero-leak or Folia requirements.

No FULL_PROVIDER, zero-leak, merge-ready or production verdict is claimed.
