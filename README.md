# IceSMP

Az **IceSMP** egy Folia-alapú Minecraft 1.21.11 plugin Java 21-hez. Egy
fantasy SMP szerver játékmenet-rendszereit fogja össze: frakciók, kasztok,
specializációk, képességek, szakmák, gazdaság, küldetések, események,
területek, relikviák, társak és adminisztráció.

## Dokumentáció

Az embernek szánt, mérvadó dokumentáció öt kézikönyvből áll:

| Kézikönyv | Mire való? |
|---|---|
| [Minden funkció](docs/FEATURES.md) | Az aktív, részleges, letiltott és tervezett rendszerek teljes katalógusa |
| [Legújabb változások](docs/LATEST_CHANGES.md) | A jelenleg futó JAR és az integrált release közötti eltérések, plugin-rollout |
| [Játékos kézikönyv](docs/PLAYER_GUIDE.md) | Kezdés, gameplay, parancsok és játékosoldali korlátok |
| [Builder kézikönyv](docs/BUILDER_GUIDE.md) | Világhelyszínek, crate-ek, NPC-k, régiók és builder-checklistek |
| [Admin kézikönyv](docs/ADMIN_GUIDE.md) | Admin/moderáció, parancsok, permissionök, GUI-k, config, recovery és playtest |

A teljes command-, route-, alias-, permission-, config-, message- és
komponensleltárt a **Repository Docs Inventory** GitHub Actions workflow
letölthető artifactja készíti. Ez gépi ellenőrzési réteg, nem hatodik
kézikönyv.

Kiegészítő belső források:

- [architektúra](docs/ARCHITECTURE.md);
- [resource pack modelljegyzék](docs/RESOURCE_PACK_CMD.md);
- [lore-kódex](docs/LORE.md) és [technikai lore-megfeleltetés](docs/LORE_REFERENCE.md);
- [roadmap](ROADMAP.md) és [ötlet-backlog](docs/ideas/BACKLOG.md).

## Jelenleg futó baseline

Az auditált szerver-JAR az `IceSMP-1.0-TESTING.jar`, SHA-256:
`da039f0e2bdf0e67b216ce82d7d3fe3b6da0af6e18f6fa175762c37493795a05`.
A resource- és bytecode-összevetés alapján a legvalószínűbb forrásállapot
`775d9e247be675db1c7c9beaaecf4a90349bfcd3` (2026. július 12.).
Ez `HIGH_CONFIDENCE`, nem exact mapping, mert a JAR nem tartalmaz Git SHA-t
vagy valódi build-időt. A részletes bizonyíték a
[legújabb változásokban](docs/LATEST_CHANGES.md#a-július-1213-i-futó-verzió-azonosítása)
olvasható.

## Build és ellenőrzés

Követelmény: Java 21 és elérhető Gradle-dependency repositoryk.

```bash
./gradlew clean build --no-daemon --stacktrace
python3 scripts/check_consistency.py
python3 -m unittest discover -s scripts/tests -p 'test_*.py' -v
python3 scripts/generate_repository_inventory.py \
  --root . --output build/repository-inventory --mode strict
python3 scripts/check_documentation_coverage.py \
  --root . \
  --inventory build/repository-inventory/repository-inventory.json \
  --output build/repository-inventory \
  --mode strict
python3 scripts/check_markdown_links.py --root .
git diff --check
```

A zöld build kód- és regressziós bizonyíték. Production rollout előtt az
[admin kézikönyv acceptance checklistjét](docs/ADMIN_GUIDE.md#release-acceptance-checklist)
is végig kell futtatni staging/Folia környezetben.
