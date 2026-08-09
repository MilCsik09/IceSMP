# IceSMP — Fejlesztői architektúra- és bővítési útmutató

> **Cél:** hogy a rendszer *átlátható, karbantartható és könnyen bővíthető* legyen. Ez a dokumentum
> a tényleges kódra épül: leírja, hogyan áll össze a plugin, milyen mintákat követünk, és
> lépésről lépésre **hogyan adj hozzá új tartalmat** anélkül, hogy bármit eltörnél.
>
> Kapcsolódó dokumentumok: [`README.md`](../README.md) (áttekintés),
> [`PLAYER_GUIDE.md`](PLAYER_GUIDE.md) (játékos-kézikönyv),
> [`ADMIN_GUIDE.md`](ADMIN_GUIDE.md#release-acceptance-checklist) (tesztelés) és
> [`ROADMAP.md`](../ROADMAP.md) (nyitott fejlesztések).

---

## 1. Nagy kép — életciklus

```
IceSMP (JavaPlugin)            ← Bukkit/Paper belépő (onEnable/onDisable)
  └─ IceSMPCore                ← a teljes rendszer összeszerelése
       ├─ konstruktor          → ~92 manager felépítése (szigorú sorrend), registerSpells()
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
| `core/` | 4 | `IceSMPCore` — összeszerelés, életciklus, ütemezés — + az élő config-apply hidak (`ConfigRuntimeReloadBridge`, `AdvancedConfigRuntimeBridge`). |
| `managers/` | 123 | Üzleti logika és állapot (gazdaság, frakciók, kasztok, szakmák, loot/raritás, recept-katalógus, pet, territórium-védelem, stb.). |
| `listeners/` | 122 | Bukkit eseménykezelők (gameplay + GUI-klikk + loot/craft/védelem + esemény-spawn debug). |
| `spells/` | 56 | Spell-rendszer: `Spell` SPI, `BaseSpell`, `ConfiguredSpell` builder, `SpellCatalog`, egyedi spellek. |
| `commands/` | 94 (65 + al-csomagok) | Parancsok. A `commands/<terület>/` al-csomagok a dispatch-stílusú alparancsokat tartják. |
| `classrelic/` | 14 | Class Relic Framework: pure resolver/katalógus/jelzések + Paper homlokzat (`ClassRelicService`). |
| `quest/` | 8 | Quest Framework v2 pure magja: forrás-policy + kontextus, kategória/láthatóság szótárak, gráf-validátor, választó-token registry, marker-paletta, valamint az első belépés üdvözlő-szövegének egyetlen szabálya (`OnboardingWelcomeCopy`: canonical copy + elavult stock-config felismerése, custom szöveg érintetlenül). |
| `gui/` | 69 | Inventory-menük + `GuiUtil` közös helperek + adat-vezérelt `CommandMenu` rendszer + staged config-editor lapok (root/kategória/operational/world/crate + reward-editor). |
| `crates/` | 14 | Dependency-free crate domain: strict validáció, selector/key plan, atomi opening lifecycle, recovery/kompenzáció, scheduler gate, audit és thread-safe formázás. |
| `factions/` | 13 | Immutable passzív-config snapshot, tiszta damage/exhaustion/target policy, központi combat-marker katalógus, mobkontextus-resolver, mulandó retaliation state és a központi frakció-névszín paletta (policy + Adventure-adapter); a tartós tagság-, történet- és adóállapot a PlayerProfile faction/economy szekcióiban él. |
| `data/` | 15 | Enumok és értékobjektumok (`CurrencyType`, `FactionType`, `JobType`, `SpecializationType`, `Territory`/`TerritoryType`, `BlockCuboid`…). |
| `relics/` | 12 (9 + `ability/`) | Relikvia-keret: `RelicRegistry`, `RelicDefinition`, triggerek, transfer-elvárás, immutable világ-pillanatkép + single-writer store. |
| `items/` | 13 | Item-gyárak (katalizátor/Lélekkapocs, befogó item, tervrajz, egyedi alapanyag…) + viselhető prezentáció. |
| `warrior/` | 2 | Harcos gameplay vertical slice: transiens harci állapot + konkrét runtime (Csatatempó, Berserker, Guardian). |
| `evoker/` | 2 | Sárkányidéző gameplay vertical slice: transiens állapot + konkrét runtime (Felerősítés, Vörös–Kék Eszencia, Visszhang/Időlenyomat). |
| `archer/` | 3 | Íjász gameplay vertical slice: transiens állapot + konkrét runtime (Szélolvasás, Pontossági lánc, Kötelék) + a repülő nyilak korlátos, magától lejáró fegyelem-nyilvántartása (`ArcherShotLedger`). |
| `shaman/` | 2 | Sámán gameplay vertical slice: transiens állapot + konkrét runtime (Totemkerék-rezonancia, Maelstrom-ritmus, Dagály↔Apály). |
| `monk/` | 2 | Szerzetes gameplay vertical slice: transiens állapot + konkrét runtime (Áramlás, Harcművészeti Lánc, Stagger, Ködszál). |
| `paladin/` | 2 | Paplovag gameplay vertical slice: transiens állapot + konkrét runtime (Meggyőződés/Eskü, Fényjelző, Ítélet-jelek, Pajzstöltet). |
| `demonhunter/` | 2 | Démonvadász gameplay vertical slice: transiens állapot + konkrét runtime (Kárhozat-terhelés, Lélektöredék/Momentum, Fájdalom/Sigil). |
| `druid/` | 2 | Druida gameplay vertical slice: transiens állapot + konkrét runtime (Természeti Erő/Évszak, kombó+Szagnyom, Nap–Hold mérleg/Eclipse, Kéregrétegek/Gyökérháló, Mag→érés→Virágzás). |
| `priest/` | 2 | Pap gameplay vertical slice: transiens állapot + konkrét runtime (Litánia-versek, Engesztelés rekurzió-őrrel + pajzsháló, Velő/Osszárium, Őrület-Küszöb). |
| `deathknight/` | 2 | Halállovag gameplay vertical slice: transiens állapot + konkrét runtime (Rúnakör Vér/Fagy/Halál, fix méretű Vér Emlékezete, Fagyjelek, Dögvész + ghúl-mutáció). |
| `assassin/` | 2 | Orgyilkos gameplay vertical slice: transiens állapot + konkrét runtime (Lehetőség négy nyitányból, háromhelyes Toxinkészlet + Dózis, Észleltség/időkorlátos rejtőzés, korlátos Járvány-nyilvántartás). |
| `storage/` | 7 | `YamlStore` (atomikus írás) + `PersistentStore` SPI + fail-closed életciklus-koordinátor. |
| `session/` | 1 | `PlayerStateCleanup` SPI (per-player állapot takarítása). |
| `utils/` | 26 | `MessageManager`, `ExperienceUtil`, `TerritoryDestination`, egyebek. |
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


### 3.1.2 Natív HUD scoreboard — konfigurálható layout

A jobb oldali natív scoreboard sorait a `hud.sidebar.layout` lista írja le; a dinamikus
játékállapot nem akadálya a szerkeszthetőségnek. A `text`, `spacer`, `separator`, `target`,
`resource`, `info` és `party` sortípusok sablonjai futásidőben kapják meg a dokumentált
`{token}` értékeket. A fejléc címe és a layout reload után élőben frissül, hibás vagy hiányzó
lista esetén pedig a beépített alapelrendezés lép életbe.

A teljes, már kibontott layout legfeljebb 15 scoreboard-sort használ. Túlcsorduláskor a
`hud.sidebar.eviction-order` szerinti opcionális szekciók esnek ki; a combat target csak harcban,
a resource csak aktív kaszt-erőforrásnál, a party pedig tagonként bővül. Az alaplayout első
`spacer` sora választja el a resource-packből érkező cím-glyphöt a felső vonaltól. A glyph
`height`/`ascent` metrikája továbbra is a resource pack font-JSON-jának felelőssége.

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
  - **Frakcióváltás- és adó-WAL** (`faction-switch-journal.yml`,
    `faction-tax-journal.yml`): a `DurableTransactionProtocol` előbb tartós prepare rekordot ír,
    majd exact wallet before/after snapshotot commitol, ezután írja a teljes membership- vagy
    treasury/debt snapshotot. Domain-write hiba esetén tartós wallet-kompenzáció történik; ha a
    kompenzáció sem írható, a journal megmarad és a globális critical-write circuit fail-closed
    állapotot tart fenn. Sikeres domain commit utáni journal-cleanup hiba nem fordítja vissza a
    már commitolt store-okat: boot recovery az all-before/all-after kombinációt idempotensen lezárja.
    Ez kontrollált process-crash recovery, nem hardverhibára vagy elvesző fsync-re vonatkozó
    elosztott exactly-once garancia.

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

### 3.8.1 Kaszt/spec rework — verziózárt kapu és adapterhatárok

A 13 kaszt / 35 specializáció reworkje külön, alapból tiltott rollout-kapu mögött épül. Az
`IceSMPCore.enable()` a gameplay store-ok betöltése előtt futtatja a
`ClassSpecDependencyPreflight` ellenőrzést. A kapu csak akkor blokkol, ha a rework és az enforcement
is aktív; legacy módban a jelenlegi production változatlanul elindul.

A pontos runtime-verziók forrása a `class-spec-dependencies.lock.yml`. A külső content- és
megjelenítési motorok nem kerülhetnek a domainbe: a `classspec/integration` portjai kizárólag stabil
UUID-t, string ID-t, immutable snapshotot és saját handle-t engednek át. CraftEngine-, BetterHud-,
ModelEngine-, MythicMobs- vagy Fancy-típus csak későbbi adaptercsomagban jelenhet meg.

A helyi 1.21.11-es runServer feladat `-Dpaper.disablePluginRemapping=true` kapcsolóval indul, hogy a
26.2-portot blokkoló legacy remapping-függés már fejlesztés közben látható legyen.

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

### 3.10 Frakciótagság és passzív-policy

#### Tagság: a fallback nem jogosultság

`FactionType` továbbra is pontosan a négy valódi frakciót jelenti. A hiányzó
`factions.yml` assignment a `FactionMembership.guest()` állapot: a játékos a
Menedék vendége, de nem `NEUTRAL` polgár. A `FactionManager` API-jának szerepei:

- `getMembership` / `getChosenFaction` / `hasChosenFaction` — autoritatív
  tagsági olvasás;
- `isEligibleForFactionBenefits`, `isMember`, `sameChosenFaction` — gameplay-
  entitlement és összehasonlítás;
- `getEconomyFaction` — kizárólag megjelenítési vagy valuta-fallback; hiányzó rekordnál
  `NEUTRAL` értéket mutathat, ezért gameplay-kapuban tilos használni;
- `everChosen` + utolsó választás PDC-történet — az assignment hiánya nem
  hozhat létre új ingyenes első választást, és nem kerülheti meg a szezonvégi
  lockoutot vagy a szezonális váltási limitet.

Quest, community goal, season source, council, tax, raid/duel/spy, caravan,
dungeon/world-boss jutalom és minden más frakciós jogosultság ugyanebből az
explicit modellből indul. Az onboarding fix `NEUTRAL` Creutzér-jutalma
vendég-útravaló; nem tesz állampolgárrá. A vendég nincs az aktuális periodikus
adóbeszedési körben, de a hiányzó assignment nem törölheti egy korábbi polgár
adóhátralékát vagy adócsalási strike-ját. A `FactionTaxDebtLedger` minden
tartozást és strike-ot `(UUID, eredet-frakció)` szerint tart nyilván: váltáskor
a régi tétel nem konvertálódik, hanem az eredeti valutából az eredeti kasszába
törlesztődik. A legacy `tax-arrears` / `tax-evasion-strikes` import eredet-frakciója a scalar
sémából nem bizonyítható, ezért aktív assignmentből vagy tartós utolsó
választásból sem kerül automatikusan kikövetkeztetésre. A rekord minden esetben
`legacy-tax-debts-unresolved` karanténban marad:
nem vesz részt automatikus beszedésben, és a következő frakcióválasztáshoz sem
kötődik hozzá. Greenfield szerveren csak explicit offline adminmigrációval vagy
kontrollált törléssel kerülhet vissza támogatott runtime state-be.

A `FactionManager` a teljes assignment+history generációt írja lemezre, mielőtt
volatile live state-et vagy lifecycle-hookot publikál. Fizetős váltásnál a
`FactionSwitchJournal` az assignment-hiányos, de historyval rendelkező admin-reset
előállapotot is teljesen rögzíti; sikertelen membership-write után a wallet
kompenzálódik, és csak sikeres durable commit után indul cooldown vagy spec-cleanup.

#### Resolver-rétegek

1. **`ConfigManager.ConfigSnapshot`** egyetlen immutable publikációs egységben tartja a
   merge-ölt YAML-t, az ugyanahhoz a generációhoz tartozó override-pathokat és a generation
   számlálót. **`FactionPassiveConfig`** ebből épít validált, immutable
   `FactionPassiveSettings` snapshotot; régi YAML + új override-index keverék nem publikálható.
2. **`FactionPassivePolicy`** Bukkit-esemény nélkül oldja fel a sebzés-, Wither-
   idő-, exhaustion- és mobtarget-döntést; ez a viselkedési unit/regressziós
   tesztek elsődleges célpontja (`factionPassiveRegressionTest`, a Gradle
   `check` része).
3. **`FactionMobContextResolver`** a runtime managerekből és PDC-markerekből
   `CORRUPTION`, `DUNGEON`, `INVASION`, `WORLD_BOSS`, `EVENT_MOB`, `QUEST_MOB`
   és `CROWN_CURSE` kontextust készít, továbbá elkülöníti a markerelt ambient
   és a vad undeadet.
4. **`FactionPassiveListener`** csak Paper/Folia adapter: eseményt csatornává
   alakít, meghívja a policyt, majd az esemény saját régiószálán alkalmazza a
   döntést. Truce-döntésnél a Paper target eventet nem pusztán cancel-eli — az
   explicit kért targetet `setTarget(null)` hívással törli, mert a cancellation az
   eredeti targetet megtartaná.
5. **`FactionPassiveService`** thread-safe, mulandó `(player UUID, mob UUID)`
   provokációs és megtorlási lease-eket tart. A nearby alert minden riasztott mobnak
   külön lease-et ad; retired/rejected scheduler callback csak a saját régi lease-ét
   törölheti, újabbat nem. Quit, kick, world/frakcióváltás, reload és disable után ürül.
6. **`FactionFoodPolicy`** a stabil signature ID-t a fogyasztás pillanatában veti össze
   az élő explicit tagsággal. Régi `FOOD_V2` stackből a beégetett consume effect az
   event `setItem` útján eltávolításra kerül; vendég vagy másik frakció nem örököl buffot.

Alap sebzés- és exhaustion-policy:

| Frakció | Csatorna | Alapérték |
|---|---|---:|
| RED | FIRE / FIRE_TICK / LAVA / HOT_FLOOR | `0.25 / 0.25 / 0.50 / 0.25` sebzésszorzó |
| RED | entitás okozta FIRE vagy továbbégés | `0.75` sebzésszorzó |
| RED | IceSMP `TUZ` spelliskola | `1.0`; csak explicit kapcsolóval érinti a RED policy |
| BLUE | FREEZE / DROWNING | `0.0 / 0.50` sebzésszorzó |
| BLUE | konfigurált természetes exhaustion ok | `0.25` cancel-esély; Hunger/script/admin ok nincs az alaplistában |
| NEUTRAL | FALL | `0.50` sebzésszorzó |
| DARK | Wither sebzés / véges effektidő | `0.50 / 0.50` szorzó, külön kapcsolókkal |

AI-precedencia, legmagasabbtól a vanilla fallbackig:

1. admin vagy scriptelt kényszercélzás;
2. boss-, dungeon-, rontás-, invázió-, event- és questkontextus;
3. koronaátok vagy más explicit harci marker;
4. provokáció és megtorlás;
5. Vérhold;
6. markerelt ambient undead-polgárjog;
7. vadoni frakciópasszív;
8. vanilla viselkedés.

Az explicit NEUTRAL policy csak spontán békés/semleges mobaggrót és külön az
Enderman spontán stare-okát szűri; tame/owner-controlled, scripted, boss/add,
eventes vagy megtorló targetet nem. A DARK ambient truce támadásig teljes lehet,
majd alapból `60 s` játékos–mob páronkénti megtorlás és `16` blokkos, külön lease-ekkel
követett undead-riasztás lép életbe. A vad DARK előny csak éjjel, targetenként
`0.50` cancel-eséllyel él. A csomagolt policyben a Vérhold **mind az ambient, mind
a vad truce-ot felülírja**; provokáció és markerelt harci content szintén harcol.

A rejtett Suttogó-státusz ugyanezt a resolver/retaliation infrastruktúrát
használja, de nem DARK polgárjog: alapból csak éjjel, targetenként `0.35`
cancel-esélyt kap, Vérhold alatt leáll, provokációra `60 s`-re megtörik. A
markerelt harci content itt is megelőzi. A truce tanúja külön
`factions.whisper.truce-witness-*` gyanúágat indíthat; ez a rejtett státusz ára,
nem faction-benefit assignment.

Minden `factions.passives.*` gameplay-érték reloadkor egyetlen config-generationből
épülő új snapshotba kerül; `/icesmp reload` után restart nem szükséges. A
sebzésszorzó véges és nem
negatív, de nincs önkényes felső plafon; az esély csak `[0,1]`. Domainhibánál a
log megnevezi a kulcsot és az érintett előny kontrolláltan kikapcsol (`1.0`
szorzó, `0.0` esély vagy `0` idő/sugár), nem csendes clamp történik. A legacy
`factions.passives.blue-hunger-slow-chance` csak akkor fallback, ha az új
`blue.natural-exhaustion-save-chance` nincs felülírva, és warning jelzi a
leszűkült exhaustion-szemantikát.

**Folia-határ:** a target event entitásának olvasása/mutációja helyi; közeli
undead-riasztásnál minden idegen mob a saját entity schedulerére kap hopot. Null
schedule, exception és retired callback külön cleanup-ágat kap; az állapot csak
UUID-ket, időbélyegeket és immutable configot tart. A dependency-free adapterteszt
a state-izolációt bizonyítja, de a valódi két-régiós entity retirement, plugin-közi
target/potion sorrend és productionközeli AI továbbra is Folia stagingkapu.

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
- **Méret:** 769 Java-fájl, ~85 000 sor; 92 `*Manager` osztály (a `managers/` csomag 123 fájl).
  Csomag-megoszlás: listeners 122, managers 122, commands 94, spells 56, gui 69, crates 14, utils 26, data 15, classrelic 14,
  items 12, relics 11, quest 7, integration 6.
- **Build:** `./gradlew clean build --no-daemon --stacktrace` futtatja a fordítást, a
  a perzisztencia-, DEV-item-, moderáció-, MOTD-, sit-, crate-, config-startup-, AFK-, HUD- és territory-capital-regressziós suite-okat.
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
## PlayerProfile platform

<!-- icesmp-doc-id: feature.platform.player_profile -->

All IceSMP-owned durable player state must enter a registered PlayerProfile section; PDC may only be runtime, item/entity metadata or a deterministic derived mirror.

### Canonical model

`PlayerProfileSnapshot` is the single logical aggregate for IceSMP-owned, restart-durable player state. It is immutable, Bukkit/YAML/SQL/HTTP independent and owner-bound by UUID. The root contains identity, lifecycle, onboarding, faction, economy, class-spec, professions, spellbook, talents, quests, companions, relics, achievements, statistics, preferences, social links, moderation and operations sections. Shared guild, party, market, claim, treasury, council, raid, season and audit-log aggregates remain separate and are referenced only by stable IDs.

### Storage boundary

Gameplay, commands, GUIs and APIs depend on `PlayerProfileRepository` and `PlayerProfileTransactionManager`, not YAML. `YamlPlayerProfileRepository` is the current adapter; a future SQL adapter must implement the same contracts. The class/spec model is `ClassSpecSection`; no independent ClassProfile aggregate or opaque ICS2 profile blob exists. Profile YAML files round-trip with literal keys on both read and write (SnakeYAML safe load/dump, duplicate keys rejected); Bukkit `YamlConfiguration` must not parse them, because it splits dotted extension keys into nested sections and silently breaks restart durability.

### Ledger-derived summaries and shared-goal splits

The shared punishment ledger (`moderation-data.yml`) stays the moderation audit authority; every ledger mutation re-derives the player's `ModerationSection` reference/summary (active punishment record IDs plus strike count) through `PlayerProfileModerationStore`, and the pre-login gate re-syncs it, so a failed publish self-heals. The weekly profession guild goal splits the same way: the week index and global counters stay a shared aggregate, while per-player contributions (`ProfessionSection.weeklyProgress` with a week marker), the per-week award marker and the pending reward XP live in the PROFESSIONS section — the award is idempotent per (player, week) over the durable owner enumeration, and the claim credits XP and clears the pending entry in one section commit. Daily budgets carry a per-budget reservation serial, so a delayed compensation can only revert the exact reservation it belongs to.

Crate settlements follow the fence-and-receipt shape: the shared crates file keeps only placements and opening-recovery fences, while per-player counts, cooldowns, the stored name and a bounded opening-id receipt list live in the STATISTICS section (`PlayerProfileCrateStore`). The profile commit happens before the fence is dropped, the in-memory `CrateLedger` is a load-time-seeded projection of the profile state, and an orphaned fence whose opening id is already receipted finalizes silently at the next load instead of double-counting. The death-to-respawn catalyst hand-back is a durable LIFECYCLE escrow (`PlayerProfileDeathEscrowStore`): the deposit commits before the respawn claim, and the claim empties the escrow in one section commit, so respawn, rejoin and crash recovery all deliver exactly once.

### Revisions and snapshots

Each section has schema and revision; the manifest carries global generation and the committed section revision map. Missing sections initialize with `-1 -> 0`; normal saves require `n -> n+1`. Full reads validate manifest generation before and after section loading, so snapshots are never an arbitrary time mixture.

### Transactions and recovery

Cross-section operations persist an owner-bound WAL and operation fingerprint, write prepared section files, atomically commit the manifest generation, close the operation receipt, apply runtime effects and clean temporary state. Restart before manifest commit rolls back; restart after commit finalizes. Duplicate operation IDs with a different fingerprint fail closed.

### Lifecycle and Folia

Join creates a session generation, recovers WALs, loads/initializes sections asynchronously, validates health, rebuilds derived mirrors, reconciles DARK/spells/companions, then marks the session ready. Quit fences mutations, drains transactions, flushes sections, cleans runtime and invalidates cache. Disable stops HTTP/admission, drains, flushes, cleans runtime and shuts executors down with a bounded timeout. Resource teardown is separate from stateful shutdown: the external resources (Bukkit service registration, HTTP adapter, repository executor, static authority) close on an idempotent always-cleanup path that also runs after a partial enable or a failed shutdown drain — refusing to save state never leaves a listener or executor behind. Bukkit entity access remains in owner/region-thread adapters; YAML I/O is asynchronous.

### YAML format

Profiles live under `plugins/IceSMP/player-profiles/<uuid>/`. `manifest.yml` records owner UUID, format, schema, generation, lifecycle status, timestamps and every section schema/revision/digest. Each section is a separate structured YAML file with `format: ICESMP-PLAYER-PROFILE-SECTION`, stable section ID, schema, revision, updated timestamp, `data` and preserved `extensions`.

The serializer is deterministic and key based. There is no Java serialization and no Base64-encoded complete profile. Missing documented optional fields use safe defaults; missing mandatory fields, wrong types, owner/section mismatches, duplicate normalized keys, invalid UTF-8, oversized input and skipped revisions fail closed. Unknown extension namespaces are preserved. Atomic replacement uses a temporary file, file sync and directory sync where supported; orphan temporary files are removed during recovery.

A corrupt section is copied to immutable evidence and quarantined independently. Identity or manifest corruption blocks the whole profile; subsystem corruption blocks only that subsystem unless a documented lossless default is safe. Recovery requires an explicit, idempotent admin operation and never deletes evidence.

### Internal Java API

`IceSMPPlayerProfileApi` is registered through Bukkit `ServicesManager`. It exposes immutable snapshots, section snapshots, name lookup, filtered public DTOs and a listener subscription. It never exposes repository adapters, YAML, file paths or mutable Bukkit objects, and all storage reads are asynchronous.

### HTTP API v1

The adapter is disabled by default and binds to `127.0.0.1` only when explicitly enabled; the adapter (and its executor) is not even instantiated while disabled. Public endpoints respect profile visibility. SELF bearer credentials are bound to one player UUID; ADMIN credentials may read health, quarantine and moderation/operations summaries. Authentication resolves before any storage read: the by-name endpoint answers 403 to anonymous callers without touching the repository (no unauthenticated O(N) name scan and no 403-vs-404 existence oracle), a SELF token only resolves its own last known name, and only an ADMIN token may run the global name lookup. Tokens are deployment secrets, are digested in memory and never logged. The adapter enforces rate, request/response size and timeout limits, supports ETag/`If-None-Match`, returns sanitized 403/404/409/429/500 responses and drains on shutdown. It has no write endpoints.

The machine contract is [`openapi/player-profile-v1.yaml`](openapi/player-profile-v1.yaml).

### SQL replacement contract

A future `SqlPlayerProfileRepository` and transaction manager must preserve owner binding, per-section CAS, manifest-equivalent snapshot generation, quarantine semantics, operation fingerprints and recovery results. Gameplay managers, DTOs, commands, GUI and HTTP code must not change. A YAML importer performs structured decode, domain validation and repository saves; it never guesses from PDC or invokes gameplay migration.

### Authority matrix

This matrix is versioned together with `scripts/player_profile_authority_allowlist.json` and enforced by `scripts/check_player_profile_authority.py`, which permits only PlayerProfile authority, runtime state, derived mirrors, item/entity metadata and explicit shared-aggregate references; the historical `TRANSITION` rows are complete.

| Current data | Current authority | Final section or aggregate | Runtime mirror | External visibility | Transition |
| --- | --- | --- | --- | --- | --- |
| class/spec, class XP, loadouts, DARK seals, Soulforge progression | PlayerProfile class-spec section | `class-spec` | rebuildable PDC/UI mirror | public/self/admin filtered | complete in root |
| spell grants, selection, favorites, mastery | legacy player PDC/managers | `spellbook` | selected-spell UI mirror | self/admin; selected public by privacy | stacked spellbook scope |
| talent points and purchased talents | legacy player PDC/manager | `talents` | GUI cache only | self/admin | stacked spellbook/talent scope |
| faction membership/history/sinner/player cooldowns | managers/PDC/YAML | `faction` | scoreboard/tag mirror | privacy filtered | stacked faction scope |
| wallets, bank, tax debt, pending refunds | player stores/managers | `economy` | display cache | self/admin | stacked economy scope |
| professions, XP, level, specialization, recipes | PDC/managers | `professions` | HUD/GUI mirror | self/public summary | stacked professions scope |
| quest state, objectives and reward receipts | quest managers/YAML/PDC | `quests` and `operations` | tracker UI | self/admin | stacked quest scope |
| pets/minions and durable companion state | PlayerProfile namespace + runtime manager | `companions` and `class-spec` | live entity map | privacy filtered | root plus lifecycle hardening |
| achievements, bestiary and milestone claims | managers/PDC/YAML | `achievements` | toast/UI cache | privacy filtered | stacked progression scope |
| kills/deaths/events/season counters | stats managers/YAML | `statistics` | scoreboard cache | privacy filtered | stacked statistics scope |
| language/HUD/scoreboard/notification/privacy | config/PDC/managers | `preferences` | online UI state | public visibility flags/self | stacked preferences scope |
| moderation case details | global moderation aggregate | reference/summary in `moderation` | runtime enforcement cache | admin only | reference integration |
| guild/party/market/claim/treasury/raid/season | separate global aggregates | stable reference only | runtime membership cache | composed PlayerView | remain separate |

Run `python3 scripts/check_player_profile_authority.py --root .` to verify the exact code-level inventory.

### Full authority scope

The greenfield transition of every IceSMP-owned, restart-durable player domain to the modular PlayerProfile repository is complete. The following domains use PlayerProfile sections as their sole durable authority:

- spellbook, spell mastery and favorites;
- talents;
- faction membership, membership history, sinner state and player cooldowns;
- wallet, bank, tax debt and pending refunds;
- professions, XP, specialization and learned recipes;
- quests, objectives and operation receipts;
- durable companion state;
- achievements, bestiary and milestone claims;
- statistics and counters;
- preferences, HUD, scoreboard, notifications and privacy;
- moderation reference and summary state.

Shared guild, party, market, claim, treasury, council, raid, season and audit-log aggregates remain separate. PlayerProfile may store only stable references to those aggregates.

### Forbidden runtime patterns

No gameplay path may treat any player PDC key, standalone player YAML file or manager-owned durable map as an authority for the domains above. There is no legacy migration, dual read, dual write, fallback or runtime kill switch.

PDC remains permitted only for:

- rebuildable runtime or UI mirrors;
- item metadata and provenance;
- entity metadata and short-lived runtime identity;
- non-authoritative integration hints that can be recreated from PlayerProfile or shared aggregates.

### Required invariants

- Every durable mutation is owner-bound and section-CAS protected.
- Cross-section mutations use the PlayerProfile transaction/WAL protocol.
- Cache state becomes authoritative only after durable commit.
- Join, quit, reconnect and plugin-disable preserve session fencing and bounded drain semantics.
- Decode, owner, revision or persistence failure is fail-closed and preserves evidence.
- Folia entity access remains on the owning entity/region scheduler; persistence I/O remains asynchronous.
- Runtime mirrors are rebuilt from PlayerProfile after join and invalidated on quit/reset.

### Merge-readiness gates

- Java 21 `clean build` and the complete regression suite pass.
- Every migrated domain has targeted persistence, CAS, recovery, reconnect and lifecycle regressions.
- `check_player_profile_authority.py` reports no unknown, stale, invalid or transition authority findings.
- No player-owned durable PDC/YAML/map authority remains outside explicitly approved metadata/mirror categories.
- Repository consistency, Markdown links, tooling self-tests and strict repository/documentation inventory pass with zero blocking or review-required findings.
- The authority matrix marks every player-owned domain complete.

## Class Relic Framework

A kaszthoz kötött, világ-egyedi Class Relic-ek KÜLÖN domainrétege a generikus relikvia-rendszer
fölött (`classrelic/` csomag). A generikus `RelicDefinition` érintetlen: a Mételytépő, a
szárny-ereklyék és minden más relikvia változatlanul működik; kaszt-fogalom (class, resonance,
awakening) kizárólag a `ClassRelicBinding`-ben él (`relics.class-relics.*` config, fail-fast
betöltéssel: ismeretlen class/spec, parent-eltérés vagy duplikált class/relic kötés a teljes
szekció elutasítása — a korábbi katalógus-pillanatkép marad publikálva, félbetöltött registry
nincs). A schema strict: a hiányzó opcionális szekció defaultolhat, de a jelen lévő rossz
típusú érték (pl. `resonances: "abc"`) reject; az Awakening `cooldown-seconds` egész,
nem-negatív és korlátos (a felső határ mellett a ready-at aritmetika nem tud túlcsordulni),
tört érték nem csonkolódik. A candidate a publish előtt a generikus relic-registryvel is
kereszt-validált: nem létező fizikai relicre mutató kötés a TELJES candidate-et elutasítja —
a `require-complete-catalog` kapu így kitalált relic-rosterrel sem PASS-olhat. A
`relics.enabled: false` explicit framework-kapu (use-site élő-config): minden feloldás
`FRAMEWORK_DISABLED`, a Class Power, a Resonance és az Awakening is inaktív — nem a
birtoklás-szken mellékhatása dönt.

**Authority-határok.** Két, szándékosan KÜLÖN igazság-forrás: (1) a világ-szintű relic-store
(`RelicManager` → `RelicWorldStateStore`, relics.yml) mondja meg, kié a relic, elveszett-e
(lost/reclaim) és mikor kész újra az Awakening — ez NEM játékosprofil-domain; (2) a Profile v2
(`ClassSpecProfileGateway` → `ClassSpecSection`) mondja meg a kasztot, az aktív specializációt,
a loadout-státuszt és a SEALED-állapotot. A framework egyiket sem duplikálja a másikba.

**Single-writer világ-relic perzisztencia, publish-commit sorrenddel.** A világ-relic
aggregátum (ownership, lost/reclaim, awakening, művelet-receiptek) immutable
`RelicWorldStateSnapshot`-ként publikált; minden logikai mutáció a `RelicWorldStateStore`
EGYETLEN szerializált kritikus szekciójában candidate pillanatképet épít, azt írja durable-re,
és CSAK sikeres írás után cseréli be (volatile publish) — a runtime-ból látható committed
állapot mindig részhalmaza a durable állapotnak, commit előtti candidate-et olvasó SOHA nem
láthat, sikertelen írásnál a candidate egyszerűen eldobódik. A betöltés/reload ugyanígy
atomikus: a teljes candidate lokálisan épül fel és egyetlen cserével publikálódik — konkurens
olvasó sosem lát üres/félig-töltött köztes állapotot. Az Awakening-arm atomikus (két konkurens
hívásból pontosan egy ARMED), az eredmény `PERSISTENCE_FAILED`, ha a lemez-írás bukik. A
lost-mutáció owner-kötött: `markLost`/`clearLost` csak a bizonyított aktuális tulajdonossal
fogadható el (stale példány korábbi gazdájának halála nem jelölheti el másvalaki élő relicét),
és árva lost állapot (tulajdonos nélkül) se memóriában, se a fájlban nem létezhet.

**Fizikai kézbesítés/transfer recovery-protokoll.** A claim/reclaim (`giveRelic`) és a PvP
transfer a világ-oldali commitot EGY durable írásban végzi a fizikai mellékhatás függő
receiptjével együtt (`operations.<relic>`): claim = ownership + lost-törlés + kézbesítés-receipt;
transfer = új tulajdonos + PDC-átírás-receipt — a fizikai lépés mindig a commit UTÁN fut.
Crash bármely lépés után determinisztikusan helyreáll a join-recovery-ből: CLAIM/RECLAIM
receipt → kézbesítés csak akkor, ha a tulajnál nincs példány (duplikátum nem születhet);
TRANSFER receipt → az új tulajnál lévő példány PDC-átírása (amíg nincs nála, a receipt
függőben marad). A `canUse` fail-closed: aktív központi tulajdonos nélkül a fizikai példány
nem használható — persistence-hiba utáni árva singleton nem működhet magától, a jogos
állapotot a claim/transfer/join-sweep/recovery állítja helyre. A generikus definíció-registry
kikapcsolt runtime mellett is betöltött (definitions ≠ gameplay-enabled), így a Class Relic
katalógus létezés-validációja disabled állapotban is fut — validálatlan katalógus akkor sem
publikálható.

**OWNER ≠ ACTIVE POSSESSION.** Az ownership önmagában nem ad gameplay-erőt: a
`requires-physical-possession` kötésnél a használható fizikai tárgynak a játékosnál kell
lennie. Lost/reclaim állapotban (a tárgy halálkor megsemmisült, a tulajdon él) minden
relic-erő szünetel; sikeres újraidézés után aktiválható újra.

**Három mechanikai réteg.** (A) *Class Power*: állandó, számítás közben lekérdezett modifier —
a fogyasztók csatornán kérdeznek (`ClassRelicService.modifier(playerId, RelicModifier.…)`),
relic-id-t és kaszt-vizsgálatot soha nem hordoznak; a modifier-út UUID-only (Player-dereferencia
és inventory-olvasás nélkül), ezért idegen régió-szálról is biztonságos hot path. (B) *Spec
Resonance*: tipizált szemantikus jelzésekre reagáló specializációs mechanika — a
`ClassGameplaySignal` sealed rekord-család hordozza az actort, a cél-identitást (UUID), a
mennyiséget és a tageket (a payload-hordozó eseményekhez a Generic alak nem használható,
stringly-typed payload nincs); a routing a `ClassRelicActivationResolver`-ben él, az
implementáció `ClassRelicResonanceHook`-ként regisztrálható, és a hook a
`ClassRelicResonanceContext`-ben a régió-helyes `actor` Player referenciát is megkapja —
globális `Bukkit.getPlayer` lookup a hookban tilos és szükségtelen. (C) *Awakening*: a
világ-egyedi nagy képesség kerete — a nagy cooldown a RELIC-kel utazik (relic-id →
awakening-ready-at abszolút időbélyeg a világ-szintű store-ban), gazdacserénél nem nullázódik
és restartot túlél; az arm a store atomikus műveletén megy át; a rövid proc-cooldownok
maradnak runtime-állapotok.

**Központi feloldás.** Minden döntés (profil használható? jó kaszt? tulajdonos? nála van?
melyik resonance?) egyetlen pontban, a pure `ClassRelicActivationResolver`-ben dől el —
a konkrét képességek csak a kész `ClassRelicActivation`-t fogyasztják. SEALED specializáció
SOHA nem rezonál, de a kaszt-szintű Class Power tovább élhet (DARK seal/unseal invariáns);
csak gameplay-re használható profil (READY + nem blokkolt session, `isGameplayUsable`)
aktiválhat.

**Folia és birtoklás-pillanatkép.** A resolver, a katalógus és a jelzések pure rétegek (nem
függenek régió-szálaktól). A fizikai birtoklás immutable pillanatképként él
(`PossessionSnapshot`): KIZÁRÓLAG a játékos saját régió-szálán készül (join-kori első szken +
másodpercenkénti frissítés a játékos schedulerén), és a szken a kanonikus
`RelicManager.canUse`-zal validál — azonos relic-id-jű, de stale/rossz gazdához kötött példány
nem ad erőt. Az UUID-only olvasók fail-closed szabállyal olvassák: ismeretlen vagy TTL-en túli
pillanatkép = nincs birtoklás (korlátlan ideig élő stale "true" nem létezhet; a maximális
konzisztencia-ablak a TTL, ~2,5 mp). Kritikus világ-relic mutáció (transfer, lost/reclaim,
give, expiry) és halál AZONNAL invalidálja az érintett pillanatképeket. A resonance-dispatch
kikényszeríti az actor-régió szerződést: idegen szálról érkező hívást maga hoppolja az actor
schedulerére; cél-oldali effekt idegen entityn csak a cél schedulerére hoppolva megengedett.

**Evoker pilot.** A `sarkany_tojas` az első migrált Class Relic: a korábbi
`ResourceBonusService`-beli ownership+isEvoker hardcode megszűnt, a max-Essence bónusz a
generikus `CLASS_RESOURCE_MAX` csatornán érkezik (változatlan, configolható 10%). A
Devastation→`dragon_echo` és Preservation→`temporal_echo` routing él, mindkettő inert
(enabled: false); az `unborn_dragon` Awakening kerete és durable cooldownja kész, gameplay
nélkül.

**13/35 teljesség-szerződés.** A `relics.require-complete-catalog: false` fejlesztési állapot:
a katalógus részleges lehet. A teljes class rework kapuja a kulcs true-ra állítása — akkor
minden classnak pontosan egy relic és minden specializationnek pontosan egy resonance
kötelező, különben a betöltés (és a CI) bukik. A class reworknek csak a gameplay-oldalt kell
hoznia (mechanikák, szemantikus események, resource-hookok, ability-tagek): az ownership, a
birtoklás-validáció, a binding-registry és a cooldown-perzisztencia ebből a keretből jön.

## Config GUI coverage

The admin GUI is an explicit **runtime-safe allowlist**, not a blind renderer for every scalar in the content configs.
The build-time `configGuiCoverageRegressionTest` merges every supported YAML file and proves:

- every GUI path exists exactly once;
- GUI type and packaged default type agree;
- numeric defaults are inside the declared range;
- enum defaults are among the declared options;
- all scalar entries under `world-events.safety.*`, `moderation.vanish.*` and
  `territory.mob-rules.doom-gate.*` are exposed;
- every remaining scalar is intentionally file/command-only (content records, lore, rewards, item definitions,
  spell/quest tables or advanced startup tuning), rather than accidentally omitted.

The test prints the exact `total / displayed / intentionally_excluded / missing / stale / duplicate` counts on every build.
A new scalar under a mandatory runtime-admin prefix fails the build until a matching GUI component is added.

### Config GUI transaction semantics

Opening the menu captures the effective values, packaged defaults, config generation and SHA-256 fingerprint of `config.yml`.
Clicks only modify an in-memory per-admin session. **Save** performs one asynchronous batch write; **Cancel**, closing the
inventory or disconnecting writes nothing. Middle-click removes the override and restores the packaged default.
A second admin save or external file edit makes an older session stale; stale sessions are rejected without overwriting data.

Entries display whether their effect is live, applied by a reload hook, or requires a restart. In particular the faction-tax
scheduler toggle/interval is restart-required; event safety and vanish capabilities are live/reload-safe.

## Frakcióhoz kötött játékosnév-színek

A játékosnév-színek egyetlen központi palettát használnak minden támogatott felületen.

| Frakció | Natív szín | Legacy/TAB kód | Vizuális szerep |
|---|---|---|---|
| RED / Láng | RED | `§c` | támadó, tüzes identitás |
| BLUE / Fagy | BLUE | `§9` | hideg, kék identitás |
| NEUTRAL / Menedék | GREEN | `§a` | a Smaragdkő/Ryanora lore-szín, a DARK-tól jól elkülönülő identitás |
| DARK / Kitaszított | DARK_GRAY | `§8` | sötét, komor identitás |
| nincs vagy ismeretlen tagság | WHITE | `§f` | fail-safe alapállapot |

A NEUTRAL korábbi szürke (`GRAY` / `§7`) színe megszűnt. A DARK nem kap lich-kék árnyalatot, mert az a vanilla névszín-készletben túl közel kerülne a BLUE frakcióhoz.

Érintett felületek:

- natív tablist játékosnév;
- fej fölötti scoreboard-team nametag;
- HUD tablist-fallback;
- HUD frakcióérték;
- natív async chat formázás;
- `%icesmp_faction_color%` PlaceholderAPI-kimenet külső TAB/scoreboard pluginokhoz.

A paletta élő-configgal felülbírálható: `tablist.faction-colors.<frakció|guest>` (NamedTextColor nevek); a defaultot a `FactionDisplayColorPolicy` adja, minden név-felület (tab, nametag, HUD, chat, `%icesmp_faction_color%`) ugyanazon a feloldón megy át. A kézi staging-ellenőrzés az [admin kézikönyv staging-mátrixai](ADMIN_GUIDE.md#kiegészítő-staging-mátrixok) közt található.

## Runtime hardening szerződések

A moderációs láthatóság, a claim-geometria, a BlockDisplay-határ és a DARK territory-spawn mérvadó szerződései.

### Vanish

- `icesmp.moderation.vanish.see` is explicit-only (`PermissionDefault.FALSE`) and is not inherited by OP, the moderation
  bundle or `icesmp.admin.all`.
- Every non-observer viewer gets both `hidePlayer(plugin, subject)` for the in-world entity and
  `unlistPlayer(subject)` for the per-viewer player list.
- Separate ownership ledgers restore only IceSMP-owned `showPlayer`/`listPlayer` pairs when vanish ends or the plugin stops.
- Visibility is reasserted after viewer join, subject toggle, teleport, world change, respawn and a delayed tracking rebuild.
- Damage immunity remains an event capability; no persistent `Player#setInvulnerable` state is written.

### Territory-style normal claims

- Quick square and two-corner rectangle claims remain compatible.
- Ordinary claims now also support `point`, `undo`, `clearpoints`, `points`, `polygon` and a dedicated `polywand` flow.
- The immutable `ClaimShape` is an exact set of claimed X-Z columns and supports concave simple polygons.
- Membership, overlap, column pricing, territory checks, exact WorldGuard row spans, YAML persistence, chunk lookup,
  particle preview and BlockDisplay rendering all consume the same shape.
- Polygon area is bounded by `claims.area-max-columns`; the vertex count is unlimited by default (`claims.polygon-max-points: 0`), because the rasterized column set makes runtime checks independent of it.
- Rasterization uses budgeted row scanlines rather than scanning the full bounding rectangle. Perimeter length, continuous
  area and every produced column are checked before publication; long/thin hostile inputs fail closed.
- Malformed stored polygons are rejected and skipped instead of silently widening to their bounding rectangle.

### Exact bounded BlockDisplay boundary

- Every exact X-Z boundary column owns a separate vertical BlockDisplay segment.
- Each segment starts at the claim's inclusive `minY` and ends at its inclusive `maxY`.
- No display block is created below or above the actually claimed Y range.
- Rectangle and concave polygon boundaries use the same stored Y band.
- RegionScheduler ownership and per-player preview cleanup remain Folia-safe.

### Stable DARK territory spawning

- DARK ambient undead use the shared `resolveSafeStandingLocation` contract.
- The floor must be solid, occluding, non-gravity, non-liquid and non-hazardous; three body blocks must be passable.
- Candidates must still be inside the exact target territory and pass claim/region rules.
- Each mob receives at most `dark-undead.spawn-attempts-per-mob: 12` distinct attempts.
- No valid location means no spawn. There is no airborne or close fallback.

### Restored original claim Y behaviour

- Claims are again bounded 3D volumes: exact rectangle/polygon X-Z shape plus inclusive `minY..maxY`.
- New quick claims use the player's Y; rectangle selections use the two Y values' midpoint; polygons use the first boundary point's Y.
- Packaged defaults remain the proven original values: `default-height: 20`, `default-depth: 20`.
- `/claim extend up|down` again expands by `y-extend-step: 5` for the original per-column burned cost.
- X-Z overlap stays exclusive, so vertically separated claims cannot be stacked over the same footprint.
- The BlockDisplay boundary is created only from `minY` through `maxY`; no wall exists at unclaimed Y levels.

### Claim persistence guarantees

- World upper bounds are stored and restored as inclusive values (`getMaxHeight() - 1`).
- Legacy `world;chunkX;chunkZ` keys are structurally validated before conversion.
- Claims from the temporary X-Z-only format, where both Y fields are absent, retain protection by receiving the full known
  world-height band and emit an operator warning instead of silently becoming a `0..0` claim.
- A record containing exactly one of `min-y` or `max-y` is corrupted, not an X-Z-only record; it is rejected fail-closed
  instead of being silently widened to the full world height.
- Stored Y bounds are clamped to a loaded world's current legal range; reversed or fully out-of-world ranges fail closed.
- One malformed trusted-player UUID is isolated to that trust entry and cannot discard the enclosing claim.
- One malformed claim entry is isolated from the rest of `claims.yml` and cannot abort the complete claim load.

### Viewer-private display guarantees

- Viewer-specific BlockDisplays set `visibleByDefault=false` inside the spawn consumer before the entity enters normal
  client tracking.
- The selected viewer is revealed only through `Player#showEntity` on the viewer's own entity scheduler.
- The compatibility `showOnlyTo` path acquires the effect entity's own Folia scheduler before changing default visibility.
- A public viewer-scoped spawn API is available for new private effects so callers do not need post-spawn hiding.
- The aurora veil also uses this pre-tracking viewer-scoped spawn API and no longer becomes public before being hidden.
- `RuntimeHardeningRegressionSuite` asserts pre-tracking privacy, aurora usage and effect/viewer entity-scheduler ownership.

### Other retained hardening

- bounded world-event/invasion/boss spawn safety and reservations;
- transactional multi-admin config GUI with stale-write rejection;
- deterministic and atomic profession recipe reload, including removal of one exact fishing-rod duplicate;
- Java/YAML item-model validation against the manifest and checked-in resource pack;
- reversible DARK daylight/zombification capability lifecycle.

### Measured audit results

- Config schema scalar entries: **9594**
- GUI-displayed entries: **203**
- Intentionally file/command-only entries: **9391**
- Missing, stale or duplicate GUI entries: **0 / 0 / 0**
- Profession recipes: **437**, after removal of one proven exact duplicate
- Profession recipe key duplicates / semantic duplicates: **0 / 0**
- Used GUI/item models: **269**
- Manifest models / checked-in pack models: **298 / 298**
- Missing manifest / pack mappings: **0 / 0**

### Automated hardening coverage

`RuntimeHardeningRegressionSuite` covers vanilla rectangle compatibility, concave polygon membership and wilderness notches,
exact overlap/boundaries, self-intersection and oversized-input rejection, bounded scanline and fail-closed persistence contracts,
entity plus tab-list vanish ownership, exact minY/maxY BlockDisplay clipping, restored vertical extension and stable DARK standing
locations with finite retries.

The full repository `check` also includes event-spawn safety, config transaction/coverage, profession recipe audit and all
previously registered regression suites.

## World-event spawn-védelem

A world-eventek helyét az `EventSpawnGuard` választja és publikálja. A guard a kezdeti
spawn előtt egységesen ellenőrzi a játékostávolságot, a látóirányt, a védett területeket,
a víz- és partpuffert, a teljes event-footprintet, a lejtést, a biomprofilt, a world
bordert és a friss eseményhelyek memóriáját.

### Alapértelmezett viselkedés

- A minimum játékostávolság legalább 192 blokk, de a tényleges Paper send/view distance
  és a 32 blokkos margó ezt automatikusan megemelheti.
- A 110 fokos, 384 blokkos konzervatív nézési kúp elutasítja a játékos előtt lévő
  helyeket. Ez szándékosan szigorúbb a blokkonkénti ray trace-nál, és Folia alatt nem
  olvas idegen régiót.
- A kereső 32 jelöltet próbál; egyszerre alapból két keresés futhat, egy keresés legfeljebb
  96 egyedi chunkot érinthet és 5 másodperc után watchdog zárja le.
- Csak már legenerált chunk tölthető vissza aszinkron módon. A kereső sosem generál új
  világterületet, és nem végez szinkron chunk-loadot régiószálon.
- A kiválasztott hely alapból 3 másodperces érkezési előjelet kap, majd közvetlenül a
  tényleges spawn előtt újra lefut a teljes validáció.
- Az utolsó eventhelyek 45 percig, 256 blokkos körben nem használhatók újra.

### Speciális profilok

- `stranger`: 64–96 blokkos helyi keresés, 48 blokk minimum, saját nézési kúp. Az Idegen
  így hallótávolságban marad, de nem a játékos előtt materializálódik.
- `escort`: távoli, teljes footprinttel validált indulóhely és négypontos útvonalvizsgálat.
- `escort-route` és `escort-wave`: a már aktív esemény belső mozgását és hullámait nem
  tiltja le a játékosok megérkezése, de a víz-, terep- és protection szabályok megmaradnak.
- `meteor`, `world-boss`, `invasion`, `cultists`, `wild-hunt`, valamint a karavánok saját
  footprint-, lejtés- és biomprofilt használnak.

### Meteor-helyreállítás

A meteor a kráter létrehozása **előtt** kiírja az érintett normál blokkok teljes
`BlockData` állapotát a `meteor-restore.yml` fájlba. Tile entityt (láda, hordó, tábla,
spawner stb.) nem ír felül, mert azok NBT-jét a BlockData nem őrizné meg.

- Normál lejáratkor a visszaállítás chunkonként, a megfelelő Folia-régióban fut.
- Graceful disable alatt ugyanez a helyreállítás indul el.
- Ha a scheduler már nem fogad taskot, vagy a folyamat félbeszakad, a recovery fájl
  megmarad, és a következő indulás world-UUID alapján folytatja a helyreállítást.
- A recovery fájl csak az összes chunk sikeres visszaállítása után törlődik.

### Fix világboss-anchorok

A legacy fix/random világboss-anchor a saját chunkjának egzakt középpontjára normalizálódik.
A meglévő `[-8, 8)` véletlen eltolás így bizonyítottan ugyanabban a chunkban marad, tehát
a probe oszlopot mindig az azt birtokló Folia-régiótask olvassa.

### Spawn-diagnosztika

Játékos adminnal:

```text
/events debug spawn <event-kulcs>
```

A parancs valódi, spawn nélküli keresést futtat, majd megmutatja a dinamikus minimumot,
a keresési gyűrűt, a footprintet, az érintett chunkokat, az eltelt időt és az elutasítási
okok darabszámát.

### Spawn-védelem a config menüben

A vízvédelem három kulcsa a `Világesemények` kategóriába kerül. Ha a kategória más
fejlesztések miatt elérné a 45 elemű kapacitást, a további placement-beállítások egy
külön `Event spawn-védelem` kategóriában jelennek meg. Minden itt szereplő beállítás
élőben olvasódik.

A kötelező kézi staging-próbák az [admin kézikönyv staging-mátrixai](ADMIN_GUIDE.md#kiegészítő-staging-mátrixok) közt találhatók.

## Szakma-recept és item-audit

| profession | recipe key | item | problem | previous behaviour | fixed behaviour | balance rationale | migration / compatibility |
|---|---|---|---|---|---|---|---|
| Fisher | `egyszeru_horgaszbot` / `kezdo_horgaszbot` | Fishing Rod | Exact semantic duplicate: `3×STICK + 2×STRING → FISHING_ROD` | Two progression records represented the same craft and could diverge by load order | `egyszeru_horgaszbot` is canonical; `kezdo_horgaszbot` and its recipe are removed | One unlock/cost path prevents fake progression depth and recipe ambiguity | Existing fishing rods remain vanilla-compatible; no item migration is required |
| All | `icesmp:prof_*` legacy masterworks | PDC-stamped masterwork tools/books | Reload/disable did not remove previously registered Bukkit keys | Disabled or removed recipes could remain craftable until restart; repeated registration could be rejected | Manager owns a deterministic key set, removes it before rebuild and on disable, then registers once | No duplicate registry entries or stale craft path | Already crafted items remain valid; only future crafting availability changes |
| All | Config catalog (438 before, 437 after) | All profession outputs | No early semantic collision validation, and a rejected reload could expose the already-cleared or partially rebuilt live maps | Similar/duplicate recipes were accepted silently; later validation failures could leave an incomplete runtime catalog | Sorted loading plus canonical input/output fingerprints validate a private candidate; immutable maps and recipe metadata are published with one `volatile` snapshot replacement | Exact duplicates fail early without destabilising active crafting, while intentional recipes with distinct input or output remain independent | Existing runtime generation remains active when a reload is rejected; no item migration is required |
| All | Unique profession outputs | Resource-pack model | Item/model references were distributed across config and pack | Missing mappings were only found visually | Build validator checks every referenced ITEM_MODEL against the manifest and checked-in pack | Visual identity remains stable without changing public model IDs | No public model ID changed; vanilla `PAPER` is the explicit no-pack fallback |

The automated audit verifies **437 recipes**, zero duplicate keys, zero semantic duplicates, immutable recipe metadata,
transactional catalog reload publication, exact unique/custom ingredient matching, profession/level gates, deterministic key order,
output-model presence and removal of stale Bukkit registrations after reload or disable.
