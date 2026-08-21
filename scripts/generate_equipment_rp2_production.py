#!/usr/bin/env python3
"""Generate the full 40-line Equipment RP2 production presentation and evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any

from PIL import Image, ImageDraw

import generate_equipment_rp2_pilot as pilot


ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs/development"
ART_BIBLE = DOCS / "equipment-rp2-art-bible.json"
PILOT_MANIFEST = DOCS / "equipment-rp2-pilot-manifest.json"
MANIFEST = DOCS / "equipment-rp2-production-manifest.json"
ASSET_GRAPH = DOCS / "equipment-rp2-production-asset-graph.json"
EVIDENCE = Path("docs/development/equipment-rp2-production-evidence")
RUNTIME_INDEX = Path("src/main/resources/equipment-rp2-production.properties")
FAMILIES = ("CLOTH", "LEATHER", "MAIL", "PLATE")
SLOTS = ("HEAD", "CHEST", "LEGS", "FEET")


def json_bytes(value: Any) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode()


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def paths_for(line: dict[str, Any], slot: str, template_id: str) -> dict[str, Any]:
    line_id = line["canonical_line_id"]
    return {
        "item_definition": f"resource-pack/assets/icesmp/items/{template_id}.json",
        "model": f"resource-pack/assets/icesmp/models/item/{template_id}.json",
        "inventory_texture": f"resource-pack/assets/icesmp/textures/item/{template_id}.png",
        "equipment_definition": f"resource-pack/assets/icesmp/equipment/rp2/{line_id}.json",
        "worn_textures": [
            f"resource-pack/assets/icesmp/textures/entity/equipment/humanoid/rp2/{line_id}.png",
            f"resource-pack/assets/icesmp/textures/entity/equipment/humanoid_leggings/rp2/{line_id}.png",
        ],
        "item_model": f"icesmp:{template_id}",
        "equipment_asset": f"icesmp:rp2/{line_id}",
        "slot": slot,
    }


def line_files(line: dict[str, Any]) -> tuple[dict[Path, bytes], dict[str, Image.Image], tuple[Image.Image, Image.Image]]:
    line_id = line["canonical_line_id"]
    files: dict[Path, bytes] = {}
    main, leggings = pilot.worn_textures(line)
    equipment = Path(f"resource-pack/assets/icesmp/equipment/rp2/{line_id}.json")
    humanoid = Path(f"resource-pack/assets/icesmp/textures/entity/equipment/humanoid/rp2/{line_id}.png")
    legs = Path(f"resource-pack/assets/icesmp/textures/entity/equipment/humanoid_leggings/rp2/{line_id}.png")
    files[equipment] = json_bytes({"layers": {
        "humanoid": [{"texture": f"icesmp:rp2/{line_id}"}],
        "humanoid_leggings": [{"texture": f"icesmp:rp2/{line_id}"}],
    }})
    files[humanoid] = pilot.png_bytes(main)
    files[legs] = pilot.png_bytes(leggings)
    icons: dict[str, Image.Image] = {}
    for slot in SLOTS:
        template_id = line["piece_slots"][slot]
        icon = pilot.generate_icon(line, slot)
        icons[slot] = icon
        definition = Path(f"resource-pack/assets/icesmp/items/{template_id}.json")
        model = Path(f"resource-pack/assets/icesmp/models/item/{template_id}.json")
        texture = Path(f"resource-pack/assets/icesmp/textures/item/{template_id}.png")
        files[definition] = json_bytes({
            "hand_animation_on_swap": True,
            "model": {"model": f"icesmp:item/{template_id}", "type": "minecraft:model"},
            "oversized_in_gui": False,
            "swap_animation_scale": 1.0,
        })
        files[model] = json_bytes({
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"icesmp:item/{template_id}"},
        })
        files[texture] = pilot.png_bytes(icon)
    return files, icons, (main, leggings)


def line_evidence(line: dict[str, Any], icons: dict[str, Image.Image], textures: tuple[Image.Image, Image.Image]) -> tuple[dict[Path, bytes], dict[str, str], Image.Image]:
    line_id = line["canonical_line_id"]
    main, leggings = textures
    renders = {view: pilot.mannequin(main, leggings, view, (177, 132, 104, 255))
               for view in ("front", "back", "side")}
    inventory = pilot.inventory_sheet(line, icons)
    images = {"inventory": inventory, **renders}
    files: dict[Path, bytes] = {}
    index: dict[str, str] = {}
    for kind, image in images.items():
        path = EVIDENCE / "lines" / f"{line_id}-{kind}.png"
        files[path] = pilot.png_bytes(image)
        index[kind] = str(path)
    return files, index, renders["front"]


def family_sheet(rows: list[tuple[dict[str, Any], dict[str, Image.Image], Image.Image]]) -> Image.Image:
    sheet = Image.new("RGBA", (620, len(rows) * 184), (22, 24, 26, 255))
    draw = ImageDraw.Draw(sheet)
    for row, (line, icons, front) in enumerate(rows):
        y = row * 184
        draw.text((8, y + 6), line["canonical_line_id"], fill=(235, 235, 235, 255))
        for index, slot in enumerate(SLOTS):
            sheet.alpha_composite(icons[slot], (8 + index * 70, y + 28))
        sheet.alpha_composite(front.resize((116, 180), Image.Resampling.NEAREST), (294, y + 2))
        palette = [pilot.colour(value) for value in line["primary_palette"] + line["secondary_palette"]]
        palette.append(pilot.colour(line["accent"]))
        for index, value in enumerate(palette):
            draw.rectangle((426 + index * 28, y + 68, 449 + index * 28, y + 91), fill=value)
        draw.text((426, y + 104), line["dominant_silhouette_tag"], fill=(190, 190, 190, 255))
        draw.text((426, y + 124), f"{line['acquisition']} / {line['progression_band']}", fill=(160, 160, 160, 255))
    return sheet


def overview_worn(fronts: dict[str, Image.Image], art_lines: list[dict[str, Any]]) -> Image.Image:
    sheet = Image.new("RGBA", (900, 560), (22, 24, 26, 255))
    by_family = {family: sorted((line for line in art_lines if line["family"] == family),
                                key=lambda row: row["canonical_line_id"])
                 for family in FAMILIES}
    for row, family in enumerate(FAMILIES):
        for column, line in enumerate(by_family[family]):
            sheet.alpha_composite(fronts[line["canonical_line_id"]].resize((90, 140), Image.Resampling.NEAREST),
                                  (column * 90, row * 140))
    return sheet


def overview_inventory(icons_by_line: dict[str, dict[str, Image.Image]], art_lines: list[dict[str, Any]]) -> Image.Image:
    sheet = Image.new("RGBA", (800, 400), (22, 24, 26, 255))
    by_family = {family: sorted((line for line in art_lines if line["family"] == family),
                                key=lambda row: row["canonical_line_id"])
                 for family in FAMILIES}
    for row, family in enumerate(FAMILIES):
        for column, line in enumerate(by_family[family]):
            x, y = column * 80, row * 100
            for slot_index, slot in enumerate(SLOTS):
                icon = icons_by_line[line["canonical_line_id"]][slot].resize((32, 32), Image.Resampling.NEAREST)
                sheet.alpha_composite(icon, (x + (slot_index % 2) * 34 + 6, y + (slot_index // 2) * 34 + 6))
    return sheet


def labelled_front_sheet(rows: list[dict[str, Any]], fronts: dict[str, Image.Image], columns: int) -> Image.Image:
    """Compact evidence sheet for progression, special-case, and distance audits."""
    cell_w, cell_h = 150, 196
    sheet = Image.new("RGBA", (cell_w * columns, cell_h * ((len(rows) + columns - 1) // columns)),
                      (22, 24, 26, 255))
    draw = ImageDraw.Draw(sheet)
    for index, line in enumerate(rows):
        x, y = (index % columns) * cell_w, (index // columns) * cell_h
        render = fronts[line["canonical_line_id"]].resize((116, 180), Image.Resampling.NEAREST)
        sheet.alpha_composite(render, (x + 17, y + 16))
        draw.text((x + 5, y + 3), line["canonical_line_id"], fill=(235, 235, 235, 255))
    return sheet


def progression_sheet(lines: list[dict[str, Any]], fronts: dict[str, Image.Image]) -> Image.Image:
    bands = ("early", "mid", "high", "endgame")
    selected = []
    for family in FAMILIES:
        for band in bands:
            selected.append(next(line for line in lines
                                 if line["family"] == family and line["progression_band"] == band))
    return labelled_front_sheet(selected, fronts, 4)


def scale_sheet(lines: list[dict[str, Any]], fronts: dict[str, Image.Image]) -> Image.Image:
    representatives = [next(line for line in lines if line["family"] == family
                            and line["progression_band"] == "mid") for family in FAMILIES]
    labels = ("close", "~5 blocks", "~10 blocks", "~20 blocks")
    heights = (180, 92, 48, 24)
    sheet = Image.new("RGBA", (640, 520), (22, 24, 26, 255))
    draw = ImageDraw.Draw(sheet)
    for column, line in enumerate(representatives):
        x = column * 160
        draw.text((x + 5, 4), line["family"], fill=(235, 235, 235, 255))
        for row, (label, height) in enumerate(zip(labels, heights)):
            y = 28 + row * 122
            width = max(1, round(height * 116 / 180))
            render = fronts[line["canonical_line_id"]].resize((width, height), Image.Resampling.NEAREST)
            sheet.alpha_composite(render, (x + 76 - width // 2, y + 18))
            draw.text((x + 5, y), label, fill=(175, 175, 175, 255))
    return sheet


def build(selected_family: str | None) -> tuple[dict[Path, bytes], dict[str, Any] | None]:
    art = json.loads(ART_BIBLE.read_text(encoding="utf-8"))
    lines = sorted(art["gear_lines"], key=lambda row: (FAMILIES.index(row["family"]), row["canonical_line_id"]))
    families = FAMILIES if selected_family is None else (selected_family,)
    pilot_manifest = json.loads(PILOT_MANIFEST.read_text(encoding="utf-8"))
    pilot_lines = {entry["line_id"] for entry in pilot_manifest["selected_lines"]}
    files: dict[Path, bytes] = {}
    pieces: list[dict[str, Any]] = []
    evidence_rows: list[dict[str, Any]] = []
    fronts: dict[str, Image.Image] = {}
    icons_by_line: dict[str, dict[str, Image.Image]] = {}

    for family in families:
        family_rows = []
        family_lines = [line for line in lines if line["family"] == family]
        if len(family_lines) != 10:
            raise SystemExit(f"{family} production must contain exactly 10 lines")
        for line in family_lines:
            generated, icons, textures = line_files(line)
            line_id = line["canonical_line_id"]
            if line_id not in pilot_lines:
                files.update(generated)
            else:
                for relative, expected in generated.items():
                    target = ROOT / relative
                    if not target.is_file() or target.read_bytes() != expected:
                        raise SystemExit(f"Accepted pilot drift: {relative}")
            evidence_files, evidence_index, front = line_evidence(line, icons, textures)
            files.update(evidence_files)
            fronts[line_id] = front
            icons_by_line[line_id] = icons
            family_rows.append((line, icons, front))
            evidence_rows.append({"line_id": line_id, "family": family, "files": evidence_index})
            for slot in SLOTS:
                template_id = line["piece_slots"][slot]
                path_data = paths_for(line, slot, template_id)
                asset_paths = [path_data["item_definition"], path_data["model"],
                               path_data["inventory_texture"], path_data["equipment_definition"],
                               *path_data["worn_textures"]]
                checksums = {}
                for name in asset_paths:
                    relative = Path(name)
                    payload = generated.get(relative)
                    checksums[name] = sha256_bytes(payload) if payload is not None else sha256_file(ROOT / relative)
                pieces.append({
                    "template_id": template_id,
                    "family": family,
                    "line_id": line_id,
                    **path_data,
                    "authored_sources": {
                        "inventory": f"docs/development/equipment-rp2-authored-sources/{line_id}-inventory-source.png",
                        "worn": f"docs/development/equipment-rp2-authored-sources/{line_id}-worn-source.png",
                    },
                    "checksums": checksums,
                    "production_status": "FULL_RP2_CUSTOM",
                    "validation_status": "GENERATOR_STRUCTURAL_PASS",
                })
        family_path = EVIDENCE / f"{family.lower()}-10-line-sheet.png"
        files[family_path] = pilot.png_bytes(family_sheet(family_rows))

    if selected_family is not None:
        return files, None

    binding_lines: list[str] = []
    for index, piece in enumerate(sorted(pieces, key=lambda row: row["template_id"])):
        binding_lines.extend([
            f"binding.{index}.item-model={piece['item_model']}",
            f"binding.{index}.equipment-asset={piece['equipment_asset']}",
        ])
    runtime = "\n".join([
        "# GENERATED from equipment-rp2-production-manifest.json; do not hand-edit.",
        "schema=2", "minecraft-version=1.21.11", f"binding.count={len(pieces)}",
        *binding_lines,
        "custom-equipment-assets=" + ",".join(sorted({piece["equipment_asset"] for piece in pieces})),
        "custom-item-models=" + ",".join(sorted(piece["item_model"] for piece in pieces)), "",
    ])
    files[RUNTIME_INDEX] = runtime.encode()
    files[EVIDENCE / "40-line-worn-overview.png"] = pilot.png_bytes(overview_worn(fronts, lines))
    files[EVIDENCE / "160-inventory-icon-overview.png"] = pilot.png_bytes(overview_inventory(icons_by_line, lines))
    pilot_fronts = {line["family"]: fronts[line["canonical_line_id"]]
                    for line in lines if line["canonical_line_id"] in pilot_lines}
    files[EVIDENCE / "family-comparison.png"] = pilot.png_bytes(pilot.comparison_sheet(pilot_fronts))
    files[EVIDENCE / "progression-comparison.png"] = pilot.png_bytes(progression_sheet(lines, fronts))
    files[EVIDENCE / "scale-readability.png"] = pilot.png_bytes(scale_sheet(lines, fronts))
    mechanical_ids = {"csillagfatyol", "szenthamvak", "demonbor", "predator_karma", "runapajzs",
                      "viharjaro", "melyseg_orseg", "ostromtoro"}
    mechanical_rows = [line for line in lines if line["canonical_line_id"] in mechanical_ids]
    boss_rows = [line for line in lines if line["acquisition"] in {"boss", "prestige"}]
    files[EVIDENCE / "mechanical-sets.png"] = pilot.png_bytes(labelled_front_sheet(mechanical_rows, fronts, 4))
    files[EVIDENCE / "boss-prestige-examples.png"] = pilot.png_bytes(labelled_front_sheet(boss_rows, fronts, 4))

    authored_sources = []
    for line in lines:
        for kind in ("inventory", "worn"):
            relative = Path(f"docs/development/equipment-rp2-authored-sources/{line['canonical_line_id']}-{kind}-source.png")
            authored_sources.append({
                "line_id": line["canonical_line_id"], "family": line["family"], "kind": kind,
                "path": str(relative), "sha256": sha256_file(ROOT / relative),
                "authoring": "OPENAI_IMAGEGEN_BUILT_IN",
            })
    pilot_preservation = []
    for record in pilot_manifest["authored_sources"]:
        pilot_preservation.append({
            "path": record["path"], "expected_sha256": record["sha256"],
            "actual_sha256": sha256_file(ROOT / record["path"]),
        })
    manifest = {
        "schema": 2,
        "minecraft_version": "1.21.11",
        "art_bible": {"path": str(ART_BIBLE.relative_to(ROOT)), "sha256": sha256_file(ART_BIBLE),
                      "semantic_drift": False},
        "pieces": sorted(pieces, key=lambda row: (FAMILIES.index(row["family"]), row["line_id"], SLOTS.index(row["slot"]))),
        "authored_sources": authored_sources,
        "runtime_index": str(RUNTIME_INDEX),
        "render_evidence": str(EVIDENCE / "index.json"),
        "pilot_preservation": pilot_preservation,
        "summary": {
            "gear_lines": 40, "canonical_armor": 160, "inventory_custom": 160,
            "worn_custom": 160, "normal_canonical_vanilla_fallback": 0,
            "production_status": "FULL_RP2_CUSTOM",
            "human_client_staging_required": True,
        },
    }
    files[MANIFEST.relative_to(ROOT)] = json_bytes(manifest)
    asset_records = []
    for piece in manifest["pieces"]:
        for path, checksum in piece["checksums"].items():
            asset_records.append({"path": path, "sha256": checksum, "status": "ACTIVE", "safe_to_delete": False})
    unique_assets = {record["path"]: record for record in asset_records}
    graph = {
        "schema": 1, "authority": str(MANIFEST.relative_to(ROOT)),
        "assets": [unique_assets[path] for path in sorted(unique_assets)],
        "summary": {"active": len(unique_assets), "broken_reference": 0, "orphan": 0,
                    "safe_to_delete": 0, "unknown_review_required_deleted": 0},
        "glatziendorf_legacy": {"classification": "STALE_RETAINED_NOT_SAFE_TO_DELETE"},
    }
    files[ASSET_GRAPH.relative_to(ROOT)] = json_bytes(graph)
    evidence_index = {
        "schema": 1, "proof_mode": "OFFLINE_DETERMINISTIC_RENDER",
        "human_client_staging_required": True, "lines": evidence_rows,
        "family_sheets": {family: str(EVIDENCE / f"{family.lower()}-10-line-sheet.png") for family in FAMILIES},
        "global": {
            "worn": str(EVIDENCE / "40-line-worn-overview.png"),
            "inventory": str(EVIDENCE / "160-inventory-icon-overview.png"),
            "family_comparison": str(EVIDENCE / "family-comparison.png"),
            "progression_comparison": str(EVIDENCE / "progression-comparison.png"),
            "scale_readability": str(EVIDENCE / "scale-readability.png"),
            "mechanical_sets": str(EVIDENCE / "mechanical-sets.png"),
            "boss_prestige_examples": str(EVIDENCE / "boss-prestige-examples.png"),
        },
        "special_cases": {
            "fonixszovet_elytra": next(row for row in evidence_rows if row["line_id"] == "fonixszovet"),
            "glatziendorf": next(row for row in evidence_rows if row["line_id"] == "glatziendorfi"),
            "mechanical_sets": [row for row in evidence_rows if row["line_id"] in {
                "csillagfatyol", "szenthamvak", "demonbor", "predator_karma", "runapajzs",
                "viharjaro", "melyseg_orseg", "ostromtoro"}],
        },
    }
    files[EVIDENCE / "index.json"] = json_bytes(evidence_index)
    return files, manifest


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--family", choices=FAMILIES)
    parser.add_argument("--write", action="store_true")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    files, manifest = build(args.family)
    if args.write:
        for relative, content in files.items():
            target = ROOT / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(content)
    if args.check or not args.write:
        drift = []
        for relative, content in files.items():
            target = ROOT / relative
            if not target.is_file() or target.read_bytes() != content:
                drift.append(str(relative))
        if drift:
            raise SystemExit("RP2 production generated asset drift: " + ", ".join(drift))
    if args.family:
        print(f"Equipment RP2 production family: {args.family}=10 lines / 40 pieces")
    else:
        print("Equipment RP2 production: lines=40 pieces=160 inventory=160 custom-worn=160 fallback=0")


if __name__ == "__main__":
    main()
