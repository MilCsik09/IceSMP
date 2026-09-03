# Frakció-, bűn- és Suttogó-rendszer rework

Ez a dokumentum a RED, BLUE, NEUTRAL és DARK frakció új, végleges játékmeneti
modelljét írja le. A cél egy könnyen érthető, HUD-mérők és aktív frakcióképességek
nélküli rendszer, amelyben minden oldal más helyzetben erős, és egyik sem általánosan
jobb a többinél.

## Tervezési alapelvek

1. A frakcióidentitást kevés, jól felismerhető passzív és társadalmi szabály adja.
2. Nincs frakcióenergia, töltés, reputációs csík vagy új HUD-érték.
3. Nincs frakcióhoz kötött aktív képesség; a kasztok, talentek és tárgyak maradnak az
   aktív játékmenet fő forrásai.
4. Az előnyök kontextuálisak. Nem adunk minden helyzetben érvényes sebzés- vagy
   gazdasági fölényt.
5. A DARK erős természetfeletti előnyeinek állandó, érthető társadalmi és fenntartási
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

### Miért nincs „legjobb” frakció?

- RED a környezeti hőveszélyben stabil, de nem kap általános PvP/PvE sebzéselőnyt.
- BLUE hosszú utazásnál takarékos, de az előnye valószínűségi és szűk oklistára zárt.
- NEUTRAL a legkényelmesebb felfedező és civil választás, de a béke nem működik
  releváns harci tartalomban.
- DARK sok veszélyt kerülhet el, de minden hétköznapi gyógyítása gyengébb, és a civil
  gazdaság jelentős része kizárja.

## DARK: erős előny, valódi hátulütő

### Gyógyítás

- Minden DARK-tag normál gyógyítása a kiszámolt érték 70%-a.
- A szabály minden `EntityRegainHealthEvent` forrásra ugyanúgy alkalmazódik, ezért
  nincs forrásonkénti, nehezen követhető kivétellista.
- Vérhold alatt és `DUNGEON` területen a szorzó 100%. Ezekben a magas tétű
  helyzetekben a hátrány nem teheti használhatatlanná a frakciót.
- A szorzó fix játékszabály, nem külön HUD-mérő.

### Társadalmi kizárás

- DARK-tag nem nyithat és nem használhat polgári NPC-boltot.
- Egyetlen kivétel a `factions.dark.blackmarket-npc` alatt megadott feketepiac.
- DARK-tag nem indíthat játékos-karavánt a közös kasszából.
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
   megerősítési ablakon belül megismétli. Csak ekkor lesz DARK-tag.

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

Kompatibilitási szabály: a régi `isSinner` jelentése kizárólag `Infamy > 0`. Így a
régi kaszt-, tárgy- és küldetéskapuk nem örök, láthatatlan jelzőből dolgoznak.

### Tisztítás és vezeklés

- A normál bűntisztítás az Infamyt és a hozzá tartozó Wanted állapotot törli.
- Az Exile és az Oath ettől külön megmarad.
- A teljes vezeklés kifejezetten az Infamy, Wanted, Exile és Oath tengelyeket zárja le.
- Egy új bűnsorozat csak akkor növeli a generációszámot, amikor az Infamy nulláról
  pozitívra vált; az exact-once pénzügyi/bűn outboxok így továbbra is biztonságosak.

## Suttogó-rendszer

### Belépés

- Csak explicit, nem DARK frakciótag válhat Suttogóvá.
- Száműzött játékos nem kezdhet új Suttogó-rítust.
- A rítus éjjel, sculk vagy sculk catalyst blokkon, meghívóval és HP-áldozattal indul.
- A közeli tanú nem szakítja meg a rítust. Ehelyett pontos bizonyítékot kap a rítust
  végző játékos ellen.

### Pontos bizonyíték

A régi általános „tanú-token” helyett a bizonyíték két UUID-hoz kötött:

- ki látta az eseményt;
- kit látott.

A bizonyíték időkorlátos, egyszer használható, és másik játékos ellen nem váltható be.
A `/suttogas vád <játékos>` először pontos online célpontot old fel, majd csak a
tanú–cél párhoz tartozó bizonyítékot fogyasztja el. Hamis vagy rossz célpontú vád nem
mozgat állapotot.

Bizonyíték keletkezik:

- látott Sötét Rítusnál;
- látott frakcióárulásnál;
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

Sikeres kultista esemény minden aktív Suttogónál legfeljebb egy fokozatnyi fedezéket
ad (`SUSPECTED → OBSERVED`, `OBSERVED → CLEAN`). `EXPOSED` állapotból nem állítja
vissza a szerepet. A kultista loot akkor is jár, ha nincs eltávolítható fokozat.

### Lelepleződés

- A rejtett Suttogó-szerep az utolsó állapotírással együtt megszűnik.
- A játékos Exile állapotot kap, nem automatikus Infamyt, Oathot vagy DARK-tagságot.
- A szerverbroadcast konfigurálható marad.
- A DARK továbbra is hallhatja a Suttogó-csatornát, ha a meglévő kapcsoló engedélyezi.

## Eltávolított rendszerek

- periodikus frakcióadó és aktív adóütemező;
- `/faction king tax` parancs és adókulcs-megjelenítés;
- frakció-ételkötelezettség, honvágy-időzítő és periodikus Éhség-debuff;
- Suttogó gyanúpont, súlyozott gyanúforrások és automatikus decay;
- konfigurálható gyanúküszöb és leleplezési bűnpont;
- frakcióhoz kötött aktív képességek és új HUD-mérők.

A régi adósság/outbox és protokollmezők kompatibilitási okból a tartós formátumban
megmaradhatnak, de nincs őket meghajtó runtime scheduler, játékosparancs vagy HUD-kijelzés.
Migráció nem része ennek a változtatásnak.

## Parancsok

| Parancs | Eredmény |
| --- | --- |
| `/faction status` | megmutatja a tagságot, Infamyt, Wanted, Exile, Oath és Suttogó-fokozat állapotát; nem kerül HUD-ra |
| `/faction status eskü` | Exile után rögzíti a DARK esküt |
| `/faction join dark` | Exile + Oath után kétlépcsősen megerősíti a tényleges tagságot |
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

## Implementációs ellenőrzőlista

- [x] Crime state külön tengelyekre bontva.
- [x] DARK belépési folyamat szétválasztva.
- [x] `/faction status [eskü]` elkészítve.
- [x] Fix Suttogó-fokozatok és célhoz kötött evidence ledger elkészítve.
- [x] Suttogó pont/decay tick eltávolítva.
- [x] DARK gyógyítási és társadalmi hátrányok bekötve.
- [x] Adóütemező, királyi adóparancs és ételkötelezettség eltávolítva.
- [x] Konfiguráció, üzenetek és advancement-szövegek frissítve.
- [x] Célzott regressziós suite hozzáadva.
- [ ] Élő szerveres playtest és telemetria-alapú finomhangolás.
