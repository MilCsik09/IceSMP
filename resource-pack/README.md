# IceSMP Hybrid Resource Pack

Minden item 64×64-es, részletes 2D inventory-ikont használ. A 3D geometria kizárólag hét,
világban megjelenített nagy tárgynál kapcsol be; a fegyverek, eszközök és felszerelések az
ellenőrzött, részletes 2D változatot tartják meg.

## Repository- és kiadási szabály

Ez a könyvtár a pack **kicsomagolt, szerkeszthető forrása**. Kész ZIP-et ne commitolj ide.
A `scripts/resource_pack.py` validálja a JSON/MCMeta- és PNG-fejléceket, majd rendezett
fájllistából, rögzített timestamp- és jogosultságadatokkal determinisztikus ZIP-et készít.
Ez a README repository-dokumentáció, ezért szándékosan kimarad a kliensnek készülő ZIP-ből,
és a módosítása önmagában nem változtatja meg a kiadási hash-t.

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
