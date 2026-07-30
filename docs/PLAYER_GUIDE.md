# IceSMP játékoskézikönyv — a Felsők útja

<!-- icesmp-doc-id: guide.player -->

> *Emlék nélkül ébredsz Aetrinita árnyékában. A Fa visszahívott az élők
> közé — de nem választott helyetted zászlót, hivatást vagy utat.*

Az IceSMP világában **Felső** vagy: egy visszatért vándor, akinek a döntései
új fejezetet írhatnak a Káosz Korában. Lehetsz hadvezér, kereskedő,
gyógyító, felfedező, mesterember vagy az, aki egy rom mélyén először hallja
meg a múlt visszhangját.

Ez az útmutató abban segít, hogy eligazodj — nem mondja meg, hogyan kell
játszanod, és nem fejti meg helyetted a világ titkait. Ha csak egy parancsot
jegyzel meg, ez legyen:

> **`/menu`** — innen a legtöbb fontos rendszer kattintással elérhető.

> Dokumentált release: `4643ab53586f0c1ee7352df16dcd477013e6fad4`
>
> A számokat és az elérhető tartalmakat a szerver aktuális beállítása
> módosíthatja. Egy világhelyszín, NPC, kazamata vagy történeti kapu csak akkor
> tekinthető elérhetőnek, ha az adott szezon világában ténylegesen ki van
> építve és aktiválva van. A játékbeli menü és üzenet mindig elsőbbséget élvez.

A rendszerek állapotát a [funkciókatalógus](FEATURES.md), az új és megváltozott
részeket a [legújabb változások](LATEST_CHANGES.md) foglalják össze.

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
15. [Party és céh](#15-party-csapat)
16. [Kazamaták](#16-kazamaták)

---

## 1. Kezdő lépések

*Aetrinita megmentette az életed. Hogy mit kezdesz vele, az már a te
történeted.*

### Az első tíz perced

1. **Nyisd meg a `/menu` menüt.** Innen eléred a karakteredet, a küldetéseket,
   a gazdaságot, a közösségi rendszereket és a világ aktuális eseményeit.
2. **Nézd meg a karakterlapodat:** `/profile`. Itt választasz kasztot, itt
   igényelheted a Lélekkapocs tárgyadat, és később innen nyílik a
   specializáció-, talent- és szakmamenü is.
3. **Válassz kasztot megfontoltan.** Egy karakternek egy kasztja lehet, és a
   cseréhez adminisztrátori teljes kaszt-reset szükséges.
4. **Tanulj szakmát.** Egy gyűjtögető és egy készítő szakmát választhatsz; a
   Halászatot és a Szakács mesterséget mindenki ismeri.
5. **Kövesd a kezdő küldetés jelzéseit.** Az első lánc játék közben vezet végig
   az alapokon. Nem kell előre tudnod a megoldásokat.
6. **Indulj el.** Harcolj, gyűjts, építs vagy keress társakat. A fejlődés nem
   egyetlen kijelölt út.

### Fontos: hogyan kezdesz frakciót választani?

Új játékosként technikailag a **Semleges/Menedék kezdőállapotban** vagy. Ez
biztonságos kiindulópont, nem egy végleges eskü. Az **első kifejezett
frakcióválasztásod ingyenes**, ezért előbb nyugodtan ismerd meg a világot és a
játékstílusokat.

### Mit hol találsz?

| Ha ezt szeretnéd… | Ezt nyisd meg |
|---|---|
| minden fő rendszert egy helyen | `/menu` |
| karakter, kaszt, spec, talent | `/profile` |
| képességek böngészése | `/spellbook` |
| küldetések | `/quest log` |
| pénz és bank | `/bank balance`, `/currency balance` |
| piac | `/market` |
| aktuális események | `/events status` |
| csapat | `/party` |
| saját birtok | `/claim` |

### Amit a képernyőd mesél

- A **HUD** mutathatja a kasztod erőforrását, a csapatod állapotát, a fontos
  világhelyzeteket és a kiválasztott adatokat. A `/hud` paranccsal személyre
  szabhatod.
- A **tablista** frakció- és ranginformációt adhat, háborúban pedig segít
  felismerni a viszonyokat.
- Harc közben **sebzésszámok**, célpontinformáció és halálösszegző segít
  megérteni, mi történt.
- A vanília **Haladás** képernyő IceSMP-füle mérföldköveket és rejtett
  felfedezéseket is őriz. Némelyik eredmény azért csendes, mert a titok a
  jutalom része.

Ha elvesznél, térj vissza a `/menu` menühöz. A világ nagy; nem kell egyetlen
este alatt megtanulnod.

---

## 2. Frakciók

*A zászló nem puszta szín. Azt mondja el, kik állnak melletted, milyen törvény
véd — és milyen árat kér tőled a hűség.*

Négy nagy hovatartozás alakítja a világot:

| Frakció | Parancsazonosító | Játékstílus és passzív |
|---|---|---|
| 🔥 **Láng — Perinfernicitas** | `red` | tűz-, láva- és forróblokk-immunitás |
| ❄️ **Fagy — Cryghaliris** | `blue` | fagyás- és fulladásimmunitás, lassabb éhségvesztés |
| ⚖️ **Menedék — Ryanora és Caldestera** | `neutral` | zuhanásimmunitás, békésebb lények, adómentesség |
| 💀 **Kitaszítottak** | `dark` | wither-immunitás, az élőhalottak nem támadnak; súlyos történeti és jogi ár |

Belépés: `/faction join <red|blue|neutral|dark>`.

### Az első választás és a későbbi váltás

- A kezdőállapotod **neutral/Menedék**, de az első tudatos választásod ingyenes.
- A későbbi váltásnak lehet pénzköltsége, várakozási ideje és szezonális
  korlátja. A jelenlegi értéket mindig a játék üzenete mutatja.
- A szezon hajrájában a váltás lezárulhat, hogy a bajnoki verseny ne legyen
  kijátszható.
- A `/faction leave` a Semleges állapotba helyez; nem biztosít kerülőutat a
  váltási szabályok körül.
- A frakcióválasztás és a kasztválasztás két külön döntés.

### A Kitaszítottak

A `dark` nem egyszerű színcsere. Bűn, száműzetés és egy sötét paktum kapcsolódik
hozzá. Az önkéntes belépés kétszeri megerősítést kér, a paktumból pedig csak a
játékban megismerhető vezeklés vezet ki.

A csábító előnyök mellé valódi kockázat jár: a bűnös jel, a körözés és a világ
bizalmatlansága. Ne lépj be úgy, hogy a figyelmeztetést nem olvastad el.

### Törvény, bűn és vérdíj

Bűnt szerezhetsz többek között tiltott gyilkossággal, súlyos árulással vagy
idegen területen elkövetett lopással. Raid, törvényes hadi-ablak és körözött
bűnöző levadászása eltérő szabályokat követhet.

- `/bounty` — körözöttek és vérdíjak.
- A rendszer jelzi, ha egy támadás vagy ölés bűnnek számított.
- Sok bűn száműzetéshez vezethet.
- A bűnpont és a sötét paktum nem feltétlenül ugyanaz: egyik eltűnése nem
  automatikusan oldja a másikat.

### Királyok és a Vének Tanácsa

A harcos frakciók királyt választhatnak. A Menedéket ehelyett hetente választott
**Vének Tanácsa** vezeti: szavazás, korlátozott kasszajog és békés gazdasági
döntések tartozhatnak hozzá, raidindítás nem.

- Harcos frakció: `/faction king`, `/faction king vote <játékos>`.
- Menedék: `/tanacs`, `/tanacs szavaz <játékos>`.

### Amit a suttogásokról tudnod kell

A világban vannak titkok, amelyeket ez a kézikönyv szándékosan nem fejt meg.
Ha különös meghívót, rítusnyomot vagy gyanús üzenetet találsz, olvasd el
figyelmesen. A rejtett utak szabályait a játék akkor mutatja meg, amikor valóban
találkozol velük — és a következményeket is neked kell mérlegelned.

---

## 3. Valuta és gazdaság

*Egy romot karddal lehet visszafoglalni. Egy országot kenyérrel, érccel és
bizalommal kell újra felépíteni.*

Minden frakciónak saját fizetőeszköze van:

| Frakció | Valuta |
|---|---|
| Láng | Parázsló Parals |
| Fagy | Hópihér-veret |
| Menedék | Creutzér |
| Kitaszítottak | Csontveret |

A pénz két formában létezhet:

- **fizikai veretként** a táskádban;
- **banki egyenlegként**, amelyből a piac és a boltok fizetnek.

### Bank és váltás

- `/bank balance` — banki egyenlegek.
- `/bank deposit` — a nálad lévő veretek befizetése.
- `/bank withdraw <valuta> <összeg>` — veretek felvétele.
- `/currency rates` — aktuális árfolyamok.
- `/currency exchange <összeg> <honnan> <hová>` — váltás.

A befizetés, a felvét és a valutaváltás alapbeállításban **fővárosi
szolgáltatás**. Ha a parancs nem működik a vadonban, keresd meg az aktív
világban kijelölt bankot vagy használd a banki NPC-t.

Az árfolyam élő: a gyakori valuta gyengülhet, a ritkább erősödhet. A váltásnak
díja és napi kerete lehet.

### Piac és aukció

- `/market` — piac megnyitása.
- `/market sell <ár> [valuta]` — a kézben tartott tárgy eladása.
- `/market auction <kikiáltási ár> [óra] [valuta] [buyout:<ár>]` — aukció.
- `/market cancel` — visszavonható saját tétel visszavétele.
- `/market claim` — megnyert vagy visszajáró tárgy átvétele.

A licitált összeg zárolódik; túllicitáláskor visszajár. Élő licites aukció nem
vonható vissza. Az eladási díj és a frakciók közti viszony módosíthatja a
végösszeget. A relikviák nem hagyományos piaci áruk.

### Honnan szerezhetsz pénzt?

- küldetésekből és napi feladatokból;
- moboktól szerezhető erszényekből;
- biztonságos felvásárlói ügyletekből;
- játékospiaci eladásból;
- parkourból, vérdíjból és egyes eseményekből;
- a veszélyesebb vidékek magasabb szintű ellenfeleitől származó
  lélekkőből/veretből.

A közvetlen pénzutalás (`/currency pay`) az alap telepítésben ki lehet
kapcsolva. Ilyenkor adj át fizikai veretet, vagy használd a piacot.

### Adományláda

Az adományláda nem piac:

- `/adomany add` — a kézben tartott stack felajánlása;
- `/adomany` — a közös kínálat böngészése.

Nincs vételár; amit elviszel, azt egy másik játékos neked szánta.

### Miért kerül pénzbe ennyi minden?

Piaci díj, adó, frakcióváltás, claim-bővítés, rituálé, komp, ládakulcs és
szakmai kellék is kivonhat pénzt a gazdaságból. Ezek tartják értékesnek a
veretet. A Menedék adómentes; más frakcióknál hátralék is keletkezhet, ezért
ne hagyd figyelmen kívül a pénzügyi figyelmeztetéseket.

---

## 4. Kasztok

*Az emlékeid elvesztek, de a véred még emlékszik arra, hogyan harcoltál.*

Az IceSMP-ben **13 kaszt** közül választasz. A kaszt meghatározza a
Lélekkapcsodat, az erőforrásodat, az alap képességeidet és a későbbi
specializációid irányát.

| Kaszt | Alaphangulat és szerepkör |
|---|---|
| Varázsló | távolsági elemi mágia és kontroll |
| Harcos | közelharc, kitartás, tankolás |
| Íjász | távoli sebzés, mozgás és csapdák |
| Orgyilkos | gyors kitörés, méreg és árnyék |
| Druida | természetmágia, alakok, több szerepkör |
| Paplovag | szent közelharc, védelem és gyógyítás |
| Halállovag | vér, fagy, rúnák és élvonal |
| Sámán | elemek, totemek és támogatás |
| Szerzetes | csi, gyors közelharc, tank vagy gyógyítás |
| Pap | fény, pajzs, gyógyítás és árnymágia |
| Boszorkánymester | átkok, tűz és idézett erők |
| Démonvadász | mozgékony, kockázatos démoni harc |
| Sárkányidéző | sárkány-eszencia, sebzés vagy gyógyítás |

### A választás súlya

Egy karakternek **egy kasztja van**. A választás után rendes játékosparanccsal
nem cserélhető; teljes resetet csak admin tud végezni, és az a kaszthoz kötött
haladás egy részét is törli. Előbb olvasd el a menü szerepcímkéit, és azt
válaszd, amelyiknek a játékmenete tetszik.

### Fejlődés

Ellenséges lények legyőzésével kaszt-XP-t kapsz. A távolabbi, magasabb szintű
szörnyek veszélyesebbek, de több fejlődést és jobb jutalmat adhatnak. A
kasztszint felső határa és a szükséges XP szerverbeállítás.

### A Lélekkapocs

Ez a kasztodhoz kötött eszköz:

- **jobb kattintás** — a kijelölt képesség használata;
- **SHIFT + görgetés** vagy **SHIFT + bal kattintás** — képességváltás;
- **SHIFT + jobb kattintás** — a varázskönyv megnyitása, ahol támogatott;
- ha elveszik, a `/profile` kasztmenüjéből újra igényelhető.

A közelharci kasztoknál a megfelelő kard vagy balta is kezelheti a
képességeket, így harc közben nem feltétlenül kell külön tárgyra váltanod.

### Kasztalapú életerő

A kasztonként eltérő alapéleterő-rendszer jelenleg konfigurációból
**kikapcsolt** állapotú. Ne számolj vele aktív előnyként: az életerőt most a
vanília alap, a felszerelés és az aktív talentek módosítják.

---

## 5. Képességek (varázslatok)

*A varázslat nem egy hosszú lista a kódexben. Ritmus, helyzetfelismerés és az a
pillanat, amikor mersz még egyet lépni.*

A szerver képességkészlete nagy, ezért ez az útmutató nem másolja be a teljes,
gyorsan avuló katalógust. A saját, aktuális listád:

- `/spellbook` — leírás, költség, várakozási idő és feloldás;
- `/profile` → **Képesség-fa** — megmutatja, mi aktív és mihez kell még szint;
- a Lélekkapocs action barja — a kiválasztott képesség és valós költsége.

### Erőforrás és költség

A legtöbb képesség a kasztod saját erőforrását használja: mana, düh, energia,
fókusz, csi vagy más tematikus készlet. A HUD mutatja a szintjét.

Nem minden varázslat egyformán fizet:

- a vérmágia életet kérhet;
- a nagy rituálé vagy idézés XP-t;
- a nehéz fizikai mozdulat éhséget;
- a legtöbb hétköznapi képesség a kaszterőforrást.

A játékbeli spellkönyv mindig a tényleges, jelenlegi árat írja ki.

### Harci ritmus

- A **cooldown** megakadályozza, hogy ugyanazt a képességet folyamatosan
  ismételd.
- Egyes képességek egymás után használva **kombót** vagy kombóláncot nyitnak.
- Játékosok ellen az ismételt kemény kontroll rövid időn belül gyengülhet.
- A kedvenceket a spellkönyvben megjelölve a váltás csak a fontos
  képességeiden lépkedhet.

### Spell-mesterség

A `/spell` és `/spell upgrade <id>` segítségével frakcióvalutáért
fejlesztheted egy képesség mesterségét. Ez csökkentheti a várakozást és
erősítheti a hatást. Fejlesztés előtt a játék kiírja a költséget; ne egy
régi táblázatból indulj ki.

---

## 6. Specializációk

*A kaszt megmutatja, honnan ered az erőd. A specializáció arról szól, mivé
formálod.*

A **25. kasztszinttől** választhatsz specializációt. Összesen 35 irány létezik,
de a menü csak a saját kasztodhoz és aktuális feltételeidhez tartozó
lehetőségeket mutatja:

`/profile` → **Specializáció**, vagy `/spec list`.

A specializáció kijelölheti a fő szerepedet:

- közelharci vagy távolsági sebző;
- varázshasználó;
- tank;
- gyógyító vagy támogató;
- társakra, alakváltásra vagy időzített kombókra építő hibrid.

A pontos képességeket ne ebből a dokumentumból válaszd: nézd meg a játékbeli
menüben, mert ott látod a jelenlegi feloldási szinteket és korlátokat.

### Feltételek és respec

- Az alap feltétel a 25. kasztszint.
- Egyes sötét irányok frakciót, bűnös állapotot vagy történeti kaput kérnek.
- `/spec respec class` visszavonja a kasztspecializációt, általában
  frakcióvalutáért.
- A régi spechez kötött képességek lekerülnek, a hozzá kötött talentpontok
  visszatérnek.

### Társak

Bizonyos specializációk befogott vagy idézett társsal játszanak. A `/pet`
menüben kezelheted őket:

- `/pet item` — befogóeszköz, ha az irányod használ ilyet;
- `/pet summon`, `/pet dismiss` — idézés és elbocsátás;
- `/pet name <név>` — elnevezés;
- `/pet stance <aktiv|passziv|marad>` — viselkedés.

A társ tapasztalatot szerezhet, megvédhet, és ritka Társvértet viselhet. A
pontos befogható vagy idézhető lényt a választott irány és a játékbeli
visszajelzés határozza meg.

---

## 7. Talentek (talent-fa)

*Két azonos kaszt sem feltétlenül ugyanúgy harcol. A különbséget gyakran nem a
fegyver, hanem a sok apró döntés adja.*

A talentek tartós karakterbónuszok vagy új aktív lehetőségek. A
`/profile` → **Talentek** menüben költheted el a pontjaidat.

### Pontok

- **Kasztpontot** a kasztod szintjeiből szerzel.
- **Szakmapontot** az összes szakmád közös fejlődése termel.
- A két pontfajta külön fára költhető.

### A fa olvasása

- Fentről lefelé haladsz; az alsó sorokhoz előfeltétel kellhet.
- Egy talentnek több rangja lehet.
- Bizonyos ágak kizárják egymást.
- A csúcstalentekhez előbb elég pontot kell elköltened az adott fában.
- A `★` jelű talent új aktív képességet is nyithat.

A konkrét talentneveket és számokat a menü mutatja. Ez jobb döntési hely, mint
egy teljes, később elavuló táblázat.

---

## 8. Szakmák

*A Káosz Korát nem csak hősök élik túl. Kell valaki, aki kivágja a gerendát,
megfőzi az ellenszert és újraélezi a pengét.*

Két fő szakmai helyed van:

- **egy gyűjtögető szakma:** Bányász, Gyógynövényész vagy Favágó;
- **egy készítő szakma:** Kovács, Alkimista vagy Bűvölő.

A **Halász** és a **Szakács** másodlagos szakma: ezeket mindenki ismeri, nem
foglalnak helyet.

### Hogyan fejlődsz?

Csak a megtanult szakmádhoz illő tevékenység ad szakma-XP-t. A bányász ércet
fejt, a gyógynövényész érett növényt gyűjt, a favágó rönköt vág; a készítők
pedig a saját műhelyfolyamataikban haladnak.

- `/profession info` — szakmai állapot.
- `/profession join <szakma>` — tanulás vagy váltás.
- `/profession recipes` — a ténylegesen ismert és zárolt receptek.
- `/szakmacel` — a szakmád heti közös célja.

A teljes receptkatalógus nem része ennek a kézikönyvnek. A receptkönyv jelzi a
szintet, a hozzávalót, az esetleges tervrajzot és azt is, ha valamilyen
szolgáltatói kellék hiányzik.

### Mesterfok és rúnák

Magasabb szinten szakmaspecializációt választhatsz, és egyes mesterségek
rúnákat vagy különleges tárgyakat készíthetnek. A rúna tartós döntés lehet:
felhelyezés előtt olvasd el a tárgy leírását, mert egy felszerelésen csak
korlátozott számú véset fér el.

---

## 9. Relikviák és rituálék

*Vannak tárgyak, amelyeket nem birtokolsz. Egy ideig csak te hordozod a
történetüket.*

A relikviák egyedi, szerver-szintű kincsek. Egy típusból egyszerre csak egy
hiteles példány lehet aktív.

### Amit tudnod kell róluk

- A fegyver-relikvia PvP-halálnál gazdát cserélhet.
- A passzív relikvia elveszhet, majd a régi tulajdonos számára korlátozott ideig
  visszaidézhető lehet.
- Hosszú inaktivitás után a kötés felszabadulhat, hogy más is megszerezhesse.
- Frakcióhoz kötött relikviát csak a megfelelő hovatartozással használhatsz.
- Relikviát hagyományos piaci tételként nem lehet eladni.

### Rituálék

Az oltár nem közönséges receptasztal:

1. találd meg vagy építsd meg a világban engedélyezett szentélyt;
2. gyűjtsd össze az áldozatot;
3. a megfelelő magblokknál használd a játék által jelzett interakciót;
4. figyeld a választ — a hiányos szentély vagy tiltott használó nem indít
   rítust.

> **Nincs univerzális „minden oltár 5×5” szabály.** A pontos struktúrát az
> aktuális szerverkonfiguráció és a ténylegesen megépített szentély határozza
> meg. Ha a világban található leírás és egy régi útmutató eltér, a jelenlegi
> oltár és a játékbeli üzenet a mérvadó.

Létezhet relikvia-, megtisztító, hazatérő vagy ideiglenes erőt adó rítus.
Ezek teljes hozzávaló- és struktúralistája szándékosan nincs itt: a felkutatás,
a kereskedés és a kísérletezés a kaland része.

---

## 10. Világesemények

*A világ nem díszlet körülötted. Néha megmozdul, és neked kell eldöntened,
elmész-e megnézni.*

Az aktuális helyzetet a `/events status` vagy a `/menu` Események része mutatja.
A nagy harci események alapból nem torlódnak egymásra.

### Milyen hírekre figyelj?

| Jelenség | Mit jelent neked? |
|---|---|
| Vérhold | erősebb éjszakai ellenfelek, nagyobb kockázat és jutalom |
| Világboss | közösségi nagy ellenfél, jelzett támadásokkal |
| Invázió | hullámokban érkező, megerősített szörnyek |
| Vad Hajsza | kóbor elit fenevad és személyes jutalom |
| Kereskedő-karaván | időleges, rotáló ritka kínálat |
| Elrejtett kincs | hozzávetőleges koordinátán kereshető zsákmány |
| Gyűjtögető-ablak | rövid bányász-, termés-, halász- vagy XP-bónusz |
| Bőség-idő | békésebb, építésre és gazdálkodásra kedvező idő |
| Kollektív kihívás | közös szervercél és közös jutalom |
| Karaván-kíséret | mozgó rakomány védelme szörnyhullámoktól |
| Meteor | ideiglenes kráter és kibányászható ritka anyag |
| Rontás-góc | terjedő veszélyzóna, megtisztítható maggal |
| Kultisták | portya, rítus vagy hírvivő — eltérő célokkal |
| Hangulat-esemény | köd, aurora, hullócsillag, szellemek vagy állatvándorlás |

A helyi, kisebb jelenségekről csak a közelben járók értesülhetnek. A
mob-események, meteorok és kincsek védett területet, játékosclaimet és
alkalmatlan terepet elkerülnek.

### A világ nehézsége

A biztonságos vidékektől távolodva a szörnyek szintje emelkedhet. Erősebbek,
de több kaszt-XP-t és jobb zsákmányt adhatnak. A spawnerből származó mobok
nem a vadon kihívásának pótlására valók, ezért nem kapják meg ugyanazt a
skálázást és jutalmat.

### Szezonális liga

A frakciók nem csak raiddel szerezhetnek pontot: világboss, közösségi cél,
rontástisztítás, párbaj és más identitáshoz illő tevékenység is számíthat.
A szezon végén jutalom és új pontverseny jön.

> **A szezonzárás nem wipe.** A karaktered, felszerelésed, pénzed,
> szakmáid, talentjeid és birtokod megmarad; a liga pontversenye indul újra.

### A halkabb történetek

Tábortűz mellett régi történetet hallhatsz, a krónikák megőrzik a korszak
eredményeit, és titkos helyek várják az első felfedezőt. Egyes világhelyek vagy
NPC-k csak akkor élnek, ha az aktuális szezon térképén a csapat már
aktiválta őket. Ha nem találod őket, az nem feltétlenül rejtvény: kérdezz rá
az adminoknál vagy nézd meg a szerver közleményeit.

---

## 11. Királyság, raid és háború

*A trónt itt nem vérvonal örökli. A Felsők szava emel valakit fölétek — és
ugyanez a szó taszíthatja le.*

### Vezetés és kassza

A Láng, Fagy és Kitaszított frakció királyt választhat. A király:

- kezelheti a frakciókasszát napi korláttal;
- adókulcsot állíthat;
- raidet és frakciószállítmányt indíthat.

A Menedéknek nincs raidindító királya; a Vének Tanácsa békés és gazdasági
jogokat kap.

Hasznos parancsok:

- `/faction king` — király és választás;
- `/faction treasury` — kassza;
- `/faction donate <összeg>` — adomány;
- `/tanacs` — a Menedék tanácsa.

### Raid

1. A király célfrakciót és — ha van — területet jelöl.
2. A felkészülési szakaszban a harcosok `/faction raid join` paranccsal
   jelentkeznek.
3. A harci szakaszban a résztvevők ölései és az objektíva pontot adhat.
4. A győztes liga-pontot és hadizsákmányt szerezhet; területi támadásnál
   birtokváltozás is történhet.

Csak a szabályosan jelentkezett harcosok közti, megfelelő helyen történt
összecsapás élvezi a raid kivételeit. Aki kívül marad, arra a békeidős
bűnszabályok vonatkoznak.

### Hadi-ablak, párbaj és kémkedés

- `/faction war` — a Láng és Fagy közti kijelölt hadi-ablak állapota.
- `/parbaj kihiv <név>` — beleegyezéses becsületpárbaj.
- `/kem <célfrakció>` — időleges kémálca, ha a szükséges integráció és
  szerverbeállítás elérhető.

Az álca nem törli el a bűnt, és harci érintkezés lebuktathat. A megrendezett,
együtéses ölés nem jogosít minden jutalomra.

### Harcjelölés

PvP után rövid ideig harcjelölést kapsz. Ez megakadályozza, hogy egy
biztonságos zónába vagy kompra lépéssel azonnal megszakítsd az összecsapást.
Figyeld az action bart, mielőtt menekülési útvonalat választasz.

---

## 12. Küldetések

*Az első feladat csak annyit kér, hogy indulj el. A későbbiek már azt kérdezik
meg: miféle emberként érsz célba?*

A küldetések lehetnek harci, gyűjtögető, készítő, felfedező, beszélgetős,
szállító, parkour-, raid- vagy világesemény-feladatok. Egy küldetés több célt
is kezelhet párhuzamosan vagy sorrendben.

### Alapparancsok

- `/quest log` — kattintható napló: Aktív, Felvehető, Teljesített.
- `/quest list` — elérhető és aktív küldetések.
- `/quest accept <id>` — felvétel.
- `/quest info` — haladás.
- `/quest abandon <id>` — feladás.

Az első belépéskor egy rövid kezdő lánc automatikusan vezet végig az alapokon.
Kövesd a képernyő jelzéseit; ez az útmutató nem sorolja fel előre a
megoldásait.

### NPC-k és történet

A küldetést adó vagy folytató NPC fölött személyes részecskejelzés jelenhet
meg. A párbeszédekben választási lehetőséget is kaphatsz, amely másik
folytatást nyit.

Egyes NPC-k naponta rotáló kínálatot adnak, más feladat hetente vagy
szezononként ismételhető. Ha az aktuális világban egy szükséges NPC nincs
kihelyezve, a szerver engedélyezhet `/quest talk <npc-név>` tartalékutat; ezt
csak akkor használd, ha a játék vagy a csapat kifejezetten erre irányít.

### Próbák, rejtvények és fejezetek

- A kasztpróbák és mesterpróbák a választott szerepedhez illő feladatokat
  adnak.
- A történeti fejezetek a szezonhoz kapcsolódhatnak.
- A rejtvényküldetés nem mutatja meg a konkrét célt — ez szándékos.
- A sötét és vezeklési láncoknak tartós következménye lehet.
- A `/bestiarium` felfedezői és harci mérföldköveket gyűjt.

Nem találsz itt megoldókulcsot. Ha elakadsz, olvasd újra a párbeszédet,
vizsgáld meg a helyszínt, kérdezz más játékosokat — és csak ezután gondolj
hibára.

---

## 13. Frakcióterületek és saját birtok

*A térképen egy vonal csak tinta. A világban fal, törvény, otthon — vagy
figyelmeztetés.*

Határátlépéskor az action bar jelzi, milyen területre érkeztél.

| Zónatípus | Mit jelent játékosként? |
|---|---|
| Frakcióterület | alapból az adott frakció tagjai építhetnek |
| Védett frakcióterület | épített magterület, általános építési tiltással |
| Védett város | biztonságos, védett közösségi hely |
| Főváros | védett zóna és gazdasági szolgáltatások kapuja |
| Kárhozat-zóna | törvényen kívüli, veszélyes PvPvE terület |
| Kazamata | kulcs- és futamkapus, nem átépíthető kalandzóna |

A pontos build-, interakció-, PvP-, tűz- és robbanásszabály zónatípusonként
eltérhet. Ha valamit nem tudsz használni, előbb nézd meg, hová léptél.

### Kárhozat Kapuja

A Kapu környéke törvényen kívüli PvPvE vidék: a PvP megengedett, az ölés nem
feltétlenül bűn, a szörnyek erősebbek, a terep pedig védett. Belépéskor rövid
védelem járhat, de támadással elveszíted.

Az alap világképben új Nether-portál nem gyújtható; a kijelölt Kapu az
átjárás központja. A tényleges kapu csak akkor használható, ha az aktuális
térképen megépült és aktív.

### Saját birtok

A claim a személyes építési és tárolóvédelmed. **Nem teszi PvP-mentessé** a
területet.

Gyors kezdés:

1. állj a kívánt helyre;
2. `/claim` — alapméretű birtok létrehozása;
3. `/claim info` — tulajdonos és határ;
4. `/claim show` — környező határok.

Pontos kijelöléshez:

- `/claim pos1`, `/claim pos2`, majd `/claim area`;
- vagy `/claim wand`: bal katt az első, jobb katt a második sarok, majd
  **SHIFT + jobb katt** a foglaláshoz.

Hasznos műveletek:

| Parancs | Mire való? |
|---|---|
| `/claim list` | saját birtokok |
| `/claim unclaim` | a terület feladása; a költség nem jár vissza |
| `/claim extend up|down` | függőleges bővítés |
| `/claim trust <név>` | teljes hozzáférés adása |
| `/claim untrust <név>` | hozzáférés visszavonása |

A claim idegen építést, konténernyitást, folyadékot, dugattyús behatást,
tűz- és robbanáskárt is blokkolhat. Védett városban vagy tiltott zónában nem
hozhatsz létre birtokot. Frakcióterületen az aktuális szerverbeállítás dönti el,
engedélyezett-e.

### Caldestera törvénye

Ha az aktuális világban a semleges főváros és törvényzóna aktív, az őrség
nyílt fegyvert nem enged kézben tartani, körözöttet pedig visszafordíthat. Az
offhand is számít; legyen szabad hely a táskádban.

---

## 14. Parancsok listája

*A jó parancs nem varázsige. Csak egy rövidebb ösvény ugyanahhoz az ajtóhoz.*

A `<kötelező>` részt neked kell megadnod, a `[választható]` elhagyható. A
legtöbb művelet a `/menu` felületéről is elérhető.

### Karakter és fejlődés

| Parancs | Mire való? |
|---|---|
| `/menu` | központi menü |
| `/profile` | karakterlap |
| `/spellbook` | képességek |
| `/spell upgrade <id>` | spell-mesterség |
| `/spec list` | választható specializációk |
| `/spec choose <id>` | specializáció választása |
| `/spec respec <class|profession>` | specializáció visszaváltása |
| `/talent` | talentfa |
| `/profession info` | szakmai állapot |
| `/profession recipes` | receptkönyv |
| `/pet` | társ kezelése, ha elérhető |
| `/stats [név]` | játékosstatisztika |
| `/achievements` | elérések |
| `/leaderboard` | ranglisták |
| `/bestiarium` | felfedezői lajstrom |

### Frakció, közösség és világ

| Parancs | Mire való? |
|---|---|
| `/faction join <id>` | frakcióválasztás |
| `/faction king` | király és szavazás állapota |
| `/faction raid status` | aktuális raid |
| `/faction war` | hadi-ablak |
| `/tanacs` | Menedék tanácsa |
| `/party` | csapat |
| `/p <üzenet>` | csapatchat |
| `/ceh` | céh |
| `/bounty` | körözöttek |
| `/events status` | aktív események |
| `/kronika` | legutóbbi krónika |
| `/lore <téma>` | nyilvános kódexlapok |
| `/komp [útvonal]` | kompjáratok |
| `/parkour list` | pályák |
| `/daily` | napi feladat |
| `/crate info <id>` | láda és kulcsinformáció |

### Gazdaság és birtok

| Parancs | Mire való? |
|---|---|
| `/bank balance` | banki egyenleg |
| `/currency rates` | árfolyamok |
| `/market` | piac |
| `/market sell <ár> [valuta]` | eladás |
| `/market claim` | átvehető aukciós tárgy |
| `/adomany` | adományláda |
| `/claim` | gyors birtok |
| `/claim info` | birtokinformáció |
| `/claim list` | saját birtokok |

### Kommunikáció és biztonság

| Parancs | Mire való? |
|---|---|
| `/msg <játékos> <üzenet>` | privát üzenet |
| `/reply <üzenet>` | válasz |
| `/report <játékos> <ok>` | bejelentés |
| `/afk` | önkéntes AFK |
| `/hud <szekció>` | HUD testreszabása |
| `/sit` | leülés/felállás |

Az admin-, builder- és fejlesztői parancsok nem tartoznak a játékoskézikönyvbe.
Ezeket az [adminútmutató](ADMIN_GUIDE.md) és a
[builderútmutató](BUILDER_GUIDE.md) kezeli.

---

## 15. Party (csapat)

*A legtöbb rom egyedül is megtalálható. Nem mindegyik akar egyedül hazaengedni.*

A party legfeljebb öt játékosból állhat, és frakciótól független: ellenlábas
zászlók alatt élő Felsők is indulhatnak közös kalandra.

### Alakítás

1. `/party invite <név>` — meghívás;
2. `/party accept` — elfogadás;
3. `/party list` — tagok és vezető;
4. `/p <üzenet>` — csapatchat.

### Mit ad?

- a közeli mobölésből származó kaszt-XP megosztható;
- bizonyos eseményeknél minden közeli tag személyes zsákmányt kap;
- a párttagok nem sebzik egymást;
- a HUD mutatja a tagok életét;
- egyes kazamatakapuk közös kulcsnyitást támogatnak.

A vezető `/party kick`, `/party promote` és `/party disband` műveleteket
használhat. Kilépés: `/party leave`. A csapat feloszolhat, ha már nincs elég
tag.

### Céh

A céh tartósabb, frakción belüli közösség közös XP-vel, szinttel és kasszával:

- `/ceh letrehoz <név>` — alapítás;
- `/ceh meghiv <játékos>`, majd `/ceh elfogad`;
- `/ceh info` — tagok, szint, kassza;
- `/ceh befizet <összeg>` — közös hozzájárulás;
- `/ceh elhagy` — kilépés.

A party egy kalandcsapat; a céh egy hosszabb távú otthon.

---

## 16. Kazamaták

*A romok nem kérdezik, milyen szinten vagy. Csak azt, van-e kulcsod — és ki
áll melletted, amikor az ajtó bezárul mögötted.*

A kazamaták kulccsal nyíló, védett dungeon-zónák. Nem bonthatod át a falukat,
a bent élő ellenfelek erősebbek, a mélyükön pedig személyes ládák, mini-boss
vagy ritka zsákmány várhat.

### Belépés

1. Szerezz az adott kazamatához tartozó kulcsot.
2. Lépj a kijelölt kapuhoz.
3. A kulcs elhasználódik, és időleges futampasszt kapsz.
4. A futam után személyes visszatérési pecsét/cooldown korlátozhatja az újabb
   belépést.

> **Partyban alapból egy kulcs nyitja meg a kaput a közelben álló
> párttagoknak is.** Nem kell minden tagnak külön kulcsot feláldoznia. A
> futampassz és a későbbi pecsét ettől még játékosonként külön érvényes.

### Bent

- Maradjatok együtt; a zóna külön mob-szintbónuszt kaphat.
- A regisztrált kincsesláda személyenként és időszakonként adhat zsákmányt.
- A mini-boss újraéledési ideje külön szabály.
- A falak védettek: az útvonalat kell megfejteni, nem a terepet kikerülni.

Egy kulcs vagy recept létezése nem bizonyítja, hogy a hozzá tartozó kazamata
már él az aktuális szezonban. Indulás előtt nézd meg a szerver közleményét,
vagy kérdezd meg, hogy a kapu ki van-e építve és aktiválva.

---

*Aetrinita alatt mindenki ugyanúgy ébred. Onnantól azonban nincs két egyforma
út. Válassz olyat, amelyről egyszer érdemes lesz krónikát írni.*
