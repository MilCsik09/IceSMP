#!/usr/bin/env python3
"""Check the internal WW-00 inventory; this is not runtime capability evidence."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE = Path("src/main/java/hu/taliann/icesmp")
MANIFEST = Path("docs/development/world-weaver/coverage.json")
SUFFIX = re.compile(r"(?:Manager|Service|Registry|Runtime|Coordinator|Authority|Catalog|Policy|Store)$")
LEVELS = {"FULL_PROVIDER", "INSPECT_ONLY_BY_DESIGN", "NO_RUNTIME_SURFACE", "OPTIONAL_FUTURE"}
ENTRYPOINTS = ("IceSMP.java", "IceSMPBootstrap.java", "core/IceSMPCore.java", "prologue/PrologueRuntime.java")


def inventory(root: Path) -> tuple[dict[str, str], dict[str, list[str]]]:
    sources = sorted((root / SOURCE).rglob("*.java"))
    by_name = {p.stem: p.relative_to(root).as_posix() for p in sources}
    authorities = {p.relative_to(root).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
                   for p in sources if SUFFIX.search(p.stem)}
    bootstrap = {}
    for entry in ENTRYPOINTS:
        path = SOURCE / entry
        text = (root / path).read_text(encoding="utf-8")
        names = re.findall(r"\bnew\s+([\w.]+)\s*\(", text)
        names += re.findall(r"\bprivate\s+(?:final|volatile)\s+([\w.]+)\s+\w+\s*;", text)
        bootstrap[path.as_posix()] = sorted({by_name[n.split(".")[-1]] for n in names
                                            if n.split(".")[-1] in by_name})
    return authorities, bootstrap


def validate(root: Path, data: dict, release: bool = False) -> list[str]:
    errors = []
    current, bootstrap = inventory(root)
    rows = data["authorities"]
    listed = {r["path"]: r for r in rows}
    if len(listed) != len(rows):
        errors.append("duplicate authority path")
    expected = set(current) | set(data["supplemental_authorities"])
    for path in sorted(expected - set(listed)):
        errors.append("unclassified authority: " + path)
    for path in sorted(set(listed) - expected):
        errors.append("stale authority: " + path)
    for path, row in listed.items():
        file = root / path
        if not file.is_file():
            errors.append("missing authority source: " + path)
        elif hashlib.sha256(file.read_bytes()).hexdigest() != row["source_sha256"]:
            errors.append("authority changed; re-audit required: " + path)
    if bootstrap != data["bootstrap_components"]:
        errors.append("bootstrap component inventory changed; coverage decision required")
    domains = {d["id"]: d for d in data["domains"]}
    if len(domains) != len(data["domains"]):
        errors.append("duplicate domain id")
    components = {p for paths in bootstrap.values() for p in paths}
    assignments = {r["path"]: r for r in data["bootstrap_assignments"]}
    if set(assignments) != components or len(assignments) != len(data["bootstrap_assignments"]):
        errors.append("unclassified or duplicate bootstrap component")
    for row in assignments.values():
        if not row["domains"] or any(d not in domains for d in row["domains"]):
            errors.append("invalid bootstrap domain assignment: " + row["path"])
    for row in rows:
        if row["domain"] not in domains:
            errors.append("unknown domain for " + row["path"])
    for domain in domains.values():
        if domain["status"] not in LEVELS:
            errors.append("invalid coverage level: " + domain["id"])
        if not domain.get("rationale", "").strip():
            errors.append("missing rationale: " + domain["id"])
        for route in domain["source_evidence"]:
            if not (root / route).is_file():
                errors.append("missing evidence source: " + route)
        if domain["status"] == "FULL_PROVIDER":
            if not domain.get("implementation_evidence"):
                errors.append("FULL_PROVIDER without implementation evidence: " + domain["id"])
    design = root / data["design"]["path"]
    if not design.is_file() or hashlib.sha256(design.read_bytes()).hexdigest() != data["design"]["sha256"]:
        errors.append("normative design differs from reviewed v2")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--release", action="store_true", help="Compatibility flag; optional domains never block release")
    args = parser.parse_args()
    data = json.loads((ROOT / MANIFEST).read_text(encoding="utf-8"))
    errors = validate(ROOT, data, args.release)
    for error in errors:
        print("FAIL: " + error)
    blockers = sum(d["status"] == "OPTIONAL_FUTURE" for d in data["domains"])
    print(f"WW-00 inventory: {len(data['authorities'])} authorities; "
          f"{len(data['domains'])} domains; {blockers} optional domains; {len(errors)} audit errors")
    print("This source inventory does not prove provider implementation or runtime safety.")
    return bool(errors)


if __name__ == "__main__":
    sys.exit(main())
