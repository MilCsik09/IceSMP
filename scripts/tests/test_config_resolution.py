from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))

from repository_inventory.config_scanner import scan_config
from repository_inventory.java_scanner import JavaIndex


class ConfigResolutionTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        source = self.root / "src/main/java/example/Demo.java"
        source.parent.mkdir(parents=True)
        source.write_text(
            """package example;
public final class Demo {
  void load(ConfigurationSection section, ConfigManager configManager) {
    section.getString("name", "default-name");
    configManager.getInt("missing", 7);
    configManager.getInt("duration", 15);
    configManager.getLong("duration", 15L);
  }
}
""",
            encoding="utf-8",
        )
        config = self.root / "src/main/resources/config/general.yml"
        config.parent.mkdir(parents=True)
        config.write_text("duration: 15\n", encoding="utf-8")

    def tearDown(self) -> None:
        self.temp.cleanup()

    def scan(self, resolutions: dict | None = None):
        manifest = {
            "config-sections": {},
            "config-resolutions": resolutions or {},
        }
        return scan_config(self.root, JavaIndex(self.root), manifest)

    def test_unresolved_dynamic_and_source_gaps_are_visible(self) -> None:
        _, findings = self.scan()
        codes = {(item.severity, item.code) for item in findings}
        self.assertIn(("REVIEW_REQUIRED", "DYNAMIC_CONFIG_PATH"), codes)
        self.assertIn(("FAIL", "CONFIG_KEY_MISSING_DEFAULT"), codes)
        self.assertIn(("FAIL", "CONFIG_TYPE_MISMATCH"), codes)

    def test_exact_resolutions_remove_review_and_preserve_warnings(self) -> None:
        resolutions = {
            "config.dynamic.demo.section.name": {
                "classification": "EXACT_RUNTIME_TEMPLATE",
                "resolved_path": "items.<id>.name",
                "reason": "The caller passes the items.<id> section.",
            },
            "config.missing": {
                "classification": "KNOWN_SOURCE_LIMITATION",
                "resolved_path": "missing",
                "reason": "No bundled value; the code fallback is 7.",
            },
            "config.duration": {
                "classification": "RESOLVED_TYPE_VARIANCE",
                "resolved_path": "duration",
                "reason": "Both readers consume the same whole-number value.",
            },
        }
        keys, findings = self.scan(resolutions)
        blocking = [
            item for item in findings
            if item.severity in {"FAIL", "REVIEW_REQUIRED"}
        ]
        self.assertEqual(blocking, [])
        codes = {(item.severity, item.code) for item in findings}
        self.assertIn(("WARN", "KNOWN_CONFIG_DEFAULT_GAP"), codes)
        self.assertIn(("WARN", "RESOLVED_CONFIG_TYPE_VARIANCE"), codes)
        dynamic = next(
            item for item in keys
            if item["id"] == "config.dynamic.demo.section.name"
        )
        self.assertEqual(dynamic["confidence"], "HIGH")
        self.assertEqual(dynamic["resolved_yaml_path"], "items.<id>.name")

    def test_stale_resolution_is_blocking(self) -> None:
        _, findings = self.scan({
            "config.removed": {
                "reason": "Old path.",
                "resolved_path": "removed",
            }
        })
        self.assertIn(
            ("FAIL", "STALE_CONFIG_RESOLUTION"),
            {(item.severity, item.code) for item in findings},
        )


if __name__ == "__main__":
    unittest.main()
