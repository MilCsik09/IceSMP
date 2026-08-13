# Season 0 / Prologue — Olethropyla, a Kárhozat Kapuja

Ez a dokumentum a Season 0 szerveroldali működésének, live-ops kezelésének,
world-builder kötéseinek és recovery szabályainak technikai kézikönyve.

A lore autoritása továbbra is a [LORE.md](LORE.md), a fejlesztői megfeleltetés
a [LORE_REFERENCE.md](LORE_REFERENCE.md), a játékosoldali szabályok pedig a
[PLAYER_GUIDE.md](PLAYER_GUIDE.md) fájlban találhatók.

## 1. Kánon és scope

Olethropyla, a **Kárhozat Kapuja** nem a Season 0-ban jön létre. A kapu a
Hetedik Vérháború óta létezik. A Prologue története arról szól, hogy a már
létező kapu instabillá válik, áttörések történnek körülötte, majd a játékosok
a Kárhozat Éjszakáján stabilizálják és ténylegesen használhatóvá teszik.

A Season 0 nem fedheti fel a Néma Királynő vagy az Első Csend valódi
magyarázatát. Ezek későbbi történeti scope-ok.

A Prologue nem normál `SeasonManager`-szezon `seasonNumber=0` értékkel. Saját,
tartós világ-authorityt használ. A normál Season 1 csak a Prologue sikeres,
idempotens lezárása után kap friss kezdési timestampet és tiszta league
állapotot.

## 2. Prologue lifecycle

A világ-authority fő állapotai:

`DORMANT -> UNSTABLE -> BREACHING -> FINALE -> GATE_OPEN -> COMPLETED`

A kapu vizuális/esemény-eszkalációja külön stage-ként fut:

`SILENCE -> CRACKS -> LEAK -> COLLAPSE`

A stage és a kapustabilitás tartós állapot. A bundled konfiguráció jelenleg:

- SILENCE: 92% stabilitás, 72 óra;
- CRACKS: 78%, 72 óra;
- LEAK: 43%, 48 óra;
- COLLAPSE: 17%, kézi/finale lezárásig gyakorlatilag tartós.

Az automatikus stage-advance configból kikapcsolható. Live módosításkor a
persistált state és a config nem keverendő össze: a config default/policy, a
`prologue.yml` pedig az aktuális világállapot.

## 3. Season 0 progression- és content-policy

A bundled Season 0 alapértékek:

- class level cap: **25**;
- specialization: zárva;
- normál relic acquisition: zárva;
- blueprint acquisition: zárva;
- engedélyezett item-raritások: `ocska`, `kozonseges`, `nem_mindennapi`, `ritka`;
- world boss, Wild Hunt és treasure event high-power útvonalak runtime overlayből tiltva.

A class XP minden normál IceSMP jutalomútja a központi policyt kapja. A cap
nem csak a level-upot blokkolja: az XP a 25. szint kezdeti küszöbén clampelődik,
így Season 0 alatt nem lehet előre bankolni a 26. szintet.

A Prologue lezárása után opcionális catch-up élhet. Bundled érték:

- target level: 25;
- multiplier: 1.75.

Ez kizárólag a lemaradók felzárkózását szolgálja; nem Season 0 power reward.

A normál relic/debug admin útvonalak megmaradhatnak staging és recovery célra,
de játékosoldali legitim megszerzés Season 0-ban zárt.

## 4. Nether és End policy

Season 0 alatt a Nether játékosok számára zárt. A normál FIRE-alapú
Nether-portál létrehozása továbbra is blokkolt.

A Prologue finálé után pontosan egy legitim vanilla `NETHER_PORTAL` utazási
hely létezik: **Olethropyla**. A játékos csak a konfigurált `prologue-gate`
anchor közeléből használhat Nether-portált. A bundled travel radius 24 blokk.

Az admin `icesmp.admin.territory.bypass` explicit bypassként használható
staging/debug célra.

A Netherből Overworld felé történő visszaút engedett; az Overworldből Nether
felé vezető út kapu-authorityt igényel. Más játékos által épített vagy korábban
ott maradt portal frame nem válhat legitim shortcut-tá.

Az End Season 0 lezárása után sem nyílik meg automatikusan. Az End portal
frame és END_PORTAL policy külön világkapu, változatlanul zárt marad.

## 5. World-builder contract

A repository szándékosan **nem tartalmaz kitalált production koordinátákat**.
A végleges staging világon négy event-spawnpoint hookot kell felvenni:

- `prologue-gate` — a tényleges Olethropyla kapu és travel authority közepe;
- `prologue-gathering` — a finálé gyülekező/briefing tere;
- `prologue-breach` — az áttörés hullámainak spawn- és harctere;
- `prologue-boss` — A Hasadék Őre boss encounterének horgonya.

A Prologue runtime ezeket `points` anchor módban használja. A hookokat az
IceSMP meglévő event-spawnpoint eszközeivel kell felvenni; ne hardcode-olj
koordinátát Java forrásba.

Builder acceptance minimum:

1. mind a négy pont ugyanazon intended world buildhez tartozik;
2. a gathering pont nem teleportcsapda és legalább a tervezett tömeget elbírja;
3. a breach és boss körül nincs víz, void, claim/region ütközés vagy szűk
   geometria, amely az encountert beszorítja;
4. a `prologue-gate` körül a tényleges vanilla portal frame és a vizuális kapu
   egyértelműen ugyanahhoz a helyhez tartozik;
5. 24 blokkon kívüli Overworld Nether-portal használat játékosként tiltott;
6. restart, WorldEdit/paste és világmásolat után minden hook újraellenőrzött;
7. a stabilitás HUD és az ambient effekt 96 blokk környékén vizuálisan
   ellenőrzött.

## 6. Breach encounterek

A Prologue egy közös encounter engine-t használ a random/admin breach-ekhez
és a finálé hullámaihoz.

Severityk:

- `MINOR` — bundled base count 4;
- `MAJOR` — bundled base count 7;
- `CRITICAL` — bundled base count 10.

A résztvevőszám 5 és 45 közé clampelődik. A mob count, boss HP és más skálák
a központi `PrologueScaling` szerint nőnek, konfigurált maximumokkal.

A Prologue mobok transient, nem-persistent entitások. PDC markerrel és
encounter ID-val rendelkeznek, lootot és XP-t nem dobhatnak, így nem nyitnak
Season 0 loot-ceiling kerülőutat.

Cleanup kizárólag a közös transient-entity scheduler-handle életcikluson fut;
globális `Bukkit.getEntity(UUID)` lookup nem használható.

## 7. Kárhozat Éjszakája — finale

A production finale fő checkpointjai:

`PREPARING -> GATHERING -> BREACH_1 -> BREACH_2 -> ELITE_WAVE -> BOSS_INTRO -> BOSS_FIGHT -> FALSE_END -> GATE_AWAKENING -> EPILOGUE -> COMPLETED`

Bundled minimum résztvevőszám: 5. A gathering nem teleportálja erővel a
játékosokat; a csapatnak fizikailag kell megérkeznie.

A finálé alatt a gate-arénán belül átmeneti PvP ceasefire él. Ez kizárólag a
finale-context idejére érvényes és nem írja át tartósan a territoryt.

A boss alapértelmezett neve **A Hasadék Őre**, entity típusa
`WITHER_SKELETON`. Bundled base HP 500 és base attack damage 9. A participant
scaling és a phase mechanikák az encounter engine-ben futnak.

A boss halála után szándékos false ending következik, majd a Gate awakening.
A kapu nem nyílhat meg a boss-victory tartós commitja előtt.

## 8. Production és rehearsal

`/prologue finale start` production finálét indít.

`/prologue finale start --rehearsal` ugyanazt az encounter-, boss-, HUD-,
scheduler- és presentation útvonalat használja, de nem ír production completion
állapotot:

- nincs tartós Gate unlock;
- nincs Founder/finale reward commit;
- nincs Chronicle/monument commit;
- nincs Season 1 transition.

A rehearsal célja, hogy a builderek és eventesek a tényleges production
útvonalat próbálják, ne egy külön, gyengébb mockot.

## 9. Pause / resume semantics

Production `finale pause` valódi eseményszünet:

- az orchestrator nem lép tovább;
- az aktív encounter mobok AI-ja megáll és combatjuk blokkolt;
- pending spawnok nem futhatnak át a pause-on;
- boss phase mechanikák nem haladhatnak;
- az encounter timeout nem fogy;
- a pause idő nem számít bele a phase age-be.

A pause állapot, a phase és a hátralévő encounter timeout restart után is
helyreállítható. `resume` ugyanabból a tartós checkpointból folytat.

A rehearsal nem kap külön durable pause/restart persistence frameworköt; a
production recovery-authority a lényeg.

## 10. Crash safety és irreverzibilis sorrend

A finálé irreverzibilis lánca:

1. boss victory;
2. Gate unlock;
3. reward plan létrehozás és Profile v2 reward commit;
4. rendkívüli Chronicle;
5. Prologue monument;
6. Season 1 prepare/activate;
7. Prologue `COMPLETED`.

A boss completion callback in-memory spawn latch-et állít, mielőtt az encounter
újra spawnolhatónak számítana. A durable victory a `finaleId`-hoz kötött és
idempotens.

Ha a boss meghalt, de a victory persistence hibázik, a rendszer fail-closed:

- második boss nem spawnolhat;
- a Gate nem nyílhat ki;
- reward/Chronicle/monument/Season 1 nem léphet tovább.

Normál recovery:

1. állítsd meg a live event műveleteket;
2. mentsd a konzollogot és a plugin Prologue state fájljait;
3. javítsd a filesystem/persistence hibát;
4. ellenőrizd `/prologue status` kimenetét;
5. használd `/prologue finale resume` parancsot;
6. ellenőrizd, hogy ugyanaz a `finaleId` folytatódik és nincs második boss.

A `/prologue gate open --force` **nem normál recovery**. Ez veszélyes admin
override: a kaput úgy nyitja ki, hogy a Prologue ettől még nem válik lezárttá.
Csak dokumentált tulajdonosi/üzemeltetői döntéssel, staging bizonyítékkal
használd.

## 11. Reward és részvétel

A Season 0 jutalmai prestige/cosmetic státuszok; nem adnak power előnyt.

A PlayerProfile v2 authority két fő flaget használ:

- `prologue_founder`;
- `prologue_finale_participant`.

A finale eligibility presence és combat contribution alapján számolható.
Bundled minimumok: 45 másodperc jelenlét, 1.0 event damage vagy 1.0 boss damage.

Az eligible UUID-k a Prologue világstate-ben is tartósan megmaradnak. Ha a
játékos Profile v2 profilja a commit pillanatában nincs cache-ben/online, a
jutalom a következő Profile-v2-ready joinkor idempotensen replayelhető.

Nincs Season 0 power item, pénz vagy relic jutalom.

## 12. Chronicle és monument

A sikeres production finale egy extraordinary Chronicle bejegyzést publikál,
one-shot receipttel. A szöveg spoiler-safe: Olethropyla megnyílását rögzítheti,
de nem fedheti fel az Első Csend vagy a Néma Királynő valódi magyarázatát.

A Prologue monument az egyszeri Season 0 / Első Expedíció történeti rekordot
ugyanabban a monument-projection rendszerben tárolja, mint a normál szezonok.
Nem cél 50 játékos nevének giant hologramként való megjelenítése.

## 13. Season 1 transition

A Prologue nem wipe-olja a játékosprofilt. A fairness-t a Season 0
progression/loot ceiling adja.

A Prologue lezárása után a Season 1:

- `season.number = 1`;
- friss, tartósan receiptezett start timestampet kap;
- tiszta normál season/community league state-ből indul;
- a Season 0 normál season driftje nem vihető át;
- a PlayerProfile v2 tartós karakterállapot megmarad;
- opcionális catch-up segítheti a később érkező játékosokat.

A Season 1 timestamp külön kritikus transition receiptből származik, így
crash replaykor nem változhat meg minden restarttal.

## 14. Admin parancsok

Permission: `icesmp.admin.prologue`.

```text
/prologue status
/prologue stage <SILENCE|CRACKS|LEAK|COLLAPSE>
/prologue stability <0-100>
/prologue breach start [MINOR|MAJOR|CRITICAL]
/prologue finale start [--rehearsal]
/prologue finale pause
/prologue finale resume
/prologue finale abort
/prologue gate open --force
```

A stage/stability parancs live-ops mutáció, ezért production használatnál
rögzítsd az okot és az előtte/utána státuszt. A force-open különösen magas
kockázatú override.

## 15. Staging acceptance

A Prologue release előtt legalább az alábbi kézi próbák szükségesek:

- [ ] PRO-01 — mind a négy builder hook feloldódik és biztonságos;
- [ ] PRO-02 — Season 0 XP cap minden ismert XP forrással és admin SET/ADD úton;
- [ ] PRO-03 — spec/relic/blueprint/high-tier loot tiltások játékosként, admin debug külön;
- [ ] PRO-04 — random MINOR/MAJOR/CRITICAL breach, 5/10/25/45 játékosnak megfelelő scaling;
- [ ] PRO-05 — rehearsal teljes hullám- és bossútja production side-effect nélkül;
- [ ] PRO-06 — production finale gathering/minimum-player/ceasefire;
- [ ] PRO-07 — pause/resume minden fontos phase-ben;
- [ ] PRO-08 — pause közbeni teljes restart és ugyanazon checkpointból resume;
- [ ] PRO-09 — boss-death és victory-persistence közti fault-injection: nincs duplicate boss vagy Gate unlock;
- [ ] PRO-10 — boss victory -> Gate -> reward -> Chronicle -> monument -> Season 1 sorrend;
- [ ] PRO-11 — Nether portal policy a gate-en belül/kívül, admin bypass és Netherből visszaút;
- [ ] PRO-12 — End továbbra is zárt;
- [ ] PRO-13 — offline eligible participant következő joinkor pontosan egyszer kap prestige státuszt;
- [ ] PRO-14 — 50 körüli online játékossal participant tracking/HUD/encounter terhelési próba.

Minden futásnál rögzítsd a pontos commit SHA-t, JAR SHA-256-ot, config snapshotot,
konzollogot és a várt/kapott eredményt.

## 16. Automatizált bizonyíték

A Prologue source-contract és pure-math regresszió:

`src/regression/java/hu/taliann/icesmp/prologue/PrologueRegressionSuite.java`

Gradle task:

```text
./gradlew prologueRegressionTest
```

A task a normál `check` verification graph része. A teljes merge gate továbbra
is Java 21 clean build/check + repository consistency/docs gates + productionközeli
Folia staging acceptance.
