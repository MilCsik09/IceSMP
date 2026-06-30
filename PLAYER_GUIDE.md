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
> - 🏰 [Frakcióterületek](docs/player-guide/13-teruletek.md)
> - ⌨️ [Parancsok listája](docs/player-guide/14-parancsok.md)
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
3. **Válassz kasztot** a Kaszt menüből, majd **igényeld a Képesség Katalizátorodat** (ugyanitt egy gomb).
4. **Ölj szörnyeket** a kaszt XP-ért, **bányássz/arass/horgássz** a szakma XP-ért.
5. Vedd fel a kaszt-próba **küldetésedet** (`/quest list`).

Első belépéskor egy rövid **bevezető cím-szekvencia** is lejátszódik. ✅

---

## 2. Frakciók ✅

Négy frakció létezik, mindegyiknek saját valutája és passzív bónusza van. Belépés:
`/faction join <red|blue|neutral|dark>`, kilépés: `/faction leave`.

| Frakció | Passzív bónusz |
|---|---|
| 🔴 **Piros** | Immunis a **tűz / láva / forró blokk** sebzésére (hő-mesterség — a Nether biztonságos) |
| 🔵 **Kék** | Immunis a **fagyásra ÉS a fulladásra**; **50% eséllyel** nem veszít éhséget (víz-mesterség — végtelen búvárkodás) |
| ⚪ **Semleges** | **Nincs zuhanás-sebzés** (esésimmunitás); a **nem-ellenséges mobok és az endermanök** nem támadják; **adómentes** |
| ⚫ **Sötét** | Immunis a wither-sebzésre; az **élőhalottak nem támadják** (a legerősebb PvE-passzív — a sinner-jelölés az ára) |

> A passzívok **egy szintre** vannak hangolva: mindegyik kb. egyformán hasznos, csak más
> helyzetben (Piros a tűznél, Kék a víz alatt, Semleges lopakodva, Sötét a szörnyek közt).

**Fontos a Sötét frakcióról:**
- Csak az léphet be, akit **bűnössé (sinner)** bélyegeztek.
- Belépéskor megköttetik a **sötét paktum** — onnantól a bűnös jelölést **soha nem lehet
  levenni** (még frakcióelhagyás után sem). Az egyetlen visszaút a **vezeklés-küldetéslánc**
  (lásd a Küldetéseknél).

**Hogyan leszel bűnös?** Ha **megölsz egy másik játékost**, +1 bűnt kapsz. **4 bűnnél**
automatikusan **száműznek a Sötét frakcióba** (örök paktummal). Kivétel: raid alatt a
hadviselő frakciók közti ölés **nem számít bűnnek**.

---

## 3. Gazdaság ✅

### Valuták és bank
Minden frakciónak saját **token**-je van (Piros / Kék / Semleges / Sötét). A pénz kétféleképp
létezik: **fizikai itemként** (token a táskádban) és **banki egyenlegként**.

- `/bank balance` — egyenlegeid • `/bank deposit` — tokenek bankba • `/bank withdraw <valuta> <összeg>` — kivét itemként
- `/currency pay <játékos> <összeg> [valuta]` — utalás • `/currency balance` — egyenleg

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
- **Frakció-reputáció:** a vételár attól is függ, milyen viszonyban van a frakciód az
  eladóéval. **Ellenséges** (vagy épp raidben álló) frakciótól drágább (+25% felár, ami elég),
  **szövetségestől** olcsóbb (−10%). Semleges viszonynál nincs változás.

### Árfolyamtáblák 📊
A fővárosokban admin által lerakott **hologram-táblák** mutatják a valuták élő, kínálat-alapú
értékét — „tőzsdei" kijelző, ami magától frissül. Ugyanezt mindig lekérheted `/currency rates`-szel is.

### Money sinkek és események
- **Állampolgári adó:** óránként a frakciótagok a saját valuta-egyenlegük **2%-át** befizetik
  a frakciókasszába (a Semlegesek mentesek). Ez csökkenti a forgalomban lévő pénzt.
- **Kereslet-sokk** (heti gazdasági esemény): időnként egy véletlen valuta értéke **átmenetileg
  x1.2–1.6-ra ugrik** (broadcast jelzi) — kereskedési lehetőség.
- **Lélekkő:** a magas szintű (Lvl 3+) skálázott szörnyek eséllyel **Sötét tokent** dobnak — a
  veszélyes, spawntól távoli vidékek így gazdaságilag is megérik.

---

## 4. Kasztok ✅

**13 kaszt** közül választhatsz a `/profile` → Kaszt menüből. **Max. 2 kasztod lehet:**
- **Elsődleges kaszt** — bármikor választható.
- **Másodlagos kaszt** — csak akkor nyílik meg, ha az elsődleges eléri a **max szintet (50)**.
  (A másodlagos nem specializálódhat.)

Minden kasztnak saját **stílusa**, saját **katalizátor-tárgya** (a „spellbook") és saját
**erőforrása** (az Erő-csík, lásd lentebb) van:

| Kaszt | Stílus | Katalizátor | Erőforrás |
|---|---|---|---|
| 🧙 **Varázsló** | Elemi és kontroll mágia, távolsági ráolvasások | 📖 Mágikus Kódex | Mana |
| ⚔️ **Harcos** | Közelharci erő, kitartás, buffok | 📯 Harci Kürt | Düh |
| 🏹 **Íjász** | Távolsági harc, mozgékonyság, csapdák | 🎒 Vadásztarsoly | Fókusz |
| 🗡️ **Orgyilkos** | Lopakodás, gyors kitörések, gyengítés | 🪨 Árnyékamulett | Energia |
| 🐻 **Druida** | Alakváltó természet-mágia (harc, kontroll, tank, gyógyítás) | 🌱 Vadon Talizmánja | Természeti Erő |
| 🔆 **Paplovag** | Szent harc, védelem és gyógyítás | 🔔 Szent Harang | Szent Erő |
| 💀 **Halállovag** | Rúna-mágia, vér és fagy, közelharci tank/DPS | 💀 Rúnakovácsolt Koponya | Runikus Erő |
| 🌊 **Sámán** | Elemek, totemek, gyógyítás és erősítés | 🪬 Ősök Totemje | Mana |
| ☯️ **Szerzetes** | Gyors közelharc, csi-energia, gyógyítás | 🎍 Jáde Bot | Csi |
| ✝️ **Pap** | Szent és árny mágia, gyógyítás | 🕯️ Szent Gyertya | Mana |
| 😈 **Boszorkánymester** | Átkok, démonok és pusztító tűz | 🏮 Lélek Lámpás | Lélekerő |
| 👁️ **Démonvadász** | Mozgékony démoni harc és bosszú | 👁️ Démonszem | Fúria |
| 🐉 **Sárkányidéző** | Sárkány-eszencia: perzselő mágia és gyógyítás | 🐲 Sárkány Esszencia | Eszencia |

### Szintezés
- A kaszt **mob ölésből** kap XP-t: alap **10 XP / ölés**, plusz a szörny szintjéért **+3 XP /
  mob-szint** (lásd Mob-szintezés). Csak ellenséges mobok adnak XP-t.
- Ha van másodlagos kasztod, az minden ölésből az **50%-ot** is megkapja.
- **Progresszív szintgörbe:** az n. szintlépés ára `60 + (n-1)×10` XP — vagyis minél magasabb
  vagy, annál többet kell ölnöd a következő szintért. Max szint: **50**.

### Képesség Katalizátor (a „spellbook")
A kaszt képességeit egy **kaszt-tematikus tárggyal** használod (a fenti táblázat *Katalizátor*
oszlopa mutatja, melyik kaszté melyik). A specializációd ugyanazt a katalizátort használja, mint
az alapkasztod.

- **Jobb katt** = a kiválasztott képesség elsütése.
- **Lopakodás + bal katt (ütés)** = váltás a feloldott képességek között (kaszt-specifikus
  hanggal; az action bar mutatja a kiválasztott képességet + a költségét).
- Ha elveszne: a Kaszt menüből bármikor újra igényelheted (admin: `/job givecatalyst`).
- A katalizátort **nem lehet** craftolásnál vagy kemencében elhasználni — védett.

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
2. A csík **magától visszatöltődik** idővel (alapból ~8 / másodperc).
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

- A **Nekromanta** különleges: csak **Sötét frakcióval + bűnös állapottal**, ÉS a **Sötét
  Beavatás** küldetés teljesítése után választható.
- **Respec:** meggondolhatod magad — a Specializáció menü **Respec** gombjával (vagy
  `/spec respec <class|profession>`) a frakcióvalutádért (alapból 100) visszaváltod a speced;
  a spec-hez kötött talentpontjaid automatikusan visszatérülnek.
- `/spec info` — aktuális specjeid.

---

## 6. Képességek (spellek) ✅

Több mint **350 képesség** van; **minden kaszt és specializáció saját, egyedi készletet** tanul
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

---

## 7. Talentek ✅

A szintjeid **talentpontokat** termelnek, amiket passzív erősítésekre költhetsz a
`/profile` → **Talentek** menüből (kattints a fejlesztendő talentre), vagy paranccsal
(`/talent`, `/talent spend <class|profession> <talent>`).

- **Kaszt ponttár:** minden **5 kasztszint** = 1 pont (az elsődleges + másodlagos szintek összegéből).
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
Bizonyos tárgyakhoz **kaszt- vagy szakmaszint kell**. Az alap szabály: **netherite felszerelést
csak a 25+ szintű Kovács** tud készíteni (craftolóasztalon és smithing asztalon is). Ha nem
felelsz meg, a craft eredménye nem jön létre, és üzenetet kapsz.

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

---

## 10. A világ veszélyei ✅

### Mob-szintezés
A spawntól távolodva a szörnyek **erősödnek**: minden **1000 blokk = +1 mob-szint**
(`[Lvl X]` névvel, több élettel és sebzéssel). Cserébe a magasabb szintű mobok **több kaszt
XP-t** és nagyobb eséllyel **lélekkövet** adnak. (A spawner-/parancs-spawnolt mobok nem
skálázódnak — a farmok biztonságosak.) A szint-névtábla alapból **csak akkor jelenik meg,
amikor ránézel a mobra** (közelről, takarás nélkül), így nem zsúfolja tele a képernyőt
falakon át vagy nagy távolságból.

### Vérhold-éjszaka 🌕
Ritkán egy éjszaka **vérholddá** változik (broadcast jelzi). Ilyenkor minden szörny **+2
szinttel** erősebb, és a **lélekkő-drop esélye megduplázódik** — kockázatos, de jövedelmező éj.

### Világbosszok 👹
Időnként egy **boss-erejű szörny** jelenik meg egy véletlen játékos közelében (broadcast +
koordináta). Spawnkor **véletlen archetípus** kerül kiválasztásra — saját névvel, stat-szorzókkal,
**szignatúra-aurával** (a túlélők a boss közelében témába illő debuffot kapnak) és jutalom-szorzóval
(pl. A Gyűrűk Őre, Lávakohó Behemót, Fagyott Trón Királya, Csontkirály, Mélységi Rém…). A boss
**~8 másodpercenként telegrafált különleges képességet** süt el (becsapódás / mérgező zóna /
add-idézés), és **50% HP alatt feldühödik** (2. fázis, erősebb csapásokkal). Aki legyőzi: a
**frakciója kasszát és liga-pontot** kap, a győztes pedig **ideiglenes buffot** (erő + ellenállás).

### Inváziók 🧟
Időnként egy **szörnyhorda** spawnol egy véletlen játékos köré (broadcast jelzi). A horda-összetétel
**véletlen** (pl. Élőhalott Áradat, Csontlégió, Pókfészek, Káosz-horda), és minden hullámot egy
**skálázott, megnevezett bajnok (mini-boss)** vezet, amely szintén telegrafált földcsapással támad.
A horda mobjai skálázottak — extra **XP-vel és lélekkő-eséllyel** jutalmaznak.

### Események megtekintése
`/events season` — szezon-állás • `/events blood-moon` — vérhold állapota.

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
- `/faction raid <célfrakció>` — **csak a király** hirdethet; a nevezési díj a kasszából megy.
- A raid alatt (alapból 15 perc) a **két hadviselő frakció közti ölés nem bűn**, hanem **pontot
  ér**.
- A végén a **több ölést szerző** oldal **hadizsákmányként** elviszi a vesztes kasszájának egy
  részét (és **liga-pontot** kap a szezonba). A győztes frakció online tagjai **győzelmi buffot**
  kapnak (Erő + Regeneráció egy ideig).
- **Ostromágyú:** craftolható ostromfegyver (vasblokk-keret + TNT + tűzpor), ami **csak aktív
  raid alatt** sül el. Jobb katt = **pusztító, de terep-barát robbanás** a célzott pontra (sebzi
  az ellenfeleket, de nem rombolja a világot). Raiden kívül nem működik.

### Szezonális liga 🏆
A frakciók **raid- és világboss-győzelmekből pontot** gyűjtenek. A szezon végén (alapból 60 nap)
a vezető frakció **kasszája jutalmat kap**, és a pontok resetelnek. Állás: `/events season`.

---

## 12. Küldetések ✅

`/quest list` (felvehető), `/quest info` (aktív + haladás), `/quest accept <id>`,
`/quest abandon <id>`. A haladásod az action barban követhető, teljesítéskor jutalmat kapsz
(kaszt XP, valuta, képesség-feloldás, vagy különleges hatás).

- **Kaszt-próbák:** minden alap kaszthoz egy bevezető küldetés (pl. „A Harcos Próbája" — ejts el
  15 szörnyet), kaszt XP jutalommal.
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
| `/bank balance/deposit/withdraw` | Bank |
| `/currency balance/pay/exchange/rates` | Valuta + árfolyam |
| `/market`, `/market sell/cancel` | Piactér |
| `/souls`, `/souls champion` | Nekromanta lélekszilánk + bajnok-idézés |
| `/quest list/info/accept/abandon` | Küldetések |
| `/events season/blood-moon` | Világesemények |
| `/faction king tax <%>` | Király: adókulcs beállítása |
| `/job givecatalyst` (admin) | Katalizátor pótlása |
| `/exchangeboard place/remove` (admin) | Árfolyamtábla kezelése |

---

## 14. Még fejlesztés alatt (WIP) 🚧 / Tervezett ⏳

Ezek **még nem elérhetők** vagy csak részben működnek — ne számíts rájuk a játékban:

- 🚧 **Bűn-rendszer:** a gyilkosság-számláló és a küszöbnél az automatikus száműzetés **kész**;
  a lopás/árulás detektálás még nincs.
- 🚧 **Raid:** nincs még 10v10 létszámkorlát vagy aréna-/területkötés (a győztes-buff és az
  ostromágyú már **kész**).
- 🚧 **Kaszt-questek:** egyelőre egyszerű „ölj X-et" típusú próbák — NPC-s / parkour pályák tervben.
- 🚧 **Piactér:** nincs még lapozás/keresés, és fizikai piactábla a fővárosokban (a reputáció-árazás már **kész**).
- 🚧 **Intro:** csak cím-szekvencia van; a látványos kamera-utaztatás még hiányzik.
- 🚧 **Szezonális liga:** működik a pontgyűjtés, de a győztes **kozmetikai relikvia-jutalma** még nincs.
- ⏳ **Ultimate képességek** külön rendszerként (jelenleg a spec-ultik töltik be ezt a szerepet).
- ⏳ **Megnevezett, szintet lépő állandó társ** (a Vadmester perzisztens companionja) — a többi
  pet-fejlesztés (parancsok, idézés-limit, talent→pet szinergia, Nekromanta lélekszilánk) **kész**.
- ⏳ **Világépítés:** a fővárosok, a Sötét romváros és a távolság-gyűrűk loot-asztalai a szerver
  csapatának feladata (a plugin a `/territory` paranccsal adja hozzá az eszközt a kijelölésükhöz).

---

*Jó kalandozást a fagyott királyságok földjén! ❄️*
