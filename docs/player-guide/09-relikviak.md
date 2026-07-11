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

Az oltárok **több-blokkos szentélyek**: nem elég a mag-blokk, köré kell építeni a teljes
szerkezetet is (a `config/relics.yml` `structure` mezője adja a mintát). A szárnyak alapmintája
egy **mag-blokk**, alatta egy alapkő, és **4 sarok-pillér** átlósan — ha a szentély hiányos, az
oltár szól, mielőtt aktiválnád.

### Egyéb oltárok 🕯️ — nem csak szárnyak

Az oltár-rituálé nem csak relikviát adhat. Ugyanúgy működik (építsd meg a szentélyt, gyűjtsd össze
az áldozatot, SHIFT + jobb katt a mag-blokkon), de más a kimenet — és ezek **ismételhetők**
(van egy rövid „feltöltődés"):

| Oltár | Mag-blokk | Áldozat | Mit ad |
|---|---|---|---|
| 🕯️ Feloldozás | Lélek-lámpás | 3 aranytömb, 2 ghast-könny, 1 megmentő-totem | Leveszi a **bűnös-jelet** és nullázza a bűneidet (a sötét paktumot nem oldja fel) |
| ⚔️ Erő áldása | Aranyozott feketekő | 12 aranyrúd, 8 lidércpor | 5 percre **Erő II + Ellenállás + Tűzállóság** |
| 🏠 Hazatérés-kő | Iránytű-kő (lodestone) | 2 ender-gyöngy | A frakciód **fővárosába teleportál** |

Vannak **kaszt-specifikus** oltárok is: pl. a **Varázsló arkán szentélye** (varázslótábla +
könyvespolc-sarkok) csak varázslónak ad arkán buffot. Az admin bármelyik kasztnak felvehet
sajátot a `requires-class` kapuval.

> Minden oltár-blokk, szerkezet, áldozat és hatás a `config/relics.yml` `rituals:` szekciójában
> testreszabható — új oltárt is felvehet az admin (`type: relic|cleanse|buff|home`,
> `requires-class`/`requires-faction` kapukkal).

---

➡️ Tovább: [Világesemények](10-vilagesemenyek.md) • [Vissza a tartalomhoz](README.md)
