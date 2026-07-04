# 13. Frakcióterületek 🏰

A világban vannak **frakcióterületek** és **fővárosok** — ezeket az adminok jelölik ki.

## Mit veszel észre belőlük?

- Amikor **átlépsz** egy terület határán, a képernyő alján (action bar) egy felirat jelzi, hol
  vagy: pl. „✦ Piros főváros ✦", vagy „vadon" ha senkié.
- Minden frakciónak lehet **1 fővárosa** és több **claimelt** (lefoglalt) területe.

## Építésvédelem (ha be van kapcsolva)

A szerver bekapcsolhatja az **építésvédelmet** (`territory.protection.enabled`). Az alap
konfigurációban ez **ki van kapcsolva**, tehát alapból csak a határátlépés-értesítés fut.
Ha az adminok bekapcsolják:
- **Idegen frakció területén nem tudsz építeni vagy bontani.**
- A saját frakciód területén és a vadonban szabadon építkezhetsz.
- (Adminoknak van „bypass" joguk, nekik mindenhol szabad.)

> Ez megvédi a frakciók fővárosait a rongálástól. Ha valahol nem tudsz blokkot lerakni/törni,
> valószínűleg egy másik frakció területén állsz.

## Admin parancsok (csak adminoknak)

- `/territory setcapital` — főváros kijelölése.
- `/territory claim` — terület lefoglalása.
- `/territory remove` — terület törlése.
- `/territory list` / `/territory info` — területek listája / infó.

---

➡️ Tovább: [Parancsok listája](14-parancsok.md) • [Vissza a tartalomhoz](README.md)
