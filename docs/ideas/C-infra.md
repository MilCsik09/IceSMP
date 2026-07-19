# C) Admin / infra ötletek

[← Ötlettár-index](../IDEAS.md)

Jelölés: **Munka** (🟢 kicsi / 🟡 közepes / 🔴 nagy) • **Érték** (⭐–⭐⭐⭐) • `[TOP]` = ajánlott következő kör.

---

### C1. Spell-használati statisztika (balansz-adat) `[TOP]`
🟢 • ⭐⭐⭐

**Mi ez:** Számláló, ami rögzíti, melyik spellt hányszor castolják sikeresen, hogy a balansz-döntések adatra épüljenek, ne csak érzésre.
**Hogyan működne:** Egy könnyű `SpellUsageManager` UUID-független `Map<String, Long>` számlálót vezet spell-id szerint, amit az `AbilityCatalystListener` a sikeres cast UTÁN (a `usesResource`/`consumeCost` út után, hogy csak a ténylegesen elsült castok számítsanak) inkrementál. Éjfélkor (a meglévő globális napi tick mellé) egy YAML-dump kerül `data/spell-usage/yyyy-MM-dd.yml` alá `YamlStore.saveAtomic`-kal, a memória-számláló nullázódik. `/icesmp balance report [nap]` (node `icesmp.admin.balance`) táblázatos chat-kimenetet ad: spell-id, kaszt, castszám, castszám/aktív-játékos. A számlálónak `ConcurrentHashMap`-nek kell lennie, mert több régió-szálról érkezik increment.
**Miért jó:** A következő balansz-kör innentől nem vakon tippel — a soha nem castolt spellek és a mindent lenyomó overperformerek egy táblázatból látszanak, a `spells-balance.yml` hangolás célzottá válik.
**Építőkövek:** `AbilityCatalystListener` cast-pipeline, `YamlStore.saveAtomic`, `IceSMPCore` globális napi tick minta, `IceSMPCommand` dispatch (`icesmp balance report`).
**Buktatók:** A számláló csak a TÉNYLEGESEN elsült castokat vegye (a no-op `executeSpell()==false` eseteket ne), különben a cooldown-blokkolt spam felfújja a számokat.

### C2. Playtest-mód kapcsoló
🟢 • ⭐⭐

**Mi ez:** Admin-kapcsoló, ami playtest alatt globálisan lerövidíti a cooldownokat és idő-kapukat, hogy ne kelljen kézzel átírni a configokat és utána visszaállítani.
**Hogyan működne:** `/icesmp playtest on|off` (node `icesmp.admin.playtest`) egy nem-perzisztens boolean flaget állít (restart alaphelyzetbe áll, hogy élesen ne maradhasson bekapcsolva). Bekapcsolt állapotban egy `PlaytestOverrideService.scale(long baseCooldownMillis)` metódus — amit a `ResourceManager`/`BaseSpell` cooldown-olvasás hív a nyers érték helyett — 10%-ra skálázza a cooldownokat és idő-kapukat (a raid-ablakot/napi-reset típusú kapukat kizárva). Bekapcsolt állapotban a HUD sarkában (vagy actionbaren) állandó „⚠ PLAYTEST MÓD” jelzés fut mindenkinek.
**Miért jó:** A PLAYTEST.md checklist ma kézi config-átírást ír elő minden körhöz — ez a lépés eltűnik, és a bekapcsolt állapot nem maradhat észrevétlenül élesben.
**Építőkövek:** `ConfigManager` réteg (a skálázás a config felett ül, nem helyettesíti), `BaseSpell.balance()`, `HudManager` sidebar-sor.
**Buktatók:** A skálázást NEM a `spells-balance.yml`-be kell írni (az restartot igényelne) — külön runtime-multiplikátor kell minden cooldown-olvasás felett.

### C3. Gazdasági faucet/sink monitor
🟢 • ⭐⭐

**Mi ez:** Heti összesítő arról, mennyi pénz keletkezett és semmisült meg forrásonként, hogy az infláció-gyanú azonnal forráshoz köthető legyen.
**Hogyan működne:** A `CurrencyManager` minden pénzmozgó hívásánál (kill-reward, quest-jutalom, piaci tranzakciós díj, claim-ár, bank-kamat) egy `Map<String forrás, Long delta>` napi számláló frissül — a C1 spell-számlálóéval azonos minta, csak a meglévő manager-metódusok belsejébe kötve, nem külön listenerrel. Vasárnap éjfélkor YAML-dump (`data/economy-report/yyyy-Www.yml`) + `/icesmp economy report` chat-táblázat: forrás, összeg, faucet/sink irány, nettó egyenleg.
**Miért jó:** A „nincs addolt pénz” elv őre — ha egy forrás hirtelen tízszeres faucetet nyit, a heti riportból egy pillantás alatt kiderül, melyik csap folyik, mielőtt a piac összeomlana.
**Építőkövek:** `CurrencyManager`, `YamlStore.saveAtomic`, `IceSMPCore` heti tick minta (a szezon-scheduler mellett).
**Buktatók:** Minden pénzmozgó hívási pontot meg kell találni és becsatornázni — ha egy manager közvetlenül ír egyenleget a monitor mellett, a riport hazudik.

### C4. Kill-reward / gazdaság szimulátor-parancs
🟡 • ⭐

**Mi ez:** Durva becslés parancsa, mennyi pénz/XP termelődne N óra átlagos játékkal a jelenlegi configon — gyors balansz-sanity-check.
**Hogyan működne:** `/icesmp simulate <óra>` (node `icesmp.admin.simulate`) a configból kiolvasott átlagos kill-reward/quest-jutalom/szakma-XP rátákkal és egy configolható „átlagos aktivitás” profillal (N kill/óra, N quest/nap) egyszerű szorzást futtat — nem valós eseményeket szimulál, csak számol. Kimenet: becsült pénz/óra, XP/óra, bontva forrásonként. Ha a C3 monitor fut, a becslés összevethető a TÉNYLEGES heti adatokkal (várt vs. mért eltérés-sor).
**Miért jó:** Egy config-változtatás (pl. kill-reward szorzó) hatását újraindítás és órás playtest nélkül, azonnal lehet látni — gyors szanity-check balansz-döntés előtt.
**Építőkövek:** `ConfigManager` (kill-reward/quest-jutalom kulcsok), opcionálisan C3 a mért adatok összevetéséhez.
**Buktatók:** Csak becslés — a chat-kimenetben egyértelműen jelezni kell, hogy modellezett érték, nem mérés, különben félrevezetően pontosnak tűnhet.

### C5. Discord-webhook híd
🟢 • ⭐⭐⭐

**Mi ez:** A nagy szerver-események (világboss, raid-indítás, szezonzárás, király-választás, lottó/krónika) kimennek egy configolható Discord-webhookra is, plugin-függőség nélkül.
**Hogyan működne:** Egy vékony `DiscordWebhookService` a `config/integrations.yml` `discord.webhook-url` + `discord.events.<esemény>: true/false` kulcsait olvassa; a meglévő broadcast-hívó pontokon (világboss-spawn, raid-start, szezon-lezárás, király-koronázás, B25 lottó, B15 krónika) egy plain HTTP POST megy JSON body-val `java.net.http.HttpClient`-tel. **Folia-szabály: kizárólag `Bukkit.getAsyncScheduler()`-ről** (soha a hívó régió-szálról — a HTTP-hívás blokkolna), a hívó oldal csak összerakja a szöveget és eldobja a taskot. Hibát (timeout, 4xx/5xx) csak konzolra logol, sose dob vissza a gameplay-útvonalra.
**Miért jó:** A szerver élete kilátszik a Discordra offline közösségi tagoknak is — retention-eszköz gyakorlatilag ingyen, mert a broadcast-szövegek már léteznek, csak egy második kimenetet kapnak.
**Építőkövek:** `Bukkit.getAsyncScheduler()` (IO-ra, a `CurrencyManager` debounce-mentés mintája), `ConfigManager` új `integrations.yml` fájl, meglévő broadcast-hívási pontok.
**Buktatók:** Soha ne hívd szinkron a régió-szálról (freeze); a webhook-URL secret — ne kerüljön plain textben a C7 audit-logba/mentésekbe, ha elkerülhető.

### C6. YAML-store integritás-őr + mentés
🟡 • ⭐⭐

**Mi ez:** Induláskori parse-próba minden `PersistentStore`-fájlra, sérülésnél automatikus visszaállítás egy rotálódó biztonsági mentésből.
**Hogyan működne:** `IceSMPCore.enable()`-ben, a `persistentStores.load()` iteráció ELŐTT egy `YamlIntegrityGuard.checkAndRestore(file)` fut minden ismert store-fájlra: betöltési próba, hiba esetén a legutóbbi `.bak1/.bak2/.bak3` visszamásolása időbélyeg szerint + konzol-figyelmeztetés. A `YamlStore.saveAtomic` sikeres írás UTÁN rotálja a backupokat (`.bak2→.bak3`, `.bak1→.bak2`, friss mentés → `.bak1`) — minden store 3 generációs biztosítást kap ingyen, a hívó managereknek nem kell tudniuk róla.
**Miért jó:** Egy rossz kézi YAML-szerkesztés vagy lemez-hiba ma potenciális adatvesztés (frakció-kassza, claimek, relikviák) — ez a réteg konzol-figyelmeztetéssel megússza, ahelyett hogy a szerver üres store-ral indulna.
**Építőkövek:** `storage/YamlStore.saveAtomic`, `storage/PersistentStore` SPI, `IceSMPCore` `persistentStores` lista.
**Buktatók:** A rotáció extra lemez-írás minden mentésnél (17 store × 3 backup) — nagy fájloknál figyelni kell a terhelésre; a visszaállítást mindig logolni kell, hogy ne tűnjön el észrevétlenül adat.

### C7. Admin audit-log
🟢 • ⭐⭐

**Mi ez:** Minden admin-parancs végrehajtása egy külön naplófájlba kerül, hogy több adminos szerveren visszakövethető legyen, ki mit állított át.
**Hogyan működne:** A `core/Permissions` kanonikus `icesmp.admin.*` node-jai már egy helyen felsorolják az admin-parancsokat — egy `AdminAuditListener` a Paper `CommandExecuteEvent`-et figyeli, és ha a parancs egy `icesmp.admin.` prefixű node-ot igényel, egy sort ír `logs/admin-audit.log`-ba (sima fájl-append, nem `YamlStore`, mert csak-hozzáfűzés): időbélyeg, végrehajtó, teljes parancs-string, cél-játékos (ha kiolvasható). Napi rotáció (`admin-audit-yyyy-MM-dd.log`), régi fájlok N nap után konfigból törölhetők.
**Miért jó:** A „ki állította ezt át?” kérdés (item-adás, config-set, event-force-start, quest-admin-reset) ma csak a memóriában él — a napló egy perc alatt válaszol.
**Építőkövek:** `core/Permissions` kanonikus node-lista, Paper `CommandExecuteEvent`, sima fájl-append.
**Buktatók:** A `CommandExecuteEvent` a parancs FUTÁSA előtt tüzel — jogosultság-elutasításnál hamis pozitívot írhat; ezt válasz-kódból vagy utólagos siker-flaggel érdemes szűrni.

### C8. Edzőbábu (training dummy)
🟢 • ⭐⭐⭐

**Mi ez:** Sebezhetetlen, magától gyógyuló céltábla-entitás, ami visszajelzi a rádobott DPS-t és összesített sebzést — spell-balansz teszteléshez és rotáció-gyakorláshoz.
**Hogyan működne:** `/icesmp dummy spawn` (node `icesmp.admin.dummy`) egy PDC-taggel jelölt, `EntityDamageEvent`-ben cancel-elt (sebezhetetlen) entitást rak le a hívó elé. Egy handler a bábura érkező, tényleges (buffolt/spell-módosított) sebzést egy per-attacker `Map<UUID, DummyStats>`-be gyűjti (összesített sebzés, találatszám, elapsed idő), az élő DPS-t action-bar mutatja vissza (`attacker.getScheduler().run(...)`, mert a bábu és a támadó külön régióban lehet). `/icesmp dummy reset` nullázza, `/icesmp dummy remove` despawnol.
**Miért jó:** A spell-balansz teszteléséhez ma élő mobot kell ölni, ami zajos mérés — egy fix célpont tiszta, ismételhető DPS-számot ad, és a játékosoknak is rotáció-gyakorló eszköz.
**Építőkövek:** `EntityDamageByEntityEvent` (a `DamageIndicator`-hoz hasonló sebzés-olvasás), entitás-scheduler hop (Folia), PDC-tag a bábu azonosítására.
**Buktatók:** A bábu ne generáljon droppot/XP-t/kill-crediteket; több egyidejű bábunál a mérést bábu-id szerint is particionálni kell, különben összemosódnak.

### C9. Folia régió-teljesítmény riport
🟡 • ⭐

**Mi ez:** Régiónkénti TPS/MSPT lista a legterheltebb chunk-koordinátákkal, mert Folián a globális TPS semmitmondó.
**Hogyan működne:** `/icesmp perf` (node `icesmp.admin.perf`) a Folia régiónkénti tick-adatait olvassa ki (régió-szálanként külön MSPT), és a legterheltebb N régiót chat-táblázatban írja ki: közelítő világ/chunk-koordináta, MSPT, entitás-szám a régióban. Csak on-demand fut (parancs-hívásra), nem folyamatos tick, hogy maga a riport ne legyen terhelés.
**Miért jó:** Egy globális „20 TPS” szám Folián hazudik, ha csak egy régió akad — ez a parancs megmondja, HOL keresendő a lag-forrás; a C20 hopper/redstone-figyelővel párban a leggyakoribb lag-panaszt fedi le.
**Építőkövek:** Folia régió-scheduler API (csak olvasás, nincs entitás-mutáció), meglévő `/icesmp` dispatch keret.
**Buktatók:** A pontos per-régió MSPT lekérdezés API-ja Folia-verziónként változhat — stabil hivatalos API hiányában entitás-sűrűség + chunk-betöltöttség közelítő proxy-metrikával kell dolgozni.

### C10. Config-diff parancs
🟢 • ⭐

**Mi ez:** Az élő configban a jar-beli defaultoktól eltérő kulcsok listája, hogy frissítéskor/hibakeresésnél azonnal látszódjon, mi van felülbírálva.
**Hogyan működne:** `/icesmp config diff` (a meglévő `config` alparancs-csoport bővítése) a `ConfigManager.CONFIG_FILES` minden fájljához betölti a JAR-ban csomagolt eredeti YAML-t, és rekurzívan összeveti az élő, betöltött `FileConfiguration`-nel — minden eltérő kulcsot egy sorban ír ki: `kulcs: default → élő érték`. Ugyanazt a kulcs-bejárást használja, mint a `ConfigValidator` (a C25 bővítés alapja is), csak érték-összevetésre, nem konvenció-ellenőrzésre.
**Miért jó:** Verzió-frissítéskor és support-kérdéseknél egy pillantásból látszik, mi az admin szándékos felülbírálása — az ingame config-rendszer (`/icesmp config get/set/unset`) természetes párja.
**Építőkövek:** `ConfigManager` két rétege (jar-default + `config.yml` override), `ConfigValidator` kulcs-bejárási mintája, `IceSMPCommand` config alparancs-csoport.
**Buktatók:** A JAR-beli default beolvasásához a plugin resource-streamjét kell újra megnyitni, nem a lemezen lévő (esetleg már felülírt) fájlt — óvatosan kell bánni a classloaderrel.

### C11. Custom item-katalógus parancs
🟢 • ⭐

**Mi ez:** Az összes `ItemFactory`-item listája egy parancsban, kattintható „adj egyet” admin-gombbal, hogy ne kelljen item-adáshoz a forráskódban keresgélni.
**Hogyan működne:** `/icesmp items [szűrő]` (node `icesmp.admin.items`) egy központi `ItemCatalogRegistry`-t jár be — minden `items/*ItemFactory` osztály egy `describe()`-szerű metódussal (id, megjelenő név, PDC-tag) regisztrálja magát (a `SpellRegistry` mintájára). A parancs chat-komponensben listázza a találatokat, minden sor kattintható `[Adj egyet]` gombbal, ami `ClickEvent.RUN_COMMAND`-dal `/icesmp give <célnév> <item-id>`-t indít (a `/menu` RUN:-akcióinak chat-kontextusú megfelelője).
**Miért jó:** Az item-adás ma forráskód-keresgélést igényel a megfelelő PDC-id megtalálásához — ez a parancs másodpercekre rövidíti, és kiküszöböli az elgépelést is.
**Építőkövek:** `items/*ItemFactory` osztályok, `SpellRegistry`-mintájú központi registry, `ClickEvent.RUN_COMMAND` (`/menu` RUN: filozófia chat-kontextusban).
**Buktatók:** Minden `ItemFactory`-t kézzel fel kell venni az új registry-be (nincs auto-discovery reflection nélkül) — érdemes ezt a bővítési recept-dokumentumba felvenni, hogy új itemnél ne felejtsék el.

### C12. Játékos-inspektor
🟡 • ⭐⭐

**Mi ez:** Egyetlen parancsban a játékos teljes plugin-állapota — kaszt, erőforrás, cooldownok, PDC-kulcsok, statok, claim, questek — support-diagnózishoz.
**Hogyan működne:** `/icesmp inspect <név>` (node `icesmp.admin.inspect`) a céljátékos ütemezőjére hoppol (Folia — más régióban lehet), és minden releváns managerből (`JobManager`, `SpecializationManager`, `ResourceManager`, `SinManager`, `StatsManager`, `ClaimManager`, `QuestManager`) egy-egy rövid gettert hív, majd összesített, szekciózott chat-kimenetet (vagy `CommandMenu`-alapú GUI-t) épít: kaszt/szint/spec, erőforrás jelenlegi/max, aktív `cd_*` cooldownok, statok, claim-összesítő, aktív questek objektíva-állással.
**Miért jó:** A „eltűnt a spellem!” / „miért nincs cooldownom?” support-kérdéseknél ma minden managert külön kell megkérdezni — ez egyben adja, a diagnózis percekről másodpercekre csökken.
**Építőkövek:** entitás-scheduler hop a célra (Folia-szabály), meglévő manager-getterek (nem új állapot, csak összefésülés), opcionálisan `CommandMenu` GUI-keret.
**Buktatók:** Sok manager egy hívásban — ha bármelyik getter fájl-IO-t végez, az egész parancs akadozhat; csak memóriabeli gettereket érdemes hívni.

### C13. Esemény-naptár config
🟡 • ⭐⭐

**Mi ez:** A világesemények véletlen időzítése mellé opcionális, cron-szerű menetrend, hogy a közösség tervezhessen.
**Hogyan működne:** Új `config/event-schedule.yml`: `schedule.<esemény-id>: ["SAT 20:00", "SUN 20:00"]` szintaxis. Egy `EventScheduleManager` globális, percenkénti tickje (`getGlobalRegionScheduler().runAtFixedRate`) ellenőrzi az egyezést, és találatnál a megfelelő esemény-manager (`BloodMoonManager`, `CaravanManager`, `WorldBossManager`…) meglévő force-start metódusát hívja — a véletlen-indítási logika VÁLTOZATLAN marad, a menetrend csak egy extra trigger-forrás. `schedule-only-mode: true` configgal a véletlen indítás eseményenként teljesen kikapcsolható.
**Miért jó:** A közösség ma nem tudja, mikor jön a vérhold vagy a karaván — kiszámítható menetrend tervezhető visszatérési okot ad, és a B30 raid-ablak természetes párja.
**Építőkövek:** meglévő esemény-managerek force-start belépési pontjai, `getGlobalRegionScheduler().runAtFixedRate`, `ConfigManager` új fájl.
**Buktatók:** Ha egy esemény már fut, amikor a menetrend újra triggerelne, a manager saját „already running” védelmére kell hagyatkozni — ezt minden bekötött managernél ellenőrizni kell.

### C14. Loot-szimulátor
🟢 • ⭐

**Mi ez:** N húzás szimulálása egy raritás/loot-táblából, eloszlás-kiírással, hogy drop-esély hangoláskor ne kelljen ölni ezret.
**Hogyan működne:** `/icesmp simulate loot <tábla-id> <N>` N-szer meghívja a `LootTable`/`ItemRarityService` már létező húzás-metódusát (ugyanazt, amit egy valódi drop-esemény hívna, csak tényleges item nélkül), és egy in-memory `Map<item/raritás, count>`-ba gyűjt. Kimenet: chat-táblázat item/raritás szerint, darabszám + mért% vs. konfigurált% eltérés-oszloppal.
**Miért jó:** A C4 gazdaság-szimulátor loot-oldali párja — egy drop-esély módosítás hatását valós ölés nélkül lehet ellenőrizni, a raritás-rendszer hangolását nagyságrendekkel gyorsítja.
**Építőkövek:** `managers/LootTable`, `ItemRarityService` (a húzás-metódus újrafelhasználva, nem duplikálva).
**Buktatók:** Ha a húzás-metódus mellékhatásos (statisztikát ír, eseményt dob), a szimulátornak dry-run flaget kell kapnia, különben a C1/C3-stílusú számlálókat is felfújja.

### C15. Napi szerver-digest
🟢 • ⭐⭐

**Mi ez:** Éjféli összefoglaló a logba (és opcionálisan Discordra) a napi aktivitásról, hogy a tulaj adatból vegye észre a trendeket.
**Hogyan működne:** A napi globális tick (a C1 spell-dump és a napi-quest reset melletti belépési pont) egy `DailyDigestManager`-t hív, ami a nap folyamán gyűjtött könnyű számlálókból (egyidejű játékos-csúcs, `IntroManager`-ből aznapi új játékosok száma, C3 napi gazdaság-egyenleg, lefutott világesemények, indított raidek) szöveges összefoglalót épít, `logs/digest/yyyy-MM-dd.log`-ba írja, majd C5 webhook esetén (async scheduler) Discordra is elküldi.
**Miért jó:** A tulaj reggeli kávéja mellé egy tömör „mi történt tegnap” — trendek (csökkenő aktivitás, elszabaduló gazdaság) korán észrevehetők log-turkálás nélkül.
**Építőkövek:** C1/C3 napi/heti számláló-minta, `IntroManager`, C5 Discord-híd (opcionális kimenet), `IceSMPCore` globális napi tick.
**Buktatók:** A digest csak annyira jó, amennyi adatot a mögötte lévő rendszerek (C1/C3) már gyűjtenek — ha azok nincsenek implementálva, a digest tartalma szegényes lesz.

### C16. YAML-séma verziózás (migrációs keret)
🟡 • ⭐

**Mi ez:** Minden `PersistentStore`-fájlba verzió-mező + induláskori upgrade-lánc, hogy egy formátum-változás ne igényeljen kézi migrálást.
**Hogyan működne:** Minden store gyökerébe `schema-version: N` kulcs kerül (hiányzó = legacy, alapból 1). `IceSMPCore.enable()`-ben, a `persistentStores.load()` ELŐTT (a C6 integritás-őrrel egy fázisban) egy `SchemaMigrationRunner` minden fájlnál a jelenlegi és a store-hoz regisztrált célverzió közti köztes migrációs lépéseket (`Migration1to2`, `Migration2to3`…) sorban lefuttatja, majd `YamlStore.saveAtomic`-kal visszaírja. A `PersistentStore` interfész egy opcionális `int schemaVersion()` metódussal bővül.
**Miért jó:** Egy jövőbeli formátum-változás (pl. claim-struktúra átalakítás) ma kézi, egyszeri szkriptet igényelne éles adaton — ez a keret tesztelhető, ismételhető lépés-lánccá teszi, amíg a technikai adósság még kicsi.
**Építőkövek:** `storage/PersistentStore` SPI, `storage/YamlStore.saveAtomic`, C6 integritás-őr (ugyanazon induláskori fázis).
**Buktatók:** A migrációs lépéseket véglegesen meg kell őrizni (egy régóta nem frissített szerver több lépést is átugorhat) — teszteletlen migráció éles adaton adatvesztést okozhat, ezért a C6 backup-rotáció előfeltétel.

### C17. Chat-szűrő + mute rendszer
🟢 • ⭐⭐

**Mi ez:** Konfigurálható kulcsszó-alapú chat-szűrő és admin-némítás, hogy a moderáció ne külön pluginra szoruljon.
**Hogyan működne:** `config/moderation.yml`: `chat-filter.blocked-words: [...]`, `chat-filter.action: WARN|BLOCK|MUTE` (ismétlődő sértésnél eszkaláció). Egy `AsyncChatEvent` listener minden üzenetet a szótár ellen ellenőriz normalizálva (ékezet/leetspeak-alap normalizálás), szóhatár-illesztéssel (nem substring); találatnál `BLOCK` = üzenet eldobása + figyelmeztetés, `MUTE` = X percre PDC-be írt `muted-until`, ami alatt a chat-esemény némán cancel-elődik. `/icesmp mute <név> <perc> [ok]` / `/icesmp unmute <név>` (node `icesmp.admin.moderation`) kézi némítás, a C7 audit-logba is beírva.
**Miért jó:** Egy MMO-jellegű SMP-n a toxikus chat retention-gyilkos — ez a legalapabb moderációs védőháló, ami nélkül ma minden sértést kézzel kell észrevenni.
**Építőkövek:** `AsyncChatEvent` (natív chat-formázó melletti listener), player-PDC (`muted-until`), C7 admin audit-log, `MessageManager`.
**Buktatók:** A szótár-szűrés hamis pozitívokat adhat substring-illesztéssel — szóhatár kell; a `MUTE` PDC-állapotot a `PlayerSessionCleanupListener`-ben explicit KI kell hagyni a takarításból (túl kell élje a kijelentkezést).

### C18. AFK-kezelés
🟢 • ⭐⭐

**Mi ez:** Inaktivitás-figyelés, ami AFK-jelzést tesz a tab-listre, és opcionálisan kizárja az AFK-játékost a kill-reward/farm-jellegű juttatásokból.
**Hogyan működne:** Egy `AfkManager` input-jellegű eseményeknél (mozgás, animáció, item-használat) frissíti a per-player `lastActivity` timestampet (UUID-kulcsos concurrent map). A globális, percenkénti tick minden online játékosra hoppol (`player.getScheduler().run`), és `now - lastActivity > afk-threshold-minutes` esetén AFK-flaget állít: `[AFK]` tab-list prefix (a `HudManager` tablist-színezés mellé), és a flag alatt a kill-reward/szakma-gyűjtés listenerek (config-kapcsolóval) kihagyják a jutalmat.
**Miért jó:** AFK-farmolás (macro-szerű grindelés) ma semmivel sem drágább, mint az aktív játék — ez egyszerre ad UX-jelzést (ki AFK a partiban) és opcionális gazdasági védőgátat.
**Építőkövek:** `HudManager` tablist-színezés mintája, entitás-scheduler hop (Folia — a globális tick minden ellenőrzésnél saját szálra ugrik), kill-reward listenerek.
**Buktatók:** Tisztán mozgás-alapú detekció AFK-fish-eket tévesen jelölhet — item-használat/animáció eseményeket is aktivitásnak kell számítani, nem csak `PlayerMoveEvent`-et.

### C19. Entitás-számláló riport
🟡 • ⭐

**Mi ez:** Chat-riport arról, mely chunkokban/entitás-típusokban torlódik a legtöbb élőlény, hogy a mob-alapú lag-forrás azonosítható legyen.
**Hogyan működne:** `/icesmp entities [világ]` (node `icesmp.admin.perf`) a betöltött chunkokat régiónként darabolva (`getRegionScheduler()`) bejárja, entitás-típusonként + chunk-klaszterenként összesít. Kimenet: top N legzsúfoltabb chunk-koordináta + domináns entitás-típus (pl. „mob-farm gyanú: 340 zombie egy 3x3 chunk-klaszterben”), és globális típus-eloszlás (item-entitás, mob, armor-stand, minion — a `MinionManager`-hez köthető).
**Miért jó:** A C9 régió-teljesítmény riport a HOL-t adja, ez a MI-t — a két parancs együtt fedi le a leggyakoribb lag-panasz diagnózisát külön monitoring-plugin nélkül.
**Építőkövek:** `getRegionScheduler()` (chunk-bejárás régiónként darabolva, Folia-szabály), `MinionManager`, meglévő `/icesmp` dispatch keret.
**Buktatók:** Egy nagy világ bejárása több régió-szálon átívelő aggregálást igényel — az összesítő map concurrent szerkezet kell legyen, a végeredményt aszinkron kell összerakni, nehogy a hívó szála blokkolódjon.

### C20. Hopper/redstone-limit figyelő
🟡 • ⭐⭐

**Mi ez:** Figyelmeztető riasztás, ha egy chunkban/claimban a hopper- vagy redstone-sűrűség túllép egy konfigolt küszöböt, mielőtt TPS-problémát okozna.
**Hogyan működne:** Egy óránkénti, régiónként darabolt globális tick megszámolja a betöltött chunkokban a `HOPPER` tileentity-ket és az aktív redstone-komponensek (`comparator`, `repeater`, `observer`) sűrűségét; ha egy chunk vagy egy claim (`ClaimManager` lekérdezéssel) meghaladja a `moderation.hopper-warn-threshold`/`redstone-warn-threshold` configot, a claim tulajdonosának (ha van) és az online adminoknak (`icesmp.admin.perf`) koordinátás figyelmeztetés megy. Nincs automatikus blokk-bontás, csak jelzés.
**Miért jó:** A hopper-alapú automata farmok és redstone-órák a leggyakoribb SMP lag-forrás, amit ma csak akkor vesz észre az admin, amikor már TPS-panasz jön — ez a C9/C19 reaktív riportjait proaktívra fordítja.
**Építőkövek:** `ClaimManager` lekérdezés (tulajdonos-azonosítás), `getRegionScheduler()` chunk-bejárás (mint C19), entitás/játékos-scheduler hop az értesítéshez.
**Buktatók:** A teljes világ periodikus block-scanje maga is terhelés lehet — kis, region-lokális darabokra kell bontani és óránál sűrűbben ne fusson.

### C21. Playtime-tracking
🟢 • ⭐

**Mi ez:** Egyszerű összjátékidő-mérés játékosonként, statisztikaként és admin-diagnózishoz.
**Hogyan működne:** `PlayerJoinEvent`-nél a `PlaytimeManager` eltárolja a session-kezdet timestampet (memóriában, UUID-map), `PlayerQuitEvent`-nél a session-hosszt hozzáadja a tárolt összesített perc-számlálóhoz (`StatsManager`-bővítés vagy önálló store). `/icesmp playtime <név>` (vagy `/stats`, ha az A15 ötlet készen van) kiírja az összesített időt. A `disable()` alatt (ahol a Folia-ütemező már nem fogad taskot) a még bejelentkezett játékosok session-hosszát is közvetlenül hozzá kell adni mentés előtt.
**Miért jó:** Alap adat, amire a C22 heatmap, a B16 mentor-rendszer és a D3 season-MVP kiválasztás is támaszkodhat — olcsó, egyszeri beruházás, sok más ötlet előfeltétele.
**Építőkövek:** `StatsManager` vagy önálló `PersistentStore`, `PlayerJoinEvent`/`PlayerQuitEvent`, a `disable()` közvetlen (nem ütemezett) cleanup mintája.
**Buktatók:** A `disable()` alatti session-lezárás könnyen kimarad, ha csak a `PlayerQuitEvent`-re hagyatkozunk — crash/`/stop` esetén ez az egyetlen hely, ahol menthető az utolsó session.

### C22. Heatmap-adatgyűjtés (halál/mozgás)
🟡 • ⭐⭐

**Mi ez:** Passzív adatgyűjtés arról, hol halnak meg és merre járnak a játékosok, hogy a világ-tervezés (spawn-pontok, események, dungeon-helyszínek) adatra épülhessen.
**Hogyan működne:** `PlayerDeathEvent`-nél egy koordináta+ok (mob-típus/spell/PvP) sor kerül egy append-only fájlba (`data/heatmap/deaths-yyyy-MM.csv`, chunk-felbontásra kerekítve). Mozgás-mintához NEM `PlayerMoveEvent` (túl gyakori, komoly overhead) — egy alacsony frekvenciájú (percenkénti) globális tick mintavételezi minden online játékos chunk-koordinátáját, `Map<chunk, count>`-ot inkrementál, amit napi CSV-be dump-ol a C15 digest mellett. Vizualizáció NEM a plugin feladata — a CSV a B18 térkép-híd vagy külső eszköz bemenete.
**Miért jó:** „Hol halnak a játékosok?” és „mely régiók néptelenek?” ma csak admin-megérzés — ez az adat konkrét világ-tervezési döntéseket tesz lehetővé, és a D8 titkos helyek elhelyezéséhez is bemenet.
**Építőkövek:** `PlayerDeathEvent`, alacsony frekvenciájú globális tick (mintavételezés, NEM `PlayerMoveEvent`), sima append-only fájl, B18 térkép-híd mint lehetséges fogyasztó.
**Buktatók:** `PlayerMoveEvent`-alapú mintavételezés Folián komoly overhead — mindenképp pillanatkép-mintavétellel kell megoldani; a CSV mérete idővel nő, rotáció/tömörítés kell.

### C23. Ütemezett restart-figyelmeztetés broadcast-lánccal
🟢 • ⭐⭐

**Mi ez:** Automatikus, eszkalálódó broadcast-sorozat egy tervezett restart előtt, hogy a játékosok ne veszítsenek progressziót (raid közben, aukción stb.).
**Hogyan működne:** `config/maintenance.yml`: `restart.schedule: ["04:00"]` napi időpont, vagy `/icesmp restart in <perc>` admin-indítás. A `RestartWarningManager` a célidőponthoz képest configolt lépcsőkben (`warn-minutes-before: [60, 30, 10, 5, 1]`) `MessageManager`-broadcastot küld, az utolsó percekben blokkolja az új raid-indítást/aukció-liciteket (ha van ilyen kapu), a célidőpontban natív `Bukkit.shutdown()`-t hív — NEM saját reimplementáció —, hogy a `disable()` lánc (store-mentés) rendesen lefusson.
**Miért jó:** Egy váratlan restart közepén megszakadt raid vagy elveszett aukciós tétel a leggyakoribb „a szerver ette meg a cuccomat” panasz forrása — az eszkalálódó figyelmeztetés ezt gyakorlatilag nullára viszi.
**Építőkövek:** `MessageManager` broadcast-üzenetek, `getGlobalRegionScheduler` (időzített ellenőrzés), meglévő raid-indítás/aukció-kapu.
**Buktatók:** A blokkolás ne maradjon örökre bekapcsolva egy elszállt konfig miatt — mindig legyen admin-oldali `cancel` a láncra.

### C24. Whitelist/szezon-onboarding automatizálás
🟡 • ⭐⭐

**Mi ez:** Új szezon indulásakor (vagy whitelist-es belépéskor) a frakció-választás/spawn-folyamat automatikus, adminmunka nélküli lebonyolítása.
**Hogyan működne:** `config/season-onboarding.yml`: `whitelist.auto-approve: true|false` + `/icesmp whitelist apply <indoklás>` jelentkező-parancs, ami egy YAML-sorba kerül; `auto-approve` esetén azonnal a natív whitelist-hez adja a nevet és broadcastol, manuális módban `/icesmp whitelist review` admin-jóváhagyás. Szezonváltáskor (`SeasonManager` lezárás-hook-ja mellé) egy `resetOnboarding()` az A5 first-join quest-láncot minden meglévő játékosra is újraindítja.
**Miért jó:** Szezon-nyitáskor a whitelist-kérelmek és frakció-elosztás kézi adminmunka — ez a lánc a belépési súrlódást (a first-day lemorzsolódás fő oka) önkiszolgáló, mégis ellenőrzött folyamattá alakítja.
**Építőkövek:** `SeasonManager` szezon-lezárás hook, A5 first-join onboarding quest-lánc, Paper natív whitelist API.
**Buktatók:** Az auto-approve visszaélhető (bot-regisztráció) — érdemes rate-limitet vagy manuális review-t alapértelmezettként hagyni.

### C25. Config-validátor bővítés
🟢 • ⭐

**Mi ez:** A meglévő `ConfigValidator` konvenció-alapú ellenőrzésének kibővítése kereszt-referencia és tartomány-szabályokkal, admin-parancsból lekérdezhető riporttal.
**Hogyan működne:** A `ConfigValidator.validate(...)` ma egyedi kulcsokat néz (material/currency/percent/idő-mértékegység konvenció) — a bővítés kereszt-kulcs szabályokat ad: `classes.<kaszt>.spell-unlocks` id-jai léteznek-e a `SpellRegistry`-ben, `spell-balance.<id>.*` id-k léteznek-e `ConfiguredSpell`-ként, `territory.protection.rules.<típus>` minden `TerritoryType`-ra lefedett-e. A „csak konzolra figyelmeztet indításkor” viselkedés mellé `/icesmp config validate` (node `icesmp.admin.config`) on-demand újra lefuttatja, chat-táblázatban visszaadva a találatokat.
**Miért jó:** A C10 config-diff azt mondja, MI változott — ez azt, hogy a változás ÉRVÉNYES-e; egy elgépelt spell-id vagy lefedetlen territórium-típus ma csak futásidőben (vagy sosem) bukik ki.
**Építőkövek:** `managers/ConfigValidator` (a konvenció-bejárás bővítése), `SpellRegistry.getAll()` kereszt-referenciához, `IceSMPCommand` config alparancs-csoport.
**Buktatók:** A kereszt-referencia szabályok karbantartás-igényesek — minden új enum/registry bevezetésekor a validátor-szabályokat is bővíteni kell, különben hamis biztonságérzetet ad.

### C26. Debug-mód spell-tracinggel
🟢 • ⭐⭐

**Mi ez:** Fejlesztői kapcsoló, ami cast-onként részletes nyomkövetést ír ki (költség-számítás, targeting, hatás-lánc lépései), hogy a spell-hibák gyorsan diagnosztizálhatók legyenek.
**Hogyan működne:** `/icesmp debug spelltrace on|off [játékos]` (node `icesmp.admin.debug`) per-player (vagy globális) flaget állít. Bekapcsolt állapotban az `AbilityCatalystListener` cast-pipeline minden lépése (targeting-eredmény, `usesResource` döntés, `canAfford`/`consume` kimenet, `ConfiguredSpell` láncolt hatásainak live-balance-szal beszorzott végértékei) egy sort ír egy debug-csatornára/konzolra. A C1 spell-statisztikától eltérően NEM aggregál, hanem egy-egy castot bont fel élőben — kizárólag debug-módban aktív.
**Miért jó:** A „nem sül el a spell” / „rossz számot ad” hibák ma forráskód-olvasással diagnosztizálhatók — ez a mód a live-balance számítás minden lépését kiteríti, a fejlesztő percek alatt megtalálja az eltérést.
**Építőkövek:** `AbilityCatalystListener` cast-pipeline, `ConfiguredSpell` live-accessorai (`spells-balance.yml` cast-idejű olvasása), `BaseSpell.balance()`.
**Buktatók:** A tracing minden láncolt hatásnál extra string-építést jelent — korai-kilépős feltétel mögé kell tenni (`if (!debugEnabled) return;`), nehogy éles szerveren véletlenül mérhető overheadet okozzon.

### C27. /icesmp version + changelog ingame
🟢 • ⭐

**Mi ez:** Egyetlen parancs, ami megmutatja a futó plugin build-verzióját és a legutóbbi változásokat, admin-diagnózishoz és support-kérdésekhez.
**Hogyan működne:** A build (`build.gradle.kts` verzió-mezője + opcionálisan git commit-hash egy `generateVersionInfo` Gradle-taskkal, ami resource-fájlba írja) alapján `/icesmp version` kiírja: plugin-verzió, build-időbélyeg, Minecraft/Paper API-verzió. `/icesmp changelog [N]` a JAR-ba csomagolt rövid bejegyzés-lista (`changelog.yml` vagy `CHANGELOG.md`-részlet) utolsó N tételét listázza chatben.
**Miért jó:** Több szerver-példány (teszt/éles) vagy gyakori frissítés esetén a „melyik verzió fut éppen” kérdés ma csak a jar-fájlnévből vagy konzol-logból dönthető el — ez másodpercekre rövidíti, a changelog ingame Discord/GitHub nélkül olvasható.
**Építőkövek:** `build.gradle.kts` verzió-mező, `IceSMPCommand` dispatch keret, JAR-ba csomagolt resource (`saveResource` minta).
**Buktatók:** A changelog-tartalmat automatizálás nélkül könnyű elfelejteni szinkronban tartani a valós commit-történettel — érdemes a release-folyamat kötelező lépéseként kezelni.

### C28. Dupe/anomália-riasztó (gazdaság-védelem)
🟡 • ⭐⭐

**Mi ez:** Valós idejű figyelő, ami egy játékos egyenlegének/egyedi itemének gyanúsan nagy ugrásánál admin-riasztást küld — a C3 heti riport reaktív, ez proaktív párja.
**Hogyan működne:** A `CurrencyManager` egyenleg-módosító metódusai (a C3-mal közös becsatornázási pont) egy csúszóablakos (pl. 60 mp) `Map<UUID, Long>` delta-összeget vezetnek; ha egy játékos egyenlege az ablakban `anomaly.currency-spike-threshold` fölé nő ismeretlen forrásból (a C3 forrás-tagek alapján), online adminoknak (`icesmp.admin.economy`) azonnali riasztás megy névvel + delta-értékkel. Item-oldalon: ha egy nem-stackelhető, PDC-tagelt egyedi item (relikvia, katalizátor) két példányban létezik egyszerre (periodikus, alacsony frekvenciájú globális PDC-ellenőrzés), az is riasztást vált ki.
**Miért jó:** A C3 heti riport egy hét múlva mondja meg, hogy folyt egy csap — ez a réteg a duplázást/exploitot PERCEKEN belül jelzi, amíg a kár korlátozott és a C7 logokból visszakövethető.
**Építőkövek:** `CurrencyManager` egyenleg-módosító hívási pontok (C3-mal közösen becsatornázva), PDC-egyediség-ellenőrzés, C7 admin audit-log.
**Buktatók:** Legitim nagy tranzakciók (piaci nagy eladás, kassza-osztalék) hamis pozitívot adhatnak — a küszöböt a C3 adatokból kell kalibrálni, az ismert forrású mozgásokat ki kell zárni, különben az adminok elkezdik ignorálni a riasztást.

### C29. Játékos-jelentés rendszer (/report)
🟢 • ⭐⭐

**Mi ez:** Beépített `/report` parancs, amivel a játékosok admin-sorba küldhetnek panaszt (chat-sértés, gyanús viselkedés, bug), hogy ne csak Discordon/kézzel jusson el az adminokhoz.
**Hogyan működne:** `/report <név> <ok>` mindenkinek elérhető; a `ReportManager` egy sort ír `data/reports.yml`-be (`YamlStore.saveAtomic`): időbélyeg, jelentő, célszemély, ok, státusz (OPEN/RESOLVED). Beküldéskor minden online admin (`icesmp.admin.moderation`) kattintható `[Ugrás]` (`teleportAsync` a célszemélyhez) és `[Inspect]` (C12 játékos-inspektor RUN:-hívása) gombos értesítést kap. `/icesmp reports [open|all]` lista + `/icesmp reports resolve <id>` lezárás, C7 audit-logba írva.
**Miért jó:** Ma egy chat-sértést vagy gyanús viselkedést csak akkor lát az admin, ha épp online és figyel — ez a strukturált sor biztosítja, hogy semmi ne vesszen el; a C17 chat-szűrő és C18 AFK mellett a moderációs eszköztár harmadik lába.
**Építőkövek:** `YamlStore.saveAtomic`, `teleportAsync` (Folia — gomb-alapú admin-teleport), C12 játékos-inspektor (Inspect-gomb célja), C7 admin audit-log.
**Buktatók:** Spam-jelentések elleni rate-limit kell (jelentőnként pl. percenként 1) — enélkül a report-sor zajossá válik és az adminok elkezdik ignorálni.
