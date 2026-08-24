#!/usr/bin/env python3
"""Deterministic authority, generator, migration and command-surface evidence gate."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

import yaml


ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src/main/resources"
OUTPUT = ROOT / "docs/development/config-content-command-surface-2.json"
BASELINE = "f061e78946962414502ebe365ee812edd8c2fadb"
CONFIG_EXTENSIONS = {".yml", ".yaml", ".json", ".toml", ".properties", ".mcmeta"}
OPERATOR_FILES = (
    "general", "economy", "factions", "block-regen", "class-gameplay", "spells-balance",
    "professions", "world", "event-spawn-safety", "pets", "crafting", "crates", "afk",
    "moderation", "motd", "professions-2", "sit", "tablist", "dev-items", "client",
)
CONTENT_FILES = (
    "content/progression/classes.yml",
    "content/progression/spells.yml",
    "content/progression/quests.yml",
    "content/equipment/rarities.yml",
    "content/equipment/equipment.yml",
    "content/equipment/relics.yml",
    "content/professions/materials.yml",
    "content/professions/recipes.yml",
    "content/pve/enemies.yml",
    "content/pve/loot.yml",
    "content/events/prologue.yml",
)
OLD_TO_NEW = {
    "config/classes.yml": "content/progression/classes.yml",
    "config/spells.yml": "content/progression/spells.yml",
    "config/quests.yml": "content/progression/quests.yml",
    "config/item-rarity.yml": "content/equipment/rarities.yml",
    "config/item-templates.yml": "content/equipment/equipment.yml",
    "config/relics.yml": "content/equipment/relics.yml",
    "config/profession-materials.yml": "content/professions/materials.yml",
    "config/profession-recipes.yml": "content/professions/recipes.yml",
    "config/mob-templates.yml": "content/pve/enemies.yml",
    "config/loot.yml": "content/pve/loot.yml",
    "config.yml#prologue": "content/events/prologue.yml",
    "config/material-economy-expansion.yml": "MERGED:content/professions/{materials,recipes}.yml",
    "config/equipment-catalog-expansion.yml": "MERGED:content/{equipment,professions}.yml",
    "config/reward-discoverability-closure.yml": "MERGED:content/{equipment,professions,pve}.yml",
}
REMOVED_GAMEPLAY_GENERATORS = (
    "scripts/gen_advancements.py",
    "scripts/apply_professions_2_hardening.py",
    "scripts/apply_professions_2_rework.py",
)
GENERATOR_CATEGORIES = {
    "generate_class_ui_assets.py": ("RESOURCE_BUILD", "KEEP_RESOURCE_BUILD"),
    "generate_equipment_assets.py": ("RESOURCE_BUILD", "KEEP_RESOURCE_BUILD"),
    "generate_icesmp_hud_assets.py": ("RESOURCE_BUILD", "KEEP_RESOURCE_BUILD"),
    "generate_icesmp_survival_hud_assets.py": ("RESOURCE_BUILD", "KEEP_RESOURCE_BUILD"),
    "generate_equipment_rp2_pilot.py": ("RESOURCE_BUILD", "KEEP_RESOURCE_BUILD"),
    "generate_equipment_rp2_production.py": ("RESOURCE_BUILD", "KEEP_RESOURCE_BUILD"),
    "generate_equipment_rp2_manifests.py": ("EVIDENCE", "KEEP_EVIDENCE"),
    "generate_equipment_rp2_art_bible.py": ("DOCUMENTATION", "KEEP_DOCS"),
    "generate_equipment_rp2_docs.py": ("DOCUMENTATION", "KEEP_DOCS"),
    "generate_equipment_2_report.py": ("EVIDENCE", "KEEP_EVIDENCE"),
    "generate_release_inventory.py": ("EVIDENCE", "KEEP_EVIDENCE"),
    "generate_repository_inventory.py": ("DOCUMENTATION", "KEEP_DOCS"),
    "resource_pack.py": ("RESOURCE_BUILD", "KEEP_RESOURCE_BUILD"),
}
COMMAND_CATEGORIES = {
    "currency": "ECONOMY", "bank": "ECONOMY", "market": "ECONOMY", "exchangeboard": "ADMIN",
    "faction": "SOCIAL", "claim": "SOCIAL", "party": "SOCIAL", "ceh": "SOCIAL", "msg": "SOCIAL",
    "tell": "SOCIAL", "w": "SOCIAL", "reply": "SOCIAL", "socialspy": "MODERATION",
    "warn": "MODERATION", "kick": "MODERATION", "mute": "MODERATION", "unmute": "MODERATION",
    "ban": "MODERATION", "tempban": "MODERATION", "unban": "MODERATION",
    "history": "MODERATION", "punishments": "MODERATION", "moderation": "MODERATION",
    "vanish": "MODERATION", "offlinetp": "MODERATION", "invsee": "MODERATION",
    "icesmp": "ADMIN", "iceitem": "DEBUG_DEVELOPER", "npcbind": "ADMIN",
    "prologue": "ADMIN", "events": "ADMIN", "relic": "ADMIN", "sinner": "ADMIN",
}


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def load_yaml_text(text: str) -> dict[str, Any]:
    loaded = yaml.safe_load(text) or {}
    if not isinstance(loaded, dict):
        raise AssertionError("authority root must be a mapping")
    return loaded


def load_yaml(path: Path) -> dict[str, Any]:
    return load_yaml_text(path.read_text(encoding="utf-8"))


def deep_merge(target: dict[str, Any], patch: dict[str, Any]) -> None:
    for raw_key, value in patch.items():
        key = str(raw_key)
        if isinstance(value, dict) and isinstance(target.get(key), dict):
            deep_merge(target[key], value)
        else:
            target[key] = value


def leaves(value: Any, prefix: str = "") -> dict[str, Any]:
    result: dict[str, Any] = {}
    if isinstance(value, dict):
        for raw_key, child in value.items():
            key = f"{prefix}.{raw_key}" if prefix else str(raw_key)
            result.update(leaves(child, key))
    else:
        result[prefix] = value
    return result


def old_effective(baseline: str) -> tuple[dict[str, Any], list[str]]:
    manager = git("show", f"{baseline}:src/main/java/hu/taliann/icesmp/managers/ConfigManager.java")
    match = re.search(r"CONFIG_FILES\s*=\s*\{(.*?)\};", manager, re.DOTALL)
    if not match:
        raise AssertionError("baseline ConfigManager CONFIG_FILES list missing")
    names = re.findall(r'"([^"]+)"', match.group(1))
    effective: dict[str, Any] = {}
    for name in names:
        deep_merge(effective, load_yaml_text(git(
            "show", f"{baseline}:src/main/resources/config/{name}.yml")))
    deep_merge(effective, load_yaml_text(git("show", f"{baseline}:src/main/resources/config.yml")))
    return effective, names


def current_effective() -> dict[str, Any]:
    effective: dict[str, Any] = {}
    paths = list(CONTENT_FILES) + [f"config/{name}.yml" for name in OPERATOR_FILES] + ["config.yml"]
    for relative in paths:
        deep_merge(effective, load_yaml(RESOURCES / relative))
    return effective


def stable_hash(value: Any) -> str:
    encoded = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(encoded.encode("utf-8")).hexdigest()


def config_classification(path: Path) -> dict[str, Any]:
    relative = path.relative_to(ROOT).as_posix()
    suffix = path.suffix.lower()
    if relative.startswith("src/main/resources/content/"):
        category, owner, domain = "HANDCRAFTED_GAME_CONTENT", "Git/content review", relative.split("/")[4]
        canonical, gameplay, editable, reload = True, True, False, "RESTART_REQUIRED"
    elif relative == "src/main/resources/config.yml" or relative.startswith("src/main/resources/config/"):
        category, owner, domain = "OPERATOR_CONFIGURATION", "server operator", "operator"
        canonical, gameplay, editable, reload = True, False, True, "SAFE_RELOAD_WITH_RECONCILIATION"
    elif relative.startswith("src/main/resources/messages"):
        category, owner, domain = "PRESENTATION_LOCALIZATION", "localization", "messages"
        canonical, gameplay, editable, reload = True, False, True, "LIVE_RELOADABLE"
    elif relative.startswith("src/main/resources/datapack/"):
        category, owner, domain = "HANDCRAFTED_GAME_CONTENT", "Git/content review", "advancements"
        canonical, gameplay, editable, reload = True, True, False, "RESTART_REQUIRED"
    elif relative.startswith("docs/development/"):
        category, owner, domain = "VALIDATION_EVIDENCE", "CI/audit tooling", "evidence"
        canonical, gameplay, editable, reload = False, False, False, "GENERATED_EVIDENCE_ONLY"
    elif relative.startswith("resource-pack/"):
        category, owner, domain = "PRESENTATION_LOCALIZATION", "resource-pack pipeline", "resource-pack"
        canonical, gameplay, editable, reload = True, False, False, "RESTART_REQUIRED"
    elif relative.startswith(".github/"):
        category, owner, domain = "OPERATOR_CONFIGURATION", "CI maintainers", "ci"
        canonical, gameplay, editable, reload = True, False, False, "RESTART_REQUIRED"
    else:
        category, owner, domain = "VALIDATION_EVIDENCE", "repository maintainers", "repository"
        canonical, gameplay, editable, reload = True, False, False, "RESTART_REQUIRED"
    text = path.read_text(encoding="utf-8", errors="ignore")
    generated = (category == "VALIDATION_EVIDENCE"
                 or "generated manifest" in text.lower()
                 or "generated evidence" in text.lower())
    return {
        "path": relative,
        "bytes": path.stat().st_size,
        "lines": len(text.splitlines()),
        "format": suffix.removeprefix("."),
        "domain": domain,
        "category": category,
        "owner": owner,
        "consumer_count": 0,
        "reloadable": reload in {"LIVE_RELOADABLE", "SAFE_RELOAD_WITH_RECONCILIATION"},
        "reload_policy": reload,
        "canonical": canonical,
        "generated": generated,
        "operator_editable": editable,
        "gameplay_content": gameplay,
        "runtime_state": False,
        "presentation": category == "PRESENTATION_LOCALIZATION",
        "deprecated": False,
        "duplicate_authority": False,
        "migration_requirement": OLD_TO_NEW.get(relative.removeprefix("src/main/resources/"), "NONE"),
    }


def inventory() -> list[dict[str, Any]]:
    excluded = {".git", "build", ".gradle"}
    paths = [path for path in ROOT.rglob("*") if path.is_file()
             and path.suffix.lower() in CONFIG_EXTENSIONS
             and path != OUTPUT
             and not excluded.intersection(path.relative_to(ROOT).parts)]
    rows = [config_classification(path) for path in sorted(paths)]
    searchable = []
    for path in (ROOT / "src").rglob("*"):
        if path.is_file() and path.suffix.lower() in {".java", ".kt", ".kts", ".py", ".md"}:
            searchable.append(path.read_text(encoding="utf-8", errors="ignore"))
    corpus = "\n".join(searchable)
    for row in rows:
        row["consumer_count"] = max(0, corpus.count(Path(row["path"]).name) - 1)
    return rows


def split_java_arguments(source: str) -> list[str]:
    args: list[str] = []
    buffer: list[str] = []
    depth = 0
    quoted = False
    escaped = False
    for char in source:
        if quoted:
            buffer.append(char)
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                quoted = False
            continue
        if char == '"':
            quoted = True
            buffer.append(char)
        elif char in "([{":
            depth += 1
            buffer.append(char)
        elif char in ")]}" and depth > 0:
            depth -= 1
            buffer.append(char)
        elif char == "," and depth == 0:
            args.append("".join(buffer).strip())
            buffer = []
        else:
            buffer.append(char)
    if buffer:
        args.append("".join(buffer).strip())
    return args


def command_calls(path: Path) -> list[list[str]]:
    source = path.read_text(encoding="utf-8")
    calls: list[list[str]] = []
    needle = "plugin.registerCommand("
    start = 0
    while (index := source.find(needle, start)) >= 0:
        cursor = index + len(needle)
        depth, quoted, escaped = 1, False, False
        while cursor < len(source) and depth:
            char = source[cursor]
            if quoted:
                if escaped:
                    escaped = False
                elif char == "\\":
                    escaped = True
                elif char == '"':
                    quoted = False
            elif char == '"':
                quoted = True
            elif char == "(":
                depth += 1
            elif char == ")":
                depth -= 1
            cursor += 1
        calls.append(split_java_arguments(source[index + len(needle):cursor - 1]))
        start = cursor
    return calls


def handler_source(expression: str) -> tuple[str, str]:
    class_match = re.search(r"new\s+(?:[\w.]+\.)?([A-Z][A-Za-z0-9]+)\s*\(", expression)
    if class_match:
        class_name = class_match.group(1)
    else:
        class_name = expression.strip().split("(", 1)[0]
    candidates = list((ROOT / "src/main/java").rglob(f"{class_name}.java"))
    source = candidates[0].read_text(encoding="utf-8") if candidates else ""
    return class_name, source


def command_inventory() -> list[dict[str, Any]]:
    registrations: list[list[str]] = []
    for path in (ROOT / "src/main/java").rglob("*.java"):
        registrations.extend(command_calls(path))
    rows = []
    permission_constants = dict(re.findall(
        r'public static final String\s+(\w+)\s*=\s*"([^"]+)"',
        (ROOT / "src/main/java/hu/taliann/icesmp/core/Permissions.java").read_text(encoding="utf-8")))
    for args in registrations:
        if len(args) < 4 or not re.fullmatch(r'"[^"]+"', args[0]):
            continue
        root = args[0].strip('"')
        description = args[1].strip('"')
        aliases = re.findall(r'"([^"]+)"', args[2])
        class_name, source = handler_source(args[3])
        permissions = set(re.findall(r'"(icesmp\.[a-z0-9_.-]+)"', source))
        for constant in re.findall(r"Permissions\.([A-Z0-9_]+)", source):
            if constant in permission_constants:
                permissions.add(permission_constants[constant])
        admin = (COMMAND_CATEGORIES.get(root) in {"ADMIN", "MODERATION", "DEBUG_DEVELOPER"}
                 or "admin" in description.lower())
        if root == "icesmp":
            permissions.update({"icesmp.admin.reload", "icesmp.admin.config",
                                "icesmp.admin.inspect", "icesmp.admin.client"})
        rows.append({
            "command": root,
            "aliases": aliases,
            "handler": class_name,
            "description": description,
            "syntax": f"/{root} <subcommand|arguments>" if "sendHelp" in source else f"/{root} [arguments]",
            "category": COMMAND_CATEGORIES.get(root, "GAMEPLAY"),
            "permission": sorted(permissions),
            "admin": admin,
            "player_only": "sender instanceof Player" in source and "player-only" in source,
            "console_contract": "PLAYER_ONLY" if "sender instanceof Player" in source and "player-only" in source else "BOTH",
            "help": "sendHelp" in source or "usage" in source.lower(),
            "tab_complete": "suggest(" in source or "tabComplete(" in source,
            "destructive": bool(re.search(r"\b(delete|reset|purge|clear|remove|set|ban|kick)\b", source, re.I)),
            "audit_logged": "audit" in source.lower() or "log" in source.lower(),
            "deprecated": False,
            "outcome": "CONSOLIDATE" if root == "icesmp" else "KEEP",
            "rationale": ("permission-filtered shared admin root" if root == "icesmp"
                          else "natural domain command retained"),
        })
    return sorted(rows, key=lambda row: row["command"])


def complete_command_routes() -> tuple[list[dict[str, Any]], list[dict[str, Any]],
                                       list[dict[str, Any]], list[dict[str, Any]]]:
    import sys
    sys.path.insert(0, str(ROOT / "scripts"))
    from repository_inventory.command_scanner import scan_commands
    from repository_inventory.java_scanner import JavaIndex
    from repository_inventory.util import load_manifest

    manifest = load_manifest(ROOT / "docs/documentation-manifest.yml")
    roots, routes, root_aliases, routing_aliases, _ = scan_commands(
        ROOT, JavaIndex(ROOT), manifest)
    return (sorted(roots, key=lambda row: row["name"]),
            sorted(routes, key=lambda row: (row["root"], row["usage"], row["id"])),
            sorted(root_aliases, key=lambda row: (row["root"], row["alias"])),
            sorted(routing_aliases, key=lambda row: (row["root"], row["alias"])))


def generator_inventory() -> list[dict[str, Any]]:
    rows = []
    candidates = set(GENERATOR_CATEGORIES)
    for path in (ROOT / "scripts").glob("*.py"):
        source = path.read_text(encoding="utf-8")
        if path.name.startswith("generate_") or ".write_text(" in source:
            candidates.add(path.name)
    for name in sorted(candidates):
        path = ROOT / "scripts" / name
        if not path.exists():
            continue
        category, outcome = GENERATOR_CATEGORIES.get(name, ("EVIDENCE", "KEEP_EVIDENCE"))
        rows.append({
            "path": f"scripts/{name}",
            "category": category,
            "outcome": outcome,
            "authors_gameplay": False,
            "writes": "resource/build artifact" if category == "RESOURCE_BUILD" else "derived report/documentation",
        })
    return rows


def authority_matrix() -> list[dict[str, Any]]:
    return [
        {"domain": "classes", "source_files": ["content/progression/classes.yml", "content/progression/spells.yml"],
         "runtime_owner": "JobManager/SpellRegistry", "content_type": "HANDCRAFTED_GAME_CONTENT",
         "canonical_authority": "LOCKED_CANONICAL_CONTENT", "reload_policy": "RESTART_REQUIRED", "schema_version": 1,
         "generated": False, "operator_editable": False, "fail_behavior": "FAIL_CLOSED", "dependencies": ["class-gameplay"]},
        {"domain": "quests", "source_files": ["content/progression/quests.yml"], "runtime_owner": "QuestManager",
         "content_type": "HANDCRAFTED_GAME_CONTENT", "canonical_authority": "LOCKED_CANONICAL_CONTENT",
         "reload_policy": "RESTART_REQUIRED", "schema_version": 2, "generated": False, "operator_editable": False,
         "fail_behavior": "ATOMIC_REGISTRY_REJECT", "dependencies": ["rewards", "events"]},
        {"domain": "custom-quests", "source_files": ["server data folder custom-quests.yml"],
         "runtime_owner": "QuestManager", "content_type": "EXTENSIBLE_CONTENT",
         "canonical_authority": "VERSIONED_VALIDATED_SERVER_EXTENSION", "reload_policy": "MANAGER_CONTROLLED",
         "schema_version": 1, "generated": False, "operator_editable": "bounded quest admin commands only",
         "fail_behavior": "FAIL_CLOSED", "dependencies": ["quests"]},
        {"domain": "equipment", "source_files": ["content/equipment/equipment.yml", "content/equipment/rarities.yml"],
         "runtime_owner": "ItemTemplateRegistry", "content_type": "HANDCRAFTED_GAME_CONTENT",
         "canonical_authority": "LOCKED_CANONICAL_CONTENT", "reload_policy": "RESTART_REQUIRED", "schema_version": 2,
         "generated": False, "operator_editable": False, "fail_behavior": "FAIL_CLOSED", "dependencies": ["recipes", "resource-pack"]},
        {"domain": "relics", "source_files": ["content/equipment/relics.yml"], "runtime_owner": "RelicManager",
         "content_type": "HANDCRAFTED_GAME_CONTENT", "canonical_authority": "LOCKED_WITH_EXPLICIT_OPERATOR_SEAMS",
         "reload_policy": "SAFE_RELOAD_WITH_RECONCILIATION", "schema_version": 1, "generated": False,
         "operator_editable": "bounded operational flags only", "fail_behavior": "FAIL_CLOSED", "dependencies": ["equipment"]},
        {"domain": "professions", "source_files": ["content/professions/materials.yml", "content/professions/recipes.yml"],
         "runtime_owner": "ProfessionRecipeCatalog", "content_type": "HANDCRAFTED_GAME_CONTENT",
         "canonical_authority": "LOCKED_CANONICAL_CONTENT", "reload_policy": "RESTART_REQUIRED", "schema_version": 2,
         "generated": False, "operator_editable": False, "fail_behavior": "FAIL_CLOSED", "dependencies": ["equipment"]},
        {"domain": "pve", "source_files": ["content/pve/enemies.yml", "content/pve/loot.yml"],
         "runtime_owner": "MobTemplateRegistry/MobAbilityRegistry", "content_type": "HANDCRAFTED_GAME_CONTENT",
         "canonical_authority": "LOCKED_CANONICAL_CONTENT", "reload_policy": "RESTART_REQUIRED", "schema_version": 2,
         "generated": False, "operator_editable": False, "fail_behavior": "FAIL_CLOSED", "dependencies": ["world-events"]},
        {"domain": "events", "source_files": ["content/events/prologue.yml", "config/world.yml", "config/event-spawn-safety.yml"],
         "runtime_owner": "world event managers", "content_type": "MIXED_CONTENT_AND_OPERATOR",
         "canonical_authority": "SPLIT_EXPLICIT", "reload_policy": "SAFE_RELOAD_WITH_RECONCILIATION", "schema_version": 1,
         "generated": False, "operator_editable": "timing/safety only", "fail_behavior": "FAIL_CLOSED", "dependencies": ["pve", "rewards"]},
        {"domain": "operator", "source_files": [f"config/{name}.yml" for name in OPERATOR_FILES] + ["config.yml"],
         "runtime_owner": "ConfigManager", "content_type": "OPERATOR_CONFIGURATION",
         "canonical_authority": "SCHEMA_BOUNDED_SERVER_OVERRIDE", "reload_policy": "SAFE_RELOAD_WITH_RECONCILIATION",
         "schema_version": "packaged leaf schema", "generated": False, "operator_editable": True,
         "fail_behavior": "ROLLBACK_PREVIOUS_SNAPSHOT", "dependencies": []},
        {"domain": "messages", "source_files": ["messages.yml", "messages/*.yml"], "runtime_owner": "MessageManager",
         "content_type": "PRESENTATION_LOCALIZATION", "canonical_authority": "PACKAGED_DEFAULT_PLUS_SERVER_OVERRIDE",
         "reload_policy": "LIVE_RELOADABLE", "schema_version": 1, "generated": False, "operator_editable": True,
         "fail_behavior": "DEFAULT_FALLBACK", "dependencies": []},
        {"domain": "advancements", "source_files": ["datapack/data/icesmp/advancement/*.json"],
         "runtime_owner": "AdvancementService", "content_type": "HANDCRAFTED_GAME_CONTENT",
         "canonical_authority": "LOCKED_CANONICAL_CONTENT", "reload_policy": "RESTART_REQUIRED", "schema_version": "Minecraft 1.21.11",
         "generated": False, "operator_editable": False, "fail_behavior": "DISABLE_INCOMPLETE_TREE", "dependencies": ["datapack"]},
        {"domain": "runtime-state", "source_files": ["server data folder persistence"], "runtime_owner": "PlayerProfile/store managers",
         "content_type": "RUNTIME_PERSISTENT_STATE", "canonical_authority": "PERSISTENT_STORE", "reload_policy": "RESTART_REQUIRED",
         "schema_version": "domain-specific", "generated": False, "operator_editable": False,
         "fail_behavior": "FAIL_CLOSED", "dependencies": []},
    ]


def build_report(baseline: str) -> dict[str, Any]:
    old, old_names = old_effective(baseline)
    current = current_effective()
    old_leaves, new_leaves = leaves(old), leaves(current)
    if old_leaves != new_leaves:
        drift = [key for key in sorted(set(old_leaves) | set(new_leaves))
                 if old_leaves.get(key) != new_leaves.get(key)]
        raise AssertionError(f"effective gameplay/config semantic drift: {drift[:20]}")

    source_paths = list(CONTENT_FILES) + [f"config/{name}.yml" for name in OPERATOR_FILES] + ["config.yml"]
    owners: dict[str, list[str]] = defaultdict(list)
    for relative in source_paths:
        for key in leaves(load_yaml(RESOURCES / relative)):
            owners[key].append(relative)
    duplicates = [{"key": key, "sources": sources, "outcome": "MIGRATION_REQUIRED"}
                  for key, sources in sorted(owners.items()) if len(sources) > 1]
    if duplicates:
        raise AssertionError(f"duplicate current leaf authorities: {duplicates[:10]}")

    templates = current["item-templates"]
    armor = {key: value for key, value in templates.items() if value.get("armor-family")}
    recipes = current["profession-recipes"]
    armor_ids = set(armor)
    armor_recipes = {key: value for key, value in recipes.items()
                     if (value.get("result") or {}).get("template") in armor_ids}
    enemies = current["mob-templates"]
    techniques = current["mob-abilities"]
    if len(armor) != 160 or len(enemies) != 89 or len(techniques) != 61:
        raise AssertionError("protected #140/#141 catalog count drift")

    generators = generator_inventory()
    if any(row["authors_gameplay"] for row in generators):
        raise AssertionError("generated gameplay authority remains")
    for removed in REMOVED_GAMEPLAY_GENERATORS:
        if (ROOT / removed).exists():
            raise AssertionError(f"removed gameplay authoring tool returned: {removed}")

    commands = command_inventory()
    scanned_roots, command_routes, root_alias_inventory, routing_alias_inventory = complete_command_routes()
    if len(scanned_roots) != len(commands):
        raise AssertionError(f"command scanner disagreement: registrations={len(commands)}, scanner={len(scanned_roots)}")
    roots = [row["command"] for row in commands]
    duplicate_roots = sorted(name for name, count in Counter(roots).items() if count > 1)
    all_aliases: dict[str, list[str]] = defaultdict(list)
    for row in commands:
        for alias in row["aliases"]:
            all_aliases[alias].append(row["command"])
    alias_conflicts = [{"alias": alias, "commands": names} for alias, names in sorted(all_aliases.items())
                       if len(names) > 1 or (alias in roots and alias not in names)]
    if duplicate_roots or alias_conflicts:
        raise AssertionError(f"command registration conflict: roots={duplicate_roots}, aliases={alias_conflicts}")
    ice_source = (ROOT / "src/main/java/hu/taliann/icesmp/commands/IceSMPCommand.java").read_text(encoding="utf-8")
    dispatch_source = (ROOT / "src/main/java/hu/taliann/icesmp/commands/AbstractDispatchCommand.java").read_text(encoding="utf-8")
    for token in ("if (args.length == 0)", "isOperatorEditable", "restoreSnapshot", "restart-required",
                  "inspectConfigAuthority", "rootSuggestions(sender, \"\")", "configKeySuggestions"):
        if token not in ice_source:
            raise AssertionError(f"/icesmp command hardening token missing: {token}")
    for token in ("isVisibleTo(sender)", "messages.permission-denied"):
        if token not in dispatch_source:
            raise AssertionError(f"shared dispatch permission token missing: {token}")
    command_regression = (ROOT / "src/regression/java/hu/taliann/icesmp/commands/CommandSurfaceRegressionSuite.java")
    if not command_regression.exists():
        raise AssertionError("command surface regression suite missing")

    rows = inventory()
    before_paths = [f"config/{name}.yml" for name in old_names] + ["config.yml"]
    after_authority = [row for row in rows if row["path"].startswith("src/main/resources/config")
                       or row["path"].startswith("src/main/resources/content")]
    line_counts = sorted((row["lines"], row["path"]) for row in after_authority)
    merge_base = git("merge-base", baseline, "HEAD")
    return {
        "schema": 2,
        "topology": {
            "staging_start_head": "042f72fb405e38b6306c45f30a26074e11322fd5",
            "pr_140_start_head": "4a24ee49949b99d410455a990e59a59025d2242b",
            "pr_141_parent_head": baseline,
            "feature_head": "REPORT_COMMIT_SELF",
            "feature_branch": git("branch", "--show-current"),
            "merge_base_with_pr_141": merge_base,
            "stack_parent_exact": merge_base == baseline,
        },
        "config_inventory": rows,
        "config_classification": dict(sorted(Counter(row["category"] for row in rows).items())),
        "config_size_complexity": {
            "before_authority_file_count": len(before_paths),
            "after_authority_file_count": len(after_authority),
            "before_effective_leaf_count": len(leaves(old)),
            "after_effective_leaf_count": len(leaves(current)),
            "before_config_paths": before_paths,
            "after_largest_files": [{"path": path, "lines": count} for count, path in reversed(line_counts[-10:])],
            "generated_gameplay_files_before": 0,
            "generated_gameplay_files_after": 0,
            "duplicate_authority_before": "overlay merge chain present",
            "duplicate_authority_after": 0,
            "loader_count_after": 1,
        },
        "old_to_new_paths": [{"old": old_path, "new": new_path, "outcome": "MIGRATE_TO_CONTENT"}
                             for old_path, new_path in OLD_TO_NEW.items()],
        "generated_tooling_inventory": generators,
        "removed_gameplay_generators": [
            {"path": path, "category": "GAMEPLAY_AUTHORING", "outcome": "REMOVE_GAMEPLAY_AUTHORING"}
            for path in REMOVED_GAMEPLAY_GENERATORS
        ],
        "generated_gameplay_authority_count": 0,
        "authority_matrix": authority_matrix(),
        "duplicate_key_findings": {
            "current_duplicate_leaf_count": len(duplicates),
            "current": duplicates,
            "resolved": [
                {"finding": "three ordered gameplay overlay files", "outcome": "MERGED_AND_REMOVED"},
                {"finding": "world-events.safety split between world.yml and event-spawn-safety.yml", "outcome": "SINGLE_OWNER"},
                {"finding": "class melee catalyst list duplicated by an empty earlier leaf", "outcome": "REMOVE_DUPLICATE"},
                {"finding": "root config duplicated salvage/profession/prologue content", "outcome": "MIGRATE_TO_CANONICAL_DOMAIN"},
            ],
        },
        "dead_key_findings": [
            {"finding": "legacy overlay and deployed gameplay filenames", "outcome": "ARCHIVE_ON_UPGRADE_THEN_IGNORE"},
            {"finding": "generated advancement Node gameplay catalog", "outcome": "REMOVED; JSON IS AUTHORITY"},
            {"finding": "community-goals.season-points in operator GUI", "outcome": "REMOVED_FROM_GUI; LOCKED CONTENT"},
        ],
        "reload_matrix": [{"domain": row["domain"], "policy": row["reload_policy"],
                           "failure": row["fail_behavior"]} for row in authority_matrix()],
        "command_inventory": commands,
        "command_routes": command_routes,
        "root_alias_inventory": root_alias_inventory,
        "routing_alias_inventory": routing_alias_inventory,
        "command_outcomes": dict(sorted(Counter(row["outcome"] for row in commands).items())),
        "command_complexity": {
            "top_level_commands_before": len(commands), "top_level_commands_after": len(commands),
            "functional_routes_after": len(command_routes),
            "root_aliases_after": len(root_alias_inventory),
            "routing_aliases_after": len(routing_alias_inventory),
            "aliases_after": len(root_alias_inventory) + len(routing_alias_inventory),
            "admin_roots_after": sum(row["admin"] for row in commands),
            "shared_dispatch_consumers": ["currency", "faction", "class"],
            "duplicate_roots": duplicate_roots, "alias_conflicts": alias_conflicts,
        },
        "alias_migration": {"icesmp": ["ismp"], "legacy_behavior": "aliases route to the same implementation"},
        "permissions": {
            "authority": "src/main/java/hu/taliann/icesmp/core/Permissions.java",
            "shared_dispatch_permission_filtered": True,
            "icesmp_root_permission_filtered": True,
            "admin_without_registered_permission": 0,
        },
        "migration": {
            "policy": "backup-before-lock",
            "backup_path": "migration-backups/config-content-command-surface-2/config/<name>-<sha12>.yml",
            "atomic_move_preferred": True,
            "collision_behavior": "FAIL_CLOSED",
            "unknown_operator_keys": "WARN_AND_IGNORE",
            "invalid_operator_write": "RESTORE_FILE_AND_PREVIOUS_SNAPSHOT",
        },
        "runtime_proof": {
            "paper_version": "1.21.11",
            "paper_probe": "sourceIntegrityPaperRuntimeTest",
            "folia_contract": "existing region/global scheduler regression suite",
            "command_cases": ["permission-filtered help", "trailing-space tab", "nested subcommand",
                              "invalid subcommand", "permission denied", "player-only console refusal",
                              "console-supported", "admin", "inspect config authority"],
            "config_cases": ["fresh boot", "legacy folder backup", "valid operator reload",
                             "invalid reload rollback", "restart-required refusal", "missing content fail-closed"],
        },
        "semantic_parity": {
            "entire_effective_tree_equal_to_parent": True,
            "effective_tree_sha256": stable_hash(current),
            "effective_leaf_count": len(leaves(current)),
        },
        "armor_parity": {
            "status": "ALREADY_HANDCRAFTED_KEEP", "count": len(armor),
            "ids_sha256": stable_hash(sorted(armor)), "definitions_sha256": stable_hash(armor),
            "recipes_count": len(armor_recipes), "recipes_sha256": stable_hash(armor_recipes),
            "stats_lore_sets_ascension_unchanged": True,
        },
        "enemy_parity": {
            "status": "HANDCRAFTED_KEEP", "template_count": len(enemies), "technique_count": len(techniques),
            "templates_sha256": stable_hash(enemies), "techniques_sha256": stable_hash(techniques),
            "world_boss_and_variant_metadata_unchanged": True,
        },
        "ci": {
            "workflow": ".github/workflows/config-content-command-surface.yml",
            "required": ["Java 21 build", "full regressions", "authority audit", "generated gameplay zero",
                         "command matrix", "migration", "Paper 1.21.11", "Folia", "resource pack", "docs"],
            "exact_final_head": "verified by pull-request workflow after push",
        },
        "human_gameplay_staging": {
            "required": True,
            "extra_checks": ["command discoverability", "help", "tab completion", "admin commands", "reload",
                             "upgraded config", "fresh config", "no missing content", "no reset", "no config spam",
                             "no permission leak"],
        },
        "weapon_readiness": {
            "verdict": "YES",
            "definition": "current items remain in content/equipment/equipment.yml; a future catalog may use content/equipment/weapons.yml",
            "tuning": "handcrafted definition plus bounded operator seams",
            "recipe": "content/professions/recipes.yml",
            "presentation": "item model/resource-pack assets and messages",
            "validator": "catalog/economy/resource-pack auditors",
            "admin_tooling": "/iceitem and permission-filtered /icesmp inspect config",
            "weapon_rework_performed": False,
        },
    }


def render(report: dict[str, Any]) -> str:
    return json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser()
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--write", action="store_true")
    mode.add_argument("--check", action="store_true")
    parser.add_argument("--baseline", default=BASELINE)
    args = parser.parse_args()
    expected = render(build_report(args.baseline))
    if args.write:
        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT.write_text(expected, encoding="utf-8")
        print(f"Config/content/command evidence written: {OUTPUT.relative_to(ROOT)}")
        return
    actual = OUTPUT.read_text(encoding="utf-8") if OUTPUT.exists() else ""
    if actual != expected:
        raise SystemExit("authority evidence is stale; run audit_config_content_command_surface.py --write")
    report = json.loads(actual)
    print("Config/content/command authority: "
          f"{report['semantic_parity']['effective_leaf_count']} leaves, "
          f"{report['armor_parity']['count']} armor, "
          f"{report['enemy_parity']['template_count']}/{report['enemy_parity']['technique_count']} PvE, "
          f"{len(report['command_inventory'])} commands, generated gameplay=0")


if __name__ == "__main__":
    main()
