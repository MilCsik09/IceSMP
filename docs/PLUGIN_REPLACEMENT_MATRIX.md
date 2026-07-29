# IceSMP külsőplugin-kiváltási mátrix

**Mérvadó base:** `claude/projekt-audit-u0hkcz` @ `f4e82263d35c645a053eccac09a3ad254a36ed90`
**Ellenőrzés:** 2026-07-28
**Elv:** az `Other/plugins/` csak funkcionális követelményforrás. Nincs production adat, ezért nincs legacy dekóder vagy migráció.

| Terület | IceSMP számára szükséges | Már megvan | Megvalósítandó / igazolandó | Tudatosan nem kell | Állapot | Teszt |
|---|---|---|---|---|---|---|
| SModeration / InvSee++ | punishment history, ban/mute enforcement, PM+SocialSpy, vanish, online inv/ender read+edit, audit, offline TP | report, chat filter/spam; ezen branchen egységes ledger és adminrendszer | valódi Folia restart/reload/disconnect/permission playtest | offline playerdata, legacy DB/config, packet interception | **PARTIAL** | `moderationRegressionTest`, teljes build; manuális runtime még kell |
| AxAFKZone / AxAPI | több AFK-zóna, közös 3D selection, biztonságos jutalmak, adminműveletek | globális AFK, `/afk`, tablista, bossbar, player scheduler tick | külön AFK scope | upstream config/command paritás, IP-limit | **MISSING** | AFK scope-ban |
| GSit | sit/click-to-sit, lifecycle, anyag- és világpolicy, egyszerű lay/crawl | alap `SitManager`, parancs és listener | külön sit/pose scope | korlátlan stack, NMS pose engine, teljes GSit API | **PARTIAL** | sit scope-ban |
| MiniMOTD | idő/random rotáció, eseményprioritás, ikonok, vanish count, reload | natív immutable snapshot, selector, ikonbetöltés és config-hook elkészült | valódi Folia ping/reload/ikon átvételi playtest | proxy/vhost/configkompatibilitás | **READY** kódszinten, runtime feltételes | `motdRegressionTest`, teljes build; manuális runtime még kell |
| CrazyCrates | preview/lista, permission/world/cooldown/key-count/stats, strict rewards | fizikai crate, PDC-kulcs, browser/preview, atomi settlement, recovery, audit és 7 rewardtípus | valódi Folia/fault-injection átvételi teszt | legacy key/config/location, teljes upstream paritás, process-crash exactly-once | **READY** kódszinten, runtime feltételes | `crateRegressionTest`, teljes build; manuális runtime még kell |
| TAB | jelenlegi IceSMP header/footer, név, nametag, sorting, ping, AFK/raid/HUD | natív rendszer megvan; moderation branchen vanish viewer-filter | csak közvetlen integráció és bizonyított bug | condition/layout/proxy/PlaceholderAPI engine | **READY** a jelenlegi igényre | meglévő build + manuális viewer teszt |
| ICEsmpadditions | Warden XP viselkedés | `WorldTweaksListener` | csak kézi eseményteszt | további upstream funkciók | **READY** | build; manuális event teszt |
| FarmProtect | crop-trample védelem | `WorldTweaksListener` | játékos/mob taposás kézi teszt | további upstream funkciók | **READY** | build; manuális event teszt |

## Moderációs reuse-audit

| Szükséges képesség | Meglévő IceSMP-komponens | Újrahasználás módja | Szükséges bővítés | Új komponens szükséges? |
|---|---|---|---|---|
| punishment persistence | `PersistentStore`, `PersistentStoreCoordinator`, `YamlStore` | közvetlen lifecycle + atomikus mentés | egy végleges `moderation-data.yml` codec | csak a domain ledger |
| parancsok/tab completion | dinamikus Paper command-regisztráció és `BasicCommand` minták | azonos diszpécser | scope-specifikus command adapterek | igen, vékony adapterek |
| permissionök | `Permissions` | központi parent/child node-ok | read/edit, vanish-see és akciónkénti node-ok | nem |
| üzenetek | `MessageManager` | `messages/moderation.yml` | új message group | nem |
| GUI | `GuiUtil`, holder/listener minták | közvetlen használat | moderációs holder és routing listener | igen, csak domain GUI |
| state cleanup | `PlayerStateCleanup`, központi listener | regisztráció a meglévő owner-listába | reconnect escrow hook | nem |
| Folia ownership | entity/global/async scheduler minták | cél- és admin-player scheduler hop | inventory transfer single-claim gate | csak tesztelhető domain gate |
| tab/MOTD | `TablistManager`, `MotdListener` | vanish state integráció | nézőspecifikus filter; MOTD a külön scope-ban | nem |
| report | `ReportManager` | GUI route a meglévő `/reports` felé | nincs | nem |

## MOTD reuse-audit

| Szükséges képesség | Meglévő IceSMP-komponens | Újrahasználás módja | Szükséges bővítés | Új komponens szükséges? |
|---|---|---|---|---|
| server-list handler | `MotdListener` | az egyetlen natív ping owner maradt | immutable snapshot, ikonmódok, strict reload | nem új listener |
| config és reload | `ConfigManager`, `/icesmp reload`, config command/GUI | ugyanazon override+reload út | célzott `motd.*` hook | nem |
| vanished count | moderációs `VanishManager` | thread-safe online UUID-cache közvetlen olvasása | explicit include/exclude getter | nem; külön vanish state tilos |
| eseményállapot | `BloodMoonManager`, `WorldBossManager`, `SeasonManager` | meglévő async-safe getterek | tiszta prioritás-selector | csak dependency-free selector |
| scheduler | Paper async + global-region scheduler minta, moderációs `SchedulerCallbackGate` | fájl/PNG async, Bukkit icon cache global | generation + single-winner gate | csak MOTD lifecycle-adapter |
| admin GUI | `ConfigMenuGUI` + listener | meglévő kategória/override mechanizmus | MOTD-kategória | nem |
| teszt lifecycle | Gradle `regressionTest` source set | új JavaExec a meglévő `check` részeként | selector, strict scalar, placeholder, secure icon és generation teszt | nem framework |
## Crate reuse-audit és garanciahatár

| Szükséges képesség | Meglévő IceSMP-komponens | Megoldás | Tudatos korlát |
|---|---|---|---|
| persistence | `PersistentStore`, `YamlStore`, coordinator | schema 2 opening/recovery rekord és atomikus save | process-kill exactly-once nincs |
| currency | `CurrencyManager` | durable batch + exact snapshot rollback token | stale/nem kompenzálható állapot kézi audit |
| command reward | global-region scheduler | submit + futás + `dispatchCommand` eredmény ellenőrzött | már lefutott command nem visszafordítható |
| access policy | `Permissions`, config snapshot | közös browser/info/preview/completion/opening gate | upstream permission-paritás nem cél |
| scheduler | moderációs single-winner minta | exception/null/retirement fallback + task lease | valódi Folia fault injection még kell |
| lifecycle | `PlayerStateCleanup`, core disable | active grant tracking, bounded drain, recovery osztályozás | crash közbeni distributed transaction nincs |
