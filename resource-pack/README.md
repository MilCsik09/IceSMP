# IceSMP Hybrid Resource Pack

Minden item 64×64-es, részletes 2D inventory-ikont használ. A 3D geometria kizárólag hét,
világban megjelenített nagy tárgynál kapcsol be; a fegyverek, eszközök és felszerelések az
ellenőrzött, részletes 2D változatot tartják meg.

## Custom wearable / armor presentation

A viselhető tárgyaknál **két külön render-identitás** létezik:

- `item-model`: az inventoryban, kézben és itemként használt `ITEM_MODEL` (`icesmp:<render-id>`);
- `equipment-asset`: a felvéve használt `EQUIPPABLE.assetId` (`icesmp:<render-id>`), amely az
  `assets/icesmp/equipment/<render-id>.json` equipment assetre mutat.

A kettő szándékosan nincs összemosva. Egy profession-result, unique item vagy nevesített loot
megadhatja mindkettőt. Az explicit `equipment-asset` mindig elsőbbséget élvez. Ha nincs megadva,
akkor kizárólag olyan itemnél használható a dokumentált **same-render-id fallback**, amelynek a
vanilla Materialja eleve rendelkezik `EQUIPPABLE` komponenssel: ilyenkor
`item-model: "icesmp:x"` → `equipment-asset: "icesmp:x"`.

A plugin a meglévő `EQUIPPABLE` komponenst `toBuilder()`-rel építi tovább, és csak az asset id-t
cseréli. Nem gyárt új equip-slotot, ezért a vanilla head/chest/legs/feet slot, equip sound,
swappability és a többi equip-tulajdonság megmarad. A presentation data componenteket minden
`ItemMeta`/affix módosítás **után** kell alkalmazni.

### Equipment asset és textúra-konvenció

Egy humanoid mellvért például:

```json
{
  "layers": {
    "humanoid": [
      { "texture": "icesmp:pelda_vert" }
    ]
  }
}
```

Fájlok:

- `assets/icesmp/equipment/pelda_vert.json`
- `assets/icesmp/textures/entity/equipment/humanoid/pelda_vert.png`
- leggings layer esetén: `textures/entity/equipment/humanoid_leggings/<id>.png`

A `scripts/resource_pack.py` build előtt ellenőrzi az összes equipment JSON layer-textúra
hivatkozását, az explicit config `equipment-asset` ID-kat, valamint az equippable
same-render-id fallbackeket. Hiányzó asset vagy texture publikálási hibát okoz.

### Jelenlegi 3D capability boundary

A vanilla Java resource-pack equipment rendszer a támogatott equipment **layer-típusokhoz**
(`humanoid`, `humanoid_leggings`, `wings`, stb.) textúrarétegeket köt. Ez kiváló saját armor-skin,
korona/fátyol/köpeny jellegű, a vanilla testgeometriára illeszkedő megjelenéshez, de az
`EQUIPPABLE.assetId` önmagában **nem egy általános, tetszőleges új player-armor mesh/bone API**.
Az inventory `ITEM_MODEL` 3D geometriája sem válik automatikusan viselt player-geometriává.

Ezért a jelenlegi kód nem színlel „3D armor támogatást”. A központi
`WearablePresentation` határ és a stabil `<render-id>` névkonvenció azért készült, hogy egy későbbi
valódi 3D wearable renderer ugyanarra az identitásra ráépülhessen. Ilyen jövőbeli renderer lehet
külön kliensoldali/modded render-megoldás, vagy egy szerveroldali entity/display-alapú vizuális
réteg; ezek külön technikai projektet, lifecycle/szinkron/LOD/visibility kezelést igényelnek, és
nem részei a mostani vanilla equipment asset támogatásnak.

## Repository- és kiadási szabály

Ez a könyvtár a pack **kicsomagolt, szerkeszthető forrása**. Kész ZIP-et ne commitolj ide.
A `scripts/resource_pack.py` validálja a JSON/MCMeta- és PNG-fejléceket, az equipment assetek
referenciáit, majd rendezett fájllistából, rögzített timestamp- és jogosultságadatokkal
determinisztikus ZIP-et készít. Ez a README repository-dokumentáció, ezért szándékosan kimarad a
kliensnek készülő ZIP-ből, és a módosítása önmagában nem változtatja meg a kiadási hash-t.

A `Publish resource pack to R2` workflow masterre kerülés után:

- `resource-packs/icesmp-<sha1>.zip` immutable kiadást tölt fel;
- `resource-packs/latest.zip` emberi, rövid cache-es aliast frissít;
- `resource-packs/manifest.json` géppel olvasható aktuális manifestet publikál;
- frissíti a plugin JAR-ba kerülő `resource-pack.properties` fallback URL/hash értékét.

A plugin kizárólag a hash-es immutable URL-t használja. A korábbi hash-es objektumokat nem
töröljük automatikusan: ezek biztosítják a gyors rollbacket, és az azonos tartalom ugyanarra
az objektumnévre épül, ezért nem hoz létre felesleges duplikátumot.

## Manuális GitHub Actions futtatás

Az Actions → **Publish resource pack to R2** → **Run workflow** menüben a `master` ág
kiválasztása után három mód érhető el:

- `validate-only`: csak a tooling tesztje és a determinisztikus ZIP-build fut; R2-t nem érint;
- `r2-preflight`: ellenőrzi a secreteket és a bucket-hozzáférést, feltölti és S3-on visszaellenőrzi
  az immutable objektumot, de nem módosítja a `latest.zip`, manifest vagy plugin metadata állapotát;
- `publish`: teljes production publikálás, kizárólag a `master` ágról. Ellenőrzi a custom-domain
  DNS-feloldását, a publikus ZIP SHA-1 értékét, majd frissíti az aliast, manifestet és metadatafájlt.

A `public_base_url` alapértéke `https://assets.icesmp.taliann.dev`. Teljes publikálás előtt ezt
a domaint a Cloudflare R2 `icesmp` bucket **Settings → Custom Domains** részében aktívként kell
hozzárendelni. A `r2-preflight` mód akkor is használható a kulcsok és az S3-hozzáférés külön
tesztelésére, ha a publikus custom domain még nem aktív.
