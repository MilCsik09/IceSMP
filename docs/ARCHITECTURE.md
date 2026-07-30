# IceSMP — Fejlesztői architektúra- és bővítési útmutató

> **Cél:** hogy a rendszer *átlátható, karbantartható és könnyen bővíthető* legyen. Ez a dokumentum
> a tényleges kódra épül: leírja, hogyan áll össze a plugin, milyen mintákat követünk, és
> lépésről lépésre **hogyan adj hozzá új tartalmat** anélkül, hogy bármit eltörnél.
>
> Kapcsolódó dokumentumok: `README.md` (áttekintés), `PLAYER_GUIDE.md` (játékos-kézikönyv),
> `PLAYTEST.md` (tesztelési útmutató), `ROADMAP.md` (nyitott fejlesztések).

---

## 1. Nagy kép — életciklus

```
IceSMP (JavaPlugin)            ← Bukkit/Paper belépő (onEnable/onDisable)
  └─ IceSMPCore                ← a teljes rendszer összeszerelése
       ├─ konstruktor          → ~90 manager felépítése (szigorú sorrend), registerSpells()
       ├─ enable()             → config + perzisztens store-ok betöltése, listenerek + parancsok
       │                         regisztrálása, ütemezett feladatok indítása
       └─ disable()            → perzisztens store-ok mentése, majd futó rendszerek leállítása
```

- **`IceSMP`** (`hu.taliann.icesmp.IceSMP`): csak delegál a `IceSMPCore`-nak.
- **`IceSMPCore`** (`core/`): az egyetlen „összeszerelő" osztály. Itt jön létre minden manager,
  itt regisztrálódik minden spell (`registerSpells()`), parancs (`registerCommands()`) és
  listener (`registerListeners()`), és innen indulnak az ütemezett feladatok.
- **Folia-kompatibilis** (`folia-supported: true`): **nincs** globális fő-szál. Minden szálkezelés
  a megfelelő Folia ütemezőn megy (lásd 4. szakasz). Ez nem opcionális — a rossz szálon végzett
  entitás-hozzáférés crashel.

---

## 2. Csomagtérkép

| Csomag | Fájlok | Szerep |
|--------|-------:|--------|
| `core/` | 2 | `IceSMPCore` — összeszerelés, életciklus, ütemezés. |
| `managers/` | 118 | Üzleti logika és állapot (gazdaság, frakciók, kasztok, szakmák, loot/raritás, recept-katalógus, pet, territórium-védelem, stb.). |
| `listeners/` | 119 | Bukkit eseménykezelők (gameplay + GUI-klikk + loot/craft/védelem). |
| `spells/` | 56 | Spell-rendszer: `Spell` SPI, `BaseSpell`, `ConfiguredSpell` builder, `SpellCatalog`, egyedi spellek. |
| `commands/` | 94 (65 + al-csomagok) | Parancsok. A `commands/<terület>/` al-csomagok a dispatch-stílusú alparancsokat tartják. |
| `gui/` | 46 | Inventory-menük + `GuiUtil` közös helperek + adat-vezérelt `CommandMenu` rendszer. |
| `crates/` | 13 | Dependency-free crate domain: strict validáció, selector/key plan, atomi opening lifecycle, recovery/kompenzáció, scheduler gate, audit és thread-safe formázás. |
| `data/` | 12 | Enumok és értékobjektumok (`CurrencyType`, `FactionType`, `JobType`, `SpecializationType`, `Territory`/`TerritoryType`…). |
| `relics/` | 9 (6 + `ability/`) | Relikvia-keret: `RelicRegistry`, `RelicDefinition`, triggerek. |
| `items/` | 12 | Item-gyárak (katalizátor, befogó item, tervrajz, egyedi alapanyag…). |
| `storage/` | 7 | `YamlStore` (atomikus írás) + `PersistentStore` SPI + fail-closed életciklus-koordinátor. |
| `session/` | 1 | `PlayerStateCleanup` SPI (per-player állapot takarítása). |
| `utils/` | 22 | `MessageManager`, `ExperienceUtil`, egyebek. |
| `integration/` | 6 | Soft-depend reflexiós hidak: PlaceholderAPI, LibsDisguises, FancyNpcs, WorldGuard, LuckPerms. |

---

## 3. Architektúra-minták (ezeket kövesd)

A rendszer egységes mintákra épül. **Új kódnál mindig a meglévő mintát használd** — ne vezess be
párhuzamos megoldást.

### 3.1 Konfiguráció — több-fájlos merge
`ConfigManager.load()` egyesíti a `config/<alrendszer>.yml` fájlokat (alapértékek), majd rájuk
olvassa a fő `config.yml`-t (override, ez nyer). A betöltött fájlokat a `CONFIG_FILES` tömb sorolja
fel. Minden hívó a megszokott `getInt/getDouble/getString("alrendszer.kulcs", default)` API-t
használja — a kulcs-útvonalak a fájlok között oszthatatlanok.

A `config.yml`-t az **ingame config-vezérlés** is ezt a réteget írja: `/icesmp config
get|set|unset|list|find` (node: `icesmp.admin.config`) bármely kulcsot lekér/felülbírál/töröl,
set/unset után azonnali reload + `ConfigValidator` fut. Mivel a managerek túlnyomó része
használat idején olvassa a configot, a legtöbb érték azonnal él. A `spell-balance.<id>.*`
kulcsok (cooldown, cost-amount, resource-cost, damage, radius, range, self-damage, heal-self,
feed-self, ignite-/freeze-ticks, knockback) kivétel nélkül CAST-időben olvasódnak
(`BaseSpell.balance` + a `ConfiguredSpell` live-accessorai + `ResourceManager.costOf`), tehát
a deklaratív spelleknél sem kell restart. Ami továbbra is indításkor dől el: a scheduler-tick
periódusok, a parancs-/listener-regisztráció és a konstruktorban cache-elt értékek.

Betöltés után a `ConfigValidator.validate(...)` **konvenció-alapú** ellenőrzést futtat a teljes
kulcstéren (soha nem dob, csak a konzolra figyelmeztet): a `material`/`materials` kulcsok valós
`Material`-t adnak-e, a `currency` kulcsok `OWN`/valuta-nevek-e, a `…percent` kulcsok a 0–100
tartományban vannak-e, a `…-minutes/-hours/-seconds/-ticks/-millis` kulcsok nem negatívak-e. Így az
admin-elgépelések (rossz item-név, kilógó százalék) tiszta log-figyelmeztetésként jelennek meg
ahelyett, hogy némán az alapértékre esnének vissza.

#### 3.1.1 Natív szerverlista-MOTD — immutable snapshot + generációkapu

A `MotdListener` nem olvas fájlt és nem járja be a konfigurációt a server-list ping szálán.
A `/icesmp reload`, a `motd.*` config-parancs és a config GUI ugyanazon célzott reload-hookot
hívja: a listener előbb szigorúan felépít egy immutable snapshotot, azonnal üríti a korábbi
ikoncache-t, majd külön async taskban csomagolja ki és olvassa a `plugins/IceSMP/icons/*.png`
fájlokat. A könyvtár és minden fájl `SecureDirectoryStream` handle-en, `NOFOLLOW_LINKS` mellett
nyílik meg; a méretellenőrzés és a dekódolás ugyanazon fájldescriptoron fut. A Bukkit
`CachedServerIcon` létrehozása a global-region scheduleren történik.

- választási mód: időalapú vagy seedelt, időablakon belül stabil random;
- eseményprioritás: vérhold → világboss → szezonzárás → normál pool;
- tokenek: kizárólag `{online}` és `{max}`; minden más brace-token config hiba; opcionális max-player override;
- a vanished count kizárólag a moderációs `VanishManager` thread-safe UUID-cache-ét használja;
- ikonmód: `NONE`, `DEFAULT`, `VARIANT`, `RANDOM`;
- ikonkapuk: symlinkmentes root/köztes/fájl útvonal, jóváhagyott data-rooton belüli secure open,
  legfeljebb 1 MiB és 64 fájl, valódi PNG, pontosan 64×64;
- a reload-generáció és a `SchedulerCallbackGate`-et újrahasznosító `MotdGenerationGate`
  megakadályozza, hogy régi, visszautasított vagy disable után befejeződő callback publikáljon;
  az ikonmap és a rendezett ID-lista egyetlen volatile immutable cache;
- hiányzó scalar a dokumentált defaultot használja; jelen lévő hibás boolean, lebegőpontos vagy
  tartományon kívüli egész, hibás enum, üres/túl nagy pool, duplikált normalizált ID és hibás
  strict MiniMessage csak a MOTD feature-t tiltja le, nem a teljes plugint.

A dependency-free `MotdSelector` tesztelhetővé teszi a rotációt és eseményprioritást. A
`motdRegressionTest` a negatív epoch floor-mod viselkedést, a random stabilitást/pool-lefedést,
a teljes signed-`long` és strict boolean szabályokat, a placeholder whitelistet, a symlink/TOCTOU
ikonvédelmet, a generációs interleavinget és a jarban szállított ikonok 64×64 dekódolását is ellenőrzi. Ez nem helyettesíti a valódi Folia ping/reload és proxy nélküli runtime playtestet.

### 3.2 Üzenetek — több-fájlos merge + formátum-tudatos rendering
`MessageManager.load()` egyesíti a `messages/<csoport>.yml` fájlokat (a `MESSAGE_GROUPS` szerint),
majd a fő `messages.yml`-t override-ként. Rendering: a `get`/`getMessage`/`getComponent` **mind**
formátum-tudatos — **MiniMessage** ha a szövegben `<...>` tag van ÉS nincs legacy `&`/`§` kód,
egyébként legacy. Sose feltételezd egyik formátumot sem; használd a generikus API-t.

### 3.3 Perzisztencia — atomikus írás + életciklus SPI
- **`storage/YamlStore.saveAtomic(file, yaml)`**: egyedi temp-fájl + atomikus rename (konkurens-biztos).
  **Minden** YAML-mentés ezen át megy — soha ne `yaml.save(file)` közvetlenül.
- **`storage/PersistentStore { load(); save(); }`**: a 34 fájlt-író store implementálja. Az
  `IceSMPCore` egy `List<PersistentStore>`-t iterál: `load()` az enable-ben, `save()` a disable-ben
  (a player-cleanup ELŐTT, hogy ne vesszen adat).
- **`storage/PersistentStoreCoordinator`**: az enable során **fail-closed** tölti be a teljes
  registryt; az első hibánál az indulás megszakad, részlegesen betöltött állapot nem menthető.
  Autosave és shutdown csak a teljesen betöltött registryt írhatja, egymással szerializálva.
- **Write-ahead napló (WAL) — ahol a mentés-időpont nem elég:** két rendszernek a következő
  autosave-ig sem szabad kockáztatnia, mert közben a világból/inventoryból már eltűnt valami.
  - **`storage/BlockRegenJournal`** (block-regen.yml checkpoint + `block-regen.wal`):
    a tile-entity snapshot tartósan lemezre kerül a konténer kiürítése előtt, és a pending
    rekordok restart után újrapróbálhatók. Az `APPLYING/APPLIED` átmenet csökkenti az elvesző
    restore-ok esélyét, de valódi Folia + process-kill fault-injection nélkül nem állítunk
    pontosan-egyszeri konténer-NBT alkalmazást.
  - **`storage/TransactionJournal`** (market-journal.yml): a prepare és a szigorú séma
    jelentősen csökkenti a félbehagyott listing/pénz/item műveletek elvesztését, és normál
    restartnál recoveryt ad. A wallet, market YAML és player inventory között nincs formális
    több-store atomicitás vagy exactly-once bizonyítás; a globális currency gate külön
    egyszerűsítési és runtime-validációs scope.

  - **Szezon–community generation commit** (`season.yml` → `community-goals.yml`): a community store tartós `season.number` markerrel jelöli, melyik szezonhoz tartozik a progressz. A zárás a community monitor alatt előbb rendezi az outboxot, majd commitolja az új `season.yml` generációt, és csak ezután nullázza/menti a community progresszt. Crash a két commit között egyetlen generációnyi marker-lemaradást hagy; bootkor ez idempotens resetként reconciliálódik. Függő régi payout, előreszaladt vagy több generációt átugró marker fail-closed.

- **DEV-item jutalom — arányos easter-egg state:** a Csodálatos Bingulus egyetlen runtime ownerhez
  kötött DEV-item, amely alapértelmezetten 10 perc aktív online birtoklás után sorsol random,
  konfigurált jutalmat. A manager egy immutable state-et tart (owner, singleton instance, issued,
  aktív idő, exact pending `ItemStack`, pity), egy lockkal és egy minimális tick gate-tel. A már
  kisorsolt exact item az inventory módosítása előtt a `dev-items-state.yml` fájlba kerül; teljes
  inventory és normál restart után ugyanaz próbálható újra.
- **Live owner reload:** `/icesmp reload` közben az új owner candidate state-je előbb kiíródik, majd
  válik aktívvá; az instance, progress, pity és pending jutalom megmarad. A tick az owner UUID-t a
  kezdéskor, az inventoryba adás előtt és a pending törlése előtt ellenőrzi. Mismatch esetén a régi
  tick egyszerűen visszatér. Nincs generation counter, owner-transition framework vagy tranzakciós
  rollback-protokoll.
- **DEV garanciahatár:** nincs receipt, grant ID, recipient binding, migration vagy exactly-once
  garancia. Process kill az inventory mutation és a completion YAML között, ritka write race,
  hardverhiba vagy extrém owner-transfer verseny esetén jutalomvesztés vagy duplikáció elfogadható.
  A DEV state hibája kizárólag a Bingulus progresszét, sorsolását és kiosztását állítja le; a market,
  wallet, currency és season store-ok ettől nem állnak le.
- **DEV regressziók:** a `devItemRewardRegressionTest` Gradle `JavaExec` task a `check` lifecycle
  része. Az intervalt, exact pending/restartot, full-inventory retryt, egyszerű owner reloadot,
  write-failure határt, strict state-et és a gate normal/exception/retired/rejection/null útjait
  teszteli. A `scripts/test_dev_item_state.py` csak tiltott legacy/overengineered tokeneket ellenőriz,
  majd ugyanezt a Gradle taskot hívja; nincs második, párhuzamos tesztrendszer. A tartós `IceSMP CI`
  workflow Java 21-en clean buildet, Gradle-suite markert, célzott Python futtatást,
  `git diff --check`-et és base/head consistency-deltát ellenőriz `contents: read` jogosultsággal.

### 3.4 Parancsok — két stílus
- **Dispatch (preferált, alparancsos):** `AbstractDispatchCommand` bázis + `Subcommand` SPI.
  A bázis kezeli a map-et, a diszpécst, a helpet és a tab-complete-et; a parancs a konstruktorára
  zsugorodik (lásd `CurrencyCommand`, `JobCommand`, `FactionCommand`, `BankCommand`). Üzenet-kulcsok:
  `messages.<név>-unknown-subcommand`, `messages.<név>-help-header`, `messages.<név>-help-<alparancs>`.
- **Egyrészes / implicit-default:** néhány parancs (Market, Pet, Soul, Spell, Events…) üres argra
  műveletet végez (nem helpet ad), vagy nem `args[0]`-ra diszpécsel. Ezek szándékosan külön
  `BasicCommand`-ok — a dispatch-bázis nem modellezi ezt a szemantikát.
- **Permissionök:** kanonikus séma a `core/Permissions` osztályban (konstansok + `register()` az
  `enable()` elején). Minden admin-node `icesmp.admin.<domain>` (default: OP), az
  `icesmp.admin.all` regisztrált szülő-node az összeset megadja egyben; a régi nevek
  (`icesmp.admin`, `icesmp.job.admin`, `icesmp.currency.admin`, `icesmp.faction.admin`,
  `icesmp.relic.admin`) alias-Permissionként a kanonikus gyereküket adják — meglévő
  LP-beállítás nem törik. Új admin-parancsnál: konstans a `Permissions`-be + a `register()`
  canonical-map-jébe egy sor.

### 3.5 Spellek — registry + builder + katalógus
- **`SpellRegistry`**: id → `Spell` map (`register`, `getById`, `getAll`).
- **`Spell` SPI** (`spells/Spell.java`): id/név/cooldown/költség + `executeSpell()` (true = hatás
  történt; false = no-op → nincs költség/cooldown) + `describe()` (spellbook-leírás) + `clearPlayerState()`
  (per-player takarítás, alapból no-op).
- **`ConfiguredSpell.builder(...)`**: adat-vezérelt spellek kód nélkül — láncolható hatások
  (`damage`, `healSelf`, `selfEffect`, `targetEffect`, `ignite`, `freeze`, `knockback`, `dash`,
  `particle`, `sound`, `aoe`, `target`, `friendly`…). A számok automatikusan a `describe()`-ba kerülnek.
- **`SpellCatalog`**: a kaszt-/spec-spellkészletek deklaratív regisztrációja (`ConfiguredSpell`-ekből).
- **Egyedi (bespoke) spellek**: ha a hatás nem fér a builderbe (pl. `HideSpell`), `extends BaseSpell`.
- **Config-driven balansz-felülbírálás** (`config/spells-balance.yml`): a `spell-balance.<id>.*` kulcsok
  **LIVE_READ**-ek — a `ConfiguredSpell` accessorai (`getDamage`, `getRange`, `getRadius`, …) és a bespoke
  spellek `BaseSpell.balance()` / `balanceInt()` segédei is CAST-időben olvassák a configot, ezért
  `/icesmp reload` után restart nélkül élnek. A `IceSMPCore.applySpellBalanceOverrides()` (`enable()`,
  `configManager.load()` után) csak az indulási log és az ismeretlen spell-id figyelmeztetés miatt fut le
  (`ConfiguredSpell.withBalanceOverrides`, immutable copy). **RESTART_ONLY** marad, ami nem érték, hanem
  szerkezet: a spell-regisztráció maga (új spell/unlock-lista), a scheduler-tick periódusok és a
  konstruktorban cache-elt értékek.

### 3.6 GUI — közös helperek + adat-vezérelt menük
- **`GuiUtil`**: közös item-/lore-építők (`icon`, `filler`, `fill`, `label`, `accent`, `grey`).
  Új menü-ikonnál ezeket használd, ne építs inline `ItemMeta`-t.
- **`CommandMenu` rendszer** (adat-vezérelt): a legtöbb menü a `CommandMenus` definíciókból + a
  `CommandMenuHolder`/`CommandMenuListener` párosból épül. Új „gombmenühöz" ezt preferáld a
  bespoke GUI helyett.

### 3.7 Player-state takarítás — registry-iterált
A `PlayerSessionCleanupListener` kilépéskor/kickkor: (a) végigmegy a regisztrált
`List<PlayerStateCleanup>`-on (managerek), és (b) a `SpellRegistry.getAll()`-on, minden spell
`clearPlayerState(uuid)`-jét hívva. A spell-ágon **nincs hardkódolt lista** — új állapotos spell
automatikusan bekerül; a manager-ág viszont kézzel karbantartott konstruktor-lista (lásd 5.7/5.8
recept: új állapotos managert fel kell venni a `stateOwners` listába).

### 3.8 Kaszt-erőforrás (`ResourceManager`) — hibrid költség
Per-kaszt „erő" 0–max meter, a HUD-oldalsávban megjelenítve (`HudManager.buildLines` hív egy
`hudLine`-t — **nem** külön boss-bar, hogy ne ütközzön a világboss-sávval). A csík **lazy módon
regenerálódik** (minden hozzáférés krediteli az eltelt időt — nincs scheduler), UUID-kulcsos
concurrent map (Folia-safe, nem nyúl entitáshoz a saját szálán kívül). `PlayerStateCleanup`-ot
implementál.

**Hibrid költségmodell** — `ResourceManager.usesResource(spell)` dönti el spellenként, mi a költség:
- `HEALTH` → marad HP (vér-mágia);
- `XP ≥ xp-ritual-threshold` (alap 80) → marad XP (nagy rituálé/idézés/időjárás/ulti);
- `HUNGER ≥ hunger-heavy-threshold` (alap 8) → marad éhség (nehéz fizikai);
- minden más → a kaszt-erőforrás.

A cast-pipeline (`AbilityCatalystListener`) ez alapján ágazik: `usesResource` spellnél
`canAfford`/`consume`/`refund` a `ResourceManageren` (a költség `Spell.getResourceCost()`,
cooldown-szint alapján); egyébként a spell saját `hasRequiredCost`/`consumeCost`/`refundCost`
(éhség/XP/HP) útja. Ha `spells.resource.enabled=false`, MINDEN spell a régi éhség/XP/HP útra esik.

> A korábbi „teli állapotban kirobbanás + empowered ablak" jutalom-mechanika **megszűnt** — a csík
> most költség (spend-modell), ami ugyanazon a sávon kizárta a build→discharge-ot.

### 3.9 Territórium-zónák és zóna-védelem

A **zóna-modell** három rétegre bomlik, hogy a geometria, a szabály-feloldás és az
eseménykezelés külön változhasson:

1. **`data/Territory` (+ `TerritoryType`)** — egy zóna: kör (`x,z,radius`) VAGY poligon
   (`{x,z}` csúcsgyűrű, ≥3), opcionális `minY`/`maxY` sávval (`NO_MIN_Y`/`NO_MAX_Y` =
   korlátlan). A `contains(...)` befoglaló-kör gyors elutasítással kezd, poligonnál
   páros-páratlan ray-casttel folytat; a `radius` a poligonnál a befoglaló-kör sugara. Átfedő
   zónáknál (`shadows`) **a védett zóna MINDIG elfedi a nem-védettet** (a pajzsot kisebb
   frakció-zóna sem tudja alávágni), egyébként a legspecifikusabb (legkisebb sugarú) nyer. A
   claim-veto 2D (oszlop) lekérést használ (`getTerritoryColumnAt`), hogy a magas claim-doboz a
   zóna Y-sávjától függetlenül ütközzön. A típus dönti el az építés/claim jogot
   (`isProtectedZone`, `isClaimable`).
2. **`TerritoryManager`** — a zónák állapota + perzisztencia (`territories.yml`, régi `capital:
   true/false` migrál). A lekérés **lock-free**: `chunkIndex` (`world;cx;cz → zónák`) a
   `ClaimManager` mintájára, minden (ritka, parancs-vezérelt) mutáció `synchronized` alatt
   újraépíti és atomikusan cseréli. Poligon-kijelöléshez per-player pont-puffer
   (`PlayerStateCleanup`). Szerkesztők: `define`/`definePolygon`/`rename`/`resize`/`setType`/
   `setYBounds`/`remove` — mind index-újraépítés + mentés.
3. **`TerritoryProtectionService` + `TerritoryProtectionListener`** — a védelmi szabályokat a
   `territory.protection.rules.<típus>.<szabály>` configból oldja fel (`build`, `interact`,
   `pvp`, `explosions`, `fire`; beégetett defaultok). `true` = tiltott: védett zónában
   mindenkinek, frakcióterületen csak a nem-tagnak. A service tiszta feloldó (Bukkit-esemény
   nélkül), a listener kizárólag delegál — így új eseményt bekötni = egy handler, ami a
   megfelelő `deny*`/`is*BlockedAt` metódust hívja.

**Fedett rések (a fő kulcsokra visszavezetve, nincs új config):** a `build` védett zónában a
mob-griefet (`EntityChangeBlockEvent`), folyadék-befolyást (`BlockFromToEvent`) és
dugattyú-tolást (`BlockPiston*Event`) is tiltja (`isTerrainProtectedAt`); a `pvp` a közelharcon
túl a lövedéket, háziállatot, TNT-t és a dobott/lingering ártó bájitalt is
(`denyCombat` + `resolveAttacker`); az `explosions` a képkeret/armor stand dekorációt is óvja.

**Bypass:** `icesmp.admin.territory.bypass` (minden, PvP is) és `icesmp.territory.builder`
(build+interakció védett zónában is, PvP nem). **Folia:** minden handler az esemény régió-szálán
fut, a lekérés lock-free; az egyetlen kereszt-entitás érintés a PvP-tiltás értesítése, ami a
támadó saját `getScheduler()`-ére hoppol.

> **Új szabály/típus bekötése:** típus → `TerritoryType` (build/claim jog); szabály → új kulcs a
> `rules` alá + `defaultRule` + a service egy `deny*`/`is*At` metódusa + egy listener-handler.
> A `claim` tiltását a `TerritoryManager.isClaimBlockedAt` adja (csak védett zónában).

---

## 4. Folia szálkezelés (KRITIKUS)

Nincs egyetlen fő-szál. A megfelelő ütemezőt használd:

| Cél | Ütemező |
|-----|---------|
| Egy entitás (player/mob) műveletei | `entity.getScheduler().run(plugin, task, retired)` / `runDelayed(...)` |
| Egy lokáció/régió blokk-/világ-művelete | `Bukkit.getRegionScheduler().run(plugin, location, task)` |
| Globális, nem hely-kötött tick | `Bukkit.getGlobalRegionScheduler().runAtFixedRate(...)` |
| Háttér (IO, nem-játék) | `Bukkit.getAsyncScheduler().runDelayed(plugin, consumer, delay, unit)` |
| Teleport | `entity.teleportAsync(loc)` |

Szabályok:
- **Sose** `Bukkit.getScheduler()` (nem támogatott Folián).
- Másik régióban lévő entitáshoz mindig hopp át annak az entitásnak az ütemezőjére.
- A `runDelayed` *retired-callbackjét* add meg, ha az állapotot vissza kell állítani akkor is, ha a
  task lejár, mielőtt lefutna (lásd `HideSpell` páncél-visszaállítás).

### 4.1 Folia audit-állapot (statikus baseline)
A központi scheduler-minták sokat javultak, de ez nem teljes runtime-garancia. A party
proximity/reward és más több-régiós hívási láncok valódi Folia tesztet igényelnek. A bevált
minták, amelyeket új kódnál is tartani kell:
- **Nincs** legacy `Bukkit.getScheduler()` / `BukkitRunnable` / `runTask*` / nyers `Thread`/`Timer`/`Executor`.
- **Nincs** szinkron `teleport(...)` — mindenhol `teleportAsync(...)`.
- **Globális ismétlődő tickek** (`IceSMPCore`: world-events, HUD, pet, adó, gazdaság-esemény) csak
  kockát dobnak / memóriabeli állapotot olvasnak; minden játékos-/entitás-munkára **hoppolnak**:
  `player.getScheduler().run(...)` (HUD, vér-hold), `pet.getScheduler().run(...)` (pet-mutáció),
  `anchor.getScheduler()` → `getRegionScheduler(location)` (world-boss / invázió mob-spawn).
- **Spellek** a kasztoló játékos régió-szálán futnak, és lokálisan idéznek (`player.getWorld().spawn`),
  az idézett entitás további léptetése annak saját ütemezőjén (`minion.getScheduler()`, `chicken.getScheduler()`).
- **`getAsyncScheduler`** kizárólag IO-ra (debounce-olt mentés a `CurrencyManager`-ben) — **soha** entitásra.
- **Kivétel — `disable()`:** leállításkor a player-cleanup *közvetlenül* fut (nem ütemezve), mert a
  Folia ütemező a shutdown alatt már nem fogad új taskot; ez a szándékos best-effort minta.

**Ökölszabály új kódhoz:** ha entitást/játékost/világot érintesz egy esemény-kezelőn KÍVÜLi
kontextusból (tick, callback, másik entitás), előbb hopp az adott entitás/régió ütemezőjére.

### 4.2 Mulandó entitások életciklusa — `utils/TransientEntities`

A világesemény-managerek UUID-kulcsú listákat tartanak (konvoj, hordamobok, fenevad, kultisták,
minionok), és tudniuk kell, él-e még az entitás. A `Bukkit.getEntity(uuid)` + `isValid()` páros
erre **nem használható**: globális tickről idegen régió entitását olvasná.

- **`register(plugin, entity)`** — az entitás SAJÁT ütemezőjén heartbeatet indít (`runAtFixedRate`),
  és eltárolja a `Handle`-t (id + generáció + scheduler). A `runAtFixedRate` visszavonás-callbackje
  (Folia akkor hívja, ha az entitás megszűnik) nyugdíjazza a handle-t.
- **`isAlive(id)`** — tisztán memóriabeli, atomi olvasás: él a handle, és a heartbeat friss-e.
  **FAIL-CLOSED: ismeretlen id = halott.** Ez szándékos — egy fail-open liveness beragadhat
  „örökké él" állapotba, és a `MajorEventGate`-en át az összes nagy eseményt letilthatja.
- **`removeById(plugin, id)`** — a tárolt scheduleren távolít el; nincs globális UUID-keresés.

**KÖTELEZŐ invariáns:** ha egy manager `isAlive`-ot hív, a spawn-útján `register`-t IS kell hívnia.
Regisztráció nélkül a saját entitása azonnal halottnak látszik, és az esemény a következő tickben
lezárul (a `check_consistency.py` `transient-liveness` őre ezt FAIL-lel fogja meg).

**Második védőháló:** a `MajorEventGate` watchdogja (`world-events.orchestration.max-active-minutes`,
alap 60) egy beragadt `isActive()`-ot egy idő után figyelmen kívül hagy — így egyetlen elveszett
életciklus-visszajelzés sem tilthatja le a többi eseményt szerver-újraindításig.

---

## 5. Bővítési receptek

### 5.1 Új konfigurációs kulcs
1. Tedd a megfelelő `src/main/resources/config/<alrendszer>.yml` fájlba (kommenttel).
2. Olvasd `configManager.getX("alrendszer.kulcs", default)`-kal. Kész — a merge automatikus.

### 5.2 Új konfigurációs alrendszer (saját fájl)
1. Hozd létre `config/<új>.yml`-t.
2. Vedd fel a nevét a `ConfigManager.CONFIG_FILES` tömbbe.
3. (A `saveResource` automatikusan kicsomagolja első indításkor.)

### 5.3 Új üzenet
1. Tedd a megfelelő `messages/<csoport>.yml`-be a `messages:` alá.
2. Hívd `messageManager.getComponent("messages.kulcs", "&7default", args...)`-szal.
   Új csoportfájlhoz vedd fel a nevét a `MessageManager.MESSAGE_GROUPS`-ba.

### 5.4 Új spell (adat-vezérelt — ez az alapeset)
A `SpellCatalog` megfelelő `register<Kaszt>` metódusában:
```java
registry.register(ConfiguredSpell.builder(mm, "spell_id", "Megjelenő Név", cooldownSec, SpellCostType.XP, 80)
        .target(6.0).damage(7.0).ignite(60).particle(Particle.FLAME, 30).sound(Sound.ENTITY_BLAZE_SHOOT, 1f, 1f)
        .build());
```
Majd a feloldási szintet a `config/classes.yml` (`classes.<kaszt>.spell-unlocks`) vagy
`config/spells.yml`/`specializations.*.spell-unlocks` alá. A `describe()` automatikus.

### 5.5 Új egyedi spell (ha a builder nem elég)
1. `public final class XSpell extends BaseSpell` — konstruktorban `super(mm, id, név, cooldown, costType, cost)`.
2. Implementáld `execute(Player)`-t; ha no-op-olhat, írd felül `executeSpell(Player)`-t és adj vissza
   `false`-t, ha nem történt hatás (így nincs költség/cooldown).
3. Ha per-player állapotot tárol, írd felül `clearPlayerState(UUID)`-t (a `SpellRegistry` automatikusan hívja).
4. Regisztráld a `IceSMPCore.registerSpells()`-ben.

### 5.6 Új parancs
- **Alparancsos:** hozz létre `commands/<terület>/` csomagot egy `<Terület>Subcommand extends Subcommand`
  markerrel + egy-egy `Subcommand` osztállyal alparancsonként; a parancs `extends AbstractDispatchCommand`,
  a konstruktor `super(mm, "<név>", "&6/<név> ...")` + `register(...)` hívások (minta: `BankCommand`).
- Regisztráld a `IceSMPCore.registerCommands()`-ben: `plugin.registerCommand("név", "leírás", List.of(aliasok), new XCommand(...))`.
- Üzenet-kulcsok a `messages.<név>-help-header` / `-help-<alparancs>` / `-unknown-subcommand` konvenció szerint.

### 5.7 Új perzisztens store
1. `implements PersistentStore`, a `load()`/`save()`-ben **`YamlStore.saveAtomic`**-ot használj.
2. Vedd fel a `IceSMPCore` `persistentStores` listájába (`List.of(...)`) — ettől automatikusan
   betöltődik enable-kor és mentődik disable-kor.

### 5.8 Új player-state tulajdonos
- **Manager/listener:** `implements PlayerStateCleanup`, írd meg `clearPlayerState(UUID)`-t, és vedd
  fel a `PlayerSessionCleanupListener` konstruktorában a `stateOwners` listába.
- **Spell:** csak írd felül a `clearPlayerState(UUID)`-t — a registry-iteráció automatikusan hívja.

### 5.9 Új relikvia
A `RelicManager` `registerRelic(...)` mintáját kövesd (id, megjelenés, trigger-konfiguráció);
a `SimpleRelicDefinition` a deklaratív eset. A triggerek a `relics/RelicTrigger`-ben.

---

## 6. Konvenciók

- **Nyelv:** minden játékos-szöveg magyar (a default stringekben is).
- **Immutabilitás:** `final` mezők/paraméterek mindenhol; értékobjektumok `record`-ként.
- **Üzenet-kulcsok:** `messages.<terület>-<cél>` (pl. `bank-help-withdraw`). Mindig adj értelmes
  default stringet a `get*` hívásban.
- **Atomikus IO:** minden YAML-mentés `YamlStore.saveAtomic`-on át.
- **Nincs párhuzamos minta:** ha van rá SPI/bázis/registry, azt használd.
- **Particle-stílus** (a tulaj kérése: „sokat adnak hozzá, de ha nem szép, sokat rontanak"):
  - `FLASH` mindig `count=1` — a képernyő-villanás nem halmozódik, a többlet csak csomag.
  - Ünneplő konfetti (`TOTEM_OF_UNDYING`) legfeljebb ~16-18 darab, szűk terítéssel.
  - Egyszeri burst ≤ ~30 darab; ami hosszabb hatás, az PULZÁLJON kis adagokban
    (`AmbientEventManager` `pulse`-minta), ne egy nagy robbanás legyen.
  - Talaj-közeli jelölők (határ, perem) a `ParticleUtil.markerY`-ról kapják a magasságot
    (terep-követés + Folia-guard) — sose lebegjenek a néző derekán dombokon át.
  - Adat-igényes particle-ök (`FLASH`, `DUST`…) mindig a `ParticleUtil.spawn`-on át
    (default-adat feloldás, konzol-hiba helyett).
- **Effekt-réteg megválasztása** (particle vs. display-entity):
  - **Particle = átmeneti visszajelzés** (ütés, cast, ambient). A formázott spell-effektek a
    `SpellVfx`-en át mennek: forma (BEAM/RING/HELIX/CONE/…) a targeting-jellegből + paletta
    (`DUST_COLOR_TRANSITION`) + a spell accent-particle-je. Pontszám-plafon (`spell-vfx.max-points`),
    minden pont `count=1` dust — a fenti particle-szabályok érvényesek rá.
  - **DisplayFx (`DisplayFxUtil`) = geometria / tartós / kliens-oldalon animált** (claim-fényfal,
    telegraph, kirakat). KÖTELEZŐ hármas: régió-száli spawn (`getRegionScheduler().run`) +
    `setPersistent(false)` + `FX_TAG` (a `DisplayFxCleanupListener` söpri a maradékot); auto-despawn
    az entitás SAJÁT schedulerén; per-nézőhöz `showOnlyTo`. Display-entitást SOSE spawnolj
    frame-enként — egyszer spawnolj, és `animateTo`-val interpoláltass.

---

## 7. Build és ismert korlátok

- **Stack:** Java 21, Gradle, Paper/Folia API `1.21.11`. Belépő/bootstrap/loader a `paper-plugin.yml`-ben.
- **Bootstrap-szint (`IceSMPBootstrap`):** a registry-fagyás előtt fut — itt regisztráljuk a
  data-driven **signature-enchantokat** (`icesmp:jegfog` stb., kulcsok: `items/SignatureEnchantKeys`);
  a kliens a registry-szinkronnal kapja őket, a leírás-Component a tooltipben renderelődik. A
  viselkedés NEM itt él (`SignatureItemListener`); a craft-stamp kulcsa `signature.custom-enchants`.
  Bővíthető: damage-type/banner-minta/trim regisztráció ugyanígy; MobEffect (bájital-effekt) NEM
  regisztrálható (kliens-hardcode) — arra szerver-oldali pszeudo-effekt a minta.
- **Jarból szállított datapack (`DATAPACK_DISCOVERY`):** a bootstrap a jar `/datapack`
  könyvtárát rendes datapackként ismerteti meg a szerverrel (`autoEnableOnServerStart`), így
  a 22 csomópontos IceSMP haladás-fa és a 3 fix toast-bejegyzés a KÓDDAL EGYÜTT verziózódik,
  futásidejű registry-mutáció nélkül. Az `AdvancementService` enable-időben csak ellenőriz;
  ha a felderítés elbukott, a régi (`@Deprecated Bukkit.getUnsafe()`) úton pótolja a hiányzó
  bejegyzéseket, és WARNING-ot logol. A fa-bejegyzések `show_toast:false` +
  `announce_to_chat:false` (a visszajelzés a rendszerek saját chat-üzenete, az ünneplő toast a
  külön `ToastUtil`-réteg) — a tartalék út JSON-generátora is ezt írja, hogy a két betöltési
  út ugyanúgy viselkedjen. Új csomópont = NODES-bejegyzés + `python3 scripts/gen_advancements.py`
  (a JSON-ok EGYETLEN forrása a Java NODES lista) + VALÓDI `AdvancementService.award(...)`
  hívás — a `scripts/check_consistency.py` négyesével ellenőrzi: hiányzó JSON, árva JSON,
  holt bejegyzés, tartalom-drift.
- **Loader-szint (`IceSMPLoader`):** runtime Maven-függőségek helye (`MavenLibraryResolver`) —
  jelenleg üres, új külső lib igényekor ide, ne a shadowJar-ba.
- **Méret:** 545 Java-fájl, ~85 000 sor; 90 `*Manager` osztály (a `managers/` csomag 118 fájl).
  Csomag-megoszlás: listeners 119, managers 118, commands 94, spells 56, gui 46, crates 13, utils 22, data 12,
  items 12, relics 9, integration 7.
- **Build:** `./gradlew clean build --no-daemon --stacktrace` futtatja a fordítást, a
  a perzisztencia-, DEV-item-, moderáció-, MOTD-, sit-, crate- és AFK-regressziós suite-okat.
- **Kiegészítő ellenőrzés:** `python3 scripts/test_dev_item_state.py` és
  `python3 scripts/check_consistency.py`. Pull requesten a `scripts/check_consistency_delta.py`
  hasonlítja a base/head eredményt.
- **Hátralévő refaktor** (build-checkpointot igénylő, szándékosan halasztott tételek): a maradék
  inline parancsok migrálásához a dispatch-bázis additív bővítése (default-subcommand + láthatósági
  predikátum); az `IceSMPCore` manager-építés factory-szétbontása (a `final` mezők miatt).
- **Garanciahatár:** a statikus, dependency-free és build-integrált regressziók nem helyettesítik a
  valódi Folia multi-region, process-kill, ENOSPC vagy permission-denied fault-injectiont.
- **Nyitott fejlesztések:** `ROADMAP.md`.

## Natív moderációs alrendszer

A moderáció egyetlen autoritatív `ModerationManager` store-ra épül. A dependency-free `PunishmentLedger` tartja az invariánsokat; a Paper/Folia adapterek csak parancsot, eventet, GUI-t és scheduler ownershipot kezelnek. A state a közös `PersistentStoreCoordinator` lifecycle-ban, `YamlStore.saveAtomic` mentéssel működik. Sikertelen mutációs mentésnél a manager visszagörgeti a memóriasnapshotot, kritikus írási hibánál fail-closed leállást kér.

A kereszt-entitásos live inventory két owner thread között halad: target scheduler → tesztelt `InventoryEscrowGate` → tartós, count-preserving `InventoryEscrowQueue` → viewer scheduler. A target completion csak a return queue publikálása után válik láthatóvá. A nullable entity-submitokat dependency-free single-winner gate és vékony Paper adapter kezeli; a repeating refresh handle race-biztos `TaskLease`-ben él. A `/reply` linket `ReplyPartnerRegistry` join-session generációval keríti el. A vanish viewer-owned visibility API-t használ. Az async pre-login gate kizárólag szálbiztos immutable/synchronized read modellt olvas. Az üzemeltetési szerződés az [admin kézikönyvben](ADMIN_GUIDE.md#11-audit-és-persistence) található.

## Natív sit-only lifecycle

A `SitManager` egy Bukkit-független, atomi `SitState` ledgerben foglalja a world+block
ülőhelyet, majd PDC-azonosított, nem persistent ArmorStand seat entityt hoz létre a régió
tulajdonos-szálán. A player/entity scheduler submit exception, null handle és retirement ugyanazon
`PaperEntityTaskSubmission` single-winner fallbacken fut; a reload/disable cleanup rövid, korlátos
drainnel követi az entity eltávolításokat. A scope kizárólag `/sit`, `/sit fel` és click-to-sit: lay,
crawl, stacking és player/NPC sitting nincs runtime wiringban.

## Natív crate settlement és recovery

A `CrateManager` egy dependency-free domainrétegre épül. A `CrateOpeningLifecycle` CAS-alapú
`RESERVED → PERSISTED → GRANTING → COMPLETED` állapotgépe biztosítja, hogy egy grant legfeljebb
egyszer legyen claimelhető, a finalize és rollback pedig kölcsönösen kizárja egymást. A stat/cooldown
mutation token csak sikeres reward-settlement után kerül az autoritatív `CrateLedger` állapotba.

A schema 2 recovery rekord `ROLLBACK_ONLY`, `REFUND_KEYS`, `REFUND_CLAIMED` és `MANUAL_REVIEW`
állapotokkal teszi explicitté a kompenzációs határt. A currency batch durable save + exact snapshot
rollback tokent használ; a command batch csak global-scheduler elfogadás, tényleges futás és sikeres
`dispatchCommand` után tekinthető sikeresnek. Már nem kompenzálható külső side effect esetén nincs
automatikus key refund, hanem auditálható részleges hiba marad. Ez nem distributed transaction és
nem process-crash exactly-once garancia.

A config snapshot generationhöz kötött: a key purchase ugyanabból a generationből számít árat és
készít kulcsot, opening finalize előtt pedig újraellenőrzi a world/location/crate-ID/definition/policy
invariánsokat. Audit append és rotáció egyetlen sorosított writeren fut; a scheduler task/rejection
single-winner gate-et és race-biztos task lease-t használ. Az üzemeltetési
és recovery-szerződést az
[admin kézikönyv crate acceptance szakasza](ADMIN_GUIDE.md#natív-crate)
foglalja össze.
