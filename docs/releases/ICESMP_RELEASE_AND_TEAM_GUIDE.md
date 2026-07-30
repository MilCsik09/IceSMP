# IceSMP integrált release- és csapatkézikönyv

<!-- icesmp-doc-id: release.team-guide -->

## 1. A dokumentum célja

Ez a kézikönyv az IceSMP jelenlegi, integrált tesztkiadásának közérthető
áttekintése. Elsősorban szervervezetőknek, adminoknak, moderátoroknak,
buildereknek, eventeseknek, tesztelőknek és a játékosokkal kommunikáló
csapattagoknak készült.

Három külön kérdésre külön dokumentum válaszol:

1. **Mi változik a jelenleg futó szerverbuildhez képest?**  
   [Deployed build → release changelog](DEPLOYED_BUILD_TO_RELEASE_CHANGELOG.md)
2. **Mi változott a rögzített Git-fejlesztési baseline óta?**  
   [Teljes fejlesztési changelog](ICESMP_FULL_DEVELOPMENT_CHANGELOG.md)
3. **Mit tud a végleges integrált plugin?**  
   Ez a kézikönyv és a [teljes funkciókatalógus](../reference/FEATURE_CATALOGUE.md).

A „release” ebben a csomagban **tesztelhető kiadási alapot** jelent. A zöld
fordítás és automatizált teszt nem helyettesíti a staging szerveren végzett
runtime, többjátékos, világépítési és hibainjektálási átvételt.

## 2. Vizsgált release és deployed baseline

| Mező | Érték |
|---|---|
| Dokumentált repository | `MilCsik09/IceSMP` |
| Dokumentált branch | `master` |
| Dokumentált HEAD | `4643ab53586f0c1ee7352df16dcd477013e6fad4` |
| Audit dátuma | 2026-07-30 |
| Git-fejlesztési baseline | `49cb32740629f3d91a08b753436f3e16d33a494d` |
| Git-baseline → release | 417 commit |
| Deployed baseline | `IceSMP-1.0-TESTING.jar` |
| Deployed SHA-256 | `da039f0e2bdf0e67b216ce82d7d3fe3b6da0af6e18f6fa175762c37493795a05` |
| Deployed forrásazonosítás | `BINARY_ONLY` |
| Release Java | 21 |
| Release Gradle wrapper | 9.4.1 |
| Minecraft / Paper API | 1.21.11 |
| Folia API | `1.21.11-R0.1-SNAPSHOT` |
| Pluginverzió | `1.0-SNAPSHOT` |
| Folia támogatás | igen |

A deployed JAR manifestje és pluginleírója nem tartalmaz Git SHA-t. Emiatt a
bináris önálló, autoritatív baseline; nem azonosítjuk automatikusan egy történeti
committal. A JAR képességei, a csomagolt alapconfig és az élő szerver tényleges
külső configja három külön dolog. Az utóbbi nem állt rendelkezésre.

Az AFK scope-javítást befogadó `master` állapoton az IceSMP CI és a repository
dokumentációs inventory ellenőrzése is sikeresen lefutott. A dokumentációs branch
ezt a rögzített forrásállapotot írja le; maga a dokumentáció nem módosít gameplay
kódot vagy adatmodellt.

## 3. Mi az IceSMP?

Az IceSMP egy Folia-kompatibilis Minecraft szerverplugin, amely egyetlen
összefüggő rendszerben kezeli a karakterfejlődést, frakciókat, politikát,
gazdaságot, küldetéseket, professionöket, egyedi tárgyakat, világmechanikákat,
eseményeket, adminisztrációt és szervermegjelenítést.

A játékosoldali tartalom mellett a plugin jelentős üzemeltetési felületet is ad:
zónák, claimek, NPC-kötések, crate-helyek, configkezelés, moderáció, audit,
recovery, HUD, tablista és MOTD. Több korábban külső pluginnal biztosított
képességnek natív megfelelője van, de a külső JAR-ok eltávolítása csak az
[átvételi tesztlista](RELEASE_ACCEPTANCE_CHECKLIST.md) teljesítése után biztonságos.

## 4. Kinek szól a rendszer?

| Szerepkör | Legfontosabb terület |
|---|---|
| Játékos | kaszt, specializáció, spell, quest, profession, gazdaság, frakció, party, pet, esemény |
| Moderátor | büntetés, report, privát üzenet, SocialSpy, vanish, előzmények |
| Admin | config, inventory/ender chest, offline teleport, crate, esemény, recovery, audit |
| Builder/world designer | territory, claim, NPC, crate, dungeon, rituálé, eventhely, teleport |
| Eventes/tartalomkészítő | eventindítás, bossok, questkapuk, jutalmak, helyszínek |
| Tesztelő | pozitív és negatív gameplay, lifecycle, restart, reload, fault injection |
| Üzemeltető | build, deployment, migráció, mentés, permission, külső pluginok |
| Kommunikációs csapat | játékosokat érintő újdonságok, tudatos scope-határok, rollout-státusz |

## 5. A release legfontosabb újdonságai

- Natív moderációs és online admincsomag warninggal, mute/ban folyamattal,
  előzményekkel, privát üzenetekkel, SocialSpy-jal, vanish-sel,
  inventory/ender chest kezeléssel és offline teleporttal.
- Natív, konfigurálható MOTD eseményprioritással, idő- és véletlen rotációval,
  ikonmódokkal és vanished játékosokat figyelembe vevő létszámmal.
- Sit-only rendszer támogatott blokkgeometriával, foglalással és teljes
  életciklus-takarítással.
- Natív crate-rendszer tartós helyekkel, kulcsokkal, böngésző- és animációs
  GUI-val, tömeges nyitással, auditálható settlementtel és recoveryvel.
- Globális automatikus és kézi AFK, tablistajelzéssel és jutalomkapukkal.
  **Jutalmazó AFK-zóna nincs.**
- Jelentősen bővített tartalom: 13 kaszt, 35 specializáció, 160 quest,
  8 profession, 438 professionrecept, 6 relikvia, 21 rituálé és több
  világ-/szezon-/politikai rendszer.
- Natív Warden-XP és player/mob farmland-trample védelem, külön kézi
  átvételi teszttel.

## 6. Mi változik a jelenlegi szerverbuildhez képest?

A bytecode- és resource-leltár szerint a deployed JAR 30 root parancsot,
56 root aliast, 16 bizonyított permissiont, 14 GUI-családot és 17 tartós
állapottulajdonost tartalmaz. A release 68 root parancsot, 79 root aliast,
93 további routing aliast, 44 bizonyított permissiont, 22 GUI-holdert és
34 lifecycle-ban kezelt
perzisztens tulajdonost tartalmaz. A baseline összes root parancsa és aliasa
megmaradt; a változás főként bővítés és megbízhatósági átdolgozás.

A legfontosabb különbségek:

| Státusz | Rendszer | Röviden |
|---|---|---|
| Új | Moderáció/admin | natív punishment, PM, SocialSpy, vanish, invsee, offline teleport |
| Új | MOTD | natív szerverlista-szöveg és ikonkezelés |
| Új | Sit-only | biztonságos ülés; nincs teljes GSit-paritás |
| Új | Crate | hely, kulcs, GUI, jutalom, tartós settlement/recovery |
| Új | Globális AFK | automatikus/kézi állapot, tablista, reward block |
| Megszűnt köztes scope | AFK-zóna | nincs zóna, időzített payout vagy bossbar |
| Jelentősen megváltozott | Tartalom | több quest, spec, recept, világ- és politikai rendszer |
| Belső megbízhatósági javítás | Lifecycle/persistence | több recovery, atomikus mentés és Folia-safe működés |

A teljes, szerepkör és teendő szerinti lista:
[Deployed build → release changelog](DEPLOYED_BUILD_TO_RELEASE_CHANGELOG.md).

## 7. Mit kell tudniuk az adminoknak?

- Ne másoljátok át vakon a régi élő configot. Készítsetek staging override-ot,
  majd ellenőrizzétek a [configreferencia](../reference/CONFIGURATION_REFERENCE.md)
  szerint.
- A moderation, crate és inventory admin állapotai tartósak lehetnek; restart,
  corrupt state, lemezhiba, rollback és manuális recovery külön tesztpont.
- A crate bizonytalan külső side effectje nem automatikusan „siker” vagy „hiba”:
  manuális vizsgálati állapotba kerülhet. Az auditot és a recovery parancsot
  ugyanazzal a tesztesettel kell ellenőrizni.
- Az inventory edit erősebb jogosultság, mint a read. Ugyanez igaz az ender
  chestre és a vanished játékosok láthatóságára.
- A config reload nem minden strukturális vagy scheduler-változtatást tud
  biztonságosan újraütemezni; új registryelem, worldcsere és periódusmódosítás
  után restart és playtest javasolt.
- Az adminfolyamatok részletesen:
  [Admin- és moderátori kézikönyv](../guides/ADMIN_AND_MODERATOR_GUIDE.md).

## 8. Mit kell tudniuk a moderátoroknak?

- A warning, kick, mute, temporary mute, ban, temporary ban, unmute és unban
  külön permission- és auditútvonal.
- A `/history` a játékos előzményeit, a `/punishments` az aktív/lejárt
  büntetéseket és adminfolyamatot teszi elérhetővé.
- A `/msg`, `/tell`, `/w`, `/reply` és `/r` ugyanahhoz a natív privátüzenet-
  rendszerhez tartozik. A SocialSpy érzékeny permission.
- A vanish használata nem jelent automatikus láthatóságot minden adminnak;
  ehhez külön visibility permission tartozik.
- Inventory- vagy ender chest szerkesztés előtt válasszátok szét a read és edit
  feladatot, és ellenőrizzétek a reconnect/escrow recoveryt.
- Büntetés- vagy inventoryhiba esetén ne javítsatok kézzel élő YAML-t futó
  szerver mellett; állítsátok meg az érintett folyamatot, mentsétek a bizonyítékot,
  majd kövessétek a recovery fejezetet.

## 9. Mit kell tudniuk a buildereknek?

- A crate, territory, claim, dungeon, parkour, NPC, rituálé, boss, event és
  teleport rendszer fizikai világ-előkészítést igényelhet.
- WorldEdit, világcsere vagy világátnevezés után a helyhez kötött persistent
  rekordok nem tekinthetők automatikusan érvényesnek.
- A sit csak támogatott, biztonságos blokkgeometrián működik. Stairs, alsó és
  felső slab, carpet, moss carpet, pale moss carpet és megfelelő hóréteg külön
  tesztelendő.
- Crate-helyet csak a natív adminfolyamattal hozzatok létre/cseréljetek/töröljetek;
  utána teszteljétek a block-, world- és definition-kötést, valamint a full
  inventory viselkedést.
- NPC-azonosító nem egyenlő kihelyezett és működő NPC-vel. A release tartalmaz
  questhivatkozásokat, de az élő binding külön builder/admin adat.
- Részletek:
  [Builder- és world designer kézikönyv](../guides/BUILDER_AND_WORLD_DESIGNER_GUIDE.md).

## 10. Mit kell tudniuk az eventeseknek?

- A nagy események közös kapun futnak; egyszerre korlátozott számú major event
  lehet aktív.
- A boss, dungeon, invasion, Wild Hunt, caravan, cultist és seasonal event
  konfigurációja nem bizonyít kész fizikai arénát vagy útvonalat.
- Jutalomtesztnél AFK, full inventory, party/personal loot, napi limit és
  currency failure esetet is vizsgálni kell.
- Kézi indítás előtt ellenőrizzétek az esemény helyét, territory/claim policyt,
  WorldGuard-hidat, felszínbiztonságot és cleanupot.
- Élő esemény előtt stagingen bizonyítsátok a start, stop, restart, timeout és
  félbeszakított állapot helyreállását.

## 11. Mit kell tudniuk a tesztelőknek?

Az automatizált build csak kódszintű bizonyíték. A kötelező runtime csomag
szerepkörökre, előkészítésre, elvárt eredményre, hibakezelésre és bizonyítékhelyre
bontva itt található:
[Release acceptance checklist](RELEASE_ACCEPTANCE_CHECKLIST.md).

Kiemelt tesztterületek:

- permissionmátrix legalább játékos, helper, moderátor, admin és konzol szereppel;
- restart/reload/disable minden tartós vagy entityt létrehozó rendszer közben;
- Folia több régióban, legalább két játékossal;
- full inventory, dupla kattintás, több stack és részleges siker;
- lemezhiba, hibás YAML és megszakított külső side effect;
- világváltás, WorldEdit, eltűnt blokk, átnevezett világ;
- külső replacement plugin jelenlétével és nélküle;
- negatív scope: nincs AFK-zóna, lay, crawl, stacking vagy player/NPC sitting.

## 12. Teljes funkciókatalógus

Az összes bizonyított aktív és adminisztratív rendszer részletes, egységes
mezőkkel készült katalógusa:
[FEATURE_CATALOGUE.md](../reference/FEATURE_CATALOGUE.md).

Fő státuszok:

| Terület | Release-státusz |
|---|---|
| Kasztok, specializációk, képességek | Aktív és játékosok számára elérhető |
| Frakció, politika, gazdaság | Aktív; rollout- és balance-teszt szükséges |
| Quest, profession, recept, item | Aktív; data-driven tartalom |
| Territory, claim, dungeon, NPC | Aktív, builder-előkészítést igényel |
| Moderáció és online admin | Tesztelési/rollout-kapu alatt |
| MOTD, tablista, HUD | Aktív, configgal engedélyezhető; runtime átvétel kell |
| Sit-only | Tesztelési/rollout-kapu alatt |
| Crate | Tesztelési/rollout-kapu alatt |
| Globális AFK | Aktív, configgal engedélyezhető |
| Jutalmazó AFK-zóna | Elvetett / out of scope |
| Lay/crawl/stacking/player-NPC sitting | Elvetett / out of scope |

## 13. Játékosfunkciók

A játékos karaktert és közösséget épít:

- végleges kasztválasztás, 35 specializáció és szintkapus képességek;
- spellbook, mastery, combo, talent és kaszterőforrás;
- négy frakció, politika, bűn, párbaj, war, raid és szezon;
- fizikai valuták, bank, váltás, piac, aukció, boltok és komp;
- questek, napi tartalom, achievement, bestiárium és közösségi cél;
- profession, recept, blueprint, egyedi tárgy, relikvia és rituálé;
- pet, party, céh, parkour, dungeon és világ-esemény;
- profil, menü, HUD, tablista, kódex és értesítések;
- globális AFK és sit-only.

Az elérhetőség permissiontől, configtól, játékosállapottól és a világ
előkészítettségétől függhet. A pontos parancsokat a
[parancsreferencia](../reference/COMMAND_REFERENCE.md) tartalmazza.

## 14. Admin- és moderációs rendszerek

A natív suite a következőket fogja össze:

- punishment létrehozás, lejárat, visszavonás és előzmény;
- report és chatbiztonság;
- privát üzenetek és SocialSpy;
- vanish és elkülönített láthatóság;
- online inventory és ender chest read/edit;
- invsee escrow, reconnect recovery és audit;
- offline teleport;
- moderációs GUI;
- persistence, shutdown és recovery.

Kritikus elv: az adminnak látható „sikeres kattintás” nem elegendő bizonyíték;
ellenőrizni kell a tartós állapotot, az auditot és a célszemély oldalát is.

## 15. Crate-rendszer

A natív crate:

- fizikai blokk- és világhelyhez köthető;
- PDC-alapú kulcsot és opcionális vásárlást használ;
- böngésző/preview és nyitási animációs GUI-t ad;
- súlyozott item-, valuta-, command-, unique-, recipe-, blueprint- és key
  jutalmakat kezel;
- bounded mass-opent és részleges nyitást támogat;
- nyitási ledgert, auditot és recoveryt tart fenn;
- generációváltásnál leválasztja a régi és az új definíciót;
- bizonytalan side effectnél manuális adminvizsgálatot kér.

A CrazyCrates csak a teljes runtime és fault-injection csomag után távolítható el.

## 16. Sit-only rendszer

A natív ülés:

- parancsból és engedélyezett kattintási útvonalból érhető el;
- támogatott blokkformán számítja a seat pozíciót;
- kizárja a veszélyes supportot, folyadékot és elégtelen clearance-t;
- egy helyet egy játékosnak foglal;
- damage, sneak, blokk-törés, teleport, világváltás, quit, kick, dismount,
  reload és disable során takarít;
- plugin-owned seat entityt használ, amely nem perzisztens világobjektum.

Nem része a scope-nak: lay, crawl, stacking, másik játékos vagy NPC megülése,
illetve a teljes GSit API-paritás.

## 17. Globális AFK-rendszer

A release globális AFK-ja:

- inaktivitás alapján automatikusan AFK-ra állít;
- `/afk` paranccsal valódi ki/be kapcsolást ad;
- aktivitáskor visszavált;
- tablistán jelöl és rangon belül hátrébb rendez;
- a profession-XP és a közös kill/boss/dungeon/Wild Hunt reward gate a
  konfigurált AFK-kaput használja;
- a fishing windfall és ambient pénzjutalom AFK esetén a configtól függetlenül
  tiltott;
- quit/reconnect/disable során takarít.

Az aktív config két működési területe az inaktivitási idő és a részleges
jutalomblokkolás. Az `afk.block-rewards` nem univerzális főkapcsoló:
profession-XP-re közvetlenül, a kill/boss útvonalra a
`kill-rewards.afk-block` fallbackjeként hat.
Nincs zóna, zónaidő, payout, bossbar vagy AFK-zóna scheduler. Régi staging/élő
override-ból az ilyen kulcsokat törölni kell.

## 18. MOTD, tablista, HUD és szervermegjelenítés

- A MOTD default variánst, `TIME` vagy `RANDOM` kiválasztást, eseményvariánst,
  vanished-count szűrést és több ikonmódot.
- Az ikonkezelésnél a hibás PNG, symlink, gyors reload és scheduler-rejection
  kötelező negatív teszt.
- A natív tablista headert, footert, játékosnevet, rangrendezést, frakció/AFK/
  vanish állapotot és pinget kezel.
- A HUD sidebaron és vizuális rétegeken mutat játékosállapotot, de a tablista
  lifecycle-ja nem függhet a HUD engedélyezettségétől.
- A TAB plugin teljes upstream-paritása nem cél; csak az IceSMP-hez bizonyított
  subset natív.

## 19. Kasztok, specializációk és képességek

A végleges registry 13 kasztot és 35 specializációt tartalmaz. A bundled
konfiguráció 420 spell-balance ID-t és 419 unlock-hivatkozást tartalmaz; ez
konfigurációs képesség, nem automatikus bizonyíték 420 külön runtime
spellregisztrációra. A részletes ID-k az
[adatkatalógusban](../reference/DATA_CONTENT_CATALOGUE.md) vannak.

Kasztválasztás, specializáció, spellbook, talent, erőforrás, mastery és combo
egymáshoz kapcsolódó, de külön tesztelendő rendszerek. Új ID vagy unlockstruktúra
bevezetésekor restart és célzott cast/unlock teszt szükséges.

## 20. Frakciók és politikai rendszerek

A négy frakcióhoz passzívák, spawnok, fizikai valuta és társadalmi szabályok
tartoznak. A politikai réteg királyt, tanácsot, adót, bűnt, bounty-t, párbajt,
war window-t, raidet, szezonligát és rejtett Suttogó-státuszt kezel.

A rendszer állapottartó és gazdasági side effecteket is végez. Bevezetés előtt
kötelező a szezonforduló, restart, offline jutalom, adó, treasury, raid és
frakcióváltás tesztje. A politikai szerepek permissionjeit nem szabad
adminpermissionnel összekeverni.

## 21. Küldetések és történeti rendszerek

A bundled végleges config 160 questet, 3 napi questdefiníciót, 21 achievementet,
7 közösségi célt és 17 objective-típust tartalmaz. A questek közt onboarding,
kaszt-, történeti, szezon-, rejtvény-, ismétlődő és NPC-kötött útvonalak vannak.

A tervezett lore önmagában nem aktív feature. Csak a registryből, configból és
elérési útvonalból bizonyított elem került az aktív katalógusba. Fizikai NPC,
territory vagy parkour hivatkozásnál a builder binding külön előfeltétel.

## 22. Események és bossok

A plugin major event kaput, világbossokat, inváziót, Wild Huntot, karavánt,
kultista eseményt, vérholdat, meteort, régészetet, treasure és több ambient/
seasonal rendszert tartalmaz. A configban négy seasonal event és több
eseménytípus található.

A major event, spawn guard, territory/claim policy, mob lifetime, reward gate,
cleanup és restart együtt tesztelendő. A configolt boss- vagy lootdefiníció nem
helyettesíti a kész arénát és a builderbejárást.

## 23. Professionök, crafting, receptek és blueprintök

A release 8 professiont, 16 profession-specializációt, 438 professionreceptet,
81 professionanyagot és 54 blueprintként tanulható receptet tartalmaz.
Craftkorlátozás, unique input, szintkapu, receptkönyv, eredmény-PDC és
resource-pack megjelenítés együtt alkotja a játékosfolyamatot.

Recipe/config reload után ellenőrizni kell a cache-generációt, a már létező
tárgyakat és a vanília craft/smelting/anvil tiltásokat.

## 24. Unique itemek és jutalmak

A rendszer egyedi receptkimeneteket, signature tárgyakat, rúnákat, ritkaságokat,
affixeket, relikviákat, crate keyt, questjutalmat és fizikai valutát kezel.
Az adatkatalógus külön választja a registry-ID-t, a recipe outputot és a
konfigurált jutalomtípust.

Full inventory és overflow esetben rendszerfüggő a viselkedés; ezt nem szabad
egyetlen általános szabályként kommunikálni. Quest, crate, event és admin item
útvonalat külön kell tesztelni.

## 25. Gazdaság és kereskedelem

Négy fizikai frakcióvaluta, bank, váltó, piaci listázás, aukció, boltok, caravan,
ferry, adó, treasury és több napi limit tartozik a gazdasághoz. A fizikai token
és a tartós számla közötti átmenet gazdasági tranzakció, ezért hiba és restart
esetén auditálandó.

Az élő árak, limitek és feature flag-ek a csatolt JAR-ból vagy repositoryból nem
bizonyítják az élő szerver tényleges override-ját. Bevezetés előtt exportálni és
kézzel összevetni kell az élő configot.

## 26. Claim-, régió- és világmechanikák

A territory kör- és poligonalakot, Y-tartományt, hat zónatípust és szabálymátrixot
kezel. A claim blokkpontos birtok, trusttal és container/physics védelemmel.
Ehhez társulhat combat tag, dungeon, portal policy, End-zár, event spawn policy,
mob scaling és a világot visszaépítő regenrendszer.

E rendszerek közvetlenül függenek a világ nevétől, koordinátáitól,
blokkszerkezetétől és a persistent helyadatoktól. Világcsere előtt mentés,
inventory, konverziós terv és teljes builderbejárás szükséges.

## 27. Teleport- és mozgásrendszerek

Frakcióspawn, respawn, ferry, offline teleport, admin teleport, eventmozgatás és
világváltás több rendszerben megjelenik. Combat tag, territory, portal policy,
sit és adminpermission korlátozhatja az útvonalat.

Az offline teleport következő belépésre érvényesülő állapotot használhat; a
tesztet quit/reconnecttel és világ hiánya esetével is le kell futtatni.

## 28. NPC- és interakciós rendszerek

Az `/npcbind` bolt-, bank-, váltó-, frakció-, quest- és parancsútvonalat köthet
NPC-hez. A questek 18 hivatkozott NPC-ID-t tartalmaznak, de bundled élő binding
nincs; az admin/builderszintű persistent állapot külön hozza létre.

FancyNpcs integráció opcionális. NPC plugin nélkül vagy bindinghiba esetén a
fallback viselkedést és a parancsalternatívát dokumentáltan ellenőrizni kell.

## 29. Admin GUI-k és kezelőfelületek

A végleges forrás 22 bizonyított GUI-holdert tartalmaz. Ide tartozik többek közt
a főmenü, profil, talent, spellbook, recept, market/auction, config,
moderáció, inventory/ender chest, crate browser/spin és több adminfelület.

A GUI-referencia nem csak osztályneveket sorol: megnyitási mód, permission,
funkcionális slot, lapozás, visszalépés, read/edit, lezárt állapot és cleanup
szerint térképezi fel:
[GUI_REFERENCE.md](../reference/GUI_REFERENCE.md).

## 30. Külső integrációk

Az IceSMP opcionális hidakat használhat PlaceholderAPI, LibsDisguises,
FancyNpcs, WorldGuard és LuckPerms felé. A híd megléte nem bizonyítja, hogy az
élő szerveren az adott plugin és kompatibilis verzió telepítve van.

Deployment előtt készítsetek tényleges élő pluginlistát és configütközés-leltárt,
különösen claim, chat, tablista, scoreboard, NPC, disguise és régióvédelem
területen.

## 31. Külső pluginok státusza

Röviden:

- AxAFKZone/AxAPI: nem kerül deploymentbe; a jutalmazó zóna scope törölve.
- GSit: marad a sit-only runtime elfogadásáig.
- CrazyCrates: marad a crate runtime és fault-injection elfogadásáig.
- SModeration/InvSee++: marad a natív adminsuite runtime elfogadásáig.
- MiniMOTD: marad a szerverlista runtime elfogadásáig.
- TAB: a natív cél nem teljes upstream-paritás; élő configleltár alapján dönthető.
- ICEsmpadditions/FarmProtect: natív megfelelő kézi teszt után fogadható el.

Autoritatív döntési mátrix:
[EXTERNAL_PLUGIN_STATUS.md](EXTERNAL_PLUGIN_STATUS.md).

## 32. Tudatosan nem támogatott funkciók

- jutalmazó AFK-zóna, zónaidő, payout és AFK bossbar;
- lay;
- crawl;
- stacking;
- más játékos vagy NPC megülése;
- teljes GSit-paritás;
- teljes TAB-klón;
- a külső pluginok ismeretlen vagy nem IceSMP által használt upstream funkciói.

Ezeket nem szabad aktívként, „hamarosan automatikusan érkezőként” vagy
deployment-előfeltételként kommunikálni.

## 33. Ismert korlátok

- Az élő külső config, adatfájlok, adatbázis és teljes pluginlista nem állt
  rendelkezésre; opcionális feature-ről csak capability-szintű állítás tehető.
- A configleltár öt kódban olvasott, de bundled defaultból hiányzó pathot és egy
  integer/long típuseltérést jelöl. Ezeket a configreferencia név szerint
  dokumentálja; staging override és runtime teszt szükséges.
- A data registryben lévő ID nem automatikusan jelent fizikailag kiépített
  helyszínt, bekötött NPC-t vagy játékosok számára elérhető tartalmat.
- A forrásban meglévő integration bridge nem igazolja a külső plugin konkrét
  verziójának runtime kompatibilitását.
- A CI nem production runtime bizonyíték.
- A plugin következő platformportja (26.2) külön review- és migrációs kör lesz;
  ez a csomag a jelenlegi 1.21.11-es tesztalapot dokumentálja.

## 34. Deployment előtti teendők

1. Mentsétek az élő plugin JAR-t, configot, adatfájlokat és világadatot.
2. Ellenőrizzétek a cél JAR hashét és a Java/Paper/Folia verziót.
3. Hasonlítsátok össze az élő configot a
   [konfigurációs referenciával](../reference/CONFIGURATION_REFERENCE.md).
4. Távolítsátok el a stale AFK-zóna-, lay/crawl/stacking- és legacy kulcsokat.
5. Állítsátok össze a permissionmátrixot; különítsétek el a read/edit,
   visibility és recovery jogokat.
6. Stagingen hozzátok létre/ellenőrizzétek a crate-, territory-, dungeon-,
   parkour-, NPC- és eventhelyeket.
7. Futtassátok végig a teljes
   [acceptance checklistet](RELEASE_ACCEPTANCE_CHECKLIST.md).
8. Ne távolítsatok el replacement plugint az adott acceptance fejezet zöld
   eredménye előtt.
9. Minden hibához rögzítsetek szerverlogot, config snapshotot, reprodukciót,
   érintett UUID-t/helyet és várt–kapott eredményt.
10. Csak visszaállítási tervvel és kijelölt felelőssel menjen production rollout.

## 35. Fogalomtár

| Fogalom | Jelentés |
|---|---|
| Deployed baseline | A szerveren jelenleg futó, csatolt `1.0-TESTING` bináris |
| Git-baseline | A fejlesztési változáslista rögzített kezdőcommitja |
| Dokumentált HEAD | A forrásállapot, amelyről ez a csomag állít |
| Capability | A JAR/forrás által támogatott képesség; nem feltétlenül élőben engedélyezett |
| Bundled default | A JAR/repository által szállított alapkonfiguráció |
| Live override | Az élő szerveren admin által módosított külső config |
| Persistence | Restartot túlélő állapot |
| Settlement | Jutalom vagy tranzakció ellenőrzött véglegesítése |
| Recovery | Megszakadt vagy bizonytalan művelet helyreállítása |
| Fault injection | Szándékosan előidézett hiba a recovery bizonyítására |
| Runtime acceptance | Valódi szerveren végzett kézi átvételi teszt |
| Rollout-kapu | Olyan teszt vagy döntés, amely nélkül nem vezetünk be/távolítunk el |
| Out of scope | Tudatosan nem támogatott funkció |

## 36. Rövid verzió- és technikai függelék

| Terület | Bizonyított release-adat |
|---|---|
| Forrásfájlok | 545 main Java-forrás |
| Root parancsok | 68 |
| Aliasok | 79 root alias + 93 routing alias |
| Bizonyított permissionök | 44 |
| GUI-holderek | 22 |
| Lifecycle persistence owner | 34 |
| Konfigurációs/adat pathok | 13 550 egyedi dokumentált path (12 223 scanner + 1 327 nyers bundled leaf-kiegészítés) |
| Üzenetkulcsok | 1 614 nem üres kulcs (resource + kódbeli fallback unió) |
| Kaszt / specializáció | 13 / 35 |
| Profession / profession-spec | 8 / 16 |
| Quest / achievement | 160 / 21 |
| Professionrecept / blueprint | 438 / 54 |
| Crate / relikvia / rituálé | 2 / 6 / 21 |

További technikai bizonyítékok:

- [Parancsreferencia](../reference/COMMAND_REFERENCE.md)
- [Permissionreferencia](../reference/PERMISSION_REFERENCE.md)
- [Konfigurációs referencia](../reference/CONFIGURATION_REFERENCE.md)
- [Üzenetkulcs-referencia](../reference/MESSAGE_REFERENCE.md)
- [GUI-referencia](../reference/GUI_REFERENCE.md)
- [Adatvezérelt tartalomkatalógus](../reference/DATA_CONTENT_CATALOGUE.md)
- [Dokumentációs lefedettség](RELEASE_DOCUMENTATION_COVERAGE.md)
- [Bizonyítékmátrix](RELEASE_EVIDENCE_MATRIX.md)
- [Print-friendly release pack](ICESMP_RELEASE_PACK.md)
