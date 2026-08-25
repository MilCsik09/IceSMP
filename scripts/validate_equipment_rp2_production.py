#!/usr/bin/env python3
"""Fail-closed validation for the full Equipment RP2 production catalog."""

from __future__ import annotations

import argparse
import hashlib
import json
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

import yaml
from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs/development"
ART_BIBLE = DOCS / "equipment-rp2-art-bible.json"
PILOT = DOCS / "equipment-rp2-pilot-manifest.json"
MANIFEST = DOCS / "equipment-rp2-production-manifest.json"
ASSET_GRAPH = DOCS / "equipment-rp2-production-asset-graph.json"
CATALOG = ROOT / "src/main/resources/content/equipment/equipment.yml"
FAMILIES = ("CLOTH", "LEATHER", "MAIL", "PLATE")
SLOTS = ("HEAD", "CHEST", "LEGS", "FEET")


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def alpha_coverage(image: Image.Image, box: tuple[int, int, int, int]) -> float:
    alpha = image.getchannel("A").crop(box)
    histogram = alpha.histogram()
    return sum(histogram[1:]) / max(1, alpha.width * alpha.height)


def validate_png(path: Path, size: tuple[int, int], errors: list[str]) -> Image.Image | None:
    if not path.is_file():
        errors.append(f"missing PNG: {path.relative_to(ROOT)}")
        return None
    try:
        image = Image.open(path).convert("RGBA")
        image.load()
    except Exception as exception:
        errors.append(f"invalid PNG {path.relative_to(ROOT)}: {exception}")
        return None
    if image.size != size:
        errors.append(f"PNG dimension drift {path.relative_to(ROOT)}: {image.size} != {size}")
    return image


def validate_family(family: str, art_lines: list[dict[str, Any]], errors: list[str]) -> dict[str, Any]:
    lines = sorted((line for line in art_lines if line["family"] == family), key=lambda row: row["canonical_line_id"])
    if len(lines) != 10:
        errors.append(f"{family}: line count {len(lines)} != 10")
    icon_fingerprints: dict[str, str] = {}
    worn_fingerprints: dict[str, str] = {}
    for line in lines:
        line_id = line["canonical_line_id"]
        for kind in ("inventory", "worn"):
            source = DOCS / "equipment-rp2-authored-sources" / f"{line_id}-{kind}-source.png"
            if not source.is_file():
                errors.append(f"missing authored source: {source.relative_to(ROOT)}")
            else:
                try:
                    Image.open(source).verify()
                except Exception as exception:
                    errors.append(f"invalid authored source {source.relative_to(ROOT)}: {exception}")
        equipment = ROOT / f"resource-pack/assets/icesmp/equipment/rp2/{line_id}.json"
        humanoid_path = ROOT / f"resource-pack/assets/icesmp/textures/entity/equipment/humanoid/rp2/{line_id}.png"
        leggings_path = ROOT / f"resource-pack/assets/icesmp/textures/entity/equipment/humanoid_leggings/rp2/{line_id}.png"
        if not equipment.is_file():
            errors.append(f"missing equipment definition: {equipment.relative_to(ROOT)}")
        else:
            try:
                definition = json.loads(equipment.read_text(encoding="utf-8"))
                expected = f"icesmp:rp2/{line_id}"
                for layer in ("humanoid", "humanoid_leggings"):
                    if definition.get("layers", {}).get(layer) != [{"texture": expected}]:
                        errors.append(f"equipment layer drift: {line_id} {layer}")
            except Exception as exception:
                errors.append(f"invalid equipment JSON {line_id}: {exception}")
        humanoid = validate_png(humanoid_path, (256, 128), errors)
        leggings = validate_png(leggings_path, (256, 128), errors)
        if humanoid is not None:
            scale = 4
            helmet_faces = [(8, 0, 16, 8), (16, 0, 24, 8), (0, 8, 8, 16),
                            (8, 8, 16, 16), (16, 8, 24, 16), (24, 8, 32, 16)]
            for face in helmet_faces:
                box = tuple(value * scale for value in face)
                if alpha_coverage(humanoid, box) < .98:
                    errors.append(f"helmet alpha hole: {line_id} {face}")
            # Boots own only the lower 5/12 side rows; no thigh-height boot pixels are allowed.
            for face in ((0, 20, 4, 27), (4, 20, 8, 27), (8, 20, 12, 27), (12, 20, 16, 27)):
                if alpha_coverage(humanoid, tuple(value * scale for value in face)) > 0:
                    errors.append(f"boots climb above calf: {line_id} {face}")
            worn_fingerprints[line_id] = digest(humanoid_path) + digest(leggings_path)
        if leggings is not None:
            scale = 4
            for face in ((16, 20, 20, 28), (20, 20, 28, 28), (28, 20, 32, 28), (32, 20, 40, 28)):
                if alpha_coverage(leggings, tuple(value * scale for value in face)) > 0:
                    errors.append(f"leggings occupy chest region: {line_id} {face}")
            for face in ((0, 8, 32, 16), (40, 20, 56, 32)):
                if alpha_coverage(leggings, tuple(value * scale for value in face)) > 0:
                    errors.append(f"leggings leak into head/arm region: {line_id} {face}")
        for slot in SLOTS:
            template_id = line["piece_slots"][slot]
            definition = ROOT / f"resource-pack/assets/icesmp/items/{template_id}.json"
            model = ROOT / f"resource-pack/assets/icesmp/models/item/{template_id}.json"
            texture = ROOT / f"resource-pack/assets/icesmp/textures/item/{template_id}.png"
            for path in (definition, model):
                if not path.is_file():
                    errors.append(f"missing inventory mapping: {path.relative_to(ROOT)}")
                else:
                    try:
                        json.loads(path.read_text(encoding="utf-8"))
                    except Exception as exception:
                        errors.append(f"invalid JSON {path.relative_to(ROOT)}: {exception}")
            icon = validate_png(texture, (64, 64), errors)
            if icon is not None:
                # 64 physical pixels must be a nearest-neighbour 2x expansion of the 32 authored grid.
                pixels = icon.load()
                for y in range(0, 64, 2):
                    for x in range(0, 64, 2):
                        values = {pixels[x + dx, y + dy] for dx in (0, 1) for dy in (0, 1)}
                        if len(values) != 1:
                            errors.append(f"inventory pixel-scale drift: {template_id} at {x},{y}")
                            break
                    else:
                        continue
                    break
                icon_fingerprints[template_id] = digest(texture)
    if len(set(icon_fingerprints.values())) != len(icon_fingerprints):
        errors.append(f"{family}: exact inventory icon collision")
    if len(set(worn_fingerprints.values())) != len(worn_fingerprints):
        errors.append(f"{family}: exact worn structural collision")
    family_sheet = DOCS / "equipment-rp2-production-evidence" / f"{family.lower()}-10-line-sheet.png"
    if not family_sheet.is_file():
        errors.append(f"missing family evidence: {family_sheet.relative_to(ROOT)}")
    return {"family": family, "lines": len(lines), "pieces": len(icon_fingerprints),
            "inventory_collisions": len(icon_fingerprints) - len(set(icon_fingerprints.values())),
            "worn_collisions": len(worn_fingerprints) - len(set(worn_fingerprints.values()))}


def validate_full(art_lines: list[dict[str, Any]], errors: list[str]) -> dict[str, Any]:
    if not MANIFEST.is_file():
        errors.append("missing full production manifest")
        return {}
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    pieces = manifest.get("pieces", [])
    summary = manifest.get("summary", {})
    if len(pieces) != 160 or len({row.get("template_id") for row in pieces}) != 160:
        errors.append(f"production piece cardinality drift: {len(pieces)}")
    family_counts = Counter(row.get("family") for row in pieces)
    line_counts = Counter(row.get("line_id") for row in pieces)
    slot_counts = defaultdict(set)
    for piece in pieces:
        slot_counts[piece.get("line_id")].add(piece.get("slot"))
        if piece.get("production_status") != "FULL_RP2_CUSTOM":
            errors.append(f"non-final production status: {piece.get('template_id')}")
        for path, expected in piece.get("checksums", {}).items():
            target = ROOT / path
            if not target.is_file() or digest(target) != expected:
                errors.append(f"checksum drift: {piece.get('template_id')} {path}")
    if family_counts != Counter({family: 40 for family in FAMILIES}):
        errors.append(f"family piece cardinality drift: {dict(family_counts)}")
    if len(line_counts) != 40 or any(value != 4 for value in line_counts.values()):
        errors.append("line piece cardinality drift")
    if any(slots != set(SLOTS) for slots in slot_counts.values()):
        errors.append("slot coverage drift")
    expected_summary = {
        "gear_lines": 40, "canonical_armor": 160, "inventory_custom": 160,
        "worn_custom": 160, "normal_canonical_vanilla_fallback": 0,
    }
    for key, value in expected_summary.items():
        if summary.get(key) != value:
            errors.append(f"manifest summary drift: {key}={summary.get(key)} != {value}")
    if manifest.get("art_bible", {}).get("sha256") != digest(ART_BIBLE):
        errors.append("Art Bible digest drift")
    if manifest.get("art_bible", {}).get("semantic_drift") is not False:
        errors.append("Art Bible semantic drift is not NO")
    for row in manifest.get("pilot_preservation", []):
        if row.get("expected_sha256") != row.get("actual_sha256"):
            errors.append(f"silent pilot drift: {row.get('path')}")
    graph = json.loads(ASSET_GRAPH.read_text(encoding="utf-8")) if ASSET_GRAPH.is_file() else {}
    graph_summary = graph.get("summary", {})
    if graph_summary.get("broken_reference") != 0 or graph_summary.get("orphan") != 0:
        errors.append(f"production asset graph is not closed: {graph_summary}")
    runtime = ROOT / manifest.get("runtime_index", "")
    if not runtime.is_file():
        errors.append("missing production runtime index")
    else:
        values = {}
        for raw in runtime.read_text(encoding="utf-8").splitlines():
            if raw and not raw.startswith("#") and "=" in raw:
                key, value = raw.split("=", 1)
                values[key] = value
        if values.get("schema") != "2" or values.get("binding.count") != "160":
            errors.append("production runtime index cardinality/schema drift")
    catalog = yaml.safe_load(CATALOG.read_text(encoding="utf-8"))
    templates = catalog.get("item-templates", catalog.get("templates", {}))
    if not isinstance(templates, dict):
        errors.append("authored equipment catalog has no template map")
    else:
        by_id = {piece["template_id"]: piece for piece in pieces}
        for template_id, piece in by_id.items():
            entry = templates.get(template_id, {})
            if entry.get("item-model") != piece["item_model"] or entry.get("equipment-asset") != piece["equipment_asset"]:
                errors.append(f"catalog presentation binding drift: {template_id}")
    return {"pieces": len(pieces), "families": dict(family_counts), "lines": len(line_counts),
            "fallback": summary.get("normal_canonical_vanilla_fallback")}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--family", choices=FAMILIES)
    parser.add_argument("--enforce", action="store_true")
    args = parser.parse_args()
    art = json.loads(ART_BIBLE.read_text(encoding="utf-8"))
    lines = art.get("gear_lines", [])
    errors: list[str] = []
    if len(lines) != 40 or len({line.get("canonical_line_id") for line in lines}) != 40:
        errors.append("Art Bible must contain 40 unique production lines")
    families = (args.family,) if args.family else FAMILIES
    family_results = [validate_family(family, lines, errors) for family in families]
    full_result = {} if args.family else validate_full(lines, errors)
    result = {"families": family_results, "full": full_result, "errors": errors,
              "status": "PASS" if not errors else "FAIL"}
    print(json.dumps(result, ensure_ascii=False, sort_keys=True))
    if args.enforce and errors:
        raise SystemExit("; ".join(errors))


if __name__ == "__main__":
    main()
