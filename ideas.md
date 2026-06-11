# IceSMP – Ötlettár

Ötletek és tervek a fantasy "királyságos" SMP élményhez. A todo.md nyers jegyzeteire és a
már megépített rendszerekre (frakciók, kasztok, szakmák, relikviák, dinamikus gazdaság,
mob szintezés) épül.

## 1. Világ és fővárosok

- **Az Élet Fája (spawn):** a semleges frakció "fővárosa", egy óriási, kézzel épített
  világfa. Itt van a valutaváltó, a piactér és a szakma-oktatók. Harc tiltva (safe zone).
- **Két királyi főváros (Piros / Kék):** 2000–3000 blokkra a spawntól, ellentétes
  irányban. A mob szintezés miatt az út odáig önmagában is progresszió (~Lvl 2–3 mobok).
- **A Sötét romváros:** az összeomlott királyság. Messzebb (5000+ blokk), magas szintű
  mobokkal körülvéve — ide csak felkészülve éri meg elindulni. Itt érhető el a
  Nekromanta kaszt-quest és a Sötét frakcióba lépés.
- **Távolság-gyűrűk:** a mob szintezésre építve 1000 blokkonként "zónák" saját
  loot-asztallal: ritkább anyagok csak külső gyűrűkben (pl. ancient debris spawn-bónusz),
  így a veszély és a jutalom együtt skálázódik.

## 2. Frakció-mechanikák

- **Királyok és választás:** frakciónként 1 király (összesen 2 + a Sötét "Főnekromanta").
  A királyt a frakció tagjai választják X naponta; a király hirdethet raidet, adót
  állíthat be, és hadi kasszát kezel.
- **Állampolgári kötelesség:** a Piros/Kék tagok heti "adót" fizetnek a frakció
  kasszájába (a dinamikus árfolyam miatt ez a valutakínálatot is szabályozza — money sink!).
  A semlegeseknek nincs kötelességük, de a frakció-bónuszaik is gyengébbek.
- **Bűn- és száműzetés-rendszer:** a meglévő sinner flag kiterjesztése számlálóvá.
  4–5 bűn (gyilkosság, lopás, árulás) után automatikus száműzetés a Sötét frakcióba.
  A Sötétből visszaút csak hosszú "vezeklés" quest-lánccal lehetséges.
- **Frakció-reputáció:** NPC kereskedők árai a frakciók közti viszonytól függnek;
  háborúban álló frakció boltjában drágább minden.

## 3. Raid és háború

- **Raid eventek:** csak király hirdetheti meg, 10v10, előre kijelölt arénában vagy a
  célpont főváros külső gyűrűjében. Raid alatt a sinner tag nem aktiválódik (engedélyezett PvP).
- **Hadizsákmány:** a vesztes frakció kasszájának egy százaléka + ideiglenes buff a
  győztesnek (pl. +10% szakma XP 2 napig).
- **Ostromgépek:** craftolható, drága "ostrom-itemek" (pl. robbantó ágyú), amelyek csak
  raid közben használhatók — money sink és kovács-tartalom egyben.

## 4. Relikviák és legendás tárgyak

- **4 frakció – 4 elytra relikvia:** frakciónként egy egyedi képességű elytra
  (todo.md ötlet). Pl.: Piros – Főnix-szárny (zuhanásnál tűzlökés, lávaimmunitás repülés
  közben); Kék – Zúzmara-szárny (víz fölött siklásbónusz, fagyaura); Semleges –
  Vándorszél (gyorsabb sikló, nyom nélküli repülés); Sötét – Csontszárny (éjjel
  erősebb, rövid wraith-forma).
- **Fegyver-relikviák PvP-ben gazdát cserélhetnek** (todo szabály): a gyilkos
  megszerezheti a megölt játékos kezében lévő fegyver-relikviát; a passzív relikviák
  védettek. A meglévő ownership-nyilvántartás (relics.yml) erre már alkalmas.
- **Rituálé-oltárok:** a relikviák nem craftolhatók, hanem több játékos közös
  rituáléjával idézhetők meg (idő + áldozati anyagok + adott helyszín, pl. a romváros
  oltára). A meglévő ritual timer erre építhető.

## 5. Kasztok és progresszió

- **Kaszt-questek:** a kaszt felvétele ne csak GUI-katt legyen: rövid bevezető quest
  (Íjász: lőtáblapálya teljesítése; Orgyilkos: észrevétlen lopakodás egy NPC mögé;
  Nekromanta: a romváros oltárán végzett sötét rituálé + DARK frakció tagság — ez utóbbi
  feltétel már implementálva van).
- **Specializációk max szinten:** Varázsló → Elementalista / Gyógyító;
  Harcos → Berserker / Védelmező; Íjász → Beast Master (idézhető farkas!) / Mesterlövész;
  Orgyilkos → Méregkeverő / Fantom; Nekromanta → Lélekidéző / Pestisúr.
- **Ultimate képességek:** kasztonként 1 nagy cooldownos (30–60 perc) látványos ulti,
  pl. Nekromanta: 3 csontváz-szolga idézése 60 mp-re.
- **Skill-fa GUI:** a meglévő JobGUI bővítése egy skill-fa nézettel, ahol a szint-alapú
  feloldások (classes.*.spell-unlocks) vizuálisan is láthatók.

## 6. Gazdaság (a dinamikus árfolyamra építve)

- **Money sinkek:** adó, raid-nevezési díj, rituálé-anyagok, NPC szolgáltatások —
  ezek nélkül a kínálat csak nő, és minden valuta elinflálódik.
- **Piaci tábla / aukciósház:** player-to-player kereskedés a saját frakcióvaluta
  kínálatát mozgatja, így a kereskedelmi súlypont tényleg eltolja az árfolyamot.
- **Árfolyam-kijelző tábla:** a fővárosokban hologram/tábla, ami a /currency rates
  adatait mutatja élőben; "tőzsdei" hangulat.
- **Heti gazdasági esemény:** véletlenszerű "kereslet-sokk" (pl. a szerver +20%
  base-value-t ad a Kék valutának 2 napra) — kereskedési lehetőség a figyelmes játékosoknak.
- **Sötét valuta = lélekkő:** a Sötét frakció valutája mobok lelkéből "desztillálható"
  (magas szintű moboktól drop) — így a külső gyűrűk grindje gazdaságilag is értelmes.

## 7. Események és világesemények

- **Első belépés "videó":** kliensoldali videó nem lehetséges vanilla kliensen, de
  jól emulálható: title-szekvenciák + kamera-utaztatás (spectator teleport útvonalon) +
  resource pack zene. Alternatíva: interaktív "prológus" ösvény a spawnon.
- **Világ-bossok:** gyűrűhatárokon időzítve megjelenő, mob-szintezett bossok; a kill
  frakció-szintű jutalmat ad (kassza + buff), így frakciók versenyeznek érte.
- **Vérhold-éjszaka:** ritka éjszaka, amikor minden mob +2 szintet kap és a Sötét
  frakció passzívjai felerősödnek; cserébe ilyenkor hullik a legtöbb lélekkő.
- **Szezonális liga:** 2–3 havonta pontverseny a frakciók közt (raid győzelmek, gazdasági
  mutatók, boss killek) — a győztes frakció kap egy szezonális kozmetikai relikviát.

## 8. Technikai ötletek (következő lépések)

- Frakció-kassza és adórendszer (`FactionTreasuryManager`, a CurrencyManager mintájára).
- Quest-keretrendszer (config-vezérelt lépések, PDC-ben tárolt haladás) — a kaszt- és
  vezeklés-questek alapja.
- Hologram-API integráció az árfolyam-táblákhoz (vagy TextDisplay entity, az 1.19.4+
  display entity-k Folián is jól működnek).
- A 4 elytra relikvia a meglévő relic framework triggereivel (RelicTrigger bővítés:
  GLIDE_START, TAKE_DAMAGE) implementálható.
- Boss-évényekhez a MobScalingManager szint-API-ja (getLevel) már ad alapot.
