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

## 3. fázis — Spellek mélyítése ✅
- ✅ **Költség-egyensúly + új költségtípus:** bevezetve a **HEALTH (élet / „vérmágia")** költség
  a HUNGER és XP mellé. Tematikus átbalanszozás: agresszív/sötét spellek életbe (~12 db),
  pontos/alkímiai spellek XP-be; az élet-költésű spell nem süthető el, ha megölne.
- ✅ **Spell-rangok (mesterség):** `SpellMasteryManager` + `/spell` — frakcióvalutáért
  fejleszthető rang (max 5), ami **csökkenti a cooldownt** (rangonként -8%, max -50%).
  Az action bar mutatja a rangot (★N).
- ✅ **Kombók:** config-vezérelt párok (`spells.combos.pairs`) — ha az időablakon belül a `first`
  után a `second` spellt sütöd el, a második **gyorsabban épül fel** (cooldown-visszatérítés) + látvány.
- ✅ **Gazdagabb vizuál / célzás-visszajelzés:** minden castnál általános „flourish" (részecske +
  hang), kombónál erősebb effekt; a kombó-jelzés és a hiányzó-cél visszajelzés az action barban.
- 🔮 *Jövőbeli finomítás:* a rang **sebzés/hatás** skálázása (most cooldown-ra hat) — invazívabb,
  külön körben.

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

## 8. fázis — Napi/heti küldetések + elérések 🔨
- ✅ **Achievement (elérés) rendszer** GUI-val és jutalmakkal (`AchievementManager`,
  `/achievements` + `/menu` Elérések gomb): mérföldkövek (raid-kill, kaszt-szint, vagyon,
  össz-szakmaszint), a stats-tickkel kiértékelve, valuta-jutalommal.
- ⬜ **Napi/heti küldetések** (rotáló, automatikus visszaállás) — későbbi körre.

## 9. fázis — Dungeon / invázió ✅
- ✅ **Invázió-esemény** (`InvasionManager`, `/events invasion`): skálázott szörnyhorda spawnol
  egy játékos köré (XP + lélekkő-eséllyel), időnként vagy admin-paranccsal. (A teljes
  dungeon/instance — saját világgenerálás — nagyobb, külön projekt.)

## 10. fázis — Szakma-receptek ✅
- ✅ **Szakma-/szint-kötött mestermű receptek** (`ProfessionRecipeManager`): Tárnász Csákány,
  Favágó Fejsze, Bástya Pajzs, Bölcs Könyve — csak a megfelelő szakma + szint craftolhatja.
  (A frakció-specifikus gating ugyanezzel a mechanizmussal bővíthető.)

## 11. fázis — Cosmetics ⬜
- ⬜ **Címek, részecske-effektek, kalapok** (a tervezett szezon-jutalom kiterjesztése), GUI-ból kezelve.

## 12. fázis — Korábbról halasztott tételek 🔨
- ✅ **Piac lapozás** (`MarketGUI` oldalazás: alsó sor előző/következő gomb + oldal-jelző).
- ⬜ **Piac keresés / kategóriák** — szöveges bevitelt igényel, későbbi körre.
- ⬜ **Megnevezett, szintet lépő állandó pet** (Vadmester perzisztens társa) — perzisztens
  companion-entitás + leveling + Folia-helyes követő-AI; nagyobb, kockázatos, halasztva.
- ⬜ **NPC-s / parkour kaszt-próbapályák** — világtartalom/NPC, nem tisztán plugin-feladat.
- ⬜ **Intro kamera-utaztatás / spectator-útvonal** — Folia-n kockázatos kamera-vezérlés, halasztva.

---

> Minden fázis végén: `gradlew build` + gyors szerverteszt, majd a következő fázis.
