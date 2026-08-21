from __future__ import annotations

import json
import subprocess
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REPORT = ROOT / "build/reports/equipment-rp2/asset-audit.json"
FINAL = ROOT / "build/reports/equipment-rp2/final/equipment-rp2-final-authority.json"
POLICY = ROOT / "src/main/resources/wearable-fallback-policy.properties"
WEARABLE = ROOT / "src/main/java/hu/taliann/icesmp/items/WearablePresentation.java"


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

    def test_final_authority_closes_runtime_broken_references(self) -> None:
        completed = subprocess.run(
            [sys.executable, "scripts/generate_equipment_rp2_manifests.py", "--enforce"],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(0, completed.returncode, completed.stdout + completed.stderr)
        authority = json.loads(FINAL.read_text(encoding="utf-8"))
        summary = authority["summary"]
        self.assertEqual("SOURCE_COMPLETE_AUTOMATED_TESTED_OFFLINE_VISUAL_PROVED", authority["readiness"])
        self.assertEqual(0, summary["broken_production_reference"])
        self.assertEqual(0, summary["missing_mandatory_active_asset"])
        self.assertEqual(0, summary["safe_delete_asset_referenced"])
        self.assertEqual(0, summary["stale_active_reference"])
        self.assertEqual(0, summary["armor_pieces_temporarily_vanilla_worn"])
        self.assertEqual(160, summary["custom_worn_assets_still_active"])
        self.assertEqual(84, summary["intentional_recipe_inventory_fallbacks"])
        self.assertEqual(0, summary["rp2_worn_line_sets_required"])
        self.assertEqual(27, summary["managed_materials"])
        print(completed.stdout.strip())

    def test_vanilla_worn_reset_repairs_serialized_asset_id_without_rebuilding_equipment(self) -> None:
        source = WEARABLE.read_text(encoding="utf-8")
        compact = "".join(source.split())
        self.assertIn("RP2_PRODUCTION.matches(requestedModel,requestedEquipment)", compact)
        self.assertIn("forcesVanillaWornMaterial(item.getType().name())", compact)
        self.assertLess(compact.index("RP2_PRODUCTION.matches(requestedModel,requestedEquipment)"),
                        compact.index("forcesVanillaWornMaterial(item.getType().name())"))
        self.assertIn("newItemStack(item.getType()).getData(DataComponentTypes.EQUIPPABLE)", compact)
        self.assertIn("current.toBuilder().assetId(vanilla.assetId()).build()", compact)
        self.assertNotIn("Equippable.equippable(", source)
        self.assertNotIn(".slot(", source)
        self.assertIn("VANILLA_FALLBACK_APPLIED", source)

        properties: dict[str, str] = {}
        for raw in POLICY.read_text(encoding="utf-8").splitlines():
            line = raw.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            properties[key.strip()] = value.strip()
        worn_rules = {part.strip() for part in properties["vanilla-worn-suffix"].split(",") if part.strip()}
        self.assertIn("ELYTRA", worn_rules, "fonixpihe_kopeny is an ELYTRA-backed canonical chest anchor")
        self.assertTrue({"_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS"}.issubset(worn_rules))
        fallback_models = {
            part.strip() for part in properties["vanilla-item-model"].split(",") if part.strip()
        }
        self.assertEqual(84, len(fallback_models))


if __name__ == "__main__":
    unittest.main()
