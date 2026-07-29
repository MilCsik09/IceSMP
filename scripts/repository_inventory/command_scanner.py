from __future__ import annotations

import re
from pathlib import Path
from typing import Any

from .java_scanner import JavaIndex, JavaSource
from .models import Evidence, Finding
from .util import (find_matching, java_strings, java_without_comments, kebab, line_number,
                   method_block, nearest_method, resolve_java_string, scan_calls, split_top_level)


ADMIN_HINTS = ("admin", "moder", "staff", "manage", "reload", "debug", "bypass", "set", "give", "remove")


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


def scan_commands(root_path: Path, index: JavaIndex, manifest: dict[str, Any]) -> tuple[list[dict[str, Any]], list[dict[str, Any]], list[Finding]]:
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
    return sorted(commands, key=lambda item: item["path"]), sorted(subcommands, key=lambda item: item["path"]), findings
