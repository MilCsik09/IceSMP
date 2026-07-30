# IceSMP admin- és moderátori kézikönyv

<!-- icesmp-doc-id: guide.admin-and-moderator -->

> Dokumentált HEAD: `4643ab53586f0c1ee7352df16dcd477013e6fad4`
>
> Audit dátuma: 2026-07-30
>
> Deployed baseline: `IceSMP-1.0-TESTING.jar` (`da039f0e2bdf0e67b216ce82d7d3fe3b6da0af6e18f6fa175762c37493795a05`, source mapping: `BINARY_ONLY`)

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

- [parancsreferencia](../reference/COMMAND_REFERENCE.md);
- [permissionreferencia](../reference/PERMISSION_REFERENCE.md);
- [GUI-referencia](../reference/GUI_REFERENCE.md);
- [konfigurációs referencia](../reference/CONFIGURATION_REFERENCE.md);
- [deployed build → release changelog](../releases/DEPLOYED_BUILD_TO_RELEASE_CHANGELOG.md);
- [release acceptance checklist](../releases/RELEASE_ACCEPTANCE_CHECKLIST.md).

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
checklist](../releases/RELEASE_ACCEPTANCE_CHECKLIST.md) kézi runtime
bizonyítékai ettől még kötelezőek.
