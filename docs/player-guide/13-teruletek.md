# 13. Frakcióterületek és saját birtok 🏰

A világban vannak **frakcióterületek** és **fővárosok** (ezeket az adminok jelölik ki), és
**saját birtokod** is lehet: a `/claim` paranccsal blokk-pontos területeket foglalhatsz, ahol senki más nem
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

1. Állj oda, ahol a birtokod közepét szeretnéd.
2. Írd be: **`/claim`** — egy **16×16 blokkos** területet foglal körülötted, **±20 blokk
   magasságban** (a claim egy doboz: fölötte/alatta a világ szabad — a menüből pénzért
   magasíthatod/mélyítheted +5 blokkonként). A határokat részecskék rajzolják ki.
   **Blokk-pontos, egyedi méretű** birtokhoz: állj a terület egyik sarkára (`/claim pos1`),
   a másikra (`/claim pos2`), majd `/claim area` — a méretet és az árat előre kiírja.
   Claim-határ átlépésekor az action-bar mutatja, kinek a birtokára léptél.

**Mennyibe kerül?**
- Az ár **oszloponként** (1×1 blokk alapterület) számolódik: az első **768 oszlop ingyenes** (~3 chunknyi terület).
- Utána minden további oszlop a **saját frakció-valutádba** kerül (alapból 0,5/oszlop)
  (alapból az első fizetős 100, utána mindegyik másfélszeres). Az ár **ELÉG** (money sink) —
  az `/claim unclaim`-nél sem jár vissza!
- Játékosonként alapból legfeljebb **8192 oszlopnyi** (~32 chunknyi) birtokod lehet.

### Mit véd a claim?

A birtokodon (a claim dobozán belül) **idegenek**:
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
| `/claim` | 16×16 blokk gyorsfoglalása körülötted (±20 blokk magasságban) |
| `/claim unclaim` | A claim felszabadítása, amiben állsz (az ár NEM jár vissza) |
| `/claim info` | Kié ez a terület? (+ határ-kirajzolás) |
| `/claim list` | Saját claimjeid listája |
| `/claim show` | A környező claimek PEREMÉNEK kirajzolása pár másodpercig (zöld = sajátod, láng = másé, komposzt = a gyorsfoglalás előnézete) |
| `/claim pos1` / `/claim pos2` | Blokk-pontos kijelölés két sarka (a blokk, amin állsz) |
| `/claim area` | A két sarok közti pontos téglalap lefoglalása (az ár előre kiírva, egyben ég el) |
| `/claim extend up\|down` | A claim magasítása / mélyítése +5 blokkonként, pénzért (a menüből is) |
| `/claim trust <név>` / `/claim untrust <név>` | Megbízott hozzáadása / elvétele |

### Hol NEM lehet claimelni?

- **Frakció-territóriumban** (az a királyság földje) és **védett zónában** (spawn/város).
- Cserébe a **meteor-becsapódás** és az **elrejtett kincs** esemény is elkerüli a claimelt
  claimelt területet — a birtokod biztonságban van tőlük.
- **Raid alatt** a claim alapból véd, de szerver-beállítástól függően a jelentkezett támadók
  a claim-ládákat hadizsákmányként **kinyithatják** (lebontani akkor sem tudják).

## Admin parancsok (csak adminoknak)

- `/territory setcapital` — főváros kijelölése.
- `/territory claim` — terület lefoglalása.
- `/territory remove` — terület törlése.
- `/territory list` / `/territory info` — területek listája / infó.
- `/claim admin unclaim` — idegen claim törlése admin-jogon (a claimben állva).

---

➡️ Tovább: [Parancsok listája](14-parancsok.md) • [Vissza a tartalomhoz](README.md)
