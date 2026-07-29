from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]


class ReleaseInventoryCliTest(unittest.TestCase):
    def test_two_ref_worktree_comparison_does_not_move_head(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "scripts").symlink_to(SCRIPTS, target_is_directory=True)
            for directory in ("src/main/java/x", "src/main/resources", "docs"):
                (root / directory).mkdir(parents=True, exist_ok=True)
            manifest = {"version": 1, "commands": {}, "features": {}, "permissions": {}, "config-sections": {}, "components": {}, "explicit-ignores": {}}
            (root / "docs/documentation-manifest.yml").write_text(json.dumps(manifest), encoding="utf-8")
            subprocess.run(["git", "init", "-q"], cwd=root, check=True)
            subprocess.run(["git", "config", "user.email", "fixture@example.invalid"], cwd=root, check=True)
            subprocess.run(["git", "config", "user.name", "Fixture"], cwd=root, check=True)
            (root / "src/main/java/x/A.java").write_text("package x; class A {}", encoding="utf-8")
            subprocess.run(["git", "add", "."], cwd=root, check=True)
            subprocess.run(["git", "commit", "-qm", "base"], cwd=root, check=True)
            base = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
            (root / "src/main/java/x/BManager.java").write_text("package x; class BManager {}", encoding="utf-8")
            subprocess.run(["git", "add", "."], cwd=root, check=True)
            subprocess.run(["git", "commit", "-qm", "head"], cwd=root, check=True)
            head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
            output = root / "build/release"
            completed = subprocess.run([sys.executable, str(SCRIPTS / "generate_release_inventory.py"),
                                        "--root", str(root), "--base-ref", base, "--head-ref", head,
                                        "--output", str(output)], cwd=root, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            self.assertEqual(completed.returncode, 0, completed.stderr)
            self.assertEqual(subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip(), head)
            data = json.loads((output / "release-inventory.json").read_text(encoding="utf-8"))
            self.assertEqual(data["metadata"]["base_sha"], base)
            self.assertEqual(data["metadata"]["head_sha"], head)
            self.assertIn("feature.b", data["delta"]["feature_delta"]["added"])


if __name__ == "__main__":
    unittest.main()
