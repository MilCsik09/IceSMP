# IceSMP teljes Git-fejlesztési changelog

<!-- icesmp-release-document: full-development-changelog -->

Ez a dokumentum azt mutatja be, mi változott a rögzített Git-fejlesztési
baseline óta. Nem a szerveren futó JAR-hoz hasonlít; ahhoz a
[deployed buildhez képesti changelog](DEPLOYED_BUILD_TO_RELEASE_CHANGELOG.md)
tartozik.

## Vizsgált tartomány

| Mező | Érték |
|---|---|
| Git-baseline | `49cb32740629f3d91a08b753436f3e16d33a494d` |
| Dokumentált branch | `master` |
| Dokumentált HEAD | `4643ab53586f0c1ee7352df16dcd477013e6fad4` |
| Tartomány | `49cb32740629f3d91a08b753436f3e16d33a494d...4643ab53586f0c1ee7352df16dcd477013e6fad4` |
| Commitmennyiség | 417 |
| Kapcsolódó PR-ek | 23 |
| Audit dátuma | 2026-07-30 |
| Platform | Java 21, Minecraft/Paper/Folia 1.21.11 |
| Pluginverzió | `1.0-SNAPSHOT` |

A változáslista a tényleges fájldiff, a bootstrap-regisztrációk, a
resource-ok és a regressziós tesztek leltárából készült. A 417 commit
címe önmagában nem bizonyíték, ezért az alábbi fejezetek funkció és
üzemeltetési hatás szerint rendezik a változásokat.

## Rövid összkép

A baseline óta az IceSMP egy nagy gameplay-magból széles, integrált
szerverplatformmá nőtt. A már meglévő kaszt-, gazdaság-, frakció-,
quest-, szakma- és világmechanikák jelentősen bővültek, miközben több
korábbi külső plugin IceSMP-specifikus feladata natív megoldást kapott.

A végleges forrásban többek között:

- 68 ténylegesen regisztrált root command és 79 root alias van;
- 545 production Java-forrásegység működik együtt;
- 34 lifecycle-ba kötött tartós állapottulajdonos 35 állományt kezel;
- 13 kaszt, 35 kasztspecializáció, 8 szakma és 16
  szakmaspecializáció szerepel;
- 160 questdefiníció, 438 szakmai recept, 81 szakmai anyag, 21
  achievement, 25 advancement JSON, 6 relikvia és 21 rituálé van a
  végleges resource-okban;
- két natív crate-definíció (`koznapi`, `ritka`) érhető el;
- a natív moderáció, MOTD, sit-only, globális AFK és crate alrendszer
  kódszinten és CI alapján integrált, de az éles pluginleváltáshoz még
  kézi Folia-átvételi teszt kell.

## Új rendszerek

### Natív moderáció és online admincsomag

Új, egységes punishment-ledger került be warning, kick, mute,
temporary mute, ban, temporary ban, unmute és unban műveletekkel.
Elérhető a büntetési előzmény, az aktív büntetések listája, a reportok
kezelése, a moderációs GUI és az auditnapló.

A privát üzenetek saját `/msg`, `/tell`, `/w` és `/reply` útvonalat
kaptak. Ezekhez SocialSpy, reply-partner kezelés és reconnect
viselkedés tartozik. Új a vanish és a külön vanish-láthatósági
jogosultság, az offline teleport, valamint az online inventory és
ender chest olvasási/szerkesztési csomag.

Az inventory-szerkesztés nem egyszerű „kattintás és remény”: escrow,
write-recovery és manuális ellenőrzési állapot védi a reconnect,
leállás és hibás mentés eseteit. Az automatizált teszt a state- és
konkurencia-szerződéseket bizonyítja; valódi játékoskapcsolat,
lemezhiba és permissionmátrix továbbra is kézi teszt.

### Natív szerverlista-MOTD

Az IceSMP saját server-list MOTD-t, rotációt és eseményprioritást kapott.
TIME és RANDOM kiválasztás, eseményvariánsok, vanished játékosok
kiszűrése, több ikonmód, PNG- és symlink-védelem, generációs kapu és
reloadkezelés került be.

A megoldás az IceSMP számára szükséges megjelenítést célozza; nem
MiniMOTD-konfiguráció kompatibilitási réteg és nem proxy/vhost klón.

### Natív sit-only

Új `/sit` és click-to-sit rendszer került be lépcsőre, alsó/felső
slabre, carpetre, moss carpetre, pale moss carpetre és konfigurálható
hórétegre. A rendszer foglalást, ülőpozíciót, folyadékot, támaszt,
clearance-t, világ- és anyagpolicyt, sérülést, teleportot, világváltást,
kilépést, kick-et, dismountot, reloadot és disable cleanupot kezel.

A scope tudatosan csak ülés. Lay, crawl, stacking, player sitting és
NPC sitting nincs.

### Natív crate-rendszer

Új crate browser, előnézet, kulcsvásárlás, crate-hely kezelés és
animált nyitás került be. A kulcs main-handből fogy, a duplanyitás és
konkurens callback ellen védett, a tömeges nyitás részleges
teljesülést is kezel.

A legfontosabb új üzembiztonsági elem a tartós opening lifecycle és a
settlement/recovery réteg. A kulcsfoglalás, állapotmentés,
jutalomadás, lezárás és kompenzáció külön lépések, ezért egy
félbeszakadt nyitás admin számára vizsgálható. A `MANUAL_REVIEW`
állapot nem automatikus siker: bizonyítékot és adminfolyamatot igényel.

### Globális AFK-rendszer

Új automatikus tétlenségészlelés és valódi ki/be kapcsoló `/afk`
került be. A tablista jelöli az AFK játékost és a saját rangján belül
hátrasorolja. Mozgás, chat, interakció vagy más parancs visszaállítja
az aktivitást.

Az exploitvédelmi kapu blokkolja az AFK játékos közvetlen mob-,
világboss-, dungeon-miniboss-, virtuális dungeonláda- és személyes
Wild Hunt jutalmát. A bossok életciklusa ettől még lezárul.

A korábban rövid időre forrásba került jutalmazó AFK-zóna, bossbar,
zónaidő és pénzkifizetés a dokumentált HEAD előtt teljesen törölve
lett. AxAFKZone/AxAPI nem célfüggőség.

### UI, megjelenítés és adminfelületek

Új natív tablista került be header/footer, név, nametag, rangsorrend,
AFK-állapot és ping megjelenítéssel. A dinamikus HUD harci fókuszt,
rotáló információt, prioritási kiszorítást, party-részt és
konfigurálható láthatóságot ad. A HUD és a tablista egymástól
függetlenül kapcsolható.

Új vagy lényegesen bővített felület a config GUI, a moderációs GUI, az
invsee, a crate browser/spin, a claim trust, a bestiárium, a pet,
profession, quest és karakterfelület.

### Új világ- és történeti mechanikák

Megjelent vagy kibővült többek között:

- a Kárhozat Kapuja (`DOOM_GATE`) PvPvE zónatípusa;
- a kulcsos dungeon, heti pecsét, virtuális láda és miniboss-loot;
- a szezon story-fejezetek és a Végítélet-hét;
- a Vad Hajsza, kultisták, rontás-góc, meteor, invázió, karaván,
  escort, világboss, hangulat- és gazdasági események;
- a titkos helyek, régészet, bestiárium, heti Krónika és
  emlékszilánk-progresszió;
- a céhek, becsületpárbaj, kémálca, hadi ablak, Suttogók-hálózat,
  szezonliga és közösségi célok;
- a világrombolás tartós, fokozatos visszaépítése fizika-, folyadék-,
  NBT- és zónapolicy-védelemmel.

### Kisebb pluginok natív megfelelői

A világbeállítások között natív Warden-XP és crop-trample védelem
jelent meg. Ezek a külső mini-plugin leváltásának képességét adják, de
játékos- és mob-trample, illetve Warden-halál teszt még szükséges.

## Bővített rendszerek

### Kaszt, specializáció, képesség és talent

A 13 kaszt rendszere 35 specializációra, több erőforrásprofilra,
spell-mesterségre, kombókra, élő balanszra, partybarát célzásra,
crowd-control diminishing returnre és formázott VFX-rétegre bővült.
Új DARK-kötött specializációk, tartós társak, rituálé-idézés,
társ-GUI és társvért jelent meg.

A képességek konfigurációs balance ID-i és a runtime spellkatalógus
nem azonos fogalom: a konfiguráció több száz finomhangolható bejegyzést
tartalmaz, míg az egyedi spellkomponensek és regisztrált variánsok
külön leltárban szerepelnek.

### Frakciók, politika és PvP

A négy frakció megtartása mellett bővült a király, kincstár, adó,
reputáció, raid, hadi ablak, szezonváltási szabály, frakcióétel,
körözés, céh és közösségi verseny. A DARK/Kitaszított és Suttogó
útvonalak külön szabályokat kaptak.

### Küldetések és tartalom

A végleges resource 160 questdefiníciót tartalmaz. Megjelent a
kezdőlánc, kasztmester-próba, fejezet, puzzle, mellékküldetés,
dungeon-starter, napi rotáció, heti feladat, közösségi cél és
admin/builder questfolyamat. A questlog és a builder chat-editor is
bővült.

### Szakmák, crafting és itemek

A szakmarendszer 8 szakmát és 16 specializációt, 438 szakmai receptet,
81 egyedi szakmai anyagot, minőséget, affixet, blueprintet,
frakció-signature itemet, mesterművet, runekovácsolást, készítőnevet és
craft-korlátokat kezel.

Az itemmodell a modern Paper/Minecraft adatkomponensekre és
`ITEM_MODEL` azonosítókra állt át. A resource pack munkafolyamata külön
manifestet és textúrás útmutatót kapott.

### Gazdaság

A négy valuta, bank, árfolyam, piactér, aukció, frakciókincstár,
adományláda, vendorok és pénznyelők kibővültek. A jutalmak fizikai
veretként kerülnek a játékoshoz; bankszámlára közvetlenül csak a banki
folyamat tesz pénzt. Napi faucet-plafonok, journal és recovery
csökkenti a duplikálás/adatvesztés kockázatát.

## Átstrukturált rendszerek

- A bootstrap és a core külön kezeli a plugin belépést, a manager- és
  listenergráfot, a commandregisztrációt és a lifecycle cleanupot.
- A permissionök kanonikus `icesmp.admin.*` sémát, valódi
  `icesmp.admin.all` szülőt és legacy aliasokat kaptak.
- A config több `config/*.yml` fájlból áll össze; a root `config.yml`
  override utolsóként nyer. Az ingame config set/unset az override-ot
  írja és reloadot indít.
- A persistence közös koordinátort, atomikus YAML-írást, kritikus
  write-hibát, corrupt-state jelzést és tranzakciós naplókat kapott.
- A display- és particle-effektek közös segédrétegre kerültek, hogy a
  vizuál, a régiószál és a cleanup egységes legyen.
- A dokumentációhoz gépi repository-, release- és coverage-inventory
  készült.

## Hibajavítások és biztonsági javítások

A 417 commitsáv számos célzott hibajavítást tartalmazott. A fontosabb
eredménycsoportok:

- cross-region és entity-scheduler hívások Folia-biztosabb kezelése;
- duplikált jutalom, konkurens gazdasági művelet és callback race
  elleni kapuk;
- tartós reward replay, season/community generation commit és
  crash-recovery;
- relikvia halál/reconnect recovery;
- claim-, terület-, NPC-, projectile-, pet- és eventútvonalak
  védelmi réseinek zárása;
- config-validáció és fail-closed indulás a kritikus alrendszereknél;
- a crate settlement egyszeri teljesülése és manuális recovery;
- moderációs állapot- és láthatósági versenyhelyzetek javítása;
- MOTD gyors reload, ikonvalidáció és scheduler-rejection kezelés;
- sit entity/reservation cleanup;
- `/afk` automatikus és kézi toggle, friss inactivity baseline,
  HUD-független tablista és AFK-jutalomkapuk.

## Folia-kompatibilitás

A kód a globális, régió-, entity- és player-schedulert feladat szerint
választja. A baseline óta sok közvetlen Bukkit-hívás került át a
megfelelő tulajdonos szálára, különösen jutalom, teleport, entity,
GUI, boss, event és cleanup útvonalon.

Ez forrás- és CI-bizonyíték, nem production bizonyíték. A valódi
Folia-szerveren végzett többjátékos, disconnect, chunk unload,
világváltás és scheduler-rejection playtestet a
[release acceptance checklist](RELEASE_ACCEPTANCE_CHECKLIST.md)
tartalmazza.

## Persistence és recovery

A final bootstrap 34 tartós állapottulajdonost koordinál; a
`MarketManager` két állományt használ, ezért 35 state fájl jelenik meg.
Külön auditlog tartozik a crate-nyitáshoz, a moderációhoz és a
chatmoderációhoz.

Az új recovery-mechanizmusok célja, hogy a félbeszakadt művelet ne
váljon csendes sikernek. A `MANUAL_REVIEW`, corrupt state és kritikus
write failure adminbeavatkozást jelent. Ezek fault-injection nélkül
nem tekinthetők production szinten bizonyítottnak.

## Commands, permissionök és GUI-k

A végleges parancsleltár 68 root commandot és 79 root aliast tartalmaz.
Az új fő csoportok a moderáció, privát üzenet, vanish, invsee,
offline teleport, MOTD/config adminfolyamat, crate, sit és globális
AFK. A régi gameplay-parancsok nagy része megmaradt, de több routing,
permission és GUI-alternatíva változott.

A teljes szintaxis a [parancsreferenciában](../reference/COMMAND_REFERENCE.md),
a 44 statikus/dinamikus release permission a
[permissionreferenciában](../reference/PERMISSION_REFERENCE.md), a
GUI-műveletek pedig a [GUI-referenciában](../reference/GUI_REFERENCE.md)
találhatók.

## Config és adatvezérelt tartalom

A baseline óta több ezer új vagy módosított config- és adatútvonal
jelent meg. A final repository inventory scanner 12 223 config/adatpathet,
a nyers bundled YAML leaf-kiegészítéssel együtt pedig 13 550 egyedi
dokumentált pathot és 1 614 nem üres message-kulcsot talál. E számba a valódi üzemeltetési
beállítások mellett a nagy adatvezérelt definíciófák is beletartoznak.

Az összes path és alapérték a
[konfigurációs referenciában](../reference/CONFIGURATION_REFERENCE.md),
a teljes resource- és fallback-kulcsunió az
[üzenetkulcs-referenciában](../reference/MESSAGE_REFERENCE.md),
a registryk és tartalmi definíciók az
[adatkatalógusban](../reference/DATA_CONTENT_CATALOGUE.md) szerepelnek.

## Tesztek

A Git-baseline óta dependency-free regressziós suite került a buildkapuba a
persistence, DEV-item jutalom, moderáció, MOTD, sit-only, crate és globális AFK
területére. A dokumentációs inventory tooling 21 unit tesztje a command-,
permission-, config- és markerfeloldás fail-closed viselkedését ellenőrzi.

Ezek kódszintű és CI-bizonyítékok. Nem helyettesítik a valódi Folia-szerveren
végzett multiplayer, restart, chunk/world lifecycle, scheduler-rejection,
full-inventory és fault-injection próbákat; azok a
[release acceptance checklistben](RELEASE_ACCEPTANCE_CHECKLIST.md) maradnak
nyitott rollout-kapuk.

## Dokumentáció

Új játékoskézikönyv, builderútmutató, szerverintegrációs és
pluginreplacement-mátrix, moderációs, crate-, sit-, AFK- és
architektúradokumentáció, playtestlista, lore-kódex és resource-pack
anyag készült. A release-csomag ezeket egyetlen, szerepkör szerint
használható indexbe rendezi, és a stale állításokat a final forrás
alapján korrigálja.

## Eltávolított vagy visszavont funkciók

- A jutalmazó AFK-zóna, AFK-bossbar és időzített AFK-kifizetés törölve.
- Lay és crawl nincs a final runtime-ban.
- Stacking, player sitting és NPC sitting nincs a sit scope-ban.
- A GSit teljes upstream-paritása nem cél.
- A TAB teljes upstream-klónja nem cél; az IceSMP számára szükséges
  natív megjelenítés marad.
- MiniMOTD config-paritás és proxy/vhost-klón nem cél.
- Offline playerdata szerkesztés és legacy moderation-adatbázis
  migráció nincs a natív moderáció scope-jában.
- Több ötlettári és lore-elem tervezett maradt. Ezek csak akkor
  szerepelnek aktívként, ha command, GUI, listener vagy automatikus
  runtime útvonal bizonyítja őket.

## Külső plugin replacement

| Külső plugin | Git-fejlesztési eredmény | Release-kapu |
|---|---|---|
| AxAFKZone / AxAPI | Scope törölve; natív globális AFK külön megmaradt | Éles jar/adat/remap-cache eltávolítása |
| GSit | Natív sit-only kódszinten integrált | Valódi Folia ülés/cleanup teszt |
| CrazyCrates | Natív crate és settlement/recovery integrált | Runtime és fault-injection teszt |
| SModeration / InvSee++ | Natív moderáció és online admincsomag integrált | Restart/reconnect/lemezhiba/permission teszt |
| MiniMOTD | Natív IceSMP MOTD integrált | Server-list, ikon, reload és jar nélküli teszt |
| TAB | IceSMP-specifikus natív HUD/tablista | Nem cél a teljes TAB-paritás |
| ICEsmpadditions | Natív Warden-XP megfelelő | Kézi runtime teszt |
| FarmProtect | Natív crop-trample megfelelő | Játékos- és mob-trample teszt |

## Ismert korlátok és forrásdrift

Az inventory az alábbi, gameplay-módosítás nélkül dokumentálandó
forrás/config eltéréseket találta:

- öt olvasott primary config kulcshoz nincs bundled YAML-default
  (`factions.kings.dethrone-on-expiry`,
  `mob-scaling.hard-cap-level`, `mob-scaling.name.visible`,
  `world-events.avoid-territory`,
  `world-events.orchestration.max-active-minutes`);
- a `factions.raid.duration-minutes` számot külön helyek egész és hosszú
  egész típusként olvassák;
- a régi `MuteCommand` és `UnmuteCommand` osztály nincs rootként
  regisztrálva; az aktív `/mute` és `/unmute` útvonalat az egységes
  moderációs router szolgálja ki;
- dinamikus registry-, state- és configszakaszok teljes pathja csak a
  szülődefinícióval együtt értelmezhető;
- az élő külső config, state, permission-kiosztás és pluginlista nem
  volt a repository/JAR audit része.

Ezek az
[evidence matrixban](RELEASE_EVIDENCE_MATRIX.md) és a
[konfigurációs referenciában](../reference/CONFIGURATION_REFERENCE.md)
kapnak pontos forráshelyet és üzemeltetési következményt.

## Technikai függelék: fő fejlesztési hullámok

| Hullám | Bizonyított tartalom |
|---|---|
| Gameplay- és Folia-auditok | cross-region, gazdaság, védelem, scheduler, cleanup és balanszjavítások |
| UI és vizuál | natív tablista, dinamikus HUD, DisplayFx, SpellVfx, resource-pack migráció |
| Lore és tartalom | kanonikus lore, signature itemek, questhullámok, világhelyszínek és builderanyag |
| Világ és szezon | esemény-orchestráció, szezonliga, történeti fejezetek, dungeon és világregen |
| Persistence hardening | fail-closed store lifecycle, journal, crash-replay és recovery |
| Plugin replacement | moderáció, MOTD, sit-only, crate, globális AFK boundary |
| Release tooling | repository inventory, release delta, documentation coverage és consistency |

Az egyes commitok és PR-ek gépi, változatlan listája a CI
`repository-docs-inventory` artifactjában marad. Ez a fő changelog
szándékosan a használati és üzemeltetési hatásra koncentrál.
