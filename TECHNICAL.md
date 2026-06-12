# IceSMP — Technikai dokumentáció

Ez a dokumentum a plugin teljes technikai referenciája: architektúra, modulok, parancsok,
jogosultságok, konfiguráció, adattárolás és fejlesztői útmutató.

- Közérthető áttekintés: [README.md](README.md)
- Tervek, ötletek: [ideas.md](ideas.md)
- Agent/fejlesztői gyorsjegyzet: [AGENTS.md](AGENTS.md)

---

## 1. Architektúra

### Életciklus

```
IceSMP.onEnable()
  └─ new IceSMPCore(plugin)        // minden manager felépítése, spellek regisztrálása
       └─ IceSMPCore.enable()
            ├─ configManager.load()        — config.yml betöltés (copyDefaults)
            ├─ messageManager.reload()     — messages.yml betöltés
            ├─ currencyManager.load()      — currency-balances.yml
            ├─ factionManager.load()       — factions.yml
            ├─ relicManager.load()         — relic definíciók + relics.yml ownership
            ├─ mobScalingManager.load()    — mob-scaling config cache
            ├─ craftingRestrictionManager.load() — craft szabályok cache
            ├─ territoryManager.load()     — territories.yml
            ├─ factionTreasuryManager.load() — treasury.yml
            ├─ registerListeners()
            ├─ registerCommands()          — Paper BasicCommand API (kódból, nem paper-plugin.yml-ből)
            └─ scheduleTaxCollection()     — global region scheduler (Paper + Folia)

IceSMPCore.disable()
  ├─ minden online játékos session-állapotának takarítása (PlayerSessionCleanupListener)
  ├─ ProfileGUI.closeAll()
  └─ currency/faction/relic/territory/treasury save() + adó-task leállítása
```

- **Belépési pontok** (`paper-plugin.yml`): `IceSMP` (fő osztály), `IceSMPBootstrap`, `IceSMPLoader`.
- **Folia-kompatibilitás:** `folia-supported: true`; minden ütemezett feladat szinkron
  (nincs `runTaskAsynchronously`); entitás-módosítás mindig a tulajdonos régiószálon
  (event handlerben) történik; a teleport `teleportAsync`-kal megy (`ShadowstepSpell`).
- **Dependency injection:** kizárólag konstruktor-injektálás; egyetlen kivétel a
  `JobManager.setXpChangeHook(...)` setter, amely a `JobManager ↔ SpecializationManager`
  körkörös függést oldja fel.

### Csomagstruktúra

| Csomag | Tartalom |
|---|---|
| `core` | `IceSMPCore` — lifecycle, wiring |
| `data` | Enumok és rekordok: `FactionType`, `CurrencyType`, `JobType`, `ProfessionType`, `SpecializationType`, `ProfessionSpecializationType`, `CraftingRule`, `Territory`, `Wallet`, `RelicOwnership` (relics csomagban) |
| `managers` | Üzleti logika és állapot (lásd 2. fejezet) |
| `listeners` | Bukkit/Folia eseménykezelők — vékonyak, managerbe delegálnak |
| `commands` | Paper `BasicCommand` implementációk; nagy domainekhez router + subcommand split (`commands/currency`, `commands/faction`, `commands/job`) |
| `spells` | `Spell` interfész, `BaseSpell` ősosztály, 124 spell (kézzel írt osztályok + `SpellCatalog` deklaratív definíciók a `ConfiguredSpell`/`ProjectileBurstSpell`/`BlinkSpell` építőelemekből), `SpellTargetingUtil` |
| `relics` | Relic framework: definíciók, triggerek, ability registry, ownership rekord |
| `items` | Item factory-k PDC tagekkel: `CurrencyItemFactory`, `RelicItemFactory`, `CatalystItemFactory` |
| `gui` | `ProfileGUI`, `JobGUI` + holderek, `ProfileBookFactory` |
| `utils` | `MessageManager`, `TextUtil`, `ExperienceUtil` |

---

## 2. Manager referencia

| Manager | Felelősség | Perzisztencia |
|---|---|---|
| `ConfigManager` | `config.yml` elérés fallback-ekkel | config.yml |
| `MessageManager` | Lokalizált üzenetek (`messages.*` kulcsok, legacy `&` és MiniMessage formátum) | messages.yml |
| `CurrencyManager` | Többvalutás egyenlegek, befizetés/kivét/utalás/váltás, item tokenek, `getTotalSupply()` | currency-balances.yml |
| `EconomyEventManager` | Heti kereslet-sokk: véletlen valuta base-value szorzó időkorláttal; global scheduler tick | economy-event.yml |
| `MarketManager` | Piactér: listázás kézből, vásárlás banki egyenlegből, eladási díj (money sink) | market.yml |
| `ExchangeRateService` | Kínálat-alapú dinamikus árfolyam: `érték = base × clamp((ref/supply)^elaszticitás, min, max)`; `getRate(from,to) = value(from)/value(to)` | — (configból számol) |
| `FactionManager` | Játékos → frakció hozzárendelés | factions.yml |
| `FactionTreasuryManager` | Frakciókasszák (adomány + időszakos állampolgári adó, money sink); a tax a global region scheduleren fut | treasury.yml |
| `KingManager` | Királyválasztás (szavazás, min-votes, ciklus-reset) és uralkodói jogok (kassza-kivét, raid) | kings.yml |
| `QuestManager` | Config-vezérelt küldetések: 6 objective-típus, lánc/kaszt/frakció/szint feltételek, jutalmak (class-xp, valuta, spell, cleanse-sins) | játékos PDC |
| `RaidManager` | Raid életciklus: hirdetés (nevezési díj), bűn-mentes hadi PvP, kill-számolás, hadizsákmány | memória |
| `JobManager` | Kasztok (elsődleges/másodlagos), XP és szint (progresszív görbe: az n. szintlépés ára `base-xp + (n-1)*increment`, max 50), spell unlock lista, szint-alapú auto-unlock (`classes.<id>.spell-unlocks`), frakció-követelmény ellenőrzés, XP-change hook | játékos PDC |
| `SpecializationManager` | Kaszt- és szakma-specializációk: feltétel-ellenőrzés (szint, frakció, sinner), kiválasztás, spec spell unlock (`specializations.<id>.spell-unlocks`) | játékos PDC |
| `TalentManager` | Két talentpont-tár (kaszt/szakma), pontköltés, WoW-szerű talent-kötések (`requires-job`/`requires-spec`/`requires-profession`), respec utáni pont-visszatérítés, attribútum módosítók idempotens alkalmazása, XP-bónusz effektek lekérdezése | játékos PDC |
| `ProfessionManager` | WoW-szerű szakmák: 1 gyűjtögető + 1 készítő főszakma-slot, másodlagosak mindenkinek; szakmánkénti XP (megmarad szakmaváltáskor is), progresszív szintgörbe | játékos PDC |
| `CraftingRestrictionManager` | Config-vezérelt craft szabályok: kaszt- és/vagy szakma-követelmény anyagonként; üzenet-throttle | — |
| `SpellRegistry` | A 124 regisztrált spell nyilvántartása id szerint | — |
| `RelicManager` | Relic definíciók (config + beépített seed), singleton tulajdonjog, 14 napos inaktivitás-lejárat, belépéskori sweep | relics.yml |
| `RelicCooldownService` | Per játékos/relic/trigger cooldownok | memória |
| `MetelytepoManager` | Mételytépő mechanikák: sinner flag, **dark pact** (örök sinner), Justice/Honor Eye képességek, fagyasztás | játékos PDC + memória |
| `MinionManager` | Idézett minionok gazda-jelölése (`minion_owner` PDC); a hűség-szabályokat a `MinionProtectionListener` érvényesíti | entitás PDC |
| `MobScalingManager` | Távolság-alapú mob szint: attribútum skálázás, névcímke, `mob_level` PDC | entitás PDC |
| `TerritoryManager` | Kör alakú frakcióterületek és fővárosok, `getTerritoryAt(Location)` | territories.yml |

### Kulcs-szabályok (üzleti logika)

**Quest-kapuk:** a `specializations.<spec>.required-quest` config kulcs quest-teljesítéshez
köti a spec felvételét (alapból: necromancer → `necromancer_initiation`); a vezeklés-lánc
(`penance_1..3`) zárótagjának `cleanse-sins` jutalma az egyetlen mód a sötét paktum
megtörésére (`MetelytepoManager.breakDarkPact`).

**Sötét paktum lánc:**
1. Sinner jelölést a Mételytépő (`MetelytepoManager.markAsSinner`) vagy admin (`/sinner set`) ad.
2. `/faction join dark` csak sinnerként engedélyezett → belépéskor `sealDarkPact()`:
   `dark_pact` PDC + sinner garantálva.
3. `clearSinner()` visszautasítja a tisztítást, ha `dark_pact` van a játékoson (örökre bűnös),
   függetlenül attól, hogy később elhagyja-e a frakciót.
4. A NECROMANCER specializáció feltétele: WIZARD elsődleges kaszt a spec-szinten + DARK
   frakció + sinner.

**Specializáció szabályok:** csak az **elsődleges** kaszt specializálódhat; egy játékosnak
max. 1 kaszt-spec és 1 szakma-spec lehet. A spec a `/spec respec <class|profession>`
paranccsal váltható vissza a saját frakcióvaluta `specializations.respec-cost` árán
(banki egyenlegből, money sink) — kaszt-respec után a spec-kötött talentek pontjai
automatikusan visszatérülnek; admin `/spec reset` ingyen töröl.

**Szakma szabályok (WoW-minta):** 1 gyűjtögető + 1 készítő főszakma választható
(`/profession join`); a másodlagos szakmák (halász, szakács) mindenkinek automatikusan
aktívak. Az XP szakmánként tárolódik (`profession_xp_<id>`), így admin szakmaváltás után
a régi szakma szintje megmarad és visszatanulható.

**Relic singleton:** `RelicManager.giveRelic` elutasítja az átadást, ha a relicnek aktív
(nem lejárt) tulajdonosa van. Lejárat: utolsó látás + `relics.inactivity.expiry-days`
(alapból 14 nap); a lejárt relic a tulajdonos belépésekor füst effekttel törlődik az
inventoryból és felszabadul.

---

## 3. Parancsok és jogosultságok

| Parancs | Aliasok | Alparancsok | Jogosultság |
|---|---|---|---|
| `/icesmp` | `ismp` | `reload` | `icesmp.admin.reload` |
| `/currency` | `money`, `eco` | `balance`, `pay`, `set`, `exchange`, `rates` | `set`: `icesmp.currency.admin` |
| `/bank` | `wallet`, `vault` | `balance`, `deposit`, `withdraw <valuta> <összeg>` | — |
| `/faction` | `f` | `join`, `leave`, `set`, `treasury [withdraw <összeg>]`, `donate <összeg>`, `king [vote/set/clear]`, `raid <frakció>` | `set`/king `set|clear`: `icesmp.faction.admin`; `treasury withdraw`: admin VAGY király; `raid`: csak király |
| `/job` | `class` | `addxp`, `setxp`, `status`, `unlockspell`, `givecatalyst`, `listspells`, `admin` | `icesmp.job.admin` (az `admin` ág: `icesmp.admin`) |
| `/profession` | `prof`, `szakma` | `join`, `info`, `list`, `set`, `clear`, `addxp` | admin ágak: `icesmp.admin.profession` |
| `/spec` | `specialization` | `list`, `choose`, `info`, `respec <class\|profession>`, `reset` | `reset`: `icesmp.admin.spec` |
| `/talent` | `talents` | `list`, `spend <class\|profession> <talent>` | — |
| `/profile` | `status`, `info` | — | — |
| `/sinner` | — | `<játékos> set\|clear\|add\|status` | `icesmp.admin` |
| `/quest` | `quests`, `kuldetes` | `list`, `info`, `accept`, `abandon`, `complete` | `complete`: `icesmp.admin.quest` |
| `/market` | `piac`, `ah` | `(browse)`, `sell <ár> [valuta]`, `cancel` | — |
| `/relic` | `relics` | `list`, `give` | `give`: `icesmp.relic.admin` |
| `/territory` | `terulet` | `setcapital`, `claim`, `remove`, `list`, `info` | `icesmp.admin.territory` |

További jogosultság: `icesmp.admin.territory.bypass` — építésvédelem megkerülése.

A parancsok a Paper Brigadier `BasicCommand` API-val, **kódból** regisztrálódnak
(`IceSMPCore.registerCommands()`), mindegyik `suggest(...)` tab-complete lefedettséggel.

---

## 4. Eseménykezelők (listeners)

| Listener | Esemény(ek) | Funkció |
|---|---|---|
| `AbilityCatalystListener` | `PlayerInteractEvent`, `PlayerAnimationEvent` | Jobb katt: cast; sneak+ütés: spell váltás; költség/cooldown pipeline |
| `SpellProjectileListener`, `SpellStateListener` | projektil/állapot események | Spell-specifikus utókezelés |
| `ClassXpListener` | `EntityDeathEvent` | Kaszt XP ölésből (+mob szint bónusz, +talent XP%, másodlagos kaszt rész) |
| `ProfessionXpListener` | `BlockBreakEvent`, `PlayerHarvestBlockEvent`, `CraftItemEvent`, `SmithItemEvent`, `EnchantItemEvent`, `InventoryClickEvent` (főzőállvány), `PlayerFishEvent`, `FurnaceExtractEvent` | Szakma XP tevékenységből a 8 szakmának (+talent XP%) |
| `JobCraftRestrictionListener` | `PrepareItemCraftEvent`, `PrepareSmithingEvent` | Tiltott craft eredmény nullázása + throttle-olt üzenet |
| `CurrencyCraftListener`, `RelicCraftSafetyListener` | `PrepareItemCraftEvent` | Tagelt itemek craft-védelme |
| `CurrencyItemRefreshListener`, `RelicItemRefreshListener` | click/join | Item vizuálok frissítése |
| `RelicInactivityListener` | `PlayerJoinEvent` | 14 napos lejárat-sweep + last-seen frissítés |
| `RelicTriggerListener` | interakció | Relic trigger dispatch (`RIGHT_CLICK_AIR/BLOCK`) |
| `MetelytepoRelicListener` | harc események | Mételytépő: sinner bélyegzés, Justice, Honor Eye |
| `MobScalingListener` | `CreatureSpawnEvent` | Mob szintezés (attribútumok + név + PDC) |
| `FactionPassiveListener` | `EntityDamageEvent`, `FoodLevelChangeEvent`, `PlayerToggleSneakEvent`, `EntityTargetLivingEntityEvent` | Frakció passzívok |
| `TalentAttributeListener` | `PlayerJoinEvent` | Talent attribútum-módosítók idempotens újra-alkalmazása |
| `TerritoryListener` | `PlayerMoveEvent` (blokk-váltásra szűrve), `BlockBreak/PlaceEvent`, `PlayerQuitEvent` | Határátlépés action bar + opcionális építésvédelem |
| `MinionProtectionListener` | `EntityTargetLivingEntityEvent` | Idézett minion soha nem támadja a gazdáját vagy a gazda másik minionját |
| `SinListener` | `PlayerDeathEvent` | Gyilkosság = +1 bűn (raid alatt a hadviselők közt: bűn helyett raid-pont); küszöbnél száműzetés |
| `ElytraRelicListener` | `EntityToggleGlideEvent`, `EntityDamageEvent` | A 4 frakció-elytra relikvia effektjei (tulajdonos + frakció ellenőrzéssel) |
| `RelicPvpTransferListener` | `PlayerDeathEvent` | Fegyver-relikviák gazdacseréje PvP-ben |
| `SoulstoneListener` | `EntityDeathEvent` | Lélekkő-drop: magas szintű skálázott mobok DARK tokent dobhatnak |
| `QuestProgressListener` | `EntityDeathEvent`, `BlockBreakEvent`, `CraftItemEvent`, `PlayerFishEvent` | Quest-haladás (a VISIT_TERRITORY a TerritoryListenerből, a REACH_LEVEL a JobManager hookból érkezik) |
| `ProfileGUIListener`, `JobGUIListener` | inventory események | GUI kattintáskezelés |
| `PlayerSessionCleanupListener` | `PlayerQuitEvent`, `PlayerKickEvent` | Központi session-állapot takarítás (minden manager `clearPlayerState`) |

---

## 5. Spell referencia

A cast pipeline: kiválasztott spell → `canCast` → költség-ellenőrzés (`HUNGER` éhségpont /
`XP` összes tapasztalatpont) → cooldown ellenőrzés → `execute(player)` → költség levonás +
cooldown indítás. A **60 mp-nél hosszabb** cooldownok PDC-be (`cd_<spellId>`) perzisztálódnak,
a rövidebbek memóriában élnek.

**Összesen 124 spell, 12 poolban (4 kaszt + 8 spec), poolonként legalább 10 egyedi képesség (a Nekromanta és a Vadmester 12-t kap az idézésekkel).**
A bővítő poolokat a `spells/SpellCatalog` definiálja deklaratívan a generikus építőelemekből:
`ConfiguredSpell` (builder: self/target/AOE célzás, sebzés, gyógyítás, ignite/fagyasztás,
villám, lökés/felrántás/odahúzás, dash, potion effekt listák, partikula+hang),
`ProjectileBurstSpell` (nyíl/szellemnyíl/tűzgolyó/széltöltet sortüzek), `BlinkSpell`
(teleportAsync-alapú villanás), plusz egyedi osztályok (Farkashívás, Méhraj, Ősi Kötelék,
Ellenméreg). A teljes pool-kiosztás és a feloldási szintek a `config.yml`
`classes.*.spell-unlocks` és `specializations.*.spell-unlocks` szekcióiban vannak.

Az alábbi tábla a kézzel írt törzs-spelleket sorolja fel (a katalógus-spellek paraméterei a
`SpellCatalog`-ban olvashatók):

| Spell id | Név | Cooldown (mp) | Költség | Alap feloldás |
|---|---|---|---|---|
| `double_jump` | Dupla Ugrás | 0 | 3 éhség | íjász 15 |
| `wisplight` | Wisplight | 0 | 1 éhség | varázsló 2 |
| `featherfoot` | Pehelykönnyű Lépte | 45 | 1 éhség | Mesterlövész spec 25 |
| `friendship` | Barátság | 45 | 4 éhség | Vadmester spec 25 |
| `multishot` | Sortűz | 45 | 5 éhség | íjász 8 |
| `angry_chicken` | Mérgező Csirke | 30 | 5 éhség | Vadmester spec 30 |
| `eagle_eye` | Sasszem | 90 | 4 éhség | íjász 3 |
| `shadowstep` | Árnyéklépés | 60 | 6 éhség | orgyilkos 5 |
| `smoke_bomb` | Füstbomba | 120 | 6 éhség | orgyilkos 12 |
| `gust` | Lökéshullám | 60 | 30 XP | harcos 15 |
| `life_drain` | Életszívás | 60 | 20 XP | Nekromanta spec 25 |
| `bone_chill` | Csontfagy | 90 | 25 XP | Nekromanta spec 30 |
| `root` | Gyökerezés | 300 | 8 éhség | varázsló 5 |
| `feast` | Lakoma | 120 | 352 XP | Berserker spec 25 |
| `armament` | Fegyverzet | 300 | 352 XP | harcos 5 |
| `inner_focus` | Belső Fókusz | 480 | 20 éhség | harcos 10 |
| `hide` | Elrejtőzés | 480 | 550 XP | Fantom spec 25 |
| `confusion` | Megzavarás | 1200 | 160 XP | varázsló 10 |
| `rain_dance` | Esőtánc | 3600 | 352 XP | varázsló 15 |
| `sun_dance` | Naptánc | 3600 | 352 XP | Elementalista spec 25 |
| `lucky_star` | Lucky Star | 0 (toggle) | XP-t éget | Elementalista spec 30 |
| `bulwark` | Bástya | 120 | 8 éhség | Védelmező spec 25 |
| `venom_strike` | Méregcsapás | 60 | 6 éhség | Méregkeverő spec 25 |

A feloldási szintek a `config.yml`-ben szabadon átírhatók (`classes.*.spell-unlocks`,
`specializations.*.spell-unlocks`). **Minden kaszt és specializáció saját, egyedi
spell-készletet tanul** — egy spell sem szerepel két feloldási listában.

### Képesség Katalizátor (a varázskönyv utódja)

A spellek használatához kaszt-tematikus katalizátor item kell (`CatalystItemFactory`,
PDC: `is_ability_catalyst` + `unique_id`):

| Kaszt | Material | Név | CMD | Váltás hangja |
|---|---|---|---|---|
| WIZARD | `ENCHANTED_BOOK` | Mágikus Kódex | 5201 | `ITEM_BOOK_PAGE_TURN` |
| WARRIOR | `GOAT_HORN` | Harci Kürt | 5202 | `ITEM_GOAT_HORN_PLAY` (rövid, pitch 2.0) |
| ARCHER | `RABBIT_HIDE` | Vadásztarsoly | 5203 | `ITEM_CROSSBOW_LOADING_START` |
| ASSASSIN | `FLINT` | Árnyékamulett | 5204 | `BLOCK_CANDLE_EXTINGUISH` |

Interakciók (`AbilityCatalystListener`): jobb katt = cast (a kecskekürt vanilla
megfújása letiltva); sneak + ütés = spellváltás kaszt-hanggal és a spell nevét +
költségét mutató action barral. Megszerzés: Job GUI katalizátor-gomb (saját igénylés,
duplikáció-védelemmel) vagy admin `/job givecatalyst`. A `CatalystCraftSafetyListener`
megakadályozza, hogy a katalizátor craft-hozzávalóként vagy kemence-üzemanyagként
elhasználódjon (FLINT/RABBIT_HIDE vanilla receptekben szerepel!).

---

## 6. Konfiguráció referencia (`config.yml`)

| Szekció | Mit vezérel |
|---|---|
| `settings` | `default-faction`, `debug` |
| `hud.profile` | Profil HUD paraméterek |
| `currency` | Alapvaluta, szimbólum, **fix** árfolyam + díj (fallback), item tokenek (anyag, model-data, név per valuta — RED/BLUE/NEUTRAL/DARK) |
| `currency.soul-drop` | Lélekkő-drop: `enabled`, `min-mob-level`, `chance-percent`, `max-amount` |
| `currency.economy-event` | Kereslet-sokk: `enabled`, `check-interval-minutes`, `chance-percent`, `duration-hours`, `min/max-multiplier` |
| `market` | `max-listings-per-player`, `fee-percent` (eladási díj = money sink) |
| `currency.dynamic-exchange` | `enabled`, `reference-supply`, `elasticity`, `min/max-multiplier`, `base-values.<VALUTA>` — a kínálat-alapú árfolyam paraméterei |
| `messages` | (örökölt, nem használt — a futásidejű üzenetforrás a `messages.yml`) |
| `factions` | Frakció nevek + `passives.*` + `tax.*` + `sins.*` + `kings.*` (min-votes, term-days, excluded) + `raid.*` (duration-minutes, entry-cost, spoils-percent, protected) |
| `relics` | `enabled`, `inactivity.*`, `weapon-relics` lista + `pvp-transfer.enabled`, üzenetek, `definitions.<id>` (vizuál + triggerek; az 5 beépített relic: metelytepo + 4 frakció-elytra) |
| `mob-scaling` | `enabled`, `blocks-per-level`, `max-level`, `health/damage-per-level`, `hostile-only`, `ignored-spawn-reasons`, `name.*` |
| `classes` | `xp.*` (per-kill, per-mob-level, secondary-share-percent, hostile-only), `leveling.*` (progresszív görbe), `specialization.required-level`, `<kaszt>.spell-unlocks` |
| `specializations` | `respec-cost` (a /spec respec ára frakcióvalutában), `<spec>.spell-unlocks` — spec-spellek kaszt-szinthez kötve |
| `talents` | `class` és `profession` tár: `points-per-levels` + `definitions.<id>` (`display-name`, `effect`, `per-rank`, `max-rank`, opcionális `requires-job`/`requires-spec`/`requires-profession`) |
| `professions` | `leveling.*` (progresszív görbe), `xp.*` (tevékenység XP a 8 szakmának), `specialization.required-level` |
| `crafting-restrictions` | `enabled`, `notify-cooldown-seconds`, `rules.<id>`: `materials` lista + `required-job`/`required-level` és/vagy `required-profession`/`required-profession-level` (minden megadott feltételnek teljesülnie kell) |
| `territory` | `notify.enabled` (action bar), `protection.enabled` (építésvédelem) |
| `quests` | `quests.<id>`: display-name, description, objective (type/count/...), requires-* feltételek, rewards (class-xp, currency, unlock-spell, cleanse-sins) |

Talent effektek: `max-health`, `movement-speed`, `attack-damage` (attribútum módosító),
`class-xp-bonus`, `profession-xp-bonus` (százalék).

`/icesmp reload` újratölti a `config.yml`-t és `messages.yml`-t. Megjegyzés: a betöltéskor
cache-elő managerek (mob scaling, craft szabályok, relic definíciók) értékei teljes újraindításnál
frissülnek garantáltan.

---

## 7. Adattárolás

### YAML fájlok (plugin adatmappa)

| Fájl | Tartalom | Író |
|---|---|---|
| `config.yml` | Konfiguráció | ConfigManager (copyDefaults) |
| `messages.yml` | Lokalizált üzenetek | MessageManager (resource másolás) |
| `currency-balances.yml` | `players.<uuid>.<VALUTA>: összeg` | CurrencyManager |
| `factions.yml` | `<uuid>: FRAKCIÓ` | FactionManager |
| `relics.yml` | `ownerships.<relicId>.owner` + `.last-seen` | RelicManager |
| `territories.yml` | `territories.<id>`: faction, name, world, x, z, radius, capital | TerritoryManager |
| `treasury.yml` | `treasury.<FAKCIÓ>: összeg` | FactionTreasuryManager |
| `kings.yml` | `kings.<FAKCIÓ>`: king UUID, election-start, votes | KingManager |
| `market.yml` | `listings.<uuid>`: seller, price, currency, item (szerializált), created-at | MarketManager |
| `economy-event.yml` | aktív kereslet-sokk (currency, multiplier, ends-at) | EconomyEventManager |

### PDC kulcsok (namespace: a plugin, `icesmp`)

**Játékoson:**
`job_primary`, `job_primary_xp`, `job_secondary`, `job_secondary_xp`, `unlocked_spells`
(vesszővel elválasztott spell idk), `quests_active`, `quests_completed`, `quest_progress_<id>`, `selected_spell_index`, `cd_<spellId>` (hosszú spell
cooldownok), `is_sinner`, `sin_count`, `dark_pact`, `profession_gathering`, `profession_crafting`,
`profession_xp_<szakmaId>` (szakmánkénti XP), `class_spec`,
`profession_spec`, `talents_class` és `talents_profession` (`id:rang,...` formátum).

**Itemen:** `currency_type`; `relic_id`, `relic_owner`, `relic_created_at`;
`is_ability_catalyst`, `unique_id`; armament tag (idézett kard jelölése).

**Entitáson:** `mob_level` (skálázott mob szintje), `minion_owner` (idézett minion gazdájának UUID-ja).

**Attribútum módosítók (játékoson, talentekből):** `icesmp:talent_max_health`,
`icesmp:talent_movement_speed`, `icesmp:talent_attack_damage` — belépéskor remove+add
mintával idempotensen újra-alkalmazva.

---

## 8. Build és fejlesztés

```bash
./gradlew build        # fordítás + jar
./gradlew test
./gradlew runServer    # xyz.jpenilla.run-paper, run/ mappában
```

- **Verziókatalógus:** `gradle/libs.versions.toml` (minecraft, `dev.folia:folia-api`,
  run-paper plugin). A folia-api `compileOnly` — futásidőben a szerver adja.
- **Java 21**, `org.gradle.configuration-cache` engedélyezve.

### Új funkció hozzáadása (minták)

- **Manager:** konstruktor `(plugin, configManager, ...)` → példányosítás az `IceSMPCore`
  konstruktorban → `load()` az `enable()`-ben, `save()` a `disable()`-ben, ha perzisztens →
  `clearPlayerState(UUID)` ha van per-session állapota (kösd be a
  `PlayerSessionCleanupListener`-be).
- **Parancs:** `BasicCommand` implementáció `execute` + `suggest` lefedettséggel →
  regisztrálás `IceSMPCore.registerCommands()`-ban.
- **Spell:** `BaseSpell` leszármazott (id, név, cooldown, költség) → `spellRegistry.register(...)`
  az `IceSMPCore`-ban → név a `messages.yml` `messages.spell.<id>.name` kulcsán → feloldási
  szint a configban. Statikus/volatile állapothoz adj `clearPlayerState`-et és kösd a cleanup
  listenerbe.
- **Item mechanika:** PDC tag a megfelelő `*ItemFactory`-ban + craft-védelem listenerrel.
- **Kódstílus:** `final` paraméterek és mezők, `public final class`, konstruktor-injektálás,
  játékos-szöveg mindig `MessageManager`-en át (`messages.yml`), szín a `TextUtil.color`-ral.

### Folia-szabályok

- Ne használj `runTaskAsynchronously`-t; az időzített feladatok szinkron futnak.
- Késleltetett task után mindig `isValid()`/null ellenőrzés entitásra és játékosra.
- Entitást csak a tulajdonos régiószálán módosíts (event handler kontextus biztonságos).
- Teleporthoz `teleportAsync`-ot használj.
