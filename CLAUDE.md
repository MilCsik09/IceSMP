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
- Sandboxban, ahol a Gradle nem éri el a repókat: `javac`-kal fordíts a cache-elt szerver-libek ellen (`run/libraries`), vagy ha az sincs, `javac -sourcepath src/main/java` futtatással szűrd ki, hogy minden hiba külső függőségből jön-e (Bukkit/Adventure „cannot find symbol" elfogadható, minden más nem; ismert baseline-műtermék: 2×"does not override" a gui/*Holder-ben). **Push előtt mindig fordítás-ellenőrzés.**
- Sandbox-buktatók: a magyar „idézőjelek" Java- ÉS Python-stringben is törnek (Java-ban ” zárójelet használj, Pythonban aposztrófos stringet); a cwd Bash-hívások közt visszaáll (`cd /home/user/IceSMP` mindig); minden YAML-szerkesztés után `python3 -c "import yaml; yaml.safe_load(...)"` ellenőrzés.
- **Tükör-repo (IceSMPGuides):** a docs egy része a `MilCsik09/IceSMPGuides` repóba is átmásolandó minden változtatásnál. Térkép: `PLAYTEST.md` ↔ gyökér; `docs/player-guide/NN-*.md` ↔ gyökér számozott fájlok (FIGYELEM: a Guides-oldali példányokon 🔜 tesztelői jelölés-réteg lehet — tartalmi merge kell, nem vak felülírás!); `docs/RESOURCE_PACK_CMD.md` ↔ gyökér; `docs/LORE.md` + `docs/LORE_REFERENCE.md` ↔ `lore/`; `docs/ideas/*` ↔ `ideas/`; `docs/IDEAS.md` ↔ `ideas/README.md`. Mindkét repót ugyanarra a feature-branchre pushold.

## Mi ez a projekt
Folia-alapú Minecraft **1.21.11** Paper-plugin (Java **21**), MMO-jellegű SMP-rendszerekkel: **13 frakció-független kaszt + 31 specializáció (~390 spell)**, hibrid kaszt-erőforrás, 4 frakció (passzívokkal, király/raid/szezon), szakmák + recept-katalógus, talentek, tárgy-raritás + loot, questek + közösségi célok, dinamikus árfolyamú gazdaság (bank/piac/aukció), relikviák + rituálé-oltárok, claimek, territórium-zónák, pet-rendszer és világesemények. A kód ~298 Java-fájl / ~62 manager. Minden játékos-szöveg **magyar**.

## Architektúra (nagy kép)
- **Belépési pontok** (`paper-plugin.yml`): `IceSMP` + `IceSMPBootstrap` + `IceSMPLoader`. A tényleges élet a `core/IceSMPCore`-ban van: konstruktorban épül fel az ÖSSZES manager (kézi DI, sorrend számít), majd `enable()`: `load()` a `persistentStores` listán → listener-regisztráció → parancs-regisztráció (kódból, nem manifestből!) → schedulerek; `disable()`: `save()` + cleanup.
- **Manager-réteg** (`managers/`): egy manager = egy rendszer állapota + perzisztenciája. Perzisztencia YAML-ba (`YamlStore.saveAtomic`, a `PersistentStore` interfészen át) vagy player-PDC-be. Per-player volatilis állapotot a `PlayerSessionCleanupListener`-ben kell takarítani (UUID-kulcsú map nem szivároghat).
- **GUI-minta** (`gui/` + `listeners/*GUIListener`): statikus GUI-osztály építi az inventoryt egy dedikált `InventoryHolder`-rel; a holder hordozza az owner-UUID-t és a slot→akció kötéseket; a listener a holder típusára szűr, mindent cancel-el, és drag+close eventet is kezel. A `/menu` hub (`CommandMenus` + `CommandMenuHolder`) akció-stringekkel dolgozik: `MENU:<almenü>` / `RUN:<parancs, menü-refresh-sel>` / `OPEN:<saját GUI-t nyitó parancs>` / `CLOSE` — a gameplay-logika MINDIG a parancsokban marad, a menü csak delegál. Admin-gombot mindig a mögöttes parancs tényleges jogosultság-node-jára kapuzz.
- **Parancsok** (`commands/`): Paper Brigadier `BasicCommand`-ok; nagyobb domainekhez router+subcommand felosztás (`commands/job|faction|currency|bank`), egyébként egy vékony osztály. Mindig adj tab-complete-et. Regisztráció: `IceSMPCore.registerCommands()`.
- **Config**: `src/main/resources/config/*.yml` — a `ConfigManager` az összeset egybefésüli (ÚJ fájlt a `CONFIG_FILES` listába is fel KELL venni!); üzenetek a `MessageManager`-en át (`messages.yml` kulcsok inline defaultokkal).
- **Élő-config konvenció**: MINDEN új kulcsot híváskor olvass (`configManager.getX` a use-site-on, ne konstruktorban) → `/icesmp reload` restart nélkül él. Ingame felülbírálás: `/icesmp config get|set|unset|list|find|menu` + kattintható GUI (`ConfigMenuGUI`, jog: `icesmp.admin.config`) — az írás EGYETLEN útja a szerializált `ConfigManager.applyOverride` (data-folder config.yml, utolsóként merge-ölve). Boolean-kulcsok egyértelműek: `allow-<szabály>` séma (true=SZABAD), legacy invertált kulcsok fallbackként olvasva.
- **Esemény-spawnok**: minden világesemény-spawn az `EventSpawnGuard`-on megy át (`isBlocked(eventKey, loc)` — territory/claim/WG per `world-events.spawn-rules.<event>` mátrix; `isUnsafeSurface`; statikus `prepare(Mob)` zombisodás/nappali égés ellen). Új eseménynél új mátrix-sor + guard-hívás kötelező.
- **Admin item-adás**: `/iceitem <unique|recept|relikvia|tervrajz> <id> [darab] [játékos]` (`ItemGiveCommand`, jog: `icesmp.admin.item`) — a recept-út a `ProfessionRecipeBookListener.buildResult` teljes stamp-láncát használja.
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
- **CMD-szabály:** minden új custom/unique item CustomModelData-t kap, és fel KELL venni a
  `docs/RESOURCE_PACK_CMD.md` regiszterbe (a resource pack készítő ebből dolgozik). Sávok:
  1001+ pénz, 4101/4201+ relikvia, 5201+ katalizátor, 5301+ pet-eszköz, 5401 ostromgép, 5410+ kijelölő-pálca,
  6000+ unique anyag, 6201+ kulcs, 6210 tervrajz, 6300+ recept-tárgy, 6450+ bolt-különlegesség,
  6460+ loot-nevesített.
- Minden gameplay-változásnál frissítsd a docsot: `PLAYER_GUIDE.md` (+ a megfelelő `docs/player-guide/` oldal), `PLAYTEST.md` checklist, feature-listánál `README.md`. A számszerű állítások egyezzenek a config-fájlokkal.
- Commit-üzenet: magyar, tömör tárgysor + felsorolásos törzs; a repo-ban használt `Co-Authored-By` + `Claude-Session` trailerekkel zárul. Csak a kijelölt feature-branchre pusholj.
- Részletes projekt-tudás: `AGENTS.md` (domain-számok, spell-költség hibrid, HUD, faction-passzívák), `docs/ARCHITECTURE.md` (technikai referencia), `ROADMAP.md` (nyitott munkák).
- **Lore/tartalom-referencia:** `docs/LORE.md` — a kanonikus kódex, TISZTA világon-belüli szöveg (ne
  írj bele mechanika-jegyzetet, táblát, config-kulcsot!). A technikai megfeleltetés a
  `docs/LORE_REFERENCE.md`-ben él: frakció↔kód (`RED`=Perinfernicitas, `BLUE`=Cryghaliris,
  `NEUTRAL`=Ryanora/Caldestera, `DARK`=Kitaszítottak), lore-elem→mechanika tábla, unique-item
  tervkatalógus, elnevezési irányelvek. Minden új tartalom (item-nevek, ételek, valuta, mob-drop,
  zóna, quest) a kódexhez illeszkedjen — a kód-kötést a referencia-fájlban vezesd.
