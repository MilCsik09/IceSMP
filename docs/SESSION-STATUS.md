# Fejlesztési állapot — modernizációs kör (2026-07-23)

Ez a dokumentum a `claude/ideas-lore-plugin-qt35xf` feature-branch aktuális állapotát
foglalja össze: mi készült el, mi a mérvadó build-helyzet, és mi maradt függőben.
A részletes, tételes háttér a `docs/ideas/BACKLOG.md` P4–P8 szekcióiban él.

## Build-helyzet (mérvadó)

- **A valódi Gradle-build ZÖLD.** `/opt/gradle/bin/gradle build --console=plain --no-daemon`
  (rendszer-Gradle 8.14.3; a projekt-wrapper 9.4.1-e a GitHub Releases-ről tiltott).
- A kör elején a valódi build **17 latens fordítási hibát** tárt fel, amiket a sandbox-javac
  szűrő elrejtett (`does not override`, `cannot find symbol: class …`, és a mögöttük bújó
  definite-assignment hibák). Mind javítva — a projekt most valóban fordul a valódi Folia
  API ellen. A CLAUDE.md Build&verify szekciója ezt a vakfolt-listát rögzíti.
- `python3 scripts/check_consistency.py` → **0 FAIL** (a checker új drift-védelemmel bővült,
  lásd lentebb).

## Ami elkészült ebben a körben

### P7 — modern data-component réteg (Paper 1.20.5+)
- **`items/ItemDataFactory`** — központi data-component helper: CONSUMABLE/FOOD, ITEM_MODEL,
  USE_COOLDOWN, TOOLTIP_DISPLAY. Kritikus invariáns betartva: a data-komponenseket MINDIG a
  `setItemMeta(...)` UTÁN, utolsóként alkalmazzuk (a meta-round-trip különben törli).
- **Signature-ételek CONSUMABLE-migrációja** — a 7 K6 frakció-étel natív CONSUMABLE/FOOD
  komponenst kapott, `food_v2` markerrel a dupla-buff/regresszió ellen (a legacy listener-út
  a migrált ételekre már nem fut).
- **+21 Szakács fogyaszthatók** — receptvezérelt `result.consumable` spec (animáció, idő,
  táplálás, telítettség, effektek) — ételek, italok, tájfogások. (Az Alkímista szakma
  megmarad a POTION-alapú főzeteknél; a custom-item ételek/italok a Szakácshoz kerültek.)
- **USE_COOLDOWN** — a katalizátor-item cooldown-csoportja komponens-alapú (a korábbi
  Material-alapú vér-cooldown fix).
- **TOOLTIP_DISPLAY** — az affix-gear elrejti a nyers ATTRIBUTE_MODIFIERS blokkot.

### CMD → ITEM_MODEL migráció (KÉSZ, 100%)
- Az összes custom/unique item **integer CustomModelData helyett ITEM_MODEL komponenst**
  visel (`icesmp:<id>`). 4 kötegben, mindegyiknél zöld valódi build:
  147 recept-tárgy · 80 egyedi anyag · 8 factory-item (siege/blueprint/pénz/katalizátor/
  valuta/befogó) · relikvia/kulcs/loot/bolt/spell/sétapálca.
- Nem-literál CMD-k is migrálva (spell `balanceInt("custom-model-data", …)`, sétapálca-ternár,
  recept-változó) — code-grep tárta fel, miután a checker literál-fókusza átengedte őket.
- **Checker drift-védelem (3b szekció):** a `check_consistency.py` mostantól FAIL-el bármely
  `setCustomModelData`-ra ÉS `custom-model-data:` config-kulcsra — visszaesés kizárva.
- A resource-pack artefaktum törölve (külső forrásban készül; a pack-készítő az
  `assets/icesmp/items/<id>.json`-t szállítja).

### P4 — bootstrap + dialog + környezeti damage
- **`managers/DialogService`** — natív szerver-oldali párbeszéd-ablakok (Dialog API,
  Paper 1.21.6+): `showNotice` + `showConfirm`. Első fogyasztó: üdvözlő-dialóg.
  A teljes P6 dialog-konverziós terv erre épül.
- **P4e `icesmp:rontas`** — környezeti damage-type a korrupt-mag aurában
  (`CorruptionAuraListener`, per-player scheduler-hop, lore-hű halál-üzenet).
- **Bootstrap:** registry-regisztráció a `compose` eseményre (nem `freeze` — a 1.21.11
  provider csak compose-t szolgál ki).

### P5 — datapack-réteg
- **`managers/AdvancementService`** — 7 advancement a stabil `loadAdvancement` úton
  (P5a), + 2 rejtett lore-advancement valódi grant-pontokkal (P5b). `advancements.enabled`
  kapuval.
- P5n (timeline-registry) felderítés lezárva → **elvetve** (nem illik a profilba).

### P8 — HUD/felület
- **P8e alacsony-HP piros vignetta** — per-player WorldBorder-trükk (gameplay-semleges),
  `hud.low-hp-vignette.{enabled,threshold-percent}` kapcsolóval.

## Ami függőben van

### P7 — új tartalom + balansz (tulaj által későbbre sorolva)
- Új-tartalom itemek: P7d DEATH_PROTECTION (totem-relikvia), P7e GLIDER (sikló-köpeny),
  P7f BLOCKS_ATTACKS (parry-relikvia), P7a EQUIPPABLE (frakció-kozmetika, pack kell),
  P7j PROFILE (fej-skinek), P7g TOOL/WEAPON.
- Attribútum-itemek: P7l SCALE (ajánlott elsőnek), P7m mozgás-identitás, P7n reach,
  P7o WAYPOINT, P7p Interaction-entity, P7q HappyGhast.

### P6 — dialog-konverziók (a DialogService-alap kész)
- P6a Quest Builder (ajánlott — megalapozza az input-mintát), P6b ConfigMenu ingame,
  P6c quest-NPC beszéd, P6d–f megerősítések, P6g–j input-alfolyamok, P6k–m infó-ablakok.

**Ajánlott következő sorrend:** 1. P7l SCALE (olcsó, nagy érték) · 2. P6a Quest Builder.

## Konvenciók, amiket a kör betartott
- Minden custom item ITEM_MODEL; új itemnél integer CMD SEHOL.
- Data-komponensek mindig a meta-műveletek UTÁN.
- Minden változás ugyanabban a commitban propagált a docsba; tükör-push a IceSMPGuides-ra.
- Push előtt: valódi build (ha elérhető) + `check_consistency.py` 0 FAIL + YAML-validáció.
