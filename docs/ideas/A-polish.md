# A) Meglévő mechanika átdolgozása / polish

[← Ötlettár-index](../IDEAS.md)

Jelölés: **Munka** (🟢 kicsi / 🟡 közepes / 🔴 nagy) • **Érték** (⭐–⭐⭐⭐) • `[TOP]` = ajánlott
következő kör • `[KÉSZ]` = már implementálva.

> A1–A16 ✅ implementálva (PLAYTEST.md).

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

---

## Bővítés — A34-től (QoL / polish az A-kör folytatásaként)

### A34. Cast-hiba üzenetek egységesítése
🟢 • ⭐ (lásd IDEAS.md A30 — a bővítés itt csak a kereszthivatkozás miatt szerepel; részletes
kidolgozást lásd az eredeti tételnél, tartalma nem duplikált itt.)

**Mi ez:** Ma többféle üzenet jön, ha nem sül el a spell (cooldown / kevés erőforrás / nincs
célpont / rossz forma).
**Hogyan működne:** Egységes formátum ikonokkal (⏳ cooldown / ⚡ erőforrás / 🎯 célpont / ❌
állapot), mindig a hátralévő idővel vagy hiányzó mennyiséggel a szövegben; egy közös
`SpellFailureMessages` util minden cast-elutasítási ágból hívva (jelenleg szórtan, az egyes
spell-osztályokban és az `AbilityCatalystListener`-ben van).
**Miért jó:** A frusztráció fele abból jön, hogy nem tudni, MIÉRT nem ment el a cast — ez a
tétel az IDEAS.md-ben A30-ként szerepel, itt csak a teljesség kedvéért van jelezve.
**Építőkövek:** `MessageManager`, `AbilityCatalystListener`.
**Buktatók:** sok cast-ág egyszerre módosul — regresszió-veszély, alapos manuális playtest kell.

### A35. Katalizátor akció-bar sáv finomítás (dinamikus szín)
🟢 • ⭐⭐

**Mi ez:** Az erő-csík action bar / HUD megjelenítése jelenleg egyszínű sáv — a töltöttségi
szint szerinti szín-átmenet (piros→sárga→zöld) azonnali vizuális visszajelzést adna kritikus
alacsony erőforrásnál.
**Hogyan működne:** A `HudManager` sáv-építő függvénye a jelenlegi/max arány alapján válasszon
`§c`/`§e`/`§a` (vagy RGB, `1.16+` API) színkódot a sáv-karakterekhez; küszöbök configolhatók
(`hud.resource-bar.thresholds`). Régió-szálon, a meglévő `HudSnapshot` frissítési ciklusba
illesztve, nincs extra scheduler-hívás.
**Miért jó:** A játékos harc közben a periférián is észreveszi, ha kritikusan alacsony az
erőforrása — kevesebb véletlen „nem volt mana" cast-kudarc.
**Építőkövek:** `HudManager`, `HudSnapshot`.
**Buktatók:** színvak-barát módban (A32) más jelzésre van szükség — a két tétel összekötendő.

### A36. Spellbook keresőmező
🟢 • ⭐

**Mi ez:** 20+ feloldott spellnél a spellkönyvben szöveges keresés is segítene a szűrőn (A11)
és kedvenceken (A4) felül.
**Hogyan működne:** Anvil-input vagy chat-prompt minta (a quest builderből ismert mintázat) a
Spellbook fejlécén; a beírt részszöveg a spell-névre/kulcsszóra szűr, a GUI a `SpellbookGUI`
építő-logikáját újrafuttatva frissül.
**Miért jó:** Sok kaszt spelljei tematikusan hasonló nevűek — kereséssel gyorsabb megtalálni
egy adott képességet, mint lapozgatással.
**Építőkövek:** `SpellbookGUI`, `SpellbookListener`, anvil-input minta (quest builder).
**Buktatók:** —

### A37. Menü-breadcrumb és „vissza" konzisztencia
🟢 • ⭐

**Mi ez:** A `/menu` beágyazott almenüiben (Frakció → Bank → Valutaváltó szintig) nincs
egységes „hova jutottam" jelzés vagy egy kattintásos visszalépés minden szinten.
**Hogyan működne:** A GUI-cím sorába rövid útvonal-jelzés (`Menü › Bank & Pénz › Valutaváltó`),
és minden almenü első slotjában egységes „← Vissza" gomb (`MENU:<szülő>` akció-string, a
meglévő minta szerint) — tisztán `CommandMenus` réteg, gameplay-logikát nem érint.
**Miért jó:** Mobil/kontroller-barát navigáció, kevesebb „hogy jutok vissza" tévelygés mély
menükben.
**Építőkövek:** `CommandMenus`, `CommandMenuHolder`.
**Buktatók:** minden almenü-építőt egységesen kell módosítani — sok kis, ismétlődő szerkesztés.

### A38. Első belépés spawn-élmény polish
🟢 • ⭐⭐

**Mi ez:** Az onboarding-lánc (A5) mellé a tényleges spawn-pillanat vizuális/hangi csiszolása.
**Hogyan működne:** Join-kor rövid title/subtitle üdvözlés + halk hangjel (nem broadcast-
szintű), a semleges főváros látványos pontján spawnoltatva (`teleportAsync`); az üdvözlő
üzenet configolható (`messages.yml` → `join-welcome-title`).
**Miért jó:** Az MMO-szerverek első benyomása sokat számít — egy 2 másodperces title-élmény
olcsó, de emlékezetes belépő.
**Építőkövek:** `PlayerJoinListener`, `teleportAsync`, `MessageManager`.
**Buktatók:** ne ütközzön az onboarding-quest üzenetével (időzítést egyeztetni kell).

### A39. Inventory-rendezés gomb katalizátor mellett
🟢 • ⭐

**Mi ez:** Gyors inventory-rendezés (sort) egy kattintással, hogy a katalizátor/szakma-
alapanyagok ne keveredjenek a lootban.
**Hogyan működne:** Sneak + jobb-katt üres levegőre (vagy `/inv sort` parancs) rendezi a
hátizsákot kategória szerint (fegyver/páncél/alapanyag/egyedi); az egyedi szakma-alapanyagokat
(saját CustomModelData 6000–6013) és a katalizátort a rendezés kihagyja, hogy a hotbar-pozíció
stabil maradjon.
**Miért jó:** Gyűjtögető-intenzív szakma-rendszernél (50+ recept) a manuális rendezgetés
fárasztó — ez tiszta kényelmi funkció.
**Építőkövek:** `items/*ItemFactory` PDC-tagek (kategória-felismerés).
**Buktatók:** a hotbar-slot ne csússzon el rendezéskor (a katalizátor mindig ugyanott legyen) —
külön kizárás kell rá.

### A40. Spell-cast hangkönyvtár egységesítés
🟢 • ⭐

**Mi ez:** Jelenleg spellenként eltérő „érzetű" hangok szólnak castkor — némelyik erősebb,
némelyik alig hallható.
**Hogyan működne:** Egy központi hangerő/pitch-tábla kaszt-onként és sebzés-kategóriánként
(gyenge/közepes/erős cast), hogy a hangzásvilág konzisztens legyen; a meglévő
`ConfiguredSpell.builder(...)` egy opcionális `soundProfile` mezőt kap, ami a táblából olvas.
**Miért jó:** A hangzásvilág önmagában is visszajelzés a spell erejéről — konzisztens hangok
professzionálisabb érzetet adnak a szervernek.
**Építőkövek:** `ConfiguredSpell`, `SpellCatalog`.
**Buktatók:** sok spellt egyszerre érint — playteszttel ellenőrizni kell, hogy egyik kaszt se
lett „túl hangos/néma".

### A41. Teleport-becsapódás vizuális jelzés
🟢 • ⭐

**Mi ez:** A `teleportAsync`-alapú spell- és kaszt-teleportok (Shadowstep, hazatérés-kő,
frakció-spawn-váltás) érkezéskor nem adnak vizuális visszajelzést.
**Hogyan működne:** Érkezéskor rövid partikel-effekt (pl. portál-részecske gyűrű) + halk hang a
célponton, a `teleportAsync` completion-callback-jéből indítva (célpont régió-szálán fut, nincs
extra hop szükséges, mivel a callback már ott fut).
**Miért jó:** Kis, de érezhető polish minden teleport-jellegű mechanikára — konzisztensebb
„varázslatos" érzet.
**Építőkövek:** `teleportAsync` callback, `ShadowstepSpell` mint minta.
**Buktatók:** sok helyről hívva (relikvia, spell, admin-parancs) — egy közös util-metódusba
érdemes kiszervezni, hogy ne duplikálódjon.

### A42. Profil-GUI összefoglaló fül
🟡 • ⭐⭐

**Mi ez:** A `/profile` GUI jelenleg kaszt/talent/szakma almenükre bontott — hiányzik egy
„mindent egy lapon" áttekintő fül.
**Hogyan működne:** A `/profile` főoldalán egy „Áttekintés" csempe, ami a `/stats` (A15) fő
számait, az aktív specializációt, a talentpont-egyenleget és a frakció-státuszt egy GUI-lapon
összegzi (lore-szövegben) — tisztán olvasó nézet, kattintás nélkül delegál a megfelelő
almenübe (`MENU:`-minta).
**Miért jó:** Új és visszatérő játékosnak egy pillantásból látszik „hol tart" — csökkenti az
almenük közti ugrálást.
**Építőkövek:** `CommandMenus`, `StatsManager`, meglévő profil-GUI építő.
**Buktatók:** —

---

**Összesen: 42 ötlet (A1–A42).**
