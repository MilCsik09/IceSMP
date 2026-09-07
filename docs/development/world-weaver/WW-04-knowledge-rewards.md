# WW-04 knowledge reward admission

Internal developer checkpoint. Branch `feature/world-weaver-ww04-pve-faction-integrity`,
PR #158, stacked on #157. Base `9f3854effbbd74eef6bc3bfb082613389f818a54`.
Neither physical issuance nor durable gameplay manipulation is enabled.

## Native authority and resolved findings

`PlayerProfileAchievementStore` now carries channel/recipient-checked immutable reward
context into the existing generation-fenced reward CAS for achievement unlock,
Bestiary entry, hidden-spot discovery and new pending reward reservation. Kill
provenance reaches this final admission through `BestiaryListener`; recipe progression
keeps the captured craft context. Default compatibility routes retain recipient checks.
Current player/world/location context reaches achievement evaluation and hidden-spot
entry. Expected quarantine refusal does not emit a storage-error diagnostic.

Bestiary previously committed its new entry before reserving the corresponding
milestone, allowing a crash between those writes to lose the reward. The canonical
store's `recordBestiaryWithRewards` now commits both in one section/WAL generation.
Up to 128 native configured rules are captured on the player owner. The current-count
milestone identity is checked against its canonical category/threshold. The existing
512-entry pending ledger limit remains active. A repeated entry can recover an existing
pending payload without changing its amount/currency after faction/config changes.
The store still emits no forged event or natural history entry.

Bestiary also called blocking wallet durability on the player's owner thread. It now
uses `AchievementManager.settlePendingReward`, the same path used by reconnect recovery.
The existing store validates the exact real pending payload before invoking delivery,
then waits for canonical delivery acknowledgement before settlement. Missing or changed
pending payload cannot authorize a reward. Wallet work runs asynchronously; class XP
resolves the player UUID on its entity scheduler. Retired/unavailable players leave XP
pending. At most 128 distinct deliveries are in flight; duplicate in-flight calls do
not launch another delivery. No second wallet, reward receipt or history authority exists.

Canonical wallet operation IDs are unchanged. Already committed wallet credit replays
without minting another balance increment, then settles its original achievement
outbox. Existing pending rewards are retained under later quarantine. A pending class-XP
payout may remain paused by the class reward gate; it is not erased or falsely settled.

Native custom advancement grants now capture source context on the caller's owner,
resolve UUID again on the final owner and check both original eligibility and current
spatial/world sources immediately before `awardCriteria`. No fake Bukkit event is used.
Hidden-spot post-profile callbacks retain UUID plus immutable reward configuration,
instead of a live Player/configuration section across that IO continuation.

## Verification

Full Java 21 main/regression compilation passes with three inherited warnings.
All 39 selected PlayerProfile, profession, knowledge, Weaver reward/ownership, native
PvE and Prologue/lifecycle suites pass. The new normal Gradle check dependency
`weaverKnowledgeRewardRegressionTest` passes 133 assertions with the production
achievement store, profile YAML/WAL and economy receipt ledger:

- queued entity/player/item/event/world/spatial and recipient quarantine;
- unchanged durable state/revision/receipts on refusal and unbound-policy denial;
- recipient/channel/milestone identity and 128-rule bounds;
- atomic Bestiary entry plus pending milestone on both sides of the WAL manifest;
- preserved pending amount/currency under later configuration/faction/quarantine;
- held/failed/refused delivery leaving the outbox untouched;
- actual economy credit and exact receipt replay after lost wallet acknowledgement;
- restart recovery without duplicate currency or false pending settlement.

Native call-site contracts additionally guard the actual shared delivery, owner UUID
resolution, async wallet call and retained kill context. Those source checks do not
establish connected-player behavior or runtime capacity/race evidence.

Four architecture checks, authored PvE audit, coverage, consistency (0 FAIL/WARN) and
profile authority guard/self-test pass. The guard reports 678 findings and zero unknown,
stale, invalid or transition findings. Five changed authorities were reviewed and
rehashed. Inventory remains 323 authorities / 55 domains / 51 blockers / 1235 main files.

## Native CI evidence gate

The existing isolated Paper/Folia native probe now also creates a fixture offline
profile through the canonical repository, admits a real Bestiary milestone and invokes
the actual achievement manager from the global owner. It verifies native wallet credit,
settlement and no-op repeat, then performs its existing chunk/entity cleanup and shutdown.
The workflow requires the additional `ICESMP_KNOWLEDGE_REWARD_RUNTIME_PROBE_PASS` marker
with explicit `offline_profile_currency_settlement` scope. No connected player,
developer authority or gameplay-enable bypass is synthesized.

Exact head `727d09d4521a7b29dc28e48d7967cf1e0c47442c`, tree
`5704b18071076954a225a031af20a1cc6826f04e`, run
[34135423917](https://github.com/MilCsik09/IceSMP/actions/runs/34135423917):
Paper native job 101785129189 and Folia native job 101785129090 both passed.
Their decoded logs contain the required offline-profile settlement marker, native PvE
proof and clean shutdown. Artifact jobs 101785128874 / 101785129104 also passed.
Verification 101785129066 failed only `trashSpriteAssetAudit` on the inherited damaged
source PNG; the new knowledge regression passed. Resource pack 34135423931 and Trash
34135423928 passed. This evidence is restricted to the stated isolated native fixture;
it does not establish connected-player or full knowledge/provider acceptance.

Exact preceding base run 34133008127: native Paper 101777386387 / Folia
101777386469 and artifact 101777386402 / 101777386463 passed. Verification
101777386069 passed the 74-assertion profession reward suite and failed only inherited
Trash source PNG validation. Resource pack 34133008070 and Trash 34133008044 passed.
Those base results do not prove the new native knowledge extension.

## Active remaining work

Connected-player Bestiary, achievement/class-XP, discovery and advancement behavior,
logout/disable/capacity races and tainted native activity still need Paper/Folia evidence.
Full item/prototype lineage, source-aware metric producers and hidden-spot first-finder
aggregate/item delivery recovery remain open domain integrations. This is not full
Knowledge provider coverage, all reward producers, WW-04 completion, zero-leak or
production acceptance. Coverage blockers and gameplay enable gates remain unchanged.
