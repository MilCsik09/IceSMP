#!/usr/bin/env python3
from __future__ import annotations
import pathlib, re
ROOT = pathlib.Path(__file__).resolve().parents[2]

def read(p): return (ROOT/p).read_text(encoding='utf-8')
def write(p,s):
    q=ROOT/p; q.parent.mkdir(parents=True,exist_ok=True); q.write_text(s,encoding='utf-8')
def once(p,old,new):
    s=read(p); c=s.count(old)
    if c!=1: raise RuntimeError(f'{p}: expected 1 occurrence, got {c}: {old[:120]!r}')
    write(p,s.replace(old,new,1))
def regex_once(p,pat,repl,flags=0):
    s=read(p); n,c=re.subn(pat,repl,s,count=1,flags=flags)
    if c!=1: raise RuntimeError(f'{p}: regex expected 1, got {c}: {pat}')
    write(p,n)

write('scripts/validate_gui_icons.py', r'''#!/usr/bin/env python3
from __future__ import annotations
import pathlib, re, sys
ROOT = pathlib.Path(__file__).resolve().parents[1]
MODEL_RE = re.compile(r'^(?:icesmp:)?([a-z0-9_]+)$')
used: dict[str,set[str]] = {}
for path in (ROOT/'src/main/resources/config').glob('*.yml'):
    text=path.read_text(encoding='utf-8')
    for match in re.finditer(r'(?:key-)?item-model:\s*["\']?([^"\'\s#}]+)', text):
        raw=match.group(1); parsed=MODEL_RE.match(raw)
        if parsed: used.setdefault(parsed.group(1),set()).add(str(path.relative_to(ROOT)))
for path in (ROOT/'src/main/java').rglob('*.java'):
    text=path.read_text(encoding='utf-8')
    for match in re.finditer(r'applyItemModel\([^;]*?"icesmp:([a-z0-9_]+)"', text, re.S):
        model=match.group(1)
        if not model.endswith('_'): used.setdefault(model,set()).add(str(path.relative_to(ROOT)))
manifest=(ROOT/'docs/RESOURCE_PACK_CMD.md').read_text(encoding='utf-8')
manifest_models=set(re.findall(r'^### `([a-z0-9_]+)`',manifest,re.M))|set(re.findall(r'\| `([a-z0-9_]+)` \|',manifest))
pack_models={p.stem for p in (ROOT/'resource-pack/assets/icesmp/items').glob('*.json')}
missing_manifest=sorted(set(used)-manifest_models)
missing_pack=sorted(set(used)-pack_models)
fallback='missing_icon'
if fallback not in pack_models:
    fallback='PAPER'
errors=[]
if missing_manifest: errors.append('missing manifest: '+', '.join(missing_manifest))
if missing_pack: errors.append('missing pack: '+', '.join(missing_pack))
print(f'GUI_ICON_COVERAGE used={len(used)} manifest={len(manifest_models)} pack={len(pack_models)} '
      f'missing_manifest={len(missing_manifest)} missing_pack={len(missing_pack)} fallback={fallback}')
if errors:
    print('\n'.join(errors),file=sys.stderr); raise SystemExit(1)
''')

p='build.gradle.kts'; s=read(p)
anchor='val runtimeHardeningRegressionTest by tasks.registering(JavaExec::class) {'
tasks=r'''val configGuiTransactionRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs staged save/cancel/reset and optimistic-concurrency config GUI regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.config.ConfigGuiTransactionRegressionSuite")
}

val configGuiCoverageRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Validates config schema ↔ GUI allowlist coverage, types, defaults and ranges."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.config.ConfigGuiCoverageRegressionSuite")
}

val professionRecipeAuditRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Validates deterministic profession recipes, semantic uniqueness and reload cleanup."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.professions.ProfessionRecipeAuditRegressionSuite")
}

'''
if s.count(anchor)!=1: raise RuntimeError('gradle task anchor mismatch')
s=s.replace(anchor,tasks+anchor,1)
old='runtimeBugfixRegressionTest, runtimeHardeningRegressionTest, eventSpawnSafetyRegressionTest,'
new='runtimeBugfixRegressionTest, runtimeHardeningRegressionTest, eventSpawnSafetyRegressionTest, configGuiTransactionRegressionTest, configGuiCoverageRegressionTest, professionRecipeAuditRegressionTest,'
if s.count(old)!=1: raise RuntimeError('gradle check dependency anchor mismatch')
s=s.replace(old,new,1)
write(p,s)

write('docs/CONFIG_GUI_COVERAGE.md', r'''# Config GUI coverage

The admin GUI is an explicit **runtime-safe allowlist**, not a blind renderer for every scalar in the content configs.
The build-time `configGuiCoverageRegressionTest` merges every supported YAML file and proves:

- every GUI path exists exactly once;
- GUI type and packaged default type agree;
- numeric defaults are inside the declared range;
- enum defaults are among the declared options;
- all scalar entries under `world-events.safety.*`, `moderation.vanish.*` and
  `territory.mob-rules.doom-gate.*` are exposed;
- every remaining scalar is intentionally file/command-only (content records, lore, rewards, item definitions,
  spell/quest tables or advanced startup tuning), rather than accidentally omitted.

The test prints the exact `total / displayed / intentionally_excluded / missing / stale / duplicate` counts on every build.
A new scalar under a mandatory runtime-admin prefix fails the build until a matching GUI component is added.

## Transaction semantics

Opening the menu captures the effective values, packaged defaults, config generation and SHA-256 fingerprint of `config.yml`.
Clicks only modify an in-memory per-admin session. **Save** performs one asynchronous batch write; **Cancel**, closing the
inventory or disconnecting writes nothing. Middle-click removes the override and restores the packaged default.
A second admin save or external file edit makes an older session stale; stale sessions are rejected without overwriting data.

Entries display whether their effect is live, applied by a reload hook, or requires a restart. In particular the faction-tax
scheduler toggle/interval is restart-required; event safety and vanish capabilities are live/reload-safe.
''')

write('docs/PROFESSION_RECIPE_ITEM_AUDIT.md', r'''# Profession recipe and item audit

| profession | recipe key | item | problem | previous behaviour | fixed behaviour | balance rationale | migration / compatibility |
|---|---|---|---|---|---|---|---|
| Fisher | `egyszeru_horgaszbot` / `kezdo_horgaszbot` | Fishing Rod | Exact semantic duplicate: `3×STICK + 2×STRING → FISHING_ROD` | Two progression records represented the same craft and could diverge by load order | `egyszeru_horgaszbot` is canonical; `kezdo_horgaszbot` and its recipe are removed | One unlock/cost path prevents fake progression depth and recipe ambiguity | Existing fishing rods remain vanilla-compatible; no item migration is required |
| All | `icesmp:prof_*` legacy masterworks | PDC-stamped masterwork tools/books | Reload/disable did not remove previously registered Bukkit keys | Disabled or removed recipes could remain craftable until restart; repeated registration could be rejected | Manager owns a deterministic key set, removes it before rebuild and on disable, then registers once | No duplicate registry entries or stale craft path | Already crafted items remain valid; only future crafting availability changes |
| All | Config catalog (438 before, 437 after) | All profession outputs | No early semantic collision validation | Similar/duplicate recipes were accepted silently | Sorted loading plus canonical input/output fingerprints fail fast on non-intentional duplicates | Intentional alternate outputs remain; exact same input+output is rejected | Config typo now blocks startup/reload rather than silently changing progression |
| All | Unique profession outputs | Resource-pack model | Item/model references were distributed across config and pack | Missing mappings were only found visually | Build validator checks every referenced ITEM_MODEL against the manifest and checked-in pack | Visual identity remains stable without changing public model IDs | No public model ID changed; vanilla `PAPER` is the explicit no-pack fallback |

The automated audit also verifies unique/custom ingredient parsing, profession gating source contracts, deterministic key order,
output model presence and that removed recipes cannot survive a reload through stale Bukkit registrations.
''')

p='docs/RESOURCE_PACK_CMD.md'; s=read(p)
append='''
## Automated GUI/item-model validation

`scripts/validate_gui_icons.py` scans Java and every supported config for namespaced ITEM_MODEL references, then requires each
reference to exist both in this manifest and in `resource-pack/assets/icesmp/items/`. Public model ids are not renumbered.
When the resource pack is absent, GUI code keeps its declared vanilla Material (the explicit fallback); the server never emits
an invented CustomModelData magic number. The profession recipe audit additionally requires every unique profession output to
resolve through `profession-materials.*.item-model`.
'''
if '## Automated GUI/item-model validation' not in s: s += append
write(p,s)

write('docs/RUNTIME_HARDENING_AUDIT.md', r'''# Runtime hardening audit — 2026-08-03

## Root causes and fixes

- **DARK territory daylight:** protection was behind mob-scaling early returns and only ran at spawn. It is now an independent,
  reversible capability reconciled on spawn, entity load, movement and teleport. Baseline vanilla flags are restored on exit;
  event/boss PDC protection takes precedence. Only daylight combustion is cancelled, never block/entity fire globally.
- **Vanish:** the visibility ledger prevented `hidePlayer` from being reissued after client retracking. Hide is now idempotently
  reasserted after join, teleport, world change and respawn. Invulnerability is not stored on the Player; damage immunity is an
  explicit event capability and is removed automatically when vanish is disabled.
- **Claim geometry:** membership and rendering independently used stored Y bounds and drew a 3D box. Both now consume one
  normalized, inclusive X–Z `ClaimFootprint`; legacy Y fields are persistence-only. Preview tasks are single-owner and cleaned on
  replacement/logout.
- **World events:** invasions spawned at the selected player and bosses used a hard-coded 24–40 block ring. A shared bounded
  guard now enforces all relevant players, world spawn, world border, territory/claim/region, loaded chunk, safe surface and
  concurrent-event reservations. No valid candidate means a logged, controlled abort — never a close fallback.
- **Recipes/config GUI/icons:** see `PROFESSION_RECIPE_ITEM_AUDIT.md`, `CONFIG_GUI_COVERAGE.md` and `RESOURCE_PACK_CMD.md`.

## Manual runtime checks still required

Paper/Folia integration tests cover pure geometry/policy and source contracts. A staging server should additionally verify packet-
level tablist/nametag behaviour with the production scoreboard plugin set, particle appearance over uneven terrain, WorldGuard
region integration, and a full resource-pack client join. These checks are visual/integration-specific and are not claimed as
fully automated.
''')

print('stage3 part 4b applied')
