# IceSMP release-dokumentációs lefedettség

<!-- icesmp-doc-id: release.documentation-coverage -->

## Eredmény

A csomag a dokumentált `master@4643ab53586f0c1ee7352df16dcd477013e6fad4`
forrásfát és az autoritatív `IceSMP-1.0-TESTING.jar` bináris baseline-t
egymástól elkülönítve térképezi fel.

| Lefedettségi cél | Eredmény | Autoritatív lista | Dokumentációs cél |
|---|---:|---|---|
| Root parancs | 68 / 68 | bootstrap-regisztráció | [COMMAND_REFERENCE](../reference/COMMAND_REFERENCE.md) |
| Funkcionális command route | 286 / 286 | handler- és dispatch-vizsgálat | [COMMAND_REFERENCE](../reference/COMMAND_REFERENCE.md) |
| Root alias | 79 / 79 | bootstrap-regisztráció | [COMMAND_REFERENCE](../reference/COMMAND_REFERENCE.md) |
| Routing alias | 93 / 93 | handler/dispatch alias-ág | [COMMAND_REFERENCE](../reference/COMMAND_REFERENCE.md) |
| Permission | 44 / 44 | runtime permissiongráf + dinamikus crate node | [PERMISSION_REFERENCE](../reference/PERMISSION_REFERENCE.md) |
| GUI-felület | 22 / 22 | holder + listener/click routing | [GUI_REFERENCE](../reference/GUI_REFERENCE.md) |
| Egyedi config/data path | 13 550 / 13 550 | scanner + nyers bundled YAML leaf-kiegészítés | [CONFIGURATION_REFERENCE](../reference/CONFIGURATION_REFERENCE.md) |
| Bundled üzenet-node | 1 269 / 1 269 | messages resource-ok | [CONFIGURATION_REFERENCE](../reference/CONFIGURATION_REFERENCE.md) |
| Nem üres üzenetkulcs-unió | 1 614 / 1 614 | resource + kódbeli fallback + használati hely | [MESSAGE_REFERENCE](../reference/MESSAGE_REFERENCE.md) |
| Data-driven kategória | 92 / 92 | enum/YAML/JSON/registry | [DATA_CONTENT_CATALOGUE](../reference/DATA_CONTENT_CATALOGUE.md) |
| Név szerint leltározott content ID | 2 435 / 2 435 | enum/YAML/JSON/registry | [DATA_CONTENT_CATALOGUE](../reference/DATA_CONTENT_CATALOGUE.md) |
| Main Java production komponens | 545 / 545 | source snapshot | [FEATURE_CATALOGUE](../reference/FEATURE_CATALOGUE.md), [evidence](RELEASE_EVIDENCE_MATRIX.md) |
| Lifecycle persistence owner | 34 / 34 | bootstrap save/load/recovery wiring | [FEATURE_CATALOGUE](../reference/FEATURE_CATALOGUE.md), [evidence](RELEASE_EVIDENCE_MATRIX.md) |
| Deployed root parancs | 30 / 30 | JAR bytecode | [deployed changelog](DEPLOYED_BUILD_TO_RELEASE_CHANGELOG.md) |
| Deployed alias | 56 / 56 | JAR descriptor/bytecode | [deployed changelog](DEPLOYED_BUILD_TO_RELEASE_CHANGELOG.md) |
| Deployed funkcióképesség | teljes bináris inventory | JAR class/resource/decompiler audit | [deployed changelog](DEPLOYED_BUILD_TO_RELEASE_CHANGELOG.md) |

## Miért nincs itt még egyszer 13 550 config-sor?

A coverage mátrix az egyetlen autoritatív céldokumentumra mutat, és annak
darabszámát, rendezett path-hashét és validációját ellenőrzi. Ugyanannak a
13 550 sornak a megkettőzése két, később eltérhető configreferenciát hozna
létre. A teljes pathlista ezért kizárólag a
[CONFIGURATION_REFERENCE.md](../reference/CONFIGURATION_REFERENCE.md)
dokumentumban él; ez a fájl a lefedettségi kapcsolatot és az eltérést rögzíti.
Ugyanez az elv érvényes a command-, permission-, GUI- és data-content listákra.

## Inventory → dokumentáció célmátrix

| Inventorycsoport | Azonosító/darab | Forrásbizonyíték | Dokumentáció | Státusz | Eltérés |
|---|---|---|---|---|---|
| Root command | `command.<root>` / 68 | `registerCommand` bootstrap | commandref | Dokumentálva | 38 új, 30 baseline-ban is volt |
| Funkcionális route | konkrét szintaxis / 286 | switch/if/helper/dispatch + tab | commandref | Dokumentálva | 0 feloldatlan dynamic/nested |
| Route-alias | közös végrehajtási ág / 93 | handler alias- és label-elemzés | commandref | Dokumentálva | A 79 root aliastól külön számolva |
| Root alias | `alias.<root>.<alias>` / 79 | regisztrációs aliaslista | commandref | Dokumentálva | 23 új, 56 baseline-ban is volt |
| Permission | `permission.<node>` / 44 | runtime `Permissions.register`, checkek, crate config | permissionref | Dokumentálva | 28 új; legacy alias külön jelölve |
| Config path | `config.<path>` / 13 550 | YAML leaf + kódbeli olvasó/fallback | configref | Dokumentálva | live override ismeretlen |
| Message path | 1 614 nem üres kulcs | bundled message YAML + kódbeli fallback | [message ref](../reference/MESSAGE_REFERENCE.md) | Dokumentálva | live override ismeretlen; 25 fallback drift név szerint jelölve |
| GUI | holder/család / 22 | holder + click/drag/close listener | GUI-ref | Dokumentálva | 8 új a baseline 14 családjához képest |
| Persistence | lifecycle owner / 34 | bootstrap load/save/recovery/disable | feature/evidence | Dokumentálva | +17 tulajdonos |
| Manager/service | component ID / minden production példány | bootstrap és source component inventory | feature/evidence | Besorolva | 0 néma komponens |
| Listener automatic feature | listener + handler | tényleges regisztráció és event handler | feature/evidence | Besorolva | 0 néma listener |
| Data category | 92 kategória | enum/YAML/JSON/registry | data catalogue | Dokumentálva | lore-only külön |
| Deployed JAR | class/resource/capability | autoritatív bináris audit | deployed changelog/evidence | Dokumentálva | source mapping `BINARY_ONLY` |
| Release source | 545 Java + resource tree | immutable source artifact | minden új release dokumentum | Dokumentálva | documented HEAD rögzített |

## Root command inventory

A teljes 68/68 root lista, aliasokkal, közönséggel, permissionnel,
konzolhasználattal, tab completionnel és deployed státusszal:
[COMMAND_REFERENCE — Root parancsok és aliasok](../reference/COMMAND_REFERENCE.md).

Az inventory invariánsai:

- minden root tényleges bootstrap-regisztrációból származik;
- a forrásban maradt, de nem regisztrált `MuteCommand` és `UnmuteCommand` nem
  aktív route, és külön ismert forrásmaradványként szerepel;
- command classnév önmagában nem számít regisztrációnak;
- a deployed 30 root egyikét sem jelöljük újként.

## Subcommand- és route inventory

A commandref 286 funkcionális route-ot és 93 route-aliast sorol. Beletartozik:

- közvetlen root-végrehajtás;
- első- és többszintű switch/if ág;
- közös dispatch mögötti subcommand;
- helper metódusba delegált út;
- opcionális argumentum által választott ág;
- GUI-ból is elérhető parancs;
- csak adminnak vagy konzolnak használható ág.

Feloldatlan dynamic route: **0**.  
Feloldatlan nested parent: **0**.

## Alias inventory

A 79 root alias mindegyike a saját root parancsánál szerepel; a 93 routing alias
a konkrét végrehajtási ág mellett található. A deployed baseline 56 root aliasa
mind megmaradt. Aliasütközés és néma alias: **0**.

## Permission inventory

A 44 node összetétele:

- 43 statikus runtime node;
- 1 bundled, crate-definícióból regisztrált dinamikus node:
  `icesmp.crate.ritka`.

A permissionref külön jelöli a parentet, runtime defaultot, legacy aliast,
érzékenységet, command/GUI/listener kapcsolatot és javasolt kiosztást.
Feloldatlan permissionkifejezés: **0** a végleges emberi interfészleltárban.

## Config- és message-inventory

| Elem | Darab | Bizonyítás |
|---|---:|---|
| Scannerrel osztályozott config/data node | 12 223 | default + reader + dynamic section |
| Nyers bundled YAML leaf-kiegészítés | 1 327 | teljes resource-fa |
| Egyedi dokumentált config/data path | 13 550 | deduplikált unió |
| Bundled message node | 1 269 | message resource-fa |
| Nem üres message-kulcsunió | 1 614 | 517 bundled+used + 717 bundled-only + 355 code-only fallback + 25 fallback drift |

A configreferencia minden sornál dokumentálja a fájlt/eredetet, jelentést,
típust, alapértéket vagy fallbacket, tartományt/enumot, hiányzó/hibás érték
viselkedést, reload/restart követelményt, közönséget, példát, veszélyt és
deployed státuszt.

Az [üzenetkulcs-referencia](../reference/MESSAGE_REFERENCE.md) külön, kereshető
táblában őrzi mind az 1 614 nem üres kulcsot, 1 181 statikusan feloldott
használati helyet, a forrásellenőrzött fallbackeket és a deployed resource
összevetést. A scanner egyetlen üres kulcsú false positive sora nincs
kulcsként dokumentálva, de a kizárás és bizonyítéka a referenciában szerepel.

### Nem blokkoló scannerjelzések feloldása

A végső report mód 1 120 figyelmeztetést őriz meg, miközben a strict kapu
blokkoló és `REVIEW_REQUIRED` eredménye nulla:

| Nyers scannerjelzés | Darab | Dokumentációs feloldás |
|---|---:|---|
| `MESSAGE_KEY_UNUSED` | 717 | `bundled-only`; nem állítjuk használhatatlannak, csak nincs statikusan feloldott `get`-hívás |
| `MESSAGE_KEY_MISSING_DEFAULT` | 365 | code-only fallback, drift és az üres false positive forrásszintű szétválasztása a message-refben |
| `MESSAGE_FALLBACK_DRIFT` | 26 | 25 valódi, név szerint jelölt drift + 1 üres scanner false positive |
| `RESOLVED_CONFIG_TYPE_VARIANCE` | 7 | a configreferencia pathonként indokolja |
| `KNOWN_CONFIG_DEFAULT_GAP` | 5 | a configreferencia fallbackkel és deploymentteendővel felsorolja |

Ezek egyikét sem számítjuk runtime átvételi bizonyítéknak, és egyik sem maradt
néma hiány. A strict kapu azért engedi tovább őket, mert az exact
manifest-/referenciafeloldás bizonyítékkal megőrzi őket.

Aktív AFK-path: `afk`, `afk.afk-after-seconds`, `afk.block-rewards`.
Jutalmazó AFK-zone/reward/bossbar aktív path: **0**.
Lay/crawl/stacking/player-NPC sitting aktív config: **0**.

## GUI inventory

A 22/22 GUI-felület a
[GUI_REFERENCE.md](../reference/GUI_REFERENCE.md) dokumentumban van.
Minden felülethez megnyitási mód, célközönség, permission, funkcionális slot/
tartomány, lapozás, visszalépés, read/edit, lezárt állapot, cleanup és deployed
státusz tartozik. Pusztán dekoratív filler slot nem funkció.

## Persistence inventory

A lifecycle inventory 34 tartós állapottulajdonost és 35 state fájlt azonosít.
A tulajdonosok a [FEATURE_CATALOGUE](../reference/FEATURE_CATALOGUE.md)
kapcsolódó rendszerénél szerepelnek; a settlement/recovery szempontból fontos
punishment, invsee escrow, offline teleport, crate, gazdaság, world regen,
faction/season, quest és builderhely-adatok az
[evidence mátrixban](RELEASE_EVIDENCE_MATRIX.md) is külön sorosak.

## Manager-, service- és listenerlefedettség

Az 545 production Java-komponens gépi inventoryja minden komponenst egy
közérthető feature-csoporthoz rendel. A feature-katalógus nem Java-osztálylistát
tesz a főszövegbe; a technikai component → feature megfeleltetés az evidence
függelék marker- és forrástáblájában marad.

Elvárt végállapot:

- nem besorolt production komponens: **0**;
- dokumentálatlan automatikus listenerfunkció: **0**;
- aktívként dokumentált out-of-scope komponens: **0**.

## Data-driven kategórialefedettség

A [DATA_CONTENT_CATALOGUE.md](../reference/DATA_CONTENT_CATALOGUE.md)
92 kategóriát és 2 435 név szerinti rekordot/ID-t tartalmaz. A bundled config
96/96 top-level szekciója kapott kategória- vagy „nem önálló tartalomregistry”
besorolást. A 0 elemű, runtime-ban admin által létrehozott kategóriák
(például parkourpálya vagy NPC-binding) nem maradtak ki: builder/admin
előkészítést igénylőként szerepelnek.

## Deployed JAR inventory

A binárisaudit az alábbiakat dolgozta fel:

- `paper-plugin.yml` és manifest;
- 371 class entry / 298 top-level class;
- 35 resource;
- 30 root, 56 root alias és 110 auditált route;
- 16 permission;
- 67 listener / 184 handler;
- 71 runtime komponens;
- 17 store;
- 14 GUI-család;
- 4 483 bundled config leaf és 906 message leaf;
- 984 data row 41 kategóriában.

Az audit nem következtetett kizárólag classnévből; bytecode/decompiler, string,
resource és routing bizonyítékot használt. A forráscommit nem volt exact módon
azonosítható, ezért a mapping `BINARY_ONLY`.

## Stale és ellentmondó állítások kezelése

Korrigált fő eltérések:

- a teaser 31 specializációja helyett a végleges enum 35;
- a régi 410 recept állítás helyett 438 professionrecept;
- a régi „~60 parancs” helyett 68 regisztrált root;
- a régi „18 GUI” helyett 22 bizonyított GUI-felület;
- az AFK-zone scope állítása helyett globális AFK zóna nélkül;
- a `/class givecatalyst` player-önkiszolgáló leírása helyett admin route;
- az aktív `/mute` a moderációs router; a régi `MuteCommand` nem regisztrált;
- az `/events chronicle` csak komment, nem aktív dispatch route.

## Záró coverage-kapu

A dokumentáció csak akkor nevezhető teljesnek, ha a végső branch ellenőrzése:

- 100% command, route, alias, permission, config, GUI, feature, deployed és
  data-content lefedettséget jelez;
- `REVIEW_REQUIRED` = 0;
- stale manifest/marker = 0;
- hiányzó vagy törött relatív link = 0;
- aktívként dokumentált out-of-scope funkció = 0;
- `./gradlew clean build --no-daemon --stacktrace` zöld;
- `python3 scripts/check_consistency.py` zöld;
- `git diff --check` zöld.

Az automatizált kapu eredménye nem helyettesíti a
[runtime acceptance checklistet](RELEASE_ACCEPTANCE_CHECKLIST.md).
