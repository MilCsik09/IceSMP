#!/usr/bin/env python3
"""Source-of-truth audit for the IceSMP class/spec/spell graph.

The parser intentionally uses only the Python standard library so Gradle can run it
on developer machines and CI without an additional package install. It validates
registration, unlock, active-kit and combo consistency and emits a complete CSV
inventory plus a human-readable summary.
"""
from __future__ import annotations

import argparse
import csv
import re
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

MATRIX_FIELDS = [
    "id", "display_name", "class", "spec", "unlock_level", "provenance",
    "registration_location", "implementation_class", "implementation_category",
    "cooldown", "legacy_cost_type", "legacy_cost_amount", "use_resource",
    "effective_class_resource_cost", "spell_school", "targeting", "range", "radius",
    "direct_damage", "aoe_damage", "dot", "healing", "hot", "shielding",
    "self_damage", "cc", "displacement", "mobility", "summon", "buff_debuff",
    "friendly_fire_behavior", "no_target_behavior", "no_op_behavior", "refund_behavior",
    "mastery_scaling", "dynamic_scaling", "gear_scaling", "class_specific_scaling",
    "generic_combo_interaction", "live_balance_override", "vfx", "sound",
    "hud_feedback_requirement", "regression_coverage", "source_evidence",
]

@dataclass
class SpellRow:
    id: str
    display_name: str = ""
    implementation_class: str = ""
    implementation_category: str = "dedicated"
    cooldown: str = ""
    legacy_cost_type: str = ""
    legacy_cost_amount: str = ""
    registration_location: str = ""
    source_evidence: str = ""
    class_id: str = ""
    spec_id: str = ""
    unlock_level: str = ""
    provenance: str = "UNREACHABLE"
    details: dict[str, str] = field(default_factory=dict)

    def to_dict(self) -> dict[str, str]:
        default_scaling = "CastModifiers.damageMultiplier"
        data = {field: "" for field in MATRIX_FIELDS}
        data.update({
            "id": self.id,
            "display_name": self.display_name,
            "class": self.class_id,
            "spec": self.spec_id,
            "unlock_level": self.unlock_level,
            "provenance": self.provenance,
            "registration_location": self.registration_location,
            "implementation_class": self.implementation_class,
            "implementation_category": self.implementation_category,
            "cooldown": self.cooldown,
            "legacy_cost_type": self.legacy_cost_type,
            "legacy_cost_amount": self.legacy_cost_amount,
            "mastery_scaling": default_scaling,
            "dynamic_scaling": default_scaling,
            "gear_scaling": default_scaling,
            "class_specific_scaling": "CastModifiers from cast pipeline",
            "refund_behavior": "typed CastOutcome; non-success is transaction-neutral",
            "live_balance_override": "cast-time spells-balance lookup",
            "source_evidence": self.source_evidence,
        })
        data.update(self.details)
        return data


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def scalar(value: str) -> str:
    value = value.strip()
    if value.startswith('"') and value.endswith('"'):
        return value[1:-1]
    return value


def parse_yaml_spell_unlocks(path: Path) -> tuple[dict[str, tuple[str, str, int]], dict[str, set[str]]]:
    """Small indentation parser for classes/spec spell-unlocks and active-kit lists."""
    unlocks: dict[str, tuple[str, str, int]] = {}
    active: dict[str, set[str]] = {}
    stack: list[tuple[int, str]] = []
    current_list: tuple[str, str] | None = None
    for raw in read(path).splitlines():
        clean = raw.split("#", 1)[0].rstrip()
        if not clean.strip():
            continue
        indent = len(clean) - len(clean.lstrip(" "))
        stripped = clean.strip()
        while stack and stack[-1][0] >= indent:
            stack.pop()
        if stripped.startswith("-"):
            if current_list is not None:
                active.setdefault(current_list[1], set()).add(scalar(stripped[1:].strip()))
            continue
        if ":" not in stripped:
            continue
        key, value = stripped.split(":", 1)
        key, value = key.strip(), value.strip()
        stack.append((indent, key))
        keys = [part for _, part in stack]
        current_list = None
        if key in {"default", "defaults", "spells", "active-kit"} and not value:
            owner = next((k for k in reversed(keys[:-1]) if k in CLASS_TO_SPECS or k in SPEC_TO_CLASS), "")
            if owner:
                current_list = (key, owner)
        if len(keys) >= 4 and keys[-2] == "spell-unlocks" and value:
            owner = keys[-3]
            try:
                level = int(value)
            except ValueError:
                continue
            if keys[0] == "classes" and owner in CLASS_TO_SPECS:
                unlocks[key] = (owner, "", level)
            elif keys[0] in {"specializations", "specs"} and owner in SPEC_TO_CLASS:
                unlocks[key] = (SPEC_TO_CLASS[owner], owner, level)
    return unlocks, active


def parse_simple_overrides(path: Path) -> tuple[dict[str, dict[str, str]], dict[str, str], list[list[str]], list[tuple[str, str]]]:
    overrides: dict[str, dict[str, str]] = {}
    schools: dict[str, str] = {}
    chains: list[list[str]] = []
    pairs: list[tuple[str, str]] = []
    section: list[tuple[int, str]] = []
    current_pair: dict[str, str] = {}
    for raw in read(path).splitlines():
        clean = raw.split("#", 1)[0].rstrip()
        if not clean.strip() or ":" not in clean:
            continue
        indent = len(clean) - len(clean.lstrip(" "))
        stripped = clean.strip()
        while section and section[-1][0] >= indent:
            section.pop()
        key, value = stripped.split(":", 1)
        key, value = key.strip(), value.strip()
        section.append((indent, key))
        path_keys = [x for _, x in section]
        if "spell-balance" in path_keys and len(path_keys) >= path_keys.index("spell-balance") + 2:
            idx = path_keys.index("spell-balance")
            spell_id = path_keys[idx + 1]
            if value:
                overrides.setdefault(spell_id, {})[key] = scalar(value)
        if len(path_keys) >= 2 and path_keys[-2] == "by-spell" and value:
            schools[key] = scalar(value)
        if "chains" in path_keys and key == "steps" and value.startswith("["):
            chains.append([scalar(x.strip()) for x in value[1:-1].split(",") if x.strip()])
        if "pairs" in path_keys and key in {"first", "second"}:
            if key == "first":
                current_pair = {"first": scalar(value)}
            elif key == "second" and current_pair.get("first"):
                pairs.append((current_pair["first"], scalar(value)))
                current_pair = {}
    return overrides, schools, chains, pairs


def method_owner(method: str) -> tuple[str, str]:
    token = re.sub(r"([a-z0-9])([A-Z])", r"\1_\2", method.removeprefix("register")).lower()
    if token in CLASS_TO_SPECS:
        return token, ""
    if token in SPEC_TO_CLASS:
        return SPEC_TO_CLASS[token], token
    return "", ""


def effect_details(block: str) -> dict[str, str]:
    d: dict[str, str] = {}
    if ".self()" in block:
        d["targeting"] = "SELF"
    target = re.search(r"\.target\(([^)]+)\)", block)
    aoe = re.search(r"\.aoe\(([^)]+)\)", block)
    if target:
        d["targeting"], d["range"] = "TARGET", target.group(1).strip()
        d["no_target_behavior"] = "NO_TARGET / refund"
    elif aoe:
        d["targeting"], d["radius"] = "AOE", aoe.group(1).strip()
        d["no_target_behavior"] = "REJECT_EMPTY unless self output"
    else:
        d.setdefault("targeting", "SELF")
    damage = re.search(r"\.damage\(([^)]+)\)", block)
    if damage:
        d["direct_damage" if not aoe else "aoe_damage"] = damage.group(1).strip()
    heal = re.search(r"\.healSelf\(([^)]+)\)", block)
    if heal:
        d["healing"] = heal.group(1).strip()
    self_damage = re.search(r"\.selfDamage\(([^)]+)\)", block)
    if self_damage:
        d["self_damage"] = self_damage.group(1).strip()
    if "ABSORPTION" in block:
        d["shielding"] = "potion absorption"
    if "REGENERATION" in block:
        d["hot"] = "regeneration"
    if "WITHER" in block or "POISON" in block or ".ignite(" in block:
        d["dot"] = "yes"
    effects = re.findall(r"targetEffect\(PotionEffectType\.([A-Z_]+)", block)
    if effects:
        d["buff_debuff"] = ",".join(effects)
        hard = [e for e in effects if e in {"SLOWNESS", "BLINDNESS", "LEVITATION", "JUMP_BOOST"}]
        if hard:
            d["cc"] = ",".join(hard) + " (explicit ccDurationMultiplier only)"
    if any(token in block for token in (".knockback(", ".pull(", ".launchUp(")):
        d["displacement"] = "yes"
    if ".dash(" in block:
        d["mobility"] = "dash"
    if ".friendly()" in block:
        d["friendly_fire_behavior"] = "allies only"
    elif aoe or target:
        d["friendly_fire_behavior"] = "SpellTargetingUtil ally gate"
    particle = re.search(r"\.particle\(Particle\.([A-Z0-9_]+)", block)
    sound = re.search(r"\.sound\(Sound\.([A-Z0-9_]+)", block)
    if particle:
        d["vfx"] = particle.group(1)
    if sound:
        d["sound"] = sound.group(1)
    return d


def parse_registrations(java_root: Path) -> tuple[dict[str, SpellRow], list[str]]:
    rows: dict[str, SpellRow] = {}
    duplicate_evidence: list[str] = []
    for path in java_root.rglob("*.java"):
        text = read(path)
        rel = path.relative_to(java_root.parents[2]).as_posix()
        method_spans: list[tuple[int, int, str]] = []
        matches = list(re.finditer(r"private static void (register[A-Za-z0-9_]+)\s*\(", text))
        for index, match in enumerate(matches):
            end = matches[index + 1].start() if index + 1 < len(matches) else len(text)
            method_spans.append((match.start(), end, match.group(1)))
        def context_owner(pos: int) -> tuple[str, str]:
            for start, end, name in method_spans:
                if start <= pos < end:
                    return method_owner(name)
            return "", ""
        pattern = re.compile(
            r"ConfiguredSpell\.builder\([^,]+,\s*\"([^\"]+)\",\s*\"([^\"]+)\",\s*([^,]+),\s*SpellCostType\.([A-Z]+),\s*([^)]+)\)(.*?)(?:\.build\(\))",
            re.S,
        )
        for match in pattern.finditer(text):
            sid, name, cooldown, cost_type, amount, tail = match.groups()
            clazz, spec = context_owner(match.start())
            row = SpellRow(sid, name, "ConfiguredSpell", "configured", cooldown.strip(), cost_type,
                           amount.strip(), rel, f"{rel}:{text.count(chr(10), 0, match.start()) + 1}", clazz, spec)
            row.details.update(effect_details(tail))
            add_row(rows, row, duplicate_evidence)
        for impl, category in (("ProjectileBurstSpell", "projectile"), ("BlinkSpell", "mobility"),
                               ("SummonMinionsSpell", "summon")):
            regex = re.compile(rf"new {impl}\(.*?\"([^\"]+)\",\s*\"([^\"]+)\",\s*([^,]+),\s*SpellCostType\.([A-Z]+),\s*([^,\n]+)", re.S)
            for match in regex.finditer(text):
                sid, name, cooldown, cost_type, amount = match.groups()
                clazz, spec = context_owner(match.start())
                row = SpellRow(sid, name, impl, category, cooldown.strip(), cost_type, amount.strip(), rel,
                               f"{rel}:{text.count(chr(10), 0, match.start()) + 1}", clazz, spec)
                row.details["summon" if category == "summon" else "mobility" if category == "mobility" else "targeting"] = (
                    "yes" if category == "summon" else "blink" if category == "mobility" else "PROJECTILE")
                add_row(rows, row, duplicate_evidence)
        class_match = re.search(r"(?:public\s+)?(?:final\s+)?class\s+(\w+)", text)
        implementation = class_match.group(1) if class_match else path.stem
        for match in re.finditer(r"super\([^;]*?\"([^\"]+)\",\s*\"([^\"]+)\",\s*([^,]+),\s*SpellCostType\.([A-Z]+),\s*([^)]+)\)", text, re.S):
            sid, name, cooldown, cost_type, amount = match.groups()
            row = SpellRow(sid, name, implementation, classify_impl(implementation, text), cooldown.strip(),
                           cost_type, amount.strip(), rel, f"{rel}:{text.count(chr(10), 0, match.start()) + 1}")
            add_row(rows, row, duplicate_evidence, prefer_existing=True)
    return rows, duplicate_evidence


def add_row(rows: dict[str, SpellRow], row: SpellRow, duplicate_evidence: list[str], prefer_existing: bool = False) -> None:
    previous = rows.get(row.id)
    if previous is not None:
        if previous.implementation_class == row.implementation_class or prefer_existing:
            if not previous.registration_location:
                previous.registration_location = row.registration_location
            return
        duplicate_evidence.append(f"{row.id}: {previous.implementation_class} vs {row.implementation_class}")
        return
    rows[row.id] = row


def classify_impl(name: str, text: str) -> str:
    lowered = name.lower()
    if "projectile" in lowered or "launchProjectile" in text:
        return "projectile"
    if "summon" in lowered or "Minion" in text or "spawn(" in text:
        return "summon"
    if "form" in lowered:
        return "form"
    if "runDelayed" in text or "runAtFixedRate" in text or "clearPlayerState" in text:
        return "stateful"
    return "dedicated"


def assign_unlocks(rows: dict[str, SpellRow], unlocks: dict[str, tuple[str, str, int]]) -> list[str]:
    missing: list[str] = []
    for spell_id, (clazz, spec, level) in unlocks.items():
        row = rows.get(spell_id)
        if row is None:
            missing.append(spell_id)
            continue
        row.class_id, row.spec_id, row.unlock_level = clazz, spec, str(level)
        row.provenance = "SPEC" if spec else "BASE"
    return missing


def valid_loadouts(row: SpellRow) -> set[tuple[str, str]]:
    if not row.class_id:
        return {(clazz, spec) for clazz, specs in CLASS_TO_SPECS.items() for spec in specs}
    if row.spec_id:
        return {(row.class_id, row.spec_id)}
    return {(row.class_id, spec) for spec in CLASS_TO_SPECS[row.class_id]}


def validate_combos(rows: dict[str, SpellRow], chains: Iterable[list[str]], pairs: Iterable[tuple[str, str]]) -> list[str]:
    errors: list[str] = []
    candidates = [("chain", chain) for chain in chains] + [("pair", list(pair)) for pair in pairs]
    for kind, steps in candidates:
        missing = [sid for sid in steps if sid not in rows]
        if missing:
            errors.append(f"{kind} references unknown spell(s): {', '.join(missing)}")
            continue
        common = None
        for sid in steps:
            loadouts = valid_loadouts(rows[sid])
            common = loadouts if common is None else common & loadouts
        if not common:
            errors.append(f"impossible {kind}: {' -> '.join(steps)}")
    return errors


def apply_overrides(rows: dict[str, SpellRow], overrides: dict[str, dict[str, str]], schools: dict[str, str], combo_ids: set[str]) -> None:
    for sid, row in rows.items():
        override = overrides.get(sid, {})
        row.details["use_resource"] = override.get("use-resource", "default")
        row.details["effective_class_resource_cost"] = override.get("resource-cost", "cooldown tier")
        row.details["spell_school"] = schools.get(sid, "class default / primordial")
        row.details["generic_combo_interaction"] = "yes" if sid in combo_ids else "no"
        row.details.setdefault("no_op_behavior", "typed by implementation")
        row.details.setdefault("hud_feedback_requirement", "cooldown + class mechanic projection")
        row.details.setdefault("regression_coverage", "spell graph + cast architecture suite")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--csv", type=Path, default=Path("build/reports/class-spell/spell-inventory.csv"))
    parser.add_argument("--report", type=Path, default=Path("build/reports/class-spell/audit-summary.md"))
    parser.add_argument("--strict", action="store_true")
    args = parser.parse_args()
    root = args.root.resolve()
    java_root = root / "src/main/java"
    classes_yml = root / "src/main/resources/config/classes.yml"
    spells_yml = root / "src/main/resources/config/spells.yml"
    required = [java_root, classes_yml, spells_yml]
    missing_files = [str(path) for path in required if not path.exists()]
    if missing_files:
        print("Missing audit inputs: " + ", ".join(missing_files), file=sys.stderr)
        return 2

    rows, duplicate_evidence = parse_registrations(java_root)
    unlocks, active = parse_yaml_spell_unlocks(classes_yml)
    unlock_missing = assign_unlocks(rows, unlocks)
    overrides, schools, chains, pairs = parse_simple_overrides(spells_yml)
    combo_errors = validate_combos(rows, chains, pairs)
    combo_ids = {sid for chain in chains for sid in chain} | {sid for pair in pairs for sid in pair}
    apply_overrides(rows, overrides, schools, combo_ids)

    active_missing = sorted({sid for values in active.values() for sid in values if sid not in rows})
    unreachable = sorted(sid for sid, row in rows.items() if row.provenance == "UNREACHABLE")
    errors = ([f"duplicate registration: {evidence}" for evidence in duplicate_evidence]
              + [f"unlock resolves to no registered spell: {spell_id}" for spell_id in sorted(unlock_missing)]
              + [f"active-kit resolves to no registered spell: {spell_id}" for spell_id in active_missing]
              + combo_errors)

    csv_path = args.csv if args.csv.is_absolute() else root / args.csv
    report_path = args.report if args.report.is_absolute() else root / args.report
    csv_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    with csv_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=MATRIX_FIELDS)
        writer.writeheader()
        for spell_id in sorted(rows):
            writer.writerow(rows[spell_id].to_dict())

    configured = sum(row.implementation_category == "configured" for row in rows.values())
    dedicated = len(rows) - configured
    report = [
        "# IceSMP class/spec/spell source audit", "",
        f"- Classes: {len(CLASS_TO_SPECS)}",
        f"- Specializations: {sum(map(len, CLASS_TO_SPECS.values()))}",
        f"- Registered spell ids discovered: {len(rows)}",
        f"- ConfiguredSpell: {configured}",
        f"- Dedicated/generic behavior classes: {dedicated}",
        f"- Unlock entries: {len(unlocks)}",
        f"- Combo chains: {len(chains)}; pairs: {len(pairs)}",
        f"- DARK-gated specs: {', '.join(sorted(DARK_SPECS))}", "",
        "## Consistency errors",
    ]
    report.extend([f"- {error}" for error in errors] or ["- none"])
    report += ["", "## Registered but not normal unlock provenance"]
    report.extend([f"- {spell_id}" for spell_id in unreachable] or ["- none"])
    report += ["", "The complete machine-readable matrix is `spell-inventory.csv`."]
    report_path.write_text("\n".join(report) + "\n", encoding="utf-8")

    print(f"IceSMP spell audit: {len(rows)} spells, {configured} configured, {dedicated} dedicated")
    for error in errors:
        print("ERROR: " + error, file=sys.stderr)
    return 1 if args.strict and errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
