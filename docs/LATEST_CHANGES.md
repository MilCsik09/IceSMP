# Mi változott a július 12-i szerververzió óta?

<!-- icesmp-doc-id: release.deployed-build-to-release -->

> *A világ ugyanaz — de sokkal több módon válaszol arra, amit benne tesztek.*

A következő IceSMP-build jóval több egy kisebb javításnál. Saját AFK-, ülés-,
crate-, MOTD- és moderációs rendszerek érkeztek, miközben a questek, szakmák,
receptek, specializációk és világban zajló események köre is jelentősen bővült.

A kód és a CI elkészült állapota még nem egyenlő az élesítéssel. Ahol kézi,
világbejárásos vagy hibaszimulációs próba hiányzik, azt ezen az oldalon külön
jelezzük.

A 13 kaszt / 35 specializáció teljes reworkjének 1.21.11-es kompatibilitási
alapja külön, alapból kikapcsolt rollout-kapu mögé került. Ez még nem játékosnak
kiadható rework: verziózárt dependency-manifestet, fail-fast preflightot,
Folia-/26.2-portolási határokat és a későbbi adapterek stabil szerződéseit adja.

> **Összehasonlítási alap:** az üzemeltető által futóként átadott
> `IceSMP-1.0-TESTING.jar`. A tartalma nagy bizonyossággal a
> **2026. július 12-i** forrásállapotnak felel meg; július 13-án nem volt
> köztes mainline commit. A JAR nem tartalmaz Git SHA-t, ezért ez
> `HIGH_CONFIDENCE`, nem `EXACT` azonosítás.

## Augusztus eleji integrációs hullám (staging előtt)

A júliusi tartalom fölé egy nagy technikai és felületi hullám érkezett, egyben:

- **PlayerProfile-alap:** minden tartós játékos-állapot (kaszt, szakma, quest,
  pénztárca, statisztika, moderációs összegzés, crate-számlálók, heti céh-cél,
  halál-escrow) egyetlen, tranzakcióvédett profilrendszerben él — restart és
  crash ellen journalozott, gépi kapuval őrzött szerkezetben.
- **Tablist és színek:** LuckPerms-rang szerinti rendezés, AFK játékosok a
  lista végén; a Menedék-polgár neve zöld (Smaragdkő-szín), így nem
  téveszthető össze a Kitaszítottal — tab, nametag, chat és külső TAB
  (`%icesmp_faction_color%`) egységesen.
- **Staff-eszközök:** automatikus single-writer `/invsee`, húzható
  adományláda-input, staged config-GUI (mentés/elvetés tranzakcióval).
- **Világesemények:** immerzív, Folia-biztos spawn-elhelyezés (távolság,
  víz- és partpuffer, nézési kúp), meteor-kráter terrain-visszaállítási
  journallal.
- **Claimek:** fail-closed betöltés + a poligon-kijelölés csúcspont-limitje
  alapból megszűnt (a területkorlát maradt a valódi kapu).
- **Konzol:** a boot-kori leltár-sorok elnémultak; hibakereséshez a
  `logging.verbose-startup` kulccsal visszakapcsolhatók. A szintezett
  (custom-nevű) mobok vanilla „Named entity … died" halál-sorát a plugin
  szűrője alapból eldobja — se terminál, se `latest.log`
  (`logging.suppress-named-entity-deaths`, élő kulcs).
- **Lifecycle- és API-hardening:** a PlayerProfile erőforrás-teardown részleges
  indulás és sikertelen leállítási drain után is garantált (nem marad hátra
  executor, HTTP listener vagy service-regisztráció); az alapból kikapcsolt
  read-only HTTP API név-feloldása auth-first — anonim hívás egyetlen
  tárolóolvasást sem indíthat el.
- **Bestiárium-mélység:** a lajstrom négy kategóriája kattintva lapozható
  (ismeretlen bejegyzés = „???"), teljesítmény-%-kal és nevezőkkel; a
  szörnyeknél fajonkénti elejtés-számláló, első-elejtés dátum és kill-alapú
  tudás-fokozatok; a világbossok archetípusonként kerülnek a lajstromba;
  `%icesmp_bestiary_*%` placeholderek külső kijelzéshez.
- **Class Relic Framework (pilot):** kaszthoz kötött, világ-egyedi relikviák
  külön domainrétege — Class Power / Spec Resonance / Awakening, Profile v2
  class/spec authorityvel, ownership↔fizikai-birtoklás szétválasztással és a
  relickel utazó durable Awakening-cooldownnal. Pilot: a Sárkánytojás-töredék
  Evoker-bónusza az új keretből érkezik (változatlan 10%); a 13/35 teljes
  roster a class rework kapuja mögött (`require-complete-catalog: false`).
  A review-kör keményítései: a világ-relic állapot (ownership, lost/reclaim,
  Awakening) single-writer perzisztencia-határ mögé került (atomikus arm,
  lemez-hiba esetén rollback — hamis ARMED siker nincs); a Resonance-szerződés
  tipizált jelzéseket hordoz (actor, cél-identitás, mennyiség, tagek) és a
  hook régió-helyes actor-kontextust kap; a fizikai birtoklás régió-szálas
  pillanatkép fail-closed TTL-lel és azonnali invalidációval (UUID-only hot
  path Player-dereferencia nélkül); a katalógus csak létező generikus relicre
  köthet; strict config-schema; a `relics.enabled` explicit framework-kapu.
- **Kódex-bővítés:** mind a 7 ereklye, a 10 világboss-archetípus, a
  kazamata-őrzők és mind a 35 specializáció-iskola kánon-bejegyzést kapott;
  a consistency-kapu gépileg őrzi, hogy nevesített tartalom ne élhessen
  kódex-horgony nélkül.

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

### Egyértelmű frakciótagság és új passzív-policy

Az új játékos most egyértelműen a Menedék **vendége**: a fizikai onboardingot
megkapja, de explicit választásig nem számít `NEUTRAL` polgárnak, ezért nem kap
frakciópasszívot, frakcióquestet, tanácsi szavazatot, community-hozzájárulást
vagy frakciós szezonpontot. A kezdőlánc Creutzér-útravalója caldesterai
vendégsegély, nem rejtett frakciójutalom. A korábbi választás tartós nyoma miatt
az assignment törlése sem nyit új „első választás” kerülőutat a szezonvégi zár
vagy a szezonális váltási limit körül, és nem törli a már fennálló adóhátralékot
vagy adócsalási strike-ot sem.

Az adóhátralék most eredet-frakciónként külön ledgerben él. Frakcióváltás nem
konvertálja a régi tartozást vagy strike-ot: a következő beszedés az eredeti
valutából az eredeti kasszába rendezi. A legacy scalar séma egyáltalán nem őriz eredet-frakciót, ezért aktív vagy korábbi
tagságból sem találunk ki hozzá valutát. Minden ilyen adat explicit adminmigrációt
igénylő karanténban marad: nem veszhet el, de a játékos következő frakciójához
sem kötődik automatikusan.

A fizetős frakcióváltás és az adóbeszedés külön write-ahead journalban rögzíti
a wallet és a domain előtte/utána állapotát. A live tagság csak a tartós
assignment+history snapshot sikeres mentése után változik; treasury/debt hiba
esetén a wallet tartós kompenzációt kap, rollbackhiba pedig fail-closed recovery
állapotot hagy.

A passzívok teljes immunitások helyett kontextusos, konfigurálható policyt
használnak:

- RED erős környezeti hővédelmet tart meg, de a FIRE/FIRE_TICK/HOT_FLOOR sebzés
  negyedét, a LAVA sebzés felét, az entitás okozta tűz háromnegyedét kapja; az
  IceSMP `TUZ` varázslat alapból teljes sebzést okoz;
- BLUE továbbra sem kap fagyássebzést, fele fulladássebzést kap, és csak a
  felsorolt természetes exhaustion események negyedét kerüli el — Hunger,
  scripted éhség és food-duty nem tűnik el;
- az explicit NEUTRAL polgár fele zuhanássebzést kap, és csak a spontán
  békés/semleges mob- vagy Enderman-szemkontaktus-aggrót szűri; provokáció és
  scriptelt/event célzás működik;
- DARK fele Wither-sebzést és felezett Wither-időt kap. Thanaopolis markerelt
  ambient lakói békések, de támadás után 60 másodperces, játékos–mob páronkénti
  megtorlás indul; a 16 blokkos riadó csak a ténylegesen riasztott példányokra
  nyit külön lease-t. A vad undead előny csak éjjel, 50% eséllyel él. Vérhold
  alatt az ambient és a vad DARK béke is alapból megszűnik.

Boss-, dungeon-, rontás-, invázió-, event-, quest- és koronaátok-célzás
megelőzi a truce-ot. A target adapter a szűrt célpontot ténylegesen `null`-ra
állítja, nem hagyja bent egy cancel miatt. A signature-food buff fogyasztáskor
az aktuális explicit tagságot ellenőrzi, a régi itemstackből pedig eltávolítja
a korábban beégetett feltétel nélküli potion effectet. Az összetartozó merged
config és override-lista egyetlen immutable generációként frissül.
Az automatizált tesztek a policyt bizonyítják, nem a valódi szerveres AI- és
szezonbalanszt; a stagingmátrix továbbra is nyitott.

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

Az inventory admin a `/invsee <játékos>` egyetlen paranccsal nyitja meg az
online main inventoryt és ender chestet: edit joggal az első megnyitó
automatikusan **write** sessiont kap, minden további egyidejű megnyitó
read-only módot; a MAIN ↔ ENDER váltás a GUI gombjával történik. A write
útvonal escrow- és reconnect recoveryt használ; emiatt az edit permission
jóval érzékenyebb, mint a read.

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
7. **Frakciótagság és passzívok:** vendég–NEUTRAL jogosultságok, a négy
   sebzés/exhaustion policy, provokáció, Enderman, ambient/vad DARK undead,
   Vérhold és harci kivételek, koronaátok, két eltérő frakció ugyanazon mobnál,
   valamint reload–relog–restart–disable lifecycle.
8. **Integrációs hullám:** PlayerProfile-, invsee/adományláda-, world-event
   spawn-védelmi, frakciószín- és runtime hardening kézi próbák — az admin
   kézikönyv „Kiegészítő staging-mátrixok” szakasza szerint.

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
| Dokumentált release | Az aktuális release-jelölt; a pontos commit- és JAR SHA az acceptance-bizonyíték része |
| Baseline-mapping auditdátuma | 2026-07-30 |
| Frakciótagság/passzív forrásaudit | 2026-08-01; automatizált policybizonyíték, staging még nyitott |

A mappinget 33/33 egyező csomagolt statikus resource, az előállított
`paper-plugin.yml`, három jellegzetes bytecode-marker és a július 14-i
következő változás hiánya támasztja alá. Exact mapping azért nincs, mert a
JAR nem tartalmaz Git SHA-t vagy megbízható build-időt.

Az élő config, permissionkiosztás, világállapot és teljes pluginlista nincs
a JAR-ban. Emiatt több rendszerről csak képességszintű következtetés adható.
A teljes 68 root parancs, 286 route, 79 root alias, 93 routing alias,
44 permission, 13 550 configútvonal és 545 production komponens gépi
referenciáját a `Repository Docs Inventory` workflow artifactja tartalmazza.
