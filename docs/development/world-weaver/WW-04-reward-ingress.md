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

## Observed native effect lifetimes

Continuation base `f1be063fe5664fc7471a94933ab4b22d90ded9f5`. Its exact-head
WorldWeaver run 34122353832 passed Paper native 101743075597, Folia native
101743075550 and artifact jobs 101743075406 / 101743075179. Native evidence now
includes the actual clean ally buff on a second mob. Verification 101743075471
failed only the inherited `phase-batch-001.png` Trash source-image audit. Resource
pack 34122353760 and Trash 34122353799 passed. These are base-commit results;
this continuation requires fresh CI. No native tainted-player playtest is claimed.

The earlier refusal for lingering player potions is now replaced by an explicit
provider-owned observed-lifetime consumer. `GameplayEffectLifetime` is a bounded,
namespaced schema and immutable parameter recipe. The generic provider registry
validates type, provider, facet, capability and supported influence scope; it has
no PvE branch. `PvEInfluenceLifetimeObserver` registers
`icesmp:pve_potion_effect@1`, validating the potion key against `Registry.EFFECT`.
The native poison/composite potion paths supply this recipe. Unregistered lingering
world/spatial effects still refuse; entity/item/event lineage remains monotonic.

Journal schema 4 persists the typed recipe with the derived influence. Schemas
1–3 remain readable; unknown historical provider/content types remain opaque
quarantine evidence. Player influence stays active regardless of wall-clock or
nominal tick duration. The native observer resolves only the UUID on EntityOwner
and reads actual potion presence there. Offline/unavailable players remain pending;
no chunks are forced or native effects replayed. Up to sixteen observations run
sequentially per round-robin maintenance pulse, with a five-second owner/provider
bound and circuit-breaker isolation.

Before reading absence, the owner acquires a neutral target observation fence.
Effect permits cannot cross that fence, including player/entity identity aliases
and policy replacement. The fence survives until the exact-record conditional end
has received durable acknowledgement; shutdown does not release an in-flight write
prematurely. An ended record retains at least five minutes of quarantine, and an
older effect permit cannot use merely that tail to apply a new lingering effect.
Changed influence evidence rejects the end. Provider/owner late completions release
their unconsumed fences, including started timeouts and shutdown. Removing or
compensating a projection does not end its already-applied native potion influence.

Verification: Java 21 full main/regression compile, all 32 Weaver/provider suites,
four native runtime suites, four architecture tests, authored PvE audit and
consistency (0 FAIL / 0 WARN) passed locally. The new normal Gradle check task
`weaverObservedInfluenceRegressionTest` passes 155 assertions: all 26 reward
channels, actual-end/tail boundaries, two distinct lifetime identities, same-effect
deduplication, exact-state conflicts, 128-fence and 16-observation caps, held fsync,
before/after write failures, real YAML restart, schema 3 migration, removed content,
projection expiry/compensation, provider timeout and owner shutdown custody.
Inventory: 322 authorities / 55 domains / 51 blockers; 1232 main Java files.

Remaining release gates include actual tainted-player potion/logout/restart tests
on Paper and Folia, complete causal creation/DOT propagation, every non-kill
producer/outbox route, and cross-region combat continuation. Clean native shield
and ally tests do not establish those gates. Gameplay admission and artifact
issuance remain disabled; this is not complete WW-04 or zero-leak acceptance.

## Native affix owner and propagation closure

Base `5d062170ebd875ce260df3bf3116fa2b3a024378`, WorldWeaver run 34125350157:
Paper native 101752628820, Folia native 101752628712 and artifact lifecycle
101752628848 / 101752628527 passed. Verification 101752628676 ran the new
155-assertion observed-lifetime suite successfully and failed only the inherited
Trash source-image audit. This is exact-base evidence, not connected-player proof.

The affix damage listener previously read the attacker's scaling/PDC profile on
the victim's event thread. It now captures victim provenance there, routes by
attacker UUID, checks ownership before reading native affixes, then carries only
immutable causal evidence into target continuations. Frostbound uses the registered
observed potion lifetime. Vampiric healing passes through the derived-effect gate
with both victim and attacker origins, preserving taint from either side.

Volatile retains immutable origin identities and coordinates after the mob dies.
Its delayed region callback re-resolves the world and checks owner plus loaded
chunk, then routes at most 32 nearby player identities through durable effect
admission. Each final target owner rechecks survival and distance. No live Location
or Player is retained by the delayed/target stages, and no chunk is force-loaded.

Full Java 21 compile, eight affected native/provider/integrity/ownership suites,
four architecture checks, coverage and consistency passed locally. The authored
PvE audit was incorrectly summarized as passing here: exact-head CI found its stale
`player.getScheduler().run` source token. The following continuation fixes and reruns
that audit against both real owner hops and final permit admission.
The native source-contract suite at this checkpoint has 26 assertions and follows the actual
owner-resolving effect helper; these static guards are not native player execution
evidence. Frostbound/Vampiric/Volatile cross-region tainted-player playtests remain
required. Projectile/summon creation, the broader damage/progression ingress audit
and all subsequent provider phases remain open. Gameplay admission stays closed.

## Canonical source lineage and native creation admission

Base `b2c920c5174003a17b587ff5ec0f223adda89ad4`, WorldWeaver run 34125919264:
Paper native 101754435876 / Folia native 101754436120 and artifact lifecycle
101754435976 / 101754436018 passed. Verification 101754435553 failed the stale
owner-hop source token in `authoredPveConsolidationAudit` and the inherited Trash
source-image audit. Resource pack 34125919238 and Trash 34125919380 passed.
The stale audit now checks both actual entity scheduler hops, owner resolution and
final one-use permit; it passes without weakening the foreign-access prohibition.

A native spawn assigns its entity UUID during creation, so the implementation first
makes its already-known parent entity's influence durable and monotonic. Only the
acknowledged permit can enter native creation, after an owner UUID hop and cast-epoch,
pause, lifecycle and final source checks. Burst arrows and both summon paths use this
route. Clean creation still requires no journal write. Child origins reuse native
projectile `getOwnerUniqueId()` and the two existing canonical summon-owner fields;
no fabricated event, reward receipt, new UUID identity or second gameplay authority
is introduced. Every further generation first makes its own known parent monotonic,
so expired world/player/spatial origins cannot wash descendants. This is conservative:
later taint on the same recorded parent also quarantines its remaining descendants.

`WeaverCausalSourceProvider` is the minimal owner-read extension required to consume
those canonical metadata APIs. The generic registry selects immutable source kinds,
not subsystem IDs; callbacks receive only UUID/kind and re-resolve on the owner.
The PvE adapter reads native `summonOrigin` APIs. Vanilla projectile owner UUIDs are
captured directly on the projectile owner. Provider contributions are bounded to 16,
the combined result to 32, and the neutral capture binding fails closed on unavailable,
retired, invalid or broken capture. The provider circuit breaker isolates relevant
source kinds; unrelated player source capture still works when a mob consumer fails.
Malformed native provenance is a per-source domain refusal, not an implicit clean
result. This extension changes no artifact or GUI implementation.

Authored spawn source/encounter/reward/parent identity is now stamped by its existing
canonical service's pre-activation spawn consumer. Legacy skeleton summon parent
identity is likewise present before activation. Transient summons become nonpersistent
there, avoiding a restart orphan if the lifespan callback is lost. Arrows retain the
native pickup prohibition. Spawn points must already be loaded and owned; foreign
spawn placement currently refuses. Death capture expands nonplayer causes only on
their owner; unavailable foreign causal state refuses reward capture instead of being
read or treated as clean. Full cross-region continuation remains an explicit scope gate.

Java 21 full compilation and all 33 Weaver/provider plus four native suites passed
locally. Five affected suites were rerun after final source/probe changes: causal source
50 assertions, observed lifetime 155, derived influence 188, native source contract
28 and provider controls 104. The causal suite covers a held fsync before creation,
all 26 reward channels after world expiry, descendants, real YAML restart without a
live parent, unchanged receipts/history, provider cap/error isolation and binding
retirement. Four architecture checks, authored PvE audit, profile authority guard
(675 findings; zero unknown/stale/invalid/transition), coverage and consistency pass.
Inventory: 323 authorities / 55 domains / 51 blockers; 1235 main Java files.

The native probe now additionally checks canonical/provider summon origin and forces
a registry-selected actual SUMMON technique through the clean creation gate. It checks
nonpersistent children, inherited native parent identity and cleanup. Fixture placement
uses the center of its existing pinned chunk; this is test setup, not runtime force
loading. Fresh exact-head Paper/Folia evidence is required before claiming that probe.
Full tainted projectile-impact/player quarantine, cross-region creation/impact,
all reward producer/outbox paths, and later WW phases remain unfinished. Neither this
checkpoint nor its fixtures justify zero-leak, full-domain or production acceptance.

## Final owner source revalidation and native summon fixture

Base `8aed92cc45ca6ad772b57527aa30b9d82a50295c`, run 34128193351:
artifact Paper 101761744797 / Folia 101761744992 passed. Both native jobs
101761744820 / 101761744687 failed at NATIVE_SUMMON_FIXTURE_UNAVAILABLE: no
currently installed kit has the legacy bare SUMMON kind selected by the fixture.
This is not evidence that native summon execution passed. Verification 101761744420
failed only the inherited Trash source-image audit; resource pack 34128193286 and
Trash 34128193339 passed.

The fixture now selects an installed COMPOSITE whose actions are all canonical
SUMMON_TEMPLATE and whose conditions are absent or HEALTH_BELOW. It sets the isolated
fixture mob's native health below that actual threshold before forcing the ability.
Production conditions are not bypassed. Children are inspected through the canonical
authored summon-origin API and both parent/child lifecycle indexes are cleaned.
Exact-head Paper/Folia evidence remains required for the corrected fixture.

A source can move into a different influenced location while waiting for IO or an
owner continuation. Final effect permits now accept a fresh immutable source capture
in addition to the original provenance. The journal rejects fresh PREPARED uncertainty
or any newly observed developer origin that was not durably admitted. Native impact
and creation owners capture current provenance immediately before claiming the permit;
no foreign live read or region-thread IO wait is introduced. Known admitted lineage
still succeeds once. Policy and observation fences remain mandatory.

Full Java 21 compile and seven affected suites passed. Derived influence now has
195 assertions, including movement into an already-tainted source, fresh PREPARED
uncertainty, a new canonical parent origin, unchanged-source single use, bounded
fresh capture and immutable input. Observed lifetime 155, causal capture 50, native
source contract 28, native control 22, provider controls 104 and Folia ownership also
pass. Four architecture checks, authored PvE audit, inventory and consistency are
locally green. Coverage remains 323 authorities / 55 domains / 51 blockers; broader
native damage/player-impact propagation and all remaining phase gates are unchanged.

## Summon retirement ownership cleanup

Base `da2a33df591005d014195f4d52d082a5e207b4fc`, WorldWeaver run 34129174734:
Paper native 101764898646 and Folia native 101764898528 now pass the actual
canonical composite summon fixture, including native health-condition admission,
created children, provider-read parent provenance, nonpersistent lifetime and cleanup.
Artifact jobs 101764898539 / 101764898130 also pass. Verification 101764898588
passes the 195-assertion propagation suite and fails only inherited Trash source PNG
validation. Resource pack 34129174738 / Trash 34129174753 pass. This is isolated
clean native evidence, not full tainted projectile/player or populated-server proof.

Review of that canonical lifecycle found its retired callback read the retired mob's
PDC and a second map retained live Mob handles. `AuthoredCreatureSpawnService` now
keeps only its existing owner-to-child UUID index. Lifespan, refresh and pause hops
carry IDs, resolve on the entity owner before live reads, and forget unavailable or
retired IDs without Bukkit access. Null scheduler admissions also retire the index
entry. Parent cleanup fences queued child pause/resume through current membership.
Atomic per-owner index updates prevent a concurrent empty-set removal from discarding
another child registration. The canonical summon-owner metadata remains the source
of durable provenance; no parallel authority is introduced.

Java 21 full compile, four affected source/causal/owner/provider suites, authored PvE
audit, coverage and consistency pass locally. The native source contract now has 29
assertions, including the absence of live-Mob storage and Bukkit/PDC reads from retired
cleanup. The profile authority guard has 673 findings and zero unknown/stale/invalid/
transition findings. Fresh Paper/Folia evidence is required for this cleanup commit.
Coverage remains 323 authorities / 55 domains / 51 blockers. The same unfinished
native damage propagation, cross-region continuation and later-phase requirements
remain open; no production or complete-phase verdict is made.

## Acknowledged instant-effect admission

Thematic parent `08bf674800bf9dc0964bcd4418cfc76627ba944e`. The journal can now
issue an immediate, single-use permit for a zero-duration effect when every original
origin/target pair already has matching acknowledged derived evidence. This reads the
same immutable journal publication; it adds no cache or alternative authority. The
permit deadline is the minimum of the ordinary five-second owner window and every
finite target's remaining admission window after reserving its full five-minute tail.
It cannot spend that tail on a late effect. Monotonic targets need no tail renewal.

Missing targets, newly introduced origins and exhausted admission windows still use
the existing durable propagation path. Lingering/observed-lifetime effects retain their
observer contract. Final source re-capture, publication/lifecycle checks and neutral
owner observation fences still run for reused permits. Neither operations/receipts nor
influence history are rewritten on successful reuse. This removes redundant IO from
existing native effect consumers; it is not the complete vanilla damage listener.

Java 21 compiles all main/regression sources. Derived propagation passes 214 assertions,
including immediate completion without a write, exact tail deadline, necessary renewal,
partial target coverage, newly introduced origin, observation fence, lingering refusal,
monotonic retention and shutdown. Observed influence 157, causal source 51, influence
persistence 162 and gameplay reward gateway also pass. The additional SEASON_CREDIT
channel participates automatically in the all-channel quarantine checks. Exact new-head
CI remains required. Coverage remains 324 authorities / 55 domains / 51 blockers;
native damage/projectile/player propagation remains active implementation work.
