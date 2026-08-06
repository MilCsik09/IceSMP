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
  7. globális AFK product-boundary (jutalmazó zóna/scheduler/payout nem térhet vissza)

Kilépési kód: 0 = zöld (warningok lehetnek), 1 = legalább egy FAIL.
"""
import os
import re
import pathlib
import subprocess
import sys
import glob

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CFG = os.path.join(REPO, "src/main/resources/config")
JAVA = os.path.join(REPO, "src/main/java")
GUIDES = os.environ.get("ICESMP_GUIDES_DIR", "/home/user/IceSMPGuides")

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

# ---------- 2b. quest next-gráf: CIKLUS tilos ----------
# Egy önmagára (vagy körben) mutató, repeatable, nulla cooldownos, már teljesített REACH_LEVEL
# quest az accept -> complete -> reward -> advanceChain -> accept láncot végtelenszer futtatná:
# sokszoros jutalom, majd StackOverflowError. Futásidőben mélység-korlát fogja, de a ciklust
# MÁR ITT ki kell szűrni, mert a korlát csak tünetet kezel.
_next_edges = {qid: q.get("next") for qid, q in quests.items()
               if isinstance(q, dict) and q.get("next")}
_state = {}


def _find_cycle(node, path):
    _state[node] = 1
    nxt = _next_edges.get(node)
    if nxt in _next_edges or nxt in quests:
        if _state.get(nxt) == 1:
            return path + [nxt]
        if _state.get(nxt, 0) == 0 and nxt is not None:
            found = _find_cycle(nxt, path + [nxt])
            if found:
                return found
    _state[node] = 2
    return None


for _qid in list(_next_edges):
    if _state.get(_qid, 0) == 0:
        _cycle = _find_cycle(_qid, [_qid])
        if _cycle:
            fail("quests.yml next-gráf CIKLUS: " + " -> ".join(_cycle)
                 + " — a lánc végtelen jutalom-hurokba futhat")
            break

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
# A kulcs a legközelebbi megelőző típusdeklarációt is hordozza, különben beágyazott
# recordok azonos nevű accessorai hamis duplikátumként buknának el.
for path in glob.glob(os.path.join(JAVA, "**/*.java"), recursive=True):
    src = read(path)
    type_decls = [(m.start(), m.group(1))
                  for m in re.finditer(r"\b(?:class|record|interface|enum)\s+(\w+)", src)]
    seen = {}
    for m in re.finditer(r"(?:public|private|protected)[\w\s<>,\[\]]*?\s(\w+)\(([^)]*)\)\s*\{", src):
        name, params = m.group(1), m.group(2)
        types = tuple(t.split(".")[-1] for t in re.findall(r"(?:final\s+)?([\w.<>\[\]]+)\s+\w+\s*(?:,|$)", params))
        owner = ""
        for pos, type_name in type_decls:
            if pos >= m.start():
                break
            owner = type_name
        key = (owner, name, types)
        if key in seen:
            fail(f"duplikált metódus: {os.path.basename(path)}: {owner}.{name}({', '.join(types)}) kétszer definiálva")
        seen[key] = True

# ---------- 6. tükör-drift ----------
MIRROR = [
    ("README.md", "README.md"),
    ("ROADMAP.md", "ROADMAP.md"),
    ("docs/FEATURES.md", "docs/FEATURES.md"),
    ("docs/LATEST_CHANGES.md", "docs/LATEST_CHANGES.md"),
    ("docs/PLAYER_GUIDE.md", "docs/PLAYER_GUIDE.md"),
    ("docs/BUILDER_GUIDE.md", "docs/BUILDER_GUIDE.md"),
    ("docs/ADMIN_GUIDE.md", "docs/ADMIN_GUIDE.md"),
    ("docs/ARCHITECTURE.md", "docs/ARCHITECTURE.md"),
    ("docs/LORE.md", "docs/LORE.md"),
    ("docs/LORE_REFERENCE.md", "docs/LORE_REFERENCE.md"),
    ("docs/QUESTS.md", "docs/QUESTS.md"),
    ("docs/RESOURCE_PACK_CMD.md", "docs/RESOURCE_PACK_CMD.md"),
    ("docs/TEASER.md", "docs/TEASER.md"),
]
if os.path.isdir(GUIDES):
    for src_rel, dst_rel in MIRROR:
        a, b = os.path.join(REPO, src_rel), os.path.join(GUIDES, dst_rel)
        if not os.path.exists(b):
            warn(f"tükör: {dst_rel} hiányzik a Guides-ból")
        elif read(a) != read(b):
            warn(f"tükör-drift: {src_rel} != Guides/{dst_rel} — tükrözés kell")

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
# ===== check-consume elteres: meta-erzekeny levonas tipus-alapu ellenorzes mellett =====
# A removeItem(new ItemStack(material, amount)) isSimilar-t (tipus + META) egyeztet, mig a
# keszlet-ellenorzesek tipus szerint szamoltak. Emiatt a nevesitett/belyegzett/serult targy
# FEDEZTE a hozzavalot, de a levonas nem talalta meg — a hozzavalo ingyen maradt (szakma-craft
# ES ritualé-aldozat). A kozos szerzodes: hu.taliann.icesmp.utils.PlainIngredients.
try:
    for _jp in pathlib.Path(REPO, "src/main/java").rglob("*.java"):
        _src = _jp.read_text(encoding="utf-8", errors="ignore")
        if "removeItem(new ItemStack(" in _src:
            fail(f"check-consume elteres: {_jp.name} — removeItem(new ItemStack(...)) meta-erzekeny "
                 f"levonas; hasznald a PlainIngredients.consume(...)-t, hogy a szamolas es a "
                 f"fogyasztas UGYANAZT a predikatumot hasznalja")
except Exception as e:
    warn(f"check-consume ellenorzes kihagyva: {e}")

# ===== Listener-prioritas matrix: progressz NEM elozheti meg a vedelmet =====
# A Bukkit sorrend LOWEST -> LOW -> NORMAL -> HIGH -> HIGHEST -> MONITOR. A vedelmi listenerek
# HIGH/HIGHEST prioritason cancel-elnek, ezert egy NORMAL prioritasu progressz-handler MAR
# konyvelt, mire a vedelem visszavonta az akciot (tiltott tores is adott XP-t/questet). Az
# ignoreCancelled=true csak a KORABBI cancel ellen ved. A megfigyelo progressz-handlereknek
# ezert MONITOR prioritason kell futniuk.
_PROGRESS_LISTENERS = ["QuestProgressListener", "DailyQuestListener", "ProfessionXpListener",
                       "ServerChallengeListener", "GatheringBuffListener"]
_CANCELLABLE = {"BlockBreakEvent", "BlockPlaceEvent", "CraftItemEvent", "PlayerFishEvent",
                "EntityPickupItemEvent", "PlayerHarvestBlockEvent", "SmithItemEvent",
                "EnchantItemEvent", "InventoryClickEvent", "PlayerItemConsumeEvent"}
try:
    for _name in _PROGRESS_LISTENERS:
        _lp = pathlib.Path(REPO, "src/main/java/hu/taliann/icesmp/listeners", _name + ".java")
        if not _lp.is_file():
            warn(f"listener-prioritas: {_name}.java nem talalhato")
            continue
        _lines = _lp.read_text(encoding="utf-8").split("\n")
        for _i, _l in enumerate(_lines):
            if "@EventHandler" not in _l or "MONITOR" in _l:
                continue
            _j = _i + 1
            while _j < len(_lines) and not _lines[_j].strip():
                _j += 1
            _sig = _lines[_j] if _j < len(_lines) else ""
            _m = re.search(r"final\s+([A-Za-z]+Event)\s+event", _sig)
            if _m and _m.group(1) in _CANCELLABLE:
                fail(f"listener-prioritas: {_name}.java:{_i + 1} — {_m.group(1)} handler NEM MONITOR "
                     f"prioritason fut, igy a vedelem (HIGH/HIGHEST) cancel-je ELOTT konyvel "
                     f"(tiltott akcio is jutalmazna)")
except Exception as e:
    warn(f"listener-prioritas ellenorzes kihagyva: {e}")

# ===== YAML 1.1 boolean-kulcs csapda =====
# Az idezojel NELKULI on/off/yes/no kulcsot a YAML 1.1 logikai ertekke alakitja, ezert a kod
# sosem talalja meg a sort (nemán az inline defaultra esik vissza). Ez a hibaosztaly szemre
# tokeletes YAML-ban is elrejtozik — gepi orre van szukseg.
try:
    import yaml as _yaml
    _yml_files = (sorted(pathlib.Path(REPO, "src/main/resources/messages").glob("*.yml"))
                  + sorted(pathlib.Path(REPO, "src/main/resources/config").glob("*.yml"))
                  + [pathlib.Path(REPO, "src/main/resources/messages.yml")])
    for _yf in _yml_files:
        if not _yf.exists():
            continue
        _data = _yaml.safe_load(_yf.read_text(encoding="utf-8")) or {}

        def _boolkeys(node, path=""):
            if isinstance(node, dict):
                for _k, _v in node.items():
                    if isinstance(_k, bool):
                        yield f"{path}.{_k}" if path else str(_k)
                    yield from _boolkeys(_v, f"{path}.{_k}" if path else str(_k))

        for _bad in _boolkeys(_data):
            fail(f"YAML boolean-kulcs csapda: {_yf.name} -> '{_bad}' — a forrasban idezojel nelkuli "
                 f"on/off/yes/no allt, a YAML logikai ertekke alakitotta, igy a kod SOSEM talalja meg "
                 f"(tedd idezojelbe: 'on': / 'off':)")
except Exception as e:
    warn(f"YAML boolean-kulcs ellenorzes kihagyva: {e}")

# ===== Advancement-drift: Java-lista <-> jar-datapack <-> valódi grant-pont =====
# Négy dolognak kell egyeznie, különben néma funkció-veszteség lesz:
#  1) minden AdvancementService NODES-id-hez legyen datapack-JSON (különben nem jelenik meg),
#  2) minden datapack-JSON legyen a NODES-ban vagy toast (különben árva fájl a jarban),
#  3) minden NODES-id-hez legyen VALÓDI award()-hívás (a "nincs holt bejegyzés" szabály),
#  4) a JSON TARTALMA is egyezzen a NODES-sal (cím/leírás/ikon/szülő/rejtettség) — egy
#     Java-oldali átírás e nélkül némán elavult JSON-t hagyna a jarban.
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
    # tartalom-drift: a generator sajat --check modja mondja meg, naprakesz-e minden JSON
    _gen = subprocess.run([sys.executable, str(pathlib.Path(REPO) / "scripts/gen_advancements.py"), "--check"],
                          capture_output=True, text=True)
    if _gen.returncode != 0:
        fail("advancement-JSON tartalom-drift — " + (_gen.stdout or _gen.stderr).strip().replace("\n", "; "))
except Exception as e:
    warn(f"advancement-drift ellenorzes kihagyva: {e}")

# ===== Meret- es tartalom-szamok: a doksi szamai a KODBOL szarmazzanak =====
# A "471 Java-fajl / 87 manager / 37 tabortuz-mese" tipusu allitasok kezzel karbantartottak
# voltak, ezert minden korben driftelnek (a valosag 473/87/150 volt, mire ez a guard megszuletett).
# A ~-os allitasoknak sav jar, a pontos daraszamoknak egzakt egyezes.
try:
    _java_files = list((pathlib.Path(REPO) / "src/main/java").rglob("*.java"))
    _measured = {
        "java-fajl": len(_java_files),
        "manager": len([p for p in _java_files if p.name.endswith("Manager.java")]),
    }

    _stores = None
    _core = read(os.path.join(REPO, "src/main/java/hu/taliann/icesmp/core/IceSMPCore.java"))
    _sm = re.search(r"persistentStores\s*=\s*List\.of\((.*?)\);", _core, re.S)
    if not _sm:
        fail("IceSMPCore: a persistentStores List.of(...) bekötés nem értelmezhető")
    else:
        _store_entries = [x.strip() for x in re.split(r",", _sm.group(1)) if x.strip()]
        _stores = len(_store_entries)
        if "devItemManager" not in _store_entries:
            fail("IceSMPCore: a devItemManager kimaradt a persistentStores lifecycle-listából")

    # (fajl, regex, mert ertek, tolerancia szazalekban) — 0 tolerancia = egzakt
    _CLAIMS = [
        ("CLAUDE.md", r"~?(\d+)\s*Java-fájl", _measured["java-fajl"], 3),
        ("CLAUDE.md", r"(\d+)\s*manager", _measured["manager"], 0),
        ("docs/ARCHITECTURE.md", r"(\d+) Java-fájl", _measured["java-fajl"], 3),
        ("docs/ARCHITECTURE.md", r"(\d+) `\*Manager` osztály", _measured["manager"], 0),
        ("docs/ARCHITECTURE.md", r"a (\d+) fájlt-író store", _stores, 0),
    ]
    for _path, _pattern, _real, _tol in _CLAIMS:
        if _real is None:
            warn(f"szam-guard: {_path} / {_pattern} — a mert ertek nem allt elo, kihagyva")
            continue
        _full = os.path.join(REPO, _path)
        if not os.path.exists(_full):
            continue
        _hit = re.search(_pattern, read(_full))
        if not _hit:
            warn(f"szam-guard: {_path} — a '{_pattern}' allitas eltunt; ha szandekos, vedd ki a guardbol")
            continue
        _claimed = int(_hit.group(1))
        _limit = _real * _tol / 100.0
        if abs(_claimed - _real) > _limit:
            fail(f"szam-drift: {_path} {_claimed}-et allit, a mert ertek {_real} "
                 f"(tolerancia: ±{_tol}% = {_limit:.0f}) — a doksi szama a kodbol szarmazzon")
except Exception as e:
    warn(f"szam-drift ellenorzes kihagyva: {e}")

# ===== Spell-feloldas provenancia: minden grant nevezze meg a forrasat =====
# Forras nelkul a spec-reset nem tudta visszavenni a sajat spelljeit (a specek hatarlan
# halmozhatoak lettek), a talent-visszavonas pedig elvitte a kaszt-szintbol IS jaro spellt.
# Source nélküli unlockSpell nincs támogatva — minden hívó explicit provenance-t adjon.
try:
    def _top_level_args(text, open_index):
        """Argumentumok a nyito zarojeltol a hozza tartozo CSUKOTIG (beagyazott hivasokkal)."""
        depth, buf, args = 0, "", []
        for index in range(open_index, len(text)):
            char = text[index]
            if char in "([":
                depth += 1
                if depth == 1:
                    continue
            elif char in ")]":
                depth -= 1
                if depth == 0:
                    args.append(buf)
                    return args
            if char == "," and depth == 1:
                args.append(buf)
                buf = ""
            else:
                buf += char
        return args

    for _jp in pathlib.Path(JAVA).rglob("*.java"):
        if _jp.name == "JobManager.java":
            continue  # az implementáció belső hívásait nem vizsgáljuk call-site guardként
        _src = _jp.read_text(encoding="utf-8", errors="ignore")
        for _match in re.finditer(r"unlockSpell\(", _src):
            _args = _top_level_args(_src, _match.end() - 1)
            if len([a for a in _args if a.strip()]) < 3:
                fail(f"spell-provenancia: {_jp.name} — unlockSpell(...) forras nelkul; adj meg "
                     f"SOURCE_BASE/SOURCE_ADMIN vagy SPEC:/TALENT:/QUEST: prefixet, kulonben a "
                     f"reset nem tudja forrasonkent visszavonni")
except Exception as e:
    warn(f"spell-provenancia ellenorzes kihagyva: {e}")

# ===== Frakcio-elhagyas: a hozzarendelest NEM szabad torolni =====
# A torolt bejegyzest a kovetkezo /faction join „elso valasztasnak" latta, ezert a leave+join
# paros megkerulte a semleges-fovaros kaput, a szezon-hajra zarat es a valtas-cooldownt.
try:
    for _jp in pathlib.Path(JAVA).rglob("*.java"):
        if _jp.name in ("FactionManager.java",):
            continue
        _src = _jp.read_text(encoding="utf-8", errors="ignore")
        if re.search(r"\.removeFaction\(", _src):
            fail(f"frakcio-allapot: {_jp.name} — removeFaction(...) torli a hozzarendelest; "
                 f"a kilepes EXPLICIT setFaction(..., FactionType.NEUTRAL) legyen")
except Exception as e:
    warn(f"frakcio-allapot ellenorzes kihagyva: {e}")

# ===== Kez-kiuritesre kovetkezo addItem: a targy visszakerulhet UGYANABBA a slotba =====
# Tele hataizsaknal az addItem egyetlen ures helye eppen az imenti kiuritett aktiv hotbar-slot,
# ezert a fegyver-tilalom uzenetet kapott jatekos kezeben MARADT a fegyver.
try:
    for _jp in pathlib.Path(JAVA).rglob("*.java"):
        _lines = _jp.read_text(encoding="utf-8", errors="ignore").split("\n")
        for _i, _line in enumerate(_lines):
            if not re.search(r"setItemIn(Main|Off)Hand\(\s*null\s*\)", _line):
                continue
            _window = "\n".join(_lines[_i:_i + 4])
            if ".addItem(" in _window:
                fail(f"slot-visszaeses: {_jp.name}:{_i + 1} — kez-kiuritest kozvetlenul addItem "
                     f"kovet; a targy tele inventorynal ugyanoda kerulhet vissza. Hasznalj "
                     f"kijelolt, nem aktiv storage slotot (setItem(index, ...))")
except Exception as e:
    warn(f"slot-visszaeses ellenorzes kihagyva: {e}")

# ===== Transient-entitas liveness: aki kerdez, annak regisztralnia is kell =====
# A TransientEntities.isAlive FAIL-CLOSED: ismeretlen id = halott. Ha egy manager kerdezi a
# liveness-t, de a spawn-utjan nem hiv register()-t, az entitas MAR a kovetkezo tickben
# halottnak latszik — az esemeny (karavan, Vad Hajsza, Idegen) azonnal lezarul, a mobok pedig
# gazdatlanul a vilagban maradnak.
try:
    _alive_files, _register_files = set(), set()
    for _jp in pathlib.Path(JAVA).rglob("*.java"):
        _src = _jp.read_text(encoding="utf-8", errors="ignore")
        if "TransientEntities.isAlive" in _src:
            _alive_files.add(_jp.name)
        if "TransientEntities.register" in _src:
            _register_files.add(_jp.name)
    for _name in sorted(_alive_files - _register_files):
        fail(f"transient-liveness: {_name} — TransientEntities.isAlive(...)-ot hiv, de sehol nem "
             f"regisztral (TransientEntities.register). A liveness fail-closed, ezert a sajat "
             f"entitasa azonnal halottnak latszik")
except Exception as e:
    warn(f"transient-liveness ellenorzes kihagyva: {e}")

# ===== Vagyon-definicio: EGYETLEN forras =====
# A ranglista/bard/kronika a DEFAULT valutat olvasta, az elerések az osszeget — ugyanaz a
# jatekos mas vagyont mutatott a ket helyen (RED/BLUE/DARK egyenleg be sem szamitott).
try:
    for _jp in pathlib.Path(JAVA).rglob("*.java"):
        if _jp.name == "CurrencyManager.java":
            continue
        _src = _jp.read_text(encoding="utf-8", errors="ignore")
        if re.search(r"getBalances\([^)]*\)\s*\.values\(\)\s*\.stream\(\)", _src, re.S):
            fail(f"vagyon-definicio: {_jp.name} — kezi valuta-osszegzes; hasznald a "
                 f"CurrencyManager.getTotalBalance(player)-t, hogy minden fogyaszto UGYANAZT "
                 f"a vagyont lassa")
except Exception as e:
    warn(f"vagyon-definicio ellenorzes kihagyva: {e}")

# ===== ARCHITECTURE.md csomagterkep: a fajlszamok a fajlrendszerbol jonnek =====
# A tabla evekig kezzel kovette a kodot, ezert minden sora elmaradt (managers 62 vs 108,
# utils 3 vs 20). Ez a guard a tabla ELSO szamat a csomag tenyleges .java-szamahoz meri.
try:
    _arch = read(os.path.join(REPO, "docs/ARCHITECTURE.md"))
    for _pkg, _claim in re.findall(r"^\|\s*`([a-z]+)/`\s*\|\s*(\d+)", _arch, re.M):
        _dir = pathlib.Path(JAVA, "hu/taliann/icesmp", _pkg)
        if not _dir.is_dir():
            fail(f"csomagterkep: `{_pkg}/` szerepel az ARCHITECTURE.md tablajaban, de nincs ilyen csomag")
            continue
        _real = len(list(_dir.rglob("*.java")))
        if int(_claim) != _real:
            fail(f"csomagterkep-drift: ARCHITECTURE.md `{_pkg}/` {_claim} fajlt allit, "
                 f"a valosag {_real}")
except Exception as e:
    warn(f"csomagterkep ellenorzes kihagyva: {e}")

# ===== /lore: minden temanak legyen tartalma, tab-complete-je ES usage-sora =====
# A `radicora` tema letezett a normalize() aliasai kozott, de nem volt sajat szocikke: csendben
# a `menedek` altalanos frakcio-osszefoglalojara iranyitott. A harom lista egyutt mozogjon.
try:
    _lore_src = read(os.path.join(JAVA, "hu/taliann/icesmp/commands/LoreCommand.java"))
    _topics_block = re.search(r"TOPICS\s*=\s*List\.of\((.*?)\);", _lore_src, re.S)
    _topics = set(re.findall(r'"([a-z0-9-]+)"', _topics_block.group(1))) if _topics_block else set()
    _entries = set(re.findall(r'Map\.entry\(\s*"([a-z0-9-]+)"', _lore_src))
    # Az olvasható, több soros súgó minden témát külön `/lore tema` alakban sorol.
    # A korábbi egyetlen `<a|b|c>` lista továbbra is támogatott a régi branchekhez.
    _usage_topics = set(re.findall(r"/lore ([a-z0-9-]+)", _lore_src))
    _legacy_usage = re.search(r"/lore <([a-z0-9|-]+)>", _lore_src)
    if _legacy_usage:
        _usage_topics.update(_legacy_usage.group(1).split("|"))
    for _t in sorted(_topics - _entries):
        fail(f"/lore tema '{_t}' szerepel a TOPICS-ban, de nincs DEFAULTS-szocikke")
    for _t in sorted(_entries - _topics):
        fail(f"/lore szocikk '{_t}' letezik, de nincs a TOPICS tab-complete listaban")
    for _t in sorted(_entries - _usage_topics):
        fail(f"/lore szocikk '{_t}' nincs benne a lore-usage sorban (a jatekos nem tud rola)")
    # alias-cel: minden normalize()-cel legyen valodi szocikk
    for _target in set(re.findall(r'->\s*"([a-z0-9-]+)";', _lore_src)):
        if _target not in _entries:
            fail(f"/lore alias '{_target}'-ra mutat, de nincs ilyen szocikk — a parancs csendben mast adna")
except Exception as e:
    warn(f"/lore tema-ellenorzes kihagyva: {e}")


# ===== AFK product boundary: global tracking only, no rewarded zones =====
try:
    _afk = configs.get("afk.yml", {}) or {}
    _afk_root = _afk.get("afk", {}) if isinstance(_afk, dict) else {}
    if not isinstance(_afk_root, dict):
        fail("AFK product-boundary: config/afk.yml afk gyökere nem mapping")
        _afk_root = {}
    for _removed_key in ("enabled", "refresh-ticks", "zones", "reward", "bossbar"):
        if _removed_key in _afk_root:
            fail(f"AFK product-boundary: tiltott afk.{_removed_key} kulcs visszatért")
    if "afk-after-seconds" not in _afk_root or "block-rewards" not in _afk_root:
        fail("AFK product-boundary: a globális timeout vagy jutalomkapu configja hiányzik")

    _afk_messages_doc = yaml.safe_load(read(os.path.join(
        REPO, "src/main/resources/messages/afk.yml"))) or {}
    _afk_messages = (_afk_messages_doc.get("messages", {})
                     if isinstance(_afk_messages_doc, dict) else {})
    for _required_message in ("afk-on", "afk-off", "afk-reward-blocked"):
        if _required_message not in _afk_messages:
            fail(f"AFK product-boundary: {_required_message} üzenet hiányzik")
    for _removed_message in ("afk-zone-enter", "afk-zone-leave", "afk-reward-received"):
        if _removed_message in _afk_messages:
            fail(f"AFK product-boundary: tiltott {_removed_message} üzenet visszatért")

    _afk_manager_source = read(os.path.join(
        JAVA, "hu/taliann/icesmp/managers/AfkManager.java"))
    _core_source = read(os.path.join(
        JAVA, "hu/taliann/icesmp/core/IceSMPCore.java"))
    for _token in ("CurrencyManager", "payOutTokens", "BossBar", "currentZone",
                   "zoneProgress", "bossBars", "record Zone"):
        if _token in _afk_manager_source:
            fail(f"AFK product-boundary: tiltott runtime token az AfkManagerben: {_token}")
    for _token in ("afkTask", "afkManager.tick()", "\"afk.refresh-ticks\""):
        if _token in _core_source:
            fail(f"AFK product-boundary: tiltott scheduler token az IceSMPCore-ban: {_token}")
except Exception as e:
    fail(f"AFK product-boundary ellenőrzés hibája: {e}")

for w in warns:
    print(f"⚠ WARN: {w}")
for f_ in fails:
    print(f"✗ FAIL: {f_}")
print(f"\nÖsszegzés: {len(fails)} FAIL, {len(warns)} WARN "
      f"({len(quests)} quest, {len(used_models)} item-model, {len(used_perms)} jog-node, "
      f"{len(known_commands)} parancsnév ellenőrizve)")
sys.exit(1 if fails else 0)
