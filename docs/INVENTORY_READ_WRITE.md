# Inventory read/write runbook

Ez a dokumentum a natív invsee és adományláda aktuális, mérvadó működését írja le.

## Invsee

Az egyetlen parancs:

```text
/invsee <online játékos>
```

Nincs több `read|edit` vagy `main|ender` argumentum.

- `icesmp.moderation.inventory.edit` jogosultsággal az első megnyitó automatikusan write sessiont kap.
- Egy céljátékoshoz egyszerre pontosan egy write session tartozhat.
- Minden további egyidejű megnyitó automatikusan read-only módba kerül.
- Csak read jogosultsággal a session mindig read-only.
- A fő inventory és az ender chest között a GUI saját gombjával lehet váltani; a writer lease megmarad.
- Bezárás, viewer-quit, target-quit, permissionvesztés vagy plugin-disable elengedi a writer lease-t.
- Egy admin egyszerre legfeljebb egy cél write lease-ét birtokolhatja.
- A tényleges itemcsere továbbra is a meglévő invsee escrow-, target-scheduler- és auditútvonalon fut.

A `/mod` céljátékos-oldalán egyetlen **Inventory / Ender chest** gomb található, amely ugyanezt az automatikus parancsot használja.

## Adományláda

A GUI felső, 0–8. slotja egyirányú deposit-zóna. A közös adományok a 9–44. sloton jelennek meg és kattintással továbbra is elvihetők.

Támogatott beadási módok:

- bal kattintás a deposit-zónára: teljes kurzorstack;
- jobb kattintás: egy darab;
- shift-kattintás a saját inventoryból;
- számbillentyűs hotbar-behelyezés;
- offhand-csere gomb;
- több deposit-slotot érintő inventory drag.

A felső inventory minden vanilla mutációja törölve van. A plugin a műveletet a játékos következő entity-tickjén hajtja végre, és csak akkor vesz el itemet, ha a forrás slot/cursor/offhand teljes stackje még pontosan megegyezik a kattintáskor rögzített snapshotpal.

Egy közös adományt egyszerre csak egy játékos vehet el. Ha a fogadó inventory megtelik, a maradék a játékos helyén esik le.

## Biztonsági határ

A runtime védi a párhuzamos kattintást, stale inventory-snapshotot, cursor/slot versenyt és a normál GUI-duplikációs útvonalakat. A `donations.yml` a meglévő debounced, atomikus YAML-mentést használja; graceful shutdown flusholja a store-t. Az operációs rendszer vagy JVM azonnali összeomlása a legutolsó, még ki nem írt debounce-ablakban nem tekinthető cross-store tranzakciónak.

## Manuális staging checklist

1. Két admin nyissa meg ugyanazt a célt: az első WRITE, a második READ.
2. A writer zárja be; a következő új megnyitás kapjon WRITE módot.
3. MAIN ↔ ENDER váltás alatt maradjon meg ugyanaz a writer lease.
4. Viewer-quit, target-quit, permissionvesztés és plugin-disable után ne maradjon lock.
5. Teszteld az invsee escrowt viewer- és target-kilépéssel szerkesztés közben.
6. Donation cursor bal/jobb katt, shift-click, drag, number key és offhand.
7. Az adományozó zárja be vagy váltsa le azonnal a GUI-t; ne nyíljon vissza automatikusan.
8. Töltsd meg a ládát és érd el a per-player limitet; a forrásitem maradjon érintetlen.
9. Két játékos egyszerre kattintson ugyanarra az adományra; csak egyik kapja meg.
10. Teszteld a megtelt fogadó inventoryt és a leftover dropot.
