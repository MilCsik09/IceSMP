# Éles szerver — plugin-integráció és ütközések

A master branch `Other/plugins/` mappájában lévő éles szerver-dump elemzése alapján.
(Frissítve az ütközések felszámolásakor.)

## 1. TAB → IceSMP natív tablist — A TAB PLUGIN LESZEDHETŐ

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

**Éles átállás:** (1) frissítsd a plugint; (2) töröld a `TAB v6.0.0.jar`-t; (3) kész — a
repo-defaultok már a natív rétegre állnak (`hud.sidebar-enabled: true`,
`tablist.enabled: true`). Ha a TAB fent marad, a konzol induláskor figyelmeztet, és a két
rendszer a neveken/teameken verekedni fog. A ㍿/㍐ glyph-ek a resource packből jönnek —
RP nélküli teszthez cseréld őket a configban sima szövegre.

**PlaceholderAPI-híd megmarad** más pluginok kedvéért (`integration/IceSMPPlaceholders`):

| Placeholder | Érték |
|---|---|
| `%icesmp_faction%` / `%icesmp_faction_id%` | frakció display-név / stabil enum-id |
| `%icesmp_class%`, `%icesmp_class_level%` | kaszt és szint |
| `%icesmp_balance%` | IceSMP-valutaegyenleg (formázva) |
| `%icesmp_resource%`, `_max`, `_percent`, `_name`, `_bar` | Erő-forrás (Mana/Düh/…) |
| `%icesmp_party_size%`, `%icesmp_party_1..5%` | party-tagok soronként |

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
   front-endeket, a gameplay-logika a parancsokban marad (CLAUDE.md GUI-szabály).
2. **AxAFKZone** — AFK-zóna jelzés átvétele: AFK-zónában az IceSMP passzív XP/erőforrás-regen
   szüneteltethető, hogy az AFK-jutalom ne legyen párhuzamos power-leveling exploit.
3. **VillagerTradeEdit** — szakma-specifikus vendor-villagerek (recept/valuta-árak) a
   profession/market rendszerhez, egyedi trade-GUI kód nélkül.

Semleges (nem ütközik): GSit, ImageFrame, FarmProtect, CoreProtect, GrimAC, ViaVersion,
minimotd, voicechat, FAWE/goBrush/VoxelSniper, SModeration, AxiomPaper.
Már integrált: LibsDisguises, PlaceholderAPI, FancyNpcs, WorldGuard, LuckPerms.
Megjegyzés: az `ICEsmpadditions.jar` (WardenDeathListener) érdemes lenne beolvasztani a fő
pluginba, hogy ne legyen kósza extra jar.

## 6. Vanilla Locator Bar (1.21.6+) — „pötty az XP-sávon"

Az XP-sávon forgáskor megjelenő, játékost jelző pötty NEM plugin: a Minecraft 1.21.6-ban
bevezetett **Locator Bar** (a közeli játékosok irány-jelzője, az XP-sáv helyén).

**Az IceSMP ezt már magától kikapcsolja:** indításkor és minden világ-betöltéskor a `locatorBar`
gamerule-t `false`-ra állítja minden világon (`config/general.yml` → `settings.disable-locator-bar`,
alapból `true`). Ha mégis meg akarod hagyni a vanilla viselkedést, állítsd `false`-ra a config-ban.
Kézi kikapcsolás továbbra is: `/gamerule locatorBar false` (világonként).
