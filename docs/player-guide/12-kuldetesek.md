# 12. Küldetések ✅

A **küldetések** kis feladatok, amikért **jutalmat** kapsz (kaszt-XP-t, pénzt — akár a
**saját frakciód valutájában** — vagy különleges hatást).

**Miféle feladatok lehetnek?** Szörny- és játékos-vadászat, blokk-bányászás és -lerakás,
craftolás, tárgygyűjtés (bányászva vagy a földről **felvéve**), evés-ivás, horgászat,
varázslás (enchant), állat-tenyésztés, **állat-szelídítés** (taming), **olvasztás** (kohó),
**falusi kereskedés**, terület-felkeresés, **bióm-felfedezés**, szint-elérés,
NPC-felkeresés, **tárgy-beszállítás NPC-nek** (odaadod neki, ő átveszi), **raid-győzelem**,
**világboss-ölés** és parkour-próba.

**Story:** a quest-NPC-k **beszélnek is** — a küldetés átvételekor és leadásakor a
történetükhöz illő sorokat mondanak (a képernyőn, a nevükkel).


Parancsok:
- `/quest list` — a felvehető és aktív küldetéseid.
- `/quest accept <id>` — felveszel egy küldetést.
- `/quest info` — megnézed az aktív küldetéseid állását.
- `/quest abandon <id>` — feladsz egy küldetést.
- `/quest log` (`gui`, `naplo`) — a grafikus **küldetésnapló** (lásd lentebb).

A haladásodat a képernyő alján (action bar) is követheted. Amikor teljesíted a feladatot, a
jutalom **automatikusan** jár.

## Kezdő küldetés-lánc (új játékosoknak) 🐣

Az **első belépésedkor automatikusan** elindul egy rövid, vezetett lánc: **Beszélj a
hírnökkel** (a semleges főváros hírnök-NPC-je — nála a királyság-választás is megnyílik) →
**Első csata** (ölj 5 szörnyet) → **Első gyűjtögetés** (gyűjts 10 rönköt). Minden lépés
teljesítésekor a **következő magától elindul**, és valutát (a végén csákányt + kenyeret is)
kapsz. Nem kell semmit beírnod — csak kövesd az action bar jelzéseit.

## Több feladat egy questben 🎯

Egy küldetés ma már **több feladatot** (objektívát) is tartalmazhat egyszerre — pl.
„ölj meg **10 szörnyet** ÉS gyűjts **16 kenyeret**”. Kétféle mód van:

- **ALL (párhuzamos):** bármely sorrendben, mindegyik feladatot teljesítened kell.
- **SEQUENCE (sorban):** mindig csak az **aktuális** lépés halad, a következő csak utána
  nyílik — ez a story-láncokhoz való.

A haladás **feladatonként külön** követhető: a HUD és a `/quest info` felsorolja az összeset,
pl. `Szörnyek 4/10 • Kenyér 8/16`.

## Küldetésnapló GUI 📓

A `/quest log` (aliasok: `gui`, `naplo`) egy **grafikus küldetésnaplót** nyit, **három füllel**:

- **Aktív** — a folyamatban lévő küldetéseid a haladásukkal; **shift-kattintással feladhatod** őket.
- **Felvehető** — a most elérhető küldetések; **kattintással felveszed**.
- **Teljesített** — a már befejezett küldetéseid.

A napló **lapozható**, ha sok küldetésed van.

## Választós párbeszéd (elágazó story) 💬

Egyes quest-NPC-k párbeszéde után **kattintható válaszopciók** jelennek meg a chatben. Amelyiket
választod, az **más-más következő küldetést** indít — így a történet elágazik aszerint, hogyan
felelsz.

## Napi NPC-kínálat (rotáció) 🔄

Néhány NPC egy nagyobb **quest-poolból** naponta csak **pár** küldetést kínál, és a kínálat
**naponta frissül**. Ezért érdemes **visszatérni** hozzájuk — máskor más feladatokat adnak.

## Ismétlődő és szezonális küldetések ♻️

- **Ismétlődő (repeatable):** teljesítés után egy **cooldown** (pl. naponta, 24 óránként)
  letelte után **újra felveheted** ugyanazt a küldetést.
- **Szezonális:** szezononként **egyszer** teljesíthető; **új szezonban újra** elérhetővé válik.

## Jutalmak 🎁

A jutalom lehet **kaszt-XP**, **pénz** és **tárgy** is (nem csak XP vagy pénz). A pénz-jutalom
lehet mindig a **saját frakciód valutája** — így pl. a Piros frakció tagja piros tokent kap.

## Frakció-közösségi célok 🤝

A küldetések mellett vannak **szerver-szintű, közösségi célok** is: egy **megosztott számláló**,
amibe egy frakció (vagy az egész szerver) **minden tagja** beleszámít — pl. „a Piros frakció
együtt gyűjtsön **1000 vasat**”. Ezt **nem** kell külön felvenned: **automatikusan gyűlik**,
ahogy a normál játék közben teljesíted a hozzá tartozó tevékenységet. Amikor a cél elkészül,
az **egész frakció jutalmat kap a kasszába** + egy rövid **buffot**, majd a cél **újraindul**.

## Kaszt-próbák (a kezdő küldetések)

Négy kezdő kaszt-próba van a konfigurációban. Jutalom: **200 kaszt-XP**.

| Küldetés | Kaszt | Feladat |
|---|---|---|
| **A Harcos Próbája** | Harcos | Ölj meg **15 szörnyet** |
| **Az Íjász Próbája** | Íjász | Vadássz le **12 szörnyet** |
| **A Varázsló Próbája** | Varázsló | Szedj **10 virágot** |
| **Az Orgyilkos Próbája** | Orgyilkos | Ölj meg **10 szörnyet** |

## Mester-próbák (NPC-s láncok) 🧭

A kezdő próba után minden kezdő kaszt **kétlépcsős mester-lánccal** folytathatja:

1. **Mentor-küldetés:** vedd fel (`/quest accept <kaszt>_mentor`), keresd fel a kasztod
   **mester-NPC-jét** (a fővárosokban áll) és **beszélj vele**. Jutalom: **100 kaszt-XP**.
2. **Mester-próba:** ezt már **maga a mester adja** — ugyanaz a kattintás, amivel a
   mentor-küldetést teljesíted, azonnal kezedbe adja a próbát. Teljesítsd a
   **próbapályáját** (időmérős parkour, pl. `/parkour start harcos_proba`).
   Jutalom: **400 kaszt-XP**.

**Hogyan találod meg?** A quest-NPC-k felett **részecske-aura** világít — de **csak neked**,
ha éppen dolgod van velük:
- **Arany aura** ❕ — az NPC **questet tud adni neked** (minden feltételed megvan hozzá).
- **Zöld aura** — egy **aktív küldetésed** hozzá szól (beszélned kell vele).

Más játékos nem látja a te jelzéseidet, te sem az övéit.

| Lánc | Kaszt | NPC | Pálya |
|---|---|---|---|
| A Harcos Mestere → Mester-próbája | Harcos | harcos mester | `harcos_proba` |
| Az Íjász Mestere → Mester-próbája | Íjász | íjász mester | `ijasz_proba` |
| A Varázsló Mestere → Mester-próbája | Varázsló | varázsló mester | `varazslo_proba` |
| Az Orgyilkos Mestere → Mester-próbája | Orgyilkos | orgyilkos mester | `orgyilkos_proba` |

> Az NPC-k és a pályák **kihelyezése a szerver-csapat feladata** — ha még nem állnak,
> a lánc egyszerűen nem halad (a küldetés nem törik el).

## Sötét Beavatás (a Nekromanta kapuja)

- **Sötét Beavatás:** zarándokolj el **Mortengradba, a Holtak Városába** (a Kitaszítottak területére).
  Jutalom: **100 kaszt-XP**. **Ezt teljesítve nyílik meg a Nekromanta specializáció** (Sötét
  frakció + bűnös állapot is kell hozzá).

## Vezeklés-lánc (a sötét paktum megtörése) 🙏

Ez az **egyetlen mód**, hogy egy bűnös (sinner) játékos megszabaduljon a **sötét paktumtól**.
Három részből áll, sorban:

| Rész | Feladat | Jutalom |
|---|---|---|
| **Vezeklés I — A Penge** | Pusztíts el **30 erős szörnyet** (min. Lvl 2) | 150 kaszt-XP |
| **Vezeklés II — Az Alázat** | Fogj ki **20 halat** | 150 kaszt-XP |
| **Vezeklés III — A Feloldozás** | Győzz le **50 elit szörnyet** (min. Lvl 4) | 400 kaszt-XP + 100 Creutzér + **a paktum megtörik!** |

A harmadik rész végén **lekerül rólad a bűnös jelölés** — feloldozást nyersz, és újra szabad
vagy.

---

➡️ Tovább: [Frakcióterületek](13-teruletek.md) • [Vissza a tartalomhoz](README.md)
