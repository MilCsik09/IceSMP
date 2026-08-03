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


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def canonical_decode(compact: str, size: int) -> bytes:
    encoded_length = 4 * ((size + 2) // 3)
    padding = (-size) % 3
    data_characters = encoded_length - padding
    canonical = compact[:data_characters] + ("=" * padding)
    raw = base64.b64decode(canonical, validate=True)
    if len(raw) != size:
        raise ValueError(f"decoded size mismatch: {len(raw)} != {size}")
    return raw


def main() -> int:
    if len(sys.argv) != 3:
        raise SystemExit("usage: recover_prefix.py COMMENTS_JSON OUTPUT_DIR")
    comments = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
    output = pathlib.Path(sys.argv[2])
    chunks = output / "chunks"
    chunks.mkdir(parents=True, exist_ok=True)
    selected: dict[int, dict[str, Any]] = {}

    for comment in comments:
        body = str(comment.get("body", "")).replace("\r\n", "\n").strip()
        match = EXACT.fullmatch(body)
        if not match:
            continue
        index = int(match.group(1))
        if index not in range(11):
            continue
        if index in selected:
            raise SystemExit(f"duplicate prefix chunk {index:02d}")
        expected_sha = match.group(2)
        expected_size = int(match.group(3))
        compact = re.sub(r"\s+", "", match.group(4))
        raw = canonical_decode(compact, expected_size)
        actual_sha = sha256(raw)
        if actual_sha != expected_sha:
            raise SystemExit(
                f"prefix chunk {index:02d} sha mismatch: {actual_sha} != {expected_sha}"
            )
        (chunks / f"{index:02d}.bin").write_bytes(raw)
        selected[index] = {
            "size": len(raw),
            "sha256": actual_sha,
            "comment_id": comment.get("id"),
        }

    missing = sorted(set(range(11)) - set(selected))
    if missing:
        raise SystemExit("missing prefix chunks: " + ",".join(f"{i:02d}" for i in missing))

    compressed = b"".join((chunks / f"{index:02d}.bin").read_bytes() for index in range(11))
    (output / "root-prefix.xz").write_bytes(compressed)
    (output / "prefix-result.json").write_text(
        json.dumps(
            {
                "parts": 11,
                "compressed_bytes": len(compressed),
                "compressed_sha256": sha256(compressed),
                "chunks": selected,
            },
            indent=2,
            sort_keys=True,
        )
        + "\n",
        encoding="utf-8",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
