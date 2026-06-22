# IceSMP — Átszervezési terv (refaktor)

> **Állapot:** TERV — **a CODE-REVIEW hibajavítások (`docs/CODE-REVIEW.md`) után** végrehajtandó.
> **Indok:** a nagy architektúra-refaktor ütközne a hátralévő bug-csomagokkal (4–6), és a hibák
> a fontosabbak. Ez a dokumentum csak rögzíti az elemzést, hogy később ne kelljen újra felmérni.

A kódbázis (214 fájl, ~25k sor) gyorsan nőtt: az újabb kód tiszta mintákat vezetett be
(subcommand-registry, adat-vezérelt menük, `ConfiguredSpell`, atomikus perzisztencia), de a régi
kódot csak részben migrálták rájuk. Alább területenként: jelen állapot, a költség, a cél, és egy
alacsony kockázatú első lépés.

## Már kész (config — a „túl hosszú" panaszra, hibajavítás-szakaszban is biztonságos volt)
- ✅ A `config.yml` halott `messages:` szekciója törölve (1062 → 943 sor). A `MessageManager`
  kizárólag a `messages.yml`-ből olvas.

## Config — hátralévő (alacsony kockázat)
- **Alrendszerenkénti config-fájlok (kérés).** Az egyetlen `config.yml` helyett minden alrendszernek
  saját fájl egy `config/` mappában: `config/classes.yml`, `config/specializations.yml`,
  `config/talents.yml`, `config/professions.yml`, `config/quests.yml`, `config/world-events.yml`,
  `config/economy.yml`, `config/factions.yml`, `config/pets.yml`, `config/market.yml` stb. (A legnagyobb
  blokkok: talents ~173 sor, specializations spell-unlocks ~111, classes, achievements/quests ~94.)
  *Megvalósítás:* a `ConfigManager`-t több-fájlossá tenni — egy fő `config.yml` (általános `settings`)
  + a `config/` mappa minden `*.yml`-jét betölti és egy logikai névtérbe (vagy egy egyesített
  `MemoryConfiguration`-be) merge-eli, hogy a meglévő `getX("alrendszer.kulcs")` hívások változatlanul
  működjenek. Alapfájlokat a jarból `saveResource`-szal kell kicsomagolni első indításkor.
  *Kockázat:* közepes (a betöltő átírása); a kulcs-útvonalak megtarthatók, így a hívó kód nem változik.
- **Alrendszerenkénti messages-fájlok (kérés).** Ugyanígy a `messages.yml` helyett `messages/` mappa:
  `messages/currency.yml`, `messages/faction.yml`, `messages/spell.yml`, `messages/market.yml`,
  `messages/quest.yml`, `messages/pet.yml`, `messages/world-event.yml`, `messages/system.yml` …
  A `MessageManager`-t több-fájlossá tenni (a `messages.<root>` névteret a fájlnévhez kötve, vagy minden
  fájlt egy egyesített konfigba olvasva). Átmenetként a jelenlegi egy `messages.yml` is maradhat, csak
  szekció-bannerekkel tagolva.
- **Üzenet-formátum egységesítése.** Jelenleg vegyes: ~128 MiniMessage (`<tag>`) + ~290 legacy (`&`).
  ✅ A *rendering* már javítva (a `MessageManager` minden útvonala — `get`/`getMessage`/`getComponent` —
  formátum-tudatos: MiniMessage VAGY legacy, a vegyeset nem rontja el). **Hátralévő (kozmetikai):** a
  `messages.yml` tartalmát egységes formátumra hozni — javasolt mindent **MiniMessage**-re, mert az
  gazdagabb (gradiens/hover/click) és a kód már mindenhol kezeli.

## Kód-architektúra

### 1. Parancs-architektúra
- **Jelen:** mind a 26 parancs `BasicCommand`. Két diszpécser-stílus: **registry** (új, 3: Currency/Job/Faction
  + `commands/{currency,faction,job}/` al-parancsok) és **inline switch** (régi, ~15: Profession, Relic,
  Quest, Territory, Spec, Events, Market, Bank, Parkour, Pet, Sinner, Talent, Spell, Soul, ExchangeBoard).
  A 3 `*Subcommand` interfész bájtra azonos.
- **Cél:** egyetlen `Subcommand` SPI + `AbstractDispatchCommand` bázis (map + diszpécs + help + tab-complete).
- **Első lépés:** a 3 azonos interfész egyesítése egy `Subcommand`-dá; egy közepes inline parancs (pl.
  `MarketCommand`) migrálása referenciának.

### 2. Perzisztencia
- **Jelen:** 3 stílus. (a) PDC-alapú (`DailyQuestManager`, `ProfessionManager`) — nincs fájl-IO.
  (b) 12 YAML-manager saját `load()/save()`-vel. Ezek közül: **atomikus + debounce async** csak
  `CurrencyManager`; **atomikus, de szinkron inline save** `MarketManager`, `FactionTreasuryManager`;
  **sima blokkoló `yaml.save()`** 9 manager (FactionManager, KingManager, SeasonManager, StatsManager,
  TerritoryManager, ParkourManager, RelicManager, EconomyEventManager, ExchangeBoardManager).
- **Költség:** ~25–40 sor azonos váz managerenként; az atomikus temp+rename blokk 3 helyen szó szerint
  másolva; 9 manager blokkoló disk-IO-t végez a régió-szálon (lásd CODE-REVIEW minta B / `CORE-2`).
- **Cél:** absztrakt `YamlStore` bázis (`read(yaml)`/`write(yaml)` + közös `load/save/requestSave`
  atomikus+debounce). Mind a 12 manager rátér; a core load/save listái egy `List<YamlStore>` iterációvá
  egyszerűsödnek.
- **Első lépés:** `YamlStore` kiemelése a `CurrencyManager` (kész minta) logikájából; egy sima manager
  (pl. `KingManager`) átállítása az API validálására, majd a többi.

### 3. MessageManager
- **Jelen:** generikus `get/getMessage/getComponent` API + 8 bespoke getter. **Halott (2):**
  `getCurrencyHelpHeader`, `getBalance` (0 hívó). **Élő (6):** mind a `MetelytepoRelicListener`-ből
  (`getSinnerMarked`, `getAbilityCooldown`, `getNoTarget`, `getTargetNotSinner`, `getJusticeActivated`,
  `getHonorEyeActivated`).
- **Cél:** a 2 halott törlése; a 6 élő migrálása `getComponent("messages.<key>", default)`-ra, majd törlés.
- **Első lépés:** a 2 halott getter törlése (fordító-igazolt). *(Ezt egyszer megkezdtem, majd a
  „refaktor a hibák után" döntés miatt visszavontam — itt a helye, később.)*

### 4. GUI-architektúra
- **Jelen:** két minta. **A (adat-vezérelt):** CommandMenus (~10 menü) + CommandMenuHolder + CommandMenuListener.
  **B (bespoke):** MarketGUI, SpellbookGUI, ProfileGUI/SpecGUI/ProfessionGUI/TalentGUI (közös CharacterGUIListener),
  SkillTreeGUI, JobGUI.
- **Duplikáció:** `label()` 6 fájlban (HudManager, CommandMenus, ProfileGUI, TalentGUI, SpecGUI, ProfessionGUI);
  `accent/grey/click` 4+ fájlban; nav-nyíl builder MarketGUI vs SpellbookGUI; lapozás-logika közel szó
  szerint MarketGUI és SpellbookGUI közt; JobGUI/SkillTreeGUI megkerüli a `GuiUtil`-t (saját `createFiller`).
- **Cél:** `accent/grey/click/label/nav/back/close` a `GuiUtil`-ba; JobGUI/SkillTreeGUI átállítása `GuiUtil`-ra;
  `PaginatedGui` bázis Market/Spellbook-hoz; hosszabb távon a statikus gombmenük (Job/Profile/Spec/Profession/
  Talent) átköltöztetése a CommandMenu A-mintába.
- **Első lépés:** `label/accent/grey/click` a `GuiUtil`-ba, a másolatok törlése (tiszta konszolidáció).

### 5. IceSMPCore „god-object"
- **Jelen:** ~563 sor; a konstruktor ~40 managert épít szigorú kézi sorrendben (egy átrendezés már
  kellett); + 24 spell, 24 parancs, ~45 listener regisztrálása; külön kézi load- és save-lista.
- **Költség:** törékeny (implicit sorrend; 3 külön kézzel szinkronban tartott lista). A legtöbbet
  változó fájl.
- **Cél (pragmatikus, nem DI-keretrendszer):** a konstruktor felbontása kohéziós factory-metódusokra
  (`createEconomy()`, `createFactions()`, `createCombatSpells()`, `createGuiContexts()`), lokalizált +
  kommentált sorrend-függőségekkel; `registerSpells()` külön metódus. A `YamlStore`-registry-vel a
  load/save lista `stores.forEach(...)`-cá egyszerűsödik.
- **Első lépés:** `registerSpells()` kiemelése a konstruktorból + sorrend-indok komment (nulla kockázat).

### 6. Player-state cleanup
- **Jelen:** `PlayerSessionCleanupListener` kézi listán hív `clearPlayerState`-et 7 manageren + 6
  **statikus** spell-cleanupon (HideSpell, LuckyStarSpell, ArmamentSpell, InnerFocusSpell, RootSpell,
  DoubleJumpSpell). A `RootSpell.clearPlayerState` már no-op (drift). A cleanup 4 listener közt szórt
  (PlayerSessionCleanupListener, HudListener, TerritoryListener, ParkourListener).
- **Kockázat:** új állapotos spell/manager hozzáadásakor könnyű elfelejteni → csendes leak (jelenleg
  nem szivárog semmi, a 6 wired spell pont a 6 állapotos — strukturális/jövőbeli rizikó).
- **Cél:** `PlayerStateCleanup` interfész; minden állapotos egység implementálja + egy listába regisztrál,
  amit a listener iterál. A spellek állapota legyen instance-szintű (a `SpellRegistry`-ben már singletonok),
  így `spellRegistry.getAll().forEach(s -> s.clearPlayerState(id))`.
- **Első lépés:** `PlayerStateCleanup` interfész a 7 már bekötött managernek; a halott `RootSpell` hívás +
  import törlése.

### 7. Egyéb
- **Statikus-állapotú spellek vs. instance-managerek** (a 6. pont gyökéroka) — a 6 legacy spell migrálása
  instance-állapotra; egyszerűek hosszú távon `ConfiguredSpell`-be.
- **Csomag-elrendezés:** a `listeners/` keveri a GUI-klikk listenereket a gameplay-listenerekkel →
  `gui/listeners/` alcsomag. Az `AbilityCatalystListener` valójában állapotos szolgáltatás (mező, parancsoknak
  átadva) — a „listener" név alulbecsli a szerepét.
- **Context-objektum burjánzás:** `CommandMenuContext` (16 függőség), `CharacterMenuContext` (11) kézi
  „god-bag"-ek; ha az 5. pontból service-locator lesz, onnan olvashatnának.

## Tartalmi bővítés ("variety") — szintén a refaktor után

Néhány alrendszer jelenleg „mutatóban" van (működő keret, kevés tartalom). Ezek bővítése külön
lista (nem hibajavítás, nem refaktor — tartalom). Az alrendszerenkénti config-fájlok (lásd fent)
pont ezt a bővítést teszik kényelmessé.

- **Világboss** — jelenleg **1 típus** (`RAVAGER`), egy statikus boss. → több boss-archetípus (saját
  képességek, fázisok, loot-tábla), véletlenszerű/rotációs választás, egyedi nevek.
- **Invázió** — jelenleg **1 mob-típus** (`ZOMBIE`) hullámonként. → többféle horda-összetétel, mini-boss
  hullám, skálázódó nehézség, frakció-specifikus jutalom.
- **Rituálék** — **~4** oltár-recept. → több rituálé (buff/summon/teleport/időjárás), ritka összetevők.
- **Relikviák** — **~7** képesség. → több relikvia-archetípus, set-bónuszok, ritkasági szintek.
- **Napi küldetések** — kis pool, kevés objective-típus. → bővebb pool, heti küldetés, streak-jutalom.
- **Elérések** — kevés mérföldkő. → több kategória (gazdaság, harc, szakma, felfedezés), fokozatok.
- **Parkour-próbák** — csak keret, a pályák kézzel épülnek. → előre definiált pálya-séták, ranglista,
  napi futam.
- **Szakma-receptek (masterwork)** — kevés recept. → szakmánként több szintű, ritka recept-fa.
- **Kozmetikumok (ROADMAP 11.)** — még nincs. → titulusok, partikli-effektek, kalapok/skinek.
- **Raid/frakció-diplomácia (ROADMAP 4–5.)** — alap. → szövetség/béke, frakció-szintek/perkek,
  objektíva-alapú (zászló) raidek, terület-elfoglalás.
- **Spell-variety** — sok spell van, de specenként a magas szintű (40+) kínálat vékonyodik. → capstone-
  spellek, spec-identitást erősítő egyedi mechanikák.

## Prioritás (a hibajavítások UTÁN)

**Gyors, biztonságos (órák, ~0 kockázat):**
1. ✅ **Kész** — 2 halott `MessageManager` getter törlése (`getCurrencyHelpHeader`, `getBalance`).
2. 🔶 **Részben kész** — `label()` a `GuiUtil`-ba kiemelve, az 5 GUI lokális másolata törölve
   (static importtal, hívások változatlanok); a `HudManager` saját `label`-je (scoreboard, decoration
   nélkül) szándékosan érintetlen. Hátra: `accent/grey` (azonos törzs, ugyanígy kiemelhető) és `click`
   (a CommandMenusban `click()` no-arg, máshol `click(String)` — eltér, óvatosan).
3. ✅ **Kész** — `registerSpells()` kiemelése a `IceSMPCore` konstruktorból.
4. ✅ **Kész** — halott `RootSpell.clearPlayerState` hívás + import + no-op metódus törlése.
5. ✅ **Kész (biztonságos variáns)** — közös `commands/Subcommand` ős létrehozva; a 3 azonos
   `*Subcommand` interfész üres `extends Subcommand` markerré vált (a metódus-szerződés egy helyen).
   A 19 implementáció és a 3 command-map ÉRINTETLEN — nulla törés-kockázat.
6. `messages.yml` szekció-bannerek.

**Közepes (napok, alacsony-közepes kockázat):**
7. ✅ **Kész** — a 6 élő `MessageManager` getter migrálva a generikus `getComponent`-re (egyetlen
   fogyasztó: `MetelytepoRelicListener`), majd törölve. A legacy bespoke API megszűnt.
8. JobGUI/SkillTreeGUI a `GuiUtil`-ra; `PaginatedGui` bázis Market/Spellbook-hoz.
9. `PlayerStateCleanup` interfész + lista-iteráció.
10. `AbstractDispatchCommand` bázis; inline parancsok migrálása egyenként.

**Nagyobb (megfontolandó):**
11. `YamlStore` bázis kiemelése a CurrencyManagerből; mind a 12 YAML-manager rátér (mindenhol atomikus +
    debounce mentés; a core load/save listái összeolvadnak).
12. Az `IceSMPCore` konstruktor felbontása domén-factory-metódusokra; load/save/cleanup regisztrált listákból.
13. A statikus-gomb GUI-k (Job/Profile/Spec/Profession/Talent) átköltöztetése az adat-vezérelt CommandMenu
    rendszerbe; a legacy spell-állapot instance-szintűvé tétele.
