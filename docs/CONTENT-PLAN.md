# IceSMP — Tartalmi bővítési terv (CONTENT-PLAN)

> **Cél:** a „mutatóban" lévő alrendszereket élő, változatos tartalommal tölteni — kreatívan, de a
> **meglévő adat-vezérelt mintákra építve** (`ConfiguredSpell` builder, `SpellCatalog`,
> `RelicRegistry`, config-vezérelt world-event-ek), hogy a bővítés könnyen karbantartható és Folia-biztos
> maradjon. Ez a dokumentum a *terv* — a megvalósítás kockázat szerint fázisolva (lásd a végén).
>
> **Lore-horgonyok:** 4 frakció/királyság — **Piros** (láng), **Kék** (jég/fagy), **Semleges** (szél/vándor),
> **Dark/Összeomlott** (csont/árny). Egy rejtélyes **törpe** civilizáció (Mételytépő). Ezt szövi át a tartalom.
>
> **Jelenlegi állapot:** 4 kaszt (Varázsló/Harcos/Íjász/Orgyilkos), 8 spec (2/kaszt); alap-spellek ~22-ig,
> a 40+ sáv vékony; 1 világboss (Ravager), 1 invázió-mob (Zombie), 3 napi-objective, 5 relikvia.

---

## 0. Korrekció + irányváltás (megvalósítás közben)

**Felfedezés:** a meglévő spell-tartalom **nem vékony** — a 8 spec mindegyike már most **10–12 spellt**
kap, 25→45 között jól elosztva, mindegyiknek van 42-es és 45-ös (300 cd) capstone-ja
(`elemental_overload`, `soul_harvest`, `berserk`, `last_stand`, `masterful_shot`, `king_of_beasts`…).
Az alap-kasztok ~22-ig adnak spellt, onnan a spec veszi át. **Tehát az 1. terület eredeti premisszája
(„adj capstone-okat") téves volt — az csak duplikáció lenne.**

**Új irány (a felhasználó kérésére): új kasztok/specek.** Az első megvalósított: a **Druida** (lásd lent).
A `JobType`/`SpecializationType` bővíthető — nincs kimerítő `switch`, minden config-stringből oldódik fel,
a `JobGUI` 6 slotot bír (4 volt használt), a `SpecGUI` szülő-kaszt szerint szűr. Egy 5. kaszt wiring-biztos.

### ✅ Druida kaszt (megvalósítva)
- **Kaszt:** `DRUID` — a természet kasztja, a meglévő természet-spelleket (`rain_dance`, `feast`,
  `wisplight`) újrahasználja + saját gyógyító/gyökér/tövis spellek (`regrowth`, `barkskin`,
  `entangling_roots`, `thornlash`).
- **Shapeshift — Forma-rendszer (`DruidFormSpell`):** **stance-rendszer** + **opcionális vizuális
  disguise**. 4 egymást kizáró forma külön statisztika-profillal: **Medve** (tank: resistance+absorption+
  strength), **Párduc** (fürge dps: speed+strength), **Hold** (caster: regen+fireres), **Utazó** (speed II).
  A formák hosszú, csendes buff-profilok (nincs scheduler → Folia-biztos), újra elsütve visszaváltozol;
  kilépéskor a registry-iterált cleanup törli őket.
- **Vizuális átalakulás (`integration/DruidDisguise`):** opcionális **LibsDisguises** híd — *tiszta
  reflexió*, nincs fordítási/kemény futásidejű függőség. Ha a szerveren fut a LibsDisguises (a
  `paper-plugin.yml` soft-dependje, `join-classpath`), a forma vizuálisan is mobbá alakít (Medve→POLAR_BEAR,
  Párduc→CAT, Hold→PARROT, Utazó→HORSE, ön-nézettel); nélküle a forma csak statisztikát vált. Bármilyen
  API-eltérés/hiányzó lib csendes no-op — soha nem töri el a cast-ot. A hívások a játékos régió-szálán
  futnak (Folia-helyes packet-küldés).
- **Specek:** **Vadőr (Feral)** — közelharci formák (savage_claw, maul, rampage, the_wild_hunt + wolf_call/
  panda_guard); **Holdjós (Balance)** — távolsági hold-/nap-mágia (moonfire, starsurge, starfall,
  celestial_alignment + sun_dance).
- **Bekötés:** `JobType.DRUID`, `SpecializationType.FERAL/LUNAR`, katalizátor-téma (Vadon Talizmánja —
  tölgycsemete), `config/classes.yml` unlock-ok (alap 2–21, specek 25–45), `SpellCatalog.registerDruid/
  Feral/Lunar`. Minden spell-ID egyedi és regisztrált (ellenőrzött).

### ✅ Paplovag kaszt (megvalósítva)
- **Kaszt:** `PALADIN` — szent harci/hibrid kaszt, katalizátora a „Szent Harang" (Bell). 10 alap-spell
  (smite, flash_heal, consecration, lay_on_hands, holy_wrath, divine_protection…), tisztán
  `ConfiguredSpell` (nincs új mechanika).
- **Specek:** **Szentlélek (Holy)** — gyógyítás/csapat-áldás (holy_light, beacon_of_light,
  blessing_of_kings, divine_shield, capstone avenging_wrath + a guardian healing_word újrahasználatával);
  **Megtorló (Retribution)** — szent sebzés (crusader_strike, judgment, divine_storm, holy_fire,
  capstone final_verdict). 26 új spell, mind egyedi ID, ellenőrzött.

### ✅ Halállovag + Sámán kasztok (megvalósítva)
- **Halállovag (`DEATH_KNIGHT`)** — rúnikus harcos, katalizátor „Rúnakovácsolt Koponya". Alap: fagy +
  vér (death_strike életszívással, chains_of_ice, bone_shield…). Specek: **Vérlovag (Blood)** tank
  önfenntartással (vampiric_blood, capstone dancing_rune_weapon); **Fagylovag (Frost)** sebző
  (obliterate, howling_blast, capstone breath_of_sindragosa).
- **Sámán (`SHAMAN`)** — elemi mágus, katalizátor „Ősök Totemje". Alap: villám/láva/jég + totem-aurák
  (a totemek jelenleg *instant aura-spellek*, nem lerakott entitások — a perzisztens totem-entitás egy
  jövőbeli, scheduler-es bővítés). Specek: **Elemi (Elemental)** távolsági (lava_burst, chain_lightning,
  capstone ascendance_flame); **Erősítő (Enhancement)** közelharci (stormstrike, crash_lightning,
  capstone doom_winds).
- 50 új spell, mind egyedi és regisztrált (globálisan ellenőrzött). A `JobGUI` slot-elrendezése
  6→8-ra bővült (két sor négyesével), hogy mind a 8 kaszt megjelenjen.

### ✅ Szerzetes kaszt (megvalósítva)
- **Szerzetes (`MONK`)** — harcművész, katalizátor „Jáde Bot" (bambusz). Alap: gyors kombók,
  mozgékonyság (roll/flying_serpent_kick), csi-gyógyítás (vivify/chi_wave). Specek: **Szélfutó
  (Windwalker)** kombó-sebző (rising_sun_kick, fists_of_fury, touch_of_death, capstone serenity);
  **Sörfőző (Brewmaster)** sör-buffos tank (keg_smash, breath_of_fire, capstone invoke_niuzao).
- 25 új spell, globálisan ellenőrzött. A `JobGUI` slot-elrendezése 8→9-re bővült (3×3 rács).

> **A 13 kaszt teljes (WoW-roster):** Varázsló, Harcos, Íjász, Orgyilkos, Druida, Paplovag, Halállovag,
> Sámán, Szerzetes, **Pap, Boszorkánymester, Démonvadász, Sárkányidéző**. Minden spell class-specifikus
> (egy spell = egy kaszt), a `JobGUI` 4×4 ráccsal (54 menü) jeleníti meg mind a 13-at. 26 spec, 35 talent.

### ✅ Perzisztens Sámán-totemek (megvalósítva)
Új `TotemManager` + `ShamanTotemSpell`: a totemek mostantól **lerakott, rövid életű ArmorStand-entitások**,
amik a SAJÁT régió-ütemezőjükön pulzálnak (Folia-helyes) a közeli entitásokra. 4 típus: **Gyógyár**
(szövetségesek regenerációja), **Perzselő** (ellenségek sebzése + gyújtás), **Szélharag** (szövetségesek
sebessége), **Földbéklyó** (ellenségek lassítása). Konfig: `spells.totem.{lifetime-seconds,radius,pulse-ticks}`.
A `healing_stream_totem`/`searing_totem` instant-aura verziói leváltva a perzisztensre; + 2 új totem
(`windfury_totem`, `earthbind_totem`) az Erősítő/Elemi specekben. A totemek a disable-kor takarítódnak (nincs orphan).

---

## 1. terület — Spell-variety: ~~capstone-ok és a 40+ sáv~~ (lásd 0. korrekció — már gazdag)

**Vízió:** minden specnek legyen egy **capstone** (40–50 szint) spellje, ami a spec identitását
csúcsra járatja, plusz 1–2 kitöltő spell a 25–40 sávba. Mind a `ConfiguredSpell.builder`-rel épül
(nincs új kód), a feloldási szint a `config/classes.yml` / `specializations.*.spell-unlocks` alá kerül.

### Capstone-ok specenként (konkrét builder-láncokkal)

| Spec | Capstone (szint) | Téma | Builder-vázlat |
|------|------------------|------|----------------|
| **Elementalist** | *Elemi Konvergencia* (45) | tűz+jég+villám egyszerre | `.target(7).aoe(5).damage(11).ignite(80).freeze(40).lightning().particle(FLAME,40).sound(...)` |
| **Necromancer** | *Lélekaratás* (45) | AoE életszívás a holtakból | `.target(8).aoe(6).damage(8).healSelf(6).targetEffect(WITHER,120,1).particle(SOUL,40)` |
| **Berserker** | *Ragnarök* (48) | önsebzés árán óriási AoE | `.self().aoe(6).damage(16).selfDamage(4).knockback(1.8).launchUp(0.6).selfEffect(STRENGTH,160,2)` |
| **Guardian** | *Sérthetetlenség Bástyája* (48) | csapat-védőaura | `.self().aoe(7).friendly().selfEffect(RESISTANCE,200,2).selfEffect(ABSORPTION,200,2).particle(BLOCK_CRACK...)` |
| **Sharpshooter** | *Nyílvihar* (45) | nyílzápor egy területre | `.target(20).aoe(5).damage(10).particle(CRIT,60).sound(ENTITY_ARROW_SHOOT...)` (vagy `ProjectileBurstSpell`) |
| **Beast Master** | *Ősvad Szövetség* (45) | pet + önmaga felerősítése | `SummonMinionsSpell` (3 farkas) + `.selfEffect(SPEED,200,1).selfEffect(STRENGTH,200,1)` |
| **Poisoner** | *Pestisfelhő* (45) | tartós AoE méreg+wither | `.target(7).aoe(5).damage(3).targetEffect(POISON,160,2).targetEffect(WITHER,80,0).particle(SNEEZE,50)` |
| **Phantom** | *Halál Tánca* (48) | láncblink célok között | `BlinkSpell` + `.target(6).damage(9).selfEffect(INVISIBILITY,60,0).knockback(0.5)` (bespoke chain) |

### Kitöltő spellek (25–40 sáv, példák)
- **Varázsló:** *Mana-örvény* (AoE pull + slow, 28), *Tér-szakadás* (blink+robbanás, 35).
- **Harcos:** *Földrengő Csapás* (kúp-knockback, 27), *Hadúr Kiáltása* (csapat-buff, friendly, 33).
- **Íjász:** *Mérgezett Vessző* (DoT-lövés, 26), *Szellem-nyíl* (átütő, 34).
- **Orgyilkos:** *Vérpenge* (lifesteal-csapás, 27), *Füstfátyol* (AoE blind+self-invis, 33).

### Új combo-párok (a meglévő `spells.combos` mintára, `config/spells.yml`)
- `convergence-finish`: `frost_touch` → `elemi_konvergencia` (gyorsabb felépülés).
- `rage-leap`: `heroic_leap` → `ragnarok`.
- `venom-cloud`: `venom_strike` → `pestisfelho`.

**Megvalósítás:** tisztán deklaratív a `SpellCatalog` megfelelő `register<Spec>` metódusában + unlock-szint
a configba + `messages/spell.yml` leírások. **Kockázat: ALACSONY** (bevált builder, nincs új mechanika).
A bespoke láncot igénylő 2 capstone (Phantom *Halál Tánca*, Berserker önsebzés-skálázás) `BaseSpell`-ben
valósul meg, ha a builder nem fedi — ezeket külön jelöljük.

---

## 2. terület — Relikviák: archetípusok, ritkaság, set-bónuszok

**Jelenlegi:** Mételytépő (törpe balta) + 4 frakció-elytra. A trigger jelenleg csak `RIGHT_CLICK_*`
(aktív). **Vízió:** több archetípus, ritkasági szintek, és frakció-set-bónuszok.

### Ritkasági szintek (szín + erő)
`COMMON` (fehér) → `RARE` (kék) → `EPIC` (lila) → `LEGENDARY` (arany) → `MYTHIC` (piros). A relikvia
neve a ritkaság színét kapja; magasabb szint = erősebb trigger-effekt.

### Új relikvia-archetípusok (lore-ba ágyazva)
- **Fegyver-relikviák** (aktív, `RIGHT_CLICK`):
  - *Törpekohó Pörölye* (LEGENDARY, törpe): jobbklikk → villám + tűzgyűrű a célpont körül.
  - *Mélység Pengéje* (EPIC, Dark): jobbklikk → következő csapás életszívással + wither.
  - *Vándor Parittyája* (RARE, Semleges): jobbklikk → lökéshullám (knockback AoE).
- **Dísz/amulett-relikviák** (offhand, passzív-jellegű aktiválás):
  - *Főnix Szíve* (LEGENDARY, Piros): jobbklikk → 5 mp tűz-immunitás + regeneráció.
  - *Zúzmara Könnye* (EPIC, Kék): jobbklikk → a közeli ellenfelek lefagynak (slow+freeze).
- **Frakció-set-bónuszok:** azonos frakció elytra + fegyver + amulett viselése → set-bónusz
  (pl. Piros set: tűz-immunitás + tűzcsóva siklás közben; Dark set: éjjel láthatatlanság + sebzés-bónusz).

### Új trigger-típusok (a varietyhez kell — KÖZEPES kockázat)
A `RelicTrigger`-t bővíteni: `ON_HIT` (támadáskor), `ON_DAMAGED` (sérüléskor), `ON_SNEAK`,
`PASSIVE_TICK` (periodikus, set-bónuszhoz). Ez új listener-bekötést igényel a relikvia-rendszerbe —
Folia-helyesen (a hordozó entitás ütemezőjén). **A passzív/set rész a magasabb kockázatú.**

**Megvalósítás:** az aktív (`RIGHT_CLICK`) relikviák tisztán a `config/relics.yml` + `RelicManager`
mintán deklaratívak (**ALACSONY** kockázat); a set-bónuszok és új triggerek kódot igényelnek (**KÖZEPES**).

---

## 3. terület — Világboss-archetípusok ✅ KÉSZ

**Megvalósítva:** a `WorldBossManager` mostantól **5 archetípus** közül választ véletlenül spawnkor —
*A Gyűrűk Őre* (Ravager), *Lávakohó Behemót* (Blaze, tűz-immunis), *Fagyott Trón Királya* (Stray,
erős lassító aura), *Csontkirály* (Wither Skeleton, wither-aura + erő), *Mélységi Rém* (Warden,
sötétség-aura, ×1.5 jutalom). Mindegyik saját entitás, név, HP-/sebzés-/jutalom-szorzó, spawnkori
self-buff, és **szignatúra-aura** (a `aura-radius`-on belüli túlélőkre periodikusan rárakott debuff).
**Fázis-mechanika:** 50% HP alatt a boss feldühödik (állandó erő+sebesség, broadcast). A fázis-tick a
boss saját régió-ütemezőjén fut (Folia-helyes), auto-retire a boss halálakor. Az archetípus a boss
PDC-jében tárolódik, így a kill-jutalom a megfelelő szorzót kapja. *(Eredeti terv lent.)*

### Eredeti terv

**Jelenlegi:** 1 config-vezérelt boss (Ravager — „A Gyűrűk Őre"). **Vízió:** több archetípus saját
témával, **fázisokkal** (HP-küszöbök → új képességek), egyedi **loot-táblával** és **rotációval/véletlennel**.

### Javasolt bossok (4 királyság-téma)
| Boss | Entitás-bázis | Téma | Fázis-mechanika |
|------|---------------|------|-----------------|
| *A Gyűrűk Őre* (megvan) | Ravager | törpe-örökség | — (alap) |
| *Lávakohó Behemót* | Magma Cube / Blaze | Piros/tűz | <50% HP: lávatócsák + tűzeső a területre |
| *Fagyott Trón Királya* | Stray / Skeleton | Kék/jég | <50%: jég-minionokat idéz, lassító aura |
| *Csontkirály* | Wither Skeleton | Dark/csont | hullámokban élőholtakat idéz; <33%: berserk |
| *Mélységi Rém* | Warden / Elder Guardian | Semleges/mély | sonic-blast AoE; sötétség-effekt |

**Közös keret:** boss-archetípus registry (`WorldBossArchetype` rekord: entitás-típus, név, HP-skála,
fázis-küszöbök → képesség-callbackek, loot-tábla, frakció-pont jutalom). A `WorldBossManager` a spawnkor
**véletlen/rotációs** archetípust választ. **Loot:** relikvia-szilánk (a 2. területhez), frakció-kincstár,
liga-pont, ritka recept (4. szakma-terület).

**Megvalósítás:** új kód (archetípus-registry + fázis-tick). A spawn/fázis-tick **Folia-helyesen** a boss
saját `getScheduler()`-jén (a meglévő world-boss minta már így spawnol). **Kockázat: KÖZEPES–MAGAS**
(új mechanika, fázis-állapot) — build-checkpoint ajánlott.

---

## 4. terület — Invázió + napi/heti küldetések

### Invázió-változatosság ✅ KÉSZ
**Megvalósítva:** a hullám VÉLETLEN horda-összetételt kap (4 archetípus: Élőhalott Áradat
[zombi+husk], Csontlégió [skeleton+stray], Pókfészek [spider+cave_spider], Káosz-horda
[vegyes+pillager]), és minden hullámot egy skálázott, **megnevezett mini-boss** (a horda bajnoka,
`mini-boss-level-bonus`-szal magasabb szint) vezet. A broadcast a horda nevét mutatja. *(Eredeti lent.)*

**Eredeti vízió:** témázott horda-összetételek + mini-boss + skálázódás.
- **Horda-típusok** (véletlen választás hullámonként): *Élőhalott Áradat* (zombi+husk), *Csontlégió*
  (skeleton+stray íjászok), *Pókfészek* (spider+cave_spider), *Káosz-horda* (vegyes + 1 vindicator).
- **Több hullám + mini-boss:** 3 hullám növekvő mérettel; az utolsó hullám egy **skálázott mini-boss**
  (a 3. terület archetípusainak gyengébb változata).
- **Skálázódás:** hullámonként +mob-count és +mob-level; frakció-specifikus jutalom a túlélőknek.

### Napi/heti küldetések
**✅ Részben kész:** a pool 3→**8** napi küldetésre bővült, az objective-típusok 3→**6**-ra
(KILL_MOBS, CATCH_FISH, BREAK_BLOCKS + új: **MINE_ORE**, **HARVEST_CROPS**, **KILL_ANIMALS**), a
`DailyQuestListener`-ben a megfelelő hookokkal (érc = `_ORE` anyagnév + ancient_debris; aratás =
kifejlett `Ageable`; állat = `Animals`). Plusz **streak-rendszer**: egymást követő napok bónusza
(konfigurálható `streak-bonus-per-day` / `streak-bonus-cap-days`). ✅ **Heti küldetés is kész:** külön
5-elemű heti pool (nagyobb célok, nagyobb jutalom), heti rotáció (epoch-day/7), saját PDC-bucket; a
`handle()` egyszerre táplálja a napit és a hetit; a `/daily` mindkettőt + a streaket mutatja.

**Eredeti vízió (a maradékhoz):**
- **Bővebb objective-pool:** `MINE_ORE`, `TRADE_MARKET` (piaci eladás/vétel), `WIN_RAID`,
  `DAMAGE_WORLD_BOSS`, `COMPLETE_PARKOUR`, `CRAFT_MASTERWORK`, `KILL_PLAYERS`.
- **Heti küldetés:** nagyobb cél + nagyobb jutalom (külön PDC-kulcs, heti rotáció).
- **Streak-jutalom:** egymást követő napok → szorzó (pl. 7 nap streak → +50% jutalom + ritka item).

**Megvalósítás:** az objective-ek a `DailyQuestManager` adat-vezérelt `daily(...)` mintáját követik
(új típusokhoz a megfelelő esemény-listener-progress kell — pl. `MINE_ORE`-hoz a blokk-törés listener).
A horda-típusok az `InvasionManager.spawnWave`-be egy összetétel-választóval. **Kockázat: ALACSONY–KÖZEPES**
(adat-vezérelt; az új objective-ekhez progress-bekötés kell, de a minta megvan).

---

## Javasolt megvalósítási sorrend (kockázat szerint)

| Fázis | Tartalom | Kockázat | Build kell? |
|-------|----------|----------|-------------|
| **1** | Capstone + kitöltő spellek (1. terület) + új combo-párok | ALACSONY | nem (deklaratív) |
| **2** | Aktív (`RIGHT_CLICK`) relikviák + ritkasági színek (2a) | ALACSONY | nem (config + minta) |
| **3** | Napi/heti objective-pool bővítés + streak (4b) | ALACSONY–KÖZEPES | részben |
| **4** | Invázió horda-típusok + mini-boss (4a) | KÖZEPES | ajánlott |
| **5** | Világboss-archetípusok + fázisok + loot (3) | KÖZEPES–MAGAS | igen |
| **6** | Relikvia set-bónuszok + új trigger-típusok (2b) | KÖZEPES | igen |

**Kezdés:** az **1. fázis (spellek)** a legjobb arány — tisztán deklaratív, azonnal érezhető variety,
Folia-semleges (a spellek a kasztoló régió-szálán futnak), és nem igényel buildet. Innen haladunk lefelé.

> Minden fázis megőrzi a Folia-baseline-t (lásd `ARCHITECTURE.md` 4.1) és a meglévő mintákat
> (lásd `ARCHITECTURE.md` 5. bővítési receptek).
