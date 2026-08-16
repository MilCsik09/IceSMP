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
17. [Prologue / Season 0](#17-prologue-season-0-amit-játékosként-tudnod-kell)

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

Új játékosként a **Menedék vendége** vagy. Ez fizikai és történeti
kezdőállapot, nem automatikus `neutral` állampolgárság: amíg nem választasz
kifejezetten frakciót, nem kapsz frakciópasszívot, frakcióquestet, tanácsi
szavazatot, közösségi cél- vagy frakciós szezonpont-jóváírást. Az onboarding
Creutzér-útravalója caldesterai vendégsegély, nem NEUTRAL-tagság.

Az **első kifejezett frakcióválasztásod ingyenes**. A Menedék teljes jogú
polgárává is tudatosan a `/faction join neutral` paranccsal válsz.

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

- A jobb felső **class HUD** mutatja a kasztod erőforrását, a class/spec mechanikád élő
  sávjait, tölteteit vagy rúnáit, a frakciódat, pénzedet, szintedet és legfeljebb három
  egyidejű világeseményt. A négy frakcióvalutád mindegyike saját, állandó helyet kap, ezért a
  nulla egyenleg sem rendezi át a panelt. A tartós class XP-sáv kikerült innen, hogy az eseménysor
  olvasható maradjon; a teljes kaszt-XP-det a `/job status` mutatja.
  A `/hud edit` kattintható szerkesztőjében külön mozgathatod, méretezheted vagy elrejtheted az
  elemeket. A DK-rúnák saját kategóriát kaptak, és nem mozgatják a többi kaszt tölteteit.
  A mentésed restart után is megmarad. A nem
  módosított elemek automatikusan követik a szerver globális HUD-alapját. Amíg a first-party
  resource pack nincs sikeresen betöltve, a kompakt natív kijelzés automatikusan marad.
- A bal felső, frakciószínű **Player Frame** váltja fel a vanilla szíveket, páncélt, éhséget
  és levegőbuborékokat. A HP current/max és százalék formában jelenik meg; az absorption külön
  pajzsként látszik. A páncél maximum nélküli flat szám, az étel saját mini-sávot
  kap, az oxigén pedig csak fogyó levegőnél jelenik meg. A frame, név, HP-sáv, HP-szöveg,
  százalék, pajzs, páncél, étel és oxigén külön editor-komponens; a Player Frame csoporttal
  együtt is mozgathatók. Pack nélkül a vanilla kijelzők maradnak láthatók.
- Ha megütsz egy mobot vagy játékost, a Player Frame mellett jelenik meg a **Target Frame**.
  A mobok bestiárium-stílusú, a játékosok frakciószínű keretet kapnak. A név, szint,
  current/max HP, százalék, rang/státusz, valamint játékosnál a class resource is látszik.
  A kijelzés nem hoz létre követő feliratot a mob testén, ezért az eredeti nametag nem tűnik el.
- Partyban legfeljebb négy másik tag **Party Frame-je** sorakozik a saját frame-ed alatt.
  Minden sor az adott tag frakciószínét, HP-ját, class resource-át, valamint a vezető,
  halott, távoli vagy offline állapotot mutatja.
- A **tablista** frakció- és ranginformációt adhat, háborúban pedig segít
  felismerni a viszonyokat.
- Harc közben **sebzésszámok**, a képernyőn megjelenő Target Frame és halálösszegző segít
  megérteni, mi történt. A rövid sebzésszámok továbbra is a találat helyén jelennek meg, a
  tartósabb HP/resource információ viszont csak a saját Target Frame-edben látható.
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

| Frakció | Parancsazonosító | Alap passzív |
|---|---|---|
| 🔥 **Láng — Perinfernicitas** | `red` | a környezeti tűz, égés és magma sebzésének 25%-a, a láváénak 50%-a marad; entitás okozta tűznél 75% marad |
| ❄️ **Fagy — Cryghaliris** | `blue` | nincs fagyássebzés, a fulladássebzés 50%-a marad; 25% esély a kijelölt természetes exhaustion elkerülésére |
| ⚖️ **Menedék — Ryanora és Caldestera** | `neutral` | a zuhanássebzés 50%-a marad; spontán békés/semleges mob- és Enderman-szemkontaktus-aggró szűrése; adómentes polgárság |
| 💀 **Kitaszítottak** | `dark` | a Wither-sebzés és -idő 50%-a marad; ambient városi undead-béke és enyhített éjszakai vad undead-előny, súlyos történeti és jogi árral |

Belépés: `/faction join <red|blue|neutral|dark>`.

### Az első választás és a későbbi váltás

- A kezdőállapotod **Menedék-vendég**, nem `neutral`; az első tudatos
  választásod ingyenes.
- A későbbi váltásnak lehet pénzköltsége, várakozási ideje és szezonális
  korlátja. A jelenlegi értéket mindig a játék üzenete mutatja.
- A szezon hajrájában a váltás lezárulhat, hogy a bajnoki verseny ne legyen
  kijátszható.
- A `/faction leave` explicit `neutral` polgárságba helyez, nem törli vissza a
  karaktert vendégállapotba. A korábbi választás tartós nyoma miatt sem a
  kilépés, sem egy hiányzó assignment nem kerüli meg a cooldown-, szezonlimit-
  vagy szezonvégi zár szabályait.
- A frakcióválasztás és a kasztválasztás két külön döntés.

### Mit jelent a passzív harc közben?

- A Láng passzívja **nem** oltja ki az IceSMP `TUZ` varázslatiskolát, és a
  markerelt boss-/eventsebzés alapból felülírhatja a környezeti védelmet.
- A Fagy exhaustion-előnye csak a konfigurált természetes mozgásforrásokra
  vonatkozik. Hunger-effektet, scripted éhséget, adminműveletet vagy az
  elmaradt hazai étel miatti food-duty következményét nem törli. A signature
  ételek buffja fogyasztáskor ellenőrzi az aktuális explicit tagságot: egy
  vendég, másik frakció tagja vagy időközben resetelt játékos nem örökli a
  tárgy korábbi tulajdonosának frakcióelőnyét.
- A Menedék békéje nem támadhatatlanság: a megtámadott lény visszaüthet, az
  Enderman az ütésre reagál, a scripted és eventes célzás pedig működik. A
  fél zuhanássebzés parkourban is megmarad.
- Thanaopolis markerelt, ambient élőhalott lakói a DARK játékost felismerik,
  amíg nem provokálja őket. Támadás után 60 másodpercig az adott játékos–mob
  pár, valamint a 16 blokkon belül ténylegesen riasztott undead példányok
  megtorolhatják a támadást; ez nem old fel globális békét minden élőholtnál.
  A vadonban csak éjjel és célzásonként 50% esélyű az előny. Vérhold alatt az
  ambient és a vad DARK béke is alapból megszűnik.
- Rontás-fajzat, dungeonmob vagy miniboss, inváziós mob/bajnok, világboss,
  scripted event- és questmob, valamint a koronaátok célzása nem kap DARK
  truce-ot.

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

A GUI felső, 0–8. sora egyirányú beadási zóna: bal kattintás a teljes
kurzorstackot, jobb kattintás egy darabot ad be; a shift-kattintás, a
hotbar-szám, az offhand-gomb és a drag is működik. Egy közös adományt
egyszerre csak egy játékos vehet el; átvétel előtt legyen üres a kurzorod.
Az adomány csak a tartós mentés után kerül a közös kínálatba. Ha közben
leáll a szerver vagy megszakad a kapcsolat, a beadás vagy az átvétel a
következő belépéskor veszteség és dupla kézbesítés nélkül lezáródik.

Nincs vételár; amit elviszel, azt egy másik játékos neked szánta.

### Miért kerül pénzbe ennyi minden?

Piaci díj, adó, frakcióváltás, claim-bővítés, rituálé, komp, ládakulcs és
szakmai kellék is kivonhat pénzt a gazdaságból. Ezek tartják értékesnek a
veretet. Az explicit Menedék (`NEUTRAL`) polgár adómentes; az assignment nélküli
vendég nem tagja a polgári adóbeszedési körnek. Más frakcióknál hátralék is
keletkezhet, ezért ne hagyd figyelmen kívül a pénzügyi figyelmeztetéseket. A
frakcióváltás a régi tartozást nem váltja át: azt továbbra is az eredeti
frakció valutájában, az eredeti kassza felé kell rendezni.

### Ládák és kulcsok

A lenti ládaszinten nyolc, alapból mindenki számára nyitható láda kap helyet:
Köznapi, Ritka, Hősi, Mitikus, Mesterség, Expedíció, Hadizsákmány és Arkánum.
A permission nem választja szét őket; az ár, a cooldown és a jutalomprofil igen.

A `/crate` böngészőben megnézheted az esélyeket és kulcsot vásárolhatsz. A
fizikai láda nyitásakor nem jelenik meg inventory-rulett: a kiválasztás a láda
felett pörgő ItemDisplayen látszik, és a jutalom csak a reveal lezárása után
kerül kiosztásra. Minden bundled láda nyitásonként pontosan egy kulcsot kér.
A preview és a világban megjelenő tárgy a valódi resource-pack modellt mutatja.
A lootban unique szakmaalapanyagok, ténylegesen craftolt és affixet rolloló
felszerelések, valamint szakma- és szinttartományból sorsolt tervrajzok is
vannak. Boss-only tervrajz csak a Mitikus poolból jöhet. Elytra egyik ládából
sem eshet; szárnyként kizárólag a relikviák léteznek.

---

## 4. Kasztok

*Az emlékeid elvesztek, de a véred még emlékszik arra, hogyan harcoltál.*

Az IceSMP-ben **13 kaszt** közül választasz. A kaszt meghatározza a
Lélekkapcsodat, az erőforrásodat, az alap képességeidet és a későbbi
specializációid irányát.

| Kaszt | Közös harci mag | Specializációk tényleges játékciklusa |
|---|---|---|
| Varázsló | **Rúnaszövés:** a legutóbbi két, megfelelő sorrendű mágiaiskola reakciót készít elő. | **Elementalista:** Tűz/Fagy/Vihar ráhangolódás → Konvergencia vagy Elemi Korona → elköltés. **Nekromanta:** korlátozott, tartós Holtak Udvara → aratás. |
| Harcos | **Düh + Csatatempó:** a Düh a képességek ára, a változatos harci tettek külön tempófokozatot építenek. | **Berserker:** Vérőrület ↔ Kimerülés, kontrollált túlpörgés és levezetés. **Védelmező:** Őrség építése → pajzs/intercept az egy Eskütárson. |
| Íjász | **Szélolvasás:** teljesen kihúzott, jól ütemezett, valódi távolsági lövések készítik elő a következő lövést. | **Mesterlövész:** egy prédán Pontossági lánc → gyengepont. **Vadmester:** közös célpont a társsal → Kötelék → társas finisher. |
| Orgyilkos | **Lehetőség:** pozíció, kitérés, interrupt vagy észrevétlenség nyit egy egyszer elkölthető finisher-ablakot. | **Méregkeverő:** három toxin és dózisaik → katalizálás. **Fantom:** véges lopakodás, Észleltség és egy Árnyéknyom-visszhang. **Pestishozó:** saját találattal ültetett, keményen korlátozott járványtörzs. |
| Druida | **Harmónia + alak/évszak:** a természetmágia Harmóniát épít, az alakváltás egy évszak áldásaként engedi ki. | **Vadőr:** kombópont és egy préda Szagnyoma → finisher. **Holdjós:** Nap↔Hold mérleg → Eclipse. **Védelmező:** Kéregrétegek és Gyökérháló. **Helyreállító:** Mag → érés → Virágzás. |
| Paplovag | **Eskü + Meggyőződés:** a választott szerephez illő tettek erősítik a következő szerepazonos képességeket. | **Szentlélek:** egy Fényjelző társ → gyógyítás-visszhang. **Megtorló:** három Ítélet-jel → Verdict. **Védő:** Pajzstöltet → Megszentelt Föld. |
| Halállovag | **Rúnakör:** a Vér és Fagy rúna újratölt, Halál rúnát csak átalakítással lehet teremteni; a spell előre kéri a saját rúnáját. | **Vérlovag:** nyolc találatnyi Vér Emlékezete → gyógyítás vagy pajzs. **Fagylovag:** Fagyjelek → részleges vagy teljes Zúzás. **Szentségtelen:** Dögvész-burst → a tartós ghúl mutációja. |
| Sámán | **Totemkerék:** egyszerre egy fő és egy kísérő totem él; azonos kategória lecseréli a régit. | **Elemi:** élő totempár + elemi egyezés → Túltöltés. **Erősítő:** Vihar↔Föld ritmus → Maelstrom → költés. **Hullámhívó:** Dagály↔Apály egymást előkészítő gyógyítás. |
| Szerzetes | **Áramlás:** a változatos technikák építik, ugyanannak a mozdulatnak a spamelése nem. | **Szélfutó:** meghatározott harcművészeti lánc → finisher. **Sörfőző:** Staggerben elhalasztott sebzés → tisztító főzet. **Ködszövő:** legfeljebb három Ködszál-társ → gyógyító tovagyűrűzés. |
| Pap | **Litánia:** a választott ima tettei verseket gyűjtenek; a teljes litánia egyszeri áldást mond ki. | **Fegyelem:** Engesztelés → sebzésből önheal és pajzsháló. **Csontpap:** nem halálos áldozat → Velő/Osszárium → gyógyítás. **Árnyék:** Őrület küszöb → erősebb, de életbe kerülő cast → tudatos levezetés. |
| Boszorkánymester | **Paktum + Lélekadósság:** a paktum ereje adósságot épít; azt csak visszafizető képesség csökkenti, a plafon új paktumot blokkol. | **Átok:** három átokoldal és egy átköthető Lélekfonal → elszívás. **Pusztítás:** Parázsbank → teljes bankos burst; maximumon Túlhevülés. **Demonológus:** legfeljebb háromféle tartós démon → paktum feloldása. |
| Démonvadász | **Kárhozat-terhelés:** a magasabb terhelés nagyobb erőt, túlterhelve nagyobb bejövő sebzést is jelent; tudatosan levezethető. | **Tombolás:** Lélektöredékek → mozgással begyűjtött Momentum. **Bosszú:** bejövő sebzésből Fájdalom → legfeljebb két Sigil vagy hasító költés. |
| Sárkányidéző | **Felerősítés:** első használat tölt, a következő I–III. rangon elenged; túl sok várakozás vagy nagy találat megszakíthatja. | **Perzselés:** vörös↔kék Eszencia-váltás → Izzás → egy burst. **Megőrzés:** egyszeri Visszhang és csak életet visszaállító Időlenyomat. |

Ezek nem puszta szerepcímkék: minden sorban van felépíthető állapot és azt
értelmesen elköltő vagy átváltó lépés. A HUD az aktív kasztodhoz tartozó
mérőt, láncot, töltetet vagy rúnát mutatja; a spellkönyv pedig az egyszerre
használható, legfeljebb hét képességedet. A pontos számokat továbbra is a
játékbeli leírásból olvasd, mert a balance élőben változhat.

A **kifizetés is látszik**, nem csak a felépítés: amikor a kaszt-magod
ténylegesen megerősít egy képességet, az akciósáv kiírja a kapott
százalékot (`✦ Kaszt-erő: +N%`), az Íjász Szélolvasása pedig a találat
pillanatában jelzi vissza az elsütést. Ha egyszerre él kombó-lánc befejező
és kaszt-bónusz, a kiírt százalék a kettő összege.

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
vanília alap, a felszerelés és az aktív talentek módosítják. Az új túlélési HUD ettől
függetlenül mindig a valódi jelenlegi/maximális értéket írja ki, ezért a későbbi HP-scaling
bekapcsolásakor nem kell tíz szívre visszanormalizálnia a nagyobb életerőt.

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
- A második megtanulható spec-slot alapból a 28. szinten nyílik. Egyszerre
  legfeljebb két specializációt őrizhetsz; háromspeces kasztnál a harmadikhoz
  az egyik helyet respecelned kell.
- `/spec switch <first|second|spec-id>` csak harcon kívül és közeli ellenségtől
  távol vált. Nem gyógyít, nem tölti vissza a kaszterőforrást, és nem nullázza
  a képességek várakozási idejét.
- Egyes sötét irányok frakciót, bűnös állapotot vagy történeti kaput kérnek.
- `/spec respec class` visszavonja a kasztspecializációt, általában
  frakcióvalutáért.
- A régi spechez kötött képességek lekerülnek, a hozzá kötött talentpontok
  visszatérnek.

### Doctrine, mesterség és záróképesség

- A 30., 40. és 50. szinten specializációnként két doctrine közül választasz
  a `/spec doctrine <30|40|50> <választás>` paranccsal. A doctrine a saját
  mechanikádat módosítja — nem csak cím vagy kozmetika —, és az adott
  spec-slothoz tartozik.
- Az 50. szinttől a spec-mesterség csak valódi harci használatból fejlődik;
  céltalan vagy AFK spellspam nem számít mesterségnek.
- A záróképességhez előbb teljesítsd a kasztod mesterpróbáját. Ezután a
  küldetésnaplóban megjelenik a saját **specializációs csúcspróbád**: 18
  sikeres használatot kér a megadott, már ismert képességeidből.
- A próba csak a megkövetelt aktív specializációval halad. Sikertelen,
  megszakított vagy másik spechez tartozó képesség nem növeli a számlálót.
  Teljesítéskor pontosan a saját szint-50-es záróképességed oldódik fel.

### Társak

Bizonyos specializációk befogott vagy idézett társsal játszanak. A `/pet`
menüben kezelheted őket:

- `/pet item` — befogóeszköz, ha az irányod használ ilyet;
- `/pet summon`, `/pet dismiss` — idézés és elbocsátás;
- `/pet select <hely>` — a társlista adott helyének kiválasztása és megidézése;
- `/pet release [hely]` — a kiválasztott vagy megadott társ végleges elengedése;
- `/pet name <név>` — elnevezés, szóközt tartalmazó névvel is;
- `/pet stance <aktiv|passziv|marad>` — viselkedés (szerep: aktív vadász, passzív kísérő, őrhelyen maradó).

A Vadmester Istállója alapból legfeljebb 3 befogott társat tart. A `/pet` menü
felső sora mutatja a társlistát; egy társra kattintva tartósan kiválasztod és
magad mellé hívod. Teli Istállóval új befogás csak elengedés után lehetséges.

A társ a te és a saját jogosult szörnyöléseiből is tapasztalatot szerezhet,
megvédhet, és ritka Társvértet viselhet. Idézéskor a rendszer biztonságos,
szabad állóhelyet keres a közeledben; ha ilyet nem talál, nem hozza létre a
petet, hanem másik helyet kér. A rituáléval egyszer már megkötött állandó társ
később új kellék nélkül visszahívható a `/pet summon` paranccsal. A
pontos befogható vagy idézhető lényt a választott irány és a játékbeli
visszajelzés határozza meg.

A **tartós társ** és az **ideiglenes idézett hullám** nem ugyanaz. Az
ideiglenes lény a képesség idejének végén eltűnhet; a megkötött Vadmester-társ,
Demonológus-démon, Nekromanta-udvaronc vagy Szentségtelen ghúl a karaktered
tartós társállapotához kötődik és relog után újraépíthető. Egy tartós idéző
képesség nem hoz létre mellette egy második, ideiglenes ikerpéldányt.

Ha egy roster megtelt, az új tartós idézés még az erőforrás és a cooldown
elköltése előtt visszautasítható; előbb engedj el vagy arass le egy régi tagot.
A Szentségtelen Dögvész-burstje csak valóban meglévő saját ghúlt mutál: a
fokozat korlátozott, ténylegesen erősíti a társat és újrabelépés után is
megmarad. Ghúl nélkül nincs láthatatlan vagy elvesző mutáció.

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
- `/profession forge` — Itemization 2.0 műhely: reroll, Stat Lock, Ascension és salvage.
- `/szakmacel` — a szakmád heti közös célja.

A teljes receptkatalógus nem része ennek a kézikönyvnek. A receptkönyv jelzi a
szintet, a hozzávalót, az esetleges tervrajzot és azt is, ha valamilyen
szolgáltatói kellék hiányzik.

Craftolni csak abból a szakmából tudsz, amelyet **éppen gyakorolsz**. A korábbi
szakmád szintje megmarad a profilodon, de a receptjei váltás után zárva vannak.

### Authored felszerelés és item műhely

Az authored gear nem „véletlen kardfajta”: a recept előre megmondja a template-et,
és csak a template által felsorolt roll-range-ekben van randomness. A szakmaszint,
tervrajz és mestermű jelző additív, plafonozott minimum-qualityt adhat. A tárgyon
megmarad a készítő UUID-ja, a név craftkori pillanatképe, a szakma, hely/idő és a
Mestermű jelző; egy későbbi névváltás nem írja át az eredetét.

A jelenlegi 48 tárgyas katalógus starter, mid-game és high-end felszerelést, három
szettet, valamint mining/fishing/hunting/farming, profession, wilderness, event és
boss forrásokat köt össze. A tíz rúna közül a Súly nagy célpont ellen, az Oltalom
alacsony életerőn, a Vadász pedig nem játékos célpontra lőve ad bounded előnyt.

A főkézben tartott canonical tárggyal nyisd meg a `/profession forge` felületet:

- **Full Reforge:** minden rollolható stat újragurul;
- **Stat Lock:** kattints egy statra, így az változatlan marad, a többi újragurul;
- **Quality Amplifier:** a következő reroll minimum qualityjét emeli;
- **Stability Seal:** a reroll megtörténik, de a következő költséglépcső nem nő;
- **Ascension:** előre megmutatott, ritka és determinisztikus fejlesztés;
- **Salvage:** irreverzibilis, veszteséges bontás runa-/salvage alapanyagra.

A gombok megmutatják a költséget és az eredményt; a tényleges művelethez
**SHIFT+katt** kell. A reroll count, az Ascension, a rúnák és a provenance piaci
adásvétel, relog és restart után sem nullázódik.

### A pilot survival gazdasági út

Egy Bányász valódi vanilla érctörésből, közös napi anti-farm sapkával ritkán
**Sarkfény-cseppkövet, Viharkvarcot, Mélységi Borostyánt, Néma Kristályt** vagy a
Netherben **Kárhozat Parazsát** talál. Silk Touch, regenerált/pajzsolt blokk, AFK,
védett régió és tele inventory nem termel ritka jutalmat. A Kovács ezt feldolgozott alapanyagokkal authored
Vadvidéki Eskükarddá vagy tervrajzos Glatziendorfi gearré kovácsolja. A gear
rerollolható, rúnázható és — trade policy szerint — a piacon eladható. A követett
világboss személyes **Fekete Villám Szilánkja** olyan komponens, amely a Jégvért
Ascensionjéhez kell. Az Ascension után ugyanaz az item UUID marad, és a rollok a régi
relatív qualityn maradnak az új tartományban.

A Bestiárium első elejtés után mutatja az authored mob rangját és archetípusát;
további elejtésekkel képesség-/ellenállás-jegyek, majd a forrásprofil nyílik meg.
Pontos drop rate-et nem spoilerez.

### Miért van, hogy egy recept ugyanannyit ad, mint a műhelyasztal?

Mert szándékosan. Az ilyen recept **gyakorló receptként** van megjelölve a
receptkönyvben: azért létezik, hogy a szakma elején legyen mit csinálni és
legyen miből XP-t szerezni — nem azért, hogy nyerj rajta. Gyakorló recept csak
alacsony szinten van, és sosem kér egyedi alapanyagot.

Minden más recept ad valamit a műhelyasztal fölé: vagy **több jön ki ugyanabból
az anyagból**, vagy olyasmi készül, amit vanília úton nem tudsz megcsinálni —
sorsolt minőségű felszerelés, valódi hatású főzet, bűvölőkönyv, étel-buff vagy
a szakmaláncok egyedi alapanyaga.

Az alkimista főzetei és a bűvölő tomusai **valódi hatást hordoznak**: a főzet
megiható és dobható, a tomus üllőn átadja a bűbájt.

### Melyik szakma mit ad?

Mindegyiknek megvan a maga terméke, és ezek nem helyettesítik egymást:

- **Kovács** — fém fegyver és páncél, sorsolt minőséggel.
- **Favágó** — íjak, fejszék és az erdőjáró bőrdarabok.
- **Bányász** — több érc ugyanabból a fejtésből, plusz saját csákány- és ásóvonal.
- **Halász** — horgászbotok és a víz alatti felszerelés.
- **Szakács** — ételek és italok rövid, tematikus buffokkal.
- **Bűvölő** — bűvölőkönyvek, tekercsek és a hét rúna.
- **Alkimista** — **harci, azonnali** főzetek: erő, gyorsaság, gyógyítás, méreg.
- **Gyógynövényes** — **hosszú hatású, harcon kívüli** kenőcsök és teák, plusz a
  **Méregvonó Pép**, az egyetlen hordozható ellenszer. Az utóbbi minden aktív hatást
  levesz rólad — a rosszat is, a jót is, tehát nem mindig éri meg meginni.

Az alkimista és a gyógynövényes szándékosan egymás ellenpárja: az egyik hatást ad,
a másik levesz.

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
| Világboss | közösségi nagy ellenfél, jelzett támadásokkal és személyes contribution-jutalommal |
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

Egy nagy esemény admin- vagy automatikus indítása előbb biztonságos helyet
kereshet. A távoli hang/részecske csak érkezési előjel; a világboss, invázió,
meteor vagy kíséret tényleges indulását a szerver eseményüzenete és az
`/events status` állapota igazolja.

### A világ nehézsége

A biztonságos vidékektől távolodva a szörnyek szintje emelkedhet. A normál vadon
Lv. 1–50 között halad; a territory, biome, föld alatti mélység, dimenzió, Vérhold
vagy más event együtt legfeljebb az általános Lv. 70 survival capig emelheti.
Authored rom, dungeon vagy boss saját szintet írhat elő; 70 fölötti szint nem a
végtelen távolsági skála, hanem külön boss/encounter tartalom. A HP gyorsabban,
a sebzés óvatosabban nő, így a magas szint nem automatikus előjel nélküli one-shot.

A **Veterán** erősebb alapellenfél, az **Elit** legfeljebb két, a neve mellett
röviden jelzett affixet kaphat; a Bajnok/Miniboss/Boss saját mechanikákat használhat.
Charge, slam, lövedéksorozat vagy zóna előtt vanilla kliensen is hang/részecske
telegráf látható — ezt figyeld, ne csak a nametaget. A spawnerből származó mobok
nem a vadon kihívásának pótlására valók, ezért nem kapják meg ugyanazt a skálázást
és jutalmat.

Világbossnál nem csak a killing blow számít. Érdemi bosssebzés, tankolás és a
támogatott encounter-célok contributiont adnak; AFK, önmagadon farmolt heal vagy
harc előtti padding nem. A boss HP-ja a harc eleji résztvevő-snapshot alapján,
csökkenő hozadékkal skálázódik, ezért ki-/belépéssel nem ugráltatható. A küszöböt
elérő játékos személyes ascension komponenst kap. Ha tele az inventoryd, a jutalom
nem esik a földre: felszabadított hellyel a következő reconnectkor újrapróbálható.

Az authored harci felszerelés enyhén figyelembe veszi a szintedet, kasztodat,
specializációdat, jelenlegi gear-statisztikáidat, üres felszereléshelyedet és a
forrást. Ez nem személyes kívánságlista: más buildhez vagy kaszthoz való,
piacon értékes darab továbbra is eshet. A közelmúlt ismétlődése csak finoman
módosítja a súlyokat, és nem garantál rövid úton Mitikus tárgyat; az előzmény
kilépéssel vagy szerver-újraindítással sem nullázódik.

### Szezonális liga

A frakciók nem csak raiddel szerezhetnek pontot: világboss, közösségi cél,
rontástisztítás, párbaj és más identitáshoz illő tevékenység is számíthat.
A szezon végén jutalom és új pontverseny jön.

> **A szezonzárás nem wipe.** A karaktered, felszerelésed, pénzed,
> szakmáid, talentjeid és birtokod megmarad; a liga pontversenye indul újra.

### A halkabb történetek

Tábortűzi történethez ülj le egy olyan székre vagy ülőblokkra, amelytől egy főirányban
pontosan egy üres blokk, majd egy égő normál vagy lélektűz-campfire áll. A közvetlen
tábortűz-kattintás nem indít mesét; a kivárás és a jutalom pillanatában is ugyanazon
a széken kell ülnöd, és az elrendezésnek változatlannak kell maradnia. A krónikák
megőrzik a korszak eredményeit, és titkos helyek várják az első felfedezőt. Egyes világhelyek vagy
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

### Honnan jön a küldetés, és hová kell visszavinni?

Minden küldetésnek saját forrása van, és csak ott vehető fel:

- **NPC-küldetés** — a mesélőre kattintva veszed fel. Ha a feladatait
  teljesítetted, a küldetés „kész" állapotba lép, és VISSZA kell térned a
  jogos leadási ponthoz (jellemzően ugyanahhoz az NPC-hez) — a jutalom és a
  záró párbeszéd ott jár.
- **Megbízás** — a küldetésnapló „Megbízások" füléről vállalható el
  kattintással; a teljesítéskor magától lezárul.
- **Történet-folytatás** — egy lánc előző lépésének teljesítése oldja fel
  (van, amelyik azonnal folytatódik, van, amelyikhez el kell menned a
  forrásához), a dialógus-választások pedig elágazó folytatást nyithatnak.
- Néhány küldetést helyszín, tárgy vagy világesemény indít.

### Alapparancsok

- `/quest log` — kattintható napló, öt füllel: Aktív, Kész (leadható),
  Megbízások (itt vállalhatsz), Elérhető (mutatja, hol indul), Teljesített.
- `/quest list` — a számodra látható küldetések.
- `/quest info` — aktív küldetéseid és haladásod.
- `/quest track <id|off>` — követett küldetés kijelölése (a naplóban ★).
- `/quest abandon <id>` — feladás.
- `/quest choose <token>` — a párbeszédben megjelenő kattintható választás egyszer használatos beváltása; a tokent nem kell és nem érdemes kézzel beírni, lejárat után beszélj újra az NPC-vel.

Az első belépéskor egy rövid kezdő lánc automatikusan vezet végig az alapokon.
Kövesd a képernyő jelzéseit; ez az útmutató nem sorolja fel előre a
megoldásait.

### NPC-k és történet

A küldetést adó vagy folytató NPC fölött személyes részecskejelzés jelenhet
meg — a színe elárulja, mi vár ott: arany = leadható küldetésed van nála,
sárga = új küldetést ad, kék = napi/heti kínálat, lila = kaszt-tartalom,
szürke = folyamatban lévő feladatod célpontja. Ha egy NPC több küldetést is
kínál, kattintható listából választhatsz. A párbeszédekben választási
lehetőséget is kaphatsz, amely másik folytatást nyit.

Egyes NPC-k naponta rotáló kínálatot adnak, más feladat hetente vagy
szezononként ismételhető.

### Próbák, rejtvények és fejezetek

- A kasztpróbák és mesterpróbák a választott szerepedhez illő feladatokat
  adnak.
- Az 50-es specializációs csúcspróba a mesterpróba után, csak a megfelelő
  aktív speccel jelenik meg. A megadott képességek 18 sikeres használata oldja
  fel a záróképességet; nem kér külön NPC-t vagy rejtett arénát.
- A történeti fejezetek a szezonhoz kapcsolódhatnak.
- A rejtvényküldetés nem mutatja meg a konkrét célt — ez szándékos.
- A sötét és vezeklési láncoknak tartós következménye lehet.
- A `/bestiarium` felfedezői és harci mérföldköveket gyűjt: a négy kategória
  (szörnyek, receptek, territóriumok, világbossok) kattintva lapozható, az
  ismeretlen bejegyzések „???"-ként várnak a felfedezésre. A szörnyeknél
  fajonként számoljuk az elejtéseket: elég kill után a bejegyzés
  tudás-fokozatot lép (kódex-jegyzet, zsákmány-jegyzet, végül mestervadász
  jelölés).

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
| `/spec switch <first|second|spec-id>` | megtanult kasztspecializáció aktív slotjának váltása biztonságos helyen |
| `/spec doctrine <30|40|50> <választás>` | a megadott szint végleges doctrine-választásának rögzítése |
| `/spec respec <class|profession>` | specializáció visszaváltása |
| `/spec esku <irgalom|itelet|oltalmazas>` | Paplovag-irány (Eskü) választása az ülésre |
| `/spec ima <vigasz|ostor|csend>` | Pap-litánia (ima) felvétele az ülésre |
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
| `/hud` / `/hud toggle <szekció>` | HUD-állapot és szekciók ki-/bekapcsolása |
| `/hud edit` | saját, restartálló HUD-layout kattintható szerkesztése |
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
- a saját frame-ed alatti party HUD mutatja a tagok életét, class resource-át és állapotát;
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

## 17. Prologue / Season 0 — amit játékosként tudnod kell

A **Prologue** az IceSMP egyszeri nyitó korszaka. Nem külön karakter vagy külön szerver:
a most megszerzett legitim karakterhaladásod, tárgyaid, achievementjeid és kozmetikai
státuszaid **nem törlődnek** a Season 1 kezdetén.

Amíg az üzemeltetők nem élesítik és az állapot `DORMANT`, a Prologue semmilyen
saját fejlődési, tartalmi vagy utazási korlátot és semmilyen Prologue HUD- vagy
ambient hatást nem alkalmaz. Ilyenkor a szerver normál konfigurációja érvényes.

Az élesített nyitó korszakban szándékos fejlődési plafon védi a később érkező játékosok esélyeit.
Az alapbeállítás szerint a kasztod **25. szintig** fejlődhet; a plafon fölé XP sem bankolódik.
A kasztspecializáció és a normál relikvia-progresszió létezik a világban, de a Prologue alatt
még nem választható/szerezhető meg rendes játékúton. Egyes magasabb loot- és tervrajzszintek
ugyanezért későbbi tartalomnak számítanak. A játékbeli menü mindig jelzi az aktuális kaput.

A **Kárhozat Kapuja** már a Prologue előtt is a világ része. Season 0 alatt elmehetsz hozzá,
a hozzá kötött felfedező- és lore-tartalom működhet, de a Kapun **nem lehet átjutni a Netherbe**.
Saját Nether-portált továbbra sem lehet szabadon létrehozni. A Kapu állapotának romlását időnként
a HUD vagy a fallback kijelzés stabilitásmérője és helyi események jelezhetik.

A Prologue lezárása után a világ wipe nélkül lép tovább Season 1-be. A normál szezonliga ekkor
indul el ténylegesen. Az új vagy lemaradó, 25. szint alatti karakterekhez configolható
**catch-up XP-bónusz** tartozhat; ez csak a normál XP-t gyorsítja, nem oszt dupla tárgyjutalmat.

A nyitó korszak végének történeti részleteit ez az útmutató szándékosan nem spoilerezi.

---

*Aetrinita alatt mindenki ugyanúgy ébred. Onnantól azonban nincs két egyforma
út. Válassz olyat, amelyről egyszer érdemes lesz krónikát írni.*

---

<sub>Dokumentált release: `4643ab53586f0c1ee7352df16dcd477013e6fad4`</sub>
