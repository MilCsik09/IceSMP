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
| `/achievements` | `ach`, `eleresek` | Elérések (mérföldkövek + jutalmak) |
| `/daily` | `napi` | A napi küldetés és haladásod |
| `/pet item\|summon\|dismiss\|name` | `tars`, `companion` | Társ: befogó eszköz, idézés, név, szint (Vadmester / Nekromanta) |
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
| `/market` | `piac`, `ah` | Piactér böngésző (vásárlás / licitálás) |
| `/market sell <ár> [valuta]` | | A kézben tartott tárgy eladása |
| `/market auction <kikiáltási ár> [óra] [valuta] [buyout:<ár>]` | | Aukció indítása a kézben tartott tárgyra (a `buyout:` opcionális azonnali-vétel ár) |
| `/market claim` | | Megnyert / visszajáró aukciós tárgyak átvétele |
| `/market cancel` | | Saját eladásaid visszavonása (élő licites aukció nem) |
| `/spec list` / `/spec choose <id>` | `specialization`, `specializacio` | Specializációk |
| `/spec respec <class\|profession>` | | Specializáció visszaváltása |
| `/talent` / `/talent spend <class\|profession> <talent>` | `talents`, `talentfa` | Talentek |
| `/profession join <szakma>` / `/profession info` | `prof`, `szakma` | Szakmák |
| `/class givecatalyst` | `kaszt`, `job` | Elveszett Képesség Katalizátor pótlása |
| `/quest list` / `/quest accept <id>` / `/quest info` | `quests`, `kuldetes` | Küldetések |
| `/quest log` | `gui`, `naplo` | **Küldetésnapló GUI** — Aktív / Felvehető / Teljesített fülek, lapozható |
| `/souls` / `/souls champion` | `soul`, `lelek` | Lélekszilánk (csak Nekromanta) |
| `/bounty` | `fejvadasz`, `korozes` | Körözési lista: ki körözött és mennyit ér a feje |
| `/spell` / `/spell upgrade <id>` | `mastery`, `mesterseg` | Spell-mesterség: valutáért rövidebb cooldown ÉS erősebb hatás (sebzés, gyógyítás, effekt-időtartam) |
| `/spellbook` | `varazskonyv`, `konyv`, `sb` | **Varázskönyv**: spellek böngészése (leírás, költség, sebzés, CD) és kiválasztása kattintással. *Sunyíts + jobb katt a katalizátoron* is megnyitja. |
| `/events season` / `/events blood-moon` / `/events caravan` | `event`, `esemeny` | Világesemények állása (szezon, vérhold, kereskedő-karaván) |
| `/party invite\|accept\|decline\|leave\|list` | `p`, `parti` | **Party (csapat)**: meghívás, csatlakozás, kilépés, taglista (max 5 fő) |
| `/party kick\|promote\|disband <név>` | | Csapatvezetői műveletek: kirúgás, vezetés átadása, feloszlatás |
| `/p <üzenet>` | | **Csapat-chat** — csak a párttagok látják |
| `/claim` | `birtok` | Az aktuális chunk lefoglalása (**saját birtok** — első 3 ingyen) |
| `/claim unclaim\|info\|list\|show` | | Claim felszabadítása / infó / lista / határ-kirajzolás |
| `/claim trust\|untrust <név>` | | Megbízott hozzáadása / elvétele (teljes hozzáférés a claimjeidhez) |

## Király-parancsok (csak a frakció királyának)

| Parancs | Mit csinál |
|---|---|
| `/faction treasury withdraw <összeg>` | Kivétel a frakciókasszából |
| `/faction king tax <százalék>` | Frakció adókulcs beállítása |
| `/faction raid <célfrakció> [terület]` | Háború (raid) hirdetése — alapból a védő fővárosáért |

A raidhez **mindenki** (nem csak a király) így kapcsolódik:

| Parancs | Mit csinál |
|---|---|
| `/faction raid join` | Jelentkezés harcosnak a felkészülés alatt (max 10/oldal) |
| `/faction raid status` | Raid-állás: fázis, pontok, létszám |

## Admin parancsok (csak adminoknak)

| Parancs | Mit csinál |
|---|---|
| `/icesmp reload` | Konfiguráció újratöltése |
| `/class addxp\|setxp <játékos> <mennyiség>` | Kaszt-XP adása/beállítása |
| `/class givecatalyst\|unlockspell <játékos> [spell]` | Katalizátor adása / spell feloldása |
| `/class admin <resetcd\|unlockallskills\|resetskills\|resetclass> <játékos>` | Cooldown-/spell-/**teljes kaszt-reset** egy játékosnak |
| `/profession set\|clear\|addxp` | Szakma-adatok kezelése |
| `/spec reset <játékos>` | Specializációk törlése |
| `/sinner <játékos> set\|clear\|add` | Bűnös állapot kezelése |
| `/quest complete <játékos> <id>` | Küldetés azonnali teljesítése |
| `/quest admin create <id> <objektíva> <darab> <név...>` | Új küldetés készítése játékon belül (több-objektívás is lehet) |
| `/quest admin addobjective <id> <objektíva> <darab> [leírás]` | További feladat hozzáadása egy questhez |
| `/quest admin set <id> objectives-mode ALL\|SEQUENCE` | Több-objektívás mód: párhuzamos vagy sorban |
| `/quest admin set <id> <mező> <érték...>` | Küldetés-mező beállítása (feltétel, jutalom, giver-npc, dialogue.choices, rotation-group, seasonal...) |
| `/quest admin delete/info/list` | Admin-küldetés törlése / megtekintése / listája |
| `/currency set <játékos> <összeg> [valuta]` | Valuta-egyenleg beállítása |
| `/faction set <játékos> <frakció>` | Játékos frakcióba sorolása |
| `/relic give <játékos> <relic_id>` | Relikvia adása |
| `/territory setcapital\|claim\|remove` | Területek kezelése |
| `/exchangeboard place\|remove` | Árfolyamtábla lerakása/törlése |
| `/events blood-moon start\|stop` | Vérhold kézi indítása / leállítása |
| `/events worldboss` | Világboss azonnali megidézése |
| `/events invasion` | Szörny-invázió azonnali indítása |
| `/events caravan arrive\|depart` | Kereskedő-karaván kézi indítása / elküldése |
| `/events ambient` | Hangulat-esemény azonnali kiváltása |
| `/events gathering` | Gyűjtögető buff-ablak megnyitása |
| `/events treasure` | Elrejtett kincs elhelyezése |
| `/events wild-hunt` | Vad Hajsza fenevad megidézése |
| `/events abundance` | Bőség-idő indítása |
| `/events challenge` | Kollektív szerver-kihívás indítása |
| `/events escort` | Karaván-kíséret (konvoj) indítása |
| `/events meteor` | Meteor-becsapódás kiváltása |
| `/events intro [játékos]` | Bemutató újrajátszása |
| `/claim admin unclaim` | Idegen claim törlése admin-jogon |
| `/parkour setstart\|setfinish\|remove <id>` | Parkour-pálya beállítása |

---

➡️ Tovább: [Party (csapat)](15-csapat.md) • [Vissza a tartalomhoz](README.md)
