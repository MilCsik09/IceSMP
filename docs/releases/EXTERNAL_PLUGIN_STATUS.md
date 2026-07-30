# IceSMP — külső pluginok release-státusza

<!-- icesmp-doc-id: release.external-plugin-status -->

Ez az oldal azt rögzíti, hogy az integrált IceSMP release
(`4643ab53586f0c1ee7352df16dcd477013e6fad4`) mely külső pluginok feladatát váltja ki, melyeknél
nem cél a teljes upstream-paritás, és milyen bizonyíték kell a production
eltávolítás előtt.

## A státuszok jelentése

- **Nem szükséges, scope törölve:** a külső plugin képességét nem akarjuk
  deploymentbe vinni; nincs eltávolítási acceptance, mert maga a scope
  megszűnt.
- **Natív implementáció elkészült, runtime átvételi tesztre vár:** a
  bootstrap, a forrás, a config és a regressziós/CI bizonyíték megvan, de
  a productionközeli kézi vagy fault-injection teszt még kapu.
- **Natív megfelelő korábban elkészült, kézi tesztre vár:** kisebb,
  automatikus natív képesség forrásban bizonyított, de a valódi szerveren
  még ellenőrizendő.
- **Továbbra is szükséges:** a külső plugin által biztosított képességnek
  nincs teljes natív megfelelője, vagy az IceSMP csak opcionális bridge-et
  ad hozzá.
- **Nem cél a teljes upstream-paritás:** az IceSMP csak a szerver számára
  szükséges részhalmazt valósítja meg.
- **Bizonytalan — további ellenőrzés szükséges:** a forrás és a JAR nem
  elég az élő deploymentdöntéshez.

> A „kódszinten elkészült” és a zöld CI nem production runtime garancia.
> Külső plugin-JAR csak az adott sorban megadott acceptance után
> távolítható el. Ez a dokumentációs kör önmagában egyetlen külső JAR
> eltávolítására sem ad végrehajtási utasítást.

## Plugin replacement döntési mátrix

| Külső plugin | Végleges dokumentált státusz | Forrás- és baseline-bizonyíték | Production teendő | Kötelező runtime teszt |
|---|---|---|---|---|
| AxAFKZone | **Nem szükséges, scope törölve** | A deployed IceSMP JAR-ban nincs AFK. A release-ben globális AFK van, de nincs zóna-, payout-, zónaidő- vagy bossbar-útvonal, és a bundled AFK-config csak timeoutot és reward blockot tartalmaz. | Ne telepítsd. Ne migrálj `zones`, reward vagy bossbar configot. | Pozitív: kézi/automatikus globális AFK és tablistajelzés. Negatív: nincs zónajutalom. |
| AxAPI | **Nem szükséges, scope törölve** | Az egyetlen megnevezett felhasználási scope, az AxAFKZone, kikerült. A natív globális AFK nem függ AxAPI-tól. | Ne telepítsd csak AFK miatt. Más pluginfüggést az élő pluginlistán külön ellenőrizz. | IceSMP indulás AxAPI nélkül; teljes élő plugin dependency smoke test. |
| GSit | **Natív implementáció elkészült, runtime átvételi tesztre vár**; egyben **Nem cél a teljes upstream-paritás** | A deployed IceSMP JAR-ban nincs ülés. A release `/sit` parancsot, click-to-sit listenert, seat policyt, geometriát, foglalást és lifecycle cleanupot regisztrál. Csak plugin-owned, nem perzisztens seat entityt használ. | A GSit addig maradjon, amíg a sit-only acceptance zöld. Eltávolításkor minden olyan gameplay/config törlendő vagy kommunikálandó, amely layre, crawlra, stackingre vagy más játékos/NPC megülésére épül. | Stairs, alsó/felső slab, carpet, moss/pale moss carpet, snow; seat position; két játékos; veszélyes support/folyadék/clearance; damage, sneak, break, teleport, world change, quit, kick, dismount, reload, disable, seat sweep; GSit nélküli indulás. |
| CrazyCrates | **Natív implementáció elkészült, runtime átvételi tesztre vár** | A deployed IceSMP JAR-ban nincs crate. A release két bundled crate-et, `/crate` commandot, browser/spin GUI-t, fizikai locationt, több rewardtípust, kulcskezelést, persistent opening/recovery ledgert és forgó auditot regisztrál. Bizonytalan side effect esetén manuális vizsgálati állapotot tart fenn. | CrazyCrates csak a teljes runtime és fault-injection csomag után távolítható el. Előbb hozd létre és validáld az összes crate-helyet, reward ID-t és admin recovery folyamatot. | Main/off-hand, dupla kattintás, több key stack, mass-open és részleges mass-open, full inventory/overflow, item/currency/command/unique/recipe/blueprint/key reward, command- és currency-hiba, reload/generation, világ- vagy definíciócsere, quit/kick/disable minden state-ben, settlement/recovery, auditrotáció, restart, manuális review; CrazyCrates nélküli indulás. |
| SModeration | **Natív implementáció elkészült, runtime átvételi tesztre vár** | A deployed IceSMP JAR-ban nincs natív warning/mute/ban/report/PM/SocialSpy/vanish suite. A release regisztrálja a büntetéseket, visszavonást, historyt, aktív listát, reportokat, PM/reply-t, SocialSpy-t, vanish-t, offline teleportot, GUI-t, persistent állapotot, expiry-t és auditot. | SModeration csak a teljes permission-, persistence-, expiry-, reconnect- és audit acceptance után távolítható el. Migráció előtt döntsd el, kell-e történeti adatimport; automatikus import nincs bizonyítva. | Permanent/temporary punishment, restart és expiry, corrupt state, lemezhiba, PM quit–reconnect, SocialSpy, vanish és visibility, report, GUI, reload/disable, permissionmátrix, offline teleport; SModeration nélküli indulás. |
| InvSee++ | **Natív implementáció elkészült, runtime átvételi tesztre vár** | A deployed IceSMP JAR-ban nincs inventory admin. A release online main inventory és ender chest read/edit GUI-t, külön read/edit permissiont, escrow-t, transfer barriert és reconnect recoveryt tartalmaz. Offline inventory szerkesztés nincs bizonyítva. | InvSee++ csak az online scope acceptance után távolítható el, és csak akkor, ha az élő csapatnak nincs szüksége InvSee++-specifikus vagy offline képességre. Az edit jog legyen szűken kiosztva. | Main/ender read/edit, célpont inventory változása, full inventory, sessionütközés, quit/kick/reconnect, reload/disable, scheduler rejection, escrow visszaadás és fault injection; InvSee++ nélküli indulás. |
| MiniMOTD | **Natív implementáció elkészült, runtime átvételi tesztre vár** | A deployed IceSMP JAR-ban nincs server-list ping listener. A release TIME/RANDOM választást, normál és eseményváltozatokat, eseményprioritást, vanished count szűrést, ikonmódokat, ikonvalidációt, path/symlink fail-closed védelmet, generation gate-et és reloadot tartalmaz. | MiniMOTD csak server-list acceptance után távolítható el. Proxy-, virtual-host- vagy MiniMOTD-specifikus funkcióra nincs teljes paritásbizonyíték. | Párhuzamos ping, TIME és RANDOM, prioritás, placeholder, vanished count, NONE/DEFAULT/VARIANT/RANDOM ikonmód, hibás/túl nagy/nem 64×64 PNG, symlink, gyors reload, scheduler rejection, MiniMOTD nélküli indulás. |
| TAB | **Nem cél a teljes upstream-paritás** | A deployed JAR HUD-ja tartalmazott részleges tablistát, bundled defaultban kikapcsolva. A release külön natív tablistaréteget, headert/footert, névmegjelenítést, rendezést és pingoszlopot ad; a bundled HUD sidebar és tablista engedélyezett. A forrás nem bizonyít általános TAB-klónt. | Ha az élő szerver csak az IceSMP-hez dokumentált subsetet használja, a natív réteg lehet kiváltási jelölt. Ha TAB-specifikus placeholder, layout, scoreboard, nametag vagy proxyfunkció kell, TAB továbbra is szükséges. Döntés előtt készíts élő TAB-config leltárt. | TAB-bal együtt és TAB nélkül: header/footer, név, frakció/AFK/vanish, sort, ping, reload, reconnect, több világ és külső chat/scoreboard kompatibilitás. |
| ICEsmpadditions | **Natív megfelelő korábban elkészült, kézi tesztre vár** | A release regisztrált world-tweaks listenere Warden-halálkor felülírja az XP-dropot. Bundled default: engedélyezve, minimum 80, maximum 125. A deployed IceSMP JAR-ban nincs ilyen külön útvonal. | A mini-plugin addig maradjon, amíg stagingen nem bizonyított a pontos drop és az együttfutás/dupla felülírás hiánya. | Warden death több mintával; 80 és 125 szélek; config off; min/max hibás sorrend; ICEsmpadditions nélkül és rövid összehasonlító próba együttfutással. |
| FarmProtect | **Natív megfelelő korábban elkészült, kézi tesztre vár** | A release külön player `PHYSICAL` és nem-player entity interact eseményen védi a farmlandot. Mindkét bundled default `true`. A deployed IceSMP JAR-ban nincs általános crop-trample útvonal. | A plugin csak játékos- és mobteszt, valamint védelmi pluginokkal való kompatibilitás után távolítható el. | Játékos ugrás/futás, több mobtípus, cancelled event, játékosnak számító kivétel, players/mobs külön ki- és bekapcsolása, FarmProtect nélküli indulás. |

## Pontosan mit jelent az AFK-döntés?

A release-ben az AFK-rendszer **nem került teljesen eltávolításra**:

- automatikus inaktivitásmérés van;
- a játékos kézzel kapcsolhatja az AFK-állapotát `/afk` paranccsal;
- a tablista jelölheti az állapotot;
- a konfiguráció engedheti, hogy az AFK játékos ne kapjon bizonyos
  profession-, dungeon-, mob-, worldboss- és eseményjutalmakat.

Ami nincs:

- AFK-zóna;
- zónában töltött idő számlálása;
- zónánkénti payout;
- időszakos valuta- vagy itemjutalom;
- AFK-zóna bossbar;
- AxAFKZone- vagy AxAPI-függés.

Ezért az AxAFKZone/AxAPI nem rollout-kapu alatt álló replacement, hanem
tudatosan törölt scope.

## A natív replacementek bizonyított határai

| Natív terület | Bizonyított scope | Nem bizonyított / nem támogatott scope |
|---|---|---|
| Sit | Ülés támogatott blokkgeometrián, világ/material policy, cleanup | Lay, crawl, stacking, player/NPC sitting, teljes GSit API/paritás |
| Crate | Két bundled crate, helyek, kulcsok, GUI, több rewardtípus, audit és recovery | Élő CrazyCrates-adat automatikus importja; production fault-tűrés acceptance nélkül |
| Moderáció | Warning, kick, mute/ban, history, reports, PM, SocialSpy, vanish, online invsee, offline teleport | Külső punishment-adat automatikus migrációja; minden upstream GUI/API |
| Inventory admin | Online main/ender read és edit, escrow/reconnect recovery | Offline inventory/ender szerkesztés nem bizonyított |
| MOTD | IceSMP server-list variánsok, eseményprioritás, count és ikon | Teljes MiniMOTD proxy/virtual-host/third-party placeholder paritás |
| TAB-megjelenítés | IceSMP HUD/tablista subset | Általános TAB layout/scoreboard/proxy/placeholder paritás |
| Warden XP | Warden XP-drop configolt tartományra állítása | ICEsmpadditions minden más esetleges képessége |
| Crop protection | Farmland player- és mobtaposásának tiltása | FarmProtect minden esetleges extra crop- vagy régiófeature-e |

## Továbbra is használt opcionális integrációk

Ezek nem a replacement-scope részei. A release továbbra is tartalmaz
opcionális bridge-et vagy integrációt:

| Plugin / rendszer | Státusz | IceSMP-kapcsolat | Deployment megjegyzés |
|---|---|---|---|
| PlaceholderAPI | **Továbbra is szükséges**, ha `%icesmp_…%` placeholdereket fogyaszt más plugin | Placeholder expansion | IceSMP alapfunkciói nélküle is indulhatnak; a fogyasztó pluginokat leltározni kell. |
| FancyNpcs | **Továbbra is szükséges** az NPC-kötött quest/shop/bank/exchange/dialog funkciókhoz | NPC binding és marker/interakció | Fizikai NPC-tartalom nélküle részben nem elérhető. |
| WorldGuard | **Továbbra is szükséges**, ha az élő világ WorldGuard bridge-re épít | Claim- és territory-policy bridge | Natív claim léte nem bizonyítja, hogy az élő WorldGuard-régiók elhagyhatók. |
| LuckPerms | **Továbbra is szükséges**, ha a natív chatnek prefix/suffix vagy a szervernek permissionkezelés kell | Chat metadata; permissionkiosztás | Az IceSMP nem permission backend. |
| LibsDisguises | **Továbbra is szükséges** a kiterjesztett druid alakváltás vizuális részéhez | Opcionális disguise bridge | Nélküle a kapcsolódó vizuális élmény csökkenhet. |

## Eltávolítási sorrend

1. Az AxAFKZone és AxAPI nem kerül deploymentbe; előtte csak azt
   ellenőrizd, hogy más élő plugin nem függ AxAPI-tól.
2. Külön staging körben validáld a kis replacementeket:
   ICEsmpadditions/Warden XP és FarmProtect/crop trample.
3. Ezután a natív MOTD-t és sit-only rendszert teszteld a külső megfelelő
   nélkül.
4. A moderációt és inventory admint csak teljes permission-, persistence-,
   reconnect- és recovery-csomaggal váltsd át.
5. A crate legyen az utolsó kritikus replacement: itt kötelező a
   fault-injection, settlement és manuális recovery bizonyíték.
6. A TAB-ról külön, az élő TAB-konfiguráció leltára alapján dönts; a
   natív IceSMP subset nem általános upstream-paritás.

## Release előtti bizonyítéklista

- [ ] A külső pluginok tényleges élő verziója és configja archiválva.
- [ ] A dependencyk és más pluginok API-használata leltározva.
- [ ] A replacementenkénti staging teszt bizonyítéka csatolva.
- [ ] Restart, reload és clean-start teszt elkészült a külső plugin nélkül.
- [ ] A permission- és parancsütközések ellenőrizve.
- [ ] Az adat- vagy configmigráció módja dokumentálva.
- [ ] Rollbackterv és visszaállítható plugin/config mentés elkészült.
- [ ] Productionből egyetlen JAR sem lett eltávolítva pusztán a zöld CI
  alapján.

Részletes tesztesetek:
[`RELEASE_ACCEPTANCE_CHECKLIST.md`](RELEASE_ACCEPTANCE_CHECKLIST.md).
A deployed JAR-hoz képesti teljes változáslista:
[`DEPLOYED_BUILD_TO_RELEASE_CHANGELOG.md`](DEPLOYED_BUILD_TO_RELEASE_CHANGELOG.md).
