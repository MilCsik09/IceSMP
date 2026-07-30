# IceSMP admin-, moderátori és tesztelői kézikönyv

<!-- icesmp-doc-id: guide.admin-and-moderator -->

> Dokumentált HEAD: `4643ab53586f0c1ee7352df16dcd477013e6fad4`
>
> Audit dátuma: 2026-07-30
>
> Deployed baseline: `IceSMP-1.0-TESTING.jar`
> (`da039f0e2bdf0e67b216ce82d7d3fe3b6da0af6e18f6fa175762c37493795a05`);
> valószínű forrásállapot:
> `775d9e247be675db1c7c9beaaecf4a90349bfcd3` (2026-07-12,
> `HIGH_CONFIDENCE`, nem `EXACT`)

Ez a kézikönyv a végleges integrált IceSMP-forrásban ténylegesen
regisztrált natív moderációs és online adminfunkciókat írja le. Adminoknak,
moderátoroknak, tesztelőknek és üzemeltetőknek szól. Nem állít teljes
SModeration- vagy InvSee++-paritást, és nem tekinti a zöld CI-t production
runtime bizonyítéknak.

A deployed IceSMP JAR-ban a dokumentumban szereplő natív moderációs,
report-, privátüzenet-, SocialSpy-, vanish-, invsee- és offline teleport
rendszer nem volt jelen. Ezek ezért az IceSMP bináris baseline-jához képest
**új** képességek. Elképzelhető, hogy az élő szerveren jelenleg külső plugin
biztosít hasonló szolgáltatást; ezt a JAR önmagában nem bizonyítja.

Kapcsolódó teljes referenciák:

- [parancsreferencia](#teljes-parancsreferencia);
- [permissionreferencia](#permissionreferencia);
- [GUI-referencia](#gui-referencia);
- [konfigurációs referencia](#konfiguráció-és-reload);
- [deployed build → release changelog](LATEST_CHANGES.md);
- [release acceptance checklist](#release-acceptance-checklist).

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

### Lefedettség és státuszjelölés

- root parancs: **68 / 68**;
- root alias: **79 / 79**;
- feloldott funkcionális route: **286**;
- korábban dinamikusnak vagy nestednek jelölt, feloldatlan route: **0**;
- `Új`: a deployed JAR nem regisztrálta; `Megváltozott`: a route család jelen volt, de a handler bytecode-ja vagy a routing bővült; `Változatlan`: az aktív handler bytecode-ja egyezik.

### Root parancsok és aliasok

| Root | Aliasok | Rövid cél | Tab | Deployed státusz |
|---|---|---|---|---|
| `/achievements` | `/ach`, `/eleresek` | Elérések (mérföldkövek + jutalmak) | van | Megváltozott |
| `/adomany` | `/adomanylada`, `/donate` | Közösségi adomány-láda | van | Megváltozott |
| `/afk` | — | Önkéntes AFK-jelölés | nincs | Új |
| `/ban` | — | Kitiltás (admin) | van | Új |
| `/bank` | `/vault`, `/wallet` | Bank parancsok | nincs | Változatlan |
| `/bestiarium` | `/bestiary`, `/lajstrom` | Bestiárium — a krónikás-lajstromod | van | Új |
| `/bounty` | `/fejvadasz`, `/korozes` | Körözési lista (fejpénzek) | van | Változatlan |
| `/ceh` | `/gild`, `/guild` | Céh (frakción belüli kisközösség) parancsok | van | Új |
| `/claim` | `/birtok` | Terület-claim parancsok | van | Megváltozott |
| `/class` | `/job`, `/kaszt` | Kaszt (class): szint, Lélekkapocs, admin | nincs | Megváltozott |
| `/crate` | `/crates`, `/ladak` | Láda (crate) parancsok | van | Új |
| `/currency` | `/eco`, `/money` | Valuta parancsok | nincs | Megváltozott |
| `/daily` | `/napi` | Napi küldetés | van | Megváltozott |
| `/emlek` | `/emlekek`, `/memory` | Emlékszilánk-beváltás (visszaemlékezés) | van | Új |
| `/events` | `/esemeny`, `/event` | Világesemény parancsok | van | Megváltozott |
| `/exchangeboard` | `/arfolyamtabla`, `/ratesboard` | Árfolyamtábla admin | van | Megváltozott |
| `/faction` | `/f` | Frakció parancsok | nincs | Megváltozott |
| `/history` | — | Teljes büntetési előzmény (admin) | van | Új |
| `/hud` | — | HUD beállítások | van | Új |
| `/iceitem` | `/icegive`, `/iitem` | Plugin-item kiadása (admin): unique/recept/relikvia/tervrajz/erszeny/dev | van | Új |
| `/icesmp` | `/ismp` | IceSMP admin | van | Megváltozott |
| `/invsee` | — | Online inventory/ender live nézet (admin) | van | Új |
| `/kem` | `/spy` | Kém-álca — rövid felderítő álöltözet | van | Új |
| `/kick` | — | Játékos kirúgása (admin) | van | Új |
| `/komp` | `/ferry` | Kompjárat: átkelés a túlpartra | van | Új |
| `/kronika` | `/chronicle` | Az utolsó Heti Krónika visszaolvasása | nincs | Új |
| `/leaderboard` | `/lb`, `/rangsor`, `/top` | Ranglisták (szint, vagyon, raid-kill) | van | Megváltozott |
| `/lore` | `/kodex` | A kódex lapjai — frakciók és helyek története | van | Új |
| `/market` | `/ah`, `/piac` | Piactér parancsok | van | Megváltozott |
| `/menu` | `/hub`, `/m` | Központi menü — minden parancs egy helyen | van | Megváltozott |
| `/moderation` | `/mod` | Natív moderációs admin GUI | van | Új |
| `/msg` | — | Privát üzenet | van | Új |
| `/mute` | — | Némítás (admin) | van | Új |
| `/npcbind` | `/npckotes` | NPC-kötések: küldetés/bolt/bankár/valutaváltó (admin) | van | Megváltozott |
| `/offlinetp` | — | Teleport az utolsó kijelentkezési helyre | van | Új |
| `/parbaj` | `/duel` | Becsület-párbaj — elégtétel a bűnökért | van | Új |
| `/parkour` | `/palya`, `/trial` | Parkour-pályák (futás, admin beállítás) | van | Megváltozott |
| `/party` | `/p`, `/parti` | Party (csapat) parancsok | van | Megváltozott |
| `/pet` | `/companion`, `/tars` | Társ (befogó item, idézés, név, szint) | van | Megváltozott |
| `/profession` | `/prof`, `/szakma` | Szakma (profession) parancsok | van | Megváltozott |
| `/profile` | `/char`, `/karakter`, `/status` | Karakterlap — kaszt, spec, szakma, talent menük | van | Változatlan |
| `/punishments` | — | Aktív büntetések (admin) | van | Új |
| `/quest` | `/kuldetes`, `/quests` | Küldetés parancsok | van | Megváltozott |
| `/relic` | `/relics`, `/relikvia` | Relikvia parancsok (admin) | van | Megváltozott |
| `/reply` | `/r` | Válasz privát üzenetre | van | Új |
| `/report` | `/bejelent` | Játékos bejelentése (admin: /reports) | van | Új |
| `/reports` | — | Bejelentések kezelése (admin) | van | Új |
| `/sinner` | — | Bűnös állapot kezelése (admin) | van | Megváltozott |
| `/sit` | — | Ülés (leül/feláll) | van | Új |
| `/socialspy` | — | Privát üzenetek megfigyelése (admin) | van | Új |
| `/soulforge` | `/lelekkovacs` | Lélek-kovács — a Nekromanta minion-fejlesztései | van | Új |
| `/souls` | `/lelek`, `/soul` | Lélekszilánk parancsok | van | Megváltozott |
| `/spec` | `/specializacio`, `/specialization` | Specializáció parancsok | van | Megváltozott |
| `/spell` | `/mastery`, `/mesterseg`, `/spells` | Spell-mesterség (cooldown + erő valutáért) | van | Megváltozott |
| `/spellbook` | `/konyv`, `/sb`, `/varazskonyv` | Varázskönyv: spellek böngészése és kiválasztása | van | Megváltozott |
| `/stats` | — | Statisztika-profil | van | Új |
| `/suttogas` | `/sutt` | A Suttogók titkos csatornája és tanú-vád | van | Új |
| `/szakmacel` | `/weeklygoal` | Szakma-céhek heti közös céljai | van | Új |
| `/talent` | `/talentfa`, `/talents` | Talent-fa parancsok | van | Megváltozott |
| `/tanacs` | `/council` | A Menedék Vének Tanácsa: szavazás, Vásár-hét | van | Új |
| `/tell` | — | Privát üzenet | van | Új |
| `/tempban` | — | Ideiglenes kitiltás (admin) | van | Új |
| `/territory` | `/terulet` | Frakció terület parancsok | van | Megváltozott |
| `/unban` | — | Kitiltás feloldása (admin) | van | Új |
| `/unmute` | — | Némítás feloldása (admin) | van | Új |
| `/vanish` | `/v` | Admin láthatatlanság | van | Új |
| `/w` | — | Privát üzenet | van | Új |
| `/warn` | — | Figyelmeztetés (admin) | van | Új |

### Route-ok

A „GUI” oszlop alternatív elérést jelez; a GUI-gomb ugyanazt a command/service kaput használja. A konzol-kompatibilitás az aktív végrehajtási ágból következik, nem az osztály nevéből.

#### `/achievements`

Aliasok: `/ach`, `/eleresek`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/achievements` | Elérések read-only GUI-ja. | Játékos | — | Nincs | Elérések nézet | — | Megváltozott |

#### `/adomany`

Aliasok: `/adomanylada`, `/donate`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/adomany` | Lapozható közösségi adományláda. | Játékos | — | Nincs | Adományláda | — | Megváltozott |
| `/adomany add` | A főkéz teljes stackjének adományozása. | Játékos | — | add | Adományláda hozzáadás gomb | — | Megváltozott |

#### `/afk`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/afk` | A globális kézi AFK-jelölés ki-/bekapcsolása. | Játékos | — | Nincs | — | Nem zónaparancs és nem fizet jutalmat; aktivitás automatikusan visszahoz. | Új |

#### `/ban`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/ban <játékos> [ok...]` | Végleges kitiltás. | Játékos vagy konzol | icesmp.moderation.ban | jogosultság szerint látható online játékosok; időzített műveletnél időminták | Moderációs GUI | Offline, de ismert célpont is használható. | Új |

#### `/bank`

Aliasok: `/vault`, `/wallet`. Deployed státusz: **Változatlan**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/bank` | Bankparancsok súgója. | Játékos | — | balance/deposit/withdraw | — | — | Változatlan |
| `/bank balance` | Minden saját bankegyenleg. | Játékos | — | Nincs | Bank menü | A deposit/withdraw főváros-only lehet. A help és tab nem sorolja a dark értéket, de a parser elfogadja. | Változatlan |
| `/bank deposit` | Fizikai valuta teljes befizetése. | Játékos | — | Nincs | Bank menü | A deposit/withdraw főváros-only lehet. A help és tab nem sorolja a dark értéket, de a parser elfogadja. | Változatlan |
| `/bank withdraw <red\|blue\|neutral\|dark> <összeg>` | Fizikai valuta kivétele. | Játékos | — | red/blue/neutral (a dark elfogadott, de nincs javasolva) | Bank menü | A deposit/withdraw főváros-only lehet. A help és tab nem sorolja a dark értéket, de a parser elfogadja. | Változatlan |

#### `/bestiarium`

Aliasok: `/bestiary`, `/lajstrom`. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/bestiarium` | Fajok, receptek, territóriumok és bossok read-only lajstroma. | Játékos | — | Nincs | Bestiárium | — | Új |

#### `/bounty`

Aliasok: `/fejvadasz`, `/korozes`. Deployed státusz: **Változatlan**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/bounty` | Online körözési lista. | Játékos vagy konzol | — | Nincs | — | — | Változatlan |

#### `/ceh`

Aliasok: `/gild`, `/guild`. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/ceh` | Céhparancsok súgója. | Játékos | — | magyar alparancsok; meghívás/kirúgásnál online nevek | — | — | Új |
| `/ceh letrehoz <név...>`<br>Routing alias: `create` | Céh alapítása. | Játékos | — | magyar alparancsok; meghívás/kirúgásnál online nevek | — | — | Új |
| `/ceh meghiv <online-játékos>`<br>Routing alias: `invite` | Tag meghívása. | Játékos | — | magyar alparancsok; meghívás/kirúgásnál online nevek | — | — | Új |
| `/ceh elfogad`<br>Routing alias: `accept` | Meghívás elfogadása. | Játékos | — | magyar alparancsok; meghívás/kirúgásnál online nevek | — | — | Új |
| `/ceh elhagy`<br>Routing alias: `leave` | Céh elhagyása. | Játékos | — | magyar alparancsok; meghívás/kirúgásnál online nevek | — | — | Új |
| `/ceh kirug <ismert-játékos>`<br>Routing alias: `kick` | Tag kirúgása. | Játékos | — | magyar alparancsok; meghívás/kirúgásnál online nevek | — | — | Új |
| `/ceh befizet <összeg>`<br>Routing alias: `deposit` | Befizetés a céhkasszába. | Játékos | — | magyar alparancsok; meghívás/kirúgásnál online nevek | — | — | Új |
| `/ceh info` | Saját céh részletei. | Játékos | — | magyar alparancsok; meghívás/kirúgásnál online nevek | — | — | Új |
| `/ceh lista`<br>Routing alias: `list` | Top céhek listája. | Játékos | — | magyar alparancsok; meghívás/kirúgásnál online nevek | — | — | Új |

#### `/claim`

Aliasok: `/birtok`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/claim [claim]` | Aktuális chunk gyorsfoglalása. | Játékos | — | alparancs; trustnál online játékos; extendnél up/down | Claim menü | — | Megváltozott |
| `/claim unclaim` | Aktuális saját claim feloldása. | Játékos | — | alparancs; trustnál online játékos; extendnél up/down | Claim menü | — | Megváltozott |
| `/claim info` | Aktuális chunk tulajdonosa és határa. | Játékos | — | alparancs; trustnál online játékos; extendnél up/down | Claim menü | — | Megváltozott |
| `/claim list` | Saját claimek listája. | Játékos | — | alparancs; trustnál online játékos; extendnél up/down | Claim menü | — | Megváltozott |
| `/claim trust <online-játékos>` | Megbízott hozzáadása minden claimhez. | Játékos | — | alparancs; trustnál online játékos; extendnél up/down | Megbízottak GUI | — | Megváltozott |
| `/claim untrust <online-játékos>` | Megbízott eltávolítása. | Játékos | — | alparancs; trustnál online játékos; extendnél up/down | Megbízottak GUI | — | Megváltozott |
| `/claim trustgui` | Megbízottak/közeli játékosok GUI. | Játékos | — | alparancs; trustnál online játékos; extendnél up/down | Megbízottak GUI | — | Új |
| `/claim show` | Claimhatárok kirajzolása. | Játékos | — | alparancs; trustnál online játékos; extendnél up/down | Claim menü | — | Megváltozott |
| `/claim pos1` | Első blokk-pontos sarok. | Játékos | — | alparancs; trustnál online játékos; extendnél up/down | Claim menü | — | Megváltozott |
| `/claim pos2` | Második blokk-pontos sarok. | Játékos | — | alparancs; trustnál online játékos; extendnél up/down | Claim menü | — | Megváltozott |
| `/claim wand`<br>Routing alias: `palca` | Birtokmérő pálca. | Játékos | — | alparancs; trustnál online játékos; extendnél up/down | Claim menü | — | Új |
| `/claim area` | Kijelölt blokkterület foglalása. | Játékos | — | alparancs; trustnál online játékos; extendnél up/down | Claim menü | — | Megváltozott |
| `/claim extend [up\|down]` | Függőleges kiterjesztés; alapértelmezés: up. | Játékos | — | alparancs; trustnál online játékos; extendnél up/down | Claim menü | — | Megváltozott |
| `/claim admin unclaim` | Idegen claim törlése az aktuális helyen. | Játékos | icesmp.admin.territory | alparancs; trustnál online játékos; extendnél up/down | Admin menü | — | Megváltozott |
| `/claim help` | Súgó. | Játékos | — | alparancs; trustnál online játékos; extendnél up/down | — | — | Megváltozott |

#### `/class`

Aliasok: `/job`, `/kaszt`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/class` | Kaszt-admin súgó. | Játékos vagy konzol | — | hét admin alparancs | — | — | Megváltozott |
| `/class addxp <online-játékos> <mennyiség>` | Kaszt-XP hozzáadása. | Játékos vagy konzol | icesmp.admin.job | online játékosok | — | A célpontot igénylő ágak csak online játékost fogadnak. | Megváltozott |
| `/class setxp <online-játékos> <mennyiség>` | Kaszt-XP beállítása. | Játékos vagy konzol | icesmp.admin.job | online játékosok | — | A célpontot igénylő ágak csak online játékost fogadnak. | Megváltozott |
| `/class status <online-játékos>` | Célpont kasztállapota. | Játékos vagy konzol | icesmp.admin.job | online játékosok | — | A célpontot igénylő ágak csak online játékost fogadnak. | Megváltozott |
| `/class unlockspell <online-játékos> <spell-id>` | Spell adminfeloldása. | Játékos vagy konzol | icesmp.admin.job | online játékos → spell ID | — | A célpontot igénylő ágak csak online játékost fogadnak. | Megváltozott |
| `/class givecatalyst <online-játékos>` | Lélekkapocs adminátadása. | Játékos vagy konzol | icesmp.admin.job | online játékosok | — | A célpontot igénylő ágak csak online játékost fogadnak. | Megváltozott |
| `/class listspells` | Regisztrált spellek adminlistája. | Játékos vagy konzol | icesmp.admin.job | Nincs | — | A célpontot igénylő ágak csak online játékost fogadnak. | Megváltozott |
| `/class admin resetcd <online-játékos>` | A célpont minden spell-cooldownjának törlése. | Játékos vagy konzol | icesmp.admin.job | adminművelet → online játékos | — | Csak online célpont; tartós adminmutáció. | Megváltozott |
| `/class admin unlockallskills <online-játékos>` | Minden regisztrált spell feloldása a célpontnak. | Játékos vagy konzol | icesmp.admin.job | adminművelet → online játékos | — | Csak online célpont; tartós adminmutáció. | Megváltozott |
| `/class admin resetskills <online-játékos>` | A célpont feloldott spelljeinek törlése. | Játékos vagy konzol | icesmp.admin.job | adminművelet → online játékos | — | Csak online célpont; tartós adminmutáció. | Megváltozott |
| `/class admin resetclass <online-játékos>` | A célpont kasztjának és kapcsolódó fejlődésének resetje. | Játékos vagy konzol | icesmp.admin.job | adminművelet → online játékos | — | Csak online célpont; tartós adminmutáció. | Megváltozott |

#### `/crate`

Aliasok: `/crates`, `/ladak`. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/crate` | Natív ládalista GUI; konzolnak vagy ismeretlen alparancsnál crate-súgó. | Játékos; konzolnak csak súgó | icesmp.crate.use | Nincs | Crate böngésző | Az ismeretlen első argumentum a jogosultságfüggő súgóra esik vissza. | Új |
| `/crate buy <láda-id> [darab]` | Kulcs vásárlása. | Játékos | icesmp.crate.use + opcionális crate-specifikus jog | láda-id-k | Crate böngésző | Darab: 1..a kódbeli biztonsági maximum. | Új |
| `/crate info [láda-id]` | Kulcsár, cooldown, mass-open szabály és esélyek. | Játékos | icesmp.crate.use + opcionális crate-specifikus jog | láda-id-k | Crate böngésző | ID nélkül a böngésző nyílik. | Új |
| `/crate preview <láda-id>` | Jutalom-előnézet megnyitása. | Játékos | icesmp.crate.use + opcionális crate-specifikus jog | láda-id-k | Crate preview | Read-only előnézet. | Új |
| `/crate set <láda-id>` | A legfeljebb 5 blokkra nézett blokk crate-helynek mentése. | Játékos | icesmp.admin.crate | láda-id-k | — | Tartós helymutáció. | Új |
| `/crate remove` | A nézett crate-hely törlése. | Játékos | icesmp.admin.crate | remove | — | A definíciót nem törli. | Új |
| `/crate give <online-játékos> <láda-id> [darab]` | Hiteles PDC-kulcs átadása. | Játékos vagy konzol | icesmp.admin.crate | online játékos → láda-id | — | A célpontnak online kell lennie. | Új |
| `/crate list` | Tartós fizikai crate-helyek listája. | Játékos vagy konzol | icesmp.admin.crate | list | — | — | Új |
| `/crate stats [játékos\|uuid] [láda-id]` | Nyitási statisztikák lekérdezése. | Játékos vagy konzol | icesmp.admin.crate | ismert játékos → láda-id | — | Konzolról a cél kötelező. | Új |
| `/crate resetstats <játékos\|uuid> [láda-id\|all]` | Nyitási stat/cooldown törlése. | Játékos vagy konzol | icesmp.admin.crate | ismert játékos → láda-id/all | — | Tartós adminmutáció. | Új |
| `/crate status` | Valid definíciók, config-hibák és manuális recovery tételek. | Játékos vagy konzol | icesmp.admin.crate | status | — | A MANUAL_REVIEW tételek adminfolyamatot igényelnek. | Új |

#### `/currency`

Aliasok: `/eco`, `/money`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/currency` | Valutaparancsok súgója. | Játékos vagy konzol | — | balance/pay/set/exchange/rates | — | — | Megváltozott |
| `/currency balance [currency]` | Saját bankegyenleg megjelenítése. | Játékos | — | valutatípusok | Bank/valutaváltó menü | A pay alapból kikapcsolható; banki műveletek fővároshoz köthetők. | Megváltozott |
| `/currency pay <online-játékos> <összeg> [currency]` | Közvetlen átutalás. | Játékos | — | online játékos → valutatípus | Bank/valutaváltó menü | A pay alapból kikapcsolható; banki műveletek fővároshoz köthetők. | Megváltozott |
| `/currency set <online-játékos> <összeg> [currency]` | Egyenleg adminbeállítása. | Játékos vagy konzol | icesmp.admin.currency | online játékos → valutatípus | Bank/valutaváltó menü | A pay alapból kikapcsolható; banki műveletek fővároshoz köthetők. | Megváltozott |
| `/currency exchange <összeg> <honnan> <hová>` | Valutaváltás. | Játékos | — | valutatípusok | Bank/valutaváltó menü | A pay alapból kikapcsolható; banki műveletek fővároshoz köthetők. | Megváltozott |
| `/currency rates` | Aktuális árfolyamok. | Játékos vagy konzol | — | Nincs | Bank/valutaváltó menü | A pay alapból kikapcsolható; banki műveletek fővároshoz köthetők. | Megváltozott |

#### `/daily`

Aliasok: `/napi`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/daily` | Napi és heti küldetés állása. | Játékos | — | Nincs | — | — | Megváltozott |

#### `/emlek`

Aliasok: `/emlekek`, `/memory`. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/emlek` | Emlékszilánk-egyenleg és beváltási lehetőségek. | Játékos | — | xp/talent/spec/lore | — | Argumentum nélkül és ismeretlen első argumentumnál is az állapotnézet fut. | Új |
| `/emlek xp` | Szilánk kaszt-XP-re. | Játékos | — | xp/talent/spec/lore | — | — | Új |
| `/emlek talent` | Szilánk bónusz talentpontra. | Játékos | — | xp/talent/spec/lore | — | — | Új |
| `/emlek spec` | Spec szintkapu korai feloldása. | Játékos | — | xp/talent/spec/lore | — | — | Új |
| `/emlek lore` | Véletlen emléktöredék. | Játékos | — | xp/talent/spec/lore | — | — | Új |

#### `/events`

Aliasok: `/esemeny`, `/event`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/events [season]` | Szezon-liga állása. | Játékos vagy konzol | — | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | Az ismeretlen első argumentum is a szezonállásra esik vissza. | Megváltozott |
| `/events status` | Minden aktív esemény és szezonállás. | Játékos vagy konzol | — | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Új |
| `/events blood-moon`<br>Routing alias: `bloodmoon` | Vérhold állapota. | Játékos vagy konzol | — | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Megváltozott |
| `/events blood-moon start`<br>Routing alias: `bloodmoon` | Vérhold kézi indítása. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | A blood-moon/caravan ismeretlen második argumentuma usage hibát ad. | Megváltozott |
| `/events blood-moon stop`<br>Routing alias: `bloodmoon` | Vérhold kézi leállítása. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | A blood-moon/caravan ismeretlen második argumentuma usage hibát ad. | Megváltozott |
| `/events caravan`<br>Routing alias: `karavan` | Kereskedő-karaván állapota. | Játékos vagy konzol | — | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Megváltozott |
| `/events caravan arrive`<br>Routing alias: `karavan`, `start` | Karaván kézi érkeztetése. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | A blood-moon/caravan ismeretlen második argumentuma usage hibát ad. | Megváltozott |
| `/events caravan depart`<br>Routing alias: `karavan`, `stop` | Karaván kézi távoztatása. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | A blood-moon/caravan ismeretlen második argumentuma usage hibát ad. | Megváltozott |
| `/events worldboss`<br>Routing alias: `world-boss`, `boss` | Világboss megidézése. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Megváltozott |
| `/events invasion`<br>Routing alias: `invazio` | Invázió indítása. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Megváltozott |
| `/events ambient`<br>Routing alias: `hangulat` | Hangulatesemény. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Megváltozott |
| `/events gathering`<br>Routing alias: `buff`, `gyujtes` | Gyűjtögető buff-ablak. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Megváltozott |
| `/events treasure`<br>Routing alias: `kincs` | Kincs elrejtése. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Megváltozott |
| `/events wild-hunt`<br>Routing alias: `wildhunt`, `hajsza` | Vad Hajsza indítása. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Megváltozott |
| `/events abundance`<br>Routing alias: `boseg` | Bőség-idő indítása. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Megváltozott |
| `/events challenge`<br>Routing alias: `kihivas` | Szerverkihívás indítása. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Megváltozott |
| `/events escort`<br>Routing alias: `kiseret` | Kíséretesemény indítása. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Megváltozott |
| `/events meteor` | Meteor indítása. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Megváltozott |
| `/events stranger`<br>Routing alias: `idegen` | Rejtélyes Idegen kézi megidézése. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Új |
| `/events corruption`<br>Routing alias: `rontas` | Rontás-góc kézi nyitása. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Új |
| `/events archeology`<br>Routing alias: `regeszet` | Régészeti lelőhely kézi nyitása. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Új |
| `/events cultists`<br>Routing alias: `kultistak` | Kultista esemény kézi indítása. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Új |
| `/events spawnpoint add <world-boss\|escort\|caravan\|cultists\|any> [id]`<br>Routing alias: `spawnpont` | Eseményspawnpont rögzítése itt. | Játékos | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Új |
| `/events spawnpoint remove <id>`<br>Routing alias: `spawnpont`, `torol` | Eseményspawnpont törlése. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Új |
| `/events spawnpoint list`<br>Routing alias: `spawnpont`, `lista` | Eseményspawnpontok listája. | Játékos vagy konzol | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Új |
| `/events intro [online-játékos]` | Intro újrajátszása. | Játékos vagy konzol; konzol csak célponttal | icesmp.admin.events | jogosultságfüggő alparancs; blood-moon/caravan művelet; intro online játékos | Események/admin menü | — | Megváltozott |

#### `/exchangeboard`

Aliasok: `/arfolyamtabla`, `/ratesboard`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/exchangeboard [place]` | Árfolyamtábla elhelyezése az aktuális helyen. | Játékos | icesmp.admin.exchangeboard | place/remove | — | Ismeretlen argumentum is place ágra esik. | Megváltozott |
| `/exchangeboard remove` | Legközelebbi tábla törlése 6 blokkon belül. | Játékos | icesmp.admin.exchangeboard | place/remove | — | — | Megváltozott |

#### `/faction`

Aliasok: `/f`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/faction` | Frakcióparancsok súgója. | Játékos vagy konzol | — | jog szerint szűrt alparancsok | Frakció menü | — | Megváltozott |
| `/faction join <frakció>` | Első frakcióválasztás vagy szabályozott váltás. | Játékos | — | frakciók | Frakció menü | — | Megváltozott |
| `/faction leave` | Kilépés a frakcióból a váltási szabályokkal. | Játékos | — | Nincs | Frakció menü | — | Megváltozott |
| `/faction set <játékos> <frakció>` | Online vagy cache-elt játékos adminbesorolása. | Játékos vagy konzol | icesmp.admin.faction | ismert játékos → frakciók | Frakció menü | — | Megváltozott |
| `/faction treasury` | Saját kassza, adminnak minden kassza megjelenítése. | Játékos | — | withdraw, ha király/admin | Frakció menü | — | Megváltozott |
| `/faction treasury withdraw <összeg>` | Kasszakivét a napi keret szerint. | Játékos | király vagy icesmp.admin.faction | withdraw | Frakció menü | — | Megváltozott |
| `/faction donate <összeg>` | Adomány a saját frakciókasszába. | Játékos | — | Nincs | Frakció menü | — | Megváltozott |
| `/faction king` | Királyok és választási állás. | Játékos vagy konzol | — | vote/tax; adminnak set/clear | Frakció menü | — | Megváltozott |
| `/faction king vote <játékos>` | Szavazat leadása. | Játékos | — | online játékosok | Frakció menü | — | Megváltozott |
| `/faction king tax <százalék>` | Saját királyság adókulcsa. | Játékos | király | tax | Frakció menü | — | Megváltozott |
| `/faction king set <frakció> <online-játékos>` | Király adminbeállítása. | Játékos vagy konzol | icesmp.admin.faction | frakció → online játékos | Frakció menü | — | Megváltozott |
| `/faction king clear <frakció>` | Király admin törlése. | Játékos vagy konzol | icesmp.admin.faction | frakció | Frakció menü | — | Megváltozott |
| `/faction raid <célfrakció> [terület]` | Raid meghirdetése. | Játékos | király | frakció → célterületek | Frakció menü | — | Megváltozott |
| `/faction raid join` | Belépés az aktív raidbe. | Játékos | — | join/status | Frakció menü | — | Megváltozott |
| `/faction raid status` | Raidállás. | Játékos | — | join/status | Frakció menü | — | Megváltozott |
| `/faction caravan send <összeg>` | Játékos-karaván indítása a kasszából. | Játékos | király vagy tanácstag | send | Frakció menü | — | Új |
| `/faction war` | Hadiablak állapota. | Játékos vagy konzol | — | adminnak start/stop | Frakció menü | — | Új |
| `/faction war start [perc]` | Hadiablak kézi indítása. | Játékos vagy konzol | icesmp.admin.war | start/stop | Frakció menü | — | Új |
| `/faction war stop` | Hadiablak kézi leállítása. | Játékos vagy konzol | icesmp.admin.war | start/stop | Frakció menü | — | Új |

#### `/history`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/history <játékos> [oldal]` | Teljes büntetési előzmény lapozva. | Játékos vagy konzol | icesmp.moderation.history | látható online játékosok | Moderációs GUI | — | Új |

#### `/hud`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/hud` | A saját HUD-szekciók állapotának listázása. | Játékos | — | toggle | — | Argumentum nélkül és ismeretlen első argumentumnál is az állapotlista fut. | Új |
| `/hud toggle <frakcio\|valuta\|kaszt\|eroforras\|esemeny\|csapat\|mind>` | Egy HUD-szekció vagy a teljes HUD ki-/bekapcsolása. | Játékos | — | a hét felsorolt szekció | — | — | Új |

#### `/iceitem`

Aliasok: `/icegive`, `/iitem`. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/iceitem` | Jogosultság ellenőrzése után a támogatott itemtípusokat és kötelező argumentumokat mutató usage. | Játékos vagy konzol | icesmp.admin.item | unique/recept/relikvia/tervrajz/erszeny/dev | — | — | Új |
| `/iceitem unique <unique-id> [darab] [online-játékos]` | Regisztrált unique material hiteles példányának kiadása. | Játékos vagy konzol; konzolnál a darab és céljátékos is kötelező | icesmp.admin.item | unique registry-ID → darab → online játékos | — | A darab 1–2304 közé clampelődik; ami nem fér el, a cél lábához esik. | Új |
| `/iceitem recept <recept-id> [darab] [online-játékos]` | A recept eredményének példányonkénti, affix-rollos felépítése és kiadása. | Játékos vagy konzol; konzolnál a darab és céljátékos is kötelező | icesmp.admin.item | recept-ID → darab → online játékos | — | A darab 1–2304 közé clampelődik; buildhiba megszakítja a ciklust. | Új |
| `/iceitem relikvia <relikvia-id> [darab] [online-játékos]` | Regisztrált relikvia hiteles kiadása. | Játékos vagy konzol; konzolnál a darab és céljátékos is kötelező | icesmp.admin.item | relikvia-ID → darab → online játékos | — | A relikviakezelő saját átadási korlátai érvényesülnek. | Új |
| `/iceitem tervrajz <recept-id> [darab] [online-játékos]` | A megadott recepthez tartozó hiteles tervrajz kiadása. | Játékos vagy konzol; konzolnál a darab és céljátékos is kötelező | icesmp.admin.item | blueprint/recept-ID → darab → online játékos | — | Csak a receptkatalógusban létező ID fogadható el. | Új |
| `/iceitem erszeny <pozitív-érték> [darab] [online-játékos]` | Véletlen valutájú kopott erszények kiadása a megadott erszényértékkel. | Játékos vagy konzol; konzolnál a darab és céljátékos is kötelező | icesmp.admin.item | 10/25/50/100 javaslat → darab → online játékos | — | Az érték bármely pozitív long lehet; legfeljebb 64 erszényt ad ki. | Új |
| `/iceitem dev <bingulus-id> [darab] [online-játékos]` | A Csodálatos Bingulus fejlesztői tárgy kiadása. | Játékos vagy konzol; konzolnál a darab és céljátékos is kötelező | icesmp.admin.item | bingulus ID → online játékos | — | Mindig egy darabot ad, kizárólag a konfigurált tulajdonosnak. | Új |

#### `/icesmp`

Aliasok: `/ismp`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/icesmp` | Jogosultság-szűrt admin súgó. | Játékos vagy konzol | icesmp.admin.reload | reload, config; inspect csak külön inspect joggal | — | — | Megváltozott |
| `/icesmp reload` | Minden config és üzenet újratöltése, a reload hookok futtatásával. | Játékos vagy konzol | icesmp.admin.reload | reload | — | — | Megváltozott |
| `/icesmp config menu` | Kurált élő config-szerkesztő megnyitása. | Játékos | icesmp.admin.reload + icesmp.admin.config | config → menu | Config menü | A gyökérparancs előbb a reload jogot is ellenőrzi. | Új |
| `/icesmp config get <kulcs>` | Az összeolvasztott config aktuális értéke. | Játékos vagy konzol | icesmp.admin.reload + icesmp.admin.config | config-kulcsok | — | A gyökérparancs előbb a reload jogot is ellenőrzi. | Új |
| `/icesmp config set <kulcs> <érték...>` | Ingame override beállítása és azonnali validálása. | Játékos vagy konzol | icesmp.admin.reload + icesmp.admin.config | config-kulcs; ismert logikai értéknél true/false | Config menü | A gyökérparancs előbb a reload jogot is ellenőrzi. | Új |
| `/icesmp config unset <kulcs>` | Ingame override törlése. | Játékos vagy konzol | icesmp.admin.reload + icesmp.admin.config | override/config-kulcsok | Config menü | A gyökérparancs előbb a reload jogot is ellenőrzi. | Új |
| `/icesmp config list` | Az ingame override-ok listázása. | Játékos vagy konzol | icesmp.admin.reload + icesmp.admin.config | config → list | Config menü | A gyökérparancs előbb a reload jogot is ellenőrzi. | Új |
| `/icesmp config find <szövegrészlet>` | Kulcskeresés a teljes összeolvasztott configban. | Játékos vagy konzol | icesmp.admin.reload + icesmp.admin.config | config → find | — | A gyökérparancs előbb a reload jogot is ellenőrzi. | Új |
| `/icesmp inspect <név>` | Online vagy cache-elt offline játékos összesített adminriportja. | Játékos vagy konzol | icesmp.admin.reload + icesmp.admin.inspect | online és helyileg ismert nevek | — | Offline célpontnál csak a UUID-alapú tartós adatok érhetők el. | Új |

#### `/invsee`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/invsee <online-játékos> [read\|edit] [main\|ender]` | Online inventory vagy ender láda élő olvasása/szerkesztése. | Játékos | read: icesmp.moderation.inventory.read; edit: icesmp.moderation.inventory.edit | látható online játékosok → read/edit → main/ender | Invsee GUI | Csak online és a viewer számára látható célpont; edit módban escrow és reconnect-recovery védi a cserét. A parser minden nem `edit` második értéket readnek, minden nem `ender` harmadik értéket mainnek vesz; további argumentumot figyelmen kívül hagy. | Új |

#### `/kem`

Aliasok: `/spy`. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/kem <célfrakció>` | Időzített kémálca. | Játékos | — | frakciók | — | LibsDisguises nélkül nem működik; raid és cooldown kapuzhatja. | Új |

#### `/kick`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/kick <online-játékos> [ok...]` | Az aktuális online session kirúgása és auditálása. | Játékos vagy konzol | icesmp.moderation.kick | jogosultság szerint látható online játékosok; időzített műveletnél időminták | Moderációs GUI | Csak online célpont. | Új |

#### `/komp`

Aliasok: `/ferry`. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/komp` | Konfigurált kompjáratok listája. | Játékos | — | útvonal-ID-k | — | — | Új |
| `/komp <útvonal-id>` | Átkelés a közeli végpontról. | Játékos | — | útvonal-ID-k | — | Harc közben tiltott; hely- és díjfeltételek konfiguráltak. | Új |

#### `/kronika`

Aliasok: `/chronicle`. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/kronika` | Utolsó Heti Krónika visszaolvasása. | Játékos vagy konzol | — | Nincs | — | — | Új |

#### `/leaderboard`

Aliasok: `/lb`, `/rangsor`, `/top`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/leaderboard [level\|wealth\|raidkills]`<br>Routing alias: `vagyon`, `raid`, `kills` | Ranglista GUI a választott kezdőkategóriával. | Játékos | — | level/wealth/raidkills | Ranglista | A vagyon a wealth, a raid és kills a raidkills kategóriára irányít. | Megváltozott |

#### `/lore`

Aliasok: `/kodex`. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/lore <téma>`<br>Routing alias: `red`, `piros`, `perinfernicitas`, `blue`, `kek`, `cryghaliris`, `neutral`, `semleges`, `ryanora`, `caldestera`, `o-caldestera`, `ocaldestera`, `gyokerek`, `dark`, `sotet`, `thanaopolis`, `mortengrad`, `kitaszitott`, `eletfa`, `elet-fa`, `karhozat`, `doom`, `olethropyla`, `suttogas`, `whisper`, `torpok`, `melyseg-nepe`, `konyv`, `kronika-lore`, `korszak`, `folyo` | Kánon-kódexlap chatben. | Játékos vagy konzol | — | lang/fagy/menedek/radicora/kitaszitottak/fa/kapu/suttogok/melyseg/korszakok/bokic | — | Aliasfeloldás: red/piros/perinfernicitas→lang; blue/kek/cryghaliris→fagy; neutral/semleges/ryanora/caldestera→menedek; o-caldestera/ocaldestera/gyokerek→radicora; dark/sotet/thanaopolis/mortengrad/kitaszitott→kitaszitottak; eletfa/elet-fa→fa; karhozat/doom/olethropyla→kapu; suttogas/whisper→suttogok; torpok/melyseg-nepe→melyseg; konyv/kronika-lore/korszak→korszakok; folyo→bokic. Ismeretlen téma usage-listát ad. | Új |

#### `/market`

Aliasok: `/ah`, `/piac`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/market [browse]` | Piactér GUI. | Játékos | — | alparancs; valuta; buyout: minta | Piactér | Csak játékos; ismeretlen alparancs súgót ad. | Megváltozott |
| `/market sell <ár> [valuta]` | Kézben tartott tárgy fix áras listázása. | Játékos | — | alparancs; valuta; buyout: minta | Piactér | Csak játékos; az élő licites aukció nem vonható vissza. | Megváltozott |
| `/market auction <kikiáltási-ár> [óra] [valuta] [buyout:<ár>\|bo:<ár>]` | Aukció indítása. | Játékos | — | alparancs; valuta; buyout: minta | Piactér | Csak játékos; az élő licites aukció nem vonható vissza. | Megváltozott |
| `/market ereklye` | Ereklye/unique börzeszűrő. | Játékos | — | alparancs; valuta; buyout: minta | Piactér | Csak játékos; az élő licites aukció nem vonható vissza. | Új |
| `/market claim` | Megnyert vagy visszajáró tárgyak átvétele. | Játékos | — | alparancs; valuta; buyout: minta | Piactér | Csak játékos; az élő licites aukció nem vonható vissza. | Megváltozott |
| `/market cancel` | Minden visszavonható saját tétel visszavonása. | Játékos | — | alparancs; valuta; buyout: minta | Piactér | Csak játékos; az élő licites aukció nem vonható vissza. | Megváltozott |
| `/market search <szöveg...>` | Szűrt piactér megnyitása. | Játékos | — | alparancs; valuta; buyout: minta | Piactér | Csak játékos; az élő licites aukció nem vonható vissza. | Megváltozott |
| `/market stats` | Aktív tételek és friss forgalom összesítője. | Játékos | — | alparancs; valuta; buyout: minta | — | Csak játékos; az élő licites aukció nem vonható vissza. | Új |

#### `/menu`

Aliasok: `/hub`, `/m`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/menu` | Központi, szerep- és jogosultságfüggő parancsmenü. | Játékos | — | Nincs | Főmenü | — | Megváltozott |

#### `/moderation`

Aliasok: `/mod`. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/moderation [online-játékos]` | Moderációs játékoslista vagy közvetlen célpontműveleti GUI. | Játékos | icesmp.moderation.gui | a viewer számára látható online játékosok | Moderációs GUI | A gombok külön-külön is ellenőrzik a művelet saját jogát. | Új |

#### `/msg`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/msg <játékos> <üzenet...>` | Privát üzenet küldése. | Játékos vagy konzol | icesmp.message | látható online játékosok | — | A három külön root ugyanarra a szolgáltatásra mutat; nem descriptor-aliasok. | Új |

#### `/mute`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/mute <játékos> [időtartam\|végleges] [ok...]` | Automatikus, ideiglenes vagy végleges némítás. | Játékos vagy konzol | icesmp.moderation.mute | jogosultság szerint látható online játékosok; időzített műveletnél időminták | Moderációs GUI | Időtartam nélkül — vagy ha a második token nem időformátum — az eszkalációs idő lép életbe. Elfogadott időegység: s, m/p, h, d/n, w vagy suffix nélküli perc, maximum 365 nap; 0/permanent/vegleges/végleges tartós. Nincs aktív /mute list ág. | Új |

#### `/npcbind`

Aliasok: `/npckotes`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/npcbind` | NPC-kötési használat és elérhető kötéstípusok. | Játékos vagy konzol | icesmp.admin.npc | NPC-nevek → kötéstípus → quest/bolt ID | — | A command kötés a kattintó játékos jogosultságait nem kerüli meg. | Megváltozott |
| `/npcbind list` | Minden NPC-kötés listája. | Játékos vagy konzol | icesmp.admin.npc | NPC-nevek → kötéstípus → quest/bolt ID | — | A command kötés a kattintó játékos jogosultságait nem kerüli meg. | Megváltozott |
| `/npcbind <npc> quest <quest-id>` | Quest-adó kötés. | Játékos vagy konzol | icesmp.admin.npc | NPC-nevek → kötéstípus → quest/bolt ID | — | A command kötés a kattintó játékos jogosultságait nem kerüli meg. | Megváltozott |
| `/npcbind <npc> shop <bolt-id>` | Bolt kötés. | Játékos vagy konzol | icesmp.admin.npc | NPC-nevek → kötéstípus → quest/bolt ID | — | A command kötés a kattintó játékos jogosultságait nem kerüli meg. | Megváltozott |
| `/npcbind <npc> bank` | Bankmenü kötés. | Játékos vagy konzol | icesmp.admin.npc | NPC-nevek → kötéstípus → quest/bolt ID | — | A command kötés a kattintó játékos jogosultságait nem kerüli meg. | Megváltozott |
| `/npcbind <npc> exchange` | Valutaváltó kötés. | Játékos vagy konzol | icesmp.admin.npc | NPC-nevek → kötéstípus → quest/bolt ID | — | A command kötés a kattintó játékos jogosultságait nem kerüli meg. | Megváltozott |
| `/npcbind <npc> faction` | Frakciómenü kötés. | Játékos vagy konzol | icesmp.admin.npc | NPC-nevek → kötéstípus → quest/bolt ID | — | A command kötés a kattintó játékos jogosultságait nem kerüli meg. | Új |
| `/npcbind <npc> command <parancs...>` | A kattintó saját jogával futó parancskötés. | Játékos vagy konzol | icesmp.admin.npc | NPC-nevek → kötéstípus → quest/bolt ID | — | A command kötés a kattintó játékos jogosultságait nem kerüli meg. | Új |
| `/npcbind <npc> clear` | Kötés törlése. | Játékos vagy konzol | icesmp.admin.npc | NPC-nevek → kötéstípus → quest/bolt ID | — | A command kötés a kattintó játékos jogosultságait nem kerüli meg. | Megváltozott |

#### `/offlinetp`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/offlinetp <játékos>` | Teleport a tartósan mentett utolsó kijelentkezési helyre. | Játékos | icesmp.moderation.offlinetp | Nincs | Moderációs GUI | A mentett világ UUID-jének és nevének is illeszkednie kell; nem tölt világot szinkron. | Új |

#### `/parbaj`

Aliasok: `/duel`. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/parbaj` | Becsület-párbaj súgója. | Játékos | — | kihiv/elfogad/elutasit | Bounty menü | — | Új |
| `/parbaj kihiv <online-játékos>`<br>Routing alias: `challenge` | Becsület-párbaj kihívás. | Játékos | — | kihiv/elfogad/elutasit; kihívásnál online nevek | Bounty menü | — | Új |
| `/parbaj elfogad`<br>Routing alias: `accept` | Kihívás elfogadása. | Játékos | — | kihiv/elfogad/elutasit; kihívásnál online nevek | Bounty menü | — | Új |
| `/parbaj elutasit`<br>Routing alias: `decline` | Kihívás elutasítása. | Játékos | — | kihiv/elfogad/elutasit; kihívásnál online nevek | Bounty menü | — | Új |

#### `/parkour`

Aliasok: `/palya`, `/trial`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/parkour [list]` | Pályák listája. | Játékos | — | list/start; adminnak setstart/setfinish/remove | — | Az ismeretlen első argumentum is a pályalistára esik vissza. | Megváltozott |
| `/parkour start <pálya-id>` | Időmérős futás indítása. | Játékos | — | pálya-ID-k | — | — | Megváltozott |
| `/parkour setstart <id> [név]` | Startpont beállítása. | Játékos | icesmp.admin.parkour | pálya-ID-k | — | — | Megváltozott |
| `/parkour setfinish <id> [sugár] [jutalom]` | Célpont, elérési sugár és jutalom beállítása. | Játékos | icesmp.admin.parkour | pálya-ID-k | — | — | Megváltozott |
| `/parkour remove <id>` | Pálya törlése. | Játékos | icesmp.admin.parkour | pálya-ID-k | — | — | Megváltozott |

#### `/party`

Aliasok: `/p`, `/parti`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/party` | Csapattaglista, ha van csapatod; különben súgó. | Játékos | — | alparancs; online játékos/tag a pozíció szerint | Party menü | — | Megváltozott |
| `/party invite <online-játékos>` | Meghívás. | Játékos | — | alparancs; online játékos/tag a pozíció szerint | Party menü | — | Megváltozott |
| `/party accept` | Meghívás elfogadása. | Játékos | — | alparancs; online játékos/tag a pozíció szerint | Party menü | — | Megváltozott |
| `/party decline` | Meghívás elutasítása. | Játékos | — | alparancs; online játékos/tag a pozíció szerint | Party menü | — | Megváltozott |
| `/party leave` | Kilépés. | Játékos | — | alparancs; online játékos/tag a pozíció szerint | Party menü | — | Megváltozott |
| `/party disband` | Csapat feloszlatása (vezető). | Játékos | — | alparancs; online játékos/tag a pozíció szerint | Party menü | — | Megváltozott |
| `/party kick <tag>` | Tag kirúgása (vezető). | Játékos | — | alparancs; online játékos/tag a pozíció szerint | Party menü | — | Megváltozott |
| `/party promote <tag>` | Vezetés átadása. | Játékos | — | alparancs; online játékos/tag a pozíció szerint | Party menü | — | Megváltozott |
| `/party list` | Taglista. | Játékos | — | alparancs; online játékos/tag a pozíció szerint | Party menü | — | Megváltozott |
| `/party chat <üzenet...>` | Csapatchat. | Játékos | — | alparancs; online játékos/tag a pozíció szerint | Party menü | — | Megváltozott |
| `/party c <üzenet...>` | Csapatchat rövid alparanccsal. | Játékos | — | alparancs; online játékos/tag a pozíció szerint | Party menü | — | Megváltozott |
| `/party <üzenet...>` | Minden fel nem ismert első szó csapatchat-üzenetként fut; így az aliasos /p <üzenet> gyors chat. | Játékos | — | alparancs; online játékos/tag a pozíció szerint | Party menü | — | Megváltozott |
| `/party help` | Súgó. | Játékos | — | alparancs; online játékos/tag a pozíció szerint | Party menü | — | Megváltozott |

#### `/pet`

Aliasok: `/companion`, `/tars`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/pet [menu]` | Társvezérlő GUI. | Játékos | — | menu/item/summon/dismiss/name/stance/info | Társ GUI | — | Megváltozott |
| `/pet info` | Társ neve, szintje és XP-je. | Játékos | — | állásmódnál aktiv/passziv/marad | Társ GUI | Ismeretlen alparancs az info nézetre esik vissza. | Megváltozott |
| `/pet item` | Befogóeszköz kérése az engedélyezett kasztnak. | Játékos | — | állásmódnál aktiv/passziv/marad | Társ GUI | Ismeretlen alparancs az info nézetre esik vissza. | Megváltozott |
| `/pet summon` | Társ idézése. | Játékos | — | állásmódnál aktiv/passziv/marad | Társ GUI | Ismeretlen alparancs az info nézetre esik vissza. | Megváltozott |
| `/pet dismiss` | Aktív társ elküldése. | Játékos | — | állásmódnál aktiv/passziv/marad | Társ GUI | Ismeretlen alparancs az info nézetre esik vissza. | Megváltozott |
| `/pet name <név>` | Társ átnevezése. | Játékos | — | állásmódnál aktiv/passziv/marad | Társ GUI | Ismeretlen alparancs az info nézetre esik vissza. | Megváltozott |
| `/pet stance <aktiv\|passziv\|marad>`<br>Routing alias: `active`, `passive`, `stay` | Támadó, passzív vagy helyben maradó állásmód. | Játékos | — | állásmódnál aktiv/passziv/marad | Társ GUI | Az active/passive/stay értékek is elfogadottak. | Új |

#### `/profession`

Aliasok: `/prof`, `/szakma`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/profession` | Szakmaparancsok súgója. | Játékos vagy konzol | — | jogosultság szerint szűrt alparancsok | — | — | Megváltozott |
| `/profession join <szakma>` | Elsődleges gyűjtögető/készítő szakma választása. | Játékos | — | elsődleges szakma-ID-k | Karakterlap / szakmaválasztó / receptkönyv | — | Megváltozott |
| `/profession info` | Saját szakmák és szintek. | Játékos | — | Nincs | Karakterlap / szakmaválasztó / receptkönyv | — | Megváltozott |
| `/profession list` | Szakmák kategóriánként. | Játékos vagy konzol | — | Nincs | Karakterlap / szakmaválasztó / receptkönyv | — | Megváltozott |
| `/profession recipes`<br>Routing alias: `receptek`, `book` | Tanult/zárolt receptkönyv. | Játékos | — | Nincs | Karakterlap / szakmaválasztó / receptkönyv | — | Megváltozott |
| `/profession blueprint <online-játékos> <recept-id>`<br>Routing alias: `tervrajz` | Tervrajz adminátadása. | Játékos vagy konzol | icesmp.admin.profession | online játékos → blueprint recept | Karakterlap / szakmaválasztó / receptkönyv | — | Megváltozott |
| `/profession set <online-játékos> <szakma>` | Szakma adminbeállítása. | Játékos vagy konzol | icesmp.admin.profession | online játékos → szakma | Karakterlap / szakmaválasztó / receptkönyv | — | Megváltozott |
| `/profession clear <online-játékos> <gathering\|crafting>` | Elsődleges szakmaslot törlése. | Játékos vagy konzol | icesmp.admin.profession | online játékos → slot | Karakterlap / szakmaválasztó / receptkönyv | — | Megváltozott |
| `/profession addxp <online-játékos> <szakma> <mennyiség>` | Szakma-XP hozzáadása. | Játékos vagy konzol | icesmp.admin.profession | online játékos → szakma | Karakterlap / szakmaválasztó / receptkönyv | — | Megváltozott |

#### `/profile`

Aliasok: `/char`, `/karakter`, `/status`. Deployed státusz: **Változatlan**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/profile` | Karakterlap megnyitása. | Játékos | — | Nincs | Karakterlap | — | Változatlan |

#### `/punishments`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/punishments [játékos]` | Minden aktív büntetés vagy egy ismert célpont aktív tételei. | Játékos vagy konzol | icesmp.moderation.history | látható online játékosok | Moderációs GUI | — | Új |

#### `/quest`

Aliasok: `/kuldetes`, `/quests`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/quest` | Játékos- és jogosultságfüggő quest-súgó. | Játékos vagy konzol | — | alparancs/quest-ID/objektívatípus/mező/online játékos a pozíció szerint | — | A builder csak admin-készítette questet szerkeszt; config-questet nem. | Megváltozott |
| `/quest log`<br>Routing alias: `gui`, `naplo`, `napló` | Küldetésnapló GUI. | Játékos | — | alparancs/quest-ID/objektívatípus/mező/online játékos a pozíció szerint | Küldetésnapló | A builder csak admin-készítette questet szerkeszt; config-questet nem. | Megváltozott |
| `/quest list` | Felvehető küldetések. | Játékos vagy konzol | — | alparancs/quest-ID/objektívatípus/mező/online játékos a pozíció szerint | — | A builder csak admin-készítette questet szerkeszt; config-questet nem. | Megváltozott |
| `/quest info` | Aktív küldetések és haladás. | Játékos | — | alparancs/quest-ID/objektívatípus/mező/online játékos a pozíció szerint | Küldetésnapló | A builder csak admin-készítette questet szerkeszt; config-questet nem. | Megváltozott |
| `/quest accept <quest-id>` | Küldetés felvétele. | Játékos | — | alparancs/quest-ID/objektívatípus/mező/online játékos a pozíció szerint | Küldetésnapló | A builder csak admin-készítette questet szerkeszt; config-questet nem. | Megváltozott |
| `/quest talk <npc-név>` | NPC-plugin nélküli beszélgetés/átadás tartalékút. | Játékos | — | alparancs/quest-ID/objektívatípus/mező/online játékos a pozíció szerint | — | A builder csak admin-készítette questet szerkeszt; config-questet nem. | Új |
| `/quest abandon <quest-id>` | Aktív küldetés eldobása. | Játékos | — | alparancs/quest-ID/objektívatípus/mező/online játékos a pozíció szerint | Küldetésnapló | A builder csak admin-készítette questet szerkeszt; config-questet nem. | Megváltozott |
| `/quest complete <online-játékos> <quest-id>` | Quest adminlezárása. | Játékos vagy konzol | icesmp.admin.quest | alparancs/quest-ID/objektívatípus/mező/online játékos a pozíció szerint | — | A builder csak admin-készítette questet szerkeszt; config-questet nem. | Megváltozott |
| `/quest admin create <id> <objektíva> <darab> <név...>` | Custom quest létrehozása. | Játékos vagy konzol | icesmp.admin.quest | alparancs/quest-ID/objektívatípus/mező/online játékos a pozíció szerint | Quest builder | A builder csak admin-készítette questet szerkeszt; config-questet nem. | Megváltozott |
| `/quest admin addobjective <id> <objektíva> <darab> [leírás...]` | Objektíva hozzáadása. | Játékos vagy konzol | icesmp.admin.quest | alparancs/quest-ID/objektívatípus/mező/online játékos a pozíció szerint | Quest builder | A builder csak admin-készítette questet szerkeszt; config-questet nem. | Megváltozott |
| `/quest admin set <id> <mező> <érték...>` | Szerkeszthető questmező beállítása. | Játékos vagy konzol | icesmp.admin.quest | alparancs/quest-ID/objektívatípus/mező/online játékos a pozíció szerint | Quest builder | A builder csak admin-készítette questet szerkeszt; config-questet nem. | Megváltozott |
| `/quest admin delete <id>` | Custom quest törlése. | Játékos vagy konzol | icesmp.admin.quest | alparancs/quest-ID/objektívatípus/mező/online játékos a pozíció szerint | Quest builder | A builder csak admin-készítette questet szerkeszt; config-questet nem. | Megváltozott |
| `/quest admin info <id>` | Custom quest részletei. | Játékos vagy konzol | icesmp.admin.quest | alparancs/quest-ID/objektívatípus/mező/online játékos a pozíció szerint | Quest builder | A builder csak admin-készítette questet szerkeszt; config-questet nem. | Megváltozott |
| `/quest admin list` | Custom questek listája. | Játékos vagy konzol | icesmp.admin.quest | alparancs/quest-ID/objektívatípus/mező/online játékos a pozíció szerint | Quest builder | A builder csak admin-készítette questet szerkeszt; config-questet nem. | Megváltozott |
| `/quest admin builder <id>` | Létrehozó vagy szerkesztő GUI. | Játékos | icesmp.admin.quest | alparancs/quest-ID/objektívatípus/mező/online játékos a pozíció szerint | Quest builder | A builder csak admin-készítette questet szerkeszt; config-questet nem. | Megváltozott |

#### `/relic`

Aliasok: `/relics`, `/relikvia`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/relic` | Relikviaparancsok súgója. | Játékos vagy konzol | — | list/give | — | — | Megváltozott |
| `/relic list` | Regisztrált relikvia-ID-k. | Játékos vagy konzol | — | list/give | Relikvia menü | — | Megváltozott |
| `/relic give <online-játékos> <relikvia-id> [mennyiség]` | Relikvia adminátadása. | Játékos vagy konzol | icesmp.admin.relic | online játékos → relikvia ID | — | — | Megváltozott |

#### `/reply`

Aliasok: `/r`. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/reply <üzenet...>`<br>Routing alias: `r` | Válasz az utolsó ténylegesen kézbesített privát beszélgetésre. | Játékos vagy konzol | icesmp.message | Nincs | — | Nincs célpontjavaslat; a state csak sikeres kézbesítés után frissül. | Új |

#### `/report`

Aliasok: `/bejelent`. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/report <név> <ok...>` | Játékosbejelentés rögzítése. | Játékos | — | online játékosok | Moderációs GUI: reports csak adminnak | Az indok legalább három szó; ugyanaz a játékos legfeljebb percenként egyszer jelenthet. | Új |

#### `/reports`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/reports` | Nyitott bejelentések listája. | Játékos vagy konzol | icesmp.admin.moderation | resolve, all | Moderációs GUI | — | Új |
| `/reports all` | Az utolsó legfeljebb húsz bejelentés, lezártakkal. | Játékos vagy konzol | icesmp.admin.moderation | all | Moderációs GUI | — | Új |
| `/reports resolve <id>` | Nyitott bejelentés lezárása. | Játékos vagy konzol | icesmp.admin.moderation | nyitott report ID-k | Moderációs GUI | — | Új |

#### `/sinner`

Aliasok: nincs. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/sinner <online-játékos> set` | A célpont bűnpontjának legalább a bűnös küszöbre állítása. | Játékos vagy konzol | icesmp.admin.sinner | online játékos → set/clear/add/status | — | A tényleges state-művelet a cél saját schedulerén fut. | Megváltozott |
| `/sinner <online-játékos> clear` | A célpont bűnpontjainak törlése. | Játékos vagy konzol | icesmp.admin.sinner | online játékos → set/clear/add/status | — | A tényleges state-művelet a cél saját schedulerén fut. | Megváltozott |
| `/sinner <online-játékos> add` | Egy bűnpont hozzáadása a célponthoz. | Játékos vagy konzol | icesmp.admin.sinner | online játékos → set/clear/add/status | — | A tényleges state-művelet a cél saját schedulerén fut. | Megváltozott |
| `/sinner <online-játékos> status` | A célpont aktuális bűnállapotának lekérdezése. | Játékos vagy konzol | icesmp.admin.sinner | online játékos → set/clear/add/status | — | A tényleges state-művelet a cél saját schedulerén fut. | Megváltozott |

#### `/sit`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/sit` | Leülés a támogatott blokkra, illetve ülés közben felállás. | Játékos | icesmp.sit | fel | — | Csak ülés; nincs lay, crawl, stacking vagy player/NPC sitting. | Új |
| `/sit fel` | Kifejezett felállás. | Játékos | icesmp.sit | fel | — | — | Új |

#### `/socialspy`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/socialspy` | A saját tartós SocialSpy állapot ki-/bekapcsolása. | Játékos | icesmp.moderation.socialspy | Nincs | — | — | Új |

#### `/soulforge`

Aliasok: `/lelekkovacs`. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/soulforge` | Nekromanta lélekkovács állapota. | Játékos | — | fejleszt | — | — | Új |
| `/soulforge fejleszt <elet\|sebzes\|letszam>`<br>Routing alias: `élet`, `sebzés`, `létszám` | Minionfejlesztési ág rangemelése. | Játékos | — | elet/sebzes/letszam | — | — | Új |

#### `/souls`

Aliasok: `/lelek`, `/soul`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/souls` | Saját lélekszilánk-egyenleg. | Játékos | — | champion | — | — | Megváltozott |
| `/souls champion` | Nekromanta bajnokidézés. | Játékos | — | champion | — | — | Megváltozott |

#### `/spec`

Aliasok: `/specializacio`, `/specialization`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/spec` | Specializációs súgó. | Játékos vagy konzol | — | list/choose/info/respec; adminnak reset | — | — | Megváltozott |
| `/spec list` | Elérhető specializációk. | Játékos | — | list/choose/info/respec; adminnak reset | Specializáció GUI | — | Megváltozott |
| `/spec choose <specializáció-id>` | Specializáció kiválasztása. | Játékos | — | választható ID-k | Specializáció GUI | — | Megváltozott |
| `/spec info` | Saját specializációállapot. | Játékos | — | Nincs | Specializáció GUI | — | Megváltozott |
| `/spec respec <class\|profession>` | Saját specializáció visszaváltása. | Játékos | — | class/profession | Specializáció GUI | — | Megváltozott |
| `/spec reset <online-játékos>` | Specializáció adminresetje. | Játékos vagy konzol | icesmp.admin.spec | online játékos | Specializáció GUI | — | Megváltozott |

#### `/spell`

Aliasok: `/mastery`, `/mesterseg`, `/spells`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/spell [info]` | Spell-mesterség állapota. | Játékos | — | info/upgrade | — | Minden, nem pontosan `upgrade <spell-id>` alakú hívás az információs nézetet adja. | Megváltozott |
| `/spell upgrade <spell-id>` | Spell-mesterség fejlesztése valutáért. | Játékos | — | feloldott spell-ID-k | — | — | Megváltozott |

#### `/spellbook`

Aliasok: `/konyv`, `/sb`, `/varazskonyv`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/spellbook` | Lapozható és szűrhető varázskönyv. | Játékos | — | Nincs | Varázskönyv | — | Megváltozott |

#### `/stats`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/stats [név]` | Saját vagy ismert játékos statisztikaprofilja. | Játékos; konzol csak névvel | — | online játékosok | — | — | Új |

#### `/suttogas`

Aliasok: `/sutt`. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/suttogas <üzenet...>` | Suttogó/Kitaszított titkos csatorna. | Játékos | — | vád | — | — | Új |
| `/suttogas vád <online-játékos>`<br>Routing alias: `vad`, `accuse` | Tanú-tokenes, eredményt el nem áruló vád. | Játékos | — | vád → online játékos | — | — | Új |

#### `/szakmacel`

Aliasok: `/weeklygoal`. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/szakmacel` | Szakma heti közös céljának állása. | Játékos | — | Nincs | — | — | Új |

#### `/talent`

Aliasok: `/talentfa`, `/talents`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/talent [list]` | Saját talentállapot. | Játékos | — | list/spend | Talent-fa | — | Megváltozott |
| `/talent spend <class\|profession> <talent-id>` | Talentpont elköltése. | Játékos | — | class/profession → elérhető talentek | Talent-fa | — | Megváltozott |

#### `/tanacs`

Aliasok: `/council`. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/tanacs [info]` | Vének Tanácsa heti állása. | Játékos | — | info/szavaz/vasarhet; szavazásnál online játékos | — | Az ismeretlen első argumentum is az információs nézetre esik vissza. | Új |
| `/tanacs szavaz <online-játékos>` | Heti tanácsi szavazat. | Játékos | — | info/szavaz/vasarhet; szavazásnál online játékos | — | — | Új |
| `/tanacs vasarhet` | Tanácstagi Vásár-hét indítása. | Játékos | — | info/szavaz/vasarhet; szavazásnál online játékos | — | — | Új |

#### `/tell`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/tell <játékos> <üzenet...>` | Privát üzenet küldése. | Játékos vagy konzol | icesmp.message | látható online játékosok | — | A három külön root ugyanarra a szolgáltatásra mutat; nem descriptor-aliasok. | Új |

#### `/tempban`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/tempban <játékos> <időtartam> [ok...]` | Ideiglenes kitiltás. | Játékos vagy konzol | icesmp.moderation.ban | jogosultság szerint látható online játékosok; időzített műveletnél időminták | Moderációs GUI | Pozitív időtartam kötelező; s, m/p, h, d/n, w vagy suffix nélküli perc, maximum 365 nap. A tabban látható `végleges` itt érvénytelen, mert ezt a route 0 időtartamként elutasítja. | Új |

#### `/territory`

Aliasok: `/terulet`. Deployed státusz: **Megváltozott**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/territory` | Territórium-admin súgó. | Játékos vagy konzol | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Megváltozott |
| `/territory pos`<br>Routing alias: `point` | Poligonpont felvétele az aktuális pozíción. | Játékos | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Megváltozott |
| `/territory wand`<br>Routing alias: `palca` | Territórium-kijelölő pálca. | Játékos | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Új |
| `/territory undo` | Utolsó pufferpont visszavonása. | Játékos | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Megváltozott |
| `/territory clearpoints`<br>Routing alias: `clear` | Minden pufferpont törlése. | Játékos | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Megváltozott |
| `/territory points` | Pufferpontok listája. | Játékos | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Megváltozott |
| `/territory create <típus> <frakció> <id> [név...]` | Poligonzóna létrehozása. | Játékos | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Megváltozott |
| `/territory circle <típus> <frakció> <id> <sugár> [név...]` | Körzóna létrehozása. | Játékos | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Megváltozott |
| `/territory setcapital <frakció> <sugár> [név...]` | Főváros-körzóna létrehozása. | Játékos | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Megváltozott |
| `/territory setspawn <frakció>` | Királyságspawn beállítása. | Játékos | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Új |
| `/territory rename <id> <új név...>` | Zóna átnevezése. | Játékos vagy konzol | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Megváltozott |
| `/territory resize <id> <sugár>` | Körzóna sugarának módosítása. | Játékos vagy konzol | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Megváltozott |
| `/territory settype <id> <típus>` | Zónatípus módosítása. | Játékos vagy konzol | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Megváltozott |
| `/territory sety <id> <minY\|~> <maxY\|~>` | Magassági sáv módosítása. | Játékos vagy konzol | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Megváltozott |
| `/territory remove <id>` | Zóna törlése. | Játékos vagy konzol | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Megváltozott |
| `/territory list` | Zónák listája. | Játékos vagy konzol | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Megváltozott |
| `/territory info` | Aktuális pozíció zónája. | Játékos | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Megváltozott |
| `/territory show [id]` | Puffer/aktuális/megadott határ kirajzolása. | Játékos | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Megváltozott |
| `/territory tp <id>`<br>Routing alias: `teleport` | Teleport a zónaközépponthoz. | Játékos | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Megváltozott |
| `/territory dungeonchest [tábla]` | Nézett tároló dungeon loot-táblához kötése/törlése. | Játékos | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Új |
| `/territory dungeonboss <zóna-id> [tábla]` | Dungeon boss spawn rögzítése. | Játékos | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Új |
| `/territory dungeonboss clear <zóna-id>` | Dungeon boss spawn törlése. | Játékos | icesmp.admin.territory | alparancs, típus/frakció/zóna-ID a pozíció szerint | — | Világot módosító ágaknál játékos és aktuális pozíció kell. | Új |

#### `/unban`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/unban <játékos> [ok...]` | Aktív kitiltás feloldása. | Játékos vagy konzol | icesmp.moderation.ban | jogosultság szerint látható online játékosok | Moderációs GUI | — | Új |

#### `/unmute`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/unmute <játékos> [ok...]` | Aktív némítás feloldása. | Játékos vagy konzol | icesmp.moderation.mute | jogosultság szerint látható online játékosok | Moderációs GUI | — | Új |

#### `/vanish`

Aliasok: `/v`. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/vanish [online-játékos]` | Saját vagy célpont tartós vanish állapotának váltása. | Játékos vagy konzol; konzol csak célponttal | icesmp.moderation.vanish | jogosultság szerint látható online játékosok | Moderációs GUI | — | Új |

#### `/w`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/w <játékos> <üzenet...>` | Privát üzenet küldése. | Játékos vagy konzol | icesmp.message | látható online játékosok | — | A három külön root ugyanarra a szolgáltatásra mutat; nem descriptor-aliasok. | Új |

#### `/warn`

Aliasok: nincs. Deployed státusz: **Új**.

| Szintaxis / routing alias | Mit csinál | Használó | Permission | Tab completion | GUI | Fontos korlát | Státusz |
|---|---|---|---|---|---|---|---|
| `/warn <játékos> [ok...]` | Figyelmeztetés kiadása. | Játékos vagy konzol | icesmp.moderation.warn | jogosultság szerint látható online játékosok; időzített műveletnél időminták | Moderációs GUI | Offline, de ismert célpont is használható. | Új |

### Bizonyított eltérések a régi leírásoktól

- A `/class givecatalyst` aktív route-ja `icesmp.admin.job` jogot kér; nem játékos-önkiszolgáló út.
- Az aktív `/mute` a közös moderációs action handlert használja. A forrásban maradt régi `MuteCommand` nincs bekötve, ezért `/mute list` nincs.
- A `/bank withdraw` parser a `dark` értéket is elfogadja, de a beépített usage és tab csak `red`, `blue`, `neutral` értéket mutat.
- `/events chronicle` nincs a tényleges dispatchben, hiába utal rá egy forráskomment.
- `/msg`, `/tell` és `/w` három külön root regisztráció ugyanazzal a handlerrel; csak `/reply` valódi root aliasa az `/r`.

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

### Főmenü és tematikus parancsmenük

- Megnyitás: /menu, /achievements, /leaderboard; belső MENU/LB navigáció.
- Célközönség és jog: Játékos; Admin panel jogosultság szerint; `Nincs a megnyitáshoz; minden célparancs saját jogát ellenőrzi`.
- Holder: `CommandMenuHolder`; méret: `27/36/45/54, nézettől függően`.
- Deployed JAR-hoz képest: **Megváltozott**.

Funkcionális slotok/műveletek:

- MAIN (45): 4 fejléc read-only; 10 karakterlap, 11 varázskönyv, 12 társ (ha elérhető), 13 questnapló, 14 napi állapot, 15 achievement, 16 leaderboard; 19 frakció, 20 party, 21 claim, 22 events, 23 bounty, 24 relic, 25 souls (csak Nekromantának); 28 bank, 29 piac, 30 adomány, 31 receptek, 32 bestiárium, 33 heti szakmacél, 34 admin (bármely adminjoggal), 40 bezárás.
- FACTION (36): 4 állapot read-only; frakció nélkül 10/12/14/16 join; tagként 10/11/12 adomány 10/50/100, 14 király/szavazás, 16 frakcióváltó; királynál 15 kasszakivét 100 és 20-tól legfeljebb három más-frakció raidgomb; 19 céh, 28 kém, 29 karaván 100, 30 kilépés, 31 vissza.
- FACTION_SWITCH (36): 4 feltételek read-only; a jelenlegi frakciót kihagyó, tömörített join/váltás csempék 10/12/14-en (frakció nélkül 10/12/14/16); 31 vissza.
- BANK (36): 4 egyenlegek read-only; 11 minden fizikai valuta befizetése, 13 saját valuta kivétele 64, 15 árfolyamok, 22 váltó, 24 ereklye-börze, 31 vissza.
- EXCHANGE (45): 4 egyenlegek; 9 forráscímke; 11/12/14/15 forrásválasztók; 27 célcímke; 29/30/32/33 célválasztók; 22 árfolyam vagy hiányzó választás read-only; érvényes párnál 38/39/40 váltás 16/32/64 és pozitív egyenlegnél 41 mind; 36 vissza.
- EVENTS (27): 0 teljes status parancs, 4 season parancs, 10 blood-moon status parancs, 12 caravan status parancs; 11 worldboss-, 13 escort-, 14 abundance-, 15 challenge-, 16 meteor- és 19 gathering/buff-állapot read-only; 22 vissza.
- RELIC (36): 4 fejléc; relikviák read-only csempéi 10–16, majd 19–25, legfeljebb 14 bejegyzés; 31 vissza.
- SOULS (27): Nekromantának 11 szilánkegyenleg read-only, 13 lélekkovács, 15 bajnokidézés; másnak 13 zárolt info; 22 vissza.
- PARTY (27): 4 fejléc; kikapcsolt vagy csapat nélküli állapotban 13 info; csapatban a tagok read-only 10–16, 19 kilépés, vezetőnek 25 feloszlatás; 22 vissza.
- CLAIM (27): kikapcsolva 13 info; aktívan 4 összegzés read-only, 10 claim, 11 unclaim, 12 show, 13 list, 14 pos1, 15 pos2, 16 area, 19 extend up, 20 extend down, 21 trustgui, 22 vissza.
- BOUNTY (27): kikapcsolva 13 info; aktívan 4 fejléc, 9–17 legfeljebb kilenc körözött játékos read-only vagy 13 üresállapot, 18 becsület-párbaj, 22 vissza.
- ADMIN (54): 4 fejléc; 10 reload, 11 config GUI, 12 exchangeboard place, 13 exchangeboard remove, 14 iceitem usage, 16 intro; eventek: 19 blood-moon start, 20 stop, 21 worldboss, 22 invasion, 23 caravan arrive, 24 depart, 25 wild-hunt, 26 corruption, 28 meteor, 29 treasure, 30 gathering, 31 abundance, 32 challenge, 33 escort, 34 ambient, 35 archeology; 37 claim admin unclaim, 38 NPC-lista, 39 quest adminlista, 49 vissza; jog nélkül csak 22 tiltás és 49 vissza.
- LEADERBOARD (54): 4 fejléc; 0 level, 1 wealth, 2 raidkills kategória; top 10 read-only a 9–18 slotokon vagy 22 üresállapot; 49 vissza.
- ACHIEVEMENTS (54): 4 fejléc; legfeljebb 36 read-only mérföldkő 9–44; 49 vissza.

- Lapozás: A dinamikus nézetek a saját forrásbeli plafonjukig töltenek; nincs általános lapozó..
- Vissza/bezárás: MENU:MAIN vagy az adott szülőnézet; CLOSE bezár..
- Read/edit különbség: A legtöbb csempe parancsot futtat; az info csempék read-onlyk..
- Hibás vagy lezárt állapot: A nem használható funkció tiltott/infó csempét kap; admin gombok csak megfelelő joggal kötődnek..
- Cleanup és clickbiztonság: Owner UUID ellenőrzés, top inventory click/drag tiltás; nincs tartós GUI-state..
- Forrás: `src/main/java/hu/taliann/icesmp/gui/CommandMenus.java`, `src/main/java/hu/taliann/icesmp/listeners/CommandMenuListener.java`.

### Karakterlap

- Megnyitás: /profile.
- Célközönség és jog: Játékos; `—`.
- Holder: `ProfileHolder`; méret: `36`.
- Deployed JAR-hoz képest: **Megváltozott**.

Funkcionális slotok/műveletek:

- 4 fej/read-only; 11 kaszt; 13 specializáció; 15 szakma; 20 talent; 22 képességfa; 24 gazdaság read-only; 27 főmenü; 31 bezárás.

- Lapozás: Nincs.
- Vissza/bezárás: 27 főmenü; 31 bezár.
- Read/edit különbség: Navigáció; fej/gazdaság csak kijelzés.
- Hibás vagy lezárt állapot: A célmenük saját állapotkapui érvényesek.
- Cleanup és clickbiztonság: Owner UUID + click/drag cancel; holder inventory nullázás.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/ProfileGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/CharacterGUIListener.java`.

### Kasztválasztó

- Megnyitás: Karakterlap /class kontextusból.
- Célközönség és jog: Játékos; `—`.
- Holder: `JobGUIHolder`; méret: `54`.
- Deployed JAR-hoz képest: **Megváltozott**.

Funkcionális slotok/műveletek:

- 10,12,14,16,19,21,23,25,28,30,32,34,37,39,41,43: 16 kaszt; 47 képességfa; 49 vissza; 51 Lélekkapocs.

- Lapozás: Nincs.
- Vissza/bezárás: 49 karakterlap.
- Read/edit különbség: Kasztválasztás/kapunyitás.
- Hibás vagy lezárt állapot: Kaszt-, katalizátor- és választási kapuk a managerben.
- Cleanup és clickbiztonság: Owner UUID, click/drag cancel, holder cleanup.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/JobGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/JobGUIListener.java`.

### Szakmaválasztó

- Megnyitás: Karakterlap.
- Célközönség és jog: Játékos; `—`.
- Holder: `ProfessionHolder`; méret: `45`.
- Deployed JAR-hoz képest: **Megváltozott**.

Funkcionális slotok/műveletek:

- 4 fejléc; 19–21 gyűjtögetők; 23–25 készítők; 30/32 másodlagos szakmák read-only; 40 vissza.

- Lapozás: Nincs.
- Vissza/bezárás: 40 karakterlap.
- Read/edit különbség: Elsődleges szakmák választása; másodlagosak kijelzése.
- Hibás vagy lezárt állapot: Slot- és szakmakorlátok managerből.
- Cleanup és clickbiztonság: Owner UUID + click/drag cancel.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/ProfessionGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/CharacterGUIListener.java`.

### Specializációk

- Megnyitás: Karakterlap vagy /spec folyamat.
- Célközönség és jog: Játékos; `—`.
- Holder: `SpecHolder`; méret: `54`.
- Deployed JAR-hoz képest: **Megváltozott**.

Funkcionális slotok/műveletek:

- 4 fejléc; 10–16 kasztspecek; 28–34 és 37–43 szakmaspecek; 45 class respec; 49 vissza; 53 profession respec.

- Lapozás: Nincs.
- Vissza/bezárás: 49 karakterlap.
- Read/edit különbség: Választás és két respec.
- Hibás vagy lezárt állapot: Szint, memóriafeloldás, költség és meglévő választás kapuz.
- Cleanup és clickbiztonság: Owner UUID + click/drag cancel.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/SpecGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/CharacterGUIListener.java`.

### Talent-fa

- Megnyitás: Karakterlap.
- Célközönség és jog: Játékos; `—`.
- Holder: `TalentHolder`; méret: `54`.
- Deployed JAR-hoz képest: **Megváltozott**.

Funkcionális slotok/műveletek:

- 0 kasztpont-címke, 4 fejléc, 45 szakmapont-címke — read-only; dinamikus talentnode-ok az 1–3. és 4–5. sorban, soronként hét belső oszloppal; 53 vissza.

- Lapozás: Nincs.
- Vissza/bezárás: 53 karakterlap.
- Read/edit különbség: Node-kattintás talentpontot költ.
- Hibás vagy lezárt állapot: Előfeltétel, max rang és pont hiánya vizuálisan lezárt.
- Cleanup és clickbiztonság: Owner UUID + click/drag cancel.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/TalentGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/CharacterGUIListener.java`.

### Képességfa

- Megnyitás: Karakterlap vagy kasztválasztó.
- Célközönség és jog: Játékos; `—`.
- Holder: `SkillTreeHolder`; méret: `54`.
- Deployed JAR-hoz képest: **Változatlan**.

Funkcionális slotok/műveletek:

- 0–44 dinamikus, read-only feloldási állapot; 49 vissza.

- Lapozás: Legfeljebb 45 bejegyzés, nincs lapozás.
- Vissza/bezárás: 49 kasztválasztó.
- Read/edit különbség: Read-only.
- Hibás vagy lezárt állapot: Feloldott/zárolt állapotot jelenít meg.
- Cleanup és clickbiztonság: Owner UUID + click/drag cancel, holder cleanup.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/SkillTreeGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/SkillTreeGUIListener.java`.

### Varázskönyv

- Megnyitás: /spellbook vagy Lélekkapocs interakció.
- Célközönség és jog: Játékos; `—`.
- Holder: `SpellbookHolder`; méret: `54`.
- Deployed JAR-hoz képest: **Megváltozott**.

Funkcionális slotok/műveletek:

- 0–44 spellbejegyzések: normál katt kiválasztás, shift-katt kedvenc ki/be; 45 előző; 47 csak feloldott/minden szűrő; 49 oldalinfó read-only; 53 következő.

- Lapozás: 45 spell/oldal.
- Vissza/bezárás: Nincs külön vissza; inventory bezárható.
- Read/edit különbség: Spellkiválasztás és szűrés; leírás read-only.
- Hibás vagy lezárt állapot: Nem használható spell zárolt állapotban marad.
- Cleanup és clickbiztonság: Owner UUID, click/drag cancel; bezáráskor holder cleanup.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/SpellbookGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/SpellbookListener.java`.

### Szakmai receptkönyv

- Megnyitás: /profession recipes.
- Célközönség és jog: Játékos; `—`.
- Holder: `ProfessionRecipeHolder`; méret: `54`.
- Deployed JAR-hoz képest: **Megváltozott**.

Funkcionális slotok/műveletek:

- 0–44 receptek: kattintás craftkísérlet; 45 előző; 49 bezárás; 53 következő.

- Lapozás: 45 recept/oldal.
- Vissza/bezárás: 49 bezárás.
- Read/edit különbség: Craftolás inventoryból; receptállapot kijelzés.
- Hibás vagy lezárt állapot: Tanulatlan/hiányos recept nem craftolható.
- Cleanup és clickbiztonság: Owner UUID, click/drag cancel; tranzakció után újrarender.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/ProfessionRecipeGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/ProfessionRecipeBookListener.java`.

### Piactér

- Megnyitás: /market, /market search, /market ereklye.
- Célközönség és jog: Játékos; `—`.
- Holder: `MarketHolder`; méret: `54`.
- Deployed JAR-hoz képest: **Megváltozott**.

Funkcionális slotok/műveletek:

- 0–44 tételek: fix árnál vásárlás; aukción bal katt minimum licit, jobb katt nagyobb licit, shift katt buyout; 45 előző; 49 oldalinfó; 53 következő.

- Lapozás: 45 tétel/oldal.
- Vissza/bezárás: Inventory bezárás.
- Read/edit különbség: Pénz- és tárgytranzakció.
- Hibás vagy lezárt állapot: Eltűnt/saját/lejárt/elégtelen fedezet állapot visszautasít.
- Cleanup és clickbiztonság: Owner UUID + click/drag cancel; siker/hiba után újrarender.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/MarketGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/MarketGUIListener.java`.

### Adományláda

- Megnyitás: /adomany.
- Célközönség és jog: Játékos; `—`.
- Holder: `DonationChestHolder`; méret: `54`.
- Deployed JAR-hoz képest: **Megváltozott**.

Funkcionális slotok/műveletek:

- 0–44 adomány elvétele; 45 főkéz adományozása; 48 előző; 49 oldalinfó; 50 következő; 53 főmenü.

- Lapozás: 45 adomány/oldal.
- Vissza/bezárás: 53 főmenü.
- Read/edit különbség: Ingyenes elvétel és teljes stack adomány.
- Hibás vagy lezárt állapot: Versenyhelyzetben már elvett tétel frissítéssel eltűnik.
- Cleanup és clickbiztonság: Owner UUID + click/drag cancel; minden művelet után újrarender.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/DonationChestGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/DonationChestListener.java`.

### Küldetésnapló

- Megnyitás: /quest log.
- Célközönség és jog: Játékos; `—`.
- Holder: `QuestLogHolder`; méret: `54`.
- Deployed JAR-hoz képest: **Változatlan**.

Funkcionális slotok/műveletek:

- 0–44 questek: aktív fülön eldobás, felvehető fülön felvétel, teljesített fül read-only; 45/46/47 fülek; 48 előző; 49 oldalinfó; 50 következő; 53 főmenü.

- Lapozás: 45 quest/oldal/fül.
- Vissza/bezárás: 53 főmenü.
- Read/edit különbség: Fülfüggő accept/abandon.
- Hibás vagy lezárt állapot: Feltétel vagy versenyhelyzet esetén hiba és frissítés.
- Cleanup és clickbiztonság: Owner UUID + click/drag cancel.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/QuestLogGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/QuestLogListener.java`.

### Quest builder

- Megnyitás: /quest admin builder <id>.
- Célközönség és jog: Admin; `icesmp.admin.quest`.
- Holder: `QuestBuilderHolder`; méret: `TYPE_PICKER 36; EDITOR 54`.
- Deployed JAR-hoz képest: **Megváltozott**.

Funkcionális slotok/műveletek:

- TYPE_PICKER: a 21 regisztrált objektívatípus pontosan a 0–20 slotokon; 35 mégse/bezárás.
- EDITOR: 4 fejléc read-only; chatmezők: 10 display-name, 11 description, 12 giver-npc, 13 cooldown-hours, 19 requires-level, 20 requires-quest, 21 requires-faction, 22 requires-job, 23 auto-start-territory, 28 rewards.class-xp, 29 rewards.currency.type, 30 rewards.currency.amount, 31 rewards.unlock-spell, 33 rewards.items, 37 dialogue.speaker, 38 dialogue.give, 39 dialogue.complete.
- EDITOR kapcsolók: 14 repeatable, 15 seasonal, 32 rewards.cleanse-sins; 16 objectives-mode ALL/SEQUENCE.
- EDITOR objektíva/admin: 41 objektívaösszegzés read-only, 42 új objektíva, 45 két egymást követő kattintásos végleges törlés, 49 bezárás.

- Lapozás: Nincs.
- Vissza/bezárás: Bezárás; prompt után szerkesztő újranyit.
- Read/edit különbség: GUI + következő chatüzenet mint mező/darabszám/név.
- Hibás vagy lezárt állapot: Configból jövő quest nem szerkeszthető.
- Cleanup és clickbiztonság: Owner UUID, click/drag cancel; prompt quit/kick esetén törlődik.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/QuestBuilderGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/QuestBuilderListener.java`.

### NPC/frakció bolt

- Megnyitás: NPC-kötés/interakció.
- Célközönség és jog: Játékos; `—`.
- Holder: `ShopHolder`; méret: `9–54, tételszám szerint`.
- Deployed JAR-hoz képest: **Megváltozott**.

Funkcionális slotok/műveletek:

- A konfigurált items lista első legfeljebb 54 eleme a saját 0-alapú listaindexével azonos sloton; hibás material kimarad és rést hagy; minden leképezett tétel kattintása banki vásárlás.

- Lapozás: Nincs; a méret 9 × clamp(ceil(tételszám/9), 1, 6), így legfeljebb 54 tétel.
- Vissza/bezárás: Inventory bezárás.
- Read/edit különbség: Vásárlás.
- Hibás vagy lezárt állapot: Zárt bolt, rossz frakció, eltűnt tétel vagy fedezethiány.
- Cleanup és clickbiztonság: Owner UUID + click/drag cancel; vásárlás után újrarender/bezárás.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/ShopGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/ShopListener.java`.

### Bestiárium

- Megnyitás: /bestiarium.
- Célközönség és jog: Játékos; `—`.
- Holder: `BestiaryHolder`; méret: `27`.
- Deployed JAR-hoz képest: **Új**.

Funkcionális slotok/műveletek:

- 10 szörnyfajok száma; 12 regisztrált receptek száma; 14 territóriumok száma; 16 világboss-definíciók száma — mind a négy csempe read-only.

- Lapozás: Nincs.
- Vissza/bezárás: Inventory bezárás.
- Read/edit különbség: Read-only összesítő.
- Hibás vagy lezárt állapot: Nincs részletes vagy kattintható kategórianézet.
- Cleanup és clickbiztonság: Minden click/drag tiltott.
- Forrás: `src/main/java/hu/taliann/icesmp/commands/BestiaryCommand.java`, `src/main/java/hu/taliann/icesmp/listeners/BestiaryListener.java`.

### Megbízottak kezelése

- Megnyitás: /claim trustgui vagy Claim menü.
- Célközönség és jog: Játékos; `—`.
- Holder: `ClaimTrustHolder`; méret: `54`.
- Deployed JAR-hoz képest: **Új**.

Funkcionális slotok/műveletek:

- 0–35 jelenlegi megbízottak: untrust; 45–52 közeli online játékosok: trust; 53 bezárás.

- Lapozás: Nincs; a két tartomány plafonja érvényes.
- Vissza/bezárás: 53 bezárás.
- Read/edit különbség: Trust/untrust parancsdelegálás.
- Hibás vagy lezárt állapot: Üres tartomány tájékoztató csempe.
- Cleanup és clickbiztonság: Owner UUID + click/drag cancel; művelet után újrarender.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/ClaimTrustGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/ClaimTrustGUIListener.java`.

### Config menü

- Megnyitás: /icesmp config menu vagy admin főmenü.
- Célközönség és jog: Admin; `icesmp.admin.config`.
- Holder: `ConfigMenuHolder`; méret: `36`.
- Deployed JAR-hoz képest: **Új**.

Funkcionális slotok/műveletek:

- ROOT: kategóriák beszúrási sorrendben 10–16, 19–25, 28–33; 35 bezárás.
- KATEGÓRIA: 0..N-1 szerkeszthető kulcs; 31 vissza; 35 bezárás.
- TOGGLE katt = vált; CYCLE katt = következő; NUMBER bal = +lépés, jobb = −lépés, shift = 5×.

- Lapozás: Nincs; a kurált katalógus kategóriánként legfeljebb 31 kulcs.
- Vissza/bezárás: 31 kategóriagyökér; 35 bezárás.
- Read/edit különbség: Data-folder config.yml override-ot ír, reloadol és validál.
- Hibás vagy lezárt állapot: Minden clicknél újra ellenőrzi a jogot; min/max clamp.
- Cleanup és clickbiztonság: Owner UUID + top inventory + permission ellenőrzés; click/drag cancel.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/ConfigMenuGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/ConfigMenuGUIListener.java`.

### Crate böngésző és preview

- Megnyitás: /crate, /crate info, /crate preview.
- Célközönség és jog: Játékos; `icesmp.crate.use + opcionális crate-specifikus jog`.
- Holder: `CrateBrowserHolder`; méret: `54`.
- Deployed JAR-hoz képest: **Új**.

Funkcionális slotok/műveletek:

- Lista: hozzáférhető crate-csempék pontosan a 10–16, 19–25, 28–34 és 37–43 tartományban (28 hely), kattintás preview; 49 bezárás.
- Preview: jutalomcsempék ugyanazon 28 contentsloton, read-only; 45 vissza a listához; 49 bezárás.

- Lapozás: Nincs; legfeljebb 28 crate vagy jutalom látható.
- Vissza/bezárás: 45 listához; 49 bezárás.
- Read/edit különbség: Read-only.
- Hibás vagy lezárt állapot: Hozzáférés-változáskor a preview nem nyílik.
- Cleanup és clickbiztonság: Owner UUID + click/drag cancel.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/CrateBrowserGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/CrateBrowserGUIListener.java`.

### Crate nyitási animáció

- Megnyitás: Sikeres crate settlement után automatikusan.
- Célközönség és jog: Játékos; `A nyitás hozzáférési jogai`.
- Holder: `CrateSpinHolder`; méret: `27`.
- Deployed JAR-hoz képest: **Új**.

Funkcionális slotok/műveletek:

- 4 állapot; 9–17 reel; 13 nyertes közép — minden slot kozmetikai, nem kattintható.

- Lapozás: Nincs.
- Vissza/bezárás: Bezárható.
- Read/edit különbség: Read-only animáció; a jutalom már jóváírt.
- Hibás vagy lezárt állapot: Nincs click action.
- Cleanup és clickbiztonság: Minden click/drag tiltott; close cancel flag leállítja a delayed láncot.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/CrateSpinGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/CrateSpinGUIListener.java`.

### Invsee

- Megnyitás: /invsee ....
- Célközönség és jog: Moderátor/Admin; `icesmp.moderation.inventory.read vagy .edit`.
- Holder: `InvseeHolder`; méret: `54`.
- Deployed JAR-hoz képest: **Új**.

Funkcionális slotok/műveletek:

- MAIN: 0–35 storage, 36–39 armor, 40 offhand; 45 ender; 49 módjelző; 53 bezárás.
- ENDER: 0–26 ender; 36 vissza main; 40 módjelző; 44 bezárás.

- Lapozás: Nincs.
- Vissza/bezárás: 36 main; close slot.
- Read/edit különbség: Read módban teljes tiltás; edit módban célslot cseréje escrow útvonalon.
- Hibás vagy lezárt állapot: Veszélyes shift/hotbar/collect/drag útvonalak tiltva; cél quit/reconnect recovery.
- Cleanup és clickbiztonság: Owner/session ellenőrzés, inventory close manager cleanup.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/InvseeGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/InvseeGUIListener.java`.

### Moderációs GUI

- Megnyitás: /moderation [játékos].
- Célközönség és jog: Moderátor/Admin; `icesmp.moderation.gui + gombonkénti műveleti jog`.
- Holder: `ModerationGuiHolder`; méret: `54`.
- Deployed JAR-hoz képest: **Új**.

Funkcionális slotok/műveletek:

- Játékoslista: név szerint rendezett, látható online játékosok közül legfeljebb 45 a 0–44 slotokon; 49 bezárás.
- Célpont: 10 warn, 11 mute 30m, 12 ban, 13 kick, 14 unmute, 15 unban, 19 history, 20 punishments, 21 reports, 22/23 main read/edit, 24/25 ender read/edit, 28 online teleport, 29 offlinetp, 30 socialspy, 31 vanish, 49 vissza, 53 bezárás.

- Lapozás: Nincs; az első 45 látható online játékos.
- Vissza/bezárás: 49 játékoslista; 53 bezárás.
- Read/edit különbség: Gombok a dokumentált parancsokat futtatják.
- Hibás vagy lezárt állapot: Jog nélküli gomb nem fut; eltűnt célpontot újra felold.
- Cleanup és clickbiztonság: Owner/láthatóság/top inventory ellenőrzés, click/drag cancel.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/ModerationGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/ModerationGUIListener.java`.

### Társ GUI

- Megnyitás: /pet vagy /pet menu.
- Célközönség és jog: Játékos; `—`.
- Holder: `PetGUIHolder`; méret: `27`.
- Deployed JAR-hoz képest: **Új**.

Funkcionális slotok/műveletek:

- 4 info; 10 summon; 11 dismiss; 12 név-hint; 14 aktiv; 15 passziv; 16 marad; 22 páncél/read-only; 26 bezárás.

- Lapozás: Nincs.
- Vissza/bezárás: 26 bezárás.
- Read/edit különbség: RUN:/pet delegálás; név gomb használati hintet ad.
- Hibás vagy lezárt állapot: Kaszt/petállapot kapuzza a műveletet.
- Cleanup és clickbiztonság: Owner UUID + click/drag cancel; bezáráskor holder inventory null.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/PetGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/PetGUIListener.java`.

### Általános GUI-biztonsági megállapítások

- Az owner UUID-t használó GUI-k más játékos kattintását elutasítják.
- A top inventory kattintásai és dragjei holder alapján tiltva vannak; a read-only felületek minden kattintást elnyelnek.
- A moderációs és config GUI nem a megnyitáskor kapott jogra hagyatkozik: a click routing újra ellenőriz.
- Az invsee edit nem közvetlen szabad drag: célslotcsere, escrow és reconnect-recovery útvonalat használ.
- A crate spin kozmetikai: a settlement a GUI megnyitása előtt megtörtént; bezárás csak az animációláncot állítja le.

---

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

### Gépileg ellenőrzött configszekciók

<!-- icesmp-doc-id: config.abundance -->
<!-- icesmp-doc-id: config.achievements -->
<!-- icesmp-doc-id: config.advancements -->
<!-- icesmp-doc-id: config.afk -->
<!-- icesmp-doc-id: config.ambient-events -->
<!-- icesmp-doc-id: config.archeology -->
<!-- icesmp-doc-id: config.banking -->
<!-- icesmp-doc-id: config.bard -->
<!-- icesmp-doc-id: config.bestiary -->
<!-- icesmp-doc-id: config.buyer -->
<!-- icesmp-doc-id: config.campfire-story -->
<!-- icesmp-doc-id: config.caravan -->
<!-- icesmp-doc-id: config.chat -->
<!-- icesmp-doc-id: config.chronicle -->
<!-- icesmp-doc-id: config.city-guards -->
<!-- icesmp-doc-id: config.claims -->
<!-- icesmp-doc-id: config.classes -->
<!-- icesmp-doc-id: config.combat -->
<!-- icesmp-doc-id: config.community-goals -->
<!-- icesmp-doc-id: config.corruption -->
<!-- icesmp-doc-id: config.crafted-by -->
<!-- icesmp-doc-id: config.crafting-restrictions -->
<!-- icesmp-doc-id: config.crates -->
<!-- icesmp-doc-id: config.crates-settings -->
<!-- icesmp-doc-id: config.cultists -->
<!-- icesmp-doc-id: config.currency -->
<!-- icesmp-doc-id: config.daily-quests -->
<!-- icesmp-doc-id: config.dark-undead -->
<!-- icesmp-doc-id: config.dev-items -->
<!-- icesmp-doc-id: config.display-fx -->
<!-- icesmp-doc-id: config.donation-chest -->
<!-- icesmp-doc-id: config.dungeon -->
<!-- icesmp-doc-id: config.dynamic -->
<!-- icesmp-doc-id: config.end-portal -->
<!-- icesmp-doc-id: config.escort -->
<!-- icesmp-doc-id: config.exchange-board -->
<!-- icesmp-doc-id: config.faction-shops -->
<!-- icesmp-doc-id: config.factions -->
<!-- icesmp-doc-id: config.ferry -->
<!-- icesmp-doc-id: config.fishing-windfall -->
<!-- icesmp-doc-id: config.gathering-buffs -->
<!-- icesmp-doc-id: config.guilds -->
<!-- icesmp-doc-id: config.health -->
<!-- icesmp-doc-id: config.hidden-spots -->
<!-- icesmp-doc-id: config.honor-duel -->
<!-- icesmp-doc-id: config.hud -->
<!-- icesmp-doc-id: config.item-rarity -->
<!-- icesmp-doc-id: config.kill-rewards -->
<!-- icesmp-doc-id: config.loot -->
<!-- icesmp-doc-id: config.market -->
<!-- icesmp-doc-id: config.memory-shards -->
<!-- icesmp-doc-id: config.meteor -->
<!-- icesmp-doc-id: config.mob-money-drop -->
<!-- icesmp-doc-id: config.mob-scaling -->
<!-- icesmp-doc-id: config.moderation -->
<!-- icesmp-doc-id: config.motd -->
<!-- icesmp-doc-id: config.nether-portal -->
<!-- icesmp-doc-id: config.onboarding -->
<!-- icesmp-doc-id: config.pakt -->
<!-- icesmp-doc-id: config.parkour -->
<!-- icesmp-doc-id: config.party -->
<!-- icesmp-doc-id: config.pets -->
<!-- icesmp-doc-id: config.player-caravan -->
<!-- icesmp-doc-id: config.profession-materials -->
<!-- icesmp-doc-id: config.profession-recipes -->
<!-- icesmp-doc-id: config.profession-weekly -->
<!-- icesmp-doc-id: config.professions -->
<!-- icesmp-doc-id: config.quest-npc-fallback -->
<!-- icesmp-doc-id: config.quest-npc-markers -->
<!-- icesmp-doc-id: config.quest-toast -->
<!-- icesmp-doc-id: config.quests -->
<!-- icesmp-doc-id: config.rare-variant -->
<!-- icesmp-doc-id: config.relics -->
<!-- icesmp-doc-id: config.rituals -->
<!-- icesmp-doc-id: config.runes -->
<!-- icesmp-doc-id: config.season-monument -->
<!-- icesmp-doc-id: config.seasonal-events -->
<!-- icesmp-doc-id: config.seasonal-events-enabled -->
<!-- icesmp-doc-id: config.server-challenge -->
<!-- icesmp-doc-id: config.settings -->
<!-- icesmp-doc-id: config.signature -->
<!-- icesmp-doc-id: config.sit -->
<!-- icesmp-doc-id: config.soulforge -->
<!-- icesmp-doc-id: config.souls -->
<!-- icesmp-doc-id: config.specializations -->
<!-- icesmp-doc-id: config.spell-balance -->
<!-- icesmp-doc-id: config.spell-vfx -->
<!-- icesmp-doc-id: config.spells -->
<!-- icesmp-doc-id: config.spy -->
<!-- icesmp-doc-id: config.tablist -->
<!-- icesmp-doc-id: config.talents -->
<!-- icesmp-doc-id: config.territory -->
<!-- icesmp-doc-id: config.treasure-events -->
<!-- icesmp-doc-id: config.wild-hunt -->
<!-- icesmp-doc-id: config.world-events -->
<!-- icesmp-doc-id: config.world-tweaks -->
<!-- icesmp-doc-id: reference.configuration -->

---

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

### Teljes gameplay playtest-mátrix

Ez a dokumentum a **teszterek** támpontja: mit kell tesztelni, hogyan, és mi van/mi nincs a
pluginban. Végig lehet menni rajta pontról pontra. A jelölőnégyzeteket ( `[ ]` → `[x]` ) ki lehet
pipálni egy másolt példányban.

> **Jelölések:** ✅ = kész, tesztelendő • 🚧 = részben kész • ⏳ = nincs benne (ne teszteld) •
> ⚠️ = Folia-kritikus pont (külön figyelni a konzol-hibákra)

---

### 0. Környezet és build

- **Szerver:** Folia **1.21.11** (NEM sima Paper — a plugin Folia-szálkezelést használ).
- **Java:** 21.
- **Build:** `./gradlew build` → a jar a `build/libs/` alatt. (A plugin forrása fordul: a teljes
  kódbázis lefordul `javac 21`-gyel a Paper 1.21.11 API ellen.)
- **Opcionális függőség:** **LibsDisguises** (soft-depend). Ha telepítve van, a **Druida formák**
  vizuálisan is átalakítják a játékost; nélküle a forma csak stat-szinten vált. Érdemes mindkét
  állapotot tesztelni (telepítve / nélküle).
- **Scoreboard / tablist:** az IceSMP számára szükséges tablist-réteg
  (header/footer, nevek, nametag+rendezés, ping-oszlop, oldalsáv) **natív**, de
  nem cél a TAB teljes upstream-paritása. A TAB csak külön runtime
  összehasonlítás és az átvételi döntés után távolítható el. Párhuzamos
  használatnál kapcsold ki az egyik megjelenítési réteget, mert ütközhetnek.
  Beállítások: `config/tablist.yml` + `general.yml` → `hud.sidebar`.
  Külső plugin megtartásakor: `tablist.enabled: false` +
  `hud.sidebar-enabled: false`, és az IceSMP-adatok `%icesmp_...%`
  placeholderekkel érhetők el PlaceholderAPI-val. Az együttfutási szabályokat
  a [legújabb változások](LATEST_CHANGES.md#külső-pluginok-rollout-státusza)
  foglalják össze.
- **Telepítés:** a jar a `plugins/` mappába, indítás, majd a `plugins/IceSMP/config/*.yml`
  szerkeszthető és `/icesmp reload`-dal (vagy újraindítással) frissíthető. Néhány érték a manager
  indulásakor töltődik be — ha egy config-változás nem üt át reload-ra, **indítsd újra** a szervert.
- **Ingame config-vezérlés:** `/icesmp config set <kulcs> <érték>` bármely kulcsot felülbírál
  játékon belülről (a `config.yml`-be íródik, azonnali reload + validátor); `get`/`find` a
  kulcsok felderítéséhez (tab-complete a teljes kulcstérből), `unset`/`list` a felülbírálások
  kezeléséhez. Spell-mana példa: `/icesmp config set spell-balance.hide.resource-cost 25`.
- **FRISSÍTÉS a deployed baseline-ról:** az autoritatív futó baseline a csatolt
  `IceSMP-1.0-TESTING.jar` (`da039f0…a05`). A JAR nem hordoz Git SHA-t, ezért
  exact mapping nem bizonyítható; a resource- és bytecode-összevetés alapján
  a július 12-i `775d9e2…` állapot a nagy bizonyosságú forrásjelölt. Az új
  jar feltöltése után az
  **új config/üzenet-fájlok maguktól
  kicsomagolódnak** első indításkor, a régiek megmaradnak (a hiányzó kulcsok biztonságos
  alapértékre esnek, a ConfigValidator a konzolon jelzi az elgépeléseket).

#### Kompatibilitás az éles szerver plugin-készletével 🔌
Az IceSMP-t úgy készítettük, hogy az éles plugin-listával együtt fusson. A lényeges pontok:

| Plugin | Mit kell tudni / beállítani |
|---|---|
| **TAB** | ⚠️ Az IceSMP-hez szükséges natív subset elérhető (`config/tablist.yml` + `hud.sidebar`), de ez nem teljes TAB-klón. **A TAB.jar csak runtime összehasonlítás és elfogadott rollout-döntés után törölhető.** Együtt futtatáskor az egyik tablista/sidebar réteget kapcsold ki. Külső TAB megtartásakor: `tablist.enabled: false` + `hud.sidebar-enabled: false`; az IceSMP-adatok `%icesmp_...%` placeholderekkel érhetők el. |
| **WorldGuard** | ✅ Automatikus: a blokkot helyező események (**meteor, kincs**) reflexiós hídon át **kerülik a WG-régiókat** (spawn/városok). Induláskor a konzol jelzi, ha a híd él. WG-régióban a mob-spawn flag blokkolhatja az esemény-mobokat (invázió/hajsza) — ez nem hiba, az esemény kecsesen kezeli. |
| **SimpleClaimSystem** | ⚠️ A natív `/claim` képesség rendelkezésre áll, de a régi SCS-claimek **nem konvertálódnak automatikusan**. Az SCS csak külön live-adatinventory, újraclaimelési/migrációs terv, runtime átvétel és rollback után távolítható el. |
| **LuckPermsChatFormatterFolia** | ⚠️ Natív chat-formázás elérhető (`chat.format-enabled`), de eltávolítás előtt élő formátum-, placeholder-, permission- és kompatibilitási teszt kell. Amíg mindkettő fent van, kapcsold ki az egyiket, különben dupla formázás történhet. |
| **GrimAC** | 🔎 Playtesten figyelni: a mozgató spellek (Villanás, Árnyéklépés, Hősi Szökellés, Dupla Ugrás, Fázisugrás…), az invázió-bajnok földcsapás-lökése és a frakció-elytrák okozhatnak fals riasztást. Ha igen: Grim-oldali exempt/enyhítés a jelzett check-re. |
| **CoreProtect** | A plugin által lehelyezett/visszaállított blokkok (meteor-kráter, kincsesláda) nem játékos-akciók, a CoreProtect nem naplózza őket — egy nagy területű **rollback a kráter-visszaállítás után** felesleges (magától visszaáll). |
| **VillagerTradeEdit** | A karaván-NPC (WanderingTrader) natív trade-GUI-ját az IceSMP letiltja és a saját boltját nyitja — a VTE a karaván-kereskedőt így nem érinti. Playtesten egyszer ellenőrizd. |
| **ViaVersion/Backwards** | Régi kliens-verziók a HUD unicode-jeleit (👑 ❤ ▮) és a hosszú oldalsáv-sorokat csonkíthatják — kozmetikai, nem hiba. |
| **FarmProtect** | Együttműködik: az IceSMP termés-listenerei `ignoreCancelled`-del futnak, a FarmProtect által tiltott esemény nem ad bónuszt. |
| **economist** | Külön gazdaság: az IceSMP saját frakció-valutát használ (nincs Vault-híd) — a két rendszer nem keveredik. |
| LuckPerms, FancyHolograms, AuMenus, voicechat, ImageFrame, Axiom/FAWE/goBrush/VoxelSniper, packetevents/ProtocolLib | Nincs ismert közvetlen ütközés. |
| GSit, CrazyCrates, SModeration, MiniMOTD | Natív kiváltásuk kódszinten elkészült, de a régi jar csak a saját runtime átvételi kapuja után távolítható el. |
| AxAFKZone / AxAPI | Nem része a cél-deploymentnek: jutalmazó AFK-zóna nincs. A repó `Other/plugins/` könyvtára csak audit-snapshot; az éles `plugins/` jarját, adatkönyvtárait és remap-cache-ét deploymentkor külön kell archiválni/törölni. |

#### Permissionök tesztelőknek
A legegyszerűbb, ha a tesztelő admin **OP** (minden node megvan), vagy egyetlen sorral:
**`icesmp.admin.all`** — regisztrált szülő-node, az ÖSSZES alábbi admin-node a gyereke
(wildcard-támogatás nélküli permission-pluginnal is működik). Pontosabb jogosztáshoz az
egységes `icesmp.admin.<domain>` séma:

| Node | Mire |
|---|---|
| `icesmp.admin.all` | **super-admin: az összes alábbi egyben** |
| `icesmp.admin.reload` | `/icesmp reload` |
| `icesmp.admin.config` | `/icesmp config get/set/unset/list/find` (ingame config-felülbírálás) |
| `icesmp.admin.events` | világesemény-triggerek |
| `icesmp.admin.job` | kaszt XP / Lélekkapocs / spell-unlock / `/class admin` |
| `icesmp.admin.currency` | valuta-egyenleg beállítás |
| `icesmp.admin.faction` | frakció-kényszerítés, király/kassza admin-műveletek |
| `icesmp.admin.quest` | küldetés force-complete + a `/quest admin` szerkesztő |
| `icesmp.admin.relic` | relikvia adása |
| `icesmp.admin.sinner` | `/sinner` bűn-kezelés |
| `icesmp.admin.territory` / `icesmp.admin.territory.bypass` | területkezelés (+ királyság-spawnok) / zóna- és claim-védelem teljes megkerülése (build+interakció+PvP) |
| `icesmp.territory.builder` | építő-jog: védett zónában is építhet/interaktálhat (PvP-tiltás rá is áll) — szerep-node, NEM admin |
| `icesmp.admin.parkour` / `icesmp.admin.exchangeboard` / `icesmp.admin.profession` / `icesmp.admin.spec` | parkour / tábla / szakma / spec admin |
| `icesmp.admin.npc` | `/npcbind` — NPC kötése küldetéshez/bolthoz/bankárhoz/valutaváltóhoz/frakció-menühöz/parancshoz |

> ♻️ **Visszafelé kompatibilis:** a régi nevek (`icesmp.admin`, `icesmp.job.admin`,
> `icesmp.currency.admin`, `icesmp.faction.admin`, `icesmp.relic.admin`) alias-ként
> regisztrálva maradnak — a meglévő LuckPerms-beállítás átírás nélkül tovább működik,
> de új jogosztásnál már a kanonikus `icesmp.admin.<domain>` nevet használd.

---

### 1. Gyors tesztelő-setup (időkapuk megkerülése)

Egy teszt-karakter beállítása másodpercek alatt (a `<j>` a játékos neve):

```
/faction set <j> RED                 # frakció kényszerítése (RED/BLUE/NEUTRAL/DARK)
/currency set <j> 5000 RED           # valuta a bankhoz/teszthez
/class addxp <j> primary 100000      # gyors szintezés (max szint 50)
/class givecatalyst <j>              # a kaszt Lélekkapcsa (spellbook-tárgy)
/class unlockspell <j> <spell_id>    # konkrét spell azonnali feloldása
/spec choose <id>                    # spec választása (25. szint kell hozzá)
```

#### Időzített események azonnali kiváltása ⚠️ (a legfontosabb teszt-parancsok)
```
/events blood-moon start   # vérhold most (stop: /events blood-moon stop)
/events worldboss          # világboss spawn a közeledbe
/events invasion           # invázió-horda indítása köréd
/events caravan arrive     # kereskedő-karaván most (távozás: /events caravan depart)
/events ambient            # véletlen hangulat-esemény kiváltása
/events gathering          # véletlen gyűjtögető buff-ablak megnyitása
/events treasure           # elrejtett kincs a közeledbe
/events wild-hunt          # kóborló elit fenevad (Vad Hajsza) idézése
/events abundance          # Bőség-idő (a vérhold pozitív ellenpárja)
/events challenge          # kollektív szerver-kihívás indítása
/events escort             # karaván-kíséret (konvoj + szörny-hullámok)
/events meteor             # meteor-becsapódás (kráter + kibányászható érc)
/events intro [j]          # bevezető cím-szekvencia újrajátszása
/events season             # szezon-pontállás
```

#### Egyéb teszt-triggerek
```
/quest complete <j> <quest_id>   # küldetés azonnali teljesítése
/quest admin create|set|delete   # küldetés-szerkesztő: quest készítése JÁTÉKON BELÜL, kód nélkül
/relic give <j> <relic_id>       # relikvia adása (loot-teszt)  — id-k: /relic list
/sinner set <j>                  # bűnössé tétel (Sötét-paktum teszt); clear/add/status is van
```

#### Config-gyorsítás teszthez (`config/world.yml`)
A valós spawn-időközök hosszúak; teszthez érdemes csökkenteni, majd `/icesmp reload` vagy restart:
```yaml
world-events:
  check-interval-seconds: 10          # alap 60
  world-boss: { check-interval-minutes: 2, chance-percent: 100 }
  invasion:   { check-interval-minutes: 2, chance-percent: 100 }
  blood-moon: { chance-percent: 100 }
mob-scaling:
  blocks-per-level: 100               # alap 1000 — így közelebb is erős mobok jönnek
```
(De a force-parancsok — `/events …` — gyorsabbak, mint a config-hangolás.)

---

### 2. Mi VAN a pluginban (rendszer-leltár) ✅

A teljes leírás a [PLAYER_GUIDE.md](PLAYER_GUIDE.md)-ban; röviden, ami tesztelhető:

- **Frakciók** (4): Piros/Kék/Semleges/Sötét, passzív bónuszokkal és valutával.
- **Kasztok** (13) + **specializációk** (35), egy kaszt/játékos (végleges, admin-reset van), 50-es max szint.
- **Képességek**: 419 konfigurált unlock-ID és 420 balanszprofil; ez
  konfigurációs képesség, nem automatikus bizonyíték 420 külön runtime spellre.
  Lélekkapocs-tárgy, **hibrid költségrendszer** (Erő-csík + HP/XP/éhség),
  cooldown, kombók, spell-mesterség.
- [ ] **Közelharci Lélekkapocs**: melee kaszttal (pl. Harcos) a kézben tartott kard/balta is
      Lélekkapocs — jobb katt cast, SHIFT+jobb katt varázskönyv, SHIFT+ütés spell-váltás; a balta
      jobb-katt fahántása NEM fut le; nem-melee kaszttal (pl. Varázsló) a kard NEM Lélekkapocs.
- [ ] **Dinamikus spell-skálázás**: magasabb kaszt-szinten mérhetően nagyobb spell-sebzés
      (+0,5%/szint), az Arkán Hatalom talent rangonként +2%-ot ad; a bónusz +50%-nál tetőzik.
- **Erő-csík** (osztály-erőforrás): HUD-sáv, regenerálódó költség-pool.
- **Talentek**: kaszt- és szakma-ponttár, általános + kötött talentek.
- **Szakmák** (gyűjtögető/készítő/másodlagos) + szakma-specializációk + craft-korlátok.
- **Gazdaság**: bank, valuta, dinamikus árfolyam, valutaváltás, piactér, állampolgári adó,
  kereslet-sokk, árfolyam-hologramok, lélekkő-drop.
- **Relikviák**: Mételytépő (fegyver, PvP-transzfer) + 4 frakció-elytra; rituálé-oltárok.
- **Pet/minion**: Vadmester & Nekromanta társak (befogás, szint, parancsok), lélekszilánk-bajnok.
- **Küldetések**: 4 kezdő kaszt-próba, Sötét Beavatás, vezeklés-lánc, napi küldetések.
- **Bűn-rendszer**: gyilkosság/árulás/lopás → bűn → 4-nél száműzetés a Sötétbe (örök paktum).
- **Királyság/raid/szezon**: királyválasztás, kassza, adó, raid, hadizsákmány, liga-pontok.
- **Világesemények**: távolság-alapú mob-szintezés, vérhold, világbossok (10 archetípus, 2 fázis),
  inváziók (horda + bajnok), szezonális liga.
- **Sámán-totemek**, **Druida-formák**, **parkour-pályák**, **GUI-k** (profil, menü, spellkönyv,
  piac, ranglista, elérések), **HUD** (oldalsáv + bossbar).

---

### 3. Mi NINCS / részleges (NE teszteld hibaként) ⏳🚧

- ✅ **Bűn-rendszer teljes:** gyilkosság (+1), **árulás** (frakciótárs ölése, +2) és **lopás**
  (idegen territóriumban konténer-fosztás, +1) is bűn — teszteld mindhármat!
- ✅ **Raid teljes:** jelentkezés + létszámkorlát (10v10), területkötés, pont-tartás objektíva,
  terület-átvétel — teszteld a 4.12 szerint!
- 🚧 **Kaszt-questek:** a 4 kezdő próba + az NPC-s mester-láncok (mentor-NPC → próbapálya)
  **készek a pluginban** — de a mester-NPC-k (FancyNpcs) és a próbapályák kihelyezéséig a
  láncok nem haladnak. Teszthez rakj ki egy NPC-t (`/npc create harcos_mester`) és egy pályát.
- 🚧 **Piactér:** fizikai piactábla még nincs (a lapozás, a `/market search`, a
  reputáció-árazás és a **licitálós aukciósház** kész — ezeket teszteld!).
- 🚧 **Intro:** a kamera-utaztatás kész, de alapból kikapcsolt (waypoint-kijelölésig).
- 🚧 **Szezonliga:** pontgyűjtés kész, a győztes **kozmetikai jutalma** még nincs.
- ⏳ **Külön „ultimate" / burst-rendszer** (a korábbi kirobbanás-mechanika kivéve).
- ⏳ **Világépítés** (fővárosok, Sötét romváros, loot-asztalok) — szerver-csapat feladata; a plugin
  csak az eszközt adja (`/territory`).
- ❌ **Másodlagos kaszt: NINCS** — a rendszer tudatosan törölve lett (egy kaszt / játékos, a
  választás végleges; admin-reset: `/class admin resetclass`). Ne jelentsd hibaként, hogy nem
  vehető fel második kaszt!

---

### 4. Tesztelési checklista rendszerenként

#### 4.1 Frakciók és passzívok ✅
- [ ] `/faction join <red|blue|neutral|dark>` és `/faction leave` működik; a Sötétbe csak bűnös léphet.
- [ ] **Piros:** állj tűzbe / lávába / magma-blokkra → **nincs sebzés**.
- [ ] **Kék:** powder snow-ban / fagyos bistromban → **nincs fagy-sebzés**; merülj víz alá hosszan →
      **nem fulladsz** (fulladás-immunitás); éhség kb. fele olyan gyorsan fogy.
- [ ] **Semleges:** ess le magasról → **nincs zuhanás-sebzés**; a semleges mobok és **endermanök**
      nem támadnak (ránézésre sem aggrózik az enderman); **nem fizet állampolgári adót**.
- [ ] **Sötét:** wither-rózsa/wither-effekt → **nincs sebzés**; zombi/csontváz/phantom/zoglin
      **nem támad** rád. (A láthatatlanság SZÁNDÉKOSAN megszűnt — ne teszteld hibaként.)
- [ ] `factions.passives.enabled: false` → minden passzív kikapcsol.

#### 4.1.1 Királyság-spawnok és váltás-kapu ✅
- [ ] **Spawn-kijelölés:** `/territory setspawn <frakció>` a PONTOS álláspontot menti (magasság +
      nézésirány) — ellenőrizd egy fa alatt állva, hogy NEM a fa tetejére kerülsz.
- [ ] **K2 signature (BLUE):** a 3 Cryghaliris-recept csak Fagy-frakcióval craftolható (más
      frakciónak hibaüzenet, a csempén ⚑ sor); Kallan Szeletelője nyila gyorsabb és többet sebez;
      a Jégvért viselve csökkenti a sebzést; a Kantár jobb kattra gyorsítja a hátast, elfogy, és
      ugyanarra a hátasra másodszor nem fogy el.
- [ ] **K3 signature (RED):** a 3 Perinfernicitas-recept csak Láng-frakcióval craftolható; a
      Tűzköpő Quick Charge-dzsal készül és lövedéke gyorsabb; az Agyar többet sebez (baltával az
      off-handben még többet); a Tollköpeny viselve kioltja a tűz/láva/forró-blokk sebzést.
- [ ] **K4 signature (NEUTRAL, ÚJ):** a 4 Menedék-recept csak Menedék-frakcióval craftolható
      (bányász 45 / horgász 40 / rúnaírnok 35 / füvész 45, tervrajzból). A Csákány érc-töréskor
      ~20% eséllyel extra dropot ad (bányász-láz ALATT szünetel — nincs stack); a Horgászbot
      ~20% eséllyel duplázza a fogást (halászati láz alatt szünetel); a Bankbetét jobb-kattra
      elfogy és +25 Creutzért ír jóvá (dupe-teszt: gyors dupla-katt se duplázzon); a
      Szellemszarvas-Bűbáj jobb-kattra gyors, ideiglenes hátast idéz (~90 mp, ~120 mp cooldown,
      a bűbáj NEM fogy el). Kulcsok: `signature.*` a `crafting.yml`-ben.
- [ ] **Signature-enchantok (ÚJ — bootstrap):** craftolj egy signature itemet → a tooltipben a
      neve alatt VALÓDI enchant-sor jelenik meg magyar lore-névvel (Kallan-íj: „Jégfog", Tűzköpő:
      „Vihartűz", Agyar: „Vérszomj", Jégvért: „Fagypáncél", Tollköpeny: „Főnixtoll", Csákány:
      „Érc-érzék", Horgászbot: „Bokic Kegye") — kliens-mod és resource pack NÉLKÜL (data-driven
      enchant-registry, a kliens szinkronizálja). Kapcsoló: `signature.custom-enchants`.
      ⚠️ Enchant-asztalról EGYÁLTALÁN nem jöhetnek (nincsenek az in_enchanting_table tagben).
      **Rider-effektek (audit — egyik enchant sem dísz):** Jégfog = nyíl-találat 2 mp lassítás;
      Vihartűz = lövedék 2 mp gyújtás; Vérszomj = sebzés 10%-a gyógyít (max 1 szív/ütés);
      Érc-érzék = +2 XP-gömb érc-törésnél; Bokic Kegye = fogáskor 30 mp Szerencse;
      Fagypáncél/Főnixtoll = iskola-ellenállás. Kulcsok: `signature.enchant-riders.*`.
      A resist-nél NINCS action-bar kiírás (spam lenne) — a sebzés-számok mutatják. ⚠️ A bootstrap-regisztráció új
      (unstable) Paper API — az ELSŐ gradle-build és szerverindulás kiemelt teszt: ha a konzol
      bootstrap-hibát dob, a `signature.custom-enchants: false` NEM elég, a Bootstrap-osztály
      regisztrációját kell javítani.
- [ ] **Iskolás mágia damage-type-ok + Rúnavért (ÚJ — bootstrap):** a spellek a saját ISKOLÁJUK
      damage-type-jával sebeznek (icesmp: tuz/fagy/szent/arnyek/termeszet/vihar/kaosz_magia +
      magia=ősmágia). Besorolás: `spells.spell-schools.by-spell.<id>` felülírás → a caster
      kasztjának `by-class` defaultja (pl. paladin=szent, death_knight=fagy, warlock=kaosz) →
      ősmágia — MINDEN spell egyedivé hangolható reload-dal. A kill-attribúció (bűn/raid/
      bounty) változatlan. **Rúnavért** (rúnaírnok 40 recept, üllőn páncélra, max 3):
      szintenként −8% MINDEN mágia ellen; **iskola-counterek**: a Fagypáncél a fagymágia, a
      Főnixtoll a tűzmágia ellen ad szintenként −10%-ot — a kettő összeadódik, 60% plafonig
      (`spells.magic-resist.*`). Vanília sebzésre egyik sem hat. Spell-halálnál magyar,
      iskolás halál-üzenet ("elemésztette a fagymágia").
      ⚠️ Teszt: fagy-spell Fagypáncéllal (számít) és tűz-spell-lel (nem számít rá az
      iskola-counter, csak a Rúnavért); kill-attribúció spell-öléssel; action-bar jelzés;
      per-item enchant-kapcsoló (`signature.custom-enchants.items.*`); első gradle-build.
- [ ] **K6 frakció-ételek (ÚJ):** a 3 séf-recept frakció-kapus (Pisztráng BLUE 25 / Rántotta RED 25 /
      Sütemény NEUTRAL 35). A Pisztráng evése rövid felszívódás-pajzsot, a Rántotta tűz-ellenállást
      ad; a Sütemény „robban" (felfelé lökés + Speed II + tűzijáték-effekt, blokk-kár nélkül).
      **Honvágy-teszt:** BLUE/RED játékos `factions.food-duty.grace-hours` (teszthez állítsd 0.01-re
      → reload) után `check-minutes`-enként rövid Éhséget + action-bar emlékeztetőt kap; bármely hal
      (BLUE) / tojás-étel (RED) evése nullázza. Új/frissen váltó játékos először türelmi időt kap.
      A DARK a **Mortengradi Hamukenyeret** kapja (éjjellátás-buff), de honvágy-kötelezettsége
      NINCS (nincs otthonuk); a NEUTRAL-nak sincs kötelezettsége.
- [ ] **K8 Emlékszilánkok (ÚJ):** az Opálos Emlékszilánk ritkán esik skálázott moboktól (unique-sor,
      3-as súly), boss/event-mobtól biztosabban (8-as súly). `/emlek` mutatja az egyenleget és az
      árakat; `/emlek xp` (3 szilánk → 500 kaszt-XP), `/emlek talent` (5 → +1 kaszt-talentpont, a
      /talent pool beszámítja), `/emlek spec` (8 → a spec-választás SZINT-kapuja elesik — a kaszt/
      frakció/bűnös/quest kapuk maradnak; másodszor nem váltható be), `/emlek lore` (1 → véletlen
      emlék-töredék). A levonás atomi (nincs dupe); kulcsok: `memory-shards.*` (general.yml).
- [ ] **K9 Suttogók (ÚJ):** élőhalottól nagyon ritkán esik a **Suttogás** meghívó (boss-tól eséllyel).
      **Sötét Rítus:** éjjel, SCULK-blokkon állva, EGYEDÜL (16 blokkon belül nincs más játékos),
      SHIFT+jobb katt a meghívóval → vér-áldozat (−6 HP) + Suttogó-státusz (rejtett!). Ha valaki a
      közelben van: a rítus meghiúsul, a szemtanúk Tanú-tokent kapnak, a jelölt nagy gyanút.
      **Titkos csatorna:** `/suttogas <üzenet>` — a rejtett Suttogók **ÉS a Kitaszítottak (DARK)**
      látják; kívülállónak „csak a szél zúg". Teszt: egy DARK-játékos kapja meg a Suttogó üzenetét,
      és ő maga is tudjon írni bele; egy RED/BLUE/NEUTRAL nem-Suttogó semmit ne lásson.
      `factions.whisper.dark-hears-channel: false` + `/icesmp reload` → a DARK kiesik a csatornából.
      **A titok ára:** a csatorna-sor kiírja a feladó nevét, tehát a DARK-játékos MEGTUDJA, ki
      Suttogó — ellenőrizd, hogy ez tudatos döntés marad (nyílt chatbe kiszivárogtathatja).
      **Gyanú:** rajtakapott testvérgyilkosság (+40) és rajtakapott rítus (+50) növeli; a szemtanúk
      `/suttogas vád <játékos>` váddal (+15, csak IGAZI Suttogóra hat, tokent fogyaszt) nyomozhatnak
      (a `vad` és `accuse` alak is működik — ne kelljen ékezetet gépelni);
      10 percenként −5 csillapodás. **Lelepleződés** (100 pont): fény-dráma + broadcast + 4 bűn →
      a bűn-küszöb a meglévő száműzetéssel a Kitaszítottak közé taszít. Kulcsok: `factions.whisper.*`.
- [ ] **K5 Káoszkor-loot:** élőhalott mobból (zombi/csontváz) eshet a Rozsdás Penge / Megrontott
      Elit Páncél (nevesített, rarity-prefixszel + affixekkel); NEM-élőhalottból sosem esik;
      az Eleftheria Könnye rituálé csak DARK-frakcióval aktiválható (síró obszidián mag).
- [ ] **K1 kánon-nevek:** a HUD/tab a rövid frakciónevet mutatja (Láng/Fagy/Menedék/Kitaszított),
      a /menu és a Profil a hosszút (pl. Láng (Perinfernicitas)); a valuta-itemek neve Parázsló
      Parals / Hópihér-veret / Creutzér / Csontveret; a `/faction join piros` (legacy név) is működik.
- [ ] **Első belépés:** vadonatúj fiók a Menedék spawnján jelenik meg (nem a világ-spawnon);
      az intro-kamera utána lejátszódik.
- [ ] **Választás-teleport:** `/faction join red` után a Piros Királyság spawnjára kerülsz
      („Üdvözöl a(z)…" üzenettel); `factions.spawn.teleport-on-join: false` → nincs teleport.
- [ ] **Respawn:** ágy/horgony nélkül meghalva a SAJÁT frakciód spawnján éledsz; ággyal az ágynál.
- [ ] **Váltás-kapu:** kijelölt semleges főváros mellett frakciót váltani (join ÉS leave) csak a
      semleges fővárosban állva lehet — máshol hibaüzenet; semleges főváros nélkül a kapu nem zár.
- [ ] **Leave = fizetős váltás:** nem-semleges frakcióból `/faction leave` levonja az 500-at és
      indítja a 72 órás cooldownt (a leave+join kerülőút megszűnt).
- [ ] **Hírnök-NPC:** `/npcbind <npc> faction` után az NPC jobb-kattra a frakció-menüt nyitja.

#### 4.2 Kasztok, Lélekkapocs, szintezés ✅
- [ ] `/profile` → Kaszt menüből mind a **13 kaszt** választható; **egy kaszt** vehető fel, utána a
      menü „Már van kasztod" jelzést ad; `/class admin resetclass` után újra választható.
- [ ] A Lélekkapocs a kaszthoz illő tárgy (pl. Varázsló = bűvölt könyv); **jobb katt** = cast,
      **lopakodás + ütés** = váltás a feloldott spellek közt (action bar mutatja a kiválasztottat + költséget).
- [ ] A Lélekkapocs craftnál/kemencében **nem használódik el** (védett).
- [ ] Mob-öléssel nő a kaszt-XP; magasabb mob-szintű mob több XP-t ad (alap 10 + 3/mob-szint).
- [ ] ⚠️ **Folia:** ölj mobot egy **régióhatáron / messziről** → az XP/üzenet hibamentesen érkezik
      (figyeld a konzolt „region"/IllegalStateException-re).

#### 4.3 Erő-csík + hibrid költség ✅ (FRISS — kiemelt teszt)
- [ ] A HUD oldalsávban látszik az **Erő-csík** (kasztonként más név/szín: Mana/Düh/Energia/Fókusz/Csi…).
- [ ] Egy hétköznapi spell elsütése **csökkenti** a csíkot; idővel **visszatöltődik** (~8/mp).
- [ ] **Üres csíknál** a spell **nem sül el** → action bar: „Nincs elég <erőforrás>!".
- [ ] **Hibrid költségek** (a spellkönyv `/spellbook` minden spellnél kiírja a költség típusát):
  - [ ] **vér-mágia** (pl. Berserker Vérszomj, Nekromanta vér-spelljei) → **életbe (❤)** kerül.
  - [ ] **nagy rituálé/idézés/időjárás/ulti** (XP ≥ 80) → **XP-be** kerül (pl. Esőtánc, Holtak Hada).
  - [ ] **nehéz fizikai** (éhség ≥ 8: állások, Második Lélegzet, Pandaőrség) → **éhségbe** kerül.
  - [ ] minden más → az **Erő-csíkba**.
- [ ] No-op cast (nincs célpont/társ) → a költség **visszatérül**, és nincs cooldown.
- [ ] **Kaszt-profilok (ÚJ):** a tár kasztonként másképp viselkedik (`spells.resource.class.*`):
  - [ ] **Düh-típus** (harcos/halállovag/démonvadász): harcon kívül a csík **lassan ürül** (2/mp),
        ütésenként **+8** töltődik, harcban (5 mp-en belüli ütés) lassú regen (3/mp) fut.
  - [ ] **Energia-típus** (orgyilkos/szerzetes 14/mp, íjász 11/mp): gyors visszatöltődés.
  - [ ] **Mana-típus** (varázsló/sámán/pap/boszorkánymester/evoker/druida 120 max, paplovag 110):
        nagyobb tár, lomhább (7/mp) regen — a HUD-sáv maximuma is a kaszt szerintit mutatja.
  - [ ] Lövedékkel (íj) bevitt találat is számít harci ütésnek (düh-töltés + harc-időbélyeg).
- [ ] `spells.resource.enabled: false` → minden spell a régi éhség/XP/HP költségre vált.
- [ ] *(Ismert finomhangolandó: néhány határeset-spell — pl. Gyökerezés 8 éhségen — a küszöb miatt
      éhséget kér, pedig a Mana is illene rá. Jelezd, ha furcsát látsz.)*

#### 4.4 Specializációk ✅
- [ ] 25. szinten `/spec choose <id>` (vagy a Specializáció menü) elérhető; a menü mutatja a feltételt.
- [ ] **Nekromanta** csak Kitaszítottként (Sötét frakció) + bűnösként + a Sötét Beavatás után választható.
- [ ] A spec feloldja a 25–45. szintű spelleket; a szerep illik (tank/heal/dps/caster/ranged).
- [ ] `/spec respec` visszavált valutáért; a spec-kötött talentpontok visszatérülnek.
- [ ] Hibrid kasztok: pl. Holy paplovag gyógyít, Retribution sebez (eltérő spell-pool).

#### 4.5 Spellek, kombó, mesterség ✅
- [ ] Több reprezentatív spell elsül és a leírt hatást teszi (sebzés/effekt/teleport/idézés).
- [ ] **Spell-VFX (ÚJ):** a spell-effekt FORMÁZOTT — célzott spell **sugarat** húz a célpontig,
      AoE spell **gyűrűt** rajzol a hatókör mentén, önmagadra ható spell **hélixet** csavar fölfelé;
      a szín a spell jellegéhez illik (tűz narancs-vörös, fagy kék-fehér…). `spell-vfx.enabled: false`
      → visszaáll a régi gömb-puff. `spell-vfx.max-points` csökkentése ritkítja a formát.
- [ ] **Paletta spec szerint:** a szín a kaszt/spec témáját követi — pl. a **Fagy** halállovag-spellek
      (obliterate, frost_strike) kék-fehérek, a **Pusztítás** boszorkánymester (chaos_bolt, incinerate)
      narancs-vörös, az **Árnyék** pap (mind_flay) sötét-lila. Config: `spell-vfx.class-palettes.<spec>`;
      egyedi kivétel: `spell-vfx.overrides.<spell-id>`. Rossz/ismeretlen paletta-név → csendes fallback
      (accent-particle szín), nincs hiba.
- [ ] **Forma a spell jellegéhez:** közelharci csapás (**frost_strike, crusader_strike, blackout_kick,
      keg_smash**) → **becsapódás-csillag** a célponton (nem sugár); lehelet (**fire_breath,
      dream_breath, primal_roar**) → **kúp** a nézési irányban; dobott (**mana_dart, toxin_dart**) →
      **ív**. A többi marad a célzás-alapon (célzott→sugár, körzet→gyűrű, önmagad→hélix). Per-spell
      felülírás: `spell-vfx.shapes.<spell-id>`.
- [ ] **Hero-ultimate szín:** a 35 spec-csúcsspell (lvl 45) kiemelt palettát kap — pl. az
      **ascendance_flame** (elemental sámán) TŰZ-színű a spec kék-fehér helyett, a **serenity**
      (windwalker) ARANY. A színek a `spell-vfx.overrides`-ban hangolhatók.
- [ ] ⚠️ **Folia:** kasztolj **régióhatárra / messzi célpontra** → a sugár/forma hibamentesen
      megjelenik, nincs konzol-hiba.
- [ ] Cooldown működik; a **60 mp feletti** cooldown kilépés után is megmarad.
- [ ] **Shift+görgetés spell-váltás:** Lélekkapocsral a kézben lopakodva görgetve a spell vált
      (előre/hátra), a hotbar-slot NEM vált el, és a Lélekkapocs neve az épp kiválasztott
      képességet mutatja; Lélekkapocs nélkül a görgetés normál slot-váltás marad.
- [ ] **Spell-kedvencek (ÚJ):** a spellkönyvben **shift-katt** egy feloldott spellen → ★ jelölés
      (újra shift-katt: le); ha van legalább egy kedvenc, a shift+görgetés **csak a kedvenceket**
      lépkedi (action bar: „★1/3"); üres kedvenc-lista = a teljes feloldott lista. A spellkönyv
      **tölcsér-gombja** a „csak feloldottak" szűrőt kapcsolja.
- [ ] **Kombó:** egy konfigurált spell-pár (pl. Fagyérintés → Arkán Lökés) rövid időn belül →
      „⚡ Kombó!" + gyorsabb felépülés.
- [ ] **Kombó-láncok (ÚJ, 3 lépés):** egy konfigurált lánc (pl. varázsló: Fagyérintés → Arkán
      Lökés → Tűzgolyó; `spells.combos.chains`) mindhárom lépése az időablakon belül → a finisher
      **+25% erővel** sül el („⚡ Kombó-lánc befejező!") + cooldown-visszatérítés; sima cast után
      az action bar mutatja a **nyíló kombó-ablakot** („⏳ Kombó-ablak: <köv. spell>"), kombó után
      a lánc következő lépését.
- [ ] `/spell upgrade <id>` valutáért növeli a mesterség-rangot (max 5 rang): a cooldown csökken
      (-8%/rang) ÉS az erő nő (+5%/rang) — magasabb rangon egy sebző spell nagyobbat üt, egy
      buff/debuff spell effektje tovább tart, a self-heal többet gyógyít; a költség/self-damage nem nő.
- [ ] Idézett társak (Nekromanta/Vadmester) **nem fordulnak ellened**, a célpontodra támadnak, idővel eltűnnek.
- [ ] **Resource-cost override:** `/icesmp config set spell-balance.<id>.resource-cost <érték>` után a
      Spellbook AZONNAL az új árat mutatja és a cast annyit von le (cooldown-módosítás nélkül);
      `unset` után visszaáll a cooldown-sávos alapár.
- [ ] **Élő spell-balansz:** deklaratív spellnél (pl. egy sima sebző spell)
      `/icesmp config set spell-balance.<id>.damage <érték>` → a következő cast MÁR az új sebzést
      viszi és a Spellbook-lore is az új számot mutatja, restart nélkül (range/radius/heal/ignite/
      freeze/knockback ugyanígy).
- [ ] **Üres AOE-cast visszatérítés:** Lökéshullám/Gyökerezés/Megzavarás/Csontfagy célpont nélkül
      „Nincs célpont" üzenetet ad, a költség visszajár és NEM indul cooldown.
- [ ] **Újracast-védelem:** Belső Fókusz lefagyás alatt, Elrejtőzés aktív invis alatt nem castolható
      újra (a sebesség/páncél nem veszhet el).

#### 4.5.1 Playtest-balansz reworkök (Ice SMP 5 TEST doksi) ✅
- [ ] **Dobható spellek:** Vadgomba (gomba 3mp után robban: slowness+poison), Rúnacsapás (kő —
      hozzáérő ellenfél 7 sebzés), Démoni Kör (só-csapda poison), Átok Nyúl (nyúl-akna 3 szív splash) —
      az itemek nem vehetők fel, a kaszterre nem hatnak.
- [ ] **Lövedékek:** Szent Harag (meglévő élet 30%-a; világbosson/Wardenen HATÁSTALAN), Lélek Csere
      (25% átszáll rád), Sarlóvetés (bumeráng oda-vissza sebez), Tűzgolyó (splash a cél körül),
      Elmerobbantás (3mp múlva robban a jelölt cél).
- [ ] **Channelek:** Mély Lélegzet (3.5mp lángszórás a nézésirányba, mozgás közben követi), Forgószél
      (5mp pörgés, újracast tiltva közben), Szellemlátás (2mp-enként váltakozó invis 8mp-ig) — kilépésre
      mind leáll (nincs beragadt állapot).
- [ ] **Kiűzés Botja:** 5mp-re kapott Knockback 3 bot — nem dobható el, nem mozgatható, lejáratkor és
      kilépéskor eltűnik.
- [ ] **Áhítat Aurája:** 10mp-ig a támadók 1 visszavert sebzést kapnak; két aurás játékos egymást ütve
      NEM kerül végtelen pingpongba (max 2 visszaverés/mp).
- [ ] **Jéglánc:** pontosan max 2 célt fagyaszt (fő cél + legközelebbi 3 blokkon belül).
- [ ] **Fagysugár (volt Fagyláz):** 2 blokk széles sugár, minden útba esőt fagyaszt. (A név-ütközések
      feloldva: Halállovag = Fagysugár, Sárkányidéző = Lánggolyó — a spec-spellek neve változatlan.)
- [ ] **Spell-item modellek:** a Vadgomba, Rúnakő, Démoni Só és Kiűzés Botja ITEM_MODEL-t hordoz — resource packkel a textúrájuk cserélhető (`docs/RESOURCE_PACK_CMD.md`).
- [ ] **Megzavarás (rework):** mobokra is LÁTHATÓAN hat — a támadó mob leáll rólad (célpont-vesztés,
      10 mp-ig ismételve), lassabb és gyengébb; játékos-ellenfél megvakul (vakság+sötétség).
      A méreg-komponens kikerült (tiszta CC).
- [ ] **Druida-formák:** Párducformában −4 szív max-élet (visszaváltáskor/relogkor visszaáll);
      Medve: éhség-debuff; Hold: mining fatigue; Utazó: weakness.

#### 4.6 Talentek ✅
- [ ] `/profile` → Talentek: kaszt-ponttár (5 szintenként 1) és szakma-ponttár (10 szintenként 1).
- [ ] Általános talent (Életerő/Erő/Fürgeség/…) azonnal hat; kötött talent csak a feltételt teljesítőnél jelenik meg.
- [ ] Respec után a pontok visszatérülnek.

#### 4.7 Szakmák ✅
- [ ] `/profession join` 1 gyűjtögető + 1 készítő; Halász/Szakács alapból megvan.
- [ ] A megfelelő tevékenység ad XP-t (bányászat/aratás/horgászat/sütés/craft).
- [ ] **Creative-guard:** creative módú blokktörés NEM ad szakma-XP-t, és nem viszi a napi
      küldetés / quest / közösségi cél számlálóit sem.
- [ ] 25. szinten szakma-spec választható.
- [ ] **Craft-korlát:** netherite felszerelést csak 25+ Kovács craftol (különben nem jön létre + üzenet).
- [ ] **Craft-korlát bővítés (ÚJ):** netherite-rúd → Bányász 20; számszeríj/pajzs → Favágó 8;
      főzőállvány → Alkimista 5; bűvölő-asztal → Enchanter 5; torta/sütőtökös pite/nyúlpörkölt →
      Séf 6. Megfelelő szinttel elkészül, alatta NEM jön létre + kap üzenetet. A nyers alapok
      (íj, sült húsok, kőszerszám) bárkinek szabadok. Kikapcsolható: `crafting-restrictions.enabled`.
- [ ] **Recept-könyv** (`/profession recipes` vagy `/menu` → Recept-könyv): a szakmáid receptjei
      tanult (zöld) / zárolt (szürke) állapottal, hozzávaló megvan/hiányzik jelzéssel, lapozva;
      kattintásra craft (hozzávalók fogynak), `affix-tier`-es recept egyedi rolled tárgyat ad.
- [ ] **Tervrajz:** blueprint-recept zárolva marad, míg meg nem szerzed a tervrajzot (mob-drop
      ritkán / `/profession blueprint <j> <id>` admin). Jobb katt a tervrajzon → megtanulod
      (már ismert tervrajz nem fogy el); utána a recept-könyvből craftolható (szint is kell).
- [ ] **Rolled-affix mestermunka:** mestermunkát craftolva a tárgy VÉLETLEN minőséget ([Közönséges]…
      [Legendás]) + random attribútum-affixeket kap (a leírásban); két craft nem ugyanaz. Shift-click
      bulk-craft alap-statú marad.
- [ ] **WoW-mob loot (loot-tábla):** sima szörnyek `loot.mob-drop.chance` eséllyel dobnak egy
      táblasort: rolled `drop`-tier felszerelést (random névvel, akár Ócska/negatív affix), sokféle
      nyersanyagot/értéket, vagy **csak-mobból-eső egyedi alapanyagot** (Vad Esszencia / Szörny Mag
      / Árnyékpor). Boss/event-mob garantáltan `boss`-tier tárgyat + boss-only Fekete Villám Szilánkot.
      Szakma-craftolt nevesített tárgy SOHA nem esik mobból.
- [ ] **Mob-only alapanyag receptekben:** a mob-only egyedi alapanyagokat igénylő receptek
      (pl. Vadbőr Vért, Villámszilánk Pengéje) csak akkor craftolhatók, ha begyűjtötted a mobból-eső anyagot;
      a recept-könyv kékkel jelzi a hiányt.
- [ ] **Egyedi alapanyag védelem:** az egyedi szakma-alapanyagok (pl. Vasesszencia = IRON_NUGGET,
      Gyógy-kivonat = ehető GLOW_BERRIES) NEM használhatók normál módon: nem craftolhatók be,
      nem kovácsolhatók, nem tüzelők, nem ehetők, nem rakhatók le — csak a recept-könyvben. Van
      saját ITEM_MODEL-jük és frappáns lore-juk.

#### 4.8 Gazdaság ✅
- [ ] `/bank deposit|withdraw|balance`, `/currency balance|pay|exchange|rates`, `/currency set` (admin).
- [ ] **Adó: fejadó + hátralék (ÚJ):** üresre ürített számlával is jár a beszedésenkénti minimum
      (`factions.tax.minimum-amount`, default 2) — 0 egyenlegnél a teljes összeg **hátralékként**
      gyűlik (üzenet jelzi), plafonig (`max-arrears`, default 50); pénz érkezése után a következő
      beszedés a hátralékot is levonja. Restart után a hátralék megmarad (treasury.yml: tax-arrears).
      A Semlegesek továbbra is mentesek.
- [ ] **Adócsalás (ÚJ):** ha a hátralék a plafonon ragad és a beszedés semmit sem tud levonni,
      strike jár (`factions.tax.evasion-strikes`, default 3); a küszöbnél a Számvevők feljelentik
      az adócsalót → **+1 bűn** (üzenettel, online beszedéskor) — a bűn-küszöb elérése a meglévő
      száműzetést indítja (Kitaszítottak). A strike törlődik, amint a tartozás a plafon alá esik;
      restart-álló (treasury.yml: tax-evasion-strikes). 0 = kikapcsolva.
- [ ] **Relikvia halál-viselkedés (ÚJ — reclaim):** passzív relikviával (pl. szárny) halj meg:
      a tárgy KÖDDÉ VÁLIK (nem esik le, senki sem veheti fel), üzenet jelzi; a tulajdon marad —
      CSAK te idézheted újra az oltárnál (áldozattal); más játékosnak az oltár elutasít. Ha
      `relics.inactivity.lost-expiry-days` (default 3 nap) alatt nem idézed újra, a relikvia
      MINDENKINEK felszabadul. Módok: `relics.passive-death.mode: reclaim|keep|drop`.
- [ ] **Relikvia-dup javítások (ÚJ):** aktív tulajdonos NEM idézheti újra a meglévő relikviáját
      (az oltár elutasít — kivéve, ha "elveszett"); admin pótlás: `/relic give` (force). Belépéskor
      a VISELT szárny is része az inaktivitás-sweepnek és a dedupnak; halott másolat belépése nem
      veszi el az aktív tulajdonos jogát.
- [ ] **Ingame config-menü (ÚJ):** `/icesmp config menu` (jog: `icesmp.admin.config`) — kategóriák
      (Adó / Világesemények / Kárhozat / Suttogók / Ételek / Signature / Relikviák / Emlék);
      boolean: katt váltás; szám: bal +, jobb −, SHIFT ×5; mód-kulcs (pl. passive-death.mode):
      katt = következő opció. Minden módosítás a config.yml override-ba íródik és AZONNAL él
      (reload + validátor fut); a teljes kulcskészlet továbbra is `/icesmp config set|find`.
- [ ] **Dinamikus árfolyam:** több valuta a szerveren → kevesebbet ér (`/currency rates`).
- [ ] **Valutaváltó GUI** (`/menu` → Bank & Pénz → Valutaváltó): forrás-választó fent, cél-választó
      lent (a forrással azonos valuta szürke, nem választható), középen élő árfolyam + 64-es előnézet;
      a 16/32/64/mind gombok a `/currency exchange` parancsot futtatják, és a váltás után a kiválasztott
      pár megmarad; fővároson kívül a váltás elutasítva (capital-only).
- [ ] **Piac:** `/market sell <ár>` a kézben tartott tárgyra (max 5 tétel); `/market` vétel a bankból;
      `/market cancel` visszavon. Eladásnál ~10% „elég" (money sink).
- [ ] **Frakció-bolt NPC:** rakj ki egy FancyNpcs NPC-t `altalanos_bolt` néven → jobb-katt megnyitja
      a vásárló GUI-t; kattintás vesz (bankból fizet, a pénz ELÉG — money sink), tele táska a földre
      dob. Elég fedezet híján hibaüzenet; `faction`-korlátozott boltban más frakciós tag nem vehet.
- [ ] **Parancs-NPC:** `/npcbind <npc> command spellbook` után az NPC-re kattintva a JÁTÉKOS
      futtatja a parancsot (a saját jogaival — pl. `command icesmp reload` sima játékosnak
      jog-hibát ad, nem fut le); `/npcbind <npc> clear` visszaállítja.
- [ ] **Kereskedő-karaván:** `/events caravan arrive` → broadcast + megjelenik a vándorkereskedő
      (WanderingTrader) a közeledben; jobb-katt megnyitja a ritka-portéka boltját (nem a natív
      trade-et!), vétel a bankból ELÉG. `/events caravan depart` → broadcast + eltűnik; utána a
      korábbi NPC-re kattintva a bolt már nem nyílik. Az entity **sebezhetetlen** és nem tolható.
- [ ] **Aukció:** `/market auction <ár> [óra]` indít; a GUI-ban **bal-katt** = min. licit,
      **jobb-katt** = nagyobb ugrás (+25%) — mindkettő bankból zárol; másik játékos túllicitál →
      az első **visszakapja** a zárolt licitet + üzenetet kap.
- [ ] **Buy-out:** `/market auction <ár> [óra] buyout:<ár>` (a buy-out ≥ kikiáltási ár, különben
      hibaüzenet); a GUI-ban **shift-katt** → azonnal megnyered a buy-out áron, a tárgy `/market
      claim`-mel átvehető, az eladó megkapja a bevételt (−díj).
- [ ] **Aukció-lejárat:** rövid (pl. 0.05 óra = 3 perc) aukció lejár → nyertesnél a tárgy, eladónál
      a licit (−10% díj); licit nélkül a tárgy visszajár. Offline nyertes **belépéskor** vagy
      `/market claim`-mel kapja meg.
- [ ] **Aukció-védelem:** saját aukcióra nem licitálhatsz; élő licites aukció `/market cancel`-lel
      nem vonható vissza; legmagasabb licitálóként nem licitálhatsz rá még egyszer — de a
      **buy-outot a vezető licitáló is használhatja** (a saját zárolt licitje visszajár).
- [ ] **Reputáció-árazás:** ellenséges/raidelő frakciótól drágább (+25%), szövetségestől olcsóbb (−10%).
- [ ] **Adó:** óránként a frakciótagok a valuta-egyenlegük 2%-át a kasszába fizetik (Semleges mentes).
- [ ] ⚠️ **Folia:** vásárolj olyan eladótól, aki **másik régióban/máshol van** → az eladó értesítése
      hibamentes (cross-entity).
- [ ] **Árfolyamtábla:** `/exchangeboard place` hologram lerak; magától frissül; `/exchangeboard remove`.
- [ ] **Adomány-láda:** `/adomany add` a kézben tartott tárgyat (teljes stack) a közös ládába teszi
      — a tárgy csak SIKERES adományozás UTÁN tűnik el a kezedből (üres kézzel / kapacitás
      betelt / saját-limit elérve esetén hibaüzenet, a tárgy marad nálad). `/adomany` megnyitja a
      lapozható böngészőt (45 tétel/oldal); kattintás egy tárgyra **azonnal elviszi** (nincs ár).
      Már elvitt tételre kattintva "már elvitte valaki más" hibaüzenet, nincs dupe. Az
      "Adományozás" csempe a GUI-ban ugyanazt csinálja, mint `/adomany add` (a kezedben lévő
      tárgyat adományozza). A Főmenü gomb visszaviszi a `/menu`-be.
- [ ] ⚠️ **Adomány-láda dupe-teszt:** két játékos (vagy gyors dupla katt) egyszerre próbálja
      elvinni ugyanazt a tételt → csak az egyik kapja meg a tárgyat, a másik hibaüzenetet kap.

#### 4.8.1 Frakcióterületek (zónák) ✅
- [ ] `/territory circle|setcapital|remove|list|info` admin parancsok működnek.
- [ ] **Poligon-zóna:** `/territory pos` több ponton (pl. egy fal mentén), `/territory points`
      listáz, `/territory undo` visszavon, `/territory show` kirajzolja, `/territory create
      protected-city <frakció> <id>` lezárja (≥3 pont kell). Belül vagy-e a poligonon: `/territory info`.
- [ ] Területhatár átlépésekor típusfüggő action bar üzenet jön (főváros / védett város / védett
      frakcióterület / frakcióterület).
- [ ] **Védett zóna** (capital / protected-city / protected-faction) — alapból teljes védelem:
  - [ ] **build**: senki nem tör/rak blokkot, nem használ vödröt, nem szed le képkeretet/armor standot.
  - [ ] **interact**: konténer/ajtó/gomb/kar/műhely jobbklikk tiltott.
  - [ ] **pvp**: játékos↔játékos sebzés (közelharc ÉS nyíl/lövedék) blokkolva, a támadó action-bar üzenetet kap.
  - [ ] **explosions**: creeper/TNT nem tör blokkot a zónában.
  - [ ] **fire**: tűzcsiholó nem gyújt, a tűz nem terjed/nem éget a zónában.
- [ ] **Kárhozat-zóna (doom-gate, ÚJ — K7):** jelölj ki egy zónát — frakció NEM kell:
      `/territory circle doom-gate <id> <sugár> Kárhozat Kapuja` (a régi, frakciós forma is él), majd:
  - [ ] Belépéskor sötétvörös action bar („senkiföldje: itt nincs törvény"), baljós Nether-hang +
        hamu/lélek particle-örvény.
  - [ ] **PvP legális** a zónában (`rules.doom-gate.allow-pvp: true`), de a frissen belépő ~8 mp
        **belépő-védelmet** kap (a támadó action-bar üzenetet lát); aki maga támad, azonnal
        elveszti a védelmét. Kilépéskor / kilépő játékosnál a grace törlődik.
  - [ ] **Ölés itt nem bűn**: PvP-kill után a killer „A Kárhozat Kapujánál nincs törvény" action
        bart kap, a bűn-számláló NEM nő (`territory.doom-gate.sin-exempt`). Raid-szentesített és
        bounty-ölés szabályai előrébb valók (azok változatlanok).
  - [ ] **Mob-boost + keményítés**: a zónában spawnoló mobok +3 szintet kapnak, nappal sem
        égnek, nem zombisodnak (`territory.mob-rules.doom-gate` — bonus-levels /
        no-daylight-burn / no-zombification; BÁRMELY zóna-típusra vagy tulajdonos-frakcióra
        — pl. `dark:` — is megadható).
  - [ ] Építés/robbanás/tűz tiltott az arénában, ajtó/oltár interakció szabad; claim nem rakható.
  - [ ] Minden kulcs élőben olvasódik → `/icesmp reload` után restart nélkül él.
- [ ] **K10 Feketepiac + Caldestera törvényei (ÚJ):** rakj ki `feketepiac` nevű bolt-NPC-t a
      Botera-negyedbe → Csontveretért árulja a két csempész-árut. **Fegyvertilalom:** a NEUTRAL
      fővárosban nyílt fegyverrel a kézben az őrség elrakatja (inventoryba kerül, action-bar);
      a **Bokic-menti Sétapálca** (bot!) átcsúszik — közelharcban +5 flat sebzés, de CSAK a
      főváros zónáján belül (`signature.setapalca.bonus-damage` + `capital-only: true` —
      a városon kívül a bot csak bot). **Körözött-kapu:** vérdíjas játékost a határon
      visszafordít; **Hamisított Menlevéllel** a zsebében beengedi. Kulcsok:
      `territory.capital-law.*`; a bolt-áru name/lore/signature mezőit a ShopManager stampeli.
- [ ] **I14 „Készítette: X" (ÚJ — Tier S):** craftolj NEVES/gear receptet → a lore alján halvány
      dőlt sor „Készítette: <név>" + PDC (crafted_by/crafted_at); a piacra téve is megmarad
      (márkajelzés). Bulk (stackelhető) eredményen NINCS (stack-törés ellen). Kapcsoló:
      `crafted-by.enabled` (profession-recipes.yml). Régi, címke nélküli itemek hibátlanul
      működnek tovább. **Bélyeg-kivétel:** a STACKELHETŐ unique-alkatrészre (affix nélkül, pl.
      `ures_kupa`) NEM kerül bélyeg — különben a craftolt és a visszakapott példány két külön
      kupacban állna. Teszt: craftolj kupát, majd igyál meg egy italt → a visszakapott üres kupa
      a craftolttal EGY stackbe álljon.
- [ ] **Új recept-eredmény kulcsok (ÚJ):** a katalógus három új `result` mezőt ismer —
      ellenőrizd mindhármat:
      - **`rarity`** → a tárgy vanília raritás-színt kap a tooltipben (a saját raritás-létránk
        `ItemDataFactory.vanillaRarityOf` szerint képezve le a 4 vanília fokra);
      - **`use-remainder`** (`unique:<id>` alakot is elfogad) → a tárgy elfogyasztása után a
        megadott tárgy marad a kézben. Kocsma-kör: `ures_kupa` → ital (pl. `jeghegyi_sor`) →
        megivás → **ugyanaz az üres kupa** kerül vissza, tehát a kupa körbe-körbe járhat;
      - **`use-cooldown`** (`{group, seconds}`) → a használat után a megadott csoport minden
        tárgya visszatöltés alatt van (a vanília cooldown-animáció látszik).
- [ ] **Hozzávaló-fedezet = hozzávaló-fogyás (ÚJ, visszaesés-teszt):** a készlet-ellenőrzés és a
      levonás UGYANAZT a szűrőt használja, ezért **identitással bíró** tárgy (unique, signature,
      „Készítette"-bélyeg, saját név vagy lore) hozzávalóként NEM számít és NEM is fogy el;
      a sérült/kopott szerszám viszont igen (számít ÉS fogy).
      **A hiba, amit keresünk:** ha egy craftolt tárgy fedezi a saját receptje plain hozzávalóját,
      de nem fogy el, akkor a hozzávaló INGYEN van (65 recept eredménye osztozik anyagon a saját
      plain hozzávalójával — pl. minden kocsma-ital `HONEY_BOTTLE`, a signature-fegyverek `BOW`/
      `CROSSBOW`/`NETHERITE_SWORD`). Teszt: legyen a hátizsákban CSAK egy craftolt Jéghegyi Sör
      (plain mézes üveg nélkül) → a Jéghegyi Sör receptje NE legyen craftolható. Ugyanez egy
      elkészült Kallan Szeletelőjével a `kallan_szeletelo` receptre.
- [ ] **D18 `/lore` kódex-parancs (ÚJ — Tier S):** `/lore` felsorolja a témákat;
      `/lore lang|fagy|menedek|kitaszitottak|fa|kapu|suttogok` (aliasok: red/blue/neutral/dark
      stb.) 5 sor kánon-szöveget ír chatbe; a szövegek messages-kulcsból felülírhatók
      (`faction-lore.<téma>.<sor>`). A fővárosi lore-pontok (tábla/NPC) admin-elhelyezésűek —
      ugyanezek a szövegek használhatók hozzájuk.
- [ ] **B15 Heti Krónika (ÚJ — Tier S):** `chronicle.interval-days` (default 7) ütemben a
      Bankárszövetség krónikája broadcastol: liga-állás + top3 szint/vagyon/raid-ölés, változatos
      nyitó/záró sablonokkal; `/kronika` (alias /chronicle) bármikor visszaolvassa az utolsó
      számot; restart-álló (chronicle.yml). Teszthez: `chronicle.interval-days: 0.001` NEM megy
      (napokban egész) — állítsd 1-re és várd ki, vagy ellenőrizd reload után az első kiadást.
- [ ] **H2 Rontás-góc (ÚJ — Tier S):** ritkán (`corruption.*`) a vadonban SCULK_CATALYST mag
      nyílik broadcast-tal; a zóna ÉJSZAKÁNKÉNT nő (+4 blokk, 64-ig), korrupt, glowing mobokat
      szül (cap: 12 egyszerre — entitás-fék tesztje!); terepet NEM ír át (csak az 1 mag-blokk).
      **Tisztítás:** ölj 15 korrupt fajzatot (számláló-üzenet a magnál), majd SHIFT+jobb katt a
      magra → loot + 5 perc regeneráció + „a Fa fellélegzik" broadcast; a mag kézzel NEM
      bányászható. Restart-teszt: a zóna corruption.yml-ből visszaáll. Spawn-hely: a
      spawn-rules.corruption mátrix-sor szerint (városba nem nyílik).
  - [ ] **P4e mag-aura (icesmp:rontas):** a magtól `corruption.aura.radius` (alap 5) blokkon
        belül állva ~2 mp-enként fél szív sebzés; belépéskor kezdődik, kilépéskor megáll.
        HALÁL ott → magyar üzenet: „… testét felemésztette a rontás." (NEM vanília halál-szöveg).
        Creative/spectator MENTES. `corruption.aura.enabled=false` → azonnal (reload) megszűnik.
        Élő-config: `damage`/`radius` állítása `/icesmp reload` után restart nélkül hat.
        Bootstrap-log: a damage-type regisztrációja az első induláskor hibátlan (nincs error-sor).
- [ ] **B42 Régészet (ÚJ — Tier S):** időnként gyanús homok/kavics lelőhely bukkan fel
      (broadcast + koordináta, `archeology.*`); ECSETTEL kiásva SZERVER-SAJÁT lelet esik
      (Emlékszilánk / Ősi Ereklyeszilánk / anyagok — sosem vanília cserép); 20 perc után a
      terep nyomtalanul visszaáll (ki nem ásott lelőhelynél is). Spawn-hely: a
      spawn-rules.archeology mátrix-sor szerint. Restartnál a lelőhely-blokk visszaáll.
- [ ] **B3 Kazamaták (ÚJ — Tier S, plugin-oldal):** jelölj ki DUNGEON zónát
      (`/territory circle dungeon <frakció> <id> <sugár> [név]`) → belépni csak kulccsal lehet
      (signature: `dungeonkulcs_<zóna-id>` — bolt/recept adja, minta a factions.yml-ben).
      Belépéskor a kulcs ELFOGY: 2 órás futam-passz (ki-be járás szabad) + 7 napos pecsét
      (új futam tiltva, action-bar a hátralévő napokkal). Admin-bypass: territory bypass jog.
      A bent spawnoló mobok +5 szintet kapnak (mob-rules.dungeon), nappal sem égnek;
      zóna-védelem: allow-séma dungeon-sor (építés/robbanás tiltva, interakció/PvP szabad).
      A pálya megépítése + boss-telepítés admin-munka. Kulcsok: `territory.dungeon.*`.
- [ ] **Frakcióterület** (`faction`): `build` csak a NEM-tagot tiltja (tag épít), `interact/pvp/
      explosions/fire` alapból szabad — a `rules.faction.*` kapcsolókkal külön állítható.
- [ ] **Bypass:** `icesmp.admin.territory.bypass` mindent megkerül (PvP is);
      `icesmp.territory.builder` védett zónában is építhet/interaktálhat, de PvP-zni NEM.
- [ ] **Kill-switch:** `protect-zones: false` → a védett zónák minden szabálya kikapcsol.
- [ ] **Grief-rések (védett zóna):** enderman nem visz el blokkot; kívülről víz/láva nem folyik be;
      dugattyú nem tol be blokkot; TNT nem pusztít képkeretet/armor standot.
- [ ] **PvP-rések (biztonságos zóna):** nyíl/lövedék, farkas (háziállat), TNT-sebzés és ártó
      splash/lingering bájital sem hat a játékosra; a támadó action-bar üzenetet kap.
- [ ] **`/territory tp <id>`** a zóna középpontjához teleportál (a legfelső blokkra; Y-korlátnál a sávba).
- [ ] **`/territory show <id>`** tetszőleges (nem alattad lévő) zóna határát is kirajzolja.
- [ ] **messages/territory.yml** felülírja az alapszövegeket (pl. `territory-pvp-denied`).
- [ ] **Claim tiltás:** védett zónában a `/claim` és `/claim area` elutasítva
      (`claim-in-protected-zone`); **normál frakcióterületen viszont ENGEDETT** (alapból
      `claims.block-in-territory: false`). Kis poligon-zóna szélén is véd (sarok+közép próbák).
- [ ] **Zóna-módosítás:** `/territory rename|resize|settype|sety <id> ...` a meglévő zónát módosítja
      (a `resize` poligonra elutasít); `settype ... capital` a régi fővárost lefokozza.
- [ ] **Magassági sáv:** `/territory sety <id> 60 ~` után a zóna csak Y=60 felett véd; az `info`
      a „Magasság" mezőben mutatja; a `~`/`*` = korlátlan.
- [ ] **Poligon-validáció:** önmetsző határvonalnál (a fal átvágja saját magát) a `create` elutasít.
- [ ] **Teljesítmény:** sok zónával is gyors a mozgás/építés (chunk-index; a lookup nem lassít).
- [ ] Régi `territories.yml` (csak `capital: true/false`) betöltése: capital→CAPITAL, egyébként FACTION.

#### 4.9 Relikviák + rituálé-oltárok ✅
- [ ] `/relic give <j> <id>` → a relikvia megjelenik; `/relic list` az id-khez.
- [ ] **Mételytépő** megjelöli/bünteti a bűnösöket; **PvP-ben** ölésnél az új gazdája a gyilkos lehet.
- [ ] **4 frakció-elytra** csak a tulajdonos + a megfelelő frakció tagja használja; a passzív szárnyak
      PvP-ben **nem** cserélnek gazdát.
- [ ] **Szárny frakció-kapuk (ÚJ):** a 4 szárny-rituálé csak a SAJÁT frakcióval végezhető el
      (requires-faction — RED-ként a Csontszárny oltára elutasít); idegen frakció tagja a szárnyat
      a **földről sem veheti fel** (`relics.wings.faction-locked-pickup`, action-bar üzenettel).
- [ ] **Csontszárny árnyék-forma (JAVÍTVA):** DARK-ként ÉJJEL siklás közben az árnyék-forma
      (láthatatlanság + gyorsaság + lélek-particle) FOLYAMATOSAN fennmarad a repülés alatt (2 mp-es
      frissítéssel), és leáll leszálláskor/napkeltekor. A teljes éjszaka számít (hajnal előtt is);
      Netherben/Endben mindig él (örök félhomály). Nappali felszállásnál action-bar jelzi, miért
      nincs effekt.
- [ ] **Rituálé-oltár:** a megfelelő oltár-blokk + áldozati tárgyak + **SHIFT+jobb katt** → megidézi a szárnyat.
- [ ] **Egy-példány szabály:** ha él a tulajdonos, nem idézhető/adható újra.
- [ ] **Multi-block szentélyek (5×5):** hiányos szerkezettel (csak a mag-blokk áll) az oltár hibát
      ír és nem aktiválódik; a teljes szentély (5×5 alapzat + 4 két-magas saroktorony) megépítve működik.
- [ ] **Típusos oltárok** (config `type`): **Feloldozás** (Lélek-lámpás) leveszi a bűnös-jelet és
      nullázza a bűnöket — de sötét paktumossal elutasítja; **Hazatérés-kő** (Lodestone) a frakció
      fővárosába teleportál (főváros híján hibaüzenet). Sikertelen kimenetnél az áldozat NEM fogy;
      cooldown alatt ismétléskor hiba.
- [ ] **Kaszt-szentélyek (13):** mindegyik `*_szentely` csak a saját kasztjának aktiválódik
      (`requires-class`), más kaszt hibaüzenetet kap; a buff a táblázat szerint felkerül.
- [ ] ⚠️ **Folia:** a Mételytépő ölés-büntetése a gyilkost **másik régióból** is hibamentesen jelöli.

#### 4.10 Pet / minion ✅
- [ ] Vadmester/Nekromanta: `/pet item` befogó eszköz → jobb katt a célon → társ; `/pet name|summon|dismiss|info`.
- [ ] A társ szintet lép a gazda öléseiből; sunyítás+jobb katt rajta → állásváltás (Támadás/Passzív/Maradj).
- [ ] Nekromanta: minden ölés után lélekszilánk (`/souls`); `/souls champion` bajnokot idéz.

#### 4.11 Küldetések + bűn-rendszer + Sötét ✅
- [ ] `/quest list|accept|info|abandon`; a haladás az action barban; teljesítéskor jutalom.
- [ ] **Onboarding-lánc (ÚJ, első belépés):** vadonatúj játékos joinkor automatikusan megkapja a
      „Beszélj a hírnökkel" questet (`onboarding_herald`, üdvözlő üzenettel); teljesítéskor
      **auto-indul** a következő („Első csata": ölj 5 szörnyet → „Első gyűjtögetés": 10 rönk,
      jutalom: valuta + csákány + kenyér). Előfeltétel a szerveren: `hirnok` nevű NPC a semleges
      fővárosban (`/npcbind hirnok faction` ajánlott). Meglévő játékosnál NEM indul újra.
      Kikapcsolás: `quests.yml` → `onboarding.enabled: false`. Quest-láncolás: a quest `next`
      mezője (adminból is állítható: `/quest admin set <id> next <köv-id>`).
- [ ] `/quest complete <j> <id>` (admin) azonnal teljesít.
- [ ] **Bűn:** ölj meg egy másik játékost → +1 bűn; **4 bűnnél** automatikus száműzetés a Sötétbe (örök paktum).
      (Raid alatt a hadakozók közti ölés **nem** bűn.)
- [ ] **Árulás:** öld meg a SAJÁT frakciótársadat → **+2 bűn** külön üzenettel. (Semleges–Semleges
      ölés sima gyilkosság, +1.)
- [ ] **Lopás:** végy ki tárgyat egy **másik frakció territóriumában** álló ládából → +1 bűn üzenettel;
      ugyanabban a területen 1 percen belül több kivét **nem** ad újabb bűnt. Saját területen és
      claimeletlen vadonban nincs bűn; virtuális GUI-k (piac, menük) nem érintettek.
- [ ] **Lopás-kivételek:** raid-háború alatt a hadviselő fél területén a zsákmányolás nem bűn;
      a `icesmp.admin.territory.bypass` joggal szintén nem.
- [ ] **Fejvadászat:** vigyél fel egy játékost 3+ bűnre (`/sinner <j> add`) → megjelenik a `/bounty`
      listán fejpénzzel. Öld meg → a gyilkos MEGKAPJA a fejpénzt (bank), NEM kap érte bűnt, a
      célpont bűnszámlálója 0-ra áll (de bűnös marad); broadcast jelzi. 3 bűn alatt sima gyilkosság.
- [ ] **Mester-lánc (NPC):** rakj ki egy FancyNpcs NPC-t `harcos_mester` néven; a `warrior_trial`
      után vedd fel a `warrior_mentor` questet, katt az NPC-re → a mentor-quest teljesül ÉS az NPC
      azonnal ADJA a `warrior_master_trial`-t (❕ üzenet); az 20 megerősített (Lvl 2+) szörny
      leölésével teljesül. Mind a 13 kasztnak van mentor+mester-próba lánca; a Boszorkánymesteré
      a `pakt_mester` NPC-hez kötött (nincs külön `boszorkany_mester`).
- [ ] **NPC-marker (per-player):** akinek felvehető questje van az NPC-nél → ARANY aura az NPC
      felett; akinek aktív TALK_TO_NPC questje szól hozzá → ZÖLD aura; egy harmadik játékos
      (feltétel nélkül) SEMMIT nem lát. A marker ~2 mp-enként pulzál, ~48 blokkos körzetben.
- [ ] **Sötét Beavatás** küldetés feloldja a Nekromantát.
- [ ] **Admin quest-szerkesztő:** `/quest admin create proba_quest KILL_MOBS 5 Próba Quest` →
      `/quest admin set proba_quest rewards.class-xp 100` → játékosként `/quest accept proba_quest`,
      5 mob után teljesül. `set`-tel giver-npc is adható (NPC adja + arany aura); `delete` törli;
      a configbeli questek NEM szerkeszthetők/törölhetők innen. Restart után is megmarad
      (custom-quests.yml).
- [ ] **Új objektívák:** próbálj ki párat — PLACE_BLOCKS (blokk-lerakás), COLLECT_ITEMS
      (felvett tárgyak, stack-nyi haladás), KILL_PLAYERS (PvP), BREED_ANIMALS, ENCHANT_ITEMS,
      CONSUME_ITEMS (evés); DELIVER_ITEMS: vidd a tárgyakat a megadott NPC-hez → kattintásra
      ÁTVESZI őket (kevesebbnél action bar mutatja, mennyi van nálad). Bővebb készlet:
      olvasztás (kohó), állat-szelídítés (taming), falusi kereskedés, bióm-felfedezés,
      raid-győzelem, világboss-ölés.
- [ ] **Több-objektívás quest (ALL):** `/quest admin create multi KILL_MOBS 5 Több Feladat` →
      `/quest admin addobjective multi COLLECT_ITEMS 16 Kenyér` → `/quest admin set multi objectives-mode ALL`
      → felvéve a `/quest info`/HUD MINDKÉT feladatot külön mutatja (pl. Szörnyek 0/5 • Kenyér 0/16),
      bármely sorrendben halad, csak MINDKETTŐ kész után teljesül.
- [ ] **Több-objektívás quest (SEQUENCE):** ugyanez `objectives-mode SEQUENCE`-szel → csak az
      AKTUÁLIS lépés halad, a következő csak az előző után nyílik (story-lánc).
- [ ] **Küldetésnapló GUI:** `/quest log` (`gui`, `naplo`) → három fül: **Aktív** (haladással;
      shift-katt = feladás), **Felvehető** (katt = felvétel), **Teljesített**; sok questnél lapozható.
- [ ] **Ismétlődő (repeatable) quest:** teljesítés után NEM vehető fel újra azonnal; a config-beli
      cooldown (pl. 24 óra) letelte után ismét felvehető.
- [ ] **Szezonális quest:** szezononként CSAK EGYSZER teljesíthető; új szezon indulása után újra elérhető.
- [ ] **Választós párbeszéd:** olyan quest-NPC-nél (`dialogue.choices`), ahol a párbeszéd után
      kattintható válaszopciók jelennek meg a chatben → különböző opció KÜLÖNBÖZŐ következő questet indít.
- [ ] **NPC napi rotáció:** `rotation-group`-os NPC egy poolból naponta csak a beállított számú questet
      kínálja; a kínálat naponta (nap váltásakor) frissül — más questek jelennek meg.
- [ ] **Frakció-közösségi cél:** a `community-goals`-hoz tartozó tevékenységet többen végezve a MEGOSZTOTT
      számláló nő (minden frakciótag beleszámít, nem egyéni felvétel); a cél elérésekor az egész frakció
      **kassza-jutalmat + rövid buffot** kap, majd a számláló újraindul.
- [ ] **Saját-frakció valuta jutalom:** `/quest admin set proba_quest rewards.currency.type OWN`
      + amount → teljesítéskor a játékos a SAJÁT frakciója valutáját kapja (Piros → piros token).
- [ ] **NPC-párbeszéd:** `/quest admin set proba_quest dialogue.speaker Aldric mester`,
      `... dialogue.give Üdv vándor!|Van egy feladatom.` → quest-átvételkor az NPC „mondja" a
      sorokat ~1,5 mp-enként; `dialogue.complete` a teljesítéskor szól (parkour-célnál is).
      A mester-láncok gyárilag kaptak példa-dialógust.
- [ ] **Vezeklés-lánc** (3 rész) az EGYETLEN mód a paktum megtörésére.
- [ ] ⚠️ **Folia:** a bűn-jelölés a gyilkost másik régióból is hibamentesen jelöli.

#### 4.12 Király, raid, kassza, szezon ✅
- [ ] `/faction king` (szavazás/koronázás); a király kivehet a kasszából, adót állít, raidet hirdet.
- [ ] `/faction treasury`, `/faction donate`; az adó és adományok töltik.
- [ ] `/faction raid <cél> [terület]` (csak király); alapból a védő fővárosáért folyik; a hirdető
      király automatikusan harcos. `/faction raid status` mutatja a fázist/pontokat/létszámot.
- [ ] **Jelentkezés:** a felkészülés alatt `/faction raid join` (max 10/oldal — a 11. jelentkezőt
      elutasítja); a bossbar a felkészülés alatt erre hív, harc alatt a pontállást mutatja.
- [ ] **Résztvevő-szabály:** csak a JELENTKEZETT harcosok közti ölés szentesített és pontozó;
      nem-jelentkezett hadviselő-frakciós ölése raid alatt is bűn (gyilkosság/árulás).
- [ ] **Zóna-szabály:** területkötött raidnél a zónán KÍVÜLI ölés szentesített, de nem ér pontot
      (külön üzenet); a zóna középpontján állva ~5 mp-enként pont jár (action bar jelzi).
- [ ] **Terület-átvétel:** ha a támadó nyer, a terület átkerül hozzá (broadcast; fővárosi státusz
      elvész); védő győzelemnél / döntetlennél marad. A végén hadizsákmány + győztes-buff.
- [ ] **Ostromágyú** (craftolható) csak aktív raid alatt sül el; terep-barát robbanás.
      ⚠️ **Folia:** célozz **távoli** pontra (másik régió) → a robbanás ott történik, konzol-hiba nélkül.
- [ ] `/events season` mutatja a liga-pontokat; a szezon végén jutalom + reset.
- [ ] **Szezon-győztes tagi jutalom:** a szezon lezárultakor a győztes frakció KASSZÁJA kapja a
      treasury-reward-ot, az ONLINE TAGJAI pedig győzelmi buffot (Erő/Regen/Falu Hőse) + tárgy-jutalmat
      (config: champion-reward-items) + ünneplő tűzijátékot; a más frakciós tagok semmit. Döntetlennél/
      pont nélkül nincs bajnok, nincs tagi jutalom.

#### 4.13 Világesemények ✅
- [ ] **Mob-szintezés:** a spawntól távolodva erősebb, `[Lvl X]` nevű mobok; a névtábla csak ránézésre
      jelenik meg; spawner/parancs-mob nem skálázódik.
- [ ] **Vérhold** (`/events blood-moon start`): erősebb mobok + dupla lélekkő-esély; broadcast.
- [ ] **Lélekkő élőhalott-kivétel:** DARK játékos zombi/csontváz-ölése NEM ad lélekkövet (élő
      szörny — pl. creeper — igen); Nekromanta élőhalott-ölése NEM ad lélek-szilánkot, élő igen
      (`currency.soul-drop.dark-undead-drops` / `souls.shards-from-undead`).
- [ ] **Világboss** (`/events worldboss`): véletlen archetípus, név, aura-debuff a közelben; ~8 mp-enként
      telegrafált képesség; **50% HP alatt feldühödik**; legyőzve kassza+pont+buff.
      ⚠️ A SUMMON-special által idézett add-ok egy idő után **eltűnnek** (nem maradnak ott örökre).
- [ ] **Boss-telegraph (ÚJ):** a SLAM/ZONE special előtt **részecske-gyűrű** rajzolja ki a veszélyzónát
      (5, ill. 3 blokk sugár) + Warden-hang; a SUMMON előtt Evoker-idéző hang szól — kivédhetőbb a special.
- [ ] **Boss-telegraph padló-lap (DisplayFx, ÚJ):** a gyűrű mellett egy lapos, piros, IZZÓ padló-lap
      NŐ kicsiről a teljes zónáig a ~1,5 mp figyelmeztetés alatt, és a becsapódáskor eltűnik — sokkal
      olvashatóbb „lépj ki innen". Nem marad blokk-szemét (a special után, relog/restart után sem).
      Kapcsoló: `display-fx.boss-telegraph.enabled: false`; a lap anyaga configos. ⚠️ Folia: régióhatáron
      spawnolt bossnál is hibamentes.
- [ ] **`/events status` (ÚJ, mindenkinek):** „Mi történik most?" — kilistázza az összes épp aktív
      világeseményt (vérhold/boss/invázió/karaván/gyűjtögető/kincs/Vad Hajsza/bőség/kihívás/kíséret/meteor,
      hátralévő perccel) + a szezon-állást; üresen „nyugalom van" üzenet. A `/menu` → Események almenü
      tetején ugyanez **óra-ikonként** (kattintásra lefuttatja a parancsot).
- [ ] **Invázió** (`/events invasion`): horda + megnevezett bajnok (telegrafált földcsapás); extra XP/lélekkő.
- [ ] **Esemény-spawn szabályok (ÚJ):** világboss / invázió / Vad Hajsza **NEM spawnol** claimelt
      frakció-territóriumba, játékos-claimbe, WG-régióba (városok), sem víz tetejére — állj be egy
      városba és `/events worldboss|invasion|wild-hunt`: nem jelenik meg semmi (a következő
      intervallumban máshol próbálkozik). Config: `world-events.spawn-rules` esemény×védelem
      mátrix (world-boss/invasion/wild-hunt/treasure/meteor × territory/claim/region/water,
      minden cella külön kapcsolható) + `world-events.spawn-rules-enabled` mester-kapcsoló (a régi avoid-territory fallbackként él); a
      kulcsok élőben olvasódnak → `/icesmp reload` után restart nélkül él. ⚠️ A régi
      `meteor.avoid-territory` kulcs megszűnt (a mátrix meteor-sora váltja).
- [ ] **Esemény-mob keményítés (ÚJ):** a Pokoli Hadúr boss / Alvilági Roham piglinjei / Pokoli Behemót
      az overworldben **NEM zombisodnak át** (várj mellettük ~15 mp-et); az invázió csontváz/zombi
      mobjai és a boss SUMMON-addjai **nappal nem gyulladnak meg**. ⚠️ Zombisodás esetén korábban a
      kill tévesen bűnnek számított (új entity, elveszett tracking) — ennek is tesztje ez.
- [ ] ⚠️ **Folia — invázió-bajnok földcsapás régióhatáron:** a bajnok slamje másik régióban álló
      játékost is hibamentesen sebez/lök (scheduler-hop, nincs IllegalStateException a konzolban).
- [ ] **Hangulat-események** (`/events ambient`): broadcast + kozmetikai effekt; az északi fény rövid
      éjjellátást ad, az állat-vándorlás passzív csordát idéz a közeledbe (balanszot nem érint).
- [ ] **Gyűjtögető buff** (`/events gathering`): broadcast a kezdetről; bányász-láznál érctöréskor
      bónusz drop, XP-óránál szorzott XP, halászati láznál esély dupla fogásra; a végén záró broadcast.
- [ ] **Kincs** (`/events treasure`): megjelölt láda (részecske-jelző) + broadcast koordinátákkal; a
      rákattintás VAGY törés **egyszer** kiosztja a loot-ot a megtalálónak, aztán eltűnik; lejáratkor
      feltáratlanul eltűnik. A vanilla láda-GUI **nem** nyílik meg.
- [ ] **Vad Hajsza** (`/events wild-hunt`): megnevezett, glowing elit fenevad; leölve ritka loot hullik
      + broadcast a vadászról; ha `expire-minutes`-en belül nem ölik meg, elszökik (broadcast).
- [ ] **Bőség-idő** (`/events abundance`): broadcast; a termés gyorsabban nő, szaporodáskor néha iker,
      kevesebb természetes szörny-spawn, gyengéd regeneráció; a végén záró broadcast. Terephez nem nyúl.
- [ ] **Szerver-kihívás** (`/events challenge`): broadcast + **boss-bar** a közös haladással; szörny-ölés
      / érc-bányászás / termés-betakarítás növeli; célnál MINDEN online játékos jutalmat kap (XP + loot +
      Sietség); lejáratkor bukás-broadcast. Belépő játékos is látja a boss-bart.
- [ ] **Karaván-kíséret** (`/events escort`): ládás láma-konvoj a cél felé halad (boss-bar = haladás + HP);
      ~45 mp-enként szörny-hullám támadja a konvojt; célba érve loot hullik + a karaván-bolt bónusz-készlete
      (`bonus-items`) egy időre elérhető; a konvoj halálakor/lejáratkor bukás. **Terep-teszt:** a kíséret-mob
      robbanása (ha van) **nem tör blokkot**; a konvoj/mobok reload után nem maradnak ott (nem perzisztens).
      **Egyszerre-egy teszt:** aktív kíséret (vagy épp induló spawn-lánc) mellett az újabb
      `/events escort` **false**-szal elhasal — sosem áll két konvoj (a második korábban
      orphanná tette volna az elsőt).
- [ ] **Meteor** (`/events meteor`): kráter jelenik meg érc-blokkokkal + broadcast a koordinátákkal; az érc
      kibányászható (valódi drop). **Terep-teszt (fontos):** `expire-minutes` után VAGY `/reload`/leállítás
      után a kráter **teljesen visszaáll** az eredeti terepre; `avoid-territory: true` mellett **nem** csapódik
      claimelt területre (állj egy `/territory`-be és nézd, hogy máshova kerül).
- [ ] **Party:** `/party invite` két fiókkal → accept után közös csapat; közeli mob-ölés XP-je
      **fejenként oszlik** (floor(xp/n), a maradék a megölőé; ha xp < létszám, csak a megölő kap);
      párttag **nem sebezhető** (kard + nyíl); Vad Hajsza/kincs esemény párttal →
      **mindenki saját (personal) lootot kap**; kilépő játékos kikerül; 2 fő alatt a csapat feloszlik;
      `/p szia` csapat-chat.
- [ ] **Party-HUD:** csapatban a HUD-oldalsávon megjelenik a „— Csapat —" szekció (👑 vezető, tagnév +
      élet-sáv); a társ sebződésekor a sávja **sárgára/pirosra vált** (~1 mp-en belül frissül); a csapat
      feloszlása után a szekció **eltűnik és nem hagy üres sorokat** az oldalsávon.
- [ ] **Megbízott-GUI (ÚJ):** `/menu` → Birtok → „Megbízottak kezelése" (vagy `/claim trustgui`):
      felül a megbízottak fejei (katt = visszavonás), alul a 15 blokkon belüli játékosok (katt =
      megbízás); a kattintás a `/claim trust|untrust` parancsot futtatja és a GUI frissül.
- [ ] **Claim:** `/claim` lefoglal (részecske-határ); másik fiók NEM tud törni/rakni/ládát nyitni benne
      (action-bar üzenet); `trust` után igen; robbanás nem bont claimelt blokkot;
      frakció-territóriumban/WG-régióban a claim elutasítva; a 4. chunktól ár (bankból, ELÉG); `unclaim`
      nem ad vissza pénzt; admin: `/claim admin unclaim`.
- [ ] **Claim terep-védelem:** kívülről gyújtott tűz nem terjed be claimre és claimelt blokk nem ég el;
      claim-határon kívüli dugattyú nem tol be / nem húz ki blokkot (claimen BELÜLI saját gép működik);
      kívülről víz/láva nem folyik be; idegen flint-and-steel gyújtás a claimen tiltva (action-bar).
- [ ] **Chat-formázó:** a chatben `[LP-prefix] Név: üzenet` formátum, a név frakció-színnel; LuckPerms
      nélkül is működik (prefix nélkül). ⚠️ Ha a régi LuckPermsChatFormatterFolia plugin még fent van,
      kapcsold ki az egyiket (dupla formázás)!

#### 4.14 GUI-k és HUD ✅
- [ ] `/menu`, `/profile`, `/spellbook`, `/market`, `/leaderboard`, `/achievements`, `/daily` megnyílik,
      a gombok működnek, a kattintások nem visznek ki tárgyat a menüből.
- [ ] **Elérések configból (`achievements.definitions`):** a 21 mérföldkő a `quests.yml`-ben él.
      Teszt: vegyél fel egy ÚJ blokkot (pl. `metric: CLASS_LEVEL`, `threshold: 5`, `reward: 25`)
      → `/icesmp reload` → az `/achievements` listában **azonnal megjelenik**, szerver-újraindítás
      nélkül, és a küszöböt elérve a veret jár. Hibás sor (ismeretlen `metric` vagy nem pozitív
      `threshold`) **kimarad és a konzol figyelmeztet** — a többi elérés ilyenkor is működik.
      FIGYELEM: meglévő elérés **id-jét ne nevezd át** (a teljesítést a játékos PDC-je az id-n
      tartja, átnevezés után újra kiosztódna).
- [ ] **`/stats [név]` (ÚJ):** kiírja a statisztika-profilt (játékos-ölések, halálok, K/D,
      mob-ölések, elsütött spellek, teljesített questek); névvel másik (online vagy már látott)
      játékosé; a számlálók ölés/halál/cast/quest-teljesítés után nőnek és restart után megmaradnak.
- [ ] **Quest builder** (`/quest admin builder <id>`, admin): új id-vel a típus-választó nyílik →
      darabszám + név a chatben → szerkesztő; létező custom questtel rögtön a szerkesztő. Mező-csempe
      kattintás után a chatbe írt érték mentődik ('mégse' megszakít), a kapcsolók (ismételhető,
      szezonális, bűn-tisztítás, objektíva-mód) helyben váltanak; törléshez két kattintás kell;
      a chatbe írt érték NEM jelenik meg a publikus chatben.
- [ ] **Admin panel** (`/menu` → Admin, csak admin-joggal): minden világesemény indítható gombbal
      (vérhold start/stop, világboss, invázió, karaván érkezés/távozás, Vad Hajsza, meteor, kincs,
      gyűjtögető buff, bőség, kihívás, kíséret, hangulat-esemény); a kezelősor (admin unclaim,
      NPC-kötések, quest admin lista) csak a megfelelő jogosultsággal látszik.
- [ ] **P4d Natív dialógus (ÚJ):** ÚJ játékos első belépésekor (az intro-cím UTÁN,
      `onboarding.welcome-dialog-delay-ticks`≈4 mp) natív **üdvözlő-ablak** ugrik fel
      (cím + első-lépés sorok + „rendben" gomb) — resource pack NÉLKÜL. ESC/gomb bezárja.
      `onboarding.welcome-dialog: false` → nem jön. Tartalom élő-configból
      (`onboarding.welcome-dialog-title/-lines`, MiniMessage). Régi játékosnál NEM jelenik meg.
- [ ] **P7 Data-component ételek (ÚJ, CONSUMABLE-migráció):** frissen craftolt signature-étel
      (Fagyasztott pisztráng, Főnixtojás-rántotta, Sárkány-pörkölt, Vadlakoma, Vándorünnep lepénye,
      Hamvak lakomája, Mortengrádi hamukenyér) evésekor a buff MOST a data-komponensből jön —
      a hatás ugyanaz, de **nincs dupla buff** (a listener a food_v2 jelölő miatt kihagyja).
      A Tiltott kakaóbabos sütemény (lökés+partikel) VÁLTOZATLAN (listener-ág). A honvágy-
      kötelezettség (BLUE hal / RED tojás) változatlanul működik. `/icesmp reload` után az
      ÚJ craftok tükrözik a buff-időt (a komponens craft-időben olvassa a configot).
- [ ] **P7 Szakács fogyaszthatók (ÚJ, recept-vezérelt CONSUMABLE + ITEM_MODEL):** a Szakács
      craftolja: **Jéghegyi Sör / Parázs Pálinka / Caldesterai Gyógytea / Mortengrádi Keserű**
      (ITALOK — ivás-animáció + hang!), **Vándor Pogácsája / Halász Fogása** (ÉTELEK). Ivás/evés
      után a megfelelő rövid buff (pl. Sör→Regeneráció+Felszívódás, Pálinka→Tűzállóság+Erő,
      Pogácsa→táplálék+Sietség, Fogás→Vízlégzés). **ITEM_MODEL**: pack nélkül alap-item
      textúrát mutat, a `icesmp:<id>` modellt a külső pack adja. A buff-idők a receptből (config).
- [ ] **Kupa-hurok (USE_REMAINDER, ÚJ):** craftolj **Üres Kupát** (Szakács 5, `GLASS:2` → 4 db),
      abból bármelyik kocsma-italt (mind a 12 kér 1 kupát) → **megivás után az Üres Kupa a
      kezedben marad** (nem sima üvegpalack, hanem a bélyegzett kupa!), és azonnal újra
      felhasználható italhoz. Ellenőrizd, hogy tele hotbarnál sem vész el (a vanília a földre
      dobja), és hogy a kupa a recept-könyvben hozzávalóként elfogy.
- [ ] **P7 USE_COOLDOWN — katalizátor cooldown-bleed javítás (ÚJ):** tarts a hotbarban egy
      pálca-katalizátort ÉS a vele AZONOS Materialú vanília itemet (pl. Homály-szilánk=FLINT →
      sima kovakő; Sárkánykirály Kürtje=GOAT_HORN → sima kürt). Castolás után CSAK a katalizátor
      sötétül el (cooldown-overlay), a vanília item NEM. (Melee-kard katalizátornál a kard
      overlay-e változatlan.)
- [ ] **P7 TOOLTIP_DISPLAY — affix-gear tiszta tooltip (ÚJ):** egy affix-rollos felszerelésen
      NINCS többé a vanília „When in Main Hand: +X Attack Damage" blokk — a stat CSAK a saját
      affix-lore-sorban (pl. „+ 12 Élesség") látszik, egyszer.
- [ ] **P8e Alacsony-HP piros vignetta (ÚJ):** HP a küszöb (alap 30%) alá esve a képernyő
      szélén vörös vészköd jelenik meg; gyógyulva/küszöb fölé eltűnik. `hud.low-hp-vignette.enabled:
      false` → nem jön. A per-player világperem NEM okoz tényleges perem-hatást (nem lök vissza,
      nem sebez). Respawn/reconnect után tiszta állapot. Threshold élő-configból.
- [ ] HUD oldalsáv: frakció, kasztok+szintek, szakmák, talentpontok, egyenleg, **Erő-csík**.
- [ ] Bossbar (világboss/raid) megjelenik — és **nem** ütközik az Erő-csíkkal (az a sidebar-on van).
- [ ] **Natív tablist (ÚJ — TAB-kiváltás, a TAB.jar NÉLKÜL tesztelendő):**
  - [ ] **Header/footer** megjelenik (glyph + animált ping/online sor, twitch/discord marquee);
        az animáció halad, de a lista NEM villog (diff-elt küldés).
  - [ ] **Tab-nevek:** LP-prefix + frakció-színes név + suffix; frakció-váltásnál a szín ~0,5
        mp-en belül átvált, közben nincs villogás.
  - [ ] **Rendezés:** a tablistában elöl a magasabb LP-rang (owner→admin→…→default), azonos
        rangon ABC; a fej fölött is látszik a prefix + frakció-szín (nametag).
  - [ ] **Ping-oszlop** a tablistában él és frissül.
  - [ ] **Oldalsáv új dizájn:** animált elválasztó-vonalak, small-caps címkék (ꜰʀᴀᴋᴄɪó/ᴋᴀꜱᴢᴛ/
        ᴇꜱᴇᴍéɴʏ/⭐ᴠᴀʟᴜᴛᴀ), cím a configból (`hud.sidebar.title`); `/hud` szekció-toggle továbbra
        is működik, és a sidebar kikapcsolása NEM töri el a nametageket/pinget (a board marad).
  - [ ] **Harc-fókusz (ÚJ):** találat adásakor VAGY kapásakor az oldalsáv „kitisztul" — csak az
        Erő-csík (+ party-frame-ek) maradnak; az utolsó találat után ~8 mp-cel a teljes HUD
        visszatér. Kikapcsolás: `hud.dynamic.combat-focus: false`; a látható szekciók a
        `combat-visible-sections` listával hangolhatók.
  - [ ] **Rotáló infósor (ÚJ):** az esemény-sor ~4 mp-enként váltakozik: aktív események ↔
        „ꜱᴢᴇᴢᴏɴ: még ~N nap" ↔ „ɴᴀᴘɪ: x/y" (kész napi kihívásnál zöld ✔); nyugalomban az
        esemény-frame kimarad a rotációból. Kikapcsolás: `hud.dynamic.rotating-line: false`
        (fix esemény-sor).
  - [ ] ⚠️ **Folia:** két játékos KÜLÖN régióban (messze egymástól) — mindkettő látja a másik
        nevét/rangját/pingjét a tablistában, konzol-hiba nélkül.
  - [ ] Ha a TAB.jar mégis fent van: induláskor konzol-figyelmeztetés jön az ütközésről.
- [ ] **Plugin-kiváltások (ÚJ — a jarok NÉLKÜL tesztelendő):**
  - [ ] **Warden-XP** (ICEsmpadditions helyett): Warden leölése 80–125 XP-t dob
        (`world-tweaks.warden-death-xp`, configolható).
  - [ ] **Termés-taposás** (FarmProtect helyett): szántóföldre ugrás (játékos ÉS mob) nem töri
        fel a földet (`world-tweaks.crop-trample-protection`).
  - [ ] **MOTD** (MiniMOTD helyett): a completion branch buildelt; eltávolítás csak az alábbi valódi Folia ping/reload tesztek után;
        idő/random rotáció, eseményprioritás, 64×64 ikonok, vanish-count és reload külön ellenőrzendő.
  - [ ] **Globális AFK:** az automatikus tétlenségészlelés, `/afk` ki/be kapcsolás,
        tablistajelzés, rangon belüli hátrasorolás és jutalomkapu működik. Jutalmazó AFK-zóna,
        bossbar vagy kifizetés nincs. Az éles AxAFKZone/AxAPI jar/adat és Paper remap-cache nem
        része a cél-deploymentnek.
  - [ ] **Crate-rendszer** (CrazyCrates helyett): a code-review-zott lifecycle buildelt és regressziózott,
        de a jar eltávolítása csak valódi Folia/fault-injection átvételi teszt után engedhető. Ellenőrizd:
        - main-hand működik, off-hand és gyors dupla katt nem indít második openinget;
        - required key több inventory stackből pontosan fogy, partial mass-open csak teljesen finanszírozott nyitásokat indít;
        - full inventorynál az item reward a játékos owner-szálán a helyére esik;
        - currency write/rollback hiba, command submit exception/null/rejection, `dispatchCommand == false` és command exception nem kap hamis completed/success állapotot;
        - két gyors reload, crate/world/definition cseréje finalize előtt, valamint quit/kick/disable minden lifecycle ponton;
        - spin/reveal entity és GUI cleanup, konkurens auditrotáció, stats-reset race;
        - restart recovery: egyszeri key refund vagy dokumentált `MANUAL_REVIEW`, jutalomduplikáció nélkül.
  - [ ] **Sit-only ülés** (GSit helyett): `/sit`, `/sit fel`, click-to-sit, üres kéz,
        stairs/slabs/carpets/moss carpet/pale moss carpet/snow pozíció, világ- és materialpolicy,
        unsafe/folyadék/clearance, namespaced tiltott parancs, konkurens reservation és minden
        damage/sneak/break/teleport/world-change/death/quit/kick/dismount/reload/disable cleanup.
        Lay, crawl, stacking és player/NPC sitting nincs a termékscope-ban.
  - [ ] **Moderáció** (SModeration helyett): `/mute <név> 5m teszt` → chat és natív `/msg`
        blokkolva; restart után is él; `/unmute`, `/history <név>`, `/punishments <név>` ugyanazt
        az autoritatív ledgert olvassa. Külön tempban/login, SocialSpy és corrupt-state teszt kell.
  - [ ] **Bejelentés:** `/report <név> <ok min 3 szó>` → az online moderátorok
        (icesmp.admin.moderation) azonnal értesülnek; 60 mp-en belül második report
        rate-limitbe fut; `/reports` lista → `/reports resolve <id>`; restart-álló.
  - [ ] **Inspektor:** `/icesmp inspect <név>` (icesmp.admin.inspect) → teljes riport
        (frakció/kaszt+XP/spec/erőforrás/egyenlegek/statok/bűn/claim/questek/cooldownok);
        offline névre korlátozott riport. ⚠️ **Folia:** távoli (másik régiós) célpontra is
        hibamentes (a riport a célpont szálán épül).
  - [ ] **Invsee** (InvSee++ helyett): `/invsee <név> read main|ender` élő read-only nézet;
        `/invsee <név> edit main|ender` csak külön edit permissionnel. Target/admin quit/kick,
        reload és reconnect közbeni escrow-visszaadás két eltérő Folia-régióban is ellenőrzendő.
- [ ] **QoL-kör (A43–A52, ÚJ):**
  - [ ] **Relációs háború-szín:** raid alatt a szemben álló fél tagjai PIROSAK a tablistádban
        és a fejük fölött — a raidben nem érintett harmadik frakciónak viszont NEM; a raid vége
        után a frakció-színek visszaállnak; a tablist-sorrend eközben NEM ugrál.
  - [ ] **AFK-jelzés/hátrasorolás:** ~3 perc tétlenség után „⌚ ᴀꜰᴋ" suffix + a játékos a rangján
        belül a tablist aljára kerül; mozgásra azonnal vissza.
  - [ ] **AFK-jutalomkapu:** AFK-jelölt játékos mob-, világboss- és kazamata-miniboss
        öléséből, virtuális kazamata-ládából vagy személyes Vad Hajsza-részesedésből NEM kap
        tiltott kassza-, liga-, buff-, advancement-, tervrajz- vagy tárgyjutalmat. A boss
        lifecycle/respawn közben lezárul.
        AFK-valuta vagy más időzített kifizetés sehol nem jelenik meg.
  - [ ] **Közös kill-jutalom előszűrő (`kill-rewards.*`):** MINDEN ölés-alapú jutalom ugyanazokon a
        szűrőkön megy át, tier szerint. Ellenőrizd:
        - **AFK-jelölten** (3+ perc) a mob-ölés nem ad kaszt-XP-t, lélekkövet, **pénz-erszényt ÉS
          mob-lootot** sem (korábban az erszény/loot még jött);
        - **spawner-mob** (rakj le spawnert) leölve nem dob erszényt/lootot, de **kaszt-XP-t igen**
          (a farmos szintezés szándékos);
        - **kreatív módban** ölve nincs FAUCET/PROGRESSION jutalom, de a **quest-haladás megy**
          (admin-teszt maradjon lehetséges);
        - **saját idézett minion** leölése semmit nem ad — sem lootot, sem XP-t, sem **quest-haladást,
          ranglista-pontot, bestiárium-bejegyzést vagy közösségi cél-számlálót** (ez utóbbi négy
          korábban pumpálható volt).
        Mind a négy szűrő kikapcsolható: `/icesmp config menu` → „Kill-jutalom szűrők".
  - [ ] **A korona átka (ÚJ, `factions.kings.crown-curse`):** ültess trónra egy királyt, majd
      állítsd `hours-per-level: 0`-ra… nem lehet (min. 1) — teszthez vedd `hours-per-level: 1`-re
      és `/icesmp reload`. Egy óra trónon = 1 szint. Ellenőrizd:
      - a szint-lépés EGYSZER szól („A korona hidegebb lett", szint/max),
      - minden szinten lich-türkiz derengés a korona (fej) körül,
      - `whisper-percent` szerint a Királynő suttog: **szintenként SAJÁT 8-soros készlet**
        (össz. 40 sor), és a szintek fokozatosan tárnak fel többet a történetből —
        1: puszta nyugtalanság → 2: a Káoszkor és a 698-as év → 3: az Elveszett Uralkodók
        NEVEI (Zhoris, Miinus, Benedictus, Lineata) → 4: Eleftheria mibenléte (a Fa negyedik
        gyermeke, az első suttogás, a Könny) → 5: nyílt ítélet (a lajstrom, a harmadik mondat).
        Ellenőrizd, hogy magasabb szinten NEM jönnek vissza az alacsonyabb szint sorai,
      - `stakes-from-level` (alap 3) ALATT semmi tét — csak hangulat,
      - fölötte: HUNGER (leáll a természetes regeneráció) + a közeli CÉLTALAN élőhalottak a
        királyt kezdik célozni (aktív célpontot NEM vesz el tőlük),
      - trónfosztás/újraválasztás után az átok **nullázódik** (nincs külön állapota: a
        trónon töltött időből számol),
      - a nem-király játékosokra semmi nem hat, és `enabled: false` mindent kikapcsol.
- [ ] **Célpont-sor:** harc-fókuszban az oldalsáv tetején „🎯 <célpont>" (játékosnál élet-sávval),
        az utolsó találat után ~10 mp-cel eltűnik.
  - [ ] **Crate-rulett:** kulcs-nyitáskor pörgő GUI (~3,5 mp, lassul), a végén a tényleges
        nyeremény áll meg; a GUI IDŐ ELŐTTI bezárása esetén is jár a nyeremény; kapcsoló:
        crates-settings.spin-animation. Quest-jutalomból kulcs: az onboarding_gather ad 1 köznapi kulcsot.
  - [ ] **3D crate-feltárás (DisplayFx, ÚJ):** a láda-blokk FÖLÖTT egy lebegő tárgy-ikon **pörög**
        a lehetséges nyeremények között (~1,5 mp), majd a tényleges nyereményen **megáll, felnagyul
        és aranyszínnel felizzik**, aztán eltűnik — mások is látják a láda mellett. Nem marad
        entitás-szemét (relog/restart után sem). Kapcsoló: `display-fx.crate-reveal.enabled: false`.
        ⚠️ Folia: a pörgetés az ikon saját szálán fut, régióhatár-közeli ládánál is hibamentes.
  - [ ] **Kombó-kiemelés:** kombó/lánc-finisher utáni 3 mp-ben a sebzés-szám nagyobb, arany, "!"-lel.
  - [ ] **Mute-eszkaláció:** `/mute <név>` perc NÉLKÜL → 1. alkalom 5 perc, majd 30/180/1440
        (moderation.escalation-minutes); a blokkolt/cenzúrázott üzenetek a
        logs/chat-moderation.log-ba kerülnek (5 MB-nál forgatás).
  - [ ] **Esemény-MOTD:** vérhold indítása után a szerverlista MOTD-ja a vérhold-variánsra vált
        (frissítsd a listát), utána vissza a normál rotációra.
- [ ] **Natív MOTD completion — `feature/native-motd-completion`:**
  - [x] **AUTOMATED:** `motdRegressionTest` — TIME floor-mod, időablakon belül stabil RANDOM, seed-érzékenység és pool-lefedés.
  - [x] **AUTOMATED:** teljes signed-`long` parser (`2^53` fölött, MIN/MAX, kétirányú overflow, string, tört/NaN/Infinity) és minden MOTD boolean típushű validációja.
  - [x] **AUTOMATED:** kizárólag `{online}`/`{max}` placeholder; MiniMessage-only és vegyes MiniMessage/token szöveg változatlanul érvényes.
  - [x] **AUTOMATED:** vérhold > világboss > szezonzárás prioritás és milliszekundum-pontos szezonzáró küszöb.
  - [x] **AUTOMATED:** secure icon-root/köztes/fájl no-follow, traversal tiltás, helyes/hibás méret, sérült/túl nagy PNG és a hét bundled 64×64 ikon.
  - [x] **AUTOMATED:** két reload, scheduler rejection/null handle, task–fallback single-winner és disable utáni késői callback nem publikál cache-t.
  - [ ] **REAL FOLIA REQUIRED / MANUAL:** párhuzamos server-list pingek TIME és RANDOM módban, hiba és region-thread stacktrace nélkül.
  - [ ] **REAL FOLIA REQUIRED:** vérhold, világboss és szezonzárás egymásra fedése; a prioritás pontosan ebben a sorrendben érvényes.
  - [ ] **PERMISSION TEST / MANUAL:** vanished admin megjelenik vagy kimarad az online countból az `exclude-vanished-from-online-count` kapcsoló szerint; nem készül második vanish-state.
  - [ ] **RELOAD TEST:** ikonfájl csere közben két gyors `/icesmp reload`; a régi async generáció nem írja vissza a régi ikonkészletet.
  - [ ] **RELOAD TEST:** `/icesmp config set motd.selection-mode random`, `rotation-seconds`, vanish toggle és `icons.mode` azonnal új snapshotot ad.
  - [ ] **NEGATIVE TEST:** sérült, nem PNG, 32×32, 65×64, root/köztes/fájl symlink és 1 MiB feletti fájl kihagyása; a plugin többi része működik.
  - [ ] **NEGATIVE TEST:** ismeretlen enum, hibás típusú boolean, lebegőpontos/overflow integer, nulla max-player override, üres/65 elemű pool, ismeretlen placeholder, duplikált normalizált ID és hibás strict MiniMessage a MOTD-t fail-safe letiltja.
  - [ ] **MANUAL:** `{online}`/`{max}`, max-player override és NONE/DEFAULT/VARIANT/RANDOM ikonmód a kliens szerverlistájában.
  - [ ] **MANUAL / NEGATIVE:** MiniMOTD jar nélkül teljes átvételi teszt; proxy/vhost és upstream configkompatibilitás nincs és nem cél.

- [ ] **QoL-kör 2 (A53–A62, ÚJ):**
  - [ ] **`/afk`:** aktívból azonnali ⌚-jelölés + hátrasorolás + jutalomkapu; második
        `/afk` kézi és automatikus AFK-ból is aktívra vált, friss tétlenségi időablakkal.
        Mozgás/chat/interakció/más parancs törli; `/icesmp:afk` ugyanígy működik.
  - [ ] **Sidebar-számok:** az oldalsáv jobb szélén NINCSENEK piros sor-számok (blank numberFormat).
  - [ ] **{event}/{ping} tokenek:** a tablist headerben/footerben használható az {event} (aktív
        események) és a {ping} mostantól színkódolt (zöld <80 / sárga <150 / piros).
  - [ ] **Invsee-frissítés:** a 🔄 gombbal új pillanatkép (2 mp-es fék); a cím mutatja a kép korát.
  - [ ] **Kulcs-lore:** a crate-kulcs lore-jában a láda neve + top-3 jutalom esély-%-kal.
  - [ ] **Mute-lejárat:** a némítás lejártakor a játékos üzenetet kap (csak EGYSZER, akkor is, ha
        chat-spammel próbálkozott közben).
  - [ ] **Report-visszajelzés:** resolve után a bejelentő üzenetet kap — online azonnal, offline a
        következő belépéskor (restart-álló).
  - [ ] **Ikon-rotáció:** icons/*.png betöltés a konzol-logban; variánshoz kötött ikon váltáskor
        a szerverlista-ikon is cserél (RP nélkül is működik — ez szerver-oldali).
  - [ ] **Kombó-csík:** cast után az action baron fogyó ▰▰▰▱▱ csík mutatja a kombó-ablakot; új
        cast megszakítja; lejáratkor csendben eltűnik (nem ír „lejárt"-at).
- [ ] **QoL-kör 3 (A69–A70 + particle-fixek, ÚJ):**
  - [ ] **Egységes menü-hangnyelv:** a /menu hubban almenü-váltás lapozás-hangot, akció
        kattintás-hangot ad; az invsee nézet-váltása is; a hangok a gui.sounds.* kulcsokkal
        felülírhatók (rossz hang-név = csend, nem hiba).
  - [ ] **Quest-toast:** küldetés teljesítésekor a jobb felső sarokban vanília toast ugrik fel
        a quest nevével (a chat-üzenet mellett); az advancement-képernyőn NEM jelenik meg
        maradvány; kikapcsolás: quest-toast.enabled: false. Ha a szerver-implementáció nem
        támogatja, egyszeri konzol-warn + a toast kimarad (a chat-üzenet marad).
  - [ ] **Terep-követő határok:** a /claim show pereme dombon-völgyön át a TALAJT követi
        („földszinten, a blokkok felett"), NEM a játékos derékmagasságában lebeg; a sarok-oszlopok
        a claim TELJES magasságát mutatják; barlangban (felszín alatt) a perem a játékos szintjén
        IS kirajzolódik. Ugyanez a /territory show ringre/körre. ⚠️ Folia: távoli (másik régiós)
        határpontnál nincs hiba (fallback a néző szintjére).
  - [ ] **Claim-fényfal (DisplayFx, ÚJ):** a /claim show a particle-perem MELLÉ élenként egy
        megnyújtott, IZZÓ üvegfalat állít (saját/trusted=zöld, idegen=piros), ami csak NEKED
        látszik (más játékos nem látja), és a `border.show-seconds` letelte után magától eltűnik —
        NEM marad blokk-szemét a világban (kilépés/relog/szerver-restart után sem). Kapcsoló:
        `display-fx.claim-wall.enabled: false`; a fal anyaga/magassága configos. ⚠️ Folia: több
        régiót átfedő nagy claimnél is hibamentesen spawnol.
  - [ ] **Kincs-fényoszlop:** a kincs-láda fölött magas, messziről látható END_ROD-oszlop áll
        (dombok mögül is kivehető), a láda-szintű csillogás mellett.
- [ ] **Particle-polish (ÚJ):**
  - [ ] **Hangulat-effektek:** a szentjánosbogár/köd/lelkek/aurora ~24 mp-ig PULZÁL (2 mp-enként),
        a flavour-höz illő helyen (bogarak bokor-magasságban, köd a talajon, aurora magasan az
        égen) — NEM egyetlen villanás a fej fölött; kilépéskor a lánc leáll.
  - [ ] **Aurora fény-fátyol (DisplayFx, ÚJ):** az északi fénynél a particle mellé **két magasan
        lebegő, áttetsző, lassan oldalra sodródó fény-lap** jelenik meg az égen (teal + lila),
        CSAK neked látszik, és az effekt végén eltűnik — nem marad blokk-szemét (relog/restart után
        sem). Kapcsoló: `display-fx.aurora.enabled: false`; anyag configos.
  - [ ] **FLASH-diéta:** boss-spawn/enrage/SLAM, invázió-bajnok, rituálé és Szent Harag villanása
        egyszeri, nem halmozott — közelről sem vakít el hosszan.
  - [ ] **Konfetti mérséklés:** escort/vad hajsza/crate/kombó ünneplő-partikelei visszafogottak
        (≤16-18 darab), a hatás megmaradt, a "particle-hányás" eltűnt.
- [ ] **Sebzés-számok (ÚJ):** játékos által (kézzel vagy lövedékkel) megütött entitás fölött lebegő
      szám mutatja a bevitt sebzést (~1 mp-ig; játékos-áldozatnál piros, mobnál sárga); gyors
      sorozat-ütésnél nem spammel (250 ms limit/célpont).
  - [ ] **Láthatóság:** alapból (`visibility: attacker-only`) CSAK a sebző látja — egy közelben
        álló harmadik játékosnak NEM jelenik meg; `spells.damage-indicators.visibility: everyone`
        után mindenki látja; **lövedékes** (íj) találatnál is a lövő kapja meg a feliratot.
  - [ ] **Teljes kikapcsolás:** `spells.damage-indicators.enabled: false` → semmilyen felirat.
- [ ] **Halál-összegző (ÚJ):** halálkor a chatben az utolsó 10 mp sebzései (max 5 sor: „-2.5❤ Zombi
      (3.2 mp-e)", lövedéknél a lövő zárójelben) + összesített sebzés. Kikapcsolás:
      `spells.death-recap.enabled: false`. Ütés NEM számítódik duplán (entitás- vs. környezeti sebzés).

#### 4.15 Druida formák + parkour ✅
- [ ] Druida forma-spellek: LibsDisguises-szel vizuális átalakulás; nélküle stat-váltás (mindkettő teszt).
- [ ] `/parkour start <id>` futás; admin: `/parkour setstart|setfinish|remove`.

#### 4.19 Teljes-review javítások (ÚJ) + /iceitem
- [ ] **Kapu-bypass zárva (KRITIKUS):** enderpearl/chorus/portál/plugin-teleporttal SEM lehet
      kulcs nélkül DUNGEON-zónába jutni, körözöttként Caldesterába lépni, vagy a Kárhozat-zóna
      belépési grace-ét kihagyni (mindhárom listener PlayerTeleportEvent-et is figyel).
      Csónakban/csillében ülve átkelve a kapu kiszállít és visszatesz a határ elé.
- [ ] **Fegyvertilalom-rések:** fővárosban bejelentkezve fegyverrel a kézben, ill. ládából
      shift-kattal az aktív slotba vett fegyvernél is elrakat az őrség (join + láda-zárás check).
- [ ] **Invázió Folia-biztos:** a horda-gyűrű tagjai régióhatár közelében is hibátlanul
      spawnolnak (pontonkénti régió-hop); `/events` státusz nem mutat "tomboló inváziót"
      azután, hogy minden mob elhullott (halál-esemény takarít); dupla `/events invasion`
      hívás nem indít torlódó hullámokat (15 mp indítási türelem).
- [ ] **Rontás-zóna:** a mob-utánpótlás sem spawnol claim/város/WG-régió belsejébe (a
      spawn-rules mátrix a terjedésre is érvényes); `corruption.enabled: false` reload után az
      aktív góc magja eltűnik, fajzatai despawnolnak; tömeges korrupt-farmolás nem okoz
      tick-lassulást (a purge-kill számláló mentése a globális tickre gyűjtve).
- [ ] **Config-írás verseny:** két admin egyszerre kattint a config-menüben / ad ki
      `/icesmp config set`-et → mindkét módosítás megmarad (szerializált override-út).
- [ ] **Frakció-étel váltásnál:** frakcióváltás után NEM jön azonnali honvágy-debuff — új
      türelmi idő indul (az időbélyeg frakcióhoz kötött).
- [ ] **Adó:** a levonás friss egyenleggel számol (a beszedés pillanatában kapott pénz is
      adózik); az adócsalás-strike a küszöbnél plafonoz; a nyilvántartásból törölt játékosok
      hátralék-sorai kitakarodnak a treasury.yml-ből.
- [ ] **Vérszomj sorrend:** a lifesteal a VÉGSŐ sebzés után számol (Jégvértes áldozaton
      kevesebbet gyógyít, mint páncélozatlanon).
- [ ] **Crafted-by stackelés:** két külön craftban készült, lore-os stackelhető étel
      összestackel (crafted_at időbélyeg csak nem-stackelhető gearre kerül).
- [ ] **B33 Végítélet-hét (ÚJ — Tier A):** állítsd a szezont úgy, hogy 7 napon belül járjon le
      (pl. `/icesmp config set world-events.season.length-days <kicsi>` — a season.yml `start`
      bélyegéhez képest számol) → napváltáskor lore-hangú broadcast (nap + pont-szorzó);
      a vérhold/világboss/invázió esély-kulcsa naponta nő (`chance-mult-per-day`), az invázió
      mob-szintje bónuszt kap; liga-pont jóváírás emelt (utolsó nap ×2 — `/events season`).
      **Utolsó nap:** EGYSZERI Szezonboss („A Lapforduló Őre") a NEUTRAL főváros pereme mellett
      (wall-offset; guard-bypass szándékos), emelt élettel; halálakor a `season-finale.boss.loot`
      tábla gurul (unique-sorok is: `unique:<id>[:db]` — LootTable-bővítés) + extra liga-pont +
      saját broadcast. Restart után NEM duplázódik (season-finale.yml jelző); főváros-zóna
      nélkül sima világboss-útra esik vissza. Kulcsok: `world-events.season-finale.*`.
- [ ] **D17 Korszakváltás-narratíva (ÚJ — Tier A):** szezonzáráskor a bajnok-broadcast után
      title („Lapforduló") + 4-6 késleltetett, krónikás-hangú chat-sor (nyitány-variáns, az
      előző szezon top-1 hősei szint/vagyon/raid szerint, bajnok-sor VAGY „üres korona",
      zárás-variáns; `line-delay-seconds` ütem). Kikapcsolás:
      `world-events.season.transition-story.enabled: false`. Variánsok messages-kulcsokból
      (`season-story-*`) felülírhatók.
- [ ] **D19 A Rejtélyes Idegen (ÚJ — Tier A):** `/events stranger` → néma, sérthetetlen,
      csuklyás alak („Az Idegen") spawnol 16-40 blokkra; a 24 blokkon belül állók egy talányos
      sort kapnak; jobb-katt NEM nyit kereskedést (elnyelve); 45 mp után lélek-köddel eltűnik.
      Természetes felbukkanás ritka (`stranger-npc.chance-percent` 6%/90 perc); spawn-rules
      mátrix-sor: `stranger`. NINCS semmilyen jutalom — ez szándékos.
      **Egyszerre-egy teszt:** amíg áll egy Idegen, az újabb `/events stranger` **false**-szal
      elhasal (nem lesz két alak), és a 45 mp-es köddé válás után megint indítható.
- [ ] **D9 Énekmondó (ÚJ — Tier A):** nevezz el egy FancyNpcs-NPC-t `enekmondo`-nak (vagy írd
      át a `bard.npc-name`-et) → jobb-kattra a HETI ballada (top-1 szint/vagyon/raid sablon-
      variánsokkal; egész héten ugyanaz a dal, hétfőnként fordul). FancyNpcs nélkül nem elérhető.
- [ ] **D15 Tábortűz-mesélés (ÚJ — Tier A):** tábortűznél SNEAK+jobb-katt → „Leülsz a tűzhöz…"
      action bar; 6 mp helyben maradás után sztori-sor + 8 XP + lélekláng-particle (a közelben
      ülők „X mesél a tűznél…" action bart látnak); elsétálva megszakad, nincs jutalom;
      cooldown 60 perc (PDC `cd_campfire_story` — csak SIKERES mesélés indítja). Kulcsok:
      `campfire-story.*` (general.yml), saját sztori-lista a `stories` kulccsal.
      **Sztori-átadás (bővítve):** a készlet **62 sor**, és végigmeséli a kódexet — Teremtés
      (Asterlayna, Aetrinita, a négy gyermek), Hasadás (Hu. 1.), a három birodalom alapítása
      (14 / 117 / 547), a Hetedik Vérháború és a 698-as Káoszkor, a Felsők megérkezése (978).
      Cél: a játékosnak NE kelljen kódexet olvasnia. Ülj le sokszor (cooldownt vedd 0-ra
      teszthez) és ellenőrizd, hogy tényleg váltakoznak, nem 5-6 sor forog.
      **Frakciós nézőpont (ÚJ):** a közös 62 sor MELLETT minden frakciónak van saját, 22 soros
      készlete — UGYANAZOK az események a saját népük hangján és elfogultságával, frakció-színnel
      (Láng=piros, Fagy=aqua, Menedék=arany, Kitaszítottak=lila). A
      `campfire-story.faction-chance-percent` (alap: 50) dönti el, milyen eséllyel jön a frakciós
      változat. Teszt: ülj le sokszor UGYANAZZAL a játékossal, majd frakciót váltva — a mesék
      hangja és színe váltson; 0-ra állítva CSAK a közös készlet jöjjön. Minden sor külön
      átírható a `campfire-story-<frakció>-<n>` kulccsal.
- [ ] **Sztori-csatornák összesítve (ÚJ):** a világ történetét négy ingame felület tanítja,
      összesen **~255 sorban** — ellenőrizd, hogy mind él:
      - **tábortűz** (62 közös sor + 4×22 frakciós = 150, ismételhető, ingyen — a fő csatorna),
      - **a Rejtélyes Idegen** (45 talányos sor, `/events stranger`),
      - **a bárd heti krónika-versszaka** (20 fejezet: MINDEN héten más szelet az idővonalból,
        a hősök dicsérete ELŐTT hangzik el — `/events`-független, az `enekmondo` NPC-nél),
      - **a korona átka** (40 suttogás, szintenként más — csak királynak).
- [ ] **Haladás-fül (ÚJ, jarból szállított datapack):** a szerver indulásakor a logban jelenjen
      meg, hogy a fa a **jar datapackjéből** él (`IceSMP advancement-fa: 22/22 bejegyzés a jar
      datapackjéből él`). Ha WARNING jön „DEPRECATED tartalék úton" szöveggel, a
      datapack-felderítés hibázott — a fa akkor is működik, de nézd meg a bootstrap-logot.
      Ellenőrizd `/datapack list`-tel, hogy az `icesmp` pack **engedélyezve** van. A **Haladás**
      képernyőn (L) legyen **IceSMP** fül `beacon` ikonnal. `advancements.enabled: false`
      (general.yml) + restart → a grantek no-opok.
- [ ] **A fa-bejegyzések NEM toastolnak és NEM írnak chatbe** (`show_toast:false`,
      `announce_to_chat:false` mindegyik JSON-ban): a visszajelzést az adott rendszer saját
      chat-üzenete adja, az ünneplő toast pedig a külön, három fix toast-bejegyzés (lásd lentebb).
      Ellenőrizd, hogy egy fa-grant (pl. kaszt-választás) NEM ugrik fel a sarokban.
- [ ] **Hovatartozás vs. Kitaszítva (ÚJ, a világ tényleges szerkezete):** a világban NÉGY
      hatalom van, de a játékos csak HÁROM közül választhat — a Menedékben KEZD (a NEUTRAL az
      alapértelmezett frakció), onnan a Lánghoz vagy a Fagyhoz állhat. A Kitaszítottak közé nem
      lépni lehet, hanem KERÜLNI. Ezt a fa is így mondja el: `faction_join` („Kikötöttél az
      egyik hatalom mellett") → alatta a REJTETT `exiled` („Bűnök vezettek a Néma Királynő népe
      közé") → alatta a `redeemed` („Megtörted az örök paktumot"). Ellenőrizd, hogy a
      `faction_join` szövege SEHOL nem állítja, hogy négyből választhatsz.
- [ ] **A Kitaszítottakhoz HÁROM út vezet, és MIND a `exiled` bejegyzést adja** (e nélkül a
      `redeemed` szülő nélküli csomópontra érkezne):
      1. **bűn-küszöb:** vigyél fel egy játékost 4 bűnre (`/sinner <j> add`) → automatikus
         száműzetés + **Kitaszítva**; majd a vezeklés-lánccal → **Vezeklés**.
      2. **Suttogó-lelepleződés (K9):** a gyanú a küszöbig → `expose()` a bűn-pipeline-on át
         száműz → **Kitaszítva** (a Suttogó-út a bűn-küszöbön keresztül fut, nem külön ágon).
      3. **önkéntes paktum:** bűnösként `/faction join dark` (kétlépcsős megerősítés) →
         **Kitaszítva** akkor is, ha a bűn-küszöböt még nem érte el.
- [ ] **„Akit a csend befogadott" (ÚJ, REJTETT — a Suttogó-státusz titka):** a Sötét Rítus
      sikere (éjjel + sculkon + magányosan + vér-áldozat) adja a `whisperer` bejegyzést.
      **Titok-ellenőrzés:** a granthez NEM tartozik chat-broadcast és NEM ugrik fel toast,
      tehát a közelben lévő játékosok semmit nem látnak belőle; a bejegyzés a saját
      haladás-fülön is rejtett keretben van. Lelepleződés után a státusz elveszik, de a
      megszerzett bejegyzés (jogosan) megmarad — az emlék nem törlődik.
- [ ] **A 22 csomópont grant-pontja:** mindegyik a saját eseményén jár, egyik sem holt.
      Sorra: kaszt-választás (`root`+Elhivatás) • spec-választás • max kaszt-szint •
      capstone-talent vásárlása • frakció-belépés • Suttogó-rítus (rejtett) • Kitaszítottak
      (három út, lásd feljebb) • királlyá választás • a korona-átok max szintje • megnyert raid
      (CSAK a győztes oldal jelentkezett harcosainak!) • vezeklés (DARK-paktum megtörése) •
      szakma-választás • szakma max szint • mestermű-craft (Legendás/Ereklye roll) • rontás-góc
      megtörése • rejtett hely • világboss • első relikvia • rituálé • pet max szint •
      parkour-futam. Gépi ellenőrzés: `python3 scripts/check_consistency.py` FAIL-el holt
      bejegyzésre, árva JSON-ra ÉS tartalom-driftre (a JSON-ok generátora:
      `python3 scripts/gen_advancements.py`).
- [ ] **Toast (ÚJ, fix bejegyzések):** quest-teljesítéskor felugrik a „Küldetés teljesítve"
      toast, és **ismételten is** (nem csak egyszer — a bejegyzés visszavonódik). A quest NEVE
      a chat-üzenetben van, nem a toaston. A haladás-fülön a toast-bejegyzések NEM látszanak
      (rejtettek). **Szaporodás-ellenőrzés:** teljesíts 20-30 questet, majd nézd meg a
      `<world>/datapacks/bukkit/` fájlszámát — MOST már nem nőhet, mert nincs futásidejű
      advancement-betöltés (a régi megoldás véletlen kulcsokat használt).
- [ ] **A38 Spawn-élmény (ÚJ — Tier A):** állíts be `world-events.intro.first-join-spawn`-t
      ("world,x,y,z[,yaw,pitch]") → az ELSŐ belépő oda teleportál, és csak utána indul az
      intro; visszatérő belépésnél rövid, halk üdvözlő title + csengő hang
      (`intro.join-welcome.*` — nem torlódik az intróval, mert első belépéskor az intro fut).
- [ ] **B19 Évszakos modifikátorok (ÚJ — Tier A):** a VALÓS évszak szorzói
      (`world-events.season-modifiers.<tel|tavasz|nyar|osz>.<esemény>`) a vérhold/világboss/
      invázió/hajsza/bőség/gyűjtögető-láz sorsolási esélyén (télen vérhold ×1.4, nyáron
      bőség ×1.4 stb.); `enabled: false` mindet kikapcsolja; a B33-finálé szorzójával összeszorzódik.
- [ ] **D3 Szezon-emlékmű (ÚJ — Tier A):** állíts be `season-monument.location`-t
      ("world,x,y,z") → szezonzáráskor a blokk a bajnok banner-színére vált, fölötte hologram
      („A Korszakok Könyve" + sorszám + bajnok + top-3 hős); a lista korszakonként bővül
      (max-lines), restart-álló (monument.yml + perzisztens TextDisplay). Bajnok nélküli
      szezon nem kerül kőbe.
- [ ] **Eldobható minion halála nem állítja le az élő társat (ÚJ):** a permanens társ és a rövid
      életű spell-minion ugyanazt a tulajdonos-jelölést viseli, és a halál-kezelő ELŐBB bontotta a
      gazda-kulcsos állapotot (aktív társ, harci cél), csak utána ellenőrizte az azonosságot.
      **Teszt:** idézd meg a permanens társat, majd idézz egy eldobható minion-hordát és hagyd
      meghalni → a társ **maradjon aktív és vezérelhető** (harci cél, állásmód); ezután a TÁRS
      haláljakor jöjjön a „társad elesett" üzenet és a respawn-cooldown.
- [ ] **Pet-rítus nem fut kétszer (ÚJ):** egy fizikai jobb kattintás main- ÉS off-hand eventet is
      ad, a rítus viszont mindkettőben a main-handet olvasta → 2+ darabos stacknél KÉT ritka
      kelléket fogyasztott. **Teszt:** tarts 2 Nyughatatlan Szívet (vagy Démon-pecsétet), futtasd a
      rítust → pontosan EGY fogyjon el.
- [ ] **Emlék-XP nem viszi el a szilánkot kaszt nélkül (ÚJ):** a `/emlek xp` előbb elvette a
      szilánkokat, és az XP-jóváírás boolean-je elveszett — kaszt nélkül a játékos elveszítette a
      ritka szilánkokat, mégis sikert olvasott. **Teszt:** kaszt NÉLKÜL `/emlek xp` → érthető
      elutasítás, és a szilánkok **maradjanak meg**; kaszttal a beváltás menjen és adjon XP-t.
- [ ] **Elérés-azonosító kanonizálás (ÚJ):** a megszerzett elérések listája PDC-ből visszaolvasva
      kisbetűsít, a tárolás viszont a config eredeti casingjét használta — egy `RichOne` azonosítójú
      elérés így MINDEN periodikus tickben újra kifizette a jutalmát.
      **Teszt:** vegyél fel a `quests.yml` `achievements.definitions` alá egy nagybetűs azonosítót
      (pl. `TesztElereny`) → indításnál a konzol figyelmeztessen, és a sor maradjon KI (különben
      ismételt jutalmat adna); a 21 shippelt elérés (mind kanonikus) változatlanul működjön, és egy
      már megszerzett elérés NE fizessen újra a stats-tick körökben.
- [ ] **Főzet-XP csak VALÓDI kivételre (ÚJ):** a főzőállvány eredmény-slotjaiban a puszta
      „van benne főzet" korábban XP-t adott, ezért a bent maradó főzetre ismételt kattintás
      korlátlan Alkimista-XP-t és heti szakma-cél haladást termelt. Most csak a tényleges kivétel
      számít. **Teszt:** kattints a kész főzetre inkompatibilis kurzorral (no-op) → **ne** kapj
      XP-t; kattints rá üres kurzorral (kivétel) → kapj; a most üres slotra kattintva ne kapj újra;
      shift-kattintás és hotbar-csere is fizessen (azok is kivételek).
- [ ] **Heti szakma-cél csak a SAJÁT szakmádtól tölthető (ÚJ):** korábban egy nem-bányász
      ércbontása is töltötte a Bányász-céh heti közös célját, mert a listener eldobta a
      XP-jóváírás eredményét. **Teszt:** olyan játékossal, akinek NINCS bányász szakmája, törj
      ércet → se személyes XP, se heti cél-haladás; bányászként ugyanez mindkettőt adja.
- [ ] **Quest-lánc ciklus nem futhat végtelen jutalom-hurokba (ÚJ):** egy önmagára (vagy körben)
      mutató, repeatable, nulla cooldownos, már teljesített `REACH_LEVEL` quest az
      `accept → complete → reward → advanceChain → accept` láncot végtelenszer futtatta volna.
      Két védelem: a `check_consistency.py` FAIL-el a `next`-gráf bármely ciklusára (push előtt
      kiderül), futásidőben pedig 16-os mélység-korlát áll, SEVERE naplóval.
      **Teszt:** `/quest admin set <id> next <ugyanaz az id>` → a checker akadjon meg; ha mégis
      élesbe kerül, a konzolban „Quest-lánc mélység-korlát" sor legyen, és a szerver ne álljon meg.
- [ ] **Céhtagság nem éli túl a frakcióváltást (ÚJ):** a céh egy frakción belüli szervezet, mégis
      az ellenséges oldalra váltó játékos tovább vezethette a régi frakció céhét, az ott teljesített
      questjei a régi céh XP-jét növelték, és a kasszát a RÉGI frakció valutájában kezelhette.
      Az egyeztetés KÖZPONTILAG a `setFaction`-ban fut, tehát minden út érvényes.
      **Teszt mind a négy úton** (`/faction join`, `/faction leave`, admin `/faction set`,
      bűn-száműzetés, vezeklés):
      1. tagként válts frakciót → lépj ki a céhből, és kapj róla üzenetet;
      2. **vezetőként** válts → az irányítás a legrégebbi másik tagra szálljon;
      3. **egyedüli vezetőként** válts → a céh szűnjön meg (ne maradjon üres, gazdátlan céh);
      4. váltás után a `/ceh info` ne mutasson tagságot, a quest-teljesítés ne töltse a régi céhet,
         és a `/ceh befizet` ne menjen.
- [ ] **Gyűjtés nem játszható vissza (ÚJ — kiadásblokkoló volt):** a `COLLECT_ITEMS` progressz a
      felvett stack méretét könyvelte, és mivel a DOBÁS mindig új item-entitást hoz létre (új UUID),
      ugyanazzal a fizikai stackkel korlátlanul növelhető volt a gyűjtő-quest ÉS az ismételhető
      közösségi cél — utóbbi minden körben kifizette a teljes treasury-, liga- és buff-jutalmat.
      **Teszt:**
      1. vegyél fel egy vasrudat gyűjtő questet (vagy legyen aktív ilyen közösségi cél);
      2. tarts magadnál egy stacket, dobd le, vedd fel — **a haladás NE nőjön**, akárhányszor
         ismételed;
      3. **pingpong-teszt:** dobd le, és MÁSIK játékos vegye fel → nála se nőjön;
      4. **halál-teszt:** halj meg (ne legyen keep-inventory), majd vedd fel a saját dropodat →
         ne nőjön (a halál-dropokat egy tickkel később, a halál helye körül jelöljük meg);
      5. **legitim út:** bányássz/ölj újat, és úgy vedd fel → **nőjön** a haladás;
      6. **tele hátizsák:** hagyj egy szabad helyet, és vegyél fel egy 64-es stacket → csak a
         TÉNYLEGESEN átkerült darab számítson (nem a teljes 64);
      7. `keep-inventory` bekapcsolva halálnál ne jelöljön semmit (nincs is drop).
- [ ] **Sérült állapotfájl NEM írható felül (ÚJ — kiadásblokkoló volt):** a
      `YamlConfiguration.loadConfiguration(File)` FAIL-OPEN — hibás YAML-nál nem dob kivételt,
      csak naplóz és ÜRES konfigurációt ad. Így a manager üres állapottal indult, a következő
      autosave pedig szabályos, de üres fájlt írt a kézzel még javítható adat FÖLÉ.
      Mind a 33 állapotfájl-betöltés a védett `YamlStore.loadTracked` úton megy.
      **Teszt (pl. `currency.yml`, `market.yml`, `claims.yml`):**
      1. állítsd le a szervert, tegyél a fájlba szintaktikailag hibás YAML-t (pl. tabulátor);
      2. indíts → a konzolban `SÉRÜLT állapotfájl: <név>` + `Karantén-másolat: <név>.corrupt-<ms>`
         + `MENTÉSE LETILTVA` SEVERE sorok;
      3. várd meg az autosave-et, majd állítsd le szabályosan → a **sérült fájl VÁLTOZATLAN**
         (nem íródott felül), és ott van mellette a `.corrupt-` másolat;
      4. javítsd a fájlt, indíts újra → az adat betöltődik, a mentés újra működik (a jelölés
         sikeres parse-ra feloldódik).
      **FIGYELEM:** a `config/` és `messages/` fájlok SZÁNDÉKOSAN maradtak fail-openen — egy
      hibás config ne akadályozza meg a szerver indulását, és azokat sosem írjuk vissza.
- [ ] **Rituálé-áldozat NEM kerülhető meg (ÚJ — kiadásblokkoló volt):** az áldozat-ellenőrzés és
      a fogyasztás MOST ugyanazt a predikátumot használja (`PlainIngredients`) — korábban az
      ellenőrzés típus szerint számolt, a levonás viszont meta-egyezést kért, ezért egy
      **átnevezett** (üllőn elnevezett) áldozat fedezte a követelményt, de nem fogyott el:
      a relikvia-idézés, tisztítás, paktum, buff és hazateleport INGYEN, korlátlanul ismételhető volt.
      **Teszt minden rituálé-típusra** (`relic`, `cleanse`, `buff`, `home`, `uncurse`, `pakt`):
      1. nevezd át üllőn a konfigurált áldozatot → a rituálé **NE induljon el**
         („Hiányoznak az áldozati tárgyak");
      2. sima, névtelen áldozattal induljon el, és az áldozat **fogyjon is el**;
      3. **kopott/sérült** áldozat (ha szerszám) számítson ÉS fogyjon;
      4. unique-material NE fedezze a sima anyag-követelményt;
      5. a konzolban NE legyen „Rituálé-áldozat nem fogyott el" SEVERE sor — az invariáns-sértést jelez.
      Ugyanez a szerződés védi a szakma-craftot; gépi őr: a `check_consistency.py` FAIL-el minden
      `removeItem(new ItemStack(...))` visszaesésre.
- [ ] **Viselt relikvia NEM tűnhet el relognál (ÚJ — kiadásblokkoló volt):** a belépési
      relikvia-sweep MOST egyetlen bejárás. A `PlayerInventory#getContents()` a teljes slotteret
      adja (0-35 tár, 36-39 páncél, 40 offhand), ezért a korábbi külön páncél-/offhand-kör
      ugyanazt a tárgyat látta másodszor, és a közös duplikátum-szűrő a VISELT relikviát törölte.
      **Teszt (mind a 6 relikviára, de főleg a 4 elytrára):**
      1. vedd FEL a relikvia-elytrát (mellvért-slot), lépj ki, lépj vissza → **maradjon meg**;
      2. ugyanez offhandbe fogott relikviával;
      3. ugyanez páncél-slotos relikviával;
      4. **valódi duplikátum**-teszt: adj magadnak KÉT példányt ugyanabból a relikviából (egyet
         viselve, egyet a tárban) → relog után pontosan EGY maradjon;
      5. **lejárat**: állítsd a relikvia inaktivitás-elenyészését azonnalira → a VISELT példány is
         évüljön el (a sweep a páncél-slotot is eléri, nem csak a tárat);
      6. a kozmetikai frissítés (név/lore újraírás) a viselt darabon is fusson le.
- [ ] **Visszavont akció NEM jutalmaz (ÚJ — kiadásblokkoló volt):** a progressz-listenerek
      (quest, napi, szakma-XP, szerver-kihívás, gyűjtő-buff) `MONITOR` prioritáson könyvelnek, a
      védelem (claim/territórium/unique) `HIGH`/`HIGHEST`-en cancel-el. Korábban a `NORMAL`
      prioritás miatt a progressz a visszavonás ELŐTT megtörtént.
      **Teszt:** állj MÁS játékos claimjébe (nincs törési jogod), és törj ismételten ércet/termést:
      1. a blokk NE törjön el (a védelem cancel-el);
      2. szakma-XP, quest-, napi-, közösségi és szerver-kihívás haladás **NE** nőjön;
      3. aktív Bányász-láz / Termés-óra alatt **NE** essen bónusz-drop a védett blokkból
         (ez korábban valódi tárgy-duplikációt adott);
      4. ugyanez tiltott blokk-lerakással és védett unique fogyóeszközzel.
      **Kontroll:** a SAJÁT claimedben ugyanezek az akciók továbbra is adjanak XP-t, haladást és
      bónusz-dropot — a MONITOR nem szüntetheti meg a legitim jutalmat.
      Gépi őr: `scripts/check_consistency.py` FAIL-el, ha egy progressz-handler visszakerül
      alacsonyabb prioritásra.
- [ ] **B54 Átkozott felszerelés (ÚJ — Tier A):** világboss/event-boss loot ~8%-a Átkozott
      (sötétvörös lore-sor): viselve darabonként +10% kimenő sebzés (cap +40%), de a
      páncél-slotból NEM vehető ki („Az átok nem ereszt…"). Felvételkor az első kísérlet
      figyelmeztet, csak a `confirm-seconds`-on (alap: 5) belüli második erősít meg. Levétel:
      építs Átok-törés oltárt (CRYING_OBSIDIAN mag + 3×3 obszidián talp, áldozat: 8 ametiszt +
      1 ghast-könny) → SHIFT+jobb-katt → minden viselt/kézben tartott átok megtörik, a tárgy megmarad.
      Kulcsok: `item-rarity.cursed.*`, rituálé: `rituals.atok_tores`.
- [ ] **B54 — a felvétel MINDHÁROM útja külön tesztelendő** (két megerősítés-kapu együtt
      dolgozik: az `InventoryClickEvent` és az `EntityEquipmentChangedEvent`, és a kettő
      korábban kioltotta egymást — ez a visszaesés-teszt):
      1. **jobb-katt** kézből (a legtermészetesebb út) → 1. kísérlet: a darab lekerül és
         visszakerül a hátizsákba + figyelmeztetés; 2. kísérlet 5 mp-en belül → **FELMARAD**.
      2. **shift-katt** a hátizsákban → 1. kísérlet: cancel + figyelmeztetés; 2. kísérlet
         5 mp-en belül → **FELMARAD** (nem szabad, hogy azonnal visszakerüljön a hátizsákba!).
      3. **kurzorral a páncél-slotba tétel** → ugyanaz, mint a 2.
      **A hurok-tünet, amit keresünk:** ha a darab a megerősítés UTÁN is visszakerül a
      hátizsákba és újra jön a figyelmeztetés, akkor a két kapu ismét ugyanazt a jelzést
      fogyasztja — a felvétel ilyenkor SEMELYIK úton nem sikerül.
      **Tele hátizsákkal:** a visszavett darab a földre esik — ellenőrizd, hogy ez CSAK a
      megerősítés előtti (elutasított) kísérletnél történhet meg, mert viselt átkozott darab
      földre kerülése a levétel-zárat is megtörné.
      **Nem átkozott páncél** fel-le vétele semmilyen üzenetet ne adjon, és NE fogyassza el egy
      folyamatban lévő átkozott-megerősítést sem.
- [ ] **F13/F14/F15 Gazdasági események (ÚJ — Tier A):** a kereslet-sokk mellett most
      **pánik** is jöhet (~35% eséllyel a sorsolt esemény lefelé üt: x0.6-0.8, piros
      broadcast, a lecsengése „A pánik elült…"); **konjunktúra**: ritkán fél órára egy
      valutában 10%→5% a piaci eladási díj (broadcast nyit/zár; a díj-kedvezmény azonnal él
      az eladás-jóváírásnál); **finálé-tőzsdeláz**: a Végítélet-hét alatt a sokkok esélye ×3,
      kilengése ×1.5, hossza ÷4. Kulcsok: `currency.economy-event.panic-*` / `finale-*`,
      `currency.market-boom.*`. A szezonzárás továbbra sem wipe — csak a liga-pontok nullázódnak.
- [ ] **I7 Évszakos termés (ÚJ — Tier A):** Bőség-idő alatt (`/events abundance` kényszerítheti)
      a Gyógynövényész betakarítás-XP-je ×1.5 (`professions.seasonal.abundance-multiplier`);
      CSAK virág/érett termény/harvest — érc/rönk-törés XP-je változatlan.
- [ ] **I22 Loot-only receptek (ÚJ — Tier A):** a három csúcs-netherit tervrajz (Mélybányász
      Netherit Csákány, Erdőirtó Netherit Fejsze, Sárkányvért) tervrajza CSAK világboss/
      event-boss lootból eshet (sima mob-drop poolból kikerült); a recept-könyvben lila
      „Csak legendás ellenfelektől szerezhető tervrajz" zárolt sor. Új recept-mező:
      `loot-only: true` (profession-recipes.yml).
- [ ] **D8 Titkos helyek (ÚJ — Tier A):** vegyél fel egy spotot a configba
      (`hidden-spots.spots.<id>`: name/location/radius/xp/rewards) → aki elsőként odaér,
      jutalmat kap + „🧭 X ELSŐKÉNT fedezte fel…" broadcast; a többiek egyszeri, fél-értékű
      jutalmat (repeat-reward-ratio) és a felfedező nevét látják; `first-finder-only: true`
      esetén csak az első kap bármit. A jelölés PDC-s (relog után sem duplázható); a check
      30 mp-enként, a játékos saját szálán fut. Kulcsok: `hidden-spots.*` (world.yml).
- [ ] **Tartalombővítés 2. hullám (ÚJ):**
  - [ ] **6 új unique anyag** (`/iceitem unique …`): jegviragpor, parazsmag, viharkvarc,
        melysegi_borostyan (régészeti leletként is!), sarkanycsont_szilank (boss-only drop),
        fonixpihe (mob-drop). Craft-védelem (nem rakható le/ehető/olvasztható) él rájuk.
  - [ ] **16 új recept**, köztük az 5 új signature fegyver: Glatziendorfi Jégtörő (lassított
        célon +25%), V. Miinus Haragja (LOOT-ONLY tervrajz; <35% HP-n +20%), Sárkánycsont Íj
        (pierce +2), I. Zhoris Lángnyelve (LOOT-ONLY; gyújtás + égő célon +15%), Napfogyatkozás
        (éjjel gyorsabb + erősebb nyíl) — kulcsok: `signature.jegtoro/miinus/sarkanycsont/
        langnyelv/napfogyatkozas.*`. Plusz: Sárkány-pörkölt (BLUE étel: hal-kötelezettség +
        rövid Erő), Sárkánycsont Pajzs, Viharüveg Lámpás, Vándor Úti Kenyere, Bokic-parti
        Gyógytea, Fagypáncél/Főnixtoll Tekercse (üllőre vihető iskola-counter könyvek).
  - [ ] **Új loot-sorok:** Fekete Csont (undead named), A Néma Királynő Suttogása (undead
        BOSS-only named, nagyon ritka), Főnixpihe (mob), Sárkánycsont-szilánk (boss).
  - [ ] **+3 /lore téma:** `melyseg` (Mélység Népe), `korszakok` (a Könyv), `bokic` (a folyó).
  - [ ] **Storytelling-poolok nőttek:** tábortűz 6→15, Idegen 6→12, bárd 15→25, krónika 8→12,
        szezon-átvezető 6→10 variáns; +2 MOTD-variáns (konyv, suttogas).
  - [ ] **+10 quest** (jegvirag_szuret, parazs_gyujtes, borostyan_kutatas, fonixpihe_vadaszat,
        sarkanycsont_kutato, porkolt_lakoma, uti_kenyer, viharkvarc_fejto, korrupt_irtas,
        karhozat_zarandoklat — az utóbbi territory-id-ját igazítsd a szerveredhez!).
- [ ] **Tartalombővítés 3. hullám (ÚJ):**
  - [ ] **34 unique anyag** (17 új): 10 szakma-gyártott (Holdezüst Huzal, Csontenyv,
        Sarkfény-cseppkő, Szavannafű-kötél, Obszidián-szilánk, Árnygomba, Lélekhamu,
        Aranyfüst-lemez, Gyöngyház-pikkely, Vándorfűszer), 3 VENDOR-ONLY (Számvevő-pecsétviasz,
        Finomított Lámpaolaj, Kovács-folyósítószer — CSAK az Általános Boltban kaphatók, a
        bolt-item `unique:` mezője a valódi PDC-s anyagot adja), 4 mob/boss-drop (Dermedt
        Könnycsepp undead, A Kapu Parazsa boss, Néma Kristály mob, Az Első Csend Szilánkja —
        a legritkább, weight 2 boss-only).
  - [ ] **176 recept** (+21): 10 anyag-recept, ünnepi étel MIND A 4 frakciónak (RED
        Vadlakoma: Gyorsaság+Tűzáll; NEUTRAL Vándorünnep Lepénye: Szerencse+Gyorsaság;
        DARK Hamvak Lakomája: Felszívódás+Éjjellátás; a BLUE pörkölt mellé), 3 új LEGENDÁS
        loot-only darab (A Mélység Népe Koronája, Viharjáró Csizma, Eleftheria Fátyla — mind
        Első Csend Szilánkját + vendor-anyagot + netheritet kér), és a meglévő legendások
        MEGDRÁGÍTVA (Miinus/Lángnyelv: +Szilánk/Parázs + folyósítószer; Jégtörő/Napfogyatkozás/
        Lámpás: vendor-anyag igény).
  - [ ] **Storytelling ~kétszerezve:** tábortűz 15→29, Idegen 12→23, bárd 25→40, krónika
        12→19, szezon-átvezető 10→16 variáns.
- [ ] **Tartalombővítés 4. hullám — vendor-gazdaság + jövedelem-csapok (ÚJ):**
  - [ ] **39 új vendor-only szakma-kellék** (szakmánként 5 — össz. 73 unique anyag): a
        **Szakmai Kellékbolt** (`/npc create kellekbolt`) mind a 42 vendor-árut árulja;
        a boltok kínálata TELJESEN configból jön (economy.yml faction-shops — sorok
        törölhetők/árazhatók/másolhatók bármely NPC-boltba). 16 recept mostantól kelléket
        (is) kér (Kősó, Írnok-tinta, Edzőolaj, Sózott csali…).
  - [ ] **Rotálódó karaván:** a karaván áru-poolja 12 tételes (4 régi + 8 RITKA alapanyag:
        Emlékszilánk, Sárkánycsont-szilánk, Főnixpihe, Néma Kristály, Borostyán, Viharkvarc,
        Könnycsepp, Kapu Parazsa — drágán); érkezésenként `stock-size` (5) darab sorsolódik
        ("ma épp ezt hozta"), a látogatás alatt stabil. Kulcsok: `caravan.rotation.*`.
  - [ ] **Felvásárló NPC** (`/npc create felvasarlo`): a kézben tartott nyersanyag jobb-kattra
        eladva a `buyer.prices` fix árain, NAPI kerettel (`daily-cap` 250, PDC-ben követve,
        éjfélkor nullázódik); PDC-s (egyedi) tárgyat nem vesz; a fizetség FIZIKAI veret a
        kézbe, egészre lefelé kerekítve (1 veret alatt nem vesz). Üzenet mutatja a maradék keretet.
  - [ ] **Kopott erszény (fizikai pénz-tárgy, WoW-stílus):** a talált pénz TÁRGYKÉNT
        érkezik — bőr-item, PDC-ben darabszám + VÉLETLEN frakció-valuta; jobb-katt FIZIKAI
        veretekre (token-item) bontja a kézbe (üzenet + hang). Admin-adás:
        `/iceitem erszeny <összeg> [darab] [játékos]`.
  - [ ] **„Számlára csak a bankból” szabály:** MINDEN jutalom-kifizetés (quest, napi/heti
        kihívás, mérföldkő, parkour, ambient-esemény, vérdíj, Felvásárló,
        Bankbetét-jegy) fizikai veretet ad a kézbe — addToBalance jutalom-úton NINCS;
        a számlára kizárólag `/bank deposit` tesz pénzt. A piac/aukció/kincstár bankon
        belüli átvezetés marad.
  - [ ] **Mob pénz-drop:** ellenséges mob játékos-ölésekor ~20% eséllyel Kopott erszény
        esik (1-4 + mob-szintenként +0,5); spawner-mob SOSEM dob (entitás-PDC jelölés,
        restartot is túléli). Kulcsok: `mob-money-drop.*`.
  - [ ] **Horgász-szerencse:** ~4% eséllyel a fogás mellé iszapba veszett Kopott erszény
        akad (5-15 összeggel, véletlen valutával); AFK-jelöltnek nem jár. Kulcsok:
        `fishing-windfall.*`.
- [ ] **D1 — Szezonális ünnepek (ÚJ):**
  - [ ] HolidayService: valós naptári MM-DD ablakok (év-átfordulással), 4 kánon-ünnep
        előre configolva (Rém-éj 10-25→11-02, Érkezés Napja 12-20→12-27, Hasadás Napja,
        Ultimátum Napja); ablak-nyitás/zárás broadcast; `override(kulcs)` API-n át a
        managerek futásidőben kaphatnak ünnep-skint (a config sosem íródik át).
        Kulcsok: world.yml `seasonal-events.*`.
- [ ] **D11 — Járőröző városi őrség (ÚJ, v1):**
  - [ ] Config-útvonalú (waypoint-listás) őr-NPC-k: plugin-spawnolta, sebezhetetlen,
        nevesített villager-őrök; a léptetés MINDIG az őr saját entity-schedulerén fut
        (kis teleportAsync-lépések, több-régiós útvonalon is); éjjel gyorsabb tempó.
  - [ ] Restart-újraspawn, shutdown-takarítás; példa-route kikommentelve. Kulcsok:
        world.yml `city-guards.*` (guards.<id>.name/world/route).
- [ ] **J7 — Rejtvény-küldetések (ÚJ; tulaj-döntés: SOSINCS súgás):**
  - [ ] `riddle: true` quest-mező: a napló/haladás-sor MINDIG "??? — a nyomot a leírás
        rejti"-t mutat — a cél sosem tárul fel, a megfejtés a játékosé/közösségé
        (az időzített súgás-fokozat kivezetve, nincs hint-minutes kulcs).
  - [ ] 26 rejtvény-quest él (rejtveny_* — gyűjtés, vadászat,
        olvasztás, biom, horgászat, NPC-keresés versbe rejtve); admin-szerkesztés:
        `/quest admin set <id> riddle true`.
  - [ ] Ellenőrzés: rejtvény-quest felvéve → a /quest info és a napló SOHA nem írja ki
        a konkrét célt, de a teljesítés a megfejtett cselekvésre magától bekövetkezik.
- [ ] **F11 — Ereklye-börze (ÚJ; tulaj-döntés: valódi relikvia NEM listázható):**
  - [ ] `/market ereklye`: a böngésző a PDC-tages tételekre (unique anyag) szűrve
        nyílik (`@ereklye` belső szűrő a MarketGUI-ban); a kereső-infra változatlan.
  - [ ] VALÓDI relikvia (relic_id PDC) listázása/aukcióba adása TILOS (hibaüzenettel) —
        a relikvia több-lépcsős kihívással szerzett, egyedi-tulajdonú tárgy, a börze a
        szilánkoké. Kapcsoló: economy.yml `market.allow-relic-listing` (default false).
  - [ ] Aukció-indításnál unique-tételre ajánlott minimum-kikiáltás figyelmeztetés
        (NEM tiltás): economy.yml `market.relic-auction.recommended-min-bid`.
- [ ] **G16 — Nagydöntő (Tier B TOP, ÚJ):**
  - [ ] A szezon utolsó 48 órájában (`season-finale.top2-window-hours`) a liga-tábla top2
        frakciójának MINDEN pont-jóváírása ×2 (`top2-point-multiplier`, a B33-szorzó UTÁN);
        az ablak nyíltakor egyszeri „NAGYDÖNTŐ!” broadcast a párosítással; szezonváltáskor
        a flag újraáll. Kulcsok: world.yml `world-events.season-finale.top2-*`.
- [ ] **H14 — Ritka spawn-variánsok (ÚJ):**
  - [ ] Spawn-kor ~1,5% eséllyel „✦ Albínó” (fehér, GLOWING) vagy „☽ Árnyék-” (lila,
        SPEED) variáns — PDC-tag + látható névtábla (MobScalingManager.maybeMakeRareVariant).
  - [ ] Kill-bónuszok: dupla kaszt-XP (ClassXpListener), emelt lélekkő-esély (Soulstone),
        és ÖNÁLLÓ bestiárium-bejegyzés (albino_/arnyek_ prefix). Kulcsok: world.yml
        `rare-variant.*`.
- [ ] **DARK undead-népesség (ÚJ, lore-ambiencia):**
  - [ ] A DARK territóriumokban folyamatos, magas szintű undead-populáció — hatókör
        configból: `scope: capital` (csak a főváros) vagy `all` (minden DARK territórium),
        `territory-id` felülbírálással; alap: 24 fő, 30 mp-enként 4-es rajokban pótolva,
        szint 4-7 (MobScaling.forceLevel); a mobok a napon SEM égnek el
        (EventSpawnGuard.prepare) és nem zombisodnak.
  - [ ] Élettartam-korlát (600 mp, a mob saját schedulerén) + halál-könyvelés — a populáció
        sosem nő a plafon fölé; spawn a helyszín régió-schedulerén. A DARK játékosokat a
        frakció-passzíva védi. MINDEN kulcs élő: world.yml `dark-undead.*`.
        (Osztály-név generikus: DarkUndeadAmbienceManager — a lore-név csak szövegben él.)
- [ ] **Rontás-góc DARK-perem sorsolás (ÚJ — elfogadott irány):**
  - [ ] A természetes góc-nyílás `corruption.dark-bias.chance-percent` (65) eséllyel egy
        véletlen DARK territórium PEREMÉN TÚL történik (a zóna szélétől
        `min-edge-distance`..`max-edge-distance` [24..96] blokkra — a territórium
        BELSEJÉT a spawn-rules amúgy is védi); ilyenkor külön broadcast szól
        („a Kitaszítottak földjének pereméről szivárgott ki”).
  - [ ] Ha nincs DARK territórium / a sorsolás nem talál / admin `forceSpawn`: marad a
        régi horgony-játékos út a régi üzenettel. Élő kulcsok: world.yml
        `corruption.dark-bias.*`. Ellenőrzés: definiálj DARK territóriumot, állítsd a
        chance-t 100-ra, várd ki (vagy admin-indítsd) a gócot → a mag a zóna szélén túl,
        de annak közelében nyílik.
- [ ] **Aszimmetrikus szezon-liga (ÚJ — tulaj-döntés a 2+1+1 felállás miatt):**
  - [ ] Forrás-súlymátrix: minden liga-pont-jóváírás forrás-címkével megy
        (`SeasonManager.addPoints(faction, amount, source)`), és a
        `world-events.season.source-weights.<forrás>.<frakció>` súly skálázza a NYERS
        pontot (a B33/G16 idő-szorzók UTÁNA); hiányzó kulcs = 1.0, 0 = nem ér pontot.
  - [ ] Meglévő források címkézve: raid-győzelem → `raid` (NEUTRAL 0.5), világboss +
        szezonzáró boss → `world-boss` (mind 1.0).
  - [ ] ÚJ pontforrások: közösségi cél teljesítése → `community`
        (quests.yml `community-goals.season-points` 8; NEUTRAL 1.5, DARK 0.75;
        szerver-célnál minden frakció kap); rontás-tisztítás → `cleanse`
        (world.yml `corruption.season-points` 6; NEUTRAL 1.5, DARK 0.5);
        becsület-párbaj győzelem → `duel` (factions.yml `honor-duel.season-points` 2;
        DARK 1.5); sikeres kém-küldetés (lebukás nélküli lejárat) → `spy`
        (factions.yml `spy.season-points` 2; DARK 1.5, mások 0.75).
  - [ ] Ellenőrzés: NEUTRAL játékossal rontás-tisztítás → 9 pont (6×1.5); DARK
        párbaj-győzelem → 3 pont (2×1.5); raid-győzelem NEUTRAL-ként → 5 pont (10×0.5);
        súly 0-ra állítva a forrás semmit nem ír jóvá. `/events season` mutatja az állást.
- [ ] **Review-kör 2 (Tier A/B utólagos átvizsgálás) — javítás-ellenőrzések:**
  - [ ] KRITIKUS-javítás: a plugin egyáltalán ELINDUL (korábban a konstruktor-sorrend
        miatt onEnable-crash lett volna: soulforge/resource/ritual huzalozás a mezők
        felépülte ELŐTT futott); `/parbaj` győzelem után a bűnpont TÉNYLEG csökken
        (SinManager.reduceSin elírt PDC-kulcsa javítva).
  - [ ] economy.yml: a `market:` blokk EGYszer szerepel (a dupla kulcs a piac/aukció
        beállításokat némán elnyelte volna) — `/icesmp config get market.fee-percent`
        a fájlbeli értéket mutatja.
  - [ ] `/ceh kirug <ismeretlen név>` nem fagyaszt (nincs blokkoló Mojang-lookup),
        ismeretlen névre hibaüzenet. `/market <TAB>` felajánlja az `ereklye`-t.
  - [ ] Rúna-felrakásnál a rúna a kurzorról TÉNYLEG elfogy (explicit setCursor);
        erszény levegőbe-kattintva továbbra is nyílik.
  - [ ] Kilépéskor takarul a párbaj- és kém-állapot (PlayerSessionCleanup-regisztráció);
        projectile-PvP két régió közt nem olvas idegen PDC-t (Sárkánytojás-bónusz
        cache-ből, isOwnedByCurrentRegion-kapuval).
  - [ ] Bestiárium-mérföldkő hibás config-sora (nem szám) csak kimarad, nem dob;
        ritka variáns csak TÉNYLEGESEN szintezett mobra kerül; játékos-karaván
        kifizetése nem duplázódhat (tick szinkronizálva); szezon-hossz élő átírása
        NEM ismétli meg a nagydöntő-boss spawnját (szezon-azonosító = kezdő-bélyeg).
- [ ] **Teljes kódbázis-audit (430 fájl, 7 mélyvizsgálat) — 1. javítás-hullám:**
  - [ ] FORDÍTÁS-BLOKKOLÓ: 4 monk/harcos spell (Whirlwind/DeepBreath/SpinningCraneKick/
        FlyingSerpentKick) statikus helperje instance-metódust hívott — javítva.
  - [ ] Relikvia-cooldown NEM nullázódik ki-be lépéssel (relog-exploit zárva); világboss-
        jutalom csak a KÖVETETT bossért jár (crash-árva példány nem fizet duplán).
  - [ ] Pakt-oltár (E25) ÉLETRE KELT: az üres sacrifice-lista eddig némán letiltotta a
        rituálét ("ritual-missing-sacrifice") — üres lista = nincs áldozat-követelmény.
  - [ ] Karaván-kíséret (escort) spawn-guard: konvoj + hullám-mobok nem spawnolnak
        territórium/claim/WG-régió belsejébe, vízre (új `escort` mátrix-sor).
  - [ ] Vagyon-elérés az ÖSSZES valuta összegét nézi (RED/BLUE/DARK játékos is elérheti);
        király-koronázás csak aktuális frakciótagnak (átállt szavazó nem koronázható);
        claim-méret long-számítás (int-túlcsordulás plafon-kerülése zárva).
  - [ ] Folia: párbaj-elfogadás a kihívó PDC-jét a SAJÁT szálán írja; céh-taglista COW
        (CME-védelem); szerver-kihívás bossbar-frissítés synchronized; kém-álca
        levétele plugin-leálláskor (spyManager.shutdown).
  - [ ] Baráti tűz: Dühös Csirke + Árnyégés nem sebez párttagot/frakciótársat; EGYETLEN
        élőhalott-definíció (csontváz-/zombiló is — UndeadUtil a közös forrás).
  - [ ] Konjunktúra (F14) a sokk kikapcsolásakor is él; adomány-láda mentése debounce-olt;
        hangulat-esemény pénze AFK-nak nem jár; 8 halott metódus + holt ctor-paraméter törölve.
- [ ] **Gameplay-review kör (teljes diff, 4 mélyvizsgálat) — balansz-javítások:**
  - [ ] BANK-ONLY zárás: a király kassza-kivéte FIZIKAI veretben érkezik + napi keret
        (`factions.treasury.withdraw-daily-cap` 1000); a VAGYON-elérések kaszt-XP-t
        fizetnek veret helyett (a kölcsön-token exploit halott).
  - [ ] Vérdíj-pénznyomda fék: ugyanarra az áldozatra csak
        `factions.sins.bounty.per-victim-cooldown-hours` (12) óránként jár kifizetés
        (a bűn-törlés jár, a broadcast elmarad, a vadász csendes üzenetet kap).
  - [ ] Kém-küldetés: liga-pont CSAK ha az álca IDEGEN frakció territóriumában jár le
        (saját bázison AFK = semmi), és naponta max `spy.points-daily-limit` (2) siker
        pontoz. Párbaj: liga-pont csak KÜLÖNBÖZŐ frakciójú felek közt; a felkérés
        60 mp után lejár és élő felkérés nem írható felül némán.
  - [ ] Liga-hangolás: raid `season-points` 5→10; B33×G16 NEM szorzódik össze (a
        nagyobbik él, max ×2 idő-szorzó); szerver-szintű közösségi cél súlyozatlanul
        fizet (community-server forrás — a NEUTRAL 1.5 csak a saját céljaira jár).
  - [ ] Pénz-csapok sapkája: mob-pénz `mob-money-drop.daily-cap` (300/nap/játékos —
        a natural-spawn darkroom-farm fékje), horgász-lelet `fishing-windfall.daily-cap`
        (150/nap); valutaváltási díj default 3% (oda-vissza spekuláció fékje).
  - [ ] H14 ÚJRA ÉL: a maybeMakeRareVariant hívás visszakötve az applyScaling-be (a
        2. review-kör sorrend-javítása kivette — a variáns-rendszer halott kód volt);
        mob-szint kemény plafon: `mob-scaling.hard-cap-level` (15) a vérhold/zóna-bónusz
        UTÁN is fog. DARK undead-spawn mostantól spawn-rules mátrix-soron megy
        (`dark-undead`: territórium engedett, claim/WG/víz tiltott).
  - [ ] Rontás dark-bias 65%→50%; rejtvény-quest pénz-jutalmak ~40%-kal vágva (a
        fejezet-questek kaszt-XP-t kaptak — a nehezebb story ne fizessen rosszabbul);
        rejtvenyi_elso_nyom → rejtveny_elso_nyom átnevezve.
  - [ ] Raid lánc-fék: `factions.raid.cooldown-minutes` (60) két hirdetés közt.
- [ ] **Menü/config/admin integráció (teljessé tétel):**
  - [ ] Főmenü: új „Bestiárium” (GUI) és „Szakma-céh heti cél” csempe; Frakció-almenü:
        „Céh”, „Kém-álca”, „Karaván-indítás: 100” (király-parancsra fut); Körözési
        lista: „Becsület-párbaj” csempe; Lélekszilánk-almenü: „Lélek-kovács” (csak
        Nekromanta); Bank-almenü: „Ereklye-börze”.
  - [ ] Admin-menü: „Config-menü” gomb (icesmp.admin.config node-ra kapuzva),
        „Item-adás” (icesmp.admin.item), „Rontás-góc nyitása” és „Régészeti lelőhely”
        esemény-trigger; a hasAnyAdminAccess a két új node-ot is nézi.
  - [ ] `/events corruption` + `/events archeology` (rontas/regeszet alias, admin-node,
        tab-complete) — a CorruptionManager.forceSpawn eddig sehonnan nem volt hívható.
  - [ ] Config-GUI (`/icesmp config menu`): 6 ÚJ kategória — Szezon-liga (pontok,
        nagydöntő), Rontás-zóna (dark-bias-szal), DARK-népesség + ritka variánsok,
        Céhek + szakma-hét, Párbaj + kém, Börze + városi őrség — mind élő kulcsokra.
- [ ] **G6 — Becsület-párbaj (ÚJ):**
  - [ ] `/parbaj kihiv <név>` (CSAK bűnös ajánlhat) → `/parbaj elfogad|elutasit`; elfogadáskor
        3 perces ablak; a párbaj-kill NEM termel bűnt és NEM fizet vérdíjat (SinListener-kizárás
        a bounty ELŐTT); ha a bűnös nyer → -1 bűnpont (SinManager.reduceSin).
  - [ ] Heti limit (2, PDC-ben, ELFOGADÁSKOR fogy — alt-exploit fék); kilépéskor állapot-takarítás.
        Kulcsok: factions.yml `honor-duel.*`.
- [ ] **G14 — Kém-álca (ÚJ, LibsDisguises soft-depend):**
  - [ ] `/kem <célfrakció>`: 60 mp-es hamis nevű játékos-álca (SpyDisguise reflexiós híd,
        PlayerDisguise); frakciónkénti álnevek (`spy.fake-names.*`); 15 perc cooldown (PDC).
  - [ ] Korlátok: aktív raid alatt nem indítható; BÁRMILYEN PvP-találat (adott VAGY kapott)
        azonnal lebuktat (SpyRevealListener, támadó-oldalon régió-hoppal); a bűn-szabályok
        az álca alatt is élnek. LibsDisguises nélkül tiszta hibaüzenet.
- [ ] **I16 — Szakma-céh heti közös cél (ÚJ):**
  - [ ] Globális, frakció-független heti számláló szakmánként (egység = termelt szakma-XP;
        AtomicLong + concurrent mapek); `/szakmacel` mutatja az állást; perzisztens
        (profession-weekly.yml).
  - [ ] Hét fordulásakor: elért cél → broadcast + a küszöb (100) feletti hozzájárulók +300
        szakma-XP-t kapnak — online azonnal (saját régió-szálon), offline belépéskor
        (perzisztált függő jutalom). Kulcsok: professions.yml `profession-weekly.*`.
- [ ] **E25 — Pakt-oltár (Boszorkánymester, ÚJ):**
  - [ ] Új rituálé-típus: `pakt` (relics.yml `pakt_oltar` — SOUL_LANTERN mag, obszidián-alap);
        csak WARLOCK kaszt, NEM halmozható; ára 1× Első Csend Szilánkja a táskából.
  - [ ] Hatás: tartós +20% max Lélekerő (player-PDC + join-kor töltött concurrent cache —
        a ResourceManager.max() szál-biztos szorzó-lookupon át olvassa). Kulcsok: `pakt.*`.
- [ ] **E32 — Sárkánytojás-töredék (Sárkányidéző, ÚJ):**
  - [ ] Új relikvia: `sarkany_tojas` (DRAGON_EGG, ITEM_MODEL `relic_sarkany_tojas`); amíg a tulajdonosa Sárkányidéző
        (EVOKER), az Eszencia-poolja +10% (`pakt.dragon-essence-bonus-percent`) — másnak
        csak presztízs-tárgy. A relikvia-szabályok (tulajdon, rablás) változatlanok.
- [ ] **E1 — Lélek-kovács (Nekromanta, ÚJ):**
  - [ ] `/soulforge` (alias /lelekkovacs): 3 ág (Élet/Sebzés/Létszám), áganként 5 rang,
        növekvő szilánk-ár (5/8/12/18/25 — classes.yml `soulforge.rank-costs`); rangok
        player-PDC-ben.
  - [ ] Cast-kor a SummonMinionsSpell a rangokból olvas (statikus híd): minion-HP ×(1+8%/rang),
        sebzés ×(1+6%/rang), Létszám-ág extra idézés-slot ([0,0,1,1,2,3] — max +3).
- [ ] **E7 — Varázsló rúnaíró affinitás (ÚJ):**
  - [ ] A Varázsló minden rúna-hatást DUPLÁN olvas (`runes.wizard-affinity-multiplier` 2.0):
        él/bástya-százalék és láng/fagy-esély szorzódik.
  - [ ] Visszhang Rúnája (ITEM_MODEL `runa_visszhang`, Varázsló-ZÁRT recept — új `job:` recept-mező +
        JobManager-ellenőrzés a craftban): találatkor eséllyel visszhang-csapás
        (+30% bónusz-sebzés, ENCHANT-partikel). Kulcsok: crafting.yml `runes.runa_visszhang.*`.
- [ ] **B21 — Bestiárium / gyűjtő-album (ÚJ):**
  - [ ] `/bestiarium` (alias /bestiary, /lajstrom): csak olvasható áttekintő GUI — 4 kategória
        (Szörnyek/Receptek/Territóriumok/Világbossok) számlálóval; a haladás player-PDC-ben
        (CSV-halmazok), nincs külön store.
  - [ ] Hookok: mob-faj első elejtése (MONITOR), boss-archetípus (WorldBossManager.isWorldBoss),
        recept első craftja (ProfessionRecipeBookListener setter-hook), territórium első
        belépés (TerritoryListener setter-hook).
  - [ ] Mérföldkövek: world.yml `bestiary.milestones.*` ("darab:veret[:broadcast]") — a jutalom
        FIZIKAI veretben érkezik; a nagy mérföldkő broadcastol.
- [ ] **B6 — Játékos-indított karaván (ÚJ):**
  - [ ] `/faction caravan send <összeg>` (csak KIRÁLY): a kasszából rakomány indul; sorsolt
        őrzőpont a vadonban (EventSpawnGuard `player-caravan` mátrix-sor: territórium/claim/
        régió/víz tiltva), broadcast a koordinátákkal — 5 percig védhető VAGY rabolható.
  - [ ] Túléli az ablakot → a kassza a rakomány ×1,25-ét kapja (profit); ellenséges játékos
        leöli a konvojt → a rakomány a RABLÓ frakció kasszájáé; saját frakciós ölés → elvész.
  - [ ] Frakciónként cooldown (90 perc); restart/leállás közben aktív szállítmány
        visszatérítődik. Élő kulcsok: world.yml `player-caravan.*`.
- [ ] **B35 — Céhek (ÚJ):**
  - [ ] `/ceh letrehoz <név>` (alapítás 250 saját valutáért a számláról — sink); csak azonos
        frakcióból hívható tag (`/ceh meghiv` → `/ceh elfogad`); vezető-átadás kilépéskor,
        egyedül maradva feloszlik. Perzisztencia: guilds.yml (YamlStore).
  - [ ] Céh-szint: quest-teljesítésenként +10 céh-XP (`guilds.xp-per-quest`); szintlépéskor
        tag-broadcast; a taglétszám-plafon 10-ről 3 szintenként +1-gyel nő (max 15).
  - [ ] `/ceh befizet <összeg>` — a tag számlájáról a céh-kasszába (bankon belüli átvezetés);
        `/ceh info` (szint/XP/tagok/kassza), `/ceh lista` (top 10 XP szerint).
  - [ ] Függő meghívás kilépéskor takarítva (PlayerStateCleanup). Élő kulcsok: factions.yml `guilds.*`.
- [ ] **B26 — Rúna-kovácsolás (ÚJ):**
  - [ ] 6 rúna unique-materialként (ITEM_MODEL `runa_*`), Kovács/Bűvölő 26-42. szintű receptek
        (runapor + tematikus unique + Rúnakréta kellék): Él (+közelharci sebzés%), Zápor
        (+lövedék-sebzés%, a nyíl örökli az íj rúnáját), Bástya (mellvért, -kapott sebzés%),
        Láng (gyújtás-esély), Fagy (lassítás-esély), Mohóság (erszény-drop esély bónusz).
  - [ ] Felhelyezés: a KURZORON tartott rúnát kattintsd a cél-tárgyra a táskában — PDC
        `rune_effect` + lore-sor; tárgyanként EGY rúna, nem cserélhető; cél-típus a
        `runes.<id>.applies` szerint (weapon/bow/chest). Élő kulcsok: crafting.yml `runes.*`.
- [ ] **J9 — Fejezet-rendszer (szezon story-ív, ÚJ):**
  - [ ] A szezonnak perzisztens **fejezet-sorszáma** van (`season.number` a season.yml-ben,
        `SeasonManager.getSeasonNumber()`); szezonváltáskor nő + „Új fejezet nyílik” broadcast.
  - [ ] `chapter: N` mező a questeken: csak az N. fejezet alatt vehető fel — lezárt fejezetnél
        „a krónika továbblapozott”, jövőbelinél „még nem nyílt meg” üzenet; a MÁR FELVETT
        fejezet-quest kegyelemből befejezhető, de a next-lánc új tagja már nem nyílik.
  - [ ] 1. fejezet demo-lánc: „A Kapu Árnyéka” (suttogások → bizonyíték → krónikás, 3 quest,
        next-láncolva); admin-szerkesztés: `/quest admin set <id> chapter <N>`.
- [ ] **Profession-mélyítés (ÚJ):**
  - [ ] **~50 recept/szakma (össz. 407 — armorer 53, enchanter 54):** minden szakma szintlétrája az 1. szinttől az 50.-ig
        kitöltve; a magas szintű receptek kelléket + ritka unique anyagot kérnek; az 50-es
        céh-mesterművek (A Mélység Szíve / A Világfa Magja / Az Erdő Szíve / A Céhmester
        Üllője / A Bölcsek Köve / A Végtelen Kódex / A Bokic Áldása / A Kapu Lakomája)
        Első Csend Szilánkját is.
  - [ ] **Craft-XP (skill-up):** recept-könyvi craft szakma-XP-t ad (`professions.xp.recipe-craft-*`;
        base 8 + szint×2); a szinted felett 10+ szinttel járó recept fél, 20+ felett 0 XP
        („szürke recept”). Szintlépéskor üzenet + hang, fokozatváltásnál külön üzenet.
  - [ ] **Fokozatok:** Inas (1–9) → Segéd → Legény → Mester → Nagymester → Legendás Mester (50);
        a szakma-GUI a szint mellett a fokozatot is mutatja.
  - [ ] **ITEM_MODEL manifest:** minden nevesített (lore-os) recept-eredmény item-modelt visel; teljes lista: `docs/RESOURCE_PACK_CMD.md` (resource pack készítéshez). Új custom item = új `icesmp:<modell-id>` + új manifest sor.
- [ ] **`/iceitem` admin item-adó (ÚJ):** `icesmp.admin.item` joggal
      `/iceitem <unique|recept|relikvia|tervrajz|erszeny> <id> [darab] [játékos]` — tab-complete
      mind az öt típus id-listájával. A `recept` út a teljes stamp-lánccal ad (signature-PDC,
      custom enchant, „Készítette", affix-roll — bitre azonos a craftolttal); a `relikvia`
      force-móddal ír tulajdont; másik játékosnak adva a cél régió-szálán landol a tárgy.

---

### 5. ⚠️ Folia-specifikus tesztpontok (kiemelt)

A plugin Folia-régió-szálkezelést használ. A leggyakoribb hiba a **cross-region/cross-entity**
hozzáférés — ezt a konzol `IllegalStateException` / „Thread ... cannot access region ..." üzenettel
jelzi. Célzottan próbáld ki ezeket (a hibajavítások ezeket fedik):

- [ ] **Kill-jutalmak régióhatáron:** ölj mobot úgy, hogy te és a mob épp más-más régió közelében
      vagytok → kaszt-XP, pet-XP, lélekszilánk, quest- és napi-haladás mind hibamentes.
- [ ] **Piac más régióban lévő eladóval:** vásárolj, miközben az eladó messze/másik régióban van.
- [ ] **Ostromágyú távoli célra:** lőj egy messzi pontra (másik régió) raid alatt.
- [ ] **Admin parancs távoli célpontra:** `/class addxp`, `/currency set`, `/faction set`,
      `/quest complete` egy **másik régióban tartózkodó** játékosra.
- [ ] **Mételytépő PvP-ölés** másik régióban lévő áldozatra/gyilkosra.
- [ ] **Totem/idézés/forma** régióhatár közelében.
- [ ] **Általános:** futtass egy hosszabb session-t több játékossal a világ különböző pontjain, és
      figyeld a konzolt bármilyen `region`/`scheduler`/`IllegalStateException` stacktrace-re.

> Ha bármelyiknél stacktrace jön a konzolon, az **bug** — jegyezd fel a teljes stacket.

---

### 6. Hibabejelentő sablon

```
Cím: <rövid leírás>
Rendszer: <pl. Erő-csík / Frakció passzív / Világboss / Folia-cross-region>
Lépések:
  1. ...
  2. ...
Elvárt eredmény: ...
Tényleges eredmény: ...
Konzol-log (ha van): <teljes stacktrace>
Folia-gyanú? (régióhatár/cross-entity volt?): igen / nem
Reprodukálható?: mindig / néha / egyszer
Build/verzió: <jar verzió>
```

---

### 7. Prioritási javaslat a teszteléshez

1. **Erő-csík + hibrid költség** (4.3) — ez a legfrissebb rendszer, itt a legvalószínűbb a finomhangolás.
2. **Folia cross-region pontok** (5.) — a stabilitás kulcsa; ezek frissen javítva, célzott teszt kell.
3. **Frakció-passzívok** (4.1) — szintén frissen módosítva (Kék fulladás, Semleges zuhanás, invis kivéve).
4. Utána a többi rendszer (kasztok, specek, gazdaság, események) végig a 4. szekció szerint.

Jó tesztelést! ❄️

### Teszter-visszajelzés kör (2026-07-20)
- [ ] **Konvoj-halál átláthatóság:** a kíséret-láma CSAK szörny-sebzéstől halhat
      (támadás/lövedék/robbanás) — esés/fulladás/kaktusz/tűz nem morzsolja némán.
      Ellenőrzés: told vízbe/magasból — nem sérül; szörny üti — sérül, a bossbar
      HP-száma frissül.
- [ ] **Kíséret lazítva:** wave-interval 45→60 mp, wave-size 4→3 (world.yml escort).
- [ ] **Horgony-rotáció:** world boss és escort nem spawnol kétszer egymás után
      ugyanarra a játékosra (ha 2+ online). Teszt: két játékossal két egymás utáni
      esemény → különböző horgony.
- [ ] **Piglin-zombisodás:** az EventSpawnGuard.prepare a bossra ÉS a SUMMON-addokra
      is fut (setImmuneToZombification) — a teszter által látott zombisodás a korábbi
      buildben volt; friss builden ellenőrizendő, hogy a Pokoli Hadúr + kísérete a
      felvilágon sem alakul át.
- [ ] **HP-sáv:** a világboss-sáv sebzésre azonnal (WorldBossListener) és 2 mp-enként
      (fázis-tick) is frissül — friss builden ellenőrizendő; az ESCORT sávja
      TÁVOLSÁG-alapú (a % az út megtett része, a HP csak szöveg) — ha ez zavaró,
      külön HP-sáv tétel nyitható.
- [ ] Backlog (ideas N24-N27): claim-segéd varázsló, hely-kötött eventek + kultista
      esemény, ismételt-spawn kegyelem (gyengébb boss VAGY ideiglenes buff),
      kereskedő-karaván szabad spawn.

### Kijelölő-pálcák (N24 — teszter-kérés)
- [ ] **Birtokmérő pálca** (`/claim wand`, ITEM_MODEL `selection_wand`, STICK): bal katt blokkra = 1. sarok,
      jobb katt = 2. sarok (méret+ár-előnézet chatben, átfedés-figyelmeztetéssel),
      SNEAK+jobb = foglalás (a /claim area teljes ár/limit-logikáján át). A kattintott
      BLOKK koordinátája megy a kijelölésbe (nem a játékos helye). A pálca-katt nem
      üt/nem nyit semmit (cancel).
- [ ] **Határkijelölő pálca** (`/territory wand`, ITEM_MODEL `selection_wand_territory`, BLAZE_ROD, admin-node):
      bal katt = poligon-pont a kattintott blokkon, jobb katt = utolsó pont visszavonása,
      SNEAK+jobb = határ-előnézet (/territory show); létrehozás továbbra is
      /territory create-tel. Jog nélkül a pálca nem csinál semmit (hibaüzenet).
- [ ] Tab-complete: `claim wand`, `territory wand`; ITEM_MODEL manifest felvéve.

### N25/N27 — Esemény-spawnpontok és hely-horgony (teszter-kérés)
- [ ] `/events spawnpoint add <world-boss|escort|caravan|any> [id]` az admin helyén
      pontot vesz fel (restart-álló: event-spawnpoints.yml); `remove <id>` / `list`.
- [ ] `world-events.anchors.<esemény>.mode`: player (default, régi viselkedés) |
      points (admin-pontok közül sorsol) | random (véletlen koordináta a fő világ
      spawnja körül, `random-radius` 1500) | mixed (pont, ha van; különben random).
      Élő kulcsok — /icesmp reload nélkül is állíthatók.
- [ ] Világboss: points-módban az admin-aréna környékére (±8 blokk) spawnol, a
      spawn-rules mátrix ott is él; escort: a konvoj-útvonal a ponttól indul;
      kereskedő-karaván: a configolt megállók (stops) elsőbbsége marad, utána
      jön a horgony-mód, végül a játékos-fallback.
- [ ] N26 kegyelem-mechanika TULAJ-DÖNTÉSSEL ELVETVE (se gyengébb boss, se buff) —
      ismétlődés ellen a horgony-rotáció + hely-horgony véd.

### N25b — Kultista esemény (ÚJ, teszter-ötlet + lore)
- [ ] Három változat súlyozott sorsolással (cultists.variant-weights): TÁMADÁS (4 fős
      portya — akolitus + pengék; az utolsó leölése broadcast-tal zár), RÍTUS (3 hív
      kört áll, SOUL-particle + kántálás-hang; rite-minutes [6] alatt MINDET le kell
      ölni → rite-loot hullik; különben a rítus beteljesül és rite-corruption-chance
      [60%] eséllyel RONTÁS-GÓC nyílik a helyszínen!), HÍRVIVŐ (magányos csuklyás a
      legközelebbi DARK territórium felé lépked — leölve zár, elérve/lejárva köddé
      válik "az üzenete célba ért" broadcast-tal).
- [ ] Spawn: spawn-rules.cultists mátrix-sor + N25 horgony-mód (anchors.cultists);
      minden mob prepare-elt (nem ég, nem zombisodik), szintezett (mob-level 5),
      PDC-jelölt; despawn plugin-leálláskor. Admin: /events cultists (+ tab).
- [ ] Ellenőrzés: rítus kivárva → góc nyílik a rítus helyén; rítus megszakítva →
      loot a kör közepén; hírvivő követése elvezet a DARK föld felé.

### Kultista × Suttogó crossover (tulaj-kérés)
- [ ] BETELJESÜLT kultista esemény (rítus lefutott VAGY a hírvivő célba ért) →
      minden online, felesküdött Suttogó gyanúja −15 (cultists.whisper-suspicion-relief,
      PRIVÁT üzenettel — nem leplez le), és a DARK frakció +3 liga-pont ("cult" forrás).
- [ ] A hírvivő tétje így: leölve loot + a hálózat vesztesége; célba érve a Suttogók
      álcája mélyül és a DARK pontot kap — a védelme valódi cél a rejtett/DARK oldalnak.
- [ ] Nem farmolható: az esemény csak természetes sorsolással (vagy admin-triggerrel)
      indul, játékos nem tudja kiváltani. Ellenőrzés: Suttogó-státuszú játékossal
      várd ki egy rítus beteljesülését → gyanú-érték csökken (/suttogas), liga-pont nő.

### P-audit javítások, 1. kör (gameplay-audit — [GYORS] tételek)
- [ ] Szerver-kihívás népesség-skálázása: `server-challenge.per-player-targets` (true)
      esetén a cél = `targets-per-player.<típus>` (slay 40 / mine 60 / harvest 80)
      × online létszám — 3 fősen és 30 fősen is elérhető. `false` → régi fix cél.
- [ ] Közösségi quest-célok újraskálázva (quests.yml community: vas 600, vadászat 1500,
      hal 500, szén 1000, sötét-zombi 800, raid-győzelem 4, világboss 3) és a heti
      szakma-célok is (professions.yml: armorer 2500, cook 1500, enchanter 1500,
      alchemist 1800) — kis szerveren is teljesíthetők.
- [ ] Onboarding-lánc folytatása: az `onboarding_gather` után új `onboarding_utmutatas`
      quest (Hírnök NPC — elirányít a /class, /profession join, /quest list felé).
- [ ] D1 ünnep-hook ÉLESÍTVE (halott kód volt): Rém-éj alatt a vérhold esélye ×2,
      az invázióé ×1.5 (`seasonal-events.remej.blood-moon-chance-mult` /
      `invasion-chance-mult` — bármely ünnepre megadható, élő kulcs).
- [ ] Világboss: a leütő SZEMÉLYES bónusz-loot-ot kap a kasszajutalom mellett
      (`world-events.world-boss.killer-loot`, 2 guríts — tárgy, sosem pénz).
- [ ] Rontás-góc: loot-tábla feljavítva (DIAMOND 2:5 + ENCHANTED_GOLDEN_APPLE) — a
      megtisztítás egyénileg is megéri; dark-undead ambiencia scope: "all" (minden
      DARK territórium, nem csak a főváros).

### P-audit javítások, 2. kör (gameplay-audit — [GYORS] tételek)
- [ ] Szezon-bajnok offline-kifizetés: a záráskor offline bajnok-tagok tárgy-jutalma
      függőbe kerül (season.yml: pending-champion-spoils, restart-álló) és a
      következő belépéskor jár, külön üzenettel. A buff/tűzijáték csak az élő
      ünneplés pillanatáé (online tagok).
- [ ] DARK-belépés kétlépcsős: az első `/faction join dark` figyelmeztet (örök paktum,
      nincs visszaút), és csak `factions.dark.join-confirm-seconds` (60) másodpercen
      belül megismételve lép életbe (0 = kikapcsolva). Ellenőrzés: első hívás nem
      vált frakciót, második (ablakon belül) igen.
- [ ] Kaszt-választó szerep-címkék: a /profile → Kaszt menü minden kaszt-ikonja
      "Szerep: ..." sort mutat (messages.job-gui-role-<id> kulccsal felülírható) —
      a visszafordíthatatlan döntés előtt látszik a tank/gyógyító/sebző irány.
- [ ] Napi NPC-quest rotáció ÉLESÍTVE: hero_trial, borostyan_kutatas, uti_kenyer,
      viharkvarc_fejto, korrupt_irtas a "napi-npc" rotation-groupban, naponta 3
      elérhető (nap-determinisztikus). A frakció-kötött anyag-questek nem rotálnak.
- [ ] Karaván unique-slot-garancia: a rotált készletben mindig van legalább egy
      `unique:` anyag (caravan.rotation.guarantee-unique) — a ritka szakma-alapanyag
      forrás sosem tűnik el egy-egy érkezésre.
- [ ] factions.yml komment-ellentmondás javítva: a Sötétbe lépés a KÓD szerint mindig
      ingyenes (sinner-feltétel + örök paktum az ára) — a config-komment már ezt mondja.

### P-audit javítások, 3. kör — esemény-orchestráció
- [ ] Egyszerre csak EGY nagy PvE-esemény indul természetes sorsolásból: a
      world-events.orchestration.major-events listán szereplő események
      (world-boss, invasion, wild-hunt, escort, cultists) sorsolása kimarad,
      amíg egy másik listás esemény aktív. Élő kulcsok; kapcsoló a config-menüben.
- [ ] Az admin /events parancsok (worldboss, invasion, wildhunt, escort, cultists)
      a kaput MEGKERÜLIK — kézi indítás mindig lehetséges.
- [ ] Ellenőrzés: futó világboss mellett az invázió/kíséret/kultista sorsolás nem
      indít újat (az intervallum ettől még pörög); a boss halála után a következő
      sorsolás már indíthat.

### P-audit javítások, 4. kör — hadi-ablak + Suttogó-erősítés + broadcast-diéta
- [ ] **Hadi-ablak:** menetrend szerint (factions.war-window.schedule, alap: szombat-
      vasárnap 18:00-20:00, szerver-helyi idő) vagy admin-nyitásra (/faction war
      start [perc], jog: icesmp.admin.war) a RED↔BLUE ölés NEM bűn és NEM
      vérdíj-eset — liga-pontot ér ("war" forrás, red/blue 1.0 súllyal).
- [ ] Farm-fék: ölőnként napi 5 pont plafon (daily-point-cap) + ugyanarra az
      áldozatra 30 perc cooldown (per-victim-cooldown-minutes) — a plafonon túli
      hadi-ölés bűn-mentes, de pontot nem ér (külön üzenet). Nyitás/zárás broadcast;
      státusz: /faction war (a következő nyitásig hátralévő idővel).
- [ ] Ellenőrzés: ablakon KÍVÜL a RED↔BLUE ölés +1 bűn (mint eddig); NEUTRAL áldozat
      az ablak alatt is a normál bűn-úton megy (DARK áldozat ölése SOSEM bűn — lásd
      lent); raid/párbaj elsőbbsége megmarad. Config-menü: új "Hadi-ablak" kategória
      (a gyökér-rács 36 slotos lett).
- [ ] **DARK-áldozat kivétel EXPLICIT (tulaj-szabály):** Kitaszított ölése sosem bűn
      (factions.sins.dark-victim-exempt, action-bar jelzéssel). Korábban ezt a
      vérdíj-ág fedte implicit módon, de a vérdíj-kifizetés nullázza a számlálót —
      a másodszor megölt Kitaszítottért bűn járt volna (rés bezárva). A 3+ bűnű
      DARK-áldozatért a vérdíj továbbra is jár.
- [ ] **Suttogó-erősítés:** beteljesült kultista eseménykor a felesküdöttek a
      gyanú-csillapítás MELLÉ privát tárgy-részesedést kapnak (cultists.
      whisper-loot-rolls [1] guríts a rite-loot táblából, "a hálózat osztozik"
      üzenettel); éjjel (13000-23000 tick) az élőhalottak a Suttogót sem támadják
      (factions.whisper.night-undead-truce) — árulkodó jel a szemtanúnak!
- [ ] **Broadcast-diéta:** a régészet-lelőhely (spawn + lejárat, archeology.
      announce-radius 160), a hullócsillag és az állat-csorda (ambient-events.
      local-announce-radius 192) csak a környéken állóknak szól (LocalAnnounce,
      Folia-safe távolság-ellenőrzés a címzett saját szálán). A nagy események
      (vérhold, boss, invázió, karaván, kihívás…) globális hírek maradnak.

### Tartalom-kör 1: mester-láncok mind a 13 kasztnak + fejezet 2-3 + capstone + kazamata-starter
- [ ] **9 új mentor+mester lánc** (druida, paplovag, halállovag, sámán, szerzetes, pap,
      boszorkánymester, démonvadász, sárkányidéző): kezdő próba → mentor (TALK_TO_NPC,
      100 XP) → mester-próba (kaszt-ízű feladat, 400 XP). Új mester-NPC-k: druida_mester,
      paplovag_mester, halallovag_mester, saman_mester, szerzetes_mester, pap_mester,
      pakt_mester, demonvadasz_mester, sarkany_mester (kihelyezés:
      BUILDER_GUIDE).
- [ ] **Parkour kivéve a kötelező útból (tulaj-döntés):** a 4 régi mester-próba is
      kaszt-ízű feladat lett (harcos: 20 Lvl2+ mob; íjász: 12 csontváz; varázsló:
      3 bűvölés; orgyilkos: 15 Lvl2+ mob) — a parkour opcionális szabadidős tartalom
      (acrobat_challenge megmaradt). Egyetlen pálya megépítése sem kötelező többé.
- [ ] **2. fejezet (2. szezon, 5 quest):** repedesek (bióm) → szilankok (16 ametiszt) →
      ujjaepites (24 vas-olvasztás) → orzok (15 Lvl4+ mob) → pecset (hírnök, záró-jutalom).
- [ ] **3. fejezet (3. szezon, 5 quest):** sohajok (20 zombi) → lelekfeny (8 lélekfáklya
      craft) → visszhang (2 echo shard) → ostrom (20 Lvl5+ mob) → harmadik_mondat
      (hírnök; ritka láda-kulcs jutalom).
- [ ] **Level-50 capstone:** ver_emlekezete (requires-level 50; 30 Lvl6+ elit mob) →
      kaszt_orokseg (hírnök; NETHERITE_INGOT + ENCHANTED_GOLDEN_APPLE + 2 ritka kulcs) —
      a 46-50-es sávnak van célja.
- [ ] **Kazamata-starter:** 2 kulcs-recept (bűvölő 30/40: A Mélység Kulcsa
      [dungeonkulcs_melyseg, ITEM_MODEL `melyseg_kulcsa`], A Csontkripta Kulcsa [dungeonkulcs_csontkripta, ITEM_MODEL `csontkripta_kulcsa`] — manifestbe felvéve); új player-guide oldal (16-kazamatak.md).
      A zónákat az építészek hozzák létre (melyseg, csontkripta id-vel).
- [ ] **Suttogó sötét-anyag:** a rítus-loot (és így a Suttogó-részesedés) mostantól
      árnyékport is adhat (unique:arnyekpor:2) — a guide ígérte sötét-mágiájú anyag él.

### Tartalom-kör 2: szezon-közép + fejezet1-bővítés + rejtvények + mellék-questek + sötét penge
- [ ] **Szezon-közepi ablak (ÚJ mechanizmus):** min-season-day / max-season-day quest-kulcs
      (QuestManager-kapu, admin-szerkeszthető, saját elutasítás-üzenetekkel) — a
      "A Fa üzenete" 3 questes lánc CSAK a szezon 20-40. napján vehető fel (ültetés →
      liget-védelem → vének, köznapi kulcs jutalommal). Ellenőrzés: a 20. nap előtt
      "még nem jött el az ideje", a 40. nap után "az ablak bezárult"; felvett quest az
      ablak zárta után is befejezhető.
- [ ] **1. fejezet 3 → 5 quest:** suttogasok → bizonyitek → ÚJ orjarat (12 Lvl2+ mob) →
      ÚJ jelentes (6 papír craft) → kronikas (a next-lánc átfűzve).
- [ ] **+6 rejtvény (16 → 22):** edes_ho (cukor), siro_ko (síró obszidián), lampas_nep
      (tengeri lámpás), kilenc_elet (macska-szelídítés), fold_vere (lávás vödör),
      hangtalan_dal (echo-szilánk) — a cél egyikben sem derül ki a leírásból.
- [ ] **4 mellék-quest a napi-npc rotációban** (pool: 5 → 9, naponta 3): kovács
      acél-rendelés (16 vas-olvasztás) + szén-szállítmány (32 szén deliver), vének
      gyógyfű-szüret (24 fű/virág) + méz-áldozat (3 mézesüveg deliver) — mind
      giver-npc-s, dialógussal, napi cooldownnal.
- [ ] **Az Éjszaka Pengéje** (sötét szignatúra-recept, bűvölő 35): netherit kard +
      4 árnyékpor + 2 echo-szilánk + 8 obszidián → crafted-affixes penge (ITEM_MODEL `ejszaka_pengeje`,
      regiszterben) — az árnyékpor sötét útról jön (rítus-loot / Suttogó-részesedés).

### Építész-kör: valós térkép + komp + portál-szabály (tulaj-döntések)
- [ ] **Komp (/komp, alias /ferry):** ferry.routes.<id> (a/b végpont "world,x,y,z",
      fee) — a játékos a KÖZELEBBI kikötőtől (max-board-distance 24) a túlpartra
      teleportál; viteldíj a banki egyenlegből ég el (money sink); 10 mp cooldown;
      révész-NPC kötés: /npcbind <npc> command "komp <útvonal>". Útvonal nélkül lista.
      Ellenőrzés: kikötőtől távol "menj közelebb"; kevés bank-egyenleggel elutasít.
- [ ] **Nether-portál világszabály:** új portál gyújtása MINDENHOL tilos
      (nether-portal.allow-creation: false, élő kulcs + config-menü kapcsoló) —
      action-bar üzenettel; a zóna-bypass jogú admin gyújthat (a Kárhozat Kapuja
      felélesztése). A NETHER_PAIR (túloldali pár) szabad → a Kapu működik.
      A Netherbe az EGYETLEN út a PvP-senkiföldjén át vezet.
- [ ] **BUILDER_GUIDE a valós térképre:** T=Pyralingrad DNy (~-10k,+10k),
      K=Glatziendorf DK (~+5.5k,+12.5k), S=új Caldestera ÉK (~+5k,+2k, óceánnal),
      spawn=Ó-Caldestera a Fa tövében (1/b szakasz); Pyralingrad-anyagok javítva
      (mangrove vérfa + akácia + kalcit-diorit-nyír); hazatérés-kő opcionális;
      kazamata-terv 10+; mob-szintezés NYITOTT tulaj-döntésként jelölve.
- [ ] **Lore-kánon bővítés:** a Menedék kettészakadása (Ó-Caldestera a Fa hű
      követőié, az új Caldestera a csalódottaké) + a pyralingradi vérfa a kódexben;
      mechanika-kötések a LORE_REFERENCE-ben.

### Névváltás: Thanaopolis (tulaj-döntés)
- [ ] A DARK főváros mai neve **Thanaopolis** (a Holtak Városa); a **Mortengrad** a
      bukás előtti történelmi név (a kódexben így él tovább). Minden jelen-idejű
      szöveg (guide-ok, questek, kandalló-mese, /lore, építész-útmutató) Thanaopolist
      mond; a régi receptek (Mortengradi Hamukenyér / Árnygomba) nevei és id-i
      VÁLTOZATLANOK — a lore szerint az ősi név a receptekben maradt fenn.
- [ ] /lore: a "thanaopolis" alias is a Kitaszítottak-oldalt nyitja (a "mortengrad"
      legacy-aliasként megmaradt). Zóna-id javaslat az építészeknek: thanaopolis.

### Névadás: Radicora (a spawn-település lore-neve)
- [ ] A Fa tövében álló régi főváros kanonikus neve **Radicora** („a gyökerek
      városa" — radix + a Ryanora/Caldestera névcsalád -ora végződése); a nép
      ajkán Ó-Caldestera. Zóna-id javaslat: radicora. A korábbi PLAYTEST-sorok
      "Ó-Caldestera" említései erre a városra értendők.

### Névadás: Olethropyla (a Kárhozat Kapuja ősi neve)
- [ ] A Kapu ősi/krónikás-neve **Olethropyla** (görög: olethros "pusztulás" + pylé
      "kapu" — Thermopülai-áthallással); a játékos-szövegek a népi "Kárhozat Kapuja"
      nevet használják továbbra is. Zóna-id KÖTÖTT: karhozat-kapuja (quest hivatkozik rá).

### 50-60 fős kör: zóna-rámpa + personal-loot + Suttogó-kedvezmény + tartalom-hullám 3
- [ ] **Zóna-rámpás mob-szint (tulaj-kérés):** a biztonságos territórium-zónák
      (faction/protected/capital — doom-gate/dungeon NEM) peremétől kifelé a szint
      0-ról nő (mob-scaling.zone-ramp.blocks-per-level [250] blokkonként +1), amíg el
      nem éri a spawn-táv szerinti normál szintet. Zóna belsejében 0. Élő kulcsok +
      config-menü. Ellenőrzés: a 12k-s főváros fala mellett Lvl 0-1, ~2500 blokkra Lvl 10.
- [ ] **Personal-loot kiterjesztés (tulaj-jóváhagyás):**
      • Kincs: az első megtaláló teljes zsákmánya után a láda runner-up-seconds [45] mp-ig
        nyitva — fejenként EGYSZER, fél-értékű gurítás, max-claimants [12] plafon.
      • Vad Hajsza: minden sebző résztvevő fél-értékű saját gurítást kap (a leütő és
        partyja kimarad a 2. körből — ők a teljeset vitték).
      • Rontás: aki 3+ korrupt fajzatot irtott, a tisztításkor fél-értékű privát gurítást kap.
- [ ] **Suttogó feketepiac-kedvezmény:** a felesküdöttek a feketepiacon CSENDBEN 25%
      kedvezményt kapnak (blackmarket-discount-percent — a kijelzett ár marad, semmi
      látható nem leplez le). Config-menü kulcs a Suttogók kategóriában.
- [ ] **Közösségi célok 50-60 főre:** vas 1500, szerver-vadászat 4000, hal 1200,
      érc 2500, sötét-zombi 1500, raid 6, világboss 5.
- [ ] **Tartalom-hullám 3 (123 → 136 quest):** +4 rejtvény (26 összesen); révész-lánc
      (ÚJ revesz NPC a kikötőben: ismerkedés → 12 hal → 24 deszka); 4 frakció-heti
      (kohók/tisztogatás/vásár/lélek-aratás, 168h); +2 napi a rotációban (hírvitel,
      fegyvermustra — pool: 11, naponta 3).
- [ ] **Ingame lore-bővítés:** +8 tábortűz-mese és +6 Idegen-sor az új nevekkel
      (Radicora, vérfa, Olethropyla, révész, Thanaopolis); /lore aliasok: radicora,
      olethropyla. TULAJ-DÖNTÉS RÖGZÍTVE: kaszt-váltás CSAK adminnal (self-service nem lesz).

### Tanács + DARK mob-rules + invázió-élettartam (tulaj-jóváhagyások)
- [ ] **Vének Tanácsa (NEUTRAL):** /tanacs szavaz <játékos> (heti 1 szavazat, csak
      Menedék-polgárra, átszavazható; hét-fordulón nullázódik + broadcast); a tanács =
      a hét élő top-3 szavazottja (factions.council.seats). Jogok: kassza-kivét
      (tanácsi napi keret: 400), karaván-indítás, /tanacs vasarhet (Creutzér piaci
      díj-kedvezmény ablak, market-week-minutes [60] — a meglévő konjunktúra-mechanikán).
      Raidet a tanács NEM hirdethet. Perzisztens: council.yml (csak a futó hét szavazatai).
      Config-menü kulcsok az Adó-kategóriában.
- [ ] **DARK mob-rules élesítve (1-es opció):** a Kitaszítottak földjén minden spawnoló
      mob +2 szint (a zóna-rámpázott alapra) és az élőhalott nappal sem ég — Thanaopolis
      környéke nem szelídített vidék. (A 4-7-es élőhalott-népesség ettől független.)
- [ ] **Invázió-mob élettartam (teszter-panasz: kóbor pókok a városban):** a le nem ölt
      horda-mob mob-lifespan-seconds [600] után köddé válik (saját entity-schedulerén,
      POOF-effekttel) — a nappal megszelídült pók nem vándorol be a semlegesbe.

### Frakció-váltás szezon-szabályok + tartalom-hullám 4 (tulaj-kérés, 2026-07-21)
- [ ] **Szezon-plafon:** ugyanabban a szezonban a 3. frakció-váltási kísérlet elutasul
      (factions.switch.max-per-season [2]); az első, ingyenes választás NEM számít bele;
      a Semlegesből ingyen váltás és a DARK-ba lépés VISZONT igen (PDC-számláló,
      szezon-bélyeggel — új szezonban nullázódik).
- [ ] **Hajrá-zár:** a szezon utolsó 7 napjában (factions.switch.lockout-final-days)
      SEMMILYEN váltás nem megy: fizetős, Semlegesből ingyenes, DARK-belépés, /faction
      leave — mind elutasul a hajrá-üzenettel. A bűn-száműzetés (kényszer-út) továbbra
      is működik.
- [ ] **Napi pool 15-re nőtt:** napi_ospatak (horgász), napi_erclelet (érc),
      napi_csontszuret (csontváz), napi_fuvesasszony (virág) — a rotációban felbukkannak.
- [ ] **4 új rejtvény (30 össz):** soharcos (állvány-craft), feher_csend (havas síkság),
      melysegek_szava (5 hal), hamu_kenyere (12 sültkrumpli) — a leírásból kitalálható,
      teljesítéskor jár a jutalom.
- [ ] **Frakció-heti 2. kör:** red_heti_hatartisztitas (45 kill), blue_heti_jegszuret
      (96 jég), neutral_heti_vasarjaras (10 villager-trade), dark_heti_csonttized
      (60 zombi) — csak a saját frakció veheti fel, 168h cooldown.
- [ ] **Gyökerek-lánc (3):** mangrove-gyökér → hajtás-ültetés → Radicora-látogatás
      (VISIT_TERRITORY "radicora" — a zóna kijelölése UTÁN tesztelhető végig).
- [ ] **Hamu és zúzmara lánc (3):** 25 kill → pajzs-craft → 3 bűvölés; a záró ritka
      kulcsot ad.
- [ ] **Beszállító-hetik (4):** fa/kő/étel/bőr leadás a vandor_kereskedo NPC-nek
      (DELIVER_ITEMS — az NPC átveszi a tárgyakat).
- [ ] **Nagy heti kihívások:** heti_nagyvadaszat (120 kill → ritka kulcs),
      heti_nagyhalaszat (40 hal → 2 köznapi kulcs).

### Fable-javítási kör (P2-leletek első hulláma, 2026-07-22)
- [ ] **Reload-race zárva:** /icesmp reload közbeni craftolás és relikvia-használat nem
      dob hibát (RelicRegistry synchronized, CraftingRestrictionManager COW-lista);
      a reload után a relic/mob-scaling/craft-restrikció configok AZONNAL élnek
      (reload-hook, restart nélkül).
- [ ] **Kultista rítus:** az utolsó hívő levágása a határidő pillanatában sem adhat
      dupla jutalmat/kettős broadcastot (claimClose csere).
- [ ] **Load/save kaszkád:** egy kézzel elrontott YAML-store mellett a többi manager
      betöltődik (severe log a hibásról), leállásnál a többi mentése lefut.
- [ ] **Autosave:** settings.autosave-minutes [10] percenként async mentés — kill -9
      után legfeljebb ennyi percnyi adat hiányzik (pl. friss stats/ranglista).
- [ ] **Raid restartnál:** folyamatban lévő ostrom alatt leállítva a szerver broadcast
      jelzi az eredmény nélküli zárást (nem némán tűnik el).
- [ ] **Párbaj-invariáns:** egy kihívó második függő kihívása "duel-pending" hibát ad;
      elfogadás csak akkor megy át, ha egyik fél sem áll már aktív párbajban.
- [ ] **Szezonális quest-id:** world-events.season.length-days átírása a futó szezonban
      NEM nyitja újra a már teljesített seasonal questeket.
- [ ] **Spell-erő plafon:** spells.total-power-cap [1.75] — max mastery + max szint +
      lánc-finisher együtt sem üt nagyobbat, mint alap-sebzés × 1.75 (pl. obliterate
      max ~14, deathfrost-kombó ~22.5 össz a korábbi 36 helyett).
- [ ] **Fővárosi raid él:** meghirdetett raid alatt a célzóna-fővárosban a REGISZTRÁLT
      harcosok tudnak PvP-zni (kill-pont születik), nem-résztvevőt továbbra is véd a
      zóna máshol; a raid végén a tiltás visszaáll.

### "A világ visszagyógyul" — robbanás-regen + törmelék (tulaj-kérés, 2026-07-22)
- [ ] **Robbanás védett zónában/claimben:** a creeper/TNT LÁTVÁNYOSAN robban — a blokkok
      törmelékként repülnek ki a középpontból (pattognak, csúsznak), landolva/pár mp után
      porfelhővel eltűnnek; SEMMI nem droppol; a kráter valódi.
- [ ] **Visszaépülés:** territory.protection.regen.delay-seconds [180] után a blokkok
      PONTOSAN az eredeti állapotukba épülnek vissza, alulról felfelé (menetenként max
      40 blokk); a közben odaépített idegen blokk drop nélkül tűnik el.
- [ ] **Láda-teszt:** robbanás láda/kemence mellett — a tile-entity blokk NEM robban ki,
      tartalma érintetlen.
- [ ] **Restart-teszt:** robbanás után, a visszaépülés ELŐTT restart — a lyuk a restart
      után is visszaépül (block-regen.yml perzisztencia).
- [ ] **Kikapcsolva** (regen.enabled: false): a korábbi teljes robbanás-tiltás él.

### Ostrom-rombolás + teljes rombolás-lefedettség (tulaj-kérés, 2026-07-22)
- [ ] **Ostrom-bontás:** élő raid alatt a CÉLZÓNÁBAN a regisztrált harcos kézzel bont
      falat — nincs drop/XP, a blokk siege-delay-seconds [300] után visszaépül;
      nem-résztvevő és zónán kívüli harcos továbbra sem bonthat.
- [ ] **Ostromon kívüli bontás** (player-break.always-enabled, ALAPBÓL KI): bekapcsolva
      bárki bonthat védett zónában (always-delay-seconds [120] regen); kikapcsolva a
      régi tiltás él. Láda/kemence SOSEM bontható így.
- [ ] **Wither-teszt:** Wither robbanás + test-bontás védett zónában/claimben —
      törmelék repül, drop nincs, minden visszaépül; a Wither többé NEM rombol véglegesen
      (a territory-oldalon ez rés volt: eddig semmi nem fogta).
- [ ] **Ravager/enderman claimben:** blokk-evés/rombolás → regen-út vagy tiltás,
      maradandó kár nincs.
- [ ] **Tempó-hangolás:** restore-interval-ticks [10] + blocks-per-pass [3] — a fal
      láthatóan, fokozatosan épül vissza (kb. 6 blokk/mp); a kulcsok átírásával a
      sebesség változik (interval-hoz restart kell).

### Regen-finomítások: gyógyulás-effekt + törmelék-% (tulaj-kérés, 2026-07-22)
- [ ] **Gyógyulás-effekt:** visszaépüléskor blokkonként az anyag saját lerakás-hangja
      szól + porfelhő (restore-effects-enabled [true]) — kikapcsolva néma.
- [ ] **Törmelék-%:** debris-percent [100] csökkentésével (pl. 40) a kirobbant blokkoknak
      csak ennyi %-a repül ki törmelékként, a többi csendben tűnik el.
- [ ] **Metadata-teszt:** lépcsők/félblokkok/forgatott blokkok orientációja a
      visszaépülés után PONTOSAN az eredeti; tábla/zászló/láda (tile-entity) eleve nem
      robban ki, adata nem veszhet el.
- [ ] **Perem-eset (ismert):** támaszték-vesztő rátett blokk (fáklya, falitábla) a
      vanília fizikával lepattanhat és droppolhat — playtesten mérd, mennyire zavaró.

### Regen 3. kör: rúnák, NBT-kapcsoló, TNT-kizárás, támasz-tudatos visszaépítés (2026-07-22)
- [ ] **Rúna-effekt:** robbanást túlélő láda/kemence/tábla körül rúna-részecskék +
      enchant-hang — nem tűnik bugnak, hogy állva maradt.
- [ ] **tile-entity-explode [ki] bekapcsolva:** láda robbanáskor kirobban, tartalma NEM
      szóródik ki, visszaépüléskor a TELJES tartalom + tábla-szöveg visszatér (restart
      közben is — block-regen.yml extra mező). Fej/zászló/spawner ilyenkor is rúna-védett.
- [ ] **TNT-lánc:** több egymás melletti TNT robbanása végigfut (lánc él), de a TNT
      blokkok NEM épülnek vissza — nincs robbanás-hurok, nincs ingyen-TNT.
- [ ] **Törmelék-%:** debris-percent 100-on MINDEN kirobbant blokk repül (nincs darabszám-
      plafon); 40-en kb. minden második-harmadik.
- [ ] **Fizika-lepattanás:** a kirobbant falról fáklya/falitábla NEM droppol — drop nélkül
      tűnik el és a fallal együtt visszaépül (BlockDestroyEvent-út).
- [ ] **Támasz-teszt:** homok/kavics fal + fáklyás fal robbantása → visszaépüléskor SEMMI
      nem esik le és nem pattan le: a gravitációs blokk megvárja az alapját, a fáklya a
      falát (support-grace-seconds [120] után kényszer-visszaépítés).

### Regen 4. kör: teljes tile-entity NBT + rendszer-átvizsgálás (2026-07-22)
- [ ] **Teljes NBT-kör** (tile-entity-explode: true): fej (textúrás!), zászló (mintás),
      spawner, lektorna+könyv, shulker-tartalom, díszcserép robbanása után MINDEN adat
      pontosan visszatér; robbanáskor semmi tartalom nem szóródik ki.
- [ ] **Dupla láda:** a robbanás csak az egyik felet éri → a TÚLÉLŐ fél tartalma
      érintetlen marad; a robbant fél visszaépülve a saját tartalmával tér vissza.
- [ ] **Dedupe:** robbanás + fizika-esemény ugyanarra a blokkra → a blokk EGYSZER áll
      vissza (nincs dupla sor-bejegyzés).

### Regen 5. kör: zóna-mátrix + hurok-fék (tulaj-kérés, 2026-07-22)
- [ ] **Zóna-mátrix:** regen.zones.wilderness true-ra állítva a vadonban is gyógyul a
      creeper-kráter/Wither-rombolás (drop nélkül); false-on vanília. Frakcióföld
      (faction) külön kulcson. A kézi bányászás a vadonban MINDIG vanília marad.
- [ ] **Regen nélküli védett zóna** (pl. zones.capital: false): a régi teljes
      robbanás-tiltás él ott — a mátrix zónánként vált a három mód közt
      (vanília / tiltás / regen).
- [ ] **Hurok-fék:** fáklya vízfolyásba regen-elve max-recaptures [3] után nem épül
      újra (a rendszer elengedi) — nincs végtelen capture-restore kör;
      recapture-window-seconds [600] után a számláló nullázódik.

### Regen 6. kör: fizika-pajzs a visszaépített blokkokon (tulaj-kérés, 2026-07-22)
- [ ] **Pajzs-teszt:** vízfolyásba visszaépült fáklya physics-shield-seconds [300] ideig
      NEM mosódik el (a víz be sem folyik a blokkjába), homok nem esik le, semmi nem
      pattan le; a pajzs lejárta után a vanília fizika visszaáll.
- [ ] **Hurok-fék mint védőháló:** pajzzsal a víz-elmosás hurok el sem indul; 0-ra
      állított pajzsnál (physics-shield-seconds: 0) a max-recaptures fék fogja meg.
- [ ] **Terhelés:** pajzsolt pozíció nélkül a BlockPhysicsEvent-handler gyors-úton
      azonnal visszatér — üresjáratban nem mérhető többlet.

### Regen 7. kör: kráter-pajzs + pajzs fő-kapcsoló (tulaj-kérés, 2026-07-22)
- [ ] **Kráter-pajzs:** robbanás után a lyukba NEM folyik be a szomszédos víz, a
      perem-homok nem omlik be — a kráter a visszaépülésig érintetlen marad; a
      visszaépült blokkok utána physics-shield-seconds-ig pajzsoltak.
- [ ] **Fő-kapcsoló:** physics-shield-enabled: false → a régi megoldás él (fizika
      normál, csak a max-recaptures hurok-fék véd); true → teljes pajzs.
- [ ] **Restart-teszt:** kráteres állapotban restart → a kráter-pozíciók a betöltés
      után is pajzsoltak (a sorból épül vissza a pajzs-lista).

### Regen 8. kör: irány-szelektív víz-szabály (tulaj-döntés, 2026-07-22)
- [ ] **Befolyás él:** vízparti fal kirobbantva → a víz LÁTVÁNYOSAN beömlik a résen és
      megül a kráterben (nem lebeg a peremen).
- [ ] **Tovább-terjedés tiltva:** a kráterben álló víz NEM folyik tovább a fal mögötti
      belső terekbe — az épület belseje szárazon marad.
- [ ] **Kiszorítás:** a visszaépülő fal blokkonként kiszorítja a beállt vizet; záródás
      után a külső víz a falnak feszül, a világ állapota a robbanás előtti.
- [ ] **Fáklya-védelem:** vizes kráterbe visszaépült fáklya az utó-pajzs alatt nem
      mosódik el, mire a pajzs lejár, a fal már körbeérte.
- [ ] **Perem-homok:** a kráterbe hulló homok/kavics ITEMKÉNT esik le (felvehető),
      nem tűnik el némán a visszaépítéskor.

### Regen 9. kör: review-javítások (2 agent leletei, 2026-07-22)
- [ ] **Befalazás-védelem:** a kráterben álló játékosra NEM épül rá a fal (a blokk
      várakozik, amíg el nem mozdul) — nincs fulladás-csapda.
- [ ] **Duplikált handler javítva:** mob-grief + regen egyetlen EntityChangeBlock
      handlerben (enderman blokk-rakás továbbra is tiltva védett zónában).
- [ ] **Zóna-mátrix az ostrom-bontásra is hat** (zones.<típus>: false → ott a bontás
      a régi tiltásra esik vissza).
- [ ] **Hurok-fék csak fizikára:** a szándékos ismételt ostrom-bontás nem tesz
      blokkot "sérthetetlenné"; dupla-törmelék nincs claim+territórium átfedésnél.
- [ ] **Terhelés-javítások:** O(1) dedupe + periodikus pajzs/history-seprés — 100 TNT-s
      lánc alatt mérendő a TPS (playtest-pont).

### Szentségtelen (Unholy) DK-spec + DARK-kaszt kapcsoló (tulaj-kérés, 2026-07-22)
- [ ] **Unholy spec:** csak Kitaszított + bűnös veheti fel (Nekromanta-minta);
      spelljei 25-45: Gennyes Csapás, Járvány, Ghúl-szolga (2 husk-minion),
      Halálörvény (vér-áras), A Holtak Szorítása, Ragály, A Holtak Serege (6 minion
      ulti) + Szentségtelen Kötelék tier3 talent.
- [ ] **Vezeklés-reset:** a vezeklés-lánc záró jutalma a DARK-kapus specet
      (Nekromanta/Szentségtelen) automatikusan elengedi (a kaszt marad, üzenettel);
      nem-DARK-spec játékosnál nem történik semmi.
- [ ] **classes.death-knight.dark-only [false]:** true-ra állítva ÚJ Halállovagot
      csak Kitaszított választhat (magyarázó üzenet); meglévő nem-DARK DK-t nem érint;
      false-on a régi működés.

### DARK-spec csomag: állandó petek + 3 új spec (tulaj-kérés, 2026-07-22)
- [ ] **Állandó ghúl (Unholy):** a Sötét Paktum-tekerccsel élőhalott (zombi/csontváz/
      phantom) fogható be tartós társnak — szintez, átnevezhető, stance-váltós, mint a
      Vadmester/Nekromanta petje; nem-élőhalott NEM fogható.
- [ ] **Warlock démon-familiáris:** kaszt-szintű (mindkét spec): vex/boszorka/blaze/
      magma-kocka/zombified piglin fogható tartós társnak a tekerccsel.
- [ ] **Csontpap (PRIEST, DARK+bűnös):** Csontforrasztás (csoport-regen aura),
      Szívó Sugár (drain), Csont-oltalom (ellenállás-áldás), Királynő Siráma
      (AoE+gyengítés), Vér-tized (SAJÁT HP-ból csoport-regen!), Sír Csendje,
      Utolsó Kenet (nagy Absorption-aura ulti) + Sírfátyol talent.
- [ ] **Pestishozó (ASSASSIN, DARK+bűnös):** Pestis-vágás→Fekete Halál DoT-íve
      (méreg/wither halmozás, Miazma lassítás) + Pestis-mesterség talent.
- [ ] **Demonológus (WARLOCK, DARK+bűnös):** Démontűz, Imp-raj (3 vex), Magma-szolga,
      Démonbőr, Káosz-láng, Áldozati Paktum (vér-áras burst), A Légió (5 vex ulti)
      + Légió-paktum talent.
- [ ] **Kapuk:** mindhárom spec csak Kitaszított+bűnösként vehető fel; vezeklésnél
      automatikusan elvész (spec-reset üzenettel).

### Pet-review P0 javítások (2026-07-22)
- [ ] **/pet item routing:** Szentségtelen és Boszorkánymester a Sötét Paktum-tekercset
      kapja (nem a beast-pórázt) — a befogás mindkét új szerepnek működik.
- [ ] **Tiltólista:** Warden/Ravager/Vasgólem/Elder Guardian/Wither/Sárkány egyik
      szereppel sem fogható be (pets.capture.blocklist, élő kulcs).
- [ ] **Lopás-védelem:** más játékos vanília úton szelídített állata (farkas, ló, macska)
      NEM fogható be idegen befogóval; a sajátod igen.

### Rituálé-idézés: lore-hű állandó társak (tulaj-döntés, 2026-07-22)
- [ ] **Modell-váltás:** a Szentségtelen és a Boszorkánymester NEM befog, hanem IDÉZ —
      a tekercs nekik nem működik, a /pet item útmutatót ad; Vadmester/Nekromanta
      befogása változatlan.
- [ ] **Kellék-beszerzés (kihívás):** Nyughatatlan Szív (ITEM_MODEL `capture_heart`) élőhalott-killből
      3% (csak Szentségtelennek esik), Démon-pecsét (ITEM_MODEL `capture_seal`) boszorka/illager-killből
      6% (csak Boszorkánymesternek).
- [ ] **Rituálé:** kellékkel a kézben jobb-klikk, CSAK ÉJJEL (night-only) — lélek-
      részecskék + hang; a kellék elfogy; nappal hibaüzenet.
- [ ] **Forma-progresszió:** pet-szint 1-14: Ghúl (husk) / Imp (vex); 15+: Csontszolga
      (wither skeleton) / Tűz-démon (blaze); 25+: Förtelem (zoglin) / Magma-behemót —
      a magasabb forma ÚJ rituálét (új kelléket) kér.
- [ ] **Idézett-prémium:** az idézett társ +5 bónusz-szintnyi statot kap (summon.
      bonus-levels) a befogott társakhoz képest; befogásra váltásnál a prémium elvész.
- [ ] **DARK-kilépés spec-reset:** kifizetős DARK→más váltásnál és /faction leave-nél
      a sötét spec automatikusan elveszik (üzenettel) — az "örök paktum" specet nem
      lehet kivinni; MagmaCube-minion halálakor NEM hasad gazdátlan kockákra;
      a dark-only DK-kapu a JobManagerben is él (minden hívási út védett).

### Pet-rework: GUI, állásmódok, Társvért, halál-cooldown (tulaj-kérés, 2026-07-22)
- [ ] **/pet GUI:** üres /pet (vagy /menu → Társ) vezérlőpultot nyit — infó-csempe
      (név/szint/XP/állapot), Idézés, Elbocsátás, Átnevezés-súgó, 3 állásmód-gomb
      (az aktuális világít), Társvért-státusz, Bezárás; minden kattintás a /pet
      alparancsokra delegál és frissíti a GUI-t.
- [ ] **Állásmód (gazda-PDC):** /pet stance aktiv|passziv|marad + sunyítás+jobb katt
      a társon (ciklikus váltás) + GUI-gombok — mindhárom út ugyanazt állítja;
      Passzívban sosem harcol, Maradj-ban helyben vár (világváltásnál utánad jön);
      a spell-idézett minionok állásmódja ettől független (entitás-PDC).
- [ ] **Halál-cooldown:** a társ halála után 120 mp-ig (pets.companion.death-respawn-
      seconds) nem idézhető újra — a GUI mutatja a hátralévő időt; 0 = kikapcsolva.
- [ ] **Társvért (ITEM_MODEL `capture_heart`/társ-felszerelés ikon):** 1% eséllyel esik szörny-killből társ-tartó kasztnak
      (csak amíg nincs felszerelve); jobb katt a SAJÁT kint lévő társon → +4 páncél
      +4 max-HP (pets.equipment.*), elfogy; újraidézés után is a társon marad;
      idegen mobra/másik játékos társára nem tehető fel.
- [ ] **Talent-átszállás:** a gazda max-health talentjeinek fele (pets.talent-health-
      share) a companionra is rászáll idézéskor (a minionokkal azonos arány).
- [ ] **Kijelentkezés-takarítás:** logoutkor a kint lévő társ/minionok despawnolnak
      (PDC-ből újraidézhető) — nem marad árva persistent entitás; belépés után
      /pet summon visszahozza.
- [ ] **Aggro-szűrő:** a társ/minion célzása játékosra és falusira csak parancsból
      (assist/defend) mehet — magától sosem támad rájuk; chunk-betöltéskor a
      gazdátlan (offline tulaj) minionok despawnolnak.

### DARK-spec hangolás + iskolák + katalógus-rend (2026-07-22)
- [ ] **Fekete Halál újrahangolva:** 150 XP (100 helyett) és 6 blokk sugár (8 helyett)
      — spells-balance.yml black_death entry a kóddal egyezik.
- [ ] **Spell-id ütközés javítva:** a Pestishozó Fertőzése plague_contagion, a
      Szentségtelen Halálörvénye unholy_coil id-t kapott — a Méregkeverő contagion
      és a DK-alap death_coil spellje változatlanul működik (regresszió-teszt!).
- [ ] **Iskola-felülírások:** Szentségtelen/Csontpap sebző spelljei árny-iskola,
      a pestis/méreg spellek természet-iskola (spells.yml by-spell) — az iskola-
      bónuszok ennek megfelelően számolnak.
- [ ] **Balansz-entryk:** mind a 28 új DARK-spell (4 spec + 5 idézés) explicit
      spells-balance.yml entryvel — /icesmp config set spell-balance.<id>.<kulcs>
      élőben hangol.

### P2 1. csomag: kis kód-patchek (tulaj-kérés, 2026-07-22)
- [ ] **Örök paktum kapu:** paktumos DARK-játékos se /faction join <más>, se /faction
      leave úton nem tud kilépni (üzenet: vezeklés az egyetlen út); a vezeklés-lánc
      után (breakDarkPact) a kilépés újra működik, és a sötét spec ilyenkor is
      elveszik a már élő reset-úton.
- [ ] **Vérdíj bűn-mosás fék:** ugyanarra az áldozatra 12 órán belül a második
      kivégzés se veretet, se bűn-nullázást nem ad (üzenet jelzi); az első
      kivégzés változatlanul fizet ÉS nulláz.
- [ ] **admin.item jog:** az icesmp.admin.all mostantól a /iceitem-et is megadja;
      LuckPerms-ben az icesmp.admin.item külön is kiadható.
- [ ] **Quest-dialógusok:** a merchant_choice a vándor kereskedőnél, a
      forest_cleansing az erdei véneknél vehető fel NPC-re kattintva — a
      dialógus-sorok ténylegesen megjelennek.
- [ ] **Vásárjárás átdolgozva:** a NEUTRAL heti Vásárjárás mostantól szállítás
      (25 kenyér/smaragd a vándor kereskedőnek), nem a Vásár hete duplikátuma.
- [ ] **Ascendant talent:** 10 elköltött pont után (requires-spent 9 + maga a
      talent) tiszta szintezésből is megvehető az 50. szinten.
- [ ] **Unique-smelt:** unique anyag (pl. mélységi borostyán) kemencében NEM olvad
      át vanília itemmé — a smelt elakad rajta.
- [ ] **Claim entitás-védelem:** idegen claimben állat/villager/armor-stand nem
      ölhető (közvetlen ütés, nyíl, szelídített állat útján sem), item-frame nem
      forgatható/fosztható, mob nem vödrözhető; szörny-ölés és PvP változatlan;
      megbízott (trust) mindent tehet.
- [ ] **Lélekkő napi keret:** 50 lélekkő/nap/játékos (élő kulcs:
      currency.soul-drop.daily-cap; 0 = korlátlan); spawner-mob eleve nem ejt
      (skálázatlan → min-mob-level alatt).
- [ ] **Parkour-fék:** pályánként napi 3 jutalmazott futam (parkour.daily-reward-limit,
      élő kulcs) — fölötte a futam és a quest-haladás számít, veret nem jár (üzenet);
      gyöngy/refő-teleport, elytra-nyitás és riptide futás közben MEGSZAKÍTJA a futamot.
- [ ] **Doksi-szinkron:** README (DARK-belépés bűn-alapú, 6 zónatípus, 400+ recept),
      01-kezdes (veret-átadás a kikapcsolt /currency pay helyett), 02-frakciok
      (paktum-zár), 03-valuta (lélekkő-keret), 14-parancsok (territory-típusok).

### A-csomag: tulaj-döntések implementálása (2026-07-22)
- [ ] **End-zár:** stronghold-keretbe nem tehető szem (üzenet), égő End-portálon nem
      lehet átlépni; zóna-bypass joggal igen; end-portal.allow=true visszaadja a vaníliát.
- [ ] **Combat-tag:** PvP-találatra 12 mp jelölés (actionbar); jelölt játékos a védett
      zónában IS sebezhető (üldözés végigvihető), a komp elutasítja; logout törli;
      12 mp harc nélkül lejár és a zóna-védelem visszaáll.
- [ ] **Min-harc-idő:** hadi-ablak pont és párbaj bűn-tisztulás csak akkor jár, ha a
      pár első összecsapása óta ≥20 mp telt el — egy-ütéses ölés üzenetet ad, pontot nem.
- [ ] **CC-DR:** ugyanazon játékosra 15 mp-en belül a 2. fagyasztás/erős lassítás fele,
      a 3. negyed ideig tart, a 4.-től immun; mobokra nem vonatkozik; spells.cc-dr élő.
- [ ] **Szezon-lépcső:** szezonzáráskor teljes végeredmény-broadcast; 2. hely fél,
      3. hely negyed kassza-jutalom (runner-up-ratios, élő kulcs).
- [ ] **Árfolyam:** reference-supply 2500 (50 fős kalibráció); napi váltási keret 200
      forrás-veret (daily-limit, 0=ki) — túllépésnél üzenet, a keret másnap nullázódik.
- [ ] **Kultista-loot:** portya/hírvivő/rítus-hívő leölve 35% eséllyel dob a
      cultists.loot táblából (árnyékpor/emlékszilánk/ritkán Suttogás-meghívó).
- [ ] **Kazamata-loot:** /territory dungeonchest regisztrált láda fejenként hetente
      fosztható (vanília nyitás cancel, virtuális loot, actionbar-visszajelzés);
      /territory dungeonboss kijelölt mini-boss belépéskor ébred (24h respawn),
      halálakor boss-tábla loot; kazamata-mob 8% bónusz-drop; minden kulcs élő.

### B-csomag: balansz-hangolások (tulaj-jóváhagyás, 2026-07-22)
- [ ] **Vér-spellek:** garrote/bloodbath/phantom_grip/soul_rot +1 sebzés (spells-balance).
- [ ] **Energia-regen:** orgyilkos/szerzetes 7/mp, íjász 6/mp — a tár most tényleg kifogyhat.
- [ ] **Tier2 talent-párok:** kasztonként eltérő effekt/szám (pl. Varázsló: spell-power vs
      HP; Íjász: sebzés vs mozgás; Boszorkánymester: erős spell-power vs kis HP) — a
      választás már nem álválasztás; respec után újraoszthatók.
- [ ] **Recept-szintek:** 6 recept a hozzávalója forrás-szintjére emelve (tutaj_keszlet 24,
      vandor_lepenye/fuszeres_nyulragu 22, kivonatos_csontliszt_bomba/kemenyfa_nyelkoteg 24,
      runapor_finomitas 26); a checker 7. szabálya mostantól FAIL-t ad ilyen driftre.
- [ ] **Régi mesterművek KI:** a 8 kódba égetett recept (Tárnász Csákány…) nem craftolható
      (legacy-masterworks=false); a meglévő példányok működnek; a katalógus-párok élnek.
- [ ] **DARK szárny-rituálé:** csontblokk 16 + lélekhomok 16 + gyémántblokk 1 — a
      többi szárnnyal azonos árszint.
- [ ] **Kaszt-megerősítés:** első kaszt-kattintás figyelmeztet, 30 mp-en belül megismételve
      választ; a DK dark-only kapu és a setPrimaryJob-elutasítás változatlan.
- [ ] **Király-lejárat:** term-days után a trón automatikusan megüresedik (broadcast),
      új szavazás indul; dethrone-on-expiry=false visszaadja a régi viselkedést.
- [ ] **Tanácsi keret:** 3 tanácstag EGYÜTT max 400/nap (közös számláló) — a 2. tanácstag
      kivétje már az elsőét is terheli.
- [ ] **Régész-megosztás:** kiásáskor a 24 blokkon belül állók 50% eséllyel (max 3 fő)
      kisebb leletet kapnak; a lelőhely-spawn 250 blokk szórású.
- [ ] **Kazamata csoport-kulcs:** egy kulcs a 16 blokkon belüli párttagoknak is nyit;
      friss pecsétes tag nem kap új passzt.
- [ ] **Suttogó tanú-gyanú:** éjszakai élőhalott-békesség kívülálló szemtanú előtt 2%
      eséllyel +1 gyanú.
- [ ] **Közösségi célok:** szezonváltáskor nullázódnak (broadcast a szezonzáró részeként).
- [ ] **Honvágy:** a debuff 45 mp Éhség (10 helyett) — érezhető, de nem büntető.
- [ ] **Rontás-góc:** 75 percenként 35% — kb. kétszer olyan gyakori, mint eddig.

### Üzembiztonsági kör: NPC-tartalék + HUD/tablist perf + tick-szétterítés (2026-07-22)
- [ ] **NPC-tartalék-út:** FancyNpcs NÉLKÜL a /quest talk <npc-név> teljesíti a
      TALK_TO_NPC/DELIVER_ITEMS célokat és felveszi a giver-npc questeket (dialógussal);
      híddal a parancs udvarias elutasítást ad (quest-npc-fallback.always=true felülírja);
      tab-complete a quest-NPC neveket ajánlja.
- [ ] **Dialógus parancsos felvételkor:** /quest accept után a give-dialógus lejátszódik
      (merchant_choice elágazó szövege parancsból is látszik).
- [ ] **Induláskori NPC-őr:** FancyNpcs hiányában WARN a logban a quest-NPC-számmal;
      híddal 60 mp után létezés-ellenőrzés — a hiányzó NPC-k név szerint a logban.
- [ ] **Tablist-perf:** a kilépő játékos team/score-bejegyzése legkésőbb ~5 mp-en belül
      (sweep-every-refresh) tűnik el; a nevek/ping/színek frissítése azonnali marad;
      60 fős terhelésnél a tablist-tick ideje mérendő (cél: nincs tüske).
- [ ] **HUD diff-cache:** változatlan oldalsáv-sor nem generál csomagot (packet-sniff
      vagy timings); sor-változás (HP, valuta) azonnal átmegy; /hud szekció-váltásnál
      és sidebar ki/be-nél nincs beragadt sor.
- [ ] **Világesemény-szétterítés:** a ~33 rendszer-tick 4-es csokrokban, az intervallum
      első felére terítve fut (timings: nincs 60 mp-enkénti globál-tüske); egy hibázó
      manager WARN-t ír, de a többi csokor lefut.
- [ ] **Pet-tick üresjárat-fék:** társ nélküli játékosokra nem megy scheduler-hop
      (timings); idézésnél azonnal indul a követés, elbocsátás/halál/logout után leáll.

### HP/sebzés-skála (A17 1. ütem, tulaj-kérés, 2026-07-22)
> ⚠️ ALAPBÓL KIKAPCSOLVA (tulaj-döntés): a teszthez előbb health.enabled=true +
> scale-heals=true (config-GUI "HP-rendszer" kategória vagy /icesmp config set).
- [ ] **Kaszt-HP-profil:** kaszt-választás után a max HP a szinttel nő (pl. Harcos 25.
      szint: 20+15=35; Varázsló 25. szint: 26.25); szint-lépés után legkésőbb 2 mp-en
      belül frissül; kaszt admin-resetnél visszaáll 20-ra; talent-HP ezen felül adódik.
- [ ] **Szívsor-normalizálás:** a kijelzés minden max HP-nál 10 szív (a szív "értéke"
      nő); health.display.normalize=false visszaadja a vanília szív-duzzadást;
      abszorpció-szívek továbbra is látszanak.
- [ ] **Harcon kívüli regen:** sebzés-kontaktus (adott VAGY kapott, PvE is) után 8 mp-ig
      nincs regen; utána 2 mp-enként max-HP 5%; 3 comb (6 food) alatt nem fut; harc
      közben a vanília étel-regen változatlan; a regen a sebzés-számokat nem triggereli.
- [ ] **Gyógyítás-skálázás:** 40 HP-s tanknál a 4-es heal 8-at gyógyít (maxHP/20 = 2.0
      szorzó, plafon 2.5); 20 HP-s frissnél változatlan; scale-heals=false kikapcsolja.
- [ ] **PvP TTK mérés:** 50-es szinteken a kill-idő érezhetően hosszabb (cél: nem
      2-3 spell); Fagylovag-kombó a CC-DR + nagyobb tavak mellett mérendő.
- [ ] **PvE mérés:** a nagyobb játékos-HP mellett a magas szintű zónák nehézsége
      (mob damage-per-level 1.0) újraértékelendő — szükség esetén emelés.
- [ ] **Admin-GUI:** /icesmp config menü új "HP-rendszer" kategória — kapcsolók élőben.

### WoW-mód 2. ütem: sebzés-profilok + Varázserő gear-affix (tulaj-kérés, 2026-07-22)
> ⚠️ ALAPBÓL KIKAPCSOLVA (health.enabled=false) — a teszthez kapcsold be.
- [ ] **Kézi sebzés-skála:** 50-es Harcos gyémántkarddal +5 sebzést üt a szint-bónuszból
      (attribútum, F3-mal ellenőrizhető); kikapcsolásnál élőben lekerül; casternél +1.5.
- [ ] **Lövedék-skála:** a nyíl/számszeríj/szigony találata +szint-bónusz sebzést kap
      (Íjász 50: +4); a bónusz a lövéskori cache-ből jön (kaszt-váltás után max 2 mp).
- [ ] **Varázserő-affix:** WoW-módban a loot/craft gearre sorsolódhat (páncél ÉS fegyver;
      "+N% Varázserő" lore); a spell-sebzés ennyivel nő (a dynamic-scaling 50%-plafonja
      alatt); Ócska-rollon negatív lehet; KIKAPCSOLT módban NEM sorsolódik, de a már
      létező példány visszakapcsoláskor újra hat.
- [ ] **Halmozódás-ellenőrzés:** szint% + talent + Varázserő együtt sem lépi át a
      spells.dynamic-scaling.max-bonus-percent plafont; a teljes szorzat a
      total-power-cap (1.75) alatt marad.

### Bootstrap-kiaknázás: 5 új iskola-counter + védőháló (tulaj-kérés, 2026-07-22)
- [ ] **Védőháló:** a bootstrap handler-hibái logolódnak, de a szerver elindul
      (szimulálható: ideiglenesen rossz kulccsal); a spellek vanília sebzésre esnek
      vissza, a signature-stamp kihagyja a hiányzó enchantot.
- [ ] **5 új counter-enchant:** Éj-fátyol (szent), Árnyűző (árnyék), Méregfojtó
      (természet), Viharfogó (vihar), Káosz-zabla (káosz) — enchanter 38-as
      tervrajz-receptek (ITEM_MODEL manifestben), enchantelt könyv, üllőn mellvértre; a
      megfelelő iskola spell-sebzését szintenként 10%-kal csökkenti (magic-resist.
      school-per-level), a Rúnavérttel a 60% plafonig összeadódik.
- [ ] **Counter-exkluzivitás:** két különböző iskola-counter NEM kerülhet egy
      páncélra üllőn (az eredmény eltűnik); Rúnavért + egy counter együtt mehet;
      a meglévő Fagypáncél/Főnixtoll párra is érvényes.
- [ ] **DARK-specek ellen:** Árnyűző mellvértben a Szentségtelen/Csontpap árny-spelljei
      ~10%-kal kisebbet ütnek; Méregfojtó a Pestishozó természet-spelljei ellen.
- [ ] **Enchant-asztal tiszta:** az új enchantok sem jönnek enchant-asztalról.

### Mélyaudit-kör: lore-kánon, /lore Radicora, escort-útvonal (2026-07-26)
- [ ] **`/lore radicora` VALÓDI szócikk:** `/lore radicora` (aliasok: `o-caldestera`,
      `ocaldestera`, `gyokerek`) Radicora/Ó-Caldestera 6 sorát adja — NEM a `menedek`
      általános frakció-összefoglalóját. `/lore` usage-sora és a tab-complete is
      felsorolja. Ismeretlen téma (`/lore izelabda`) továbbra is a usage-sort adja,
      nem csendben más szócikket.
- [ ] **`/lore fa` eredet-javítás:** a Fa „Asterlayna lelkének utolsó
      csillagszilánkjából kelt ki" — nem „a teste fölött nőtt" (a kódex szerint a
      teste semmivé foszlott).
- [ ] **`/lore menedek` földrajz:** Ryanora ősi szíve a Fa tövében (Radicora), a mai
      főváros Caldestera a szoroson túl, komppal — a két hely nem keverhető össze.
- [ ] **Tábortűz kánon-hűség:** az Asterlayna-leszállás sora már szóbeszédként szól
      („Van, aki azt tartja…") és nem írja át az Első Háború okát; a DARK Királyok
      Átka-sor sem azt mondja, hogy a Felsőkre nem hat, hanem hogy a sírban nem
      tarthatja őket, viszont a korona szorít, míg viselik.
- [ ] **Escort-útvonal spawn-guard:** `/events escort` — a konvoj nem CSAK védelem-mentes
      pontról indul, hanem az útvonala mintavett pontjai (25/50/75/100%) is védelem-
      mentesek. Teszt: vegyél fel egy claimet/territóriumot az anchor mellé úgy, hogy a
      lehetséges célok többsége bele essen → a konvoj vagy más irányba indul, vagy
      (ha mind a 8 próbált irány védett) ebben az intervallumban nem indul el egy sem.
      A `world-events.spawn-rules.escort.*` mátrix-sor kikapcsolásával a korlát megszűnik.
- [ ] **Ereklye-flavor:** a legendás flavor-sorok között NEM szerepel egyediség-állítás
      („Egyetlen darab létezik belőle") — a ritkaság ismételhetően generálódik, egyediséget
      csak a nevesített, ID-zett relikvia állít.
- [ ] **Gépi őrök (nem ingame, de a körhöz tartozik):** `python3 scripts/check_consistency.py`
      FAIL-el, ha (a) egy doksi-szám elszakad a mért értéktől (Java-fájl/manager/store/
      tábortűz-mese/Idegen-sor), (b) az ARCHITECTURE.md csomagtérkép fájlszáma driftel,
      (c) egy `/lore` téma tab-complete/szócikk/usage-sor hármasa szétcsúszik.

### Mélyaudit-kör: állapotgépek (spell-provenancia, frakció, király, raid) 2026-07-26
- [ ] **Spec-reset nem halmoz (HIGH-19):** válassz kaszt-specet, szintezz addig, hogy a spec
      spelljei feloldódjanak (`/spell lista`), majd `/spec respec class` → a spec spelljei
      ELTŰNNEK a listából. Válassz másik specet: csak AZ ő spelljei jönnek. A kaszt-szintből
      járó spellek MINDIG megmaradnak.
- [ ] **Talent-visszavonás forrásérzékeny (MED-07):** ha egy talent olyan spellt ad, amit a
      kaszt-szint IS ad, a talent elvesztésekor (spec-váltás → `refundUnavailableTalents`)
      a spell MEGMARAD. Ha csak a talent adta, eltűnik.
- [ ] **Admin-feloldás sérthetetlen:** `/job admin unlockallskills <játékos>` után egy
      spec-respec NEM veszi el a spelleket (ADMIN forrás).
- [ ] **Régi (provenancia előtti) mentés:** olyan játékossal, aki a frissítés ELŐTT halmozott
      össze két spec spellkészletét, az első `/spec respec class` letakarítja a régi spec
      spelljeit is (a backfill visszamenőleg forráshoz kötötte őket).
- [ ] **Frakció-kilépés nem kerülőút (HIGH-17):** RED-ként `/faction leave` (fizetős, kapus)
      → utána `/faction join blue` a világ közepén: az **elutasítás** a semleges-főváros kapura
      hivatkozik. Szezon-hajrában (`factions.switch.lockout-final-days` ablakban) a leave utáni
      join is TILOS. Új játékos első `/faction join`-ja továbbra is ingyenes és kapu nélküli.
- [ ] **Király-mandátum a koronázástól (HIGH-18):** `factions.kings.term-days: 1` +
      szavazás a ciklus vége felé → a friss király mandátuma a koronázástól számol (nem esik
      le azonnal), a korona-átok szintje 0-ról indul (`/faction king info`), a szavazólap
      kiürül, és a `crowned` haladás-bejegyzés MEGJÖN (eddig csak admin-koronázásnál jött).
      Restart után a mandátum nem indul újra (reign-start a kings.yml-ben).
- [ ] **Raid-nevezés csak felkészülésben (HIGH-16):** raid hirdetés → várd meg a harci szakasz
      kezdetét → `/faction raid join` → „a harci szakasz már megkezdődött" hibát ad.
- [ ] **Raid-díj visszatérítés restartnál (HIGH-16):** hirdess raidet (kassza csökken a
      nevezési díjjal), majd állítsd le a szervert → a broadcast a visszatérítést is említi,
      és a frakciókassza visszakapja a díjat (`/faction treasury`).
- [ ] **Raid-cooldown restart-álló (HIGH-16):** raid vége után azonnal restart → a
      `/faction raid start` továbbra is cooldown-hibát ad (raids.yml).
- [ ] **Fegyvertilalom offhandben is (DEEP-MED-10):** offhandbe vett karddal állj be
      Caldesterába → az őrség azt is elrakja. Töltsd tele a hátizsákot (csak az aktív slot
      legyen szabad) → a fegyver NEM kerül vissza a kezedbe; helyette „tele a hátizsákod"
      figyelmeztetést kapsz.
- [ ] **Párbaj heti számláló (DEEP-MED-11):** `honor-duel.weekly-limit: 2` — használd el a
      heti kettőt, várd meg a hét-váltást (vagy állítsd át a rendszeridőt) → az új hét első
      párbaja elfogadható. Visszautasítás (`/parbaj nem`) után a kihívó AZONNAL kihívhatja
      újra ugyanazt a játékost (nincs „duel-pending" a lejáratig).
- [ ] **Közösségi cél maradéka átvisz (DEEP-LOW-02):** állíts célt 10-re, adj be 8-at, majd
      egy 64-es stacket → a cél teljesül, és a következő ciklus NEM 0-ról, hanem a maradékkal
      indul (`/celok`); egy hozzájárulás legfeljebb 3 ciklust zár le.
- [ ] **Függőleges biome-progressz (DEEP-LOW-01):** barlang-biome quest (`EXPLORE_BIOME`,
      pl. lush_caves) — ásd le magad EGY oszlopban a barlang-biome-ba: a progressz megjön
      (eddig csak X/Z elmozdulásra futott az ellenőrzés).
- [ ] **Globális AFK toggle és tablista (DEEP-LOW-03):** `/afk` ON, majd mozgás nélkül
      azonnal `/afk` OFF → a jelölés eltűnik és nem jelenik vissza azonnal automatikusan.
      Ezután indíts `hud.enabled: false`, `tablist.enabled: true` beállítással: a tablista
      és az AFK hátrasorolás HUD nélkül is működik; tablista-kikapcsoláskor a suffix/teamek eltűnnek.
- [ ] **Relikvia keep-mód recovery (DEEP-HIGH-07):** `relics.passive-death.mode: keep` —
      halj meg passzív relikviával, majd respawn ELŐTT lépj ki. Visszajelentkezés után a
      relikvia a rituálé-oltárnál ÚJRAIDÉZHETŐ (a tulajdon él, nem ragadt be). Ha viszont
      normálisan respawnolsz, a tárgy visszakerül ÉS az oltár NEM ad második példányt.
- [ ] **Gépi őrök:** `python3 scripts/check_consistency.py` FAIL-el, ha (a) egy
      `unlockSpell(...)` forrás nélkül hív, (b) valahol `removeFaction(...)` törli a
      frakció-hozzárendelést, (c) kéz-kiürítést közvetlenül `addItem` követ.

### Mélyaudit-kör: territórium-átadás, advancement-fa, vagyon, WG-híd (2026-07-26)
- [ ] **Raid-foglalás megőrzi a függőleges sávot (DEEP-MED-07):** adj egy frakcióterületnek
      Y-sávot (`/territory sety <id> 60 90`), raideld el → a győztes frakcióé lesz, DE a
      terület Y-sávja MEGMARAD (`/territory info <id>` — eddig teljes világmagasságúvá vált),
      és a poligon-alak/rádiusz/középpont is változatlan; a főváros-státusz nem száll át.
- [ ] **Hiányos advancement-fa KIKAPCSOL (MED-10):** töröld ki EGY advancement-JSON-t a világ
      datapack-könyvtárából, indítsd újra → a log SEVERE sorban NEVESÍTI a hiányzó
      bejegyzés(eke)t, és az advancement-rendszer kikapcsol (nem oszt némán semmit).
      A teljes pack visszaállítása után újraindításnál minden bejegyzés él.
- [ ] **Advancement élő kikapcsolás:** `/icesmp config set advancements.enabled false` →
      innentől nem jön toast/bejegyzés (eddig a betöltött fa tovább osztott);
      visszakapcsolás után `/icesmp reload` kell (a fa regisztrációja indulási).
- [ ] **Egységes vagyon (MED-08):** adj a játékosnak EGYSZERRE többféle valutát (pl. 500
      Parals + 300 Creutzér). A `/toplista vagyon`, a heti krónika/bárdi ének és a
      vagyon-elérés UGYANAZT az összeget mutatja (az összes valuta összegét) — eddig a
      ranglista csak a default valutát nézte.
- [ ] **Claim-átfedés valódi metszés (HIGH-20):** hozz létre WorldGuardban egy KICSI régiót
      (pl. 5×5) úgy, hogy ne essen chunk-középre, és egy másikat eltérő Y-magasságban →
      a `/claim` a környező területre ELUTASÍT (eddig a mintapontok közt átcsúszott).
- [ ] **WG-híd megszakító (HIGH-20):** ha a WG-lekérdezés hibázik (pl. WG-reload közben), a
      log figyelmeztet, a claim-ellenőrzés 60 másodpercig ELUTASÍT (fail-closed), utána a
      híd magától újra próbálkozik — NEM marad végleg kikapcsolva. WorldGuard NÉLKÜL a
      claim továbbra is engedélyezett (nincs régió, amit védeni kell).

### P0 kiadásblokkolók: block-regen napló + piaci tranzakció-napló + kill-kontextus (2026-07-26)
> Ezek a blokk-visszaépítés, a piac és a mob-jutalom TARTÓSSÁGI/szál-helyességi javításai.
> A tesztek nyers leállítást (`kill -9`) is kérnek — külön teszt-világon futtasd.

#### Block-regen write-ahead napló (CRIT-05)
- [ ] **Tartalom-vesztés:** `territory.protection.regen.tile-entity-explode: true`, védett zónában
      tegyél egy ládába 3 jól felismerhető tárgyat, robbants rá TNT-vel, majd 2 percen belül
      `kill -9` (NEM `/stop`). Újraindítás után a láda visszaépül, PONTOSAN a három tárggyal
      (a duplikáció is hiba).
- [ ] **Örök lyuk:** `regen.delay-seconds: 20`, robbants ki ~30 blokkos falszakaszt, majd 25 s
      múlva — MIKÖZBEN épül vissza — `/stop`. Újraindulás után a fal maradéka is visszaépül.
- [ ] **Napló-hiba fail-safe (NINCS dupe-farm):** `chmod 500 plugins/IceSMP`, majd robbants rá
      egy TELI ládára. A láda sértetlenül él túl (óvó rúna-effekt), a logban napló-hiba.
      **KRITIKUS:** ha a láda MÉGIS visszaépül, egyszer épül vissza — NEM töltődik újra
      másodpercenként (az ismételt világ-mutáció korlátlan tárgy-duplikáció lenne).
- [ ] **Hiányzó világ:** robbants külön világban, `/stop`, nevezd át a világ mappáját, indíts
      újra → világonként EGY warning, a rekordok a block-regen.yml-ben MEGVANNAK. Visszanevezés
      + újraindítás után a blokkok visszaépülnek. `/icesmp reload` után a warning újra jöhet.
- [ ] **Napló-tisztulás:** ~10 blokk kirobbantása, teljes visszaépülés, `/stop` → a
      `block-regen.yml` `pending` szakasza ÜRES, a `block-regen.wal.rotated` NEM létezik.
- [ ] **Sérült checkpoint:** rontsd el a `block-regen.yml`-t, indíts újra → karantén-másolat +
      mentés-tiltás a logban, a robbanások tovább működnek, a `.wal` nő (rekord nem vész el).

#### Piaci tranzakció-napló (CRIT-06)
- [ ] **Tiszta út:** `/market sell 100` → a tárgy eltűnik a kézből, a tétel a GUI-ban van, és a
      `market-journal.yml` a művelet UTÁN ÜRES.
- [ ] **Vásárlás:** A listáz 100-ért, B megveszi → B bankjából a kiírt összeg, A-nak a 10% díj
      után 90, a tárgy B-nél, a napló üres.
- [ ] **Crash vásárlás közben:** `kill -9` közvetlenül egy vásárlás után. Újraindítás: VAGY a
      tárgy B-nél van ÉS A pénzt kapott, VAGY a tétel visszaáll és B pénze megvan — köztes
      állapot nincs. **Sosem fordulhat elő, hogy B megkapja a tárgyat és a pénze is visszajön.**
- [ ] **Crash listázás közben:** listázás után azonnal `kill -9`. Belépéskor EGY üzenet, és a
      tárgy PONTOSAN egy helyen van (inventory VAGY piaci tétel).
- [ ] **Két nyitott listázás (tranzakciónkénti jelző):** írásvédett napló mellett próbálj két
      különböző tárgyat listázni, majd oldd fel és indíts újra → MINDKÉT tárgy előkerül
      (egy közös jelző esetén a második felülírta volna az elsőt, és az elveszett volna).
- [ ] **Rejtett tétel nem foglal helyet:** helyreállításra váró (még meg nem erősített) tétel
      NEM számít bele a `market.max-listings-per-player` limitbe, és nem okoz hamis
      „élő licites aukció nem vonható vissza" hibát.
- [ ] **Sérült napló:** szemetet a `market-journal.yml`-be, újraindítás → `/market sell`
      ELUTASÍT érthető magyar üzenettel („a tranzakció-napló most nem írható"), a GUI-vásárlás
      is, az aukció-lezárás halasztódik; pénz és tárgy érintetlen.
- [ ] **Aukció escrow:** A aukciót indít, B licitál, C túllicitál → B visszakapja a zárolt
      licitet, C-től a nagyobb összeg levonva. Nyers leállítás + újraindítás: az egyenlegek
      ugyanezek.

#### Kill-kontextus és jutalom-utak (CRIT-07)
- [ ] **Kereszt-régiós ölés:** íjjal/spellel ölj mobot távoli régióban → az erszény/lélekkő/
      kazamata-drop a MOB helyén, a kaszt-XP/lélekszilánk/pet-XP nálad, konzol-hiba NINCS.
- [ ] **Nincs dupla erszény:** rúnás fegyver, `mob-money-drop.chance-percent: 100` + bónusz 50
      → ölésenként PONTOSAN egy erszény.
- [ ] **Kill-szintű retesz:** ugyanaz az ölés két jutalom-csatornán sem fizet kétszer (a retesz
      az áldozat UUID-jéhez kötött, nem a kontextus-példányhoz).
- [ ] **Kijelentkezés a hop előtt:** ölj mobot és AZONNAL lépj ki → nincs konzol-hiba.
- [ ] **Előszűrők:** kreatívban / AFK-ban / spawner-mobra / saját minionra a FAUCET-jutalmak
      nem fizetnek; spawner-mobnál a kaszt-XP és pet-XP IGEN.
- [ ] **Ismert korlát (nyitott):** kereszt-régiós ölésnél a PÁRT-XP megosztás és a Vad Hajsza
      személyes lootja némán elmaradhat (pozíció-olvasás az áldozat szálán, fail-open) — ezt
      külön kör zárja, nem regresszió.

### P0-A…F csomagok: fail-closed perzisztencia és Folia-életciklus (2026-07-26)
> Ezek a csomagok az adatvesztés és a szál-helyesség ellen dolgoznak. Több teszt NYERS
> leállítást (`kill -9`) kér — külön teszt-világon futtasd, és előtte mentsd a `plugins/IceSMP`-t.

#### P0-A — sérült állapotfájl fail-closed
- [ ] **Indulás megszakad:** állítsd le a szervert, írj szemetet a `plugins/IceSMP/currency-balances.yml`-be,
      indíts újra → a log SEVERE sorban nevezi a fájlt, karantén-másolat készül, és az IceSMP
      **NEM indul el** (nem fut tovább üres egyenlegekkel). A karantén visszaállítása után indul.
- [ ] **Szemantikai sérülés is fogja:** ép YAML, de negatív egyenleg / nem-UUID kulcs →
      ugyanaz a fail-closed viselkedés (nem csak a parse-hiba számít).
- [ ] **A közösségi cél számlálója is:** negatív `progress` érték a `community-goals.yml`-ben →
      indulás megszakad, nem nullázza némán a célt.

#### P0-B — piac/wallet fail-stop
- [ ] **Napló nem írható → a művelet elmarad:** `chmod 500 plugins/IceSMP`, majd `/market sell 100`
      → érthető magyar hibaüzenet („a tranzakció-napló most nem írható"), a tárgy a kézben MARAD.
      Ugyanez a GUI-vásárlásnál. Jogosultság visszaadása után minden működik.
- [ ] **Wallet-commit a napló törlése ELŐTT:** vásárlás közbeni `kill -9` után az egyenleg és a
      tárgy MINDIG egy irányba zárul — sosem fordulhat elő, hogy a vevőnél a tárgy ÉS a pénze is.

#### P0-C — block-regen tokenes recovery
- [ ] **Teli láda + kill -9:** védett zónában robbants rá egy 3 felismerhető tárgyat tartalmazó
      ládára, 2 percen belül `kill -9` → újraindítás után a láda PONTOSAN a három tárggyal épül
      vissza (se több — a duplikáció is hiba —, se kevesebb).
- [ ] **Nincs dupe-farm:** írásvédett data-könyvtár mellett a láda **egyszer** épül vissza, NEM
      töltődik újra másodpercenként.

#### P0-D — forrás-eseményes gyűjtés + contribution receipt
- [ ] **Ledob–felvesz nem duplázza:** `COLLECT_ITEMS` közösségi célnál dobd el és vedd fel
      ugyanazt a stacket többször → a számláló CSAK egyszer nő.
- [ ] **Olvasztás/horgászat egyszer számít:** kemencéből kivett és kifogott tétel is pontosan
      egyszer könyvelődik, `kill -9` + újraindítás után sem ismétlődik (tartós receipt).

#### P0-E — Folia kill-pillanatkép + scheduleres párt-jutalom
- [ ] **Kereszt-régiós párt-XP:** két párttag távoli régiókban; öld meg a mobot úgy, hogy a
      másik tag a megosztási sugáron BELÜL, de MÁSIK régióban legyen → mindkettő megkapja az
      osztott XP-t (korábban a megosztás némán elmaradt), és a konzolon nincs szál-hiba.
- [ ] **Sugáron kívüli tag nem kap:** a sugáron kívüli párttag nem kap részt; a teljes XP a
      gyilkosnál marad (nincs XP-nyomtatás és nincs veszteség).
- [ ] **Offline/kilépő tag:** ölés után azonnal lépjen ki az egyik tag → nincs konzol-hiba, a
      többiek megkapják a részüket (a 4 tickes határidő lezárja az aggregációt).

#### P0-F — mulandó entitás életciklus + esemény-watchdog
- [ ] **Karaván-kíséret VÉGIGFUT:** `/events escort` → a konvoj elindul és célba ér (NEM zárul
      le azonnal „a konvoj elesett" üzenettel). Ez a regisztráció-teszt: a liveness fail-closed,
      így regisztráció nélkül az esemény az első tickben elhalna.
- [ ] **Vad Hajsza VÉGIGFUT:** `/events wildhunt` → a fenevad életben marad a lejáratig vagy a
      leöléséig (nem „megszökik" azonnal).
- [ ] **Idegen NPC marad:** `/events stranger` → az NPC a beállított ideig áll, nem tűnik el
      azonnal és nem spawnol újra ismételten.
- [ ] **Halál azonnal felszabadít:** öld meg a Vad Hajsza fenevadát → az esemény azonnal zárul,
      és rögtön indítható új nagy esemény (nem kell megvárni a watchdogot).
- [ ] **Minion-cap azonnal ürül:** idézd meg a maximális minion-számot, öld meg az egyiket →
      1-2 másodpercen belül idézhető új (nem GC-függő).
- [ ] **Watchdog (beragadt esemény):** ha egy esemény `isActive()`-ja beragadna, a
      `world-events.orchestration.max-active-minutes` (alap 60) lejárta után a többi nagy
      esemény ismét indulhat magától — nem kell szerver-újraindítás.

### PR-review javítások: tartósság-bizonyíték és megszakító-gyógyulás (2026-07-26)
- [ ] **Sikertelen playerdata-mentés nem zárja a naplót (P0):** a `MarketManager.persistPlayer`
      hibája után a `market-journal.yml` bejegyzés NYITVA marad (logban figyelmeztetés).
      Teszt: listázz, majd nézd meg a naplót — ha a mentés bukott, a bejegyzés ott van, és a
      következő indulás a jelzőből dönt. **Sosem fordulhat elő, hogy a tétel a lemezen van ÉS a
      tárgy a kézben (dupe), vagy hogy a piac már nem tartozik a tárggyal, de a játékos sem
      mentette el (vesztés).**
- [ ] **Sikertelen pénz-helyreállítás újrapróbálható (P1):** crash után olyan helyreállítással,
      ahol a levonás nem fedezett → a log SEVERE sort ad, a napló-bejegyzés MEGMARAD, és a
      KÖVETKEZŐ indulás újra megpróbálja (eddig véglegesen legitimálódott az eltérő egyenleg).
- [ ] **Sérült market.yml nem dob el tételt (P1):** rontsd el egy tétel valutáját/tárgyát, egy
      eladó-UUID-t, vagy hagyj zárolt licitet licitáló nélkül → az indulás MEGSZAKAD karantén-
      másolattal (eddig némán átugrotta, és a tárgy/escrow elveszett).
- [ ] **WorldGuard-reload után a híd magához tér (P1):** `/wg reload` (vagy a WG plugin
      újratöltése) közben futtass claim-ellenőrzést → a híd hibát logol és 60 mp-re kikapcsol,
      **utána viszont ÚJRA feloldja a WG-hivatkozásokat**, és ismét helyesen válaszol. Nem marad
      hibás a szerver-újraindításig. Amíg hibás, a `/claim` elutasít (fail-closed).
- [ ] **Spell-provenancia migráció szint-helyes (P2):** olyan játékossal, aki egy MAGASABB
      szintű kaszt-spellt talentből/specből kapott meg, a backfill NEM ír rá `BASE` forrást →
      a talent/spec elvesztésekor a spell is elmegy (eddig véglegesen nála maradt). Ellenőrizd
      azt is, hogy idegen kaszt specének táblája nem attribútál (Varázslónak nincs Harcos-spece).
- [ ] **Közösségi jutalom crash-biztos (P1):** állítsd a célt 1-re, teljesítsd, és a kifizetés
      pillanatában `kill -9`. Újraindításkor a log „N függő kifizetés — újrajátszás" sort ad,
      és a kincstár/szezon/buff jutalom MEGÉRKEZIK (eddig a nyugta miatt az esemény nem
      játszódott újra, a jutalom pedig elmaradt). A jutalom nem duplázódik, ha nem volt crash.

### PR-re-review javítások: tanú-megtartás, szigorú betöltés, idempotens kifizetés (2026-07-26)
- [ ] **Commitolt helyreállítás tanúja megmarad:** ha egy commitolt BUY/BID/SETTLE javítása
      nem sikerül (fedezetlen levonás), a bejegyzés nyitva marad, ÉS a tanúja bent marad a
      `market.yml`-ben. Teszt: a következő indulás **előre** (a commitolt célértékre) próbálja
      újra, nem visszaforgat. Ellenőrzés: a `committed-txn` lista tartalmazza a nyitott
      bejegyzés azonosítóját.
- [ ] **Várólistás tárgylista szigorú:** rontsd el egy `pending-deliveries` lista EGY elemét
      (pl. írj bele stringet), indíts újra → az indulás MEGSZAKAD karantén-másolattal.
      Eddig az az egy elem nyomtalanul eltűnt, a lista többi tárgya betöltődött.
- [ ] **Kétirányú licit-invariáns:** licitáló ZÁROLT ÖSSZEG NÉLKÜL, illetve nem véges/negatív
      ár, licit vagy buy-out érték szintén indulás-megszakítást ad.
- [ ] **WG-híd nincs fail-open ablak újra-feloldás közben:** WorldGuard-reload alatt futtass
      párhuzamosan claim-ellenőrzést → **egyetlen** `/claim` sem mehet át azon, hogy a híd
      félig-kész állapotot mutat („nincs régió"). Amíg nem tud válaszolni, elutasít.
- [ ] **Bukott újra-feloldás után is próbálkozik:** ha a WG épp reload közben van és az újra-
      feloldás elbukik, a log újabb 60 mp-es ablakot jelez, és a híd a következő ablakban
      ISMÉT próbál — nem marad hallgatásban a szerver-újraindításig.
- [ ] **Közösségi kifizetés PONTOSAN egyszer (idempotens):** teljesítsd a célt, majd
      `kill -9` a kifizetés közben. Újraindításkor a log „függő kifizetés — újrajátszás" sort
      ad, és a kincstár/szezon-pont **pontosan egyszer** növekszik (se elmaradás, se duplázás).
      Ellenőrzés: `faction-treasury.yml` → `applied-grants`, `season.yml` → `season.applied-grants`
      tartalmazza a `community:<id>:treasury:<FRAKCIÓ>` / `:season:` azonosítót; ismételt
      indítás után az összeg NEM nő tovább.
- [ ] **Részleges kifizetés pótlódik:** ha a kincstár-írás sikerül, a szezon-írás nem (pl.
      írásvédett `season.yml`), a log RÉSZBEN-sikert jelez, a bejegyzés MARAD, és a jogosultság
      visszaadása után az újrajátszás CSAK a szezon-pontot pótolja — a kincstár nem kap újra.
- [ ] **Config-változás nem téríti el a replayt:** hagyj függő kifizetést, majd írd át (vagy
      töröld) a cél `reward-treasury` értékét a configban, és indíts újra → a kifizetés a
      MENTETT pillanatkép szerint történik (a törölt cél jutalma sem veszik el).

### Csodálatos Bingulus — örökös DEV item

Teszt-tulajdonos UUID: `eb80c20f-092a-4d76-bd44-d168c91ea9e2`.

Gyorsítás teszthez:

```text
/icesmp config set dev-items.csodalatos_bingulus.reward-interval-seconds 10
/icesmp reload
/iceitem dev csodalatos_bingulus 1 <tulajdonos>
```

- [ ] A tárgy csak a konfigurált UUID-jű játékosnak adható.
- [ ] Saját inventoryn belül mozgatható, de nem dobható el és nem rakható ládába/ender chestbe.
- [ ] Halál után visszatér; más játékos nem tudja megtartani vagy felvenni.
- [ ] Offline állapotban és a tárgy hiányakor nem halad a jutalomóra.
- [ ] Telt inventorynál a kisorsolt jutalom várakozik, nem esik a földre és nem sorsolódik újra.
- [ ] A vanilla, `unique:`, `recipe:` és `blueprint:` jutalmak működnek.
- [ ] Két másolt Bingulus sem gyorsítja a jutalmazást; a manager egy hiteles példányt hagy meg.
- [ ] Restart után megmarad a rész-progressz, a pending jutalom és a pity-számláló.

### 0. stabilitási fázis — két-régiós Folia ellenőrzés (2026-07-28)

Két teszter álljon egymástól távol (külön régió, ~200+ blokk), egy pedig a jelenség
helyszínén. A konzolt végig figyeld `region`/`scheduler`/`IllegalStateException` stacktrace-re.

- [ ] **Totem régióhatáron:** sámán rakjon totemet régióhatár közelébe úgy, hogy a
      hatósugárba eső mob/játékos a szomszéd régióban áll — a buff/sebzés megérkezik,
      konzol-hiba nélkül. Crash-teszt: aktív totemnél öld meg a szervert (kill -9),
      restart után a totem-állvány NEM marad a világban.
- [ ] **Pet távoli célponton:** a gazdi lövedékkel sebezzen távoli (másik régiós) mobot —
      a pet nem dob hibát; ha a cél átfut a határon, a következő tickben újra felveszi.
- [ ] **Világboss ZONE-special:** a bossnál a zóna-telegráf régióhatáron álló játékossal
      is hibamentes.
- [ ] **Párt-jutalom régióhatáron:** két párttag KÜLÖN régióban, sugáron belül — kincs
      nyitásakor / mob-ölésnél MINDKETTEN kapnak (a korábbi néma kimaradás megszűnt).
- [ ] **Hazatérés-rituálé kudarca:** érvénytelen fővárosnál / tiltott teleportnál a
      2 ender pearl és a cooldown MEGMARAD, hibaüzenettel; sikeres útnál egyszer fogy.
- [ ] **Reload-teljesség:** recept-ár módosítás + `/icesmp reload` → a recept-könyv az
      új árat mutatja restart nélkül; `spell-vfx.enabled: false` + reload → a spell-VFX
      azonnal eltűnik.
- [ ] **Config-őrök:** `/icesmp config set` NaN-ra hibatűrő (nem lesz ingyen bolt-item);
      egy bemásolt `zz-backup.yml` reload után warningot ad és NEM ír felül kulcsot.
- [ ] **Sérült állapotfájl:** egy kézzel elrontott UUID a factions.yml-ben → az indulás
      MEGÁLL, a fájl karanténba kerül, semmi nem íródik felül.
- [ ] **Gazdasági crash-teszt (H-ECON-001 — a WAL-kör átvételi tesztje, addig VÁRHATÓAN BUKIK):** `/bank kivet 100` után AZONNAL öld meg a
      szervert (kill -9) — restart után az egyenleg a levont érték, dupla pénz nincs
      (a veret a crash miatt veszhet el, de nem duplázódik). Ugyanez befizetésre:
      a beadott veret vagy a számlán van, vagy visszakerült — sosem tűnik el nyomtalanul.
- [ ] **Fizetős claim crash-teszt:** claim-vásárlás után azonnali kill — restart után a
      levonás ÉS a claim együtt van meg (vagy együtt hiányzik), félkész állapot nincs.

### Natív moderáció — replacement scope (2026-07-28)

- [x] **AUTOMATED:** `./gradlew moderationRegressionTest` — ledger restart/expiry/revocation/history/invariánsok; invsee restart snapshot, count-preserving single-claim, claim utáni törlés, corrupt/partial/schema/count state; scheduler submit exception/null/retirement single-winner; repeating-task publish race; `/reply` quit–reconnect generációvédelem.
- [x] **AUTOMATED:** `./gradlew clean build --no-daemon --stacktrace` — teljes fordítás és regressziós lifecycle.
- [ ] **REAL FOLIA REQUIRED / RESTART TEST:** tempmute és tempban mentése, restart előtti/utáni enforcement, lejárat.
- [ ] **NEGATIVE TEST / RESTART TEST:** hibás, részleges, duplikált és ellentmondó `moderation-data.yml` fail-closed karantén.
- [ ] **NEGATIVE TEST:** írásvédett data könyvtár / lemezhiba — mutáció rollback, nincs hamis sikerüzenet, plugin fail-closed.
- [ ] **REAL FOLIA REQUIRED:** `/msg` két eltérő régióban; sender/recipient quit–reconnect a kézbesítési hop közben; a régi callback nem épít `/reply` linket az új sessionhez; SocialSpy státuszok.
- [ ] **PERMISSION TEST:** minden akció, GUI-gomb és tab completion külön permissionnel; invsee read/edit szétválasztás.
- [ ] **REAL FOLIA REQUIRED / RELOAD TEST:** vanish relog, world change, reload, tablist viewer-filter, nametag, online/MOTD count.
- [ ] **NEGATIVE TEST:** vanished admin mob target, item pickup, incoming/outgoing damage és interaction kapcsolók.
- [ ] **REAL FOLIA REQUIRED:** invsee fő/ender read és edit eltérő régiókban; target és admin quit/kick minden scheduler-hopnál; azonnal retired/null submit fault-injectionnél nincs elvesző vagy duplikált stack és nincs stale refresh task.
- [ ] **RELOAD TEST:** aktív invsee session reload/disable alatt bezár; admission lezárás után minden befogadott transfer drainelődik; final save után restart/reconnect restore; sikeres return utáni kontrollált újraindítás nem adja vissza ismét a rekordot.
- [ ] **NEGATIVE TEST:** `/offlinetp` hiányzó adat, nem betöltött vagy UUID-ben eltérő világ; nincs szinkron world/chunk load.

A fenti kézi pontok teljesítése előtt az SModeration/InvSee++ eltávolíthatósága **nem végleges**.


## Gépileg ellenőrzött interface-markerek

A részletes gépi interface-adatok a CI artifactban találhatók.

<!-- icesmp-doc-id: alias.achievements.ach -->
<!-- icesmp-doc-id: alias.achievements.eleresek -->
<!-- icesmp-doc-id: alias.adomany.adomanylada -->
<!-- icesmp-doc-id: alias.adomany.donate -->
<!-- icesmp-doc-id: alias.bank.vault -->
<!-- icesmp-doc-id: alias.bank.wallet -->
<!-- icesmp-doc-id: alias.bestiarium.bestiary -->
<!-- icesmp-doc-id: alias.bestiarium.lajstrom -->
<!-- icesmp-doc-id: alias.bounty.fejvadasz -->
<!-- icesmp-doc-id: alias.bounty.korozes -->
<!-- icesmp-doc-id: alias.ceh.gild -->
<!-- icesmp-doc-id: alias.ceh.guild -->
<!-- icesmp-doc-id: alias.claim.birtok -->
<!-- icesmp-doc-id: alias.class.job -->
<!-- icesmp-doc-id: alias.class.kaszt -->
<!-- icesmp-doc-id: alias.crate.crates -->
<!-- icesmp-doc-id: alias.crate.ladak -->
<!-- icesmp-doc-id: alias.currency.eco -->
<!-- icesmp-doc-id: alias.currency.money -->
<!-- icesmp-doc-id: alias.daily.napi -->
<!-- icesmp-doc-id: alias.emlek.emlekek -->
<!-- icesmp-doc-id: alias.emlek.memory -->
<!-- icesmp-doc-id: alias.events.esemeny -->
<!-- icesmp-doc-id: alias.events.event -->
<!-- icesmp-doc-id: alias.exchangeboard.arfolyamtabla -->
<!-- icesmp-doc-id: alias.exchangeboard.ratesboard -->
<!-- icesmp-doc-id: alias.faction.f -->
<!-- icesmp-doc-id: alias.iceitem.icegive -->
<!-- icesmp-doc-id: alias.iceitem.iitem -->
<!-- icesmp-doc-id: alias.icesmp.ismp -->
<!-- icesmp-doc-id: alias.kem.spy -->
<!-- icesmp-doc-id: alias.komp.ferry -->
<!-- icesmp-doc-id: alias.kronika.chronicle -->
<!-- icesmp-doc-id: alias.leaderboard.lb -->
<!-- icesmp-doc-id: alias.leaderboard.rangsor -->
<!-- icesmp-doc-id: alias.leaderboard.top -->
<!-- icesmp-doc-id: alias.lore.kodex -->
<!-- icesmp-doc-id: alias.market.ah -->
<!-- icesmp-doc-id: alias.market.piac -->
<!-- icesmp-doc-id: alias.menu.hub -->
<!-- icesmp-doc-id: alias.menu.m -->
<!-- icesmp-doc-id: alias.moderation.mod -->
<!-- icesmp-doc-id: alias.npcbind.npckotes -->
<!-- icesmp-doc-id: alias.parbaj.duel -->
<!-- icesmp-doc-id: alias.parkour.palya -->
<!-- icesmp-doc-id: alias.parkour.trial -->
<!-- icesmp-doc-id: alias.party.p -->
<!-- icesmp-doc-id: alias.party.parti -->
<!-- icesmp-doc-id: alias.pet.companion -->
<!-- icesmp-doc-id: alias.pet.tars -->
<!-- icesmp-doc-id: alias.profession.prof -->
<!-- icesmp-doc-id: alias.profession.szakma -->
<!-- icesmp-doc-id: alias.profile.char -->
<!-- icesmp-doc-id: alias.profile.karakter -->
<!-- icesmp-doc-id: alias.profile.status -->
<!-- icesmp-doc-id: alias.quest.kuldetes -->
<!-- icesmp-doc-id: alias.quest.quests -->
<!-- icesmp-doc-id: alias.relic.relics -->
<!-- icesmp-doc-id: alias.relic.relikvia -->
<!-- icesmp-doc-id: alias.reply.r -->
<!-- icesmp-doc-id: alias.report.bejelent -->
<!-- icesmp-doc-id: alias.soulforge.lelekkovacs -->
<!-- icesmp-doc-id: alias.souls.lelek -->
<!-- icesmp-doc-id: alias.souls.soul -->
<!-- icesmp-doc-id: alias.spec.specializacio -->
<!-- icesmp-doc-id: alias.spec.specialization -->
<!-- icesmp-doc-id: alias.spell.mastery -->
<!-- icesmp-doc-id: alias.spell.mesterseg -->
<!-- icesmp-doc-id: alias.spell.spells -->
<!-- icesmp-doc-id: alias.spellbook.konyv -->
<!-- icesmp-doc-id: alias.spellbook.sb -->
<!-- icesmp-doc-id: alias.spellbook.varazskonyv -->
<!-- icesmp-doc-id: alias.suttogas.sutt -->
<!-- icesmp-doc-id: alias.szakmacel.weeklygoal -->
<!-- icesmp-doc-id: alias.talent.talentfa -->
<!-- icesmp-doc-id: alias.talent.talents -->
<!-- icesmp-doc-id: alias.tanacs.council -->
<!-- icesmp-doc-id: alias.territory.terulet -->
<!-- icesmp-doc-id: alias.vanish.v -->
<!-- icesmp-doc-id: command.achievements -->
<!-- icesmp-doc-id: command.adomany -->
<!-- icesmp-doc-id: command.afk -->
<!-- icesmp-doc-id: command.ban -->
<!-- icesmp-doc-id: command.bank -->
<!-- icesmp-doc-id: command.bestiarium -->
<!-- icesmp-doc-id: command.bounty -->
<!-- icesmp-doc-id: command.ceh -->
<!-- icesmp-doc-id: command.claim -->
<!-- icesmp-doc-id: command.class -->
<!-- icesmp-doc-id: command.crate -->
<!-- icesmp-doc-id: command.currency -->
<!-- icesmp-doc-id: command.daily -->
<!-- icesmp-doc-id: command.emlek -->
<!-- icesmp-doc-id: command.events -->
<!-- icesmp-doc-id: command.exchangeboard -->
<!-- icesmp-doc-id: command.faction -->
<!-- icesmp-doc-id: command.history -->
<!-- icesmp-doc-id: command.hud -->
<!-- icesmp-doc-id: command.iceitem -->
<!-- icesmp-doc-id: command.icesmp -->
<!-- icesmp-doc-id: command.invsee -->
<!-- icesmp-doc-id: command.kem -->
<!-- icesmp-doc-id: command.kick -->
<!-- icesmp-doc-id: command.komp -->
<!-- icesmp-doc-id: command.kronika -->
<!-- icesmp-doc-id: command.leaderboard -->
<!-- icesmp-doc-id: command.lore -->
<!-- icesmp-doc-id: command.market -->
<!-- icesmp-doc-id: command.menu -->
<!-- icesmp-doc-id: command.moderation -->
<!-- icesmp-doc-id: command.msg -->
<!-- icesmp-doc-id: command.mute -->
<!-- icesmp-doc-id: command.npcbind -->
<!-- icesmp-doc-id: command.offlinetp -->
<!-- icesmp-doc-id: command.parbaj -->
<!-- icesmp-doc-id: command.parkour -->
<!-- icesmp-doc-id: command.party -->
<!-- icesmp-doc-id: command.pet -->
<!-- icesmp-doc-id: command.profession -->
<!-- icesmp-doc-id: command.profile -->
<!-- icesmp-doc-id: command.punishments -->
<!-- icesmp-doc-id: command.quest -->
<!-- icesmp-doc-id: command.relic -->
<!-- icesmp-doc-id: command.reply -->
<!-- icesmp-doc-id: command.report -->
<!-- icesmp-doc-id: command.reports -->
<!-- icesmp-doc-id: command.sinner -->
<!-- icesmp-doc-id: command.sit -->
<!-- icesmp-doc-id: command.socialspy -->
<!-- icesmp-doc-id: command.soulforge -->
<!-- icesmp-doc-id: command.souls -->
<!-- icesmp-doc-id: command.spec -->
<!-- icesmp-doc-id: command.spell -->
<!-- icesmp-doc-id: command.spellbook -->
<!-- icesmp-doc-id: command.stats -->
<!-- icesmp-doc-id: command.suttogas -->
<!-- icesmp-doc-id: command.szakmacel -->
<!-- icesmp-doc-id: command.talent -->
<!-- icesmp-doc-id: command.tanacs -->
<!-- icesmp-doc-id: command.tell -->
<!-- icesmp-doc-id: command.tempban -->
<!-- icesmp-doc-id: command.territory -->
<!-- icesmp-doc-id: command.unban -->
<!-- icesmp-doc-id: command.unmute -->
<!-- icesmp-doc-id: command.vanish -->
<!-- icesmp-doc-id: command.w -->
<!-- icesmp-doc-id: command.warn -->
<!-- icesmp-doc-id: permission.icesmp.admin -->
<!-- icesmp-doc-id: permission.icesmp.admin.all -->
<!-- icesmp-doc-id: permission.icesmp.admin.config -->
<!-- icesmp-doc-id: permission.icesmp.admin.crate -->
<!-- icesmp-doc-id: permission.icesmp.admin.currency -->
<!-- icesmp-doc-id: permission.icesmp.admin.events -->
<!-- icesmp-doc-id: permission.icesmp.admin.exchangeboard -->
<!-- icesmp-doc-id: permission.icesmp.admin.faction -->
<!-- icesmp-doc-id: permission.icesmp.admin.inspect -->
<!-- icesmp-doc-id: permission.icesmp.admin.item -->
<!-- icesmp-doc-id: permission.icesmp.admin.job -->
<!-- icesmp-doc-id: permission.icesmp.admin.moderation -->
<!-- icesmp-doc-id: permission.icesmp.admin.npc -->
<!-- icesmp-doc-id: permission.icesmp.admin.parkour -->
<!-- icesmp-doc-id: permission.icesmp.admin.profession -->
<!-- icesmp-doc-id: permission.icesmp.admin.quest -->
<!-- icesmp-doc-id: permission.icesmp.admin.relic -->
<!-- icesmp-doc-id: permission.icesmp.admin.reload -->
<!-- icesmp-doc-id: permission.icesmp.admin.sinner -->
<!-- icesmp-doc-id: permission.icesmp.admin.spec -->
<!-- icesmp-doc-id: permission.icesmp.admin.territory -->
<!-- icesmp-doc-id: permission.icesmp.admin.territory.bypass -->
<!-- icesmp-doc-id: permission.icesmp.admin.war -->
<!-- icesmp-doc-id: permission.icesmp.crate.ritka -->
<!-- icesmp-doc-id: permission.icesmp.crate.use -->
<!-- icesmp-doc-id: permission.icesmp.currency.admin -->
<!-- icesmp-doc-id: permission.icesmp.faction.admin -->
<!-- icesmp-doc-id: permission.icesmp.job.admin -->
<!-- icesmp-doc-id: permission.icesmp.message -->
<!-- icesmp-doc-id: permission.icesmp.moderation.ban -->
<!-- icesmp-doc-id: permission.icesmp.moderation.gui -->
<!-- icesmp-doc-id: permission.icesmp.moderation.history -->
<!-- icesmp-doc-id: permission.icesmp.moderation.inventory.edit -->
<!-- icesmp-doc-id: permission.icesmp.moderation.inventory.read -->
<!-- icesmp-doc-id: permission.icesmp.moderation.kick -->
<!-- icesmp-doc-id: permission.icesmp.moderation.mute -->
<!-- icesmp-doc-id: permission.icesmp.moderation.offlinetp -->
<!-- icesmp-doc-id: permission.icesmp.moderation.socialspy -->
<!-- icesmp-doc-id: permission.icesmp.moderation.vanish -->
<!-- icesmp-doc-id: permission.icesmp.moderation.vanish.see -->
<!-- icesmp-doc-id: permission.icesmp.moderation.warn -->
<!-- icesmp-doc-id: permission.icesmp.relic.admin -->
<!-- icesmp-doc-id: permission.icesmp.sit -->
<!-- icesmp-doc-id: permission.icesmp.territory.builder -->
<!-- icesmp-doc-id: route-alias.ceh.befizet-sszeg-9367a8fe72.deposit-c3b9fb78 -->
<!-- icesmp-doc-id: route-alias.ceh.elfogad-6f392ee845.accept-c125d039 -->
<!-- icesmp-doc-id: route-alias.ceh.elhagy-238de74a27.leave-af193190 -->
<!-- icesmp-doc-id: route-alias.ceh.kirug-ismert-j-t-kos-f3118ea29e.kick-0db10f2c -->
<!-- icesmp-doc-id: route-alias.ceh.letrehoz-n-v-e159c0c9de.create-fa8847b0 -->
<!-- icesmp-doc-id: route-alias.ceh.lista-5ae7861e0e.list-a330395c -->
<!-- icesmp-doc-id: route-alias.ceh.meghiv-online-j-t-kos-643986349e.invite-5014f9af -->
<!-- icesmp-doc-id: route-alias.claim.wand-852c619412.palca-0c18f304 -->
<!-- icesmp-doc-id: route-alias.events.abundance-b4d9893c3e.boseg-0e3fff0a -->
<!-- icesmp-doc-id: route-alias.events.ambient-4875fc8cc4.hangulat-77d7303f -->
<!-- icesmp-doc-id: route-alias.events.archeology-59c0c86dd5.regeszet-e40487bd -->
<!-- icesmp-doc-id: route-alias.events.blood-moon-22be7052e8.bloodmoon-f05aee90 -->
<!-- icesmp-doc-id: route-alias.events.blood-moon-start-ab72f4e2a5.bloodmoon-f05aee90 -->
<!-- icesmp-doc-id: route-alias.events.blood-moon-stop-c363352a8d.bloodmoon-f05aee90 -->
<!-- icesmp-doc-id: route-alias.events.caravan-a8813234aa.karavan-d92937d2 -->
<!-- icesmp-doc-id: route-alias.events.caravan-arrive-a5dc4d50fc.karavan-d92937d2 -->
<!-- icesmp-doc-id: route-alias.events.caravan-arrive-a5dc4d50fc.start-cced28c6 -->
<!-- icesmp-doc-id: route-alias.events.caravan-depart-9193099e2e.karavan-d92937d2 -->
<!-- icesmp-doc-id: route-alias.events.caravan-depart-9193099e2e.stop-6c45cb72 -->
<!-- icesmp-doc-id: route-alias.events.challenge-cc41714193.kihivas-1ab58bc8 -->
<!-- icesmp-doc-id: route-alias.events.corruption-31120a4dec.rontas-b768f13b -->
<!-- icesmp-doc-id: route-alias.events.cultists-835d9785cd.kultistak-613a2e18 -->
<!-- icesmp-doc-id: route-alias.events.escort-7bf8c23aa4.kiseret-ca97308f -->
<!-- icesmp-doc-id: route-alias.events.gathering-4d330f218c.buff-048b41bf -->
<!-- icesmp-doc-id: route-alias.events.gathering-4d330f218c.gyujtes-02ee978a -->
<!-- icesmp-doc-id: route-alias.events.invasion-ad9a636b3a.invazio-9f7fa9d7 -->
<!-- icesmp-doc-id: route-alias.events.spawnpoint-add-world-boss-escort-caravan-cultists-any-id-8274b4ad60.spawnpont-f9cb59a1 -->
<!-- icesmp-doc-id: route-alias.events.spawnpoint-list-79877fb1e0.lista-0296c43c -->
<!-- icesmp-doc-id: route-alias.events.spawnpoint-list-79877fb1e0.spawnpont-f9cb59a1 -->
<!-- icesmp-doc-id: route-alias.events.spawnpoint-remove-id-636b60fe54.spawnpont-f9cb59a1 -->
<!-- icesmp-doc-id: route-alias.events.spawnpoint-remove-id-636b60fe54.torol-3bf98227 -->
<!-- icesmp-doc-id: route-alias.events.stranger-8113ee3373.idegen-d9f72b44 -->
<!-- icesmp-doc-id: route-alias.events.treasure-eb1b8c1835.kincs-a54cd157 -->
<!-- icesmp-doc-id: route-alias.events.wild-hunt-33733e2193.hajsza-7abed88e -->
<!-- icesmp-doc-id: route-alias.events.wild-hunt-33733e2193.wildhunt-972cdfce -->
<!-- icesmp-doc-id: route-alias.events.worldboss-b1d9efaf9c.boss-a5e7c002 -->
<!-- icesmp-doc-id: route-alias.events.worldboss-b1d9efaf9c.world-boss-33862e12 -->
<!-- icesmp-doc-id: route-alias.leaderboard.level-wealth-raidkills-ccd3ef3f77.kills-a52ff550 -->
<!-- icesmp-doc-id: route-alias.leaderboard.level-wealth-raidkills-ccd3ef3f77.raid-263dae9a -->
<!-- icesmp-doc-id: route-alias.leaderboard.level-wealth-raidkills-ccd3ef3f77.vagyon-7f1c4815 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.blue-16477688 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.caldestera-16f23f3d -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.cryghaliris-4e5bfdac -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.dark-e6bb5689 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.doom-910ecd3e -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.elet-fa-fad4b021 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.eletfa-d1b9537e -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.folyo-13e86b39 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.gyokerek-fc33e873 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.karhozat-d4bde78b -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.kek-b794385f -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.kitaszitott-12211f29 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.konyv-b13b3be4 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.korszak-46a05f95 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.kronika-lore-ee554773 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.melyseg-nepe-ced305f2 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.mortengrad-f0de9006 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.neutral-7e2372f4 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.o-caldestera-c62b7f98 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.ocaldestera-acef24c1 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.olethropyla-8d139239 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.perinfernicitas-15766dc7 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.piros-ef14443b -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.red-b1f51a51 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.ryanora-d6567440 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.semleges-49399cab -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.sotet-c5ee87e0 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.suttogas-157ec9e3 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.thanaopolis-b7ceb138 -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.torpok-4cefc26e -->
<!-- icesmp-doc-id: route-alias.lore.t-ma-e42a542b71.whisper-ba795559 -->
<!-- icesmp-doc-id: route-alias.parbaj.elfogad-8ed448f0cf.accept-c125d039 -->
<!-- icesmp-doc-id: route-alias.parbaj.elutasit-7ac397cfb7.decline-fd5f8cbe -->
<!-- icesmp-doc-id: route-alias.parbaj.kihiv-online-j-t-kos-77340a6a59.challenge-2dd00bd7 -->
<!-- icesmp-doc-id: route-alias.pet.stance-aktiv-passziv-marad-fb3ceee1b8.active-96879611 -->
<!-- icesmp-doc-id: route-alias.pet.stance-aktiv-passziv-marad-fb3ceee1b8.passive-a7f1d7bc -->
<!-- icesmp-doc-id: route-alias.pet.stance-aktiv-passziv-marad-fb3ceee1b8.stay-39be1528 -->
<!-- icesmp-doc-id: route-alias.profession.blueprint-online-j-t-kos-recept-id-75d1da1621.tervrajz-9069daad -->
<!-- icesmp-doc-id: route-alias.profession.recipes-d99cf16c6c.book-92719fe0 -->
<!-- icesmp-doc-id: route-alias.profession.recipes-d99cf16c6c.receptek-326be994 -->
<!-- icesmp-doc-id: route-alias.quest.log-6d94f9ac8a.gui-04700787 -->
<!-- icesmp-doc-id: route-alias.quest.log-6d94f9ac8a.napl-4e4f85c3 -->
<!-- icesmp-doc-id: route-alias.quest.log-6d94f9ac8a.naplo-282c9fad -->
<!-- icesmp-doc-id: route-alias.reply.zenet-612b6f0ac3.r-454349e4 -->
<!-- icesmp-doc-id: route-alias.soulforge.fejleszt-elet-sebzes-letszam-e24f7f80ec.l-tsz-m-cbd2def0 -->
<!-- icesmp-doc-id: route-alias.soulforge.fejleszt-elet-sebzes-letszam-e24f7f80ec.let-978d3f9e -->
<!-- icesmp-doc-id: route-alias.soulforge.fejleszt-elet-sebzes-letszam-e24f7f80ec.sebz-s-994e83f7 -->
<!-- icesmp-doc-id: route-alias.suttogas.v-d-online-j-t-kos-cd4a9b4db2.accuse-603ec1f3 -->
<!-- icesmp-doc-id: route-alias.suttogas.v-d-online-j-t-kos-cd4a9b4db2.vad-651bfad0 -->
<!-- icesmp-doc-id: route-alias.territory.clearpoints-a3c52199a3.clear-913a4cb9 -->
<!-- icesmp-doc-id: route-alias.territory.pos-83500aed80.point-251fecd5 -->
<!-- icesmp-doc-id: route-alias.territory.tp-id-809cb14024.teleport-e99f7ff1 -->
<!-- icesmp-doc-id: route-alias.territory.wand-de21264e40.palca-0c18f304 -->
<!-- icesmp-doc-id: route.achievements.root-4ddb61bbff -->
<!-- icesmp-doc-id: route.adomany.add-b01aecc6b5 -->
<!-- icesmp-doc-id: route.adomany.root-896218f922 -->
<!-- icesmp-doc-id: route.afk.root-5475cc764f -->
<!-- icesmp-doc-id: route.ban.j-t-kos-ok-d58e60fdc2 -->
<!-- icesmp-doc-id: route.bank.balance-0ce79a966f -->
<!-- icesmp-doc-id: route.bank.deposit-d3026e7f77 -->
<!-- icesmp-doc-id: route.bank.root-5546d57526 -->
<!-- icesmp-doc-id: route.bank.withdraw-red-blue-neutral-dark-sszeg-837b26fa49 -->
<!-- icesmp-doc-id: route.bestiarium.root-86cedece7b -->
<!-- icesmp-doc-id: route.bounty.root-930183e5f0 -->
<!-- icesmp-doc-id: route.ceh.befizet-sszeg-9367a8fe72 -->
<!-- icesmp-doc-id: route.ceh.elfogad-6f392ee845 -->
<!-- icesmp-doc-id: route.ceh.elhagy-238de74a27 -->
<!-- icesmp-doc-id: route.ceh.info-b76de437dd -->
<!-- icesmp-doc-id: route.ceh.kirug-ismert-j-t-kos-f3118ea29e -->
<!-- icesmp-doc-id: route.ceh.letrehoz-n-v-e159c0c9de -->
<!-- icesmp-doc-id: route.ceh.lista-5ae7861e0e -->
<!-- icesmp-doc-id: route.ceh.meghiv-online-j-t-kos-643986349e -->
<!-- icesmp-doc-id: route.ceh.root-2a787591e2 -->
<!-- icesmp-doc-id: route.claim.admin-unclaim-3a49df98f3 -->
<!-- icesmp-doc-id: route.claim.area-197d175442 -->
<!-- icesmp-doc-id: route.claim.claim-20ce366db5 -->
<!-- icesmp-doc-id: route.claim.extend-up-down-42379080c0 -->
<!-- icesmp-doc-id: route.claim.help-92f62de5b6 -->
<!-- icesmp-doc-id: route.claim.info-5761497c96 -->
<!-- icesmp-doc-id: route.claim.list-dc01b5c04b -->
<!-- icesmp-doc-id: route.claim.pos1-b0e536ddfc -->
<!-- icesmp-doc-id: route.claim.pos2-4b996c6d87 -->
<!-- icesmp-doc-id: route.claim.show-577edd5549 -->
<!-- icesmp-doc-id: route.claim.trust-online-j-t-kos-b9a427c2cf -->
<!-- icesmp-doc-id: route.claim.trustgui-6d7c53398e -->
<!-- icesmp-doc-id: route.claim.unclaim-921e28c61a -->
<!-- icesmp-doc-id: route.claim.untrust-online-j-t-kos-b0b336ee22 -->
<!-- icesmp-doc-id: route.claim.wand-852c619412 -->
<!-- icesmp-doc-id: route.class.addxp-online-j-t-kos-mennyis-g-4492d1559e -->
<!-- icesmp-doc-id: route.class.admin-resetcd-online-j-t-kos-6a335f757f -->
<!-- icesmp-doc-id: route.class.admin-resetclass-online-j-t-kos-012226e88e -->
<!-- icesmp-doc-id: route.class.admin-resetskills-online-j-t-kos-d53fd5bc5c -->
<!-- icesmp-doc-id: route.class.admin-unlockallskills-online-j-t-kos-c16e8031f8 -->
<!-- icesmp-doc-id: route.class.givecatalyst-online-j-t-kos-9b2c72e248 -->
<!-- icesmp-doc-id: route.class.listspells-8ae1b5d52f -->
<!-- icesmp-doc-id: route.class.root-4bc3de9620 -->
<!-- icesmp-doc-id: route.class.setxp-online-j-t-kos-mennyis-g-051bd470b0 -->
<!-- icesmp-doc-id: route.class.status-online-j-t-kos-07f230c02d -->
<!-- icesmp-doc-id: route.class.unlockspell-online-j-t-kos-spell-id-91cd823b26 -->
<!-- icesmp-doc-id: route.crate.buy-l-da-id-darab-bc58b9f983 -->
<!-- icesmp-doc-id: route.crate.give-online-j-t-kos-l-da-id-darab-eb82d50ae8 -->
<!-- icesmp-doc-id: route.crate.info-l-da-id-9ff0385057 -->
<!-- icesmp-doc-id: route.crate.list-aa44f4dfea -->
<!-- icesmp-doc-id: route.crate.preview-l-da-id-b56ef1b2d2 -->
<!-- icesmp-doc-id: route.crate.remove-2c7a16650f -->
<!-- icesmp-doc-id: route.crate.resetstats-j-t-kos-uuid-l-da-id-all-6cafe1d747 -->
<!-- icesmp-doc-id: route.crate.root-4df4fe3c03 -->
<!-- icesmp-doc-id: route.crate.set-l-da-id-9e3e673970 -->
<!-- icesmp-doc-id: route.crate.stats-j-t-kos-uuid-l-da-id-6f820eec2b -->
<!-- icesmp-doc-id: route.crate.status-f84c129c2f -->
<!-- icesmp-doc-id: route.currency.balance-currency-73fb29b82a -->
<!-- icesmp-doc-id: route.currency.exchange-sszeg-honnan-hov-3120de7963 -->
<!-- icesmp-doc-id: route.currency.pay-online-j-t-kos-sszeg-currency-466cf70e4d -->
<!-- icesmp-doc-id: route.currency.rates-dbef3411ec -->
<!-- icesmp-doc-id: route.currency.root-035bfb2f68 -->
<!-- icesmp-doc-id: route.currency.set-online-j-t-kos-sszeg-currency-589e786299 -->
<!-- icesmp-doc-id: route.daily.root-8ad2b380cb -->
<!-- icesmp-doc-id: route.emlek.lore-3021e093e9 -->
<!-- icesmp-doc-id: route.emlek.root-7a3664a5ca -->
<!-- icesmp-doc-id: route.emlek.spec-495f15ff26 -->
<!-- icesmp-doc-id: route.emlek.talent-c777d563f2 -->
<!-- icesmp-doc-id: route.emlek.xp-0a1396db40 -->
<!-- icesmp-doc-id: route.events.abundance-b4d9893c3e -->
<!-- icesmp-doc-id: route.events.ambient-4875fc8cc4 -->
<!-- icesmp-doc-id: route.events.archeology-59c0c86dd5 -->
<!-- icesmp-doc-id: route.events.blood-moon-22be7052e8 -->
<!-- icesmp-doc-id: route.events.blood-moon-start-ab72f4e2a5 -->
<!-- icesmp-doc-id: route.events.blood-moon-stop-c363352a8d -->
<!-- icesmp-doc-id: route.events.caravan-a8813234aa -->
<!-- icesmp-doc-id: route.events.caravan-arrive-a5dc4d50fc -->
<!-- icesmp-doc-id: route.events.caravan-depart-9193099e2e -->
<!-- icesmp-doc-id: route.events.challenge-cc41714193 -->
<!-- icesmp-doc-id: route.events.corruption-31120a4dec -->
<!-- icesmp-doc-id: route.events.cultists-835d9785cd -->
<!-- icesmp-doc-id: route.events.escort-7bf8c23aa4 -->
<!-- icesmp-doc-id: route.events.gathering-4d330f218c -->
<!-- icesmp-doc-id: route.events.intro-online-j-t-kos-88febc6e32 -->
<!-- icesmp-doc-id: route.events.invasion-ad9a636b3a -->
<!-- icesmp-doc-id: route.events.meteor-8144d5c9a3 -->
<!-- icesmp-doc-id: route.events.season-0484dad8c1 -->
<!-- icesmp-doc-id: route.events.spawnpoint-add-world-boss-escort-caravan-cultists-any-id-8274b4ad60 -->
<!-- icesmp-doc-id: route.events.spawnpoint-list-79877fb1e0 -->
<!-- icesmp-doc-id: route.events.spawnpoint-remove-id-636b60fe54 -->
<!-- icesmp-doc-id: route.events.status-3f316413cd -->
<!-- icesmp-doc-id: route.events.stranger-8113ee3373 -->
<!-- icesmp-doc-id: route.events.treasure-eb1b8c1835 -->
<!-- icesmp-doc-id: route.events.wild-hunt-33733e2193 -->
<!-- icesmp-doc-id: route.events.worldboss-b1d9efaf9c -->
<!-- icesmp-doc-id: route.exchangeboard.place-dd99f9a6f9 -->
<!-- icesmp-doc-id: route.exchangeboard.remove-238e179878 -->
<!-- icesmp-doc-id: route.faction.caravan-send-sszeg-19f36c48e1 -->
<!-- icesmp-doc-id: route.faction.donate-sszeg-f22a0cacaf -->
<!-- icesmp-doc-id: route.faction.join-frakci-3aa58cfbd1 -->
<!-- icesmp-doc-id: route.faction.king-cb0eb0b838 -->
<!-- icesmp-doc-id: route.faction.king-clear-frakci-f809a896f3 -->
<!-- icesmp-doc-id: route.faction.king-set-frakci-online-j-t-kos-31f370ac59 -->
<!-- icesmp-doc-id: route.faction.king-tax-sz-zal-k-af0668c552 -->
<!-- icesmp-doc-id: route.faction.king-vote-j-t-kos-3a5ae01125 -->
<!-- icesmp-doc-id: route.faction.leave-dd49a29a9e -->
<!-- icesmp-doc-id: route.faction.raid-c-lfrakci-ter-let-6fc1e21fb6 -->
<!-- icesmp-doc-id: route.faction.raid-join-1e2256d245 -->
<!-- icesmp-doc-id: route.faction.raid-status-f7f0ed89e6 -->
<!-- icesmp-doc-id: route.faction.root-0e119be40e -->
<!-- icesmp-doc-id: route.faction.set-j-t-kos-frakci-681dc23a49 -->
<!-- icesmp-doc-id: route.faction.treasury-daa12fc76e -->
<!-- icesmp-doc-id: route.faction.treasury-withdraw-sszeg-4c84aba263 -->
<!-- icesmp-doc-id: route.faction.war-c78150450b -->
<!-- icesmp-doc-id: route.faction.war-start-perc-de690421fb -->
<!-- icesmp-doc-id: route.faction.war-stop-23b6a56d72 -->
<!-- icesmp-doc-id: route.history.j-t-kos-oldal-28b7b11d63 -->
<!-- icesmp-doc-id: route.hud.root-98fc1cf7c6 -->
<!-- icesmp-doc-id: route.hud.toggle-frakcio-valuta-kaszt-eroforras-esemeny-csapat-mind-098d489766 -->
<!-- icesmp-doc-id: route.iceitem.dev-bingulus-id-darab-online-j-t-kos-dadc3c7c7d -->
<!-- icesmp-doc-id: route.iceitem.erszeny-pozit-v-rt-k-darab-online-j-t-kos-be048fb800 -->
<!-- icesmp-doc-id: route.iceitem.recept-recept-id-darab-online-j-t-kos-44157e0fe6 -->
<!-- icesmp-doc-id: route.iceitem.relikvia-relikvia-id-darab-online-j-t-kos-54553a15d7 -->
<!-- icesmp-doc-id: route.iceitem.root-77ef588c2d -->
<!-- icesmp-doc-id: route.iceitem.tervrajz-recept-id-darab-online-j-t-kos-4c749ad1a2 -->
<!-- icesmp-doc-id: route.iceitem.unique-unique-id-darab-online-j-t-kos-aba63b8dfc -->
<!-- icesmp-doc-id: route.icesmp.config-find-sz-vegr-szlet-9769e38c69 -->
<!-- icesmp-doc-id: route.icesmp.config-get-kulcs-ddfd01586c -->
<!-- icesmp-doc-id: route.icesmp.config-list-1dbb2c6483 -->
<!-- icesmp-doc-id: route.icesmp.config-menu-2342f17ab0 -->
<!-- icesmp-doc-id: route.icesmp.config-set-kulcs-rt-k-2048ff1985 -->
<!-- icesmp-doc-id: route.icesmp.config-unset-kulcs-66a43b357e -->
<!-- icesmp-doc-id: route.icesmp.inspect-n-v-cf976d4f92 -->
<!-- icesmp-doc-id: route.icesmp.reload-59cdd77521 -->
<!-- icesmp-doc-id: route.icesmp.root-3d9c831e41 -->
<!-- icesmp-doc-id: route.invsee.online-j-t-kos-read-edit-main-ender-8fd4819785 -->
<!-- icesmp-doc-id: route.kem.c-lfrakci-91c0c8e276 -->
<!-- icesmp-doc-id: route.kick.online-j-t-kos-ok-05104518c2 -->
<!-- icesmp-doc-id: route.komp.root-1969bea642 -->
<!-- icesmp-doc-id: route.komp.tvonal-id-c3bea2b90e -->
<!-- icesmp-doc-id: route.kronika.root-7e4a313de0 -->
<!-- icesmp-doc-id: route.leaderboard.level-wealth-raidkills-ccd3ef3f77 -->
<!-- icesmp-doc-id: route.lore.t-ma-e42a542b71 -->
<!-- icesmp-doc-id: route.market.auction-kiki-lt-si-r-ra-valuta-buyout-r-bo-r-9e5a92f7db -->
<!-- icesmp-doc-id: route.market.browse-0289c12800 -->
<!-- icesmp-doc-id: route.market.cancel-041b753cec -->
<!-- icesmp-doc-id: route.market.claim-f558873257 -->
<!-- icesmp-doc-id: route.market.ereklye-13c3460bb9 -->
<!-- icesmp-doc-id: route.market.search-sz-veg-18b2b3d8f4 -->
<!-- icesmp-doc-id: route.market.sell-r-valuta-57dd501253 -->
<!-- icesmp-doc-id: route.market.stats-fcdaa5edd9 -->
<!-- icesmp-doc-id: route.menu.root-8b07263efa -->
<!-- icesmp-doc-id: route.moderation.online-j-t-kos-6a68bc313b -->
<!-- icesmp-doc-id: route.msg.j-t-kos-zenet-59f5bb82b9 -->
<!-- icesmp-doc-id: route.mute.j-t-kos-id-tartam-v-gleges-ok-5f83de69c5 -->
<!-- icesmp-doc-id: route.npcbind.list-973f13be25 -->
<!-- icesmp-doc-id: route.npcbind.npc-bank-1ffdb9725f -->
<!-- icesmp-doc-id: route.npcbind.npc-clear-6a78aae506 -->
<!-- icesmp-doc-id: route.npcbind.npc-command-parancs-fe58b86b80 -->
<!-- icesmp-doc-id: route.npcbind.npc-exchange-51ad41d49d -->
<!-- icesmp-doc-id: route.npcbind.npc-faction-8ddac3b81f -->
<!-- icesmp-doc-id: route.npcbind.npc-quest-quest-id-314dad3eb6 -->
<!-- icesmp-doc-id: route.npcbind.npc-shop-bolt-id-2ea19e1d0e -->
<!-- icesmp-doc-id: route.npcbind.root-2e255ce8c8 -->
<!-- icesmp-doc-id: route.offlinetp.j-t-kos-1fcc8178cc -->
<!-- icesmp-doc-id: route.parbaj.elfogad-8ed448f0cf -->
<!-- icesmp-doc-id: route.parbaj.elutasit-7ac397cfb7 -->
<!-- icesmp-doc-id: route.parbaj.kihiv-online-j-t-kos-77340a6a59 -->
<!-- icesmp-doc-id: route.parbaj.root-ad3b3b75ac -->
<!-- icesmp-doc-id: route.parkour.list-89ff8ef94c -->
<!-- icesmp-doc-id: route.parkour.remove-id-3bb2785e27 -->
<!-- icesmp-doc-id: route.parkour.setfinish-id-sug-r-jutalom-e12f0390ad -->
<!-- icesmp-doc-id: route.parkour.setstart-id-n-v-873b963232 -->
<!-- icesmp-doc-id: route.parkour.start-p-lya-id-e2efe6915c -->
<!-- icesmp-doc-id: route.party.accept-6f9c636cbc -->
<!-- icesmp-doc-id: route.party.c-zenet-aba9f8fcf6 -->
<!-- icesmp-doc-id: route.party.chat-zenet-ea615e7445 -->
<!-- icesmp-doc-id: route.party.decline-8e1aef6b0a -->
<!-- icesmp-doc-id: route.party.disband-a0bbc2eb5a -->
<!-- icesmp-doc-id: route.party.help-82e4e4b65f -->
<!-- icesmp-doc-id: route.party.invite-online-j-t-kos-391290d946 -->
<!-- icesmp-doc-id: route.party.kick-tag-1f43396265 -->
<!-- icesmp-doc-id: route.party.leave-94df1da4cb -->
<!-- icesmp-doc-id: route.party.list-a76808d4f9 -->
<!-- icesmp-doc-id: route.party.promote-tag-a40ba54bf3 -->
<!-- icesmp-doc-id: route.party.root-ddd9549572 -->
<!-- icesmp-doc-id: route.party.zenet-aa13d37028 -->
<!-- icesmp-doc-id: route.pet.dismiss-0764e57f33 -->
<!-- icesmp-doc-id: route.pet.info-5b47360ae5 -->
<!-- icesmp-doc-id: route.pet.item-ef593759b5 -->
<!-- icesmp-doc-id: route.pet.menu-0a4aca2b61 -->
<!-- icesmp-doc-id: route.pet.name-n-v-1bea63e82f -->
<!-- icesmp-doc-id: route.pet.stance-aktiv-passziv-marad-fb3ceee1b8 -->
<!-- icesmp-doc-id: route.pet.summon-b4246998f6 -->
<!-- icesmp-doc-id: route.profession.addxp-online-j-t-kos-szakma-mennyis-g-34c6d265e3 -->
<!-- icesmp-doc-id: route.profession.blueprint-online-j-t-kos-recept-id-75d1da1621 -->
<!-- icesmp-doc-id: route.profession.clear-online-j-t-kos-gathering-crafting-94f838cfa8 -->
<!-- icesmp-doc-id: route.profession.info-903dd2a3ac -->
<!-- icesmp-doc-id: route.profession.join-szakma-7eabe1c640 -->
<!-- icesmp-doc-id: route.profession.list-f390d34b26 -->
<!-- icesmp-doc-id: route.profession.recipes-d99cf16c6c -->
<!-- icesmp-doc-id: route.profession.root-9ff8b16ee7 -->
<!-- icesmp-doc-id: route.profession.set-online-j-t-kos-szakma-b4ba6c62ba -->
<!-- icesmp-doc-id: route.profile.root-55d6850288 -->
<!-- icesmp-doc-id: route.punishments.j-t-kos-774de3965f -->
<!-- icesmp-doc-id: route.quest.abandon-quest-id-c70615d52c -->
<!-- icesmp-doc-id: route.quest.accept-quest-id-ebcd4fef74 -->
<!-- icesmp-doc-id: route.quest.admin-addobjective-id-objekt-va-darab-le-r-s-eb03e45589 -->
<!-- icesmp-doc-id: route.quest.admin-builder-id-170544ece4 -->
<!-- icesmp-doc-id: route.quest.admin-create-id-objekt-va-darab-n-v-54102deb8d -->
<!-- icesmp-doc-id: route.quest.admin-delete-id-e0f41067f7 -->
<!-- icesmp-doc-id: route.quest.admin-info-id-9bb4774bc5 -->
<!-- icesmp-doc-id: route.quest.admin-list-5dc546862b -->
<!-- icesmp-doc-id: route.quest.admin-set-id-mez-rt-k-83b7969b58 -->
<!-- icesmp-doc-id: route.quest.complete-online-j-t-kos-quest-id-341a175f72 -->
<!-- icesmp-doc-id: route.quest.info-d76d7067e7 -->
<!-- icesmp-doc-id: route.quest.list-d10bd003cc -->
<!-- icesmp-doc-id: route.quest.log-6d94f9ac8a -->
<!-- icesmp-doc-id: route.quest.root-2330b6d04b -->
<!-- icesmp-doc-id: route.quest.talk-npc-n-v-36432687d5 -->
<!-- icesmp-doc-id: route.relic.give-online-j-t-kos-relikvia-id-mennyis-g-8b05b3b354 -->
<!-- icesmp-doc-id: route.relic.list-c45f611cc0 -->
<!-- icesmp-doc-id: route.relic.root-c5c7ae3318 -->
<!-- icesmp-doc-id: route.reply.zenet-612b6f0ac3 -->
<!-- icesmp-doc-id: route.report.n-v-ok-fbb91ba7da -->
<!-- icesmp-doc-id: route.reports.all-3358168940 -->
<!-- icesmp-doc-id: route.reports.resolve-id-5e4903abb8 -->
<!-- icesmp-doc-id: route.reports.root-420499c21e -->
<!-- icesmp-doc-id: route.sinner.online-j-t-kos-add-ea7bdc87ce -->
<!-- icesmp-doc-id: route.sinner.online-j-t-kos-clear-baa6a50757 -->
<!-- icesmp-doc-id: route.sinner.online-j-t-kos-set-4487ebd0b7 -->
<!-- icesmp-doc-id: route.sinner.online-j-t-kos-status-8febd3a6d4 -->
<!-- icesmp-doc-id: route.sit.fel-97e382b436 -->
<!-- icesmp-doc-id: route.sit.root-be684d6e26 -->
<!-- icesmp-doc-id: route.socialspy.root-f752d1a522 -->
<!-- icesmp-doc-id: route.soulforge.fejleszt-elet-sebzes-letszam-e24f7f80ec -->
<!-- icesmp-doc-id: route.soulforge.root-173a0e18b3 -->
<!-- icesmp-doc-id: route.souls.champion-f81b808b7b -->
<!-- icesmp-doc-id: route.souls.root-b3b82d6cd9 -->
<!-- icesmp-doc-id: route.spec.choose-specializ-ci-id-eb820838cd -->
<!-- icesmp-doc-id: route.spec.info-2f86e46064 -->
<!-- icesmp-doc-id: route.spec.list-3a48d599f5 -->
<!-- icesmp-doc-id: route.spec.reset-online-j-t-kos-e785a7cc0e -->
<!-- icesmp-doc-id: route.spec.respec-class-profession-d6b17a0059 -->
<!-- icesmp-doc-id: route.spec.root-987236570c -->
<!-- icesmp-doc-id: route.spell.info-a7e028c4a3 -->
<!-- icesmp-doc-id: route.spell.upgrade-spell-id-7725da645e -->
<!-- icesmp-doc-id: route.spellbook.root-250358ba15 -->
<!-- icesmp-doc-id: route.stats.n-v-b12560a71c -->
<!-- icesmp-doc-id: route.suttogas.v-d-online-j-t-kos-cd4a9b4db2 -->
<!-- icesmp-doc-id: route.suttogas.zenet-5ab5968bcd -->
<!-- icesmp-doc-id: route.szakmacel.root-5b9277c302 -->
<!-- icesmp-doc-id: route.talent.list-1240393f51 -->
<!-- icesmp-doc-id: route.talent.spend-class-profession-talent-id-4d443e368d -->
<!-- icesmp-doc-id: route.tanacs.info-98e9cb2be3 -->
<!-- icesmp-doc-id: route.tanacs.szavaz-online-j-t-kos-39363bc937 -->
<!-- icesmp-doc-id: route.tanacs.vasarhet-2b790f1d02 -->
<!-- icesmp-doc-id: route.tell.j-t-kos-zenet-92351ac64b -->
<!-- icesmp-doc-id: route.tempban.j-t-kos-id-tartam-ok-e05bb07e0d -->
<!-- icesmp-doc-id: route.territory.circle-t-pus-frakci-id-sug-r-n-v-1f1b505ae2 -->
<!-- icesmp-doc-id: route.territory.clearpoints-a3c52199a3 -->
<!-- icesmp-doc-id: route.territory.create-t-pus-frakci-id-n-v-f315256212 -->
<!-- icesmp-doc-id: route.territory.dungeonboss-clear-z-na-id-126c83f468 -->
<!-- icesmp-doc-id: route.territory.dungeonboss-z-na-id-t-bla-0775bbfe37 -->
<!-- icesmp-doc-id: route.territory.dungeonchest-t-bla-2ac1095770 -->
<!-- icesmp-doc-id: route.territory.info-bc573dcb2e -->
<!-- icesmp-doc-id: route.territory.list-8b41a6ee36 -->
<!-- icesmp-doc-id: route.territory.points-e068457b4c -->
<!-- icesmp-doc-id: route.territory.pos-83500aed80 -->
<!-- icesmp-doc-id: route.territory.remove-id-be464443d2 -->
<!-- icesmp-doc-id: route.territory.rename-id-j-n-v-0e55b1e6f3 -->
<!-- icesmp-doc-id: route.territory.resize-id-sug-r-a7bc47bae0 -->
<!-- icesmp-doc-id: route.territory.root-551f2787da -->
<!-- icesmp-doc-id: route.territory.setcapital-frakci-sug-r-n-v-d7c17b656b -->
<!-- icesmp-doc-id: route.territory.setspawn-frakci-3b9d0276a8 -->
<!-- icesmp-doc-id: route.territory.settype-id-t-pus-ef4b1e7f15 -->
<!-- icesmp-doc-id: route.territory.sety-id-min-y-max-y-2fd5f53085 -->
<!-- icesmp-doc-id: route.territory.show-id-302bacf618 -->
<!-- icesmp-doc-id: route.territory.tp-id-809cb14024 -->
<!-- icesmp-doc-id: route.territory.undo-d88a000549 -->
<!-- icesmp-doc-id: route.territory.wand-de21264e40 -->
<!-- icesmp-doc-id: route.unban.j-t-kos-ok-0538618e15 -->
<!-- icesmp-doc-id: route.unmute.j-t-kos-ok-4cf10ebd4d -->
<!-- icesmp-doc-id: route.vanish.online-j-t-kos-025d0452a8 -->
<!-- icesmp-doc-id: route.w.j-t-kos-zenet-f5a5ab9bc2 -->
<!-- icesmp-doc-id: route.warn.j-t-kos-ok-73731e594b -->
