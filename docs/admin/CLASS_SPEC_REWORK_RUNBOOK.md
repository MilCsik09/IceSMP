# Profile v2 operations runbook

## Authority model

Profile v2 is always enabled and is the sole class/spec authority. There is no migration mode, legacy fallback,
dual-write period or kill switch. `class-spec-rework.dependencies.enforce` controls dependency validation only; it
does not select another runtime.

The completed gameplay vertical slices are the **Harcos** (`warrior`: `berserker`, `guardian`), the **Sárkányidéző** (`evoker`: `devastation`/Perzselés, `preservation`/Megőrzés) and the **Íjász** (`archer`: `sharpshooter`/Mesterlövész, `beast_master`/Vadmester) the **Sámán** (`shaman`: `elemental`/Elemi, `enhancement`/Erősítő, `tidal`/Hullámhívó) the **Szerzetes** (`monk`: `windwalker`/Szélfutó, `brewmaster`/Sörfőző, `mistweaver`/Ködszövő) the **Paplovag** (`paladin`: `holy`/Szentlélek, `retribution`/Megtorló, `protection`/Oltalmazó) and the **Démonvadász** (`demon_hunter`: `havoc`/Tombolás, `vengeance`/Bosszú). Combat meters, charges, chains and prepared-heal windows are transient runtime state; class, loadouts, doctrines, mastery, capstone and the companion stable remain Profile v2 state. The completed slices are listed in the explicit `GameplayV2ClassPolicy` allowlist — every other class stays single-spec and fail-closed.

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

For the completed gameplay-v2 classes (Warrior, Evoker, Archer, Shaman, Monk, Paladin, Demon Hunter):

- `/spec switch <first|second|spec-id>` changes the active learned specialization only when the switch safety gate passes;
- `/spec doctrine <30|40|50> <choice>` commits the active loadout's doctrine choice;
- class level/XP is shared by the two loadouts; doctrine, mastery and capstone are slot-local;
- resource and active spell cooldown consequences are not reset by a legal loadout switch;
- spec-local transient state (Berserker Vérőrület/Kimerülés, Guardian Őrség/Eskütárs, Evoker Izzás/Visszhang/Időlenyomat/jelölt társ, any held Felerősítés charge, Archer Szélolvasás/Pontossági lánc/Kötelék, Sámán Rezonancia/Maelstrom/Ár, Szerzetes Áramlás/Lánc/Ködszál, Paplovag Meggyőződés/Jelek/Pajzstöltet, and Démonvadász Terhelés/Töredék/Fájdalom/Sigil) is cleared at the switch boundary. The Paplovag session Eskü choice deliberately survives a spec switch (class-level identity) and resets on relog. The Sörfőző Stagger pool is applied immediately (never lethal on its own) before it clears — a switch or logout is not a consequence-free escape. The Vadmester **stable roster is durable Profile v2 state** and deliberately survives the switch; only the transient bond clears.

## Switch safety

The switch gate is live-config driven:

- `classes.specialization.second-slot-level` — default 28;
- `classes.specialization.switch-combat-grace-seconds` — default 8;
- `classes.specialization.switch-safe-radius` — default 12 blocks.

The SECOND loadout unlocks and is accepted only for the classes in the `GameplayV2ClassPolicy` allowlist (`warrior`, `evoker`, `archer`, `shaman`, `monk`, `paladin`, `demon_hunter`). Other classes remain single-spec until their own gameplay slice explicitly enables and validates second-spec switching.

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

## Demon Hunter gameplay rules

- **Kárhozat-terhelés (class core):** demonic casts build load. The heated band (40+) empowers demonic casts through the capped shared power pipeline; the overloaded band (80+) gives a bigger bonus but every incoming hit bites harder (`overload-taken-penalty-percent`). The trade is fully readable on the HUD and player-controlled: `consume_magic` vents a chunk and idle load decays lazily — never a random punish.
- **Tombolás (Lélektöredék + Momentum):** damaging casts build a lightweight fragment counter (max 5 — never item entities); a mobility cast (`fel_rush`, `vengeful_retreat`) collects everything for a per-fragment heal and arms a short Momentum window that empowers the next damage cast.
- **Bosszú (Fájdalom + Sigilek):** taken damage builds the Fájdalom pool; Sigil casts and `soul_cleave`/`fel_blade` spend it. At most TWO Sigils stand concurrently — a third is rejected until one expires. No zone entities and no generic zone framework; the sigil spells' own catalog effects are the area control.
- **Mastery:** fragment collections, sigils, cleaves and the capstone earn mastery XP only in combat.
- The melee-catalyst compatibility list now names only `death_knight`; every completed gameplay-v2 class requires its personal Lélekkapocs to cast.

## Druid gameplay rules

- **Természeti Erő és Évszak (class core):** nature casts build harmony with lazy decay. An **alakváltás on the existing form system** (no new form engine) releases the whole pool at once as the season bound to that form — Tavasz = Holdforma (heal + regeneration), Nyár = Párducforma (strength), Ősz = Utazóforma (a short window that empowers the next casts through the capped shared power pipeline), Tél = Medveforma (resistance + absorption). Below `harmony.release-threshold` the form still works, only the blessing is withheld.
- **Vadőr (kombópont + Szagnyom):** claw casts build combo points (bounded); staying on ONE prey keeps the Szagnyom trail live for a PvE/PvP-split damage bonus, switching prey starts a new trail. The listed finishers spend every point at once — the combo count empowers the finisher through the capped pipeline, and the finisher heal only pays in combat.
- **Holdjós (Nap↔Hold mérleg → Eclipse):** solar casts lean the balance toward Nap, lunar casts toward Hold. Reaching the cap arms the Eclipse window (both schools empowered) and restarts the sweep — an Eclipse is earned by swinging the balance, never by camping one school.
- **Védelmező (Kéregrétegek + Gyökérháló):** defensive casts stack bark layers that sit ONLY on the druid; each meaningful hit (at least `min-damage-to-crack`) cracks one layer and is blunted by it, so chip damage cannot strip the armor. `entangling_roots`/`guardian_swipe` arm a root window that slows melee attackers on the attacker's own region thread. No ally binding, no target-bound reverse index and no zone entities — a deliberately different tank identity from both the Warrior Guardian and the Paladin Oltalmazó.
- **Helyreállító (Mag → érés → Virágzás):** heal-over-time casts plant seeds as pure counters (never persistent world plant entities), bounded by `maximum-seeds`. A seed must ripen (`ripen-millis`) before a bloom can harvest it; a bloom with no ripe seed is rejected up front, unripe seeds keep maturing after a harvest and forgotten seeds wither (`expiry-millis`).
- **Mastery:** season releases, finishers, Eclipses, bark stacks and blooms earn mastery XP only in combat.

## Paladin gameplay rules

- **Meggyőződés és Eskü (class core):** the paladin chooses a direction with `/spec esku <irgalom|itelet|oltalmazas>` (session choice; the default follows the active spec's role). In-role casts — and, under Oltalmazás, taking hits — build Meggyőződés with lazy decay; at the threshold, in-role casts empower through the capped shared power pipeline.
- **Szentlélek (Fényjelző):** sneak + right-click with the Harang marks ONE beacon ally; listed heals echo once as a bounded flat heal on the beacon's own scheduler. No raid-wide passive heal by construction.
- **Megtorló (Ítélet-jelek):** `judgment` lights Bűn, `blade_of_justice` lights Dac, `holy_fire` lights Kárhozat; all three inside the window arm the Verdict, which the listed finishers consume for a capped burst.
- **Oltalmazó (Pajzstöltet → Megszentelt Föld):** defensive casts and taken hits build the charge; `shield_of_the_righteous`/`guardian_of_kings`/`final_stand` spend it for self-resistance plus a single bounded protective pass over nearby allies (scheduler hops; no oath-target reverse index — a deliberately different tank identity from the Warrior Guardian).
- **Mastery:** beacon echoes, Verdicts and Megszentelt Föld grants earn mastery XP only in combat.

## Monk gameplay rules

- **Áramlás (class core):** technique variety builds flow; repeating a recently used technique earns nothing and refreshes its staleness. Flow decays lazily when idle.
- **Szélfutó (Harcművészeti Lánc):** one explicit, config-declared chain (`classes.monk.chain.steps`); the expected order advances it and any listed finisher consumes it through the capped shared power pipeline. No generic combo engine — the chain is plain data.
- **Sörfőző (Stagger + Főzetöv):** a bounded percent of every hit defers into the Stagger pool (cap: percent of max health); the pool drains in direct half-heart-floored health steps on the player's own scheduler — never a duplicated damage event and never lethal on its own. `purifying_brew` (and doctrine-gated `breath_of_fire`, the `invoke_niuzao` capstone) clears part of it; quit/kick/spec-switch applies the remainder instantly.
- **Ködszövő (Ködszál):** at most three allies linked with the Élet Ága (sneak + right-click; the oldest link is replaced at capacity). Direct heals ripple once to each valid link on the ally's own scheduler; invalid links self-prune at use time.
- **Mastery:** chain finishers, purifies and landed ripples grant mastery XP only in combat.

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
- Hullámhívó: `shaman_tidal_trial`;
- Szélfutó: `monk_windwalker_trial`;
- Sörfőző: `monk_brewmaster_trial`;
- Ködszövő: `monk_mistweaver_trial`;
- Szentlélek: `paladin_holy_trial`;
- Megtorló: `paladin_retribution_trial`;
- Oltalmazó: `paladin_protection_trial`;
- Tombolás: `demon_hunter_havoc_trial`;
- Bosszú: `demon_hunter_vengeance_trial`;
- Vadőr: `druid_feral_trial`;
- Holdjós: `druid_lunar_trial`;
- Védelmező: `druid_ironbark_trial`;
- Helyreállító: `druid_restoration_trial`.

The Evoker, Archer, Shaman, Monk, Paladin, Demon Hunter and Druid trial quest ids are deliberately mechanical placeholders: the canonical trial names/lore live in the game-design document that is not currently available in this repository, so no lore names were invented for them. The repository does not claim that any trial's physical build exists. Do not fabricate coordinates or mark a trial completed through unrelated kills. Builder/event provisioning and staging validation are mandatory before those trials are considered live content.

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
- Monk chain cadence, Stagger drain/purify pressure under real tanking (including the logout/switch consequence), and Ködszál ripple delivery across regions;
- Paladin Eskü/Meggyőződés role feel, beacon echo across regions, Verdict cadence and Megszentelt Föld area pressure;
- Demon Hunter load-band risk feel (overload fragility in real fights), fragment/mobility weave and the two-Sigil tank rotation;
- death/quit/kick/disable cleanup and reconnect reconstruction;
- personal Kürt/Fiola loss, death retention, external-container transfer attempts, foreign copy and duplicate-copy behavior;
- real TTK/healing/CC/party balance. Unit tests prove invariants, not final balance.

Hosted GitHub Actions that fail with `steps=null` are runner/credit infrastructure failures, not evidence of a code failure. Keep the PR draft and rely on a full local or actually-executed runner validation before merge.

## Backup and restore

Back up the complete Profile v2 directory, quarantine evidence, respec journal and currency store together.
Restore them as one consistent set. Restoring legacy class/spec PDC is not a supported rollback.
