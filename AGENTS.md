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
  - `SpellRegistry` -> holds 15 registered spells (DoubleJumpSpell, FriendshipSpell, FeatherfootSpell, AngryChickenSpell, InnerFocusSpell, RootSpell, WisplightSpell, FeastSpell, RainDanceSpell, SunDanceSpell, ArmamentSpell, ConfusionSpell, HideSpell, GustSpell, LuckyStarSpell); populated in `IceSMPCore` constructor via explicit `spellRegistry.register(new ...Spell(...))` calls; spells define cost type (HUNGER or XP) and cost amount.
  - `SpellbookItemFactory` -> creates spellbook items with `is_spellbook` and `unique_id` PDC tags; player spell state uses `selected_spell_index` + `cd_*` cooldown keys.
  - `RelicManager` -> loads relic cosmetics/triggers from `config.yml` (relics.definitions.*) on top of a hardcoded relic seed (`metelytepo`); runtime persistence hook is `save()` (currently no-op).
  - `RelicCooldownService` -> manages in-memory relic cooldowns per player/relic/trigger; `isOnCooldown()`, `startCooldown()`, `getRemainingMillis()`, `clearPlayer()` for session cleanup.
  - `MetelytepoManager` -> player PDC sinner flag (`is_sinner`) + in-memory runtime cooldown/state maps; handles "Mételytépő" relic special mechanics.
- Commands are registered in code (not in `paper-plugin.yml`) via `plugin.registerCommand(...)` inside `IceSMPCore.registerCommands()`.
- Command handlers are routers or thin executors; business logic stays in managers/subcommands (see `commands/currency/*`, `commands/faction/*`, `commands/job/*`, plus direct handlers in `commands/BankCommand.java`, `commands/ProfileCommand.java`, `commands/SinnerCommand.java`, `commands/RelicCommand.java`).
- Item-backed systems rely on PDC tags: currency uses `currency_type` (`items/CurrencyItemFactory.java`); relics use `relic_id`, `relic_owner`, `relic_created_at` (`items/RelicItemFactory.java`); spellbooks use `is_spellbook` + `unique_id` (`items/SpellbookItemFactory.java`) and player spell state uses `selected_spell_index` + `cd_*` keys (`listeners/SpellbookListener.java`).
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
- Prefer `MessageManager` + `messages.yml` for player-facing text (examples: `messages.currency-help-*`, `messages.faction-help-*`, `messages.job-help-*`, `messages.relic-help-*`, `messages.spellbook.*`, `messages.system.*`, `messages.admin.icesmp.reload.*`).
- Color formatting uses `TextUtil.color(...)` (Adventure serializer bridge), not deprecated Bukkit `ChatColor`.
- Enums accept both internal IDs and localized names (`FactionType.fromInput`, `CurrencyType.fromInput`).
- Safety listeners intentionally block crafting with tagged custom items (`CurrencyCraftListener`, `RelicCraftSafetyListener`).
- Inventory refresh patterns are system-specific: currency items refresh sync on next tick after click/drag (`CurrencyItemRefreshListener`), while relic visuals refresh on join (`RelicItemRefreshListener`).
- **Spell system:** Each spell specifies cost type (`SpellCostType.HUNGER` or `SpellCostType.XP`) and cost amount; spells with cooldown `>= 60s` persist via PDC keys (`cd_*`), shorter cooldowns stay in-memory (`SpellbookListener` line 41-50); all spell task cleanup is centralized in `PlayerSessionCleanupListener`.
- **Job progression:** Jobs use level-based XP with `JobManager` managing primary/secondary slots; `JobGUI` provides in-game class selection UI; spell unlocks are per-player via PDC `unlocked_spells` key.
- **Relic system:** Relic triggers (`RelicTrigger` enum: `RIGHT_CLICK_AIR`, `RIGHT_CLICK_BLOCK`) dispatch through `RelicTriggerListener`; cooldowns managed per player/relic/trigger by `RelicCooldownService` (always call `clearPlayer()` on session cleanup).
## Integration points and gotchas
- External API surface is intentionally thin: `compileOnly(dev.folia:folia-api)` (via `libs.folia-api`); no DB driver dependency right now.
- `RelicAbilityRegistry` exists, but no abilities are registered yet; trigger configs referencing `ability-id` will no-op with warning.
- `RelicManager.save()` is currently a no-op placeholder; relic ownership/timers are not persisted to a dedicated relic data file yet.
- Spell cooldown persistence is split in `SpellbookListener`: cooldowns for spells with `cooldown >= 60s` are persisted to player PDC via `cd_*` keys, shorter cooldowns stay in-memory.
- Per-player volatile state cleanup is centralized in `listeners/PlayerSessionCleanupListener` (quit/kick + plugin disable loop in `IceSMPCore.disable()`).
- `run/` contains mutable runtime artifacts (worlds, logs, plugin data); treat it as diagnostics/runtime state, not source of truth.
- README is partly roadmap-oriented; prefer source files above for current behavior.
## When adding features
- Wire new systems through `IceSMPCore` (construct manager -> `load()` in `enable()` -> `save()` in `disable()` if persistent).
- For new commands, follow existing patterns: router/subcommand split for grouped domains (`commands/currency`, `commands/faction`, `commands/job`) and thin single-class handlers for focused commands (`BankCommand`, `ProfileCommand`, `SinnerCommand`, `RelicCommand`), always with `suggest(...)` tab-complete coverage.
- For new item mechanics, add/validate PDC keys in the relevant `*ItemFactory`, then guard against crafting/exploits in listeners.
- For new spells, implement `spells/Spell`, register in `IceSMPCore` via `spellRegistry.register(...)`, and ensure any volatile/static spell state is cleared through the existing cleanup hooks.
- For new GUI elements, place factory/holder classes in `gui/` and register listeners in `IceSMPCore.registerListeners()`; use `ProfileGUI.closeAll()` pattern for centralized cleanup.
- **Folia-specific:** Always use null-checks after delayed task scheduling (e.g., `if (player == null || !player.isValid())`); use `entity.isValid()` before entity operations in tasks; never schedule async tasks.

