#!/usr/bin/env python3
from __future__ import annotations

import json
import pathlib
import tarfile
import sys

BLOCK = 512


def tar_checksum(header: bytes) -> tuple[bool, int | None, int]:
    if len(header) != BLOCK or header == b"\0" * BLOCK:
        return False, None, 0
    raw = header[148:156].strip(b"\0 ")
    try:
        expected = int(raw or b"0", 8)
    except ValueError:
        return False, None, 0
    calculated = sum(header[:148]) + (8 * ord(" ")) + sum(header[156:])
    return expected == calculated, expected, calculated


def candidate_offsets(data: bytes) -> list[int]:
    candidates: set[int] = {0}
    manifest = b".payload-manifest.json"
    cursor = 0
    while True:
        found = data.find(manifest, cursor)
        if found < 0:
            break
        candidates.add(found)
        cursor = found + 1
    cursor = 0
    while True:
        found = data.find(b"ustar", cursor)
        if found < 0:
            break
        if found >= 257:
            candidates.add(found - 257)
        cursor = found + 1
    for offset in range(0, min(len(data), 1024 * 1024), BLOCK):
        candidates.add(offset)
    return sorted(offset for offset in candidates if offset >= 0 and offset + BLOCK <= len(data))


def safe_relative(name: str) -> pathlib.PurePosixPath:
    path = pathlib.PurePosixPath(name)
    if path.is_absolute() or ".." in path.parts or not path.parts:
        raise RuntimeError(f"unsafe tar path: {name!r}")
    return path


def main() -> int:
    if len(sys.argv) != 4:
        raise SystemExit("usage: extract_complete_tar.py PARTIAL_TAR OUTPUT_DIR REPORT_JSON")
    archive = pathlib.Path(sys.argv[1])
    output = pathlib.Path(sys.argv[2])
    report_path = pathlib.Path(sys.argv[3])
    output.mkdir(parents=True, exist_ok=True)

    data = archive.read_bytes()
    valid_candidates: list[dict[str, object]] = []
    for offset in candidate_offsets(data):
        header = data[offset : offset + BLOCK]
        valid, expected, calculated = tar_checksum(header)
        if not valid:
            continue
        name = header[:100].split(b"\0", 1)[0].decode("utf-8", errors="replace")
        valid_candidates.append(
            {
                "offset": offset,
                "name": name,
                "expected_checksum": expected,
                "calculated_checksum": calculated,
                "magic": header[257:265].decode("ascii", errors="replace"),
            }
        )

    diagnostics = {
        "archive_bytes": len(data),
        "first_128_hex": data[:128].hex(),
        "first_128_text": data[:128].decode("utf-8", errors="replace"),
        "manifest_offsets": [
            index
            for index in range(len(data))
            if data.startswith(b".payload-manifest.json", index)
        ][:20],
        "valid_tar_candidates": valid_candidates[:50],
    }
    print(json.dumps(diagnostics, indent=2, sort_keys=True))
    if not valid_candidates:
        report_path.write_text(
            json.dumps({**diagnostics, "error": "no checksum-valid tar header found"}, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        raise SystemExit("no checksum-valid tar header found")

    # Prefer an archive beginning with the payload manifest. Otherwise use the earliest valid header.
    selected = next(
        (candidate for candidate in valid_candidates if candidate["name"] == ".payload-manifest.json"),
        valid_candidates[0],
    )
    start = int(selected["offset"])
    trimmed = archive.with_name("partial-trimmed.tar")
    trimmed.write_bytes(data[start:])

    complete: list[dict[str, object]] = []
    truncated: dict[str, object] | None = None
    try:
        with tarfile.open(trimmed, mode="r:") as payload:
            for member in payload:
                if not member.isfile():
                    continue
                relative = safe_relative(member.name)
                try:
                    stream = payload.extractfile(member)
                    body = stream.read() if stream is not None else b""
                except (tarfile.ReadError, EOFError, OSError) as exc:
                    truncated = {
                        "path": member.name,
                        "declared_size": member.size,
                        "error": f"{type(exc).__name__}: {exc}",
                    }
                    break
                if len(body) != member.size:
                    truncated = {
                        "path": member.name,
                        "declared_size": member.size,
                        "actual_size": len(body),
                        "error": "short member body",
                    }
                    break
                target = output.joinpath(*relative.parts)
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(body)
                target.chmod(member.mode & 0o777)
                complete.append({"path": member.name, "size": member.size})
    except tarfile.ReadError as exc:
        if not complete:
            raise
        truncated = truncated or {
            "path": None,
            "error": f"ReadError after complete members: {exc}",
        }

    result = {
        **diagnostics,
        "selected_tar_start": selected,
        "trimmed_archive_bytes": trimmed.stat().st_size,
        "complete_count": len(complete),
        "complete": complete,
        "truncated": truncated,
    }
    report_path.write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
