from __future__ import annotations

import re
from pathlib import Path
from typing import Any

from .java_scanner import JavaIndex
from .models import Evidence, Finding
from .util import flatten_yaml_paths, iter_files, java_strings, line_number, posix, scan_calls, split_top_level


def scan_messages(root: Path, index: JavaIndex) -> tuple[list[dict[str, Any]], list[Finding]]:
    defined: dict[str, list[dict[str, Any]]] = {}
    findings: list[Finding] = []
    resources = root / "src/main/resources"
    if resources.exists():
        for path in sorted(iter_files(resources, ("*.yml", "*.yaml"))):
            relative = posix(path, root)
            flat = flatten_yaml_paths(path)
            message_file = "message" in path.name.lower() or "/messages/" in f"/{relative}"
            for key, value in flat.items():
                normalized = key[9:] if key.startswith("messages.") else key
                if message_file or key.startswith("messages."):
                    defined.setdefault(normalized, []).append({"file": relative, "value": value})

    used: dict[str, list[dict[str, Any]]] = {}
    fallbacks: dict[str, set[str]] = {}
    for src in index.sources:
        for offset, call in scan_calls(src.source, "get"):
            prefix = src.source[max(0, offset-60):offset]
            if "messageManager." not in prefix and "messages." not in prefix:
                continue
            args = split_top_level(call)
            if not args: continue
            values = java_strings(args[0])
            if not values: continue
            key = values[0]
            normalized = key[9:] if key.startswith("messages.") else key
            used.setdefault(normalized, []).append({"source": src.relative, "line": line_number(src.source, offset), "symbol": "MessageManager.get"})
            if len(args) > 1:
                fallback_values = java_strings(args[1])
                if fallback_values: fallbacks.setdefault(normalized, set()).add(fallback_values[0])
        for match in re.finditer(r"\.sendMessage\s*\(\s*(\"(?:\\.|[^\"\\])*\")", src.source):
            value = java_strings(match.group(1))[0]
            if value.strip():
                findings.append(Finding("WARN", "HARDCODED_PLAYER_MESSAGE",
                                        f"Hard-coded player-facing message: {value[:80]}", f"message.hardcoded.{src.class_name}.{line_number(src.source, match.start())}",
                                        (Evidence(src.relative, line_number(src.source, match.start()), "sendMessage"),)))

    messages: list[dict[str, Any]] = []
    for key in sorted(set(defined) | set(used)):
        defs, uses = defined.get(key, []), used.get(key, [])
        if uses and not defs:
            findings.append(Finding("WARN", "MESSAGE_KEY_MISSING_DEFAULT",
                                    f"Message key '{key}' is used but not defined in a default message resource.", f"message.{key}",
                                    tuple(Evidence(x["source"], x["line"], x["symbol"]) for x in uses[:3])))
        if defs and not uses:
            findings.append(Finding("WARN", "MESSAGE_KEY_UNUSED", f"Message key '{key}' is defined but has no static use.", f"message.{key}"))
        if len(fallbacks.get(key, set())) > 1:
            findings.append(Finding("WARN", "MESSAGE_FALLBACK_DRIFT",
                                    f"Message key '{key}' has multiple inline fallbacks.", f"message.{key}"))
        messages.append({"id": f"message.{key}", "key": key, "definitions": defs,
                         "uses": sorted(uses, key=lambda x: (x["source"], x["line"])),
                         "fallbacks": sorted(fallbacks.get(key, set())),
                         "confidence": "HIGH" if defs and uses else "MEDIUM"})
    return messages, findings
