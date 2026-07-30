# IceSMP release acceptance checklist

<!-- icesmp-release-document: acceptance-checklist -->

Ez a lista a `master`
`4643ab53586f0c1ee7352df16dcd477013e6fad4` kiadási jelöltjéhez
tartozik. A CI a kódszintű szerződéseket bizonyítja; egyetlen alábbi
runtime pontot sem pipál ki automatikusan.

## Bizonyítékkezelés

Minden futás kapjon külön könyvtárat:

`evidence/2026-07-30/<terület>/<teszt-azonosító>/`

Ide kerüljön:

- a pontos JAR SHA-256 és szerververzió;
- a használt config másolata titkok nélkül;
- konzollog és releváns audit/state fájl;
- képernyőkép vagy rövid videó, ha a viselkedés vizuális;
- tesztelő neve, időpont, eredmény és hibajegy;
- restart/fault-injection esetén az „előtte” és „utána” állapot.

Hiba esetén ne ismételd vakon ugyanazt az éles adaton. Állítsd le az
érintett rolloutot, őrizd meg a bizonyítékot, nyiss hibajegyet, és csak
javított builddel folytasd.

## Szerepkörönkénti jóváhagyás

Az alábbi hét fejezet külön-külön pipálandó. A jóváhagyó ne csak a négyzetet
jelölje: az alatta megadott bizonyítékhelyet is töltse ki.

### Szervervezető

- [ ] **Felelős:** szervervezető
- **Előkészítés:** végleges scope, külsőplugin-mátrix, rollbackterv és
  játékoskommunikáció áttekintése.
- **Elvárt eredmény:** a rollout határai, a bent maradó pluginok és a
  visszaállítási döntési pontok írásban elfogadottak.
- **Hiba esetén:** a release nem telepíthető; a hiányzó tulajdonosi döntést
  külön jegyzőkönyvben kell lezárni.
- **Bizonyíték helye:** `leadership/approval/`.

### Admin

- [ ] **Felelős:** vezető admin
- **Előkészítés:** config snapshot, tesztadat, permissionprofilok, recovery- és
  crate-forgatókönyvek.
- **Elvárt eredmény:** a config, persistence, moderáció, crate és recovery
  releváns tesztsorai bizonyítékkal zártak.
- **Hiba esetén:** az érintett rendszer rolloutját le kell állítani, a state-et
  archiválni és hibajegyet nyitni.
- **Bizonyíték helye:** `admin/approval/`.

### Moderátor

- [ ] **Felelős:** moderátori vezető
- **Előkészítés:** helper/mod/admin tesztfiókok, punishment-, report-,
  PM/SocialSpy- és vanish-próba.
- **Elvárt eredmény:** a moderációs permissionhatárok, audit és játékosfolyamatok
  a MOD-sorok szerint működnek.
- **Hiba esetén:** a natív moderáció nem válthatja ki az élő rendszert.
- **Bizonyíték helye:** `moderation/approval/`.

### Builder és world designer

- [ ] **Felelős:** vezető builder/world designer
- **Előkészítés:** staging világmásolat, crate-/event-/boss-/NPC-helyek és
  világpolicy-lista.
- **Elvárt eredmény:** a fizikai helyek, blokkok, világkorlátozások,
  WorldEdit-utáni állapot és NPC-kötések bejárása zöld.
- **Hiba esetén:** az érintett hely nem kerülhet productionbe; koordinátát és
  reprodukciót kell rögzíteni.
- **Bizonyíték helye:** `builder/approval/`.

### Eventes és tartalomkészítő

- [ ] **Felelős:** eventes/tartalomkészítő
- **Előkészítés:** tesztesemény, bosshely, quest/NPC-kötés, reward- és
  full-inventory tesztkarakter.
- **Elvárt eredmény:** az esemény indítása, lezárása, jutalma és területvédelme
  végigpróbált.
- **Hiba esetén:** az esemény ne kerüljön menetrendbe; az érintett trigger és
  helyszín kerüljön a hibajegybe.
- **Bizonyíték helye:** `events/approval/`.

### Tesztelő

- [ ] **Felelős:** kijelölt release-tesztelő
- **Előkészítés:** a jelen dokumentum teljes pozitív, negatív, restart- és
  fault-injection mátrixa.
- **Elvárt eredmény:** minden végrehajtott sorhoz várt–kapott eredmény és
  bizonyíték tartozik; a kihagyott sor indokolt.
- **Hiba esetén:** reprodukálható hibajegy készül, az érintett rollout-kapu
  nyitva marad.
- **Bizonyíték helye:** `testing/approval/`.

### Deploymentet végző személy

- [ ] **Felelős:** deploymentet végző üzemeltető
- **Előkészítés:** mentés, pontos JAR-hash, config- és pluginlista, karbantartási
  ablak, start- és rollback-parancsok.
- **Elvárt eredmény:** a telepítés és smoke test naplózott; a külső plugin csak
  a saját elfogadott kapuja után kerül ki.
- **Hiba esetén:** az előre rögzített rollback fut, az élő state és log
  megőrzésével.
- **Bizonyíték helye:** `deployment/approval/`.

## Moderáció és online admin

| Kész | Teszt | Felelős | Előkészítés | Elvárt eredmény | Hiba esetén | Bizonyíték |
|---|---|---|---|---|---|---|
| [ ] | MOD-01 Warning | Moderátor | két tesztjátékos, `icesmp.moderation.warn` | `/warn` bekerül a historyba és auditba | punishment rollout stop | `moderation/MOD-01/` |
| [ ] | MOD-02 Kick | Moderátor | online célpont | `/kick` leválasztja, ok auditálva | command és auditlog mentése | `moderation/MOD-02/` |
| [ ] | MOD-03 Permanent mute | Moderátor | chatelő célpont | chat tiltott, más funkciók elérhetők | mute enforcement hibajegy | `moderation/MOD-03/` |
| [ ] | MOD-04 Temporary mute + expiry | Moderátor | rövid, pl. 30 s időtartam | lejáratkor automatikusan oldódik | óra/state vizsgálat | `moderation/MOD-04/` |
| [ ] | MOD-05 Permanent ban | Admin | külön tesztfiók | belépés tiltott, ok/idő látható | azonnali rollback a tesztfiókon | `moderation/MOD-05/` |
| [ ] | MOD-06 Temporary ban + expiry | Admin | rövid tesztban | lejárat előtt tilt, utána enged | expiry scheduler/state vizsgálat | `moderation/MOD-06/` |
| [ ] | MOD-07 Unmute/unban | Moderátor | aktív mute és ban | csak megfelelő típust old, audit marad | ledger és permission vizsgálat | `moderation/MOD-07/` |
| [ ] | MOD-08 History/active | Moderátor | többféle punishment | history teljes, active csak aktív rekord | ne törölj state-et; hibajegy | `moderation/MOD-08/` |
| [ ] | MOD-09 Restart + expiry | Admin | temp punishment, kontrollált restart | restart után is aktív, majd lejár | state backup, rollout stop | `moderation/MOD-09/` |
| [ ] | MOD-10 Corrupt state | Admin/üzemeltető | másolt tesztadat, szándékosan hibás YAML | fail-closed/egyértelmű hiba; nincs csendes adatvesztés | fájl és stacktrace megőrzése | `moderation/MOD-10/` |
| [ ] | MOD-11 Lemezírási hiba | Üzemeltető | tesztkörnyezetben write-deny/fault injection | művelet nem látszik sikeresnek; kritikus hiba látható | írás visszaállítása, state összevetés | `moderation/MOD-11/` |
| [ ] | MOD-12 Report lifecycle | Moderátor | nyitott report, offline bejelentő | lista/kezelés/feedback működik | report store megőrzése | `moderation/MOD-12/` |
| [ ] | MOD-13 PM quit–reconnect | Tesztelő | A és B `/msg`, majd B reconnect | reply partner nem irányul rossz sessionre; reconnect után determinisztikus | PM rollout stop | `moderation/MOD-13/` |
| [ ] | MOD-14 `/msg` aliasok | Tesztelő | `/msg`, `/tell`, `/w`, `/reply` | azonos natív csatorna, permission és hibaszöveg | command routing bizonyíték | `moderation/MOD-14/` |
| [ ] | MOD-15 SocialSpy | Moderátor | külön spy és két beszélő | csak jogosult spy látja; ki/be kapcsolható | permissionkiosztás ellenőrzése | `moderation/MOD-15/` |
| [ ] | MOD-16 Vanish | Admin | vanished és normál néző | normál játékos nem látja a vanished admint | ne használd éles moderációra | `moderation/MOD-16/` |
| [ ] | MOD-17 Vanish visibility | Admin | `icesmp.moderation.vanish.see` ki/be | csak a node-dal rendelkező látja | LuckPerms export megőrzése | `moderation/MOD-17/` |
| [ ] | MOD-18 Online inventory read | Moderátor | online célpont, read node | tartalom látható, nem módosul | session bezárása | `moderation/MOD-18/` |
| [ ] | MOD-19 Online inventory edit | Admin | edit node, jelölt tárgy | változás egyszeri, audit/recovery konzisztens | escrow lezárás nélkül ne folytasd | `moderation/MOD-19/` |
| [ ] | MOD-20 Ender chest read/edit | Admin | elkülönített teszttárgyak | read nem ír; edit helyesen ment | célpont maradjon online a vizsgálatig | `moderation/MOD-20/` |
| [ ] | MOD-21 Escrow reconnect recovery | Admin | edit közben célpont kilép | nincs duplikáció vagy elveszett tárgy; recovery állapot kezelhető | state és inventory snapshot | `moderation/MOD-21/` |
| [ ] | MOD-22 Reload/disable | Admin | nyitott moderációs/invsee GUI | session lezárul, state menthető | plugin vissza, recovery vizsgálat | `moderation/MOD-22/` |
| [ ] | MOD-23 Permissionmátrix | Admin | külön helper/mod/admin profil | minden node csak a javasolt szerepkörnek enged | LuckPerms kiosztás javítása | `moderation/MOD-23/` |
| [ ] | MOD-24 Offline teleport | Moderátor | korábban kilépett célpont | utolsó ismert helyre, helyes világba teleportál | világ/hely state ellenőrzése | `moderation/MOD-24/` |
| [ ] | MOD-25 Moderációs GUI | Moderátor | több report/punishment és lapozás | slotok, lapok, back, lezárt állapot helyes | GUI bezárása, clicklog | `moderation/MOD-25/` |

## Natív MOTD

| Kész | Teszt | Felelős | Előkészítés | Elvárt eredmény | Hiba esetén | Bizonyíték |
|---|---|---|---|---|---|---|
| [ ] | MOTD-01 Párhuzamos ping | Tesztelő | több kliens/status lekérő párhuzamosan | nincs race, kivétel vagy kevert válasz | pingterhelés leállítása | `motd/MOTD-01/` |
| [ ] | MOTD-02 TIME rotáció | Admin | TIME mód, rövid tesztablak | idő szerint determinisztikus sor | config+timestamp megőrzése | `motd/MOTD-02/` |
| [ ] | MOTD-03 RANDOM rotáció | Admin | RANDOM mód, több ping | csak valid variánsok, nincs üres output | seed nem elvárt; minták mentése | `motd/MOTD-03/` |
| [ ] | MOTD-04 Eseményprioritás | Eventes | több egyidejű jelölt esemény | legmagasabb prioritás nyer | eseményállapot logolása | `motd/MOTD-04/` |
| [ ] | MOTD-05 Vanished count | Moderátor | online + vanished játékos | publikus count nem szivárogtatja a vanished admint | MOTD rollout stop | `motd/MOTD-05/` |
| [ ] | MOTD-06 Ikonmódok | Admin | bundled, custom és rotáló mód | 64×64 valid ikon jelenik meg | default ikonra vissza | `motd/MOTD-06/` |
| [ ] | MOTD-07 Hibás PNG | Admin | sérült/rossz méretű másolat | egyértelmű fallback, nincs crash | hibás fájl eltávolítása | `motd/MOTD-07/` |
| [ ] | MOTD-08 Symlink | Üzemeltető | teszt symlink az ikonkönyvtárban | policy szerint elutasított, nincs path escape | symlink törlése | `motd/MOTD-08/` |
| [ ] | MOTD-09 Gyors reload | Admin | egymás utáni config reloadok | csak legfrissebb generáció publikálódik | stabil config visszaállítása | `motd/MOTD-09/` |
| [ ] | MOTD-10 Scheduler rejection | Üzemeltető | kontrollált disable/reload verseny | nincs stale publish vagy leak | log és thread dump mentése | `motd/MOTD-10/` |
| [ ] | MOTD-11 MiniMOTD nélkül | Üzemeltető | MiniMOTD jar/adat nélkül, backup mellett | IceSMP indul, server-list válasz működik | plugin vissza, rollout stop | `motd/MOTD-11/` |

## Sit-only

| Kész | Teszt | Felelős | Előkészítés | Elvárt eredmény | Hiba esetén | Bizonyíték |
|---|---|---|---|---|---|---|
| [ ] | SIT-01 Stairs | Builder/tesztelő | alsó/felső, irányok és waterlog | csak policy szerint ülhető, helyes pozíció | problémás shape tiltása | `sit/SIT-01/` |
| [ ] | SIT-02 Slab | Builder/tesztelő | alsó és felső slab | helyes ülőmagasság, dupla slab policy szerint | config/material rollback | `sit/SIT-02/` |
| [ ] | SIT-03 Carpetek | Builder/tesztelő | carpet, moss, pale moss | stabil ülőpozíció | érintett material tiltása | `sit/SIT-03/` |
| [ ] | SIT-04 Snow | Builder/tesztelő | több hóréteg | konfigurált maximum és magasság helyes | snow support kikapcsolása | `sit/SIT-04/` |
| [ ] | SIT-05 Unsafe support | Tesztelő | levegő/instabil támasz/folyadék | ülés megtagadva | blokkpolicy szigorítása | `sit/SIT-05/` |
| [ ] | SIT-06 Clearance | Tesztelő | blokk a fej/ülőhely felett | nincs suffocation vagy clipping | helyszín lezárása | `sit/SIT-06/` |
| [ ] | SIT-07 Két játékos | Tesztelő | egy ülőhely, két egyidejű kérés | pontosan egy foglalás nyer | session reset, hibajegy | `sit/SIT-07/` |
| [ ] | SIT-08 Damage/sneak | Tesztelő | ülés közben sérülés és sneak | policy szerinti azonnali felállás | seat entity sweep | `sit/SIT-08/` |
| [ ] | SIT-09 Support break | Builder | ülés alatt blokk törése | felállás és entity cleanup | chunk sweep | `sit/SIT-09/` |
| [ ] | SIT-10 Teleport/world change | Tesztelő | teleport és világváltás | nincs hátramaradt seat/reservation | sweep + restart teszt | `sit/SIT-10/` |
| [ ] | SIT-11 Quit/kick/dismount | Tesztelő | mindhárom kilépési út | minden állapot kitakarítva | session/state összevetés | `sit/SIT-11/` |
| [ ] | SIT-12 Reload/disable | Admin | ülő játékosok mellett | mindenki biztonságosan feláll, entity eltűnik | seat sweep, rollback | `sit/SIT-12/` |
| [ ] | SIT-13 Retired scheduler | Üzemeltető | gyors reload/disable | régi callback nem állít vissza state-et | log és tasklista | `sit/SIT-13/` |
| [ ] | SIT-14 Seat entity sweep | Admin | szándékosan árva marker tesztvilágban | indulási/disable sweep eltakarítja | kézi entity cleanup | `sit/SIT-14/` |
| [ ] | SIT-15 GSit nélkül | Üzemeltető | GSit jar/adat nélkül, backup mellett | `/sit` és click-to-sit működik | GSit vissza, rollout stop | `sit/SIT-15/` |
| [ ] | SIT-16 Nem támogatott pózok | Tesztelő | lay/crawl/stack/player/NPC próbák | IceSMP nem kínál ilyen útvonalat | command/plugin ütközés vizsgálata | `sit/SIT-16/` |

## Natív crate

| Kész | Teszt | Felelős | Előkészítés | Elvárt eredmény | Hiba esetén | Bizonyíték |
|---|---|---|---|---|---|---|
| [ ] | CRATE-01 Main/off-hand | Tesztelő | kulcs mindkét kézben | main-hand nyit; off-hand nem duplikál | crate tiltása | `crate/CRATE-01/` |
| [ ] | CRATE-02 Dupla kattintás | Tesztelő | gyors dupla/interleaved click | egy opening és egy kulcsfoglalás | ledger+audit mentése | `crate/CRATE-02/` |
| [ ] | CRATE-03 Több key stack | Tesztelő | több kulcs egy stackben | nyitásonként pontos fogyás | crate rollout stop | `crate/CRATE-03/` |
| [ ] | CRATE-04 Részleges mass-open | Tesztelő | kevés kulcs/hely, nagy kérés | teljesült mennyiség pontosan jelzett | inventory+ledger snapshot | `crate/CRATE-04/` |
| [ ] | CRATE-05 Full inventory | Tesztelő | teljes inventory | overflow policy szerint nincs elveszett jutalom | ne zárd MANUAL_REVIEW nélkül | `crate/CRATE-05/` |
| [ ] | CRATE-06 Item reward | Tesztelő | determinisztikus tesztcrate | item egyszer kerül átadásra | opening id megőrzése | `crate/CRATE-06/` |
| [ ] | CRATE-07 Currency reward | Tesztelő | valuta reward | pontos fizikai veret/előírt settlement | ledger és balance összevetés | `crate/CRATE-07/` |
| [ ] | CRATE-08 Command reward | Admin | ártalmatlan tesztcommand | pontosan egyszer fut | command audit megőrzése | `crate/CRATE-08/` |
| [ ] | CRATE-09 Command failure | Admin | szándékosan hibás command | nem lesz hamis COMPLETED; recovery látható | crate tiltása, manuális review | `crate/CRATE-09/` |
| [ ] | CRATE-10 Currency failure | Admin | fault-injection tesztvaluta | kompenzáció vagy review, nincs dupla fizetés | ledger zárolása | `crate/CRATE-10/` |
| [ ] | CRATE-11 Reload/generation | Admin | opening közbeni config reload | opening saját snapshotja konzisztens | régi és új config mentése | `crate/CRATE-11/` |
| [ ] | CRATE-12 Világcsere | Builder | location világának átnevezett másolata | invalid location nem fizet/nyit csendben | world vissza vagy location újrakötés | `crate/CRATE-12/` |
| [ ] | CRATE-13 Definition csere | Admin | azonos ID módosított definícióval | generációs snapshot megakadályozza a keverést | config rollback | `crate/CRATE-13/` |
| [ ] | CRATE-14 Quit minden state-ben | Tesztelő | kilépés RESERVED/PERSISTED/GRANTING közben | determinisztikus recovery, nincs duplázás | opening id szerinti vizsgálat | `crate/CRATE-14/` |
| [ ] | CRATE-15 Kick/disable | Admin | kick és plugin disable külön | state lezárt vagy recoverable | restart előtt fájlmásolat | `crate/CRATE-15/` |
| [ ] | CRATE-16 Settlement/recovery | Admin | félbehagyott openingek | single-claim finalize/rollback | kézi döntés, auditcsatolás | `crate/CRATE-16/` |
| [ ] | CRATE-17 Auditrotáció | Üzemeltető | kis tesztlimit, sok opening | rotáció után is olvasható és rendezett | logarchiválás | `crate/CRATE-17/` |
| [ ] | CRATE-18 Restart | Üzemeltető | opening után kontrollált restart | ledger/state konzisztens | rollout stop | `crate/CRATE-18/` |
| [ ] | CRATE-19 MANUAL_REVIEW | Vezető admin | szándékos nem eldönthető failure | nem auto-complete; dokumentált emberi döntés | jutalmat csak bizonyíték után adj | `crate/CRATE-19/` |
| [ ] | CRATE-20 CrazyCrates nélkül | Üzemeltető | külső jar/adat nélkül, backup mellett | native set/buy/open/recovery működik | külső plugin vissza, hibajegy | `crate/CRATE-20/` |

## Globális AFK

| Kész | Teszt | Felelős | Előkészítés | Elvárt eredmény | Hiba esetén | Bizonyíték |
|---|---|---|---|---|---|---|
| [ ] | AFK-01 Automatikus állapot | Tesztelő | rövid teszt-timeout | küszöb előtt aktív, küszöbnél AFK | config visszaállítása | `afk/AFK-01/` |
| [ ] | AFK-02 `/afk` ki/be | Tesztelő | aktív és auto-AFK állapot | mindkettőből helyesen kapcsol, OFF friss baseline | reward gate rollout stop | `afk/AFK-02/` |
| [ ] | AFK-03 Activity reset | Tesztelő | mozgás/chat/interakció/más parancs | azonnal aktív; `/afk` maga nem pre-clearel | listener hibajegy | `afk/AFK-03/` |
| [ ] | AFK-04 HUD nélküli tablista | Admin | `hud=false`, `tablist=true` | AFK suffix és sorrend működik | tablist visszakapcsolás | `afk/AFK-04/` |
| [ ] | AFK-05 Disable cleanup | Admin | AFK jelölések mellett tablist/plugin disable | név/header/footer/team/objective kitakarítva | reconnect + scoreboard cleanup | `afk/AFK-05/` |
| [ ] | AFK-06 Configvezérelt reward gate | Tesztelő | `afk.block-rewards` és `kill-rewards.afk-block` true/false; profession, mob, boss, dungeon, Wild Hunt | profession és kill/boss útvonal a dokumentált kulcsprecendenciát követi; lifecycle jutalomtiltás mellett is lezárul | érintett jutalomforrás tiltása | `afk/AFK-06/` |
| [ ] | AFK-06B Feltétlen reward gate | Tesztelő | mindkét AFK-kulcs false; fishing windfall és ambient pénzjutalom | AFK játékos e két jutalmat továbbra sem kapja meg | forráseltérés hibajegy, termékdöntés | `afk/AFK-06B/` |
| [ ] | AFK-07 Nincs zónás jutalom | Admin | live Ax fájlok eltávolítva | nincs zone, bossbar, timer vagy payout | deployment leállítása | `afk/AFK-07/` |

## Mini-plugin megfelelői

| Kész | Teszt | Felelős | Előkészítés | Elvárt eredmény | Hiba esetén | Bizonyíték |
|---|---|---|---|---|---|---|
| [ ] | MINI-01 Warden XP | Tesztelő | Warden normál és nem játékos kill | csak policy szerinti XP | külső plugin marad | `mini/MINI-01/` |
| [ ] | MINI-02 Player crop trample | Tesztelő | játékos ugrik termésre | configured protection működik | FarmProtect marad | `mini/MINI-02/` |
| [ ] | MINI-03 Mob crop trample | Tesztelő | mob tapos termést | configured protection működik | FarmProtect marad | `mini/MINI-03/` |

## Builder és világ

| Kész | Teszt | Felelős | Előkészítés | Elvárt eredmény | Hiba esetén | Bizonyíték |
|---|---|---|---|---|---|---|
| [ ] | WORLD-01 Crate helyek | Builder/admin | minden final crate block és világ | location, block és world policy egyezik | hely újrakötése | `world/WORLD-01/` |
| [ ] | WORLD-02 Territórium/claim | Builder/admin | határpontok és bypass profil | védelem, trust és zónaszabály helyes | építés stop | `world/WORLD-02/` |
| [ ] | WORLD-03 Quest/NPC | Builder/eventes | minden használt NPC és questhely | FancyNpcs-kötés és fallback út működik | kötés újraépítése | `world/WORLD-03/` |
| [ ] | WORLD-04 Boss/event anchor | Eventes | minden fix spawnhely | biztonságos, nem WG/claim-konfliktusos | anchor eltávolítása | `world/WORLD-04/` |
| [ ] | WORLD-05 WorldEdit/világcsere | Builder | staging másolat utáni bejárás | crate, territory, NPC, ritual, dungeon ép | rollback snapshot | `world/WORLD-05/` |
| [ ] | WORLD-06 Resource pack | Builder/tartalomkészítő | final pack és fallback kliens | ITEM_MODEL helyes, fallback használható | pack rollout stop | `world/WORLD-06/` |

## Deployment

| Kész | Lépés | Felelős | Előkészítés | Elvárt eredmény | Hiba esetén | Bizonyíték |
|---|---|---|---|---|---|---|
| [ ] | DEP-01 Artifact azonosítás | Üzemeltető | release JAR | SHA rögzítve, nem a deployed baseline JAR | ismeretlen JAR nem telepíthető | `deployment/DEP-01/` |
| [ ] | DEP-02 Teljes backup | Üzemeltető | világ, plugins, config, state, remap cache | visszaállítható backup és restore próba | deployment törlése | `deployment/DEP-02/` |
| [ ] | DEP-03 Config merge | Admin | live config + referencia | ismeretlen/legacy AFK kulcsok külön kezelve | stagingben javítás | `deployment/DEP-03/` |
| [ ] | DEP-04 Permissionkiosztás | Vezető admin | LuckPerms export | 44 final statikus/dinamikus node áttekintve | ne nyisd ki a szervert | `deployment/DEP-04/` |
| [ ] | DEP-05 Ax cleanup | Üzemeltető | backup | AxAFKZone/AxAPI jar, adat és remap-cache nincs a célban | vissza backupból, vizsgálat | `deployment/DEP-05/` |
| [ ] | DEP-06 Feltételes pluginok | Szervervezető | kitöltött acceptance | GSit/CrazyCrates/SModeration/InvSee++/MiniMOTD csak saját kapu után kerül ki | külső plugin marad | `deployment/DEP-06/` |
| [ ] | DEP-07 Első staging start | Üzemeltető | tiszta log és másolt state | nincs kritikus persistence/config hiba | azonnali stop, logmentés | `deployment/DEP-07/` |
| [ ] | DEP-08 Reload/disable/restart | Admin | staging online tesztelők | lifecycle cleanup és újraindulás stabil | rollout stop | `deployment/DEP-08/` |
| [ ] | DEP-09 Smoke test | Tesztelő | minden szerepkör | login, command, GUI, event, economy alapok működnek | hibajegy és rollback | `deployment/DEP-09/` |
| [ ] | DEP-10 Rollback próba | Üzemeltető | staging backup | korábbi build+state visszaállítható | production rollout tiltott | `deployment/DEP-10/` |
| [ ] | DEP-11 Csapatkommunikáció | Szervervezető | team summary és guide linkek | admin/mod/builder/tester tudja a változást | rollout elhalasztása | `deployment/DEP-11/` |
| [ ] | DEP-12 Production go/no-go | Szervervezető | minden kötelező pipa | dokumentált GO vagy indokolt NO-GO | nincs részleges, néma rollout | `deployment/DEP-12/` |

## Záró döntés

| Kész | Döntés | Kitölti |
|---|---|---|
| [ ] | Minden kötelező teszt PASS | Tesztvezető |
| [ ] | Minden nyitott hiba elfogadott vagy javított | Szervervezető |
| [ ] | Rollback bizonyított | Üzemeltető |
| [ ] | Külső pluginonkénti eltávolítás külön jóváhagyott | Szervervezető |
| [ ] | Production deployment engedélyezve | Szervervezető |

Ha bármely kritikus persistence-, duplikációs, permission-, reconnect-,
world-location- vagy lifecycle-teszt hibás, a döntés automatikusan
`NO-GO`.
