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
- level 28 unlocks the existing second Profile v2 slot **for Warrior only**; SECOND-slot learning and loadout switching for unreworked classes fail closed at the gateway;
- Warrior slot switching is combat-gated, enemy-proximity-gated, preserves common Düh/cooldowns and clears specialization-local transient state;
- level 30/40/50 Warrior doctrines are durable and slot-local; a committed tier cannot silently overwrite another choice;
- level 50 starts specialization-local mastery and exposes the capstone trial state; capstone completion remains Profile v2 state;
- the Sárkánykirály Kürtje remains the Warrior's required personal Lélekkapocs/spellbook: owner/class-bound physical mirror, Profile-v2-backed permissions, existing right-click / sneak-right-click / sneak-scroll UX and a maximum seven-spell active combat set;
- duplicate/foreign physical artifacts do not create spell, resource or cooldown authority; the personal artifact cannot be moved into external inventories and death retention uses the same-event `itemsToKeep` path instead of asynchronous escrow hand-back;
- Harcos transient state has explicit death/quit/kick/disable/spec-switch cleanup;
- `WarriorGameplayRegressionSuite` and `WarriorProfileRegressionSuite` are part of Gradle `check` and the CI marker contract; blocker regressions include 50 ms polling plus source contracts for Defiant ordering, Warrior-only second slot and Soulbond transfer/death handling.

## Explicitly still open after this PR

- the remaining 12 classes and 33 specializations have not passed this vertical-slice gameplay gate;
- no generic mechanics-core primitive library is planned from this first implementation; common extraction is allowed only after real repeated consumers prove the same lifecycle/invariants;
- the physical world content for `warrior_berserker_broken_horn` (Törött Kürt) and `warrior_guardian_last_wall` (Utolsó Fal) is a builder/event gate; no fabricated coordinates or fake arena completion is part of the code slice;
- the current Class Relic catalog has no canonical Warrior binding/resonance/awakening definition. The framework is reused but no new Warrior relic design is invented here;
- full numeric Warrior PvE/PvP balance, TTK, party pressure and real Guardian objective protection require staging playtest;
- the separate class-HP/A17 rollout remains disabled and is not activated by this gameplay work;
- complete ability kits and gameplay loops for every other specialization remain open.

## Release gates outside unit/regression execution

- real multi-region Folia staging, including Guardian oath target retirement and cross-region support effects;
- Berserker PvP burst/execution, critical-health Defiant recovery and cooldown playtest;
- Warrior second-spec switch under real combat/logout/reconnect timing;
- Lélekkapocs loss/full-inventory/reconnect plus external-container transfer tests on a running server;
- builder provisioning and gameplay validation of both final trials;
- deployment plugin bundle and dependency-lock validation;
- controlled filesystem permission/ENOSPC tests;
- longer multi-player leak/soak testing.

These staging gates do not authorize a legacy fallback. Failure remains fail closed and must be corrected before release.
