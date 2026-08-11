#!/usr/bin/env python3
"""Exact class/spec/spell audit for IceSMP.

This audit deliberately separates source definitions from runtime registration.
A constructor or builder proves DEFINED only; REGISTERED requires a reachable
SpellRegistry.register(...) call in the startup registration authorities.

The script is standard-library only so it can run under Gradle/CI and from a
plain Java 21 developer checkout without provisioning Python packages.
"""
from __future__ import annotations

import argparse
import csv
import re
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable

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
PROGRESSION_PROVENANCE = {"BASE", "SPEC", "TALENT", "QUEST"}

# Explicit non-level provenance. Keep this small and reviewable; a registered id
# with no BASE/SPEC unlock and no entry here is a strict-audit error.
EXPLICIT_PROVENANCE = {
    "talent_surge": ("TALENT", "Talent tree spell registered by SpellCatalog.registerTalentSpells"),
}

# Manual semantic presentation review. These remain ConfiguredSpell because the
# actual gameplay payload is immediate/generic, but their class-signature fantasy
# warrants distinct presentation. The audit only applies entries that are actually
# ConfiguredSpell in the exact tree, so stale names cannot inflate counts.
SIGNATURE_PRESENTATION_IDS = {
    "gravity_well", "elemental_overload", "berserk", "last_stand",
    "masterful_shot", "king_of_beasts", "deathcap", "spectre",
    "celestial_alignment", "avenging_wrath", "final_verdict",
    "breath_of_sindragosa", "ascendance_flame", "doom_winds", "serenity",
    "invoke_niuzao", "evangelism", "void_eruption", "darkglare",
    "metamorphosis_havoc", "the_hunt", "metamorphosis_veng", "dragonrage",
    "eternity_breath", "rewind", "incarnation_bear", "tranquility",
    "spirit_tide", "revival", "final_stand",
}

# C/D are explicit human findings, never inferred merely from builder use. Strict
# mode fails while one remains here, forcing migration or an explicit review change.
CONFIGURED_IDENTITY_FINDINGS: dict[str, tuple[str, str]] = {}

# Semantic combo findings cannot be inferred reliably from regex alone. These are
# known invalid/native-duplicate chains and are rejected if they reappear.
FORBIDDEN_COMBO_NAMES = {
    "soul-collapse": "Affliction/Destruction cross-spec chain has no valid active loadout",
    "way-of-hundred-fists": "duplicates the Monk native martial-chain reward",
}

CSV_FIELDS = [
    "id", "display_name", "class", "spec", "provenance",
    "defined", "registered", "unlock_referenced", "active_kit_referenced",
    "combo_referenced", "balance_configured", "implementation_class",
    "implementation_category", "configured_spell_category",
    "configured_spell_category_reason", "unlock_level", "active_kit",
    "balance_entry", "spell_school", "combo_reference", "targeting",
    "cooldown", "effective_cost", "damage", "healing", "cc", "mobility",
    "summon", "delayed", "projectile", "scaling_path", "regression_coverage",
    "definition_location", "registration_location",
]

@dataclass
class Definition:
    spell_id: str
    display_name: str
    implementation_class: str
    implementation_category: str
    source_path: str
    line: int
    cooldown: str = ""
    cost_type: str = ""
    cost_amount: str = ""
    class_id: str = ""
    spec_id: str = ""
    details: dict[str, str] = field(default_factory=dict)
    configured_block: str = ""

@dataclass
class SpellRow:
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
    class_id: str = ""
    spec_id: str = ""
    unlock_level: str = ""
    active_kit_owners: set[str] = field(default_factory=set)
    combo_names: set[str] = field(default_factory=set)
    balance: dict[str, str] = field(default_factory=dict)
    school: str = ""

    @property
    def primary(self) -> Definition | None:
        if not self.definitions:
            return None
        configured = [d for d in self.definitions if d.implementation_category == "configured"]
        return configured[0] if configured else self.definitions[0]


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def clean_scalar(value: str) -> str:
    value = value.strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in {'"', "'"}:
        return value[1:-1]
    return value


def line_no(text: str, index: int) -> int:
    return text.count("\n", 0, index) + 1


def snake_case(name: str) -> str:
    return re.sub(r"([a-z0-9])([A-Z])", r"\1_\2", name).lower()


def extract_method(text: str, method_name: str) -> tuple[int, int, str] | None:
    match = re.search(r"\b(?:public|private|protected)\s+(?:static\s+)?[\w<>?, .\[\]]+\s+"
                      + re.escape(method_name) + r"\s*\([^)]*\)\s*\{", text)
    if not match:
        return None
    brace = text.find("{", match.start())
    depth = 0
    in_string = False
    escape = False
    for index in range(brace, len(text)):
        char = text[index]
        if in_string:
            if escape:
                escape = False
            elif char == "\\":
                escape = True
            elif char == '"':
                in_string = False
            continue
        if char == '"':
            in_string = True
        elif char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return match.start(), index + 1, text[match.start():index + 1]
    return None


def extract_register_calls(text: str, receiver_pattern: str = r"(?:spellRegistry|registry)") -> list[tuple[int, str]]:
    calls: list[tuple[int, str]] = []
    pattern = re.compile(receiver_pattern + r"\.register\s*\(")
    for match in pattern.finditer(text):
        open_paren = text.find("(", match.start())
        depth = 0
        in_string = False
        escape = False
        for index in range(open_paren, len(text)):
            char = text[index]
            if in_string:
                if escape:
                    escape = False
                elif char == "\\":
                    escape = True
                elif char == '"':
                    in_string = False
                continue
            if char == '"':
                in_string = True
            elif char == "(":
                depth += 1
            elif char == ")":
                depth -= 1
                if depth == 0:
                    calls.append((match.start(), text[match.start():index + 1]))
                    break
    return calls


def owner_from_register_method(method: str) -> tuple[str, str]:
    token = snake_case(method.removeprefix("register"))
    if token in CLASS_TO_SPECS:
        return token, ""
    if token in SPEC_TO_CLASS:
        return SPEC_TO_CLASS[token], token
    return "", ""


def classify_impl(name: str, text: str) -> str:
    lowered = name.lower()
    if name == "ConfiguredSpell":
        return "configured"
    if "form" in lowered:
        return "form"
    if "summon" in lowered or "minion" in lowered:
        return "summon"
    if "projectile" in lowered or "launchProjectile" in text or "Projectile" in text:
        return "projectile"
    if "runDelayed" in text or "runAtFixedRate" in text or "clearPlayerState" in text:
        return "stateful"
    return "dedicated"


def parse_effect_details(block: str) -> dict[str, str]:
    out: dict[str, str] = {}
    if ".target(" in block:
        out["targeting"] = "TARGET"
    elif ".aoe(" in block:
        out["targeting"] = "AOE"
    else:
        out["targeting"] = "SELF"
    damage = re.search(r"\.damage\(([^)]+)\)", block)
    if damage:
        out["damage"] = damage.group(1).strip()
    heal = re.search(r"\.healSelf\(([^)]+)\)", block)
    if heal:
        out["healing"] = heal.group(1).strip()
    effects = re.findall(r"targetEffect\(PotionEffectType\.([A-Z_]+)", block)
    hard = [effect for effect in effects if effect in {"SLOWNESS", "BLINDNESS", "LEVITATION", "JUMP_BOOST"}]
    freeze = re.search(r"\.freeze\(([^)]+)\)", block)
    if hard or freeze:
        values = list(hard)
        if freeze:
            values.append("FREEZE")
        out["cc"] = ",".join(values)
    if ".dash(" in block:
        out["mobility"] = "dash"
    if any(token in block for token in (".knockback(", ".pull(", ".launchUp(")):
        out["mobility"] = (out.get("mobility", "") + "+displacement").strip("+")
    return out


def discover_definitions(java_root: Path) -> tuple[dict[str, list[Definition]], dict[str, str]]:
    definitions: dict[str, list[Definition]] = {}
    class_to_id: dict[str, str] = {}
    for path in java_root.rglob("*.java"):
        text = read(path)
        rel = path.relative_to(java_root.parents[2]).as_posix()
        class_match = re.search(r"(?:public\s+)?(?:final\s+)?class\s+(\w+)", text)
        impl = class_match.group(1) if class_match else path.stem

        # Dedicated BaseSpell subclasses: constructor super(...) proves definition only.
        for match in re.finditer(
                r"super\([^;]*?\"([^\"]+)\",\s*\"([^\"]+)\",\s*([^,]+),\s*"
                r"SpellCostType\.([A-Z]+),\s*([^)]+)\)", text, re.S):
            sid, name, cooldown, cost_type, cost_amount = match.groups()
            definition = Definition(
                sid, name, impl, classify_impl(impl, text), rel, line_no(text, match.start()),
                cooldown.strip(), cost_type, cost_amount.strip())
            definitions.setdefault(sid, []).append(definition)
            class_to_id[impl] = sid

        # Catalog/generic definitions carry literal ids in builder/constructor calls.
        method_matches = list(re.finditer(r"private static void (register[A-Za-z0-9_]+)\s*\(", text))
        method_spans: list[tuple[int, int, str]] = []
        for index, method_match in enumerate(method_matches):
            end = method_matches[index + 1].start() if index + 1 < len(method_matches) else len(text)
            method_spans.append((method_match.start(), end, method_match.group(1)))

        def context_owner(position: int) -> tuple[str, str]:
            for start, end, method in method_spans:
                if start <= position < end:
                    return owner_from_register_method(method)
            return "", ""

        pattern = re.compile(
            r"ConfiguredSpell\.builder\([^,]+,\s*\"([^\"]+)\",\s*\"([^\"]+)\",\s*([^,]+),\s*"
            r"SpellCostType\.([A-Z]+),\s*([^)]+)\)(.*?)(?:\.build\(\))", re.S)
        for match in pattern.finditer(text):
            sid, name, cooldown, cost_type, cost_amount, tail = match.groups()
            clazz, spec = context_owner(match.start())
            definition = Definition(
                sid, name, "ConfiguredSpell", "configured", rel, line_no(text, match.start()),
                cooldown.strip(), cost_type, cost_amount.strip(), clazz, spec,
                parse_effect_details(tail), match.group(0))
            definitions.setdefault(sid, []).append(definition)

        for generic in ("ProjectileBurstSpell", "BlinkSpell", "SummonMinionsSpell"):
            for match in re.finditer(r"new\s+" + generic + r"\s*\((.*?)\)", text, re.S):
                literals = re.findall(r"\"([^\"]+)\"", match.group(1))
                if len(literals) < 2:
                    continue
                sid, name = literals[0], literals[1]
                clazz, spec = context_owner(match.start())
                category = "projectile" if generic == "ProjectileBurstSpell" else (
                    "summon" if generic == "SummonMinionsSpell" else "dedicated")
                definition = Definition(
                    sid, name, generic, category, rel, line_no(text, match.start()),
                    class_id=clazz, spec_id=spec,
                    details={"projectile": "yes"} if category == "projectile" else
                            ({"summon": "yes"} if category == "summon" else {"mobility": "blink"}))
                definitions.setdefault(sid, []).append(definition)
    return definitions, class_to_id


def reachable_catalog_methods(text: str) -> set[str]:
    reachable: set[str] = {"registerExpansionSpells", "registerSummonSpells"}
    changed = True
    while changed:
        changed = False
        for method in list(reachable):
            span = extract_method(text, method)
            if not span:
                continue
            body = span[2]
            for called in re.findall(r"\b(register[A-Z][A-Za-z0-9_]*)\s*\(", body):
                if called not in reachable:
                    reachable.add(called)
                    changed = True
    return reachable


def resolve_registration_expression(expression: str, class_to_id: dict[str, str]) -> str | None:
    configured = re.search(r"ConfiguredSpell\.builder\([^,]+,\s*\"([^\"]+)\"", expression)
    if configured:
        return configured.group(1)
    constructor = re.search(r"new\s+(\w+)\s*\(", expression)
    if constructor:
        impl = constructor.group(1)
        if impl in class_to_id:
            return class_to_id[impl]
        args = expression[constructor.end():]
        literals = re.findall(r"\"([^\"]+)\"", args)
        if literals:
            return literals[0]
    return None


def discover_runtime_registrations(root: Path, class_to_id: dict[str, str]) -> tuple[dict[str, list[str]], list[str]]:
    registrations: dict[str, list[str]] = {}
    unresolved: list[str] = []
    authorities = [
        root / "src/main/java/hu/taliann/icesmp/core/IceSMPCore.java",
        root / "src/main/java/hu/taliann/icesmp/spells/SpellCatalog.java",
    ]
    for path in authorities:
        text = read(path)
        rel = path.relative_to(root).as_posix()
        allowed_ranges: list[tuple[int, int]] = []
        if path.name == "IceSMPCore.java":
            span = extract_method(text, "registerSpells")
            if span:
                allowed_ranges.append((span[0], span[1]))
        else:
            for method in reachable_catalog_methods(text):
                span = extract_method(text, method)
                if span:
                    allowed_ranges.append((span[0], span[1]))
        for position, expression in extract_register_calls(text):
            if not any(start <= position < end for start, end in allowed_ranges):
                continue
            sid = resolve_registration_expression(expression, class_to_id)
            location = f"{rel}:{line_no(text, position)}"
            if sid is None:
                unresolved.append(f"{location}: {expression[:120].replace(chr(10), ' ')}")
                continue
            registrations.setdefault(sid, []).append(location)
    return registrations, unresolved


def parse_unlocks(path: Path) -> dict[str, tuple[str, str, int]]:
    unlocks: dict[str, tuple[str, str, int]] = {}
    stack: list[tuple[int, str]] = []
    for raw in read(path).splitlines():
        clean = raw.split("#", 1)[0].rstrip()
        if not clean.strip() or ":" not in clean:
            continue
        indent = len(clean) - len(clean.lstrip(" "))
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
            unlocks[key] = (owner, "", level)
        elif keys[0] == "specializations" and owner in SPEC_TO_CLASS:
            unlocks[key] = (SPEC_TO_CLASS[owner], owner, level)
    return unlocks


def parse_active_kits(path: Path) -> dict[str, set[str]]:
    active: dict[str, set[str]] = {}
    stack: list[tuple[int, str]] = []
    current_owner = ""
    for raw in read(path).splitlines():
        clean = raw.split("#", 1)[0].rstrip()
        if not clean.strip():
            continue
        indent = len(clean) - len(clean.lstrip(" "))
        stripped = clean.strip()
        while stack and stack[-1][0] >= indent:
            stack.pop()
        if stripped.startswith("-"):
            if current_owner:
                active.setdefault(current_owner, set()).add(clean_scalar(stripped[1:].strip()))
            continue
        if ":" not in stripped:
            continue
        key, value = stripped.split(":", 1)
        key, value = key.strip(), value.strip()
        stack.append((indent, key))
        keys = [part for _, part in stack]
        current_owner = ""
        if not value and "active-kit" in keys and key in SPEC_TO_CLASS:
            current_owner = key
    return active


def parse_balance(path: Path) -> dict[str, dict[str, str]]:
    entries: dict[str, dict[str, str]] = {}
    lines = read(path).splitlines()
    in_root = False
    current = ""
    for raw in lines:
        clean = raw.split("#", 1)[0].rstrip()
        if not clean.strip():
            continue
        indent = len(clean) - len(clean.lstrip(" "))
        stripped = clean.strip()
        if indent == 0:
            in_root = stripped == "spell-balance:"
            current = ""
            continue
        if not in_root:
            continue
        if indent == 2 and stripped.endswith(":"):
            current = stripped[:-1].strip()
            entries.setdefault(current, {})
        elif indent >= 4 and current and ":" in stripped:
            key, value = stripped.split(":", 1)
            entries[current][key.strip()] = clean_scalar(value.strip())
    return entries


def parse_spell_config(path: Path) -> tuple[dict[str, str], dict[str, list[str]], dict[str, tuple[str, str]]]:
    text = read(path)
    schools: dict[str, str] = {}
    combos: dict[str, list[str]] = {}
    pairs: dict[str, tuple[str, str]] = {}
    # by-spell is the only school map whose keys are spell ids.
    school_match = re.search(r"(?ms)^\s*by-spell:\s*\n(.*?)(?=^\s{2}\S|\Z)", text)
    if school_match:
        for match in re.finditer(r"(?m)^\s{6}([a-z0-9_:-]+):\s*([a-z0-9_:-]+)\s*$", school_match.group(1)):
            schools[match.group(1)] = match.group(2)
    chain_match = re.search(r"(?ms)^\s{4}chains:\s*\n(.*?)(?=^\s{4}pairs:|\Z)", text)
    if chain_match:
        current = ""
        for raw in chain_match.group(1).splitlines():
            stripped = raw.split("#", 1)[0].strip()
            indent = len(raw) - len(raw.lstrip(" "))
            if indent == 6 and stripped.endswith(":"):
                current = stripped[:-1]
            elif current and stripped.startswith("steps:"):
                value = stripped.split(":", 1)[1].strip()
                if value.startswith("[") and value.endswith("]"):
                    combos[current] = [clean_scalar(x.strip()) for x in value[1:-1].split(",") if x.strip()]
    pair_match = re.search(r"(?ms)^\s{4}pairs:\s*\n(.*?)(?=^\S|\Z)", text)
    if pair_match:
        current = ""
        first = ""
        for raw in pair_match.group(1).splitlines():
            stripped = raw.split("#", 1)[0].strip()
            indent = len(raw) - len(raw.lstrip(" "))
            if indent == 6 and stripped.endswith(":"):
                current, first = stripped[:-1], ""
            elif current and stripped.startswith("first:"):
                first = clean_scalar(stripped.split(":", 1)[1])
            elif current and stripped.startswith("second:") and first:
                pairs[current] = (first, clean_scalar(stripped.split(":", 1)[1]))
    return schools, combos, pairs


def valid_loadouts(row: SpellRow) -> set[tuple[str, str]]:
    if not row.class_id:
        return {(clazz, spec) for clazz, specs in CLASS_TO_SPECS.items() for spec in specs}
    if row.spec_id:
        return {(row.class_id, row.spec_id)}
    return {(row.class_id, spec) for spec in CLASS_TO_SPECS[row.class_id]}


def configured_review(definition: Definition) -> tuple[str, str]:
    explicit = CONFIGURED_IDENTITY_FINDINGS.get(definition.spell_id)
    if explicit:
        return explicit
    block = definition.configured_block
    forbidden_lifecycle = ("runDelayed", "runAtFixedRate", "launchProjectile", "PersistentDataContainer",
                           "targetMemory", "channel", "chargeSession")
    if any(token in block for token in forbidden_lifecycle):
        return "D", "Configured payload contains lifecycle/state behavior that requires a dedicated Java implementation"
    if definition.spell_id in SIGNATURE_PRESENTATION_IDS:
        return "B", "Immediate generic gameplay is sufficient; class-signature presentation should stay visually distinctive"
    targeting = definition.details.get("targeting", "SELF")
    outputs = [name for name in ("damage", "healing", "cc", "mobility") if definition.details.get(name)]
    summary = ", ".join(outputs) if outputs else "buff/debuff/utility"
    return "A", f"Immediate {targeting} {summary} primitive; no scheduler, projectile lifecycle, target memory or persistent state"


def scan_override_inventory(java_root: Path) -> dict[str, list[str]]:
    inventory = {name: [] for name in ("execute", "executeSpell", "executeSpellScalar", "executeCast", "cast")}
    patterns = {
        "execute": r"\bvoid\s+execute\s*\(\s*final?\s*Player\s+\w+\s*\)",
        "executeSpell": r"\bboolean\s+executeSpell\s*\(\s*final?\s*Player\s+\w+\s*\)",
        "executeSpellScalar": r"\bboolean\s+executeSpell\s*\(\s*final?\s*Player\s+\w+\s*,\s*final?\s*double\s+\w+\s*\)",
        "executeCast": r"\bCastOutcome\s+executeCast\s*\(\s*final?\s*Player\s+\w+\s*\)",
        "cast": r"\bCastOutcome\s+cast\s*\(\s*final?\s*Player\s+\w+\s*,\s*final?\s*CastModifiers\s+\w+\s*\)",
    }
    for path in java_root.rglob("*.java"):
        if path.name == "Spell.java":
            continue
        text = read(path)
        rel = path.relative_to(java_root.parents[2]).as_posix()
        for name, pattern in patterns.items():
            if re.search(pattern, text):
                inventory[name].append(rel)
    return inventory


def scan_delayed_projectiles(rows: dict[str, SpellRow], root: Path) -> tuple[list[dict[str, str]], list[str]]:
    inventory: list[dict[str, str]] = []
    errors: list[str] = []
    for row in rows.values():
        definition = row.primary
        if not row.registered or definition is None:
            continue
        path = root / definition.source_path
        if not path.is_file():
            continue
        text = read(path)
        delayed = "runDelayed" in text or "runAtFixedRate" in text
        projectile = "launchProjectile" in text or "Projectile" in text or "teleportAsync" in text
        if not delayed and not projectile:
            continue
        needs_scaled_snapshot = delayed and any(token in text for token in (
            "SpellDamageUtil", "SpellHealingUtil", ".damage(", "setHealth("))
        snapshot = any(token in text for token in (
            "SpellExecutionContext.capture()", "SpellDamageUtil.markProjectile", "CastModifiers modifiers"))
        owner = "entity/player scheduler" if "getScheduler()" in text else (
            "global/region scheduler" if "Scheduler" in text else "synchronous projectile event")
        inventory.append({
            "id": row.spell_id,
            "implementation": definition.implementation_class,
            "delayed": "yes" if delayed else "no",
            "projectile": "yes" if projectile else "no",
            "modifier_snapshot": "yes" if snapshot else ("not required" if not needs_scaled_snapshot else "MISSING"),
            "caster_snapshot": "UUID/immutable" if "UUID" in text else "same owner thread",
            "target_snapshot": "entity/UUID bounded" if ("UUID" in text or "LivingEntity" in text) else "n/a",
            "thread_owner": owner,
            "cleanup": "explicit" if any(token in text for token in ("remove()", "task.cancel()", "clearPlayerState")) else "event-owned",
        })
        if needs_scaled_snapshot and not snapshot:
            errors.append(f"delayed scaled spell lacks immutable CastModifiers snapshot: {row.spell_id} ({definition.source_path})")
    return inventory, errors


def regression_graph(root: Path) -> tuple[list[dict[str, str]], list[str]]:
    regression_root = root / "src/regression/java"
    build = read(root / "build.gradle.kts")
    registrations: dict[str, str] = {}
    for match in re.finditer(
            r"val\s+(\w+)\s*=\s*registerRegression\(\s*\"([^\"]+)\"\s*,.*?\"([^\"]+RegressionSuite)\"\s*\)",
            build, re.S):
        variable, task, fqcn = match.groups()
        registrations[fqcn] = variable or task
    check_text = build[build.find("tasks.check"):]
    suite_sources: dict[str, tuple[Path, str]] = {}
    for path in regression_root.rglob("*RegressionSuite.java"):
        text = read(path)
        package = re.search(r"package\s+([\w.]+);", text)
        clazz = re.search(r"class\s+(\w+RegressionSuite)\b", text)
        if package and clazz:
            suite_sources[f"{package.group(1)}.{clazz.group(1)}"] = (path, text)
    delegated: set[str] = set()
    simple_to_fqcn = {fqcn.rsplit(".", 1)[-1]: fqcn for fqcn in suite_sources}
    for _, text in suite_sources.values():
        for simple, fqcn in simple_to_fqcn.items():
            if re.search(r"\b" + re.escape(simple) + r"\.main\s*\(", text):
                delegated.add(fqcn)
    rows: list[dict[str, str]] = []
    errors: list[str] = []
    mandatory_prefixes = (
        "hu.taliann.icesmp.warrior.", "hu.taliann.icesmp.evoker.", "hu.taliann.icesmp.archer.",
        "hu.taliann.icesmp.shaman.", "hu.taliann.icesmp.monk.", "hu.taliann.icesmp.paladin.",
        "hu.taliann.icesmp.demonhunter.", "hu.taliann.icesmp.druid.", "hu.taliann.icesmp.priest.",
        "hu.taliann.icesmp.deathknight.", "hu.taliann.icesmp.assassin.", "hu.taliann.icesmp.warlock.",
        "hu.taliann.icesmp.wizard.", "hu.taliann.icesmp.classspec.", "hu.taliann.icesmp.playerprofile.",
        "hu.taliann.icesmp.spells.", "hu.taliann.icesmp.hud.", "hu.taliann.icesmp.lifecycle.",
        "hu.taliann.icesmp.config.",
    )
    for fqcn, (path, _) in sorted(suite_sources.items()):
        task_variable = registrations.get(fqcn, "")
        check_wired = bool(task_variable and task_variable in check_text)
        delegated_only = fqcn in delegated and not task_variable
        mandatory = fqcn.startswith(mandatory_prefixes)
        state = "TASK+CHECK" if check_wired else ("TASK_ONLY" if task_variable else ("DELEGATED" if delegated_only else "ORPHAN"))
        rows.append({"suite": fqcn, "state": state, "task": task_variable, "path": path.relative_to(root).as_posix()})
        if mandatory and state in {"TASK_ONLY", "ORPHAN"}:
            errors.append(f"mandatory regression suite is not check-wired/delegated: {fqcn} ({state})")
    for required in ("hu.taliann.icesmp.wizard.WizardGameplayRegressionSuite",
                     "hu.taliann.icesmp.wizard.WizardProfileRegressionSuite",
                     "hu.taliann.icesmp.spells.SpellCastArchitectureRegressionSuite",
                     "hu.taliann.icesmp.spells.ClassSpellAuditRegressionSuite"):
        state = next((row["state"] for row in rows if row["suite"] == required), "MISSING")
        if state != "TASK+CHECK":
            errors.append(f"required explicit Gradle regression task is not in check: {required} ({state})")
    return rows, errors


def git_value(root: Path, *args: str) -> str:
    try:
        return subprocess.check_output(["git", *args], cwd=root, text=True, stderr=subprocess.DEVNULL).strip()
    except Exception:
        return "unknown"


def scan_druid_naming(root: Path) -> list[str]:
    findings: list[str] = []
    roots = [root / "src/main", root / "docs"]
    for base in roots:
        if not base.exists():
            continue
        for path in base.rglob("*"):
            if not path.is_file() or path.suffix.lower() not in {".java", ".yml", ".yaml", ".md", ".json", ".properties"}:
                continue
            try:
                text = read(path)
            except UnicodeDecodeError:
                continue
            for index, line in enumerate(text.splitlines(), 1):
                low = line.lower()
                if "harmony" in low and "természeti erő" in low:
                    findings.append(f"{path.relative_to(root)}:{index}: {line.strip()}")
    return findings


def build_rows(definitions: dict[str, list[Definition]], registrations: dict[str, list[str]],
               unlocks: dict[str, tuple[str, str, int]], active_kits: dict[str, set[str]],
               balance: dict[str, dict[str, str]], schools: dict[str, str],
               combos: dict[str, list[str]], pairs: dict[str, tuple[str, str]]) -> dict[str, SpellRow]:
    all_ids = set(definitions) | set(registrations) | set(unlocks) | set(balance)
    all_ids |= {sid for values in active_kits.values() for sid in values}
    all_ids |= {sid for steps in combos.values() for sid in steps}
    all_ids |= {sid for pair in pairs.values() for sid in pair}
    rows = {sid: SpellRow(sid, definitions=list(definitions.get(sid, []))) for sid in all_ids}
    for sid, locations in registrations.items():
        rows[sid].registered = True
        rows[sid].registration_locations.extend(locations)
    for sid, (clazz, spec, level) in unlocks.items():
        row = rows[sid]
        row.unlock_referenced = True
        row.class_id, row.spec_id, row.unlock_level = clazz, spec, str(level)
        row.provenance = "SPEC" if spec else "BASE"
        row.provenance_reason = f"classes.yml spell-unlocks ({spec or clazz})"
    for provenance_id, (provenance, reason) in EXPLICIT_PROVENANCE.items():
        if provenance_id in rows and not rows[provenance_id].provenance:
            rows[provenance_id].provenance = provenance
            rows[provenance_id].provenance_reason = reason
    for owner, ids in active_kits.items():
        for sid in ids:
            rows[sid].active_kit_referenced = True
            rows[sid].active_kit_owners.add(owner)
    for name, steps in combos.items():
        for sid in steps:
            rows[sid].combo_referenced = True
            rows[sid].combo_names.add(name)
    for name, pair in pairs.items():
        for sid in pair:
            rows[sid].combo_referenced = True
            rows[sid].combo_names.add(name)
    for sid, entry in balance.items():
        rows[sid].balance_configured = True
        rows[sid].balance = entry
    for sid, school in schools.items():
        if sid in rows:
            rows[sid].school = school
    for row in rows.values():
        definition = row.primary
        if definition and not row.class_id:
            row.class_id, row.spec_id = definition.class_id, definition.spec_id
    return rows


def validate(rows: dict[str, SpellRow], registrations: dict[str, list[str]],
             unresolved: list[str], balance: dict[str, dict[str, str]],
             active_kits: dict[str, set[str]], combos: dict[str, list[str]],
             pairs: dict[str, tuple[str, str]], override_inventory: dict[str, list[str]],
             delayed_errors: list[str], regression_errors: list[str], druid_naming: list[str],
             root: Path) -> list[str]:
    errors: list[str] = []
    errors.extend(f"unresolved runtime registration: {item}" for item in unresolved)
    registered = set(registrations)
    balance_ids = set(balance)
    for sid in sorted(registered - balance_ids):
        errors.append(f"registered spell has no spells-balance.yml entry: {sid}")
    for sid in sorted(balance_ids - registered):
        errors.append(f"dead spells-balance.yml entry is not registered: {sid}")
    for sid, row in sorted(rows.items()):
        if row.registered and not row.definitions:
            errors.append(f"runtime registration has no source definition: {sid}")
        if row.unlock_referenced and not row.registered:
            errors.append(f"unlock references non-registered spell: {sid}")
        if row.active_kit_referenced and not row.registered:
            errors.append(f"active-kit references non-registered spell: {sid}")
        if row.combo_referenced and not row.registered:
            errors.append(f"combo references non-registered spell: {sid}")
        if row.registered and not row.provenance:
            errors.append(f"registered spell has no explicit provenance: {sid}")
        if row.active_kit_referenced and row.spec_id:
            invalid_owners = sorted(owner for owner in row.active_kit_owners if owner != row.spec_id)
            if invalid_owners:
                errors.append(f"cross-spec active-kit leakage for {sid}: {', '.join(invalid_owners)}")
        definition = row.primary
        if definition and definition.implementation_category == "configured":
            category, reason = configured_review(definition)
            if category in {"C", "D"}:
                errors.append(f"ConfiguredSpell {category} finding not migrated: {sid}: {reason}")
    for name, steps in combos.items():
        missing = [sid for sid in steps if sid not in registered]
        if missing:
            errors.append(f"combo chain {name} references non-registered spell(s): {', '.join(missing)}")
            continue
        common: set[tuple[str, str]] | None = None
        for sid in steps:
            loadouts = valid_loadouts(rows[sid])
            common = loadouts if common is None else common & loadouts
        if not common:
            errors.append(f"impossible combo chain {name}: {' -> '.join(steps)}")
        if name in FORBIDDEN_COMBO_NAMES:
            errors.append(f"forbidden semantic combo returned: {name}: {FORBIDDEN_COMBO_NAMES[name]}")
    for name, pair in pairs.items():
        missing = [sid for sid in pair if sid not in registered]
        if missing:
            errors.append(f"combo pair {name} references non-registered spell(s): {', '.join(missing)}")
            continue
        common = valid_loadouts(rows[pair[0]]) & valid_loadouts(rows[pair[1]])
        if not common:
            errors.append(f"impossible combo pair {name}: {pair[0]} -> {pair[1]}")
        if name in FORBIDDEN_COMBO_NAMES:
            errors.append(f"forbidden semantic combo returned: {name}: {FORBIDDEN_COMBO_NAMES[name]}")
    errors.extend(delayed_errors)
    errors.extend(regression_errors)
    errors.extend(f"Druid Harmony naming collision: {finding}" for finding in druid_naming)

    scalar = override_inventory["executeSpellScalar"]
    if scalar != ["src/main/java/hu/taliann/icesmp/spells/ConfiguredSpell.java"]:
        errors.append("executeSpell(Player,double) override inventory changed; expected ConfiguredSpell only: " + ", ".join(scalar))
    spell_source = read(root / "src/main/java/hu/taliann/icesmp/spells/Spell.java")
    configured_source = read(root / "src/main/java/hu/taliann/icesmp/spells/ConfiguredSpell.java")
    if "return cast(player, CastModifiers.standardPower(powerMultiplier)).effectApplied();" not in spell_source:
        errors.append("Spell scalar compatibility bridge no longer delegates one-way to typed cast")
    if "final CastOutcome outcome = executeCast(player);" not in spell_source:
        errors.append("Spell.cast no longer has executeCast as the single canonical typed path")
    if "return cast(player, CastModifiers.standardPower(power)).effectApplied();" not in configured_source:
        errors.append("ConfiguredSpell scalar override no longer delegates one-way to typed cast")
    if "effect.getDuration() * power" in configured_source:
        errors.append("ConfiguredSpell generic power still scales effect/CC duration")
    registry_source = read(root / "src/main/java/hu/taliann/icesmp/managers/SpellRegistry.java")
    if "putIfAbsent" not in registry_source or "Duplicate spell id" not in registry_source:
        errors.append("SpellRegistry duplicate fail-fast contract missing")
    return errors


def row_to_csv(row: SpellRow) -> dict[str, str]:
    definition = row.primary
    category = reason = ""
    if definition and definition.implementation_category == "configured":
        category, reason = configured_review(definition)
    balance_cost = row.balance.get("resource-cost") or row.balance.get("cost-amount") if row.balance else ""
    details = definition.details if definition else {}
    return {
        "id": row.spell_id,
        "display_name": definition.display_name if definition else "",
        "class": row.class_id,
        "spec": row.spec_id,
        "provenance": row.provenance,
        "defined": "yes" if row.definitions else "no",
        "registered": "yes" if row.registered else "no",
        "unlock_referenced": "yes" if row.unlock_referenced else "no",
        "active_kit_referenced": "yes" if row.active_kit_referenced else "no",
        "combo_referenced": "yes" if row.combo_referenced else "no",
        "balance_configured": "yes" if row.balance_configured else "no",
        "implementation_class": definition.implementation_class if definition else "",
        "implementation_category": definition.implementation_category if definition else "",
        "configured_spell_category": category,
        "configured_spell_category_reason": reason,
        "unlock_level": row.unlock_level,
        "active_kit": ",".join(sorted(row.active_kit_owners)),
        "balance_entry": "yes" if row.balance_configured else "no",
        "spell_school": row.school or "class default / primordial",
        "combo_reference": ",".join(sorted(row.combo_names)),
        "targeting": details.get("targeting", "implementation-defined"),
        "cooldown": row.balance.get("cooldown", definition.cooldown if definition else ""),
        "effective_cost": balance_cost or (definition.cost_amount if definition else ""),
        "damage": row.balance.get("damage", details.get("damage", "")),
        "healing": row.balance.get("heal-self", details.get("healing", "")),
        "cc": details.get("cc", ""),
        "mobility": details.get("mobility", ""),
        "summon": "yes" if definition and definition.implementation_category == "summon" else "no",
        "delayed": "yes" if definition and definition.implementation_category in {"stateful", "summon"} else "no",
        "projectile": "yes" if definition and definition.implementation_category == "projectile" else "no",
        "scaling_path": "CastModifiers -> SpellExecutionContext/shared output primitives",
        "regression_coverage": "class/profile + SpellCastArchitecture + strict source graph",
        "definition_location": ";".join(f"{d.source_path}:{d.line}" for d in row.definitions),
        "registration_location": ";".join(row.registration_locations),
    }


def write_report(path: Path, root: Path, rows: dict[str, SpellRow], errors: list[str],
                 combos: dict[str, list[str]], pairs: dict[str, tuple[str, str]],
                 overrides: dict[str, list[str]], delayed_inventory: list[dict[str, str]],
                 regression_rows: list[dict[str, str]], druid_naming: list[str]) -> None:
    defined = {sid for sid, row in rows.items() if row.definitions}
    registered = {sid for sid, row in rows.items() if row.registered}
    reachable = {sid for sid, row in rows.items() if row.registered and row.provenance in PROGRESSION_PROVENANCE}
    configured_rows = [row for row in rows.values() if row.registered and row.primary and row.primary.implementation_category == "configured"]
    categories = {letter: 0 for letter in "ABCD"}
    for row in configured_rows:
        letter, _ = configured_review(row.primary)
        categories[letter] += 1
    impl_counts: dict[str, int] = {}
    for row in rows.values():
        if row.registered and row.primary:
            key = row.primary.implementation_category
            impl_counts[key] = impl_counts.get(key, 0) + 1
    base_count = sum(row.registered and row.provenance == "BASE" for row in rows.values())
    spec_count = sum(row.registered and row.provenance == "SPEC" for row in rows.values())
    other_provenance = sum(row.registered and row.provenance not in {"BASE", "SPEC"} for row in rows.values())
    head = git_value(root, "rev-parse", "HEAD")
    base = git_value(root, "rev-parse", "origin/staging")

    out = [
        "# IceSMP class/spec/spell/cast audit", "",
        "> Generated by `scripts/audit_class_spell_system.py --strict` from the exact checkout.",
        "> The feature SHA below is the audited source commit at generation time; committing this report creates a later metadata-only HEAD.", "",
        "## Git evidence", "",
        f"- Audited feature SHA: `{head}`",
        f"- Staging base SHA: `{base}`", "",
        "## Inventory totals", "",
        f"- Classes: **{len(CLASS_TO_SPECS)}**",
        f"- Specializations: **{sum(len(v) for v in CLASS_TO_SPECS.values())}**",
        f"- Source-defined spell IDs: **{len(defined)}**",
        f"- Runtime-registered spell IDs: **{len(registered)}**",
        f"- Normal progression-reachable spell IDs: **{len(reachable)}**",
        f"- BASE provenance: **{base_count}**",
        f"- SPEC provenance: **{spec_count}**",
        f"- TALENT/QUEST/SYSTEM/ADMIN/DEV/allowlisted provenance: **{other_provenance}**",
        f"- Implementation breakdown: {', '.join(f'{key}={value}' for key, value in sorted(impl_counts.items()))}", "",
        "## ConfiguredSpell verdict — OPTION B (hybrid)", "",
        f"- A: **{categories['A']}**",
        f"- B: **{categories['B']}**",
        f"- C: **{categories['C']}**",
        f"- D: **{categories['D']}**", "",
        "A is restricted to immediate SELF/TARGET/AOE primitives with no scheduler/projectile/summon/target-memory lifecycle. B keeps the same generic gameplay payload but is manually marked for signature presentation. C/D are explicit review findings and strict mode refuses to pass while one remains unmigrated.", "",
        "### B/C/D reasons", "",
        "| Spell | Category | Reason |",
        "|---|---:|---|",
    ]
    for row in sorted(configured_rows, key=lambda item: item.spell_id):
        category, reason = configured_review(row.primary)
        if category != "A":
            out.append(f"| `{row.spell_id}` | {category} | {reason} |")
    if all(configured_review(row.primary)[0] == "A" for row in configured_rows):
        out.append("| — | — | none |")

    out += ["", "## Class verdicts", "", "| Class | Base spells | Specs | Registered/unlock consistency |", "|---|---:|---|---|"]
    for clazz, specs in CLASS_TO_SPECS.items():
        base_spells = [r for r in rows.values() if r.class_id == clazz and r.provenance == "BASE"]
        issue = any(r.unlock_referenced and not r.registered for r in rows.values() if r.class_id == clazz)
        out.append(f"| `{clazz}` | {len(base_spells)} | {', '.join(specs)} | {'FAIL' if issue else 'PASS'} |")

    out += ["", "## Specialization verdicts", "", "| Spec | Class | Spell count | Active-kit refs | DARK | Verdict |", "|---|---|---:|---:|---|---|"]
    for clazz, specs in CLASS_TO_SPECS.items():
        for spec in specs:
            spec_rows = [r for r in rows.values() if r.spec_id == spec and r.provenance == "SPEC"]
            kit = [r for r in rows.values() if spec in r.active_kit_owners]
            issue = any(r.unlock_referenced and not r.registered for r in spec_rows) or any(not r.registered for r in kit)
            out.append(f"| `{spec}` | `{clazz}` | {len(spec_rows)} | {len(kit)} | {'yes' if spec in DARK_SPECS else 'no'} | {'FAIL' if issue else 'PASS'} |")

    out += ["", "## Typed/scalar execution override inventory", ""]
    for name, paths in overrides.items():
        out.append(f"- `{name}`: **{len(paths)}** — " + (", ".join(f"`{p}`" for p in sorted(paths)) or "none"))

    out += ["", "## Delayed/projectile exact inventory", "",
            "| Spell | Implementation | Delayed | Projectile | Modifier snapshot | Caster snapshot | Target snapshot | Thread owner | Cleanup |",
            "|---|---|---|---|---|---|---|---|---|"]
    for item in sorted(delayed_inventory, key=lambda value: value["id"]):
        out.append("| `{id}` | `{implementation}` | {delayed} | {projectile} | {modifier_snapshot} | {caster_snapshot} | {target_snapshot} | {thread_owner} | {cleanup} |".format(**item))

    out += ["", "## Combo audit", "", f"- Chains: **{len(combos)}**", f"- Pairs: **{len(pairs)}**",
            "- `soul-collapse` and `way-of-hundred-fists` are explicit semantic deny-list entries and strict mode fails if either reappears.", "",
            "## Regression graph", "", "| Suite | Wiring | Task |", "|---|---|---|"]
    for row in regression_rows:
        out.append(f"| `{row['suite']}` | {row['state']} | `{row['task']}` |")

    out += ["", "## Architecture decisions", "",
            "- PlayerProfile/ClassSpec remains durable authority; class services are transient projections.",
            "- Cast flow: input → authority/active kit → validation/preparation → modifiers → affordability/reservation → typed execution → state commit → cooldown → feedback/stats.",
            "- Standard spell power scales damage/healing/shield magnitude, never hard-CC duration implicitly.",
            "- Scheduler/projectile behavior must carry immutable modifier snapshots across thread/region hops.",
            "- ConfiguredSpell remains for immediate generic primitives; dedicated Java behavior owns stateful/delayed/projectile/summon identity.", "",
            "## Druid naming", "",
            "- Primary resource: **Természeti Erő**",
            "- Secondary mechanic: **Harmónia**",
            f"- Ambiguous Harmony/Természeti Erő source lines: **{len(druid_naming)}**", "",
            "## Strict audit result", ""]
    out.append("**PASS**" if not errors else "**FAIL**")
    if errors:
        out += ["", "### Errors"] + [f"- {error}" for error in errors]
    else:
        out += ["", "No definition/registration/provenance, balance parity, active-kit, combo, delayed-scaling, required regression-wiring or Druid naming errors remain."]
    out += ["", "## NEEDS PLAYTEST", "",
            "Only numeric balance/readability questions remain for live-server playtest; no source-auditable correctness issue is intentionally deferred here.", "",
            "The complete per-spell evidence is versioned in `docs/audits/class-spell-inventory.csv`."]
    path.write_text("\n".join(out) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--csv", type=Path, default=Path("docs/audits/class-spell-inventory.csv"))
    parser.add_argument("--report", type=Path, default=Path("docs/audits/CLASS_SPELL_CAST_AUDIT.md"))
    parser.add_argument("--strict", action="store_true")
    args = parser.parse_args()
    root = args.root.resolve()
    required = [
        root / "src/main/java", root / "src/regression/java",
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

    java_root = root / "src/main/java"
    definitions, class_to_id = discover_definitions(java_root)
    registrations, unresolved = discover_runtime_registrations(root, class_to_id)
    unlocks = parse_unlocks(root / "src/main/resources/config/classes.yml")
    active_kits = parse_active_kits(root / "src/main/resources/config/class-gameplay.yml")
    balance = parse_balance(root / "src/main/resources/config/spells-balance.yml")
    schools, combos, pairs = parse_spell_config(root / "src/main/resources/config/spells.yml")
    rows = build_rows(definitions, registrations, unlocks, active_kits, balance, schools, combos, pairs)
    override_inventory = scan_override_inventory(java_root)
    delayed_inventory, delayed_errors = scan_delayed_projectiles(rows, root)
    regression_rows, regression_errors = regression_graph(root)
    druid_naming = scan_druid_naming(root)
    errors = validate(rows, registrations, unresolved, balance, active_kits, combos, pairs,
                      override_inventory, delayed_errors, regression_errors, druid_naming, root)

    csv_path = args.csv if args.csv.is_absolute() else root / args.csv
    report_path = args.report if args.report.is_absolute() else root / args.report
    csv_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    with csv_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=CSV_FIELDS)
        writer.writeheader()
        for sid in sorted(rows):
            writer.writerow(row_to_csv(rows[sid]))
    write_report(report_path, root, rows, errors, combos, pairs, override_inventory,
                 delayed_inventory, regression_rows, druid_naming)

    defined = sum(bool(row.definitions) for row in rows.values())
    registered = sum(row.registered for row in rows.values())
    reachable = sum(row.registered and row.provenance in PROGRESSION_PROVENANCE for row in rows.values())
    configured = sum(row.registered and row.primary is not None
                     and row.primary.implementation_category == "configured" for row in rows.values())
    print(f"IceSMP strict spell audit: defined={defined} registered={registered} progression={reachable} configured={configured}")
    for error in errors:
        print("ERROR: " + error, file=sys.stderr)
    if args.strict and errors:
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
