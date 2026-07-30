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
- ⬜ **elkötelezett fejlesztés** — része az A–G tervnek;
- ◇ **builder- vagy runtime-kapu** — kézi előkészítést, illetve próbát igényel;
- 💡 **ötlet** — értékes irány, de még nincs ütemezve;
- ⏸ **döntésre vár** — tulajdonosi vagy design-döntés nélkül nem indul.

## 1. Következő kiadási kapuk

### 1.1. Kiadásblokkoló

- 🚧 **H-ECON-001 — több tartományt érintő gazdasági crash-ablak.**
  A bank-, claim- és adományfolyamatok egy része memóriát, inventoryt és
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
- ◇ A hibrid spellköltségeket és a frakciópasszívok számait élő
  playtestből kell hangolni.
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

Az alábbiak értékes irányok, de nem részei az A–G vállalásnak. Csak
külön scope-, exploit-, Folia- és gazdasági review után kerülhetnek fel
elkötelezett fázisba.

### Progresszió és történet

💡 Kaszt-story questlánc; frakció-fejlesztési fa; relikvia által adott
territóriumbuff; elfoglalható erőforráspontok; fiókszintű
meta-progresszió; tárgyszettek; presztízs és reforge.

### PvE és világesemények

💡 Világboss add/interrupt mechanika; esemény-auto-party; heti
kihívásrotáció; vándorló vagy mythic boss; szörnyfészek; kooperatív
boss-finisher.

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
