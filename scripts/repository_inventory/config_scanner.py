from __future__ import annotations

import re
from pathlib import Path
from typing import Any

from .java_scanner import JavaIndex
from .models import Evidence, Finding
from .util import flatten_yaml_paths, iter_files, java_strings, kebab, line_number, nearest_method, posix

READ_PATTERN = re.compile(
    r"(?P<receiver>(?:[A-Za-z_$][A-Za-z0-9_$]*\s*\.\s*)?getConfig\(\)\s*\.|[A-Za-z_$][A-Za-z0-9_$]*\s*\.)"
    r"(?P<method>getBoolean|getString|getInt|getLong|getDouble|getStringList|getIntegerList|getConfigurationSection|contains)"
    r"\s*\(\s*(?P<key>\"(?:\\.|[^\"\\])*\")(?:\s*,\s*(?P<fallback>[^\)]+))?\)", re.DOTALL)

TYPE_MAP = {"getBoolean": "BOOLEAN", "getString": "STRING", "getInt": "INTEGER", "getLong": "LONG",
            "getDouble": "DOUBLE", "getStringList": "STRING_LIST", "getIntegerList": "INTEGER_LIST",
            "getConfigurationSection": "SECTION", "contains": "UNKNOWN"}


def _receiver_context(receiver: str) -> tuple[str, str] | None:
    normalized = re.sub(r"\s+", "", receiver)
    lower = normalized.lower()
    name = normalized.split(".", 1)[0]
    if "getconfig()" in lower or "configmanager" in lower or name.lower() in {"pluginconfig", "mainconfig"}:
        return "PRIMARY", name
    if name.lower() in {"config", "section", "settings", "configuration"}:
        return "DYNAMIC_SECTION", name
    return None


def scan_config(root: Path, index: JavaIndex, manifest: dict[str, Any]) -> tuple[list[dict[str, Any]], list[Finding]]:
    defaults: dict[str, list[dict[str, Any]]] = {}
    findings: list[Finding] = []
    resources = root / "src/main/resources"
    for path in sorted(iter_files(resources if resources.exists() else root, ("*.yml", "*.yaml"))):
        relative = posix(path, root)
        if "/messages/" in f"/{relative}" or path.name.lower() in ("messages.yml", "paper-plugin.yml", "plugin.yml"):
            continue
        for key, value in flatten_yaml_paths(path).items():
            defaults.setdefault(key, []).append({"file": relative, "value": value})

    reads: dict[str, list[dict[str, Any]]] = {}
    display_paths: dict[str, str] = {}
    contexts: dict[str, str] = {}
    types: dict[str, set[str]] = {}
    fallbacks: dict[str, set[str]] = {}
    for src in index.sources:
        for match in READ_PATTERN.finditer(src.source):
            context = _receiver_context(match.group("receiver"))
            if not context:
                continue
            context_kind, receiver_name = context
            strings = java_strings(match.group("key"))
            if not strings:
                continue
            key = strings[0]
            if context_kind == "PRIMARY":
                inventory_path = key
            else:
                inventory_path = f"dynamic.{kebab(src.class_name)}.{kebab(receiver_name)}.{key}"
            reader = {"source": src.relative, "line": line_number(src.source, match.start()),
                      "symbol": nearest_method(src.source, match.start()), "method": match.group("method"),
                      "receiver": re.sub(r"\s+", "", match.group("receiver")), "context": context_kind}
            reads.setdefault(inventory_path, []).append(reader)
            display_paths[inventory_path] = key
            contexts[inventory_path] = context_kind
            types.setdefault(inventory_path, set()).add(TYPE_MAP[match.group("method")])
            if match.group("fallback"):
                fallbacks.setdefault(inventory_path, set()).add(match.group("fallback").strip())

    keys: list[dict[str, Any]] = []
    for inventory_path in sorted(set(defaults) | set(reads)):
        context_kind = contexts.get(inventory_path, "DEFAULT_RESOURCE")
        key = display_paths.get(inventory_path, inventory_path)
        default_entries = defaults.get(inventory_path, []) if context_kind != "DYNAMIC_SECTION" else []
        readers = reads.get(inventory_path, [])
        type_values = sorted(types.get(inventory_path, {"UNKNOWN"}))
        if readers and not default_entries and context_kind == "PRIMARY":
            findings.append(Finding("FAIL", "CONFIG_KEY_MISSING_DEFAULT",
                                    f"Primary config key '{key}' is read but no default resource key was found.", f"config.{inventory_path}",
                                    tuple(Evidence(x["source"], x["line"], x["symbol"]) for x in readers[:3])))
        if readers and context_kind == "DYNAMIC_SECTION":
            findings.append(Finding("REVIEW_REQUIRED", "DYNAMIC_CONFIG_PATH",
                                    f"Config/data section key '{key}' has a dynamic parent and cannot be linked to one full YAML path statically.",
                                    f"config.{inventory_path}", tuple(Evidence(x["source"], x["line"], x["symbol"]) for x in readers[:3])))
        if len(type_values) > 1:
            severity = "FAIL" if context_kind == "PRIMARY" else "REVIEW_REQUIRED"
            findings.append(Finding(severity, "CONFIG_TYPE_MISMATCH",
                                    f"Config key '{key}' is read as incompatible types in the same context: {', '.join(type_values)}",
                                    f"config.{inventory_path}"))
        symbols = " ".join(item["symbol"].lower() for item in readers)
        lifecycle = "RELOAD" if "reload" in symbols else ("STARTUP" if any(x in symbols for x in ("load", "enable", "constructor")) else "UNKNOWN")
        section = inventory_path.split(".")[0]
        docs_entry = manifest.get("config-sections", {}).get(f"config.{section}", {})
        keys.append({
            "id": f"config.{inventory_path}", "path": inventory_path, "resolved_yaml_path": key if context_kind != "DYNAMIC_SECTION" else "",
            "section": section, "source_configs": default_entries,
            "default": default_entries[0]["value"] if default_entries else None,
            "code_fallbacks": sorted(fallbacks.get(inventory_path, set())), "types": type_values,
            "readers": sorted(readers, key=lambda item: (item["source"], item["line"])),
            "feature": f"feature.{section.replace('_', '-').replace('.', '-')}", "lifecycle": lifecycle,
            "classification": "DYNAMIC_DATA_SECTION" if context_kind == "DYNAMIC_SECTION" else ("PRIMARY_CONFIG" if readers else "DATA_DRIVEN_DEFAULT"),
            "documentation": docs_entry.get("docs", []) if isinstance(docs_entry, dict) else [],
            "confidence": "REVIEW_REQUIRED" if context_kind == "DYNAMIC_SECTION" else ("HIGH" if readers and default_entries and len(type_values) == 1 else "MEDIUM"),
        })
    return keys, findings
