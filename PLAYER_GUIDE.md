# IceSMP — Játékos Tájékoztató

Üdv a fagyott királyságok földjén! Ez a tájékoztató **mindent** elmagyaráz, ami jelenleg
elérhető a szerveren: hogyan működnek a frakciók, a kasztok, a szakmák, a képességek, a
talentek, a gazdaság és a világesemények. A végén külön listában megtalálod, mi az, ami még
**fejlesztés alatt (WIP)** áll.

> ## 📂 Részletes, oldalankénti kézikönyv
> Ez az oldal az **áttekintés**. A **teljes, mindenre kiterjedő** leírást (pl. **minden spell**
> mit tud és mennyibe kerül, **minden szakma** mitől és mennyi XP-t kap) a külön, könnyen
> olvasható oldalakon találod — még egy nagyon fiatal játékos is megérti:
>
> - 🚀 [Kezdő lépések](docs/player-guide/01-kezdes.md)
> - ⚔️ [Frakciók](docs/player-guide/02-frakciok.md)
> - 💰 [Valuta és gazdaság](docs/player-guide/03-valuta-gazdasag.md)
> - 🧙 [Kasztok](docs/player-guide/04-kasztok.md)
> - ✨ [Képességek — minden spell költséggel](docs/player-guide/05-kepessegek.md)
> - 🌟 [Specializációk](docs/player-guide/06-specializaciok.md)
> - 🌳 [Talentek (talent-fa)](docs/player-guide/07-talentek.md)
> - ⛏ [Szakmák — minden XP-forrással](docs/player-guide/08-szakmak.md)
> - 🗡 [Relikviák és rituálék](docs/player-guide/09-relikviak.md)
> - 🌕 [Világesemények](docs/player-guide/10-vilagesemenyek.md)
> - 👑 [Királyság, raid és háború](docs/player-guide/11-raid-haboru.md)
> - 📜 [Küldetések](docs/player-guide/12-kuldetesek.md)
> - 🏰 [Frakcióterületek és saját birtok (claim)](docs/player-guide/13-teruletek.md)
> - ⌨️ [Parancsok listája](docs/player-guide/14-parancsok.md)
> - 👥 [Party (csapat)](docs/player-guide/15-csapat.md)
>
> Kezdőlap: [docs/player-guide/README.md](docs/player-guide/README.md)

> Jelölések: ✅ = kész és kipróbálható • 🚧 = részben kész • ⏳ = még nincs kész
> A számértékek (szintek, %-ok, idők) a szerver beállításaitól függően változhatnak — az
> alábbiak az alapértelmezések.

---

## 1. Az első lépések

1. **Válassz frakciót** (`/faction join <frakció>`) — ez dönti el a valutádat és a passzív bónuszodat.
2. **Nyisd meg a profilod** (`/profile`) — ez a **Karakterlap hub**: a fejen élő összegzés
   (frakció, kasztok + szintek, specializációk, szakmák + szintek, állapot, talentpontok,
   egyenlegek), és innen **gombokkal eléred az összes karakter-menüt**: Kaszt, Specializáció,
   Szakma, Talentek, Képesség-fa.
3. **Válassz kasztot** a Kaszt menüből, majd **igényeld a Lélekkapocsodat** (ugyanitt egy gomb).
4. **Ölj szörnyeket** a kaszt XP-ért, **bányássz/arass/horgássz** a szakma XP-ért.
5. Vedd fel a kaszt-próba **küldetésedet** (`/quest list`).

Első belépéskor egy rövid **bevezető cím-szekvencia** is lejátszódik. ✅

---

## 2. Frakciók ✅

Négy frakció létezik, mindegyiknek saját valutája és passzív bónusza van. Belépés:
`/faction join <red|blue|neutral|dark>`, kilépés: `/faction leave`.

Az **első csatlakozás ingyenes és időzítetlen**, és mindenki **Semlegesként kezd** — új
játékosként a **Menedék spawnján** jelensz meg, és amikor királyságot választasz,
a plugin **odateleportál az új királyságod spawnjára**. Ha nincs ágyad/respawn-horgonyod,
halál után is a **saját királyságod spawnján** éledsz újra.

A **Semlegesből bárhová ingyen** léphetsz át, és a **Sötétbe lépés is mindig ingyenes** (annak a
bűnös-feltétel + az örök paktum az ára). Minden más frakcióváltás (Láng↔Fagy, illetve vissza a
Semlegesbe — a `/faction leave` is ide számít!) a jelenlegi frakciód valutájában **alapból
500-ba** kerül, és utána **72 óráig** nem válthatsz újra (`factions.switch.cost` /
`factions.switch.cooldown-hours`). **Frakciót váltani csak a Menedék fővárosában (Caldestera)
állva lehet** (ott, ahol a királyság-választó hírnök NPC is áll) — amíg a szerveren nincs
kijelölt semleges főváros, ez a korlát nem él.

| Frakció | Passzív bónusz |
|---|---|
| 🔴 **Láng** (Perinfernicitas) | Immunis a **tűz / láva / forró blokk** sebzésére (hő-mesterség — a Nether biztonságos) |
| 🔵 **Fagy** (Cryghaliris) | Immunis a **fagyásra ÉS a fulladásra**; **50% eséllyel** nem veszít éhséget (víz-mesterség — végtelen búvárkodás) |
| ⚪ **Menedék** (Ryanora & Caldestera) | **Nincs zuhanás-sebzés** (esésimmunitás); a **nem-ellenséges mobok és az endermanök** nem támadják; **adómentes** |
| ⚫ **Kitaszított** (A Kitaszítottak) | Immunis a wither-sebzésre; az **élőhalottak nem támadják** (a legerősebb PvE-passzív — a sinner-jelölés az ára) |

> A passzívok **egy szintre** vannak hangolva: mindegyik kb. egyformán hasznos, csak más
> helyzetben (Piros a tűznél, Kék a víz alatt, Semleges esésnél és vándorlásnál, Sötét a szörnyek közt).

A chatben a neved a **frakciód színében** jelenik meg (a rang-prefixszel együtt).

**Fontos a Kitaszítottakról (Sötét frakció):**
- Csak az léphet be, akit **bűnössé (sinner)** bélyegeztek.
- Belépéskor megköttetik a **sötét paktum** — onnantól a bűnös jelölést **soha nem lehet
  levenni** (még frakcióelhagyás után sem). Az egyetlen visszaút a **vezeklés-küldetéslánc**
  (lásd a Küldetéseknél).

**Hogyan leszel bűnös?** **4 bűnnél** automatikusan **száműznek a Kitaszítottak közé** (örök
paktummal). Bűnt háromféleképp követhetsz el:
- **Gyilkosság:** megölsz egy másik játékost → **+1 bűn**.
- **Árulás:** a **saját frakciótársadat** ölöd meg → **+2 bűn** (a Semlegesek laza közössége
  kivétel — köztük az ölés sima gyilkosság).
- **Lopás:** egy **másik frakció területén** konténerből (láda, hordó, kemence…) tárgyat
  veszel ki → **+1 bűn** (egy fosztogatás-sorozat területenként egyszer számít).

Kivétel: **raid alatt** a **jelentkezett harcosok** (`/faction raid join`) közti **ölés és az
ellenség földjén való zsákmányolás nem számít bűnnek** — aki nem jelentkezett, arra raid alatt
is a békeidős szabályok élnek.

**Fejvadászat:** aki elér **3 bűnt**, az **körözötté** válik — a fejére **fejpénz** kerül (a
`/bounty` lista mutatja). Aki megöl egy körözöttet, **megkapja a fejpénzt** és **nem kap érte
bűnt** (igazságos kivégzés); a bűnöző bűnszámlálója nullázódik.

---

## 3. Gazdaság ✅

### Valuták és bank
Minden frakciónak saját **token**-je van (Piros / Kék / Semleges / Sötét). A pénz kétféleképp
létezik: **fizikai itemként** (token a táskádban) és **banki egyenlegként**.

- `/bank balance` — egyenlegeid • `/bank deposit` — tokenek bankba • `/bank withdraw <valuta> <összeg>` — kivét itemként
- `/currency balance` — egyenleg-nézet

> 💡 A **banki ügyintézés** (be-/kivét és **valutaváltás**) alapból **csak a fővárosokban** működik.
> A **közvetlen utalás** (`/currency pay`) a KP-alapú gazdaságban **alapból ki van kapcsolva** — a
> játékos–játékos csere kézből kézbe (token/item) vagy a **piacon** keresztül zajlik.

### Dinamikus árfolyam
A valuták értéke **élő, kínálat-alapú**: minél több van egy valutából a szerveren összesen,
annál **kevesebbet ér** a többihez képest. Aki figyeli a piacot, jól járhat a váltáson.

- `/currency rates` — aktuális árfolyamok (kínálat, érték, váltási arány)
- `/currency exchange <összeg> <honnan> <hová>` — valutaváltás az aktuális árfolyamon (díjjal)

### Piactér ✅
Játékos–játékos kereskedés:
- `/market sell <ár> [valuta]` — a **kezedben tartott tárgyat** listázod eladásra (alapból a
  frakciód valutájában). Max. 5 tételed lehet egyszerre.
- `/market` — böngésző felület; kattints egy tételre a **megvásárláshoz** (a banki egyenlegedből fizet).
- `/market cancel` — visszavonod a saját tételeidet (visszakapod az itemeket).
- **Eladási díj:** minden eladásból ~10% „elég" (eltűnik a gazdaságból) — ez fékezi az inflációt.
- **Aukció:** `/market auction <kikiáltási ár> [óra] [valuta] [buyout:<ár>]` — a kezedben tartott
  tárgyra **licitálós** aukciót indítasz (alapból 24 óra, max. 72). A `buyout:<ár>` opcionális:
  aki eléri, **azonnal megnyeri** az aukciót. A GUI-ban kattintás-típussal licitálsz — **bal-katt**:
  minimum licit, **jobb-katt**: nagyobb ugrás, **shift-katt**: azonnali buy-out.
  A licit a **bankodból zárolódik**, túllicitálásnál **automatikusan visszajár**. Lejáratkor a
  legmagasabb licit (díj levonása után) az eladóé, a tárgy a nyertesé — ha épp nem vagy fenn,
  belépéskor vagy `/market claim`-mel kapod meg. Élő licites aukció **nem vonható vissza**.
  (Aukciónál a licit fix összeg — a reputáció-felár csak a fix-áras tételekre vonatkozik.)
- **Frakció-reputáció:** a vételár attól is függ, milyen viszonyban van a frakciód az
  eladóéval. **Ellenséges** (vagy épp raidben álló) frakciótól drágább (+25% felár, ami elég),
  **szövetségestől** olcsóbb (−10%). Semleges viszonylatban nincs változás.

### Adomány-láda ✅
Közösségi, **ár nélküli** ajándék-tár — mindenkié, mindenki tehet bele és vehet ki belőle:
- `/adomany add` — a **kezedben tartott tárgyat** (teljes stack) a közös ládába teszed.
- `/adomany` — megnyitod a böngészőt; kattints egy tárgyra és **elviszed** (nincs fizetés).
- A ládának van teljes kapacitása, és annak van egy plafonja, hogy egy adományozónak
  hány **el nem vitt** tétele lehet egyszerre benne (admin-konfigurálható).

### Árfolyamtáblák 📊
A fővárosokban admin által lerakott **hologram-táblák** mutatják a valuták élő, kínálat-alapú
értékét — „tőzsdei" kijelző, ami magától frissül. Ugyanezt mindig lekérheted `/currency rates`-szel is.

### Money sinkek és események
- **Állampolgári adó:** óránként a frakciótagok a saját valuta-egyenlegük **2%-át**, de
  **legalább 2 érme fejadót** befizetnek a frakciókasszába (a Semlegesek mentesek). Az üresen
  tartott számla sem kibúvó: amit a számla nem fedez, **hátralékként** gyűlik (legfeljebb 50
  érméig), és a következő beszedésekkor automatikusan levonódik. Aki tartósan a plafonon ülő
  hátralékkal, fizetés nélkül „csal", azt a **Számvevők feljelentik** — **bűnt** kap, és a bűnök
  súlya a Kitaszítottak közé taszíthatja. Ez csökkenti a forgalomban lévő pénzt.
- **Kereslet-sokk** (heti gazdasági esemény): időnként egy véletlen valuta értéke **átmenetileg
  x1.2–1.6-ra ugrik** (broadcast jelzi) — kereskedési lehetőség.
- **Lélekkő:** a magas szintű (Lvl 3+) skálázott szörnyek eséllyel **Csontveretet** dobnak — a
  veszélyes, spawntól távoli vidékek így gazdaságilag is megérik.
- **Frakció-boltok:** a fővárosi **bolt-NPC-kre jobb-kattintva** fix áron vehetsz portékát a banki
  egyenlegedből — a pénz eltűnik (money sink).
- **Kereskedő-karaván:** időnként egy **vándorkereskedő** bukkan fel a világban (broadcast jelzi,
  merre, és meddig marad). Amíg itt van, **jobb-katt a karaván-NPC-re** → ritka portékák boltja fix
  áron (a pénz szintén eltűnik). Ha lekésed, legközelebb máshol tűnik fel.

---

## 4. Kasztok ✅

**13 kaszt** közül választhatsz a `/profile` → Kaszt menüből. **Egy kasztod lehet** — ezt
választod ki, és ez határozza meg a képességeidet és a specializációdat. (A kaszt választása
végleges; ha új kasztot szeretnél, egy adminnak kell resetelnie: `/class admin resetclass`.)

Minden kasztnak saját **stílusa**, saját **Lélekkapocs-tárgya** (a „spellbook") és saját
**erőforrása** (az Erő-csík, lásd lentebb) van:

| Kaszt | Stílus | Lélekkapocs | Erőforrás |
|---|---|---|---|
| 🧙 **Varázsló** | Elemi és kontroll mágia, távolsági ráolvasások | 📖 Caldesterai Rúnakódex | Mana |
| ⚔️ **Harcos** | Közelharci erő, kitartás, buffok | 📯 Sárkánykirály Kürtje | Düh |
| 🏹 **Íjász** | Távolsági harc, mozgékonyság, csapdák | 🎒 Soleil Vadásztarsolya | Fókusz |
| 🗡️ **Orgyilkos** | Lopakodás, gyors kitörések, gyengítés | 🪨 Homály-szilánk | Energia |
| 🐻 **Druida** | Alakváltó természet-mágia (harc, kontroll, tank, gyógyítás) | 🌱 Aetrinita Sarja | Természeti Erő |
| 🔆 **Paplovag** | Szent harc, védelem és gyógyítás | 🔔 Hajnaltűz Harangja | Szent Erő |
| 💀 **Halállovag** | Rúna-mágia, vér és fagy, közelharci tank/DPS | 💀 Néma Rúnakoponya | Runikus Erő |
| 🌊 **Sámán** | Elemek, totemek, gyógyítás és erősítés | 🪬 Ősvihar Totemje | Mana |
| ☯️ **Szerzetes** | Gyors közelharc, csi-energia, gyógyítás | 🎍 Élet Ága | Csi |
| ✝️ **Pap** | Szent és árny mágia, gyógyítás | 🕯️ Asterlayna Gyertyája | Mana |
| 😈 **Boszorkánymester** | Átkok, démonok és pusztító tűz | 🏮 Kárhozat Lámpása | Lélekerő |
| 👁️ **Démonvadász** | Mozgékony démoni harc és bosszú | 👁️ Hasadék Szeme | Fúria |
| 🐉 **Sárkányidéző** | Sárkány-eszencia: perzselő mágia és gyógyítás | 🐲 Sárkányvér-fiola | Eszencia |

### Szintezés
- A kaszt **mob ölésből** kap XP-t: alap **10 XP / ölés**, plusz a szörny szintjéért **+3 XP /
  mob-szint** (lásd Mob-szintezés). Csak ellenséges mobok adnak XP-t.
- **Progresszív szintgörbe:** az n. szintlépés ára `60 + (n-1)×10` XP — vagyis minél magasabb
  vagy, annál többet kell ölnöd a következő szintért. Max szint: **50**.

### Lélekkapocs (a „spellbook")
A kaszt képességeit egy **kaszt-tematikus tárggyal** használod (a fenti táblázat *Lélekkapocs*
oszlopa mutatja, melyik kaszté melyik). A specializációd ugyanazt a Lélekkapcsot használja, mint
az alapkasztod.

- **Jobb katt** = a kiválasztott képesség elsütése.
- **Lopakodás + bal katt (ütés)** = váltás a feloldott képességek között (kaszt-specifikus
  hanggal; az action bar mutatja a kiválasztott képességet + a költségét).
- **Lopakodás + görgetés** (Lélekkapocsral a kézben) = gyors spell-váltás előre/hátra — a
  hotbar-slot nem vált, és a Lélekkapocs **neve mindig az épp kiválasztott képességet** mutatja.
- **★ Kedvencek:** a spellkönyvben (`/spellbook`) **shift-katt** kedvencnek jelöl egy feloldott
  képességet; ha van kedvenced, a görgetés **csak a kedvenceket lépkedi** (üres lista = mindent).
  A spellkönyv tölcsér-gombja a „csak feloldottak" szűrő.
- Ha elveszne: a Kaszt menüből bármikor újra igényelheted (admin: `/job givecatalyst`).
- A Lélekkapcsot **nem lehet** craftolásnál vagy kemencében elhasználni — védett.
- **Közelharci kasztoknak** (Harcos, Paplovag, Halállovag, Szerzetes, Démonvadász) a kézben
  tartott **kard vagy balta is Lélekkapocsként működik** — harc közben nem kell tárgyat váltani.
- **Dinamikus skálázás:** a képességek ereje a kaszt-szinttel nő (+0,5%/szint), és az
  **Arkán Hatalom** talent (+2%/rang) tovább növeli (a bónusz +50%-ig kúszhat).

### Képesség-fa
A Kaszt menü **„Képesség-fa"** gombja megmutatja a kasztod (és a választott specializációd)
összes képességét **feloldási szint szerint**: a feloldottak ragyognak, a zároltak a szükséges
szintet mutatják.

### Osztály-erőforrás — az Erő-csík ⚡ ✅
Minden kasztnak van egy **erőforrása** (Mana, Düh, Energia, Fókusz, Csi, Runikus Erő…), amit a
**HUD oldalsávban** (a képernyő jobb szélén, a scoreboardon) egy **színes, 10 szegmenses csík**
mutat. **Ez a képességeid fő „üzemanyaga" — a legtöbb spell ezt fogyasztja.**

**Hogyan működik?**
1. **A legtöbb képesség ennyit fogyaszt** az Erő-csíkból. A költség a képesség erejétől függ:
   a gyors, pörgős alapképességek **olcsók** (~15–20), a nagy ultik **drágák** (~50).
2. A csík **magától visszatöltődik** idővel — de **kasztonként másképp viselkedik**:
   - **Düh-típus** (Harcos, Halállovag, Démonvadász): harcon kívül lassan **ürül**, minden
     **bevitt ütés tölti** (+8), harcban lassú regen is fut — a dühöt a harc termeli!
   - **Energia-típus** (Orgyilkos, Szerzetes ~14/mp; Íjász ~11/mp): gyorsan visszapörög.
   - **Mana-típus** (Varázsló, Sámán, Pap, Boszorkánymester, Evoker, Druida: 120-as tár;
     Paplovag: 110): nagyobb készlet, lomhább (~7/mp) regen.
3. Ha **nincs elég** erőforrásod egy képességhez, az **nem sül el** — az action bar jelzi
   (pl. „Nincs elég Mana!").

Így a varázslásnak **ritmusa** van: pár képesség után meg kell várnod, míg a csík újratöltődik —
nem tudsz vég nélkül spammelni.

> **Hibrid költség — minden spell a hozzá illő „valutát" fizeti:**
> - 🩸 **Vér-mágia** (ön-áldozó képességek, pl. Berserker/Nekromanta) → **életet (❤)** kerül.
> - ✨ **Nagy rituálé / idézés / időjárás / szignatúra-ulti** → **tapasztalatot (XP)** kerül.
> - 🍗 **Nehéz fizikai** (állások, második lélegzet, totem/állat-idézés) → **éhséget** kerül.
> - ⚡ **Minden más** (a hétköznapi mágia és stamina) → az **osztály-erőforrást** (a fenti sáv).
>
> A spellkönyv és az action bar mindig kiírja, melyik képesség mit kér. (A `spells.resource.*`
> configgal a küszöbök és a visszatöltés hangolható, vagy `enabled: false`-szal mindenki a régi
> éhség/XP/HP modellre vált.)

---

## 5. Specializációk ✅

A **25. szinttől** az elsődleges kasztod **specializálódhat** — a legegyszerűbben a
`/profile` → **Specializáció** menüből (vagy paranccsal: `/spec list`, `/spec choose <id>`).
A menü mutatja, melyik elérhető és mi a feltétele. Összesen **31 specializáció** van, és a
legerősebb képességek (Lvl 25–45) csak így érhetők el.

A **spec határozza meg a szerepedet** — milyen stílusban a leghatékonyabb a kaszt. A szerepek:
🗡️ közelharci DPS, 🏹 távolsági DPS, ✨ caster, 🛡️ **tank**, ➕ **gyógyító**.

| Kaszt | Specializációk (szerep) |
|---|---|
| 🧙 Varázsló | 🌊 **Elementalista** (caster) • 💀 **Nekromanta** (caster) |
| ⚔️ Harcos | 🩸 **Berserker** (DPS) • 🛡️ **Védelmező** (tank) |
| 🏹 Íjász | 🎯 **Mesterlövész** (táv. DPS) • 🐺 **Vadmester** (táv. DPS) |
| 🗡️ Orgyilkos | ☠️ **Méregkeverő** (DPS) • 👻 **Fantom** (DPS) |
| 🐻 Druida | 🐾 **Vadőr** (DPS) • 🌙 **Holdjós** (caster) • 🌳 **Védelmező/Ironbark** (tank) • 💚 **Helyreállító** (gyógyító) |
| 🔆 Paplovag | ☀️ **Szentlélek** (gyógyító) • ⚖️ **Megtorló** (DPS) • 🛡️ **Védő** (tank) |
| 💀 Halállovag | 🩸 **Vérlovag** (tank) • ❄️ **Fagylovag** (DPS) |
| 🌊 Sámán | ⚡ **Elemi** (caster) • 🔨 **Erősítő** (DPS) • 🌊 **Hullámhívó** (gyógyító) |
| ☯️ Szerzetes | 💨 **Szélfutó** (DPS) • 🍺 **Sörfőző** (tank) • 🌫️ **Ködszövő** (gyógyító) |
| ✝️ Pap | 🙏 **Fegyelem** (gyógyító) • 🌑 **Árnyék** (caster) |
| 😈 Boszorkánymester | 🍂 **Átok** (caster) • 🔥 **Pusztítás** (caster) |
| 👁️ Démonvadász | 💥 **Tombolás** (DPS) • 🛡️ **Bosszú** (tank) |
| 🐉 Sárkányidéző | 🔥 **Perzselés** (caster) • 💧 **Megőrzés** (gyógyító) |

Így **minden szerep lefedett**: van tank, gyógyító, caster, közel- és távharci DPS bőven, és sok
kaszt több szerepre is alkalmas a specválasztással (pl. egy Paplovag lehet gyógyító, DPS vagy tank).

- A **Nekromanta** különleges: csak **Kitaszítottként (Sötét/dark frakció) + bűnös állapottal**, ÉS a **Sötét
  Beavatás** küldetés teljesítése után választható.
- **Respec:** meggondolhatod magad — a Specializáció menü **Respec** gombjával (vagy
  `/spec respec <class|profession>`) a frakcióvalutádért (alapból 100) visszaváltod a speced;
  a spec-hez kötött talentpontjaid automatikusan visszatérülnek.
- `/spec info` — aktuális specjeid.

---

## 6. Képességek (spellek) ✅

Több mint **390 képesség** van; **minden kaszt és specializáció saját, egyedi készletet** tanul
(nincs átfedés). A szintekkel **automatikusan feloldódnak**. Alább kasztonként a *jellegüket*
mutatjuk be — a **teljes lista** (minden spell pontos hatásával, költségével és feloldási
szintjével) a [Képességek oldalon](docs/player-guide/05-kepessegek.md) található.

### Hogyan működik egy képesség?
- **Költség (hibrid):** minden képesség a hozzá illő „valutát" fizeti — a legtöbb az
  **osztály-erőforrásodat** (Erő-csík: Mana/Düh/Energia…), a vér-mágia **életet (❤)**, a nagy
  rituálék/ultik **XP-t**, a nehéz fizikai képességek **éhséget**. Ha nincs elég, a képesség
  **nem sül el** (az action bar és a spellkönyv kiírja, mibe kerül). Az Erő-csík magától
  visszatöltődik (lásd a 4. szakaszt).
- **Visszatöltés (cooldown):** minden képességnek van egy újrahasználati ideje is. A **60 mp-nél
  hosszabb** cooldownok kilépés után is megmaradnak. (Tehát két kapu van: a költség és a cooldown —
  a pörgős képességeket inkább a költség, a nagy ultikat a cooldown fékezi.)
- **Célzás:** van önmagadra ható (self), célzott (a célkereszted alá), és terület (AOE) képesség.

### Varázsló — elemi és kontroll
Mana Nyíl (gyors sebzés), Fagyérintés (fagyaszt + lassít), Wisplight (fény), Gyökerezés (AOE
lassítás), Arkán Pajzs (felszívás + ellenállás), Megzavarás, **Villanás** (rövid teleport),
Esőtánc, Arkán Lökés (AOE + ellökés), Gravitációs Örvény (lebegtet + lassít).

### Harcos — erő és kitartás
Csatakiáltás (erő + sebesség), Fegyverzet, Hősi Szökellés (előreugrás), Markolatütés (sebzés +
lassítás), Belső Fókusz, Második Lendület (öngyógyítás), Lökéshullám, Forgószél (AOE + ellökés),
Vasbőr (ellenállás), Megfélemlítés (AOE gyengítés).

### Íjász — távolság és mozgás
Vadászjel (megvilágít), Sasszem, Hátraszökkenés (hátraugrás), Sortűz, Átütő Lövedék
(szellemnyíl), Csapdázás (lassítás), Dupla Ugrás, Álcázás (láthatatlanság), Nyílzápor (7 nyíl),
Szélléptek (sebesség + ugrás).

### Orgyilkos — lopakodás és gyengítés
Tőrhajítás, Adrenalin (sebesség + sietség), Árnyéklépés (a célpont mögé teleportál), Ínmetszés
(lassítás), Elsötétítés (vakítás), Füstbomba, Kitérés (hátraugrás + láthatatlanság), Fojtás
(erős lassítás), Árnyéksuhanás (gyors kitörés), Haláljegy (megjelöl + gyengít).

### A többi kaszt — jelleg
A 9 további kasztnak is **teljes, egyedi alap-készlete** van (a pontos spelleket lásd a
[Képességek oldalon](docs/player-guide/05-kepessegek.md)):

- 🐻 **Druida** — alakváltó természet-mágia: karom- és tüske-sebzés, gyökerező kontroll,
  napsugár/holdfény ráolvasások, és a specekkel **tank** (kéreg-páncél) vagy **gyógyító**
  (életfakasztó regeneráció) szerep is.
- 🔆 **Paplovag** — szent harc és védelem: pörölycsapások, megszentelt föld, pajzsok és
  szövetséges-gyógyító/buffoló aurák; lehet **gyógyító**, **DPS** vagy **tank**.
- 💀 **Halállovag** — rúna-mágia: vér- és fagy-csapások, élet-elszívás, fagyasztó kontroll,
  közelharci **tank** (vér) vagy **DPS** (fagy).
- 🌊 **Sámán** — elemek és **totemek**: villám- és láva-mágia, lerakható totemek, amelyek
  pulzálva gyógyítják a szövetségeseket vagy sebzik az ellenfeleket; **caster**, **DPS** vagy
  **gyógyító**.
- ☯️ **Szerzetes** — gyors csi-alapú közelharc: kombó-ütések, rúgások, sörfőző **tank**-fortélyok,
  és köd-alapú **gyógyítás**.
- ✝️ **Pap** — szent és árny-mágia: gyógyítás, pajzsok, fény-sebzés, illetve árny-oldalon
  elme- és wither-mágia (**caster**).
- 😈 **Boszorkánymester** — átkok, démon-idézés és pusztító tűz: méreg/wither DoT-ok, lélek-mágia,
  felperzselő robbanások (**caster**).
- 👁️ **Démonvadász** — mozgékony démoni harc: kitörések, szárnyalás, perzselő csapások és
  bosszúálló **tank**-forma.
- 🐉 **Sárkányidéző** — sárkány-eszencia: perzselő tűz- és kék-mágia távolról, illetve
  smaragd-eszenciás **gyógyítás**.

### Specializációs készletek (Lvl 25–45)
Az alábbi 8 (eredeti) spec részletes felsorolása mellett a **többi 23 spec** is teljes,
Lvl 25–45 közt feloldódó ulti-készlettel rendelkezik — a teljes listát a
[Specializációk](docs/player-guide/06-specializaciok.md) és a
[Képességek](docs/player-guide/05-kepessegek.md) oldal tartalmazza.
- **Elementalista:** Naptánc, Tűzgolyó, Fagyrobbanás (AOE fagy), **Mennykőcsapás** (valódi
  villám), Lucky Star, Parázsvihar (AOE gyújtás), Örvényrántás (odahúz), Kőbőr, Viharlöket,
  **Elemi Túltöltés** (tűz + fagy + robbanás ulti).
- **Nekromanta:** Sírkéz (wither), Lélekszívás (AOE + öngyógyítás), Csontfagy, Rettegésaura,
  Csontdárda, Dögvészérintés, **Holtak Hada** (zombihorda idézése), Halálpaktum (saját életért
  cserébe erő), Kísértetforma, **Csontíjászok** (íjas csontvázak), Lélekaratás ulti.
- **Berserker:** Lakoma, Düh, Vakmerő Csapás (önsebzéses nagy ütés), Vérszomj, Földcsapás
  (felrepít), Üvöltés, Megállíthatatlan, Vad Ugrás, Vérfürdő, **Berserk** ulti.
- **Védelmező:** Bástya, Kihívás (taunt), Megerősítés, Pajzsfal, **Gyógyító Szó** és **Égisz**
  (szövetséges-buffoló aurák!), Vasbástya, Pajzsroham, Megtorlás, **Végső Ellenállás** ulti.
- **Mesterlövész:** Pehelykönnyű Lépte, Sasles, Fejlövés, Duplalövés, Jelzőfény, Térdlövés,
  Magasles, Tökéletes Fókusz, Szellemsortűz, **Mesterlövés** (12 sebzés, 20 blokk).
- **Vadmester:** Barátság, **Farkashívás**, **Méhraj**, Mérgező Csirke, **Ősi Kötelék** (a saját
  társaidat buffolja), Vastag Irha, **Pandaőrség**, Ragadozó Érzékek, Csorda Rohama,
  Sólyomcsapás, **Vad Falka**, Vadak Ura ulti.
- **Méregkeverő:** Méregcsapás, Toxinnyíl, Savfröccs (AOE méreg), Zsibbasztószer, **Ellenméreg**
  (megtisztít), Ragály, Bénító Csapás, Mérgező Felhő, Sorvasztó Méreg, **Gyilkos Galóca** ulti.
- **Fantom:** Elrejtőzés, Szellemléptek, **Fázisugrás**, Kísértés (sötétség), Rémület, Hidegfolt
  (AOE fagy), Éteri Forma, Fantomszorítás (levitálva tartja az áldozatot), Rémsuttogás,
  **Kísértet** ulti.

> **Idézett társak (minionok):** a Nekromanta és a Vadmester idézései **gazda-hűek** — sosem
> fordulnak ellened vagy a többi társad ellen —, automatikusan rátámadnak a célpontodra, és
> egy idő után eltűnnek. **Vezérlés:** lopakodás + jobb katt a saját társadon → állásmód-váltás
> (**Támadás** → **Passzív**, nem támad → **Maradj**, helyben fagy). Egyszerre korlátozott
> számú társad lehet, és a **kaszt Életerő-talentjeid a társaidat is erősítik** (+HP).

> **Nekromanta lélekszilánkok:** ha **Nekromanta-specet** játszol, minden megölt ellenség
> után **lélekszilánkot** kapsz. `/souls` — megnézed az egyenleged; `/souls champion` —
> a szilánkokból egy **megerősített Wither-csontváz bajnokot** idézel, ami a szokásos
> időzített idézéseknél erősebb (az idézés-limited rá is vonatkozik).

> **Idézett társak ≠ társ-állat (pet):** a fenti minionok a spell által idézett, ideiglenes
> segítők. Ettől külön van a **tartós társ-állat** (`/pet`): a Vadmester/Nekromanta egy
> spec-specifikus **befogó tárggyal** befoghat egy mobot, ami **szintet lép (max 30)**,
> előhívható és eltehető — részletek: [Képességek oldal](docs/player-guide/05-kepessegek.md).

> **Spell-mesterség (`spell mastery`):** a feloldott képességeidet **frakcióvalutáért
> rangsorolhatod** (max. **5 rang**), ami rangonként **−8% cooldownt** és **+5% erőt** ad —
> tiszta, nem tolakodó „képesség-erősítés" a talentek és a kaszt-szint fölött.

> **Kombók és kombó-láncok:** bizonyos képesség-párok gyors egymásutánban elsütve „⚡ Kombó!"-t
> adnak (gyorsabb felépülés), a **3 lépéses láncok** befejezője pedig **+25% erővel** csap be
> (pl. Varázsló: Fagyérintés → Arkán Lökés → Tűzgolyó). A cast után az action bar mutatja a
> **nyíló kombó-ablakot** és a következő lépést — részletek: [Képességek oldal](docs/player-guide/05-kepessegek.md).

---

## 7. Talentek ✅

A szintjeid **talentpontokat** termelnek, amiket passzív erősítésekre költhetsz a
`/profile` → **Talentek** menüből (kattints a fejlesztendő talentre), vagy paranccsal
(`/talent`, `/talent spend <class|profession> <talent>`).

- **Kaszt ponttár:** minden **5 kasztszint** = 1 pont (a kasztod szintjéből).
- **Szakma ponttár:** az **összes szakmád** szintjeiből, minden **10 szint** = 1 pont.

### Általános talentek (mindenkinek)
| Talent | Hatás | Max rang |
|---|---|---|
| Életerő | +2 max élet / rang | 5 |
| Fürgeség | +mozgási sebesség / rang | 5 |
| Erő | +0.5 sebzés / rang | 5 |
| Tudásszomj | +5% kaszt XP / rang | 4 |
| Szorgalom | +10% szakma XP / rang | 5 |
| Kitartás | +1 max élet / rang | 3 |

### Kötött talentek (WoW-stílus)
Vannak **kaszt-, specializáció- és szakma-kötött** talentek is — ezeket **csak az látja és
költheti**, aki teljesíti a feltételt, és csak nála hatnak. Pár példa: *Brutalitás* (Berserker,
+1 sebzés/rang), *Lélekpaktum* (Nekromanta, +élet), *Falkavezér* (Vadmester), *Rendíthetetlen*
(Védelmező, +3 élet/rang), *Fegyvermesteri Fokozat* (Harcos), *Bányász Állóképesség* (Bányász).

> **Respec és talentek:** ha visszaváltod a specializációd, a spec-kötött talentjeid hatása
> megszűnik, és a beléjük fektetett pontok **automatikusan visszakerülnek** a tárba.

**Előny/hátrány tipp:** az attribútum-talentek (élet/sebzés/sebesség) azonnal és folyamatosan
hatnak — jók túlélésre vagy gyors farmolásra. Az XP-bónusz talentek (Tudásszomj, Szorgalom)
hosszú távon térülnek meg: minél korábban beléjük fektetsz, annál gyorsabban szintezel utána.

---

## 8. Szakmák ✅

WoW-mintára **két fő szakmád** lehet — **egy gyűjtögető és egy készítő** — a másodlagos szakmák
pedig mindenkinek automatikusan fejlődnek. A legkönnyebben a `/profile` → **Szakma** menüből
tanulsz és nézed a szintjeidet (vagy paranccsal: `/profession join <szakma>`, `/profession info`).

| Kategória | Szakmák | XP-forrás |
|---|---|---|
| 🧺 **Gyűjtögető** (1 választható) | ⛏ Bányász • 🌿 Gyógynövényész • 🪓 Favágó | érc / termény+virág+bogyó / rönk |
| 🔨 **Készítő** (1 választható) | ⚒ Kovács • ⚗ Alkimista • ✨ Bűvölő | páncélcraft + smithing / főzet kivétele / bűvölőasztal |
| 🎣 **Másodlagos** (mindenkié) | 🐟 Halász • 🍲 Szakács | horgászat / étel sütése |

- **Szintezés:** progresszív görbe (az n. szint ára `100 + (n-1)×15` XP), max **50**.
- **A szakma XP külön tárolódik** szakmánként — ha az admin szakmát vált neked, a régi szinted
  megmarad és visszatanulható.
- A **25. szinttől** minden szakma **specializálódhat** (szakmánként 2 irány, pl.
  Fegyverkovács / Páncélkovács, Főzetmester / Transzmutátor, Séf / Hentes).

### Craft-korlátozások
Bizonyos tárgyakhoz **kaszt- vagy szakmaszint kell** — ez teszi értékessé a szakmákat és a köztük
lévő kereskedelmet. Ha nem felelsz meg, a craft eredménye nem jön létre, és üzenetet kapsz. A
jelenlegi kapuk:

| Tárgy | Kell hozzá |
|---|---|
| Netherite felszerelés (fegyver + páncél) | **Páncélkovács 25** |
| Netherite-rúd (finomítás) | **Bányász 20** |
| Számszeríj, pajzs | **Favágó 8** |
| Főzőállvány | **Alkimista 5** |
| Bűvölő-asztal | **Enchanter 5** |
| Torta, sütőtökös pite, nyúlpörkölt | **Séf 6** |

A nyers alapok (fapáncél, kőszerszám, íj, sült húsok) szabadok maradnak — a kapuk a **csúcs-
kimenetet és a szakma-„állomásokat"** védik, nem a korai játékot. (Az alkimista főzés és az
enchanter bűvölés érdemi kapuzása külön fejlesztés — lásd a tervet.)

---

## 9. Relikviák ✅

Egyedi, legendás tárgyak. Szabályok: **egy relikviából csak egy létezhet** a szerveren, és
**14 nap inaktivitás** után a relikvia füstként elenyészik (a tulajdonos belépésekor), majd
újra megszerezhetővé válik.

- **A Mételytépő** — harci fejsze, ami megjelöli a bűnösöket és ítéletet hajt végre rajtuk.
  **Fegyver-relikvia:** ha megölnek a PvP-ben, az új gazdája a gyilkosod lehet!
- **4 frakció-elytra** (csak a tulajdonos + a megfelelő frakció tagja használhatja):
  - 🔴 **Főnix-szárny:** tűz/láva-immunitás; a zuhanás lángviharban végződik (felgyújtja a közeli ellenfeleket).
  - 🔵 **Zúzmara-szárny:** fagyimmunitás; felszálláskor megfagyasztja a környező ellenfeleket.
  - ⚪ **Vándorszél:** nincs zuhanósebzés; felszálláskor széllökés-boost.
  - ⚫ **Csontszárny:** wither-immunitás; éjszakai repüléskor árnyformába vált (láthatatlanság + sebesség).
- A **passzív relikviák (a szárnyak) PvP-ben védettek** — nem cserélnek gazdát.

### Rituálé-oltárok 🔮
A négy elytra-relikvia nem craftolható — **oltáron kell megidézni** őket. Keress (vagy építs)
egy adott **oltár-blokkot**, gyűjtsd össze a hozzá tartozó **áldozati tárgyakat**, majd
**lopakodás (SHIFT) + jobb katt** az oltáron:

| Relikvia | Oltár-blokk | Áldozat (alap) |
|---|---|---|
| 🔴 Főnix-szárny | Magmatömb | 8 lángrúd, 16 tűzcsóva, 1 aranytömb |
| 🔵 Zúzmara-szárny | Kék jég | 16 tömör jég, 8 prizmarin-kristály, 1 gyémánttömb |
| ⚪ Vándorszél | Ametiszttömb | 32 toll, 8 fantommembrán, 1 smaragdtömb |
| ⚫ Csontszárny | Lélektalaj | 8 csonttömb, 1 wither-csontvázkoponya, 2 netherit-törmelék |

Ha a relikviának már van **élő tulajdonosa**, nem idézhető meg újra (egy-példány szabály).

### Egyéb oltárok — nem csak relikvia
Az oltárok nem csak a szárnyakat adják. Ugyanezzel a **SHIFT + jobb katt** módszerrel működnek:
- 🕊️ **Feloldozás-oltár** — **letörli a bűnös-jelölést és a bűn-számlálódat** (a sötét paktum
  visszafordíthatatlan része NEM törölhető így — azt csak a Vezeklés-lánc bontja); van cooldownja.
- 🏠 **Hazatérés-kő** — a frakciód **fővárosába teleportál**.
- ⚜️ **13 kaszt-szentély** — kasztonként egy-egy tematikus **buff-oltár**.

A teljes lista áldozati költségekkel: [Relikviák oldal](docs/player-guide/09-relikviak.md).

---

## 10. A világ veszélyei ✅

### Mob-szintezés
A spawntól távolodva a szörnyek **erősödnek**: minden **1000 blokk = +1 mob-szint**
(`[Lvl X]` névvel, több élettel és sebzéssel), alapból **legfeljebb 10 szintig**. Cserébe a magasabb szintű mobok **több kaszt
XP-t** és nagyobb eséllyel **lélekkövet** adnak. (A spawner-/parancs-spawnolt mobok nem
skálázódnak — a farmok biztonságosak.) ⚠️ Kivétel: a **Sötét (Kitaszított)** játékosnak az
**élőhalott** mobokból nem esik lélekkő („a Királynő nem fizet a testvérgyilkosságért" — azok
úgysem védekeznek ellene); élő szörnyekből nekik is. A **Nekromanta** lélek-szilánkja is csak
**élő** szörnyből jön. A szint-névtábla alapból **csak akkor jelenik meg,
amikor ránézel a mobra** (közelről, takarás nélkül), így nem zsúfolja tele a képernyőt
falakon át vagy nagy távolságból.

> **Hol jelenhetnek meg az események?** A mob-spawnoló események (világboss, invázió, vad
> hajsza) — akárcsak a meteor és a kincs — **soha nem érkeznek városba**: claimelt
> frakció-territóriumba, játékos-claimbe és védett régióba nem spawnolnak, és víz tetejére sem.
> Az esemény-szörnyek **nem zombisodnak át** az overworldben, és **nappal sem égnek el**.

### Vérhold-éjszaka 🌕
Ritkán egy éjszaka **vérholddá** változik (broadcast jelzi). Ilyenkor minden szörny **+2
szinttel** erősebb, és a **lélekkő-drop esélye megduplázódik** — kockázatos, de jövedelmező éj.

### Világbosszok 👹
Időnként egy **boss-erejű szörny** jelenik meg egy véletlen játékos közelében (broadcast +
koordináta). Spawnkor **véletlen archetípus** kerül kiválasztásra — saját névvel, stat-szorzókkal,
**szignatúra-aurával** (a túlélők a boss közelében témába illő debuffot kapnak) és jutalom-szorzóval
(pl. A Gyűrűk Őre, Lávakohó Behemót, Fagyott Trón Királya, Csontkirály, Mélységi Rém…). A boss
**~8 másodpercenként telegrafált különleges képességet** süt el (becsapódás / mérgező zóna /
add-idézés) — a veszélyzónát **részecske-gyűrű** rajzolja ki, külön **hangjelzéssel** —, és
**50% HP alatt feldühödik** (2. fázis, erősebb csapásokkal). Aki legyőzi: a
**frakciója kasszát és liga-pontot** kap, a győztes pedig **ideiglenes buffot** (erő + ellenállás).

### Inváziók 🧟
Időnként egy **szörnyhorda** spawnol egy véletlen játékos köré (broadcast jelzi). A horda-összetétel
**véletlen** (pl. Élőhalott Áradat, Csontlégió, Pókfészek, Káosz-horda), és minden hullámot egy
**skálázott, megnevezett bajnok (mini-boss)** vezet, amely szintén telegrafált földcsapással támad.
A horda mobjai skálázottak — extra **XP-vel és lélekkő-eséllyel** jutalmaznak.

### Kereskedő-karaván ✦
Időnként egy **vándorkereskedő karaván** érkezik egy helyszínre (broadcast jelzi, melyik világba és
mennyi ideig marad). Amíg a városban van, **jobb-katt a karaván-NPC-re** → egy bolt nyílik **ritka
portékákkal** (arany alma, gyémánttömb, névcímke, tapasztalat-palack…), fix áron a banki
egyenlegedből. A kifizetett pénz eltűnik (money sink). A karaván **korlátozott ideig** marad, majd
továbbáll — ha lekésed, legközelebb máshol bukkan fel.

### Hangulat-események ✦
Időnként apró, **légköri események** teszik élőbbé a világot (nem befolyásolják a balanszot):
**északi fény** (rövid éjjellátás + csillámló égbolt), **hulló csillag** (broadcast a becsapódás
irányával), **köd**, **bolyongó szellemek**, **szentjánosbogarak**, valamint **állat-vándorlás**
(egy passzív állatcsorda vándorol be a közeledbe — élelemforrás, nem pénz).

### Gyűjtögető buff-ablakok ⛏🎣
Időnként megnyílik egy **szerver-szintű bónusz-ablak** (kb. 15 percre) — csak nyersanyag/XP, sosem
pénz: **bányász-láz** (érc-blokk bónusz drop), **termés-óra** (beérett termés bónusz hozam),
**halászati láz** (esély dupla fogásra), **tapasztalat-óra** (XP-szorzó mindenből). Egy broadcast
jelzi a kezdetét és a végét — ilyenkor érdemes rákapcsolni a megfelelő tevékenységre!

### Elrejtett kincs 🗺
Időnként egy **megjelölt kincsesláda** bukkan fel egy játékos közelében, és a broadcast megadja a
**hozzávetőleges helyét** (világ + koordináta). Az **első**, aki odaér és **rákattint** (vagy
kibányássza), viszi a teljes loot-ot (nyersanyag, nem pénz), majd a láda eltűnik. Ha senki nem
találja meg időben, feltáratlanul elenyészik — érdemes sietni!

### Vad Hajsza 🐺
Időnként egy **megnevezett, feldühödött elit fenevad** (Ősi Fenevad, Csont Vadász, Vén Mágus,
Pokoli Behemót) kóborol be egy játékos közelébe — kóborló mini-fenyegetés az invázió-hordák és a
világbossok között. Aki **leteríti**, **ritka loot-ot** kap (nyersanyag, nem pénz); ha időben senki
nem öli meg, eltűnik a vadonban.

### Bőség-idő 🌱
A vérhold **pozitív ellenpárja**: egy nyugodt időablak, amikor a **termés gyorsabban nő**, az
**állatok néha ikret ellenek**, **kevesebb szörny** spawnol, és **gyengéd regeneráció** leng
mindenkin. Építeni, farmolni, feltöltődni való — a béke szigete a háború közepén.

### Kollektív szerver-kihívás ⚔
Időnként az **egész szerver** kap egy közös, időzített célt (pl. öljetek meg együtt 500 szörnyet /
bányásszatok 800 ércet / takarítsatok be 1000 termést) — a haladást **boss-bar** mutatja mindenkinek.
Ha időben **együtt** teljesítitek, **minden online játékos** jutalmat kap (XP + nyersanyag-csomag +
rövid Sietség-buff). Közös cél, közös jutalom.

### Karaván-kíséret 🛡
Időnként egy **konvoj** (ládás láma) indul útnak egy cél felé, és útközben **szörny-hullámok**
támadják — a haladást **boss-bar** mutatja. A közelben lévő játékosoknak **életben kell tartaniuk**,
míg célba ér. Ha odaér, a **zsákmány a célnál** hullik, és a **kereskedő-karaván boltja egy ideig
bővebb (ritka) készlettel árul**. Ha a konvoj elesik vagy lejár az idő, a szállítmány elvész. (A
kíséret-mobok robbanása **sosem rongálja a terepet**.)

### Meteor-becsapódás ☄
Időnként egy **meteor** csapódik be a vadonba (a broadcast megadja a helyét), és egy kis **kráter**
marad, tele **ritka, kibányászható érccel** (gyémánt, smaragd, ősi törmelék…). Siess: a kráter csak
egy ideig marad, aztán **magától visszaáll az eredeti terep** — amit addig kibányászol, a tiéd. A
meteor **sosem csapódik claimelt frakció-területre**, és **nem rombolja maradandóan** a világot.

### Események megtekintése
**`/events status`** — „Mi történik most?": minden éppen aktív világesemény egy listában
(hátralévő idővel) + a szezon-állás • `/events season` — szezon-állás • `/events blood-moon` — vérhold állapota • `/events caravan` —
kereskedő-karaván állapota. (Admin: `/events caravan arrive|depart` • `/events ambient` • `/events
gathering` • `/events treasure` • `/events wild-hunt` • `/events abundance` • `/events challenge` •
`/events escort` • `/events meteor`.) A `/menu` → **Események** almenü mindezt egy helyen, **élő
státusszal** mutatja: szezon-állás, vérhold, világboss, karaván, kíséret, bőség-idő,
szerver-kihívás és meteor-kráter.

### Party (csapat) 👥
WoW-stílusú csapat: max **5 fő**, teljesen **frakciótól függetlenül** (bármelyik frakcióból lehet
egy csapatban). Meghívás: `/party invite <név>`, elfogadás: `/party accept` (elutasítás: `/party
decline`). A közeli mob-ölésekből járó XP a közelben lévő párttagok közt **fejenként oszlik meg** —
minél többen vagytok együtt, annál kisebb rész jut mindenkinek (igazi WoW-módra). A plugin saját
loot-eseményeinél (**Vad Hajsza**, **elrejtett kincs**) nem egy közös zsákmány esik le: minden
közelben lévő párttag a **saját (personal) jutalmát** kapja. Párton belül **nincs PvP** — a tagok
sem közelharccal, sem nyíllal nem tudják sebezni egymást. Csapat-chat: `/p <üzenet>` (csak a
párttagok látják). A csapatvezetőnek külön jogai vannak: `/party kick <név>`, `/party promote
<név>`, `/party disband`. Ha valaki kilép a szerverről, kikerül a csapatból; ha 2 fő alá csökken a
létszám, a csapat automatikusan feloszlik.

**Party-HUD:** amíg csapatban vagy, a HUD-oldalsávon egy **„— Csapat —" szekció** mutatja a tagokat:
név + **színkódolt élet-sáv** (zöld/sárga/piros) + szív-szám, a vezetőt 👑 jelöli — élőben frissül,
így harc közben is látod, kinek kell segítség (WoW party-frame-módra). Ha nem vagy csapatban, a
szekció el sem foglal helyet az oldalsávon.

**Okos oldalsáv (HUD):** a képernyő jobb szélén lévő oldalsáv **magától alkalmazkodik**:
harcban „kitisztul" — csak az **Erő-csík** és a **party-frame-ek** maradnak, majd pár
másodperccel az utolsó találat után visszatér a teljes nézet. Az infósor **forog**:
aktív események ↔ szezon-visszaszámláló ↔ a napi kihívás állása váltakozik rajta.
A `/hud <szekció>` paranccsal bármely blokk (frakció/kaszt/erőforrás/esemény/valuta/csapat)
egyenként ki-be kapcsolható, a `/hud mind` az egész oldalsávot rejti el — a beállításod
kilépés után is megmarad.

**Harc-visszajelzés:** minden ütésed fölött **lebegő sebzés-szám** jelenik meg a célponton
(mobnál sárga, játékosnál piros — kézi és lövedékes találatra is; alapból **csak te látod**,
a szerver configból mindenki számára láthatóra állíthatja vagy kikapcsolhatja), halálkor pedig a chatben
**halál-összegzőt** kapsz: az utolsó 10 másodperc találatai (mennyi ❤, kitől/mitől, mikor) és
az összesített sebzés — így mindig tudod, mi vitt el.

### Terület-claim (saját birtok) 🏠
`/claim` egy **16×16 blokkos négyzetet** foglal le **körülötted** (a pozíciódra igazítva, **nem** a
vanilla chunk-rácshoz). Az első **~768 oszlop (kb. 3 gyorsfoglalásnyi terület) ingyenes**, utána
minden további oszlop a **saját frakció-valutádban** fizetendő, **fix 0,5/oszlop** áron (nem drágul
oszloponként — csak a megvett oszlopok számával nő a végösszeg). Ez az ár **ELÉG** (money sink),
tehát az `/claim unclaim`-nál sem jár vissza.

**Mit véd a claim:** idegenek nem törhetnek/rakhatnak blokkot, nem nyithatnak konténert (láda,
hordó, kemence…), nem üríthetnek vödröt, nem szedhetnek le kép-/festménykeretet a te birtokodon —
és a **robbanás sem bontja** a claimelt blokkokat (a blokk-evő mobok, pl. enderman, szintén nem
vihetnek el blokkot). A **tűz** nem gyullad meg, nem terjed és nem éget el claimelt blokkot, kívülről
**folyadék** (víz/láva) nem folyhat be, és idegen **dugattyú** sem tolhat be/húzhat ki blokkot a
birtokodról (a saját claimeden belüli gépeid persze működnek). **Fontos:** a claim a **PvP-t NEM
tiltja** — ez háborús szerver, a claim csak az építést és a lopást védi.

A tulajdonos `/claim trust <név>` paranccsal megbízottakat adhat (teljes hozzáférés minden
claimjéhez), `/claim untrust <név>` paranccsal vonhatja vissza — vagy GUI-ból: `/menu` →
Birtok → **„Megbízottak kezelése"** (felül a megbízottak: katt = visszavonás; alul a közeli
játékosok: katt = megbízás). `/claim info` megmutatja, kié az
adott chunk (+ kirajzolja a határát), `/claim show` pedig részecskékkel **és egy izzó fényfallal**
rajzolja ki a közeli claimek határát — a fal a saját/megbízott birtokodnál zöld, idegennél piros,
és csak te látod (pár másodperc után eltűnik).

**Védett zónába** (főváros, védett város, védett frakcióterület) **nem lehet claimelni** — ott
senki sem építhet. **Normál frakcióterületre viszont IGEN**: a saját birtokod a frakciód földjén
is elférhet, így a claim és a territórium rendszer együtt működik. A meteor-becsapódás és az
elrejtett kincs esemény is elkerüli a claimelt területet. Raid alatt a claim alapból véd, de
szerver-beállítástól függően a jelentkezett támadók a claim-ládákat hadizsákmányként
**kinyithatják** (nem lebonthatják). A zónatípusokról bővebben: [Frakcióterületek](docs/player-guide/13-teruletek.md).

---

## 11. Királyság, raid és háború ✅

### Királyválasztás 👑
Minden harcos frakció (Piros / Kék / Sötét — a Semleges kivételével) **királyt választhat**:
- `/faction king vote <játékos>` — szavazás a saját frakciód egy tagjára.
- Aki eléri a **minimum szavazatszámot** és vezeti a listát, azt **megkoronázzák** (broadcast).
- A választási ciklus időnként újraindul. `/faction king` — aktuális király + szavazatok.

**A király jogai:** kivehet a **frakciókasszából** (`/faction treasury withdraw`), **raidet
hirdethet**, és **beállíthatja a frakció adókulcsát** (`/faction king tax <százalék>`, a config
maximumáig).

### Frakciókassza
- `/faction treasury` — a kassza egyenlege • `/faction donate <összeg>` — adomány a saját valutádból.
- A kasszát az **adó** és az **adományok** töltik; a király és a raid-zsákmány költi.

### Raid ⚔️
- `/faction raid <célfrakció> [terület]` — **csak a király** hirdethet; a nevezési díj a
  kasszából megy. A raid alapból a védő **fővárosáért** folyik.
- **Felkészülés** (2 perc): harcosok jelentkezése `/faction raid join`-nal (**max 10/oldal**);
  állás: `/faction raid status`.
- A harci szakaszban **csak a jelentkezett harcosok közti ölés** szentesített és pontozó
  (területkötött raidnél csak a zónán belül); a terület **középpontjának tartása** is pontot
  termel. Ha a **támadó nyer**, a **területet is elfoglalja**.
- A végén (a harc alapból 15 perc) a **több pontot szerző** oldal **hadizsákmányként** elviszi a
  vesztes kasszájának egy részét (és **liga-pontot** kap a szezonba). A győztes frakció online
  tagjai **győzelmi buffot** kapnak (Erő + Regeneráció egy ideig).
- **Ostromágyú:** craftolható ostromfegyver (vasblokk-keret + TNT + tűzpor), ami **csak aktív
  raid alatt** sül el. Jobb katt = **pusztító, de terep-barát robbanás** a célzott pontra (sebzi
  az ellenfeleket, de nem rombolja a világot). Raiden kívül nem működik.

### Szezonális liga 🏆
A frakciók **raid- és világboss-győzelmekből pontot** gyűjtenek. A szezon végén (alapból 60 nap)
a vezető frakció **kasszája jutalmat kap**, a győztes frakció **online tagjai** pedig **győzelmi
buffot + tárgy-jutalmat + ünneplő tűzijátékot**; utána a pontok resetelnek. Állás: `/events season`.

---

## 12. Küldetések ✅

**Új játékosként** az első belépésedkor automatikusan elindul egy rövid **kezdő
küldetés-lánc** („Beszélj a hírnökkel" → „Első csata" → „Első gyűjtögetés") — minden lépés
teljesítésekor a következő magától indul, jutalommal.

`/quest list` (felvehető), `/quest info` (aktív + haladás), `/quest accept <id>`,
`/quest abandon <id>`, `/quest log` (grafikus **küldetésnapló**: Aktív / Felvehető /
Teljesített fülek, shift-katt = feladás). A haladásod az action barban követhető,
teljesítéskor jutalmat kapsz (kaszt XP, valuta — akár a **saját frakciód pénze** —,
**tárgy**, képesség-feloldás vagy különleges hatás).

- **Több feladat egy questben:** egy küldetés több objektívát is tartalmazhat egyszerre
  (pl. „ölj 10 szörnyet ÉS gyűjts 16 kenyeret”); mód: **ALL** (párhuzamos) vagy **SEQUENCE**
  (sorban, story-lánchoz). A HUD/`/quest info` feladatonként mutatja a haladást.
- **Ismétlődő és szezonális:** vannak **repeatable** questek (cooldown, pl. napi 24 óra után
  újra felvehető) és **szezonális** questek (szezononként egyszer, új szezonban újra).
  Egyes NPC-k **naponta frissülő** kínálatból (rotáció) adnak questeket, és a párbeszédük
  után **választós opciók** indíthatnak eltérő folytatást.
- **Frakció-közösségi célok:** szerver-szintű, **megosztott számláló**, amibe egy frakció
  (vagy az egész szerver) minden tagja beleszámít (pl. „a Piros frakció gyűjtsön 1000 vasat”).
  Nem egyéni quest — a normál játék közben gyűlik; teljesítéskor a frakció **kassza-jutalmat +
  rövid buffot** kap, majd a cél újraindul.
- **Mester-próbák (NPC-s láncok):** a kezdő próba után a kasztod **mester-NPC-jénél**
  jelentkezhetsz (beszélj vele — FancyNpcs NPC a fővárosban); a **mester-próbát már maga
  az NPC adja**, és teljesítened kell a **próbapályáját** (időmérős parkour).
  Jutalom: 100 + 400 kaszt-XP.
- **NPC-jelzés csak neked:** a quest-NPC-k felett **részecske-aura** világít, kizárólag annak
  a játékosnak, akit érint — **arany** = questet tud adni neked, **zöld** = aktív küldetésed
  hozzá szól.
- **Story és beszállítás:** a quest-NPC-k **párbeszédet mondanak** a küldetés átadásakor és
  leadásakor; a **beszállító questeknél** (DELIVER_ITEMS) a kért tárgyakat az NPC kattintásra
  **átveszi tőled**. A jutalom-valuta lehet mindig a **saját frakciód pénze** is.
- **Kaszt-próbák:** jelenleg a négy kezdő próba érhető el (`warrior_trial`, `archer_trial`,
  `wizard_trial`, `assassin_trial`), kaszt XP jutalommal. A többi kaszt saját NPC-s próbája
  még tervezett tartalom.
- **Sötét Beavatás:** a Nekromanta specializáció **kapuja** — zarándoklat a Sötét romvárosba
  (DARK frakció szükséges).
- **Vezeklés-lánc** (3 rész): elit szörnyek pusztítása → alázat-tanulás horgászattal → 50 elit
  mob legyőzése. A lánc teljesítése az **EGYETLEN mód a sötét paktum megtörésére** — feloldozást
  nyersz a bűneid alól.

---

## 13. Hasznos parancsok — gyorslista

| Parancs | Mire jó |
|---|---|
| `/profile` | Profil, frakció, egyenlegek, kasztválasztó |
| `/faction join/leave/king/raid/treasury/donate` | Frakció-műveletek |
| `/spec list/choose/info/respec` | Specializációk |
| `/talent`, `/talent spend …` | Talentek |
| `/profession join/info/list` | Szakmák |
| `/profession recipes` | Recept-könyv (egy-kattintásos craftolás a tanult receptekből) |
| `/daily` | Napi (naponta forgó) küldetések |
| `/achievements` | Teljesítmények — mérföldkő-célok valuta-jutalommal |
| `/stats [név]` | Statisztika-profil: ölések, halálok, K/D, mob-ölések, castolt spellek, questek |
| `/leaderboard` | Ranglisták (kaszt-szint, vagyon, raid-ölések) |
| `/pet` | Társ-állat (Vadmester/Nekromanta): befogás, előhívás, szintlépés |
| `/bank balance/deposit/withdraw` | Bank |
| `/currency balance/pay/exchange/rates` | Valuta + árfolyam |
| `/market`, `/market sell/auction/claim/cancel` | Piactér + aukciósház |
| `/adomany`, `/adomany add` | Közösségi adomány-láda (ár nélküli ajándékozás) |
| `/souls`, `/souls champion` | Nekromanta lélekszilánk + bajnok-idézés |
| `/quest list/info/accept/abandon`, `/quest log` | Küldetések + grafikus küldetésnapló |
| `/events season/blood-moon/caravan` | Világesemények (szezon, vérhold, kereskedő-karaván) |
| `/party`, `/p <üzenet>` | Party (csapat) + csapat-chat |
| `/claim`, `/claim trust <név>` | Terület-claim (saját birtok) |
| `/faction king tax <%>` | Király: adókulcs beállítása |
| `/job givecatalyst` (admin) | Lélekkapocs pótlása |
| `/exchangeboard place/remove` (admin) | Árfolyamtábla kezelése |

---

## 14. Még fejlesztés alatt (WIP) 🚧 / Tervezett ⏳

Ezek **még nem elérhetők** vagy csak részben működnek — ne számíts rájuk a játékban:

- 🚧 **Kaszt-questek:** az NPC-s mester-próba láncok (mentor-NPC + próbapálya) **készek a
  pluginban**, de a mester-NPC-k és a pályák kihelyezése a szerver-csapatra vár — addig a
  láncok nem haladnak.
- 🚧 **Piactér:** fizikai piactábla a fővárosokban még nincs (a lapozás, a `/market search`
  keresés, a reputáció-árazás és a **licitálós aukciósház** már **kész**).
- 🚧 **Intro:** a cím-szekvencia és a kamera-utaztatás **kész**, de a kameraút alapból ki van
  kapcsolva, amíg a szerver-csapat ki nem jelöli a waypointokat.
- ⏳ **Ultimate képességek** külön rendszerként (jelenleg a spec-ultik töltik be ezt a szerepet).
- ⏳ **Világépítés:** a fővárosok, a Sötét romváros és a távolság-gyűrűk loot-asztalai a szerver
  csapatának feladata (a plugin a `/territory` paranccsal adja hozzá az eszközt a kijelölésükhöz).

---

*Jó kalandozást a fagyott királyságok földjén! ❄️*
