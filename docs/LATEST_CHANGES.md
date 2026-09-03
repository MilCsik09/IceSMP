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

- **Kaszt-kifizetés visszajelzés:** a kaszt-magok eddig is emelték a képességek
  erejét, de a játékos ebből semmit nem látott. Mostantól a megerősített cast
  kiírja a kapott százalékot, az Íjász Szélolvasása pedig a találat
  pillanatában jelez; kombó-lánc befejezőnél a kiírt érték a két bónusz összege.
- **PlayerProfile-alap:** minden tartós játékos-állapot (kaszt, szakma, quest,
  pénztárca, statisztika, moderációs összegzés, crate-számlálók, heti céh-cél,
  halál-escrow) egyetlen, tranzakcióvédett profilrendszerben él — restart és
  crash ellen journalozott, gépi kapuval őrzött szerkezetben.
- **Tablist és színek:** LuckPerms-rang szerinti rendezés, AFK játékosok a
  lista végén; a Menedék-polgár neve zöld (Smaragdkő-szín), így nem
  téveszthető össze a Kitaszítottal — a saját tablista, nametag, chat és a
  csak olvasó `%icesmp_faction_color%` kimenet egységesen.
- **First-party adaptív IceSMP HUD:** öt külön frakciógrafika (külön Menedék-vendég
  erődkerettel), 64×64-es class/utility/rúna/pénznem ikonok, class/spec/szint/event parity,
  minden class strukturált mechanikaállapota, legfeljebb öt generic metric, automatikus
  charge-pipek, Wizard háromcsatornás ráhangolódása és DK slotonkénti rúnaregeneráció.
  Mind a négy frakcióvaluta állandó helyen, nulla egyenleggel is megjelenik;
  packhiánynál játékosonkénti natív fallback marad; a kijelzés teljesen first-party, külső HUD plugin nélkül.
  A panelglyphök a kliens 256×256-os font-atlasz korlátján belül maradnak, a jobb oldali
  horgony clip-space alapú, a teljes kompozíció fizikai méretét pedig a shader a kliens
  `ScreenSize` értékéből normalizálja. A magyar feliratok négyszeresen túlmintavételezett,
  élsimított Inter SemiBold forrásból készülnek; ablak- és GUI-scale váltás nem mozdíthatja vagy
  többszörös méretűre nagyíthatja a HUD-ot.
- **Moduláris Player Frame és HP-scaling előkészítés:** a normál vanilla szív-, páncél-, étel- és
  oxigénsávot pack-readiness után egy bal felső, frakciószínű Player Frame váltja. A név, frame,
  HP current/max, százalék, absorption, páncél, étel és feltételes oxigén külön editor-elem;
  a páncél maximum és százalékos sáv nélküli flat érték. A gyors, Folia-safe tick
  külön fut a class/sidebar snapshotfrissítéstől. A
  class-health gate továbbra is kikapcsolt, de a normalizálás már tiltott, így későbbi staging
  aktiváláskor a HUD a valódi skálázott HP-t fogja mutatni. Hardcore-heart asset nincs felülírva.
- **Target/Party Frame és tisztább class panel:** a tartós class XP-sáv kikerült, az eseménylábléc
  teljes szélességben legfeljebb három aktív eseményt mutat. A DK-rúnák saját editor-kategóriát
  kaptak. Találat után screen-space Target Frame jelenik meg: a mob saját bestiárium-, a játékos
  frakciófüggő grafikát, current/max HP-t, százalékot, szintet/státuszt és játékos resource-t kap.
  A rendszer nem hoz létre követő vitals-TextDisplayt, így az eredeti mobnév érintetlen marad.
  A Player Frame alatt négy WoW-stílusú, tagonként frakciószínű Party Frame mutat HP-t,
  resource-t és online/távol/halott/vezető állapotot.
- **Staff-eszközök:** automatikus single-writer `/invsee`, húzható
  adományláda-input, staged config-GUI (mentés/elvetés tranzakcióval).
- **Társ-rendszer:** a Profile v2-ben tárolt társlista most a `/pet` GUI-ból és
  `/pet select <hely>` paranccsal ténylegesen váltható; nem aktív társ is célzottan
  elengedhető. Az aszinkron GUI csak a tartós művelet után frissül, az idézés
  biztonságos, betöltött Folia-lokális állóhelyet keres, a pet saját ölése is ad
  companion XP-t, a halál cooldownja pedig a durable commit alatt is fail-closed.
- **Teljes class-integritás:** mind a 35 specializáció hét használható
  aktív képességgel, hat ténylegesen bekötött doctrine-nal és mechanikailag
  fogyasztott szint-50-es capstone-nal rendelkezik. Az új 35
  specializációs csúcspróba csak az adott spec sikeres kasztolásait számolja.
  A Druida, Pap, Sárkányidéző, Sámán, Varázsló és Halállovag hiányzó
  producer→consumer ciklusai elkészültek; a durable démon/élőholt idézések
  többé nem hoznak létre párhuzamos ideiglenes másolatot.
- **Világesemények:** immerzív, Folia-biztos spawn-elhelyezés (távolság,
  víz- és partpuffer, nézési kúp), meteor-kráter terrain-visszaállítási
  journallal. A kereső jelöltjei most chunk-középre kerülnek, a 7 blokkos
  effektív footprint/partpuffer egy régión belül marad, ezért a világboss,
  invázió, meteor és escort keresése nem égeti el idő előtt a chunk-budgetet.
  Az escort route és az inváziós mellékmobok egyoszlopos belső profilt
  használnak; az admin parancs aszinkron keresést, nem kész spawnt jelent.
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
  A review-körök keményítései: a világ-relic állapot (ownership, lost/reclaim,
  Awakening, művelet-receiptek) immutable pillanatképként publikált, single-writer
  perzisztencia-határ mögött — a candidate csak sikeres lemez-commit után válik
  láthatóvá (hamis ARMED siker és félig-töltött reload-állapot nincs); a relic
  kézbesítés/transfer durable receipt-alapú recovery-protokollt kapott (crash
  után determinisztikus, duplikátum-mentes helyreállítás, a tárgy-PDC és a
  világ-tulajdonos nem csúszhat szét tartósan); a lost-jelölés owner-kötött
  (stale példány gazdája nem jelölheti el más élő relicét, árva lost nincs); a
  `canUse` fail-closed (központi tulajdonos nélkül a példány nem működik); a
  Resonance-szerződés tipizált jelzéseket hordoz (actor, cél-identitás,
  mennyiség — kill/block/forma/mozgás/low-health payloadokkal) és a hook
  régió-helyes actor-kontextust kap; a fizikai birtoklás régió-szálas
  pillanatkép fail-closed TTL-lel és azonnali invalidációval; a katalógus csak
  létező generikus relicre köthet, kikapcsolt rendszer mellett is validálva;
  strict config-schema; a `relics.enabled` explicit framework-kapu.
- **Kódex-bővítés:** mind a 7 ereklye, a 10 világboss-archetípus, a
  kazamata-őrzők és mind a 35 specializáció-iskola kánon-bejegyzést kapott;
  a consistency-kapu gépileg őrzi, hogy nevesített tartalom ne élhessen
  kódex-horgony nélkül.
- **Quest Framework v2 (MMO-rework):** minden küldetésnek explicit forrása
  van (NPC / Megbízások-tábla / lánc / helyszín / tárgy / esemény), és a
  felvétel + leadás kizárólag a jogosult forrásnál történhet — a régi nyílt
  `/quest accept`, a napló távoli felvétele, a `/quest talk`
  NPC-megszemélyesítés és a bind-alapú átadás bypass-ai megszűntek (a
  parancsok admin-eszközzé váltak). NPC-questnél a feladatok teljesítése után
  a küldetés KÉSZ állapotba lép és az adó NPC-nél adható le (záró dialógus +
  jutalom ott); a napló öt füles (Aktív/Kész/Megbízások/Elérhető/Teljesített),
  küldetés-követéssel; az NPC-k több questnél kattintható, egyszer
  használatos tokenes listát kínálnak, a marker-színek központi palettából
  jönnek (arany=leadható, sárga=új, kék=napi/heti, lila=kaszt, türkiz=titok).
  Kategória- és láthatóság-rendszer (a rejtett quest felfedezésig sehol nem
  szivárog), tartós felfedezés- és forrás-audit a PlayerProfile-ban, atomikus
  és teljes gráf-validációval kapuzott quest-reload (hibás config a korábbi
  definíciókat hagyja élőben), mind a 195 csomagolt küldetés migrálva
  (160 világ- és történeti küldetés + 35 specializációs csúcspróba).

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
| Szakmai recept | 124 | 376 |
| Szakmai alapanyag | 9 | 81 |
| Relikvia | 5 | 6 |
| Rituálé | 19 | 21 |
| Natív crate | 0 | 8 |

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
vagy a szezonális váltási limit körül.

A periodikus frakcióadó, az adóütemező és a királyi adókulcs-parancs megszűnt.
A régi debt/outbox és protokollmezők csak kompatibilitási maradványok. A fizetős
frakcióváltás WAL-védelme változatlan: a live tagság csak a tartós
assignment+history snapshot sikeres mentése után változik, wallet-hiba esetén
pedig a művelet fail-closed marad.

A passzívok teljes immunitások helyett kontextusos, konfigurálható policyt
használnak:

- RED erős környezeti hővédelmet tart meg, de a FIRE/FIRE_TICK/HOT_FLOOR sebzés
  negyedét, a LAVA sebzés felét, az entitás okozta tűz háromnegyedét kapja; az
  IceSMP `TUZ` varázslat alapból teljes sebzést okoz;
- BLUE továbbra sem kap fagyássebzést, fele fulladássebzést kap, és csak a
  felsorolt természetes exhaustion események negyedét kerüli el — Hunger és
  scripted éhség nem tűnik el; periodikus ételkötelezettség nincs;
- az explicit NEUTRAL polgár fele zuhanássebzést kap, és csak a spontán
  békés/semleges mob- vagy Enderman-szemkontaktus-aggrót szűri; provokáció és
  scriptelt/event célzás működik;
- DARK fele Wither-sebzést és felezett Wither-időt kap. Thanaopolis markerelt
  ambient lakói békések, de támadás után 60 másodperces, játékos–mob páronkénti
  megtorlás indul; a 16 blokkos riadó csak a ténylegesen riasztott példányokra
  nyit külön lease-t. A vad undead előny csak éjjel, 50% eséllyel él. Vérhold
  alatt az ambient és a vad DARK béke is alapból megszűnik. Ellensúlyként a
  normál gyógyítás 70%-os, a polgári bolt és játékos-karaván tiltott, a kompár
  kétszeres; Vérhold/DUNGEON gyógyítása és a kijelölt feketepiac kivétel.

Boss-, dungeon-, rontás-, invázió-, event-, quest- és koronaátok-célzás
megelőzi a truce-ot. A target adapter a szűrt célpontot ténylegesen `null`-ra
állítja, nem hagyja bent egy cancel miatt. A signature-food buff fogyasztáskor
az aktuális explicit tagságot ellenőrzi, a régi itemstackből pedig eltávolítja
a korábban beégetett feltétel nélküli potion effectet. Az összetartozó merged
config és override-lista egyetlen immutable generációként frissül.
Az Infamy, Wanted, Exile, DARK-eskü és tagság külön tengely. A Suttogó kockázata
nem láthatatlan pont: a pontos tanú–cél bizonyíték három érvényes váddal fixen
`CLEAN → OBSERVED → SUSPECTED → EXPOSED` állapotot jár be, decay nélkül.
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
crate-nél világban futó ItemDisplay-revealt indíthat. Inventory-rulett nem
nyílik; a jutalom side effectje csak a reveal lezárása után indul. A release
nyolc, permission nélküli definíciót bizonyít: `koznapi`, `ritka`, `hosi`,
`mitikus`, `mesterseg`, `expedicio`, `hadizsakmany` és `arkanum`.
Ezek az alsó nyolc fizikai állomást töltik ki; a felső nyolc hely különleges
ládáknak marad. A lapos vanilla táblák helyét tematikus unique alapanyagok,
valódi craft- és affix-láncon át készülő tárgyak, valamint szint/szakma szerint
szűrt random tervrajzok vették át. A Mitikus láda külön boss-only
tervrajz-poolt kaphat; Elytra minden crate item-, recept- és tervrajzútvonalon
tiltott. A browser és a világ-reveal a jutalom valódi itemmodelljét mutatja.
Éles megnyitás előtt a helyeket, rewardokat,
inventory-overflow-t, settlementet és recoveryt kötelező stagingen tesztelni.

### Privát üzenetek és report

Új PM/reply útvonalak és játékosreportok érkeztek. A report indoka legalább
három szó. A chatnapló, a SocialSpy és a reportkezelés hozzáférését az
adatkezelési szabályzathoz és a staffszerepekhez kell igazítani.

### A szakmák végre azt adják, amit ígérnek

A receptkatalógus átesett egy teljes átvizsgáláson: 437-ről 295-re csökkent,
majd a szakma-identitás pótlásával **376 receptre** állt be. Minden recept
indokolja a létezését.

- **Az alkimista főzetei hatnak.** Korábban mind a 16 főzet üres palack volt: a
  neve gyógyítást vagy erőt ígért, de semmit nem csinált. Most valódi hatásuk
  van, és a dobható, illetve elnyúló változatokat a vanília terület- és
  időtartam-kezelése működteti.
- **A bűvölő tomusai átadják a bűbájt.** A 13 enchantkönyv üresen került ki a
  receptkönyvből; üllőn nem adtak semmit. Most mindegyik a nevében ígért bűbájt
  viszi.
- **Eltűnt 15 nyersanyag-hurok.** Több receptpár körbe volt kötve úgy, hogy a
  kör végén több nyersanyag jött ki, mint amennyi bement — rúnapor és glowstone,
  sötéttölgy, lazurit, sőt vasból arany. Ezek korlátlanul ismételhető
  pénznyomdák voltak.
- **A tervrajz egyszer fogy el.** Korábban a lap gyors áthelyezésével a recept
  elmentődött, de a tervrajz megmaradt — minden boss-only tervrajz újra és újra
  eladható volt.
- **Csak azt craftolod, amit gyakorolsz.** Szakmaváltás után a régi szakma
  receptjei zárva vannak, még nyitva maradt receptkönyvből is.
- **Ami a műhelyasztallal egyenértékű, az meg van jelölve.** Az ilyen recept
  „gyakorló receptként" jelenik meg: azért van, hogy a szakma elején legyen
  miből XP-t szerezni, nem azért, hogy nyerj rajta. Csak alacsony szinten van, és
  sosem kér egyedi alapanyagot.
- A recept-craft XP-je mostantól a **heti céh-célt is tölti**, és a tömeges munka
  (shift-craft, kemencéből egyszerre kivett adag) darabonként számít.

- **Minden szakma kapott saját terméket.** A takarítás után látszott, hogy a
  gyógynövényesnek 27 receptből egyetlen egyedi tárgya volt — festéket és cukrot
  gyártott. Most övé a **kenőcs- és teavonal**: hosszú hatású, harcon kívüli
  támogatás, és a **Méregvonó Pép**, az egyetlen hordozható ellenszer, ami minden
  aktív hatást levesz (a jókat is — nem mindig éri meg). Az alkimista marad a
  harci, azonnali főzeteké; a kettő szándékosan egymás ellenpárja. A bányász saját
  ásó- és szerencsecsákány-vonalat kapott, a favágó egy erdőjáró bőrszettet.
- Ezzel a katalógus 295-ről **376 receptre** nőtt, és nincs olyan szakma, ahol
  ötnél több szint telne el új recept nélkül. A bűvölő 21 új tomust kapott a
  korábban lefedetlen vanília bűbájokra, az alkimista dobható és elnyúló
  változatokat, a halász pedig négy szigonyt és a búvárfelszerelést.

Ami eltűnt, az nagyrészt a műhelyasztal átnevezett másolata volt, vagy olyan
tárgy, amit vanília úton csak lootból lehet szerezni — a Tenger Szíve, a
nautilusz-héj, a szivacs és a totem gyárthatósága a ritkaságukat szüntette meg.

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
vanished játékosok kiszűrésével és több ikonmóddal működhet. A HUD és a saját
tablista az IceSMP-hez szükséges megjelenítési funkciókat adja.

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
| Általános külső tablista-motor | Nem cél; az IceSMP saját tablistája a szükséges felületeket kezeli |
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

## Season 0 / Prologue — Kárhozat Kapuja

A release-jelölt tartalmazza a Season 0 egyszeri **Prologue** életciklust és
Olethropyla, a már létező ősi Kárhozat Kapuja köré épülő átmenetet. A rendszer
külön tartós világállapotot, alapból 25-ös Season 0 class-szintplafont,
specializáció-/relic-/blueprint-/high-tier loot kapukat, Nether travel policyt,
Gate Breach és finale encountert, participant scalinget, rehearsal módot,
Profile v2 Founder/finale participationt, rendkívüli Krónikát, emlékművet és
Season 1 átmenetet kezel.

A `DORMANT` most teljes pass-through állapot: a Prologue nem rak XP-,
specializáció-, relic-, blueprint-, rarity- vagy eseménykorlátot a szerverre,
nem tiltja a normál season/community lifecycle-t, nem alkalmaz Nether-pecsétet
vagy gate-location authorityt, nem futtat HUD/ambient/breach hatást, és nem ad
idő előtti post-Prologue catch-upot. Az általános, Prologue-tól független
szerverpolicyk továbbra is a saját konfigurációjuk szerint működnek.

A completion hardeningben a Prologue encounter cleanupból kikerült a globális
`Bukkit.getEntity(UUID)` lookup; az event entityk a közös transient-entity
scheduler-handle életcikluson takarítódnak. A production `finale pause` már
nemcsak az orchestrator tickjét állítja meg: az aktív encounter AI-ja, combatja,
pending spawnjai, boss mechanikái és timeoutja is szünetel. A pause idő nem
fogyasztja a timeout-budgetet, a pauseolt phase és a hátralévő encounter-idő
restart után is megmarad, és resume ugyanabból a checkpointból építkezik.

A finale boss halála azonnali in-memory spawn latch-et állít, mielőtt az
encounter újra spawnolhatónak számítana; a tartós boss-victory állapot a
`finaleId`-hoz kötött és idempotens. Persistence hiba esetén a finale fail-closed:
nincs második boss, Gate activation vagy Season 1 továbblépés, amíg a tartós
állapot nem rendezhető. Az irreverzibilis lánc sorrendje továbbra is
**boss victory → Gate unlock → reward plan/Profile v2 reward → Chronicle →
monument → Season 1 prepare/activate**, one-shot receiptekkel védve.

A világépítői bekötéshez négy konfigurált runtime hook tartozik:
`prologue-gate`, `prologue-gathering`, `prologue-breach`, `prologue-boss`.
A repository szándékosan nem talál ki ezekhez koordinátát; a végleges staging
világon kell őket biztonságos helyre kötni és bejárni. A productionközeli Folia
pause/restart/finale és world-hook acceptance ettől továbbra is kézi staging
kapu, nem CI-állítás.
