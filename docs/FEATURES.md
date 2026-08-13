# IceSMP funkciókatalógus

<!-- icesmp-doc-id: guide.features -->

Ez a fájl a plugin és a csomagolt konfiguráció jelenlegi, játékosként
érzékelhető állapotát foglalja össze. Nem feladatlista és nem changelog:
ha valami itt „kész", az azt jelenti, hogy a forrásban és a default
konfigurációban megvan a működő útja. A külső pluginek, világépítés vagy
éles szerverkonfiguráció ettől még igényelhet külön deploymentet.

A részletes használat a [játékoskézikönyvben](PLAYER_GUIDE.md), a
világépítői kötéseket a [builderútmutatóban](BUILDER_GUIDE.md), a
stafffolyamatokat az [adminútmutatóban](ADMIN_GUIDE.md), a még nyitott
fejlesztési irányokat pedig a [ROADMAP.md](../ROADMAP.md) tartalmazza.

## Rövid állapotkép

| Terület | Jelenlegi állapot |
|---|---|
| Karakter | 13 kaszt, 35 specializáció, talentek, 8 szakma |
| Tartós játékosállapot | PlayerProfile v2 az egyetlen authority; moduláris szekciók, CAS/WAL tranzakciók, lifecycle teardown |
| Frakciók | Láng, Fagy, Menedék, Kitaszítottak; külön Menedék-vendég kezdőállapot |
| Harc | kaszterőforrások, spell-cast, combat tag, sebzéstípusok, frakciópasszív-precedencia |
| Tárgyak | rarity/affix, signature craft, unique anyagok, rúnák, relikviák |
| Világ | territóriumok, claimek, kazamaták, világesemények, rontás, karaván, meteor |
| Gazdaság | négy valuta, bank, váltó, piac/aukció, adó, frakciókassza, crate-ek |
| Közösség | party, céh, tanács, király/raid/szezon, achievement, ranglista, Krónika |
| Admin | natív moderáció, invsee, config editor, inspect, event/debug eszközök |
| Kliens | first-party resource pack + opcionális IceSMP Client protokoll-alap; vanilla kliens fallback |

---

## Karakter és PlayerProfile v2

A tartós játékosállapot egyetlen authorityje a **PlayerProfile v2**. A
kaszt, specializáció, szakma, gazdaság, quest, achievement, statisztika,
moderációs összegzés, crate-számlálók, weekly goal és halál-escrow nem
külön player-YAML/PDC rendszerekben él.

Fő tulajdonságok:

- moduláris, típusos profile-szekciók;
- revision/CAS alapú mutációk;
- több szekciót érintő tranzakciónál write-ahead journal;
- crash utáni recovery;
- session-generation fencing: egy régi async callback nem írhat az új
  belépési generációra;
- quarantine és explicit admin recovery hibás profilnál;
- teljes lifecycle teardown executorral, HTTP listenerrel és service-regisztrációval együtt;
- opcionális read-only HTTP API, alapból kikapcsolva, auth-first névfeloldással.

A PlayerProfile nem kliens-authority: az opcionális kliensmod csak szerverről
kapott projectiont jeleníthet meg, döntést nem hoz.

## Kasztok és specializációk

A szerver 13 kasztot kezel:

**Varázsló, Harcos, Íjász, Orgyilkos, Druida, Paplovag, Halállovag, Sámán,
Szerzetes, Pap, Boszorkánymester, Démonvadász, Sárkányidéző.**

Összesen 35 class-specializáció létezik. A modern class/spec rendszer
Profile v2-backed:

- első és — megfelelő szint után — második loadout slot;
- specenkénti doctrine/mesterség/capstone állapot;
- slotonként izolált mechanikaállapot;
- DARK/sinner kapuk a sötét specializációknál;
- biztonságos loadoutváltás harcon kívül;
- teljes kaszt-reset csak adminfolyamat;
- kaszthoz kötött spell-grant ledger: `BASE`, `SPEC`, `TALENT`, `QUEST`,
  `ADMIN` provenance, így spec- vagy talentváltás nem töröl idegen forrásból
  szerzett képességet.

A 13 kaszt gameplay-v2 implementációja saját strukturált mechanikaállapotot
ad a HUD-nak (pl. Harcos Csatatempo, DK Rúnakör, Sámán Totemkerék,
Szerzetes Áramlás, Varázsló Rúnaszövés). A részletes számokat és doctrine
választásokat a játékbeli karaktermenü mutatja.

### Lélekkapocs és cast

Minden kaszt kap egy védett, kaszt-tematikus **Lélekkapocs** katalizátort.
A tárgy nem dobható el, halálkor megmarad, elvesztéskor a profilmenüből
pótolható. A cast a szerveroldali spell-registryből indul; a resource pack
és a kliensmod nem authority.

### Kaszt-HP

A kasztonként eltérő base HP rendszer (`health.enabled`) a csomagolt configban
alapból **kikapcsolt**. Bekapcsolása külön PvE/PvP balanszkapu.

## Talentek

Külön kaszt- és szakmapontokból épülő talentfák működnek. A rendszer kezeli:

- rangokat;
- előfeltételeket;
- kölcsönösen kizáró ágakat;
- tier-költési küszöböt;
- capstone-t;
- aktív képességet adó talentet;
- respec utáni grant-visszavonást.

A talentpont- és kiválasztási állapot PlayerProfile-backed.

## Szakmák és receptek

Nyolc szakma:

- gyűjtögető: **Bányász, Gyógynövényész, Favágó**;
- készítő: **Kovács, Alkimista, Bűvölő**;
- másodlagos: **Halász, Szakács**.

A szakmai rendszer tud:

- XP/szint;
- primary slotokat;
- szakmaspecializációt;
- determinisztikus receptkatalógust;
- blueprint-only recepteket és tervrajztanulást;
- heti szakmai célokat;
- signature craftot;
- egyedi profession-materialeket;
- rúnákat és mestermű-minőséget.

A recept-regisztráció reloadkor determinisztikusan takarítja a saját
korábbi recipe key-eit, így nem halmoz duplikációt.

## Tárgyraritás, affixek és unique tárgyak

A rarity-létra:

**Ócska → Közönséges → Nem mindennapi → Ritka → Epikus → Legendás → Ereklye.**

A rarity meghatározza a színt, affixszámot, erősséget és negatív esélyt.
Mobloot, bossloot és profession-craft ugyanazt a közös rendszert használja,
de eltérő tier-súlyokkal.

Támogatott affixek többek között:

- max HP;
- armor/toughness;
- movement speed;
- attack damage/speed;
- spell power.

A rendszer PDC-vel jelöli a már rollolt darabot, hogy ne lehessen újra
sorsolni. A signature tárgyak és unique anyagok saját item modelt kaphatnak.

## Relikviák

Világ-egyedi relikviák működnek tartós központi ownership authorityvel.

- egy aktív tulajdonos relikviatípusonként;
- ownership és fizikai tárgy külön kezelt;
- lost/reclaim állapot;
- inaktivitási lejárat;
- fegyverrelikviánál PvP-transfer;
- világcommit + pending operation receipt a claim/reclaim/transfer fizikai
  mellékhatásaihoz;
- join recovery;
- központi tulajdonos nélkül a stale fizikai példány fail-closed, nem használható.

### Class Relic Framework

Külön class-relic domainréteg él a generikus relikviák fölött:

- class binding;
- Class Power;
- Spec Resonance routing;
- Awakening durable cooldown;
- Profile v2 class/spec authority;
- fizikai birtoklás-kapu.

A jelenlegi pilot a **Sárkánytojás-töredék / Evoker**. A teljes 13/35
katalógus rollout-kapu mögött marad; a framework nem gyárt automatikusan
hiányzó relikviákat.

## Rituálék

Multi-block oltár rendszer működik. Egy rituálé konfigurálhat:

- magblokkot és szerkezetet;
- sacrifice listát;
- class/faction kaput;
- cooldownot;
- relikvia-idézést;
- cleanse/buff/home/egyéb dedikált outcome-ot.

A konkrét oltár geometriája config, nem hardcoded univerzális minta.

## Frakciók és passzívok

Négy frakció van:

- `RED` — Láng / Perinfernicitas;
- `BLUE` — Fagy / Cryghaliris;
- `NEUTRAL` — Menedék / Ryanora & Caldestera;
- `DARK` — Kitaszítottak.

Az assignment nélküli új játékos **Menedék-vendég**, nem implicit NEUTRAL.
Explicit választásig nincs frakciópasszív, polgári adó, tanácsi jog,
community- vagy season-credit.

A passzívok központi precedence policy szerint működnek. Scriptelt
boss/dungeon/event célzás, koronaátok, provokáció, Vérhold és más magasabb
prioritású helyzetek felülírhatják az egyszerű ambient előnyt.

- RED: környezeti tűz/hő kárcsökkentés;
- BLUE: fagyásimmunitás, fulladáscsökkentés, kijelölt természetes exhaustion esélyes elkerülése;
- NEUTRAL: fél zuhanáskár és korlátozott spontán békés/semleges aggróvédelem;
- DARK: fél Wither és kontextusos undead-truce.

A DARK undead-béke provokációra páronként visszavonható, a közelben ténylegesen
riasztott mobok reagálhatnak, Vérhold alatt pedig alapból nem él.

## Bűn, körözés és Kitaszítottak

A bűnrendszer kezeli a bűnpontot, körözött státuszt, vérdíjat és a
küszöb feletti száműzetést. A DARK nem egyszerűen választható negyedik
szín: önkéntes belépéshez bűnös állapot és megerősített paktum kell, vagy
más történeti út vezetheti oda a karaktert.

A Suttogó-réteg kódszinten működik, de tulajdonosi döntés szerint **nem
publikus feature-dokumentáció**. Ezen a fájlon kívül se részletezd játékosnak.

## Király, tanács, raid és háború

A harcos frakciók királyt választanak; a Menedék Vének Tanácsát.

A király:

- kasszát kezelhet napi limittel;
- adókulcsot állíthat;
- raidet indíthat;
- frakciószállítmányt kezelhet.

A raid külön `PREP`/`ACTIVE`/settlement életciklus, jelentkezőkkel,
objektívával és season reward útvonallal. A RED↔BLUE hadi-ablak külön,
configurálható pontforrás. A becsületpárbaj beleegyezéses kivétel a normál
PvP/bűnszabály alól.

## Szezon és közösségi célok

A SeasonManager frakcióliga:

- tartós szezon sorszám és start timestamp;
- frakciónkénti pontok;
- forrássúlyok;
- grand-finale window;
- season close után következő generáció;
- közösségi cél state külön season markerrel;
- lezárási reward outbox/receiptek;
- PlayerProfile member rewardok.

A szezonzárás **nem wipe**.

A közösségi célok kill/collect/break/worldboss/raid jellegű célokat tudnak,
frakció- vagy szerver-szinten. Teljesítésük tartós contribution receipttel és
pending completion outboxszal rendelkezik.

## Territóriumok

A `TerritoryManager` zónákat kezel típussal, frakcióval és pontos
határral. Főbb típusok:

- faction territory;
- protected territory;
- protected city;
- capital;
- `DOOM_GATE`;
- dungeon.

A központi `TerritoryProtectionService` kezeli a build/interact/fire/
explosion/PvP policykat; az admin bypass és a builder szerep külön permission.

### Kárhozat Kapuja

A Doom Gate magas veszélyű PvPvE senkiföldje. Alapból:

- PvP legális;
- belépési grace létezik, támadással elveszik;
- ölés nem bűn;
- mob-level bónusz;
- build/robbanás/tűz védelem;
- speciális hangulat.

A vanilla saját Nether-portál gyújtása alapból tiltott; a világ egyetlen
legitim kapuja a Kárhozat Kapuja. Az End külön tulajdonosi policy szerint
zárva marad a későbbi admin-eseményig.

## Claimek

A személyes claim:

- rectangle és pontos poligon alak;
- bounded Y tartomány;
- overlap/area limit;
- trust;
- block/container/fluid/piston/fire/explosion védelem;
- vizuális boundary preview;
- WorldGuard/territory konfliktuskapu;
- reload/mentés;
- admin műveletek.

A claim nem ad automatikus PvP-védelmet.

## Világesemények

A world-event orchestration több egymástól eltérő eseményt kezel:

- vérhold;
- world boss;
- invázió;
- Vad Hajsza;
- kereskedő-karaván;
- kincs;
- gyűjtőbuffok;
- Bőség-idő;
- szerver-kihívás;
- escort;
- meteor;
- rontás;
- kultisták;
- Stranger NPC;
- ambient események.

A `MajorEventGate` megakadályozza a konfliktusos nagy események torlódását.

### EventSpawnGuard

Közös, fail-closed placement policy:

- territory / player claim / WorldGuard szűrés;
- víz- és partpuffer;
- világspawn és world-border;
- player distance;
- dinamikus view/send distance;
- nézési kúp;
- biome profile;
- footprint és lejtés;
- loaded/generated chunk szabály;
- bounded async keresés;
- concurrency budget;
- reservation és recent-location memória;
- arrival warning.

Az event mobok transient entity registrybe kerülnek; a registry captured
entity schedulerrel takarít, nem globális UUID entity lookupból.

## Meteor és terrain-visszaállítás

A meteor valódi, ideiglenes krátert képes létrehozni. A terrain-módosítás
write-ahead, restartálló journalból áll helyre. A blokkmutáció region-scheduleres,
a restoration bounded batch-ekben fut.

## Rontás

A corruption rendszer markerelt rontás-gócot, aura-sebzést, ellenségeket és
megtisztítást kezel. A terrain/zone kezelés és a jutalom a közös
world-event/loot infrastruktúrát használja.

## Kazamaták

A dungeon rendszer kezeli:

- belépő kulcsot és party-közös nyitást;
- per-player pass/cooldown állapotot;
- protected dungeon territoryt;
- mob-level bónuszt;
- regisztrált személyes ládákat;
- mini-bosst és visszatérési időt;
- boss/miniboss lootot.

A kód nem bizonyítja, hogy az összes dungeon fizikailag megépült az aktuális
világon; ez builder/deployment feladat.

## Quest Framework v2

A quest nem globális „accept bárhonnan" lista. Minden definíciónak explicit
forrása van:

- NPC;
- Megbízások-tábla;
- lánc;
- helyszín;
- item;
- esemény.

A rendszer támogatja:

- pickup és turn-in source authorityt;
- NPC dialogot és multi-quest választót;
- signed, egyszer használatos choice tokent;
- `READY_TO_TURN_IN` állapotot;
- tracked questet;
- ötfüles quest logot;
- rejtett/felfedezendő questet;
- kategóriát és repeatability policyt;
- exact source/NPC auditot;
- teljes gráfvalidálást;
- PlayerProfile progress/felfedezés/audit state-et;
- pending fizikai reward recoveryt.

A 160 csomagolt quest definíció a v2 forrásmodellre van migrálva. A fizikai
NPC-k és helyszínek ettől még builder-kapuk.

## Bestiárium és felfedezés

Négy kategória:

- mobok;
- profession recipe-k;
- territóriumok;
- világboss-archetípusok.

A szörnybejegyzés kill countot, első elejtési dátumot és több tudásfokozatot
kezel. Az ismeretlen tartalom `???`. A discovery/achievement state
PlayerProfile-backed; a global first discoverer külön világ-aggregátum.

## Party

A party frakciófüggetlen, max. ötfős kalandcsapat:

- meghívás/elfogadás;
- leader/promote/kick;
- party chat;
- közeli XP-share;
- personal loot;
- partybar;
- párton belüli friendly-fire tiltás;
- dungeon party key támogatás.

## Céh

A céh tartós, frakción belüli közösség. Taglista, rang, XP/szint, kassza és
heti céhcél tartozik hozzá. A céh nem helyettesíti a party-t.

## Gazdaság

Négy valuta:

- Parázsló Parals;
- Hópihér-veret;
- Creutzér;
- Csontveret.

A bank kezeli a PlayerProfile bankegyenleget és a fizikai veret deposit/
withdraw utat. A váltó dinamikus árfolyamot, napi limitet és díjat használ.

### Adó

A frakcióadó:

- alap százalék + fejadó;
- eredet-frakcióhoz kötött hátralék ledger;
- frakcióváltás nem mossa vagy konvertálja át;
- ismételt nemfizetés bűnkövetkezményt kaphat;
- explicit NEUTRAL polgár adómentes;
- Menedék-vendég nincs a polgári beszedési körben.

### Piac és aukció

A market fixáras és licites tételeket kezel:

- listing fee;
- valuta;
- auction end/buyout;
- zárolt licit és refund;
- claimable item;
- relikvia tiltás;
- season/faction gazdasági policyk.

### Adományláda

Tartós, ingyenes közösségi item pool. A beadás és átvétel write-ahead/recovery
útvonalon megy, hogy disconnect vagy crash se duplázzon/elvesszen tárgyat.

## Crate rendszer

A natív crate-rendszer 8 default crate-et ad:

- Köznapi;
- Ritka;
- Hősi;
- Mitikus;
- Mesterség;
- Expedíció;
- Hadizsákmány;
- Arkánum.

A normál használathoz nincs crate-specifikus permission-követelmény. Van:

- kulcsvásárlás;
- preview;
- súlyozott loot;
- random blueprint;
- crafted és affixed item outcome;
- unique anyag;
- hologram/ItemDisplay presentation;
- tartós open statistics és recovery.

A vanilla inventory-rulett nincs használva; a világban lévő ItemDisplay
mutatja a reveal-t. Elytra crate-lootból tiltott.

## HUD

A HUD first-party rendszer; külső BetterHud nem authority.

### First-party resource-pack HUD

- öt faction/theme keret, külön vendégkerettel;
- class/spec/szint;
- health/food/armor;
- class resource;
- generic metric slotok;
- charge/rúna/mechanika ikonok;
- cooldown/proc állapotok;
- target és event információ;
- négy fix wallet slot;
- 13 class strukturált mechanikái;
- resource-pack readiness routing;
- vanilla/compact fallback.

A shader a képernyőméretből normalizál, támogat pozíció- és scale-variánsokat.

### HUD editor

`/hud edit`:

- personal és admin global scope;
- komponensválasztás;
- X/Y mozgatás;
- 1/5/10/15-ös lépés;
- közvetlen értékbevitel;
- scale;
- anchor/layout/margin;
- visibility;
- preset/preview;
- undo/reset;
- mentés.

A személyes layout PlayerProfile-backed, restartálló és **sparse override**:
amit a játékos nem módosított, tovább követi a global defaultot.

## Resource pack

A plugin Paper `addResourcePack` API-val adja hozzá a saját csomagot, nem
írja felül a szerver vagy más plugin packját. A publikált URL és SHA-1 build
metadata; stagingre explicit override használható.

A pack támogat egy külső immutable base + first-party IceSMP overlay
determinisztikus merge pipeline-t. Buildoldalon asset-audit és package
validáció ellenőrzi a HUD manifestet, glyph-méreteket és positioning shadert.

## IceSMP Client protokoll-alap

A szerveren elkészült az opcionális kliensmod transportfoundation:

- plugin messaging transport;
- HELLO/ACK/REJECT handshake;
- negotiated protocol;
- session generation;
- capability lista;
- rate limiting;
- bounded/strict UTF-8 envelope;
- vanilla kliens fallback;
- `/icesmp client` admin diagnosztika/resync.

A `client.features.*` modulkapuk alapból kikapcsoltak; a Fabric kliens és a
native UI/cast funkciók külön rollout-fázisok. A kliens nem lehet authoritative.

## Natív moderáció

A saját moderáció lefedi:

- warn;
- kick;
- mute/temp mute;
- ban/temp ban/unban;
- büntetési history;
- report;
- social spy;
- vanish;
- offline teleport;
- moderation GUI;
- online inventory/enderchest inspection és szerkesztés.

Az inventory editor single-writer és escrow/rollback védelmet használ. A
vanish per-viewer world + tab-list visibilityt kezel, nem Bukkit
invulnerabilityt; a `vanish-see` külön explicit permission.

## AFK

Natív globális AFK state működik manual `/afk` és automatikus inaktivitási
idő alapján. A TAB és más fogyasztók ugyanazt a state-et olvassák. A korábbi
AFK reward-zóna nincs a first-party termékben.

## Ülés és tábortűzi történetek

A natív sit rendszer block-seat policyt, helyfoglalást és lifecycle cleanupot
használ. A lépcső/szék ülési pontját a blokk geometriájához igazítja.

Campfire story csak akkor indul, ha a játékos ténylegesen ül, és az elrendezés:

**campfire → egy üres blokk → szék/ülőblokk**

a négy főirány egyikében. A rendszer indulás és jutalom előtt is újraellenőrzi
a széket és a tábortüzet; közvetlen campfire-kattintás nem indít történetet.

## MOTD

Natív MOTD rotáció és event-priority támogatás működik, saját ikonválasztással
és tesztelt sorrenddel.

## Tablista és faction display

LuckPerms meta alapján rangsorolt tablista, AFK játékosokkal a végén.
Faction palette központi, ugyanazt a színt fogyasztja a tab, nametag, chat és
PAPI projection. A Menedék zöld, nem keveredik a DARK megjelenéssel.

## Config és adminfelület

A `ConfigManager` a fő `config.yml` plusz támogatott subsystem YAML fájlokat
egyesíti, override réteggel és atomikusan publikált snapshotokkal.

Van:

- `/icesmp reload`;
- ingame config override;
- staged config GUI save/cancel/reset;
- optimistic concurrency;
- advanced text/list input;
- live apply a támogatott rendszereknél;
- config schema/GUI coverage regression.

A configmenü nem ígér runtime apply-t olyan kulcsra, amelynek fogyasztója csak
induláskor épül fel.

## Világépítői/admin kötési eszközök

A forrás támogat:

- territórium kijelölést és spawnokat;
- poligon claimet;
- selection wandokat;
- NPC bindet;
- parkour pontokat;
- event spawnpointokat;
- dungeon chestet;
- kompútvonalat;
- crate helyeket;
- exchange boardot;
- admin inspectet.

A tényleges koordináták, NPC-k és építmények az élő világ deploymentjéhez
tartoznak, nem kerülnek hardcoded értékként ebbe a katalógusba.

## Fallback és integráció

A plugin Folia-first. Külső integrációk közül több opcionális:

- PlaceholderAPI — projection;
- LuckPerms — rank/meta;
- WorldGuard — protection bridge;
- FancyNpcs — fizikai NPC;
- LibsDisguises — egyes vizuális funkciók.

A saját core state nem függhet attól, hogy egy opcionális display/integráció
épp rendelkezésre áll-e.

## Ami nem tekinthető kész világ-tartalomnak pusztán a kód miatt

A plugin támogathat egy helyszínt vagy NPC-t úgy, hogy az adott production
mapon még nincs megépítve. Külön builderkapu többek között:

- 18 quest/NPC szerep;
- territóriumok és frakcióspawnok;
- kezdő parkour;
- Thanaopolis;
- hidden spot koordináták;
- dungeon belsők és ládák;
- egyes event spawnpointok;
- rituáléoltárok;
- intro waypointok.

A pontos kötési lista és acceptance a [builderútmutatóban](BUILDER_GUIDE.md)
és az [adminútmutatóban](ADMIN_GUIDE.md) él.

---

<sub>Dokumentált release: `c58780d912be0a5c3df08c8cf7bd60e7f88f4271`</sub>

## Season 0 / Prologue — Kárhozat Kapuja

A Prologue egyszeri nyitó világkorszak, külön authorityvel; a normál
`SeasonManager` továbbra is Season 1+ frakcióligát kezel. A tartós
`PrologueManager` state machine a Kapu eszkalációját, finálé-checkpointjait és
az irreverzibilis átmenet receipteit őrzi.

A kész szerveroldali feature-ek:

- `SILENCE → CRACKS → LEAK → COLLAPSE` eszkaláció és kapustabilitás-HUD/fallback;
- Season 0 class-XP ceiling (default 25), spec/relic/blueprint/felső loot content gate;
- Season 0 alatt Overworld→Nether travel tiltás, Season 1-ben is csak a kijelölt
  Olethropyla-anchor legitim; saját FIRE portálok továbbra is tiltottak;
- reusable `MINOR/MAJOR/CRITICAL` Gate Breach encounter participant-count scalinggel;
- külön checkpointolt Prologue finale, event-context PvP ceasefire és ugyanazon
  encounter útvonalat használó `--rehearsal`;
- production pause alatt pending spawn, AI, event damage, boss mechanika és timeout
  is megáll; a hátralévő timeout restartálló receiptből folytatható;
- boss-halál után azonnali in-memory spawn latch, finaleId-kötött durable
  pending/victory receipt és persistence-hibánál fail-closed pause;
- Gate unlock → Profile v2 prestige reward → rendkívüli Krónika → Prologue emlékmű
  → friss Season 1 start receipt/activation idempotens settlement;
- Founder/finale státusz presztízs, nem power; offline jogosult Profile v2-ready
  belépéskor ugyanazt az egyszeri státuszt kapja meg;
- Season 0→Season 1 között nincs wipe.

A feature runtime-ja nem tartalmaz autoritatív map-koordinátát. A kiadáshoz a
`prologue-gate`, `prologue-gathering`, `prologue-breach` és `prologue-boss`
world hookok tényleges builder-beállítása és staging acceptance szükséges.
Az End nyitása, az Első Csend megfejtése és a Néma Királynő végjátéka nem a
Prologue feature scope-ja.
