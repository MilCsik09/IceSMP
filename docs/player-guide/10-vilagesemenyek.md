# 10. Világesemények ✅

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

## Mob-szintezés (a világ nehézsége) 🧟

A világ **kifelé egyre veszélyesebb**:
- A spawntól távolodva a szörnyek **erősödnek**: **minden 1000 blokk = +1 mob-szint** (max 10).
- A magasabb szintű szörny **több életű és erősebb**, de cserébe **több kaszt-XP-t** és nagyobb
  eséllyel **lélekkövet (Csontveret)** ad.
- A **spawner-ből / parancsból** érkező szörnyek **nem** skálázódnak — így a farmok
  biztonságosak maradnak.
- A szint-névtábla (`[Lvl X]`) alapból **csak akkor látszik, ha ránézel** a szörnyre — így nem
  zsúfolja tele a képernyőt falakon át vagy messziről.

## Vérhold-éjszaka 🌕

Ritkán beköszönt egy **vérhold**:
- A szörnyek **+pár szintet** kapnak (még erősebbek).
- A **lélekkő-drop esélye megnő** — kockázatos, de jövedelmező éjszaka.
- Egy üzenet jelzi mindenkinek, amikor kezdődik.

## Világboss 👹

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

## Invázió ⚔️
Időnként egy **szörnyhorda** tör be egy játékos köré — ragyogó, **megerősített (skálázott)**
szörnyek hulláma. A horda-összetétel **véletlen** (pl. Élőhalott Áradat, Csontlégió, Pókfészek,
Káosz-horda), és minden hullámot egy **megnevezett bajnok (mini-boss)** vezet, amely szintén
telegrafált földcsapással támad. Veszélyes, de a legyőzésük **több kaszt-XP-t** és nagyobb
**lélekkő-esélyt** ad, mint a hétköznapi szörnyek. (Admin: `/events invasion`.)

## Kereskedő-karaván ✦

Időnként egy **vándorkereskedő karaván** érkezik (egy üzenet jelzi, melyik világba és mennyi
ideig marad). Amíg itt van, **jobb-katt a karaván-NPC-re** → bolt nyílik **ritka portékákkal**
(arany alma, gyémánttömb, névcímke, tapasztalat-palack…), fix áron a banki egyenlegedből.
A kifizetett pénz eltűnik (money sink). Ha lekésed, legközelebb **máshol** bukkan fel.
Állapot: `/events caravan`. Részletek: [Valuta és gazdaság](03-valuta-gazdasag.md).

## Vad Hajsza 🐺

Időnként egy **megnevezett, feldühödött elit fenevad** (pl. Ősi Fenevad, Csont Vadász, Vén
Mágus, Pokoli Behemót) kóborol be egy játékos közelébe — kóborló mini-fenyegetés az
invázió-hordák és a világbossok között. Aki **leteríti**, **ritka zsákmányt** kap (nyersanyag,
nem pénz); ha időben senki nem öli meg, eltűnik a vadonban.

## Elrejtett kincs 🗺

Időnként egy **megjelölt kincsesláda** bukkan fel valahol, és az üzenet megadja a
**hozzávetőleges helyét** (világ + koordináta). Az **első**, aki odaér és **rákattint** (vagy
kibányássza), viszi a teljes zsákmányt (nyersanyag, nem pénz), majd a láda eltűnik. Ha senki
nem találja meg időben, feltáratlanul elenyészik — siess!

## Gyűjtögető buff-ablakok ⛏🎣

Időnként megnyílik egy **szerver-szintű bónusz-ablak** (kb. 15 percre) — csak nyersanyag/XP,
sosem pénz:
- **Bányász-láz** — az érc-blokkok bónusz dropot adnak.
- **Termés-óra** — a beérett termés bónusz hozamot ad.
- **Halászati láz** — esély dupla fogásra.
- **Tapasztalat-óra** — XP-szorzó mindenből.

Egy üzenet jelzi a kezdetét és a végét — ilyenkor érdemes rákapcsolni a megfelelő tevékenységre!

## Bőség-idő 🌱

A vérhold **pozitív ellenpárja**: egy nyugodt időablak, amikor a **termés gyorsabban nő**, az
**állatok néha ikret ellenek**, **kevesebb szörny** spawnol, és **gyengéd regeneráció** leng
mindenkin. Építeni, farmolni, feltöltődni való — a béke szigete a háború közepén.

## Kollektív szerver-kihívás ⚔

Időnként az **egész szerver** kap egy közös, időzített célt (pl. öljetek meg együtt szörnyeket /
bányásszatok ércet / takarítsatok be termést) — a cél **az online létszámhoz igazodik**
(fejenként kb. 40 szörny / 60 érc / 80 termés), így kevesen és sokan is teljesíthető. A haladást
**boss-bar** mutatja mindenkinek. Ha időben **együtt** teljesítitek, **minden online játékos** jutalmat kap
(XP + nyersanyag-csomag + rövid Sietség-buff). Közös cél, közös jutalom.

## Karaván-kíséret 🛡

Időnként egy **konvoj** (ládás láma) indul útnak egy cél felé, és útközben **szörny-hullámok**
támadják — a haladást **boss-bar** mutatja. A közelben lévő játékosoknak **életben kell
tartaniuk**, míg célba ér:
- Ha **odaér**: a zsákmány a célnál hullik, és a **kereskedő-karaván boltja egy ideig bővebb
  (ritka) készlettel árul**.
- Ha a konvoj **elesik** vagy lejár az idő, a szállítmány elvész.
- A kíséret-mobok robbanása **sosem rongálja a terepet**.

## Meteor-becsapódás ☄

Időnként egy **meteor** csapódik be a vadonba (az üzenet megadja a helyét), és egy kis
**kráter** marad, tele **ritka, kibányászható érccel** (gyémánt, smaragd, ősi törmelék…).
Siess: a kráter csak egy ideig marad, aztán **magától visszaáll az eredeti terep** — amit addig
kibányászol, a tiéd. A meteor **sosem csapódik claimelt területre vagy frakció-territóriumba**,
és **nem rombolja maradandóan** a világot.

## Kultisták 🕯

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

## Rontás-góc 🕸

Időnként egy **rontás-góc** nyílik a vadonban (broadcast megadja a helyét): közepén egy
**sculk-mag**, körülötte a zóna **éjszakánként terjed**, és **korrupt, világító fajzatokat**
szül. A rontás **leggyakrabban a Kitaszítottak földjének pereméről szivárog ki** — a DARK
territóriumok környékén számíts rá leginkább! **Tisztítás:** ölj le elég korrupt fajzatot,
majd a magot **SHIFT + jobb kattintással** törd meg → loot, rövid regeneráció, és „a Fa
fellélegzik”. Ha senki nem tisztítja, a zóna a plafonjáig nő, és onnan ontja a szörnyeket.

## Hangulat-események ✦

Időnként apró, **légköri események** teszik élőbbé a világot (nem befolyásolják a balanszot):
**északi fény** (rövid éjjellátás + magasan lebegő, sodródó fény-fátyol az égen), **hulló csillag** (üzenet a becsapódás
irányával), **köd**, **bolyongó szellemek**, **szentjánosbogarak**, valamint **állat-vándorlás**
(egy passzív állatcsorda vándorol a közeledbe — élelemforrás).

> 👥 **Csapatban vagy?** A plugin loot-eseményeinél (**Vad Hajsza**, **elrejtett kincs**) nem
> egy közös zsákmány esik le: minden közelben lévő párttag a **saját (personal) jutalmát**
> kapja. Részletek: [Party (csapat)](15-csapat.md).

## Szezonális liga 🏆

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

### Végítélet-hét — a szezon zárása 📖

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

## A világ hangjai 🕯

- **Korszakváltás:** szezonzáráskor a krónikások **rövid történetben** búcsúztatják a letűnt
  korszakot — az előző szezon hőseinek nevével. Figyeld a „Lapforduló" címet!
- **Az Énekmondó:** a fővárosi **bárd** minden héten **balladát** költ a szerver legnagyobbjairól
  (szint, vagyon, háborús hírnév). Kattints rá jobb gombbal, és hallgasd meg — hétfőnként új dal.
- **Tábortűz-mesélés:** ülj le egy **tábortűz** mellé (**sneak + jobb-katt**), és maradj ott pár
  másodpercet — a tűz mesél neked a régi időkről, és egy kevés tapasztalatot is ad. (Óránként
  egyszer; a körülötted ülők látják, hogy mesélsz.)
- **Az Idegen:** néha, valahol… feltűnik egy **csuklyás alak**. Akik közel állnak, hallanak tőle
  egy mondatot. Mire odaérsz — általában már nincs sehol. Hogy ki ő? A kódex hallgat róla.

## Titkos helyek 🧭

A világban **rejtett pontok** lapulnak (egy kilátó, egy barlangmélyi szentély…). Aki **elsőként**
talál rá egyre, **jutalmat kap, és a nevét az egész szerver megismeri** — „bekerül a térképekbe".
A későbbi felfedezők is kapnak egyszeri, kisebb jutalmat. Hogy hol vannak? Azt senki sem árulja
el — járd a világot nyitott szemmel!

## Bemutató (intro)

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
> tudják kézzel is kiváltani — lásd a [Parancsok](14-parancsok.md) oldalt.

---

➡️ Tovább: [Királyság, raid és háború](11-raid-haboru.md) • [Vissza a tartalomhoz](README.md)

> 💰 **Kultista-zsákmány:** a Királynő kósza hívei (portya, hírvivő, rítus-hívők) leölve
> eséllyel értéket dobnak — aranyat, árnyékport, emlékszilánkot, ritkán Suttogás-meghívót.
