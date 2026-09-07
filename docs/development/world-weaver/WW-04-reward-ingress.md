# WW-04 — reward ingress integration

Internal developer evidence. Based on projection checkpoint
`0e29a105ceea19e7ecb71ec0efd3b923f3ee8646`. This is an integration checkpoint,
not a reward-leak closure or permission to enable gameplay manipulation.

## Neutral boundary

`GameplayRewardGate` owns no reward, progression, influence or receipt state. Core
binds the existing `InfluenceRewardEligibilityPolicy` to `WeaverInfluenceLookup`.
Unbound, unavailable, failed and retired policy bindings deny admission. Binding
handles support disable/re-enable without letting an old shutdown clear a newer
binding. A policy evaluation racing shutdown cannot return a stale allow.

`RewardSourceContext` represents unclaimed world output without inventing a player
UUID. Policies must explicitly implement source-only checks; the default denies.
Composite policies retain deny-wins behavior for both recipient and source checks.

`BukkitRewardSources` captures the victim's world/location on its owning region.
Only immutable UUID identities are taken from killer/causal handles; no foreign
mutable entity state is inspected. Death provenance includes victim, direct and
causing entity, killer, world and location. Delayed damage, summons, items, event
fields and other derived effects still need the durable propagation routes listed
below; the last damage source is not a substitute for complete causal history.

`MobKillUtil.KillContext` retains this immutable source context. Existing Survival,
AFK, spawner, minion and authored reward-owner policies remain active. Admission,
claim and owner-hop execution re-evaluate influence; an allow decision is not cached
across the killer continuation. The callback resolves its player on that owner
instead of carrying the original live Player into the callback.

## Current call-site integration

| Surface | Ingress / output route now gated | Remaining evidence / work |
| --- | --- | --- |
| Vanilla mob drops and death XP | `VanillaRewardIntegrityListener` LOWEST and HIGHEST; explicit EntityDeath and PlayerDeath handlers | Native tainted/clean drop and XP runtime probe; acquisition/derived-source propagation |
| Player inventory on death | Not treated as newly minted mob loot; only death XP uses this output gate | Prototype/tainted item lifecycle is a separate item integration |
| Warden configured XP | `WorldTweaksListener.onWardenDeath` source gate before generation | Native output evidence |
| Generic/custom mob loot | `MobLootListener.onEntityDeath` source gate even when require-player-kill is disabled | Personalization/queued delivery settlement context; event-specific reward managers |
| Dungeon boss loot | `DungeonLootListener` passes eligibility into the existing boss lifecycle's reward flag | Lifecycle still completes when reward denied; native evidence required |
| Dungeon bonus loot | Existing `MobKillUtil.eligibleKill` now includes influence | Downstream item acquisition provenance |
| Trash mob flavor drop | Existing FLAVOR context + `claimOnce` now include influence | Canonical Trash item provenance and later transformations |
| Class XP kill ingress | `ClassXpListener` CLASS_XP check on killer owner, same source context | Carry context through asynchronous profile mutation/reservation; non-kill XP sources |
| Pet XP kill ingress | `PetXpListener` PET_XP check on owner; credited companion identity unchanged | Companion source propagation; profile commit/reservation context and capture drops |
| Bestiary kill ingress | `BestiaryListener` BESTIARY check on owner | Canonical record/milestone reservation and legitimate pending payout handling |
| Mob tracking ingress | `StatsCombatListener` uses the now-gated tracking KillContext | Profile counter settlement; non-mob and other metric origins |
| Quest kill ingress | `QuestProgressListener` QUEST_PROGRESS check on owner | Context through quest/profile mutation and reward outbox; non-kill objectives |
| Community kill contribution ingress | Explicit COMMUNITY_GOAL check before each contribution | Durable contribution/completion/outbox context and non-kill sources |
| Mob money | `MobMoneyDropListener` shared kill gate and CURRENCY_FAUCET owner check | Token acquisition and other currency faucets; legitimate transfers/refunds remain distinct |
| Soulstone drop | Shared kill gate plus CURRENCY_FAUCET before budget/drop | Item token provenance and downstream conversion |
| Party vanilla XP sharing | Root kill gate plus each beneficiary's VANILLA_XP check on its owner; fallback also gated | Native cross-region party evidence; personal loot is separate |

The generic policy suite's 26-channel checks prove policy behavior, not that all
26 channels' native producers have been integrated. The authoritative remaining
producer list is the WW-00 reward/progression ingress and settlement audit.

## Remaining closure work

- Carry immutable provenance into canonical asynchronous reward/progression
  admission and persist legitimate eligibility with each domain's existing receipt.
  Preserve valid preexisting outboxes; no receipt spoofing or blanket deletion.
- Gate every non-kill quest, profession/gathering, achievement/discovery,
  community/weekly/server challenge, personal loot, crate, currency faucet,
  parkour, raid/war/event and developer-item reward path.
- Propagate durable influence before projectile/summon/DOT/potion/item/event/spatial
  child effects. Direct death provenance alone does not close delayed or indirect
  gameplay influence.
- Item acquisition, transformation, container, trade, consumption and prototype
  escape restrictions must retain lineage and respect canonical item authority.
- Real Paper/Folia populated reward and failure-injection probes, plus client
  playtest evidence. Both physical artifact issuance and durable gameplay admission
  remain disabled until integrity coverage is complete.

## Verification

The installed gateway regression checks exclusive binding, source-only world rewards,
all 26 channels against victim/causal/player/world/spatial evidence, late continuation
quarantine, lookup linkage failure, disable/re-enable and shutdown races. Existing
Weaver integrity, PvE, DEV, AFK and Trash catalog regressions are retained. Full
Java 21 main/regression compilation passed against 49 real dependencies, with zero
errors and three inherited warnings. All 33 relevant suites passed locally, along
with four architecture checks, the PlayerProfile authority guard/self-test (656
findings, zero unknown/stale/invalid/transition), consistency (zero FAIL/WARN), and
the authored PvE consolidation audit (zero shadow combat authorities). Inventory:
300 authorities, 55 domains, 51 blockers, zero audit errors. Exact-head CI is still
required; no complete reward-integration verdict follows from these local tests.

Preceding projection head remote evidence: build `34080004666`, Paper
`101613374581` and Folia `101613374427` succeeded. Verification `101613374585`
compiled main/regression sources and passed both the real provider projection and
bounded dispatcher suites; its only failed task was inherited `trashSpriteAssetAudit`.
Resource pack `34080004725`, Trash hardening `34080004732` and docs inventory
`34080004637` succeeded. These are clean-store readiness/shutdown checks, not
populated reward or client gameplay proof.
