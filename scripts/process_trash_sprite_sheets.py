#!/usr/bin/env python3
"""Deterministically slices reviewed AI sprite sheets into canonical Trash pack assets."""

from __future__ import annotations

import argparse
import hashlib
import io
import json
from pathlib import Path

import yaml
from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = ROOT / "dev-assets/trash/source"
MANIFEST = SOURCE_ROOT / "manifest.json"
CATALOG = ROOT / "src/main/resources/content/trash/catalog.yml"
PACK_ROOT = ROOT / "resource-pack/assets/icesmp"
TEXTURE_ROOT = PACK_ROOT / "textures/item/trash"
ITEM_ROOT = PACK_ROOT / "items/trash"
MODEL_ROOT = PACK_ROOT / "models/item/trash"
TARGET_SIZE = 64
CONTENT_SIZE = 56


def load_authority() -> tuple[list[str], list[str], dict[str, object]]:
    catalog = yaml.safe_load(CATALOG.read_text(encoding="utf-8"))
    item_ids = list(catalog["items"])
    phase_ids = list(catalog["lifecycle-phases"])
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    if manifest.get("schema_version") != 1:
        raise ValueError("trash sprite manifest schema_version must be 1")
    return item_ids, phase_ids, manifest


def normalize_cell(cell: Image.Image, item_id: str) -> Image.Image:
    rgba = cell.convert("RGBA")
    alpha = rgba.getchannel("A").point(lambda value: 255 if value >= 32 else 0)
    rgba.putalpha(alpha)
    bbox = alpha.getbbox()
    if bbox is None:
        raise ValueError(f"empty sprite cell: {item_id}")
    crop = rgba.crop(bbox)
    scale = min(CONTENT_SIZE / crop.width, CONTENT_SIZE / crop.height)
    width = max(1, round(crop.width * scale))
    height = max(1, round(crop.height * scale))
    crop = crop.resize((width, height), Image.Resampling.NEAREST)
    # The image model authors the pixels; quantization merely enforces the reviewed 4–8 tone budget.
    quantized = crop.quantize(colors=8, method=Image.Quantize.FASTOCTREE,
                              dither=Image.Dither.NONE).convert("RGBA")
    quantized.putalpha(crop.getchannel("A").point(lambda value: 255 if value >= 128 else 0))
    output = Image.new("RGBA", (TARGET_SIZE, TARGET_SIZE), (0, 0, 0, 0))
    output.alpha_composite(quantized, ((TARGET_SIZE - width) // 2, (TARGET_SIZE - height) // 2))
    return output


def sync_text(path: Path, content: str, check_only: bool) -> None:
    if check_only:
        if not path.is_file() or path.read_text(encoding="utf-8") != content:
            raise ValueError(f"stale generated Trash asset: {path}")
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def sync_texture(path: Path, image: Image.Image, check_only: bool) -> bytes:
    encoded = io.BytesIO()
    image.save(encoded, format="PNG", optimize=True)
    content = encoded.getvalue()
    if check_only:
        if not path.is_file() or path.read_bytes() != content:
            raise ValueError(f"stale generated Trash asset: {path}")
    else:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
    return content


def sync_models(item_id: str, check_only: bool) -> None:
    item_definition = {
        "model": {"type": "minecraft:model", "model": f"icesmp:item/trash/{item_id}"}
    }
    generated_model = {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"icesmp:item/trash/{item_id}"},
    }
    sync_text(ITEM_ROOT / f"{item_id}.json",
              json.dumps(item_definition, ensure_ascii=False, indent=2) + "\n", check_only)
    sync_text(MODEL_ROOT / f"{item_id}.json",
              json.dumps(generated_model, ensure_ascii=False, indent=2) + "\n", check_only)


def process(check_only: bool) -> tuple[int, int]:
    catalog_ids, phase_ids, manifest = load_authority()
    authority_ids = catalog_ids + phase_ids
    seen: set[str] = set()
    hashes: dict[str, str] = {}
    if not check_only:
        TEXTURE_ROOT.mkdir(parents=True, exist_ok=True)
    for sheet in manifest["sheets"]:
        rows = int(sheet["rows"])
        columns = int(sheet["columns"])
        ids = list(sheet["ids"])
        if rows * columns != len(ids):
            raise ValueError(f"cell/ID mismatch in {sheet['file']}")
        source_path = SOURCE_ROOT / sheet["file"]
        if not source_path.is_file():
            raise ValueError(f"missing AI source sheet: {source_path}")
        source = Image.open(source_path).convert("RGBA")
        alpha_histogram = source.getchannel("A").histogram()
        transparent_fraction = sum(alpha_histogram[:32]) / (source.width * source.height)
        if transparent_fraction < 0.20:
            raise ValueError(f"AI source sheet lacks transparent separation: {source_path}")
        for index, item_id in enumerate(ids):
            if item_id not in authority_ids:
                raise ValueError(f"unknown catalog ID in sprite manifest: {item_id}")
            if item_id in seen:
                raise ValueError(f"duplicate sprite manifest ID: {item_id}")
            seen.add(item_id)
            row, column = divmod(index, columns)
            left = round(source.width * column / columns)
            right = round(source.width * (column + 1) / columns)
            top = round(source.height * row / rows)
            bottom = round(source.height * (row + 1) / rows)
            output = normalize_cell(source.crop((left, top, right, bottom)), item_id)
            output_path = TEXTURE_ROOT / f"{item_id}.png"
            content = sync_texture(output_path, output, check_only)
            digest = hashlib.sha256(content).hexdigest()
            if digest in hashes:
                raise ValueError(f"duplicate output texture: {item_id} == {hashes[digest]}")
            hashes[digest] = item_id
            sync_models(item_id, check_only)
    expected_prefix = authority_ids[:len(seen)]
    if list(item_id for sheet in manifest["sheets"] for item_id in sheet["ids"]) != expected_prefix:
        raise ValueError("sprite manifest must cover one contiguous catalog prefix in canonical order")
    for root, suffix in ((TEXTURE_ROOT, ".png"), (ITEM_ROOT, ".json"), (MODEL_ROOT, ".json")):
        actual = {path.stem for path in root.glob(f"*{suffix}")}
        if actual != seen:
            missing = sorted(seen - actual)[:5]
            extra = sorted(actual - seen)[:5]
            raise ValueError(f"Trash asset output drift in {root}: missing={missing}, extra={extra}")
    base_count = len(seen.intersection(catalog_ids))
    phase_count = len(seen.intersection(phase_ids))
    print(f"Trash AI sprite assets ready: {base_count}/330 identities; "
          f"{phase_count}/{len(phase_ids)} lifecycle phases")
    return base_count, phase_count


def validate(require_complete: bool, check_only: bool) -> None:
    base_count, phase_count = process(check_only)
    if require_complete and (base_count != 330 or phase_count != 27):
        raise ValueError("production Trash asset gate requires 330/330 base identities and "
                         f"27/27 phases, found {base_count}/330 and {phase_count}/27")
    for path in TEXTURE_ROOT.glob("*.png"):
        image = Image.open(path)
        if image.size != (64, 64) or image.mode != "RGBA":
            raise ValueError(f"invalid final Trash sprite: {path}")
        alpha_values = set(image.getchannel("A").get_flattened_data())
        if not alpha_values.issubset({0, 255}) or 0 not in alpha_values or 255 not in alpha_values:
            raise ValueError(f"Trash sprite must use non-empty binary alpha: {path}")
        colours = {pixel[:3] for pixel in image.get_flattened_data() if pixel[3] == 255}
        if not 1 <= len(colours) <= 8:
            raise ValueError(f"Trash sprite tone budget must be 1..8: {path} has {len(colours)}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="validate committed outputs without writing")
    parser.add_argument("--require-complete", action="store_true",
                        help="require all 330 base and 27 lifecycle-phase assets")
    args = parser.parse_args()
    validate(args.require_complete, args.check)


if __name__ == "__main__":
    main()
