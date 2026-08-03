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


def decode_all(compact: str) -> bytes:
    try:
        return base64.b64decode(compact, validate=True)
    except Exception:
        padding = (-len(compact)) % 4
        return base64.b64decode(compact + ("=" * padding), validate=True)


def find_window(data: bytes, size: int, expected_sha: str) -> int | None:
    if len(data) < size:
        return None
    for offset in range(len(data) - size + 1):
        if sha256(data[offset : offset + size]) == expected_sha:
            return offset
    return None


def combine_with_known_part(
    known: bytes,
    sources: list[tuple[str, bytes]],
    expected_size: int,
    expected_sha: str,
) -> tuple[bytes, str] | None:
    remaining = expected_size - len(known)
    if remaining <= 0:
        return None
    for source_name, source in sources:
        if len(source) < remaining:
            continue
        for offset in range(len(source) - remaining + 1):
            window = source[offset : offset + remaining]
            left = known + window
            if sha256(left) == expected_sha:
                return left, f"known-prefix-plus-{source_name}-window-{offset}"
            right = window + known
            if sha256(right) == expected_sha:
                return right, f"{source_name}-window-{offset}-plus-known-suffix"
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
    variant_report: list[dict[str, Any]] = []
    verified_variants: dict[int, list[tuple[str, bytes]]] = {}
    exact_records: list[dict[str, Any]] = []
    anomalies: list[dict[str, Any]] = []

    # First pass: inventory and independently validate every exact/lettered variant.
    for comment in comments:
        body = str(comment.get("body", "")).replace("\r\n", "\n").strip()
        first = body.split("\n", 1)[0]
        if first.startswith("PPROOT2-"):
            observed.append(first)
        match = VARIANT.fullmatch(body)
        if not match:
            continue

        label = match.group(1)
        index = int(label[:2])
        declared_sha = match.group(2)
        declared_size = int(match.group(3))
        compact = re.sub(r"\s+", "", match.group(4))
        raw_all = decode_all(compact)
        verified_raw: bytes | None = None
        verification = "none"
        trailing_characters = 0

        try:
            candidate, trailing_characters = canonical_prefix(compact, declared_size)
            if sha256(candidate) == declared_sha:
                verified_raw = candidate
                verification = "canonical-prefix"
        except Exception:
            pass

        if verified_raw is None:
            offset = find_window(raw_all, declared_size, declared_sha)
            if offset is not None:
                verified_raw = raw_all[offset : offset + declared_size]
                verification = f"verified-window-offset-{offset}"

        variant_report.append(
            {
                "label": label,
                "declared_size": declared_size,
                "declared_sha256": declared_sha,
                "decoded_size": len(raw_all),
                "decoded_sha256": sha256(raw_all),
                "verified": verified_raw is not None,
                "verification": verification,
                "trailing_characters": trailing_characters,
                "comment_id": comment.get("id"),
            }
        )
        if verified_raw is not None and not label.isdigit():
            verified_variants.setdefault(index, []).append((label, verified_raw))
        if label.isdigit() and index in range(13):
            exact_records.append(
                {
                    "index": index,
                    "expected_sha": declared_sha,
                    "expected_size": declared_size,
                    "compact": compact,
                    "raw_all": raw_all,
                    "comment_id": comment.get("id"),
                    "verified_raw": verified_raw,
                    "verification": verification,
                    "trailing_characters": trailing_characters,
                }
            )

    for record in exact_records:
        index = int(record["index"])
        if index in selected:
            raise SystemExit(f"duplicate exact PPROOT2 chunk {index:02d}")
        expected_sha = str(record["expected_sha"])
        expected_size = int(record["expected_size"])
        raw_all = bytes(record["raw_all"])
        raw = record["verified_raw"]
        repair = str(record["verification"])

        if raw is None:
            sources: list[tuple[str, bytes]] = [(f"exact-{index:02d}", raw_all)]
            sources.extend(verified_variants.get(index, []))
            recovered: tuple[bytes, str] | None = None
            for label, known in verified_variants.get(index, []):
                recovered = combine_with_known_part(
                    known,
                    sources,
                    expected_size,
                    expected_sha,
                )
                if recovered is not None:
                    anomalies.append(
                        {
                            "index": f"{index:02d}",
                            "type": "chunk-reconstructed-from-independently-hashed-variant",
                            "variant": label,
                            "repair": recovered[1],
                        }
                    )
                    break
            if recovered is None:
                details = [
                    item for item in variant_report
                    if str(item.get("label", "")).startswith(f"{index:02d}")
                ]
                raise SystemExit(
                    f"chunk {index:02d} could not be reconstructed with exact SHA {expected_sha}; "
                    f"variants={json.dumps(details, sort_keys=True)}"
                )
            raw, repair = recovered

        if len(raw) != expected_size or sha256(raw) != expected_sha:
            raise SystemExit(f"chunk {index:02d} final validation failed")

        trailing_characters = int(record["trailing_characters"])
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
            "sha256": sha256(raw),
            "comment_id": record["comment_id"],
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
        "variants": variant_report,
        "anomalies": anomalies,
    }
    (output / "result-pre-xz.json").write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
