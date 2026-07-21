# 🏗 Építész-útmutató — mit kell megépíteni az IceSMP világába

> Ez a doksi a szerver-építőknek szól: **mit**, **hova**, **kb. mekkorára**, **milyen
> stílusban** — és **miért**. Minden szakasznál ott van, hogy az adott építmény melyik
> plugin-rendszert szolgálja ki, és mi történik, amíg nincs kész (spoiler: általában
> semmi nem törik el, csak az adott tartalom "vár"). A lore-hűség forrása a
> `docs/LORE.md` (kódex). A koordináták/zónák kijelölése az admin-csapat dolga — az
> építmény elkészülte UTÁN.

---

## Szótár és alapfogalmak (építészeknek, akik nem élnek a plugin-configban)

**Ki mit csinál? — a munkamegosztás:**
- **TI (építészek):** megépítitek a HELYET (város, tér, liget, móló, oltár, kazamata).
- **Az admin-csapat:** a kész hely köré húzza a zónát, beállítja a spawn-pontokat,
  KIHELYEZI az NPC-ket és bekonfigurálja a koordinátákat. Nektek EGYETLEN parancsot
  sem kell tudnotok — a doksiban szereplő parancsok az adminoknak szólnak.
- **A plugin:** adja a tartalmat (questek, események, mobok) — az már meg van írva,
  csak a helyet és az NPC-t várja.

**Fogalmak:**
- **NPC:** a plugin által kirakott, nem-játékos figura (kinézete beállítható skin).
  Rákattintva beszél, questet ad, boltot nyit stb. — a funkciót az admin köti rá.
  **Nektek csak a HELYÉT kell megépíteni** (egy tér, egy pult, egy céhház).
- **Quest / quest-lánc:** játékos-küldetés ("ölj 5 szörnyet", "beszélj a hírnökkel").
  A lánc = egymás után automatikusan következő küldetések. MÁR MEG VANNAK ÍRVA —
  senki nem "rakja le" őket, csak az NPC-jük kell a világba.
- **Onboarding-lánc:** az ÚJ játékos első küldetés-sora, ami az első belépéskor
  magától elindul: "Beszélj a hírnökkel → ölj 5 szörnyet → gyűjts 10 rönköt → …".
  Ez fogja kézen a kezdőt — ezért kritikus, hogy a hírnök NPC a spawn közelében álljon.
- **Zóna (territórium):** az adminok által kijelölt terület-védelem. A `protected`/
  `capital` zónában SENKI nem építhet és nem bonthat — **a városaitokat a játékosok
  NEM tudják kiütni, sem creeper, sem TNT** (a zóna kijelölése után).
- **Claim:** a játékosok SAJÁT kis birtoka — csak a sima `faction` zónákban (tanyavidék)
  claimelhetnek, a városaitokban soha.
- **Spawn / first-join spawn:** ahol az új játékos ELŐSZÖR megjelenik. Az admin
  állítja be egy pontos koordinátára — nektek egy fogadó-teret kell építeni hozzá.
- **Mob-szint (Lvl):** a szörnyek ereje 0-10-ig. A Lvl 0-1 = sima vanília-erősségű
  zombi/csontváz — a kezdő játékos ellenfele. A Lvl 10 = többszörös élet/sebzés,
  végjáték. "Kezdő-barát környék" = ott csak Lvl 0-1 szörnyek spawnolnak éjjel.
  A szörnyek az épületeiteket NEM bántják (és a védett zónában semmi sem).
- **Lore-nevek gyorstalpaló:** **a Fa** = a Világfa, a világ közepén álló óriásfa
  (lásd Radicora — ez EGY ÉPÍTMÉNY, ti építitek!); **Bokic** = a világ nagy folyója,
  a Fa vidékén folyik át (a lore szerint "egy alvó szív ere" — sosem fagy be);
  **Radicora/Caldestera** = a semleges nép régi és új fővárosa; **Thanaopolis** =
  a holtak romvárosa; **Olethropyla / Kárhozat Kapuja** = a nagy törött Nether-portál.

---

## 0. A világ elrendezése (a VALÓS térkép szerint)

A kijelölt helyszínek (hozzávetőleges koordináták a seed-térképről):

```
                          spawn (0,0) — a Fa + RADICORA
                                        │      ~6k, óceánnal
                                        │         ╲
                                        │      🌊 ~2k átkelés (KOMP)
                                        │           ╲
                                        │        ⚖ S — ÚJ CALDESTERA (kb. +5000, +2000)
                                        │
              (délnyugat)                              (délkelet)
   🔥 T — PYRALINGRAD (-8908, +9938 ✔)      ❄ K — GLATZIENDORF (kb. +5500, +12500)
                     ╲                                  ╱
                      ☠ OLETHROPYLA (Kárhozat Kapuja) — a kettő közti déli senkiföldjén
```

- **Spawn (0,0):** a Fa tövében áll **Radicora** („a gyökerek városa", népnyelven
  Ó-Caldestera) — innen indul minden új játékos.
- **S — új Caldestera** ÉK-en, ~6k-ra: a tényleges NEUTRAL **főváros**. A szárazföldtől
  ~2k blokknyi óceán választja el — az átkelés **komppal** megy, nem híddal.
- **T — Pyralingrad** DNy-on: **RÖGZÍTETT hely (-8908, +9938)**, ~13.3k a spawntól,
  magashegyi terepen (Y~290 környékén) — hegyi lángváros! **K — Glatziendorf** DK-en (~13.5k).
- **Kárhozat Kapuja:** a déli sávban, T és K között félúton javasolt (az építész dönt).
- **Thanaopolis (DARK):** még nincs kijelölve — félreeső, sötét vidékre való.

### Miért nem gond a nagy távolság? — a mob-szintezés, érthetően

A mobok ereje két szabályból áll össze, és **mindkettő él**:

1. **Táv-szabály:** a spawntól (0,0) távolodva minden 1000 blokk +1 mob-szint (max 10).
   Ez adja a világ "kifelé veszélyesebb" ívét.
2. **Zóna-rámpa:** minden **város-zóna** (capital/faction/protected territórium)
   biztonsági buborékot kap. A zóna **belsejében a természetes spawn Lvl 0**, a
   zóna **peremétől kifelé** pedig 250 blokkonként nő eggyel a szint, amíg utol nem
   éri a táv-szabály szerinti "helyi normált".

**A gyakorlatban:** a ~13.3k-ra épült Pyralingrad kapujában Lvl 0-1 szörnyek vannak,
~500 blokkra Lvl 2, és kb. 2500 blokkra éri el a Lvl 10-et. Vagyis a fővárosok
környéke kezdőbarát, de aki messzebb merészkedik, az a végjáték-vadonban találja
magát. **Építészként ebből az következik:** a városok köré nyugodtan tervezhettek
tanyákat/falvakat (az első ~1000 blokk szelíd), a "veszélyes látványosságokat"
(romok, kazamata-bejáratok) viszont érdemes kijjebb tenni.

**Kivételek:** a Kárhozat-zóna és a kazamaták NEM számítanak biztonságos zónának
(ott bónusz-szintek vannak); a **DARK territórium pedig fordítva működik** — ott
minden spawnoló mob +2 szintet kap, és az élőhalott nappal sem ég el (a Kitaszítottak
földje nem szelídített vidék).

### Közlekedés és a Nether — két kőbe vésett szabály

- **Komp:** a hosszú vízi átkelések megoldása a `/komp` rendszer — két fix kikötő-pont
  között teleportál (viteldíjért). Móló + révész-NPC kell hozzá mindkét parton
  (részletek a 8. szakaszban). Az új Caldestera felé **ez a fő út**.
- **Nether-portál sehol sem gyújtható** — a világ EGYETLEN élő kapuja a Kárhozat
  Kapuja lesz, amit az admin-csapat gyújt meg. Következmény: **a Netherbe csak a
  PvP-senkiföldjén át vezet út** — ez szándékos játéktervezés (minden nether-expedíció
  kockázat), és egyben lore (a kapuk kora lejárt). Ha valaki portált próbál gyújtani,
  a plugin megfogja és üzenetet kap.

---

## 0/b. Amit építés közben érdemes tudni a plugin-oldalról

**Semmi nem törik el attól, hogy még nincs kész.** A rendszerek "fail-open" vagy
"várakozó" módban indulnak:
- amíg nincs kijelölt semleges főváros, a frakcióváltás kapuja nem zár;
- amíg egy quest-NPC nincs kint, a hozzá tartozó lánc egyszerűen nem halad
  (a küldetés nem hibázik, csak vár);
- amíg nincs frakció-spawn beállítva, a spawn-teleportok no-opok.
Tehát nyugodtan lehet szakaszosan építeni és bekötni.

**A zóna-típusok — mit csinálnak valójában:**

| Zóna-típus | Ki építhet? | Claimelhető? | Extra hatás |
|---|---|---|---|
| `faction` | csak a tulajdonos frakció tagjai | IGEN (játékos-birtokok ide valók) | — |
| `protected-faction` | SENKI | nem | falak/műemlékek pajzsa |
| `protected-city` | SENKI | nem | semleges műemlékek (pl. Radicora) |
| `capital` | SENKI | nem | **bank/váltó-kapu** + fővárosi törvény + frakcióváltás-helyszín |
| `doom-gate` | SENKI | nem | legális PvP, bűn-mentes ölés, bónusz-mobok |
| `dungeon` | SENKI | nem | kulcs-kapu + heti pecsét + kemény mobok |

**Miért fontos a `capital` zóna?** Három rendszer is rá van kötve: (1) a **bank és a
valutaváltás CSAK fővárosban** használható — ezért kell minden fővárosba bank-épület
NPC-kkel; (2) a **fővárosi törvény** (Caldesterában fegyvertilalom + körözött-kitiltás);
(3) **frakciót váltani** csak a semleges fővárosban lehet.

**Élő config:** szinte minden számot (rámpa, viteldíj, mob-szintek…) az adminok
játék közben, restart nélkül tudnak hangolni — ha építés közben valami "nem jónak
érződik" (pl. túl meredek a rámpa), szóljatok, egy parancs átállítani.

---

## 1. Caldestera — a Semleges Főváros (a legfontosabb építés!)

**Lore:** a világ kereskedelmi és tudományos központja — a Fától ELKÖLTÖZÖTT nép
ragyogó új városa a vizeken túl. **Tölgy, tégla és kvarc** gazdag polgári városa;
szigorúan **fegyvermentes** (a plugin be is tartatja). Az utcákon szarvasok, a falak
közt Vámház, a sikátorokban a Botera-negyed feketepiaca.

**Méret:** kb. **200×200 blokk** városmag + falak. A falakon KÍVÜL hagyjatok 20-30
blokk szabad sávot: a **szezonzáró boss a fal mellé spawnol** (minden szezon végén
egyszer), és kell hely a csatának.

**Miért ez a legfontosabb?** Ide fut be a gazdaság (bank-kapu!), a frakcióváltás, a
szezon-emlékmű, a mester-questek nagy része és a Vének Tanácsa politikája. Amíg
Caldestera nincs kész, a játék "falu-móddal" megy — működik, de a városi rétegek
(törvény, piac-negyed hangulat, tanács-terem) nem élnek.

**Kötelező elemek (mindnek van plugin-funkciója):**

| Épület/hely | Mit szolgál | Technikai kötés |
|---|---|---|
| **Főtér a Hírnökkel** | onboarding + útmutatás + fejezet-questek + napi hírvitel — a legtöbbet használt NPC! | `/npc create hirnok` |
| **Frakció-követségek / választó-terem** | frakcióVÁLTÁS (csak itt működik) | 3-4 NPC: `/npcbind <npc> faction <red/blue/dark>` |
| **Bank / Bankárszövetség székház** | bank + váltás CSAK fővárosban megy — enélkül a gazdaság áll | banker: `/npcbind <npc> bank`, váltó: `exchange` |
| **Tanács-terem (ÚJ!)** | a Vének Tanácsa (heti választott 3 fő) székhelye — flavor a /tanacs köré | építmény, kötés nem kell |
| **Árfolyam-csarnok** | élő árfolyam-hologramok | `/exchangeboard place` a falakra |
| **Piac- / aukciós csarnok** | flavor a /market, /auction köré; a tanács Vásár-hete idején ez a "színpad" | nem kötelező kötés |
| **Vámház + Botera-negyed** | lore: feketepiac (a `feketepiac` bolt-NPC ide való!), csempészek | bolt-NPC: `/npcbind <npc> shop feketepiac` |
| **A Korszakok Könyve emlékmű** | szezonzáráskor ide kerül a bajnok bannerje + hologram-krónika | talapzat + `season-monument.location` |
| **Mester-céhházak** | a 13 kaszt mentor-questjei — enélkül a kaszt-láncok állnak | 13 mester-NPC (10. szakasz) |
| **Kovács-negyed** | kovács-questek (napi rotációban is!) | `/npc create kovacs_mester` |
| **Karaván-megálló / fogadó-udvar** | a vándorkereskedő fix megállója | `caravan.stops` koordináta |
| **Őrjárat-útvonalak** | járőröző városi őrség (éjjel gyorsabb tempó) — élet az utcákon | `city-guards.guards.<id>.route` |
| **Láda-terem** | crate-ládák (kulcsos nyitás) | `/crate set <láda-id>` a fizikai ládára |
| **Szentély-negyed** | kaszt-oltárok (buff-rituálék) | 6. szakasz (multiblock) |
| **Parkour-pálya (OPCIONÁLIS)** | szabadidős kihívás — a kaszt-fejlődés NEM függ tőle | `/parkour setstart ...` |
| **Hazatérés-kő (OPCIONÁLIS)** | `hazateres` rituálé (fővárosba teleport) — kihagyható, a komp/utak pótolják | LODESTONE mag-oltár |

**Zónázás (az építés után, admin):**
- városmag: `/territory create capital neutral caldestera Caldestera` — ettől él a
  bank-kapu, a törvény és a váltás-helyszín;
- Ryanora vidéke (tanyák, gázlók): `faction NEUTRAL` — ITT claimelhetnek a játékosok;
- műemlékek: `protected-city`.

### 1/b. Radicora — a régi főváros a Fa tövében (a spawn-település)

**Lore (építész-kánon):** a nagy háborúk után a város kettészakadt — a csalódottak
elhajóztak és felépítették az új Caldesterát; a Fa **igaz követői** a gyökereknél
maradtak. Az ő városuk **Radicora**, „a gyökerek városa" (a nép ajkán Ó-Caldestera).
**Régies, kisebb, szerényebb** — szándékosan üt el az új főváros pompájától.

**Méret:** kb. **80×80 - 100×100 blokk** falu-városka a Fa körül — DE: **ha a Fa
tövében sziget van, a sziget nyer.** A méret-szám csak irányadó; ha a terep (sziget,
folyópart) mást diktál, igazodj a terephez, és inkább legyen sűrűbb-hangulatosabb,
mint nagy.

**A FA MAGA IS ÉPÍTÉSI FELADAT — sőt, a világ jelképe!** A vanília/generált fa nem
elég: építsetek belőle **monumentális Világfát** — vastag, gyökérzetes törzs (a
gyökerek átfuthatnak a szigeten, akár a víz fölé is), dús, többszintes lombkorona,
kb. **40-80 blokk magas** (a ti ízlésetek szerint — a lényeg az "ősi, élő óriás"
érzés). Nyugodtan bontsátok el/építsétek át a mostani gyér fát. Lore-támpont: a Fa
a világ teremtő-őrzője, a gyökerei "mélyebbre nyúlnak, mint a Királynő álma" — a
tábortűz-mesék szerint az egyik gyökere a tábortüzek alatt fut. Anyag: szabad
(kéreg-textúrák, moha, lomb-rétegek, akár világító pontok a lombban — a Fa fénye
lore-elem).

**Miért kritikus, pedig kicsi?** Mert **itt landol minden új játékos** (first-join
spawn), és az első 10 perc élménye itt dől el. Az onboarding-lánc első lépése
("Beszélj a hírnökkel") CSAK akkor tud elindulni, ha a Hírnök elérhető közelségben
van — ezért VAGY a Hírnök áll Radicorában (és az új Caldesterában egy másik
quest-NPC), VAGY Radicorában egy "révkalauz" jellegű NPC irányít tovább. Javaslat:
**a Hírnök Radicorában álljon** — minden kezdő-quest oda köti, és a kezdő úgyis ott van.

**Ide való (tételesen, építész-szemmel):**
- **Fogadó-tér a first-join spawnhoz:** egy kis tér/tisztás a Fa tövében (mondjuk
  a törzs egyik oldalán), ahová az új játékos "megérkezik" — pár pad, lámpás,
  útjelző tábla-hangulat. A PONTOS pontot az adminok állítják be, miután kész —
  nektek csak a teret kell megépíteni, és szólni, melyik oldalra terveztétek.
- **A Hírnök helye** a fogadó-tér mellett: egy kikiáltó-pult/emelvény/hirdetőtábla.
  Az NPC-t az adminok rakják ki — ti a "színpadát" építitek.
- **Az erdei vének ligete:** a városka SZÉLÉN vagy kicsit kijjebb — jó ötlet a folyó
  túlpartja is, ha van kis gázló/palló hozzá (rövid séta legyen, 30-60 blokk, ne
  túra). Egy hangulatos tisztás kell: vén fák, kőkör vagy faragott rönk-ülőhelyek,
  moha. Az `erdei_venek` NPC-t ide rakják majd az adminok; a hozzá tartozó questek
  ("A Fa üzenete" a szezon közepén, napi gyógyfű-szüret stb.) már meg vannak írva —
  nektek CSAK a liget kell.
- **Fogadó + kikötő/gázló a Bokicnál** (a Bokic = a nagy folyó): kis móló vagy
  gázló-kövek, halász-hangulat.
- **Zónázás:** `protected-city` — azaz a kész Radicorát SENKI nem bonthatja.
  A környék kezdő-barát: éjjel is csak Lvl 0-1 (vanília-erejű) szörnyek járnak,
  és az épületekben azok sem tesznek kárt.

---

## 2. Pyralingrad — a Láng fővárosa (RED)

**Lore:** a Vérszavanna szíve, Soleil főnix-örökségének városa — tikkasztó, egzotikus,
agresszív építészet: lángoló tornyok, főnix-motívumok, lándzsás kapuk.

**Anyagok (építész-kánon):** a belvárosban **mangrove** — a „vérfa", a nemesség
jelképe (e fák egy csepp főnix-esszenciát hordoznak — ez már a kódexben is kánon);
a köznép negyede **akácia**; a falak **kalcit-diorit-nyír** keveréke. (Homokkő NINCS.)

**Méret:** az építész keze szabad — a lényeg a lenti elemek megléte.

**Kötelező elemek és MIÉRTJEIK:**
- **Trónterem** — a király-rendszer színtere: itt "történik" a koronázás, az
  adó-állítás, a raid-hirdetés (mind parancs, de kell a hely, ahol a politika él).
- **Frakció-spawn tér** (`/territory setspawn red`) — ide teleportál, aki a Lánghoz
  csatlakozik, és ágy nélkül itt éled újra. Enélkül a csatlakozás-teleport no-op.
- **Bank-fiók + váltó NPC** — a bank fővároshoz kötött: e nélkül a RED játékosnak
  Caldesteráig kell utaznia minden bank-ügyhöz!
- **Frakció-bolt** (shop-NPC) — a RED tematikus árukészlete (money sink).
- **Kassza-terem / kincstár** — flavor a /faction treasury + adó köré.
- **Városfalak + kapu** — raid-célpont hangulat (a raid-zónát az adminok jelölik ki).
- **Karaván-megálló** (ha a karaván ide is jár: `caravan.stops`).
- Ide illő kaszt-szentélyek: `harcos_szentely` (ANVIL), `demonvadasz_szentely`
  (CHISELED_NETHER_BRICKS), `saman_szentely` (LIGHTNING_ROD).
- Opcionális: hazatérés-kő, városi őrség útvonal, kazamata-lejárat.

**Zónázás:** `capital red` a mag + `faction RED` a környező birtokok (claimelhető) +
`protected-faction` a falakra.

---

## 3. Glatziendorf — a Fagy fővárosa (BLUE)

**Lore:** az örök tél városa a Jégmezőkön, Kallan sárkány-örökösei. **Fenyő, kő és
gyapjú** — fagyos, sötét, büszke: vastag falak, sárkány-motívumok, jégbe vágott tornyok.

**Elemek:** ugyanaz a lista (és ugyanazok a miértek), mint Pyralingradnál — trónterem,
frakció-spawn, bank-fiók + váltó, frakció-bolt, kincstár, falak, karaván-megálló —
BLUE-ra hangolva. Biom: havas síkság/hegység.
- Ide illő szentélyek: `ijasz_szentely` (FLETCHING_TABLE), `sarkanyidezo_szentely`
  (END_STONE_BRICKS), `pap_szentely` (SEA_LANTERN), `szerzetes_szentely` (BELL) —
  és a `sarkany_mester` NPC (sárkány-lore!).

**Zónázás:** `capital blue` + `faction BLUE` + `protected-faction`.

---

## 4. Thanaopolis — a Holtak Városa (DARK)

**Lore:** a régi világ Mortengradnak nevezte; ami a Káoszkor után a romjain áll, azt
a száműzöttek hívják **Thanaopolisnak** — düledező tornyok, benőtt sikátorok, kővázak.

**A plugin ezt ÉLŐVÉ teszi, és ezt építéskor tudni kell:**
- a DARK territóriumban magától spawnol egy **4-7. szintű élőhalott-lakosság**
  (max 24 fő, 30 mp-enként 4-esével pótlódik, 10 perc élettartammal) — ők a
  "polgárok": a DARK-tagokat és éjjel a Suttogókat NEM bántják, mindenki másra
  halálosak;
- ezen felül a DARK földön **minden** spawnoló mob +2 szintet kap, és az élőhalott
  **nappal sem ég el** — a város éjjel-nappal "lakott";
- ehhez a városnak **sötétnek kell lennie**: kevés fényforrás, árnyékos utcák —
  a túlvilágított rom nem tud spawnolni!

**Méret:** kb. **100×150 blokk romváros**. NE legyen szép: beomlott tetők, hiányos
falak, benőtt utcák, üres piacterek, mohás kő, sculk-erek.

**Kötelező elemek:**
- **Frakció-spawn** a romok közt (`/territory setspawn dark`) — ide száműzik a
  bűnösöket, és a DARK-ra váltók is ide érkeznek.
- **A Csontszámvevő kincstára** — a DARK bank/váltó NPC-je (lore: ő vereti a
  Csontveretet a lélekkövekből). Romos bank-épület; kötés: `bank` + `exchange`.
- **A Suttogók legszentebb oltára** — a romok MÉLYÉN, NEHEZEN megtalálhatóan:
  `eleftheria_konnye` oltár (CRYING_OBSIDIAN mag, csak DARK aktiválhatja) +
  körülötte **SCULK-padló**. A sculk kritikus: **Suttogóvá válni csak éjjel,
  egyedül, SCULK-blokkon állva lehet** — és több ilyen rejtett sculk-folt is
  legyen szétszórva a világban (nem csak itt!), hogy a rítus helye maga is rejtély
  legyen.
- **`feloldozas` oltár** (SOUL_LANTERN mag) — a vezeklés-lánc végi bűn-tisztító
  szentély; jó helye egy megtört kápolna a város szélén (a vezeklő "kifelé" tart).
- **Kripta-szint** — a `csontkripta` kazamata ide való (lásd 7. szakasz): a
  "Csontkripta Kulcsa" recept már él, a kaput ide kell megépíteni!
- Ide illő szentélyek: `halallovag_szentely` (CRYING_OBSIDIAN), `boszorkany_szentely`
  (RESPAWN_ANCHOR), `pakt_oltar` (SOUL_LANTERN), `bone_wing` (SOUL_SOIL).
- Hazatérés-kő romos változata — OPCIONÁLIS.

**Zónázás:** `capital dark thanaopolis` + a környék `faction DARK`. A `sotet_beavatas`
quest (a Nekromanta-spec kapuja) a DARK territóriumra ÉRKEZÉST figyeli — tehát a
zóna kijelölésével a quest magától életre kel.

---

## 5. Olethropyla — a Kárhozat Kapuja, a Senkiföldje (DOOM_GATE)

**Lore:** a Hetedik Vérháborút kirobbantó óriás Nether-portál; a régi térképeken
Olethropyla, a nép nyelvén a Kárhozat Kapuja. Itt nincs törvény: **a PvP legális,
az ölés nem bűn** — és mostantól ez **a világ egyetlen működő Nether-átjárója** is.

**Építés:**
- **Monumentális, TÖRÖTT portál-építmény** — 15-25 blokk magas obszidián kapu-keret,
  repedezve; körülötte korrupt táj (netherrack-erek, soul sand, halott fák,
  láva-hasadékok).
- **A keretben legyen egy SZABÁLYOS (max 23×23-as) belső nyílás is** — ez lesz a
  ténylegesen működő portál-rész, amit az adminok gyújtanak meg. A többi repedt
  díszlet marad. (A gyújtás admin-joggal megy — játékos sehol sem gyújthat portált.)
- Körülötte **aréna-jellegű romtáj** ~60-100 blokk sugárban: fedezékek, romok,
  csontok, törött ostromgépek, két sereg zászlói.
- **Amit a plugin ad rá:** bónusz-szintű mobok, belépési PvP-türelmi idő, robbanás-
  és tűzvédett terep — a zóna védett, tehát amit építetek, azt a játékosok nem
  bonthatják el.

**Zónázás:** `/territory circle doom-gate karhozat-kapuja <sugár>` — a zóna-id
KÖTELEZŐEN `karhozat-kapuja` (egy felfedező-quest név szerint erre hivatkozik);
frakció nélkül; a sugár fedje az egész arénát.

---

## 6. Rituálé-oltárok (multiblock szentélyek) — pontos építési recept

**Mi ez játékmenetileg?** A világban szétszórt kis szentélyek, amiket a játékos
SHIFT+jobb kattal aktivál: buffot, relikviát, bűn-tisztítást vagy haza-teleportot
adnak. A plugin a **pontos blokk-mintát** ellenőrzi — ha egy blokk nem stimmel,
az oltár "néma". Építés után MINDIG teszteljétek aktiválással!

Az alapminta (5×5 lábnyom, 3 magas):
- **Alapzat:** 5×5 padló a mag-blokk alatt (y-1) a megadott anyagból;
- **4 saroktorony:** a sarkokon 2 magas oszlop;
- **középen a MAG-BLOKK** (ez az "aktiváló gomb").
- A pontos lista oltáranként a `config/relics.yml` `rituals.<id>.structure` kulcsában.
- A `pakt_oltar` kisebb: 3×3 obszidián alap, közepén CRYING_OBSIDIAN, rajta SOUL_LANTERN.

**Oltár-lista (mag-blokk → hova való):**

| Rituálé | Mag-blokk | Javasolt hely |
|---|---|---|
| `eleftheria_konnye` (DARK relikvia) | CRYING_OBSIDIAN | Thanaopolis mélye (rejtett) |
| `phoenix_wing` | MAGMA_BLOCK | Vérszavanna / Pyralingrad környéke |
| `frost_wing` | BLUE_ICE | Jégmezők / Glatziendorf környéke |
| `wander_wind` | AMETHYST_BLOCK | vadon, magaslat |
| `bone_wing` | SOUL_SOIL | Thanaopolis / temető-rom |
| `feloldozas` (bűn-tisztítás) | SOUL_LANTERN | Thanaopolis széli kápolna |
| `atok_tores` | CRYING_OBSIDIAN | vadon, romhely |
| `hazateres` (haza-teleport) | LODESTONE | fővárosokba — OPCIONÁLIS |
| 13 kaszt-szentély (`*_szentely`) | lásd relics.yml | fővárosi szentély-negyedek + vadon |

A kaszt-szentélyeket oszd el tematikusan (druida → erdei liget, varázsló → akadémia,
sámán → viharvert csúcs…). Kifejezetten JÓ, ha némelyik a vadonban van és
felfedezésre vár — a felfedezés maga is tartalom.

---

## 7. Kazamaták (DUNGEON zónák)

**Mi ez játékmenetileg?** Kulcs-kapus, heti pecsétes dungeonök: a belépéshez
kulcs-tárgy kell, ami belépéskor ELFOGY; cserébe 2 órás futam-passz jár, majd
7 napos pecsét (addig nem mehet vissza ugyanoda a játékos). A benti mobok +5
szint-bónuszt kapnak és nappal sem égnek; a zóna védett (nem bontható).

**Két kazamata már "meg van hirdetve" a játékosoknak** (a kulcs-receptek élnek,
a guide írja őket) — ezért ez a kettő élvez prioritást:

| Zóna-id (KÖTÖTT!) | Név/téma | Hely |
|---|---|---|
| `melyseg` | **a Vasművek elhagyott tárnái** — ipari tárna-labirintus, rozsdás gépek, rúna-vésetek | vadon, a spawn-régió peremén |
| `csontkripta` | **Thanaopolis kriptamélye** — csont-folyosók, a Csontszámvevő "levéltára" | Thanaopolis alatt/mellett |

**Fontos technikai kötés:** a zóna-id és a kulcs összetartozik — a "Mélység Kulcsa"
a `dungeonkulcs_melyseg` pecsétet viseli, ezért a zónát PONTOSAN `melyseg` id-vel
kell létrehozni (`/territory create|circle dungeon <frakció> melyseg ...`), különben
a kulcs nem nyitja. Új kazamatához csak egy új kulcs-recept sor kell a configban —
szóljatok, és 1 perc alatt felvesszük (az építész-terv 10+ dungeonjéhez mind lehet
saját kulcs).

**Irányelvek:** 40-80 blokk belső hossz, folyosók + 2-3 terem + boss-terem; jól
látható bejárat-építmény; a boss admin-eszközzel telepíthető.

---

## 8. Esemény-infrastruktúra (fix helyszínek a világeseményekhez)

**Miért kellenek fix pontok?** Alapból a világesemények véletlen játékos mellé
spawnolnak. A spawnpont-rendszerrel HELYHEZ köthetők — pl. állandó világboss-aréna —,
amitől a világ kiszámíthatóbb és "helyei" lesznek a dolgoknak.

- **Világboss-aréna:** 1-2 állandó aréna a vadonban (~40-60 blokk átmérő, romok/
  fedezékek, középen 8 blokk szabad spawn-tér). Kijelölés: állj középre →
  `/events spawnpoint add world-boss [id]`, majd az anchors-mód átállítása
  (`points` = mindig itt; `mixed` = itt, ha van pont, különben random).
- **Kíséret-indulópont:** karaván-út menti állomás (őrtorony + út) —
  `/events spawnpoint add escort`. A konvoj innen indul és az út mentén halad.
- **Kereskedő-karaván megállók:** fogadó-udvarok a városokban — `caravan.stops`.
- **Kultista-helyszínek** (opcionális, hangulatos): baljós kőkörök, romos kápolnák —
  `/events spawnpoint add cultists`.
- **Komp-kikötők:** móló MINDKÉT parton + révész-NPC. Bekötés: a két végpont a
  configba (`ferry.routes.<id>` — a/b koordináta + viteldíj), a révész-NPC-re pedig
  `/npcbind <npc> command "komp <útvonal-id>"` — így a mólón kattintás = átkelés.
  A viteldíj a játékos bankjából ég el (money sink). A `revesz` nevű NPC-hez
  ráadásul quest-lánc is tartozik (ismerkedés → hal → móló-javítás)!
- **Utak!** A fővárosokat összekötő úthálózat a Bokic-gázlókkal — a kíséret, a
  kultista-hírvivő és a karaván is "úton járó" esemény, út nélkül a semmiben
  bolyonganak.

---

## 9. Titkos helyek (felfedező-tartalom)

**Mi ez játékmenetileg?** Admin-kijelölt rejtett pontok: aki ELSŐKÉNT ér oda,
broadcast + teljes jutalom; a későbbiek egyszeri fél-jutalmat kapnak. Olcsó, de
nagyon hatásos felfedező-tartalom — a térkép "fehér foltjait" tölti meg.

Szórjatok szét **8-15 mini-nevezetességet** (10-30 blokkos építmények):
elveszett kilátó, ledőlt óriás-szobor, remete-kunyhó, elhagyott halász-tanya a
Bokicnál, jégbarlangi szentély, homokba temetett karaván, Asterlayna
csillag-krátere, Káoszkor-emlékmű… A lore-ból érdemes nevet meríteni (Elveszett
Uralkodók, céhek, Vérháborúk). Bekötés: `hidden-spots.spots.<id>` (name +
location + radius + rewards) — az admin-csapat 1 perc alatt felveszi.

---

## 10. NPC-összesítő (kit kell kihelyezni és bekötni)

**Hogyan működik?** FancyNpcs-szel készül az NPC (`/npc create <belső-név>`), a
kinézete szabad — a plugin a BELSŐ NÉV alapján köti a funkciót. Amíg egy NPC nincs
kint, a hozzá tartozó questek várnak (nem hibáznak). A quest-NPC-k felett a
játékos SZÁMÁRA személyre szabott aura világít (arany = questet adna, zöld = aktív
quest szól hozzá) — kihelyezés után ezt látni is fogjátok.

| NPC belső név | Hely | Mit csinál |
|---|---|---|
| `hirnok` | **Radicora** főtér (javasolt!) | onboarding + útmutatás + MIND a 3 fejezet-zárás + capstone + napi hírvitel — a legfontosabb NPC |
| `harcos_mester` | Caldestera céhház | harcos mentor+mester lánc |
| `ijasz_mester` | Caldestera céhház | íjász lánc |
| `varazslo_mester` | Caldestera akadémia | varázsló lánc |
| `orgyilkos_mester` | Botera-negyed | orgyilkos lánc |
| `druida_mester` | erdei liget (Ryanora) | druida lánc |
| `paplovag_mester` | Caldestera szentély-negyed | paplovag lánc |
| `halallovag_mester` | Thanaopolis kapuja / kripta | halállovag lánc |
| `saman_mester` | viharvert magaslat v. Glatziendorf | sámán lánc |
| `szerzetes_mester` | hegyi kolostor v. Caldestera | szerzetes lánc |
| `pap_mester` | Caldestera szentély-negyed | pap lánc |
| `pakt_mester` | Botera-negyed mélye / rejtett zug | boszorkánymester lánc |
| `demonvadasz_mester` | a Kárhozat Kapuja pereme | démonvadász lánc |
| `sarkany_mester` | Glatziendorf | sárkányidéző lánc |
| `kovacs_mester` | Caldestera kovács-negyed | beszállító + napi rotációs questek |
| `erdei_venek` | erdei liget (Radicora közelében) | napi questek + "A Fa üzenete" szezon-közepi lánc |
| `revesz` | komp-kikötő mólója | révész quest-lánc + komp-kötés |
| `vandor_kereskedo` | (a karaván hozza magával) | quest-célpont |
| frakció-választók (3-4 db) | Caldestera követségek | `/npcbind <npc> faction <frakció>` |
| banker + váltó NPC-k | MINDEN főváros bankja | `bank` / `exchange` kötés |
| bolt-NPC-k (pl. `altalanos_bolt`, `kellekbolt`, `feketepiac`) | fővárosi boltok / Botera | `shop` kötés |
| Csontszámvevő | Thanaopolis kincstár | bank + exchange (DARK) |

---

## 11. Gyors méret-puska

| Építés | Méret (kb.) |
|---|---|
| Caldestera városmag | 200×200 + falak (kívül 20-30 blokk szabad sáv!) |
| Radicora | 80-100 oldalhosszú |
| Pyralingrad / Glatziendorf | az építész keze szabad (falak + elemek a lényeg) |
| Thanaopolis romváros | 100×150 — SÖTÉTEN tartva |
| Kárhozat Kapuja | 15-25 magas kapu (benne szabályos nyílás!), 60-100 sugarú zóna |
| Rituálé-oltár | 5×5×3 (pakt: 3×3) |
| Kazamata | 40-80 belső hossz |
| Titkos hely | 10-30 |
| Világboss-aréna | 40-60 átmérő |
| Komp-kikötő | móló + révész-bódé, pár blokk |

## 12. Admin-parancs puska (az építés utáni bekötéshez)

```
/territory pos|wand ... create <capital|faction|protected-faction|protected-city> <frakció> <id> [név]
/territory circle <típus> <frakció> <id> <sugár>      # kör-zóna (doom-gate: frakció nélkül)
/territory create|circle dungeon <frakció> <id> ...    # kazamata (id = a kulcs-signature vége!)
/territory setcapital <frakció> <id>                   # főváros-kijelölés
/territory setspawn <frakció>                          # frakció-spawn (a pontos pozíciód)
/npc create <név>  +  /npcbind <név> <quest|shop|bank|exchange|faction|command> ...
/parkour setstart|setfinish <pálya-id>                 # opcionális
/events spawnpoint add <world-boss|escort|caravan|cultists|any> [id]
/exchangeboard place
/crate set <láda-id>
/icesmp config set season-monument.location "world,x,y,z"
/icesmp config set world-events.intro.first-join-spawn "world,x,y,z"
# + config-fájlok: ferry.routes, caravan.stops, city-guards.guards, hidden-spots.spots
```

## 13. Javasolt sorrend + "minimálisan játszható világ" checklist

**Sorrend:** 1. Radicora (spawn + Hírnök!) → 2. Caldestera → 3. Thanaopolis →
4. a két harcos főváros → 5. Kárhozat Kapuja → 6. oltárok/kazamaták/titkos
helyek/arénák folyamatosan.

**Ez a minimum, amivel már ÉLES teszt indítható (MVP):**
- [ ] Radicora áll, first-join spawn beállítva, `hirnok` NPC kint és questet ad
- [ ] Caldestera városmag + `capital neutral` zóna (bank-kapu él) + banker/váltó NPC
- [ ] frakció-választó NPC-k kint (vagy ideiglenesen: /faction join paranccsal megy)
- [ ] mind a 4 frakció-spawn beállítva (a DARK-é Thanaopolis-helyszínen, akár rom-kezdeménnyel)
- [ ] a Kárhozat Kapuja zóna kijelölve (akár egyszerűbb első verzióban) + a portál meggyújtva
- [ ] komp-útvonal bekötve az új Caldestera felé (különben úszni kell!)
- [ ] 4-5 mester-NPC kint (a többi jöhet folyamatosan — a láncok várnak, nem törnek)

Minden más — oltárok, kazamaták, titkos helyek, arénák, őrjáratok, a többi mester —
**menet közben, élő szerveren is pótolható**, a rendszerek úgy vannak megírva, hogy
türelmesen várjanak rá.

## 14. GYIK — építész-kérdések, gyors válaszok

**"A méretre figyeljek, vagy a terepre?"** — A terep nyer. A méretek irányszámok;
sziget/folyópart/hegyoldal esetén igazodj a tájhoz.

**"Átépíthetem a generált dolgokat (pl. a Fát)?"** — Igen! A Fa kifejezetten
építési feladat (monumentális Világfa) — a generált/gyér verzió csak helyjelölő.
Ugyanez igaz bármely tereptárgyra a városaitok területén.

**"Mi az az NPC, és ki rakja ki?"** — Kattintható figura, amit az ADMIN-csapat
helyez ki és köt be, MIUTÁN megépítettétek a helyét. Ti soha nem raktok ki NPC-t —
ti a teret/pultot/céhházat építitek, ahol majd állni fog.

**"Ki rakja le a questeket?"** — Senki: a questek a pluginban élnek, már megírva.
Amint az NPC-jük kikerül a helyére, maguktól működnek.

**"A játékosok szét tudják verni, amit építek?"** — A zónázott területeken (minden
város, oltár-környék, kazamata, Kapu-aréna) NEM — se kézzel, se TNT-vel, se creeper.
A zónán kívüli vadon-dekorációt (utak, titkos helyek) elvileg érhetné kár, ezért a
fontosabb vadon-építményeket is zónázni fogjuk (`protected-city` típussal).

**"Mit tud egy Lvl 0-1 játékos / szörny?"** — A Lvl 0-1 szörny sima vanília-erejű.
A kezdő játékos vanília felszereléssel + 1-2 kaszt-képességgel játszik. A szint a
SZÖRNYEK ereje, nem a játékosé — a "kezdő-barát környék" azt jelenti, hogy ott éjjel
sem jön Lvl 5-ös vérfarkas-erejű zombi.

**"Honnan tudom, hova kerül pontosan a spawn?"** — Sehonnan — fordítva megy: ti
megépítitek a fogadó-teret, megmondjátok, melyik az, és az adminok arra a pontra
állítják a spawnt. Ugyanez igaz minden koordinátás dologra (megállók, arénák,
titkos helyek): előbb épül, aztán kötjük be.

**"Mi az a Bokic / Radicora / Thanaopolis / Olethropyla?"** — Lásd a Szótárat a
doksi elején; a teljes történet a lore-kódexben (`LORE.md`) van — érdemes átfutni,
sok építési ötlet van benne (vérfák, céhek, romvárosok, a Fa fénye…).
