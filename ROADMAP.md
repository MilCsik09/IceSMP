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
- ⏸ **döntésre vár** — tulajdonosi vagy design-döntés nélkül nem indul.

## 1. Következő kiadási kapuk

### 1.1. Kiadásblokkoló

- 🚧 **H-ECON-001 — több tartományt érintő gazdasági crash-ablak.**
  A bank- és claimfolyamatok egy része memóriát, inventoryt és
  több külön állományt módosít, de ezekhez nincs közös, tartós commitpont.
  A megoldási irány szűk WAL/pending rekord: az irreverzibilis lépés előtt
  tartós műveleti rekord, majd idempotens induláskori recovery. Teljes
  wallet- vagy claim-snapshotot nem szabad régiószálon szinkron írni.

**Kilépési feltétel:** a normál út, lemezhiba és több időablakban
megszakított folyamat is bizonyítottan ugyanarra az eredményre áll helyre;
nincs dupla kifizetés, elveszett tárgy vagy kifizetett, de létre nem jött
claim.

### 1.2. Megerősített technikai adósság

Ezek nem mind kiadásblokkolók, de a forrásban még létező rések. Az
implementálásuk előtt tételenként újra kell igazolni a kiváltási utat.

- 🚧 A `claims.yml` hibás szemantikai rekordját a loader jelenleg
  kihagyhatja, egy későbbi mentés pedig véglegesítheti az adatvesztést.
  Fail-closed betöltés, karantén és látható mentési hiba szükséges.
- ⬜ A HUD és a parkour quit-takarítása mellé kick-út kell; a hosszú életű
  report-, cooldown- és debounce mapekhez explicit purge-szabály kell.
- ⬜ A legacy `claims.block-in-*` beállításokat egyértelmű, validált sémára
  kell migrálni.
- ⬜ A GUI-kban maradt közvetlen szövegek kerüljenek a
  `MessageManager`-be.
- ⬜ A `/icesmp reload` csak valóban sikeres validálás után küldjön
  sikerüzenetet.
- ⬜ A `ProtectionBridge` konkrét policy/flag alapján döntsön; ne kezeljen
  automatikusan minden WorldGuard-régiót tiltott területként.
- ⬜ A kereskedő-karaván minden spawnútja menjen át az
  `EventSpawnGuard`-on, és a régiószálra hopolt callback ismét ellenőrizze,
  hogy az esemény még ugyanahhoz a generációhoz tartozik.
- ⬜ A `/menu` adjon utat a `/tanacs`, `/komp` és `/faction war`
  funkciókhoz; staff-elemet csak megfelelő jogosultsággal mutasson.
- ⬜ Tanácsszavazásnál játékidő-alapú alt-védelem, az ambient jutalmaknál
  napi keret, a parkournál tartós ranglista szükséges.
- ⬜ A szezon 41–53. napjának történeti üresjáratát és a túl korán
  elérhető rejtvényeket tartalom- és időkapu-tervvel kell rendezni.
- ⏸ A Mételytépő és a Sárkánytojás-töredék tényleges megszerzési forrása
  tulajdonosi döntést igényel.

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

- ⬜ Elit-affix réteg: kevés, jól olvasható affix, legfeljebb kettő
  mobonként, spawnkor rögzített döntéssel és bounded élettartammal.
- ⬜ Eseményvezérelt boss contribution ledger sebzés, gyógyítás, tankolás
  és mechanikai részvétel alapján.
- ⬜ Személyes harci összefoglaló; nyilvános DPS-szégyenfal nélkül.
- ⬜ Bestiárium 2.0 többszintű kutatással és információs/kozmetikai
  jutalmakkal.

**Kapunyitás D/E felé:** minden idézett entitás életciklusa rendezett,
a jutalom pénzsemleges, az offline jogosultság az inboxba kerül.

### D — Profession és item economy

- ⬜ A 16 profession-specializáció tényleges passzívjai és fizetős respec.
- ⬜ Veszteséges salvage szigorú tiltólistával.
- ⬜ `ItemIdentityService` és bounded item history, csak fontos
  mérföldkövekkel.
- ⬜ Rúna 2.0 UX: előnézet, kódex és rúnapor-salvage; több foglalat nélkül.
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
- ⬜ A H fázis hátralévő nagyjai: élő staging-teszt (CLIENT-02..20),
  Phase 8b — FX-esemény csatorna (presentation-sáv, ADVANCED_FX_V1):
  szerver-emitterek (világboss-telegráf, awakening-arming) + kliens FX.

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


