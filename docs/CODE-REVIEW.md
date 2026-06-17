# IceSMP — Code Review találatok

> **Állapot:** nyitott találatok gyűjteménye • **Utolsó frissítés:** 2026-06-17
> **Ág:** `claude/gracious-rubin-0hxvbd`
>
> **Javítási napló:**
> - **1. csomag — gazdaság-stop ✅** (`ECON-1`, `ECON-2`, `ECON-3`, `ECON-4`/`CORE-1`/`CORE-2`,
>   `ECON-6`, `CMD-1`, `CMD-2`, `CMD-3`, `CMD-8`): atomikus balansz (`compute`/`tryDeduct`),
>   piaci pénz-konzerválás + atomikus claim, debounce-olt atomikus (temp+rename) mentés,
>   `Double.isFinite` guardok. Fordítás/teszt helyben szükséges.

Ez a dokumentum az IceSMP teljes kódbázisának (214 Java fájl, ~24 700 sor) átfogó
review-ját **és** a session-ben hozzáadott funkciók (`06dcb17…HEAD`, 69 fájl, ~5 400 sor)
diff-review-ját tartalmazza, egységes követhető formában.

## ⚠️ Módszertani megjegyzés

A review **statikus elemzés** (forráskód-olvasás + szimbólum-trace), **nem** fordítás/futtatás:
a környezetben a PaperMC/Folia repo blokkolt (403), így `./gradlew build`-et **helyben kell**
lefuttatni. A wiring-trace **nem talált** konstruktor-/overload-/regisztrációs eltérést, tehát
fordításnak tisztának kell lennie. A Folia-szálkezelési súlyosságok „kód alapján" minősítettek
(jellemző viselkedés: *régióhatároknál dob, melee közelben általában működik*).

## Súlyossági jelölés

- 🔴 **CRITICAL** — gazdaságot tör / adatvesztés / bárki kihasználhatja
- 🟠 **HIGH** — valódi funkcionális hiba vagy reális crash/exploit
- 🟡 **MEDIUM** — helytelen viselkedés, leak, ritka crash, balansz-exploit
- 🟢 **LOW** — robusztusság, konzisztencia, takarítás (nincs viselkedésváltozás)

## Tartalom

- [Átfogó minták (A–F)](#átfogó-minták)
- [CRITICAL találatok](#critical)
- [HIGH találatok](#high)
- [MEDIUM találatok](#medium)
- [LOW találatok](#low)
- [Diff-review: pet/session funkciók (PET-*)](#diff-review--petsession-funkciók)
- [Javasolt javítási sorrend](#javasolt-javítási-sorrend)

---

## Átfogó minták

Ezek a visszatérő hibaosztályok; rendszerszintű javításuk egyszerre sok egyedi találatot megszüntet.

- **A. Hiányzó `Double.isFinite` ellenőrzés a számbevitelnél.** `Double.parseDouble("NaN"|"Infinity")`
  nem dob, és `amount <= 0` **hamis** NaN-ra → minden pozitivitás-ellenőrzés megkerülhető.
  Érintett: `CMD-1`, `CMD-2`, `CMD-3`, `CMD-8`.
- **B. `save()` minden mutációnál, szinkronban, zár nélkül, nem atomikusan.** A teljes YAML
  kiírása minden változásnál a hívó szálon; Folián a global tax-task és a régió-szálú parancsok
  ugyanazt a fájlt írják. Érintett: `ECON-4`, `RELIC-1`, `CORE-2`, `CORE-5`.
  → dirty-flag + periodikus async flush + temp-fájl + atomikus rename + zár.
- **C. Cross-region entity-hozzáférés.** Nem a futó szál „saját" entitásának mutálása (event másik
  szereplője, célpont, spawn-horgony) Folián hibát dob. Érintett: `DATA-1`, `CMBT-1`, `CMBT-2`,
  `CMBT-5`, `SPELL-1`, `GUI-1`, `GUI-2`, `CMD-4`, `ECON-7`.
  → hopp az adott entitás `getScheduler()`-ére / a location `getRegionScheduler()`-ére.
- **D. Spawnolt/birtokolt entitások nincsenek eltakarítva `disable()`-kor.** Érintett: `CMBT-3`,
  `CMBT-4`, `CORE-4`. → minden spawner-managernek `shutdown()`, amit a `disable()` hív.
- **E. UUID-kulcsú map-ek nincsenek takarítva quitkor.** Lassú memóriaszivárgás. Érintett:
  `DATA-3`, `DATA-4`, `RELIC-3`, `CORE-5`. → minden a `PlayerSessionCleanupListener`-en át.
- **F. Költség/cooldown a sikeresség ismerete *előtt*.** `consumeCost`+`putCooldown` az `execute()`
  no-op-ja ellenére is lefut. Érintett: `SPELL-3`. → `execute()` adjon vissza `boolean`-t.

---

## CRITICAL

### `ECON-1` — A valuta-tár konkurens, de nem atomikus
- [x] ✅ **Javítva (1. csomag).** **Fájl:** `managers/CurrencyManager.java:33`, `adjustBalance` (~399)
- **Súlyosság:** 🔴 CRITICAL
- **Leírás:** `balances = ConcurrentHashMap<UUID, EnumMap<CurrencyType,Double>>`. A map szálbiztos,
  de az `EnumMap` érték **nem**, és a balansz-módosítás read-modify-write (`getOrDefault` →
  `put(current+delta)`) atomicitás nélkül. Folián „A fizet B-nek" (A régió-szálán) ütközhet
  „B keres pénzt" (B szálán) — mindkettő B `EnumMap`-jét írja.
- **Hatás:** elveszett frissítés → pénz teremtődik/eltűnik; egyidejű `put` az `EnumMap`-en belső
  állapotot is ronthat.
- **Fix:** minden balansz-mutáció `balances.compute(uuid, …)`-on át, a belső módosítás `merge`/`compute`
  atomikus művelettel; vagy per-UUID zár.

### `ECON-2` — A piac pénzt teremt/éget (reputációs ár vs. listaár)
- [x] ✅ **Javítva (1. csomag).** **Fájl:** `managers/MarketManager.java:200-208`, `buy()`
- **Súlyosság:** 🔴 CRITICAL
- **Leírás:** a vevő a reputáció-módosított árat fizeti (`getEffectivePrice`), de az eladó a
  **listaárból** kap (`listing.price()*(1-fee)`). A két oldal nincs ugyanabból az összegből származtatva.
- **Hatás:** szövetséges vevő (kedvezmény) → pénz teremtődik; ellenséges (felár) → pénz eltűnik.
  Alt-tal végteleníthető: alt listáz 100-ért, te 80-ért veszed, alt kap 90-et → **+10/ciklus**.
- **Fix:** mindkét oldalt a ténylegesen fizetett összegből (`buyerCost`) számold:
  `sellerShare = buyerCost*(1-fee/100)`, `burned = buyerCost-sellerShare`.

### `CMBT-1` — Világboss spawn cross-region location-olvasás
- [ ] **Fájl:** `managers/WorldBossManager.java:128`, `triggerSpawnNear()`
- **Súlyosság:** 🔴 CRITICAL (Folia)
- **Leírás:** `tick()` a global schedulerről választ random játékost, majd `anchor.getLocation()`-t
  olvas **a régió-hopp előtt**. `forceSpawn(null)` ugyanígy a parancs-szálról.
- **Hatás:** Folia `IllegalStateException` (idegen régió), a boss nem spawnol, konzol-spam.
- **Fix:** előbb az anchor saját szálán olvasd a helyét: `anchor.getScheduler().run(… loc …)`,
  majd `getRegionScheduler().run(loc, …)` a spawnra.

### `CMBT-2` — Invázió spawn cross-region location-olvasás
- [ ] **Fájl:** `managers/InvasionManager.java:86`, `triggerNear()`
- **Súlyosság:** 🔴 CRITICAL (Folia)
- **Leírás:** azonos minta, mint `CMBT-1`: `anchor.getLocation().clone()` a global tickről, hopp előtt.
- **Hatás:** az invázió nem indul, konzol hibák.
- **Fix:** anchor szálán olvasd a helyet, utána régió-scheduler a hullám-spawnra.

> **Megjegyzés (`CORE-1`):** a `CurrencyManager.save()` szinkronizálatlan/nem-atomikus volta önállóan is
> kritikus persistence-kockázat (torz fájl a global tax-task és a parancs-szál egyidejű írásakor).
> Lásd egyesítve: `ECON-4`.

---

## HIGH

### `ECON-3` — A piaci vétel nem atomikus a lemondással szemben
- [x] ✅ **Javítva (1. csomag).** **Fájl:** `managers/MarketManager.java:189-216`, `buy()` / `cancelListings()`
- **Súlyosság:** 🟠 HIGH
- **Leírás:** `buy()` `synchronized`, de `cancelListings()`/`createListing()` **nem**, és ugyanazt a
  `listings` map-et módosítják. A `buy()` előbb von, utána távolít.
- **Hatás:** vevő-vétel és eladó-lemondás versenye → item-duplikáció vagy levont, de teljesítetlen vétel.
- **Fix:** minden listing-mutáció azonos zár alatt; `buy()`-ban előbb `listings.remove()` mint atomikus
  „claim", csak siker esetén vonj és szállíts; hiba esetén visszatérítés.

### `ECON-4` / `CORE-1` / `CORE-2` — `save()` szinkronizálatlan, nem atomikus, minden mutációnál
- [x] ✅ **Javítva (1. csomag)** — CurrencyManager/MarketManager/FactionTreasuryManager (a többi manager még hátravan, lásd 4. csomag). **Fájl:** `managers/CurrencyManager.java:78` (és minta B szerint RelicManager, MarketManager, KingManager, ParkourManager)
- **Súlyosság:** 🟠 HIGH (a konkurens írás kritikus felé hajlik)
- **Leírás:** minden deposit/withdraw/transfer/market-buy a teljes YAML-t kiírja a hívó szálon, zár és
  temp-fájl nélkül. A global tax-task és a régió-szálú parancsok egyidejűleg írhatják a fájlt.
- **Hatás:** fél-kiírt / sérült fájl, IO-vihar (TPS-esés), tág versenyhelyzet-ablak.
- **Fix:** dirty-flag + egyetlen periodikus async flush (+ `disable()`-kor egyszer); temp-fájl +
  `Files.move(…, ATOMIC_MOVE, REPLACE_EXISTING)`; zárral védett snapshot.

### `ECON-5` — Talentpont-könyvelés duplán számol lejárt talenteket
- [ ] **Fájl:** `managers/TalentManager.java:269` (`getSpentPoints`), `198` (`treeGatesMet`), `241` (`refundUnavailableTalents`)
- **Súlyosság:** 🟠 HIGH
- **Leírás:** `getSpentPoints` minden rangot összead (a követelményt vesztett talenteket is), de azok
  refundolhatók és nem fejtenek ki hatást; a capstone-kapu („N pont elköltve") beleszámítja a „halott"
  rangokat. A refund-pass futásától függően a szám ingadozik → nemdeterminisztikus capstone.
- **Hatás:** respec-exploit: a követelmény ki-be kapcsolásával capstone érhető el „nem létező" pontokkal.
- **Fix:** a spent-pont és a kapu csak az aktuálisan `meetsRequirements` talentekből számoljon; a
  `refundUnavailableTalents` determinisztikusan fusson minden kapu-/pontszámítás előtt.

### `SPELL-1` — Mérges csirke cross-region sebzés
- [ ] **Fájl:** `spells/AngryChickenSpell.java:55`
- **Súlyosság:** 🟠 HIGH (Folia)
- **Leírás:** a projektil-csirke `chicken.getScheduler().runAtFixedRate`-ben fut, és `living.damage(8, shooter)`-t
  hív + a shootert kéri le — a csirke ~8 blokkot repül, átléphet régióhatárt, így a célpont/shooter más szálé.
- **Hatás:** entitás-mutáció idegen régió-szálról → Folia hiba / sérült állapot a határoknál.
- **Fix:** a sebzést a célpont saját schedulerén: `living.getScheduler().run(… living.damage(8, shooter))`.

### `SPELL-2` — `RootSpell` az égbe katapultál (`JUMP_BOOST` 250)
- [ ] **Fájl:** `spells/RootSpell.java:27`
- **Súlyosság:** 🟠 HIGH
- **Leírás:** a „gyökerezés" minden közeli lényre `JUMP_BOOST` **amplifier 250**-et rak (SLOWNESS 10 mellett).
  A 250-es szint az égbe lövi a lényt.
- **Hatás:** halálos esési sebzés, grief/exploit (saját magadra vagy másra). A szándék immobilizálás volt.
- **Fix:** `JUMP_BOOST` amplifier **128** (ez vanilla-ban tiltja az ugrást), vagy csak SLOWNESS.

### `RELIC-1` — `RelicManager.save()` az event-szálon, konkurensen
- [ ] **Fájl:** `managers/RelicManager.java:269` (és 345, 470 hívási pontok)
- **Súlyosság:** 🟠 HIGH (minta B)
- **Leírás:** `recordOwnership()→save()` fut join-, halál- (PvP-transfer) és quit-handlerből; Folián
  különböző régió-szálak egyszerre írják a `relics.yml`-t, zár/temp-fájl nélkül.
- **Hatás:** törött ownership-fájl (relikvia-tulajdon elvesztése), blokkoló írás a tick-en.
- **Fix:** minta B (async, atomikus, zárolt írás); sose `yaml.save()` közvetlenül event-handlerből.

### `RELIC-2` — Join-kori újra-claim szingleton-ellenőrzés nélkül → relikvia-dupe
- [ ] **Fájl:** `managers/RelicManager.java:333`, `handlePlayerJoin`
- **Súlyosság:** 🟠 HIGH
- **Leírás:** belépéskor minden hordott relikviára felírja a tulajdont, ha az item-tulaj null vagy maga a
  belépő — szingleton-kényszer nélkül. A `giveRelic()` csak adáskor kényszerít szingletont, birtokláskor soha.
- **Hatás:** ha valahogy két példány létrejön (drop/pickup, korábbi másolat), mindkettő „érvényessé" válik,
  mindkettő használható.
- **Fix:** belépéskor detektáld a szingleton-relikvia több példányát és töröld a feleslegeseket; vagy
  `relic_created_at` authoritatív érték-ellenőrzés a klónozott PDC ellen.

### `CMBT-3` — Világboss entitás-leak `disable()`-kor + kettős skálázás
- [ ] **Fájl:** `managers/WorldBossManager.java:157`
- **Súlyosság:** 🟠 HIGH (minta D)
- **Leírás:** a boss `setPersistent(true)`+`setRemoveWhenFarAway(false)`, de `disable()` csak a taskot
  állítja le, a bosst nem despawnolja. Mivel RAVAGER `Monster`, a `MobScalingManager` is ráfut → a boss-attribútumokra
  távolság-skálázás rakódik és felülírja a `[Világboss]` nevet.
- **Hatás:** reload/restart után árva, perzisztens boss; eltorzult statok/név.
- **Fix:** `shutdown()` ami despawnolja az aktív bosst (`disable()` hívja); a boss PDC-jét jelöld a spawn
  *előtt*, és a `MobScalingManager` hagyja ki a `world_boss`-jelölt / `SpawnReason.CUSTOM` lényeket.

### `CMBT-4` — Petek árván maradnak `disable()`-kor (lásd még `PET-2`)
- [ ] **Fájl:** `managers/PetManager.java:145` (`summon`/`adopt`)
- **Súlyosság:** 🟠 HIGH (minta D)
- **Leírás:** a pet perzisztens + `removeWhenFarAway(false)`, de nincs `PetManager.shutdown()`; `disable()`
  csak a `petTask`-ot állítja le. Ha summonkor a chunk nincs betöltve, `Bukkit.getEntity` null → **új** pet
  spawnol, a régi árva marad (a tick-loop sosem vezérli, de a MinionManager-regiszterbe számít).
- **Fix:** `shutdown()` ami online játékosonként a pet saját szálán despawnol; summonkor stale állapot
  törlése duplikálás helyett.

### `CMBT-5` / `PET-6` — Pet-tick cross-region `owner.getLocation()` olvasás
- [ ] **Fájl:** `managers/PetManager.java:292` (`runPetTick`, `resolveTarget`, `acquireNearbyThreat`)
- **Súlyosság:** 🟠 HIGH (Folia)
- **Leírás:** a tick a **pet** régió-szálán fut, de `owner.getLocation()/getWorld()`-öt olvas és a pet
  felé teleportál; a gazda gyakran más régióban van (épp ez váltja ki a követést).
- **Hatás:** off-region olvasás → Folia hiba, a tick megszakad, a pet nem követ.
- **Fix:** a gazda helyét a gazda szálán snapshotold (vagy thread-safe last-known-location cache), és azt add
  át a pet régiójának. (A `teleportAsync` maga jó; a forrás-`getLocation()` a probléma.)

### `GUI-1` — Menü gomb inline parancsot futtat a klikk-szálon (off-region)
- [ ] **Fájl:** `listeners/CommandMenuListener.java:81`
- **Súlyosság:** 🟠 HIGH (Folia)
- **Leírás:** az `OPEN:`/`RUN:` akciók `player.performCommand(...)`-ot hívnak a `InventoryClickEvent`
  (klikkelő játékos régió-) szálán; olyan parancsok futnak így, mint `faction raid`, `treasury withdraw`,
  `exchangeboard place`, amelyek frakció-szintű/másik-játékos/blokk-állapotot piszkálnak.
- **Hatás:** ha a célparancs nem hoppol a megfelelő régióra, Folia-hiba / sérült állapot.
- **Fix:** ne futtass gameplay-parancsot inline; a parancs saját schedulerén / megfelelő
  régió-/global-scheduleren keresztül.

### `GUI-2` — Piaci eladó-értesítés cross-region (lásd `ECON-2/3`)
- [ ] **Fájl:** `listeners/MarketGUIListener.java:83-90`
- **Súlyosság:** 🟠 HIGH (Folia)
- **Leírás:** sikeres vétel után `Bukkit.getPlayer(seller).sendMessage(...)` a **vevő** régió-szálán; az
  eladó más régióban lehet. (A balansz UUID-kulcsú adat, az túléli; a `Player`-objektum hozzáférés nem.)
- **Fix:** az eladó-értesítést az eladó schedulerén / globálison futtasd.

### `CMD-1` — `/faction donate NaN` megmérgezi a frakció-kasszát
- [x] ✅ **Javítva (1. csomag).** **Fájl:** `commands/faction/FactionDonateSubcommand.java:62`
- **Súlyosság:** 🟠 HIGH (minta A)
- **Leírás:** `Double.parseDouble("NaN")` sikerül; `amount <= 0` hamis NaN-ra → a donate folytatódik,
  `deposit(faction, NaN)`. Ez **unprivilegizált** parancs.
- **Hatás:** a kassza NaN lesz, onnantól minden `getBalance()`/összehasonlítás eltörik (`Infinity` is injektálható).
- **Fix:** `if (!Double.isFinite(amount) || amount <= 0) { elutasít }`.

### `CMD-2` — `/faction treasury withdraw Infinity` pénzt teremthet
- [x] ✅ **Javítva (1. csomag)** — parancs-oldali `isFinite` + atomikus treasury withdraw. **Fájl:** `commands/faction/FactionTreasurySubcommand.java:98`
- **Súlyosság:** 🟠 HIGH (minta A)
- **Leírás:** azonos NaN/Infinity-bypass a király/admin withdraw-ban; ha a `withdraw` nem véd, a játékos
  bankja végtelen/NaN lesz.
- **Fix:** `Double.isFinite` guard + védekező clamp a `FactionTreasuryManager.withdraw/addToBalance`-ban.

### `DATA-1` — `SinListener` a gyilkost mutálja off-region
- [ ] **Fájl:** `listeners/SinListener.java:69`
- **Súlyosság:** 🟠 HIGH (Folia)
- **Leírás:** `PlayerDeathEvent` az **áldozat** szálán fut; a handler a `getKiller()`-t (más játékos, más
  régió) piszkálja: `addSin` (PDC-írás), `exileToDark` (hang/partikli a gyilkos helyén), `recordRaidKill`,
  `sendMessage`. Távoli/projektil-ölésnél a két játékos külön régióban van.
- **Hatás:** `IllegalStateException` a halál-handlerben (fél-alkalmazott drop/pontozás).
- **Fix:** csak az áldozat/régió-lokális olvasás az event-szálon; a gyilkos-oldali munkát
  `killer.getScheduler().run(...)`-ban.

### `DATA-2` — NEUTRAL örök láthatatlanság reloggig
- [ ] **Fájl:** `listeners/FactionPassiveListener.java:110`
- **Súlyosság:** 🟠 HIGH
- **Leírás:** sneakre `INVISIBILITY` `INFINITE_DURATION`, un-sneakre feltétel nélküli `removePotionEffect`.
  (1) Sneak közbeni kilépés → relog után tartósan láthatatlan (nem sneakel, az un-sneak handler nem fut).
  (2) A vak add/remove elnyeli/elrontja a spell-láthatatlanságot (HideSpell stb.) és viszont.
- **Fix:** saját marker-flag (csak akkor távolítsd, ha ez a listener tette fel); join/quit rekonciliáció.

---

## MEDIUM

### `SPELL-3` — Bukott/no-op kasztra is levonódik a költség és a cooldown
- [ ] **Fájl:** `listeners/AbilityCatalystListener.java:201-206`
- **Súlyosság:** 🟡 MEDIUM (minta F)
- **Leírás:** `consumeCost(player)` → `execute(player)` → feltétlen `putCooldown`. Sok `execute` no-op-ol
  (nincs célpont/companion: `ConfiguredSpell`, `LifeDrainSpell`, `PrimalBondSpell`, `BeeSwarmSpell`).
- **Hatás:** elvétett kaszt elégeti az XP-t/életet/hungert és teljes cooldownt indít.
- **Fix:** `Spell.execute` adjon vissza `boolean`-t; csak siker esetén `consumeCost`+`putCooldown`.

### `SPELL-4` — A kombó-cooldown-refund elérheti a 100%-ot (teljes bypass)
- [ ] **Fájl:** `listeners/AbilityCatalystListener.java:247-249`, `206`
- **Súlyosság:** 🟡 MEDIUM
- **Leírás:** kombónál `putCooldown(now - comboRefundMillis)`; a refund-százalék 0..100-ra van vágva, 100%-nál
  a maradék cooldown 0. A mastery-csökkentés erre rakódik.
- **Fix:** effektív refund ≤80%-ra vágni és/vagy minimum-cooldown padló a `getRemainingCooldown`-ban.

### `SPELL-5` — `HideSpell` páncél nem áll vissza kilépéskor (null retired-callback)
- [ ] **Fájl:** `spells/HideSpell.java:48`
- **Súlyosság:** 🟡 MEDIUM
- **Leírás:** a páncélt `HIDDEN_ARMOR`-ba menti és a slotokat üríti, majd `runDelayed(..., null, …)` — a
  retired-callback **null**. Folián kilépéskor a függő task retire-ölődik a callback futása nélkül, így a
  `clearHide` sosem fut (a testvérspellek átadnak retired-callbacket).
- **Fix:** `runDelayed(plugin, t -> clearHide(id), () -> clearHide(id), TICKS)`; a `clearHide` idempotens.

### `RELIC-3` — `MetelytepoManager` freeze/bypass map-ek mob-UUID-kra szivárognak
- [ ] **Fájl:** `managers/MetelytepoManager.java:334` (és `listeners/MetelytepoRelicListener.java:334`)
- **Súlyosság:** 🟡 MEDIUM (minta E)
- **Leírás:** `frozenSpeed`/`abilityDamageBypass` tetszőleges `LivingEntity` (mob) UUID-kra, de a `cleanup`
  csak játékos-UUID-t töröl. Ha a mob meghal/kicsekkol az unfreeze-delay előtt, az `isValid()` guard kilép a
  map-entry törlése nélkül → korlátlan növekedés.
- **Fix:** az unfreeze-callbackben `!isValid()` esetén is töröld az entryt; `EntityDeathEvent`-re takaríts.

### `RELIC-4` — Nem-atomikus cooldown + dupla interact-útvonal
- [ ] **Fájl:** `listeners/MetelytepoRelicListener.java:148-180`
- **Súlyosság:** 🟡 MEDIUM
- **Leírás:** `isOnCooldown()` → később `triggerCooldown()` (külön olvasás/írás, nincs CAS); a sneak+jobb-katt
  entitáson az `onInteractEntity` is meghívja a `handleHonorEye`-t.
- **Hatás:** gyors kattintással két ability-futás csúszik be egy cooldown-ablakba.
- **Fix:** atomikus cooldown-foglalás (`putIfAbsent`/`computeIfAbsent`) az effekt **előtt**; az interact-utak deduplikálása.

### `RELIC-5` — Lejárt-szingleton edge: két aktív példány
- [ ] **Fájl:** `managers/RelicManager.java:396`, `giveRelic`
- **Súlyosság:** 🟡 MEDIUM
- **Leírás:** ha az aktuális tulajdon lejárt, B megkapja az új relikviát, de A még létező itemje (a sweep csak
  A belépésekor fut) továbbra is működik (a `canUse()` az item saját tagjét nézi, nem a központi rekordot).
- **Fix:** a `canUse()`/trigger a központi `ownerships` rekordot is konzultálja; lejárt fizikai példányok érvénytelenítése.

### `ECON-6` — Tört valuta a `exchange` kerekítésében
- [x] ✅ **Javítva (1. csomag).** **Fájl:** `managers/CurrencyManager.java:373-381`
- **Súlyosság:** 🟡 MEDIUM
- **Leírás:** `exchange()` a nyers `double` `netTargetAmount`-ot írja jóvá, de `Math.round`-ot ad vissza a
  UI-nak; a tárolt balansz tört, a `withdraw` (int) az egész részt húzza, a tört megmarad.
- **Fix:** egységes egész (long) reprezentáció vagy konzisztens `floor`/`round` minden jóváíráskor; konzerváció-ellenőrzés.

### `ECON-7` — `KingManager.recount` broadcast/`getOfflinePlayer` rossz szálon
- [ ] **Fájl:** `managers/KingManager.java:233`
- **Súlyosság:** 🟡 MEDIUM (Folia)
- **Leírás:** `vote()` a szavazó régió-szálán hívja `recount()`-ot, ami `Bukkit.getOfflinePlayer(leader).getName()`
  (blokkoló név-lookup) és `Bukkit.getServer().broadcast()` (minden régió) műveletet végez.
- **Fix:** a broadcast/név-feloldás a global region scheduleren; nevet előre, aszinkron.

### `CMBT-6` — `activeBossUntil` a spawn megerősítése előtt áll be
- [ ] **Fájl:** `managers/WorldBossManager.java:132`
- **Súlyosság:** 🟡 MEDIUM
- **Leírás:** `triggerSpawnNear` előbb állítja `activeBossUntil`-t és `true`-t ad vissza, majd a `spawnBoss`
  nem-mob típusnál 0-ra állítja. Az admin „spawnolt"-at lát, miközben nincs boss.
- **Fix:** előzetes mob-típus-validáció; `activeBossUntil` csak sikeres spawn után, a `spawnBoss`-on belül.

### `CMBT-7` — `RaidManager` recordKill/endRaid pontozási verseny
- [ ] **Fájl:** `managers/RaidManager.java:134`
- **Súlyosság:** 🟡 MEDIUM
- **Leírás:** `endRaid` (global) nullázza az `activeRaid`-et és snapshotol, miközben `recordKill` (más régió)
  épp beolvasta a nem-null `activeRaid`-et → a kill elveszhet vagy a vég után számolódik.
- **Fix:** `recordKill` és `endRaid` közös zár alatt; `endRaid` előbb állítson `closed` flaget (CAS), a kései
  killeket determinisztikusan utasítsa el.

### `CMD-3` — `/market sell NaN` ár
- [x] ✅ **Javítva (1. csomag).** **Fájl:** `commands/MarketCommand.java:69`
- **Súlyosság:** 🟡 MEDIUM (minta A)
- **Leírás:** a parancs nem ellenőriz finitséget/pozitivitást, a NaN/Infinity árat a managerre bízza.
- **Fix:** `if (!Double.isFinite(price) || price <= 0) return;` a parancsban.

### `CMD-4` — `/job givecatalyst <más>` cél-inventory off-region
- [ ] **Fájl:** `commands/job/JobGiveCatalystSubcommand.java:73`
- **Súlyosság:** 🟡 MEDIUM (Folia, minta C)
- **Leírás:** a parancs-szálon `target.getInventory().addItem` és `target.getWorld().dropItemNaturally(...)`,
  miközben a cél más régióban/világban lehet.
- **Fix:** `target.getScheduler().run(...)`-ban a cél-mutáció; minden `getPlayerExact`-cél hasonlóan.

### `CMD-5` — `/faction set <typo>` bogus UUID-ra ír + blokkol
- [ ] **Fájl:** `commands/faction/FactionSetSubcommand.java:54`
- **Súlyosság:** 🟡 MEDIUM
- **Leírás:** `Bukkit.getOfflinePlayer(String)` sosem null (és blokkoló Mojang-lookup lehet a fő szálon);
  elgépelt név → nem létező UUID-ra perzisztál frakció-adatot.
- **Fix:** előbb `getPlayerExact`; offline-nál csak ismert/cache-elt + `hasPlayedBefore()`/`getName()!=null`.

### `CORE-3` — `disable()`: cleanup a `save()` előtt + load/save aszimmetria
- [ ] **Fájl:** `core/IceSMPCore.java:345` (cleanup ~368-371, save ~374-385)
- **Súlyosság:** 🟡 MEDIUM
- **Leírás:** a player-state cleanup a `save()`-ek **előtt** fut; ha bármelyik cleanup-út in-memory state-et
  töröl, a save a takarított állapotot menti (adatvesztés). `mobScalingManager`/`craftingRestrictionManager`
  `load()`-ol, de nem `save()`-el (aszimmetria).
- **Fix:** előbb minden manager `save()`, utána cleanup; a `cleanupPlayerState` ne dobjon el mentetlen adatot.

### `CORE-5` — Korlátlanul növő, sosem prunált UUID-kulcsú tárak
- [ ] **Fájl:** `managers/StatsManager.java:89` (és CurrencyManager, RelicManager, market listings)
- **Súlyosság:** 🟡 MEDIUM (minta B+E)
- **Leírás:** minden valaha belépett játékos örök bejegyzést kap; nincs inaktivitás-alapú pruning. A teljes-fájl
  újraírással (minta B) a mentés egyre lassul.
- **Fix:** periodikus pruning inaktivitás-küszöb felett (RelicManager már tárol last-seen-t); vagy per-player fájl/DB.

### `DATA-3` — Listener-map-ek nincsenek takarítva quitkor
- [ ] **Fájl:** `listeners/ProfessionRecipeListener.java` (`hintThrottle`), `listeners/SiegeWeaponListener.java` (`debounce`)
- **Súlyosság:** 🟡 MEDIUM (minta E)
- **Leírás:** nincs `PlayerQuitEvent`-takarítás és a `cleanupPlayerState` sem hívja őket → minden interakciózó
  játékos örök entryt hagy.
- **Fix:** quit-handler / `PlayerSessionCleanupListener` bevonás.

### `DATA-4` — `TerritoryListener` nem takarít kick-re
- [ ] **Fájl:** `listeners/TerritoryListener.java:105`
- **Súlyosság:** 🟡 MEDIUM (minta E)
- **Leírás:** `lastTerritoryIds` csak `PlayerQuitEvent`-re ürül; kickre stale entry marad.
- **Fix:** explicit kick-handler vagy `PlayerSessionCleanupListener` bevonás.

### `PET-3` — Pet teljes gyógyulás minden szintlépéskor
- [ ] **Fájl:** `managers/PetManager.java`, `applyBuffs` (`pet.setHealth(maxHealth.getValue())`)
- **Súlyosság:** 🟡 MEDIUM
- **Leírás:** az `applyBuffs` szintlépéskor (és summonkor) is fut; az 1 HP-n harcoló pet azonnal teljes életre
  gyógyul, amint a gazda ölésével szintet lép → gyakorlatilag megölhetetlen grindelésnél.
- **Fix:** szintlépéskor csak a max-HP-t növeld; teljes gyógyítás csak summonkor.

### `PET-4` — Napi küldetés UTC-éjfélkor, feladat közben reset
- [ ] **Fájl:** `managers/DailyQuestManager.java`, `today()`/`ensureFresh`
- **Súlyosság:** 🟡 MEDIUM
- **Leírás:** `today() = currentTimeMillis()/86_400_000L` UTC-nap szerint vödröz; nem-UTC játékosnál az UTC-éjfél
  a nap közepén tör → a haladás némán nullázódik és az aktív cél lecserélődik.
- **Fix:** szerver-helyi dátum vagy konfigurálható reset-óra.

### `PET-5` — `setCombatTarget` entity-lookup minden damage-eventnél
- [ ] **Fájl:** `managers/PetManager.java`, `setCombatTarget`
- **Súlyosság:** 🟡 MEDIUM (perf)
- **Leírás:** `PetCombatListener`-ből minden `EntityDamageByEntityEvent`-re fut, és azonnal PDC-olvasást +
  `Bukkit.getEntity(UUID)`-t végez, pedig csak Vadmester/Nekromanta tarthat petet.
- **Fix:** korai `if (!canOwnPet(owner)) return;` (olcsó enum-összevetés) a lookup előtt.

### `PET-7` — A HUD mindent újraépít és újraküld másodpercenként
- [ ] **Fájl:** `managers/HudManager.java`, `update`
- **Súlyosság:** 🟡 MEDIUM (perf)
- **Leírás:** minden frissítésnél (alap 20t) mind a 6 sidebar-sor és minden team-prefix + `playerListName`
  újraíródik, akkor is, ha semmi nem változott → ~600 scoreboard + 100 tab packet/mp 100 játékosnál.
- **Fix:** per-játékos utolsó-render cache, csak a változott bejegyzések küldése.

---

## LOW

### `SPELL-6` — `InnerFocusSpell` „mérgezett" walk-speed újrakasztnál
- [ ] **Fájl:** `spells/InnerFocusSpell.java:33-39`
- **Súlyosság:** 🟢 LOW
- **Leírás:** újrabelépésnél a már fagyott 0.0 walk-speed kerülhet a `FROZEN_PLAYERS`-be, így a restore 0.0-t
  állít vissza; a `consumeCost` a deklarált költséget ignorálva 0-ra állítja a foodot.
- **Fix:** re-entry guard, csak `>0` walk-speed mentése; `consumeCost` legyen konzisztens a deklarált költséggel.

### `SPELL-7` — `ConfiguredSpell` freeze-matematika eltér a builder-kontraktustól
- [ ] **Fájl:** `spells/ConfiguredSpell.java:149-150`
- **Súlyosság:** 🟢 LOW
- **Leírás:** `setFreezeTicks(max(current, FREEZE_BASE_TICKS + freezeTicks))` — mindig +140 tick, és a `max()`
  miatt nem halmozódik; a tervező a konfighoz képest +7s-et lát.
- **Fix:** tisztázni a `FREEZE_BASE_TICKS` szándékát; vagy `max(current, freezeTicks)`.

### `RELIC-6` — `createProxyPickaxeForMining` unsafe enchant-másolás
- [ ] **Fájl:** `listeners/MetelytepoRelicListener.java:359`
- **Súlyosság:** 🟢 LOW
- **Leírás:** `addUnsafeEnchantments(getEnchantments())` minden (akár átok/unsafe) enchantot átmásol; defenzív
  null/air guard hiányzik.
- **Fix:** csak bányászat-releváns enchantok (Efficiency/Fortune/Silk Touch/Unbreaking); source guard.

### `RELIC-7` — PvP-transfer drop-onként „tisztára mossa" a duplikátumokat
- [ ] **Fájl:** `listeners/RelicPvpTransferListener.java:45`
- **Súlyosság:** 🟢 LOW
- **Leírás:** több azonos relikvia-drop esetén mindegyik átírja a központi rekordot és a gyilkoshoz köti; plusz
  drop-onként `save()`.
- **Fix:** szingleton-relikviából legfeljebb egy átvitele, a többi eldobása; egyszeri rekord-írás; identitás-ellenőrzés.

### `GUI-3` — A piac GUI a listaárat mutatja, de az effektív árat vonja
- [ ] **Fájl:** `gui/MarketGUI.java:131`
- **Súlyosság:** 🟢 LOW (de UX/bizalom)
- **Leírás:** a lore `listing.price()`-t ír, a vétel `getEffectivePrice()`-t von → a vevő mást fizet, mint amit lát.
- **Fix:** a néző-specifikus effektív árat rendereld (vagy mutasd mindkettőt).

### `GUI-4` — `ProfileHolder` nincs owner-ellenőrzés
- [ ] **Fájl:** `listeners/CharacterGUIListener.java:63`
- **Súlyosság:** 🟢 LOW
- **Leírás:** a profil-hub holdere nem hordoz owner-UUID-t és nem ellenőriz, szemben a Spec/Profession/Talent ágakkal.
- **Fix:** owner-UUID a `ProfileHolder`-be + ellenőrzés (defense-in-depth, konzisztencia).

### `GUI-5` — `SkillTreeGUIListener` hiányzó raw-slot bound-check
- [ ] **Fájl:** `listeners/SkillTreeGUIListener.java:43`
- **Súlyosság:** 🟢 LOW
- **Leírás:** nincs felső-inventory bound-check a testvér-listenerekhez képest (ma biztonságos, de törékeny).
- **Fix:** ugyanaz a `getRawSlot()` tartomány-ellenőrzés.

### `GUI-6` — `MarketGUI.matchesFilter` lehetséges NPE null meta-nál
- [ ] **Fájl:** `gui/MarketGUI.java:99`
- **Súlyosság:** 🟢 LOW
- **Leírás:** `getItemMeta()` dereferálva `displayName()`-hez `hasItemMeta()` után; sérült/edge listing-item a render-ciklusban dobhat.
- **Fix:** egyszeri `meta` null-check, mint a `createDisplayItem`-ben.

### `GUI-7` — `MarketHolder.getInventory()` fantom-inventory close után
- [ ] **Fájl:** `gui/MarketHolder.java:57`
- **Súlyosság:** 🟢 LOW
- **Leírás:** close után `inventory=null`; a fallback új 9-slotos inventoryt gyárt ugyanazzal a holderrel →
  félrevezető holder-identitás/slot-térkép.
- **Fix:** tárolt inventory vagy `IllegalStateException` (mint `ProfileHolder`).

### `GUI-8` — `MarketGUIListener` NEXT_SLOT nincs lapszám-ellenőrzés
- [ ] **Fájl:** `listeners/MarketGUIListener.java:52`
- **Súlyosság:** 🟢 LOW
- **Leírás:** utolsó lapon a NEXT-katt is újranyit (a `open()` clamppel), felesleges rebuild/scan; a PREV guardolt.
- **Fix:** tükrözd a PREV guardot (csak ha van következő lap).

### `CMD-6` — `/territory claim ... <radius>` felső korlát nélkül
- [ ] **Fájl:** `commands/TerritoryCommand.java:207`
- **Súlyosság:** 🟢 LOW
- **Leírás:** `parseRadius` csak `<=0`-t utasít el; közel `Integer.MAX_VALUE` radius lefagyaszthatja/OOM-olhatja a szervert (admin-only).
- **Fix:** ésszerű max-clamp; ugyanígy a ParkourCommand radius/reward.

### `CMD-7` — `SinnerCommand.suggest()` teljes roster prefix-szűrés nélkül
- [ ] **Fájl:** `commands/SinnerCommand.java:95`
- **Súlyosság:** 🟢 LOW
- **Leírás:** `args.length==0`-nál minden online név vissza, szűrés nélkül (a többi ág szűr); konzisztencia-nit.
- **Fix:** prefix-szűrés / az `args.length==0` ág elhagyása.

### `CMD-8` — `/currency exchange <Long.MAX_VALUE>` túlcsordulás
- [x] ✅ **Javítva (1. csomag)** — az atomikus `tryDeduct` + a from-egyenleg kapu megszünteti az overflow-mintet. **Fájl:** `commands/currency/CurrencyExchangeSubcommand.java:60`
- **Súlyosság:** 🟢 LOW (minta A)
- **Leírás:** `value*rate` túlcsordulhat a managerben; a wrap-elt pozitív érték hibás jóváírást adhat.
- **Fix:** input-cap (balansz/config max) + `Math.multiplyExact`/`BigDecimal` a `exchange`-ben.

### `CORE-6` — `MetelytepoManager` freeze nincs lemondva `disable()`-kor
- [ ] **Fájl:** `managers/MetelytepoManager.java:318`
- **Súlyosság:** 🟢 LOW (overlap `RELIC-3`)
- **Leírás:** a függő unfreeze-task elveszhet shutdownkor; a játékos fagyott mozgásállapotban maradhat a következő indításkor.
- **Fix:** `shutdown()` ami az online játékosok freeze-effektjeit/attribútumait visszaállítja.

### `CORE-7` — `ConfigManager` minden reloadnál `saveConfig()`
- [ ] **Fájl:** `managers/ConfigManager.java:29`
- **Súlyosság:** 🟢 LOW
- **Leírás:** `copyDefaults(true)` + `saveConfig()` minden (re)loadnál újraírja az 1057 soros configot,
  elveszítve az operátor-kommenteket/formázást; rossz szálon torz írás kockázata.
- **Fix:** csak akkor `saveConfig()`, ha tényleg bővült a default; írás a global region scheduleren.

### `CORE-8` — Konstruktor-idejű, config-függő init a `load()` előtt
- [ ] **Fájl:** `core/IceSMPCore.java:211` (és `IceSMP.onEnable`)
- **Súlyosság:** 🟢 LOW
- **Leírás:** a managerek a `configManager.load()` (~312) előtt épülnek; bármely konstruktor-idejű config-olvasás
  csak fallbacket lát. Konstruktor-közbeni kivételnél fél-inicializált plugin.
- **Fix:** minden config-függő init az `enable()`-be (load után); side-effect-mentes konstruktorok; try/catch az enable-ön.

### `DATA-5` — `TalentAttributeListener` nincs respawn-hook
- [ ] **Fájl:** `listeners/TalentAttributeListener.java:17`
- **Súlyosság:** 🟢 LOW
- **Leírás:** a talent-attribútumok csak joinkor (idempotensen) kerülnek fel; nincs `PlayerRespawnEvent` — ma
  biztonságos (a vanilla megtartja respawnnál), de törékeny, ha más rendszer törli őket.
- **Fix:** `PlayerRespawnEvent`/`PlayerChangedWorldEvent` hook (idempotens újraalkalmazás).

### `DATA-6` — Halászat két listenerben + crops-feltételezés
- [ ] **Fájl:** `listeners/ProfessionXpListener.java:145`
- **Súlyosság:** 🟢 LOW
- **Leírás:** `ProfessionXpListener` és `QuestProgressListener` is kezeli a `CAUGHT_FISH`-t (szándékos szétválasztás);
  a CROPS-ág csak `Ageable`-t kezel.
- **Fix:** dokumentálni a kettős figyelést; a CROPS-halmazt szinkronban tartani az `Ageable`-feltevéssel.

### `PET-1` — Nekromanta petek sosem szintlépnek (lásd combat-review)
- [ ] **Fájl:** `listeners/PetXpListener.java:30`
- **Súlyosság:** 🟠 HIGH (itt listázva a diff-review folytonosságáért)
- **Leírás:** `if (killer == null || !petManager.isBeastMaster(killer)) return;` — az `addXp`/`canOwnPet`
  mindkét specet támogatja, de az egyetlen XP-forrás `isBeastMaster`-re szűr → a Nekromanta petje örökre 1. szint.
  Plusz csak közvetlen `getKiller()`-ölésre fut (pet/spell/DoT ölés null killer → semmi), ellentétben a
  dokumentált „nearby kills"-szel.
- **Fix:** a kapu `canOwnPet` legyen; az XP a gazda közeli öléseiből (ne csak közvetlen `getKiller`).

### `PET-2` — Pet-halál/despawn nincs kezelve (lásd `CMBT-4`)
- [ ] **Fájl:** `managers/PetManager.java` (`entityKey` életciklus)
- **Súlyosság:** 🟠 HIGH (itt listázva a diff-review folytonosságáért)
- **Leírás:** a pet-UUID csak `dismiss()`-ben törlődik; nincs `EntityDeathEvent`/remove-handler. Halál után az
  `entityKey` halott UUID-ra mutat, a `/pet summon` ingyen újraspawnol a tárolt típusból (nincs büntetés/értesítés),
  és a perzisztens petek `disable()`-kor árván maradnak (halmozódnak).
- **Fix:** entity-szintű death/remove-listener, ami törli az `entityKey`/`combatTargets`/`attackReady`-t és
  (opcionálisan) értesíti a gazdát; nem a `dismiss()`-re aggatva.

### `PET-8` — „Frakció-valutában jutalom" 3× duplikálva
- [ ] **Fájl:** `AchievementManager.award`, `ParkourManager.checkFinish`, `DailyQuestManager`
- **Súlyosság:** 🟢 LOW (reuse)
- **Leírás:** a `getFaction → CurrencyType.fromFactionType → addToBalance` blokk három helyen másolva.
- **Fix:** `CurrencyManager.rewardInFactionCurrency(Player, amount)` kiemelése.

### `PET-9` — Entity-feloldó boilerplate 4× a `PetManager`-ben
- [ ] **Fájl:** `managers/PetManager.java` (`tick`, `removeActive`, `resolveTarget`, `activePet`)
- **Súlyosság:** 🟢 LOW (reuse)
- **Leírás:** a `UUID.fromString → Bukkit.getEntity → try/catch → instanceof Mob` minta négyszer.
  (Plusz: `AchievementManager`/`StatsManager` 60 mp-enként minden játékost újraszámol rövidre-zárás nélkül.)
- **Fix:** közös `activePet`/helper használata.

---

## Javasolt javítási sorrend

A leghatékonyabb a **mintánkénti** javítás (egy minta-fix sok találatot zár), kockázat szerint csomagolva:

1. **Gazdaság-stop (azonnal):** `ECON-2`, `ECON-1`, `ECON-4`, `ECON-3` + minta A (`CMD-1`, `CMD-2`, `CMD-3`, `CMD-8`).
   *Élesben pénzt törnek, részben unprivilegizáltan.*
2. **Spell-helyesség:** `SPELL-2` (Root), `SPELL-3`+`SPELL-4` (minta F + combo-cap), `SPELL-5` (Hide).
3. **Folia cross-region (minta C):** `DATA-1`, `CMBT-1`, `CMBT-2`, `SPELL-1`, `GUI-1`, `GUI-2`, `CMD-4`, `CMBT-5`/`PET-6`, `ECON-7`.
4. **Lifecycle/persistence:** minta D (`CMBT-3`, `CMBT-4`, `CORE-4`), `CORE-3` sorrend, minta B central save (`ECON-4`, `RELIC-1`, `CORE-5`), minta E quit-cleanup (`DATA-3`, `DATA-4`, `RELIC-3`).
5. **Pet + talent + relikvia-dupe:** `PET-1`, `PET-2`, `PET-3`, `PET-5`; `ECON-5`; `RELIC-2`, `RELIC-4`, `RELIC-5`.
6. **A maradék MEDIUM/LOW + reuse:** `PET-4`, `PET-7`, `GUI-3`, `PET-8`, `PET-9`, és a többi LOW.

> **Emlékeztető:** a javítások után **`./gradlew build`** és szerver-teszt szükséges — a session sok nem-fordított
> kódot adott hozzá, és a Folia-súlyosságok futtatás nélkül nem igazolhatók teljesen.
