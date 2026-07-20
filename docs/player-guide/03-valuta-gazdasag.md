# 3. Valuta és gazdaság 💰

A szerveren **igazi, élő gazdaság** van: a pénz értéke változik, lehet kereskedni, és vannak
helyek, ahol a pénz „eltűnik" (hogy ne legyen túl sok belőle). Ne aggódj, lépésről lépésre
elmagyarázzuk.

## A pénzed: tokenek és a bank

Minden frakciónak saját **token**-je (érméje) van: Piros, Kék, Semleges, Sötét. A pénzed
**kétféleképp** létezhet:

- **Fizikai itemként** — token a táskádban (papír-szerű tárgy).
- **Banki egyenlegként** — egy szám, ami nálad „be van fizetve".

Parancsok:
- `/bank balance` — megnézed a banki egyenlegeidet.
- `/bank deposit` — a nálad lévő tokeneket **bankba teszed** (**csak fővárosban**).
- `/bank withdraw <valuta> <összeg>` — a bankból **kiveszel** tokeneket (**csak fővárosban**).
- `/currency balance` — gyors egyenleg-nézet.
- `/currency pay <játékos> <összeg> [valuta]` — közvetlen utalás, de a KP-alapú gazdaságban
  **alapból ki van kapcsolva** (lásd lentebb, „A KP-alapú gazdaság szabályai").

## Dinamikus árfolyam (miért változik a pénz értéke?)

Képzeld el, hogy minél több van valamiből, annál kevesebbet ér — pont, mint a valóságban.

- Ha egy frakció **túl sok** pénzt „termel", az a token **leértékelődik** (kevesebbet ér a
  többihez képest).
- Ha valamiből **kevés** van, az **felértékelődik**.

Megnézheted és válthatsz:
- `/currency rates` — megmutatja az aktuális árfolyamokat (mennyi van belőle, mennyit ér, mi a
  váltási arány).
- `/currency exchange <összeg> <honnan> <hová>` — átváltod egyik valutát a másikra a mostani
  árfolyamon (kis díjjal). Aki figyeli a piacot, jól járhat!

> 🖱️ **Nem kell parancsot gépelned!** A `/menu` → **Bank & Pénz** → **Valutaváltó** gombbal egy
> kattintós váltó nyílik: fent kiválasztod a **forrás-**, lent a **cél-valutát**, középen látod az
> élő árfolyamot, és gyors gombokkal (16 / 32 / 64 / mind) rögtön válthatsz.

A fővárosokban **árfolyamtáblák** (lebegő hologramok) is mutatják az aktuális értékeket. 📊

## Piactér (kereskedés más játékosokkal) 🛒

- `/market sell <ár> [valuta]` — a **kezedben tartott tárgyat** kiteszed eladásra. (Alapból a
  saját frakciód valutájában; max. 5 tételed lehet egyszerre.)
- `/market` — megnyitja a **böngészőt**; kattints egy tételre a **megvásárláshoz** (a banki
  egyenlegedből fizet).
- `/market cancel` — visszaveszed a saját tételeidet (visszakapod a tárgyat).

**Aukció (licitálás):** ⚖️
- `/market auction <kikiáltási ár> [óra] [valuta] [buyout:<ár>]` — a kezedben tartott tárgyra
  **aukciót** indítasz (alapból 24 órás, legfeljebb 72). A `buyout:<ár>` opcionális: aki ennyit
  licitál, **azonnal megnyeri** az aukciót. (A buy-out nem lehet kisebb a kikiáltási árnál.)
- A böngészőben a licit **kattintás-típussal** állítható:
  - **bal-katt** → a minimum következő licit (az aktuális +10%),
  - **jobb-katt** → nagyobb ugrás (az aktuális +25%),
  - **shift-katt** → azonnali megvétel a buy-out áron (ha van megadva).
- A licited a **bankodból azonnal zárolódik**; ha valaki túllicitál, **automatikusan visszakapod**.
- Lejáratkor a nyertes viszi a tárgyat, az eladó a legmagasabb licitet (díj levonásával). Ha a
  nyertes (vagy licit nélküli aukciónál az eladó) épp nincs fenn, **belépéskor** vagy
  `/market claim`-mel veszi át a tárgyat.
- Élő licites aukció **nem vonható vissza**; licit nélkülit a `/market cancel` visszaad.
- **Relikvia nem listázható!** A relikviák több-lépcsős kihívással szerzett, egyedi tárgyak —
  piacra/aukcióra csak a **szilánkok és unique anyagok** kerülhetnek (`/market ereklye` szűrő).

**Eladási díj:** minden eladásból kb. **10% eltűnik** a gazdaságból — ez tartja kordában az
inflációt (a pénz „elértéktelenedését").

## Adomány-láda (közösségi ajándékozás) 🎁

Ez **nem piac** — nincs ár, nincs valuta, tiszta ajándékozás. Egy szerver-szintű, közös tár:
- `/adomany add` — a **kezedben tartott tárgyat** (a teljes stack-et) beteszed a közös ládába.
- `/adomany` — megnyitod a böngésző felületet; kattints egy tárgyra, és **ingyen elviszed**.
- A tárgy neve mellett a lorén látod, **ki** adományozta.
- A ládának van egy teljes kapacitása, és annak, hogy egy adományozónak hány **el nem vitt**
  tétele lehet egyszerre benne (mindkettő admin-konfigurálható).

**Reputáció-ár:** a vételár attól is függ, milyen viszonyban van a frakciód az eladóéval:
**ellenségtől drágább (+25%)**, **szövetségestől olcsóbb (−10%)**.

## Honnan jön a pénz? (jövedelem-források) 🪙

> 🏦 **Aranyszabály: a számládra pénz KIZÁRÓLAG a banki befizetésen át kerülhet!** Minden
> jutalom és talált pénz **fizikai veretben** (token-itemben) érkezik a kezedbe — ha a
> bankszámládon akarod tudni, vidd be a fővárosi bankba (`/bank deposit`).

- **Kopott erszény (mob-drop):** az ellenséges szörnyek legyőzésekor eséllyel egy **erszény**
  esik — fizikai tárgy, benne **véletlen frakció-valutával** (az összeg a szörny szintjével nő).
  **Jobb-katt** az erszénnyel, és a veretek a kezedbe hullanak. Spawner-szörny sosem dob!
- **Horgász-szerencse:** horgászat közben kis eséllyel egy **iszapba veszett erszény** is a
  horogra akad — ugyanúgy jobb-kattal nyitod ki. (AFK-horgásznak nem jár.)
- **Felvásárló NPC:** a fővárosi **Felvásárlónál** a kézben tartott nyersanyagot (termény, hal,
  érc, bőr…) **fix áron eladhatod** — **veretben fizet, egyenesen a kezedbe**. Ez a biztos
  alapjövedelem, de **napi kerete** van, és egyedi/különleges tárgyat nem vesz meg. A jobb
  árat mindig a játékos-piac adja!
- **Küldetések és napi feladatok:** a questek, napi kihívások, közösségi célok és
  mérföldkövek jutalma is **veretben** érkezik a kezedbe.
- **Lélekkő:** a magas szintű szörnyek Sötét tokent ejtenek (részletek lentebb).
- **Vérdíj és parkour:** a fejvadász-rendszer és a parkour-próbák szintén veretben fizetnek.
- **Piac és aukció:** amit megtermelsz/kicraftolsz, a piacon másik játékosnak adhatod el —
  a piaci bevétel a **bankszámládra** érkezik (a piac a bankon keresztül köt üzletet).

## Hová „tűnik" a pénz? (money sinkek)

Hogy a pénz értékes maradjon, több helyen is „elszívódik":

- **Állampolgári adó:** óránként a frakciótagok a saját valuta-egyenlegük **2%-át**, de
  **legalább 2 érme fejadót** befizetnek a frakciókasszába (a Semlegesek mentesek). Az üresen
  tartott számla sem kibúvó: amit a számla nem fedez, **hátralékként** gyűlik (legfeljebb 50
  érméig), és a következő beszedésekkor automatikusan levonódik. Aki tartósan a plafonon ülő
  hátralékkal, fizetés nélkül „csal", azt a **Számvevők feljelentik** — **bűnt** kap, és a
  bűnök súlya a Kitaszítottak közé taszíthatja.
- **Kereslet-sokk** (időnként): egy véletlen valuta értéke átmenetileg **megugrik** (x1,2–1,6) —
  ezt egy üzenet jelzi mindenkinek. Jó alkalom kereskedni!
- **Piaci pánik** (a sokk tükörpárja): ritkábban egy valuta értéke átmenetileg **lezuhan**
  (x0,6–0,8) — aki ilyenkor mer vásárolni, a normalizálódáskor nyerhet rajta. A piac
  kétirányú: nemcsak felfelé mozog!
- **Konjunktúra** (rövid fellendülés): időnként egy valutában **fél órára feleannyi a piaci
  eladási díj** (10% helyett 5%) — üzenet jelzi, mikor éri meg igazán adni-venni.
- **Szezonzáró tőzsdeláz:** a szezon utolsó hetében (Végítélet-hét) a sokkok **sűrűbbek,
  hevesebbek és rövidebbek** — aki figyeli a piacot, nagyot nyerhet (vagy veszíthet).
- **Eladási díj, raid-nevezés, rituálé-alapanyagok** — ezek is mind „elnyelnek" pénzt.
- **Vendor-only szakma-kellékek:** a **Szakmai Kellékbolt** (és más boltok) több tucat,
  **kizárólag boltban kapható** kelléket árulnak (Kősó, Írnok-tinta, Edzőolaj, Sózott csali,
  Számvevő-pecsétviasz, Lámpaolaj, Kovács-folyósítószer…) — szakmánként legalább 5-féle.
  A magasabb szintű receptek hozzávalóként kérik őket, így a szakma-progresszió folyamatosan
  pénzt „éget” (money sink). A boltok kínálata teljesen config-vezérelt: az admin szabja meg,
  melyik NPC mit áruljon.
- **Frakció-boltok:** a fővárosokban álló **bolt-NPC-kre jobb-kattintva** egy vásárló felület
  nyílik — fix áron vehetsz alapanyagot/fogyóeszközt a banki egyenlegedből. A kifizetett pénz
  **eltűnik** a gazdaságból (money sink). Egyes boltok csak a saját frakciód tagjainak árulnak.
- **Kereskedő-karaván:** időnként egy **vándorkereskedő** bukkan fel a világban (egy üzenet jelzi,
  merre) — csak **korlátozott ideig** marad. Amíg itt van, **jobb-kattints a karaván-NPC-re**, és
  ritka portékákat vehetsz a banki egyenlegedből — köztük **ritka szakma-alapanyagokat**
  (Emlékszilánk, Sárkánycsont-szilánk, Főnixpihe, Néma Kristály…), amikhez máshol alig jutsz hozzá.
  A kínálat **érkezésenként rotálódik**: a karaván a teljes áru-listájából mindig csak néhány
  tételt hoz („ma épp ezt”), úgyhogy minden látogatás más! A kifizetett pénz szintén **eltűnik**
  (money sink). Ha lekésed, legközelebb máshol tűnik fel — érdemes odasietni!

## Lélekkő — a veszélyes vidékek jutalma

A spawntól messze a szörnyek erősebbek. A **magas szintű (3+) szörnyek** eséllyel **Sötét
tokent** (lélekkövet) ejtenek — így a távoli, veszélyes helyeken kalandozni **gazdaságilag is
megéri**.

⚠️ **Kivétel — „a Királynő nem fizet a testvérgyilkosságért":** a **Sötét (Kitaszított)**
játékosnak az **élőhalott** mobokból (zombi, csontváz, phantom, zoglin, wither) **nem esik
lélekkő** — azok úgysem védekeznek ellene (frakció-passzív), így a kockázat nélküli farmolás
nem terem pénzt. Az ÉLŐ szörnyek (creeper, pók, witch, Nether-mobok…) a Sötéteknek is fizetnek.

---

➡️ Tovább: [Kasztok](04-kasztok.md) • [Vissza a tartalomhoz](README.md)

## A KP-alapú gazdaság szabályai 🏦

- A szerveren a **készpénz (token item)** az alap: a **player–player kereskedelem kézből
  kézbe** zajlik (átadod a tokent / az itemet), vagy a **piacon**.
- **Banki ügyintézés** (befizetés, kivét, **valutaváltás**) **csak a fővárosokban** lehetséges —
  keresd fel valamelyik város bankját.
- **Közvetlen utalás (`/currency pay`) nincs** — a bankszámla a player–szerver ügyletekhez van
  (boltok, piac, claimek ára onnan megy).
