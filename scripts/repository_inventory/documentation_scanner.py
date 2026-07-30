from __future__ import annotations

import re
from pathlib import Path
from typing import Any

from .models import Evidence, Finding
from .util import iter_files, posix, read_text

MARKER = re.compile(r"<!--\s*icesmp-doc-id:\s*([^\s>]+)\s*-->")


def marker_index(root: Path) -> dict[str, list[dict[str, Any]]]:
    result: dict[str, list[dict[str, Any]]] = {}
    docs = root / "docs"
    if not docs.exists(): return result
    for path in sorted(iter_files(docs, ("*.md",))):
        text = read_text(path)
        for match in MARKER.finditer(text):
            result.setdefault(match.group(1), []).append({"file": posix(path, root), "line": text.count("\n", 0, match.start()) + 1})
    return result


def _entry_docs(entry: Any) -> list[str]:
    return entry.get("docs", []) if isinstance(entry, dict) else []


def _artifact_backed_sections(manifest: dict[str, Any]) -> set[str]:
    """Return inventory sections whose exact reference lives in the CI artifact.

    Human guides should explain systems and workflows, not carry hundreds of
    invisible stable-ID comments.  The manifest remains the fail-closed mapping,
    while ``write_repository_reports`` renders the exact command, permission,
    config and component reference into the downloadable Repository Docs
    Inventory artifact.
    """
    policy = manifest.get("documentation-policy", {})
    sections = policy.get("artifact-backed-sections", []) if isinstance(policy, dict) else []
    if not isinstance(sections, list) or not all(isinstance(item, str) for item in sections):
        raise ValueError("documentation-policy.artifact-backed-sections must be a string list")
    allowed = {"commands", "permissions", "config-sections", "components"}
    unknown = sorted(set(sections) - allowed)
    if unknown:
        raise ValueError(f"Unsupported artifact-backed documentation section(s): {', '.join(unknown)}")
    return set(sections)


def check_coverage(root: Path, inventory: dict[str, Any], manifest: dict[str, Any], mode: str) -> dict[str, Any]:
    findings: list[Finding] = []
    markers = marker_index(root)
    artifact_backed = _artifact_backed_sections(manifest)
    required: dict[str, tuple[str, dict[str, Any]]] = {}
    for command in inventory.get("commands", []):
        required[command["id"]] = ("commands", command)
    root_aliases = inventory.get("root_aliases")
    if root_aliases is None:
        root_aliases = [
            {"id": f"alias.{command['name']}.{alias}"}
            for command in inventory.get("commands", [])
            for alias in command.get("aliases", [])
        ]
    for alias in root_aliases:
        required[alias["id"]] = ("commands", alias)
    routes = inventory.get("routes", inventory.get("subcommands", []))
    for command in routes:
        required[command["id"]] = ("commands", command)
    for alias in inventory.get("routing_aliases", []):
        required[alias["id"]] = ("commands", alias)
    for permission in inventory.get("permissions", []): required[permission["id"]] = ("permissions", permission)
    for feature in inventory.get("features", []):
        if set(feature.get("audience", [])) & {"PLAYER", "MODERATOR", "ADMIN", "TESTER"}:
            required[feature["id"]] = ("features", feature)
    for key in inventory.get("config_keys", []):
        section_id = f"config.{key['section']}"
        required.setdefault(section_id, ("config-sections", {"id": section_id}))
    for component in inventory.get("components", []):
        if component.get("audience") != ["OUT_OF_SCOPE"]:
            required[component["id"]] = ("components", component)

    undocumented: list[str] = []
    missing_markers: list[str] = []
    bad_paths: list[str] = []
    for stable_id, (section, _) in sorted(required.items()):
        entry = manifest.get(section, {}).get(stable_id)
        docs = _entry_docs(entry)
        if not entry:
            severity = "FAIL" if mode == "strict" else ("REVIEW_REQUIRED" if section in ("features", "components") else "WARN")
            findings.append(Finding(severity, "UNDOCUMENTED_INVENTORY_ITEM",
                                    f"{stable_id} has no {section} manifest entry.", stable_id))
            undocumented.append(stable_id)
            continue
        for doc in docs:
            if not (root / doc).is_file():
                findings.append(Finding("FAIL" if mode == "strict" else "WARN", "DOC_PATH_MISSING",
                                        f"Manifest path does not exist: {doc}", stable_id, (Evidence(doc, 1, stable_id),)))
                bad_paths.append(doc)
        if docs and stable_id not in markers and section not in artifact_backed:
            findings.append(Finding("FAIL" if mode == "strict" else "WARN", "DOC_MARKER_MISSING",
                                    f"No icesmp-doc-id marker found for {stable_id}.", stable_id))
            missing_markers.append(stable_id)
        if stable_id in markers and docs:
            marker_files = {x["file"] for x in markers[stable_id]}
            if not marker_files.intersection(docs):
                findings.append(Finding("FAIL" if mode == "strict" else "WARN", "DOC_MARKER_WRONG_FILE",
                                        f"Marker for {stable_id} is not in a manifest-listed file.", stable_id))

    # Public/staff audience filtering decides which features require reader
    # documentation, not whether a valid internal or componentless feature is
    # known to the inventory. Keep the full feature/component ID set for stale
    # manifest detection so explicitly classified internal entries are not
    # reported as stale.
    inventory_ids = set(required)
    inventory_ids.update(x["id"] for x in inventory.get("features", []))
    inventory_ids.update(x["id"] for x in inventory.get("components", []))
    for section in ("commands", "features", "permissions", "config-sections", "components"):
        for stable_id in manifest.get(section, {}):
            if stable_id.startswith("_"):
                continue
            if stable_id not in inventory_ids:
                findings.append(Finding("FAIL" if mode == "strict" else "WARN", "STALE_MANIFEST_ENTRY",
                                        f"Manifest entry {stable_id} no longer exists in inventory.", stable_id))
    for stable_id, locations in markers.items():
        if len(locations) > 1:
            findings.append(Finding("FAIL" if mode == "strict" else "WARN", "DUPLICATE_DOC_MARKER",
                                    f"Documentation marker {stable_id} appears in multiple locations.", stable_id,
                                    tuple(Evidence(x["file"], x["line"], stable_id) for x in locations)))

    def percentage(documented: int, total: int) -> float:
        return 100.0 if total == 0 else round(documented * 100.0 / total, 2)
    command_ids = [x["id"] for x in inventory.get("commands", [])]
    sub_ids = [x["id"] for x in routes]
    root_alias_ids = [x["id"] for x in root_aliases]
    routing_alias_ids = [x["id"] for x in inventory.get("routing_aliases", [])]
    alias_ids = [*root_alias_ids, *routing_alias_ids]
    player_features = [x["id"] for x in inventory.get("features", []) if "PLAYER" in x.get("audience", [])]
    admin_features = [x["id"] for x in inventory.get("features", []) if set(x.get("audience", [])) & {"ADMIN", "MODERATOR"}]
    permission_ids = [x["id"] for x in inventory.get("permissions", [])]
    def doc_count(ids: list[str], section: str) -> int:
        return sum(
            1
            for stable_id in ids
            if manifest.get(section, {}).get(stable_id)
            and (stable_id in markers or section in artifact_backed)
        )
    metrics = {
        "commands_documented": percentage(doc_count(command_ids, "commands"), len(command_ids)),
        "subcommands_documented": percentage(doc_count(sub_ids, "commands"), len(sub_ids)),
        "root_aliases_documented": percentage(doc_count(root_alias_ids, "commands"), len(root_alias_ids)),
        "routing_aliases_documented": percentage(doc_count(routing_alias_ids, "commands"), len(routing_alias_ids)),
        "aliases_documented": percentage(doc_count(alias_ids, "commands"), len(alias_ids)),
        "player_features_documented": percentage(doc_count(player_features, "features"), len(player_features)),
        "admin_features_documented": percentage(doc_count(admin_features, "features"), len(admin_features)),
        "permissions_documented": percentage(doc_count(permission_ids, "permissions"), len(permission_ids)),
        "config_sections_classified": percentage(sum(1 for x in {f"config.{k['section']}" for k in inventory.get('config_keys', [])} if x in manifest.get("config-sections", {})), len({k['section'] for k in inventory.get('config_keys', [])})),
        "artifact_backed_sections": len(artifact_backed),
        "unclassified_components": sum(1 for x in inventory.get("components", []) if x.get("confidence") == "REVIEW_REQUIRED"),
        "review_required_findings": sum(1 for x in [*inventory.get("findings", []), *[f.to_dict() for f in findings]] if x.get("severity") == "REVIEW_REQUIRED"),
    }
    finding_dicts = [item.to_dict() for item in findings]
    return {"mode": mode, "metrics": metrics, "artifact_backed_sections": sorted(artifact_backed),
            "undocumented": undocumented, "missing_markers": missing_markers,
            "bad_paths": sorted(set(bad_paths)), "markers": markers, "findings": finding_dicts}
