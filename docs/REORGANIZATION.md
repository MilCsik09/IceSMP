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
- **Jelen:** mind a 25 parancs `BasicCommand` (a `commands/Subcommand.java` a közös SPI, nem parancs).
  Két diszpécser-stílus: **registry** (új, 3: Currency/Job/Faction
  + `commands/{currency,faction,job}/` al-parancsok) és **inline switch** (régi, ~15: Profession, Relic,
  Quest, Territory, Spec, Events, Market, Bank, Parkour, Pet, Sinner, Talent, Spell, Soul, ExchangeBoard).
  A 3 `*Subcommand` interfész bájtra azonos.
- **Cél:** egyetlen `Subcommand` SPI + `AbstractDispatchCommand` bázis (map + diszpécs + help + tab-complete).
- **Első lépés:** a 3 azonos interfész egyesítése egy `Subcommand`-dá; egy közepes inline parancs (pl.
  `MarketCommand`) migrálása referenciának.

### 2. Perzisztencia
- **Jelen (frissítve):** 3 stílus. (a) PDC-alapú (`DailyQuestManager`, `ProfessionManager`) — nincs fájl-IO.
  (b) 12 YAML-manager. ✅ **Mind a 12 mostantól a közös `storage/YamlStore.saveAtomic`-on át ír**
  (egyedi temp + atomikus rename, konkurens-biztos) — a duplikált atomikus blokkok megszűntek, és a 9
  korábban nem-atomikus író is biztonságos lett. **Hátralévő:** (1) debounce-mentés — jelenleg csak a
  `CurrencyManager` debounce-ol (`requestSave`), a többi 11 szinkron (atomikusan) ment a régió-szálon;
  (2) a `IceSMPCore` kézi load/save listáinak összeolvasztása egyetlen iterációvá.
- **Cél (hátralévő):** a managerek mentés-hívásai debounce-on át (a CurrencyManager mintája), és egy
  regisztrált store-lista, amit a core `load()`/`save()` végigiterál (a 2 kézi lista helyett).
- **Részletek:** lásd a Prioritás-lista 11. pontját (ott a részleges-kész státusz).

### 3. MessageManager ✅ KÉSZ
- **Eredmény:** mind a 8 bespoke getter törölve; a `MessageManager` már csak a generikus
  `get/getMessage/getComponent` API-t adja (mindegyik formátum-tudatos: MiniMessage VAGY legacy).
  A 6 élő hívó (`MetelytepoRelicListener`) átállt `getComponent("messages.<key>", default[, args])`-ra.
  (Lásd a Prioritás-lista 1. és 7. pontját.)

### 4. GUI-architektúra
- **Jelen:** két minta. **A (adat-vezérelt):** CommandMenus (~10 menü) + CommandMenuHolder + CommandMenuListener.
  **B (bespoke):** MarketGUI, SpellbookGUI, ProfileGUI/SpecGUI/ProfessionGUI/TalentGUI (közös CharacterGUIListener),
  SkillTreeGUI, JobGUI.
- **Duplikáció (frissítve):** ✅ `label/accent/grey` már a `GuiUtil`-ban (a GUI-k static importtal hívják);
  a `label` privát változata már csak 2 helyen van: `GuiUtil` (a közös) és `HudManager` (scoreboard,
  szándékosan eltérő — nincs italic-decoration). **Hátralévő duplikáció:** a `click` (CommandMenusban
  no-arg `click()`, máshol `click(String)` — eltérő, ezért kihagyva); nav-nyíl builder MarketGUI vs
  SpellbookGUI; lapozás-logika MarketGUI vs SpellbookGUI; JobGUI saját `createFiller`; SkillTreeGUI
  megkerüli a `GuiUtil.icon`-t (inline item-építés).
- **Cél (hátralévő):** `nav/back/close` + lapozás a `GuiUtil`-ba / egy `PaginatedGui` bázisba
  (Market/Spellbook); JobGUI/SkillTreeGUI átállítása `GuiUtil`-ra; hosszabb távon a statikus gombmenük
  (Job/Profile/Spec/Profession/Talent) átköltöztetése a CommandMenu A-mintába.

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
- **Jelen (frissítve):** `PlayerSessionCleanupListener` kézi listán hív `clearPlayerState`-et 7 manageren +
  **5 statikus** spell-cleanupon (HideSpell, LuckyStarSpell, ArmamentSpell, InnerFocusSpell, DoubleJumpSpell).
  *(A `RootSpell` no-op cleanupja már törölve — lásd #4.)* A cleanup 4 listener közt szórt
  (PlayerSessionCleanupListener, HudListener, TerritoryListener, ParkourListener).
- **Kockázat:** új állapotos spell/manager hozzáadásakor könnyű elfelejteni → csendes leak (jelenleg
  nem szivárog semmi, az 5 wired spell pont az 5 állapotos — strukturális/jövőbeli rizikó).
- **Cél:** `PlayerStateCleanup` interfész; minden állapotos egység implementálja + egy listába regisztrál,
  amit a listener iterál. A spellek állapota legyen instance-szintű (a `SpellRegistry`-ben már singletonok),
  így `spellRegistry.getAll().forEach(s -> s.clearPlayerState(id))`.
- **Első lépés:** `PlayerStateCleanup` interfész a 7 már bekötött managernek; a listener egy regisztrált
  listát iterál.

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
2. ✅ **Kész (a `click` kivételével)** — `label`, `accent`, `grey` a `GuiUtil`-ba kiemelve; a GUI-k
   lokális másolatai törölve (static importtal, ~150 hívás VÁLTOZATLAN). A `HudManager.label` (scoreboard,
   decoration nélkül) szándékosan érintetlen. A `click` szándékosan kihagyva: a CommandMenusban `click()`
   no-arg, máshol `click(String)` — eltérő szignatúra, nem vonható össze törés nélkül.
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
9. 🔶 **Részben kész** — `session/PlayerStateCleanup` interfész létrehozva; a 7 állapotos manager/listener
   implementálja, a `PlayerSessionCleanupListener` egy regisztrált `List<PlayerStateCleanup>`-ot iterál
   (a konstruktor-szignatúra változatlan). Hátra: az 5 statikus spell-cleanup instance-szintűvé tétele,
   hogy azok is a listába kerüljenek (a static→instance migráció build-checkpointot igényel).
10. 🔶 **Részben kész** — `commands/AbstractDispatchCommand` bázis létrehozva (map + diszpécs + help +
    tab-complete egy helyen); a 3 registry-parancs (Currency/Job/Faction) rátért, mindegyik a
    konstruktorára zsugorodott (~210 sor duplikáció megszűnt). A message-kulcsok pontosan megőrzöttek,
    az IceSMPCore-hívások változatlanok. Hátra: a ~15 inline-switch parancs migrálása a bázisra.

**Nagyobb (megfontolandó):**
11. 🔶 **Részben kész** — `storage/YamlStore.saveAtomic` egységes, konkurens-biztos (egyedi temp + atomikus
    rename) ÍRÁS; mind a 12 YAML-manager erre tért át (a 9 eddig nem-atomikus író is). Hátra: debounce
    minden managernél (jelenleg csak a CurrencyManager debounce-ol) + a core load/save listák összeolvasztása.
12. Az `IceSMPCore` konstruktor felbontása domén-factory-metódusokra; load/save/cleanup regisztrált listákból.
13. A statikus-gomb GUI-k (Job/Profile/Spec/Profession/Talent) átköltöztetése az adat-vezérelt CommandMenu
    rendszerbe; a legacy spell-állapot instance-szintűvé tétele.
