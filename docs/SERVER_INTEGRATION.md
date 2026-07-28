# Éles szerver — plugin-integráció és ütközések

A master branch `Other/plugins/` mappájában lévő éles szerver-dump elemzése alapján.
(Frissítve az ütközések felszámolásakor.)

## 1. TAB → IceSMP natív tablist — runtime ellenőrzés után kivezethető

**Új állapot:** a teljes TAB-funkcionalitás, amit a szerver használt, natívan megy
(`managers/TablistManager` + `HudManager` + `config/tablist.yml`):

| TAB-funkció | Natív megfelelő |
|---|---|
| Header/footer (animált, glyph) | `tablist.header-footer` — `{anim:<név>}` / `{player}` / `{ping}` / `{online}` / `{max}` tokenek |
| Tab-név (LP-prefix + frakció-szín + suffix) | `tablist.tab-names` — diff-elt (villogásmentes), LuckPerms-hídon át |
| Nametag + rendezés (owner→…→default, ABC) | `tablist.nametags` + `tablist.sorting.group-order` — nézőnkénti scoreboard-teamek |
| Ping-oszlop | `tablist.playerlist-ping` |
| Scoreboard-oldalsáv (IceSMP-adatokkal) | `hud.sidebar-enabled: true` + `hud.sidebar.title` — TAB-dizájn portolva (elválasztó-animáció, small-caps címkék) |
| Animációk (animations.yml) | `tablist.animations.<név>` — időalapú frame-váltás, minimum 250 ms (a TAB 50 ms-os marquee-i ritkított frame-ekkel portolva) |

**Átállási kapu:** a natív réteg a jelenlegi IceSMP-igényt lefedi, de a TAB jar csak a
`PLAYTEST.md` szerinti viewer-, sorting-, AFK-, raid-, reload- és permissionteszt után távolítható el.
A párhuzamos működés nem támogatott: a két rendszer ugyanazokat a player-list/scoreboard adatokat
kezeli. A ㍿/㍐ glyph-ek a resource packből jönnek; resource pack nélküli teszthez a configban
sima szövegre cserélhetők.

**PlaceholderAPI-híd megmarad** más pluginok kedvéért (`integration/IceSMPPlaceholders`):

| Placeholder | Érték |
|---|---|
| `%icesmp_faction%` / `%icesmp_faction_id%` | frakció display-név / stabil enum-id |
| `%icesmp_class%`, `%icesmp_class_level%` | kaszt és szint |
| `%icesmp_balance%` | IceSMP-valutaegyenleg (formázva) |
| `%icesmp_resource%`, `_max`, `_percent`, `_name`, `_bar` | Erő-forrás (Mana/Düh/…) |
| `%icesmp_party_size%`, `%icesmp_party_1..5%` | party-tagok soronként |
| `%icesmp_faction_color%` | a frakció puszta színkódja (`§c`/`§9`/`§7`/`§8`) — nametag/tab-prefix végére fűzve |
| `%icesmp_event%` | az aktív világesemények egy sorban (max 2 név + `+N`) |

*(A tab-nevek frakció-színét natívan a TablistManager adja — a fenti placeholderek csak
külső megjelenítőknek, pl. BlueMap/Discord-hidaknak kellenek.)*

## 2. SimpleClaimSystem ↔ IceSMP claim — PARANCS-ÜTKÖZÉS

Mindkét plugin regisztrálja a `/claim` parancsot; amelyik később tölt be, az nyer, a másiké
némán elérhetetlen. Az IceSMP `claims.enabled: true` óta a natív claim-rendszer él —
**a SimpleClaimSystem.jar eltávolítása javasolt** az éles szerverről. (A SCS gazdagabb
GUI/claim-piac funkcióit a claim-rework roadmap-tétel fedheti le.)

## 3. LuckPermsChatFormatterFolia ↔ IceSMP chat-formázó — DUPLA FORMÁZÁS

Az IceSMP `chat.format-enabled: true` ÉS a LuckPermsChatFormatterFolia is telepítve van —
a general.yml kommentje szerint a natív formázó pont ezt a plugint váltja ki.
**A LuckPermsChatFormatterFolia-1.1.1.jar eltávolítása javasolt.**

## 4. FancyNpcs — explicit NPC-kötések (`/npcbind`)

Az NPC-integráció (küldetés-adó, bolt, bankár, valutaváltó) mostantól nem az NPC saját
nevéből (`giver-npc:` / `faction-shops.<név>`) következik: `/npcbind <npc> quest|shop|bank|exchange|clear`
paranccsal bármelyik FancyNpcs-NPC kötelezővé tehető. A bankár/valutaváltó kötés a
**meglévő bank menüt** nyitja (`/menu` → Bank & Pénz gombjai) — ezért **a bankár-NPC-t
tedd a frakció fővárosába**: a bank-parancsok (`deposit`/`withdraw`/`exchange`) tényleges
végrehajtása a `banking.capital-only` config-kapun megy át (alapból `true`), ami csak
fővárosban engedi át a műveletet; ha ezt a korlátozást fel akarod oldani (pl. tetszőleges
helyen működő bankár), állítsd `false`-ra a `config/economy.yml`-ben. Kötés nélküli NPC-k
változatlanul a régi név-alapú logikával működnek (teljes visszafele-kompatibilitás).

## 5. Crossover-ötletek (top 3)

1. **AuMenus** — a menü-fájljai még a gyári példák; IceSMP-parancsokra kötve (console:
   `icesmp ...` akciók + `%icesmp_...%` lore-placeholderek) staff Java nélkül építhet új
   front-endeket, a gameplay-logika a parancsokban marad (agent GUI-szabály).
2. **Natív AFK-zónák** — a közös claim-kompatibilis 3D selection, a meglévő `AfkManager`,
   `CurrencyManager`, `MessageManager` és Folia scheduler-minták együtt biztosítják a célzott
   AFK-zóna viselkedést. Az AxAFKZone/AxAPI eltávolítása csak a dedikált runtime playtest után engedett.
3. **VillagerTradeEdit** — szakma-specifikus vendor-villagerek (recept/valuta-árak) a
   profession/market rendszerhez, egyedi trade-GUI kód nélkül.

Semleges (nem ütközik): GSit, ImageFrame, CoreProtect, GrimAC, ViaVersion, voicechat,
FAWE/goBrush/VoxelSniper, SModeration, AxiomPaper.
Már integrált: LibsDisguises, PlaceholderAPI, FancyNpcs, WorldGuard, LuckPerms.

## 5.1 Plugin-leépítési kapuk

A repositoryban található natív alap vagy elkészült branch önmagában nem jogosít külső jar
eltávolítására. A mérvadó állapotot a `docs/PLUGIN_REPLACEMENT_MATRIX.md`, az adott draft PR
remote CI-je és a `PLAYTEST.md` runtime kapui együtt adják. A szerverhez nincs production vagy
legacy adat, ezért migrációs lépések nem részei ennek a programnak.

| Külső plugin | Natív IceSMP-állapot | Eltávolítási kapu |
|---|---|---|
| **TAB** | a jelenleg szükséges tablist/HUD funkciók natívan megvannak | viewer-, sorting-, AFK/raid-, reload- és permission-playtest |
| **ICEsmpadditions** | `WorldTweaksListener` Warden-XP viselkedés | kézi Warden death/XP event teszt |
| **FarmProtect** | `WorldTweaksListener` crop-trample védelem | játékos- és mob-taposás kézi teszt |
| **SModeration / InvSee++** | a `feature/native-moderation-suite` draft PR egységes ledgerrel, ban gate-tel, SocialSpyjal, vanish-sel és online inv/ender read-edit móddal készül | zöld remote CI + dokumentált valódi Folia restart/reload/disconnect/permission/fault-injection teszt |
| **AxAFKZone / AxAPI** | a `feature/native-afk-zones` scope több zónát, közös 3D selectiont és validált rewardokat ad | zöld remote CI + valódi Folia zone/reload/restart/reward/full-inventory/permission teszt |
| **GSit** | meglévő alap `SitManager`; completion külön scope | sit/pose draft PR, zöld CI és lifecycle playtest |
| **MiniMOTD** | meglévő alap `MotdListener`; completion külön scope | MOTD draft PR, zöld CI, ikon/reload/event/vanish count playtest |
| **CrazyCrates** | meglévő fizikai crate/PDC/weighted-reward alap; completion külön scope | crate draft PR, zöld CI, key/cooldown/full-inventory/stat/restart playtest |

A SimpleClaimSystem és LuckPermsChatFormatterFolia korábbi kivezetési döntése külön, már létező
natív rendszerekhez tartozik. CoreProtect, GrimAC, WorldEdit/FAWE/WorldGuard, ViaVersion,
ProtocolLib/packetevents, voicechat, LuckPerms, PlaceholderAPI, LibsDisguises, FancyNpcs és a
szerverüzemeltetési eszközök nem részei ennek a kiváltási programnak.

## 6. Vanilla Locator Bar (1.21.6+) — „pötty az XP-sávon"

Az XP-sávon forgáskor megjelenő, játékost jelző pötty NEM plugin: a Minecraft 1.21.6-ban
bevezetett **Locator Bar** (a közeli játékosok irány-jelzője, az XP-sáv helyén).

**Az IceSMP ezt már magától kikapcsolja:** indításkor és minden világ-betöltéskor a `locatorBar`
gamerule-t `false`-ra állítja minden világon (`config/general.yml` → `settings.disable-locator-bar`,
alapból `true`). Ha mégis meg akarod hagyni a vanilla viselkedést, állítsd `false`-ra a config-ban.
Kézi kikapcsolás továbbra is: `/gamerule locatorBar false` (világonként).
