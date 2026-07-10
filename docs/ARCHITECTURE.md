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
       ├─ konstruktor          → ~40 manager felépítése (szigorú sorrend), registerSpells()
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
| `core/` | 1 | `IceSMPCore` — összeszerelés, életciklus, ütemezés. |
| `managers/` | 39 | Üzleti logika és állapot (gazdaság, frakciók, kasztok, szakmák, pet, stb.). |
| `listeners/` | 44 | Bukkit eseménykezelők (gameplay + GUI-klikk). |
| `spells/` | 36 | Spell-rendszer: `Spell` SPI, `BaseSpell`, `ConfiguredSpell` builder, `SpellCatalog`, egyedi spellek. |
| `commands/` | 27 (+ al-csomagok) | Parancsok. A `commands/<terület>/` al-csomagok a dispatch-stílusú alparancsokat tartják. |
| `gui/` | 21 | Inventory-menük + `GuiUtil` közös helperek + adat-vezérelt `CommandMenu` rendszer. |
| `data/` | 10 | Enumok és értékobjektumok (`CurrencyType`, `FactionType`, `JobType`, `SpecializationType`…). |
| `relics/` | 6 (+ `ability/`) | Relikvia-keret: `RelicRegistry`, `RelicDefinition`, triggerek. |
| `items/` | 5 | Item-gyárak (katalizátor, befogó item…). |
| `storage/` | 2 | `YamlStore` (atomikus írás) + `PersistentStore` (load/save SPI). |
| `session/` | 1 | `PlayerStateCleanup` SPI (per-player állapot takarítása). |
| `utils/` | 3 | `MessageManager`, `ExperienceUtil`, egyebek. |

---

## 3. Architektúra-minták (ezeket kövesd)

A rendszer egységes mintákra épül. **Új kódnál mindig a meglévő mintát használd** — ne vezess be
párhuzamos megoldást.

### 3.1 Konfiguráció — több-fájlos merge
`ConfigManager.load()` egyesíti a `config/<alrendszer>.yml` fájlokat (alapértékek), majd rájuk
olvassa a fő `config.yml`-t (override, ez nyer). A betöltött fájlokat a `CONFIG_FILES` tömb sorolja
fel. Minden hívó a megszokott `getInt/getDouble/getString("alrendszer.kulcs", default)` API-t
használja — a kulcs-útvonalak a fájlok között oszthatatlanok.

Betöltés után a `ConfigValidator.validate(...)` **konvenció-alapú** ellenőrzést futtat a teljes
kulcstéren (soha nem dob, csak a konzolra figyelmeztet): a `material`/`materials` kulcsok valós
`Material`-t adnak-e, a `currency` kulcsok `OWN`/valuta-nevek-e, a `…percent` kulcsok a 0–100
tartományban vannak-e, a `…-minutes/-hours/-seconds/-ticks/-millis` kulcsok nem negatívak-e. Így az
admin-elgépelések (rossz item-név, kilógó százalék) tiszta log-figyelmeztetésként jelennek meg
ahelyett, hogy némán az alapértékre esnének vissza.

### 3.2 Üzenetek — több-fájlos merge + formátum-tudatos rendering
`MessageManager.load()` egyesíti a `messages/<csoport>.yml` fájlokat (a `MESSAGE_GROUPS` szerint),
majd a fő `messages.yml`-t override-ként. Rendering: a `get`/`getMessage`/`getComponent` **mind**
formátum-tudatos — **MiniMessage** ha a szövegben `<...>` tag van ÉS nincs legacy `&`/`§` kód,
egyébként legacy. Sose feltételezd egyik formátumot sem; használd a generikus API-t.

### 3.3 Perzisztencia — atomikus írás + életciklus SPI
- **`storage/YamlStore.saveAtomic(file, yaml)`**: egyedi temp-fájl + atomikus rename (konkurens-biztos).
  **Minden** YAML-mentés ezen át megy — soha ne `yaml.save(file)` közvetlenül.
- **`storage/PersistentStore { load(); save(); }`**: a 12 fájlt-író manager implementálja. Az
  `IceSMPCore` egy `List<PersistentStore>`-t iterál: `load()` az enable-ben, `save()` a disable-ben
  (a player-cleanup ELŐTT, hogy ne vesszen adat).

### 3.4 Parancsok — két stílus
- **Dispatch (preferált, alparancsos):** `AbstractDispatchCommand` bázis + `Subcommand` SPI.
  A bázis kezeli a map-et, a diszpécst, a helpet és a tab-complete-et; a parancs a konstruktorára
  zsugorodik (lásd `CurrencyCommand`, `JobCommand`, `FactionCommand`, `BankCommand`). Üzenet-kulcsok:
  `messages.<név>-unknown-subcommand`, `messages.<név>-help-header`, `messages.<név>-help-<alparancs>`.
- **Egyrészes / implicit-default:** néhány parancs (Market, Pet, Soul, Spell, Events…) üres argra
  műveletet végez (nem helpet ad), vagy nem `args[0]`-ra diszpécsel. Ezek szándékosan külön
  `BasicCommand`-ok — a dispatch-bázis nem modellezi ezt a szemantikát.

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
- **Config-driven balansz-felülbírálás** (`config/spells-balance.yml`): `IceSMPCore.applySpellBalanceOverrides()`
  az `enable()`-ben, `configManager.load()` után egyszer lefut, és minden `ConfiguredSpell`-re alkalmazza
  a `spell-balance.<id>` alatti kulcsokat (`ConfiguredSpell.withBalanceOverrides`, immutable copy). A ~49
  bespoke (stateful) spell nincs benne — a `spells-balance.yml` fejlécében fel van sorolva. Mivel a spellek
  csak indításkor regisztrálódnak, a fájl módosítása után szerver-újraindítás kell (`/icesmp reload` nem elég).

### 3.6 GUI — közös helperek + adat-vezérelt menük
- **`GuiUtil`**: közös item-/lore-építők (`icon`, `filler`, `fill`, `label`, `accent`, `grey`).
  Új menü-ikonnál ezeket használd, ne építs inline `ItemMeta`-t.
- **`CommandMenu` rendszer** (adat-vezérelt): a legtöbb menü a `CommandMenus` definíciókból + a
  `CommandMenuHolder`/`CommandMenuListener` párosból épül. Új „gombmenühöz" ezt preferáld a
  bespoke GUI helyett.

### 3.7 Player-state takarítás — registry-iterált
A `PlayerSessionCleanupListener` kilépéskor/kickkor: (a) végigmegy a regisztrált
`List<PlayerStateCleanup>`-on (managerek), és (b) a `SpellRegistry.getAll()`-on, minden spell
`clearPlayerState(uuid)`-jét hívva. **Nincs hardkódolt lista** — új állapotos egység automatikusan
bekerül (lásd 5.7 recept).

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

### 4.1 Audit-állapot (baseline — ŐRIZD MEG)
A teljes kódbázist átnéztük Folia-kompatibilitásra; **nulla sértés**. A bevált minták, amelyeket
új kódnál is tartani kell:
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

---

## 7. Build és ismert korlátok

- **Stack:** Java 21, Gradle, Paper/Folia API `1.21.11`. Belépő/bootstrap/loader a `paper-plugin.yml`-ben.
- **Méret:** ~227 Java-fájl, ~25 800 sor.
- **Hátralévő refaktor** (build-checkpointot igénylő, szándékosan halasztott tételek): a maradék
  inline parancsok migrálásához a dispatch-bázis additív bővítése (default-subcommand + láthatósági
  predikátum); az `IceSMPCore` manager-építés factory-szétbontása (a `final` mezők miatt); a mentések
  debounce-olása (async IO + flush-on-disable).
- **Nyitott fejlesztések:** `ROADMAP.md`.
