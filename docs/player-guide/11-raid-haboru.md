# 11. Királyság, raid és háború ⚔️

A frakciók nem csak külön élnek — **királyt választhatnak**, **háborúzhatnak**, és van **közös
kasszájuk**. Itt a nagy, csapatszintű játék.

## Királyválasztás 👑

Minden harcos frakció (Piros / Kék / Sötét — a Semleges kivételével) **királyt választhat**:
- `/faction king vote <játékos>` — szavazol a saját frakciód egy tagjára.
- Aki eléri a **minimum szavazatszámot** és vezeti a listát, azt **megkoronázzák** (mindenki
  látja az üzenetet).
- A választási ciklus időnként **újraindul** (új választás).
- `/faction king` — megmutatja a jelenlegi királyt és a szavazatokat.

**A király jogai:**
- Kivehet a **frakciókasszából** (`/faction treasury withdraw <összeg>`).
- **Beállíthatja a frakció adókulcsát** (`/faction king tax <százalék>`).
- **Raidet (háborút) hirdethet** egy másik frakció ellen.

## Frakciókassza 🏦

- `/faction treasury` — a kassza egyenlege.
- `/faction donate <összeg>` — adományozol a saját valutádból a kasszába.
- A kasszát az **adó** és az **adományok** töltik; a **király** és a **raid-zsákmány** költi.

## Raid (frakcióháború) ⚔️

- `/faction raid <célfrakció> [terület]` — **csak a király** hirdethet. A **nevezési díj** a
  kasszából megy. A raid alapból a védő **fővárosáért** folyik (vagy a megadott területért);
  ha a védőnek nincs területe, kötetlen, csak-ölés raid lesz.
- **Felkészülés (alapból 2 perc):** mindkét oldal harcosai jelentkeznek — `/faction raid join`
  (alapból **max 10 fő/oldal**; a hirdető király automatikusan bekerül). `/faction raid status`
  mutatja az állást.
- **Harci szakasz (alapból 15 perc):** csak a **jelentkezett harcosok közti ölés** szentesített
  (nem bűn) és **pontozó** (alapból 5 pont) — területhez kötött raidnél csak akkor ér pontot,
  ha az áldozat a **raid-zónán belül** esik el. Aki nem jelentkezett, arra a békeidős
  bűn-szabályok élnek raid alatt is!
- **Pont-tartás:** a raid-terület **középpontja** elfoglalható objektíva — minden bent álló
  harcos **pontot termel** az oldalának (alapból 5 mp-enként +1). A bossbar élőben mutatja
  a pontállást.
- A végén a **több pontot szerző** oldal **hadizsákmányként** elviszi a vesztes kasszájának egy
  részét, és **liga-pontot** kap a szezonba.
- **Terület-átvétel:** ha a **támadó** nyeri a területhez kötött raidet, a terület **átkerül
  hozzá** (a fővárosi státusz elvész — a hódítmány nem lesz új főváros).
- A **győztes frakció online tagjai** kapnak egy **győzelmi buffot** (Erő + Regeneráció).
- Raid alatt a jelentkezett harcosok az ellenség földjén **bűntelenül zsákmányolhatnak**
  a konténerekből is.

### Ostromágyú 💥

Raidhez bevethetsz egy **craftolható ostromfegyvert**:
- Recept: **vasblokk-keret + TNT + tűzpor** (TNT-csille alapú).
- **Csak aktív raid alatt** sül el. Jobb katt = **pusztító, de terep-barát robbanás** a célzott
  pontra (sebzi az ellenfeleket, de **nem rombolja le a világot**). Raiden kívül nem működik.

## Frakció-reputáció

A frakciók **barátok vagy ellenségek** lehetnek (a szerver állítja, és raid alatt a hadakozók
automatikusan ellenségek). Ez a **piaci árakat** módosítja: ellenségtől **drágább** (+25%),
szövetségestől **olcsóbb** (−10%) vásárolni. Lásd: [Valuta és gazdaság](03-valuta-gazdasag.md).

## Nekromanta lélekszilánk (csak Nekromantáknak)

Ha **Nekromanta-specet** játszol, minden megölt ellenség után **lélekszilánkot** kapsz:
- `/souls` — megnézed, hány szilánkod van.
- `/souls champion` — a szilánkokból **megerősített Wither-csontváz bajnokot** idézel (erősebb,
  mint a szokásos szolgák).

---

➡️ Tovább: [Küldetések](12-kuldetesek.md) • [Vissza a tartalomhoz](README.md)

## Frakció-szállítmány — védd vagy rabold! 🐫

A király a kasszából **szállítmányt indíthat**: `/faction caravan send <összeg>`. A rakomány
egy kihirdetett **őrzőpontra** kerül (mindenki látja a koordinátákat!), és pár percig ott áll:

- Ha a szállítmány **túléli az ablakot**, a kassza a rakományt **nyereséggel** kapja vissza.
- Ha egy **ellenséges frakció** játékosa leöli a konvojt, a rakomány **az övék**.
- Saját frakciós „baleset" esetén a rakomány **elvész** — ne álljatok a saját karaván útjába!

Kockázat és jutalom: minél nagyobb a rakomány, annál édesebb célpont. Gyűjtsd a védőket!

## Becsület-párbaj és kém-álca ⚔🕵

- **Becsület-párbaj:** ha bűnös vagy, elégtételt ajánlhatsz: `/parbaj kihiv <név>`. Ha a sértett
  elfogadja (`/parbaj elfogad`), 3 perces, beleegyezéses párbaj indul — **nem termel bűnt**, és
  ha a bűnös nyer, **egy bűnpontja letörlődik**. Hetente legfeljebb kétszer.
- **Kém-álca:** `/kem <célfrakció>` — 60 másodpercre álnevet öltesz (felderítéshez!). Raid alatt
  nem megy, és **egyetlen ütés — adott vagy kapott — azonnal lebuktat**. Az álca a bűn alól nem
  ment fel: a lopás lopás marad.
