#!/usr/bin/env python3
"""Publish/check compact committed Equipment RP2-A authority documents.

The full 1791-file reference graph remains a deterministic CI artifact from
`equipment_rp2_asset_audit.py`. The repository keeps the complete canonical 160-piece/40-line
handoff plus every equipment/material/armor-linked physical asset record, while a SHA-256
digest pins the full physical inventory without duplicating unrelated HUD/UI graph data.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

import generate_equipment_rp2_manifests as generator

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs/development"


def canonical(value: object) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def armor_handoff(raw: dict) -> dict:
    return {
        "template_id": raw["template_id"],
        "display_name": raw["display_name"],
        "family": raw["family"],
        "slot": raw["slot"],
        "gear_line": raw["gear_line"],
        "archetype": raw["archetype"],
        "progression_band": raw["progression_band"],
        "acquisition": raw["acquisition"],
        "profession": raw["profession"],
        "rarity": raw["rarity"],
        "set": raw["set"],
        "signature": raw["signature"],
        "ascension": raw["ascension"],
        "backing_material": raw["backing_material"],
        "current_inventory_item_model": raw["item_model"],
        "current_inventory_textures": raw["item_textures"],
        "current_worn_representation": raw["current_worn_representation"],
        "current_asset_status": raw["asset_status"],
        "rp2_inventory_texture_required": raw["rp2_inventory_texture_required"],
        "rp2_worn_model_required": raw["rp2_worn_model_required"],
        "notes": raw["rp2_inventory_reason"],
    }


def requirement_handoff(raw: dict) -> dict:
    return {
        "status": raw["status"],
        "asset_type": raw["asset_type"],
        "template_ids": raw["template_ids"],
        "gear_line": raw["gear_line"],
        "family": raw["family"],
        "slot": raw["slot"],
        "current_visual_mode": raw["current_visual_mode"],
        "reason": raw["reason"],
    }


def published() -> dict[str, str]:
    _authority, generated, errors = generator.build()
    if errors:
        raise SystemExit("RP2 authority is not publishable: " + "; ".join(errors))

    raw_manifest = json.loads(generated["equipment-rp2-asset-manifest.json"])
    all_physical = raw_manifest["physical_assets"]
    focus = [
        row for row in all_physical
        if row.get("category") in {"equipment", "materials"}
        or row.get("template_ids")
    ]
    physical_digest = hashlib.sha256(canonical(all_physical).encode("utf-8")).hexdigest()
    compact_manifest = {
        "schema": 1,
        "scope": "Equipment Resource Pack 2.0-A canonical asset manifest",
        "summary": raw_manifest["summary"],
        "full_pack_inventory": {
            "physical_file_count": len(all_physical),
            "sha256": physical_digest,
            "artifact_source": "scripts/equipment_rp2_asset_audit.py",
            "policy": "unrelated files remain in the full audit graph and are never deletion candidates by omission",
        },
        "physical_focus_assets": focus,
        "virtual_authority": {
            "vanilla_fallback_count": len(raw_manifest["virtual_fallbacks"]),
            "required_new_count": len(raw_manifest["required_new"]),
            "records": [
                "equipment-rp2-worn-fallback.json",
                "equipment-rp2-required-new.json",
            ],
        },
    }

    raw_armor = json.loads(generated["equipment-rp2-armor-matrix.json"])
    armor_matrix = {"schema": 1, "armor": [armor_handoff(row) for row in raw_armor["armor"]]}

    raw_required = json.loads(generated["equipment-rp2-required-new.json"])
    required_new = {
        "schema": 1,
        "inventory_item_requirements": [requirement_handoff(row) for row in raw_required["inventory_item_requirements"]],
        "worn_line_requirements": [requirement_handoff(row) for row in raw_required["worn_line_requirements"]],
        "material_item_texture_requirements": raw_required["material_item_texture_requirements"],
        "other_equipment_texture_requirements": raw_required["other_equipment_texture_requirements"],
    }

    result: dict[str, str] = {
        "equipment-rp2-asset-manifest.json": canonical(compact_manifest) + "\n",
        "equipment-rp2-armor-matrix.json": canonical(armor_matrix) + "\n",
        "equipment-rp2-required-new.json": canonical(required_new) + "\n",
    }
    for name in (
        "equipment-rp2-gear-lines.json",
        "equipment-rp2-materials.json",
        "equipment-rp2-worn-fallback.json",
        "equipment-rp2-safe-delete.json",
        "equipment-rp2-final-authority.json",
    ):
        result[name] = canonical(json.loads(generated[name])) + "\n"
    return result


def main() -> None:
    parser = argparse.ArgumentParser()
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--write", action="store_true")
    mode.add_argument("--check", action="store_true")
    args = parser.parse_args()

    docs = published()
    DOCS.mkdir(parents=True, exist_ok=True)
    if args.write:
        for name, content in docs.items():
            (DOCS / name).write_text(content, encoding="utf-8")
        print(f"Published {len(docs)} RP2-A authority documents.")
        return

    drift = [
        name for name, content in docs.items()
        if not (DOCS / name).is_file() or (DOCS / name).read_text(encoding="utf-8") != content
    ]
    if drift:
        raise SystemExit("RP2 committed manifest drift: " + ", ".join(drift))
    print(f"RP2 committed authority check passed: {len(docs)} documents.")


if __name__ == "__main__":
    main()
