#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def replace_once(relative: str, old: str, new: str) -> None:
    path = ROOT / relative
    content = path.read_text(encoding="utf-8")
    count = content.count(old)
    if count != 1:
        raise RuntimeError(f"{relative}: expected one occurrence of {old!r}, got {count}")
    path.write_text(content.replace(old, new, 1), encoding="utf-8")


replace_once(
    "CLAUDE.md",
    "A dokumentált release 551 Java-fájl / 90 manager.",
    "A dokumentált release 569 Java-fájl / 90 manager.",
)
replace_once(
    "docs/ARCHITECTURE.md",
    "| `data/` | 13 | Enumok és értékobjektumok",
    "| `data/` | 14 | Enumok és értékobjektumok",
)

print("runtime hardening documentation drift updated")
