# Frakció-, bűn- és Suttogó-rendszer rework

Ez a dokumentum a RED, BLUE, NEUTRAL és DARK frakció jelenleg implementált játékmeneti
modelljét írja le. A cél egy könnyen érthető, HUD-mérők és aktív frakcióképességek
nélküli rendszer, amelyben minden oldal más helyzetben erős, és egyik se legyen általánosan
jobb a többinél. Ez balance-cél; az élő játékpróba még hátravan.

### A terv és a megvalósítás határa

Az [eredeti beszélgetés](https://chatgpt.com/share/6a90b142-b5f4-83eb-9beb-84a7a5471cba)
Faction/Crime-terve és W1–W33 Suttogó-követelményei ismét elérhetők. A későbbi
felhasználói egyszerűsítés felülírja a frakciómérőket, a számszerű Suttogó-gyanút
és annak időbeli csökkenését. A DARK civil ellenséges jogállása szintén a későbbi
döntés szerint marad; a korábbi „nem Wanted DARK normál PvP-jogállású” javaslat
nem a végleges irány.

W18 legalább egy korlátozott aktív titkos feladatot kér. A kultista ametisztátadás
ezt a minimumot megvalósítja; teljes szabotázs-/információ-/küldetéshálózat és
forgó titkos boltkészlet nem kötelező pótlás. W3–W4 felfedezhetőségi részletei,
W8 eseményhez kötött bizonyítéka, W9 teljes láthatósági szabálya és W28 önkéntes
visszaútja viszont nem tekinthetők lezártnak. Az eredeti terv a régi profilok
biztonságos átvezetését is kéri. Ezek konkrét feladatait a
[ROADMAP](../ROADMAP.md#frakcióbűnsuttogó-rework-átvételi-kapui) tartalmazza.
Az alábbi implementált-elemek lista nem jelenti az eredeti terv teljes átvételét.

## Tervezési alapelvek

1. A frakcióidentitást kevés, jól felismerhető passzív és társadalmi szabály adja.
2. Nincs frakcióenergia, töltés, reputációs csík vagy új HUD-érték.
3. Nincs frakcióhoz kötött aktív képesség; a kasztok, talentek és tárgyak maradnak az
   aktív játékmenet fő forrásai.
4. Az előnyök kontextuálisak. Nem adunk minden helyzetben érvényes sebzés- vagy
   gazdasági fölényt.
5. A DARK erős természetfeletti előnyeinek állandó, érthető társadalmi és játékmeneti
   ára van.
6. A rejtett szerepek kockázata diszkrét állapotokkal működik, nem láthatatlan
   pontszámmal.

## Frakciók végleges szerepe

| Frakció | Fő identitás | Egyszerű előnyök | Korlát / ellensúly |
| --- | --- | --- | --- |
| RED – Láng | veszélyvállaló, hőhöz szokott harcos | erős környezeti tűz-, láva- és magma-védelem; RED signature ételek | entitásból és scriptelt harcból érkező tűz ellen csak részleges vagy semmilyen védelem; nincs általános sebzésbónusz |
| BLUE – Fagy | túlélő, kitartó felfedező | fagyásimmunitás, részleges fulladásvédelem, 25% esély a felsorolt természetes exhaustion események megtakarítására; BLUE signature ételek | az esély csak sprintre, sprintugrásra, úszásra és vízi mozgásra vonatkozik; nem ír felül éhséget, admin- vagy scriptelt ételszint-változást |
| NEUTRAL – Menedék | biztonság, utazás, diplomácia | fél zuhanássebzés; spontán passzív/semleges mob-aggro és Enderman-szemkontaktus békéje; saját gazdasági tanács | ütés, explicit célzás, küldetés-, dungeon- és eseményharc megtöri vagy felülírja a békét; nincs raidkirály és nincs harci csúcselőny |
| DARK – Kitaszított | törvényen kívüli, természetfeletti túlélő | fél Wither-sebzés és -idő; ambient undead béke; vad undead éjszakai, esélyes békéje; DARK/Suttogó hálózat és signature ételek | normál gyógyítás csak 70%; polgári boltok és játékos-karaván tiltva; komp kétszeres ár; a DARK áldozat megölése nem bűn |

### Milyen ellensúlyok akadályozzák az általános fölényt?

- RED a környezeti hőveszélyben stabil, de nem kap általános PvP/PvE sebzéselőnyt.
- BLUE hosszú utazásnál takarékos, de az előnye valószínűségi és szűk oklistára zárt.
- NEUTRAL a legkényelmesebb felfedező és civil választás, de a béke nem működik
  releváns harci tartalomban.
- DARK sok veszélyt kerülhet el, de minden hétköznapi gyógyítása gyengébb, és a civil
  gazdaság jelentős része kizárja.

## DARK: erős előny, valódi hátulütő

### Gyógyítás

- Minden DARK-tag normál gyógyítása a kiszámolt érték 70%-a.
- A szabály a vanilla/Paper `EntityRegainHealthEvent` gyógyításokra és a saját kaszt-,
  spell-, életlopás-, harcon kívüli regeneráció-, Dacoló- és Időlenyomat-gyógyításokra is vonatkozik. A saját pozitív gyógyítás
  a közös `SpellHealingUtil` kapun halad át. HP-plafon igazítása és megszakadt képesség
  költségének visszaállítása nem gyógyítás, ezért nem kap ismételt szorzót.
- Vérhold alatt és `DUNGEON` területen a szorzó 100%. Ezekben a magas tétű
  helyzetekben a hátrány nem teheti használhatatlanná a frakciót.
- A szorzó fix játékszabály, nem külön HUD-mérő.

### Társadalmi kizárás

- DARK-tag nem nyithat és nem használhat polgári NPC-boltot.
- Egyetlen kivétel a `factions.dark.blackmarket-npc` alatt megadott feketepiac.
- DARK-tag és száműzött civil nem indíthat játékos-karavánt a közös kasszából.
- A száműzetés a civil boltokból és a Tanácsból is kizár.
- A civil fővárosok kapuja a DARK-tagságot és a száműzetést önállóan felismeri;
  a Wanted-menlevél ezeket nem oldja fel. Thanaopolis nem civil főváros.
- Az első megtámadott fél önvédelme a combat-tag idejében jogszerű, DARK-ként is.
- A komp alapdíjának kétszeresét fizeti.
- A DARK áldozat továbbra is törvényen kívüli: megölése nem generál Infamyt.

Ez a hátrány nem egy újabb szám, amit folyamatosan figyelni kell. A játékos konkrét
helyzetekben, természetes visszajelzésből érti meg: gyengébben gyógyul, a civil
kereskedő elutasítja, a révész felárat kér.

## Tagság és DARK-belépés

A tagság nem a bűnrendszer automatikus kimenete. A DARK-belépés tudatos, háromlépcsős
folyamat:

1. **Exile:** a játékos eléri a száműzetési feltételt, vagy egy Suttogó
   lelepleződése miatt száműzött lesz. A jelenlegi frakciótagsága ettől nem változik.
2. **Oath:** a száműzött játékos külön kiadja a `/faction status eskü` parancsot.
   Ez tartós DARK-esküt rögzít, de még nem változtat tagságot.
3. **Membership:** a játékos kiadja a `/faction join dark` parancsot, majd a
   megerősítési ablakon belül megismétli. Csak ekkor lesz DARK-tag. A megerősítés
   nem kapcsolható ki nullás konfigurációval. Ez az út nem igényel belépést a tiltott
   semleges fővárosba; a civil frakcióváltás helykapuja külön szabály.

Az admin `/faction set <játékos> dark` útja az Exile és Oath előfeltételt együttesen
beállítja, hogy ne hozzon létre lehetetlen DARK-állapotot.

## A bűnrendszer külön tengelyei

| Tengely | Jelentés | Mi kapcsolja be? | Mi nem történik automatikusan? |
| --- | --- | --- | --- |
| Infamy | az aktuális bűnpontok száma | gyilkosság, árulás, lopás és más explicit bűnforrás | önmagában nem jelent Wanted vagy DARK-tagságot |
| Wanted | jogos vérdíjcélpont | a konfigurált vérdíjküszöb elérése | nem száműz és nem tesz DARK-taggá |
| Exile | a törvényből való tartós kitaszítás | száműzetési küszöb vagy Suttogó-leleplezés | nem tesz esküt és nem vált frakciót |
| Oath | a DARK felé tett tudatos eskü | `/faction status eskü`, csak Exile után | nem vált frakciót |
| Membership | tényleges, látható frakciótagság | `/faction join ...` vagy adminbeállítás | nem következik pusztán Infamy/Exile állapotból |

A régi `isSinner` jelentése kizárólag `Infamy > 0`. **A sötét specializációkat ez nem
nyitja:** kötelező a DARK-tagság és a letett Sötét Eskü (`OATH` kapu). A specializáció
lezárul, ha valamelyik feltétel megszűnik; fejlődése megmarad.

### Tisztítás és vezeklés

- A normál bűntisztítás az Infamyt és a hozzá tartozó Wanted állapotot törli.
- Az Exile és az Oath ettől külön megmarad.
- A teljes vezeklés kifejezetten az Infamy, Wanted, Exile és Oath tengelyeket zárja le.
- Egy új bűnsorozat csak akkor növeli a generációszámot, amikor az Infamy nulláról
  pozitívra vált; az exact-once pénzügyi/bűn outboxok így továbbra is biztonságosak.

## Suttogó-rendszer

### Belépés

- Csak explicit, nem DARK frakciótag válhat Suttogóvá.
- Száműzött játékos nem kezdhet új Suttogó-rítust. Leleplezés után a száműzetés
  feloldása mellett fix 24 órát is várni kell; ezt a fedezék nem rövidíti.
- A rítus éjjel, sculk vagy sculk catalyst blokkon, főkézben tartott meghívóval,
  **SHIFT + jobb kattintással** és HP-áldozattal indul. A tárgy lore-ja ezt kiírja.
- A HP-ellenőrzés és a tartós előkészítés megelőzi a fogyasztást. A sikerjelzés és
  a rítus bizonyítéka csak a tartós szerepváltás után keletkezik.
- A félbeszakadt rítus exact előtte/utána inventory-bizonylattal helyreállítható.
  Ismeretlen vagy kevert inventory-állapotnál zárolás és adminvizsgálat következik.
- A közeli tanú nem szakítja meg a rítust. Ehelyett pontos bizonyítékot kap a rítust
  végző játékos ellen.

A jelenlegi időkapu csak `NORMAL` világban kér éjszakát; Nether/End környezetben
sculkon nappal is elindulhat a rítus. A magány a titkosságot segíti, nem belépési
előfeltétel. Ez a kód tényleges működése; az eredeti W5 szigorúbb magányfeltétele
és a dimenziók lore-indoklása külön tisztázandó tétel a ROADMAP-ben.

### Pontos bizonyíték

A régi általános „tanú-token” helyett a bizonyíték két UUID-hoz kötött:

- ki látta az eseményt;
- kit látott.

A bizonyíték időkorlátos, egyszer használható, és másik játékos ellen nem váltható be.
A jelenlegi rekord lejáratot és felhasználási jelzőt tárol; eseménytípus vagy
eseményazonosító még nincs benne. A tanú–cél kötés ezért önmagában nem teljes W8-megfelelés.
A gyanúsított tartós profiljában él, ezért egyik fél kilépése és a restart sem törli.
Ugyanaz a tanú–gyanúsított pár a nyom élettartamán belül nem kap új, farmolható
bizonyítékot; az elhasznált nyugta a lejáratig megmarad. Falon át nincs bizonyíték:
a tanú saját Folia-régióján ellenőrzött rálátás kell. Nem birtokolt régiót keresztező
sugár bizonytalan, ezért nem ad bizonyítékot.
A `/suttogas vád <játékos>` először pontos online célpontot old fel, majd csak a
tanú–cél párhoz tartozó bizonyítékot váltja be a fokozatváltozással egy mentésben.
A cél időközbeni kilépése nem szakítja ketté ezt a tranzakciót. Hamis vagy rossz célpontú vád nem
mozgat állapotot.

Bizonyíték keletkezik:

- látott Sötét Rítusnál;
- látott frakcióárulásnál;
- a titkos ametisztátadás megfigyelésénél;
- amikor egy kívülálló közelről látja, hogy az éjszakai undead-békesség egy
  Suttogót elenged.

### Fix leleplezési fokozatok

| Állapot | Jelentés | Következő érvényes vád |
| --- | --- | --- |
| `CLEAN` | nincs aktív nyom | `OBSERVED` |
| `OBSERVED` | egy hiteles megfigyelés | `SUSPECTED` |
| `SUSPECTED` | két hiteles megfigyelés | `EXPOSED` |
| `EXPOSED` | a szerep lelepleződött és megszűnt | végállapot |

Nincs gyanúpont, százalék, súlyozás, decay vagy konfigurálható leleplezési küszöb.
A harmadik érvényes vád mindig leleplez.

Sikeres kultista esemény **csak a minősített résztvevőnek** ad legfeljebb egy fokozat
fedezéket (`SUSPECTED → OBSERVED`, `OBSERVED → CLEAN`). `EXPOSED` állapotból nem
állítja vissza a szerepet. A minősített, online résztvevő lootot akkor is kap, ha nincs
eltávolítható fokozata. Az esemény kudarca nem ad fedezéket vagy részesedést.

A `/suttogas megbízás` leírja a konkrét feladatot: aktív rítus vagy hírvivő kultistájának közönséges
ametisztszilánkot kell átadni főkézből, SHIFT + jobb kattintással. Eseményenként egy
átadás számít; az átadás szemtanúi is bizonyítékot szerezhetnek. A részvétel az adott
transiens eseményhez kötött; szerverleállításkor az esemény megszakad.

A titkos csatorna tartós véletlen álnevet használ, a valódi nevet és account-UUID-t
nem küldi ki. A `/suttogas állapot` kiírja a fokozatot és a rítus várakozását.
Profilolvasási hiba esetén hibát jelez, nem állít valótlan `CLEAN` állapotot.

### Lelepleződés

- A rejtett Suttogó-szerep az utolsó állapotírással együtt megszűnik.
- A játékos ugyanabban a tartós commitban Exile állapotot és visszatérési határidőt kap,
  nem automatikus Infamyt, Oathot vagy DARK-tagságot.
- A szerverbroadcast konfigurálható marad.
- A DARK továbbra is hallhatja a Suttogó-csatornát, ha a meglévő kapcsoló engedélyezi.

## Civil egyensúly, kassza és szezon

- Az első választás ingyenes. Minden későbbi RED/BLUE/NEUTRAL váltás ugyanazt a
  díj-, cooldown-, főváros- és szezonplafon-szabályt követi, NEUTRAL-ból is.
- A király és a Tanács nem vehet ki személyes pénzt a közös kasszából. A közösségi
  kiadások, karaván és adomány megmaradnak; `treasury withdraw` csak admin-karbantartás.
- A becsületpárbaj nem tisztít bűnt. A NEUTRAL saját polgárának megölése is árulás.
- NEUTRAL raid/war/spy katonai pontot nem kap. A civil és közösségi pontforrások megmaradnak.
- A ligapont a minősített, elmúlt 7 napi aktív frakciólétszám fölött csökkenő hozamú:
  az osztó `sqrt(max(1, aktív létszám / population-reference))`, alap referencia 5.
  Az egész pontos rendszer kerekít, pozitív forrásnál minimum 1 pont; ezért ez a kis
  pontértékű forrásokat kevésbé fékezi, mint a nagy eseményjutalmakat.
- Személyes bajnoki jutalomhoz legalább 3 igazolt hozzájárulás kell. Kategóriánként és
  UTC-naponként egy számít (közösségi cél, megtisztítás, minősített bossrészvétel,
  érvényes raid/war/párbaj/kémküldetés). Online jelenlét nem hozzájárulás.
- A részvétel szezonhoz és az aktuális tagsági időponthoz kötött; frakcióváltással nem
  vihető át. Offline is megmarad. Az `/events status` mutatja a személyes feltételt.

## Eltávolított rendszerek

- periodikus frakcióadó és aktív adóütemező;
- `/faction king tax` parancs és adókulcs-megjelenítés;
- frakció-ételkötelezettség, honvágy-időzítő és periodikus Éhség-debuff;
- Suttogó gyanúpont, súlyozott gyanúforrások és automatikus decay;
- konfigurálható gyanúküszöb és leleplezési bűnpont;
- frakcióhoz kötött aktív képességek és új HUD-mérők.

A régi adósság/outbox és protokollmezők kompatibilitási okból a tartós formátumban
megmaradhatnak, de nincs őket meghajtó runtime scheduler, játékosparancs vagy HUD-kijelzés.
Ez a változtatás nem valósít meg teljes régiadat-migrációt. Az eredeti terv ezt
kéri; nem felhasználó által elvetett követelmény. A `processOutbox` pénzügyi
segédmetódusnak nincs aktív hívója, ezért a megmaradt régi tételek rendezését sem
a `load()`, sem a kiürített `collectTaxes()` nem végzi el. A már levont pénzt nem
szabad egyszerűen törölni vagy ismét beszedni; az ellenőrzött átvezetés külön
kiadási feltétel a ROADMAP-ben.

## Parancsok

| Parancs | Eredmény |
| --- | --- |
| `/faction status` | megmutatja a tagságot, Infamyt, Wanted, Exile, Oath és Suttogó-fokozat állapotát; nem kerül HUD-ra |
| `/faction status eskü` | Exile után rögzíti a DARK esküt |
| `/faction join dark` | Exile + Oath után kétlépcsősen megerősíti a tényleges tagságot |
| `/suttogas állapot` | a saját fokozat és a fix visszatérési várakozás |
| `/suttogas megbízás` | a kultista átadás szabályai és kockázata |
| `/suttogas vád <játékos>` | az adott célhoz kötött bizonyítékot egyszer beváltja, és egy fokozatot léptet |

## Konfigurációs felület

Megmaradó fő egyensúlyi beállítások:

- a RED/BLUE/NEUTRAL/DARK környezeti passzívok meglévő szorzói és esélyei;
- Suttogó undead-béke esélye és harci kivételei;
- bizonyíték élettartama és tanúsugara;
- rítus HP-költsége és leleplezési broadcast;
- Suttogó feketepiaci kedvezménye;
- signature ételbuffok időtartama.

Nem konfigurálható a DARK 70%-os normál gyógyítása, a kétszeres kompár, a három
Suttogó-vád és az egyfokozatú fedezék. Ezek a játékos számára tanulható, stabil
szabályok, nem adminisztratív finomhangolók.

## Balance-hipotézis és kézi ellenőrzés

| Teszt | Elvárt eredmény |
| --- | --- |
| RED lava/fire környezetben | jelentősen kevesebb környezeti sebzés, de scriptelt harci tűz nem válik triviálissá |
| BLUE hosszú sprint/úszás alatt | átlagosan 25% releváns exhaustion-megtakarítás, garantált éhségimmunitás nélkül |
| NEUTRAL vadonban | spontán béke működik; ütés és event/quest célzás után a mob harcol |
| DARK normál regen/potion/étel mellett | a gyógyulás 70%-a érvényesül |
| DARK Blood Moon/DUNGEON alatt | teljes gyógyítás marad |
| DARK civil bolt/karaván/komp | civil bolt és karaván elutasít; feketepiac nyílik; komp kétszeres díjat von |
| Infamy eléri a Wanted küszöböt | vérdíjlista aktiválódik, tagság nem változik |
| Infamy eléri az Exile küszöböt | Exile aktiválódik, Oath és tagság nem változik |
| Exile → eskü → DARK join | mindhárom lépés külön és sorrendben szükséges |
| három külön érvényes Suttogó-vád | pontosan `OBSERVED`, `SUSPECTED`, majd `EXPOSED` |
| rossz célnév vagy másik cél | bizonyíték nem használható fel más ellen |
| kultista siker `SUSPECTED` állapotban | egy fokozatot visszalép, nem törli az egész kockázatot |

## Implementált elemek

A kiadási és játékpróba-kapuk kizárólag a [ROADMAP](../ROADMAP.md) alatt élnek.

- [x] Crime state külön tengelyekre bontva.
- [x] DARK belépési folyamat szétválasztva.
- [x] `/faction status [eskü]` elkészítve.
- [x] Fix Suttogó-fokozatok és tartós, célhoz és tanúhoz kötött bizonyíték elkészítve.
- [x] Suttogó pont/decay tick eltávolítva.
- [x] DARK gyógyítási és társadalmi hátrányok bekötve.
- [x] Adóütemező, királyi adóparancs és ételkötelezettség eltávolítva.
- [x] Konfiguráció, üzenetek és advancement-szövegek frissítve.
- [x] Célzott regressziós suite hozzáadva.
