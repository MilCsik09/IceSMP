# IceSMP — a jelenleg futó buildtől az integrált release-ig

<!-- icesmp-doc-id: release.deployed-build-to-release -->

Ez a dokumentum azt válaszolja meg, hogy **mi változik a szerveren jelenleg
futó `IceSMP-1.0-TESTING.jar` képességeihez képest**. Nem a Git-fejlesztési
történetet írja le, és nem helyettesíti a teljes funkciókézikönyvet.

## Vizsgált állapot

| Mező | Érték |
|---|---|
| Deployed baseline | `IceSMP-1.0-TESTING.jar` |
| Baseline SHA-256 | `da039f0e2bdf0e67b216ce82d7d3fe3b6da0af6e18f6fa175762c37493795a05` |
| Baseline pluginverzió | `1.0-TESTING` |
| Baseline API / Folia | Paper API `1.21.11`; `folia-supported: true` |
| Dokumentált release | `4643ab53586f0c1ee7352df16dcd477013e6fad4` |
| Audit dátuma | 2026-07-30 |
| Baseline forráscommitja | `BINARY_ONLY` — a JAR-ban nincs Git SHA, exact commit nem bizonyítható |

### Mit tekintünk bizonyítéknak?

A baseline-nál a JAR descriptorát, minden classát, beágyazott resource-át,
command routingját, permissionellenőrzését, GUI-holderét, listenerét,
perzisztens store-ját és adatvezérelt tartalmát vizsgáltuk. A release-nél a
bootstrapot, a regisztrált parancsokat, manager-, service- és
listenerútvonalakat, GUI-routingot, konfigurációt, persistence-wiringot,
teszteket és a buildelt artifactot használtuk.

Az itt szereplő „Új” azt jelenti, hogy a képesség **nincs benne a deployed
IceSMP JAR-ban, de benne van az integrált release-ben**. Ettől még az élő
szerveren korábban külső plugin biztosíthatott hasonló funkciót.

> **Fontos:** a JAR-ba csomagolt alapérték nem bizonyítja az élő szerver
> jelenlegi konfigurációját. A zöld CI pedig kód- és regressziós bizonyíték,
> nem production runtime bizonyíték. A plugin-JAR-ok eltávolításához az ebben
> a dokumentumban jelölt kézi és fault-injection tesztek is szükségesek.

## Gyors áttekintés

| Leltárelem | Deployed JAR | Integrált release | Eltérés |
|---|---:|---:|---|
| Regisztrált root command | 30 | 68 | +38 |
| Root alias | 56 | 79 | +23 |
| Bizonyított permission node | 16 | 44 | +28; ebből 1 bundled crate-permission |
| GUI-holder / funkcionális GUI-család | 14 | 22 | +8 |
| Lifecycle-ba kötött persistent state owner | 17 | 34 | +17 |
| Top-level production class | 298 | 545 main Java source unit | jelentős bővülés |
| Kaszt | 13 | 13 | változatlan darabszám |
| Kasztspecializáció | 31 | 35 | +4 |
| Profession | 8 | 8 | változatlan darabszám |
| Profession-specializáció | 16 | 16 | változatlan darabszám |
| Questdefiníció | 45 | 160 | +115 |
| Profession-recept | 124 | 438 | +314 |
| Profession-anyag | 9 | 81 | +72 |
| Konfigurált spell-balance azonosító | 392 | 420 | +28 konfigurált azonosító |
| Relikvia | 5 | 6 | +1 |
| Rituálé | 19 | 21 | +2 |
| Natív crate-definíció | 0 | 2 | +2 |

A „420 konfigurált spell-balance azonosító” nem jelenti automatikusan, hogy
mind a 420 külön, játékos által elérhető spellként regisztrálódik. A
runtime spell registry és az unlockfeltételek határozzák meg a tényleges
elérhetőséget.

## A legfontosabb változások

Minden sor ugyanazt a hat mezőt használja: státusz, közönség, magyarázat,
szükséges teendő, kapcsolódó parancs vagy GUI, illetve runtime teszt.

| Státusz | Érintett közönség | Rövid magyarázat | Szükséges admin- vagy builderteendő | Parancs / GUI | Runtime teszt |
|---|---|---|---|---|---|
| Új | Játékos, Admin, Tesztelő | Globális AFK-észlelés: automatikus inaktivitás és kézi kapcsolás. Az AFK játékosok több jutalomútvonalból kizárhatók. | Állítsd be az inaktivitási időt és a jutalomblokkolást; ellenőrizd a tablistajelzést. | `/afk`; natív tablista | Igen: mozgás, nézelődés, chat, parancs, interakció, reconnect és jutalomkapuk |
| Megszűnt | Szervervezető, Admin | A fejlesztés közben megjelent jutalmazó AFK-zóna végleg kikerült. A deployed JAR-ban sem volt ilyen, ezért ez termékscope-törlés, nem deployed képesség elvesztése. | Ne telepíts AxAFKZone/AxAPI-t, és ne vigyél át zóna-, payout- vagy bossbar-configot. | Nincs zónaparancs vagy GUI | Igen: bizonyítani kell, hogy nincs zónajutalom, miközben a globális AFK működik |
| Új | Játékos, Builder, Admin, Tesztelő | Natív sit-only rendszer támogatott blokkgeometriával és lifecycle cleanup-pal. | Ellenőrizd az engedélyezett világokat, a tiltott anyagokat és a fizikai ülőhelyeket. | `/sit [fel]`; jobb kattintás | Igen: minden blokkforma, foglalás, damage/sneak/break/teleport/reload/disable |
| Megszűnt | Szervervezető, Játékos | Lay, crawl, stacking, valamint player- vagy NPC-sitting nem része a natív scope-nak. | Ezekre ne ígérj GSit-paritást; a GSit csak sit runtime elfogadás után távolítható el. | Nincs | Igen: negatív teszt, hogy egyik tiltott útvonal sem érhető el |
| Új | Játékos, Admin, Builder, Tesztelő | Natív crate-rendszer vásárlással, preview-val, fizikai crate-helyekkel, több jutalomtípussal, audit- és recovery-folyamattal. | Hozd létre és ellenőrizd a crate-helyeket; teszteld az inventory overflow-t és a recoveryt. | `/crate …`; crate browser és spin GUI | Igen, fault injectionnel is |
| Új | Moderátor, Admin, Tesztelő | Natív warning, kick, mute/temp mute, ban/temp ban, visszavonás, history, aktív punishment, report, PM, SocialSpy, vanish, inventory/ender admin és offline teleport. | Készíts szerepkörönkénti permissionmátrixot; teszteld a persistence-t, expiry-t, escrow-t és auditot. | Moderációs parancsok; `/moderation`; `/invsee` | Igen, minden kritikus útvonalon |
| Új | Szervervezető, Admin, Tesztelő | Natív MOTD több változattal, idő- vagy véletlen választással, eseményprioritással, vanished játékosok kiszűrésével és ikonkezeléssel. | Ellenőrizd a szövegeket, ikonokat, symlink-szabályt és a proxy/server-list környezetet. | Konfiguráció és `/icesmp reload` | Igen: párhuzamos ping, ikonhibák, gyors reload, scheduler rejection |
| Jelentősen megváltozott | Játékos, Admin, Builder, Eventes | Jelentősen bővült a quest-, profession-, recept-, item-, történeti, dungeon-, esemény- és politikai tartalom. | A fizikai helyszíneket, NPC-kötéseket, kapukat, lootot és erőforráscsomagot stagingen validáld. | Több meglévő és új command/GUI | Igen, tartalmi és világbejárásos playtest |
| Új | Üzemeltető, Tesztelő | Natív Warden-XP és player/mob crop-trample védelem váltja a mini-pluginok képességeit. | Az élő configba csak ellenőrzött értékeket vigyél át. | Automatikus listener | Igen: Warden XP-tartomány, játékos- és mobtaposás |

## Játékosokat érintő újdonságok

| Státusz | Közönség | Magyarázat | Teendő | Parancs / GUI | Runtime teszt |
|---|---|---|---|---|---|
| Új | Játékos | Globális AFK kézi és automatikus állapottal; a saját `/afk` parancs nem számít aktivitásnak. | Kommunikáld, hogy ez nem jutalmazó zóna. | `/afk` | Igen |
| Új | Játékos | Sit-only: támogatott lépcsőn, slabon, carpeten és hórétegen lehet leülni, ha a világ- és biztonsági policy engedi. | Játékosszabályzatban írd le a felállás módját. | `/sit`, jobb kattintás | Igen |
| Új | Játékos | Natív crate-vásárlás, információ, preview és nyitás. A bundled tartalom két crate-et (`koznapi`, `ritka`) bizonyít. | Csak validált, fizikailag elhelyezett crate-et hirdess meg. | `/crate buy\|info\|preview`; GUI | Igen |
| Új | Játékos | Privát üzenetek és válaszútvonalak. | Állítsd össze a chat- és adatkezelési szabályzatot. | `/msg`, `/tell`, `/w`, `/reply` (`/r`) | Igen |
| Új | Játékos | Saját report küldése, legalább háromszavas indokkal. | Készíts moderátori feldolgozási SLA-t. | `/report <név> <indok>` | Igen |
| Új | Játékos | Bestiárium, céh, emlékek, krónika, lore-kódex, komp, becsületpárbaj, lélekkovács, statok, suttogás, szakmai heti cél és tanács funkciók. | Csak a ténylegesen aktivált és világban előkészített részeket kommunikáld. | `/bestiarium`, `/ceh`, `/emlek`, `/kronika`, `/lore`, `/komp`, `/parbaj`, `/soulforge`, `/stats`, `/suttogas`, `/szakmacel`, `/tanacs` | Igen, funkciónként |
| Jelentősen megváltozott | Játékos, Tartalomkészítő | 160 quest, 438 profession-recept, 81 profession-anyag, 35 kasztspecializáció, új runák, signature itemek, dungeon loot és szezonális tartalom került a bundled release-be. | Ellenőrizd az unlockfeltételeket, recepteket, jutalmakat és resource packet. | Quest-, profession-, spell-, item- és eseményfelületek | Igen |
| Kisebb változást kapott | Játékos | A bundled HUD oldalsávja és natív tablistája alapból engedélyezett lett; ez nem bizonyítja az élő override-ot. | Stagingen ellenőrizd a TAB-bal és más formatterekkel való ütközést. | `/hud`; automatikus tablista | Igen |

## Admin- és moderátori változások

| Státusz | Közönség | Magyarázat | Teendő | Parancs / GUI | Runtime teszt |
|---|---|---|---|---|---|
| Új | Moderátor, Admin | Warning, kick, állandó/ideiglenes mute és ban, unmute/unban, history és aktív punishment lista. Az állapot tartós, lejárati feldolgozással. | Permissionmátrix, restart/expiry, sérült fájl és lemezhiba teszt. | `/warn`, `/kick`, `/mute`, `/ban`, `/tempban`, `/unmute`, `/unban`, `/history`, `/punishments` | Igen |
| Új | Moderátor, Admin | Reportbeadás és admin reportkezelés, lezárással. | Döntsd el, ki láthatja és zárhatja le a reportokat. | `/report`; `/reports [all\|resolve <id>]` | Igen |
| Új | Moderátor, Admin | Privát üzenet, reply-partner, SocialSpy és chatmoderációs audit. | Ellenőrizd quit–reconnect viselkedést és az adatvédelmi jogosultságot. | `/msg`, `/tell`, `/w`, `/reply`, `/socialspy` | Igen |
| Új | Moderátor, Admin | Vanish és külön vanish-láthatósági permission. | Teszteld a player listát, MOTD countot, join/quit láthatóságot és spectator/admin eseteket. | `/vanish [játékos]` | Igen |
| Új | Moderátor, Admin | Online main inventory és ender chest read/edit; editmódhoz escrow és reconnect recovery tartozik. | Csak szűk edit permissiont adj; fault-injection teszteld az escrow-t. | `/invsee <játékos> [read\|edit] [main\|ender]`; GUI | Igen, kritikus |
| Új | Moderátor, Admin | Utolsó ismert helyre történő offline teleport. | Ellenőrizd a világ meglétét és a célhely biztonságát. | `/offlinetp <játékos>` | Igen |
| Új | Admin | Moderációs GUI és admin inspection/config felületek. | Jogosultság és minden funkcionális slot ellenőrzése. | `/moderation [játékos]`; `/icesmp inspect\|config …`; config GUI | Igen |
| Új | Admin | Crate admin: helyezés, törlés, kulcsadás, lista, stat és státusz. | Csak tesztelt recovery után nyiss éles crate-et. | `/crate set\|remove\|give\|list\|stats\|resetstats\|status` | Igen |
| Belső megbízhatósági javítás | Admin, Üzemeltető | Központi persistent-store koordináció, korrupt-state és kritikus írási hibák jelzése, tranzakciós naplók és forgó auditlogok kerültek be. | Figyeld az indítási/disable logot; készíts mentési és manuális review folyamatot. | Nincs közös parancs; státuszparancsok és logok | Igen, lemezhiba és restart |

## Buildereket és world designereket érintő változások

| Státusz | Közönség | Magyarázat | Teendő | Parancs / GUI | Runtime teszt |
|---|---|---|---|---|---|
| Új | Builder, Admin | A natív crate fizikai blokkhoz és világhoz köthető. Helycsere, törlés és világcsere recoveryt érint. | Stagingen hozz létre, cserélj és törölj crate-helyet; dokumentáld a world policy-t. | `/crate set <id>`, `/crate remove` | Igen |
| Új | Builder | Sit blokkgeometria: stairs, alsó/felső slabs, carpet, moss/pale moss carpet és snow; clearance, folyadék, support és blacklist szabályok. | Minden használt blokkformát és ülésmagasságot járj végig. | Jobb kattintás, `/sit` | Igen |
| Jelentősen megváltozott | Builder, Eventes | Új event spawnpoint-, dungeon gate/chest/boss-, hidden spot-, archaeology-, corruption/cultist- és szezonális világkapcsolatok. | WorldEdit, világátnevezés vagy csere után teljes helyszínvalidáció. | `/events …`; `/territory …`; kapcsolódó admineszközök | Igen |
| Jelentősen megváltozott | Builder | Claim trust GUI/wand és új territory/dungeon útvonalak bővítik a területkezelést. | Ellenőrizd a prioritást, bypass permissionöket és WorldGuard-átfedést. | `/claim …`; `/territory …`; trust GUI | Igen |
| Új | Builder, Tartalomkészítő | Új signature item, runa, dev item, advancement, quest- és profession-content resource pack függőségeket hozhat. | A végleges resource packkel ellenőrizd a megjelenést és recepteket. | Item/admin és tartalmi felületek | Igen |

## Új parancsok

A release 38 új root commandot regisztrál. A moderációs és
plugin-replacement parancsok használata:

| Státusz | Közönség | Parancs és cél | Teendő | GUI | Runtime teszt |
|---|---|---|---|---|---|
| Új | Játékos | `/afk` — globális AFK kézi kapcsolása | AFK timeout és reward block ellenőrzése | Nincs | Igen |
| Új | Játékos | `/sit [fel]` — leülés vagy felállás | Világ- és anyagpolicy ellenőrzése | Nincs | Igen |
| Új | Játékos, Admin | `/crate buy\|info\|preview …`; admin: `set\|remove\|give\|list\|stats\|resetstats\|status` | Permission és crate-hely ellenőrzése | Browser és spin GUI | Igen |
| Új | Moderátor | `/warn <player> [reason]`; `/kick <player> [reason]` | Audit és permission teszt | Moderációs GUI | Igen |
| Új | Moderátor | `/mute <player> [30m\|2h\|7d\|permanent] [reason]`; `/unmute <player> [reason]` | Expiry/restart teszt | Moderációs GUI | Igen |
| Új | Moderátor | `/ban <player> [reason]`; `/tempban <player> <30m\|2h\|7d> [reason]`; `/unban <player> [reason]` | Login/expiry/restart teszt | Moderációs GUI | Igen |
| Új | Moderátor | `/history <player> [page]`; `/punishments [player]` | Oldalszám és aktív/lejárt állapot teszt | Moderációs GUI | Igen |
| Új | Játékos, Moderátor | `/report <name> <legalább háromszavas indok>`; `/reports [all\|resolve <id>]` | Reportfolyamat kialakítása | Nincs | Igen |
| Új | Játékos, Moderátor | `/msg`, `/tell`, `/w <player> <message>`; `/reply` vagy `/r <message>`; `/socialspy` | Reply-partner és SocialSpy permission teszt | Nincs | Igen |
| Új | Moderátor, Admin | `/vanish [online-player]`; `/invsee <online-player> [read\|edit] [main\|ender]`; `/offlinetp <player>`; `/moderation [online-player]` | Read/edit és láthatósági jogosultság szétválasztása | Invsee és moderációs GUI | Igen |

További új rootok:

| Terület | Új root commandok | Megjegyzés |
|---|---|---|
| Történet és felfedezés | `/bestiarium`, `/emlek`, `/kronika`, `/lore` | Bestiárium, emlékek, krónika és lore-kódex |
| Közösség és politika | `/ceh`, `/tanacs`, `/parbaj` | Céh, tanács és becsületpárbaj |
| Világ és közlekedés | `/komp` | Komprendszer |
| Lélek és progression | `/soulforge`, `/szakmacel`, `/stats` | Lélekkovács, heti szakmai cél és statok |
| Kémkedés és kommunikáció | `/kem`, `/suttogas` | Kém- és suttogásrendszer |
| Admin/item | `/iceitem` | Item- és dev item adminútvonal |
| Megjelenítés | `/hud` | Játékos HUD-kezelés |

Az új aliasok:

`bestiary`, `lajstrom`, `gild`, `guild`, `crates`, `ladak`, `memory`,
`emlekek`, `iitem`, `icegive`, `spy`, `ferry`, `chronicle`, `kodex`,
`duel`, `mod`, `r`, `bejelent`, `lelekkovacs`, `sutt`, `weeklygoal`,
`council` és `v`. A teljes routingot és tab completiont a
[`COMMAND_REFERENCE.md`](../reference/COMMAND_REFERENCE.md) tartalmazza.

## Megváltozott parancsok

A bytecode/source routing összehasonlítás 25, már a deployed JAR-ban is
létező root implementációján talált változást:

| Státusz | Közönség | Érintett rootok | Teendő | Kapcsolódó GUI | Runtime teszt |
|---|---|---|---|---|---|
| Jelentősen megváltozott | Admin, Builder, Eventes | `/icesmp`, `/claim`, `/events`, `/faction`, `/market`, `/npcbind`, `/territory` | Az új config/inspect, trust, esemény, war, market, NPC és dungeon ágakat szerepkörönként teszteld. | Config, trust, market és admin GUI-k | Igen |
| Kisebb változást kapott | Játékos, Admin | `/achievements`, `/adomany`, `/daily`, `/exchangeboard`, `/leaderboard`, `/menu`, `/parkour`, `/party`, `/pet`, `/profession`, `/quest`, `/relic`, `/sinner`, `/souls`, `/spec`, `/spell`, `/spellbook`, `/talent` | A régi használati minták mellett futtasd az új content és validációs eseteket. | A kapcsolódó meglévő GUI-k | Igen, célzottan |

Öt root implementációja bytecode-szinten változatlan a baseline-hoz képest:
`/bank`, `/bounty`, `/class`, `/currency`, `/profile`. A környező
manager-, config- vagy contentváltozás ezek működésére ettől még hatással
lehet.

## Megszűnt parancsok

| Státusz | Közönség | Magyarázat | Teendő | Parancs / GUI | Runtime teszt |
|---|---|---|---|---|---|
| Már korábban is elérhető volt | Minden szerepkör | A 30 deployed root egyikét sem távolította el a release. | Régi makrók és dokumentációk továbbra is ellenőrizendők a megváltozott argumentumok miatt. | Nincs megszűnt root | Igen, command smoke test |
| Megszűnt | Admin | Nincs jutalmazó AFK-zóna adminparancs; az átmeneti scope nem része a release-nek. | Ne dokumentálj vagy ossz ki nem létező zónaparancsot. | Nincs | Negatív teszt |

## Új vagy változott permissionök

A baseline 16 permissionje mind megmaradt. A release 27 új statikus node-ot
és egy bundled, dinamikus crate-node-ot bizonyít:

| Státusz | Közönség | Node-ok | Teendő | Parancs / GUI | Runtime teszt |
|---|---|---|---|---|---|
| Új | Admin | `icesmp.admin.all`, `.config`, `.crate`, `.currency`, `.faction`, `.inspect`, `.item`, `.job`, `.moderation`, `.relic`, `.sinner`, `.war` | Ne add öröklés nélkül széles stáb-csoportnak; készíts explicit mátrixot. | Adminparancsok és GUI-k | Igen |
| Új | Moderátor | `icesmp.moderation.warn`, `.kick`, `.mute`, `.ban`, `.history`, `.gui`, `.socialspy`, `.vanish`, `.vanish.see`, `.offlinetp` | Különítsd el a végrehajtási, megtekintési és láthatósági jogokat. | Moderációs suite | Igen |
| Új | Moderátor, Admin | `icesmp.moderation.inventory.read`, `.inventory.edit` | Az edit kritikus; csak célzottan oszd ki. | `/invsee`; GUI | Igen, escrow-val |
| Új | Játékos | `icesmp.message`, `icesmp.sit`, `icesmp.crate.use` | Ellenőrizd az alapcsoportot és a tiltott világokat. | PM, sit, crate | Igen |
| Új | Játékos, VIP/admin | `icesmp.crate.ritka` | Ez csak a bundled `ritka` crate permissionje; további crate-ek dinamikus node-okat adhatnak. | Ritka crate | Igen |

A descriptor egyik node-hoz sem bizonyít élő defaultot vagy
permission-plugin kiosztást. Az élő LuckPerms- vagy más permissionállapotot
külön kell exportálni és ellenőrizni.

## Konfigurációs változások

| Státusz | Közönség | Magyarázat | Teendő | Parancs / GUI | Runtime teszt |
|---|---|---|---|---|---|
| Új | Admin | Új fő configterületek: globális AFK, crate, moderation, MOTD, sit, tablista és dev item. | Ne másold vakon a defaultot; készíts kontrollált override-ot. | `/icesmp config …`; config GUI | Igen |
| Megszűnt | Admin | A végleges AFK-configban csak `afk-after-seconds` és `block-rewards` marad. Nincs `zones`, zónajutalom, payout, bossbar, refresh vagy zóna-`enabled`. | Töröld az átmeneti/zónás kulcsokat minden staging override-ból. | `/icesmp reload` | Igen |
| Jelentősen megváltozott | Tartalomkészítő, Admin | Quest, profession-recept, profession-anyag, itemrarity, faction shop, spell balance/VFX, world event, dungeon, territory, ritual és signature tartalom jelentősen bővült. | Validáld a hivatkozott ID-kat és jutalmakat; ne hagyj orphan contentet. | Tartalmi parancsok/GUI-k | Igen |
| Kisebb változást kapott | Admin | Bundled HUD sidebar és tablista `true`; currency exchange fee 3%; dinamikus árfolyam reference supply 2500. | Az élő override-ot hasonlítsd össze, mert a JAR default nem bizonyít deploymentet. | `/hud`, `/currency rates` | Igen |
| Átnevezett vagy áthelyezett | Tartalomkészítő | Több legacy custom-model-data beállítást modern item model path vált, és egyes territory protection szabályok átstrukturálódtak. | Resource pack és régi override migráció szükséges. | Item- és territory-rendszer | Igen |
| Megszűnt | Admin, Builder | Négy korábbi parkour master-trial configútvonal kikerült, miközben a kapcsolódó questek céljai átalakultak. | Régi pályahivatkozások és questleírások ellenőrzése. | `/parkour`, `/quest` | Igen |

A reload újraolvassa a fő configot és célzottan frissíti többek között a
crate-, moderation-, invsee-, vanish-, MOTD-, sit-, relic-, spell-VFX- és
achievementállapotot. A már elindított scheduler periódusa nem minden
esetben ütemeződik újra; az intervallumkulcsokat restart-requiredként kell
kezelni, amíg a hot-reschedule nincs külön bizonyítva.

## GUI-változások

A baseline 14 GUI-családja megmaradt. Nyolc új holderrel bizonyított
funkcionális felület jelent meg:

| Státusz | Közönség | GUI | Teendő | Megnyitás | Runtime teszt |
|---|---|---|---|---|---|
| Új | Játékos | Bestiárium | Lapozás, lezárt/ismert állapot és visszalépés teszt. | `/bestiarium` | Igen |
| Új | Játékos, Builder | Claim trust | Tulajdonos, trust/untrust és cleanup teszt. | `/claim trustgui` útvonal | Igen |
| Új | Admin | Config menü | Minden módosító slot és permission teszt. | `/icesmp config menu` útvonal | Igen |
| Új | Játékos | Crate browser és crate spin | Preview, nyitás, dupla kattintás és bezárás teszt. | `/crate`; fizikai crate | Igen |
| Új | Moderátor, Admin | Invsee | Read/edit, main/ender és escrow cleanup teszt. | `/invsee` | Igen, kritikus |
| Új | Moderátor, Admin | Moderációs GUI | Minden büntetési és history action teszt. | `/moderation` | Igen |
| Új | Játékos | Pet GUI | Állapot, stance és cleanup teszt. | `/pet` kapcsolódó útvonal | Igen |

## Persistence és recovery

A baseline 17 lifecycle-store-ja mind megmaradt. A release 17 további
perzisztens tulajdonost köt az indítási/mentési lifecycle-ba:

`BlockRegenJournal`, `ChronicleManager`, `CorruptionManager`,
`CouncilManager`, `CrateManager`, `DevItemManager`, `DungeonLootService`,
`EventSpawnPointManager`, `GuildManager`, `HiddenSpotManager`,
`InvseeManager`, `ModerationManager`, `ProfessionWeeklyGoalManager`,
`RaidManager`, `ReportManager`, `SeasonFinaleManager` és
`SeasonMonumentManager`.

Az új állományok között szerepel a `crates-data.yml`,
`invsee-escrow.yml`, `moderation-data.yml`, `reports.yml`,
`event-spawnpoints.yml`, `dungeon-loot.yml`, `block-regen.yml` és több
történeti/szezonális state. A market külön `market-journal.yml`
tranzakciós naplót is használ.

| Státusz | Közönség | Magyarázat | Teendő | Parancs / GUI | Runtime teszt |
|---|---|---|---|---|---|
| Belső megbízhatósági javítás | Üzemeltető | Központi load/save koordináció, temp/atomic írási útvonalak, korrupt-state és kritikus írási hiba jelzése. | Mentés, írásjog, lemezbetelés és visszaállítás teszt. | Startup/disable log | Igen, fault injection |
| Belső megbízhatósági javítás | Admin | Crate opening ledger, recovery ledger és forgó auditlog; bizonytalan külső side effect esetén `MANUAL_REVIEW`. | Legyen dokumentált manuális settlement-folyamat; ne adj automatikusan kétszer jutalmat. | `/crate status`; `crate-openings.log` | Igen, kritikus |
| Belső megbízhatósági javítás | Moderátor, Admin | Inventory edit escrow, transfer barrier és reconnect recovery csökkenti az itemvesztés/duplázás kockázatát. | Teszteld quit, kick, reload, disable és scheduler failure közben. | `/invsee … edit …`; GUI | Igen, kritikus |
| Belső megbízhatósági javítás | Moderátor, Admin | Punishment ledger, expiry és audit tartós state-t ad a moderációnak. | Restart, sérült state, lejárat és visszavonás teszt. | Moderációs suite | Igen |

## Új gameplay-rendszerek

| Státusz | Közönség | Rendszer | Teendő | Elérés | Runtime teszt |
|---|---|---|---|---|---|
| Új | Játékos | Bestiárium és statisztikai harci nyilvántartás | Loot/kill és megjelenítés ellenőrzése. | `/bestiarium`, `/stats` | Igen |
| Új | Játékos, Eventes | Céh, tanács, becsületpárbaj, war window és közösségi politika | Szabályok és konfliktusok playtestje. | `/ceh`, `/tanacs`, `/parbaj`, `/faction war` | Igen |
| Új | Játékos, Tartalomkészítő | Emlék, krónika, lore, campfire story, hidden spot és dialog alapú történeti réteg | A világhelyek és szövegek teljes bejárása. | `/emlek`, `/kronika`, `/lore`; automatikus triggerek | Igen |
| Új | Játékos, Builder | Dungeon gate/loot, régészet, corruption és kultista események | Kapuk, loot, regen journal és event spawnpontok ellenőrzése. | `/events …`; világinterakció | Igen |
| Új | Játékos | Runák, signature itemek, soulforge, cursed gear és item provenance | Resource pack, craft, anvil, drop és védelem teszt. | `/soulforge`; iteminterakciók | Igen |
| Új | Játékos | Combat tag, damage indicator, death recap, low-health border és natív health szabályok | PvP/PvE és vizuális teszt. | Automatikus | Igen |
| Új | Játékos | Komp, faction caravan, stranger NPC, capital law és city guard kiegészítések | Fizikai útvonal/NPC és régióteszt. | `/komp`; NPC és világtrigger | Igen |
| Új | Játékos | Warden XP és crop-trample protection | Configérték és más védelmi pluginok ütközésének ellenőrzése. | Automatikus | Igen |

## Jelentősen megváltozott rendszerek

| Státusz | Közönség | Rendszer | Teendő | Elérés | Runtime teszt |
|---|---|---|---|---|---|
| Jelentősen megváltozott | Tartalomkészítő, Játékos | Questkatalógus: 45-ről 160 bundled definícióra bővült. | ID-, objective-, reward- és location-hivatkozások validálása. | `/quest`; quest GUI | Igen |
| Jelentősen megváltozott | Tartalomkészítő, Játékos | Profession-receptek: 124-ről 438-ra; profession-anyagok: 9-ről 81-re. | Craft/smith/enchant feltételek, blueprint és overflow teszt. | `/profession`; receptkönyv | Igen |
| Jelentősen megváltozott | Játékos | Kasztspecializációk: 31-ről 35-re; spell balance 28 további konfigurált ID-val. | Unlock és balance playtest; registry-eltérés keresése. | `/spec`, `/spell`, `/spellbook` | Igen |
| Jelentősen megváltozott | Eventes, Builder | Eseményorchestration, új spawnpointok, season finale/monument, dungeon és faction war rétegek. | Egymást kizáró nagy események, prioritás és recovery teszt. | `/events`; világhelyek | Igen |
| Jelentősen megváltozott | Admin, Builder | Claim-, territory- és NPC-kötési routing bővült. | WorldGuard/FancyNpcs jelenléttel és nélkül is teszteld. | `/claim`, `/territory`, `/npcbind` | Igen |

## Megszűnt vagy elvetett funkciók

| Státusz | Közönség | Funkció | Teendő | Parancs / GUI | Runtime teszt |
|---|---|---|---|---|---|
| Megszűnt | Szervervezető, Admin | Jutalmazó AFK-zóna, zónában töltött idő alapú payout és AFK-zóna bossbar. | AxAFKZone/AxAPI ne kerüljön deploymentbe; régi zónaconfig ne kerüljön át. | Nincs | Negatív teszt |
| Megszűnt | Játékos | Lay, crawl, stacking, player sitting és NPC sitting scope. | Ne kommunikálj teljes GSit-paritást. | Nincs | Negatív teszt |
| Megszűnt | Admin | Legacy `settings.debug` és több elavult content/config útvonal. | Migráció előtt diffeld az élő override-ot. | Config | Igen |
| Átnevezett vagy áthelyezett | Builder, Tartalomkészítő | Régi custom-model-data és territory protection configok egy része modern item model/új struktúra alá került. | Resource pack és override migráció. | Nincs közös parancs | Igen |

## Belső megbízhatósági javítások

| Státusz | Közönség | Javítás | Teendő | Kapcsolódó felület | Runtime teszt |
|---|---|---|---|---|---|
| Belső megbízhatósági javítás | Üzemeltető | Folia-barát entity/global/region scheduler útvonalak, task lease és callback gate több új rendszerben. | Teszteld valódi Folia szerveren; ne csak Paper unit/regression teszten. | Automatikus lifecycle | Igen |
| Belső megbízhatósági javítás | Admin | Crate generation fence, pending-open kizárás, kulcsrollback, partial mass-open és recovery settlement. | Main/off-hand, dupla kattintás, több kulcsstack és minden failure mód. | Crate GUI és parancs | Igen, fault injection |
| Belső megbízhatósági javítás | Moderátor | Moderációs mutation gate, szigorú YAML-számolvasás, expiry és scheduler callback gate. | Párhuzamos büntetés és corrupt-state teszt. | Moderációs suite | Igen |
| Belső megbízhatósági javítás | Admin | MOTD generation gate, ikonvalidátor, symlink/path fail-closed és scheduler rejection fallback. | Hibás PNG, túl nagy fájl, symlink és gyors reload. | Server-list ping | Igen |
| Belső megbízhatósági javítás | Admin | Sit seat-entity sweep, lifecycle cleanup és foglalásvédelem. | Reload/disable és retired scheduler teszt. | `/sit` | Igen |
| Belső megbízhatósági javítás | Admin | AFK state thread-safe trackerre került; cleanup és aktivitás-idővonal regresszióval fedett. | Automatikus/kézi váltás és reconnect timeline teszt. | `/afk` | Igen |

## Külső pluginokat érintő változások

| Státusz | Közönség | Plugin | Teendő | Natív megfelelő | Runtime teszt |
|---|---|---|---|---|---|
| Megszűnt | Szervervezető | AxAFKZone és AxAPI | Ne telepítsd; scope törölve. | Globális AFK marad, zóna nélkül | AFK negatív/pozitív teszt |
| Új | Szervervezető, Admin | GSit kiváltási jelölt | GSit csak a sit acceptance után távolítható el. | Sit-only, nem teljes GSit-klón | Kötelező |
| Új | Szervervezető, Admin | CrazyCrates kiváltási jelölt | Csak runtime és fault-injection acceptance után távolítható el. | Natív crate | Kötelező, kritikus |
| Új | Szervervezető, Moderátor | SModeration és InvSee++ kiváltási jelölt | Csak teljes permission/persistence/recovery teszt után távolítható el. | Natív moderáció és inventory admin | Kötelező, kritikus |
| Új | Szervervezető | MiniMOTD kiváltási jelölt | Server-list acceptance után távolítható el. | Natív MOTD | Kötelező |
| Kisebb változást kapott | Szervervezető | TAB | Az IceSMP-hez szükséges subset natív; a teljes upstream-paritás nem cél. Élő TAB-specifikus igényt külön leltározz. | HUD és natív tablista | Kötelező |
| Új | Szervervezető | ICEsmpadditions és FarmProtect kiváltási jelölt | Warden XP, player- és mob-trample kézi teszt után dönthető el az eltávolítás. | `world-tweaks` listener | Kötelező |

Részletes döntési mátrix:
[`EXTERNAL_PLUGIN_STATUS.md`](EXTERNAL_PLUGIN_STATUS.md).

## Deployment előtti teendők

1. Készíts mentést az összes jelenlegi IceSMP state-ről és élő
   konfigurációról; a deployed JAR bundled defaultja nem az élő config.
2. Diffeld az élő override-ot az új configstruktúrával. Külön keresd az
   AFK-zóna-, legacy custom-model-data-, territory- és parkour-kulcsokat.
3. Állíts össze explicit permissionmátrixot játékos, helper, moderátor,
   admin és vezető admin szerepkörre. Az inventory edit, vanish visibility,
   punishment, crate admin, currency és config jog legyen külön.
4. Staging világban készítsd elő és ellenőrizd a crate-helyeket, event
   spawnpointokat, dungeon kapukat/ládákat/bosshelyeket, kompokat,
   teleportpontokat, NPC-kötéseket és questhelyszíneket.
5. Futtasd végig a moderációs, MOTD-, sit-, crate-, Warden-XP- és
   crop-trample acceptance checklistet, beleértve a restartot, reloadot,
   disable-t és fault injectiont.
6. Ellenőrizd a resource pack item modeljeit, signature itemeket, runákat,
   blueprintet, recepteket és minden új content ID-t.
7. A kiváltandó külső pluginokat egyszerre csak stagingen kapcsold ki.
   Productionből csak a hozzájuk tartozó acceptance bizonyíték után
   távolítsd el őket.
8. Figyeld a `moderation-audit.log`, `chat-moderation.log`,
   `crate-openings.log`, escrow/recovery state és startup/disable hibákat.

## Ismert runtime korlátok és bizonytalanságok

| Státusz | Közönség | Korlát | Teendő | Érintett felület | Runtime teszt |
|---|---|---|---|---|---|
| Nem állapítható meg az élő config nélkül | Minden szerepkör | Nem bizonyítható, mely opcionális feature, világ, integráció vagy override aktív jelenleg. | Kérj élő config- és pluginlistát; capability és deployment state maradjon külön. | Minden configos rendszer | Igen |
| Nem állapítható meg az élő config nélkül | Admin | A tényleges permissionkiosztás nem része sem a JAR-nak, sem a source-nak. | Exportáld és auditáld az élő permission plugin állapotát. | Minden védett command/GUI | Igen |
| Belső megbízhatósági javítás | Üzemeltető | A CI sikeres kód- és regressziós jel, de nem bizonyít valódi Paper/Folia, proxy, filesystem, WorldGuard, FancyNpcs vagy külső plugin interakciót. | Futtass staging runtime tesztet productionközeli környezetben. | Teljes plugin | Igen |
| Nem állapítható meg az élő config nélkül | Tartalomkészítő | A bundled content elérhetősége függhet questelőfeltételtől, regisztrációtól, világhelytől, NPC-től és feature flagtől. | Teljes contentbejárás és registry-hivatkozás ellenőrzés. | Quest, event, item, profession | Igen |
| Kisebb változást kapott | Fejlesztő, Üzemeltető | A read-only source inventory öt hiányzó bundled config defaultot, egy configtípus-eltérést és két nem regisztrált legacy command implementációt jelzett. Ezek nem deployed→release funkciók, de rollout előtt külön forrásjegyet igényelnek. | Ne dokumentáld a nem regisztrált legacy osztályokat commandként; a hat configfindingot ellenőrizd. | Config és command bootstrap | Igen |

## Bizonyíték-összefoglaló

- A baseline hash ellenőrzött, a bináris forráscommitja `BINARY_ONLY`.
- A baseline 30 root commandja, 56 root aliasa, 16 permissionje, 14
  GUI-családja és 17 persistent store-ja binárisan feloldott.
- A release bootstrap 68 rootot, 79 root aliast, 43 statikus permissiont és egy
  bundled dinamikus crate-permissiont, 22 GUI-holdert és 34 persistent
  state ownert bizonyít.
- A baseline-ban nincs AFK, moderáció/PM/SocialSpy/vanish/invadmin, MOTD,
  sit, crate, Warden-XP vagy általános crop-trample védelem.
- A release-ben a globális AFK megmarad, a jutalmazó AFK-zóna és minden
  zónajutalom-útvonal hiányzik.
- A lay, crawl, stacking és player/NPC sitting nincs regisztrálva.
- A CI eredménye nem helyettesíti a
  [`RELEASE_ACCEPTANCE_CHECKLIST.md`](RELEASE_ACCEPTANCE_CHECKLIST.md)
  végrehajtását.
