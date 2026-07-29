from pathlib import Path
import re

END = r'>>>>>>> [^\n]*\n'
END_NO_NL = r'>>>>>>> [^\n]*'

def sub_file(path: str, pattern: str, replacement: str, *, count: int = 0, flags: int = re.S) -> None:
    p = Path(path)
    s = p.read_text()
    new, n = re.subn(pattern, replacement, s, count=count, flags=flags)
    if n == 0:
        raise SystemExit(f'Expected merge conflict pattern missing in {path}: {pattern[:80]}')
    p.write_text(new)

sub_file('CHANGELOG.md',
         r'<<<<<<< HEAD\n\| `CrazyCrates`.*?' + END,
         '''| `CrazyCrates` | **FELTÉTELES** — a natív crate lifecycle code-review-zott és regressziózott; valódi Folia/fault-injection átvételi teszt még kell |\n| `AxAFKZone` / `AxAPI` | **NEM KELL** — a jutalmazó AFK-zóna termékscope törölve; a meglévő globális `/afk` változatlanul megmarad |\n| `GSit` | **FELTÉTELES** — a natív sit-only lifecycle buildelt és regressziózott; valódi Folia seat/cleanup átvételi playtest még kell |\n''')
sub_file('CHANGELOG.md',
         r'<<<<<<< HEAD\n- Az új config-fájlok.*?' + END,
         '- Az új config-fájlok (pl. `crates.yml`, `dev-items.yml`, `motd.yml`, `sit.yml`, `tablist.yml`) a jarból\n')

playtest = '''  - [ ] **Globális AFK:** az automatikus AFK-észlelés, `/afk`, tablistajelzés és meglévő\n        exploitvédelmi lifecycle változatlanul működik. Jutalmazó AFK-zóna nincs és nem készül;\n        AxAFKZone/AxAPI jar nem része a deploymentnek.\n  - [ ] **Crate-rendszer** (CrazyCrates helyett): a code-review-zott lifecycle buildelt és regressziózott,\n        de a jar eltávolítása csak valódi Folia/fault-injection átvételi teszt után engedhető. Ellenőrizd:\n        - main-hand működik, off-hand és gyors dupla katt nem indít második openinget;\n        - required key több inventory stackből pontosan fogy, partial mass-open csak teljesen finanszírozott nyitásokat indít;\n        - full inventorynál az item reward a játékos owner-szálán a helyére esik;\n        - currency write/rollback hiba, command submit exception/null/rejection, `dispatchCommand == false` és command exception nem kap hamis completed/success állapotot;\n        - két gyors reload, crate/world/definition cseréje finalize előtt, valamint quit/kick/disable minden lifecycle ponton;\n        - spin/reveal entity és GUI cleanup, konkurens auditrotáció, stats-reset race;\n        - restart recovery: egyszeri key refund vagy dokumentált `MANUAL_REVIEW`, jutalomduplikáció nélkül.\n  - [ ] **Sit-only ülés** (GSit helyett): `/sit`, `/sit fel`, click-to-sit, üres kéz,\n        stairs/slabs/carpets/moss carpet/pale moss carpet/snow pozíció, világ- és materialpolicy,\n        unsafe/folyadék/clearance, namespaced tiltott parancs, konkurens reservation és minden\n        damage/sneak/break/teleport/world-change/death/quit/kick/dismount/reload/disable cleanup.\n        Lay, crawl, stacking és player/NPC sitting nincs a termékscope-ban.\n'''
sub_file('PLAYTEST.md', r'<<<<<<< HEAD\n.*?' + END, playtest, count=1)

build_tasks = '''val sitRegressionTest by tasks.registering(JavaExec::class) {\n    group = "verification"\n    description = "Runs native sit-only policy, reservation and lifecycle regressions."\n    dependsOn(tasks.named(regressionTest.classesTaskName))\n    classpath = regressionTest.runtimeClasspath\n    mainClass.set("hu.taliann.icesmp.managers.SitRegressionSuite")\n}\n\nval crateRegressionTest by tasks.registering(JavaExec::class) {\n    group = "verification"\n    description = "Runs native crate validation, settlement, recovery and scheduler regressions."\n    dependsOn(tasks.named(regressionTest.classesTaskName))\n    classpath = regressionTest.runtimeClasspath\n    mainClass.set("hu.taliann.icesmp.crates.CrateRegressionSuite")\n}\n'''
sub_file('build.gradle.kts',
         r'<<<<<<< HEAD\nval sitRegressionTest.*?' + END + r'}\n',
         build_tasks)
sub_file('build.gradle.kts',
         r'<<<<<<< HEAD\n        motdRegressionTest, sitRegressionTest\)\n=======\n        motdRegressionTest, crateRegressionTest\)\n' + END_NO_NL,
         '        motdRegressionTest, sitRegressionTest, crateRegressionTest)')

server_rows = '''| **AxAFKZone (+AxAPI)** | meglévő globális AFK | **NEM KELL:** jutalmazó AFK-zóna scope törölve; nincs migráció |\n| **CrazyCrates** | `CrateManager` + strict config/policy, browser/preview, atomi settlement, recovery fence, audit és 7 rewardtípus | **FELTÉTELES:** zöld crate/full build után valódi Folia/fault-injection teszt kell (currency/command hiba, reload/disable, restart recovery, full inventory); process-crash exactly-once nincs állítva |\n| **GSit** | natív sit-only lifecycle | **FELTÉTELES:** valódi Folia seat/cleanup átvételi teszt után távolítható el |\n'''
sub_file('docs/SERVER_INTEGRATION.md', r'<<<<<<< HEAD\n.*?' + END, server_rows)

sub_file('src/main/java/hu/taliann/icesmp/core/IceSMPCore.java',
         r'<<<<<<< HEAD\n        shutdownStep\("sitManager", sitManager::shutdown\);\n=======\n        shutdownStep\("crateManager", crateManager::shutdown\);\n' + END_NO_NL,
         '        shutdownStep("sitManager", sitManager::shutdown);\n        shutdownStep("crateManager", crateManager::shutdown);')
sub_file('src/main/java/hu/taliann/icesmp/core/IceSMPCore.java',
         r'<<<<<<< HEAD\n            if \(key\.startsWith\("sit\."\)\) \{\n                sitManager\.reload\(\);\n=======\n            if \(key\.startsWith\("crates\."\)\) \{\n                crateManager\.reloadConfig\(\);\n' + END + r'            \}',
         '            if (key.startsWith("sit.")) {\n                sitManager.reload();\n            }\n            if (key.startsWith("crates.")) {\n                crateManager.reloadConfig();\n            }')

arch = Path('docs/ARCHITECTURE.md').read_text()
arch, n1 = re.subn(r'<<<<<<< HEAD\n\| `managers/`.*?' + END,
'''| `managers/` | __MANAGERS__ | Üzleti logika és állapot (gazdaság, frakciók, kasztok, szakmák, loot/raritás, recept-katalógus, pet, territórium-védelem, stb.). |\n| `listeners/` | __LISTENERS__ | Bukkit eseménykezelők (gameplay + GUI-klikk + loot/craft/védelem). |\n''', arch, flags=re.S)
arch, n2 = re.subn(r'<<<<<<< HEAD\n- \*\*Méret:\*\*.*?' + END,
'''- **Méret:** __JAVA__ Java-fájl, ~85 000 sor; __MANAGER_CLASSES__ `*Manager` osztály (a `managers/` csomag __MANAGERS__ fájl).\n  Csomag-megoszlás: listeners __LISTENERS__, managers __MANAGERS__, commands __COMMANDS__, spells __SPELLS__, gui __GUI__, crates __CRATES__, utils __UTILS__, data __DATA__,\n''', arch, flags=re.S)
combined = '''\n## Natív sit-only lifecycle\n\nA `SitManager` egy Bukkit-független, atomi `SitState` ledgerben foglalja a world+block\nülőhelyet, majd PDC-azonosított, nem persistent ArmorStand seat entityt hoz létre a régió\ntulajdonos-szálán. A player/entity scheduler submit exception, null handle és retirement ugyanazon\n`PaperEntityTaskSubmission` single-winner fallbacken fut; a reload/disable cleanup rövid, korlátos\ndrainnel követi az entity eltávolításokat. A scope kizárólag `/sit`, `/sit fel` és click-to-sit: lay,\ncrawl, stacking és player/NPC sitting nincs runtime wiringban.\n\n## Natív crate settlement és recovery\n\nA `CrateManager` egy dependency-free domainrétegre épül. A `CrateOpeningLifecycle` CAS-alapú\n`RESERVED → PERSISTED → GRANTING → COMPLETED` állapotgépe biztosítja, hogy egy grant legfeljebb\negyszer legyen claimelhető, a finalize és rollback pedig kölcsönösen kizárja egymást. A stat/cooldown\nmutation token csak sikeres reward-settlement után kerül az autoritatív `CrateLedger` állapotba.\n\nA schema 2 recovery rekord `ROLLBACK_ONLY`, `REFUND_KEYS`, `REFUND_CLAIMED` és `MANUAL_REVIEW`\nállapotokkal teszi explicitté a kompenzációs határt. A currency batch durable save + exact snapshot\nrollback tokent használ; a command batch csak global-scheduler elfogadás, tényleges futás és sikeres\n`dispatchCommand` után tekinthető sikeresnek. Már nem kompenzálható külső side effect esetén nincs\nautomatikus key refund, hanem auditálható részleges hiba marad. Ez nem distributed transaction és\nnem process-crash exactly-once garancia.\n\nA config snapshot generationhöz kötött: a key purchase ugyanabból a generationből számít árat és\nkészít kulcsot, opening finalize előtt pedig újraellenőrzi a world/location/crate-ID/definition/policy\ninvariánsokat. Audit append és rotáció egyetlen sorosított writeren fut; a scheduler task/rejection\nsingle-winner gate-et és race-biztos task lease-t használ. Részletes szerződés: [`CRATES.md`](CRATES.md).\n'''
arch, n3 = re.subn(r'<<<<<<< HEAD\n\n## Natív sit-only lifecycle.*?' + END_NO_NL, combined, arch, flags=re.S)
if (n1, n2, n3) != (1, 1, 1):
    raise SystemExit(f'Architecture conflict match counts: {(n1,n2,n3)}')

from glob import glob
counts = {
    '__JAVA__': len(glob('src/main/java/**/*.java', recursive=True)),
    '__MANAGER_CLASSES__': len(glob('src/main/java/**/*Manager.java', recursive=True)),
    '__MANAGERS__': len(glob('src/main/java/hu/taliann/icesmp/managers/*.java')),
    '__LISTENERS__': len(glob('src/main/java/hu/taliann/icesmp/listeners/*.java')),
    '__COMMANDS__': len(glob('src/main/java/hu/taliann/icesmp/commands/**/*.java', recursive=True)),
    '__SPELLS__': len(glob('src/main/java/hu/taliann/icesmp/spells/*.java')),
    '__GUI__': len(glob('src/main/java/hu/taliann/icesmp/gui/*.java')),
    '__CRATES__': len(glob('src/main/java/hu/taliann/icesmp/crates/*.java')),
    '__UTILS__': len(glob('src/main/java/hu/taliann/icesmp/utils/*.java')),
    '__DATA__': len(glob('src/main/java/hu/taliann/icesmp/data/*.java')),
}
for key, value in counts.items():
    arch = arch.replace(key, str(value))
Path('docs/ARCHITECTURE.md').write_text(arch.rstrip() + '\n')

Path('docs/CRATE_REVIEW_VERIFICATION.md').write_text('''# Native crate and sit integration verification\n\n## Immutable integration state\n\n- Integration target before crate merge: `2432e031a1f63fa4d77bcc5ac25245de597b2675`\n- Target includes merged sit-only PR #47.\n- Reviewed crate branch before integration: `11fcaffa2903e24d275c9466c2fdff52b90ce1fb`\n- Common pre-feature target: `2dede0eee5513a9102be6d22140d572aa7ee1513`\n\nThe integration uses a real three-way merge. It preserves the sit-only runtime and the reviewed crate\nsettlement/recovery implementation while keeping the target CI workflow unchanged.\n\n## Verified checks\n\nThe integrated source passed locally with the retained Gradle cache:\n\n- `./gradlew crateRegressionTest sitRegressionTest --offline --no-daemon --stacktrace`;\n- `./gradlew clean build --offline --no-daemon --stacktrace --no-configuration-cache`;\n- crate, sit, moderation, MOTD, persistence and DEV regressions through Gradle `check`;\n- `python3 scripts/check_consistency.py` with `0 FAIL / 0 WARN`;\n- `git diff --check`.\n\nA normal remote CI run is required on the published integration commit before PR #49 is merged.\n\n## Guarantee boundary\n\nThis proves repository-level lifecycle, validation, scheduler, cleanup and recovery invariants. It\ndoes not claim a distributed transaction or process-crash exactly-once guarantee. CrazyCrates and\nGSit remain conditionally removable only after their documented real Folia and fault-injection\nplaytests succeed.\n''')

for path in Path('.').rglob('*'):
    if path.is_file() and '.git' not in path.parts:
        text = path.read_text(errors='ignore')
        if '<<<<<<< ' in text or '\n=======\n' in text or '>>>>>>> ' in text:
            raise SystemExit(f'Unresolved merge marker in {path}')
