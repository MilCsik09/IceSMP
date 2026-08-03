import base64
import importlib.util
import json
import struct
import sys
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "validate_fancynpcs_snapshot.py"
SPEC = importlib.util.spec_from_file_location("validate_fancynpcs_snapshot", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class FancyNpcsSnapshotValidatorTest(unittest.TestCase):
    def test_parse_and_sanitize(self):
        with tempfile.TemporaryDirectory() as temp:
            config = Path(temp) / "npcs.yml"
            config.write_text(
                "npcs:\n"
                "  abc:\n"
                "    name: test_npc\n"
                "    skin:\n"
                "      identifier: https://example.test/skin.png?token=secret\n",
                encoding="utf-8",
            )
            refs = MODULE.parse_references(config)
            self.assertEqual(1, len(refs))
            self.assertEqual("test_npc", refs[0].npc_name)
            self.assertEqual("https://example.test/skin.png", MODULE.sanitized_url(refs[0].identifier))

    def test_local_png_dimensions(self):
        with tempfile.TemporaryDirectory() as temp:
            skin = Path(temp) / "skin.png"
            skin.write_bytes(
                b"\x89PNG\r\n\x1a\n"
                + struct.pack(">I", 13)
                + b"IHDR"
                + struct.pack(">II", 64, 64)
            )
            self.assertEqual((64, 64), MODULE.png_dimensions(skin))

    def test_remote_cache_requires_matching_signed_texture(self):
        with tempfile.TemporaryDirectory() as temp:
            cache_dir = Path(temp)
            reference = MODULE.SkinReference(
                "abc", "test_npc", "https://example.test/skin.png",
                "npcs.abc.skin.identifier",
            )
            expected_name = base64.b64encode(reference.identifier.encode()).decode() + ".json"
            self.assertEqual(cache_dir / expected_name,
                             MODULE.cache_path_for_identifier(cache_dir, reference.identifier))
            self.assertIn("CACHE_MISSING", MODULE.validate_remote_cache(reference, cache_dir))
            (cache_dir / expected_name).write_text(json.dumps({
                "skinData": {
                    "identifier": reference.identifier,
                    "textureValue": "value",
                    "textureSignature": "signature",
                }
            }), encoding="utf-8")
            self.assertIsNone(MODULE.validate_remote_cache(reference, cache_dir))

    def test_remote_cache_rejects_identifier_mismatch(self):
        with tempfile.TemporaryDirectory() as temp:
            cache_dir = Path(temp)
            reference = MODULE.SkinReference(
                "abc", "test_npc", "https://example.test/skin.png",
                "npcs.abc.skin.identifier",
            )
            path = MODULE.cache_path_for_identifier(cache_dir, reference.identifier)
            path.write_text(json.dumps({
                "skinData": {
                    "identifier": "https://example.test/other.png",
                    "textureValue": "value",
                    "textureSignature": "signature",
                }
            }), encoding="utf-8")
            self.assertIn("identifier_mismatch", MODULE.validate_remote_cache(reference, cache_dir))


if __name__ == "__main__":
    unittest.main()
