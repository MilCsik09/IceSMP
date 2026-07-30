#!/usr/bin/env python3
"""Validate repository-local Markdown links without modifying the repository."""

from __future__ import annotations

import argparse
import re
import sys
import urllib.parse
from pathlib import Path

LINK = re.compile(r"!?\[[^\]]*]\(([^)\n]+)\)")
HEADING = re.compile(r"^(#{1,6})\s+(.+?)\s*#*\s*$")
SCHEME = re.compile(r"^[A-Za-z][A-Za-z0-9+.-]*:")


def _slug(value: str) -> str:
    value = re.sub(r"<[^>]+>", "", value)
    value = re.sub(r"[`*_~\[\]]", "", value).strip().lower()
    value = re.sub(r"[^\w\-\s]", "", value, flags=re.UNICODE)
    return re.sub(r"[\s-]+", "-", value).strip("-")


def _anchors(path: Path) -> set[str]:
    anchors: set[str] = set()
    duplicates: dict[str, int] = {}
    fenced = False
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.lstrip().startswith("```"):
            fenced = not fenced
            continue
        if fenced:
            continue
        match = HEADING.match(line)
        if not match:
            continue
        base = _slug(match.group(2))
        index = duplicates.get(base, 0)
        duplicates[base] = index + 1
        anchors.add(base if index == 0 else f"{base}-{index}")
    return anchors


def _target(raw: str) -> str:
    raw = raw.strip()
    if raw.startswith("<") and raw.endswith(">"):
        return raw[1:-1]
    # Optional Markdown title after a whitespace-separated target.
    return re.split(r'\s+(?=["\'])', raw, maxsplit=1)[0]


def check(root: Path) -> list[str]:
    failures: list[str] = []
    anchor_cache: dict[Path, set[str]] = {}
    for source in sorted(root.rglob("*.md")):
        if any(part in {".git", "build"} for part in source.parts):
            continue
        text = source.read_text(encoding="utf-8")
        for line_number, line in enumerate(text.splitlines(), 1):
            for match in LINK.finditer(line):
                target = _target(match.group(1))
                if not target or SCHEME.match(target) or target.startswith(("/", "//")):
                    continue
                path_part, separator, fragment = target.partition("#")
                decoded_path = urllib.parse.unquote(path_part)
                resolved = source if not decoded_path else (source.parent / decoded_path).resolve()
                try:
                    resolved.relative_to(root)
                except ValueError:
                    failures.append(
                        f"{source.relative_to(root)}:{line_number}: link escapes repository: {target}"
                    )
                    continue
                if resolved.is_dir():
                    resolved = resolved / "README.md"
                if not resolved.is_file():
                    failures.append(
                        f"{source.relative_to(root)}:{line_number}: missing target: {target}"
                    )
                    continue
                if separator and fragment and resolved.suffix.lower() == ".md":
                    expected = urllib.parse.unquote(fragment).lower()
                    anchors = anchor_cache.setdefault(resolved, _anchors(resolved))
                    if expected not in anchors:
                        failures.append(
                            f"{source.relative_to(root)}:{line_number}: missing anchor "
                            f"#{fragment} in {resolved.relative_to(root)}"
                        )
    return failures


def main() -> int:
    parser = argparse.ArgumentParser(description="Check repository-local Markdown links.")
    parser.add_argument("--root", default=".", help="Repository root")
    args = parser.parse_args()
    root = Path(args.root).resolve()
    failures = check(root)
    if failures:
        print("\n".join(failures), file=sys.stderr)
        print(f"Markdown link check: FAIL ({len(failures)} error(s))", file=sys.stderr)
        return 1
    print("Markdown link check: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
