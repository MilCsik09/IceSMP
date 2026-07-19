# L) Lore-kiemelt ötletek (válogatás)

A kanonikus kódex ([docs/LORE.md](../LORE.md)) alapján **kiválogatott, erősen lore-illeszkedő
tételek** a B/D/H/J kategóriákból — az eredeti azonosítók megtartva (a forrás-fájlban egysoros
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

---

**Összesen: 14 tétel** (S: 5, A: 9). Ajánlott kezdés: **H2 + B42** (a gyógyuló Fa és a Mélység Népe azonnal játszhatóvá válik), majd **B15** (a krónikás-hang életben tartása).
