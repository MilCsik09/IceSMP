# Profile v2 operations runbook

## Authority model

Profile v2 is always enabled and is the sole class/spec authority. There is no migration mode, legacy fallback,
dual-write period or kill switch. `class-spec-rework.dependencies.enforce` controls dependency validation only; it
does not select another runtime.

The first gameplay vertical slice is the **Harcos** (`warrior`) with `berserker` and `guardian`. Its combat meters are transient runtime state; class, loadouts, doctrines, mastery and capstone remain Profile v2 state.

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

For Warrior gameplay:

- `/spec switch <first|second>` changes the active learned Warrior specialization only when the switch safety gate passes;
- `/spec doctrine <30|40|50> <choice>` commits the active loadout's doctrine choice;
- class level/XP is shared by the two loadouts; doctrine, mastery and capstone are slot-local;
- Düh and active spell cooldown consequences are not reset by a legal loadout switch;
- Berserker Vérőrület/Kimerülés and Guardian Őrség/Eskütárs are transient and are cleared at the switch boundary.

## Warrior switch safety

The switch gate is live-config driven:

- `classes.specialization.second-slot-level` — default 28;
- `classes.specialization.switch-combat-grace-seconds` — default 8;
- `classes.specialization.switch-safe-radius` — default 12 blocks.

The current vertical slice unlocks and accepts the SECOND loadout only for `warrior`. Other classes remain single-spec until their own gameplay slice explicitly enables and validates second-spec switching.

A switch fails closed while the player is still in the combat grace, while a hostile living entity is inside the configured radius, while the target slot is unavailable, or while the Profile v2 session is not READY. The switch must never be used as a heal, Düh reset or cooldown reset.

## Warrior Lélekkapocs recovery

The Sárkánykirály Kürtje is a personal spellbook mirror, not the durable class authority. A valid physical copy is owner/class bound. Foreign copies are not usable and cannot be picked up by another player.

The personal artifact cannot be moved into an external inventory. On death it is removed from the drop list and added to `PlayerDeathEvent#getItemsToKeep()` in the same event, so there is no asynchronous claim/materialize/redeposit crash window. If the physical mirror is ever missing, the normal owner-bound Soulbond refresh path can rebuild it from Profile v2 without changing class/spec progression.

A Warrior cast requires the personal Kürt; the generic melee-catalyst compatibility path does not bypass it. The active combat list is capped at seven spells and reuses the existing spellbook/favorites UX.

## Berserker Dacoló durability rule

`defiant` no longer cancels a lethal damage event before persistence. It is a critical-health recovery:

1. a surviving hit projects the Berserker below `classes.warrior.berserker.defiant.trigger-health-ratio`;
2. the persistent cooldown reservation commits first in PlayerProfile;
3. only after that durable witness exists is the region-thread recovery allowed to raise health to the configured floor and force maximum Kimerülés.

A crash before the cooldown commit therefore grants no recovery side effect. A scheduler/runtime failure after durable commit is fail-closed and may consume the cooldown without granting the recovery; that is preferable to duplicating a durable death-save effect.

## Warrior capstone/build gates

At level 50 the relevant loadout may enter capstone `AVAILABLE`. The stable content contracts are:

- Berserker: `warrior_berserker_broken_horn` — **Törött Kürt**;
- Guardian: `warrior_guardian_last_wall` — **Utolsó Fal**.

The repository does not claim that the physical arena, caravan or capital-gate build already exists. Do not fabricate coordinates or mark the trial completed through unrelated kills. Builder/event provisioning and staging validation are mandatory before those trials are considered live content.

The current `relics.class-relics` catalog contains no canonical Warrior Class Relic binding. Do not invent an operational Warrior relic/resonance/awakening entry as a workaround; the class is complete without a relic and the future relic content is a separate gate.

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

## Warrior staging acceptance

Before moving the gameplay PR out of draft, perform live Folia tests for:

- legal/illegal second-spec switching, including combat, nearby enemies, reconnect and cooldown/Düh preservation;
- Berserker 70+ Fury, 50 ms-equivalent HUD polling, safe dump, overdrive, aftermath, Hóhér PvE/PvP values and durable-before-effect Dacoló critical recovery;
- Guardian Eskütárs player/NPC/objective assignment, entity removal, intercept, shield and multi-player support without damage recursion;
- death/quit/kick/disable cleanup and reconnect reconstruction;
- personal Kürt loss, death retention, external-container transfer attempts, foreign copy and duplicate-copy behavior;
- real TTK/healing/CC/party balance. Unit tests prove invariants, not final balance.

Hosted GitHub Actions that fail with `steps=null` are runner/credit infrastructure failures, not evidence of a code failure. Keep the PR draft and rely on a full local or actually-executed runner validation before merge.

## Backup and restore

Back up the complete Profile v2 directory, quarantine evidence, respec journal and currency store together.
Restore them as one consistent set. Restoring legacy class/spec PDC is not a supported rollback.
