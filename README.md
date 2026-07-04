# IceSMP

Az **IceSMP** egy Folia-alapú Minecraft plugin (1.21.11, Paper API kompatibilis), amely egy
fantasy "királyságos" SMP szerver teljes játékmenet-rendszerét adja: frakciók, kasztok és
specializációk, talentek, szakmák, katalizátor-alapú képességek, legendás relikviák,
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

**A Sötét frakció különleges:** csak az léphet be, akit a Mételytépő relikvia (vagy admin)
**bűnössé (sinner)** bélyegzett. Belépéskor megköttetik a **sötét paktum** — onnantól a
bűnös jelölés **soha többé nem törölhető le**, akkor sem, ha később elhagyod a frakciót.

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
csak így érhetők el. Összesen **31 specializáció** van — a spec dönti el a szerepedet (DPS /
caster / tank / gyógyító), így minden szerep lefedett. A döntés nem örök: a `/spec respec`
paranccsal **frakcióvalutáért visszaválthatod** a speced (a spec-talentjeid pontjai ilyenkor
visszatérülnek):

| Kaszt | Specializációk |
|---|---|
| Varázsló | 🌊 Elementalista • 💀 **Nekromanta** (csak bűnösként, Sötét frakcióval!) |
| Harcos | 🩸 Berserker • 🛡 Védelmező |
| Íjász | 🎯 Mesterlövész • 🐺 Vadmester |
| Orgyilkos | ☠ Méregkeverő • 👻 Fantom |
| Druida | 🐾 Vadőr • 🌙 Holdjós • 🌳 Védelmező • 💚 Helyreállító |
| Paplovag | ☀️ Szentlélek • ⚖️ Megtorló • 🛡 Védő |
| Halállovag | 🩸 Vérlovag • ❄️ Fagylovag |
| Sámán | ⚡ Elemi • 🔨 Erősítő • 🌊 Hullámhívó |
| Szerzetes | 💨 Szélfutó • 🍺 Sörfőző • 🌫️ Ködszövő |
| Pap | 🙏 Fegyelem • 🌑 Árnyék |
| Boszorkánymester | 🍂 Átok • 🔥 Pusztítás |
| Démonvadász | 💥 Tombolás • 🛡 Bosszú |
| Sárkányidéző | 🔥 Perzselés • 💧 Megőrzés |

### ✨ Képesség Katalizátor

Minden kaszt a saját, tematikus **Képesség Katalizátorával** használja a képességeit
(a kasztválasztó GUI-ból bármikor igényelhető, ha elveszett; admin: `/job givecatalyst`):

| Kaszt | Katalizátor |
|---|---|
| Varázsló | 📖 **Mágikus Kódex** (bűvölt könyv) |
| Harcos | 📯 **Harci Kürt** (kecskekürt) |
| Íjász | 🎒 **Vadásztarsoly** (nyúlbőr) |
| Orgyilkos | 🪨 **Árnyékamulett** (kovakő) |
| Druida | 🌱 **Vadon Talizmánja** (tölgycsemete) |
| Paplovag | 🔔 **Szent Harang** (harang) |
| Halállovag | 💀 **Rúnakovácsolt Koponya** (wither-koponya) |
| Sámán | 🪬 **Ősök Totemje** (mentő totem) |
| Szerzetes | 🎍 **Jáde Bot** (bambusz) |
| Pap | 🕯️ **Szent Gyertya** (fehér gyertya) |
| Boszorkánymester | 🏮 **Lélek Lámpás** (lélek-lámpás) |
| Démonvadász | 👁️ **Démonszem** (ender-szem) |
| Sárkányidéző | 🐲 **Sárkány Esszencia** (sárkánylehelet) |

- **Jobb katt** — kiválasztott képesség elsütése
- **Lopakodás + ütés (bal katt)** — váltás a feloldott képességek között, kaszt-specifikus
  hanggal (lapozás / kürt / számszeríj / suttogó szél) és a képesség nevét + költségét
  mutató action bar kijelzéssel

Minden képességnek költsége (hibrid: a legtöbb az osztály-erőforrás, a vér/rituálé/fizikai spellek HP/XP/éhség) és visszatöltési ideje van. Több mint 390 képesség van a
rendszerben — kasztonként és specializációnként legalább 10 —, és **minden kaszt és specializáció saját, egyedi képességeket tanul**: nincs
átfedés a kasztok között.

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
Főzetmester / Transzmutátor, Séf / Hentes).

### 🗡 Relikviák

Egyedi, legendás tárgyak (jelenleg: **A Mételytépő** harci fejsze, amely megbélyegzi a
bűnösöket és ítéletet hajt végre rajtuk). Szabályaik:

- Egy relikviából **csak egy létezhet** a szerveren.
- **14 nap inaktivitás** után a relikvia füstként elenyészik, és újra megszerezhetővé válik.
- A négy **frakció-elytra** (Főnix-/Zúzmara-/Vándor-/Csontszárny) nem craftolható, hanem
  **rituálé-oltáron** idézhető meg: a megfelelő blokkon lopakodás + jobb katt az áldozati
  tárgyakkal.

### 💰 Gazdaság — dinamikus árfolyammal

Minden frakciónak saját valutája van (Piros / Kék / Semleges / Sötét token), amelyek fizikai
itemként és banki egyenlegként is léteznek:

- `/bank deposit` — a nálad lévő tokenek bankba helyezése; `/bank withdraw` — kivét itemként
- `/currency pay` — utalás játékosok közt; `/currency exchange` — valutaváltás
- **Az árfolyam élő:** egy valuta értékét a szerveren lévő összmennyisége határozza meg.
  Ha egy frakció elárasztja a gazdaságot a pénzével, az inflálódik és kevesebbet ér.
  Az aktuális árfolyamokat a `/currency rates`, illetve a fővárosokba lerakható **árfolyam-
  hologramok** (`/exchangeboard`) mutatják.
- **Piactér** (`/market`): játékosok közti adásvétel banki egyenlegből, eladási díjjal. A
  vételárat a **frakció-reputáció** is módosítja (ellenségtől felár, szövetségestől kedvezmény).
- **Aukciósház** (`/market auction`): licitálós eladás lejárattal — a licit a bankból zárolódik,
  túllicitálásnál automatikusan visszajár; a lejárt aukció nyertese offline is megkapja a
  tárgyat (belépéskor vagy `/market claim`).

### 🧟 Távolság-alapú nehézség

A spawntól távolodva a világ veszélyesebbé válik: minden 1000 blokk +1 mob szint
(`[Lvl X] Zombi` névvel, több élettel és sebzéssel). A spawner-/parancs-spawnolt mobok nem
skálázódnak, így a farmok nem törhetik el a rendszert.

### 🏰 Frakcióterületek

Adminok fővárosokat és területeket jelölhetnek ki (`/territory setcapital`, `/territory claim`).
A határátlépést a játékosok action bar üzenetben látják ("✦ Piros főváros ✦"), és opcionálisan
bekapcsolható az **építésvédelem** is: idegen frakció területén nem lehet építeni/bontani. Az
alap konfigurációban az értesítés aktív, az építésvédelem viszont ki van kapcsolva.

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
  LibsDisguises (Druida-formák vizuálja) — mindkettő nélkül is teljes értékűen fut
- Minden fő rendszer elkészült: frakciók (passzívokkal), 13 kaszt / 31 spec / 390+ spell,
  hibrid erőforrás-költség, talentek, szakmák, gazdaság + piac, relikviák + rituálék,
  világesemények (vérhold / világbossok / inváziók), király/raid/szezon, küldetések, pet-rendszer.
- A hátralévő irányok: [ROADMAP.md](ROADMAP.md)
