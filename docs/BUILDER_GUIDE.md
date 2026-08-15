# IceSMP builder kézikönyv

<!-- icesmp-doc-id: guide.builder-and-world-designer -->

<details>
<summary>Dokumentum-forrásállapot (HEAD, audit dátuma, futó baseline)</summary>

- Dokumentált HEAD: `73508dfa1bb40e6be54ab215bbe02dd0ae003e54`
- Audit dátuma: 2026-07-30
- Deployed baseline: `IceSMP-1.0-TESTING.jar` (`da039f0e2bdf0e67b216ce82d7d3fe3b6da0af6e18f6fa175762c37493795a05`); valószínű forrásállapot: `775d9e247be675db1c7c9beaaecf4a90349bfcd3` (2026-07-12, `HIGH_CONFIDENCE`, nem `EXACT`)

</details>

Egy IceSMP-helyszín akkor kész, amikor nemcsak szép, hanem **él is**. A kapu a megfelelő világba vezet, az NPC felismeri a küldetést, a crate nem nyeli el a jutalmat, és a bossnak marad helye megmozdulni.

Ez a kézikönyv abban segít, hogy az építményből ellenőrzött játéktér legyen. Nem mondja meg, milyen legyen egy város stílusa; azt mutatja meg, milyen azonosító, koordináta, kötés és átadási próba kell ahhoz, hogy a plugin valóban használni tudja.

> A lore és a teaser hangulatot, nevet és irányt ad. A runtime-azonosító viszont mindig a forrás, a config vagy a listázó parancs szerint mérvadó.

Kapcsolódó dokumentumok:

- [teljes parancsreferencia](ADMIN_GUIDE.md#teljes-parancsreferencia);
- [permissionreferencia](ADMIN_GUIDE.md#permissionreferencia);
- [konfigurációs referencia](ADMIN_GUIDE.md#konfiguráció-és-reload);
- [GUI-referencia](ADMIN_GUIDE.md#gui-referencia);
- [adatvezérelt tartalomkatalógus](FEATURES.md);
- [teljes quest-, dialógus- és NPC-elhelyezési leltár](QUESTS.md);
- [külső pluginok státusza](LATEST_CHANGES.md#külső-pluginok-rövid-státusza);
- [release acceptance checklist](ADMIN_GUIDE.md#release-acceptance-checklist).

## 1. A buildermunka alapszabálya

Egy IceSMP-helyszín három külön rétegből állhat:

1. **fizikai építmény** — a blokkok, utak, termek, arénák és díszletek;
2. **runtime-kötés** — például egy crate-koordináta, zóna, NPC-kötés vagy
   event spawnpont;
3. **konfiguráció vagy tartós adat** — az azonosító, világpolicy, jutalom,
   útvonal, cooldown és más működési adat.

Az építmény attól még nem működő tartalom, hogy látványra kész. Ugyanígy
egy konfigurált azonosító sem garantál jó játékélményt, ha a helyszínen
nincs elég hely, biztonságos talaj vagy megfelelő útvonal.

Minden helyszínhez legyen egy rövid átadólap:

- helyszín neve és belső azonosítója;
- világ neve;
- középpont vagy releváns koordináták;
- kapcsolódó parancsok és config pathok;
- szükséges permissionök;
- használt külső plugin vagy resource pack;
- módosítás előtt készített mentés;
- stagingteszt felelőse és bizonyítékának helye.

> **Fontos:** a zöld build és CI azt bizonyítja, hogy a kód összeáll és az
> automatizált ellenőrzések átmennek. Nem bizonyítja, hogy egy konkrét
> világmásolatban jó helyre került a spawn, elfér a boss, felvehetők az
> eldobott jutalmak vagy egy WorldEdit-művelet után minden koordinátakötés
> érvényes maradt.

## 2. Mely rendszerek igényelnek fizikai világhelyszínt?

| Rendszer | Fizikai hely kell? | Hogyan kötődik a világhoz? | Bundled állapot | Fő builderteendő |
|---|---|---|---|---|
| Crate | Igen, ha blokkra kattintható crate kell | Tartós világ UUID + világnév + blokkkoordináta | Nyolc alapdefiníció van; fizikai placement runtime adat | Kösd az alsó szint 8 állomását, majd `/crate set <id>` |
| Sit-only | Nem külön helyszín, de a blokkok geometriája számít | Engedélyezett világok, anyagok és blokkállapot | Alapból engedélyezett | Támogatott ülőfelületekkel és megfelelő fejhellyel építs |
| Territórium/főváros | Igen | Kör- vagy poligonzóna és opcionális magassági sáv | Nincs bundled élő zónapéldány | Határ, típus, frakció és spawn adminbekötése |
| Játékosclaim | Játékos hozza létre | Blokkpontos doboz a világ nevével | Runtime tartós adat | Úgy tervezz telkeket, hogy a claimhatárok ne vágják ketté a közös infrastruktúrát |
| Frakcióspawn | Igen | Admin pontos állóhelye és nézési iránya | Nincs bundled beállított pont | Biztonságos érkezőtér, majd `/territory setspawn <frakció>` |
| Első belépési introspawn | Opcionális | `world,x,y,z[,yaw,pitch]` configérték | Bundled érték üres | Biztonságos kezdőpont és kamera/intro playtest |
| Event spawnpont | Opcionális, de fix arénához szükséges | Tartós világnév + egész blokkkoordináta | Nincs bundled pont | Pont létrehozása és az anchor mód beállítása |
| Világboss-aréna | Fix anchor módban igen | Event spawnpont + world-event config | Nincs név szerinti bundled aréna | Szabad spawnmag, harctér, menekülési és nézői zóna |
| Kereskedő-karaván | Opcionális | Configolt stoplista vagy event anchor; ezek hiányában játékosközeli fallback | Stoplista üres | Akadálymentes udvar és kereskedő-interakció teszt |
| Komp | Igen, ha használni akarjátok | Két configolt végpont | Nincs bundled útvonal | Mindkét part, biztonságos érkezés, közeli beszállási pont |
| Parkour | Igen | Tartós start- és célpont | Nincs bundled pálya | Start, cél, sugár, kizuhanás és jutalom teszt |
| Quest: területlátogatás | Igen | Quest által hivatkozott territory ID | Három terület-ID-hivatkozás van | A zóna-ID-t pontosan egyeztesd a questtel |
| Quest: NPC-beszélgetés/átadás | Igen, ha FancyNpcs útvonalat használtok | NPC belső neve és opcionális tartós `/npcbind` | Nincs bundled élő binding | NPC-hely, belső név, kattintási hozzáférés és fallback teszt |
| Quest: parkour | Igen | Quest által hivatkozott pálya-ID | Egy pálya-ID-hivatkozás van | A persistent pálya ID-je egyezzen |
| Dungeon | Igen | `DUNGEON` zóna + opcionális láda- és bosspontok | Nincs bundled zónapéldány | Belépés, loot, boss, reset és védelmi határ teszt |
| Dungeon lootláda | Igen | Nézett chest/trapped chest/barrel koordinátája | Runtime tartós adat | `/territory dungeonchest [tábla]`, majd játékosonkénti loot teszt |
| Dungeon miniboss | Igen | DUNGEON zóna-ID + admin állóhelye | Két configolt miniboss-profil | `/territory dungeonboss <zóna-id> [tábla]` |
| Rituálé-oltár | Igen | Pontos, magblokkhoz viszonyított multiblock minta | 21 rítusdefiníció | A config minden offsetjét pontosan építsd meg és aktiváld |
| Rejtett hely | Igen, ha engedélyezitek | Configolt hely, sugár és jutalom | A bundled `spots` map üres | Helyszín, felfedezési sugár és pickup-biztonság |
| Szezon-emlékmű | Opcionális | Egy configolt hely | Bundled location üres | Tartósan szabad terület a bannernek és hologramnak |
| Városi őrjárat | Opcionális | Világ + legalább két waypoint | Bundled guard map üres | Járható útvonal, talaj- és szűkületteszt |
| Árfolyamtábla | Igen | Admin aktuális állóhelye | Runtime elhelyezés | Látható, nem torlaszoló hely és eltávolítási hozzáférés |
| Profession craft | Nem kötelező | `/profession recipes` GUI közvetlenül a játékos inventoryjából craftol | 310 receptdefiníció | A műhely csak tematikus; ne állítsd technikai követelménynek |
| Resource-packes tárgymegjelenés | Nem helykoordináta, de vizuális előfeltétel | Namespaced `item-model` kulcsok | Számos aktív modellhivatkozás | A packben legyen megfelelő modell, és teszteld pack nélkül is |
| Globális AFK | Nem | Aktivitás és globális állapot | Nincs builderkötés | Ne építs jutalmazó AFK-zónát; ilyen deployment scope nincs |

## 3. Általános világmódosítási folyamat

### 3.1. Új helyszín

1. Válaszd ki az autoritatív belső ID-t, vagy hozz létre új, ékezet nélküli,
   stabil ID-t.
2. Ellenőrizd, hivatkozik-e rá quest, recipe, NPC-kötés vagy más tartós
   rekord.
3. Építsd meg a helyszínt szerveroldali builder bypass joggal, de normál
   játékosjoggal is járd be.
4. Kösd be a helyet a megfelelő paranccsal vagy konfigurációval.
5. Listázó vagy státuszparanccsal olvasd vissza a mentett állapotot.
6. Futtass legalább egy sikeres és egy hibás játékosutat.
7. Kontrollált restart után ismételd meg a működési próbát.
8. Mentsd el a koordinátát, ID-t és a bizonyítékot az átadólapon.

### 3.2. Meglévő hely áthelyezése

Az „épület átmásolása” és a „runtime-hely áthelyezése” nem ugyanaz.
WorldEdit, FAWE vagy világszerkesztő nem írja át automatikusan az IceSMP
tartós koordinátáit.

Biztonságos sorrend:

1. állítsd le az érintett hely játékosforgalmát;
2. listázd és jegyezd fel az összes régi kötést;
3. készíts mentést a világról és a plugin adatkönyvtáráról;
4. távolítsd el vagy irányítsd át a régi runtime-kötéseket;
5. másold vagy építsd át a fizikai helyet;
6. hozd létre az új kötéseket;
7. ellenőrizd, hogy a régi koordinátán már nincs rejtett aktív útvonal;
8. reload/restart után normál játékosként teszteld.

### 3.3. Világ átnevezése vagy cseréje

A különböző rendszerek nem azonos módon tárolják a világazonosságot.

| Kötésfajta | Világazonosítás | Átnevezés/csere kockázata |
|---|---|---|
| Crate placement | Világ UUID **és** pontos világnév | Eltérésnél a strict load hibának tekintheti a helyet; indulási karantén is lehet |
| Event spawnpont | Világnév | Betöltetlen vagy átnevezett világnál a pont nem oldható fel és fallback út jöhet |
| Ferry, introspawn, rejtett hely, őrjárat | Configban világnév | A hibás név a funkciót kihagyhatja vagy használhatatlanná teheti |
| Territory és claim | Tartós világnév | Azonos nevű cserevilágra régi koordináták kerülhetnek; teljes térképaudit kell |
| Parkour | Tartós helyadat | A start és cél külön is ellenőrizendő |
| NPC | A külső NPC-rendszer pozíciója; az IceSMP-kötés belső névre mutat | Az NPC-t a külső rendszerben is át kell helyezni; a név ne változzon véletlenül |

Világcsere előtt ne csak fájlnevet keress. Listázd a crate-eket,
territorykat, eventpontokat, pályákat, NPC-ket és minden configolt
`world,x,y,z` értéket. Crate esetén a régi világ még betöltött állapotában
távolítsd el vagy migráld a placementeket; ne várd meg, amíg a strict loader
csak a következő induláskor jelzi a hibát.

## 4. Natív crate-rendszer

### 4.1. A definíció és a fizikai hely külön dolog

A `config/crates.yml` crate-definíciója mondja meg a kulcsot, árat,
jutalmakat, cooldownokat, hangot, broadcastot, permissiont és a
világpolicy szerinti használhatóságot. A fizikai crate-hely külön, tartós
runtime-adat a `crates-data.yml` fájlban.

Egy crate-definíció attól még létezhet, hogy egyetlen blokk sincs hozzá
regisztrálva. Egy regisztrált blokk pedig veszélyessé válhat, ha a hozzá
tartozó definíciót előbb törlitek a configból.

A bundled release nyolc crate ID-t ad: `koznapi`, `ritka`, `hosi`,
`mitikus`, `mesterseg`, `expedicio`, `hadizsakmany` és `arkanum`.
Ezek az alsó szint 8 állomását töltik ki; a felső szint 8 helye későbbi
szezonális, frakciós és eventládáknak marad. Mindegyik `TRIPWIRE_HOOK`
alapú, saját item-modellel és jutalomprofillal, és a bundled configban egyik
sem kér crate-specifikus permissiont. Az elhelyezéskor az ID-t pontosan,
ékezet nélkül add meg.

A reward-preview nem puszta Material-ikont használ: unique itemnél,
recepttárgynál, tervrajznál és ládakulcsnál a tényleges `ITEM_MODEL` kerül a
GUI-ba és a világban futó revealbe. A `random-blueprint` jutalom szakmával,
`min-level`/`max-level` tartománnyal és az alapból hamis
`include-loot-only` kapcsolóval szűrhető. Üres pool érvényteleníti a crate
definícióját. `ELYTRA` sem közvetlen itemként, sem Elytrát eredményező
receptből/tervrajzból nem engedélyezett; repülőszárnyat csak a relikviarendszer
kezelhet.

### 4.2. Crate-hely létrehozása

Szükséges permission: `icesmp.admin.crate`.

1. Ellenőrizd, hogy a crate ID valid: `/crate status`.
2. Állj legfeljebb öt blokkra a célblokktól, és pontosan nézz rá.
3. Add ki: `/crate set <láda-id>`.
4. Ellenőrizd: `/crate list`.
5. Normál játékosjoggal próbáld ki:
   - kulcs nélkül;
   - hibás kulccsal;
   - egy megfelelő kulccsal;
   - több megfelelő kulccsal, lopakodva;
   - tele inventoryval.

A célblokk anyaga nincs chestre korlátozva: technikailag bármelyik
megcélzott blokk regisztrálható. Ettől függetlenül a játékos számára
egyértelmű, stabil és nem interaktív funkcióval ütköző blokkot válassz.

Egy release legfeljebb 128 fizikai crate-regisztrációt enged. A
regisztráció blokkkoordinátához kötődik, nem a blokk „tárgyazonosságához”.

### 4.3. Crate-hely cseréje

Ha ugyanazon a koordinátán másik valid crate ID-val futtatod a
`/crate set <új-id>` parancsot, a mapping lecserélődik. A mentés atomikus;
íráshibánál a korábbi mapping visszaáll.

Cserénél:

1. `/crate list`;
2. állítsd le a játékosforgalmat;
3. `/crate set <új-id>` ugyanazon a blokkon;
4. `/crate list` és `/crate status`;
5. próbáld ki a régi és az új kulcsot is;
6. restart után ismét ellenőrizd.

Ne cserélj crate-definíciót aktív ItemDisplay-reveal vagy settlement közben. A
config-generáció változása megszakíthatja a folyamatot, amely ezután
recovery-ágra kerülhet.

### 4.4. Crate-hely törlése

1. Nézz a regisztrált blokkra legfeljebb öt blokkról.
2. Add ki: `/crate remove`.
3. Ellenőrizd: `/crate list`.
4. Csak ezután törd ki, másold át vagy cseréld le a fizikai blokkot.

A `/crate remove` csak a runtime-mappinget törli; a fizikai blokkot és a
crate-definíciót nem módosítja. Ha a mentés sikertelen, a mapping
visszaáll.

Fordítva is igaz: a blokk kitörése vagy más anyagra cserélése **nem**
törli a crate-regisztrációt. BlockBreak eseményhez nincs automatikus
placement-törlés. A WorldEdit által átmásolt crate-díszlet az új helyen
nem lesz crate, a régi koordinátán pedig a mapping rejtve megmaradhat.

### 4.5. Crate-világpolicy

Egy crate `worlds` listája:

- üres listánál minden betöltött világot enged;
- nem üres listánál allowlist;
- a config feldolgozásakor ismeretlen vagy nem betöltött világnév az adott
  crate-definíciót érvénytelenné teszi;
- hibás adattípus szintén az adott definíció hibája.

A nyitás közben a rendszer újra ellenőrzi a crate-definíciót,
config-generációt, világ UUID-t és nevet, blokkkoordinátát, crate ID-t,
permissiont és világpolicyt. A játékosnak a settlement alatt nyolc blokkon
belül kell maradnia.

### 4.6. Builderátadás és recovery-határ

A crate location világ UUID-t, pontos világnevet és blokkkoordinátát tárol.
Világ- vagy ID-csere előtt listázd és kontrolláltan mozgasd a placementeket;
a fizikai blokk másolása nem másolja a runtime-kötést.

A crate körül legyen sík, felvehető padló, ne legyen hopper, láva, void,
mély víz vagy idegen claim. Full inventorynál az overflow ide eshet.

Átadáskor teszteld:

- kulcs nélkül, rossz és helyes kulccsal;
- main és off handdel, több key stackkel és mass-opennel;
- tele inventoryval;
- reload és restart után;
- világ-, hely- és definíciócsere után.

A settlement, audit, `MANUAL_REVIEW` és fault-injection recovery már
üzemeltetői felelősség. A
[teljes crate acceptance](ADMIN_GUIDE.md#natív-crate) lezárásáig a
CrazyCrates nem távolítható el.

## 5. Sit-only: ülésre alkalmas építés

### 5.1. Scope

Az IceSMP natív kiváltása kizárólag ülés:

- van `/sit` és `/sit fel`;
- van konfigurálható jobb kattintásos leülés;
- nincs lay;
- nincs crawl;
- nincs stacking;
- nincs player sitting vagy NPC sitting;
- nem cél a GSit teljes upstream-paritása.

Ezekhez az elvetett funkciókhoz ne építs kötelező pályát, ne írj
jelmagyarázatot, és ne ígérd őket a játékosoknak.

### 5.2. Bundled világ- és anyagpolicy

A `config/sit.yml` alapbeállítása:

- `sit.enabled: true`;
- világ-whitelist: üres;
- világ-blacklist: üres;
- engedélyezett tagek: `STAIRS`, `SLABS`, `CARPETS`;
- külön engedélyezett anyagok: `MOSS_CARPET`, `PALE_MOSS_CARPET`, `SNOW`;
- material blacklist: `POWDER_SNOW`, `MAGMA_BLOCK`, `CAMPFIRE`,
  `SOUL_CAMPFIRE`;
- unsafe helyek tiltva;
- a kattintás alapból csak üres kézzel működik;
- legnagyobb kattintási távolság: 4,5 blokk.

Policy sorrend:

1. a világ-blacklist mindig tilt;
2. üres whitelist minden nem blacklistelt világot enged;
3. nem üres whitelist csak a felsorolt világokat engedi;
4. ugyanaz a világ whitelistben és blacklistben hibás config;
5. a material blacklist minden tag- vagy külön anyagengedélyt felülír.

A világ- és anyagnevek normalizáltak, de elgépelésre ne építs.
Érvénytelen sit config esetén a teljes sit funkció biztonságosan letilt.

### 5.3. Pontos ülésmagasság

Az X/Z pozíció mindig a blokk közepe. Az alábbi Y-offset a célblokk
alsó koordinátájához képest értendő.

| Blokkforma | Ülésmagasság | Builder-megjegyzés |
|---|---:|---|
| Alsó stairs | `+0,30` | A stair facing és shape nem módosítja az ülőpontot |
| Felső stairs | `+0,80` | Ellenőrizd a fejteret a magasabb ülőpont miatt |
| Alsó slab | `+0,50` | Stabil pad vagy ülőlap |
| Felső slab | `+1,00` | Az ülőpont a teljes blokk tetején van |
| Double slab | `+1,00` | Ugyanaz a magasság, mint a felső slabnél |
| Vanilla carpet | `+0,0625` | Közel talajszintű ülés |
| Moss carpet | `+0,0625` | Külön anyagként is engedélyezett |
| Pale moss carpet | `+0,0625` | Külön anyagként is engedélyezett |
| Snow | `rétegek / 8` | 1–8 réteg, minimum `0,0625` |
| Más, configban külön engedélyezett anyag | `+0,30` | Generikus fallback; külön playtest szükséges |

Az ülésgeometria nem „keresi meg” a dekoráció vizuális közepét. Például
saroklépcsőnél is a blokk közepe az ülőpont, ezért díszes padsorokat több
irányból is próbálj ki.

### 5.4. Biztonság és clearance

Ha `allow-unsafe-locations: false`, akkor:

- az ülőblokk nem lehet folyadék, waterlogged vagy veszélyes;
- az ülőblokk feletti első **és második** blokk legyen átjárható,
  nem folyadék és nem veszélyes;
- a játékos nem lehet vízben vagy más folyadékban;
- a játékos nem lehet járműben;
- a játékos nem lehet levegőben.

A veszélyes készletbe többek között tartozik:

`LAVA`, `FIRE`, `SOUL_FIRE`, `MAGMA_BLOCK`, `CAMPFIRE`,
`SOUL_CAMPFIRE`, `CACTUS`, `SWEET_BERRY_BUSH`, `WITHER_ROSE`,
`POWDER_SNOW`, `POINTED_DRIPSTONE`.

Az „unsafe engedélyezése” nem teszi jó designná a láva, tűz vagy
fulladásveszély melletti ülést. Ezt csak kontrollált, indokolt helyszínnél
használd.

### 5.5. Átadási próba

Egy blokkot egyszerre csak egy játékos foglalhat. Minden használt formán
próbáld ki a jobb kattintást, a `/sit` és `/sit fel` parancsot, két játékos
egyidejű foglalását, valamint a sebzés, sneak, supporttörés, teleport,
világváltás, quit és reload utáni felállást.

A GSit csak a teljes
[sit-only acceptance](ADMIN_GUIDE.md#sit-only) után távolítható el.
Layre, crawlra, stackingre vagy player/NPC sittingre ne építs pályát:
ezek tudatosan nem részei a natív rendszernek.

## 6. Territórium, claim és világvédelem

### 6.1. Területtípusok

| Típus | Ki építhet normál joggal? | Személyes claim? | Builderhasználat |
|---|---|---|---|
| `FACTION` | A tulajdonos frakció tagjai, a protection config szerint | Igen | Lakó- és játékosépítési övezet |
| `PROTECTED_FACTION` | Senki; csak admin/builder bypass | Nem | Fal, monumentum, frakciómag |
| `PROTECTED_CITY` | Senki; csak admin/builder bypass | Nem | Semleges város, landmark |
| `CAPITAL` | Senki; csak admin/builder bypass | Nem | Főváros; bank/exchange helykapu is |
| `DOOM_GATE` | Senki; csak admin/builder bypass | Nem | Védett PvPvE senkiföldje |
| `DUNGEON` | Senki; csak admin/builder bypass | Nem | Kazamata és dungeonmechanikák |

Az `icesmp.territory.builder` kifejezetten védett zónában való
szerverépítésre szolgál. Az `icesmp.admin.territory.bypass` ennél tágabb,
teljes zóna- és claimvédelmi megkerülés; csak szűk admini körnek add.

### 6.2. Zónák létrehozása és módosítása

Szükséges permission: `icesmp.admin.territory`.

Poligonnál:

```text
/territory pos
/territory undo
/territory clearpoints
/territory points
/territory create <típus> <frakció> <id> [név...]
```

Legalább három, legfeljebb a parancs által elfogadott számú, egyazon
világban felvett, önmagát nem metsző pont kell. A `wand`/`palca` útvonal is
használható. Körzónánál:

```text
/territory circle <típus> <frakció> <id> <sugár> [név...]
```

`DOOM_GATE` típusnál a frakció elhagyható; belső tulajdonosként semleges
érték kerül be.

Főváros körből, az admin aktuális pozíciója körül:

```text
/territory setcapital <frakció> <sugár> [név...]
```

Pontos 3D téglatest-főváros a natív claim-kijelöléssel:

```text
/claim pos1
/claim pos2
/territory setcapital <frakció> selection [név...]
```

A claim-kijelölő pálcát a `/claim wand` adja; annak két sarka ugyanígy
használható. A `selection` mód mindhárom tengelyen blokkpontosan a két
sarok közti, inkluzív `minX..maxX`, `minY..maxY`, `minZ..maxZ` dobozt
menti. Mindkét saroknak és a parancsot kiadó adminnak ugyanabban a világban
kell lennie; az X/Z kiterjedés legfeljebb 1025×1025 blokk lehet. Siker után
a kijelölés törlődik; hiba esetén megmarad javításra. Meglévő személyes
claimet fedő kijelölést előbb a dokumentált `/claim admin unclaim`
folyamattal kell rendezni.

A doboz operatív középoszlopában legyen szilárd talaj és két blokk járható
hely a kijelölt Y-sávon belül. A `/territory tp` és a home rituálé csak
ilyen biztonságos célra teleportál; enélkül a művelet meghiúsul, a home
rituálé áldozata pedig visszajár.

Mindkét forma pontos aktív szintaxis. A régi builderleírásban szereplő
`/territory setcapital <frakció> <id>` forma stale, ne használd. A
`/territory show <id>` a magasságkorlátos zónánál az alsó és felső
keretet, valamint a függőleges éleket is kirajzolja.

Módosítás és ellenőrzés:

```text
/territory rename <id> <új név...>
/territory resize <id> <sugár>
/territory settype <id> <típus>
/territory sety <id> <minY|~> <maxY|~>
/territory remove <id>
/territory list
/territory info
/territory show [id]
/territory tp <id>
```

Zóna átépítése után mindig használd a `show` útvonalat, és járd körbe a
határt normál játékosjoggal. A fal, kapu, út, pince és tető lehetőleg ne
pont a protection szélén álljon.

### 6.3. Frakcióspawn

```text
/territory setspawn <frakció>
```

A parancs az admin pontos állóhelyét, teljes Y-koordinátáját és nézési
irányát menti. Nem keres automatikusan biztonságos talajt.

Az érkezőpontnál:

- legyen legalább 2×2 járható felület;
- legyen két blokk fejhely;
- ne legyen folyadék, tűz, void, keskeny perem vagy zárt ajtó;
- a nézési irány mutasson a kívánt tájékozódási pontra;
- ellenőrizd explicit frakcióválasztással, frakcióváltással és ágy/anchor nélküli
  halál utáni respawnnal.

Az assignment nélküli új játékos Menedék-vendég, nem `NEUTRAL` polgár. A
`factions.spawn.first-join-at-neutral` ennek ellenére fizikailag a semleges
frakcióspawnra teheti mint biztonságos fogadóhelyre; ez nem hoz létre
assignmentet vagy frakcióelőnyt. A `world-events.intro.first-join-spawn`
ezután külön introhelyre viheti. Együtt teszteld a két érkezési lépést, majd
külön az explicit `/faction join neutral` utáni valódi NEUTRAL spawnteleportot.

### 6.4. Claimek és közös infrastruktúra

A játékosclaim lehet gyors chunkclaim vagy blokkpontos terület. A
`/claim area` a `pos1`/`pos2` kijelölést használja, a `/claim extend`
függőlegesen bővít.

World designnál:

- a főutakat, hidakat, kapukat, csatornákat és közös állomásokat ne tedd
  játékos által claimelhető keskeny sávba;
- a játékostelkek között legyen karbantartható közterület;
- a föld alatti és magas építményeket is vedd figyelembe;
- zónahatár-változás után teszteld, hogy meglévő claimek nem kerültek
  tiltott vagy idegen területre;
- admini idegen claim törléshez `/claim admin unclaim` használható, de ez
  adatmutáció, ezért legyen dokumentált indoka és mentése.

## 7. Teleport- és mozgáspontok

### 7.1. Első belépési introspawn

Config:

```text
world-events.intro.first-join-spawn: "world,x,y,z[,yaw,pitch]"
```

Üres értéknél nincs intro-teleport. Beállított értéknél a plugin async
teleport után indítja a címszekvenciát.

Teszteld:

- új, még sosem csatlakozott játékossal;
- betöltött és betöltetlen célchunkkal;
- helyes yaw/pitch értékkel;
- hibás világnévvel és hibás formátummal;
- assignment nélküli vendégként, majd külön explicit `NEUTRAL` választással;
- opcionális kameraút engedélyezése esetén megszakított reconnecttel.

### 7.2. Komp

Az útvonal a `config/economy.yml` fájlban:

```text
ferry.routes.<id>.name
ferry.routes.<id>.a
ferry.routes.<id>.b
ferry.routes.<id>.fee
```

A végpont formátuma `world,x,y,z[,yaw,pitch]`. A `/komp <útvonal-id>` a
játékoshoz közelebbi végponttól visz a másikhoz, ha a beszállási
távolságon belül van, nincs harcban, ki tudja fizetni a díjat és a cooldown
engedi.

Builderfeltételek:

- mindkét végpont szilárd, legalább 2×2-es érkezőtér;
- két blokk fejhely;
- ne spawnoljon korlátban, kapuban, vízben vagy más játékoscsapdában;
- a configolt pont legyen a mólón, ne a díszlet közepén;
- legyen egyértelmű út a kikötőből;
- a beszállási sugár ne érjen át véletlenül másik emeletre vagy partra.

NPC-kötéshez használható:

```text
/npcbind <npc-belső-név> command komp <útvonal-id>
```

A command a kattintó játékos saját jogaival fut; nem kerül meg permissiont
vagy harci korlátozást.

### 7.3. Parkour

```text
/parkour setstart <id> [név]
/parkour setfinish <id> [sugár] [jutalom]
/parkour remove <id>
```

Szükséges permission: `icesmp.admin.parkour`.

Egy pályánál teszteld a startot, célsugarat, cél megközelítését több
irányból, kizuhanást, teleportot, újraindítást és a jutalomkiosztást. A
zuhanásos akadályt külön járd végig assignment nélküli vendéggel és explicit
`NEUTRAL` polgárral: csak az utóbbi kapja az alap `0.50` zuhanásszorzót, de ő
sem zuhanásimmunis.
Ha quest `PARKOUR_TRIAL` objektíva hivatkozik rá, a pálya ID-jének pontosan
egyeznie kell.

### 7.4. Árfolyamtábla

```text
/exchangeboard place
/exchangeboard remove
```

Az elhelyezés az admin aktuális pozícióját használja. Az eltávolítás a
legközelebbi táblát keresi hat blokkon belül. Hagyj hozzá
karbantartási hozzáférést, és ne helyezd másik kattintható blokk vagy NPC
közvetlen ütközési terébe.

## 8. Questhelyszínek és NPC-kötések

### 8.1. Mely questek kérnek világ-előkészítést?

A következő objektívatípusoknak van közvetlen builderhatása:

- `VISIT_TERRITORY` — pontos territory ID;
- `TALK_TO_NPC` — pontos NPC belső név;
- `DELIVER_ITEMS` — pontos cél-NPC;
- `PARKOUR_TRIAL` — pontos pálya-ID;
- `EXPLORE_BIOME` — a megfelelő biome ténylegesen elérhető legyen;
- `PLACE_BLOCKS` és `BREAK_BLOCKS` — legyen jogszerűen módosítható
  játékostér;
- dungeon- vagy eseménycélok — a kapcsolt zóna/arénarendszer működjön.

A configquesteket ne írd át world build közben rögtönzött ID-kkel. A
runtime quest builder csak az admin által létrehozott custom questeket
szerkeszti; a bundled configquestekhez forrás/config release-folyamat kell.

### 8.2. NPC-integráció

Az IceSMP FancyNpcs-integrációja soft dependency. FancyNpcs nélkül a plugin
elindul, a `/npcbind` rekordok megmaradnak, de a fizikai NPC-kattintásnak
nincs runtime hatása. A `/quest talk <npc-név>` fallback tesztelhető.

Aktív kötések:

```text
/npcbind list
/npcbind <npc> quest <quest-id>
/npcbind <npc> shop <bolt-id>
/npcbind <npc> bank
/npcbind <npc> exchange
/npcbind <npc> faction
/npcbind <npc> command <parancs...>
/npcbind <npc> clear
```

Szükséges permission: `icesmp.admin.npc`.

Az NPC-hely legyen:

- normál játékos számára megközelíthető;
- jobb kattintással elérhető;
- más interaktív blokk és crate kattintási terétől elkülönítve;
- tömegben is azonosítható;
- a questmarkereknek vizuálisan olvasható;
- olyan zónában, ahol a játékos nem tudja eltolni, elásni vagy körbezárni.

A binding kulcsa az NPC belső neve, nem a megjelenített neve. NPC
átnevezése előtt listázd a kötéseket. A `command` kötés a játékos saját
permissionjével fut, tehát adminparancsot nem lehet vele játékosnak
„kiosztani”.

### 8.3. Quest- és lore-ID-k

A lore alapján ajánlott helyszín vagy NPC csak kreatív terv, amíg nincs
aktív resource- vagy persistent kötése. Az autoritatív katalógus jelenleg
külön felsorolja:

- a questek által hivatkozott NPC-ID-ket;
- a questek által hivatkozott territory ID-ket;
- a questek által hivatkozott parkour ID-ket;
- az üres bundled NPC-binding és territory-instance kategóriákat.

Ha egy lore-elemet megépítetek, de nincs hozzá aktív runtime-út, jelöld:
**„Tervezett, de ebben a release-ben nem aktív.”**

## 9. Eseményhelyszínek, bossarénák és karaván

### 9.1. Fix eseményspawnpont

```text
/events spawnpoint add <world-boss|escort|caravan|cultists|any> [id]
/events spawnpoint remove <id>
/events spawnpoint list
```

Az `add` az admin aktuális blokkkoordinátáját és világnevét menti az
`event-spawnpoints.yml` store-ba. Ütköző ID esetén a végleges ID
automatikusan kiegészülhet; ezért mindig olvasd vissza a listát.

Az anchor mód a `world-events.anchors.<esemény>.mode` kulcs:

- `player` — játékoshorgony;
- `points` — kijelölt pont, ha használható;
- `random` — a fő világ spawnja körüli véletlen hely;
- `mixed` — pont, ha van, különben random;
- az `any` típusú pont bármely támogatott eseményhez használható.

Ha `points` módban nincs feloldható pont, a hívó a játékosút felé eshet
vissza. Ne feltételezd, hogy egy hibás világnév „biztonságosan letiltja” az
eseményt.

### 9.2. Világboss-aréna

Minimum builderfeltételek:

- sík, szabad spawnmag;
- a boss és addok körül legalább több blokk akadálymentes mozgástér;
- a telegráfok jól látható talajon jelenjenek meg;
- legyen menekülési út, de ne legyen könnyű boss-bebörtönző rés;
- ne legyen véletlen claim, WorldGuard-régió vagy territory-ütközés, ha a
  spawn rule ezeket tiltja;
- ne legyen víz, szakadék vagy dekoráció, amely azonnal beszorítja a mobot;
- nézőpont és respawnút ne közvetlenül a sebzési zónába érkezzen.

A `world-events.spawn-rules` eseménytípusonként szabályozza a territory,
claim, WorldGuard-régió és víz kerülését. A fix pont megléte nem kerül meg
minden további spawn-validációt.

Teszteld az arénát normál és szezonbosszal, második fázissal,
speciáltámadással, addokkal, despawnnal, boss halálával és teljes
inventorys lootkiosztással.

### 9.3. Kereskedő-karaván

A `caravan.stops` üres listája mellett a rendszer event anchorhoz vagy
online játékos közeli fallbackhez nyúlhat. Fix városi megállóhoz configolj
világot és koordinátát.

Megállónál:

- legyen szilárd talaj és elég hely a Wandering Trader entitásnak;
- a shop megnyitása ne ütközzön más NPC-vel;
- ne lehessen a kereskedőt falba, vízbe vagy void fölé spawnoltatni;
- legyen közönség- és tömeghely;
- teszteld az automatikus érkezést, `/events caravan arrive` és
  `/events caravan depart` útvonalat;
- restart/disable után ne maradjon árva kereskedő.

### 9.4. Városi őrjárat

A `city-guards.guards.<id>` világot és route-listát kér. Legalább két
érvényes waypoint kell. Az őr nem valódi pathfindinggal járja be az utat:
lépésekben teleportál a pontok felé, a következő X/Z helyen a legmagasabb
talajhoz igazítva.

Ezért:

- ne vezess route-ot barlang, híd alatti szint vagy többszintes belső tér
  fölött anélkül, hogy külön kipróbálnád;
- ne legyen a pontok között másik világ;
- a szűk ajtó, kerítés és tető könnyen vizuális átvágást okozhat;
- nappali és éjszakai lépéshosszal is járasd végig;
- restart után ellenőrizd az újraspawnolást.

## 10. Dungeonök és bosshelyek

### 10.1. Dungeon zóna

Először hozz létre `DUNGEON` territoryt. A questek és kulcsok hivatkozott
ID-jének pontosan egyeznie kell a zóna ID-jével.

Egy működő kazamata tartalmazzon:

- jól olvasható bejáratot;
- a zónahatáron belüli teljes járható teret;
- olyan oldalfalakat, amelyeket a védelmi határ nem vág ketté;
- biztonságos belépési és visszafordulási pontot;
- chest/barrel lootpontokat;
- miniboss számára akadálymentes spawnteret;
- olyan mennyezetet és ajtókat, ahol a configolt mobtípus elfér;
- kijutási és halál utáni visszatérési folyamatot.

### 10.2. Dungeon lootláda

```text
/territory dungeonchest [tábla]
```

A parancs a legfeljebb öt blokkra nézett `CHEST`, `TRAPPED_CHEST` vagy
`BARREL` blokkot kapcsolja a táblához; ugyanazon a blokkon újra futtatva
törli a regisztrációt.

A loot játékosonként virtuális és cooldownos, ezért:

- a fizikai container tartalma ne legyen a dungeonjutalom autoritatív
  forrása;
- több játékossal is teszteld ugyanazt a ládát;
- WorldEdit-copy után az új container nem örökli automatikusan a kötést;
- blockcsere előtt töröld a kötést;
- teszteld teljes inventoryval, mert az overflow itt is a játékosnál
  eshet le.

### 10.3. Dungeon miniboss

```text
/territory dungeonboss <zóna-id> [tábla]
/territory dungeonboss clear <zóna-id>
```

A beállítás az admin aktuális állóhelyét használja, és csak létező
`DUNGEON` zónához fogadja el. Az alap loot-tábla `kazamata-boss`.

A bosspont körül hagyj elegendő helyet az entitásnak, a játékosoknak és az
itemdropnak. Próbáld ki a zónába lépéses ébredést, respawnt, több játékos
egyidejű belépését, restartot és a clear utáni viselkedést.

## 11. Rituálé-oltárok

### 11.1. A config a tervrajz

A rítusok autoritatív definíciója a `config/relics.yml` `rituals.<id>`
szekciója. Minden rítus meghatározhat:

- `altar-block` magblokkot;
- `structure` listát;
- áldozatokat;
- kaszt- vagy frakciókövetelményt;
- cooldownokat és kimenetet.

A struktúrasor formátuma:

```text
dx,dy,dz:MATERIAL
```

Minden offset a kattintott magblokkhoz képest értendő. A plugin minden
felsorolt blokk pontos anyagát ellenőrzi. Ne egyszerűsítsd automatikusan
„5×5 oltárra”: több rítus hasonló mintát használ, de az egyetlen
autoritatív recept az adott `structure` lista.

### 11.2. Aktiválás és teszt

A játékos lopakodva, main hand jobb kattintással aktiválja a magblokkot.
Builderátadás:

1. másold ki az adott rítus minden offsetjét;
2. jelöld meg a magblokk koordinátáját;
3. építsd meg a struktúrát pontos anyagokból;
4. teszteld jogosult és nem jogosult kaszttal/frakcióval;
5. próbáld hiányzó egyetlen blokkal is;
6. próbáld hiányzó és elegendő áldozattal;
7. ellenőrizd cooldown alatt;
8. teleportáló rítusnál a célhelyet külön vizsgáld.

WorldEdit-forgatás vagy tükrözés megváltoztathatja az offsetek
orientációját. A matcher nem forgatja hozzá automatikusan a tervet a
díszlethez; paste után mindig ténylegesen aktiváld.

## 12. Profession-, crafting- és itemhelyszínek

### 12.1. Profession craft nem blokkhoz kötött

A szakmai receptkönyv:

```text
/profession recipes
```

A GUI a játékos inventoryjából ellenőrzi és fogyasztja a hozzávalókat,
majd az eredményt az inventoryba adja; overflow esetén a játékos helyén
eldobja. Nincs kötelező kovácsasztal-, üllő-, főzőállvány- vagy más
world-station ellenőrzés ezen a craft útvonalon.

Ezért a kovácsműhely, alkimista-labor, céhház vagy konyha:

- erősen ajánlott world design és játékosvezetés;
- lehet NPC-, quest- vagy command-hozzáférési pont;
- **nem** bizonyított technikai előfeltétele a szakmai GUI-craftnak.

Ne írd ki azt, hogy „csak ennél az asztalnál craftolható”, hacsak külön
runtime-kötés vagy későbbi forrásmódosítás ezt ténylegesen nem vezeti be.

### 12.2. Vanilla crafting és védett egyedi alapanyagok

A plugin egyes vanília crafting recepteket szakma/szint alapján
korlátozhat. Az egyedi szakmai alapanyagok PDC-azonosítót használnak, és
védve vannak attól, hogy véletlenül vanília receptben, furnace fuelként,
evésre vagy blokklerakásra használják őket. Ezek a saját profession recipe
book összetevői.

Builderként ez azt jelenti:

- tematikus vanilla stationt építhetsz, de a recipe book GUI-t is
  kommunikáld;
- ne használj valódi egyedi alapanyagot dekorációs block-itemként;
- teszteld a műhelyt normál játékossal, ne csak admini `/iceitem` tárggyal;
- jutalomátadó hely körül legyen overflow-biztos padló.

### 12.3. Resource pack és item model

A crate-kulcsok, crate-jutalmak, profession-anyagok, recepteredmények,
lootok, relikviák és más tárgyak namespaced `item-model` kulcsokat
használhatnak, például `icesmp:...`.

A modell vizuális réteg: az item runtime-azonosságát a plugin adatai
biztosítják, de a tervezett megjelenéshez a resource packben léteznie kell
a megfelelő modellnek.

Átadás előtt:

- gyűjtsd ki a helyszínen kiosztható item-model kulcsokat;
- ellenőrizd őket a release resource packjében;
- próbáld packkel és pack nélkül;
- ne építs olyan vizuális rejtvényt, amely pack nélkül teljesen
  értelmezhetetlenné válik, ha a pack nincs kötelezőre állítva;
- item-model átnevezést a configgal és a packkel egy release-ben végezz.

## 13. Rejtett helyek, emlékművek és további configolt pontok

### 13.1. Rejtett hely

`hidden-spots.spots.<id>` legalább nevet, helyet és sugarat igényel; jutalom
és XP is társítható hozzá. A bundled map üres, tehát a mechanika
capabilityként aktív, de nincs kész release-helyszín.

A sugár:

- ne érjen be véletlenül közútra vagy másik emeletre;
- legyen teljesíthető a látványosan kijelölt pont elérésével;
- ne fedjen át másik hidden spottal;
- a jutalomdrop legyen felvehető.

Első és későbbi látogatóval is teszteld; a rendszer külön jutalomarányt és
first-finder-only módot támogat.

### 13.2. Szezon-emlékmű

Config:

```text
season-monument.enabled
season-monument.location
season-monument.max-lines
```

A `location` formátuma `world,x,y,z`. Üres location mellett a történeti
rekordok gyűlhetnek, de fizikai emlékmű nem jelenik meg.

Hagyj helyet:

- a bajnok frakció bannerblokkjának;
- a hologram több sorának;
- a későbbi szezonváltásoknak;
- a játékosforgalomnak és screenshotnak.

Teszteld szezonlezárás staging-szimulációjával; ne production
szezonzáráskor derüljön ki, hogy falban vagy másik kijelzőben jelenik meg.

### 13.3. Világportálok

A bundled world policy szerint új Nether-portál létrehozása alapból
tiltott, az End pedig config szerint zárható. A meglévő portál és az új
portál gyújtása nem ugyanaz a szabály.

Portálhelyszínt csak admini esemény- és permissiontervvel adj át. A
dekoratív kapuknál egyértelműen különítsd el a valódi, működő vanilla
keretet. Teszteld játékos és builder bypass joggal, mindkét irányú
utazással és világújraindítással.

## 14. WorldEdit/FAWE utáni kötelező ellenőrzés

WorldEdit vagy más világpaste után ellenőrizd:

- crate: a régi koordinátán törölted-e a mappinget, az újon létrehoztad-e;
- sit: maradt-e érvényes support, fejhely és biztonságos geometria;
- territory: a zónahatár lefedi-e az új falat, pincét és tetőt;
- claim: nem került-e közös elem játékosclaimbe;
- spawn: nincs-e blokkban, levegőben vagy veszélyben;
- ferry: mindkét végpont a megfelelő padlóra érkezik-e;
- eventpoint: az aréna közepe továbbra is szabad-e;
- parkour: start, cél és célsugár jó helyen van-e;
- dungeon: chest és boss runtime-kötések megmaradtak-e;
- rituálé: minden offset anyaga és orientációja pontos-e;
- NPC: a külső NPC pozíciója és belső neve változatlan-e;
- configolt pontok: világ- és koordinátaértékek az új buildhez igazodnak-e;
- resource pack: a kihelyezett mintatárgyak modellje továbbra is feloldódik-e.

Ezután kontrollált `/icesmp reload` vagy teljes restart szükséges az
érintett rendszer dokumentált reloadviselkedése szerint. A reload nem
helyettesíti a listázást és a játékosoldali próbát.

## 15. Konfiguráció és világépítés függőségei

| Változtatás | Elsődleges hely | Biztonságos alkalmazás | Kötelező visszaellenőrzés |
|---|---|---|---|
| Sit világ/anyagpolicy | `config/sit.yml` | Configvalidálás, `/icesmp reload` | `/sit` minden érintett formán; aktív ülések resetje |
| Crate definíció/world allowlist | `config/crates.yml` | Placementlista és mentés után reload | `/crate status`, fizikai nyitás, restart |
| Ferry route | `config/economy.yml` | Mindkét végpont készre építése után reload | `/komp`, díj, távolság, harci tiltás |
| Caravan stops | `config/economy.yml` | Megálló megépítése után reload | Kézi és automatikus érkezés/távozás |
| Introspawn | `config/world.yml` | Új fiókkal staging | Teleport, intro, reconnect |
| Event anchor mód | `config/world.yml` + persistent pontok | Pontlista után configváltás | Kézi esemény minden érintett típushoz |
| Rejtett hely | `config/world.yml` | Mentés + pontos sugár | Első és későbbi felfedező |
| Szezon-emlékmű | `config/world.yml` | Üres staging helyen | Banner/hologram és többsoros megjelenés |
| Városi őrség | `config/world.yml` | Legalább két waypoint | Nappali/éjszakai teljes kör |
| Rituálé | `config/relics.yml` + fizikai multiblock | Pontos offsetlista alapján | Sikeres és hiányos aktiválás |
| Quest config | `config/quests.yml` | Release-folyamatban, nem rögtönzött live editként | Quest felvétel, haladás, restart |
| Profession item model | profession resource-ok + resource pack | Azonos verzióban | Packkel/pack nélkül és item-azonosság |

A teljes típus-, alapérték-, hibakezelési és reloadmátrixot a
[konfigurációs referencia](ADMIN_GUIDE.md#konfiguráció-és-reload)
tartalmazza. Ha az adott kulcs reloadviselkedése nincs bizonyítva,
kontrollált teljes restarttal számolj.

## 16. Permissionök a builderfolyamatban

| Permission | Mire való | Javasolt kiosztás |
|---|---|---|
| `icesmp.territory.builder` | Építés védett server-zónákban | Builder szerep, csak staging/építési időre ha lehetséges |
| `icesmp.admin.territory` | Territoryk, dungeon chest/boss, admin claim route | Lead builder vagy world admin |
| `icesmp.admin.territory.bypass` | Teljes zóna- és claimvédelem megkerülése | Szűk vezető admini kör |
| `icesmp.admin.crate` | Crate placement, kulcs, stat és status | Crate-admin vagy lead builder |
| `icesmp.admin.events` | Eventpont és kézi események | Eventes/admin |
| `icesmp.admin.npc` | Tartós NPC-kötések | Tartalomadmin |
| `icesmp.admin.parkour` | Pálya start/cél/törlés | Pályaépítő/admin |
| `icesmp.admin.exchangeboard` | Árfolyamtábla elhelyezés/törlés | Gazdasági world admin |
| `icesmp.admin.quest` | Custom quest builder és admin route-ok | Tartalomadmin, nem minden builder |
| `icesmp.admin.reload` | Config reload | Üzemeltető vagy vezető admin |

Ne adj `icesmp.admin.all` vagy OP jogot pusztán azért, hogy valaki
építhessen. A builder bypass és az adott domain permissionje külön
kezelhető.

## 17. Átadás és kötelező playtest

Egy helyszín átadólapján legyen:

- [ ] stabil belső ID, világ és koordináta;
- [ ] a kapcsolódó config-, quest-, NPC- és parancshivatkozás;
- [ ] módosítás előtti, visszaállítható mentés;
- [ ] normál játékosos, builderes és permission nélküli próba;
- [ ] biztonságos talaj, fejhely, érkezés és jutalom-overflow;
- [ ] a listázó/status parancs bizonyítéka;
- [ ] reload és teljes restart utáni próba;
- [ ] WorldEdit vagy világcsere utáni újraaudit;
- [ ] resource-pack modell packkel és fallback nélkül;
- [ ] felelős, eredmény és bizonyíték helye.

Külön ellenőrizd a crate-eket, minden használt ülésformát, a négy
frakcióspawnt, az introspawn-t, a komp mindkét végét, parkourt, event- és
bosspontokat, dungeon chest/boss kötést, NPC-k belső nevét, questhelyeket,
rituáléstruktúrákat és rejtett helyeket.

### Ne csináld

- Ne törd ki a crate-blokkot a `/crate remove` előtt.
- Ne nevezz át világot, crate ID-t vagy NPC belső nevet kötésleltár nélkül.
- Ne várd, hogy a WorldEdit-copy magával vigye a runtime-kötéseket.
- Ne szerkessz élő state-fájlt mentés és offline migrációs terv nélkül.
- Ne adj OP vagy `icesmp.admin.all` jogot pusztán az építéshez.
- Ne forgasd vagy tükrözd a rituálét aktiválási próba nélkül.
- Ne ígérj jutalmazó AFK-zónát, layt, crawlt vagy player/NPC sittinget.
- Ne nevezz lore-elemet aktív gameplaynek valódi registry/config/kötés nélkül.
- Ne tekints zöld CI-t production world playtestnek.

### Ismert határok

- A repository nem tartalmazza az élő világ és a külső pluginok teljes
  állapotát.
- Nincs csomagolt kész territory-, frakcióspawn-, parkour-, NPC-binding-,
  ferry-, caravan-, guard- vagy hidden-spot készlet.
- A FancyNpcs és WorldGuard soft dependency; az élő világ igénye dönt a
  használatukról.
- A profession craft GUI-alapú, nem követel fizikai műhelyblokkot.
- A crate recovery kritikus ágai admin/üzemeltetői folyamatok.

A teljes pipálható csapatfolyamat:
[release acceptance checklist](ADMIN_GUIDE.md#release-acceptance-checklist).

## 18. Season 0 / Prologue — Olethropyla runtime hookok

A Prologue **nem használ beégetett világkoordinátákat**. A végleges staging
világon négy konfigurált runtime hookot kell feloldani és ellenőrizni:

| Hook | Szerep | Builderfeltétel |
|---|---|---|
| `prologue-gate` | Olethropyla / Kárhozat Kapuja központi kapu-anchor | a tényleges ősi Kapu helye; védett, jól megközelíthető, a Nether-travel policyvel együtt tesztelve |
| `prologue-gathering` | a production finale gyülekezőpontja | nagyobb játékoscsoport számára szabad, biztonságos tér, ne essen spawn- vagy combatveszélybe |
| `prologue-breach` | a breach- és finale-hullámok encounter anchorja | moboknak/addoknak elegendő mozgástér, tiszta spawnmag, nincs claim/WG/territory ütközés |
| `prologue-boss` | a finale boss arena anchorja | boss + addok + telegraphok számára szabad aréna, menekülési és játékosforgalmi útvonallal |

A dokumentációba **ne írj kitalált koordinátát**. A hookok tényleges értéke a
végleges world buildből jön; módosítás előtt készíts world/config backupot,
és minden kötést a végleges release-builddel olvass vissza.

### Builder acceptance

A Prologue helyszínt csak akkor add át, ha mind a négy hook ténylegesen
feloldódik, nincs idegen claim/WorldGuard/protection konfliktus, és a normál
játékosos hozzáférés megfelel a Season 0 policynek. Külön próbáld ki:

1. a `prologue-gate` elérhetőségét Season 0 alatt úgy, hogy a Kapu még nem
   átjárható;
2. a rehearsal teljes gathering → breach → boss útját tartós Gate/reward
   side effect nélkül;
3. production finale startot, pause-t, resume-ot és az irreverzibilis
   szakasz előtti abortot;
4. aktív wave és boss közbeni pause-t: a mobok nem harcolhatnak tovább, új
   spawn/mechanika nem indulhat, a játékos sem ütheti büntetlenül a
   befagyasztott event mobot;
5. `BOSS_FIGHT` + pause alatti kontrollált restartot, majd resume-ot;
6. abort, timeout és kontrollált shutdown után az event entityk teljes
   cleanupját, árva Prologue mob nélkül;
7. Gate-unlock után az egyetlen legitim Nether-átjárást Olethropylán; más
   Nether-portál létrehozása továbbra is tiltott;
8. az End policy változatlanságát — a Prologue buildermunka nem nyitja meg a
   Véget és nem hoz létre alternatív portálrendszert.

A world-hook acceptance kézi stagingkapu. A source-level Folia és regression
tesztek nem helyettesítik a tényleges aréna-, collision-, spawn- és
játékosforgalmi próbát.
