#!/usr/bin/env python3
"""Validate the repository FancyNpcs export without contacting skin services."""

from __future__ import annotations

import argparse
import base64
import json
import struct
import sys
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlsplit, urlunsplit


@dataclass(frozen=True)
class SkinReference:
    npc_id: str
    npc_name: str
    identifier: str
    config_path: str


def _scalar(raw: str) -> str:
    value = raw.strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in {'"', "'"}:
        return value[1:-1]
    return value


def parse_references(config_path: Path) -> list[SkinReference]:
    references: list[SkinReference] = []
    current_id = "unknown"
    current_name = "unknown"
    in_skin = False
    for line in config_path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        indent = len(line) - len(line.lstrip(" "))
        if indent == 2 and stripped.endswith(":"):
            current_id = stripped[:-1]
            current_name = "unknown"
            in_skin = False
        elif indent == 4 and stripped.startswith("name:"):
            current_name = _scalar(stripped.split(":", 1)[1])
        elif indent == 4 and stripped == "skin:":
            in_skin = True
        elif indent <= 4 and stripped and not stripped.startswith("#"):
            in_skin = False
        elif in_skin and indent == 6 and stripped.startswith("identifier:"):
            identifier = _scalar(stripped.split(":", 1)[1])
            references.append(SkinReference(
                current_id,
                current_name,
                identifier,
                f"npcs.{current_id}.skin.identifier",
            ))
    return references


def png_dimensions(path: Path) -> tuple[int, int]:
    data = path.read_bytes()[:24]
    if len(data) < 24 or data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        raise ValueError("not a PNG with an IHDR header")
    return struct.unpack(">II", data[16:24])


def sanitized_url(identifier: str) -> str:
    split = urlsplit(identifier)
    return urlunsplit((split.scheme, split.netloc, split.path, "", ""))


def cache_path_for_identifier(cache_dir: Path, identifier: str) -> Path:
    encoded = base64.b64encode(identifier.encode("utf-8")).decode("ascii")
    return cache_dir / f"{encoded}.json"


def validate_remote_cache(reference: SkinReference, cache_dir: Path) -> str | None:
    cache_path = cache_path_for_identifier(cache_dir, reference.identifier)
    if not cache_path.is_file():
        return (
            f"CACHE_MISSING npc={reference.npc_name} path={reference.config_path} "
            f"url={sanitized_url(reference.identifier)}"
        )
    try:
        payload = json.loads(cache_path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exception:
        return (
            f"CACHE_INVALID npc={reference.npc_name} path={reference.config_path} "
            f"url={sanitized_url(reference.identifier)} reason={type(exception).__name__}"
        )
    skin_data = payload.get("skinData")
    if not isinstance(skin_data, dict):
        return (
            f"CACHE_INVALID npc={reference.npc_name} path={reference.config_path} "
            f"url={sanitized_url(reference.identifier)} reason=missing_skinData"
        )
    if skin_data.get("identifier") != reference.identifier:
        return (
            f"CACHE_INVALID npc={reference.npc_name} path={reference.config_path} "
            f"url={sanitized_url(reference.identifier)} reason=identifier_mismatch"
        )
    if not isinstance(skin_data.get("textureValue"), str) or not skin_data["textureValue"].strip():
        return (
            f"CACHE_INVALID npc={reference.npc_name} path={reference.config_path} "
            f"url={sanitized_url(reference.identifier)} reason=missing_textureValue"
        )
    if not isinstance(skin_data.get("textureSignature"), str) or not skin_data["textureSignature"].strip():
        return (
            f"CACHE_INVALID npc={reference.npc_name} path={reference.config_path} "
            f"url={sanitized_url(reference.identifier)} reason=missing_textureSignature"
        )
    return None


def validate(config_path: Path, skins_dir: Path, cache_dir: Path, strict_remote: bool) -> int:
    failures: list[str] = []
    remote: list[SkinReference] = []
    local_count = 0
    cached_remote = 0
    for reference in parse_references(config_path):
        if reference.identifier.startswith(("https://", "http://")):
            remote.append(reference)
            continue

        local_count += 1
        skin_path = (skins_dir / reference.identifier).resolve()
        try:
            skin_path.relative_to(skins_dir.resolve())
        except ValueError:
            failures.append(
                f"UNSAFE_PATH npc={reference.npc_name} path={reference.config_path} file={reference.identifier}"
            )
            continue
        if not skin_path.is_file():
            failures.append(
                f"MISSING npc={reference.npc_name} path={reference.config_path} file={reference.identifier}"
            )
            continue
        try:
            width, height = png_dimensions(skin_path)
        except (OSError, ValueError) as exception:
            failures.append(
                f"INVALID npc={reference.npc_name} path={reference.config_path} "
                f"file={reference.identifier} reason={exception}"
            )
            continue
        if (width, height) not in {(64, 32), (64, 64)}:
            failures.append(
                f"DIMENSIONS npc={reference.npc_name} path={reference.config_path} "
                f"file={reference.identifier} size={width}x{height}"
            )

    for reference in remote:
        cache_failure = validate_remote_cache(reference, cache_dir)
        cache_state = "missing-or-invalid"
        if cache_failure is None:
            cached_remote += 1
            cache_state = "texture+signature"
        else:
            failures.append(cache_failure)
        print(
            "REMOTE_SKIN "
            f"npc={reference.npc_name} path={reference.config_path} "
            f"url={sanitized_url(reference.identifier)} cache={cache_state}"
        )

    print(
        "FancyNpcs snapshot: "
        f"local={local_count}, remote={len(remote)}, cached_remote={cached_remote}, "
        f"failures={len(failures)}"
    )
    for failure in failures:
        print(failure, file=sys.stderr)
    if strict_remote and remote:
        print("Remote skin identifiers are forbidden in strict mode.", file=sys.stderr)
        return 1
    return 1 if failures else 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--config", type=Path, default=Path("Other/plugins/FancyNpcs/npcs.yml")
    )
    parser.add_argument(
        "--skins-dir", type=Path, default=Path("Other/plugins/FancyNpcs/skins")
    )
    parser.add_argument(
        "--cache-dir", type=Path, default=Path("Other/plugins/FancyNpcs/.data/skins")
    )
    parser.add_argument("--strict-remote", action="store_true")
    args = parser.parse_args()
    return validate(args.config, args.skins_dir, args.cache_dir, args.strict_remote)


if __name__ == "__main__":
    raise SystemExit(main())
