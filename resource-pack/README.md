# IceSMP Hybrid Resource Pack

Minden item 64×64-es, részletes 2D inventory-ikont használ. A 3D geometria kizárólag hét,
világban megjelenített nagy tárgynál kapcsol be; a fegyverek, eszközök és felszerelések az
ellenőrzött, részletes 2D változatot tartják meg.

## Repository- és kiadási szabály

Ez a könyvtár a pack **kicsomagolt, szerkeszthető forrása**. Kész ZIP-et ne commitolj ide.
A `scripts/resource_pack.py` validálja a JSON/MCMeta- és PNG-fejléceket, majd rendezett
fájllistából, rögzített timestamp- és jogosultságadatokkal determinisztikus ZIP-et készít.

A `Publish resource pack to R2` workflow masterre kerülés után:

- `resource-packs/icesmp-<sha1>.zip` immutable kiadást tölt fel;
- `resource-packs/latest.zip` emberi, rövid cache-es aliast frissít;
- `resource-packs/manifest.json` géppel olvasható aktuális manifestet publikál;
- frissíti a plugin JAR-ba kerülő `resource-pack.properties` fallback URL/hash értékét.

A plugin kizárólag a hash-es immutable URL-t használja. A korábbi hash-es objektumokat nem
töröljük automatikusan: ezek biztosítják a gyors rollbacket, és az azonos tartalom ugyanarra
az objektumnévre épül, ezért nem hoz létre felesleges duplikátumot.
