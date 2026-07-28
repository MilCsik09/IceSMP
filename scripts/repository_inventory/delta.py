from __future__ import annotations

from typing import Any


def _index(items: list[dict[str, Any]]) -> dict[str, dict[str, Any]]:
    return {item["id"]: item for item in items}


def compare_group(base: list[dict[str, Any]], head: list[dict[str, Any]]) -> dict[str, Any]:
    left, right = _index(base), _index(head)
    added = sorted(set(right) - set(left))
    removed = sorted(set(left) - set(right))
    modified = []
    details = {}
    ignored = {"source_evidence", "registration_line", "line"}
    for stable_id in sorted(set(left) & set(right)):
        a = {k: v for k, v in left[stable_id].items() if k not in ignored}
        b = {k: v for k, v in right[stable_id].items() if k not in ignored}
        if a != b:
            modified.append(stable_id)
            details[stable_id] = {"before": a, "after": b}
    return {"added": added, "removed": removed, "modified": modified, "details": details}


def compare_inventories(base: dict[str, Any], head: dict[str, Any]) -> dict[str, Any]:
    groups = {
        "command_delta": (base.get("commands", []) + base.get("subcommands", []), head.get("commands", []) + head.get("subcommands", [])),
        "permission_delta": (base.get("permissions", []), head.get("permissions", [])),
        "config_delta": (base.get("config_keys", []), head.get("config_keys", [])),
        "message_delta": (base.get("message_keys", []), head.get("message_keys", [])),
        "feature_delta": (base.get("features", []), head.get("features", [])),
        "component_delta": (base.get("components", []), head.get("components", [])),
    }
    return {name: compare_group(*values) for name, values in groups.items()}
