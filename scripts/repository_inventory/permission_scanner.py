from __future__ import annotations

import hashlib
import json
import re
from pathlib import Path
from typing import Any

from .java_scanner import JavaIndex, JavaSource
from .models import Evidence, Finding
from .util import (
    find_matching,
    flatten_yaml_paths,
    java_strings,
    java_without_comments,
    line_number,
    read_text,
    scan_calls,
    split_top_level,
)


_PERMISSION_NODE = re.compile(r"^icesmp\.[a-z0-9_.-]+$")
_JAVA_NAME = re.compile(r"^[A-Za-z_$][A-Za-z0-9_$.]*$")
_METHOD_KEYWORDS = {"if", "for", "while", "switch", "catch", "synchronized", "new"}


def _kind(relative: str) -> str:
    for name in ("commands", "gui", "listeners", "managers", "integration"):
        if f"/{name}/" in f"/{relative}":
            return name[:-1].upper() if name.endswith("s") else name.upper()
    return "COMPONENT"


def _permission_values(expression: str, constants: dict[str, str]) -> set[str]:
    """Resolve only literal/constant permission values present in an expression.

    This deliberately does not guess arbitrary String values. Compound Java
    expressions (ternaries and switch arms) may contain more than one exact
    permission constant, so the result is a set.
    """
    values: set[str] = set()
    stripped = expression.strip()
    if not stripped:
        return values

    if _JAVA_NAME.fullmatch(stripped):
        value = constants.get(stripped) or constants.get(stripped.split(".")[-1])
        if value and _PERMISSION_NODE.fullmatch(value):
            values.add(value)
        return values

    for value in java_strings(stripped):
        if _PERMISSION_NODE.fullmatch(value):
            values.add(value)
    for token in re.findall(r"[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*", stripped):
        value = constants.get(token) or constants.get(token.split(".")[-1])
        if value and _PERMISSION_NODE.fullmatch(value):
            values.add(value)
    return values


def _constant_table(index: JavaIndex) -> dict[str, str]:
    """Build a fixed-point table that also understands String constant aliases."""
    constants = dict(index.constants)
    assignments: list[tuple[JavaSource, str, str]] = []
    pattern = re.compile(
        r"\b(?:public|protected|private)?\s*(?:static\s+)?(?:final\s+)?"
        r"String\s+([A-Z][A-Z0-9_]*)\s*=\s*([^;]+);",
        re.DOTALL,
    )
    for src in index.sources:
        for match in pattern.finditer(java_without_comments(src.source)):
            assignments.append((src, match.group(1), match.group(2)))

    changed = True
    while changed:
        changed = False
        for src, name, expression in assignments:
            values = _permission_values(expression, constants)
            if len(values) != 1:
                continue
            value = next(iter(values))
            keys = [name, f"{src.class_name}.{name}"]
            if src.package:
                keys.append(f"{src.package}.{src.class_name}.{name}")
            for key in keys:
                if key not in constants:
                    constants[key] = value
                    changed = True
    return constants


def _constants_for_source(constants: dict[str, str], src: JavaSource) -> dict[str, str]:
    """Prefer the current class' constants over same-named constants elsewhere."""
    contextual = dict(constants)
    prefixes = [f"{src.class_name}."]
    if src.package:
        prefixes.insert(0, f"{src.package}.{src.class_name}.")
    for prefix in prefixes:
        for key, value in constants.items():
            if key.startswith(prefix):
                contextual[key.removeprefix(prefix)] = value
    contextual.update(src.constants)
    return contextual


def _source_evidence(src: JavaSource, offset: int, symbol: str, detail: str = "") -> dict[str, Any]:
    return {
        "source": src.relative,
        "line": line_number(src.source, offset),
        "symbol": symbol,
        "kind": _kind(src.relative),
        "detail": detail,
    }


def _new_item(node: str, default: str = "UNKNOWN", confidence: str = "HIGH") -> dict[str, Any]:
    lower = node.lower()
    if "moderation" in lower:
        audience = "MODERATOR"
    elif "admin" in lower or "builder" in lower:
        audience = "ADMIN"
    else:
        audience = "PLAYER"
    return {
        "id": f"permission.{node}",
        "node": node,
        "sources": [],
        "resolution_evidence": [],
        "commands": [],
        "guis": [],
        "listeners": [],
        "audience": audience,
        "default": default,
        "documentation": [],
        "confidence": confidence,
        "registered": False,
        "legacy_alias": False,
        "children": [],
    }


def _ensure_node(nodes: dict[str, dict[str, Any]], node: str,
                 default: str = "UNKNOWN", confidence: str = "HIGH") -> dict[str, Any]:
    item = nodes.setdefault(node, _new_item(node, default, confidence))
    if item["default"] == "UNKNOWN" and default != "UNKNOWN":
        item["default"] = default
    if confidence == "HIGH":
        item["confidence"] = "HIGH"
    return item


def _permissions_source(index: JavaIndex) -> JavaSource | None:
    candidates = [
        src for src in index.sources
        if src.class_name == "Permissions" and "registerCratePermissions" in src.source
    ]
    return candidates[0] if len(candidates) == 1 else None


def _registered_static_permissions(
    index: JavaIndex,
    constants: dict[str, str],
    nodes: dict[str, dict[str, Any]],
) -> list[Finding]:
    """Inventory the canonical registry, including loop maps and legacy aliases."""
    findings: list[Finding] = []
    src = _permissions_source(index)
    if src is None:
        return findings
    clean = java_without_comments(src.source)
    source_constants = _constants_for_source(constants, src)

    # Canonical/moderation maps are registered by the entrySet loops. Other
    # maps (children) use dynamic entry keys and therefore do not match here.
    put_pattern = re.compile(r"\b([A-Za-z_$][A-Za-z0-9_$]*)\.put\s*\(\s*([^,]+),")
    for match in put_pattern.finditer(clean):
        values = _permission_values(match.group(2), source_constants)
        for node in values:
            item = _ensure_node(nodes, node, "OP")
            item["registered"] = True
            item["sources"].append(_source_evidence(
                src, match.start(), "Permissions.register",
                f"{match.group(1)}.put -> PermissionDefault.OP",
            ))

    # Direct Permission constructors cover TRUE player nodes, parent nodes and
    # the canonical maps' loop body. Dynamic loop variables are handled by the
    # map/config passes instead of being guessed here.
    expected_dynamic = {"entry.getKey()", "legacyNode", "node"}
    for offset, call in scan_calls(src.source, "Permission"):
        parts = split_top_level(call)
        if not parts:
            continue
        expression = parts[0].strip()
        values = _permission_values(expression, source_constants)
        default_match = re.search(r"\bPermissionDefault\.([A-Z_]+)\b", call)
        default = default_match.group(1) if default_match else "UNKNOWN"
        if values:
            for node in values:
                item = _ensure_node(nodes, node, default)
                item["registered"] = True
                item["sources"].append(_source_evidence(
                    src, offset, "Permissions.register", f"PermissionDefault.{default}",
                ))
        elif expression not in expected_dynamic and not expression.startswith("entry."):
            findings.append(Finding(
                "FAIL",
                "PERMISSION_REGISTRATION_UNRESOLVED",
                f"Registered permission expression could not be resolved: {expression}",
                "",
                (Evidence(src.relative, line_number(src.source, offset), "new Permission"),),
            ))

    # alias("legacy", CANONICAL...) registrations are real OP nodes with
    # canonical children, not prose-only compatibility notes.
    for offset, call in scan_calls(src.source, "alias"):
        parts = split_top_level(call)
        if not parts:
            continue
        literals = java_strings(parts[0])
        if not literals or not _PERMISSION_NODE.fullmatch(literals[0]):
            continue
        node = literals[0]
        children: set[str] = set()
        for expression in parts[1:]:
            children.update(_permission_values(expression, source_constants))
        item = _ensure_node(nodes, node, "OP")
        item["registered"] = True
        item["legacy_alias"] = True
        item["children"].extend(children)
        item["sources"].append(_source_evidence(
            src, offset, "Permissions.alias",
            "legacy alias -> " + ", ".join(sorted(children)),
        ))
    return findings


def _crate_config_permissions(root: Path, index: JavaIndex) -> list[tuple[str, dict[str, Any]]]:
    """Return validated config-driven nodes only when runtime registration is wired."""
    if not any("registerCratePermissions" in src.source for src in index.sources):
        return []
    path = root / "src/main/resources/config/crates.yml"
    if not path.is_file():
        return []
    flattened = flatten_yaml_paths(path)
    relative = path.relative_to(root).as_posix()
    raw_lines = read_text(path).splitlines()
    result: list[tuple[str, dict[str, Any]]] = []
    for config_path, value in sorted(flattened.items()):
        if not re.fullmatch(r"crates\.[^.]+\.permission", config_path):
            continue
        if not isinstance(value, str) or not value:
            continue
        if not _PERMISSION_NODE.fullmatch(value):
            continue
        line = next(
            (
                number for number, raw in enumerate(raw_lines, 1)
                if re.match(r"^\s*permission\s*:", raw)
                and value in raw
            ),
            1,
        )
        result.append((value, {
            "source": relative,
            "line": line,
            "symbol": config_path,
            "kind": "CONFIG",
            "detail": "Permissions.registerCratePermissions",
        }))
    return result


def _method_declarations(src: JavaSource) -> list[dict[str, Any]]:
    clean = java_without_comments(src.source)
    pattern = re.compile(
        r"\b([A-Za-z_$][A-Za-z0-9_$]*)\s*\(([^;{}]*)\)\s*"
        r"(?:throws\s+[^{]+)?\{",
        re.DOTALL,
    )
    declarations: list[dict[str, Any]] = []
    for match in pattern.finditer(clean):
        name = match.group(1)
        if name in _METHOD_KEYWORDS:
            continue
        opening = clean.find("{", match.start())
        closing = find_matching(src.source, opening, "{", "}")
        if closing < 0:
            continue
        parameters: list[str] = []
        for parameter in split_top_level(match.group(2)):
            names = re.findall(r"[A-Za-z_$][A-Za-z0-9_$]*", parameter)
            parameters.append(names[-1] if names else "")
        declarations.append({
            "name": name,
            "parameters": parameters,
            "start": match.start(),
            "opening": opening,
            "closing": closing,
        })
    return declarations


def _enclosing_method(src: JavaSource, offset: int) -> dict[str, Any] | None:
    matches = [
        declaration for declaration in _method_declarations(src)
        if declaration["opening"] < offset < declaration["closing"]
    ]
    return max(matches, key=lambda item: item["opening"]) if matches else None


def _new_constructor_arguments(index: JavaIndex, class_name: str) -> list[tuple[JavaSource, int, list[str]]]:
    pattern = re.compile(
        r"\bnew\s+(?:[A-Za-z_$][A-Za-z0-9_$]*\.)*"
        + re.escape(class_name)
        + r"\s*\("
    )
    result: list[tuple[JavaSource, int, list[str]]] = []
    for caller in index.sources:
        clean = java_without_comments(caller.source)
        for match in pattern.finditer(clean):
            opening = clean.find("(", match.start())
            closing = find_matching(caller.source, opening)
            if closing >= 0:
                result.append((
                    caller,
                    match.start(),
                    split_top_level(caller.source[opening + 1:closing]),
                ))
    return result


def _method_call_arguments(src: JavaSource, method_name: str) -> list[tuple[int, list[str]]]:
    declarations = {(item["start"], item["opening"]) for item in _method_declarations(src) if item["name"] == method_name}
    result: list[tuple[int, list[str]]] = []
    for offset, call in scan_calls(src.source, method_name):
        # A declaration is also syntactically token(...); exclude it.
        if any(start <= offset <= opening for start, opening in declarations):
            continue
        result.append((offset, split_top_level(call)))
    return result


def _method_return_permissions(src: JavaSource, method_name: str,
                               constants: dict[str, str]) -> tuple[set[str], list[dict[str, Any]]]:
    values: set[str] = set()
    evidence: list[dict[str, Any]] = []
    for declaration in _method_declarations(src):
        if declaration["name"] != method_name:
            continue
        body = src.source[declaration["opening"] + 1:declaration["closing"]]
        body_offset = declaration["opening"] + 1
        expressions: list[tuple[int, str]] = []
        for match in re.finditer(r"->\s*([^,;}]+)", body):
            expressions.append((body_offset + match.start(), match.group(1)))
        for match in re.finditer(r"\breturn\s+([^;]+);", body):
            expressions.append((body_offset + match.start(), match.group(1)))
        for offset, expression in expressions:
            resolved = _permission_values(expression, constants)
            if resolved:
                values.update(resolved)
                evidence.append(_source_evidence(
                    src, offset, method_name, f"return -> {', '.join(sorted(resolved))}",
                ))
    return values, evidence


def _resolve_rhs(
    src: JavaSource,
    expression: str,
    constants: dict[str, str],
) -> tuple[set[str], list[dict[str, Any]]]:
    values = _permission_values(expression, constants)
    if values:
        return values, []
    call_match = re.fullmatch(
        r"(?:this\.)?([A-Za-z_$][A-Za-z0-9_$]*)\s*\([^)]*\)",
        expression.strip(),
        re.DOTALL,
    )
    if call_match:
        return _method_return_permissions(src, call_match.group(1), constants)
    return set(), []


def _resolve_generic_expression(
    root: Path,
    index: JavaIndex,
    src: JavaSource,
    offset: int,
    expression: str,
    constants: dict[str, str],
    crate_permissions: list[tuple[str, dict[str, Any]]],
) -> tuple[set[str], list[dict[str, Any]]]:
    source_constants = _constants_for_source(constants, src)
    direct = _permission_values(expression, source_constants)
    if direct:
        return direct, []
    name = expression.strip()

    # The crate access gate is a validated config value registered by
    # Permissions.registerCratePermissions during every config snapshot swap.
    if name == "definition.permission()" and "registerCratePermissions" in src.source:
        if crate_permissions:
            return {node for node, _ in crate_permissions}, [item for _, item in crate_permissions]
        return set(), [_source_evidence(
            src, offset, "hasPermission", "optional config permission set is empty",
        )]

    if not re.fullmatch(r"[A-Za-z_$][A-Za-z0-9_$]*", name):
        return set(), []

    # Nearest local String assignment (e.g. a ternary or a helper switch).
    assignment_pattern = re.compile(
        r"\b(?:final\s+)?String\s+" + re.escape(name) + r"\s*=\s*(.*?);",
        re.DOTALL,
    )
    assignments = [match for match in assignment_pattern.finditer(src.source, 0, offset)]
    if assignments:
        match = assignments[-1]
        values, evidence = _resolve_rhs(src, match.group(1), source_constants)
        if values:
            evidence.append(_source_evidence(
                src, match.start(), name, f"local assignment -> {', '.join(sorted(values))}",
            ))
            return values, evidence

    # Method parameter flow (e.g. ModerationGUI.put(..., permission, ...)).
    enclosing = _enclosing_method(src, offset)
    if enclosing and name in enclosing["parameters"]:
        parameter_index = enclosing["parameters"].index(name)
        values: set[str] = set()
        evidence: list[dict[str, Any]] = []
        for call_offset, arguments in _method_call_arguments(src, enclosing["name"]):
            if parameter_index >= len(arguments):
                continue
            resolved, nested = _resolve_rhs(src, arguments[parameter_index], source_constants)
            if resolved:
                values.update(resolved)
                evidence.extend(nested)
                evidence.append(_source_evidence(
                    src, call_offset, enclosing["name"],
                    f"argument {parameter_index + 1} -> {', '.join(sorted(resolved))}",
                ))
        if values:
            return values, evidence

    # Constructor-to-field flow used by the reusable moderation commands.
    assignment = re.search(
        r"\bthis\." + re.escape(name)
        + r"\s*=\s*([A-Za-z_$][A-Za-z0-9_$]*)\s*;",
        src.source,
    )
    if assignment:
        constructor_parameter = assignment.group(1)
        constructors = [
            declaration for declaration in _method_declarations(src)
            if declaration["name"] == src.class_name
            and constructor_parameter in declaration["parameters"]
        ]
        if len(constructors) == 1:
            parameter_index = constructors[0]["parameters"].index(constructor_parameter)
            values: set[str] = set()
            evidence: list[dict[str, Any]] = []
            for caller, call_offset, arguments in _new_constructor_arguments(index, src.class_name):
                if parameter_index >= len(arguments):
                    continue
                resolved, nested = _resolve_rhs(
                    caller,
                    arguments[parameter_index],
                    _constants_for_source(constants, caller),
                )
                if resolved:
                    values.update(resolved)
                    evidence.extend(nested)
                    evidence.append(_source_evidence(
                        caller, call_offset, f"new {src.class_name}",
                        f"constructor argument {parameter_index + 1} -> {', '.join(sorted(resolved))}",
                    ))
            if values:
                return values, evidence
    return set(), []


def _permission_contract(
    root: Path,
    manifest: dict[str, Any],
    nodes: dict[str, dict[str, Any]],
) -> list[Finding]:
    """Validate an exact, source-hash-protected permission manifest contract."""
    section = manifest.get("permissions", {})
    policy = section.get("_contract") if isinstance(section, dict) else None
    if not isinstance(policy, dict):
        return []

    findings: list[Finding] = []
    entries = {
        stable_id: entry
        for stable_id, entry in section.items()
        if not stable_id.startswith("_") and isinstance(entry, dict)
    }
    actual = {item["id"]: item for item in nodes.values()}

    entries_hash = hashlib.sha256(
        json.dumps(entries, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    ).hexdigest()
    if policy.get("entries_sha256") != entries_hash:
        findings.append(Finding(
            "FAIL",
            "PERMISSION_CONTRACT_ENTRY_DRIFT",
            "Permission contract entries changed without refreshing the contract digest.",
            "permissions._contract",
        ))

    for stable_id in sorted(set(actual) - set(entries)):
        findings.append(Finding(
            "FAIL",
            "PERMISSION_CONTRACT_NODE_MISSING",
            f"Resolved permission {stable_id} is absent from the exact permission contract.",
            stable_id,
        ))
    for stable_id in sorted(set(entries) - set(actual)):
        findings.append(Finding(
            "FAIL",
            "PERMISSION_CONTRACT_NODE_STALE",
            f"Contract permission {stable_id} is no longer resolved from the source/config.",
            stable_id,
        ))

    for stable_id in sorted(set(actual) & set(entries)):
        item = actual[stable_id]
        entry = entries[stable_id]
        config_dynamic = any(source["kind"] == "CONFIG" for source in item["sources"])
        detected = {
            "kind": "permission",
            "node": item["node"],
            "default": item["default"],
            "registration": "CONFIG_DYNAMIC" if config_dynamic else (
                "STATIC" if item["registered"] else "USE_ONLY"
            ),
            "legacy_alias": item["legacy_alias"],
            "children": sorted(item["children"]),
        }
        declared = {
            "kind": str(entry.get("kind", "")),
            "node": str(entry.get("node", "")),
            "default": str(entry.get("default", "")),
            "registration": str(entry.get("registration", "")),
            "legacy_alias": bool(entry.get("legacy_alias", False)),
            "children": sorted(str(child) for child in entry.get("children", [])),
        }
        if declared != detected:
            findings.append(Finding(
                "FAIL",
                "PERMISSION_CONTRACT_NODE_DRIFT",
                f"{stable_id} differs from its exact permission contract: "
                f"declared {declared}, detected {detected}.",
                stable_id,
            ))

    configured = {
        item["id"] for item in actual.values()
        if any(source["kind"] == "CONFIG" for source in item["sources"])
    }
    static = {
        item["id"] for item in actual.values()
        if item["registered"] and item["id"] not in configured
    }
    expected_counts = policy.get("expected_counts", {})
    actual_counts = {
        "total": len(actual),
        "static_registered": len(static),
        "config_dynamic": len(configured),
    }
    for key, count in actual_counts.items():
        if expected_counts.get(key) != count:
            findings.append(Finding(
                "FAIL",
                "PERMISSION_CONTRACT_COUNT_DRIFT",
                f"Permission contract expects {key}={expected_counts.get(key)!r}, resolved {count}.",
                "permissions._contract",
            ))

    required_sources = {
        evidence["source"]
        for item in actual.values()
        for field in ("sources", "resolution_evidence")
        for evidence in item[field]
        if str(evidence.get("source", "")).startswith("src/")
    }
    source_hashes = policy.get("source_sha256", {})
    if not isinstance(source_hashes, dict):
        source_hashes = {}
        findings.append(Finding(
            "FAIL",
            "PERMISSION_CONTRACT_SOURCE_POLICY_MISSING",
            "Exact permission contract has no source_sha256 map.",
            "permissions._contract",
        ))
    for relative in sorted(required_sources - set(source_hashes)):
        findings.append(Finding(
            "FAIL",
            "PERMISSION_CONTRACT_SOURCE_MISSING",
            f"Permission source {relative} is not protected by the exact contract.",
            "permissions._contract",
            (Evidence(relative, 1, "source_sha256"),),
        ))
    for relative in sorted(set(source_hashes) - required_sources):
        findings.append(Finding(
            "FAIL",
            "PERMISSION_CONTRACT_SOURCE_STALE",
            f"Permission contract hashes source {relative}, but no permission evidence uses it.",
            "permissions._contract",
            (Evidence(relative, 1, "source_sha256"),),
        ))
    for relative, expected_hash in sorted(source_hashes.items()):
        path = root / relative
        if not path.is_file():
            findings.append(Finding(
                "FAIL",
                "PERMISSION_CONTRACT_SOURCE_NOT_FOUND",
                f"Permission contract source does not exist: {relative}.",
                "permissions._contract",
                (Evidence(relative, 1, "source_sha256"),),
            ))
            continue
        actual_hash = hashlib.sha256(path.read_bytes()).hexdigest()
        if actual_hash != expected_hash:
            findings.append(Finding(
                "FAIL",
                "PERMISSION_CONTRACT_SOURCE_DRIFT",
                f"Permission source {relative} changed without an exact contract update.",
                "permissions._contract",
                (Evidence(relative, 1, f"expected {expected_hash}; actual {actual_hash}"),),
            ))
    return findings


def scan_permissions(
    root: Path,
    index: JavaIndex,
    commands: list[dict[str, Any]],
    subcommands: list[dict[str, Any]],
    manifest: dict[str, Any],
) -> tuple[list[dict[str, Any]], list[Finding]]:
    nodes: dict[str, dict[str, Any]] = {}
    findings: list[Finding] = []
    constants = _constant_table(index)

    findings.extend(_registered_static_permissions(index, constants, nodes))
    crate_permissions = _crate_config_permissions(root, index)
    for node, evidence in crate_permissions:
        item = _ensure_node(nodes, node, "FALSE")
        item["registered"] = True
        item["sources"].append(evidence)

    for src in index.sources:
        for offset, call in scan_calls(src.source, "hasPermission"):
            expression = split_top_level(call)[0] if call else ""
            resolved, resolution_evidence = _resolve_generic_expression(
                root, index, src, offset, expression, constants, crate_permissions,
            )
            if not resolved:
                if resolution_evidence:
                    continue
                findings.append(Finding(
                    "FAIL",
                    "PERMISSION_UNRESOLVED",
                    f"Permission expression could not be resolved: {expression}",
                    "",
                    (Evidence(src.relative, line_number(src.source, offset), "hasPermission"),),
                ))
                continue
            for node in resolved:
                item = _ensure_node(nodes, node)
                item["sources"].append(_source_evidence(
                    src, offset, "hasPermission",
                    f"{expression.strip()} -> {node}",
                ))
                item["resolution_evidence"].extend(resolution_evidence)
                kind = _kind(src.relative)
                if kind == "GUI":
                    item["guis"].append(src.class_name)
                if kind == "LISTENER":
                    item["listeners"].append(src.class_name)

    for command in [*commands, *subcommands]:
        for node in command.get("permission", []):
            if not isinstance(node, str) or not _PERMISSION_NODE.fullmatch(node):
                continue
            item = _ensure_node(nodes, node)
            item["commands"].append(command["id"])

    findings.extend(_permission_contract(root, manifest, nodes))

    docs = manifest.get("permissions", {})
    for node, item in nodes.items():
        entry = docs.get(item["id"], {})
        if isinstance(entry, dict):
            item["documentation"] = entry.get("docs", [])
        item["children"] = sorted(set(item["children"]))
        for field in ("sources", "resolution_evidence"):
            unique = {
                (
                    evidence["source"],
                    evidence["line"],
                    evidence["symbol"],
                    evidence["kind"],
                    evidence.get("detail", ""),
                )
                for evidence in item[field]
            }
            item[field] = [
                {
                    "source": source,
                    "line": line,
                    "symbol": symbol,
                    "kind": kind,
                    "detail": detail,
                }
                for source, line, symbol, kind, detail in sorted(unique)
            ]
        for field in ("commands", "guis", "listeners"):
            item[field] = sorted(set(item[field]))
    return sorted(nodes.values(), key=lambda item: item["node"]), findings
