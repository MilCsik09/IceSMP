# IceSMP admin-, moderátori és tesztelői kézikönyv

<!-- icesmp-doc-id: guide.admin-and-moderator -->

<details>
<summary>Dokumentum-forrásállapot (HEAD, audit dátuma, futó baseline)</summary>

- Dokumentált HEAD: `4643ab53586f0c1ee7352df16dcd477013e6fad4`
- Audit dátuma: 2026-07-30
- Deployed baseline: `IceSMP-1.0-TESTING.jar` (`da039f0e2bdf0e67b216ce82d7d3fe3b6da0af6e18f6fa175762c37493795a05`); valószínű forrásállapot: `775d9e247be675db1c7c9beaaecf4a90349bfcd3` (2026-07-12, `HIGH_CONFIDENCE`, nem `EXACT`)

</details>

Az adminfelület nem varázspálca. Minden kattintás mögött egy játékos története, egy tartós állapot és gyakran egy visszaállítási kötelezettség áll.
Ez a kézikönyv azért készült, hogy a csapat gyorsan tudjon cselekedni, de ugyanilyen gyorsan meg is tudja mondani, **ki, mit és miért tett**.

> **Alapelv:** a legkisebb szükséges jogosultságot add, kritikus mutáció előtt készíts bizonyítékot, hiba esetén pedig állítsd meg a folyamatot — ne próbáld találomra „helyrehozni” az élő state-et.

A napi játékoshasználat a [játékoskézikönyvben](PLAYER_GUIDE.md), a fizikai világkötések a [builder kézikönyvben](BUILDER_GUIDE.md), a teljes rendszerkép pedig a [funkciókatalógusban](FEATURES.md) található.

## 1. Bevezetés előtti minimum

A natív rendszert csak staging playtest után vedd át külső moderációs
pluginoktól. Az első teszt előtt:

1. készíts visszaállítható mentést a teljes IceSMP pluginmappáról és a
   játékosadatokról;
2. külön tesztcsoportokkal állítsd be a permissionmátrixot;
3. ellenőrizd, hogy a szerverfolyamat írhatja a plugin adat- és
   `logs/` könyvtárát;
4. teszteld a restartot, lejáratot, sérült állapotot és lemezírási hibát;
5. invsee editnél teszteld a viewer és a céljátékos kilépését, a reloadot,
   a kontrollált disable-t és külön az azonnali process crash-t;
6. csak az elfogadási bizonyítékok rögzítése után távolítsd el az
   SModeration vagy InvSee++ JAR-t.

> **Adatvédelmi figyelmeztetés:** a SocialSpy és a chat-moderációs napló
> privát üzenetek tartalmát is megjelenítheti vagy rögzítheti. A
> jogosultságot szűken oszd, a naplóhoz való fájlhozzáférést korlátozd, és
> a játékosok felé alkalmazd a szerver adatkezelési szabályzatát.

## 2. Jogosultsági modell

### 2.1. Moderációs node-ok

| Permission | Mire jogosít | Runtime alapérték | Javasolt kiosztás |
|---|---|---|---|
| `icesmp.admin.moderation` | A teljes natív moderációs csomag parent node-ja; közvetlenül ez kell a `/reports` útvonalhoz is. | OP | Csak olyan szerepnek, amelynél a teljes csomag elfogadható, vagy a permission backendben explicit child tiltások vannak. |
| `icesmp.moderation.warn` | `/warn` | OP | Moderátor |
| `icesmp.moderation.kick` | `/kick` | OP | Moderátor |
| `icesmp.moderation.mute` | `/mute`, `/unmute` | OP | Moderátor |
| `icesmp.moderation.ban` | `/ban`, `/tempban`, `/unban` | OP | Senior moderátor vagy admin |
| `icesmp.moderation.history` | `/history`, `/punishments` | OP | Moderátor |
| `icesmp.moderation.socialspy` | `/socialspy`; natív PM-ek tartalmának megfigyelése | OP | Szűk senior moderátori kör |
| `icesmp.moderation.vanish` | Saját vagy online cél vanish állapotának kapcsolása | OP | Admin |
| `icesmp.moderation.vanish.see` | Vanished játékosok megtekintése | OP | Admin vagy vezető moderátor |
| `icesmp.moderation.offlinetp` | Online célhoz, illetve mentett kijelentkezési helyre teleport a moderációs GUI-ból; `/offlinetp` | OP | Admin vagy senior moderátor |
| `icesmp.moderation.inventory.read` | Online inventory és ender chest csak olvasható nézete | OP | Senior moderátor |
| `icesmp.moderation.inventory.edit` | Online inventory és ender chest szerkesztése | OP | Csak vezető admin |
| `icesmp.moderation.gui` | `/moderation` és `/mod` megnyitása | OP | Moderátor |
| `icesmp.message` | `/msg`, `/tell`, `/w`, `/reply`, `/r` | TRUE | Játékosok |
| `icesmp.admin.reload` | `/icesmp reload` és az `/icesmp` admin gyökér | OP | Üzemeltető vagy admin |

Az `icesmp.admin.all` minden kanonikus IceSMP admin-domain parentje,
beleértve az `icesmp.admin.moderation` csomagot. Ezt csak vezető
adminnak vagy üzemeltetőnek add.

### 2.2. Fontos parent-node következmény

Az `icesmp.admin.moderation` nem pusztán „reportlista-jog”: parentként mind
a tizenkét `icesmp.moderation.*` leaf node-ot igaz értékkel adja tovább. A
`/reports` ugyanakkor közvetlenül ezt a parent node-ot ellenőrzi.

Ha valaki kezelhet reportokat, de például nem kaphat inventory editet vagy
bant, akkor a permission backendben:

- add meg az `icesmp.admin.moderation` node-ot;
- az érzékeny child node-okat explicit `false` értékkel tiltsd;
- stagingen tényleges játékosfiókkal ellenőrizd a parancsot és a GUI
  gombjait is.

Az OP státusz alapból minden felsorolt moderációs node-ot megad. Ne használd
az OP-ot szerepkörkezelés helyett.

### 2.3. Javasolt szerepköri felosztás

Ez üzemeltetési javaslat, nem automatikus forrásbeli szerepdefiníció.

| Szerep | Javasolt képességek | Különösen ne add automatikusan |
|---|---|---|
| Játékos | `icesmp.message`; `/report` permission nélkül | Minden admin node |
| Moderátor | warn, kick, mute, history, GUI; szükség szerint reportkezelés | ban, vanish, inventory edit |
| Senior moderátor | moderátori készlet + ban, SocialSpy, inventory read | inventory edit, `admin.all` |
| Admin | vanish, vanish visibility, offline teleport; indokolt esetben inventory edit | `admin.all`, ha nincs rá üzemeltetési szükség |
| Vezető admin/üzemeltető | teljesen ellenőrzött adminmátrix, reload | Ne ossza meg a saját super-node-ját alsóbb csoporttal |

## 3. Teljes moderációs parancsmátrix

A `<…>` kötelező, a `[…]` opcionális argumentum. Az „audit” oszlop
megkülönbözteti az autoritatív állapotot a best-effort szöveges naplótól.

| Parancs | Alias | Argumentum és példa | Közönség | Permission | Konzol | GUI-alternatíva | Fontos korlát | Audit |
|---|---|---|---|---|---|---|---|---|
| `/warn <játékos> [ok]` | — | Példa: `/warn Anna reklám a chatben` | Moderátor | `icesmp.moderation.warn` | Igen | Moderációs GUI, 10. slot | A cél legyen online, Bukkit által cache-elt offline játékos vagy már ismert moderációs cél. | Tartós punishment rekord, adminnév/UUID, idő, ok és rekordazonosító. |
| `/kick <játékos> [ok]` | — | Példa: `/kick Anna ismételt flood` | Moderátor | `icesmp.moderation.kick` | Igen | Moderációs GUI, 13. slot | Csak online cél; előbb a mentésnek kell sikerülnie, utána történik a kick. | Tartós punishment rekord; a kick történeti eseményként marad meg. |
| `/mute <játékos> [30m\|2h\|7d\|végleges] [ok]` | — | `/mute Anna 30m flood`; `/mute Anna flood`; `/mute Anna végleges bot` | Moderátor | `icesmp.moderation.mute` | Igen | Moderációs GUI: fix 30 perc, 11. slot | Idő nélkül az előzmények szerinti eszkaláció fut; maximum 365 nap. Egy célon egyszerre legfeljebb egy aktív mute-család lehet. | Tartós punishment ledger. |
| `/unmute <játékos> [ok]` | — | Példa: `/unmute Anna téves riasztás` | Moderátor | `icesmp.moderation.mute` | Igen | Moderációs GUI, 14. slot | Aktív mute nélkül sikertelen. | Külön tartós UNMUTE rekord, az eredeti mute rekordjához kapcsolva. |
| `/ban <játékos> [ok]` | — | Példa: `/ban Anna klienscsalás` | Senior moderátor/Admin | `icesmp.moderation.ban` | Igen | Moderációs GUI, 12. slot | Végleges ban; az ismeretlen, sosem cache-elt név nem oldható fel. | Tartós punishment ledger; online cél mentés után kerül kirúgásra. |
| `/tempban <játékos> <idő> [ok]` | — | Példa: `/tempban Anna 7d visszaeső csalás` | Senior moderátor/Admin | `icesmp.moderation.ban` | Igen | Nincs külön tempban gomb | Kötelező pozitív idő; `végleges` itt érvénytelen; maximum 365 nap. | Tartós punishment ledger. |
| `/unban <játékos> [ok]` | — | Példa: `/unban Anna fellebbezés elfogadva` | Senior moderátor/Admin | `icesmp.moderation.ban` | Igen | Moderációs GUI, 15. slot | Aktív ban nélkül sikertelen. | Külön tartós UNBAN rekord, az eredeti banhoz kapcsolva. |
| `/history <játékos> [oldal]` | — | Példa: `/history Anna 2` | Moderátor | `icesmp.moderation.history` | Igen | Moderációs GUI, 19. slot | Oldalanként 8 rekord; hibás oldalérték 1, túl nagy érték az utolsó oldalra kerül. | Read-only lekérdezés; nincs külön lekérdezési audit. A forrásadat a ledger. |
| `/punishments [játékos]` | — | `/punishments`; `/punishments Anna` | Moderátor | `icesmp.moderation.history` | Igen | Moderációs GUI, 20. slot | Argumentum nélkül globális lista; csak logikailag aktív rekordokat mutat. | Read-only lekérdezés; nincs külön lekérdezési audit. |
| `/report <név> <ok>` | `/bejelent` | Példa: `/report Anna tiltott kliens használata` | Játékos | Nincs | Nem | Nincs | Legalább 3 szavas ok; önbejelentés tiltott; a célnak nem kell léteznie vagy online lennie; játékosonként 60 mp cooldown. | `reports.yml`: bejelentő, cél név, ok, idő és állapot. |
| `/reports` | — | `/reports`; `/reports all`; `/reports resolve 17` | Moderátor/Admin | `icesmp.admin.moderation` | Igen | Moderációs GUI, 21. slot | Az alaplista a nyitott reportokat mutatja; `all` legfeljebb 20 legutóbbit; a lezáráshoz nincs külön indokmező vagy lezárási időbélyeg. | `reports.yml` rögzíti a lezáró nevét; nincs külön auditlog. |
| `/msg <játékos> <üzenet>` | — | Példa: `/msg Anna Kérlek, gyere a spawnhoz.` | Játékos | `icesmp.message` | Nem | Nincs | Csak online és a feladó számára látható cél; önmagának nem írhat. | Ha a chatlog engedélyezett: kézbesített és mute/spam/filter miatt blokkolt PM naplózódik. |
| `/tell <játékos> <üzenet>` | — | A `/msg` önálló, azonos működésű root változata. | Játékos | `icesmp.message` | Nem | Nincs | Nem alias: külön regisztrált root, de ugyanazt a PM-szolgáltatást használja. | Ugyanaz, mint `/msg`. |
| `/w <játékos> <üzenet>` | — | A `/msg` önálló, azonos működésű root változata. | Játékos | `icesmp.message` | Nem | Nincs | Nem alias; a némítottparancs-blokkolás alaplistájában is szerepel. | Ugyanaz, mint `/msg`. |
| `/reply <üzenet>` | `/r` | Példa: `/r Rendben, indulok.` | Játékos | `icesmp.message` | Nem | Nincs | Csak sikeresen kézbesített előző PM hoz létre reply-partnert; quit vagy kick törli a kapcsolatot. | Ugyanaz, mint a PM-eknél. |
| `/socialspy` | — | Argumentum nélküli tartós ki/be kapcsoló | Senior moderátor | `icesmp.moderation.socialspy` | Nem | Moderációs GUI, 30. slot; mindig a GUI használóját kapcsolja | Csak a natív IceSMP PM-útvonalat figyeli; nem packet interceptor és nem lát más plugin üzeneteibe. | A kapcsoló állapota tartós; nincs külön kapcsolási auditlog. |
| `/vanish [online játékos]` | `/v` | `/vanish`; `/v Anna` | Admin | `icesmp.moderation.vanish` | Cél megadásával igen | Moderációs GUI, 31. slot | Toggle, nem explicit `on/off`; cél csak online lehet. Argumentum nélkül csak játékos saját magán használhatja. | A vanish állapot tartós; nincs külön kapcsolási auditlog. |
| `/invsee <online játékos> [read\|edit] [main\|ender]` | — | `/invsee Anna read main`; `/invsee Anna edit ender` | Senior moderátor/Admin | read: `icesmp.moderation.inventory.read`; edit: `icesmp.moderation.inventory.edit` | Nem | Moderációs GUI, 22–25. slot | Csak online, látható cél; saját inventory tiltott; nincs offline playerdata-szerkesztés. Hibás/hiányzó mód `read`, hibás/hiányzó nézet `main`. | Editenként best-effort `logs/moderation-audit.log`; escrow külön tartós állapot. Read megnyitása nincs naplózva. |
| `/offlinetp <játékos>` | — | Példa: `/offlinetp Anna` | Admin/Senior moderátor | `icesmp.moderation.offlinetp` | Nem | Moderációs GUI, 29. slot; a 28. slot külön online teleport | A világ UUID-jának és nevének egyeznie és a világnak betöltve lennie kell; nincs biztonságos hely keresése. | Nincs külön teleport-audit. |
| `/moderation [online játékos]` | `/mod` | `/moderation`; `/mod Anna` | Moderátor/Admin | `icesmp.moderation.gui` | Nem | Maga a GUI | Legfeljebb 45 látható online játékos, nincs lapozás; az akciógombokhoz külön leaf permission kell. | A megnyitás nincs naplózva; a gombok a mögöttes parancs auditját öröklik. |
| `/icesmp reload` | `/ismp reload` | Konfiguráció és üzenetek újratöltése | Admin/Üzemeltető | `icesmp.admin.reload` | Igen | Az admin/config felületek egyes útvonalai | Nem tölti újra a tartós moderációs state fájlokat; bezárja az élő invsee sessionöket. | Nincs külön moderációs audit; konzol-visszajelzés van. |

### 3.1. Tab completion és láthatóság

A moderációs céljátékos-completion az online, a parancskiadó számára
látható játékosokat ajánlja fel. A vanished vagy más okból rejtett játékost
olyan viewer nem kapja meg javaslatként, aki nem láthatja.

Ez nem minden parancsnál jelenti ugyanazt:

- punishment parancsnál az exact online név, a Bukkit által cache-elt
  offline játékos, majd a moderációs ledger ismert játékoslistája használható;
- `/kick`, `/vanish`, `/invsee` és a GUI célpontjai online állapotot
  igényelnek;
- `/report` nem validálja, hogy a megadott célnév valaha létezett-e;
- `/offlinetp` csak már mentett kijelentkezési hellyel működik.

## 4. Warning, kick, mute és ban

### 4.1. Közös működés

Minden büntetés stabil rekordazonosítót, cél- és adminazonosítót/nevet,
okot, létrehozási időt, opcionális lejáratot és állapotot kap.

- Ha nincs ok, az alapérték `Nincs megadva`.
- Egy szövegmező legfeljebb 512 karakter lehet.
- Egy céljátékosnak egyszerre legfeljebb egy aktív mute-család és egy aktív
  ban-család rekordja lehet.
- A warning és a kick történeti esemény: nem aktív korlátozásként, hanem
  rögzített rekordként marad meg.
- Az aktív ban az async pre-login ellenőrzésnél blokkol, és az indokot,
  ideiglenes bannál a hátralévő időt is közli.
- A tartós mentés sikere megelőzi az online mellékhatást. Így például egy
  ban csak sikeres state-írás után rúgja ki az aktuális játékost.

### 4.2. Időtartamok és mute-eszkaláció

Elfogadott időegységek:

| Példa | Jelentés |
|---|---|
| `30` vagy `30m` vagy `30p` | 30 perc |
| `45s` | 45 másodperc |
| `2h` | 2 óra |
| `7d` vagy `7n` | 7 nap |
| `2w` | 2 hét |
| `0`, `permanent`, `vegleges`, `végleges` | Végleges; csak a `/mute` útvonalon használható |

Az időzített maximum 365 nap.

A `/mute Anna ok...` forma időtartam nélkül az adott játékos korábbi
mute-család rekordjainak száma alapján választ időt. A bundled alaplista:
5, 30, 180 és 1440 perc; a lista végén az utolsó érték ismétlődik. Például:

- `/mute Anna túl gyors chat` → eszkalált ideiglenes mute;
- `/mute Anna 30m túl gyors chat` → pontosan 30 perces mute;
- `/mute Anna végleges bot-hirdetés` → végleges mute.

Ha a második token időtartamnak néz ki, de érvénytelen — például `400d` —,
a parancs hibával leáll; nem kezeli automatikusan az indok első szavaként.

### 4.3. Lejárat és visszavonás

Az ideiglenes büntetés a lejárati pillanattól logikailag inaktív, akkor is,
ha a karbantartó feladat még nem írta át a rekord állapotát. A percenkénti
karbantartás ezt később lejártként tartósan is rögzíti.

Az `/unmute` és `/unban`:

- csak aktív, megfelelő családú büntetést old fel;
- külön feloldási rekordot hoz létre;
- összekapcsolja az eredeti és a feloldási rekordot;
- rögzíti a feloldó admin nevét/UUID-ját, az időt és az okot.

Ne törölj kézzel ledgerbejegyzést egy feloldás „egyszerűsítésére”; használd
a parancsot, hogy az auditlánc megmaradjon.

### 4.4. History és aktív nézet

`/history <játékos> [oldal]` minden rekordtípust mutat, oldalanként nyolcat.
Az `/punishments [játékos]` csak a jelenleg logikailag aktív
korlátozásokat mutatja, játékos nélkül globálisan.

Javasolt moderációs folyamat:

1. ellenőrizd a `/history` oldalt;
2. ellenőrizd az aktív állapotot;
3. rögzíts pontos, tárgyszerű okot;
4. hajtsd végre az akciót;
5. jegyezd fel a visszaadott rekordazonosítót a ticketben vagy belső
   incidensnaplóban.

## 5. Reportok

### 5.1. Játékosoldal

A játékos `/report <név> <legalább háromszavas ok>` paranccsal küldhet
bejelentést. A rendszer:

- tiltja az önbejelentést;
- eltávolítja az `&` formázási karaktert az okból;
- játékosonként legfeljebb egy reportot enged 60 másodpercenként;
- siker után értesíti az online
  `icesmp.admin.moderation` jogosultakat.

A cooldown csak memóriában él, ezért restart után újraindul. A megadott
célnév nem kap UUID-validációt; elírás vagy nem létező név is rögzíthető.

### 5.2. Adminoldal

- `/reports` — minden nyitott report, a legrégebbitől;
- `/reports all` — nyitott és lezárt reportok, a legújabbtól, maximum 20;
- `/reports resolve <id>` — nyitott report lezárása.

Lezárás után a bejelentő online állapotban azonnali, offline állapotban a
következő belépéskor tartósan várakozó visszajelzést kap. A lezárt, a
létrehozási idő alapján 30 napnál régebbi reportokat a betöltés törli; a
nyitott reportok megmaradnak.

Korlátok:

- a lezárás nem kér és nem tárol külön indokot;
- a rekord tárolja a lezáró nevét, de nem tárol külön lezárási időt;
- a reportlista nem céljátékos-specifikus GUI: a céloldal reportgombja is a
  globális `/reports` listát nyitja;
- a report-state maga az auditnyom; nincs külön report-auditlog.

## 6. Privát üzenetek, chatvédelem és SocialSpy

### 6.1. Privát üzenet kézbesítése

`/msg`, `/tell` és `/w` három külön regisztrált root parancs, de azonos
szolgáltatást használ. A `/reply` aliasa `/r`.

A feladó csak akkor kap sikervisszajelzést, amikor a címzett saját
schedulerén a kézbesítés ténylegesen lefutott. Csak ezután jön létre a
kétirányú reply-kapcsolat.

Quit vagy kick:

- lezárja az adott játékos PM-sessionjét;
- mindkét irányból törli a reply-kapcsolatot;
- reconnect után a `/reply` nem működik addig, amíg új PM-et nem
  kézbesítettek sikeresen.

Nincs offline PM és nincs más plugin üzeneteit elfogó packet
interception.

### 6.2. Mute, spam és szűrő

A natív PM-et küldés előtt ugyanaz a mute-, spam- és szövegszűrés vizsgálja.
A bundled alapérték:

- minimum 1500 ms két elfogadott üzenet között;
- azonos üzenet 20 másodpercen belül ismételve blokkolt;
- a tiltott szavak kis-/nagybetűtől független részszó-egyezést használnak;
- `CENSOR` módban a találat csillagozódik, `BLOCK` módban az egész üzenet
  elutasításra kerül;
- ismeretlen filtermód biztonságos `BLOCK` fallbacket kap.

A public chat moderációs listenerét a `moderation.enabled` kapcsolja. A
natív PM parancs ezzel szemben közvetlenül futtatja a mute-, spam- és
filterellenőrzést, ezért ezek a PM-en az általános kapcsoló kikapcsolása
mellett is érvényben maradnak.

Némítás alatt a bundled tiltott parancscímkék:
`msg`, `w`, `tell`, `me`, `r`. A namespaced alakok is normalizálódnak.

### 6.3. SocialSpy

A `/socialspy` tartós, játékosonkénti kapcsoló. A jogosultságot a rendszer:

- a kapcsolás pillanatában;
- és minden megfigyelt üzenet kézbesítésekor újra ellenőrzi.

A spy a natív PM-eknél többek között ezeket az állapotokat láthatja:

- `DELIVERED`;
- `BLOCKED_MUTED`;
- `BLOCKED_SPAM`;
- `BLOCKED_FILTER`;
- `TARGET_OFFLINE`;
- `TARGET_RETIRED`.

A feladó és a címzett nem kap saját spy-másolatot. A SocialSpy állapota
restart után is megmarad, de a kapcsolásról nincs külön szöveges auditlog.

### 6.4. Chatnapló

Ha `moderation.chat-log.enabled: true`, a
`logs/chat-moderation.log` rögzíti:

- a némítás miatt blokkolt public chatet/parancsot;
- a filter által blokkolt vagy cenzúrázott public chatet;
- a spam miatt blokkolt eseményt;
- a kézbesített natív PM-et;
- a mute, spam vagy filter miatt blokkolt natív PM-et.

A log az eredeti üzenetszöveget is tartalmazhatja. Öt MiB felett egyetlen
`.1` fájlba rotálódik. A logírás best-effort: hiba esetén warning kerül a
konzolra, de a chat- vagy PM-döntés nem gördül vissza. A korai
`TARGET_OFFLINE` és scheduler-retirement PM-kimenet SocialSpyban látható
lehet, de nem minden ilyen kimenet kap fájllog sort.

## 7. Vanish

### 7.1. Láthatóság

`/vanish` a saját, `/vanish <online játékos>` egy online cél állapotát
kapcsolja. Nincs külön `on` vagy `off` argumentum: mindig toggle történik.

- `icesmp.moderation.vanish.see` nélkül a viewer nem látja a vanished
  játékost;
- a látási jog nem ad vanish-kapcsolási jogot, és fordítva;
- a belépési és kilépési üzenet vanished állapotban elmarad;
- mob nem választhat vanished játékost célpontnak;
- a natív MOTD és tablista online számlálója kihagyhatja a vanished
  játékost.

A tab completion és a moderációs játékoslista elrejti a nem látható
célokat. A manuálisan beírt `/vanish <pontos-online-név>` útvonal viszont a
toggle jogosultságot ellenőrzi, nem a `vanish.see` jogot; emiatt a
vanish-kapcsolási jogot önmagában se oszd széles körben.

### 7.2. Gameplay-kapuk

A bundled alapkonfigurációban a vanished admin:

- nem vesz fel tárgyat;
- nem sebez és nem sebezhető;
- nem interaktál blokkal vagy entitással.

A damage-tiltás a kimenő és bejövő, illetve projectile sebzést is érinti.
Ezek configgal változtathatók.

Vanish nem jelent teljes szerveroldali „csendet”: a forrás nem tiltja
automatikusan a vanished admin chatjét vagy parancsait, és más plugin saját
online számlálója sem köteles az IceSMP-filtert használni.

Az IceSMP csak a saját maga által létrehozott hide/show kapcsolatokat
állítja vissza; más plugin rejtését nem oldja fel.

### 7.3. Lifecycle

A vanish állapot tartós és relog után megmarad. Config reload újraszámolja
a láthatóságot. Kontrollált disablekor az IceSMP best-effort visszaállítja
a saját rejtéseit, miközben a tartós állapot a következő indulásra megmarad.

## 8. Online inventory és ender chest

### 8.1. Read és edit különbség

Az invsee csak online, a viewer számára látható másik játékost támogat.
Nincs offline playerdata-parser, saját inventory adminnézet vagy crafting
slot kezelése.

| Nézet | Célterület | Mód |
|---|---|---|
| `main` | storage 0–35, armor 36–39, offhand 40 | `read` vagy `edit` |
| `ender` | ender chest 0–26 | `read` vagy `edit` |

A nézet körülbelül 10 tickenként frissül. Read módban minden
inventory-interakció tiltott. Edit módban:

- a felső cél-slot és az admin kurzorán lévő stack cserélődik;
- drag a felső inventoryba tiltott;
- shift-move, hotbar-swap, collect-to-cursor és ismeretlen akció tiltott;
- a rendszer a kattintáskor ismét ellenőrzi az edit permissiont;
- a kiszorított tárgy először az admin kurzorára, majd inventoryjába,
  végül — ha minden megtelt — az admin helyén természetes dropként kerül.

Hiányzó vagy ismeretlen mód read-onlyra, hiányzó vagy ismeretlen nézet
main inventoryra esik vissza. Érzékeny munkánál mindig írd ki mindkét
argumentumot.

### 8.2. Invsee-audit

Minden sikeres edit best-effort sort ír a
`logs/moderation-audit.log` fájlba:

- admin UUID és név;
- cél UUID és név;
- `MAIN` vagy `ENDER` nézet;
- raw slot;
- beillesztett és kiszorított material + mennyiség.

A log nem tartalmazza az item teljes metaadatát, enchantjait vagy egyedi
NBT/PDC tartalmát. A logírás hibája warningot okoz, de nem gördíti vissza a
már elvégzett inventory editet. A read-only megnyitás nincs külön
auditálva.

### 8.3. Escrow és kontrollált recovery

Az edit közben a rendszer egyetlen aktuális tulajdonost tart nyilván a
mozgatott stackhez. Ha a viewer vagy a cél kilép, reload vagy kontrollált
disable történik, a visszaadandó item:

1. közvetlenül visszakerül, ha ez biztonságosan lehetséges;
2. egyébként az `invsee-escrow.yml` visszaadási sorába kerül;
3. az admin következő belépésekor visszaáll;
4. sikertelen visszaadásnál a sor elejére kerül vissza.

Az escrow séma legfeljebb 10 000 játékost és összesen 100 000 itemrekordot
enged. Sérült, duplikált, túlméretes vagy ismeretlen szerkezetű autoritatív
state induláskor fail-closed.

### 8.4. Garanciahatár azonnali process crashnél

Nincs a player inventoryt és a plugin state fájlt egyetlen
write-ahead-log tranzakcióba fogó, formális exactly-once protokoll.

- Ha a process a cél inventoryjának írása és a következő tartós
  escrow-save között azonnal leáll, a visszaadandó tárgy elveszhet.
- Ha a process reconnect-visszaadás után, de a következő save előtt áll le,
  a tárgy ismételten visszaadható.

Ezért az invsee edit átvételéhez kötelező a crash-fault-injection teszt, és
abrupt crash után tilos vakon visszaadni egy itemet. Előbb egyeztesd a
játékosadatot, az escrow-t, az auditlogot, a konzollogot és a mentést.

## 9. Offline teleport

Az utolsó hely a játékos `PlayerQuit` eseményénél kerül tartós állapotba:

- világ UUID és név;
- koordináták;
- yaw és pitch;
- mentési idő.

Az `/offlinetp` nem tölt be szinkron világot vagy chunkot, és nem keres
biztonságos padlót. A teleport elutasításra kerül, ha:

- nincs mentett hely;
- a világ nincs betöltve;
- a világ UUID-ja megváltozott;
- a név alapján talált világ UUID-ja nem egyezik a mentettel;
- az async teleport sikertelen.

Világ átnevezése, cseréje vagy újragenerálása után ezt az útvonalat külön
teszteld. A GUI 28. slotja az online játékos aktuális helyére teleportál,
a 29. slot a mentett kijelentkezési helyet használja. Egyik útvonalnak
sincs külön auditlogja.

## 10. Moderációs GUI

### 10.1. Játékoslista

`/moderation` vagy `/mod` 54 slotos listát nyit:

- az első 45 slotban az online, viewer számára látható játékosok vannak,
  ábécésorrendben;
- nincs lapozás, ezért 45-nél több látható játékos esetén a további célokhoz
  használd a `/moderation <név>` vagy a közvetlen parancsot;
- 49. slot: bezárás.

### 10.2. Céljátékos műveletei

| Slot | Művelet | Permission | Tényleges route / különbség |
|---:|---|---|---|
| 10 | Figyelmeztetés | `icesmp.moderation.warn` | `/warn <cél> Moderációs GUI` |
| 11 | 30 perces mute | `icesmp.moderation.mute` | `/mute <cél> 30m Moderációs GUI` |
| 12 | Végleges ban | `icesmp.moderation.ban` | `/ban <cél> Moderációs GUI` |
| 13 | Kick | `icesmp.moderation.kick` | `/kick <cél> Moderációs GUI` |
| 14 | Unmute | `icesmp.moderation.mute` | `/unmute <cél> Moderációs GUI` |
| 15 | Unban | `icesmp.moderation.ban` | `/unban <cél> Moderációs GUI` |
| 19 | Teljes history | `icesmp.moderation.history` | `/history <cél>` |
| 20 | Aktív punishment | `icesmp.moderation.history` | `/punishments <cél>` |
| 21 | Reportlista | `icesmp.admin.moderation` | Globális `/reports`, nem célszűrt |
| 22 | Main inventory read | `icesmp.moderation.inventory.read` | `/invsee <cél> read main` |
| 23 | Main inventory edit | `icesmp.moderation.inventory.edit` | `/invsee <cél> edit main` |
| 24 | Ender chest read | `icesmp.moderation.inventory.read` | `/invsee <cél> read ender` |
| 25 | Ender chest edit | `icesmp.moderation.inventory.edit` | `/invsee <cél> edit ender` |
| 28 | Teleport online célhoz | `icesmp.moderation.offlinetp` | Közvetlen GUI-művelet; nincs parancsalternatívája és külön auditja |
| 29 | Utolsó kijelentkezési hely | `icesmp.moderation.offlinetp` | `/offlinetp <cél>` |
| 30 | SocialSpy kapcsoló | `icesmp.moderation.socialspy` | A viewert, nem a kiválasztott célt kapcsolja |
| 31 | Cél vanish kapcsoló | `icesmp.moderation.vanish` | `/vanish <cél>` |
| 49 | Vissza | — | Online játékoslista |
| 53 | Bezárás | — | GUI bezárása |

A GUI csak a viewernek engedélyezett ikonokat rajzolja ki, és kattintáskor
ismét ellenőrzi a permissiont. A legtöbb gomb a normál parancsot hívja,
tehát ugyanazt a validációt és auditot kapja.

A GUI nem kér egyedi indokot vagy időtartamot: büntetésnél az indok
`Moderációs GUI`, a mute fix 30 perc. Egyedi ügyhöz használd a parancsot.

Ha a cél közben kilép, a GUI a 29. slotos offline teleport kivételével
bezárul és hibát jelez.

## 11. Audit és persistence

| Állomány / nyom | Mit tárol | Írási viselkedés | Fontos korlát |
|---|---|---|---|
| `moderation-data.yml` | Punishment ledger, SocialSpy UUID-k, vanished UUID-k, utolsó kijelentkezési helyek | Mutáció előtt snapshot; atomi mentés; hiba esetén memóriarollback; kritikus írási hiba lezárja az új mutációkat és plugin-disable-t kezdeményez | Nincs schema migration; ne szerkeszd élő szerver mellett |
| `reports.yml` | Reportok és offline bejelentői visszajelzések | Atomi fájlcsere | A reportmutáció nem kap a punishment ledgerrel azonos snapshot/rollback és kritikus circuit-breaker garanciát |
| `invsee-escrow.yml` | Visszaadandó itemstackek admin UUID szerint | Közös autosave és kontrollált shutdown-save; szigorú struktúraellenőrzés | Azonnali crashnél nincs cross-store/playerdata exactly-once garancia |
| `logs/moderation-audit.log` | Sikeres invsee edit összefoglalója | Aszinkron, append, best-effort | Hiba nem fordítja vissza az editet; nincs teljes itemmeta |
| `logs/chat-moderation.log` | Moderált chat és több natív PM-kimenet | Aszinkron, append, egy `.1` rotáció | Hiba nem fordítja vissza a chatdöntést; érzékeny üzenetszöveget tartalmazhat |
| Szerverkonzol | Fail-closed, save, scheduler és recovery hibák | Runtime log | A logrotáció és külső logmegőrzés az üzemeltetési környezet feladata |

Az autoritatív YAML-írás ideiglenes fájlt, fájl-fsyncet, lehetőség szerint
atomi replace-t és könyvtár-fsyncet használ. Az atomic move támogatásának
hiányán kívüli valódi hibát nem álcázza egyszerű fallbackként.

Sérült YAML esetén a rendszer byte-megőrző
`<fájlnév>.corrupt-<epoch>` karanténmásolatot próbál készíteni, letiltja az
érintett path további írását és megszakítja az indulást. A reportok
egyes szemantikailag hibás rekordmezői ugyanakkor átugorhatók; ezért
gyanúsan hiányos reportlista esetén a fájlt és a startup logot is vizsgáld.

## 12. Reload és shutdown

### 12.1. `/icesmp reload`

A plugin reload:

- újratölti a configot és az üzeneteket;
- új validált moderációs config-snapshotot készít;
- bezárja az élő invsee sessionöket, és visszaadja vagy escrow-ba teszi a
  mozgásban lévő itemeket;
- újraszámolja a vanish-láthatóságot;
- más reloadképes IceSMP-rendszereket is frissít.

Nem tölti újra a `moderation-data.yml`, `reports.yml` vagy
`invsee-escrow.yml` tartós állományokat. Ezek kézi szerkesztése után a
reload nem elég, és élő szerver mellett egyébként sem biztonságos a
szerkesztés.

### 12.2. Kontrollált leállítás

Disablekor:

1. leáll az expiry-feladat;
2. a rendszer lezárja az új moderation mutációk és invsee editek
   befogadását;
3. legfeljebb 10 másodpercet vár a már befogadott moderation és invsee
   műveletek kifutására;
4. lezárja az autosave-kaput;
5. rendezi az invsee és vanish transient állapotot;
6. végső közös mentést végez;
7. ezután takarítja a játékossessionöket.

Ha bármely 10 másodperces drain nem fejeződik be, a core megtagadja a
végső shutdown-save-et és súlyos hibát ír a konzolra. Ilyen leállás után a
következő startup előtt recovery-ellenőrzés kell.

Használj kontrollált server stopot. Az azonnali process kill nem kapja meg
ezeket a garanciákat.

## 13. Recovery runbook

### 13.1. Sérült `moderation-data.yml` vagy `invsee-escrow.yml`

1. Ne töröld és ne írd felül az eredeti fájlt.
2. Állítsd le a szervert; ellenőrizd, hogy nem maradt futó Java process.
3. Másold ki az eredetit, a `.corrupt-*` példányt, a szerverlogot és a
   legutóbbi jó backupot.
4. Állapítsd meg, hogy szintaktikai vagy sémahiba történt.
5. Offline környezetben javíts vagy állíts vissza.
6. Indíts staging példányt, és ellenőrizd a historyt, aktív ban/mute
   állapotot, vanish/SocialSpy state-et vagy az escrow darabszámot.
7. Csak ezután indíts productiont.

### 13.2. Kritikus lemezírási hiba

`moderation-data.yml` kritikus írási hibájánál a rendszer visszagörgeti az
adott memóriamutációt, lezárja az új moderation műveleteket és
plugin-disable-t kezdeményez.

Teendő:

- állítsd le kontrolláltan a szervert;
- ellenőrizd a szabad helyet, jogosultságot, I/O hibát és a fájlrendszert;
- őrizd meg a logot és a state fájlt;
- ne ismételd vakon a moderációs parancsot;
- javítás után stagingen hasonlítsd össze a historyt a ticketekkel.

### 13.3. Report-mentési hiba

A reportstore atomi fájlírást használ, de a report létrehozása/lezárása nem
kap teljes memóriasnapshot-visszagörgetést és kritikus
plugin-disable-kaput. Írási hiba után a memória és a lemez eltérhet.

Teendő:

- állítsd le a reportfeldolgozást;
- mentsd a konzollogot és a `reports.yml` fájlt;
- kontrollált restart előtt egyeztesd a bejelentéseket;
- ellenőrizd, hogy a bejelentő kapott-e visszajelzést;
- ne jelöld bizonyítottnak a lezárást pusztán a parancsvisszajelzésből.

### 13.4. Invsee ismeretlen cél-slot állapot

Ha egy editnél a rendszer sem a cél slot előtti, sem az utána szándékolt
állapotot nem tudja bizonyítani, duplikáció elkerülésére megtagadja az
automatikus item-visszaadást, lezárja az editbefogadást és plugin-disable-t
kezdeményez.

Ez **MANUAL_REVIEW** incidens:

1. fagyaszd be az érintett admin és cél inventorymódosításait;
2. őrizd meg a teljes konzollogot, játékosadatot, escrow-t és auditlogot;
3. azonosítsd a target UUID-t, nézetet és raw slotot;
4. hasonlítsd össze az admin és a cél aktuális itemjét a legutóbbi
   mentéssel;
5. csak egy bizonyított tulajdonosnak adj vissza itemet;
6. dokumentáld a döntést és az item teljes metaadatát.

### 13.5. Abrupt crash invsee edit körül

Ne következtesd az auditlog hiányából, hogy az edit nem történt meg, és az
auditlog meglétéből sem, hogy az escrow-save már tartós volt.

Az egyeztetési sorrend:

1. playerdata és legutóbbi backup;
2. `invsee-escrow.yml`;
3. `moderation-audit.log`;
4. szerverkonzol időrendje;
5. admin és cél vallomása/ticketje;
6. kézi, dokumentált döntés.

## 14. Deployment előtti playtest

Minden sorhoz rögzíts dátumot, tesztelőt, build SHA-t és bizonyítéklinket.

| ID | Teszt és felelős | Előkészítés | Elvárt eredmény | Hiba esetén | Bizonyíték |
|---|---|---|---|---|---|
| MOD-01 | Warning + kick — Moderátor | Két online tesztfiók, külön leaf permissionök | A warning értesít és historyba kerül; kick csak mentés után bont sessiont | Külső plugin marad; log + ledger mentése | Parancskimenet, `/history`, `moderation-data.yml` backup |
| MOD-02 | Mute és eszkaláció — Moderátor/Tesztelő | Tiszta előzményű, majd ismételten némított fiók | 5/30/180/1440 perces bundled lépcsők; public chat és natív PM blokkol | Rollout stop; config és ledger vizsgálat | Videó/log, `/history`, `/punishments` |
| MOD-03 | Időparser — Tesztelő | Tesztfiók | `30`, `30m`, `45s`, `2h`, `7d`, `2w` jó; `400d` és hibás suffix elutasítva | Ne engedélyezd a moderátori használatot | Parancskimenet |
| MOD-04 | Temp expiry + restart — Üzemeltető | Rövid temp mute és temp ban, kontrollált restart | Lejárat után nincs enforcement; restart előtt/után konzisztens state | Backup visszaállítás, ledger vizsgálat | Prelogin/chat teszt, state diff |
| MOD-05 | Ban enforcement — Admin | Online és offline ismert cél | Ban blokkolja a következő prelogint; unban után beléphet | Külső ban plugin marad | Kliensvideó, prelogin üzenet, history |
| MOD-06 | Unmute/unban audit — Senior moderátor | Aktív mute és ban | Külön feloldási rekord és kétirányú kapcsolat az eredetihez | Ne módosíts YAML-t kézzel | `/history`, state-részlet |
| MOD-07 | Corrupt state — Üzemeltető | Másolaton szándékosan sérült `moderation-data.yml` | Indulás megszakad, karanténmásolat készül, írás nem folytatódik | Production deployment stop | Startup log, `.corrupt-*` hash |
| MOD-08 | Lemezhiba — Üzemeltető | Fault-injection: ENOSPC vagy írásmegtagadás | Mutáció rollback, admission zárás, plugin-disable kezdeményezés | Filesystem javítás + teljes recovery | Konzollog, előtte/utána state |
| REP-01 | Report lifecycle — Moderátor | Online és offline bejelentő | 3 szó ellenőrzés, cooldown, adminértesítés, resolve, reconnect feedback | Report workflow ne kerüljön élesbe | `reports.yml`, kliensvideó |
| REP-02 | Report I/O hiba — Üzemeltető | Nem-production fault-injection | Hiba látható; eltérés manuálisan felismerhető és egyeztethető | Reportfogadás stop | Konzollog, file hash/diff |
| PM-01 | PM + reply — Moderátor/Tesztelő | Két játékos és egy SocialSpy fiók | Siker csak tényleges delivery után; kétirányú `/reply` | Külső PM/SocialSpy marad | Három kliens videója, chatlog |
| PM-02 | Quit–reconnect race — Tesztelő | Címzett kilép kézbesítés közben, majd reconnect | Nincs hamis siker és nincs stale reply-partner | Rollout stop | Időzített kliens/log bizonyíték |
| PM-03 | Mute/filter/spam/SocialSpy — Moderátor | Minden blokkállapot külön előkészítve | Helyes státusz és enforcement; permission elvesztése után spy nem kap új sort | Permission/config javítás | SocialSpy-kimenet és chatlog |
| VAN-01 | Láthatósági mátrix — Admin | Vanish admin, see-jogos és see-jog nélküli viewer | Helyes hide/show, join/quit elnyomás, relog utáni state | Külső vanish marad | Kliensvideók, permission dump |
| VAN-02 | Gameplay + count — Admin/Tesztelő | Pickup, damage, projectile, block/entity interact, mob, MOTD/tablista | Bundled policy szerint minden tiltás és számlálás helyes | Config/route vizsgálat | Videó, server-list screenshot, tablista |
| INV-01 | Read/edit main és ender — Vezető admin | Egyedi metaadatú itemek, részben teli inventory | Read nem módosít; edit cursor-swap; slotok és audit helyes | InvSee++ marad | Videó, auditlog, előtte/utána inventory |
| INV-02 | Full inventory overflow — Vezető admin | Admin inventory és kurzor tele | Kiszorított item nem vész el; dokumentált fallback/drop | Azonnali rollout stop | Videó, item darabszám |
| INV-03 | Viewer/target quit + reconnect — Tesztelő | Edit közben mindkét kilépési sorrend | Pontosan egy visszaadás kontrollált lifecycle-ban | Escrow és playerdata manual review | Escrow snapshot, kliensvideó |
| INV-04 | Reload/disable — Üzemeltető | Nyitott read és edit session, mozgásban lévő item | Session bezár; item visszatér vagy escrow-ba kerül; restart után recovery | Ne távolítsd el InvSee++-t | Pre/post state hash, log |
| INV-05 | Abrupt crash — Üzemeltető | Eldobható tesztszerver, process kill több időablakban | A dokumentált garanciahatár reprodukálható; recovery runbook végrehajtható | Production edit tiltása | Playerdata/escrow/audit idővonal |
| TP-01 | Offline teleport — Admin/Builder | Normál, hiányzó, átnevezett és UUID-cserélt világ | Csak egyező, betöltött világba teleport; nincs sync load | Pontok/world mapping javítása | Videó és konzollog |
| PERM-01 | Permissionmátrix — Üzemeltető | Nem-OP fiókok szerepkörönként | Parancs, tab completion, GUI ikon és kattintás ugyanazt a határt tartja | Permission rollout stop | LuckPerms export + képernyőkép |
| LIFE-01 | Kontrollált shutdown — Üzemeltető | Folyamatban lévő moderation és invsee műveletek | Admission lezár, drain befejeződik, végső save lefut | Súlyos log esetén recovery ellenőrzés | Shutdown log és state hash |

## 15. Ismert korlátok

- A natív rendszer runtime átvételi tesztre vár; CI és regressziós teszt nem
  bizonyítja a valódi Folia scheduler-ownershipot vagy a production
  fájlrendszert.
- Az invsee kizárólag online inventoryt és ender chestet kezel.
- Abrupt process crashnél az invsee nem garantál cross-store exactly-once
  itemátadást.
- A moderation- és chat-audit szövegfájl best-effort, nem autoritatív
  tranzakciós journal.
- A report lezárásához nincs indok és külön lezárási idő.
- A moderációs játékoslista nem lapozható, és legfeljebb 45 látható online
  játékost mutat.
- A SocialSpy csak a natív IceSMP privátüzenet-parancsokat látja.
- Vanish nem tiltja automatikusan a vanished admin chatjét és parancsait.
- Offline teleport nem végez veszélyvizsgálatot, világ- vagy chunkbetöltést.
- Az élő szerver külső configja, permission-adatbázisa és state fájljai nem
  voltak a repository-forrásaudit részei; az éles állapotot staging
  migrációval kell bizonyítani.

## 16. Üzemeltetési gyorslista

### Műszak elején

- Ellenőrizd a nyitott `/reports` listát.
- Ellenőrizd az aktív `/punishments` listát.
- Nézd meg, van-e persistence, audit vagy scheduler warning a konzolban.
- SocialSpy használatakor ellenőrizd a jogosultságot és az adatkezelési
  indokot.

### Büntetés előtt

- Oldd fel pontosan a célt; offline névnél ellenőrizd az UUID-t.
- Nézd meg a `/history` oldalt.
- Használj tárgyszerű okot.
- Válassz időtartamot explicit módon, ha nem az eszkalációt akarod.

### Inventory edit előtt

- Legyen incidens- vagy ticketazonosító.
- Ellenőrizd, hogy a cél online és stabil kapcsolatú.
- Rögzíts előtte állapotot egyedi itemnél.
- Egyszerre csak egy admin szerkessze a cél inventoryját.
- Edit után ellenőrizd az auditlogot és az item darabszámát.

### Műszak végén / deploymentkor

- Ne maradjon feldolgozatlan manual-review incidens.
- Ne legyen aktív invsee edit kontrollált stop előtt.
- Őrizd meg a releváns logokat és state-backupot.
- Súlyos drain-, corrupt- vagy write-failure log esetén ne tekintsd a
  leállást tisztának.

## 17. Forrás- és tesztbizonyíték

A kézikönyv állításait a következő végleges forrásútvonalak támasztják alá:

- bootstrap és command wiring:
  `src/main/java/hu/taliann/icesmp/core/IceSMPCore.java`;
- permissiongráf:
  `src/main/java/hu/taliann/icesmp/core/Permissions.java`;
- büntetés, chat, PM-state és tartós moderáció:
  `src/main/java/hu/taliann/icesmp/managers/ModerationManager.java`;
- punishment ledger és duration parser:
  `src/main/java/hu/taliann/icesmp/moderation/`;
- reportstore és admin routing:
  `src/main/java/hu/taliann/icesmp/managers/ReportManager.java`,
  `src/main/java/hu/taliann/icesmp/commands/ReportCommand.java`,
  `src/main/java/hu/taliann/icesmp/commands/ReportsCommand.java`;
- PM és SocialSpy:
  `src/main/java/hu/taliann/icesmp/commands/PrivateMessageCommand.java`;
- vanish:
  `src/main/java/hu/taliann/icesmp/managers/VanishManager.java` és a
  kapcsolódó listenerek;
- invsee és escrow:
  `src/main/java/hu/taliann/icesmp/managers/InvseeManager.java` és a
  kapcsolódó GUI/listener útvonalak;
- persistence primitive:
  `src/main/java/hu/taliann/icesmp/storage/`;
- bundled moderation config:
  `src/main/resources/config/moderation.yml`;
- automatizált regresszió:
  `src/regression/java/hu/taliann/icesmp/moderation/ModerationRegressionSuite.java`
  és `ModerationReviewRegressionSuite.java`.

Az automatizált suite többek között restart/expiry, revocation-link,
malformed state, escrow ownership és rollback, shutdown gate,
duration-parser, reply reconnect-interleaving, permission/visibility és
scheduler-rejection ágakat vizsgál. A [release acceptance
checklist](#release-acceptance-checklist) kézi runtime
bizonyítékai ettől még kötelezőek.


---

## Teljes parancsreferencia

A napi munkához nem 286 route bemagolása kell, hanem annak ismerete, melyik
eszköz milyen felelősséggel jár. A pontos root/subcommand/alias routingot a
`Repository Docs Inventory` CI-artifact gépileg generálja és ellenőrzi.

| Terület | Legfontosabb parancsok | Mire használd? |
|---|---|---|
| Alaprendszer | `/icesmp`, `/menu`, `/profile`, `/stats`, `/hud` | állapot, reload, config, játékosnézet |
| Moderáció | `/warn`, `/kick`, `/mute`, `/tempban`, `/ban`, `/unmute`, `/unban`, `/history`, `/punishments`, `/reports` | dokumentált staffintézkedés |
| Láthatóság és kommunikáció | `/msg`, `/tell`, `/w`, `/reply`, `/socialspy`, `/vanish`, `/moderation` | PM, megfigyelés, staffjelenlét |
| Inventory és hely | `/invsee`, `/offlinetp` | online inventory read/edit és utolsó ismert hely |
| Tartalomadmin | `/events`, `/quest`, `/npcbind`, `/territory`, `/parkour`, `/crate`, `/iceitem` | esemény-, világ-, crate- és itemkezelés |
| Karakter és gazdaság | `/class`, `/spec`, `/profession`, `/currency`, `/faction`, `/relic`, `/sinner` | ritka, naplózandó játékosmutáció |
| Játékosrendszerek | `/afk`, `/sit`, `/party`, `/claim`, `/market`, `/bank`, `/spellbook`, `/talent` és a többi publikus root | a játékoskézikönyv szerinti használat |

### Magas kockázatú szintaxisok

| Parancs | Biztonságos használat |
|---|---|
| `/invsee <játékos> read <main|ender>` | megtekintés, tartalommódosítás nélkül |
| `/invsee <játékos> edit <main|ender>` | csak bizonyítékkal és escrow/recovery ismeretében |
| `/offlinetp <játékos>` | a staff teleportál a cél utolsó ismert helyére; nem a célt mozgatja |
| `/crate set <id>` / `/crate remove` | csak stagingen ellenőrzött blokk- és világkötéshez |
| `/territory setcapital <frakció> selection [név...]` | a `/claim pos1` + `/claim pos2` pontos X/Y/Z dobozát teszi védett fővárossá; előbb ellenőrizd a személyes claim-konfliktust és utána a `/territory show` rajzot |
| `/icesmp reload` | configmentés és validáció után; strukturális változásnál restart kellhet |
| `/faction set`, `/currency set`, `/relic give`, `/iceitem` | gazdasági vagy progressionmutáció; mindig jegyezd fel |

### Bizonyított eltérések a régi leírásoktól

- A `/class givecatalyst` aktív route-ja `icesmp.admin.job` jogot kér; nem játékos-önkiszolgáló út.
- Az aktív `/mute` a közös moderációs action handlert használja; `/mute list` nincs.
- A `/bank withdraw` parser a `dark` értéket is elfogadja, de a beépített usage és tab csak
  `red`, `blue`, `neutral` értéket mutat.
- `/events chronicle` nincs a tényleges dispatchben.
- `/msg`, `/tell` és `/w` három külön root ugyanazzal a handlerrel; csak `/reply` valódi aliasa az `/r`.

---

## Permissionreferencia

### Gyors kiosztási szabályok

- `icesmp.admin.all` csak vezető adminnak/üzemeltetőnek való.
- A `icesmp.admin.moderation` csomag minden moderációs leaf node-ot megad; finomhangolt csapatnál inkább leaf node-okat ossz.
- `inventory.edit`, `admin.currency`, `admin.crate`, `admin.item`, `admin.territory.bypass` különösen érzékeny.
- A legacy node-ok működnek, de új kiosztásnál a kanonikus `icesmp.admin.*` neveket használd.
- A crate-definíciók tetszőleges, `icesmp.` prefixű plusz node-ot regisztrálhatnak default `FALSE` értékkel.

### Teljes node-lista (44)

| Node | Leírás | Célközönség | Command | GUI | Listener/service | Parent | Default | Érzékenység | Javasolt kiosztás | Deployed változás |
|---|---|---|---|---|---|---|---|---|---|---|
| `icesmp.admin.all` | Az összes kanonikus admin-node szülője. | Vezető admin | — | Admin panel minden jogosultságfüggő eleme | — | — | OP | kritikus | Csak vezető admin/üzemeltető | Új |
| `icesmp.admin.reload` | Plugin config és üzenetek reloadja; az /icesmp gyökér kapuja is. | Admin | /icesmp, /icesmp reload | Admin panel: reload | — | icesmp.admin.all | OP | magas | Admin | Megváltozott |
| `icesmp.admin.config` | Élő config override és Config GUI. | Vezető admin | /icesmp config * | Config menü | ConfigMenuGUIListener | icesmp.admin.all | OP | kritikus | Szűk üzemeltetői kör | Új |
| `icesmp.admin.events` | Világesemények kézi indítása és spawnpontkezelés. | Eventes/Admin | /events adminágak | Esemény/Admin menü | — | icesmp.admin.all | OP | magas | Eventes és admin | Megváltozott |
| `icesmp.admin.npc` | NPC-kötések kezelése. | Admin/Builder | /npcbind * | Admin menü | NpcInteractionListener | icesmp.admin.all | OP | magas | NPC-t kezelő builder/admin | Megváltozott |
| `icesmp.admin.quest` | Quest admin és builder. | Admin/Eventes | /quest complete; /quest admin * | Quest builder | QuestBuilderListener | icesmp.admin.all | OP | magas | Quest designer/admin | Megváltozott |
| `icesmp.admin.parkour` | Parkour pálya létrehozása/törlése. | Builder/Admin | /parkour setstart/setfinish/remove | — | — | icesmp.admin.all | OP | magas | Builder | Megváltozott |
| `icesmp.admin.exchangeboard` | Árfolyamtábla kezelése. | Builder/Admin | /exchangeboard | Admin menü | — | icesmp.admin.all | OP | magas | Builder/admin | Megváltozott |
| `icesmp.admin.territory` | Territórium- és claim-admin. | Builder/Admin | /territory *; /claim admin unclaim | Claim/Admin menü | SelectionWandListener | icesmp.admin.all | OP | kritikus | World designer/vezető admin | Megváltozott |
| `icesmp.admin.territory.bypass` | Claim- és régióvédelem teljes megkerülése. | Vezető admin | — | — | ClaimProtectionListener; TheftListener; TerritoryProtectionService | icesmp.admin.all | OP | kritikus | Csak vezető admin | Megváltozott |
| `icesmp.admin.spec` | Más játékos specializációjának resetje. | Admin | /spec reset | — | — | icesmp.admin.all | OP | magas | Admin | Megváltozott |
| `icesmp.admin.profession` | Szakma adminmutációk. | Admin | /profession blueprint/set/clear/addxp | — | — | icesmp.admin.all | OP | magas | Admin | Megváltozott |
| `icesmp.admin.job` | Kaszt, XP, spell és katalizátor adminmutációk. | Admin | /class * | — | — | icesmp.admin.all | OP | kritikus | Admin | Új |
| `icesmp.admin.currency` | Játékosegyenleg beállítása. | Vezető admin | /currency set | — | — | icesmp.admin.all | OP | kritikus | Gazdasági admin | Új |
| `icesmp.admin.faction` | Frakció, király és kassza admin. | Vezető admin | /faction set; king set/clear; treasury | Frakció menü | — | icesmp.admin.all | OP | kritikus | Vezető admin | Új |
| `icesmp.admin.relic` | Relikvia adminátadás. | Vezető admin | /relic give | — | — | icesmp.admin.all | OP | kritikus | Vezető admin | Új |
| `icesmp.admin.sinner` | Bűnállapot adminmutáció. | Admin | /sinner | — | — | icesmp.admin.all | OP | magas | Admin | Új |
| `icesmp.admin.war` | Hadiablak kézi vezérlése. | Eventes/Admin | /faction war start/stop | — | — | icesmp.admin.all | OP | magas | Eventes/vezető admin | Új |
| `icesmp.admin.crate` | Crate hely, kulcs, stat és recovery admin. | Vezető admin | /crate set/remove/give/list/stats/resetstats/status | — | CrateListener | icesmp.admin.all | OP | kritikus | Crate-admin | Új |
| `icesmp.admin.inspect` | Összesített játékosinspektor. | Admin | /icesmp inspect | — | — | icesmp.admin.all | OP | magas | Admin | Új |
| `icesmp.admin.item` | Bármely natív/plugin item kiadása. | Fejlesztő/vezető admin | /iceitem | Admin menü | — | icesmp.admin.all | OP | kritikus | Csak fejlesztő/vezető admin | Új |
| `icesmp.territory.builder` | Építés a védett zónákban teljes admin-bypass nélkül. | Builder | — | — | TerritoryProtectionService | icesmp.admin.all | OP | magas | Megbízható builder | Megváltozott |
| `icesmp.admin.moderation` | Moderációs csomag szülőnode; a report admin is közvetlenül használja. | Moderátor/Admin | /reports | Moderációs GUI reports gomb | Moderation/Report listenerek | icesmp.admin.all | OP | kritikus | Moderátori szerepcsomag | Új |
| `icesmp.moderation.warn` | Figyelmeztetés. | Moderátor | /warn | Moderációs GUI 10 | — | icesmp.admin.moderation | OP | magas | Moderátor | Új |
| `icesmp.moderation.kick` | Kirúgás. | Moderátor | /kick | Moderációs GUI 13 | — | icesmp.admin.moderation | OP | magas | Moderátor | Új |
| `icesmp.moderation.mute` | Némítás és feloldás. | Moderátor | /mute, /unmute | Moderációs GUI 11/14 | Chat/PM mute enforcement | icesmp.admin.moderation | OP | magas | Moderátor | Új |
| `icesmp.moderation.ban` | Ban/tempban/unban. | Admin/Moderátor | /ban, /tempban, /unban | Moderációs GUI 12/15 | Login ban enforcement | icesmp.admin.moderation | OP | kritikus | Senior moderátor/admin | Új |
| `icesmp.moderation.history` | History és aktív punishmentek. | Moderátor | /history, /punishments | Moderációs GUI 19/20 | — | icesmp.admin.moderation | OP | közepes | Moderátor | Új |
| `icesmp.moderation.socialspy` | SocialSpy állapot. | Moderátor | /socialspy | Moderációs GUI 30 | PrivateMessageCommand | icesmp.admin.moderation | OP | magas | Senior moderátor | Új |
| `icesmp.moderation.vanish` | Vanish kezelése. | Admin | /vanish | Moderációs GUI 31 | VanishManager/listenerek | icesmp.admin.moderation | OP | magas | Admin | Új |
| `icesmp.moderation.vanish.see` | Vanish játékosok láthatósága. | Vezető admin | — | Játékoslista/moderációs célpontszűrés | VanishManager | icesmp.admin.moderation | OP | magas | Admin | Új |
| `icesmp.moderation.offlinetp` | Utolsó logouthelyre teleport. | Moderátor/Admin | /offlinetp | Moderációs GUI 28/29 | Logout location capture | icesmp.admin.moderation | OP | magas | Admin | Új |
| `icesmp.moderation.inventory.read` | Online inventory/ender read. | Moderátor | /invsee ... read | Moderációs GUI 22/24 | InvseeGUIListener | icesmp.admin.moderation | OP | magas | Senior moderátor | Új |
| `icesmp.moderation.inventory.edit` | Online inventory/ender szerkesztés escrow-val. | Vezető admin | /invsee ... edit | Moderációs GUI 23/25 | InvseeGUIListener | icesmp.admin.moderation | OP | kritikus | Csak vezető admin | Új |
| `icesmp.moderation.gui` | Moderációs GUI megnyitása. | Moderátor | /moderation | Moderációs GUI | ModerationGUIListener | icesmp.admin.moderation | OP | közepes | Moderátor | Új |
| `icesmp.crate.use` | Natív crate böngészés, kulcsvásárlás és nyitás alapkapuja. | Játékos | /crate játékoságak; fizikai nyitás | Crate böngésző/spin | CrateListener; CrateManager | — | TRUE | közepes | Minden játékos | Új |
| `icesmp.message` | Natív privát üzenetek. | Játékos | /msg, /tell, /w, /reply | — | PrivateMessageCommand | — | TRUE | alacsony | Minden játékos | Új |
| `icesmp.sit` | Natív /sit és click-to-sit. | Játékos | /sit | — | SitInteractionListener és lifecycle listenerek | — | TRUE | alacsony | Minden játékos | Új |
| `icesmp.admin` | Legacy alias: kaszt- és sinner-admin. | Admin | /class adminágak; /sinner | — | — | — | OP | magas | Migráció után kanonikus node-ok | Megváltozott |
| `icesmp.job.admin` | Legacy alias az icesmp.admin.job node-ra. | Admin | /class * | — | — | — | OP | magas | Csak kompatibilitás | Megváltozott |
| `icesmp.currency.admin` | Legacy alias az icesmp.admin.currency node-ra. | Admin | /currency set | — | — | — | OP | kritikus | Csak kompatibilitás | Megváltozott |
| `icesmp.faction.admin` | Legacy alias az icesmp.admin.faction node-ra. | Admin | /faction adminágak | — | — | — | OP | kritikus | Csak kompatibilitás | Megváltozott |
| `icesmp.relic.admin` | Legacy alias az icesmp.admin.relic node-ra. | Admin | /relic give | — | — | — | OP | kritikus | Csak kompatibilitás | Megváltozott |
| `icesmp.crate.ritka` | A bundled ritka crate konfigurált hozzáférési kapuja. | Játékos/tesztelő | /crate info/preview/buy és fizikai nyitás | Crate böngésző | CrateManager/CrateListener | — | FALSE | közepes | A ritka crate-re jogosult csoport | Új |

### Parent- és kompatibilitási gráf

- `icesmp.admin.all` gyerekei: minden kanonikus admin-domain, a `icesmp.territory.builder` és a `icesmp.admin.moderation` csomag.
- `icesmp.admin.moderation` gyerekei: a 12 `icesmp.moderation.*` leaf node.
- Nem gyereke az `admin.all` node-nak: `icesmp.crate.use`, `icesmp.message`, `icesmp.sit` és a per-crate dinamikus node-ok.
- Legacy: `icesmp.admin` → `icesmp.admin.job` + `icesmp.admin.sinner`; `icesmp.job.admin`, `icesmp.currency.admin`, `icesmp.faction.admin`, `icesmp.relic.admin` → megfelelő kanonikus node.

### Dinamikus crate-node

A release bundled `config/crates.yml` fájljában a `koznapi` crate permissionje üres, a `ritka` crate-é `icesmp.crate.ritka`. A runtime minden valid, `icesmp.` prefixű crate-node-ot `FALSE` defaulttal regisztrál. Ezért élő config módosítása új node-ot hozhat létre; az élő kiosztás a csatolt szerverconfig nélkül nem bizonyítható.

---

## GUI-referencia

### Lefedettség: 22 / 22 aktív GUI-felület

| GUI | Megnyitás | Közönség / jog | Méret | Deployed státusz |
|---|---|---|---|---|
| Főmenü és tematikus parancsmenük | /menu, /achievements, /leaderboard; belső MENU/LB navigáció | Játékos; Admin panel jogosultság szerint / `Nincs a megnyitáshoz; minden célparancs saját jogát ellenőrzi` | 27/36/45/54, nézettől függően | Megváltozott |
| Karakterlap | /profile | Játékos / `—` | 36 | Megváltozott |
| Kasztválasztó | Karakterlap /class kontextusból | Játékos / `—` | 54 | Megváltozott |
| Szakmaválasztó | Karakterlap | Játékos / `—` | 45 | Megváltozott |
| Specializációk | Karakterlap vagy /spec folyamat | Játékos / `—` | 54 | Megváltozott |
| Talent-fa | Karakterlap | Játékos / `—` | 54 | Megváltozott |
| Képességfa | Karakterlap vagy kasztválasztó | Játékos / `—` | 54 | Változatlan |
| Varázskönyv | /spellbook vagy Lélekkapocs interakció | Játékos / `—` | 54 | Megváltozott |
| Szakmai receptkönyv | /profession recipes | Játékos / `—` | 54 | Megváltozott |
| Piactér | /market, /market search, /market ereklye | Játékos / `—` | 54 | Megváltozott |
| Adományláda | /adomany | Játékos / `—` | 54 | Megváltozott |
| Küldetésnapló | /quest log | Játékos / `—` | 54 | Változatlan |
| Quest builder | /quest admin builder <id> | Admin / `icesmp.admin.quest` | TYPE_PICKER 36; EDITOR 54 | Megváltozott |
| NPC/frakció bolt | NPC-kötés/interakció | Játékos / `—` | 9–54, tételszám szerint | Megváltozott |
| Bestiárium | /bestiarium | Játékos / `—` | 27 | Új |
| Megbízottak kezelése | /claim trustgui vagy Claim menü | Játékos / `—` | 54 | Új |
| Config menü | /icesmp config menu vagy admin főmenü | Admin / `icesmp.admin.config` | 36 | Új |
| Crate böngésző és preview | /crate, /crate info, /crate preview | Játékos / `icesmp.crate.use + opcionális crate-specifikus jog` | 54 | Új |
| Crate nyitási animáció | Sikeres crate settlement után automatikusan | Játékos / `A nyitás hozzáférési jogai` | 27 | Új |
| Invsee | /invsee ... | Moderátor/Admin / `icesmp.moderation.inventory.read vagy .edit` | 54 | Új |
| Moderációs GUI | /moderation [játékos] | Moderátor/Admin / `icesmp.moderation.gui + gombonkénti műveleti jog` | 54 | Új |
| Társ GUI | /pet vagy /pet menu | Játékos / `—` | 27 | Új |

### GUI-biztonság

- Kattintáskor mindig újra történjen permission- és célállapot-ellenőrzés.
- Inventory edit, config, crate admin és gazdasági mutáció előtt rögzíts bizonyítékot.
- GUI bezárása, célpont kilépése, reload vagy disable után ne maradjon függő session.
- A 22 aktív GUI teljes holder/listener leltárát a CI-artifact tartalmazza.

## Konfiguráció és reload

Az aktív konfiguráció több `src/main/resources/config/*.yml` fájlból
áll össze; az élő pluginmappában ugyanezek a tartományok felülírhatók.
A teljes, minden pathot, típust, alapértéket és olvasót tartalmazó lista
nem kézi dokumentum: a `Repository Docs Inventory` workflow artifactjában
a `config-keys.md`/`.json` fájlok adják.

Fő adminfelületek:

- `/icesmp reload` — a reloadolható snapshotok, üzenetek és validáció
  frissítése;
- `/icesmp config get|set|unset|list|find` — ellenőrzött runtime
  felülírás;
- konfigurációs GUI — a támogatott adminbeállításokhoz;
- közvetlen YAML-szerkesztés — csak mentéssel és staging-ellenőrzéssel.

Restart kellhet scheduler-periódus, registry/definíció, világ- vagy
integrációs struktúra módosításakor. Hibás típusnál vagy értéknél az
alrendszer fallbacket, warningot vagy letiltást használhat; ezért reload
után mindig ellenőrizd a konzolt.

### Kaszt/spec rework staging-kapu

A rework alapból tiltott: `class-spec-rework.enabled: false`. Bekapcsolás előtt az üzemeltetőnek
telepítenie kell a `class-spec-dependencies.lock.yml` fájlban pontosan rögzített kötelező plugineket,
majd stagingen `-Dpaper.disablePluginRemapping=true` kapcsolóval kell indítania a szervert. Ha az
enforcement aktív és kötelező plugin hiányzik vagy eltérő verziójú, az IceSMP még a tartós gameplay
betöltése előtt leáll. A részletes mentési, hibakezelési és rollbackfolyamat:
`docs/admin/CLASS_SPEC_REWORK_RUNBOOK.md`.

### Konfigurációs fájlok

- `afk.yml`
- `classes.yml`
- `crafting.yml`
- `crates.yml`
- `dev-items.yml`
- `economy.yml`
- `factions.yml`
- `general.yml`
- `item-rarity.yml`
- `loot.yml`
- `moderation.yml`
- `motd.yml`
- `pets.yml`
- `profession-materials.yml`
- `profession-recipes.yml`
- `professions.yml`
- `quests.yml`
- `relics.yml`
- `sit.yml`
- `spells-balance.yml`
- `spells.yml`
- `tablist.yml`
- `world.yml`

### Üzenetfájlok

- `messages/afk.yml`
- `messages/claim.yml`
- `messages/currency.yml`
- `messages/devitem.yml`
- `messages/faction.yml`
- `messages/job.yml`
- `messages/market.yml`
- `messages/moderation.yml`
- `messages/party.yml`
- `messages/pet.yml`
- `messages/profession.yml`
- `messages/quest.yml`
- `messages/relic.yml`
- `messages/sit.yml`
- `messages/spec.yml`
- `messages/spell.yml`
- `messages/system.yml`
- `messages/territory.yml`
- `messages/world.yml`

### Release acceptance checklist

<!-- icesmp-release-document: acceptance-checklist -->

Ez a lista a `master`
`4643ab53586f0c1ee7352df16dcd477013e6fad4` kiadási jelöltjéhez
tartozik. A CI a kódszintű szerződéseket bizonyítja; egyetlen alábbi
runtime pontot sem pipál ki automatikusan.

### Bizonyítékkezelés

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

### Szerepkörönkénti jóváhagyás

Az alábbi hét fejezet külön-külön pipálandó. A jóváhagyó ne csak a négyzetet
jelölje: az alatta megadott bizonyítékhelyet is töltse ki.

#### Szervervezető

- [ ] **Felelős:** szervervezető
- **Előkészítés:** végleges scope, külsőplugin-mátrix, rollbackterv és
  játékoskommunikáció áttekintése.
- **Elvárt eredmény:** a rollout határai, a bent maradó pluginok és a
  visszaállítási döntési pontok írásban elfogadottak.
- **Hiba esetén:** a release nem telepíthető; a hiányzó tulajdonosi döntést
  külön jegyzőkönyvben kell lezárni.
- **Bizonyíték helye:** `leadership/approval/`.

#### Admin

- [ ] **Felelős:** vezető admin
- **Előkészítés:** config snapshot, tesztadat, permissionprofilok, recovery- és
  crate-forgatókönyvek.
- **Elvárt eredmény:** a config, persistence, moderáció, crate és recovery
  releváns tesztsorai bizonyítékkal zártak.
- **Hiba esetén:** az érintett rendszer rolloutját le kell állítani, a state-et
  archiválni és hibajegyet nyitni.
- **Bizonyíték helye:** `admin/approval/`.

#### Moderátor

- [ ] **Felelős:** moderátori vezető
- **Előkészítés:** helper/mod/admin tesztfiókok, punishment-, report-,
  PM/SocialSpy- és vanish-próba.
- **Elvárt eredmény:** a moderációs permissionhatárok, audit és játékosfolyamatok
  a MOD-sorok szerint működnek.
- **Hiba esetén:** a natív moderáció nem válthatja ki az élő rendszert.
- **Bizonyíték helye:** `moderation/approval/`.

#### Builder és world designer

- [ ] **Felelős:** vezető builder/world designer
- **Előkészítés:** staging világmásolat, crate-/event-/boss-/NPC-helyek és
  világpolicy-lista.
- **Elvárt eredmény:** a fizikai helyek, blokkok, világkorlátozások,
  WorldEdit-utáni állapot és NPC-kötések bejárása zöld.
- **Hiba esetén:** az érintett hely nem kerülhet productionbe; koordinátát és
  reprodukciót kell rögzíteni.
- **Bizonyíték helye:** `builder/approval/`.

#### Eventes és tartalomkészítő

- [ ] **Felelős:** eventes/tartalomkészítő
- **Előkészítés:** tesztesemény, bosshely, quest/NPC-kötés, reward- és
  full-inventory tesztkarakter.
- **Elvárt eredmény:** az esemény indítása, lezárása, jutalma és területvédelme
  végigpróbált.
- **Hiba esetén:** az esemény ne kerüljön menetrendbe; az érintett trigger és
  helyszín kerüljön a hibajegybe.
- **Bizonyíték helye:** `events/approval/`.

#### Tesztelő

- [ ] **Felelős:** kijelölt release-tesztelő
- **Előkészítés:** a jelen dokumentum teljes pozitív, negatív, restart- és
  fault-injection mátrixa.
- **Elvárt eredmény:** minden végrehajtott sorhoz várt–kapott eredmény és
  bizonyíték tartozik; a kihagyott sor indokolt.
- **Hiba esetén:** reprodukálható hibajegy készül, az érintett rollout-kapu
  nyitva marad.
- **Bizonyíték helye:** `testing/approval/`.

#### Deploymentet végző személy

- [ ] **Felelős:** deploymentet végző üzemeltető
- **Előkészítés:** mentés, pontos JAR-hash, config- és pluginlista, karbantartási
  ablak, start- és rollback-parancsok.
- **Elvárt eredmény:** a telepítés és smoke test naplózott; a külső plugin csak
  a saját elfogadott kapuja után kerül ki.
- **Hiba esetén:** az előre rögzített rollback fut, az élő state és log
  megőrzésével.
- **Bizonyíték helye:** `deployment/approval/`.

### Moderáció és online admin

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

### Natív MOTD

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

### Sit-only

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

### Natív crate

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

### Globális AFK

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

### Mini-plugin megfelelői

| Kész | Teszt | Felelős | Előkészítés | Elvárt eredmény | Hiba esetén | Bizonyíték |
|---|---|---|---|---|---|---|
| [ ] | MINI-01 Warden XP | Tesztelő | Warden normál és nem játékos kill | csak policy szerinti XP | külső plugin marad | `mini/MINI-01/` |
| [ ] | MINI-02 Player crop trample | Tesztelő | játékos ugrik termésre | configured protection működik | FarmProtect marad | `mini/MINI-02/` |
| [ ] | MINI-03 Mob crop trample | Tesztelő | mob tapos termést | configured protection működik | FarmProtect marad | `mini/MINI-03/` |

### Builder és világ

| Kész | Teszt | Felelős | Előkészítés | Elvárt eredmény | Hiba esetén | Bizonyíték |
|---|---|---|---|---|---|---|
| [ ] | WORLD-01 Crate helyek | Builder/admin | minden final crate block és világ | location, block és world policy egyezik | hely újrakötése | `world/WORLD-01/` |
| [ ] | WORLD-02 Territórium/claim | Builder/admin | határpontok és bypass profil | védelem, trust és zónaszabály helyes | építés stop | `world/WORLD-02/` |
| [ ] | WORLD-02B 3D főváros | Builder/admin | radiusos és claim-kijelöléses főváros stagingen | radius mód változatlan; a hat XYZ-határ, vertikális/világváltás, restart, `/territory show`, claim-konfliktus, kijelölés-életciklus és biztonságos tp/home helyes | rollout stop; hibánál kijelölés megtartása | `world/WORLD-02B/` |
| [ ] | WORLD-03 Quest/NPC | Builder/eventes | minden használt NPC és questhely | FancyNpcs-kötés és fallback út működik | kötés újraépítése | `world/WORLD-03/` |
| [ ] | WORLD-04 Boss/event anchor | Eventes | minden fix spawnhely | biztonságos, nem WG/claim-konfliktusos | anchor eltávolítása | `world/WORLD-04/` |
| [ ] | WORLD-05 WorldEdit/világcsere | Builder | staging másolat utáni bejárás | crate, territory, NPC, ritual, dungeon ép | rollback snapshot | `world/WORLD-05/` |
| [ ] | WORLD-06 Resource pack | Builder/tartalomkészítő | final pack és fallback kliens | ITEM_MODEL helyes, fallback használható | pack rollout stop | `world/WORLD-06/` |

### Deployment

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

### Záró döntés

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


---

---

## Gyors döntési szabály

Ha egy kritikus persistence-, duplikációs, permission-, reconnect-,
world-location- vagy lifecycle-teszt hibás, a release döntése automatikusan
**NO-GO**. A zöld build nem írja felül a hiányzó runtime bizonyítékot.
