# IceSMP GUI-referencia

> Dokumentált HEAD: `4643ab53586f0c1ee7352df16dcd477013e6fad4`

A lista a tényleges InventoryHolder-konstrukciót és a hozzájuk regisztrált click/drag/close routingot követi. Dekoratív filler slot nem funkcionális művelet. Minden funkcionális slot vagy dinamikus tartomány szerepel.

## Lefedettség: 22 / 22 aktív GUI-felület

| GUI | Megnyitás | Közönség / jog | Méret | Deployed státusz |
|---|---|---|---|---|
| Főmenü és tematikus parancsmenük | /menu, /achievements, /leaderboard; belső MENU/LB navigáció | Játékos; Admin panel jogosultság szerint / `Nincs a megnyitáshoz; minden célparancs saját jogát ellenőrzi` | 27/36/45/54, nézettől függően | Megváltozott |
| Karakterlap | /profile | Játékos / `—` | 36 | Megváltozott |
| Kasztválasztó | Karakterlap /class kontextusból | Játékos / `—` | 54 | Megváltozott |
| Szakmaválasztó | Karakterlap | Játékos / `—` | 45 | Megváltozott |
| Specializációk | Karakterlap vagy /spec folyamat | Játékos / `—` | 54 | Megváltozott |
| Talent-fa | Karakterlap | Játékos / `—` | 54 | Megváltozott |
| Képességfa | Karakterlap vagy kasztválasztó | Játékos / `—` | 54 | Változatlan |
| Varázskönyv | /spellbook vagy Lélekkapocs interakció | Játékos / `—` | 54 | Megváltozott |
| Szakmai receptkönyv | /profession recipes | Játékos / `—` | 54 | Megváltozott |
| Piactér | /market, /market search, /market ereklye | Játékos / `—` | 54 | Megváltozott |
| Adományláda | /adomany | Játékos / `—` | 54 | Megváltozott |
| Küldetésnapló | /quest log | Játékos / `—` | 54 | Változatlan |
| Quest builder | /quest admin builder <id> | Admin / `icesmp.admin.quest` | TYPE_PICKER 36; EDITOR 54 | Megváltozott |
| NPC/frakció bolt | NPC-kötés/interakció | Játékos / `—` | 9–54, tételszám szerint | Megváltozott |
| Bestiárium | /bestiarium | Játékos / `—` | 27 | Új |
| Megbízottak kezelése | /claim trustgui vagy Claim menü | Játékos / `—` | 54 | Új |
| Config menü | /icesmp config menu vagy admin főmenü | Admin / `icesmp.admin.config` | 36 | Új |
| Crate böngésző és preview | /crate, /crate info, /crate preview | Játékos / `icesmp.crate.use + opcionális crate-specifikus jog` | 54 | Új |
| Crate nyitási animáció | Sikeres crate settlement után automatikusan | Játékos / `A nyitás hozzáférési jogai` | 27 | Új |
| Invsee | /invsee ... | Moderátor/Admin / `icesmp.moderation.inventory.read vagy .edit` | 54 | Új |
| Moderációs GUI | /moderation [játékos] | Moderátor/Admin / `icesmp.moderation.gui + gombonkénti műveleti jog` | 54 | Új |
| Társ GUI | /pet vagy /pet menu | Játékos / `—` | 27 | Új |

## Főmenü és tematikus parancsmenük

- Megnyitás: /menu, /achievements, /leaderboard; belső MENU/LB navigáció.
- Célközönség és jog: Játékos; Admin panel jogosultság szerint; `Nincs a megnyitáshoz; minden célparancs saját jogát ellenőrzi`.
- Holder: `CommandMenuHolder`; méret: `27/36/45/54, nézettől függően`.
- Deployed JAR-hoz képest: **Megváltozott**.

Funkcionális slotok/műveletek:

- MAIN (45): 4 fejléc read-only; 10 karakterlap, 11 varázskönyv, 12 társ (ha elérhető), 13 questnapló, 14 napi állapot, 15 achievement, 16 leaderboard; 19 frakció, 20 party, 21 claim, 22 events, 23 bounty, 24 relic, 25 souls (csak Nekromantának); 28 bank, 29 piac, 30 adomány, 31 receptek, 32 bestiárium, 33 heti szakmacél, 34 admin (bármely adminjoggal), 40 bezárás.
- FACTION (36): 4 állapot read-only; frakció nélkül 10/12/14/16 join; tagként 10/11/12 adomány 10/50/100, 14 király/szavazás, 16 frakcióváltó; királynál 15 kasszakivét 100 és 20-tól legfeljebb három más-frakció raidgomb; 19 céh, 28 kém, 29 karaván 100, 30 kilépés, 31 vissza.
- FACTION_SWITCH (36): 4 feltételek read-only; a jelenlegi frakciót kihagyó, tömörített join/váltás csempék 10/12/14-en (frakció nélkül 10/12/14/16); 31 vissza.
- BANK (36): 4 egyenlegek read-only; 11 minden fizikai valuta befizetése, 13 saját valuta kivétele 64, 15 árfolyamok, 22 váltó, 24 ereklye-börze, 31 vissza.
- EXCHANGE (45): 4 egyenlegek; 9 forráscímke; 11/12/14/15 forrásválasztók; 27 célcímke; 29/30/32/33 célválasztók; 22 árfolyam vagy hiányzó választás read-only; érvényes párnál 38/39/40 váltás 16/32/64 és pozitív egyenlegnél 41 mind; 36 vissza.
- EVENTS (27): 0 teljes status parancs, 4 season parancs, 10 blood-moon status parancs, 12 caravan status parancs; 11 worldboss-, 13 escort-, 14 abundance-, 15 challenge-, 16 meteor- és 19 gathering/buff-állapot read-only; 22 vissza.
- RELIC (36): 4 fejléc; relikviák read-only csempéi 10–16, majd 19–25, legfeljebb 14 bejegyzés; 31 vissza.
- SOULS (27): Nekromantának 11 szilánkegyenleg read-only, 13 lélekkovács, 15 bajnokidézés; másnak 13 zárolt info; 22 vissza.
- PARTY (27): 4 fejléc; kikapcsolt vagy csapat nélküli állapotban 13 info; csapatban a tagok read-only 10–16, 19 kilépés, vezetőnek 25 feloszlatás; 22 vissza.
- CLAIM (27): kikapcsolva 13 info; aktívan 4 összegzés read-only, 10 claim, 11 unclaim, 12 show, 13 list, 14 pos1, 15 pos2, 16 area, 19 extend up, 20 extend down, 21 trustgui, 22 vissza.
- BOUNTY (27): kikapcsolva 13 info; aktívan 4 fejléc, 9–17 legfeljebb kilenc körözött játékos read-only vagy 13 üresállapot, 18 becsület-párbaj, 22 vissza.
- ADMIN (54): 4 fejléc; 10 reload, 11 config GUI, 12 exchangeboard place, 13 exchangeboard remove, 14 iceitem usage, 16 intro; eventek: 19 blood-moon start, 20 stop, 21 worldboss, 22 invasion, 23 caravan arrive, 24 depart, 25 wild-hunt, 26 corruption, 28 meteor, 29 treasure, 30 gathering, 31 abundance, 32 challenge, 33 escort, 34 ambient, 35 archeology; 37 claim admin unclaim, 38 NPC-lista, 39 quest adminlista, 49 vissza; jog nélkül csak 22 tiltás és 49 vissza.
- LEADERBOARD (54): 4 fejléc; 0 level, 1 wealth, 2 raidkills kategória; top 10 read-only a 9–18 slotokon vagy 22 üresállapot; 49 vissza.
- ACHIEVEMENTS (54): 4 fejléc; legfeljebb 36 read-only mérföldkő 9–44; 49 vissza.

- Lapozás: A dinamikus nézetek a saját forrásbeli plafonjukig töltenek; nincs általános lapozó..
- Vissza/bezárás: MENU:MAIN vagy az adott szülőnézet; CLOSE bezár..
- Read/edit különbség: A legtöbb csempe parancsot futtat; az info csempék read-onlyk..
- Hibás vagy lezárt állapot: A nem használható funkció tiltott/infó csempét kap; admin gombok csak megfelelő joggal kötődnek..
- Cleanup és clickbiztonság: Owner UUID ellenőrzés, top inventory click/drag tiltás; nincs tartós GUI-state..
- Forrás: `src/main/java/hu/taliann/icesmp/gui/CommandMenus.java`, `src/main/java/hu/taliann/icesmp/listeners/CommandMenuListener.java`.

## Karakterlap

- Megnyitás: /profile.
- Célközönség és jog: Játékos; `—`.
- Holder: `ProfileHolder`; méret: `36`.
- Deployed JAR-hoz képest: **Megváltozott**.

Funkcionális slotok/műveletek:

- 4 fej/read-only; 11 kaszt; 13 specializáció; 15 szakma; 20 talent; 22 képességfa; 24 gazdaság read-only; 27 főmenü; 31 bezárás.

- Lapozás: Nincs.
- Vissza/bezárás: 27 főmenü; 31 bezár.
- Read/edit különbség: Navigáció; fej/gazdaság csak kijelzés.
- Hibás vagy lezárt állapot: A célmenük saját állapotkapui érvényesek.
- Cleanup és clickbiztonság: Owner UUID + click/drag cancel; holder inventory nullázás.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/ProfileGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/CharacterGUIListener.java`.

## Kasztválasztó

- Megnyitás: Karakterlap /class kontextusból.
- Célközönség és jog: Játékos; `—`.
- Holder: `JobGUIHolder`; méret: `54`.
- Deployed JAR-hoz képest: **Megváltozott**.

Funkcionális slotok/műveletek:

- 10,12,14,16,19,21,23,25,28,30,32,34,37,39,41,43: 16 kaszt; 47 képességfa; 49 vissza; 51 Lélekkapocs.

- Lapozás: Nincs.
- Vissza/bezárás: 49 karakterlap.
- Read/edit különbség: Kasztválasztás/kapunyitás.
- Hibás vagy lezárt állapot: Kaszt-, katalizátor- és választási kapuk a managerben.
- Cleanup és clickbiztonság: Owner UUID, click/drag cancel, holder cleanup.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/JobGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/JobGUIListener.java`.

## Szakmaválasztó

- Megnyitás: Karakterlap.
- Célközönség és jog: Játékos; `—`.
- Holder: `ProfessionHolder`; méret: `45`.
- Deployed JAR-hoz képest: **Megváltozott**.

Funkcionális slotok/műveletek:

- 4 fejléc; 19–21 gyűjtögetők; 23–25 készítők; 30/32 másodlagos szakmák read-only; 40 vissza.

- Lapozás: Nincs.
- Vissza/bezárás: 40 karakterlap.
- Read/edit különbség: Elsődleges szakmák választása; másodlagosak kijelzése.
- Hibás vagy lezárt állapot: Slot- és szakmakorlátok managerből.
- Cleanup és clickbiztonság: Owner UUID + click/drag cancel.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/ProfessionGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/CharacterGUIListener.java`.

## Specializációk

- Megnyitás: Karakterlap vagy /spec folyamat.
- Célközönség és jog: Játékos; `—`.
- Holder: `SpecHolder`; méret: `54`.
- Deployed JAR-hoz képest: **Megváltozott**.

Funkcionális slotok/műveletek:

- 4 fejléc; 10–16 kasztspecek; 28–34 és 37–43 szakmaspecek; 45 class respec; 49 vissza; 53 profession respec.

- Lapozás: Nincs.
- Vissza/bezárás: 49 karakterlap.
- Read/edit különbség: Választás és két respec.
- Hibás vagy lezárt állapot: Szint, memóriafeloldás, költség és meglévő választás kapuz.
- Cleanup és clickbiztonság: Owner UUID + click/drag cancel.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/SpecGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/CharacterGUIListener.java`.

## Talent-fa

- Megnyitás: Karakterlap.
- Célközönség és jog: Játékos; `—`.
- Holder: `TalentHolder`; méret: `54`.
- Deployed JAR-hoz képest: **Megváltozott**.

Funkcionális slotok/műveletek:

- 0 kasztpont-címke, 4 fejléc, 45 szakmapont-címke — read-only; dinamikus talentnode-ok az 1–3. és 4–5. sorban, soronként hét belső oszloppal; 53 vissza.

- Lapozás: Nincs.
- Vissza/bezárás: 53 karakterlap.
- Read/edit különbség: Node-kattintás talentpontot költ.
- Hibás vagy lezárt állapot: Előfeltétel, max rang és pont hiánya vizuálisan lezárt.
- Cleanup és clickbiztonság: Owner UUID + click/drag cancel.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/TalentGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/CharacterGUIListener.java`.

## Képességfa

- Megnyitás: Karakterlap vagy kasztválasztó.
- Célközönség és jog: Játékos; `—`.
- Holder: `SkillTreeHolder`; méret: `54`.
- Deployed JAR-hoz képest: **Változatlan**.

Funkcionális slotok/műveletek:

- 0–44 dinamikus, read-only feloldási állapot; 49 vissza.

- Lapozás: Legfeljebb 45 bejegyzés, nincs lapozás.
- Vissza/bezárás: 49 kasztválasztó.
- Read/edit különbség: Read-only.
- Hibás vagy lezárt állapot: Feloldott/zárolt állapotot jelenít meg.
- Cleanup és clickbiztonság: Owner UUID + click/drag cancel, holder cleanup.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/SkillTreeGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/SkillTreeGUIListener.java`.

## Varázskönyv

- Megnyitás: /spellbook vagy Lélekkapocs interakció.
- Célközönség és jog: Játékos; `—`.
- Holder: `SpellbookHolder`; méret: `54`.
- Deployed JAR-hoz képest: **Megváltozott**.

Funkcionális slotok/műveletek:

- 0–44 spellbejegyzések: normál katt kiválasztás, shift-katt kedvenc ki/be; 45 előző; 47 csak feloldott/minden szűrő; 49 oldalinfó read-only; 53 következő.

- Lapozás: 45 spell/oldal.
- Vissza/bezárás: Nincs külön vissza; inventory bezárható.
- Read/edit különbség: Spellkiválasztás és szűrés; leírás read-only.
- Hibás vagy lezárt állapot: Nem használható spell zárolt állapotban marad.
- Cleanup és clickbiztonság: Owner UUID, click/drag cancel; bezáráskor holder cleanup.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/SpellbookGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/SpellbookListener.java`.

## Szakmai receptkönyv

- Megnyitás: /profession recipes.
- Célközönség és jog: Játékos; `—`.
- Holder: `ProfessionRecipeHolder`; méret: `54`.
- Deployed JAR-hoz képest: **Megváltozott**.

Funkcionális slotok/műveletek:

- 0–44 receptek: kattintás craftkísérlet; 45 előző; 49 bezárás; 53 következő.

- Lapozás: 45 recept/oldal.
- Vissza/bezárás: 49 bezárás.
- Read/edit különbség: Craftolás inventoryból; receptállapot kijelzés.
- Hibás vagy lezárt állapot: Tanulatlan/hiányos recept nem craftolható.
- Cleanup és clickbiztonság: Owner UUID, click/drag cancel; tranzakció után újrarender.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/ProfessionRecipeGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/ProfessionRecipeBookListener.java`.

## Piactér

- Megnyitás: /market, /market search, /market ereklye.
- Célközönség és jog: Játékos; `—`.
- Holder: `MarketHolder`; méret: `54`.
- Deployed JAR-hoz képest: **Megváltozott**.

Funkcionális slotok/műveletek:

- 0–44 tételek: fix árnál vásárlás; aukción bal katt minimum licit, jobb katt nagyobb licit, shift katt buyout; 45 előző; 49 oldalinfó; 53 következő.

- Lapozás: 45 tétel/oldal.
- Vissza/bezárás: Inventory bezárás.
- Read/edit különbség: Pénz- és tárgytranzakció.
- Hibás vagy lezárt állapot: Eltűnt/saját/lejárt/elégtelen fedezet állapot visszautasít.
- Cleanup és clickbiztonság: Owner UUID + click/drag cancel; siker/hiba után újrarender.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/MarketGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/MarketGUIListener.java`.

## Adományláda

- Megnyitás: /adomany.
- Célközönség és jog: Játékos; `—`.
- Holder: `DonationChestHolder`; méret: `54`.
- Deployed JAR-hoz képest: **Megváltozott**.

Funkcionális slotok/műveletek:

- 0–44 adomány elvétele; 45 főkéz adományozása; 48 előző; 49 oldalinfó; 50 következő; 53 főmenü.

- Lapozás: 45 adomány/oldal.
- Vissza/bezárás: 53 főmenü.
- Read/edit különbség: Ingyenes elvétel és teljes stack adomány.
- Hibás vagy lezárt állapot: Versenyhelyzetben már elvett tétel frissítéssel eltűnik.
- Cleanup és clickbiztonság: Owner UUID + click/drag cancel; minden művelet után újrarender.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/DonationChestGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/DonationChestListener.java`.

## Küldetésnapló

- Megnyitás: /quest log.
- Célközönség és jog: Játékos; `—`.
- Holder: `QuestLogHolder`; méret: `54`.
- Deployed JAR-hoz képest: **Változatlan**.

Funkcionális slotok/műveletek:

- 0–44 questek: aktív fülön eldobás, felvehető fülön felvétel, teljesített fül read-only; 45/46/47 fülek; 48 előző; 49 oldalinfó; 50 következő; 53 főmenü.

- Lapozás: 45 quest/oldal/fül.
- Vissza/bezárás: 53 főmenü.
- Read/edit különbség: Fülfüggő accept/abandon.
- Hibás vagy lezárt állapot: Feltétel vagy versenyhelyzet esetén hiba és frissítés.
- Cleanup és clickbiztonság: Owner UUID + click/drag cancel.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/QuestLogGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/QuestLogListener.java`.

## Quest builder

- Megnyitás: /quest admin builder <id>.
- Célközönség és jog: Admin; `icesmp.admin.quest`.
- Holder: `QuestBuilderHolder`; méret: `TYPE_PICKER 36; EDITOR 54`.
- Deployed JAR-hoz képest: **Megváltozott**.

Funkcionális slotok/műveletek:

- TYPE_PICKER: a 21 regisztrált objektívatípus pontosan a 0–20 slotokon; 35 mégse/bezárás.
- EDITOR: 4 fejléc read-only; chatmezők: 10 display-name, 11 description, 12 giver-npc, 13 cooldown-hours, 19 requires-level, 20 requires-quest, 21 requires-faction, 22 requires-job, 23 auto-start-territory, 28 rewards.class-xp, 29 rewards.currency.type, 30 rewards.currency.amount, 31 rewards.unlock-spell, 33 rewards.items, 37 dialogue.speaker, 38 dialogue.give, 39 dialogue.complete.
- EDITOR kapcsolók: 14 repeatable, 15 seasonal, 32 rewards.cleanse-sins; 16 objectives-mode ALL/SEQUENCE.
- EDITOR objektíva/admin: 41 objektívaösszegzés read-only, 42 új objektíva, 45 két egymást követő kattintásos végleges törlés, 49 bezárás.

- Lapozás: Nincs.
- Vissza/bezárás: Bezárás; prompt után szerkesztő újranyit.
- Read/edit különbség: GUI + következő chatüzenet mint mező/darabszám/név.
- Hibás vagy lezárt állapot: Configból jövő quest nem szerkeszthető.
- Cleanup és clickbiztonság: Owner UUID, click/drag cancel; prompt quit/kick esetén törlődik.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/QuestBuilderGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/QuestBuilderListener.java`.

## NPC/frakció bolt

- Megnyitás: NPC-kötés/interakció.
- Célközönség és jog: Játékos; `—`.
- Holder: `ShopHolder`; méret: `9–54, tételszám szerint`.
- Deployed JAR-hoz képest: **Megváltozott**.

Funkcionális slotok/műveletek:

- A konfigurált items lista első legfeljebb 54 eleme a saját 0-alapú listaindexével azonos sloton; hibás material kimarad és rést hagy; minden leképezett tétel kattintása banki vásárlás.

- Lapozás: Nincs; a méret 9 × clamp(ceil(tételszám/9), 1, 6), így legfeljebb 54 tétel.
- Vissza/bezárás: Inventory bezárás.
- Read/edit különbség: Vásárlás.
- Hibás vagy lezárt állapot: Zárt bolt, rossz frakció, eltűnt tétel vagy fedezethiány.
- Cleanup és clickbiztonság: Owner UUID + click/drag cancel; vásárlás után újrarender/bezárás.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/ShopGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/ShopListener.java`.

## Bestiárium

- Megnyitás: /bestiarium.
- Célközönség és jog: Játékos; `—`.
- Holder: `BestiaryHolder`; méret: `27`.
- Deployed JAR-hoz képest: **Új**.

Funkcionális slotok/műveletek:

- 10 szörnyfajok száma; 12 regisztrált receptek száma; 14 territóriumok száma; 16 világboss-definíciók száma — mind a négy csempe read-only.

- Lapozás: Nincs.
- Vissza/bezárás: Inventory bezárás.
- Read/edit különbség: Read-only összesítő.
- Hibás vagy lezárt állapot: Nincs részletes vagy kattintható kategórianézet.
- Cleanup és clickbiztonság: Minden click/drag tiltott.
- Forrás: `src/main/java/hu/taliann/icesmp/commands/BestiaryCommand.java`, `src/main/java/hu/taliann/icesmp/listeners/BestiaryListener.java`.

## Megbízottak kezelése

- Megnyitás: /claim trustgui vagy Claim menü.
- Célközönség és jog: Játékos; `—`.
- Holder: `ClaimTrustHolder`; méret: `54`.
- Deployed JAR-hoz képest: **Új**.

Funkcionális slotok/műveletek:

- 0–35 jelenlegi megbízottak: untrust; 45–52 közeli online játékosok: trust; 53 bezárás.

- Lapozás: Nincs; a két tartomány plafonja érvényes.
- Vissza/bezárás: 53 bezárás.
- Read/edit különbség: Trust/untrust parancsdelegálás.
- Hibás vagy lezárt állapot: Üres tartomány tájékoztató csempe.
- Cleanup és clickbiztonság: Owner UUID + click/drag cancel; művelet után újrarender.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/ClaimTrustGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/ClaimTrustGUIListener.java`.

## Config menü

- Megnyitás: /icesmp config menu vagy admin főmenü.
- Célközönség és jog: Admin; `icesmp.admin.config`.
- Holder: `ConfigMenuHolder`; méret: `36`.
- Deployed JAR-hoz képest: **Új**.

Funkcionális slotok/műveletek:

- ROOT: kategóriák beszúrási sorrendben 10–16, 19–25, 28–33; 35 bezárás.
- KATEGÓRIA: 0..N-1 szerkeszthető kulcs; 31 vissza; 35 bezárás.
- TOGGLE katt = vált; CYCLE katt = következő; NUMBER bal = +lépés, jobb = −lépés, shift = 5×.

- Lapozás: Nincs; a kurált katalógus kategóriánként legfeljebb 31 kulcs.
- Vissza/bezárás: 31 kategóriagyökér; 35 bezárás.
- Read/edit különbség: Data-folder config.yml override-ot ír, reloadol és validál.
- Hibás vagy lezárt állapot: Minden clicknél újra ellenőrzi a jogot; min/max clamp.
- Cleanup és clickbiztonság: Owner UUID + top inventory + permission ellenőrzés; click/drag cancel.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/ConfigMenuGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/ConfigMenuGUIListener.java`.

## Crate böngésző és preview

- Megnyitás: /crate, /crate info, /crate preview.
- Célközönség és jog: Játékos; `icesmp.crate.use + opcionális crate-specifikus jog`.
- Holder: `CrateBrowserHolder`; méret: `54`.
- Deployed JAR-hoz képest: **Új**.

Funkcionális slotok/műveletek:

- Lista: hozzáférhető crate-csempék pontosan a 10–16, 19–25, 28–34 és 37–43 tartományban (28 hely), kattintás preview; 49 bezárás.
- Preview: jutalomcsempék ugyanazon 28 contentsloton, read-only; 45 vissza a listához; 49 bezárás.

- Lapozás: Nincs; legfeljebb 28 crate vagy jutalom látható.
- Vissza/bezárás: 45 listához; 49 bezárás.
- Read/edit különbség: Read-only.
- Hibás vagy lezárt állapot: Hozzáférés-változáskor a preview nem nyílik.
- Cleanup és clickbiztonság: Owner UUID + click/drag cancel.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/CrateBrowserGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/CrateBrowserGUIListener.java`.

## Crate nyitási animáció

- Megnyitás: Sikeres crate settlement után automatikusan.
- Célközönség és jog: Játékos; `A nyitás hozzáférési jogai`.
- Holder: `CrateSpinHolder`; méret: `27`.
- Deployed JAR-hoz képest: **Új**.

Funkcionális slotok/műveletek:

- 4 állapot; 9–17 reel; 13 nyertes közép — minden slot kozmetikai, nem kattintható.

- Lapozás: Nincs.
- Vissza/bezárás: Bezárható.
- Read/edit különbség: Read-only animáció; a jutalom már jóváírt.
- Hibás vagy lezárt állapot: Nincs click action.
- Cleanup és clickbiztonság: Minden click/drag tiltott; close cancel flag leállítja a delayed láncot.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/CrateSpinGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/CrateSpinGUIListener.java`.

## Invsee

- Megnyitás: /invsee ....
- Célközönség és jog: Moderátor/Admin; `icesmp.moderation.inventory.read vagy .edit`.
- Holder: `InvseeHolder`; méret: `54`.
- Deployed JAR-hoz képest: **Új**.

Funkcionális slotok/műveletek:

- MAIN: 0–35 storage, 36–39 armor, 40 offhand; 45 ender; 49 módjelző; 53 bezárás.
- ENDER: 0–26 ender; 36 vissza main; 40 módjelző; 44 bezárás.

- Lapozás: Nincs.
- Vissza/bezárás: 36 main; close slot.
- Read/edit különbség: Read módban teljes tiltás; edit módban célslot cseréje escrow útvonalon.
- Hibás vagy lezárt állapot: Veszélyes shift/hotbar/collect/drag útvonalak tiltva; cél quit/reconnect recovery.
- Cleanup és clickbiztonság: Owner/session ellenőrzés, inventory close manager cleanup.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/InvseeGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/InvseeGUIListener.java`.

## Moderációs GUI

- Megnyitás: /moderation [játékos].
- Célközönség és jog: Moderátor/Admin; `icesmp.moderation.gui + gombonkénti műveleti jog`.
- Holder: `ModerationGuiHolder`; méret: `54`.
- Deployed JAR-hoz képest: **Új**.

Funkcionális slotok/műveletek:

- Játékoslista: név szerint rendezett, látható online játékosok közül legfeljebb 45 a 0–44 slotokon; 49 bezárás.
- Célpont: 10 warn, 11 mute 30m, 12 ban, 13 kick, 14 unmute, 15 unban, 19 history, 20 punishments, 21 reports, 22/23 main read/edit, 24/25 ender read/edit, 28 online teleport, 29 offlinetp, 30 socialspy, 31 vanish, 49 vissza, 53 bezárás.

- Lapozás: Nincs; az első 45 látható online játékos.
- Vissza/bezárás: 49 játékoslista; 53 bezárás.
- Read/edit különbség: Gombok a dokumentált parancsokat futtatják.
- Hibás vagy lezárt állapot: Jog nélküli gomb nem fut; eltűnt célpontot újra felold.
- Cleanup és clickbiztonság: Owner/láthatóság/top inventory ellenőrzés, click/drag cancel.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/ModerationGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/ModerationGUIListener.java`.

## Társ GUI

- Megnyitás: /pet vagy /pet menu.
- Célközönség és jog: Játékos; `—`.
- Holder: `PetGUIHolder`; méret: `27`.
- Deployed JAR-hoz képest: **Új**.

Funkcionális slotok/műveletek:

- 4 info; 10 summon; 11 dismiss; 12 név-hint; 14 aktiv; 15 passziv; 16 marad; 22 páncél/read-only; 26 bezárás.

- Lapozás: Nincs.
- Vissza/bezárás: 26 bezárás.
- Read/edit különbség: RUN:/pet delegálás; név gomb használati hintet ad.
- Hibás vagy lezárt állapot: Kaszt/petállapot kapuzza a műveletet.
- Cleanup és clickbiztonság: Owner UUID + click/drag cancel; bezáráskor holder inventory null.
- Forrás: `src/main/java/hu/taliann/icesmp/gui/PetGUI.java`, `src/main/java/hu/taliann/icesmp/listeners/PetGUIListener.java`.

## Általános GUI-biztonsági megállapítások

- Az owner UUID-t használó GUI-k más játékos kattintását elutasítják.
- A top inventory kattintásai és dragjei holder alapján tiltva vannak; a read-only felületek minden kattintást elnyelnek.
- A moderációs és config GUI nem a megnyitáskor kapott jogra hagyatkozik: a click routing újra ellenőriz.
- Az invsee edit nem közvetlen szabad drag: célslotcsere, escrow és reconnect-recovery útvonalat használ.
- A crate spin kozmetikai: a settlement a GUI megnyitása előtt megtörtént; bezárás csak az animációláncot állítja le.
