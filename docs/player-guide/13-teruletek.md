# 13. Frakcióterületek és saját birtok 🏰

A világban vannak **frakcióterületek** és **fővárosok** (ezeket az adminok jelölik ki), és
**saját birtokod** is lehet: a `/claim` paranccsal blokk-pontos területeket foglalhatsz, ahol senki más nem
építhet és nem lophat.

## Mit veszel észre belőlük?

- Amikor **átlépsz** egy terület határán, a képernyő alján (action bar) egy felirat jelzi, hol
  vagy: pl. „✦ Piros főváros ✦", „⛨ védett város ⛨", vagy „vadon" ha senkié.
- Minden frakciónak lehet **1 fővárosa** és több **területe**.
- A területek lehetnek **kör** alakúak vagy **poligonok** (pontos határvonal, pl. egy városfal
  mentén kijelölve) — ezt az adminok döntik el.

## Zónatípusok

A térkép védelme több **zónatípusra** épül:

| Típus | Ki építhet itt? | Lehet ide claimelni? |
|---|---|---|
| **Frakcióterület** | Csak az adott **frakció tagjai** | ✅ Igen (a saját birtokod ide is mehet) |
| **Védett frakcióterület** | **Senki** (a frakció magja: falak, műemlékek) | ❌ Nem |
| **Védett város** | **Senki** (jellemzően semleges városok) | ❌ Nem |
| **Főváros** | **Senki** (egyben a bank/valutaváltó helyszíne) | ❌ Nem |
| **Kárhozat-zóna** ☠ | **Senki** (PvPvE senkiföldje — lásd lent) | ❌ Nem |
| **Kazamata** 🗝 | **Senki** (kulccsal járható dungeon: 2 órás futam, 7 napos pecsét) | ❌ Nem |

> ⛨ A **védett zónák** (védett város/frakcióterület és a főváros) a térkép **pajzsa**: ott
> alapból **senki** sem építhet/bonthat, nincs interakció, **nincs PvP**, robbanás és tűz sem
> tesz kárt, és **claimelni sem lehet**. Csak az admin/builder jogok kerülik meg.

### ☠ Kárhozat Kapuja — a PvPvE senkiföldje

A kódex szerint a Hetedik Vérháborút kirobbantó **óriás Nether-portál** környéke a szerver
legveszélyesebb zónája. Itt minden másképp működik:

- **A PvP legális** — a zónában bárki megtámadhat bárkit, és az ölés **nem számít bűnnek**
  („a Kapunál nincs törvény"). A frakciók itt nyíltan összecsaphatnak.
- **Belépő-védelem:** a zónába lépve pár másodperc PvP-védelmet kapsz (spawn-kill ellen) —
  de aki maga támad, azonnal elveszti.
- **A szörnyek erősebbek**: a zónában spawnoló mobok bónusz szinteket kapnak — cserébe a
  magasabb szint jobb lootot ér (a Néma Királynő élőhalottaitól a nevesített relikvia-drop is
  eshet).
- Az aréna maga védett: **építeni nem lehet**, robbanás és tűz nem rongálja — de ajtók,
  oltárok szabadon használhatók.
- Belépéskor baljós hang és hamu-örvény jelzi, hogy a Kapu árnyékába értél.

**Frakcióterületen** csak az adott frakció tagjai építhetnek (mások nem), viszont ide a
játékosok **saját birtokot (`/claim`) is foglalhatnak** — így a claim rendszer és a
territórium rendszer együtt működik.

### ⛩ Az egyetlen kapu — nether-portál szabály

Ezen a világon **új nether-portált nem lehet gyújtani** — sehol. A világ **egyetlen élő
kapuja a Kárhozat Kapuja**: aki a Netherbe akar jutni, annak a **senkiföldjén át** vezet
az útja, ahol a PvP legális és az ölés nem bűn. A kapu használata tehát mindig kockázat —
pontosan úgy, ahogy a régiek mesélik.

## Mi tiltható zónánként?

Az adminok **zónatípusonként külön-külön** állíthatják, mi szabad az adott zónában
(`territory.protection.rules` a configban, egyértelmű kulcsokkal: `allow-<szabály>: true` =
szabad, `false` = tilos):

| Szabály | Mit tilt le | Frakcióterület alapból | Védett zóna alapból |
|---|---|---|---|
| **build** | blokk törése/rakása, vödör, kép-/festménykeret, armor stand | 🔒 nem-tagoknak | 🔒 mindenkinek |
| **interact** | konténer/ajtó/gomb/kar/műhely jobbklikk | 🔓 szabad | 🔒 mindenkinek |
| **pvp** | játékos↔játékos sebzés (biztonságos zóna) | 🔓 szabad (harc mehet) | 🔒 tilos |
| **explosions** | creeper/TNT/kristály blokk- és dekoráció-kára | 🔓 mehet | 🔒 tiltva |
| **fire** | tűz gyújtása/terjedése/égése | 🔓 mehet | 🔒 tiltva |

> Így pl. egy „védett város" teljes biztonságos zóna (se rombolás, se PvP, se tűz), egy
> frakcióváros viszont csak a nem-tagok építését tiltja, de a harc és a nyílt interakció megy.

A védelem a „hátsó ajtókat" is lefedi: védett zónában a **mob-grief** (enderman), a kívülről
**befolyó víz/láva** és a **dugattyú** sem módosítja a terepet (a `build` szabály alá esnek); a
**PvP** a közelharcon túl a **nyílra/lövedékre, háziállatra, TNT-re és ártó bájitalra** is áll.

### Megkerülő jogok (bypass)

- **`icesmp.admin.territory.bypass`** — mindent megkerül (build, interakció, **PvP** is).
- **`icesmp.territory.builder`** — **építő-jog**: a védett zónában is **építhet és
  interaktálhat** (de a PvP-tiltás rá is vonatkozik). Az építő-csapatnak ideális, admin-jog nélkül.

> Ha valahol nem tudsz blokkot lerakni/törni: vagy egy másik frakció területén állsz, vagy egy
> védett zónában (főváros/védett város).

## Saját birtok — terület-claim 🏠

A **claim** a te személyes, védett földed — frakciótól függetlenül bárki claimelhet.

### Hogyan claimelj?

1. Állj oda, ahol a birtokod közepét szeretnéd.
2. Írd be: **`/claim`** — egy **16×16 blokkos** területet foglal körülötted, **±20 blokk
   magasságban** (a claim egy doboz: fölötte/alatta a világ szabad — a menüből pénzért
   magasíthatod/mélyítheted +5 blokkonként). A határokat részecskék rajzolják ki.
   **Blokk-pontos, egyedi méretű** birtokhoz: állj a terület egyik sarkára (`/claim pos1`),
   a másikra (`/claim pos2`), majd `/claim area` — a méretet és az árat előre kiírja.
   **Kényelmesebb út — a Birtokmérő pálca** (`/claim wand`): bal kattintás a blokkra =
   1. sarok, jobb kattintás = 2. sarok (a méret és az ár azonnal megjelenik), majd
   **SNEAK + jobb kattintás = foglalás**. Nem kell a sarkokra odaállni — elég rájuk mutatni!
   Claim-határ átlépésekor az action-bar mutatja, kinek a birtokára léptél.

**Mennyibe kerül?**
- Az ár **oszloponként** (1×1 blokk alapterület) számolódik: az első **768 oszlop ingyenes** (~3 chunknyi terület).
- Utána minden további oszlop a **saját frakció-valutádba** kerül, **fix 0,5/oszlop** áron
  (nem drágul oszloponként — csak a megvett oszlopok számával nő a végösszeg). Az ár **ELÉG**
  (money sink) — az `/claim unclaim`-nél sem jár vissza!
- Játékosonként alapból legfeljebb **8192 oszlopnyi** (~32 chunknyi) birtokod lehet.

### Mit véd a claim?

A birtokodon (a claim dobozán belül) **idegenek**:
- nem törhetnek és nem rakhatnak le blokkot;
- nem nyithatnak **konténert** (láda, hordó, kemence…);
- nem üríthetnek vödröt, nem szedhetik le a kép-/festménykereteket;
- a **robbanás sem bontja** a claimelt blokkokat, és a blokk-evő mobok (pl. enderman) sem
  vihetnek el semmit;
- a **tűz** nem gyullad meg, nem terjed és nem éget claimelt blokkot;
- kívülről **folyadék** (víz/láva) nem folyhat be, és idegen **dugattyú** sem tolhat be /
  húzhat ki blokkot (a claimen belüli saját gépek működnek).

> ⚔️ **Fontos:** a claim a **PvP-t NEM tiltja** — ez háborús szerver! A claim csak az
> **építést és a lopást** védi, harcolni a birtokodon is lehet.

### Megbízottak (trust)

- `/claim trust <név>` — a megbízott **teljes hozzáférést** kap **minden** claimedhez
  (építhet, nyithat ládát).
- `/claim untrust <név>` — megbízás visszavonása.
- **GUI-ból is megy:** `/menu` → Birtok → **„Megbízottak kezelése"** — felül a megbízottaid
  (kattintás = visszavonás), alul a közeledben álló játékosok (kattintás = megbízás).

### Hasznos claim-parancsok

| Parancs | Mit csinál |
|---|---|
| `/claim` | 16×16 blokk gyorsfoglalása körülötted (±20 blokk magasságban) |
| `/claim unclaim` | A claim felszabadítása, amiben állsz (az ár NEM jár vissza) |
| `/claim info` | Kié ez a terület? (+ határ-kirajzolás) |
| `/claim list` | Saját claimjeid listája |
| `/claim show` | A környező claimek PEREMÉNEK kirajzolása pár másodpercig (zöld = sajátod, láng = másé, komposzt = a gyorsfoglalás előnézete) |
| `/claim pos1` / `/claim pos2` | Blokk-pontos kijelölés két sarka (a blokk, amin állsz) |
| `/claim wand` | **Birtokmérő pálca** — sarok-kijelölés kattintással, SNEAK+jobb = foglalás |
| `/claim area` | A két sarok közti pontos téglalap lefoglalása (az ár előre kiírva, egyben ég el) |
| `/claim extend up\|down` | A claim magasítása / mélyítése +5 blokkonként, pénzért (a menüből is) |
| `/claim trust <név>` / `/claim untrust <név>` | Megbízott hozzáadása / elvétele |

### Hol NEM lehet claimelni?

- **Védett zónában**: főváros, védett város, védett frakcióterület (spawn/város).
- **Frakcióterületen viszont IGEN** — a saját birtokod a frakciód földjén is elférhet
  (kivéve, ha a szerver a `claims.block-in-territory` kapcsolóval ezt is letiltja).
- Cserébe a **meteor-becsapódás** és az **elrejtett kincs** esemény is elkerüli a claimelt
  területet — a birtokod biztonságban van tőlük.
- **Raid alatt** a claim alapból véd, de szerver-beállítástól függően a jelentkezett támadók
  a claim-ládákat hadizsákmányként **kinyithatják** (lebontani akkor sem tudják).

## Admin parancsok (csak adminoknak)

Kör-zóna a pozíciódnál, vagy pontos **poligon** a bejárt határpontokból:

- `/territory pos` — határpont hozzáadása a jelenlegi pozíciódnál (poligonhoz, akár 10+ pont).
- `/territory undo` / `clearpoints` / `points` — a határpont-puffer kezelése.
- `/territory create <típus> <frakció> <id> [név...]` — poligon-zóna lezárása a pontokból.
- `/territory circle <típus> <frakció> <id> <sugár> [név...]` — kör-zóna a pozíciódnál.
- `/territory create doom-gate <id> [név...]` / `/territory circle doom-gate <id> <sugár> [név...]`
  — a Kárhozat-zóna frakció-semleges, ezért ott a `<frakció>` argumentum elhagyható.
- `/territory setcapital <frakció> <sugár> [név...]` — főváros (kör) gyorsan.
- `/territory show [id]` — határrajz: a puffered + az aktuális zóna, vagy a megadott zóna.
- `/territory tp <id>` — teleportálás a zóna középpontjához.
- `/territory rename <id> <név...>` — zóna átnevezése.
- `/territory resize <id> <sugár>` — kör-zóna új sugara (poligonra nem működik).
- `/territory settype <id> <típus>` — zóna típusának megváltoztatása.
- `/territory sety <id> <minY> <maxY>` — magassági sáv beállítása (`~` = korlátlan; pl.
  `/territory sety varos 60 ~` = csak 60-as magasságtól felfelé véd).
- `/territory remove <id>` — zóna törlése.
- `/territory list` / `/territory info` — zónák listája / az aktuális zóna infója (magassággal).
- `/claim admin unclaim` — idegen claim törlése admin-jogon (a claimben állva).

> **Típusok:** `faction` (csak tagok építhetnek), `protected-faction` / `protected-city`
> (senki), `capital` (főváros), `doom-gate` (Kárhozat-zóna: PvPvE senkiföldje).
> A városfal mentén így pontosan kijelölhető a terület: járd
> körbe a falat, minden saroknál `/territory pos`, végül `/territory create protected-city ...`.
> A rendszer figyelmeztet, ha a határvonal **önmagát keresztezné** (összegabalyodott fal).

---

➡️ Tovább: [Parancsok listája](14-parancsok.md) • [Vissza a tartalomhoz](README.md)

## Mortengrad — a holtak fővárosa 💀

A Kitaszítottak fővárosa a lore szerint is az élőholtaké: a territóriumban **folyamatosan
magas szintű undead-horda** kóborol, akik **nappal sem égnek el**. A Kitaszítottakat a Néma
Királynő népe békén hagyja — mindenki más csak saját felelősségére lépjen be. A látvány
(és a loot) megéri… ha túléled.
