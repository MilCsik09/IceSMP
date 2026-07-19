# 4. Kasztok ✅

A **kaszt** a hős-típusod: ez dönti el, milyen képességeid (varázslataid) lehetnek. **13 kaszt**
közül választhatsz a `/profile` → **Kaszt** menüből. Minden kasztnak saját katalizátor-tárgya
(a „varázskönyved") és saját **erőforrása** (Erő-csík) van.

| Kaszt | Stílus | Katalizátor | Erőforrás |
|---|---|---|---|
| 🧙 **Varázsló** | Elemi és kontroll mágia, távolsági ráolvasások | 📖 Caldesterai Rúnakódex | Mana |
| ⚔️ **Harcos** | Közelharci erő, kitartás, buffok | 📯 Sárkánykirály Kürtje | Düh |
| 🏹 **Íjász** | Távolsági harc, mozgékonyság, csapdák | 🎒 Soleil Vadásztarsolya | Fókusz |
| 🗡️ **Orgyilkos** | Lopakodás, gyors kitörések, gyengítés | 🪨 Homály-szilánk | Energia |
| 🐻 **Druida** | Alakváltó természet-mágia (harc / kontroll / tank / gyógyítás) | 🌱 Aetrinita Sarja | Természeti Erő |
| 🔆 **Paplovag** | Szent harc, védelem és gyógyítás | 🔔 Hajnaltűz Harangja | Szent Erő |
| 💀 **Halállovag** | Rúna-mágia, vér és fagy, közelharci tank/DPS | 💀 Néma Rúnakoponya | Runikus Erő |
| 🌊 **Sámán** | Elemek, totemek, gyógyítás és erősítés | 🪬 Ősvihar Totemje | Mana |
| ☯️ **Szerzetes** | Gyors közelharc, csi-energia, gyógyítás | 🎍 Élet Ága | Csi |
| ✝️ **Pap** | Szent és árny mágia, gyógyítás | 🕯️ Asterlayna Gyertyája | Mana |
| 😈 **Boszorkánymester** | Átkok, démonok és pusztító tűz | 🏮 Kárhozat Lámpása | Lélekerő |
| 👁️ **Démonvadász** | Mozgékony démoni harc és bosszú | 👁️ Hasadék Szeme | Fúria |
| 🐉 **Sárkányidéző** | Sárkány-eszencia: perzselő mágia és gyógyítás | 🐲 Sárkányvér-fiola | Eszencia |

> Az **Erő-csík** a HUD oldalsávban van; a **legtöbb** képesség ezt fogyasztja (idővel
> visszatöltődik, üres csíknál a spell nem sül el). **Hibrid:** a vér-mágia életet, a nagy
> rituálék XP-t, a nehéz fizikai képességek éhséget kérnek. Részletek a
> [fő tájékoztatóban](../../PLAYER_GUIDE.md).

## Egy kaszt, végleges választás

- **Egy kasztod lehet** — ezt választod ki a menüből, és ez a hősöd. Ez adja a képességeidet
  és (25. szinttől) a specializációdat.
- **A választás végleges:** ha új kasztot szeretnél, egy adminnak kell reszetelnie
  (`/class admin resetclass <játékos>` — ez törli a kasztot, a specet és a feloldott spelleket).

## Szintezés — hogyan erősödsz?

A kaszt **szörnyek (mobok) megölésével** kap **XP-t** (tapasztalatot):

- **Alap: 10 XP** minden ellenséges mob megöléséért.
- **+3 XP minden „mob-szintért".** A spawntól messzebb a szörnyek erősebbek és magasabb
  szintűek (lásd a [Világesemények / Mob-szintezés] részt) — egy 3-as szintű szörny tehát
  `10 + 3×3 = 19` XP-t ad.
- Csak **ellenséges** mobok adnak XP-t (tehén, csirke nem).

**Mennyi kell egy szinthez?** A következő szint ára `60 + (előző szintek száma × 10)` XP:
- 1 → 2 szint: **60 XP** (kb. 6 alap-mob)
- 2 → 3 szint: **70 XP**
- 3 → 4 szint: **80 XP** … és így tovább. **Max szint: 50.**

Minél magasabb a szinted, annál több ölés kell a következőhöz — de a magas szintű szörnyek
egyenként több XP-t is adnak, szóval érdemes a veszélyesebb, távolabbi vidékekre menni.

## A Képesség Katalizátor (a „varázskönyved")

A képességeidet egy **kaszt-tematikus tárggyal** használod (lásd a fenti táblázatot):

- **Jobb katt** = a kiválasztott képesség elsütése.
- **Lopakodás (SHIFT) + ütés (bal katt)** = váltás a feloldott képességeid között. A képernyő
  alján látod, melyik van kiválasztva és mennyibe kerül.
- **Ha elveszett:** a `/profile` → Kaszt menüből bármikor **újra igényelheted** (egy gombbal).
- A Katalizátort **nem lehet** craftnál vagy kemencében véletlenül elhasználni — védett tárgy.

## Képesség-fa

A `/profile` → **Képesség-fa** gomb megmutatja a kasztod (és a választott specializációd)
**összes** képességét, feloldási szint szerint: a már feloldottak ragyognak, a zároltak
mutatják, hányadik szint kell hozzájuk.

> A teljes képességlistát (mit tud, mennyibe kerül) lásd: [Képességek](05-kepessegek.md).

---

➡️ Tovább: [Képességek](05-kepessegek.md) • [Specializációk](06-specializaciok.md) • [Vissza](README.md)
