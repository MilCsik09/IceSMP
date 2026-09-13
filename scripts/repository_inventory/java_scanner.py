from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path

from .util import find_matching, iter_files, java_constants, java_without_comments, posix, read_text


def duplicate_method_signatures(source: str) -> list[tuple[str, str, tuple[str, ...]]]:
    """Keep the consistency guard's signatures scoped to their actual named/anonymous type body."""
    clean = java_without_comments(source)
    clean = re.sub(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'',
                   lambda m: re.sub(r'[^\n]', ' ', m.group()), clean)
    scopes: list[tuple[int, int, str]] = []
    for match in re.finditer(r"\b(?:class|record|interface|enum)\s+(\w+)", clean):
        offset = match.end()
        while offset < len(clean) and clean[offset] not in "{;":
            if clean[offset] == "(":
                end = find_matching(clean, offset)
                if end < 0:
                    break
                offset = end
            offset += 1
        if offset < len(clean) and clean[offset] == "{":
            end = find_matching(clean, offset, "{", "}")
            if end >= 0:
                scopes.append((offset, end, match.group(1)))
    for match in re.finditer(r"\bnew\s+[\w.$]+(?:\s*<[^;{}]*?>)?\s*\(", clean):
        end = find_matching(clean, match.end() - 1)
        if end < 0:
            continue
        offset = end + 1
        while offset < len(clean) and clean[offset].isspace():
            offset += 1
        if offset < len(clean) and clean[offset] == "{":
            end = find_matching(clean, offset, "{", "}")
            if end >= 0:
                scopes.append((offset, end, f"anonymous@{clean.count(chr(10), 0, offset) + 1}"))
    seen = set()
    duplicates = []
    for match in re.finditer(r"(?:public|private|protected)[\w\s<>,\[\]]*?\s(\w+)\(([^)]*)\)\s*\{", clean):
        enclosing = [scope for scope in scopes if scope[0] < match.start() < scope[1]]
        owner = max(enclosing, default=(-1, len(clean), ""))
        name, params = match.group(1), match.group(2)
        types = tuple(t.split(".")[-1] for t in re.findall(r"(?:final\s+)?([\w.<>\[\]]+)\s+\w+\s*(?:,|$)", params))
        key = (owner[0], name, types)
        if key in seen:
            duplicates.append((owner[2], name, types))
        seen.add(key)
    return duplicates


@dataclass
class JavaSource:
    path: Path
    relative: str
    source: str
    package: str
    class_name: str
    constants: dict[str, str]


class JavaIndex:
    def __init__(self, root: Path) -> None:
        self.root = root
        self.sources: list[JavaSource] = []
        self.by_class: dict[str, list[JavaSource]] = {}
        self.constants: dict[str, str] = {}
        self._load()

    def _load(self) -> None:
        for path in sorted(iter_files(self.root, ("*.java",))):
            relative = posix(path, self.root)
            if "/src/test/" in f"/{relative}" or not relative.startswith("src/main/java/"):
                continue
            source = read_text(path)
            clean = java_without_comments(source)
            package_match = re.search(r"\bpackage\s+([A-Za-z0-9_.]+)\s*;", clean)
            class_match = re.search(r"\b(?:class|interface|enum|record)\s+([A-Za-z_$][A-Za-z0-9_$]*)", clean)
            class_name = class_match.group(1) if class_match else path.stem
            item = JavaSource(path, relative, source, package_match.group(1) if package_match else "", class_name, java_constants(source))
            self.sources.append(item)
            self.by_class.setdefault(class_name, []).append(item)
            for name, value in item.constants.items():
                self.constants.setdefault(name, value)
                self.constants[f"{class_name}.{name}"] = value
                if item.package:
                    self.constants[f"{item.package}.{class_name}.{name}"] = value

    def resolve_class(self, class_name: str) -> JavaSource | None:
        simple = class_name.strip().split(".")[-1]
        candidates = self.by_class.get(simple, [])
        return candidates[0] if candidates else None
