# AGENTS Guide for IceSMP
## Project snapshot
- Folia-based plugin for Minecraft `1.21.11`, Java `21`, Gradle wrapper build (`build.gradle.kts`, `gradle/libs.versions.toml`).
- **Folia-compatible:** plugin loads with `folia-supported: true` in `paper-plugin.yml`; all scheduler tasks are sync (no async).
- Entry points are declared in `src/main/resources/paper-plugin.yml`: `IceSMP` + `IceSMPBootstrap` + `IceSMPLoader`.
- Runtime orchestration lives in `src/main/java/hu/taliann/icesmp/core/IceSMPCore.java`.
## Architecture and data flow
- `IceSMP.onEnable()` builds `IceSMPCore`; `IceSMPCore.enable()` loads managers, then registers listeners and commands.
- Manager layer owns state and persistence:
  - `ConfigManager` -> loads `config.yml` from plugin data folder.
  - `MessageManager` -> wraps config messages, provides localized text via `MessageManager.getMessage(key, ...args)`.
  - `CurrencyManager` -> `currency-balances.yml` in plugin data folder; multi-currency with `CurrencyType` enum (RED, BLUE, NEUTRAL).
  - `FactionManager` -> `factions.yml` in plugin data folder; maps players to `FactionType` enum.
  - `JobManager` -> player PDC keys (`job_primary`, `job_secondary`, `*_xp`, `unlocked_spells`) rather than a plugin YAML file; manages class progression and spell unlock state.
  - `SpellRegistry` -> holds 120 registered spells: 23 handcrafted ones registered in the `IceSMPCore` constructor plus the expansion pools defined declaratively in `spells/SpellCatalog` (built from `ConfiguredSpell` builder, `ProjectileBurstSpell`, `BlinkSpell` and bespoke classes); spells define cost type (HUNGER or XP) and cost amount; every class and specialization has its own unique unlock list (no spell appears in two lists).
  - `CatalystItemFactory` -> creates class-themed Ability Catalyst items with `is_ability_catalyst` and `unique_id` PDC tags; player spell state uses `selected_spell_index` + `cd_*` cooldown keys.
  - `RelicManager` -> loads relic cosmetics/triggers from `config.yml` (relics.definitions.*) on top of a hardcoded relic seed (`metelytepo`); persists singleton relic ownership + last-seen timestamps to `relics.yml` (`save()`/`loadOwnerships()`); join-time inactivity sweep removes expired relics (`relics.inactivity.*` config, `RelicInactivityListener`).
  - `RelicCooldownService` -> manages in-memory relic cooldowns per player/relic/trigger; `isOnCooldown()`, `startCooldown()`, `getRemainingMillis()`, `clearPlayer()` for session cleanup.
  - `MetelytepoManager` -> player PDC sinner flag (`is_sinner`) + in-memory runtime cooldown/state maps; handles "Mételytépő" relic special mechanics.
- Commands are registered in code (not in `paper-plugin.yml`) via `plugin.registerCommand(...)` inside `IceSMPCore.registerCommands()`.
- Command handlers are routers or thin executors; business logic stays in managers/subcommands (see `commands/currency/*`, `commands/faction/*`, `commands/job/*`, plus direct handlers in `commands/BankCommand.java`, `commands/ProfileCommand.java`, `commands/SinnerCommand.java`, `commands/RelicCommand.java`).
- Item-backed systems rely on PDC tags: currency uses `currency_type` (`items/CurrencyItemFactory.java`); relics use `relic_id`, `relic_owner`, `relic_created_at` (`items/RelicItemFactory.java`); ability catalysts use `is_ability_catalyst` + `unique_id` (`items/CatalystItemFactory.java`) and player spell state uses `selected_spell_index` + `cd_*` keys (`listeners/AbilityCatalystListener.java`).
- GUIs: `ProfileGUI` (read-only profile display with book factory); `JobGUI` + `JobGUIHolder` (class/job selection and spell management).
## Developer workflows (verified)
- List available tasks:
```powershell
Set-Location "C:\Users\csikm\Desktop\IceSMP"
.\gradlew.bat tasks --all
```
- Core loops you will actually use:
```powershell
Set-Location "C:\Users\csikm\Desktop\IceSMP"
.\gradlew.bat build
.\gradlew.bat test
.\gradlew.bat runServer
```
- `runServer` comes from `xyz.jpenilla.run-paper`; it uses the local `run/` directory for server state/logs.
- **Folia-specific testing:** All tasks are sync; verify new code doesn't use `runTaskAsynchronously()` (not supported). Ensure null-checks when accessing players/entities delayed after task scheduling (Folia may relocate them across regions).
## Codebase-specific conventions
- Prefer `MessageManager` + `messages.yml` for player-facing text (examples: `messages.currency-help-*`, `messages.faction-help-*`, `messages.job-help-*`, `messages.relic-help-*`, `messages.catalyst.*`, `messages.system.*`, `messages.admin.icesmp.reload.*`).
- Color formatting uses `TextUtil.color(...)` (Adventure serializer bridge), not deprecated Bukkit `ChatColor`.
- Enums accept both internal IDs and localized names (`FactionType.fromInput`, `CurrencyType.fromInput`).
- Safety listeners intentionally block crafting with tagged custom items (`CurrencyCraftListener`, `RelicCraftSafetyListener`).
- Inventory refresh patterns are system-specific: currency items refresh sync on next tick after click/drag (`CurrencyItemRefreshListener`), while relic visuals refresh on join (`RelicItemRefreshListener`).
- **Spell system:** Each spell specifies cost type (`SpellCostType.HUNGER` or `SpellCostType.XP`) and cost amount; spells with cooldown `>= 60s` persist via PDC keys (`cd_*`), shorter cooldowns stay in-memory (`AbilityCatalystListener` line 41-50); all spell task cleanup is centralized in `PlayerSessionCleanupListener`.
- **Job progression:** Jobs use level-based XP with `JobManager` managing primary/secondary slots; `JobGUI` provides in-game class selection UI; spell unlocks are per-player via PDC `unlocked_spells` key.
- **Relic system:** Relic triggers (`RelicTrigger` enum: `RIGHT_CLICK_AIR`, `RIGHT_CLICK_BLOCK`) dispatch through `RelicTriggerListener`; cooldowns managed per player/relic/trigger by `RelicCooldownService` (always call `clearPlayer()` on session cleanup).
## Integration points and gotchas
- External API surface is intentionally thin: `compileOnly(dev.folia:folia-api)` (via `libs.folia-api`); no DB driver dependency right now.
- `RelicAbilityRegistry` exists, but no abilities are registered yet; trigger configs referencing `ability-id` will no-op with warning.
- `RelicManager.save()` persists relic ownership records to `relics.yml`; expired (default 14+ day inactive) relics are removed from the holder's inventory on join with a smoke effect.
- `MobScalingManager` + `MobScalingListener` implement distance-based mob leveling (`mob-scaling.*` config); levels are tagged on entities via the `mob_level` PDC key.
- `CraftingRestrictionManager` + `JobCraftRestrictionListener` enforce job/profession level based crafting rules (`crafting-restrictions.*` config) for both crafting table and smithing table results.
- `JobType` has 4 base classes (wizard, warrior, archer, assassin). `JobManager.setXp` auto-unlocks spells mapped under `classes.<jobId>.spell-unlocks` and fires the XP-change hook (`setXpChangeHook`, wired to `SpecializationManager`); class XP comes from kills via `ClassXpListener`.
- `SpecializationType` (2 specs per class) + `ProfessionSpecializationType` + `SpecializationManager` (`/spec`): only the primary class specializes (at `classes.specialization.required-level`); NECROMANCER is the wizard's dark spec requiring the DARK faction + sinner mark; spec spell unlocks live under `specializations.<specId>.spell-unlocks`.
- Dark pact rule: joining the DARK faction requires the sinner mark and seals `dark_pact` PDC (sinner becomes permanent — `MetelytepoManager.clearSinner` returns false); enforced in `FactionJoinSubcommand`/`FactionSetSubcommand`.
- `TalentManager` (`/talent`): class and profession point pools from levels (`talents.*` config); attribute effects applied as player attribute modifiers (idempotent re-apply on join via `TalentAttributeListener`), XP-bonus effects queried by the XP listeners.
- `TerritoryManager` + `/territory` admin command + `TerritoryListener`: circular faction claims persisted to `territories.yml`, capital per faction, action-bar border notify (`territory.notify`), optional build protection (`territory.protection`).
- `ProfessionManager` (PDC keys `profession`, `profession_xp`) + `ProfessionXpListener` + `/profession` command implement grind professions (armorer, miner, farmer, fisherman).
- `FactionType`/`CurrencyType` include DARK; `FactionPassiveListener` applies passive faction bonuses (`factions.passives.*` config).
- `ExchangeRateService` computes supply-driven dynamic exchange rates (`currency.dynamic-exchange.*` config); `/currency rates` shows live values; `CurrencyManager.getTotalSupply` sums banked balances.
- Spell cooldown persistence is split in `AbilityCatalystListener`: cooldowns for spells with `cooldown >= 60s` are persisted to player PDC via `cd_*` keys, shorter cooldowns stay in-memory.
- Per-player volatile state cleanup is centralized in `listeners/PlayerSessionCleanupListener` (quit/kick + plugin disable loop in `IceSMPCore.disable()`).
- `run/` contains mutable runtime artifacts (worlds, logs, plugin data); treat it as diagnostics/runtime state, not source of truth.
- README.md is the player/admin-facing overview; TECHNICAL.md holds the full technical reference (commands, permissions, config, persistence, listener/spell tables). Prefer source files for exact current behavior.
## When adding features
- Wire new systems through `IceSMPCore` (construct manager -> `load()` in `enable()` -> `save()` in `disable()` if persistent).
- For new commands, follow existing patterns: router/subcommand split for grouped domains (`commands/currency`, `commands/faction`, `commands/job`) and thin single-class handlers for focused commands (`BankCommand`, `ProfileCommand`, `SinnerCommand`, `RelicCommand`), always with `suggest(...)` tab-complete coverage.
- For new item mechanics, add/validate PDC keys in the relevant `*ItemFactory`, then guard against crafting/exploits in listeners.
- For new spells, implement `spells/Spell`, register in `IceSMPCore` via `spellRegistry.register(...)`, and ensure any volatile/static spell state is cleared through the existing cleanup hooks.
- For new GUI elements, place factory/holder classes in `gui/` and register listeners in `IceSMPCore.registerListeners()`; use `ProfileGUI.closeAll()` pattern for centralized cleanup.
- **Folia-specific:** Always use null-checks after delayed task scheduling (e.g., `if (player == null || !player.isValid())`); use `entity.isValid()` before entity operations in tasks; never schedule async tasks.

