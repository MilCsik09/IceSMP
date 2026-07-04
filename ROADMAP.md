# IceSMP — Fejlesztési ütemterv (ROADMAP)

Ez az **egyetlen előre néző terv-dokumentum**. A megvalósult állapotot a
[README.md](README.md) és a [PLAYER_GUIDE.md](PLAYER_GUIDE.md) írja le, az architektúrát a
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), a tesztelést a [PLAYTEST.md](PLAYTEST.md).
(A korábbi terv-doksik — ideas.md, todo.md, CONTENT-PLAN, DEPTH-ROADMAP, a fázis-napló —
megvalósultak és törölve lettek; a még nyitott pontjaik itt élnek tovább.)

Jelölés: ⬜ tervezett • 🔨 folyamatban • 💡 ötlet (nincs elköteleződés)

---

## Nyitott fejlesztések

### Ismert hibák / technikai kockázatok
- Jelenleg nincs nyitott, reprodukált technikai blocker. A legutóbbi auditban talált Folia
  célpont-scheduler hibák, az Angry Chicken cross-region damager kockázata és az orb Java 21
  build-környezete javítva lett; playtesten továbbra is figyeljétek a konzolt
  `region`/`scheduler`/`IllegalStateException` stacktrace-ekre.

### Játékmenet
- ⬜ **Raid-mélyítés:** 10v10 létszámkorlát és jelentkezés; aréna-/területkötés;
  objektív-alapú raid (zászlófoglalás, pont-tartás); terület átvétele győzelemmel.
  (A győztes-buff és az ostromágyú kész.)
- ⬜ **Bűn-rendszer bővítés:** lopás/árulás detektálás (most csak a gyilkosság számít bűnnek).
- ⬜ **Kaszt-questek felturbózása:** NPC-s próbák az „ölj X-et" helyett (FancyNpcs a szerveren
  elérhető; a parkour-keret kész).
- ⬜ **Szezonliga jutalom:** a győztes frakció kozmetikai/relikvia-jutalma a szezon végén.
- ⬜ **Frakció-diplomácia:** szövetség/békekötés parancsok a királyoknak; frakció-szintek és perkek.
- 💡 **Külön ulti-töltő sáv:** a kivett „kirobbanás" helyett egy második, lassan töltődő ulti-mérő,
  hogy az erőforrás-költség MELLETT látványos burst-jutalom is legyen.
- 💡 **Cosmetics:** részecske-effektek, kalapok (a szezon-jutalom kiterjesztése), GUI-ból.
  (Címek/rangok NEM — ütközne a szerver rang-pluginjaival.)

### Gazdaság
- ⬜ **Valódi aukciósház:** licit, lejárat, túllicit-visszafizetés (a fix-áras piac + keresés + lapozás kész).
- 💡 **Bank-kamat / kölcsön**, **frakció-bolt NPC-k** (money sink), **kereskedő-karaván esemény**.

### Balansz (élő playtest visszajelzés alapján)
- 🔨 **Hibrid spell-költség finomhangolás:** határeset-spellek „valutájának" pontosítása
  (pl. a 8-éhséges Gyökerezés), tier-alapú erőforrás-költségek és regen-ráta hangolása.
- ⬜ **Frakció-passzív számok** felülvizsgálata playtest után (a Semleges invis már kivéve).
- ⬜ **Spell-mesterség kiterjesztés:** a rang sebzés/hatás-skálázása (most csak cooldown-ra hat).

### Világépítés (szerver-csapat, nem plugin-kód)
- ⬜ Fővárosok, az Élet Fája (spawn), a Sötét romváros megépítése; `/territory` kijelölések.
- ⬜ Parkour-pályák, rituálé-oltár helyszínek és intro-kamera waypointok kihelyezése.

---

## Karbantartási elvek

- Minden változás **Folia-szabálykövető** (ARCHITECTURE 4. fejezet): entitást csak a saját
  régió-szálán mutálunk; cross-entity művelet a cél `getScheduler()`-én fut.
- Új spell a `ConfiguredSpell` builderrel + `classes.yml` unlock-bejegyzéssel; új világtartalom
  config-vezérelt. Fordítás-ellenőrzés minden változás után.
- Player-facing szöveg magyarul; a guide-ok (PLAYER_GUIDE + docs/player-guide + PLAYTEST)
  minden játékmenet-változással együtt frissülnek.
