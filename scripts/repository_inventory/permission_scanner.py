from __future__ import annotations

from pathlib import Path
from typing import Any

from .java_scanner import JavaIndex
from .models import Evidence, Finding
from .util import line_number, resolve_java_string, scan_calls, split_top_level


def _kind(relative: str) -> str:
    for name in ("commands", "gui", "listeners", "managers", "integration"):
        if f"/{name}/" in f"/{relative}":
            return name[:-1].upper() if name.endswith("s") else name.upper()
    return "COMPONENT"


def scan_permissions(root: Path, index: JavaIndex, commands: list[dict[str, Any]], subcommands: list[dict[str, Any]], manifest: dict[str, Any]) -> tuple[list[dict[str, Any]], list[Finding]]:
    nodes: dict[str, dict[str, Any]] = {}
    findings: list[Finding] = []
    constant_names: dict[str, set[str]] = {}

    for src in index.sources:
        constants = {**index.constants, **src.constants}
        for name, value in src.constants.items():
            if "PERMISSION" in name:
                constant_names.setdefault(value, set()).add(name)
        for offset, call in scan_calls(src.source, "hasPermission"):
            expression = split_top_level(call)[0] if call else ""
            node = resolve_java_string(expression, constants)
            if not node:
                findings.append(Finding("REVIEW_REQUIRED", "PERMISSION_UNRESOLVED",
                                        f"Permission expression could not be resolved: {expression}", "",
                                        (Evidence(src.relative, line_number(src.source, offset), "hasPermission"),)))
                continue
            item = nodes.setdefault(node, {
                "id": f"permission.{node}", "node": node, "sources": [], "commands": [], "guis": [], "listeners": [],
                "audience": "ADMIN" if any(x in node for x in ("admin", "moder", "staff", "bypass")) else "PLAYER",
                "default": "UNKNOWN", "documentation": [], "confidence": "HIGH",
            })
            evidence = {"source": src.relative, "line": line_number(src.source, offset), "symbol": "hasPermission", "kind": _kind(src.relative)}
            item["sources"].append(evidence)
            if evidence["kind"] == "GUI": item["guis"].append(src.class_name)
            if evidence["kind"] == "LISTENER": item["listeners"].append(src.class_name)

    for command in [*commands, *subcommands]:
        for node in command.get("permission", []):
            item = nodes.setdefault(node, {
                "id": f"permission.{node}", "node": node, "sources": [], "commands": [], "guis": [], "listeners": [],
                "audience": "ADMIN" if "admin" in node or "moder" in node else "PLAYER", "default": "UNKNOWN",
                "documentation": [], "confidence": "HIGH",
            })
            item["commands"].append(command["id"])

    for node, names in constant_names.items():
        if len(names) > 1:
            findings.append(Finding("WARN", "DUPLICATE_PERMISSION_CONSTANT",
                                    f"Permission {node} is declared by multiple constants: {', '.join(sorted(names))}",
                                    f"permission.{node}"))
        if node not in nodes:
            findings.append(Finding("WARN", "UNUSED_PERMISSION", f"Permission constant {node} has no detected usage.", f"permission.{node}"))
            nodes[node] = {"id": f"permission.{node}", "node": node, "sources": [], "commands": [], "guis": [], "listeners": [],
                           "audience": "ADMIN" if "admin" in node else "PLAYER", "default": "UNKNOWN", "documentation": [], "confidence": "MEDIUM"}

    docs = manifest.get("permissions", {})
    for node, item in nodes.items():
        entry = docs.get(item["id"], {})
        if isinstance(entry, dict): item["documentation"] = entry.get("docs", [])
        for field in ("sources", "commands", "guis", "listeners"):
            if field == "sources":
                item[field] = sorted({(x["source"], x["line"], x["symbol"], x["kind"]) for x in item[field]})
                item[field] = [{"source": x[0], "line": x[1], "symbol": x[2], "kind": x[3]} for x in item[field]]
            else:
                item[field] = sorted(set(item[field]))
    return sorted(nodes.values(), key=lambda item: item["node"]), findings
