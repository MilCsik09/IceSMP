# 14. Parancsok listája 📜

Itt minden parancsot megtalálsz egy helyen. A `< >` közé **te írsz be** valamit; a `[ ]` azt
jelenti, hogy **elhagyható**.

> ## 🖱️ A legegyszerűbb út: `/menu`
> Nem kell parancsokat gépelned! Írd be: **`/menu`** (vagy `/hub`), és egy **kattintós
> főmenü** nyílik meg, ahonnan minden rendszer egy gombnyomásra elérhető: Karakterlap,
> Frakció, Bank & Pénz, Piac, Küldetések, Események, Relikviák, Lélekszilánk (és adminoknak
> egy admin panel). Minden almenüben gombokkal intézhetsz mindent — a háttérben ugyanazokat a
> parancsokat futtatja, amiket lent látsz.

## Mindennapi parancsok (mindenkinek)

| Parancs | Aliasok | Mit csinál |
|---|---|---|
| `/menu` | `hub`, `m` | **Központi kattintós menü** — innen minden elérhető |
| `/leaderboard` | `lb`, `top`, `rangsor` | Ranglisták: leggazdagabb, legmagasabb szint, legtöbb raid-kill |
| `/achievements` | `ach` | Elérések (mérföldkövek + jutalmak) |
| `/daily` | `napi` | A napi küldetés és haladásod |
| `/pet item\|summon\|dismiss\|name` | `tars` | Társ: befogó eszköz, idézés, név, szint (Vadmester / Nekromanta) |
| `/parkour list\|start <id>` | `trial`, `palya` | Időmérős parkour-pályák |
| `/market search <szöveg>` | `piac`, `ah` | Keresés a piacon (a /market mellett) |
| `/profile` | `karakter`, `char`, `status` | A **karakterlap** — kaszt, spec, szakma, talent, képesség-fa menük |
| `/faction join <frakció>` | `/f` | Belépés egy frakcióba (`red`/`blue`/`neutral`) |
| `/faction leave` | `/f` | Kilépés a frakcióból |
| `/faction king vote <játékos>` | `/f` | Szavazás a frakciód királyára |
| `/bank balance` | `wallet`, `vault` | Banki egyenlegeid |
| `/bank deposit` | | Tokenek bankba helyezése |
| `/bank withdraw <valuta> <összeg>` | | Tokenek kivétele |
| `/currency balance` | `money`, `eco` | Egyenleg gyorsnézet |
| `/currency pay <játékos> <összeg> [valuta]` | | Pénz utalása |
| `/currency exchange <összeg> <honnan> <hová>` | | Valutaváltás |
| `/currency rates` | | Aktuális árfolyamok |
| `/market` | `piac`, `ah` | Piactér böngésző (vásárlás) |
| `/market sell <ár> [valuta]` | | A kézben tartott tárgy eladása |
| `/market cancel` | | Saját eladásaid visszavonása |
| `/spec list` / `/spec choose <id>` | `specializacio` | Specializációk |
| `/spec respec <class\|profession>` | | Specializáció visszaváltása |
| `/talent` / `/talent spend <class\|profession> <talent>` | `talentfa` | Talentek |
| `/profession join <szakma>` / `/profession info` | `prof`, `szakma` | Szakmák |
| `/class givecatalyst` | `kaszt`, `job` | Elveszett Képesség Katalizátor pótlása |
| `/quest list` / `/quest accept <id>` / `/quest info` | `quests`, `kuldetes` | Küldetések |
| `/souls` / `/souls champion` | `soul`, `lelek` | Lélekszilánk (csak Nekromanta) |
| `/spell` / `/spell upgrade <id>` | `mastery`, `mesterseg` | Spell-mesterség: cooldown-csökkentés valutáért |
| `/events season` / `/events blood-moon` | `event`, `esemeny` | Világesemények állása |

## Király-parancsok (csak a frakció királyának)

| Parancs | Mit csinál |
|---|---|
| `/faction treasury withdraw <összeg>` | Kivétel a frakciókasszából |
| `/faction king tax <százalék>` | Frakció adókulcs beállítása |
| `/faction raid <célfrakció>` | Háború (raid) hirdetése |

## Admin parancsok (csak adminoknak)

| Parancs | Mit csinál |
|---|---|
| `/icesmp reload` | Konfiguráció újratöltése |
| `/class addxp\|setxp\|...` | Kaszt-adatok kezelése |
| `/profession set\|clear\|addxp` | Szakma-adatok kezelése |
| `/spec reset <játékos>` | Specializációk törlése |
| `/sinner <játékos> set\|clear\|add` | Bűnös állapot kezelése |
| `/relic give` | Relikvia adása |
| `/territory setcapital\|claim\|remove` | Területek kezelése |
| `/exchangeboard place\|remove` | Árfolyamtábla lerakása/törlése |
| `/events bloodmoon start\|stop` | Vérhold kézi indítása / leállítása |
| `/events worldboss` | Világboss azonnali megidézése |
| `/events invasion` | Szörny-invázió azonnali indítása |
| `/events intro [játékos]` | Bemutató újrajátszása |
| `/parkour setstart\|setfinish\|remove <id>` | Parkour-pálya beállítása |

---

[Vissza a tartalomhoz](README.md)
