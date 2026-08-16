# IceSMP — fejlesztési ütemterv

Ez az IceSMP egyetlen előre tekintő tervdokumentuma. Csak azt tartalmazza,
ami még valóban nyitott, elkötelezett következő lépés vagy külön
tulajdonosi döntésre váró irány.

- A jelenlegi játékállapot: [docs/FEATURES.md](docs/FEATURES.md)
- A legutóbbi változások: [docs/LATEST_CHANGES.md](docs/LATEST_CHANGES.md)
- Az üzemeltetési és átvételi folyamat:
  [docs/ADMIN_GUIDE.md](docs/ADMIN_GUIDE.md#release-acceptance-checklist)
- A világépítői előkészítés: [docs/BUILDER_GUIDE.md](docs/BUILDER_GUIDE.md)
- A technikai alapelvek: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)

Az implementáció megítélésében mindig a végleges forrás és a csomagolt
konfiguráció a mérvadó. Egy zöld build nem helyettesíti a Folia
runtime-tesztet, egy lore-ban szereplő hely pedig nem helyettesíti a
világban elvégzett bekötést.

Jelölések:

- 🚧 **kiadási kapu** — rollout előtt kötelező;
- ⬜ **elkötelezett fejlesztés** — része az A–H tervnek;
- ◇ **builder- vagy runtime-kapu** — kézi előkészítést, illetve próbát igényel;
- 💡 **ötlet** — értékes irány, de még nincs ütemezve;
- ⏸ **döntésre vár** — tulajdonosi vagy design-döntés nélkül indul.

## 1. Következő kiadási kapuk

### 1.1. Kiadásblokkoló

- 🚧 **H-ECON-001 — több tartományt érintő gazdasági crash-ablak.**
  A bank- és claimfolyamatok egy része memóriát, inventoryt és
  több külön állományt módosít, de ezekhez nincs közös, tartós commitpont.
  A megoldási irány szűk WAL/pending rekord: az irreverzibilis lépés előtt
  tartós műveleti rekord, majd idempotens induláskori recovery. Teljes
  wallet- vagy claim-snapshotot nem szabad régiószálon szinkron írni.
  A minta már létezik a repóban (`RespecTransactionJournal` +
  `RespecRecoveryProtocol`, `DurableTransactionProtocol` +
  `DurableRecoveryPolicy`, `FactionSwitchJournal`) — ide ezt kell rákötni,
  nem újat tervezni. A `ClaimManager`-ben jelenleg nincs journal; a
  `CurrencyManager.deposit:555-592` az itemeket az enqueue-olt
  wallet-mutáció tartós kiírása ELŐTT veszi ki az inventoryból (logikai
  hibára van kompenzáció, crashre nincs).

**Kilépési feltétel:** a normál út, lemezhiba és több időablakban
megszakított folyamat is bizonyítottan ugyanarra az eredményre áll helyre;
nincs dupla kifizetés, elveszett tárgy vagy kifizetett, de létre nem jött
claim.

### 1.2. Megerősített technikai adósság

Ezek nem mind kiadásblokkolók, de a forrásban még létező rések. Az
implementálásuk előtt tételenként újra kell igazolni a kiváltási utat.
A lista tételei 2026-08-14-én forrásban ellenőrizve; ami azóta elkészült,
az ki van véve (kereskedő-karaván spawnútja: `CaravanManager:176-192`
`EventSpawnGuard` + generáció-újraellenőrzés a hopolt callbackben).

- 🚧 A `claims.yml` hibás szemantikai rekordját a loader jelenleg
  kihagyhatja (`ClaimManager.load:1184-1187`), a következő `flushToDisk`
  pedig már csak a túlélő claimeket írja vissza — így véglegesíti az
  adatvesztést. Fail-closed betöltés, karantén és látható mentési hiba
  szükséges.
- ⬜ A hosszú életű report-, cooldown- és debounce mapekhez explicit
  purge-szabály kell. A HUD/parkour kick-út ELŐTT tisztázandó, hogy valós
  rés-e: a `ParkourListener` csak `PlayerQuitEvent`-et kezel, de a Paper
  kick után is dob quit-eventet — ezt runtime-próbával kell eldönteni, nem
  kódolvasással (a kaszt-service-ek eddig külön kezelték a kicket).
- ⬜ A legacy `claims.block-in-*` beállításokat egyértelmű, validált sémára
  kell migrálni.
- ⬜ A GUI-kban maradt közvetlen szövegek kerüljenek a
  `MessageManager`-be.
- ⬜ A `/icesmp reload` csak valóban sikeres validálás után küldjön
  sikerüzenetet. A quest-hibáról már megy külön üzenet
  (`IceSMPCommand:131-135`), de a siker-üzenet utána feltétel nélkül elmegy,
  és a `ConfigValidator.validate` `void` — előbb visszatérési értéket kell
  adnia, hogy legyen mire kapuzni.
- ⬜ A `ProtectionBridge` konkrét policy/flag alapján döntsön; a
  `queryProtected:96-101` jelenleg `getApplicableRegions(...).size() > 0`
  alapján minden WorldGuard-régiót tiltott területként kezel.
- ⬜ A `/menu` adjon utat a `/tanacs`, `/komp` és `/faction war`
  funkciókhoz; staff-elemet csak megfelelő jogosultsággal mutasson. A két
  parancs regisztrálva van (`IceSMPCore:1812-1813`), csak a `CommandMenus`
  csempéje hiányzik.
- ⬜ Tanácsszavazásnál játékidő-alapú alt-védelem, az ambient jutalmaknál
  napi keret, a parkournál tartós ranglista szükséges.
- ⬜ Vanishben a publikus chat némán eldobódik
  (`VanishListener.onChat:155-161`, `moderation.vanish.allow-chat: false`) —
  a játékos nem kap visszajelzést, ami szerverhibának látszik.
  Egy `MessageManager`-kulcs kell hozzá, a blokk szándékos.

**Szakma-katalógus rework (2026-08-15) — lezárva.** A katalógus 437-ről 295-re csökkent, majd
a szakma-identitás pótlásával 376-ra állt be; minden recept kimondja a fajtáját, és a fajta-szabályokat gépi kapu tartja
fenn (`check_consistency.py` + `professionRecipeAuditRegressionTest`). Lezárt tételek:
15 nyersanyag-hurok, 16 hatás nélküli főzet, 13 üres enchantkönyv, 9 loot-ritkaságot
törő recept, a tervrajz-duplikáció, az inaktív szakmával craftolás, a recept-XP heti
célba kötése és a tömeges XP darabszám-alapú jóváírása. A részletek a
`docs/ARCHITECTURE.md` „Recept-fajta szerződés" szekciójában élnek.

- ◇ **Szakma-rework runtime acceptance:** a `docs/ADMIN_GUIDE.md` PROF-01..07 sorai
  productionközeli Folia stagingen még kézi próbát igényelnek — különösen a
  tervrajz-fogyasztás versenyhelyzete, a 16 főzet tényleges hatása és a Méregvonó Pép
  hatás-törlése.
- ⏸ A 15 új identitás-recept balansza (gyógynövényes kenőcsvonal, bányász ásó- és
  szerencsecsákány, favágó erdőjáró szett, két alkimista főzet) élő próbán mérendő:
  a hatás-időtartamok konzervatív kiindulópontok, nem mért értékek.
- ⏸ A szakmák közti XP-tempó (a mért ~11 270 favágó akció vs. ~1 879 alkimista craft
  ugyanazért az 1→50 alap-XP-ért) továbbra is nyitott: nem az akciószámot, hanem a
  várható játékidőt kell kiegyenlíteni, és ehhez mérés kell, nem becslés.
- ⬜ A szezon 41–53. napjának történeti üresjáratát és a túl korán
  elérhető rejtvényeket tartalom- és időkapu-tervvel kell rendezni.
- ⏸ A Mételytépő és a Sárkánytojás-töredék tényleges megszerzési forrása
  tulajdonosi döntést igényel.

**PlayerProfile v2 felülvizsgálat (2026-08-14) — igazolt nyitott pontok.**
A réteg magja (CAS + 2 fázisú WAL + atomikus szekcióírás + karantén) helytálló;
az alábbi rések kódban visszaigazoltak, javításuk tételenként külön döntést kér:

- 🚧 A talent-vásárlás `operationId`-je véletlen UUID-komponenst hordoz
  (`PlayerProfileTalentStore.java:114`), miközben az operation-ledger dedupja
  pontos azonosító-egyezésen áll (`YamlPlayerProfileTransactionManager`) — így a
  talent-út retry/replay elleni idempotencia-védelme hatástalan. Determinisztikus
  azonosító önmagában a respec utáni jogos újravásárlást is elnyelné; a javításhoz
  respec-generáció komponens kell az azonosítóban (tulajdonosi tervdöntés).
- 🚧 Az `enqueue` a teljes session-munkát a `sessionTails.compute` lambdán belül
  fűzi (`PlayerProfileService.java:339-354`): már lezárt előzménynél a `work` és a
  befejező `remove` szinkron, a CHM kulcs-zár alatt futhat — azonos játékosra
  visszahívó listener/beágyazott művelet rekurzív `compute`-ot okoz
  (deadlock/`IllegalStateException`-kockázat). A munkaindítást a compute-on
  kívülre kell vinni.
- 🚧 `YamlPlayerProfileRepository.loadLocked`: a manifest dekódolása nincs
  karanténvédelem alatt (`:96-112`) — sérült manifest a teljes profilbetöltést
  dönti szekció-karantén helyett; deklarálatlan szekciónál a default-írás
  manifest-frissítés nélkül fut; az evidencia-mentés korlátlan
  `Files.readAllBytes`-szal dolgozik.
- 🚧 Quit-út: a `flush → invalidate` sorrend (`PlayerProfileService.java:155-157`)
  ablakot hagy, amelyben a flush után, invalidálás előtt induló írás a régi
  cache-példányon landolhat; a kilépési barrier nem zárja ki a párhuzamos mutációt.
- ⬜ A CAS profil-szintű: az `expectedGeneration` a `profileRevision`
  (`PlayerProfileService.java:191`), így bármely szekció írása minden más szekció
  párhuzamos íróját retryra kényszeríti, a többszekciós tranzakciók pedig nem
  retry-olnak automatikusan — terhelés alatt éhezési kockázat.
- ⬜ A manager-réteg gameplay-útjai régió-szálon blokkolnak a profilműveleteken
  (`.join()` — pl. `CurrencyManager`, `FactionManager`, `SinManager`); a
  virtuális-szálas executor torlódásakor ez régió-tick-lag, a fenti
  compute-rekurzióval együtt rosszabb. Kell egy kimondott szabály: mely utak
  blokkolhatnak, és mekkora időkorláttal.
- ⬜ A szekció-konstruktor limitek jogos növekedésnél is kivételt dobnak és
  egészséges szekciót karanténoznak — pl. `ProfessionSection.recipes` cap 512,
  miközben a receptkatalógus már 437 tételes; headroom-figyelés vagy fokozatos
  bővítési út kell.
- ⬜ `EconomyReceiptLedger.makeRoom`: tele kvóta + aktív replay-ablak esetén
  `IllegalStateException` (`:110-113`) — nagyon aktív játékos jogos jóváírása
  hard-failel. A fail-closed szándékos, de kezelt hibaút és riasztás kell mellé.
- ⬜ A HTTP `sections/<id>` végpont a nyers `snapshot.value()`-t szerializálja
  (`PlayerProfileHttpServer.java:229-231`) a kurált DTO-k helyett — SELF-scope-on
  belső mezők (extensions, receipt-sorok) szivárognak; kurált szekció-DTO kell.
- ⏸ Az invsee-visszaadási sor önálló, tartós player-item authority a
  PlayerProfile-on kívül (`InvseeManager.java:111,122`, `invsee-escrow.yml`), és a
  guild-tagság tárolása is a profilrétegen kívül él — az authority-mátrix alá
  vonásuk (szekció vagy dokumentált kivétel) tulajdonosi döntés.

## 2. Builderkapuk

A kód és a csomagolt config önmagában nem építi meg a szezont. A következő
tételek a szervercsapat feladatai:

- ◇ **18 NPC-szerep** fizikai kihelyezése és `/npcbind` kötése a
  [teljes quest- és NPC-leltár](docs/QUESTS.md) alapján;
- ◇ a szükséges **4 territory ID** kijelölése, majd a **4 frakcióspawn**
  pontos állóhelyének és nézési irányának mentése;
- ◇ a `kezdo_parkour` pálya megépítése és bekötése;
- ⬜ a `dark-capital` quest-territory és a kanonikus `thanaopolis` ID
  egységesítése a world build előtt;
- ⬜ a `merchant_choice` választási időzítésének javítása, valamint a két
  pályát említő, de mobölést mérő mester-dialógus összehangolása;
- ◇ a rituáléoltárok és az intro kamera-waypointok megépítése;
- ◇ a `hidden-spots.spots` tényleges helyszínekkel való feltöltése;
- ◇ a kazamaták belső tereinek, ládáinak és bosslootjának elkészítése;
- ◇ minden használt crate, kompút, karavánmegálló és más fizikai kötés
  leltározása az élő világban.

**Kilépési feltétel:** minden kötésnek van felelőse, pontos azonosítója,
koordinátája, pozitív és negatív próbája, valamint visszaállítható mentése.

## 3. Runtime- és balanszkapuk

- ◇ Az A17 kaszt-HP rendszer alapból ki van kapcsolva. Bekapcsolás előtt
  egységes pajzs/abszorpció-szabály, PvP TTK- és PvE sebzésteszt kell.
- ◇ A frakciópasszív-rework defaultjai csak konzervatív kiindulópontok. A
  `docs/ADMIN_GUIDE.md` teljes membership/RED/BLUE/NEUTRAL/DARK, vegyes
  játékosos, Suttogó- és lifecycle mátrixát productionközeli Folia stagingen
  végig kell futtatni; az automatizált policyteszt nem runtime playtest.
- ◇ Legalább egy teljes szezonban, privacy-safe aggregátumokkal mérni kell
  frakciónként az elkerült sebzést, étel/exhaustion alakulását, halálokat,
  quest- és dungeon-clear időt, eventrészvételt, gazdasági megtakarítást és
  season-source termelést. Csak ezután indokolt a `0.25/0.50/0.75` damage,
  `0.25` exhaustion és `0.50` wild-undead defaultok újrahangolása.
- ◇ Külön nyitott kapu a DARK/non-DARK és NEUTRAL/non-NEUTRAL párok ugyanazon
  mobnál, provokációval és nélküle, régióhatáron át; a játékos–mob retaliation
  lease-ek target-függetlenségét, scheduler rejectiont, retired callbacket és
  state-cleanupot loggal kell bizonyítani.
- ◇ Fault-injection stagingen külön bizonyítandó a fizetős frakcióváltás és az
  adóbeszedés WAL-recoveryje: wallet-write hiba, domain-write hiba, sikeres és
  sikertelen kompenzáció, journal-cleanup hiba, circuit-open és kontrollált
  restart utáni idempotens folytatás.
- ◇ Az Íjász és az Orgyilkos tényleges DPS-ét célbábun és valódi
  harchelyzetben is mérni kell; a DoT és a vanília sebzésréteg miatt a
  papírérték nem elég.
- ◇ 50–60 játékosnak megfelelő terheléssel mérendő a tablista, a
  világesemény-köteg és a pet tickelése.
- ◇ Kötelező a két régiót érintő Folia-próba, kontrollált restart, több
  ponton megszakított folyamat és írásvédett/lemezhibás fault injection.
- ◇ Külső plugint csak a saját acceptance csomagja után szabad kivenni.
  Ez különösen a GSit, CrazyCrates, SModeration, InvSee++, MiniMOTD, TAB,
  ICEsmpadditions és FarmProtect kiváltására vonatkozik.

A PlaceholderAPI, FancyNpcs, WorldGuard, LuckPerms és LibsDisguises
integrációit nem szabad replacementként kezelni addig, amíg a használó
élő funkciók és configok másképp nem bizonyítják.

## 4. Elkötelezett kivitelezési terv

A sorrend szándékos: előbb láthatóság és biztonság, utána kézbesítés,
mélyebb PvE, gazdasági tartalom, világirányítás, titkos történeti réteg,
végül live-ops. Egy későbbi fázis csak akkor induljon, ha az előfeltétele
már tartós és megfigyelhető.

### A — Production Visibility

- ⬜ `/icesmp health`: read-only operátori pillanatkép
  storage/WAL-állapotról, autosave-ról, eseménykapuról, átmeneti
  entitásokról és integrációkról.
- ⬜ Strukturált content-validálás egységes diagnosztikai modellel és
  `/icesmp validate` paranccsal.
- ⬜ `/bugreport`: kategória, minimális automatikus kontextus,
  rate limit és fingerprint-alapú deduplikáció; chatelőzmény, IP és más
  játékos adata nélkül.
- ⬜ Bounded, append-only `AdminAuditLog` a későbbi admin- és
  live-ops műveletekhez.

**Kapunyitás B felé:** a hibák állapota fájlolvasás és regionális
entitás-hozzáférés nélkül lekérdezhető; a diagnosztika nem tartalmaz
érzékeny adatot.

### B — Inbox és retention

- ⬜ Központi, idempotens `PlayerInboxService` az offline vagy késleltetett
  jutalmakhoz.
- ⬜ `NotificationRouter` és játékosonkénti értesítési preferenciák;
  kritikus üzenet nem némítható.
- ⬜ Többlépcsős, előfeltételes és rejtett achievement-láncok.
- ⬜ Visszatérési összefoglaló, gyorsmenü és szervernaptár a már létező
  Krónika-, szezon- és inboxadatokból.

**Kapunyitás C/F felé:** ugyanaz a jutalom újrapróbálva sem duplikálódik,
offline címzettnél sem vész el, és a titkos kategória nem szivárog
nyilvános felületre.

### C — PvE Depth

- ✅ `MobTemplate` + hibrid 1–50 progression, ahol az explicit encounter/zóna
  felülírja, a távolság/mélység/territory pedig survival-wilderness fallback;
  általános vadon hard cap 70. A pilot 6 authored template-et, vanilla fallbacket,
  12 archetype-vokabulárt és külön bounded HP/damage görbét ad.
- ✅ Elit-affix réteg: kevés, jól olvasható affix, legfeljebb kettő
  mobonként, spawnkor rögzített döntéssel és bounded élettartammal.
- ✅ Eseményvezérelt, legfeljebb 128 résztvevős boss contribution ledger sebzés,
  támogatás, tankolás és objective API-val; a világboss start-snapshotból skálázódik,
  a személyes komponens receipt-alapúan idempotens és tele inventorynál függőben marad.
- ⬜ Személyes harci összefoglaló; nyilvános DPS-szégyenfal nélkül.
- ⬜ Bestiárium 2.0 többszintű kutatással és információs/kozmetikai
  jutalmakkal.

**Release-gate:** a dependency-free domain/source regresszió nem helyettesíti az
exact Java 21 CI-t és a 50–60 fős Folia staging playtestet (region-hop, late join,
disconnect, full inventory, boss despawn/restart, képesség-telegráf olvashatóság).

**Kapunyitás D/E felé:** minden idézett entitás életciklusa rendezett,
a jutalom pénzsemleges, az offline jogosultság az inboxba kerül.

### D — Survival itemizáció, profession és piac

- ⬜ A 16 profession-specializáció tényleges passzívjai és fizetős respec.
- ✅ Controlled reroll (Full Reforge, Stat Lock, Quality Amplifier, Stability Seal),
  deterministic authored ascension és veszteséges salvage szigorú legacy/admin/
  bind tiltással, bounded költséggörbével és item-mutation WAL recoveryvel.
- ✅ Az authored `ItemTemplate`/`ItemInstance`/`ItemIdentityService`, 0–2 rúnahely,
  signature/set fogyasztó és build-aware, restartbiztos soft-diversity alap
  elkészült; a Phase 4–5 pure-domain regresszió és consistency kapu zöld, a teljes
  Java 21 Gradle + Folia staging identity/migration/death/market runtime acceptance
  továbbra is kötelező release-gate.
- ✅ Az első, szándékosan kicsi survival vertical slice 6 authored template-re épül:
  vanilla mining → Sarkfény-cseppkő → profession craft → reroll/rúna/piac →
  világboss-komponens → ugyanazon UUID-val ascension. A 30–40 darabos katalógusbővítés
  csak a pilot runtime/balance elfogadása után következhet.
- ◇ Rúna 2.0: a canonical instance 0–2 socketes insert/change állapota és lifecycle-
  API-ja kész; a külön remove/replace UX és kódex még nyitott.
- ⬜ Crafting order piactér escrow-val és naplózott settlementtel.

**Kapunyitás E felé:** a tárgyazonosság másolás, újraindítás és
inventoryhiba után is bizonyítható; nincs új pénzforrás.

### E — Living World Director

- ⬜ Egységes `EventOutcome` minden esemény strukturált eredményéhez.
- ⬜ Fair, éhezéses súlyozású `Event Director` a meglévő
  `MajorEventGate` fölött.
- ⬜ Legfeljebb háromlépcsős eseményláncok, ciklus nélkül.
- ⬜ Időzített, visszafordítható kudarc-következmény és legfeljebb egy
  aftershock eseményenként.

**Kapunyitás F/G felé:** minden új eseménytípus használja a spawnvédelmi
mátrixot, bounded, restartbiztos és pénzsemleges.

### F — Whisper War

- ⬜ Titkos küldetések.
- ⬜ Suttogó-befolyás hálózat.
- ⬜ Kontraspionázs.
- ⬜ Anti-leak szerződés minden kapcsolódó PAPI-, Krónika-,
  achievement-, inbox- és adminnézetre.

**Kilépési feltétel:** a szerepjátékos titok nem jelenik meg jogosulatlan
felhasználó tab-complete-jében, toastjában, placeholderében vagy
visszatérési összefoglalójában.

### G — LiveOps és balansz

- ⬜ Feature flagek determinisztikus UUID-hash rollouttal; gazdasági
  tranzakcióra és itemformátumra nincs százalékos rollout.
- ⬜ Lejáró live-ops presetek dry-run diffel, rollbackkel és auditloggal.
- ⬜ Bounded balansztelemetria és heti riport, 60–90 napos megőrzéssel;
  a rendszer csak jelez, nem balanszol automatikusan.
- ⬜ Moderációs workflow 2.0 a meglévő reportmodell migrálásával, új
  párhuzamos case-rendszer nélkül.

### H — IceSMP Client Platform (opcionális Fabric kliensmod)

A szerveroldali protokoll-alap (Client Bridge: transport, kézfogás,
session-registry, rate limit, `/icesmp client` diagnosztika) elkészült —
lásd `docs/ARCHITECTURE.md` „Client Bridge” szekció. A folytatás
fázisonként, a terv szerinti sorrendben:

- ✅ Külön Fabric repo (`MilCsik09/IceSMP-Fabric`): client-only skeleton
  exact 1.21.11-re, bájtazonos protokoll-port golden-vector suite-tal,
  kézfogás-állapotgép szimulált szerveres flow-regresszióval, kliens-config
  és kézfogás-státusz debug overlay, inert other-server mód.
- ⬜ Phase 0 transport spike ÉLŐ bizonyítása: valódi Paper↔Fabric HELLO/ACK
  roundtrip exact 1.21.11-en, reconnect + proxy-hatás (CLIENT-02
  acceptance-sor). A sandbox-oldali fele (codec + kézfogás-kör szimulált
  szerverrel) a Fabric-repo suite-jaiban kész; az élő út staging-teszt.
- ✅ Native HUD szerveroldal: `HudStatePayload` (0x20) sorosítás a meglévő
  `HudSnapshot`/`ClassHudState` projekcióból, change-driven push +
  resync-teljes-state, vanilla suppression (sidebar/first-party/compact) a
  `ClientHudRoute` seamen át — lásd „Native HUD routing” az
  ARCHITECTURE-ben. ⬜ Fabric-oldali natív HUD-renderer; a
  `client.features.native-hud` kapu éles nyitása csak a kliens-release-szel.
- ✅ Ability bar + `CAST_SLOT` szerveroldal: publikus slot-cast belépő a
  canonical cast-magon (`castActiveKitSlot` — vanilla parity kapukkal:
  katalizátor a főkézben, profil-készenlét, közös debounce),
  `ABILITY_KIT_STATE` change-signature push, gépi `ACTION_RESULT`,
  CAST-rate-limit — lásd „Ability bar és CAST_SLOT” az ARCHITECTURE-ben.
  A `keybind-cast`/`ability-bar` kapuk élés nyitása a kliens-release-szel.
- ✅ Natív Spellbook: SPELLBOOK_STATE projekció (a vanilla GUI-val azonos
  katalógus, olcsó változás-jellel), SELECT_SPELL/TOGGLE_FAVORITE actionök
  a meglévő validált use-case-eken, UI-rate-limit — mindkét oldalon.
- ✅ Natív Profile/Character screen: PROFILE_STATE a /profile GUI-val
  azonos tartalommal (ClientProfileProjector, PlayerProfile
  authority-szabály szerint internals nélkül) — mindkét oldalon.
- ✅ Relic-state v1: saját-játékos RELIC_STATE projekció (ClassRelicActivation
  tükre, RELIC_RENDER_V1 kapu) + kliensoldali relic-sor a natív HUD-ban.
- ✅ Relic attachment-broadcast infra: RELIC_ATTACHMENT_STATE (közeli aktív
  viselők, Folia-safe PositionCache + lock-mentes resolve úton,
  RELIC_ATTACHMENT_V1 kapu) + awakening-readyAt query
  (ClassRelicService.awakeningReadyAt, a RELIC_STATE
  awakeningRemainingMillis mezője, normalizált change-signature dedupe).
  ⬜ A tényleges attachment-renderer/FX a Phase 8 dolga, a
  resonance/awakening tartalmi élesítésével együtt.
- ✅ Natív talentek: TALENT_STATE (isAvailable-szűrt, a 64-es
  protokoll-limitet és a más-kaszt-privacy-t egyszerre tartva) +
  PURCHASE_TALENT a CAS-védett spendPoint use-case-en — mindkét oldalon.
  Respec-action szándékosan nem része a protokollnak (SpecGUI-döntés).
- ✅ Natív Quest Journal: QUEST_STATE (öt fül, isVisible-szűrve — HIDDEN
  sosem szivárog, riddle „???”-ként utazik, fülönkénti cap + total) +
  TRACK_QUEST mint egyetlen engedett quest-mutáció; accept/turn-in
  kliens-actionként tiltva (forrás-authority) — mindkét oldalon.
- ✅ Natív Professions: PROFESSION_STATE (nyolc-szakmás roster szinttel,
  XP-bontással, recept-/tervrajz-számokkal és heti céh-céllal; spec-opció
  csak aktív szakmákra) + SELECT_PROFESSION (CAS-mutáció dönt, foglalt slot
  REJECTED) és SELECT_PROFESSION_SPEC (canSelect-kapus use-case; respec
  szándékosan nem protokoll-action) — mindkét oldalon. Recept-katalógus
  tétel-szinten nem utazik: a recept-böngésző a product spec külön modulja.
- ✅ Natív recept-böngésző (product spec Modul 10): BROWSE_RECIPES →
  requestId-korrelált RECIPE_PAGE pull-modellben (a 437 elemes katalógus
  nem fér a push-limitbe), a vanilla recept-könyv csempe-logikájával bitre
  azonos tartalommal; lap-méret élő configból
  (`client.limits.recipe-page-size`). Craft-action szándékosan nincs a
  protokollban — a craft a vanilla recept-könyv tranzakciós útján marad.
- ✅ Party frame (Phase 7 első modul): strukturált PARTY_STATE a vanilla
  HUD party-soraival azonos adatforrásból (fél-szív kvantálás, régió-átmenet
  fallback), read-only — a party-mutáció a /party parancson marad; a natív
  kliens a frame aktív állapotában nem duplázza a HUD-panel party-sorait.
- ✅ Boss/encounter frame: BOSS_STATE a vanilla világboss-bar adatkörével
  + név/archetípus/dühöngés (WorldBossManager lock-mentes display-tükrei),
  HP egész százalékra kvantálva; a natív frame-et kapó játékosnál a
  vanilla bar elhallgat (ClientHudRoute.bossFrameActive suppression).
  Kazamata mini-bossnak nincs vanilla felülete — display-paritás okán a
  frame-ben sem szerepel; encounter-scope/contribution külön rendszer
  híján nincs.
- ✅ Territory overlay: TERRITORY_STATE — az aktuális zóna (név/típus/
  tulajdonos, a vanilla actionbar + /territory info adatköre) tartós
  overlay-ként, az aktuális zónán futó raid állásával; a zóna-lookup a
  lock-mentes chunk-indexen fut a néző szálán. Zóna-geometria szándékosan
  nem utazik (térkép-overlay külön fázis lenne).
- ✅ Faction screen (Phase 7 zárás): FACTION_STATE — tagság, kincstár +
  adókulcs, király + tally, szezon-állás (vendégnek is, publikus adat),
  élő raid-státusz, hadi-ablak; PlayerProfile-internals nélkül. Join/leave
  szándékosan nem protokoll-action (a csatlakozás Menedék-főváros
  forrás-kötött — hely-authority bypass lenne).
- ✅ Phase 8a — attachment-renderer + awakening-FX v1 (tisztán
  kliensoldali, protokoll-változás nélkül): világtérbeli relikvia-jelvény
  a viselők fölött a RELIC_ATTACHMENT_STATE-ből, rezonancia-lüktetéssel;
  a HUD Awakening-kész sora lüktet.
- ✅ Phase 8b — FX-esemény csatorna: a presentation-sáv első üzenete
  (FX_EVENT, tranziens fire-and-forget, ADVANCED_FX_V1 kapu) a
  ClientFxRoute domain-seamen át; v1-emitterek: világboss SLAM/ZONE/SUMMON
  telegráf + awakening-arming; kézbesítés PositionCache-rádiusszal, a
  vanilla telegráf minden kliensnek változatlan (az FX kiegészítő réteg).
- ✅ Teljes review-kör (tulaj-kérésre, 5 szempont-audit): a megerősített
  leletek javítva — világboss-tükör publikálási sorrend, kézfogás-kori
  cache-race, dedupe-cache csak sikeres küldés után, resync-END
  try/finally, HUD-tick védőháló a kliensréteg hibái ellen,
  BROWSE_RECIPES saját-szakma kapu (vanilla paritás), PositionCache/emitFx
  unloaded-world védelem; kliensen resync-ürítés teljessé téve,
  recept-fülek saját szakmára szűrve.
- ✅ World-event spawn hardening: automatikus jelöltek chunk-középre
  igazítva, effektív footprint/partpuffer egy régión belüli 7 blokkra
  korlátozva, escort-route és inváziós hullám belső profillal; az admin
  parancsok az aszinkron keresést nem jelentik többé kész spawnként.
- ✅ Teljes class-mechanika audit: mind a 13 kaszt, 35 specializáció, 210
  doctrine és 35 capstone producer→consumer útja bekötve; minden alap aktív
  kit 7/7 feloldható spell. A csúcspróbák spec- és szintkapus
  `CAST_SPELLS` questek, a durable pet/minion roster egyetlen példány-authorityt
  használ, a Szentségtelen ghúl mutációja pedig tényleges Profile v2 társállapot.
- ⬜ Review-ből nyitva hagyott kis tételek: (1) a protokollnak nincs
  aggregát (beágyazott listás) payload-méret garanciája — a jelenlegi
  tartalom-skálán elméleti, a hibaút a HUD-tick védőhálóval lefedve; ha a
  katalógus-tartalom nagyságrendet nő, encode-oldali aggregát-cap kell.
  (2) A Fabric golden-vector suite csak a foundation-payloadokat fedi
  hexával — az újabb payloadok szerződését a bájtazonos port + a
  flow-suite roundtripjei őrzik; hex-vektor bővítés opcionális erősítés.
  (3) PLAYER_ANIMATION_V1: fenntartott, nem implementált capability
  (Phase 8 folytatás) — a kliens nem hirdeti, kapu zárva.
- ⬜ A H fázis lezárása: élő staging-teszt (CLIENT-02..21) — minden
  további bővítés (új FX-emitterek, spell-animációk, Phase 9
  admin-eszközök) ez után ütemezendő.

**Kilépési feltétel fázisonként:** vanilla kliens viselkedése változatlan,
nincs dupla presentation, a kliens semmiben nem authority, és a feature
egyetlen `client.features.*` kapcsolóval visszakapcsolható.

## 5. Közös alapok és függőségek

| Alap | Első fázis | További használók |
|---|---:|---|
| `AdminAuditLog` | A | G |
| `PlayerInboxService` | B | C, F, G |
| `NotificationRouter` | B | F, G és bugreport-visszajelzés |
| Contribution/telemetry réteg | C | D és G |
| `ItemIdentityService` | D | salvage, history és crafting order |
| `EventOutcome` | E | E és G |
| Feature flag/preset alap | G | későbbi rolloutok |

## 6. Ötletbank — még nincs ütemezve

Az alábbiak értékes irányok, de nem részei az A–H vállalásnak. Csak
külön scope-, exploit-, Folia- és gazdasági review után kerülhetnek fel
elkötelezett fázisba.

### Progresszió és történet

💡 Kaszt-story questlánc; frakció-fejlesztési fa; relikvia által adott
territóriumbuff; elfoglalható erőforráspontok; fiókszintű
meta-progresszió; tárgyszettek; presztízs és reforge.

### PvE és világesemények

💡 Világboss add/interrupt mechanika; esemény-auto-party; heti
kihívásrotáció; vándorló vagy mythic boss; szörnyfészek; kooperatív
boss-finisher; bestiárium tanulmány-bónusz (III. tudás-fokozat után kis,
config-kapcsolós bónusz a tanulmányozott faj ellen — pl. +2–3% sebzés
és/vagy lélekkő-esély szorzó; balansz-review és a passzív-precedencia
láncban rögzített hely szükséges hozzá).

### PvP és frakcióháború

💡 Raid zászlólopással; kasszafeltörési fázisok; 3v3 határvidéki
skirmish; háborús időablak; anti-snowball fékek; további raidvariánsok.

### Gazdaság és szakmák

💡 Claimhez kötött chest shop; buy order; kasszából fedezett
raidbiztosítás; heti királyi megbízások; zálogház; mestermű-esély;
szakmák közti receptlánc; napi szakmamegrendelés; műhely és mestermunka
quest.

### Quest, kaszt és játékos-UX

💡 Sürgős, party- és escortquest; tartós döntési flagek; questanalitika;
erősebb kasztidentitás-mechanikák; okos gyógyítás; quest HUD;
spell-loadout; menübadge.

### Közösség és világ

💡 Védett, lebomló sír; útkőhálózat; kocsmai buffok; játékosszobrok;
közösségi építések; szezonokon átívelő időkapszula.

### Megfigyelhetőség és integráció

💡 Spellhasználati statisztika; faucet/sink riport; edzőbábu;
Discord-webhook; YAML-integritásőr.

### Modern API és resource pack

💡 `AttackRange`, `KineticWeapon`, `UseEffects` és `Recipes`
adatkomponensek; szerveroldali resource-pack push; egyedi fontok;
`TileState`; Structure API; csak a forrásban még valóban nem használt
Paper/Folia event hookok.

### Opcionális függőségcsökkentés

💡 `economist`/`service-io`, FancyHolograms, AuMenus és
VillagerTradeEdit kiváltásának vizsgálata; a WorldGuard csak teljes
élő policy-leltár és külön migrációs terv után kerülhet szóba.

## 7. Definition of Done

Egy roadmap-tétel csak akkor zárható le, ha:

1. a forrás, a config és a felhasználói viselkedés ugyanazt mondja;
2. a Folia ownership minden érintett entitásnál igazolt;
3. restart-, kick-, quit-, reload- és lemezhibaútja rendezett;
4. nincs új faucet vagy jutalomduplikáció;
5. az új parancs, alias, permission, GUI és configút bekerült a gépi
   inventoryba és a megfelelő kézikönyvbe;
6. a builderfüggőséghez pontos azonosító és átvételi bizonyíték tartozik;
7. a build, consistency, inventory és Markdown-linkellenőrzés zöld;
8. a szükséges staging/runtime pontot nem CI alapján, hanem ténylegesen
   kipipálták az admin acceptance checklistben.

## 8. Season 0 / Prologue — PR #121 kiadási állapot

A `feat/prologue-doom-gate` branch a külön Prologue lifecycle-t, a Season 0
content/progression gate-eket, Olethropyla egyetlen legitim Nether-átjáró
policyjét, a Gate Breach/finale útvonalat, Profile v2 prestige státuszokat és a
Season 1 átmenetet tartalmazza. A completion pass a finale pause, transient
entity cleanup és boss-victory persistence race hardeningjét is lezárta.

- ✅ **Forrásoldali completion:** Folia-safe transient cleanup, valódi encounter
  pause, pause-időt kizáró timeout, paused restart recovery, finaleId-kötött
  boss-victory pending receipt, fail-closed persistence failure és idempotens
  Gate/reward/Season 1 settlement elkészült.
- ✅ **DORMANT pass-through:** élesítés előtt nincs Prologue content/progression
  ceiling, season/community override, Nether authority, HUD/ambient/breach vagy
  idő előtti catch-up; a normál szerverconfig marad érvényben.
- ✅ **Dokumentációs szinkron:** lore mapping, player-facing Prologue policy,
  admin live-ops és builder hookok a meglévő kanonikus guide-okban szerepelnek.
- ◇ **World-builder acceptance:** a `prologue-gate`, `prologue-gathering`,
  `prologue-breach`, `prologue-boss` hookok tényleges élő térképes pontjai,
  arena/perem és Nether-oldali érkezés továbbra is kézi world-build feladat.
- ◇ **Staging runtime acceptance:** rehearsal, production pause/resume,
  pause→restart→resume és a victory crash-windowk productionközeli Folia
  szerveren még kézi próbát igényelnek; az automatizált regresszió nem helyettesíti ezt.
- 🚧 **Build/CI gate:** csak az exact PR HEAD-en futott Java 21 `check`,
  consistency/docs inventory és CI bizonyíték után tekinthető a PR kiadásra
  késznek. Ha a GitHub runner billing/spending-limit miatt el sem indul, az
  platform-blocker, nem zöld validáció.

A Prologue scope-on kívül marad a Season 2 End-nyitás, az Első Csend
magyarázata és a Néma Királynő végjátéka; ezek nem #121 hiányosságok.
