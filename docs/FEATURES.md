# IceSMP — a világ rendszerei

<!-- icesmp-doc-id: reference.feature-catalogue -->

> *A Fa visszahívott, de nem mondta meg, mivé kell válnod.*
>
> Az IceSMP-ben a harc, a mesterségek, a politika, a kereskedelem és a történet
> ugyanannak a világnak a részei. Ez a katalógus azt mutatja meg, milyen életet
> élhetsz benne — és azt is őszintén jelzi, amihez még világépítés vagy runtime próba kell.

Ez az oldal az **egyetlen általános funkciótérkép**. A használat részletei a
[játékos-](PLAYER_GUIDE.md), [builder-](BUILDER_GUIDE.md) és
[admin kézikönyvben](ADMIN_GUIDE.md), a jelenlegi szerverbuildhez képesti eltérések pedig a
[legújabb változásokban](LATEST_CHANGES.md) találhatók.

## Hogyan olvasd?

- **Aktív**: a forrásban valódi elérési útja van.
- **Builder-előkészítést igényel**: a rendszer működik, de a helyszín, NPC vagy világkötés még lehet hiányos.
- **Configgal engedélyezhető**: a képesség létezik, az élő beállítás dönti el, fut-e.
- **Rollout-kapu alatt**: kódszinten és CI alapján jelen van, de productionközeli kézi próba kell.
- **Tervezett**: lore- vagy kommunikációs irány; nem ígért, aktív gameplay.

> **Leltár, nem olvasnivaló:** a 68 root parancs, 286 route, 79+93 alias,
> 44 permission, 13 550 configútvonal és 545 production komponens teljes technikai
> referenciáját a `Repository Docs Inventory` CI-artifact generálja. Itt csak az marad,
> ami egy játékosnak vagy csapattagnak valóban segít megérteni a rendszert.

A katalógus **48 implementált rendszercsoportot** és **1 tudatos planning-határt** ír le.
A release forrásállapota `4643ab53586f0c1ee7352df16dcd477013e6fad4`; az üzemeltető által
futóként átadott JAR nagy bizonyossággal a 2026. július 12-i `775d9e247…` állapothoz tartozik.

## Alaprendszer és üzemeltetés

A játékos ebből többnyire csak annyit érez, hogy a világ emlékszik rá, a parancsok következetesen válaszolnak, és egy újratöltés után sem marad minden félúton. Az admincsapat számára viszont ez a biztonsági háló, amelyre minden más rendszer épül.

### Plugin-életciklus és rendszerindítás

<!-- icesmp-doc-id: feature.core.lifecycle -->

> **Tesztelési vagy rollout-kapu alatt** · A futó JAR-hoz képest: **Már korábban is elérhető volt**

Az IceSMP szolgáltatásainak, parancsainak és eseménykezelőinek rendezett indítása, újratöltése és leállítása.

- **Így találkozol vele:** Szerverindítás, plugin enable/disable; adminisztratív `/icesmp reload`.
- **Kinek szól:** Fejlesztő/üzemeltető, Admin, Tesztelő.
- **Mitől mozdul meg:** Szerverindításkor felépíti, leállításkor lezárja a szolgáltatásokat.
- **Ami még kellhet hozzá:** Nincs külön világépítési feladat; deployment előtt mentés és staging-indítás szükséges.
- **Fontos határ:** A sikeres CI nem bizonyítja a teljes Folia/Paper production életciklust.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: —
- Config: `general.*`, valamint az egyes alrendszerek saját gyökérszakaszai.
- Tartós állapot: A szolgáltatások saját store-jait koordinálja; önmagában nem játékosadat.
- Reload: A `/icesmp reload` támogatott részeket frissít; scheduler-periódus vagy strukturális változás restartot igényelhet.

</details>

### Konfiguráció, üzenetek és validáció

<!-- icesmp-doc-id: feature.core.configuration -->

> **Aktív, adminisztratív** · A futó JAR-hoz képest: **Jelentősen megváltozott**

A csomagolt YAML-alapértékeket, az élő felülírásokat és a lokalizált üzeneteket egységes snapshotként kezeli.

- **Így találkozol vele:** `/icesmp reload`, `/icesmp config`, konfigurációs GUI és fájlszerkesztés. Parancs: /icesmp config; /icesmp reload. GUI: Config menü.
- **Kinek szól:** Admin, Fejlesztő/üzemeltető, Tesztelő.
- **Mitől mozdul meg:** Induláskor és reloadkor beolvasás/ellenőrzés történik.
- **Ami még kellhet hozzá:** Az élő configot verziózott mentéssel kell kezelni; világazonosítók és helyek módosítása előtt staging-ellenőrzés kell.
- **Fontos határ:** Nincs minden kulcshoz teljes schema-validator; hibás típusnál fallback vagy alrendszer-letiltás is lehetséges.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: Kapcsolódó/ágankénti követelmény: `icesmp.admin.config`
- Config: Minden aktív config; részletesen a konfigurációs referenciában.
- Tartós állapot: A konfiguráció fájlban tartós; a runtime snapshot memóriában él.
- Reload: A támogatott cache-ek atomikusan frissülnek; bizonyos struktúrák és már futó taskok restartot igényelnek.

</details>

### Persistence, naplók és recovery

<!-- icesmp-doc-id: feature.core.persistence -->

> **Tesztelési vagy rollout-kapu alatt** · A futó JAR-hoz képest: **Belső megbízhatósági javítás**

Koordinált YAML-store-ok, tranzakciós naplók, sérült állapot felismerése és kritikus írási hibák láthatóvá tétele.

- **Így találkozol vele:** Automatikus; egyes recovery-folyamatokhoz adminparancs vagy logellenőrzés tartozik.
- **Kinek szól:** Admin, Fejlesztő/üzemeltető, Tesztelő.
- **Mitől mozdul meg:** Állapotváltozáskor, periodikus flushkor, reload/disable alatt és indulási recoverykor.
- **Ami még kellhet hozzá:** Nincs builderfeladat; rendszeres mentés, írási jog és elegendő tárhely szükséges.
- **Fontos határ:** Lemezhiba és félbeszakított tranzakció csak célzott fault-injection teszttel igazolható.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: —
- Config: Az érintett rendszerek persistence-, audit- és recovery-beállításai.
- Tartós állapot: YAML-state, tranzakciós journal, blokk-regenerációs journal és rendszer-specifikus store-ok.
- Reload: Reload előtt flush és új snapshot; egyes journal-recovery csak induláskor fut.

</details>

### Külső integrációs hidak

<!-- icesmp-doc-id: feature.core.integrations -->

> **Aktív, külső integrációval bővül** · A futó JAR-hoz képest: **Jelentősen megváltozott**

LuckPerms-, védelem-, placeholder- és FancyNpcs-kapcsolatok, valamint kaszt-/kémálca kompatibilitási pontok.

- **Így találkozol vele:** Automatikus soft-dependency felismerés; NPC-kötés és pluginonkénti konfiguráció.
- **Kinek szól:** Admin, Builder, Fejlesztő/üzemeltető, Tesztelő.
- **Mitől mozdul meg:** A partnerplugin jelenlétekor és az érintett eseményeknél.
- **Ami még kellhet hozzá:** NPC-ket és védett régiókat a tényleges partnerpluginban is létre kell hozni.
- **Fontos határ:** A pontos működés a telepített külső plugin verziójától és élő configjától függ.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: —
- Config: `integrations.*`, protection-, NPC- és placeholder-beállítások.
- Tartós állapot: Az IceSMP saját kötéseket tárolhat; a külső plugin adatai nem részei az IceSMP-mentésnek.
- Reload: A legtöbb híd következő eseménynél az új snapshotot használja; pluginlista-változás restarthoz kötött.

</details>

### Közös végrehajtási és segédszolgáltatások

<!-- icesmp-doc-id: feature.core.shared_services -->

> **Aktív, adminisztratív** · A futó JAR-hoz képest: **Belső megbízhatósági javítás**

Közös dispatch, tab completion, ütemezésbiztos callbackek, pozíció- és játékmód-cache, szöveg- és vizuális segédek.

- **Így találkozol vele:** Közvetlen játékosbelépési pont nincs; más funkciók használják.
- **Kinek szól:** Fejlesztő/üzemeltető, Tesztelő.
- **Mitől mozdul meg:** Az őket hívó parancsok, GUI-k és listenerek részeként.
- **Ami még kellhet hozzá:** Nincs közvetlen builderfeladat.
- **Fontos határ:** Önálló játékosfunkcióként nem értelmezhető; a komponensmap a teljes technikai lefedettség miatt tartalmazza.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: —
- Config: Több alrendszer configja; nincs egyetlen közös publikus gyökér.
- Tartós állapot: Általában memóriabeli; a tulajdonos rendszer felel a tartósságért.
- Reload: A tulajdonos rendszer reloadszabálya érvényes.

</details>

## Adminisztráció és moderáció

Egy lore-szerver akkor marad élhető, ha a szabályok nemcsak le vannak írva, hanem visszakövethetően és a szükséges legkisebb jogosultsággal alkalmazhatók. Itt található minden, ami a rend fenntartásához kell.

### Admin- és konfigurációs kezelőfelület

<!-- icesmp-doc-id: feature.admin.control_panel -->

> **Aktív, adminisztratív** · A futó JAR-hoz képest: **Új**

Jogosultságszűrt admin súgó, reload, config-kezelés, inspect és főmenüből elérhető adminműveletek.

- **Így találkozol vele:** `/icesmp`, `/icesmp reload`, `/icesmp config`, admin főmenü. Parancs: /icesmp (alias: /ismp).
- **Kinek szól:** Admin, Fejlesztő/üzemeltető, Tesztelő.
- **Mitől mozdul meg:** Csak explicit parancs vagy GUI-kattintás.
- **Ami még kellhet hozzá:** Nincs világépítési feladat; szerepkörönként külön permissionmátrix kell.
- **Fontos határ:** Kritikus jog; ne adj teljes admin-parentet napi moderátori szerepkörnek.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: Kapcsolódó/ágankénti követelmény: `icesmp.admin.all`; `icesmp.admin.config`; `icesmp.admin.inspect`; `icesmp.admin.reload`; `icesmp.admin.reload + icesmp.admin.config`; `icesmp.admin.reload + icesmp.admin.inspect`
- Config: `general.*`, GUI- és permissionbeállítások.
- Tartós állapot: Az adminműveletek által módosított config/store tartós lehet; a GUI-state nem.
- Reload: A reloadág a támogatott alrendszereket frissíti.

</details>

### Natív büntetési és moderációs rendszer

<!-- icesmp-doc-id: feature.admin.moderation -->

> **Tesztelési vagy rollout-kapu alatt** · A futó JAR-hoz képest: **Új**

Warning, kick, mute, temp mute, ban, temp ban, feloldás, előzmény, aktív büntetés, GUI, audit és expiry.

- **Így találkozol vele:** `/warn`, `/kick`, `/mute`, `/tempban`, `/ban`, `/unmute`, `/unban`, `/history`, `/punishments`, `/moderation`. Parancs: /ban; /history; /kick; /moderation (alias: /mod); /mute; /punishments; /tempban; /unban; /unmute; /warn. GUI: Moderációs GUI.
- **Kinek szól:** Moderátor, Admin, Tesztelő, Fejlesztő/üzemeltető.
- **Mitől mozdul meg:** Login-ellenőrzés, chatblokkolás, büntetés-expiry és adminművelet.
- **Ami még kellhet hozzá:** Nincs builderfeladat; előre rögzített permissionmátrix és ügyrend kell.
- **Fontos határ:** SModeration csak restart-, corrupt-state-, lemezhiba- és permissionteszt után távolítható el.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: Kapcsolódó/ágankénti követelmény: `icesmp.admin.moderation`; `icesmp.moderation.ban`; `icesmp.moderation.gui`; `icesmp.moderation.history`; `icesmp.moderation.inventory.edit`; `icesmp.moderation.inventory.read`; `icesmp.moderation.kick`; `icesmp.moderation.mute`; `icesmp.moderation.offlinetp`; `icesmp.moderation.socialspy`; `icesmp.moderation.vanish`; `icesmp.moderation.warn`
- Config: `moderation.*`, kapcsolódó messages és permissions.
- Tartós állapot: Büntetések, audit és expiry-adatok tartós store-ban.
- Reload: Szöveg/policy reloadolható; aktív expiry és recovery restarttesztet igényel.

</details>

### Report, privát üzenet, reply és SocialSpy

<!-- icesmp-doc-id: feature.admin.reports_messaging -->

> **Tesztelési vagy rollout-kapu alatt** · A futó JAR-hoz képest: **Új**

Játékosreportok, kezelői reportnézet, privát üzenetek, reply partner és jogosultságkötött SocialSpy.

- **Így találkozol vele:** `/report`, `/reports`, `/msg`, `/tell`, `/w`, `/reply`, `/socialspy`, `/suttogas`. Parancs: /msg; /reply (alias: /r); /report (alias: /bejelent); /reports; /socialspy; /suttogas (alias: /sutt); /tell; /w.
- **Kinek szól:** Játékos, Moderátor, Admin, Tesztelő.
- **Mitől mozdul meg:** Parancs, report-feedback esemény és SocialSpy figyelés.
- **Ami még kellhet hozzá:** Nincs builderfeladat; adatkezelési és hozzáférési szabály kell.
- **Fontos határ:** A privát kommunikáció érzékeny adat; csak kijelölt szerepkör kapjon megfigyelési jogot.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: Kapcsolódó/ágankénti követelmény: `icesmp.admin.moderation`; `icesmp.message`; `icesmp.moderation.socialspy`
- Config: `moderation.*`, private-message-, report-, SocialSpy- és message-beállítások.
- Tartós állapot: Reportok tartósak; reply-partner és egyes SocialSpy-sessionállapotok memóriabeliek.
- Reload: Policy és szövegek reloadolhatók; quit–reconnect viselkedést runtime kell igazolni.

</details>

### Vanish és láthatóság

<!-- icesmp-doc-id: feature.admin.vanish -->

> **Tesztelési vagy rollout-kapu alatt** · A futó JAR-hoz képest: **Új**

Moderátori rejtőzés, külön láthatósági jog és kapcsolódó játékosszám-/megjelenítéskezelés.

- **Így találkozol vele:** `/vanish`; jogosultság alapján más staff láthatja. Parancs: /vanish (alias: /v).
- **Kinek szól:** Moderátor, Admin, Tesztelő.
- **Mitől mozdul meg:** Állapotváltás, join/quit, player-info és server-list frissítés.
- **Ami még kellhet hozzá:** Nincs builderfeladat.
- **Fontos határ:** Más vanish/tablist pluginnal való együttműködés csak runtime igazolható.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: Kapcsolódó/ágankénti követelmény: `icesmp.admin.moderation`; `icesmp.moderation.vanish`; `icesmp.moderation.vanish.see`
- Config: `vanish.*`, `motd.*`, `tablist.*` és permissions.
- Tartós állapot: A vanish állapot rendszerbeállítástól függően tartós vagy sessionjellegű; reconnectet tesztelni kell.
- Reload: Policy reloadolható; már elküldött láthatósági csomagok következő frissítéskor rendeződnek.

</details>

### Inventory-admin, escrow és offline teleport

<!-- icesmp-doc-id: feature.admin.inventory_teleport -->

> **Tesztelési vagy rollout-kapu alatt** · A futó JAR-hoz képest: **Új**

Online inventory/ender chest olvasás és szerkesztés, biztonságos escrow/recovery, valamint utolsó ismert helyre offline teleport. Egy céljátékoshoz egyszerre egy write session tartozhat: edit joggal az első megnyitó kapja automatikusan, minden további egyidejű megnyitó read-only.

- **Így találkozol vele:** `/invsee`, `/offlinetp`; Invsee GUI. Parancs: /invsee; /offlinetp.
- **Kinek szól:** Moderátor, Admin, Tesztelő, Fejlesztő/üzemeltető.
- **Mitől mozdul meg:** Explicit adminművelet, bezárás, disconnect, reconnect és indulási recovery.
- **Ami még kellhet hozzá:** Nincs builderfeladat; recovery-helyet és manuális eljárást dokumentálni kell.
- **Fontos határ:** InvSee++ csak disconnect/restart/lemezhiba és read/edit permissionteszt után távolítható el.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: Kapcsolódó/ágankénti követelmény: `icesmp.admin.moderation`; `icesmp.moderation.inventory.edit`; `icesmp.moderation.inventory.read`; `icesmp.moderation.offlinetp`; `read: icesmp.moderation.inventory.read; write: icesmp.moderation.inventory.edit`
- Config: `moderation.*`, invsee-, escrow-, audit- és permissionbeállítások.
- Tartós állapot: Escrow és utolsó ismert hely tartós; nyitott GUI sessionállapot.
- Reload: Config reloadolható, de függőben lévő escrow-nál előbb settlement/recovery szükséges.

</details>

## Játékosállapot és szervermegjelenítés

Ez a réteg ad arcot a szervernek: mit látsz belépéskor, mi kerül a HUD-ra, hogyan szól hozzád a chat, és miként jelzi a világ, hogy éppen mi történik.

### Globális AFK-rendszer

<!-- icesmp-doc-id: feature.player.global_afk -->

> **Tesztelési vagy rollout-kapu alatt** · A futó JAR-hoz képest: **Új**

Kézi és inaktivitásalapú AFK-állapot, tablistajelzés és útvonalanként eltérően konfigurálható jutalomblokkolás, jutalmazó zónák nélkül.

- **Így találkozol vele:** `/afk`; az aktivitás visszavonja az állapotot.
- **Kinek szól:** Játékos, Admin, Tesztelő.
- **Mitől mozdul meg:** Inaktivitási idő, mozgás és játékosaktivitás; a profession- és közös kill/boss jutalomkapuk configot is olvasnak, a fishing windfall és ambient pénzjutalom AFK esetén feltétel nélkül tilt.
- **Ami még kellhet hozzá:** Nincs AFK-zóna, bossbar vagy jutalmazó terület; builder-előkészítés nem kell.
- **Fontos határ:** A `afk.block-rewards` nem univerzális főkapcsoló: profession-XP-re közvetlenül, kill/boss útvonalra a `kill-rewards.afk-block` fallbackjeként hat; fishing windfall és ambient pénzjutalom mindig tiltott AFK esetén. A release-ben nincs `afk.zones`, zónajutalom vagy AFK-bossbar.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: —
- Config: `afk.afk-after-seconds`, `afk.block-rewards`.
- Tartós állapot: Nincs tartós AFK-store; sessionállapot.
- Reload: Timeout, profession-XP és a közös kill/boss jutalomkapu reload után az új snapshotból működik; a fishing windfall és ambient jutalom tiltása nem configvezérelt.

</details>

### Főmenü, profil és karakterlap

<!-- icesmp-doc-id: feature.player.menus_profile -->

> **Aktív és játékosok számára elérhető** · A futó JAR-hoz képest: **Jelentősen megváltozott**

Központi játékosmenük, karakteradatok, tematikus navigáció és jogosultságfüggő adminbelépési pont.

- **Így találkozol vele:** `/menu`, `/profile` és több tematikus parancs GUI-megnyitása. Parancs: /menu (alias: /hub, /m); /profile (alias: /char, /karakter, /status). GUI: Főmenü és tematikus parancsmenük; Karakterlap; Specializációk; Szakmaválasztó; Talent-fa.
- **Kinek szól:** Játékos, Admin, Tesztelő.
- **Mitől mozdul meg:** Kizárólag parancs vagy GUI-kattintás.
- **Ami még kellhet hozzá:** Nincs kötelező világépítési feladat; resource-pack modellek megjelenését ellenőrizni kell.
- **Fontos határ:** Egyes csempék csak akkor aktívak, ha a kapcsolódó rendszer és permission elérhető.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: Kapcsolódó/ágankénti követelmény: `icesmp.admin.config`; `icesmp.admin.events`; `icesmp.admin.exchangeboard`; `icesmp.admin.item`; `icesmp.admin.npc`; `icesmp.admin.quest`; `icesmp.admin.reload`
- Config: GUI-, profil-, általános és üzenetbeállítások.
- Tartós állapot: A profil mögötti játékosadat tartós; a megnyitott GUI-state sessionjellegű.
- Reload: Megjelenési config következő megnyitáskor frissül; struktúraváltásnál újranyitás kell.

</details>

### HUD és natív tablista

<!-- icesmp-doc-id: feature.player.hud_tablist -->

> **Tesztelési vagy rollout-kapu alatt** · A futó JAR-hoz képest: **Jelentősen megváltozott**

Kapcsolható HUD, rendezett tablista, szerep-/állapotjelzések és IceSMP-specifikus szerverinformációk. A tablista LuckPerms-rang szerint rendez (`tablist.sorting.group-order`), az AFK játékosok a teljes lista végére kerülnek, és az AFK-blokkon belül is a rang+név sorrend érvényesül. A név-színek frakciónként a `tablist.faction-colors.*` kulcsokból jönnek — a Menedék-polgár zöld (Smaragdkő/Ryanora lore-szín), így nem téveszthető össze a sötétszürke Kitaszítottal; a chat-névszín és a `/menu` frakcióválasztó ugyanezt a palettát követi, a raid alatti háborús jelölés színe pedig a `tablist.nametags.war-color` kulccsal hangolható.

- **Így találkozol vele:** `/hud`; a tablista automatikus.
- **Kinek szól:** Játékos, Admin, Tesztelő.
- **Mitől mozdul meg:** Csatlakozáskor, periodikus frissítéskor, státusz- és adatváltozáskor.
- **First-party IceSMP HUD:** a resource pack elfogadása után játékosonként aktiválódó,
  rögzített geometriájú kijelzés öt teljesen külön skinből (a Menedék vendége saját erődkeretet kap),
  13 class-ikonból, class/spec/resource/mechanika állapotból,
  legfeljebb öt generic metricből, kilenc charge-pipből, DK-rúnákból és négy külön pénztárcahelyből.
  A fő frakcióvaluta mindig, a másik három banki valuta nulla egyenlegnél is a saját, stabil
  helyén jelenik meg. A tartós class XP-sáv nem foglalja az eseményláblécet; az legfeljebb három
  párhuzamos eseményt ír ki teljes szélességben.
- **Skálázásbiztos Player Frame:** bal felső, frakciószínű, gyors külön tickből frissülő HP-sáv
  jelenlegi/maximális értékkel, százalékkal és absorption-jelöléssel, továbbá pontos páncél-, étel-
  és feltételes oxigénkijelzéssel; az armor maximum nélküli flat szám, az oxigénoszlop csak fogyó
  levegőnél jelenik meg. A frame és minden érték saját editor-komponens. A kiadott pack
  csak a normál vanilla survival sprite-okat teszi
  átlátszóvá; hardcore szíveket szándékosan nem fed le. A panel nem függ a class/sidebar HUD
  láthatóságától, így `/hud mind` vagy natív class-HUD routing sem hagyhatja vakon a játékost.
- **Fallback:** pack nélkül a natív compact Folia HUD marad. A resource-packes megjelenítés az
  IceSMP first-party része; a survival értékeket ilyenkor a változatlan vanilla sávok mutatják,
  külső HUD plugin pedig nincs a runtime- vagy dependency-stackben.
- **Információs parity:** frakció, class, specializáció, class-szint, pénz, aktuális event,
  class resource és a játékhoz szükséges elsődleges/másodlagos, illetve összetett kiegészítő
  mechanikák ugyanabból az immutable HUD snapshotból készülnek. A Wizard három elemi
  ráhangolódása külön mini bar, a diszkrét combo/stack/charge értékek pedig vizuális pipsort kapnak.
- **Személyes layout-editor:** a `/hud edit` lapozott, kattintható nézete kijelölt-elem kiemelést,
  1/5/10/15 pixeles mozgatást, közvetlen X/Y/méret bevitelt, preset- és szintetikus preview-váltást,
  láthatóságot, undo/reset/save/cancel műveleteket ad. A Player, Target és Party Frame külön
  mozgatható csoport, alattuk minden grafika/adat saját elem. A DK-rúnák saját kategóriában vannak,
  és nem osztoznak a többi kaszt generic charge-komponensével. A Profile v2 csak a tiszta
  `hud.layout-v2.*` eltéréseket tárolja; legacy layout-migráció nincs.
- **Target Frame:** egy játékos vagy mob megsebzése eseményvezérelten screen-space célpontkeretet
  nyit a Player Frame mellett. A mob passzív/semleges/ellenséges/elit/boss bestiárium-grafikát,
  a játékos saját frakciószínt kap; név, szint, current/max HP, százalék, státusz és játékosnál
  class resource jelenik meg. Követő vitals-TextDisplay nincs, az eredeti entitásnévhez nem nyúl.
- **Party Frame:** a Player Frame alatt legfeljebb négy másik tag WoW-stílusú sora látszik.
  A sor palettája mindig az adott tag frakciója; HP/resource mellett vezető, halott, távoli és
  offline állapotot is mutat. A renderer kizárólag immutable, Folia-safe cache-eket olvas.
- **Ami még kellhet hozzá:** Nincs builderfeladat; a saját tablista és HUD production megjelenését deployment előtt ellenőrizni kell.
- **Fontos határ:** A natív tablista az IceSMP-hez szükséges funkciókat biztosítja, nem általános külső tablista-motor.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: Kapcsolódó/ágankénti követelmény: `icesmp.admin.moderation`; `icesmp.moderation.vanish`; `icesmp.moderation.vanish.see`
- Config: `hud.*`, `tablist.*`.
- Tartós állapot: A HUD-szekciók és a ritka személyes layout-felülírások a Profile v2
  `preferences` szekciójában maradnak meg; a globális alap config, a tablista runtime nézet.
- Reload: Szövegek és megjelenési opciók reloadolhatók; futó frissítési periódus restartot igényelhet.

</details>

### Natív MOTD és szerverlista-megjelenítés

<!-- icesmp-doc-id: feature.player.motd -->

> **Tesztelési vagy rollout-kapu alatt** · A futó JAR-hoz képest: **Új**

TIME/RANDOM MOTD-választás, eseményprioritás, vanished játékosok szűrése és ellenőrzött ikonmódok.

- **Így találkozol vele:** Automatikus server-list ping; admin `/icesmp reload`.
- **Kinek szól:** Fejlesztő/üzemeltető, Admin, Tesztelő.
- **Mitől mozdul meg:** Minden szerverlista-pingnél biztonságos snapshotból.
- **Ami még kellhet hozzá:** Az ikonfájlokat helyesen kell telepíteni; PNG, symlink és fallback viselkedést tesztelni kell.
- **Fontos határ:** Az élő ikonfájlok és MiniMOTD nélküli production viselkedés forrásból nem bizonyítható.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: —
- Config: `motd.*`, ikon- és eseménybeállítások.
- Tartós állapot: Nincs játékosállapot; a kiválasztási snapshot memóriabeli.
- Reload: Gyors reload támogatott, de párhuzamos ping és scheduler rejection runtime tesztet igényel.

</details>

### Chat, vizuális visszajelzés és harci kijelzés

<!-- icesmp-doc-id: feature.player.chat_feedback -->

> **Aktív, configgal engedélyezhető** · A futó JAR-hoz képest: **Jelentősen megváltozott**

Chat-formázás, üzenetkezelés, sebzésjelzés, halálösszegzés, alacsony élet jelzése és ideiglenes vizuális effektek.

- **Így találkozol vele:** Automatikus; az üzenetszövegek configból érkeznek.
- **Kinek szól:** Játékos, Moderátor, Admin, Tesztelő.
- **Mitől mozdul meg:** Chat-, sebzés-, halál- és állapot-eseményeknél.
- **Ami még kellhet hozzá:** Nincs világépítési feladat.
- **Fontos határ:** Más chat/HUD pluginnal való prioritáskülönbséget runtime kell ellenőrizni.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: —
- Config: `messages.*`, chat-, HUD- és vizuális beállítások.
- Tartós állapot: Többségében sessionállapot; statisztika vagy moderáció külön store-ba kerülhet.
- Reload: Üzenetek reloadolhatók; aktív ideiglenes effektek nem feltétlenül épülnek újra.

</details>

### Beléptetés és kezdőfolyamat

<!-- icesmp-doc-id: feature.player.onboarding -->

> **Aktív, builder-előkészítést igényel** · A futó JAR-hoz képest: **Jelentősen megváltozott**

Első belépéshez és bevezető történeti lépésekhez kapcsolódó automatikus játékosfolyamat.

- **Így találkozol vele:** Első belépés és kapcsolódó introállapot.
- **Kinek szól:** Játékos, Admin, Builder, Tesztelő.
- **Mitől mozdul meg:** Join, respawn vagy konfigurált bevezető trigger.
- **Ami még kellhet hozzá:** Spawn-, bevezető helyszín és biztonságos teleportpont szükséges lehet.
- **Fontos határ:** A tényleges élő world és már bevezetett játékosállomány nincs a repositoryban.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: —
- Config: Intro-, onboarding-, world- és message-beállítások.
- Tartós állapot: A teljesített bevezető állapota tartós.
- Reload: Szöveg és célpont frissülhet; folyamatban lévő játékosnál staging-próba kell.

</details>

## Karakterfejlődés és tartalom

A Felsők emlékei elvesztek, de a vérük emlékezik. A kaszt, a szakma, a megtalált tervrajz és egy visszaszerzett emlékszilánk mind ugyanannak az útnak egy darabja: annak, akivé a karaktered válik.

### Kasztok, specializációk és kasztképességek

<!-- icesmp-doc-id: feature.progression.classes_specializations -->

> **Aktív és játékosok számára elérhető** · A futó JAR-hoz képest: **Jelentősen megváltozott**

Kasztválasztás, XP/szint, specializáció, kasztpasszívok és admin XP/unlock műveletek. Mind a 13 kaszt és 35 specializáció saját, ténylegesen fogyasztott harci mechanikával fut; a 210 doctrine-választás nem puszta címke, hanem a hozzá tartozó class service viselkedését módosítja. Minden alap aktív készlet pontosan hét, a kaszt/spec által valóban feloldható képességet tartalmaz. A külön class-health réteg létezik, de a csomagolt alapbeállításban ki van kapcsolva (`health.enabled: false`).

- **Így találkozol vele:** `/class`, `/spec`, kaszt- és specializációs GUI. Parancs: /class (alias: /job, /kaszt); /spec (alias: /specializacio, /specialization). GUI: Kasztválasztó; Specializációk.
- **Kinek szól:** Játékos, Admin, Tesztelő, Eventes.
- **Mitől mozdul meg:** Választás, XP-források, szintlépés, képességfeloldás, doctrine-döntés, a spec saját producer→consumer harci ciklusa és kapcsolódó combat/craft esemény.
- **Ami még kellhet hozzá:** Nincs kötelező helyszín; resource-pack ikonok és balance-adatok tesztelendők.
- **Fontos határ:** A konkrét élő balance és több-régiós Folia viselkedés stagingben ellenőrizendő; production legacy játékosadat-migráció nincs.

A teljes, 13 kasztot és 35 specializációt kiszolgáló Profile v2 alap a kaszt/spec egyetlen
autoritatív adatmodellje és persistence-rétege. Nincs legacy player-profile migráció, PDC fallback,
dual authority vagy runtime kill switch. Hiányzó profil determinisztikus revision-0 greenfield
aggregátumként jön létre; hibás vagy owner-eltérő profil quarantine-ba kerül és fail-closed marad.
Az IceSMP a verziózárt dependency manifest alapján ellenőrzi a kötelező megjelenítési és content
stacket; eltérésnél nem aktivál félkész profilt.

Az 50. szintű záróképesség egyik specializációnál sem automatikus és nem
dekoráció: a kaszt mesterpróbája után megjelenő, az aktív specializációhoz
kötött `CAST_SPELLS` csúcspróbát kell teljesíteni. A 35 próbát a quest-rendszer
tartós teljesítése oldja fel, utána a Profile v2 capstone állapota adja a
képesség-provenanciát. Specváltás vagy respec nem hagyhat hátra idegen
képességet, mérőt, töltetet vagy cooldown-resetet.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: `icesmp.admin.job`; `icesmp.admin.spec`; quarantine recovery: `icesmp.admin.spec.recover`
- Config: `classes.*`, `class-gameplay.yml`, `spells.*`, specialization-, doctrine-, active-kit- és ability-definíciók.
- Startup dependency policy: `class-spec-rework.dependencies.enforce`; dependency lock: `class-spec-dependencies.lock.yml`. Nincs runtime rollout flag.
- Tartós állapot: ownerhez kötött Profile v2 kaszt, XP/szint, loadout, companion, Soulforge és operation receipt; explicit spell-provenance ledger.
- Reload: Balance részben reloadolható; új enum/registry-szerkezet restartot igényel.

</details>

### Talent- és képességfák

<!-- icesmp-doc-id: feature.progression.talents_skills -->

> **Aktív és játékosok számára elérhető** · A futó JAR-hoz képest: **Jelentősen megváltozott**

Pontelosztás, követelmények, attribútumhatások, skill-tree GUI és respec.

- **Így találkozol vele:** `/talent`; talent- és skill-tree GUI. Parancs: /talent (alias: /talentfa, /talents). GUI: Képességfa; Talent-fa.
- **Kinek szól:** Játékos, Admin, Tesztelő, Eventes.
- **Mitől mozdul meg:** Pontköltés, respec, belépés és attribútumfrissítés.
- **Ami még kellhet hozzá:** Nincs világépítési feladat; GUI/resource-pack megjelenést ellenőrizni kell.
- **Fontos határ:** Hibás vagy körkörös követelmény csak teljes adatvalidációval zárható ki.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: —
- Config: `classes.*`, talent- és skill-tree szekciók.
- Tartós állapot: Pontok, választások és respecállapot tartós.
- Reload: Megjelenés és balance reloadolható; fa-struktúra változásához restart/migrációteszt kell.

</details>

### Varázslatok, mastery és varázskönyv

<!-- icesmp-doc-id: feature.progression.spells -->

> **Aktív és játékosok számára elérhető** · A futó JAR-hoz képest: **Jelentősen megváltozott**

Regisztrált spellkatalógus, célzás, költség, cooldown, projectile/state kezelés, kedvencek, mastery és varázskönyv.

- **Így találkozol vele:** `/spell`, `/spellbook`, kasztparancs spellágai és Spellbook GUI. Parancs: /spell (alias: /mastery, /mesterseg, /spells); /spellbook (alias: /konyv, /sb, /varazskonyv). GUI: Varázskönyv.
- **Kinek szól:** Játékos, Admin, Tesztelő, Eventes.
- **Mitől mozdul meg:** Cast, projectile, sebzés, állapot, feloldás és GUI-művelet.
- **Ami még kellhet hozzá:** Nincs általános helyszín; aréna- és védelemkompatibilitás, VFX és resource pack tesztelendő.
- **Fontos határ:** Folia entity/régióhatár, projectile és PVP-protection viselkedését runtime kell ellenőrizni.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: Kapcsolódó/ágankénti követelmény: `icesmp.admin.job`
- Config: `spells.*`, `spell-balance.*`, VFX- és ability-beállítások.
- Tartós állapot: Unlock, kedvenc és mastery játékosonként tartós; aktív cast/state runtime.
- Reload: Balance castkor olvasható; registry/új spell struktúraváltása restartot igényel.

</details>

### Professionök, specializációk és heti célok

<!-- icesmp-doc-id: feature.progression.professions -->

> **Aktív, builder-előkészítést igényel** · A futó JAR-hoz képest: **Jelentősen megváltozott**

Nyolc profession, szakmai specializációk, XP, heti cél, gyűjtési bónusz és szakmai GUI.
A receptkatalógus 392 receptet tartalmaz, és minden recept kimondja a **fajtáját**
(gyakorló / hozam / egyedi / lánc / ritkaság); a fajta szabja meg, mit adhat a
vanília fölé, és ezt gépi kapu tartja fenn.

- **Így találkozol vele:** `/profession`, `/szakmacel`, profession GUI. Parancs: /profession (alias: /prof, /szakma); /szakmacel (alias: /weeklygoal). GUI: Szakmaválasztó.
- **Kinek szól:** Játékos, Admin, Builder, Tesztelő, Eventes.
- **Mitől mozdul meg:** Gyűjtés, craft, heti reset/cél és GUI-választás.
- **Ami még kellhet hozzá:** Szakmai alapanyagforrások, biztonságos farmok és esetleges munkaállomások ellenőrzendők.
- **Fontos határ:** WorldEdit és lootforrás-változás után a fejlődési sebességet újra kell mérni.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: Kapcsolódó/ágankénti követelmény: `icesmp.admin.profession`
- Config: `professions.*`, `profession-materials.*`, heti cél és resource beállítások.
- Tartós állapot: Profession, XP, specializáció és heti cél állapota tartós.
- Reload: Balance és receptek célzott reloadot kapnak; periódus/reset scheduler restartot igényelhet.
- Recept-fajták: minden recept `kind:` mezőt visel. `egyedi` csak funkcionális komponenssel
  (affix/enchant/attribútum/consumable/signature/potion-effects), `ritkasag` csak boss-kötött
  alapanyaggal és `amount: 1`-gyel, `gyakorlo` csak L15-ig és egyedi alapanyag nélkül.
- Eredmény-mezők: `result.potion-effects` (+ `result.potion-color`) valódi főzethatáshoz,
  `result.enchant` a bűvölőkönyvekhez — mindkettőt a `buildResult` alkalmazza.
- Craft-kapu: a recept-craft élő szakmatagságot kér (`hasProfession`), nem csak megőrzött szintet.
- Tervrajz: a lap a tartós mentés ELINDÍTÁSA ELŐTT fogy el, sikertelen mentésnél visszajár.
- A recept-craft XP-je a heti céh-célt is tölti; a tömeges munka (shift-craft, kemencéből
  kivett adag) darabonként számít a `professions.xp.bulk-event-cap` sapkáig.

</details>

### Crafting, receptek, blueprintök és katalizátorok

<!-- icesmp-doc-id: feature.progression.crafting_blueprints -->

> **Aktív, builder-előkészítést igényel** · A futó JAR-hoz képest: **Jelentősen megváltozott**

Szakmai receptkönyv, craft-korlátok, blueprint-feloldás, katalizátorvédelem és masterwork craft.

- **Így találkozol vele:** Crafting események, blueprint item, szakmai receptkönyv GUI. GUI: Szakmai receptkönyv.
- **Kinek szól:** Játékos, Admin, Builder, Tesztelő, Eventes.
- **Mitől mozdul meg:** Craft-előkészítés/befejezés, blueprint használat és item-validáció.
- **Ami még kellhet hozzá:** Craftállomások, recept-hozzávalók és resource-pack itemmodellek ellenőrzendők.
- **Fontos határ:** Tömeges vagy automatizált craft és inventory-failure útvonalakat runtime kell tesztelni.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: —
- Config: `crafting.*`, `profession-recipes.*`, `profession-materials.*`, itemdefiníciók.
- Tartós állapot: Blueprint/unlock és szakmai állapot tartós; craft tranzakció eseményalapú.
- Reload: Receptcache célzottan reloadolható; strukturális registry-váltás restartot igényelhet.

</details>

### Unique itemek, ritkaság, rúnák és signature tárgyak

<!-- icesmp-doc-id: feature.progression.items_rarity -->

> **Aktív, builder-előkészítést igényel** · A futó JAR-hoz képest: **Jelentősen megváltozott**

Az Itemization 2.0 stabil template- és példányazonosságot, hatfokú ritkaságot,
normalizált roll qualityt, 0–2 rúnahelyet, signature/set fogyasztókat és bounded,
UUID-authority crafter provenance-t ad. A `/profession forge` vanilla GUI Full
Reforge, egy authored rollra alkalmazható Stat Lock, quality-padlót emelő Amplifier,
költséglépcsőt megőrző Stability Seal, determinisztikus Ascension és veszteséges
salvage előnézetet kínál. A Forge kiválasztott socketes rúna-remove és egyetlen
WAL-műveletként végrehajtott old→new replace előnézetet is ad; a régi rúna a bundled
`destroy` policy szerint megsemmisül. Reroll, rúnacsere és ascension közben az item UUID-ja nem
változik; az ismételt reroll költséglépcsője magával az itemmel utazik.

A jelenlegi systemic survival katalógus 48 authored template, 15 registry-bekötött
Signature Effect, 3 szett, 7 explicit ascension-út és 10 valós rúna. A 392 receptből
15 gear-recept canonical `ItemTemplate → ItemInstance`; ezek szint, blueprint és
masterwork quality-padlót adhatnak, de nem változtatják meg a template stat-budgetjét.

Az ascension csak explicit stage-et definiáló template-nél működik. Az új rollérték
a régi normalizált qualityt viszi át az új authored tartományba, nem sorsol újra;
közben item level, fixed/rolled budget, követelmény, rune socket, lore/model és
Signature-fokozat változhat. A pilotban a magasabb Signature-fokozat additív,
configolt erősítést kap, nem kontrollálatlan multiplikatív szorzót.

A régi random-affix, egyrúnás, relikvia- és custom tárgyak felismerhetők maradnak.
Az ismeretlen eredetű legacy gear nem rerollolható, nem ascendelhető és nem salvage-
elhető automatikusan; piaci engedélye külön config-policy. A combat authored gear
engedélyezésekor a normál gear producer nem esik vissza új legacy random-affix gearre,
a crate parser pedig ilyen receptjutalmat elutasítja.
Az eddigi legacy egy-rúnás insert kompatibilitás megmarad, de canonical remove/replace
nem fut rajta. Külön, kétoldalú közvetlen player-trade rendszer nincs a repóban; az
ellenőrzött ItemInstance-átruházási authority ebben a körben a WAL-os market.

- **Így találkozol vele:** `/profession recipes`, `/profession forge`, mining,
  combat/world-boss loot, rúnázás, `/market`; admin: `/iceitem template <id>`.
- **Kinek szól:** Játékos, Admin, Builder, Tesztelő, Eventes.
- **Mitől mozdul meg:** Item létrehozása, craftja, rerollja, rúnázása, piaci
  escrow-ja, salvage-e és authored ascensionje.
- **Ami még kellhet hozzá:** Resource-pack `ITEM_MODEL` mappingek, lootforrások és displaynevek ellenőrzendők.
- **Fontos határ:** Meglévő, régi metadata-s itemek migrációját production mintán kell tesztelni.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: —
- Config: `itemization.*`, `item-templates.*`, `item-sets.*`, `item-rarity.*`, `dev-items.*`, crafting-, rune- és signature-item definíciók.
- Tartós állapot: A canonical példányadat, reroll count/költséglépcső, receipt-lista,
  rune/ascension/provenance az itemben van. A bounded loot-diverzitási előzmény a
  PlayerProfile v2 statisztikai szekciójában, a ritka mining napi anti-farm budgetje
  a Profile v2 economy extensionben marad reconnect és restart után is.
- Tranzakció: `item-mutation-journal.yml` exact before/after inventory snapshotot
  készít payment előtt; playerdata publish után a receiptet tartalmazó candidate és
  az elfogyasztott anyagok egy snapshotként recoveryzhetők. Insert/remove/replace ugyanazt
  a boundaryt használja; mixed rune/payment witness nem automatikusan rendeződik.
- Reload: Definíciók részben reloadolhatók; meglévő itemek frissítő listeneren vagy újrageneráláskor változnak.

</details>

### Relikviák, lelkek és soulforge

<!-- icesmp-doc-id: feature.progression.relics_souls -->

> **Aktív, builder-előkészítést igényel** · A futó JAR-hoz képest: **Jelentősen megváltozott**

Egyedi relikviák, ownership/transfer, triggerelt képességek, cooldown, soul shard/soulstone és soulforge. A `relics.passive-death.mode: keep` útvonal a passzív relikviát csak a Profile v2 halál-escrow tartós commitja után veszi ki a drop-listából; respawnkor vagy a következő belépéskor idempotens receipt alapján pontosan egyszer kézbesíti, ezért a halál és respawn közötti restart sem veszíti el. Sikertelen escrow-írásnál a tárgy a dropban marad. A generikus réteg fölött külön **Class Relic Framework** él (`relics.class-relics.*`): kaszthoz kötött, világ-egyedi relikviák Class Power / Spec Resonance / Awakening rétegekkel — a bónusz csak akkor jár, ha a Profile v2 szerinti kaszt egyezik ÉS a használható fizikai tárgy a játékosnál van (az ownership önmagában nem elég); SEALED specializáció nem rezonál; az Awakening nagy cooldownja a relickel utazik (gazdacsere/restart nem nullázza), az aktiválása atomikus és csak megtörtént lemez-commit után számít sikeresnek; a `relics.enabled: false` explicit kapu — ilyenkor Class Power, Resonance és Awakening egyaránt inaktív. Pilot: a Sárkánytojás-töredék (Evoker, +10% max Essence a `CLASS_RESOURCE_MAX` csatornán).

- **Így találkozol vele:** `/relic`, `/souls`, `/soulforge`; itemhasználat és craft. Parancs: /relic (alias: /relics, /relikvia); /soulforge (alias: /lelekkovacs); /souls (alias: /lelek, /soul).
- **Kinek szól:** Játékos, Admin, Builder, Tesztelő, Eventes.
- **Mitől mozdul meg:** Relikvia-trigger, PVP transfer, inactivity, craft és soul esemény.
- **Ami még kellhet hozzá:** Soulforge/rituálé helyszín és resource-pack itemek ellenőrzendők, ha a config fizikai helyet kér.
- **Fontos határ:** PVP transfer, megtelt inventory, escrow-íráshiba és halál–restart–join közbeni átadás runtime tesztet igényel.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: Kapcsolódó/ágankénti követelmény: `icesmp.admin.relic`; `icesmp.relic.admin`
- Config: `relics.*`, soul-, ritual-, loot- és itemdefiníciók.
- Tartós állapot: Tulajdonjog, cooldown, lelkek és forgeállapot tartós.
- Reload: Relic cache célzott reloadot kap; aktív cooldown/ownership változást stagingen kell ellenőrizni.

</details>

### Achievementek, advancementek, statisztikák és leaderboard

<!-- icesmp-doc-id: feature.progression.achievements_stats -->

> **Aktív és játékosok számára elérhető** · A futó JAR-hoz képest: **Jelentősen megváltozott**

Mérföldkövek és jutalmak, datapack advancementek, harci statisztika és ranglisták.

- **Így találkozol vele:** `/achievements`, `/stats`, `/leaderboard`; főmenü. Parancs: /achievements (alias: /ach, /eleresek); /leaderboard (alias: /lb, /rangsor, /top); /stats.
- **Kinek szól:** Játékos, Admin, Tesztelő, Eventes.
- **Mitől mozdul meg:** Játékmeneti progress, harci esemény, jutalomátvétel és lekérdezés.
- **Ami még kellhet hozzá:** Nincs kötelező helyszín; jutalmak és datapack/resource-pack betöltés ellenőrzendő.
- **Fontos határ:** Leaderboard cache és nagy adatállomány teljesítménye csak valós adatmennyiséggel mérhető.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: Kapcsolódó/ágankénti követelmény: `icesmp.admin.crate`
- Config: Achievement-, advancement-, stat- és leaderboard-definíciók.
- Tartós állapot: Progress, átvett jutalom és statisztika tartós.
- Reload: Definíciók következő kiértékeléskor használhatók; advancement JSON változás restart/reload tesztet igényel.

</details>

### Questek, napi küldetések és quest builder

<!-- icesmp-doc-id: feature.progression.quests_daily -->

> **Aktív, builder-előkészítést igényel** · A futó JAR-hoz képest: **Jelentősen megváltozott**

Adatvezérelt küldetések MMO-életciklussal (Quest Framework v2): explicit forrás-keret (NPC / Megbízások-tábla / lánc / helyszín / tárgy / esemény / auto / admin), a felvétel és a leadás KIZÁRÓLAG a jogosult forrásnál történhet; NPC-forrású questnél a feladatok teljesítése után KÉSZ állapot jön, és a leadási pontnál (alapból az adó NPC-nél) zárul a küldetés. Kategóriák (story/mellék/kaszt/specializáció/frakció/napi/heti/titok…), kaszt-, specializáció- és szintkapuk, láthatóság (a rejtett quest felfedezésig sehol nem látszik), tartós felfedezés és küldetés-követés, öt-füles napló, objective progress — köztük sikeres, engedélyezett kasztolást számláló `CAST_SPELLS` —, napi feladatok és admin/builder questkészítő.

- **Így találkozol vele:** `/quest`, `/daily`; questlog (öt fül: Aktív/Kész/Megbízások/Elérhető/Teljesített) és quest builder GUI. Parancs: /quest (alias: /kuldetes, /quests); /daily. GUI: Küldetésnapló; Quest builder.
- **Kinek szól:** Játékos, Admin, Builder, Eventes, Tesztelő.
- **Mitől mozdul meg:** Objective események, hitelesített NPC-interakció (leadás > beszélgetés/szállítás > kínálat prioritással, több questnél tokenes választólistával), napló-megbízás elvállalása, lánc-feloldás és admin szerkesztés.
- **Ami még kellhet hozzá:** Questhelyszínek, NPC-kötések, biztonságos célterületek és jutalomoverflow tesztelendő.
- **Fontos határ:** Az élő NPC-k és világhelyek hiányában csak capability-szintű következtetés adható.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: Kapcsolódó/ágankénti követelmény: `icesmp.admin.quest` (a `/quest accept` és `/quest talk` is admin-parancs — a forrás-authority parancsból nem kerülhető meg).
- Config: `quests.*` (start/turn-in/category/visibility séma), napi küldetés-, NPC- és rewarddefiníciók.
- Tartós állapot: Aktív quest, objective progress, forrás-audit, felfedezés, követett quest, napi állapot és builder által mentett definíció tartós (PlayerProfile QuestSection az egyetlen player-authority).
- Reload: a quest-registry csere atomikus és teljes gráf-validációval kapuzott — érvénytelen candidate a korábbi definíciókat hagyja élőben; az admin-szerkesztő ugyanezen a validátoron megy át mentés előtt.

</details>

### Krónika, emlékek és aktív történeti mechanikák

<!-- icesmp-doc-id: feature.progression.story_lore -->

> **Aktív, builder-előkészítést igényel** · A futó JAR-hoz képest: **Jelentősen megváltozott**

A forrásban ténylegesen bekötött krónika, emlék, lore-parancs, párbeszéd és tábortűzi történet; nem azonos a teljes tervezett lore-ral.

- **Így találkozol vele:** `/kronika`, `/emlek`, `/lore`; dialógus- és történeti triggerek. Parancs: /emlek (alias: /emlekek, /memory); /kronika (alias: /chronicle); /lore (alias: /kodex).
- **Kinek szól:** Játékos, Builder, Eventes, Tesztelő.
- **Mitől mozdul meg:** Felfedezés, konfigurált történeti esemény, illetve sikeres leülés egy `szék → 1 üres blokk → égő campfire` elrendezésben; a campfire közvetlen kattintása nem trigger.
- **Ami még kellhet hozzá:** Történeti helyszínek, NPC-k és aktiváló blokkok/területek előkészítendők.
- **Fontos határ:** Csak a regisztrált forrás- és resource-tartalom aktív; a LORE.md/TEASER.md önmagában nem implementáció.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: —
- Config: Story-, chronicle-, memory-, dialogue-, quest- és message-definíciók.
- Tartós állapot: Felfedezett emlékek és krónikaállapot tartós.
- Reload: Szövegek reloadolhatók; aktív folyamat és helyszíncsere külön tesztelendő.

</details>

### Társak és befogás

<!-- icesmp-doc-id: feature.progression.pets -->

> **Aktív és játékosok számára elérhető** · A futó JAR-hoz képest: **Jelentősen megváltozott**

Pet/társ kezelés, befogás, XP, harci viselkedés, parancsok és társ-GUI.

- **Így találkozol vele:** `/pet`; Pet GUI; befogó item és játékosparancs. Parancs: /pet (alias: /companion, /tars). GUI: Társ GUI.
- **Kinek szól:** Játékos, Admin, Tesztelő, Eventes.
- **Mitől mozdul meg:** Befogás, társlistás kiválasztás, pet parancs, combat, gazda- vagy pet-killből érkező XP és GUI-művelet.
- **Ami még kellhet hozzá:** Nincs kötelező helyszín; az idézés betöltött, Folia-lokális oszlopokban stabil talajt és három blokk szabad testteret keres.
- **Fontos határ:** Entity lifecycle, chunk unload és protection plugin interakció runtime tesztet igényel.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: —
- Config: `pets.*`, benne az Istálló-kapacitás és a biztonságos spawn keresési tartománya; capture-item és kapcsolódó message/itemdefiníciók.
- Tartós állapot: Tulajdonjog, petadat és XP tartós.
- Reload: Balance reloadolható részei következő eseménynél érvényesek; aktív pet entityk újraszinkronizálása kellhet.

</details>

### Party és közös jutalommegosztás

<!-- icesmp-doc-id: feature.progression.party -->

> **Aktív és játékosok számára elérhető** · A futó JAR-hoz képest: **Jelentősen megváltozott**

Party létrehozás/kezelés, tagság, eseményfigyelés és közös jutalomfeloldás.

- **Így találkozol vele:** `/party`; főmenü party nézete. Parancs: /party (alias: /p, /parti).
- **Kinek szól:** Játékos, Admin, Tesztelő.
- **Mitől mozdul meg:** Partyparancs, join/leave, jutalom és játékos-lifecycle esemény.
- **Ami még kellhet hozzá:** Nincs builderfeladat.
- **Fontos határ:** Quit/reconnect és régiók közötti rewardmegosztás Folia runtime tesztet igényel.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: —
- Config: Party- és rewardbeállítások.
- Tartós állapot: Partyállapot a konfigurált store szerint tartós; ideiglenes invite sessionjellegű lehet.
- Reload: Policy reloadolható; aktív meghívások és partyváltozás tesztelendő.

</details>

## Gazdaság és jutalmak

A négy veret nem puszta pontszám. Kézről kézre jár, bankba kerül, ládakulcsot vesz, piaci alkut indít — és közben a világ háborúi, hiányai és bősége is nyomot hagy az értékén.

### Valuta, bank és átváltás

<!-- icesmp-doc-id: feature.economy.currency_bank -->

> **Tesztelési vagy rollout-kapu alatt** · A futó JAR-hoz képest: **Jelentősen megváltozott**

Több valuta, fizikai valutaitem, egyenleg, fizetés, admin set, bankbetét/kivét és árfolyam.

- **Így találkozol vele:** `/currency`, `/bank`; főmenü bank/átváltó nézete. Parancs: /bank (alias: /vault, /wallet); /currency (alias: /eco, /money).
- **Kinek szól:** Játékos, Admin, Tesztelő, Fejlesztő/üzemeltető.
- **Mitől mozdul meg:** Parancs, craft, loot, jutalom és gazdasági tranzakció.
- **Ami még kellhet hozzá:** Nincs kötelező helyszín; shopok és jutalomforrások egyenlegét felül kell vizsgálni.
- **Fontos határ:** Dupla költés, tárolási hiba és inventory-overflow csak fault-injection teszttel zárható ki.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: Kapcsolódó/ágankénti követelmény: `icesmp.admin.currency`; `icesmp.currency.admin`
- Config: `economy.*`, currency-, bank- és exchange-rate definíciók.
- Tartós állapot: Wallet/bankegyenleg és tranzakciós állapot tartós.
- Reload: Árfolyam és limitek reloadolhatók; félbeszakított írásnál recovery szükséges.

</details>

### Piac, boltok, adományláda és exchange board

<!-- icesmp-doc-id: feature.economy.market_shops -->

> **Aktív, builder-előkészítést igényel** · A futó JAR-hoz képest: **Jelentősen megváltozott**

Játékospiac, NPC/frakció shop, vevőszolgáltatás, kézbesítés, adományláda és gazdasági hirdetőtábla.

- **Így találkozol vele:** `/market`, `/adomany`, `/exchangeboard`; Market, Shop és Donation GUI. Parancs: /adomany (alias: /adomanylada, /donate); /exchangeboard (alias: /arfolyamtabla, /ratesboard); /market (alias: /ah, /piac). GUI: Adományláda; NPC/frakció bolt; Piactér.
- **Kinek szól:** Játékos, Admin, Builder, Tesztelő, Eventes.
- **Mitől mozdul meg:** Listing/vásárlás/kézbesítés, NPC-interakció, adomány és board művelet.
- **Ami még kellhet hozzá:** Shop NPC-k, adományláda-hely és esetleges board-helyszínek kötése szükséges.
- **Fontos határ:** Az adomány csak tartós előkészítés és region-szálas forráslevonás után jelenik meg; félbeszakított beadás vagy átvétel belépéskor automatikusan folytatódik vagy visszagörög.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: Kapcsolódó/ágankénti követelmény: `icesmp.admin.exchangeboard`
- Config: `economy.*`, market-, shop-, donation- és exchange-board definíciók.
- Tartós állapot: Listingek, journal, kézbesítés és gazdasági állapot tartós; az adományláda prepare/available/claim fázisai atomikus YAML-snapshotban élnek.
- Reload: Árak/listák részben reloadolhatók; nyitott tranzakciónál settlement kell.

</details>

### Natív crate-rendszer

<!-- icesmp-doc-id: feature.economy.crates -->

> **Tesztelési vagy rollout-kapu alatt** · A futó JAR-hoz képest: **Új**

Nyolc permission nélküli alapláda, fizikai crate-helyek, kulcsvásárlás/-felhasználás, valós itemmodelleket mutató browser/preview GUI, világban futó ItemDisplay-reveal, tematikus unique/craftolt/affixes és szűrt random-tervrajz jutalmak, audit, settlement és recovery. A crate parser közvetlen itemként, profession-receptként és tervrajz-poolon keresztül is kizárja az Elytrát; repülőszárny kizárólag a relikviarendszerből létezhet.

- **Így találkozol vele:** `/crate`; crate blokk, browser/preview GUI és a láda felett futó ItemDisplay-reveal. Parancs: /crate (alias: /crates, /ladak). A nyitás nem nyit inventory-rulettet.
- **Kinek szól:** Játékos, Admin, Builder, Tesztelő, Fejlesztő/üzemeltető.
- **Mitől mozdul meg:** Blokkinterakció, kulcshasználat, GUI-kattintás, settlement/recovery és adminparancs.
- **Ami még kellhet hozzá:** Minden crate-hez világ, blokk és hely szükséges; cserét/törlést kontrollált adminfolyamattal kell végezni.
- **Fontos határ:** CrazyCrates csak main/off-hand, mass-open, reward failure, restart és MANUAL_REVIEW fault-injection után távolítható el.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: Kapcsolódó/ágankénti követelmény: `icesmp.admin.crate`; `icesmp.crate.use`. A nyolc bundled láda permissionje üres; az opcionális `icesmp.*` crate-specifikus gate támogatott marad.
- Config: `crates.*`, crate location/world policy, reward- és auditbeállítások.
- Tartós állapot: Crate-, opening-, ledger-, audit- és recovery-állapot tartós.
- Reload: Definíciók generációváltással reloadolhatók; futó opening a saját snapshotján fejeződik be.

</details>

### Napi jutalmak, bounty és jutalomforrások

<!-- icesmp-doc-id: feature.economy.rewards_bounty -->

> **Aktív, configgal engedélyezhető** · A futó JAR-hoz képest: **Jelentősen megváltozott**

Napi átvétel, bounty, pénzeszsák, mobpénz, reward-budget és party-kompatibilis jutalomfeloldás.

- **Így találkozol vele:** `/daily`, `/bounty`; loot-, kill- és event-triggerek. Parancs: /bounty (alias: /fejvadasz, /korozes); /daily (alias: /napi).
- **Kinek szól:** Játékos, Admin, Eventes, Tesztelő.
- **Mitől mozdul meg:** Napi ciklus, kill, bounty teljesítés, itemhasználat és reward trigger.
- **Ami még kellhet hozzá:** Bounty/event célpontok és jutalomforrások biztonságát ellenőrizni kell.
- **Fontos határ:** AFK-blokkolás, full inventory és economy-storage hiba esetén runtime teszt szükséges.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: —
- Config: Daily-, bounty-, loot-, money-pouch- és rewardbeállítások.
- Tartós állapot: Napi átvétel, bounty és egyes budgetállapotok tartósak.
- Reload: Jutalomtáblák reloadolhatók; periodikus reset task restarthoz kötött lehet.

</details>

## Frakciók, politika és terület

A zászló szövetségest, törvényt és ellenséget is jelent. A játékosok királyt választhatnak, tanácsot emelhetnek, földet védhetnek és háborút indíthatnak; a politika nem díszlet, hanem közös játéktér.

### Frakciótagság, viszonyok és frakciópasszívok

<!-- icesmp-doc-id: feature.factions.membership_relations -->

> **Aktív, builder-előkészítést igényel** · A futó JAR-hoz képest: **Jelentősen megváltozott**

Belépés/kilépés/váltás, frakciókapcsolatok, étel/passzív/spawn hatások és frakcióspecifikus játékmenet. A frakciórekord nélküli új játékos a Menedék **vendége**, nem automatikus `NEUTRAL` polgár: frakcióelőny csak kifejezett választás után jár.

- **Így találkozol vele:** `/faction`; főmenü frakciónézete. Parancs: /faction (alias: /f).
- **Kinek szól:** Játékos, Admin, Builder, Tesztelő, Eventes.
- **Mitől mozdul meg:** Tagságváltás, join/quit, combat, fogyasztás, spawn és passzív esemény.
- **Passzív defaultok:** RED környezeti hőnél `0.25/0.25/0.50/0.25` megtartott FIRE/FIRE_TICK/LAVA/HOT_FLOOR sebzés, entitás-tűznél `0.75`; a `TUZ` spelliskola változatlan. BLUE: fagyás `0`, fulladás `0.50`, a konfigurált természetes exhaustion okoknál `25%` megtakarítás. NEUTRAL: zuhanás `0.50`, csak spontán békés/semleges aggró és Enderman-szemkontaktus szűrhető. DARK: Wither sebzés/idő `0.50/0.50`, markerelt ambient undead-béke `60 s` megtorlással és `16` blokkos riadóval, vad undeadnél éjszakai `50%` target-cancel.
- **Harci precedencia:** admin/scriptelt célzás → markerelt boss/dungeon/rontás/invázió/event/quest → koronaátok → provokáció/megtorlás → Vérhold → ambient polgárjog → vad passzív → vanilla. Vérhold alatt az ambient és a vad DARK truce alapból egyaránt megszűnik. A passzív nem támadhatatlanság.
- **Jogosultság és tartósság:** a signature-food buff fogyasztáskor élő tagságot kér; a frakcióváltás assignment+history snapshotot és wallet-WAL-t használ. Sikertelen tartós írás nem publikál sikeres váltást és nem indít lifecycle jutalmat.
- **Ami még kellhet hozzá:** Frakcióspawnokat, védett területeket és váltási feltételeket elő kell készíteni.
- **Fontos határ:** Az automatizált policy- és regressziós tesztek nem bizonyítják a productionközeli mob-AI-t, többjátékos viselkedést vagy szezonbalanszt; ehhez az admin acceptance mátrix szerinti staging playtest kell.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: Kapcsolódó/ágankénti követelmény: `icesmp.admin.faction`; `icesmp.admin.war`; `icesmp.faction.admin`; `király`; `király vagy icesmp.admin.faction`; `király vagy tanácstag`
- Config: `factions.*`, különösen `factions.passives.*`, továbbá relation-, food- és spawn-definíciók.
- Tartós állapot: Az explicit tagság és utolsó választás egy durable generáció; fizetős váltás exact wallet/membership WAL-lal recoveryzhető. A vendégállapot assignment hiánya. A provokációs/truce-state játékos–mob páronként mulandó és lifecycle cleanupot kap.
- Reload: Minden frakciópasszív gameplay-érték ugyanabból az atomikusan publikált config-generationből frissül; restart nem kell. Ez nem helyettesíti az aktív combat alatti staging reloadtesztet.

</details>

### Céhek, királyság, tanács és frakciókassza

<!-- icesmp-doc-id: feature.factions.guilds_leadership -->

> **Aktív, builder-előkészítést igényel** · A futó JAR-hoz képest: **Jelentősen megváltozott**

Céhkezelés, vezetői/királyi műveletek, tanács, treasury és közösségi politikai állapot.

- **Így találkozol vele:** `/ceh`, `/tanacs`, `/faction king|treasury`; frakciómenü. Parancs: /ceh (alias: /gild, /guild); /tanacs (alias: /council); /faction king|treasury.
- **Kinek szól:** Játékos, Admin, Eventes, Tesztelő.
- **Mitől mozdul meg:** Parancs, választás/vezetői művelet, treasury tranzakció és event.
- **Ami még kellhet hozzá:** Tanács-/trónhelyszín és szerepjátékos folyamat előkészítése ajánlott.
- **Fontos határ:** A politikai játékmenet élő szerverfolyamata és eventes szabályai nem vezethetők le pusztán a kódból.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: Kapcsolódó/ágankénti követelmény: `icesmp.admin.faction`
- Config: `factions.*`, guild-, king-, council- és treasury-definíciók.
- Tartós állapot: Céh, vezetés és tanács tartós; a treasury, az eredet-frakciós adósság és a walletet is érintő adóbeszedés write-ahead journallal, startup recoveryvel és fail-closed kritikus írási körrel védett. Ismeretlen eredetű fejlesztői legacy adósság karanténban marad, és nem kötődik automatikusan későbbi frakcióhoz.
- Reload: Policy reloadolható; vezetői állapotváltozás staging- és permissiontesztet igényel.

</details>

### Claim, territory és védelem

<!-- icesmp-doc-id: feature.factions.land_claims -->

> **Aktív, builder-előkészítést igényel** · A futó JAR-hoz képest: **Jelentősen megváltozott**

Területfoglalás, trust, world/régióvédelem, territory típusok és admin törlés/kiválasztó eszköz.

- **Így találkozol vele:** `/claim`, `/territory`; ClaimTrust GUI és selection wand. A `/territory setcapital <frakció> selection [név...]` a claim két sarokpontjából pontos X/Y/Z fővárost hoz létre; a régi sugaras forma változatlan. Parancs: /claim (alias: /birtok); /territory (alias: /terulet). GUI: Megbízottak kezelése.
- **Kinek szól:** Játékos, Admin, Builder, Tesztelő.
- **Mitől mozdul meg:** Blokk-interakció, break/place, PvP/használat, claimparancs és GUI.
- **Ami még kellhet hozzá:** Világpolicy, régióhatárok, spawnok és WorldEdit utáni audit szükséges.
- **Fontos határ:** A külső protection plugin és az élő worldlisták nélkül a tényleges konfliktusmátrix nem bizonyítható.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: Kapcsolódó/ágankénti követelmény: `icesmp.admin.territory`; `icesmp.admin.territory.bypass`; `icesmp.territory.builder`
- Config: `factions.*`, claim-, territory-, world- és protection-beállítások.
- Tartós állapot: Claim, trust és territory állapot tartós.
- Reload: Policy reloadolható; világátnevezés vagy régiótípus-váltás migrációt igényel.

</details>

### Háború, raid, kémkedés és ostrom

<!-- icesmp-doc-id: feature.factions.conflict_espionage -->

> **Aktív, builder-előkészítést igényel** · A futó JAR-hoz képest: **Jelentősen megváltozott**

Háborús ablak, raid, kémálca/felfedés, lopás, caravan konfliktus és ostromfegyverek.

- **Így találkozol vele:** `/faction raid|war|caravan`, `/kem`, `/parbaj`; item- és eventtriggerek. Parancs: /kem (alias: /spy); /faction raid|war|caravan; /parbaj.
- **Kinek szól:** Játékos, Admin, Builder, Eventes, Tesztelő.
- **Mitől mozdul meg:** War window, raid, PvP, reveal/theft, siege item és caravan esemény.
- **Ami még kellhet hozzá:** Raid/ostrom területeket, útvonalakat és védelmi kivételeket elő kell készíteni.
- **Fontos határ:** Exploit-, dupe-, offline-raid és protection-interakció teljes playtestet igényel.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: Kapcsolódó/ágankénti követelmény: `icesmp.admin.territory.bypass`; `icesmp.admin.war`
- Config: `factions.*`, raid-, war-, spy-, siege- és caravan-definíciók.
- Tartós állapot: Raid-, war-, kém- és guild/faction állapot tartós lehet; aktív combat runtime.
- Reload: Policy reloadolható; folyamatban lévő raid/war saját snapshotot igényel.

</details>

## Világ, események és interakció

A megsebzett világ jelekben beszél: vörös holdban, egy távoli horda zajában, a karaván porfelhőjében vagy egy frissen nyílt rontás-gócban. Ezek a rendszerek teszik változóvá azt, ami más szerveren csak statikus térkép lenne.

### Világesemények és bossok

<!-- icesmp-doc-id: feature.world.events -->

> **Aktív, builder-előkészítést igényel** · A futó JAR-hoz képest: **Jelentősen megváltozott**

Blood Moon, world boss, Wild Hunt, invasion, meteor, caravan, escort, abundance, treasure és szerverchallenge.

A közös spawnkereső az automatikus jelölteket chunk-középre igazítja, a
footprint- és partvizsgálatot pedig legfeljebb 7 blokkos, egy Folia-régión
belüli körre korlátozza. Így egy jelölt csak egy chunkot fogyaszt a keresési
keretből; az escort útvonala és az invázió külső hulláma saját, egyoszlopos
belső profilt használ. Adminindításkor a parancs először csak a keresés
elindulását igazolja, tényleges sikert az esemény spawn utáni broadcastja jelez.

- **Így találkozol vele:** `/events`; admin eventindítók és automatikus eseménytriggerek. Parancs: /events (alias: /esemeny, /event).
- **Kinek szól:** Játékos, Admin, Builder, Eventes, Tesztelő.
- **Mitől mozdul meg:** Ütemezett/valószínűségi trigger, adminindítás, spawnpont és lifecycle esemény.
- **Ami még kellhet hozzá:** Spawnpontok, útvonalak, bossarénák, biztonságos visszaállítás és régiók szükségesek.
- **Fontos határ:** Egyidejű esemény, chunk unload, restart és cleanup production runtime tesztet igényel.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: Kapcsolódó/ágankénti követelmény: `icesmp.admin.events`
- Config: `world.*`, `world-events.water-safety.*`, `world-events.profiles.*`,
  valamint event-, boss-, mob-, loot- és season-definíciók.
- Tartós állapot: Event-, spawnpont-, raid- és finaleállapot részben tartós.
- Reload: Definíciók reloadolhatók részenként; aktív esemény saját state-jét nem szabad félúton lecserélni.

</details>

### Season 0 / Prologue — Olethropyla

<!-- icesmp-doc-id: feature.world.prologue -->

> **Aktív kód, kézi élesítést és builder-előkészítést igényel** · A futó JAR-hoz képest: **Új rendszer**

Az egyszeri Prologue lifecycle Season 0 progression- és contentkapukat, Gate
Breach/finale eseményt, Olethropyla Nether-authorityt, résztvevő-skálázást,
prestige státuszokat és a Season 1 idempotens átmenetét kezeli.

- **Így találkozol vele:** Élesítés után a Kárhozat Kapujának HUD-ja, áttörései és fináléja; admin live-ops: `/prologue`.
- **Kinek szól:** Játékos, Admin, Builder, Eventes, Tesztelő.
- **Mitől mozdul meg:** Kizárólag a `/prologue start` vagy más, állapotot ténylegesen módosító adminfolyamat után; a friss `DORMANT` állapot teljes pass-through.
- **Ami még kellhet hozzá:** A négy `prologue-*` runtime hook és a productionközeli staging acceptance.
- **Fontos határ:** `DORMANT` alatt nincs Prologue XP-/contentkorlát, season/community override, Nether-pecsét vagy gate-location authority, HUD/ambient/breach és catch-up; a Prologue-tól független szerverconfig változatlanul érvényes.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: `icesmp.admin.prologue`
- Config: `world-events.prologue.*`, valamint a kapcsolódó spawn/profile/anchor utak.
- Tartós állapot: `prologue.yml`, finale run state és Season 1 transition receipt.
- Reload: A tartalmi overlay csak aktív Prologue alatt él; `DORMANT`/lezárás/tiltás esetén a konfigurált értékek visszaállnak.

</details>

### Évszakok, közösségi célok és ambient események

<!-- icesmp-doc-id: feature.world.seasons_ambient -->

> **Aktív, builder-előkészítést igényel** · A futó JAR-hoz képest: **Jelentősen megváltozott**

Szezonállapot, jutalmak, történetmesélés, finálé, monumentum, holiday, ambient és gazdasági módosítók.

- **Így találkozol vele:** `/events`; automatikus szezon- és ambient folyamat.
- **Kinek szól:** Játékos, Admin, Builder, Eventes, Tesztelő.
- **Mitől mozdul meg:** Idő/ciklus, közösségi progress, holiday, world állapot és admin trigger.
- **Ami még kellhet hozzá:** Szezonmonumentum, fináléhelyszín és kapcsolódó NPC/eventpontok előkészítendők.
- **Fontos határ:** Időzóna, szerveróra, restart és párhuzamos eventhatás runtime ellenőrzést igényel.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: —
- Config: `world.*`, season-, holiday-, community-goal- és ambient-definíciók.
- Tartós állapot: Szezon, jutalom, közösségi cél, monumentum és finálé állapota tartós.
- Reload: Szöveg/balance reloadolható; szezonváltás és scheduler újraindítása kontrollált folyamat.

</details>

### Mobok, skálázás, loot és bestiárium

<!-- icesmp-doc-id: feature.world.mobs_loot -->

> **Aktív, builder-előkészítést igényel** · A futó JAR-hoz képest: **Jelentősen megváltozott**

Mobskálázás, loot table, dungeon/mob jutalom, minionvédelem, bestiárium és undead
segédszabályok. A combat gear-sor releváns forrásnál authored Itemization 2.0
template-et választ: level/class/spec/build/üres slot/forrás legfeljebb 1,5× súlyt
adhat, az utolsó legfeljebb 32 drop pedig csak enyhe, nem garantált diverzitási
korrekciót végez. A más kasztnak való, kereskedhető tárgyak súlya pozitív marad.

A Mob/Encounter 2.0 normál survival progressionje Lv. 1–50, a földrajzi/event
bónuszokkal elérhető általános hard cap Lv. 70. A mob szintjét rétegesen az explicit
encounter vagy authored hely, a MobTemplate, majd a territory/biome/mélység/távolság
és event állapot határozza meg. A HP gyorsabb, a damage lassabb, külön bounded görbén
nő; 70 fölötti boss csak authored override, nem wilderness extrapoláció.

A rendszer 18 canonical MobTemplate-et használ surface/night/deep/swamp/Nether/End/
storm és meglévő event forrásokra, miközben a többi vanilla mob fallbackként
helyes marad. A hét rank: Normal, Veteran, Elite, Champion, Miniboss, Boss és World
Boss; a 12 archetype közös ability registryre épül. Hat reusable ability vanilla
telegráfot ad, az Elite spawnkor legfeljebb két biztonságos affixet kaphat a hétből.
Az invasion hullámai/bajnoka, a kultisták és a Wild Hunt canonical rankot kapnak.

A világboss startkor stabil, csökkenő hozadékú player-count és élő equipped-CombatPower
snapshotot készít. A power csak valid main/offhand+armor canonical itemek tényleges
stat/rúna/Signature/szett kontextusa; nem publikus Gear Score. Late
join után contribution gyűjthető, de a HP nem ugrál. A bounded ledger sebzést,
tankolást, Monk/Paladin ally-healt és shieldet, valamint telegráf-kitérés objective-et
támogat; self-paddinget,
pre-combat farmot és dupla settlementet elutasít. Az érdemi résztvevő személyes,
PlayerProfile receipt-alapú ascension komponenst kap; tele inventorynál a jutalom
reconnectig függőben marad, nem esik a földre.

- **Így találkozol vele:** `/bestiarium`; automatikus spawn/kill/loot események. Parancs: /bestiarium (alias: /bestiary, /lajstrom). GUI: Bestiárium (kattintható kategória-főoldal + lapozható lajstrom: ismert bejegyzések ikonnal, ismeretlenek „???" sziluettként, teljesítmény-%-kal). A szörny-bejegyzések faj-szintű mélységet kapnak: elejtés-számláló, első-elejtés dátum és kill-alapú tudás-fokozatok (kódex-jegyzet → zsákmány-jegyzet → mestervadász), a világbossok archetípusonként (nem vanilla-fajonként) kerülnek a lajstromba. Külső kijelzéshez: `%icesmp_bestiary_<kategória>%` és `_total` placeholderek.
- **Kinek szól:** Játékos, Admin, Builder, Eventes, Tesztelő.
- **Mitől mozdul meg:** Mob spawn, sebzés, ölés, loot, bestiárium-felfedezés és minion lifecycle.
- **Ami még kellhet hozzá:** Mobspawnokat, arénákat, telegráf-láthatóságot,
  farmvédelmet, 50–60 fős encounter terhelést és lootforrásokat stagingen ellenőrizni kell.
- **Fontos határ:** A forrásregresszió nem bizonyít production Folia region-hopot,
  harcérzetet vagy vanilla/custom mob és más lootplugin együttműködést.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: —
- Config: `world.yml` `mob-scaling.*` és `world-events.world-boss.*`,
  `mob-templates.yml`, `loot.*`, `itemization.loot.*`, bestiary- (mérföldkövek,
  `bestiary.knowledge-tiers`, `bestiary.codex-notes.*`) és miniondefiníciók.
- Tartós állapot: Bestiárium progress, bounded authored-loot előzmény és egyes
  loot/event state-ek tartósak; mob/ability/ledger entity runtime. A személyes boss
  reward eligibility/delivery receipt a PlayerProfile v2 `OPERATIONS` szekcióban él.
- Reload: Loot/balance reloadolható; már spawnolt mobok nem feltétlenül változnak visszamenőleg.

</details>

### Dungeon kapuk és dungeon loot

<!-- icesmp-doc-id: feature.world.dungeons -->

> **Aktív, builder-előkészítést igényel** · A futó JAR-hoz képest: **Jelentősen megváltozott**

Dungeonbelépési feltételek, kapuvédelem, lootkiosztás és restartbiztos lootállapot.

- **Így találkozol vele:** Világbeli dungeon gate és lootinterakció.
- **Kinek szól:** Játékos, Admin, Builder, Eventes, Tesztelő.
- **Mitől mozdul meg:** Belépés, blokk/container interakció, loot és lifecycle.
- **Ami még kellhet hozzá:** Dungeonvilág, kapuk, lootkonténerek, protection és recovery útvonalak előkészítendők.
- **Fontos határ:** A tényleges világartifact hiányában a helyszínkészség nem bizonyítható.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: —
- Config: `world.*`, dungeon- és lootdefiníciók.
- Tartós állapot: Dungeon-loot state tartós; gate runtime policy.
- Reload: Definíció reloadolható lehet; aktív dungeon és lootjournal miatt restartteszt kell.

</details>

### Teleport, komp, NPC-kötés és interakció

<!-- icesmp-doc-id: feature.world.travel_npc -->

> **Aktív, builder-előkészítést igényel** · A futó JAR-hoz képest: **Jelentősen megváltozott**

Komp/utazás, NPC-binding, Stranger NPC és FancyNpcs-quest/shop kapcsolatok.

- **Így találkozol vele:** `/komp`, `/npcbind`; NPC- és világbeli interakció. Parancs: /komp (alias: /ferry); /npcbind (alias: /npckotes).
- **Kinek szól:** Játékos, Admin, Builder, Eventes, Tesztelő.
- **Mitől mozdul meg:** NPC click/dialog, teleport/komp és binding adminművelet.
- **Ami még kellhet hozzá:** Biztonságos célpontok, NPC-k, kompállomások és világnevek előkészítendők.
- **Fontos határ:** A külső FancyNpcs és élő világ nélkül csak capability bizonyítható.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: Kapcsolódó/ágankénti követelmény: `icesmp.admin.npc`
- Config: `world.*`, NPC-, ferry-, teleport-, shop- és questdefiníciók.
- Tartós állapot: NPC-kötések és egyes utazási állapotok tartósak.
- Reload: Célpontok reloadolhatók; világátnevezés vagy NPC-ID csere migrációt igényel.

</details>

### Parkour, archeológia és felfedezés

<!-- icesmp-doc-id: feature.world.parkour_discovery -->

> **Aktív, builder-előkészítést igényel** · A futó JAR-hoz képest: **Jelentősen megváltozott**

Parkourpályák, checkpoint/progress, archeológiai megosztás és rejtett helyek felfedezése.

- **Így találkozol vele:** `/parkour`; világinterakció és felfedezési trigger. Parancs: /parkour (alias: /palya, /trial).
- **Kinek szól:** Játékos, Admin, Builder, Eventes, Tesztelő.
- **Mitől mozdul meg:** Pályakezdés/checkpoint/cél, archeológia és hidden-spot belépés.
- **Ami még kellhet hozzá:** Pályákat, checkpointokat, biztonságos visszarakást és rejtett pontokat létre kell hozni.
- **Fontos határ:** A pályák fizikai megléte az élő világ nélkül nem bizonyítható.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: Kapcsolódó/ágankénti követelmény: `icesmp.admin.parkour`
- Config: `world.*`, parkour-, archeology- és hidden-spot definíciók.
- Tartós állapot: Parkour és felfedezési progress tartós lehet; futó pálya sessionállapot.
- Reload: Definíció reloadolható; aktív futam és WorldEdit utáni helyellenőrzés kell.

</details>

### Natív sit-only rendszer

<!-- icesmp-doc-id: feature.world.sit_only -->

> **Tesztelési vagy rollout-kapu alatt** · A futó JAR-hoz képest: **Új**

Ülés támogatott lépcsőn, alsó/felső slabon, carpet/moss carpet/pale moss carpet és snow geometrián; a marker a tényleges ülőfelületre, alsó lépcsőn az irány és a stair-shape szerinti treadre kerül, foglalás- és lifecycle-cleanuppal.

- **Így találkozol vele:** `/sit [fel]`; jobb kattintás, ha a policy engedi.
- **Kinek szól:** Játékos, Admin, Builder, Tesztelő.
- **Mitől mozdul meg:** Interakció, sneak, damage, break, teleport, world change, quit/kick/dismount, reload/disable.
- **Ami még kellhet hozzá:** Ülőblokkok geometriáját, supportot, folyadékot, clearance-t és világlistát ellenőrizni kell.
- **Fontos határ:** Lay, crawl, stacking, player/NPC sitting nem támogatott; GSit csak sit átvételi teszt után távolítható el.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: Kapcsolódó/ágankénti követelmény: `icesmp.sit`
- Config: `sit.*` világ- és material policy.
- Tartós állapot: Nincs tartós ülés; seat entity és foglalás sessionállapot.
- Reload: Policy reloadolható, aktív ülések cleanupja kötelező.

</details>

### Világszabályok, crop protection és biztonsági policy

<!-- icesmp-doc-id: feature.world.rules_protection -->

> **Tesztelési vagy rollout-kapu alatt** · A futó JAR-hoz képest: **Új**

World game rule enforcement, portal guard, player/mob farmland-trample védelem, blokk-visszaállítás és általános protection bridge.

- **Így találkozol vele:** Automatikus listenerek és admin config.
- **Kinek szól:** Játékos, Admin, Builder, Tesztelő.
- **Mitől mozdul meg:** Világbetöltés, blokk-, portal-, trample- és protection esemény.
- **Ami még kellhet hozzá:** Világ whitelist/blacklist, protected régiók és block-regeneration policy ellenőrzendő.
- **Fontos határ:** FarmProtect/ICEsmpadditions csak player/mob trample és Warden XP playtest után távolítható el.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: Kapcsolódó/ágankénti követelmény: `icesmp.admin.territory`; `icesmp.admin.territory.bypass`
- Config: `world.*`, game-rule-, crop-protection-, portal- és block-regen beállítások.
- Tartós állapot: Blokk-regeneráció journal tartós; a legtöbb policy runtime.
- Reload: Policy reloadolható; már ütemezett regen és világszabály-frissítés restartot igényelhet.

</details>

### Combat tag, párbaj és harci szabályok

<!-- icesmp-doc-id: feature.world.combat_rules -->

> **Aktív, configgal engedélyezhető** · A futó JAR-hoz képest: **Jelentősen megváltozott**

Combat tag, becsületpárbaj, resource combat, regen- és sebzéssegédek, valamint állapotcleanup.

- **Így találkozol vele:** `/parbaj`; automatikus combat listenerek. Parancs: /parbaj (alias: /duel).
- **Kinek szól:** Játékos, Admin, Tesztelő.
- **Mitől mozdul meg:** Sebzés, kilépés, death, duel accept/end és resource conflict.
- **Ami még kellhet hozzá:** Párbajterület és biztonságos visszarakás ellenőrzendő, ha a config helyhez köti.
- **Fontos határ:** Folia régióhatár, teleport, logout és protection plugin konfliktus runtime tesztet igényel.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: —
- Config: Combat-, duel-, health- és world policy.
- Tartós állapot: Párbaj/statisztika részben tartós; combat tag sessionállapot.
- Reload: Balance reloadolható; aktív combat sessiont nem feltétlenül írja át.

</details>

### Korrupció, bűn és cursed hatások

<!-- icesmp-doc-id: feature.world.corruption_sin -->

> **Aktív, configgal engedélyezhető** · A futó JAR-hoz képest: **Jelentősen megváltozott**

Korrupciós aura/állapot, sinner parancs, crown curse és cursed gear viselkedés.

- **Így találkozol vele:** `/sinner`; automatikus aura, gear és story/event triggerek.
- **Kinek szól:** Játékos, Admin, Eventes, Tesztelő.
- **Mitől mozdul meg:** Combat, itemviselés, terület/event és periodikus állapot.
- **Ami még kellhet hozzá:** Kapcsolódó régiók, eventek és tárgyak előkészítendők.
- **Fontos határ:** Történeti jelentése csak az implementált triggerekig tekinthető aktívnak.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: Kapcsolódó/ágankénti követelmény: `icesmp.admin`; `icesmp.admin.sinner`
- Config: `world.*`, corruption-, sin-, crown- és cursed-gear definíciók.
- Tartós állapot: Korrupció és bűn állapota tartós; aura runtime.
- Reload: Balance reloadolható; aktív aura/state migrációja tesztelendő.

</details>

### Rituálék és totemek

<!-- icesmp-doc-id: feature.world.rituals -->

> **Aktív, builder-előkészítést igényel** · A futó JAR-hoz képest: **Jelentősen megváltozott**

Adatvezérelt rituálék, triggerfeltételek, totemek, itemfogyasztás és eseményhatások.

- **Így találkozol vele:** Világbeli rituáléinterakció; kapcsolódó admin/event indítás.
- **Kinek szól:** Játékos, Admin, Builder, Eventes, Tesztelő.
- **Mitől mozdul meg:** Blokkminta, itemhasználat, idő/event és ritual listener.
- **Ami még kellhet hozzá:** Rituáléhelyeket, blokkmintákat, clearance-t és védelmi kivételeket elő kell készíteni.
- **Fontos határ:** Blokkminta, inventory failure és párhuzamos aktiválás runtime tesztet igényel.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: —
- Config: `world.*`, ritual-, totem-, item- és eventdefiníciók.
- Tartós állapot: Egyes rituálé/totem állapotok tartósak; effekt runtime.
- Reload: Definíció reloadolható részenként; folyamatban lévő rituálé saját snapshotot igényel.

</details>

## Fejlesztői és diagnosztikai funkciók

Ezek nem játékosjutalmak, hanem ellenőrzött műhelyeszközök. Arra valók, hogy a csapat gyorsan reprodukáljon egy helyzetet, majd nyom nélkül vissza tudjon térni a normál működéshez.

### Fejlesztői tárgyak és diagnosztika

<!-- icesmp-doc-id: feature.developer.items_debug -->

> **Aktív, adminisztratív** · A futó JAR-hoz képest: **Jelentősen megváltozott**

Jogosultságvédett dev-itemek, itemadás, debug/inspect és a fejlesztői tárgyak visszaélés elleni védelme.

- **Így találkozol vele:** `/iceitem`; admin inspect/debug útvonalak. Parancs: /iceitem (alias: /icegive, /iitem).
- **Kinek szól:** Fejlesztő/üzemeltető, Admin, Tesztelő.
- **Mitől mozdul meg:** Explicit parancs, dev-item használat és védelmi listener.
- **Ami még kellhet hozzá:** Productionben csak kijelölt staging/admin környezetben használható.
- **Fontos határ:** Kritikus jogosultság; normál admin/moderátor szerepkörnek nem javasolt.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: Kapcsolódó/ágankénti követelmény: `icesmp.admin.item`
- Config: `dev-items.*`, developer/debug és permissions.
- Tartós állapot: Dev-item state és audit tartós lehet; debug sessionállapot.
- Reload: Definíció reloadolható része csak új itemnél biztos; meglévő tárgyat ellenőrizni kell.

</details>

### IceSMP Client Bridge (protokoll-alap)

<!-- icesmp-doc-id: feature.developer.client_bridge -->

> **Aktív, adminisztratív** · A futó JAR-hoz képest: **Új rendszer**

Az opcionális Fabric kliensmod (IceSMP Client) szerveroldali alapja: plugin messaging transport
(`icesmp:client`), verzió- és capability-kézfogás, session-kezelés, rate limit, admin-diagnosztika,
state-projekciók (HUD_STATE natív HUD routinggal és vanilla-HUD-elhallgatással;
ABILITY_KIT_STATE az ability barhoz) és az első action-út: CAST_SLOT keybind-cast, ami a
katalizátorral közös canonical cast-magon fut, azonos parity-kapukkal. A kliens sosem authority,
a vanilla kliens + kötelező resource pack teljes értékű marad.

- **Így találkozol vele:** `/icesmp client <stats|név>`, `/icesmp client resync <név>`; a játékos
  számára láthatatlan, amíg nincs IceSMP Client modja.
- **Kinek szól:** Fejlesztő/üzemeltető, Admin.
- **Mitől mozdul meg:** A kliensmod kézfogás-üzenete; admin-diagnosztikai parancs.
- **Ami még kellhet hozzá:** A Fabric kliensmod (külön `MilCsik09/IceSMP-Fabric` repo) — a
  `client.features.*` kapcsolók a kliens-release-ig maradjanak `false`.
- **Fontos határ:** A `client.required` termék-elv szerint `false`: az Enhanced kliens nem lehet
  gameplay-előny feltétele.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: `icesmp.admin.client`
- Config: `client.*` (`client.yml`): `enabled` (rollback-kapcsoló), `protocol.min/max`,
  `resource-pack-schema`, `limits.*`, `features.*` (mind alapból false), `debug`.
- Tartós állapot: Nincs — a session-registry in-memory, quit/reconnect/disable eldobja.
- Reload: Minden kulcs élő (use-site olvasás); `client.enabled: false` restart nélkül állítja le a hidat.

</details>

## Tervezett, de nem aktív tartalom

A lore több ajtót mutat, mint amennyi ma kinyitható. Ez a rész választja el a kánont és a kommunikációs ötleteket a ténylegesen elérhető játékrendszerektől.

### Csak lore-ban vagy teaserben szereplő tervek

<!-- icesmp-doc-id: feature.planning.lore_only -->

> **Tervezett, de nem implementált** · A futó JAR-hoz képest: **Nem állapítható meg az élő config nélkül**

Tervezett, de ebben a release-ben nem aktív. A LORE.md és TEASER.md kizárólag narratív/tervezési forrás; regisztráció nélküli elemeik nem kerülnek az aktív funkciók közé.

- **Így találkozol vele:** Nincs aktív command, GUI, listener vagy automatikus útvonal.
- **Kinek szól:** Fejlesztő/üzemeltető, Eventes.
- **Mitől mozdul meg:** Nincs.
- **Ami még kellhet hozzá:** Ne építs rá production folyamatot addig, amíg külön implementáció és bizonyíték nem készül.
- **Fontos határ:** A dokumentumokból nem szabad implementációt feltételezni.

<details>
<summary>Admin- és technikai jegyzet</summary>

- Permission: —
- Config: Nincs aktív konfiguráció.
- Tartós állapot: Nincs.
- Reload: Nem értelmezhető.

</details>

## Amit tudatosan nem ígérünk

- Nincs jutalmazó AFK-zóna, zónaidő, payout vagy AFK-bossbar. A globális AFK ettől még aktív rendszer.
- A natív ülés **sit-only**: nincs lay, crawl, stacking, más játékos vagy NPC megülése.
- A natív tablista és ülés az IceSMP-hez szükséges részhalmaz; nem általános tablista-motor vagy teljes GSit-klón.
- A lore-ban és teaserben szereplő hely, szereplő vagy ötlet csak akkor aktív gameplay,
  ha a forrásban parancs, GUI, listener, registry vagy configolt elérési út is tartozik hozzá.
- A repository egy képességet bizonyít; azt nem, hogy az élő világban minden NPC, zóna,
  dungeon, crate és eventhely már fel is épült.

## Élesítés előtt

A „rollout-kapu alatt” jelölés nem udvarias bizonytalanság, hanem konkrét feladat:
stagingen végig kell járni a [legújabb változások](LATEST_CHANGES.md) rollout-listáját és az
[admin acceptance csomagot](ADMIN_GUIDE.md#release-acceptance-checklist). Külső plugin csak
a saját tesztcsomagjának sikeres lezárása után távolítható el.

---

<sub>Dokumentációs snapshot: 2026-07-30 · release `4643ab535…` · deployed mapping:
`775d9e247…` (`HIGH_CONFIDENCE`, nem `EXACT`).</sub>
