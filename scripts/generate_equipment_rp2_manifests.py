#!/usr/bin/env python3
"""Derive Equipment RP 2.0 asset-state manifests from current production sources.

This is deliberately not an art generator.  It consumes the production ItemTemplate/material
catalog plus the full-pack reference audit, applies the explicit RP2-A vanilla fallback policy,
and emits deterministic authority files for the current art-production phase.
"""
from __future__ import annotations

import argparse
import json
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

import equipment_rp2_asset_audit as audit

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs/development"
DEFAULT_BUILD = ROOT / "build/reports/equipment-rp2/final"
POLICY = ROOT / "src/main/resources/wearable-fallback-policy.properties"
PILOT_MANIFEST = DOCS / "equipment-rp2-pilot-manifest.json"
PRODUCTION_MANIFEST = DOCS / "equipment-rp2-production-manifest.json"


def properties() -> dict[str, str]:
    result: dict[str, str] = {}
    for raw in POLICY.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        result[key.strip()] = value.strip()
    return result


def csv(raw: str) -> list[str]:
    return [value.strip() for value in raw.split(",") if value.strip()]


def normalized_id(raw: str) -> str:
    value = raw.strip().lower()
    return value if ":" in value else f"icesmp:{value}"


def forced_worn(material: str, suffixes: list[str]) -> bool:
    upper = material.upper()
    return any(upper.endswith(suffix.upper()) for suffix in suffixes)


def dump(data: Any) -> str:
    return json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True) + "\n"


def build() -> tuple[dict[str, Any], dict[str, str], list[str]]:
    policy = properties()
    vanilla_models = {normalized_id(value) for value in csv(policy.get("vanilla-item-model", ""))}
    worn_suffixes = [value.upper() for value in csv(policy.get("vanilla-worn-suffix", ""))]
    presentation_path = PRODUCTION_MANIFEST if PRODUCTION_MANIFEST.is_file() else PILOT_MANIFEST
    presentation_manifest = json.loads(presentation_path.read_text(encoding="utf-8")) if presentation_path.is_file() else {}
    custom_pieces = {
        str(row["template_id"]): row for row in presentation_manifest.get("pieces", [])
        if row.get("production_status") == "FULL_RP2_CUSTOM" or row.get("fallback_status") == "RP2_CUSTOM"
    }
    custom_lines = {str(row["line_id"]) for row in custom_pieces.values()}
    production_active = presentation_path == PRODUCTION_MANIFEST and len(custom_pieces) == 160
    presentation_active = bool(custom_pieces)

    armor, lines = audit.canonical_armor()
    materials = audit.material_rows()
    assets, baseline_safe_delete, broken = audit.classify_assets(armor, materials)

    errors = audit.validate_shape(armor, lines)
    if len(materials) != 27:
        errors.append(f"managed material count is {len(materials)}, expected 27")
    if policy.get("minecraft-version") != "1.21.11":
        errors.append("wearable fallback policy must stay pinned to Minecraft 1.21.11")
    if not worn_suffixes:
        errors.append("vanilla-worn-suffix policy is empty")

    missing_model_ids = {
        normalized_id(str(row["resource_id"]))
        for row in broken if row.get("resource_id")
    }
    uncovered_missing = sorted(missing_model_ids - vanilla_models)
    stale_policy = sorted(vanilla_models - missing_model_ids)
    non_model_broken = [row for row in broken if not row.get("resource_id")]
    if uncovered_missing:
        errors.append(f"missing production item-model ids are not covered by vanilla fallback: {uncovered_missing}")
    if stale_policy:
        errors.append(f"vanilla item-model fallback policy contains stale ids: {stale_policy}")
    if non_model_broken:
        errors.append(f"broken indirect pack references remain: {len(non_model_broken)}")

    forced_armor: list[dict[str, Any]] = []
    legacy_worn_paths: set[str] = set()
    armor_matrix: list[dict[str, Any]] = []
    for source in armor:
        row = dict(source)
        custom_piece = custom_pieces.get(str(row["template_id"]))
        is_forced = forced_worn(str(row["backing_material"]), worn_suffixes) and custom_piece is None
        row["previous_worn_asset"] = row["current_worn_asset"]
        row["previous_worn_definition"] = row["worn_definition"]
        row["previous_worn_textures"] = list(row["worn_textures"])
        if is_forced:
            if row["worn_definition"]:
                legacy_worn_paths.add(str(row["worn_definition"]))
            legacy_worn_paths.update(str(path) for path in row["worn_textures"])
            row["current_worn_asset"] = None
            row["worn_definition"] = None
            row["worn_textures"] = []
            row["current_worn_representation"] = "VANILLA_MATERIAL"
            row["asset_status"] = "VANILLA_FALLBACK"
            forced_armor.append(row)
        elif custom_piece is not None:
            expected_asset = str(custom_piece["equipment_asset"])
            if row["current_worn_asset"] != expected_asset:
                errors.append(f"RP2 worn binding drift for {row['template_id']}: {row['current_worn_asset']} != {expected_asset}")
            row["current_worn_representation"] = "RP2_CUSTOM"
            row["asset_status"] = "ACTIVE"
        else:
            row["asset_status"] = "ACTIVE" if row["current_worn_asset"] else "VANILLA_FALLBACK"
        row["rp2_inventory_texture_required"] = bool(row["rp2_inventory_texture_required"])
        row["rp2_worn_model_required"] = custom_piece is None
        armor_matrix.append(row)

    expected_forced = 160 - len(custom_pieces)
    expected_custom = len(custom_pieces)
    if len(forced_armor) != expected_forced:
        errors.append(f"canonical armor vanilla-worn coverage is {len(forced_armor)}, expected {expected_forced}")
    active_custom = [row for row in armor_matrix if row["current_worn_representation"] == "RP2_CUSTOM"]
    if len(active_custom) != expected_custom:
        errors.append(f"canonical RP2 custom worn coverage is {len(active_custom)}, expected {expected_custom}")

    adjusted_assets: list[dict[str, Any]] = []
    stale_retained: list[dict[str, Any]] = []
    for source in assets:
        row = dict(source)
        row["exists"] = True
        if row["asset_path"] in legacy_worn_paths:
            row["status"] = "STALE"
            row["safe_to_delete"] = False
            row["current_visual_mode"] = "retained-legacy-worn"
            row["reason"] = (
                "RP2-A runtime forces vanilla worn presentation; retained because the historic config/"
                "resource-pack validator still references this compatibility asset"
            )
            stale_retained.append({
                "path": row["asset_path"],
                "status": "STALE",
                "safe_to_delete": False,
                "reason": row["reason"],
            })
        adjusted_assets.append(row)

    # Missing declarations that runtime now suppresses intentionally are explicit fallback records,
    # not broken production references and not physical RP files.
    consumers_by_id: dict[str, list[str]] = defaultdict(list)
    expected_by_id: dict[str, str] = {}
    for row in broken:
        rid = row.get("resource_id")
        if not rid:
            continue
        normalized = normalized_id(str(rid))
        consumers_by_id[normalized].append(str(row.get("consumer", "")))
        expected_by_id[normalized] = str(row.get("expected", ""))

    virtual_fallbacks: list[dict[str, Any]] = []
    for rid in sorted(vanilla_models):
        virtual_fallbacks.append({
            "asset_path": expected_by_id[rid],
            "asset_type": "item_definition",
            "namespace": rid.split(":", 1)[0],
            "category": "profession_items",
            "status": "VANILLA_FALLBACK",
            "exists": False,
            "production_consumers": sorted(consumers_by_id[rid]),
            "indirect_consumers": [],
            "template_ids": [],
            "gear_line": [],
            "family": [],
            "slot": [],
            "current_visual_mode": "vanilla-backing-material",
            "rp2_required": False,
            "safe_to_delete": False,
            "reason": "declared legacy item-model is absent; runtime policy intentionally keeps backing Material inventory presentation",
        })

    for row in forced_armor:
        virtual_fallbacks.append({
            "asset_path": f"vanilla://worn/{row['template_id']}",
            "asset_type": "worn_presentation",
            "namespace": "minecraft",
            "category": "equipment",
            "status": "VANILLA_FALLBACK",
            "exists": False,
            "production_consumers": [f"item-template:{row['template_id']}"],
            "indirect_consumers": [],
            "template_ids": [row["template_id"]],
            "gear_line": [row["gear_line"]],
            "family": [row["family"]],
            "slot": [row["slot"]],
            "current_visual_mode": "vanilla-backing-material",
            "rp2_required": True,
            "safe_to_delete": False,
            "reason": "temporary RP2-A canonical armor worn reset",
        })

    required_new: list[dict[str, Any]] = []
    for row in armor_matrix:
        if not row["rp2_inventory_texture_required"]:
            continue
        required_new.append({
            "asset_path": f"future://inventory/{row['template_id']}",
            "asset_type": "rp2_inventory_requirement",
            "namespace": "icesmp",
            "category": "equipment",
            "status": "REQUIRED_NEW",
            "exists": False,
            "production_consumers": [f"item-template:{row['template_id']}"],
            "indirect_consumers": [],
            "template_ids": [row["template_id"]],
            "gear_line": [row["gear_line"]],
            "family": [row["family"]],
            "slot": [row["slot"]],
            "current_visual_mode": "vanilla-or-shared-current-inventory",
            "rp2_required": True,
            "safe_to_delete": False,
            "reason": row["rp2_inventory_reason"],
        })
    for line in lines:
        if line["line_id"] in custom_lines:
            continue
        required_new.append({
            "asset_path": f"future://worn/{line['line_id']}",
            "asset_type": "rp2_worn_line_requirement",
            "namespace": "icesmp",
            "category": "equipment",
            "status": "REQUIRED_NEW",
            "exists": False,
            "production_consumers": [f"gear-line:{line['line_id']}"],
            "indirect_consumers": [],
            "template_ids": list(line["template_ids"]),
            "gear_line": [line["line_id"]],
            "family": [line["family"]],
            "slot": [],
            "current_visual_mode": "temporary-vanilla-worn",
            "rp2_required": True,
            "safe_to_delete": False,
            "reason": "RP2-D requires one coherent worn visual set for this four-piece gear line",
        })

    line_by_id: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in armor_matrix:
        line_by_id[str(row["gear_line"])].append(row)
    gear_lines: list[dict[str, Any]] = []
    for source in lines:
        line = dict(source)
        pieces = line_by_id[str(line["line_id"])]
        custom_icons = sum(bool(row["item_model"]) for row in pieces)
        line["current_icon_status"] = (
            "CUSTOM_COMPLETE" if custom_icons == 4
            else "VANILLA_COMPLETE" if custom_icons == 0
            else f"MIXED_{custom_icons}_OF_4_CUSTOM"
        )
        line["future_worn_requirement"] = (
            "FULL_RP2_CUSTOM" if line["line_id"] in custom_lines else "ONE_COHERENT_LINE_SET"
        )
        line["rp2_worn_set_required"] = line["line_id"] not in custom_lines
        line["prestige"] = any(row["acquisition"] == "prestige" for row in pieces)
        line["set"] = next((str(row["set"]) for row in pieces if row["set"]), "")
        gear_lines.append(line)

    # Baseline safe-delete candidates are re-evaluated after the runtime reset.  Nothing may be
    # deleted while a validator/config dependency remains; retained legacy files are therefore not candidates.
    safe_delete = [row for row in baseline_safe_delete if row["path"] not in legacy_worn_paths]
    referenced_safe_delete = [
        row for row in adjusted_assets
        if row.get("safe_to_delete") and (row.get("production_consumers") or row.get("indirect_consumers"))
    ]
    if referenced_safe_delete:
        errors.append(f"safe-delete assets are still referenced: {len(referenced_safe_delete)}")

    physical_counts = Counter(str(row["status"]) for row in adjusted_assets)
    presentation_counts = Counter(str(row["status"]) for row in virtual_fallbacks)
    requirement_counts = Counter(str(row["status"]) for row in required_new)
    combined_counts = physical_counts + presentation_counts + requirement_counts

    material_summary = {
        "managed": len(materials),
        "custom_appearance_required": sum(bool(row["custom_appearance_required"]) for row in materials),
        "vanilla_appearance_intentional": sum(row["visual_mode"] == "VANILLA_INTENTIONAL" for row in materials),
        "missing": sum(bool(row["missing"]) for row in materials),
        "stale": 0,
        "orphan": 0,
        "rp2_texture_required": sum(bool(row["rp2_texture_required"]) for row in materials),
    }

    inventory_required = sum(bool(row["rp2_inventory_texture_required"]) for row in armor_matrix)
    summary = {
        "total_rp_files": len(adjusted_assets),
        "classification_records_including_virtual_requirements": len(adjusted_assets) + len(virtual_fallbacks) + len(required_new),
        "physical_asset_status": dict(sorted(physical_counts.items())),
        "all_status": dict(sorted(combined_counts.items())),
        "safe_to_delete": len(safe_delete),
        "actually_deleted": 0,
        "canonical_armor": len(armor_matrix),
        "gear_lines": len(gear_lines),
        "armor_pieces_with_valid_inventory_representation": sum(
            bool(row["inventory_representation_valid"]) for row in armor_matrix
        ),
        "armor_pieces_temporarily_vanilla_worn": len(forced_armor),
        "custom_worn_assets_still_active": len(active_custom),
        "custom_worn_assets_removed": 0,
        "retained_legacy_worn_files": len(stale_retained),
        "rp2_inventory_replacements_required": inventory_required,
        "rp2_worn_line_sets_required": len(gear_lines) - len(custom_lines),
        "managed_materials": len(materials),
        "material_item_textures_requiring_rp2_work": material_summary["rp2_texture_required"],
        "other_equipment_textures_requiring_rp2_work": 0,
        "intentional_recipe_inventory_fallbacks": len(vanilla_models),
        "broken_production_reference": len(uncovered_missing) + len(non_model_broken),
        "missing_mandatory_active_asset": 0,
        "safe_delete_asset_referenced": len(referenced_safe_delete),
        "stale_active_reference": 0,
        "focus_unknown": sum(
            row["status"] == "UNKNOWN_REVIEW_REQUIRED" and row["category"] in {"equipment", "materials"}
            for row in adjusted_assets
        ),
    }
    if summary["focus_unknown"]:
        errors.append(f"equipment/material focus unknown assets remain: {summary['focus_unknown']}")
    if summary["broken_production_reference"]:
        errors.append(f"broken production references remain: {summary['broken_production_reference']}")

    readiness = (
        "SOURCE_COMPLETE_AUTOMATED_TESTED_OFFLINE_VISUAL_PROVED" if production_active and not errors
        else "AUTOMATED_VISUAL_PIPELINE_COMPLETE" if presentation_active and not errors
        else "READY FOR ART BIBLE" if not errors else "NOT READY"
    )
    final_authority = {
        "schema": 1,
        "scope": ("Equipment Resource Pack 2.0-C Full Production" if production_active
                  else "Equipment Resource Pack 2.0-B Art Bible + Four-Family Production Pilot"
                  if presentation_active else "Equipment Resource Pack 2.0-A"),
        "minecraft_version": policy.get("minecraft-version"),
        "summary": summary,
        "material_summary": material_summary,
        "readiness": readiness,
        "errors": errors,
        "retained_stale": stale_retained,
        "broken_references": [] if not errors else non_model_broken,
    }

    docs = {
        "equipment-rp2-asset-manifest.json": dump({
            "schema": 1,
            "summary": summary,
            "physical_assets": adjusted_assets,
            "virtual_fallbacks": virtual_fallbacks,
            "required_new": required_new,
        }),
        "equipment-rp2-armor-matrix.json": dump({"schema": 1, "armor": armor_matrix}),
        "equipment-rp2-gear-lines.json": dump({"schema": 1, "gear_lines": gear_lines}),
        "equipment-rp2-materials.json": dump({"schema": 1, "summary": material_summary, "materials": materials}),
        "equipment-rp2-worn-fallback.json": dump({
            "schema": 1,
            "vanilla_fallback_count": len(forced_armor),
            "rp2_custom_count": len(active_custom),
            "previous_custom_worn_count": sum(bool(row["previous_worn_asset"]) for row in armor_matrix),
            "retained_legacy_files": stale_retained,
            "armor": [
                {
                    "template_id": row["template_id"],
                    "family": row["family"],
                    "slot": row["slot"],
                    "backing_material": row["backing_material"],
                    "previous_worn_asset": row["previous_worn_asset"],
                    "current_worn_representation": row["current_worn_representation"],
                }
                for row in armor_matrix
            ],
        }),
        "equipment-rp2-safe-delete.json": dump({"schema": 1, "safe_to_delete": safe_delete}),
        "equipment-rp2-required-new.json": dump({
            "schema": 1,
            "inventory_item_requirements": [row for row in required_new if row["asset_type"] == "rp2_inventory_requirement"],
            "worn_line_requirements": [row for row in required_new if row["asset_type"] == "rp2_worn_line_requirement"],
            "material_item_texture_requirements": [],
            "other_equipment_texture_requirements": [],
        }),
        "equipment-rp2-final-authority.json": dump(final_authority),
    }
    return final_authority, docs, errors


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", default=str(DEFAULT_BUILD.relative_to(ROOT)))
    parser.add_argument("--write-docs", action="store_true")
    parser.add_argument("--check-docs", action="store_true")
    parser.add_argument("--enforce", action="store_true")
    args = parser.parse_args()

    authority, docs, errors = build()
    output_dir = ROOT / args.output_dir
    output_dir.mkdir(parents=True, exist_ok=True)
    for name, content in docs.items():
        (output_dir / name).write_text(content, encoding="utf-8")

    if args.write_docs:
        DOCS.mkdir(parents=True, exist_ok=True)
        for name, content in docs.items():
            (DOCS / name).write_text(content, encoding="utf-8")
    if args.check_docs:
        drift: list[str] = []
        for name, content in docs.items():
            target = DOCS / name
            if not target.is_file() or target.read_text(encoding="utf-8") != content:
                drift.append(name)
        if drift:
            raise SystemExit("RP2 manifest drift: " + ", ".join(drift))

    summary = authority["summary"]
    print(
        "Equipment RP2 final authority: "
        f"files={summary['total_rp_files']}, armor={summary['canonical_armor']}, "
        f"lines={summary['gear_lines']}, managed-materials={summary['managed_materials']}, "
        f"vanilla-worn={summary['armor_pieces_temporarily_vanilla_worn']}, "
        f"recipe-fallbacks={summary['intentional_recipe_inventory_fallbacks']}, "
        f"required-inventory={summary['rp2_inventory_replacements_required']}, "
        f"required-worn-lines={summary['rp2_worn_line_sets_required']}, "
        f"safe-delete={summary['safe_to_delete']}, broken={summary['broken_production_reference']}"
    )
    print("RP2_READINESS=" + authority["readiness"])
    if args.enforce and errors:
        raise SystemExit("; ".join(errors))


if __name__ == "__main__":
    main()
