# 9. Relikviák és rituálék ✅

A **relikviák** legendás, egyedi tárgyak különleges erővel. Két nagy szabály van rájuk:

- **Egy relikviából csak EGY létezhet** a szerveren egyszerre.
- Ha a tulajdonosa **14 napig nem lép be**, a relikvia **füstként elenyészik**, és újra
  megszerezhetővé válik (más is megkaphatja).

## A Mételytépő ⚔️

Egy különleges **harci fejsze**, ami a **bűnösök (sinnerek) ellen** hat:
- **Megbélyegzi** és **megbünteti** a bűnös játékosokat (Justice / Honor Eye képességek).
- **Lefagyasztja** az élőhalottakat (zombi, csontváz).
- **Fegyver-relikvia:** ha a tulajdonosát **megölik PvP-ben**, a fejsze **a gyilkosé lesz**!
  (A „passzív" relikviák — pl. a szárnyak — ettől védettek.)

## A négy frakció-szárny (elytra-relikviák) 🪽

Mind a négy frakciónak van egy saját **szárnya**. **Csak a tulajdonos ÉS a megfelelő frakció
tagja** használhatja:

| Szárny | Frakció | Mit tud |
|---|---|---|
| 🔴 **Főnix-szárny** | Piros | Tűz/láva-immunitás; **zuhanáskor lángvihar** (felgyújtja a közeli ellenfeleket) |
| 🔵 **Zúzmara-szárny** | Kék | Fagyimmunitás; **felszálláskor megfagyasztja** a környező ellenfeleket |
| ⚪ **Vándorszél** | Semleges | **Nincs zuhanósebzés**; felszálláskor **széllökés-boost** |
| ⚫ **Csontszárny** | Sötét | Wither-immunitás; **éjszakai repüléskor árnyformába** vált (láthatatlanság + sebesség) |

## Rituálé-oltárok 🔮 — így szerzed meg a szárnyakat

A négy szárnyat **nem lehet craftolni** — egy **oltáron kell megidézni** őket. Keress (vagy
építs) egy adott **oltár-blokkot**, gyűjtsd össze a hozzá tartozó **áldozati tárgyakat**, majd
állj az oltárra és **lopakodás (SHIFT) + jobb katt**:

| Szárny | Oltár-blokk | Áldozati tárgyak |
|---|---|---|
| 🔴 Főnix-szárny | Magmatömb | 8 lángrúd, 16 tűzcsóva, 1 aranytömb |
| 🔵 Zúzmara-szárny | Kék jég | 16 tömör jég, 8 prizmarin-kristály, 1 gyémánttömb |
| ⚪ Vándorszél | Ametiszttömb | 32 toll, 8 fantommembrán, 1 smaragdtömb |
| ⚫ Csontszárny | Lélektalaj (soul soil) | 8 csonttömb, 1 wither-csontvázkoponya, 2 netherit-törmelék |

Ha a szárnynak **már van élő tulajdonosa**, nem idézheted meg újra (az „egy példány" szabály
miatt). Várnod kell, amíg felszabadul.

### Multi-block szentélyek 🏛️

Az oltárok **komoly, több-blokkos szentélyek**: nem elég a mag-blokk, meg kell **építeni a teljes
szerkezetet** köré. Az alapminta egy **5×5-ös alapzat** (a mag-blokk alatt, tematikus blokkból) és
**4 két-magas saroktorony** a külső sarkokban — ha a szentély hiányos, az oltár szól, mielőtt
aktiválnád. A pontos mintát a `config/relics.yml` `structure` mezője adja.

### Egyéb oltárok 🕯️ — nem csak szárnyak

Az oltár-rituálé nem csak relikviát adhat. Ugyanúgy működik (építsd meg a szentélyt, gyűjtsd össze
az áldozatot, SHIFT + jobb katt a mag-blokkon), de más a kimenet — és ezek **ismételhetők**
(van egy rövid „feltöltődés"):

| Oltár | Mag-blokk | Áldozat | Mit ad |
|---|---|---|---|
| 🕯️ Feloldozás | Lélek-lámpás | 3 aranytömb, 2 ghast-könny, 1 megmentő-totem | Leveszi a **bűnös-jelet** és nullázza a bűneidet (a sötét paktumot nem oldja fel) |
| 🏠 Hazatérés-kő | Iránytű-kő (lodestone) | 2 ender-gyöngy | A frakciód **fővárosába teleportál** |

### Kaszt-szentélyek ⚜️ — mind a 13 kasztnak

Minden kaszt kap egy **saját szentélyt**, ami csak neki ad tematikus, időleges buffot (5 perc,
`requires-class` kapuval). Mindegyik 5×5-ös szentély, egyedi mag-blokkal és áldozattal:

| Kaszt | Mag-blokk | Buff |
|---|---|---|
| Varázsló | Varázsló-asztal | Sietség II + Regeneráció |
| Harcos | Üllő | Erő II + Ellenállás |
| Íjász | Nyílkészítő-asztal | Gyorsaság + Ugrás II |
| Orgyilkos | Sculk-Lélekkapocs | Láthatatlanság + Gyorsaság II |
| Druida | Virágzó azálea | Regeneráció + Ugrás + Vízlégzés |
| Paplovag | Fényporkő (glowstone) | Ellenállás + Regeneráció + Tűzállóság |
| Halállovag | Síró obszidián | Erő + Felszívás III |
| Sámán | Villámhárító | Sietség + Conduit-erő + Gyorsaság |
| Szerzetes | Harang | Gyorsaság II + Ugrás + Ellenállás |
| Pap | Tengeri lámpás | Regeneráció + Ellenállás |
| Boszorkánymester | Újraéledés-horgony | Erő + Tűzállóság + Felszívás |
| Démonvadász | Vésett nether-tégla | Gyorsaság II + Erő |
| Sárkányidéző | Végkő-tégla | Sietség II + Felszívás + Tűzállóság |

> Minden oltár-blokk, szerkezet, áldozat és hatás a `config/relics.yml` `rituals:` szekciójában
> testreszabható — új oltárt is felvehet az admin (`type: relic|cleanse|buff|home`,
> `requires-class`/`requires-faction` kapukkal).

---

➡️ Tovább: [Világesemények](10-vilagesemenyek.md) • [Vissza a tartalomhoz](README.md)
