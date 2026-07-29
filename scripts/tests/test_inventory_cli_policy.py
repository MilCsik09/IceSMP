from __future__ import annotations

import sys
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))

from generate_repository_inventory import exit_code_for_inventory


class InventoryCliPolicyTest(unittest.TestCase):
    def test_report_mode_surfaces_failures_without_blocking_adoption(self) -> None:
        inventory = {"findings": [{"severity": "FAIL", "code": "EXAMPLE"}]}
        self.assertEqual(0, exit_code_for_inventory("report", inventory))

    def test_strict_mode_blocks_fail_findings(self) -> None:
        inventory = {"findings": [{"severity": "FAIL", "code": "EXAMPLE"}]}
        self.assertEqual(1, exit_code_for_inventory("strict", inventory))

    def test_strict_mode_succeeds_without_fail_findings(self) -> None:
        inventory = {"findings": [{"severity": "REVIEW_REQUIRED", "code": "EXAMPLE"}]}
        self.assertEqual(0, exit_code_for_inventory("strict", inventory))


if __name__ == "__main__":
    unittest.main()
