from __future__ import annotations

from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from . import TOOL_VERSION
from .command_scanner import scan_commands
from .config_scanner import scan_config
from .documentation_scanner import check_coverage
from .feature_scanner import scan_components_and_features
from .java_scanner import JavaIndex
from .message_scanner import scan_messages
from .models import Finding, finding_counts
from .permission_scanner import scan_permissions
from .util import git, load_manifest, stable_digest


def generate_inventory(root: Path, mode: str = "report") -> dict[str, Any]:
    root = root.resolve()
    manifest_path = root / "docs/documentation-manifest.yml"
    try:
        manifest = load_manifest(manifest_path)
    except ValueError as exc:
        manifest = {"version": 1, "commands": {}, "features": {}, "permissions": {}, "config-sections": {}, "components": {}, "explicit-ignores": {}}
        manifest_error = Finding("FAIL", "MANIFEST_SYNTAX_ERROR", str(exc), "manifest")
    else:
        manifest_error = None
    index = JavaIndex(root)
    commands, subcommands, command_findings = scan_commands(root, index, manifest)
    permissions, permission_findings = scan_permissions(root, index, commands, subcommands, manifest)
    config_keys, config_findings = scan_config(root, index, manifest)
    message_keys, message_findings = scan_messages(root, index)
    features, components, component_findings = scan_components_and_features(root, index, commands, permissions, manifest)
    findings = [*command_findings, *permission_findings, *config_findings, *message_findings, *component_findings]
    if manifest_error: findings.insert(0, manifest_error)
    ids = [x["id"] for group in (commands, subcommands, permissions, config_keys, message_keys, features, components) for x in group]
    for stable_id in sorted(set(ids)):
        if ids.count(stable_id) > 1:
            findings.append(Finding("FAIL", "DUPLICATE_STABLE_ID", f"Duplicate stable ID across inventory: {stable_id}", stable_id))
    try:
        sha = git(root, "rev-parse", "HEAD")
    except Exception:
        sha = "UNKNOWN"
    inventory: dict[str, Any] = {
        "schema_version": 1, "tool_version": TOOL_VERSION,
        "generated_at": datetime.now(timezone.utc).replace(microsecond=0).isoformat(),
        "repository_root": ".", "git_sha": sha,
        "commands": commands, "subcommands": subcommands, "permissions": permissions,
        "config_keys": config_keys, "message_keys": message_keys, "features": features, "components": components,
        "findings": [item.to_dict() for item in sorted(findings)],
    }
    coverage = check_coverage(root, inventory, manifest, mode)
    inventory["documentation_coverage"] = coverage
    inventory["findings"].extend(coverage["findings"])
    inventory["findings"] = sorted(inventory["findings"], key=lambda x: (x["severity"], x["code"], x.get("stable_id", ""), x["message"]))
    counts = {
        "root_commands": len(commands), "subcommands": len(subcommands),
        "aliases": sum(len(x.get("aliases", [])) for x in commands), "permissions": len(permissions),
        "config_keys": len(config_keys), "message_keys": len(message_keys), "features": len(features), "components": len(components),
        "player_features": sum(1 for x in features if "PLAYER" in x.get("audience", [])),
        "admin_features": sum(1 for x in features if set(x.get("audience", [])) & {"ADMIN", "MODERATOR"}),
        "unclassified": sum(1 for x in components if x.get("confidence") == "REVIEW_REQUIRED"),
        "undocumented_commands": sum(1 for x in coverage["undocumented"] if x.startswith("command.") or x.startswith("alias.")),
        "undocumented_features": sum(1 for x in coverage["undocumented"] if x.startswith("feature.")),
    }
    counts.update({f"findings_{k.lower()}": v for k, v in finding_counts(inventory["findings"]).items()})
    inventory["counts"] = counts
    fingerprint_source = {k: v for k, v in inventory.items() if k not in ("generated_at",)}
    fingerprint_source["git_sha"] = sha
    inventory["deterministic_fingerprint"] = stable_digest(str(fingerprint_source), 24)
    return inventory
