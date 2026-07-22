# P2 — Teljes gameplay-audit, 2. kör (2026-07-21, 14 szempont, több-agentes mélyvizsgálat)

> 18 párhuzamos szempont (14 fő + 4 záró): játékos-életút • gazdaság • frakció/PvP • világesemények+loot •
> quest-konzisztencia • spell/kaszt-balansz • szakmák+itemek+relikviák • claim/grief-védelem •
> Folia-konkurrencia • GUI/parancs/UX • talent/spec/erőforrás • perzisztencia/restart •
> integrációs hidak • teljesítmény (50-60 fő). A számok config-ellenőrzöttek; a kiemelt
> leleteket kézzel is visszaellenőriztük a kódban. Az előző (P1) audit mind a 15 lelete lezárult
> (részletek a git-történetben); ez a doksi az élő audit, helyben frissül. Státusz: a ✅ jelölt tételek javítva; a többi tulaj-döntésre vár.

## 🔴 Strukturális leletek

### Frakció / PvP
1. ✅ KÉSZ (P2-1. csomag) — **A DARK "örök paktum" pénzért megkerülhető.** A `/faction join red|blue|neutral` ÉS a
   `/faction leave` is kienged a Kitaszítottak közül a sima váltás-díjért (500) — sehol nincs
   `hasDarkPact` ellenőrzés, miközben a belépési figyelmeztetés és a SinManager javadoc is
   örök paktumot ígér, és az egyetlen szándékolt kiút a vezeklés-lánc (`breakDarkPact`,
   egyetlen hívó: QuestManager vezeklés-jutalom). A bűn→száműzetés→vezeklés narratíva így
   fizetős forgóajtó. KÉZZEL MEGERŐSÍTVE. → `hasDarkPact` kapu a join/leave váltás-ágain.
2. ✅ KÉSZ (P2-1. csomag: a bűn-törlés is a 12h cooldown alá került) — **Vérdíj bűn-törlése megrendezett halállal.** A bounty-ág (SinListener) az áldozat
   bűneit feltétel nélkül nullázza (`clear-sins-on-death`), a 12h cooldown csak a
   PÉNZ-kifizetést fogja vissza — egy 3 bűnű játékos baráti kivégzéssel korlátlanul
   nullázhatja magát a 4-bűnös száműzetés előtt. (A kódkomment részben szándékosnak
   jelzi — "az életével fizetett" —, de az exile-kerülő exploit áll.) KÉZZEL MEGERŐSÍTVE.
   → a bűn-reset is menjen cooldown alá, vagy csak tényleges kifizetésnél nullázzon.
3. ✅ RÉSZBEN KÉSZ (Fable-kör: raid-tudatos PvP-kapu a célzónában; ostrom-rombolás továbbra sincs — design-döntés) — **A raid a saját fő célpontján nem tud működni.** A raid alapból a védekező FŐVÁROSÁT
   jelöli célnak, de a főváros védett zóna: a `TerritoryProtectionService.denyCombat`
   SEHOL nem kérdezi a RaidManagert (kézzel megerősítve: 0 hivatkozás), így fővárosi
   raidnél a PvP-sebzés cancel-elődik → kill-pont sosem születhet. Blokk-rombolást a raid
   soha, sehol nem lazít ("ostrom" nem létezik, csak PvP+lootolás nem-védett zónában).
   → a raid explicit lazítsa a combat/build-tiltást a raid-zónában a regisztrált
   harcosokra, VAGY a raid-cél ne protected-zone legyen.

### Claim / grief
4. ✅ KÉSZ (P2-1. csomag: entity-damage + item-frame + armor-stand + mob-vödör kapuk) — **Idegen claimben az élőlények és a kirakott tárgyak védtelenek.** Nincs
   `EntityDamageByEntityEvent` handler a claim-listenerben (állat/villager/armor-stand
   ölhető), nincs `PlayerInteractEntityEvent`/`PlayerArmorStandManipulateEvent` (item-frame
   tartalma és állvány-felszerelés lopható törés nélkül), nincs `PlayerBucketEntityEvent`
   (mob-vödrözés). A blokk/robbanás/folyadék/piston-védelem viszont teljes.
   → a territórium-oldali minta átvétele a claim-oldalra.

### Világesemények
5. ✅ KÉSZ (Fable-kör) — **Kultista rítus dupla-lezárás versenyhelyzet.** Az utolsó hívő halála (mob régió-szál)
   és a rítus-határidő (globál tick) egyszerre zárhatja le ugyanazt a rítust — nincs közös
   atomi "ki nyert" őr (a testvér-managerek claimSettlement-mintája itt hiányzik) → dupla
   jutalom + ellentmondó broadcast lehetséges. → compareAndSet-szerű lezárás-csere.
6. **Az invázió és a kultista-sorsolások 65%-a jutalom nélküli holt tartalom.** Az
   inváziónak nincs semmilyen dedikált loot-ja (csak normál mob-XP), a kultista
   attack/courier variánsok (össz-súly 65%) leölve sem adnak tárgyat — racionális játékos
   kihagyja őket. → invasion.reward-loot + cultists attack/courier loot-tábla.

### Gazdaság
7. ✅ KÉSZ (P2-1. csomag: daily-cap 50; a spawner-kizárás a min-mob-level révén eleve élt) — **A lélekkő-drop az egyetlen napi keret nélküli pénz-faucet.** Nincs napi cap (a
   mob-money-drop 300 és fishing-windfall 150 kapott), nincs spawner-kizárás, és a
   nem-DARK játékosok élőhalott-farmja sincs tiltva — uncapped Csontveret-forrás, 3%
   díjjal átváltva. → napi keret + spawner-kizárás + undead-szűkítés.
8. **Az árfolyam 50-60 fős szerveren a 4× plafonon ragadna.** A reference-supply (10000)
   nagy szerverre kalibrált: ~625 alatti össz-bankegyenlegnél már max-multiplier; ráadásul
   csak a BANKI egyenleg számít kínálatnak — tömeges kivéttel (fizikai veret zsebben) a
   kínálat mesterségesen ritkítható, majd felfújt árfolyamon visszaváltható.
   → reference-supply újrakalibrálás + napi váltási limit (F21).

### Onboarding / quest
9. **A teljes onboarding + sztori-gerinc a FancyNpcs-en lóg, tartalék nélkül.** A
   TALK_TO_NPC objektíva a híd nélkül VÉGLEG teljesíthetetlen (csendes korai return); a
   `hirnok` 9, az `erdei_venek` 5 quest-ponton kötelező — köztük az auto-induló első
   quest és mindhárom fejezet-zárás. Az intro/motd nem mond parancsot fallbackként.
   → kódos tartalék-út + a hiányzó NPC-ről hangos log/admin-jelzés.
10. ✅ KÉSZ (P2-1. csomag: giver-npc mindkettőre) — **Két quest párbeszéde soha nem jelenik meg.** `merchant_choice` (a JÁTÉK EGYETLEN
    elágazó story-választása!) és `forest_cleansing` (lépéssorrend-magyarázat) giver-npc
    nélkül csak `/quest accept`-tel vehető fel, ami a dialógus-render utat kihagyja.
    → giver-npc: "vandor_kereskedo" ill. "erdei_venek" felvétele.

### Kód-egészség
11. ✅ KÉSZ (Fable-kör; + reload-hook: a 3 load()-cache-elő manager reloadra frissül) — **Reload-race két managerben (Folia).** `RelicManager.triggerConfigs` + RelicRegistry
    (sima HashMap/LinkedHashMap) és `CraftingRestrictionManager.rules` (sima ArrayList)
    clear+rebuild mintával épül újra `/icesmp reload` alatt, miközben régió-szálak
    olvassák — reload közbeni craft/relikvia-használat CME/crash-képes. A házon belüli
    megoldás (ConfigManager volatile+build-then-swap mintája) nem lett végigvezetve.
12. ✅ KÉSZ (P2-1. csomag) — **`icesmp.admin.item` nincs regisztrálva a Permissions-ben** (KÉT agent egymástól
    függetlenül) — az `icesmp.admin.all` NEM adja meg a `/iceitem`-et. → ITEM felvétele
    a canonical map-be.
13. ✅ KÉSZ (Fable-kör) — **A persistentStores load/save lánca hibakezelés nélküli.** Egy sérült YAML egyetlen
    managerben megszakítja a forEach-et: minden utána következő manager üresen indul
    (load) vagy mentetlen marad (save) — kaszkád-adatvesztés egyetlen rossz sorból.
    → per-manager try/catch + log.
14. ✅ KÉSZ (Fable-kör: settings.autosave-minutes async mentés + raid-zárás broadcast restartnál) — **Nincs időszakos autosave; a StatsManager KIZÁRÓLAG leállásnál ment.** Crash =
    minden ranglista-progressz + az utolsó mentés utáni összes YAML-mutáció elvész
    (a MarketManager 2s debounce-a kivétel, az jó minta). Az aktív raid pedig még tiszta
    restartnál is csendben megsemmisül (nincs force-resolve). → async autosave ütemezés +
    raid-lezárás shutdown()-ban.

### Balansz
15. ✅ KÉSZ (Fable-kör: spells.total-power-cap 1.75) — **Mastery × dinamikus skálázás szorzódik, plafon nélkül (2.25×, finisherrel 2.81×).**
    Nem-ultimate spellek 15-22 sebzést ütnek max szinten (obliterate 18 / 20 mp-enként,
    a deathfrost kombó 36 nyers sebzés 4 mp alatt — 60% max rezisztencia után is ~14).
    → a két szorzó additív összevonása vagy közös 1.75× sapka.
16. **Fagylovag perma-CC.** 8-9 freeze/slow spell egy specen, összesített uptime max
    szinten ~100-105% egyetlen célon — diminishing returns nélkül szinte folyamatos
    fagyontartás. → CC-DR (ismételt CC csökkenő időtartammal).
17. ✅ KÉSZ (P2-1. csomag: requires-spent 9) — **Az ascendant capstone-talent tiszta szintezésből elérhetetlen.** 50 szint / 5 =
    10 pont; a talent requires-spent: 10 UTÁN 11.-ként kellene megvenni — csak a rejtett
    /emlek talent (Emlékszilánk) út adja ki. KÉZZEL MEGERŐSÍTVE (classes.yml:555,1117).
    → requires-spent 9-re, vagy garantált bónusz-pont az 50. szinten.
18. **Két relikvia legálisan megszerezhetetlen** (Mételytépő, Sárkánytojás-töredék —
    se rituálé, se loot-tábla), és mivel a Mételytépő az egyetlen weapon-relic, a teljes
    RelicPvpTransferListener holt kód. → rituálé/boss-loot forrás mindkettőnek.

### Teljesítmény
19. **Tablist-szinkron O(n²) fél másodpercenként + HUD-oldalsáv diff-cache nélkül.**
    60 főnél több százezer összehasonlítás/tick a tablistben; a HUD ~900 változatlan
    tartalmú csomag/mp. → cleanup ritkítása + last-line cache (a tablist header/footer
    diff-mintája már házon belül megvan).

## 🟡 Érdemi leletek (válogatás, domain szerint)

- **Hadi-ablak win-trade:** két összejátszó fél 30 percenként váltott öléssel kockázat
  nélkül termeli a liga-pontot — min. harc-idő/HP-veszteség feltétel vagy kétirányú
  pár-cooldown kellene. (A pár-cooldown ráadásul volatilis: restart nullázza.)
- **Tanácsi kassza-keret fejenkénti** (3×400=1200/nap > királyi 1000, a kódkomment
  ellenkezőjét állítja; KÉT agent találta) → frakció-szintű közös számláló.
- **Tanács-szavazás alt-védelem nélkül** (min. játékidő-feltétel javasolt).
- **Inaktív király nem bukik le automatikusan** — lejáratkor csak a szavazatok nullázódnak,
  új min-votes jelölt nélkül örökre marad. → automatikus trónfosztás lejáratkor.
- **Suttogó éjszakai békesség kockázat-mentes** — az ígért "árulkodó jel" gyanú-hurok
  nincs bekötve (döntés kérdése: maradjon ingyen-előny, vagy kis witness-esély).
- **Combat-tag nincs** — vesztésre álló harcból safe-zone-ba/kompra sétálás; a /komp
  sem néz harc-állapotot. → 10-15 mp combat-flag, ami zónába-lépést/kompot tilt.
- **End-portál kikerüli az "egyetlen kapu" szabályt** (a FIRE-tiltás a stronghold-ra nem
  hat) → döntés: End engedett-e; ha nem, portál-aktiválás blokk.
- **Zóna-rámpa torzít elnyúlt zónáknál** (befoglaló-kör becslés): hosszú tengelyen túl
  messzire nyúlik a 0-szint, rövid tengelyen túl korán durvul → poligon-él távolság vagy
  dokumentált kompromisszum.
- **Rontás-góc: globális singleton, ~8h átlag-ritkaság** 50-60 főre kevés (megfontolandó:
  világonként egy, vagy sűrítés).
- **Karaván-spawn felülírhatja a már lezárt szállítmányt** (placeholder-UUID re-check
  hiányzik a hopolt callbackben) + a kalmár-karaván nem megy át az EventSpawnGuard-on.
- **Vér-spellek gyengébbek az azonos tierű XP-spelleknél** (a "kockázatnak ára van" elv
  fordítva sül el) + nekromanta-típusú HP-drain speceknek nincs olcsó visszagyógyulása.
- **Energia-kasztok (orgyilkos/szerzetes/íjász) erőforrása sosem fogy ki** (regen 11-14/mp
  vs. ~1/mp tényleges drain) — illuzórikus gazdálkodás.
- **Tier2 talent-párok mind a 13 kasztnál numerikusan azonosak** (+1.5 dmg vs +6 HP) —
  álválasztás; 6 szakmának nincs saját talentje; scholar/arcane_might holt tier1-ek.
- **6 recept korábban nyílik, mint a hozzávalója termelhető** (pl. tutaj_keszlet
  fisherman 11 ← szavannafu_kotel CSAK lumberjack 24-től) → szint-emelés vagy bolt-forrás.
- **Bástya-pajzs két párhuzamos rendszerben** (régi ProfessionRecipeManager-mestermű
  mindig jobb, mint az új katalógus-recept) → a 8 régi mestermű összevonása/törlése.
- ✅ KÉSZ (P2-1. csomag: FurnaceSmelt-tiltás) — **Unique anyag kemencében elolvasztható** (FurnaceSmeltEvent-védelem hiányzik; pl.
  melysegi_borostyan → sima aranyrúd). → smelt-input tiltás unique-tagen.
- **DARK szárny-rituálé aránytalanul drága** (Wither-koponya + 2 netherite scrap vs. a
  többiek farmolható blokkjai) → hozzávaló-csere.
- ✅ KÉSZ (P2-1. csomag: a Vásárjárás DELIVER_ITEMS objektívát kapott) — **NEUTRAL dupla heti kereskedő-quest:** az új `neutral_heti_vasarjaras` majdnem
  duplikálja a meglévő `neutral_heti_vasar`-t (2×120/hét közel ingyen; SAJÁT tartalom-
  hullám-4 hiba) → az új quest más objektívát kapjon.
- **`/icesmp reload` három managernél nem él** (Relic/MobScaling/CraftingRestriction a
  load()-ban cache-el — reload után elavult marad restartig) + reload-sikerüzenet korrupt
  confignál is. → load() hívása a reload-ágban vagy use-site olvasás.
- **save() szinkronizáltság inkonzisztens** (12 persistentStore-manager save-je nem
  synchronized — konkurens mentésnél a régebbi snapshot nyerhet) → egységes synchronized.
- **WorldGuard-híd bármely régiót tiltottnak vesz** (flag-ellenőrzés nélkül) + a hidak
  lazy-init logja csak első használatkor jelez + dangling NPC-kötés mindenhol néma +
  LibsDisguises-hídak (druida/kém/fekvés) felülírják egymás álcáját.
- **/tanacs, /komp, /faction war hiányzik a /menu-ből**; relikvia-üzenetek a
  MessageManager-en kívül (1 hardcoded string); actionbar-források arbitráció nélkül
  írják felül egymást; admin-alparancsok jog nélkül is látszanak a tab/help-ben.
- **worldEventsTask: ~35 tick() egy globál-szálas kötegben** 60s-enként (tüske-kockázat) +
  PetManager 4×/mp minden játékosra hop-ol pet nélkül is → mérendő playtesten.
- **~13 napos sztori-lyuk a szezon 41-53. napján** (a Fa üzenete 40-ig, finálé 54-től) +
  mind a 30 rejtvény kapu nélkül, 1. naptól kimeríthető (elő-terheltség visszaszökött).
- **Kaszt-választás megerősítés nélkül** — a legdrágább (admin-only visszafordítható)
  döntés egyetlen kattintás; a DARK-belépés kétlépcsős mintája átvehető.
- **Beszállító-hetik olcsó faucet** (4×90/hét percek alatt gyűjthető anyagért) — hangolás.

## 🟢 Polish (válogatás)

- Doksi-számok: 12-kuldetesek "négy kaszt-próba" (valójában 13), közösségi cél "600 vas"
  (config: 1500); PAPI-táblázatból hiányzik 2 placeholder; TalentGUI "spell-power"
  címke lefordítatlan; lucky_star rejtett XP-drain ára nincs a config-kommentben.
- Alvó funkciók: auto-start-territory és REACH_LEVEL objective 0 használattal; 4+.
  fejezet nem létezik (chapter-questek 4. szezontól mind zártak); rotation-daily-count
  fájl-sorrend-függő minta.
- Meteor-kráter méret hard-cap kódban (config-túllövés ellen); rituálé-cooldownok
  volatilisek (restart nulláz).

## Ami kifejezetten jól áll (a 14 jelentés szerint)

- **A "számlára csak bankból" szent invariáns NEM sérül** — minden jutalom-kifizetés
  fizikai veret (payOutTokens), a bank-egyenleget csak befizetés és a piaci elszámolás
  (másik játékos már levont pénze) mozgatja.
- **160 quest gépi validáláson hibátlan** (hivatkozások, enum-nevek, kötelező mezők,
  crate-key/currency formátumok, season-day sávok).
- **GUI-biztonság hibátlan**: mind a 18 holder-listener páros típusszűr, mindent cancel-el,
  drag+close kezelt — dupe-rés nincs.
- **Folia-fegyelem magas**: tiltott scheduler 0, sync teleport 0, a cross-entity hop
  minta szisztematikusan tartva és dokumentálva.
- **Recept-katalógus ép**: 72/72 unique-hivatkozás él, CMD-ütközés nincs, üres szint-sáv
  nincs; stateful spellek clearPlayerState lefedettsége hiánytalan; stacking-sapkák
  (mastery/dynamic/resist) rétegenként rendben; mozgás-eventek és memória-növekedés
  tiszta; halál-gazdaság átgondolt (nincs frusztráció-spirál); szint-görbe 1-50 egyenletes;
  betöltési sorrend mind az 5 hídra helyes.

## Világépítő-checklist (a questek által megkövetelt kézi kihelyezések)

- **18 NPC:** hirnok, vandor_kereskedo, erdei_venek, kovacs_mester, revesz, pakt_mester +
  a 13 kaszt-mester (harcos/ijasz/varazslo/orgyilkos/druida/paplovag/halallovag/saman/
  szerzetes/pap/demonvadasz/sarkany + warlock-megfelelő) — /npcbind kötésekkel.
- **4 territory-id:** `dark-capital`, `erdei-szentely`, `karhozat-kapuja`, `radicora` —
  egyik sincs még kijelölve, mind /territory admin-eszközzel készítendő.

## FÜGGELÉK — Záró kör (15-18. szempont: mellék-rendszerek, szezon-életciklus,
## lore-kánon, doksi↔config)

### 🔴 Új strukturális leletek

20. ✅ KÉSZ (Fable-kör) — **Párbaj pár-invariáns megtörhető.** A kihívó több párhuzamos függő kihívása közül a
    második elfogadás felülírja az elsőt az active-mapben — az első célpont egy már nem
    élő párbajra hivatkozva bűn/vérdíj-mentesen ölhet. → challenge() tiltsa a több
    kimenő pendinget, vagy accept() ellenőrizze újra az aktív állapotot.
21. **A céh funkció nélküli váz.** A kassza egyirányú (deposit van, withdraw/felhasználás
    SEHOL), a céh-szint csak taglétszám-plafont emel — se perk, se kedvezmény, se
    integráció (egyetlen külső hívó a quest-XP). A 250-es alapítási díj után a rendszer
    semmit nem ad. A legerősebb "sziget" a kódbázisban. → kassza-felhasználási út +
    legalább egy szint-kötött előny, vagy őszinte kivezetés.
22. ✅ KÉSZ (P2-1. csomag: napi 3 jutalmazott futam/pálya + gyöngy/refő/elytra/riptide futam-megszakítás) — **A parkour korlátlan pénz-faucet, anti-cheat nélkül.** Nincs per-pálya cooldown/napi
    limit (start→cél→start ciklus azonnali veret-kifizetéssel ismételhető), és a cél-check
    csak távolságot néz — pearl/elytra/mobilitás-spell bejárás nélkül célba visz.
    → napi limit + mobilitás-tiltás futás alatt (checkpoint-lánc).
23. **A szezon-jutalom winner-takes-all.** A 2-4. helyezett frakció SEMMIT nem kap, még
    záró-broadcastot sem — 60 nap liga-munka a mezőny 3/4-ének nulla visszajelzéssel
    zárul. → lépcsőzetes jutalom (pl. fél/negyed kassza a 2-3. helynek) + záró-összegző
    broadcast mindenkinek.
24. **A kazamata kód-szinten üres héj.** A kulcs-kapu és a +5 mob-szint működik, de nincs
    dungeon-specifikus loot/boss/szoba-logika — a kapu mögött admin-építés nélkül csak
    "erősebb mobokkal teli üres tér" van. → dungeon-loot/boss bekötése VAGY explicit
    jelzés az építész-doksiban, hogy a belső tartalom 100%-ban kézi.

### 🟡 Új érdemi leletek

- ✅ KÉSZ (Fable-kör: currentSeasonId = getSeasonStart) — **Szezonális quest-azonosító instabil:** a currentSeasonId az élő getSeasonEndMillis-re
  épül — egy length-days állítás szezon közben újranyitja az összes teljesített
  szezonális questet. → getSeasonNumber()-re váltás. (A rollover-mag egyébként
  megbízható: offline átfordulást pótol, dupla-rollover kizárt, a bajnok-megállapítás
  a nullázás előtt fut.)
- **Párbajos bűn-mosás:** önként vesztő szövetségessel heti 2 bűn-tisztítás kockázat
  nélkül → min. harc-idő/sebzés feltétel (ugyanaz a fék, mint a hadi-ablak win-trade-re).
- **Régészet "első kattintó visz mindent"** — a runner-up minta (HiddenSpot
  repeat-reward-ratio) átvehető; ráadásul a lelőhely mindig egy véletlen játékos 80
  blokkos körzetében spawnol, és csak 160 blokkon belül hirdet — a horgony-játékos
  szisztematikus előnyben. → megosztott jutalom + szórtabb spawn.
- **Rejtett helyek: a config üres** (hidden-spots.spots: {}) — a rendszer kész, de
  tartalom nélkül néma. VILÁGÉPÍTŐ-CHECKLIST tétel.
- **Közösségi célok sosem nullázódnak szezonváltáskor** (inkonzisztens a többi rendszer
  szezon-modelljével) → döntés + dokumentálás.
- **Kazamata-belépés fejenként külön kulcs** (5 fős céh-túra = 5 kulcs, nincs
  csoport-mód) → megfontolandó kedvezmény; **kém-akciót bármely globális raid blokkol**
  (nem csak az érintett frakcióké); **ambient-jutalomnak nincs napi capje** (kicsi, de
  elvi következetlenség); **addPoints/rollover verseny** őr nélkül (60 naponta pár pont
  kockázat); **bajnok-jutalom nem skálázódik létszámmal**; **parkournak nincs
  ranglistája** (best-time nem perzisztált); **honvágy-büntetés gyakorlatilag
  érzékelhetetlen** (10s Hunger 0 / 5 perc — ha szándékos, dokumentálni).

### 🟡 Doksi-lemaradások (a "second-wave" rendszereket a magas szintű doksik nem követték)

- ✅ **02-frakciok "Suttogók (tervezett)" szekció** — a rendszer TELJESEN él; a 🔜/tervezett
  jelölés törölve, a szekció élő rendszerként szól (mindkét repóban; tulaj-kérés: 2026-07-22).
- ✅ KÉSZ (P2-1. csomag) — **README elavult DARK-belépés** ("Mételytépő bélyegez bűnössé" — valójában a 4-bűnös
  számláló; a Mételytépő ma már csak fegyver).
- ✅ KÉSZ (P2-1. csomag: README + 14-parancsok frissítve; az AGENTS.md már 6-ot mondott) — **Zóna-típusok: 4-ként dokumentálva, valójában 6** (DOOM_GATE, DUNGEON hiányzik a
  README-ből, AGENTS-ből, a 14-parancsok territory-típuslistájából; az AGENTS.md 15-16.
  sora önellentmondó).
- ✅ KÉSZ (P2-1. csomag: README 400+, 01-kezdes veret-átadásra írva; a PLAYER_GUIDE kazamata-link már élt) — **README "120+ recept" → valójában 410**; **01-kezdes a kikapcsolt /currency pay-t
  élőként ajánlja**; **PLAYER_GUIDE linklistájából és szövegéből teljesen hiányzik a
  16-kazamatak oldal**.

### 🟢 Lore-kánon: NULLA törés

A név-kánon (Thanaopolis/Radicora/Olethropyla/Mortengrad-items-only) gépi ellenőrzésen
hibátlan; idővonal és frakció-attribúciók mindenhol helyesek; a LORE.md tisztaság-szabálya
áll. Két referencia-rés: a Vének Tanácsa és a hadi-ablak hiányzik a LORE_REFERENCE
lore→mechanika táblájából (+ zone-ramp fél mondat) — pótlandó.

### Világépítő-checklist kiegészítés

- **Rejtett helyek feltöltése** (world.yml hidden-spots.spots — jelenleg üres) és
  **kazamata-belsők megépítése + loot-elhelyezés** (a kapu mögött nincs generált tartalom).

## Javasolt javítási sorrend (tulaj-döntésre; a függelék-leletekkel egyesítve)

1. **Azonnali kód-patchek (kicsik, egyértelműek):** #1 DARK-kapu, #2 bounty-reset-cooldown,
   #12 admin.item jog, #10 giver-npc 2 questre, #17 ascendant, #11 reload-race (2 osztály),
   #13 load/save try/catch, NEUTRAL dupla-heti szétválasztás, unique-smelt-védelem,
   #20 párbaj-invariáns, #22 parkour-limit, szezonális quest-id stabilizálás,
   LORE_REFERENCE + doksi-lemaradások (Suttogó-jelölés ✅ törölve; README-javítások).
2. **Kis-közepes patchek:** #4 claim entity-védelem, #5 kultista race, #14 autosave +
   raid-lezárás, #7 soulstone-cap, combat-tag, #19 tablist/HUD optimalizálás,
   #23 lépcsőzetes szezon-jutalom, régészet runner-up, párbaj/hadi-ablak harc-feltétel.
3. **Design-döntést igényel:** #3 raid-cél (fővárosi ostrom-modell), #6 invázió/kultista
   loot, #8 árfolyam-kalibráció, #15-16 spell-sapkák + CC-DR, End-portál, Suttogó
   witness-hurok, király-trónfosztás, tanács-keret modell, #21 céh-tartalom (vagy
   kivezetés), #24 kazamata-loot modell, közösségi célok szezon-viselkedése.
4. **Playtest-mérés (nem kód):** tablist/worldEventsTask/petTask időzítés 50-60 fővel;
   Íjász/Orgyilkos valós DPS (a papír-metrika vak a DoT/vanília rétegre).
