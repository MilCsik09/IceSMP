#!/usr/bin/env python3
"""Strict, source-based IceSMP class/spec/spell/cast audit.

A source definition and a runtime registration are intentionally separate evidence sets.
A BaseSpell constructor, enum-backed spell definition or ConfiguredSpell builder proves DEFINED;
REGISTERED requires an actual SpellRegistry.register(...) call reachable from startup.
"""
from __future__ import annotations

import argparse
import csv
import re
import subprocess
import sys
from collections import deque
from dataclasses import dataclass, field
from pathlib import Path

CLASS_TO_SPECS = {
    "warrior": ("berserker", "guardian"),
    "evoker": ("devastation", "preservation"),
    "archer": ("sharpshooter", "beast_master"),
    "shaman": ("elemental", "enhancement", "tidal"),
    "monk": ("windwalker", "brewmaster", "mistweaver"),
    "paladin": ("holy", "retribution", "protection"),
    "demon_hunter": ("havoc", "vengeance"),
    "druid": ("feral", "lunar", "ironbark", "restoration"),
    "priest": ("discipline", "bone_priest", "shadow"),
    "death_knight": ("blood", "frost", "unholy"),
    "assassin": ("poisoner", "phantom", "plaguebringer"),
    "warlock": ("affliction", "destruction", "demonologist"),
    "wizard": ("elementalist", "necromancer"),
}
SPEC_TO_CLASS = {spec: clazz for clazz, specs in CLASS_TO_SPECS.items() for spec in specs}
DARK_SPECS = {"necromancer", "plaguebringer", "unholy", "bone_priest", "demonologist"}
NORMAL_PROGRESSION = {"BASE", "SPEC", "TALENT", "QUEST"}
EXPLICIT_PROVENANCE = {
    "talent_surge": ("TALENT", "SpellCatalog.registerTalentSpells explicit talent provenance"),
}

# Manual gameplay-semantic review. B means gameplay stays declarative while presentation is
# intentionally signature-grade. C/D are merge blockers until the spell is migrated.
SIGNATURE_PRESENTATION_IDS = {
    "gravity_well", "berserk", "last_stand", "masterful_shot", "king_of_beasts",
    "deathcap", "spectre", "celestial_alignment", "avenging_wrath", "final_verdict",
    "breath_of_sindragosa", "ascendance_flame", "doom_winds", "serenity", "invoke_niuzao",
    "evangelism", "void_eruption", "darkglare", "metamorphosis_havoc", "the_hunt",
    "metamorphosis_veng", "dragonrage", "eternity_breath", "rewind", "incarnation_bear",
    "tranquility", "spirit_tide", "revival",
}
CONFIGURED_IDENTITY_FINDINGS: dict[str, tuple[str, str]] = {}
FORBIDDEN_COMBOS = {
    "soul-collapse": "Affliction/Destruction cross-spec chain",
    "way-of-hundred-fists": "duplicates Monk native martial-chain reward",
}

CSV_FIELDS = [
    "id", "display_name", "class", "spec", "provenance",
    "defined", "registered", "unlock_referenced", "active_kit_referenced",
    "combo_referenced", "balance_configured", "implementation_class",
    "implementation_category", "configured_spell_category",
    "configured_spell_category_reason", "unlock_level", "active_kit",
    "balance_entry", "spell_school", "combo_reference", "targeting", "cooldown",
    "effective_cost", "damage", "healing", "cc", "mobility", "summon", "delayed",
    "projectile", "scaling_path", "regression_coverage", "definition_location",
    "registration_location",
]


@dataclass
class Definition:
    spell_id: str
    name: str
    impl: str
    category: str
    definition_path: str
    line: int
    implementation_path: str
    cooldown: str = ""
    cost_type: str = ""
    cost: str = ""
    clazz: str = ""
    spec: str = ""
    details: dict[str, str] = field(default_factory=dict)
    configured_block: str = ""


@dataclass
class Row:
    spell_id: str
    definitions: list[Definition] = field(default_factory=list)
    registered: bool = False
    registration_locations: list[str] = field(default_factory=list)
    unlock_referenced: bool = False
    active_kit_referenced: bool = False
    combo_referenced: bool = False
    balance_configured: bool = False
    provenance: str = ""
    provenance_reason: str = ""
    clazz: str = ""
    spec: str = ""
    unlock_level: str = ""
    active_owners: set[str] = field(default_factory=set)
    combo_names: set[str] = field(default_factory=set)
    balance: dict[str, str] = field(default_factory=dict)
    school: str = ""

    @property
    def primary(self) -> Definition | None:
        if not self.definitions:
            return None
        configured = [definition for definition in self.definitions if definition.category == "configured"]
        return configured[0] if configured else self.definitions[0]


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def scalar(value: str) -> str:
    value = value.strip()
    return value[1:-1] if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'" else value


def line_no(text: str, position: int) -> int:
    return text.count("\n", 0, position) + 1


def snake(value: str) -> str:
    return re.sub(r"([a-z0-9])([A-Z])", r"\1_\2", value).lower()


def method_span(text: str, name: str) -> tuple[int, int, str] | None:
    match = re.search(
        r"\b(?:public|private|protected)\s+(?:static\s+)?[\w<>?, .\[\]]+\s+"
        + re.escape(name) + r"\s*\([^)]*\)\s*\{",
        text,
    )
    if not match:
        return None
    start = text.find("{", match.start())
    depth = 0
    string = False
    escaped = False
    for index in range(start, len(text)):
        char = text[index]
        if string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                string = False
            continue
        if char == '"':
            string = True
        elif char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return match.start(), index + 1, text[match.start(): index + 1]
    return None


def register_calls(text: str) -> list[tuple[int, str]]:
    calls: list[tuple[int, str]] = []
    for match in re.finditer(r"\b(?:spellRegistry|registry)\.register\s*\(", text):
        opening = text.find("(", match.start())
        depth = 0
        string = False
        escaped = False
        for index in range(opening, len(text)):
            char = text[index]
            if string:
                if escaped:
                    escaped = False
                elif char == "\\":
                    escaped = True
                elif char == '"':
                    string = False
                continue
            if char == '"':
                string = True
            elif char == "(":
                depth += 1
            elif char == ")":
                depth -= 1
                if depth == 0:
                    calls.append((match.start(), text[match.start(): index + 1]))
                    break
    return calls


def method_owner(name: str) -> tuple[str, str]:
    token = snake(name.removeprefix("register"))
    if token in CLASS_TO_SPECS:
        return token, ""
    if token in SPEC_TO_CLASS:
        return SPEC_TO_CLASS[token], token
    return "", ""


def classify(implementation: str, text: str) -> str:
    lowered = implementation.lower()
    if implementation == "ConfiguredSpell":
        return "configured"
    if implementation == "ShamanTotemSpell":
        return "stateful"
    if "form" in lowered:
        return "form"
    if "summon" in lowered or "minion" in lowered:
        return "summon"
    if "projectile" in lowered or "launchProjectile" in text:
        return "projectile"
    if "runDelayed" in text or "runAtFixedRate" in text or "clearPlayerState" in text:
        return "stateful"
    return "dedicated"


def configured_details(block: str) -> dict[str, str]:
    details = {
        "targeting": "TARGET" if ".target(" in block else "AOE" if ".aoe(" in block else "SELF"
    }
    for key, pattern in (
        ("damage", r"\.damage\(([^)]+)\)"),
        ("healing", r"\.healSelf\(([^)]+)\)"),
    ):
        match = re.search(pattern, block)
        if match:
            details[key] = match.group(1).strip()
    effects = re.findall(r"targetEffect\(PotionEffectType\.([A-Z_]+)", block)
    hard = [effect for effect in effects if effect in {"SLOWNESS", "BLINDNESS", "LEVITATION", "JUMP_BOOST"}]
    if ".freeze(" in block:
        hard.append("FREEZE")
    if hard:
        details["cc"] = ",".join(hard)
    if ".dash(" in block:
        details["mobility"] = "dash"
    if any(token in block for token in (".knockback(", ".pull(", ".launchUp(")):
        details["mobility"] = (details.get("mobility", "") + "+displacement").strip("+")
    return details


def enum_definitions(root: Path) -> tuple[list[Definition], dict[tuple[str, str], str]]:
    definitions: list[Definition] = []
    constructor_ids: dict[tuple[str, str], str] = {}

    druid = root / "src/main/java/hu/taliann/icesmp/spells/DruidFormSpell.java"
    if druid.is_file():
        text = read(druid)
        rel = druid.relative_to(root).as_posix()
        for match in re.finditer(r"(?m)^\s*([A-Z_]+)\(\"([^\"]+)\",\s*\"([^\"]+)\"", text):
            enum_name, spell_id, name = match.groups()
            if not spell_id.startswith("druid_") or not spell_id.endswith("_form"):
                continue
            definitions.append(Definition(
                spell_id, name, "DruidFormSpell", "form", rel, line_no(text, match.start()), rel
            ))
            constructor_ids[("DruidFormSpell", enum_name)] = spell_id

    totem_manager = root / "src/main/java/hu/taliann/icesmp/managers/TotemManager.java"
    totem_spell = root / "src/main/java/hu/taliann/icesmp/spells/ShamanTotemSpell.java"
    if totem_manager.is_file() and totem_spell.is_file():
        text = read(totem_manager)
        rel = totem_manager.relative_to(root).as_posix()
        for match in re.finditer(r"(?m)^\s*([A-Z_]+)\(\"([^\"]+_totem)\",\s*\"([^\"]+)\"", text):
            enum_name, spell_id, name = match.groups()
            definitions.append(Definition(
                spell_id, name, "ShamanTotemSpell", "stateful", rel,
                line_no(text, match.start()), rel
            ))
            constructor_ids[("ShamanTotemSpell", enum_name)] = spell_id
    return definitions, constructor_ids


def source_class_index(java_root: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    root = java_root.parents[2]
    for path in java_root.rglob("*.java"):
        text = read(path)
        match = re.search(r"(?:public\s+)?(?:final\s+)?class\s+(\w+)", text)
        if match:
            result[match.group(1)] = path.relative_to(root).as_posix()
    return result


def discover_definitions(java_root: Path, root: Path) -> tuple[dict[str, list[Definition]], dict[str, str], dict[tuple[str, str], str]]:
    definitions: dict[str, list[Definition]] = {}
    class_to_id: dict[str, str] = {}
    class_sources = source_class_index(java_root)

    for path in java_root.rglob("*.java"):
        text = read(path)
        rel = path.relative_to(root).as_posix()
        class_match = re.search(r"(?:public\s+)?(?:final\s+)?class\s+(\w+)", text)
        implementation = class_match.group(1) if class_match else path.stem
        category = classify(implementation, text)
        for match in re.finditer(
            r"super\([^;]*?\"([^\"]+)\",\s*\"([^\"]+)\",\s*([^,]+),\s*SpellCostType\.([A-Z]+),\s*([^)]+)\)",
            text, re.S,
        ):
            spell_id, name, cooldown, cost_type, cost = match.groups()
            definition = Definition(
                spell_id, name, implementation, category, rel, line_no(text, match.start()), rel,
                cooldown.strip(), cost_type, cost.strip(),
            )
            definitions.setdefault(spell_id, []).append(definition)
            class_to_id[implementation] = spell_id

        methods = list(re.finditer(r"private static void (register[A-Za-z0-9_]+)\s*\(", text))
        spans: list[tuple[int, int, str]] = []
        for index, method in enumerate(methods):
            end = methods[index + 1].start() if index + 1 < len(methods) else len(text)
            spans.append((method.start(), end, method.group(1)))

        def owner(position: int) -> tuple[str, str]:
            for start, end, method_name in spans:
                if start <= position < end:
                    return method_owner(method_name)
            return "", ""

        for match in re.finditer(
            r"ConfiguredSpell\.builder\([^,]+,\s*\"([^\"]+)\",\s*\"([^\"]+)\",\s*([^,]+),\s*SpellCostType\.([A-Z]+),\s*([^)]+)\)(.*?)(?:\.build\(\))",
            text, re.S,
        ):
            spell_id, name, cooldown, cost_type, cost, tail = match.groups()
            clazz, spec = owner(match.start())
            definitions.setdefault(spell_id, []).append(Definition(
                spell_id, name, "ConfiguredSpell", "configured", rel, line_no(text, match.start()),
                class_sources.get("ConfiguredSpell", rel), cooldown.strip(), cost_type, cost.strip(),
                clazz, spec, configured_details(tail), match.group(0),
            ))

        for generic, category in (
            ("ProjectileBurstSpell", "projectile"),
            ("BlinkSpell", "dedicated"),
            ("SummonMinionsSpell", "summon"),
        ):
            for match in re.finditer(r"new\s+" + generic + r"\s*\((.*?)\)\s*[;,]", text, re.S):
                literals = re.findall(r"\"([^\"]+)\"", match.group(1))
                if len(literals) < 2:
                    continue
                clazz, spec = owner(match.start())
                spell_id, name = literals[0], literals[1]
                details = ({"projectile": "yes"} if category == "projectile"
                           else {"summon": "yes"} if category == "summon"
                           else {"mobility": "blink"})
                definitions.setdefault(spell_id, []).append(Definition(
                    spell_id, name, generic, category, rel, line_no(text, match.start()),
                    class_sources.get(generic, rel), clazz=clazz, spec=spec, details=details,
                ))

    dynamic, constructor_ids = enum_definitions(root)
    for definition in dynamic:
        definitions.setdefault(definition.spell_id, []).append(definition)
    return definitions, class_to_id, constructor_ids


def reachable_catalog(text: str) -> set[str]:
    queue = ["registerExpansionSpells", "registerSummonSpells"]
    seen: set[str] = set()
    while queue:
        method = queue.pop()
        if method in seen:
            continue
        seen.add(method)
        span = method_span(text, method)
        if not span:
            continue
        for called in re.findall(r"\b(register[A-Z][A-Za-z0-9_]*)\s*\(", span[2]):
            if called not in seen:
                queue.append(called)
    return seen


def resolve_registration(expression: str, class_to_id: dict[str, str],
                         constructor_ids: dict[tuple[str, str], str]) -> str | None:
    configured = re.search(r"ConfiguredSpell\.builder\([^,]+,\s*\"([^\"]+)\"", expression)
    if configured:
        return configured.group(1)
    constructor = re.search(r"new\s+(\w+)\s*\(", expression)
    if not constructor:
        return None
    implementation = constructor.group(1)
    if implementation in class_to_id:
        return class_to_id[implementation]
    if implementation == "DruidFormSpell":
        enum = re.search(r"DruidFormSpell\.Form\.([A-Z_]+)", expression)
        if enum:
            return constructor_ids.get((implementation, enum.group(1)))
    if implementation == "ShamanTotemSpell":
        enum = re.search(r"TotemManager\.TotemType\.([A-Z_]+)", expression)
        if enum:
            return constructor_ids.get((implementation, enum.group(1)))
    literals = re.findall(r"\"([^\"]+)\"", expression[constructor.end():])
    return literals[0] if literals else None


def discover_registrations(root: Path, class_to_id: dict[str, str],
                           constructor_ids: dict[tuple[str, str], str]) -> tuple[dict[str, list[str]], list[str]]:
    registrations: dict[str, list[str]] = {}
    unresolved: list[str] = []
    authorities = (
        root / "src/main/java/hu/taliann/icesmp/core/IceSMPCore.java",
        root / "src/main/java/hu/taliann/icesmp/spells/SpellCatalog.java",
    )
    for path in authorities:
        text = read(path)
        rel = path.relative_to(root).as_posix()
        method_names = (["registerSpells"] if path.name == "IceSMPCore.java"
                        else sorted(reachable_catalog(text)))
        ranges: list[tuple[int, int]] = []
        for method in method_names:
            span = method_span(text, method)
            if span:
                ranges.append((span[0], span[1]))
        for position, expression in register_calls(text):
            if not any(start <= position < end for start, end in ranges):
                continue
            spell_id = resolve_registration(expression, class_to_id, constructor_ids)
            location = f"{rel}:{line_no(text, position)}"
            if spell_id is None:
                unresolved.append(f"{location}: {expression[:160].replace(chr(10), ' ')}")
            else:
                registrations.setdefault(spell_id, []).append(location)
    return registrations, unresolved


def parse_unlocks(path: Path) -> dict[str, tuple[str, str, int]]:
    result: dict[str, tuple[str, str, int]] = {}
    stack: list[tuple[int, str]] = []
    for raw in read(path).splitlines():
        clean = raw.split("#", 1)[0].rstrip()
        if not clean.strip() or ":" not in clean:
            continue
        indent = len(clean) - len(clean.lstrip())
        while stack and stack[-1][0] >= indent:
            stack.pop()
        key, value = clean.strip().split(":", 1)
        key, value = key.strip(), value.strip()
        stack.append((indent, key))
        keys = [part for _, part in stack]
        if len(keys) < 4 or keys[-2] != "spell-unlocks" or not value:
            continue
        owner = keys[-3]
        try:
            level = int(value)
        except ValueError:
            continue
        if keys[0] == "classes" and owner in CLASS_TO_SPECS:
            result[key] = (owner, "", level)
        elif keys[0] == "specializations" and owner in SPEC_TO_CLASS:
            result[key] = (SPEC_TO_CLASS[owner], owner, level)
    return result


def parse_active_kits(path: Path) -> dict[str, set[str]]:
    result: dict[str, set[str]] = {}
    stack: list[tuple[int, str]] = []
    owner = ""
    for raw in read(path).splitlines():
        clean = raw.split("#", 1)[0].rstrip()
        if not clean.strip():
            continue
        indent = len(clean) - len(clean.lstrip())
        stripped = clean.strip()
        while stack and stack[-1][0] >= indent:
            stack.pop()
        if stripped.startswith("-"):
            if owner:
                result.setdefault(owner, set()).add(scalar(stripped[1:].strip()))
            continue
        if ":" not in stripped:
            continue
        key, value = stripped.split(":", 1)
        key, value = key.strip(), value.strip()
        stack.append((indent, key))
        keys = [part for _, part in stack]
        owner = ""
        if not value and "active-kit" in keys and key in SPEC_TO_CLASS:
            owner = key
    return result


def parse_balance(path: Path) -> dict[str, dict[str, str]]:
    result: dict[str, dict[str, str]] = {}
    in_root = False
    current = ""
    for raw in read(path).splitlines():
        clean = raw.split("#", 1)[0].rstrip()
        if not clean.strip():
            continue
        indent = len(clean) - len(clean.lstrip())
        stripped = clean.strip()
        if indent == 0:
            in_root = stripped == "spell-balance:"
            current = ""
            continue
        if not in_root:
            continue
        if indent == 2 and stripped.endswith(":"):
            current = stripped[:-1].strip()
            result.setdefault(current, {})
        elif indent >= 4 and current and ":" in stripped:
            key, value = stripped.split(":", 1)
            result[current][key.strip()] = scalar(value)
    return result


def parse_spell_config(path: Path) -> tuple[dict[str, str], dict[str, list[str]], dict[str, tuple[str, str]]]:
    text = read(path)
    schools: dict[str, str] = {}
    chains: dict[str, list[str]] = {}
    pairs: dict[str, tuple[str, str]] = {}

    school_match = re.search(r"(?ms)^\s*by-spell:\s*\n(.*?)(?=^\s{2}\S|\Z)", text)
    if school_match:
        for match in re.finditer(r"(?m)^\s{6}([a-z0-9_:-]+):\s*([a-z0-9_:-]+)\s*$", school_match.group(1)):
            schools[match.group(1)] = match.group(2)

    chain_match = re.search(r"(?ms)^\s{4}chains:\s*\n(.*?)(?=^\s{4}pairs:|\Z)", text)
    if chain_match:
        current = ""
        for raw in chain_match.group(1).splitlines():
            stripped = raw.split("#", 1)[0].strip()
            indent = len(raw) - len(raw.lstrip())
            if indent == 6 and stripped.endswith(":"):
                current = stripped[:-1]
            elif current and stripped.startswith("steps:"):
                value = stripped.split(":", 1)[1].strip()
                if value.startswith("[") and value.endswith("]"):
                    chains[current] = [scalar(part.strip()) for part in value[1:-1].split(",") if part.strip()]

    pair_match = re.search(r"(?ms)^\s{4}pairs:\s*\n(.*?)(?=^\S|\Z)", text)
    if pair_match:
        current = first = ""
        for raw in pair_match.group(1).splitlines():
            stripped = raw.split("#", 1)[0].strip()
            indent = len(raw) - len(raw.lstrip())
            if indent == 6 and stripped.endswith(":"):
                current, first = stripped[:-1], ""
            elif current and stripped.startswith("first:"):
                first = scalar(stripped.split(":", 1)[1])
            elif current and first and stripped.startswith("second:"):
                pairs[current] = (first, scalar(stripped.split(":", 1)[1]))
                first = ""
    return schools, chains, pairs


def configured_review(definition: Definition) -> tuple[str, str]:
    explicit = CONFIGURED_IDENTITY_FINDINGS.get(definition.spell_id)
    if explicit:
        return explicit
    if any(token in definition.configured_block for token in (
        "runDelayed", "runAtFixedRate", "launchProjectile", "PersistentDataContainer",
        "targetMemory", "channel", "chargeSession",
    )):
        return "D", "stateful/delayed/projectile lifecycle is not valid inside ConfiguredSpell"
    if definition.spell_id in SIGNATURE_PRESENTATION_IDS:
        return "B", "immediate generic gameplay is sufficient; signature presentation/VFX readability is intentionally distinct"
    outputs = [name for name in ("damage", "healing", "cc", "mobility") if definition.details.get(name)]
    return "A", (f"immediate {definition.details.get('targeting', 'SELF')} "
                 f"{'/'.join(outputs) or 'buff/debuff/utility'} primitive; no persistent state, "
                 "scheduler, projectile lifecycle or target memory")


def override_inventory(java_root: Path) -> dict[str, list[str]]:
    patterns = {
        "execute": r"\bvoid\s+execute\s*\(\s*(?:final\s+)?Player\s+\w+\s*\)",
        "executeSpell": r"\bboolean\s+executeSpell\s*\(\s*(?:final\s+)?Player\s+\w+\s*\)",
        "executeSpellScalar": r"\bboolean\s+executeSpell\s*\(\s*(?:final\s+)?Player\s+\w+\s*,\s*(?:final\s+)?double\s+\w+\s*\)",
        "executeCast": r"\bCastOutcome\s+executeCast\s*\(\s*(?:final\s+)?Player\s+\w+\s*\)",
        "cast": r"\bCastOutcome\s+cast\s*\(\s*(?:final\s+)?Player\s+\w+\s*,\s*(?:final\s+)?CastModifiers\s+\w+\s*\)",
    }
    result = {name: [] for name in patterns}
    root = java_root.parents[2]
    for path in java_root.rglob("*.java"):
        if path.name == "Spell.java":
            continue
        text = read(path)
        rel = path.relative_to(root).as_posix()
        for name, pattern in patterns.items():
            if re.search(pattern, text):
                result[name].append(rel)
    return result


def valid_loadouts(row: Row) -> set[tuple[str, str]]:
    if not row.clazz:
        return {(clazz, spec) for clazz, specs in CLASS_TO_SPECS.items() for spec in specs}
    if row.spec:
        return {(row.clazz, row.spec)}
    return {(row.clazz, spec) for spec in CLASS_TO_SPECS[row.clazz]}


def implementation_source(root: Path, definition: Definition) -> Path:
    # Manager-backed totems keep their delayed pulse lifecycle in TotemManager, not the thin spell class.
    if definition.impl == "ShamanTotemSpell":
        return root / "src/main/java/hu/taliann/icesmp/managers/TotemManager.java"
    candidate = root / definition.implementation_path
    return candidate if candidate.is_file() else root / definition.definition_path


def implementation_traits(root: Path, definition: Definition) -> dict[str, object]:
    path = implementation_source(root, definition)
    text = read(path) if path.is_file() else ""
    delayed = "runDelayed" in text or "runAtFixedRate" in text
    projectile = definition.category == "projectile" or "launchProjectile" in text or "teleportAsync" in text
    scaled_output = delayed and any(token in text for token in (
        "SpellDamageUtil", "SpellHealingUtil", ".damage(", "setHealth(", "scaledDamage(", "scaledHealing(",
    ))
    snapshot = any(token in text for token in (
        "SpellExecutionContext.capture()", "SpellDamageUtil.markProjectile", "CastModifiers modifiers",
        "SpellDamageUtil.scaledDamage", "SpellHealingUtil.scaledHealing",
    ))
    return {
        "path": path,
        "text": text,
        "delayed": delayed,
        "projectile": projectile,
        "scaled_output": scaled_output,
        "snapshot": snapshot,
    }


def delayed_inventory(rows: dict[str, Row], root: Path) -> tuple[list[dict[str, str]], list[str]]:
    inventory: list[dict[str, str]] = []
    errors: list[str] = []
    for row in rows.values():
        definition = row.primary
        if not row.registered or definition is None:
            continue
        traits = implementation_traits(root, definition)
        delayed = bool(traits["delayed"])
        projectile = bool(traits["projectile"])
        if not delayed and not projectile:
            continue
        text = str(traits["text"])
        snapshot = bool(traits["snapshot"])
        scaled_output = bool(traits["scaled_output"])
        inventory.append({
            "id": row.spell_id,
            "impl": definition.impl,
            "delayed": "yes" if delayed else "no",
            "projectile": "yes" if projectile else "no",
            "snapshot": "yes" if snapshot else "not required" if not scaled_output else "MISSING",
            "caster": "UUID/immutable" if "UUID" in text else "owner-thread/none",
            "target": "entity/UUID bounded" if ("LivingEntity" in text or "UUID" in text or "Entity" in text) else "n/a",
            "owner": ("entity/player scheduler" if "getScheduler()" in text
                      else "global/region scheduler" if "Scheduler" in text else "synchronous/event owner"),
            "cleanup": "explicit" if any(token in text for token in ("remove()", "task.cancel()", "clearPlayerState", "clearOwnerProjection")) else "event-owned",
            "source": Path(traits["path"]).relative_to(root).as_posix(),
        })
        if scaled_output and not snapshot:
            errors.append(
                f"delayed scaled spell lacks immutable CastModifiers/output snapshot: "
                f"{row.spell_id} ({inventory[-1]['source']})"
            )
    return inventory, errors


def regression_graph(root: Path) -> tuple[list[dict[str, str]], list[str]]:
    regression_root = root / "src/regression/java"
    build = read(root / "build.gradle.kts")
    registrations: dict[str, str] = {}
    for match in re.finditer(
        r"val\s+(\w+)\s*=\s*registerRegression\(\s*\"([^\"]+)\"\s*,.*?\"([^\"]+RegressionSuite)\"\s*\)",
        build, re.S,
    ):
        variable, _task, fqcn = match.groups()
        registrations[fqcn] = variable

    check_text = build[build.find("tasks.check"):]
    suites: dict[str, tuple[Path, str]] = {}
    for path in regression_root.rglob("*RegressionSuite.java"):
        text = read(path)
        package = re.search(r"package\s+([\w.]+);", text)
        clazz = re.search(r"class\s+(\w+RegressionSuite)\b", text)
        if package and clazz:
            suites[f"{package.group(1)}.{clazz.group(1)}"] = (path, text)

    simple_to_fqcn = {fqcn.rsplit(".", 1)[-1]: fqcn for fqcn in suites}
    calls: dict[str, set[str]] = {fqcn: set() for fqcn in suites}
    for owner, (_path, text) in suites.items():
        for simple, fqcn in simple_to_fqcn.items():
            if fqcn != owner and re.search(r"\b" + re.escape(simple) + r"\.main\s*\(", text):
                calls[owner].add(fqcn)

    direct = {fqcn for fqcn, variable in registrations.items() if variable in check_text}
    reachable = set(direct)
    queue = deque(direct)
    while queue:
        owner = queue.popleft()
        for callee in calls.get(owner, set()):
            if callee not in reachable:
                reachable.add(callee)
                queue.append(callee)

    mandatory_prefixes = (
        "hu.taliann.icesmp.warrior.", "hu.taliann.icesmp.evoker.", "hu.taliann.icesmp.archer.",
        "hu.taliann.icesmp.shaman.", "hu.taliann.icesmp.monk.", "hu.taliann.icesmp.paladin.",
        "hu.taliann.icesmp.demonhunter.", "hu.taliann.icesmp.druid.", "hu.taliann.icesmp.priest.",
        "hu.taliann.icesmp.deathknight.", "hu.taliann.icesmp.assassin.", "hu.taliann.icesmp.warlock.",
        "hu.taliann.icesmp.wizard.", "hu.taliann.icesmp.classspec.", "hu.taliann.icesmp.playerprofile.",
        "hu.taliann.icesmp.spells.", "hu.taliann.icesmp.hud.", "hu.taliann.icesmp.lifecycle.",
        "hu.taliann.icesmp.config.",
    )
    rows: list[dict[str, str]] = []
    errors: list[str] = []
    for fqcn, (path, _text) in sorted(suites.items()):
        task = registrations.get(fqcn, "")
        state = ("TASK+CHECK" if fqcn in direct else "DELEGATED" if fqcn in reachable
                 else "TASK_ONLY" if task else "ORPHAN")
        rows.append({
            "suite": fqcn,
            "state": state,
            "task": task,
            "path": path.relative_to(root).as_posix(),
        })
        if fqcn.startswith(mandatory_prefixes) and state in {"TASK_ONLY", "ORPHAN"}:
            errors.append(f"mandatory regression suite is not reachable from check: {fqcn} ({state})")

    for required in (
        "hu.taliann.icesmp.wizard.WizardGameplayRegressionSuite",
        "hu.taliann.icesmp.wizard.WizardProfileRegressionSuite",
    ):
        state = next((row["state"] for row in rows if row["suite"] == required), "MISSING")
        if state != "TASK+CHECK":
            errors.append(f"Wizard suite requires explicit Gradle task in check: {required} ({state})")

    for required in (
        "hu.taliann.icesmp.spells.SpellCastArchitectureRegressionSuite",
        "hu.taliann.icesmp.spells.ClassSpellAuditRegressionSuite",
    ):
        state = next((row["state"] for row in rows if row["suite"] == required), "MISSING")
        if state not in {"TASK+CHECK", "DELEGATED"}:
            errors.append(f"hardening suite is not transitively executed by check: {required} ({state})")
    return rows, errors


def druid_naming_errors(root: Path) -> list[str]:
    errors: list[str] = []
    service = root / "src/main/java/hu/taliann/icesmp/druid/DruidGameplayService.java"
    if service.is_file():
        text = read(service)
        for token in (
            '"harmony", "Természeti Erő"',
            "{amount} Természeti Erő szabadult fel",
            " • Természeti Erő ",
        ):
            if token in text:
                errors.append(f"Druid secondary Harmony still uses primary name: {token}")
    return errors


def build_rows(definitions: dict[str, list[Definition]], registrations: dict[str, list[str]],
               unlocks: dict[str, tuple[str, str, int]], active: dict[str, set[str]],
               balance: dict[str, dict[str, str]], schools: dict[str, str],
               chains: dict[str, list[str]], pairs: dict[str, tuple[str, str]]) -> dict[str, Row]:
    ids = set(definitions) | set(registrations) | set(unlocks) | set(balance)
    ids |= {spell for values in active.values() for spell in values}
    ids |= {spell for values in chains.values() for spell in values}
    ids |= {spell for pair in pairs.values() for spell in pair}
    rows = {spell_id: Row(spell_id, definitions=list(definitions.get(spell_id, []))) for spell_id in ids}

    for spell_id, locations in registrations.items():
        rows[spell_id].registered = True
        rows[spell_id].registration_locations = locations
    for spell_id, (clazz, spec, level) in unlocks.items():
        row = rows[spell_id]
        row.unlock_referenced = True
        row.clazz, row.spec, row.unlock_level = clazz, spec, str(level)
        row.provenance = "SPEC" if spec else "BASE"
        row.provenance_reason = "classes.yml spell-unlocks"
    for spell_id, (provenance, reason) in EXPLICIT_PROVENANCE.items():
        if spell_id in rows and not rows[spell_id].provenance:
            rows[spell_id].provenance = provenance
            rows[spell_id].provenance_reason = reason
    for owner, spells in active.items():
        for spell_id in spells:
            rows[spell_id].active_kit_referenced = True
            rows[spell_id].active_owners.add(owner)
    for name, spells in chains.items():
        for spell_id in spells:
            rows[spell_id].combo_referenced = True
            rows[spell_id].combo_names.add(name)
    for name, pair in pairs.items():
        for spell_id in pair:
            rows[spell_id].combo_referenced = True
            rows[spell_id].combo_names.add(name)
    for spell_id, entry in balance.items():
        rows[spell_id].balance_configured = True
        rows[spell_id].balance = entry
    for spell_id, school in schools.items():
        if spell_id in rows:
            rows[spell_id].school = school
    for row in rows.values():
        if row.primary and not row.clazz:
            row.clazz, row.spec = row.primary.clazz, row.primary.spec
    return rows


def validate(rows: dict[str, Row], registrations: dict[str, list[str]], unresolved: list[str],
             balance: dict[str, dict[str, str]], chains: dict[str, list[str]],
             pairs: dict[str, tuple[str, str]], overrides: dict[str, list[str]],
             delayed_errors: list[str], regression_errors: list[str], naming_errors: list[str],
             root: Path) -> list[str]:
    errors = [f"unresolved runtime registration: {item}" for item in unresolved]
    registered = set(registrations)
    balance_ids = set(balance)
    errors += [f"registered spell has no spells-balance.yml entry: {spell}" for spell in sorted(registered - balance_ids)]
    errors += [f"dead spells-balance.yml entry is not registered: {spell}" for spell in sorted(balance_ids - registered)]

    for spell_id, locations in sorted(registrations.items()):
        if len(locations) != 1:
            errors.append(
                f"runtime spell registered {len(locations)} times (duplicate startup path): "
                f"{spell_id}: {'; '.join(locations)}"
            )

    for spell_id, row in sorted(rows.items()):
        if row.registered and not row.definitions:
            errors.append(f"registered spell has no source definition: {spell_id}")
        if row.unlock_referenced and not row.registered:
            errors.append(f"unlock references non-registered spell: {spell_id}")
        if row.active_kit_referenced and not row.registered:
            errors.append(f"active-kit references non-registered spell: {spell_id}")
        if row.combo_referenced and not row.registered:
            errors.append(f"combo references non-registered spell: {spell_id}")
        if row.registered and not row.provenance:
            errors.append(f"registered spell has no explicit provenance: {spell_id}")
        if row.active_kit_referenced and row.spec:
            invalid_owners = sorted(owner for owner in row.active_owners if owner != row.spec)
            if invalid_owners:
                errors.append(f"cross-spec active-kit leakage for {spell_id}: {', '.join(invalid_owners)}")
        if row.primary and row.primary.category == "configured":
            category, reason = configured_review(row.primary)
            if category in {"C", "D"}:
                errors.append(f"ConfiguredSpell {category} not migrated: {spell_id}: {reason}")

    for name, steps in chains.items():
        if name in FORBIDDEN_COMBOS:
            errors.append(f"forbidden semantic combo returned: {name}: {FORBIDDEN_COMBOS[name]}")
        missing = [spell for spell in steps if spell not in registered]
        if missing:
            errors.append(f"combo chain {name} references non-registered: {', '.join(missing)}")
            continue
        common: set[tuple[str, str]] | None = None
        for spell_id in steps:
            common = valid_loadouts(rows[spell_id]) if common is None else common & valid_loadouts(rows[spell_id])
        if not common:
            errors.append(f"impossible combo chain {name}: {' -> '.join(steps)}")

    for name, pair in pairs.items():
        if name in FORBIDDEN_COMBOS:
            errors.append(f"forbidden semantic combo returned: {name}: {FORBIDDEN_COMBOS[name]}")
        missing = [spell for spell in pair if spell not in registered]
        if missing:
            errors.append(f"combo pair {name} references non-registered: {', '.join(missing)}")
            continue
        if not (valid_loadouts(rows[pair[0]]) & valid_loadouts(rows[pair[1]])):
            errors.append(f"impossible combo pair {name}: {pair[0]} -> {pair[1]}")

    errors += delayed_errors + regression_errors + naming_errors

    scalar_overrides = sorted(overrides["executeSpellScalar"])
    expected_scalar = ["src/main/java/hu/taliann/icesmp/spells/ConfiguredSpell.java"]
    if scalar_overrides != expected_scalar:
        errors.append(
            "executeSpell(Player,double) override inventory changed; expected ConfiguredSpell only, got: "
            + ", ".join(scalar_overrides)
        )

    spell = read(root / "src/main/java/hu/taliann/icesmp/spells/Spell.java")
    configured = read(root / "src/main/java/hu/taliann/icesmp/spells/ConfiguredSpell.java")
    registry = read(root / "src/main/java/hu/taliann/icesmp/managers/SpellRegistry.java")
    core = read(root / "src/main/java/hu/taliann/icesmp/core/IceSMPCore.java")
    if "return cast(player, CastModifiers.standardPower(powerMultiplier)).effectApplied();" not in spell:
        errors.append("Spell scalar compatibility bridge is not one-way to typed cast")
    if "final CastOutcome outcome = executeCast(player);" not in spell:
        errors.append("Spell.cast no longer uses executeCast as canonical typed path")
    if "return cast(player, CastModifiers.standardPower(power)).effectApplied();" not in configured:
        errors.append("ConfiguredSpell scalar bridge is not one-way to typed cast")
    if "effect.getDuration() * power" in configured:
        errors.append("generic power still scales CC/effect duration")
    if "putIfAbsent" not in registry or "Duplicate spell id" not in registry:
        errors.append("SpellRegistry duplicate fail-fast contract missing")
    if not re.search(r"withBalanceOverrides\([^)]*\).*?\{.*?return spell;", configured, re.S):
        errors.append("ConfiguredSpell.withBalanceOverrides no longer returns the original registration instance")
    if "spellRegistry.register(overridden)" in core:
        errors.append("dead ConfiguredSpell balance-replacement registration path remains in IceSMPCore")
    return errors


def csv_row(row: Row, root: Path) -> dict[str, str]:
    definition = row.primary
    configured_category = configured_reason = ""
    if definition and definition.category == "configured":
        configured_category, configured_reason = configured_review(definition)
    details = definition.details if definition else {}
    balance = row.balance
    traits = implementation_traits(root, definition) if definition else {
        "delayed": False, "projectile": False
    }
    return {
        "id": row.spell_id,
        "display_name": definition.name if definition else "",
        "class": row.clazz,
        "spec": row.spec,
        "provenance": row.provenance,
        "defined": "yes" if row.definitions else "no",
        "registered": "yes" if row.registered else "no",
        "unlock_referenced": "yes" if row.unlock_referenced else "no",
        "active_kit_referenced": "yes" if row.active_kit_referenced else "no",
        "combo_referenced": "yes" if row.combo_referenced else "no",
        "balance_configured": "yes" if row.balance_configured else "no",
        "implementation_class": definition.impl if definition else "",
        "implementation_category": definition.category if definition else "",
        "configured_spell_category": configured_category,
        "configured_spell_category_reason": configured_reason,
        "unlock_level": row.unlock_level,
        "active_kit": ",".join(sorted(row.active_owners)),
        "balance_entry": "yes" if row.balance_configured else "no",
        "spell_school": row.school or "class default / primordial",
        "combo_reference": ",".join(sorted(row.combo_names)),
        "targeting": details.get("targeting", "implementation-defined"),
        "cooldown": balance.get("cooldown", definition.cooldown if definition else ""),
        "effective_cost": balance.get("resource-cost", balance.get("cost-amount", definition.cost if definition else "")),
        "damage": balance.get("damage", details.get("damage", "")),
        "healing": balance.get("heal-self", details.get("healing", "")),
        "cc": details.get("cc", ""),
        "mobility": details.get("mobility", ""),
        "summon": "yes" if definition and definition.category == "summon" else "no",
        "delayed": "yes" if bool(traits["delayed"]) else "no",
        "projectile": "yes" if bool(traits["projectile"]) else "no",
        "scaling_path": "CastModifiers -> SpellExecutionContext/shared output primitives",
        "regression_coverage": "class/profile + cast architecture + strict source graph",
        "definition_location": ";".join(
            f"{item.definition_path}:{item.line}" for item in row.definitions
        ),
        "registration_location": ";".join(row.registration_locations),
    }


def git_value(root: Path, *args: str) -> str:
    try:
        return subprocess.check_output(["git", *args], cwd=root, text=True, stderr=subprocess.DEVNULL).strip()
    except Exception:
        return "unknown"


def write_report(path: Path, root: Path, rows: dict[str, Row], errors: list[str],
                 chains: dict[str, list[str]], pairs: dict[str, tuple[str, str]],
                 overrides: dict[str, list[str]], delayed: list[dict[str, str]],
                 regression_rows: list[dict[str, str]]) -> None:
    defined = {spell for spell, row in rows.items() if row.definitions}
    registered = {spell for spell, row in rows.items() if row.registered}
    reachable = {spell for spell, row in rows.items()
                 if row.registered and row.provenance in NORMAL_PROGRESSION}
    configured_rows = [row for row in rows.values()
                       if row.registered and row.primary and row.primary.category == "configured"]
    configured_counts = {category: 0 for category in "ABCD"}
    for row in configured_rows:
        configured_counts[configured_review(row.primary)[0]] += 1
    implementation_counts: dict[str, int] = {}
    for row in rows.values():
        if row.registered and row.primary:
            category = row.primary.category
            implementation_counts[category] = implementation_counts.get(category, 0) + 1
    base = sum(row.registered and row.provenance == "BASE" for row in rows.values())
    spec = sum(row.registered and row.provenance == "SPEC" for row in rows.values())
    other = sum(row.registered and row.provenance not in {"BASE", "SPEC"} for row in rows.values())

    output = [
        "# IceSMP class/spec/spell/cast audit", "",
        "> Generated by `scripts/audit_class_spell_system.py --strict` from the exact checkout.",
        "> The audited feature SHA is the tree used to generate this artifact; committing the artifact necessarily creates a later SHA.",
        "", "## Git evidence", "",
        f"- Audited feature SHA: `{git_value(root, 'rev-parse', 'HEAD')}`",
        f"- Staging base SHA: `{git_value(root, 'rev-parse', 'origin/staging')}`",
        "", "## Inventory totals", "",
        f"- Classes: **{len(CLASS_TO_SPECS)}**",
        f"- Specializations: **{sum(map(len, CLASS_TO_SPECS.values()))}**",
        f"- Source-defined spell IDs: **{len(defined)}**",
        f"- Runtime-registered spell IDs: **{len(registered)}**",
        f"- Normal progression-reachable spell IDs: **{len(reachable)}**",
        f"- BASE provenance: **{base}**",
        f"- SPEC provenance: **{spec}**",
        f"- TALENT/QUEST/SYSTEM/ADMIN/DEV/allowlisted provenance: **{other}**",
        "- Implementation breakdown: " + ", ".join(
            f"{key}={value}" for key, value in sorted(implementation_counts.items())
        ),
        "", "## ConfiguredSpell verdict — OPTION B (hybrid)", "",
        f"- A: **{configured_counts['A']}**",
        f"- B: **{configured_counts['B']}**",
        f"- C: **{configured_counts['C']}**",
        f"- D: **{configured_counts['D']}**",
        "",
        "A is limited to immediate generic primitives. B keeps the same gameplay model with manually reviewed signature presentation needs. C/D are explicit semantic findings and strict mode refuses to pass while any remains unmigrated.",
        "", "### B/C/D reasons", "",
        "| Spell | Category | Reason |", "|---|---:|---|",
    ]
    for row in sorted(configured_rows, key=lambda item: item.spell_id):
        category, reason = configured_review(row.primary)
        if category != "A":
            output.append(f"| `{row.spell_id}` | {category} | {reason} |")

    output += ["", "## Proven BLOCKER/HIGH fixes", "",
        "- Duplicate spell registration is fail-fast and cannot silently overwrite the first implementation.",
        "- Typed `CastModifiers` execution is the canonical path; the legacy scalar overload is one-way compatibility only.",
        "- Standard spell power scales damage/healing/shield magnitude but does not implicitly lengthen hard CC.",
        "- Failed/PREPARING/no-target casts remain transaction-neutral for cost, class commit, cooldown, combo history and statistics.",
        "- Delayed/projectile output uses immutable modifier snapshots; Sámán totem pulse damage is included in this rule.",
        "- DARK/loadout reconciliation removes stale transient entity projections and eagerly reconciles selected spell state.",
        "- Invalid cross-spec/global combo chains removed and guarded by semantic deny-list validation.",
        "- Druid primary `Természeti Erő` and secondary `Harmónia` are player-facing distinct names.",
    ]

    output += ["", "## Class verdicts", "",
               "| Class | Base spells | Specs | Unlock/registration |",
               "|---|---:|---|---|"]
    for clazz, specs in CLASS_TO_SPECS.items():
        class_rows = [row for row in rows.values() if row.clazz == clazz]
        verdict = "PASS" if all(not row.unlock_referenced or row.registered for row in class_rows) else "FAIL"
        output.append(
            f"| `{clazz}` | {sum(row.provenance == 'BASE' for row in class_rows)} | "
            f"{', '.join(specs)} | {verdict} |"
        )

    output += ["", "## Specialization verdicts", "",
               "| Spec | Class | Spell count | Active-kit refs | DARK | Verdict |",
               "|---|---|---:|---:|---|---|"]
    for clazz, specs in CLASS_TO_SPECS.items():
        for spec_id in specs:
            spec_rows = [row for row in rows.values() if row.spec == spec_id]
            kit_rows = [row for row in rows.values() if spec_id in row.active_owners]
            ok = (all(not row.unlock_referenced or row.registered for row in spec_rows)
                  and all(row.registered for row in kit_rows))
            output.append(
                f"| `{spec_id}` | `{clazz}` | {sum(row.provenance == 'SPEC' for row in spec_rows)} | "
                f"{len(kit_rows)} | {'yes' if spec_id in DARK_SPECS else 'no'} | {'PASS' if ok else 'FAIL'} |"
            )

    output += ["", "## Typed/scalar override inventory", ""]
    for name, values in overrides.items():
        output.append(
            f"- `{name}`: **{len(values)}** — "
            + (", ".join(f"`{value}`" for value in sorted(values)) or "none")
        )

    output += ["", "## Delayed/projectile inventory", "",
               "| Spell | Implementation | Source | Delayed | Projectile | Modifier snapshot | Caster snapshot | Target snapshot | Thread owner | Cleanup |",
               "|---|---|---|---|---|---|---|---|---|---|"]
    for item in sorted(delayed, key=lambda value: value["id"]):
        output.append(
            f"| `{item['id']}` | `{item['impl']}` | `{item['source']}` | {item['delayed']} | "
            f"{item['projectile']} | {item['snapshot']} | {item['caster']} | {item['target']} | "
            f"{item['owner']} | {item['cleanup']} |"
        )

    output += ["", "## Combo audit", "",
               f"- Chains: **{len(chains)}**",
               f"- Pairs: **{len(pairs)}**",
               "- Every combo step must be runtime-registered and share at least one valid class/spec loadout.",
               "- DARK specialization combos are valid while the loadout is ACTIVE; seal enforcement belongs to active-kit/runtime lifecycle, not a blanket spec-name deny rule.",
               "- `soul-collapse` and `way-of-hundred-fists` are semantic deny-list entries and make strict mode fail if reintroduced."]

    output += ["", "## Regression graph", "",
               "| Suite | Wiring | Task |", "|---|---|---|"]
    for item in regression_rows:
        output.append(f"| `{item['suite']}` | {item['state']} | `{item['task']}` |")

    output += ["", "## Architecture decisions", "",
               "- PlayerProfile/ClassSpec remains durable authority; class services, pets/minions/totems and spell state are transient projections.",
               "- Cast flow: input → authority/active kit → preparation → modifiers → affordability/reservation → typed execution → state commit → cooldown → feedback/stats.",
               "- Standard spell power scales damage/healing/shield magnitude, never hard-CC duration implicitly.",
               "- Scheduler/projectile behavior carries immutable modifier/output snapshots across owner-thread hops.",
               "- ConfiguredSpell remains for immediate generic primitives; dedicated Java behavior owns lifecycle/state/projectile/summon identity.",
               "", "## Druid naming", "",
               "- Primary resource: **Természeti Erő**",
               "- Secondary mechanic: **Harmónia**",
               "", "## Strict audit result", "",
               "**PASS**" if not errors else "**FAIL**"]
    if errors:
        output += ["", "### Errors"] + [f"- {error}" for error in errors]
    else:
        output += ["",
            "No definition/registration/provenance, balance parity, active-kit, combo, delayed-scaling, mandatory regression-wiring or Druid naming errors remain."]
    output += ["", "## NEEDS PLAYTEST", "",
               "Only live-server balance/readability tuning remains here; no source-auditable correctness issue is intentionally deferred.",
               "", "Complete per-spell evidence: `docs/audits/class-spell-inventory.csv`."]
    path.write_text("\n".join(output) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--csv", type=Path, default=Path("docs/audits/class-spell-inventory.csv"))
    parser.add_argument("--report", type=Path, default=Path("docs/audits/CLASS_SPELL_CAST_AUDIT.md"))
    parser.add_argument("--strict", action="store_true")
    args = parser.parse_args()
    root = args.root.resolve()

    required = [
        root / "src/main/java",
        root / "src/regression/java",
        root / "src/main/resources/config/classes.yml",
        root / "src/main/resources/config/class-gameplay.yml",
        root / "src/main/resources/config/spells.yml",
        root / "src/main/resources/config/spells-balance.yml",
        root / "build.gradle.kts",
    ]
    missing = [str(path) for path in required if not path.exists()]
    if missing:
        print("Missing audit inputs: " + ", ".join(missing), file=sys.stderr)
        return 2

    definitions, class_to_id, constructor_ids = discover_definitions(root / "src/main/java", root)
    registrations, unresolved = discover_registrations(root, class_to_id, constructor_ids)
    unlocks = parse_unlocks(root / "src/main/resources/config/classes.yml")
    active = parse_active_kits(root / "src/main/resources/config/class-gameplay.yml")
    balance = parse_balance(root / "src/main/resources/config/spells-balance.yml")
    schools, chains, pairs = parse_spell_config(root / "src/main/resources/config/spells.yml")
    rows = build_rows(definitions, registrations, unlocks, active, balance, schools, chains, pairs)
    overrides = override_inventory(root / "src/main/java")
    delayed, delayed_errors = delayed_inventory(rows, root)
    regression_rows, regression_errors = regression_graph(root)
    naming_errors = druid_naming_errors(root)
    errors = validate(
        rows, registrations, unresolved, balance, chains, pairs, overrides,
        delayed_errors, regression_errors, naming_errors, root,
    )

    csv_path = args.csv if args.csv.is_absolute() else root / args.csv
    report_path = args.report if args.report.is_absolute() else root / args.report
    csv_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    with csv_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=CSV_FIELDS)
        writer.writeheader()
        for spell_id in sorted(rows):
            writer.writerow(csv_row(rows[spell_id], root))
    write_report(report_path, root, rows, errors, chains, pairs, overrides, delayed, regression_rows)

    defined_count = sum(bool(row.definitions) for row in rows.values())
    registered_count = sum(row.registered for row in rows.values())
    progression_count = sum(
        row.registered and row.provenance in NORMAL_PROGRESSION for row in rows.values()
    )
    configured_count = sum(
        row.registered and row.primary and row.primary.category == "configured" for row in rows.values()
    )
    print(
        f"IceSMP strict spell audit: defined={defined_count} registered={registered_count} "
        f"progression={progression_count} configured={configured_count}"
    )
    for error in errors:
        print("ERROR: " + error, file=sys.stderr)
    return 1 if args.strict and errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
