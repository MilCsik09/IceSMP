# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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
  ne kelljen ismételni. Új leletek az élő `docs/ideas/P2-gameplay-audit.md`-be, helyben.
- Egyszerre max 4-5 párhuzamos agent (a session-limit egyszer már elvitte a teljes kört).
- **Légy token-takarékos**: tömör válaszok, célzott fájlolvasás, ne duplikáld a subagent
  munkáját.

## Build & verify
```bash
./gradlew build      # plugin jar -> build/libs
./gradlew runServer  # helyi tesztszerver (run/ könyvtár, 1.21.11)
```
- Nincs teszt-suite; az ellenőrzés = hibátlan fordítás + kézi playtest (`PLAYTEST.md`).
- **HA a Gradle eléri a repókat (repo.papermc.io + extendedclip + md-5.net engedélyezve):
  a VALÓDI build a mérvadó, NEM a sandbox-javac.** Rendszer-Gradle van telepítve:
  `/opt/gradle/bin/gradle build --console=plain --no-daemon` (a projekt-wrapper 9.4.1-et
  töltene a github releases-ről, ami tiltott lehet; a rendszer-Gradle 8.14.3 lefordítja).
  Push előtt ha elérhető, EZ fusson.
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
- Sandbox-buktatók: a magyar „idézőjelek" Java- ÉS Python-stringben is törnek (Java-ban ” zárójelet használj, Pythonban aposztrófos stringet); a cwd Bash-hívások közt visszaáll (`cd /home/user/IceSMP` mindig); minden YAML-szerkesztés után `python3 -c "import yaml; yaml.safe_load(...)"` ellenőrzés.
- **Gépi drift-ellenőrzés — push előtt KÖTELEZŐ:** `python3 scripts/check_consistency.py`
  (YAML-ok, quest-hivatkozások, CMD-regiszter, jog-node-regisztráció, /menu célok,
  tükör-drift). FAIL-lel nem pushol­unk; a WARN-okat vagy javítjuk, vagy indokoljuk a
  commit-üzenetben. Ha új drift-osztály derül ki (audit talál olyat, amit kézzel kellett
  észrevenni), a scriptet BŐVÍTENI kell, ne csak a hibát javítani.
- **Verzió-bump ellenőrzés:** MC/Paper-frissítésnél az ELSŐ törési pont a bootstrap
  (unstable registry-API) — a védőháló catch-el és logol, de bumpnál kötelező ellenőrizni:
  a bootstrap fordul, a damage-type-ok és enchantok regisztrálódnak (log), a resist/stamp él.
- **Tükör-repo (IceSMPGuides):** a docs egy része a `MilCsik09/IceSMPGuides` repóba is átmásolandó minden változtatásnál. Térkép: `PLAYTEST.md` ↔ gyökér; `docs/player-guide/NN-*.md` ↔ gyökér számozott fájlok (FIGYELEM: a Guides-oldali példányokon 🔜 tesztelői jelölés-réteg lehet — tartalmi merge kell, nem vak felülírás!); `docs/RESOURCE_PACK_CMD.md` ↔ gyökér; `docs/EPITESZ_UTMUTATO.md` ↔ gyökér; `docs/TEASER.md` ↔ gyökér; `docs/PITCH.md` ↔ gyökér; `docs/FEATURES.md` ↔ gyökér; `docs/LORE.md` + `docs/LORE_REFERENCE.md` ↔ `lore/`; `docs/ideas/*` ↔ `ideas/`; `docs/IDEAS.md` ↔ `ideas/README.md`. Mindkét repót ugyanarra a feature-branchre pushold.

## Mi ez a projekt
Folia-alapú Minecraft **1.21.11** Paper-plugin (Java **21**), MMO-jellegű SMP-rendszerekkel: **13 frakció-független kaszt + 35 specializáció (~390 spell)**, hibrid kaszt-erőforrás, 4 frakció (passzívokkal, király/raid/szezon), szakmák + recept-katalógus, talentek, tárgy-raritás + loot, questek + közösségi célok, dinamikus árfolyamú gazdaság (bank/piac/aukció), relikviák + rituálé-oltárok, claimek, territórium-zónák, pet-rendszer és világesemények. A kód ~298 Java-fájl / ~62 manager. Minden játékos-szöveg **magyar**.

## Architektúra (nagy kép)
- **Belépési pontok** (`paper-plugin.yml`): `IceSMP` + `IceSMPBootstrap` + `IceSMPLoader`. A tényleges élet a `core/IceSMPCore`-ban van: konstruktorban épül fel az ÖSSZES manager (kézi DI, sorrend számít), majd `enable()`: `load()` a `persistentStores` listán → listener-regisztráció → parancs-regisztráció (kódból, nem manifestből!) → schedulerek; `disable()`: `save()` + cleanup.
- **Manager-réteg** (`managers/`): egy manager = egy rendszer állapota + perzisztenciája. Perzisztencia YAML-ba (`YamlStore.saveAtomic`, a `PersistentStore` interfészen át) vagy player-PDC-be. Per-player volatilis állapotot a `PlayerSessionCleanupListener`-ben kell takarítani (UUID-kulcsú map nem szivároghat).
- **GUI-minta** (`gui/` + `listeners/*GUIListener`): statikus GUI-osztály építi az inventoryt egy dedikált `InventoryHolder`-rel; a holder hordozza az owner-UUID-t és a slot→akció kötéseket; a listener a holder típusára szűr, mindent cancel-el, és drag+close eventet is kezel. A `/menu` hub (`CommandMenus` + `CommandMenuHolder`) akció-stringekkel dolgozik: `MENU:<almenü>` / `RUN:<parancs, menü-refresh-sel>` / `OPEN:<saját GUI-t nyitó parancs>` / `CLOSE` — a gameplay-logika MINDIG a parancsokban marad, a menü csak delegál. Admin-gombot mindig a mögöttes parancs tényleges jogosultság-node-jára kapuzz.
- **Parancsok** (`commands/`): Paper Brigadier `BasicCommand`-ok; nagyobb domainekhez router+subcommand felosztás (`commands/job|faction|currency|bank`), egyébként egy vékony osztály. Mindig adj tab-complete-et. Regisztráció: `IceSMPCore.registerCommands()`.
- **Config**: `src/main/resources/config/*.yml` — a `ConfigManager` az összeset egybefésüli (ÚJ fájlt a `CONFIG_FILES` listába is fel KELL venni!); üzenetek a `MessageManager`-en át (`messages.yml` kulcsok inline defaultokkal).
- **Élő-config konvenció**: MINDEN új kulcsot híváskor olvass (`configManager.getX` a use-site-on, ne konstruktorban) → `/icesmp reload` restart nélkül él. Ingame felülbírálás: `/icesmp config get|set|unset|list|find|menu` + kattintható GUI (`ConfigMenuGUI`, jog: `icesmp.admin.config`) — az írás EGYETLEN útja a szerializált `ConfigManager.applyOverride` (data-folder config.yml, utolsóként merge-ölve). Boolean-kulcsok egyértelműek: `allow-<szabály>` séma (true=SZABAD), legacy invertált kulcsok fallbackként olvasva.
- **Esemény-spawnok**: minden világesemény-spawn az `EventSpawnGuard`-on megy át (`isBlocked(eventKey, loc)` — territory/claim/WG per `world-events.spawn-rules.<event>` mátrix; `isUnsafeSurface`; statikus `prepare(Mob)` zombisodás/nappali égés ellen). Új eseménynél új mátrix-sor + guard-hívás kötelező.
- **Admin item-adás**: `/iceitem <unique|recept|relikvia|tervrajz> <id> [darab] [játékos]` (`ItemGiveCommand`, jog: `icesmp.admin.item`) — a recept-út a `ProfessionRecipeBookListener.buildResult` teljes stamp-láncát használja.
- **Itemek**: PDC-tagekkel (IDENTITÁS: signature_item, unique-id — ez marad) az `items/*ItemFactory`
  osztályokban, craft-safety listenerekkel védve. **Viselkedés/megjelenés = data-component** (1.20.5+,
  `items/ItemDataFactory`): CONSUMABLE/FOOD (ételek-italok), később GLIDER/DEATH_PROTECTION/EQUIPPABLE/
  USE_COOLDOWN/TOOLTIP_DISPLAY, és a CMD→ITEM_MODEL átállás. **KRITIKUS sorrend-invariáns:** a
  `itemStack.setData(...)` UTÁN a `setItemMeta(...)` TÖRLI a komponenst (a meta-round-trip nem hordozza)
  — a data-komponenseket MINDIG a meta-műveletek UTÁN, utolsóként kell alkalmazni (a `buildResult` a végén hívja).
- **Spellek**: deklaratív `ConfiguredSpell.builder(...)` a `spells/SpellCatalog`-ban + unlock a `config/classes.yml`-ben; külön osztály csak valóban állapotos spellnek (kötelező `clearPlayerState` override).
- **Soft-depend integrációk** (`integration/`): PlaceholderAPI, LibsDisguises, FancyNpcs, WorldGuard, LuckPerms — mind reflexiós híd, a plugin nélkülük is fut. Build-oldalon csak `compileOnly` (lásd `build.gradle.kts`).

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
- **MD-politika:** új .md fájl CSAK kifejezett tulaj-kérésre születhet. Auditok a meglévő
  `docs/ideas/P2-gameplay-audit.md`-t frissítik HELYBEN (nem új fájl körönként); ötletek
  egyetlen helyre mennek: `docs/ideas/BACKLOG.md`. A `PLAYER_GUIDE.md` csak index — az
  egyetlen igazságforrás a `docs/player-guide/` oldalak.
- **Definition of Done — minden változás UGYANABBAN a commitban propagál:**
  - új parancs → tab-complete + `/menu` csempe (CommandMenus) + `14-parancsok.md` + jog-node
  - új jog-node → `Permissions.java` canonical map (különben az admin.all nem adja meg!)
  - új config-kulcs → use-site olvasás (élő-config) + ha admin-hangolandó: ConfigMenuGUI
  - új custom item → **ITEM_MODEL** (modern; `item-model:`/`ItemDataFactory.applyItemModel`) +
    `docs/RESOURCE_PACK_CMD.md` „ITEM_MODEL tárgyak" szekció-sor. (A régi CMD-sávos itemek migrálásig
    maradnak; ÚJ itemnél már NEM adunk integer CMD-t.)
  - új quest-NPC / territory-id → a P2-audit világépítő-checklistjére is fel kell kerülnie
  - új rendszer/mechanika → érintett `docs/player-guide/` oldal + PLAYTEST-blokk +
    `LORE_REFERENCE.md` sor (ha lore-kötött) + README feature-lista, ha ott is szerepel
  - minden doksi-szám a configból származik, nem fejből
  - záráskor: fordítás-ellenőrzés + `scripts/check_consistency.py` + tükör-push
- Játékos-szöveg magyarul, `MessageManager` + `messages.yml` kulccsal és inline defaulttal.
- **Item-megjelenés szabálya:** ÚJ custom/unique item **ITEM_MODEL** komponenst kap (string
  modell-id az `icesmp:` névtérben; `result.item-model:` a receptben / `ItemDataFactory.applyItemModel`),
  és fel KELL venni a `docs/RESOURCE_PACK_CMD.md` „ITEM_MODEL tárgyak" szekciójába (a resource pack
  készítő ebből dolgozik). A **régi integer-CMD** itemek a migrálásig maradnak, sávjaik:
  1001+ pénz, 4101/4201+ relikvia, 5201+ katalizátor, 5301+ pet-eszköz, 5401 ostromgép, 5410+ kijelölő-pálca,
  6000+ unique anyag, 6201+ kulcs, 6210 tervrajz, 6300+ recept-tárgy, 6450+ bolt-különlegesség,
  6460+ loot-nevesített. (CMD→ITEM_MODEL teljes migráció: P7 alatt, egyeztetve a pack-készítővel.)
- Minden gameplay-változásnál frissítsd a docsot: a megfelelő `docs/player-guide/` oldal (a `PLAYER_GUIDE.md` csak index, nem kell bővíteni), `PLAYTEST.md` checklist, feature-listánál `README.md`. A számszerű állítások egyezzenek a config-fájlokkal.
- Commit-üzenet: magyar, tömör tárgysor + felsorolásos törzs; a repo-ban használt `Co-Authored-By` + `Claude-Session` trailerekkel zárul. Csak a kijelölt feature-branchre pusholj.
- Részletes projekt-tudás: `AGENTS.md` (domain-számok, spell-költség hibrid, HUD, faction-passzívák), `docs/ARCHITECTURE.md` (technikai referencia), `ROADMAP.md` (nyitott munkák).
- **Lore/tartalom-referencia:** `docs/LORE.md` — a kanonikus kódex, TISZTA világon-belüli szöveg (ne
  írj bele mechanika-jegyzetet, táblát, config-kulcsot!). A technikai megfeleltetés a
  `docs/LORE_REFERENCE.md`-ben él: frakció↔kód (`RED`=Perinfernicitas, `BLUE`=Cryghaliris,
  `NEUTRAL`=Ryanora/Caldestera, `DARK`=Kitaszítottak), lore-elem→mechanika tábla, unique-item
  tervkatalógus, elnevezési irányelvek. Minden új tartalom (item-nevek, ételek, valuta, mob-drop,
  zóna, quest) a kódexhez illeszkedjen — a kód-kötést a referencia-fájlban vezesd.
