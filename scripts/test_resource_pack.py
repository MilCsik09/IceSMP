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

    def test_deterministic_zip_ignores_mtime_and_has_pack_at_root(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "source"
            self.make_pack(root)
            first = Path(temp) / "first.zip"
            second = Path(temp) / "second.zip"

            first_hash, _ = resource_pack.build_pack(root, first)
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

    def test_metadata_update_is_idempotent(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            metadata = Path(temp) / "resource-pack.properties"
            sha1 = "a" * 40
            self.assertTrue(resource_pack.update_metadata(metadata, "https://example.invalid/pack.zip", sha1))
            self.assertFalse(resource_pack.update_metadata(metadata, "https://example.invalid/pack.zip", sha1))
            self.assertIn(f"sha1={sha1}", metadata.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
