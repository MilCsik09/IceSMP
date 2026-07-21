# O — Refaktor-jelöltek / technikai adósság (egyesített backlog)

> 2026-07-20: a docs/REFACTOR_CANDIDATES.md 1:1 átemelve az ideas-rendszerbe, kiegészítve
> a teljes kódbázis-audit N19-N23 tételeivel (a fájl végén). Státusz-frissítés: a #9
> (DonationChest debounce) az auditkörben elkészült.

Ez a dokumentum a 2026. júliusi átfogó kód-audit során **talált, de szándékosan nem javított**
leleteket rögzíti részletesen: mindegyiknél leírjuk a problémát, hogy miért maradt ki a
javító körből, a javasolt megoldást és a becsült munka/kockázat arányt. A célja, hogy egy
későbbi takarító kör ne a nulláról induljon.

A javított hibák NEM szerepelnek itt — azok a `claude/plugin-bug-fixes-tqqmsr` branch
commitjaiban vannak dokumentálva. A nyitott feature-munkák helye továbbra is a
[ROADMAP.md](../ROADMAP.md); ez a fájl kizárólag technikai adósság.

Jelölés: 🔴 érdemes hamarosan • 🟡 következő takarító körre • 🟢 ráér / kozmetikai

---

## Gyors áttekintés

| # | Jelölt | Kategória | Prioritás |
|---|--------|-----------|-----------|
| 1 | `StatsManager` láthatósági race | Konkurencia | 🟡 |
| 2 | Világesemény-managerek force-vs-tick race | Konkurencia | 🟡 |
| 3 | `SunDanceSpell` recept-cache check-then-act | Konkurencia | 🟢 |
| 4 | `prefixAt` helper 17 fájlban duplikálva | Duplikáció | 🟡 |
| 5 | Spell-célzás duplikált mintái | Duplikáció | 🟡 |
| 6 | Világesemény-közös minták (`WorldEventUtil`) | Duplikáció | 🟡 |
| 7 | `QuestManager.handleTerritoryEnter` O(questek) | Teljesítmény | 🟢 |
| 8 | `RelicItemFactory` reflexiós metódus-scan cache | Teljesítmény | 🟢 |
| 9 | `DonationChestManager` szinkron mentés | Teljesítmény | ✅ KÉSZ (2026-07-20 audit) |
| 10 | `FactionPassiveListener` korai kilépés sorrendje | Teljesítmény | 🟢 |
| 11 | GUI close-cleanup inkonzisztencia (3 listener) | Minta-konzisztencia | 🟢 |
| 12 | `CommandMenus` körözési lista cross-region PDC-olvasás | Folia-szigor | 🟢 |
| 13 | Bank/exchange gate-ellenőrzés sorrendje | Kozmetika | 🟢 |
| 14 | `RaidManager.participants` kilépéskori helyfoglalás | Gameplay-megfigyelés | 🟢 |
| 15 | Escort-útpontok `getHighestBlockYAt` pontatlanság | Kozmetika (ROADMAP-örökség) | 🟢 |
| 16 | Aukció: licit elérheti a buy-outot — GUI-tipp hiányos | Kozmetika (ROADMAP-örökség) | 🟢 |
| 17 | `CharacterGUIListener` — GUI-ba duplikált respec-logika | Duplikáció / minta-sértés | 🟡 |
| 18 | DisplayFx — nincs `max-per-player` entitás-plafon | Teljesítmény / robusztusság | 🟡 |
| 19 | `DisplayFxUtil.showOnlyTo` — `hideEntity` kereszt-száli hívása | Folia-szigor | 🟢 |
| 20 | Per-nézős fx-entitások skálázódása (aurora/claim-fal) | Teljesítmény | 🟢 |

A dokumentum végén: **megvizsgált, de teendőt nem igénylő** esetek (hogy egy későbbi audit
ne vizsgálja meg őket újra feleslegesen).

---

## Konkurencia

### 1. 🟡 `StatsManager` — láthatósági race a ranglista-mezőkön

**Hely:** `managers/StatsManager.java` (kb. 35–40. sor: belső `Stat` osztály; írók:
`recordSnapshot`, `recordRaidKill`; olvasó: `top()`).

**Probléma:** a `Stat` osztály mezői (`name`, `level`, `wealth`, `raidKills`) sima, nem
`volatile` mezők. Különböző játékosok saját régió-szálai írják őket szinkronizáció nélkül,
miközben a `top()` a globális tick-szálról streamben olvassa. A `raidKills++` ráadásul
nem-atomi read-modify-write, tehát egyidejű két kill esetén egy inkrement elveszhet.
A Java memóriamodell szerint az olvasó szál elavult értéket láthat.

**Miért nem javítottuk:** kizárólag a ranglista-megjelenítést érinti (nincs gazdasági vagy
perzisztencia-hatás), és a hibás olvasás következménye legfeljebb egy pár másodpercig
pontatlan toplista.

**Javasolt megoldás:** a `Stat` mezőit `volatile`-ra váltani, a `raidKills`-t
`AtomicInteger`-re; VAGY a teljes `Stat`-ot immutable rekorddá alakítani és a map-ben
`compute`-tal cserélni (ez utóbbi illik a kódbázis copy-on-write mintáihoz).

**Munka/kockázat:** ~fél óra, alacsony kockázat. Fordítás-ellenőrzés elég.

### 2. 🟡 Világesemény-managerek — admin-force vs. periodikus tick race

**Hely:** `BloodMoonManager`, `CaravanManager`, `AbundanceManager`, `EscortManager`,
`InvasionManager`, `GatheringBuffManager` — pl. `EscortManager.forceStart` (`synchronized`)
vs. a `tick()`-ből hívott, NEM szinkronizált `start()` (kb. 120–153. sor).

**Probléma:** az esemény-állapotot (`active`, `convoyId` stb.) a periodikus `tick()`
(globális régió-szál) és az admin `/events <típus>` force-parancsok (a parancsot kiadó
játékos régió-szála) is ellenőrzik-majd-írják. A mezők `volatile`-ok, de a
check-then-act szekvencia nem atomi, és több managernél csak az egyik belépési pont van
`synchronized`-olva. Ha az admin pont a tick-indítás pillanatában ad ki force-ot,
elméletileg két konvoj/karaván/invázió indulhat egyszerre, orphan entitásokat hagyva.

**Miért nem javítottuk:** nagyon alacsony valószínűségű (emberi időzítés + másodperces
tick-raszter), nincs gazdasági hatás, és 6 managert érint — összehangolt, egységes javítást
érdemel, nem eseti foltozást.

**Javasolt megoldás:** managerenként egyetlen monitor: a `tick()`-beli indító ág ÉS a
force-metódusok ugyanazon a lockon (legegyszerűbb: mindkét belépési pont egy közös
`synchronized startInternal(...)`-t hív, ami maga ellenőrzi az `active` flaget).
Érdemes a 6. ponttal (`WorldEventUtil`) együtt megcsinálni, mert ugyanazokat a fájlokat
érinti.

**Munka/kockázat:** ~1–2 óra a 6 managerre, alacsony kockázat, de kézi playtest ajánlott
(`/events` force-parancsok az aktív esemény alatt).

### 3. 🟢 `SunDanceSpell` — recept-cache dupla felépítése

**Hely:** `spells/SunDanceSpell.java` (kb. 22. és 106–128. sor, `recipeCachePopulated`).

**Probléma:** a `volatile boolean recipeCachePopulated` flag check-then-act mintával védi a
recept-cache egyszeri felépítését. Két különböző régió-szálon egyidejűleg castolt SunDance
mindkét szálon felépítheti a cache-t. Nem adatvesztés (az eredmény ugyanaz), csak felesleges
duplán végzett munka egyetlen alkalommal.

**Javasolt megoldás:** `ConcurrentHashMap.computeIfAbsent`-alapú lazy-init, vagy a cache
felépítése a konstruktorban/`enable()`-ben (a recept-katalógus akkor már elérhető).

**Munka/kockázat:** ~15 perc, kockázatmentes.

---

## Duplikáció (helperbe emelés)

### 4. 🟡 `prefixAt(String[], int)` — 17 fájlban szó szerint azonos helper

**Hely:** `commands/` alatt 17 osztály tartalmazza ugyanazt a private static metódust:
`ClaimCommand`, `NpcBindCommand`, `TerritoryCommand`, `QuestCommand`, `PartyCommand`,
`SpecCommand`, `EventsCommand`, `SpellCommand`, `ProfessionCommand`, `RelicCommand`,
`ParkourCommand`, `TalentCommand`, `FactionKingSubcommand`, `FactionRaidSubcommand`,
`JobAdminSubcommand`, `JobUnlockSpellSubcommand`, `CurrencyExchangeSubcommand`
(+ az újonnan hozzáadott currency tab-complete-ek inline változatai).

**Probléma:** a tab-complete-hez használt „az adott pozíción gépelés alatt álló szó
(kisbetűsítve), vagy üres" segéd mindenhol újra van írva. Egy jövőbeli viselkedés-változtatás
(pl. ékezet-normalizálás) 17+ helyen igényelne módosítást.

**Javasolt megoldás:** default metódusként a `commands/Subcommand` interfészre emelni
(`default String prefixAt(String[] args, int index)`), vagy egy `commands/CommandUtil`
osztályba. Utána mechanikus csere fájlonként — ez tipikusan delegálható feladat
(haiku-szintű, fájlonként azonos recept).

**Munka/kockázat:** ~1 óra, mechanikus; fordítás-ellenőrzés elég.

### 5. 🟡 Spell-célzás duplikált mintái

**Hely / két minta:**
- **„Ray-trace célpont vagy hibaüzenet+false":** `BeeSwarmSpell` (kb. 31–35.),
  `LifeDrainSpell` (27–31.), `ShadowstepSpell` (30–34.), `VenomStrikeSpell` (31–35.) —
  szinte szó szerint azonos blokk (rayTrace → null-check → `resolveMessage("no-target",…)`
  → `return false`).
- **„Körzeti élőlény-szűrés":** `BoneChillSpell`, `GustSpell`, `RootSpell`,
  `SmokeBombSpell`, `ConfusionSpell`, `PrimalBondSpell` — mind ugyanazt a
  `getNearbyEntities(...)` + `instanceof LivingEntity` + `entity == player` szűrést írja le.
  (Az üres-körzet visszatérítést a 4 érintett spellnél már javítottuk — a szűrés-duplikáció
  maradt.)

**Javasolt megoldás:** két helper a meglévő `SpellTargetingUtil`-ba:
- `LivingEntity requireTarget(BaseSpell spell, Player player, double range)` — null-nál
  maga küldi a no-target üzenetet;
- `List<LivingEntity> nearbyLiving(Player player, double radius)` — a kaszter kihagyásával.

Fontos: a hívók viselkedése NEM változhat (ugyanaz az üzenet-kulcs, ugyanaz a
sugár-szemantika), különben a playtest-checklist is frissítendő.

**Munka/kockázat:** ~1–2 óra 10 fájlra, alacsony kockázat; 1-2 spell kézi playtestje ajánlott.

### 6. 🟡 Világesemény-közös minták — `WorldEventUtil` / `TransientEntityHandle`

**Hely:** az esemény-managerek 5–8 helyen duplikálják ugyanazokat a mintákat
(a ROADMAP korábbi code-review-jának öröksége): véletlen horgony-játékos választás,
perc→millis konverzió, enabled-enum sorsolás, mulandó entitás biztonságos eltávolítása
(érintett többek közt: `TreasureEventManager`, `WildHuntManager`, `MeteorEventManager`,
`CaravanManager`, `EscortManager`, `AmbientEventManager`).

**Javasolt megoldás:** egy `managers/WorldEventUtil` (statikus helperek: horgony-választás,
idő-konverzió, súlyozott enum-sorsolás) és/vagy egy `TransientEntityHandle` (entitás-spawn +
élettartam + biztonságos remove a saját schedulerén). A 2. ponttal (force-vs-tick lock)
együtt érdemes, mert ugyanazok a fájlok.

**Munka/kockázat:** ~2–3 óra, közepes — világesemény-playtest kell utána
(`/events <típus>` mindegyik érintettre).

---

## Teljesítmény

### 7. 🟢 `QuestManager.handleTerritoryEnter` — O(összes quest) minden belépésre

**Hely:** `managers/QuestManager.java` (kb. 712–741. sor).

**Probléma:** minden territórium-belépés eseménynél végigmegy az ÖSSZES quest-id-n és
mindegyikhez `getQuestSection()`-t hív, hogy megtalálja az auto-start questeket. A jelenlegi
katalógus-méretnél (tucatnyi quest) ez elhanyagolható, de a quest-szám növekedésével
belépésenkénti lineáris költség.

**Javasolt megoldás:** a config-betöltéskor (és `/quest admin` mentéskor) egy
`Map<String territoryId, List<String questIds>>` auto-start index felépítése — a belépéskor
csak egy map-lookup marad. A meglévő copy-on-write minta (`customQuests`) követhető.

**Munka/kockázat:** ~1 óra; figyelni kell, hogy a `/quest admin` szerkesztő minden
mentési útvonala invalidálja az indexet.

### 8. 🟢 `RelicItemFactory` — ismételt reflexiós metódus-pásztázás

**Hely:** `items/RelicItemFactory.java` (kb. 203–284. sor,
`createPickaxeToolRule`/`applyPickaxeToolRule`).

**Probléma:** minden Mételytépő-relikvia `create()`/`refresh()` híváskor újra végigpásztázza
a `ToolRule`/`ToolComponent` API metódus-tömbjét reflexióval, holott a felderített
`Method`-referenciák a plugin életciklusa alatt változatlanok.

**Javasolt megoldás:** lazy-init statikus cache a `Method`-referenciákra (a
`MethodHandles`-re váltás opcionális extra). A meglévő reflexiós hidak (integration/)
mintája követhető: egyszeri felderítés, `volatile` mezőben tárolva, hibánál no-op.

**Munka/kockázat:** ~fél óra, alacsony; egy relikvia-refresh kézi ellenőrzése elég.

### 9. ✅ KÉSZ — `DonationChestManager` — szinkron mentés (2026-07-20 auditkörben javítva: requestSave debounce)

**Hely:** `managers/DonationChestManager.java` (kb. 141–183. sor, `donateHeldItem`,
`takeEntry`).

**Probléma:** minden adományozás/kivétel szinkron `save()`-et hív (teljes YAML-írás a
régió-szálon). NEM hot path (ritka, játékos-vezérelt akció), így ez tisztán konzisztencia-
kérdés: a testvér-managerek (Currency/Claim/Market/Season/CommunityGoal) már mind a
debounce-olt `requestSave()` mintát használják.

**Javasolt megoldás:** ugyanaz a 2 másodperces `AtomicBoolean` + `getAsyncScheduler().runDelayed`
debounce, `save()` szinkron marad shutdownra. Copy-paste a `SeasonManager`-ből.

**Munka/kockázat:** ~15 perc, kockázatmentes.

### 10. 🟢 `FactionPassiveListener.onEntityDamage` — korai kilépés sorrendje

**Hely:** `listeners/FactionPassiveListener.java` (kb. 58–90. sor).

**Probléma:** minden `EntityDamageEvent`-nél (minden ütés, esés, fulladás a szerveren)
előbb fut le a `factionManager.getFaction(...)` lookup és a cause-`switch`, mint hogy a
kód eldöntené, releváns-e egyáltalán a damage-ok. Olcsó map-olvasás, tehát nem mérhető
probléma — de a leggyakoribb eventek egyikén fut, és a fordított sorrend ingyen van.

**Javasolt megoldás:** először a damage-cause szűrés (egy előre felépített
`EnumSet<DamageCause>.contains`), és csak találat esetén a faction-lookup.

**Munka/kockázat:** ~15 perc, kockázatmentes.

---

## Minta-konzisztencia / Folia-szigor

### 11. 🟢 GUI close-cleanup hiánya 3 listenerben

**Hely:** `listeners/ProfessionRecipeBookListener.java`, `listeners/QuestBuilderListener.java`,
`listeners/SpellbookListener.java` — nincs `InventoryCloseEvent` handlerük.

**Probléma:** a projekt dokumentált GUI-mintája szerint a listener a close-eventet is kezeli
(`holder.setInventory(null)`). Ez a három listener nem teszi. NEM szivárgás és nem exploit —
minden megnyitás új holdert gyárt, a régi a GC-re marad —, csak eltérés a többi 8 listener
mintájától, ami egy jövőbeli, state-et hordozó holder-módosításnál csapdává válhat.

**Javasolt megoldás:** a többi GUI-listener close-handlerének átvétele (3×6 sor), vagy a
minta kivezetése egy közös `GuiListenerBase`-be (nagyobb refaktor, csak akkor éri meg, ha
egyébként is nyúlunk a GUI-réteghez).

**Munka/kockázat:** ~20 perc, kockázatmentes.

### 12. 🟢 `CommandMenus` — körözési lista más játékosok PDC-jét olvassa hop nélkül

**Hely:** `gui/CommandMenus.java` (kb. 705–710. sor) → `SinManager.getSinCount`.

**Probléma:** a körözési-lista menü építésekor a menüt nyitó játékos szálán végigmegy a
`Bukkit.getOnlinePlayers()`-en és mindegyik PDC-jéből olvassa a bűn-számlálót. A szigorú
Folia-szabály szerint más entitás PDC-olvasása is hopot igényelne; gyakorlatban a PDC-olvasás
nem dob szál-ellenőrzési hibát, a kockázat csak elavult érték megjelenítése. A GUI-építés
szinkron természete miatt a hop-per-játékos itt architekturálisan kényelmetlen.

**Javasolt megoldás:** a `SinManager` tartson egy memóriabeli, a bűn-változásokkor frissített
`Map<UUID, Integer>` snapshotot (a PDC marad a perzisztens forrás), és a menü ebből olvasson.
Alternatíva: async menü-összeállítás, de az aránytalan bonyolítás.

**Munka/kockázat:** ~1 óra, alacsony; kézi teszt: bűn-szerzés után a menü frissül-e.

---

## Kozmetika / gameplay-megfigyelések

### 13. 🟢 Bank/exchange — gate-ellenőrzés az arg-validálás előtt

**Hely:** `commands/bank/BankWithdrawSubcommand.java`, `commands/currency/CurrencyExchangeSubcommand.java`.

**Probléma:** a „csak fővárosban használható" kapu-ellenőrzés megelőzi az `args.length`
ellenőrzést, így a nem fővárosban álló játékos hibás argumentumokra is a főváros-hibát
kapja a használati útmutató helyett. Működésre nincs hatása.

**Javasolt megoldás:** az arg-validálást előre venni (a testvér-subcommandok sorrendje).

**Munka/kockázat:** ~10 perc, kockázatmentes.

### 14. 🟢 `RaidManager.participants` — kilépő résztvevő a raid végéig foglalja a helyet

**Hely:** `managers/RaidManager.java` (kb. 56. sor, `participants`).

**Megfigyelés:** ha egy jelentkezett résztvevő aktív raid közben kilép, a bejegyzése a raid
végéig megmarad, tehát a 10v10 oldal-korlátból egy helyet „fogva tart". Ez szándékos
tervezésnek tűnik (visszacsatlakozó játékos folytathatja), NEM memória-szivárgás
(`startRaid`/`endRaid`/`shutdown` törli). Ha gameplay-oldalon problémát okoz (rage-quit
helyfoglalás), egy türelmi idős auto-kick (pl. 2 perc offline után szabadul a slot) a
megoldás — de ez balansz-döntés, a szerver-csapaté.

### 15. 🟢 Escort-útpontok — `getHighestBlockYAt` lombkorona/víz felett

**Hely:** `managers/EscortManager.java` (útpont-generálás). (ROADMAP-örökség.)

**Probléma:** az escort-konvoj útpontjai a legmagasabb blokk tetejére kerülnek, ami erdőben
a lombkorona TETEJÉT, víznél a vízfelszínt jelentheti — a konvoj útvonala kozmetikailag
furcsa lehet. A kincs/meteor eseményeknél ugyanez már kezelve van (ott van „szilárd talaj"
keresés lefelé).

**Javasolt megoldás:** a kincs/meteor helykereső helperének kiemelése (illeszkedik a 6. pont
`WorldEventUtil`-jába) és használata az escort-útpontokra.

**Munka/kockázat:** ~fél óra a 6. ponttal együtt; escort-esemény playtest kell.

### 16. 🟢 Aukció — a minimum/nagy licit elérheti a buy-out árat és azonnal zár

**Hely:** `managers/MarketManager.java` (bid/buy-out logika), `gui/MarketGUI` (tooltip).
(ROADMAP-örökség.)

**Megfigyelés:** eBay-szemantika — ha a (bal-kattos minimum vagy jobb-kattos +25%) licit
eléri a buy-out árat, az aukció azonnal lezárul. Ez SZÁNDÉKOS, de a GUI-tipp csak a
shift-katt = buy-out lehetőséget említi, a „licited elérheti a buy-outot és azonnal nyersz"
esetet nem. Emellett a vétel-üzenetben szereplő ár elvben eltérhet a ténylegesen levonttól,
ha a frakció-viszony (ár-szorzó) pont a kattintás pillanatában vált — kirakati eltérés,
a levonás maga konzisztens.

**Javasolt megoldás:** a MarketGUI aukciós tooltip kiegészítése egy sorral; az üzenetben a
ténylegesen levont összeg visszaírása a becsült helyett.

**Munka/kockázat:** ~fél óra, kockázatmentes.

### 17. 🟡 `CharacterGUIListener` — a respec-logika duplikálva a GUI-ban

**Hely:** `listeners/CharacterGUIListener.java` (a spec-menü respec-ága, kb. 215–245. sor) vs.
`commands/SpecCommand.handleRespec`.

**Probléma:** a projekt dokumentált menü-mintája szerint „a gameplay-logika MINDIG a
parancsokban marad, a menü csak delegál" (`RUN:<parancs>` akcióval). A karakter-menü
respec-gombja ehelyett újraimplementálja a teljes respec-folyamatot (ár-levonás,
spec-reset, talent-visszatérítés, üzenetek) — a 2026-07-14-i auditkör pont emiatt találta
meg ugyanazt a get+set balance-race-t itt is, amit a parancsban már javítottunk (azóta
mindkét hely atomi `deductFromBalance`-t használ, de a duplikáció maradt). Minden jövőbeli
respec-szabály (pl. ár-változás, új visszatérítési ág) két helyen igényel módosítást.

**Javasolt megoldás:** a respec-folyamat kiemelése egy közös helperbe (pl.
`SpecializationManager.performRespec(player, classPool)` ami a teljes tranzakciót viszi és
eredmény-objektumot ad), a parancs ÉS a GUI is ezt hívja; VAGY a GUI-gomb átállítása
`RUN:spec respec ...` delegálásra a menü-minta szerint.

**Munka/kockázat:** ~1 óra, alacsony; respec kézi playtest (parancsból + menüből) kell.

---

## DisplayFx-réteg (a #17 PR-review leletei, 2026-07-19)

Az új display-entity effekt-réteg (`utils/DisplayFxUtil`, claim-fényfal / crate-3D / boss-telegraph
/ aurora-fátyol) code-review-ja során felmerült, **nem blokkoló** finomítások. A réteg alap-
életciklusa (régió-száli spawn + `setPersistent(false)` + `FX_TAG` + chunk-load söprés + entitás-
saját-scheduleres auto-despawn) rendben; ezek robusztussági/teljesítmény-tartalékok.

### 18. 🟡 DisplayFx — nincs `max-per-player` entitás-plafon

**Hely:** `managers/ClaimManager.java` → `showDisplayWalls`; `utils/DisplayFxUtil.java`.

**Probléma:** a `/claim show` a közeli claimeket egy chunk-sugarú (`claims.border.radius`, default 2)
scannel gyűjti, és **claimenként 4 fal-entitást** spawnol a nézőnek. Sok, egymáshoz közeli claim
esetén (sűrűn beépített terület) ez egyszerre sok display-entitást hozhat létre. Az A71-ötlet
kifejezetten említett egy `display-fx.max-per-player` (~24) plafont, ami a megvalósításból kimaradt.
A gyakorlati kockázat alacsony (a scan-sugár kicsi, az entitások rövid életűek és auto-despawnolnak),
de egy felső korlát olcsó biztosíték a patológiás esetekre.

**Javasolt megoldás:** `display-fx.max-per-player` config-kulcs (default ~24); a `showDisplayWalls`
számolja a már kihelyezett fal-entitásokat, és a plafon elérésekor hagyja abba a további claimek
kirajzolását (a legközelebbieket előnyben részesítve). Alternatíva: a feldolgozott közeli-claim
darabszám korlátozása. A crate-reveal (1 entitás) és a boss-telegraph (1 lap) nem érintett.

**Munka/kockázat:** ~30 perc, alacsony; kézi teszt: sok szomszédos claim között `/claim show`.

### 19. 🟢 `DisplayFxUtil.showOnlyTo` — `hideEntity` kereszt-száli hívása

**Hely:** `utils/DisplayFxUtil.java` → `showOnlyTo`.

**Probléma:** a per-nézős elrejtés az fx-entitás régió-szálán fut (a spawn-taskból hívva), de ott
végigmegy a `Bukkit.getOnlinePlayers()`-en, és a *többi* játékosra hív `player.hideEntity(plugin, fx)`-et
— ez más régió-szálakhoz tartozó játékosok érintése. A Paper `hideEntity` implementációja ezt
jellemzően tolerálja (tracker-művelet + csomag), így a kockázat alacsony, de a szigorú Folia-szabály
szerint más entitás érintése hopot igényelne.

**Javasolt megoldás:** három út a kompromisszum szerint — (a) elfogadjuk és dokumentáljuk mint tolerált
mintát (mint a #12-nél); (b) a `hideEntity`-t minden célpont saját schedulerére hopoljuk (biztosabb,
de per-játékos hop-költség); (c) ha az API enged spawn-idejű néző-korlátozást, azt használjuk. A
jelenlegi egyszerű „broadcast-then-hide" a legolcsóbb; a hop csak akkor indokolt, ha a gyakorlatban
szál-hibát tapasztalunk.

**Munka/kockázat:** ~1 óra, alacsony; Folia-szerveren tesztelendő (több online játékos, claim-show).

### 20. 🟢 Per-nézős fx-entitások skálázódása (aurora / claim-fal)

**Hely:** `managers/AmbientEventManager.java` → `spawnAuroraVeil` (2 lap/játékos);
`managers/ClaimManager.java` → `showDisplayWalls` (4/claim/néző).

**Probléma:** a per-nézős effektek entitás-száma a létszámmal skálázódik. Egy aurora-esemény minden
online játékosnak 2 égi lapot spawnol egyszerre — nagy létszámnál pillanatnyi entitás-csúcs (2·N).
Rövid életűek és auto-despawnolnak, így elfogadható, de mérésre érdemes szélsőséges létszámnál.

**Javasolt megoldás:** az aurora esetén megfontolható **világ-szintű, közös fátyol** a per-játékos
helyett (elveszik a per-nézős párosítás az éjjellátással, cserébe konstans entitás-szám); vagy egy
globális egyidejű-fx plafon. Alacsony prioritás — előbb mérni kell, valóban gond-e nagy szerveren.

**Munka/kockázat:** ~1–2 óra, alacsony; teljesítmény-mérés kell a döntéshez (ne optimalizáljunk vakon).

---

## Megvizsgálva — teendő NINCS

Ezek az auditban felmerült, de elemzés után nem-problémának minősített esetek. Azért
rögzítjük őket, hogy egy későbbi audit ne költsön rájuk újra időt.

- **`IceSMPCore.disable()` közvetlen player-cleanup** (scheduler-hop nélkül): dokumentált,
  szándékos kivétel — shutdownkor a Folia-ütemező már nem fogad taskot
  (ARCHITECTURE §4.1 „Kivétel — disable()").
- **`HideSpell`/`InnerFocusSpell` relog-race gyanú** (a lejáró visszaállító task felülírja
  az új session állapotát): NEM lehetséges — a task a játékos entity-schedulerén fut, ami a
  kilépéskor a retired-callbackkel együtt megszűnik; az újracsatlakozó játékos új entitás,
  amin a régi task már nem fut le. A kilépéskori visszaállítást a PlayerQuit-cleanup fedi.
- **`ProfileHolder` owner-ellenőrzés** és a **GUI cancel-lefedettség** (shift-klikk,
  hotbar-swap, double-click): a 2026-07-14-i körben javítva/ellenőrizve — mind a 14 holder
  feltétel nélküli `setCancelled(true)`-val és owner-checkkel védett.
- **`MarketManager` load-oldali hibatűrés** (hibás bidder-UUID nem dobja el a listinget):
  már defenzív, adatvesztés-mentes.
- **`YamlStore.saveAtomic`**: egyedi temp-fájl + atomikus rename, konkurens mentésekre
  biztonságos; a debounce-olt mentések `synchronized`/`saveLock` alatt futnak.
- **Kill-reward listener-család** (ClassXp/PetXp/SoulShard/Soulstone/WorldBoss/WildHunt/
  Escort/Party/MobLoot/ServerChallenge): mind MÁS jutalmat ad ugyanarra a halál-eventre —
  nincs dupla-jóváírás.

## 2026-07-19-i audit-kör (kijavítva + új jelöltek)

### Kijavítva ebben a körben ✅
- **PetCombatListener** — minden sebzés-eventnél az áldozat szálán, hop nélkül olvasta a
  támadó PDC-jét. Megoldás: a `setCombatTarget` szétbontva szál-helyes felekre
  (`isEligibleCombatTarget` a cél szálán, `canReceiveCombatTarget` a gazda szálán,
  `putCombatTarget` concurrent-map írás) + kétirányú hop a listenerben.
- **ClassXp/SoulShard/PetXp listener** — az eligibility-ellenőrzés (killer-PDC-olvasás) a mob
  szálán futott a hop ELŐTT; mindhárom teljes check+award a killer hopján belülre került.
- **PartyManager.getNearbyMembers** — a `source.getLocation()` védetlen kereszt-régió olvasása
  (Wild Hunt: a fenevad szálán) try/catch mögé, üres listával visszaesve (ground-drop marad).
- **AfkManager.cleanup** — a `manualAfk` a normál kilépés-útvonalon sosem ürült (a
  `clearPlayerState` online ága korán tért vissza); a cleanup törli.

### Megvizsgálva — teendő NINCS
- **WorldBossManager.handleBossDeath** — `getFaction` ConcurrentHashMap-olvasás, a potion-buff
  hoppal, a `killer.getName()` immutábilis profil-adat: szál-biztos.

### 21. 🟡 `AbilityCatalystListener` — felelősség-szétbontás
774 sor, 9 UUID-kulcsú map egy osztályban (cooldown, kombó-követés, cast-history, UI-hintek).
Bontás javasolt: cooldown-kezelő / kombó-detektor / hint-réteg.

### 22. 🟢 `MobLootListener.rollTable` — duplikált gear-fallback
Az üres-tábla ág és a switch default ága szó szerint ugyanazt a
`pickGear + affixService.roll(..., true)` párost ismétli — egy privát metódusba emelhető.

### 23. 🟢 `CrateManager.persist()` — szinkron teljes-YAML írás minden mutációnál
Ugyanaz a minta, mint a #9 (DonationChestManager) — a debounce-javítással együtt kezelendő.


---

## A teljes kódbázis-audit tételei (2026-07-20, korábban ideas/N 19-23)

### 24. 🟡 `MobKillUtil.eligibleKill` — közös kill-jutalom előszűrő
19 listener kezeli külön az EntityDeathEvent kill-jutalmat, eltérő AFK/minion/Monster-
szűréssel — közös helper kell, hogy a fékek egy helyen éljenek (a minion/AFK-réseket a
2026-07-20-i kör pontonként javította, de a minta tovább driftelhet).

### 25. 🟡 `DailyBudget` PDC-util
A nap-kulcs + összeg-kulcs "napi keret" minta 5+ helyen kézzel írva (kassza-kivét, buyer,
kém-pont, horgász-sapka, mob-pénz) — egy spend(player, key, amount, cap) util kiváltaná.

### 26. 🟡 `ErrorMessages.resolve` — közös hibakulcs→default tábla
A defaultErrorFor switch 11+ osztályban majdnem szó szerint ismétlődik.

### 27. 🟡 `PeriodicChanceEvent` — világesemény-ütemező váz
5 manager (Abundance/Ambient/Archeology/Caravan/BloodMoon) azonos "enabled→intervallum→
esély→fire" váza — közös helper; a #2 (force-vs-tick lock) és #6 (WorldEventUtil)
tételekkel EGYÜTT érdemes, ugyanazokat a fájlokat érintik.

### 28. 🟡 Elérés-küszöbök configba (AchievementManager)
A tábla hardcode-olt, az élő-config konvenciót törve; + a vagyon-elérés kölcsön-tőkével
(több játékos közt körbeadva) fejenként egyszer így is kijátszható — lifetime-betét
metrika megfontolandó.
