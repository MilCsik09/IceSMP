from __future__ import annotations

import json
import subprocess
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REPORT = ROOT / "build/reports/equipment-rp2/asset-audit.json"


class EquipmentRp2AssetAuditTest(unittest.TestCase):
    def test_production_shape_and_report_generation(self) -> None:
        completed = subprocess.run(
            [sys.executable, "scripts/equipment_rp2_asset_audit.py"],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(0, completed.returncode, completed.stdout + completed.stderr)
        self.assertTrue(REPORT.is_file(), "RP2 asset audit did not create its report")
        report = json.loads(REPORT.read_text(encoding="utf-8"))
        self.assertEqual(160, report["production_authority"]["canonical_armor"])
        self.assertEqual(40, report["production_authority"]["gear_lines"])
        self.assertEqual(27, report["production_authority"]["managed_materials"])
        self.assertFalse(report["shape_errors"])
        print(completed.stdout.strip())
        print("RP2_BASELINE_STATUS_COUNTS=" + json.dumps(report["asset_counts"], sort_keys=True))
        print("RP2_BASELINE_SAFE_DELETE=" + json.dumps([row["path"] for row in report["safe_to_delete"]], sort_keys=True))
        print("RP2_BASELINE_CUSTOM_WORN=" + json.dumps([
            row["template_id"] for row in report["armor"] if row["current_worn_asset"]
        ], sort_keys=True))


if __name__ == "__main__":
    unittest.main()
