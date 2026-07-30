from __future__ import annotations

import hashlib
import json
import re
from pathlib import Path
from typing import Any

from .java_scanner import JavaIndex, JavaSource
from .models import Evidence, Finding
from .util import (find_matching, java_strings, java_without_comments, kebab, line_number,
                   method_block, nearest_method, resolve_java_string, scan_calls,
                   split_top_level, stable_digest)


ADMIN_HINTS = ("admin", "moder", "staff", "manage", "reload", "debug", "bypass", "set", "give", "remove")


def route_stable_id(root: str, syntax: str) -> str:
    """Return the stable ID used by the exact command-interface contract."""
    tail = syntax.strip().removeprefix("/").strip()
    if tail == root:
        slug = "root"
    else:
        prefix = root + " "
        slug = kebab(tail[len(prefix):] if tail.startswith(prefix) else tail) or "route"
    return f"route.{root}.{slug}-{stable_digest(syntax.strip(), 10)}"


def routing_alias_stable_id(route_id: str, alias: str) -> str:
    """Return a collision-safe stable ID for an alias of one functional route."""
    route_part = route_id.removeprefix("route.")
    return f"route-alias.{route_part}.{kebab(alias) or 'alias'}-{stable_digest(alias, 8)}"


def root_alias_stable_id(root: str, alias: str) -> str:
    return f"alias.{root}.{kebab(alias) or 'alias'}"


def _permission_nodes(value: Any) -> list[str]:
    if isinstance(value, list):
        values = value
    else:
        values = [value]
    return sorted({
        node
        for item in values
        for node in re.findall(r"\bicesmp(?:\.[a-z0-9_-]+)+\b", str(item).lower())
    })


def _source_sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _command_contract(
    root_path: Path,
    manifest: dict[str, Any],
    commands: list[dict[str, Any]],
) -> tuple[list[dict[str, Any]], list[dict[str, Any]], list[dict[str, Any]], list[Finding]]:
    """Resolve the repository's complete command surface from an exact evidence contract.

    Java-only heuristics remain useful for repositories without a contract. IceSMP's
    nested/helper-based routing is instead represented by an exhaustive manifest
    whose command-source hashes make additions, removals and stale routes blocking.
    """
    section = manifest.get("commands", {})
    policy = section.get("_contract") if isinstance(section, dict) else None
    if not isinstance(policy, dict):
        return [], [], [], []

    findings: list[Finding] = []
    entries = {
        stable_id: entry
        for stable_id, entry in section.items()
        if not stable_id.startswith("_") and isinstance(entry, dict)
    }
    roots = {stable_id: entry for stable_id, entry in entries.items() if entry.get("kind") == "root"}
    root_aliases = {stable_id: entry for stable_id, entry in entries.items() if entry.get("kind") == "root-alias"}
    route_entries = {stable_id: entry for stable_id, entry in entries.items() if entry.get("kind") == "route"}
    route_aliases = {stable_id: entry for stable_id, entry in entries.items() if entry.get("kind") == "routing-alias"}
    unknown = sorted(set(entries) - set(roots) - set(root_aliases) - set(route_entries) - set(route_aliases))
    for stable_id in unknown:
        findings.append(Finding(
            "FAIL", "COMMAND_CONTRACT_KIND_UNKNOWN",
            f"Command manifest entry {stable_id} has an unknown or missing kind.",
            stable_id,
        ))

    declared_entries_hash = policy.get("entries_sha256")
    actual_entries_hash = hashlib.sha256(
        json.dumps(entries, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    ).hexdigest()
    if declared_entries_hash != actual_entries_hash:
        findings.append(Finding(
            "FAIL", "COMMAND_CONTRACT_ENTRY_DRIFT",
            "Command contract entries changed without refreshing the contract digest.",
            "commands._contract",
        ))

    actual_roots = {item["id"]: item for item in commands}
    for stable_id in sorted(set(actual_roots) - set(roots)):
        findings.append(Finding(
            "FAIL", "COMMAND_CONTRACT_ROOT_MISSING",
            f"Registered root command {stable_id} is absent from the exact command contract.",
            stable_id,
        ))
    for stable_id in sorted(set(roots) - set(actual_roots)):
        findings.append(Finding(
            "FAIL", "COMMAND_CONTRACT_ROOT_STALE",
            f"Contract root {stable_id} is no longer registered.",
            stable_id,
        ))

    root_alias_inventory: list[dict[str, Any]] = []
    for stable_id, command in sorted(actual_roots.items()):
        entry = roots.get(stable_id)
        if not entry:
            continue
        expected = {
            "name": command["name"],
            "implementation": command["implementation"],
            "aliases": sorted(command.get("aliases", [])),
            "registration_file": command["registration_file"],
            "registration_line": command["registration_line"],
        }
        declared = {
            "name": str(entry.get("name", "")),
            "implementation": str(entry.get("implementation", "")),
            "aliases": sorted(str(alias).lower() for alias in entry.get("aliases", [])),
            "registration_file": str(entry.get("registration_file", "")),
            "registration_line": int(entry.get("registration_line", 0)),
        }
        if declared != expected:
            findings.append(Finding(
                "FAIL", "COMMAND_CONTRACT_ROOT_DRIFT",
                f"{stable_id} registration differs from its command contract: expected {declared}, detected {expected}.",
                stable_id,
                tuple(Evidence(e["source"], e["line"], e["symbol"]) for e in command.get("source_evidence", [])),
            ))
        command["documentation"] = list(entry.get("docs", []))

        expected_alias_ids = {
            root_alias_stable_id(command["name"], alias): alias
            for alias in command.get("aliases", [])
        }
        declared_alias_ids = {
            alias_id: alias_entry
            for alias_id, alias_entry in root_aliases.items()
            if alias_entry.get("root") == command["name"]
        }
        for alias_id in sorted(set(expected_alias_ids) - set(declared_alias_ids)):
            findings.append(Finding(
                "FAIL", "COMMAND_CONTRACT_ROOT_ALIAS_MISSING",
                f"Registered alias /{expected_alias_ids[alias_id]} is absent from the exact command contract.",
                alias_id,
            ))
        for alias_id in sorted(set(declared_alias_ids) - set(expected_alias_ids)):
            findings.append(Finding(
                "FAIL", "COMMAND_CONTRACT_ROOT_ALIAS_STALE",
                f"Contract alias {alias_id} is no longer registered for /{command['name']}.",
                alias_id,
            ))
        for alias_id, alias in sorted(expected_alias_ids.items()):
            alias_entry = declared_alias_ids.get(alias_id)
            if not alias_entry:
                continue
            if str(alias_entry.get("alias", "")).lower() != alias:
                findings.append(Finding(
                    "FAIL", "COMMAND_CONTRACT_ROOT_ALIAS_DRIFT",
                    f"{alias_id} declares a different alias than /{alias}.",
                    alias_id,
                ))
            root_alias_inventory.append({
                "id": alias_id,
                "kind": "root-alias",
                "root": command["name"],
                "alias": alias,
                "path": alias,
                "documentation": list(alias_entry.get("docs", [])),
                "confidence": "HIGH",
                "source_evidence": list(command.get("source_evidence", [])),
            })

    unattached_root_aliases = sorted(
        set(root_aliases) - {item["id"] for item in root_alias_inventory}
    )
    for alias_id in unattached_root_aliases:
        findings.append(Finding(
            "FAIL", "COMMAND_CONTRACT_ROOT_ALIAS_STALE",
            f"Contract root alias {alias_id} does not match a registered root alias.",
            alias_id,
        ))

    routes: list[dict[str, Any]] = []
    syntax_seen: dict[str, str] = {}
    for stable_id, entry in sorted(route_entries.items()):
        root = str(entry.get("root", "")).lower()
        syntax = str(entry.get("syntax", "")).strip()
        expected_id = route_stable_id(root, syntax)
        if stable_id != expected_id:
            findings.append(Finding(
                "FAIL", "COMMAND_CONTRACT_ROUTE_ID_DRIFT",
                f"Route {syntax or stable_id} must use stable ID {expected_id}.",
                stable_id,
            ))
        if f"command.{root}" not in actual_roots:
            findings.append(Finding(
                "FAIL", "COMMAND_CONTRACT_ROUTE_ROOT_MISSING",
                f"Route {syntax or stable_id} refers to unregistered root /{root}.",
                stable_id,
            ))
        if not syntax.startswith(f"/{root}") or (
            len(syntax) > len(root) + 1 and syntax[len(root) + 1] != " "
        ):
            findings.append(Finding(
                "FAIL", "COMMAND_CONTRACT_ROUTE_SYNTAX_INVALID",
                f"Route syntax {syntax!r} does not belong to /{root}.",
                stable_id,
            ))
        if syntax in syntax_seen:
            findings.append(Finding(
                "FAIL", "COMMAND_CONTRACT_ROUTE_DUPLICATE",
                f"Route syntax {syntax!r} is declared by both {syntax_seen[syntax]} and {stable_id}.",
                stable_id,
            ))
        syntax_seen[syntax] = stable_id
        source = str(entry.get("source", ""))
        permission = _permission_nodes(entry.get("permission", []))
        tab_text = str(entry.get("tab_completion", "")).lower()
        routes.append({
            "id": stable_id,
            "kind": "route",
            "path": syntax.removeprefix("/"),
            "name": syntax.removeprefix("/").split(" ", 1)[-1],
            "root": root,
            "arguments": _arguments_from_usage(syntax),
            "permission": permission,
            "audience": _audience(permission, f"{entry.get('executor', '')} {entry.get('purpose', '')}", root),
            "usage": syntax,
            "help": str(entry.get("purpose", "")),
            "tab_completion": tab_text not in ("", "—", "nincs", "none", "false"),
            "implementation_method": Path(source).stem,
            "source": source,
            "line": int(entry.get("line", 1)),
            "confidence": "HIGH",
            "route_aliases": [str(alias) for alias in entry.get("route_aliases", [])],
            "documentation": list(entry.get("docs", [])),
            "source_evidence": [{"source": source, "line": int(entry.get("line", 1)), "symbol": syntax}],
            "present_in_deployed": entry.get("present_in_deployed"),
            "deployed_status": entry.get("deployed_status", ""),
            "gui_alternative": entry.get("gui_alternative", ""),
            "limitation": entry.get("limitation", ""),
        })

    routing_alias_inventory: list[dict[str, Any]] = []
    expected_routing_alias_ids: dict[str, tuple[str, str]] = {}
    for route in routes:
        for alias in route.get("route_aliases", []):
            alias_id = routing_alias_stable_id(route["id"], alias)
            expected_routing_alias_ids[alias_id] = (route["id"], alias)
    for alias_id in sorted(set(expected_routing_alias_ids) - set(route_aliases)):
        route_id, alias = expected_routing_alias_ids[alias_id]
        findings.append(Finding(
            "FAIL", "COMMAND_CONTRACT_ROUTING_ALIAS_MISSING",
            f"Routing alias {alias!r} for {route_id} is absent from the exact command contract.",
            alias_id,
        ))
    for alias_id in sorted(set(route_aliases) - set(expected_routing_alias_ids)):
        findings.append(Finding(
            "FAIL", "COMMAND_CONTRACT_ROUTING_ALIAS_STALE",
            f"Contract routing alias {alias_id} no longer belongs to a route.",
            alias_id,
        ))
    routes_by_id = {item["id"]: item for item in routes}
    for alias_id, (route_id, alias) in sorted(expected_routing_alias_ids.items()):
        entry = route_aliases.get(alias_id)
        if not entry:
            continue
        if entry.get("route") != route_id or str(entry.get("alias", "")) != alias:
            findings.append(Finding(
                "FAIL", "COMMAND_CONTRACT_ROUTING_ALIAS_DRIFT",
                f"{alias_id} differs from route {route_id} alias {alias!r}.",
                alias_id,
            ))
        route = routes_by_id[route_id]
        routing_alias_inventory.append({
            "id": alias_id,
            "kind": "routing-alias",
            "root": route["root"],
            "route": route_id,
            "alias": alias,
            "documentation": list(entry.get("docs", [])),
            "confidence": "HIGH",
            "source_evidence": list(route.get("source_evidence", [])),
        })

    expected_counts = policy.get("expected_counts", {})
    actual_counts = {
        "root_commands": len(commands),
        "functional_routes": len(routes),
        "root_aliases": len(root_alias_inventory),
        "routing_aliases": len(routing_alias_inventory),
    }
    for key, actual in actual_counts.items():
        declared = expected_counts.get(key)
        if declared != actual:
            findings.append(Finding(
                "FAIL", "COMMAND_CONTRACT_COUNT_DRIFT",
                f"Command contract expects {key}={declared!r}, resolved {actual}.",
                "commands._contract",
            ))

    source_hashes = policy.get("source_sha256", {})
    if not isinstance(source_hashes, dict):
        source_hashes = {}
        findings.append(Finding(
            "FAIL", "COMMAND_CONTRACT_SOURCE_POLICY_MISSING",
            "Exact command contract has no source_sha256 map.",
            "commands._contract",
        ))
    required_sources = {
        item["registration_file"] for item in commands
    } | {
        route["source"] for route in routes
    }
    for relative in sorted(required_sources - set(source_hashes)):
        findings.append(Finding(
            "FAIL", "COMMAND_CONTRACT_SOURCE_MISSING",
            f"Command source {relative} is not protected by the exact contract.",
            "commands._contract",
            (Evidence(relative, 1, "source_sha256"),),
        ))
    for relative in sorted(set(source_hashes) - required_sources):
        findings.append(Finding(
            "FAIL", "COMMAND_CONTRACT_SOURCE_STALE",
            f"Command contract hashes source {relative}, but no registered root or route uses it.",
            "commands._contract",
            (Evidence(relative, 1, "source_sha256"),),
        ))
    for relative, expected_hash in sorted(source_hashes.items()):
        path = root_path / relative
        if not path.is_file():
            findings.append(Finding(
                "FAIL", "COMMAND_CONTRACT_SOURCE_NOT_FOUND",
                f"Command contract source does not exist: {relative}.",
                "commands._contract",
                (Evidence(relative, 1, "source_sha256"),),
            ))
            continue
        actual_hash = _source_sha256(path)
        if actual_hash != expected_hash:
            findings.append(Finding(
                "FAIL", "COMMAND_CONTRACT_SOURCE_DRIFT",
                f"Command source {relative} changed without an exact contract update.",
                "commands._contract",
                (Evidence(relative, 1, f"expected {expected_hash}; actual {actual_hash}"),),
            ))

    for command in commands:
        command["help_path"] = [
            route["id"] for route in routes if route["root"] == command["name"]
        ]
    return (
        sorted(routes, key=lambda item: (item["root"], item["usage"], item["id"])),
        sorted(root_alias_inventory, key=lambda item: (item["root"], item["alias"])),
        sorted(routing_alias_inventory, key=lambda item: (item["root"], item["route"], item["alias"])),
        findings,
    )


def _resolve_impl(expression: str, registration_source: JavaSource) -> str | None:
    match = re.search(r"\bnew\s+([A-Za-z0-9_$.]+)\s*\(", expression)
    if match:
        return match.group(1).split(".")[-1]
    variable = expression.strip()
    if re.fullmatch(r"[A-Za-z_$][A-Za-z0-9_$]*", variable):
        prefix = registration_source.source
        match = re.search(r"\b([A-Za-z0-9_$.]+)\s+" + re.escape(variable) + r"\s*=\s*new\s+([A-Za-z0-9_$.]+)\s*\(", prefix)
        if match:
            return match.group(2).split(".")[-1]
    return None


def _resolve_aliases(expression: str, constants: dict[str, str]) -> tuple[list[str], bool]:
    if expression.strip() in ("List.of()", "Collections.emptyList()", "Set.of()"):
        return [], True
    strings = java_strings(expression)
    if strings:
        return sorted({item.lower() for item in strings}), True
    resolved = resolve_java_string(expression, constants)
    return ([resolved.lower()] if resolved else []), resolved is not None


def _permissions(text: str, index: JavaIndex, source: JavaSource) -> list[str]:
    found: set[str] = set()
    for _, call in scan_calls(text, "hasPermission"):
        expression = split_top_level(call)[0] if call else ""
        value = resolve_java_string(expression, {**index.constants, **source.constants})
        if value:
            found.add(value)
    for name, value in source.constants.items():
        if "PERMISSION" in name:
            found.add(value)
    return sorted(found)


def _audience(permissions: list[str], text: str, root: str = "") -> list[str]:
    lower = " ".join(permissions).lower() + " " + text.lower() + " " + root.lower()
    audiences: list[str] = []
    if "moder" in lower or "mute" in lower or "report" in lower:
        audiences.append("MODERATOR")
    if any(item in lower for item in ("admin", "manage", "reload", "debug", "bypass", "invsee", "npcbind", "iceitem")):
        audiences.append("ADMIN")
    if "test" in lower:
        audiences.append("TESTER")
    if "developer" in lower or " dev" in lower:
        audiences.append("DEVELOPER")
    if not audiences or "player" in lower:
        audiences.insert(0, "PLAYER")
    return list(dict.fromkeys(audiences))


def _find_execute_player_only(source: str) -> bool:
    block = method_block(source, "execute")
    text = block[1] if block else source
    return bool(re.search(r"!\s*\(\s*sender\s+instanceof\s+Player|!\s*\([^)]*getSender\(\)\s+instanceof\s+Player", text))


def _method_for_case(expression: str) -> str:
    match = re.search(r"(?:return\s+)?([A-Za-z_$][A-Za-z0-9_$]*)\s*\(", expression)
    return match.group(1) if match else "execute"


def _arguments_from_usage(usage: str) -> list[dict[str, Any]]:
    args: list[dict[str, Any]] = []
    for token in re.findall(r"<[^>]+>|\[[^\]]+\]", usage):
        args.append({"name": token[1:-1], "required": token.startswith("<"), "token": token})
    return args


def _usage_for(source: str, root: str, sub: str) -> str:
    candidates = []
    for value in java_strings(source):
        if f"/{root} {sub}" in value:
            plain = re.sub(r"&[0-9A-FK-ORa-fk-or]", "", value)
            match = re.search(r"/" + re.escape(root) + r"\s+" + re.escape(sub) + r"[^\n|]*", plain)
            candidates.append(match.group(0).strip(" .") if match else plain)
    return sorted(candidates, key=len)[0] if candidates else f"/{root} {sub}"


def _switch_blocks(source: str) -> list[tuple[int, int, str]]:
    clean = java_without_comments(source)
    result: list[tuple[int, int, str]] = []
    for match in re.finditer(r"\bswitch\s*\(", clean):
        paren_open = clean.find("(", match.start())
        paren_close = find_matching(source, paren_open, "(", ")")
        if paren_close < 0:
            continue
        expression = source[paren_open + 1:paren_close]
        arg_match = re.search(r"args\s*\[\s*(\d+)\s*\]", java_without_comments(expression))
        if not arg_match:
            continue
        cursor = paren_close + 1
        while cursor < len(clean) and clean[cursor].isspace():
            cursor += 1
        if cursor >= len(clean) or clean[cursor] != "{":
            continue
        closing = find_matching(source, cursor, "{", "}")
        if closing >= 0:
            result.append((int(arg_match.group(1)), cursor, source[cursor + 1:closing]))
    return result


def _direct_subcommands(root: str, src: JavaSource, index: JavaIndex) -> tuple[list[dict[str, Any]], list[Finding]]:
    result: dict[str, dict[str, Any]] = {}
    findings: list[Finding] = []
    for arg_index, block_start, block in _switch_blocks(src.source):
        if arg_index > 1:
            continue
        for match in re.finditer(r"\bcase\s+(.+?)\s*->\s*([^;{}]+(?:;|\{)?)", block, re.DOTALL):
            labels = java_strings(match.group(1))
            if not labels:
                continue
            handler = _method_for_case(match.group(2))
            for label in labels:
                parent = root
                if arg_index == 1:
                    findings.append(Finding("REVIEW_REQUIRED", "NESTED_PARENT_UNRESOLVED",
                                            f"Nested subcommand '{label}' uses args[1]; parent path requires review.",
                                            f"command.{root}.{label}",
                                            (Evidence(src.relative, line_number(src.source, block_start + match.start()), handler),)))
                path = f"{parent}.{label.lower()}"
                method = method_block(src.source, handler)
                method_text = method[1] if method else src.source
                permissions = _permissions(method_text, index, src)
                usage = _usage_for(src.source, root, label.lower())
                result[path] = {
                    "id": f"command.{path}", "path": path, "name": label.lower(), "root": root,
                    "arguments": _arguments_from_usage(usage), "permission": permissions,
                    "audience": _audience(permissions, method_text, label), "usage": usage,
                    "help": usage, "tab_completion": False, "implementation_method": handler,
                    "source": src.relative, "line": line_number(src.source, block_start + match.start()),
                    "confidence": "HIGH" if arg_index == 0 else "REVIEW_REQUIRED",
                }
    clean = java_without_comments(src.source)
    patterns = [
        r'"([^"\\]+)"\s*\.\s*equals(?:IgnoreCase)?\s*\(\s*args\s*\[\s*0\s*\]\s*\)',
        r'args\s*\[\s*0\s*\]\s*\.\s*equals(?:IgnoreCase)?\s*\(\s*"([^"\\]+)"\s*\)',
    ]
    for pattern in patterns:
        for match in re.finditer(pattern, clean):
            label = match.group(1).lower()
            path = f"{root}.{label}"
            if path in result:
                continue
            usage = _usage_for(src.source, root, label)
            permissions = _permissions(src.source[max(0, match.start()-300):match.end()+800], index, src)
            result[path] = {
                "id": f"command.{path}", "path": path, "name": label, "root": root,
                "arguments": _arguments_from_usage(usage), "permission": permissions,
                "audience": _audience(permissions, clean[match.start():match.end()+400], label),
                "usage": usage, "help": usage, "tab_completion": False,
                "implementation_method": nearest_method(src.source, match.start()) or "execute",
                "source": src.relative, "line": line_number(src.source, match.start()), "confidence": "MEDIUM",
            }
    suggest = method_block(src.source, "suggest")
    if suggest:
        suggestion_values = set(java_strings(suggest[1]))
        for item in result.values():
            if item["name"] in suggestion_values:
                item["tab_completion"] = True
    if ("args[0]" in clean or "args [0]" in clean) and not result:
        findings.append(Finding("REVIEW_REQUIRED", "DYNAMIC_COMMAND_ROUTING",
                                f"{src.class_name} reads command arguments but no static subcommand route was resolved.",
                                f"command.{root}", (Evidence(src.relative, 1, src.class_name),)))
    return sorted(result.values(), key=lambda item: item["path"]), findings


def _method_return_string(source: str, method: str) -> str:
    block = method_block(source, method)
    if not block:
        return ""
    match = re.search(r"\breturn\s+(\"(?:\\.|[^\"\\])*\")\s*;", java_without_comments(block[1]))
    values = java_strings(match.group(1)) if match else []
    return values[0] if values else ""


def _dispatch_subcommands(root: str, src: JavaSource, index: JavaIndex) -> tuple[list[dict[str, Any]], list[Finding]]:
    result: list[dict[str, Any]] = []
    findings: list[Finding] = []
    for offset, call in scan_calls(src.source, "register"):
        expression = split_top_level(call)[0] if call else ""
        match = re.search(r"\bnew\s+([A-Za-z0-9_$.]+)\s*\(", expression)
        if not match:
            findings.append(Finding("REVIEW_REQUIRED", "DYNAMIC_SUBCOMMAND_REGISTRATION",
                                    f"Subcommand registration in {src.class_name} could not be resolved: {expression[:100]}",
                                    f"command.{root}", (Evidence(src.relative, line_number(src.source, offset), "register"),)))
            continue
        class_name = match.group(1).split(".")[-1]
        sub_src = index.resolve_class(class_name)
        if not sub_src:
            findings.append(Finding("FAIL", "SUBCOMMAND_IMPLEMENTATION_MISSING",
                                    f"Registered subcommand class {class_name} was not found.", f"command.{root}",
                                    (Evidence(src.relative, line_number(src.source, offset), "register"),)))
            continue
        name = _method_return_string(sub_src.source, "name")
        if not name:
            findings.append(Finding("REVIEW_REQUIRED", "SUBCOMMAND_NAME_UNRESOLVED",
                                    f"{class_name}.name() is not a static string.", f"command.{root}",
                                    (Evidence(sub_src.relative, 1, class_name),)))
            name = f"review-{kebab(class_name)}"
        usage = _method_return_string(sub_src.source, "usage") or f"/{root} {name}"
        description = _method_return_string(sub_src.source, "description")
        permissions = _permissions(sub_src.source, index, sub_src)
        result.append({
            "id": f"command.{root}.{name.lower()}", "path": f"{root}.{name.lower()}", "name": name.lower(), "root": root,
            "arguments": _arguments_from_usage(usage), "permission": permissions,
            "audience": _audience(permissions, sub_src.source, name), "usage": usage,
            "help": description, "tab_completion": "tabComplete" in sub_src.source,
            "implementation_method": f"{class_name}.execute", "source": sub_src.relative,
            "line": line_number(sub_src.source, sub_src.source.find("execute")),
            "confidence": "HIGH" if not name.startswith("review-") else "REVIEW_REQUIRED",
        })
    return sorted(result, key=lambda item: item["path"]), findings


def scan_commands(
    root_path: Path,
    index: JavaIndex,
    manifest: dict[str, Any],
) -> tuple[
    list[dict[str, Any]],
    list[dict[str, Any]],
    list[dict[str, Any]],
    list[dict[str, Any]],
    list[Finding],
]:
    commands: list[dict[str, Any]] = []
    subcommands: list[dict[str, Any]] = []
    findings: list[Finding] = []
    registered_impls: set[str] = set()
    names: dict[str, str] = {}

    for registration_source in index.sources:
        for offset, call in scan_calls(registration_source.source, "registerCommand"):
            args = split_top_level(call)
            if len(args) < 4:
                findings.append(Finding("FAIL", "COMMAND_REGISTRATION_PARSE_ERROR",
                                        "registerCommand call has fewer than four arguments.", "",
                                        (Evidence(registration_source.relative, line_number(registration_source.source, offset), "registerCommand"),)))
                continue
            constants = {**index.constants, **registration_source.constants}
            name = resolve_java_string(args[0], constants)
            if not name:
                stable = f"command.review-{line_number(registration_source.source, offset)}"
                findings.append(Finding("REVIEW_REQUIRED", "DYNAMIC_COMMAND_NAME",
                                        f"Command name could not be resolved from {args[0]}", stable,
                                        (Evidence(registration_source.relative, line_number(registration_source.source, offset), "registerCommand"),)))
                name = f"review-{line_number(registration_source.source, offset)}"
            name = name.lower()
            description = resolve_java_string(args[1], constants) or ""
            aliases, aliases_resolved = _resolve_aliases(args[2], constants)
            impl = _resolve_impl(args[3], registration_source)
            if not impl:
                findings.append(Finding("FAIL", "COMMAND_IMPLEMENTATION_UNRESOLVED",
                                        f"Implementation for /{name} could not be resolved from {args[3][:100]}", f"command.{name}",
                                        (Evidence(registration_source.relative, line_number(registration_source.source, offset), "registerCommand"),)))
            else:
                registered_impls.add(impl)
            impl_src = index.resolve_class(impl or "")
            if not impl_src:
                findings.append(Finding("FAIL", "COMMAND_IMPLEMENTATION_MISSING",
                                        f"Implementation source for /{name} ({impl or 'unknown'}) was not found.", f"command.{name}",
                                        (Evidence(registration_source.relative, line_number(registration_source.source, offset), "registerCommand"),)))
                command_permissions: list[str] = []
                player_only = False
                confidence = "REVIEW_REQUIRED"
                detected: list[dict[str, Any]] = []
            else:
                command_permissions = _permissions(impl_src.source, index, impl_src)
                player_only = _find_execute_player_only(impl_src.source)
                if "extends AbstractDispatchCommand" in java_without_comments(impl_src.source):
                    detected, extra = _dispatch_subcommands(name, impl_src, index)
                else:
                    detected, extra = _direct_subcommands(name, impl_src, index)
                findings.extend(extra)
                confidence = "HIGH" if aliases_resolved else "REVIEW_REQUIRED"
                subcommands.extend(detected)
            docs_entry = manifest.get("commands", {}).get(f"command.{name}", {})
            docs = docs_entry.get("docs", []) if isinstance(docs_entry, dict) else []
            command = {
                "id": f"command.{name}", "path": name, "name": name, "namespace": "icesmp",
                "aliases": aliases, "implementation": impl or "", "registration_file": registration_source.relative,
                "registration_line": line_number(registration_source.source, offset), "description": description,
                "usage": f"/{name}", "audience": _audience(command_permissions, (impl_src.source if impl_src else description), name),
                "permission": command_permissions, "player_only": player_only, "console_compatible": not player_only,
                "tab_completion": bool(impl_src and " suggest(" in impl_src.source),
                "help_path": [item["path"] for item in detected], "documentation": docs, "confidence": confidence,
                "source_evidence": [{"source": registration_source.relative, "line": line_number(registration_source.source, offset), "symbol": "registerCommand"}],
            }
            commands.append(command)
            for candidate in [name, *aliases]:
                if candidate in names:
                    findings.append(Finding("FAIL", "COMMAND_OR_ALIAS_COLLISION",
                                            f"Command/alias '{candidate}' collides between {names[candidate]} and command.{name}.",
                                            f"command.{name}", (Evidence(registration_source.relative, command["registration_line"], "registerCommand"),)))
                else:
                    names[candidate] = f"command.{name}"

    ignores = manifest.get("explicit-ignores", {})
    for src in index.sources:
        clean = java_without_comments(src.source)
        is_command = "implements BasicCommand" in clean or "extends AbstractDispatchCommand" in clean
        if not is_command or re.search(r"\babstract\s+class\b", clean) or src.class_name in ("AbstractDispatchCommand",):
            continue
        component_id = f"component.{src.package}.{src.class_name}" if src.package else f"component.{src.class_name}"
        if src.class_name not in registered_impls and component_id not in ignores:
            findings.append(Finding("FAIL", "UNREGISTERED_COMMAND_IMPLEMENTATION",
                                    f"{src.class_name} implements a command but is not registered and is not explicitly ignored.",
                                    component_id, (Evidence(src.relative, 1, src.class_name),)))

    all_ids = [item["id"] for item in commands] + [item["id"] for item in subcommands]
    for stable_id in sorted(set(all_ids)):
        if all_ids.count(stable_id) > 1:
            findings.append(Finding("FAIL", "DUPLICATE_STABLE_ID", f"Duplicate command stable ID: {stable_id}", stable_id))

    commands = sorted(commands, key=lambda item: item["path"])
    contract_routes, root_aliases, routing_aliases, contract_findings = _command_contract(
        root_path, manifest, commands
    )
    if isinstance(manifest.get("commands", {}).get("_contract"), dict):
        # The exhaustive, hash-bound contract replaces deliberately incomplete Java
        # routing heuristics. Registration/collision/parser failures remain visible.
        superseded = {
            "DYNAMIC_COMMAND_ROUTING",
            "DYNAMIC_SUBCOMMAND_REGISTRATION",
            "NESTED_PARENT_UNRESOLVED",
            "SUBCOMMAND_NAME_UNRESOLVED",
        }
        findings = [finding for finding in findings if finding.code not in superseded]
        subcommands = contract_routes
    else:
        root_aliases = [
            {
                "id": root_alias_stable_id(command["name"], alias),
                "kind": "root-alias",
                "root": command["name"],
                "alias": alias,
                "path": alias,
                "documentation": manifest.get("commands", {}).get(
                    root_alias_stable_id(command["name"], alias), {}
                ).get("docs", []),
                "confidence": command.get("confidence", "HIGH"),
                "source_evidence": list(command.get("source_evidence", [])),
            }
            for command in commands
            for alias in command.get("aliases", [])
        ]
        routing_aliases = []
    findings.extend(contract_findings)
    all_contract_ids = [
        item["id"]
        for group in (commands, subcommands, root_aliases, routing_aliases)
        for item in group
    ]
    for stable_id in sorted(set(all_contract_ids)):
        if all_contract_ids.count(stable_id) > 1:
            findings.append(Finding(
                "FAIL", "DUPLICATE_STABLE_ID",
                f"Duplicate command-interface stable ID: {stable_id}.",
                stable_id,
            ))
    return (
        commands,
        sorted(subcommands, key=lambda item: item["path"]),
        root_aliases,
        routing_aliases,
        findings,
    )
