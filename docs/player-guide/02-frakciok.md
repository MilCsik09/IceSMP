# 2. Frakciók ✅

A **frakció** az „oldalad" a világban. Négy van, és mindegyiknek saját **pénzneme** és **passzív
bónusza** (egy állandó képesség, amiért nem kell semmit csinálnod) van.

Belépés: `/faction join <red|blue|neutral|dark>` • Kilépés: `/faction leave`

> 🏷️ **Kanonikus nevek:** a négy frakció a lore szerint **Láng** (*Perinfernicitas*, `red`),
> **Fagy** (*Cryghaliris*, `blue`), **Menedék** (*Ryanora & Caldestera*, `neutral`) és
> **Kitaszított** (*A Kitaszítottak*, `dark`). A parancs-azonosítók változatlanok.

Az **első csatlakozásod** teljesen **ingyenes és időzítetlen**, és mindenki a **Menedékben (Semleges) kezd** — új játékosként a **Menedék spawnján** jelensz meg. Amikor királyságot
választasz, a plugin **odateleportál az új királyságod spawnjára**, és ha nincs ágyad /
respawn-horgonyod, halál után is a **saját királyságod spawnján** éledsz újra — így mindig
tudod, hol a fővárosod.

A **Menedékből bárhová ingyen** válthatsz, és a **Kitaszítottak közé lépés is mindig ingyenes**
(annak a bűnös-feltétel + az örök paktum az ára). Minden más frakcióváltás (Láng↔Fagy,
illetve vissza a Menedékbe — a `/faction leave` kilépés is ugyanígy fizetős!) a **jelenlegi
frakciód valutájában** kerül **alapból 500-ba**, és utána **72 óráig** nem válthatsz megint
(`factions.switch.cost` / `factions.switch.cooldown-hours` a configban) — ez a
frakció-hopping ellen véd. **Váltani csak a Menedék fővárosában, Caldesterában állva lehet**
(ott, ahol a királyság-választó hírnök NPC is áll) — így a döntés mindig a semleges földön,
„hivatalosan" születik meg.

A passzívok **egy szintre vannak hangolva** — mindegyik kb. egyformán hasznos, csak más
helyzetben erős, így a választás ízlés (playstyle) kérdése, nem „melyik a legjobb":

| Frakció | Passzív bónusz | Mire jó |
|---|---|---|
| 🔴 **Láng** (Perinfernicitas) | Immunis a **tűz / láva / forró blokk** sebzésére | Hő-mesterség: a Nether és a láva veszélytelen |
| 🔵 **Fagy** (Cryghaliris) | Immunis a **fagyásra ÉS a fulladásra**; **50% eséllyel** nem veszít éhséget | Víz-mesterség: végtelen búvárkodás, hideg biómok, víz alatti építés/aknázás |
| ⚪ **Menedék** (Ryanora & Caldestera) | **Nincs zuhanás-sebzés** (esésimmunitás); a **nem-ellenséges mobok és az endermanök** nem támadják; **adómentes** | Biztos léptű vándor: magasból is leugorhatsz, az endermanre ránézhetsz, és nincs állampolgári adó |
| ⚫ **Kitaszított** (A Kitaszítottak) | Immunis a **wither-sebzésre**; az **élőhalottak (zombi, csontváz, phantom, zoglin) nem támadják** | A **legerősebb PvE-passzív**: éjszaka és barlang szinte veszélytelen — cserébe az **örök bűnös-jelölés** |


### 🍲 A frakciók konyhája (K6)

A kódex szerint minden népnek megvan a maga konyhája — és a frakció-aura ezt meg is követeli:

- **Fagy (BLUE):** halon él — ha ~12 órán át nem eszel halat (bármely hal, vagy a séf
  **Fagyasztott Tavi Pisztrángja**), enyhe honvágy tör rád (rövid Éhség + emlékeztető).
  A Pisztráng rövid **felszívódás-pajzsot** is ad.
- **Láng (RED):** tojás-ételen él — a **Fűszeres Főnixtojás-Rántotta** (vagy tökpite/torta)
  nullázza a honvágyat; a Rántotta rövid **tűz-ellenállást** is ad.
- **Ünnepi ételek — minden frakciónak a magáé** (a séf-recept frakció-kapus):
  a Fagy **Sárkány-pörköltje** (Erő), a Láng **Vérszavannai Vadlakomája** (Gyorsaság +
  tűz-oltalom), a Menedék **Vándorünnep Lepénye** (Szerencse + Gyorsaság) és a
  Kitaszítottak **Hamvak Lakomája** (Felszívódás + Éjjellátás). Ahol van honvágy-
  kötelezettség, az ünnepi étel azt is teljesíti.
- **Menedék:** a **Tiltott Kakaóbabos Sütemény** (a cukrászok Asterlayna Gyümölcsének hívják)
  fogyasztva **„robban"** — feldob, felgyorsít, csillagszóró-effekttel. A Bankárszövetség
  hivatalosan tiltja. Hivatalosan.
- **Kitaszítottak (DARK):** a **Mortengradi Hamukenyér** a kenyerük (rövid éjjellátást ad) —
  de honvágy-kötelezettségük **nincs**: a Kitaszítottaknak nincs otthonuk, amire honvágyuk
  lehetne.

A honvágy **puha** mechanika (config: `factions.food-duty`), új játékost és frissen váltót
türelmi idő véd.

> 💬 A **chatben a neved a frakciód színében** jelenik meg (a rang-prefixszel együtt) — így
> mindenki azonnal látja, ki melyik oldalon áll.

## ⚫ A Kitaszítottak — a „Sötét" (dark) frakció

A Kitaszítottak frakciója nem egy „sima választás" — ez a **börtön a világ végén**, a **Kitaszítottak**
száműzetése, a Néma Királynő árnyéka. **Kétféleképpen** kerülsz ide:

- **Bűnözőként:** akit **4 bűnnél (sinner)** a világ automatikusan ide **száműz** (lásd lentebb).
- **Lelepleződött Suttogóként:** akit rajtakapnak, hogy titokban **a Suttogók** hálózatát szolgálja
  (lásd 🔜 lentebb), azt egy csapásra ide veti a bélyeg.

Belépéskor megköttetik a **sötét paktum**: a bűnös jelölést **soha nem lehet levenni** — még akkor
sem, ha elhagyod a frakciót. Az **egyetlen** visszaút a **vezeklés-küldetéslánc** (lásd
[Küldetések](12-kuldetesek.md)). Cserébe tiéd a **legerősebb PvE-passzív**: az élőhalottak békén
hagynak, s a wither sem árt — a Néma Királynő megjelöli, de meg is óvja a szolgáit.

A Kitaszítottaknak is van otthonuk: **Mortengrad, a Holtak Városa** — egy élőhalottak lakta **rom-főváros**,
ahová a száműzöttek újraélednek. Mivel rátok nem támadnak a holtak, ez a düledező város nektek menedék;
mindenki másnak halálos csapda. *(A romváros megépítése a szerver-csapatra vár — lásd 🔜.)*

## Hogyan leszek bűnös (sinner)?

- **Gyilkosság:** ha **megölsz egy másik játékost**, **+1 bűnt** kapsz.
- **Árulás:** ha a **saját frakciótársadat** ölöd meg, az súlyosabb — **+2 bűn**. (A Menedék népe
  laza közösség: köztük az ölés sima gyilkosságnak számít.)
- **Lopás:** ha egy **másik frakció területén** álló konténerből (láda, hordó, kemence,
  hopper…) tárgyat veszel ki, **+1 bűnt** kapsz. Egy fosztogatás-sorozat területenként
  egyszer számít (nem minden kattintás külön bűn).
- **4 bűnnél** automatikusan **száműznek a Kitaszítottak közé** (örök paktummal).
- **Kivétel:** **raid** (frakcióháború) alatt a **jelentkezett harcosok** közti **ölés és az
  ellenség földjén való zsákmányolás nem számít bűnnek** — aki nem jelentkezett
  (`/faction raid join`), arra raid alatt is a békeidős szabályok élnek. Lásd
  [Raid és háború](11-raid-haboru.md).

> A bűnösöket egy különleges relikvia, a **Mételytépő** is megjelölheti és megbüntetheti —
> erről a [Relikviák](09-relikviak.md) oldalon olvashatsz.

## Fejvadászat (körözés) 💰

Aki elér egy bizonyos bűnszámot (alapból **3 bűn**), az **körözötté** válik — a fejére
**fejpénz** kerül (a bűnök száma × egy fix összeg, alapból Creutzérben). A `/bounty`
paranccsal megnézheted a körözési listát: ki körözött és mennyit ér a feje.

Ha **megölsz egy körözött bűnözőt**:
- **megkapod a fejpénzt** (a bankodba);
- **nem kapsz érte bűnt** — ez igazságos kivégzés, nem gyilkosság;
- a bűnöző **bűnszámlálója nullázódik** (a bűnös-jelölése viszont megmarad).

Így a bűnözés kockázatos: minél többet vétkezel, annál nagyobb célpont vagy a fejvadászoknak.

## 🔜 A Suttogók — a rejtett hálózat *(tervezett)*

> *Ez a rendszer még fejlesztés alatt áll — a lore már számol vele, a mechanika hamarosan érkezik.*

A **Suttogók** (Az Éjszaka Gyermekei) nem külön frakció, hanem egy **titkos státusz**, amely a
**látható frakciód fölé** rétegződik. Láng, Fagy vagy Menedék maradsz mindenki szemében — miközben
titkon a Néma Királynő ügyét szolgálod, és **sötét-mágiájú tárgyakhoz** jutsz. A hovatartozásod
kívülről **láthatatlan**: sötét mágia rejti.

**Hogyan csatlakozol?** Egy **titkos, éjszakai rítussal** — magányosan (más játékos nélkül a
közeledben), egy elrejtett **Suttogó-oltárnál**, ahol felajánlod a hűségedet és egy áldozatot. A
rítus helye és módja maga is rejtély: lore-nyomokból, egy titkos hírnöktől vagy egy ritka „meghívóból"
derül ki.

**Hogyan lepleződsz le?** A sötét erő nem tűri a napvilágot:
- ha **közterületen, mások szeme láttára használsz sötét mágiát** (vagy Suttogó-tárgyat);
- ha **rajtakapnak** egy rítuson vagy egy **áruláson** (frakciótárs hátbaszúrása);
- ha a **gyanú** ellened gyűlik, míg át nem üt a bélyeg.

**Mi történik ekkor?** A titkos Suttogóból egy csapásra **Kitaszított** lesz: **bűnössé** válsz, a
világ **száműz a Kitaszítottak közé**, és **azonnali vérdíj** kerül a fejedre — a többiek vadászni
kezdenek rád. A leplet nem lehet visszaölteni; innen csak a **vezeklés** vezet vissza. *(A pontos
mechanika-terv — rítus, gyanú-mérő, vizuális jelek — a fejlesztői ötlettár K9 tétele.)*

## Frakció-viszonyok (reputáció)

A frakciók **barátok vagy ellenségek** lehetnek egymással (a szerver állítja be, és raid alatt
a hadakozók automatikusan ellenségek). Ez a **piaci árakat** is befolyásolja:

- **Ellenséges** frakció tagjától vásárolni **drágább** (+25% felár).
- **Szövetséges** frakciótól **olcsóbb** (−10%).

Részletek: [Valuta és gazdaság](03-valuta-gazdasag.md).

---

➡️ Tovább: [Valuta és gazdaság](03-valuta-gazdasag.md) • [Vissza a tartalomhoz](README.md)
