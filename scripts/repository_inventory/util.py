from __future__ import annotations

import ast
import hashlib
import json
import os
import re
import subprocess
from pathlib import Path
from typing import Any, Iterable, Iterator


def posix(path: Path, root: Path) -> str:
    try:
        return path.resolve().relative_to(root.resolve()).as_posix()
    except ValueError:
        return path.as_posix()


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8-sig", errors="replace")


def line_number(text: str, index: int) -> int:
    return text.count("\n", 0, max(0, index)) + 1


def kebab(value: str) -> str:
    value = re.sub(r"([a-z0-9])([A-Z])", r"\1-\2", value)
    value = re.sub(r"[^A-Za-z0-9]+", "-", value).strip("-")
    return value.lower()


def stable_digest(value: str, length: int = 12) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()[:length]


def dump_json(path: Path, data: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def load_json(path: Path) -> Any:
    return json.loads(read_text(path))


def run(command: list[str], cwd: Path, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(command, cwd=cwd, text=True, encoding="utf-8", errors="replace",
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=check)


def git(root: Path, *args: str, check: bool = True) -> str:
    result = run(["git", *args], root, check=check)
    return result.stdout.strip()


def java_without_comments(source: str) -> str:
    """Remove comments while retaining strings and preserving offsets/newlines."""
    chars = list(source)
    i = 0
    state = "code"
    while i < len(chars):
        c = chars[i]
        n = chars[i + 1] if i + 1 < len(chars) else ""
        if state == "code":
            if c == '"':
                state = "string"
            elif c == "'":
                state = "char"
            elif c == "/" and n == "/":
                chars[i] = chars[i + 1] = " "
                state = "line_comment"
                i += 1
            elif c == "/" and n == "*":
                chars[i] = chars[i + 1] = " "
                state = "block_comment"
                i += 1
        elif state == "string":
            if c == "\\":
                i += 1
            elif c == '"':
                state = "code"
        elif state == "char":
            if c == "\\":
                i += 1
            elif c == "'":
                state = "code"
        elif state == "line_comment":
            if c == "\n":
                state = "code"
            else:
                chars[i] = " "
        elif state == "block_comment":
            if c == "*" and n == "/":
                chars[i] = chars[i + 1] = " "
                state = "code"
                i += 1
            elif c != "\n":
                chars[i] = " "
        i += 1
    return "".join(chars)


def find_matching(text: str, start: int, opening: str = "(", closing: str = ")") -> int:
    if start < 0 or start >= len(text) or text[start] != opening:
        return -1
    depth = 0
    i = start
    state = "code"
    while i < len(text):
        c = text[i]
        n = text[i + 1] if i + 1 < len(text) else ""
        if state == "code":
            if c == '"':
                state = "string"
            elif c == "'":
                state = "char"
            elif c == "/" and n == "/":
                state = "line_comment"; i += 1
            elif c == "/" and n == "*":
                state = "block_comment"; i += 1
            elif c == opening:
                depth += 1
            elif c == closing:
                depth -= 1
                if depth == 0:
                    return i
        elif state == "string":
            if c == "\\": i += 1
            elif c == '"': state = "code"
        elif state == "char":
            if c == "\\": i += 1
            elif c == "'": state = "code"
        elif state == "line_comment":
            if c == "\n": state = "code"
        elif state == "block_comment":
            if c == "*" and n == "/": state = "code"; i += 1
        i += 1
    return -1


def scan_calls(source: str, token: str) -> Iterator[tuple[int, str]]:
    clean = java_without_comments(source)
    start = 0
    pattern = re.compile(r"(?<![A-Za-z0-9_$])" + re.escape(token) + r"\s*\(")
    while True:
        match = pattern.search(clean, start)
        if not match:
            return
        open_index = clean.find("(", match.start())
        close_index = find_matching(source, open_index)
        if close_index < 0:
            yield match.start(), source[open_index + 1:]
            return
        yield match.start(), source[open_index + 1:close_index]
        start = close_index + 1


def split_top_level(value: str, separator: str = ",") -> list[str]:
    result: list[str] = []
    depth = {"(": 0, "[": 0, "{": 0, "<": 0}
    pairs = {")": "(", "]": "[", "}": "{", ">": "<"}
    start = 0
    state = "code"
    i = 0
    while i < len(value):
        c = value[i]
        if state == "code":
            if c == '"': state = "string"
            elif c == "'": state = "char"
            elif c in depth: depth[c] += 1
            elif c in pairs and depth[pairs[c]] > 0: depth[pairs[c]] -= 1
            elif c == separator and not any(depth.values()):
                result.append(value[start:i].strip()); start = i + 1
        elif state == "string":
            if c == "\\": i += 1
            elif c == '"': state = "code"
        elif state == "char":
            if c == "\\": i += 1
            elif c == "'": state = "code"
        i += 1
    result.append(value[start:].strip())
    return result


def java_strings(expression: str) -> list[str]:
    values: list[str] = []
    for match in re.finditer(r'"(?:\\.|[^"\\])*"', expression, re.DOTALL):
        try:
            values.append(ast.literal_eval(match.group(0)))
        except (ValueError, SyntaxError):
            values.append(match.group(0)[1:-1])
    return values


def java_constants(source: str) -> dict[str, str]:
    constants: dict[str, str] = {}
    clean = java_without_comments(source)
    pattern = re.compile(r"\b(?:public|protected|private)?\s*(?:static\s+)?(?:final\s+)?String\s+([A-Z][A-Z0-9_]*)\s*=\s*(\"(?:\\.|[^\"\\])*\")\s*;")
    for match in pattern.finditer(clean):
        strings = java_strings(match.group(2))
        if strings:
            constants[match.group(1)] = strings[0]
    return constants


def resolve_java_string(expression: str, constants: dict[str, str]) -> str | None:
    strings = java_strings(expression)
    if strings:
        return strings[0]
    token = expression.strip().split(".")[-1]
    return constants.get(token)


def method_block(source: str, method_name: str) -> tuple[int, str] | None:
    clean = java_without_comments(source)
    pattern = re.compile(r"\b" + re.escape(method_name) + r"\s*\([^;{}]*\)\s*(?:throws\s+[^\{]+)?\{")
    match = pattern.search(clean)
    if not match:
        return None
    opening = clean.find("{", match.start())
    closing = find_matching(source, opening, "{", "}")
    if closing < 0:
        return None
    return opening, source[opening + 1:closing]


def nearest_method(source: str, index: int) -> str:
    clean = java_without_comments(source[:index])
    matches = list(re.finditer(r"\b([A-Za-z_$][A-Za-z0-9_$]*)\s*\([^;{}]*\)\s*(?:throws\s+[^\{]+)?\{", clean))
    return matches[-1].group(1) if matches else ""


def _strip_yaml_inline_comment(raw: str) -> str:
    state = "code"
    for index, char in enumerate(raw):
        if state == "code":
            if char == '"': state = "double"
            elif char == "'": state = "single"
            elif char == "#" and (index == 0 or raw[index - 1].isspace()):
                return raw[:index].rstrip()
        elif state == "double":
            if char == "\\":
                continue
            if char == '"': state = "code"
        elif state == "single" and char == "'":
            state = "code"
    return raw.rstrip()


def parse_scalar(raw: str) -> Any:
    value = _strip_yaml_inline_comment(raw).strip()
    if not value:
        return None
    if value[0:1] in ('"', "'") and value[-1:] == value[0]:
        try: return ast.literal_eval(value)
        except (ValueError, SyntaxError): return value[1:-1]
    lower = value.lower()
    if lower in ("true", "false"): return lower == "true"
    if lower in ("null", "~"): return None
    if value.startswith("[") and value.endswith("]"):
        return [parse_scalar(item) for item in split_top_level(value[1:-1]) if item.strip()]
    try:
        return int(value)
    except ValueError:
        try: return float(value)
        except ValueError: return value


def flatten_yaml_paths(path: Path) -> dict[str, Any]:
    """Conservative indentation parser: extracts mapping paths/defaults without executing YAML tags."""
    result: dict[str, Any] = {}
    stack: list[tuple[int, str]] = []
    for raw_line in read_text(path).splitlines():
        if not raw_line.strip() or raw_line.lstrip().startswith("#") or raw_line.lstrip().startswith("-"):
            continue
        match = re.match(r"^(\s*)([^:#][^:]*?):(?:\s+(.*))?$", raw_line)
        if not match:
            continue
        indent = len(match.group(1).replace("\t", "    "))
        key = match.group(2).strip().strip('"\'')
        value = (match.group(3) or "").strip()
        while stack and stack[-1][0] >= indent:
            stack.pop()
        path_parts = [item[1] for item in stack] + [key]
        full = ".".join(path_parts)
        result[full] = parse_scalar(value) if value else None
        if not value:
            stack.append((indent, key))
    return result


def load_manifest(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {"version": 1, "commands": {}, "features": {}, "permissions": {}, "config-sections": {}, "components": {}, "explicit-ignores": {}}
    text = read_text(path)
    try:
        data = json.loads(text)
    except json.JSONDecodeError as exc:
        raise ValueError(f"Manifest must be JSON-compatible YAML: {exc}") from exc
    if not isinstance(data, dict):
        raise ValueError("Manifest root must be a mapping")
    return data


def markdown_escape(value: Any) -> str:
    return str(value if value is not None else "").replace("|", "\\|").replace("\n", " ")


def write_markdown_table(path: Path, title: str, headers: list[str], rows: Iterable[Iterable[Any]], intro: str = "") -> None:
    lines = [f"# {title}", ""]
    if intro:
        lines.extend([intro, ""])
    lines.append("| " + " | ".join(headers) + " |")
    lines.append("| " + " | ".join("---" for _ in headers) + " |")
    for row in rows:
        lines.append("| " + " | ".join(markdown_escape(item) for item in row) + " |")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def iter_files(root: Path, patterns: tuple[str, ...]) -> Iterator[Path]:
    ignored = {".git", ".gradle", "build", "run", "node_modules", ".idea"}
    for pattern in patterns:
        for path in root.rglob(pattern):
            if path.is_file() and not any(part in ignored for part in path.parts):
                yield path


def unique_sorted(items: Iterable[Any], key=None) -> list[Any]:
    seen: set[str] = set()
    result: list[Any] = []
    for item in sorted(items, key=key):
        marker = json.dumps(item, ensure_ascii=False, sort_keys=True) if isinstance(item, (dict, list)) else repr(item)
        if marker not in seen:
            seen.add(marker); result.append(item)
    return result
