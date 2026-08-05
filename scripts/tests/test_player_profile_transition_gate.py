from __future__ import annotations

import json
import unittest
from collections import Counter
from pathlib import Path


class PlayerProfileTransitionGateTest(unittest.TestCase):
    def test_no_legacy_player_authority_is_transitional(self) -> None:
        root = Path(__file__).resolve().parents[2]
        payload = json.loads(
            (root / "scripts/player_profile_authority_allowlist.json").read_text(encoding="utf-8")
        )
        transition_by_key = {
            str(entry.get("key", "")): entry
            for entry in payload.get("entries", [])
            if entry.get("category") == "TRANSITION" and str(entry.get("key", ""))
        }
        transitions = [transition_by_key[key] for key in sorted(transition_by_key)]
        by_path: Counter[str] = Counter()
        for entry in transitions:
            parts = str(entry.get("key", "")).split("|", 2)
            by_path[parts[1] if len(parts) > 1 else "<unresolved>"] += 1
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
