# Class/spec rework gap analysis — Profile v2 foundation and gameplay slices

## Foundation already closed

- greenfield Profile v2 as the sole class/spec authority;
- owner-bound immutable aggregate and deterministic codec;
- strict YAML envelope, CAS, atomic replacement, quarantine and explicit recovery;
- concurrent first-profile initialization and session-generation fencing;
- complete DARK gate-set seal/unseal persistence;
- explicit spell provenance;
- durable class XP/level, pet roster and Soulforge receipts;
- respec WAL/wallet/profile recovery protocol;
- bounded lifecycle shutdown and Folia scheduler boundaries.

## Gameplay closed by the Harcos vertical slice

- the first complete class gameplay implementation is `warrior` with `berserker` and `guardian` only;
- Düh remains the existing class-resource/cast-cost pool; Csatatempó is a separate transient class decision layer with `Rendezett`, `Heves` and `Túlcsorduló` tiers;
- Berserker has concrete Vérőrület/Kimerülés build, safe-dump, overdrive/aftermath and explicit PvE/PvP burst clamps;
- Kimerülés integration preserves fractional elapsed time under frequent HUD/combat polling and splits overdrive-end timing correctly;
- `defiant` is durability-first critical recovery: persistent cooldown reservation commits before any region-thread recovery side effect, so a crash before commit cannot grant a free survival effect;
- Guardian has concrete Őrség plus one Eskütárs, with shield/intercept protection and no recursive damage-redirection path;
- level 28 unlocks the existing second Profile v2 slot for the completed gameplay-v2 classes (`GameplayV2ClassPolicy` allowlist); SECOND-slot learning and loadout switching for unreworked classes fail closed at the gateway;
- Warrior slot switching is combat-gated, enemy-proximity-gated, preserves common Düh/cooldowns and clears specialization-local transient state;
- level 30/40/50 Warrior doctrines are durable and slot-local; a committed tier cannot silently overwrite another choice;
- level 50 starts specialization-local mastery and exposes the capstone trial state; capstone completion remains Profile v2 state;
- the Sárkánykirály Kürtje remains the Warrior's required personal Lélekkapocs/spellbook: owner/class-bound physical mirror, Profile-v2-backed permissions, existing right-click / sneak-right-click / sneak-scroll UX and a maximum seven-spell active combat set;
- duplicate/foreign physical artifacts do not create spell, resource or cooldown authority; the personal artifact cannot be moved into external inventories and death retention uses the same-event `itemsToKeep` path instead of asynchronous escrow hand-back;
- Harcos transient state has explicit death/quit/kick/disable/spec-switch cleanup;
- `WarriorGameplayRegressionSuite` and `WarriorProfileRegressionSuite` are part of Gradle `check` and the CI marker contract; blocker regressions include 50 ms polling plus source contracts for Defiant ordering, Warrior-only second slot and Soulbond transfer/death handling.

## Gameplay closed by the Sárkányidéző vertical slice

- the second complete class gameplay implementation is `evoker` with `devastation` (Perzselés) and `preservation` (Megőrzés);
- the class core is Felerősítés: charge-and-release on four concrete spells (`fire_breath`, `eternity_surge`, `dream_breath`, `spiritbloom`) with hold-rank I/II/III, damage interruption and a fizzle window — no generic charged-spell framework;
- Perzselés plays a single Vörös–Kék Eszencia alternation counter; the armed burst empowers exactly one cast through the shared, doubly-capped power pipeline, with `iker_aram`/`tulhevites`/`orok_izzas`/`kettos_szikra` doctrine variants;
- Megőrzés plays Visszhang (single-use prepared-heal echo on the caster and one Fiola-marked ally, delivered on the target entity scheduler) and Időlenyomat (heal-only health imprint, single-use, window-bounded, cap-bounded; no inventory/position/quest/item/currency rollback by contract and by regression);
- the Sárkányvér-fiola reuses the one shared Soulbond lifecycle (owner/class binding, container/death/foreign-copy/duplicate handling) with Evoker presentation and the Megőrzés marking interaction;
- second-slot unlock, loadout switching, doctrine choice, mastery contribution and capstone reconciliation were generalized behind the explicit `GameplayV2ClassPolicy` allowlist instead of per-class copies; every unreworked class remains fail-closed;
- Evoker mastery XP (empowered release, resonance burst, landed echo, imprint restore) is combat-gated to keep dummy/AFK farming worthless;
- Evoker transient state has explicit death/quit/kick/disable/spec-switch cleanup, including the rule that a held charge never survives a spec switch;
- `EvokerGameplayRegressionSuite` and `EvokerProfileRegressionSuite` are part of Gradle `check` and the CI marker contract, covering charge ranks/fizzle/interrupt, essence alternation/burst/retention, echo single-use, imprint heal-only bounds, allowlist gateway behavior and slot isolation;
- a pre-existing staging regression was fixed en route: the `check` dependsOn rewrap had broken the `AdvancedConfigMenuRegressionSuite` source contract, so `./gradlew check` failed on the base branch; the list was re-wrapped to restore the contract.

## Gameplay closed by the Íjász vertical slice

- the third complete class gameplay implementation is `archer` with `sharpshooter` (Mesterlövész) and `beast_master` (Vadmester);
- the class core is Szélolvasás: a disciplined (full-draw, paced, real-distance) hit arms one single-use read that empowers the next disciplined shot; spam breaks it, static camping alone earns nothing, and distance is measured from the recorded shot origin without cross-region entity access;
- Mesterlövész plays Préda-jel + Pontossági lánc: consecutive full-draw hits on one prey build a bounded chain; the weak-point finisher consumes it as an event-based damage bonus under explicit `classes.archer.pve/pvp-max-bonus-percent` clamps, with `nyugodt_kez`/`gyors_felhuzas`/`eles_szem`/`mely_loves`/`sorozat`/`egy_loves_egy_elet` doctrine variants;
- Vadmester plays Kötelék on top of the existing companion authority: coordination with the active companion's combat target builds the bond, `primal_bond`/`king_of_beasts` spend it, and pet death collapses it unless `orok_kotelek` retains part; the companion szerep/viselkedés dimension is the existing stance system;
- the non-DARK companion lifecycle is proven on the existing PetManager/`CompanionProfile` machinery: the `beast_master.stable` roster is durable and slot-local, live pet entity UUIDs remain runtime-only, capture into a full stable (`pets.stable.maximum`, default 3) fails closed, and the new `/pet release` frees a slot with a durable-first companion REMOVE;
- Archer mastery XP (weak point, coordination, bond spend, capstone) is combat-gated;
- Archer transient state has explicit death/quit/kick/disable/spec-switch cleanup; the durable stable deliberately survives a loadout switch while the transient bond clears;
- `ArcherGameplayRegressionSuite` and `ArcherProfileRegressionSuite` are part of Gradle `check` and the CI marker contract, covering read pacing/single-use/distance anchoring, chain prey/window/retention, bond build/spend/collapse, allowlist gateway behavior, roster survival across switches and slot isolation.

## Explicitly still open after this PR

- the remaining 10 classes and 29 specializations have not passed this vertical-slice gameplay gate;
- no generic mechanics-core primitive library is planned from this first implementation; common extraction is allowed only after real repeated consumers prove the same lifecycle/invariants;
- the physical world content for `warrior_berserker_broken_horn` (Törött Kürt), `warrior_guardian_last_wall` (Utolsó Fal), `evoker_devastation_trial`, `evoker_preservation_trial`, `archer_sharpshooter_trial` and `archer_beast_master_trial` is a builder/event gate; no fabricated coordinates or fake arena completion is part of the code slices. The Evoker and Archer trial ids intentionally carry no lore names because the canonical game-design document (`IceSMP_Kasztok_es_Specializaciok_Teljes_Jatekdesign_VEGLEGES.md`) is not available in this repository or the session file library; the Evoker and Archer doctrine identifiers are likewise mechanic-descriptive working names pending canonical verification;
- the current Class Relic catalog has no canonical Warrior binding/resonance/awakening definition. The framework is reused but no new Warrior relic design is invented here;
- full numeric Warrior and Evoker PvE/PvP balance, TTK, party pressure and real Guardian objective protection require staging playtest; the Evoker empower/burst bonus rides the shared cast-power pipeline, so its PvP clamp is the double cap (`classes.evoker.max-power-bonus-percent` + `spells.total-power-cap`), not a per-target split;
- the separate class-HP/A17 rollout remains disabled and is not activated by this gameplay work;
- complete ability kits and gameplay loops for every other specialization remain open.

## Release gates outside unit/regression execution

- real multi-region Folia staging, including Guardian oath target retirement, Evoker marked-ally echo delivery and cross-region support effects;
- Berserker PvP burst/execution, critical-health Defiant recovery and cooldown playtest;
- Evoker empowered-release timing feel, burst cadence and echo/imprint heal pressure under real party play;
- Archer read/chain cadence, stable capture/release at capacity and live companion coordination across regions;
- gameplay-v2 second-spec switch under real combat/logout/reconnect timing on every allowlisted class;
- Lélekkapocs loss/full-inventory/reconnect plus external-container transfer tests on a running server;
- builder provisioning and gameplay validation of both final trials;
- deployment plugin bundle and dependency-lock validation;
- controlled filesystem permission/ENOSPC tests;
- longer multi-player leak/soak testing.

These staging gates do not authorize a legacy fallback. Failure remains fail closed and must be corrected before release.
