#!/usr/bin/env python3
"""Gépi drift-ellenőrző — push előtt futtatandó (a fordítás-ellenőrzés mellett).

Azokat a konzisztencia-osztályokat ellenőrzi, amiket kézzel könnyű elfelejteni:
  1. minden config-YAML parse-olható
  2. quest-hivatkozások épek (next/requires-quest/crate-key/requires-faction/rotáció)
  3. CustomModelData: minden használt CMD szerepel a docs/RESOURCE_PACK_CMD.md regiszterben
  4. jogosultság-node-ok: minden kódban használt icesmp.admin.* regisztrálva van a
     Permissions.java-ban (FAIL — az icesmp.admin.all csak a regisztrált node-okat adja meg)
  5. /menu akció-célok (RUN:/OPEN:) létező parancsra mutatnak
  6. tükör-repo drift (ha a IceSMPGuides checkout elérhető)

Kilépési kód: 0 = zöld (warningok lehetnek), 1 = legalább egy FAIL.
"""
import os
import re
import sys
import glob

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CFG = os.path.join(REPO, "src/main/resources/config")
JAVA = os.path.join(REPO, "src/main/java")
GUIDES = "/home/user/IceSMPGuides"

fails = []
warns = []


def fail(msg):
    fails.append(msg)


def warn(msg):
    warns.append(msg)


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


# ---------- 1. YAML parse ----------
try:
    import yaml
except ImportError:
    print("HIBA: pyyaml nem elérhető — a script nem tud futni.")
    sys.exit(1)

configs = {}
for path in sorted(glob.glob(os.path.join(CFG, "*.yml"))):
    name = os.path.basename(path)
    try:
        configs[name] = yaml.safe_load(read(path)) or {}
    except yaml.YAMLError as e:
        fail(f"YAML parse-hiba: {name}: {str(e).splitlines()[0]}")

# ---------- 2. quest-integritás ----------
qroot = configs.get("quests.yml", {})
quests = qroot.get("quests", {}) if isinstance(qroot, dict) else {}
crates = (configs.get("crates.yml", {}) or {}).get("crates", {}) or {}
FACTIONS = {"RED", "BLUE", "NEUTRAL", "DARK"}
for qid, q in quests.items():
    if not isinstance(q, dict):
        continue
    for field in ("next", "requires-quest"):
        ref = q.get(field)
        if ref and ref not in quests:
            fail(f"quests.yml {qid}: {field}: '{ref}' nem létező quest-id")
    fac = q.get("requires-faction")
    if fac and fac not in FACTIONS:
        fail(f"quests.yml {qid}: requires-faction '{fac}' érvénytelen")
    ck = (q.get("rewards") or {}).get("crate-key")
    if ck:
        crate_id = str(ck).split(":")[0]
        if crate_id not in crates:
            fail(f"quests.yml {qid}: crate-key láda-id '{crate_id}' nincs a crates.yml-ben")
    if q.get("rotation-group") and not q.get("repeatable"):
        warn(f"quests.yml {qid}: rotation-group tag repeatable nélkül")

# ---------- 3. CMD-regiszter lefedettség ----------
register = read(os.path.join(REPO, "docs/RESOURCE_PACK_CMD.md"))
registered_cmds = set(int(m) for m in re.findall(r"\b([1-9]\d{3})\b", register))
used = {}  # cmd -> [hol]
for name, path in [(os.path.basename(p), p) for p in glob.glob(os.path.join(CFG, "*.yml"))]:
    for m in re.finditer(r"(?:key-)?custom-model-data:\s*(\d+)", read(path)):
        used.setdefault(int(m.group(1)), []).append(name)
for path in glob.glob(os.path.join(JAVA, "**/*.java"), recursive=True):
    src = read(path)
    for m in re.finditer(r"(?:setCustomModelData|customModelData)\(\s*(\d{4})\s*\)", src):
        used.setdefault(int(m.group(1)), []).append(os.path.basename(path))
for cmd, places in sorted(used.items()):
    if cmd not in registered_cmds:
        fail(f"CMD {cmd} használatban ({places[0]}), de HIÁNYZIK a docs/RESOURCE_PACK_CMD.md regiszterből")

# ---------- 4. jogosultság-node-ok ----------
perm_src = read(os.path.join(JAVA, "hu/taliann/icesmp/core/Permissions.java"))
canonical = set(re.findall(r'"(icesmp\.[a-z.]+)"', perm_src))
used_perms = set()
for path in glob.glob(os.path.join(JAVA, "**/*.java"), recursive=True):
    if path.endswith("Permissions.java"):
        continue
    used_perms.update(re.findall(r'"(icesmp\.admin\.[a-z.]+)"', read(path)))
for node in sorted(used_perms - canonical):
    fail(f"jog-node '{node}' használatban, de nincs a Permissions.java-ban regisztrálva "
         f"(az icesmp.admin.all nem adja meg!)")

# ---------- 5. /menu akció-célok ----------
core_src = read(os.path.join(JAVA, "hu/taliann/icesmp/core/IceSMPCore.java"))
known_commands = set()
for m in re.finditer(r'registerCommand\(\s*"([a-z]+)"[^;]*?List\.of\(([^)]*)\)', core_src, re.S):
    known_commands.add(m.group(1))
    known_commands.update(re.findall(r'"([a-z]+)"', m.group(2)))
menus_path = os.path.join(JAVA, "hu/taliann/icesmp/gui/CommandMenus.java")
if os.path.exists(menus_path):
    for m in re.finditer(r'"(?:RUN|OPEN):([a-z]+)', read(menus_path)):
        if m.group(1) not in known_commands:
            fail(f"CommandMenus: RUN/OPEN cél '{m.group(1)}' nem regisztrált parancs")

# ---------- 5b. duplikált metódus-szignatúrák (a sandbox-javac elnyeli!) ----------
for path in glob.glob(os.path.join(JAVA, "**/*.java"), recursive=True):
    seen = {}
    for m in re.finditer(r"(?:public|private|protected)[\w\s<>,\[\]]*?\s(\w+)\(([^)]*)\)\s*\{", read(path)):
        name, params = m.group(1), m.group(2)
        types = tuple(t.split(".")[-1] for t in re.findall(r"(?:final\s+)?([\w.<>\[\]]+)\s+\w+\s*(?:,|$)", params))
        key = (name, types)
        if key in seen:
            fail(f"duplikált metódus: {os.path.basename(path)}: {name}({', '.join(types)}) kétszer definiálva")
        seen[key] = True

# ---------- 6. tükör-drift ----------
MIRROR = [
    ("PLAYTEST.md", "PLAYTEST.md"),
    ("docs/RESOURCE_PACK_CMD.md", "RESOURCE_PACK_CMD.md"),
    ("docs/EPITESZ_UTMUTATO.md", "EPITESZ_UTMUTATO.md"),
    ("docs/TEASER.md", "TEASER.md"),
    ("docs/PITCH.md", "PITCH.md"),
    ("docs/FEATURES.md", "FEATURES.md"),
    ("docs/LORE.md", "lore/LORE.md"),
    ("docs/LORE_REFERENCE.md", "lore/LORE_REFERENCE.md"),
]
if os.path.isdir(GUIDES):
    for src_rel, dst_rel in MIRROR:
        a, b = os.path.join(REPO, src_rel), os.path.join(GUIDES, dst_rel)
        if not os.path.exists(b):
            warn(f"tükör: {dst_rel} hiányzik a Guides-ból")
        else:
            # A Guides-oldali példányban a player-guide linkek gyökér-relatívak.
            if read(a).replace("player-guide/", "") != read(b).replace("player-guide/", ""):
                warn(f"tükör-drift: {src_rel} != Guides/{dst_rel} — tükrözés kell")
    ideas_a = set(os.path.basename(p) for p in glob.glob(os.path.join(REPO, "docs/ideas/*.md")))
    ideas_b = set(os.path.basename(p) for p in glob.glob(os.path.join(GUIDES, "ideas/*.md")))
    ideas_b.discard("README.md")  # az a docs/IDEAS.md tükre, nem ideas-fájl
    for extra in sorted(ideas_a ^ ideas_b):
        warn(f"tükör: ideas/{extra} csak az egyik repóban létezik")

# ---------- 7. recept-hozzávaló szint-sorrend ----------
# Egy recept nem nyílhat korábban, mint amikor a unique hozzávalója termelhetővé válik.
try:
    _rdata = yaml.safe_load(read(os.path.join(REPO, "src/main/resources/config/profession-recipes.yml")))
    _recipes = []
    def _walk_recipes(d, path=()):
        if isinstance(d, dict):
            for k, v in d.items():
                _walk_recipes(v, path + (k,))
            if "result" in d and "ingredients" in d:
                _recipes.append((path, d))
    _walk_recipes(_rdata)
    _produced = {}
    for _p, _r in _recipes:
        _uid = (_r.get("result") or {}).get("unique")
        if _uid:
            _lvl = _r.get("level", 1)
            if _uid not in _produced or _lvl < _produced[_uid][1]:
                _produced[_uid] = (_p[-1], _lvl)
    for _p, _r in _recipes:
        _lvl = _r.get("level", 1)
        for _ing in _r.get("ingredients") or []:
            _s = str(_ing)
            if _s.startswith("unique:"):
                _uid = _s.split(":")[1]
                if _uid in _produced and _produced[_uid][1] > _lvl:
                    fail(f"recept '{_p[-1]}' (L{_lvl}) korábban nyílik, mint a hozzávalója "
                         f"'{_uid}' (forrás: {_produced[_uid][0]} L{_produced[_uid][1]})")
except Exception as e:
    warn(f"recept-szint ellenőrzés kihagyva: {e}")

# ---------- eredmény ----------
for w in warns:
    print(f"⚠ WARN: {w}")
for f_ in fails:
    print(f"✗ FAIL: {f_}")
print(f"\nÖsszegzés: {len(fails)} FAIL, {len(warns)} WARN "
      f"({len(quests)} quest, {len(used)} CMD, {len(used_perms)} jog-node, "
      f"{len(known_commands)} parancsnév ellenőrizve)")
sys.exit(1 if fails else 0)
