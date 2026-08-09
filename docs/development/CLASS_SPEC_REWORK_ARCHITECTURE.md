# Class/spec section architecture

Class/spec is no longer an independent profile aggregate. Its canonical durable architecture is the `class-spec` section described in [../ARCHITECTURE.md](../ARCHITECTURE.md#playerprofile-platform).

## Gameplay implementation rule

Gameplay does **not** introduce a second authority or a generic mechanic platform. The order of preference is:

1. reuse the existing PlayerProfile/Profile v2, Spell, ResourceManager, HUD, Quest and Class Relic boundaries;
2. implement the class mechanic with concrete, small state/services;
3. extract a helper only after multiple real classes prove the same lifecycle and invariants.

A class-specific integer/timestamp/map is not a reason to build a meter/mark/state-machine DSL. Durable state belongs in the existing Profile v2 loadout. Combat-only state must stay transient and carry explicit death/quit/kick/disable/spec-switch cleanup.

## Harcos vertical slice

`WarriorGameplayService` is the first concrete gameplay consumer. `WarriorCombatState` contains only the Harcos mechanics:

- class-common Csatatempó;
- Berserker Vérőrület/Kimerülés/overdrive;
- Guardian Őrség/Eskütárs.

It is event-driven; there is no per-tick online-player scan. Guardian protection uses shield/intercept effects rather than recursive damage redirection. Cross-entity Guardian effects run through the target entity scheduler.

The existing `ResourceManager` remains the Düh authority for spell cost/gain. Csatatempó is intentionally a different transient decision layer. `BukkitClassSpecRuntimeAdapter` preserves class-common Düh and active cooldowns on `LOADOUT_SWITCH`, while the Harcos service clears the old specialization state.

## Sárkányidéző vertical slice

`EvokerGameplayService` is the second concrete gameplay consumer and the deliberate architectural counter-proof: it does not clone the Warrior shape. `EvokerCombatState` contains only the Sárkányidéző mechanics:

- class-core Felerősítés: a seconds-scale charge on a handful of concrete spells (first click charges, second click releases at rank I/II/III; a hit interrupts, overholding fizzles);
- Perzselés Vörös–Kék Eszencia: one red/blue alternation counter whose armed burst rides the existing capped cast-power pipeline — no damage-amplification event handler and no extra meters;
- Megőrzés Visszhang + Időlenyomat: a single-use prepared-heal echo (self plus one Fiola-marked ally) and one heal-only health imprint. The imprint restores health toward the recorded value under a configured cap and never touches inventory, position, quests, items or currency;
- there is no class meter, no target-bound reverse index and no kill-event resource path.

The empower and burst bonuses enter combat exclusively through `AbilityCatalystListener`'s existing power computation (`castPowerBonusPercent`, clamped by `classes.evoker.max-power-bonus-percent` and the global `spells.total-power-cap`). Cross-entity echo heals run on the target entity's scheduler. Mastery contributions are combat-gated through the existing `ResourceManager` combat tracker.

## Íjász vertical slice

`ArcherGameplayService` is the third concrete gameplay consumer and the first non-DARK companion proof. `ArcherCombatState` contains only the Íjász mechanics:

- class-core Szélolvasás: one single-use read armed by a disciplined hit (full draw + shot pacing + real distance measured from the recorded shot origin — plain coordinates, never a cross-region entity read);
- Mesterlövész: one prey target with a bounded precision chain and a weak-point finisher, applied as an event-based damage bonus under explicit PvE/PvP caps;
- Vadmester: one Kötelék percentage built from coordinated hits on the active companion's combat target. The durable stable stays entirely in the existing PetManager/`CompanionProfile` machinery (`beast_master.stable` namespace); the slice only adds a capacity gate at capture, a durable-first `/pet release` REMOVE path, two runtime-projection accessors and a pet-death hook — no new pet framework.

There is no repeating task and no proximity scan in the archer runtime; everything is event-driven off bow-shot and damage events.

## Sámán vertical slice

`ShamanGameplayService` is the fourth concrete gameplay consumer and the totem-infrastructure proof. `ShamanCombatState` contains only the Sámán mechanics (Overload charge, rhythm-built Maelstrom with alternating blessing sides, one signed Dagály↔Apály tide). The Totemkerék extends the existing TotemManager minimally: every type gains a fő/kísérő category, a per-owner projection tracks the live pair, and placing a same-category totem replaces the old one on its own region scheduler. Resonance queries read that projection; no totem lifecycle logic moved out of the manager, and the shaman runtime adds no repeating task or proximity scan.

## Szerzetes vertical slice

`MonkGameplayService` is the fifth concrete gameplay consumer. `MonkCombatState` contains only the Szerzetes mechanics: the Áramlás variety meter (bounded recent-technique window with lazy decay), one explicit config-declared martial chain, one bounded Stagger pool and up to three Ködszál link identities. The Stagger drain steps health directly on the player's scheduler with a half-heart floor — it can never emit a duplicated damage event or kill on its own — and the remaining pool lands immediately on quit/kick/spec-switch so the deferral cannot be escaped. Ripple heals always hop to the linked ally's scheduler. No repeating global task and no proximity scan lives here.

## Paplovag vertical slice

`PaladinGameplayService` is the sixth concrete gameplay consumer. `PaladinCombatState` contains only the Paplovag mechanics: the session Eskü choice (defaulting to the active spec's role, surviving spec switches, reset on logout), the Meggyőződés meter with lazy decay, the three Ítélet-jelek toward a Verdict and one Pajzstöltet charge. The Fényjelző is a single beacon handle whose echo hops to the ally's scheduler; the Megszentelt Föld is a one-shot bounded pass over nearby allies at cast time — no repeating task, no zone entity and no Warrior-Guardian-style target-bound reverse index.

## Second specialization, doctrines and mastery

The existing two Profile v2 loadouts remain authoritative. Level 28 unlocks the second slot for the completed gameplay-v2 classes, enumerated by the explicit `GameplayV2ClassPolicy` allowlist (`warrior`, `evoker`, `archer`, `shaman`, `monk`, `paladin`) — a plain list, not a capability framework. A switch is allowed only outside the configured combat grace and without a hostile living entity inside the configured safety radius. Switching does not heal, reset the class resource or reset cooldowns, and a held Felerősítés charge never survives the switch boundary.

Doctrine choices live in `ClassLoadout#doctrineChoices`, keyed by the level tier (`level_30`, `level_40`, `level_50`). They are slot-local and durable. Mastery and capstone state remain the existing loadout fields; no mastery YAML or separate talent/doctrine store exists.

## Lélekkapocs

The Sárkánykirály Kürtje stays the Warrior's spellbook/caster focus and uses the existing catalyst/spellbook UX. The physical `ItemStack` carries only rebuildable owner/class/presentation mirrors; it is never gameplay authority. Profile v2 and spell provenance decide what may be cast.

A Warrior must use its personal Kürt and an Evoker its personal Sárkányvér-fiola to cast; the melee-catalyst compatibility path is closed for every allowlisted gameplay-v2 class. The active combat set is capped at seven spells per gameplay-v2 class by reusing the existing favorites/selection system. Spec changes update the same artifact's presentation and invalidate old spec grants through the existing runtime reconcile path. The Fiola additionally serves as the Megőrzés ally-marking tool (sneak + right-click), mirroring the Guardian oath UX without duplicating its target-bound state model.

## Content gates outside this architecture

The current Class Relic catalog defines only the Evoker/Sárkánytojás pilot, so the Warrior slice does not invent a relic. `Törött Kürt`, `Utolsó Fal`, `evoker_devastation_trial` and `evoker_preservation_trial` have stable capstone trial identifiers and Profile v2 completion state, but their physical content remains a builder/event gate until it exists in the world. The Evoker trial ids carry no lore names because the canonical game-design document is not available in this repository; naming them is content work, not code work.
