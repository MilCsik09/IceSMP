#!/usr/bin/env python3
"""Deterministic combat/item/encounter authority and simulation evidence."""
from __future__ import annotations

import argparse
import copy
import json
import statistics
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

import yaml

from generate_long_term_equipment_catalog import SET_ARMOR, SET_BUDGET, SET_TOUGHNESS, SLOT_SHARE, STAT_WEIGHTS

ROOT = Path(__file__).resolve().parents[1]
CFG = ROOT / "src/main/resources/config"
AUTHORITY = ROOT / "docs/development/combat-balance-authority.json"
DEFAULT_OUTPUT = ROOT / "build/reports/combat-foundation"
CONFIG_FILES = (
    "item-templates.yml", "profession-materials.yml", "profession-recipes.yml", "professions-2.yml",
    "material-economy-expansion.yml", "equipment-catalog-expansion.yml",
    "reward-discoverability-closure.yml", "world.yml", "mob-templates.yml",
)
GROUP = {
    "attack_damage": "offense", "attack_speed": "offense", "ability_power": "offense",
    "max_health": "defense", "armor": "defense", "armor_toughness": "defense",
    "movement_speed": "utility",
}
ARMOR_FAMILIES = ("CLOTH", "LEATHER", "MAIL", "PLATE")
ARMOR_SLOTS = ("head", "chest", "legs", "feet")
MELEE_MATERIALS = ("_SWORD", "_AXE", "TRIDENT", "GOLDEN_HOE", "BLAZE_ROD")


def load(path: Path) -> dict[str, Any]:
    raw = yaml.safe_load(path.read_text(encoding="utf-8")) if path.is_file() else {}
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
        merge(result, load(CFG / name))
    return result


def midpoint_stats(template: dict[str, Any]) -> dict[str, float]:
    values = {str(key): float(value) for key, value in (template.get("fixed-stats") or {}).items()}
    for key, bounds in (template.get("rolled-stats") or {}).items():
        values[str(key)] = values.get(str(key), 0.0) + (float(bounds["min"]) + float(bounds["max"])) / 2.0
    if template.get("base-armor"):
        values["armor"] = values.get("armor", 0.0) + float(template["base-armor"])
    if template.get("base-damage"):
        values["attack_damage"] = values.get("attack_damage", 0.0) + float(template["base-damage"])
    unknown = set(values).difference(STAT_WEIGHTS)
    if unknown:
        raise AssertionError(f"unknown combat stat(s): {sorted(unknown)}")
    return values


def weighted(values: dict[str, float]) -> dict[str, float]:
    result = {"offense": 0.0, "defense": 0.0, "utility": 0.0}
    for stat, amount in values.items():
        result[GROUP[stat]] += max(0.0, amount) * STAT_WEIGHTS[stat]
    return result


def band_for(template: dict[str, Any]) -> str:
    metadata = template.get("encounter-metadata") or {}
    band = str(metadata.get("progression-band", ""))
    if band:
        return band
    level = int(template.get("item-level", 1))
    return "early" if level <= 16 else "mid" if level <= 27 else "high" if level <= 36 else "endgame"


def armor_rows(templates: dict[str, dict[str, Any]]) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for template_id, template in sorted(templates.items()):
        family = str(template.get("armor-family", "")).upper()
        slot = str(template.get("slot", "")).lower()
        if family not in ARMOR_FAMILIES or slot not in ARMOR_SLOTS:
            continue
        values = midpoint_stats(template)
        groups = weighted(values)
        total = sum(groups.values())
        band = band_for(template)
        expected = SET_BUDGET[band] * SLOT_SHARE[slot]
        rows.append({
            "template_id": template_id,
            "family": family,
            "slot": slot.upper(),
            "material": str(template.get("material", "")),
            "band": band.upper(),
            "item_level": int(template["item-level"]),
            "level_requirement": int(template.get("level-requirement", 0)),
            "rarity": str(template.get("rarity", "")).upper(),
            "stats_midpoint": {key: round(value, 5) for key, value in sorted(values.items())},
            "raw_offense": {key: round(value, 5) for key, value in sorted(values.items()) if GROUP[key] == "offense"},
            "raw_defense": {key: round(value, 5) for key, value in sorted(values.items()) if GROUP[key] == "defense"},
            "raw_utility": {key: round(value, 5) for key, value in sorted(values.items()) if GROUP[key] == "utility"},
            "offense_budget": round(groups["offense"], 5),
            "defense_budget": round(groups["defense"], 5),
            "utility_budget": round(groups["utility"], 5),
            "normalized_budget": round(total, 5),
            "expected_budget": round(expected, 5),
            "expected_delta_percent": round((total / expected - 1.0) * 100.0, 3),
            "set_id": str(template.get("set-id", "")),
            "signature_id": str(template.get("signature-effect", "")),
            "ascension_path": list(template.get("ascension-path") or []),
            "vanilla_benchmark_ratio": None,
            "flags": ["PAPER_RUNTIME_RATIO_PENDING"],
        })
    if len(rows) != 160:
        raise AssertionError(f"combat report expected 160 armor rows, found {len(rows)}")
    medians: dict[tuple[str, str], float] = {}
    buckets: dict[tuple[str, str], list[float]] = defaultdict(list)
    for row in rows:
        buckets[(row["band"], row["slot"])].append(float(row["normalized_budget"]))
    for key, values in buckets.items():
        medians[key] = statistics.median(values)
    for row in rows:
        median = medians[(row["band"], row["slot"])]
        delta = (float(row["normalized_budget"]) / median - 1.0) * 100.0
        row["same_band_slot_median"] = round(median, 5)
        row["same_band_median_delta_percent"] = round(delta, 3)
        row["status"] = "VERIFIED" if abs(delta) <= 12.0 and abs(float(row["expected_delta_percent"])) <= 12.0 else "BALANCE_REQUIRED"
    return rows


def combat_item_rows(templates: dict[str, dict[str, Any]]) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for template_id, template in sorted(templates.items()):
        family = str(template.get("family", ""))
        is_shield = str(template.get("slot", "")) == "off-hand" and str(template.get("material", "")) == "SHIELD"
        if family != "weapon" and not is_shield:
            continue
        values = midpoint_stats(template)
        groups = weighted(values)
        material = str(template.get("material", ""))
        melee = material.endswith(MELEE_MATERIALS)
        attack_damage = 1.0 + values.get("attack_damage", 0.0)
        attack_speed = max(0.1, 4.0 + values.get("attack_speed", 0.0))
        row = {
            "template_id": template_id,
            "family": family.upper(),
            "slot": str(template.get("slot", "")).upper(),
            "material": material,
            "item_level": int(template["item-level"]),
            "level_requirement": int(template.get("level-requirement", 0)),
            "rarity": str(template.get("rarity", "")).upper(),
            "stats_midpoint": {key: round(value, 5) for key, value in sorted(values.items())},
            "raw_offense": {key: round(value, 5) for key, value in sorted(values.items()) if GROUP[key] == "offense"},
            "raw_defense": {key: round(value, 5) for key, value in sorted(values.items()) if GROUP[key] == "defense"},
            "raw_utility": {key: round(value, 5) for key, value in sorted(values.items()) if GROUP[key] == "utility"},
            "normalized_budget": round(sum(groups.values()), 5),
            "attack_damage_with_player_base": round(attack_damage, 5) if melee else None,
            "attack_speed_with_player_base": round(attack_speed, 5) if melee else None,
            "melee_dps_signal": round(attack_damage * attack_speed, 5) if melee else None,
            "projectile_damage_authority": "VANILLA_PROJECTILE_RUNTIME" if material in {"BOW", "CROSSBOW", "TRIDENT"} else "NOT_APPLICABLE",
            "vanilla_benchmark_ratio": None,
            "flags": ["PAPER_RUNTIME_RATIO_PENDING"],
            "status": "VERIFIED" if int(template.get("level-requirement", 0)) == max(1, int(template["item-level"]) - 4) else "BALANCE_REQUIRED",
        }
        rows.append(row)
    if len(rows) != 25:
        raise AssertionError(f"combat report expected 25 existing weapon/off-hand rows, found {len(rows)}")
    return rows


BUILD_PROFILES = {
    "CLOTH": {"class": "wizard", "specialization": "elementalist", "role": "caster_support"},
    "LEATHER": {"class": "assassin", "specialization": "phantom", "role": "mobile_offense"},
    "MAIL": {"class": "archer", "specialization": "sharpshooter", "role": "hybrid_ranged"},
    "PLATE": {"class": "warrior", "specialization": "guardian", "role": "defensive_melee"},
}


def player_benchmark_builds(armor: list[dict[str, Any]],
                            combat: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Representative current class/spec loadouts; not a claim about live player skill or latency."""
    builds: list[dict[str, Any]] = []
    for family, profile in BUILD_PROFILES.items():
        builds.append({
            "checkpoint": "STARTER_BASIC", "family_orientation": family, **profile,
            "player_level": 1, "gear_templates": [], "gear_authority": "PAPER_RUNTIME_BASIC_SURVIVAL",
            "normalized_gear_budget": None, "effective_health": None, "effective_dps": None,
            "physical_mitigation": None, "physical_effective_health": None,
            "status": "PAPER_RUNTIME_DENOMINATOR_REQUIRED",
        })
    levels = {"EARLY": 12, "MID": 24, "HIGH": 34, "ENDGAME": 44}
    for band, level in levels.items():
        for family, profile in BUILD_PROFILES.items():
            selected: list[dict[str, Any]] = []
            for slot in ("HEAD", "CHEST", "LEGS", "FEET"):
                candidates = [row for row in armor if row["family"] == family
                              and row["band"] == band and row["slot"] == slot]
                if not candidates:
                    raise AssertionError(f"missing benchmark armor candidates: {family}/{band}/{slot}")
                selected.append(min(candidates, key=lambda row: (
                    abs(float(row["expected_delta_percent"])), row["template_id"])))
            stats: defaultdict[str, float] = defaultdict(float)
            for row in selected:
                for stat, value in row["stats_midpoint"].items():
                    stats[stat] += float(value)
            weapon_candidates = [row for row in combat if int(row["item_level"]) <= level + 4]
            if family == "MAIL":
                ranged = [row for row in weapon_candidates if row["material"] in {"BOW", "CROSSBOW", "TRIDENT"}]
                weapon_candidates = ranged or weapon_candidates
            elif family in {"LEATHER", "PLATE"}:
                melee = [row for row in weapon_candidates if row["melee_dps_signal"] is not None]
                weapon_candidates = melee or weapon_candidates
            weapon = max(weapon_candidates, key=lambda row: (
                float(row["normalized_budget"]), int(row["item_level"]), row["template_id"]))
            for stat, value in weapon["stats_midpoint"].items():
                stats[stat] += float(value)
            armor_value = stats["armor"]
            toughness = stats["armor_toughness"]
            health = 20.0 + stats["max_health"]
            armor_points = min(20.0, max(armor_value / 5.0,
                                        armor_value - 16.0 / (toughness + 8.0)))
            mitigation = min(0.8, armor_points / 25.0)
            attack_damage = max(0.1, 1.0 + stats["attack_damage"])
            attack_speed = max(0.1, 4.0 + stats["attack_speed"])
            dps = attack_damage * attack_speed + stats["ability_power"] * 0.10
            budget = sum(float(row["normalized_budget"]) for row in selected) + float(weapon["normalized_budget"])
            builds.append({
                "checkpoint": band, "family_orientation": family, **profile,
                "player_level": level,
                "gear_templates": [row["template_id"] for row in selected] + [weapon["template_id"]],
                "gear_authority": "EFFECTIVE_CANONICAL_CATALOG_MIDPOINT",
                "stats_midpoint": {key: round(value, 5) for key, value in sorted(stats.items())},
                "normalized_gear_budget": round(budget, 5),
                "effective_health": round(health, 5),
                "effective_dps": round(dps, 5),
                "physical_mitigation": round(mitigation, 6),
                "physical_effective_health": round(health / max(0.2, 1.0 - mitigation), 5),
                "status": "SOURCE_SIMULATION_ONLY",
            })
    return builds


def level_gate_matrix() -> list[dict[str, Any]]:
    return [
        {"identity": "NOT_MANAGED", "profile_ready": False, "restriction": False, "level": False, "result": "NOT_MANAGED", "contributes": True},
        {"identity": "VALID", "profile_ready": False, "restriction": True, "level": False, "result": "PROFILE_NOT_READY", "contributes": False},
        {"identity": "VALID", "profile_ready": True, "restriction": False, "level": False, "result": "RESTRICTED", "contributes": False},
        {"identity": "VALID", "profile_ready": True, "restriction": True, "level": False, "result": "UNDER_LEVEL", "contributes": False},
        {"identity": "VALID", "profile_ready": True, "restriction": True, "level": True, "result": "ACTIVE", "contributes": True},
    ]


def technique_report(config: dict[str, Any]) -> dict[str, Any]:
    abilities: dict[str, dict[str, Any]] = config.get("mob-abilities", {}) or {}
    templates: dict[str, dict[str, Any]] = config.get("mob-templates", {}) or {}
    rank_defaults = config.get("mob-scaling", {}).get("rank-abilities", {}) or {}
    usage = Counter(ability for template in templates.values() for ability in (template.get("abilities") or []))
    return {
        "ability_count": len(abilities),
        "template_count": len(templates),
        "rank_defaults": {str(rank).upper(): list(ids or []) for rank, ids in sorted(rank_defaults.items())},
        "abilities": [{
            "ability_id": ability_id,
            "kind": ability.get("kind"),
            "telegraph_ticks": int(ability.get("telegraph-ticks", 0)),
            "recovery_ticks": int(ability.get("recovery-ticks", 0)),
            "interruptible": bool(ability.get("interruptible", False)),
            "target_rule": str(ability.get("target-rule", "CURRENT_TARGET")),
            "eligible_ranks": list(ability.get("eligible-ranks") or []),
            "eligible_archetypes": list(ability.get("eligible-archetypes") or []),
            "template_uses": usage[ability_id],
            "status": "VERIFIED" if int(ability.get("telegraph-ticks", 0)) >= 10 and int(ability.get("recovery-ticks", 0)) >= 0 else "BALANCE_REQUIRED",
        } for ability_id, ability in sorted(abilities.items())],
    }


def ttk_matrix(config: dict[str, Any], builds: list[dict[str, Any]]) -> list[dict[str, Any]]:
    scaling = config.get("mob-scaling", {}) or {}
    curves = scaling.get("curves", {}) or {}
    ranks = scaling.get("ranks", {}) or {}
    rank_abilities = scaling.get("rank-abilities", {}) or {}
    rows: list[dict[str, Any]] = []
    for build in builds:
        if build["checkpoint"] == "STARTER_BASIC":
            continue
        level = int(build["player_level"])
        player_dps = float(build["effective_dps"])
        player_ehp = float(build["physical_effective_health"])
        base_health = 20.0 * min(float(curves.get("maximum-health-multiplier", 8.0)),
                                 1.0 + max(0, level - 1) * float(curves.get("health-per-level", .08)))
        base_damage = 3.0 * min(float(curves.get("maximum-damage-multiplier", 3.0)),
                                1.0 + max(0, level - 1) * float(curves.get("damage-per-level", .025)))
        for rank, values in sorted(ranks.items()):
            mob_health = base_health * float(values.get("health-multiplier", 1.0))
            mob_damage = base_damage * float(values.get("damage-multiplier", 1.0))
            armor = float(values.get("armor-bonus", 0.0))
            mob_mitigation = min(20.0, max(armor / 5.0, armor - 16.0 / 8.0)) / 25.0
            ttk = mob_health / max(.1, player_dps * (1.0 - mob_mitigation))
            kit = list(rank_abilities.get(rank, []) or [])
            burst_window = 6.0
            burst_damage = mob_damage * (1.0 + min(4, len(kit)) * 0.25)
            incoming_dps = mob_damage / 2.0 + max(0.0, burst_damage - mob_damage) / burst_window
            ttl = player_ehp / max(.1, incoming_dps)
            required_healing = max(0.0, incoming_dps - player_ehp / 30.0)
            rows.append({
                "checkpoint": build["checkpoint"], "player_level": level,
                "player_class": build["class"], "player_specialization": build["specialization"],
                "player_family_orientation": build["family_orientation"],
                "gear_loadout": build["gear_templates"],
                "normalized_gear_budget": build["normalized_gear_budget"],
                "mob_profile": "REPRESENTATIVE_VANILLA_HOSTILE", "rank": str(rank).upper(),
                "technique_kit": kit, "mob_effective_health": round(mob_health, 3),
                "mob_damage_per_hit": round(mob_damage, 3),
                "mob_physical_mitigation": round(mob_mitigation, 6),
                "player_effective_dps": round(player_dps, 3),
                "player_effective_health": round(player_ehp, 3),
                "player_physical_mitigation": build["physical_mitigation"],
                "sustained_incoming_dps": round(incoming_dps, 3),
                "burst_window_seconds": burst_window, "burst_damage": round(burst_damage, 3),
                "seconds_to_kill": round(ttk, 3), "seconds_to_live": round(ttl, 3),
                "required_healing_per_second_for_30s": round(required_healing, 3),
                "status": "VERIFIED" if 1.0 <= ttk <= 75.0 and ttl >= 3.0 else "BALANCE_REQUIRED",
            })
    return rows


def build_report(config: dict[str, Any]) -> dict[str, Any]:
    templates: dict[str, dict[str, Any]] = config.get("item-templates", {}) or {}
    armor = armor_rows(templates)
    combat = combat_item_rows(templates)
    builds = player_benchmark_builds(armor, combat)
    techniques = technique_report(config)
    ttk = ttk_matrix(config, builds)
    passive = config.get("wildlife-retaliation", {}) or {}
    findings = [row["template_id"] for row in armor + combat if row["status"] != "VERIFIED"]
    findings.extend(f"TTK:{row['checkpoint']}:{row['player_family_orientation']}:{row['rank']}"
                    for row in ttk if row["status"] != "VERIFIED")
    findings.extend(f"ABILITY:{row['ability_id']}" for row in techniques["abilities"] if row["status"] != "VERIFIED")
    return {
        "schema": 1,
        "runtime_denominator": {
            "authority": "Paper 1.21.11 fresh ItemStack ATTRIBUTE_MODIFIERS",
            "artifact": "build/reports/combat-foundation/vanilla-runtime-benchmark.json",
            "required_material_families": ["leather", "golden", "chainmail", "iron", "diamond", "netherite", "sword", "axe", "bow", "crossbow", "trident", "shield"],
        },
        "normalized_budget_model": {
            "formula": "sum(max(0, midpoint_stat) * stat_weight)",
            "stat_weights": STAT_WEIGHTS,
            "set_budget_by_band": SET_BUDGET,
            "slot_share": SLOT_SHARE,
            "same_band_outlier_percent": 12.0,
            "negative_attack_speed": "authored pacing cost; excluded from positive power budget but applied at runtime",
        },
        "level_gate": {"precedence": ["identity", "slot", "duplicate_uuid", "profile_ready", "class_family_spec", "level_requirement", "suppression"], "matrix": level_gate_matrix()},
        "armor_count": len(armor),
        "combat_item_count": len(combat),
        "armor": armor,
        "combat_items": combat,
        "player_benchmark_builds": builds,
        "enemy_rank_ttks": ttk,
        "technique_coverage": techniques,
        "wildlife_retaliation": passive,
        "balance_required": sorted(findings),
        "status": "SOURCE_VERIFIED" if not findings and passive.get("enabled") is not None else "BALANCE_REQUIRED",
    }


def dump(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n"


def runtime_join(report: dict[str, Any], benchmark_path: Path) -> dict[str, Any]:
    """Join the external Paper denominator without changing the tracked source authority."""
    benchmark = json.loads(benchmark_path.read_text(encoding="utf-8"))
    if benchmark.get("runtime") != "Paper 1.21.11" or len(benchmark.get("items", [])) != 38:
        raise AssertionError("runtime benchmark must be the exact 38-row Paper 1.21.11 evidence")
    vanilla = {row["material"]: row for row in benchmark["items"]}
    joined = copy.deepcopy(report)
    slot_suffix = {"HEAD": "HELMET", "CHEST": "CHESTPLATE", "LEGS": "LEGGINGS", "FEET": "BOOTS"}
    band_material = {"EARLY": "IRON", "MID": "DIAMOND", "HIGH": "NETHERITE", "ENDGAME": "NETHERITE"}

    def denominator(row: dict[str, Any]) -> float:
        return (max(0.0, float(row.get("armor", 0.0))) * STAT_WEIGHTS["armor"]
                + max(0.0, float(row.get("armor_toughness", 0.0))) * STAT_WEIGHTS["armor_toughness"]
                + max(0.0, float(row.get("attack_damage_modifier", 0.0))) * STAT_WEIGHTS["attack_damage"]
                + max(0.0, float(row.get("attack_speed_modifier", 0.0))) * STAT_WEIGHTS["attack_speed"])

    for row in joined["armor"]:
        reference = f"{band_material[row['band']]}_{slot_suffix[row['slot']]}"
        value = denominator(vanilla[reference])
        row["vanilla_benchmark_material"] = reference
        row["vanilla_benchmark_ratio"] = round(float(row["normalized_budget"]) / value, 6)
        row["flags"] = ["PAPER_RUNTIME_RATIO_VERIFIED"]
    for row in joined["combat_items"]:
        reference = row["material"]
        value = denominator(vanilla[reference])
        row["vanilla_benchmark_material"] = reference
        row["vanilla_benchmark_ratio"] = (round(float(row["normalized_budget"]) / value, 6)
                                           if value > 0.0 else None)
        row["flags"] = (["PAPER_RUNTIME_RATIO_VERIFIED"] if value > 0.0 else
                        ["VANILLA_NON_ATTRIBUTE_COMBAT_AUTHORITY"])
    joined["runtime_denominator"]["sha256"] = __import__("hashlib").sha256(
        benchmark_path.read_bytes()).hexdigest()
    joined["runtime_denominator"]["status"] = "PAPER_RUNTIME_JOINED"
    return joined


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true")
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--output", default=str(DEFAULT_OUTPUT.relative_to(ROOT)))
    parser.add_argument("--runtime-benchmark")
    args = parser.parse_args()
    report = build_report(effective())
    rendered = dump(report)
    output = ROOT / args.output
    output.mkdir(parents=True, exist_ok=True)
    (output / "combat-balance-report.json").write_text(rendered, encoding="utf-8")
    (output / "combat-items.json").write_text(dump({"armor": report["armor"], "combat_items": report["combat_items"]}), encoding="utf-8")
    (output / "player-benchmarks.json").write_text(dump(report["player_benchmark_builds"]), encoding="utf-8")
    (output / "ttk-matrix.json").write_text(dump(report["enemy_rank_ttks"]), encoding="utf-8")
    (output / "technique-coverage.json").write_text(dump(report["technique_coverage"]), encoding="utf-8")
    (output / "level-gate-matrix.json").write_text(dump(report["level_gate"]), encoding="utf-8")
    (output / "passive-wildlife-report.json").write_text(dump(report["wildlife_retaliation"]), encoding="utf-8")
    if args.write:
        AUTHORITY.write_text(rendered, encoding="utf-8")
    if args.check or not args.write:
        if not AUTHORITY.is_file() or AUTHORITY.read_text(encoding="utf-8") != rendered:
            raise SystemExit("combat-balance-authority.json is stale; run audit with --write")
    if report["status"] != "SOURCE_VERIFIED":
        raise SystemExit(f"combat encounter audit requires balance: {report['balance_required'][:20]}")
    if args.runtime_benchmark:
        runtime = runtime_join(report, Path(args.runtime_benchmark).resolve())
        (output / "combat-balance-runtime-report.json").write_text(dump(runtime), encoding="utf-8")
        (output / "combat-items-runtime.json").write_text(dump({
            "armor": runtime["armor"], "combat_items": runtime["combat_items"]}), encoding="utf-8")
    print(f"Combat encounter audit: armor={report['armor_count']}, combat-items={report['combat_item_count']}, status={report['status']}")


if __name__ == "__main__":
    main()
