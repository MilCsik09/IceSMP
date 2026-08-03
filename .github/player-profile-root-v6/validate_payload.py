#!/usr/bin/env python3
from __future__ import annotations

import base64
import hashlib
import json
import pathlib
import re
import sys
from typing import Any

EXACT = re.compile(
    r"\APPROOT2-(\d{2})/13 sha256=([0-9a-f]{64}) bytes=(\d+)\n([A-Za-z0-9+/=\r\n\t ]+)\Z"
)
VARIANT = re.compile(
    r"\APPROOT2-([0-9]{2}[a-z]?)/13 sha256=([0-9a-f]{64}) bytes=(\d+)\n([A-Za-z0-9+/=\r\n\t ]+)\Z"
)


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def canonical_prefix(compact: str, size: int) -> tuple[bytes, int]:
    encoded_length = 4 * ((size + 2) // 3)
    padding = (-size) % 3
    data_characters = encoded_length - padding
    if len(compact) < data_characters:
        raise ValueError(
            f"base64 shorter than declared payload: {len(compact)} < {data_characters}"
        )
    canonical = compact[:data_characters] + ("=" * padding)
    raw = base64.b64decode(canonical, validate=True)
    if len(raw) != size:
        raise ValueError(f"canonical decode size mismatch: {len(raw)} != {size}")
    return raw, len(compact) - data_characters


def find_window(data: bytes, size: int, expected_sha: str) -> int | None:
    if len(data) < size:
        return None
    for offset in range(len(data) - size + 1):
        if sha256(data[offset : offset + size]) == expected_sha:
            return offset
    return None


def main() -> int:
    if len(sys.argv) != 3:
        raise SystemExit("usage: validate_payload.py COMMENTS_JSON OUTPUT_DIR")
    comments_path = pathlib.Path(sys.argv[1])
    output = pathlib.Path(sys.argv[2])
    output.mkdir(parents=True, exist_ok=True)
    chunks_dir = output / "chunks"
    chunks_dir.mkdir(parents=True, exist_ok=True)

    comments: list[dict[str, Any]] = json.loads(comments_path.read_text(encoding="utf-8"))
    selected: dict[int, dict[str, Any]] = {}
    observed: list[str] = []
    variants: list[dict[str, Any]] = []
    anomalies: list[dict[str, Any]] = []

    for comment in comments:
        body = str(comment.get("body", "")).replace("\r\n", "\n").strip()
        first = body.split("\n", 1)[0]
        if first.startswith("PPROOT2-"):
            observed.append(first)

        variant_match = VARIANT.fullmatch(body)
        if variant_match:
            compact_variant = re.sub(r"\s+", "", variant_match.group(4))
            try:
                raw_variant = base64.b64decode(compact_variant, validate=True)
                variant_actual = sha256(raw_variant)
                variants.append(
                    {
                        "label": variant_match.group(1),
                        "declared_size": int(variant_match.group(3)),
                        "declared_sha256": variant_match.group(2),
                        "decoded_size": len(raw_variant),
                        "decoded_sha256": variant_actual,
                        "comment_id": comment.get("id"),
                    }
                )
            except Exception as exc:
                variants.append(
                    {
                        "label": variant_match.group(1),
                        "error": str(exc),
                        "comment_id": comment.get("id"),
                    }
                )

        match = EXACT.fullmatch(body)
        if not match:
            continue
        index = int(match.group(1))
        if index not in range(13):
            continue
        if index in selected:
            raise SystemExit(f"duplicate exact PPROOT2 chunk {index:02d}")

        expected_sha = match.group(2)
        expected_size = int(match.group(3))
        compact = re.sub(r"\s+", "", match.group(4))

        raw, trailing_characters = canonical_prefix(compact, expected_size)
        actual_sha = sha256(raw)
        repair = "canonical-prefix"

        if actual_sha != expected_sha:
            try:
                raw_all = base64.b64decode(compact, validate=True)
            except Exception:
                raw_all = b""
            offset = find_window(raw_all, expected_size, expected_sha)
            if offset is None:
                raise SystemExit(
                    f"chunk {index:02d} sha mismatch after canonical padding repair: "
                    f"{actual_sha} != {expected_sha}; decoded_size={len(raw_all)}"
                )
            raw = raw_all[offset : offset + expected_size]
            actual_sha = sha256(raw)
            repair = f"verified-window-offset-{offset}"
            anomalies.append(
                {
                    "index": f"{index:02d}",
                    "type": "verified-window-selected-from-corrupt-comment",
                    "offset": offset,
                    "decoded_size": len(raw_all),
                }
            )

        if actual_sha != expected_sha:
            raise SystemExit(f"chunk {index:02d} final sha mismatch")
        if trailing_characters:
            anomalies.append(
                {
                    "index": f"{index:02d}",
                    "type": "trailing-base64-characters-excluded-after-hash-verification",
                    "trailing_characters": trailing_characters,
                }
            )

        (chunks_dir / f"{index:02d}.bin").write_bytes(raw)
        selected[index] = {
            "size": len(raw),
            "sha256": actual_sha,
            "comment_id": comment.get("id"),
            "repair": repair,
            "trailing_characters": trailing_characters,
        }

    missing = sorted(set(range(13)) - set(selected))
    if missing:
        raise SystemExit(
            "missing exact PPROOT2 chunks: "
            + ",".join(f"{index:02d}" for index in missing)
            + "\nobserved headers:\n"
            + "\n".join(observed)
        )

    compressed = b"".join((chunks_dir / f"{index:02d}.bin").read_bytes() for index in range(13))
    (output / "root.patch.xz").write_bytes(compressed)
    result = {
        "payload": "PPROOT2",
        "parts": 13,
        "compressed_bytes": len(compressed),
        "compressed_sha256": sha256(compressed),
        "chunks": selected,
        "observed_headers": observed,
        "variants": variants,
        "anomalies": anomalies,
    }
    (output / "result-pre-xz.json").write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
