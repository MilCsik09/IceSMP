# 3. Valuta és gazdaság 💰

A szerveren **igazi, élő gazdaság** van: a pénz értéke változik, lehet kereskedni, és vannak
helyek, ahol a pénz „eltűnik" (hogy ne legyen túl sok belőle). Ne aggódj, lépésről lépésre
elmagyarázzuk.

## A pénzed: tokenek és a bank

Minden frakciónak saját **token**-je (érméje) van: Piros, Kék, Semleges, Sötét. A pénzed
**kétféleképp** létezhet:

- **Fizikai itemként** — token a táskádban (papír-szerű tárgy).
- **Banki egyenlegként** — egy szám, ami nálad „be van fizetve".

Parancsok:
- `/bank balance` — megnézed a banki egyenlegeidet.
- `/bank deposit` — a nálad lévő tokeneket **bankba teszed**.
- `/bank withdraw <valuta> <összeg>` — a bankból **kiveszel** tokeneket.
- `/currency pay <játékos> <összeg> [valuta]` — **utalsz** valakinek.
- `/currency balance` — gyors egyenleg-nézet.

## Dinamikus árfolyam (miért változik a pénz értéke?)

Képzeld el, hogy minél több van valamiből, annál kevesebbet ér — pont, mint a valóságban.

- Ha egy frakció **túl sok** pénzt „termel", az a token **leértékelődik** (kevesebbet ér a
  többihez képest).
- Ha valamiből **kevés** van, az **felértékelődik**.

Megnézheted és válthatsz:
- `/currency rates` — megmutatja az aktuális árfolyamokat (mennyi van belőle, mennyit ér, mi a
  váltási arány).
- `/currency exchange <összeg> <honnan> <hová>` — átváltod egyik valutát a másikra a mostani
  árfolyamon (kis díjjal). Aki figyeli a piacot, jól járhat!

A fővárosokban **árfolyamtáblák** (lebegő hologramok) is mutatják az aktuális értékeket. 📊

## Piactér (kereskedés más játékosokkal) 🛒

- `/market sell <ár> [valuta]` — a **kezedben tartott tárgyat** kiteszed eladásra. (Alapból a
  saját frakciód valutájában; max. 5 tételed lehet egyszerre.)
- `/market` — megnyitja a **böngészőt**; kattints egy tételre a **megvásárláshoz** (a banki
  egyenlegedből fizet).
- `/market cancel` — visszaveszed a saját tételeidet (visszakapod a tárgyat).

**Eladási díj:** minden eladásból kb. **10% eltűnik** a gazdaságból — ez tartja kordában az
inflációt (a pénz „elértéktelenedését").

**Reputáció-ár:** a vételár attól is függ, milyen viszonyban van a frakciód az eladóéval:
**ellenségtől drágább (+25%)**, **szövetségestől olcsóbb (−10%)**.

## Hová „tűnik" a pénz? (money sinkek)

Hogy a pénz értékes maradjon, több helyen is „elszívódik":

- **Állampolgári adó:** óránként a frakciótagok a saját token-egyenlegük **2%-át** befizetik a
  frakciókasszába (a Semlegesek mentesek).
- **Kereslet-sokk** (időnként): egy véletlen valuta értéke átmenetileg **megugrik** (x1,2–1,6) —
  ezt egy üzenet jelzi mindenkinek. Jó alkalom kereskedni!
- **Eladási díj, raid-nevezés, rituálé-alapanyagok** — ezek is mind „elnyelnek" pénzt.

## Lélekkő — a veszélyes vidékek jutalma

A spawntól messze a szörnyek erősebbek. A **magas szintű (3+) szörnyek** eséllyel **Sötét
tokent** (lélekkövet) ejtenek — így a távoli, veszélyes helyeken kalandozni **gazdaságilag is
megéri**.

---

➡️ Tovább: [Kasztok](04-kasztok.md) • [Vissza a tartalomhoz](README.md)
