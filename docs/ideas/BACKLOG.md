# Ötlet-backlog (konszolidált)

Ez a fájl a `docs/ideas/` 15 korábbi ötlet-fájljának (A–O) **NYITOTT** tételeit egyesíti egyetlen
tömör backlogba — a már KÉSZ tételek kimaradtak, azok a git-történetben és a forrás-fájlokban
élnek tovább. Dátum: 2026-07-22.

Jelölés: 🟢/🟡/🔴 = munka (kicsi/közepes/nagy), ⭐–⭐⭐⭐ = becsült érték, `[TOP]` = ajánlott kör.

---

## A — polish

- **A17** Teljes HP-rendszer átdolgozása — kaszt-HP-profilok, healthScale-normalizálás, harcon-kívüli regen, sebzés-újrahangolás, pajzs-egységesítés. Tulaj kérésére, KÜLÖN nagy körben, nem ez a hullám. 🔴⭐⭐⭐
- **A18** Spell-loadoutok — A4 kedvenc-PDC-re épülő 2-3 elmenthető spell-készlet (PvP/farm/boss) gyors váltással. 🟢⭐⭐
- **A19** Kombó-jelzések a spellkönyvben — lore-sor jelzi, ha a spell kombó-pár/-lánc tagja. 🟢⭐
- **A20** Quest-tracker a HUD-on — követett quest objektíva-állása állandó oldalsáv-szekció. 🟡⭐⭐
- **A21** Halál-pont visszajelzés — halál-koordináta kiírás + rövid ideig élő iránytű-jelzés. 🟢⭐⭐
- **A22** Ranglisták bővítése — A15 statokból (K/D, mob-kill, cast, quest) új `/leaderboard` kategóriák. 🟢⭐
- **A23** NPC-dialógus polish — hang, írógép-effekt, folytatás-jelzés a quest-dialógusokhoz. 🟢⭐
- **A24** Szakma-recept kedvencek + keresés — recept-katalógus GUI kereső + csillagozás. 🟢⭐
- **A25** Spell-adatlap a spellkönyvben — jobb-katt élő, mastery-szorzott számokkal bővebb infólap. 🟢⭐⭐
- **A26** Élő cooldown a spellkönyvben — csempe-lore mutatja a hátralévő cooldownt. 🟢⭐
- **A27** Okos gyógyítás (party-célzás) — sneak-cast healer automatikusan a legsebzettebb párttagra megy. 🟡⭐⭐⭐
- **A28** Menü-badge-ek — `/menu` csempéken jelzés, ha van teendő (talentpont, napi quest, lejárt piaci tétel). 🟡⭐⭐
- **A29** Elit/boss mobok vizuális megkülönböztetése — névtábla szín/szimbólum (♦/☠). 🟢⭐
- **A30** Cast-hiba üzenetek egységesítése — közös ikonos formátum minden cast-elutasításnál. 🟢⭐
- **A31** Frakció-infó oldal passzívákkal — `/faction info` kiírja a tényleges passzíva-értékeket configból. 🟢⭐
- **A32** Színvak-barát HUD-mód — `/hud colorblind`, szimbólum-alapú sávok. 🟢⭐
- **A33** Katalizátor-skinek (CMD) — kasztonkénti egyedi CustomModelData a katalizátoron. 🟢⭐
- **A34** Rövid, mobil-barát üzenet-mód — `/hud compact`, rövidített HUD/action-bar szövegek. 🟢⭐
- **A35** Katalizátor akció-bar sáv finomítás — erő-csík dinamikus szín (zöld→sárga→piros). 🟢⭐⭐
- **A36** Spellbook keresőmező — anvil/chat-input szöveges spell-keresés. 🟢⭐
- **A37** Menü-breadcrumb és „vissza" konzisztencia — egységes útvonal-jelzés + vissza-gomb minden almenüben. 🟢⭐
- **A39** Inventory-rendezés gomb — sneak+jobb-katt/parancs rendezi a hátizsákot kategória szerint. 🟢⭐
- **A40** Spell-cast hangkönyvtár egységesítés — központi hangerő/pitch-tábla kaszt+sebzés-kategória szerint. 🟢⭐
- **A41** Teleport-becsapódás vizuális jelzés — érkezéskor partikel+hang minden teleport-spellnél. 🟢⭐
- **A42** Profil-GUI összefoglaló fül — `/profile` „Áttekintés" csempe minden fő adattal egy lapon. 🟡⭐⭐
- **A63** `/ping` parancs — saját/más ping színkódolva a tablist-snapshotból. 🟢⭐
- **A64** Animált oldalsáv-cím — `{anim:<név>}` támogatás a HUD sidebar címéhez. 🟢⭐
- **A65** AFK-zóna vizuális perem — halvány partikel-határ az AFK-jutalom-zónának. 🟢⭐
- **A66** Ritka crate-nyeremény broadcast — alacsony esélyű jutalomnál szerver-broadcast. 🟢⭐⭐
- **A67** Kattintható stat-kártya a chatben — `/stats` megosztható hover-kártyaként. 🟡⭐⭐
- **A68** Halál-összegző hover-részletek — death recap sorok hoverre gyilkos kaszt/szint/KD adatot mutatnak. 🟢⭐
A-polish összefoglaló: A1–16, A38(→L), A43–62, A69–72 nagyrészt implementálva — részletek a git-történetben.

## B — mechanika

- **B1** `[TOP]` Heti Királyi Megbízások (battlepass-lite) — frakciónkénti heti feladatlista pontokért, kassza-jutalom + top-tag buff/kozmetika. 🟡⭐⭐⭐
- **B2** Duel/párbaj-rendszer téttel — `/duel <név> [tét]`, escrow-zárolt tét, győztes viszi (90/10 sink). 🟡⭐⭐⭐
- **B4** Pet-képességek — petenként egy aktív képesség (provokál/gyorsít/aura), szint-kapuval. 🟡⭐⭐
- **B5** Területi erőforrás-pontok — elfoglalható „lelőhely"-zónák, óránkénti nyersanyag/XP-buff a birtokos frakciónak. 🔴⭐⭐⭐
- **B7** Frakció-fejlesztési fa — kasszapénzből vásárolható tartós upgrade-ek (XP%, olcsóbb claim, gyorsabb regen). 🟡⭐⭐⭐
- **B8** Natív crate-rendszer — CrazyCrates-kiváltás saját loot-táblával és nyitás-animációval. 🟡⭐⭐
- **B9** Kozmetikák GUI-ból — részecske-nyom/kalap/halál-üzenet frakció-valutáért vagy szezon-jutalomból. 🟡⭐⭐
- **B10** Napi vadász-cél — naponta sorsolt elit mob koordináta-körzettel, első elejtő viszi a lootot. 🟢⭐⭐
- **B11** Ostromgépek a raidhez — telepíthető katapult/ballista, védmű-blokk törése. 🔴⭐⭐
- **B12** Királyi politika: adó/kincstár-döntések — `/faction policy tax|dividend|goal`. 🟡⭐⭐
- **B13** Piaci vételi megbízások (buy order) — fordított piac, vevő escrow-ba zár, bárki teljesítheti. 🟡⭐⭐
- **B14** Frakció-raktár — rang-alapú közös láda naplóval. 🟡⭐⭐
- **B16** Mentor-rendszer — veterán+új játékos pár, közös questekkel, pénz-semleges jutalommal. 🟡⭐
- **B17** Kaszt-próba arénák — hullám-túlélés aréna, kaszt-mester questlánc lépcsője. 🟡⭐⭐
- **B18** Térkép-híd (BlueMap/Dynmap) — territórium/claim-zónák kirajzolása webtérképen. 🟡⭐⭐
- **B20** Relikvia-reforge + paragon — ritka anyagból újrakovácsolás, max szint utáni paragon-pontok; csak késői fázisban érdemes. 🔴⭐⭐
- **B22** Címek (title-ök) — elérésekből/szezonból nyíló, chat-prefixbe tehető cím; ROADMAP-ütközés (LP-rang vs kozmetikai cím) egyeztetendő. 🟡⭐⭐
- **B23** Játékos-boltok (chest shop) — tábla+láda bolt claimen, fix áras adás-vétel offline is. 🔴⭐⭐⭐
- **B24** Bank-lekötés kamattal — lekötött pénz, lejáratkor a frakció-kincstárból fizetett prémium. 🟡⭐
- **B25** Heti lottó — jegy frakció-valutáért, heti sorsolás a befizetések egy részéből. 🟢⭐⭐
- **B27** Dungeon-affixek — heti rotálódó kihívás-módosítók a dungeonökhöz/világbosshoz/invázióhoz. 🟡⭐⭐
- **B28** Kaszt-story questlánc — kasztonként 5-8 lépéses story-küldetéssor a 25. szintű spec-választásig. 🟡⭐⭐⭐
- **B29** NPC-reputáció — nevezetes NPC-k felé hírnév-skála, kedvezmény/exkluzív recept. 🔴⭐⭐
- **B30** Háború-ablakok — raid csak megadott idősávban indítható + védett hétvége. 🟡⭐⭐⭐
- **B31** Zsoldos-tábla — király kasszából vérdíj-jellegű megbízások (raid-védelem, eszkort, célpont). 🟡⭐⭐
- **B32** Építőverseny-esemény — admin-kijelölt telkek, GUI-szavazás, kozmetika/cím-jutalom. 🟢⭐
- **B34** Lebomló sír (grave) halálkor — halálkor a cucc védett sír-blokkba kerül, X perc után szabad. 🟡⭐⭐
- **B36** Gyorsutazás-hálózat (útkövek) — admin-útkövek, aktiválás odautazással, táv-arányos díj. 🟡⭐⭐⭐
- **B37** Nyíl-műhely — craftolható speciális nyilak (horgony/jelző/robbanó). 🟡⭐⭐
- **B38** Mythic spell-variánsok — mastery 5 fölött módosult viselkedésű, drága spell-átlényegülés. 🔴⭐⭐
- **B39** Védmű-építés raid-védelemhez — barikád/lassító mező/riasztó-harang a védő oldalnak. 🟡⭐⭐
- **B40** NPC-kereskedőhajó / vándorkereskedő — heti limitált készletű ritka áru, frakció-valutáért. 🟡⭐⭐
- **B41** Aréna-liga (heti bajnokság, ELO) — B2/B17 fölé heti 1v1/2v2 liga ELO-val. 🟡⭐⭐
- **B43** Pet-tenyésztés — két max-szintű pet párosítható, utód örököl+mutáció; niche, alacsony prioritás. 🔴⭐
- **B44** Diplomácia: szövetség és fegyverszünet — király-szintű frakció-reláció döntés; csak 4+ frakciónál éri meg igazán. 🔴⭐⭐
- **B45** Torony-védelem invázió-mód — invázió-variáns a főváros kapujánál, közösségi védelem-számláló. 🟡⭐⭐
- **B46** Trófea-halak és horgász-dicsőség — ritka nevesített halak, ranglista, trófea-fal item. 🟢⭐
- **B47** Ereklye-expedíciók — heti 3-fázisú PvE mini-raid (mini-boss→ásatás→menekítés). 🟡⭐⭐
- **B48** Territórium-őrposztok — építhető őrtorony, idegen-belépés jelzés a frakciónak. 🟡⭐⭐
- **B49** Ideiglenes claim-vendégjog — időkorlátos, korlátozott build/interact jog nem-trust játékosnak. 🟢⭐⭐
- **B50** Claim-fejlődési szintek — aktivitás-alapú claim-evolúció kis QoL-bónuszokkal. 🟡⭐⭐
- **B51** Fiók-szintű meta-progresszió (veterán-fa) — kaszt-független, sosem nullázódó QoL-perkek. 🟡⭐⭐
- **B52** Fegyver-mesterség (item-szintű XP) — a fegyverhez kötött XP/bónusz, tárggyal együtt vész el. 🟡⭐⭐
- **B53** Tárgy-fúzió — két azonos-típusú ritka tárgy összeolvasztása, sikertelenség = mindkettő elég. 🟡⭐⭐
- **B55** Önkéntes nehézség-fogadalom — opt-in kockázat/jutalom mód (sebzés ki/be, extra XP/loot). 🟢⭐⭐
- **B56** Napi bejelentkezési sorozat (login streak) — egymást követő napi belépésért fokozódó jutalom. 🟢⭐⭐
- **B57** Heti kaszt-kihívás — kasztonkénti heti objektíva spec-specifikus jutalommal. 🟡⭐⭐
- **B58** Relikvia-őrzés territórium-buff — kiállított relikvia passzív buffot ad a frakcióterületnek. 🟡⭐⭐⭐
- **B59** Gyűjtögető pet-segéd — bizonyos petek eséllyel extra alapanyagot hoznak gyűjtögetésnél. 🟡⭐⭐
- **B60** Tárgy-szett szinergia — azonos tematikus szett egyidejű viselése kis passzív bónuszt ad. 🟡⭐⭐
- **B61** Közös frakció-munkaprojekt (közmunka) — tagok anyaggal finanszírozzák a fejlesztést, nem csak a király. 🟡⭐⭐
- **B62** Kölcsönös felszerelés-kölcsönzés — party/céh-tagok közti PDC-nyomon-követett bizalmi kölcsönzés. 🟢⭐
B-mechanika összefoglaló: B3, B6, B15, B19, B21, B26, B33, B35, B42, B54 KÉSZ — lásd L-lore-kiemelt.md.

## C — infra

- **C1** `[TOP]` Spell-használati statisztika — spell-cast számláló balansz-döntésekhez, `/icesmp balance report`. 🟢⭐⭐⭐
- **C2** Playtest-mód kapcsoló — admin globális cooldown/idő-kapu skálázás playteszthez. 🟢⭐⭐
- **C3** Gazdasági faucet/sink monitor — heti forrásonkénti pénz-keletkezés/megsemmisülés riport. 🟢⭐⭐
- **C4** Kill-reward/gazdaság szimulátor-parancs — durva becslés N óra átlagjátékra a configból. 🟡⭐
- **C5** Discord-webhook híd — nagy szerver-események (boss/raid/szezon/király) Discordra. 🟢⭐⭐⭐
- **C6** YAML-store integritás-őr + mentés — induláskori parse-próba, sérülésnél auto-visszaállítás backupból. 🟡⭐⭐
- **C7** Admin audit-log — minden admin-parancs végrehajtása naplófájlba. 🟢⭐⭐
- **C8** Edzőbábu (training dummy) — sebezhetetlen DPS-mérő céltábla-entitás. 🟢⭐⭐⭐
- **C9** Folia régió-teljesítmény riport — régiónkénti MSPT/entitásszám lista lag-kereséshez. 🟡⭐
- **C10** Config-diff parancs — élő config vs jar-default eltérések listázása. 🟢⭐
- **C11** Custom item-katalógus parancs — `/icesmp items`, kattintható „adj egyet" gombbal. 🟢⭐
- **C12** Játékos-inspektor — `/icesmp inspect`, teljes plugin-állapot egy parancsban support-diagnózishoz. 🟡⭐⭐
- **C13** Esemény-naptár config — cron-szerű, opcionális menetrend a világeseményekhez. 🟡⭐⭐
- **C14** Loot-szimulátor — N húzás szimulálása egy loot-táblából, eloszlás-kiírással. 🟢⭐
- **C15** Napi szerver-digest — éjféli log/Discord-összefoglaló a napi aktivitásról. 🟢⭐⭐
- **C16** YAML-séma verziózás (migrációs keret) — schema-version + induláskori upgrade-lánc. 🟡⭐
- **C17** Chat-szűrő + mute rendszer — kulcsszó-alapú szűrő és admin-némítás. 🟢⭐⭐
- **C18** AFK-kezelés — inaktivitás-figyelés, tab-list jelzés, opcionális jutalom-kizárás. 🟢⭐⭐
- **C19** Entitás-számláló riport — chunkonkénti/típusonkénti torlódás-riport. 🟡⭐
- **C20** Hopper/redstone-limit figyelő — küszöb feletti sűrűségnél proaktív riasztás. 🟡⭐⭐
- **C21** Playtime-tracking — összjátékidő-mérés játékosonként. 🟢⭐
- **C22** Heatmap-adatgyűjtés (halál/mozgás) — passzív adatgyűjtés világ-tervezéshez. 🟡⭐⭐
- **C23** Ütemezett restart-figyelmeztetés broadcast-lánccal — eszkalálódó figyelmeztetés tervezett restart előtt. 🟢⭐⭐
- **C24** Whitelist/szezon-onboarding automatizálás — önkiszolgáló whitelist-kérelem + auto-approve. 🟡⭐⭐
- **C25** Config-validátor bővítés — kereszt-referencia és tartomány-szabályok, on-demand riport. 🟢⭐
- **C26** Debug-mód spell-tracinggel — cast-onkénti részletes nyomkövetés fejlesztői diagnózishoz. 🟢⭐⭐
- **C27** `/icesmp version` + changelog ingame — build-verzió és changelog egy parancsban. 🟢⭐
- **C28** Dupe/anomália-riasztó — valós idejű gyanús egyenleg-ugrás/item-duplikáció riasztás. 🟡⭐⭐
- **C29** Játékos-jelentés rendszer (`/report`) — beépített report-sor admin-gombokkal. 🟢⭐⭐

## D — világ, hangulat, közösség

- **D2** Városi hirdetőtábla — player-generált apróhirdetés GUI-táblán, sink-díjjal. 🟢⭐⭐
- **D4** Hangulat-rétegek bővítése — új ambient hang/mikro-esemény (meteor-zápor, szentjánosbogár-raj). 🟢⭐
- **D5** Kocsma (social hub) ital-buffokkal — szakma-készítette italok rövid nem-harci buffal. 🟡⭐⭐
- **D6** Frakció-fanfárok és kürtjelek — nagy pillanatokhoz frakció-specifikus hangjel. 🟢⭐
- **D7** Napszak-üdvözlő NPC-k — fővárosi NPC-k napszak-függő odaszólása. 🟢⭐
- **D10** Szezon-ereklye vitrin — előző szezonok győztes trófeái kiállítva. 🟢⭐
- **D12** Piaci zsivaj-hangok — halk, folyamatos nyüzsgés-hang a piac körül. 🟢⭐
- **D13** Harangtorony órajelzés — napszakváltásnál haranghang+jelzés. 🟢⭐
- **D14** Közös vacsora-buff a kocsmában — D5 közösségi kiterjesztése, egyidejű fogyasztóknak bónusz. 🟢⭐⭐
- **D16** Csoportkép-pont — fővárosi „emlék-pont", csapat-jelenlétkor ünnepi jelenet+bejegyzés. 🟢⭐
- **D20** Szobor-oszlop saját statokkal — mérföldkövet elérő játékosok fej+tábla a fővárosban. 🟡⭐⭐
- **D21** Vendégkönyv a fővárosban — fizikai könyv-állvány, bárki üzenetet hagyhat. 🟢⭐
- **D22** Közösségi híd/út-építő projekt — blokk-adományozásos közös cél, látható végeredménnyel. 🟡⭐⭐
- **D23** Per-bióm ambient hangprofilok — bióm-specifikus hangkészlet az ambient-tick-hez. 🟢⭐
- **D24** Éjszakai fáklya-fény esemény — napnyugtakor söprő fény-részecske a városfal mentén. 🟢⭐
- **D25** Üzenetpalack / kívánságfal — eldobható item, ami más játékosnál landol később. 🟡⭐
- **D26** Időkapszula-esemény — szezononkénti beásható láda, következő szezonzáráskor nyílik. 🟡⭐⭐
D-világ összefoglaló: D1, D3, D8, D9, D11, D15, D17, D18, D19 KÉSZ — lásd L-lore-kiemelt.md.

## E — kaszt és specializáció

- **E2** Druida: évszak-hangolódás — világ-állapothoz igazodó kis passzív spell-szorzó. 🟢⭐
- **E3** Harcos: pajzsfal állás — tank-stance, mögötte állóknak sebzéscsökkentés. 🟡⭐⭐
- **E4** Szerzetes: sörfőzde-mesterség — Sörfőző spec exkluzív D5 kocsma-ital-készítés. 🟢⭐⭐
- **E5** Boszorkánymester: lélek-alku — kockázat/jutalom mini-mechanika (spell-erő vs HP-cost, bűn). 🟡⭐
- **E6** Íjász: sólyom-felderítő — ideiglenes felderítő-társ, glowing-tag hostile célokra. 🟡⭐
- **E8** Harcos: ostromtörő fegyelem — Harcos-bónusz B11 ostromgép-kezelésnél + raid-Düh-regen. 🟢⭐⭐
- **E9** Íjász: tegez-mesterség — B37 speciális nyilak Íjász-exkluzívak/olcsóbbak. 🟢⭐⭐
- **E10** Orgyilkos: kombó-pont felhalmozás — másodlagos, alap-találatból gyűlő finisher-pont. 🟡⭐⭐⭐
- **E11** Orgyilkos: végső árnyék (execute-forma) — alacsony HP célponton látványos execute-branch. 🟢⭐⭐⭐
- **E12** Druida: alakváltás hang/partikel-aláírás — forma-specifikus érzék-jel formaváltáskor. 🟢⭐⭐
- **E13** Paplovag: eskü-töltés — blokkolt/elnyelt sebzés Szent Erővé alakul. 🟡⭐⭐⭐
- **E14** Paplovag: eskü-pár — Szentlélek+Megtorló spec csapat-kombó bónusza. 🟡⭐⭐
- **E15** Halállovag: rúna-pecsét kombó — Runikus Erő 3 rúna-típus (Vér/Fagy/Halál) egyensúlyaként. 🔴⭐⭐⭐
- **E16** Halállovag: fagyott vér — Fagylovag fagyasztása extra sebzést nyit Vérlovagnak. 🟡⭐⭐
- **E17** Halállovag: fagy/vér vizuális aláírás — egységes vér+fagy témájú partikel/hang. 🟢⭐
- **E18** Sámán: totem-hálózat — 2-3 egyidejű elem-totem, konvergencia-bónusszal. 🟡⭐⭐⭐
- **E19** Sámán: szellemvezető — totemek a párttagok petjeire is hatnak. 🟡⭐⭐
- **E20** Sámán: elemi egyesülés (4 totem ulti) — ritka, nagy AoE ha egyszerre mind a 4 elem-totem aktív. 🟡⭐⭐⭐
- **E21** Szerzetes: csi-áramlás — Ködszövő+Szélfutó spec csapat-kombó a Csi-erőforrásra. 🟡⭐⭐
- **E22** Pap: ima-visszhang — sikeres heal esély kritikus gyógyítás+Mana-visszatérítés. 🟡⭐⭐⭐
- **E23** Pap: fájdalom-megváltás — Fegyelem+Árnyék spec DoT-elszívás heal-kombó. 🟡⭐⭐
- **E24** Pap: szent/árny hang-aláírás — a két spec eltérő hang/partikel castoláskor. 🟢⭐
- **E26** Orgyilkos/Boszorkánymester: rettegés-lánc — két kaszt közötti CC-kombó (curse+félelem). 🟡⭐⭐
- **E27** Démonvadász: bosszú-fúria — Fúria elszenvedett sebzésből is töltődik. 🟡⭐⭐⭐
- **E28** Démonvadász: démon-forma — 100% Fúriánál rövid, erős átalakulás-ulti. 🔴⭐⭐⭐
- **E29** Démonvadász: vadászösvény — kaszt-exkluzív bónusz sinner-jelölésű célpontok ellen. 🟢⭐⭐
- **E30** Sárkányidéző: eszencia-egyensúly — Eszencia két-pólusú (Tűz/Élet) skálává alakítása. 🟡⭐⭐⭐
- **E31** Sárkányidéző: sárkánylehelet — magas Eszencia-polaritásnál csatorna-ulti kúp-AoE. 🔴⭐⭐⭐
E-kaszt összefoglaló: E1, E7, E25, E32 KÉSZ — lásd L-lore-kiemelt.md.

## F — gazdaság és kereskedelem

- **F1** Árfolyam-történet grafikon — napi snapshot-log + sparkline a valutaváltóban. 🟢⭐⭐
- **F2** Frakció-valuta árfolyam-fogadás — zéró-összegű spekulációs jegy (valuta fel/le). 🟡⭐⭐⭐
- **F3** Territóriumi vám — idegen frakció földjén magasabb piaci díj a birtokos kasszájának. 🟡⭐⭐
- **F4** Progresszív piaci díj — sávos, tranzakció-mérettel arányos ELÉG-díj. 🟢⭐
- **F5** Escrow-s adásvételi szerződés — egyedi tárgy csalás-védett P2P adásvétele. 🟡⭐⭐
- **F6** Részletfizetéses szerződés — F5 bővítése ütemezett részletfizetéssel, kötbérrel. 🟡⭐⭐
- **F7** Szállítási szerződés (futár-megbízás) — A→B áru-szállítás díjért, útközben rabolható. 🟡⭐⭐
- **F8** Raid-biztosítás — havi díjas frakció-biztosítás, vereségnél részleges kártérítés kasszából. 🟡⭐⭐⭐
- **F9** Napszámos piac — player-to-player fizetett munka-hirdetőtábla escrow-val. 🟡⭐⭐
- **F10** Rúna-anyag árupiac — B26 rúna-alapanyagoknak dedikált piaci fül/szűrő; B26 után. 🟢⭐⭐
- **F12** Kozmetika-árverés — admin-indított luxus-aukció, teljes befolyt összeg ELÉG. 🟢⭐⭐
- **F16** Kereskedő-céh hűségszint — B29 reputáció bővítése piaci forgalom alapján; B29 után. 🔴⭐⭐
- **F17** Aukció licit-sniper védelem — utolsó pillanatos licitnél lejárat-hosszabbítás. 🟢⭐⭐
- **F18** Aukció rezervár-ár — eladó rejtett minimumára, ha nem teljesül, tárgy visszajár. 🟢⭐
- **F19** Zálogház (item-fedezetű gyorshitel) — tárgy zálogba kasszapénzért, kamattal kiváltható. 🟡⭐⭐
- **F20** Frakció-kötvény — tagok kölcsönöznek a kasszának projektre, kamatos visszafizetéssel. 🟡⭐⭐
- **F21** Napi váltási limit — anti-arbitrázs napi keret a valutaváltásra. 🟢⭐
- **F22** Kereskedelmi szövetség/vámmentesség — B44 diplomácia gazdasági kiterjesztése; B44 után. 🟡⭐⭐
- **F23** Gazdasági index-tábla a fővárosban — C3 adatokból játékos-barát trend-hologram; C3 után. 🟢⭐⭐
- **F24** Fuvarozói piac — F7 futár-reputáció (értékelés, teljesített szállítás); F7 után. 🟡⭐
- **F25** Kereskedelmi szezon-verseny — szezonvégi forgalom-ranglista, sinkelt pénzből jutalom. 🟢⭐⭐
- **F26** Hadiadó — király időkorlátos vész-adója raid-finanszírozásra. 🟢⭐⭐
F-gazdaság összefoglaló: F11, F13, F14, F15 KÉSZ — lásd L-lore-kiemelt.md.

## G — PvP, frakció-háború és rivalizálás

- **G1** `[TOP]` Raid-célpont: zászlólopás mód — mozgó célpont-raid-variáns, üldözhető zászlóhordozóval. 🟡⭐⭐⭐
- **G2** Raid-célpont: kassza-feltörés fázisokkal — 3-fázisú mini-objektíva-lánc a kasszáért. 🟡⭐⭐⭐
- **G3** Ostromlétra/kötél mechanika — raid alatt telepíthető ideiglenes falmászó-eszköz. 🟢⭐⭐
- **G4** 3v3 skirmish-pont a territórium-határon — kis beleegyezéses csapat-PvP várólistával. 🟡⭐⭐⭐
- **G5** Határvillongás-esemény — időszakos, szentesített PvP-zóna két frakció határán. 🟡⭐⭐
- **G7** PvP-jelzős zóna extra loottal — mindig szabad PvP-zóna, cserébe jobb loot. 🟡⭐⭐
- **G8** Hadizsákmány-token rendszer — szentesített kill nem-item tokent ad, PvP-boltban váltható. 🟡⭐⭐
- **G9** Kill-streak jelzés szerénység-fékkel — csökkenő hozamú streak-bónusz snowball ellen. 🟢⭐⭐
- **G10** Heti rivális-frakció kijelölés — liga-állás alapján automatikus rivális-pár, extra pont. 🟡⭐⭐⭐
- **G11** Frakció-morál: sorozatvereség védő-buff — anti-snowball buff sorozatos raid-vereség után. 🟡⭐⭐
- **G12** Gyengébb frakció felzárkóztató mechanika — leszakadó frakciónak PvE XP/kill-bónusz. 🟢⭐⭐
- **G13** Füstjelzők/zászlók raid-jelölőnek — csapat-színű taktikai jelölő-item raid alatt. 🟢⭐⭐
- **G15** Ellátmány-vonal raid alatt — másodlagos taktikai célpont, elvágható utánpótlás-láda. 🟡⭐⭐
- **G17** Raid-előrejelzés — hirdetés után rövid mozgósítási ablak a védőnek felkészülésre. 🟢⭐⭐
- **G18** Kereszttűz-bónusz — 3+ frakciós összecsapás külön szabálykészlettel raid/skirmish-ben. 🟡⭐⭐
- **G19** PvP-tanonc mód — új játékosok rövid, önként megszakítható PvP-védelme. 🟢⭐⭐
- **G20** Frakció-hírszerzés: ellenség online-térkép — király/felderítő GUI, zóna-szintű, késleltetett adat. 🟢⭐
- **G21** Frakció-becsület-pontok — pénz-semleges PvP-presztízs szentesített győzelmekért. 🟢⭐⭐
- **G22** Menekülő-bónusz: utolsó állás — kritikus HP-ról sikeres menekülésért kis gyógyulás. 🟢⭐
- **G23** Frakció-zászló kihelyezhető trófeaként — raid-győzelem után dekoratív trófea a fővárosba. 🟢⭐
- **G24** Fegyvernyugvás utáni cooldown — ugyanaz a frakció-pár nem raidelheti egymást azonnal újra. 🟢⭐⭐
- **G25** Csapat-egyensúlyozó raid-jelentkezéskor — létszám-egyenlőtlenség figyelmeztetés, nem kényszer. 🟢⭐⭐
G-pvp összefoglaló: G6, G14, G16 KÉSZ — lásd L-lore-kiemelt.md.

## H — PvE, világesemények és végjáték

- **H1** `[TOP]` Vándorló világboss (nomád fenevad) — több napig kóborló boss, nyomot hagy, kockázat-skálázott loot. 🔴⭐⭐⭐
- **H3** Éjszakai ostrom a claimek ellen (opt-in) — invázió-jellegű önkéntes claim-védés. 🟡⭐⭐⭐
- **H4** Hullócsillag-mező gyűjtő-verseny — meteor-esemény raj-módban, ranglista-versennyel. 🟡⭐⭐
- **H5** Szörny-fészek felszámolás — táguló szörny-forrás struktúra, minél előbb rommá kell tenni. 🟡⭐⭐⭐
- **H6** Esemény-kaszkád — kihasználatlan világesemény tematikus követő eseményt indít. 🟢⭐⭐
- **H7** Csali-esemény (csapda-zsákmány) — kincs-esemény kockázatos ambush-variánsa. 🟢⭐⭐
- **H8** `[TOP]` Világboss add-fázis + interrupt-ellenőrzés — csatorna-speciál, ami megszakítható/addokkal jár. 🟡⭐⭐⭐
- **H9** Világboss enrage-timer — túl lassú harcnál végleges enrage-bónusz a bossnak. 🟢⭐⭐
- **H10** Heti mythic világboss-változat lockouttal — felturbózott heti boss, per-account lockout. 🔴⭐⭐⭐
- **H11** Boss-loot token-rendszer pity-számlálóval — boss-token bolt + garantált ritka drop N ölés után. 🟡⭐⭐
- **H12** Kevert fázis-mechanika — világboss fázisonként más archetípus speciálját kölcsönzi. 🟡⭐⭐
- **H13** Kooperatív boss-végrehajtás — alacsony HP-n több-játékosos „gyenge pont" finish-fázis. 🟡⭐⭐⭐
- **H15** Éjszakai elit-járőrök a vadonban — folyamatos, esemény-broadcast nélküli éjszakai veszély. 🟡⭐⭐
- **H16** Bióm-specifikus veszély-szintek — bióm-alapú mob-szint-módosító a táv-alapú skálázás mellé. 🟢⭐⭐
- **H17** Vadon-veszélytérkép — `/dangermap`, csak felfedezett veszélyeket mutató infólekérdezés. 🟢⭐
- **H18** `[TOP]` Nyilvános esemény-csoportok auto-partyval — súrlódásmentes ideiglenes csoport világesemény indulásakor. 🟡⭐⭐⭐
- **H19** Világ-cél: közös boss-gyengítés fázis — mythic-hét előtti közösségi cél gyengíti/javítja a bosst. 🟡⭐⭐⭐
- **H20** Mentőakció-esemény (NPC kimentése) — időkorlátos NPC-kiszabadítás őrzött helyszínen. 🟡⭐⭐
- **H21** SOS-jelzőrakéta — párt-mentes segély-jelzés item, közeliek iránytű-jelzést kapnak. 🟢⭐⭐
- **H22** `[TOP]` Heti kihívás-rotáció pontozással — meglévő világeseményekből heti pontozott feladat-készlet. 🟡⭐⭐⭐
- **H23** PvE ranglista-szezon — frakciós ligától független, egyéni PvE-szezon-pontszám. 🟡⭐⭐
- **H24** Elhagyott esemény automatikus takarítása — elakadt/üres világesemények force-despawnja. 🟢⭐⭐
- **H25** PvE mérföldkő-jutalmak a heti rotációhoz — H22 streak-jutalom egymást követő hetekért. 🟢⭐⭐
H-pve összefoglaló: H2, H14 KÉSZ — lásd L-lore-kiemelt.md.

## I — szakmák, gyűjtögetés és készítés

- **I1** `[TOP]` Mestermű-esély — kritikus minőségű craft, raritás-létrán egy fokkal feljebb kis eséllyel. 🟡⭐⭐⭐
- **I2** Ércfajta-mikrospecializáció 50. szinten — második, szűkebb szakma-fókusz-választás. 🟡⭐⭐
- **I3** Napi szakma-megrendelés NPC-től — napi 1-2 HAVE/CRAFT célt kiadó NPC extra XP-ért. 🟡⭐⭐⭐
- **I4** Szerszám-karbantartás és -kopás — láthatatlan élesség-mutató, köztes alapanyaggal karbantartható. 🟡⭐⭐
- **I5** Craft-sor / kötegelt gyártás — N darab egyszerre craftolható a recept-könyvből. 🟢⭐⭐
- **I6** `[TOP]` Ritka nagy érc-ér esemény jelzéssel — véletlen helyen nagy érc-lelőhely, közelítő broadcast. 🟡⭐⭐⭐
- **I8** Fadöntés-fizika Favágónak — magas szinten az egész fatörzs egyszerre dől ki. 🟡⭐⭐
- **I9** Fenntartható erdőgazdálkodás — csemete-visszaültetésért kis XP-bónusz. 🟢⭐
- **I10** Bányász-radar — aktiválható közeli-érc jelzés magas szintű/specializált Bányásznak. 🟡⭐⭐
- **I11** Craft-minijáték — időzített kattintás opcionális bónusz-minőségért mestermunkánál. 🟡⭐⭐
- **I12** Műhely-blokkok — claimre építhető szakma-állomás, közelségi craft-bónusszal. 🟡⭐⭐⭐
- **I13** Közös műhely a fővárosban — bérelt, díjas I12-szintű bónusz saját befektetés nélkül. 🟢⭐⭐
- **I15** Megrendelő-tábla — D2 hirdetőtábla szakma-megrendelés fül, kérés-vezérelt craft-piac. 🟡⭐⭐
- **I17** Fogyóeszköz szakma-buffok — köszörűkő/fenő olaj rövid nem-harci szakma-bónusszal. 🟢⭐⭐
- **I18** Recept-lánc — Kovács „nyers pengéjét" Alkimista finomítja, jobb végeredmény. 🟡⭐⭐⭐
- **I19** Recept-lánc — friss Gyógynövényész-termény extra bónuszt ad a Szakács receptjének. 🟢⭐⭐
- **I20** Halász csali-kotyvalék — Alkimista-termék, ami növeli a ritka/trófea hal esélyét. 🟢⭐
- **I21** Szakma-presztízs — 50. szint után önkéntes újrakezdés tartós, kicsi XP-szorzóért. 🟡⭐⭐
- **I23** Szakma-mester NPC-k — edzés/felgyorsítás fizetős XP-lökettel, napi limittel. 🟢⭐
- **I24** Szakma-ranglista havi verseny — havi resetes szakma-specifikus `/leaderboard` kategória. 🟢⭐
- **I25** Napi „hozott alapanyag" bónusz-ablak — napi első N esemény extra XP, csökkenő görbével. 🟢⭐
I-szakmák összefoglaló: I7, I14, I16, I22 KÉSZ — lásd L-lore-kiemelt.md.

## J — questek, story és progresszió

- **J1** Kíséret-küldetés objektíva (ESCORT_NPC) — élő NPC-kísérés A-ból B-be, elbukhat. 🟡⭐⭐⭐
- **J2** Több-területes felfedező objektíva (EXPLORE_TERRITORY) — N különböző territórium felkeresése egy questen belül. 🟢⭐⭐
- **J3** Recept-craftolás objektíva (CRAFT_RECIPE) — konkrét szakma-recept mint quest-cél. 🟢⭐⭐
- **J4** Párbaj-győzelem objektíva (WIN_DUEL) — B2 duel-győzelemre kötött objektíva-típus; B2 után. 🟢⭐⭐
- **J5** Esemény-túlélés objektíva (SURVIVE_EVENT) — világesemény túlélése questcélként. 🟡⭐⭐
- **J6** `[TOP]` Sürgős küldetések (idő-limit bónusszal) — időkorlátos extra jutalom, sosem büntet. 🟢⭐⭐⭐
- **J8** Csoportos quest (party-közös progressz) — párt egészére összesített haladású quest-variáns. 🟡⭐⭐⭐
- **J10** Döntés-következmény flagek — dialógus-választás tartós PDC-flaget hagy, később olvasható. 🟡⭐⭐⭐
- **J11** Több befejezés jutalom-variánssal — záró quest a döntés-flagek szerint eltérő kimenetet ad. 🟡⭐⭐
- **J12** Visszatérő NPC-k emlékezettel — ugyanaz az NPC hetekkel később új questtel, döntésre hivatkozva. 🟡⭐⭐⭐
- **J13** Megváltható ellenség-NPC — story-ellenfél legyőzhető VAGY dialógussal megválthatunk. 🟡⭐⭐
- **J14** Hírnév-questek (B29-hez) — egyszeri quest gyorsítja a hírnév-szint lépcsőt; B29 után. 🟢⭐⭐
- **J15** Mestermunka quest-sor — szakma 50. szintjén egyedi, egyszeri záró küldetés. 🟡⭐⭐⭐
- **J16** Heti napi-quest kombó bónusz — 5 különböző napi quest teljesítéséért heti láda. 🟢⭐⭐
- **J17** Quest-sablonok a builderhez — előre kitöltött sablonokból induló quest-létrehozás. 🟢⭐⭐
- **J18** Quest-statisztika (dobási arány) — accept/complete/abandon számláló, C1 mintájára. 🟢⭐⭐⭐
- **J19** Quest-lánc előnézet a naplóban — a quest-csempe lore-ja mutatja, hova vezet a lánc. 🟢⭐
- **J20** Frakció-váltó döntés-quest (defekció) — ritka, nagy tétű story-alapú frakcióváltás. 🔴⭐⭐
- **J21** Választható jutalom-variáns — questteljesítéskor GUI-ból választható 2-3 jutalom közül. 🟢⭐⭐
- **J22** Lánc-checkpoint / vészreset admin-eszköz — beragadt SEQUENCE-lánc lépésre-állítása. 🟢⭐⭐
- **J23** Idő-alapú NPC-kínálat — egy NPC csak éjjel/nappal kínál bizonyos questet. 🟢⭐
- **J24** Quest-adta cím jutalom (B22-hez) — kizárólag questből nyerhető cím-jutalom; B22 után. 🟢⭐⭐
J-quest összefoglaló: J7, J9 KÉSZ — lásd L-lore-kiemelt.md.

## K — lore-integráció

K-lore.md teljes egészében a L-lore-kiemelt.md-be költözött („A kódex beépítési terve, K1–K10"); mind KÉSZ.

## L — lore-kiemelt válogatás

L-lore-kiemelt.md mind a 49 tétele (K1–K10, Tier S/A/B: A38, B3, B6, B15, B19, B21, B26, B33, B35, B42, B54,
D1, D3, D8, D9, D11, D15, D17, D18, D19, E1, E7, E25, E32, F11, F13, F14, F15, G6, G14, G16, H2, H14,
I7, I14, I16, I22, J7, J9) KÉSZ — implementálva, lásd git-történet.

## M — bootstrap / loader

- **M1** `[TOP]` Iskolánkénti rúna-tekercsek — Rúnavért mellé iskola-counter enchantek (Fagyűző/Lángűző/Fényrúna/Csendrúna). 🟡⭐⭐⭐
- **M2** Kárhozat-lehelet damage-type — a Kárhozat-zóna közepén DoT, amit a Rúnavért nem blokkol. 🟡⭐⭐
- **M3** Csontszámvevő „adósság-érintése" damage-type — tematikus, sosem-halálos „emlékeztető" sebzés. 🟢⭐
- **M4** Suttogó-jel enchant (rejtett átok) — leleplezett Suttogó fegyverére kerülő eltávolíthatatlan átok. 🟡⭐
- **M5** Loader: adatbázis-réteg előkészítés — HikariCP+SQLite/H2 runtime-resolver, csak ha a YAML szűkös lesz. 🔴⭐⭐ (skálázásnál ⭐⭐⭐)
- **M6** Bootstrap-parancsok (LifecycleEvents.COMMANDS) — parancs-regisztráció bootstrap-lifecycle-re, csak ha a jelenlegi út deprecálódik. 🟢⭐
- **M7** Frakció-banner-minták — RP-függő: regisztrált banner-minta frakciónként.
- **M8** A Mélység Népe festményei — RP-függő: painting-variánsok a kódex jeleneteivel.
- **M9** Lore-zene (jukebox-song registry) — RP-függő: frakció-himnuszok ritka lootként.
- **M10** Frakció-trimek — RP-függő: armor-trim minta/anyag frakciónként.

## N — review-bővítések

- **N1** `/events spawncheck` debug-parancs — EventSpawnGuard `isBlocked`/`isUnsafeSurface` admin-diagnózisa.
- **N2** Archeológia: több egyidejű lelőhely — egyetlen site-mező listává generalizálva.
- **N3** Archeológia „pity timer" — N sikertelen ásás után garantált unique lelet.
- **N4** Purge-kill toplista — rontás-irtás StatsManager-kategória a Heti Krónikának.
- **N5** Krónika-archívum — utolsó N szám tárolása, `/kronika <szám>` paraméterrel.
- **N6** `/faction tax arrears` + `payoff` — saját hátralék megtekintés és önkéntes törlesztés.
- **N7** Signature-étel buff-cooldown — az abszorpció/tűzállóság korlátlan újraevés-frissítésének fékezése.
- **N8** `/suttogas allapot` — Suttogó saját gyanú-szintje + admin-oldali statisztika.
- **N9** Zóna-belépési üzenet cooldown — gyors határmozgásnál ne villogjon az action-bar.
- **N10** `territory.mob-rules` max-level zónánkénti felülbírálás — boss/esemény-mobok kizárása a zóna-bónuszból.
- **N11** Memória-szilánk: profession-pool bónuszpont beváltás — a talent-út mintájára, napi/heti limittel.
- **N12** Iskola-tekercsek — fogyasztható, rövid iskola-ellenállást adó tekercs (M1-hoz kapcsolódik).
- **N13** Páncél-enchant riderek — Rúnavért mellé aktív mellékhatású páncél-enchantok.
- **N14** Rúnavért VFX — finom rúna-particle sikeres iskola-ellenálláskor.
- **N15** Közös PlayerMoveEvent-diszpécser — 9 listener redundáns zóna-lookupjának egyesítése; csak nagy létszámnál éri meg.
N-review összefoglaló: N16, N17, N18, N24, N25, N25b, N27 KÉSZ; N26 tulaj-döntéssel ELVETVE.

## O — refaktor / technikai adósság

- **O1** 🟡 StatsManager láthatósági race — `Stat`-mezők nem volatile/atomic, ranglista pontatlanság-kockázat.
- **O2** 🟡 Világesemény-managerek force-vs-tick race — admin force-parancs és periodikus tick ütközhet, orphan entitás.
- **O3** 🟢 SunDanceSpell recept-cache dupla felépítés — check-then-act, felesleges duplikált munka.
- **O4** 🟡 `prefixAt` helper 17 fájlban duplikálva — tab-complete segédfüggvény közös helperbe emelendő.
- **O5** 🟡 Spell-célzás duplikált mintái — ray-trace célpont és körzeti szűrés ismétlődik több spellben.
- **O6** 🟡 Világesemény-közös minták (`WorldEventUtil`) — horgony-választás/idő-konverzió/entitás-eltávolítás közös helperbe.
- **O7** 🟢 `QuestManager.handleTerritoryEnter` O(összes quest) — auto-start index-építés a lineáris keresés helyett.
- **O8** 🟢 `RelicItemFactory` reflexiós metódus-scan cache — Method-referenciák lazy-init cache-elése.
- **O10** 🟢 `FactionPassiveListener` korai kilépés sorrendje — damage-cause szűrés a faction-lookup elé.
- **O11** 🟢 GUI close-cleanup hiánya 3 listenerben — ProfessionRecipeBook/QuestBuilder/Spellbook listener.
- **O12** 🟢 `CommandMenus` körözési lista cross-region PDC-olvasás — SinManager memóriabeli snapshot-map kellene.
- **O13** 🟢 Bank/exchange gate-ellenőrzés sorrendje — arg-validálás kerüljön a főváros-kapu elé.
- **O14** 🟢 `RaidManager.participants` kilépéskori helyfoglalás — szándékos, de türelmi idős auto-kick fontolható.
- **O15** 🟢 Escort-útpontok `getHighestBlockYAt` pontatlanság — lombkorona/víz felszín fölé kerülhet az útpont.
- **O16** 🟢 Aukció — licit elérheti a buy-outot, GUI-tipp/üzenet-pontosítás hiányos.
- **O17** 🟡 `CharacterGUIListener` — GUI-ba duplikált respec-logika, közös helperbe/RUN:-delegálásba kellene.
- **O18** 🟡 DisplayFx — nincs `max-per-player` entitás-plafon a claim-fal-scannél.
- **O19** 🟢 `DisplayFxUtil.showOnlyTo` — `hideEntity` kereszt-száli hívása, szigorú Folia-szabály szerint hop kellene.
- **O20** 🟢 Per-nézős fx-entitások skálázódása — aurora/claim-fal entitásszám a létszámmal nő, mérés indokolt.
- **O21** 🟡 `AbilityCatalystListener` felelősség-szétbontás — 774 sor, 9 map egy osztályban, bontás javasolt.
- **O22** 🟢 `MobLootListener.rollTable` duplikált gear-fallback — privát metódusba emelhető.
- **O23** 🟢 `CrateManager.persist()` szinkron teljes-YAML írás — debounce-javítás a #9-cel együtt.
- **O24** 🟡 `MobKillUtil.eligibleKill` közös kill-jutalom előszűrő — 19 listener eltérő AFK/minion-szűrése egységesítendő.
- **O25** 🟡 `DailyBudget` PDC-util — napi keret-minta 5+ helyen kézzel írva, közös `spend()` util kellene.
- **O26** 🟡 `ErrorMessages.resolve` közös hibakulcs→default tábla — 11+ osztályban ismétlődő switch.
- **O27** 🟡 `PeriodicChanceEvent` világesemény-ütemező váz — 5 manager azonos váza közös helperbe (O2/O6-tal együtt).
- **O28** 🟡 Elérés-küszöbök configba (AchievementManager) — hardcode-olt tábla + vagyon-elérés kölcsön-tőke kijátszhatóság.
O-refaktor összefoglaló: #9 (DonationChestManager debounce) KÉSZ (2026-07-20 auditkör); a többi nyitott technikai adósság.

## P — kormányzás, gazdaság-hurok és rework-jelöltek (2026-07-22, tulaj-kérés)

### P1 — Király/adó rework kiegészítő ötletek (az 5 pillér mellé)

- **P1a** Hadiadó-rendelet — raid előtt a király emelt adó-hetet hirdethet; a befolyt többlet
  látható "hadi-költségvetés" (raid-díj + győzelmi jutalom-alap ebből megy). 🟡⭐⭐
- **P1b** Vereség-reparáció — vesztes raidnél a támadó frakció kasszája fix hányadot fizet
  a védőnek; a királyi döntésnek (raid-indítás) így valódi tétje van. 🟢⭐⭐
- **P1c** Progresszív adó + újonc-mentesség — az adó a banki vagyon sávjai szerint nő; az
  első N nap (onboarding) adómentes. A "fejadó" a szegényeket ma aránytalanul üti. 🟡⭐⭐⭐
- **P1d** Választási kampány-hét — a jelöltek előre kihirdetik az első rendeletüket
  (programot); a szavazó tudja, mire szavaz. 🟡⭐⭐
- **P1e** Trón-kihívás (puccs-párbaj) — hetente egyszer a legtöbb szavazatú kihívó
  becsület-párbajra hívhatja a királyt; győzelme azonnali új választást ír ki. A
  min-harc-idő fék már él, a párbaj-infrastruktúra kész. 🟡⭐⭐
- **P1f** Koronázási ceremónia — új király megválasztásakor rituálé-esemény a fővárosban,
  rövid frakció-buff a jelenlévőknek (social sűrűség). 🟢⭐
- **P1g** Interregnum-zár — üres trón alatt a kassza csak minimál-költést enged; a
  frakciót ez tereli szavazni. 🟢⭐⭐
- **P1h** Királyi kegy — a király hetente egy "bajnokot" jelölhet (kis buff + Krónika-
  említés); pozitív jog, visszaélésre alkalmatlan. 🟢⭐

### P2 — Rokon rendszerek, ahol ugyanígy hiányzik a játék-hurok (rework-jelöltek)

- **P2a [TOP] Frakció-infrastruktúra (a kassza értelme)** — a kassza ma nyelő nélküli
  gyűjtőláda: legyen költhető VÁROSFEJLESZTÉSRE (fokozatok: bank-kamat, fővárosi
  craft-buff, őrség-erő, raid-védmű-bónusz). Ez adja az adó "miért"-jét, a rendeletek
  költési célját, és látható, közös progressziót — a király/adó rework természetes
  folytatása. 🔴⭐⭐⭐
- **P2b [TOP] Frakció-reputáció dinamikussá tétele** — a barát/ellenség viszony ma
  admin-set statikus tábla; mozgassa esemény (hadi-ablak, raid, karaván-kifosztás,
  szövetségi segítség), és hasson többre a piaci feláron túl (komp-ár, zóna-belépési
  hangulat, kereskedő-készlet). Geopolitika admin nélkül. 🔴⭐⭐⭐
- **P2c Raid 2.0 — ostrom-célpontok** — a raid ma tiszta kill-pont verseny: cél-objektum
  réteg kellene (védmű-rombolás a regen-rendszerrel + ostromgép-item már kész; zászló-
  elfoglalás), vereség-reparációval (P1b). A "csata" így hely-alapú lesz, nem TDM. 🔴⭐⭐
- **P2d Aktivitás-mérés közös alapként** — a szavazati jog (kormányzás-fék), a tanács
  alt-védelme, a szezon-jutalom skálázás és az AFK-fék mind ugyanazt a "heti aktív
  játékidő" mércét kéri — egyszer megépítve négy rendszer kapja meg. 🟡⭐⭐⭐
- **P2e Piac-megrendelések (buy order)** — a piac ma csak eladó-oldali; a vevő-oldali
  megrendelés a craftereknek kereslet-jelzést, a gazdaságnak mélységet ad (a
  börze-tábla infrastruktúra részben megvan). 🔴⭐⭐
- **P2f Céh mint frakció-alegység (a szétszabdalás-félelem feloldása)** — a céh ne
  KÜLÖN közösség legyen (tulaj-aggály: tovább osztaná a playerbase-t), hanem a SAJÁT
  FRAKCIÓ liga-pontjaira és közösségi céljaira dolgozó "brigád": céh-hozzájárulás =
  frakció-pont bónusz + Krónika-említés. Így összeköt, nem szétválaszt — a kassza-váz
  pedig végre kap funkciót. 🟡⭐⭐
- **P2g Szezon-identitásutak kiegyensúlyozása** — a season.source-weights mátrix él, de
  kalibrálatlan: playtest-mérés után a 4 frakció-út (war/trade/faith/wild) pont-hozamát
  egy szintre kell hozni, különben a liga eldől az út-választáson. 🟡⭐⭐ (playtest-függő)

### P3 — Teljes rework-térkép (rendszer-leltár alapján, 2026-07-22)

A P2-audit nyitott leleteiből + a rendszer-leltárból összeállított rework-programok
(nem új leletek — a meglévők PROGRAMOKBA rendezve, becsült sorrenddel).

**Nagy rework-programok:**

- **P3a [TOP] HP/sebzés-skála (= A17)** — a vanília 20 HP-ra 5-8 sebzésű spellek épülnek:
  a PvP TTK rövid, a gyógyítás-értékek és a pajzsok inkonzisztensek. Kaszt-HP-profilok,
  healthScale-normalizálás, harcon kívüli regen, pajzs-egységesítés, sebzés-újrahangolás
  EGY körben — minden más balansz erre épül. Tulaj-döntés szerint külön nagy kör. 🔴⭐⭐⭐
- **P3b [TOP] Onboarding NPC-függetlenítés (= P2-audit #9)** — a teljes sztori-gerinc a
  FancyNpcs-en lóg néma hibával: TALK_TO_NPC kódos tartalék-út (pl. /quest talk parancs
  vagy auto-teljesülés zóna-belépésre), hiányzó NPC-nél hangos admin-riasztás. Enélkül
  egy plugin-hiba az egész új-játékos élményt csendben öli meg. 🔴⭐⭐⭐
- **P3c [TOP] HUD/tablist teljesítmény (= P2-audit #19)** — O(n²) tablist-szinkron fél
  másodpercenként + diff-cache nélküli oldalsáv: 50-60 fő ELŐTT kötelező kör (last-line
  cache, cleanup-ritkítás, batch-diff). 🟡⭐⭐⭐
- **P3d Szezon-dramaturgia** — 13 napos sztori-lyuk (41-53. nap), mind a 30 rejtvény
  1. naptól kimeríthető (kapuzás visszaszökött), a finálé-ív pacing: a szezon közepére
  tartalom-ütem kell (heti rejtvény-nyitás, köztes Fa-üzenetek). 🟡⭐⭐
- **P3e Talent-rendszer mélyítés** — holt tier1-ek (scholar/arcane_might), 6 szakmának
  nincs saját talentje, összesen 3 tier: a fák rövidek és részben halottak. A tier2
  differenciálás (kész) folytatása: tier1-takarítás + szakma-talentek + opcionális 4.
  tier a 40+ szinteknek. 🔴⭐⭐
- **P3f Világesemény-ütemező szétterítés** — ~35 tick() egyetlen globál-kötegben
  60 mp-enként (tüske-kockázat): fázis-eltolásos szétterítés az intervallumon belül.
  Playtest-méréssel együtt. 🟡⭐⭐

**Kis-közepes reworkok (egy-egy ülésben lehúzhatók):**

- **P3g Integrációs hidak higiénia** — WorldGuard-híd flag-ellenőrzés nélkül tilt;
  LibsDisguises-hidak (druida-forma/kém/fekvés) felülírják egymás álcáját (arbitráció
  kell); dangling NPC-kötés mindenhol néma. 🟡⭐⭐
- **P3h Actionbar-arbitráció** — több forrás (claim-warn, honvágy, esemény, combat-tag)
  felülírja egymást: közös actionbar-csatorna prioritással/sorbanállással. 🟡⭐⭐
- **P3i /menu lefedettség + admin-tab szűrés** — /tanacs, /komp, /faction war hiányzik a
  menüből; az admin-alparancsok jog nélkül is látszanak a tab/helpben. 🟢⭐
- **P3j Karaván-javítások** — a kalmár-karaván nem megy át az EventSpawnGuard-on; a
  spawn felülírhatja a már lezárt szállítmányt (placeholder-UUID re-check). 🟢⭐⭐
- **P3k Parkour-ranglista** — best-time perzisztálás + /leaderboard kategória (a napi
  limit után a parkour versenyfelülete ez lenne). 🟢⭐
- **P3l Apró gazdaság-hangolások** — beszállító-hetik olcsó faucet (4×90/hét), ambient-
  jutalom napi cap, bajnok-jutalom létszám-skálázás, addPoints/rollover verseny-őr. 🟢⭐
- **P3m Kém-akció raid-szűkítés** — csak az érintett frakciók raidje blokkolja, ne
  bármely globális. 🟢⭐

**Felmértük, és NEM kér reworkot:** crate (natív, kulcs-nyelővel), claim (védelem
teljes), bounty/párbaj (fékek élnek), pet (friss rework), kazamata-loot (friss),
vezeklés-lánc, emlékszilánk, komp, moderáció, AFK, bestiárium, Suttogó-pipeline.
