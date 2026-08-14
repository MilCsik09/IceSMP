# Season 0 / Prologue — Olethropyla, a Kárhozat Kapuja

## Kánon és termékhatár

A Prologue nem külön normál szezon és nem Season 1 rövidített változata. Olethropyla nem a Prologue alatt keletkezik: a Hetedik Vérháború óta áll a Jégmezők és a Vérszavanna közötti Senkiföldjén. A Season 0 a Felsők korában bekövetkező új destabilizálódását, majd a Kárhozat Éjszakájának sikeres lezárása után a stabil átjáróvá válását kezeli.

A Prologue nem magyarázza meg az Első Csend természetét, és a Néma Királynő nem fináléboss. Az End továbbra sem Season 0/Season 1 tartalom; a meglévő owner-policy szerint a későbbi Season 2 admin-eseményig zárva marad.

## Játékosnak látható alapelvek

- A karakter, kaszt, legitim tárgyak, achievementek és cosmetic státuszok Season 1-re megmaradnak; nincs wipe.
- Season 0-ban a kasztszint alapértelmezett plafonja 25. A plafon fölött XP nem bankolódik.
- A kaszt-specializáció, a normál relikvia-progression, blueprint drop és felső gear/affix raritások alapból zártak.
- A „Zarándoklat a Kapuhoz” teljesíthető: a Kapuhoz el lehet jutni, csak az átkelés zárt.
- A saját Nether-portál továbbra sem legitim út. Season 0-ban az Overworld → Nether travel zárt; a finálé után kizárólag Olethropyla builder által kijelölt központi kapuja nyit utat. Nether → Overworld visszatérés megmarad.
- A Kapu stabilitása a környéken, kapcsolódó event alatt, illetve az összeomlási szakaszban hangsúlyosan jelenik meg.
- Season 1-től az új/lemaradó karaktereknél az alap catch-up ×1,75 25-ös kasztszintig; 25-nél automatikusan megszűnik.

## Prologue authority

A tartós világállapotot `PrologueManager` kezeli. A normál `SeasonManager` Season 1+ authority marad; Prologue alatt a normál liga, community goal drift és `SeasonFinaleManager` runtime-gate mögött áll.

A tartós állapotgép:

`DORMANT → UNSTABLE → BREACHING → FINALE → GATE_OPEN → COMPLETED`

A Kapu eszkalációs szakaszai:

1. `SILENCE` — Hallgatás;
2. `CRACKS` — Repedések;
3. `LEAK` — Szivárgás;
4. `COLLAPSE` — Összeomlás.

A stage és a stabilitás külön tartós adat. Az idővonal automatikusan léptethető, de adminból explicit módosítható. A kritikus világállapot `prologue.yml` fájlban, atomic `YamlStore` írással él; persistence-hibánál az in-memory mutation visszagörget, ezért a transition fail-closed.

## Progression/content policy

A `PrologueContentPolicy` az authority a következő gate-ekre:

- kaszt-XP plafon;
- kaszt-specializáció;
- relikvia normál megszerzése;
- blueprint drop;
- felső rarity/loot ceiling;
- Nether traversal;
- Season 1 catch-up.

A class-XP cap és catch-up a közös `JobManager` Profile v2 mutation útján történik. A durable operation receipt replaye a már elfogadott mutation módját és értékét játssza vissza, így retry közben nem változik a kiosztott XP. A Profile v2 memory/spec unlock út külön ugyanazt a policyt ellenőrzi.

A Season 0 loot ceiling runtime-only config overlay. Nem írja át a live configfájlokat: a blueprint és boss drop felső forrásokat lenullázza, a nem engedélyezett raritás-súlyokat 0-ra állítja, valamint a konfigurált nagy power-event forrásokat kikapcsolja. `COMPLETED` után normál config reload állítja vissza a Season 1 értékeket.

## Gate Breach

A `PrologueEncounterEngine` egyetlen újrahasznosítható encounter motor:

- `MINOR`, `MAJOR`, `CRITICAL` severity;
- warning pulse, hang és particle;
- konfigurálható hullámok és elite;
- ténylegesen a Kapu körül jelen lévő játékosok alapján lineárisan, minimum/maximum korláttal skálázott mob count;
- meglévő `MajorEventGate`, `EventSpawnGuard` és `TransientEntities` integráció;
- Folia region/entity/player scheduler használat;
- Prologue event mobokból nincs vanilla power loot vagy XP.

A természetes breach csak a `LEAK`/`COLLAPSE` fázisban indulhat, és csak akkor, ha ténylegesen van játékos a Kapu környékén.

## Kárhozat Éjszakája

A finálé külön `PrologueFinaleManager`, nem a Season 1+ `SeasonFinaleManager` reskinje. A checkpointok:

`PREPARING → GATHERING → BREACH_1 → BREACH_2 → ELITE_WAVE → BOSS_INTRO → BOSS_FIGHT → FALSE_END → GATE_AWAKENING → EPILOGUE → COMPLETED`

A gathering szerver-wide kihirdetés után utazásra kéri a játékosokat, de nem teleportál. A scaling baseline a tényleges arena-participant snapshotból készül. A finálé kontextusa a Doom Gate környezetében átmeneti PvP ceasefire-t ad; a territory permanens PvP-policyje nem íródik át.

A default bossnév „A Hasadék Őre”, és csak konfigurálható helykitöltő lore-megnevezés. A boss determinisztikus: 65% fölött telegráfozott slam, 65–30% között addok és slam, 30% alatt enrage + arena hazard. A mechanikák participant-count alapján korlátozottan skálázódnak.

A boss halála után rövid false ending következik, majd Gate awakening. A Gate csak tartós `bossDefeated + finaleVictory` checkpoint után nyitható meg.

## Crash/restart recovery

A győzelmi flow sorrendje:

1. boss defeated;
2. finale victory committed;
3. Gate unlocked;
4. durable reward plan created;
5. Profile v2 reward delivery/replay;
6. chronicle/monument one-shot receipt;
7. tiszta Season 1 generation;
8. Prologue `COMPLETED`;
9. normál Season 1 lifecycle aktiválása.

Következmények:

- boss halála utáni, Gate unlock előtti crash esetén a boss nem spawnol újra;
- Gate unlock utáni crash esetén a Kapu nyitva marad és a reward flow folytatódik;
- részleges reward delivery után a Profile v2 achievement CAS/idempotencia és a durable participant reward-plan akadályozza a duplázást;
- offline jogosult a következő Profile v2-ready belépéskor kapja meg ugyanazt a grantot;
- a Season 1 start timestamp külön kritikus receiptben egyszer foglalódik le, ezért recovery közben nem driftel a plugin vagy Prologue indulási idejére.

A finálé wave közbeni restart az aktuális checkpoint hullámát újraindíthatja; előtte power reward nincs, ezért ez nem dupláz jutalmat. Boss fight restartkor csak nem legyőzött boss állhat vissza.

## Prestige jutalmak

A Prologue nem ad combat vagy gazdasági előnyt.

Profile v2 achievement/flag azonosítók:

- `prologue_founder` — Founder / Első Expedíció státusz;
- `prologue_finale_participant` — Kárhozat Éjszakája tényleges résztvevő.

A finale eligibility nem last-hit alapú: presence + finale damage vagy boss damage bizonyíték alapján készül. A thresholdok live configból állíthatók.

## Krónika és emlékmű

Sikeres production finálé után:

- egyszeri rendkívüli krónika készül Olethropyla megnyílásáról;
- a meglévő `SeasonMonumentManager` általánosított Prologue-bejegyzést kap: „Az Első Expedíció”, finálénév, dátum és participant count;
- nincs fake Season 0 frakciógyőztes.

## Season 1 transition

A transition új `season.yml` generationt készít `season.number = 1` és a tényleges indulási pillanatot rögzítő `season.start` értékkel. A `community-goals.yml` tiszta Season 1 generationnel indul. Csak a Gate unlock + durable reward plan után lehet `COMPLETED` állapotot commitolni; ezután oldódik fel a normál liga, community goal és normál season finale lifecycle.

A Kapu nyitva marad, de a saját Nether-portál policy nem változik. Az End zárva marad.

## Admin/live-ops

Permission: `icesmp.admin.prologue`.

- `/prologue status`
- `/prologue stage <SILENCE|CRACKS|LEAK|COLLAPSE>`
- `/prologue stability <0-100>`
- `/prologue breach start [MINOR|MAJOR|CRITICAL]`
- `/prologue finale start`
- `/prologue finale start --rehearsal`
- `/prologue finale pause`
- `/prologue finale resume`
- `/prologue finale abort`
- `/prologue gate open --force`

A `gate open --force` szándékosan explicit veszélyes override. A production state-mutationök a Prologue audit historyban maradnak.

A rehearsal ugyanazt a wave/scaling/boss/HUD/scheduler útvonalat használja, de nem commitol Gate unlockot, Founder/finale jutalmat, krónikát, monumentet vagy Season 1 transitiont.

## World-builder contract

A runtime nem tartalmaz autoritatív koordinátát. A meglévő event spawnpoint rendszerrel a következő event key-khez kell builder anchor:

- `prologue-gate` — a monumentális Kárhozat Kapuja és a legitim Overworld → Nether travel ellenőrzési pontja;
- `prologue-gathering` — rally/gathering tér; hiányában Gate anchor fallback;
- `prologue-breach` — breach hullámok központja; hiányában Gate anchor fallback;
- `prologue-boss` — boss-aréna központja; hiányában Gate anchor fallback.

A mapon szükséges továbbá:

- monumentális, évszázadok óta álló Olethropyla;
- finale arena és biztonságos perem;
- több breach spawn-pozícióra alkalmas járható terület;
- korrupciós dekoráció és jó vizuális sightline;
- Season 1 Nether-side arrival point/world-build összekötés.

A spawn anchorokhoz a meglévő `/events spawnpoint` admin workflow használható. A plugin nem talál ki koordinátát.

## Resource pack

A Prologue server-side alapja nem igényel kötelező új pack assetet. A stabilitás Adventure BossBar fallbackkel resource pack nélkül is érthető, a boss mechanikák vanilla particle/sound telegráfot használnak. Később opcionálisan adható dedikált stability ikon, Founder badge, finale achievement ikon vagy boss/corruption asset anélkül, hogy a gameplay authority ezekre támaszkodna.

## Szándékosan későbbi scope

- normál Season 1+ faction finale mechanikák a meglévő `SeasonFinaleManager` hatáskörében;
- teljes class relic roster/power progression;
- az End megnyitása a későbbi Season 2 owner-eventben;
- az Első Csend valódi természetének magyarázata;
- a Néma Királynő végjátékának felfedése;
- opcionális, pusztán vizuális Prologue resource-pack bővítés.
