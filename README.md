# IceSMP

Az **IceSMP** egy Folia-alapú Minecraft plugin (1.21.11, Paper API kompatibilis), amely egy
fantasy "királyságos" SMP szerver teljes játékmenet-rendszerét adja: frakciók, kasztok és
specializációk, talentek, szakmák, Lélekkapocs-alapú képességek, legendás relikviák,
dinamikus gazdaság, távolság-alapú nehézség és frakcióterületek.

> 🎮 **Játékos tájékoztató:** [PLAYER_GUIDE.md](PLAYER_GUIDE.md) — mit hogyan használj a játékban
> (frakciók, kasztok, spellek, talentek, szakmák, gazdaság, világesemények) + mi a WIP.
> A részletes, oldalankénti kézikönyv (minden spell-lel): [docs/player-guide/](docs/player-guide/README.md)
> 🏗️ **Architektúra / fejlesztői referencia:** [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) —
> modulok, minták, Folia-szálkezelési szabályok, perzisztencia.
> 🧪 **Playtest kézikönyv:** [PLAYTEST.md](PLAYTEST.md) — mit és hogyan teszteljenek a teszterek.
> 🗺️ **Tervek:** [ROADMAP.md](ROADMAP.md)

---

## Mit tud a plugin? (közérthetően)

### ⚔️ Frakciók — négy oldal, négy sors

| Frakció | Belépés | Passzív bónusz |
|---|---|---|
| 🔴 **Piros** | `/faction join red` | Immunis a tűz, láva és forró blokkok sebzésére |
| 🔵 **Kék** | `/faction join blue` | Immunis a fagyásra **és a fulladásra**, lassabban éhezik |
| ⚪ **Semleges** | `/faction join neutral` (alapértelmezett) | **Nincs zuhanás-sebzés**; a nem-ellenséges mobok **és endermanök** békén hagyják; adómentes |
| ⚫ **Sötét** | `/faction join dark` — **csak bűnösként!** | Immunis a wither-sebzésre; az élőhalottak nem támadják |

**A Sötét frakció különleges:** csak **bűnösként (sinner)** léphetsz be — bűnössé a
tetteid tesznek (lásd lentebb), a belépés pedig kétlépcsős megerősítést kér. Belépéskor megköttetik a **sötét paktum** — onnantól a
bűnös jelölés **soha többé nem törölhető le**, akkor sem, ha később elhagyod a frakciót.
Bűn a **gyilkosság** (+1), az **árulás** (saját frakciótárs megölése, +2) és a **lopás**
(másik frakció területén konténer-fosztás, +1); 4 bűnnél automatikus a száműzetés. Raid
alatt a hadviselők közti ölés és zsákmányolás nem bűn.

### 🧙 Kasztok és specializációk

**13 kaszt** választható a profil GUI-ból (egy kaszt / játékos; a választás végleges, adminnal
reseteltethető: `/class admin resetclass`):

- **Varázsló** — elemi és kontroll mágia
- **Harcos** — közelharci erő és kitartás
- **Íjász** — távolsági harc és mozgékonyság
- **Orgyilkos** — lopakodás és meglepetés
- **Druida** — alakváltó természet-mágia (harc / kontroll / tank / gyógyítás)
- **Paplovag** — szent harc, védelem és gyógyítás
- **Halállovag** — rúna-mágia, vér és fagy
- **Sámán** — elemek, totemek, gyógyítás
- **Szerzetes** — gyors csi-közelharc és gyógyítás
- **Pap** — szent és árny mágia
- **Boszorkánymester** — átkok, démonok, pusztító tűz
- **Démonvadász** — mozgékony démoni harc
- **Sárkányidéző** — sárkány-eszencia: perzselés és gyógyítás

Minden kasztnak saját **erőforrása** (Erő-csík a HUD-on) van: a **legtöbb** képesség ezt fogyasztja
(idővel visszatöltődik; üres csíknál a spell nem sül el). **Hibrid** rendszer: a vér-mágia életet,
a nagy rituálék XP-t, a nehéz fizikai képességek éhséget kérnek — mindegyik a hozzá illő költséget.

A kasztok **mob ölésből** kapnak XP-t (minél messzebb merészkedsz a spawntól, annál erősebb
mobok jönnek — és annál több XP-t adnak). A szintek WoW-mintára **egyre több XP-be
kerülnek**, a képességek (spellek) pedig a szintekkel **automatikusan feloldódnak**.

A 25. szinttől **specializálódhatsz** (`/spec choose <id>`), és a legerősebb képességek
csak így érhetők el. Összesen **35 specializáció** van — a spec dönti el a szerepedet (DPS /
caster / tank / gyógyító), így minden szerep lefedett. A döntés nem örök: a `/spec respec`
paranccsal **frakcióvalutáért visszaválthatod** a speced (a spec-talentjeid pontjai ilyenkor
visszatérülnek):

| Kaszt | Specializációk |
|---|---|
| Varázsló | 🌊 Elementalista • 💀 **Nekromanta** (csak bűnösként, Sötét frakcióval!) |
| Harcos | 🩸 Berserker • 🛡 Védelmező |
| Íjász | 🎯 Mesterlövész • 🐺 Vadmester |
| Orgyilkos | ☠ Méregkeverő • 👻 Fantom • 🦠 **Pestishozó** (csak bűnösként, Sötét frakcióval!) |
| Druida | 🐾 Vadőr • 🌙 Holdjós • 🌳 Védelmező • 💚 Helyreállító |
| Paplovag | ☀️ Szentlélek • ⚖️ Megtorló • 🛡 Védő |
| Halállovag | 🩸 Vérlovag • ❄️ Fagylovag • 🧟 **Szentségtelen** (csak bűnösként, Sötét frakcióval!) |
| Sámán | ⚡ Elemi • 🔨 Erősítő • 🌊 Hullámhívó |
| Szerzetes | 💨 Szélfutó • 🍺 Sörfőző • 🌫️ Ködszövő |
| Pap | 🙏 Fegyelem • 🌑 Árnyék • 🦴 **Csontpap** (csak bűnösként, Sötét frakcióval!) |
| Boszorkánymester | 🍂 Átok • 🔥 Pusztítás • 👁 **Demonológus** (csak bűnösként, Sötét frakcióval!) |
| Démonvadász | 💥 Tombolás • 🛡 Bosszú |
| Sárkányidéző | 🔥 Perzselés • 💧 Megőrzés |

### ✨ Lélekkapocs

Minden kaszt a saját, tematikus **Lélekkapocsával** használja a képességeit
(a kasztválasztó GUI-ból bármikor igényelhető, ha elveszett; admin: `/job givecatalyst`):

| Kaszt | Lélekkapocs |
|---|---|
| Varázsló | 📖 **Caldesterai Rúnakódex** (bűvölt könyv) |
| Harcos | 📯 **Sárkánykirály Kürtje** (kecskekürt) |
| Íjász | 🎒 **Soleil Vadásztarsolya** (nyúlbőr) |
| Orgyilkos | 🪨 **Homály-szilánk** (kovakő) |
| Druida | 🌱 **Aetrinita Sarja** (tölgycsemete) |
| Paplovag | 🔔 **Hajnaltűz Harangja** (harang) |
| Halállovag | 💀 **Néma Rúnakoponya** (wither-koponya) |
| Sámán | 🪬 **Ősvihar Totemje** (mentő totem) |
| Szerzetes | 🎍 **Élet Ága** (bambusz) |
| Pap | 🕯️ **Asterlayna Gyertyája** (fehér gyertya) |
| Boszorkánymester | 🏮 **Kárhozat Lámpása** (lélek-lámpás) |
| Démonvadász | 👁️ **Hasadék Szeme** (ender-szem) |
| Sárkányidéző | 🐲 **Sárkányvér-fiola** (sárkánylehelet) |

- **Jobb katt** — kiválasztott képesség elsütése
- **Lopakodás + ütés (bal katt)** — váltás a feloldott képességek között, kaszt-specifikus
  hanggal (lapozás / kürt / számszeríj / suttogó szél) és a képesség nevét + költségét
  mutató action bar kijelzéssel
- **Közelharci kasztoknak** (Harcos, Paplovag, Halállovag, Szerzetes, Démonvadász) a kézben tartott
  **kard/balta is Lélekkapocs** — nem kell tárgyat váltani harc közben.

Minden képességnek költsége (hibrid: a legtöbb az osztály-erőforrás, a vér/rituálé/fizikai spellek HP/XP/éhség) és visszatöltési ideje van. Több mint 390 képesség van a
rendszerben — kasztonként és specializációnként legalább 10 —, és **minden kaszt és specializáció saját, egyedi képességeket tanul**: nincs
átfedés a kasztok között. A képességek **ereje a kaszt-szinttel és talentekkel skálázódik**
(a spell-mesterség fölött), a **balansz pedig config-vezérelt** (`spells-balance.yml`).

### 🌟 Talentek

A kaszt- és szakmaszintek **talentpontokat** termelnek (alapból 5 kasztszintenként, illetve
összesen 10 szakmaszintenként 1 pont). A pontokat passzív erősítésekre költheted (`/talent`):
több élet, gyorsabb mozgás, nagyobb sebzés, vagy extra XP-szerzés. WoW-mintára vannak
**kaszt-, specializáció- és szakma-kötött talentek** is (pl. a Berserker „Brutalitás"-a
vagy a Bányász „Állóképesség"-e) — ezeket csak a feltételt teljesítő játékos látja és
használhatja, respec után pedig a pontjaik automatikusan visszatérülnek.

### ⚒️ Szakmák — WoW-mintára

Két fő szakmád lehet: **egy gyűjtögető és egy készítő** (`/profession join <szakma>`),
a másodlagos szakmák pedig mindenkinek maguktól fejlődnek:

| Kategória | Szakmák |
|---|---|
| 🧺 **Gyűjtögető** (1 választható) | ⛏ Bányász • 🌿 Gyógynövényész • 🪓 Favágó |
| 🔨 **Készítő** (1 választható) | ⚒ Kovács • ⚗ Alkimista • ✨ Bűvölő |
| 🎣 **Másodlagos** (mindenkié) | 🐟 Halász • 🍲 Szakács |

A párosítások a WoW logikáját követik (Bányász→Kovács, Gyógynövényész→Alkimista,
Favágó→Bűvölő). Minden szakma a tényleges tevékenységből fejlődik: bányászat, aratás és
virágszedés, favágás, páncélcraft és smithing, bűvölőasztal, főzetek kivétele a
főzőállványból, horgászat, étel sütése. A szintek **egyre több XP-be kerülnek**
(progresszív görbe), és a XP szakmánként megmarad akkor is, ha az admin szakmát vált neked.

A **netherite felszerelést csak a 25+ szintű Kovács** készítheti el! A 25. szinttől
minden szakma specializálódhat — szakmánként 2 irány (pl. Fegyverkovács / Páncélkovács,
Főzetmester / Transzmutátor, Séf / Hentes) **valódi mechanikai passzívokkal** (dupladrop,
XP-bónusz, ital-hosszabbítás).

**Recept-könyv** (`/profession recipes`): WoW-szerű, több mint **400 recept** — a tanultak
zölddel, a zároltak szürkén, egy kattintással craftolható. A receptek **szintre** vagy
**tervrajzból** (Knowledge Book — NPC-bolt / mob-drop / admin) nyílnak meg. Egyes szakmák
**egyedi köztes alapanyagot** gyártanak (pl. *Tiszta Vasesszencia*, *Rúnapor*), amit a
magasabb receptek igényelnek — ezek nem használhatók normál módon, csak a recept-könyvben.

### ✨ Tárgy-raritás és loot (rolled itemek)

Minden craftolt felszerelés és mob-loot **véletlen raritást** kap egy létrán (**Ócska → Közönséges
→ Nem mindennapi → Ritka → Epikus → Legendás → Ereklye**) + random **attribútum-affixeket** — mint
WoW-ban / Terraria reforge-ban. A raritás a nevet, a színt, az affixek számát és erejét adja; az
**Ócska csak átkos (negatív) affixet** kap.

- 🧑‍🏭 **Szakma-craft:** erős alap, megtervezett névvel.
- 👹 **Mob-loot:** súlyozott loot-tábláról sokféle tárgy — rolled felszerelés (random névvel, akár
  átkos), nyersanyagok, és **csak-mobból-eső egyedi alapanyagok** (*Vad Esszencia*, *Szörny Mag*…),
  amiket a szakma-receptek igényelnek. Szakma-craftolt tárgy sosem esik mobból.
- 🐉 **Világboss / nehéz event loot:** a legmagasabb raritások + boss-only *Fekete Villám Szilánk*.

### 🗡 Relikviák és rituálé-oltárok

Egyedi, legendás tárgyak (pl. **A Mételytépő** harci fejsze, amely megbélyegzi a
bűnösöket). Szabályaik:

- Egy relikviából **csak egy létezhet** a szerveren.
- **14 nap inaktivitás** után a relikvia füstként elenyészik, és újra megszerezhetővé válik.
- A négy **frakció-elytra** (Főnix-/Zúzmara-/Vándor-/Csontszárny) **rituálé-oltáron** idézhető meg.
- Az oltárok **több-blokkos szentélyek** (5×5 alapzat + saroktornyok), és nem csak relikviát adnak:
  **Feloldozás** (bűn-tisztítás), **Hazatérés-kő** (fővárosba teleport), és **mind a 13 kasztnak
  saját szentélye** tematikus buffal.

### 💰 Gazdaság — dinamikus árfolyammal

Minden frakciónak saját valutája van (Piros / Kék / Semleges / Csontveret), amelyek fizikai
itemként és banki egyenlegként is léteznek:

- `/bank deposit` — a nálad lévő tokenek bankba helyezése; `/bank withdraw` — kivét itemként
  (**a banki ügyintézés alapból csak a fővárosokban** működik — KP-gazdaság)
- `/currency exchange` — valutaváltás (kattintós **Valutaváltó** a `/menu` → Bank & Pénz alatt is);
  a `/currency pay` közvetlen utalás alapból **ki van kapcsolva** (a játékos–játékos csere item-alapú)
- **Az árfolyam élő:** egy valuta értékét a szerveren lévő összmennyisége határozza meg.
  Ha egy frakció elárasztja a gazdaságot a pénzével, az inflálódik és kevesebbet ér.
  Az aktuális árfolyamokat a `/currency rates`, illetve a fővárosokba lerakható **árfolyam-
  hologramok** (`/exchangeboard`) mutatják.
- **Piactér** (`/market`): játékosok közti adásvétel banki egyenlegből, eladási díjjal. A
  vételárat a **frakció-reputáció** is módosítja (ellenségtől felár, szövetségestől kedvezmény).
- **Aukciósház** (`/market auction`): licitálós eladás lejárattal — a licit a bankból zárolódik,
  túllicitálásnál automatikusan visszajár; a lejárt aukció nyertese offline is megkapja a
  tárgyat (belépéskor vagy `/market claim`).
- **Adomány-láda** (`/adomany`): szerver-szintű, ár nélküli közösségi ajándéktár — bárki
  adományozhat egy tárgyat (`/adomany add`, a kézben tartott stack), és bárki elveheti a
  böngésző GUI-ból, ingyen.

### 🧟 Távolság-alapú nehézség

A spawntól távolodva a világ veszélyesebbé válik: minden 1000 blokk +1 mob szint
(`[Lvl X] Zombi` névvel, több élettel és sebzéssel). A spawner-/parancs-spawnolt mobok nem
skálázódnak, így a farmok nem törhetik el a rendszert.

### 🏰 Frakcióterületek

Adminok **kör-** vagy **poligon-zónákat** jelölhetnek ki (`/territory circle`, illetve a fal
mentén bejárt pontokból `/territory pos` → `/territory create`). Hat **zónatípus** védi a
térképet: **frakcióterület** (csak a frakció tagjai építhetnek, de ide a játékosok
**claimelhetnek** is), a **védett frakcióterület**, **védett város** és **főváros**,
ahol **senki** sem építhet és claimelni sem lehet, továbbá a **Kárhozat Kapuja**
(törvényen kívüli PvP-zóna) és a **kazamata** (kulcs-kapus, emelt mob-szintű dungeon). A határátlépést a játékosok típusfüggő action
bar üzenetben látják ("✦ Piros főváros ✦", "⛨ védett város ⛨"). Zónatípusonként **külön
állítható**, mi tiltott (`territory.protection.rules`): **build**, **interact**, **pvp**,
**explosions**, **fire** — így egy védett város teljes biztonságos zóna, egy frakcióváros viszont
csak a nem-tagok építését tiltja. Megkerülő jogok: `icesmp.admin.territory.bypass` (minden) és
`icesmp.territory.builder` (építő-jog védett zónában is).

### 📋 Natív HUD és tablist (TAB plugin nélkül)

A teljes megjelenítő-réteg beépített: **scoreboard-oldalsáv** (frakció, kaszt, Erő-csík,
esemény, valuta, party-frame-ek, animált elválasztókkal), **tablist header/footer**
animációkkal, **LP-prefixes + frakció-színes tab-nevek**, **fej fölötti nametag +
rang-alapú rendezés** és **ping-oszlop** — mind villogásmentes (csak változáskor megy ki
csomag) és Folia-korrekt. Beállítás: `config/tablist.yml` + `general.yml` → `hud.*`;
külső tablist-plugin (TAB) nem szükséges.

---

## Parancsok (gyorsreferencia)

| Parancs | Aliasok | Mire való |
|---|---|---|
| `/profile` | `karakter`, `char`, `status` | Profil megnyitása (frakció, egyenlegek, kasztválasztás) |
| `/faction join/leave` | `f` | Frakcióba lépés / kilépés |
| `/spec list/choose/info` | `specialization`, `specializacio` | Specializációk |
| `/talent`, `/talent spend` | `talents`, `talentfa` | Talentek megtekintése és fejlesztése |
| `/profession join/info/list` | `prof`, `szakma` | Szakma választás és állapot |
| `/bank balance/deposit/withdraw` | `wallet`, `vault` | Banki műveletek |
| `/currency balance/pay/exchange/rates` | `money`, `eco` | Valutaműveletek és árfolyamok |
| `/market`, `/market sell/auction/claim/cancel/search` | `piac`, `ah` | Piactér + aukciósház (reputáció-árazással) |
| `/adomany`, `/adomany add` | `donate`, `adomanylada` | Közösségi adomány-láda (ár nélküli ajándékozás) |
| `/faction king vote/tax/raid` | `f` | Királyválasztás, adókulcs, raid (király) |
| `/souls`, `/souls champion` | `soul`, `lelek` | Nekromanta lélekszilánk + bajnok |
| `/quest …` | `quests`, `kuldetes` | Küldetések |
| `/events …` | `event`, `esemeny` | Világesemények |
| `/exchangeboard place/remove` | `ratesboard`, `arfolyamtabla` | Árfolyamtábla (admin) |
| `/class …` | `kaszt`, `job` | Kaszt adminisztráció (admin) |
| `/relic list/give` | `relics`, `relikvia` | Relikvia adminisztráció (admin) |
| `/sinner <játékos> set/clear/add/status` | — | Bűnös státusz kezelése (admin) |
| `/territory …` | `terulet` | Területek kijelölése (admin) |
| `/icesmp reload` | `ismp` | Konfiguráció újratöltése (admin) |
| `/icesmp config get/set/unset/list/find` | `ismp config` | Bármely config-kulcs ingame lekérése/felülbírálása (admin) |

A teljes parancs-referencia: [docs/player-guide/14-parancsok.md](docs/player-guide/14-parancsok.md);
a jogosultság-node-ok listája a [PLAYTEST.md](PLAYTEST.md)-ben.

---

## Telepítés és build

**Követelmények:** Java 21 (Temurin ajánlott), Folia/Luminol 1.21.11 szerver.

```bash
./gradlew build      # plugin jar a build/libs alá
./gradlew runServer  # helyi tesztszerver indítása (run/ mappa)
```

A plugin első indításkor létrehozza a `config.yml` és `messages.yml` fájlokat a plugin
adatmappájában — minden játékmeneti érték és üzenet ott testreszabható.

## Projekt állapota

- **API:** Folia 1.21.11 (Paper API kompatibilis) • **Nyelv:** Java 21 • **Build:** Gradle
- **Folia-kompatibilis:** `folia-supported: true`; minden feladat régió-/entitás-ütemezőn fut
- **Soft-dependenciák:** PlaceholderAPI (`%icesmp_...%` placeholderek, pl. TAB-hoz),
  LibsDisguises (Druida-formák vizuálja), FancyNpcs (NPC-s kaszt-mester próbák),
  WorldGuard (a meteor/kincs események kerülik a WG-régiókat), LuckPerms (chat
  prefix/suffix a natív chat-formázóban) — mindegyik nélkül is teljes értékűen fut
- Minden fő rendszer elkészült: frakciók (passzívokkal), 13 kaszt / 35 spec / 390+ spell,
  hibrid erőforrás-költség, talentek, szakmák, gazdaság + piac, relikviák + rituálék,
  világesemények (vérhold / világbossok / inváziók), király/raid/szezon, küldetések, pet-rendszer.
- A hátralévő irányok: [ROADMAP.md](ROADMAP.md)
