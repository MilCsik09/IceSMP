#!/usr/bin/env python3
from pathlib import Path

path = Path("docs/RESOURCE_PACK_CMD.md")
text = path.read_text(encoding="utf-8")
if "## Wearable / equipment render-szerződés" in text:
    raise SystemExit(0)

anchor = (
    "Minden tétel négy fogódzót ad a művésznek: **Alap-item** (a vanilla sziluett-referencia), "
    "**Ábrázolás** (mit ábrázoljon), **Színvilág** (paletta + akcent) és **Hangulat / lore** "
    "(a világon belüli érzet).\n\n"
)
if text.count(anchor) != 1:
    raise SystemExit("RESOURCE_PACK_CMD wearable-doc anchor missing or ambiguous")

section = r'''## Wearable / equipment render-szerződés

A custom armor és wearable itemeknél két külön azonosítót kell kezelni:

- `item-model`: az inventory/kéz `ITEM_MODEL` komponense (`icesmp:<render-id>`), az
  `assets/icesmp/items/<render-id>.json` item modellhez;
- `equipment-asset`: a felvéve használt `EQUIPPABLE.assetId` (`icesmp:<render-id>`), az
  `assets/icesmp/equipment/<render-id>.json` equipment assethez.

Profession recipe resultnál tehát ez a teljes, explicit forma:

```yaml
result:
  material: DIAMOND_CHESTPLATE
  item-model: "icesmp:pelda_vert"
  equipment-asset: "icesmp:pelda_vert"
```

Named loot és `profession-materials` definíció ugyanígy használhat `equipment-asset` mezőt.
Az explicit mező mindig elsőbbséget élvez. Ha hiányzik, kizárólag olyan vanilla Materialnál,
amely eleve `EQUIPPABLE`, használható a determinisztikus same-render-id fallback:
`item-model: "icesmp:x"` → `equipment-asset: "icesmp:x"`. A pack-validator ezt a fallbacket
is ellenőrzi, tehát hiányzó `assets/icesmp/equipment/x.json` nem juthat át a CI-n.

A relikvia-szárnyak ugyanezt a stabil render identityt használják (`icesmp:relic_<id>`): az
inventory item-model és az `assets/icesmp/equipment/relic_<id>.json` wings asset azonos néven
kapcsolódik, de a runtime továbbra is két külön data componentként kezeli őket.

Az equipment JSON layer textúrája például:

```json
{
  "layers": {
    "humanoid": [
      { "texture": "icesmp:pelda_vert" }
    ]
  }
}
```

Ehhez a textúra: `assets/icesmp/textures/entity/equipment/humanoid/pelda_vert.png`;
leggings layernél a könyvtár `humanoid_leggings`, elytra-szárnynál `wings`.

### Vanilla 3D-határ

A jelenlegi Java resource-pack equipment rendszer a rögzített equipment layer-típusokon
(`humanoid`, `humanoid_leggings`, `wings`, stb.) renderel textúrarétegeket. Az
`EQUIPPABLE.assetId` nem általános, tetszőleges új játékos-armor mesh/bone definíció, és az
inventory `ITEM_MODEL` 3D geometriája nem kerül automatikusan a játékos testére. Emiatt a
plugin nem színlel valódi új 3D armor-geometriát.

A `WearablePresentation` a központi bővítési pont. A stabil `<render-id>` legyen a jövőbeli
renderer identitása is; egy későbbi kliensoldali/modded vagy külön entity/display-alapú 3D
wearable réteg így ugyanarra a tartalom-azonosítóra épülhet, a jelenlegi vanilla equipment
assetek újraírása nélkül.

'''
path.write_text(text.replace(anchor, anchor + section), encoding="utf-8")
