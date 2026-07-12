# IceSMP — teljes kód-review (2026-07-12)

> A teljes kódbázis (`298` Java-fájl, ~`45 000` sor) végigolvasva. A rendszer magját
> (`IceSMPCore`, a perzisztencia/session SPI-k, a `Spell`/`BaseSpell`/`ConfiguredSpell`
> spell-motor, az `AbilityCatalystListener` cast-pipeline) közvetlenül; a többi csomagot
> csomagonként, tényleges fájl-olvasással. Ez a dokumentum a talált eltéréseket rögzíti.
>
> **Összkép:** a kódbázis Folia-fegyelme kiemelkedő — a `QuestManager` (volatile copy-on-write)
> és a `TerritoryManager`/`ClaimManager` (lock-free chunk-index) referencia-minta. Nincs
> `Bukkit.getScheduler()`, nincs szinkron `teleport(...)`, a kill-reward listenerek helyesen
> hopolnak a gyilkos ütemezőjére, a GUI-k nem szivárogtatnak admin-jogot, és egyetlen állapotos
> spell sem hiányos `clearPlayerState`-tel. Az alábbiak pontszerű finomítások.

## Ebben a commitban javítva

| Hely | Hiba | Javítás |
|---|---|---|
| `ProfessionCommand.handleBlueprint` | Folia: cél-inventory írása hop nélkül (a testvér `handleAddXp` hopol) | `target.getScheduler().run(...)` hop |
| `RelicCommand.handleGive` → `RelicManager.giveRelic` | Folia: cél-inventory írása hop nélkül | +`plugin` param, `target.getScheduler().run(...)` hop |
| `SpecCommand.handleReset` | Folia: cél-PDC írása (resetSpecializations) hop nélkül | +`plugin` param, hop |
| `FactionSetSubcommand` (DARK) | Folia: cél-PDC írása (`sealDarkPact`) hop nélkül | +`plugin` param (FactionCommandon át), hop |
| `PetManager` | per-player map leak: `combatTargets` nem takarul kilépéskor | implementálja a `PlayerStateCleanup`-ot, regisztrálva a session-cleanupban |
| `RitualManager` | per-player `cooldowns` nested map leak | implementálja a `PlayerStateCleanup`-ot, regisztrálva |
| `RitualListener` | per-player `debounce` map leak (nincs quit-handler) | quit/kick handler törli a bejegyzést |
| `ConfigManager.configuration` | reload-láthatóság: nem `volatile`, több régió-szálról olvasva | `volatile` |
| `SunDanceSpell.recipeCachePopulated` | statikus cache-flag nem `volatile`, kasztolók versenghetnek | `volatile` |
| `RelicCommand` (egész fájl) | angol fallback-szövegek (konvenció: minden szöveg magyar) | magyarra fordítva |

> **Fordítás-ellenőrzés:** Gradle a sandboxban nem éri el a repókat, ezért a `javac -sourcepath`
> módszerrel (`config/CLAUDE.md`) ellenőrizve: a módosított fájlok egyetlen strukturális hibát
> sem adnak — minden fordítási hiba a hiányzó külső jarokból (Bukkit/Paper/Adventure) ered.

## Nyitott találatok (valós build melletti követésre)

### Magas — Folia kereszt-entitás érintés hop nélkül
- **`MetelytepoRelicListener` (Justice / Honor Eye).** A `handleHonorEye` **100 blokkos** sugárban
  (`player.getNearbyEntities`) közvetlenül `addPotionEffect`/`freezeUndead`-el több régiót átfedő
  entitásokon; a `handleJustice`/`onRelicDamage` a támadó↔célpont közül mindig az egyik oldalt a
  másik szálán érinti. **Kényes javítás** (a relic-harc két belépési pontja invertálja a
  szerepeket) — entitásonkénti `getScheduler()` hop kell, körültekintéssel. *(Szándékosan nem
  javítottam automatikusan.)*
- **`WorldBossListener` / `WildHuntListener`.** A haldokló mob szálán adják át a `killer`-t a
  managernek hop nélkül, szemben minden más kill-reward listenerrel. Meg kell erősíteni, hogy a
  `WorldBossManager.handleBossDeath` / `WildHuntManager.onSlain` valóban a gyilkos
  PDC-jét/inventoryját érinti-e; ha igen, `killer.getScheduler()` hop kell.
- **`HudManager.partyMemberLine`.** A néző szálán olvassa a party-tagok `getHealth()`/
  `MAX_HEALTH` mezőit (cross-region). Try/catch védi, de architekturális a rendezése (a HUD
  szinkron építi a sorokat) — külön mérlegelést igényel.

### Közepes
- **`QuestBuilderListener` chat-capture** — a mező-kitöltő prompt nem jár le; ha az admin
  elnavigál kitöltés nélkül, minden további chat-üzenete elnyelődik és a questbe kerül. Idő- vagy
  esemény-alapú lejárat kellene.
- **Config-cache láthatóság** — `MobScalingManager` (skálázási mezők) és
  `CraftingRestrictionManager` (`enabled`, `notifyCooldownMillis`) `load()`-ban beállított,
  nem-`volatile` mezőket olvas több régió-szálról (mint a most javított `ConfigManager`).
- **`CommunityGoalManager.recordContribution`** — minden hozzájáruló eseményre szinkron
  `saveAtomic` lemezírás (a hot path-on); a `CurrencyManager` debounce-olt mintája ajánlott.

### Alacsony
- **`StatsManager.Stat` / `ParkourManager.Course`** — a `ConcurrentHashMap`-en belüli mutálható
  objektum-mezők nem `volatile`-ek (láthatóság, elméletileg tört `double` olvasás a ranglistán).
- **`SinListener`** — a bounty-broadcast a hop előtt olvassa a `killer.getName()`-et.
- **`ClaimProtectionListener`** — nincs generikus `HangingBreakEvent` (robbanás) handler, míg a
  `TerritoryProtectionListener`-ben van (aszimmetria).
- **`HideSpell`** — a `teleportAsync` eredménye nincs ellenőrizve a páncél-elrejtés előtt (a
  `BlinkSpell`/`ShadowstepSpell` gate-el a `thenAccept`-tel).
- **Katalizátor újra-igénylés** (`JobGUIListener`) — csak az inventoryt nézi; ládába/bankba
  eltéve duplázható (kis hatás, nem kereskedhető item).
- **Holder `getInventory()` szerződés** — a `gui/*Holder` osztályok háromféleképp kezelik a
  set előtti hívást (exception / dummy / `null`); a `null`-osak latens NPE-kockázatot hordoznak.
- **`RelicItemFactory`** — egy relic stat-rebalance-e reflexióval, több csendes fallbackkel
  (törékeny, ha a Paper API szignatúrák változnak).
- **`DailyQuestManager.daily`/`weekly`** — bájtra azonos privát segédek (duplikáció).
