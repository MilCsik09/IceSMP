#!/usr/bin/env python3
"""Generate and verify the deterministic Equipment 2.0 migration/handoff inventory."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

import yaml


ROOT = Path(__file__).resolve().parents[1]
ITEMS = ROOT / "src/main/resources/config/item-templates.yml"
AUTHORED_CATALOG = ROOT / "src/main/resources/config/equipment-catalog-expansion.yml"
RECIPES = ROOT / "src/main/resources/config/profession-recipes.yml"
OUTPUT = ROOT / "docs/development/equipment-2-handoff.json"
ARMOR_SLOTS = {"head", "chest", "legs", "feet"}
CLASS_FAMILIES = {
    "cloth": ["priest", "warlock", "wizard"],
    "leather": ["monk", "demon_hunter", "druid", "assassin"],
    "mail": ["archer", "shaman", "evoker"],
    "plate": ["warrior", "paladin", "death_knight"],
}
STAT_WEIGHTS = {
    "attack_damage": 5.0,
    "attack_speed": 8.0,
    "ability_power": 1.25,
    "max_health": 0.70,
    "armor": 2.0,
    "armor_toughness": 2.5,
    "knockback_resistance": 30.0,
    "movement_speed": 120.0,
}
STAT_GROUPS = {
    "attack_damage": "offensive",
    "attack_speed": "offensive",
    "ability_power": "offensive",
    "max_health": "defensive",
    "armor": "defensive",
    "armor_toughness": "defensive",
    "knockback_resistance": "defensive",
    "movement_speed": "utility",
}
SLOT_SHARE = {"chest": 1.0, "legs": 0.82, "head": 0.62, "feet": 0.56}
RARITY_ORDER = ["common", "uncommon", "rare", "epic", "legendary", "mythic"]


def load_yaml(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as stream:
        return yaml.safe_load(stream)


def midpoint_stats(template: dict[str, Any]) -> dict[str, float]:
    values = {key: float(value) for key, value in template.get("fixed-stats", {}).items()}
    for key, bounds in template.get("rolled-stats", {}).items():
        values[key] = values.get(key, 0.0) + (float(bounds["min"]) + float(bounds["max"])) / 2.0
    if template.get("base-armor"):
        values["armor"] = values.get("armor", 0.0) + float(template["base-armor"])
    if template.get("base-damage"):
        values["attack_damage"] = values.get("attack_damage", 0.0) + float(template["base-damage"])
    return values


def budget_row(template_id: str, template: dict[str, Any], profiles: dict[str, Any]) -> dict[str, Any]:
    family = template["armor-family"]
    values = midpoint_stats(template)
    raw = {"offensive": 0.0, "defensive": 0.0, "utility": 0.0}
    for stat, value in values.items():
        raw[STAT_GROUPS[stat]] += value * STAT_WEIGHTS[stat]
    shares = profiles[family]["budget"]
    normalized = sum(raw[group] / float(shares[group]) for group in raw)
    rarity = 1.0 + RARITY_ORDER.index(template["rarity"]) * 0.10
    expected = SLOT_SHARE[template["slot"]] * rarity * (2.0 + int(template["item-level"]) * 0.12)
    health = values.get("max_health", 0.0)
    armor = values.get("armor", 0.0) / float(profiles[family]["base-armor-coefficient"])
    ratio = normalized / expected
    return {
        "template_id": template_id,
        "family": family.upper(),
        "item_level": int(template["item-level"]),
        "rarity": template["rarity"].upper(),
        "offensive_budget": round(raw["offensive"], 4),
        "defensive_budget": round(raw["defensive"], 4),
        "utility_budget": round(raw["utility"], 4),
        "total_normalized_budget": round(normalized, 4),
        "expected_tier_budget": round(expected, 4),
        "normalized_to_expected": round(ratio, 4),
        "physical_effective_health_signal": round((20.0 + health) * (1.0 + armor / 100.0), 4),
        "status": "EXPLICIT_EXCEPTION" if template.get("family-exception")
        else ("BALANCE_REQUIRED" if ratio < 0.20 or ratio > 8.0 else "VERIFIED"),
        "exception_reason": template.get("family-exception", ""),
    }


def build_report() -> dict[str, Any]:
    item_config = load_yaml(ITEMS)
    templates = item_config["item-templates"]
    authored = load_yaml(AUTHORED_CATALOG).get("item-templates", {})
    templates = {
        template_id: (authored[template_id]
                      if template_id in authored and authored[template_id].get("armor-family")
                      else template)
        for template_id, template in templates.items()
    }
    profiles = item_config["itemization"]["equipment"]["family-profiles"]
    recipes = load_yaml(RECIPES)["profession-recipes"]
    if len(templates) != 48:
        raise ValueError(f"expected 48 authored templates, found {len(templates)}")
    for template_id, template in templates.items():
        has_family = bool(template.get("armor-family"))
        if (template.get("slot") in ARMOR_SLOTS) != has_family:
            raise ValueError(f"armor slot/family mismatch: {template_id}")
        if has_family and template["armor-family"] not in CLASS_FAMILIES:
            raise ValueError(f"invalid ArmorFamily: {template_id}")

    distribution = {family.upper(): 0 for family in CLASS_FAMILIES}
    migration = []
    visual = []
    budgets = []
    for template_id, template in templates.items():
        family = template.get("armor-family")
        if family:
            distribution[family.upper()] += 1
            budgets.append(budget_row(template_id, template, profiles))
        allowed = list(template.get("class-restrictions") or (CLASS_FAMILIES[family] if family else []))
        stat_names = sorted(midpoint_stats(template))
        migration.append({
            "template_id": template_id,
            "slot": template["slot"].upper(),
            "old_role": f"{template['family'].upper()}:{template['slot'].upper()}:{','.join(stat_names) or 'NO_STATS'}",
            "new_armor_family": family.upper() if family else None,
            "allowed_classes": allowed,
            "balance_adjustment": template.get("family-exception")
            or ("Family profile szerint auditált; authored statok megtartva."
                if family else "Nem armor slot; fake ArmorFamily nélkül marad."),
        })
        equipment_asset = template.get("equipment-asset", "")
        item_model = template.get("item-model", "")
        is_armor = template.get("slot") in ARMOR_SLOTS
        visual.append({
            "template_id": template_id,
            "armor_family": family.upper() if family else None,
            "slot": template["slot"].upper(),
            "class_restriction": allowed,
            "current_asset": equipment_asset or item_model or f"minecraft:{template['material'].lower()}",
            "missing_equipment_asset": bool(is_armor and not equipment_asset),
            "needs_redesign": bool(is_armor and not equipment_asset),
            "orientation_fit_review_required": bool(is_armor),
        })

    set_families: dict[str, list[str]] = {}
    for set_id in item_config.get("item-sets", {}):
        families = sorted({template["armor-family"].upper() for template in templates.values()
                           if template.get("set-id") == set_id and template.get("armor-family")})
        if len(families) > 1:
            raise ValueError(f"mixed family set: {set_id}")
        set_families[set_id] = families

    canonical_recipes = []
    for recipe_id, recipe in recipes.items():
        output_id = recipe.get("result", {}).get("template")
        if not output_id:
            continue
        output = templates[output_id]
        canonical_recipes.append({
            "recipe_id": recipe_id,
            "output_template": output_id,
            "armor_family": output.get("armor-family", "").upper() or None,
            "current_profession": recipe["profession"],
            "current_materials": list(recipe.get("ingredients", [])),
            "future_processing_chain_needed": bool(output.get("armor-family")),
            "migration_complexity": "HIGH" if output.get("armor-family") else "MEDIUM",
        })
    if len(canonical_recipes) != 15:
        raise ValueError(f"expected 15 canonical gear recipes, found {len(canonical_recipes)}")

    ascension = [template_id for template_id, template in templates.items()
                 if template.get("ascension-path")]
    if len(ascension) != 7:
        raise ValueError(f"expected 7 ascendable templates, found {len(ascension)}")
    armor_count = sum(distribution.values())
    asset_coverage = sum(1 for row in visual
                         if row["armor_family"] and not row["missing_equipment_asset"])
    return {
        "schema": 1,
        "authority": {
            "template_count": len(templates),
            "armor_template_count": armor_count,
            "family_distribution": distribution,
            "class_families": {key.upper(): value for key, value in CLASS_FAMILIES.items()},
            "material_is_not_armor_family": True,
            "basic_survival_gear_exempt": True,
        },
        "migration": migration,
        "sets": set_families,
        "ascension": {"template_count": len(ascension), "family_stable_templates": ascension},
        "budget_report": budgets,
        "resource_pack_handoff": {
            "armor_custom_asset_coverage": f"{asset_coverage}/{armor_count}",
            "inventory": visual,
        },
        "professions_2_handoff": {
            "canonical_recipe_count": len(canonical_recipes),
            "recipes": canonical_recipes,
            "ownership_policy_finalized": False,
        },
    }


def render(report: dict[str, Any]) -> str:
    return json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser()
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--write", action="store_true")
    mode.add_argument("--check", action="store_true")
    args = parser.parse_args()
    expected = render(build_report())
    if args.write:
        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT.write_text(expected, encoding="utf-8")
        print(f"Equipment 2.0 handoff written: {OUTPUT.relative_to(ROOT)}")
        return
    actual = OUTPUT.read_text(encoding="utf-8") if OUTPUT.exists() else ""
    if actual != expected:
        raise SystemExit("Equipment 2.0 handoff is stale; run scripts/generate_equipment_2_report.py --write")
    report = json.loads(actual)
    print("Equipment 2.0 handoff verified: "
          f"{report['authority']['template_count']} templates, "
          f"{report['professions_2_handoff']['canonical_recipe_count']} canonical recipes")


if __name__ == "__main__":
    main()
