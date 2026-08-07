#!/usr/bin/env python3
"""Validate, build and publish metadata for the IceSMP resource pack.

The ZIP builder is intentionally deterministic: identical client-facing contents produce identical
bytes, SHA-1 values and R2 object names regardless of file mtimes, operating system or zlib
version. Pack files are stored without a second compression pass because PNG assets are already
compressed and the small size difference is worth the stronger reproducibility guarantee.

Wearable validation is intentionally part of the pack build. The server-side ITEM_MODEL id and the
worn EQUIPPABLE asset are different resource identities; a broken equipment JSON, missing layer
texture, explicit equipment-asset config reference, or an invalid same-id wearable fallback must
fail before a pack can be published.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import struct
import sys
import zipfile
from pathlib import Path, PurePosixPath
from typing import Iterable

DOS_EPOCH = (1980, 1, 1, 0, 0, 0)
MAX_FILES = 20_000
MAX_UNCOMPRESSED_BYTES = 100 * 1024 * 1024
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
SHA1_PATTERN = re.compile(r"^[0-9a-f]{40}$")
RESOURCE_LOCATION_PATTERN = re.compile(r"^[a-z0-9_.-]+:[a-z0-9_./-]+$")
CONFIG_RESOURCE_PATTERN = re.compile(
    r"(?P<key>equipment-asset|(?:key-)?item-model)\s*:\s*[\"']?(?P<value>[a-z0-9_.-]+(?::[a-z0-9_./-]+)?)",
    re.IGNORECASE,
)
FLOW_MATERIAL_PATTERN = re.compile(r"(?:^|[,\{])\s*(?:item|material)\s*:\s*[\"']?([A-Z0-9_]+)", re.IGNORECASE)
# Repository documentation belongs beside the source pack but must not affect client bytes/hash.
SOURCE_ONLY_PATHS = frozenset({"README.md"})


class PackError(RuntimeError):
    """A user-facing pack validation/build error."""


def iter_pack_files(root: Path) -> list[tuple[PurePosixPath, Path]]:
    if not root.is_dir():
        raise PackError(f"Resource-pack source directory does not exist: {root}")

    files: list[tuple[PurePosixPath, Path]] = []
    casefolded: dict[str, str] = {}
    total_size = 0

    for path in root.rglob("*"):
        if path.is_symlink():
            raise PackError(f"Symlinks are not allowed in the pack: {path.relative_to(root)}")
        if not path.is_file():
            continue

        relative = PurePosixPath(path.relative_to(root).as_posix())
        if str(relative) in SOURCE_ONLY_PATHS:
            continue
        if relative.is_absolute() or ".." in relative.parts or "\\" in str(relative):
            raise PackError(f"Unsafe resource-pack path: {relative}")
        if any(ord(character) < 32 for character in str(relative)):
            raise PackError(f"Control character in resource-pack path: {relative}")
        if relative.name in {".DS_Store", "Thumbs.db"} or relative.suffix.lower() == ".zip":
            raise PackError(f"Generated/editor artifact must not be committed into the source pack: {relative}")

        folded = str(relative).casefold()
        previous = casefolded.get(folded)
        if previous is not None and previous != str(relative):
            raise PackError(f"Case-colliding paths are not portable: {previous} / {relative}")
        casefolded[folded] = str(relative)

        total_size += path.stat().st_size
        files.append((relative, path))

    if len(files) > MAX_FILES:
        raise PackError(f"Pack contains too many files: {len(files)} > {MAX_FILES}")
    if total_size > MAX_UNCOMPRESSED_BYTES:
        raise PackError(
            f"Pack is too large uncompressed: {total_size} > {MAX_UNCOMPRESSED_BYTES} bytes"
        )
    return sorted(files, key=lambda item: str(item[0]))


def validate_png(path: Path, relative: PurePosixPath) -> None:
    with path.open("rb") as handle:
        header = handle.read(24)
    if len(header) < 24 or header[:8] != PNG_SIGNATURE or header[12:16] != b"IHDR":
        raise PackError(f"Invalid PNG header: {relative}")
    width, height = struct.unpack(">II", header[16:24])
    if width <= 0 or height <= 0 or width > 8192 or height > 8192:
        raise PackError(f"Implausible PNG dimensions in {relative}: {width}x{height}")


def normalize_resource_location(raw: str, *, default_namespace: str = "icesmp") -> str:
    value = raw.strip().lower()
    if ":" not in value:
        value = f"{default_namespace}:{value}"
    if not RESOURCE_LOCATION_PATTERN.fullmatch(value) or ".." in value.split(":", 1)[1].split("/"):
        raise PackError(f"Invalid resource location: {raw!r}")
    return value


def equipment_assets(root: Path) -> dict[str, Path]:
    assets: dict[str, Path] = {}
    assets_root = root / "assets"
    if not assets_root.is_dir():
        return assets
    for namespace_dir in sorted(path for path in assets_root.iterdir() if path.is_dir()):
        equipment_root = namespace_dir / "equipment"
        if not equipment_root.is_dir():
            continue
        for path in sorted(equipment_root.rglob("*.json")):
            relative = path.relative_to(equipment_root).with_suffix("").as_posix()
            asset_id = f"{namespace_dir.name}:{relative}"
            previous = assets.get(asset_id)
            if previous is not None:
                raise PackError(f"Duplicate equipment asset id {asset_id}: {previous} / {path}")
            assets[asset_id] = path
    return assets


def validate_equipment_assets(root: Path) -> dict[str, Path]:
    """Validate equipment JSON shape and every layer -> texture reference."""
    assets = equipment_assets(root)
    for asset_id, path in sorted(assets.items()):
        namespace = asset_id.split(":", 1)[0]
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exception:
            raise PackError(f"Invalid equipment JSON for {asset_id}: {exception}") from exception
        layers = data.get("layers") if isinstance(data, dict) else None
        if not isinstance(layers, dict) or not layers:
            raise PackError(f"Equipment asset {asset_id} must contain a non-empty 'layers' object")
        for layer_type, entries in layers.items():
            if not isinstance(layer_type, str) or not re.fullmatch(r"[a-z0-9_]+", layer_type):
                raise PackError(f"Equipment asset {asset_id} has invalid layer type: {layer_type!r}")
            if not isinstance(entries, list) or not entries:
                raise PackError(f"Equipment asset {asset_id} layer '{layer_type}' must be a non-empty list")
            for index, entry in enumerate(entries):
                if not isinstance(entry, dict) or not isinstance(entry.get("texture"), str):
                    raise PackError(
                        f"Equipment asset {asset_id} layer '{layer_type}' entry #{index + 1} "
                        "must contain a string 'texture'"
                    )
                texture_id = normalize_resource_location(entry["texture"], default_namespace=namespace)
                texture_namespace, texture_path = texture_id.split(":", 1)
                expected = (
                    root
                    / "assets"
                    / texture_namespace
                    / "textures"
                    / "entity"
                    / "equipment"
                    / layer_type
                    / f"{texture_path}.png"
                )
                if not expected.is_file():
                    relative = expected.relative_to(root).as_posix()
                    raise PackError(
                        f"Equipment asset {asset_id} layer '{layer_type}' references missing texture "
                        f"{texture_id}; expected {relative}"
                    )
    return assets


def is_vanilla_equippable_material(material: str | None) -> bool:
    if not material:
        return False
    value = material.strip().upper()
    if value == "ELYTRA" or value == "CARVED_PUMPKIN":
        return True
    if value.endswith(("_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS")):
        return True
    return value in {
        "PLAYER_HEAD",
        "CREEPER_HEAD",
        "DRAGON_HEAD",
        "PIGLIN_HEAD",
        "SKELETON_SKULL",
        "WITHER_SKELETON_SKULL",
        "ZOMBIE_HEAD",
    }


def _strip_yaml_scalar(raw: str) -> str:
    value = raw.strip()
    if value and value[0] in "\"'" and value[-1:] == value[0]:
        value = value[1:-1]
    return value.strip()


def _sibling_scalar(lines: list[str], index: int, key: str) -> str | None:
    """Return a scalar sibling of a block-style YAML key without needing a YAML dependency."""
    line = lines[index]
    indent = len(line) - len(line.lstrip(" "))
    pattern = re.compile(rf"^\s{{{indent}}}{re.escape(key)}\s*:\s*(.*?)\s*(?:#.*)?$")

    start = index
    while start > 0:
        previous = lines[start - 1]
        stripped = previous.strip()
        if stripped and not stripped.startswith("#"):
            previous_indent = len(previous) - len(previous.lstrip(" "))
            if previous_indent < indent:
                break
        start -= 1
    end = index + 1
    while end < len(lines):
        following = lines[end]
        stripped = following.strip()
        if stripped and not stripped.startswith("#"):
            following_indent = len(following) - len(following.lstrip(" "))
            if following_indent < indent:
                break
        end += 1

    for candidate in lines[start:end]:
        match = pattern.match(candidate)
        if match:
            return _strip_yaml_scalar(match.group(1))
    return None


def validate_config_equipment_references(root: Path, assets: dict[str, Path]) -> tuple[int, int]:
    """Validate explicit config refs and the documented same-id fallback for vanilla-equippable items."""
    config_root = root.parent / "src" / "main" / "resources" / "config"
    if not config_root.is_dir():
        return 0, 0

    explicit_count = 0
    fallback_count = 0
    for config_path in sorted(config_root.glob("*.yml")):
        text = config_path.read_text(encoding="utf-8")
        lines = text.splitlines()

        # Any explicit equipment-asset anywhere (block or flow YAML) must resolve to a pack asset.
        for line_number, line in enumerate(lines, start=1):
            for match in CONFIG_RESOURCE_PATTERN.finditer(line):
                if match.group("key").lower() != "equipment-asset":
                    continue
                explicit_count += 1
                asset_id = normalize_resource_location(match.group("value"))
                if asset_id not in assets:
                    raise PackError(
                        f"{config_path.relative_to(root.parent)}:{line_number}: equipment-asset "
                        f"{asset_id} has no matching assets/<namespace>/equipment/*.json"
                    )

        # Validate the same-id fallback only when the config also proves a vanilla-equippable material.
        for index, line in enumerate(lines):
            model_match = re.search(
                r"(?:key-)?item-model\s*:\s*[\"']?([a-z0-9_.-]+(?::[a-z0-9_./-]+)?)",
                line,
                re.IGNORECASE,
            )
            if not model_match:
                continue
            model_id = normalize_resource_location(model_match.group(1))

            explicit_match = re.search(
                r"equipment-asset\s*:\s*[\"']?([a-z0-9_.-]+(?::[a-z0-9_./-]+)?)",
                line,
                re.IGNORECASE,
            )
            flow_material = FLOW_MATERIAL_PATTERN.search(line)
            material = flow_material.group(1) if flow_material else _sibling_scalar(lines, index, "material")
            explicit = explicit_match.group(1) if explicit_match else _sibling_scalar(lines, index, "equipment-asset")

            if explicit or not is_vanilla_equippable_material(material):
                continue
            fallback_count += 1
            if model_id not in assets:
                raise PackError(
                    f"{config_path.relative_to(root.parent)}:{index + 1}: equippable material {material} "
                    f"uses item-model {model_id} without equipment-asset; the documented same-id "
                    f"fallback requires equipment asset {model_id}, but it is missing"
                )

    return explicit_count, fallback_count


def validate_pack(root: Path) -> list[tuple[PurePosixPath, Path]]:
    required = (root / "pack.mcmeta", root / "pack.png", root / "assets")
    if not required[0].is_file() or not required[1].is_file() or not required[2].is_dir():
        raise PackError("Pack root must contain pack.mcmeta, pack.png and assets/")

    files = iter_pack_files(root)
    json_count = 0
    png_count = 0

    for relative, path in files:
        if path.suffix.lower() in {".json", ".mcmeta"}:
            try:
                json.loads(path.read_text(encoding="utf-8"))
            except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exception:
                raise PackError(f"Invalid JSON in {relative}: {exception}") from exception
            json_count += 1
        if path.suffix.lower() == ".png":
            validate_png(path, relative)
            png_count += 1

    try:
        pack_meta = json.loads((root / "pack.mcmeta").read_text(encoding="utf-8"))
        pack_section = pack_meta["pack"]
    except (KeyError, TypeError, json.JSONDecodeError) as exception:
        raise PackError("pack.mcmeta must contain a JSON object named 'pack'") from exception
    if not isinstance(pack_section, dict) or "description" not in pack_section:
        raise PackError("pack.mcmeta pack section must contain a description")

    equipment = validate_equipment_assets(root)
    explicit_refs, fallback_refs = validate_config_equipment_references(root, equipment)

    total_size = sum(path.stat().st_size for _, path in files)
    print(
        f"Validated resource pack: {len(files)} client files, {json_count} JSON/MCMeta, "
        f"{png_count} PNG, {len(equipment)} equipment assets, "
        f"{explicit_refs} explicit equipment refs, {fallback_refs} checked wearable fallbacks, "
        f"{total_size} bytes"
    )
    return files


def build_pack(root: Path, output: Path) -> tuple[str, int]:
    files = validate_pack(root)
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_suffix(output.suffix + ".tmp")
    temporary.unlink(missing_ok=True)

    try:
        with zipfile.ZipFile(
            temporary,
            mode="w",
            compression=zipfile.ZIP_STORED,
            strict_timestamps=True,
        ) as archive:
            for relative, path in files:
                info = zipfile.ZipInfo(str(relative), date_time=DOS_EPOCH)
                info.compress_type = zipfile.ZIP_STORED
                info.create_system = 3
                info.external_attr = 0o100644 << 16
                info.flag_bits |= 0x800
                archive.writestr(info, path.read_bytes())
        temporary.replace(output)
    finally:
        temporary.unlink(missing_ok=True)

    payload = output.read_bytes()
    sha1 = hashlib.sha1(payload).hexdigest()
    print(f"Built {output}: {len(payload)} bytes, SHA-1 {sha1}")
    return sha1, len(payload)


def write_github_outputs(path: Path, values: dict[str, str | int]) -> None:
    with path.open("a", encoding="utf-8") as handle:
        for key, value in values.items():
            handle.write(f"{key}={value}\n")


def update_metadata(path: Path, url: str, sha1: str) -> bool:
    if not SHA1_PATTERN.fullmatch(sha1):
        raise PackError(f"Invalid SHA-1 metadata value: {sha1}")
    content = (
        "# Generated by .github/workflows/resource-pack-r2.yml. Do not edit by hand.\n"
        f"url={url}\n"
        f"sha1={sha1}\n"
    )
    previous = path.read_text(encoding="utf-8") if path.exists() else None
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")
    return previous != content


def write_manifest(path: Path, *, url: str, sha1: str, size: int, commit: str) -> None:
    if not SHA1_PATTERN.fullmatch(sha1):
        raise PackError(f"Invalid SHA-1 manifest value: {sha1}")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(
            {
                "schema": 1,
                "url": url,
                "sha1": sha1,
                "size": size,
                "source_commit": commit,
            },
            indent=2,
            sort_keys=True,
        )
        + "\n",
        encoding="utf-8",
    )


def command_validate(args: argparse.Namespace) -> int:
    validate_pack(args.source)
    return 0


def command_build(args: argparse.Namespace) -> int:
    sha1, size = build_pack(args.source, args.output)
    file_name = f"icesmp-{sha1}.zip"
    key = f"{args.object_prefix.strip('/')}/{file_name}"
    url = f"{args.public_base_url.rstrip('/')}/{key}"
    if args.github_output:
        write_github_outputs(
            args.github_output,
            {
                "sha1": sha1,
                "size": size,
                "file_name": file_name,
                "object_key": key,
                "url": url,
            },
        )
    return 0


def command_update_metadata(args: argparse.Namespace) -> int:
    changed = update_metadata(args.metadata, args.url, args.sha1)
    print("Updated resource-pack metadata." if changed else "Resource-pack metadata already current.")
    return 0


def command_manifest(args: argparse.Namespace) -> int:
    write_manifest(
        args.output,
        url=args.url,
        sha1=args.sha1,
        size=args.size,
        commit=args.commit,
    )
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    validate = subparsers.add_parser("validate", help="Validate a source pack without building it")
    validate.add_argument("--source", type=Path, default=Path("resource-pack"))
    validate.set_defaults(handler=command_validate)

    build = subparsers.add_parser("build", help="Build a deterministic ZIP and calculate its SHA-1")
    build.add_argument("--source", type=Path, default=Path("resource-pack"))
    build.add_argument("--output", type=Path, required=True)
    build.add_argument("--public-base-url", default="https://assets.icesmp.taliann.dev")
    build.add_argument("--object-prefix", default="resource-packs")
    build.add_argument("--github-output", type=Path)
    build.set_defaults(handler=command_build)

    metadata = subparsers.add_parser("update-metadata", help="Update the generated JAR metadata")
    metadata.add_argument("--metadata", type=Path, default=Path("src/main/resources/resource-pack.properties"))
    metadata.add_argument("--url", required=True)
    metadata.add_argument("--sha1", required=True)
    metadata.set_defaults(handler=command_update_metadata)

    manifest = subparsers.add_parser("manifest", help="Write a public machine-readable manifest")
    manifest.add_argument("--output", type=Path, required=True)
    manifest.add_argument("--url", required=True)
    manifest.add_argument("--sha1", required=True)
    manifest.add_argument("--size", type=int, required=True)
    manifest.add_argument("--commit", required=True)
    manifest.set_defaults(handler=command_manifest)

    return parser


def main(argv: Iterable[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(list(argv) if argv is not None else None)
    try:
        return int(args.handler(args))
    except PackError as exception:
        print(f"resource-pack error: {exception}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
