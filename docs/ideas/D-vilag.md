# D) Világ, hangulat, közösség

[← Ötlettár-index](../IDEAS.md)

Jelölés: **Munka** (🟢 kicsi / 🟡 közepes / 🔴 nagy) • **Érték** (⭐–⭐⭐⭐) — nincs elköteleződés, brainstorm-réteg.

### D1. Szezonális ünnepek
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Naptárhoz kötött vizuális/tartalmi skin a meglévő világeseményekre (október: tök-fejes invázió, december: ajándék-esemény).
**Hogyan működne:** Config dátumtartomány (`seasonal-events.halloween.start: "10-25"` / `.end: "11-02"`), éjféli globális tick ellenőrzi az aktuális naptári dátumot, és ha aktív egy ablak, felülírja futásidőben az Invázió/Vérhold/Kincs-esemény paramétereit (mob-skin CMD-vel, vérhold broadcast-szöveg „rém-éj”-re, decemberi ajándék-láda loot-tábla) — ugyanaz az `AmbientEventManager`/`InvasionManager` hívási lánc, csak paraméterezve, nem új rendszer.
**Miért jó:** A már ismert eseményeket (vérhold, invázió) új köntösben adja vissza — a valós naptárhoz igazodás azonnali „most van október!” él-a-világ érzetet kelt kis munkából.
**Építőkövek:** `AmbientEventManager`, `InvasionManager`, CMD-skin infra (A33).
**Buktatók:** Az időzóna-függő dátum-logika legyen konzisztens; a felülírás csak runtime-paraméter maradjon, ne írja át tartósan a configot.

### D2. Városi hirdetőtábla
**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** Player-generált apróhirdetés-tábla (kereslek/eladó/toborzás) egy GUI-táblán a fővárosban.
**Hogyan működne:** Fizikai tábla-blokk jobb-katt nyitja az `ExchangeBoardManager` mintájára épülő GUI-t (papír-csempék, lore = hirdetés-szöveg + feladó + lejárat); feladás kis díjért (`board.post-fee`, sink), `board.expire-days: 7` után napi takarító-tick automatikusan törli. Tárolás YAML-listaként (`YamlStore`: id, owner, szöveg, timestamp). Admin bármit törölhet (`icesmp.admin.board`).
**Miért jó:** A dinamikus piac mellé organikus, nem-tranzakciós kereskedelmi/közösségi találkozóhelyet ad („keresek 5 embert raidhez” jellegű hirdetéseknek).
**Építőkövek:** `ExchangeBoardManager` GUI-minta, `YamlStore`, sink-elv (ELÉG-elszámolás).
**Buktatók:** Trágár/spam-szöveg ellen egyszerű szűrő vagy gyors admin-törlés kell.

### D3. Szezon-emlékművek
**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** Szezonzáráskor a győztes frakció fizikai nyoma a fővárosban (zászló/szobor + névtábla).
**Hogyan működne:** A szezonváltás-hookban (liga-lezárás) egy admin-előkészített koordinátán (`season-monument.location`) automatikusan cserélődik egy fej-blokk/banner a győztes frakció színére, mellé `TextDisplay`-hologram íródik a szezon számával és a `StatsManager` MVP-top 3 nevével. A korábbi emlékmű nem törlődik, a lista alul bővül (vagy a D10 vitrinbe kerül).
**Miért jó:** A dicsőség fizikai, maradandó nyoma a világban — a következő szezon kezdetén mindenki látja, mit kell legyőznie.
**Építőkövek:** `StatsManager` top-lekérdezés, `TextDisplay`-minta, szezon-lezárás hook.
**Buktatók:** Az admin-előkészítés (helyszín + build) kézi munka; kódból csak a tábla-csere/hologram-frissítés megy.

### D4. Hangulat-rétegek bővítése
**Munka:** 🟢 • **Érték:** ⭐

**Mi ez:** Az `AmbientEventManager` meglévő eseményeihez (aurora, köd, vándorlás) finomabb hang-réteg + két új mikro-esemény.
**Hogyan működne:** Minden ambient-eseményhez rövid, vanília hangokból komponált „hangkép” (pl. aurora alatt halk `AMBIENT_CAVE` + ametiszt-csengés) régió-lokális `playSound`-dal a trigger pillanatában. Két új `AmbientEventType`: `METEOR_SHOWER` (több rövid hullócsillag-streak egymás után) és `FIREFLY_SWARM` (szentjánosbogár-raj mocsárban éjjel, alacsony sebességű `END_ROD` particle) — konfigkapcsoló a meglévő `ambient-events.types.<kulcs>` mintán.
**Miért jó:** A világ folyamatosan apró meglepetéseket tartogat kill/quest-tempó nélkül is — az AFK-sétát is élménnyé teszi.
**Építőkövek:** `AmbientEventManager` enum + `configKey` minta, régió-lokális `playSound`/`spawnParticle`.
**Buktatók:** Az `interval-minutes`/`chance-percent` súlyozás vonatkozzon az újakra is, különben zajossá válik.

### D5. Kocsma (social hub) ital-buffokkal
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** A fővárosi kocsmában fogyasztható, szakma-készítette italok rövid, nem harci buffokat adnak.
**Hogyan működne:** A Szakács/Sörfőző (E4-hez kapcsolódva) recept-katalógusból italt craftol (PDC-tag `icesmp_drink=<id>`), amit jobb-katt fogyasztva a listener egy configolt `PotionEffect`-listát ad (`drinks.<id>.effects`, pl. Luck + szakma-XP-szorzó, `drinks.<id>.duration-seconds`) — alapból csak a kocsma territórium-zónában (`drinks.require-tavern-zone`). PDC-timestamp cooldown a spam-fogyasztás ellen.
**Miért jó:** Fizikai találkozóhelyet ad a fővárosban, a szakma-lánc (alapanyag → recept → fogyasztás) sink+kereslet, harc nélkül is okot ad a bejövetelre.
**Építőkövek:** Recept-katalógus, `ItemFactory`-PDC minta, territórium-zóna lekérdezés.
**Buktatók:** A buff ne legyen erős harci előny, különben kötelező „kocsma-farm” rutin lesz minden raid előtt.

### D6. Frakció-fanfárok és kürtjelek
**Munka:** 🟢 • **Érték:** ⭐

**Mi ez:** Nagy pillanatokhoz (raid-indítás, koronázás, szezongyőzelem) frakció-specifikus hangjel vanília hangokból.
**Hogyan működne:** A meglévő broadcast-hookok (raid-indítás, király-választás, szezonzárás) mellé egy `playFanfare(FactionType, FanfareType)` segédmetódus néhány hangot időzítve lejátszik minden érintett online játékosnak — Folián minden hop az adott entitás saját scheduler-én (`player.getScheduler().run(...)`). Opcionális goat horn item a frakció-boltban, amit bárki elfújhat.
**Miért jó:** Néhány másodperces hangzási aláírás minden nagy pillanathoz zsigeri „ez most fontos!” jelzés — olcsó munka, erős hangulati hozam.
**Építőkövek:** Meglévő broadcast-hookok, `ItemFactory`-minta.
**Buktatók:** Minden entity-hop a saját régió-szálon fusson (Folia); ne legyen túl hosszú/ismétlődésnél idegesítő.

### D7. Napszak-üdvözlő NPC-k
**Munka:** 🟢 • **Érték:** ⭐

**Mi ez:** A fővárosi NPC-k (hírnök, kereskedők) napszak-függő odaszólásai a közelben elhaladóknak.
**Hogyan működne:** Percenkénti régió-lokális tick az NPC koordinátája körüli sugárban (`npc-greetings.radius`) lévő játékosoknak — ha az adott NPC cooldownja lejárt (memória-map NPC-id → timestamp) — action bar/chat üzenetet küld napszak-függő szövegkészletből (`messages.yml`), a FancyNpcsQuestBridge-en át azonosítva az NPC-t. Vérhold közeledtekor külön figyelmeztető sor.
**Miért jó:** A statikus NPC-k élőnek hatnak anélkül, hogy bármi mechanikai súlyuk lenne — a város „lélegzik”.
**Építőkövek:** `FancyNpcsQuestBridge`, `MessageManager`, `AmbientEventManager` napszak/vérhold állapot.
**Buktatók:** Cooldown nélkül chat-spam lenne — per-NPC throttle kötelező.

### D8. Felfedezhető titkos helyek
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Admin-kijelölt rejtett pontok, amiket először megtaláló játékos felfedezés-jutalmat kap.
**Hogyan működne:** Config-listában koordináta + sugár + jutalom (`hidden-spots.<id>.location/radius/reward`); alacsony gyakoriságú, throttle-olt proximity-check (player move eventen vagy periodikus, kis-sugarú scan a territórium chunk-index mintájára) észleli az első belépőt, PDC/YAML `discovered-by` zárja le (`hidden-spots.first-finder-only` configolható). Jutalom: token/XP + bestiárium-bejegyzés (B21), ritkán titkos kereskedő nyit (B40).
**Miért jó:** A kézzel épített world-building végre játékmechanikai súlyt kap — a felfedezés önálló játékstílussá válik.
**Építőkövek:** Territórium chunk-index minta, B21 bestiárium-infra, B40 titkos kereskedő.
**Buktatók:** A proximity-check ne fusson minden tick minden játékosra — throttle és kis sugár kötelező a teljesítmény miatt.

### D9. Énekmondó NPC (generált balladák)

→ **Átkerült a lore-kiemelt válogatásba:** [L-lore-kiemelt.md](L-lore-kiemelt.md)

### D10. Szezon-ereklye vitrin
**Munka:** 🟢 • **Érték:** ⭐

**Mi ez:** A szezon-emlékmű (D3) mellett az előző szezonok győztes relikviái/trófeái kiállítva.
**Hogyan működne:** Minden szezonzáráskor egy admin-előkészített vitrin-keretbe (`ItemDisplay` entitás) automatikusan bekerül a szezon reprezentatív trófea-itemje (pl. „Szezon N emlék-érem”) az `ItemDisplay#setItemStack` hívással a szezonzárás hookjából, mellette tábla a szezonszámmal és győztes frakcióval. A vitrin-slot koordináták configban (`season-showcase.slots`), a feltöltött szezonok YAML-listája véd az ismétlődés ellen.
**Miért jó:** A szerver „múzeuma” — minél régebb óta fut a világ, annál látványosabb a folyosó; history sense, ami hónapokra/évekre köti a közösséget.
**Építőkövek:** D3 emlékmű-hook, `ItemDisplay` entitás, `YamlStore`.
**Buktatók:** Véges vitrin-slotok — kezelni kell, mi történik, ha elfogynak (rotáció vagy admin-bővítés).

### D11. Járőröző városi őrség
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** FancyNpcs-alapú őr-NPC-k, amik előre megadott útvonalon körbejárnak a fővárosban.
**Hogyan működne:** Config-listában waypoint-koordináták NPC-nként (`city-guards.<id>.route`); periodikus (pl. 2 mp-enkénti) léptetés a következő ponthoz mindig az adott NPC-entitás SAJÁT régió-szálán (`entity.getScheduler().run(...)`), mert az útvonal több chunk-régiót is átszelhet. Éjjel/vérhold alatt sűrűbb léptetés + fáklya-tartás, nappal lassabb „őrjárat” tempó; közeli PvP-riasztás/raid esetén rövid figyelmeztető sor (D7 infra kiterjesztése).
**Miért jó:** A fővárosban nem csak kereskedő- és quest-NPC-k állnak — az őrjárat mozgása azt sugallja, hogy a város él és véd, erősítve a frakciós hovatartozás-érzést.
**Építőkövek:** `FancyNpcsQuestBridge`, D7 napszak-üzenet infra, entity-scheduler minta.
**Buktatók:** A waypoint-lépés MINDIG az NPC saját régió-szálán fusson; sok NPC * sűrű tick teljesítmény-költség, throttle kell.

### D12. Piaci zsivaj-hangok
**Munka:** 🟢 • **Érték:** ⭐

**Mi ez:** A fővárosi piac/tőzsde-zóna körül halk, folyamatos „nyüzsgés”-hangréteg.
**Hogyan működne:** A CAPITAL/PROTECTED_CITY zóna piaci al-területén (`market-ambience.location/radius`) 20-30 mp-enkénti régió-lokális tick a zónában lévő játékosoknak lejátszik egy halkított hang-kollázst (villager-morajlás, láda-nyitás, léptek), csak ha `market-ambience.min-players-nearby` teljesül (üres piacon csend). Éjjel automatikusan elhalkul (`market-ambience.night-silent: true`).
**Miért jó:** A piac-GUI mögötti fizikai hely is éljen — belépve azonnal érezhető, hogy „itt kereskednek”, mechanikai hatás nélkül.
**Építőkövek:** `TerritoryManager` zóna-lekérdezés, `AmbientEventManager` régió-lokális `playSound` minta.
**Buktatók:** Halkabb maradjon a valódi jelzés-hangoknál; a tick csak akkor fusson, ha tényleg van játékos a zónában.

### D13. Harangtorony órajelzés
**Munka:** 🟢 • **Érték:** ⭐

**Mi ez:** A fővárosi harangtorony minden napszakváltásnál (reggel/dél/este/éjfél) haranghangot ad.
**Hogyan működne:** Alacsony gyakoriságú globális tick figyeli a világ `getTime()` értékét; a négy konfigurált küszöbnél (`bell-tower.times: [0, 6000, 12000, 18000]`) egyszer triggerel `BLOCK_BELL_USE` hangot + rövid action-bar jelzést (`"🔔 Dél van"`) a torony koordinátája körüli sugárban lévőknek, régió-lokálisan (`getRegionScheduler().run(plugin, location, ...)`). World-onkénti memória-map véd az ismétlődő triggertől.
**Miért jó:** Apró, állandó időérzék-jelző — a napszak-függő eseményekkel (vérhold, D7) összekötve konzisztens „élő óra” hatást kelt.
**Építőkövek:** `getGlobalRegionScheduler()`/`getRegionScheduler()` régió-lokális hívás, D7 napszak-infra.
**Buktatók:** Több világnál külön kell számolni; a küszöb-ellenőrzés percenkénti gyakoriság elég, nem kell minden tick.

### D14. Közös vacsora-buff a kocsmában
**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** A D5 kocsma-mechanika közösségi kiterjesztése — ha többen egyszerre esznek együtt, mindenki bónuszt kap.
**Hogyan működne:** A kocsma zónában, ha egy rövid ablakban (`communal-dinner.window-seconds`, pl. 30 mp) legalább `communal-dinner.min-players` játékos fogyaszt kocsma-ételt/italt (D5 PDC-tag alapján), egy broadcast + minden résztvevőnek a szóló-fogyasztásnál erősebb/hosszabb buff jár. Számlálás rövid életű, régió-lokális memória-listával (nem perzisztens), ablak lejártával nullázódik.
**Miért jó:** Szándékosan közös étkezésre ösztönöz — a kocsma nemcsak fogyasztóhely, hanem társasági esemény lesz.
**Építőkövek:** D5 kocsma-item infra, territórium-zóna lekérdezés, broadcast-minta.
**Buktatók:** A számláló csak egy régió-szálon (egy zóna) él, nincs cross-thread szinkron-igény, de érdemes ellenőrizni nagy zónáknál.

### D15. Tábortűz-mesélés XP-vel

→ **Átkerült a lore-kiemelt válogatásba:** [L-lore-kiemelt.md](L-lore-kiemelt.md)

### D16. Csoportkép-pont
**Munka:** 🟢 • **Érték:** ⭐

**Mi ez:** A fővárosban kijelölt „emlék-pont”, ahol egy csapat összeállva ünnepi jelenetet és bejegyzést kap.
**Hogyan működne:** Admin-kijelölt koordináta+sugár (`photo-spot.location/radius`); ha `photo-spot.min-players` játékos egyszerre a sugáron belül van és egyikük jobb-katt a jelző-blokkra, rövid tűzijáték + hang indul, és a jelenlévők neve + timestamp bekerül egy megosztott „emlékkönyvbe” (a D21 vendégkönyv-tárolással közös YAML-lista). Nincs harci/gazdasági hatás, tisztán szimbolikus.
**Miért jó:** Baráti társaságoknak/céheknek (B35) kézzelfogható, visszatérő „voltunk itt együtt” pillanatot ad.
**Építőkövek:** D21 vendégkönyv-tárolás, tűzijáték-spawn minta (szezongyőzelem ünneplés).
**Buktatók:** `min-players` nélkül soló-spam lenne — a közösségi jelleg a lényeg, egyfős trigger tiltandó.

### D17. Szezon-átvezető broadcast-történetek

→ **Átkerült a lore-kiemelt válogatásba:** [L-lore-kiemelt.md](L-lore-kiemelt.md)

### D18. A 4 frakció lore-jának ingame felszíne

→ **Átkerült a lore-kiemelt válogatásba:** [L-lore-kiemelt.md](L-lore-kiemelt.md)

### D19. Rejtélyes idegen NPC ritka felbukkanása

→ **Átkerült a lore-kiemelt válogatásba:** [L-lore-kiemelt.md](L-lore-kiemelt.md)

### D20. Szobor-oszlop saját statokkal
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** A fővárosban folyamatosan bővülő „hírességek oszlopsora” — nem csak szezon-győztesek (D3), egyedi mérföldkövet elérő játékosok is felkerülnek.
**Hogyan működne:** Mérföldkő elérésekor (pl. első 100 boss-kill, teljes bestiárium — B21-hez kapcsolódva) a rendszer a következő szabad slotra (config-lista koordinátákkal, `hall-of-fame.slots`) fej-blokkot (`player.getPlayerProfile()`-texturált skull) + `TextDisplay`-táblát helyez a névvel és a mérföldkővel. Feltöltött slotok YAML-ban, hogy restart ne duplikáljon.
**Miért jó:** A D3-mal ellentétben nem csak győztes frakciónak ad emléket — sokkal több játékosnak ad esélyt fizikai nyomot hagyni.
**Építőkövek:** D3 emlékmű-hook mintája, B21 bestiárium mérföldkövek, `TextDisplay`-minta, `YamlStore`.
**Buktatók:** Véges slot-szám — admin-bővítés vagy „legutóbbi 20” rotációs logika kell.

### D21. Vendégkönyv a fővárosban
**Munka:** 🟢 • **Érték:** ⭐

**Mi ez:** Egy fizikai könyv-állvány a fővárosban, ahol bárki rövid üzenetet hagyhat a szerver történetéhez.
**Hogyan működne:** Jobb-katt a vendégkönyv-blokkon egy anvil-input/chat-prompt GUI nyílik (quest-builder minta), a beírt szöveg (karakterlimittel) + játékosnév + timestamp bekerül egy YAML-listába (`YamlStore`); megnyitáskor az utolsó N bejegyzés lapozható `BookMeta`-könyvként jelenik meg. Napi/heti beírási limit per player (PDC) a spam ellen.
**Miért jó:** Közvetlen, admin-közvetítés nélküli játékos-hang — apró, spontán történetek gyűlnek, amiket újak és régiek is olvasgatnak.
**Építőkövek:** Quest-builder chat-input minta, `YamlStore`, `BookMeta`-oldalazás.
**Buktatók:** Trágár/sértő tartalom ellen szűrő vagy admin-törlés kell; limit nélkül egy játékos monológjává silányulna.

### D22. Közösségi híd/út-építő projekt
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Admin által kijelölt, hiányos útszakasz/híd, amit a játékosok közösen, blokk-adományozással fejeznek be — nyomhagyás céllal, nem fogyasztási jutalomért.
**Hogyan működne:** Kijelölt terület (territórium PLOT-mintára) + célszám (pl. „adj le 2000 kőblokkot”) a `CommunityGoalManager.contribute()` metódusán, új objectiveType-tal; boss-bar mutatja a haladást szerver-szinten. Célszám elérésekor a híd/út admin által előre megépített, addig barrier-blokkos/láthatatlan szakasza „megnyílik” (barrier-eltávolítás vagy schematic-lerakás egy parancsból), tábla örökíti meg a top-N hozzájáruló nevét, kis XP/token jár a résztvevőknek.
**Miért jó:** A közösségi cél LÁTHATÓ, TARTÓS változást hoz a világtérképen — a „mi építettük” büszkeség erősebb kohéziót ad, mint bármilyen buff.
**Építőkövek:** `CommunityGoalManager` contribute-infra, boss-bar minta (kollektív szerver-kihívás), territórium PLOT (B32 mintájára).
**Buktatók:** Az admin-oldali „megnyitás” projektenkénti kézi build-előkészítést igényel, nem generikus.

### D23. Per-bióm ambient hangprofilok
**Munka:** 🟢 • **Érték:** ⭐

**Mi ez:** Az `AmbientEventManager` bővítése, hogy a periodikus hangulat-hang a játékos aktuális biómjához illeszkedjen.
**Hogyan működne:** A meglévő ambient-tick lekérdezi a triggerelt játékos `Location#getBlock().getBiome()` értékét, és egy config-map (`ambient-events.biome-profiles.<kategória>: [hang-lista]`) alapján néhány bióm-CSOPORT (sivatag, barlang/mélység, erdő, óceán, hó) hangkészletéből választ — nincs profilnál a jelenlegi generikus hang fallback marad. Régió-lokális, a meglévő trigger-hívási helyre épül, nincs extra tick.
**Miért jó:** A világ nem homogén „ambient zajt” sugároz mindenhol — a bióm-specifikus hang aláhúzza, hol jársz éppen, gyakorlatilag nulla extra teljesítmény-költséggel.
**Építőkövek:** `AmbientEventManager` meglévő trigger-hívás, `ConfigManager` map-olvasás.
**Buktatók:** A bióm-kategorizálás (60+ vanília bióm → néhány csoport) egyszeri, de aprólékos config-munka; hiányzó profilnál mindig legyen fallback.

### D24. Éjszakai fáklya-fény esemény
**Munka:** 🟢 • **Érték:** ⭐

**Mi ez:** Napnyugtakor a fővárosi fal menti fáklyák/lámpák köré rövid, látványos fény-részecske „gyújtás” söpör végig.
**Hogyan működne:** A D13 napszak-detektálásra épülve, egy admin-gyűjtött koordináta-lista (`torch-lighting.points`, a D8 titkos hely-lista mintájára) mentén sorban, kis késleltetéssel pontonként (söprő hatás), `FLAME`/`END_ROD` particle-csík + halk `ITEM_FLINTANDSTEEL_USE` hang jelenik meg a közeli játékosoknak. Nincs blokk-változás — tisztán vizuális réteg.
**Miért jó:** Minden este ismétlődő, finom „a város felkészül az éjszakára” rituálé, ami a vérhold-fenyegetés kontrasztját is erősíti.
**Építőkövek:** D13 napszak-detektálás, D8 admin-koordináta-lista minta, régió-lokális particle/hang-hívás.
**Buktatók:** Sok pont * sok játékos esetén a késleltetett sorozat maradjon régió-lokális; a pontlista kézi admin-munka.

### D25. Üzenetpalack / kívánságfal
**Munka:** 🟡 • **Érték:** ⭐

**Mi ez:** Egy kijelölt parti/tavi ponton eldobható „üzenetpalack” item, amiben a játékos rövid üzenetet hagy — amit valaki más talál meg később.
**Hogyan működne:** Egy craftolható item PDC-be írja az üzenetet (`items/*ItemFactory` minta); vízbe dobáskor a palack egy YAML-várólistába kerül véletlen `bottle.min-hours`/`max-hours` késleltetéssel, majd egy MÁSIK random online játékos HUD-ján/action barján kézbesítési jelzést kap, és a palack az inventoryjába kerül. Egyszerre csak `bottle.max-active` aktív palack a spam ellen.
**Miért jó:** Névtelen, aszinkron kapcsolatot teremt idegen játékosok között — kis meglepetés-élmény, ami sok-száz-fős SMP-ken oldja az elszigeteltség-érzést.
**Építőkövek:** `ItemFactory` PDC-minta, `YamlStore` várólista-tárolás, HUD/action-bar jelzés-minta.
**Buktatók:** Trágár tartalom szűrése itt is kell; a kézbesítés-tick alacsony gyakoriságú, globális legyen, ne fusson feleslegesen sűrűn.

### D26. Időkapszula-esemény
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Szezononként egyszer beásható „időkapszula” láda, ami csak a következő szezon zárásakor nyílik ki újra.
**Hogyan működne:** `/timecapsule bury <item>` egy admin-kijelölt kapszula-ponton (vagy saját claimen belül, configolható) YAML-bejegyzésbe rejt egy tárgyat (owner, item, bury-timestamp, unlock-szezonszám = aktuális + 1). A következő szezonzárás-hook (D17-tel közös trigger-pont) végigmegy a lejárt bejegyzéseken, és a tulajdonos következő belépésekor „időkapszula kinyílt” üzenettel + a tárggyal és egy opcionális „üzenet a jövő magamnak” szöveggel adja vissza.
**Miért jó:** Hosszú távú, több hetes/hónapos elköteleződést épít („mit rejtsek el, mire emlékezzek majd?”) — a szezon-ciklust személyessé teszi, nem csak frakciós versennyé.
**Építőkövek:** Szezonszám-nyilvántartás, `YamlStore`, szezonzárás-hook (D17).
**Buktatók:** Kezelni kell az offline/kilépett tulajdonos esetét — posta-rendszer híján egyszerű „várólista, amíg be nem lép” megoldás kell.
