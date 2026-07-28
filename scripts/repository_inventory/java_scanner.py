from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path

from .util import iter_files, java_constants, java_without_comments, posix, read_text


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
