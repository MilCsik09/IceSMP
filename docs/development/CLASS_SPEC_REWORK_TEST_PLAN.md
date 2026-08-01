# Kaszt- és specializáció-rework — Profile v2 regressziós tesztterv

## Tesztelési elv

A Profile v2 kapuja dependency-free Java regressziós suite, repository-tooling tesztek és valódi
Gradle build. Source-string guard önmagában nem elfogadási bizonyíték. A suite ugyanazokat a publikus
domain-, codec-, repository- és alkalmazási API-kat hajtja végre, mint a runtime adapterek.

Minden hibaágban ellenőrizni kell az autoritatív tartós profilt, a cache-t, a kompenzációt és a
runtime-blokkot is; nem elég az exception típusa.

## Domain és invariánsok

Kötelező futó esetek:

- üres, schema 2 profil létrehozása két `EMPTY` slottal;
- pontosan egy aktív slot; két aktív slot elutasítása;
- duplikált spec és rossz parent class elutasítása;
- második slot elutasítása `secondSpecUnlocked=false` mellett;
- rejtett adatot hordozó `EMPTY` slot elutasítása;
- `SEALED` és `MIGRATION_REVIEW` loadout nem aktiválható;
- `MIGRATION_REVIEW` profil normál runtime-ja blokkolt;
- `CORRUPT_QUARANTINE` profil normál mutationje blokkolt;
- általános class reset nem töröl review/quarantine adatot;
- mastery rang 0–10 és nem negatív XP;
- Soulforge kizárólag `necromancer` loadoutban;
- roster kizárólag a négy engedélyezett spec saját névterében;
- tartós modellben nincs Bukkit player/entity vagy élő entity UUID.

## Codec

A codec-fixture a profil minden mezőjét kitölti, beleértve a két loadoutot, doktrínákat, masteryt,
signature-t, capstone-t, rostert, mechanikai és migrációs állapotot. Kötelező esetek:

- teljes round-trip és objektumegyenlőség;
- byte-for-byte determinisztikus output eltérő map/set beillesztési sorrendből is;
- magic-, codec-version- és checksum-hiba;
- csonkolt payload és trailing adat;
- ismeretlen enum;
- ismételt mapkulcs és normalizálás utáni kulcsütközés;
- hibás UTF-8;
- túlméretes string, lista, map, set és teljes payload;
- negatív és maximumnál nagyobb hosszmező;
- Java object serialization fejlécének elutasítása.

A corrupt fixture eredeti byte-jai quarantine-ba kerülnek, és nem jön létre üres helyettesítő profil.

## Repository, CAS és cache

- első mentés kizárólag `expected=-1`, `next.revision=0`;
- normál `n → n+1` mentés;
- stale expected revision és kihagyott revision elutasítása;
- két párhuzamos mutationből soros, determinisztikus eredmény;
- régebbi async completion nem publikálhat újabb cache fölé;
- injected write failure után a régi tartós profil és cache marad autoritatív;
- hibás mentés class/spec session blockot hoz létre;
- quarantine mentés és változatlan payload-visszaolvasás;
- cache invalidation után tartós reload;
- quit `flush(player)` és disable `flushAll`;
- disable után új mutation/task elutasítása.

Az I/O fault-fixture nem hamisíthat sikeres írást: a tesztnek a tényleges repository write-portot kell
hibára állítania.

## Migráció

- érvényes kaszt/spec → slot 1 aktív, slot 2 üres, revision 0;
- spec nélküli játékos;
- ugyanazon snapshot második migrációja nem duplikál adatot vagy grantet;
- ismeretlen és parent classhoz nem illő spec → `MIGRATION_REVIEW`;
- kiválasztott és kedvenc spell megőrzése;
- mind a négy companion-roster helyes besorolása;
- élő entity UUID eldobása;
- Soulforge Nekromantával és orphaned Soulforge Nekromanta nélkül;
- normalizált legacy kulcsütközés;
- korlátozott ismeretlen mechanikai snapshot megőrzése;
- persistence-hiba után nincs v2 sikerjel és a legacy adat érintetlen;
- retry-versenyben pontosan egy `-1 → 0` commit.

## DARK seal/unseal

Mind az öt DARK specet külön fixture-rel kell futtatni. A mátrix lefedi:

- faction-, sinner- és quest-seal;
- aktív slot kiürül, automatikus másik-spec aktiválás nincs;
- csak `SPEC:*` spell provenance kerül visszavonásra;
- pet/minion/form/transient cleanup pontosan egyszer fut;
- mastery, doctrine, signature, capstone és roster változatlan;
- a megfelelő kapu helyreállása `INACTIVE` állapotra unsealel;
- másik okkal történő unseal elutasított;
- admin-, persistence- és quarantine-seal nem oldódik gate eventtől;
- seal-persist hiba session blockot és fail-safe runtime cleanupot ad.

## Companion és Soulforge

- egyik loadout rosterét a másik nem olvashatja és nem módosíthatja;
- `beast_master`, `necromancer`, `unholy` és `demonologist` névterei nem cserélhetők fel;
- rituális `unholy`/`demonologist` társat az általános summon út elutasít;
- logout, death, world change, seal és reset determinisztikusan takarítja az élő runtime-ot;
- Soulforge csak aktív, használható Nekromanta loadoutból érhető el;
- párhuzamos Soulforge-upgrade egy rangot és egy költséget számol el;
- mentési hibánál a költség visszajár és rang nem nő.
- tartós rangcommit utáni runtime-hibánál nincs szilánkrefund és a session blokkolt.

## Respec, admin reset és provenance

- specből kapott grant törlődik, quest/talent/class/admin grant megmarad;
- profilmentési hibánál teljes refund;
- tartós commit utáni runtime-hibánál nincs refund, a session blokkolt;
- azonos retry token nem von le kétszer;
- admin reset mentési hibánál nem jelent sikert;
- class PDC/legacy mező nem törlődik profile commit előtt;
- review/quarantine profil resetje elutasított és snapshotja változatlan;
- részleges spell/pet/runtime aktiváció nincs hiba után.

## Lifecycle és Folia

Az adaptertesztek fake scheduler ownershiptal bizonyítják:

- join: flag nélkül legacy-only; flaggel load vagy migrate;
- activation-pending alatt a betöltött profil sem ad gameplay/spec/pet/mechanika readet;
- ugyanazon sessionben nincs egyszerre legacy és v2 spec runtime;
- player/PDC snapshot csak player scheduleren;
- fájl-I/O nem player/entity region threaden;
- gate/spell reconcile csak sikeres load/persist után;
- quit cleanup után `flush(player)` és cache cleanup;
- disable leállítja az új taskokat, cleanupol és `flushAll`-t hív;
- retired/rejected scheduler callback single-winner fallbackje;
- globális cache nem tart hosszú életű `Player` vagy `Entity` referenciát.

Valódi Folia stagingen külön kézi eset a cross-region pet/minion cleanup, process-kill, ENOSPC és
permission-denied fault injection; ezeket a dependency-free suite nem állíthatja bizonyítottnak.

## Lokális elfogadási kapu

A végleges diffen fut:

```bash
./gradlew clean build --no-daemon --stacktrace
python3 scripts/check_consistency.py
python3 scripts/check_markdown_links.py --root .
python3 -m unittest discover -s scripts/tests -p "test_*.py"
python3 scripts/generate_repository_inventory.py --root . --output build/repository-inventory --mode report
python3 scripts/check_documentation_coverage.py --root . --inventory build/repository-inventory/repository-inventory.json --output build/repository-inventory --mode report
git diff --check
```

A Gradle `check` feladatnak közvetlenül függenie kell a Profile v2 regression suite-tól. A CI logban
külön, stabil pass-marker bizonyítja, hogy a suite nem maradt ki. A repository inventory artifactban
nem jelenhet meg új `UNCLASSIFIED_COMPONENT`, `UNDOCUMENTED_INVENTORY_ITEM`, command/permission
contract drift vagy új review-required Profile v2 finding.

## Remote elfogadási kapu

A stacked draft PR-en zöld kell legyen:

- `IceSMP CI / Consistency delta`;
- `IceSMP CI / Java 21 build and regressions`;
- `Repository Docs Inventory / Repository and documentation inventory`;
- minden branch protection által előírt további check.

Hibánál a konkrét job log gyökérokát ugyanazon feature branchen kell javítani és újraellenőrizni.
