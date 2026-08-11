#!/usr/bin/env python3
"""Dependency-free regressions for scripts/resource_pack.py."""

from __future__ import annotations

import hashlib
import importlib.util
import json
import os
import tempfile
import unittest
import zipfile
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("resource_pack.py")
SPEC = importlib.util.spec_from_file_location("resource_pack", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
resource_pack = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(resource_pack)

# The validator intentionally checks the PNG signature/IHDR/dimensions without image libraries.
MINIMAL_PNG_HEADER = (
    b"\x89PNG\r\n\x1a\n"
    b"\x00\x00\x00\x0dIHDR"
    b"\x00\x00\x00\x01\x00\x00\x00\x01"
)


class ResourcePackToolingTest(unittest.TestCase):
    def make_pack(self, root: Path) -> None:
        (root / "assets" / "icesmp" / "models").mkdir(parents=True)
        (root / "pack.mcmeta").write_text(
            json.dumps({"pack": {"description": "test", "pack_format": 75}}),
            encoding="utf-8",
        )
        (root / "pack.png").write_bytes(MINIMAL_PNG_HEADER)
        (root / "assets" / "icesmp" / "models" / "example.json").write_text(
            json.dumps({"parent": "minecraft:item/generated"}),
            encoding="utf-8",
        )
        (root / "README.md").write_text("repository-only documentation\n", encoding="utf-8")

    def add_equipment(self, root: Path, asset: str = "test_helmet",
                      layer: str = "humanoid", texture: str | None = None,
                      with_texture: bool = True) -> None:
        texture_id = texture or asset
        equipment_dir = root / "assets" / "icesmp" / "equipment"
        equipment_dir.mkdir(parents=True, exist_ok=True)
        (equipment_dir / f"{asset}.json").write_text(
            json.dumps({"layers": {layer: [{"texture": f"icesmp:{texture_id}"}]}}),
            encoding="utf-8",
        )
        if with_texture:
            texture_path = (
                root / "assets" / "icesmp" / "textures" / "entity" / "equipment"
                / layer / f"{texture_id}.png"
            )
            texture_path.parent.mkdir(parents=True, exist_ok=True)
            texture_path.write_bytes(MINIMAL_PNG_HEADER)

    def write_config(self, pack_root: Path, name: str, content: str) -> None:
        config_root = pack_root.parent / "src" / "main" / "resources" / "config"
        config_root.mkdir(parents=True, exist_ok=True)
        (config_root / name).write_text(content, encoding="utf-8")

    def add_first_party_hud_layer(self, root: Path) -> None:
        shader = root / "assets" / "minecraft" / "shaders" / "core" / "rendertype_text.vsh"
        shader.parent.mkdir(parents=True, exist_ok=True)
        shader.write_text(
            "#version 330\n#moj_import <minecraft:dynamictransforms.glsl>\n"
            "#moj_import <minecraft:projection.glsl>\nvoid main(){fog_spherical_distance(vec3(0));}\n",
            encoding="utf-8",
        )
        shader.with_suffix(".fsh").write_text(
            "#version 330\n#moj_import <minecraft:dynamictransforms.glsl>\n"
            "void main(){apply_fog(vec4(0),0,0,0,0,0,0,vec4(0));}\n",
            encoding="utf-8",
        )
        manifest = root / "assets" / "icesmp_hud" / "hud-manifest.json"
        manifest.parent.mkdir(parents=True, exist_ok=True)
        manifest.write_text('{"schema":1}\n', encoding="utf-8")

    def test_legacy_hud_shader_contract_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "source"
            self.make_pack(root)
            shader_root = root / "assets" / "minecraft" / "shaders" / "core"
            shader_root.mkdir(parents=True, exist_ok=True)
            (shader_root / "rendertype_text.vsh").write_text(
                "#version 150\nuniform int FogShape;\n", encoding="utf-8")
            (shader_root / "rendertype_text.fsh").write_text(
                "#version 150\nuniform vec4 FogColor;\nvoid main(){linear_fog();}\n",
                encoding="utf-8",
            )
            with self.assertRaisesRegex(resource_pack.PackError, "Minecraft 1.21.11"):
                resource_pack.validate_pack(root)

    def test_deterministic_zip_ignores_mtime_and_source_only_docs(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "source"
            self.make_pack(root)
            first = Path(temp) / "first.zip"
            second = Path(temp) / "second.zip"

            first_hash, _ = resource_pack.build_pack(root, first)
            (root / "README.md").write_text("edited repository documentation\n", encoding="utf-8")
            for path in root.rglob("*"):
                os.utime(path, (1_900_000_000, 1_900_000_000))
            second_hash, _ = resource_pack.build_pack(root, second)

            self.assertEqual(first_hash, second_hash)
            self.assertEqual(first.read_bytes(), second.read_bytes())
            self.assertEqual(first_hash, hashlib.sha1(first.read_bytes()).hexdigest())
            with zipfile.ZipFile(first) as archive:
                self.assertEqual(
                    archive.namelist(),
                    [
                        "assets/icesmp/models/example.json",
                        "pack.mcmeta",
                        "pack.png",
                    ],
                )

    def test_invalid_json_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "source"
            self.make_pack(root)
            (root / "assets" / "icesmp" / "models" / "example.json").write_text("{", encoding="utf-8")
            with self.assertRaises(resource_pack.PackError):
                resource_pack.validate_pack(root)

    def test_generated_zip_inside_source_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "source"
            self.make_pack(root)
            (root / "old.zip").write_bytes(b"not a source file")
            with self.assertRaises(resource_pack.PackError):
                resource_pack.validate_pack(root)

    def test_equipment_layer_texture_reference_is_validated(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "resource-pack"
            self.make_pack(root)
            self.add_equipment(root, with_texture=False)

            with self.assertRaisesRegex(resource_pack.PackError, "references missing texture"):
                resource_pack.validate_pack(root)

    def test_explicit_equipment_asset_must_exist(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "resource-pack"
            self.make_pack(root)
            self.write_config(
                root,
                "loot.yml",
                'loot:\n  table:\n    - { type: named, item: IRON_CHESTPLATE, '
                'item-model: "icesmp:test_helmet", equipment-asset: "icesmp:missing" }\n',
            )

            with self.assertRaisesRegex(resource_pack.PackError, "equipment-asset icesmp:missing"):
                resource_pack.validate_pack(root)

    def test_equippable_same_id_fallback_must_have_asset(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "resource-pack"
            self.make_pack(root)
            self.write_config(
                root,
                "profession-recipes.yml",
                "profession-recipes:\n  helmet:\n    result:\n      material: IRON_HELMET\n"
                '      item-model: "icesmp:missing_helmet"\n',
            )

            with self.assertRaisesRegex(resource_pack.PackError, "same-id fallback requires equipment asset"):
                resource_pack.validate_pack(root)

    def test_equippable_same_id_fallback_accepts_existing_asset_and_texture(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "resource-pack"
            self.make_pack(root)
            self.add_equipment(root, asset="test_helmet")
            self.write_config(
                root,
                "profession-recipes.yml",
                "profession-recipes:\n  helmet:\n    result:\n      material: IRON_HELMET\n"
                '      item-model: "icesmp:test_helmet"\n',
            )

            resource_pack.validate_pack(root)

    def test_versioned_policy_covers_body_and_saddle_equipment(self) -> None:
        self.assertEqual(resource_pack.FALLBACK_MINECRAFT_VERSION, "1.21.11")
        for material in (
            "IRON_HORSE_ARMOR",
            "GOLDEN_HORSE_ARMOR",
            "DIAMOND_HORSE_ARMOR",
            "WOLF_ARMOR",
            "SADDLE",
            "WHITE_HARNESS",
        ):
            with self.subTest(material=material):
                self.assertTrue(resource_pack.allows_implicit_same_id_fallback(material))

    def test_horse_armor_same_id_fallback_must_have_asset(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "resource-pack"
            self.make_pack(root)
            self.write_config(
                root,
                "profession-recipes.yml",
                "profession-recipes:\n  horse:\n    result: { material: IRON_HORSE_ARMOR, "
                'item-model: "icesmp:vas_lopancel" }\n',
            )

            with self.assertRaisesRegex(resource_pack.PackError, "same-id fallback requires equipment asset"):
                resource_pack.validate_pack(root)

    def test_horse_armor_same_id_fallback_accepts_horse_body_asset(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "resource-pack"
            self.make_pack(root)
            self.add_equipment(root, asset="vas_lopancel", layer="horse_body")
            self.write_config(
                root,
                "profession-recipes.yml",
                "profession-recipes:\n  horse:\n    result: { material: IRON_HORSE_ARMOR, "
                'item-model: "icesmp:vas_lopancel" }\n',
            )

            resource_pack.validate_pack(root)

    def test_non_equippable_item_model_does_not_require_equipment_asset(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "resource-pack"
            self.make_pack(root)
            self.write_config(
                root,
                "profession-recipes.yml",
                "profession-recipes:\n  paper:\n    result:\n      material: PAPER\n"
                '      item-model: "icesmp:paper_icon"\n',
            )

            resource_pack.validate_pack(root)

    def test_metadata_update_is_idempotent(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            metadata = Path(temp) / "resource-pack.properties"
            sha1 = "a" * 40
            self.assertTrue(resource_pack.update_metadata(metadata, "https://example.invalid/pack.zip", sha1))
            self.assertFalse(resource_pack.update_metadata(metadata, "https://example.invalid/pack.zip", sha1))
            self.assertIn(f"sha1={sha1}", metadata.read_text(encoding="utf-8"))

    def test_owned_layer_merge_is_deterministic_and_preserves_external_files(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "resource-pack"
            self.make_pack(root)
            self.add_first_party_hud_layer(root)
            base = Path(temp) / "external.zip"
            with zipfile.ZipFile(base, "w") as archive:
                archive.writestr("pack.mcmeta", '{"pack":{"description":"external","pack_format":1}}')
                archive.writestr("assets/external/example.txt", "base")
                archive.writestr(
                    "assets/minecraft/shaders/core/rendertype_text.vsh", "external-shader")
            first = Path(temp) / "first.zip"
            second = Path(temp) / "second.zip"
            metadata = Path(temp) / "merged.properties"

            first_hash, first_size = resource_pack.merge_pack(base, root, first, metadata)
            second_hash, second_size = resource_pack.merge_pack(base, root, second)

            self.assertEqual((first_hash, first_size), (second_hash, second_size))
            self.assertEqual(first.read_bytes(), second.read_bytes())
            self.assertIn(f"sha1={first_hash}", metadata.read_text(encoding="utf-8"))
            with zipfile.ZipFile(first) as archive:
                self.assertEqual(archive.read("assets/external/example.txt"), b"base")
                self.assertEqual(
                    archive.read("assets/minecraft/shaders/core/rendertype_text.vsh")
                    .decode("utf-8").splitlines()[0],
                    "#version 330",
                )
                pack = json.loads(archive.read("pack.mcmeta"))
                self.assertEqual(pack["pack"]["min_format"], 75)
                self.assertEqual(pack["pack"]["max_format"], 75)

    def test_unowned_merge_collision_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "resource-pack"
            self.make_pack(root)
            self.add_first_party_hud_layer(root)
            conflict = root / "assets" / "minecraft" / "models" / "conflict.json"
            conflict.parent.mkdir(parents=True, exist_ok=True)
            conflict.write_text('{"parent":"minecraft:item/generated"}', encoding="utf-8")
            base = Path(temp) / "external.zip"
            with zipfile.ZipFile(base, "w") as archive:
                archive.writestr("pack.mcmeta", '{"pack":{"description":"external"}}')
                archive.writestr("assets/minecraft/models/conflict.json", "{}")

            with self.assertRaisesRegex(resource_pack.PackError, "Unowned resource-pack collision"):
                resource_pack.merge_pack(base, root, Path(temp) / "merged.zip")


if __name__ == "__main__":
    unittest.main()
