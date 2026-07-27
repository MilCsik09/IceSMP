#!/usr/bin/env python3
from __future__ import annotations

import pathlib

ROOT = pathlib.Path.cwd()


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


def main() -> None:
    manager_path = ROOT / "src/main/java/hu/taliann/icesmp/managers/DevItemManager.java"
    manager = manager_path.read_text(encoding="utf-8")
    manager = replace_once(
        manager,
        "            if (loaded.hasPendingReward() && rankOf(loaded.pendingRarity()) < 0) {\n",
        "            DevItemStateData.validateLegacyReceiptMigration(\n"
        "                    loaded.hasPendingReward(),\n"
        "                    optionalString(yaml, \"bingulus.pending.grant-id\"),\n"
        "                    optionalString(yaml, \"bingulus.pending.recipient\"));\n"
        "            if (loaded.hasPendingReward() && rankOf(loaded.pendingRarity()) < 0) {\n",
        "legacy receipt load gate",
    )
    manager = replace_once(
        manager,
        "    private String requireString(final YamlConfiguration yaml, final String path) {\n",
        "    private String optionalString(final YamlConfiguration yaml, final String path) {\n"
        "        final Object raw = yaml.get(path);\n"
        "        if (raw == null) {\n"
        "            return \"\";\n"
        "        }\n"
        "        if (raw instanceof String value) {\n"
        "            return value;\n"
        "        }\n"
        "        throw new IllegalArgumentException(path + \" must be a string when present\");\n"
        "    }\n\n"
        "    private String requireString(final YamlConfiguration yaml, final String path) {\n",
        "optional legacy string helper",
    )
    manager_path.write_text(manager, encoding="utf-8")

    test_path = ROOT / "src/regression/java/hu/taliann/icesmp/managers/DevItemStateDataRegressionTest.java"
    test = test_path.read_text(encoding="utf-8")
    test = replace_once(
        test,
        "        emptyPendingStateIsValid();\n",
        "        emptyPendingStateIsValid();\n"
        "        receiptFreeLegacySnapshotsRemainCompatible();\n"
        "        partialLegacyReceiptMetadataIsRejected();\n"
        "        receiptBackedPendingRewardRequiresManualReconciliation();\n",
        "regression test invocation",
    )
    test = replace_once(
        test,
        "    private static DevItemStateData pendingState() {\n",
        "    private static void receiptFreeLegacySnapshotsRemainCompatible() {\n"
        "        DevItemStateData.validateLegacyReceiptMigration(false, \"\", \"\");\n"
        "        DevItemStateData.validateLegacyReceiptMigration(true, \"\", \"\");\n"
        "    }\n\n"
        "    private static void partialLegacyReceiptMetadataIsRejected() {\n"
        "        expectThrows(IllegalArgumentException.class,\n"
        "                () -> DevItemStateData.validateLegacyReceiptMigration(true, OWNER.toString(), \"\"));\n"
        "        expectThrows(IllegalArgumentException.class,\n"
        "                () -> DevItemStateData.validateLegacyReceiptMigration(true, \"\", OWNER.toString()));\n"
        "        expectThrows(IllegalArgumentException.class,\n"
        "                () -> DevItemStateData.validateLegacyReceiptMigration(false, OWNER.toString(), NEW_OWNER.toString()));\n"
        "    }\n\n"
        "    private static void receiptBackedPendingRewardRequiresManualReconciliation() {\n"
        "        expectThrows(IllegalArgumentException.class,\n"
        "                () -> DevItemStateData.validateLegacyReceiptMigration(\n"
        "                        true, INSTANCE.toString(), OWNER.toString()));\n"
        "    }\n\n"
        "    private static DevItemStateData pendingState() {\n",
        "legacy receipt regression methods",
    )
    test_path.write_text(test, encoding="utf-8")

    architecture_path = ROOT / "docs/ARCHITECTURE.md"
    architecture = architecture_path.read_text(encoding="utf-8")
    architecture = replace_once(
        architecture,
        "  inventory-módosítás és a pending törlése közötti erőszakos process-kill ablakra.\n",
        "  inventory-módosítás és a pending törlése közötti erőszakos process-kill ablakra.\n"
        "  A korábbi receipt-protokollból maradt, még nyugtázatlan pending rekord fail-closed indul,\n"
        "  mert automatikusan nem dönthető el, hogy a playerdata már megkapta-e a jutalmat.\n",
        "architecture migration caveat",
    )
    architecture_path.write_text(architecture, encoding="utf-8")

    audit_path = ROOT / "docs/audits/IceSMP_audit_update_b6db9d2.md"
    audit = audit_path.read_text(encoding="utf-8")
    audit = replace_once(
        audit,
        "- kézzel sérült playerdata és minden lehetséges storage kombinációja nem kap külön állapotgépet.\n",
        "- kézzel sérült playerdata és minden lehetséges storage kombinációja nem kap külön állapotgépet;\n"
        "- a #33 receipt-protokolljából maradt, receipt-backed pending jutalom automatikus találgatás\n"
        "  helyett fail-closed, egyszeri operátori reconciliationt igényel.\n",
        "audit migration limitation",
    )
    audit = replace_once(
        audit,
        "| Bukkit-független regressziós teszteset | 10 | 7 |\n",
        "| Bukkit-független regressziós teszteset | 10 | 10 |\n",
        "audit test count",
    )
    audit_path.write_text(audit, encoding="utf-8")


if __name__ == "__main__":
    main()
