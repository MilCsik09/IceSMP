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

## Second specialization, doctrines and mastery

The existing two Profile v2 loadouts remain authoritative. Level 28 unlocks the second slot for this gameplay slice. A Warrior switch is allowed only outside the configured combat grace and without a hostile living entity inside the configured safety radius. Switching does not heal, reset Düh or reset cooldowns.

Doctrine choices live in `ClassLoadout#doctrineChoices`, keyed by the level tier (`level_30`, `level_40`, `level_50`). They are slot-local and durable. Mastery and capstone state remain the existing loadout fields; no mastery YAML or separate talent/doctrine store exists.

## Lélekkapocs

The Sárkánykirály Kürtje stays the Warrior's spellbook/caster focus and uses the existing catalyst/spellbook UX. The physical `ItemStack` carries only rebuildable owner/class/presentation mirrors; it is never gameplay authority. Profile v2 and spell provenance decide what may be cast.

A Warrior must use its personal Kürt to cast. The active combat set is capped at seven spells by reusing the existing favorites/selection system. Spec changes update the same artifact's presentation and invalidate old spec grants through the existing runtime reconcile path.

## Content gates outside this architecture

The current Class Relic catalog defines only the Evoker/Sárkánytojás pilot, so this slice does not invent a Warrior relic. `Törött Kürt` and `Utolsó Fal` have stable capstone trial identifiers and Profile v2 completion state, but their physical arena/caravan/gate content remains a builder/event gate until it exists in the world.
