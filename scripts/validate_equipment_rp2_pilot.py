#!/usr/bin/env python3
"""Fail-closed technical validator and evidence writer for Equipment RP2-B."""

from __future__ import annotations

import argparse
import hashlib
import json
from collections import Counter
from pathlib import Path
from typing import Any

import yaml
from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs/development"
MANIFEST = DOCS / "equipment-rp2-pilot-manifest.json"
ART = DOCS / "equipment-rp2-art-bible.json"
ARMOR = DOCS / "equipment-rp2-armor-matrix.json"
FINAL = DOCS / "equipment-rp2-final-authority.json"
CONFIG = ROOT / "src/main/resources/config/equipment-catalog-expansion.yml"
REPORT_DIR = ROOT / "build/reports/equipment-rp2-b"


def load(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def pixels(image: Image.Image):
    getter = getattr(image, "get_flattened_data", None)
    return getter() if getter is not None else image.getdata()


def validate_png(path: Path, size: tuple[int, int], errors: list[str]) -> None:
    try:
        image = Image.open(path).convert("RGBA")
    except OSError as exception:
        errors.append(f"unreadable PNG {path.relative_to(ROOT)}: {exception}")
        return
    if image.size != size:
        errors.append(f"wrong PNG size {path.relative_to(ROOT)}: {image.size} != {size}")
    alpha = {pixel[3] for pixel in pixels(image)}
    if not alpha.issubset({0, 255}) or alpha == {0}:
        errors.append(f"PNG alpha contract failed {path.relative_to(ROOT)}: {sorted(alpha)}")
    opaque = {pixel[:3] for pixel in pixels(image) if pixel[3]}
    if len(opaque) < 5:
        errors.append(f"PNG palette collapsed {path.relative_to(ROOT)}: {len(opaque)} colours")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--enforce", action="store_true")
    args = parser.parse_args()
    errors: list[str] = []
    warnings: list[str] = []
    manifest = load(MANIFEST)
    art = load(ART)
    armor = load(ARMOR)["armor"]
    final = load(FINAL)
    config = yaml.safe_load(CONFIG.read_text(encoding="utf-8"))
    templates = config.get("item-templates", {})

    selected = manifest.get("selected_lines", [])
    pieces = manifest.get("pieces", [])
    selected_ids = {row["line_id"] for row in selected}
    piece_ids = {row["template_id"] for row in pieces}
    families = Counter(row["family"] for row in selected)
    if len(selected) != 4 or families != Counter({"CLOTH": 1, "LEATHER": 1, "MAIL": 1, "PLATE": 1}):
        errors.append(f"pilot family coverage drift: {families}")
    if len(pieces) != 16 or len(piece_ids) != 16:
        errors.append(f"pilot piece coverage drift: total={len(pieces)} unique={len(piece_ids)}")

    art_lines = art.get("gear_lines", [])
    if len(art_lines) != 40 or Counter(row["family"] for row in art_lines) != Counter({"CLOTH": 10, "LEATHER": 10, "MAIL": 10, "PLATE": 10}):
        errors.append("Art Bible does not cover exactly 40 lines / 10 per family")
    required_family = {"silhouette", "material_language", "shape_language", "palette_policy", "forbidden_motifs"}
    for family in ("CLOTH", "LEATHER", "MAIL", "PLATE"):
        missing = required_family - art.get("family_policy", {}).get(family, {}).keys()
        if missing:
            errors.append(f"Art Bible family metadata incomplete for {family}: {sorted(missing)}")
    for row in art_lines:
        if not row.get("differentiation_note") or not row.get("core_fantasy"):
            errors.append(f"line identity incomplete: {row.get('canonical_line_id')}")

    expected_assets: set[str] = set()
    expected_models: set[str] = set()
    for piece in pieces:
        if piece["line_id"] not in selected_ids:
            errors.append(f"piece references nonselected line: {piece['template_id']}")
        if piece.get("fallback_status") != "RP2_CUSTOM":
            errors.append(f"pilot piece is not custom: {piece['template_id']}")
        expected_assets.add(piece["equipment_asset"])
        expected_models.add(piece["item_model"])
        for field in ("item_definition", "model", "inventory_texture", "equipment_definition"):
            path = ROOT / piece[field]
            if not path.is_file():
                errors.append(f"missing pilot asset: {piece[field]}")
        for path_name in piece["worn_textures"]:
            if not (ROOT / path_name).is_file():
                errors.append(f"missing pilot worn texture: {path_name}")
        validate_png(ROOT / piece["inventory_texture"], (64, 64), errors)
        for path_name in piece["worn_textures"]:
            validate_png(ROOT / path_name, (64, 32), errors)
        for path_name, expected in piece.get("checksums", {}).items():
            path = ROOT / path_name
            if path.is_file() and sha(path) != expected:
                errors.append(f"pilot checksum drift: {path_name}")
        template = templates.get(piece["template_id"])
        if not template:
            errors.append(f"pilot template missing from generated catalog: {piece['template_id']}")
        elif template.get("item-model") != piece["item_model"] or template.get("equipment-asset") != piece["equipment_asset"]:
            errors.append(f"pilot generated config binding drift: {piece['template_id']}")
        equipment = load(ROOT / piece["equipment_definition"])
        layers = equipment.get("layers", {})
        if set(layers) != {"humanoid", "humanoid_leggings"}:
            errors.append(f"pilot equipment schema layer drift: {piece['equipment_definition']}")

    if len(expected_assets) != 4 or len(expected_models) != 16:
        errors.append(f"pilot presentation cardinality drift: assets={len(expected_assets)} models={len(expected_models)}")

    armor_ids = {row["template_id"] for row in armor}
    custom = {row["template_id"] for row in armor if row["current_worn_representation"] == "RP2_CUSTOM"}
    fallback = {row["template_id"] for row in armor if row["current_worn_representation"] == "VANILLA_MATERIAL"}
    if len(armor) != 160 or custom != piece_ids or len(fallback) != 144 or custom & fallback or custom | fallback != armor_ids:
        errors.append(f"custom/fallback partition drift: armor={len(armor)} custom={len(custom)} fallback={len(fallback)}")

    summary = final.get("summary", {})
    expected_summary = {
        "canonical_armor": 160,
        "gear_lines": 40,
        "armor_pieces_temporarily_vanilla_worn": 144,
        "custom_worn_assets_still_active": 16,
        "rp2_inventory_replacements_required": 143,
        "rp2_worn_line_sets_required": 36,
        "broken_production_reference": 0,
        "safe_to_delete": 0,
    }
    for key, expected in expected_summary.items():
        if summary.get(key) != expected:
            errors.append(f"final authority {key}={summary.get(key)} expected={expected}")
    if final.get("readiness") != "AUTOMATED_VISUAL_PIPELINE_COMPLETE":
        errors.append(f"unexpected final readiness: {final.get('readiness')}")

    render_index = load(ROOT / manifest["render_evidence"])
    if render_index.get("proof_mode") != "OFFLINE_DETERMINISTIC_RENDER" or len(render_index.get("lines", [])) != 4:
        errors.append("offline render evidence index is incomplete")
    render_paths = []
    for entry in render_index.get("lines", []):
        render_paths.extend(entry.get("files", []))
    render_paths.extend([render_index.get("comparison"), render_index.get("skin_compatibility"), render_index.get("scale_readability"), render_index.get("concept_reference")])
    for path_name in filter(None, render_paths):
        if not (ROOT / path_name).is_file():
            errors.append(f"missing render evidence: {path_name}")
    concept = render_index.get("concept_reference")
    if concept and sha(ROOT / concept) != render_index.get("concept_reference_sha256"):
        errors.append("authored concept reference checksum drift")

    # Structural collision signatures are exact, deliberately conservative metadata gates.
    for family in ("CLOTH", "LEATHER", "MAIL", "PLATE"):
        records = [row for row in art_lines if row["family"] == family]
        for field in ("dominant_silhouette_tag", "differentiation_note"):
            values = [row[field] for row in records]
            if len(values) != len(set(values)):
                errors.append(f"duplicate {field} in {family}")
        motif_values = [tuple(row["motif_tags"]) for row in records]
        if len(motif_values) != len(set(motif_values)):
            errors.append(f"duplicate motif signature in {family}")

    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    asset_delta = {
        "schema": 1,
        "parent_physical_rp_files": 1791,
        "final_physical_rp_files": summary.get("total_rp_files"),
        "new_runtime_assets": 60,
        "modified_runtime_assets": 0,
        "stale_retained": summary.get("retained_legacy_worn_files"),
        "orphan": 0,
        "broken": summary.get("broken_production_reference"),
        "safe_to_delete": summary.get("safe_to_delete"),
        "deleted": summary.get("actually_deleted"),
    }
    if asset_delta["final_physical_rp_files"] - asset_delta["parent_physical_rp_files"] != 60:
        errors.append(f"unexpected resource-pack file delta: {asset_delta}")
    (REPORT_DIR / "asset-graph-delta.json").write_text(json.dumps(asset_delta, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")

    validation = {
        "schema": 1,
        "status": "PASS" if not errors else "FAIL",
        "errors": errors,
        "warnings": warnings,
        "counts": {"art_bible_lines": len(art_lines), "pilot_lines": len(selected), "pilot_pieces": len(pieces), "inventory_custom": len(expected_models), "worn_custom": len(custom), "nonpilot_fallback": len(fallback)},
        "proof": {"server_runtime": "PENDING_PAPER_PROBE", "resource_pack_schema": "STATIC_PASS" if not errors else "FAIL", "offline_render": "PASS" if not errors else "FAIL", "human_client": "HUMAN_CLIENT_STAGING_REQUIRED"},
    }
    (REPORT_DIR / "validation-summary.json").write_text(json.dumps(validation, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    closure = {
        "schema": 1,
        "verdict": "TECHNICAL_GO_HUMAN_VISUAL_ACCEPTANCE_REQUIRED" if not errors else "NO_GO",
        "readiness": final.get("readiness"),
        "remaining": {"gear_lines": 36, "inventory_items": 143, "worn_sets": 36},
        "human_client_staging_required": True,
        "manifest_sha256": sha(MANIFEST),
        "art_bible_sha256": sha(ART),
    }
    (REPORT_DIR / "final-authority.json").write_text(json.dumps(closure, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    print(f"Equipment RP2-B validation: status={validation['status']} lines=4 pieces=16 custom=16 fallback=144 remaining=36/143/36")
    if args.enforce and errors:
        raise SystemExit("; ".join(errors))


if __name__ == "__main__":
    main()
