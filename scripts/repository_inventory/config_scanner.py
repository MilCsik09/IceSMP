from __future__ import annotations

import re
from pathlib import Path
from typing import Any

from .java_scanner import JavaIndex
from .models import Evidence, Finding
from .util import flatten_yaml_paths, iter_files, java_strings, line_number, nearest_method, posix, read_text

READ_PATTERN = re.compile(
    r"(?P<receiver>(?:[A-Za-z_$][A-Za-z0-9_$]*\s*\.\s*)?getConfig\(\)\s*\.|[A-Za-z_$][A-Za-z0-9_$]*\s*\.)"
    r"(?P<method>getBoolean|getString|getInt|getLong|getDouble|getStringList|getIntegerList|getConfigurationSection|contains)"
    r"\s*\(\s*(?P<key>\"(?:\\.|[^\"\\])*\")(?:\s*,\s*(?P<fallback>[^\)]+))?\)", re.DOTALL)

TYPE_MAP = {"getBoolean": "BOOLEAN", "getString": "STRING", "getInt": "INTEGER", "getLong": "LONG",
            "getDouble": "DOUBLE", "getStringList": "STRING_LIST", "getIntegerList": "INTEGER_LIST",
            "getConfigurationSection": "SECTION", "contains": "UNKNOWN"}


def scan_config(root: Path, index: JavaIndex, manifest: dict[str, Any]) -> tuple[list[dict[str, Any]], list[Finding]]:
    defaults: dict[str, list[dict[str, Any]]] = {}
    findings: list[Finding] = []
    for path in sorted(iter_files(root / "src/main/resources" if (root / "src/main/resources").exists() else root, ("*.yml", "*.yaml"))):
        relative = posix(path, root)
        if "message" in path.name.lower() or path.name.lower() in ("paper-plugin.yml", "plugin.yml"):
            continue
        for key, value in flatten_yaml_paths(path).items():
            defaults.setdefault(key, []).append({"file": relative, "value": value})

    reads: dict[str, list[dict[str, Any]]] = {}
    types: dict[str, set[str]] = {}
    fallbacks: dict[str, set[str]] = {}
    for src in index.sources:
        clean = src.source
        config_variables = {"configManager", "config", "yaml", "section", "settings"}
        for declaration in re.finditer(
                r"\b(?:YamlConfiguration|FileConfiguration|ConfigurationSection|ConfigManager)\s+([A-Za-z_$][A-Za-z0-9_$]*)",
                clean):
            config_variables.add(declaration.group(1))
        for match in READ_PATTERN.finditer(clean):
            receiver = re.sub(r"\s+", "", match.group("receiver"))
            receiver_name = receiver.split(".", 1)[0]
            receiver_lower = receiver.lower()
            if "getconfig()" not in receiver_lower and receiver_name not in config_variables \
                    and not any(hint in receiver_lower for hint in ("config", "yaml", "section", "settings")):
                continue
            strings = java_strings(match.group("key"))
            if not strings:
                continue
            key = strings[0]
            method = match.group("method")
            reader = {"source": src.relative, "line": line_number(src.source, match.start()),
                      "symbol": nearest_method(src.source, match.start()), "method": method}
            reads.setdefault(key, []).append(reader)
            types.setdefault(key, set()).add(TYPE_MAP[method])
            if match.group("fallback"):
                fallbacks.setdefault(key, set()).add(match.group("fallback").strip())

    keys: list[dict[str, Any]] = []
    for key in sorted(set(defaults) | set(reads)):
        default_entries = defaults.get(key, [])
        readers = reads.get(key, [])
        type_values = sorted(types.get(key, {"UNKNOWN"}))
        if readers and not default_entries:
            findings.append(Finding("FAIL", "CONFIG_KEY_MISSING_DEFAULT",
                                    f"Code reads config key '{key}', but no default resource key was found.", f"config.{key}",
                                    tuple(Evidence(x["source"], x["line"], x["symbol"]) for x in readers[:3])))
        if len(type_values) > 1:
            findings.append(Finding("FAIL", "CONFIG_TYPE_MISMATCH",
                                    f"Config key '{key}' is read as incompatible types: {', '.join(type_values)}", f"config.{key}"))
        if default_entries and not readers:
            findings.append(Finding("WARN", "CONFIG_KEY_NOT_READ",
                                    f"Default config key '{key}' has no statically detected production reader; classify data-only/dynamic use if intentional.",
                                    f"config.{key}", (Evidence(default_entries[0]["file"], 1, key),)))
        lifecycle = "UNKNOWN"
        symbols = " ".join(item["symbol"].lower() for item in readers)
        if "reload" in symbols: lifecycle = "RELOAD"
        elif any(x in symbols for x in ("load", "enable", "constructor")): lifecycle = "STARTUP"
        section = key.split(".")[0]
        docs_entry = manifest.get("config-sections", {}).get(f"config.{section}", {})
        keys.append({
            "id": f"config.{key}", "path": key, "section": section,
            "source_configs": default_entries, "default": default_entries[0]["value"] if default_entries else None,
            "code_fallbacks": sorted(fallbacks.get(key, set())), "types": type_values,
            "readers": sorted(readers, key=lambda item: (item["source"], item["line"])),
            "feature": f"feature.{section.replace('_', '-').replace('.', '-')}", "lifecycle": lifecycle,
            "documentation": docs_entry.get("docs", []) if isinstance(docs_entry, dict) else [],
            "confidence": "HIGH" if readers and default_entries and len(type_values) == 1 else "MEDIUM",
        })
    return keys, findings
