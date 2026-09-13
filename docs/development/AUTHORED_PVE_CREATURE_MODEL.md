# Authored PvE Creature Model

## Authority boundary

World and encounter managers own timing, safe placement, roster selection, wave/narrative flow,
contribution, settlement and event HUD state. They provide an immutable spawn context to
`AuthoredCreatureSpawnService`. The spawned entity then receives its `MobTemplate` (or the
explicit generic event profile), level, rank, stat projection, reward owner and `MobAbilityRuntime`
attachment from the same authority used by ordinary creatures.

`WorldBossManager` no longer selects attacks, runs a combat phase scheduler, mutates combat base
attributes or executes slam/zone/summon damage. `InvasionManager` no longer runs a champion slam
scheduler. `PrologueEncounterEngine` still sequences breaches, pause/resume, timeout and finale
completion, but no longer owns elite stat multipliers or finale boss slam/add/hazard combat.

## Spawn context and stat provenance

An authored request contains source ID, encounter ID, role, template or explicit generic species,
level, rank/archetype where applicable, reward owner, transient policy, lifespan, participant
modifier and optional summon owner. Template requests reject entity/rank/archetype shadow
overrides. Generic requests require all three explicitly. Invalid requests fail before a fallback
mob can be published.

Stats have one order:

1. vanilla entity baseline;
2. `MobTemplate.StatProfile` where authored;
3. common level curve;
4. canonical rank multiplier;
5. one optional encounter participant modifier.

The last layer is written to `icesmp:encounter_stat_modifier`; a second application throws. World
boss and Prologue participant scaling use this layer. Blood Moon and Season Finale remain bounded
world/event level or chance modifiers and do not become a second combat engine.

## Technique and phase model

Legacy reusable ability kinds remain compatible. Migrated content adds only two action primitives:

- `APPLY_EFFECT`: bounded self or nearby-player potion effect;
- `SUMMON_TEMPLATE`: bounded canonical template add with owner, lifespan and cleanup.

`HEALTH_THRESHOLD` is the only additional boss trigger. Each ability ID is consumed once per entity
runtime, queued deterministically when multiple thresholds are crossed by one hit, and executed
through the normal telegraph, cooldown, recovery, interrupt and cast-epoch path. Boss-specific
threshold techniques and `prologue_call_adds` are the concrete uses; the old global `boss_enrage`
and `boss_slam` identities were retired. There is no variable language, phase graph or encounter
DSL. Timer techniques use the common contextual selector, so distance, health, cooldown and repeat
penalty influence the next fair, bounded choice instead of a fixed rotation.

## World boss authority

The ten current world bosses keep migration-safe template IDs while their shipped identities are
Körzáró, Salakkohó Szíve, Kallan Elárvult Trónja, Koronátlan Csontúr, A Visszanéző Csend,
Selyemanya Vezhra, Orkánénekes Rael, Rothadás Hordozója, A Jelszó Nélküli and Varkhaz, a
Kaputörő. The Java roster contains only template selection identity and the existing event reward
multiplier; authored display/bestiary identity comes from the template. Each boss has a distinct
positioning problem, exact kit, threshold escalation, presentation language, resistance/weakness
pair and counterplay rather than sharing a generic slam/enrage package.

Major counterplay remains readable: slam telegraphs invite disengage/dodge; zones require moving
out; projectile bursts reward lateral movement; summon casts can be interrupted and make adds a
priority; enrage is a visible threshold escalation.

## Invasion and Prologue

Invasion waves resolve one of eight authored frontline/ranged/control compositions. Every horde
champion resolves one of eight CHAMPION templates whose kit supports that composition. Horde
choice, count, safe placement, announcements and timeout remain invasion orchestration.

Prologue has canonical templates for all five breach species, the ELITE Hasadékbajnok, the finale
boss and three add identities. Pause increments the runtime cast epoch, blocks new casts, cancels
stale delayed execution and pauses owned adds; resume reopens the same runtime. Narrative gates,
wave completion, failure and finale callbacks remain Prologue responsibilities.

## Summon and transient lifecycle

Template summons are capped globally and per action at three. Adds have a finite lifetime, an owner
link, `RewardOwner.NONE`, canonical level/rank/profile attachment and no recursive summon kit.
Owner death/detach, encounter abort, timeout and plugin shutdown remove owned adds. Pause/resume is
propagated through each add's entity scheduler.

## Reward ownership

Combat capability does not imply reward eligibility. `RewardOwner.GENERIC` permits the existing
rank/boss-band item listener. `EVENT` excludes that listener because the event settlement owns the
reward. `NONE` is used by Prologue creatures and summoned adds. World bosses deliberately retain
the existing split: the generic boss-band item roll is distinct from contribution-gated component,
treasury, season and buff settlement. Prologue finale rewards remain in the Prologue reward service;
Dungeon miniboss rewards remain dungeon-owned.

## Folia and performance

The caller reaches the spawn service on the spawn location's region scheduler. Entity mutation and
ability attachment happen on that owner thread. Nearby-player damage/effects hop to each player's
scheduler. Summon pause/cleanup hops to each add scheduler. No global event-mob scan, per-cast YAML
parse, filesystem access, world-wide target search or unbounded nearby iteration was introduced.

## Validation and evidence

`AuthoredPveContentValidator` rejects missing roster/template/action/effect references at startup.
`scripts/audit_authored_pve_consolidation.py` produces the producer inventory, before/after shadow
combat report, boss/invasion/Prologue matrices, primitive coverage, stat provenance, threshold,
summon, reward and Folia reports. The exact-head workflow adds topology, Java 21 build, cumulative
creature regressions and Paper 1.21.11 runtime proof to the immutable artifact.

## Tuning and future boundary

Combat content belongs in `content/pve/enemies.yml`. Event timing, probability, participant
coefficients, placement and settlement stay in world/Prologue config. Human staging must tune
telegraphs, counterplay, participant scaling and reward feel before merge.

A later Composable Encounter & Boss Authoring Runtime is justified only if shipped content needs an
encounter-wide phase graph, objectives, intermissions, arena state, branching or threat authority.
Those capabilities are intentionally absent here.
