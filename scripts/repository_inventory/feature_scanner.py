from __future__ import annotations

import re
from pathlib import Path
from typing import Any

from .java_scanner import JavaIndex, JavaSource
from .models import Evidence, Finding
from .util import kebab

SUFFIX_CATEGORY = {
    "Command": "COMMAND", "Manager": "MANAGER", "Service": "SERVICE", "Listener": "LISTENER",
    "GUI": "GUI", "Holder": "GUI_HOLDER", "Factory": "ITEM_FACTORY", "Store": "PERSISTENT_STORE",
    "Repository": "PERSISTENT_STORE", "Bridge": "INTEGRATION", "Integration": "INTEGRATION",
    "Spell": "SPELL", "Quest": "QUEST", "Recipe": "RECIPE", "Reward": "REWARD",
}


def _category(src: JavaSource) -> tuple[str, str]:
    name = src.class_name
    for suffix, category in SUFFIX_CATEGORY.items():
        if name.endswith(suffix) and len(name) > len(suffix):
            return category, name[:-len(suffix)]
    package = src.package.split(".")[-1] if src.package else ""
    if package in ("storage", "persistence"): return "PERSISTENT_STORE", name
    if package in ("items",): return "ITEM", re.sub(r"Item$", "", name)
    if package in ("gui",): return "GUI_COMPONENT", name
    if package in ("spells",): return "SPELL_COMPONENT", re.sub(r"Spell$", "", name)
    return "COMPONENT", name


def _audience(category: str, source: str) -> list[str]:
    lower = source.lower()
    if category in ("PERSISTENT_STORE", "INTEGRATION"): return ["INTERNAL"]
    values: list[str] = []
    if any(x in lower for x in ("icesmp.admin", "icesmp.moder", "permission.admin", "permission.moder")):
        values.append("ADMIN" if "admin" in lower else "MODERATOR")
    if category in ("COMMAND", "MANAGER", "SERVICE", "LISTENER", "GUI", "GUI_HOLDER", "ITEM_FACTORY", "ITEM", "SPELL", "QUEST", "RECIPE", "REWARD"):
        values.insert(0, "PLAYER")
    return list(dict.fromkeys(values or ["INTERNAL"]))


def scan_components_and_features(root: Path, index: JavaIndex, commands: list[dict[str, Any]], permissions: list[dict[str, Any]], manifest: dict[str, Any]) -> tuple[list[dict[str, Any]], list[dict[str, Any]], list[Finding]]:
    components: list[dict[str, Any]] = []
    features: dict[str, dict[str, Any]] = {}
    findings: list[Finding] = []
    command_by_impl = {item.get("implementation"): item for item in commands}
    manifest_components = manifest.get("components", {})
    ignores = manifest.get("explicit-ignores", {})

    for src in index.sources:
        if src.class_name.endswith(("Test", "Tests", "RegressionTest", "RegressionSuite")):
            audience = ["TESTER"]
        else:
            category, stem = _category(src)
            audience = _audience(category, src.source)
        category, stem = _category(src)
        component_id = f"component.{src.package}.{src.class_name}" if src.package else f"component.{src.class_name}"
        manifest_entry = manifest_components.get(component_id, {})
        ignored = ignores.get(component_id)
        feature_id = manifest_entry.get("feature") if isinstance(manifest_entry, dict) else None
        if not feature_id and category != "COMPONENT":
            feature_id = f"feature.{kebab(stem)}"
        confidence = "HIGH" if manifest_entry else ("MEDIUM" if category != "COMPONENT" else "REVIEW_REQUIRED")
        if ignored:
            audience = ["OUT_OF_SCOPE"]
            confidence = "HIGH"
        elif category == "COMPONENT" and not manifest_entry:
            findings.append(Finding("REVIEW_REQUIRED", "UNCLASSIFIED_COMPONENT",
                                    f"Production component {src.package}.{src.class_name} has no deterministic feature classification.",
                                    component_id, (Evidence(src.relative, 1, src.class_name),)))
        command = command_by_impl.get(src.class_name)
        if command:
            audience = command.get("audience", audience)
            feature_id = feature_id or f"feature.{kebab(stem)}"
        component = {"id": component_id, "class": f"{src.package}.{src.class_name}" if src.package else src.class_name,
                     "source": src.relative, "category": category, "audience": audience,
                     "feature": feature_id or "", "confidence": confidence,
                     "ignore_reason": ignored.get("reason", "") if isinstance(ignored, dict) else ""}
        components.append(component)
        if feature_id:
            feature = features.setdefault(feature_id, {"id": feature_id, "name": feature_id.split("feature.", 1)[-1],
                "technical_description": "Automatically grouped production components; finalize wording/classification in documentation manifest.",
                "sources": [], "commands": [], "permissions": [], "config_sections": [], "guis": [], "message_keys": [],
                "persistence": [], "documentation": [], "audience": [], "confidence": "MEDIUM"})
            feature["sources"].append(component["class"])
            feature["audience"].extend(audience)
            if category == "GUI": feature["guis"].append(component["class"])
            if category == "PERSISTENT_STORE": feature["persistence"].append(component["class"])
            if command: feature["commands"].append(command["id"])

    manifest_features = manifest.get("features", {})
    # A deliberately componentless feature is still a valid documentation
    # concept (for example, a lore-only plan explicitly classified as not
    # implemented). Requiring an opt-in flag preserves stale-entry detection:
    # arbitrary old manifest features are not silently promoted into inventory.
    for feature_id, entry in manifest_features.items():
        if feature_id in features or not isinstance(entry, dict) or entry.get("componentless") is not True:
            continue
        features[feature_id] = {
            "id": feature_id,
            "name": feature_id.split("feature.", 1)[-1],
            "technical_description": entry.get(
                "description",
                "Explicitly documented componentless feature.",
            ),
            "sources": [],
            "commands": [],
            "permissions": [],
            "config_sections": [],
            "guis": [],
            "message_keys": [],
            "persistence": [],
            "documentation": entry.get("docs", []),
            "audience": entry.get("audience", ["INTERNAL"]),
            "confidence": "HIGH",
        }
    for feature_id, feature in features.items():
        entry = manifest_features.get(feature_id, {})
        if isinstance(entry, dict):
            feature["documentation"] = entry.get("docs", [])
            feature["audience"] = entry.get("audience", feature["audience"])
            feature["technical_description"] = entry.get("description", feature["technical_description"])
            feature["confidence"] = "HIGH"
        feature["sources"] = sorted(set(feature["sources"]))
        feature["commands"] = sorted(set(feature["commands"]))
        feature["guis"] = sorted(set(feature["guis"]))
        feature["persistence"] = sorted(set(feature["persistence"]))
        feature["audience"] = sorted(set(feature["audience"]))
    return sorted(features.values(), key=lambda x: x["id"]), sorted(components, key=lambda x: x["id"]), findings
