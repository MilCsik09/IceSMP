# 6. Specializációk ✅

A **specializáció** a kasztod „kiteljesedése": egy irány, amit a **25. szinttől** választhatsz
az elsődleges kasztodnak. Ez nyitja meg a legerősebb, legtematikusabb képességeket (25–45.
szint között).

Hogyan? `/profile` → **Specializáció** menü → kattints a választott irányra. (Vagy parancs:
`/spec list`, `/spec choose <azonosító>`.) A menü mindig csak **a te kasztod** irányait mutatja,
és kiírja, ha valamihez még nem teljesíted a feltételt.

Összesen **35 specializáció** van. A **spec dönti el a szerepedet** (🗡️ közelharci DPS,
🏹 távolsági DPS, ✨ caster, 🛡️ tank, ➕ gyógyító) — vagyis milyen stílusban a leghatékonyabb:

| Kaszt | Specializációk (szerep) |
|---|---|
| 🧙 Varázsló | 🌊 **Elementalista** — tűz/jég/villám (caster) • 💀 **Nekromanta** — holtak/lélek (caster) |
| ⚔️ Harcos | 🩸 **Berserker** — támadás/vér (DPS) • 🛡️ **Védelmező** — pajzs/buff (tank) |
| 🏹 Íjász | 🎯 **Mesterlövész** — pontos lövések (táv. DPS) • 🐺 **Vadmester** — állat-társak (táv. DPS) |
| 🗡️ Orgyilkos | ☠️ **Méregkeverő** — mérgek (DPS) • 👻 **Fantom** — árnyék/félelem (DPS) • ☣️ **Pestishozó** — pestis/sorvasztás (DPS, csak Kitaszított) |
| 🐻 Druida | 🐾 **Vadőr** (DPS) • 🌙 **Holdjós** (caster) • 🌳 **Védelmező** — kéreg (tank) • 💚 **Helyreállító** (gyógyító) |
| 🔆 Paplovag | ☀️ **Szentlélek** (gyógyító) • ⚖️ **Megtorló** (DPS) • 🛡️ **Védő** (tank) |
| 💀 Halállovag | 🩸 **Vérlovag** (tank) • ❄️ **Fagylovag** (DPS) • 🧟 **Szentségtelen** — élőholt ragály + ghúl (DPS, csak Kitaszított) |
| 🌊 Sámán | ⚡ **Elemi** (caster) • 🔨 **Erősítő** (DPS) • 🌊 **Hullámhívó** (gyógyító) |
| ☯️ Szerzetes | 💨 **Szélfutó** (DPS) • 🍺 **Sörfőző** (tank) • 🌫️ **Ködszövő** (gyógyító) |
| ✝️ Pap | 🙏 **Fegyelem** (gyógyító) • 🌑 **Árnyék** (caster) • 🦴 **Csontpap** — csont-liturgia (gyógyító, csak Kitaszított) |
| 😈 Boszorkánymester | 🍂 **Átok** (caster) • 🔥 **Pusztítás** (caster) • 👿 **Demonológus** — démon-idézés (caster, csak Kitaszított) |
| 👁️ Démonvadász | 💥 **Tombolás** (DPS) • 🛡️ **Bosszú** (tank) |
| 🐉 Sárkányidéző | 🔥 **Perzselés** (caster) • 💧 **Megőrzés** (gyógyító) |

## Feltételek

- **Szint 25** az elsődleges kasztban.
- A **sötét specek** (💀 Nekromanta, 🧟 Szentségtelen, 🦴 Csontpap, ☣️ Pestishozó, 👿 Demonológus)
  csak **Kitaszítottként (Sötét/dark frakció) + bűnös (sinner) állapottal** választhatók; a
  Nekromantához ezen felül a **Sötét Beavatás** küldetés teljesítése is kell.
- Ha **elhagyod a Kitaszítottakat** (vagy a vezeklés-lánccal letisztulsz), a sötét
  specializációd **elveszik** — új specet kell választanod.

## Respec (meggondolás)

Ha mégis más irányt szeretnél, a Specializáció menü **Respec** gombjával (vagy
`/spec respec class`) **visszaváltod** a specializációd. Ez a **frakcióvalutádba kerül**
(alapból 100), és a specializációhoz kötött **talentpontjaid automatikusan visszatérülnek**,
hogy újra elköltsd őket. Utána új irányt választhatsz.

> A szakmáknak **is** vannak specializációi, ugyanígy működnek (lásd a [Szakmák](08-szakmak.md)
> oldalt). Egyszerre egy kaszt-spec **és** egy szakma-spec lehet aktív.

## 🐾 Társ-rendszer (Vadmester, Nekromanta, Szentségtelen, Boszorkánymester)

A **Vadmester** és a **Nekromanta** szintet lépő **társat** tarthat — és ez **bármilyen
mob** lehet, nem csak farkas vagy zombi!

1. Írd be: `/pet item` — kapsz egy **befogó eszközt**:
   - 🐺 **Vadmester → Ősi Kötés Póráza**: bármely **nem ellenséges** állatra/lényre jobb katt
     (pl. tehén, róka, ló, papagáj, delfin…).
   - 💀 **Nekromanta → Sötét Paktum-tekercs**: bármely **ellenséges / élőholt** mobra jobb katt
     (pl. zombi, csontváz, pók, creeper…).
2. **Jobb katt** a kiszemelt lényen a befogó eszközzel — társaddá fogadod, az eszköz elhasználódik.
3. `/pet name <név>` — adj nevet neki; `/pet summon` újra előhívja, `/pet dismiss` elküldi.

A társ **típusa, szintje, neve és XP-je** megmarad (a gazdád ölései szintezik), és **követ** téged.

### 🌒 Rituálé-idézés (Szentségtelen és Boszorkánymester)

A 🧟 **Szentségtelen** és a 😈 **Boszorkánymester** társa **nem befogható — rituálé idézi**:

1. Szerezd meg a kelléket (ritka zsákmány, csak a megfelelő szerepnek esik):
   - **Nyughatatlan Szív** — élőholtakból (zombi, csontváz, phantom), a Szentségtelennek.
   - **Démon-pecsét** — boszorkákból és illagerekből, a Boszorkánymesternek.
2. **Éjjel**, a kellékkel a kezedben **jobb katt** — a rituálé elfogyasztja a kelléket,
   és megköti a társat.
3. A társ **formája a szintjével fejlődik**: a Szentségtelené Ghúl → **Csontszolga** (15. szint)
   → **Förtelem** (25. szint); a Boszorkánymesteré Imp → **Tűz-démon** (15) → **Magma-behemót** (25).
   A magasabb forma **új rituálét (új kelléket)** kér.
4. Az idézett társ **erő-prémiumot** kap (+5 szintnyi statot) — a nehezebb beszerzés ellentételezése.

### 🎛️ Társ-GUI és állásmódok

A `/pet` parancs (vagy a `/menu` Társ-csempéje) **kattintható vezérlőpultot** nyit:
idézés, elbocsátás, átnevezés, állásmód-gombok és a Társvért állapota egy helyen.

- **Állásmódok:** **Támadás** (harcol és megvéd) / **Passzív** (csak követ) / **Maradj**
  (helyben vár). Váltás a GUI-ból, a `/pet stance <aktiv|passziv|marad>` paranccsal, vagy
  **sunyítás + jobb katt** a társadon.
- **Halálnak tétje van:** ha a társad elesik, **2 percig** nem idézheted újra.
- A kaszt-talentjeid **max-életerő bónuszának fele** a társadra is átszáll.

### 🛡️ Társvért

Ritka zsákmány szörnyekből (csak társ-tartó kasztoknak esik): **jobb katt vele a saját
társadon** — a társ **+páncélt és +életerőt** kap. A vért a gazdához kötődik: a társ
újraidézésekor is rajta marad.

### A társ harcol is — bármilyen lény legyen az

A társad **rendes harci pet**, függetlenül attól, milyen mob: nem a mob saját esze irányítja,
hanem a játék **maga viszi a célpontra és üti meg** — így egy befogott tehén vagy róka is verekszik!

- **Segít támadni:** ha megütsz egy lényt, a társad ráveti magát.
- **Megvéd:** ha téged támadnak, a társad a támadóra fordul.
- **Magától véd:** a közeli ellenséges mobokra magától rátámad.
- **Parancsok:** **sunyíts (Shift) + jobb katt** a társadon, hogy váltogasd az állását:
  **Támadás → Passzív → Maradj**. (Passzívan nem harcol, „Maradj" állásban egy helyben vár.)
- Soha nem támad **téged** vagy a **saját társaidat**.

A társ sebzése és életereje a **szintjével nő**. A nem szelídíthető társakat a játék
**automatikusan melléd teleportálja**, ha túl messze kerülnek.

## Melyik képességet mikor kapom meg?

Minden specializációnak 9–12 saját képessége van, amelyek a **25. és 45. szint** között
fokozatosan oldódnak fel. A pontos listát (mit tud, mennyibe kerül, hányadik szinten) a
[Képességek](05-kepessegek.md) oldal „Specializációs képességek" része tartalmazza.

---

➡️ Tovább: [Talentek](07-talentek.md) • [Vissza a tartalomhoz](README.md)
