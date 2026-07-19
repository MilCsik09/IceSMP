# L) Lore-kiemelt ötletek (válogatás)

A kanonikus kódex ([docs/LORE.md](../LORE.md)) alapján **kiválogatott, erősen lore-illeszkedő
tételek** az A/B/D/E/F/G/H/I/J kategóriákból — az eredeti azonosítók megtartva (a forrás-fájlban egysoros
mutató maradt a helyükön). Minden tétel elején a **lore-horgony**: melyik kódex-elem teszi
különösen indokolttá.

Jelölés: **Munka** (🟢 kicsi / 🟡 közepes / 🔴 nagy) • **Érték** (⭐–⭐⭐⭐).

---

## Tier S — a kódex szinte megköveteli
### H2. Terjedő korrupt zóna

> **Lore-horgony:** a Kárhozat Kapujából szivárgó rontás ↔ a gyógyuló Fa — a tisztítás lore-ból a Fát gyógyítja (kódex V./VII.)

🔴 • ⭐⭐⭐

**Mi ez:** Egy sötét energiával fertőzött terület, ami — ha hagyják — éjszakánként nő, és
egyre veszélyesebb, egyre erősebb mobokat szül.
**Hogyan működne:** Ritkán, a vadonban kijelölt magpontból induló zóna (kis sugár), ami
`corruption.spread-rate` config szerint tick-enként (éjjelente) terjed, amíg el nem éri a
`radius-cap`-et vagy meg nem tisztítják. A zónán belül a MobScalingManager egy „korrupt"
mob-készletet szór (erősebb, sötét-token esély feljebb), a megtisztításhoz a korrupt mobok
egy hányadát meg kell ölni ÉS egy központi „mag"-blokkot elpusztítani (TreasureEvent-szerű
egyszeri interakció). Sikeres tisztítás jutalma nyersanyag + rövid buff a közeli játékosoknak.
Folia: a terjedés csak a magpont régiójában fut (`getRegionScheduler`), chunk-határon átnyúló
terjedésnél szomszéd-régióba hop, nem globális szkennelés.
**Miért jó:** Passzív fenyegetés, ami cselekvésre kényszerít — ha senki nem törődik vele, a
világ ténylegesen rosszabbodik, ami erős „élő világ" érzetet ad és csapatmunkára ösztönöz.
**Építőkövek:** MobScalingManager mob-készlet felülbírálás, TreasureEvent-mintájú interakció,
WorldGuard-bridge (ne terjedjen védett zónába).
**Buktatók:** Kontroll nélkül a mob-entitásszám elszabadulhat egy régióban — kemény cap
(max egyidejű korrupt mob) és a takarítás-figyelő (H24) kötelező párja.
### B42. Régészet szakma-ág

> **Lore-horgony:** a Mélység Népe romjainak és az Emlékszilánkoknak a kiásása (kódex I./V.)

**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Gyanús homok/kavics blokkok ecsettel áshatók: cserép-szilánkok → relikvia-
szilánk/bestiárium-oldal/recept-lap.
**Hogyan működne:** A vanília 1.20 archeológia-mechanika (gyanús blokk + ecset) rátölthető
egy `ArcheologyManager`-rel: a blokk-spawn ritka, világ-szórt (a kincs-esemény
elhelyezés-mintáját követve), a kiásott cserép-szilánk PDC-taggel jelöli, mi állítható
össze belőle (relikvia-szilánk, bestiárium-oldal — B21, recept-lap).
**Miért jó:** A vanília rendszerre épít (kevesebb saját munka), és felfedező-játékstílust
szolgáló, csendes tartalmat ad, ami más rendszerekbe (bestiárium, relikvia) táplál be.
**Építőkövek:** Vanília archeológia API, kincs-esemény elhelyezés-minta, bestiárium (B21).
**Buktatók:** A vanília gyanús blokk loot-táblája felülírandó, hogy csak a szerver-saját
tartalom (nem vanília pottery sherd) essen belőle — gondos config-munka kell.
### B3. Kézzel épített dungeonök kulcs-itemmel

> **Lore-horgony:** törp-kazamaták és Káoszkor-romok — a Mélység Népe eltűnése kész dungeon-narratíva (kódex I.)

**Munka:** 🔴 • **Érték:** ⭐⭐⭐

**Mi ez:** Szerver-csapat által épített helyszínek, admin által kijelölve, kulcs-itemért
belépve, bent skálázott mobokkal + boss-szal, heti lockouttal.
**Hogyan működne:** `/territory` új `DUNGEON` típusa (nem claimelhető, belépés-ellenőrzéssel);
belépéskor a `ClaimManager`/`TerritoryProtectionListener` mintájára egy `DungeonManager`
ellenőrzi a kulcs-item PDC-tagjét (frakció-valutáért craftolható/vásárolható — sink) és a
`PersistentDataContainer`-ben tárolt heti lockout-bélyeget. Bent a `MobScaling`
nehézség-skálázás fixen a dungeon szintjére állítva (nem távolság-alapú), a boss a
világboss-archetípus infrát (fázisok, telegraph, SLAM/ZONE speciál) újrahasznosítja.
**Miért jó:** Kézzel tervezett, ismételhető PvE-tartalom heti ritmussal — a procedurálisan
generált világesemények mellett „igazi" dungeon-élményt ad.
**Építőkövek:** `TerritoryManager` zónatípus, `MobScaling`, világboss fázis/telegraph-infra,
frakció-valuta sink.
**Buktatók:** Nagy világépítési munka (nem plugin-kód); a heti lockout PDC-logikát csoportra
(party) kell számolni, nem csak egyénre, különben a csapatból egy tag zárva marad.
### B15. Heti krónika (auto-újság)

> **Lore-horgony:** a kódexet a Bankárszövetség krónikásai írják — a heti újság ugyanez a hang, élőben (kódex VIII.)

**Munka:** 🟢 • **Érték:** ⭐

**Mi ez:** Hét végén auto-generált könyv-item/chat-összefoglaló a hét eseményeiről.
**Hogyan működne:** Vasárnap éjfélkor egy globális scheduler tick összeszedi a
`StatsManager` heti top-adatait (raid-eredmény, szezon-állás, legnagyobb piaci üzlet,
körözöttek) egy sablon-szövegbe, ami `written_book` itemként kerül a fővárosi hirdetőtáblára
(D2) és broadcast chat-üzenetként is kimegy.
**Miért jó:** A meglévő statokból szinte ingyen összerakható, és a szerver „élő világ"
érzését erősíti — a játékosok visszaolvashatják, mi történt, míg ők offline voltak.
**Építőkövek:** `StatsManager`, globális scheduler tick, `written_book` item, hirdetőtábla (D2).
**Buktatók:** A sablon-szöveg gépi íze könnyen szárazra sikerülhet — pár változatos
mondat-sablon kell a monotónia elkerülésére.
### D18. A 4 frakció lore-jának ingame felszíne

> **Lore-horgony:** a kódex ingame-esítése: lore-könyvek, NPC-szövegek, feliratok

**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** A frakciók (RED/BLUE/NEUTRAL/DARK) háttértörténete ne csak a guide-ban létezzen, hanem apró, world-based nyomokban is felbukkanjon.
**Hogyan működne:** Minden frakció fővárosában 2-3 admin-elhelyezett „lore-pont” (tábla-blokk, könyv-item egy polcon, vagy NPC dialógus-ág a FancyNpcs-hídon át) meséli a frakció eredetét/etikáját — a szöveg `messages.yml` dedikált `faction-lore.<faction>.<n>` kulcs-készletéből, amit az A31 frakció-infó oldal linkelhet. Kiegészítésként `/lore <frakció>` parancs ugyanezt chatbe írja azoknak, akik nem tudnak odautazni.
**Miért jó:** A frakció-választás (ma inkább mechanikai: passzívák + szín) érzelmi/narratív súlyt kap.
**Építőkövek:** A31 frakció-infó oldal, `MessageManager` kulcs-minta, `FancyNpcsQuestBridge` dialógus-ág.
**Buktatók:** A fő munka tartalom-írás, nem kód; a 4 frakció lore-mennyisége maradjon kiegyensúlyozott.

### I14. Szakma-címke az itemen — „Készítette: X"

> **Lore-horgony:** szó szerinti kánon-mondat: „a mester keze alól kikerülő mű a nevét is viseli" (kódex VIII. — A Tárgyak Lelke)

**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** Minden szakma-craftolt tárgy PDC-ben és a lore-ban megkapja a készítő nevét (és opcionálisan a craftolás idejét).
**Hogyan működne:** A `ProfessionRecipeManager` craft-végrehajtás végén egy `crafted_by`/`crafted_at` PDC-pár kerül az itemre, a lore-ba egy halvány szürke sor („Készítette: Anna"). A piacon (`MarketGUI`) ez a sor megmarad, tehát a hírneves craftolók neve „márkajelzésként" utazik a tárggyal.
**Miért jó:** Presztízs-mechanika pénz-semlegesen: a jó hírű szakma-mesterek tárgyai felismerhetők és keresettebbek lesznek a piacon — szociális dimenziót ad a craftolásnak, ami eddig névtelen volt.
**Építőkövek:** `ProfessionRecipeManager`, ItemFactory-minta (PDC+lore), `MarketManager` (megjelenítés).
**Buktatók:** A régi (címke nélküli) tárgyakkal való visszamenőleges kompatibilitás — null-biztos lore-építés, ne dobjon hibát a hiányzó PDC-mezőn.

## Tier A — erős illeszkedés
### B33. Szezonzáró világesemény („végítélet-hét")

> **Lore-horgony:** a Korszakok Könyve lapfordulása + a fél-álom Királynő nyugtalansága (kódex VII./VIII.)

**Munka:** 🟡 • **Érték:** ⭐⭐⭐

**Mi ez:** A szezon utolsó hetében eszkalálódó modifikátorok, az utolsó napon szerver-boss
a fővárosnál.
**Hogyan működne:** A szezon-scheduler utolsó 7 napjában egy `SeasonFinaleManager` a
meglévő esemény-managerek súlyait/gyakoriságát fokozatosan emeli (sűrűbb vérhold,
erősebb invázió, dupla liga-pont — config-táblázat naponkénti eszkalációval); az utolsó
napon egy egyszeri, globálisan broadcastolt szerver-boss spawn a fővárosnál (világboss-
archetípus infra, egyedi loot-tábla).
**Miért jó:** A szezonoknak drámai ívet ad — nem csak „lejár a liga", hanem érezhetően
felfut a világ a záráshoz, ami a résztvevők számára emlékezetes pillanatot teremt.
**Építőkövek:** Szezon-scheduler, esemény-managerek súly-rendszere, világboss-archetípus infra.
**Buktatók:** Az eszkaláció ne váljon já(rha)tatlanná a gyengébb frakciónak — a boss-nehézség
és a modifikátorok ne büntessék túl azt, aki lemaradt a szezonban.
### J9. Fejezet-rendszer (szezononkénti story-ív)

> **Lore-horgony:** a korszakok = fejezetek — a szezon-story-ív kánonja készen áll (kódex VIII.)

🔴 • **Érték:** ⭐⭐⭐

**Mi ez:** A `next`-láncolást egy magasabb szintű "fejezet" fogalom fogja össze: minden
szezon egy story-ívet nyit (a `SeasonManager` szezonváltásához kötve), amiben csak az adott
szezon alatt elérhető quest-láncok futnak.
**Hogyan működne:** `chapter: "szezon3"` mező a questeken; a `QuestManager` elfogadás-
ellenőrzésébe egy `requires-chapter-active` szűrő kerül, ami a `SeasonManager.getCurrentSeasonId()`-
del veti össze. Be nem fejezett fejezet-questek szezonváltáskor archiválódnak (a lezárt
állapot PDC-ben marad, de új fejezet-questek nem nyílnak rájuk).
**Miért jó:** A szezon-rendszernek (király, raid, liga) most nincs NARRATÍV íve — ez adja
meg a "évad-történet" érzést, a heti krónikával (B15) és a szezonzáró eseménnyel (B33)
összefűzve.
**Építőkövek:** `SeasonManager`, `next`-láncolás, `QuestManager` requires-* keret.
**Buktatók:** Nagy tartalom-munka (írás), és a szezonváltáskor futó láncoknál explicit
kell dönteni: törlődik vagy átmenetileg befejezhető marad-e a félbehagyott quest.
### D17. Szezon-átvezető broadcast-történetek

> **Lore-horgony:** a korszak-átmenetek krónikás-hangú narrálása (kódex VIII.)

**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** Szezonváltáskor száraz statisztika helyett rövid, narratív hangvételű broadcast-sorozat vezeti fel az új szezont.
**Hogyan működne:** A szezonzárás hookjához 3-5 üzenetből álló, késleltetett (`runDelayed` a globális scheduleren) szöveg-sorozat kötődik — az előző szezon eseményeiből (StatsManager top, B15 krónika, győztes frakció) sablon-szöveggel generált „korszakváltás” narratíva (`messages.yml` season-intro sablonok), a bemutató (intro) cím-szekvencia vizuális stílusával (title/subtitle + chat).
**Miért jó:** A szezon-ciklus (ma csak pontszám-reset) valódi „fejezetváltásnak” érződik — történet-ívet ad egy visszaszámláló számnak.
**Építőkövek:** Szezonzárás-hook, B15 heti krónika adatforrás, meglévő intro cím-szekvencia.
**Buktatók:** Ne legyen blokkoló/hosszú (title-sorozat, nem kényszerített várakozás); több sablon-variáns kell az ismétlődés ellen.
### D19. Rejtélyes idegen NPC ritka felbukkanása

> **Lore-horgony:** kész K9-horgony: a Suttogók toborzója vagy az Első Csend hírnöke (kódex VII.)

**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** Egy név nélküli, kapucnis „Idegen” NPC, aki ritkán, véletlen helyen/időben feltűnik, mond egy talányos sort, majd eltűnik.
**Hogyan működne:** Alacsony esély (`stranger-npc.chance-percent`, az `AmbientEventManager` esély-mintájával) triggerkor egy FancyNpcs-NPC spawnol véletlen, spawntól távoli, játékos-közeli koordinátán (a világboss spawn-választás mintájára), rövid, több-variánsos, talányos sort mond, majd `despawn-seconds` múlva particle-lel eltűnik. Nincs mechanikai jutalom — se kereskedő (B40), se questadó, tisztán atmoszférikus.
**Miért jó:** A világ rejtélyt hordoz — a közösség „láttátok az Idegent?” beszélgetései admin-kontroll nélküli, szabad közösségi tartalmat generálnak.
**Építőkövek:** `AmbientEventManager` esély/trigger-minta, `FancyNpcsQuestBridge` NPC-spawn, világboss spawn-választás.
**Buktatók:** Mechanikai jutalom TILOS hozzá (elveszti a rejtély-jelleget); túl gyakori feltűnés lerontja a ritkaság-érzetet.
### D9. Énekmondó NPC (generált balladák)

> **Lore-horgony:** balladák az Elveszett Uralkodókról és a Vérháborúkról (kódex-függelékek)

**Munka:** 🟢 • **Érték:** ⭐

**Mi ez:** A fővárosi bárd hetente a top-játékosokról „énekel” a statisztikák alapján.
**Hogyan működne:** Heti tick (a szezon-scheduler mintájára) lekérdezi a `StatsManager` top-3 kategóriáját (K/D, mob-ölés, quest — A15/A22), és sablon-szöveg-készletből (`messages.yml`, `{player}`/`{stat}` placeholder) összeállít 2-3 sort, amit a bárd NPC dialógusból (jobb-katt) vagy periodikus chat-buborékból megjelenít. A heti krónikával (B15) közös adatforrás, külön prezentáció.
**Miért jó:** A száraz ranglista-számokból narratívát csinál — vicces, megosztható pillanat, gyakorlatilag ingyen alapanyagból.
**Építőkövek:** `StatsManager` top-lekérdezés, `FancyNpcsQuestBridge` dialógus, B15 heti krónika.
**Buktatók:** A sablon-szöveg ne ismétlődjön gépiesen — kategóriánként több variáns kell.
### D15. Tábortűz-mesélés XP-vel

> **Lore-horgony:** a kódex szájhagyomány-felülete — tábortűz melletti mesélés

**Munka:** 🟢 • **Érték:** ⭐

**Mi ez:** Tábortűz mellé ülve (sneak+jobb-katt) rövid, ismétlődő „mesélés” mikro-esemény kevés XP-vel.
**Hogyan működne:** A listener figyeli a tábortűz sneak+jobb-kattot; ha a játékos `campfire-story.hold-seconds` ideig `campfire-story.radius`-on belül marad (mozgás megszakítja), rövid partikel/hang-jelenet (`SOUL_FIRE_FLAME` + halk `AMBIENT_CAVE`) után kis XP-jutalom (`campfire-story.xp-reward`) és véletlen sztori-sor (config-lista, D18 frakció-lore-ból is meríthet). PDC cooldown (`cd_campfire_story`) az AFK-farm ellen.
**Miért jó:** A dekoratív tábortűz köré harc nélküli, közösségi ritmust ad (több játékos üljön egy tűz köré) — alacsony tétű „élj a világban” tartalom.
**Építőkövek:** `PlayerInteractListener` minta, PDC-cooldown minta (spell-cooldownok), D18 lore-szövegkészlet.
**Buktatók:** Cooldown/hold-seconds nélkül AFK-bot-farmolható XP-forrás lenne — alacsony összeg, hosszú cooldown kötelező.
### B26. Rúna-kovácsolás (enchant-kiegészítő szakma-ág)

> **Lore-horgony:** Caldestera elveszett rúna-tudása + a Mélység Népe, a világ első kovácsai (kódex I./VI.)

**Munka:** 🔴 • **Érték:** ⭐⭐⭐

**Mi ez:** Rúna-itemek craftolása ritka anyagokból, felhelyezés fegyverre/páncélra kis
tematikus bónuszokkal.
**Hogyan működne:** A rúna egy `ItemFactory`-mintás PDC-item (`RuneItemFactory`), a
recept-katalógusba a Kovács/Varázsló ág 25+ szintű recepteként kerül; a „felhelyezés"
egy anvil-szerű GUI-lépés, ami a célfegyver PDC-jébe ír egy `rune_effect` mezőt (a
spell-effekt logika ezt olvassa cast/találat idején kis, additív szorzóként).
**Miért jó:** A talent/mastery mellé egy harmadik, ITEM-oldali progressziós tengelyt ad —
loot-izgalmat a raritás-rendszer fölé, mert a rúna FÜGGETLEN a fegyver raritásától.
**Építőkövek:** `ItemFactory`-minta, recept-katalógus, spell-effekt hook-pontok.
**Buktatók:** A rúna-hatásokat spellenként kell integrálni (nagy felületű változtatás) —
érdemes egy szűk, közös hook-ponton (pl. `SpellCastContext` módosító lista) átvezetni,
ne szórtan minden spell-osztályban.
### B35. Céhek (frakción belüli kisközösségek)

> **Lore-horgony:** a VIII. fejezet Céh-öröksége — a Felsők újraélesztik a letűnt céheket

**Munka:** 🔴 • **Érték:** ⭐⭐⭐

**Mi ez:** 5-15 fős céh saját névvel, kasszával, céh-questekkel és céh-szintlépéssel — a
frakció (50+) és a party (5) közti köztes réteg.
**Hogyan működne:** `GuildManager` a `PartyManager` perzisztens, kibővített testvére:
`/guild create <név>` (frakción belül, tagok csak azonos frakcióból), saját `YamlStore`-
perzisztált kassza (bank-infra), céh-szintű közösségi cél-questek (a meglévő közösségi
cél-minta céh-scope-ra szűkítve), céh-szintlépés (tag-aktivitás összesítéséből XP).
**Miért jó:** A közösségi kohézió legerősebb eszköze — az 50+ fős frakció túl nagy az
igazi közösségérzethez, az 5 fős party túl kicsi a tartós szervezethez; a céh a kettő közt hidal.
**Építőkövek:** `PartyManager` minta, közösségi cél-infra, bank/kassza-infra, `YamlStore`.
**Buktatók:** Nagy munka (új manager + GUI + perzisztencia + rang-rendszer réteg) — érdemes
a party-infra közvetlen kiterjesztéseként indulni, ne nulláról.
### B54. Elátkozott felszerelés (kockázatos erő)

> **Lore-horgony:** az Ócska-átok és az Első Csend-érintette tárgyak motívuma (kódex I. + item-rarity)

**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** Ritka, erős tárgyak egy csoportja, amik felvétel után nem vehetők le szabadon
(egy rituálé/quest kell a „megtöréshez"), cserébe komoly bónuszt adnak.
**Hogyan működne:** A raritás-létra egy „Átkozott" al-kategóriája (a loot-tábla ritka
ágán, mob-loot vagy világboss-forrásból); az item PDC-je egy `cursed: true` flaget hordoz,
amit egy `PlayerArmorChangeEvent`/inventory-listener figyel — levételi kísérletnél
visszahelyezi, hacsak a játékos nincs a rituálé-oltárnál (a meglévő Feloldozás-szolgáltatás
mintájára egy „Átok-törés" opcióval).
**Miért jó:** Tematikus, önkéntes kockázat-vállalás — a nagy erő ára az elköteleződés,
ami izgalmas döntési helyzetet teremt anélkül, hogy PvP-t vagy gazdaságot érintene.
**Építőkövek:** Raritás/loot-rendszer, rituálé-oltár Feloldozás-szolgáltatás mintája,
inventory-listener.
**Buktatók:** A kényszerített viselet UX-szempontból frusztráló lehet, ha a játékos nem
érti előre a szabályt — egyértelmű lore-szöveg és felvétel előtti megerősítő GUI-prompt kötelező.

### A38. Első belépés spawn-élmény polish

> **Lore-horgony:** a Szent Zóna-ébredés + a zarándoklat élménye (kódex V.)

🟢 • ⭐⭐

**Mi ez:** Az onboarding-lánc (A5) mellé a tényleges spawn-pillanat vizuális/hangi csiszolása.
**Hogyan működne:** Join-kor rövid title/subtitle üdvözlés + halk hangjel (nem broadcast-
szintű), a semleges főváros látványos pontján spawnoltatva (`teleportAsync`); az üdvözlő
üzenet configolható (`messages.yml` → `join-welcome-title`).
**Miért jó:** Az MMO-szerverek első benyomása sokat számít — egy 2 másodperces title-élmény
olcsó, de emlékezetes belépő.
**Építőkövek:** join-listener, `teleportAsync`, `MessageManager`.
**Buktatók:** ne ütközzön az onboarding-quest üzenetével — az időzítést egyeztetni kell, hogy
ne torlódjon két üdvözlés egyszerre.
### B6. Játékos-indított karaván (szállítmány-rablás)

> **Lore-horgony:** a vándorkaraván kánonja — „védelmük a Felsők becsülete" — játékos-karavánra kiterjesztve (kódex VIII.)

**Munka:** 🔴 • **Érték:** ⭐⭐

**Mi ez:** A karaván-esemény player-verziója: frakció indít szállítmányt A→B kasszából
finanszírozva, célba érve bónusz, útközben rabolható.
**Hogyan működne:** `/faction caravan send <cél>` (király/megbízott jog) a meglévő karaván-
esemény entitás-mozgás logikáját (escort-konvoj útpontjai) indítja frakció-kasszából fizetett
áruval; más frakció tagjai a raid-jelentkezés mintájára „lesből" jelentkezhetnek egy
rablás-ablakra. Sikeres védés = célba ér + kassza-bónusz, sikeres rablás = a rakomány a
rabló frakció kasszájába megy.
**Miért jó:** Kockázat/jutalom gazdasági minijáték, ami PvP-tartalmat termel frakciók között
anélkül, hogy formális hadüzenet kellene hozzá.
**Építőkövek:** Karaván-esemény konvoj-mozgás, raid-jelentkezés infra, `FactionManager` kassza.
**Buktatók:** Az útvonal-számítás (pathfinding konvojnak) régió-lokálisan kis lépésekben
menjen (Folia blokk-szkennelés szabály), és a rablás-ablak ne legyen kihasználható
off-time időzítéssel (ld. B30 háború-ablak elve alkalmazható ide is).
### B19. Évszakos világ-modifikátorok

> **Lore-horgony:** az Égi Jelek + a gyógyuló Fa / a Királynő álmának ciklusai évszak-modifikátorként (kódex V./VII./VIII.)

**Munka:** 🟢 • **Érték:** ⭐

**Mi ez:** A szezonhoz kötött finom világ-hangolás: télen gyakoribb fagy-események, nyáron
Bőség-idő gyakoribb.
**Hogyan működne:** `config/season-modifiers.yml`: szezononként szorzó-táblázat az esemény-
managerek véletlen-súlyaira (a meglévő esemény-súly rendszer olvassa ki egy plusz
szezon-szorzót). Nincs új esemény, csak a meglévők gyakoriság-hangolása.
**Miért jó:** Olcsó „élő világ" réteg — a szezon nemcsak liga-pontban, hanem a világ
hangulatában is érezhető.
**Építőkövek:** Esemény-managerek súlyozott sorsolása, szezonliga-scheduler.
**Buktatók:** Csak finomhangolás, nem új tartalom — a hatás könnyen észrevehetetlen marad,
ha a szorzók túl kicsik.
### B21. Bestiárium / gyűjtő-album

> **Lore-horgony:** a krónikás-lajstrom műfaj (kódex-függelékek) bestiárium-formában

**Munka:** 🟡 • **Érték:** ⭐⭐⭐

**Mi ez:** Perzisztens album: megölt mob-típusok, elkészített receptek, felfedezett
territóriumok, legyőzött boss-archetípusok pipálódnak.
**Hogyan működne:** `/bestiary` GUI (statikus GUI-minta), a haladás `StatsManager`
bővítéseként PDC-számlálók (mob-típusonként első kill dátuma, recept-katalógus első
craft flag, territórium first-entry flag). Mérföldkő-jutalmak (10/50/100 faj) az
achievement-infrán, broadcast + krónika-bejegyzés (B15) nagy mérföldköveknél.
**Miért jó:** Gyűjtögető-hajlamú játékosnak hónapokra ad célt, és szinte az ÖSSZES
meglévő rendszert (mob, szakma, territórium, boss) egyetlen progressziós UI-ba fűzi össze.
**Építőkövek:** `StatsManager` PDC-számlálók, GUI-minta, achievement-infra, heti krónika (B15).
**Buktatók:** Sok apró hook-pontot igényel (minden mob-típus, minden recept, minden
territórium bejárása) — inkrementálisan, kategóriánként érdemes bevezetni.
### D3. Szezon-emlékművek

> **Lore-horgony:** a Korszakok Könyve fizikai emlékművei — „neve örökre bekerül" (kódex VIII.)

**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** Szezonzáráskor a győztes frakció fizikai nyoma a fővárosban (zászló/szobor + névtábla).
**Hogyan működne:** A szezonváltás-hookban (liga-lezárás) egy admin-előkészített koordinátán (`season-monument.location`) automatikusan cserélődik egy fej-blokk/banner a győztes frakció színére, mellé `TextDisplay`-hologram íródik a szezon számával és a `StatsManager` MVP-top 3 nevével. A korábbi emlékmű nem törlődik, a lista alul bővül (vagy a D10 vitrinbe kerül).
**Miért jó:** A dicsőség fizikai, maradandó nyoma a világban — a következő szezon kezdetén mindenki látja, mit kell legyőznie.
**Építőkövek:** `StatsManager` top-lekérdezés, `TextDisplay`-minta, szezon-lezárás hook.
**Buktatók:** Az admin-előkészítés (helyszín + build) kézi munka; kódból csak a tábla-csere/hologram-frissítés megy.
### D8. Felfedezhető titkos helyek

> **Lore-horgony:** a Mélység Népe romjai + a világ rejtett zugai (kódex I.)

**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Admin-kijelölt rejtett pontok, amiket először megtaláló játékos felfedezés-jutalmat kap.
**Hogyan működne:** Config-listában koordináta + sugár + jutalom (`hidden-spots.<id>.location/radius/reward`); alacsony gyakoriságú, throttle-olt proximity-check (player move eventen vagy periodikus, kis-sugarú scan a territórium chunk-index mintájára) észleli az első belépőt, PDC/YAML `discovered-by` zárja le (`hidden-spots.first-finder-only` configolható). Jutalom: token/XP + bestiárium-bejegyzés (B21), ritkán titkos kereskedő nyit (B40).
**Miért jó:** A kézzel épített world-building végre játékmechanikai súlyt kap — a felfedezés önálló játékstílussá válik.
**Építőkövek:** Territórium chunk-index minta, B21 bestiárium-infra, B40 titkos kereskedő.
**Buktatók:** A proximity-check ne fusson minden tick minden játékosra — throttle és kis sugár kötelező a teljesítmény miatt.
### E1. Nekromanta: lélek-kovácsolás

> **Lore-horgony:** a lélek-kánon: a holtidéző az élők lelkét aratja (kódex VII.)

**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** A lélekszilánk-gyűjtés (`SoulShardManager`) kapjon tartós, presztízs-jellegű befektetési célt a Nekromanta minion-seregéhez.

**Hogyan működne:** `/soulforge` GUI-ban 3 fejleszthető ág (Élet/Sebzés/Létszám), áganként 5 rang, rangonként növekvő lélekszilánk-ár (pl. 5→8→12→18→25), minden 3. Létszám-rang +1 minion-slot (max +3 az alap fölé). A rangok egy player-store-ban (a `MinionManager` mellett) tárolódnak; cast-kor a `SummonMinionsSpell` ebből olvassa a stat-szorzókat, a spawnolt minion attribútum-módosítóit a saját entity-scheduleren állítja be (Folia). Max rang elérésekor egyedi minion-CMD-skin (A33/B9 iránnyal összekötve).

**Miért jó:** a Nekromanta végjáték-identitása lesz, hogy a serege fizikailag látszik nőni a szezonnal — a niche, unlock-feltételes (Sötét frakció + bűnös) úthoz így súlyos jutalom társul.

**Építőkövek:** `SoulShardManager`, `MinionManager`, `SummonMinionsSpell` (minion-minta).

**Buktatók:** a végtelenül skálázódó sereg PvP-ben balansztörő lehet — rang-cap és PvP-zónákban (arénák, B41 liga) minion-létszám-limit kell.
### E7. Varázsló: rúnaíró affinitás

> **Lore-horgony:** Caldestera elveszett rúna-tudása — a varázsló-forrás (kódex VI.)

**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** A B26 rúnakovácsolás Varázsló-kaszt-exkluzív bónusza — a kaszt „olvassa" a rúnákat.

**Hogyan működne:** Ha egy Varázsló rúnázott fegyvert/páncélt visel, a rúna tematikus bónusza (pl. +2% spell-erő) rá nézve megduplázódik (equip-eventkor PDC-olvasás, ItemFactory rúna-tag alapján), más kaszt csak alap-értéken kapja. Cserébe a Varázsló recept-listájában (`professions.yml`) 1-2 kaszt-exkluzív rúna-recept nyílik (pl. „Mana-visszhang rúna": ritka esély ingyen újracastolásra), amit csak Varázsló olvashat fel sikeresen (recipe-gate a JobType-ra).

**Miért jó:** a rúna-rendszer nem homogenizálja a kasztokat, hanem a Varázsló „mágia mestere" identitását item-oldalon is erősíti.

**Építőkövek:** B26 rúnakovácsolás, ItemFactory PDC-minta, `professions.yml` recept-gate.

**Buktatók:** kaszt-exkluzív item-bónusz könnyen „kötelező pick"-ké válhat — a hatás maradjon kis, additív jellegű.
### E25. Boszorkánymester: rituálé-oltár pakt

> **Lore-horgony:** a rituálé-oltárok törvénye + a Kárhozat-forrás pakt-fantáziája (kódex VI./VIII.)

**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** A Boszorkánymester Lélekerő-erőforrása közvetlen kapcsolatba kerül a relikvia-rendszer rituálé-oltárával.

**Hogyan működne:** A rituálé-oltárnál (`relics.yml`) a Boszorkánymester egyszeri, kaszt-exkluzív „pakt"-ceremóniát végezhet: a maximális Lélekerő-poolja tartósan +20%-kal nő (talent max-attribútum mintájú PDC-módosító), cserébe az oltár egy ritka rituálé-alapanyagot fogyaszt, ami kizárólag B47 ereklye-expedíciókból szerezhető.

**Miért jó:** a Lélekerő-rendszer (E5 kockázat/jutalom identitása) konkrét, ritka végjáték-célt kap, összekötve a Boszorkánymestert a rituálé-oltárral és a B47 expedíciós tartalommal.

**Építőkövek:** relics.yml rituálé-oltár, B47 ereklye-expedíciók (alapanyag-forrás), talent max-attribútum minta.

**Buktatók:** tartós, végleges pool-növelés balansz-kockázat — nem halmozható cap kell, és a ritka alapanyag miatt csak keveseknél lesz elérhető (tudatosan vállalt szűkösség végjáték-tartalomként).
### E32. Sárkányidéző: sárkánytojás-relikvia

> **Lore-horgony:** sárkány-eszencia + az Ereklyék Törvénye (kódex VI./VIII.)

**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Kaszt-exkluzív relikvia-típus, ami az Eszencia-rendszerhez kötődik.

**Hogyan működne:** Egy ritka relikvia („Sárkánytojás-töredék", új `relics.yml` bejegyzés) csak Sárkányidéző birtokában aktiválva az Eszencia-skála (E30) mindkét szélső pólusát 10%-kal kiterjeszti (-110..+110), hosszabb ideig fenntartható extrém polaritás-bónuszt engedve. A relikvia birtoklása/aktiválása a meglévő relikvia-szabályokat (cooldown, esetleges PvP-rablás) követi, csak a hatás-branch kaszt-gate-elt (JobType-ellenőrzés a hatás-alkalmazásnál).

**Miért jó:** a relikvia-rendszer konkrét, kaszt-tematikus ágat kap, összekötve az E30/E31 eszencia-identitást a szerver nagy presztízs-tárgyaival — kaszt-specifikus izgalmat ad a relikvia-vadászatnak.

**Építőkövek:** relics.yml relikvia-rendszer, E30 Eszencia-polaritás, JobType-gate a relikvia-effekt-alkalmazásban.

**Buktatók:** a kaszt-exkluzív hatás miatt más kaszt „értéktelennek" találhatja a relikviát, ha megszerzi — érdemes egy kaszt-független alap-hatást is adni neki, a Sárkányidéző-bónusz csak plusz réteg legyen.

---

[← Ötlettár-index](../IDEAS.md)
### F13. Piaci pánik esemény

> **Lore-horgony:** „a pénz értéke a világ állapotát követi" — a pánik mint kánon-esemény (kódex VIII.)

🟡 • ⭐⭐

**Mi ez:** Ritka világesemény, ami egy véletlen valuta árfolyamát átmenetileg leveri — a
meglévő kereslet-sokk (felfelé mozgás) tükörpárja.
**Hogyan működne:** A kereslet-sokk (`ExchangeRateService`, x1,2–1,6 emelés) mellé egy
szimmetrikus „pánik" ág (x0,6–0,8 szorzó) ugyanabból az esemény-ütemezőből, broadcast-
üzenettel („Pánik tört ki a Kék piacon!"). Nincs közvetlen pénzmozgás — csak az árfolyam-
számítás bemenete változik időlegesen, ugyanúgy, mint a meglévő sokknál.
**Miért jó:** A jelenlegi rendszer csak felfelé lő ki — a lefelé mozgás hiánya hosszú távon
csak inflál. A pánik szimmetrikus kockázatot ad, és a spekulánsoknak (F2) is izgalmasabb
terepet teremt.
**Építőkövek:** `ExchangeRateService` kereslet-sokk logikája (tükrözve).
**Buktatók:** Túl gyakori/durva pánik random-frusztrálónak érződhet — kis valószínűség és jól
látható broadcast kell, nehogy „láthatatlan büntetésként" éljék meg.
### F14. Konjunktúra esemény

> **Lore-horgony:** ugyanaz a kánon-mondat — a konjunktúra a jó idők lenyomata (kódex VIII.)

🟢 • ⭐

**Mi ez:** A piaci pánik (F13) pozitív párja: időszakos „fellendülés", amikor egy valutában
ideiglenesen csökken a piaci eladási díj (nem az árfolyam).
**Hogyan működne:** Broadcast-vezérelt időablak (pl. 30 perc), amíg a `MarketManager` adott
valutában kötött eladásainak díja a szokásos 10% helyett 5% — kevesebb pénz ég el, de a
kereskedési kedv nő, mert olcsóbb kereskedni. Az esemény-ütemező (mint a többi világesemény)
sorsolja, admin-beavatkozás nem kell.
**Miért jó:** Aktív kereskedésre ösztönöz konkrét időablakban (mint a kereslet-sokk),
közösségi zajt gerjeszt („most van a fellendülés, adj-vegyél!") — olcsó, tisztán pozitív
hangulati elem.
**Építőkövek:** `MarketManager` díjszámítás, esemény-ütemező minta.
**Buktatók:** Az alacsonyabb díj rövid távon kevesebb sinket jelent — gyakoriságban szigorúan
korlátozva kell tartani, hogy ne törje meg az inflációs egyensúlyt.
### F15. Szezonzáró árfolyam-sokk

> **Lore-horgony:** a Korszakok lapfordulása az árfolyamban (kódex VIII.)

🟢 • ⭐⭐

**Mi ez:** A szezon utolsó napjaiban (a B33 „végítélet-hét" gazdasági rétege) minden valuta
árfolyama felgyorsult ütemben, láthatóan ingadozik.
**Hogyan működne:** `season.closing-days` config alatt az `ExchangeRateService` kereslet-sokk
gyakorisága/amplitúdója megszorzódik (pl. 3×), a fővárosi árfolyamtáblák (hologram) frissítési
gyakorisága is nő. Nincs új pénzforrás — csak a meglévő sokk-mechanika idő-alapú felturbózása.
**Miért jó:** A B33 szezonzáró világeseményhez illő gazdasági dráma-réteg — az utolsó napok
kereskedése emlékezetesebb, aki figyeli a piacot, nagyot nyerhet/veszíthet, ami sztorikat
generál.
**Építőkövek:** `ExchangeRateService`, B33 szezonzáró esemény-ütemezés (előfeltétel).
**Buktatók:** Csak B33 mellett teljes az élménye — önállóan is működik, de a hatás kisebb.
### G6. Becsület-párbaj a bűn tisztítására

> **Lore-horgony:** a bűn/vezeklés-rendszer becsület-útja (kódex VII. — Kitaszítottak)

🟡 • ⭐⭐

**Mi ez:** A bűnös játékos felajánlhat egy „becsület-párbajt" — ha nyer az áldozat ellen (vagy
annak frakciótársa ellen), egy bűnpontja letörlődik.
**Hogyan működne:** `/duel <név> honor` — a B2 duel-infrára épülő új mód: tét helyett a
győztes bűnszámlálója (`SinManager`) -1-et kap, ha a kihívó bűnös és az ellenfél a sértett fél
(vagy annak nevezett képviselője). A kihívás csak akkor ajánlható fel, ha a kihívó bűnszáma
> 0. A meccs maga beleegyezéses (bűnt nem termel, ahogy minden duel).
**Miért jó:** Szimbolikus, RP-s út a bűn csökkentésére a vezeklés-küldetéslánc mellett — a
sértett fél dönthet, elfogadja-e az „elégtételt", ahelyett hogy csak várna a fejvadászokra.
**Építőkövek:** SinManager, B2 duel-infra (escrow nélkül).
**Buktatók:** exploit-veszély, ha a bűnös a saját alt/haverja ellen "veszít" szándékosan
bűntisztázásért — érdemes napi/heti limitet szabni rá.
### G14. Kém-mechanika: álca a LibsDisguises-hídon

> **Lore-horgony:** a Suttogók álca-motívuma nyílt kém-mechanikaként (kódex VII.)

🔴 • ⭐⭐

**Mi ez:** Raid előtt egy speciális itemmel/spellel rövid időre másik frakció tagjának
álcázhatod magad, hogy felderíts (korlátozott, kockázatos taktikai eszköz).
**Hogyan működne:** `/spy disguise <célfrakció>` — a `DruidDisguise`-híd (integration/,
LibsDisguises soft-depend) mintájára egy időkorlátos (pl. 60 mp) álca, ami az álcázott
frakció-nevét/skin-jelzését mutatja MÁS játékosoknak, de a saját HUD-on/tablistán a valódi
adat marad admin/rendszer-oldalon. Korlátok: nem használható aktív raid harci szakaszban
(csak felkészülés/felderítés közben), és PvP-interakció (találat adása/kapása) azonnal
lebuktatja (leveszi az álcát). Bűn-rendszer: az álca alatti kém-tevékenység (pl. területre
belépés) a normál lopás/behatolás szabályai szerint minősül — az álca nem ment fel bűn alól.
**Miért jó:** Roleplay-mély taktikai réteg a szervezett frakcióknak; a kemény korlátok
(nincs harci előny, kill lebuktat) miatt nem lesz PvP-erőforrás, csak információs játék.
**Építőkövek:** integration/DruidDisguise (LibsDisguises-híd), RaidManager fázis-ellenőrzés,
SinManager.
**Buktatók:** komoly félreértés-/panasz-potenciál („becsapott!”) — világos szabály és
UI-jelzés kell, hogy ne érezzék tisztességtelennek; Folia-oldalon a disguise-frissítés
mindig a céljátékos saját régió-szálán fusson.
### I7. Évszakos termények a Bőség-időhöz kötve

> **Lore-horgony:** a Bőség ideje — a Fa gyógyuló lehelete a termésben (kódex VIII.)

**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** A Bőség-idő (a szezon-esemény infra hangulati ablaka) alatt a Gyógynövényész termény-betakarítása extra XP-t/hozamot ad, azon kívül visszaáll az alap ütem.
**Hogyan működne:** A `ProfessionXpListener.onPlayerHarvestBlock`-ban egy config-szorzó (`professions.seasonal.abundance-multiplier`) az aktív világesemény-getterből olvasva (mintázat: E2 druida-hangolódás ugyanezt az eseményt olvassa spell-oldalról). Opcionálisan egy „téli szűkösség" ellentétes irányú szorzó is bevezethető, de a minimum verzió csak a bőség-bónusz.
**Miért jó:** Olcsó „élő világ" réteg, ami a Gyógynövényész-t is bekapcsolja a szezon-ritmusba (eddig csak a druida spellek reagáltak rá) — a gyűjtögető ritmusa illeszkedik a világ ritmusához.
**Építőkövek:** Világesemény-getterek (Bőség-idő állapot), `ProfessionXpListener`, `ConfigManager`.
**Buktatók:** A szorzó csak a HARVEST eseményre hasson, ne a sima blokk-törésre (különben Favágó/Bányász is véletlenül bónuszt kapna).
### I16. Szakma-céh heti közös cél

> **Lore-horgony:** a Céhek Öröksége — céh-közösségi célok (kódex VIII.)

**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Az azonos szakmát űzők (frakciótól függetlenül) egy heti, szakma-szintű közösségi célban vesznek részt („Bányászok együtt: 5000 érc egy hét alatt").
**Hogyan működne:** A meglévő közösségi cél-infra (frakció-szintű mintára) egy GLOBÁLIS, szakma-szűrt számláló-változata: `ProfessionXpListener` minden releváns eseménynél hozzáad egy megosztott heti számlálóhoz, ha a játékos az adott szakmát űzi. Cél elérésekor mindenki, aki hozzájárult (min. küszöb felett), közös jutalmat kap (XP-bónusz-hét vagy kozmetika). Heti reset a szezon-scheduler mintájára.
**Miért jó:** Frakció-semleges közösségi réteget ad a szakma-rendszerhez — a Bányászok mint „szakma-közösség" először kapnak közös identitást és célt a frakciós struktúra felett, ami a heti visszatérést erősíti (B1 párja szakma-oldalon).
**Építőkövek:** Közösségi cél-infra, `ProfessionXpListener`, `ProfessionManager` (aktív szakma-szűrés).
**Buktatók:** A globális számláló több régió-szálról is íródik — konkurrens/atomic számláló kell (a `QuestManager.customQuests` copy-on-write mintája vagy synchronized long).
### I22. Ritka recept-lapok loot-forrásból

> **Lore-horgony:** „tervrajzokat ment ki a romok közül" — a kánon-mondat kiterjesztése recept-lapokra (kódex VIII.)

**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** A meglévő tervrajz (Knowledge Book) rendszer bővítése: a legritkább recepteket KIZÁRÓLAG világboss/nehéz esemény loot adja, sosem NPC-bolt.
**Hogyan működne:** A `ProfessionRecipeCatalog`-ban egy új `loot-only: true` mező a csúcs-recepteknél (pl. Ereklye-szintű mestermunkák), amit a világboss/esemény loot-tábla bővítése ad ki (a guide már említi az „Ősi Ereklyeszilánk" boss-only alapanyagot — ugyanez az elv, de magára a RECEPTRE). A recept-könyvben zárolt sorként látszik, lore: „Csak legendás ellenfelektől szerezhető".
**Miért jó:** A világbossokat/nehéz eseményeket a szakma-progresszió szempontjából is tétre teszi (eddig csak felszerelés-loot forrás volt), és a legritkább recepteknek presztízs-értéket ad — nem lehet pénzért megvenni.
**Építőkövek:** `ProfessionRecipeCatalog`, világboss/esemény loot-tábla, `ItemRarityService` (boss tier).
**Buktatók:** Legyen elég recept ÉS elég gyakori a boss-esemény, hogy ne érezzék elérhetetlennek — a C1 spell-statisztika mintájára érdemes loot-drop számlálót is vezetni (mennyi tervrajz esett/hét).

---

**Összesen: 33 tétel** (S: 6, A: 27). Ajánlott kezdés: **H2 + B42** (a gyógyuló Fa és a Mélység Népe azonnal játszhatóvá válik), majd **B15** (a krónikás-hang életben tartása).
