# 7. Talentek (talent-fa) ✅

A **talentek** apró, **örökös erősítések**, amelyeket **talentpontokból** veszel meg. A
szintezéssel pontokat gyűjtesz, és egy **fán** (felülről lefelé) költöd el őket. Megnyitás:
`/profile` → **Talentek**.

## Honnan jön a talentpont?

- **Kaszt-pont:** minden **5 kaszt-szint** után **1 pont** (a kasztod szintjéből).
- **Szakma-pont:** minden **10 szakma-szint** után **1 pont** (az **összes** szakmád szintje
  összeadódik — a másodlagosak is!).

A két pontfajta külön: kaszt-pontot csak a kaszt-fába, szakma-pontot csak a szakma-fába tehetsz.

## Hogyan olvasd a fát?

- A fa **felülről lefelé** épül. A felső sor (1. szint / „tier") **azonnal** elérhető.
- A lentebbi talentek **zárolva** vannak (🔒), amíg a **fölöttük lévő szülő-talentbe** nem teszel
  legalább 1 pontot. A zárolt talent kiírja, melyik a szülője.
- Csak azok a talentek látszanak, amik **hozzád illenek** (a kasztodhoz, specializációdhoz vagy
  szakmádhoz tartoznak).
- Egy talentnek több **rangja** lehet — minden pont +1 rang, egy határig (max rang).

### Különleges talent-típusok
- ⚔️ **Egymást kizáró ágak:** néhány talent **kizárja a testvérét** — ha az egyikbe pontot
  teszel, a másik zárolódik. Választanod kell! Pl. **Behemót** (+élet) **vagy** **Hadúr**
  (+sebzés) — nem lehet mindkettő.
- 👑 **Csúcs-talent (capstone):** erős talent a fa „csúcsán", ami csak akkor nyílik, ha már
  **elég pontot elköltöttél** abban a fában (pl. a **Felemelkedés** 10 elköltött pont után).
- ★ **Aktív talent:** nem csak passzív bónusz — **képességet old fel** a Lélekkapcsodban!
  Pl. a **Felemelkedés** feloldja a **Talentum Lendület** ultit (rövid, erős önbuff). Ezek a
  talentek `★` jellel látszanak.

> Ha **respec-elsz** egy specializációt, a hozzá kötött talentpontok **visszajárnak** (és az
> aktív talent által adott képesség is lekerül). A zárolt talentek mindig kiírják, **miért**
> zártak (szülő kell / kizárt ág / kevés elköltött pont).

---

## 🗡️ Kaszt talent-fa

### 1. szint — mindenkinek (nincs feltétel)

| Talent | Mit ad rangonként | Max rang |
|---|---|---|
| **Életerő** | +2 életpont (=1 szív) | 5 |
| **Fürgeség** | gyorsabban jársz | 5 |
| **Erő** | +0,5 sebzés | 5 |
| **Tudásszomj** | +5% kaszt-XP minden öléskor | 4 |
| **Arkán Hatalom** | +2% spell-erő (a dinamikus skálázás rétegében) | 5 |

### 2. szint — kaszt-kötött (előbb a szülőt kell fejleszteni)

| Talent | Kinek | Szülő | Mit ad | Max rang |
|---|---|---|---|---|
| **Fegyvermesteri Fokozat** | Harcos | Erő | +0,5 sebzés | 3 |
| **Fürge Vadász** | Íjász | Fürgeség | gyorsabb mozgás | 3 |

### 3. szint — specializáció-kötött (előbb a szülőt kell fejleszteni)

| Talent | Specializáció | Szülő | Mit ad | Max rang |
|---|---|---|---|---|
| **Elemi Ráhangolódás** | Elementalista | Erő | +0,5 sebzés | 4 |
| **Lélekpaktum** | Nekromanta | Életerő | +2 életpont | 4 |
| **Brutalitás** | Berserker | Erő | +1 sebzés | 3 |
| **Rendíthetetlen** | Védelmező | Életerő | +3 életpont | 3 |
| **Szélvész Léptek** | Mesterlövész | Fürgeség | gyorsabb mozgás | 3 |
| **Falkavezér** | Vadmester | Életerő | +2 életpont | 4 |
| **Vipera Reflexek** | Méregkeverő | Fürgeség | gyorsabb mozgás | 4 |
| **Kísértetjárás** | Fantom | Fürgeség | gyorsabb mozgás | 3 |

---

## ⚒️ Szakma talent-fa

### 1. szint — mindenkinek

| Talent | Mit ad rangonként | Max rang |
|---|---|---|
| **Szorgalom** | +10% szakma-XP minden munkánál | 5 |
| **Kitartás** | +1 életpont | 3 |

### 2. szint — szakma-kötött (előbb a szülőt kell fejleszteni)

| Talent | Kinek | Szülő | Mit ad | Max rang |
|---|---|---|---|---|
| **Bányász Állóképesség** | Bányász | Kitartás | +1 életpont | 2 |
| **Arkán Belátás** | Bűvölő | Szorgalom | +5% kaszt-XP | 2 |

> Tipp: a **Tudásszomj** (kaszt-XP) és a **Szorgalom** (szakma-XP) korán befektetve **gyorsítja
> az egész fejlődésedet** — minél előbb veszed fel őket, annál hamarabb szintezel utána.

---

➡️ Tovább: [Szakmák](08-szakmak.md) • [Vissza a tartalomhoz](README.md)
