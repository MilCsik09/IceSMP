# Profile v2 operations runbook

## Authority model

Profile v2 is always enabled and is the sole class/spec authority. There is no migration mode, legacy fallback,
dual-write period or kill switch. `class-spec-rework.dependencies.enforce` controls dependency validation only; it
does not select another runtime.

The completed gameplay vertical slices are the **Harcos** (`warrior`: `berserker`, `guardian`), the **Sárkányidéző** (`evoker`: `devastation`/Perzselés, `preservation`/Megőrzés) and the **Íjász** (`archer`: `sharpshooter`/Mesterlövész, `beast_master`/Vadmester) and the **Sámán** (`shaman`: `elemental`/Elemi, `enhancement`/Erősítő, `tidal`/Hullámhívó). Combat meters, charges, chains and prepared-heal windows are transient runtime state; class, loadouts, doctrines, mastery, capstone and the companion stable remain Profile v2 state. The completed slices are listed in the explicit `GameplayV2ClassPolicy` allowlist — every other class stays single-spec and fail-closed.

## Normal states

| State | Gameplay | Operator action |
|---|---:|---|
| `READY` | allowed when session is READY and no operation is pending | none |
| `REVIEW` | blocked | inspect diagnostic and recovery-required operation |
| `QUARANTINED` | blocked | preserve evidence and use explicit recovery |
| `SEALED` loadout | that loadout blocked | restore all eligible gates or use documented admin action |
| reconciliation required | blocked | inspect runtime failure, retry cleanup/reconnect |

## Diagnostics

Use `/spec info` for schema, revision, class, slots, complete seal reasons, slot mastery, doctrine/capstone state and session block detail. Repository logs include owner UUID, bounded error detail and evidence ID; raw payloads are not dumped into logs.

For the completed gameplay-v2 classes (Warrior, Evoker, Archer, Shaman):

- `/spec switch <first|second|spec-id>` changes the active learned specialization only when the switch safety gate passes;
- `/spec doctrine <30|40|50> <choice>` commits the active loadout's doctrine choice;
- class level/XP is shared by the two loadouts; doctrine, mastery and capstone are slot-local;
- resource and active spell cooldown consequences are not reset by a legal loadout switch;
- spec-local transient state (Berserker Vérőrület/Kimerülés, Guardian Őrség/Eskütárs, Evoker Izzás/Visszhang/Időlenyomat/jelölt társ, any held Felerősítés charge, Archer Szélolvasás/Pontossági lánc/Kötelék, and Sámán Rezonancia/Maelstrom/Ár) is cleared at the switch boundary. The Vadmester **stable roster is durable Profile v2 state** and deliberately survives the switch; only the transient bond clears.

## Switch safety

The switch gate is live-config driven:

- `classes.specialization.second-slot-level` — default 28;
- `classes.specialization.switch-combat-grace-seconds` — default 8;
- `classes.specialization.switch-safe-radius` — default 12 blocks.

The SECOND loadout unlocks and is accepted only for the classes in the `GameplayV2ClassPolicy` allowlist (`warrior`, `evoker`, `archer`, `shaman`). Other classes remain single-spec until their own gameplay slice explicitly enables and validates second-spec switching.

A switch fails closed while the player is still in the combat grace, while a hostile living entity is inside the configured radius, while the target slot is unavailable, or while the Profile v2 session is not READY. The switch must never be used as a heal, Düh reset or cooldown reset.

## Warrior Lélekkapocs recovery

The Sárkánykirály Kürtje is a personal spellbook mirror, not the durable class authority. A valid physical copy is owner/class bound. Foreign copies are not usable and cannot be picked up by another player.

The personal artifact cannot be moved into an external inventory. On death it is removed from the drop list and added to `PlayerDeathEvent#getItemsToKeep()` in the same event, so there is no asynchronous claim/materialize/redeposit crash window. If the physical mirror is ever missing, the normal owner-bound Soulbond refresh path can rebuild it from Profile v2 without changing class/spec progression.

A Warrior cast requires the personal Kürt and an Evoker cast requires the personal **Sárkányvér-fiola**; the generic melee-catalyst compatibility path does not bypass either. The active combat list is capped at seven spells per gameplay-v2 class and reuses the existing spellbook/favorites UX. The Fiola recovery, death retention and foreign-copy rules are identical to the Kürt — one shared owner-bound Soulbond lifecycle, class-specific presentation only.

## Archer gameplay rules

- **Szélolvasás (class core):** a disciplined hit — full draw (`classes.archer.wind.full-draw-force`), paced rhythm (`shot-pacing-millis`) and real distance (`minimum-distance`, measured from the recorded shot origin) — arms one read; the next disciplined shot deals bonus damage. Spam breaks an armed read; the read is single-use and window-bounded. Static camping alone earns nothing.
- **Mesterlövész (Préda-jel + Pontossági lánc):** consecutive full-draw hits on the same prey build a bounded chain (`precision.maximum-chain`); at `weak-point-threshold` the next hit on the prey (or `masterful_shot`) consumes it as a weak-point strike. Switching targets or letting the window lapse restarts the chain. Wind + weak-point bonuses are capped by explicit `classes.archer.pve/pvp-max-bonus-percent` clamps.
- **Vadmester (Kötelék + Istálló):** arrow hits on the active companion's current combat target build the Kötelék; `primal_bond` and the capstone `king_of_beasts` spend it (durable roster and pet identity stay in Profile v2/PetManager). The stable holds at most `pets.stable.maximum` (default 3) captured companions; capture into a full stable fails closed and `/pet release` frees a slot with a durable-first REMOVE. The companion's szerep/viselkedés is the existing stance system (`/pet stance`). Pet death collapses the bond unless the level-50 doctrine retains part of it.
- **Mastery:** weak-point finishes, pet coordination, bond spends and the capstone grant mastery XP only in combat (`classes.archer.mastery.combat-window-seconds`).

## Shaman gameplay rules

- **Totemkerék (class core):** the four existing totem types split into fő (Perzselő, Földbéklyó) and kísérő (Gyógyár, Szélharag) categories; at most one of each lives per shaman and placing a same-category totem replaces the previous one. The totem lifecycle stays entirely in TotemManager (own region scheduler, bounded lifetime, crash-orphan cleanup); the gameplay service only reads the pair projection.
- **Elemi (Elemi Rezonancia):** with the full pair standing (the `mely_gyokerek` doctrine accepts one totem), casts whose element matches a live totem charge the Túltöltés; at the threshold the next resonant cast overloads through the shared, capped power pipeline.
- **Erősítő (Fegyveráldás + Maelstrom):** melee hits build the Maelstrom; a hit inside the rhythm window (`rhythm-min/max-millis`) alternates the Vihar↔Föld blessing side and earns the bonus — pure spam earns only the base. `stormstrike`/`crash_lightning` spend it, the `doom_winds` capstone vents everything.
- **Hullámhívó (Dagály ↔ Apály):** direct heals push the tide toward Dagály, chain heals toward Apály; the reached side empowers the NEXT cast of the other family once, then the tide flows back to the middle — the ping-pong consume prevents any infinite heal feedback loop.
- **Mastery:** overloads, Maelstrom spends/vents and tide consumes grant mastery XP only in combat.

## Evoker gameplay rules

- **Felerősítés (class core):** the first click on an empowerable spell (`fire_breath`, `eternity_surge`, `dream_breath`, `spiritbloom`) starts a charge without spending resources; the next click releases it at rank I/II/III depending on hold time. An overheld charge fizzles (`classes.evoker.empower.fizzle-millis`) and a hit at or above `classes.evoker.empower.interrupt-damage` interrupts it. A held charge never survives death, logout or a spec switch.
- **Perzselés (Vörös–Kék Eszencia):** alternating red/blue essence spells build Izzás up to `burst-threshold`; repeating a color restarts the chain. An armed burst empowers the next essence spell through the shared, capped cast-power pipeline (`classes.evoker.max-power-bonus-percent` plus the global `spells.total-power-cap`).
- **Megőrzés (Visszhang + Időlenyomat):** `echo` arms a single-use echo window — the next prepared heal repeats once, on the caster and on the ally marked with the Fiola (sneak + right-click). `reversion` records a heal-only Időlenyomat: consuming it (`temporal_anomaly`, capstone `rewind`) restores health toward the recorded value, bounded by the configured cap. The imprint never rolls back inventory, position, quests, items or currency — health only, single-use, window-bounded.
- **Mastery:** empowered releases, resonance bursts, landed echoes and imprint restores grant mastery XP only in combat (`classes.evoker.mastery.combat-window-seconds`); dummy/AFK spam earns nothing.

## Berserker Dacoló durability rule

`defiant` no longer cancels a lethal damage event before persistence. It is a critical-health recovery:

1. a surviving hit projects the Berserker below `classes.warrior.berserker.defiant.trigger-health-ratio`;
2. the persistent cooldown reservation commits first in PlayerProfile;
3. only after that durable witness exists is the region-thread recovery allowed to raise health to the configured floor and force maximum Kimerülés.

A crash before the cooldown commit therefore grants no recovery side effect. A scheduler/runtime failure after durable commit is fail-closed and may consume the cooldown without granting the recovery; that is preferable to duplicating a durable death-save effect.

## Capstone/build gates

At level 50 the relevant loadout may enter capstone `AVAILABLE`. The stable content contracts are:

- Berserker: `warrior_berserker_broken_horn` — **Törött Kürt**;
- Guardian: `warrior_guardian_last_wall` — **Utolsó Fal**;
- Perzselés: `evoker_devastation_trial`;
- Megőrzés: `evoker_preservation_trial`;
- Mesterlövész: `archer_sharpshooter_trial`;
- Vadmester: `archer_beast_master_trial`;
- Elemi: `shaman_elemental_trial`;
- Erősítő: `shaman_enhancement_trial`;
- Hullámhívó: `shaman_tidal_trial`.

The Evoker, Archer and Shaman trial quest ids are deliberately mechanical placeholders: the canonical trial names/lore live in the game-design document that is not currently available in this repository, so no lore names were invented for them. The repository does not claim that any trial's physical build exists. Do not fabricate coordinates or mark a trial completed through unrelated kills. Builder/event provisioning and staging validation are mandatory before those trials are considered live content.

The current `relics.class-relics` catalog contains Evoker pilot relic content from the Class Relic Framework but no canonical Warrior binding. Do not invent an operational relic/resonance/awakening entry as a workaround; each class is playable without a relic and future relic content is a separate gate.

## Quarantine recovery

1. Stop repeated login attempts for the target while investigating.
2. Record the owner UUID, evidence ID, quarantine reason and filesystem backup.
3. Confirm that the evidence/marker belongs to the target UUID.
4. Run:

   ```text
   /spec recover <player|uuid> confirm
   ```

   Permission: `icesmp.admin.spec.recover`.
5. The command preserves evidence, records an audit ID and creates a clean, inactive revision-0 profile.
6. Have the player reconnect and choose class/spec again.

Do not use normal reset for a quarantined profile and do not copy another player's profile file into place.

## Runtime reconciliation failure

A durable profile commit and runtime application are separate reported phases. If persistence succeeded but
spell/pet/form/Warrior transient cleanup or rebuild failed, the session is marked reconciliation-required. Do not refund or replay economic operations manually. Correct the scheduler/runtime cause, perform idempotent cleanup, then reconnect.

## Respec recovery

On startup/login the recovery protocol compares the respec journal, wallet witness and Profile v2 receipt. It
finishes or rolls back a partial operation deterministically. Do not edit only one of these stores. Preserve all
three before manual intervention.

A Warrior respec/reset must also clear specialization-local transient state. Common class progression is only removed by the existing explicit class-reset path; a normal loadout respec must not silently create a parallel class authority.

## Soulforge and companion incidents

Soulforge rank and shard balance commit in one profile revision and are protected by a durable operation ID.
Repeated committed IDs are no-ops. Companion roster state is durable; live entity UUIDs are runtime-only. If
spawn/rebuild fails, keep the roster and repair/retry the runtime side rather than deleting the profile.

## Shutdown

Disable closes mutation admission, drains accepted operations for bounded intervals, flushes the repository,
invalidates sessions and then stops executors/runtime adapters. Warrior transient state and live Eskütárs handles are lifecycle-owned and must be cleared before the runtime is considered stopped. A timeout is an operational failure: preserve logs and stores, perform a controlled restart and let recovery protocols evaluate pending operations. Never wait indefinitely or repeatedly reload the plugin in-process.

## Gameplay staging acceptance

Before moving a gameplay PR out of draft, perform live Folia tests for:

- legal/illegal second-spec switching, including combat, nearby enemies, reconnect and resource/cooldown preservation;
- Berserker 70+ Fury, 50 ms-equivalent HUD polling, safe dump, overdrive, aftermath, Hóhér PvE/PvP values and durable-before-effect Dacoló critical recovery;
- Guardian Eskütárs player/NPC/objective assignment, entity removal, intercept, shield and multi-player support without damage recursion;
- Evoker Felerősítés charge/release/fizzle/interrupt feel, Izzás alternation and burst cadence, echo delivery to the marked ally across regions, and Időlenyomat restore bounds;
- Archer Szélolvasás pacing/distance feel, precision-chain and weak-point cadence, stable capture/release at capacity, bond coordination with a live companion across regions, and pet-death bond collapse;
- Shaman Totemkerék replacement across regions, resonance/overload cadence with the live pair, Fegyveráldás rhythm feel and Dagály↔Apály heal pressure without feedback loops;
- death/quit/kick/disable cleanup and reconnect reconstruction;
- personal Kürt/Fiola loss, death retention, external-container transfer attempts, foreign copy and duplicate-copy behavior;
- real TTK/healing/CC/party balance. Unit tests prove invariants, not final balance.

Hosted GitHub Actions that fail with `steps=null` are runner/credit infrastructure failures, not evidence of a code failure. Keep the PR draft and rely on a full local or actually-executed runner validation before merge.

## Backup and restore

Back up the complete Profile v2 directory, quarantine evidence, respec journal and currency store together.
Restore them as one consistent set. Restoring legacy class/spec PDC is not a supported rollback.
