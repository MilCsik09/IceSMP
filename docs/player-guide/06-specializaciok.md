# 6. Specializációk ✅

A **specializáció** a kasztod „kiteljesedése": egy irány, amit a **25. szinttől** választhatsz
az elsődleges kasztodnak. Ez nyitja meg a legerősebb, legtematikusabb képességeket (25–45.
szint között).

Hogyan? `/profile` → **Specializáció** menü → kattints a választott irányra. (Vagy parancs:
`/spec list`, `/spec choose <azonosító>`.) A menü mindig csak **a te kasztod** irányait mutatja,
és kiírja, ha valamihez még nem teljesíted a feltételt.

Összesen **31 specializáció** van. A **spec dönti el a szerepedet** (🗡️ közelharci DPS,
🏹 távolsági DPS, ✨ caster, 🛡️ tank, ➕ gyógyító) — vagyis milyen stílusban a leghatékonyabb:

| Kaszt | Specializációk (szerep) |
|---|---|
| 🧙 Varázsló | 🌊 **Elementalista** — tűz/jég/villám (caster) • 💀 **Nekromanta** — holtak/lélek (caster) |
| ⚔️ Harcos | 🩸 **Berserker** — támadás/vér (DPS) • 🛡️ **Védelmező** — pajzs/buff (tank) |
| 🏹 Íjász | 🎯 **Mesterlövész** — pontos lövések (táv. DPS) • 🐺 **Vadmester** — állat-társak (táv. DPS) |
| 🗡️ Orgyilkos | ☠️ **Méregkeverő** — mérgek (DPS) • 👻 **Fantom** — árnyék/félelem (DPS) |
| 🐻 Druida | 🐾 **Vadőr** (DPS) • 🌙 **Holdjós** (caster) • 🌳 **Védelmező** — kéreg (tank) • 💚 **Helyreállító** (gyógyító) |
| 🔆 Paplovag | ☀️ **Szentlélek** (gyógyító) • ⚖️ **Megtorló** (DPS) • 🛡️ **Védő** (tank) |
| 💀 Halállovag | 🩸 **Vérlovag** (tank) • ❄️ **Fagylovag** (DPS) |
| 🌊 Sámán | ⚡ **Elemi** (caster) • 🔨 **Erősítő** (DPS) • 🌊 **Hullámhívó** (gyógyító) |
| ☯️ Szerzetes | 💨 **Szélfutó** (DPS) • 🍺 **Sörfőző** (tank) • 🌫️ **Ködszövő** (gyógyító) |
| ✝️ Pap | 🙏 **Fegyelem** (gyógyító) • 🌑 **Árnyék** (caster) |
| 😈 Boszorkánymester | 🍂 **Átok** (caster) • 🔥 **Pusztítás** (caster) |
| 👁️ Démonvadász | 💥 **Tombolás** (DPS) • 🛡️ **Bosszú** (tank) |
| 🐉 Sárkányidéző | 🔥 **Perzselés** (caster) • 💧 **Megőrzés** (gyógyító) |

## Feltételek

- **Szint 25** az elsődleges kasztban.
- A **Nekromanta** különleges: csak **Kitaszítottként (Sötét/dark frakció) + bűnös (sinner) állapottal**, ÉS a
  **Sötét Beavatás** küldetés teljesítése után választható.

## Respec (meggondolás)

Ha mégis más irányt szeretnél, a Specializáció menü **Respec** gombjával (vagy
`/spec respec class`) **visszaváltod** a specializációd. Ez a **frakcióvalutádba kerül**
(alapból 100), és a specializációhoz kötött **talentpontjaid automatikusan visszatérülnek**,
hogy újra elköltsd őket. Utána új irányt választhatsz.

> A szakmáknak **is** vannak specializációi, ugyanígy működnek (lásd a [Szakmák](08-szakmak.md)
> oldalt). Egyszerre egy kaszt-spec **és** egy szakma-spec lehet aktív.

## 🐾 Társ befogása (Vadmester és Nekromanta)

A **Vadmester** és a **Nekromanta** szintet lépő **társat** tarthat — és ez **bármilyen
mob** lehet, nem csak farkas vagy zombi!

1. Írd be: `/pet item` — kapsz egy **befogó eszközt**:
   - 🐺 **Vadmester → Szelídítő Póráz**: bármely **nem ellenséges** állatra/lényre jobb katt
     (pl. tehén, róka, ló, papagáj, delfin…).
   - 💀 **Nekromanta → Lélekkötő Tekercs**: bármely **ellenséges / élőholt** mobra jobb katt
     (pl. zombi, csontváz, pók, creeper…).
2. **Jobb katt** a kiszemelt lényen a befogó eszközzel — társaddá fogadod, az eszköz elhasználódik.
3. `/pet name <név>` — adj nevet neki; `/pet summon` újra előhívja, `/pet dismiss` elküldi.

A társ **típusa, szintje, neve és XP-je** megmarad (a gazdád ölései szintezik), és **követ** téged.

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
