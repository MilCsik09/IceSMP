# IceSMP — Fejlesztési ütemterv (ROADMAP)

Az `ideas.md` alaptervei elkészültek. Ez a dokumentum a **következő körös** fejlesztéseket
rögzíti, fázisokra bontva. A fázisokat egyenként, külön commit(ek)ben valósítjuk meg, hogy
build + szerverteszt után haladhassunk a következőre.

Jelölés: ✅ kész • 🔨 folyamatban • ⬜ tervezett

---

## 1. fázis — QoL / élmény-HUD 🔨
Gyors, nagy hatású, jól elszigetelt felület-elemek. (`HudManager` + `HudListener`, `hud.*` config.)
- ✅ **Oldalsáv (sidebar) scoreboard:** frakció, saját valuta-egyenleg, kaszt + szint, aktív esemény.
- ✅ **Boss-bar:** a raid / világboss / vérhold állapotának kijelzése (raidnél hátralévő idővel).
- ✅ **Tab-lista:** frakció-színek és prefixek a játékosnevek előtt.
- ⬜ **Folyamatos cooldown-kijelző** action barban — a 3. fázisba (spellek) tolva, mert a
  spell-állapothoz kapcsolódik (az action bart a spell-rendszer is használja).

## 2. fázis — Talent-fa mélyítése ✅
- ✅ **Aktív talentek:** a `grants-spell` mezővel a talent **képességet old fel** a Katalizátorban
  (pl. a `Felemelkedés` capstone a `Talentum Lendület` ultit adja); respec/lejárat visszavonja.
- ✅ **Egymást kizáró ágak:** az `excludes` mezővel egy ág kizárja a testvérét (pl. `Behemót` vs `Hadúr`).
- ✅ **Csúcs-talent (capstone):** a `requires-spent: N` mezővel a talent csak N elköltött pont után
  nyílik (pl. `Felemelkedés`). A GUI minden zár-okot kiír (szülő / kizárt ág / szükséges pont).

## 3. fázis — Spellek mélyítése 🔨
- ✅ **Költség-egyensúly + új költségtípus:** bevezetve a **HEALTH (élet / „vérmágia")** költség
  a HUNGER és XP mellé. Tematikus átbalanszozás: agresszív/sötét spellek életbe (berserker,
  nekromanta, fantom, finisherek ~12 db), pontos/alkímiai spellek XP-be; az élet-költésű spell
  nem süthető el, ha megölne. (Kérés szerint: ez egy első kör, később finomítandó.)
- ⬜ **Spell-rangok:** a képességek fejleszthető erőssége (pont/erőforrás).
- ⬜ **Kombók:** egymásra épülő képesség-láncok bónusszal.
- ⬜ **Jobb célzás:** célzás-segéd, ráhatás-jelzés.
- ⬜ **Gazdagabb vizuál:** több részecske/hang, becsapódás-effektek.

## 4. fázis — Raid mélyítése ⬜
- ⬜ **10v10 létszámkorlát** és jelentkezés.
- ⬜ **Aréna-/területkötés:** a raid egy adott területhez kötve.
- ⬜ **Objektív-alapú raid:** zászlófoglalás / pont-tartás, nem csak ölésszám.
- ⬜ **Terület átvétele győzelemmel:** a győztes claimet szerez/veszít a vesztes.

## 5. fázis — Frakció-diplomácia ⬜
- ⬜ **Szövetség / békekötés** parancsok a királyoknak.
- ⬜ **Frakció-szintek és -perkek:** a frakció fejlődik (kassza/aktivitás alapján), perkeket old fel.

## 6. fázis — Gazdaság ⬜
- ⬜ **Valódi aukciósház:** licit, lejárat, túllicit-visszafizetés.
- ⬜ **Bank-kamat / kölcsön.**
- ⬜ **Frakció-bolt NPC-k** (fix árú alap-tárgyak, money sink).
- ⬜ **Kereskedő-karaván esemény** (mozgó, kifosztható/kereskedhető).

## 7. fázis — Ranglisták ✅
- ✅ **Leaderboardok GUI-val:** leggazdagabb, legmagasabb szint, legtöbb raid-kill
  (`StatsManager` + `leaderboard.yml`; `/leaderboard` parancs és a `/menu` Ranglisták gomb;
  kategória-váltó gombokkal). A szint/vagyon pillanatkép a világesemény-tickkel frissül,
  a raid-kill a kill-eseménykor nő.

> **Admin extra (kérés):** `/events bloodmoon start|stop` és `/events worldboss` — a vérhold és a
> világboss **kézi triggerelése** (a `/menu` Admin paneljén is gombbal). `BloodMoonManager.forceStart/
> forceEnd`, `WorldBossManager.forceSpawn`.

## 8. fázis — Napi/heti küldetések + elérések ⬜
- ⬜ **Napi/heti küldetések** (rotáló, automatikus visszaállás).
- ⬜ **Achievement (elérés) rendszer** GUI-val és jutalmakkal.

## 9. fázis — Dungeon / invázió ⬜
- ⬜ **Dungeon / instance** (mini-kazamata loot-asztallal) **vagy invázió-esemény** a világboss mellé.

## 10. fázis — Szakma-receptek ⬜
- ⬜ **Egyedi, frakció-specifikus craftolható tárgyak**, hogy a szakmák kézzelfoghatóak legyenek
  (szakma-/szint-/frakció-kötött receptek).

## 11. fázis — Cosmetics ⬜
- ⬜ **Címek, részecske-effektek, kalapok** (a tervezett szezon-jutalom kiterjesztése), GUI-ból kezelve.

## 12. fázis — Korábbról halasztott tételek ⬜
- ⬜ **Megnevezett, szintet lépő állandó pet** (Vadmester perzisztens társa).
- ⬜ **NPC-s / parkour kaszt-próbapályák.**
- ⬜ **Piac lapozás / keresés / kategóriák.**
- ⬜ **Intro kamera-utaztatás / spectator-útvonal.**

---

> Minden fázis végén: `gradlew build` + gyors szerverteszt, majd a következő fázis.
