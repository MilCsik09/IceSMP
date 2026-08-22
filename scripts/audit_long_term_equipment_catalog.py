#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

import yaml

ROOT = Path(__file__).resolve().parents[1]
CFG = ROOT / "src/main/resources/config"
DEFAULT_OUTPUT = ROOT / "build/reports/long-term-equipment/equipment-catalog.json"
ARMOR_FAMILIES = ("CLOTH", "LEATHER", "MAIL", "PLATE")
ARMOR_SLOTS = ("head", "chest", "legs", "feet")
STAT_WEIGHTS = {
    "attack_damage": 5.0,
    "attack_speed": 8.0,
    "ability_power": 1.25,
    "max_health": 0.70,
    "armor": 2.0,
    "armor_toughness": 2.5,
    "movement_speed": 120.0,
}
STAT_GROUPS = {
    "attack_damage": "offense",
    "attack_speed": "offense",
    "ability_power": "offense",
    "max_health": "defense",
    "armor": "defense",
    "armor_toughness": "defense",
    "movement_speed": "utility",
}
SLOT_SHARE = {"chest": 1.0, "legs": 0.82, "head": 0.62, "feet": 0.56}
COMBAT_SLOT_SHARE = {"chest": 0.34, "legs": 0.28, "head": 0.19, "feet": 0.19}
SET_BUDGET = {"early": 32.0, "mid": 60.0, "high": 76.0, "endgame": 92.0}
RARITY_ORDER = ["common", "uncommon", "rare", "epic", "legendary", "mythic"]
CONFIG_FILES = [
    "item-templates.yml",
    "profession-materials.yml",
    "profession-recipes.yml",
    "professions-2.yml",
    "material-economy-expansion.yml",
    "equipment-catalog-expansion.yml",
    "reward-discoverability-closure.yml",
]


def load(name: str) -> dict[str, Any]:
    path = CFG / name
    if not path.exists():
        return {}
    raw = yaml.safe_load(path.read_text(encoding="utf-8"))
    return raw if isinstance(raw, dict) else {}


def merge(target: dict[str, Any], patch: dict[str, Any]) -> None:
    for key, value in patch.items():
        if isinstance(value, dict) and isinstance(target.get(key), dict):
            merge(target[key], value)
        else:
            target[key] = value


def effective() -> dict[str, Any]:
    result: dict[str, Any] = {}
    for name in CONFIG_FILES:
        merge(result, load(name))
    return result


def midpoint_stats(template: dict[str, Any]) -> dict[str, float]:
    values = {key: float(value) for key, value in (template.get("fixed-stats") or {}).items()}
    for key, bounds in (template.get("rolled-stats") or {}).items():
        values[key] = values.get(key, 0.0) + (float(bounds["min"]) + float(bounds["max"])) / 2.0
    if template.get("base-armor"):
        values["armor"] = values.get("armor", 0.0) + float(template["base-armor"])
    if template.get("base-damage"):
        values["attack_damage"] = values.get("attack_damage", 0.0) + float(template["base-damage"])
    unknown = set(values).difference(STAT_WEIGHTS)
    if unknown:
        raise AssertionError(f"unknown budget stat(s): {sorted(unknown)}")
    return values


def budget(template_id: str, template: dict[str, Any], profiles: dict[str, Any]) -> dict[str, Any]:
    family = str(template["armor-family"]).upper()
    profile = profiles[family.lower()]
    values = midpoint_stats(template)
    raw = {"offense": 0.0, "defense": 0.0, "utility": 0.0}
    for stat, value in values.items():
        raw[STAT_GROUPS[stat]] += value * STAT_WEIGHTS[stat]
    # STAT_WEIGHTS are the shared combat-impact currency. Family budget shares remain build
    # identity guidance and must not make equal-power items incomparable across families.
    normalized = sum(raw.values())
    rarity = str(template.get("rarity", "common")).lower()
    slot = str(template["slot"]).lower()
    metadata = template.get("encounter-metadata") or {}
    band = str(metadata.get("progression-band", ""))
    expected = COMBAT_SLOT_SHARE[slot] * SET_BUDGET[band]
    ratio = normalized / expected if expected else 0.0
    return {
        "template_id": template_id,
        "family": family,
        "line": str(metadata.get("catalog-line", "")),
        "slot": slot.upper(),
        "item_level": int(template["item-level"]),
        "rarity": rarity.upper(),
        "normalized_budget": round(normalized, 5),
        "expected_budget": round(expected, 5),
        "normalized_to_expected": round(ratio, 5),
        "offense": round(raw["offense"], 5),
        "defense": round(raw["defense"], 5),
        "utility": round(raw["utility"], 5),
        "acquisition": str(metadata.get("catalog-acquisition", "")),
        "profession_source": str(metadata.get("catalog-profession", "")),
        "set": str(template.get("set-id", "")),
        "signature": str(template.get("signature-effect", "")),
        "ascension": list(template.get("ascension-path") or []),
        "status": "BALANCE_REQUIRED" if ratio < 0.88 or ratio > 1.12 else "VERIFIED",
    }


def line_dominance(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    by_line: dict[tuple[str, str], list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        if row["line"]:
            by_line[(row["family"], row["line"])].append(row)
    averages: dict[tuple[str, str], dict[str, float]] = {}
    for key, values in by_line.items():
        if len(values) != 4:
            continue
        averages[key] = {
            group: sum(float(value[group]) for value in values) / 4.0
            for group in ("offense", "defense", "utility")
        }
    findings: list[dict[str, Any]] = []
    for (family, line), signal in sorted(averages.items()):
        candidates = [(key, other) for key, other in averages.items() if key[0] == family and key[1] != line]
        dominated = []
        for (_, other_line), other in candidates:
            weakly_better = all(signal[group] >= other[group] - 1e-9 for group in signal)
            materially_better = any(signal[group] > other[group] * 1.05 + 1e-9 for group in signal)
            if weakly_better and materially_better:
                dominated.append(other_line)
        if len(dominated) >= 7:
            findings.append({
                "family": family,
                "line": line,
                "status": "BALANCE_REQUIRED",
                "reason": "source-level budget signal weakly dominates at least seven same-family lines",
                "dominates": sorted(dominated),
                "average_signal": {key: round(value, 5) for key, value in signal.items()},
            })
    return findings


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default=str(DEFAULT_OUTPUT.relative_to(ROOT)))
    parser.add_argument("--require-eight-sets", action="store_true")
    args = parser.parse_args()

    config = effective()
    templates: dict[str, dict[str, Any]] = config.get("item-templates", {}) or {}
    recipes: dict[str, dict[str, Any]] = config.get("profession-recipes", {}) or {}
    profiles = config.get("itemization", {}).get("equipment", {}).get("family-profiles", {})
    assert set(map(str.upper, profiles)) == set(ARMOR_FAMILIES), "all four family profiles must remain authoritative"

    armor: dict[str, dict[str, Any]] = {}
    for template_id, template in templates.items():
        family = str(template.get("armor-family", "")).upper()
        slot = str(template.get("slot", "")).lower()
        if family in ARMOR_FAMILIES and slot in ARMOR_SLOTS:
            armor[str(template_id)] = template

    assert len(armor) == 160, f"expected exactly 160 canonical armor templates, found {len(armor)}"
    assert len(set(armor)) == 160, "canonical armor template IDs must be unique"

    family_counts = Counter(str(template["armor-family"]).upper() for template in armor.values())
    assert family_counts == Counter({family: 40 for family in ARMOR_FAMILIES}), family_counts
    slot_counts: dict[str, Counter[str]] = {
        family: Counter(str(template["slot"]).lower() for template in armor.values()
                        if str(template["armor-family"]).upper() == family)
        for family in ARMOR_FAMILIES
    }
    for family, counts in slot_counts.items():
        assert counts == Counter({slot: 10 for slot in ARMOR_SLOTS}), f"{family}: slot drift: {counts}"

    line_members: dict[tuple[str, str], list[str]] = defaultdict(list)
    acquisition = Counter()
    crafted_owners = Counter()
    archetypes: dict[str, Counter[str]] = {family: Counter() for family in ARMOR_FAMILIES}
    rarity_distribution: dict[str, Counter[str]] = {family: Counter() for family in ARMOR_FAMILIES}
    level_distribution: dict[str, Counter[str]] = {family: Counter() for family in ARMOR_FAMILIES}
    missing_identity: list[str] = []
    for template_id, template in armor.items():
        family = str(template["armor-family"]).upper()
        metadata = template.get("encounter-metadata") or {}
        line = str(metadata.get("catalog-line", ""))
        acq = str(metadata.get("catalog-acquisition", ""))
        archetype = str(metadata.get("catalog-archetype", ""))
        if not line or acq not in {"crafted", "world", "boss", "prestige"} or not archetype:
            missing_identity.append(template_id)
            continue
        line_members[(family, line)].append(template_id)
        acquisition[acq] += 1
        archetypes[family][archetype] += 1
        rarity_distribution[family][str(template.get("rarity", "common")).upper()] += 1
        level = int(template.get("item-level", 1))
        band = "EARLY" if level < 20 else "MID" if level < 30 else "HIGH" if level < 40 else "ENDGAME"
        level_distribution[family][band] += 1
        if acq == "crafted":
            owner = str(metadata.get("catalog-profession", ""))
            crafted_owners[owner] += 1
    assert not missing_identity, f"armor missing catalog identity metadata: {missing_identity}"
    assert len(line_members) == 40, f"expected 40 coherent lines, found {len(line_members)}"
    for key, members in line_members.items():
        assert len(members) == 4, f"{key}: expected four pieces, found {len(members)}"
        member_slots = {str(armor[item]["slot"]).lower() for item in members}
        assert member_slots == set(ARMOR_SLOTS), f"{key}: line slot coverage drift: {member_slots}"

    assert acquisition == Counter({"crafted": 64, "world": 48, "boss": 32, "prestige": 16}), acquisition
    assert crafted_owners == Counter({"armorer": 24, "enchanter": 24, "alchemist": 16}), crafted_owners

    recipe_by_template: dict[str, list[tuple[str, dict[str, Any]]]] = defaultdict(list)
    for recipe_id, recipe in recipes.items():
        template_id = str((recipe.get("result") or {}).get("template", ""))
        if template_id:
            recipe_by_template[template_id].append((str(recipe_id), recipe))
    crafted_ids = {template_id for template_id, template in armor.items()
                   if (template.get("encounter-metadata") or {}).get("catalog-acquisition") == "crafted"}
    recipe_errors = []
    for template_id in sorted(crafted_ids):
        candidates = recipe_by_template.get(template_id, [])
        if len(candidates) != 1:
            recipe_errors.append(f"{template_id}: expected one canonical recipe, got {len(candidates)}")
            continue
        _, recipe = candidates[0]
        owner = str(recipe.get("profession", ""))
        expected_owner = str((armor[template_id].get("encounter-metadata") or {}).get("catalog-profession", ""))
        if owner != expected_owner:
            recipe_errors.append(f"{template_id}: recipe owner {owner} != catalog owner {expected_owner}")
    assert not recipe_errors, "; ".join(recipe_errors)
    assert len(crafted_ids) == 64, f"expected 64 crafted armor outputs, found {len(crafted_ids)}"

    set_members: dict[str, list[str]] = defaultdict(list)
    for template_id, template in armor.items():
        set_id = str(template.get("set-id", ""))
        if set_id:
            set_members[set_id].append(template_id)
    mechanical_sets = {
        set_id: sorted(members) for set_id, members in set_members.items()
        if len(members) == 4 and {str(armor[item]["slot"]).lower() for item in members} == set(ARMOR_SLOTS)
    }
    if args.require_eight_sets:
        assert len(mechanical_sets) == 8, f"expected exactly eight four-armor sets, found {len(mechanical_sets)}"
    else:
        assert 7 <= len(mechanical_sets) <= 8, f"catalog phase expects 7-8 qualifying sets, found {len(mechanical_sets)}"

    budget_rows = [budget(template_id, template, profiles) for template_id, template in sorted(armor.items())]
    budget_outliers = [row for row in budget_rows if row["status"] == "BALANCE_REQUIRED"]
    dominance = line_dominance(budget_rows)

    duplicate_fingerprints: dict[str, list[str]] = defaultdict(list)
    for template_id, template in armor.items():
        metadata = template.get("encounter-metadata") or {}
        fingerprint = json.dumps({
            "name": template.get("display-name"),
            "family": template.get("armor-family"),
            "slot": template.get("slot"),
            "stats": midpoint_stats(template),
            "source": metadata.get("catalog-acquisition"),
            "line": metadata.get("catalog-line"),
        }, sort_keys=True, ensure_ascii=False)
        duplicate_fingerprints[fingerprint].append(template_id)
    accidental_duplicates = [members for members in duplicate_fingerprints.values() if len(members) > 1]
    assert not accidental_duplicates, f"accidental armor identity duplicates: {accidental_duplicates}"

    signature_count = sum(1 for template in armor.values() if template.get("signature-effect"))
    ascension_count = sum(1 for template in armor.values() if template.get("ascension-path"))
    report = {
        "schema": 1,
        "canonical_armor_count": len(armor),
        "family_counts": dict(sorted(family_counts.items())),
        "slot_counts": {family: dict(sorted(counts.items())) for family, counts in slot_counts.items()},
        "gear_line_count": len(line_members),
        "acquisition_split": dict(sorted(acquisition.items())),
        "crafted_profession_ownership": dict(sorted(crafted_owners.items())),
        "canonical_crafted_recipe_count": len(crafted_ids),
        "mechanical_four_piece_set_count": len(mechanical_sets),
        "mechanical_four_piece_sets": mechanical_sets,
        "signature_armor_count": signature_count,
        "ascendable_armor_count": ascension_count,
        "family_reports": {
            family: {
                "item_count": family_counts[family],
                "slots": dict(sorted(slot_counts[family].items())),
                "archetypes": dict(sorted(archetypes[family].items())),
                "rarity": dict(sorted(rarity_distribution[family].items())),
                "progression_band": dict(sorted(level_distribution[family].items())),
                "set_count": len({str(armor[item].get("set-id")) for item in armor
                                  if str(armor[item].get("armor-family", "")).upper() == family
                                  and str(armor[item].get("set-id", ""))
                                  and str(armor[item].get("set-id")) in mechanical_sets}),
                "signature_count": sum(1 for item in armor.values()
                                       if str(item.get("armor-family", "")).upper() == family
                                       and item.get("signature-effect")),
                "ascension_count": sum(1 for item in armor.values()
                                       if str(item.get("armor-family", "")).upper() == family
                                       and item.get("ascension-path")),
            } for family in ARMOR_FAMILIES
        },
        "budget_outlier_count": len(budget_outliers),
        "budget_outliers": budget_outliers,
        "horizontal_progression_findings": dominance,
        "horizontal_progression_status": "BALANCE_REQUIRED" if dominance else "SOURCE_VERIFIED",
        "armor_budget_report": budget_rows,
    }
    output = ROOT / args.output
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(
        "Long-term equipment catalog audit: "
        f"armor={len(armor)}, families={dict(family_counts)}, lines={len(line_members)}, "
        f"crafted={len(crafted_ids)}, sets={len(mechanical_sets)}, "
        f"budget-outliers={len(budget_outliers)}, dominance={len(dominance)}"
    )


if __name__ == "__main__":
    main()
