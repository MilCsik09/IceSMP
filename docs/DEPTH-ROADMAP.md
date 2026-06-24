# IceSMP — Mélység-roadmap (depth over breadth)

> **Vezérelv:** a tartalom *mennyisége* már bőséges (13 kaszt, ~225 spell, 8 boss, 6 horda, küldetések,
> achievementek). A hosszú távú szórakozást innen nem több *ugyanolyan* tartalom adja, hanem a **mélység**:
> valódi build-döntések, mechanikás encounterek, üldözendő loot, és max-szint utáni cél. Ez a dokumentum
> a tervet rögzíti — a tényleges kódra alapozva, priorizálva. **Megvalósítás build-checkpoint után ajánlott.**
>
> Kapcsolódó: `docs/CONTENT-PLAN.md` (megvalósított tartalom), `docs/ARCHITECTURE.md` (minták, Folia-szabályok).

---

## Hol tart most a rendszer (kiindulás)
- **Spellek:** `ConfiguredSpell` builder (damage/heal/effect/knockback/dash/aoe/ignite/freeze…) + néhány
  bespoke (`ProjectileBurstSpell`, `BlinkSpell`, `DruidFormSpell`, `ShamanTotemSpell`). Költség: HUNGER/XP/HEALTH.
  Van combo-rendszer (`spells.combos`) és spell-mastery (cooldown-csökkentés).
- **Talentek:** `TalentManager` — `requires-job`/`requires-spec`/`requires-talent`/`excludes`/`requires-spent`
  feltételek; hatások: `max-health`/`movement-speed`/`attack-damage`/`class-xp-bonus`/`profession-xp-bonus`
  + `grants-spell` (a capstone már ad spellt!). **Tehát a választás-/spell-adó infrastruktúra részben MEGVAN.**
- **Bossok:** `WorldBossManager` per-boss fázis-tick a boss régió-ütemezőjén (50% HP enrage + szignatúra-aura).
  **A per-entitás tick-keret megvan — csak a mechanikák hiányoznak róla.**
- **Resource:** nincs per-kaszt erőforrás (mind HUNGER/XP/HEALTH). Kivétel: `SoulShardManager` (nekromanta lélekszilánk).

---

## 1. Spell-mélység — mechanika a sebzésen túl

**Probléma:** a ~225 spell nagy része „sebezz X-et / rakj fel Y effektet" variáció; ~6 alap-mechanika.

**Javasolt rétegek (növekvő mélység):**
1. **Stack + detonáció.** Egyes spellek *stacket* raknak a célra (PDC/effekt-jelölő), egy „finisher" spell
   elfogyasztja őket burst-re. (Pl. Affliction: dot-stackek → `darkglare` detonál.) → valódi rotáció.
2. **Zóna-spellek (persistent ground AoE).** A `ShamanTotemSpell`/`TotemManager` mintát általánosítani:
   lerakott, pulzáló terület-effekt (tűzgyűrű, gyógymező, méregfelhő). Folia: a meglévő totem-tick minta.
3. **Channeled / charged cast.** „Tarts nyomva a feltöltéshez" (Evoker `empower` fantázia): hosszabb
   tartás → erősebb hatás. Kell egy charge-állapot a katalizátor-listenerben.
4. **Proc-láncok / cooldown-reset.** Egy spell esélyt ad egy másik azonnali resetjére → emergens combo.
5. **Spell-módosítók / rúnák (glyphs).** A játékos egy spellt *átalakít* (pl. „a Tűzgolyód láncol",
   „a Gyógyításod sebző is"). Adat-vezérelt: egy módosító-réteg a `ConfiguredSpell` fölött. **Ez köti
   össze a talent-reworkkel** (lásd #2) — a talent-csomópont egy spell-módosítót ad.

**Megvalósítás:** új builder-primitívek + egy `SpellModifier` réteg; a `executeSpell()` már támogatja a
no-op→nincs-cooldown logikát, erre építhető a charge/stack. **Kockázat:** közepes–magas (új mechanikák).
**Folia:** zóna/persistent rész a totem-mintán (entitás régió-tick), biztonságos.

---

## 2. Talent-rendszer újratervezés — class-specifikus, valódi build

**Probléma:** a talentek lapos statok (max-health/attack-damage/movement-speed), a tier-1 közös mindenkinek,
a spec-csomópont 1/spec. Nincs valódi döntés.

**Cél: kasztonként saját talent-fa, valódi trade-offokkal.**
1. **Kaszt-specifikus fák.** A közös tier-1 helyett minden kaszt saját gyökér-talentjei (kaszt-identitás).
   A `requires-job` már létezik — ki kell terjeszteni az egész fára.
2. **Spell-módosító csomópontok.** Ne csak `+stat` — a node *megváltoztat egy spellt* (a #1 SpellModifier-rel):
   „a Holdtűzöd most lánc", „a Pajzsod sebzést tükröz". A `grants-spell` hook már megvan — bővíteni
   `modifies-spell`-re.
3. **Választás-ágak (`excludes`).** Már létezik (juggernaut/warlord) — minden tier-en kötelező döntés:
   2-3 egymást kizáró ág → ugyanaz a spec többféleképp játszható (pl. Frost DK: burst vs sustain ág).
4. **Capstone-ok playstyle-formálók.** Nem `+6 HP` — hanem egy mechanika-váltó (pl. „a formáid közben is
   kasztolhatsz", „a totemjeid duplázódnak").
5. **Pont-büdzsé.** Úgy hangolva, hogy NE lehessen mindent felvenni — a döntés legyen valódi.

**Megvalósítás:** a `TalentManager` effekt-rendszerét kiterjeszteni attribútumokon túl „spell-módosító" és
„proc" típusokra; a config-fát kasztonként újraírni. **Ez a legnagyobb mélység-kar, de a legnagyobb munka is** —
és a #1 SpellModifier-réteget igényli előfeltételként. **Kockázat:** magas (talent + spell rendszer együtt).

---

## 3. Boss/invázió-mechanikák — ne statszörnyek legyenek

**Probléma:** 8 boss / 6 horda, de mindegyik = felskálázott mob + aura + 50% enrage. Nincs counterplay.

**Cél: telegrafált, válaszra kényszerítő mechanikák a meglévő per-boss fázis-ticken.**
1. **Telegrafált AoE.** A boss megjelöl egy területet (partikli ~1.5 mp), majd becsap → a játékosnak EL kell
   lépnie. (A `startPhaseTick` már fut a boss régió-szálán — ide jön a telegraph→delay→damage.)
2. **Archetípus-specifikus képességek.** Mindegyiknek 1-2 egyedi: Fagyott Trón = jég-tüske-vonal; Lávakohó =
   lávatócsák; Csontkirály = élőhalott add-okat idéz; Vihar Hírnöke = láncvillám a legközelebbire;
   Méreg Anyakirálynő = pókhálót/lassító zónát rak.
3. **Add-fázisok.** HP-küszöbnél a boss kis hordát idéz (mini-invázió) — kezelni kell őket.
4. **Counterplay-elemek.** „állj a zónába" (soak), „terülj szét" (chain), „öld az add-okat", „szakítsd meg
   a kasztot" (ehhez interrupt kell — lásd #6).
5. **Invázió-eszkaláció.** Időzített hullámok (1→2→3 növekvő), az utolsó egy mechanikás mini-boss.

**Megvalósítás:** a `startPhaseTick`-et bővíteni archetípus-callbackkel (telegraph-ütemezés a boss
ütemezőjén, `runDelayed` a becsapásra). **A keret megvan, Folia-helyes.** **Kockázat:** közepes.

---

## 4. Loot + ritkaság-rendszer *(saját javaslat — legjobb érték/ráfordítás)*

**Probléma:** nincs üldözendő loot. A relikvia lehetne a „chase item", de ~7 van, kozmetikus, nincs ritkaság.

**Cél: ritkasági-tier + drop-tábla + set-bónusz = heteken át tartó cél.**
- **Ritkasági szintek** (Common→Mythic, szín-kódolt) a relikviákon és egyedi tárgyakon.
- **Drop-táblák** a bossokról/inváziókról (a 8 boss / 6 horda kiváló forrás): ritka relikvia-szilánk →
  rituáléval összerakható (a `RitualManager` már megvan!).
- **Set-bónuszok** (frakció-szettek: elytra + fegyver + amulett azonos frakcióból → bónusz).
- **Affix-rendszer** (opcionális): a loot véletlen stat-módosítókat kap → két azonos tárgy se egyforma.

**Megvalósítás:** új `RelicAbility` implementációk + drop-listener + ritkaság-meta. **Új kód** (a relikvia-
képességek most hardkódoltak). **Kockázat:** közepes–magas. **De ez adja a leghosszabb endgame-hurkot.**

---

## 5. Prestige / horizontális endgame *(saját javaslat)*

**Probléma:** lvl 50 + spec + relik után nincs cél.
- **Paragon/prestige:** max szint után végtelen „paragon" pontok kis bónuszokra, vagy prestige-reset presztízs-jutalomért.
- **Kozmetikumok (ROADMAP 11.):** titulusok, partikli-nyomok, kalapok — **olcsó fejleszteni, óriási retenció.**
  Tisztán vizuális → PvP-balanszot nem érint, alacsony kockázat.
- **Szezon-hurok:** a `SeasonManager` (liga) már megvan — kösd soft-resethez + szezon-jutalmakhoz
  (titulus/kozmetikum) → ismételhető meta-loop.

---

## 6. Saját meglátásaim — a legnagyobb rejtett karok

1. **Per-kaszt erőforrás = a hiányzó kaszt-identitás.** Most MINDEN kaszt HUNGER/XP/HEALTH-ből kasztol.
   A legmélyebb hiányzó réteg: Szerzetes csi/combo-pont, Boszorkánymester lélekszilánk (a `SoulShardManager`
   *már létezik* — kiterjeszthető!), Halállovag runikus erő, Orgyilkos combo-pont. Saját erőforrás +
   generátor/költő spellek = valódi rotáció és kaszt-érzet. **Ez a #1 mélység-multiplikátor.**
2. **Interrupt / counterplay a PvP-hez.** A spellek most „fire-and-forget". Interrupt (kasztmegszakítás),
   CC-törés, diminishing returns (lánc-CC ellen) → a PvP (a fő endgame) sokkal mélyebb és kompetitívebb lesz.
3. **A balanszhoz telemetria kell.** 225 spell-t vakon hangolni reménytelen. Egy egyszerű spell-használati
   napló (melyik spellt sütik / melyik „dead") megmondja, mit kell javítani. **Olcsó, hatalmas megtérülés.**
4. **A talent- és spell-rework EGYÜTT tervezendő.** A class-specifikus talentek (#2) értelmét a spell-
   módosítók (#1) adják — külön-külön fél-megoldás. Ezt a kettőt egy designnal kezelném.
5. **„Kevesebb, de mélyebb" spell.** Hosszú távon jobb 8 *mechanikás* spell speconként, mint 12 statszörny.
   Build-teszt után érdemes a „dead" spelleket mechanikára cserélni, nem továbbiakat hozzáadni.

---

## Prioritás (érték / ráfordítás / kockázat / build-függőség)

| # | Tétel | Érték | Ráfordítás | Kockázat | Build kell |
|---|-------|:-----:|:----------:|:--------:|:----------:|
| 6.3 | Spell-telemetria (dead-spell napló) | Közepes | **Kicsi** | Alacsony | nem |
| 5 | Kozmetikumok / titulusok | **Magas** | Kicsi–közepes | Alacsony | ajánlott |
| 3 | Boss/invázió-mechanikák | **Magas** | Közepes | Közepes | igen |
| 4 | Loot + ritkaság-rendszer | **Magas** | Közepes–nagy | Közepes-magas | igen |
| 6.1 | Per-kaszt erőforrás | **Nagyon magas** | Nagy | Magas | igen |
| 1 | Spell-mechanikák (stack/zóna/módosító) | **Magas** | Nagy | Közepes-magas | igen |
| 2 | Talent-rework (class-spec, build) | **Nagyon magas** | **Nagy** | Magas | igen |
| 6.2 | Interrupt / PvP-counterplay | Magas | Közepes | Közepes | igen |

## Javasolt sorrend
1. **Build + szerver-teszt** (előfeltétel — innen derül ki, mi unalmas igazából, és innen lehet hangolni).
2. **Kozmetikumok + telemetria** (olcsó, biztonságos, azonnali retenció + adat a balanszhoz).
3. **Boss/invázió-mechanikák** (a keret megvan, közepes munka, nagy élmény-ugrás).
4. **SpellModifier-réteg + Talent-rework EGYÜTT** (a legnagyobb mélység-kar — ezt egy designnal).
5. **Loot/ritkaság + per-kaszt erőforrás** (az endgame-hurok és a kaszt-identitás).

> **Záró meglátás:** a projekt *szélességben* kész — a következő minőségi ugrás a **mélység**. A leg-
> nagyobb karok (talent-rework + spell-módosítók + per-kaszt erőforrás) együtt tervezendők, és **egy
> élő build-teszt után** érdemes nekik futni, hogy ne vakon, hanem a tapasztalat alapján mélyítsünk.
