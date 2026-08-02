import importlib.util
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


if __name__ == "__main__":
    unittest.main()
