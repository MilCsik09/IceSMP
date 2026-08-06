from __future__ import annotations

import sys
import unittest
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCRIPTS = ROOT / "scripts"
if str(SCRIPTS) not in sys.path:
    sys.path.insert(0, str(SCRIPTS))

import check_player_profile_authority as authority  # noqa: E402


class PlayerProfileTransitionGateTest(unittest.TestCase):
    def test_no_legacy_player_authority_is_transitional(self) -> None:
        report = authority.audit(
            ROOT, ROOT / "scripts/player_profile_authority_allowlist.json"
        )
        transitions = list(report["transitions"])
        by_path: Counter[str] = Counter(
            str(entry.get("path", "<unresolved>")) for entry in transitions
        )
        detail = "\n".join(
            f"{count:4d} {path}"
            for path, count in sorted(by_path.items(), key=lambda item: (-item[1], item[0]))
        )
        self.assertEqual(
            [],
            transitions,
            "Legacy PlayerProfile transition authorities remain:\n" + detail,
        )


if __name__ == "__main__":
    unittest.main()
