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


## Durable derived-effect admission checkpoint

Base `ae57ca912b23c782e467d67ae6b321d7114cb6c8`, WW-04 #158. The neutral
`GameplayEffectGate` owns no influence, reward or receipt state. It returns a
single-use permit for the native target owner. Closed, retired, failed or replaced
bindings cannot admit an effect. The original Weaver journal owns the durable
propagation transition; the native MobAbilityRuntime remains the gameplay authority.

The immutable index exposes bounded source-origin tracing across entity/player,
item, event, world and spatial evidence. PREPARED source intent means uncertainty
and refuses derived mutation rather than inventing applied history. For applied
SANDBOX origins the journal adds target lineage before acknowledging a permit.
Root/target identity is deterministic, fractional locations normalize to blocks,
transitive propagation keeps the original developer origin, and repeats extend
quarantine without duplicating evidence. Canonical operations, receipts and effect
deltas are unchanged. Existing journal capacity reservations and fsync publication
remain mandatory; ambiguous writes close admission and retain conservative evidence.

Normal clean effects use a synchronous immutable policy read without a journal
write/queue. Final owner admission rechecks source uncertainty, newly introduced
origins, target evidence, journal lifecycle and policy binding. A permit can be used
once within five seconds. Its deadline starts before storage acknowledgement, so
slow fsync cannot consume the required five-minute player tail before mutation.
The regression advances an injected clock across that boundary without sleeping.

Native direct/area/composite damage and knockback, poison/composite potion delivery
and ally buff paths now pass through this route. Sources and target location are
captured on their owners; continuations retain UUIDs, immutable positions and typed
native parameters, then resolve the target again. Player fanout is capped at 32;
ally fanout remains six. Monotonic entity/item/event targets preserve taint across
arbitrarily long effects. The CI native probe additionally selects an actual
registry-backed ALLY_BUFF template and verifies the clean effect traverses the gate
and reaches another mob before canonical cleanup.

**Active lifetime gate:** SANDBOX lingering effects on non-monotonic targets
(player/world/location) currently refuse. An effect's nominal tick duration is not
proof of actual end under lag, logout or unload. A durable observed-lifetime
consumer is required before those effects can be admitted; a wall-clock-only expiry
was explicitly rejected during the audit. Clean effects remain available, with a
final refusal if their source becomes tainted. This is not full potion/DOT closure.
Projectile/summon creation, volatile/affix effects, item/event propagation and all
remaining non-kill producer/outbox gates remain active work. Gameplay manipulation
and physical issuance stay disabled.

Local evidence: full Java 21 main/regression compile and 38 selected Weaver/DEV/native
suites passed. After the slow-fsync deadline correction, five affected suites were
rerun; the new normal Gradle propagation task passes 188 assertions. Evidence includes
all 26 reward channels on six derived target kinds, transitive origin, immutable
canonical receipt history, single-use/lifecycle admission, a held fsync publication
fence, before/after write failure and real YAML restart. The native ally probe needs
its own new-head CI. Inventory: 319 authorities / 55 domains / 51 blockers.

Prior `ae57ca912b23c782e467d67ae6b321d7114cb6c8` CI run 34119895633:
Paper native 101735307357, Folia native 101735307132 and both artifact lifecycle
jobs 101735306862 / 101735307048 passed. Verification 101735307108 found a stale
`consumedThresholds` source-token assertion after history extraction, in addition to
the inherited Trash source-sheet audit failure. The authored PvE audit now follows
both native `history.consumed`/`history.consume` calls and the bounded once-only set
implementation; its substantive threshold/pause/cast-epoch gate is preserved and the
audit passes locally. Resource pack 34119895538 and Trash 34119895617 passed.
