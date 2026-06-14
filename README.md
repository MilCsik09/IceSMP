# IceSMP

Az **IceSMP** egy Folia-alapú Minecraft plugin (1.21.11, Paper API kompatibilis), amely egy
fantasy "királyságos" SMP szerver teljes játékmenet-rendszerét adja: frakciók, kasztok és
specializációk, talentek, szakmák, katalizátor-alapú képességek, legendás relikviák,
dinamikus gazdaság, távolság-alapú nehézség és frakcióterületek.

> 🎮 **Játékos tájékoztató:** [PLAYER_GUIDE.md](PLAYER_GUIDE.md) — mit hogyan használj a játékban
> (frakciók, kasztok, spellek, talentek, szakmák, gazdaság, világesemények) + mi a WIP.
> 📘 **Technikai dokumentáció:** [TECHNICAL.md](TECHNICAL.md) — architektúra, parancs- és
> jogosultság-referencia, config leírás, adattárolás, fejlesztői útmutató.
> ✅ **Pontos készültségi állapot:** [STATUS.md](STATUS.md) — mi van kész, ismert korlátok, mi hiányzik.
> 💡 **Ötlettár / tervek:** [ideas.md](ideas.md)

---

## Mit tud a plugin? (közérthetően)

### ⚔️ Frakciók — négy oldal, négy sors

| Frakció | Belépés | Passzív bónusz |
|---|---|---|
| 🔴 **Piros** | `/faction join red` | Immunis a tűz, láva és forró blokkok sebzésére |
| 🔵 **Kék** | `/faction join blue` | Immunis a fagyásra, lassabban éhezik |
| ⚪ **Semleges** | `/faction join neutral` (alapértelmezett) | Lopakodás közben láthatatlan; a nem-ellenséges mobok békén hagyják |
| ⚫ **Sötét** | `/faction join dark` — **csak bűnösként!** | Immunis a wither-sebzésre; az élőhalottak nem támadják |

**A Sötét frakció különleges:** csak az léphet be, akit a Mételytépő relikvia (vagy admin)
**bűnössé (sinner)** bélyegzett. Belépéskor megköttetik a **sötét paktum** — onnantól a
bűnös jelölés **soha többé nem törölhető le**, akkor sem, ha később elhagyod a frakciót.

### 🧙 Kasztok és specializációk

Négy alap kaszt választható a profil GUI-ból (max. 2 kaszt / játékos; a másodlagos csak az
elsődleges max szintje után nyílik meg, és nem specializálódhat):

- **Varázsló** — támogató és elemi mágia
- **Harcos** — közelharci erő és kitartás
- **Íjász** — távolsági harc és mozgékonyság
- **Orgyilkos** — lopakodás és meglepetés

A kasztok **mob ölésből** kapnak XP-t (minél messzebb merészkedsz a spawntól, annál erősebb
mobok jönnek — és annál több XP-t adnak). A szintek WoW-mintára **egyre több XP-be
kerülnek**, a képességek (spellek) pedig a szintekkel **automatikusan feloldódnak**.

A 25. szinttől **specializálódhatsz** (`/spec choose <id>`), és a legerősebb képességek
csak így érhetők el. A döntés nem örök: a `/spec respec` paranccsal **frakcióvalutáért
visszaválthatod** a speced (a spec-talentjeid pontjai ilyenkor visszatérülnek):

| Kaszt | Specializációk |
|---|---|
| Varázsló | 🌊 Elementalista • 💀 **Nekromanta** (csak bűnösként, Sötét frakcióval!) |
| Harcos | 🩸 Berserker • 🛡 Védelmező |
| Íjász | 🎯 Mesterlövész • 🐺 Vadmester |
| Orgyilkos | ☠ Méregkeverő • 👻 Fantom |

### ✨ Képesség Katalizátor

Minden kaszt a saját, tematikus **Képesség Katalizátorával** használja a képességeit
(a kasztválasztó GUI-ból bármikor igényelhető, ha elveszett; admin: `/job givecatalyst`):

| Kaszt | Katalizátor |
|---|---|
| Varázsló (és Nekromanta) | 📖 **Mágikus Kódex** (bűvölt könyv) |
| Harcos | 📯 **Harci Kürt** (kecskekürt) |
| Íjász | 🎒 **Vadásztarsoly** (nyúlbőr) |
| Orgyilkos | 🪨 **Árnyékamulett** (kovakő) |

- **Jobb katt** — kiválasztott képesség elsütése
- **Lopakodás + ütés (bal katt)** — váltás a feloldott képességek között, kaszt-specifikus
  hanggal (lapozás / kürt / számszeríj / suttogó szél) és a képesség nevét + költségét
  mutató action bar kijelzéssel

Minden képességnek költsége (éhség vagy XP) és visszatöltési ideje van. 124 képesség van a
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

### 💰 Gazdaság — dinamikus árfolyammal

Minden frakciónak saját valutája van (Piros / Kék / Semleges / Sötét token), amelyek fizikai
itemként és banki egyenlegként is léteznek:

- `/bank deposit` — a nálad lévő tokenek bankba helyezése; `/bank withdraw` — kivét itemként
- `/currency pay` — utalás játékosok közt; `/currency exchange` — valutaváltás
- **Az árfolyam élő:** egy valuta értékét a szerveren lévő összmennyisége határozza meg.
  Ha egy frakció elárasztja a gazdaságot a pénzével, az inflálódik és kevesebbet ér.
  Az aktuális árfolyamokat a `/currency rates` mutatja.

### 🧟 Távolság-alapú nehézség

A spawntól távolodva a világ veszélyesebbé válik: minden 1000 blokk +1 mob szint
(`[Lvl X] Zombi` névvel, több élettel és sebzéssel). A spawner-/parancs-spawnolt mobok nem
skálázódnak, így a farmok nem törhetik el a rendszert.

### 🏰 Frakcióterületek

Adminok fővárosokat és területeket jelölhetnek ki (`/territory setcapital`, `/territory claim`).
A határátlépést a játékosok action bar üzenetben látják ("✦ Piros főváros ✦"), és opcionálisan
bekapcsolható az **építésvédelem** is: idegen frakció területén nem lehet építeni/bontani.

---

## Parancsok (gyorsreferencia)

| Parancs | Aliasok | Mire való |
|---|---|---|
| `/profile` | `status`, `info` | Profil megnyitása (frakció, egyenlegek, kasztválasztás) |
| `/faction join/leave` | `f` | Frakcióba lépés / kilépés |
| `/spec list/choose/info` | `specialization` | Specializációk |
| `/talent`, `/talent spend` | `talents` | Talentek megtekintése és fejlesztése |
| `/profession join/info/list` | `prof`, `szakma` | Szakma választás és állapot |
| `/bank balance/deposit/withdraw` | `wallet`, `vault` | Banki műveletek |
| `/currency balance/pay/exchange/rates` | `money`, `eco` | Valutaműveletek és árfolyamok |
| `/job …` | `class` | Kaszt adminisztráció (admin) |
| `/relic list/give` | `relics` | Relikvia adminisztráció (admin) |
| `/sinner <játékos> set/clear` | — | Bűnös státusz kezelése (admin) |
| `/territory …` | `terulet` | Területek kijelölése (admin) |
| `/icesmp reload` | `ismp` | Konfiguráció újratöltése (admin) |

A teljes parancs- és jogosultság-referencia a [TECHNICAL.md](TECHNICAL.md)-ben található.

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
- **Folia-kompatibilis:** `folia-supported: true`, minden ütemezett feladat szinkron
- Minden tervezett fázis (1–7) elkészült: alapok, frakciók, valuták és váltó, relikviák,
  mob skálázás, szakmák és craft-korlátozások, kasztok/specializációk/talentek, területek.
- A következő irányok az [ideas.md](ideas.md)-ben: raid eventek, frakció-kassza és adó,
  quest-keretrendszer, relikvia-elytrák, világesemények.
