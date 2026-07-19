# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Munkaszervezés (a repo tulajdonosának kérése)
- A feladatokat alapértelmezésben **delegáld gyengébb/olcsóbb subagenteknek** (Agent tool): `haiku` — keresés, összegzés, mechanikus szerkesztés; `sonnet` — kód-review, körülhatárolt implementáció.
- Ügyelj rá, hogy a feladat **ne haladja meg a delegált agent tudását**; ami a legerősebb modellt igényli (architektúra-döntés, Folia-konkurrencia, kényes refaktor), azt tartsd magadnál, ahogy a subagent-eredmények ellenőrzését és integrálását is.
- **Légy token-takarékos**: tömör válaszok, célzott fájlolvasás, ne duplikáld a subagent munkáját.

## Build & verify
```bash
./gradlew build      # plugin jar -> build/libs
./gradlew runServer  # helyi tesztszerver (run/ könyvtár, 1.21.11)
```
- Nincs teszt-suite; az ellenőrzés = hibátlan fordítás + kézi playtest (`PLAYTEST.md`).
- Sandboxban, ahol a Gradle nem éri el a repókat: `javac`-kal fordíts a cache-elt szerver-libek ellen (`run/libraries`), vagy ha az sincs, `javac -sourcepath src/main/java` futtatással szűrd ki, hogy minden hiba külső függőségből jön-e (Bukkit/Adventure „cannot find symbol" elfogadható, minden más nem). **Push előtt mindig fordítás-ellenőrzés.**

## Mi ez a projekt
Folia-alapú Minecraft **1.21.11** Paper-plugin (Java **21**), MMO-jellegű SMP-rendszerekkel: **13 frakció-független kaszt + 31 specializáció (~390 spell)**, hibrid kaszt-erőforrás, 4 frakció (passzívokkal, király/raid/szezon), szakmák + recept-katalógus, talentek, tárgy-raritás + loot, questek + közösségi célok, dinamikus árfolyamú gazdaság (bank/piac/aukció), relikviák + rituálé-oltárok, claimek, territórium-zónák, pet-rendszer és világesemények. A kód ~298 Java-fájl / ~62 manager. Minden játékos-szöveg **magyar**.

## Architektúra (nagy kép)
- **Belépési pontok** (`paper-plugin.yml`): `IceSMP` + `IceSMPBootstrap` + `IceSMPLoader`. A tényleges élet a `core/IceSMPCore`-ban van: konstruktorban épül fel az ÖSSZES manager (kézi DI, sorrend számít), majd `enable()`: `load()` a `persistentStores` listán → listener-regisztráció → parancs-regisztráció (kódból, nem manifestből!) → schedulerek; `disable()`: `save()` + cleanup.
- **Manager-réteg** (`managers/`): egy manager = egy rendszer állapota + perzisztenciája. Perzisztencia YAML-ba (`YamlStore.saveAtomic`, a `PersistentStore` interfészen át) vagy player-PDC-be. Per-player volatilis állapotot a `PlayerSessionCleanupListener`-ben kell takarítani (UUID-kulcsú map nem szivároghat).
- **GUI-minta** (`gui/` + `listeners/*GUIListener`): statikus GUI-osztály építi az inventoryt egy dedikált `InventoryHolder`-rel; a holder hordozza az owner-UUID-t és a slot→akció kötéseket; a listener a holder típusára szűr, mindent cancel-el, és drag+close eventet is kezel. A `/menu` hub (`CommandMenus` + `CommandMenuHolder`) akció-stringekkel dolgozik: `MENU:<almenü>` / `RUN:<parancs, menü-refresh-sel>` / `OPEN:<saját GUI-t nyitó parancs>` / `CLOSE` — a gameplay-logika MINDIG a parancsokban marad, a menü csak delegál. Admin-gombot mindig a mögöttes parancs tényleges jogosultság-node-jára kapuzz.
- **Parancsok** (`commands/`): Paper Brigadier `BasicCommand`-ok; nagyobb domainekhez router+subcommand felosztás (`commands/job|faction|currency|bank`), egyébként egy vékony osztály. Mindig adj tab-complete-et. Regisztráció: `IceSMPCore.registerCommands()`.
- **Config**: `src/main/resources/config/*.yml` — a `ConfigManager` az összeset egybefésüli; üzenetek a `MessageManager`-en át (`messages.yml` kulcsok inline defaultokkal).
- **Itemek**: PDC-tagekkel az `items/*ItemFactory` osztályokban, craft-safety listenerekkel védve.
- **Spellek**: deklaratív `ConfiguredSpell.builder(...)` a `spells/SpellCatalog`-ban + unlock a `config/classes.yml`-ben; külön osztály csak valóban állapotos spellnek (kötelező `clearPlayerState` override).
- **Soft-depend integrációk** (`integration/`): PlaceholderAPI, LibsDisguises, FancyNpcs, WorldGuard, LuckPerms — mind reflexiós híd, a plugin nélkülük is fut. Build-oldalon csak `compileOnly` (lásd `build.gradle.kts`).

## Folia-szabályok (KRITIKUS — részletek: `docs/ARCHITECTURE.md` §4)
- Soha `Bukkit.getScheduler()`; entitáshoz `entity.getScheduler()`, helyhez `getRegionScheduler()`, globálishoz `getGlobalRegionScheduler()`.
- Az eventek az esemény-entitás régió-szálán futnak. **MÁSIK entitás bármilyen érintése** (PDC/inventory olvasás, akár `sendMessage`) **scheduler-hoppal**: `target.getScheduler().run(plugin, task -> {...}, null)` — minta: kill-reward listenerek, MarketGUI seller-értesítés.
- Megosztott, több régió-szálról elért állapot: synchronized/concurrent szerkezet, vagy copy-on-write snapshot (minta: `QuestManager.customQuests`).
- Teleport: `teleportAsync`; blokk-szkennelés kicsi és régió-lokális legyen.

## Konvenciók
- Játékos-szöveg magyarul, `MessageManager` + `messages.yml` kulccsal és inline defaulttal.
- Minden gameplay-változásnál frissítsd a docsot: `PLAYER_GUIDE.md` (+ a megfelelő `docs/player-guide/` oldal), `PLAYTEST.md` checklist, feature-listánál `README.md`. A számszerű állítások egyezzenek a config-fájlokkal.
- Commit-üzenet: magyar, tömör tárgysor + felsorolásos törzs; a repo-ban használt `Co-Authored-By` + `Claude-Session` trailerekkel zárul. Csak a kijelölt feature-branchre pusholj.
- Részletes projekt-tudás: `AGENTS.md` (domain-számok, spell-költség hibrid, HUD, faction-passzívák), `docs/ARCHITECTURE.md` (technikai referencia), `ROADMAP.md` (nyitott munkák).
- **Lore/tartalom-referencia:** `docs/LORE.md` — a kanonikus világ-történet + a frakció↔kód megfeleltetés (`RED`=Perinfernicitas, `BLUE`=Cryghaliris, `NEUTRAL`=Ryanora/Caldestera, `DARK`=Suttogók) + unique-item katalógus. Minden új tartalom (item-nevek, ételek, valuta, mob-drop, zóna, quest) ehhez illeszkedjen.
