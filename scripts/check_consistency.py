#!/usr/bin/env python3
"""Gépi drift-ellenőrző — push előtt futtatandó (a fordítás-ellenőrzés mellett).

Azokat a konzisztencia-osztályokat ellenőrzi, amiket kézzel könnyű elfelejteni:
  1. minden config-YAML parse-olható
  2. quest-hivatkozások épek (next/requires-quest/crate-key/requires-faction/rotáció)
  3. ITEM_MODEL: minden deklarált modell-id szerepel a docs/RESOURCE_PACK_CMD.md manifestben
  4. jogosultság-node-ok: minden kódban használt icesmp.admin.* regisztrálva van a
     Permissions.java-ban (FAIL — az icesmp.admin.all csak a regisztrált node-okat adja meg)
  5. /menu akció-célok (RUN:/OPEN:) létező parancsra mutatnak
  6. tükör-repo drift (ha a IceSMPGuides checkout elérhető)

Kilépési kód: 0 = zöld (warningok lehetnek), 1 = legalább egy FAIL.
"""
import os
import re
import pathlib
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

# ---------- 3. ITEM_MODEL manifest-lefedettség + legacy drift-védelem ----------
manifest = read(os.path.join(REPO, "docs/RESOURCE_PACK_CMD.md"))
manifest_models = set(re.findall(r"^### `([a-z0-9_]+)`", manifest, re.M)) | set(re.findall(r"\| `([a-z0-9_]+)` \|", manifest))
used_models = {}
for name, path in [(os.path.basename(p), p) for p in glob.glob(os.path.join(CFG, "*.yml"))]:
    for m in re.finditer(r"(?:key-)?item-model:\s*[\"']?([^\"'\s#}]+)", read(path)):
        value = m.group(1)
        model = value.split(":", 1)[1] if value.startswith("icesmp:") else value
        if re.fullmatch(r"[a-z0-9_]+", model):
            used_models.setdefault(model, set()).add(name)
for path in glob.glob(os.path.join(JAVA, "**/*.java"), recursive=True):
    src = read(path)
    for m in re.finditer(r"applyItemModel\([^;]*?\"icesmp:([a-z0-9_]+)\"", src, re.S):
        model = m.group(1)
        if not model.endswith("_"):
            used_models.setdefault(model, set()).add(os.path.basename(path))
for model, places in sorted(used_models.items()):
    if model not in manifest_models:
        fail(f"ITEM_MODEL '{model}' használatban ({sorted(places)[0]}), de hiányzik a docs/RESOURCE_PACK_CMD.md manifestből")

# A teljes migráció után numerikus CustomModelData nem kerülhet vissza.
for path in glob.glob(os.path.join(JAVA, "**/*.java"), recursive=True):
    src = read(path)
    if re.search(r"\.setCustomModelData(Component)?\s*\(", src):
        fail(f"{os.path.basename(path)}: setCustomModelData(...) — MIGRÁLVA, ITEM_MODEL-t használj "
             f"(ItemDataFactory.applyItemModel)")
for path in glob.glob(os.path.join(CFG, "*.yml")):
    if re.search(r"(?:key-)?custom-model-data:\s*\d", read(path)):
        fail(f"{os.path.basename(path)}: custom-model-data — MIGRÁLVA, item-model kulcsot használj "
             f'(item-model: "icesmp:<id>")')

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
# ===== Advancement-drift: Java-lista <-> jar-datapack <-> valódi grant-pont =====
# Három dolognak kell egyeznie, különben néma funkció-veszteség lesz:
#  1) minden AdvancementService NODES-id-hez legyen datapack-JSON (különben nem jelenik meg),
#  2) minden datapack-JSON legyen a NODES-ban vagy toast (különben árva fájl a jarban),
#  3) minden NODES-id-hez legyen VALÓDI award()-hívás (a "nincs holt bejegyzés" szabály).
try:
    _svc = (pathlib.Path(REPO) / "src/main/java/hu/taliann/icesmp/managers/AdvancementService.java").read_text(encoding="utf-8")
    _node_ids = set(re.findall(r'new Node\("([a-z_]+)"', _svc))
    _adv_dir = pathlib.Path(REPO) / "src/main/resources/datapack/data/icesmp/advancement"
    _files = {p_.stem for p_ in _adv_dir.glob("*.json")} if _adv_dir.is_dir() else set()
    _toasts = {f_ for f_ in _files if f_.startswith("toast_")}
    _tree_files = _files - _toasts
    if not _files:
        fail("a jar-datapack advancement-könyvtára üres vagy hiányzik "
             "(src/main/resources/datapack/data/icesmp/advancement)")
    for _missing in sorted(_node_ids - _tree_files):
        fail(f"advancement '{_missing}' szerepel az AdvancementService NODES-ban, de NINCS "
             f"datapack-JSON-ja — a bejegyzes nem jelenik meg a haladas-fulon")
    for _orphan in sorted(_tree_files - _node_ids):
        fail(f"advancement-JSON '{_orphan}.json' arva: nincs hozza NODES-bejegyzes")
    # grant-pontok: az egesz forrasfaban keressuk az award("<id>") hivasokat
    _granted = set()
    for _j in (pathlib.Path(REPO) / "src/main/java").rglob("*.java"):
        _granted |= set(re.findall(r'award\(\s*[A-Za-z_][\w.]*\s*,\s*"([a-z_]+)"\s*\)',
                                   _j.read_text(encoding="utf-8", errors="ignore")))
    for _dead in sorted(_node_ids - _granted):
        fail(f"advancement '{_dead}' HOLT bejegyzes: nincs hozza AdvancementService.award() hivas")
    # a toast-bejegyzesek a ToastUtil Kind enumjabol jonnek
    _toast_src = (pathlib.Path(REPO) / "src/main/java/hu/taliann/icesmp/utils/ToastUtil.java").read_text(encoding="utf-8")
    _kinds = set(re.findall(r'"(toast_[a-z_]+)"', _toast_src))
    for _missing in sorted(_kinds - _toasts):
        fail(f"toast-advancement '{_missing}' a ToastUtil Kind enumjaban van, de nincs datapack-JSON-ja")
    for _orphan in sorted(_toasts - _kinds):
        warn(f"toast-advancement JSON '{_orphan}.json' nincs hasznalatban a ToastUtil Kind enumjaban")
except Exception as e:
    warn(f"advancement-drift ellenorzes kihagyva: {e}")

for f_ in fails:
    print(f"✗ FAIL: {f_}")
print(f"\nÖsszegzés: {len(fails)} FAIL, {len(warns)} WARN "
      f"({len(quests)} quest, {len(used_models)} item-model, {len(used_perms)} jog-node, "
      f"{len(known_commands)} parancsnév ellenőrizve)")
sys.exit(1 if fails else 0)
