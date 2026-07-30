# IceSMP játékoskézikönyv

<!-- icesmp-doc-id: guide.player -->

> Dokumentált release: `4643ab53586f0c1ee7352df16dcd477013e6fad4`
>
> A szerver beállításai módosíthatják a számokat; a játékbeli menük és üzenetek
> mindig az éppen betöltött konfigurációt mutatják.

Ez az egyetlen kanonikus játékosútmutató. A legegyszerűbb belépési pont a
`/menu`: a legtöbb rendszer kattintással is elérhető. A teljes rendszer- és
rollout-státusz a [funkciókatalógusban](FEATURES.md), a legújabb eltérések a
[változáslistában](LATEST_CHANGES.md) olvashatók.

## Tartalom

1. [Kezdő lépések](#1-kezdő-lépések)
2. [Frakciók](#2-frakciók)
3. [Valuta és gazdaság](#3-valuta-és-gazdaság)
4. [Kasztok](#4-kasztok)
5. [Képességek](#5-képességek-varázslatok)
6. [Specializációk](#6-specializációk)
7. [Talentek](#7-talentek-talent-fa)
8. [Szakmák](#8-szakmák)
9. [Relikviák és rituálék](#9-relikviák-és-rituálék)
10. [Világesemények](#10-világesemények)
11. [Királyság, raid és háború](#11-királyság-raid-és-háború)
12. [Küldetések](#12-küldetések)
13. [Területek és claim](#13-frakcióterületek-és-saját-birtok)
14. [Játékosparancsok](#14-parancsok-listája)
15. [Party](#15-party-csapat)
16. [Kazamaták](#16-kazamaták)

---

## 1. Kezdő lépések 🚀

Most léptél be először? Semmi gond — kövesd ezt a pár lépést, és máris benne vagy a játékban.

> **Nem kell semmit előre tudnod.** Se a történetet, se a rendszereket nem kell elolvasnod
> ahhoz, hogy játssz: az első belépéskor egy kezdő küldetés-lánc mindent megtanít, a világ
> története pedig játék közben, apránként tárul fel — csak annak, aki kíváncsi rá. Ez a
> kézikönyv is csak segédlet: akkor lapozd fel, ha elakadtál vagy mélyebbre ásnál.

### Az első 10 perced

1. **Válassz frakciót.** Írd be: `/faction join <frakció>` — a frakciók: `red` (Piros),
   `blue` (Kék), `neutral` (Semleges). Ez dönti el a **pénznemedet** és egy **passzív bónuszt**
   (pl. a Pirosat nem égeti a tűz). Részletek: [Frakciók](#2-frakciók).
   > A `dark` (Sötét) frakcióba **nem lehet csak úgy** belépni — oda csak „bűnös" játékos kerül.
2. **Nyisd meg a karakterlapod:** `/profile`. Ez a **központi menü**. A fejen látod a fontos
   adataidat, és gombokról eléred az összes almenüt: **Kaszt, Specializáció, Szakma, Talentek,
   Képesség-fa**.
3. **Válassz kasztot** a Kaszt menüből — **13 kaszt** közül (Varázsló, Harcos, Íjász, Orgyilkos,
   Druida, Paplovag, Halállovag, Sámán, Szerzetes, Pap, Boszorkánymester, Démonvadász,
   Sárkányidéző), majd **igényeld a Lélekkapocsodat** (egy gomb ugyanott). Ezzel
   használod a varázslataidat. (Minden kasztnak saját **Erő-csíkja** is van a HUD-on — a legtöbb
   spell ezt fogyasztja, és idővel visszatöltődik; a vér/rituálé/fizikai spellek HP/XP/éhséget
   kérnek. Lásd [Kasztok](#4-kasztok).)
4. **Tanulj szakmát** a Szakma menüből: **1 gyűjtögetőt** (pl. Bányász) és **1 készítőt**
   (pl. Kovács). A Halász és Szakács alapból a tiéd.
5. **Kezdj el játszani!**
   - **Szörnyeket ölve** kapsz **kaszt-XP-t** (így szintezel és nyitsz új képességeket).
   - Harcban minden találatod fölött **lebegő sebzés-szám** jelzi, mennyit ütöttél; ha meghalsz,
     a chatben **halál-összegző** mutatja, mi vitt el (utolsó 10 mp találatai + összesített sebzés).
   - **Bányászva / aratva / horgászva / főzve** kapsz **szakma-XP-t**.
   *(Az első belépésedkor automatikusan elindul egy rövid **kezdő küldetés-lánc** — „Beszélj a
   hírnökkel" → „Első csata" → „Első gyűjtögetés" —, ami végigvezet a kezdésen, és minden
   állomásért jutalmat ad. Lásd: [Küldetések](#12-küldetések).)*
6. **Vedd fel a kaszt-próbádat:** `/quest list`, majd `/quest accept <id>`. Ezzel jutalmat
   szerezhetsz. Részletek: [Küldetések](#12-küldetések).

Első belépéskor egy rövid **bemutató cím-szekvencia** is lejátszódik — ez csak egyszer fut le.

### Mit hol találok?

| Amit szeretnél | Hová menj |
|---|---|
| **Mindent egy helyen, kattintással** | **`/menu`** |
| Megnézni az adataimat | `/profile` |
| Képességet használni | tartsd kézben a Lélekkapcsot → jobb katt |
| Pénzt nézni / adni | `/bank balance` — pénzt adni a fizikai veret kézből átadásával lehet |
| Eladni valamit | `/market sell <ár>` |
| Küldetést felvenni | `/quest list` |
| Csapatot alakítani a barátaiddal | `/party invite <név>` — lásd [Party](#15-party-csapat) |
| Megvédeni a házam (saját birtok) | állj a chunkba és `/claim` — lásd [Területek](#13-frakcióterületek-és-saját-birtok) |

> A teljes parancslista: [Parancsok](#14-parancsok-listája).

### 🖥️ Képernyő: HUD, tablista, sebzés-számok

A szerver **saját, natív** felületet ad (nem kell hozzá külső mod vagy resource-trükk):

- **Dinamikus HUD (oldalsáv):** harc közben a HUD **a harchoz fontos sorokra** vált (erőforrás,
  csapat), és egy **forgó infósor** váltogatja a kevésbé sürgős adatokat — így a korlátozott hely
  mindig a leghasznosabbat mutatja. Célzáskor egy **célpont-sor** is megjelenhet az ellenfél
  életével.
- **Natív tablista:** saját **fejléc/lábléc** (animációkkal), a nevek a rangod szerint rendezve,
  és **relációs háború-színek** — raid alatt az ellenséges frakció neve **pirosan** villan a
  tablistádban és a fejük fölött. A **ping** is színkódolt.
- **Lebegő sebzés-számok:** ütéskor a sebzés száma felugrik a célpont felett (alapból **csak te
  látod, amit te okozol** — configból állítható/kikapcsolható).
- **Haladás-fül (natív advancementek):** a vanília **Haladás** képernyőn (alapból **L**) van egy
  saját **IceSMP** fül — külső mod és resource pack nélkül. A mérföldköveid itt gyűlnek: első
  kaszt, specializáció, frakció-csatlakozás, első szakma, és **rejtett** teljesítmények is (pl.
  egy rontás-góc megtisztítása, egy titkos hely felfedezése). A bejegyzések **csendben**
  érkeznek — nincs felugró értesítés és nincs chat-hirdetés, mert néhány közülük olyan titok,
  amit nem szeretnél a szomszédod orrára kötni. Amit teljesítettél, arról a rendszer amúgy is
  szól neked a chaten; a fülre azért érdemes benézni, hogy meglásd, **mi van még hátra**.

---

---

## 2. Frakciók ✅

A **frakció** az „oldalad" a világban. Négy van, és mindegyiknek saját **pénzneme** és **passzív
bónusza** (egy állandó képesség, amiért nem kell semmit csinálnod) van.

Belépés: `/faction join <red|blue|neutral|dark>` • Kilépés: `/faction leave`

> 🏷️ **Kanonikus nevek:** a négy frakció a lore szerint **Láng** (*Perinfernicitas*, `red`),
> **Fagy** (*Cryghaliris*, `blue`), **Menedék** (*Ryanora & Caldestera*, `neutral`) és
> **Kitaszított** (*A Kitaszítottak*, `dark`). A parancs-azonosítók változatlanok.

Az **első csatlakozásod** teljesen **ingyenes és időzítetlen**, és mindenki a **Menedékben (Semleges) kezd** — új játékosként a **Menedék spawnján** jelensz meg. Amikor királyságot
választasz, a plugin **odateleportál az új királyságod spawnjára**, és ha nincs ágyad /
respawn-horgonyod, halál után is a **saját királyságod spawnján** éledsz újra — így mindig
tudod, hol a fővárosod.

A `/faction leave` kilépés **Semlegesbe helyez** (nem „frakció nélküli" állapotba): a következő
belépésre így is a semleges-főváros kapu, a szezon-hajrá zára és a váltás-cooldown vonatkozik —
a leave+join páros nem kerülőút.

A **Menedékből bárhová ingyen** válthatsz, és a **Kitaszítottak közé lépés is mindig ingyenes**
(annak a bűnös-feltétel + az örök paktum az ára). Minden más frakcióváltás (Láng↔Fagy,
illetve vissza a Menedékbe — a `/faction leave` kilépés is ugyanígy fizetős!) a **jelenlegi
frakciód valutájában** kerül **alapból 500-ba**, és utána **72 óráig** nem válthatsz megint
(`factions.switch.cost` / `factions.switch.cooldown-hours` a configban) — ez a
frakció-hopping ellen véd. Ezen felül **szezononként legfeljebb 2× válthatsz** (az első,
ingyenes választás nem számít bele, de a Menedékből való ingyen váltás és a Sötétbe lépés
igen), és **a szezon utolsó hetében egyáltalán nincs váltás** — a liga hajráját azzal a
zászlóval fejezed be, amelyik alatt elkezdted (`factions.switch.max-per-season` /
`factions.switch.lockout-final-days`). **Váltani csak a Menedék fővárosában, Caldesterában állva lehet**
(ott, ahol a királyság-választó hírnök NPC is áll) — így a döntés mindig a semleges földön,
„hivatalosan" születik meg.

A passzívok **egy szintre vannak hangolva** — mindegyik kb. egyformán hasznos, csak más
helyzetben erős, így a választás ízlés (playstyle) kérdése, nem „melyik a legjobb":

| Frakció | Passzív bónusz | Mire jó |
|---|---|---|
| 🔴 **Láng** (Perinfernicitas) | Immunis a **tűz / láva / forró blokk** sebzésére | Hő-mesterség: a Nether és a láva veszélytelen |
| 🔵 **Fagy** (Cryghaliris) | Immunis a **fagyásra ÉS a fulladásra**; **50% eséllyel** nem veszít éhséget | Víz-mesterség: végtelen búvárkodás, hideg biómok, víz alatti építés/aknázás |
| ⚪ **Menedék** (Ryanora & Caldestera) | **Nincs zuhanás-sebzés** (esésimmunitás); a **nem-ellenséges mobok és az endermanök** nem támadják; **adómentes** | Biztos léptű vándor: magasból is leugorhatsz, az endermanre ránézhetsz, és nincs állampolgári adó |
| ⚫ **Kitaszított** (A Kitaszítottak) | Immunis a **wither-sebzésre**; az **élőhalottak (zombi, csontváz, phantom, zoglin) nem támadják** | A **legerősebb PvE-passzív**: éjszaka és barlang szinte veszélytelen — cserébe az **örök bűnös-jelölés** |

#### 🍲 A frakciók konyhája (K6)

A kódex szerint minden népnek megvan a maga konyhája — és a frakció-aura ezt meg is követeli:

- **Fagy (BLUE):** halon él — ha ~12 órán át nem eszel halat (bármely hal, vagy a séf
  **Fagyasztott Tavi Pisztrángja**), enyhe honvágy tör rád (rövid Éhség + emlékeztető).
  A Pisztráng rövid **felszívódás-pajzsot** is ad.
- **Láng (RED):** tojás-ételen él — a **Fűszeres Főnixtojás-Rántotta** (vagy tökpite/torta)
  nullázza a honvágyat; a Rántotta rövid **tűz-ellenállást** is ad.
- **Ünnepi ételek — minden frakciónak a magáé** (a séf-recept frakció-kapus):
  a Fagy **Sárkány-pörköltje** (Erő), a Láng **Vérszavannai Vadlakomája** (Gyorsaság +
  tűz-oltalom), a Menedék **Vándorünnep Lepénye** (Szerencse + Gyorsaság) és a
  Kitaszítottak **Hamvak Lakomája** (Felszívódás + Éjjellátás). Ahol van honvágy-
  kötelezettség, az ünnepi étel azt is teljesíti.
- **Menedék:** a **Tiltott Kakaóbabos Sütemény** (a cukrászok Asterlayna Gyümölcsének hívják)
  fogyasztva **„robban"** — feldob, felgyorsít, csillagszóró-effekttel. A Bankárszövetség
  hivatalosan tiltja. Hivatalosan.
- **Kitaszítottak (DARK):** a **Mortengradi Hamukenyér** a kenyerük (rövid éjjellátást ad) —
  de honvágy-kötelezettségük **nincs**: a Kitaszítottaknak nincs otthonuk, amire honvágyuk
  lehetne.

A honvágy **puha** mechanika (config: `factions.food-duty`), új játékost és frissen váltót
türelmi idő véd.

> 💬 A **chatben a neved a frakciód színében** jelenik meg (a rang-prefixszel együtt) — így
> mindenki azonnal látja, ki melyik oldalon áll.

### ⚫ A Kitaszítottak — a „Sötét" (dark) frakció

A Kitaszítottak frakciója nem egy „sima választás" — ez a **börtön a világ végén**, a **Kitaszítottak**
száműzetése, a Néma Királynő árnyéka. **Kétféleképpen** kerülsz ide:

- **Bűnözőként:** akit **4 bűnnél (sinner)** a világ automatikusan ide **száműz** (lásd lentebb).
- **Lelepleződött Suttogóként:** akit rajtakapnak, hogy titokban **a Suttogók** hálózatát szolgálja
  (lásd lentebb), azt egy csapásra ide veti a bélyeg.

A belépés **kétlépcsős**: az első `/faction join dark` csak figyelmeztet a paktum súlyára —
ha komolyan gondolod, egy percen belül **meg kell ismételned**. Belépéskor aztán megköttetik
a **sötét paktum**: a bűnös jelölés **soha nem törlődik le**, és amíg a paktum áll,
a frakciót **el sem hagyhatod** — se pénzért, se `/faction leave`-vel. Az **egyetlen**
kiút a **vezeklés-küldetéslánc** (lásd [Küldetések](#12-küldetések)): az oldja a
paktumot, és utána léphetsz tovább. Cserébe tiéd a **legerősebb PvE-passzív**: az élőhalottak békén
hagynak, s a wither sem árt — a Néma Királynő megjelöli, de meg is óvja a szolgáit.

A Kitaszítottaknak is van otthonuk: **Thanaopolis, a Holtak Városa** — egy élőhalottak lakta **rom-főváros**,
ahová a száműzöttek újraélednek. Mivel rátok nem támadnak a holtak, ez a düledező város nektek menedék;
mindenki másnak halálos csapda. *(A romváros megépítése a szerver-csapatra vár.)*

### ⚖ A Vének Tanácsa — a Menedék vezetése

A Menedéknek **nincs királya** (az Armageddon-ultimátum tiltja a fegyvert) — helyette
**hetente választott, 3 fős Vének Tanácsa** vezeti:

- **Szavazás:** `/tanacs szavaz <játékos>` — hetente egy szavazatod van (átszavazhatod);
  a hét fordulóján a választás újraindul. Állás: `/tanacs`.
- **A tanács jogai:** kassza-kivét (saját, kisebb napi kerettel), karaván-indítás, és a
  békés zászlóshajó: a **Vásár-hét** (`/tanacs vasarhet`) — a tanács kihirdetheti, hogy a
  **Creutzér piaci díja átmenetileg kedvezményes** legyen (pörög a kereskedelem!).
- **Amit a tanács NEM tehet:** raidet nem hirdethet — a Menedék fegyvertelen marad.

### Hogyan leszek bűnös (sinner)?

- **Gyilkosság:** ha **megölsz egy másik játékost**, **+1 bűnt** kapsz. Kivétel a
  **Kitaszított (DARK) áldozat**: ő a törvényen kívül áll — az ölése **sosem bűn**
  (sőt, ha vérdíj van a fején, még fizetnek is érte).
- **Árulás:** ha a **saját frakciótársadat** ölöd meg, az súlyosabb — **+2 bűn**. (A Menedék népe
  laza közösség: köztük az ölés sima gyilkosságnak számít.)
- **Lopás:** ha egy **másik frakció területén** álló konténerből (láda, hordó, kemence,
  hopper…) tárgyat veszel ki, **+1 bűnt** kapsz. Egy fosztogatás-sorozat területenként
  egyszer számít (nem minden kattintás külön bűn).
- **4 bűnnél** automatikusan **száműznek a Kitaszítottak közé** (örök paktummal).
- **Kivétel:** **raid** (frakcióháború) alatt a **jelentkezett harcosok** közti **ölés és az
  ellenség földjén való zsákmányolás nem számít bűnnek** — aki nem jelentkezett
  (`/faction raid join`), arra raid alatt is a békeidős szabályok élnek. Lásd
  [Raid és háború](#11-királyság-raid-és-háború).
- **Kivétel — hadi-ablak:** hétvégente (alapból szombat-vasárnap 18-20 óra) megnyílik a
  **hadi-ablak**: ez alatt a **Láng↔Fagy ölés nem bűn és nem vérdíj-eset** — szentesített
  hadicselekmény, amely **liga-pontot ér** a frakciódnak (napi plafonnal, hogy ne legyen
  farmolható). Állás és időpont: `/faction war`. A nyitást és zárást broadcast jelzi.

> A bűnösöket egy különleges relikvia, a **Mételytépő** is megjelölheti és megbüntetheti —
> erről a [Relikviák](#9-relikviák-és-rituálék) oldalon olvashatsz.

### Fejvadászat (körözés) 💰

Aki elér egy bizonyos bűnszámot (alapból **3 bűn**), az **körözötté** válik — a fejére
**fejpénz** kerül (a bűnök száma × egy fix összeg, alapból Creutzérben). A `/bounty`
paranccsal megnézheted a körözési listát: ki körözött és mennyit ér a feje.

Ha **megölsz egy körözött bűnözőt**:
- **megkapod a fejpénzt** (veretben, a kezedbe — ugyanarra a fejre fél naponta csak egyszer fizet a Bankárszövetség);
- **nem kapsz érte bűnt** — ez igazságos kivégzés, nem gyilkosság;
- a bűnöző **bűnszámlálója nullázódik** (a bűnös-jelölése viszont megmarad).

Így a bűnözés kockázatos: minél többet vétkezel, annál nagyobb célpont vagy a fejvadászoknak.

### 🌒 A Suttogók — a rejtett hálózat

A **Suttogók** (Az Éjszaka Gyermekei) nem külön frakció, hanem egy **titkos státusz**, amely a
**látható frakciód fölé** rétegződik. Láng, Fagy vagy Menedék maradsz mindenki szemében — miközben
titkon a Néma Királynő ügyét szolgálod, és **sötét-mágiájú tárgyakhoz** jutsz. A hovatartozásod
kívülről **láthatatlan**: sötét mágia rejti.

**Hogyan csatlakozol?** Egy **titkos, éjszakai rítussal** — magányosan (más játékos nélkül a
közeledben), egy elrejtett **Suttogó-oltárnál**, ahol felajánlod a hűségedet és egy áldozatot. A
rítus helye és módja maga is rejtély: lore-nyomokból, egy titkos hírnöktől vagy egy ritka „meghívóból"
derül ki.

**Hogyan lepleződsz le?** A sötét erő nem tűri a napvilágot:
- ha **közterületen, mások szeme láttára használsz sötét mágiát** (vagy Suttogó-tárgyat);
- ha **rajtakapnak** egy rítuson vagy egy **áruláson** (frakciótárs hátbaszúrása);
- ha a **gyanú** ellened gyűlik, míg át nem üt a bélyeg.

**A csatorna.** A hálózatnak saját, kívülről láthatatlan beszélgetése van
(`/suttogas <üzenet>`): ezt a rejtett Suttogók **és a Kitaszítottak** is hallják — ők a Néma
Királynő *nyílt* népe, a Suttogók pedig ugyanannak a hálózatnak a rejtett fele. Aki nem tartozik
bele, annak a parancs semmit nem árul el. **Vigyázz:** a csatorna kiírja a feladó nevét, tehát a
Kitaszítottak megtudják, ki Suttogó a látható frakciókban — a titkod annyira biztonságos, amennyire
a Királynő népe hallgatni tud.

**Mi jár érte?** A Királynő gondoskodik a híveiről:
- ha a kósza kultisták ügye **beteljesül** (egy rítus lefut vagy egy hírvivő célba ér), minden
  felesküdött Suttogó **gyanúja csillapodik**, és **titkos tárgy-részesedést** kap a hálózattól;
- **éjszaka az élőhalottak békén hagyják** a felesküdötteket — a Királynő óvó keze. Vigyázat:
  a figyelmes szemtanúnak ez **árulkodó jel** lehet…

**Mi történik ekkor?** A titkos Suttogóból egy csapásra **Kitaszított** lesz: **bűnössé** válsz, a
világ **száműz a Kitaszítottak közé**, és **azonnali vérdíj** kerül a fejedre — a többiek vadászni
kezdenek rád. A leplet nem lehet visszaölteni; innen csak a **vezeklés** vezet vissza.

### Frakció-viszonyok (reputáció)

A frakciók **barátok vagy ellenségek** lehetnek egymással (a szerver állítja be, és raid alatt
a hadakozók automatikusan ellenségek). Ez a **piaci árakat** is befolyásolja:

- **Ellenséges** frakció tagjától vásárolni **drágább** (+25% felár).
- **Szövetséges** frakciótól **olcsóbb** (−10%).

Részletek: [Valuta és gazdaság](#3-valuta-és-gazdaság).

---

---

## 3. Valuta és gazdaság 💰

A szerveren **igazi, élő gazdaság** van: a pénz értéke változik, lehet kereskedni, és vannak
helyek, ahol a pénz „eltűnik" (hogy ne legyen túl sok belőle). Ne aggódj, lépésről lépésre
elmagyarázzuk.

### A pénzed: tokenek és a bank

Minden frakciónak saját **verete** (érméje) van, és mindegyiknek saját neve is:

| Frakció | A valuta neve |
|---|---|
| 🔥 Perinfernicitas (Piros) | **Parázsló Parals** |
| ❄️ Cryghaliris (Kék) | **Hópihér-veret** |
| ⚖️ Ryanora & Caldestera (Semleges) | **Creutzér** |
| 💀 A Kitaszítottak (Sötét) | **Csontveret** |

A pénzed **kétféleképp** létezhet:

- **Fizikai itemként** — token a táskádban (papír-szerű tárgy).
- **Banki egyenlegként** — egy szám, ami nálad „be van fizetve".

Parancsok:
- `/bank balance` — megnézed a banki egyenlegeidet.
- `/bank deposit` — a nálad lévő tokeneket **bankba teszed** (**csak fővárosban**).
- `/bank withdraw <valuta> <összeg>` — a bankból **kiveszel** tokeneket (**csak fővárosban**).
- `/currency balance` — gyors egyenleg-nézet.
- `/currency pay <játékos> <összeg> [valuta]` — közvetlen utalás, de a KP-alapú gazdaságban
  **alapból ki van kapcsolva** (lásd lentebb, „A KP-alapú gazdaság szabályai").

### Dinamikus árfolyam (miért változik a pénz értéke?)

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

### Piactér (kereskedés más játékosokkal) 🛒

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

### Adomány-láda (közösségi ajándékozás) 🎁

Ez **nem piac** — nincs ár, nincs valuta, tiszta ajándékozás. Egy szerver-szintű, közös tár:
- `/adomany add` — a **kezedben tartott tárgyat** (a teljes stack-et) beteszed a közös ládába.
- `/adomany` — megnyitod a böngésző felületet; kattints egy tárgyra, és **ingyen elviszed**.
- A tárgy neve mellett a lorén látod, **ki** adományozta.
- A ládának van egy teljes kapacitása, és annak, hogy egy adományozónak hány **el nem vitt**
  tétele lehet egyszerre benne (mindkettő admin-konfigurálható).

**Reputáció-ár:** a vételár attól is függ, milyen viszonyban van a frakciód az eladóéval:
**ellenségtől drágább (+25%)**, **szövetségestől olcsóbb (−10%)**.

### Honnan jön a pénz? (jövedelem-források) 🪙

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

### Hová „tűnik" a pénz? (money sinkek)

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
  tételt hoz („ma épp ezt”), úgyhogy minden látogatás más — de **legalább egy ritka
  szakma-alapanyag mindig van a kocsiban**! A kifizetett pénz szintén **eltűnik**
  (money sink). Ha lekésed, legközelebb máshol tűnik fel — érdemes odasietni!

### Lélekkő — a veszélyes vidékek jutalma

A spawntól messze a szörnyek erősebbek. A **magas szintű (3+) szörnyek** eséllyel **Sötét
tokent** (lélekkövet) ejtenek — így a távoli, veszélyes helyeken kalandozni **gazdaságilag is
megéri**.

⚠️ **Kivétel — „a Királynő nem fizet a testvérgyilkosságért":** a **Sötét (Kitaszított)**
játékosnak az **élőhalott** mobokból (zombi, csontváz, phantom, zoglin, wither) **nem esik
lélekkő** — azok úgysem védekeznek ellene (frakció-passzív), így a kockázat nélküli farmolás
nem terem pénzt. Az ÉLŐ szörnyek (creeper, pók, witch, Nether-mobok…) a Sötéteknek is fizetnek.

A valutaváltásnak **napi kerete** van (alapból 200 a forrás-valutában) — a tömeges
oda-vissza váltogatás árfolyam-manipulációja így nem működik.

A lélekkőnek **napi kerete** van (alapból 50 darab játékosonként) — fölötte aznap már nem
esik több. A spawner-mobok sosem ejtenek lélekkövet (nem skálázódnak).

---

### A KP-alapú gazdaság szabályai 🏦

- A szerveren a **készpénz (token item)** az alap: a **player–player kereskedelem kézből
  kézbe** zajlik (átadod a tokent / az itemet), vagy a **piacon**.
- **Banki ügyintézés** (befizetés, kivét, **valutaváltás**) **csak a fővárosokban** lehetséges —
  keresd fel valamelyik város bankját.
- **Közvetlen utalás (`/currency pay`) alapból ki van kapcsolva** — a
  bankszámla default deploymentben a player–szerver ügyletekhez van (boltok,
  piac, claimek ára onnan megy). Admin termékdöntéssel engedélyezheti.

---

## 4. Kasztok ✅

A **kaszt** a hős-típusod: ez dönti el, milyen képességeid (varázslataid) lehetnek. **13 kaszt**
közül választhatsz a `/profile` → **Kaszt** menüből. Minden kasztnak saját Lélekkapocs-tárgya
(a „varázskönyved") és saját **erőforrása** (Erő-csík) van.

| Kaszt | Stílus | Lélekkapocs | Erőforrás |
|---|---|---|---|
| 🧙 **Varázsló** | Elemi és kontroll mágia, távolsági ráolvasások | 📖 Caldesterai Rúnakódex | Mana |
| ⚔️ **Harcos** | Közelharci erő, kitartás, buffok | 📯 Sárkánykirály Kürtje | Düh |
| 🏹 **Íjász** | Távolsági harc, mozgékonyság, csapdák | 🎒 Soleil Vadásztarsolya | Fókusz |
| 🗡️ **Orgyilkos** | Lopakodás, gyors kitörések, gyengítés | 🪨 Homály-szilánk | Energia |
| 🐻 **Druida** | Alakváltó természet-mágia (harc / kontroll / tank / gyógyítás) | 🌱 Aetrinita Sarja | Természeti Erő |
| 🔆 **Paplovag** | Szent harc, védelem és gyógyítás | 🔔 Hajnaltűz Harangja | Szent Erő |
| 💀 **Halállovag** | Rúna-mágia, vér és fagy, közelharci tank/DPS | 💀 Néma Rúnakoponya | Runikus Erő |
| 🌊 **Sámán** | Elemek, totemek, gyógyítás és erősítés | 🪬 Ősvihar Totemje | Mana |
| ☯️ **Szerzetes** | Gyors közelharc, csi-energia, gyógyítás | 🎍 Élet Ága | Csi |
| ✝️ **Pap** | Szent és árny mágia, gyógyítás | 🕯️ Asterlayna Gyertyája | Mana |
| 😈 **Boszorkánymester** | Átkok, démonok és pusztító tűz | 🏮 Kárhozat Lámpása | Lélekerő |
| 👁️ **Démonvadász** | Mozgékony démoni harc és bosszú | 👁️ Hasadék Szeme | Fúria |
| 🐉 **Sárkányidéző** | Sárkány-eszencia: perzselő mágia és gyógyítás | 🐲 Sárkányvér-fiola | Eszencia |

> Az **Erő-csík** a HUD oldalsávban van; a **legtöbb** képesség ezt fogyasztja (idővel
> visszatöltődik, üres csíknál a spell nem sül el). **Hibrid:** a vér-mágia életet, a nagy
> rituálék XP-t, a nehéz fizikai képességek éhséget kérnek. Részletek a
> [fő tájékoztatóban](#iceSMP-játékoskézikönyv).

### Egy kaszt, végleges választás

- **Egy kasztod lehet** — ezt választod ki a menüből, és ez a hősöd. Ez adja a képességeidet
  és (25. szinttől) a specializációdat.
- **A választás végleges:** ha új kasztot szeretnél, egy adminnak kell reszetelnie
  (`/class admin resetclass <játékos>` — ez törli a kasztot, a specet és a feloldott spelleket).
- Segítségül a választó menüben minden kaszt **szerep-címkét** visel (pl. „Sebző / Tank /
  Gyógyító”) — így már a döntés előtt látod, milyen irányba fejlődhet a hősöd.

### Szintezés — hogyan erősödsz?

A kaszt **szörnyek (mobok) megölésével** kap **XP-t** (tapasztalatot):

- **Alap: 10 XP** minden ellenséges mob megöléséért.
- **+3 XP minden „mob-szintért".** A spawntól messzebb a szörnyek erősebbek és magasabb
  szintűek (lásd a [Világesemények / Mob-szintezés] részt) — egy 3-as szintű szörny tehát
  `10 + 3×3 = 19` XP-t ad.
- Csak **ellenséges** mobok adnak XP-t (tehén, csirke nem).

**Mennyi kell egy szinthez?** A következő szint ára `60 + (előző szintek száma × 10)` XP:
- 1 → 2 szint: **60 XP** (kb. 6 alap-mob)
- 2 → 3 szint: **70 XP**
- 3 → 4 szint: **80 XP** … és így tovább. **Max szint: 50.**

Minél magasabb a szinted, annál több ölés kell a következőhöz — de a magas szintű szörnyek
egyenként több XP-t is adnak, szóval érdemes a veszélyesebb, távolabbi vidékekre menni.

### A Lélekkapocs (a „varázskönyved")

A képességeidet egy **kaszt-tematikus tárggyal** használod (lásd a fenti táblázatot):

- **Jobb katt** = a kiválasztott képesség elsütése.
- **Lopakodás (SHIFT) + ütés (bal katt)** = váltás a feloldott képességeid között. A képernyő
  alján látod, melyik van kiválasztva és mennyibe kerül.
- **Ha elveszett:** a `/profile` → Kaszt menüből bármikor **újra igényelheted** (egy gombbal).
- A Lélekkapcsot **nem lehet** craftnál vagy kemencében véletlenül elhasználni — védett tárgy.

### Képesség-fa

A `/profile` → **Képesség-fa** gomb megmutatja a kasztod (és a választott specializációd)
**összes** képességét, feloldási szint szerint: a már feloldottak ragyognak, a zároltak
mutatják, hányadik szint kell hozzájuk.

> A teljes képességlistát (mit tud, mennyibe kerül) lásd: [Képességek](#5-képességek-varázslatok).

### ❤️ Életerő: a kasztod alkata számít *(jelenleg kikapcsolva)*

> ⚠️ Ez a rendszer **egyelőre nem aktív** — az életerő mindenkinek a vanília 20 (10 szív),
> a talentek módosítják. Az alábbi leírás a tervezett működés, amit a szerver egy későbbi
> balansz-körben kapcsol be.

A maximális életerőd a **kasztod jellegével és szintjével nő** (a szívsor közben mindig
10 szív marad — a szívek "sűrűbbek" lesznek):

| Alkat | Kasztok | Növekedés | Max HP az 50. szinten* |
|---|---|---|---|
| 🛡️ Tank-alkat | Harcos, Halállovag | +0.6/szint | ~50 |
| ⚔️ Kiegyensúlyozott | Paplovag (+0.55), Szerzetes, Druida, Démonvadász (+0.45) | +0.45–0.55/szint | ~42–47 |
| 🏹 Mozgékony | Sámán (+0.4), Íjász, Orgyilkos (+0.35) | +0.35–0.4/szint | ~37–40 |
| ✨ Caster | Sárkányidéző (+0.3), Varázsló, Boszorkánymester, Pap (+0.25) | +0.25–0.3/szint | ~32–35 |

\* a talentek és a Rúnavért ezen felül jönnek.

A **sebzésed is nő a szinttel** (a WoW-mód másik fele): a fizikai kasztok kézi/íjas
sebzése szintenként +0.08–0.10 (50-en: +4–5), a castereké +0.03–0.05 — ők cserébe a
**spell-erő szint-skálázást** kapják (szintenként +0,5%, plafonig). A HP gyorsabban nő,
mint a sebzés, így a harcok a szintekkel együtt is hosszabbodnak, nem rövidülnek.

**A gear is számít:** a ritkaság-affixek (Szívósság, Élesség, Vértezet…) mellett a
WoW-módban új **Varázserő** affix is eshet páncélra és fegyverre — %-ban növeli a
spelljeid erejét, így a caster-felszerelés végre sebzést is ad, nem csak túlélést.

- **Harcon kívüli regen:** ha 8 másodpercig nem ért és nem okoztál sebzést, az életerőd
  magától töltődik (2 másodpercenként a maximum 5%-a) — de csak ha nem vagy éhes
  (legalább 3 comb). Harcban továbbra is az étel, a gyógyítás és a pajzsok tartanak életben.
- **A gyógyítások együtt nőnek veled:** a direkt gyógyító képességek a max életerőddel
  arányosan erősödnek, így magas szinten sem értéktelenednek el.

---

---

## 5. Képességek (varázslatok) ✅

Több mint **390 képesség** van — **minden a 13 kaszt és a 35 specializáció** saját, egyedi
készletet kap. Ez az oldal **mind a 13 kasztot és mind a 35 specializációt** felsorolja: mit
csinál egy-egy képesség, mennyibe kerül, mennyit kell rá várni, és hányadik szinten oldódik fel.
A saját kasztodra mindig naprakész a játékbeli **Képesség-fa** menü is (`/profile` → Képesség-fa).

### Hogyan működnek a képességek?

A kasztod képességeit egy **Lélekkapocs** nevű tárggyal használod (a kasztodhoz illő tárgy, pl.
a Varázslónak egy könyv, a Sámánnak egy totem). Tartsd a kezedben, és:

- **Jobb katt** = a kiválasztott képesség elsütése.
- **Lopakodás (SHIFT) + görgetés** = gyors váltás a képességeid között előre/hátra — a
  hotbar nem vált el, és a Lélekkapocs **neve az épp kiválasztott képességet** mutatja.
- **★ Kedvencek:** a spellkönyvben (`/spellbook`) **shift-katt** egy feloldott képességen →
  kedvencnek jelölöd (★). Ha van legalább egy kedvenced, a shift+görgetés **csak a
  kedvenceket** lépkedi — 20+ képességnél így villámgyors a váltás. (Újra shift-katt: levétel;
  üres kedvenc-lista = megint mindent görget.) A spellkönyv **tölcsér-gombjával** a „csak
  feloldottak" nézetre szűrhetsz.
- **Lopakodás (SHIFT) + bal katt (ütés)** = váltás a feloldott képességeid között. A képernyő
  alján (action bar) látod, melyik van kiválasztva és mennyibe kerül.

> ✨ **Látvány:** a képességek most **formázott effektet** kapnak — a célzott spell **sugarat**
> húz a célpontig, a körzeti spell **gyűrűt** rajzol a hatókör mentén, az önmagadra ható spell
> **fölfelé csavarodó fényörvényt** — a szín pedig a spell jellegéhez illik (tűz narancs-vörös,
> fagy kék-fehér, szentség arany…). Tisztán vizuális, a hatáson nem változtat.

> ⚔️ **Közelharci kasztoknak** (Harcos, Paplovag, Halállovag, Szerzetes, Démonvadász): a kezedben
> tartott **kard vagy balta is Lélekkapocsként működik** — nem kell tárgyat váltanod a harc közben!
> Ugyanazok a mozdulatok érvényesek (jobb katt = cast, SHIFT+jobb katt = varázskönyv,
> SHIFT+ütés = képesség-váltás).

> 📈 **Dinamikus erő-skálázás:** a képességeid ereje a **kaszt-szinteddel** nő (alapból
> +0,5%/szint), és az **Arkán Hatalom** talent (+2%/rang) tovább növeli — a spell-mesterség
> (`/spell upgrade`) bónusza fölött. A szint+talent bónusz felső korlátja +50%.

#### ⚡ Az Erő-csík (osztály-erőforrás) — a fő költség
Minden kasztnak van egy **erőforrása** (Mana, Düh, Energia, Fókusz, Csi…), amit a HUD oldalsávban
egy színes csík mutat. **A legtöbb képesség ezt fogyasztja:**

- **A legtöbb képesség ennyit FOGYASZT** a csíkból (a gyors képességek olcsók ~15–20, az ultik
  drágák ~50).
- A csík **magától visszatöltődik** idővel — de **kasztonként másképp viselkedik**:
  - **Düh-típus** (Harcos, Halállovag, Démonvadász): harcon kívül lassan **ürül**, minden
    **bevitt ütés tölti** (+8), harcban lassú visszatöltődés is fut — dühöt a harc termel!
  - **Energia-típus** (Orgyilkos, Szerzetes ~14/mp; Íjász ~11/mp): kis szünetekkel is gyorsan
    visszapörög — pörgős rotációra való.
  - **Mana-típus** (Varázsló, Sámán, Pap, Boszorkánymester, Evoker, Druida: 120-as tár;
    Paplovag: 110): nagyobb készlet, de lomhább (~7/mp) regen — a nagy leadást ki kell várni.
- Ha **nincs elég** erőforrásod, a képesség **nem sül el** (az action bar jelzi).

**Hibrid rendszer** — minden képesség a hozzá illő költséget kéri: a legtöbb az Erő-csíkot, de a
**vér-mágia életet (❤)**, a **nagy rituálé/idézés/ulti XP-t**, a **nehéz fizikai képesség éhséget**.

#### A táblázatok oszlopai
- **Mit csinál:** egyszerűen, mit tesz a képesség.
- **Költség:** mennyit „fizetsz" érte. Lehet **⚡ Erő-csík** (osztály-erőforrás), **🍗 éhség**,
  **XP** vagy **❤ élet** — a spellkönyv (`/spellbook`) mindig kiírja, melyik képesség mit kér.
  *(Ahol ⚡ áll, ott kifejezetten hangolt Erő-csík ár van; a 🍗/XP jelölésű hétköznapi
  spellek is az Erő-csíkot fogyasztják — a cooldown-sávjuk szerinti alapáron —, csak a
  vér (❤), a rituálé/idézés (nagy XP) és a nehéz-fizikai (sok 🍗) spellek, valamint a
  kifejezetten éhség-alapúra állított mozgás-spellek (Villanás, Hátraszökkenés, Dupla Ugrás)
  fizetnek ténylegesen a jelölt költséggel. A `/spellbook` mindig a valós árat mutatja.)*
- **Várakozás:** ennyi ideig kell várni, mire újra használható (cooldown).
- **Szint:** a kasztod hányadik szintjén oldódik fel automatikusan.

> A **sebzés** szívekben értendő: 1 szív = 2 sebzéspont. Pl. „6 sebzés" = 3 szív.
> A „blokk" a távolságot jelenti (1 blokk ≈ 1 méter).

---

> ⚖️ **PvP-szabály — csökkenő kontroll (DR):** ugyanarra a **játékosra** ismételt
> kemény kontroll (fagyasztás, erős lassítás) rövid időn belül egyre gyengébben hat:
> 100% → 50% → 25% → immunis. Szörnyekre nem vonatkozik.

### ⭐ Spell-mesterség és kombók

- **Mesterség (rang):** a `/spell` paranccsal **frakcióvalutáért** fejlesztheted a képességeid
  „mesterségét". Minden rang **rövidíti a várakozási időt** (rangonként -8%, max 5 rang = -40%)
  **ÉS erősíti a hatást**: a **sebzés**, az **önmagad gyógyítása** és a felrakott **effektek
  időtartama** rangonként +5%-kal nő (max +50% az 5. rangon). A költséget és az önsebzést a
  mesterség NEM növeli. A kiválasztott képesség action bar-ján a **★ + szám** mutatja a rangot.
  (`/spell upgrade <id>`)
- **Kombók:** ha **rövid időn belül** (pár mp) bizonyos képességeket **megfelelő sorrendben**
  sütsz el, a második **gyorsabban épül fel** (cooldown-visszatérítés) — és látványos „⚡ Kombó!"
  jelzést kapsz. Néhány alap kombó: Fagyérintés → Arkán Lökés • Ínmetszés → Fojtás •
  Csapdázás → Nyílzápor • Csatakiáltás → Forgószél • Toxinnyíl → Ragály.
- **Kombó-láncok (3 lépés):** néhány kaszt teljes láncot fűzhet: ha mindhárom lépést az
  időablakon belül sütöd el, a **befejező +25% erővel** csap be és gyorsabban is épül fel
  („⚡ Kombó-lánc befejező!"). Láncok: Varázsló: Fagyérintés → Arkán Lökés → Tűzgolyó •
  Halállovag: Jéglánc → Fagycsapás → Megsemmisítés • Sámán: Lángrengés → Lávakitörés →
  Földrengés • Szerzetes: Felkelő Nap Rúgás → Düh Ökle → Sötét Rúgás • Pap: Szent Sújtás →
  Elmerobbantás → Szent Nóva • Boszorkánymester: Romlás → Instabil Átok → Káoszlövedék.
  A cast után az **action bar mutatja a nyíló kombó-ablakot** és a lánc következő lépését —
  könnyű tanulni.

---

## Alapkasztok (13)

### 🧙 Varázsló — alap képességek

Távolsági mágia, lassítás, védelem. A költsége többnyire **XP**.

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Wisplight** | Egy fénygömböt lősz ki, ami 5 percre **bevilágítja** a sötétet, ahová becsapódik | 1 🍗 | nincs | 2 |
| **Mana Nyíl** | Mágikus nyíl 14 blokkra, **3 sebzés** | 8 XP | 10 mp | 3 |
| **Gyökerezés** | Helyben **gyökerezteti** a környező ellenfeleket | 8 🍗 | 5 perc | 5 |
| **Fagyérintés** | A célpontot **megfagyasztja** és lelassítja (8 blokk) | 40 ⚡ | 45 mp | 7 |
| **Arkán Pajzs** | **Extra szíveket** (felszívódás) és sebzéscsökkentést ad magadnak | 30 XP | 90 mp | 9 |
| **Megzavarás** | 15 blokkban **összezavar**: a játékosok megvakulnak, a mobok **elvesztik a célpontjukat** (10 mp-ig újra és újra), lelassulnak és gyengébben ütnek | 160 XP | 20 perc | 10 |
| **Villanás** | **8 blokkot előre teleportálsz**, amerre nézel | 1,5 🍗 | 30 mp | 12 |
| **Esőtánc** | **Esőt** hív a környékre | 352 XP | 60 perc | 15 |
| **Arkán Lökés** | Körkörös **robbanás** (5 blokk): 3 sebzés + ellök | 40 XP | 90 mp | 18 |
| **Gravitációs Örvény** | A körülötted lévőket **a levegőbe emeli** és lelassítja (6 blokk) | 60 XP | 150 mp | 21 |

### ⚔️ Harcos — alap képességek

Közelharci erő, buffok, kitartás. A költsége többnyire **éhség**.

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Csatakiáltás** | Magadra **Erő + Gyorsaság** buff 15 mp-re | 4 🍗 | 90 mp | 3 |
| **Fegyverzet** | Azonnal **teljes páncélt + kardot** kapsz 2 percre (akkor működik, ha nincs rajtad páncél) | 352 XP | 5 perc | 5 |
| **Hősi Szökellés** | **Előrelendülsz** egy nagy ugrással | 3 🍗 | 25 mp | 7 |
| **Markolatütés** | 4 blokkon belül **3 sebzés** + erős lassítás | 4 🍗 | 30 mp | 9 |
| **Belső Fókusz** | 5 mp-re **helyben megállsz**, cserébe majdnem sebezhetetlen + gyógyulsz | 20 🍗 | 8 perc | 10 |
| **Második Lendület** | **3 szívet gyógyulsz** + regeneráció | 8 🍗 | 3 perc | 12 |
| **Lökéshullám** | A magad előtti ellenfeleket **ellöki** | 30 XP | 60 mp | 15 |
| **Forgószél** | **5 mp-es pörgés-channel:** minden ütemben 2 sebzés + ellökés (4 blokk) | 6 🍗 | 150 mp | 18 |
| **Vasbőr** | 10 mp-re **erős sebzéscsökkentés** | 8 🍗 | 150 mp | 20 |
| **Megfélemlítés** | A környékbeli ellenfeleket **legyengíti és lelassítja** (6 blokk) | 6 🍗 | 120 mp | 22 |

### 🏹 Íjász — alap képességek

Távolsági harc, mozgékonyság, csapdák. A költsége többnyire **éhség**.

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Vadászjel** | A célpont **felragyog** (20 blokkról is látod) | 2 🍗 | 30 mp | 2 |
| **Sasszem** | Élesebb látás + gyorsabb lépés | 4 🍗 | 90 mp | 3 |
| **Hátraszökkenés** | **Hátraugrasz** a veszélyből | 1,5 🍗 | 15 mp | 5 |
| **Sortűz** | Több nyilat lősz ki egyszerre | 5 🍗 | 45 mp | 8 |
| **Átütő Lövedék** | Egy **gyors, átütő** szellem-nyíl, ami **3 célponton üt át** | 25 XP | 35 mp | 10 |
| **Csapdázás** | A célpontot **helyhez köti** (erős lassítás, 14 blokk) | 4 🍗 | 45 mp | 12 |
| **Dupla Ugrás** | A levegőben **még egyet ugorhatsz** | 1,5 🍗 | 5 mp | 15 |
| **Álcázás** | 8 mp-re **láthatatlanná válsz** | 5 🍗 | 120 mp | 17 |
| **Nyílzápor** | **7 nyíl** legyezőben, terülő sebzés | 60 XP | 90 mp | 20 |
| **Szélléptek** | 20 mp-re **gyorsaság + magasabb ugrás** | 5 🍗 | 90 mp | 22 |

### 🗡️ Orgyilkos — alap képességek

Lopakodás, gyors kitörések, gyengítés. A költsége **éhség**.

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Tőrhajítás** | Gyors **tőrt dobsz** a célra | 2 🍗 | 15 mp | 2 |
| **Adrenalin** | **Gyorsaság + bányászgyorsaság** 8 mp-re | 4 🍗 | 60 mp | 4 |
| **Árnyéklépés** | A célpont **mögé teleportálsz** | 60 ⚡ | 1,5 perc | 5 |
| **Ínmetszés** | 5 blokkon belül **2 sebzés** + erős lassítás | 4 🍗 | 35 mp | 8 |
| **Elsötétítés** | A célpontot **megvakítja** és legyengíti (10 blokk) | 5 🍗 | 60 mp | 10 |
| **Füstbomba** | Füstöt vetsz, hogy **eltűnj** a harcból | 6 🍗 | 120 mp | 12 |
| **Kitérés** | Hátraugrasz + rövid **láthatatlanság** | 5 🍗 | 75 mp | 15 |
| **Fojtás** | 3,5 blokkon belül **5 sebzés** + nagyon erős lassítás | 2,5 ❤ | 90 mp | 18 |
| **Árnyéksuhanás** | **Hosszú előrelendülés** + rövid láthatatlanság | 5 🍗 | 60 mp | 20 |
| **Haláljegy** | A célpont **felragyog + legyengül** 12 mp-re (15 blokk) | 60 ⚡ | 120 mp | 24 |

### 🌿 Druida — alap képességek

Természet-mágia + négy alakváltó forma (medve/párduc/hold/utazó).

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Sarjadás** | **3 szívet gyógyulsz** + regeneráció | 40 XP | 30 mp | 2 |
| **Napperzselés** | 4 sebzés + **felgyújt** (12 blokk) | 35 XP | 25 mp | 3 |
| **Töviscsapás** | 5 blokkon belül **4 sebzés** + méreg | 3 🍗 | 25 mp | 5 |
| **Párducforma** | **Párduc-alak:** sebesség + erő a gyors lecsapásokhoz, de amíg aktív **4 szívvel csökken** a maximum életed | 50 ⚡ | 6 mp | 7 |
| **Gyökérfonat** | A célpontot **helyhez köti** (nagyon erős lassítás, 8 blokk) | 4 🍗 | 45 mp | 9 |
| **Kéregbőr** | 8 mp-re **erős sebzéscsökkentés** | 4 🍗 | 1,5 perc | 11 |
| **Medveforma** | **Medve-alak:** ellenállás (I), felszívás és erő, kissé lassabban, de gyorsabban éhezel | 50 ⚡ | 6 mp | 13 |
| **Vadgomba** | **Dobható gomba**, ami 3 mp múlva felrobban: 4 blokkos körben lassítás + méreg 4 mp-re | 40 XP | 60 mp | 15 |
| **Utazóforma** | **Utazó-alak:** jelentős sebesség a gyors helyváltáshoz, de **gyengeség (II)** sújt | 50 ⚡ | 6 mp | 17 |
| **Ciklon** | A célpontot **a levegőbe emeli** + lassítja (10 blokk) | 4 🍗 | 50 mp | 19 |
| **Holdforma** | **Hold-alak:** regeneráció + tűzállóság a hold-mágiához, de **erős bányászlassultság** sújt | 50 ⚡ | 6 mp | 21 |

### ✨ Paplovag — alap képességek

Szent harci mágia: sújtás, gyógyítás, áldás-aurák.

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Sújtás** | Szent sújtás: **5 sebzés** (14 blokk) | 30 XP | 20 mp | 2 |
| **Villámgyógyítás** | Gyorsan **2,5 szívet gyógyulsz** | 40 XP | 30 mp | 4 |
| **Áhítat Aurája** | 10 mp-re **sebzéscsökkentés** + tüske: aki megüt, **1 sebzést visszakap** | 4 🍗 | 60 mp | 6 |
| **Igazság Pörölye** | 3 sebzés + **erős lassítás** (8 blokk) | 4 🍗 | 45 mp | 8 |
| **Megszentelés** | Tüzes terület: 2 sebzés + **felgyújt** (5 blokk) | 5 🍗 | 60 mp | 11 |
| **Erő Áldása** | Magadra **Erő** buff 15 mp-re | 5 🍗 | 120 mp | 13 |
| **Szent Harag** | **Fény-lövedék:** a célpont jelenlegi életének **30%-át elveszíti** (bossra nem hat) | 60 XP | 90 mp | 15 |
| **Isteni Védelem** | Erős sebzés- és **tűzvédelem** | 6 🍗 | 120 mp | 17 |
| **Sugárzás** | A **csapattársaidat gyógyítja** a környéken (8 blokk) | 6 🍗 | 120 mp | 19 |
| **Kézrátétel** | 30 mp-re **jóllakottságot** ad (a korábbi öngyógyítás helyett) | 70 XP | 150 mp | 21 |

### 💀 Halállovag — alap képességek

Rúnikus harc, fagy és vér; önfenntartás életszívással.

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Halálcsapás** | 6 sebzés + **öngyógyítás** (közelharc) | 3 🍗 | 20 mp | 2 |
| **Rúnacsapás** | **Dobható rúnakő:** az első hozzáérő ellenfélnek 7 sebzés + lassítás | 3 🍗 | 40 mp | 4 |
| **Halálfonál** | 5 sebzés + **sorvadás** (14 blokk) | 35 XP | 30 mp | 6 |
| **Jéglánc** | A célpontot + egy közeli ellenfelet (max 2 cél) **megfagyaszt + lelassít** (12 blokk) | 3 🍗 | 30 mp | 8 |
| **Jeges Érintés** | 3 sebzés + **megfagyaszt** + lassít (10 blokk) | 35 XP | 35 mp | 11 |
| **Csontpajzs** | Sebzéscsökkentés + **extra szív** | 5 🍗 | 60 mp | 13 |
| **Vérforralás** | Körkörös 3 sebzés + sorvadás (5 blokk) | 4 🍗 | 45 mp | 15 |
| **Mágiapajzs** | Sebzés- és **tűzvédelem** | 5 🍗 | 90 mp | 17 |
| **Sötét Parancs** | A közeli ellenfeleket **lelassítja**, te védettebb leszel (6 blokk) | 4 🍗 | 60 mp | 19 |
| **Fagysugár** | 2 blokk széles **fagysugarat** lősz előre, ami megfagyaszt mindenkit az útjában (12 blokk) | 40 XP | 90 mp | 21 |

### 🌩️ Sámán — alap képességek

Elemi mágia (villám/láva/jég) + totem-aurák.

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Villámnyíl** | **Villám** a célpontra: 5 sebzés (16 blokk) | 30 XP | 20 mp | 2 |
| **Gyógyár** | **3 szívet gyógyulsz** | 40 XP | 30 mp | 4 |
| **Földrengés** | Körkörös **5 sebzés** + ellökés (5 blokk) | 3 🍗 | 30 mp | 6 |
| **Lángrengés** | 4 sebzés + **felgyújt** (12 blokk) | 35 XP | 30 mp | 8 |
| **Fagyrengés** | 4 sebzés + **megfagyaszt** + lassít (12 blokk) | 35 XP | 30 mp | 11 |
| **Perzselő Totem** | Lerakott **támadó totem**, ami az ellenfeleket sebzi és felgyújtja | 50 XP | 75 mp | 15 |
| **Villámpajzs** | 8 mp-re **sebzéscsökkentés** | 5 🍗 | 90 mp | 13 |
| **Gyógyár-totem** | Lerakott **gyógyító totem**, ami a környéket gyógyítja | 5 🍗 | 90 mp | 19 |
| **Szélroham** | Magadra **gyorsaság** buff | 4 🍗 | 60 mp | 17 |
| **Ősi Szellem** | **4 szívet gyógyulsz** + erős regeneráció | 70 XP | 150 mp | 21 |

### 👊 Szerzetes — alap képességek

Harcművészet: gyors kombók, mozgékonyság, csi-gyógyítás.

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Tigristenyér** | Közeli **5 sebzés** | 2 🍗 | 15 mp | 2 |
| **Gördülés** | Gyorsan **előregördülsz** | 2 🍗 | 20 mp | 4 |
| **Sötét Rúgás** | 6 sebzés + **erős ellökés** (4 blokk) | 3 🍗 | 20 mp | 6 |
| **Élénkítés** | **2,5 szívet gyógyulsz** + a 6 blokkos körben lévő ellenfeleket 7,5 mp-re **felfénylővé** teszi | 40 XP | 30 mp | 8 |
| **Pörgő Darurúgás** | Körkörös **4 sebzés**, **két hullámban** (4 blokk) | 4 🍗 | 30 mp | 11 |
| **Csi Hullám** | 10 blokkos körben az ellenfeleket **felfénylővé** teszi + **lelassítja** 5 mp-re | 35 XP | 30 mp | 13 |
| **Provokálás** | A közeli ellenfeleket **lelassítja**, te védettebb leszel (6 blokk) | 3 🍗 | 45 mp | 15 |
| **Repülő Kígyórúgás** | **Hosszú előrelendülés**; becsapódáskor **+5 sebzés és ellökés** | 3 🍗 | 35 mp | 17 |
| **Ártalom Kiűzése** | Körkörös 2 sebzés + **öngyógyítás** (4 blokk), 5 mp-re **Taszítás III** botot ad | 45 XP | 90 mp | 19 |
| **Lábseprés** | Körkörös **gyengeség (II)** + ellök (5 blokk) | 4 🍗 | 60 mp | 21 |

### ⛪ Pap — alap képességek

Szent és árny mágia, gyógyítás.

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Szent Sújtás** | Szent sújtás: sebzés helyett **gyengeséget** (I) okoz (14 blokk) | 30 XP | 25 mp | 2 |
| **Gyógyítás** | **3 szívet gyógyulsz** | 40 XP | 30 mp | 4 |
| **Megújulás** | Magadra **regeneráció** | 30 XP | 30 mp | 6 |
| **Erő Szava: Pajzs** | Magadra **extra szívek** (felszívódás) + 5 mp-re **lelassulsz** | 4 🍗 | 45 mp | 8 |
| **Árnyszó: Fájdalom** | A célpontra **sorvadás** (12 blokk) | 3 🍗 | 25 mp | 11 |
| **Elmerobbantás** | Megjelölöd a célt; **3 mp múlva felrobban**: 6 sebzés + 5 splash sebzés (14 blokk) | 40 XP | 40 mp | 13 |
| **Feloldozás** | Magadra **sebzéscsökkentés** | 4 🍗 | 60 mp | 15 |
| **Szent Nóva** | A **csapattársaidat gyógyítja** a környéken (6 blokk) | 5 🍗 | 60 mp | 17 |
| **Elhalványulás** | Rövid **láthatatlanság + gyorsaság** | 4 🍗 | 75 mp | 19 |
| **Lebegés** | Magadra **magasabb ugrás** | 3 🍗 | 60 mp | 21 |

### 🔮 Boszorkánymester — alap képességek

Átkok, démon-tűz, életszívás.

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Árnyéklövedék** | **5 sebzés** (16 blokk) | 30 XP | 20 mp | 2 |
| **Romlás** | A célpontra **sorvadás** (12 blokk) | 3 🍗 | 25 mp | 4 |
| **Életszívás** | 3 sebzés + **magadat gyógyítod** (12 blokk) | 35 XP | 45 mp | 6 |
| **Gyengeség Átka** | A célpontot **legyengíti** (12 blokk) | 3 🍗 | 30 mp | 8 |
| **Felgyújtás** | 4 sebzés + **felgyújt** (14 blokk) | 35 XP | 30 mp | 11 |
| **Átok Nyúl** | **3 mp-es nyúl-akna:** érintésre 3 szívnyi splash sebzés | 35 XP | 90 mp | 13 |
| **Rettegés** | A célpontot **lelassítja + összezavarja** (10 blokk) | 4 🍗 | 60 mp | 15 |
| **Gyötrelem Átka** | A célpontra **sorvadás + méreg** (12 blokk) | 2 ❤ | 45 mp | 17 |
| **Démonpáncél** | Erős **sebzéscsökkentés** | 5 🍗 | 90 mp | 19 |
| **Démoni Kör** | **Előrelendülés**; a lendület helyén só-csapdát hagysz — a felvevő 3 mp-re **mérgezést** kap | 3 🍗 | 45 mp | 21 |

### 😈 Démonvadász — alap képességek

Fel-mágia, mozgékony közelharc.

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Démon Harapása** | Közeli **5 sebzés** | 2 🍗 | 15 mp | 2 |
| **Fel Roham** | **Hosszú előrelendülés** + sebesség | 2 🍗 | 20 mp | 4 |
| **Lélek Csere** | **Lövedék:** a célpont jelenlegi életének 25%-a rád száll át | 2 🍗 | 50 mp | 6 |
| **Sarlóvetés** | **Bumeráng-sarló:** oda-vissza repülve mindkét irányban 5 sebzést okoz (14 blokk) | 30 XP | 40 mp | 8 |
| **Lángaura** | Körkörös 3 sebzés + **felgyújt** (5 blokk) | 40 XP | 45 mp | 11 |
| **Káoszcsapás** | Közeli **6 sebzés** + **4 blokkal felütés** a levegőbe | 3 🍗 | 25 mp | 13 |
| **Mágiafalás** | Magadra **sebzéscsökkentés** | 3 🍗 | 60 mp | 15 |
| **Gyötrés** | A közeli ellenfeleket **legyengíti**, te védettebb leszel (6 blokk) | 3 🍗 | 45 mp | 17 |
| **Siklás** | Magadra **magasabb ugrás + gyorsaság** | 2 🍗 | 45 mp | 19 |
| **Szellemlátás** | 8 mp-ig **2 mp-enként váltakozó láthatatlanság** | 3 🍗 | 75 mp | 21 |

### 🐉 Sárkányidéző — alap képességek

Sárkány-mágia: tűz, szél, smaragd-gyógyítás.

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Lánggolyó** | 5 sebzés + 3 mp **égés**, 2,5 blokkos **robbanással** (15 blokk) | 30 XP | 20 mp | 2 |
| **Azúr Csapás** | **5 sebzés** (14 blokk) | 30 XP | 20 mp | 4 |
| **Smaragd Virág** | Gyógyulás + regeneráció | 40 XP | 45 mp | 6 |
| **Szárnycsapás** | A közeli ellenfeleket **ellöki + lelassítja** (5 blokk) | 3 🍗 | 30 mp | 8 |
| **Tűzlehelet** | Körkörös 5 sebzés + **felgyújt** (6 blokk) | 45 XP | 45 mp | 11 |
| **Farokcsapás** | Körkörös 4 sebzés + ellök (5 blokk) | 3 🍗 | 30 mp | 13 |
| **Lebegés** | Magadra **magasabb ugrás + gyorsaság + lassú esés** | 2 🍗 | 45 mp | 15 |
| **Obszidián Pikkelyek** | Sebzés- és **tűzvédelem** | 5 🍗 | 90 mp | 17 |
| **Mély Lélegzet** | **3,5 mp-es lángszórás-kúp:** sebez + felgyújt mindenkit, aki bele kerül (6 blokk) | 60 XP | 90 mp | 19 |
| **Zöldellő Ölelés** | 5 blokkos körben az ellenfeleket **legyengíti**, téged **megerősít** (Erő) 6 mp-re | 50 XP | 60 mp | 21 |

---

## Specializációs képességek (25. szinttől)

Ezek **csak akkor** oldódnak fel, ha az adott specializációt választottad (lásd a
[Specializációk](#6-specializációk) oldalt). Minden specnek 25–45. szint között oldódnak fel
a saját képességei.

### 🌊 Elementalista (Varázsló-spec) — tűz, jég, villám

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Naptánc** | **Napsütést** hív (eltünteti az esőt) | 352 XP | 60 perc | 25 |
| **Tűzgolyó** | **Tűzgolyót** lősz, ami felrobban | 40 XP | 45 mp | 26 |
| **Fagyrobbanás** | Körkörös **fagyasztás** + lassítás (6 blokk) | 60 XP | 90 mp | 28 |
| **Mennykőcsapás** | **Villámot** hív a célpontra: 6 sebzés (18 blokk) | 80 XP | 120 mp | 30 |
| **Szerencsecsillag** | **40% eséllyel kivédesz** minden sebzést, amíg aktív (másodpercenként XP-t fogyaszt) | 0 XP | azonnali | 30 |
| **Parázsvihar** | Tüzes terület: 2 sebzés + **felgyújt** (6 blokk) | 70 XP | 120 mp | 33 |
| **Örvényrántás** | A célpontot **magadhoz rántod** (14 blokk) | 45 XP | 60 mp | 36 |
| **Kőbőr** | Erős sebzéscsökkentés (de lelassulsz) | 60 XP | 150 mp | 39 |
| **Viharlöket** | **Széllökés** lövedék | 35 XP | 40 mp | 42 |
| **Elemi Túltöltés** | **Hatalmas robbanás:** 5 sebzés + tűz + fagy + ellök (7 blokk) | 150 XP | 5 perc | 45 |

### 💀 Nekromanta (Varázsló-spec) — holtak, lélek, dögvész

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Életszívás** | Sebzed a célt, és **magadat gyógyítod** | 20 XP | 60 mp | 25 |
| **Sírkéz** | A célpontot **lefogja** (lassítás + sorvadás, 10 blokk) | 2 ❤ | 45 mp | 26 |
| **Lélekszívás** | Körkörös sebzés + **öngyógyítás** (5 blokk) | 50 XP | 90 mp | 28 |
| **Csontfagy** | Dermesztő **fagyhullám** a környékre | 25 XP | 90 mp | 30 |
| **Rettegésaura** | Sorvadás + gyengeség a közeli ellenfeleknek (6 blokk) | 4 ❤ | 120 mp | 32 |
| **Csontdárda** | **Csontnyilat** lősz | 30 XP | 30 mp | 34 |
| **Dögvészérintés** | Méreg + sorvadás + éhség a célpontra | 3 ❤ | 75 mp | 36 |
| **Holtak Hada** | **4 zombi szolgát** idézel 45 mp-re | 90 XP | 4 perc | 38 |
| **Halálpaktum** | Feláldozol 2 szívet, cserébe **Erő + extra szív** | 40 XP | 150 mp | 39 |
| **Kísértetforma** | Láthatatlan + gyors leszel (de gyengébb) | 4 ❤ | 3 perc | 42 |
| **Csontíjászok** | **2 csontváz-íjász szolgát** idézel | 100 XP | 4 perc | 44 |
| **Lélekaratás** | Nagy körkörös sebzés + **erős öngyógyítás** (7 blokk) | 150 XP | 5 perc | 45 |

### 🩸 Berserker (Harcos-spec) — vér, düh, támadás

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Lakoma** | Teljesen **feltölti az éhséged** | 352 XP | 2 perc | 25 |
| **Düh** | **Erő + Gyorsaság** 12 mp-re | 4 ❤ | 90 mp | 26 |
| **Vakmerő Csapás** | 6 sebzés, de **1 szívet te is kapsz** | 4 🍗 | 25 mp | 27 |
| **Vérszomj** | Feláldozol 1 szívet → **gyorsaság + erő** | 6 🍗 | 120 mp | 29 |
| **Földcsapás** | A földbe csapsz: sebzés + **a levegőbe dobja** az ellenfeleket | 6 🍗 | 60 mp | 32 |
| **Üvöltés** | Lassítás + gyengeség a környékre (7 blokk) | 5 🍗 | 90 mp | 34 |
| **Megállíthatatlan** | **Extra szív + gyorsaság** | 4 ❤ | 150 mp | 37 |
| **Vad Ugrás** | **Nagy ugrás** előre | 4 🍗 | 40 mp | 39 |
| **Vérfürdő** | Körkörös 5 sebzés + **öngyógyítás** | 4 ❤ | 150 mp | 42 |
| **Berserk** | A végső buff: **erős Erő + Gyorsaság + védelem** | 12 🍗 | 5 perc | 45 |

### 🛡️ Védelmező (Harcos-spec) — pajzs, védelem, gyógyítás

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Bástya** | Sziklaszilárddá válsz, a csapások lepattannak | 8 🍗 | 2 perc | 25 |
| **Kihívás** | A közeli ellenfeleket **magadra húzod** (lassítja őket), te védettebb leszel | 4 🍗 | 60 mp | 26 |
| **Megerősítés** | 30 mp-re **extra szívek** | 6 🍗 | 120 mp | 27 |
| **Pajzsfal** | Nagyon erős sebzéscsökkentés (de lassú leszel) | 8 🍗 | 150 mp | 29 |
| **Gyógyító Szó** | A **csapattársaidat gyógyítja** a környéken (8 blokk) | 6 🍗 | 120 mp | 32 |
| **Vasbástya** | Sebzés- és **tűzvédelem** | 7 🍗 | 150 mp | 34 |
| **Pajzsroham** | Pajzzsal **előrerontasz**, közben védettebb vagy | 4 🍗 | 45 mp | 37 |
| **Égisz** | A **csapattársaidnak** ad sebzésvédelmet (8 blokk) | 8 🍗 | 3 perc | 39 |
| **Megtorlás** | 5 blokkon belül 4 sebzés + **erős ellökés** | 5 🍗 | 45 mp | 42 |
| **Végső Ellenállás** | A végső védőbuff: **sok extra szív + erős regeneráció** | 10 🍗 | 5 perc | 45 |

### 🎯 Mesterlövész (Íjász-spec) — pontos, távoli lövések

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Pehelykönnyű Lépte** | **Lassú esés** — nincs zuhanósebzés, lebegve érsz földet | 1 🍗 | 45 mp | 25 |
| **Sasles** | A környék ellenfelei **felragyognak** + éjjellátás | 4 🍗 | 60 mp | 26 |
| **Fejlövés** | Egy pontos lövés: **7 sebzés** (15 blokk) | 40 XP | 60 mp | 27 |
| **Duplalövés** | **2 nyíl** gyorsan egymás után | 4 🍗 | 30 mp | 29 |
| **Jelzőfény** | Nagy területen **felfedi** az ellenfeleket + éjjellátás | 4 🍗 | 90 mp | 32 |
| **Térdlövés** | 2 sebzés + **erős lassítás** (15 blokk) | 5 🍗 | 45 mp | 34 |
| **Magasles** | **Felugrasz** + lassú esés (jó kilátóhely) | 4 🍗 | 60 mp | 37 |
| **Tökéletes Fókusz** | Gyors lövés + éjjellátás + gyorsaság | 6 🍗 | 120 mp | 39 |
| **Szellemsortűz** | **5 szellem-nyíl** legyezőben | 60 XP | 90 mp | 42 |
| **Mesterlövés** | A végső lövés: **12 sebzés** (20 blokk) | 80 XP | 5 perc | 45 |

### 🐺 Vadmester (Íjász-spec) — állat-társak

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Barátság** | A közeli **állatokat megszelídíti/lecsillapítja** | 4 🍗 | 45 mp | 25 |
| **Farkashívás** | **Hű farkas társat** idézel | 100 XP | 10 perc | 26 |
| **Méhraj** | **Méhrajt** szabadítasz az ellenfélre | 6 🍗 | 3 perc | 28 |
| **Mérgező Csirke** | Egy **robbanó csirkét** lősz ki lövedékként | 5 🍗 | 30 mp | 30 |
| **Ősi Kötelék** | A közeli **társaidat felerősíti** | 5 🍗 | 2 perc | 32 |
| **Vastag Irha** | Sebzésvédelem + extra szív | 6 🍗 | 120 mp | 34 |
| **Pandaőrség** | **2 harcias pandát** idézel | 8 🍗 | 4 perc | 35 |
| **Ragadozó Érzékek** | Nagy területen felfedi az ellenfeleket + éjjellátás | 4 🍗 | 90 mp | 37 |
| **Csorda Rohama** | **Gyors előrelendülés** + sebesség | 5 🍗 | 60 mp | 39 |
| **Sólyomcsapás** | 5 sebzés + rövid vakítás (12 blokk) | 5 🍗 | 60 mp | 42 |
| **Vad Falka** | **3 vad farkast** idézel | 9 🍗 | 4 perc | 43 |
| **Vadak Ura** | A végső: a környék ellenfeleit **legyengíti**, téged **felerősít** | 10 🍗 | 5 perc | 45 |

### ☠️ Méregkeverő (Orgyilkos-spec) — mérgek

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Méregcsapás** | Közeli csapás **méreggel** | 6 🍗 | 60 mp | 25 |
| **Toxinnyíl** | Mérgezett nyíl: 1 sebzés + méreg (12 blokk) | 3 🍗 | 20 mp | 26 |
| **Savfröccs** | Területre **savat** fröccsentesz: 2 sebzés + méreg (4 blokk) | 4 🍗 | 45 mp | 27 |
| **Zsibbasztószer** | A célpontot **lelassítja, legyengíti** (8 blokk) | 5 🍗 | 60 mp | 29 |
| **Ellenméreg** | **Leszedi rólad a mérgeket** és rossz hatásokat | 4 🍗 | 90 mp | 32 |
| **Ragály** | Mérgező felhő a környékre (6 blokk) | 7 🍗 | 120 mp | 34 |
| **Bénító Csapás** | 2 sebzés + **majdnem teljes lebénítás** (4,5 blokk) | 6 🍗 | 75 mp | 37 |
| **Mérgező Felhő** | Méreg + **hányinger** a környéken (5 blokk) | 7 🍗 | 120 mp | 39 |
| **Sorvasztó Méreg** | **Erős méreg** + tartós éhség (8 blokk) | 50 XP | 90 mp | 42 |
| **Gyilkos Galóca** | A végső méreg: 4 sebzés + **nagyon erős méreg** + gyengeség (8 blokk) | 70 XP | 4 perc | 45 |

### 👻 Fantom (Orgyilkos-spec) — árnyék, félelem, eltűnés

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Elrejtőzés** | Egy közeli **biztonságos helyre teleportálsz**, eltűnik rólad a páncél, és rövid **láthatatlanság + gyorsaság** | 550 XP | 8 perc | 25 |
| **Szellemléptek** | Rövid **láthatatlanság + gyorsaság** | 4 🍗 | 60 mp | 26 |
| **Fázisugrás** | **6 blokkot teleportálsz** előre | 5 🍗 | 45 mp | 27 |
| **Kísértés** | A célpontot **megvakítja + elsötétíti** (10 blokk) | 5 🍗 | 60 mp | 29 |
| **Rémület** | A környék ellenfeleit **megvakítja, lelassítja** (6 blokk) | 6 🍗 | 120 mp | 32 |
| **Hidegfolt** | Körkörös **fagyasztás** (5 blokk) | 5 🍗 | 90 mp | 34 |
| **Éteri Forma** | Láthatatlan + védettebb (de gyengébb) | 7 🍗 | 3 perc | 37 |
| **Fantomszorítás** | A célpontot **a levegőbe emeli** + 2 sebzés (10 blokk) | 3 ❤ | 75 mp | 39 |
| **Rémsuttogás** | Gyengeség + hányinger + sötétség a célpontra (12 blokk) | 3 ❤ | 90 mp | 42 |
| **Kísértet** | A végső: **hosszú láthatatlanság + gyorsaság + ugrás** | 4 ❤ | 5 perc | 45 |

### 🐾 Vadőr (Druida-spec) — közelharci formák, karmok

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Vad Karom** | Közeli **6 sebzés** | 3 🍗 | 20 mp | 25 |
| **Vad Roham** | **Előrelendülés** + rövid sebzéscsökkentés | 4 🍗 | 35 mp | 27 |
| **Marás** | 7 sebzés + ellök (4 blokk) | 4 🍗 | 30 mp | 30 |
| **Mancscsapás** | Körkörös **5 sebzés** (4 blokk) | 3 🍗 | 25 mp | 32 |
| **Felfrissülés** | Gyógyulás + erős regeneráció | 50 XP | 60 mp | 34 |
| **Ősi Üvöltés** | Gyengeség + lassítás a környékre (6 blokk) | 5 🍗 | 75 mp | 37 |
| **Túlélő Ösztön** | Sebzéscsökkentés + regeneráció | 5 🍗 | 90 mp | 39 |
| **Tombolás** | Magadra **Erő + Gyorsaság** | 6 🍗 | 90 mp | 42 |
| **Vad Hajsza** | A végső: körkörös 8 sebzés + ellök + **Erő + Gyorsaság** (6 blokk) | 150 XP | 5 perc | 45 |

### 🌙 Holdjós (Druida-spec) — hold- és nap-mágia

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Holdtűz** | 5 sebzés + **felgyújt** (16 blokk) | 30 XP | 20 mp | 25 |
| **Csillagár** | **7 sebzés** (18 blokk) | 45 XP | 35 mp | 28 |
| **Csillagláng** | 5 sebzés + **felgyújt** (14 blokk) | 40 XP | 30 mp | 30 |
| **Hurrikán** | Körkörös 3 sebzés + ellök + lassít (6 blokk) | 60 XP | 90 mp | 33 |
| **Napsugár** | 6 sebzés + felgyújt + **megvakít** (14 blokk) | 55 XP | 60 mp | 36 |
| **Csillaghullás** | Körkörös **6 sebzés** (6 blokk) | 80 XP | 120 mp | 40 |
| **Hold Áldása** | A **csapattársaidat gyógyítja** a környéken (8 blokk) | 6 🍗 | 120 mp | 42 |
| **Égi Együttállás** | A végső: körkörös 8 sebzés + regeneráció (7 blokk) | 150 XP | 5 perc | 45 |

### 💛 Szentlélek (Paplovag-spec) — gyógyítás, csapat-áldások

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Szent Fény** | **4 szívet gyógyulsz** + erős regeneráció | 45 XP | 30 mp | 25 |
| **Fény Jelzőtüze** | A **csapattársaidat gyógyítja** a környéken (8 blokk) | 6 🍗 | 90 mp | 27 |
| **Dicsőség Szava** | A **csapattársaidat gyógyítja** + magadat (6 blokk) | 45 XP | 30 mp | 30 |
| **Királyok Áldása** | A csapatnak **Erő + sebzéscsökkentés** (8 blokk) | 6 🍗 | 120 mp | 33 |
| **Aura-mesterség** | A csapatnak **erős sebzéscsökkentés** (8 blokk) | 6 🍗 | 120 mp | 36 |
| **Isteni Pajzs** | **Erős sebzéscsökkentés + extra szívek** | 8 🍗 | 3 perc | 39 |
| **Őrangyal** | **3 szívet gyógyulsz** + regeneráció + extra szív | 70 XP | 150 mp | 42 |
| **Megtorló Harag** | A végső: a csapatnak **regeneráció + Erő**, magadat gyógyítod (7 blokk) | 150 XP | 5 perc | 45 |

### ⚖️ Megtorló (Paplovag-spec) — szent sújtó sebzés

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Keresztes Csapás** | Közeli **6 sebzés** | 3 🍗 | 20 mp | 25 |
| **Ítélet** | **6 sebzés** (16 blokk) | 40 XP | 30 mp | 27 |
| **Igazság Pengéje** | 7 sebzés + ellök (6 blokk) | 4 🍗 | 35 mp | 30 |
| **Szent Tűz** | 5 sebzés + **felgyújt** (14 blokk) | 50 XP | 45 mp | 33 |
| **Buzgalom** | Magadra **Erő + Gyorsaság** | 5 🍗 | 75 mp | 36 |
| **Isteni Vihar** | Körkörös 5 sebzés + ellök (5 blokk) | 5 🍗 | 60 mp | 39 |
| **Hamuébresztés** | Körkörös 4 sebzés + **felgyújt** (5 blokk) | 60 XP | 90 mp | 42 |
| **Végső Ítélet** | A végső: körkörös 9 sebzés + ellök (6 blokk) | 150 XP | 5 perc | 45 |

### 🩸 Vérlovag (Halállovag-spec) — tank, életszívás

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Szívcsapás** | 6 sebzés + **öngyógyítás** (közelharc) | 3 🍗 | 20 mp | 25 |
| **Velőtörés** | 5 sebzés + **extra szív** (4 blokk) | 4 🍗 | 30 mp | 27 |
| **Rúnacsapolás** | Gyógyulás + sebzéscsökkentés | 4 🍗 | 60 mp | 30 |
| **Vértükör** | Erős **sebzéscsökkentés** | 5 🍗 | 90 mp | 33 |
| **Vámpírvér** | Gyógyulás + extra szívek + regeneráció | 6 🍗 | 120 mp | 36 |
| **Csontvihar** | Körkörös 4 sebzés + **öngyógyítás** + ellök (5 blokk) | 70 XP | 120 mp | 39 |
| **Táncoló Rúnafegyver** | A végső: körkörös 8 sebzés + erős öngyógyítás + Erő + védelem (6 blokk) | 150 XP | 5 perc | 45 |

### ❄️ Fagylovag (Halállovag-spec) — fagyos közelharc

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Megsemmisítés** | Közeli **8 sebzés** | 3 🍗 | 20 mp | 25 |
| **Fagycsapás** | 6 sebzés + **megfagyaszt** (5 blokk) | 3 🍗 | 25 mp | 27 |
| **Fagykasza** | Körkörös 5 sebzés + megfagyaszt (4 blokk) | 4 🍗 | 35 mp | 30 |
| **Üvöltő Szél** | Körkörös 4 sebzés + megfagyaszt + lassít (5 blokk) | 45 XP | 45 mp | 33 |
| **Könyörtelen Tél** | Körkörös 3 sebzés + megfagyaszt + lassít (5 blokk) | 55 XP | 75 mp | 36 |
| **Dermesztő Suhanás** | 5 sebzés + **megfagyaszt** (12 blokk) | 50 XP | 60 mp | 39 |
| **Fagyoszlop** | Magadra **Erő + sebzéscsökkentés** | 5 🍗 | 90 mp | 42 |
| **Sindragosa Lehelete** | A végső: körkörös 8 sebzés + erős fagyasztás + lassítás (6 blokk) | 150 XP | 5 perc | 45 |

### ⚡ Elemi (Sámán-spec) — távolsági elemi tüzérség

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Lávakitörés** | 7 sebzés + **felgyújt** (16 blokk) | 40 XP | 25 mp | 25 |
| **Földbéklyó-totem** | Lerakott **lassító totem**, ami a közeli ellenfeleket lelassítja | 5 🍗 | 75 mp | 27 |
| **Láncvillám** | Körkörös 5 sebzés + **villám** (6 blokk) | 45 XP | 35 mp | 28 |
| **Földmoraj** | Körkörös 4 sebzés + ellök (6 blokk) | 55 XP | 60 mp | 30 |
| **Elemi Robbanás** | 6 sebzés + megfagyaszt + felgyújt (14 blokk) | 50 XP | 45 mp | 33 |
| **Mennydörgés** | Körkörös 4 sebzés + ellök + **villám** (6 blokk) | 60 XP | 90 mp | 36 |
| **Viharőrző** | Magadra **Erő** buff | 5 🍗 | 75 mp | 39 |
| **Felemelkedés** | A végső: körkörös 8 sebzés + villám + felgyújt (7 blokk) | 150 XP | 5 perc | 45 |

### 🔨 Erősítő (Sámán-spec) — elemekkel feltöltött közelharc

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Viharcsapás** | Közeli **7 sebzés** | 3 🍗 | 20 mp | 25 |
| **Lávacsapás** | 6 sebzés + **felgyújt** (4,5 blokk) | 3 🍗 | 25 mp | 27 |
| **Szélharag-totem** | Lerakott **sebesség-totem**, ami a közeli társakat felgyorsítja | 5 🍗 | 90 mp | 28 |
| **Szélharag** | Magadra **gyorsaság + Erő** | 5 🍗 | 75 mp | 30 |
| **Robbanó Villám** | Körkörös 5 sebzés + **villám** (5 blokk) | 45 XP | 45 mp | 33 |
| **Vad Szellem** | Magadra **gyorsaság + Erő** | 5 🍗 | 90 mp | 36 |
| **Hasítás** | 7 sebzés + erős ellökés (5 blokk) | 5 🍗 | 60 mp | 39 |
| **Földi Fegyver** | Magadra **Erő + sebzéscsökkentés** | 5 🍗 | 90 mp | 42 |
| **Végzetszél** | A végső: körkörös 8 sebzés + villám + gyorsaság + Erő (6 blokk) | 150 XP | 5 perc | 45 |

### 🌪️ Szélfutó (Szerzetes-spec) — kombó-alapú közelharc

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Felkelő Nap Rúgás** | Közeli **7 sebzés** | 3 🍗 | 20 mp | 25 |
| **Repülő Rúgás** | **Hosszú előrelendülés** + sebesség | 3 🍗 | 35 mp | 27 |
| **Düh Ökle** | Körkörös 6 sebzés + lassítás (5 blokk) | 5 🍗 | 45 mp | 30 |
| **Örvénylő Sárkányütés** | Körkörös 6 sebzés + ellök (5 blokk) | 5 🍗 | 60 mp | 33 |
| **Vihar, Föld, Tűz** | Magadra **Erő + Gyorsaság** | 6 🍗 | 90 mp | 36 |
| **Halál Érintése** | **9 sebzés** (5 blokk) | 60 XP | 90 mp | 39 |
| **Derű** | A végső: körkörös 9 sebzés + Gyorsaság + Erő (6 blokk) | 150 XP | 5 perc | 45 |

### 🍺 Sörfőző (Szerzetes-spec) — sör-tank, területi kontroll

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Hordócsapás** | Körkörös 4 sebzés + lassítás (5 blokk) | 4 🍗 | 25 mp | 25 |
| **Tűz Lehelete** | Körkörös 3 sebzés + **felgyújt** (5 blokk) | 45 XP | 45 mp | 27 |
| **Tisztító Sör** | Gyógyulás + sebzéscsökkentés | 40 XP | 45 mp | 30 |
| **Vasbőr Sör** | Sebzéscsökkentés + extra szív | 5 🍗 | 90 mp | 33 |
| **Összecsapás** | Körkörös ellök + lassítás (6 blokk) | 5 🍗 | 75 mp | 36 |
| **Erősítő Sör** | Erős sebzéscsökkentés + regeneráció | 6 🍗 | 120 mp | 39 |
| **Égi Sör** | Extra szívek + sebzéscsökkentés | 6 🍗 | 120 mp | 42 |
| **Niuzao Megidézése** | A végső: körkörös 7 sebzés + erős védelem + extra szívek (6 blokk) | 150 XP | 5 perc | 45 |

### 📿 Fegyelem (Pap-spec) — pajzs és gyógyítás

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Erő Szava: Sugárzás** | A **csapattársaidat gyógyítja** a környéken (8 blokk) | 45 XP | 30 mp | 25 |
| **Vezeklés** | 5 sebzés + **öngyógyítás** (14 blokk) | 45 XP | 35 mp | 28 |
| **Engesztelés** | A csapatnak **erős regeneráció** (8 blokk) | 5 🍗 | 90 mp | 30 |
| **Fájdalomtompítás** | Erős **sebzéscsökkentés** | 6 🍗 | 120 mp | 33 |
| **Elragadtatás** | Extra szívek + regeneráció | 70 XP | 120 mp | 36 |
| **Mennyei Korlát** | Erős extra szívek + sebzéscsökkentés | 7 🍗 | 150 mp | 39 |
| **Evangelizáció** | A végső: a csapatnak regeneráció + extra szívek, magadat gyógyítod (8 blokk) | 150 XP | 5 perc | 45 |

### 🌑 Árnyék (Pap-spec) — árny-sebzés, elme-mágia

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Elme Ostor** | 4 sebzés + lassítás (12 blokk) | 30 XP | 20 mp | 25 |
| **Vámpírérintés** | Sorvadás + **öngyógyítás** (12 blokk) | 2 ❤ | 30 mp | 28 |
| **Árnyszó: Halál** | **8 sebzés** (12 blokk) | 50 XP | 45 mp | 30 |
| **Elmeperzselés** | Körkörös **4 sebzés** (5 blokk) | 55 XP | 60 mp | 33 |
| **Szóródás** | Erős sebzéscsökkentés + gyorsaság | 6 🍗 | 120 mp | 36 |
| **Üresség Kitörése** | Körkörös 5 sebzés + ellök (6 blokk) | 60 XP | 90 mp | 39 |
| **Üresség Árja** | A végső: körkörös 8 sebzés + sorvadás (7 blokk) | 150 XP | 5 perc | 45 |

### 🕸️ Átok (Boszorkánymester-spec) — tartós sebző átkok

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Instabil Átok** | A célpontra **erős sorvadás** (12 blokk) | 2 ❤ | 25 mp | 25 |
| **Agónia** | A célpontra **méreg + sorvadás** (12 blokk) | 3 🍗 | 25 mp | 28 |
| **Ártó Markolat** | 5 sebzés + lassítás (12 blokk) | 40 XP | 30 mp | 30 |
| **Élet Lecsapolása** | 4 sebzés + **öngyógyítás** (12 blokk) | 45 XP | 45 mp | 33 |
| **Romlás Magja** | Körkörös **sorvadás** (5 blokk) | 5 🍗 | 60 mp | 36 |
| **Lélekrothadás** | Körkörös 3 sebzés + erős sorvadás (6 blokk) | 3 ❤ | 90 mp | 39 |
| **Sötét Tekintet** | A végső: körkörös 7 sebzés + erős sorvadás (7 blokk) | 150 XP | 5 perc | 45 |

### 🔥 Pusztítás (Boszorkánymester-spec) — démon-tűz robbanások

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Káoszlövedék** | **8 sebzés** (16 blokk) | 45 XP | 30 mp | 25 |
| **Elhamvasztás** | 5 sebzés + **felgyújt** (14 blokk) | 30 XP | 20 mp | 28 |
| **Lángba Borítás** | 4 sebzés + **felgyújt** (12 blokk) | 35 XP | 30 mp | 30 |
| **Tűzeső** | Körkörös 4 sebzés + felgyújt (6 blokk) | 55 XP | 60 mp | 33 |
| **Árnydüh** | Körkörös 3 sebzés + lassítás (6 blokk) | 5 🍗 | 90 mp | 36 |
| **Kataklizma** | Körkörös 6 sebzés + felgyújt + ellök (6 blokk) | 60 XP | 75 mp | 39 |
| **Infernál Megidézése** | A végső: körkörös 8 sebzés + felgyújt + ellök (7 blokk) | 150 XP | 5 perc | 45 |

### 🗡️ Tombolás (Démonvadász-spec) — fel-sebző közelharc

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Pengetánc** | Körkörös **5 sebzés** (4 blokk) | 4 🍗 | 25 mp | 25 |
| **Szemsugár** | 7 sebzés + **felgyújt** (16 blokk) | 55 XP | 60 mp | 28 |
| **Fel Sortűz** | Körkörös **5 sebzés** (6 blokk) | 55 XP | 75 mp | 30 |
| **Bosszúszomjas Visszavonulás** | **Előrelendülés** + sebesség | 3 🍗 | 45 mp | 33 |
| **Esszencia Törés** | **8 sebzés** (5 blokk) | 55 XP | 60 mp | 36 |
| **Átváltozás** | Magadra **Erő + Gyorsaság + védelem** | 6 🍗 | 120 mp | 39 |
| **A Vadászat** | A végső: előrelendülés + körkörös 8 sebzés + gyorsaság (6 blokk) | 150 XP | 5 perc | 45 |

### 🛡️ Bosszú (Démonvadász-spec) — fel-tank

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Lélekhasítás** | Körkörös 4 sebzés + **öngyógyítás** (5 blokk) | 4 🍗 | 25 mp | 25 |
| **Démontüskék** | **Sebzéscsökkentés** | 4 🍗 | 60 mp | 28 |
| **Tüzes Bélyeg** | 3 sebzés + felgyújt + sebzéscsökkentés (10 blokk) | 45 XP | 60 mp | 30 |
| **Láng Pecsétje** | Körkörös 4 sebzés + **felgyújt** (5 blokk) | 45 XP | 45 mp | 33 |
| **Fel Pusztítás** | Körkörös 6 sebzés + **öngyógyítás** (5 blokk) | 60 XP | 90 mp | 36 |
| **Átváltozás (Bosszú)** | Erős sebzéscsökkentés + extra szívek | 7 🍗 | 150 mp | 39 |
| **Fel Penge** | A végső: körkörös 8 sebzés + erős öngyógyítás + védelem (6 blokk) | 150 XP | 5 perc | 45 |

### 🔥 Perzselés (Sárkányidéző-spec) — tűz-/azúr-sebzés

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Máglya** | Körkörös 5 sebzés + **felgyújt** (5 blokk) | 45 XP | 30 mp | 25 |
| **Szétbontás** | **7 sebzés** (16 blokk) | 40 XP | 25 mp | 28 |
| **Eternity Surge** (Örökkévalóság Hulláma) | Körkörös **6 sebzés** (6 blokk) | 55 XP | 60 mp | 30 |
| **Robbanó Csillag** | **6 sebzés** (16 blokk) | 50 XP | 45 mp | 33 |
| **Tűzvihar** | Körkörös 4 sebzés + felgyújt (6 blokk) | 60 XP | 75 mp | 36 |
| **Sárkánydüh** | Magadra **Erő + Gyorsaság** | 6 🍗 | 90 mp | 39 |
| **Örök Lehelet** | A végső: körkörös 9 sebzés + felgyújt (7 blokk) | 150 XP | 5 perc | 45 |

### 💚 Megőrzés (Sárkányidéző-spec) — smaragd-álom gyógyítás

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Álomlehelet** | A **csapattársaidat gyógyítja** a környéken (8 blokk) | 5 🍗 | 60 mp | 25 |
| **Szellemvirág** | A csapatot gyógyítja + magadat (6 blokk) | 50 XP | 45 mp | 28 |
| **Visszafordítás** | Gyógyulás + regeneráció | 40 XP | 30 mp | 30 |
| **Visszhang** | Regeneráció + extra szívek | 5 🍗 | 60 mp | 33 |
| **Időbeli Anomália** | Erős **extra szívek** | 6 🍗 | 90 mp | 36 |
| **Stázis** | Erős **sebzéscsökkentés** | 6 🍗 | 120 mp | 39 |
| **Visszatekerés** | A végső: a csapatnak regeneráció + extra szívek, magadat gyógyítod (8 blokk) | 150 XP | 5 perc | 45 |

### 🌳 Védelmező (Druida-spec) — medve-tank

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Roncsolás** | Közeli **6 sebzés** | 3 🍗 | 20 mp | 25 |
| **Tövispajzs** | Magadra **extra szívek** | 4 🍗 | 45 mp | 27 |
| **Vaskéreg** | Erős **sebzéscsökkentés** | 5 🍗 | 60 mp | 30 |
| **Védő Csapás** | Körkörös 4 sebzés + ellök (4 blokk) | 4 🍗 | 30 mp | 33 |
| **Borzas Bunda** | Sebzéscsökkentés + regeneráció | 5 🍗 | 75 mp | 36 |
| **Eszeveszett Regen** | Gyógyulás + erős regeneráció | 60 XP | 90 mp | 39 |
| **Túlélő Ösztön** | Nagyon erős **sebzéscsökkentés** | 7 🍗 | 150 mp | 42 |
| **Megtestesülés: Medve** | A végső: erős védelem + extra szívek + Erő | 150 XP | 5 perc | 45 |

### 🌷 Helyreállító (Druida-spec) — természet-gyógyítás

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Megfiatalítás** | Magadra **erős regeneráció** | 35 XP | 20 mp | 25 |
| **Gyógyító Érintés** | **4 szívet gyógyulsz** | 45 XP | 30 mp | 27 |
| **Gyorsgyógyír** | Gyógyulás + regeneráció | 45 XP | 30 mp | 30 |
| **Életvirágzás** | A **csapattársaidat gyógyítja** (8 blokk) | 5 🍗 | 45 mp | 33 |
| **Természet Gyógyíre** | Gyógyulás + sebzéscsökkentés | 50 XP | 60 mp | 36 |
| **Vad Virágzás** | A csapatnak **erős regeneráció** (8 blokk) | 6 🍗 | 90 mp | 39 |
| **Nyugalom** | A csapatnak regeneráció + extra szív (8 blokk) | 8 🍗 | 3 perc | 42 |
| **Virágzás** | A végső: a csapatnak nagyon erős regeneráció, magadat gyógyítod (8 blokk) | 150 XP | 5 perc | 45 |

### 🌊 Hullámhívó (Sámán-spec) — víz-/szellem-gyógyítás

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Gyógyhullám** | **3,5 szívet gyógyulsz** | 40 XP | 25 mp | 25 |
| **Örvénylés** | Gyógyulás + regeneráció | 35 XP | 20 mp | 27 |
| **Lánc-gyógyítás** | A **csapattársaidat gyógyítja** (8 blokk) | 5 🍗 | 45 mp | 30 |
| **Földpajzs** | Extra szív + sebzéscsökkentés | 5 🍗 | 75 mp | 33 |
| **Gyógyeső** | A csapatnak **erős regeneráció** (8 blokk) | 6 🍗 | 90 mp | 36 |
| **Szellemkötés** | A csapatnak **sebzéscsökkentés** (8 blokk) | 6 🍗 | 90 mp | 39 |
| **Ősi Vezetés** | Gyógyulás + erős regeneráció | 70 XP | 120 mp | 42 |
| **Szellemár** | A végső: a csapatnak nagyon erős regeneráció, magadat gyógyítod (8 blokk) | 150 XP | 5 perc | 45 |

### 🌫️ Ködszövő (Szerzetes-spec) — köd-gyógyítás

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Csillapító Köd** | Gyógyulás + regeneráció | 35 XP | 20 mp | 25 |
| **Megújító Köd** | Magadra **erős regeneráció** | 35 XP | 25 mp | 27 |
| **Beborító Köd** | A **csapattársaidat gyógyítja** (8 blokk) | 4 🍗 | 30 mp | 30 |
| **Sheilun Áldása** | **4 szívet gyógyulsz** + regeneráció | 70 XP | 120 mp | 33 |
| **Mennydörgő Tea** | Magadra **gyorsaság + regeneráció** | 5 🍗 | 75 mp | 36 |
| **Esszencia-forrás** | A csapatnak **erős regeneráció** (8 blokk) | 6 🍗 | 90 mp | 39 |
| **Életgubó** | Erős **extra szívek** | 6 🍗 | 120 mp | 42 |
| **Feltámasztás** | A végső: a csapatnak nagyon erős regeneráció, magadat gyógyítod (8 blokk) | 150 XP | 5 perc | 45 |

### ✝️ Védő (Paplovag-spec) — szent tank

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Bosszúálló Pajzs** | 5 sebzés + ellök (14 blokk) | 4 🍗 | 30 mp | 25 |
| **Igazak Pajzsa** | Sebzéscsökkentés + extra szív | 4 🍗 | 25 mp | 27 |
| **Igazak Pörölye** | Körkörös **4 sebzés** (4 blokk) | 4 🍗 | 25 mp | 30 |
| **Áldott Pöröly** | 4 sebzés + lassítás (8 blokk) | 40 XP | 30 mp | 33 |
| **Megszentelt Föld** | Körkörös 3 sebzés + sebzéscsökkentés (5 blokk) | 5 🍗 | 45 mp | 36 |
| **Buzgó Védő** | Sebzéscsökkentés + regeneráció | 6 🍗 | 90 mp | 39 |
| **Ősi Királyok Őre** | Nagyon erős **sebzéscsökkentés** | 7 🍗 | 150 mp | 42 |
| **Végső Kiállás** | A végső: erős sebzéscsökkentés + extra szívek | 150 XP | 5 perc | 45 |

---

### 🧟 Szentségtelen (Halállovag-spec, csak Kitaszított) — élőholt ragály és szolgák

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Gennyes Csapás** | 6 sebzés + **sorvadás** (4,5 blokk) | 3 🍗 | 22 mp | 25 |
| **Járvány** | Körkörös 3 sebzés + méreg (5 blokk) | 40 XP | 45 mp | 27 |
| **Ghúl-szolga** | 2 ghúl idézése (40 mp-ig harcolnak) | 60 XP | 3 perc | 30 |
| **Halálörvény** | 7 sebzés (6 blokk) — az árát **vérben** fizeted | 3 ❤ | 30 mp | 33 |
| **A Holtak Szorítása** | Körkörös 2 sebzés + erős lassítás (4,5 blokk) | 35 XP | 40 mp | 36 |
| **Ragály** | Körkörös 5 sebzés + sorvadás (6 blokk) | 50 XP | 75 mp | 39 |
| **A Holtak Serege** | A végső: 6 élőholt harcos idézése (40 mp-ig) | 110 XP | 5 perc | 45 |

Ezen felül a Szentségtelennek **állandó ghúl-társa** van (rituálé-idézés — lásd a
[Specializációk](#6-specializációk) oldal Társ-részét).

### 🦴 Csontpap (Pap-spec, csak Kitaszított) — a Néma Királynő liturgiája

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Csontforrasztás** | Szövetséges kör: regeneráció + magadra 3 gyógyítás (4 blokk) | 30 XP | 25 mp | 25 |
| **Szívó Sugár** | 4 sebzés + magadra 3 gyógyítás (7 blokk) | 3 🍗 | 30 mp | 27 |
| **Csont-oltalom** | Szövetséges kör: sebzéscsökkentés (5 blokk) | 35 XP | 60 mp | 30 |
| **A Királynő Siráma** | Körkörös 3 sebzés + gyengítés (5 blokk) | 40 XP | 45 mp | 33 |
| **Vér-tized** | Szövetséges kör: erős regeneráció (6 blokk) — az árát **vérben** fizeted | 4 ❤ | 75 mp | 36 |
| **A Sír Csendje** | Körkörös 4 sebzés + sorvadás (5 blokk) | 50 XP | 90 mp | 39 |
| **Utolsó Kenet** | A végső: szövetséges kör felszívódás-pajzs + magadra 8 gyógyítás (8 blokk) | 90 XP | 4 perc | 45 |

### ☣️ Pestishozó (Orgyilkos-spec, csak Kitaszított) — pestis és sorvasztás

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Pestis-vágás** | 5 sebzés + méreg (4,5 blokk) | 3 🍗 | 20 mp | 25 |
| **Fertőzés** | Körkörös 2 sebzés + erős méreg (4 blokk) | 35 XP | 40 mp | 27 |
| **Fekete Kelések** | 3 sebzés + erős sorvadás (6 blokk) | 40 XP | 50 mp | 30 |
| **Miazma** | Körkörös 3 sebzés + lassítás (5 blokk) | 45 XP | 60 mp | 33 |
| **Gennyedő Sebek** | 6 sebzés + sorvadás (5 blokk) — az árát **vérben** fizeted | 3 ❤ | 75 mp | 36 |
| **Pestis-szél** | Körkörös 4 sebzés + méreg (7 blokk) | 55 XP | 90 mp | 39 |
| **Fekete Halál** | A végső: körkörös 7 sebzés + erős sorvadás (6 blokk) | 150 XP | 5 perc | 45 |

### 👿 Demonológus (Boszorkánymester-spec, csak Kitaszított) — a Légió idézője

| Képesség | Mit csinál | Költség | Várakozás | Szint |
|---|---|---|---|---|
| **Démontűz-lövedék** | 5 sebzés + meggyújt (7 blokk) | 3 🍗 | 25 mp | 25 |
| **Imp-raj** | 3 imp idézése (30 mp-ig harcolnak) | 60 XP | 3 perc | 27 |
| **Magma-szolga** | Izzó behemót idézése (45 mp-ig) | 70 XP | 3 perc 20 mp | 30 |
| **Démonbőr** | Magadra erős sebzéscsökkentés | 45 XP | 90 mp | 33 |
| **Káosz-láng** | Körkörös 5 sebzés + meggyújt (5 blokk) | 50 XP | 60 mp | 36 |
| **Áldozati Paktum** | Körkörös 6 sebzés (6 blokk) — az árát **vérben** fizeted | 5 ❤ | 2 perc | 39 |
| **A Légió** | A végső: 5 démon idézése (40 mp-ig) | 110 XP | 5 perc | 45 |

Ezen felül a Boszorkánymesternek (kaszt-szinten) **állandó démon-familiárisa** van
(rituálé-idézés — lásd a [Specializációk](#6-specializációk) oldal Társ-részét).

### ★ Talent-képességek (aktív talentekből)

Ezek **nem a szintedből**, hanem az **aktív talentekből** oldódnak fel (lásd
[Talentek](#7-talentek-talent-fa)), és **bármelyik kaszttal** használhatók a Lélekkapocsral:

| Képesség | Mit csinál | Költség | Várakozás | Honnan |
|---|---|---|---|---|
| **Talentum Lendület** | Erős önbuff: **Erő + Védelem + Regeneráció + extra szív** ~12 mp-re | 100 XP | 5 perc | „Felemelkedés" capstone talent |

---

#### ✧ Mágia-sebzés és Rúnavért

A varázslatok saját **mágia-sebzésként** ütnek — a vanília páncél mellett létezik ellenük egy
külön védelem: a **Rúnavért** enchant. A rúnaírnokok (enchanter 40) **Rúnavért-tekercset**
készítenek (enchantelt könyv), amit **üllőn** vihetsz a páncélodra; darabonként és szintenként
csökkenti a bejövő spell-sebzést (max 3. szint — két azonos szintű könyv üllőn kombinálható).
A vanília sebzés ellen nem véd — ez kifejezetten a mágia countere.

**Iskolák:** minden varázslat egy **iskolához** tartozik (tűz, fagy, szent, árnyék, természet,
vihar, káosz, ősmágia) — alapból a kaszt szabja meg (pl. a Paplovag szent, a Halállovag fagy,
a Boszorkánymester káosz mágiával üt), de spellenként is testre szabható. A **Fagypáncél** és a
**Főnixtoll** signature-vért nem csak dísz: a saját iskolájuk (fagy-, ill. tűzmágia) ellen
külön ellenállást adnak, ami a Rúnavérttel összeadódik.

### Lélek-kovács és rúna-affinitás ☠◆

- **Nekromanta — `/soulforge`:** a lélekszilánkjaidat tartósan a sereged erejébe fektetheted:
  Élet, Sebzés és Létszám ág, áganként 5 rang. A magas Létszám-rang extra idézés-slotot ad —
  a sereged szó szerint nő a szezonnal!
- **Varázsló — rúnaíró affinitás:** a Varázsló „olvassa" a rúnákat: minden rúna-hatás
  **duplán** érvényesül nála, és csak ő készítheti el a **Visszhang Rúnáját** (találatkor
  eséllyel visszhang-csapás).

### Pakt és Sárkánytojás — kaszt-végjáték 🐉

- **Boszorkánymester — Pakt-oltár:** építs pakt-oltárt (SOUL_LANTERN mag), és az Első Csend
  Szilánkjáért cserébe a max Lélekerőd **tartósan +20%**. Egyszeri és visszavonhatatlan —
  a Kárhozat nem alkuszik kétszer.
- **Sárkányidéző — Sárkánytojás-töredék:** új relikvia; amíg a tiéd ÉS Sárkányidéző vagy,
  az Eszencia-medred +10%-kal tágabb. Másnak csak hideg kő — neked végjáték-cél.

---

## 6. Specializációk ✅

A **specializáció** a kasztod „kiteljesedése": egy irány, amit a **25. szinttől** választhatsz
az elsődleges kasztodnak. Ez nyitja meg a legerősebb, legtematikusabb képességeket (25–45.
szint között).

Hogyan? `/profile` → **Specializáció** menü → kattints a választott irányra. (Vagy parancs:
`/spec list`, `/spec choose <azonosító>`.) A menü mindig csak **a te kasztod** irányait mutatja,
és kiírja, ha valamihez még nem teljesíted a feltételt.

Összesen **35 specializáció** van. A **spec dönti el a szerepedet** (🗡️ közelharci DPS,
🏹 távolsági DPS, ✨ caster, 🛡️ tank, ➕ gyógyító) — vagyis milyen stílusban a leghatékonyabb:

| Kaszt | Specializációk (szerep) |
|---|---|
| 🧙 Varázsló | 🌊 **Elementalista** — tűz/jég/villám (caster) • 💀 **Nekromanta** — holtak/lélek (caster) |
| ⚔️ Harcos | 🩸 **Berserker** — támadás/vér (DPS) • 🛡️ **Védelmező** — pajzs/buff (tank) |
| 🏹 Íjász | 🎯 **Mesterlövész** — pontos lövések (táv. DPS) • 🐺 **Vadmester** — állat-társak (táv. DPS) |
| 🗡️ Orgyilkos | ☠️ **Méregkeverő** — mérgek (DPS) • 👻 **Fantom** — árnyék/félelem (DPS) • ☣️ **Pestishozó** — pestis/sorvasztás (DPS, csak Kitaszított) |
| 🐻 Druida | 🐾 **Vadőr** (DPS) • 🌙 **Holdjós** (caster) • 🌳 **Védelmező** — kéreg (tank) • 💚 **Helyreállító** (gyógyító) |
| 🔆 Paplovag | ☀️ **Szentlélek** (gyógyító) • ⚖️ **Megtorló** (DPS) • 🛡️ **Védő** (tank) |
| 💀 Halállovag | 🩸 **Vérlovag** (tank) • ❄️ **Fagylovag** (DPS) • 🧟 **Szentségtelen** — élőholt ragály + ghúl (DPS, csak Kitaszított) |
| 🌊 Sámán | ⚡ **Elemi** (caster) • 🔨 **Erősítő** (DPS) • 🌊 **Hullámhívó** (gyógyító) |
| ☯️ Szerzetes | 💨 **Szélfutó** (DPS) • 🍺 **Sörfőző** (tank) • 🌫️ **Ködszövő** (gyógyító) |
| ✝️ Pap | 🙏 **Fegyelem** (gyógyító) • 🌑 **Árnyék** (caster) • 🦴 **Csontpap** — csont-liturgia (gyógyító, csak Kitaszított) |
| 😈 Boszorkánymester | 🍂 **Átok** (caster) • 🔥 **Pusztítás** (caster) • 👿 **Demonológus** — démon-idézés (caster, csak Kitaszított) |
| 👁️ Démonvadász | 💥 **Tombolás** (DPS) • 🛡️ **Bosszú** (tank) |
| 🐉 Sárkányidéző | 🔥 **Perzselés** (caster) • 💧 **Megőrzés** (gyógyító) |

### Feltételek

- **Szint 25** az elsődleges kasztban.
- A **sötét specek** (💀 Nekromanta, 🧟 Szentségtelen, 🦴 Csontpap, ☣️ Pestishozó, 👿 Demonológus)
  csak **Kitaszítottként (Sötét/dark frakció) + bűnös (sinner) állapottal** választhatók; a
  Nekromantához ezen felül a **Sötét Beavatás** küldetés teljesítése is kell.
- Ha **elhagyod a Kitaszítottakat** (vagy a vezeklés-lánccal letisztulsz), a sötét
  specializációd **elveszik** — új specet kell választanod.

### Respec (meggondolás)

Ha mégis más irányt szeretnél, a Specializáció menü **Respec** gombjával (vagy
`/spec respec class`) **visszaváltod** a specializációd. Ez a **frakcióvalutádba kerül**
(alapból 100), és a specializációhoz kötött **talentpontjaid automatikusan visszatérülnek**,
hogy újra elköltsd őket. Utána új irányt választhatsz.

> A respec **elveszi a régi specializáció spelljeit** is — a specek nem halmozhatók. Ami a
> kaszt-szintedből vagy egy talentből jár, azt megtartod: minden feloldás megjegyzi, honnan
> kapta a spellt, és csak a saját forrása vonhatja vissza.

> A szakmáknak **is** vannak specializációi, ugyanígy működnek (lásd a [Szakmák](#8-szakmák)
> oldalt). Egyszerre egy kaszt-spec **és** egy szakma-spec lehet aktív.

### 🐾 Társ-rendszer (Vadmester, Nekromanta, Szentségtelen, Boszorkánymester)

A **Vadmester** és a **Nekromanta** szintet lépő **társat** tarthat — és ez **bármilyen
mob** lehet, nem csak farkas vagy zombi!

1. Írd be: `/pet item` — kapsz egy **befogó eszközt**:
   - 🐺 **Vadmester → Ősi Kötés Póráza**: bármely **nem ellenséges** állatra/lényre jobb katt
     (pl. tehén, róka, ló, papagáj, delfin…).
   - 💀 **Nekromanta → Sötét Paktum-tekercs**: bármely **ellenséges / élőholt** mobra jobb katt
     (pl. zombi, csontváz, pók, creeper…).
2. **Jobb katt** a kiszemelt lényen a befogó eszközzel — társaddá fogadod, az eszköz elhasználódik.
3. `/pet name <név>` — adj nevet neki; `/pet summon` újra előhívja, `/pet dismiss` elküldi.

A társ **típusa, szintje, neve és XP-je** megmarad (a gazdád ölései szintezik), és **követ** téged.

#### 🌒 Rituálé-idézés (Szentségtelen és Boszorkánymester)

A 🧟 **Szentségtelen** és a 😈 **Boszorkánymester** társa **nem befogható — rituálé idézi**:

1. Szerezd meg a kelléket (ritka zsákmány, csak a megfelelő szerepnek esik):
   - **Nyughatatlan Szív** — élőholtakból (zombi, csontváz, phantom), a Szentségtelennek.
   - **Démon-pecsét** — boszorkákból és illagerekből, a Boszorkánymesternek.
2. **Éjjel**, a kellékkel a kezedben **jobb katt** — a rituálé elfogyasztja a kelléket,
   és megköti a társat.
3. A társ **formája a szintjével fejlődik**: a Szentségtelené Ghúl → **Csontszolga** (15. szint)
   → **Förtelem** (25. szint); a Boszorkánymesteré Imp → **Tűz-démon** (15) → **Magma-behemót** (25).
   A magasabb forma **új rituálét (új kelléket)** kér.
4. Az idézett társ **erő-prémiumot** kap (+5 szintnyi statot) — a nehezebb beszerzés ellentételezése.

#### 🎛️ Társ-GUI és állásmódok

A `/pet` parancs (vagy a `/menu` Társ-csempéje) **kattintható vezérlőpultot** nyit:
idézés, elbocsátás, átnevezés, állásmód-gombok és a Társvért állapota egy helyen.

- **Állásmódok:** **Támadás** (harcol és megvéd) / **Passzív** (csak követ) / **Maradj**
  (helyben vár). Váltás a GUI-ból, a `/pet stance <aktiv|passziv|marad>` paranccsal, vagy
  **sunyítás + jobb katt** a társadon.
- **Halálnak tétje van:** ha a társad elesik, **2 percig** nem idézheted újra.
- A kaszt-talentjeid **max-életerő bónuszának fele** a társadra is átszáll.

#### 🛡️ Társvért

Ritka zsákmány szörnyekből (csak társ-tartó kasztoknak esik): **jobb katt vele a saját
társadon** — a társ **+páncélt és +életerőt** kap. A vért a gazdához kötődik: a társ
újraidézésekor is rajta marad.

#### A társ harcol is — bármilyen lény legyen az

A társad **rendes harci pet**, függetlenül attól, milyen mob: nem a mob saját esze irányítja,
hanem a játék **maga viszi a célpontra és üti meg** — így egy befogott tehén vagy róka is verekszik!

- **Segít támadni:** ha megütsz egy lényt, a társad ráveti magát.
- **Megvéd:** ha téged támadnak, a társad a támadóra fordul.
- **Magától véd:** a közeli ellenséges mobokra magától rátámad.
- **Parancsok:** **sunyíts (Shift) + jobb katt** a társadon, hogy váltogasd az állását:
  **Támadás → Passzív → Maradj**. (Passzívan nem harcol, „Maradj" állásban egy helyben vár.)
- Soha nem támad **téged** vagy a **saját társaidat**.

A társ sebzése és életereje a **szintjével nő**. A nem szelídíthető társakat a játék
**automatikusan melléd teleportálja**, ha túl messze kerülnek.

### Melyik képességet mikor kapom meg?

Minden specializációnak 9–12 saját képessége van, amelyek a **25. és 45. szint** között
fokozatosan oldódnak fel. A pontos listát (mit tud, mennyibe kerül, hányadik szinten) a
[Képességek](#5-képességek-varázslatok) oldal „Specializációs képességek" része tartalmazza.

---

---

## 7. Talentek (talent-fa) ✅

A **talentek** apró, **örökös erősítések**, amelyeket **talentpontokból** veszel meg. A
szintezéssel pontokat gyűjtesz, és egy **fán** (felülről lefelé) költöd el őket. Megnyitás:
`/profile` → **Talentek**.

### Honnan jön a talentpont?

- **Kaszt-pont:** minden **5 kaszt-szint** után **1 pont** (a kasztod szintjéből).
- **Szakma-pont:** minden **10 szakma-szint** után **1 pont** (az **összes** szakmád szintje
  összeadódik — a másodlagosak is!).

A két pontfajta külön: kaszt-pontot csak a kaszt-fába, szakma-pontot csak a szakma-fába tehetsz.

### Hogyan olvasd a fát?

- A fa **felülről lefelé** épül. A felső sor (1. szint / „tier") **azonnal** elérhető.
- A lentebbi talentek **zárolva** vannak (🔒), amíg a **fölöttük lévő szülő-talentbe** nem teszel
  legalább 1 pontot. A zárolt talent kiírja, melyik a szülője.
- Csak azok a talentek látszanak, amik **hozzád illenek** (a kasztodhoz, specializációdhoz vagy
  szakmádhoz tartoznak).
- Egy talentnek több **rangja** lehet — minden pont +1 rang, egy határig (max rang).

#### Különleges talent-típusok
- ⚔️ **Egymást kizáró ágak:** néhány talent **kizárja a testvérét** — ha az egyikbe pontot
  teszel, a másik zárolódik. Választanod kell! Pl. **Behemót** (+élet) **vagy** **Hadúr**
  (+sebzés) — nem lehet mindkettő.
- 👑 **Csúcs-talent (capstone):** erős talent a fa „csúcsán", ami csak akkor nyílik, ha már
  **elég pontot elköltöttél** abban a fában (pl. a **Felemelkedés** 10 elköltött pont után).
- ★ **Aktív talent:** nem csak passzív bónusz — **képességet old fel** a Lélekkapcsodban!
  Pl. a **Felemelkedés** feloldja a **Talentum Lendület** ultit (rövid, erős önbuff). Ezek a
  talentek `★` jellel látszanak.

> Ha **respec-elsz** egy specializációt, a hozzá kötött talentpontok **visszajárnak** (és az
> aktív talent által adott képesség is lekerül). A zárolt talentek mindig kiírják, **miért**
> zártak (szülő kell / kizárt ág / kevés elköltött pont).

---

### 🗡️ Kaszt talent-fa

#### 1. szint — mindenkinek (nincs feltétel)

| Talent | Mit ad rangonként | Max rang |
|---|---|---|
| **Életerő** | +2 életpont (=1 szív) | 5 |
| **Fürgeség** | gyorsabban jársz | 5 |
| **Erő** | +0,5 sebzés | 5 |
| **Tudásszomj** | +5% kaszt-XP minden öléskor | 4 |
| **Arkán Hatalom** | +2% spell-erő (a dinamikus skálázás rétegében) | 5 |

#### 2. szint — kaszt-kötött (előbb a szülőt kell fejleszteni)

| Talent | Kinek | Szülő | Mit ad | Max rang |
|---|---|---|---|---|
| **Fegyvermesteri Fokozat** | Harcos | Erő | +0,5 sebzés | 3 |
| **Fürge Vadász** | Íjász | Fürgeség | gyorsabb mozgás | 3 |

#### 3. szint — specializáció-kötött (előbb a szülőt kell fejleszteni)

| Talent | Specializáció | Szülő | Mit ad | Max rang |
|---|---|---|---|---|
| **Elemi Ráhangolódás** | Elementalista | Erő | +0,5 sebzés | 4 |
| **Lélekpaktum** | Nekromanta | Életerő | +2 életpont | 4 |
| **Brutalitás** | Berserker | Erő | +1 sebzés | 3 |
| **Rendíthetetlen** | Védelmező | Életerő | +3 életpont | 3 |
| **Szélvész Léptek** | Mesterlövész | Fürgeség | gyorsabb mozgás | 3 |
| **Falkavezér** | Vadmester | Életerő | +2 életpont | 4 |
| **Vipera Reflexek** | Méregkeverő | Fürgeség | gyorsabb mozgás | 4 |
| **Kísértetjárás** | Fantom | Fürgeség | gyorsabb mozgás | 3 |

---

### ⚒️ Szakma talent-fa

#### 1. szint — mindenkinek

| Talent | Mit ad rangonként | Max rang |
|---|---|---|
| **Szorgalom** | +10% szakma-XP minden munkánál | 5 |
| **Kitartás** | +1 életpont | 3 |

#### 2. szint — szakma-kötött (előbb a szülőt kell fejleszteni)

| Talent | Kinek | Szülő | Mit ad | Max rang |
|---|---|---|---|---|
| **Bányász Állóképesség** | Bányász | Kitartás | +1 életpont | 2 |
| **Arkán Belátás** | Bűvölő | Szorgalom | +5% kaszt-XP | 2 |

> Tipp: a **Tudásszomj** (kaszt-XP) és a **Szorgalom** (szakma-XP) korán befektetve **gyorsítja
> az egész fejlődésedet** — minél előbb veszed fel őket, annál hamarabb szintezel utána.

---

---

## 8. Szakmák ✅

A szakmák a „munkáid" a világban: bányászás, favágás, kovácsolás, főzés és társaik. Minél
többet csinálod őket, annál magasabb szintre érsz bennük.

### A szabályok dióhéjban

- **Kétféle fő szakmád lehet:** **1 gyűjtögető** (kifelé hozod az alapanyagot) **+ 1 készítő**
  (tárgyat gyártasz belőle). Ezeket a `/profile` → **Szakma** menüből tanulod meg.
- **A másodlagos szakmákat (Halász, Szakács) mindenki ismeri** automatikusan — nem foglalnak
  helyet.
- **XP-t csak akkor kapsz, ha tényleg „űzöd" a szakmát.** Pl. érc bányászásáért csak akkor jár
  Bányász-XP, ha a Bányász a tanult gyűjtögető szakmád. (A Halász/Szakács XP mindig jár, mert
  azokat mindenki ismeri.)
- **Szintezés:** a következő szint ára `100 + (előző szintek száma × 15)` XP. Tehát:
  - 1 → 2 szint: **100 XP**
  - 2 → 3 szint: **115 XP**
  - 3 → 4 szint: **130 XP** … és így tovább. **Max szint: 50.**
- **Fokozatok:** a szinted egyben céh-fokozat is — **Inas** (1–9), **Segéd** (10–19),
  **Legény** (20–29), **Mester** (30–39), **Nagymester** (40–49), **Legendás Mester** (50).
  A fokozatod a szakma-menüben is látszik, és szint-/fokozatlépéskor üzenet + hang jelzi.
- **Szakmaváltáskor a szinted megmarad** (külön tárolódik szakmánként), így később vissza is
  tanulhatod ott, ahol abbahagytad.

---

### 🧺 Gyűjtögető szakmák (válassz 1-et)

| Szakma | Mivel kapsz XP-t | Mennyi XP | Példa |
|---|---|---|---|
| ⛏ **Bányász** | **Érc kibányászása** (bármilyen `_ore`, és az ősi törmelék) | **5 XP / érc** | 20 érc = 1. szintlépés (100 XP) |
| 🌿 **Gyógynövényész** | **Virág letörése**, **érett termény** betakarítása (búza, répa, krumpli, cékla, nether wart, édes bogyó, kakaó) | **3 XP / darab** | ~34 termény = 1. szintlépés |
| 🪓 **Favágó** | **Rönk (fatörzs) kivágása** | **2 XP / rönk** | 50 rönk = 1. szintlépés |

> **Fontos:** csak az **érett** termény ad XP-t (amikor már teljesen kinőtt). A zöld, fiatal
> növény letörése nem számít.

### 🔨 Készítő szakmák (válassz 1-et)

| Szakma | Mivel kapsz XP-t | Mennyi XP |
|---|---|---|
| ⚒ **Kovács** | **Páncél/pajzs craftolása** (sisak, mellvért, nadrág, csizma, pajzs) | **8 XP / darab** |
| ⚒ **Kovács** | **Kovácsolóasztalnál (smithing) fejlesztés** (pl. netherit-fejlesztés) | **15 XP / alkalom** |
| ⚗ **Alkimista** | **Megfőzött bájital kivétele** a főzőállványból (sima/dobható/elnyúló) | **12 XP / bájital** |
| ✨ **Bűvölő** | **Tárgy elvarázsolása** a bűvölőasztalnál | **10 XP / alkalom** |

### 🎣 Másodlagos szakmák (mindenki ismeri)

| Szakma | Mivel kapsz XP-t | Mennyi XP |
|---|---|---|
| 🐟 **Halász** | **Hal kifogása** horgászással | **4 XP / fogás** |
| 🍲 **Szakács** | **Megsült étel kivétele** a kemencéből | **3 XP / étel** |

> Tipp: a Halász és a Szakács XP **mindig** jár, bármelyik kasztot/szakmát is választottad —
> ezért ezekkel mindig tudsz haladni a háttérben.

---

### Szakma-specializációk (25. szinttől)

Amikor egy szakmád eléri a **25. szintet**, **specializálódhat** — ezt a `/profile` →
**Specializáció** menüből választod ki (szakmánként **2 irány**). Ezek tematikus „mesterfokok":

| Szakma | Specializációk |
|---|---|
| Bányász | Aranyásó • Vájármester |
| Gyógynövényész | Botanikus • Természetbúvár |
| Favágó | Erdész • Ácsmester |
| Kovács | Fegyverkovács • Páncélkovács |
| Alkimista | Főzetmester • Transzmutátor |
| Bűvölő | Rúnamester • Arkanista |
| Halász | Horgászmester • Kincsvadász |
| Szakács | Séf • Hentes |

> Egyszerre **egy** szakma-specializációd lehet. Ha váltanál, a Specializáció menü **Respec**
> gombjával tudod visszacsinálni (a frakcióvalutádba kerül).

### Recept-könyv 📖 (`/profession recipes`)

A szakmáidnak **teljes recept-listája** van, WoW-módra. Nyisd meg a **`/profession recipes`**
paranccsal (vagy `/menu` → **Recept-könyv**): egy lapozható könyvben látod a szakmáid összes
receptjét — a **tanultakat zölddel**, a **zároltakat szürkén** (odaírja, mi kell: szint vagy
tervrajz), és minden recepthez a **hozzávalókat** (megvan/hiányzik). Egy kattintással **craftolsz**,
ha minden feltétel megvan (a hozzávalók a táskádból fogynak).

**Craftolni = fejlődni (WoW-módra):** a recept-könyvi craft **szakma-XP-t is ad** — minél
magasabb szintű a recept, annál többet. De vigyázz: a **rég kinőtt receptek „kiszürkülnek”** —
ha a szinted 10+ szinttel a recept felett jár, már csak fél XP-t, 20+ felett semmit sem ad.
Mindig a szintedhez közeli recepteket készítsd a leggyorsabb fejlődésért!

**Szintlétra:** a bundled katalógusban **438 recept** van: a Bányász, Gyógynövényész,
Favágó, Alkimista és Halász 50-50, a Kovács 54, a Bűvölő 62, a Szakács 72
receptet kap. A receptek az 1–50. szint között oszlanak el. A magasabb receptek
bolti kellékeket és ritka unique anyagokat
kérnek, az 50. szintű **céh-mesterművek** (pl. *A Mélység Szíve*, *A Bokic Áldása*, *A Kapu
Lakomája*, *Az Erdő Szíve*) pedig az *Első Csend Szilánkját* is.

**Kétféleképp tanulsz receptet:**
- 📈 **Szintre:** a receptek nagy része a szakma adott szintjén magától megnyílik.
- 📜 **Tervrajzból:** a ritka/erős receptekhez egy **tervrajz** (Knowledge Book) kell — ezt
  **NPC-boltból** veheted, **szörnyek ejthetik**, vagy admin adhatja. **Jobb katt** a tervrajzon =
  megtanulod a receptet (utána már a recept-könyvből craftolható, ha megvan hozzá a szint).

**Egyedi köztes alapanyagok:** egyes szakmák **egyedi alapanyagot** gyártanak (pl. *Tiszta
Vasesszencia*, *Rúnapor*, *Jégvirág-por*, *Parázsmag*, *Viharkvarc*, *Mélységi Borostyán*), amit a magasabb receptek hozzávalóként kérnek — ezek nem sima itemek,
hanem szakma-specifikus félkész anyagok (mint WoW-ban a „Spirit Dust"). A recept-könyv jelzi, ha
egy recepthez egyedi alapanyag kell (kék színnel).

### Mestermű receptek 🛠️

Magas szinten a szakmák **különleges tárgyakat** craftolhatnak, amiket **csak az adott szakma +
szint** birtokában lehet elkészíteni (a recept látszik, de a végeredmény üres, amíg nincs meg a
feltétel). Néhány mestermű (alapból **15. szinttől**):

| Tárgy | Szakma | Mit ad |
|---|---|---|
| ⛏ **Tárnász Csákány** | Bányász | Hatékonyság IV + Törhetetlenség III csákány |
| 🪓 **Favágó Fejsze** | Favágó | Hatékonyság IV + Törhetetlenség II fejsze |
| 🛡 **Bástya Pajzs** | Kovács | Törhetetlenség V pajzs |
| 📖 **Bölcs Könyve** | Bűvölő | Javítás (Mending) varázskönyv |

> A receptek smaragd-alapanyaggal készülnek (hogy ne ütközzenek a vanilla receptekkel).

**🍺 Szakács kocsma-italok és tájfogások (20+ recept)** — a Szakács saját **italokat**
(ivás-animációval!) és **ételeket** főz, mind rövid, nem-harci buffal. Ízelítő:
*Jéghegyi Sör* (regeneráció), *Parázs Pálinka* (tűzállóság+erő), *Hamvasztott Kávé* (sietség),
*Kőfejtő Söre* (ellenállás), *Tengerész Rum* (vízlégzés+ugrás), *Árnyéklikőr* (rövid láthatatlanság),
*Aranyfényű Mézsör* (szerencse), *Szentelt Bor* (gyógyulás), *Pásztor Ürücombja*, *Bányász Szalonnája*
(sietség), *Harcos Húsos Tála* (erő), *Tüzes Chilis Tál* (tűzállóság). A hatás-idők a szerveren hangolhatók.

> 🍺 **Kupa-hurok:** minden kocsma-ital **Üres Kupába** készül (Szakács 5. szint, üvegből), és
> amikor megiszod, **a kupa a kezedben marad** — nem vész el. Ugyanazt a kupát újra és újra
> megtöltheted, tehát a kocsma nem nyersanyag-nyelő: egyszer kell kupát csinálnod, utána a
> hozzávaló csak az, amit az ital maga kér.

#### 🔒 Craft-korlátok (vanilla tárgyak)

Néhány **erős vanilla tárgy** is szakmához kötött — ez teszi értékessé a szakmákat és a köztük
lévő cserekereskedelmet. Ha nincs meg a szint, a craft eredménye nem jön létre, és üzenetet kapsz:

| Tárgy | Kell hozzá |
|---|---|
| Netherite felszerelés | **Páncélkovács 25** |
| Netherite-rúd (finomítás) | **Bányász 20** |
| Számszeríj, pajzs | **Favágó 8** |
| Főzőállvány | **Alkimista 5** |
| Bűvölő-asztal | **Enchanter 5** |
| Torta, sütőtökös pite, nyúlpörkölt | **Séf 6** |

A korai alapok (íj, kőszerszám, sült húsok, fapáncél) bárkinek szabadok — a kapuk a csúcs-
kimenetet és a szakma-„állomásokat" védik.

#### ✨ Minőség és affixek (egyedi tárgyak)

> 🔮 A WoW-módban (ha a szerver bekapcsolja) az affixek közt **Varázserő** is sorsolódhat —
> %-ban növeli a spellek erejét (caster-gear).

Minden mestermunka craftoláskor (és minden mob-loot tárgy) **véletlen raritást** és **random
attribútum-affixeket** kap — így nincs két egyforma darab! A raritás egy **létrán** helyezkedik el
(mint WoW-ban / Terraria reforge-ban): a jelző, a szín és az affixek illenek a raritáshoz, és a
**magasabb raritás erősebb affixeket** ad (a prefix „fedi" az affixet):

| Raritás | Szín | Affixek | Erő | Megjegyzés |
|---|---|---|---|---|
| Ócska | sötétszürke | 1 | 0.4× | **csak negatív** affix (átok)! |
| Közönséges | fehér | 1 | 0.6× | ritkán negatív |
| Nem mindennapi | zöld | 2 | 0.8× | |
| Ritka | kék | 2 | 1.0× | |
| Epikus | lila | 3 | 1.2× | |
| Legendás | arany | 4 | 1.4× | |
| Ereklye | piros | 4 | 1.6× | csúcs (csak boss-loot) |

Az affixek attribútumok: **Szívósság** (+élet), **Vértezet**, **Keménység**, **Fürgeség**
(páncélon), illetve **Élesség** (+sebzés) és **Gyorsaság** (fegyveren/szerszámon). A tárgy neve
elé a raritás kerül a hozzá illő jelzővel (pl. `[Legendás] Isteni Penge` vagy `[Ócska] Rozsdás Vért`),
a bónuszok a leírásban látszanak (negatív affix pirossal).

> 🌾 **Bőség-idő bónusz:** amíg a Bőség-idő világesemény tart, a **Gyógynövényész**
> betakarítása (virág, érett termény) **másfélszeres szakma-XP-t** ad — érdemes az
> ablakra időzíteni az aratást.

> 📜 **Loot-only tervrajzok:** a legerősebb mestermunkák (pl. a netherit csúcs-szerszámok,
> a *Sárkányvért*, a *V. Miinus Haragja* és az *I. Zhoris Lángnyelve*) tervrajza **kizárólag világbossoktól és nehéz eseményektől** eshet —
> boltban nem kapható. A recept-könyvben lila sor jelzi: „Csak legendás ellenfelektől
> szerezhető tervrajz".

> ☠ **Átkozott tárgyak:** a boss-zsákmány ritkán az **Első Csend érintését** hordozza
> (sötétvörös lore-sor). Az átkozott darab **erőt ad** (darabonként +10% sebzés, legfeljebb
> +40%), de **felvéve nem ereszt** — a páncél nem vehető le szabadon! Felvételkor a játék
> kétszer is megkérdez (első kattintás figyelmeztet, a gyors második erősít meg). Az átkot
> csak az **Átok-törés oltára** oldja (síró obszidián mag, obszidián talapzat; áldozat:
> ametiszt + ghast-könny) — a tárgy megmarad, csak az átok (és a bónusz) tűnik el.

**Balansz — a forrás dönti el, MELY raritások eshetnek:**
- 🧑‍🏭 **Szakma-craft:** nincs Ócska, kiegyensúlyozott (Közönséges→Legendás), **megtervezett névvel**.
- 👹 **Mob-drop:** a szörnyek **sokféle** tárgyat ejtenek egy súlyozott loot-tábláról: egyedi
  rolled felszerelést (eshet **Ócska** is, csak átkos affixszel!), mindenféle nyersanyagot/értéket,
  és **csak-mobból-eső egyedi alapanyagokat** (*Vad Esszencia*, *Szörny Mag*, *Árnyékpor*), amiket a
  szakma-receptek igényelnek. A szakma által craftolt (nevesített) tárgyak **soha nem esnek mobból**.
- 🐉 **Világboss / nehéz event loot:** magas raritások (Ritka→**Ereklye**) + boss-only egyedi
  alapanyag (*Fekete Villám Szilánk*, a legendás receptekhez) — a legjobb forrás.

#### ⚑ Frakció-signature receptek (tervrajz)

A birodalmak **saját, vágyott végjáték-tárgyakat** kaptak — tervrajzból tanulható, **frakcióhoz
kötött** receptek (a recept-könyv ⚑ sora jelzi). Az első kör a **Fagyott Királyságé** (Fagy/BLUE,
páncélkovács): **Kallan Szeletelője** (íj — gyorsabb, páncéltörő nyilak), **Glatziendorfi Jégvért**
(mellvért — viselve tompítja a sebzést), **Jégsárkány-Kantár** (jobb katt egy hátason: tartós
gyorsítás, a kantár elfogy). A második kör a **Lángoló Birodalomé** (Láng/RED, páncélkovács):
**Pyralingradi Tűzköpő** (számszeríj — Quick Charge + gyorsabb lövedék), **A Vérszavanna Agyara**
(kard — bónusz-sebzés, baltával a másik kézben még nagyobb), **Főnix-Tollköpeny** (mellvért-slot —
tűz/láva-immunitás viselve; a craft RED-kapus, de a köpeny kereskedhető). A harmadik kör a **Menedéké** (NEUTRAL — gazdaság/kényelem):
**Vasművek Akadémiájának Csákánya** (bányász 45 — érc-töréskor +20% eséllyel extra drop; bányász-láz
esemény alatt szünetel), **Bokic-menti Horgászbot** (horgász 40 — +20% eséllyel dupla fogás),
**Smaragdkő Bankbetét** (rúnaírnok 35 — jobb-kattal Creutzérre váltható betétjegy), és a
**Szellemszarvas-Bűbáj** (füvész 45 — jobb-katt: ideiglenes, gyors szellem-hátast idéz; nem fogy el,
cooldownnal). Ráadásul minden signature
tárgy saját, **egyedi enchantot** visel (pl. „Jégfog", „Vérszomj", „Főnixtoll") — a neve valódi
enchant-sorként ragyog a tooltipben. A perkek pontos számai
configból hangolhatók (`signature.*` a `crafting.yml`-ben).

### Talentpontok a szakmákból

Az **összes szakmád szintje** együtt **talentpontot** termel a szakma-talentfádhoz: **minden
10 szint után 1 pont** (lásd a [Talentek](#7-talentek-talent-fa) oldalt). Tehát megéri több szakmát is
szintezni — még a másodlagosakat is!

---

### Rúna-kovácsolás ◆

A magas szintű Kovácsok és Bűvölők (26+) **rúnákat** készíthetnek — ritka anyagokból
(Rúnapor, tematikus unique anyag, bolti Rúnakréta). A rúnát **fogd a kurzorodra, és
kattintsd rá** a fegyverre/íjra/mellvértre a táskádban: a véset a tárgyba ég, és kis
állandó bónuszt ad (pl. +sebzés, gyújtás-esély, sebzés-csillapítás, több erszény-drop).
**Egy tárgy — egy rúna**, és a véset nem cserélhető: gondold meg, mire teszed! A rúna a
tárgy ritkaságától FÜGGETLEN — a legendás pengére is ér, meg a kezdő vaskardra is.

### Szakma-céh heti közös cél ⚒

Az azonos szakmát űzők — frakciótól függetlenül — **közös heti célt** töltenek: minden
megtermelt szakma-XP a céh számlálójába is beszámít. Az állást a `/szakmacel` mutatja.
Ha a cél teljesül, a hét fordulásakor minden érdemi hozzájáruló **+300 szakma-XP-t** kap
(ha épp nem vagy fent, belépéskor érkezik). A Bányászok végre egy zászló alatt!

---

## 9. Relikviák és rituálék ✅

A **relikviák** legendás, egyedi tárgyak különleges erővel. Két nagy szabály van rájuk:

- **Egy relikviából csak EGY létezhet** a szerveren egyszerre.
- Ha a tulajdonosa **14 napig nem lép be**, a relikvia **füstként elenyészik**, és újra
  megszerezhetővé válik (más is megkaphatja).

### A Mételytépő ⚔️

Egy különleges **harci fejsze**, ami a **bűnösök (sinnerek) ellen** hat:
- **Megbélyegzi** és **megbünteti** a bűnös játékosokat (Justice / Honor Eye képességek).
- **Lefagyasztja** az élőhalottakat (zombi, csontváz).
- **Fegyver-relikvia:** ha a tulajdonosát **megölik PvP-ben**, a fejsze **a gyilkosé lesz**!
  (A „passzív" relikviák — pl. a szárnyak — ettől védettek.)

### A négy frakció-szárny (elytra-relikviák) 🪽

Mind a négy frakciónak van egy saját **szárnya**. **Csak a tulajdonos ÉS a megfelelő frakció
tagja** használhatja:

| Szárny | Frakció | Mit tud |
|---|---|---|
| 🔴 **Főnix-szárny** | Piros | Tűz/láva-immunitás; **zuhanáskor lángvihar** (felgyújtja a közeli ellenfeleket) |
| 🔵 **Zúzmara-szárny** | Kék | Fagyimmunitás; **felszálláskor megfagyasztja** a környező ellenfeleket |
| ⚪ **Vándorszél** | Semleges | **Nincs zuhanósebzés**; felszálláskor **széllökés-boost** |
| ⚫ **Csontszárny** | Sötét | Wither-immunitás; **éjszakai repüléskor árnyformába** vált (láthatatlanság + sebesség) |

### Rituálé-oltárok 🔮 — így szerzed meg a szárnyakat

A négy szárnyat **nem lehet craftolni** — egy **oltáron kell megidézni** őket. Keress (vagy
építs) egy adott **oltár-blokkot**, gyűjtsd össze a hozzá tartozó **áldozati tárgyakat**, majd
állj az oltárra és **lopakodás (SHIFT) + jobb katt**:

| Szárny | Oltár-blokk | Áldozati tárgyak |
|---|---|---|
| 🔴 Főnix-szárny | Magmatömb | 8 lángrúd, 16 tűzcsóva, 1 aranytömb |
| 🔵 Zúzmara-szárny | Kék jég | 16 tömör jég, 8 prizmarin-kristály, 1 gyémánttömb |
| ⚪ Vándorszél | Ametiszttömb | 32 toll, 8 fantommembrán, 1 smaragdtömb |
| ⚫ Csontszárny | Lélektalaj (soul soil) | 8 csonttömb, 1 wither-csontvázkoponya, 2 netherit-törmelék |

Ha a szárnynak **már van élő tulajdonosa**, nem idézheted meg újra (az „egy példány" szabály
miatt). Várnod kell, amíg felszabadul.

#### Multi-block szentélyek 🏛️

Az oltárok **komoly, több-blokkos szentélyek**: nem elég a mag-blokk, meg kell **építeni a teljes
szerkezetet** köré. Az alapminta egy **5×5-ös alapzat** (a mag-blokk alatt, tematikus blokkból) és
**4 két-magas saroktorony** a külső sarkokban — ha a szentély hiányos, az oltár szól, mielőtt
aktiválnád. A pontos mintát a `config/relics.yml` `structure` mezője adja.

#### Egyéb oltárok 🕯️ — nem csak szárnyak

Az oltár-rituálé nem csak relikviát adhat. Ugyanúgy működik (építsd meg a szentélyt, gyűjtsd össze
az áldozatot, SHIFT + jobb katt a mag-blokkon), de más a kimenet — és ezek **ismételhetők**
(van egy rövid „feltöltődés"):

| Oltár | Mag-blokk | Áldozat | Mit ad |
|---|---|---|---|
| 🕯️ Feloldozás | Lélek-lámpás | 3 aranytömb, 2 ghast-könny, 1 megmentő-totem | Leveszi a **bűnös-jelet** és nullázza a bűneidet (a sötét paktumot nem oldja fel) |
| 🏠 Hazatérés-kő | Iránytű-kő (lodestone) | 2 ender-gyöngy | A frakciód **fővárosába teleportál** |

#### Kaszt-szentélyek ⚜️ — mind a 13 kasztnak

Minden kaszt kap egy **saját szentélyt**, ami csak neki ad tematikus, időleges buffot (5 perc,
`requires-class` kapuval). Mindegyik 5×5-ös szentély, egyedi mag-blokkal és áldozattal:

| Kaszt | Mag-blokk | Buff |
|---|---|---|
| Varázsló | Varázsló-asztal | Sietség II + Regeneráció |
| Harcos | Üllő | Erő II + Ellenállás |
| Íjász | Nyílkészítő-asztal | Gyorsaság + Ugrás II |
| Orgyilkos | Sculk-Lélekkapocs | Láthatatlanság + Gyorsaság II |
| Druida | Virágzó azálea | Regeneráció + Ugrás + Vízlégzés |
| Paplovag | Fényporkő (glowstone) | Ellenállás + Regeneráció + Tűzállóság |
| Halállovag | Síró obszidián | Erő + Felszívás III |
| Sámán | Villámhárító | Sietség + Conduit-erő + Gyorsaság |
| Szerzetes | Harang | Gyorsaság II + Ugrás + Ellenállás |
| Pap | Tengeri lámpás | Regeneráció + Ellenállás |
| Boszorkánymester | Újraéledés-horgony | Erő + Tűzállóság + Felszívás |
| Démonvadász | Vésett nether-tégla | Gyorsaság II + Erő |
| Sárkányidéző | Végkő-tégla | Sietség II + Felszívás + Tűzállóság |

> Minden oltár-blokk, szerkezet, áldozat és hatás a `config/relics.yml` `rituals:` szekciójában
> testreszabható — új oltárt is felvehet az admin (`type: relic|cleanse|buff|home`,
> `requires-class`/`requires-faction` kapukkal).

---

#### 💧 Eleftheria Könnye *(gyűjtő-relikvia)*

Megkövült, éjfekete csepp — a Néma Királynő első suttogása hozta létre. **Nincs aktív képessége**:
presztízs- és lore-kincs, egy példányban létezik a szerveren. Rituálé-oltára (síró obszidián mag,
feketekő szentély) **csak a Kitaszítottak (DARK) frakcióval** aktiválható.

### Mi történik a relikviával, ha meghalsz? ⚰️

- A **fegyver-relikvia** (pl. Mételytépő) PvP-halálnál a **gyilkosé** lesz (gazdát cserél).
- A **passzív relikvia** (szárnyak, Eleftheria Könnye) **köddé válik** — nem esik le, senki sem
  veheti fel. A kötés viszont él: **csak te idézheted újra** a rituálé-oltárnál (az áldozat újra
  kell!). Ha **~3 napon belül** nem idézed újra, a relikvia **mindenkinek felszabadul** — siess.
- Ha az admin a **megőrző (`keep`) módot** állította be, a passzív relikvia **respawnkor
  visszakerül hozzád**. Ha még a respawn előtt kilépsz (vagy elszáll a szerver), a tárgy
  nem tűnik el véglegesen: a kötés akkor is „elveszett" jelölést kap, tehát ugyanúgy
  **újraidézheted az oltárnál**.
- Aktív tulajdonosként az oltár nem ad második példányt — egy relikvia, egy gazda, egy tárgy.

### DEV itemek — Csodálatos Bingulus

A **DEV itemek nem relikviák**: személyhez kötött, egyedi fejlesztői tárgyak, ezért nem járnak le,
nem szabadulnak fel inaktivitás miatt, és PvP-ben sem kerülnek új tulajdonoshoz.

A **Csodálatos Bingulus** Bence örökös DEV iteme. Amíg a tulajdonosa online van, és a Bingulus a
saját inventoryjában található, 10 aktív percenként sorsol egy jutalomritkaságot, majd az adott
kategóriából vanilla vagy IceSMP-s saját itemet ad. Nem dobható el, nem helyezhető tárolóba, és
halál vagy szerver-újraindítás után automatikusan visszatér a tulajdonosához.

---

## 10. Világesemények ✅

Időnként **különleges dolgok** történnek az egész szerveren. Ezekre figyelj — extra jutalmat
(vagy extra veszélyt!) hozhatnak.

> **Helyi hírek:** a személyes léptékű események (régészeti lelőhely, hullócsillag,
> vándorló csorda) **nem szerver-hírek** — csak az értesül róluk, aki a **közelben** jár.
> Érdemes nyitott szemmel járni a vadont!

> **Egyszerre egy nagy kaland:** a nagy mob-események (világboss, invázió, vad hajsza,
> kíséret, kultisták) nem torlódnak — amíg az egyik zajlik, a következő nagy esemény
> megvárja a sorát. Így mindig tudod, merre van a "fő műsor".

> **Hol jelenhetnek meg?** A mob-spawnoló események (világboss, invázió, vad hajsza) — akárcsak
> a meteor és a kincs — **soha nem érkeznek városba**: claimelt frakció-territóriumba,
> játékos-claimbe és védett régióba nem spawnolnak, és víz tetejére sem. Az esemény-szörnyek
> **nem zombisodnak át** az overworldben (a pokolbéli fajták sem), és **nappal sem égnek el**.

### Mob-szintezés (a világ nehézsége) 🧟

A világ **kifelé egyre veszélyesebb**:
- A spawntól távolodva a szörnyek **erősödnek**: **minden 1000 blokk = +1 mob-szint** (max 10).
- A magasabb szintű szörny **több életű és erősebb**, de cserébe **több kaszt-XP-t** és nagyobb
  eséllyel **lélekkövet (Csontveret)** ad.
- A **spawner-ből / parancsból** érkező szörnyek **nem** skálázódnak — így a farmok
  biztonságosak maradnak.
- A szint-névtábla (`[Lvl X]`) alapból **csak akkor látszik, ha ránézel** a szörnyre — így nem
  zsúfolja tele a képernyőt falakon át vagy messziről.

### Vérhold-éjszaka 🌕

Ritkán beköszönt egy **vérhold**:
- A szörnyek **+pár szintet** kapnak (még erősebbek).
- A **lélekkő-drop esélye megnő** — kockázatos, de jövedelmező éjszaka.
- Egy üzenet jelzi mindenkinek, amikor kezdődik.

### Világboss 👹

Időnként egy hatalmas **világboss** szörny jelenik meg egy véletlen játékos közelében (ragyog,
hogy könnyű észrevenni):
- Spawnkor **véletlen archetípus** kerül kiválasztásra — saját névvel, stat-szorzókkal és
  **szignatúra-aurával** (a boss közelében a túlélők témába illő debuffot kapnak). Pl.
  A Gyűrűk Őre, Lávakohó Behemót, Fagyott Trón Királya, Csontkirály, Mélységi Rém…
- A boss **~8 másodpercenként telegrafált különleges képességet** süt el (becsapódás / mérgező
  zóna / add-idézés) — a veszélyzónát **részecske-gyűrű** ÉS egy **növekvő, piros izzó padló-lap**
  rajzolja ki (a lap a becsapódásig tölti ki a zónát), külön **hangjelzés**
  (Warden-morajlás, idézésnél Evoker-kántálás) kíséri: amíg a lap nő, van időd ellépni a jelzett helyről!
- **50% HP alatt feldühödik** (2. fázis): erősebb, gyorsabb csapások.
- Aki **leüti**, annak a **frakciója kasszát + liga-pontot** kap; a **legyőző játékos** egy
  időleges **harci buffot** (Erő + Védelem) ÉS **személyes bónusz-zsákmányt** kap (gyémánt,
  tapasztalat-palack, arany alma, ritkán emlékszilánk — tárgy, sosem pénz).

### Invázió ⚔️
Időnként egy **szörnyhorda** tör be egy játékos köré — ragyogó, **megerősített (skálázott)**
szörnyek hulláma. A horda-összetétel **véletlen** (pl. Élőhalott Áradat, Csontlégió, Pókfészek,
Káosz-horda), és minden hullámot egy **megnevezett bajnok (mini-boss)** vezet, amely szintén
telegrafált földcsapással támad. Veszélyes, de a legyőzésük **több kaszt-XP-t** és nagyobb
**lélekkő-esélyt** ad, mint a hétköznapi szörnyek. (Admin: `/events invasion`.)

### Kereskedő-karaván ✦

Időnként egy **vándorkereskedő karaván** érkezik (egy üzenet jelzi, melyik világba és mennyi
ideig marad). Amíg itt van, **jobb-katt a karaván-NPC-re** → bolt nyílik **ritka portékákkal**
(arany alma, gyémánttömb, névcímke, tapasztalat-palack…), fix áron a banki egyenlegedből.
A kifizetett pénz eltűnik (money sink). Ha lekésed, legközelebb **máshol** bukkan fel.
Állapot: `/events caravan`. Részletek: [Valuta és gazdaság](#3-valuta-és-gazdaság).

### Vad Hajsza 🐺

Időnként egy **megnevezett, feldühödött elit fenevad** (pl. Ősi Fenevad, Csont Vadász, Vén
Mágus, Pokoli Behemót) kóborol be egy játékos közelébe — kóborló mini-fenyegetés az
invázió-hordák és a világbossok között. Aki **leteríti**, **ritka zsákmányt** kap (nyersanyag,
nem pénz); ha időben senki nem öli meg, eltűnik a vadonban.

### Elrejtett kincs 🗺

Időnként egy **megjelölt kincsesláda** bukkan fel valahol, és az üzenet megadja a
**hozzávetőleges helyét** (világ + koordináta). Az **első**, aki odaér és **rákattint** (vagy
kibányássza), viszi a teljes zsákmányt (nyersanyag, nem pénz), majd a láda eltűnik. Ha senki
nem találja meg időben, feltáratlanul elenyészik — siess!

### Gyűjtögető buff-ablakok ⛏🎣

Időnként megnyílik egy **szerver-szintű bónusz-ablak** (kb. 15 percre) — csak nyersanyag/XP,
sosem pénz:
- **Bányász-láz** — az érc-blokkok bónusz dropot adnak.
- **Termés-óra** — a beérett termés bónusz hozamot ad.
- **Halászati láz** — esély dupla fogásra.
- **Tapasztalat-óra** — XP-szorzó mindenből.

Egy üzenet jelzi a kezdetét és a végét — ilyenkor érdemes rákapcsolni a megfelelő tevékenységre!

### Bőség-idő 🌱

A vérhold **pozitív ellenpárja**: egy nyugodt időablak, amikor a **termés gyorsabban nő**, az
**állatok néha ikret ellenek**, **kevesebb szörny** spawnol, és **gyengéd regeneráció** leng
mindenkin. Építeni, farmolni, feltöltődni való — a béke szigete a háború közepén.

### Kollektív szerver-kihívás ⚔

Időnként az **egész szerver** kap egy közös, időzített célt (pl. öljetek meg együtt szörnyeket /
bányásszatok ércet / takarítsatok be termést) — a cél **az online létszámhoz igazodik**
(fejenként kb. 40 szörny / 60 érc / 80 termés), így kevesen és sokan is teljesíthető. A haladást
**boss-bar** mutatja mindenkinek. Ha időben **együtt** teljesítitek, **minden online játékos** jutalmat kap
(XP + nyersanyag-csomag + rövid Sietség-buff). Közös cél, közös jutalom.

### Karaván-kíséret 🛡

Időnként egy **konvoj** (ládás láma) indul útnak egy cél felé, és útközben **szörny-hullámok**
támadják — a haladást **boss-bar** mutatja. A közelben lévő játékosoknak **életben kell
tartaniuk**, míg célba ér:
- Ha **odaér**: a zsákmány a célnál hullik, és a **kereskedő-karaván boltja egy ideig bővebb
  (ritka) készlettel árul**.
- Ha a konvoj **elesik** vagy lejár az idő, a szállítmány elvész.
- A kíséret-mobok robbanása **sosem rongálja a terepet**.

### Meteor-becsapódás ☄

Időnként egy **meteor** csapódik be a vadonba (az üzenet megadja a helyét), és egy kis
**kráter** marad, tele **ritka, kibányászható érccel** (gyémánt, smaragd, ősi törmelék…).
Siess: a kráter csak egy ideig marad, aztán **magától visszaáll az eredeti terep** — amit addig
kibányászol, a tiéd. A meteor **sosem csapódik claimelt területre vagy frakció-territóriumba**,
és **nem rombolja maradandóan** a világot.

### Kultisták 🕯

A Néma Királynő kósza hívei időnként előbújnak a homályból — háromféleképpen:
- **Portya:** kis kultista csapat támad — szórd szét őket, a szerver értesül a győzelemről.
- **Rítus:** a hívek kört állnak és kántálnak. **Szakítsd meg** (öld le mindet), mielőtt az
  idő lejár — jutalom-loot hullik a kör közepén. Ha a rítus **beteljesül**, jó eséllyel
  **rontás-góc nyílik** a helyszínen…
- **Hírvivő:** magányos csuklyás alak oson a Kitaszítottak földje felé. **Kövesd** — vagy
  állítsd meg, mielőtt köddé válik és az üzenete célba ér.

**A tét kétoldalú:** ha egy kultista esemény **beteljesül** (a rítus lefut, vagy a hírvivő
célba ér), a Néma Királynő **rejtett hívei** — a Suttogók — jutalmat kapnak: az álcájuk
mélyül, és a Kitaszítottak liga-pontot nyernek. Vagyis a kultisták **védelme** is valódi
játék: sosem tudhatod, hogy aki melletted "késve ér oda" a rítushoz, az ügyetlen… vagy hű.

### Rontás-góc 🕸

Időnként egy **rontás-góc** nyílik a vadonban (broadcast megadja a helyét): közepén egy
**sculk-mag**, körülötte a zóna **éjszakánként terjed**, és **korrupt, világító fajzatokat**
szül. A rontás **leggyakrabban a Kitaszítottak földjének pereméről szivárog ki** — a DARK
territóriumok környékén számíts rá leginkább! **Tisztítás:** ölj le elég korrupt fajzatot,
majd a magot **SHIFT + jobb kattintással** törd meg → loot, rövid regeneráció, és „a Fa
fellélegzik”. Ha senki nem tisztítja, a zóna a plafonjáig nő, és onnan ontja a szörnyeket.

> ⚠️ **A mag közelében a rontás beléd mar:** a sculk-magot körülvevő pár blokkos gyűrűben
> **ismétlődő sebzést** kapsz (a *rontás* emészt, nem a szörnyek). Tehát a mag-törést vagy
> **gyorsan** vidd véghez, vagy **gyógyítással**/társsal — nem lehet kényelmesen ácsorogni a
> góc szívében. (A hatókör és a sebzés a szerveren hangolható, akár ki is kapcsolható.)

### Hangulat-események ✦

Időnként apró, **légköri események** teszik élőbbé a világot (nem befolyásolják a balanszot):
**északi fény** (rövid éjjellátás + magasan lebegő, sodródó fény-fátyol az égen), **hulló csillag** (üzenet a becsapódás
irányával), **köd**, **bolyongó szellemek**, **szentjánosbogarak**, valamint **állat-vándorlás**
(egy passzív állatcsorda vándorol a közeledbe — élelemforrás).

> 👥 **Csapatban vagy?** A plugin loot-eseményeinél (**Vad Hajsza**, **elrejtett kincs**) nem
> egy közös zsákmány esik le: minden közelben lévő párttag a **saját (personal) jutalmát**
> kapja. Részletek: [Party (csapat)](#15-party-csapat).

### Szezonális liga 🏆

A frakciók **pontot gyűjtenek** — de nem csak háborúból! A liga **aszimmetrikus**: minden
frakció a **saját identitás-útján** pontoz a legerősebben, így mind a négynek van legitim
útja a bajnoki címhez:

| Pontforrás | Kinek éri meg leginkább? |
|---|---|
| ⚔ Raid-győzelem | a hadviselőknek (Perinfernicitas, Cryghaliris, Kitaszítottak) — a semleges népeknek fél-súlyú |
| 👹 Világboss-ölés | mindenkinek egyformán |
| 🏛 Közösségi cél teljesítése | a semleges népeknek (a virágzás az ő győzelmük — másfélszeres súly) |
| 🌿 Rontás-góc megtisztítása | a semleges népeknek (a Fa gyógyítása); a Kitaszítottaknak fél-súlyú |
| 🤺 Becsület-párbaj győzelem | a Kitaszítottaknak (másfélszeres súly) |
| 🕵 Sikeres kém-küldetés | a Kitaszítottaknak (a Suttogók útja — másfélszeres súly) |

A szezon végén (alapból 60 nap) a **vezető frakció** győz:
- a **frakciókassza** nagy jutalmat kap;
- a győztes frakció **online tagjai** győzelmi **buffot** (Erő + Regeneráció + Falu Hőse) és
  **tárgy-jutalmat** (alapból gyémánt + aranyalma) kapnak, egy ünneplő **tűzijátékkal**;
- aki a záráskor **offline** volt, a tárgy-jutalmát a **következő belépéskor** kapja meg
  (nem marad le róla);

majd a pontok lenullázódnak — kezdődik az új szezon. Az aktuális állást a `/events season` mutatja.

#### Végítélet-hét — a szezon zárása 📖

A szezon **utolsó hetében** a Korszakok Könyvének lapja fordulni kezd, és a világ napról napra
vadabb lesz:

- **sűrűbb vérhold, gyakoribb világboss és invázió** — az esélyek naponta nőnek;
- az inváziós hordák **erősebbek** (napi mob-szint bónusz);
- **minden liga-pont többet ér** — a szorzó naponta nő, az utolsó napon **dupla pont** jár;
- az **utolsó napon** megjelenik a **Szezonboss** (alapból *A Lapforduló Őre*) a semleges
  főváros **falai előtt** — emelt élettel, és a legyőzése **egyedi zsákmányt** (ritka anyagok,
  emlékszilánk) + **extra liga-pontot** ad a leütő frakciójának.

A napváltásokat és a boss érkezését a krónikás-hangú kihirdetések jelzik — az utolsó napokban
érdemes együtt mozogni a frakcióddal: itt dőlhet el a bajnoki cím.

A szezon zárásakor a krónikások **rövid átvezető történettel** búcsúztatják a korszakot, a
bajnok frakció pedig **kőbe vésve** marad meg: a főváros emlékművén („A Korszakok Könyve")
korszakról korszakra bővül a bajnokok és hőseik listája.

> 🍂 **Évszakok:** a világ a valós évszakokra is rezonál — télen sűrűbb a vérhold és a Vad
> Hajsza, nyáron gyakoribb a Bőség-idő és a gyűjtögető-láz. Figyeld a naptárat!

> ℹ️ **Fontos: a szezonzárás NEM wipe!** Egyedül a **liga-pontok** nullázódnak (új bajnoki
> évad indul). A szinted, a tárgyaid, a pénzed, a bázisod, a claimjeid, a talentjeid, a
> szakmáid — MINDEN megmarad. A szezon csak a frakciók közti verseny ciklusa, nem a világé.

### A világ hangjai 🕯

- **Korszakváltás:** szezonzáráskor a krónikások **rövid történetben** búcsúztatják a letűnt
  korszakot — az előző szezon hőseinek nevével. Figyeld a „Lapforduló" címet!
- **Az Énekmondó:** a fővárosi **bárd** minden héten **balladát** költ a szerver legnagyobbjairól
  (szint, vagyon, háborús hírnév). Kattints rá jobb gombbal, és hallgasd meg — hétfőnként új dal.
- **Tábortűz-mesélés:** ülj le egy **tábortűz** mellé (**sneak + jobb-katt**), és maradj ott pár
  másodpercet — a tűz mesél neked a régi időkről, és egy kevés tapasztalatot is ad. (Óránként
  egyszer; a körülötted ülők látják, hogy mesélsz.)
- **Az Idegen:** néha, valahol… feltűnik egy **csuklyás alak**. Akik közel állnak, hallanak tőle
  egy mondatot. Mire odaérsz — általában már nincs sehol. Hogy ki ő? A kódex hallgat róla.

### Titkos helyek 🧭

A világban **rejtett pontok** lapulnak (egy kilátó, egy barlangmélyi szentély…). Aki **elsőként**
talál rá egyre, **jutalmat kap, és a nevét az egész szerver megismeri** — „bekerül a térképekbe".
A későbbi felfedezők is kapnak egyszeri, kisebb jutalmat. Hogy hol vannak? Azt senki sem árulja
el — járd a világot nyitott szemmel!

### Bemutató (intro)

Amikor **először** lépsz be, lejátszódik egy rövid, hangulatos **cím-szekvencia**. Ez csak
egyszer fut le. (Admin újra le tudja játszani.)

> **Hangulat-események napszakhoz kötve:** az északi fény, a hulló csillag és a szellemek
> csak **éjjel**, a szentjánosbogarak **szürkületkor**, a köd **hajnalban vagy esőben**, az
> állatvándorlás **nappal** jelenik meg — és aki az esemény pillanatában a **szabad ég alatt**
> van, kis token-jutalmat + az eseményhez illő rövid buffot kap (pl. aurora → éjjellátás).
>
> Ezeket az eseményeket a `/events` paranccsal nézheted meg — az **`/events status`** egyben
> kiírja, **mi történik éppen most** (minden aktív esemény + hátralévő idő + szezon-állás),
> részletesebben pedig `/events season`, `/events blood-moon`, `/events caravan` —, vagy a
> `/menu` → **Események** almenüben, aminek a tetején az **óra-ikon** ugyanezt az élő
> összegzést mutatja, és ami
> **élő státusszal** mutatja a vérholdat, világbosst, karavánt, kíséretet, bőség-időt,
> szerver-kihívást és a meteor-krátert. A többi eseményt (meteor, kincs, kihívás…) az adminok
> tudják kézzel is kiváltani — lásd a [Parancsok](#14-parancsok-listája) oldalt.

---

> 💰 **Kultista-zsákmány:** a Királynő kósza hívei (portya, hírvivő, rítus-hívők) leölve
> eséllyel értéket dobnak — aranyat, árnyékport, emlékszilánkot, ritkán Suttogás-meghívót.

---

## 11. Királyság, raid és háború ⚔️

A frakciók nem csak külön élnek — **királyt választhatnak**, **háborúzhatnak**, és van **közös
kasszájuk**. Itt a nagy, csapatszintű játék.

### Királyválasztás 👑

Minden harcos frakció (Piros / Kék / Sötét — a Semleges kivételével) **királyt választhat**:
- `/faction king vote <játékos>` — szavazol a saját frakciód egy tagjára.
- Aki eléri a **minimum szavazatszámot** és vezeti a listát, azt **megkoronázzák** (mindenki
  látja az üzenetet).
- A választási ciklus időnként **újraindul** (új választás).
- `/faction king` — megmutatja a jelenlegi királyt és a szavazatokat.

**A király jogai:**
- Kivehet a **frakciókasszából** (`/faction treasury withdraw <összeg>`) — a pénz **veretben,
  a kezébe** érkezik (a bankba magának kell befizetnie), és **napi keret** korlátozza.
- **Beállíthatja a frakció adókulcsát** (`/faction king tax <százalék>`).
- **Raidet (háborút) hirdethet** egy másik frakció ellen.

### Frakciókassza 🏦

- `/faction treasury` — a kassza egyenlege.
- `/faction donate <összeg>` — adományozol a saját valutádból a kasszába.
- A kasszát az **adó** és az **adományok** töltik; a **király** és a **raid-zsákmány** költi.

### Raid (frakcióháború) ⚔️

- `/faction raid <célfrakció> [terület]` — **csak a király** hirdethet. A **nevezési díj** a
  kasszából megy. A raid alapból a védő **fővárosáért** folyik (vagy a megadott területért);
  ha a védőnek nincs területe, kötetlen, csak-ölés raid lesz.
- **Felkészülés (alapból 2 perc):** mindkét oldal harcosai jelentkeznek — `/faction raid join`
  (alapból **max 10 fő/oldal**; a hirdető király automatikusan bekerül). `/faction raid status`
  mutatja az állást. **Nevezni CSAK ebben a szakaszban lehet** — a harc megkezdése után már
  nem lehet beállni egy megviselt oldal ellen.
- **Harci szakasz (alapból 15 perc):** csak a **jelentkezett harcosok közti ölés** szentesített
  (nem bűn) és **pontozó** (alapból 5 pont) — területhez kötött raidnél csak akkor ér pontot,
  ha az áldozat a **raid-zónán belül** esik el. Aki nem jelentkezett, arra a békeidős
  bűn-szabályok élnek raid alatt is!
- **Pont-tartás:** a raid-terület **középpontja** elfoglalható objektíva — minden bent álló
  harcos **pontot termel** az oldalának (alapból 5 mp-enként +1). A bossbar élőben mutatja
  a pontállást.
- A végén a **több pontot szerző** oldal **hadizsákmányként** elviszi a vesztes kasszájának egy
  részét, és **liga-pontot** kap a szezonba.
- **Terület-átvétel:** ha a **támadó** nyeri a területhez kötött raidet, a terület **átkerül
  hozzá** (a fővárosi státusz elvész — a hódítmány nem lesz új főváros).
- A **győztes frakció online tagjai** kapnak egy **győzelmi buffot** (Erő + Regeneráció).
- Raid alatt a jelentkezett harcosok az ellenség földjén **bűntelenül zsákmányolhatnak**
  a konténerekből is.

#### Ostromágyú 💥

Raidhez bevethetsz egy **craftolható ostromfegyvert**:
- Recept: **vasblokk-keret + TNT + tűzpor** (TNT-csille alapú).
- **Csak aktív raid alatt** sül el. Jobb katt = **pusztító, de terep-barát robbanás** a célzott
  pontra (sebzi az ellenfeleket, de **nem rombolja le a világot**). Raiden kívül nem működik.

### Frakció-reputáció

A frakciók **barátok vagy ellenségek** lehetnek (a szerver állítja, és raid alatt a hadakozók
automatikusan ellenségek). Ez a **piaci árakat** módosítja: ellenségtől **drágább** (+25%),
szövetségestől **olcsóbb** (−10%) vásárolni. Lásd: [Valuta és gazdaság](#3-valuta-és-gazdaság).

### Nekromanta lélekszilánk (csak Nekromantáknak)

Ha **Nekromanta-specet** játszol, minden megölt ellenség után **lélekszilánkot** kapsz:
- `/souls` — megnézed, hány szilánkod van.
- `/souls champion` — a szilánkokból **megerősített Wither-csontváz bajnokot** idézel (erősebb,
  mint a szokásos szolgák).

---

### Frakció-szállítmány — védd vagy rabold! 🐫

A király a kasszából **szállítmányt indíthat**: `/faction caravan send <összeg>`. A rakomány
egy kihirdetett **őrzőpontra** kerül (mindenki látja a koordinátákat!), és pár percig ott áll:

- Ha a szállítmány **túléli az ablakot**, a kassza a rakományt **nyereséggel** kapja vissza.
- Ha egy **ellenséges frakció** játékosa leöli a konvojt, a rakomány **az övék**.
- Saját frakciós „baleset" esetén a rakomány **elvész** — ne álljatok a saját karaván útjába!

Kockázat és jutalom: minél nagyobb a rakomány, annál édesebb célpont. Gyűjtsd a védőket!

### Becsület-párbaj és kém-álca ⚔🕵

- **Becsület-párbaj:** ha bűnös vagy, elégtételt ajánlhatsz: `/parbaj kihiv <név>`. Ha a sértett
  elfogadja (`/parbaj elfogad`), 3 perces, beleegyezéses párbaj indul — **nem termel bűnt**, és
  ha a bűnös nyer, **egy bűnpontja letörlődik**. Hetente legfeljebb kétszer.
- **Kém-álca:** `/kem <célfrakció>` — 60 másodpercre álnevet öltesz (felderítéshez!). Raid alatt
  nem megy, és **egyetlen ütés — adott vagy kapott — azonnal lebuktat**. Az álca a bűn alól nem
  ment fel: a lopás lopás marad.

### ⚔️ Harc-jelölés (combat-tag)

PvP-találatkor mindkét fél **12 másodpercre harc-jelölést** kap:

- a jelölt játékos **bemehet** a védett zónába, de ott sem kap PvP-védelmet — a
  vesztésre álló fél nem válik sebezhetetlenné egy fővárosba sétálással;
- a **komp nem indul**, amíg harcolsz.

A **hadi-ablak liga-pontja** és a **párbaj bűn-tisztulása** csak **valódi összecsapás**
után jár: a két félnek legalább **20 másodperce** harcban kell állnia egymással — az
egy-ütéses, megrendezett "váltott ölés" se pontot, se bűn-mosást nem ér.

---

## 12. Küldetések ✅

A **küldetések** kis feladatok, amikért **jutalmat** kapsz (kaszt-XP-t, pénzt — akár a
**saját frakciód valutájában** — vagy különleges hatást).

**Miféle feladatok lehetnek?** Szörny- és játékos-vadászat, blokk-bányászás és -lerakás,
craftolás, tárgygyűjtés (bányászva vagy a földről **felvéve**), evés-ivás, horgászat,
varázslás (enchant), állat-tenyésztés, **állat-szelídítés** (taming), **olvasztás** (kohó),
**falusi kereskedés**, terület-felkeresés, **bióm-felfedezés**, szint-elérés,
NPC-felkeresés, **tárgy-beszállítás NPC-nek** (odaadod neki, ő átveszi), **raid-győzelem**,
**világboss-ölés** és parkour-próba.

**Story:** a quest-NPC-k **beszélnek is** — a küldetés átvételekor és leadásakor a
történetükhöz illő sorokat mondanak (a képernyőn, a nevükkel).

Parancsok:
- `/quest list` — a felvehető és aktív küldetéseid.
- `/quest accept <id>` — felveszel egy küldetést.
- `/quest info` — megnézed az aktív küldetéseid állását.
- `/quest abandon <id>` — feladsz egy küldetést.
- `/quest log` (`gui`, `naplo`) — a grafikus **küldetésnapló** (lásd lentebb).

A haladásodat a képernyő alján (action bar) is követheted. Amikor teljesíted a feladatot, a
jutalom **automatikusan** jár.

### Kezdő küldetés-lánc (új játékosoknak) 🐣

Az **első belépésedkor automatikusan** elindul egy rövid, vezetett lánc: **Beszélj a
hírnökkel** (a semleges főváros hírnök-NPC-je — nála a királyság-választás is megnyílik) →
**Első csata** (ölj 5 szörnyet) → **Első gyűjtögetés** (gyűjts 10 rönköt). Minden lépés
teljesítésekor a **következő magától elindul**, és valutát (a végén csákányt + kenyeret is)
kapsz. Nem kell semmit beírnod — csak kövesd az action bar jelzéseit.

### Több feladat egy questben 🎯

Egy küldetés ma már **több feladatot** (objektívát) is tartalmazhat egyszerre — pl.
„ölj meg **10 szörnyet** ÉS gyűjts **16 kenyeret**”. Kétféle mód van:

- **ALL (párhuzamos):** bármely sorrendben, mindegyik feladatot teljesítened kell.
- **SEQUENCE (sorban):** mindig csak az **aktuális** lépés halad, a következő csak utána
  nyílik — ez a story-láncokhoz való.

A haladás **feladatonként külön** követhető: a HUD és a `/quest info` felsorolja az összeset,
pl. `Szörnyek 4/10 • Kenyér 8/16`.

### Küldetésnapló GUI 📓

A `/quest log` (aliasok: `gui`, `naplo`) egy **grafikus küldetésnaplót** nyit, **három füllel**:

- **Aktív** — a folyamatban lévő küldetéseid a haladásukkal; **shift-kattintással feladhatod** őket.
- **Felvehető** — a most elérhető küldetések; **kattintással felveszed**.
- **Teljesített** — a már befejezett küldetéseid.

A napló **lapozható**, ha sok küldetésed van.

### Választós párbeszéd (elágazó story) 💬

Egyes quest-NPC-k párbeszéde után **kattintható válaszopciók** jelennek meg a chatben. Amelyiket
választod, az **más-más következő küldetést** indít — így a történet elágazik aszerint, hogyan
felelsz.

### Napi NPC-kínálat (rotáció) 🔄

Néhány NPC egy nagyobb **quest-poolból** naponta csak **pár** küldetést kínál, és a kínálat
**naponta frissül**. Ezért érdemes **visszatérni** hozzájuk — máskor más feladatokat adnak.

### Ismétlődő és szezonális küldetések ♻️

- **Ismétlődő (repeatable):** teljesítés után egy **cooldown** (pl. naponta, 24 óránként)
  letelte után **újra felveheted** ugyanazt a küldetést. A hétköznapi napi NPC-küldetések
  **naponta rotálódnak**: a poolból minden nap más hármas érhető el — nézz vissza másnap!
- **Heti kihívások:** a napi küldetések mellett **hetente ismétlődő** feladatok is vannak —
  frakciónként saját heti feladat (pl. a Láng határjárása, a Fagy jégszürete), **beszállító**
  küldetések a vándor kereskedőnek (fa, kő, étel, bőr), és két nagy heti kihívás (a nagy
  hajtóvadászat, a nagy fogás) **láda-kulcs** jutalommal.
- **Szezonális:** szezononként **egyszer** teljesíthető; **új szezonban újra** elérhetővé válik.

### Jutalmak 🎁

A jutalom lehet **kaszt-XP**, **pénz** és **tárgy** is (nem csak XP vagy pénz). A pénz-jutalom
lehet mindig a **saját frakciód valutája** — így pl. a Piros frakció tagja piros tokent kap.

### Frakció-közösségi célok 🤝

A küldetések mellett vannak **szerver-szintű, közösségi célok** is: egy **megosztott számláló**,
amibe egy frakció (vagy az egész szerver) **minden tagja** beleszámít — pl. „a Piros frakció
együtt gyűjtsön **1500 vasat**”. Ezt **nem** kell külön felvenned: **automatikusan gyűlik**,
ahogy a normál játék közben teljesíted a hozzá tartozó tevékenységet. Amikor a cél elkészül,
az **egész frakció jutalmat kap a kasszába** + egy rövid **buffot**, majd a cél **újraindul**.

### Kaszt-próbák (a kezdő küldetések)

**Mind a 13 kasztnak** van kezdő próbája. Jutalom: **200 kaszt-XP**.

| Küldetés | Kaszt | Feladat |
|---|---|---|
| **A Harcos Próbája** | Harcos | Ölj meg **15 szörnyet** |
| **Az Íjász Próbája** | Íjász | Vadássz le **12 szörnyet** |
| **A Varázsló Próbája** | Varázsló | Szedj **10 virágot** |
| **Az Orgyilkos Próbája** | Orgyilkos | Ölj meg **10 szörnyet** |
| **A Druida Próbája** | Druida | Szaporíts **5 állatot** |
| **A Paplovag Próbája** | Paplovag | Ölj meg **15 szörnyet** |
| **A Halállovag Próbája** | Halállovag | Ölj meg **15 szörnyet** |
| **A Sámán Próbája** | Sámán | Vágj ki **10 rönköt** |
| **A Szerzetes Próbája** | Szerzetes | Ölj meg **15 szörnyet** |
| **A Pap Próbája** | Pap | Bányássz **10 fénykövet** |
| **A Boszorkánymester Próbája** | Boszorkánymester | Ölj meg **15 szörnyet** |
| **A Démonvadász Próbája** | Démonvadász | Ölj meg **15 szörnyet** |
| **A Sárkányidéző Próbája** | Sárkányidéző | Bányássz **10 ametiszt-fürtöt** |

### Mester-próbák (NPC-s láncok) 🧭

A kezdő próba után **mind a 13 kaszt** kétlépcsős mester-lánccal folytathatja:

1. **Mentor-küldetés:** vedd fel (`/quest accept <kaszt>_mentor`), keresd fel a kasztod
   **mester-NPC-jét** (a fővárosokban áll) és **beszélj vele**. Jutalom: **100 kaszt-XP**.
2. **Mester-próba:** ezt már **maga a mester adja** — ugyanaz a kattintás, amivel a
   mentor-küldetést teljesíted, azonnal kezedbe adja a próbát. A próba mindig a
   **kasztodhoz illő feladat** (vadászat, szelídítés, bűvölés…). Jutalom: **400 kaszt-XP**.

**Hogyan találod meg?** A quest-NPC-k felett **részecske-aura** világít — de **csak neked**,
ha éppen dolgod van velük:
- **Arany aura** ❕ — az NPC **questet tud adni neked** (minden feltételed megvan hozzá).
- **Zöld aura** — egy **aktív küldetésed** hozzá szól (beszélned kell vele).

Más játékos nem látja a te jelzéseidet, te sem az övéit.

| Kaszt | Mester | Mester-próba |
|---|---|---|
| Harcos | Aldric mester | 20 megerősített szörny (Lvl 2+) |
| Íjász | Lysa mesterasszony | 12 csontváz |
| Varázsló | Orvus főmágus | 3 tárgy megbűvölése |
| Orgyilkos | A Névtelen | 15 megerősített szörny (Lvl 2+) |
| Druida | Ylvara, a Vén Tölgy | 3 vad megszelídítése |
| Paplovag | Seratiel lovag-parancsnok | 20 szörny (Lvl 3+) |
| Halállovag | Morvran, a Fagyott Penge | 15 elit szörny (Lvl 4+) |
| Sámán | Tharkun, a Viharlátó | 12 rézrúd kiolvasztása |
| Szerzetes | Csendes Jin apát | 12 szörny ÉS 5 hal |
| Pap | Elenora főtisztelendő | 3 tárgy megbűvölése |
| Boszorkánymester | Az Alkuszó | 16 lélekhomok |
| Démonvadász | Karyx, a Megjelölt | 18 szörny (Lvl 3+) |
| Sárkányidéző | Vaelith, a Pikkelyes Bölcs | 24 ametisztszilánk |

> A mester-NPC-k **kihelyezése a szerver-csapat feladata** — ha még nem állnak,
> a lánc egyszerűen nem halad (a küldetés nem törik el). A **parkour** opcionális
> szabadidős tartalom maradt (pl. akrobata-kihívás) — a kaszt-fejlődés nem függ tőle.

### Sötét Beavatás (a Nekromanta kapuja)

- **Sötét Beavatás:** zarándokolj el **Thanaopolisba, a Holtak Városába** (a Kitaszítottak területére).
  Jutalom: **100 kaszt-XP**. **Ezt teljesítve nyílik meg a Nekromanta specializáció** (Sötét
  frakció + bűnös állapot is kell hozzá).

### Vezeklés-lánc (a sötét paktum megtörése) 🙏

Ez az **egyetlen mód**, hogy egy bűnös (sinner) játékos megszabaduljon a **sötét paktumtól**.
Három részből áll, sorban:

| Rész | Feladat | Jutalom |
|---|---|---|
| **Vezeklés I — A Penge** | Pusztíts el **30 erős szörnyet** (min. Lvl 2) | 150 kaszt-XP |
| **Vezeklés II — Az Alázat** | Fogj ki **20 halat** | 150 kaszt-XP |
| **Vezeklés III — A Feloldozás** | Győzz le **50 elit szörnyet** (min. Lvl 4) | 400 kaszt-XP + 100 Creutzér + **a paktum megtörik!** |

A harmadik rész végén **lekerül rólad a bűnös jelölés** — feloldozást nyersz, és újra szabad
vagy.

---

### Fejezetek — a szezon története 📖

Néhány küldetés egy-egy **fejezethez** (szezonhoz) kötődik: csak az adott szezon alatt
vehetők fel, és a történetük a szezon ívét meséli. Szezonváltáskor a krónika **továbblapoz**:
a régi fejezet küldetései lezárulnak (amit már felvettél, azt még befejezheted!), és új
fejezet-küldetések nyílnak. Figyeld a „📖 Új fejezet nyílik” üzenetet!

### Rejtvény-küldetések — a cél rejtve marad 🧩

Néhány küldetés **rejtvény**: a leírás egy versbe/találós kérdésbe rejti, mit kell tenned —
a napló és a haladás-sor **soha nem árulja el a célt** (csak „???” látszik). Ha megfejted és
megteszed, a küldetés magától teljesül. Ha elakadsz: gondolkodj, járj utána, vagy kérdezz
más játékosokat — a közös fejtörés a játék része. Jelenleg 30 rejtvény vár megfejtőre.

### Bestiárium — a krónikás-lajstromod 📜

A `/bestiarium` paranccsal megnyílik a **lajstromod**: a krónikások számon tartják, hány
szörny-FAJT ejtettél el, hány receptet készítettél el először, hány territóriumot jártál be,
és hány világboss-archetípust győztél le. A **mérföldkövek** (pl. 10/25/50 faj) veret-jutalmat
adnak a kezedbe, a nagy mérföldköveknél az egész szerver értesül. Hónapokra ad gyűjtögető-célt!

> 🛟 **Ha egy mesélő (NPC) eltűnne:** a küldetés-rendszer nem akad el — a `/quest talk
> <npc-név>` paranccsal a beszélgetés-célok teljesíthetők és a küldetések felvehetők,
> a párbeszéd-sorokkal együtt. A parancs csak akkor él, ha az NPC-k tényleg nem elérhetők.

---

## 13. Frakcióterületek és saját birtok 🏰

A világban vannak **frakcióterületek** és **fővárosok** (ezeket az adminok jelölik ki), és
**saját birtokod** is lehet: a `/claim` paranccsal blokk-pontos területeket foglalhatsz, ahol senki más nem
építhet és nem lophat.

### Mit veszel észre belőlük?

- Amikor **átlépsz** egy terület határán, a képernyő alján (action bar) egy felirat jelzi, hol
  vagy: pl. „✦ Piros főváros ✦", „⛨ védett város ⛨", vagy „vadon" ha senkié.
- Minden frakciónak lehet **1 fővárosa** és több **területe**.
- A területek lehetnek **kör** alakúak vagy **poligonok** (pontos határvonal, pl. egy városfal
  mentén kijelölve) — ezt az adminok döntik el.

### Zónatípusok

A térkép védelme több **zónatípusra** épül:

| Típus | Ki építhet itt? | Lehet ide claimelni? |
|---|---|---|
| **Frakcióterület** | Csak az adott **frakció tagjai** | ✅ Igen (a saját birtokod ide is mehet) |
| **Védett frakcióterület** | **Senki** (a frakció magja: falak, műemlékek) | ❌ Nem |
| **Védett város** | **Senki** (jellemzően semleges városok) | ❌ Nem |
| **Főváros** | **Senki** (egyben a bank/valutaváltó helyszíne) | ❌ Nem |
| **Kárhozat-zóna** ☠ | **Senki** (PvPvE senkiföldje — lásd lent) | ❌ Nem |
| **Kazamata** 🗝 | **Senki** (kulccsal járható dungeon: 2 órás futam, 7 napos pecsét) | ❌ Nem |

> ⛨ A **védett zónák** (védett város/frakcióterület és a főváros) a térkép **pajzsa**: ott
> alapból **senki** sem építhet/bonthat, nincs interakció, **nincs PvP**, robbanás és tűz sem
> tesz kárt, és **claimelni sem lehet**. Csak az admin/builder jogok kerülik meg.

#### ☠ Kárhozat Kapuja — a PvPvE senkiföldje

A kódex szerint a Hetedik Vérháborút kirobbantó **óriás Nether-portál** környéke a szerver
legveszélyesebb zónája. Itt minden másképp működik:

- **A PvP legális** — a zónában bárki megtámadhat bárkit, és az ölés **nem számít bűnnek**
  („a Kapunál nincs törvény"). A frakciók itt nyíltan összecsaphatnak.
- **Belépő-védelem:** a zónába lépve pár másodperc PvP-védelmet kapsz (spawn-kill ellen) —
  de aki maga támad, azonnal elveszti.
- **A szörnyek erősebbek**: a zónában spawnoló mobok bónusz szinteket kapnak — cserébe a
  magasabb szint jobb lootot ér (a Néma Királynő élőhalottaitól a nevesített relikvia-drop is
  eshet).
- Az aréna maga védett: **építeni nem lehet**, robbanás és tűz nem rongálja — de ajtók,
  oltárok szabadon használhatók.
- Belépéskor baljós hang és hamu-örvény jelzi, hogy a Kapu árnyékába értél.

**Frakcióterületen** csak az adott frakció tagjai építhetnek (mások nem), viszont ide a
játékosok **saját birtokot (`/claim`) is foglalhatnak** — így a claim rendszer és a
territórium rendszer együtt működik.

#### ⛩ Az egyetlen kapu — nether-portál szabály

Ezen a világon **új nether-portált nem lehet gyújtani** — sehol. A világ **egyetlen élő
kapuja a Kárhozat Kapuja**: aki a Netherbe akar jutni, annak a **senkiföldjén át** vezet
az útja, ahol a PvP legális és az ölés nem bűn. A kapu használata tehát mindig kockázat —
pontosan úgy, ahogy a régiek mesélik.

### Mi tiltható zónánként?

Az adminok **zónatípusonként külön-külön** állíthatják, mi szabad az adott zónában
(`territory.protection.rules` a configban, egyértelmű kulcsokkal: `allow-<szabály>: true` =
szabad, `false` = tilos):

| Szabály | Mit tilt le | Frakcióterület alapból | Védett zóna alapból |
|---|---|---|---|
| **build** | blokk törése/rakása, vödör, kép-/festménykeret, armor stand | 🔒 nem-tagoknak | 🔒 mindenkinek |
| **interact** | konténer/ajtó/gomb/kar/műhely jobbklikk | 🔓 szabad | 🔒 mindenkinek |
| **pvp** | játékos↔játékos sebzés (biztonságos zóna) | 🔓 szabad (harc mehet) | 🔒 tilos |
| **explosions** | creeper/TNT/kristály blokk- és dekoráció-kára | 🔓 mehet | 🔒 tiltva |
| **fire** | tűz gyújtása/terjedése/égése | 🔓 mehet | 🔒 tiltva |

> Így pl. egy „védett város" teljes biztonságos zóna (se rombolás, se PvP, se tűz), egy
> frakcióváros viszont csak a nem-tagok építését tiltja, de a harc és a nyílt interakció megy.

A védelem a „hátsó ajtókat" is lefedi: védett zónában a **mob-grief** (enderman), a kívülről
**befolyó víz/láva** és a **dugattyú** sem módosítja a terepet (a `build` szabály alá esnek); a
**PvP** a közelharcon túl a **nyílra/lövedékre, háziállatra, TNT-re és ártó bájitalra** is áll.

#### Megkerülő jogok (bypass)

- **`icesmp.admin.territory.bypass`** — mindent megkerül (build, interakció, **PvP** is).
- **`icesmp.territory.builder`** — **építő-jog**: a védett zónában is **építhet és
  interaktálhat** (de a PvP-tiltás rá is vonatkozik). Az építő-csapatnak ideális, admin-jog nélkül.

> Ha valahol nem tudsz blokkot lerakni/törni: vagy egy másik frakció területén állsz, vagy egy
> védett zónában (főváros/védett város).

#### Caldestera városi törvénye

A semleges fővárosban **nyílt fegyvert egyik kezedben sem** tarthatsz (kard, balta, szigony,
íj, számszeríj, buzogány — az **offhand is** beleszámít): az őrség elrakatja a hátizsákodba,
egy kijelölt, nem aktív slotba. Ha a hátizsákod tele van, nem dobja el és nem is teszi vissza
a kezedbe — **helyet kell csinálnod**, addig figyelmeztetést kapsz. Körözötteket a kapunál
visszafordít az őrség (hacsak nincs náluk menlevél).

### Saját birtok — terület-claim 🏠

A **claim** a te személyes, védett földed — frakciótól függetlenül bárki claimelhet.

#### Hogyan claimelj?

1. Állj oda, ahol a birtokod közepét szeretnéd.
2. Írd be: **`/claim`** — egy **16×16 blokkos** területet foglal körülötted, **±20 blokk
   magasságban** (a claim egy doboz: fölötte/alatta a világ szabad — a menüből pénzért
   magasíthatod/mélyítheted +5 blokkonként). A határokat részecskék rajzolják ki.
   **Blokk-pontos, egyedi méretű** birtokhoz: állj a terület egyik sarkára (`/claim pos1`),
   a másikra (`/claim pos2`), majd `/claim area` — a méretet és az árat előre kiírja.
   **Kényelmesebb út — a Birtokmérő pálca** (`/claim wand`): bal kattintás a blokkra =
   1. sarok, jobb kattintás = 2. sarok (a méret és az ár azonnal megjelenik), majd
   **SNEAK + jobb kattintás = foglalás**. Nem kell a sarkokra odaállni — elég rájuk mutatni!
   Claim-határ átlépésekor az action-bar mutatja, kinek a birtokára léptél.

**Mennyibe kerül?**
- Az ár **oszloponként** (1×1 blokk alapterület) számolódik: az első **768 oszlop ingyenes** (~3 chunknyi terület).
- Utána minden további oszlop a **saját frakció-valutádba** kerül, **fix 0,5/oszlop** áron
  (nem drágul oszloponként — csak a megvett oszlopok számával nő a végösszeg). Az ár **ELÉG**
  (money sink) — az `/claim unclaim`-nél sem jár vissza!
- Játékosonként alapból legfeljebb **8192 oszlopnyi** (~32 chunknyi) birtokod lehet.

#### Mit véd a claim?

A birtokodon (a claim dobozán belül) **idegenek**:
- nem törhetnek és nem rakhatnak le blokkot;
- nem nyithatnak **konténert** (láda, hordó, kemence…);
- nem üríthetnek vödröt, nem szedhetik le a kép-/festménykereteket;
- a **robbanás sem bontja** a claimelt blokkokat, és a blokk-evő mobok (pl. enderman) sem
  vihetnek el semmit;
- a **tűz** nem gyullad meg, nem terjed és nem éget claimelt blokkot;
- kívülről **folyadék** (víz/láva) nem folyhat be, és idegen **dugattyú** sem tolhat be /
  húzhat ki blokkot (a claimen belüli saját gépek működnek).

> ⚔️ **Fontos:** a claim a **PvP-t NEM tiltja** — ez háborús szerver! A claim csak az
> **építést és a lopást** védi, harcolni a birtokodon is lehet.

#### Megbízottak (trust)

- `/claim trust <név>` — a megbízott **teljes hozzáférést** kap **minden** claimedhez
  (építhet, nyithat ládát).
- `/claim untrust <név>` — megbízás visszavonása.
- **GUI-ból is megy:** `/menu` → Birtok → **„Megbízottak kezelése"** — felül a megbízottaid
  (kattintás = visszavonás), alul a közeledben álló játékosok (kattintás = megbízás).

#### Hasznos claim-parancsok

| Parancs | Mit csinál |
|---|---|
| `/claim` | 16×16 blokk gyorsfoglalása körülötted (±20 blokk magasságban) |
| `/claim unclaim` | A claim felszabadítása, amiben állsz (az ár NEM jár vissza) |
| `/claim info` | Kié ez a terület? (+ határ-kirajzolás) |
| `/claim list` | Saját claimjeid listája |
| `/claim show` | A környező claimek PEREMÉNEK kirajzolása pár másodpercig (zöld = sajátod, láng = másé, komposzt = a gyorsfoglalás előnézete) |
| `/claim pos1` / `/claim pos2` | Blokk-pontos kijelölés két sarka (a blokk, amin állsz) |
| `/claim wand` | **Birtokmérő pálca** — sarok-kijelölés kattintással, SNEAK+jobb = foglalás |
| `/claim area` | A két sarok közti pontos téglalap lefoglalása (az ár előre kiírva, egyben ég el) |
| `/claim extend up\|down` | A claim magasítása / mélyítése +5 blokkonként, pénzért (a menüből is) |
| `/claim trust <név>` / `/claim untrust <név>` | Megbízott hozzáadása / elvétele |

#### Hol NEM lehet claimelni?

- **Védett zónában**: főváros, védett város, védett frakcióterület (spawn/város).
- **Frakcióterületen viszont IGEN** — a saját birtokod a frakciód földjén is elférhet
  (kivéve, ha a szerver a `claims.block-in-territory` kapcsolóval ezt is letiltja).
- Cserébe a **meteor-becsapódás** és az **elrejtett kincs** esemény is elkerüli a claimelt
  területet — a birtokod biztonságban van tőlük.
- **Raid alatt** a claim alapból véd, de szerver-beállítástól függően a jelentkezett támadók
  a claim-ládákat hadizsákmányként **kinyithatják** (lebontani akkor sem tudják).

### Admin parancsok (csak adminoknak)

Kör-zóna a pozíciódnál, vagy pontos **poligon** a bejárt határpontokból:

- `/territory pos` — határpont hozzáadása a jelenlegi pozíciódnál (poligonhoz, akár 10+ pont).
- `/territory undo` / `clearpoints` / `points` — a határpont-puffer kezelése.
- `/territory create <típus> <frakció> <id> [név...]` — poligon-zóna lezárása a pontokból.
- `/territory circle <típus> <frakció> <id> <sugár> [név...]` — kör-zóna a pozíciódnál.
- `/territory create doom-gate <id> [név...]` / `/territory circle doom-gate <id> <sugár> [név...]`
  — a Kárhozat-zóna frakció-semleges, ezért ott a `<frakció>` argumentum elhagyható.
- `/territory setcapital <frakció> <sugár> [név...]` — főváros (kör) gyorsan.
- `/territory show [id]` — határrajz: a puffered + az aktuális zóna, vagy a megadott zóna.
- `/territory tp <id>` — teleportálás a zóna középpontjához.
- `/territory rename <id> <név...>` — zóna átnevezése.
- `/territory resize <id> <sugár>` — kör-zóna új sugara (poligonra nem működik).
- `/territory settype <id> <típus>` — zóna típusának megváltoztatása.
- `/territory sety <id> <minY> <maxY>` — magassági sáv beállítása (`~` = korlátlan; pl.
  `/territory sety varos 60 ~` = csak 60-as magasságtól felfelé véd).
- `/territory remove <id>` — zóna törlése.
- `/territory list` / `/territory info` — zónák listája / az aktuális zóna infója (magassággal).
- `/claim admin unclaim` — idegen claim törlése admin-jogon (a claimben állva).

> **Típusok:** `faction` (csak tagok építhetnek), `protected-faction` / `protected-city`
> (senki), `capital` (főváros), `doom-gate` (Kárhozat-zóna: PvPvE senkiföldje).
> A városfal mentén így pontosan kijelölhető a terület: járd
> körbe a falat, minden saroknál `/territory pos`, végül `/territory create protected-city ...`.
> A rendszer figyelmeztet, ha a határvonal **önmagát keresztezné** (összegabalyodott fal).

---

### Thanaopolis — a holtak fővárosa 💀

A Kitaszítottak fővárosa a lore szerint is az élőholtaké: a territóriumban **folyamatosan
magas szintű undead-horda** kóborol, akik **nappal sem égnek el**. A Kitaszítottakat a Néma
Királynő népe békén hagyja — mindenki más csak saját felelősségére lépjen be. A látvány
(és a loot) megéri… ha túléled.

---

## 14. Parancsok listája 📜

Itt minden parancsot megtalálsz egy helyen. A `< >` közé **te írsz be** valamit; a `[ ]` azt
jelenti, hogy **elhagyható**.

> ## 🖱️ A legegyszerűbb út: `/menu`
> Nem kell parancsokat gépelned! Írd be: **`/menu`** (vagy `/hub`), és egy **kattintós
> főmenü** nyílik meg, ahonnan minden rendszer egy gombnyomásra elérhető, tematikus sorokba
> rendezve: **Karakter** (Karakterlap, Varázskönyv, Társ, Küldetésnapló, Napi küldetés,
> Elérések, Ranglisták) • **Közösség & világ** (Frakció, Csapat, Birtok, Események,
> Körözési lista, Relikviák, Lélekszilánk) • **Gazdaság** (Bank & Pénz, Piac — és
> adminoknak egy admin panel). Minden almenüben gombokkal intézhetsz mindent —
> a háttérben ugyanazokat a parancsokat futtatja, amiket lent látsz.

### Mindennapi parancsok (mindenkinek)

| Parancs | Aliasok | Mit csinál |
|---|---|---|
| `/menu` | `hub`, `m` | **Központi kattintós menü** — innen minden elérhető |
| `/leaderboard` | `lb`, `top`, `rangsor` | Ranglisták: leggazdagabb, legmagasabb szint, legtöbb raid-kill |
| `/achievements` | `ach`, `eleresek` | Elérések (mérföldkövek + jutalmak) |
| `/stats [név]` | — | Statisztika-profil: ölések, halálok, K/D, mob-ölések, castolt spellek, teljesített questek |
| `/hud <szekció>` | — | HUD-oldalsáv szekciók ki-be kapcsolása (frakcio/kaszt/eroforras/esemeny/valuta/csapat/mind) |
| `/sit` / `/sit fel` | — | Leülés vagy felállás; támogatott lépcsőre, fél-lapra, szőnyegre/mohaszőnyegre és hórétegre üres kézzel jobb-katt is leültet |
| `/afk` | — | Önkéntes AFK-jelölés ki/be |
| `/crates` / `/crate` | `ladak` | Read-only ládalista-GUI; kattintással jutalom-preview |
| `/crate buy <id> [db]` / `/crate info <id>` / `/crate preview <id>` | — | Kulcsvásárlás, required key/cooldown/mass-open info és esélylista. Főkézből jobb katt a fizikai ládára = nyitás; lopakodva katt = engedélyezett többszörös nyitás |
| `/report <név> <ok>` | `bejelent` | Játékos bejelentése a moderátoroknak (percenként egyszer) |
| `/daily` | `napi` | A napi küldetés és haladásod |
| `/pet [menu\|item\|summon\|dismiss\|name\|stance\|info]` | `tars`, `companion` | Társ-GUI (üresen), befogó eszköz, idézés, név, állásmód (Vadmester / Nekromanta / Szentségtelen / Boszorkánymester) |
| `/parkour list\|start <id>` | `trial`, `palya` | Időmérős parkour-pályák |
| `/market search <szöveg>` | `piac`, `ah` | Keresés a piacon (a /market mellett) |
| `/profile` | `karakter`, `char`, `status` | A **karakterlap** — kaszt, spec, szakma, talent, képesség-fa menük |
| `/faction join <frakció>` | `/f` | Belépés egy frakcióba (`red`/`blue`/`neutral`) |
| `/faction leave` | `/f` | Kilépés a frakcióból |
| `/faction king vote <játékos>` | `/f` | Szavazás a frakciód királyára |
| `/bank balance` | `wallet`, `vault` | Banki egyenlegeid |
| `/bank deposit` | | Tokenek bankba helyezése (**csak fővárosban**) |
| `/bank withdraw <valuta> <összeg>` | | Tokenek kivétele (**csak fővárosban**) |
| `/currency balance` | `money`, `eco` | Egyenleg gyorsnézet |
| `/currency pay <játékos> <összeg> [valuta]` | | Közvetlen utalás — **alapból kikapcsolva** (KP-gazdaság) |
| `/currency exchange <összeg> <honnan> <hová>` | | Valutaváltás (**csak fővárosban**) |
| `/currency rates` | | Aktuális árfolyamok |
| `/market` | `piac`, `ah` | Piactér böngésző (vásárlás / licitálás) |
| `/market sell <ár> [valuta]` | | A kézben tartott tárgy eladása |
| `/market auction <kikiáltási ár> [óra] [valuta] [buyout:<ár>]` | | Aukció indítása a kézben tartott tárgyra (a `buyout:` opcionális azonnali-vétel ár) |
| `/market claim` | | Megnyert / visszajáró aukciós tárgyak átvétele |
| `/market cancel` | | Saját eladásaid visszavonása (élő licites aukció nem) |
| `/adomany` | `donate`, `adomanylada` | Közösségi adomány-láda böngésző (ingyenes elvétel) |
| `/adomany add` | | A kézben tartott tárgy (teljes stack) adományozása a közös ládába |
| `/spec list` / `/spec choose <id>` | `specialization`, `specializacio` | Specializációk |
| `/spec respec <class\|profession>` | | Specializáció visszaváltása |
| `/talent` / `/talent spend <class\|profession> <talent>` | `talents`, `talentfa` | Talentek |
| `/emlek` / `/emlek xp\|talent\|spec\|lore` | `memory`, `emlekek` | Emlékszilánk-beváltás: kaszt-XP / talentpont / spec-kapu / emlék-töredék |
| `/suttogas <üzenet>` / `/suttogas vád <játékos>` | `sutt` | A Suttogók (és a Kitaszítottak) titkos csatornája / tanú-vád (K9). A vád-alparancs `vad` és `accuse` alakban is megy, hogy ne kelljen ékezetet gépelni |
| `/lore <téma>` | `kodex` | A kódex lapjai chatben (frakciók, a Fa, a Kapu, a Suttogók) |
| `/kronika` | `chronicle` | Az utolsó Heti Krónika visszaolvasása (liga-állás, toplisták) |
| `/profession join <szakma>` / `/profession info` | `prof`, `szakma` | Szakmák |
| `/profession recipes` | `prof`, `szakma` | **Recept-könyv** — tanult/zárolt receptek, 1 kattintásos craft |
| `/quest list` / `/quest accept <id>` / `/quest info` | `quests`, `kuldetes` | Küldetések |
| `/quest talk <npc-név>` | `quests`, `kuldetes` | NPC-tartalék-út: küldetés-beszélgetés/átadás NPC-plugin nélkül (csak ha a mesélők nem elérhetők) |
| `/quest log` | `gui`, `naplo` | **Küldetésnapló GUI** — Aktív / Felvehető / Teljesített fülek, lapozható |
| `/souls` / `/souls champion` | `soul`, `lelek` | Lélekszilánk (csak Nekromanta) |
| `/soulforge` / `/soulforge fejleszt <ág>` | `lelekkovacs` | **Lélek-kovács** (csak Nekromanta): minion-fejlesztési ágak szilánkért |
| `/bounty` | `fejvadasz`, `korozes` | Körözési lista: ki körözött és mennyit ér a feje |
| `/ceh` / `/ceh alapit\|meghiv\|elfogad\|elhagy\|kirug\|befizet` | `guild`, `gild` | **Céh**: frakción belüli kisközösség (közös XP, céh-szint) |
| `/bestiarium` | `bestiary`, `lajstrom` | **Bestiárium GUI**: elejtett fajok, receptek, territóriumok, bossok — mérföldkő-jutalmakkal |
| `/szakmacel` | `weeklygoal` | A szakmád heti közös célja: állás + a saját hozzájárulásod |
| `/parbaj kihiv <név>` / `/parbaj elfogad\|elutasit` | `duel` | **Becsület-párbaj**: bűnösként elégtétel — győzelemért −1 bűnpont |
| `/kem <célfrakció>` | `spy` | **Kém-álca**: rövid álruha felderítéshez (egy ütés lebuktat) |
| `/market ereklye` | | A piac **ereklye-börze** szűrője (szilánkok, unique anyagok) |
| `/spell` / `/spell upgrade <id>` | `mastery`, `mesterseg` | Spell-mesterség: valutáért rövidebb cooldown ÉS erősebb hatás (sebzés, gyógyítás, effekt-időtartam) |
| `/spellbook` | `varazskonyv`, `konyv`, `sb` | **Varázskönyv**: spellek böngészése (leírás, költség, sebzés, CD) és kiválasztása kattintással. *Sunyíts + jobb katt a Lélekkapcson* is megnyitja. |
| `/events status` | `event`, `esemeny` | „Mi történik most?" — minden aktív világesemény + szezon-állás egyben |
| `/komp [útvonal]` | `ferry` | Átkelés a kompon (a kikötőben állva); útvonal nélkül a járatok listája |
| `/tanacs [szavaz <játékos>\|vasarhet]` | `council` | A Menedék Vének Tanácsa: heti szavazás, tanácstagként Vásár-hét |
| `/events season` / `/events blood-moon` / `/events caravan` | `event`, `esemeny` | Világesemények állása (szezon, vérhold, kereskedő-karaván) |
| `/party invite\|accept\|decline\|leave\|list` | `p`, `parti` | **Party (csapat)**: meghívás, csatlakozás, kilépés, taglista (max 5 fő) |
| `/party kick\|promote <név>` | | Csapatvezetői műveletek: kirúgás, vezetés átadása |
| `/party disband` | | Csapat feloszlatása (csak vezető) |
| `/p <üzenet>` | | **Csapat-chat** — csak a párttagok látják |
| `/claim` | `birtok` | 16×16 blokk gyorsfoglalása körülötted (**saját birtok**, ±20 blokk magasan) |
| `/claim unclaim\|info\|list\|show` | | Claim felszabadítása / infó / lista / határ-kirajzolás |
| `/claim pos1\|pos2\|area` | | Blokk-pontos terület kijelölése és foglalása |
| `/claim wand` | `palca` | **Birtokmérő pálca**: bal katt = 1. sarok, jobb = 2. sarok (ár-előnézet), SNEAK+jobb = foglalás |
| `/claim extend up\|down` | | Claim magasítása / mélyítése (+5 blokk, pénzért) |
| `/claim trust\|untrust <név>` | | Megbízott hozzáadása / elvétele (teljes hozzáférés a claimjeidhez) |

### Király-parancsok (csak a frakció királyának)

| Parancs | Mit csinál |
|---|---|
| `/faction treasury withdraw <összeg>` | Kivétel a frakciókasszából (veretben a kézbe, napi kerettel) |
| `/faction king tax <százalék>` | Frakció adókulcs beállítása |
| `/faction raid <célfrakció> [terület]` | Háború (raid) hirdetése — alapból a védő fővárosáért |
| `/faction caravan send <összeg>` | **Játékos-karaván**: rakomány indítása a kasszából — sikeres kíséretnél +25% érkezik vissza |

A raidhez **mindenki** (nem csak a király) így kapcsolódik:

| Parancs | Mit csinál |
|---|---|
| `/faction raid join` | Jelentkezés harcosnak a felkészülés alatt (max 10/oldal) |
| `/faction raid status` | Raid-állás: fázis, pontok, létszám |

### Privát üzenetek

| Parancs | Mit csinál |
|---|---|
| `/msg <játékos> <üzenet>` | Privát üzenet; aliasok: `/tell`, `/w` |
| `/reply <üzenet>` | Válasz az utolsó ténylegesen kézbesített privát beszélgetésre; alias: `/r` |

### Admin parancsok (csak adminoknak)

| Parancs | Mit csinál |
|---|---|
| `/icesmp reload` | Konfiguráció újratöltése; a natív MOTD snapshotja és 64×64 ikonkészlete is újraépül, a régi async generáció nem írhatja felül az újat |
| `/icesmp inspect <név>` | Teljes játékos-riport: kaszt/erőforrás/statok/bűn/claim/questek/cooldownok |
| `/invsee <név> [read|edit] [main|ender]` | Online live inventory/ender nézet; read és edit külön permission |
| `/warn`, `/kick`, `/mute`, `/unmute`, `/ban`, `/tempban`, `/unban` | Egységes natív punishment műveletek; `/history` és `/punishments` a lekérdezés |
| `/moderation` | Permission-szűrt moderációs admin GUI |
| `/socialspy` / `/vanish [név]` | Tartós SocialSpy és admin vanish |
| `/offlinetp <név>` | Utolsó ismert kijelentkezési helyre teleport |
| `/reports` / `/reports resolve <id>` | Játékos-bejelentések listája és lezárása |
| `/class addxp\|setxp <játékos> <mennyiség>` | Kaszt-XP adása/beállítása |
| `/class givecatalyst\|unlockspell <játékos> [spell]` | Lélekkapocs adása / spell feloldása |
| `/class admin <resetcd\|unlockallskills\|resetskills\|resetclass> <játékos>` | Cooldown-/spell-/**teljes kaszt-reset** egy játékosnak |
| `/profession set\|clear\|addxp` | Szakma-adatok kezelése |
| `/profession blueprint <játékos> <recept-id>` | Tervrajz (recept-feloldó) átadása egy játékosnak |
| `/spec reset <játékos>` | Specializációk törlése |
| `/sinner <játékos> set\|clear\|add` | Bűnös állapot kezelése |
| `/quest complete <játékos> <id>` | Küldetés azonnali teljesítése |
| `/quest admin create <id> <objektíva> <darab> <név...>` | Új küldetés készítése játékon belül (több-objektívás is lehet) |
| `/quest admin addobjective <id> <objektíva> <darab> [leírás]` | További feladat hozzáadása egy questhez |
| `/quest admin set <id> objectives-mode ALL\|SEQUENCE` | Több-objektívás mód: párhuzamos vagy sorban |
| `/quest admin set <id> <mező> <érték...>` | Küldetés-mező beállítása (feltétel, jutalom, giver-npc, dialogue.choices, rotation-group, seasonal...) |
| `/quest admin delete/info/list` | Admin-küldetés törlése / megtekintése / listája |
| `/quest admin builder <id>` | **Kattintós küldetés-szerkesztő** — új id-vel létrehozó varázsló (objektíva-választó + chat-bevitel), létező custom questtel mező-szerkesztő GUI |
| `/currency set <játékos> <összeg> [valuta]` | Valuta-egyenleg beállítása |
| `/faction set <játékos> <frakció>` | Játékos frakcióba sorolása |
| `/relic give <játékos> <relic_id>` | Relikvia adása |
| `/territory circle\|create <típus> ...` | Kör- vagy poligon-zóna kijelölése (típus: faction, protected-faction, protected-city, capital, doom-gate, dungeon) |
| `/territory pos\|undo\|clearpoints\|points\|show [id]` | Poligon-határpontok bejárása és előnézete (pl. városfal mentén) |
| `/territory tp <id>` | Teleportálás a zóna középpontjához |
| `/territory dungeonchest [tábla]` | A nézett láda/hordó regisztrálása kazamata-kincsesládának (újra kiadva törli) |
| `/territory dungeonboss <zóna-id> [tábla]` | Kazamata mini-boss spawn-pont kijelölése (clear <zóna-id> = törlés) |
| `/territory rename\|resize\|settype\|sety <id> ...` | Meglévő zóna módosítása (név / sugár / típus / magassági sáv) |
| `/territory setcapital\|remove\|list\|info` | Főváros gyorskijelölés / zóna törlése / listája / infó |
| `/exchangeboard place\|remove` | Árfolyamtábla lerakása/törlése |
| `/events blood-moon start\|stop` | Vérhold kézi indítása / leállítása |
| `/events worldboss` | Világboss azonnali megidézése |
| `/events invasion` | Szörny-invázió azonnali indítása |
| `/events caravan arrive\|depart` | Kereskedő-karaván kézi indítása / elküldése |
| `/events ambient` | Hangulat-esemény azonnali kiváltása |
| `/events gathering` | Gyűjtögető buff-ablak megnyitása |
| `/events treasure` | Elrejtett kincs elhelyezése |
| `/events wild-hunt` | Vad Hajsza fenevad megidézése |
| `/events abundance` | Bőség-idő indítása |
| `/events challenge` | Kollektív szerver-kihívás indítása |
| `/events escort` | Karaván-kíséret (konvoj) indítása |
| `/events meteor` | Meteor-becsapódás kiváltása |
| `/events stranger` | A Rejtélyes Idegen megidézése |
| `/events spawnpoint add\|remove\|list` | Esemény-spawnpontok (pl. állandó világboss-aréna) — mód: `world-events.anchors.*` |
| `/events cultists` | Kultista esemény azonnali indítása (portya / rítus / hírvivő sorsolással) |
| `/events corruption` | Rontás-góc azonnali nyitása a közeledben |
| `/events archeology` | Régészeti lelőhely azonnali felbukkanása |
| `/events intro [játékos]` | Bemutató újrajátszása |
| `/iceitem <unique\|recept\|relikvia\|tervrajz\|erszeny> <id> [db] [játékos]` | Bármely plugin-item admin-adása |
| `/icesmp config menu` | Kattintható élő-config szerkesztő; a „Szerverlista és MOTD” kategória kezeli az enable, TIME/RANDOM, rotáció, vanish count és ikonmód kulcsokat |
| `/claim admin unclaim` | Idegen claim törlése admin-jogon |
| `/parkour setstart\|setfinish\|remove <id>` | Parkour-pálya beállítása |
| `/npcbind <npc> quest\|shop\|bank\|exchange\|clear` (`npckotes`) | NPC explicit kötése küldetéshez/bolthoz/bankárhoz/valutaváltóhoz (a bank/exchange a meglévő bank menüt nyitja) |
| `/npcbind list` | Minden NPC-kötés kiírása |
| `/crate set <id>` / `/crate remove` | A nézett blokk crate-helyként mentése vagy törlése |
| `/crate give <játékos> <id> [db]` / `/crate list` | PDC-kulcs átadása / tartós fizikai helyek listája |
| `/crate stats <játékos|uuid> [id]` | Nyitási statisztika lekérdezése |
| `/crate resetstats <játékos|uuid> [id|all]` / `/crate status` | Stat/cooldown reset / valid config és hibák |

---

#### DEV item tesztelése

- `/iceitem dev csodalatos_bingulus 1 <játékos>` — a Csodálatos Bingulus hiteles példányának
  kiadása. Csak a `dev-items.yml`-ben rögzített tulajdonos lehet célpont; jog: `icesmp.admin.item`.

---

## 15. Party (csapat) 👥

WoW-stílusú **csapat**: legfeljebb **5 fő**, teljesen **frakciótól függetlenül** — bármelyik
frakcióból lehet valaki a csapatodban, akár egy Piros és egy Kék is együtt kalandozhat.

### Hogyan alakíts csapatot?

1. **Hívd meg** a barátodat: `/party invite <név>` (aki meghív, az lesz a **csapatvezető**).
2. A meghívott **elfogadja**: `/party accept` (vagy elutasítja: `/party decline`).
3. Kész! A tagokat a `/party list` (vagy simán `/party`) mutatja — a vezetőt 👑 jelöli.

### Mit ad a csapat?

- **Közös XP:** a közeli mob-ölésekből járó kaszt-XP a közelben lévő párttagok közt
  **fejenként oszlik meg** — minél többen vagytok együtt, annál kisebb rész jut mindenkinek
  (igazi WoW-módra); az osztás utáni maradék a **megölőé**. Cserébe együtt sokkal gyorsabban
  és biztonságosabban öltök.
- **Personal loot az eseményeknél:** a plugin loot-eseményeinél (**Vad Hajsza**,
  **elrejtett kincs**) nem egy közös zsákmányon kell osztozni — minden közelben lévő párttag
  a **saját jutalmát** kapja. Lásd [Világesemények](#10-világesemények).
- **Nincs PvP párton belül:** a tagok **nem tudják sebezni egymást** — sem közelharccal, sem
  nyíllal. Nyugodtan varázsolhatsz a kavarodásban.
- **Csapat-chat:** `/p <üzenet>` — csak a párttagok látják. (Hosszabb formában:
  `/party chat <üzenet>`.)

### Party-HUD 📊

Amíg csapatban vagy, a **HUD-oldalsávon** (a képernyő jobb szélén) egy **„— Csapat —" szekció**
mutatja a tagokat: név + **színkódolt élet-sáv** (zöld / sárga / piros) + szív-szám, a vezetőt
👑 jelöli. **Élőben frissül**, így harc közben is látod, kinek kell segítség — mint egy igazi
WoW party-frame. Ha nem vagy csapatban, a szekció el sem foglal helyet az oldalsávon.

### A csapatvezető jogai 👑

| Parancs | Mit csinál |
|---|---|
| `/party kick <név>` | Tag kirúgása a csapatból |
| `/party promote <név>` | A vezetés átadása egy tagnak |
| `/party disband` | A csapat feloszlatása |

### Jó tudni

- Kilépés a csapatból: `/party leave`.
- Ha valaki **kilép a szerverről**, automatikusan kikerül a csapatból.
- Ha a létszám **2 fő alá** csökken, a csapat **automatikusan feloszlik**.
- A meghívó **lejár** egy idő után — ha nem sikerült elfogadni, kérj újat.

---

### Céhek — a frakción belüli kisközösséged ⚜

A frakció (50+ fő) és a party (5 fő) közé érkezik a **céh**: 10-15 fős, tartós közösség
saját névvel, kasszával és céh-szinttel.

- `/ceh letrehoz <név>` — céh-alapítás (kis díj a számládról). Csak frakciótag alapíthat.
- `/ceh meghiv <játékos>` → a meghívott `/ceh elfogad` — **csak a saját frakciódból** hívhatsz.
- `/ceh befizet <összeg>` — a számládról a **céh-kasszába** teszel (közös célokra gyűjthettek).
- `/ceh info` — szint, XP, tagok, kassza; `/ceh lista` — a szerver top-céhei.
- **Céh-szint:** a tagok quest-teljesítései céh-XP-t termelnek — szintlépéskor mindenki
  értesül, és a **taglétszám-plafon nő** (10-ről akár 15-ig). A közös munka szó szerint
  nagyobbá teszi a céhet!
- Kilépés: `/ceh elhagy` (a vezetés az első tagra száll); a vezető `/ceh kirug`-gal rúghat.

---

## 16. Kazamaták 🗝

A **kazamaták** kulcs-kapus, védett dungeon-zónák: kívülről zárt, megépített
labirintusok, bent erősebb szörnyekkel és a mélyükön zsákmánnyal. A romok a Káoszkor
emlékei — a Vasművek elhagyott tárnái, elsüllyedt céh-pincék, kriptamélyek.

### Hogyan jutok be?

1. **Szerezz kulcsot.** A kulcs egy **megjelölt tárgy** (pl. „A Mélység Kulcsa"),
   amelyet a **bűvölő (enchanter) szakma** mesterei készítenek receptből — vagy ritkán
   boltban, zsákmányban bukkan fel. Minden kazamatának **saját kulcsa** van.
2. **Lépj be a kapun.** A zóna határán a kulcs **elfogy**, és cserébe kapsz egy
   **futam-passzt** (alapból **2 óra**): ennyi ideig szabadon ki-be járhatsz.
3. **A futam után pecsét.** A passz lejártával **heti pecsét** kerül rád (alapból
   **7 nap**): addig nem léphetsz be újra ebbe a kazamatába. A pecsét **fejenkénti** —
   a csapatod minden tagja **saját kulccsal** lép be.

### Mire számíts bent?

- A szörnyek **erősebbek**, mint kint (a zóna külön mob-szabályai szerint) — vigyél
  csapatot!
- A kazamata **védett**: nem bontható és nem építhető át — az út a labirintuson át
  vezet, nem a falon át.
- A mélyén a szerver-csapat által elhelyezett **zsákmány és/vagy boss** vár.

> 👥 **Csoport-kedvezmény:** ha **csapatban** érkeztek, **egy kulcs** a kapunál 16 blokkon
> belül álló párttagoknak is nyit — az 5 fős túrához nem kell 5 kulcs. (A heti pecsét
> mindenkire külön érvényes.)

### Mit találsz bent? (loot-réteg)

- **Kincsesládák:** a kazamatákban elrejtett ládák **fejenként, hetente** adnak zsákmányt —
  mindenki a sajátját kapja, nincs "első kattintó visz mindent". A láda kifosztva is ott
  marad, neked egy hét múlva töltődik újra.
- **Mini-boss:** minden kazamatának saját őrzője van (pl. *A Mélység Őrzője*), aki a
  belépésedkor ébred, ha már letelt az újraéledése (alapból 24 óra). Leölve **bővebb,
  ritkább zsákmányt** dob (ereklyeszilánk, emlékszilánk, sárkánycsont…).
- **Bónusz mob-drop:** a kazamata szörnyei a sima zsákmányon felül is dobhatnak
  értéket — a kapu mögötti kockázat megéri.

### Ismert kulcsok

| Kulcs | Készíti | Kazamata |
|---|---|---|
| **A Mélység Kulcsa** | bűvölő (30. szint) | a Vasművek elhagyott tárnái |
| **A Csontkripta Kulcsa** | bűvölő (40. szint) | Thanaopolis kriptamélye |

> A kazamaták **fizikai megépítése a szerver-csapat feladata** — ahogy elkészülnek,
> a kulcsok is értelmet nyernek. Ha egy kulcs receptje már tanulható, de a kapu még
> nem áll, várj egy kicsit: épül!

---
