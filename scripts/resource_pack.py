#!/usr/bin/env python3
"""Validate, build and publish metadata for the IceSMP resource pack.

The ZIP builder is intentionally deterministic: identical client-facing contents produce identical
bytes, SHA-1 values and R2 object names regardless of file mtimes, operating system or zlib
version. Pack files are stored without a second compression pass because PNG assets are already
compressed and the small size difference is worth the stronger reproducibility guarantee.
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

    total_size = sum(path.stat().st_size for _, path in files)
    print(
        f"Validated resource pack: {len(files)} client files, {json_count} JSON/MCMeta, "
        f"{png_count} PNG, {total_size} bytes"
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
