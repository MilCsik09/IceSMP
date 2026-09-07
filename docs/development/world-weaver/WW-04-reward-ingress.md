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
| Class XP | `ClassXpListener` → `JobManager` → typed gateway request → real YAML reward CAS, with full kill source context | Non-kill upstream provenance and native execution evidence |
| Pet XP | Exact companion plus kill provenance through `PetManager` and the serialized profile reward CAS | Durable companion influence propagation and native evidence |
| Pet ritual/equipment drops | Immutable serialized item and reward context across the region continuation; recheck before drop, no unloaded chunk force load | Native cross-region output evidence |
| Bingulus rewards | Pause timer before progress/roll; recheck before delivery claim and after storage continuation; preserve pending item/pity/progress on refusal | Native pause/resume and pending-delivery playtest |
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
  parkour and raid/war/event reward path. Bingulus entry/delivery is now gated; its native evidence remains required.
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

## Profile admission checkpoint

Based on `920fb92151a4ae347ec5bfc64e46f41dcdc4d49c`. Class/pet reward requests now
carry immutable `RewardContext` through both the gateway queue and the storage queue.
The existing `YamlPlayerProfileRepository` checks it under its canonical per-profile
lock after revision validation and immediately before `commitSingle` writes the WAL.
This is the reward admission point. A quarantine published after admission does not
retroactively revoke the accepted canonical WAL/receipt. A quarantine visible before
admission rejects the candidate without changing XP, receipts, revisions or session
health. There is no secondary reward receipt store or authority.

The guarded storage interfaces fail closed by default; an implementation lacking
serialized reward admission cannot silently delegate to an ordinary save. Recipient
and channel must match the class/pet mutation. Compatibility ADD/delta calls retain
recipient quarantine; owner-side manager defaults also capture world/location.
Kill callers pass the complete detached victim/causal source context. Canonical
explicit SET remains a domain mutation; reward-generated clamped SET still carries
its reward context. No new developer command or alternate mutation authority exists.

Existing parameter-bound operation receipts are checked before attempting another
save. Accepted receipts replay under later quarantine without rewriting the reward.
Profile format, operation identifiers and receipt identity remain unchanged.
The admission context is transient immutable input; accepted outcome durability is
owned by the existing WAL and operation receipt, not by a fabricated eligibility token.

`WeaverProfileRewardAdmissionRegressionSuite` uses the real gateway, both production
adapters and YAML repository with an injected executor and existing WAL fault hook.
51 assertions cover causal and recipient quarantine while queued, exact companion XP,
unchanged disk bytes and revision/receipt on refusal, session usability, wrong
recipient/channel, compatibility ADD, unbound/unsupported policy, restart replay,
parameter mismatch and lost acknowledgement after the accepted manifest write.
It is a normal Gradle check dependency. All 67 relevant Weaver, artifact, PvE,
PlayerProfile, class/spec, AFK, Trash and Evoker suites passed; full Java 21 compile
passed against 49 real dependencies (three inherited warnings). Four architecture
checks, authority guard/self-test (658 findings, zero unknown/stale/invalid/transition)
and consistency (zero FAIL/WARN) passed. This evidence does not establish all-domain
reward leak closure or native populated server behavior.

The coverage audit now tracks five canonical repository/adapter ports explicitly
under profile infrastructure and the existing Bingulus behavior under developer self:
306 authorities, 55 domains, 51 implementation blockers. Persistence ports are not
raw developer editors; their gameplay surfaces remain owned by the corresponding
class/pet and later domain providers. Source changes were reviewed and rehashed.

Exact preceding head CI `920fb92151a4ae347ec5bfc64e46f41dcdc4d49c`: verification run
`34081527215`; Paper `101617584458` and Folia `101617584580` PASS; verification job
`101617584635` compiled and passed the reward gateway suite, with only inherited
`trashSpriteAssetAudit` failing. Resource pack `34081527251` and Trash hardening
`34081527275` PASS. CI for this new checkpoint remains required; native populated
reward, causal propagation and client gates remain open.
