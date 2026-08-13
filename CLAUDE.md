# CLAUDE.md

This file is a Claude Code compatibility shim. The project is currently Codex-first and the canonical agent instructions live in `AGENTS.md`; keep this file so the project can be rolled back to Claude Code later without losing workflow context. When a durable rule changes, update `AGENTS.md` first and mirror the Claude-relevant subset here in the same commit.

## Munkaszervezés (tapasztalat-alapú delegálási szabályok, 2026-07-22)
- **Mit delegálj `sonnet`-nek (bevált):** körülhatárolt, READ-ONLY elemzés/audit egyetlen
  jól definiált szemponttal; mechanikus több-fájlos szerkesztés SZIGORÚ tilalmi listával
  (mit szabad, mit tilos); tömeges tartalom-konszolidálás. A promptba mindig: pontos
  scope, mit NE ismételjen (élő audit-doksi), bizonyíték-formátum (fájl:sor), kimeneti
  limit.
- **Mit NE delegálj:** architektúra-döntés, Folia-érzékeny refaktor, integrálás és a
  subagent-leletek ELLENŐRZÉSE — ez mindig a fő-agent dolga (a leleteket kézzel kell
  visszaigazolni a kódban, mielőtt hibaként kezeljük).
- **`haiku`-t a gyakorlat nem igazolta** — az "egyszerű" feladatok is ítélőképességet
  kérnek, és az utólagos ellenőrzés többe kerül, mint a sonnet-különbözet. Csak tiszta
  felsorolás/keresés-jellegű feladatra, ha egyáltalán.
- **`opus` a középső sávra:** körülhatárolható, de sonnet-nél több ítélőképességet kérő
  feladat — kényes (pl. konkurrencia-érzékeny) scope-olt elemzés/refaktor pontos
  specifikációval, kritikus javítás független másod-ellenőrzése. NEM helyettesíti a
  fő-agentet ott, ahol a session-kontextus számít (a delegált agent mindig üresen indul).
- **Teljes több-agentes audit-kör CSAK kifejezett tulaj-kérésre** — drága (agentenként
  100-340k token), és a gépi drift-ellenőrző + a Definition of Done pont azért él, hogy
  ne kelljen ismételni. Új, igazolt nyitott lelet a `ROADMAP.md` megfelelő
  részébe kerüljön; külön auditnaplót ne hozz létre.
- Egyszerre max 4-5 párhuzamos agent (a session-limit egyszer már elvitte a teljes kört).
- **Légy token-takarékos**: tömör válaszok, célzott fájlolvasás, ne duplikáld a subagent
  munkáját.

## Build & verify
```bash
python3 -m pip install Pillow  # required by HUD asset generation/audit
./gradlew build      # plugin jar -> build/libs
./gradlew runServer  # helyi tesztszerver (run/ könyvtár, 1.21.11)
```
- A `check` task dependency-free regressziós suite-okat futtat a persistence,
  DEV-item, moderáció, MOTD, sit, crate, config-startup, globális AFK,
  frakciótagság/passzív-policy és territory-capital területén. Ezek mellett
  a hibátlan build, a consistency gate és az
  `docs/ADMIN_GUIDE.md#release-acceptance-checklist` szerinti kézi playtest is kötelező.
- Célzott frakcióteszt: `./gradlew factionPassiveRegressionTest`; a normál
  `check` része, de passzív-reworknél külön is futtatandó.
- **HA a Gradle eléri a repókat (repo.papermc.io + extendedclip + md-5.net engedélyezve):
  a VALÓDI build a mérvadó, NEM a sandbox-javac.** Elsőként a wrapperrel fuss:
  `./gradlew build --console=plain --no-daemon`. Ha a környezetben külön rendszer-Gradle van
  megadva, azt is lehet használni, de ne feltételezz fix `/opt/gradle` útvonalat.
- **A sandbox-javac-szűrőnek IGAZOLT vakfoltjai vannak** (2026-07-23, valódi build tárta fel:
  17 latens hiba maradt rejtve): a szűrő eldobja a `does not override`-ot ÉS a
  `cannot find symbol: class ...`-t (belső osztályra is!), a `might not have been initialized`
  (definite-assignment) hibák pedig a szimbólum-hibák MÖGÖTT rejtőznek (a javac a flow-analízist
  csak feloldott szimbólumok után futtatja). Ezért egy külső típuson „elbukó" hívás (pl.
  `entity.getScheduler().run(plugin, …)`) az argumentum-hibát (`plugin` nincs scope-ban) is
  elrejti. **A sandbox-szűrő „0 belső hiba" NEM garantál fordulást — csak a valódi build az.**
- Ha a Gradle NEM éri el a repókat: `javac -sourcepath src/main/java` + szűrő csak DURVA
  előszűrő (a fenti vakfoltokkal); Bukkit/Adventure „cannot find symbol: variable/method"
  külső hiba elfogadható, de a `class`-szimbólum és `does not override` hibákat KÉZZEL is
  nézd át. Ismert baseline-műtermék: 2×"does not override" a gui/*Holder-ben.
- Sandbox-buktatók: a magyar „idézőjelek" Java- ÉS Python-stringben is törnek (Java-ban ” zárójelet használj, Pythonban aposztrófos stringet); a cwd Bash-hívások közt visszaállhat (mindig ellenőrizd a repo gyökerét); minden YAML-szerkesztés után `python3 -c "import yaml; yaml.safe_load(...)"` ellenőrzés.
- **Gépi drift-ellenőrzés — push előtt KÖTELEZŐ:** `python3 scripts/check_consistency.py`
  (YAML-ok, quest-hivatkozások, ITEM_MODEL visszaesés-védelem, jog-node-regisztráció, /menu célok,
  tükör-drift). FAIL-lel nem pushol­unk; a WARN-okat vagy javítjuk, vagy indokoljuk a
  commit-üzenetben. Ha új drift-osztály derül ki (audit talál olyat, amit kézzel kellett
  észrevenni), a scriptet BŐVÍTENI kell, ne csak a hibát javítani.
- **Verzió-bump ellenőrzés:** MC/Paper-frissítésnél az ELSŐ törési pont a bootstrap
  (unstable registry-API) — a védőháló catch-el és logol, de bumpnál kötelező ellenőrizni:
  a bootstrap fordul, a damage-type-ok és enchantok regisztrálódnak (log), a resist/stamp él.
- **Tükör-repo (IceSMPGuides):** az aktív, kanonikus szabály az `AGENTS.md`
  „Docs and IceSMPGuides mirror” szekciójában él. Minden megtartott Markdown-
  dokumentumot szó szerint, azonos relatív útvonallal kell tükrözni a
  `MilCsik09/IceSMPGuides` repóba; ne maradjon külön gyökérszintű guide-másolat.

## Mi ez a projekt
Folia-alapú Minecraft **1.21.11** Paper-plugin (Java **21**), MMO-jellegű SMP-rendszerekkel: **13 frakció-független kaszt + 35 specializáció**, hibrid kaszt-erőforrás, 4 frakció (passzívokkal, király/raid/szezon), szakmák + recept-katalógus, talentek, tárgy-raritás + loot, questek + közösségi célok, dinamikus árfolyamú gazdaság (bank/piac/aukció), relikviák + rituálé-oltárok, claimek, territórium-zónák, pet-rendszer és világesemények. A dokumentált release 825 Java-fájl / 92 manager. Minden játékos-szöveg **magyar**.

## Architektúra (nagy kép)
- **Belépési pontok** (`paper-plugin.yml`): `IceSMP` + `IceSMPBootstrap` + `IceSMPLoader`. A tényleges élet a `core/IceSMPCore`-ban van: konstruktorban épül fel az ÖSSZES manager (kézi DI, sorrend számít), majd `enable()`: `load()` a `persistentStores` listán → listener-regisztráció → parancs-regisztráció (kódból, nem manifestből!) → schedulerek; `disable()`: `save()` + cleanup.
- **Manager-réteg** (`managers/`): egy manager = egy rendszer állapota + perzisztenciája. Perzisztencia YAML-ba (`YamlStore.saveAtomic`, a `PersistentStore` interfészen át) vagy player-PDC-be. Per-player volatilis állapotot a `PlayerSessionCleanupListener`-ben kell takarítani (UUID-kulcsú map nem szivároghat).
- **GUI-minta** (`gui/` + `listeners/*GUIListener`): statikus GUI-osztály építi az inventoryt egy dedikált `InventoryHolder`-rel; a holder hordozza az owner-UUID-t és a slot→akció kötéseket; a listener a holder típusára szűr, mindent cancel-el, és drag+close eventet is kezel. A `/menu` hub (`CommandMenus` + `CommandMenuHolder`) akció-stringekkel dolgozik: `MENU:<almenü>` / `RUN:<parancs, menü-refresh-sel>` / `OPEN:<saját GUI-t nyitó parancs>` / `CLOSE` — a gameplay-logika MINDIG a parancsokban marad, a menü csak delegál. Admin-gombot mindig a mögöttes parancs tényleges jogosultság-node-jára kapuzz.
- **Parancsok** (`commands/`): Paper Brigadier `BasicCommand`-ok; nagyobb domainekhez router+subcommand felosztás (`commands/job|faction|currency|bank`), egyébként egy vékony osztály. Mindig adj tab-complete-et. Regisztráció: `IceSMPCore.registerCommands()`.
- **Config**: `src/main/resources/config/*.yml` — a `ConfigManager` kizárólag a `CONFIG_FILES` allowlist támogatott fájljait fésüli egybe (ÚJ fájlt a listába is fel KELL venni; az ismeretlen `.yml` kimarad és figyelmeztetést kap); üzenetek a `MessageManager`-en át (`messages.yml` kulcsok inline defaultokkal).
- **Élő-config konvenció**: MINDEN új kulcsot híváskor olvass (`configManager.getX` a use-site-on, ne konstruktorban) → `/icesmp reload` restart nélkül él. Ingame felülbírálás: `/icesmp config get|set|unset|list|find|menu` + kattintható GUI (`ConfigMenuGUI`, jog: `icesmp.admin.config`) — az írás EGYETLEN útja a szerializált `ConfigManager.applyOverride` (data-folder config.yml, utolsóként merge-ölve). A merged YAML és az override-pathok egyetlen immutable `ConfigManager.ConfigSnapshot` objektumban, egy atomikus referenciacserével publikálódnak; ne vezess vissza külön volatile generációrészeket. Boolean-kulcsok egyértelműek: `allow-<szabály>` séma (true=SZABAD), legacy invertált kulcsok fallbackként olvasva.
- **Esemény-spawnok**: minden világesemény-spawn az `EventSpawnGuard`-on megy át (`isBlocked(eventKey, loc)` — territory/claim/WG per `world-events.spawn-rules.<event>` mátrix; `isUnsafeSurface`; statikus `prepare(Mob)` zombisodás/nappali égés ellen). Új eseménynél új mátrix-sor + guard-hívás kötelező.
- **Frakciótagság és passzívok:** assignment nélkül a játékos Menedék-vendég, nem implicit `NEUTRAL`; gameplay-jogosultsághoz mindig `FactionMembership`/`FactionManager.isEligibleForFactionBenefits` kell, a `getEconomyFaction` csak megjelenítési/valuta-fallback. Tagságot csak a durable assignment+history candidate sikeres mentése után publikálj; fizetős váltásnál `FactionSwitchJournal` kötelező. Az adósság, strike és collection outbox kizárólag a PlayerProfile ECONOMY szekciójában él (`PlayerProfileTaxStore`), exact wallet mutation és idempotens receipt mellett. A sebzés-, exhaustion- és targetdöntést a tiszta `FactionPassivePolicy` hozza, a `FactionPassiveListener` csak Paper/Folia adapter; CANCEL döntésnél a targetet ténylegesen `null`-ra állítja. A DARK retaliation játékos–mob páronkénti, a nearby alert minden riasztott mobnak külön lease. Vérhold az ambient és vad DARK truce fölött áll. Signature-food buff csak fogyasztáskori live tagsággal futhat; itembe égetett feltétel nélküli frakcióbuff tilos.
- **Admin item-adás**: `/iceitem <unique|recept|relikvia|tervrajz> <id> [darab] [játékos]` (`ItemGiveCommand`, jog: `icesmp.admin.item`) — a recept-út a `ProfessionRecipeBookListener.buildResult` teljes stamp-láncát használja.
- **Itemek**: PDC-tagekkel (IDENTITÁS: signature_item, unique-id — ez marad) az `items/*ItemFactory`
  osztályokban, craft-safety listenerekkel védve. **Viselkedés/megjelenés = data-component** (1.20.5+,
  `items/ItemDataFactory`): CONSUMABLE/FOOD (ételek-italok), később GLIDER/DEATH_PROTECTION/EQUIPPABLE/
  USE_COOLDOWN/TOOLTIP_DISPLAY, és az ITEM_MODEL réteg. **KRITIKUS sorrend-invariáns:** a
  `itemStack.setData(...)` UTÁN a `setItemMeta(...)` TÖRLI a komponenst (a meta-round-trip nem hordozza)
  — a data-komponenseket MINDIG a meta-műveletek UTÁN, utolsóként kell alkalmazni (a `buildResult` a végén hívja).
- **Spellek**: deklaratív `ConfiguredSpell.builder(...)` a `spells/SpellCatalog`-ban + unlock a `config/classes.yml`-ben; külön osztály csak valóban állapotos spellnek (kötelező `clearPlayerState` override).
- **Soft-depend integrációk**: PlaceholderAPI, LibsDisguises, FancyNpcs, WorldGuard és LuckPerms — runtime opcionális hidak, a plugin nélkülük is fut. Build-oldalon csak PlaceholderAPI és LibsDisguises `compileOnly` (lásd `build.gradle.kts`); a többi híd reflexiós. A first-party HUD kizárólag immutable `HudSnapshot` adatot olvas, és nem gameplay authority.

## Folia-szabályok (KRITIKUS — részletek: `docs/ARCHITECTURE.md` §4)
- Soha `Bukkit.getScheduler()`; entitáshoz `entity.getScheduler()`, helyhez `getRegionScheduler()`, globálishoz `getGlobalRegionScheduler()`.
- Az eventek az esemény-entitás régió-szálán futnak. **MÁSIK entitás bármilyen érintése** (PDC/inventory olvasás, akár `sendMessage`) **scheduler-hoppal**: `target.getScheduler().run(plugin, task -> {...}, null)` — minta: kill-reward listenerek, MarketGUI seller-értesítés.
- Megosztott, több régió-szálról elért állapot: synchronized/concurrent szerkezet, vagy copy-on-write snapshot (minta: `QuestManager.customQuests`).
- Teleport: `teleportAsync`; blokk-szkennelés kicsi és régió-lokális legyen.

## Konvenciók
- **Komment-politika (tulaj-kérés, 2026-07-22):** Java-kommentet CSAK megszorítás/invariáns
  indoklására írunk (miért ilyen a kód, mi törne el nélküle — pl. Folia-hop, atomicitás,
  fail-open ok). TILOS: provenancia ("audit-lelet", "tulaj-döntés", tétel-azonosítók,
  review-hivatkozás), kód-narrálás ("// mentés"), változás-történet ("// mostantól").
  A config-YAML kommentjei kivételek: ott a komment a dokumentáció.
- **MD-politika:** új .md fájl CSAK kifejezett tulaj-kérésre születhet.
  Igazolt nyitott auditpont és még nem vállalt ötlet egyaránt a `ROADMAP.md`
  megfelelő részébe kerül; külön audit- vagy backlogoldal nem készül.
  Embernek szánt új feature/reference/release oldal helyett az öt kanonikus
  kézikönyv egyikét kell frissíteni.
- **Definition of Done — minden változás UGYANABBAN a commitban propagál:**
  - új parancs → tab-complete + `/menu` csempe (CommandMenus) +
    `docs/PLAYER_GUIDE.md` érintett része + `docs/ADMIN_GUIDE.md` parancsreferenciája + jog-node
  - új jog-node → `Permissions.java` canonical map (különben az admin.all nem adja meg!)
  - új config-kulcs → use-site olvasás (élő-config) + ha admin-hangolandó: ConfigMenuGUI
  - új custom item → **ITEM_MODEL** (modern; `item-model:`/`ItemDataFactory.applyItemModel`) +
    `docs/RESOURCE_PACK_CMD.md` manifest-sor. (ÚJ itemnél már NEM adunk régi numerikus modelladatot.)
  - új quest-NPC / territory-id → `docs/BUILDER_GUIDE.md` + a `ROADMAP.md` alkalmazható builderkapuja
  - új rendszer/mechanika → `docs/FEATURES.md` + érintett szerepköri kézikönyv +
    acceptance-eset a `docs/ADMIN_GUIDE.md`-ban + `LORE_REFERENCE.md` sor, ha lore-kötött
  - minden doksi-szám a configból származik, nem fejből
  - kliensprotokoll-változás (bármi a `client/protocol/` alatt, `ClientCapability`,
    kézfogás/session-kapu szemantika) → bájtazonos re-port a `MilCsik09/IceSMP-Fabric`
    repóba (`src/main/java/hu/taliann/icesmp/client/protocol/`) + a golden-vectorok és
    flow-tesztek frissítése az ottani suite-okban, UGYANABBAN a munkaegységben — a két
    repo a vezetéken sosem csúszhat szét; a kanonikus protokoll-doksi a
    `docs/ARCHITECTURE.md` „Client Bridge” szekciója
  - záráskor: fordítás-ellenőrzés + `scripts/check_consistency.py` + tükör-push
- Játékos-szöveg magyarul, `MessageManager` + `messages.yml` kulccsal és inline defaulttal.
- **Item-megjelenés szabálya (ITEM_MODEL migráció KÉSZ, 2026-07-23):** MINDEN custom/unique
  item **ITEM_MODEL** komponenst visel (string modell-id az `icesmp:` névtérben) — régi numerikus modelladatot
  már SEHOL nem használunk (a `scripts/check_consistency.py` FAIL-el bármely `setCustomModelData`-ra
  ÉS `custom-model-data:` config-kulcsra). Új itemnél: `result.item-model: "icesmp:<id>"` a receptben,
  vagy `ItemDataFactory.applyItemModel(item, "icesmp:<id>")` a factoryban (a setItemMeta UTÁN!), és
  fel KELL venni a `docs/RESOURCE_PACK_CMD.md` manifestbe (a pack-készítő az
  `assets/icesmp/items/<id>.json`-t szállítja). A resource pack manifest a modern modell-id-ket sorolja.
- Minden gameplay-változásnál frissítsd az öt kanonikus kézikönyv érintett részeit. A számszerű állítások egyezzenek a config-fájlokkal.
- Commit-üzenet: magyar, tömör tárgysor + felsorolásos törzs; a repo-history kompatibilitás miatt a meglévő `Co-Authored-By` + `Claude-Session` trailerekkel zárul, amíg a tulaj nem vált trailer-politikát. Csak a kijelölt feature-branchre pusholj.
- Részletes projekt-tudás: `AGENTS.md` (domain-számok, spell-költség hibrid, HUD, faction-passzívák), `docs/ARCHITECTURE.md` (technikai referencia), `ROADMAP.md` (nyitott munkák).
- **Lore/tartalom-referencia:** `docs/LORE.md` — a kanonikus kódex, TISZTA világon-belüli szöveg (ne
  írj bele mechanika-jegyzetet, táblát, config-kulcsot!). A technikai megfeleltetés a
  `docs/LORE_REFERENCE.md`-ben él; a teljes builder-oldali quest-, dialógus- és NPC-leltár a
  `docs/QUESTS.md`-ben található. A referencia frakció↔kód (`RED`=Perinfernicitas, `BLUE`=Cryghaliris,
  `NEUTRAL`=Ryanora/Caldestera, `DARK`=Kitaszítottak), lore-elem→mechanika tábla, unique-item
  tervkatalógus, elnevezési irányelvek. Minden új tartalom (item-nevek, ételek, valuta, mob-drop,
  zóna, quest) a kódexhez illeszkedjen — a kód-kötést a referencia-fájlban vezesd.
## PlayerProfile authority rule

Do not add new persistent player PDC/YAML/map authorities. Add or extend a PlayerProfile section and update the authority matrix/guard. PlayerProfile HTTP remains read-only and disabled by default.


