# Mi változott a július 12-i szerververzió óta?

<!-- icesmp-doc-id: release.deployed-build-to-release -->

> *A világ ugyanaz — de sokkal több módon válaszol arra, amit benne tesztek.*

A következő IceSMP-build jóval több egy kisebb javításnál. Saját AFK-, ülés-,
crate-, MOTD- és moderációs rendszerek érkeztek, miközben a questek, szakmák,
receptek, specializációk és világban zajló események köre is jelentősen bővült.

A kód és a CI elkészült állapota még nem egyenlő az élesítéssel. Ahol kézi,
világbejárásos vagy hibaszimulációs próba hiányzik, azt ezen az oldalon külön
jelezzük.

> **Összehasonlítási alap:** az üzemeltető által futóként átadott
> `IceSMP-1.0-TESTING.jar`. A tartalma nagy bizonyossággal a
> **2026. július 12-i** forrásállapotnak felel meg; július 13-án nem volt
> köztes mainline commit. A JAR nem tartalmaz Git SHA-t, ezért ez
> `HIGH_CONFIDENCE`, nem `EXACT` azonosítás.

## Harminc másodpercben

| Terület | Július 12-i build | Következő release |
|---|---:|---:|
| Root parancs | 30 | 68 |
| Root alias | 56 | 79 |
| Bizonyított permission | 16 | 44 |
| Funkcionális GUI-család | 14 | 22 |
| Kaszt | 13 | 13 |
| Specializáció | 31 | 35 |
| Questdefiníció | 45 | 160 |
| Szakmai recept | 124 | 438 |
| Szakmai alapanyag | 9 | 81 |
| Relikvia | 5 | 6 |
| Rituálé | 19 | 21 |
| Natív crate | 0 | 2 |

A 420 konfigurált spell-balance azonosító nem 420 automatikusan elérhető
képességet jelent: a registry, a kaszt, a specializáció és a feloldási
feltételek együtt döntik el, mit használhat a játékos.

## Naplóalapú stabilitási javítások (2026. július 30. – augusztus 2.)

- A Mélytépő attribútumfrissítése stabil kulcsú, idempotens csere: ismételt join
  és refresh nem dupláz sebzést, a nem IceSMP modifier megmarad.
- Egy hibás relikvia-slot nem szakítja félbe a teljes inventory frissítését;
  throttlingolt, UUID/slot/item/relic kontextusú hiba marad utána.
- Plugin-disable alatt az Escort/mulandó entity cleanup nem regisztrál új Folia
  taskot, a shutdown többször hívva is biztonságos.
- Új `/quest admin validatenpcs` mutatja az exact FancyNpcs belsőnév-hiányokat,
  case eltéréseket és az érintett quest/config útvonalakat. A jelenlegi exportban
  a 18 kötelező NPC továbbra is manuális builder/deployment kapu.
- A FancyNpcs-snapshot validátor ellenőrzi a lokális skinfájlokat és az NPC-hez
  kötve listázza a remote URL-eket anélkül, hogy hálózati hibától blokkolná a buildet.

## Amit játékosként észreveszel

### Új utak a karakterednek

Négy új specializációval már **35 eltérő irány** közül választhatsz. A
questtartalom több mint háromszorosára, a szakmai receptek száma több mint
háromszorosára bővült. Új anyagok, rúnák, signature tárgyak, relikvia- és
rituálétartalom került a csomagba.

Ez nem jelenti azt, hogy az élő világ minden kötése automatikusan elkészült:
NPC, helyszín, kapu, láda, resource-pack modell vagy feloldási feltétel még
igényelhet builder- és staging-átvételt.

### A világ többé nem puszta háttér

A bővített eseményrendszer világbossokat, inváziókat, vérholdat, karavánt,
kultista helyzeteket, rontás-gócokat, meteorokat, felfedezést és szezonális
kihívásokat tud megszólaltatni. Az eventes csapat ebből csak azt hirdesse meg,
aminek a helyszíneit és jutalmait az aktuális világon is végigjárta.

### Több közösségi játék

Új vagy kibővített felületet kapott a bestiárium, a céh, az emlék- és
krónikarendszer, a lore-kódex, a komp, a becsületpárbaj, a lélekkovács,
a heti szakmai cél, a tanács és a suttogás. A frakciók, a politika, a
területek és a háború továbbra is játékosi döntésekből épülnek.

### Egyszerűbb, globális AFK

A szerver automatikusan felismerheti az inaktivitást, a játékos pedig kézzel
is átválthat `/afk` paranccsal. Az AFK-állapot megjelenhet a tablistán, és
bizonyos jutalmakból kizárhatja az inaktív játékost.

**Tudatos határ:** nincs jutalmazó AFK-zóna, zónaidő, kifizetés vagy
AFK-bossbar. Ez nem a július 12-i szerverről eltűnő funkció, hanem egy
fejlesztés közben elvetett irány.

### Ülés — pontosan annyi, amennyi kell

A natív sit-only rendszer támogatott lépcsőn, slabon, szőnyegen és hórétegen
enged leülni, ha a világ- és biztonsági policy ezt megengedi. Nem cél a teljes
GSit-funkciókészlet: nincs lay, crawl, stacking, játékos- vagy NPC-megülés.

### Natív crate-ek

A játékos vásárolhat kulcsot, információt és preview-t kérhet, majd fizikai
crate-nél animált nyitást indíthat. A release két csomagolt definíciót
bizonyít: `koznapi` és `ritka`. Éles megnyitás előtt a helyeket, rewardokat,
inventory-overflow-t, settlementet és recoveryt kötelező stagingen tesztelni.

### Privát üzenetek és report

Új PM/reply útvonalak és játékosreportok érkeztek. A report indoka legalább
három szó. A chatnapló, a SocialSpy és a reportkezelés hozzáférését az
adatkezelési szabályzathoz és a staffszerepekhez kell igazítani.

## Amit a staff kap

### Natív moderáció

A release warningot, kicket, állandó és ideiglenes mute-ot/tiltást,
visszavonást, historyt, aktív punishment-listát, reportkezelést, PM-et,
SocialSpy-t, vanish-t, moderációs GUI-t és offline teleportot ad.

Az inventory admin online main inventoryt és ender chestet tud külön
**read** vagy **edit** módban megnyitni. Az edit útvonal escrow- és reconnect
recoveryt használ; emiatt az edit permission jóval érzékenyebb, mint a read.

### Natív MOTD és megjelenítés

A server-list MOTD idő- vagy véletlen választással, eseményprioritással,
vanished játékosok kiszűrésével és több ikonmóddal működhet. A natív HUD és
tablista az IceSMP-hez szükséges részhalmazt adja, nem általános TAB-klón.

### Biztonságosabb mentés és recovery

Központi store-koordináció, korrupciójelzés, tranzakciós naplók, forgó
auditlogok és kontrollált leállítási útvonalak kerültek be. Ezek kód- és
regressziós bizonyítékot adnak; a lemezhibát, félbeszakított folyamatot és
reconnectet valódi fault-injection teszttel is ellenőrizni kell.

## Amit a builder- és eventes csapat kap

- fizikai crate-helyek és világkötés;
- támogatott ülőhelygeometria és biztonsági policy;
- event spawnpontok és világesemény-kapcsolatok;
- dungeon gate, chest, boss és lootkötések;
- rejtett helyek, régészeti pontok, rontás- és kultista helyszínek;
- claim trust GUI és kibővített territory/dungeon útvonalak;
- NPC-kötések, kompok, teleportpontok és questhelyszínek;
- új item modellek, rúnák, blueprint- és receptkapcsolatok.

Az új source önmagában nem építi át a világot. Világcsere, átnevezés,
WorldEdit-művelet vagy resource-pack frissítés után a kapcsolódó pontokat újra
végig kell járni.

## Tudatosan elvetett vagy szűkített irányok

| Irány | Végleges döntés |
|---|---|
| Jutalmazó AFK-zóna | Elvetett fejlesztési terv; a globális AFK marad |
| AFK-zóna payout, idő és bossbar | Nincs a release-ben |
| Lay és crawl | Nem része a natív ülésnek |
| Player/NPC sitting és stacking | Nem része a natív ülésnek |
| Teljes TAB-klón | Nem cél; csak az IceSMP-hez szükséges subset |
| Teljes GSit-klón | Nem cél; sit-only |
| Offline inventory edit | Nem bizonyított natív képesség |

A július 12-i build 30 root parancsa közül **egy sem szűnt meg**. A fenti
tételek nem élő funkcióvesztések, hanem későbbi tervek tudatos határai.

## Külső pluginok rövid státusza

<!-- icesmp-doc-id: release.external-plugin-status -->

> **Productionből még semmit nem szabad pusztán a zöld CI alapján
> eltávolítani.** A natív megfelelőket pluginonként külön, productionközeli
> stagingen kell átvenni. A teljes tesztcsomag az
> [admin kézikönyvben](ADMIN_GUIDE.md#release-acceptance-checklist) található.

| Plugin | Dokumentált döntés | Mi kell még? |
|---|---|---|
| AxAFKZone | Nem kerül deploymentbe; a jutalmazó zónascope elvetve | Globális AFK pozitív és zónajutalom negatív teszt |
| AxAPI | AFK miatt nem szükséges | Ellenőrizni kell, függ-e tőle más élő plugin |
| GSit | Natív sit-only kiváltási jelölt | Teljes blokkgeometria- és lifecycle-átvétel |
| CrazyCrates | Natív crate kiváltási jelölt | Teljes runtime, settlement és fault-injection csomag |
| SModeration | Natív moderáció kiváltási jelölt | Permission, persistence, expiry, reconnect és audit |
| InvSee++ | Az online read/edit scope kiváltási jelöltje | Escrow/recovery és az élő offline igény külön vizsgálata |
| MiniMOTD | Natív MOTD kiváltási jelölt | Párhuzamos ping, ikon, reload, proxy/server-list próba |
| TAB | Csak akkor váltható ki, ha elég a natív subset | Élő TAB-config és ütközések leltára |
| ICEsmpadditions | Warden-XP natív megfelelője jelen van | Drop-tartomány és dupla felülírás teszt |
| FarmProtect | Player- és mob-trample natív megfelelője jelen van | Mindkét eseményút és védelmi kompatibilitás teszt |

### Továbbra is szükséges integrációk

| Plugin | Miért marad? |
|---|---|
| PlaceholderAPI | Ha más plugin `%icesmp_…%` placeholdereket fogyaszt |
| FancyNpcs | NPC-kötött quest, shop, bank, exchange és dialógus miatt |
| WorldGuard | Ha az élő világ régió- és területpolicyja erre épül |
| LuckPerms | Permission backendként, illetve chat metadata miatt |
| LibsDisguises | A kiterjesztett druida-vizuálokhoz és a `/kem` disguise útvonalához |

## Mi vár még stagingtesztre?

1. **Moderáció:** restart és expiry, korrupt state, lemezhiba, PM
   quit–reconnect, SocialSpy, vanish, report, inventory read/edit,
   escrow/recovery, permissionmátrix és offline teleport.
2. **MOTD:** párhuzamos ping, TIME/RANDOM, eseményprioritás, vanished count,
   ikonmódok, hibás PNG, symlink, gyors reload és scheduler rejection.
3. **Sit-only:** minden támogatott blokk, seat position, foglalás, damage,
   sneak, break, teleport, world change, quit/kick/dismount és seat sweep.
4. **Crate:** mindkét kéz, dupla kattintás, több key stack, részleges
   mass-open, full inventory, minden rewardtípus, külső command/currency
   hiba, világ- és definíciócsere, settlement, restart és manuális review.
5. **Mini-rendszerek:** Warden XP, játékos- és mob-crop-trample.
6. **Világbejárás:** crate-ek, NPC-k, dungeonök, eventpontok, kompok,
   teleportok, questek, claim/territory és resource-pack item modellek.

## Élesítés rövid sorrendje

1. Archiváld az élő JAR-t, pluginlistát, configot és state-et.
2. Diffeld az élő override-ot az új configgal; ne a bundled defaultból
   következtess a production állapotra.
3. Készíts explicit játékos/helper/moderátor/admin permissionmátrixot.
4. Ellenőrizd a világ- és resource-pack kötéseket.
5. Futtasd végig az
   [acceptance checklistet](ADMIN_GUIDE.md#release-acceptance-checklist).
6. Stagingen egyszerre csak egy kiváltandó külső plugint kapcsold ki.
7. Productionből csak mentés, bizonyíték és rollbackterv mellett távolíts el
   JAR-t.

## Bizonyíték és ismert határok

| Mező | Érték |
|---|---|
| Baseline JAR | `IceSMP-1.0-TESTING.jar` |
| SHA-256 | `da039f0e2bdf0e67b216ce82d7d3fe3b6da0af6e18f6fa175762c37493795a05` |
| Valószínű forrás | `775d9e247be675db1c7c9beaaecf4a90349bfcd3` — 2026-07-12 |
| Mapping | `HIGH_CONFIDENCE`, nem `EXACT` |
| Dokumentált release | `4643ab53586f0c1ee7352df16dcd477013e6fad4` |
| Auditdátum | 2026-07-30 |

A mappinget 33/33 egyező csomagolt statikus resource, az előállított
`paper-plugin.yml`, három jellegzetes bytecode-marker és a július 14-i
következő változás hiánya támasztja alá. Exact mapping azért nincs, mert a
JAR nem tartalmaz Git SHA-t vagy megbízható build-időt.

Az élő config, permissionkiosztás, világállapot és teljes pluginlista nincs
a JAR-ban. Emiatt több rendszerről csak képességszintű következtetés adható.
A teljes 68 root parancs, 286 route, 79 root alias, 93 routing alias,
44 permission, 13 550 configútvonal és 545 production komponens gépi
referenciáját a `Repository Docs Inventory` workflow artifactja tartalmazza.
