# Custom Model Data leltár (resource pack-hez)

Az IceSMP minden egyedi itemje **numerikus custom model data (CMD)** értéket kap a Paper
`CustomModelDataComponent` API-ján át (float-listaként, pl. `1001.0`). Az alábbi táblázat a
teljes, kódból ellenőrzött leltár — egy resource pack ezekre az érték+alapanyag párokra tud
modellt kötni.

## Teljes lista (CMD szerint rendezve)

| CMD | Alapanyag | Item | Megjelenített név | Forrás |
|-----|-----------|------|-------------------|--------|
| 1001 | PAPER | Piros valuta | Piros Token | `data/CurrencyType.java:6` |
| 1002 | PAPER | Kék valuta | Kék Token | `data/CurrencyType.java:7` |
| 1003 | PAPER | Semleges valuta | Semleges Token | `data/CurrencyType.java:8` |
| 1004 | PAPER | Sötét valuta | Sötét Token | `data/CurrencyType.java:9` |
| 4101 | GOLDEN_AXE | Mételytépő relikvia | Mételytépő | `managers/RelicManager.java` |
| 4201 | ELYTRA | Szárny-relikvia (Piros) | Főnix-szárny | `managers/RelicManager.java:102` |
| 4202 | ELYTRA | Szárny-relikvia (Kék) | Zúzmara-szárny | `managers/RelicManager.java:110` |
| 4203 | ELYTRA | Szárny-relikvia (Semleges) | Vándorszél | `managers/RelicManager.java:118` |
| 4204 | ELYTRA | Szárny-relikvia (Sötét) | Csontszárny | `managers/RelicManager.java:126` |
| 5201 | ENCHANTED_BOOK | Katalizátor — Varázsló | Mágikus Kódex | `items/CatalystItemFactory.java:52` |
| 5202 | GOAT_HORN | Katalizátor — Harcos | Harci Kürt | `items/CatalystItemFactory.java:56` |
| 5203 | RABBIT_HIDE | Katalizátor — Íjász | Vadásztarsoly | `items/CatalystItemFactory.java:59` |
| 5204 | FLINT | Katalizátor — Orgyilkos | Árnyékamulett | `items/CatalystItemFactory.java:62` |
| 5205 | OAK_SAPLING | Katalizátor — Druida | Vadon Talizmánja | `items/CatalystItemFactory.java:65` |
| 5206 | BELL | Katalizátor — Paladin | Szent Harang | `items/CatalystItemFactory.java:68` |
| 5207 | WITHER_SKELETON_SKULL | Katalizátor — Halállovag | Rúnakovácsolt Koponya | `items/CatalystItemFactory.java:71` |
| 5208 | TOTEM_OF_UNDYING | Katalizátor — Sámán | Ősök Totemje | `items/CatalystItemFactory.java:74` |
| 5209 | BAMBOO | Katalizátor — Szerzetes | Jáde Bot | `items/CatalystItemFactory.java:77` |
| 5210 | WHITE_CANDLE | Katalizátor — Pap | Szent Gyertya | `items/CatalystItemFactory.java:80` |
| 5211 | SOUL_LANTERN | Katalizátor — Boszorkánymester | Lélek Lámpás | `items/CatalystItemFactory.java:83` |
| 5212 | ENDER_EYE | Katalizátor — Démonvadász | Démonszem | `items/CatalystItemFactory.java:86` |
| 5213 | DRAGON_BREATH | Katalizátor — Idéző | Sárkány Esszencia | `items/CatalystItemFactory.java:89` |

Összesen **22 hozzárendelés, mind a 22 CMD-érték egyedi** (a Mételytépő korábban az 1001-en
osztozott a Piros Tokennel; 4101-re számoztuk át, ami a relics.yml-ben dokumentált érték).

## Tartományok

| Tartomány | Cél |
|-----------|-----|
| 1001–1004 | Frakció-valuták (PAPER) |
| 4101 | Mételytépő (GOLDEN_AXE) |
| 4201–4204 | Frakció-szárnyak (ELYTRA) |
| 5201–5213 | Kaszt-katalizátorok (kasztonként saját alapanyag) |

## CMD nélküli egyedi itemek (csak PDC-tag azonosítja őket)
- Befogó eszközök (`CaptureItemFactory`): LEAD ill. GHAST_TEAR
- Ostromágyú (`SiegeWeaponFactory`): TNT_MINECART

Ha ezekhez is modell kell, előbb CMD-t kell kapniuk a factory-jukban.

## Config/kód viszony
- Az `economy.yml` `model-data` és a `relics.yml` `custom-model-data`/`material` kulcsait a
  kód **nem olvassa** — a tényleges értékek a fenti Java-forrásokban vannak hardkódolva; a
  config-értékek dokumentációként vannak velük szinkronban tartva (a Mételytépő
  GOLDEN_AXE + 4101 mindkét helyen). Resource pack készítésénél ez a táblázat a mérvadó.
