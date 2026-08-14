from __future__ import annotations

import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
REPOSITORY_ROOT = SCRIPTS.parent
sys.path.insert(0, str(SCRIPTS))

from repository_inventory.java_scanner import JavaIndex
from repository_inventory.permission_scanner import scan_permissions


class PermissionScannerFixtureTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)

    def tearDown(self) -> None:
        self.temp.cleanup()

    def write(self, relative: str, text: str) -> None:
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")

    def sources(self) -> None:
        self.write("src/main/java/example/core/Permissions.java", """
package example.core;
import java.util.*;
public final class Permissions {
  public static final String ADMIN = "icesmp.admin.example";
  public static final String PLAYER = "icesmp.example.use";
  public static void register() {
    final Map<String, String> canonical = new LinkedHashMap<>();
    canonical.put(ADMIN, "Admin");
    for (final Map.Entry<String, String> entry : canonical.entrySet()) {
      registerNode(pm, new Permission(entry.getKey(), entry.getValue(), PermissionDefault.OP));
    }
    registerNode(pm, new Permission(PLAYER, "Player", PermissionDefault.TRUE));
    registerNode(pm, alias("icesmp.example.legacy", ADMIN));
  }
  public static void registerCratePermissions(final Collection<String> permissionNodes) {
    for (final String node : permissionNodes) {
      registerNode(pm, new Permission(node, "Crate", PermissionDefault.FALSE));
    }
  }
  private static Permission alias(final String legacyNode, final String... children) {
    return new Permission(legacyNode, "Legacy", PermissionDefault.OP);
  }
  private static void registerNode(Object pm, Permission permission) {}
}
""")
        self.write("src/main/java/example/core/Core.java", """
package example.core;
public final class Core {
  void register() {
    new example.commands.GenericCommand(Permissions.ADMIN);
  }
}
""")
        self.write("src/main/java/example/commands/GenericCommand.java", """
package example.commands;
public final class GenericCommand {
  private final String permission;
  GenericCommand(final String permission) { this.permission = permission; }
  void execute(Player player) { if (!player.hasPermission(permission)) return; }
}
""")
        self.write("src/main/java/example/gui/PermissionGui.java", """
package example.gui;
import example.core.Permissions;
public final class PermissionGui {
  static void open(Player player) { put(player, Permissions.ADMIN); }
  private static void put(final Player player, final String permission) {
    if (!player.hasPermission(permission)) return;
  }
}
""")
        self.write("src/main/java/example/commands/ChoiceCommand.java", """
package example.commands;
import example.core.Permissions;
public final class ChoiceCommand {
  void execute(Player player, boolean edit) {
    final String required = edit ? Permissions.ADMIN : Permissions.PLAYER;
    if (!player.hasPermission(required)) return;
  }
}
""")
        self.write("src/main/java/example/listeners/SlotListener.java", """
package example.listeners;
import example.core.Permissions;
public final class SlotListener {
  void click(Player player, int slot) {
    final String requiredPermission = permissionForSlot(slot);
    if (requiredPermission != null && !player.hasPermission(requiredPermission)) return;
  }
  private static String permissionForSlot(final int slot) {
    return switch (slot) {
      case 1 -> Permissions.ADMIN;
      case 2 -> Permissions.PLAYER;
      default -> null;
    };
  }
}
""")
        self.write("src/main/java/example/managers/CrateManager.java", """
package example.managers;
import example.core.Permissions;
public final class CrateManager {
  void load(java.util.Collection<Definition> definitions) {
    Permissions.registerCratePermissions(definitions.stream().map(Definition::permission).toList());
  }
  boolean canUse(Player player, Definition definition) {
    return definition.permission() == null || player.hasPermission(definition.permission());
  }
}
""")
        self.write("src/main/resources/config/crates.yml", """
crates:
  public:
    permission: ""
  staff:
    permission: "icesmp.crate.staff"
""")

    def scan(self, manifest: dict | None = None):
        return scan_permissions(
            self.root,
            JavaIndex(self.root),
            [],
            [],
            manifest or {"permissions": {}},
        )

    @staticmethod
    def contract_entries(permissions: list[dict]) -> dict:
        entries: dict[str, dict] = {}
        for item in permissions:
            dynamic = any(source["kind"] == "CONFIG" for source in item["sources"])
            entries[item["id"]] = {
                "kind": "permission",
                "node": item["node"],
                "default": item["default"],
                "registration": "CONFIG_DYNAMIC" if dynamic else (
                    "STATIC" if item["registered"] else "USE_ONLY"
                ),
                "legacy_alias": item["legacy_alias"],
                "children": item["children"],
                "docs": [],
            }
        return entries

    def test_resolves_registry_alias_constructor_local_helper_and_crate_config(self) -> None:
        self.sources()
        permissions, findings = self.scan()
        by_node = {item["node"]: item for item in permissions}

        self.assertEqual(
            {
                "icesmp.admin.example",
                "icesmp.example.use",
                "icesmp.example.legacy",
                "icesmp.crate.staff",
            },
            set(by_node),
        )
        self.assertEqual("OP", by_node["icesmp.admin.example"]["default"])
        self.assertEqual("TRUE", by_node["icesmp.example.use"]["default"])
        self.assertTrue(by_node["icesmp.example.legacy"]["legacy_alias"])
        self.assertEqual(["icesmp.admin.example"], by_node["icesmp.example.legacy"]["children"])
        self.assertEqual("FALSE", by_node["icesmp.crate.staff"]["default"])
        self.assertTrue(by_node["icesmp.crate.staff"]["registered"])
        self.assertTrue(any(
            evidence["symbol"] == "new GenericCommand"
            for evidence in by_node["icesmp.admin.example"]["resolution_evidence"]
        ))
        self.assertTrue(any(
            evidence["symbol"] == "permissionForSlot"
            for evidence in by_node["icesmp.example.use"]["resolution_evidence"]
        ))
        self.assertFalse([finding for finding in findings if finding.code == "PERMISSION_UNRESOLVED"])

    def test_unknown_expression_is_blocking(self) -> None:
        self.write("src/main/java/example/Unknown.java", """
package example;
public final class Unknown {
  void run(Player player) { player.hasPermission(resolveAtRuntime()); }
}
""")
        _, findings = self.scan()
        unresolved = [finding for finding in findings if finding.code == "PERMISSION_UNRESOLVED"]
        self.assertEqual(1, len(unresolved))
        self.assertEqual("FAIL", unresolved[0].severity)

    def test_exact_contract_detects_source_drift(self) -> None:
        self.sources()
        permissions, findings = self.scan()
        self.assertFalse(findings)
        entries = self.contract_entries(permissions)
        source_paths = {
            evidence["source"]
            for item in permissions
            for field in ("sources", "resolution_evidence")
            for evidence in item[field]
            if evidence["source"].startswith("src/")
        }
        source_hashes = {
            relative: hashlib.sha256((self.root / relative).read_bytes()).hexdigest()
            for relative in source_paths
        }
        contract = {
            "expected_counts": {"total": 4, "static_registered": 3, "config_dynamic": 1},
            "source_sha256": source_hashes,
            "entries_sha256": hashlib.sha256(
                json.dumps(entries, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()
            ).hexdigest(),
        }
        manifest = {"permissions": {"_contract": contract, **entries}}
        _, findings = self.scan(manifest)
        self.assertFalse([finding for finding in findings if finding.severity == "FAIL"])

        path = self.root / "src/main/java/example/commands/GenericCommand.java"
        path.write_text(path.read_text(encoding="utf-8") + "\n// drift\n", encoding="utf-8")
        _, findings = self.scan(manifest)
        self.assertIn("PERMISSION_CONTRACT_SOURCE_DRIFT", {finding.code for finding in findings})


class RepositoryPermissionContractTest(unittest.TestCase):
    def test_repository_resolves_exactly_47_static_permissions_without_unknown_expressions(self) -> None:
        permissions, findings = scan_permissions(
            REPOSITORY_ROOT,
            JavaIndex(REPOSITORY_ROOT),
            [],
            [],
            {"permissions": {}},
        )
        by_node = {item["node"]: item for item in permissions}
        static = {
            item["node"] for item in permissions
            if item["registered"] and not any(source["kind"] == "CONFIG" for source in item["sources"])
        }
        dynamic = {
            item["node"] for item in permissions
            if any(source["kind"] == "CONFIG" for source in item["sources"])
        }

        self.assertEqual(47, len(permissions))
        self.assertEqual(47, len(static))
        self.assertEqual(set(), dynamic)
        self.assertTrue(by_node["icesmp.admin"]["legacy_alias"])
        self.assertTrue(by_node["icesmp.job.admin"]["legacy_alias"])
        self.assertTrue(any(
            evidence["symbol"] == "new ModerationActionCommand"
            for evidence in by_node["icesmp.moderation.warn"]["resolution_evidence"]
        ))
        self.assertFalse([
            finding for finding in findings
            if finding.code in {"PERMISSION_UNRESOLVED", "PERMISSION_REGISTRATION_UNRESOLVED"}
        ])


if __name__ == "__main__":
    unittest.main()
