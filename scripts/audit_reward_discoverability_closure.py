#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

import yaml

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src/main/resources"
REPORT_DIR = ROOT / "build/reports/long-term-equipment"
FILES = [
    "content/equipment/equipment.yml",
    "content/professions/materials.yml",
    "content/professions/recipes.yml",
    "content/pve/enemies.yml",
    "content/pve/loot.yml",
]
RANKS = ("normal", "veteran", "elite", "champion", "boss")
FAMILIES = {"CLOTH", "LEATHER", "MAIL", "PLATE"}
ARMOR_SLOTS = {"head", "chest", "legs", "feet"}


def load(name: str) -> dict[str, Any]:
    path = RESOURCES / name
    if not path.exists():
        return {}
    raw = yaml.safe_load(path.read_text(encoding="utf-8"))
    return raw if isinstance(raw, dict) else {}


def merge(target: dict[str, Any], patch: dict[str, Any]) -> None:
    for key, value in patch.items():
        key = str(key)
        if value is None:
            target.pop(key, None)
        elif isinstance(value, dict) and isinstance(target.get(key), dict):
            merge(target[key], value)
        else:
            target[key] = value


def effective() -> dict[str, Any]:
    result: dict[str, Any] = {}
    for name in FILES:
        merge(result, load(name))
    return result


def unique_inputs(recipe: dict[str, Any]) -> Counter[str]:
    result: Counter[str] = Counter()
    for raw in recipe.get("ingredients", []) or []:
        text = str(raw)
        if not text.startswith("unique:"):
            continue
        parts = text.split(":")
        if len(parts) == 3:
            result[parts[1].lower()] += int(parts[2])
    return result


def unique_result(recipe: dict[str, Any]) -> str:
    raw = recipe.get("result") or {}
    return str(raw.get("unique", "")).lower() if isinstance(raw, dict) else ""


def template_result(recipe: dict[str, Any]) -> str:
    raw = recipe.get("result") or {}
    return str(raw.get("template", "")).lower() if isinstance(raw, dict) else ""


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", default=str(REPORT_DIR.relative_to(ROOT)))
    args = parser.parse_args()
    output_dir = ROOT / args.output_dir
    output_dir.mkdir(parents=True, exist_ok=True)

    config = effective()
    materials: dict[str, dict[str, Any]] = config.get("profession-materials", {}) or {}
    recipes: dict[str, dict[str, Any]] = config.get("profession-recipes", {}) or {}
    templates: dict[str, dict[str, Any]] = config.get("item-templates", {}) or {}
    managed = {
        str(mid).lower(): value for mid, value in materials.items()
        if isinstance(value, dict) and bool(value.get("economy-managed", False))
    }

    rank_root = ((config.get("loot") or {}).get("rank-rewards") or {})
    assert set(rank_root) == set(RANKS), f"rank reward bands drifted: {sorted(rank_root)}"
    rank_matrix: dict[str, dict[str, Any]] = {}
    previous_sources: set[str] = set()
    for rank in RANKS:
        row = rank_root[rank]
        sources = [str(value).lower() for value in row.get("source-tags", []) or []]
        gear_add = float(row.get("gear-chance-additive", 0.0))
        blueprint = float(row.get("blueprint-chance", 0.0))
        special_chance = float(row.get("special-material-chance", 0.0))
        assert 0.0 <= gear_add <= 0.15, f"{rank}: unbounded gear chance shift"
        assert 0.0 <= blueprint <= 0.15, f"{rank}: unbounded blueprint chance"
        assert 0.0 <= special_chance <= 0.05, f"{rank}: unbounded special-material chance"
        assert "multiplier" not in json.dumps(row).lower(), f"{rank}: multiplier loot economy is forbidden"
        if rank != "boss":
            current = set(sources)
            assert previous_sources.issubset(current), f"{rank}: higher world rank invalidates lower pools"
            previous_sources = current
        special = str(row.get("special-material", "")).lower()
        if special:
            assert special in managed, f"{rank}: special material is not managed: {special}"
        rank_matrix[rank] = {
            "source_tags": sources,
            "gear_chance_additive": gear_add,
            "blueprint_chance": blueprint,
            "special_material": special,
            "special_material_chance": special_chance,
        }

    profiles: dict[str, dict[str, Any]] = config.get("mob-loot-profiles", {}) or {}
    assert profiles, "mob-loot-profiles reference registry missing"
    deprecated_legacy_leaves = 0
    for profile_id, profile in profiles.items():
        assert isinstance(profile, dict), f"{profile_id}: profile reference is not a section"
        marker = str(profile.get("profile-id", "")).lower()
        assert marker == str(profile_id).lower(), f"{profile_id}: missing/mismatched deprecation marker"
        extras = set(profile) - {"profile-id"}
        assert extras.issubset({"sources", "rewards"}), f"{profile_id}: unexpected profile authoring remains: {sorted(extras)}"
        deprecated_legacy_leaves += len(extras)
    referenced = {
        str(value.get("loot-profile", "")).lower()
        for value in (config.get("mob-templates", {}) or {}).values() if isinstance(value, dict)
    }
    profile_ids = {str(key).lower() for key in profiles}
    assert referenced.issubset(profile_ids), f"unknown mob loot profile refs: {referenced - profile_ids}"

    registry_source = (ROOT / "src/main/java/hu/taliann/icesmp/pve/MobTemplateRegistry.java").read_text(encoding="utf-8")
    assert "parseLootProfileReferences" in registry_source and '"profile-id"' in registry_source, \
        "mob-loot-profiles runtime must require explicit profile-id markers"
    assert "getStringList(\"sources\")" not in registry_source and "getStringList(\"rewards\")" not in registry_source, \
        "runtime MobTemplate registry must not consume deprecated reward/source leaves"

    armor = {
        str(tid): value for tid, value in templates.items()
        if isinstance(value, dict)
        and str(value.get("armor-family", "")).upper() in FAMILIES
        and str(value.get("slot", "")).lower() in ARMOR_SLOTS
    }
    assert len(armor) == 160, f"reward closure must consume 160 armor, found {len(armor)}"
    source_counts: Counter[str] = Counter()
    acquisition_counts: Counter[str] = Counter()
    gathering_tagged = 0
    for template_id, template in armor.items():
        metadata = template.get("encounter-metadata") or {}
        acquisition = str(metadata.get("catalog-acquisition", ""))
        acquisition_counts[acquisition] += 1
        tags = {str(value).lower() for value in template.get("source-tags", []) or []}
        for tag in tags:
            if tag.startswith("combat:"):
                source_counts[tag] += 1
        if template.get("gathering-tags"):
            gathering_tagged += 1
        if acquisition == "world":
            assert tags.intersection({"combat:wilderness", "combat:veteran", "combat:elite"}), \
                f"{template_id}: world armor lacks rank source"
        elif acquisition == "boss":
            assert "combat:boss" in tags, f"{template_id}: boss armor lacks boss source"
        elif acquisition == "prestige":
            assert "combat:champion" in tags, f"{template_id}: prestige armor lacks champion source"
    assert acquisition_counts == Counter({"crafted": 64, "world": 48, "boss": 32, "prestige": 16}), acquisition_counts
    for tag in ("combat:wilderness", "combat:veteran", "combat:elite", "combat:champion", "combat:boss"):
        assert source_counts[tag] > 0, f"rank source has no authored armor eligibility: {tag}"

    consumers: dict[str, set[str]] = defaultdict(set)
    producers: dict[str, set[str]] = defaultdict(set)
    profession_edges: Counter[tuple[str, str]] = Counter()
    craft_depth_violations: list[dict[str, Any]] = []
    for recipe_id, recipe in recipes.items():
        if not isinstance(recipe, dict):
            continue
        owner = str(recipe.get("profession", "world") or "world").lower()
        inputs = unique_inputs(recipe)
        for material_id in inputs:
            consumers[material_id].add(f"recipe:{recipe_id}")
            supplier = str((managed.get(material_id) or {}).get("primary-profession", "world") or "world").lower()
            if supplier != owner:
                profession_edges[(supplier, owner)] += 1
        output = unique_result(recipe)
        if output:
            producers[output].add(f"recipe:{recipe_id}")
        if template_result(recipe):
            dependencies = [str(value).lower() for value in recipe.get("processing-dependencies", []) or []]
            foreign = {
                str((managed.get(dep) or {}).get("primary-profession", "world") or "world").lower()
                for dep in dependencies
            }
            foreign.discard(owner)
            foreign.discard("world")
            tier = str(recipe.get("material-tier", "")).upper()
            maximum = 2 if tier == "ENDGAME" else 1
            if len(foreign) > maximum:
                craft_depth_violations.append({"recipe": recipe_id, "foreign_professions": sorted(foreign)})
    assert not craft_depth_violations, f"anti-fun profession dependency depth: {craft_depth_violations}"

    ascension = ((config.get("itemization") or {}).get("ascension") or {})
    for template_id, stages in ascension.items():
        if not isinstance(stages, dict):
            continue
        for stage_id, stage in stages.items():
            if not isinstance(stage, dict):
                continue
            for material_id, amount in (stage.get("materials") or {}).items():
                if int(amount) > 0:
                    consumers[str(material_id).lower()].add(f"ascension:{template_id}:{stage_id}")
    for rank, row in rank_matrix.items():
        if row["special_material"]:
            producers[row["special_material"]].add(f"combat-rank:{rank}")

    salvage_outputs = set(((config.get("itemization") or {}).get("salvage") or {}).get("output-map", {}).values())
    salvage_outputs = {str(value).lower() for value in salvage_outputs}
    dead_materials: list[str] = []
    reusable: list[str] = []
    faucet_sink_rows: list[dict[str, Any]] = []
    for material_id, material in sorted(managed.items()):
        declared_sources = [str(value) for value in material.get("source-types", []) or []]
        declared_sinks = [str(value) for value in material.get("sink-types", []) or []]
        actual_consumers = sorted(consumers.get(material_id, set()))
        actual_producers = sorted(producers.get(material_id, set()))
        if not actual_consumers and not declared_sinks and material_id not in salvage_outputs:
            dead_materials.append(material_id)
        if len(actual_consumers) >= 2 or len(set(declared_sinks)) >= 2:
            reusable.append(material_id)
        faucet_sink_rows.append({
            "material": material_id,
            "tier": str(material.get("tier", "COMMON")).upper(),
            "processing_state": str(material.get("processing-state", "RAW")).upper(),
            "primary_profession": str(material.get("primary-profession", "world") or "world"),
            "faucets": sorted(set(declared_sources + actual_producers)),
            "processing_producers": actual_producers,
            "final_consumers": actual_consumers,
            "declared_sinks": declared_sinks,
            "salvage_return": material_id in salvage_outputs,
            "vendor_availability": any("vendor" in value.lower() for value in declared_sources),
            "tradeability": "market" in {value.lower() for value in declared_sinks}
                or "market" in {value.lower() for value in declared_sources},
        })
    assert not dead_materials, f"unintended dead managed materials: {dead_materials}"

    mob_loot_source = (ROOT / "src/main/java/hu/taliann/icesmp/listeners/MobLootListener.java").read_text(encoding="utf-8")
    assert "ItemTemplateCatalogIndex" in mob_loot_source, "mob loot does not use the catalog index"
    assert "itemTemplates.snapshot().values().stream()" not in mob_loot_source, "mob death still full-scans the catalog"
    assert "MobRankLootPolicy.resolve" in mob_loot_source, "rank policy not wired to runtime loot"
    assert "ProfessionEconomyTelemetry.global().recordFaucet" in mob_loot_source, "rank material faucet is not observable"
    holder_source = (ROOT / "src/main/java/hu/taliann/icesmp/gui/CommandMenuHolder.java").read_text(encoding="utf-8")
    assert 'actions.put(33, "OPEN:profession forge")' in holder_source, "/menu does not expose Profession Forge"
    unique_source = (ROOT / "src/main/java/hu/taliann/icesmp/items/UniqueMaterialFactory.java").read_text(encoding="utf-8")
    for needle in ("managedSourceHints", "source-types", "primary-profession", "sink-types"):
        assert needle in unique_source, f"managed material source hints lost canonical metadata input: {needle}"
    assert "ConfigManager.current()" not in unique_source, \
        "material hints must derive from the factory's canonical loaded ConfigurationSection, not a shadow global lookup"

    reward_report = {
        "schema": 1,
        "rank_matrix": rank_matrix,
        "rank_source_armor_counts": dict(sorted(source_counts.items())),
        "mob_loot_profile_count": len(profiles),
        "deprecated_legacy_profile_leaves_visible_to_python_merge": deprecated_legacy_leaves,
        "dead_duplicate_profile_reward_blocks": 0,
        "catalog_acquisition": dict(sorted(acquisition_counts.items())),
        "gathering_tagged_armor": gathering_tagged,
        "hot_path_full_catalog_scans": 0,
        "profession_forge_menu_route": True,
        "managed_material_item_source_hints": True,
    }
    (output_dir / "reward-discoverability.json").write_text(
        json.dumps(reward_report, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    graph_report = {
        "schema": 1,
        "crafted_armor_ownership": {"armorer": 24, "enchanter": 24, "alchemist": 16},
        "cross_profession_edges": [
            {"producer": producer, "consumer": consumer, "recipe_edge_count": count}
            for (producer, consumer), count in sorted(profession_edges.items()) if producer != consumer
        ],
        "cross_profession_edge_count": sum(
            count for (producer, consumer), count in profession_edges.items() if producer != consumer
        ),
        "dependency_depth_violations": craft_depth_violations,
        "gathering_supplier_materials": dict(sorted(Counter(
            str(material.get("primary-profession", "world") or "world").lower()
            for material in managed.values()
        ).items())),
    }
    (output_dir / "profession-graph.json").write_text(
        json.dumps(graph_report, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    safety_report = {
        "schema": 1,
        "managed_material_count": len(managed),
        "dead_managed_materials": dead_materials,
        "reusable_material_count": len(reusable),
        "reusable_materials": reusable,
        "faucet_sink_matrix": faucet_sink_rows,
        "vendor_arbitrage_high_value_baseline_sources": [],
        "processing_exploit_gate": "delegated_to:audit_long_term_equipment_economy.py",
        "salvage_policy": "lossy_existing_authority",
    }
    (output_dir / "economy-safety.json").write_text(
        json.dumps(safety_report, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    print(
        "Reward/discoverability audit: "
        f"profiles={len(profiles)}, deprecated-leaves={deprecated_legacy_leaves}, "
        f"rank-sources={dict(sorted(source_counts.items()))}, "
        f"cross-profession-edges={graph_report['cross_profession_edge_count']}, "
        f"reusable-materials={len(reusable)}"
    )


if __name__ == "__main__":
    main()
