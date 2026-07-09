# 13. Frakcióterületek és saját birtok 🏰

A világban vannak **frakcióterületek** és **fővárosok** (ezeket az adminok jelölik ki), és
**saját birtokod** is lehet: a `/claim` paranccsal lefoglalhatsz chunkokat, ahol senki más nem
építhet és nem lophat.

## Mit veszel észre belőlük?

- Amikor **átlépsz** egy terület határán, a képernyő alján (action bar) egy felirat jelzi, hol
  vagy: pl. „✦ Piros főváros ✦", vagy „vadon" ha senkié.
- Minden frakciónak lehet **1 fővárosa** és több **claimelt** (lefoglalt) területe.

## Építésvédelem (ha be van kapcsolva)

A szerver bekapcsolhatja az **építésvédelmet** (`territory.protection.enabled`). Az alap
konfigurációban ez **ki van kapcsolva**, tehát alapból csak a határátlépés-értesítés fut.
Ha az adminok bekapcsolják:
- **Idegen frakció területén nem tudsz építeni vagy bontani.**
- A saját frakciód területén és a vadonban szabadon építkezhetsz.
- (Adminoknak van „bypass" joguk, nekik mindenhol szabad.)

> Ez megvédi a frakciók fővárosait a rongálástól. Ha valahol nem tudsz blokkot lerakni/törni,
> valószínűleg egy másik frakció területén állsz.

## Saját birtok — terület-claim 🏠

A **claim** a te személyes, védett földed — frakciótól függetlenül bárki claimelhet.

### Hogyan claimelj?

1. Állj bele abba a **chunkba** (16×16 blokkos terület), amit le akarsz foglalni.
2. Írd be: **`/claim`** — kész is! A chunk-határt részecskék rajzolják ki.

**Mennyibe kerül?**
- Az első **3 chunk ingyenes**.
- Utána minden további chunk a **saját frakció-valutádba** kerül, és **egyre drágább**
  (alapból az első fizetős 100, utána mindegyik másfélszeres). Az ár **ELÉG** (money sink) —
  az `/claim unclaim`-nél sem jár vissza!
- Játékosonként alapból legfeljebb **10 chunkod** lehet.

### Mit véd a claim?

A te chunkodban **idegenek**:
- nem törhetnek és nem rakhatnak le blokkot;
- nem nyithatnak **konténert** (láda, hordó, kemence…);
- nem üríthetnek vödröt, nem szedhetik le a kép-/festménykereteket;
- a **robbanás sem bontja** a claimelt blokkokat, és a blokk-evő mobok (pl. enderman) sem
  vihetnek el semmit.

> ⚔️ **Fontos:** a claim a **PvP-t NEM tiltja** — ez háborús szerver! A claim csak az
> **építést és a lopást** védi, harcolni a birtokodon is lehet.

### Megbízottak (trust)

- `/claim trust <név>` — a megbízott **teljes hozzáférést** kap **minden** claimedhez
  (építhet, nyithat ládát).
- `/claim untrust <név>` — megbízás visszavonása.

### Hasznos claim-parancsok

| Parancs | Mit csinál |
|---|---|
| `/claim` | Az aktuális chunk lefoglalása |
| `/claim unclaim` | Az aktuális chunk felszabadítása (az ár NEM jár vissza) |
| `/claim info` | Kié ez a chunk? (+ határ-kirajzolás) |
| `/claim list` | Saját claimjeid listája |
| `/claim show` | Chunk-határ kirajzolása részecskékkel (zöld = szabad/sajátod, láng = másé) |
| `/claim trust <név>` / `/claim untrust <név>` | Megbízott hozzáadása / elvétele |

### Hol NEM lehet claimelni?

- **Frakció-territóriumban** (az a királyság földje) és **védett zónában** (spawn/város).
- Cserébe a **meteor-becsapódás** és az **elrejtett kincs** esemény is elkerüli a claimelt
  chunkokat — a birtokod biztonságban van tőlük.
- **Raid alatt** a claim alapból véd, de szerver-beállítástól függően a jelentkezett támadók
  a claim-ládákat hadizsákmányként **kinyithatják** (lebontani akkor sem tudják).

## Admin parancsok (csak adminoknak)

- `/territory setcapital` — főváros kijelölése.
- `/territory claim` — terület lefoglalása.
- `/territory remove` — terület törlése.
- `/territory list` / `/territory info` — területek listája / infó.
- `/claim admin unclaim` — idegen claim törlése admin-jogon (az adott chunkban állva).

---

➡️ Tovább: [Parancsok listája](14-parancsok.md) • [Vissza a tartalomhoz](README.md)
