#!/usr/bin/env python3
"""Validate, build and publish metadata for the IceSMP resource pack.

The ZIP builder is intentionally deterministic: identical client-facing contents produce identical
bytes, SHA-1 values and R2 object names regardless of file mtimes, operating system or zlib
version. Pack files are stored without a second compression pass because PNG assets are already
compressed and the small size difference is worth the stronger reproducibility guarantee.

Wearable validation is intentionally part of the pack build. The server-side ITEM_MODEL id and the
worn EQUIPPABLE asset are different resource identities; a broken equipment JSON, missing layer
texture, explicit equipment-asset config reference, or an invalid same-id wearable fallback must
fail before a pack can be published. Runtime and CI consume the same versioned fallback policy.
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
FALLBACK_POLICY_PATH = (
    Path(__file__).resolve().parents[1]
    / "src"
    / "main"
    / "resources"
    / "wearable-fallback-policy.properties"
)
# Repository documentation belongs beside the source pack but must not affect client bytes/hash.
SOURCE_ONLY_PATHS = frozenset({"README.md"})
MERGE_OWNED_PREFIXES = ("assets/icesmp/", "assets/icesmp_hud/")
MERGE_OWNED_FILES = frozenset(
    {
        "pack.mcmeta",
        "pack.png",
        "assets/minecraft/shaders/core/rendertype_text.vsh",
        "assets/minecraft/shaders/core/rendertype_text.fsh",
        "assets/minecraft/textures/gui/sprites/boss_bar/white_background.png",
        "assets/minecraft/textures/gui/sprites/boss_bar/white_progress.png",
    }
)


class PackError(RuntimeError):
    """A user-facing pack validation/build error."""


def _read_simple_properties(path: Path) -> dict[str, str]:
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError as exception:
        raise PackError(f"Wearable fallback policy is missing/unreadable: {path}") from exception

    values: dict[str, str] = {}
    for line_number, raw in enumerate(lines, start=1):
        line = raw.strip()
        if not line or line.startswith(("#", "!")):
            continue
        if "=" not in line:
            raise PackError(f"Invalid wearable fallback policy line {line_number}: {raw!r}")
        key, value = line.split("=", 1)
        key = key.strip()
        if not key:
            raise PackError(f"Empty wearable fallback policy key on line {line_number}")
        values[key] = value.strip()
    return values


def load_wearable_fallback_policy(
    path: Path = FALLBACK_POLICY_PATH,
) -> tuple[str, frozenset[str], tuple[str, ...]]:
    values = _read_simple_properties(path)
    if values.get("schema") != "1":
        raise PackError("Unsupported wearable fallback policy schema")
    minecraft_version = values.get("minecraft-version", "").strip()
    if not minecraft_version:
        raise PackError("Wearable fallback policy is missing minecraft-version")

    exact = frozenset(
        value.strip().upper()
        for value in values.get("exact", "").split(",")
        if value.strip()
    )
    suffixes = tuple(
        value.strip().upper()
        for value in values.get("suffix", "").split(",")
        if value.strip()
    )
    if not exact and not suffixes:
        raise PackError("Wearable fallback policy contains no material rules")
    return minecraft_version, exact, suffixes


FALLBACK_MINECRAFT_VERSION, FALLBACK_EXACT_MATERIALS, FALLBACK_MATERIAL_SUFFIXES = (
    load_wearable_fallback_policy()
)


def allows_implicit_same_id_fallback(material: str | None) -> bool:
    """Return whether the shared pinned policy permits implicit same-render-id fallback."""
    if not material:
        return False
    value = material.strip().upper()
    if value in FALLBACK_EXACT_MATERIALS:
        return True
    return any(value.endswith(suffix) for suffix in FALLBACK_MATERIAL_SUFFIXES)


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
        if candidate.lstrip().startswith("#"):
            continue
        match = pattern.match(candidate)
        if match:
            return _strip_yaml_scalar(match.group(1))
    return None


def validate_config_equipment_references(root: Path, assets: dict[str, Path]) -> tuple[int, int]:
    """Validate explicit refs and shared-policy same-id fallbacks in checked-in config."""
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
            if line.lstrip().startswith("#"):
                continue
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

        # Runtime uses the exact same versioned policy before it may infer item-model -> equipment asset.
        for index, line in enumerate(lines):
            if line.lstrip().startswith("#"):
                continue
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

            if explicit or not allows_implicit_same_id_fallback(material):
                continue
            fallback_count += 1
            if model_id not in assets:
                raise PackError(
                    f"{config_path.relative_to(root.parent)}:{index + 1}: fallback-policy material {material} "
                    f"uses item-model {model_id} without equipment-asset; the shared "
                    f"{FALLBACK_MINECRAFT_VERSION} same-id fallback requires equipment asset "
                    f"{model_id}, but it is missing"
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
    validate_hud_shader_contract(root)

    total_size = sum(path.stat().st_size for _, path in files)
    print(
        f"Validated resource pack: {len(files)} client files, {json_count} JSON/MCMeta, "
        f"{png_count} PNG, {len(equipment)} equipment assets, "
        f"{explicit_refs} explicit equipment refs, {fallback_refs} checked wearable fallbacks "
        f"(policy {FALLBACK_MINECRAFT_VERSION}), {total_size} bytes"
    )
    return files


def validate_hud_shader_contract(root: Path) -> None:
    """Reject the pre-1.21.11 text shader contract that makes clients fail reload."""
    shader_root = root / "assets" / "minecraft" / "shaders" / "core"
    vertex = shader_root / "rendertype_text.vsh"
    fragment = shader_root / "rendertype_text.fsh"
    if not vertex.exists() and not fragment.exists():
        return
    if not vertex.is_file() or not fragment.is_file():
        raise PackError("IceSMP HUD text shader requires both rendertype_text.vsh and .fsh")

    vertex_text = vertex.read_text(encoding="utf-8")
    fragment_text = fragment.read_text(encoding="utf-8")
    if (not vertex_text.startswith("#version 330")
            or "<minecraft:dynamictransforms.glsl>" not in vertex_text
            or "<minecraft:projection.glsl>" not in vertex_text
            or "fog_spherical_distance" not in vertex_text
            or "<minecraft:globals.glsl>" not in vertex_text
            or "vec2 hudScale = vec2(responsiveScale) * ui / ScreenSize" not in vertex_text
            or "const float HUD_LAYOUT_SCALES[8]" not in vertex_text
            or "int layoutCode = (packedColor.r & 15)" not in vertex_text
            or "vec2 selectedHudScale = hudScale * layoutScale" not in vertex_text
            or "layoutYOffset * 2.0 * clipPosition.w / ScreenSize.y" not in vertex_text
            or "uniform int FogShape" in vertex_text):
        raise PackError("IceSMP HUD vertex shader does not match Minecraft 1.21.11")
    if (not fragment_text.startswith("#version 330")
            or "<minecraft:dynamictransforms.glsl>" not in fragment_text
            or "apply_fog(" not in fragment_text
            or "uniform vec4 FogColor" in fragment_text
            or "linear_fog(" in fragment_text):
        raise PackError("IceSMP HUD fragment shader does not match Minecraft 1.21.11")


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


def _safe_base_entries(path: Path) -> dict[str, bytes]:
    if not path.is_file():
        raise PackError(f"External base pack is missing: {path}")
    entries: dict[str, bytes] = {}
    total_size = 0
    try:
        with zipfile.ZipFile(path) as archive:
            for info in archive.infolist():
                if info.is_dir() or info.filename.startswith(("__MACOSX/", ".DS_Store")):
                    continue
                name = info.filename.replace("\\", "/")
                relative = PurePosixPath(name)
                if relative.is_absolute() or ".." in relative.parts or name != str(relative):
                    raise PackError(f"Unsafe external ZIP entry: {info.filename}")
                if name in entries:
                    raise PackError(f"Duplicate external ZIP entry: {name}")
                total_size += info.file_size
                if len(entries) >= MAX_FILES or total_size > MAX_UNCOMPRESSED_BYTES:
                    raise PackError("External pack exceeds the safe file-count or size budget")
                entries[name] = archive.read(info)
    except zipfile.BadZipFile as exception:
        raise PackError(f"External base pack is not a valid ZIP: {path}") from exception
    if "pack.mcmeta" not in entries:
        raise PackError("External base pack has no root pack.mcmeta")
    return entries


def _overlay_may_replace(name: str) -> bool:
    return name in MERGE_OWNED_FILES or name.startswith(MERGE_OWNED_PREFIXES)


def merge_pack(base: Path, overlay_root: Path, output: Path,
               metadata: Path | None = None) -> tuple[str, int]:
    """Merge an immutable external base with the explicitly owned IceSMP layer."""
    entries = _safe_base_entries(base)
    collisions: list[str] = []
    for relative, source in validate_pack(overlay_root):
        name = str(relative)
        if name in entries:
            if not _overlay_may_replace(name):
                raise PackError(f"Unowned resource-pack collision: {name}")
            collisions.append(name)
        entries[name] = source.read_bytes()

    required = (
        "pack.mcmeta",
        "assets/minecraft/shaders/core/rendertype_text.vsh",
        "assets/icesmp_hud/hud-manifest.json",
    )
    for name in required:
        if name not in entries:
            raise PackError(f"Merged resource pack is missing required IceSMP entry: {name}")
    if not any(name.startswith("assets/icesmp/") for name in entries):
        raise PackError("Merged resource pack is missing the IceSMP gameplay namespace")

    try:
        root = json.loads(entries["pack.mcmeta"].decode("utf-8"))
        pack = root["pack"]
        if not isinstance(pack, dict):
            raise TypeError
    except (UnicodeDecodeError, json.JSONDecodeError, KeyError, TypeError) as exception:
        raise PackError("Merged pack.mcmeta is invalid") from exception
    pack.pop("supported_formats", None)
    pack["pack_format"] = 75
    pack["min_format"] = 75
    pack["max_format"] = 75
    entries["pack.mcmeta"] = json.dumps(
        root, ensure_ascii=False, separators=(",", ":"), sort_keys=True
    ).encode("utf-8")

    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_suffix(output.suffix + ".tmp")
    temporary.unlink(missing_ok=True)
    try:
        with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_STORED,
                             strict_timestamps=True) as archive:
            for name in sorted(entries):
                info = zipfile.ZipInfo(name, date_time=DOS_EPOCH)
                info.compress_type = zipfile.ZIP_STORED
                info.create_system = 3
                info.external_attr = 0o100644 << 16
                info.flag_bits |= 0x800
                archive.writestr(info, entries[name])
        temporary.replace(output)
    finally:
        temporary.unlink(missing_ok=True)

    payload = output.read_bytes()
    sha1 = hashlib.sha1(payload).hexdigest()
    if metadata is not None:
        metadata.parent.mkdir(parents=True, exist_ok=True)
        metadata.write_text(f"sha1={sha1}\nsize={len(payload)}\n", encoding="utf-8")
    print(
        f"Merged {output}: {len(entries)} files, {len(collisions)} owned collisions, "
        f"{len(payload)} bytes, SHA-1 {sha1}"
    )
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


def command_merge(args: argparse.Namespace) -> int:
    merge_pack(args.base, args.source, args.output, args.metadata)
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

    merge = subparsers.add_parser(
        "merge", help="Deterministically merge an external base with the owned IceSMP layer")
    merge.add_argument("--base", type=Path, required=True)
    merge.add_argument("--source", type=Path, default=Path("resource-pack"))
    merge.add_argument("--output", type=Path, required=True)
    merge.add_argument("--metadata", type=Path)
    merge.set_defaults(handler=command_merge)

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
