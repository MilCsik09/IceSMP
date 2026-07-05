# 12. Küldetések ✅

A **küldetések** kis feladatok, amikért **jutalmat** kapsz (kaszt-XP-t, pénzt — akár a
**saját frakciód valutájában** — vagy különleges hatást).

**Miféle feladatok lehetnek?** Szörny- és játékos-vadászat, blokk-bányászás és -lerakás,
craftolás, tárgygyűjtés, evés-ivás, horgászat, varázslás (enchant), állat-tenyésztés,
terület-felkeresés, szint-elérés, NPC-felkeresés, **tárgy-beszállítás NPC-nek** (odaadod
neki, ő átveszi) és parkour-próba.

**Story:** a quest-NPC-k **beszélnek is** — a küldetés átvételekor és leadásakor a
történetükhöz illő sorokat mondanak (a képernyőn, a nevükkel).


Parancsok:
- `/quest list` — a felvehető és aktív küldetéseid.
- `/quest accept <id>` — felveszel egy küldetést.
- `/quest info` — megnézed az aktív küldetéseid állását.
- `/quest abandon <id>` — feladsz egy küldetést.

A haladásodat a képernyő alján (action bar) is követheted. Amikor teljesíted a feladatot, a
jutalom **automatikusan** jár.

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

- **Sötét Beavatás:** zarándokolj el a **Sötét romvárosba** (a Sötét frakció területére).
  Jutalom: **100 kaszt-XP**. **Ezt teljesítve nyílik meg a Nekromanta specializáció** (Sötét
  frakció + bűnös állapot is kell hozzá).

## Vezeklés-lánc (a sötét paktum megtörése) 🙏

Ez az **egyetlen mód**, hogy egy bűnös (sinner) játékos megszabaduljon a **sötét paktumtól**.
Három részből áll, sorban:

| Rész | Feladat | Jutalom |
|---|---|---|
| **Vezeklés I — A Penge** | Pusztíts el **30 erős szörnyet** (min. Lvl 2) | 150 kaszt-XP |
| **Vezeklés II — Az Alázat** | Fogj ki **20 halat** | 150 kaszt-XP |
| **Vezeklés III — A Feloldozás** | Győzz le **50 elit szörnyet** (min. Lvl 4) | 400 kaszt-XP + 100 Semleges token + **a paktum megtörik!** |

A harmadik rész végén **lekerül rólad a bűnös jelölés** — feloldozást nyersz, és újra szabad
vagy.

---

➡️ Tovább: [Frakcióterületek](13-teruletek.md) • [Vissza a tartalomhoz](README.md)
