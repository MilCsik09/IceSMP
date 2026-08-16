#!/usr/bin/env python3
"""Gépi drift-ellenőrző — push előtt futtatandó (a fordítás-ellenőrzés mellett).

Azokat a konzisztencia-osztályokat ellenőrzi, amiket kézzel könnyű elfelejteni:
  1. minden config-YAML parse-olható
  2. quest-hivatkozások épek (next/requires-quest/crate-key/requires-faction/rotáció)
  3. class/spec lefedettség (capstone-próbák, doctrine-horgok, mechanikai capstone-bekötés)
  4. ITEM_MODEL: minden deklarált modell-id szerepel a docs/RESOURCE_PACK_CMD.md manifestben
  5. jogosultság-node-ok: minden kódban használt icesmp.admin.* regisztrálva van a
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

# ---------- 1b. Mob/Encounter 2.0 authored catalog ----------
_mob_doc = configs.get("mob-templates.yml", {}) or {}
_mob_abilities = _mob_doc.get("mob-abilities", {}) or {}
_mob_loot = _mob_doc.get("mob-loot-profiles", {}) or {}
_mob_templates = _mob_doc.get("mob-templates", {}) or {}
_mob_ranks = {"NORMAL", "VETERAN", "ELITE", "CHAMPION", "MINIBOSS", "BOSS", "WORLD_BOSS"}
_mob_archetypes = {"BRUISER", "CHARGER", "SKIRMISHER", "RANGED", "ARTILLERY",
                   "DEFENDER", "SUPPORT", "HEALER", "SUMMONER", "ASSASSIN",
                   "CONTROLLER", "FLYING"}
_mob_ability_kinds = {"LUNGE", "GROUND_SLAM", "PROJECTILE_BURST", "SHIELD",
                      "HEAL_PULSE", "SUMMON"}
if not 4 <= len(_mob_abilities) <= 64:
    fail(f"mob-abilities katalógus mérete {len(_mob_abilities)}; elvárt 4-64")
if not 4 <= len(_mob_templates) <= 256:
    fail(f"mob-templates katalógus mérete {len(_mob_templates)}; elvárt 4-256")
_normalized_mob_ids = {}
_bestiary_ids = set()
for _aid, _ability in _mob_abilities.items():
    if not isinstance(_ability, dict) or str(_ability.get("kind", "")).upper() not in _mob_ability_kinds:
        fail(f"mob-ability '{_aid}' kind érvénytelen")
    if str(_ability.get("kind", "")).upper() in {"LUNGE", "GROUND_SLAM", "PROJECTILE_BURST", "SUMMON"} \
            and int(_ability.get("telegraph-ticks", 0)) < 10:
        fail(f"mob-ability '{_aid}' veszélyes, de nincs olvasható telegraph")
for _mid, _template in _mob_templates.items():
    _normalized = str(_mid).lower().replace("-", "_")
    if _normalized in _normalized_mob_ids:
        fail(f"MobTemplate normalizált duplicate: '{_normalized_mob_ids[_normalized]}' / '{_mid}'")
    _normalized_mob_ids[_normalized] = _mid
    if not isinstance(_template, dict):
        fail(f"MobTemplate '{_mid}' nem mapping")
        continue
    if str(_template.get("rank", "")).upper() not in _mob_ranks:
        fail(f"MobTemplate '{_mid}' rank érvénytelen")
    if str(_template.get("archetype", "")).upper() not in _mob_archetypes:
        fail(f"MobTemplate '{_mid}' archetype érvénytelen")
    for _ability_id in _template.get("abilities", []) or []:
        if _ability_id not in _mob_abilities:
            fail(f"MobTemplate '{_mid}' hiányzó abilityre hivatkozik: '{_ability_id}'")
    if _template.get("loot-profile") not in _mob_loot:
        fail(f"MobTemplate '{_mid}' loot profile érvénytelen: '{_template.get('loot-profile')}'")
    _bestiary = str(_template.get("bestiary-id", "")).strip().lower()
    if not _bestiary or _bestiary in _bestiary_ids:
        fail(f"MobTemplate '{_mid}' Bestiary ID hiányzik vagy duplicate: '{_bestiary}'")
    _bestiary_ids.add(_bestiary)
    if len(set(_template.get("affix-pool", []) or [])) > 7:
        fail(f"MobTemplate '{_mid}' affix pool túllépi a 7 canonical affixet")

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

# ---------- 2c. class/spec tartalmi lefedettség ----------
_spec_manager_path = os.path.join(JAVA, "hu/taliann/icesmp/managers/SpecializationManager.java")
_spec_manager = read(_spec_manager_path)
_trial_pairs = dict(re.findall(r'Map\.entry\("([a-z0-9_]+)",\s*"([a-z0-9_]+)"\)',
                               _spec_manager))
for _trial_id, _spec_id in sorted(_trial_pairs.items()):
    _trial = quests.get(_trial_id)
    if not isinstance(_trial, dict):
        fail(f"capstone-próba hiányzik: {_trial_id} ({_spec_id})")
        continue
    if _trial.get("category") != "SPECIALIZATION":
        fail(f"{_trial_id}: category SPECIALIZATION kell")
    if _trial.get("requires-specialization") != _spec_id:
        fail(f"{_trial_id}: requires-specialization '{_spec_id}' kell")
    if int(_trial.get("requires-level", 0) or 0) < 50:
        fail(f"{_trial_id}: requires-level legalább 50 kell")
    _objective = _trial.get("objective") or {}
    if _objective.get("type") != "CAST_SPELLS" or not _objective.get("spells"):
        fail(f"{_trial_id}: nem üres CAST_SPELLS objective kell")

_service_specs = {
    "Warrior": {"berserker", "guardian"},
    "Evoker": {"devastation", "preservation"},
    "Archer": {"sharpshooter", "beast_master"},
    "Shaman": {"elemental", "enhancement", "tidal"},
    "Monk": {"windwalker", "brewmaster", "mistweaver"},
    "Paladin": {"holy", "retribution", "protection"},
    "DemonHunter": {"havoc", "vengeance"},
    "Druid": {"feral", "lunar", "ironbark", "restoration"},
    "Priest": {"discipline", "bone_priest", "shadow"},
    "DeathKnight": {"blood", "frost", "unholy"},
    "Assassin": {"poisoner", "phantom", "plaguebringer"},
    "Warlock": {"affliction", "destruction", "demonologist"},
    "Wizard": {"elementalist", "necromancer"},
}
_config_key_for_service = {
    "DeathKnight": "death_knight",
    "DemonHunter": "demon_hunter",
}
_service_for_spec = {
    spec: os.path.join(JAVA, f"hu/taliann/icesmp/{name.lower()}/{name}GameplayService.java")
    for name, specs_for_service in _service_specs.items() for spec in specs_for_service
}

for _match in re.finditer(
        r"case\s+([A-Z_]+)\s*->\s*switch\s*\(level\)\s*\{(.*?)\n\s*\};",
        _spec_manager, re.S):
    _spec_id = _match.group(1).lower()
    _service_path = _service_for_spec.get(_spec_id)
    if _service_path is None or not os.path.exists(_service_path):
        fail(f"doctrine-audit: {_spec_id} gameplay service-e nem oldható fel")
        continue
    _service_source = read(_service_path)
    for _doctrine_id in re.findall(r'"([a-z0-9_]+)"', _match.group(2)):
        if f'"{_doctrine_id}"' not in _service_source:
            fail(f"doctrine nincs bekötve: {_spec_id}.{_doctrine_id} "
                 f"({os.path.basename(_service_path)})")

_class_gameplay = configs.get("class-gameplay.yml", {}) or {}
_class_roots = _class_gameplay.get("classes", {}) or {}
_specializations = _class_gameplay.get("specializations", {}) or {}


def _string_values(value, skip_active_kit=False):
    if isinstance(value, str):
        return {value}
    if isinstance(value, list):
        result = set()
        for item in value:
            result |= _string_values(item, skip_active_kit)
        return result
    if isinstance(value, dict):
        result = set()
        for key, item in value.items():
            if skip_active_kit and key == "active-kit":
                continue
            result |= _string_values(item, skip_active_kit)
        return result
    return set()


for _spec_id, _trial_id in sorted((spec, trial) for trial, spec in _trial_pairs.items()):
    _capstone = (_specializations.get(_spec_id) or {}).get("capstone-spell")
    if not _capstone:
        fail(f"{_spec_id}: capstone-spell hiányzik a class-gameplay.yml-ből")
        continue
    _service_path = _service_for_spec.get(_spec_id)
    _service_source = read(_service_path) if _service_path and os.path.exists(_service_path) else ""
    _service_name = next((name for name, specs_for_service in _service_specs.items()
                          if _spec_id in specs_for_service), "")
    _class_name = _config_key_for_service.get(_service_name, _service_name.lower())
    _mechanic_values = _string_values(_class_roots.get(_class_name, {}), True)
    if _capstone not in _mechanic_values and f'"{_capstone}"' not in _service_source:
        fail(f"capstone csak gimmick/generikus spell: {_spec_id}.{_capstone}")

_classes_catalog = configs.get("classes.yml", {}) or {}
_class_unlocks = _classes_catalog.get("classes", {}) or {}
_spec_unlocks = _classes_catalog.get("specializations", {}) or {}
for _service_name, _spec_ids in _service_specs.items():
    _class_id = _config_key_for_service.get(_service_name, _service_name.lower())
    _class_gameplay_root = _class_roots.get(_class_id, {}) or {}
    _kit = _class_gameplay_root.get("active-kit", {}) or {}
    _maximum = int(_kit.get("maximum", 0) or 0)
    _base_spells = set(((_class_unlocks.get(_class_id) or {}).get("spell-unlocks") or {}).keys())
    for _spec_id in _spec_ids:
        _configured = _kit.get(_spec_id) or []
        if len(_configured) != _maximum:
            fail(f"{_class_id}.{_spec_id}: active-kit {len(_configured)} spell, várt {_maximum}")
        _available = (_base_spells
                      | set(((_spec_unlocks.get(_spec_id) or {}).get("spell-unlocks") or {}).keys())
                      | set(((_specializations.get(_spec_id) or {}).get("spell-unlocks") or {}).keys()))
        for _spell_id in _configured:
            if _spell_id not in _available:
                fail(f"{_class_id}.{_spec_id}: active-kit nem feloldható spell: {_spell_id}")

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

# A manifest-sor csak a BRIEF; a kliens a PNG-t rendereli. Deklarált item-model
# hiányzó textúrával a játékban hiányzó-modellként (fekete-lila kocka) jelenik meg,
# és ezt eddig SEMMI nem fogta meg. WARN, nem FAIL: a művészi munka a kódtól külön
# ütemben halad, de a hiány legyen látható és számolható.
_pack_item_textures = {
    os.path.basename(_p)[:-4]
    for _p in glob.glob(os.path.join(REPO, "resource-pack/assets/icesmp/textures/item/*.png"))
}
if _pack_item_textures:
    _missing_textures = sorted(set(used_models) - _pack_item_textures)
    if _missing_textures:
        warn(f"resource pack: {len(_missing_textures)} deklarált ITEM_MODEL-hez nincs PNG a packban "
             f"(elsők: {', '.join(_missing_textures[:5])}) — a kliens hiányzó modellt renderel, "
             f"amíg a textúrák meg nem érkeznek")

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
    ("docs/TEXTURE_WORKSHEET.md", "docs/TEXTURE_WORKSHEET.md"),
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
# ezert MONITOR prioritason kell futniuk. KIVETEL: a cancel-only vedelmi handler (pl. a quest
# fizikai jutalom-stamp zarolasa) direkt HIGH/HIGHEST prioritason cancel-el es semmit nem
# konyvel — az ilyet a torzse azonositja: van setCancelled(true), es nincs manager-hivas.
_PROGRESS_LISTENERS = ["QuestProgressListener", "DailyQuestListener", "ProfessionXpListener",
                       "ServerChallengeListener", "GatheringBuffListener"]
_CANCELLABLE = {"BlockBreakEvent", "BlockPlaceEvent", "CraftItemEvent", "PlayerFishEvent",
                "EntityPickupItemEvent", "PlayerHarvestBlockEvent", "SmithItemEvent",
                "EnchantItemEvent", "InventoryClickEvent", "PlayerItemConsumeEvent"}
_PROGRESS_CALL = re.compile(r"\b[a-z][A-Za-z]*Manager\s*\.")
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
            if not _m or _m.group(1) not in _CANCELLABLE:
                continue
            _body_end = _j + 1
            while _body_end < len(_lines) and "@EventHandler" not in _lines[_body_end]:
                _body_end += 1
            _body = "\n".join(_lines[_j:_body_end])
            if "setCancelled(true)" in _body and not _PROGRESS_CALL.search(_body):
                continue
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


# ===== lore-lefedettseg: nevesitett tartalom nem elhet kodex-horgony nelkul =====
# Kezzel talalt drift-osztaly (2026-08-07 audit): 5 relikvia es 3 nevesitett boss letezett a
# configban a kodex barmely emlitese nelkul. A kapu: minden relics.yml display-name, minden
# world.yml boss/miniboss nev ES minden SpecializationType display-nev szerepeljen a LORE.md-ben.
try:
    _lore_md = read(os.path.join(REPO, "docs/LORE.md"))
    _relics_yml = read(os.path.join(CFG, "relics.yml"))
    for _rname in re.findall(r'display-name:\s*"([^"]+)"', _relics_yml):
        if _rname not in _lore_md:
            fail(f"lore-lefedettseg: a(z) '{_rname}' relikvianak nincs kodex-bejegyzese a LORE.md-ben")
    _world_yml = read(os.path.join(CFG, "world.yml"))
    for _bname in re.findall(r'^\s+name:\s*"([^"]+)"\s*$', _world_yml, re.M):
        if _bname not in _lore_md:
            fail(f"lore-lefedettseg: a(z) '{_bname}' nevesitett boss/orzo nincs a LORE.md-ben")
    _spec_src = read(os.path.join(JAVA, "hu/taliann/icesmp/data/SpecializationType.java"))
    for _disp in re.findall(r'\("[a-z_]+",\s*"([^"]+)",\s*JobType\.', _spec_src):
        _plain = re.sub(r"<[^>]+>", "", _disp)
        if _plain not in _lore_md:
            fail(f"lore-lefedettseg: a(z) '{_plain}' specializacio-iskola nincs a LORE.md fuggelekeben")
    _boss_src = read(os.path.join(JAVA, "hu/taliann/icesmp/managers/WorldBossManager.java"))
    for _draw in re.findall(r'EntityType\.[A-Z_]+,\s*"([^"]+)"', _boss_src):
        _plain = re.sub(r"&[0-9a-fk-or]", "", _draw).replace("[Világboss]", "")
        _plain = re.sub(r"^[^A-Za-zÁÉÍÓÖŐÚÜŰáéíóöőúüű]+", "", _plain).strip()
        if _plain and _plain not in _lore_md:
            fail(f"lore-lefedettseg: a(z) '{_plain}' vilagboss-archetipus nincs a LORE.md-ben")
except Exception as e:
    warn(f"lore-lefedettseg ellenorzes kihagyva: {e}")


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

# ---------- szakma-katalógus: recept-fajta + nyersanyag-hurok ----------
# A katalógus önpolicolásának a magja. Minden recept KIMONDJA a fajtáját, és a fajta
# megmondja, milyen kart húzhat meg; a hurok-detektor pedig a katalógus adataiból
# számol, nem külső vanilla-modellből. Amit ez a szakasz állít, az mind a fájlból
# levezethető — a hozam-arány emberi szabály marad, azt itt SEM állítjuk.
try:
    _KINDS = {"gyakorlo", "hozam", "egyedi", "lanc", "ritkasag"}
    _FUNC = ("template", "affix-tier", "enchant", "attributes", "consumable", "signature", "potion-effects")
    _GYAKORLO_MAX_LEVEL = 15
    # Boss/esemény-kötött alapanyagok: csak ezek kapuzhatnak ritkaság-receptet.
    _BOSS_UNIQUES = {
        "elso_csend_szilankja", "osi_ereklyeszilank", "szorny_mag", "vad_esszencia",
        "sarkanycsont_szilank", "karhozat_parazs", "fonixpihe", "dermedt_konnycsepp",
        "nema_kristaly", "emlekszilank", "arnyekpor",
    }
    # Boss/esemény-kötött VANILLA anyagok: ezek is kapuzhatnak ritkaság-receptet.
    _BOSS_MATERIALS = {"NETHER_STAR", "DRAGON_EGG", "ELYTRA", "HEART_OF_THE_SEA",
                       "TOTEM_OF_UNDYING", "ECHO_SHARD"}
    # A vanilla MAGA is duplikálja ezt a tárgyat ugyanilyen recepttel — a katalógusból ez
    # nem látszik, ezért nevesítve engedjük át a hurok-detektoron.
    _VANILLA_DUPLICATION = {"kovacsmesteri_sablon"}
    # A rúnák fogyasztója a rúna-felhelyezés (nem recept-hozzávaló) — nem zsákutca.
    # A suttogas_meghivo esemeny-belepo (WhisperListener), nem craft-alapanyag.
    _RUNE_SINKS = {"runa_elek", "runa_zapor", "runa_bastya", "runa_lang",
                   "runa_fagy", "runa_moho", "runa_visszhang", "suttogas_meghivo"}
    # Vanillában visszaalakítható blokk↔item párok: a katalógus nem láthatja, hogy a
    # kimenet a bemenetté alakítható vissza, ezért a hurok-detektornak meg kell mondani.
    _REVERSIBLE = {
        "AMETHYST_BLOCK": ("AMETHYST_SHARD", 4), "LAPIS_BLOCK": ("LAPIS_LAZULI", 9),
        "COAL_BLOCK": ("COAL", 9), "IRON_BLOCK": ("IRON_INGOT", 9),
        "GOLD_BLOCK": ("GOLD_INGOT", 9), "DIAMOND_BLOCK": ("DIAMOND", 9),
        "REDSTONE_BLOCK": ("REDSTONE", 9), "SLIME_BLOCK": ("SLIME_BALL", 9),
        "DRIED_KELP_BLOCK": ("DRIED_KELP", 9), "HONEY_BLOCK": ("HONEY_BOTTLE", 4),
        "GLOWSTONE": ("GLOWSTONE_DUST", 4), "PRISMARINE": ("PRISMARINE_SHARD", 4),
    }

    _recipes = (configs.get("profession-recipes.yml") or {}).get("profession-recipes") or {}
    _materials = (configs.get("profession-materials.yml") or {}).get("profession-materials") or {}
    _templates = (configs.get("item-templates.yml") or {}).get("item-templates") or {}

    def _inputs(_rec):
        """(anyag -> darab, unique -> darab) a hozzávaló-listából."""
        _plain, _uniq = {}, {}
        for _entry in (_rec.get("ingredients") or []):
            _parts = str(_entry).split(":")
            if _parts[0] == "unique":
                _uniq[_parts[1]] = _uniq.get(_parts[1], 0) + int(_parts[2] if len(_parts) > 2 else 1)
            else:
                _plain[_parts[0]] = _plain.get(_parts[0], 0) + int(_parts[1] if len(_parts) > 1 else 1)
        return _plain, _uniq

    _made_unique, _used_unique = {}, {}
    for _rid, _rec in _recipes.items():
        _res = _rec.get("result") or {}
        if "unique" in _res:
            _made_unique.setdefault(_res["unique"], []).append(_rid)
        _, _u = _inputs(_rec)
        for _uid in _u:
            _used_unique.setdefault(_uid, []).append(_rid)

    for _rid, _rec in sorted(_recipes.items()):
        _res = _rec.get("result") or {}
        _kind = _rec.get("kind")
        _plain, _uniq = _inputs(_rec)
        _amount = int(_res.get("amount", 1))
        _template_id = _res.get("template")
        if _template_id:
            _template = _templates.get(_template_id)
            if not isinstance(_template, dict):
                fail(f"recept-fajta: '{_rid}' ismeretlen authored template-et ad: '{_template_id}'")
            elif (_res.get("unique") or _amount != 1
                  or str(_res.get("material", "")).upper()
                  != str(_template.get("material", "")).upper()):
                fail(f"recept-fajta: '{_rid}' canonical result material/stack eltér a "
                     f"'{_template_id}' template-től")
        if _kind not in _KINDS:
            fail(f"recept-fajta: '{_rid}' kind='{_kind}' — a megengedettek: {sorted(_KINDS)}")
            continue
        if _kind == "egyedi" and not any(_k in _res for _k in _FUNC):
            fail(f"recept-fajta: '{_rid}' kind=egyedi, de a kimeneten NINCS funkcionális "
                 f"komponens ({', '.join(_FUNC)}) — üres ígéret-item")
        if _kind == "lanc" and "unique" not in _res and not _uniq:
            fail(f"recept-fajta: '{_rid}' kind=lanc, de se egyedi kimenete, se egyedi hozzávalója nincs")
        if _kind == "gyakorlo":
            if int(_rec.get("level", 0)) > _GYAKORLO_MAX_LEVEL:
                fail(f"recept-fajta: '{_rid}' kind=gyakorlo L{_rec.get('level')} — a gyakorlórecept "
                     f"csak L{_GYAKORLO_MAX_LEVEL}-ig megengedett (fölötte a vanilla-paritás sértő)")
            if _uniq:
                fail(f"recept-fajta: '{_rid}' kind=gyakorlo, de egyedi alapanyagot kér — "
                     f"a gyakorlórecept nem kerülhet semmibe a nyersanyagon felül")
        if _kind == "ritkasag":
            if _amount != 1:
                fail(f"recept-fajta: '{_rid}' kind=ritkasag amount={_amount} — a ritkaság nem sokszorozható")
            if not (set(_uniq) & _BOSS_UNIQUES) and not (set(_plain) & _BOSS_MATERIALS):
                fail(f"recept-fajta: '{_rid}' kind=ritkasag, de nincs boss/esemény-kötött alapanyaga "
                     f"(egy boss-kötött egyedi alapanyag vagy {sorted(_BOSS_MATERIALS)} egyike kell)")

    for _uid, _rids in sorted(_made_unique.items()):
        if _uid not in _used_unique and _uid not in _RUNE_SINKS:
            fail(f"lánc-zsákutca: a(z) '{_uid}' egyedi alapanyag craftolható ({_rids[0]}), "
                 f"de egyetlen recept sem használja")
    for _uid in sorted(_materials):
        if _uid not in _used_unique and _uid not in _made_unique and _uid not in _RUNE_SINKS:
            warn(f"felhasználatlan egyedi alapanyag: '{_uid}' — sem recept nem kéri, sem recept nem adja")

    # 1-körös hurok: a recept a saját bemenetét sokszorozza.
    for _rid, _rec in sorted(_recipes.items()):
        _res = _rec.get("result") or {}
        _mat = _res.get("material")
        _plain, _ = _inputs(_rec)
        _amount = int(_res.get("amount", 1))
        if _rid in _VANILLA_DUPLICATION:
            continue
        if _mat and _mat in _plain and _amount > _plain[_mat]:
            fail(f"nyersanyag-hurok: '{_rid}' {_plain[_mat]}× {_mat} → {_amount}× {_mat} "
                 f"(+{_amount - _plain[_mat]}/craft, korlátlanul ismételhető)")
        # Blokk↔item visszaalakítás: a kimenet többe kerül, mint amennyiből a bemenet kijön.
        for _in_mat, _in_amt in _plain.items():
            _rev = _REVERSIBLE.get(_in_mat)
            if _rev and _mat == _rev[0] and _amount > _in_amt * _rev[1]:
                fail(f"nyersanyag-hurok: '{_rid}' {_in_amt}× {_in_mat} → {_amount}× {_mat}, "
                     f"de {_rev[1]}× {_mat} visszaalakítható 1× {_in_mat}-re (+{_amount - _in_amt * _rev[1]})")

    # 2-körös hurok: A egyedi alapanyagot gyárt, B azt elhasználva TÖBBET ad vissza A bemenetéből.
    for _a_id, _a in sorted(_recipes.items()):
        _a_res = _a.get("result") or {}
        _a_unique = _a_res.get("unique")
        if not _a_unique:
            continue
        _a_plain, _ = _inputs(_a)
        _a_out = int(_a_res.get("amount", 1))
        for _b_id, _b in sorted(_recipes.items()):
            _b_res = _b.get("result") or {}
            _b_plain, _b_uniq = _inputs(_b)
            if _a_unique not in _b_uniq or not _b_res.get("material"):
                continue
            _shared = _b_res["material"]
            if _shared not in _a_plain:
                continue
            # Hány A-craft kell egy B-hez, és mennyi közös anyagba kerül összesen.
            _cycles = -(-_b_uniq[_a_unique] // max(1, _a_out))
            _spent = _cycles * _a_plain[_shared] + _b_plain.get(_shared, 0)
            _gained = int(_b_res.get("amount", 1))
            if _gained > _spent:
                fail(f"nyersanyag-hurok: '{_a_id}' + '{_b_id}' kör nettó +{_gained - _spent}× "
                     f"{_shared} ({_spent} be, {_gained} ki) — korlátlanul ismételhető")

    # A dokumentált receptszám nem csúszhat el a valóditól.
    _catalog_size = len(_recipes)
    for _doc_rel in ("docs/BUILDER_GUIDE.md", "docs/FEATURES.md", "AGENTS.md", "CLAUDE.md"):
        _doc_path = os.path.join(REPO, _doc_rel)
        if not os.path.exists(_doc_path):
            continue
        for _claim in re.findall(r"(\d{3})\s*(?:recept|receptes|recipe)", read(_doc_path)):
            if int(_claim) != _catalog_size:
                fail(f"receptszám-drift: {_doc_rel} {_claim} receptet állít, a katalógusban "
                     f"{_catalog_size} van")
except Exception as e:
    fail(f"szakma-katalógus ellenőrzés hibája: {e}")

for w in warns:
    print(f"⚠ WARN: {w}")
for f_ in fails:
    print(f"✗ FAIL: {f_}")
print(f"\nÖsszegzés: {len(fails)} FAIL, {len(warns)} WARN "
      f"({len(quests)} quest, {len(used_models)} item-model, {len(used_perms)} jog-node, "
      f"{len(known_commands)} parancsnév ellenőrizve)")
sys.exit(1 if fails else 0)
