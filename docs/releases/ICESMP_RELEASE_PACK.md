# IceSMP integrált release pack

<!-- icesmp-doc-id: release.print-pack -->

> Nyomtatható és PDF-be exportálható, továbbítható összefoglaló.
> Dokumentált release: `master@4643ab53586f0c1ee7352df16dcd477013e6fad4`
> Audit: 2026-07-30

## Dokumentumhasználat

Ez a csomag egy helyen foglalja össze a release-t, a szerveren jelenleg futó
buildhez képesti változásokat, az aktív funkciókat, az admin- és builderteendőket,
a fontos parancsokat, a külső pluginok állapotát, az átvételi teszteket és az
ismert korlátokat.

A részletes technikai táblák külön referenciákban maradnak:

- [teljes parancsreferencia](../reference/COMMAND_REFERENCE.md);
- [teljes permissionreferencia](../reference/PERMISSION_REFERENCE.md);
- [minden configpath](../reference/CONFIGURATION_REFERENCE.md);
- [minden GUI és művelete](../reference/GUI_REFERENCE.md);
- [adatvezérelt tartalom](../reference/DATA_CONTENT_CATALOGUE.md);
- [bizonyíték- és lefedettségi mátrix](RELEASE_EVIDENCE_MATRIX.md).

## Release summary

Az IceSMP 1.21.11-es integrált tesztalapja Java 21-et, Gradle 9.4.1 wrappert és
Folia API `1.21.11-R0.1-SNAPSHOT`-ot használ. A dokumentált forrás
`4643ab53586f0c1ee7352df16dcd477013e6fad4`; a rögzített
`49cb32740629f3d91a08b753436f3e16d33a494d` Git-baseline óta 417 commit
változását tartalmazza.

A szerveren jelenleg futó baseline a csatolt `IceSMP-1.0-TESTING.jar`:

| Mező | Érték |
|---|---|
| SHA-256 | `da039f0e2bdf0e67b216ce82d7d3fe3b6da0af6e18f6fa175762c37493795a05` |
| Pluginverzió | `1.0-TESTING` |
| API-verzió | `1.21.11` |
| Folia | `true` |
| Forrásmapping | `BINARY_ONLY` |

A JAR nem tartalmaz commit SHA-t, ezért közvetlen bináris audit készült róla.
Az élő külső config és adatfájl nem állt rendelkezésre; opcionális funkciónál a
release pack képességet és bundled defaultot dokumentál, nem bizonyítja az élő
engedélyezettséget.

## A deployed buildhez képesti változás

| Terület | Deployed JAR | Release | Változás |
|---|---:|---:|---|
| Root parancs | 30 | 68 | +38 |
| Alias | 56 | 79 | +23 |
| Bizonyított permission | 16 | 44 | +28 |
| GUI-holder/család | 14 | 22 | +8 |
| Persistent owner | 17 | 34 | +17 |

A baseline összes root parancsa és aliasa megmaradt.

### Legfontosabb újdonságok

- **Natív moderation/admin suite:** punishment, history, report, PM/reply,
  SocialSpy, vanish, inventory/ender chest read/edit, escrow, offline teleport,
  GUI, audit és recovery.
- **Natív MOTD:** default variáns, `TIME`/`RANDOM` kiválasztás,
  eseményprioritás, vanished count és ikonmódok.
- **Sit-only:** támogatott block geometry, foglalás és lifecycle cleanup.
- **Natív crate:** fizikai hely, key, GUI, mass-open, több rewardtípus,
  settlement, audit és recovery.
- **Globális AFK:** automatikus/kézi állapot, tablista és reward gate.
- **Natív mini-plugin megfelelő:** Warden-XP és player/mob farmland-trample
  védelem.
- **Tartalmi bővülés:** kaszt, specializáció, quest, profession, crafting,
  relikvia, politika, világmechanika, event és boss.

### Megszűnt vagy tudatosan elvetett scope

- jutalmazó AFK-zóna, zónaidő, payout és AFK bossbar;
- lay;
- crawl;
- stacking;
- másik játékos vagy NPC megülése;
- teljes GSit- és teljes TAB-paritás.

Az AFK-zóna nem a deployed JAR meglévő képességének elvesztése: a deployed
binárisban sem volt. A fejlesztés közben megjelent köztes scope került ki,
miközben a globális AFK megmaradt.

## Teljes funkciókatalógus — vezetői nézet

| Funkciócsoport | Státusz | Kinek fontos? | Rollout feltétel |
|---|---|---|---|
| Kaszt, specializáció, spell, talent | Aktív, játékos | játékos, tartalom, teszt | unlock/cast/balance |
| Frakció, politika, szezon | Aktív | játékos, admin, eventes | restart/offline/treasury |
| Gazdaság, bank, market, auction | Aktív | játékos, admin | tranzakció és limit |
| Quest, achievement, közösségi cél | Aktív, data-driven | játékos, builder | NPC/hely/binding |
| Profession, craft, blueprint | Aktív, data-driven | játékos, tartalom | recipe/PDC/RP |
| Unique item, relikvia, rituálé | Aktív, builderigényes | játékos, builder | helyszín és reclaim |
| Territory, claim, dungeon | Aktív, builderigényes | builder, admin | világbejárás |
| World regen és world policy | Aktív/configos | builder, üzemeltető | fault/restart/physics |
| Event, boss, caravan | Aktív/configos | eventes, builder | spawn/reward/cleanup |
| Pet, party, céh, parkour | Aktív/részben előkészítendő | játékos, builder | lifecycle és hely |
| Moderáció/admin | Rollout-kapu alatt | moderátor, admin | teljes acceptance |
| MOTD/tablista/HUD | Rollout-kapu alatt | admin, kommunikáció | valós client/server ping |
| Sit-only | Rollout-kapu alatt | játékos, builder | geometry/lifecycle |
| Crate | Rollout-kapu alatt | játékos, admin, builder | fault injection |
| Globális AFK | Aktív/configos | játékos, admin | timeline/reward gate |
| AFK-zóna/lay/crawl/stacking | Elvetett/out of scope | mindenki | negatív bizonyíték |

### Bizonyított adatvezérelt tartalom

| Kategória | Darab |
|---|---:|
| Kaszt | 13 |
| Specializáció | 35 |
| Profession | 8 |
| Profession-specializáció | 16 |
| Quest | 160 |
| Achievement | 21 |
| Közösségi cél | 7 |
| Objective-típus | 17 |
| Professionrecept | 438 |
| Blueprintként tanulható recept | 54 |
| Professionanyag | 81 |
| Crate | 2 |
| Relikvia | 6 |
| Rituálé | 21 |
| Frakció / valuta | 4 / 4 |
| Territorytípus | 6 |

A 420 spell-balance ID konfigurációs képességet jelent; nem állítjuk, hogy
mindegyik külön runtime spellként regisztrált. A bundled quest-NPC, territory és
parkour hivatkozás sem bizonyít kész élő bindingot vagy helyszínt.

## Admin- és moderátori rész

### Kötelező működési elvek

1. A read és edit permissiont mindig külön kezeld.
2. A vanish használata és a vanished játékos látása külön jog.
3. A punishment „sikerét” login, expiry, restart és history oldalról is ellenőrizd.
4. Inventory editnél audit, reconnect és escrow recovery szükséges.
5. Futó szervernél ne javíts kézzel tartós állapotfájlt mentés nélkül.
6. Crate vagy currency bizonytalan side effectjét ne zárd automatikusan sikeresre.
7. Minden hibajegyhez log, UUID, időpont, config snapshot és reprodukció kell.

### Fontos moderációs parancsok

| Cél | Szintaxis/alias |
|---|---|
| Warning | `/warn <player> [reason]` |
| Kick | `/kick <player> [reason]` |
| Mute | `/mute <player> [duration] [reason]` |
| Unmute | `/unmute <player> [reason]` |
| Ban | `/ban <player> [reason]` |
| Temporary ban | `/tempban <player> <duration> [reason]` |
| Unban | `/unban <player> [reason]` |
| Előzmény | `/history <player> [page]` |
| Aktív/lejárt büntetés | `/punishments [player]` |
| Report | `/report ...` |
| Privát üzenet | `/msg`, `/tell`, `/w` |
| Válasz | `/reply`, `/r` |
| SocialSpy | `/socialspy` |
| Vanish | `/vanish [online-player]` |
| Inventory/admin | `/invsee <online-player> [read|edit] [main|ender]` |
| Offline teleport | `/offlinetp <player>` |
| Moderációs GUI | `/moderation [online-player]` |

A pontos alias, argumentum, permission, konzolhasználat, GUI-alternatíva és
auditstátusz az
[admin/moderátor kézikönyvben](../guides/ADMIN_AND_MODERATOR_GUIDE.md) található.

### Persistence és recovery

- Punishment: sérült store, expiry és restart.
- PM: reply partner quit/reconnect után.
- Vanish: join/quit, player list, MOTD count és permission.
- Inventory: read/edit, ender chest, escrow, reconnect, reload és disable.
- Offline teleport: hiányzó játékos/világ és következő login.
- Crate: pending nyitás, ledger, generation, settlement és manuális vizsgálat.

## Builder- és world designer rész

### Fizikai előkészítést igénylő területek

- crate blokkok és világpolicy;
- territory/claim, főváros, protected city, dungeon és doom gate;
- questhely, NPC-binding és dialóguscél;
- event- és bossaréna, caravan/komp útvonal;
- profession/crafting pont, bolt, bank és váltó;
- rituáléoltár és relikvia-helyszín;
- parkourpálya, teleport- és spawnpont;
- resource-packhez kötött egyedi itemek bemutatóhelyei.

### Crate checklist

- Hozd létre/cseréld/töröld natív adminútvonalon.
- Ellenőrizd a crate ID-t, világot, koordinátát és blocktípust.
- Teszteld eltűnt blokk, világátnevezés és definíciócsere után.
- Teszteld main/off-hand keyvel, több stackkel és dupla kattintással.
- Teszteld full inventory, overflow, részleges mass-open és minden rewardtípus
  mellett.
- Őrizd meg az auditot és a recovery bizonyítékát.

### Sit checklist

- Stairs, alsó/felső slab, carpet, moss carpet, pale moss carpet és snow.
- Pontos seat height/position.
- Kétjátékos-foglalás.
- Unsafe support, folyadék, hibás clearance.
- Support blokk törése.
- Damage/sneak/teleport/world change/quit/kick/dismount/reload/disable.
- Seat entity sweep GSit nélkül.

### WorldEdit vagy világcsere után

Mentsd a világot és a pluginadatokat, majd ellenőrizd újra az összes
helyrekordot. A névben egyező, de tartalmában lecserélt világ sem automatikusan
biztonságos. Crate, territory, NPC, dungeon, parkour, rituálé, boss és teleport
esetén külön pozitív és hiányzó-hely teszt kell.

### Ne csináld

- Ne építs a configban lévő placeholder koordinátára bejárás nélkül.
- Ne nevezd át a világot a persistent helyek felmérése nélkül.
- Ne távolíts el crate blokkot aktív/pending nyitás közben.
- Ne hagyj ülést folyadék, veszélyes support vagy szűk fejhely mellett.
- Ne tekints egy quest-NPC ID-t kész FancyNpcs bindingnak.
- Ne ígérj lay/crawl/stacking vagy AFK-zóna funkciót.

## Fontos admin- és builderparancsok

| Terület | Belépési pont |
|---|---|
| Plugin/config | `/icesmp ...` |
| Teljes config GUI | `/icesmp config ...` |
| Eventek | `/events ...` |
| Territory | `/territory ...` |
| Claim | `/claim ...` |
| NPC binding | `/npcbind ...` |
| Crate admin | `/crate ...` |
| Plugin item | `/iceitem ...` |
| Parkour | `/parkour ...` |
| Moderáció | `/moderation`, punishment parancsok |
| Inventory admin | `/invsee ...` |
| Offline teleport | `/offlinetp ...` |

A tényleges 68 root, 286 feloldott funkcionális route, 93 route-alias és
79 root alias teljes, konzol- és
tab-completion információval a
[parancsreferenciában](../reference/COMMAND_REFERENCE.md) található.

## Külső pluginok státusza

| Plugin | Release-döntés |
|---|---|
| AxAFKZone | Nem szükséges, scope törölve |
| AxAPI | AFK miatt nem szükséges; teljes élő dependencylistát ellenőrizni kell |
| GSit | Sit-only runtime acceptance-ig marad |
| CrazyCrates | Crate runtime + fault injection végéig marad |
| SModeration | Natív moderation acceptance-ig marad |
| InvSee++ | Natív inventory-admin acceptance-ig marad |
| MiniMOTD | Valós server-list acceptance-ig marad |
| TAB | Nem cél teljes paritás; élő configleltár alapján döntendő |
| ICEsmpadditions | Warden-XP kézi tesztjéig marad |
| FarmProtect | Player/mob trample tesztjéig marad |

Részletes bizonyíték és eltávolítási sorrend:
[EXTERNAL_PLUGIN_STATUS.md](EXTERNAL_PLUGIN_STATUS.md).

## Acceptance checklist — vezetői kivonat

### Moderáció

- [ ] punishment restart, expiry, corrupt state és lemezhiba;
- [ ] PM quit/reconnect, reply és SocialSpy;
- [ ] vanish, visibility, player list és MOTD count;
- [ ] inventory/ender chest read/edit;
- [ ] escrow és reconnect recovery;
- [ ] reload/disable és permissionmátrix;
- [ ] offline teleport.

### MOTD

- [ ] párhuzamos ping;
- [ ] TIME és RANDOM;
- [ ] eseményprioritás és vanished count;
- [ ] minden ikonmód, hibás PNG és symlink;
- [ ] gyors reload és scheduler rejection;
- [ ] MiniMOTD nélküli indulás.

### Sit-only

- [ ] minden támogatott blokk és seat pozíció;
- [ ] kétjátékos-foglalás;
- [ ] unsafe support, folyadék és clearance;
- [ ] damage/sneak/break/teleport/world change;
- [ ] quit/kick/dismount/reload/disable;
- [ ] retired scheduler és seat sweep;
- [ ] GSit nélküli indulás.

### Crate

- [ ] main/off-hand, dupla kattintás és több key stack;
- [ ] mass-open, részleges nyitás és full inventory;
- [ ] minden rewardtípus;
- [ ] command- és currency failure;
- [ ] reload, generation, világ- és definíciócsere;
- [ ] quit/kick/disable minden állapotban;
- [ ] settlement/recovery, auditrotáció és restart;
- [ ] manuális vizsgálati adminfolyamat;
- [ ] CrazyCrates nélküli indulás.

### AFK és mini-pluginok

- [ ] automatikus/kézi AFK, aktivitás és reconnect;
- [ ] tablistajelzés és reward gate;
- [ ] negatív bizonyíték: nincs AFK-zóna vagy payout;
- [ ] Warden-XP;
- [ ] player crop trample;
- [ ] mob crop trample.

A teljes checklist minden tételnél megadja a felelőst, előkészítést, elvárt
eredményt, hibánál következő lépést és bizonyítékhelyet:
[RELEASE_ACCEPTANCE_CHECKLIST.md](RELEASE_ACCEPTANCE_CHECKLIST.md).

## Ismert korlátok

- A CI nem production runtime bizonyíték.
- Az élő config, store-ok és teljes pluginlista nélkül több állítás csak
  capability-szintű.
- Öt kódban olvasott configpathnak nincs bundled defaultja; egy configértéknél
  integer/long típusdrift található. A configreferencia ezeket név szerint jelöli.
- Egy config/registry ID nem bizonyít kész világhelyet vagy NPC-bindingot.
- A resource pack külön artifact; az item megjelenítést valós kliensen kell
  ellenőrizni.
- Az opcionális külső hidak konkrét pluginverzióját stagingen kell igazolni.
- A következő 26.2 platformport külön review- és migrációs feladat; ez a csomag
  a jelenlegi 1.21.11-es stabil tesztalapot dokumentálja.

## Deployment döntés

Ez a release kódszinten és CI alapján integrált tesztalap. Production rollout
csak akkor javasolt, ha:

1. az élő config és permissionmátrix összevetése megtörtént;
2. a builderfüggő helyek stagingen elkészültek vagy igazoltan nem aktívak;
3. a teljes acceptance checklist bizonyítékkal zöld;
4. minden kiváltandó külső plugin saját rollout-kapuja zöld;
5. van mentés, rollback terv és kijelölt deploymentfelelős.
