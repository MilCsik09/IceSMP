# J) Questek, story és progresszió

[← Ötlettár-index](../IDEAS.md)

Jelölés: **Munka** (🟢 kicsi / 🟡 közepes / 🔴 nagy) • **Érték** (⭐–⭐⭐⭐) • `[TOP]` = ajánlott
következő kör.

A quest-keretrendszer (`QuestManager`, `quests.yml`, `QuestBuilderGUI`) már tud objektíva-
típusokat (KILL_MOBS, COLLECT_ITEMS, TALK_TO_NPC, DELIVER_ITEMS, VISIT_TERRITORY stb.),
ALL/SEQUENCE módot, elágazó dialógust (`dialogue.choices`), NPC-rotációt, ismételhető/
szezonális küldetéseket, `next`-láncolást, admin quest-buildert és közösségi célokat (lásd
`docs/player-guide/12-kuldetesek.md`). Az alábbi ötletek erre az alapra épülnek. Duplikáció
elkerülése végett: az onboarding-lánc → **A5** (✅ kész), a HUD quest-tracker → **A20**, a heti
frakció-megbízások → **B1**, a napi vadász-cél → **B10**, a kaszt-story-lánc → **B28** — ezekre
csak hivatkozunk, itt nem ismételjük.

---

### J1. Kíséret-küldetés objektíva (ESCORT_NPC)
🟡 • **Érték:** ⭐⭐⭐

**Mi ez:** Egy NPC-t élve kell elkísérni A pontból B-be — útközben mobok támadhatják, a
küldetés elbukhat, ha az NPC meghal.
**Hogyan működne:** Új `ESCORT_NPC` érték az `OBJECTIVE_TYPES` halmazban; `objective.npc` +
`objective.destination-territory` + opcionális `objective.waypoints` (koordináta-lista) a
quests.yml sémában. A `FancyNpcsQuestBridge` mintájára egy útvonal-séta NPC spawnol, egy kis
periódusú region-tick figyeli az NPC HP-jét és a cél-territórium belépést (`TerritoryManager`
chunk-index). Folia: az NPC a saját régió-szálán mozog, a játékos PDC-jébe írt progressz
mindig a JÁTÉKOS schedulerére hop-oljon, ne az NPC-ére.
**Miért jó:** Dinamikus veszélyt ad a story-questekhez, jól passzol a visszatérő NPC-khez
(J12) és a kaszt-story-hoz (B28) — nem statikus "állj be egy körbe" feladat.
**Építőkövek:** `FancyNpcsQuestBridge`, `QuestManager` objektíva-keret, `TerritoryManager`.
**Buktatók:** NPC-pathfinding elakadhat blokkokon — időtúllépés + admin `/quest admin reset`
kell ellene, hogy ne ragadjon el a lánc.

### J2. Több-területes felfedező objektíva (EXPLORE_TERRITORY)
🟢 • **Érték:** ⭐⭐

**Mi ez:** A meglévő VISIT_TERRITORY (egy célpont) mellé egy "járd be mind" változat: N darab
*különböző* territórium felkeresése egy küldetésen belül.
**Hogyan működne:** `objective.type: "EXPLORE_TERRITORY"` + `objective.territories` (lista)
vagy `objective.territory-type` (pl. mind a négy főváros) + `objective.count`. A meglévő
territórium-belépés listener bővül: a PDC-be sanitized set-ként (`exploredIds` string-listaként
egy külön kulcs alatt) gyűjti a már meglátogatott azonosítókat, amíg el nem éri a count-ot.
**Miért jó:** Felfedezés-alapú questeknek (D8 titkos helyek, bestiárium B21) természetes
motort ad — nem kell 5 külön SEQUENCE-lépés egyetlen "járd be a világot" célhoz.
**Építőkövek:** `TerritoryManager` chunk-index, meglévő VISIT_TERRITORY listener.
**Buktatók:** A PDC-halmaz mérete korlátos legyen (max néhány tucat id), különben a
`getPersistentDataContainer` string hosszú lesz — inkább bitmaszk/index-lista, ne szabad szöveg.

### J3. Recept-craftolás objektíva (CRAFT_RECIPE)
🟢 • **Érték:** ⭐⭐

**Mi ez:** A jelenlegi CRAFT_ITEMS (anyag+darabszám) mellé egy pontosabb változat: konkrét
szakma-recept elkészítése, nem csak a végtermék anyaga.
**Hogyan működne:** `objective.type: "CRAFT_RECIPE"` + `objective.recipe-id` a
`ProfessionRecipeCatalog` azonosítóira mutatva. A `ProfessionRecipeManager` craft-hook-jába
(ami már tudja, melyik recept készült el) egy quest-progressz-hívás kerül, ugyanúgy ahogy a
KILL_MOBS a kill-listenerbe van bekötve.
**Miért jó:** A szakma-küldetéseket (mestermunka-lánc, J15) pontosan egy recepthez lehet
kötni ("készítsd el a Mesteracél Kardot"), nem csak "készíts 5 vas valamit".
**Építőkövek:** `ProfessionRecipeCatalog`, `ProfessionRecipeManager`, `QuestManager`.
**Buktatók:** Recept-id-k átnevezés/verzió-frissítéskor törhetnek — a builder validáljon a
katalógus ellen, mint most a materials mezőnél.

### J4. Párbaj-győzelem objektíva (WIN_DUEL)
🟢 • **Érték:** ⭐⭐

**Mi ez:** "Győzz le valakit egy tétmentes (vagy tétes) párbajban" típusú objektíva, a B2
duel-rendszer győzelem-eseményére kötve.
**Hogyan működne:** `objective.type: "WIN_DUEL"` + `objective.count`. A B2 duel-lezáró
kódjában (ahol a győztes eldől) egy `QuestManager.forEachActive(winner, "WIN_DUEL", ...)`
hívás, a KILL_PLAYERS mintájára — csak beleegyezéses meccsre számít, nem világ-PvP-re.
**Miért jó:** A duel/aréna-liga (B2, B41) köré story-réteget lehet húzni ("bajnokság-előd
küldetés: nyerj 3 párbajt") pénz-semlegesen.
**Építőkövek:** B2 duel-rendszer (előfeltétel — ez az ötlet a B2 után értelmezhető),
`QuestManager.forEachActive`.
**Buktatók:** B2-től függ, önmagában nem indítható — a sablon jelezze a listában "B2 után".

### J5. Esemény-túlélés objektíva (SURVIVE_EVENT)
🟡 • **Érték:** ⭐⭐

**Mi ez:** "Éld túl a vérholdat/inváziót anélkül, hogy meghalnál" típusú objektíva egy aktív
világeseményhez kötve.
**Hogyan működne:** `objective.type: "SURVIVE_EVENT"` + `objective.event` (pl. `BLOOD_MOON`,
`INVASION`) + opcionális `objective.no-death: true`. A megfelelő esemény-manager start/end
hook-jába (pl. `BloodMoonManager`) egy résztvevő-lista kerül; a `PlayerDeathEvent` a
résztvevőt kiveszi (kudarc), a manager end-eseménye a bennmaradókat teljesíti.
**Miért jó:** A világeseményeket (D1, B33 végítélet-hét) egyéni cél-réteggel gazdagítja anélkül,
hogy magát az eseményt módosítaná — csak megfigyeli.
**Építőkövek:** a világesemény-managerek start/end callback-jei, `PlayerDeathEvent`.
**Buktatók:** Az esemény hossza változó lehet (véletlen időzítés) — a küldetés-leírásban ne
ígérjünk fix percet, csak "amíg az esemény tart".

### J6. Sürgős küldetések (idő-limit bónusszal) `[TOP]`
🟢 • **Érték:** ⭐⭐⭐

**Mi ez:** Időkorlátos küldetés-variáns: ha a normál céljait X percen belül teljesíted,
extra jutalom jár; lejárat után a quest simán megmarad (nem büntet), csak a bónusz vész el.
**Hogyan működne:** `urgent: true` + `urgent-minutes: 15` + `urgent-bonus-currency`/
`urgent-bonus-item` mező a quest-sémában. Elfogadáskor egy `deadline`-timestamp PDC-kulcs
íródik; teljesítéskor a `complete()` összeveti az aktuális idővel a fix jutalom mellé. A
HUD quest-tracker (A20) opcionálisan hátralévő időt is mutathat.
**Miért jó:** Alacsony munkával ad adrenalin-elemet a megszokott quest-körnek, retention-re jó
(gyakori bejelentkezés = több esély elkapni egy sürgőset), és nem büntet, csak jutalmaz.
**Építőkövek:** `QuestManager` accept/complete, A20 tracker (opcionális UI).
**Buktatók:** A szerver-oldali óra és a kliens-visszajelzés szinkronban legyen — ha a HUD
késik, a játékos igazságtalannak érzi a lekésett bónuszt.

### J7. Rejtvény-küldetések (koordináta-nyomok / jelkép-keresés)
🟡 • **Érték:** ⭐⭐

**Mi ez:** SEQUENCE-láncú küldetés, ahol az egyes lépések nem nyíltan adott koordinátát,
hanem egy rejtvényt/nyomot mutatnak (dialógus-szöveg, tábla, jelkép-item), és a játékosnak
kell kitalálnia a következő helyszínt.
**Hogyan működne:** Nem igényel új objektíva-típust — a meglévő VISIT_TERRITORY/TALK_TO_NPC
láncot SEQUENCE módban, `dialogue.give` szövegben rejtett nyommal ("a törött torony
árnyékában, ahol a nap delelőn áll…") és opcionális `objective.description` felülírással,
ami NEM árulja el a pontos célt (csak a HUD-on "???" jelenik meg, amíg oda nem érsz). Az
admin quest-builder GUI-ba egy "rejtett leírás" checkbox kerülhet.
**Miért jó:** A story-orientált játékosoknak felfedezés-élményt ad a világ tényleges
bejárásával, nem csak a HUD-nyíl követésével — jól illik a titkos helyekhez (D8).
**Építőkövek:** meglévő SEQUENCE-lánc + dialógus-mező, `QuestBuilderGUI`.
**Buktatók:** Túl nehéz rejtvénynél a játékos elakad és feladja a láncot — legyen egy
opcionális "súgás" fokozat (pl. 10 perc után a HUD mégis megmutatja a célt).

### J8. Csoportos quest (party-közös progressz)
🟡 • **Érték:** ⭐⭐⭐

**Mi ez:** Egy quest-variáns, amit party-ban vesztek fel, és a progressz NEM egyénenként,
hanem a teljes party-ra összesítve halad (bárki öl/gyűjt, mindenkinek számít).
**Hogyan működne:** `party-shared: true` mező. Elfogadáskor a `PartyManager` aktuális
tagsora bekerül a quest PDC-metaadatába (snapshot, nem élő referencia); a progressz-írás a
KILL_MOBS-stílusú listenerekben a snapshot MINDEN tagjának PDC-jét frissíti — Folia miatt
tagonként a saját régió-szálukra hop-olva (`target.getScheduler().run`).
**Miért jó:** A közösségi célok (frakció-szintű) és az egyéni quest között hiányzik a
"kis csoport együtt" réteg — a party-játékot (5 fő) ad hoc story-célhoz köti.
**Építőkövek:** `PartyManager`, `QuestManager` progressz-írók, a közösségi célok mintája
(shared counter).
**Buktatók:** Party-váltás/kilépés közben a snapshot és az élő tagság szétcsúszhat — a
teljesítés-ellenőrzésnél mindig a SNAPSHOT-ot nézzük, ne az aktuális partyt.

### J9. Fejezet-rendszer (szezononkénti story-ív)

→ **Átkerült a lore-kiemelt válogatásba:** [L-lore-kiemelt.md](L-lore-kiemelt.md)

### J10. Döntés-következmény flagek (elágazó dialógus emlékezik)
🟡 • **Érték:** ⭐⭐⭐

**Mi ez:** A meglévő `dialogue.choices` válasz-elágazás ne csak a KÖVETKEZŐ questet döntse
el, hanem egy tartós "döntés-flaget" is hagyjon, amit KÉSŐBBI, akár más láncbeli questek is
olvashatnak.
**Hogyan működne:** `dialogue.choices.<n>.set-flag: "megkimelte_a_foglyot"` mező; a flag egy
egyszerű PDC boolean-kulcs (`quest_flag_<id>` névtérrel). Új accept-feltétel:
`requires-flag` / `requires-not-flag`, ugyanabban a szűrő-láncban mint a `requires-quest`.
**Miért jó:** Ez a story-elágazás valódi ereje — enélkül a választás csak azt dönti el, MI
JÖN LEGKÖZELEBB, flag-gel viszont a 10. questben is számíthat, mit tettél a 2.-ban (l.
"több befejezés" J11).
**Építőkövek:** `dialogue.choices`, `QuestManager` requires-* szűrő, PDC.
**Buktatók:** A flag-nevek szabad szöveg — admin-builderben kötelező autocomplete/validálás
kell, különben elgépeléssel néma, soha nem teljesülő feltételek keletkeznek.

### J11. Több befejezés jutalom-variánssal
🟡 • **Érték:** ⭐⭐

**Mi ez:** Egy story-lánc záró küldetése a korábbi döntés-flagek (J10) alapján eltérő
befejezést (dialógus-szöveg + jutalom-variáns) ad — ugyanaz a quest-id, más kimenet.
**Hogyan működne:** A záró questen `reward-variants` lista, mindegyikhez `condition-flag` +
saját `rewards` blokk; a `complete()` a legelső illeszkedő variánst választja, alapértelmezés
a lista végén. A dialógus `complete`-szövege is variánsonként külön adható.
**Miért jó:** A story-játékosoknak valódi súlyt ad a korábbi választásoknak (nem csak
kozmetikai szövegkülönbség), és admin-oldalról egy questbe sűríthető, amit különben 3 külön
questként kellene fenntartani.
**Építőkövek:** J10 flagek, `QuestManager.complete()`, `QuestBuilderGUI` rewards-szerkesztő.
**Buktatók:** Több variáns = több karbantartási felület a buildernek — a `/quest admin`
szerkesztőbe listás UI kell, hogy ne YAML-t kelljen kézzel piszkálni.

### J12. Visszatérő NPC-k emlékezettel
🟡 • **Érték:** ⭐⭐⭐

**Mi ez:** Ugyanaz az NPC (pl. egy mentett falusi) hetekkel később ÚJ küldetéssel jelentkezik,
és a dialógusa hivatkozik a korábbi döntésedre (J10 flag).
**Hogyan működne:** Nem új mechanika, hanem konvenció: a `giver-npc` mező ugyanarra a
FancyNpcs-azonosítóra mutat több, egymástól időben távoli questnél; a `requires-flag`
(J10) szűri, hogy csak azok kapják meg, akik az adott döntést hozták, és a
`dialogue.give` szövege ágazik a flag szerint (két `dialogue` variáns, mint J11-nél).
**Miért jó:** Ez adja a "élő világ, emlékszik rád" érzést — a bestiárium/album (B21) és a
kaszt-story (B28) mellé egy KÖZÖS, karakter-vezérelt szál, ami hosszú távon köti a
játékost egy konkrét NPC-hez, nem csak egy questsorhoz.
**Építőkövek:** `FancyNpcsQuestBridge`, J10 flagek, meglévő aura-jelzés (arany/zöld).
**Buktatók:** Tartalom-tervezési fegyelmet igényel (ki kell találni, mikor "aktiválódik" az
NPC újra) — technikailag olcsó, kreatívan drága.

### J13. Megváltható ellenség-NPC
🟡 • **Érték:** ⭐⭐

**Mi ez:** Egy story-szál végén az "ellenség" (pl. egy korrupt kereskedő vagy áruló mester)
kétféleképp zárható: legyőzöd (KILL_MOBS-szerű objektíva rá) VAGY egy dialógus-választással
(J10) megváltod — utóbbi esetben a végén szövetségessé válik (J12 visszatérő NPC-ként).
**Hogyan működne:** A záró quest SEQUENCE-ben elágazik: `dialogue.choices` egyik ága
`objective.type: "KILL_MOBS"` az NPC-hez rendelt egyedi mobra (custom-name/PDC-tag alapú
azonosítás), másik ága azonnal `next`-tel egy "megváltás" questre ugrik harc nélkül.
**Miért jó:** Morális választást ad, ami nem csak szöveg, hanem játékmenet-elágazás — a
"legyőzöm vagy megmentem" klasszikus RPG-döntést hozza be alacsony extra munkával a meglévő
elemekből.
**Építőkövek:** J10 flagek, meglévő KILL_MOBS + dialógus-elágazás, J12.
**Buktatók:** Az egyedi "boss" mob spawn/despawn-kezelése (ne maradjon árván, ha a játékos
kilép a megváltás-ág mellett) — takarítást igényel, mint bármely quest-spawnolt entitás.

### J14. Hírnév-questek (B29-hez)
🟢 • **Érték:** ⭐⭐

**Mi ez:** Amikor a B29 NPC-reputáció rendszer megvalósul, a hírnév-szint LÉPCSŐIT
küldetés-teljesítéssel (nem csak passzív pontgyűjtéssel) is meg lehessen ugrani — pl. egy
egyszeri "bizalom-próba" quest az NPC-nél, ami azonnal a következő hírnév-sávra tol.
**Hogyan működne:** `rewards.reputation.<npc-id>: <amount>` mező a rewards blokkban (a
`rewards.class-xp`/`currency` mintájára), amit a `complete()` a B29 reputáció-managerének
ad át. Nem helyettesíti a passzív gyűjtést, csak felgyorsítja.
**Miért jó:** A B29-nek questszerű mérföldkövet ad ("a Kovácsmester próbája") a lassú,
észrevehetetlen pontozgatás mellé — motivációsan kézzelfoghatóbb.
**Építőkövek:** B29 (előfeltétel), `QuestManager` rewards-keret.
**Buktatók:** B29-től függ, önmagában nem indítható.

### J15. Mestermunka quest-sor (szakma 50. szint → I-kategória)
🟡 • **Érték:** ⭐⭐⭐

**Mi ez:** Amikor egy szakma eléri az 50. szintet, a `ProfessionManager` egy egyedi,
egyszeri "mestermunka" küldetést nyisson (pl. "kovácsold meg a Mesteracél Pengét") a
CRAFT_RECIPE objektívával (J3), amiért egyedi cím/kozmetika jár.
**Hogyan működne:** A `ProfessionManager` szint-lépés hook-jába (ahol most a szint-üzenet
megy) egy `QuestManager.autoStart(player, "mestermunka_<szakma>")` hívás kerül — hasonlóan
az onboarding auto-indításhoz (A5), csak szint-triggerelve, nem első belépéskor. A quest
SEQUENCE, végén J3 CRAFT_RECIPE + a recept-alapanyag beszerzése (COLLECT_ITEMS).
**Miért jó:** A szakma-progressziónak (I-kategória, recept-katalógus) most nincs
"csúcspont-pillanata" — ez ad egy ünnepelt záró-momentumot az 50. szinthez, ahogy a
kaszt-story (B28) a 25. szinthez.
**Építőkövek:** `ProfessionManager`, J3 CRAFT_RECIPE, A5 auto-start minta.
**Buktatók:** Szakmánként külön tartalom-írás (recept-választás, dialógus) — a keret olcsó,
a 8+ szakmára skálázott tartalom nem.

### J16. Heti napi-quest kombó bónusz
🟢 • **Érték:** ⭐⭐

**Mi ez:** Ha egy héten (hétfő–vasárnap) összesen 5 KÜLÖNBÖZŐ napi küldetést teljesítesz, egy
extra "heti láda" jutalom jár — a napi rendszer fölé húzott láthatatlan meta-cél.
**Hogyan működne:** A `DailyQuestManager` teljesítés-hookjába egy heti számláló (PDC,
`weekly_daily_count` + `weekly_reset_timestamp`, a szezon-scheduler heti-tick mintájára
nullázva) — 5. teljesítésnél broadcast nélkül, csendes chat-üzenet + láda-item adása.
**Miért jó:** Olcsó módja, hogy a napi questek (amik önmagukban gyorsan unalmassá válnak)
heti visszatérési ritmust is generáljanak — a heti megbízásokkal (B1) párhuzamosan, de
attól függetlenül (más pool, más ritmus-logika).
**Építőkövek:** `DailyQuestManager`, heti reset-tick (a szezon-scheduler mintája).
**Buktatók:** A "különböző" feltétel pontos definíciója (napi ID vagy típus szerint) —
egyértelműsíteni kell, nehogy ugyanazt a napi questet 5x felvéve átverhető legyen (ha
egyáltalán ismételhető ugyanaznap — alapból nem az).

### J17. Quest-sablonok a builderhez
🟢 • **Érték:** ⭐⭐

**Mi ez:** A `/quest admin create` ne üres questet hozzon létre, hanem a `QuestBuilderGUI`-ban
válaszható "sablonokból" induljon (pl. "egyszerű vadászat", "SEQUENCE gyűjtő-lánc",
"NPC-dialógusos story-lépés") előre kitöltött mezőkkel.
**Hogyan működne:** Egy statikus sablon-lista (kód vagy `quest-templates.yml`) mindegyike egy
részleges quest-szekció (objective.type, objectives-mode, üres dialógus-placeholder); a
GUI-ban "Sablonból" gomb a kiválasztott sablont másolja az új quest alap-mezőibe, amit az
admin utána a meglévő `EDITABLE_FIELDS`-szerkesztővel finomít.
**Miért jó:** A quest-builder ma mezőnként kézi (`/quest admin set <mező> <érték>`) — a
sablon a gyakori mintákat (pl. "napi rotációs vadászat") pár kattintásra rövidíti, kevesebb
elgépelt objective.type-ot eredményez.
**Építőkövek:** `QuestBuilderGUI`, `QuestManager.EDITABLE_FIELDS`, `OBJECTIVE_TYPES` validáció.
**Buktatók:** A sablonok karbantartása extra munka minden új objektíva-típusnál (J1–J5) —
érdemes a sablon-listát ugyanabban a PR-ban bővíteni, amiben az új típus landol.

### J18. Quest-statisztika (dobási arány, C1 mintájára)
🟢 • **Érték:** ⭐⭐⭐

**Mi ez:** Számláló: melyik questet hányan veszik fel, hányan fejezik be, és hányan hagyják
abba (`/quest abandon` vagy inaktivitás) — `/icesmp quest report` táblázat, a C1
spell-statisztika mintájára.
**Hogyan működne:** Három számláló quest-id-nként (memóriában, napi YAML-dumppal a C1
mintáját követve): `accepted`, `completed`, `abandoned` — az `accept()`, `complete()` és
`abandon()` metódusokba egy-egy sor. A jelentés befejezési arányt (%) is számol, hogy
kilógjon, melyik questnél magas az elhagyási ráta (tervezési hiba jele: túl nehéz, túl
hosszú, vagy elakadó lánc).
**Miért jó:** A tartalom-tervezés (különösen a story-láncok, J9) most vak — ez adja meg az
ADAT-alapú visszajelzést, melyik quest kell újratervezni, pont ahogy a C1 a spell-balansznak.
**Építőkövek:** C1 mintája (számláló + napi dump + report parancs), `QuestManager`
accept/complete/abandon hook-jai.
**Buktatók:** Az `abandon` csak az explicit lemondást méri — a "sosem fejezi be, de nem is
mondja le" esethez időzített (pl. 30 napos) "elhagyottnak tekintve" jelzés is kellene.

### J19. Quest-lánc előnézet a naplóban
🟢 • **Érték:** ⭐

**Mi ez:** A `/quest log` "Felvehető"/"Aktív" fülén a quest-csempe lore-jában jelenjen meg,
hova vezet a lánc (a `next` mezőn/`requires-quest`-en át) — "Ez elvezet: A Harcos Mestere →
A Harcos Próbája".
**Hogyan működne:** A `QuestLogGUI` csempe-építésekor a `next` mezőt (és rekurzívan annak
`next`-jét, max 2-3 mélységig, ciklusvédelemmel) egy rövid lore-sorba fűzi.
**Miért jó:** Most a játékos csak lépésről lépésre látja a láncot, nem tudja előre, "megéri-e"
belevágni egy hosszú SEQUENCE-be — ez a `PLAYER_GUIDE.md`-t helyettesíti in-game infóval.
**Építőkövek:** `QuestLogGUI`, meglévő `next`-lánc mező.
**Buktatók:** Ciklikus `next`-hivatkozás (admin-hiba) végtelen ciklust okozhatna — kötelező
mélység-limit és látott-id-halmaz kell a rekurzióhoz.

### J20. Frakció-váltó döntés-quest (defekció)
🔴 • **Érték:** ⭐⭐

**Mi ez:** Egy ritka, nagy tétű story-quest, aminek végén a játékos dönthet: marad a
frakciójában, VAGY egy komoly áldozat (pénz, hírnév-veszteség J14, cím-elvesztés) árán
átléphet egy másikba — nem sima `/faction join`, hanem narratívan indokolt választás.
**Hogyan működne:** `rewards.faction-change: true` a záró dialógus-választásnál (J10 flag-
gel párban); a `complete()` a `FactionManager` frakcióváltó API-ját hívja meg, ha a
játékos ezt az ágat választotta. Erős korlátozás kell: cooldown (pl. szezononként 1x), és
csak akkor ajánlható fel, ha a jelenlegi frakciójában nincs vezető tisztsége (király).
**Miért jó:** A frakció-rendszernek ma nincs "story-indokolt" váltási útja, csak admin-
kényszer vagy új karakter — ez ad egy ritka, emlékezetes döntési pontot minden szezonban.
**Építőkövek:** `FactionManager`, J10 flagek, J14 hírnév (opcionális ár).
**Buktatók:** Frakció-egyensúlyra (4 frakciós meta) hatással lehet — mindenképp cooldown +
esetleg admin-jóváhagyás kell, nehogy tömeges "meta-váltásra" ösztönözzön.

### J21. Választható jutalom-variáns
🟢 • **Érték:** ⭐⭐

**Mi ez:** Egyes questek teljesítésekor a játékos GUI-ból választhat 2-3 jutalom közül
(pl. "pénz VAGY egy ritka item VAGY szakma-XP"), nem automatikusan kapja mind/az elsőt.
**Hogyan működne:** `rewards.choice: true` + `rewards.options` lista (mindegyik saját
class-xp/currency/items blokkal) a quest-sémában. A `complete()` ilyenkor NEM ad azonnal
jutalmat, hanem egy kis választó-GUI-t nyit (a `QuestLogGUI` holder-mintájára); a
kiválasztás után íródik a tényleges jutalom.
**Miért jó:** Alacsony munkával ad választás-érzést minden végjáték-questnél (nem csak a
nagy story-elágazásoknál, J11) — a játékos a saját build-jéhez illő jutalmat viheti.
**Építőkövek:** `QuestManager.complete()`, GUI-minta (dedikált holder + slot-akció).
**Buktatók:** Ha a játékos kilép a választó-GUI-ból döntés nélkül, a quest ne ragadjon
"félig teljesítve" állapotban — a `close` eventre alapértelmezett (első) opciót kell adni.

### J22. Lánc-checkpoint / vészreset admin-eszköz
🟢 • **Érték:** ⭐⭐

**Mi ez:** Admin-parancs, ami egy elakadt SEQUENCE-lánc adott lépésénél "beragadt" játékost
egy konkrét lépésre állít vissza (előre vagy hátra), anélkül hogy a teljes questet törölni
kellene.
**Hogyan működne:** `/quest admin setstep <player> <questId> <index>` — közvetlenül írja a
`objectiveProgressKey`/lépés-PDC-t a megadott indexre. Naplózva (admin audit, C7 mintája),
mert állapot-manipuláció.
**Miért jó:** Az NPC-kihelyezés-függő láncok (mester-próbák, ESCORT_NPC J1, EXPLORE_TERRITORY
J2) könnyen "beragadnak", ha egy NPC/pálya hiányzik vagy hibás — ma csak `abandon` +
újra-felvétel van, ami SEQUENCE-nél visszaveti az elejére. Ez a support-eszköz percek alatt
old fel egy elakadt játékost kézzel.
**Építőkövek:** `QuestManager` belső progressz-kulcsok, C7 admin audit-log (ha megvan).
**Buktatók:** Csak admin-jog, mert állapotot közvetlenül ír — véletlen célpont-index
hibás/nem-létező lépésre állíthatja a questet, validálni kell a lépésszám ellen.

### J23. Idő-alapú NPC-kínálat (éjjel/nappal más quest)
🟢 • **Érték:** ⭐

**Mi ez:** Egy NPC (pl. egy orgazda-jellegű mellékszereplő) csak ÉJJEL kínáljon bizonyos
questet — nappal más (vagy semmilyen) ajánlata van ugyanannál az NPC-nél.
**Hogyan működne:** `objective`-től független `availability: "NIGHT"`/`"DAY"` mező a
questen, amit a napi NPC-rotáció (a `rotation-group`/`rotation-daily-count` melletti) szűrő
néz meg elfogadás- és ajánlat-listázáskor (`world.getTime()` alapján, region-lokálisan az
NPC világán).
**Miért jó:** A napi NPC-rotáció (meglévő) mellé egy MÁSODIK dimenziót ad a "miért érdemes
visszajönni" érzésnek — összecseng a napszak-üdvözlő NPC-kkel (D7) és a titkos kereskedővel
(B40), olcsó tartalom-változatosság.
**Építőkövek:** meglévő NPC-rotáció (`rotation-group`), D7 napszak-infra.
**Buktatók:** Az időzóna/world-time ellenőrzés régió-lokális legyen (ne globális óra), mert
Folián világ-idő lekérdezés is a világ region-szálához kötött.

### J24. Quest-adta cím jutalom (B22-hez kötve)
🟢 • **Érték:** ⭐⭐

**Mi ez:** Egyedi, story-jelentőségű questek (mester-próba, vezeklés-lánc, mestermunka J15)
záró jutalma legyen egy KIZÁRÓLAG questből nyerhető cím (B22 title-rendszer), ne csak
kaszt-XP/pénz.
**Hogyan működne:** Ha a B22 címrendszer elkészül: `rewards.title: "sárkányölő"` mező a
rewards blokkban (a `rewards.unlock-spell` mintájára), amit a `complete()` a B22 cím-
managerének ad át felold(ás)ra.
**Miért jó:** A jutalom-paletta ma XP/pénz/item — a cím pénz-semleges presztízs-jutalom,
pont a legemlékezetesebb questekhez illik (nem napi rutinhoz), és a chat-prefixben
állandó emlék marad a teljesítésről.
**Építőkövek:** B22 (előfeltétel), `QuestManager` rewards-keret.
**Buktatók:** B22-től függ, önmagában nem indítható; cím-id-ket a builderben validálni kell
a B22 katalógus ellen, mint a `rewards.unlock-spell`-nél a spell-katalógus ellen.

---

➡️ Kapcsolódó: [A) Meglévő mechanika átdolgozása](A-polish.md) • [Ötlettár-index](../IDEAS.md)
