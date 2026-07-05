# 12. Küldetések ✅

A **küldetések** kis feladatok, amikért **jutalmat** kapsz (általában kaszt-XP-t, néha pénzt
vagy különleges hatást). 

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

1. **Mentor-küldetés:** keresd fel a kasztod **mester-NPC-jét** (a fővárosokban áll) és
   **beszélj vele** — ez veszi fel a próbát. Jutalom: **100 kaszt-XP**.
2. **Mester-próba:** teljesítsd a mester **próbapályáját** (időmérős parkour, pl.
   `/parkour start harcos_proba`). Jutalom: **400 kaszt-XP**.

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
