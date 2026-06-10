# IceSMP

Az **IceSMP** egy Folia-alapú, 1.21.11-es kompatibilis Minecraft plugin, amelynek célja egy moduláris, jól karbantartható **SMP** szerverélmény felépítése.

## Áttekintés

A projekt három fő rendszer köré épül:

1. **Frakció rendszer** – királyságok / factionök kezelése, passzív bónuszokkal és saját valutákkal.
2. **Alkotói ereklyék** – ritka, egyedi képességet adó tárgyak PDC alapú védelemmel.
3. **Távolság-alapú szintezés** – a spawn ponttól távolodva erősödő mobok.

A fejlesztés célja, hogy a kód jól szétválasztott modulokból álljon, és később könnyen bővíthető legyen.

## Technológiai stack

- **Szerver API:** Folia 1.21.11 (Paper API kompatibilis)
- **Kompatibilitás:** Luminol
- **Nyelv:** Java 21
- **JDK:** Temurin 21
- **Build system:** Gradle
- **Plugin meta:** `paper-plugin.yml`

## Jelenlegi belépési pontok

A projekt jelenlegi fő osztályai a következők:

- `hu.taliann.icesmp.IceSMP` – a fő plugin osztály
- `hu.taliann.icesmp.IceSMPBootstrap` – bootstrapper
- `hu.taliann.icesmp.IceSMPLoader` – loader

> Megjegyzés: Folia plugin módban a parancsok nem a `paper-plugin.yml`-ben vannak deklarálva, hanem kódból regisztrálódnak az `IceSMPCore` indulásakor.

> Megjegyzés: a jelenlegi package név `hu.Taliann.iceSMP`. A későbbi egységesítés során érdemes lehet teljesen kisbetűs Java package konvencióra váltani, például `hu.taliann.icesmp`.

## Javasolt mappastruktúra

A projekt modulos felépítése:

- `core/` – fő plugin osztály, config betöltés, lifecycle kezelése
- `data/` – enumok és adatmodellek
- `managers/` – rendszerek logikája és vezérlése
- `listeners/` – Bukkit/Folia eseménykezelők
- `commands/` – játékos és admin parancsok
- `tasks/` – ismétlődő és aszinkron feladatok
- `utils/` – segédfüggvények, pl. színek, PDC, matek

## Fő rendszerek

### 1. Frakció rendszer

A szerver alapját ez a modul adja.

**Adattárolás:**
- SQLite adatbázis, vagy
- `factions.yml` alapú mentés

**Tervezett parancsok:**
- `/faction join <királyság>`
- `/faction leave`
- opcionális admin parancsok: `/faction set`

**Tervezett listenerek:**
- `EntityDamageEvent` – tűzsebzés tiltása a Pirosaknak, jég bónusz a Kékeknek
- `FoodLevelChangeEvent` – éhség lassítása a Kékeknek
- `PlayerToggleSneakEvent` – láthatatlanság adása a Semlegeseknek
- `EntityTargetEvent` – semleges mobok békésen tartása a Semleges frakciónak

### 2. Valutaváltó / frakciógazdaság

A szerveren több, frakciókhoz kötött valuta fut, és ezek **külön itemként** jelennek meg PDC-vel és custom model data-val. A játékosok a `/currency exchange` parancssal tudnak váltani közöttük. A váltás árfolyama és díja konfigurálható, így később frakciók közti kereskedelem és elfogadás is erre épülhet.

### 3. Játékos profil UI

A `/profile` parancs egy egyszerű UI-t nyit meg, ahol látható a játékos frakciója és az egyenlege.

### 4. Alkotói ereklyék

Egyedi, rendkívül ritka tárgyak, amelyek különleges képességeket adnak.

**Adattárolás:**
- `PersistentDataContainer` a tárgyon
- `relics.yml` a tulajdonosi / időadatok követésére

**Fő mechanikák:**
- egyedi tárgyak létrehozása `CustomModelData` használatával
- általános rituálé időzítővel (`BukkitRunnable`)
- inaktivitás-ellenőrzés belépéskor
- trigger + cooldown framework (`RIGHT_CLICK_AIR`, `RIGHT_CLICK_BLOCK`)
- config-alapú ability binding (`ability-id`)


**Inaktivitás-kezelés:**
- belépéskor ellenőrzés, hogy a játékos relikviája lejárt-e
- lejárat esetén a tárgy eltávolítása az inventoryból
- füst effekt lejátszása

### 5. Távolság-alapú szintező

A világ a spawn ponttól távolodva fokozatosan veszélyesebbé válik.

**Tervezett listener:**
- `CreatureSpawnEvent`

**Szintképlet:**
- `távolság / 1000 = szint`
- példa: `2000 blokk = Lvl 2`

**Módosítások:**
- `Attribute.GENERIC_MAX_HEALTH`
- `Attribute.GENERIC_ATTACK_DAMAGE`
- custom name: `[Lvl X] Zombi`

## Fejlesztési ütemterv

### Fázis 1 – Alapok
- [x] Főosztály felmérése
- [x] `config.yml` létrehozása
- [x] `FactionType` enum megírása
- [x] Alap valuta rendszer (`CurrencyManager`, `currency.yml`)

### Fázis 2 – Királyságok
- [x] adatbázis vagy YAML alapú mentés
- [x] `/faction` parancs váz és subcommand router
- [x] passzív frakció listenerek alapjai

### Fázis 2.5 – Valuták és váltó
- [x] többvalutás `CurrencyManager`
- [x] `/currency exchange` alparancs
- [x] valutaárfolyam és díj configból
- [x] item-alapú valuta tokenek PDC-vel és custom model data-val

### Fázis 3 – Ereklyék
- [ ] PDC utilok
- [ ] ereklye tárgyak generálása
- [ ] rituálé időzítő
- [ ] inaktivitás-törlő task

### Fázis 4 – Világ & Harc
- [ ] mob skálázó rendszer a `CreatureSpawnEvent` alapján
- [ ] spawn távolság szerinti attribútum-módosítások
- [ ] névkezelés és szint kijelzés

### Fázis 5 – Szakmák / extra szabályok
- [ ] opcionális craftolási korlátozások
- [ ] szint vagy kaszt alapú tiltások

## Konvenciók

- A rendszer legyen **moduláris** és könnyen tesztelhető.
- Az üzleti logika kerüljön a `managers/` és `data/` rétegekbe.
- A listenerek csak delegáljanak, ne tartalmazzanak túl sok logikát.
- A helper függvények maradjanak a `utils/` csomagban.

## Következő lépés

A következő fejlesztési lépés a **Frakció rendszer** alapjainak megírása:

1. `FactionType` enum
2. config betöltés
3. mentési réteg
4. `/faction` parancs
5. passzív frakció bónuszok

## Projekt állapota

- **Név:** IceSMP
- **API:** Folia 1.21.11 (Paper API kompatibilis)
- **Nyelv:** Java 21
- **Build:** Gradle
- **Státusz:** tervezési / kezdeti fejlesztési fázis

