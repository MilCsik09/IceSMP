#!/usr/bin/env python3
"""Equipment Resource Pack 2.0-A production-derived asset authority audit.

The audit intentionally separates three questions:

* what production currently references (direct roots + indirect JSON chains),
* what the 160-piece armor / 40-line catalog structurally requires, and
* what may be removed safely after the temporary vanilla worn reset.

It never edits the resource pack.  Use --enforce-final only after the reset/cleanup
phase; without it the script is a report-only baseline probe suitable for discovering
legacy worn references before they are removed.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from collections import Counter, defaultdict, deque
from pathlib import Path
from typing import Any, Iterable

import yaml

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "resource-pack"
ASSETS = PACK / "assets"
CONFIG = ROOT / "src/main/resources/config"
DEFAULT_OUTPUT = ROOT / "build/reports/equipment-rp2/asset-audit.json"

TEMPLATE_OVERLAYS = (
    "item-templates.yml",
    "material-economy-expansion.yml",
    "equipment-catalog-expansion.yml",
    "reward-discoverability-closure.yml",
)
MATERIAL_OVERLAYS = (
    "profession-materials.yml",
    "profession-recipes.yml",
    "professions-2.yml",
    "material-economy-expansion.yml",
    "equipment-catalog-expansion.yml",
    "reward-discoverability-closure.yml",
)
PLAYER_ARMOR_SUFFIXES = ("_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS")
TEXT_EXTENSIONS = {
    ".java", ".kt", ".kts", ".py", ".yml", ".yaml", ".json", ".properties",
    ".md", ".txt", ".gradle", ".xml", ".toml", ".mcmeta",
}
RESOURCE_ID = re.compile(r"icesmp:[a-z0-9_./-]+")


class AuditFailure(RuntimeError):
    pass


def load_yaml(path: Path) -> dict[str, Any]:
    if not path.is_file():
        return {}
    raw = yaml.safe_load(path.read_text(encoding="utf-8"))
    return raw if isinstance(raw, dict) else {}


def deep_merge(target: dict[str, Any], patch: dict[str, Any]) -> None:
    for raw_key, value in patch.items():
        key = str(raw_key)
        if value is None:
            target.pop(key, None)
        elif isinstance(value, dict) and isinstance(target.get(key), dict):
            deep_merge(target[key], value)
        elif isinstance(value, dict):
            target[key] = json.loads(json.dumps(value))
        else:
            target[key] = value


def effective_section(section: str, files: Iterable[str]) -> dict[str, dict[str, Any]]:
    merged: dict[str, Any] = {}
    for name in files:
        root = load_yaml(CONFIG / name)
        patch = root.get(section, {})
        if isinstance(patch, dict):
            deep_merge(merged, patch)
    return {str(key): value for key, value in merged.items() if isinstance(value, dict)}


def normalize_id(raw: Any) -> str | None:
    if raw is None:
        return None
    value = str(raw).strip().lower()
    if not value:
        return None
    return value if ":" in value else f"icesmp:{value}"


def split_id(resource_id: str) -> tuple[str, str]:
    normalized = normalize_id(resource_id)
    if normalized is None:
        raise AuditFailure(f"invalid empty resource id: {resource_id!r}")
    namespace, path = normalized.split(":", 1)
    return namespace, path


def asset_file(resource_id: str, kind: str) -> Path:
    namespace, path = split_id(resource_id)
    suffix = ".png" if kind == "textures" else ".json"
    return ASSETS / namespace / kind / f"{path}{suffix}"


def rel(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def read_json(path: Path) -> dict[str, Any] | list[Any] | None:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError):
        return None


def collect_text_corpora() -> tuple[str, str]:
    production_parts: list[str] = []
    docs_parts: list[str] = []
    for path in ROOT.rglob("*"):
        if not path.is_file() or path.suffix.lower() not in TEXT_EXTENSIONS:
            continue
        relative = path.relative_to(ROOT)
        if relative.parts and relative.parts[0] in {".git", "build"}:
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError):
            continue
        if relative.parts and relative.parts[0] == "docs":
            docs_parts.append(text)
            continue
        if relative.parts and relative.parts[0] == "resource-pack":
            continue
        production_parts.append(text)
    return "\n".join(production_parts), "\n".join(docs_parts)


def walk_keyed_values(value: Any, wanted: set[str], out: list[tuple[str, str]], prefix: str = "") -> None:
    if isinstance(value, dict):
        for raw_key, child in value.items():
            key = str(raw_key)
            here = f"{prefix}.{key}" if prefix else key
            if key in wanted and isinstance(child, str) and child.strip():
                out.append((here, child.strip()))
            walk_keyed_values(child, wanted, out, here)
    elif isinstance(value, list):
        for index, child in enumerate(value):
            walk_keyed_values(child, wanted, out, f"{prefix}[{index}]")


def config_roots() -> tuple[dict[str, set[str]], list[dict[str, str]]]:
    roots: dict[str, set[str]] = defaultdict(set)
    missing: list[dict[str, str]] = []
    for path in sorted(CONFIG.glob("*.yml")):
        raw = load_yaml(path)
        found: list[tuple[str, str]] = []
        walk_keyed_values(raw, {"item-model", "equipment-asset"}, found)
        for location, value in found:
            rid = normalize_id(value)
            if rid is None:
                continue
            kind = "items" if location.endswith("item-model") else "equipment"
            target = asset_file(rid, kind)
            consumer = f"config:{path.name}:{location}"
            roots[rel(target)].add(consumer)
            if not target.is_file():
                missing.append({"consumer": consumer, "resource_id": rid, "expected": rel(target)})
    return roots, missing


def code_roots(production_text: str) -> dict[str, set[str]]:
    roots: dict[str, set[str]] = defaultdict(set)
    ids = sorted(set(RESOURCE_ID.findall(production_text.lower())))
    for rid in ids:
        namespace, path = split_id(rid)
        for kind in ("items", "equipment", "models", "textures", "font"):
            target = asset_file(f"{namespace}:{path}", kind)
            if target.is_file():
                roots[rel(target)].add(f"source-literal:{rid}")
    return roots


def texture_target(raw: str) -> Path | None:
    value = normalize_id(raw)
    if value is None or value.startswith("minecraft:"):
        return None
    return asset_file(value, "textures")


def model_target(raw: str) -> Path | None:
    value = normalize_id(raw)
    if value is None or value.startswith("minecraft:"):
        return None
    return asset_file(value, "models")


def json_edges(path: Path) -> tuple[set[Path], list[dict[str, str]]]:
    data = read_json(path)
    if data is None:
        return set(), []
    targets: set[Path] = set()
    broken: list[dict[str, str]] = []
    normalized = path.as_posix()

    def require(target: Path | None, reason: str) -> None:
        if target is None:
            return
        targets.add(target)
        if not target.is_file():
            broken.append({"consumer": rel(path), "reference": reason, "expected": rel(target)})

    def item_models(value: Any, parent_key: str | None = None) -> None:
        if isinstance(value, dict):
            for key, child in value.items():
                if key == "model" and isinstance(child, str):
                    require(model_target(child), child)
                else:
                    item_models(child, key)
        elif isinstance(value, list):
            for child in value:
                item_models(child, parent_key)

    if "/items/" in normalized:
        item_models(data)
    elif "/models/" in normalized and isinstance(data, dict):
        parent = data.get("parent")
        if isinstance(parent, str):
            require(model_target(parent), parent)
        textures = data.get("textures")
        if isinstance(textures, dict):
            for raw in textures.values():
                if isinstance(raw, str) and not raw.startswith("#"):
                    require(texture_target(raw), raw)
        overrides = data.get("overrides")
        if isinstance(overrides, list):
            for override in overrides:
                if isinstance(override, dict) and isinstance(override.get("model"), str):
                    require(model_target(str(override["model"])), str(override["model"]))
    elif "/equipment/" in normalized and isinstance(data, dict):
        layers = data.get("layers")
        if isinstance(layers, dict):
            for layer, entries in layers.items():
                if not isinstance(entries, list):
                    continue
                for entry in entries:
                    if not isinstance(entry, dict) or not isinstance(entry.get("texture"), str):
                        continue
                    rid = normalize_id(entry["texture"])
                    if rid is None or rid.startswith("minecraft:"):
                        continue
                    namespace, texture_path = split_id(rid)
                    target = ASSETS / namespace / "textures/entity/equipment" / str(layer) / f"{texture_path}.png"
                    require(target, f"{layer}:{rid}")
    elif "/font/" in normalized and isinstance(data, dict):
        providers = data.get("providers")
        if isinstance(providers, list):
            for provider in providers:
                if not isinstance(provider, dict) or not isinstance(provider.get("file"), str):
                    continue
                require(texture_target(provider["file"]), str(provider["file"]))
    elif "/atlases/" in normalized and isinstance(data, dict):
        for source in data.get("sources", []) or []:
            if not isinstance(source, dict):
                continue
            source_type = str(source.get("type", ""))
            if source_type == "minecraft:single" and isinstance(source.get("resource"), str):
                require(texture_target(source["resource"]), str(source["resource"]))
            elif source_type == "minecraft:directory" and isinstance(source.get("source"), str):
                rid = normalize_id(source["source"])
                if rid is None or rid.startswith("minecraft:"):
                    continue
                namespace, source_path = split_id(rid)
                directory = ASSETS / namespace / "textures" / source_path
                if directory.is_dir():
                    targets.update(p for p in directory.rglob("*.png") if p.is_file())
                else:
                    broken.append({"consumer": rel(path), "reference": rid, "expected": rel(directory)})
    return targets, broken


def build_reference_graph() -> tuple[dict[str, set[str]], dict[str, set[str]], list[dict[str, str]], str]:
    production_text, docs_text = collect_text_corpora()
    roots, broken = config_roots()
    for path, reasons in code_roots(production_text).items():
        roots[path].update(reasons)

    # These are intentionally dynamic runtime bindings guarded by ResourcePackRegressionSuite.
    for asset_id in ("relic_phoenix_wing", "relic_frost_wing", "relic_wander_wind", "relic_bone_wing",
                     "vas_lopancel", "arany_lopancel", "gyemant_lopancel"):
        target = asset_file(f"icesmp:{asset_id}", "equipment")
        if target.is_file():
            roots[rel(target)].add(f"runtime-dynamic:{asset_id}")

    for infrastructure in (PACK / "pack.mcmeta", PACK / "pack.png"):
        if infrastructure.is_file():
            roots[rel(infrastructure)].add("pack-root")
    for registry_dir in (ASSETS / "icesmp/font", ASSETS / "icesmp/atlases"):
        if registry_dir.is_dir():
            for path in registry_dir.rglob("*.json"):
                roots[rel(path)].add("pack-registry")

    edges: dict[str, set[str]] = defaultdict(set)
    queue = deque(sorted(roots))
    seen: set[str] = set()
    while queue:
        current_rel = queue.popleft()
        if current_rel in seen:
            continue
        seen.add(current_rel)
        current = ROOT / current_rel
        if not current.is_file() or current.suffix.lower() not in {".json", ".mcmeta"}:
            continue
        targets, local_broken = json_edges(current)
        broken.extend(local_broken)
        for target in targets:
            target_rel = rel(target)
            edges[current_rel].add(target_rel)
            if target.is_file() and target_rel not in seen:
                queue.append(target_rel)

    return roots, edges, broken, docs_text


def reverse_edges(edges: dict[str, set[str]]) -> dict[str, set[str]]:
    reverse: dict[str, set[str]] = defaultdict(set)
    for source, targets in edges.items():
        for target in targets:
            reverse[target].add(source)
    return reverse


def reachable(roots: dict[str, set[str]], edges: dict[str, set[str]]) -> set[str]:
    found: set[str] = set()
    queue = deque(roots)
    while queue:
        node = queue.popleft()
        if node in found:
            continue
        found.add(node)
        queue.extend(edges.get(node, ()))
    return found


def policy_suffixes() -> tuple[str, list[str]]:
    path = ROOT / "src/main/resources/wearable-fallback-policy.properties"
    props: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        props[key.strip()] = value.strip()
    version = props.get("minecraft-version", "")
    suffixes = [part.strip().upper() for part in props.get("suffix", "").split(",") if part.strip()]
    return version, suffixes


def allows_implicit_armor(material: str, suffixes: list[str]) -> bool:
    upper = str(material).upper()
    return any(upper.endswith(suffix) for suffix in suffixes)


def resolve_item_chain(item_model: str | None) -> tuple[str | None, list[str], list[str]]:
    if not item_model:
        return None, [], []
    root = asset_file(item_model, "items")
    if not root.is_file():
        return rel(root), [], [rel(root)]
    textures: set[str] = set()
    missing: set[str] = set()
    seen: set[Path] = set()
    queue = deque([root])
    while queue:
        path = queue.popleft()
        if path in seen:
            continue
        seen.add(path)
        targets, broken = json_edges(path)
        missing.update(row["expected"] for row in broken)
        for target in targets:
            if "/textures/" in target.as_posix() and target.suffix == ".png":
                textures.add(rel(target))
            elif target.suffix == ".json" and target.is_file():
                queue.append(target)
    return rel(root), sorted(textures), sorted(missing)


def resolve_equipment_chain(asset_id: str | None) -> tuple[str | None, list[str], list[str]]:
    if not asset_id:
        return None, [], []
    root = asset_file(asset_id, "equipment")
    if not root.is_file():
        return rel(root), [], [rel(root)]
    targets, broken = json_edges(root)
    textures = sorted(rel(path) for path in targets if path.suffix == ".png")
    return rel(root), textures, sorted(row["expected"] for row in broken)


def canonical_armor() -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    templates = effective_section("item-templates", TEMPLATE_OVERLAYS)
    version, suffixes = policy_suffixes()
    rows: list[dict[str, Any]] = []
    for template_id, template in sorted(templates.items()):
        metadata = template.get("encounter-metadata") or {}
        if not isinstance(metadata, dict) or not metadata.get("catalog-line"):
            continue
        if str(template.get("family", "")).lower() != "armor":
            continue
        item_model = normalize_id(template.get("item-model"))
        explicit_equipment = normalize_id(template.get("equipment-asset"))
        material = str(template.get("material", "")).upper()
        implicit = bool(item_model and allows_implicit_armor(material, suffixes))
        worn_asset = explicit_equipment or (item_model if implicit else None)
        item_root, item_textures, item_missing = resolve_item_chain(item_model)
        worn_root, worn_textures, worn_missing = resolve_equipment_chain(worn_asset)
        rows.append({
            "template_id": template_id,
            "display_name": str(template.get("display-name", template_id)),
            "family": str(template.get("armor-family", "")).upper(),
            "slot": str(template.get("slot", "")).upper(),
            "gear_line": str(metadata.get("catalog-line")),
            "archetype": str(metadata.get("catalog-archetype", "")),
            "progression_band": str(metadata.get("progression-band", "")),
            "acquisition": str(metadata.get("catalog-acquisition", "")),
            "profession": str(metadata.get("catalog-profession", "") or ""),
            "rarity": str(template.get("rarity", "")),
            "set": str(template.get("set-id", "") or ""),
            "signature": bool(template.get("signature") or template.get("signature-id") or template.get("signature-ability")),
            "ascension": bool(template.get("ascension") or template.get("ascension-path") or template.get("ascendable")),
            "backing_material": material,
            "item_model": item_model,
            "item_definition": item_root,
            "item_textures": item_textures,
            "item_chain_missing": item_missing,
            "explicit_equipment_asset": explicit_equipment,
            "implicit_same_id_equipment_asset": bool(implicit and not explicit_equipment),
            "current_worn_asset": worn_asset,
            "worn_definition": worn_root,
            "worn_textures": worn_textures,
            "worn_chain_missing": worn_missing,
            "current_worn_representation": "CUSTOM_EQUIPMENT_ASSET" if worn_asset else "VANILLA_MATERIAL",
            "minecraft_version": version,
        })

    model_lines: dict[str, set[str]] = defaultdict(set)
    for row in rows:
        if row["item_model"]:
            model_lines[str(row["item_model"])].add(str(row["gear_line"]))
    for row in rows:
        model = row["item_model"]
        shared_between_lines = bool(model and len(model_lines[str(model)]) > 1)
        row["inventory_representation_valid"] = not row["item_chain_missing"]
        row["rp2_inventory_texture_required"] = not bool(model) or shared_between_lines
        row["rp2_inventory_reason"] = (
            "vanilla backing appearance has no dedicated canonical inventory asset" if not model
            else "current custom item model is shared by multiple canonical gear lines" if shared_between_lines
            else "existing line-local custom item chain is valid and may be retained"
        )
        row["rp2_worn_model_required"] = True

    groups: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        groups[str(row["gear_line"])].append(row)
    lines: list[dict[str, Any]] = []
    for line_id, pieces in sorted(groups.items()):
        ordered = sorted(pieces, key=lambda row: str(row["slot"]))
        families = sorted({str(row["family"]) for row in ordered})
        lines.append({
            "line_id": line_id,
            "template_ids": [str(row["template_id"]) for row in ordered],
            "family": families[0] if len(families) == 1 else "/".join(families),
            "archetype": next((str(row["archetype"]) for row in ordered if row["archetype"]), ""),
            "progression": next((str(row["progression_band"]) for row in ordered if row["progression_band"]), ""),
            "acquisition": next((str(row["acquisition"]) for row in ordered if row["acquisition"]), ""),
            "profession": next((str(row["profession"]) for row in ordered if row["profession"]), ""),
            "set_or_prestige": bool(any(row["set"] for row in ordered) or any(row["acquisition"] in {"boss_reward", "faction_reward", "rare_drop", "prestige"} for row in ordered)),
            "current_inventory_custom_count": sum(bool(row["item_model"]) for row in ordered),
            "rp2_inventory_work_count": sum(bool(row["rp2_inventory_texture_required"]) for row in ordered),
            "current_custom_worn_count": sum(bool(row["current_worn_asset"]) for row in ordered),
            "worn_fallback_state": "VANILLA" if all(not row["current_worn_asset"] for row in ordered) else "CUSTOM_OR_MIXED",
            "rp2_worn_set_required": True,
        })
    return rows, lines


def material_rows() -> list[dict[str, Any]]:
    materials = effective_section("profession-materials", MATERIAL_OVERLAYS)
    rows: list[dict[str, Any]] = []
    for material_id, material in sorted(materials.items()):
        if not bool(material.get("economy-managed", False)):
            continue
        item_model = normalize_id(material.get("item-model"))
        item_root, textures, missing = resolve_item_chain(item_model)
        rows.append({
            "material_id": material_id,
            "display_name": str(material.get("display-name", material_id)),
            "backing_material": str(material.get("material", "")).upper(),
            "tier": str(material.get("tier", "")),
            "processing_state": str(material.get("processing-state", "")),
            "item_model": item_model,
            "item_definition": item_root,
            "textures": textures,
            "missing": missing,
            "visual_mode": "CUSTOM" if item_model else "VANILLA_INTENTIONAL",
            "custom_appearance_required": bool(item_model),
            "rp2_texture_required": False,
            "visual_necessity_reason": (
                "existing production config explicitly requests a custom item model" if item_model
                else "no production visual requirement; vanilla backing is intentional until a future scoped decision"
            ),
        })
    return rows


def asset_kind(path: Path) -> str:
    text = path.as_posix()
    if path.name == "pack.mcmeta": return "pack_metadata"
    if path.name == "pack.png": return "pack_icon"
    if path.suffix == ".md": return "documentation"
    if "/items/" in text: return "item_definition"
    if "/models/" in text: return "model"
    if "/equipment/" in text and path.suffix == ".json": return "equipment_definition"
    if "/font/" in text and path.suffix == ".json": return "font_definition"
    if "/atlases/" in text: return "atlas_definition"
    if path.suffix == ".png": return "texture"
    return "misc"


def category(path: Path, managed_ids: set[str]) -> str:
    text = path.as_posix().lower()
    stem = path.stem.lower()
    if "/equipment/" in text or "/entity/equipment/" in text: return "equipment"
    if stem in managed_ids: return "materials"
    if any(token in text for token in ("hud", "/gui/", "bossbar", "party", "player_frame")): return "hud_ui"
    if "/font/" in text: return "fonts"
    if any(token in stem for token in ("runa", "rune")): return "runes"
    if any(token in stem for token in ("coin", "token", "valuta", "currency")): return "currency"
    if any(token in text for token in ("mob", "entity")): return "mobs_entities"
    return "misc"


def hash_groups(files: list[Path]) -> dict[str, list[str]]:
    groups: dict[str, list[str]] = defaultdict(list)
    for path in files:
        try:
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
        except OSError:
            continue
        groups[digest].append(rel(path))
    return {digest: paths for digest, paths in groups.items() if len(paths) > 1}


def classify_assets(armor: list[dict[str, Any]], materials: list[dict[str, Any]]) -> tuple[list[dict[str, Any]], list[dict[str, Any]], list[dict[str, Any]]]:
    roots, edges, broken, docs_text = build_reference_graph()
    reverse = reverse_edges(edges)
    active = reachable(roots, edges)
    managed_ids = {str(row["material_id"]).lower() for row in materials}
    files = sorted(path for path in PACK.rglob("*") if path.is_file())
    duplicates = hash_groups(files)
    duplicate_lookup: dict[str, list[str]] = {}
    for paths in duplicates.values():
        for path in paths:
            duplicate_lookup[path] = paths

    legacy_worn_paths: set[str] = set()
    for row in armor:
        if row["worn_definition"]:
            legacy_worn_paths.add(str(row["worn_definition"]))
        legacy_worn_paths.update(str(path) for path in row["worn_textures"])

    records: list[dict[str, Any]] = []
    safe_delete: list[dict[str, Any]] = []
    for path in files:
        relative = rel(path)
        basename = path.stem.lower()
        consumers = sorted(roots.get(relative, set()))
        indirect = sorted(reverse.get(relative, set()))
        is_active = relative in active
        is_focus = (relative in legacy_worn_paths or "/equipment/" in relative or
                    "/textures/entity/equipment/" in relative or basename in managed_ids)
        doc_reference = basename in docs_text.lower() or relative in docs_text
        duplicate_peers = duplicate_lookup.get(relative, [])
        if is_active:
            state = "ACTIVE_SHARED" if len(consumers) + len(indirect) > 1 else "ACTIVE"
            reason = "reachable from production/runtime pack roots"
        elif doc_reference and is_focus:
            state = "FUTURE_HANDOFF"
            reason = "not production-reachable, but retained by developer/future documentation"
        elif duplicate_peers:
            state = "DUPLICATE"
            reason = "bit-identical content exists elsewhere; semantic deletion is not inferred from hash equality"
        elif is_focus:
            state = "ORPHAN"
            reason = "equipment/material-focus file has no direct production root or indirect pack consumer"
        elif path.name == "README.md":
            state = "FUTURE_HANDOFF"
            reason = "developer pack documentation"
        else:
            state = "UNKNOWN_REVIEW_REQUIRED"
            reason = "outside equipment/material cleanup focus and not proven production-reachable by this audit"

        safe = state == "ORPHAN" and not consumers and not indirect and not doc_reference
        record = {
            "asset_path": relative,
            "asset_type": asset_kind(path),
            "namespace": "icesmp" if "/assets/icesmp/" in f"/{relative}" else "pack",
            "category": category(path, managed_ids),
            "status": state,
            "production_consumers": consumers,
            "indirect_consumers": indirect,
            "template_ids": sorted(str(row["template_id"]) for row in armor if relative in ({row["item_definition"], row["worn_definition"]} | set(row["item_textures"]) | set(row["worn_textures"]))),
            "gear_line": sorted({str(row["gear_line"]) for row in armor if relative in ({row["item_definition"], row["worn_definition"]} | set(row["item_textures"]) | set(row["worn_textures"]))}),
            "family": sorted({str(row["family"]) for row in armor if relative in ({row["item_definition"], row["worn_definition"]} | set(row["item_textures"]) | set(row["worn_textures"]))}),
            "slot": sorted({str(row["slot"]) for row in armor if relative in ({row["item_definition"], row["worn_definition"]} | set(row["item_textures"]) | set(row["worn_textures"]))}),
            "current_visual_mode": "custom-worn" if relative in legacy_worn_paths else "pack",
            "rp2_required": False,
            "safe_to_delete": safe,
            "duplicate_peers": sorted(peer for peer in duplicate_peers if peer != relative),
            "reason": reason,
        }
        records.append(record)
        if safe:
            safe_delete.append({"path": relative, "previous_purpose": "legacy equipment/material asset", "proof": "no production root; no indirect JSON consumer; no documented future handoff", "reason": reason})
    return records, safe_delete, broken


def validate_shape(armor: list[dict[str, Any]], lines: list[dict[str, Any]]) -> list[str]:
    errors: list[str] = []
    if len(armor) != 160:
        errors.append(f"canonical armor count is {len(armor)}, expected 160")
    if len(lines) != 40:
        errors.append(f"gear line count is {len(lines)}, expected 40")
    families = Counter(str(row["family"]) for row in armor)
    for family in ("CLOTH", "LEATHER", "MAIL", "PLATE"):
        if families[family] != 40:
            errors.append(f"{family} armor count is {families[family]}, expected 40")
    for line in lines:
        if len(line["template_ids"]) != 4:
            errors.append(f"gear line {line['line_id']} has {len(line['template_ids'])} pieces, expected 4")
    return errors


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default=str(DEFAULT_OUTPUT.relative_to(ROOT)))
    parser.add_argument("--enforce-final", action="store_true")
    args = parser.parse_args()

    armor, lines = canonical_armor()
    materials = material_rows()
    records, safe_delete, broken = classify_assets(armor, materials)
    shape_errors = validate_shape(armor, lines)
    broken_unique = sorted({json.dumps(row, ensure_ascii=False, sort_keys=True): row for row in broken}.values(), key=lambda row: json.dumps(row, ensure_ascii=False, sort_keys=True))
    counts = Counter(str(row["status"]) for row in records)
    custom_worn = [row for row in armor if row["current_worn_asset"]]
    vanilla_worn = [row for row in armor if not row["current_worn_asset"]]
    inventory_work = [row for row in armor if row["rp2_inventory_texture_required"]]

    focus_unknown = [row for row in records if row["status"] == "UNKNOWN_REVIEW_REQUIRED" and row["category"] in {"equipment", "materials"}]
    final_errors = list(shape_errors)
    if broken_unique:
        final_errors.append(f"broken production/indirect asset references: {len(broken_unique)}")
    if args.enforce_final and custom_worn:
        final_errors.append(f"canonical armor still requests custom worn assets: {len(custom_worn)}")
    if args.enforce_final and focus_unknown:
        final_errors.append(f"equipment/material focus assets remain UNKNOWN_REVIEW_REQUIRED: {len(focus_unknown)}")
    if args.enforce_final and any(row["safe_to_delete"] and (row["production_consumers"] or row["indirect_consumers"]) for row in records):
        final_errors.append("a SAFE_TO_DELETE asset is still referenced")

    report = {
        "schema": 1,
        "scope": "Equipment RP 2.0-A",
        "production_authority": {
            "minecraft_version": policy_suffixes()[0],
            "canonical_armor": len(armor),
            "gear_lines": len(lines),
            "managed_materials": len(materials),
        },
        "asset_counts": {"total_rp_files": len(records), **dict(sorted(counts.items()))},
        "reference_integrity": {
            "broken_production_reference": len(broken_unique),
            "safe_delete_asset_referenced": sum(bool(row["safe_to_delete"] and (row["production_consumers"] or row["indirect_consumers"])) for row in records),
            "focus_unknown": len(focus_unknown),
        },
        "equipment_counts": {
            "canonical_armor": len(armor),
            "gear_lines": len(lines),
            "armor_pieces_with_valid_inventory_representation": sum(bool(row["inventory_representation_valid"]) for row in armor),
            "armor_pieces_custom_worn": len(custom_worn),
            "armor_pieces_vanilla_worn": len(vanilla_worn),
            "rp2_inventory_replacements_required": len(inventory_work),
            "rp2_worn_line_sets_required": sum(bool(row["rp2_worn_set_required"]) for row in lines),
        },
        "material_counts": {
            "managed": len(materials),
            "custom_appearance_required": sum(bool(row["custom_appearance_required"]) for row in materials),
            "vanilla_appearance_intentional": sum(row["visual_mode"] == "VANILLA_INTENTIONAL" for row in materials),
            "missing": sum(bool(row["missing"]) for row in materials),
            "rp2_texture_required": sum(bool(row["rp2_texture_required"]) for row in materials),
        },
        "shape_errors": shape_errors,
        "final_gate_errors": final_errors,
        "broken_references": broken_unique,
        "safe_to_delete": safe_delete,
        "armor": armor,
        "gear_lines": lines,
        "materials": materials,
        "assets": records,
    }

    output = ROOT / args.output
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    print(
        "Equipment RP2 asset audit: "
        f"files={len(records)}, armor={len(armor)}, lines={len(lines)}, materials={len(materials)}, "
        f"custom-worn={len(custom_worn)}, vanilla-worn={len(vanilla_worn)}, "
        f"safe-delete={len(safe_delete)}, broken={len(broken_unique)}"
    )
    if shape_errors:
        raise AuditFailure("; ".join(shape_errors))
    if args.enforce_final and final_errors:
        raise AuditFailure("; ".join(final_errors))


if __name__ == "__main__":
    main()
