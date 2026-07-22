# 1. Kezdő lépések 🚀

Most léptél be először? Semmi gond — kövesd ezt a pár lépést, és máris benne vagy a játékban.

> **Nem kell semmit előre tudnod.** Se a történetet, se a rendszereket nem kell elolvasnod
> ahhoz, hogy játssz: az első belépéskor egy kezdő küldetés-lánc mindent megtanít, a világ
> története pedig játék közben, apránként tárul fel — csak annak, aki kíváncsi rá. Ez a
> kézikönyv is csak segédlet: akkor lapozd fel, ha elakadtál vagy mélyebbre ásnál.

## Az első 10 perced

1. **Válassz frakciót.** Írd be: `/faction join <frakció>` — a frakciók: `red` (Piros),
   `blue` (Kék), `neutral` (Semleges). Ez dönti el a **pénznemedet** és egy **passzív bónuszt**
   (pl. a Pirosat nem égeti a tűz). Részletek: [Frakciók](02-frakciok.md).
   > A `dark` (Sötét) frakcióba **nem lehet csak úgy** belépni — oda csak „bűnös" játékos kerül.
2. **Nyisd meg a karakterlapod:** `/profile`. Ez a **központi menü**. A fejen látod a fontos
   adataidat, és gombokról eléred az összes almenüt: **Kaszt, Specializáció, Szakma, Talentek,
   Képesség-fa**.
3. **Válassz kasztot** a Kaszt menüből — **13 kaszt** közül (Varázsló, Harcos, Íjász, Orgyilkos,
   Druida, Paplovag, Halállovag, Sámán, Szerzetes, Pap, Boszorkánymester, Démonvadász,
   Sárkányidéző), majd **igényeld a Lélekkapocsodat** (egy gomb ugyanott). Ezzel
   használod a varázslataidat. (Minden kasztnak saját **Erő-csíkja** is van a HUD-on — a legtöbb
   spell ezt fogyasztja, és idővel visszatöltődik; a vér/rituálé/fizikai spellek HP/XP/éhséget
   kérnek. Lásd [Kasztok](04-kasztok.md).)
4. **Tanulj szakmát** a Szakma menüből: **1 gyűjtögetőt** (pl. Bányász) és **1 készítőt**
   (pl. Kovács). A Halász és Szakács alapból a tiéd.
5. **Kezdj el játszani!**
   - **Szörnyeket ölve** kapsz **kaszt-XP-t** (így szintezel és nyitsz új képességeket).
   - Harcban minden találatod fölött **lebegő sebzés-szám** jelzi, mennyit ütöttél; ha meghalsz,
     a chatben **halál-összegző** mutatja, mi vitt el (utolsó 10 mp találatai + összesített sebzés).
   - **Bányászva / aratva / horgászva / főzve** kapsz **szakma-XP-t**.
   *(Az első belépésedkor automatikusan elindul egy rövid **kezdő küldetés-lánc** — „Beszélj a
   hírnökkel" → „Első csata" → „Első gyűjtögetés" —, ami végigvezet a kezdésen, és minden
   állomásért jutalmat ad. Lásd: [Küldetések](12-kuldetesek.md).)*
6. **Vedd fel a kaszt-próbádat:** `/quest list`, majd `/quest accept <id>`. Ezzel jutalmat
   szerezhetsz. Részletek: [Küldetések](12-kuldetesek.md).

Első belépéskor egy rövid **bemutató cím-szekvencia** is lejátszódik — ez csak egyszer fut le.

## Mit hol találok?

| Amit szeretnél | Hová menj |
|---|---|
| **Mindent egy helyen, kattintással** | **`/menu`** |
| Megnézni az adataimat | `/profile` |
| Képességet használni | tartsd kézben a Lélekkapcsot → jobb katt |
| Pénzt nézni / adni | `/bank balance` — pénzt adni a fizikai veret kézből átadásával lehet |
| Eladni valamit | `/market sell <ár>` |
| Küldetést felvenni | `/quest list` |
| Csapatot alakítani a barátaiddal | `/party invite <név>` — lásd [Party](15-csapat.md) |
| Megvédeni a házam (saját birtok) | állj a chunkba és `/claim` — lásd [Területek](13-teruletek.md) |

> A teljes parancslista: [Parancsok](14-parancsok.md).

## 🖥️ Képernyő: HUD, tablista, sebzés-számok

A szerver **saját, natív** felületet ad (nem kell hozzá külső mod vagy resource-trükk):

- **Dinamikus HUD (oldalsáv):** harc közben a HUD **a harchoz fontos sorokra** vált (erőforrás,
  csapat), és egy **forgó infósor** váltogatja a kevésbé sürgős adatokat — így a korlátozott hely
  mindig a leghasznosabbat mutatja. Célzáskor egy **célpont-sor** is megjelenhet az ellenfél
  életével.
- **Natív tablista:** saját **fejléc/lábléc** (animációkkal), a nevek a rangod szerint rendezve,
  és **relációs háború-színek** — raid alatt az ellenséges frakció neve **pirosan** villan a
  tablistádban és a fejük fölött. A **ping** is színkódolt.
- **Lebegő sebzés-számok:** ütéskor a sebzés száma felugrik a célpont felett (alapból **csak te
  látod, amit te okozol** — configból állítható/kikapcsolható).

---

➡️ Tovább: [Frakciók](02-frakciok.md) • [Vissza a tartalomhoz](README.md)
