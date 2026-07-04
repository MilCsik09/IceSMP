# AGENTS Guide for IceSMP

## Project snapshot
- Folia-based plugin for Minecraft `1.21.11`, Java `21`, Gradle wrapper build (`build.gradle.kts`, `gradle/libs.versions.toml`).
- **Folia-compatible:** `folia-supported: true` in `paper-plugin.yml`; every task runs on region/entity schedulers (never `Bukkit.getScheduler()`, never async entity access).
- Entry points: `IceSMP` + `IceSMPBootstrap` + `IceSMPLoader` (declared in `paper-plugin.yml`); runtime orchestration in `core/IceSMPCore.java` (manager construction → `load()` → listeners → commands → schedulers; `save()`/cleanup in `disable()`).
- Soft dependencies (`compileOnly`, runtime-optional): **PlaceholderAPI** (`integration/IceSMPPlaceholders`, registered reflectively from `IceSMPCore.registerPlaceholders()`; exposes `%icesmp_...%`) and **LibsDisguises** (`integration/DruidDisguise` reflective bridge for Druid form visuals; `isTransitive = false`).
- Docs: `README.md` (overview) • `PLAYER_GUIDE.md` + `docs/player-guide/` (player manual, includes full spell tables) • `PLAYTEST.md` (tester handbook incl. permission nodes and admin triggers) • `docs/ARCHITECTURE.md` (technical reference, **Folia rules in section 4**) • `ROADMAP.md` (open work). Prefer source files for exact current behavior.

## Domain snapshot (current, verified against code)
- **13 classes** (`data/JobType`), **31 specializations** (`data/SpecializationType`), ~390 spells. **One class per player** (the secondary-class system was removed); the choice is permanent — admin reset via `/class admin resetclass <player>` (wipes class + spec + unlocked spells; also clears legacy `job_secondary*` PDC).
- **Spell costs are hybrid** (`ResourceManager.usesResource`): HEALTH-cost spells stay on HP (blood magic); XP ≥ `spells.resource.xp-ritual-threshold` stays XP (rituals/ults); HUNGER ≥ `spells.resource.hunger-heavy-threshold` stays hunger (heavy physical); everything else spends the per-class **resource pool** (Mana/Düh/Energia…, lazy-regenerating, shown on the HUD sidebar). `spells.resource.enabled: false` reverts everything to declared HUNGER/XP/HEALTH costs.
- **Faction passives** (`FactionPassiveListener`): RED fire/lava/hot-floor immunity; BLUE freeze+drowning immunity + hunger-slow; NEUTRAL fall-damage immunity + ignored by non-hostiles/endermen + tax-exempt (sneak-invisibility was removed for balance); DARK wither immunity + ignored by undead (incl. Wither) — strongest PvE perk, offset by the permanent sinner mark.
- **HUD** (`HudManager`): sidebar + faction-coloured tab names + shared boss bars. `hud.sidebar-enabled` / `hud.tablist-enabled` config toggles exist for coexistence with an external scoreboard plugin (TAB); the PAPI bridge reads only the thread-safe per-player `HudSnapshot` (refreshed on each player's region thread).

## Build & verify
```bash
./gradlew build      # plugin jar -> build/libs
./gradlew runServer  # local test server (run/ directory)
```
- In sandboxed environments where Gradle cannot reach the repos, compile against the cached server libraries instead: `javac -d <out> -cp "$(find run/libraries -iname '*.jar' | tr '\n' ':')" <sources>` — exclude `integration/IceSMPPlaceholders.java` if the PlaceholderAPI jar is unavailable locally (it compiles in the real build).
- **Always compile-verify before pushing.** The full source set must produce 0 errors.

## Folia rules (CRITICAL — see docs/ARCHITECTURE.md §4)
- Events fire on the event entity's region thread. **Mutating (or reading PDC/inventory of) any OTHER entity — including `killer.sendMessage` — requires hopping to that entity's scheduler:** `target.getScheduler().run(plugin, task -> {...}, null)`. The kill-reward listeners, admin target-player subcommands, MarketGUI seller notice and SiegeWeapon remote explosion all follow this pattern — copy it.
- Remote locations/blocks: `getRegionScheduler().run(plugin, location, task)`. Global ticks: `getGlobalRegionScheduler()`, hopping per-player/per-entity for any entity access.
- Keep block scans small and region-local; use `teleportAsync`; entity tasks that outlive the entity need retired-callback awareness.

## Codebase conventions
- Player-facing text is Hungarian, via `MessageManager` + `messages.yml` keys with inline defaults.
- Managers own state + persistence (YAML via `YamlStore.saveAtomic` or player PDC). Per-player volatile state implements `PlayerStateCleanup` / is cleared in `PlayerSessionCleanupListener` — UUID-keyed maps must not leak.
- Commands are registered in `IceSMPCore.registerCommands()` (not the manifest): router + subcommand split for grouped domains (`commands/job|faction|currency|bank`), thin single classes otherwise; always provide tab-complete.
- New spells: prefer the declarative `ConfiguredSpell.builder(...)` in `spells/SpellCatalog` + an unlock entry in `config/classes.yml` (`classes.<id>.spell-unlocks` or the spec's list); dedicated classes only for genuinely stateful spells (those must override `clearPlayerState`).
- Item mechanics ride on PDC tags via the `items/*ItemFactory` classes, guarded by craft-safety listeners.
- Spell cooldowns ≥ 60s persist via `cd_*` PDC keys; shorter ones are in-memory (`AbilityCatalystListener`).
- Config lives in `src/main/resources/config/*.yml` (ConfigManager merges all of them); numeric guide claims (XP rates, thresholds) must match those files.

## When adding features
- Wire through `IceSMPCore` (construct → `load()` in `enable()` → `save()` in `disable()` for persistent stores).
- Update the docs with every gameplay change: `PLAYER_GUIDE.md` (+ the relevant `docs/player-guide/` page), `PLAYTEST.md` checklist, and `README.md` if the feature list changes.
- Commit messages end with the `Co-Authored-By` + `Claude-Session` trailers used in this repo's history; push to the designated feature branch only.
