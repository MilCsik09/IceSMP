# IceSMP quest- és NPC-builder kézikönyv

<!-- icesmp-doc-id: guide.quest-and-npc-builder -->

> Ez a dokumentum a világépítőnek és a tartalomadminnak szól. Megmutatja, melyik
> NPC belső néven kell létrejöjjön, melyik quest miért keresi, milyen történeti
> szerepet hordoz, és milyen world-hook nélkül marad teljesíthetetlen. Nem ad
> koordinátát: a repository nem tartalmazza az élő világ autoritatív pontjait.

<details>
<summary>Dokumentum-forrásállapot és lefedettség</summary>

- Forráság: `master`.
- Dokumentált commit: `73508dfa1bb40e6be54ab215bbe02dd0ae003e54`.
- Questforrás: `src/main/resources/config/quests.yml`.
- Runtimeforrás: `QuestManager`, `FancyNpcsQuestBridge`, `QuestCommand`, `NpcBindCommand` és `NpcBindingManager`.
- Loreforrás: `docs/LORE.md` és `docs/LORE_REFERENCE.md`; a tervezetet az owner lore-baseline-jával is összeolvastuk.
- Bundled questek: **160/160**.
- Quest-NPC belső ID-k: **18/18**.
- Játékos-látható `dialogue` blokkot tartalmazó questek: **45/45**.
- Territory-hookok: **4**; parkour-hookok: **1**; biome-hookok: **5**.

</details>

## 1. Mi biztos, és mi builder-ajánlás?

- **Forrásból biztos:** minden backtickes quest-, NPC-, territory-, pálya- és biom-ID;
  a player-facing név, leírás, dialógus, cél, feltétel, lánc és jutalom a fenti
  commit `quests.yml` fájljából származik.
- **Kánonból biztos:** Aetrinita, Radicora, Caldestera, Ryanora, Pyralingrad,
  Glatziendorf, Thanaopolis, a Kárhozat Kapuja, a Bokic, a Vasművek, a
  Káoszkor és a kasztforrások szerepe a lore-dokumentumokból származik.
- **Builder-ajánlás:** az NPC-k javasolt városrésze, díszlete, látóvonala és
  útvonala kreatív, de a forráskorlátokhoz igazított javaslat. Ezek nem runtime
  koordináták és nem bizonyítják, hogy az élő világban a hely már létezik.
- **Külön figyelmeztetés:** a kasztmesterek neve és dialógusa runtime-tartalom,
  de a `LORE_REFERENCE.md` szerint a „mesterek” intézménye jelenleg nem kánon.
  Építsd őket játékmechanikai oktatóként; ne állíts róluk új történelmi tényt,
  amíg az owner nem kanonizálja őket.

## 2. Release-blokkoló world inventory

| Hook | Forrásból biztos szükséglet | Builder teendő |
|---|---|---|
| Quest-NPC | 18 pontos FancyNpcs belső ID | Létrehozás, megközelíthetőség, marker- és kattintásteszt |
| Territory | 4 pontos ID | Zóna létrehozása, `show`-bejárás, normál játékos próba |
| Parkour | 1 pontos pálya-ID | Start/cél/sugár beállítása és quest-finish próba |
| Biom | 5 elérendő biom | Bizonyított útvonal; a questhez ne kelljen tiltott világkapu |
| Élő NPC-binding | A repositoryban nincs kész world/NPC állapot | `/npcbind list` leltár és staging bizonyíték |
| Koordináták | A forrás nem ad élő koordinátát | Az átadólapon rögzítsd; ebben a fájlban ne találj ki pontot |

### A questek által igényelt territoryk

| Runtime ID | Kapcsolódó quest | Forrásból biztos jelentés | Builder-ajánlás / blokk |
|---|---|---|---|
| `radicora` | `gyokerek_3` | Radicora, a Fa tövénél fekvő „gyökerek városa”; kezdőterület | A teljes települést vagy a Világfa nyilvános magját fedje; ne egy rejtett pont legyen |
| `karhozat-kapuja` | `karhozat_zarandoklat` | A Kárhozat Kapuja játékos-neve; a lore ősi neve Olethropyla | `DOOM_GATE` zóna, olvasható bejárattal és visszafordulási ponttal |
| `erdei-szentely` | `forest_cleansing` | A Sylvana-lánc háromlépéses céljának záróhelye | Az `erdei_venek` közelében, de önállóan bejárható szentély; a határátlépés legyen egyértelmű |
| `dark-capital` | `necromancer_initiation` | **A quest jelenlegi runtime ID-je** | **Névütközés:** a kánon térképezése a DARK fővárost `thanaopolis` ID-ként írja. Kiadás előtt config- vagy territory-migrációval legyen egyetlen egységes ID; ne találj ki két „fővárost”. |

### Parkour és biome hookok

- Pálya: `kezdo_parkour`; a `kezdo_parkour` pályát a `acrobat_challenge` kéri.
- Biomok:
  - `cherry_grove` → `rejtveny_rozsaszin_ho`; legyen normál túlélő útvonal és tényleges biom-regisztráció.
  - `windswept_hills` → `fejezet2_repedesek`; legyen normál túlélő útvonal és tényleges biom-regisztráció.
  - `desert` → `wanderlust_desert`; legyen normál túlélő útvonal és tényleges biom-regisztráció.
  - `jungle` → `wanderlust_jungle`; legyen normál túlélő útvonal és tényleges biom-regisztráció.
  - `snowy_plains` → `rejtveny_feher_csend`; legyen normál túlélő útvonal és tényleges biom-regisztráció.

## 3. NPC-k létrehozása és kötési szabályok

A quest a FancyNpcs **belső nevét** hasonlítja, nem a skin nevét vagy a
megjelenített dialógusnevet. A biztos létrehozási minta:

```text
/npc create <belső-id>
/npcbind list
```

A 18 ID-t pontosan kisbetűvel és aláhúzással használd. Átnevezés előtt
ellenőrizd a questkonfigot, a FancyNpcs állapotot és az `npc-bindings.yml`
leltárát.

Egy NPC-kattintás runtime sorrendje forrásból biztos (Quest Framework v2 —
MMO-prioritás, a döntést a központi forrás-authority hozza):

1. minden KÉSZ (minden célját teljesített) quest, amelynek a leadási pontja ez
   az NPC, leadásra kerül — jutalom + záró dialógus itt jár;
2. minden aktív `TALK_TO_NPC` cél teljesül, illetve a megfelelő
   `DELIVER_ITEMS` cél megpróbálja átvenni a tárgyakat; ha ettől a quest
   minden célja kész ÉS az NPC a jogos leadási pont, ugyanez a kattintás le
   is adja;
3. ha az NPC-hez tartozó (`start: NPC` forrású) felvehető questből pontosan
   egy van, azt azonnal megkapod; többől kattintható, egyszer használatos
   tokenes listából választasz — a lista sorrendje: story > kaszt > mellék >
   napi/heti > titok;
4. a `/npcbind` binding NEM felvételi jogosultság, csak UI-mutató: `QUEST`
   binding esetén is a quest saját `start.npc` mezője dönt; a `SHOP`, `BANK`,
   `EXCHANGE`, `FACTION`, `COMMAND` bindingek változatlanul működnek;
5. a `COMMAND` a kattintó játékos saját permissionjével fut;
6. a `/quest talk <npc-név>` NPC-szimuláció ADMIN-parancs (`icesmp.admin.quest`)
   — teszteléshez és híd-kiesés áthidalására; a játékos-út a tényleges
   NPC-kattintás.

A személyes questmarker alapból 48 blokkon belül, 40 tickenként frissül, a
színe a központi palettából jön: arany = leadható questje van a játékosnak;
sárga = új quest vehető fel; kék = napi/heti kínálat; lila =
kaszt/specializáció/relikvia-tartalom; türkiz = titok; szürke = folyamatban
lévő beszélgetés/szállítás célpontja. A marker nem helyettesíti a táblázást és
a világos útvonalat.

### Forrás-séma (v2) builder-szemmel

A `quests.yml` minden questje deklarálhat forrást és leadást (a teljes
mező-referencia a fájl fejlécében él):

- `start: { type: NPC, npc: "<pontos NPC-név>" }` — az NPC adja kattintásra;
  a leadás defaultja ugyanaz az NPC.
- `start: { type: CHAIN }` (+ `auto-accept: true` az azonnali folytatáshoz) —
  lánc-feloldás (`next` vagy dialógus-választás) nyitja.
- `start` nélkül — Megbízások-tábla (küldetésnapló), automatikus lezárással.
- `category:` a napló-rendezés és a marker-szín; `visibility:` a lista-kori
  láthatóság (`HIDDEN` = felfedezésig semmilyen player-felületen nem látszik).
- A gráf-validátor minden reloadnál a TELJES katalógust ellenőrzi (ismeretlen
  `next`/`requires-quest`, lánc-ciklus, üres quest, hibás forrás-mező) —
  hibás fájl a korábbi definíciókat hagyja élőben.

### Mit jelent a „leadás” a világban?

- **NPC-forrású questnél (`start: NPC`) a leadás fizikai:** a feladatok
  teljesítése után a quest KÉSZ állapotba lép, és a jogos leadási pontnál
  (alapból ugyanannál az NPC-nél) zárul le — a jutalom és a
  `dialogue.complete` OTT szólal meg. A játékos a „térj vissza" útmutatást
  chatben és a napló Kész fülén is látja.
- **Megbízás-forrású questnél** (tábla, `start` nélkül) a quest a számláló
  elérésekor azonnal teljesül; a `dialogue.complete` ott szólal meg, ahol a
  teljesítés történt.
- Explicit `turn-in:` szekcióval a leadás helye NPC-től eltérő is lehet
  (territórium vagy esemény).
- Csak a `TALK_TO_NPC` és `DELIVER_ITEMS` kényszeríti a játékost a megnevezett
  NPC-hez; csak a `VISIT_TERRITORY` és `PARKOUR_TRIAL` kényszerít konkrét
  world-hookhoz.
- Az olyan dialógus, mint „erőpróba-pálya” vagy „a szentély asztalánál”,
  hangulati szöveg, ha az objektíva valójában `KILL_MOBS` vagy `ENCHANT_ITEMS`.
  Díszletet építhetsz hozzá, de ne állítsd, hogy a runtime ott ellenőrzi.

### Forrásból következő ellenőrzési kockázatok

- `merchant_choice`: a kattintható választások a `give` fázisban jelennek meg,
  miközben mindkét célquest a `merchant_choice` teljesítését követeli. Ellenőrizd
  stagingben, hogy a játékos a Benedekkel folytatott következő interakció után
  mindkét ágat fel tudja venni; az azonnal kattintott opció jelenleg blokkolódhat.
- A `warrior_master_trial` és `archer_master_trial` dialógusa pályát említ, de
  objektívájuk mobölés; a bundled configban csak az `acrobat_challenge` használ
  valódi `PARKOUR_TRIAL` célt.
- `dark-capital` runtime-hivatkozás és `thanaopolis` kánon-ID eltér; ezt nem
  world build közben, ad hoc duplikált zónával kell elfedni.

## 4. A 18 kötelező quest-NPC

Az alábbi kapcsolatok mind forrásból biztosak; a helyszín és a díszlet
builder-ajánlás. A „cél” lista `TALK_TO_NPC` és `DELIVER_ITEMS` hivatkozást is
tartalmaz.

| Belső ID | Szerep | Questadó (`start: NPC`) | Cél-NPC |
|---|---|---|---|
| `hirnok` | A Hírnök — krónikás, onboarding- és frakcióválasztó kontakt | `hirnok_hirvitel` | `rejtveny_elso_nyom`, `fejezet1_kronikas`, `fejezet2_pecset`, `fejezet3_harmadik_mondat`, `kaszt_orokseg`, `hirnok_hirvitel`, `onboarding_herald`, `onboarding_utmutatas` |
| `vandor_kereskedo` | Benedek kereskedő — bajba jutott karavános és heti beszállító | `merchant_choice` | `rejtveny_zsakos_vandor`, `merchant_distress`, `merchant_choice`, `merchant_trade_help`, `neutral_heti_vasarjaras`, `beszallito_fa`, `beszallito_ko`, `beszallito_elelem`, `beszallito_bor` |
| `erdei_venek` | Sylvana vénasszony / az erdei vének — Aetrinita ligetének gyógyítói | `forest_cleansing`, `venek_gyogyfu_szuret`, `venek_mez_aldozat` | `rejtveny_gyokerek_bolcse`, `fa_uzenete_3`, `forest_elder_call`, `venek_mez_aldozat` |
| `harcos_mester` | Aldric mester — harcosoktató | `warrior_master_trial` | `warrior_mentor` |
| `ijasz_mester` | Lysa mesterasszony — íjászoktató | `archer_master_trial` | `archer_mentor` |
| `varazslo_mester` | Orvus főmágus — varázslómester | `wizard_master_trial` | `wizard_mentor` |
| `orgyilkos_mester` | A Névtelen — orgyilkosmester | `assassin_master_trial` | `assassin_mentor` |
| `druida_mester` | Ylvara, a Vén Tölgy — druidamester | `druid_master_trial` | `druid_mentor` |
| `paplovag_mester` | Seratiel lovag-parancsnok — paplovagmester | `paladin_master_trial` | `paladin_mentor` |
| `halallovag_mester` | Morvran, a Fagyott Penge — halállovagmester | `death_knight_master_trial` | `death_knight_mentor` |
| `saman_mester` | Tharkun, a Viharlátó — sámánmester | `shaman_master_trial` | `shaman_mentor` |
| `szerzetes_mester` | Csendes Jin apát — szerzetesmester | `monk_master_trial` | `monk_mentor` |
| `pap_mester` | Elenora főtisztelendő — papmester | `priest_master_trial` | `priest_mentor` |
| `pakt_mester` | Az Alkuszó — boszorkánymester-tanító | `warlock_master_trial` | `warlock_mentor` |
| `demonvadasz_mester` | Karyx, a Megjelölt — démonvadászmester | `demon_hunter_master_trial` | `demon_hunter_mentor` |
| `sarkany_mester` | Vaelith, a Pikkelyes Bölcs — sárkányidéző-mester | `evoker_master_trial` | `evoker_mentor` |
| `kovacs_mester` | Gorvik kovácsmester — céhes megrendelő és napi questadó | `kovacs_acel_rendeles`, `kovacs_szenszallitmany`, `kovacs_fegyvermustra` | `blacksmith_delivery`, `kovacs_szenszallitmany` |
| `revesz` | A révész — a Radicora–Caldestera vízi út mesélője | `revesz_vacsoraja`, `revesz_moloja` | `revesz_ismerkedes`, `revesz_moloja` |

### Részletes elhelyezési lapok

#### 4.1. `hirnok` — A Hírnök — krónikás, onboarding- és frakcióválasztó kontakt

- **Forrásból biztos kapcsolatok:** questadó: `hirnok_hirvitel`; cél: `rejtveny_elso_nyom`, `fejezet1_kronikas`, `fejezet2_pecset`, `fejezet3_harmadik_mondat`, `kaszt_orokseg`, `hirnok_hirvitel`, `onboarding_herald`, `onboarding_utmutatas`.
- **Lore-kontekstus:** A kódex szerint a hírnökök viszik a nép kéréseit és a krónikák feladatait; a runtime onboarding a Menedék vendégeként küldi a játékost Caldestera fővárosi Hírnökéhez. Ez még nem `NEUTRAL` polgárság.
- **Builder-ajánlás — hely:** Új Caldestera nyilvános érkezési/főterén, a Radicorából és a komp felől érkező út csomópontján. Legyen messziről olvasható, de ne álljon közvetlenül crate, kapu vagy más kattintható blokk mellett.
- **Builder-ajánlás — hozzáférés:** Minden új játékos és minden frakció elérje harc, díj és veszélyes zóna nélkül. A Radicora → Caldestera zarándokút végpontjaként legyen kitáblázva.
- **Binding-döntés:** Ha `/npcbind hirnok faction` készül, a leadás és a TALK/DELIVER célok továbbra is haladnak, majd a frakciómenü nyílik; viszont az NPC új questet NEM kínál fel, ezért a `hirnok_hirvitel` (start: NPC `hirnok`) elérhetetlenné válik a játékosoknak. A questkínálathoz hagyd kötetlenül a hírnököt, és a frakciómenühöz használj külön szolgáltató NPC-t vagy parancsot.

#### 4.2. `vandor_kereskedo` — Benedek kereskedő — bajba jutott karavános és heti beszállító

- **Forrásból biztos kapcsolatok:** questadó: `merchant_choice`; cél: `rejtveny_zsakos_vandor`, `merchant_distress`, `merchant_choice`, `merchant_trade_help`, `neutral_heti_vasarjaras`, `beszallito_fa`, `beszallito_ko`, `beszallito_elelem`, `beszallito_bor`.
- **Lore-kontekstus:** Caldestera vándorkaravánjai ritka árut visznek a veszélyes utakon; Benedek története bosszú vagy kereskedelmi újjáépítés felé ágazik.
- **Builder-ajánlás — hely:** Állandó caldesterai karavánudvarban vagy a városkapu melletti országúti megállóban. A `merchant_distress` úton lévő kereskedőt ír le, de a sok heti beszállítás miatt a tényleges NPC ne tűnjön el az esemény-karavánnal.
- **Builder-ajánlás — hozzáférés:** Minden frakció számára stabil, egész szezonban elérhető lerakóhely; legalább néhány blokk szabad tér a tömegnek és a leadás előtti inventory-rendezésnek.
- **Binding-döntés:** A legacy, belső név szerinti questút elég; külön `QUEST` binding nem szükséges.

#### 4.3. `erdei_venek` — Sylvana vénasszony / az erdei vének — Aetrinita ligetének gyógyítói

- **Forrásból biztos kapcsolatok:** questadó: `forest_cleansing`, `venek_gyogyfu_szuret`, `venek_mez_aldozat`; cél: `rejtveny_gyokerek_bolcse`, `fa_uzenete_3`, `forest_elder_call`, `venek_mez_aldozat`.
- **Lore-kontekstus:** Aetrinita gyökerei, a gyógyuló Fa és a ligetek védelme adják a történeti horgonyt; a forrás egyszer Sylvanát, máskor az erdei véneket nevezi meg.
- **Builder-ajánlás — hely:** Az `erdei-szentely` jól járható előterében vagy közvetlenül a határán, Radicora felől jelzett gyökérösvény végén. A többfázisú tisztítás végén a játékos magát a szentély-territoryt is felkeresi.
- **Builder-ajánlás — hozzáférés:** Ne legyen faction lock, meredek parkour vagy magas mob-szint mögött. A giverhez a szentély teljesítése előtt és után is vissza lehessen térni.
- **Binding-döntés:** Hagyd a belső név szerinti giver/objective útvonalon; több questet ad config-sorrendben.

#### 4.4. `harcos_mester` — Aldric mester — harcosoktató

- **Forrásból biztos kapcsolatok:** questadó: `warrior_master_trial`; cél: `warrior_mentor`.
- **Lore-kontekstus:** A harcos Kallan harcos-szelleméből és a régi királyságok hadi örökségéből merít.
- **Builder-ajánlás — hely:** Nyilvános kasztudvar tágas gyakorlóterén, fegyverbábuk és biztonságos nézőtér mellett.
- **Builder-ajánlás — hozzáférés:** Minden frakció harc nélkül elérje; a próba vadonban teljesül, nem helyi arénában.
- **Binding-döntés:** Külön binding nélkül a mentor-beszélgetés után ugyanaz az NPC adja a mesterpróbát.

#### 4.5. `ijasz_mester` — Lysa mesterasszony — íjászoktató

- **Forrásból biztos kapcsolatok:** questadó: `archer_master_trial`; cél: `archer_mentor`.
- **Lore-kontekstus:** Az íjászok Soleil lángmadár-lovas vadászainak fegyelmét öröklik.
- **Builder-ajánlás — hely:** A kasztudvar leválasztott lőterén, biztonságos háttérfallal és világos megközelítéssel.
- **Builder-ajánlás — hozzáférés:** Ne kelljen lövészetet vagy parkourt teljesíteni az NPC eléréséhez.
- **Binding-döntés:** Külön binding nélkül működik a mentor → mesterpróba átadás.

#### 4.6. `varazslo_mester` — Orvus főmágus — varázslómester

- **Forrásból biztos kapcsolatok:** questadó: `wizard_master_trial`; cél: `wizard_mentor`.
- **Lore-kontekstus:** A varázslók Asterlayna szőtteséből és Caldestera elveszett rúnatudásából merítenek.
- **Builder-ajánlás — hely:** A caldesterai Akadémia rúnatermében vagy könyvtári csarnokában, bűvölőállomás közelében.
- **Builder-ajánlás — hozzáférés:** Nyilvános akadémiai útvonal; az NPC ne legyen zárt személyzeti ajtó mögött.
- **Binding-döntés:** Külön binding nem szükséges.

#### 4.7. `orgyilkos_mester` — A Névtelen — orgyilkosmester

- **Forrásból biztos kapcsolatok:** questadó: `assassin_master_trial`; cél: `assassin_mentor`.
- **Lore-kontekstus:** Az orgyilkosok az Első Csend homályából merítenek; a Csendet a dokumentum ne magyarázza meg.
- **Builder-ajánlás — hely:** Caldestera árnyékos, de jelzett sikátorában vagy a Botera-negyed peremén. A rejtekhely hangulatos legyen, de a questmarker 48 blokkon belül vezethesse a játékost.
- **Builder-ajánlás — hozzáférés:** Ne legyen fővárosi fegyvertilalommal vagy bűnkapuval teljesíthetetlen; minden orgyilkos elérje.
- **Binding-döntés:** Külön binding nem szükséges.

#### 4.8. `druida_mester` — Ylvara, a Vén Tölgy — druidamester

- **Forrásból biztos kapcsolatok:** questadó: `druid_master_trial`; cél: `druid_mentor`.
- **Lore-kontekstus:** A druidák a megrepedt Élet Fája gyökereinek természetmágiáját hívják.
- **Builder-ajánlás — hely:** Radicora és Aetrinita felé nyíló, védett ligetben vagy a caldesterai botanikus kertben.
- **Builder-ajánlás — hozzáférés:** Minden frakció számára szabad, állatszelídítésre nem kényszerítő megközelítés.
- **Binding-döntés:** Külön binding nem szükséges.

#### 4.9. `paplovag_mester` — Seratiel lovag-parancsnok — paplovagmester

- **Forrásból biztos kapcsolatok:** questadó: `paladin_master_trial`; cél: `paladin_mentor`.
- **Lore-kontekstus:** A paplovagok Asterlayna csillagfényét és Soleil tisztító lángját követik.
- **Builder-ajánlás — hely:** Semleges rendház udvarán vagy nyilvános őrségi gyakorlótéren, szentélykapcsolattal.
- **Builder-ajánlás — hozzáférés:** A Menedék fegyvermentessége miatt az NPC-hez ne kelljen kivont fegyverrel belépni.
- **Binding-döntés:** Külön binding nem szükséges.

#### 4.10. `halallovag_mester` — Morvran, a Fagyott Penge — halállovagmester

- **Forrásból biztos kapcsolatok:** questadó: `death_knight_master_trial`; cél: `death_knight_mentor`.
- **Lore-kontekstus:** A halállovagok Eleftheria mérgének halál-, vér-, fagy- és rúnamágiájából merítenek.
- **Builder-ajánlás — hely:** Nyilvános emlékkriptában vagy a városi temetőkert őrzött peremén, nem DARK-zónában.
- **Builder-ajánlás — hozzáférés:** Ne igényeljen száműzetést, bűnt vagy Thanaopolis elérését; a kaszt önmagában elég.
- **Binding-döntés:** Külön binding nem szükséges.

#### 4.11. `saman_mester` — Tharkun, a Viharlátó — sámánmester

- **Forrásból biztos kapcsolatok:** questadó: `shaman_master_trial`; cél: `shaman_mentor`.
- **Lore-kontekstus:** A sámánok Kallan viharmágiáját és a Fa elemi erejét kötik a totemekbe.
- **Builder-ajánlás — hely:** Bokic-parti viharszentélynél vagy nyitott, rézzel és totemekkel jelölt udvaron.
- **Builder-ajánlás — hozzáférés:** Biztonságos folyóparti út, fulladás- vagy villámcsapda nélkül.
- **Binding-döntés:** Külön binding nem szükséges.

#### 4.12. `szerzetes_mester` — Csendes Jin apát — szerzetesmester

- **Forrásból biztos kapcsolatok:** questadó: `monk_master_trial`; cél: `monk_mentor`.
- **Lore-kontekstus:** A szerzetesek Aetrinita életerejét, a Csit áramoltatják testükön.
- **Builder-ajánlás — hely:** Csendes kolostorkertben, kis tó vagy Bokic-parti meditációs terasz mellett.
- **Builder-ajánlás — hozzáférés:** A horgászathoz legyen közeli, normál módon elérhető víz, de az NPC ne álljon bele a horgászhelybe.
- **Binding-döntés:** Külön binding nem szükséges.

#### 4.13. `pap_mester` — Elenora főtisztelendő — papmester

- **Forrásból biztos kapcsolatok:** questadó: `priest_master_trial`; cél: `priest_mentor`.
- **Lore-kontekstus:** A papok Asterlayna csillagfényéből és Soleil tisztító lángjából merítenek.
- **Builder-ajánlás — hely:** Asterlayna-fényű nyilvános templomban vagy virrasztó-szentélyben, bűvölőállomás közelében.
- **Builder-ajánlás — hozzáférés:** Minden frakció és bűn nélküli játékos elérje; ne legyen frakciótemplomhoz zárva.
- **Binding-döntés:** Külön binding nem szükséges.

#### 4.14. `pakt_mester` — Az Alkuszó — boszorkánymester-tanító

- **Forrásból biztos kapcsolatok:** questadó: `warlock_master_trial`; cél: `warlock_mentor`.
- **Lore-kontekstus:** A boszorkánymesterek a Kárhozat Kapujából átszivárgó káoszt formálják paktummá.
- **Builder-ajánlás — hely:** Az Akadémia felügyelt paktumkamrájában vagy a Botera-negyed jelzett pincéjében.
- **Builder-ajánlás — hozzáférés:** Ne legyen DARK-, bűn- vagy Kárhozat-kapuhoz kötve; a warlock kaszt minden frakcióból elérje.
- **Binding-döntés:** Külön binding nem szükséges.

#### 4.15. `demonvadasz_mester` — Karyx, a Megjelölt — démonvadászmester

- **Forrásból biztos kapcsolatok:** questadó: `demon_hunter_master_trial`; cél: `demon_hunter_mentor`.
- **Lore-kontekstus:** A démonvadász a Kapu káoszát fordítja a démonok ellen, bosszúból és túlélésből.
- **Builder-ajánlás — hely:** A Kárhozat Kapuja felé vezető út biztonságos városi őrposztján vagy felkészítő csarnokában.
- **Builder-ajánlás — hozzáférés:** Az NPC a veszélyes DOOM_GATE zónán kívül maradjon; a játékos csak a próba után induljon vadászni.
- **Binding-döntés:** Külön binding nem szükséges.

#### 4.16. `sarkany_mester` — Vaelith, a Pikkelyes Bölcs — sárkányidéző-mester

- **Forrásból biztos kapcsolatok:** questadó: `evoker_master_trial`; cél: `evoker_mentor`.
- **Lore-kontekstus:** A sárkányidézők a jégsárkány-vér és Soleil lángjának sárkány-esszenciáját csapolják meg.
- **Builder-ajánlás — hely:** Ametiszttel jelölt csillagvizsgálóban vagy az Akadémia sárkánykutató termében.
- **Builder-ajánlás — hozzáférés:** Nyilvános és frakciósemleges; ne igényeljen jég- vagy lángfővárosi belépést.
- **Binding-döntés:** Külön binding nem szükséges.

#### 4.17. `kovacs_mester` — Gorvik kovácsmester — céhes megrendelő és napi questadó

- **Forrásból biztos kapcsolatok:** questadó: `kovacs_acel_rendeles`, `kovacs_szenszallitmany`, `kovacs_fegyvermustra`; cél: `blacksmith_delivery`, `kovacs_szenszallitmany`.
- **Lore-kontekstus:** A Mélység Népe volt az első kovácsnép; a mai Vasművek céhei az elveszett mesterséget élesztik újjá.
- **Builder-ajánlás — hely:** A Vasművek Akadémiáját idéző caldesterai kovácsnegyedben, működő kohók és lerakópult mellett.
- **Builder-ajánlás — hozzáférés:** Tűz- és lávabiztos, tömegben is kattintható leadópont; legyen hely inventory-rendezésre.
- **Binding-döntés:** Hagyd kötetlenül, hogy config-sorrendben tudja adni a több napi feladatot.

#### 4.18. `revesz` — A révész — a Radicora–Caldestera vízi út mesélője

- **Forrásból biztos kapcsolatok:** questadó: `revesz_vacsoraja`, `revesz_moloja`; cél: `revesz_ismerkedes`, `revesz_moloja`.
- **Lore-kontekstus:** Az új Caldestera a vizeken túl épült; a komp köti össze a gyökereket őrző Radicorával.
- **Builder-ajánlás — hely:** A tényleges kompút egyik fő, stabil mólóján, a beszállóhelytől néhány blokkal elkülönítve.
- **Builder-ajánlás — hozzáférés:** Díj nélkül elérhető beszélgetési pont; biztonságos 2×2 padló, fejhely és vízbe lökést gátló korlát.
- **Binding-döntés:** A `/npcbind revesz command komp <útvonal-id>` a leadást és a TALK/DELIVER célt még haladja, és a már elindult láncot sem töri meg, de az NPC új questet nem kínál fel. Ha a révésznek külön is fel kell ajánlania a questjeit, maradjon kötetlen, és külön jegykezelő NPC nyissa a `/komp` útvonalat.

## 5. Történeti és játékmeneti láncok

Ezek a láncok segítenek úgy elhelyezni az NPC-ket és útjelzőket, hogy a
játékos következő lépése térben is érthető legyen.

### Onboarding és világba érkezés

`onboarding_herald` → `onboarding_hunt` → `onboarding_gather` → `onboarding_utmutatas`.
A játékos az Élet Fája/Radicora kezdőteréből benefit-free Menedék-vendégként a
caldesterai Hírnökhöz jut, majd csatát, gyűjtést, kaszt- és szakmaválasztást
tanul. Assignment csak kifejezett `/faction join` után keletkezik; a lánc fix
`NEUTRAL` valutajutalmai caldesterai vendég-útravalók, nem saját-frakciós
jutalmak. A Hírnök útvonala ne vezessen közvetlenül magas szintű vadonba vagy
fizetős komp mögé.

### Szezonfejezetek

- 1. fejezet: `fejezet1_suttogasok` → `fejezet1_bizonyitek` →
  `fejezet1_orjarat` → `fejezet1_jelentes` → `fejezet1_kronikas`.
- 2. fejezet: `fejezet2_repedesek` → `fejezet2_szilankok` →
  `fejezet2_ujjaepites` → `fejezet2_orzok` → `fejezet2_pecset`.
- 3. fejezet: `fejezet3_sohajok` → `fejezet3_lelekfeny` →
  `fejezet3_visszhang` → `fejezet3_ostrom` → `fejezet3_harmadik_mondat`.

A fejezetek a Kárhozat Kapuja, a Hasadás, a Káoszkor és a Néma Királynő
félálmának nyomait követik; a hírnök zárja le a krónikalapokat. A „harmadik
mondat” narratív tét, nem megépítendő vagy elsüthető világvége-mechanika.

### A Fa és az emlékek

- `fa_uzenete_1` → `fa_uzenete_2` → `fa_uzenete_3` csak a szezon 20–40.
  napján indul; az erdei véneknél zár.
- `ver_emlekezete` → `kaszt_orokseg` az 50-es kasztszint capstone-ja; a
  hírnök hirdeti ki.
- `gyokerek_1` → `gyokerek_2` → `gyokerek_3` Radicorában zár; ez a
  gyökerek városa és Aetrinita közti kapcsolatot erősíti.

### Kasztút

Mind a 13 kaszt mintája: alappróba → beszélgetés a mesterrel → mesterpróba.
A mester NPC a mentor-lépést ugyanazon kattintással lezárhatja, majd azonnal
átadhatja a mesterpróbát. Ezért az NPC legyen egyetlen, jól kattintható entitás,
ne két azonos belső nevű másolat.

| Kaszt-ID | Mester-ID | Alappróba | Mentor | Mesterpróba |
|---|---|---|---|---|
| `warrior` | `harcos_mester` | `warrior_trial` | `warrior_mentor` | `warrior_master_trial` |
| `archer` | `ijasz_mester` | `archer_trial` | `archer_mentor` | `archer_master_trial` |
| `wizard` | `varazslo_mester` | `wizard_trial` | `wizard_mentor` | `wizard_master_trial` |
| `assassin` | `orgyilkos_mester` | `assassin_trial` | `assassin_mentor` | `assassin_master_trial` |
| `druid` | `druida_mester` | `druid_trial` | `druid_mentor` | `druid_master_trial` |
| `paladin` | `paplovag_mester` | `paladin_trial` | `paladin_mentor` | `paladin_master_trial` |
| `death_knight` | `halallovag_mester` | `death_knight_trial` | `death_knight_mentor` | `death_knight_master_trial` |
| `shaman` | `saman_mester` | `shaman_trial` | `shaman_mentor` | `shaman_master_trial` |
| `monk` | `szerzetes_mester` | `monk_trial` | `monk_mentor` | `monk_master_trial` |
| `priest` | `pap_mester` | `priest_trial` | `priest_mentor` | `priest_master_trial` |
| `warlock` | `pakt_mester` | `warlock_trial` | `warlock_mentor` | `warlock_master_trial` |
| `demon_hunter` | `demonvadasz_mester` | `demon_hunter_trial` | `demon_hunter_mentor` | `demon_hunter_master_trial` |
| `evoker` | `sarkany_mester` | `evoker_trial` | `evoker_mentor` | `evoker_master_trial` |

### Benedek, Sylvana, Gorvik és a révész

- Benedek: `merchant_distress` → `merchant_choice`, majd választás
  `merchant_bandit_hunt` vagy `merchant_trade_help`; ugyanő fogadja a heti
  beszállításokat.
- Sylvana: `forest_elder_call` → `forest_cleansing` → `forest_restoration`;
  az `erdei-szentely` a középső quest harmadik, sorrendi lépése.
- Gorvik: `blacksmith_delivery`, `kovacs_acel_rendeles`,
  `kovacs_szenszallitmany`, `kovacs_fegyvermustra` — a műhely egyszerre
  történeti pont és napi szolgáltatóhely.
- Révész: `revesz_ismerkedes` → `revesz_vacsoraja` → `revesz_moloja`.
- Sötét ív: `necromancer_initiation`, majd külön vezeklési lánc:
  `penance_1` → `penance_2` → `penance_3`.
- Háborús minilánc: `hamu_zuzmara_1` → `hamu_zuzmara_2` →
  `hamu_zuzmara_3`.

## 6. Teljes player-facing dialóguskatalógus

Az alábbi **45/45** `dialogue` blokk szó szerint a questforrásból származik.
A `give` a quest átadásakor, a `complete` a teljesítéskor jelenik meg; a sorok
körülbelül 1,5 másodpercenként követik egymást.

| Quest ID | Beszélő | Átadás (`give`) | Teljesítés (`complete`) | Választás |
|---|---|---|---|---|
| `warrior_mentor` | Aldric mester | — | „Szóval te volnál az új penge... Lássuk, mit érsz.” | — |
| `warrior_master_trial` | Aldric mester | „Az erőpróba-pálya vár. Ha végigmész rajta, harcosnak nevezhetlek.” | „Végigmentél? Akkor mostantól a tanítványom vagy. Büszkeséggel tölt el.” | — |
| `archer_mentor` | Lysa mesterasszony | — | „A szemed éles, de a lábad lassú, vándor.” | — |
| `archer_master_trial` | Lysa mesterasszony | „Fuss végig az ügyességi pályámon — az íjász lába a fegyvere fele.” | „Fürge vagy, mint a nyílvessző. Megérdemled a címed.” | — |
| `wizard_mentor` | Orvus főmágus | — | „Érzem benned az erőt... de a mágia fegyelem nélkül csak füst.” | — |
| `wizard_master_trial` | Orvus főmágus | „A mágia fegyelem. Mutasd meg, hogy uralod a bűvölés művészetét!” | „A rúnák elfogadtak. Az elméd immár penge — használd bölcsen.” | — |
| `assassin_mentor` | A Névtelen | — | „Pszt... a falak is figyelnek. Jó, hogy megtaláltál.” | — |
| `assassin_master_trial` | A Névtelen | „Az éjszaka vadjai a legjobb tanítók. Vadássz, míg az árnyak be nem fogadnak.” | „Egy hang nélkül tetted meg. Az árnyak mostantól testvérüknek hívnak.” | — |
| `druid_mentor` | Ylvara, a Vén Tölgy | — | „A Fa hangja szól belőled... de a vad még nem bízik benned.” | — |
| `druid_master_trial` | Ylvara, a Vén Tölgy | „Aetrinita kötése nem kényszer — bizalom. Szerezd meg a vadon bizalmát!” | „A vad immár melletted jár. A liget befogadott, gyermekem.” | — |
| `paladin_mentor` | Seratiel lovag-parancsnok | — | „A fény nem a kardodban él, hanem abban, amiért kirántod.” | — |
| `paladin_master_trial` | Seratiel lovag-parancsnok | „Indulj szent hadjáratra: a sötétség erős szolgáit sújtsd le!” | „A fény veled harcolt. Térdelj le — lovaggá ütlek.” | — |
| `death_knight_mentor` | Morvran, a Fagyott Penge | — | „A halál nem ellenség. Rossz kézben eszköz — a miénkben fegyver.” | — |
| `death_knight_master_trial` | Morvran, a Fagyott Penge | „Az elit fajzatok lelke a tiéd lehet. Arasd le őket!” | „A fagy és a vér immár egy benned. Kelj fel, lovag.” | — |
| `shaman_mentor` | Tharkun, a Viharlátó | — | „Az elemek nem szolgák — táncpartnerek. Tanuld a lépést!” | — |
| `shaman_master_trial` | Tharkun, a Viharlátó | „A vihar fémje a réz. Olvaszd ki a föld ereiből az elemek tiszteletére!” | „A rézben ott alszik a villám. Az elemek elfogadtak, sámán.” | — |
| `monk_mentor` | Csendes Jin apát | — | „A leggyorsabb ököl a nyugodt elméből indul.” | — |
| `monk_master_trial` | Csendes Jin apát | „Test és lélek: küzdj, majd ülj le a vízhez és halássz. Mindkettő ugyanaz a gyakorlat.” | „Az egyensúly megvan benned. A kolostor kapuja nyitva áll.” | — |
| `priest_mentor` | Elenora főtisztelendő | — | „A hit önmagában kevés — áldássá kell formálni.” | — |
| `priest_master_trial` | Elenora főtisztelendő | „Áldd meg a fegyvert, amely védi a gyengét: bűvölj a szentély asztalánál!” | „Az áldásod immár erő. A szentély a tiéd is, gyermekem.” | — |
| `warlock_mentor` | Az Alkuszó | — | „Minden hatalomnak ára van. A kérdés csak az: te fizetsz, vagy neked fizetnek?” | — |
| `warlock_master_trial` | Az Alkuszó | „A paktumhoz lélek-homok kell — a mélység pora, amelyben a holtak lélegzete ül.” | „Az alku megköttetett. A mélység immár válaszol, ha szólítod.” | — |
| `demon_hunter_mentor` | Karyx, a Megjelölt | — | „Ahhoz, hogy levadászd a sötétet, előbb bele kell nézned.” | — |
| `demon_hunter_master_trial` | Karyx, a Megjelölt | „A bosszú nem várhat: a sötétség erős szolgái közül végezz tizennyolccal!” | „A jeleid immár égnek. Vadász vagy — és soha többé préda.” | — |
| `evoker_mentor` | Vaelith, a Pikkelyes Bölcs | — | „A sárkány-mágia rezonancia. Hangolódj, vagy elég a saját tüzedben.” | — |
| `evoker_master_trial` | Vaelith, a Pikkelyes Bölcs | „Az ametiszt a sárkányének kristálya. Gyűjts eleget, hogy meghalljad!” | „Hallod már, ugye? A pikkelyesek elfogadtak, idéző.” | — |
| `merchant_distress` | Benedek kereskedő | „Segíts, vándor! A karavánomat rablók fosztották ki az úton.”<br>„Gyere közelebb, hadd meséljem el, mi történt!” | „Hála az égnek, hogy megálltál. Elmondom, mire lenne szükségem.” | — |
| `merchant_choice` | Benedek kereskedő | „Két út áll előtted: vagy megbosszulod a rablást, vagy segítesz újraindítani a kereskedést.” | „Bölcs döntés lesz, bárhogy is választasz.” | 1. „Vadásszatok a rablókra” → `merchant_bandit_hunt`<br>2. „Segítek újjáépíteni a kereskedést” → `merchant_trade_help` |
| `merchant_bandit_hunt` | Benedek kereskedő | — | „Megbosszultad a karavánomat. Örökké hálás leszek!” | — |
| `merchant_trade_help` | Benedek kereskedő | — | „A raktáram újra megtelt! Fogadd el ezt hálám jeléül.” | — |
| `forest_elder_call` | Sylvana vénasszony | „Érzed? Az erdő beteg. Valami rontás terjed a fák között.” | „Jó, hogy eljöttél. Szükségem lesz a segítségedre.” | — |
| `forest_cleansing` | Sylvana vénasszony | „Először tisztítsd meg a ligetet a romlott vadaktól.”<br>„Aztán gyűjts gyógynövényt, hogy elkészíthessem az ellenszert.”<br>„Végül vidd el az elixírt a szentélybe, hogy hasson.” | „Az erdő újra lélegzik. Köszönöm, vándor.” | — |
| `forest_restoration` | Sylvana vénasszony | — | „Az új csemeték gyökeret vernek. Az erdő emlékezni fog rád.” | — |
| `blacksmith_delivery` | Gorvik kovácsmester | „Kifogytam a nyersanyagból! Hozz vasat és szenet, jól megfizetlek.” | „Pont ez kellett. A kalapácsom újra dolgozhat.” | — |
| `kovacs_acel_rendeles` | Gorvik kovácsmester | „A céh acélt rendelt, de a kohóm nem győzi. Olvassz nekem, és megéri.” | „Ez már beszéd! A Vasművek büszke lenne rád.” | — |
| `kovacs_szenszallitmany` | Gorvik kovácsmester | „Szén nélkül hideg a kohó, hideg kohó mellett éhes a kovács. Segíts!” | „Meleg a műhely, tele a bendő. Derék munka.” | — |
| `venek_gyogyfu_szuret` | Az erdei vének | „A liget gyógyfüvei megértek. Szedj nekünk — a Fa nevében.” | „A főzeteink újra erősek lesznek. A liget köszöni.” | — |
| `venek_mez_aldozat` | Az erdei vének | „A Fa gyökereihez mézet öntünk, hogy édes legyen az álma. Hozz a kaptárakból!” | „A gyökerek isznak. Az álom ma édes lesz.” | — |
| `revesz_ismerkedes` | A révész | — | „Új arc a mólón. Jegyezd meg: a víz nem ellenség. Csak nem barát.” | — |
| `revesz_vacsoraja` | A révész | „A szoros felett hosszú a nap. Hozz halat, és mesélek valamit a vízről.” | „Jó fogás. A víz ma kegyes volt — hozzád is, hozzám is.” | — |
| `revesz_moloja` | A révész | „A móló palánkjai korhadnak. Hozz deszkát, és a komp neked mindig időben indul.” | „Erős munka. Mostantól a mólón mindig van helyed — és a történeteimnek is.” | — |
| `hirnok_hirvitel` | A hírnök | „A krónikának papír kell, a hírnek láb. Te most mindkettő leszel.” | „A hír célba ért. A krónika nem felejt — és én sem.” | — |
| `kovacs_fegyvermustra` | Gorvik kovácsmester | „Az újoncoknak penge kell, nekem meg segéd. Kovácsolj, és tanulsz is valamit.” | „Nem rossz él. Még lesz belőled valaki.” | — |
| `onboarding_herald` | Hírnök | „Üdvözöllek a szigeten, vándor! Én vagyok a Hírnök.”<br>„Mielőtt utadra engednélek, ismerkedjünk meg — gyere közelebb!” | „Örvendek! Mostantól itthon vagy — válaszd ki, melyik királysághoz csatlakozol.” | — |
| `onboarding_utmutatas` | A Hírnök | — | „Ügyes vagy, vándor. Most jön a java.”<br>„Válassz kasztot a /class paranccsal — ez határozza meg, KI leszel.”<br>„Válassz szakmát a /profession join paranccsal — ebből fogsz megélni.”<br>„És ha készen állsz: a /quest list mutatja a kasztod próbáját. Sok szerencsét!” | — |

## 7. Teljes bundled questkatalógus

A katalógus **160/160** quest ID-t fed le, config-sorrendben. A player-facing
név és leírás szó szerinti. A rejtvények konkrét megoldását ez a builderdoc sem
írja ki: a világban csak a verset és a tematikus környezetet használd, ne tegyél
ki megoldótáblát.

### 7.1. Rejtvények — első készlet (1–26)

| # / ID | Játékosnak látható név és leírás | Objektíva | Feltétel / lánc | Jutalom |
|---|---|---|---|---|
| **1.** `rejtveny_elso_nyom` | **Rejtvény: Az első nyom**<br>„Ahol minden út összefut, és a kikiáltó hangja messzebb ér, mint a harangszó — ott vár, aki a neveket jegyzi.” A cél rejtve marad: fejtsd meg a sorokat! | **Rejtvénycél:** a játékosnaplóban `???`; a megoldást ne írd ki táblára vagy útjelzőre. | rejtvény | 30 `OWN` valuta |
| **2.** `rejtveny_zold_arany` | **Rejtvény: A zöld arany**<br>„Aranyat aratsz, de nem csillog; szélben ring, míg sarló nem éri, s kenyérré lesz, mire elhallgat. Gyűjts belőle egy kosárnyit.” | **Rejtvénycél:** a játékosnaplóban `???`; a megoldást ne írd ki táblára vagy útjelzőre. | rejtvény | 80 kaszt-XP; 25 `OWN` valuta |
| **3.** `rejtveny_nyolc_lab` | **Rejtvény: A szövőmester**<br>„Nyolc lábon jár, hálót sző, de sosem halász; a sötét zugok takácsa ő. Fizesd ki tízszer a szövőbérét.” | **Rejtvénycél:** a játékosnaplóban `???`; a megoldást ne írd ki táblára vagy útjelzőre. | rejtvény | 100 kaszt-XP; 30 `OWN` valuta |
| **4.** `rejtveny_csontzenesz` | **Rejtvény: A csontzenész**<br>„Húrt penget, de nem zenész; szárazon zörög, ha táncba kezd, s nyílvesszővel köszönt. Némíts el egy tucatot a zenekarából.” | **Rejtvénycél:** a játékosnaplóban `???`; a megoldást ne írd ki táblára vagy útjelzőre. | rejtvény | 100 kaszt-XP; 30 `OWN` valuta |
| **5.** `rejtveny_ko_konnye` | **Rejtvény: A kő könnye**<br>„Tűzben sír a kő, és könnye keményebb, mint ő maga volt. Gyűjtsd össze tizenhat cseppjét a láng túloldalán.” | **Rejtvénycél:** a játékosnaplóban `???`; a megoldást ne írd ki táblára vagy útjelzőre. | rejtvény | 120 kaszt-XP; 40 `OWN` valuta |
| **6.** `rejtveny_rozsaszin_ho` | **Rejtvény: A rózsaszín hó**<br>„Hó hull, de nem tél van; szirom száll, de nem hervad a táj. Állj meg ott, ahol a fák elpirulnak.” | **Rejtvénycél:** a játékosnaplóban `???`; a megoldást ne írd ki táblára vagy útjelzőre. | rejtvény | 100 kaszt-XP; 30 `OWN` valuta |
| **7.** `rejtveny_ezust_sziv` | **Rejtvény: Az ezüst szív**<br>„A sötét tükör alatt ezüst szív dobog; türelmed a fonál, a várakozás a fegyvered. Emelj ki tízet a mélység ajándékából.” | **Rejtvénycél:** a játékosnaplóban `???`; a megoldást ne írd ki táblára vagy útjelzőre. | rejtvény | 100 kaszt-XP; 30 `OWN` valuta |
| **8.** `rejtveny_morgo_vad` | **Rejtvény: A morgó vad**<br>„Benned lakik egy vad, mely naponta morog, s csak az hallgattatja el, mi a zöld aranyból született. Csitítsd el ötször.” | **Rejtvénycél:** a játékosnaplóban `???`; a megoldást ne írd ki táblára vagy útjelzőre. | rejtvény | 80 kaszt-XP; 25 `OWN` valuta |
| **9.** `rejtveny_csillag_konnye` | **Rejtvény: A csillagok könnye**<br>„A mélység őrzi a csillagok könnyét: fényük jégbe fagyott, s csak a legmohóbb csákány éri el. Hozz fel hármat.” | **Rejtvénycél:** a játékosnaplóban `???`; a megoldást ne írd ki táblára vagy útjelzőre. | rejtvény | 150 kaszt-XP; 50 `OWN` valuta |
| **10.** `rejtveny_ket_vilag` | **Rejtvény: A két világ vándora**<br>„Ne nézz a szemébe annak, ki két világ közt jár: hosszú árnya köveket cipel, s haragja egy pillantásba fér. Küldj haza hármat közülük.” | **Rejtvénycél:** a játékosnaplóban `???`; a megoldást ne írd ki táblára vagy útjelzőre. | rejtvény | 150 kaszt-XP; 50 `OWN` valuta |
| **11.** `rejtveny_pasztor_mosolya` | **Rejtvény: A pásztor mosolya**<br>„Ahol kettőből három lesz, ott a pásztor mosolyog. Legyen okod ötször mosolyogni.” | **Rejtvénycél:** a játékosnaplóban `???`; a megoldást ne írd ki táblára vagy útjelzőre. | rejtvény | 100 kaszt-XP; 30 `OWN` valuta |
| **12.** `rejtveny_tenyernyi_jovo` | **Rejtvény: A tenyérnyi jövő**<br>„Ami ma tenyerednyi, holnap az eget tartja. Ültess nyolc jövőt a földbe.” | **Rejtvénycél:** a játékosnaplóban `???`; a megoldást ne írd ki táblára vagy útjelzőre. | rejtvény | 80 kaszt-XP; 25 `OWN` valuta |
| **13.** `rejtveny_orrhangu_kalmar` | **Rejtvény: Az orrhangú kalmár**<br>„Se kardja, se címere, mégis minden faluban úr; bólint, ha zöld kő csörren a pulton. Köss vele háromszor alkut.” | **Rejtvénycél:** a játékosnaplóban `???`; a megoldást ne írd ki táblára vagy útjelzőre. | rejtvény | 100 kaszt-XP; 30 `OWN` valuta |
| **14.** `rejtveny_zold_banat` | **Rejtvény: A zöld bánat**<br>„Zöld bánat oson mögéd hangtalan, s ölelése a végső búcsú. Előzd meg nyolcszor a búcsút.” | **Rejtvénycél:** a játékosnaplóban `???`; a megoldást ne írd ki táblára vagy útjelzőre. | rejtvény | 120 kaszt-XP; 40 `OWN` valuta |
| **15.** `rejtveny_zsakos_vandor` | **Rejtvény: A zsákos vándor**<br>„Mindenhol jár, mégsem lakik sehol; zsákjában messzi földek illata, nyelvén ezer piac zaja. Keresd meg, és hallgasd meg.” | **Rejtvénycél:** a játékosnaplóban `???`; a megoldást ne írd ki táblára vagy útjelzőre. | rejtvény | 80 kaszt-XP; 25 `OWN` valuta |
| **16.** `rejtveny_gyokerek_bolcse` | **Rejtvény: A gyökerek bölcse**<br>„A gyökerek közt ül, ki többet látott, mint a fák évgyűrűi; szava lassú, mint a moha növése. Ülj le mellé.” | **Rejtvénycél:** a játékosnaplóban `???`; a megoldást ne írd ki táblára vagy útjelzőre. | rejtvény | 80 kaszt-XP; 25 `OWN` valuta |
| **17.** `rejtveny_feher_arany` | **Rejtvény: A fehér arany**<br>„A lángok földjének mélyén terem, fehér, mint a csont, s a tornyok tőle ragyognak. Hozz belőle tizenhatot.” | **Rejtvénycél:** a játékosnaplóban `???`; a megoldást ne írd ki táblára vagy útjelzőre. | rejtvény | 90 kaszt-XP; 35 `OWN` valuta |
| **18.** `rejtveny_tuz_gyumolcse` | **Rejtvény: A tűz gyümölcse**<br>„Tüske őrzi, vörösen ég, de nem melegít; a fenyők árnyéka rejti, s aki mohó, annak a keze bánja. Szedj egy kosárnyit.” | **Rejtvénycél:** a játékosnaplóban `???`; a megoldást ne írd ki táblára vagy útjelzőre. | rejtvény | 80 kaszt-XP; 30 `OWN` valuta |
| **19.** `rejtveny_eg_kove` | **Rejtvény: Az ég köve**<br>„Az ég egyszer a mélybe költözött, és kővé vált — kékebb, mint a tenger, s a bűvölők tőle álmodnak. Fejts ki belőle két tucatnyit.” | **Rejtvénycél:** a játékosnaplóban `???`; a megoldást ne írd ki táblára vagy útjelzőre. | rejtvény | 90 kaszt-XP; 35 `OWN` valuta |
| **20.** `rejtveny_vilagito_ejfel` | **Rejtvény: A világító éjfél**<br>„Éjfél úszik a vízben, s ha megijed, ragyogó sötétet hagy maga után. Gyűjts négyet a ragyogásából.” | **Rejtvénycél:** a játékosnaplóban `???`; a megoldást ne írd ki táblára vagy útjelzőre. | rejtvény | 100 kaszt-XP; 40 `OWN` valuta |
| **21.** `rejtveny_edes_ho` | **Rejtvény: Az édes hó**<br>„Fehér, mint a hó, de nem hideg; a nádból sajtolják, s a szakács keze alatt arannyá lesz. Hozz belőle egy marékkal a világra.” | **Rejtvénycél:** a játékosnaplóban `???`; a megoldást ne írd ki táblára vagy útjelzőre. | rejtvény | 70 kaszt-XP; 25 `OWN` valuta |
| **22.** `rejtveny_siro_ko` | **Rejtvény: A síró kő**<br>„Fekete, mint az éj, s mégis lila könnyet hullat — a Kapu testvére, kit a láva és a víz nászán túl bűvölet szült. Végy belőle egyet.” | **Rejtvénycél:** a játékosnaplóban `???`; a megoldást ne írd ki táblára vagy útjelzőre. | rejtvény | 110 kaszt-XP; 45 `OWN` valuta |
| **23.** `rejtveny_lampas_nep` | **Rejtvény: A lámpás nép**<br>„Nem gyertya, mégis világít; nem hal, mégis a mélyben terem. A tenger kertjének fénye — hozz fel négyet a felszínre.” | **Rejtvénycél:** a játékosnaplóban `???`; a megoldást ne írd ki táblára vagy útjelzőre. | rejtvény | 120 kaszt-XP; 50 `OWN` valuta |
| **24.** `rejtveny_kilenc_elet` | **Rejtvény: A kilenc élet**<br>„Puha talpon jár, s a leghosszabb éjben is hazatalál. Nyerd el a bizalmát — ne karddal, hanem türelemmel.” | **Rejtvénycél:** a játékosnaplóban `???`; a megoldást ne írd ki táblára vagy útjelzőre. | rejtvény | 90 kaszt-XP; 35 `OWN` valuta |
| **25.** `rejtveny_fold_vere` | **Rejtvény: A föld vére**<br>„A hegy ereiben folyik, de nem víz; vörösen izzik, ha felszínre tör, s kővé dermed, ha nevén nevezik. Meríts belőle egy vödörrel.” | **Rejtvénycél:** a játékosnaplóban `???`; a megoldást ne írd ki táblára vagy útjelzőre. | rejtvény | 100 kaszt-XP; 40 `OWN` valuta |
| **26.** `rejtveny_hangtalan_dal` | **Rejtvény: A hangtalan dal**<br>„A mélyváros őrzi a dalt, amit senki élő nem énekelt. Egy szilánkja elég — de vigyázz: aki sokáig hallgatja, azt a csend visszahallgatja.” | **Rejtvénycél:** a játékosnaplóban `???`; a megoldást ne írd ki táblára vagy útjelzőre. | rejtvény | 140 kaszt-XP; 60 `OWN` valuta |

### 7.2. Szezonfejezetek, a Fa üzenete és az 50-es capstone (27–46)

| # / ID | Játékosnak látható név és leírás | Objektíva | Feltétel / lánc | Jutalom |
|---|---|---|---|---|
| **27.** `fejezet1_suttogasok` | **1. fejezet: Suttogások a Kapunál**<br>A Kárhozat Kapuja felől furcsa hírek érkeznek. Győzd le a Kapu kisodorta szörnyeket (10 db, 3+ szintű). | szörny legyőzése; ×10; minimum mob-szint: 3 | szezonfejezet: 1; következő: `fejezet1_bizonyitek` | 100 kaszt-XP; 40 `OWN` valuta |
| **28.** `fejezet1_bizonyitek` | **1. fejezet: Bizonyíték a mélyből**<br>A céhek bizonyítékot kérnek: gyűjts 24 lőport a Kapu kisodorta szörnyekből — a robbanás-szag a Kapu jele. | tárgy összegyűjtése; ×24; anyag: `GUNPOWDER` | szezonfejezet: 1; következő: `fejezet1_orjarat` | 120 kaszt-XP; 60 `OWN` valuta |
| **29.** `fejezet1_orjarat` | **1. fejezet: Őrjárat a peremvidéken**<br>A falvak félnek: járd a peremvidéket, és tisztítsd meg — győzz le 12 megerősített szörnyet (min. Lvl 2). | szörny legyőzése; ×12; minimum mob-szint: 2 | szezonfejezet: 1; következő: `fejezet1_jelentes` | 110 kaszt-XP; 45 `OWN` valuta |
| **30.** `fejezet1_jelentes` | **1. fejezet: Az írott szó**<br>Amit láttál, azt írásba kell adni. Készíts 6 papírt a jelentéshez — a krónika nem szóbeszédből épül. | tárgy elkészítése; ×6; anyag: `PAPER` | szezonfejezet: 1; következő: `fejezet1_kronikas` | 90 kaszt-XP; 35 `OWN` valuta |
| **31.** `fejezet1_kronikas` | **1. fejezet: A Krónikás pecsétje**<br>Vidd a híreket a fővárosba: beszélj a hírnökkel, hogy a krónikába kerüljön, amit láttál. A fejezet zárása gazdagon fizet. | beszélgetés NPC-vel; NPC: `hirnok` | szezonfejezet: 1 | 150 kaszt-XP; 120 `OWN` valuta |
| **32.** `fa_uzenete_1` | **A Fa üzenete: Új hajtások**<br>A szezon derekán a Fa megmozdul, és növekedést kér. Ültess 8 csemetét a sebzett tájakon. | blokk elhelyezése; ×8; anyag: `OAK_SAPLING`, `BIRCH_SAPLING`, `SPRUCE_SAPLING`, `JUNGLE_SAPLING`, `ACACIA_SAPLING`, `DARK_OAK_SAPLING`, `CHERRY_SAPLING` | szezonnap: 20–40; következő: `fa_uzenete_2` | 100 kaszt-XP; 40 `OWN` valuta |
| **33.** `fa_uzenete_2` | **A Fa üzenete: A ligetek védelme**<br>A rontás a fiatal ligetek felé kúszik. Vágd vissza: győzz le 12 megerősített szörnyet (min. Lvl 3). | szörny legyőzése; ×12; minimum mob-szint: 3 | szezonnap: 20–40; következő: `fa_uzenete_3` | 130 kaszt-XP; 60 `OWN` valuta |
| **34.** `fa_uzenete_3` | **A Fa üzenete: A vének hálája**<br>A liget hálás. Keresd fel az erdei véneket, hogy átadják, amit a Fa neked szánt. | beszélgetés NPC-vel; NPC: `erdei_venek` | szezonnap: 20–40 | 150 kaszt-XP; 100 `OWN` valuta; crate-kulcs: `koznapi:1` |
| **35.** `fejezet2_repedesek` | **2. fejezet: A repedések éneke**<br>A hegyek közt a föld ma is a Hasadásra emlékszik. Járd meg a szélfútta ormokat, és hallgasd meg, mit mesélnek. | biom felfedezése; biom: `windswept_hills` | szezonfejezet: 2; következő: `fejezet2_szilankok` | 120 kaszt-XP; 40 `OWN` valuta |
| **36.** `fejezet2_szilankok` | **2. fejezet: A világ könnyei**<br>A Hasadás fájdalma kristállyá dermedt a mélyben. Gyűjts 16 ametisztszilánkot — a hegy könnyeit. | tárgy összegyűjtése; ×16; anyag: `AMETHYST_SHARD` | szezonfejezet: 2; következő: `fejezet2_ujjaepites` | 140 kaszt-XP; 60 `OWN` valuta |
| **37.** `fejezet2_ujjaepites` | **2. fejezet: A céhek öröksége**<br>A birodalmakat nemcsak karddal építik újjá. Olvassz ki 24 vasrudat a Vasművek Akadémiájának emlékére. | tárgy kiolvasztása; ×24; anyag: `IRON_INGOT` | szezonfejezet: 2; következő: `fejezet2_orzok` | 140 kaszt-XP; 60 `OWN` valuta |
| **38.** `fejezet2_orzok` | **2. fejezet: A mélység őrzői**<br>A repedések mélyéről a Káoszkor őrzői másznak elő. Győzz le 15 elit szörnyet (min. Lvl 4). | szörny legyőzése; ×15; minimum mob-szint: 4 | szezonfejezet: 2; következő: `fejezet2_pecset` | 180 kaszt-XP; 80 `OWN` valuta |
| **39.** `fejezet2_pecset` | **2. fejezet: A második pecsét**<br>A krónika új lapja megtelt. Vidd a hírnöknek, amit a repedésekről megtudtál — a fejezet zárása gazdagon fizet. | beszélgetés NPC-vel; NPC: `hirnok` | szezonfejezet: 2 | 200 kaszt-XP; 150 `OWN` valuta; item: `EXPERIENCE_BOTTLE:16` |
| **40.** `fejezet3_sohajok` | **3. fejezet: Az álom sóhajai**<br>A Királynő nyugtalanul forgolódik, s a holtak megindulnak. Küldj vissza 20 élőhalottat az álomba. | szörny legyőzése; ×20; entitás: `ZOMBIE` | szezonfejezet: 3; következő: `fejezet3_lelekfeny` | 140 kaszt-XP; 50 `OWN` valuta |
| **41.** `fejezet3_lelekfeny` | **3. fejezet: Lélekfény-virrasztás**<br>A virrasztók lélekfénnyel tartják távol az árnyakat. Készíts 8 lélekfáklyát a falak őreinek. | tárgy elkészítése; ×8; anyag: `SOUL_TORCH` | szezonfejezet: 3; következő: `fejezet3_visszhang` | 150 kaszt-XP; 70 `OWN` valuta |
| **42.** `fejezet3_visszhang` | **3. fejezet: Az Első Csend visszhangja**<br>A mélyvárosok falai még őrzik a hangot, amely a Királynőt is elragadta. Hozz fel 2 visszhang-szilánkot — de ne hallgasd sokáig. | tárgy összegyűjtése; ×2; anyag: `ECHO_SHARD` | szezonfejezet: 3; következő: `fejezet3_ostrom` | 200 kaszt-XP; 100 `OWN` valuta |
| **43.** `fejezet3_ostrom` | **3. fejezet: A Kapu ostroma**<br>A Kapu felől a legerősebb fajzatok özönlenek. Törd meg az ostromot: győzz le 20 elit szörnyet (min. Lvl 5). | szörny legyőzése; ×20; minimum mob-szint: 5 | szezonfejezet: 3; következő: `fejezet3_harmadik_mondat` | 220 kaszt-XP; 120 `OWN` valuta |
| **44.** `fejezet3_harmadik_mondat` | **3. fejezet: A harmadik mondat**<br>Amit a Kapunál láttál, azt a krónikának tudnia kell — mert a harmadik mondatot senki élő nem akarja hallani. Beszélj a hírnökkel. | beszélgetés NPC-vel; NPC: `hirnok` | szezonfejezet: 3 | 250 kaszt-XP; 200 `OWN` valuta; item: `GOLDEN_APPLE:3`; crate-kulcs: `ritka:1` |
| **45.** `ver_emlekezete` | **A Vér Emlékezete**<br>Ötven szint — az ősök öröksége már majdnem egész benned. Bizonyítsd a beteljesülést: győzz le 30 elit szörnyet (min. Lvl 6). | szörny legyőzése; ×30; minimum mob-szint: 6 | minimum szint: 50; következő: `kaszt_orokseg` | 500 kaszt-XP; 300 `OWN` valuta |
| **46.** `kaszt_orokseg` | **Az örökség pecsétje**<br>A Vér Emlékezete beteljesült — az vagy, aki mindig is voltál. A hírnök hirdesse ki a neved a krónikában! | beszélgetés NPC-vel; NPC: `hirnok` | — | 200 `OWN` valuta; item: `NETHERITE_INGOT:1`, `ENCHANTED_GOLDEN_APPLE:1`; crate-kulcs: `ritka:2` |

### 7.3. Kasztpróbák, mesterek és a sötét/vezeklési ív (47–90)

| # / ID | Játékosnak látható név és leírás | Objektíva | Feltétel / lánc | Jutalom |
|---|---|---|---|---|
| **47.** `warrior_trial` | **A Harcos Próbája**<br>Bizonyítsd erődet: ejts el 15 szörnyet. | szörny legyőzése; ×15 | kaszt: `warrior` | 200 kaszt-XP |
| **48.** `archer_trial` | **Az Íjász Próbája**<br>Vadássz le 12 szörnyet. | szörny legyőzése; ×12 | kaszt: `archer` | 200 kaszt-XP |
| **49.** `wizard_trial` | **A Varázsló Próbája**<br>Gyűjts mágikus reagenseket: szedj 10 virágot. | blokk kitermelése; ×10; anyag: `DANDELION`, `POPPY`, `BLUE_ORCHID`, `ALLIUM`, `OXEYE_DAISY`, `CORNFLOWER`, `LILY_OF_THE_VALLEY` | kaszt: `wizard` | 200 kaszt-XP |
| **50.** `assassin_trial` | **Az Orgyilkos Próbája**<br>Némítsd el a sötétség 10 teremtményét. | szörny legyőzése; ×10 | kaszt: `assassin` | 200 kaszt-XP |
| **51.** `warrior_mentor` | **A Harcos Mestere**<br>Keresd fel a harcos mestert a fővárosban, és jelentkezz a mester-próbára. | beszélgetés NPC-vel; NPC: `harcos_mester` | kaszt: `warrior`; előfeltétel: `warrior_trial` | 100 kaszt-XP |
| **52.** `warrior_master_trial` | **A Harcos Mester-próbája**<br>Bizonyítsd a pengéd a vadonban: terítsd le 20 megerősített szörnyet (min. Lvl 2). | szörny legyőzése; ×20; minimum mob-szint: 2 | előfeltétel: `warrior_mentor`; adó NPC: `harcos_mester` | 400 kaszt-XP |
| **53.** `archer_mentor` | **Az Íjász Mestere**<br>Keresd fel az íjász mestert a fővárosban, és jelentkezz a mester-próbára. | beszélgetés NPC-vel; NPC: `ijasz_mester` | kaszt: `archer`; előfeltétel: `archer_trial` | 100 kaszt-XP |
| **54.** `archer_master_trial` | **Az Íjász Mester-próbája**<br>Párbaj a holt íjászokkal: ejts el 12 csontvázat — vessző vessző ellen. | szörny legyőzése; ×12; entitás: `SKELETON` | előfeltétel: `archer_mentor`; adó NPC: `ijasz_mester` | 400 kaszt-XP |
| **55.** `wizard_mentor` | **A Varázsló Mestere**<br>Keresd fel a varázsló mestert a fővárosban, és jelentkezz a mester-próbára. | beszélgetés NPC-vel; NPC: `varazslo_mester` | kaszt: `wizard`; előfeltétel: `wizard_trial` | 100 kaszt-XP |
| **56.** `wizard_master_trial` | **A Varázsló Mester-próbája**<br>Bizonyítsd a fegyelmed a bűvölő-asztalnál: bűvölj meg 3 tárgyat. | tárgy bűvölése; ×3 | előfeltétel: `wizard_mentor`; adó NPC: `varazslo_mester` | 400 kaszt-XP |
| **57.** `assassin_mentor` | **Az Orgyilkos Mestere**<br>Keresd fel az orgyilkos mestert az árnyak közt, és jelentkezz a mester-próbára. | beszélgetés NPC-vel; NPC: `orgyilkos_mester` | kaszt: `assassin`; előfeltétel: `assassin_trial` | 100 kaszt-XP |
| **58.** `assassin_master_trial` | **Az Orgyilkos Mester-próbája**<br>Vadássz az éj leple alatt: terítsd le 15 megerősített szörnyet (min. Lvl 2). | szörny legyőzése; ×15; minimum mob-szint: 2 | előfeltétel: `assassin_mentor`; adó NPC: `orgyilkos_mester` | 400 kaszt-XP |
| **59.** `druid_mentor` | **A Druida Mestere**<br>Keresd fel a druida mesterét, és jelentkezz a mester-próbára. | beszélgetés NPC-vel; NPC: `druida_mester` | kaszt: `druid`; előfeltétel: `druid_trial` | 100 kaszt-XP |
| **60.** `druid_master_trial` | **A Druida Mester-próbája**<br>Teremtsd meg az ősi kötést: szelídíts meg 3 vadat. | állat megszelídítése; ×3 | előfeltétel: `druid_mentor`; adó NPC: `druida_mester` | 400 kaszt-XP |
| **61.** `paladin_mentor` | **A Paplovag Mestere**<br>Keresd fel a paplovag mesterét, és jelentkezz a mester-próbára. | beszélgetés NPC-vel; NPC: `paplovag_mester` | kaszt: `paladin`; előfeltétel: `paladin_trial` | 100 kaszt-XP |
| **62.** `paladin_master_trial` | **A Paplovag Mester-próbája**<br>Szent hadjárat: győzz le 20 megerősített szörnyet (min. Lvl 3). | szörny legyőzése; ×20; minimum mob-szint: 3 | előfeltétel: `paladin_mentor`; adó NPC: `paplovag_mester` | 400 kaszt-XP |
| **63.** `death_knight_mentor` | **A Halállovag Mestere**<br>Keresd fel a halállovag mesterét, és jelentkezz a mester-próbára. | beszélgetés NPC-vel; NPC: `halallovag_mester` | kaszt: `death_knight`; előfeltétel: `death_knight_trial` | 100 kaszt-XP |
| **64.** `death_knight_master_trial` | **A Halállovag Mester-próbája**<br>Arass a mélységben: pusztíts el 15 elit szörnyet (min. Lvl 4). | szörny legyőzése; ×15; minimum mob-szint: 4 | előfeltétel: `death_knight_mentor`; adó NPC: `halallovag_mester` | 400 kaszt-XP |
| **65.** `shaman_mentor` | **A Sámán Mestere**<br>Keresd fel a sámán mesterét, és jelentkezz a mester-próbára. | beszélgetés NPC-vel; NPC: `saman_mester` | kaszt: `shaman`; előfeltétel: `shaman_trial` | 100 kaszt-XP |
| **66.** `shaman_master_trial` | **A Sámán Mester-próbája**<br>Az elemek kohója: olvassz ki 12 rézrudat. | tárgy kiolvasztása; ×12; anyag: `COPPER_INGOT` | előfeltétel: `shaman_mentor`; adó NPC: `saman_mester` | 400 kaszt-XP |
| **67.** `monk_mentor` | **A Szerzetes Mestere**<br>Keresd fel a szerzetes mesterét, és jelentkezz a mester-próbára. | beszélgetés NPC-vel; NPC: `szerzetes_mester` | kaszt: `monk`; előfeltétel: `monk_trial` | 100 kaszt-XP |
| **68.** `monk_master_trial` | **A Szerzetes Mester-próbája**<br>Test és lélek egyensúlya: győzz le 12 szörnyet ÉS fogj ki 5 halat. | **ALL:** 1. szörny legyőzése; ×12; HUD: „Test: 12 szörny” + 2. hal kifogása; ×5; HUD: „Lélek: 5 hal” | előfeltétel: `monk_mentor`; adó NPC: `szerzetes_mester` | 400 kaszt-XP |
| **69.** `priest_mentor` | **A Pap Mestere**<br>Keresd fel a pap mesterét, és jelentkezz a mester-próbára. | beszélgetés NPC-vel; NPC: `pap_mester` | kaszt: `priest`; előfeltétel: `priest_trial` | 100 kaszt-XP |
| **70.** `priest_master_trial` | **A Pap Mester-próbája**<br>A szentély áldása: bűvölj meg 3 tárgyat. | tárgy bűvölése; ×3 | előfeltétel: `priest_mentor`; adó NPC: `pap_mester` | 400 kaszt-XP |
| **71.** `warlock_mentor` | **A Boszorkánymester Mestere**<br>Keresd fel a boszorkánymester mesterét, és jelentkezz a mester-próbára. | beszélgetés NPC-vel; NPC: `pakt_mester` | kaszt: `warlock`; előfeltétel: `warlock_trial` | 100 kaszt-XP |
| **72.** `warlock_master_trial` | **A Boszorkánymester Mester-próbája**<br>A paktum ára: gyűjts 16 lélekhomokot a mélységből. | tárgy összegyűjtése; ×16; anyag: `SOUL_SAND` | előfeltétel: `warlock_mentor`; adó NPC: `pakt_mester` | 400 kaszt-XP |
| **73.** `demon_hunter_mentor` | **A Démonvadász Mestere**<br>Keresd fel a démonvadász mesterét, és jelentkezz a mester-próbára. | beszélgetés NPC-vel; NPC: `demonvadasz_mester` | kaszt: `demon_hunter`; előfeltétel: `demon_hunter_trial` | 100 kaszt-XP |
| **74.** `demon_hunter_master_trial` | **A Démonvadász Mester-próbája**<br>Bosszúhadjárat: semmisíts meg 18 megerősített szörnyet (min. Lvl 3). | szörny legyőzése; ×18; minimum mob-szint: 3 | előfeltétel: `demon_hunter_mentor`; adó NPC: `demonvadasz_mester` | 400 kaszt-XP |
| **75.** `evoker_mentor` | **A Sárkányidéző Mestere**<br>Keresd fel a sárkányidéző mesterét, és jelentkezz a mester-próbára. | beszélgetés NPC-vel; NPC: `sarkany_mester` | kaszt: `evoker`; előfeltétel: `evoker_trial` | 100 kaszt-XP |
| **76.** `evoker_master_trial` | **A Sárkányidéző Mester-próbája**<br>Sárkány-rezonancia: gyűjts 24 ametisztszilánkot. | tárgy összegyűjtése; ×24; anyag: `AMETHYST_SHARD` | előfeltétel: `evoker_mentor`; adó NPC: `sarkany_mester` | 400 kaszt-XP |
| **77.** `hero_trial` | **A Hős Napi Próbája**<br>Tisztítsd meg a vidéket és lásd el a falut. | **ALL:** 1. szörny legyőzése; ×10; HUD: „Szörnyek” + 2. tárgy összegyűjtése; ×16; anyag: `BREAD`; HUD: „Kenyér” | ismételhető: 24 óra; rotáció: `napi-npc`, napi 3 | 150 kaszt-XP; 40 `OWN` valuta |
| **78.** `necromancer_initiation` | **Sötét Beavatás**<br>Zarándokolj el Thanaopolisba, a Holtak Városába, és hajts végre rituálét az oltárnál. | territory felkeresése; territory: `dark-capital` | frakció: `DARK` | 100 kaszt-XP |
| **79.** `penance_1` | **Vezeklés I. — A Penge**<br>Fordítsd erődet a sötétség ellen: pusztíts el 30 erős szörnyet (min. Lvl 2). | szörny legyőzése; ×30; minimum mob-szint: 2 | frakció: `DARK` | 150 kaszt-XP |
| **80.** `penance_2` | **Vezeklés II. — Az Alázat**<br>Tanulj alázatot: fogj ki 20 halat. | hal kifogása; ×20 | előfeltétel: `penance_1` | 150 kaszt-XP |
| **81.** `penance_3` | **Vezeklés III. — A Feloldozás**<br>Az utolsó próba: győzz le 50 elit szörnyet (min. Lvl 4). A paktum megtörik. | szörny legyőzése; ×50; minimum mob-szint: 4 | előfeltétel: `penance_2` | 400 kaszt-XP; 100 `NEUTRAL` valuta; bűnök megtisztítása |
| **82.** `druid_trial` | **A Druida Próbája**<br>Kösd össze magad a természettel: párosíts 5 állatot a vadonban. | állatok szaporítása; ×5 | kaszt: `druid` | 200 kaszt-XP |
| **83.** `paladin_trial` | **A Paplovag Próbája**<br>Bizonyítsd szent eltökéltséged: ejts el 15 szörnyet. | szörny legyőzése; ×15 | kaszt: `paladin` | 200 kaszt-XP |
| **84.** `death_knight_trial` | **A Halállovag Próbája**<br>Tisztítsd meg a vidéket a rontástól: pusztíts el 15 erős szörnyet (min. Lvl 1). | szörny legyőzése; ×15; minimum mob-szint: 1 | kaszt: `death_knight` | 200 kaszt-XP |
| **85.** `shaman_trial` | **A Sámán Próbája**<br>Faragj totemeket: gyűjts 10 rönköt az erdőből. | blokk kitermelése; ×10; anyag: `OAK_LOG`, `BIRCH_LOG`, `SPRUCE_LOG`, `JUNGLE_LOG`, `ACACIA_LOG`, `DARK_OAK_LOG`, `MANGROVE_LOG`, `CHERRY_LOG` | kaszt: `shaman` | 200 kaszt-XP |
| **86.** `monk_trial` | **A Szerzetes Próbája**<br>Fegyelmezd testedet és elmédet: győzz le 15 szörnyet puszta elszántsággal. | szörny legyőzése; ×15 | kaszt: `monk` | 200 kaszt-XP |
| **87.** `priest_trial` | **A Pap Próbája**<br>Gyűjts szentfényt a szentély számára: bányássz 10 fénykövet. | blokk kitermelése; ×10; anyag: `GLOWSTONE` | kaszt: `priest` | 200 kaszt-XP |
| **88.** `warlock_trial` | **A Boszorkánymester Próbája**<br>Pecsételd meg paktumod: küldj a mélységbe 15 teremtményt. | szörny legyőzése; ×15 | kaszt: `warlock` | 200 kaszt-XP |
| **89.** `demon_hunter_trial` | **A Démonvadász Próbája**<br>Vadássz a sötétség szolgáira: semmisíts meg 15 erős szörnyet (min. Lvl 1). | szörny legyőzése; ×15; minimum mob-szint: 1 | kaszt: `demon_hunter` | 200 kaszt-XP |
| **90.** `evoker_trial` | **A Sárkányidéző Próbája**<br>Hangold rá magad a sárkány-mágiára: gyűjts 10 ametisztfürtöt. | blokk kitermelése; ×10; anyag: `AMETHYST_CLUSTER` | kaszt: `evoker` | 200 kaszt-XP |

### 7.4. Mesterségek, Benedek, Sylvana, Gorvik és a révész (91–109)

| # / ID | Játékosnak látható név és leírás | Objektíva | Feltétel / lánc | Jutalom |
|---|---|---|---|---|
| **91.** `miner_ore_haul` | **Ércfejtő Norma**<br>Bányássz ki 64 érctömböt a mélyben. | blokk kitermelése; ×64; anyag: `IRON_ORE`, `DEEPSLATE_IRON_ORE`, `GOLD_ORE`, `DEEPSLATE_GOLD_ORE`, `COPPER_ORE`, `DEEPSLATE_COPPER_ORE` | — | 60 `OWN` valuta; item: `IRON_PICKAXE:1` |
| **92.** `angler_big_catch` | **Nagy Fogás**<br>Fogj ki 20 halat a vizekből. | hal kifogása; ×20 | — | 50 `OWN` valuta; item: `FISHING_ROD:1` |
| **93.** `smith_smelt_iron` | **Kovács Kaloda**<br>Olvassz be 32 vasat a kohóban. | tárgy kiolvasztása; ×32; anyag: `IRON_INGOT` | — | 55 `OWN` valuta; item: `ANVIL:1` |
| **94.** `farmer_harvest` | **Learatás Ideje**<br>Takaríts be 96 termést a földekről. | tárgy összegyűjtése; ×96; anyag: `WHEAT`, `CARROT`, `POTATO`, `BEETROOT` | — | 45 `OWN` valuta; item: `BREAD:16` |
| **95.** `merchant_distress` | **A Kereskedő Segélykiáltása**<br>Keresd fel a bajba jutott vándorkereskedőt az úton. | beszélgetés NPC-vel; NPC: `vandor_kereskedo` | — | 20 `OWN` valuta |
| **96.** `merchant_choice` | **A Kereskedő Kérése**<br>Beszélj újra Benedekkel, és válassz utat. | beszélgetés NPC-vel; NPC: `vandor_kereskedo` | előfeltétel: `merchant_distress`; adó NPC: `vandor_kereskedo` | 10 `OWN` valuta |
| **97.** `merchant_bandit_hunt` | **Bosszú az Úton**<br>Tisztítsd meg az utat a rablóktól: számolj le 20 fosztogatóval (min. Lvl 1). | szörny legyőzése; ×20; entitás: `PILLAGER`; minimum mob-szint: 1 | előfeltétel: `merchant_choice` | 120 kaszt-XP; 80 `OWN` valuta |
| **98.** `merchant_trade_help` | **Új Kezdet a Piacon**<br>Szállíts Benedeknek friss árut, hogy újraindíthassa a kereskedést. | tárgyak leszállítása; ×40; anyag: `BREAD`, `EMERALD`; NPC: `vandor_kereskedo` | előfeltétel: `merchant_choice` | 80 `OWN` valuta |
| **99.** `forest_elder_call` | **Az Erdei Vénasszony Hívása**<br>Keresd fel az erdei vénasszonyt a szentélynél. | beszélgetés NPC-vel; NPC: `erdei_venek` | — | 15 `OWN` valuta |
| **100.** `forest_cleansing` | **Az Erdő Megtisztítása**<br>Segíts Sylvanának megtisztítani az erdőt a rontástól — három lépésben. | **SEQUENCE:** 1. szörny legyőzése; ×15; entitás: `ZOMBIE`; HUD: „Rontott élőhalottak” → 2. tárgy összegyűjtése; ×20; anyag: `SWEET_BERRIES`, `GLOW_BERRIES`; HUD: „Gyógynövények” → 3. territory felkeresése; territory: `erdei-szentely`; HUD: „Az erdei szentély” | előfeltétel: `forest_elder_call`; adó NPC: `erdei_venek` | 180 kaszt-XP; 60 `OWN` valuta |
| **101.** `forest_restoration` | **Az Erdő Újjászületése**<br>Ültess 30 csemetét, hogy az erdő újjászülethessen. | blokk elhelyezése; ×30; anyag: `OAK_SAPLING`, `BIRCH_SAPLING`, `SPRUCE_SAPLING` | előfeltétel: `forest_cleansing` | 150 kaszt-XP; 50 `OWN` valuta |
| **102.** `blacksmith_delivery` | **Utánpótlás a Kovácsműhelynek**<br>Szállíts vasat és szenet a kovácsmesternek. | tárgyak leszállítása; ×30; anyag: `IRON_INGOT`, `COAL`; NPC: `kovacs_mester` | — | 70 `OWN` valuta; item: `IRON_INGOT:8` |
| **103.** `kovacs_acel_rendeles` | **Acél a céhnek**<br>Olvassz ki 16 vasrudat a kovácsmester rendelésére. | tárgy kiolvasztása; ×16; anyag: `IRON_INGOT` | ismételhető: 24 óra; rotáció: `napi-npc`; adó NPC: `kovacs_mester` | 60 kaszt-XP; 45 `OWN` valuta |
| **104.** `kovacs_szenszallitmany` | **Szén a kohóba**<br>Szállíts 32 szenet a kovácsműhelybe. | tárgyak leszállítása; ×32; anyag: `COAL`; NPC: `kovacs_mester` | ismételhető: 24 óra; rotáció: `napi-npc`; adó NPC: `kovacs_mester` | 55 `OWN` valuta; item: `TORCH:16` |
| **105.** `venek_gyogyfu_szuret` | **Gyógyfű-szüret**<br>Gyűjts 24 magas füvet és virágot a vének főzeteihez. | tárgy összegyűjtése; ×24; anyag: `SHORT_GRASS`, `FERN`, `DANDELION`, `POPPY`, `OXEYE_DAISY`, `CORNFLOWER` | ismételhető: 24 óra; rotáció: `napi-npc`; adó NPC: `erdei_venek` | 55 kaszt-XP; 35 `OWN` valuta |
| **106.** `venek_mez_aldozat` | **Méz-áldozat**<br>Vigyél 3 mézesüveget a véneknek a Fa áldozatához. | tárgyak leszállítása; ×3; anyag: `HONEY_BOTTLE`; NPC: `erdei_venek` | ismételhető: 24 óra; rotáció: `napi-npc`; adó NPC: `erdei_venek` | 70 kaszt-XP; 40 `OWN` valuta |
| **107.** `revesz_ismerkedes` | **A révész**<br>Keresd fel a révészt a kikötőben — ő visz át a túlpartra, ha megfizeted. | beszélgetés NPC-vel; NPC: `revesz` | következő: `revesz_vacsoraja` | 60 kaszt-XP; 20 `OWN` valuta |
| **108.** `revesz_vacsoraja` | **A révész vacsorája**<br>Fogj 12 halat a révésznek — a szoros felett hosszú a nap. | hal kifogása; ×12 | adó NPC: `revesz`; következő: `revesz_moloja` | 80 kaszt-XP; 35 `OWN` valuta |
| **109.** `revesz_moloja` | **A révész mólója**<br>Vigyél 24 deszkát a révésznek a móló javításához. | tárgyak leszállítása; ×24; anyag: `OAK_PLANKS`, `SPRUCE_PLANKS`, `BIRCH_PLANKS`; NPC: `revesz` | adó NPC: `revesz` | 100 kaszt-XP; 60 `OWN` valuta; item: `COOKED_SALMON:6` |

### 7.5. Frakciós, heti, felfedező és alapanyag-küldetések (110–132)

| # / ID | Játékosnak látható név és leírás | Objektíva | Feltétel / lánc | Jutalom |
|---|---|---|---|---|
| **110.** `red_heti_kohok` | **A kohók hete**<br>Pyralingrad kohói sosem hűlnek ki: olvassz ki 64 fémrudat a Láng dicsőségére. | tárgy kiolvasztása; ×64; anyag: `IRON_INGOT`, `GOLD_INGOT`, `COPPER_INGOT` | frakció: `RED`; ismételhető: 168 óra | 200 kaszt-XP; 120 `OWN` valuta |
| **111.** `blue_heti_tisztogatas` | **A Jégmezők tisztogatása**<br>Glatziendorf határvidékét tisztán tartjuk: győzz le 60 megerősített szörnyet (min. Lvl 2). | szörny legyőzése; ×60; minimum mob-szint: 2 | frakció: `BLUE`; ismételhető: 168 óra | 200 kaszt-XP; 120 `OWN` valuta |
| **112.** `neutral_heti_vasar` | **A vásár hete**<br>Caldestera a kereskedelemből él: köss 20 üzletet a falusi kalmárokkal. | falusi kereskedés; ×20 | frakció: `NEUTRAL`; ismételhető: 168 óra | 200 kaszt-XP; 120 `OWN` valuta |
| **113.** `dark_heti_aratas` | **Lélek-aratás**<br>A Királynő szavának maradékát arasd: pusztíts el 40 erős fajzatot (min. Lvl 3). | szörny legyőzése; ×40; minimum mob-szint: 3 | frakció: `DARK`; ismételhető: 168 óra | 200 kaszt-XP; 120 `OWN` valuta |
| **114.** `hirnok_hirvitel` | **Hírvitel**<br>Vigyél 8 papírt a hírnöknek — a krónika lapjai fogynak. | tárgyak leszállítása; ×8; anyag: `PAPER`; NPC: `hirnok` | ismételhető: 24 óra; rotáció: `napi-npc`; adó NPC: `hirnok` | 50 kaszt-XP; 35 `OWN` valuta |
| **115.** `kovacs_fegyvermustra` | **Fegyvermustra**<br>Kovácsolj 2 vaskardot a céh újoncainak. | tárgy elkészítése; ×2; anyag: `IRON_SWORD` | ismételhető: 24 óra; rotáció: `napi-npc`; adó NPC: `kovacs_mester` | 60 kaszt-XP; 40 `OWN` valuta |
| **116.** `red_supply_run` | **A Láng Utánpótlása**<br>Gyűjts 40 vasat a Piros frakció hadigazdaságának. | tárgy összegyűjtése; ×40; anyag: `IRON_INGOT` | frakció: `RED` | 90 `OWN` valuta |
| **117.** `blue_coastal_watch` | **Kék Parti Őrjárat**<br>Védd a partvidéket: ölj meg 20 vízi szörnyet. | szörny legyőzése; ×20; entitás: `DROWNED` | frakció: `BLUE` | 90 `OWN` valuta |
| **118.** `neutral_trade_pact` | **Semleges Kereskedelmi Egyezmény**<br>Köss 10 kereskedést egy falusival a Semleges kassza javára. | falusi kereskedés; ×10 | frakció: `NEUTRAL` | 90 `OWN` valuta |
| **119.** `dark_bone_harvest` | **Csontgyűjtés a Sötétségnek**<br>Arass 25 csontvázat a Sötét frakció rituáléihoz. | szörny legyőzése; ×25; entitás: `SKELETON` | frakció: `DARK` | 90 `OWN` valuta |
| **120.** `wanderlust_desert` | **Vándorlás — Sivatag**<br>Fedezd fel a végtelen sivatagot. | biom felfedezése; biom: `desert` | — | 80 kaszt-XP; 30 `OWN` valuta |
| **121.** `wanderlust_jungle` | **Vándorlás — Dzsungel**<br>Vágj utat a sűrű dzsungelben. | biom felfedezése; biom: `jungle` | — | 80 kaszt-XP; 30 `OWN` valuta |
| **122.** `acrobat_challenge` | **Akrobata Kihívás**<br>Teljesítsd a kezdő ügyességi pályát (/parkour start kezdo_parkour). | parkour teljesítése; pálya: `kezdo_parkour` | — | 100 kaszt-XP; 40 `OWN` valuta |
| **123.** `jegvirag_szuret` | **Jégvirág-szüret**<br>Gyűjts 12 búzavirágot a Jégvirág-por őrléséhez. | tárgy összegyűjtése; ×12; anyag: `CORNFLOWER` | frakció: `BLUE`; ismételhető: 24 óra | 60 kaszt-XP; 25 `OWN` valuta |
| **124.** `parazs_gyujtes` | **Parázs a kohóknak**<br>Szerezz 8 lángrúd-port a Parázsmag kinyeréséhez. | tárgy összegyűjtése; ×8; anyag: `BLAZE_POWDER` | frakció: `RED`; ismételhető: 24 óra | 70 kaszt-XP; 30 `OWN` valuta |
| **125.** `borostyan_kutatas` | **Borostyán a mélyből**<br>Hozz fel 6 nyers aranyat a Mélységi Borostyán tisztításához. | tárgy összegyűjtése; ×6; anyag: `RAW_GOLD` | ismételhető: 48 óra; rotáció: `napi-npc` | 60 kaszt-XP; 25 `OWN` valuta |
| **126.** `fonixpihe_vadaszat` | **Főnixpihe-vadászat**<br>Ejts el 10 lángőrt (blaze) — pihéik a Napfogyatkozás íjához kellenek. | szörny legyőzése; ×10; entitás: `BLAZE` | frakció: `RED` | 120 kaszt-XP; 45 `OWN` valuta |
| **127.** `sarkanycsont_kutato` | **Sárkánycsont-kutató**<br>Terítsd le a vidék erősebb szörnyeit — a csontjaik közt sárkánycsont lapulhat. | szörny legyőzése; ×8; minimum mob-szint: 5 | frakció: `BLUE` | 140 kaszt-XP; 50 `OWN` valuta |
| **128.** `porkolt_lakoma` | **Ünnepi lakoma**<br>Fogyassz el egy tál pörköltet — Glatziendorf ünnepi étke erőt ad. | tárgy elfogyasztása; ×1; anyag: `RABBIT_STEW` | frakció: `BLUE`; ismételhető: 72 óra | 50 kaszt-XP; item: `COOKED_SALMON:4` |
| **129.** `uti_kenyer` | **A vándor konyhája**<br>Süss 6 kenyeret — a karavánok úti kenyérként viszik magukkal. | tárgy elkészítése; ×6; anyag: `BREAD` | ismételhető: 24 óra; rotáció: `napi-npc` | 40 kaszt-XP; 15 `OWN` valuta |
| **130.** `viharkvarc_fejto` | **A vihar kövei**<br>Gyűjts 24 kvarcot a Viharkvarc fejtéséhez. | tárgy összegyűjtése; ×24; anyag: `QUARTZ` | ismételhető: 48 óra; rotáció: `napi-npc` | 80 kaszt-XP; 35 `OWN` valuta |
| **131.** `korrupt_irtas` | **A rontás ellen**<br>Irtsd a vadon fajzatait — a Fa minden leölt szörnnyel könnyebben lélegzik. | szörny legyőzése; ×20 | ismételhető: 24 óra; rotáció: `napi-npc` | 90 kaszt-XP; 30 `OWN` valuta |
| **132.** `karhozat_zarandoklat` | **Zarándoklat a Kapuhoz**<br>Járj a Kárhozat Kapujánál — és gyere vissza élve. | territory felkeresése; territory: `karhozat-kapuja` | — | 150 kaszt-XP; 60 `OWN` valuta |

### 7.6. Onboarding, napi rotáció, új rejtvények, miniláncok és beszállítás (133–160)

| # / ID | Játékosnak látható név és leírás | Objektíva | Feltétel / lánc | Jutalom |
|---|---|---|---|---|
| **133.** `onboarding_herald` | **Beszélj a hírnökkel**<br>Keresd fel a hírnököt a semleges fővárosban, és válaszd ki a királyságodat. | beszélgetés NPC-vel; NPC: `hirnok` | következő: `onboarding_hunt` | 15 `NEUTRAL` valuta (vendég-útravaló) |
| **134.** `onboarding_hunt` | **Első csata**<br>Bizonyítsd, hogy készen állsz: ölj meg 5 szörnyet. | szörny legyőzése; ×5 | előfeltétel: `onboarding_herald`; következő: `onboarding_gather` | 20 `NEUTRAL` valuta (vendég-útravaló) |
| **135.** `onboarding_gather` | **Első gyűjtögetés**<br>Vágj ki 10 rönköt — kezdő felszerelés vár érte. | tárgy összegyűjtése; ×10; anyag: `OAK_LOG`, `BIRCH_LOG`, `SPRUCE_LOG`, `JUNGLE_LOG`, `ACACIA_LOG`, `DARK_OAK_LOG`, `MANGROVE_LOG`, `CHERRY_LOG` | előfeltétel: `onboarding_hunt`; következő: `onboarding_utmutatas` | 25 `NEUTRAL` valuta (vendég-útravaló); item: `WOODEN_PICKAXE:1`, `BREAD:8`; crate-kulcs: `koznapi:1` |
| **136.** `onboarding_utmutatas` | **Az utad kezdete**<br>Térj vissza a hírnökhöz — elmondja, hogyan válassz kasztot (/class), szakmát (/profession join), és hol vár a kaszt-próbád (/quest list). | beszélgetés NPC-vel; NPC: `hirnok` | előfeltétel: `onboarding_gather` | 50 kaszt-XP; 25 `NEUTRAL` valuta (vendég-útravaló) |
| **137.** `napi_ospatak` | **A vén halász kérése**<br>Az Őspatak öreg halásza már nem bírja a hálót. Fogj helyette nyolc halat — a fele a tiéd. | hal kifogása; ×8 | ismételhető: 24 óra; rotáció: `napi-npc` | 120 kaszt-XP; 35 `OWN` valuta |
| **138.** `napi_erclelet` | **Ércjárat**<br>A kovácsműhely kifogyott a nyersanyagból. Fejts ki huszonnégy vas- vagy rézércet a környék tárnáiból. | blokk kitermelése; ×24; anyag: `IRON_ORE`, `DEEPSLATE_IRON_ORE`, `COPPER_ORE`, `DEEPSLATE_COPPER_ORE` | ismételhető: 24 óra; rotáció: `napi-npc` | 120 kaszt-XP; 35 `OWN` valuta |
| **139.** `napi_csontszuret` | **Csontszüret**<br>Éjszakánként csontvázak gyülekeznek a földeken. Tizenkettőt küldj vissza a földbe, mielőtt learatják, amit nem ők vetettek. | szörny legyőzése; ×12; entitás: `SKELETON` | ismételhető: 24 óra; rotáció: `napi-npc` | 130 kaszt-XP; 35 `OWN` valuta |
| **140.** `napi_fuvesasszony` | **A füvesasszony listája**<br>A füvesasszony főzeteihez friss virág kell — tizenhat szálat kér, fajtája mindegy, csak a rét adja. | tárgy összegyűjtése; ×16; anyag: `DANDELION`, `POPPY`, `CORNFLOWER`, `OXEYE_DAISY`, `AZURE_BLUET`, `ALLIUM` | ismételhető: 24 óra; rotáció: `napi-npc` | 110 kaszt-XP; 30 `OWN` valuta |
| **141.** `rejtveny_soharcos` | **Rejtvény: A sosem-harcolt harcos**<br>„Páncélt hord, de sosem vérzett. Áll, ahová állítod, s némán viseli, amit rábízol. Teremtsd meg — fából lesz, mégis katona.” | **Rejtvénycél:** a játékosnaplóban `???`; a megoldást ne írd ki táblára vagy útjelzőre. | rejtvény | 30 `OWN` valuta |
| **142.** `rejtveny_feher_csend` | **Rejtvény: A fehér csend**<br>„Van egy táj, ahol a fű elfelejtett zöldellni, s a hó nem vendég, hanem gazda. Állj meg ott, ahol a csend fehér.” | **Rejtvénycél:** a játékosnaplóban `???`; a megoldást ne írd ki táblára vagy útjelzőre. | rejtvény | 30 `OWN` valuta |
| **143.** `rejtveny_melysegek_szava` | **Rejtvény: A mélység szava**<br>„A tükör alatt némák laknak; öt szótlant húzz a fényre, és a víz elárulja, amit a part sosem mond ki.” | **Rejtvénycél:** a játékosnaplóban `???`; a megoldást ne írd ki táblára vagy útjelzőre. | rejtvény | 30 `OWN` valuta |
| **144.** `rejtveny_hamu_kenyere` | **Rejtvény: A hamu kenyere**<br>„Ami a földben hallgatott, a tűzben szólal meg. Tizenkétszer szólaltasd meg — a kemence tudja, mit ér a türelem.” | **Rejtvénycél:** a játékosnaplóban `???`; a megoldást ne írd ki táblára vagy útjelzőre. | rejtvény | 30 `OWN` valuta |
| **145.** `red_heti_hatartisztitas` | **A Vérszavanna határjárása**<br>A Láng nem tűr férget a portáján. Járd a határvidéket, és tisztíts meg negyvenöt szörnyet Pyralingrad nevében. | szörny legyőzése; ×45 | frakció: `RED`; ismételhető: 168 óra | 200 kaszt-XP; 120 `OWN` valuta |
| **146.** `blue_heti_jegszuret` | **Jégszüret**<br>Glatziendorf falai a fagyból élnek. Vágj ki kilencvenhat jégtömböt az örök tél mezőiről a kőfaragóknak. | blokk kitermelése; ×96; anyag: `ICE`, `PACKED_ICE`, `BLUE_ICE` | frakció: `BLUE`; ismételhető: 168 óra | 200 kaszt-XP; 120 `OWN` valuta |
| **147.** `neutral_heti_vasarjaras` | **Vásárjárás**<br>A Menedék ereje az alku — de a vásár áruból él. Szállíts kenyeret és smaragdot a vándor kereskedőnek, hogy a karaván tovább indulhasson. | tárgyak leszállítása; ×25; anyag: `BREAD`, `EMERALD`; NPC: `vandor_kereskedo` | frakció: `NEUTRAL`; ismételhető: 168 óra | 200 kaszt-XP; 120 `OWN` valuta |
| **148.** `dark_heti_csonttized` | **Csont-tized**<br>Thanaopolis utcáin a holtak járnak — de a vadonban kóborlók senkinek sem szolgálnak. Hatvan gazdátlan élőhalottat hajts be a tizedbe. | szörny legyőzése; ×60; entitás: `ZOMBIE` | frakció: `DARK`; ismételhető: 168 óra | 220 kaszt-XP; 130 `OWN` valuta |
| **149.** `gyokerek_1` | **A gyökerek emlékezete I.**<br>A vén kertész szerint a vérfák gyökere emlékszik arra, amit a világ elfelejtett. Gyűjts nyolc mangrove-gyökeret a mocsaras partokról. | tárgy összegyűjtése; ×8; anyag: `MANGROVE_ROOTS` | következő: `gyokerek_2` | 120 kaszt-XP; 40 `OWN` valuta |
| **150.** `gyokerek_2` | **A gyökerek emlékezete II.**<br>„A gyökér csak akkor beszél, ha új hajtást hall.” Ültess el négy mangrove-hajtást — hadd folytassák, amit az öregek elkezdtek. | blokk elhelyezése; ×4; anyag: `MANGROVE_PROPAGULE` | előfeltétel: `gyokerek_1`; következő: `gyokerek_3` | 130 kaszt-XP; 45 `OWN` valuta |
| **151.** `gyokerek_3` | **A gyökerek emlékezete III.**<br>A hajtások megeredtek. Vidd el a hírüket a Világfa tövéhez, Radicorába — a gyökerek ott futnak össze, ahol a történetek is. | territory felkeresése; territory: `radicora` | előfeltétel: `gyokerek_2` | 150 kaszt-XP; 60 `OWN` valuta; crate-kulcs: `koznapi:1` |
| **152.** `hamu_zuzmara_1` | **Hamu és zúzmara I. — Az él**<br>A veterán zsoldos szerint a háborút hétköznap nyerik meg. Bizonyíts: terítsd le huszonöt szörnyet, és tartsd élesen a mozdulatot. | szörny legyőzése; ×25 | következő: `hamu_zuzmara_2` | 140 kaszt-XP; 45 `OWN` valuta |
| **153.** `hamu_zuzmara_2` | **Hamu és zúzmara II. — A fal**<br>„Aki csak vág, elesik. Aki fedez is, hazatér.” Készíts magadnak pajzsot — a zsoldos addig szóba se áll veled. | tárgy elkészítése; ×1; anyag: `SHIELD` | előfeltétel: `hamu_zuzmara_1`; következő: `hamu_zuzmara_3` | 140 kaszt-XP; 45 `OWN` valuta |
| **154.** `hamu_zuzmara_3` | **Hamu és zúzmara III. — Pengére írt eskü**<br>Az utolsó lecke: a fegyver annyit ér, amennyit belefoglalsz. Bűvölj meg három tárgyat — aztán a zsoldos kezet ráz, és a hadi-ablakban már bajtársként köszönt. | tárgy bűvölése; ×3 | előfeltétel: `hamu_zuzmara_2` | 160 kaszt-XP; 60 `OWN` valuta; crate-kulcs: `ritka:1` |
| **155.** `beszallito_fa` | **Beszállítás: épületfa**<br>A vándor kereskedő szekere üresen kong. Szállíts le hatvannégy szál rönköt — az ácsok már várják a túlparton. | tárgyak leszállítása; ×64; anyag: `OAK_LOG`, `SPRUCE_LOG`, `BIRCH_LOG`, `DARK_OAK_LOG`, `ACACIA_LOG`, `MANGROVE_LOG`, `CHERRY_LOG`; NPC: `vandor_kereskedo` | ismételhető: 168 óra | 150 kaszt-XP; 90 `OWN` valuta |
| **156.** `beszallito_ko` | **Beszállítás: faragott kő**<br>A kereskedő kőműveseknek visz árut: kilencvenhat zúzottkövet kér, egyenesen a tárnából. | tárgyak leszállítása; ×96; anyag: `COBBLESTONE`, `COBBLED_DEEPSLATE`; NPC: `vandor_kereskedo` | ismételhető: 168 óra | 150 kaszt-XP; 90 `OWN` valuta |
| **157.** `beszallito_elelem` | **Beszállítás: úti elemózsia**<br>Hosszú az út a királyságok közt. Adj le negyven adag ételt a kereskedőnek — a karavánok gyomra nagy úr. | tárgyak leszállítása; ×40; anyag: `BREAD`, `COOKED_BEEF`, `COOKED_PORKCHOP`, `COOKED_CHICKEN`, `COOKED_MUTTON`, `BAKED_POTATO`; NPC: `vandor_kereskedo` | ismételhető: 168 óra | 150 kaszt-XP; 90 `OWN` valuta |
| **158.** `beszallito_bor` | **Beszállítás: cserzett bőr**<br>Könyvkötőknek, nyergeseknek, vérteseknek — bőr mindig kell. Huszonnégy darabot vár a kereskedő. | tárgyak leszállítása; ×24; anyag: `LEATHER`; NPC: `vandor_kereskedo` | ismételhető: 168 óra | 150 kaszt-XP; 90 `OWN` valuta |
| **159.** `heti_nagyvadaszat` | **A nagy hajtóvadászat**<br>A krónikás hetente megnyitja a vadászlajstromot: százhúsz szörny, aki győzi. A neved a lap tetejére kerül — a jutalom a lap aljára. | szörny legyőzése; ×120 | ismételhető: 168 óra | 400 kaszt-XP; 150 `OWN` valuta; crate-kulcs: `ritka:1` |
| **160.** `heti_nagyhalaszat` | **A nagy fogás**<br>A kikötő heti fogadása: negyven hal egy hét alatt. A vén halászok szerint lehetetlen. A vén halászok sok mindent mondanak. | hal kifogása; ×40 | ismételhető: 168 óra | 300 kaszt-XP; 120 `OWN` valuta; crate-kulcs: `koznapi:2` |

## 8. Builder acceptance checklist

- [ ] A FancyNpcs listában mind a 18 belső ID pontosan egyszer szerepel.
- [ ] A plugin indulási logja mind a 18 quest-NPC-t a helyén találja; nincs hiányzó NPC warning.
- [ ] Minden NPC-t normál játékos, minden érintett frakció és megfelelő kaszt elér.
- [ ] Arany és zöld személyes marker is kipróbálva; a marker nem másik emeleten lebeg.
- [ ] `TALK_TO_NPC` egy kattintással halad; `DELIVER_ITEMS` kevés tárggyal nem vesz el semmit, elég tárggyal pontosan a szükséges mennyiséget veszi át.
- [ ] A több questet adó NPC config-sorrendje és napi rotációja végigpróbálva.
- [ ] Minden explicit `/npcbind` esetén dokumentált, hogy a legacy giver-scan miért hagyható el.
- [ ] FancyNpcs nélküli stagingben `/quest talk <npc-id>` működik; aktív híd mellett a fallback policy megfelel a confignek.
- [ ] A négy territory ID létezik; a `dark-capital` ↔ `thanaopolis` eltérés rendezve.
- [ ] A `kezdo_parkour` teljesítése haladást ad az `acrobat_challenge` questhez.
- [ ] Mind az öt biome túlélő módban és tiltott portál nélkül elérhető.
- [ ] Onboarding új fiókkal, reconnecttel és teljes inventoryval végigpróbálva.
- [ ] A 45 dialógus give/complete időzítése, a Benedek-választás mindkét ága és a questjutalmak tesztelve.
- [ ] WorldEdit vagy világmásolás után NPC-nevek, territoryk, komp, parkour és útjelzés újraauditálva.
- [ ] Az átadólap tartalmazza a tényleges világot, koordinátát, felelőst, mentést, pozitív/negatív próbát és bizonyítékhelyet.

### Gyors számellenőrzés

- Questek: `160`.
- Egyedi quest-NPC ID-k: `18`.
- Dialógusos questek: `45`.
- Territory ID-k: `4`.
- Parkour ID-k: `1`.
- Biom ID-k: `5`.

A számok a dokumentált commit `quests.yml` fájljából generált leltárak; élő
world-state-et csak szerveroldali listázás és playtest igazol.
