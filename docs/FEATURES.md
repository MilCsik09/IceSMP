# IceSMP funkciókatalógus

<!-- icesmp-doc-id: reference.feature-catalogue -->

- Dokumentált branch: `master`
- Dokumentált HEAD: `4643ab53586f0c1ee7352df16dcd477013e6fad4`
- Deployed baseline: `IceSMP-1.0-TESTING.jar` (`da039f0e2bdf0e67b216ce82d7d3fe3b6da0af6e18f6fa175762c37493795a05`)
- Audit dátuma: `2026-07-30`
- Bizonyított production komponens: **545 / 545**
- Regisztrált root command: **68 / 68**
- GUI: **22 / 22**
- Aktív config path: **13 550**
- Adatvezérelt kategória / név szerinti elem: **92 / 2 435**
- Feloldatlan komponens: **0**

## Mire szolgál ez a katalógus?

Ez a dokumentum a végleges integrált forrás minden ténylegesen bekötött funkcióját közérthető rendszerblokkokba rendezi. Nem pusztán osztályneveket vagy régi README-állításokat sorol: a bootstrap, a regisztrált parancsok és routingágak, GUI-k, listenerek, managerek/service-ek, persistence komponensek, configok és adatvezérelt resource-ok összevetésére épül.

A napi használati részleteket a [játékos-](PLAYER_GUIDE.md), [builder-](BUILDER_GUIDE.md) és [admin kézikönyv](ADMIN_GUIDE.md) tartalmazza. A teljes command-, route-, alias-, permission-, config-, message- és komponensleltárt a `Repository Docs Inventory` workflow letölthető artifactja állítja elő; ezek nem kézzel karbantartott olvasói oldalak.

A deployed státusz a csatolt JAR képességeit hasonlítja a release-hez. Ez nem bizonyítja az élő szerver külső configját vagy adatállományát. A release-státusz pedig azt jelzi, hogyan vezethető be az adott rendszer; a „Tesztelési vagy rollout-kapu alatt” kód- és CI-kész állapotot jelent, nem production elfogadást.

## Lefedettségi összefoglaló

| Rendszercsoport | Katalógustétel | Forráskomponens |
|---|---:|---:|
| Alaprendszer és üzemeltetés | 5 | 31 |
| Adminisztráció és moderáció | 5 | 54 |
| Játékosállapot és szervermegjelenítés | 6 | 37 |
| Karakterfejlődés és tartalom | 12 | 210 |
| Gazdaság és jutalmak | 4 | 67 |
| Frakciók, politika és terület | 4 | 52 |
| Világ, események és interakció | 11 | 90 |
| Fejlesztői és diagnosztikai funkciók | 1 | 4 |
| Tervezett, de nem aktív tartalom | 1 | 0 |
| **Összesen** | **49** | **545** |

## Státuszok röviden

- **Aktív és játékosok számára elérhető:** normál játékosútvonalon elérhető.
- **Aktív, adminisztratív:** működő staff/üzemeltetési képesség.
- **Aktív, builder-előkészítést igényel:** a kód aktív, de világhely, NPC, régió vagy tartalom-előkészítés kell.
- **Aktív, configgal engedélyezhető:** forrásból bizonyított, de az élő bekapcsoltság külső config nélkül nem állítható.
- **Aktív, külső integrációval bővül:** partnerplugin nélkül csökkentett vagy nincs integrációs út.
- **Tesztelési vagy rollout-kapu alatt:** kódban/CI-ben jelen van, de kézi vagy fault-injection átvétel szükséges.
- **Tervezett, de nem implementált:** nincs aktív regisztráció; nem production funkció.

## Alaprendszer és üzemeltetés

### Plugin-életciklus és rendszerindítás

| Mező | Érték |
|---|---|
| Funkcióazonosító | `core.lifecycle` |
| Release-státusz | **Tesztelési vagy rollout-kapu alatt** |
| Deployed JAR-hoz képesti státusz | **Már korábban is elérhető volt** |
| Célközönség | Fejlesztő/üzemeltető, Admin, Tesztelő |
| Közérthető leírás | Az IceSMP szolgáltatásainak, parancsainak és eseménykezelőinek rendezett indítása, újratöltése és leállítása. |
| Elérés | Szerverindítás, plugin enable/disable; adminisztratív `/icesmp reload`. |
| Command | /icesmp reload |
| GUI | — |
| Automatikus trigger | Szerverindításkor felépíti, leállításkor lezárja a szolgáltatásokat. |
| Permission | — |
| Kapcsolódó config | `general.*`, valamint az egyes alrendszerek saját gyökérszakaszai. |
| Builder-/világ-előkészítés | Nincs külön világépítési feladat; deployment előtt mentés és staging-indítás szükséges. |
| Persistence | A szolgáltatások saját store-jait koordinálja; önmagában nem játékosadat. |
| Reloadviselkedés | A `/icesmp reload` támogatott részeket frissít; scheduler-periódus vagy strukturális változás restartot igényelhet. |
| Ismert korlát | A sikeres CI nem bizonyítja a teljes Folia/Paper production életciklust. |
| Forrásbizonyíték és részfunkciók | 6 komponens (COMPONENT: 5, LISTENER: 1); részfunkció-azonosítók: `player-session-cleanup`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Konfiguráció, üzenetek és validáció

| Mező | Érték |
|---|---|
| Funkcióazonosító | `core.configuration` |
| Release-státusz | **Aktív, adminisztratív** |
| Deployed JAR-hoz képesti státusz | **Jelentősen megváltozott** |
| Célközönség | Admin, Fejlesztő/üzemeltető, Tesztelő |
| Közérthető leírás | A csomagolt YAML-alapértékeket, az élő felülírásokat és a lokalizált üzeneteket egységes snapshotként kezeli. |
| Elérés | `/icesmp reload`, `/icesmp config`, konfigurációs GUI és fájlszerkesztés. |
| Command | /icesmp config; /icesmp reload |
| GUI | Config menü |
| Automatikus trigger | Induláskor és reloadkor beolvasás/ellenőrzés történik. |
| Permission | Kapcsolódó/ágankénti követelmény: `icesmp.admin.config` |
| Kapcsolódó config | Minden aktív config; részletesen a konfigurációs referenciában. |
| Builder-/világ-előkészítés | Az élő configot verziózott mentéssel kell kezelni; világazonosítók és helyek módosítása előtt staging-ellenőrzés kell. |
| Persistence | A konfiguráció fájlban tartós; a runtime snapshot memóriában él. |
| Reloadviselkedés | A támogatott cache-ek atomikusan frissülnek; bizonyos struktúrák és már futó taskok restartot igényelnek. |
| Ismert korlát | Nincs minden kulcshoz teljes schema-validator; hibás típusnál fallback vagy alrendszer-letiltás is lehetséges. |
| Forrásbizonyíték és részfunkciók | 6 komponens (COMPONENT: 1, GUI: 1, GUI_HOLDER: 1, LISTENER: 1, MANAGER: 2); részfunkció-azonosítók: `config`, `config-menu`, `config-menu-gui`, `message`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Persistence, naplók és recovery

| Mező | Érték |
|---|---|
| Funkcióazonosító | `core.persistence` |
| Release-státusz | **Tesztelési vagy rollout-kapu alatt** |
| Deployed JAR-hoz képesti státusz | **Belső megbízhatósági javítás** |
| Célközönség | Admin, Fejlesztő/üzemeltető, Tesztelő |
| Közérthető leírás | Koordinált YAML-store-ok, tranzakciós naplók, sérült állapot felismerése és kritikus írási hibák láthatóvá tétele. |
| Elérés | Automatikus; egyes recovery-folyamatokhoz adminparancs vagy logellenőrzés tartozik. |
| Command | — |
| GUI | — |
| Automatikus trigger | Állapotváltozáskor, periodikus flushkor, reload/disable alatt és indulási recoverykor. |
| Permission | — |
| Kapcsolódó config | Az érintett rendszerek persistence-, audit- és recovery-beállításai. |
| Builder-/világ-előkészítés | Nincs builderfeladat; rendszeres mentés, írási jog és elegendő tárhely szükséges. |
| Persistence | YAML-state, tranzakciós journal, blokk-regenerációs journal és rendszer-specifikus store-ok. |
| Reloadviselkedés | Reload előtt flush és új snapshot; egyes journal-recovery csak induláskor fut. |
| Ismert korlát | Lemezhiba és félbeszakított tranzakció csak célzott fault-injection teszttel igazolható. |
| Forrásbizonyíték és részfunkciók | 7 komponens (PERSISTENT_STORE: 7); részfunkció-azonosítók: `block-regen-journal`, `corrupt-state-file-error`, `critical-persistence-write-error`, `persistent`, `persistent-store-coordinator`, `transaction-journal`, `yaml`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Külső integrációs hidak

| Mező | Érték |
|---|---|
| Funkcióazonosító | `core.integrations` |
| Release-státusz | **Aktív, külső integrációval bővül** |
| Deployed JAR-hoz képesti státusz | **Jelentősen megváltozott** |
| Célközönség | Admin, Builder, Fejlesztő/üzemeltető, Tesztelő |
| Közérthető leírás | LuckPerms-, védelem-, placeholder- és FancyNpcs-kapcsolatok, valamint kaszt-/kémálca kompatibilitási pontok. |
| Elérés | Automatikus soft-dependency felismerés; NPC-kötés és pluginonkénti konfiguráció. |
| Command | — |
| GUI | — |
| Automatikus trigger | A partnerplugin jelenlétekor és az érintett eseményeknél. |
| Permission | — |
| Kapcsolódó config | `integrations.*`, protection-, NPC- és placeholder-beállítások. |
| Builder-/világ-előkészítés | NPC-ket és védett régiókat a tényleges partnerpluginban is létre kell hozni. |
| Persistence | Az IceSMP saját kötéseket tárolhat; a külső plugin adatai nem részei az IceSMP-mentésnek. |
| Reloadviselkedés | A legtöbb híd következő eseménynél az új snapshotot használja; pluginlista-változás restarthoz kötött. |
| Ismert korlát | A pontos működés a telepített külső plugin verziójától és élő configjától függ. |
| Forrásbizonyíték és részfunkciók | 4 komponens (COMPONENT: 1, INTEGRATION: 3); részfunkció-azonosítók: `fancy-npcs-quest`, `luck-perms`, `protection`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Közös végrehajtási és segédszolgáltatások

| Mező | Érték |
|---|---|
| Funkcióazonosító | `core.shared_services` |
| Release-státusz | **Aktív, adminisztratív** |
| Deployed JAR-hoz képesti státusz | **Belső megbízhatósági javítás** |
| Célközönség | Fejlesztő/üzemeltető, Tesztelő |
| Közérthető leírás | Közös dispatch, tab completion, ütemezésbiztos callbackek, pozíció- és játékmód-cache, szöveg- és vizuális segédek. |
| Elérés | Közvetlen játékosbelépési pont nincs; más funkciók használják. |
| Command | — |
| GUI | — |
| Automatikus trigger | Az őket hívó parancsok, GUI-k és listenerek részeként. |
| Permission | — |
| Kapcsolódó config | Több alrendszer configja; nincs egyetlen közös publikus gyökér. |
| Builder-/világ-előkészítés | Nincs közvetlen builderfeladat. |
| Persistence | Általában memóriabeli; a tulajdonos rendszer felel a tartósságért. |
| Reloadviselkedés | A tulajdonos rendszer reloadszabálya érvényes. |
| Ismert korlát | Önálló játékosfunkcióként nem értelmezhető; a komponensmap a teljes technikai lefedettség miatt tartalmazza. |
| Forrásbizonyíték és részfunkciók | 8 komponens (COMMAND: 1, COMPONENT: 7); részfunkció-azonosítók: `abstract-dispatch`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

## Adminisztráció és moderáció

### Admin- és konfigurációs kezelőfelület

| Mező | Érték |
|---|---|
| Funkcióazonosító | `admin.control_panel` |
| Release-státusz | **Aktív, adminisztratív** |
| Deployed JAR-hoz képesti státusz | **Új** |
| Célközönség | Admin, Fejlesztő/üzemeltető, Tesztelő |
| Közérthető leírás | Jogosultságszűrt admin súgó, reload, config-kezelés, inspect és főmenüből elérhető adminműveletek. |
| Elérés | `/icesmp`, `/icesmp reload`, `/icesmp config`, admin főmenü. |
| Command | /icesmp (alias: /ismp) |
| GUI | — |
| Automatikus trigger | Csak explicit parancs vagy GUI-kattintás. |
| Permission | Kapcsolódó/ágankénti követelmény: `icesmp.admin.all`; `icesmp.admin.config`; `icesmp.admin.inspect`; `icesmp.admin.reload`; `icesmp.admin.reload + icesmp.admin.config`; `icesmp.admin.reload + icesmp.admin.inspect` |
| Kapcsolódó config | `general.*`, GUI- és permissionbeállítások. |
| Builder-/világ-előkészítés | Nincs világépítési feladat; szerepkörönként külön permissionmátrix kell. |
| Persistence | Az adminműveletek által módosított config/store tartós lehet; a GUI-state nem. |
| Reloadviselkedés | A reloadág a támogatott alrendszereket frissíti. |
| Ismert korlát | Kritikus jog; ne adj teljes admin-parentet napi moderátori szerepkörnek. |
| Forrásbizonyíték és részfunkciók | 2 komponens (COMMAND: 1, COMPONENT: 1); részfunkció-azonosítók: `ice-smp`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Natív büntetési és moderációs rendszer

| Mező | Érték |
|---|---|
| Funkcióazonosító | `admin.moderation` |
| Release-státusz | **Tesztelési vagy rollout-kapu alatt** |
| Deployed JAR-hoz képesti státusz | **Új** |
| Célközönség | Moderátor, Admin, Tesztelő, Fejlesztő/üzemeltető |
| Közérthető leírás | Warning, kick, mute, temp mute, ban, temp ban, feloldás, előzmény, aktív büntetés, GUI, audit és expiry. |
| Elérés | `/warn`, `/kick`, `/mute`, `/tempban`, `/ban`, `/unmute`, `/unban`, `/history`, `/punishments`, `/moderation`. |
| Command | /ban; /history; /kick; /moderation (alias: /mod); /mute; /punishments; /tempban; /unban; /unmute; /warn |
| GUI | Moderációs GUI |
| Automatikus trigger | Login-ellenőrzés, chatblokkolás, büntetés-expiry és adminművelet. |
| Permission | Kapcsolódó/ágankénti követelmény: `icesmp.admin.moderation`; `icesmp.moderation.ban`; `icesmp.moderation.gui`; `icesmp.moderation.history`; `icesmp.moderation.inventory.edit`; `icesmp.moderation.inventory.read`; `icesmp.moderation.kick`; `icesmp.moderation.mute`; `icesmp.moderation.offlinetp`; `icesmp.moderation.socialspy`; `icesmp.moderation.vanish`; `icesmp.moderation.warn` |
| Kapcsolódó config | `moderation.*`, kapcsolódó messages és permissions. |
| Builder-/világ-előkészítés | Nincs builderfeladat; előre rögzített permissionmátrix és ügyrend kell. |
| Persistence | Büntetések, audit és expiry-adatok tartós store-ban. |
| Reloadviselkedés | Szöveg/policy reloadolható; aktív expiry és recovery restarttesztet igényel. |
| Ismert korlát | SModeration csak restart-, corrupt-state-, lemezhiba- és permissionteszt után távolítható el. |
| Forrásbizonyíték és részfunkciók | 27 komponens (COMMAND: 7, COMPONENT: 14, GUI: 1, GUI_HOLDER: 1, LISTENER: 3, MANAGER: 1); részfunkció-azonosítók: `active-punishments`, `chat-moderation`, `moderation`, `moderation-action`, `moderation-gui`, `moderation-login`, `moderation-revoke`, `mute`, `punishment-history`, `unmute`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Report, privát üzenet, reply és SocialSpy

| Mező | Érték |
|---|---|
| Funkcióazonosító | `admin.reports_messaging` |
| Release-státusz | **Tesztelési vagy rollout-kapu alatt** |
| Deployed JAR-hoz képesti státusz | **Új** |
| Célközönség | Játékos, Moderátor, Admin, Tesztelő |
| Közérthető leírás | Játékosreportok, kezelői reportnézet, privát üzenetek, reply partner és jogosultságkötött SocialSpy. |
| Elérés | `/report`, `/reports`, `/msg`, `/tell`, `/w`, `/reply`, `/socialspy`, `/suttogas`. |
| Command | /msg; /reply (alias: /r); /report (alias: /bejelent); /reports; /socialspy; /suttogas (alias: /sutt); /tell; /w |
| GUI | — |
| Automatikus trigger | Parancs, report-feedback esemény és SocialSpy figyelés. |
| Permission | Kapcsolódó/ágankénti követelmény: `icesmp.admin.moderation`; `icesmp.message`; `icesmp.moderation.socialspy` |
| Kapcsolódó config | `moderation.*`, private-message-, report-, SocialSpy- és message-beállítások. |
| Builder-/világ-előkészítés | Nincs builderfeladat; adatkezelési és hozzáférési szabály kell. |
| Persistence | Reportok tartósak; reply-partner és egyes SocialSpy-sessionállapotok memóriabeliek. |
| Reloadviselkedés | Policy és szövegek reloadolhatók; quit–reconnect viselkedést runtime kell igazolni. |
| Ismert korlát | A privát kommunikáció érzékeny adat; csak kijelölt szerepkör kapjon megfigyelési jogot. |
| Forrásbizonyíték és részfunkciók | 10 komponens (COMMAND: 5, COMPONENT: 1, LISTENER: 2, MANAGER: 2); részfunkció-azonosítók: `private-message`, `report`, `report-feedback`, `reports`, `social-spy`, `whisper`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Vanish és láthatóság

| Mező | Érték |
|---|---|
| Funkcióazonosító | `admin.vanish` |
| Release-státusz | **Tesztelési vagy rollout-kapu alatt** |
| Deployed JAR-hoz képesti státusz | **Új** |
| Célközönség | Moderátor, Admin, Tesztelő |
| Közérthető leírás | Moderátori rejtőzés, külön láthatósági jog és kapcsolódó játékosszám-/megjelenítéskezelés. |
| Elérés | `/vanish`; jogosultság alapján más staff láthatja. |
| Command | /vanish (alias: /v) |
| GUI | — |
| Automatikus trigger | Állapotváltás, join/quit, player-info és server-list frissítés. |
| Permission | Kapcsolódó/ágankénti követelmény: `icesmp.admin.moderation`; `icesmp.moderation.vanish`; `icesmp.moderation.vanish.see` |
| Kapcsolódó config | `vanish.*`, `motd.*`, `tablist.*` és permissions. |
| Builder-/világ-előkészítés | Nincs builderfeladat. |
| Persistence | A vanish állapot rendszerbeállítástól függően tartós vagy sessionjellegű; reconnectet tesztelni kell. |
| Reloadviselkedés | Policy reloadolható; már elküldött láthatósági csomagok következő frissítéskor rendeződnek. |
| Ismert korlát | Más vanish/tablist pluginnal való együttműködés csak runtime igazolható. |
| Forrásbizonyíték és részfunkciók | 3 komponens (COMMAND: 1, LISTENER: 1, MANAGER: 1); részfunkció-azonosítók: `vanish`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Inventory-admin, escrow és offline teleport

| Mező | Érték |
|---|---|
| Funkcióazonosító | `admin.inventory_teleport` |
| Release-státusz | **Tesztelési vagy rollout-kapu alatt** |
| Deployed JAR-hoz képesti státusz | **Új** |
| Célközönség | Moderátor, Admin, Tesztelő, Fejlesztő/üzemeltető |
| Közérthető leírás | Online inventory/ender chest olvasás és szerkesztés, biztonságos escrow/recovery, valamint utolsó ismert helyre offline teleport. |
| Elérés | `/invsee`, `/offlinetp`; Invsee GUI. |
| Command | /invsee; /offlinetp |
| GUI | Invsee |
| Automatikus trigger | Explicit adminművelet, bezárás, disconnect, reconnect és indulási recovery. |
| Permission | Kapcsolódó/ágankénti követelmény: `icesmp.admin.moderation`; `icesmp.moderation.inventory.edit`; `icesmp.moderation.inventory.read`; `icesmp.moderation.offlinetp`; `read: icesmp.moderation.inventory.read; edit: icesmp.moderation.inventory.edit` |
| Kapcsolódó config | `moderation.*`, invsee-, escrow-, audit- és permissionbeállítások. |
| Builder-/világ-előkészítés | Nincs builderfeladat; recovery-helyet és manuális eljárást dokumentálni kell. |
| Persistence | Escrow és utolsó ismert hely tartós; nyitott GUI sessionállapot. |
| Reloadviselkedés | Config reloadolható, de függőben lévő escrow-nál előbb settlement/recovery szükséges. |
| Ismert korlát | InvSee++ csak disconnect/restart/lemezhiba és read/edit permissionteszt után távolítható el. |
| Forrásbizonyíték és részfunkciók | 12 komponens (COMMAND: 2, COMPONENT: 6, GUI: 1, GUI_HOLDER: 1, LISTENER: 1, MANAGER: 1); részfunkció-azonosítók: `invsee`, `invsee-gui`, `offline-teleport`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

## Játékosállapot és szervermegjelenítés

### Globális AFK-rendszer

| Mező | Érték |
|---|---|
| Funkcióazonosító | `player.global_afk` |
| Release-státusz | **Tesztelési vagy rollout-kapu alatt** |
| Deployed JAR-hoz képesti státusz | **Új** |
| Célközönség | Játékos, Admin, Tesztelő |
| Közérthető leírás | Kézi és inaktivitásalapú AFK-állapot, tablistajelzés és útvonalanként eltérően konfigurálható jutalomblokkolás, jutalmazó zónák nélkül. |
| Elérés | `/afk`; az aktivitás visszavonja az állapotot. |
| Command | /afk |
| GUI | — |
| Automatikus trigger | Inaktivitási idő, mozgás és játékosaktivitás; a profession- és közös kill/boss jutalomkapuk configot is olvasnak, a fishing windfall és ambient pénzjutalom AFK esetén feltétel nélkül tilt. |
| Permission | — |
| Kapcsolódó config | `afk.afk-after-seconds`, `afk.block-rewards`. |
| Builder-/világ-előkészítés | Nincs AFK-zóna, bossbar vagy jutalmazó terület; builder-előkészítés nem kell. |
| Persistence | Nincs tartós AFK-store; sessionállapot. |
| Reloadviselkedés | Timeout, profession-XP és a közös kill/boss jutalomkapu reload után az új snapshotból működik; a fishing windfall és ambient jutalom tiltása nem configvezérelt. |
| Ismert korlát | A `afk.block-rewards` nem univerzális főkapcsoló: profession-XP-re közvetlenül, kill/boss útvonalra a `kill-rewards.afk-block` fallbackjeként hat; fishing windfall és ambient pénzjutalom mindig tiltott AFK esetén. A release-ben nincs `afk.zones`, zónajutalom vagy AFK-bossbar. |
| Forrásbizonyíték és részfunkciók | 4 komponens (COMMAND: 1, COMPONENT: 1, LISTENER: 1, MANAGER: 1); részfunkció-azonosítók: `afk`, `afk-activity`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Főmenü, profil és karakterlap

| Mező | Érték |
|---|---|
| Funkcióazonosító | `player.menus_profile` |
| Release-státusz | **Aktív és játékosok számára elérhető** |
| Deployed JAR-hoz képesti státusz | **Jelentősen megváltozott** |
| Célközönség | Játékos, Admin, Tesztelő |
| Közérthető leírás | Központi játékosmenük, karakteradatok, tematikus navigáció és jogosultságfüggő adminbelépési pont. |
| Elérés | `/menu`, `/profile` és több tematikus parancs GUI-megnyitása. |
| Command | /menu (alias: /hub, /m); /profile (alias: /char, /karakter, /status) |
| GUI | Főmenü és tematikus parancsmenük; Karakterlap; Specializációk; Szakmaválasztó; Talent-fa |
| Automatikus trigger | Kizárólag parancs vagy GUI-kattintás. |
| Permission | Kapcsolódó/ágankénti követelmény: `icesmp.admin.config`; `icesmp.admin.events`; `icesmp.admin.exchangeboard`; `icesmp.admin.item`; `icesmp.admin.npc`; `icesmp.admin.quest`; `icesmp.admin.reload` |
| Kapcsolódó config | GUI-, profil-, általános és üzenetbeállítások. |
| Builder-/világ-előkészítés | Nincs kötelező világépítési feladat; resource-pack modellek megjelenését ellenőrizni kell. |
| Persistence | A profil mögötti játékosadat tartós; a megnyitott GUI-state sessionjellegű. |
| Reloadviselkedés | Megjelenési config következő megnyitáskor frissül; struktúraváltásnál újranyitás kell. |
| Ismert korlát | Egyes csempék csak akkor aktívak, ha a kapcsolódó rendszer és permission elérhető. |
| Forrásbizonyíték és részfunkciók | 11 komponens (COMMAND: 2, GUI: 1, GUI_COMPONENT: 4, GUI_HOLDER: 2, LISTENER: 2); részfunkció-azonosítók: `character-gui`, `character-menu-context`, `command-menu`, `command-menu-context`, `command-menus`, `gui-util`, `menu`, `profile`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### HUD és natív tablista

| Mező | Érték |
|---|---|
| Funkcióazonosító | `player.hud_tablist` |
| Release-státusz | **Tesztelési vagy rollout-kapu alatt** |
| Deployed JAR-hoz képesti státusz | **Jelentősen megváltozott** |
| Célközönség | Játékos, Admin, Tesztelő |
| Közérthető leírás | Kapcsolható HUD, rendezett tablista, szerep-/állapotjelzések és IceSMP-specifikus szerverinformációk. |
| Elérés | `/hud`; a tablista automatikus. |
| Command | /hud |
| GUI | — |
| Automatikus trigger | Csatlakozáskor, periodikus frissítéskor, státusz- és adatváltozáskor. |
| Permission | Kapcsolódó/ágankénti követelmény: `icesmp.admin.moderation`; `icesmp.moderation.vanish`; `icesmp.moderation.vanish.see` |
| Kapcsolódó config | `hud.*`, `tablist.*`. |
| Builder-/világ-előkészítés | Nincs builderfeladat; a meglévő TAB-plugin funkcióigényét deployment előtt fel kell mérni. |
| Persistence | A HUD-beállítás játékoshoz kötötten tárolható; a tablista runtime nézet. |
| Reloadviselkedés | Szövegek és megjelenési opciók reloadolhatók; futó frissítési periódus restartot igényelhet. |
| Ismert korlát | Nem cél a TAB teljes upstream-paritása. |
| Forrásbizonyíték és részfunkciók | 5 komponens (COMMAND: 1, COMPONENT: 1, LISTENER: 1, MANAGER: 2); részfunkció-azonosítók: `hud`, `tablist`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Natív MOTD és szerverlista-megjelenítés

| Mező | Érték |
|---|---|
| Funkcióazonosító | `player.motd` |
| Release-státusz | **Tesztelési vagy rollout-kapu alatt** |
| Deployed JAR-hoz képesti státusz | **Új** |
| Célközönség | Fejlesztő/üzemeltető, Admin, Tesztelő |
| Közérthető leírás | TIME/RANDOM MOTD-választás, eseményprioritás, vanished játékosok szűrése és ellenőrzött ikonmódok. |
| Elérés | Automatikus server-list ping; admin `/icesmp reload`. |
| Command | /icesmp reload |
| GUI | — |
| Automatikus trigger | Minden szerverlista-pingnél biztonságos snapshotból. |
| Permission | — |
| Kapcsolódó config | `motd.*`, ikon- és eseménybeállítások. |
| Builder-/világ-előkészítés | Az ikonfájlokat helyesen kell telepíteni; PNG, symlink és fallback viselkedést tesztelni kell. |
| Persistence | Nincs játékosállapot; a kiválasztási snapshot memóriabeli. |
| Reloadviselkedés | Gyors reload támogatott, de párhuzamos ping és scheduler rejection runtime tesztet igényel. |
| Ismert korlát | Az élő ikonfájlok és MiniMOTD nélküli production viselkedés forrásból nem bizonyítható. |
| Forrásbizonyíték és részfunkciók | 4 komponens (COMPONENT: 3, LISTENER: 1); részfunkció-azonosítók: `motd`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Chat, vizuális visszajelzés és harci kijelzés

| Mező | Érték |
|---|---|
| Funkcióazonosító | `player.chat_feedback` |
| Release-státusz | **Aktív, configgal engedélyezhető** |
| Deployed JAR-hoz képesti státusz | **Jelentősen megváltozott** |
| Célközönség | Játékos, Moderátor, Admin, Tesztelő |
| Közérthető leírás | Chat-formázás, üzenetkezelés, sebzésjelzés, halálösszegzés, alacsony élet jelzése és ideiglenes vizuális effektek. |
| Elérés | Automatikus; az üzenetszövegek configból érkeznek. |
| Command | — |
| GUI | — |
| Automatikus trigger | Chat-, sebzés-, halál- és állapot-eseményeknél. |
| Permission | — |
| Kapcsolódó config | `messages.*`, chat-, HUD- és vizuális beállítások. |
| Builder-/világ-előkészítés | Nincs világépítési feladat. |
| Persistence | Többségében sessionállapot; statisztika vagy moderáció külön store-ba kerülhet. |
| Reloadviselkedés | Üzenetek reloadolhatók; aktív ideiglenes effektek nem feltétlenül épülnek újra. |
| Ismert korlát | Más chat/HUD pluginnal való prioritáskülönbséget runtime kell ellenőrizni. |
| Forrásbizonyíték és részfunkciók | 10 komponens (COMPONENT: 5, LISTENER: 5); részfunkció-azonosítók: `chat-format`, `damage-indicator`, `death-recap`, `display-fx-cleanup`, `low-health-border`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Beléptetés és kezdőfolyamat

| Mező | Érték |
|---|---|
| Funkcióazonosító | `player.onboarding` |
| Release-státusz | **Aktív, builder-előkészítést igényel** |
| Deployed JAR-hoz képesti státusz | **Jelentősen megváltozott** |
| Célközönség | Játékos, Admin, Builder, Tesztelő |
| Közérthető leírás | Első belépéshez és bevezető történeti lépésekhez kapcsolódó automatikus játékosfolyamat. |
| Elérés | Első belépés és kapcsolódó introállapot. |
| Command | — |
| GUI | — |
| Automatikus trigger | Join, respawn vagy konfigurált bevezető trigger. |
| Permission | — |
| Kapcsolódó config | Intro-, onboarding-, world- és message-beállítások. |
| Builder-/világ-előkészítés | Spawn-, bevezető helyszín és biztonságos teleportpont szükséges lehet. |
| Persistence | A teljesített bevezető állapota tartós. |
| Reloadviselkedés | Szöveg és célpont frissülhet; folyamatban lévő játékosnál staging-próba kell. |
| Ismert korlát | A tényleges élő world és már bevezetett játékosállomány nincs a repositoryban. |
| Forrásbizonyíték és részfunkciók | 3 komponens (LISTENER: 2, MANAGER: 1); részfunkció-azonosítók: `intro`, `onboarding`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

## Karakterfejlődés és tartalom

### Kasztok, specializációk és kasztképességek

| Mező | Érték |
|---|---|
| Funkcióazonosító | `progression.classes_specializations` |
| Release-státusz | **Aktív és játékosok számára elérhető** |
| Deployed JAR-hoz képesti státusz | **Jelentősen megváltozott** |
| Célközönség | Játékos, Admin, Tesztelő, Eventes |
| Közérthető leírás | Kasztválasztás, XP/szint, specializáció, class health, kasztpasszívok és admin XP/unlock műveletek. |
| Elérés | `/class`, `/spec`, kaszt- és specializációs GUI. |
| Command | /class (alias: /job, /kaszt); /spec (alias: /specializacio, /specialization) |
| GUI | Kasztválasztó; Specializációk |
| Automatikus trigger | Választás, XP-források, szintlépés, képességfeloldás és kapcsolódó combat/craft esemény. |
| Permission | Kapcsolódó/ágankénti követelmény: `icesmp.admin`; `icesmp.admin.job`; `icesmp.admin.spec`; `icesmp.job.admin` |
| Kapcsolódó config | `classes.*`, `spells.*`, specialization- és ability-definíciók. |
| Builder-/világ-előkészítés | Nincs kötelező helyszín; resource-pack ikonok és balance-adatok tesztelendők. |
| Persistence | Kaszt, XP, specializáció és unlockok játékosonként tartósak. |
| Reloadviselkedés | Balance részben reloadolható; új enum/registry-szerkezet restartot igényel. |
| Ismert korlát | A konkrét élő balance és már létező játékosadat-migráció az élő config nélkül nem bizonyítható. |
| Forrásbizonyíték és részfunkciók | 25 komponens (COMMAND: 2, COMPONENT: 11, GUI: 2, GUI_HOLDER: 2, LISTENER: 4, MANAGER: 3, SERVICE: 1); részfunkció-azonosítók: `ability-catalyst`, `bard`, `class-health`, `class-xp`, `job`, `job-craft-restriction`, `job-gui`, `spec`, `specialization`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Talent- és képességfák

| Mező | Érték |
|---|---|
| Funkcióazonosító | `progression.talents_skills` |
| Release-státusz | **Aktív és játékosok számára elérhető** |
| Deployed JAR-hoz képesti státusz | **Jelentősen megváltozott** |
| Célközönség | Játékos, Admin, Tesztelő, Eventes |
| Közérthető leírás | Pontelosztás, követelmények, attribútumhatások, skill-tree GUI és respec. |
| Elérés | `/talent`; talent- és skill-tree GUI. |
| Command | /talent (alias: /talentfa, /talents) |
| GUI | Képességfa; Talent-fa |
| Automatikus trigger | Pontköltés, respec, belépés és attribútumfrissítés. |
| Permission | — |
| Kapcsolódó config | `classes.*`, talent- és skill-tree szekciók. |
| Builder-/világ-előkészítés | Nincs világépítési feladat; GUI/resource-pack megjelenést ellenőrizni kell. |
| Persistence | Pontok, választások és respecállapot tartós. |
| Reloadviselkedés | Megjelenés és balance reloadolható; fa-struktúra változásához restart/migrációteszt kell. |
| Ismert korlát | Hibás vagy körkörös követelmény csak teljes adatvalidációval zárható ki. |
| Forrásbizonyíték és részfunkciók | 9 komponens (COMMAND: 1, GUI: 2, GUI_HOLDER: 2, LISTENER: 2, MANAGER: 1, SERVICE: 1); részfunkció-azonosítók: `respec`, `skill-tree`, `skill-tree-gui`, `talent`, `talent-attribute`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Varázslatok, mastery és varázskönyv

| Mező | Érték |
|---|---|
| Funkcióazonosító | `progression.spells` |
| Release-státusz | **Aktív és játékosok számára elérhető** |
| Deployed JAR-hoz képesti státusz | **Jelentősen megváltozott** |
| Célközönség | Játékos, Admin, Tesztelő, Eventes |
| Közérthető leírás | Regisztrált spellkatalógus, célzás, költség, cooldown, projectile/state kezelés, kedvencek, mastery és varázskönyv. |
| Elérés | `/spell`, `/spellbook`, kasztparancs spellágai és Spellbook GUI. |
| Command | /spell (alias: /mastery, /mesterseg, /spells); /spellbook (alias: /konyv, /sb, /varazskonyv) |
| GUI | Varázskönyv |
| Automatikus trigger | Cast, projectile, sebzés, állapot, feloldás és GUI-művelet. |
| Permission | Kapcsolódó/ágankénti követelmény: `icesmp.admin.job` |
| Kapcsolódó config | `spells.*`, `spell-balance.*`, VFX- és ability-beállítások. |
| Builder-/világ-előkészítés | Nincs általános helyszín; aréna- és védelemkompatibilitás, VFX és resource pack tesztelendő. |
| Persistence | Unlock, kedvenc és mastery játékosonként tartós; aktív cast/state runtime. |
| Reloadviselkedés | Balance castkor olvasható; registry/új spell struktúraváltása restartot igényel. |
| Ismert korlát | Folia entity/régióhatár, projectile és PVP-protection viselkedését runtime kell ellenőrizni. |
| Forrásbizonyíték és részfunkciók | 73 komponens (COMMAND: 2, COMPONENT: 7, GUI: 1, GUI_HOLDER: 1, LISTENER: 4, MANAGER: 2, SPELL: 52, SPELL_COMPONENT: 4); részfunkció-azonosítók: `angry-chicken`, `antidote`, `armament`, `base`, `bee-swarm`, `blink`, `bone-chill`, `bulwark`, `chains-of-ice`, `configured`, `confusion`, `deep-breath`, `demonic-circle`, `devotion-aura`, `double-jump`, `druid-form`, `eagle-eye`, `expel-harm`, `feast`, `featherfoot`, `flying-serpent-kick`, `friendship`, `frost-fever`, `glaive-throw`, `gust`, `hide`, `holy-wrath`, `inner-focus`, `life-drain`, `living-flame`, `lucky-star`, `mind-blast`, `multishot`, `primal-bond`, `projectile-burst`, `rain-dance`, `root`, `rune-strike`, `shadowburn`, `shadowstep`, `shaman-totem`, `smoke-bomb`, `soul-exchange`, `spectral-sight`, `spell`, `spell-catalog`, `spell-cost-type`, `spell-damage`, `spell-favorites`, `spell-mastery`, `spell-projectile`, `spell-state`, `spell-targeting-util`, `spellbook`, `spinning-crane-kick`, `summon-minions`, `sun-dance`, `venom-strike`, `whirlwind`, `wild-mushroom`, `wisplight`, `wolf-call`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Professionök, specializációk és heti célok

| Mező | Érték |
|---|---|
| Funkcióazonosító | `progression.professions` |
| Release-státusz | **Aktív, builder-előkészítést igényel** |
| Deployed JAR-hoz képesti státusz | **Jelentősen megváltozott** |
| Célközönség | Játékos, Admin, Builder, Tesztelő, Eventes |
| Közérthető leírás | Nyolc profession, szakmai specializációk, XP, heti cél, gyűjtési bónusz és szakmai GUI. |
| Elérés | `/profession`, `/szakmacel`, profession GUI. |
| Command | /profession (alias: /prof, /szakma); /szakmacel (alias: /weeklygoal) |
| GUI | Szakmaválasztó |
| Automatikus trigger | Gyűjtés, craft, heti reset/cél és GUI-választás. |
| Permission | Kapcsolódó/ágankénti követelmény: `icesmp.admin.profession` |
| Kapcsolódó config | `professions.*`, `profession-materials.*`, heti cél és resource beállítások. |
| Builder-/világ-előkészítés | Szakmai alapanyagforrások, biztonságos farmok és esetleges munkaállomások ellenőrzendők. |
| Persistence | Profession, XP, specializáció és heti cél állapota tartós. |
| Reloadviselkedés | Balance és receptek célzott reloadot kapnak; periódus/reset scheduler restartot igényelhet. |
| Ismert korlát | WorldEdit és lootforrás-változás után a fejlődési sebességet újra kell mérni. |
| Forrásbizonyíték és részfunkciók | 13 komponens (COMMAND: 2, COMPONENT: 2, GUI: 1, GUI_HOLDER: 1, LISTENER: 2, MANAGER: 4, SERVICE: 1); részfunkció-azonosítók: `gathering-buff`, `profession`, `profession-weekly`, `profession-weekly-goal`, `profession-xp`, `resource`, `resource-bonus`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Crafting, receptek, blueprintök és katalizátorok

| Mező | Érték |
|---|---|
| Funkcióazonosító | `progression.crafting_blueprints` |
| Release-státusz | **Aktív, builder-előkészítést igényel** |
| Deployed JAR-hoz képesti státusz | **Jelentősen megváltozott** |
| Célközönség | Játékos, Admin, Builder, Tesztelő, Eventes |
| Közérthető leírás | Szakmai receptkönyv, craft-korlátok, blueprint-feloldás, katalizátorvédelem és masterwork craft. |
| Elérés | Crafting események, blueprint item, szakmai receptkönyv GUI. |
| Command | — |
| GUI | Szakmai receptkönyv |
| Automatikus trigger | Craft-előkészítés/befejezés, blueprint használat és item-validáció. |
| Permission | — |
| Kapcsolódó config | `crafting.*`, `profession-recipes.*`, `profession-materials.*`, itemdefiníciók. |
| Builder-/világ-előkészítés | Craftállomások, recept-hozzávalók és resource-pack itemmodellek ellenőrzendők. |
| Persistence | Blueprint/unlock és szakmai állapot tartós; craft tranzakció eseményalapú. |
| Reloadviselkedés | Receptcache célzottan reloadolható; strukturális registry-váltás restartot igényelhet. |
| Ismert korlát | Tömeges vagy automatizált craft és inventory-failure útvonalakat runtime kell tesztelni. |
| Forrásbizonyíték és részfunkciók | 14 komponens (COMPONENT: 3, GUI: 1, GUI_HOLDER: 1, ITEM_FACTORY: 1, LISTENER: 6, MANAGER: 2); részfunkció-azonosítók: `blueprint-item`, `blueprint-use`, `catalyst-craft-safety`, `catalyst-protection`, `crafting-restriction`, `masterwork-craft`, `profession-recipe`, `profession-recipe-book`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Unique itemek, ritkaság, rúnák és signature tárgyak

| Mező | Érték |
|---|---|
| Funkcióazonosító | `progression.items_rarity` |
| Release-státusz | **Aktív, builder-előkészítést igényel** |
| Deployed JAR-hoz képesti státusz | **Jelentősen megváltozott** |
| Célközönség | Játékos, Admin, Builder, Tesztelő, Eventes |
| Közérthető leírás | Adatvezérelt ritkaság, egyedi anyag, item-provenance, rúnázás, signature enchantok és tárgyvédelmek. |
| Elérés | Craft, loot, itemhasználat, anvil/rúna esemény; admin itemadás. |
| Command | — |
| GUI | — |
| Automatikus trigger | Item létrehozása, frissítése, craftja, lootja és használata. |
| Permission | — |
| Kapcsolódó config | `item-rarity.*`, `dev-items.*`, crafting-, rune- és signature-item definíciók. |
| Builder-/világ-előkészítés | Resource-pack CustomModelData, lootforrások és displaynevek ellenőrzendők. |
| Persistence | Az itemadat magában az itemben és egyes dev-item state-ekben tartós. |
| Reloadviselkedés | Definíciók részben reloadolhatók; meglévő itemek frissítő listeneren vagy újrageneráláskor változnak. |
| Ismert korlát | Meglévő, régi metadata-s itemek migrációját production mintán kell tesztelni. |
| Forrásbizonyíték és részfunkciók | 11 komponens (COMPONENT: 1, ITEM: 1, ITEM_FACTORY: 3, LISTENER: 5, SERVICE: 1); részfunkció-azonosítók: `catalyst-item`, `item-data`, `item-provenance`, `item-rarity`, `rune-apply`, `rune-effect`, `school-counter-anvil`, `signature-enchant-keys`, `signature-item`, `unique-material`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Relikviák, lelkek és soulforge

| Mező | Érték |
|---|---|
| Funkcióazonosító | `progression.relics_souls` |
| Release-státusz | **Aktív, builder-előkészítést igényel** |
| Deployed JAR-hoz képesti státusz | **Jelentősen megváltozott** |
| Célközönség | Játékos, Admin, Builder, Tesztelő, Eventes |
| Közérthető leírás | Egyedi relikviák, ownership/transfer, triggerelt képességek, cooldown, soul shard/soulstone és soulforge. |
| Elérés | `/relic`, `/souls`, `/soulforge`; itemhasználat és craft. |
| Command | /relic (alias: /relics, /relikvia); /soulforge (alias: /lelekkovacs); /souls (alias: /lelek, /soul) |
| GUI | — |
| Automatikus trigger | Relikvia-trigger, PVP transfer, inactivity, craft és soul esemény. |
| Permission | Kapcsolódó/ágankénti követelmény: `icesmp.admin.relic`; `icesmp.relic.admin` |
| Kapcsolódó config | `relics.*`, soul-, ritual-, loot- és itemdefiníciók. |
| Builder-/világ-előkészítés | Soulforge/rituálé helyszín és resource-pack itemek ellenőrzendők, ha a config fizikai helyet kér. |
| Persistence | Tulajdonjog, cooldown, lelkek és forgeállapot tartós. |
| Reloadviselkedés | Relic cache célzott reloadot kap; aktív cooldown/ownership változást stagingen kell ellenőrizni. |
| Ismert korlát | PVP transfer, full inventory és disconnect közbeni átadás runtime tesztet igényel. |
| Forrásbizonyíték és részfunkciók | 27 komponens (COMMAND: 3, COMPONENT: 9, ITEM_FACTORY: 1, LISTENER: 9, MANAGER: 4, SERVICE: 1); részfunkció-azonosítók: `elytra-relic`, `metelytepo`, `metelytepo-relic`, `relic`, `relic-cooldown`, `relic-craft-safety`, `relic-inactivity`, `relic-item`, `relic-item-refresh`, `relic-pvp-transfer`, `relic-trigger`, `soul`, `soul-shard`, `soulforge`, `soulstone`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Achievementek, advancementek, statisztikák és leaderboard

| Mező | Érték |
|---|---|
| Funkcióazonosító | `progression.achievements_stats` |
| Release-státusz | **Aktív és játékosok számára elérhető** |
| Deployed JAR-hoz képesti státusz | **Jelentősen megváltozott** |
| Célközönség | Játékos, Admin, Tesztelő, Eventes |
| Közérthető leírás | Mérföldkövek és jutalmak, datapack advancementek, harci statisztika és ranglisták. |
| Elérés | `/achievements`, `/stats`, `/leaderboard`; főmenü. |
| Command | /achievements (alias: /ach, /eleresek); /leaderboard (alias: /lb, /rangsor, /top); /stats |
| GUI | — |
| Automatikus trigger | Játékmeneti progress, harci esemény, jutalomátvétel és lekérdezés. |
| Permission | Kapcsolódó/ágankénti követelmény: `icesmp.admin.crate` |
| Kapcsolódó config | Achievement-, advancement-, stat- és leaderboard-definíciók. |
| Builder-/világ-előkészítés | Nincs kötelező helyszín; jutalmak és datapack/resource-pack betöltés ellenőrzendő. |
| Persistence | Progress, átvett jutalom és statisztika tartós. |
| Reloadviselkedés | Definíciók következő kiértékeléskor használhatók; advancement JSON változás restart/reload tesztet igényel. |
| Ismert korlát | Leaderboard cache és nagy adatállomány teljesítménye csak valós adatmennyiséggel mérhető. |
| Forrásbizonyíték és részfunkciók | 7 komponens (COMMAND: 3, LISTENER: 1, MANAGER: 2, SERVICE: 1); részfunkció-azonosítók: `achievement`, `achievements`, `advancement`, `leaderboard`, `stats`, `stats-combat`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Questek, napi küldetések és quest builder

| Mező | Érték |
|---|---|
| Funkcióazonosító | `progression.quests_daily` |
| Release-státusz | **Aktív, builder-előkészítést igényel** |
| Deployed JAR-hoz képesti státusz | **Jelentősen megváltozott** |
| Célközönség | Játékos, Admin, Builder, Eventes, Tesztelő |
| Közérthető leírás | Adatvezérelt küldetések, objective progress, napi feladatok, questnapló és admin/builder questkészítő. |
| Elérés | `/quest`, `/daily`; questlog és quest builder GUI. |
| Command | /quest (alias: /kuldetes, /quests); /daily |
| GUI | Küldetésnapló; Quest builder |
| Automatikus trigger | Objective események, NPC-interakció, napi ciklus és admin szerkesztés. |
| Permission | Kapcsolódó/ágankénti követelmény: `icesmp.admin.quest` |
| Kapcsolódó config | `quests.*`, napi küldetés-, NPC- és rewarddefiníciók. |
| Builder-/világ-előkészítés | Questhelyszínek, NPC-kötések, biztonságos célterületek és jutalomoverflow tesztelendő. |
| Persistence | Aktív quest, objective progress, napi állapot és builder által mentett definíció tartós. |
| Reloadviselkedés | Adatbetöltés/célzott reload támogatott részen; folyamatban lévő quest kompatibilitását tesztelni kell. |
| Ismert korlát | Az élő NPC-k és világhelyek hiányában csak capability-szintű következtetés adható. |
| Forrásbizonyíték és részfunkciók | 11 komponens (COMMAND: 1, GUI: 2, GUI_HOLDER: 2, LISTENER: 4, MANAGER: 2); részfunkció-azonosítók: `daily-quest`, `quest`, `quest-builder`, `quest-log`, `quest-progress`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Krónika, emlékek és aktív történeti mechanikák

| Mező | Érték |
|---|---|
| Funkcióazonosító | `progression.story_lore` |
| Release-státusz | **Aktív, builder-előkészítést igényel** |
| Deployed JAR-hoz képesti státusz | **Jelentősen megváltozott** |
| Célközönség | Játékos, Builder, Eventes, Tesztelő |
| Közérthető leírás | A forrásban ténylegesen bekötött krónika, emlék, lore-parancs, párbeszéd és tábortűzi történet; nem azonos a teljes tervezett lore-ral. |
| Elérés | `/kronika`, `/emlek`, `/lore`; dialógus- és történeti triggerek. |
| Command | /emlek (alias: /emlekek, /memory); /kronika (alias: /chronicle); /lore (alias: /kodex) |
| GUI | — |
| Automatikus trigger | Felfedezés, interakció, campfire vagy konfigurált történeti esemény. |
| Permission | — |
| Kapcsolódó config | Story-, chronicle-, memory-, dialogue-, quest- és message-definíciók. |
| Builder-/világ-előkészítés | Történeti helyszínek, NPC-k és aktiváló blokkok/területek előkészítendők. |
| Persistence | Felfedezett emlékek és krónikaállapot tartós. |
| Reloadviselkedés | Szövegek reloadolhatók; aktív folyamat és helyszíncsere külön tesztelendő. |
| Ismert korlát | Csak a regisztrált forrás- és resource-tartalom aktív; a LORE.md/TEASER.md önmagában nem implementáció. |
| Forrásbizonyíték és részfunkciók | 6 komponens (COMMAND: 3, LISTENER: 1, MANAGER: 1, SERVICE: 1); részfunkció-azonosítók: `campfire-story`, `chronicle`, `dialog`, `kronika`, `lore`, `memory`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Társak és befogás

| Mező | Érték |
|---|---|
| Funkcióazonosító | `progression.pets` |
| Release-státusz | **Aktív és játékosok számára elérhető** |
| Deployed JAR-hoz képesti státusz | **Jelentősen megváltozott** |
| Célközönség | Játékos, Admin, Tesztelő, Eventes |
| Közérthető leírás | Pet/társ kezelés, befogás, XP, harci viselkedés, parancsok és társ-GUI. |
| Elérés | `/pet`; Pet GUI; befogó item és játékosparancs. |
| Command | /pet (alias: /companion, /tars) |
| GUI | Társ GUI |
| Automatikus trigger | Befogás, pet parancs, combat, XP és GUI-művelet. |
| Permission | — |
| Kapcsolódó config | `pets.*`, capture-item és kapcsolódó message/itemdefiníciók. |
| Builder-/világ-előkészítés | Nincs kötelező helyszín; mob-kompatibilitás és biztonságos pet spawn tesztelendő. |
| Persistence | Tulajdonjog, petadat és XP tartós. |
| Reloadviselkedés | Balance reloadolható részei következő eseménynél érvényesek; aktív pet entityk újraszinkronizálása kellhet. |
| Ismert korlát | Entity lifecycle, chunk unload és protection plugin interakció runtime tesztet igényel. |
| Forrásbizonyíték és részfunkciók | 10 komponens (COMMAND: 1, GUI: 1, GUI_HOLDER: 1, ITEM_FACTORY: 1, LISTENER: 5, MANAGER: 1); részfunkció-azonosítók: `capture-item`, `pet`, `pet-capture`, `pet-combat`, `pet-command`, `pet-gui`, `pet-xp`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Party és közös jutalommegosztás

| Mező | Érték |
|---|---|
| Funkcióazonosító | `progression.party` |
| Release-státusz | **Aktív és játékosok számára elérhető** |
| Deployed JAR-hoz képesti státusz | **Jelentősen megváltozott** |
| Célközönség | Játékos, Admin, Tesztelő |
| Közérthető leírás | Party létrehozás/kezelés, tagság, eseményfigyelés és közös jutalomfeloldás. |
| Elérés | `/party`; főmenü party nézete. |
| Command | /party (alias: /p, /parti) |
| GUI | — |
| Automatikus trigger | Partyparancs, join/leave, jutalom és játékos-lifecycle esemény. |
| Permission | — |
| Kapcsolódó config | Party- és rewardbeállítások. |
| Builder-/világ-előkészítés | Nincs builderfeladat. |
| Persistence | Partyállapot a konfigurált store szerint tartós; ideiglenes invite sessionjellegű lehet. |
| Reloadviselkedés | Policy reloadolható; aktív meghívások és partyváltozás tesztelendő. |
| Ismert korlát | Quit/reconnect és régiók közötti rewardmegosztás Folia runtime tesztet igényel. |
| Forrásbizonyíték és részfunkciók | 4 komponens (COMMAND: 1, COMPONENT: 1, LISTENER: 1, MANAGER: 1); részfunkció-azonosítók: `party`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

## Gazdaság és jutalmak

### Valuta, bank és átváltás

| Mező | Érték |
|---|---|
| Funkcióazonosító | `economy.currency_bank` |
| Release-státusz | **Tesztelési vagy rollout-kapu alatt** |
| Deployed JAR-hoz képesti státusz | **Jelentősen megváltozott** |
| Célközönség | Játékos, Admin, Tesztelő, Fejlesztő/üzemeltető |
| Közérthető leírás | Több valuta, fizikai valutaitem, egyenleg, fizetés, admin set, bankbetét/kivét és árfolyam. |
| Elérés | `/currency`, `/bank`; főmenü bank/átváltó nézete. |
| Command | /bank (alias: /vault, /wallet); /currency (alias: /eco, /money) |
| GUI | — |
| Automatikus trigger | Parancs, craft, loot, jutalom és gazdasági tranzakció. |
| Permission | Kapcsolódó/ágankénti követelmény: `icesmp.admin.currency`; `icesmp.currency.admin` |
| Kapcsolódó config | `economy.*`, currency-, bank- és exchange-rate definíciók. |
| Builder-/világ-előkészítés | Nincs kötelező helyszín; shopok és jutalomforrások egyenlegét felül kell vizsgálni. |
| Persistence | Wallet/bankegyenleg és tranzakciós állapot tartós. |
| Reloadviselkedés | Árfolyam és limitek reloadolhatók; félbeszakított írásnál recovery szükséges. |
| Ismert korlát | Dupla költés, tárolási hiba és inventory-overflow csak fault-injection teszttel zárható ki. |
| Forrásbizonyíték és részfunkciók | 20 komponens (COMMAND: 2, COMPONENT: 13, ITEM_FACTORY: 1, LISTENER: 2, MANAGER: 1, SERVICE: 1); részfunkció-azonosítók: `bank`, `currency`, `currency-craft`, `currency-item`, `currency-item-refresh`, `exchange-rate`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Piac, boltok, adományláda és exchange board

| Mező | Érték |
|---|---|
| Funkcióazonosító | `economy.market_shops` |
| Release-státusz | **Aktív, builder-előkészítést igényel** |
| Deployed JAR-hoz képesti státusz | **Jelentősen megváltozott** |
| Célközönség | Játékos, Admin, Builder, Tesztelő, Eventes |
| Közérthető leírás | Játékospiac, NPC/frakció shop, vevőszolgáltatás, kézbesítés, adományláda és gazdasági hirdetőtábla. |
| Elérés | `/market`, `/adomany`, `/exchangeboard`; Market, Shop és Donation GUI. |
| Command | /adomany (alias: /adomanylada, /donate); /exchangeboard (alias: /arfolyamtabla, /ratesboard); /market (alias: /ah, /piac) |
| GUI | Adományláda; NPC/frakció bolt; Piactér |
| Automatikus trigger | Listing/vásárlás/kézbesítés, NPC-interakció, adomány és board művelet. |
| Permission | Kapcsolódó/ágankénti követelmény: `icesmp.admin.exchangeboard` |
| Kapcsolódó config | `economy.*`, market-, shop-, donation- és exchange-board definíciók. |
| Builder-/világ-előkészítés | Shop NPC-k, adományláda-hely és esetleges board-helyszínek kötése szükséges. |
| Persistence | Listingek, journal, kézbesítés és gazdasági állapot tartós. |
| Reloadviselkedés | Árak/listák részben reloadolhatók; nyitott tranzakciónál settlement kell. |
| Ismert korlát | Full inventory, disconnect és tárolási hiba esetén runtime recovery teszt szükséges. |
| Forrásbizonyíték és részfunkciók | 18 komponens (COMMAND: 3, GUI: 3, GUI_HOLDER: 3, LISTENER: 4, MANAGER: 4, SERVICE: 1); részfunkció-azonosítók: `buyer`, `donation-chest`, `exchange-board`, `market`, `market-delivery`, `market-gui`, `shop`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Natív crate-rendszer

| Mező | Érték |
|---|---|
| Funkcióazonosító | `economy.crates` |
| Release-státusz | **Tesztelési vagy rollout-kapu alatt** |
| Deployed JAR-hoz képesti státusz | **Új** |
| Célközönség | Játékos, Admin, Builder, Tesztelő, Fejlesztő/üzemeltető |
| Közérthető leírás | Fizikai crate-helyek, kulcsvásárlás/-felhasználás, browser/spin GUI, több jutalomtípus, audit, settlement és recovery. |
| Elérés | `/crate`; crate blokk, browser és spin GUI. |
| Command | /crate (alias: /crates, /ladak) |
| GUI | Crate böngésző és preview; Crate nyitási animáció |
| Automatikus trigger | Blokkinterakció, kulcshasználat, GUI-kattintás, settlement/recovery és adminparancs. |
| Permission | Kapcsolódó/ágankénti követelmény: `icesmp.admin.crate`; `icesmp.crate.ritka`; `icesmp.crate.use`; `icesmp.crate.use + opcionális crate-specifikus jog` |
| Kapcsolódó config | `crates.*`, crate location/world policy, reward- és auditbeállítások. |
| Builder-/világ-előkészítés | Minden crate-hez világ, blokk és hely szükséges; cserét/törlést kontrollált adminfolyamattal kell végezni. |
| Persistence | Crate-, opening-, ledger-, audit- és recovery-állapot tartós. |
| Reloadviselkedés | Definíciók generációváltással reloadolhatók; futó opening a saját snapshotján fejeződik be. |
| Ismert korlát | CrazyCrates csak main/off-hand, mass-open, reward failure, restart és MANUAL_REVIEW fault-injection után távolítható el. |
| Forrásbizonyíték és részfunkciók | 23 komponens (COMMAND: 1, COMPONENT: 13, GUI: 2, GUI_HOLDER: 2, ITEM_FACTORY: 1, LISTENER: 3, MANAGER: 1); részfunkció-azonosítók: `crate`, `crate-browser`, `crate-browser-gui`, `crate-key`, `crate-spin`, `crate-spin-gui`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Napi jutalmak, bounty és jutalomforrások

| Mező | Érték |
|---|---|
| Funkcióazonosító | `economy.rewards_bounty` |
| Release-státusz | **Aktív, configgal engedélyezhető** |
| Deployed JAR-hoz képesti státusz | **Jelentősen megváltozott** |
| Célközönség | Játékos, Admin, Eventes, Tesztelő |
| Közérthető leírás | Napi átvétel, bounty, pénzeszsák, mobpénz, reward-budget és party-kompatibilis jutalomfeloldás. |
| Elérés | `/daily`, `/bounty`; loot-, kill- és event-triggerek. |
| Command | /bounty (alias: /fejvadasz, /korozes); /daily (alias: /napi) |
| GUI | — |
| Automatikus trigger | Napi ciklus, kill, bounty teljesítés, itemhasználat és reward trigger. |
| Permission | — |
| Kapcsolódó config | Daily-, bounty-, loot-, money-pouch- és rewardbeállítások. |
| Builder-/világ-előkészítés | Bounty/event célpontok és jutalomforrások biztonságát ellenőrizni kell. |
| Persistence | Napi átvétel, bounty és egyes budgetállapotok tartósak. |
| Reloadviselkedés | Jutalomtáblák reloadolhatók; periodikus reset task restarthoz kötött lehet. |
| Ismert korlát | AFK-blokkolás, full inventory és economy-storage hiba esetén runtime teszt szükséges. |
| Forrásbizonyíték és részfunkciók | 6 komponens (COMMAND: 2, COMPONENT: 1, ITEM_FACTORY: 1, LISTENER: 2); részfunkció-azonosítók: `bounty`, `daily`, `mob-money-drop`, `money-pouch`, `money-pouch-item`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

## Frakciók, politika és terület

### Frakciótagság, viszonyok és frakciópasszívok

| Mező | Érték |
|---|---|
| Funkcióazonosító | `factions.membership_relations` |
| Release-státusz | **Aktív, builder-előkészítést igényel** |
| Deployed JAR-hoz képesti státusz | **Jelentősen megváltozott** |
| Célközönség | Játékos, Admin, Builder, Tesztelő, Eventes |
| Közérthető leírás | Belépés/kilépés/váltás, frakciókapcsolatok, étel/passzív/spawn hatások és frakcióspecifikus játékmenet. |
| Elérés | `/faction`; főmenü frakciónézete. |
| Command | /faction (alias: /f) |
| GUI | — |
| Automatikus trigger | Tagságváltás, join/quit, combat, fogyasztás, spawn és passzív esemény. |
| Permission | Kapcsolódó/ágankénti követelmény: `icesmp.admin.faction`; `icesmp.admin.war`; `icesmp.faction.admin`; `király`; `király vagy icesmp.admin.faction`; `király vagy tanácstag` |
| Kapcsolódó config | `factions.*`, relation-, passive-, food- és spawn-definíciók. |
| Builder-/világ-előkészítés | Frakcióspawnokat, védett területeket és váltási feltételeket elő kell készíteni. |
| Persistence | Tagság, relation és frakcióállapot tartós. |
| Reloadviselkedés | Balance reloadolható; tagság- és world-kötés változása migrációtesztet igényel. |
| Ismert korlát | Élő LuckPerms/group és világconfig nélkül a tényleges rollout nem bizonyítható. |
| Forrásbizonyíték és részfunkciók | 13 komponens (COMMAND: 1, COMPONENT: 7, LISTENER: 3, MANAGER: 2); részfunkció-azonosítók: `faction`, `faction-food`, `faction-passive`, `faction-relation`, `faction-spawn`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Céhek, királyság, tanács és frakciókassza

| Mező | Érték |
|---|---|
| Funkcióazonosító | `factions.guilds_leadership` |
| Release-státusz | **Aktív, builder-előkészítést igényel** |
| Deployed JAR-hoz képesti státusz | **Jelentősen megváltozott** |
| Célközönség | Játékos, Admin, Eventes, Tesztelő |
| Közérthető leírás | Céhkezelés, vezetői/királyi műveletek, tanács, treasury és közösségi politikai állapot. |
| Elérés | `/ceh`, `/tanacs`, `/faction king\|treasury`; frakciómenü. |
| Command | /ceh (alias: /gild, /guild); /tanacs (alias: /council); /faction king\|treasury |
| GUI | — |
| Automatikus trigger | Parancs, választás/vezetői művelet, treasury tranzakció és event. |
| Permission | Kapcsolódó/ágankénti követelmény: `icesmp.admin.faction` |
| Kapcsolódó config | `factions.*`, guild-, king-, council- és treasury-definíciók. |
| Builder-/világ-előkészítés | Tanács-/trónhelyszín és szerepjátékos folyamat előkészítése ajánlott. |
| Persistence | Céh, vezetés, tanács és treasury állapota tartós. |
| Reloadviselkedés | Policy reloadolható; vezetői állapotváltozás staging- és permissiontesztet igényel. |
| Ismert korlát | A politikai játékmenet élő szerverfolyamata és eventes szabályai nem vezethetők le pusztán a kódból. |
| Forrásbizonyíték és részfunkciók | 11 komponens (COMMAND: 2, COMPONENT: 2, LISTENER: 1, MANAGER: 6); részfunkció-azonosítók: `capital-law`, `city-guard`, `council`, `crown-curse`, `faction-treasury`, `guild`, `king`, `tanacs`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Claim, territory és védelem

| Mező | Érték |
|---|---|
| Funkcióazonosító | `factions.land_claims` |
| Release-státusz | **Aktív, builder-előkészítést igényel** |
| Deployed JAR-hoz képesti státusz | **Jelentősen megváltozott** |
| Célközönség | Játékos, Admin, Builder, Tesztelő |
| Közérthető leírás | Területfoglalás, trust, world/régióvédelem, territory típusok és admin törlés/kiválasztó eszköz. |
| Elérés | `/claim`, `/territory`; ClaimTrust GUI és selection wand. |
| Command | /claim (alias: /birtok); /territory (alias: /terulet) |
| GUI | Megbízottak kezelése |
| Automatikus trigger | Blokk-interakció, break/place, PvP/használat, claimparancs és GUI. |
| Permission | Kapcsolódó/ágankénti követelmény: `icesmp.admin.territory`; `icesmp.admin.territory.bypass`; `icesmp.territory.builder` |
| Kapcsolódó config | `factions.*`, claim-, territory-, world- és protection-beállítások. |
| Builder-/világ-előkészítés | Világpolicy, régióhatárok, spawnok és WorldEdit utáni audit szükséges. |
| Persistence | Claim, trust és territory állapot tartós. |
| Reloadviselkedés | Policy reloadolható; világátnevezés vagy régiótípus-váltás migrációt igényel. |
| Ismert korlát | A külső protection plugin és az élő worldlisták nélkül a tényleges konfliktusmátrix nem bizonyítható. |
| Forrásbizonyíték és részfunkciók | 14 komponens (COMMAND: 2, COMPONENT: 2, GUI: 1, GUI_HOLDER: 1, LISTENER: 5, MANAGER: 2, SERVICE: 1); részfunkció-azonosítók: `claim`, `claim-protection`, `claim-trust`, `claim-trust-gui`, `selection-wand`, `territory`, `territory-protection`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Háború, raid, kémkedés és ostrom

| Mező | Érték |
|---|---|
| Funkcióazonosító | `factions.conflict_espionage` |
| Release-státusz | **Aktív, builder-előkészítést igényel** |
| Deployed JAR-hoz képesti státusz | **Jelentősen megváltozott** |
| Célközönség | Játékos, Admin, Builder, Eventes, Tesztelő |
| Közérthető leírás | Háborús ablak, raid, kémálca/felfedés, lopás, caravan konfliktus és ostromfegyverek. |
| Elérés | `/faction raid\|war\|caravan`, `/kem`, `/parbaj`; item- és eventtriggerek. |
| Command | /kem (alias: /spy); /faction raid\|war\|caravan; /parbaj |
| GUI | — |
| Automatikus trigger | War window, raid, PvP, reveal/theft, siege item és caravan esemény. |
| Permission | Kapcsolódó/ágankénti követelmény: `icesmp.admin.territory.bypass`; `icesmp.admin.war` |
| Kapcsolódó config | `factions.*`, raid-, war-, spy-, siege- és caravan-definíciók. |
| Builder-/világ-előkészítés | Raid/ostrom területeket, útvonalakat és védelmi kivételeket elő kell készíteni. |
| Persistence | Raid-, war-, kém- és guild/faction állapot tartós lehet; aktív combat runtime. |
| Reloadviselkedés | Policy reloadolható; folyamatban lévő raid/war saját snapshotot igényel. |
| Ismert korlát | Exploit-, dupe-, offline-raid és protection-interakció teljes playtestet igényel. |
| Forrásbizonyíték és részfunkciók | 14 komponens (COMMAND: 1, COMPONENT: 4, ITEM_FACTORY: 1, LISTENER: 4, MANAGER: 4); részfunkció-azonosítók: `raid`, `siege-weapon`, `spy`, `spy-reveal`, `stranger`, `stranger-npc`, `theft`, `war-window`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

## Világ, események és interakció

### Világesemények és bossok

| Mező | Érték |
|---|---|
| Funkcióazonosító | `world.events` |
| Release-státusz | **Aktív, builder-előkészítést igényel** |
| Deployed JAR-hoz képesti státusz | **Jelentősen megváltozott** |
| Célközönség | Játékos, Admin, Builder, Eventes, Tesztelő |
| Közérthető leírás | Blood Moon, world boss, Wild Hunt, invasion, meteor, caravan, escort, abundance, treasure és szerverchallenge. |
| Elérés | `/events`; admin eventindítók és automatikus eseménytriggerek. |
| Command | /events (alias: /esemeny, /event) |
| GUI | — |
| Automatikus trigger | Ütemezett/valószínűségi trigger, adminindítás, spawnpont és lifecycle esemény. |
| Permission | Kapcsolódó/ágankénti követelmény: `icesmp.admin.events` |
| Kapcsolódó config | `world.*`, event-, boss-, mob-, loot- és season-definíciók. |
| Builder-/világ-előkészítés | Spawnpontok, útvonalak, bossarénák, biztonságos visszaállítás és régiók szükségesek. |
| Persistence | Event-, spawnpont-, raid- és finaleállapot részben tartós. |
| Reloadviselkedés | Definíciók reloadolhatók részenként; aktív esemény saját state-jét nem szabad félúton lecserélni. |
| Ismert korlát | Egyidejű esemény, chunk unload, restart és cleanup production runtime tesztet igényel. |
| Forrásbizonyíték és részfunkciók | 23 komponens (COMMAND: 1, COMPONENT: 2, LISTENER: 7, MANAGER: 13); részfunkció-azonosítók: `abundance`, `blood-moon`, `caravan`, `cultist-event`, `escort`, `event-spawn-point`, `events`, `invasion`, `meteor-event`, `player-caravan`, `server-challenge`, `treasure`, `treasure-event`, `wild-hunt`, `world-boss`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Évszakok, közösségi célok és ambient események

| Mező | Érték |
|---|---|
| Funkcióazonosító | `world.seasons_ambient` |
| Release-státusz | **Aktív, builder-előkészítést igényel** |
| Deployed JAR-hoz képesti státusz | **Jelentősen megváltozott** |
| Célközönség | Játékos, Admin, Builder, Eventes, Tesztelő |
| Közérthető leírás | Szezonállapot, jutalmak, történetmesélés, finálé, monumentum, holiday, ambient és gazdasági módosítók. |
| Elérés | `/events`; automatikus szezon- és ambient folyamat. |
| Command | /events |
| GUI | — |
| Automatikus trigger | Idő/ciklus, közösségi progress, holiday, world állapot és admin trigger. |
| Permission | — |
| Kapcsolódó config | `world.*`, season-, holiday-, community-goal- és ambient-definíciók. |
| Builder-/világ-előkészítés | Szezonmonumentum, fináléhelyszín és kapcsolódó NPC/eventpontok előkészítendők. |
| Persistence | Szezon, jutalom, közösségi cél, monumentum és finálé állapota tartós. |
| Reloadviselkedés | Szöveg/balance reloadolható; szezonváltás és scheduler újraindítása kontrollált folyamat. |
| Ismert korlát | Időzóna, szerveróra, restart és párhuzamos eventhatás runtime ellenőrzést igényel. |
| Forrásbizonyíték és részfunkciók | 12 komponens (COMPONENT: 3, MANAGER: 7, SERVICE: 2); részfunkció-azonosítók: `ambient-event`, `community-goal`, `dark-undead-ambience`, `economy-event`, `holiday`, `season`, `season-finale`, `season-monument`, `seasonal-modifier`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Mobok, skálázás, loot és bestiárium

| Mező | Érték |
|---|---|
| Funkcióazonosító | `world.mobs_loot` |
| Release-státusz | **Aktív, builder-előkészítést igényel** |
| Deployed JAR-hoz képesti státusz | **Jelentősen megváltozott** |
| Célközönség | Játékos, Admin, Builder, Eventes, Tesztelő |
| Közérthető leírás | Mobskálázás, loot table, dungeon/mob jutalom, minionvédelem, bestiárium és undead segédszabályok. |
| Elérés | `/bestiarium`; automatikus spawn/kill/loot események. |
| Command | /bestiarium (alias: /bestiary, /lajstrom) |
| GUI | Bestiárium |
| Automatikus trigger | Mob spawn, sebzés, ölés, loot, bestiárium-felfedezés és minion lifecycle. |
| Permission | — |
| Kapcsolódó config | `world.*`, `loot.*`, mob-, bestiary-, scaling- és miniondefiníciók. |
| Builder-/világ-előkészítés | Mobspawnokat, arénákat, farmvédelmet és lootforrásokat ellenőrizni kell. |
| Persistence | Bestiárium progress és egyes loot/event state-ek tartósak; mob entity runtime. |
| Reloadviselkedés | Loot/balance reloadolható; már spawnolt mobok nem feltétlenül változnak visszamenőleg. |
| Ismert korlát | Vanilla/custom mob és más lootplugin együttműködése runtime tesztet igényel. |
| Forrásbizonyíték és részfunkciók | 12 komponens (COMMAND: 1, COMPONENT: 2, GUI_HOLDER: 1, LISTENER: 5, MANAGER: 3); részfunkció-azonosítók: `bestiary`, `fishing-windfall`, `minion`, `minion-protection`, `mob-loot`, `mob-scaling`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Dungeon kapuk és dungeon loot

| Mező | Érték |
|---|---|
| Funkcióazonosító | `world.dungeons` |
| Release-státusz | **Aktív, builder-előkészítést igényel** |
| Deployed JAR-hoz képesti státusz | **Jelentősen megváltozott** |
| Célközönség | Játékos, Admin, Builder, Eventes, Tesztelő |
| Közérthető leírás | Dungeonbelépési feltételek, kapuvédelem, lootkiosztás és restartbiztos lootállapot. |
| Elérés | Világbeli dungeon gate és lootinterakció. |
| Command | — |
| GUI | — |
| Automatikus trigger | Belépés, blokk/container interakció, loot és lifecycle. |
| Permission | — |
| Kapcsolódó config | `world.*`, dungeon- és lootdefiníciók. |
| Builder-/világ-előkészítés | Dungeonvilág, kapuk, lootkonténerek, protection és recovery útvonalak előkészítendők. |
| Persistence | Dungeon-loot state tartós; gate runtime policy. |
| Reloadviselkedés | Definíció reloadolható lehet; aktív dungeon és lootjournal miatt restartteszt kell. |
| Ismert korlát | A tényleges világartifact hiányában a helyszínkészség nem bizonyítható. |
| Forrásbizonyíték és részfunkciók | 3 komponens (LISTENER: 2, SERVICE: 1); részfunkció-azonosítók: `dungeon-gate`, `dungeon-loot`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Teleport, komp, NPC-kötés és interakció

| Mező | Érték |
|---|---|
| Funkcióazonosító | `world.travel_npc` |
| Release-státusz | **Aktív, builder-előkészítést igényel** |
| Deployed JAR-hoz képesti státusz | **Jelentősen megváltozott** |
| Célközönség | Játékos, Admin, Builder, Eventes, Tesztelő |
| Közérthető leírás | Komp/utazás, NPC-binding, Stranger NPC és FancyNpcs-quest/shop kapcsolatok. |
| Elérés | `/komp`, `/npcbind`; NPC- és világbeli interakció. |
| Command | /komp (alias: /ferry); /npcbind (alias: /npckotes) |
| GUI | — |
| Automatikus trigger | NPC click/dialog, teleport/komp és binding adminművelet. |
| Permission | Kapcsolódó/ágankénti követelmény: `icesmp.admin.npc` |
| Kapcsolódó config | `world.*`, NPC-, ferry-, teleport-, shop- és questdefiníciók. |
| Builder-/világ-előkészítés | Biztonságos célpontok, NPC-k, kompállomások és világnevek előkészítendők. |
| Persistence | NPC-kötések és egyes utazási állapotok tartósak. |
| Reloadviselkedés | Célpontok reloadolhatók; világátnevezés vagy NPC-ID csere migrációt igényel. |
| Ismert korlát | A külső FancyNpcs és élő világ nélkül csak capability bizonyítható. |
| Forrásbizonyíték és részfunkciók | 4 komponens (COMMAND: 2, MANAGER: 2); részfunkció-azonosítók: `ferry`, `komp`, `npc-bind`, `npc-binding`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Parkour, archeológia és felfedezés

| Mező | Érték |
|---|---|
| Funkcióazonosító | `world.parkour_discovery` |
| Release-státusz | **Aktív, builder-előkészítést igényel** |
| Deployed JAR-hoz képesti státusz | **Jelentősen megváltozott** |
| Célközönség | Játékos, Admin, Builder, Eventes, Tesztelő |
| Közérthető leírás | Parkourpályák, checkpoint/progress, archeológiai megosztás és rejtett helyek felfedezése. |
| Elérés | `/parkour`; világinterakció és felfedezési trigger. |
| Command | /parkour (alias: /palya, /trial) |
| GUI | — |
| Automatikus trigger | Pályakezdés/checkpoint/cél, archeológia és hidden-spot belépés. |
| Permission | Kapcsolódó/ágankénti követelmény: `icesmp.admin.parkour` |
| Kapcsolódó config | `world.*`, parkour-, archeology- és hidden-spot definíciók. |
| Builder-/világ-előkészítés | Pályákat, checkpointokat, biztonságos visszarakást és rejtett pontokat létre kell hozni. |
| Persistence | Parkour és felfedezési progress tartós lehet; futó pálya sessionállapot. |
| Reloadviselkedés | Definíció reloadolható; aktív futam és WorldEdit utáni helyellenőrzés kell. |
| Ismert korlát | A pályák fizikai megléte az élő világ nélkül nem bizonyítható. |
| Forrásbizonyíték és részfunkciók | 6 komponens (COMMAND: 1, LISTENER: 2, MANAGER: 3); részfunkció-azonosítók: `archeology`, `archeology-share`, `hidden-spot`, `parkour`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Natív sit-only rendszer

| Mező | Érték |
|---|---|
| Funkcióazonosító | `world.sit_only` |
| Release-státusz | **Tesztelési vagy rollout-kapu alatt** |
| Deployed JAR-hoz képesti státusz | **Új** |
| Célközönség | Játékos, Admin, Builder, Tesztelő |
| Közérthető leírás | Ülés támogatott lépcsőn, alsó/felső slabon, carpet/moss carpet/pale moss carpet és snow geometrián; foglalás- és lifecycle-cleanuppal. |
| Elérés | `/sit [fel]`; jobb kattintás, ha a policy engedi. |
| Command | /sit |
| GUI | — |
| Automatikus trigger | Interakció, sneak, damage, break, teleport, world change, quit/kick/dismount, reload/disable. |
| Permission | Kapcsolódó/ágankénti követelmény: `icesmp.sit` |
| Kapcsolódó config | `sit.*` világ- és material policy. |
| Builder-/világ-előkészítés | Ülőblokkok geometriáját, supportot, folyadékot, clearance-t és világlistát ellenőrizni kell. |
| Persistence | Nincs tartós ülés; seat entity és foglalás sessionállapot. |
| Reloadviselkedés | Policy reloadolható, aktív ülések cleanupja kötelező. |
| Ismert korlát | Lay, crawl, stacking, player/NPC sitting nem támogatott; GSit csak sit átvételi teszt után távolítható el. |
| Forrásbizonyíték és részfunkciók | 6 komponens (COMMAND: 1, COMPONENT: 3, LISTENER: 1, MANAGER: 1); részfunkció-azonosítók: `sit`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Világszabályok, crop protection és biztonsági policy

| Mező | Érték |
|---|---|
| Funkcióazonosító | `world.rules_protection` |
| Release-státusz | **Tesztelési vagy rollout-kapu alatt** |
| Deployed JAR-hoz képesti státusz | **Új** |
| Célközönség | Játékos, Admin, Builder, Tesztelő |
| Közérthető leírás | World game rule enforcement, portal guard, player/mob farmland-trample védelem, blokk-visszaállítás és általános protection bridge. |
| Elérés | Automatikus listenerek és admin config. |
| Command | — |
| GUI | — |
| Automatikus trigger | Világbetöltés, blokk-, portal-, trample- és protection esemény. |
| Permission | Kapcsolódó/ágankénti követelmény: `icesmp.admin.territory`; `icesmp.admin.territory.bypass` |
| Kapcsolódó config | `world.*`, game-rule-, crop-protection-, portal- és block-regen beállítások. |
| Builder-/világ-előkészítés | Világ whitelist/blacklist, protected régiók és block-regeneration policy ellenőrzendő. |
| Persistence | Blokk-regeneráció journal tartós; a legtöbb policy runtime. |
| Reloadviselkedés | Policy reloadolható; már ütemezett regen és világszabály-frissítés restartot igényelhet. |
| Ismert korlát | FarmProtect/ICEsmpadditions csak player/mob trample és Warden XP playtest után távolítható el. |
| Forrásbizonyíték és részfunkciók | 6 komponens (LISTENER: 5, SERVICE: 1); részfunkció-azonosítók: `block-regen`, `dev-item-protection`, `portal-guard`, `unique-material-protection`, `world-game-rule`, `world-tweaks`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Combat tag, párbaj és harci szabályok

| Mező | Érték |
|---|---|
| Funkcióazonosító | `world.combat_rules` |
| Release-státusz | **Aktív, configgal engedélyezhető** |
| Deployed JAR-hoz képesti státusz | **Jelentősen megváltozott** |
| Célközönség | Játékos, Admin, Tesztelő |
| Közérthető leírás | Combat tag, becsületpárbaj, resource combat, regen- és sebzéssegédek, valamint állapotcleanup. |
| Elérés | `/parbaj`; automatikus combat listenerek. |
| Command | /parbaj (alias: /duel) |
| GUI | — |
| Automatikus trigger | Sebzés, kilépés, death, duel accept/end és resource conflict. |
| Permission | — |
| Kapcsolódó config | Combat-, duel-, health- és world policy. |
| Builder-/világ-előkészítés | Párbajterület és biztonságos visszarakás ellenőrzendő, ha a config helyhez köti. |
| Persistence | Párbaj/statisztika részben tartós; combat tag sessionállapot. |
| Reloadviselkedés | Balance reloadolható; aktív combat sessiont nem feltétlenül írja át. |
| Ismert korlát | Folia régióhatár, teleport, logout és protection plugin konfliktus runtime tesztet igényel. |
| Forrásbizonyíték és részfunkciók | 7 komponens (COMMAND: 1, COMPONENT: 1, LISTENER: 3, MANAGER: 2); részfunkció-azonosítók: `combat-tag`, `health-regen`, `honor-duel`, `resource-combat`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Korrupció, bűn és cursed hatások

| Mező | Érték |
|---|---|
| Funkcióazonosító | `world.corruption_sin` |
| Release-státusz | **Aktív, configgal engedélyezhető** |
| Deployed JAR-hoz képesti státusz | **Jelentősen megváltozott** |
| Célközönség | Játékos, Admin, Eventes, Tesztelő |
| Közérthető leírás | Korrupciós aura/állapot, sinner parancs, crown curse és cursed gear viselkedés. |
| Elérés | `/sinner`; automatikus aura, gear és story/event triggerek. |
| Command | /sinner |
| GUI | — |
| Automatikus trigger | Combat, itemviselés, terület/event és periodikus állapot. |
| Permission | Kapcsolódó/ágankénti követelmény: `icesmp.admin`; `icesmp.admin.sinner` |
| Kapcsolódó config | `world.*`, corruption-, sin-, crown- és cursed-gear definíciók. |
| Builder-/világ-előkészítés | Kapcsolódó régiók, eventek és tárgyak előkészítendők. |
| Persistence | Korrupció és bűn állapota tartós; aura runtime. |
| Reloadviselkedés | Balance reloadolható; aktív aura/state migrációja tesztelendő. |
| Ismert korlát | Történeti jelentése csak az implementált triggerekig tekinthető aktívnak. |
| Forrásbizonyíték és részfunkciók | 8 komponens (COMMAND: 1, LISTENER: 4, MANAGER: 2, SERVICE: 1); részfunkció-azonosítók: `corruption`, `corruption-aura`, `cursed-gear`, `sin`, `sinner`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

### Rituálék és totemek

| Mező | Érték |
|---|---|
| Funkcióazonosító | `world.rituals` |
| Release-státusz | **Aktív, builder-előkészítést igényel** |
| Deployed JAR-hoz képesti státusz | **Jelentősen megváltozott** |
| Célközönség | Játékos, Admin, Builder, Eventes, Tesztelő |
| Közérthető leírás | Adatvezérelt rituálék, triggerfeltételek, totemek, itemfogyasztás és eseményhatások. |
| Elérés | Világbeli rituáléinterakció; kapcsolódó admin/event indítás. |
| Command | — |
| GUI | — |
| Automatikus trigger | Blokkminta, itemhasználat, idő/event és ritual listener. |
| Permission | — |
| Kapcsolódó config | `world.*`, ritual-, totem-, item- és eventdefiníciók. |
| Builder-/világ-előkészítés | Rituáléhelyeket, blokkmintákat, clearance-t és védelmi kivételeket elő kell készíteni. |
| Persistence | Egyes rituálé/totem állapotok tartósak; effekt runtime. |
| Reloadviselkedés | Definíció reloadolható részenként; folyamatban lévő rituálé saját snapshotot igényel. |
| Ismert korlát | Blokkminta, inventory failure és párhuzamos aktiválás runtime tesztet igényel. |
| Forrásbizonyíték és részfunkciók | 3 komponens (LISTENER: 1, MANAGER: 2); részfunkció-azonosítók: `ritual`, `totem`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

## Fejlesztői és diagnosztikai funkciók

### Fejlesztői tárgyak és diagnosztika

| Mező | Érték |
|---|---|
| Funkcióazonosító | `developer.items_debug` |
| Release-státusz | **Aktív, adminisztratív** |
| Deployed JAR-hoz képesti státusz | **Jelentősen megváltozott** |
| Célközönség | Fejlesztő/üzemeltető, Admin, Tesztelő |
| Közérthető leírás | Jogosultságvédett dev-itemek, itemadás, debug/inspect és a fejlesztői tárgyak visszaélés elleni védelme. |
| Elérés | `/iceitem`; admin inspect/debug útvonalak. |
| Command | /iceitem (alias: /icegive, /iitem) |
| GUI | — |
| Automatikus trigger | Explicit parancs, dev-item használat és védelmi listener. |
| Permission | Kapcsolódó/ágankénti követelmény: `icesmp.admin.item` |
| Kapcsolódó config | `dev-items.*`, developer/debug és permissions. |
| Builder-/világ-előkészítés | Productionben csak kijelölt staging/admin környezetben használható. |
| Persistence | Dev-item state és audit tartós lehet; debug sessionállapot. |
| Reloadviselkedés | Definíció reloadolható része csak új itemnél biztos; meglévő tárgyat ellenőrizni kell. |
| Ismert korlát | Kritikus jogosultság; normál admin/moderátor szerepkörnek nem javasolt. |
| Forrásbizonyíték és részfunkciók | 4 komponens (COMMAND: 1, COMPONENT: 1, ITEM_FACTORY: 1, MANAGER: 1); részfunkció-azonosítók: `dev-item`, `item-give`. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

## Tervezett, de nem aktív tartalom

### Csak lore-ban vagy teaserben szereplő tervek

| Mező | Érték |
|---|---|
| Funkcióazonosító | `planning.lore_only` |
| Release-státusz | **Tervezett, de nem implementált** |
| Deployed JAR-hoz képesti státusz | **Nem állapítható meg az élő config nélkül** |
| Célközönség | Fejlesztő/üzemeltető, Eventes |
| Közérthető leírás | Tervezett, de ebben a release-ben nem aktív. A LORE.md és TEASER.md kizárólag narratív/tervezési forrás; regisztráció nélküli elemeik nem kerülnek az aktív funkciók közé. |
| Elérés | Nincs aktív command, GUI, listener vagy automatikus útvonal. |
| Command | — |
| GUI | — |
| Automatikus trigger | Nincs. |
| Permission | — |
| Kapcsolódó config | Nincs aktív konfiguráció. |
| Builder-/világ-előkészítés | Ne építs rá production folyamatot addig, amíg külön implementáció és bizonyíték nem készül. |
| Persistence | Nincs. |
| Reloadviselkedés | Nem értelmezhető. |
| Ismert korlát | A dokumentumokból nem szabad implementációt feltételezni. |
| Forrásbizonyíték és részfunkciók | 0 komponens (nincs production komponens); részfunkció-azonosítók: —. Rendszerszintű forrás-, bináris-, config- és elérési bizonyíték: CI inventory artifact. |

## Kifejezetten kizárt vagy nem támogatott scope

| Scope | Release-állapot | Mit jelent? |
|---|---|---|
| Jutalmazó AFK-zóna, zónatimer, zónakifizetés és AFK-bossbar | **Elvetett / out of scope** | Nincs aktív final config- vagy runtime út; csak globális AFK marad. |
| Lay és crawl | **Elvetett / out of scope** | A natív GSit-kiváltás kizárólag ülés. |
| Stacking, player sitting és NPC sitting | **Elvetett / out of scope** | Nincs aktív parancs, listener vagy GUI út. |
| Teljes TAB-klón | **Elvetett / out of scope** | Csak az IceSMP számára szükséges natív HUD/tablista subset cél. |
| LORE.md/TEASER.md regisztráció nélküli ötletei | **Tervezett, de nem implementált** | **Tervezett, de ebben a release-ben nem aktív.** |

## Élesítés előtti általános kapuk

1. Moderáció: punishment expiry/restart, corrupt state, lemezhiba, PM reconnect, SocialSpy, vanish, inventory escrow és offline teleport.
2. MOTD: párhuzamos ping, TIME/RANDOM, event priority, vanished count, ikonmódok, hibás PNG/symlink, gyors reload és MiniMOTD nélküli indulás.
3. Sit-only: minden támogatott blokkforma, foglalás, damage/sneak/break/teleport/world change/quit/kick/dismount, reload/disable és seat sweep.
4. Crate: main/off-hand, dupla kattintás, mass-open, full inventory, minden rewardtípus, currency/command failure, generation reload, settlement/recovery, restart és `MANUAL_REVIEW`.
5. Világ és tartalom: NPC-k, questhelyek, eventspawnok, bossarénák, professionforrások, claim/régiók, WorldEdit utáni helyaudit és resource-pack itemek.
6. Külső pluginok: egyik replacement JAR-t sem szabad kizárólag CI alapján eltávolítani; előbb a rendszer saját acceptance checklistje legyen bizonyítottan zöld.

## Bizonyíték- és határjegyzet

- A component-map **545** egyedi production komponenst rendel pontosan egy funkcióhoz; feloldatlan elem: **0**.
- A dokumentált forrásállapot **545 Java-fájlban 90 manager** osztályt
  tartalmaz; ez technikai leltár, nem játékosoknak ígért feature-darabszám.
- A forrásinterfész-leltár **68** root commandot, **286** routingágat, **44** kanonikus permissiont és **22** GUI-t bizonyít.
- A konfigurációs leltár **13 550** egyedi pathot  az adatleltár **92** kategóriát és **2 435** név szerinti elemet fed le.
- A deployed JAR nem tartalmaz Git SHA-t; forráscommit-egyezés helyett a bináris önálló baseline. Az élő külső config és adatállomány hiánya minden opcionális rendszer tényleges bekapcsoltságánál megmaradó korlát.

<!-- BEGIN GENERATED FEATURE MANIFEST MARKERS -->
## Dokumentációs markerregiszter — funkciók

Az alábbi stabil azonosítók a dokumentációs coverage tooling számára készültek. A részletes tartalom az azonos nevű katalógustételben olvasható.

| Stabil feature ID | Funkció | Release-státusz |
|---|---|---|
| <!-- icesmp-doc-id: feature.core.lifecycle --> `feature.core.lifecycle` | Plugin-életciklus és rendszerindítás | Tesztelési vagy rollout-kapu alatt |
| <!-- icesmp-doc-id: feature.core.configuration --> `feature.core.configuration` | Konfiguráció, üzenetek és validáció | Aktív, adminisztratív |
| <!-- icesmp-doc-id: feature.core.persistence --> `feature.core.persistence` | Persistence, naplók és recovery | Tesztelési vagy rollout-kapu alatt |
| <!-- icesmp-doc-id: feature.core.integrations --> `feature.core.integrations` | Külső integrációs hidak | Aktív, külső integrációval bővül |
| <!-- icesmp-doc-id: feature.core.shared_services --> `feature.core.shared_services` | Közös végrehajtási és segédszolgáltatások | Aktív, adminisztratív |
| <!-- icesmp-doc-id: feature.admin.control_panel --> `feature.admin.control_panel` | Admin- és konfigurációs kezelőfelület | Aktív, adminisztratív |
| <!-- icesmp-doc-id: feature.player.global_afk --> `feature.player.global_afk` | Globális AFK-rendszer | Tesztelési vagy rollout-kapu alatt |
| <!-- icesmp-doc-id: feature.player.menus_profile --> `feature.player.menus_profile` | Főmenü, profil és karakterlap | Aktív és játékosok számára elérhető |
| <!-- icesmp-doc-id: feature.player.hud_tablist --> `feature.player.hud_tablist` | HUD és natív tablista | Tesztelési vagy rollout-kapu alatt |
| <!-- icesmp-doc-id: feature.player.motd --> `feature.player.motd` | Natív MOTD és szerverlista-megjelenítés | Tesztelési vagy rollout-kapu alatt |
| <!-- icesmp-doc-id: feature.player.chat_feedback --> `feature.player.chat_feedback` | Chat, vizuális visszajelzés és harci kijelzés | Aktív, configgal engedélyezhető |
| <!-- icesmp-doc-id: feature.player.onboarding --> `feature.player.onboarding` | Beléptetés és kezdőfolyamat | Aktív, builder-előkészítést igényel |
| <!-- icesmp-doc-id: feature.admin.moderation --> `feature.admin.moderation` | Natív büntetési és moderációs rendszer | Tesztelési vagy rollout-kapu alatt |
| <!-- icesmp-doc-id: feature.admin.reports_messaging --> `feature.admin.reports_messaging` | Report, privát üzenet, reply és SocialSpy | Tesztelési vagy rollout-kapu alatt |
| <!-- icesmp-doc-id: feature.admin.vanish --> `feature.admin.vanish` | Vanish és láthatóság | Tesztelési vagy rollout-kapu alatt |
| <!-- icesmp-doc-id: feature.admin.inventory_teleport --> `feature.admin.inventory_teleport` | Inventory-admin, escrow és offline teleport | Tesztelési vagy rollout-kapu alatt |
| <!-- icesmp-doc-id: feature.progression.classes_specializations --> `feature.progression.classes_specializations` | Kasztok, specializációk és kasztképességek | Aktív és játékosok számára elérhető |
| <!-- icesmp-doc-id: feature.progression.talents_skills --> `feature.progression.talents_skills` | Talent- és képességfák | Aktív és játékosok számára elérhető |
| <!-- icesmp-doc-id: feature.progression.spells --> `feature.progression.spells` | Varázslatok, mastery és varázskönyv | Aktív és játékosok számára elérhető |
| <!-- icesmp-doc-id: feature.progression.professions --> `feature.progression.professions` | Professionök, specializációk és heti célok | Aktív, builder-előkészítést igényel |
| <!-- icesmp-doc-id: feature.progression.crafting_blueprints --> `feature.progression.crafting_blueprints` | Crafting, receptek, blueprintök és katalizátorok | Aktív, builder-előkészítést igényel |
| <!-- icesmp-doc-id: feature.progression.items_rarity --> `feature.progression.items_rarity` | Unique itemek, ritkaság, rúnák és signature tárgyak | Aktív, builder-előkészítést igényel |
| <!-- icesmp-doc-id: feature.progression.relics_souls --> `feature.progression.relics_souls` | Relikviák, lelkek és soulforge | Aktív, builder-előkészítést igényel |
| <!-- icesmp-doc-id: feature.progression.achievements_stats --> `feature.progression.achievements_stats` | Achievementek, advancementek, statisztikák és leaderboard | Aktív és játékosok számára elérhető |
| <!-- icesmp-doc-id: feature.progression.quests_daily --> `feature.progression.quests_daily` | Questek, napi küldetések és quest builder | Aktív, builder-előkészítést igényel |
| <!-- icesmp-doc-id: feature.progression.story_lore --> `feature.progression.story_lore` | Krónika, emlékek és aktív történeti mechanikák | Aktív, builder-előkészítést igényel |
| <!-- icesmp-doc-id: feature.progression.pets --> `feature.progression.pets` | Társak és befogás | Aktív és játékosok számára elérhető |
| <!-- icesmp-doc-id: feature.progression.party --> `feature.progression.party` | Party és közös jutalommegosztás | Aktív és játékosok számára elérhető |
| <!-- icesmp-doc-id: feature.economy.currency_bank --> `feature.economy.currency_bank` | Valuta, bank és átváltás | Tesztelési vagy rollout-kapu alatt |
| <!-- icesmp-doc-id: feature.economy.market_shops --> `feature.economy.market_shops` | Piac, boltok, adományláda és exchange board | Aktív, builder-előkészítést igényel |
| <!-- icesmp-doc-id: feature.economy.crates --> `feature.economy.crates` | Natív crate-rendszer | Tesztelési vagy rollout-kapu alatt |
| <!-- icesmp-doc-id: feature.economy.rewards_bounty --> `feature.economy.rewards_bounty` | Napi jutalmak, bounty és jutalomforrások | Aktív, configgal engedélyezhető |
| <!-- icesmp-doc-id: feature.factions.membership_relations --> `feature.factions.membership_relations` | Frakciótagság, viszonyok és frakciópasszívok | Aktív, builder-előkészítést igényel |
| <!-- icesmp-doc-id: feature.factions.guilds_leadership --> `feature.factions.guilds_leadership` | Céhek, királyság, tanács és frakciókassza | Aktív, builder-előkészítést igényel |
| <!-- icesmp-doc-id: feature.factions.land_claims --> `feature.factions.land_claims` | Claim, territory és védelem | Aktív, builder-előkészítést igényel |
| <!-- icesmp-doc-id: feature.factions.conflict_espionage --> `feature.factions.conflict_espionage` | Háború, raid, kémkedés és ostrom | Aktív, builder-előkészítést igényel |
| <!-- icesmp-doc-id: feature.world.events --> `feature.world.events` | Világesemények és bossok | Aktív, builder-előkészítést igényel |
| <!-- icesmp-doc-id: feature.world.seasons_ambient --> `feature.world.seasons_ambient` | Évszakok, közösségi célok és ambient események | Aktív, builder-előkészítést igényel |
| <!-- icesmp-doc-id: feature.world.mobs_loot --> `feature.world.mobs_loot` | Mobok, skálázás, loot és bestiárium | Aktív, builder-előkészítést igényel |
| <!-- icesmp-doc-id: feature.world.dungeons --> `feature.world.dungeons` | Dungeon kapuk és dungeon loot | Aktív, builder-előkészítést igényel |
| <!-- icesmp-doc-id: feature.world.travel_npc --> `feature.world.travel_npc` | Teleport, komp, NPC-kötés és interakció | Aktív, builder-előkészítést igényel |
| <!-- icesmp-doc-id: feature.world.parkour_discovery --> `feature.world.parkour_discovery` | Parkour, archeológia és felfedezés | Aktív, builder-előkészítést igényel |
| <!-- icesmp-doc-id: feature.world.sit_only --> `feature.world.sit_only` | Natív sit-only rendszer | Tesztelési vagy rollout-kapu alatt |
| <!-- icesmp-doc-id: feature.world.rules_protection --> `feature.world.rules_protection` | Világszabályok, crop protection és biztonsági policy | Tesztelési vagy rollout-kapu alatt |
| <!-- icesmp-doc-id: feature.world.combat_rules --> `feature.world.combat_rules` | Combat tag, párbaj és harci szabályok | Aktív, configgal engedélyezhető |
| <!-- icesmp-doc-id: feature.world.corruption_sin --> `feature.world.corruption_sin` | Korrupció, bűn és cursed hatások | Aktív, configgal engedélyezhető |
| <!-- icesmp-doc-id: feature.world.rituals --> `feature.world.rituals` | Rituálék és totemek | Aktív, builder-előkészítést igényel |
| <!-- icesmp-doc-id: feature.developer.items_debug --> `feature.developer.items_debug` | Fejlesztői tárgyak és diagnosztika | Aktív, adminisztratív |
| <!-- icesmp-doc-id: feature.planning.lore_only --> `feature.planning.lore_only` | Csak lore-ban vagy teaserben szereplő tervek | Tervezett, de nem implementált |
<!-- END GENERATED FEATURE MANIFEST MARKERS -->


## Gépileg ellenőrzött komponenslefedettség

Az alábbi rejtett azonosítók biztosítják, hogy mind az 545 production komponens egy dokumentált funkcióhoz tartozzon. A részletes forrás- és kapcsolatlista a CI artifact `components.json` és `features.json` fájljaiban található.

<!-- icesmp-doc-id: component.hu.taliann.icesmp.IceSMP -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.IceSMPBootstrap -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.IceSMPLoader -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.AbstractDispatchCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.AchievementsCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.ActivePunishmentsCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.AfkCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.BankCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.BestiaryCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.BountyCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.ClaimCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.CrateCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.CurrencyCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.DailyCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.DonationChestCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.EventsCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.ExchangeBoardCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.FactionCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.GuildCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.HonorDuelCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.HudCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.IceSMPCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.InvseeCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.ItemGiveCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.JobCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.KompCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.KronikaCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.LeaderboardCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.LoreCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.MarketCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.MemoryCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.MenuCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.ModerationActionCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.ModerationCommandSupport -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.ModerationGuiCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.ModerationRevokeCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.MuteCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.NpcBindCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.OfflineTeleportCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.ParkourCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.PartyCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.PetCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.PrivateMessageCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.ProfessionCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.ProfessionWeeklyCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.ProfileCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.PunishmentHistoryCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.QuestCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.RelicCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.ReportCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.ReportsCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.SinnerCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.SitCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.SocialSpyCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.SoulCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.SoulforgeCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.SpecCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.SpellCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.SpellbookCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.SpyCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.StatsCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.Subcommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.TalentCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.TanacsCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.TerritoryCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.UnmuteCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.VanishCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.WhisperCommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.bank.BankBalanceSubcommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.bank.BankDepositSubcommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.bank.BankSubcommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.bank.BankWithdrawSubcommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.currency.CurrencyBalanceSubcommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.currency.CurrencyExchangeSubcommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.currency.CurrencyPaySubcommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.currency.CurrencyRatesSubcommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.currency.CurrencySetSubcommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.currency.CurrencySubcommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.faction.FactionCaravanSubcommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.faction.FactionDonateSubcommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.faction.FactionJoinSubcommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.faction.FactionKingSubcommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.faction.FactionLeaveSubcommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.faction.FactionRaidSubcommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.faction.FactionSetSubcommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.faction.FactionSubcommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.faction.FactionSwitchRules -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.faction.FactionTreasurySubcommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.faction.FactionWarSubcommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.job.JobAddXpSubcommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.job.JobAdminSubcommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.job.JobGiveCatalystSubcommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.job.JobListSpellsSubcommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.job.JobSetXpSubcommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.job.JobStatusSubcommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.job.JobSubcommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.commands.job.JobUnlockSpellSubcommand -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.core.IceSMPCore -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.core.Permissions -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.crates.CrateAuditWriter -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.crates.CrateCallbackGate -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.crates.CrateCommandBatch -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.crates.CrateFormatting -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.crates.CrateLedger -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.crates.CrateOpeningLifecycle -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.crates.CrateRecoveryLedger -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.crates.CrateRewardProgress -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.crates.CrateRules -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.crates.CrateTaskLease -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.crates.CrateTaskSubmission -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.crates.KeyConsumption -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.crates.WeightedSelector -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.data.CraftingRule -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.data.CurrencyType -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.data.FactionType -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.data.JobType -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.data.ProfessionCategory -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.data.ProfessionSpecializationType -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.data.ProfessionType -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.data.SpecializationType -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.data.SpellSchool -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.data.Territory -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.data.TerritoryType -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.data.Wallet -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.BestiaryHolder -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.CharacterMenuContext -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.ClaimTrustGUI -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.ClaimTrustHolder -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.CommandMenuContext -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.CommandMenuHolder -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.CommandMenus -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.ConfigMenuGUI -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.ConfigMenuHolder -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.CrateBrowserGUI -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.CrateBrowserHolder -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.CrateSpinGUI -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.CrateSpinHolder -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.DonationChestGUI -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.DonationChestHolder -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.GuiUtil -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.InvseeGUI -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.InvseeHolder -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.JobGUI -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.JobGUIHolder -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.MarketGUI -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.MarketHolder -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.ModerationGUI -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.ModerationGuiHolder -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.PetGUI -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.PetGUIHolder -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.ProfessionGUI -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.ProfessionHolder -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.ProfessionRecipeGUI -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.ProfessionRecipeHolder -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.ProfileGUI -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.ProfileHolder -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.QuestBuilderGUI -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.QuestBuilderHolder -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.QuestLogGUI -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.QuestLogHolder -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.ShopGUI -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.ShopHolder -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.SkillTreeGUI -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.SkillTreeHolder -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.SpecGUI -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.SpecHolder -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.SpellbookGUI -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.SpellbookHolder -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.TalentGUI -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.gui.TalentHolder -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.integration.DruidDisguise -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.integration.FancyNpcsQuestBridge -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.integration.IceSMPPlaceholders -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.integration.LuckPermsBridge -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.integration.ProtectionBridge -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.integration.SpyDisguise -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.items.BlueprintItemFactory -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.items.CaptureItemFactory -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.items.CatalystItemFactory -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.items.CrateKeyFactory -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.items.CurrencyItemFactory -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.items.DevItemFactory -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.items.ItemDataFactory -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.items.MoneyPouchItemFactory -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.items.RelicItemFactory -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.items.SiegeWeaponFactory -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.items.SignatureEnchantKeys -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.items.UniqueMaterialFactory -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.AbilityCatalystListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.AbundanceListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.AfkActivityListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.ArcheologyShareListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.BestiaryListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.BlueprintUseListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.CampfireStoryListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.CapitalLawListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.CaravanListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.CatalystCraftSafetyListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.CatalystProtectionListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.CharacterGUIListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.ChatFormatListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.ChatModerationListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.ClaimProtectionListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.ClaimTrustGUIListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.ClassXpListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.CombatTagListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.CommandMenuListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.ConfigMenuGUIListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.CorruptionAuraListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.CorruptionListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.CrateBrowserGUIListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.CrateListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.CrateSpinGUIListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.CurrencyCraftListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.CurrencyItemRefreshListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.CursedGearListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.DailyQuestListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.DamageIndicatorListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.DeathRecapListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.DevItemProtectionListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.DisplayFxCleanupListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.DonationChestListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.DungeonGateListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.DungeonLootListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.ElytraRelicListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.EscortListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.FactionFoodListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.FactionPassiveListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.FactionSpawnListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.FishingWindfallListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.GatheringBuffListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.HealthRegenListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.HudListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.IntroListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.InvseeGUIListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.ItemProvenanceListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.JobCraftRestrictionListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.JobGUIListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.LowHealthBorderListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.MarketDeliveryListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.MarketGUIListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.MasterworkCraftListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.MetelytepoRelicListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.MinionProtectionListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.MobLootListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.MobMoneyDropListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.MobScalingListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.ModerationGUIListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.ModerationLoginListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.MoneyPouchListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.MotdListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.OnboardingListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.ParkourListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.PartyListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.PetCaptureListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.PetCombatListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.PetCommandListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.PetGUIListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.PetXpListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.PlayerSessionCleanupListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.PortalGuardListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.ProfessionRecipeBookListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.ProfessionRecipeListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.ProfessionXpListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.QuestBuilderListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.QuestLogListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.QuestProgressListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.RelicCraftSafetyListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.RelicInactivityListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.RelicItemRefreshListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.RelicPvpTransferListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.RelicTriggerListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.ReportFeedbackListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.ResourceCombatListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.RitualListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.RuneApplyListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.RuneEffectListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.SchoolCounterAnvilListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.SelectionWandListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.ServerChallengeListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.ShopListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.SiegeWeaponListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.SignatureItemListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.SinListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.SitListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.SkillTreeGUIListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.SoulShardListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.SoulstoneListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.SpellDamageListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.SpellProjectileListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.SpellStateListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.SpellbookListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.SpyRevealListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.StatsCombatListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.StrangerListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.TalentAttributeListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.TerritoryListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.TerritoryProtectionListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.TheftListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.TreasureListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.UniqueMaterialProtectionListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.VanishListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.WhisperListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.WildHuntListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.WorldBossListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.WorldGameRuleListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.listeners.WorldTweaksListener -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.AbundanceManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.AchievementManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.AdvancementService -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.AfkManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.AmbientEventManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ArcheologyManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.BardManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.BestiaryManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.BlockRegenService -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.BloodMoonManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.BuyerService -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.CaravanManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ChronicleManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.CityGuardManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ClaimManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ClassHealthService -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.CombatTagManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.CommunityGoalManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.CommunitySeasonState -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ConfigManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ConfigValidator -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.CorruptionManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.CouncilManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.CraftingRestrictionManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.CrateManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.CrownCurseManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.CultistEventManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.CurrencyManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.CurrencyStorageUnavailableException -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.CursedGearService -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.DailyQuestManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.DarkUndeadAmbienceManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.DevItemManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.DevItemStateData -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.DialogService -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.DonationChestManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.DungeonLootService -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.EconomyEventManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.EscortManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.EventSpawnGuard -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.EventSpawnPointManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ExchangeBoardManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ExchangeRateService -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.FactionManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.FactionRelationManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.FactionTreasuryManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.FerryManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.GatheringBuffManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.GlobalAfkTracker -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.GuildManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.HiddenSpotManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.HolidayService -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.HonorDuelManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.HudManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.IntroManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.InvasionManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.InvseeManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ItemRarityService -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.JobManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.KingManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.LootTable -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.MajorEventGate -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.MarketManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.MetelytepoManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.MeteorEventManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.MinionManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.MobScalingManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ModerationManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.NpcBindingManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ParkourManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.PartyManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.PetManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.PlayerCaravanManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ProfessionManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ProfessionRecipeCatalog -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ProfessionRecipeManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ProfessionWeeklyGoalManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.QuestManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.RaidManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.RelicCooldownService -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.RelicManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ReportManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ResourceBonusService -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ResourceManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.RespecService -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.RitualManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.SeasonFinaleManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.SeasonManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.SeasonMonumentManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.SeasonRewardStateData -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.SeasonStoryTeller -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.SeasonalModifierService -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ServerChallengeManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.ShopManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.SinManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.SitManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.SitState -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.SoulShardManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.SoulforgeManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.SpecializationManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.SpellFavoritesManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.SpellMasteryManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.SpellRegistry -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.SpyManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.StatsManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.StrangerNpcManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.TablistManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.TablistOrdering -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.TalentManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.TerritoryManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.TerritoryProtectionService -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.TotemManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.TreasureEventManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.VanishManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.WarWindowManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.WhisperManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.WildHuntManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.managers.WorldBossManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.EntityTaskSubmission -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.InventoryEscrowGate -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.InventoryEscrowQueue -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.InventoryTransferBarrier -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.InventoryWriteRecovery -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.InvseeEscrowSchema -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.LastKnownLocation -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.ModerationDuration -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.ModerationMutationGate -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.ModerationSpamGuard -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.ModerationTextFilter -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.PaperEntityTaskSubmission -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.PunishmentLedger -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.PunishmentRecord -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.PunishmentState -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.PunishmentType -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.ReplyPartnerRegistry -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.SchedulerCallbackGate -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.StrictYamlNumber -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.moderation.TaskLease -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.motd.MotdGenerationGate -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.motd.MotdIconValidator -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.motd.MotdSelector -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.relics.RelicDefinition -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.relics.RelicOwnership -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.relics.RelicRegistry -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.relics.RelicTrigger -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.relics.RelicTriggerConfig -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.relics.SimpleRelicDefinition -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.relics.ability.RelicAbility -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.relics.ability.RelicAbilityContext -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.relics.ability.RelicAbilityRegistry -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.session.PlayerStateCleanup -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.sit.SitGeometry -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.sit.SitPolicy -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.AngryChickenSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.AntidoteSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.ArmamentSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.BaseSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.BeeSwarmSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.BlinkSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.BoneChillSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.BulwarkSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.ChainsOfIceSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.ConfiguredSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.ConfusionSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.DeepBreathSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.DemonicCircleSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.DevotionAuraSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.DoubleJumpSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.DruidFormSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.EagleEyeSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.ExpelHarmSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.FeastSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.FeatherfootSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.FlyingSerpentKickSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.FriendshipSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.FrostFeverSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.GlaiveThrowSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.GustSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.HideSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.HolyWrathSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.InnerFocusSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.LifeDrainSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.LivingFlameSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.LuckyStarSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.MindBlastSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.MultishotSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.PrimalBondSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.ProjectileBurstSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.RainDanceSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.RootSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.RuneStrikeSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.ShadowburnSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.ShadowstepSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.ShamanTotemSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.SmokeBombSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.SoulExchangeSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.SpectralSightSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.Spell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.SpellCatalog -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.SpellCostType -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.SpellTargetingUtil -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.SpinningCraneKickSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.SummonMinionsSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.SunDanceSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.VenomStrikeSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.WhirlwindSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.WildMushroomSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.WisplightSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.spells.WolfCallSpell -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.storage.BlockRegenJournal -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.storage.CorruptStateFileError -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.storage.CriticalPersistenceWriteError -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.storage.PersistentStore -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.storage.PersistentStoreCoordinator -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.storage.TransactionJournal -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.storage.YamlStore -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.CcDiminish -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.DailyBudget -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.DisplayFxUtil -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.ExperienceUtil -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.GameModeCache -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.ItemProvenance -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.LocalAnnounce -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.MessageManager -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.MobKillUtil -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.ParticleUtil -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.PartyRewardResolver -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.PeriodicChanceEvent -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.PlainIngredients -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.PositionCache -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.SpellDamageUtil -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.SpellVfx -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.TabCompleteUtil -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.TextAnimator -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.TextUtil -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.ToastUtil -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.TransientEntities -->
<!-- icesmp-doc-id: component.hu.taliann.icesmp.utils.UndeadUtil -->
