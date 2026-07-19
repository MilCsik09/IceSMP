# A) Meglévő mechanika átdolgozása / polish

[← Ötlettár-index](../IDEAS.md)

Jelölés: **Munka** (🟢 kicsi / 🟡 közepes / 🔴 nagy) • **Érték** (⭐–⭐⭐⭐) • `[TOP]` = ajánlott
következő kör • `[KÉSZ]` = már implementálva.

> A1–A16, A43–A62 ✅ implementálva (PLAYTEST.md).

---

### A1. CC-audit — „mob-vak" effektek felderítése `[KÉSZ]`
🟢 • ⭐⭐⭐ • **Státusz:** ✅ KÉSZ

**Mi ez:** Az összes CC-spell átvizsgálása „játékos vs mob" szemmel, mert a látás-zavaró
effektek (blindness/darkness/nausea) mobokra hatástalanok voltak.
**Hogyan valósult meg:** A Megzavarás spell reworkolva: mobra célozva célpont-vesztés
(aggro-törlés, 10 mp-ig ismételve) + slowness/gyengeség, játékos-célpontra vakság+sötétség; a
korábbi méreg-komponens kikerült (tiszta CC lett). PLAYTEST 4.5.1 külön checklist-pontban
ellenőrzi a mob-oldali hatást.
**Miért jó:** Megelőzi a „ez nem csinál semmit?" playtest-jelentéseket, a CC-kaszok (druida,
varázsló, sámán) érezhetően működnek PvE-ben is.
**Építőkövek:** `ConfusionSpell`, mob AI `setTarget`/aggro API.
**Buktatók:** —

### A2. Party-tudatos AoE spellek `[KÉSZ]`
🟡 • ⭐⭐⭐ • **Státusz:** ✅ KÉSZ

**Mi ez:** Az AoE spellek (Forgószél, Csi Hullám, Földrengés, splash-ek…) korábban a saját
csapattársat is ütötték.
**Hogyan valósult meg:** Központi `SpellTargetingUtil.isHostile(caster, target)` szűrő (párty-
tag kizárva, config szerint azonos frakció is kizárható) — minden AoE/splash spell ezen megy
át (`ConfiguredSpell`, illetve a dedikált state-es spellek mint `WhirlwindSpell`,
`SpinningCraneKickSpell`).
**Miért jó:** Csapatos PvE/PvP-ben a caster kaszok (varázsló, sámán, druida) végre biztonságosan
AoE-zhetnek a párttársak közelében — a korábbi frusztráció megszűnt.
**Építőkövek:** `PartyManager`, faction-tagság lekérdezés.
**Buktatók:** —

### A3. Kaszt-erőforrás identitás (regen-profilok) `[KÉSZ]`
🟡 • ⭐⭐⭐ • **Státusz:** ✅ KÉSZ

**Mi ez:** A kaszt-erőforrások (Mana/Düh/Energia/Csi…) korábban egyformán regeneráltak — most
WoW-osan eltérő profillal töltődnek.
**Hogyan valósult meg:** `spells.resource.class.*` config-szekció: **Düh**-típus (harcos,
halállovag, démonvadász) harcon kívül lassan ürül (2/mp), ütésenként +8 töltődik, harcban
lassú regen fut (3/mp, lövedék-találat is beleszámít); **Energia**-típus gyors regen
(orgyilkos/szerzetes 14/mp, íjász 11/mp); **Mana**-típus nagy pool + lomha regen (7/mp, 110-120
max). A HUD-sáv max-értéke is kaszt szerinti.
**Miért jó:** A 13 kaszt *érzésre* is különbözik, nem csak spell-listában — mélyebb kaszt-
identitás, retention-tényező a hardcore rotáció-optimalizáló játékosoknak.
**Építőkövek:** `ResourceManager`, `classes.yml`.
**Buktatók:** —

### A4. Spell-kedvencek a görgetéshez `[KÉSZ]`
🟢 • ⭐⭐ • **Státusz:** ✅ KÉSZ

**Mi ez:** 20+ feloldott spellnél a shift+görgetéses spell-váltás fárasztó volt — most
kedvencnek jelölhetők a leggyakrabban használt spellek.
**Hogyan valósult meg:** A Spellbookban shift-katt egy feloldott spellen → ★ jelölés (PDC-
lista); ha van legalább egy kedvenc, a katalizátorral lopakodva görgetés csak a kedvenceket
lépkedi (action bar: „★1/3"), üres lista esetén a teljes feloldott listát. A spellkönyv
tölcsér-gombja emellett „csak feloldottak" szűrőt is kapcsol (lásd A11).
**Miért jó:** Gyors rotáció-váltás harc közben, kevesebb véletlen rossz spell-cast.
**Építőkövek:** `SpellbookGUI`, `SpellbookListener`, `AbilityCatalystListener` (görgetés-kezelés).
**Buktatók:** —

### A5. First-join onboarding quest-lánc `[KÉSZ]`
🟡 • ⭐⭐⭐ • **Státusz:** ✅ KÉSZ

**Mi ez:** Az új játékos első 10 percét vezető, automatikusan induló kezdő quest-lánc.
**Hogyan valósult meg:** Join-kor auto-indul a „Beszélj a hírnökkel" (`onboarding_herald`)
quest; teljesítéskor a `next` mezőn keresztül auto-láncolva „Első csata" (5 szörny) → „Első
gyűjtögetés" (10 rönk, jutalom valuta+csákány+kenyér). Feltétel a szerveren egy `hirnok" nevű
FancyNpcs NPC a semleges fővárosban (TALK_TO_NPC objektíva). Kikapcsolható:
`quests.yml` → `onboarding.enabled: false`; meglévő játékosnál nem indul újra.
**Miért jó:** Az első benyomás vezetett, nem üres — kritikus az új játékosok megtartásához egy
komplex MMO-rendszerben.
**Építőkövek:** `QuestManager`, TALK_TO_NPC objektíva, FancyNpcs-híd.
**Buktatók:** —

### A6. Aukció/piac polish + /market stats `[KÉSZ]`
🟢 • ⭐⭐ • **Státusz:** ✅ KÉSZ

**Mi ez:** A piac láthatóságát javító polish: buy-out tooltip pontosítás + összesítő parancs.
**Hogyan valósult meg:** `/market stats` (mindenkinek elérhető) kiírja az aktuális listingek és
az utolsó max. 50 eladás összesítését (`MarketManager.getStats()` → `MarketStats`); a
buy-out ár és a ténylegesen levont összeg immár konzisztensen jelenik meg a vásárlási
üzenetben és az aukciós GUI-ban (shift-katt = azonnali buy-out).
**Miért jó:** A dinamikus árfolyamú gazdaság átláthatóbb — a játékos adatból dönt, nem
tapogatózik.
**Építőkövek:** `MarketManager`, `MarketCommand`.
**Buktatók:** —

### A7. Katalizátor-cooldown vizuálisan a hotbaron `[KÉSZ]`
🟢 • ⭐⭐⭐ • **Státusz:** ✅ KÉSZ

**Mi ez:** A katalizátor-item cooldownja a vanília szürke „töltődés-overlay" formájában is
látszik a hotbaron.
**Hogyan valósult meg:** Cast után `Player#setCooldown(material, ticks)` hívás a katalizátor
anyagára (`AbilityCatalystListener`) — azonnal látszik, mikor castolható újra, action-bar
számolgatás nélkül.
**Miért jó:** Kis munkából nagy, azonnal érezhető UX-nyereség — a legtöbb visszajelzett „ez a
polish tetszett" tétel.
**Építőkövek:** vanília `Player#setCooldown` API.
**Buktatók:** —

### A8. Sebzés-számok / hit-visszajelzés `[KÉSZ]`
🟡 • ⭐⭐ • **Státusz:** ✅ KÉSZ

**Mi ez:** Spell-találatkor lebegő sebzés-szám a célpont fölött.
**Hogyan valósult meg:** `TextDisplay`-alapú lebegő számok (~1 mp élettartam), játékos-áldozatnál
piros, mobnál sárga; gyors sorozat-ütésnél nem spammel (250 ms limit/célpont); lövedékes (íj)
találatnál is a lövő kapja. Láthatóság configolható: alapból csak a sebző látja
(`attacker-only`), `everyone`-ra állítható; teljesen kikapcsolható
(`spells.damage-indicators.enabled: false`).
**Miért jó:** Azonnali, vizuális visszajelzés a rotáció hatékonyságáról; aki zavarónak találja,
kikapcsolhatja.
**Építőkövek:** hologram-infra (`TextDisplay`), `spells.damage-indicators.*` config.
**Buktatók:** —

### A9. Halál-összegző (death recap) `[KÉSZ]`
🟡 • ⭐⭐ • **Státusz:** ✅ KÉSZ

**Mi ez:** Halál után chat-összegző: ki/mi ölt meg, az utolsó mp-ek sebzés-forrásai.
**Hogyan valósult meg:** Halálkor a chatben az utolsó 10 mp sebzései (max 5 sor, pl. „-2.5❤
Zombi (3,2 mp-e)", lövedéknél a lövő zárójelben) + összesített sebzés; ring-buffer per player,
entitás- vs. környezeti sebzés nem duplázódik. Kikapcsolható:
`spells.death-recap.enabled: false`.
**Miért jó:** A PvP-szerver örök „mi ölt meg??" kérdésére ad azonnali választ — kevesebb
support-kérdés, több tanulható visszajelzés.
**Építőkövek:** combat-event listenerek, per-player ring-buffer.
**Buktatók:** —

### A10. Világboss-telegraph polish `[KÉSZ]`
🟢 • ⭐⭐ • **Státusz:** ✅ KÉSZ

**Mi ez:** A világboss SLAM/ZONE speciáljainak jelzése hangosabb és olvashatóbb lett.
**Hogyan valósult meg:** A SLAM/ZONE special előtt részecske-gyűrű rajzolja ki a veszélyzónát
(5, illetve 3 blokk sugár) + Warden-hang; SUMMON előtt Evoker-idéző hang szól.
**Miért jó:** A special kivédhetőbb, a világboss-harc olvashatóbb — kevesebb „honnan jött ez a
sebzés" panasz.
**Építőkövek:** világboss-esemény telegraph-infra, partikel/hang API.
**Buktatók:** —

### A11. Spellbook rendezés/szűrés `[KÉSZ]`
🟢 • ⭐ • **Státusz:** ✅ KÉSZ

**Mi ez:** A Spellbook GUI-ban a feloldott spellek elöl, szint szerint rendezve, szűrhetően.
**Hogyan valósult meg:** A spellkönyv tölcsér-gombja kapcsolja a „csak feloldottak" szűrőt
(`SpellbookGUI`/`SpellbookListener`); a listázás szint szerint rendezett.
**Miért jó:** Tiszta, gyorsan áttekinthető GUI 20+ spellnél is.
**Építőkövek:** `SpellbookGUI`, `SpellbookHolder`.
**Buktatók:** —

### A12. HUD-testreszabás `[KÉSZ]`
🟡 • ⭐⭐ • **Státusz:** ✅ KÉSZ

**Mi ez:** A HUD-oldalsáv szekciói egyenként ki-bekapcsolhatók.
**Hogyan valósult meg:** `/hud toggle <szekció|mind>` (`HudCommand`) a `HudManager.SECTIONS`
készletén (párt, erőforrás, esemény-sáv…) váltja a láthatóságot, PDC-ben tárolva
(`toggleSection`/`hiddenSections`); `/hud toggle mind` az egész sidebart ki/bekapcsolja.
**Miért jó:** Aki külső scoreboard-pluginnal (TAB) ütközne, vagy egyszerűen letisztultabb
képernyőt akar, saját ízlésére szabhatja.
**Építőkövek:** `HudManager`, `hud.sidebar-enabled`/`hud.tablist-enabled` config.
**Buktatók:** —

### A13. Claim-GUI `[KÉSZ]`
🟡 • ⭐⭐ • **Státusz:** ✅ KÉSZ

**Mi ez:** A megbízott-kezelés (trust/untrust) GUI-ból, parancs-pötyögés nélkül.
**Hogyan valósult meg:** `/menu` → Birtok → „Megbízottak kezelése" (vagy `/claim trustgui`):
felül a megbízottak fejei (katt = visszavonás), alul a 15 blokkon belüli játékosok (katt =
megbízás) — a kattintás a `/claim trust|untrust` parancsot futtatja, a GUI a RUN:-mintát
követve frissül (`ClaimTrustGUI`/`ClaimTrustGUIListener`).
**Miért jó:** A gameplay-logika a parancsban marad (konvenció), a GUI csak kényelmi réteg —
gyorsabb, mint névre emlékezni és begépelni.
**Építőkövek:** `ClaimManager`, `/menu` RUN:-minta.
**Buktatók:** A teljes claim-műveleti kör (y-bővítés, raid-lootable kapcsoló, határ-mutatás)
egyelőre a trust-GUI-ra korlátozódik — bővítése a következő kör része lehet.

### A14. „Mi történik most?" esemény-oldal `[KÉSZ]`
🟢 • ⭐⭐ • **Státusz:** ✅ KÉSZ

**Mi ez:** Egy parancs, ami kilistázza az összes épp aktív világeseményt.
**Hogyan valósult meg:** `/events status` (mindenkinek): az összes aktív világesemény
(vérhold/boss/invázió/karaván/gyűjtögető/kincs/Vad Hajsza/bőség/kihívás/kíséret/meteor)
hátralévő idővel + a szezon-állás; üresen „nyugalom van" üzenet. A `/menu` → Események almenü
tetején ugyanez óra-ikonként, kattintásra lefuttatva.
**Miért jó:** A játékos egy pillantásból tudja, érdemes-e most részt venni valamiben — nem kell
a chatlogot visszapörgetni.
**Építőkövek:** esemény-managerek meglévő állapot-getterei, `CommandMenus`.
**Buktatók:** —

### A15. Statisztika-profil `[KÉSZ]`
🟡 • ⭐ • **Státusz:** ✅ KÉSZ

**Mi ez:** Személyes statisztika-lap: killek, halálok, K/D, castolt spellek, teljesített
questek.
**Hogyan valósult meg:** `/stats [név]` — saját vagy (online/már látott) másik játékos
profilja; a számlálók ölés/halál/cast/quest-teljesítés után nőnek és restart után is
megmaradnak (YamlStore).
**Miért jó:** Versenyszellem, önmérés — a StatsManager ranglista-infrájának természetes
kiegészítője.
**Építőkövek:** `StatsManager`.
**Buktatók:** minden új számláló hot-path írás — bővítésnél figyelni kell a régió-szálas
terhelésre.

### A16. Kombó-rendszer mélyítés `[KÉSZ]`
🟡 • ⭐⭐ • **Státusz:** ✅ KÉSZ

**Mi ez:** A pár-alapú spell-kombó (gyorsabb felépülés) mellé 3 lépéses kombó-láncok.
**Hogyan valósult meg:** `spells.combos.chains` config (pl. varázsló: Fagyérintés → Arkán
Lökés → Tűzgolyó) — mindhárom lépés az időablakon belül → a finisher +25% erővel sül el
(„⚡ Kombó-lánc befejező!") + cooldown-visszatérítés; az action bar mutatja a nyíló
kombó-ablakot és a lánc következő lépését.
**Miért jó:** Mélyebb rotáció-tervezés a haladó játékosoknak, vizuális jutalomérzet a helyes
sorrendért.
**Építőkövek:** kombó-infra (`spells.combos.pairs` + `chains`), action bar visszajelzés.
**Buktatók:** —

### A17. Teljes HP-rendszer átdolgozása `[TULAJ KÉRÉSE — kidolgozott javaslat, külön körben]`
🔴 • ⭐⭐⭐

**Mi ez:** A vanília 20 HP + étel-regen nem illik egy MMO-jellegű szerverhez: minden kaszt
ugyanannyit bír, a harc kimenetelét a golden apple / étel-spam dönti el, a spell-sebzésszámok
pedig a 10 szíves skálán túl durva lépcsőkben hangolhatók.

**Hogyan működne (fázisokban):**
1. **Kasztonkénti alap-HP profilok** — `GENERIC_MAX_HEALTH` attribútum-módosítóval (a
   talent-rendszer max-health effektje már pontosan így dolgozik, ugyanaz a plumbing):
   tank/melee (harcos, paplovag, halállovag) ~26-30 HP, hibrid (szerzetes, démonvadász,
   sámán, druida) ~22-24, íjász/orgyilkos ~20, caster (varázsló, pap, boszorkánymester,
   evoker) ~16-18. Config: `classes.yml` → `<kaszt>.base-health`, szint-skálázással
   (`health-per-level`, pl. +0,2/szint, cap).
2. **Szív-kijelzés normalizálás** — `player.setHealthScale(20)` mindenkinél, hogy a több
   HP ne csúfítsa el a hotbart (a tényleges értéket a HUD/`/stats` mutassa számmal).
3. **Regen-átdolgozás** — a vanília saturation-regen kikapcsolása (gamerule vagy
   `naturalRegeneration` off + saját tick): étel = éhség-költség fedezet, a gyógyulás
   forrása harcon kívüli lassú regen (pl. 1 HP/2 mp, 8 mp-cel az utolsó sebződés után —
   a ResourceManager lastCombat mintája újrahasznosítható) + gyógyító spellek/szakma-ételek.
   Így a healer-spec és a Szakács tényleges értéket kap.
4. **Sebzés-újrahangolás** — a spell-balance értékek átnézése az új HP-skálán (a
   spell-balance.yml live-read rendszere miatt ez restart nélkül iterálható); a
   sebzés-számok (A8) és a halál-összegző (A9) adja hozzá a visszajelzést, a C1
   spell-statisztika az adatot.
5. **Pajzs/absorption egységesítés** — a meglévő pajzs-jellegű effektek (Devotion Aura
   reflect, Bulwark, absorption-adó spellek) közös „shield" rétegbe terelése, hogy a
   HUD-on és a death recapben is egyértelmű legyen.
6. **Kapcsolódások** — talent max-health effekt az ÚJ alapra épüljön (százalékos, ne fix);
   faction-passzívák és relikviák HP-bónuszai auditálandók; PLAYTEST külön fejezetet kap.

**Miért jó:** A kasztok végre HP-ben is érezhetően különböznek (tank vs. caster identitás),
a harc kimenetelét a rotáció/pozicionálás dönti el, nem a golden apple-spam — mélyebb,
kompetitívebb PvP/PvE.

**Építőkövek:** talent max-health plumbing, `ResourceManager` lastCombat-minta,
`spells-balance.yml` live-read, `classes.yml`.

**Buktatók:** Kockázat: minden harci rendszert érint (spell-balansz, mob-skálázás, világboss,
raid) — külön ágon, teljes playtest-körrel érdemes, NEM a mostani A-hullám része.

### A18. Spell-loadoutok (kedvenc-készletek)
🟢 • ⭐⭐

**Mi ez:** Az A4 kedvencekre építve 2-3 elmenthető kedvenc-készlet (pl. „PvP" / „farm" / „boss").
**Hogyan működne:** Az A4-ből ismert PDC-lista mintát bővítve 2-3 külön csv-lista tárolása
(`loadout-1/2/3`), váltás a spellkönyv fejlécéből (nyíl-gombok) vagy `/spellbook loadout <n>`
paranccsal; az aktív loadout indexe is PDC-ben, a katalizátoros shift+görgetés mindig a
kiválasztott loadoutot lépkedi, az action bar mutatja melyik aktív („Készlet: PvP").
**Miért jó:** PvP/farm/boss helyzetekhez külön optimalizált rotáció gyors váltással — kaszt-
játékérzet nagy dobása kis munkából, mert a PDC-infra már megvan A4-ből.
**Építőkövek:** A4 kedvenc-PDC minta, `SpellbookGUI`, `AbilityCatalystListener` görgetés-logika.
**Buktatók:** a 3 készlet elférjen a spellkönyv fejlécén, ne zsúfolja túl a GUI-t.

### A19. Kombó-jelzések a spellkönyvben
🟢 • ⭐

**Mi ez:** A spell-csempe lore-jában jelezze, ha a spell egy kombó-pár vagy -lánc tagja.
**Hogyan működne:** A `spells.combos.pairs`/`chains` configból generált lore-sor („⚡ Kombó:
Fagyérintés → EZ → Tűzgolyó") a `SpellbookGUI` csempe-építésébe kerül; statikus, csak GUI-
nyitáskori számítás, nincs tick-frissítés szükséges.
**Miért jó:** A játékos a GUI-ból tanulja a kombó-láncokat, nem külső wikiről — az A16
kombó-mélyítés befektetése jobban kihasználttá válik.
**Építőkövek:** A16 kombó-config, `SpellbookGUI`.
**Buktatók:** hosszú láncoknál a lore ne legyen túl zsúfolt (max 1-2 sor).

### A20. Quest-tracker a HUD-on
🟡 • ⭐⭐

**Mi ez:** Egy kiválasztott („követett") quest objektíva-állása állandóan az oldalsávon.
**Hogyan működne:** `/quest track <id>` parancs vagy a küldetésnapló GUI-ból kattintva állítja
be a követett questet (PDC-kulcs); a `HudManager` egy új, A12 toggle-lal kapuzható szekciót épít
a party-szekció mintájára, ami a `QuestManager` haladás-állapotát olvassa ki minden HUD-
frissítéskor a játékos saját régió-szálán (nincs extra scheduler-hop).
**Miért jó:** A haladás ma csak action-barban villan fel röviden — az állandó tracker nem hagyja
elfelejteni a küldetést, különösen többlépéses (ALL/SEQUENCE) questeknél.
**Építőkövek:** `HudManager` (A12 szekció-minta), `QuestManager`, HUD party-szekció mint minta.
**Buktatók:** több aktív quest esetén egyértelmű kiválasztás kell (utoljára elfogadott vagy
explicit track) — kerülni kell az oldalsáv túlzsúfolását.

### A21. Halál-pont visszajelzés
🟢 • ⭐⭐

**Mi ez:** Halál után a halál helyének kiírása és rövid ideig élő irány-jelző.
**Hogyan működne:** Az A9 death recap mellé a chatbe kiírja a halál koordinátáit + világot;
emellett rövid ideig (pl. 2 perc) élő action bar iránytű („⚰ 214 blokk ÉK felé"), amit egy
dedikált feladat számol a játékos aktuális pozíciójából a mentett halál-pontig, a saját
`entity.getScheduler()`-én futva.
**Miért jó:** A cucc-visszaszerzés frusztrációját csökkenti, köztes megoldás a keep-inventory és
a full-loot között; a lebomló sír (lásd B34) natúr nagyobb testvére, de önmagában is elég.
**Építőkövek:** A9 death recap infra, action bar API.
**Buktatók:** sok halál esetén csak a legutóbbi pont kövessen, régi jelzők ne halmozódjanak.

### A22. Ranglisták bővítése az új statokból
🟢 • ⭐

**Mi ez:** Az A15 statisztika-profil számlálói (K/D, mob-ölés, spell-cast, quest) kerüljenek fel
a `/leaderboard`-ra új kategóriákként.
**Hogyan működne:** A `StatsManager` Category enumjának bővítése (KILLS, DEATHS, KD, MOB_KILLS,
SPELLS_CAST, QUESTS_COMPLETED) és a `top()` lekérdezés ugyanarra a YAML-alapú számláló-tárra
épül, amit az A15 már ír — nincs új adatforrás, csak új nézet.
**Miért jó:** Olcsó bővítés, ami versennyé teszi a statisztika-profilt — közvetlen célt ad a
versengő játékosrétegnek.
**Építőkövek:** `StatsManager` (A15-ből), `/leaderboard` GUI.
**Buktatók:** sok kategóriánál a leaderboard GUI-nak lapozás kell (más listáknál már létező minta).

### A23. NPC-dialógus polish
🟢 • ⭐

**Mi ez:** A quest-dialógusok prezentációs csiszolása: hang, írógép-effekt, folytatás-jelzés.
**Hogyan működne:** Beszélőnkénti hang (falusi hümmögés, kürt a hírnöknél) minden dialógus-sor
kiírásakor; írógép-effekt (soronkénti késleltetett kiírás `getGlobalRegionScheduler()`-rel
ütemezve, mivel csak a nézőnek szóló chat-üzenetről van szó); `<gomb>`-stílusú
folytatás-jelzés a sor végén.
**Miért jó:** Tisztán prezentációs réteg a meglévő `dialogue.give`/`dialogue.complete`
mechanikára — a questek „élőbbnek" hatnak minimális kockázattal.
**Építőkövek:** `QuestManager` dialógus-küldés, globális scheduler.
**Buktatók:** az írógép-effekt ne lassítsa/blokkolja a quest-progressziót — csak vizuális, a
teljesítés-logika ne várjon rá.

### A24. Szakma-recept kedvencek + keresés
🟢 • ⭐

**Mi ez:** A recept-katalógus GUI-ba keresőmező és kedvenc-csillagozás.
**Hogyan működne:** Anvil-input vagy chat-prompt minta (a quest builderből ismert mintázat) a
receptkönyv fejlécén szöveges szűrésre; kedvenc-csillagozás az A4 PDC-mintájával (recept-id
lista), a csillagozott receptek a lista elejére kerülnek.
**Miért jó:** 50+ receptnél a lapozgatás fájdalmas — a szakma-rendszer A11/A4-hez hasonló
kényelmi párja.
**Építőkövek:** recept-katalógus GUI, A4 kedvenc-PDC minta, anvil-input minta (quest builder).
**Buktatók:** —

### A25. Spell-adatlap a spellkönyvben
🟢 • ⭐⭐

**Mi ez:** A spell-csempére jobb-katt egy részletes adatlapot nyit a tényleges, élő számokkal.
**Hogyan működne:** A LIVE spell-balance értékekből (`spells-balance.yml` live-read) számolt
sebzés/gyógyítás/időtartam, a mastery-rang és a dinamikus szorzók MÁR beszorozva („nálad: 7,2
❤"); a `SpellbookGUI` egy második réteg GUI-t (vagy bővebb lore-t) nyit a caster aktuális
mastery-adatai alapján.
**Miért jó:** A játékos ne fejben számoljon — a rendszer tudja a számokat, ami a hibrid
erőforrás-rendszer (A3) mellett különösen fontos átláthatóság.
**Építőkövek:** `spells-balance.yml` live-read, mastery-rendszer (`/spell upgrade`),
`SpellbookGUI`.
**Buktatók:** —

### A26. Élő cooldown a spellkönyvben
🟢 • ⭐

**Mi ez:** A spell-csempe lore-jában az aktuális hátralévő cooldown megjelenítése.
**Hogyan működne:** A meglévő `getRemainingCooldown` API-t a GUI-nyitás pillanatában (nem
tick-frissítve) beköti a csempe-építésbe, a lore egy sorába („⏳ Hátralévő: 4 mp").
**Miért jó:** Nem kell kilépni a GUI-ból az action bar cooldown-számláláshoz — egy pillantásból
látszik minden spell állapota.
**Építőkövek:** cooldown-API (`AbilityCatalystListener`), `SpellbookGUI`.
**Buktatók:** —

### A27. Okos gyógyítás (party-célzás)
🟡 • ⭐⭐⭐

**Mi ez:** Gyógyító spellek sneak-cast változata: automatikusan a legsebzettebb, látótávon
belüli párttagra megy.
**Hogyan működne:** Lopakodva castolva a healer-spellek a legalacsonyabb HP-arányú párttagot
választják célpontnak (a HUD party-szekció élet-adatai már ismertek); a célválasztás a caster
régió-szálán fut, a tényleges gyógyítás-alkalmazás a célpont schedulerén keresztül hop-ol
(**Folia:** `target.getScheduler().run(plugin, task -> {...}, null)`).
**Miért jó:** Folián célkereszttel gyógyítani körülményes csapatharcban — ez a WoW-os „mouseover
heal" megfelelője, élvezetesebbé teszi a healer-játékstílust.
**Építőkövek:** `PartyManager` (HP-adatok), A2 targeting-infra, Folia scheduler-hop minta.
**Buktatók:** látótáv/akadály-ellenőrzés kell, nehogy falon át gyógyítson; egyforma sebzettségnél
determinisztikus tie-break szükséges.

### A28. Menü-badge-ek (piros pötty)
🟡 • ⭐⭐

**Mi ez:** A /menu és almenü-csempéken jelzés, ha van teendő (felvehető talentpont, kész napi
quest, lejárt piaci tétel, felvehető heti megbízás).
**Hogyan működne:** Közös „van-e teendő?" lekérdező réteg a managerek meglévő gettereire
(TalentManager, QuestManager napi-quest állapot, MarketManager lejárt tétel); a `CommandMenus`
GUI-nyitáskor a csempe lore-jába vagy glow-enchant-be (`addEnchant` + `HIDE_ENCHANTS`) rajzolja
a jelzést.
**Miért jó:** A játékos nem felejt el felvehető jutalmakat/pontokat — retention-tényező kevés
munkából.
**Építőkövek:** TalentManager, QuestManager, MarketManager, `CommandMenus`.
**Buktatók:** minden lekérdezés GUI-nyitáskor fusson le, ne terhelje túl a menü-megnyitást — rövid
TTL-es cache-eléssel érdemes.

### A29. Elit/boss mobok vizuális megkülönböztetése
🟢 • ⭐

**Mi ez:** A mob-szintezés `[Lvl X]` névtáblája mellé szín-kód és szimbólum az elit/boss mobokhoz.
**Hogyan működne:** A mob-szintezés névtábla-építő logikája kiegészül: invázió-bajnok/Vad
Hajsza-fenevad/boss-add esetén a névtábla ♦ (elit) vagy ☠ (boss) szimbólumot és eltérő
színkódot kap (pl. arany elit, sötétvörös boss) — ugyanott, ahol a szintezés a nevet építi.
**Miért jó:** Egy pillantásból látszik, mibe szalad bele a játékos — kevesebb váratlan halál
gyanútlan felfedezőknél.
**Építőkövek:** mob-szintezés névtábla-építő, InvasionManager/WildHunt bajnok-jelölés.
**Buktatók:** —

### A30. Cast-hiba üzenetek egységesítése
🟢 • ⭐

**Mi ez:** Ma többféle üzenet jön, ha nem sül el a spell (cooldown / kevés erőforrás / nincs
célpont / rossz forma).
**Hogyan működne:** Egységes formátum ikonokkal (⏳ cooldown / ⚡ erőforrás / 🎯 célpont), mindig
a hátralévő idővel/hiányzó mennyiséggel; egy közös üzenet-építő metódus (`MessageManager`
bővítés) minden cast-elutasítási ágból hívva, felváltva a jelenleg spell-osztályonként eltérő
szövegezést.
**Miért jó:** A frusztráció fele abból jön, hogy nem tudni, MIÉRT nem ment el a cast — ez a
tétel közvetlenül ezt oldja.
**Építőkövek:** `MessageManager`, `AbilityCatalystListener`, `ConfiguredSpell` cast-elutasítási
ágak.
**Buktatók:** sok cast-ágat érint egyszerre — regresszió-veszély, alapos manuális playtest kell
minden kaszthoz.

### A31. Frakció-infó oldal passzívákkal
🟢 • ⭐

**Mi ez:** A `/faction info` (és a /menu Frakció almenü) írja ki a frakció-passzívák tényleges
értékeit configból.
**Hogyan működne:** A `FactionPassiveListener` mögötti config-értékek (pl. Semleges tax-exempt,
Sötét wither-immunitás) kiolvasása és megjelenítése a `/faction info` szövegében, nem csak a
guide-ban leírva; a `/menu` Frakció almenü csempe-lore-ja ugyanezt mutatja.
**Miért jó:** A számszerű állítás = config elv itt is érvényesül — a játékosnak nem kell külső
dokumentációt keresnie a saját passzívájához.
**Építőkövek:** `FactionPassiveListener`, `/faction info`, `CommandMenus`.
**Buktatók:** —

### A32. Színvak-barát HUD-mód
🟢 • ⭐

**Mi ez:** A piros/zöld/sárga élet- és erőforrás-sávok mellé szimbólum-alapú, magasabb
kontrasztú mód.
**Hogyan működne:** `/hud colorblind` (A12 HUD-toggle mintájára, PDC-flag) — bekapcsolva a
`HudManager` a szín mellé/helyett szimbólumokat rajzol a sávokba (▲ tele, ▼ kritikus, ◆
közepes) és magasabb kontrasztú palettát választ.
**Miért jó:** Hozzáférhetőségi fejlesztés kevés munkával — a színvak játékosok is egyértelműen
olvassák a HUD-ot harc közben.
**Építőkövek:** `HudManager` (A12 toggle-infra).
**Buktatók:** —

### A33. Katalizátor-skinek (CMD)
🟢 • ⭐

**Mi ez:** Kasztonként egyedi custom model data a katalizátor-itemen.
**Hogyan működne:** A RESOURCE_PACK_CMD.md folyamat (a dobó-spelleknél már kitaposott, pl.
Vadgomba 6101/Rúnakő 6102) alapján minden kaszt katalizátora kap egy CMD-értéket; resource
pack cserélhető textúrával, az alap-item és a funkció változatlan marad.
**Miért jó:** A 13 kaszt vizuális identitása a kézben is látszik, nemcsak a spell-listában —
kozmetikai, de erős immerzió-nyereség (később kozmetika-boltba is köthető, lásd B9).
**Építőkövek:** RESOURCE_PACK_CMD.md folyamat, `items/*ItemFactory` PDC-tagek.
**Buktatók:** resource pack disztribúció szerver-oldali kérdés, nem plugin-kód.

---

## Bővítés — A34-től (QoL / polish az A-kör folytatásaként)

### A34. Rövid, mobil-barát üzenet-mód
🟢 • ⭐

**Mi ez:** Sok üzenet (spellbook lore, cast-hiba, HUD-sáv) hosszú magyar szöveget használ, ami
kis GUI-skálán vagy alacsonyabb felbontáson (pl. Bedrock-átjárós/mobil klienssel csatlakozó
játékos) csonkolva jelenik meg.
**Hogyan működne:** `/hud compact` kapcsoló (A12 toggle-mintájára, PDC-flag) — bekapcsolva a HUD-
sáv és az action bar üzenetek rövidített változatot használnak (pl. „Nincs elég mana!" helyett
„⚡ Kevés mana"); a `MessageManager` minden érintett kulcshoz egy `-compact` variánst is tárol
`messages.yml`-ben, a HUD/üzenet-küldő a flag alapján választ.
**Miért jó:** A kisebb képernyőn vagy alacsonyabb GUI-skálán játszóknak is olvasható marad a
HUD — hozzáférhetőségi és kényelmi tétel egyben, ami a mobil/kontroller-közeli játékmódot is
kiszolgálja.
**Építőkövek:** `MessageManager` (kulcs-pár minta), `HudManager`, A12 toggle-infra.
**Buktatók:** két üzenet-verziót kell karbantartani minden érintett kulcsnál — hosszú távon a
karbantartási teher nő, ezért csak a leggyakoribb HUD/action bar kulcsokra érdemes bevezetni.

### A35. Katalizátor akció-bar sáv finomítás (dinamikus szín)
🟢 • ⭐⭐

**Mi ez:** Az erő-csík HUD-megjelenítése jelenleg egyszínű sáv — a töltöttségi szint szerinti
szín-átmenet (zöld→sárga→piros) azonnali vizuális visszajelzést adna kritikusan alacsony
erőforrásnál.
**Hogyan működne:** A `HudManager` sáv-építő logikája a jelenlegi/max arány alapján válasszon
színkódot a sáv-karakterekhez (pl. `§a`/`§e`/`§c`); a küszöbök configolhatók
(`hud.resource-bar.thresholds`). A meglévő `HudSnapshot` frissítési ciklusba illesztve, a
játékos saját régió-szálán, nincs extra scheduler-hívás.
**Miért jó:** A játékos harc közben a periférián is észreveszi, ha kritikusan alacsony az
erőforrása — kevesebb véletlen „nem volt mana" cast-kudarc.
**Építőkövek:** `HudManager`, `HudSnapshot`.
**Buktatók:** színvak-barát módban (A32) más jelzésre van szükség — a két tételt össze kell
hangolni, hogy ne ütközzenek.

### A36. Spellbook keresőmező
🟢 • ⭐

**Mi ez:** 20+ feloldott spellnél a spellkönyvben szöveges keresés is segítene a szűrőn (A11)
és kedvenceken (A4) felül.
**Hogyan működne:** Anvil-input vagy chat-prompt minta (a quest builderből ismert mintázat) a
Spellbook fejlécén; a beírt részszöveg a spell-névre szűr, a `SpellbookGUI` építő-logikáját
újrafuttatva frissül.
**Miért jó:** Sok kaszt spelljei tematikusan hasonló nevűek — kereséssel gyorsabb megtalálni
egy adott képességet, mint lapozgatással.
**Építőkövek:** `SpellbookGUI`, `SpellbookListener`, anvil-input minta (quest builder).
**Buktatók:** —

### A37. Menü-breadcrumb és „vissza" konzisztencia
🟢 • ⭐

**Mi ez:** A `/menu` beágyazott almenüiben (pl. Frakció → Bank → Valutaváltó szintig) nincs
egységes „hova jutottam" jelzés vagy azonos helyen lévő visszalépés-gomb.
**Hogyan működne:** A GUI-cím sorába rövid útvonal-jelzés kerül (`Menü › Bank & Pénz ›
Valutaváltó`), és minden almenü első slotjában egységes „← Vissza" gomb (`MENU:<szülő>`
akció-string, a meglévő minta szerint) — tisztán `CommandMenus` réteg, gameplay-logikát nem
érint.
**Miért jó:** Mobil/kontroller-barát navigáció, kevesebb „hogy jutok vissza" tévelygés mély
menükben.
**Építőkövek:** `CommandMenus`, `CommandMenuHolder`.
**Buktatók:** minden almenü-építőt egységesen kell módosítani — sok kis, ismétlődő szerkesztés.

### A38. Első belépés spawn-élmény polish

→ **Átkerült a lore-kiemelt válogatásba:** [L-lore-kiemelt.md](L-lore-kiemelt.md)

### A39. Inventory-rendezés gomb katalizátor mellett
🟢 • ⭐

**Mi ez:** Gyors inventory-rendezés (sort) egy kattintással, hogy a katalizátor/szakma-
alapanyagok ne keveredjenek a lootban.
**Hogyan működne:** Sneak + jobb-katt üres levegőre (vagy `/inv sort` parancs) rendezi a
hátizsákot kategória szerint (fegyver/páncél/alapanyag/egyedi); az egyedi szakma-alapanyagokat
(saját CustomModelData 6000–6013) és a katalizátort a rendezés kihagyja, hogy a hotbar-pozíció
stabil maradjon.
**Miért jó:** Gyűjtögető-intenzív szakma-rendszernél (50+ recept) a manuális rendezgetés
fárasztó — tiszta kényelmi funkció, ami a loot-mennyiséget kezelhetőbbé teszi.
**Építőkövek:** `items/*ItemFactory` PDC-tagek (kategória-felismerés).
**Buktatók:** a hotbar-slot ne csússzon el rendezéskor (a katalizátor mindig ugyanott legyen) —
külön kizárás kell rá.

### A40. Spell-cast hangkönyvtár egységesítés
🟢 • ⭐

**Mi ez:** Jelenleg spellenként eltérő „érzetű" hangok szólnak castkor — némelyik erősebb,
némelyik alig hallható.
**Hogyan működne:** Központi hangerő/pitch-tábla kaszt-onként és sebzés-kategóriánként
(gyenge/közepes/erős cast) a hangzásvilág konzisztenciájáért; a `ConfiguredSpell.builder(...)`
egy opcionális `soundProfile` mezőt kap, ami a táblából olvas cast-kor.
**Miért jó:** A hangzásvilág önmagában is visszajelzés a spell erejéről — konzisztens hangok
professzionálisabb érzetet adnak a szervernek.
**Építőkövek:** `ConfiguredSpell`, `SpellCatalog`.
**Buktatók:** sok spellt egyszerre érint — playteszttel ellenőrizni kell, hogy egyik kaszt se
lett „túl hangos" vagy „néma".

### A41. Teleport-becsapódás vizuális jelzés
🟢 • ⭐

**Mi ez:** A `teleportAsync`-alapú spell- és kaszt-teleportok (pl. Shadowstep, hazatérés-kő,
frakció-spawn-váltás) érkezéskor nem adnak vizuális visszajelzést.
**Hogyan működne:** Érkezéskor rövid partikel-effekt (portál-részecske gyűrű) + halk hang a
célponton, a `teleportAsync` completion-callback-jéből indítva — a callback már a célpont
régió-szálán fut, nincs extra scheduler-hop szükséges.
**Miért jó:** Kis, de érezhető polish minden teleport-jellegű mechanikára — konzisztensebb,
„varázslatosabb" érzet minden villanásnál.
**Építőkövek:** `teleportAsync` callback, `ShadowstepSpell` mint minta.
**Buktatók:** sok helyről hívva (relikvia, spell, admin-parancs) — egy közös util-metódusba
érdemes kiszervezni, hogy ne duplikálódjon a kód.

### A42. Profil-GUI összefoglaló fül
🟡 • ⭐⭐

**Mi ez:** A `/profile` GUI jelenleg kaszt/talent/szakma almenükre bontott — hiányzik egy
„mindent egy lapon" áttekintő fül.
**Hogyan működne:** A `/profile` főoldalán egy „Áttekintés" csempe, ami a `/stats` (A15) fő
számait, az aktív specializációt, a talentpont-egyenleget és a frakció-státuszt egy GUI-lapon
összegzi (lore-szövegben) — tisztán olvasó nézet, kattintás nélkül delegál a megfelelő
almenübe (`MENU:`-minta).
**Miért jó:** Új és visszatérő játékosnak egy pillantásból látszik „hol tart" — csökkenti az
almenük közti ugrálást, és a badge-ekkel (A28) párban proaktívan jelzi a teendőket is.
**Építőkövek:** `CommandMenus`, `StatsManager`, meglévő profil-GUI építő.
**Buktatók:** —

---

## Továbbfejlesztési kör a natív plugin-kiváltásokhoz (A43–A52) — ✅ IMPLEMENTÁLVA

### A43. Relációs tablist-színek (háború-tudatos nevek)
**Munka:** 🟡 • **Érték:** ⭐⭐⭐

**Mi ez:** Raid/háború alatt az ELLENSÉGES frakció tagjainak neve pirosas árnyalatot kap a tablistában és a fej fölött — nézőnként másképp.
**Hogyan működne:** A nametag-teamek amúgy is a NÉZŐ saját scoreboardján élnek (TablistManager) — a `syncViewerBoard` a team-színt a néző↔célpont reláció szerint választaná: aktív raidben a szemben álló fél piros, szövetséges (B44) zöldes. A RaidManager aktív feleit és az isAlly-t (A2) olvassa; diff-elt, csak raid-állapotváltáskor megy ki csomag.
**Miért jó:** Harcban egy pillantásból látszik, ki ellenség — a TAB ezt sosem tudta volna (relációs szín per-viewer). A PvP-élmény legnagyobb olcsó dobása a friss tablist-infrán.
**Építőkövek:** TablistManager per-viewer teamek, RaidManager.getActiveRaid, SpellTargetingUtil.isAlly.
**Buktatók:** Rendezés-kulcs ne változzon relációtól (különben sorrend-ugrálás); csak a team-szín módosuljon.

### A44. AFK-játékosok hátrasorolása a tablistában
**Munka:** 🟢 • **Érték:** ⭐

**Mi ez:** Az AFK-jelölt játékosok a tablista aljára kerülnek (a rangjukon belül).
**Hogyan működne:** A TablistManager `sortKey`-e AFK esetén egy 'z'-közeli előtag-karaktert szúr a rang-karakter után; a diff-logika miatt a váltás egyetlen team-átrakás.
**Miért jó:** Az aktív játékosok elöl — ránézésre látszik, ki elérhető.
**Építőkövek:** TablistManager.sortKey, AfkManager.isAfk (már bekötve).
**Buktatók:** Gyakori AFK-váltakozás sorrend-ugrálást okoz — az afk-after-seconds elég nagy legyen.

### A45. Harc-fókusz: célpont-sor a HUD-on
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Harc-fókusz módban az oldalsáv tetején az UTOLJÁRA megütött célpont neve + élet-sávja.
**Hogyan működne:** A DamageIndicatorListener (A8) úgyis látja a találatokat: egy ConcurrentHashMap<UUID, célpont-UUID+idő>; a HudManager harc-fókusz ágában egy plusz sor a party-frame élet-sáv renderelőjével (mob-célpontnál a mob típusneve). 10 mp után a sor eltűnik.
**Miért jó:** WoW-os target-frame érzés — bossnál/duelben a legfontosabb hiányzó információ.
**Építőkövek:** DamageIndicatorListener, HudManager healthBar, harc-fókusz ág.
**Buktatók:** Cross-region entitás-olvasás — a party-frame try/catch mintáját kell követni.

### A46. AFK-kapuk a jutalom-rendszereken
**Munka:** 🟢 • **Érték:** ⭐⭐⭐

**Mi ez:** AFK-státuszban a kill-reward/kaszt-XP/szakma-XP nem jár — az AFK-farm exploit lezárása.
**Hogyan működne:** Az AfkManager.isAfk (már globális) kapuként a ClassXpListener/kill-reward/ProfessionXp útvonalak elején: AFK-nál csendes no-op (config: `afk.block-rewards: true`). Az AFK-zóna-jutalom persze kivétel.
**Miért jó:** A natív AFK-rendszer fő ígérete volt — auto-farm mellett AFK-zó játékos ne termeljen XP-t/pénzt.
**Építőkövek:** AfkManager.isAfk, a meglévő XP/reward-listenerek.
**Buktatók:** A mozgás-detektálás ne legyen kijátszható (AFK-gép: nézés-forgatás — yaw/pitch delta már aktivitásnak számít; megfontolandó a szigorítás: csak pozíció-változás számítson).

### A47. Crate-nyitás animáció (rulett-GUI)
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Kulcs-nyitáskor rövid „pörgetés": egy 27-es GUI-ban végigfutó item-sor, ami a nyereményen áll meg.
**Hogyan működne:** A CrateManager.open a sorsolás UTÁN nyit egy GUI-t; a pörgetést a néző entity-schedulerén futó ismétlődő task lépteti (0,1→0,5 mp lassulással, hanggal), a végén a középső slot a nyeremény + tényleges jóváírás. A GUI-minta (holder+listener, mindent cancel) kész.
**Miért jó:** A crate-élmény fele a dramaturgia — a CrazyCrates fő vonzereje pont ez volt.
**Építőkövek:** CrateManager, GUI-minta, entity-scheduler ismétlődő task (DamageIndicator despawn-minta).
**Buktatók:** A jutalom a pörgetés ELŐTT dőljön el (bezárt GUI = attól még jóváírás); task-leak ellen a close-event állítsa le a léptetőt.

### A48. Kulcs-források bekötése (crate a gazdaságba)
**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** A crate-kulcsok a meglévő jutalom-csatornákba: napi/heti kihívás, quest-jutalom, világesemény-loot, szezon-helyezés.
**Hogyan működne:** A quest-jutalom sémába `crate-key: <id>:<db>` mező; a DailyQuest/heti megbízás (B1) és a boss-loot táblák kulcs-itemet adhatnak (CrateKeyFactory.createKey). A /crate buy mellett így „megkeresett" kulcs is van.
**Miért jó:** A crate így nem csak sink, hanem motivációs hurok — a kulcs a visszatérés jutalma.
**Építőkövek:** CrateKeyFactory, QuestManager applyRewards, loot-táblák.
**Buktatók:** A kiosztott (ingyen) kulcsok aránya kontrollált legyen, különben a buy-sink kiürül.

### A49. Ülés-bővítés: /lay és ülés-pózok
**Munka:** 🟡 • **Érték:** ⭐

**Mi ez:** GSit-paritás kiegészítés: fekvés (/lay) és pár ülés-변 póz.
**Hogyan működne:** A fekvés kliens-oldalon póz-trükköt igényel (alvás-póz packet vagy LibsDisguises-híd) — a meglévő DruidDisguise reflexiós minta kiterjeszthető; ha nincs LibsDisguises, a /lay nem elérhető (soft-degrade, mint a druida-formáknál).
**Miért jó:** Szerepjáték/screenshot-érték; a kocsma (D5) hangulatához illik.
**Építőkövek:** SitManager, LibsDisguises-híd.
**Buktatók:** Póz-packetek anticheat (GrimAC) false-positive kockázata — teszt kell.

### A50. Esemény-tudatos MOTD
**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** A szerverlista-MOTD tükrözi az élő állapotot: vérhold alatt vörös MOTD, világboss alatt boss-hirdetés, szezonzáró héten visszaszámláló.
**Hogyan működne:** A MotdListener a variáns-választás előtt megnézi az esemény-managerek getterjeit (BloodMoon/WorldBoss/Season — mind volatile-olvasás, async-safe getterek kellenek, ellenőrzendő); configban `event-variants.<esemény>` blokkok, amik aktív eseménynél felülírják a normál rotációt.
**Miért jó:** A szerverlista élő kirakat — „valami történik BENT" érzés, ami belépésre csábít.
**Építőkövek:** MotdListener, esemény-getterek (A14-hez már kiépítve).
**Buktatók:** Az esemény-getterek async-hívhatóságát egyenként ellenőrizni kell (csak volatile mező-olvasás lehet).

### A51. Kombó/finisher sebzés-szám kiemelés
**Munka:** 🟢 • **Érték:** ⭐

**Mi ez:** A lebegő sebzés-szám (A8) kombónál/finishernél nagyobb és színesebb („25.4!" arany, skálázott TextDisplay).
**Hogyan működne:** Az AbilityCatalystListener a kombó/finisher tényét egy rövid TTL-es konkurens map-be írja; a DamageIndicatorListener a spawn előtt megnézi, és a Transformation scale-t 1.5×-re, a színt aranyra állítja.
**Miért jó:** A kombó-rendszer (A16) vizuális jutalma — a „nagy szám" dopamin ingyen.
**Építőkövek:** DamageIndicatorListener (Transformation már használatban), kombó-detektálás.
**Buktatók:** A spell-sebzés nem mindig EntityDamageByEntity-ként csapódik le (potion/tick-sebzés) — csak a közvetlen sebzésre működik.

### A52. Moderáció-eszkaláció és chat-napló
**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** Ismételt némítás automatikusan hosszabb (5→30→180 perc), és a kiszűrt/némított üzenetek külön naplóba kerülnek.
**Hogyan működne:** A ModerationManager mute-történetet tart (UUID→count, YamlStore); a /mute perc-megadás nélkül az eszkalációs lépcsőt használja. A blokkolt üzenetek `logs/chat-moderation.log`-ba (async append), az admin audit-log (C7) mintájára.
**Miért jó:** Következetes büntetés-lépcső vita nélkül; a napló bizonyíték vitás esetekben.
**Építőkövek:** ModerationManager (most készül), YamlStore.
**Buktatók:** A napló forgatása (méret-limit), különben nő a végtelenbe.

---

## Újabb QoL-vadászat a friss rendszereken (A53–A62) — ✅ IMPLEMENTÁLVA

### A53. /afk parancs (önkéntes AFK-jelölés)
**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** A játékos maga jelezheti, hogy elment: `/afk` → azonnali ⌚-jelölés + hátrasorolás.
**Hogyan működne:** Vékony parancs az AfkManager-re: egy `manualAfk` set; az isAfk ezt VAGY az időzítőt nézi; bármilyen aktivitás törli. A jutalomkapu (A46) azonnal élesedik.
**Miért jó:** Udvariasság a csapat felé („ne várjatok rám") — és a játékos maga védi magát az AFK-halál ellen.
**Építőkövek:** AfkManager, TablistManager AFK-útvonal (kész).
**Buktatók:** —

### A54. {event} token a tablist-fejlécben
**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** A header/footer sorokban használható `{event}` token: az épp aktív világesemények egy sorban.
**Hogyan működne:** A TablistManager applyTokens-e a HudManager eventLabel()-jének plain-text változatát helyettesíti be (snapshot-ból, nem élő hívásból). A default header kapna egy „⚔ {event}" sort.
**Miért jó:** A tablist megnyitása = azonnali helyzetkép; az A14 „mi történik most" a leggyakrabban nézett felületre kerül.
**Építőkövek:** TablistManager token-rendszer, HudSnapshot.event().
**Buktatók:** A diff-cache miatt esemény-váltáskor amúgy is frissül — extra csomag-teher nincs.

### A55. Invsee frissítő-gomb
**Munka:** 🟢 • **Érték:** ⭐

**Mi ez:** A read-only invsee GUI-ba egy 🔄 gomb: új pillanatképet kér a célpontról.
**Hogyan működne:** A gomb újra lefuttatja az InvseeCommand snapshot-útvonalát (célpont-szál hop → új GUI); a cím mutatja a pillanatkép korát („(12 mp-es kép)").
**Miért jó:** A „pillanatkép elavul" gyengeség fele eltűnik, élő-nézet komplexitás nélkül.
**Építőkövek:** InvseeGUI/Command (kész), holder-akció minta.
**Buktatók:** Rate-limit (max 1 frissítés/2 mp), különben hop-spam.

### A56. Crate-esélyek a kulcs-item lore-jában
**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** A kulcs lore-ja mutatja a top-3 jutalmat esély-százalékkal és a láda nevét.
**Hogyan működne:** A CrateKeyFactory a createKey-nél a config reward-súlyokból %-ot számol és lore-sorokat ír; a /crate info logikája újrahasznosítható.
**Miért jó:** A kulcs önmagát adja el — a piacra/tradebe kerülő kulcsnak is látszik az értéke.
**Építőkövek:** CrateKeyFactory, CrateManager esély-számítás.
**Buktatók:** Config-változás után a régi kulcsok lore-ja elavul (kozmetikai — a sorsolás mindig a friss configból megy; egy lore-frissítés nyitáskor megoldja).

### A57. Mute-lejárat értesítés
**Munka:** 🟢 • **Érték:** ⭐

**Mi ez:** A némítás lejártakor a játékos üzenetet kap („A némításod lejárt — újra beszélhetsz").
**Hogyan működne:** Nincs időzítő: a ModerationManager isMuted lusta-lejárat ága már észleli a lejáratot — ott egy egyszeri flag + a következő chat-próbálkozásnál (vagy azonnal, ha online: player-szál hop) az üzenet.
**Miért jó:** A játékos ma csak próbálgatással tudja meg, hogy vége — apró, de sokat számító tisztelet.
**Építőkövek:** ModerationManager lejárat-ág.
**Buktatók:** —

### A58. Report-visszajelzés a bejelentőnek
**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** A `/reports resolve` a BEJELENTŐT is értesíti (online: azonnal; offline: következő belépéskor).
**Hogyan működne:** A resolve eltárolja a „kézbesítetlen visszajelzés" listát (reporterUuid→üzenet, YamlStore); online reporternél azonnali hop-üzenet, offline-nál a join-listener üríti.
**Miért jó:** A bejelentés ma fekete lyuk — a visszajelzés a /report-ba vetett bizalom kulcsa (különben leszoknak róla).
**Építőkövek:** ReportManager, join-értesítés minta (OnboardingListener).
**Buktatók:** —

### A59. Szerverlista-ikon rotáció
**Munka:** 🟢 • **Érték:** ⭐

**Mi ez:** A MOTD mellé az ikon is váltakozik/eseményhez igazodik (vérholdkor vörös logó).
**Hogyan működne:** A MotdListener induláskor betölti a data-mappa `icons/` PNG-it (Bukkit.loadServerIcon — 64×64), a ping-eventen a variánshoz rendelt ikont állítja (event.setServerIcon). Az esemény-variáns configja `icon:` mezőt kap.
**Miért jó:** Az esemény-MOTD (A50) vizuális párja — a kirakat teljes.
**Építőkövek:** MotdListener variáns-rendszer (kész).
**Buktatók:** Az ikon-betöltés IO — induláskor egyszer, cache-elve; a ping-en csak referencia-állítás.

### A60. Sidebar-pontszámok elrejtése (blank numberFormat)
**Munka:** 🟢 • **Érték:** ⭐⭐⭐

**Mi ez:** Az oldalsáv jobb szélén látszó piros sor-számok (14, 13, 12…) eltüntetése.
**Hogyan működne:** 1.20.3+ API: a Score `numberFormat(NumberFormat.blank())` hívása a HudManager sor-score beállításánál (és az objective-en defaultként). Egy-két sor kód.
**Miért jó:** A legrégebbi scoreboard-csúfság tűnik el — a TAB-os szerverek ezért packet-trükköztek éveken át; nekünk egy API-hívás. A teljes oldalsáv-dizájn ettől lesz „kész".
**Építőkövek:** HudManager update()/buildSidebar.
**Buktatók:** API-név ellenőrzés (io.papermc.paper.scoreboard.numbers.NumberFormat) — fordítás dönti el.

### A61. Ping-színezés a tablist-tokenben
**Munka:** 🟢 • **Érték:** ⭐

**Mi ez:** A `{ping}` token szín-kódolva: zöld <80, sárga <150, piros felette.
**Hogyan működne:** Az applyTokens a {ping}-et „&a72" formában helyettesíti a küszöbök szerint (küszöbök configból: tablist.ping-colors).
**Miért jó:** Egy pillantásból látszik a kapcsolat minősége — a ping-oszlop mellé a headerben is.
**Építőkövek:** TablistManager.applyTokens.
**Buktatók:** —

### A62. Kombó-ablak visszaszámláló csík
**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** A „⏳ Kombó-ablak: X" action bar üzenet kap egy fogyó csíkot (▰▰▰▱▱), ami az ablak hátralévő idejét mutatja.
**Hogyan működne:** A cast utáni hint nem egyszeri üzenet, hanem a játékos entity-schedulerén 500 ms-onként frissülő rövid task-lánc (max 8 lépés = 4 mp), ami az eltelt idő szerint rajzolja a csíkot; új cast vagy lejárat megszakítja.
**Miért jó:** A kombó-tanulás fele az időérzék — a csík zsigeri visszajelzést ad, mikor „fér még bele" a következő spell.
**Építőkövek:** AbilityCatalystListener kombó-hint (kész), entity-scheduler lánc (CrateSpinGUI minta).
**Buktatók:** Az action bart más rendszerek is használják (cast-hibák) — a task ne írja felül a frissebb üzenetet (timestamp-ellenőrzés).

---

## QoL-vadászat, 3. kör (A63–A70)

### A63. /ping parancs
**Munka:** 🟢 • **Érték:** ⭐

**Mi ez:** `/ping [név]` — saját (vagy más) ping színkódolva, a tablist-küszöbökkel.
**Hogyan működne:** Vékony parancs a TabInfo-snapshotból (a TablistManager már gyűjti a pinget) — más játékos entitását nem is kell érinteni. Ugyanazok a zöld/sárga/piros küszöbök (A61).
**Miért jó:** A „lagolok?" kérdés önkiszolgáló válasza; a snapshot-infra ingyen adja.
**Építőkövek:** TablistManager snapshotok, ping-colors config.
**Buktatók:** —

### A64. Animált oldalsáv-cím
**Munka:** 🟢 • **Érték:** ⭐

**Mi ez:** A sidebar címe ({@code hud.sidebar.title}) is lehet animáció: `{anim:<név>}` támogatás.
**Hogyan működne:** A buildSidebar/update a címet a TextAnimator-on át rendereli, ha token van benne; diff-elt (csak frame-váltáskor íródik újra). Egy finom szín-hullámzó „Ice SMP" felirat a glyph mellett.
**Miért jó:** A TAB-os szerverek jellegzetes „élő cím" hatása — nálunk pár sor.
**Építőkövek:** TextAnimator, HudManager cím-kezelés.
**Buktatók:** Gyors frame-ek cím-újraregisztrálást igényelnek (objective displayName-írás — olcsó, de 500 ms-nál ne gyorsabban).

### A65. AFK-zóna vizuális perem
**Munka:** 🟢 • **Érték:** ⭐

**Mi ez:** Az AFK-zónába belépéskor (és onnan 10 mp-enként) halvány részecske-perem mutatja a zóna határát.
**Hogyan működne:** A claim `show` határ-kirajzoló mintája az AfkManager tick zóna-ágában: a zóna-doboz élei mentén ritkított partikelek, csak a bent állóknak (régió-lokális spawnParticle a játékos szálán).
**Miért jó:** A játékos látja, meddig ér a jutalom-zóna — nem lép ki véletlenül fél blokkal.
**Építőkövek:** AfkManager zóna-doboz, claim-határ partikel-minta.
**Buktatók:** Partikel-mennyiség fékezése (nagy zónánál csak a játékoshoz közeli élek).

### A66. Ritka crate-nyeremény broadcast
**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** Ha valaki alacsony esélyű (pl. <10% súlyú) jutalmat húz, szerver-broadcast megy („✨ X kihúzta: Netherite-törmelék a Ritka Ládából!").
**Hogyan működne:** A CrateManager sorsolása ismeri a húzott jutalom súly-arányát; küszöb alatt (crates-settings.broadcast-below-percent, default 10) broadcast a meglévő üzenet-infrán. Kapcsolható.
**Miért jó:** A crate-hype fele a mások szerencséjének látványa — ingyen marketing a kulcs-sinknek.
**Építőkövek:** CrateManager sorsolás, broadcast-minta.
**Buktatók:** Spam-fék: játékosonként max 1 broadcast/perc.

### A67. Kattintható stat-kártya a chatben
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** A /stats kimenet kap egy „megosztás" sort: kattintva a chatbe posztolható egy tömör, hover-rel bővülő stat-kártya.
**Hogyan működne:** Adventure hover/click eventek: a kártya egy sor („⚔ X statjai — vidd fölé!"), hoverText a teljes statokkal; a megosztás egy rejtett parancson át megy (rate-limit 1/perc). A chat-formázó változatlan.
**Miért jó:** A statisztika közösségi funkciót kap — flex és rivalizálás, ami a ranglistát is hajtja.
**Építőkövek:** StatsCommand, Adventure hover/click API.
**Buktatók:** A megosztó-parancs validálja, hogy a játékos a SAJÁT kártyáját posztolja.

### A68. Halál-összegző hover-részletek
**Munka:** 🟢 • **Érték:** ⭐

**Mi ez:** A death recap sorai hover-re többet mondanak: a gyilkos játékos kaszt/szint/K-D adata, mob-forrásnál a mob-szint.
**Hogyan működne:** A DeathRecapListener a rögzítéskor (a sértett szálán, ahol az adat elérhető) a bejegyzésbe teszi a plusz mezőket; a kiíráskor Component hoverText. Semmilyen új esemény-út.
**Miért jó:** „Ki volt ez és mekkora?" — a bosszú-információ egy hoverre van, a chat mégsem lesz hosszabb.
**Építőkövek:** DeathRecapListener ring-buffer, Adventure hover.
**Buktatók:** A gyilkos adatainak olvasása a SÉRTETT szálán történik — csak a snapshotolható mezők (név/szint a kill-pillanatban).

### A69. Egységes menü-hangnyelv `[KÉSZ]`
**Munka:** 🟢 • **Érték:** ⭐⭐

**Mi ez:** Közös hang-készlet minden GUI-ra: nyitás, lapozás, siker, hiba, vásárlás — ma GUI-nként ad-hoc (vagy néma).
**Hogyan működne:** `GuiUtil.sound(player, GuiSound.OPEN|PAGE|SUCCESS|ERROR|BUY)` — egyetlen enum + a GUI-listenerek hívásainak egységesítése (mechanikus csere). A hangok configból felülírhatók (gui.sounds.*).
**Miért jó:** A felület „egy kézből valónak" hangzik — apró, de az igényesség-érzet nagy része pont ez.
**Építőkövek:** GuiUtil, meglévő GUI-listenerek.
**Buktatók:** —

### A70. Quest-teljesítés toast `[KÉSZ]`
**Munka:** 🟡 • **Érték:** ⭐⭐

**Mi ez:** Quest teljesítésekor a jobb felső sarokban vanília advancement-toast is felugrik a chat-üzenet mellett.
**Hogyan működne:** Paper toast-küldés (ideiglenes advancement adás/elvétel vagy a Paper API toast-supportja — ellenőrzendő, melyik érhető el 1.21.11-en tisztán); a quest display-neve + ikonja. A QuestManager complete()-jébe egy hívás.
**Miért jó:** A teljesítés-pillanat ünnepibb — a vanília nyelvén szól, minden kliensen működik.
**Építőkövek:** QuestManager.complete, Paper toast-út.
**Buktatók:** Ha csak advancement-trükkel megy, a receptek/advancement-lista ne szennyeződjön (rejtett, azonnal visszavont advancement).

### A71. Display-entity effekt-réteg (DisplayFx) `[KÉSZ — util + mindhárom pilot]`
**Munka:** 🟡 • **Érték:** ⭐⭐⭐

**Mi ez:** Egy vékony, közös effekt-réteg a display entityk (BlockDisplay/ItemDisplay/TextDisplay) fölé, ami a particle-rendszer mellé (nem helyette!) belép mindenhova, ahol az effekt **geometria, tartós, vagy animált** — mert ott a particle csak drága, szaggatott pontfelhő tud lenni. Munkamegosztás: particle = átmeneti visszajelzés (ütés, cast, ambient), DisplayFx = vonal/sík/tárgy, ami áll, forog vagy simán mozog.

**Hogyan működne:**
- **`utils/DisplayFxUtil`** — a teljes életciklust egy helyen kezeli:
  - `spawn(...)`: régió-szálon spawnol (`getRegionScheduler().run` a cél-lokációra), minden entityre `setPersistent(false)` + `icesmp_fx` scoreboard-tag + PDC-tag a birtokos rendszerrel (`fx-owner: claim|crate|boss|ritual`).
  - `autoDespawn(entity, ticks)`: az entity saját schedulerén (`entity.getScheduler().runDelayed`) — Folia-tisztán, a régióval együtt él/hal.
  - `animate(display, transformation, durationTicks)`: beállítja az interpolációs időt + cél-transzformációt — a kliens simán animál, a szerver **egyetlen** csomagot küld tickenkénti újraszórás helyett.
  - `showOnlyTo(entity, player)`: spawn után minden más online játékosra `hideEntity` — per-nézős effekt (a claim show csak a kérőnek látszik).
  - **Árva-takarítás:** chunk-load listener + induláskor world-scan az `icesmp_fx` tagre → azonnali remove. Crash/restart után sem marad szemét a világban.
- **Pilot 1 — claim/territórium-határ „fényfal":** a mostani pontozott particle-perem helyett (mellett) élenként egy-egy megnyújtott, félig átlátszó üveg-BlockDisplay (skála-transzformációval nyújtva, glow-színnel a claim-státusz szerint: saját=zöld, idegen=piros). 15 mp után auto-despawn, csak a kérő látja. Configból: `display-fx.claim-wall.enabled|material|seconds`.
- **Pilot 2 — crate-nyitás 3D:** a CrateSpin GUI mellé/helyett a láda fölött ItemDisplay pörgeti a lehetséges nyereményeket (interpolált forgatás + skála-pulzus), a nyertes itemnél megáll és felnagyul. A sorsolás-logika változatlan, csak a látvány rétege cserél.
- **Pilot 3 — boss/AoE telegraph:** világesemény-bossok csapása előtt a veszélyzóna padlóján lapos, piros, pulzáló BlockDisplay-sík (skála 0→teljes az előjelzés ideje alatt) — a particle-gyűrűnél sokkal olvashatóbb „innen lépj ki" jelzés.
- Későbbi fogyasztók ugyanerre a rétegre: rituálé-oltár aura, relikvia-lebegtetés, piactéri kirakat (ItemDisplay a bódé fölött), AFK-zóna perem (A65 ezzel szebb).

**Miért jó:** A szerver ma tickenként particle-csomagokat szór, hogy egy határvonalat „tartson életben" — a display entity ugyanezt egy spawn + egy animációs csomagból oldja meg, szebben. A látvány folytonos (vonal, nem pontsor), a mozgás sima (kliens-oldali interpoláció), és per-nézős. Egyetlen util-rétegen múlik, hogy minden későbbi effekt-ötlet (aura, telegraph, kirakat) 5 sorból megvalósítható legyen.

**Építőkövek:** Paper Display API (1.19.4+, interpoláció + transzformációk), meglévő per-nézős infrastruktúra (HudManager board-minta a glow-hoz), ClaimManager/TerritoryCommand határ-kirajzolók, CrateSpinGUI, világesemény-managerek.

**Buktatók:**
- **Folia:** a display entity valódi entitás — spawn/módosítás CSAK a cél-régió szálán; mozgó effektnél `teleportAsync`. A util minden belépési pontja régió-schedulerrel véd.
- **Szemét-kockázat:** a `setPersistent(false)` + tag + kettős takarítás (chunk-load + induló scan) kötelező hármas — enélkül restart után display-szemét marad.
- **Entitás-költség:** egy határ-fal élenként 1 entity (4–12 db), nem blokkonkénti — a darabszámot a util kapja meg felső korlátként (`display-fx.max-per-player`, default ~24).
- **Régi kliensek/ViaVersion:** 1.19.4 alatti kliensnek a display entity nem létezik — ha a szerver enged régebbi klienst, particle-fallback kell (a util `supportsDisplay(player)` ága).

**Állapot:** a `DisplayFxUtil` (spawn+tag+`setPersistent(false)`+auto-despawn+`showOnlyTo`+`animateTo`+`wallSegment`+`groundTelegraph`) és a `DisplayFxCleanupListener` (chunk-load söprés) kész; **mindhárom pilot él:** Pilot 1 — claim-fényfal a `/claim show`-ban (`display-fx.claim-wall.*`); Pilot 2 — 3D crate-feltárás a láda fölött (`spawnItemDisplay` pörgetés → nyertesen megáll+felizzik, `display-fx.crate-reveal.*`); Pilot 3 — world-boss AoE növekvő padló-telegraph (`display-fx.boss-telegraph.*`, a SLAM/ZONE 30-tick figyelmeztetése alatt kitölti a zónát).

**Al-eset — aurora mint DisplayFx `[KÉSZ]`:** az `AmbientEventManager` aurora-ága a particle-pulzusok mellé most **két magasan lebegő, áttetsző, lassan oldalra sodródó fény-lapot** (BlockDisplay, per-nézős, teal+lila glow) spawnol — folytonos égi fény-fátyol, nem csak pontfelhő. `display-fx.aurora.*` configgal kapcsolható/anyagozható; auto-despawn az effekt végén. A többi ambient (szentjánosbogár, köd) per-nézős, olcsó particle marad.

---

### A72. Spell-VFX forma- és paletta-réteg (SpellVfx) `[KÉSZ]`
**Munka:** 🟡 • **Érték:** ⭐⭐⭐

**Mi ez:** A ~390 spell látvány-identitása. Ma a `ConfiguredSpell.playFeedback` **egyetlen gömb-alakú particle-puffot** szór a célpont mellkasához (`focus + 1.0y`, `particleCount`, egy hang) — így a Jéglánc, a Lángrengés és a Lávakitörés fizikailag ugyanaz az effekt, csak más particle-színnel. Ez a tétel egy kis **forma-primitív könyvtárat** ad, hogy a spell ne `particle + count`-ot deklaráljon, hanem **formát + palettát**, és magától önmagának nézzen ki. A particle marad az eszköz — csak formázva, színezve, időzítve, rétegezve használjuk (ma a particle kb. 20%-át hozzuk ki). Független A71-től: nincs entitás-életciklus, tiszta particle.

**Hogyan működne:**
- **`utils/SpellVfx`** — időalapú forma-emitterek (mint a TextAnimator/ambient pulzus: `System.currentTimeMillis()` frame-választás, **nincs per-tick scheduler**), mind régió-lokális, a kasztoló szálán futva:
  - `beam(from, to, palette)` — vonal-interpoláció a kasztolótól a célig (TARGET-spellek: a „lánc"/„sugár"/„nyaláb" tényleg a cél felé mutat).
  - `ring(center, radius, palette)` + `expandingRing(center, r0→r1, seconds)` — AoE-k: a hatókör *látszik*, nem egy formátlan pötty.
  - `helix(base, height, palette)` — SELF-buffok felfelé csavarodó kettős spirálja.
  - `cone(origin, direction, angle, length)` — lehelet/kúp-spellek.
  - `impactBurst(point, palette)` — becsapódás-csillag (a mostani puff kontrollált utódja).
  - `arc(from, to)` — lövedék-csóva parabolán.
- **Paletta = `DustOptions` / `DUST_COLOR_TRANSITION`:** minden spec kap egy 2–3 színű palettát (tűz narancs→vörös, fagy kék→fehér, romlás lila→fekete, szentség arany→fehér). A por mérete és szín-átmenete adja a „mágia-anyagérzetet", amit a nyers particle nem tud. Ez önmagában, forma nélkül is óriási identitás-nyereség.
- **Retrofit a `ConfiguredSpell`-be:** a builder kap egy `vfx(Shape, Palette)` leírót; ha nincs megadva, a **`Targeting`-ből default** jön (`TARGET→beam+impact`, `AOE→expandingRing`, `SELF→helix`) — így egyetlen mező hozzáadásával mind a 390 spell azonnal kap formát, a kézzel hangolt kivételeket pedig felül lehet írni a `SpellCatalog` builderében.
- **Hang-réteg (filléres bónusz):** a mostani egy hang helyett a paletta hordozhat egy 2–3 hangból álló „akkordot" különböző pitch-en — a spell hallhatóan is erősebbnek szól. Ugyanabba a `vfx` leíróba fér.

**Miért jó:** Ez a legjobb érték/munka arányú lépés az egész látvány-fronton. Nem új tech (nincs entitás, nincs Folia-életciklus-kockázat), mégis a 390 spell egy csapásra **önmagának néz ki** — a forma a mechanikát tükrözi (lánc chainel, kitörés felfelé tör, AoE gyűrűként terül), a szín a spec-identitást. A default-a-Targetingből elv miatt a retrofit nem 390 kézi hangolás, hanem egy mező + a kiemelt spellek finomítása.

**Állapot:** kész — `SpellVfx` util (BEAM/ARC/IMPACT/RING/HELIX/CONE + 10 paletta) a `ConfiguredSpell.playFeedback`-be kötve; a forma a `Targeting`-ből jön, `spell-vfx.*` configgal kapcsolható. **Paletta-illesztés spec/kaszt szinten:** a `spell-vfx.class-palettes.<kaszt/spec>` hozzárendelés a `classes.yml` `spell-unlocks` kulcsain át ráterjed a spec MINDEN spelljére (~44 bejegyzés → ~390 spell), a spec felülírja a kasztot, a `spell-vfx.overrides.<spell-id>` pedig az egyedieket; ami egyik listában sincs, az az accent-particle-ből kap színt. Explicit `vfx(...)` builderrel is felülírható.

**Hero-ultimate paletták:** a 31 spec-ultimate (lvl 45) kézzel válogatott palettát kap a `spell-vfx.overrides`-ban, hogy a csúcs-spell ránézésre kitűnjön — a legtöbb megerősíti a spec-témát, néhány szándékosan eltér (pl. `elemental` spec STORM, de `ascendance_flame` FIRE; `windwalker` STORM, de `serenity` HOLY).

**Forma-illesztés:** a forma alapból a `Targeting`-ből jön (SELF→HELIX, TARGET→BEAM, AOE→RING), és egy óvatos, egyértelmű kulcsszó-heurisztika finomítja a spell-id alapján: közelharci csapás (`*_strike/_kick/_smash/_slam/_cleave/_rend…`) → IMPACT (becsapódás-csillag, nem sugár); dobott lövedék (`*_dart/_throw/_toss…`) → ARC (parabola); lehelet (`*_breath/_roar/_spray…`) → CONE (a néző irányába). Minden más marad a targeting-alapon. Per-spell felülírás: `spell-vfx.shapes.<spell-id>: BEAM|ARC|IMPACT|RING|HELIX|CONE`.

**Építőkövek:** `ConfiguredSpell.playFeedback` (a becsatlakozási pont), `Targeting` enum (a default-forma forrása), `ParticleUtil.spawn` (a primitívek erre épülnek), TextAnimator idő-frame minta, `SpellCatalog` builder-hívások.

**Buktatók:**
- **Particle-mennyiség:** a formák pontszáma felső korláttal (a `beam`/`ring` hossz-arányos, de config-cap: `spell-vfx.max-points-per-shape`), különben nagy hatókörnél particle-vihar. A FLASH-diéta és confetti-szabályok (ARCHITECTURE §6) rájuk is vonatkoznak.
- **Folia:** minden emitter a kasztoló régió-szálán fut, a `focus` helyi — cross-region nincs. AOE-nél a `center` a spell saját régiójában van, rendben.
- **Retrofit-fegyelem:** a `Targeting`-default ne írja felül a már kézzel hangolt, állapotos spellek (külön osztályok) saját effektjeit — a `vfx` csak a deklaratív `ConfiguredSpell`-útra hat.
- **Paletta-karbantartás:** 31 spec palettája a `SpellCatalog`-ban vagy a `classes.yml`-ben éljen egy helyen, ne szóródjon szét.

---

**Összesen: 72 ötlet (A1–A72).**
