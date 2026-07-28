from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))

from repository_inventory.delta import compare_inventories
from repository_inventory.inventory import generate_inventory


class InventoryFixtureTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        (self.root / "src/main/java/example/commands").mkdir(parents=True)
        (self.root / "src/main/java/example/core").mkdir(parents=True)
        (self.root / "src/main/java/example/managers").mkdir(parents=True)
        (self.root / "src/main/java/example/listeners").mkdir(parents=True)
        (self.root / "src/main/resources/config").mkdir(parents=True)
        (self.root / "docs").mkdir()
        subprocess.run(["git", "init", "-q"], cwd=self.root, check=True)
        subprocess.run(["git", "config", "user.email", "fixture@example.invalid"], cwd=self.root, check=True)
        subprocess.run(["git", "config", "user.name", "Fixture"], cwd=self.root, check=True)

    def tearDown(self) -> None:
        self.temp.cleanup()

    def write(self, relative: str, text: str) -> None:
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")

    def manifest(self, extra: dict | None = None) -> None:
        data = {"version": 1, "commands": {}, "features": {}, "permissions": {},
                "config-sections": {}, "components": {}, "explicit-ignores": {}}
        if extra:
            for key, value in extra.items(): data[key].update(value)
        self.write("docs/documentation-manifest.yml", json.dumps(data, indent=2))

    def commit(self, message: str = "fixture") -> str:
        subprocess.run(["git", "add", "."], cwd=self.root, check=True)
        subprocess.run(["git", "commit", "-qm", message], cwd=self.root, check=True)
        return subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=self.root, text=True).strip()

    def base_sources(self, duplicate_alias: bool = False) -> None:
        alias = 'List.of("tool", "t")' if not duplicate_alias else 'List.of("other")'
        self.write("src/main/java/example/core/Core.java", f'''package example.core;
import java.util.List;
public final class Core {{
  void register() {{
    plugin.registerCommand(
      "root", "Fixture root", {alias},
      new example.commands.RootCommand());
    plugin.registerCommand("dispatch", "Dispatch", List.of("d"), new example.commands.DispatchCommand());
    plugin.registerCommand("other", "Other", List.of("tool"), new example.commands.OtherCommand());
  }}
}}''')
        self.write("src/main/java/example/commands/RootCommand.java", '''package example.commands;
import java.util.*;
public final class RootCommand implements BasicCommand {
 public static final String ADMIN_PERMISSION = "icesmp.admin.root";
 public void execute(CommandSourceStack stack, String[] args) {
   var sender = stack.getSender();
   if (!(sender instanceof Player player)) return;
   if (args.length == 0) { sendHelp(player); return; }
   switch (args[0].toLowerCase(Locale.ROOT)) {
     case "show" -> handleShow(player, args);
     case "set" -> handleSet(player, args);
     case "dynamic" -> helper(player, args);
     default -> sendHelp(player);
   }
 }
 private void handleShow(Player player, String[] args) {
   player.sendMessage(messages.get("root-show", "Shown"));
   player.sendMessage(messages.get("root-missing", "Missing default"));
 }
 private void handleSet(Player player, String[] args) { if (!player.hasPermission(ADMIN_PERMISSION)) return; player.sendMessage(messages.get("root-set", "Set")); }
 private void helper(Player player, String[] args) {
   if (args.length < 2) return;
   switch (args[1].toLowerCase(java.util.Locale.ROOT)) {
     case "nested" -> player.sendMessage("hard coded nested");
     default -> {}
   }
 }
 private void sendHelp(Player player) { player.sendMessage(messages.get("root-help", "/root show | /root set <value>")); }
 public Collection<String> suggest(CommandSourceStack stack, String[] args) { return List.of("show", "set", "dynamic"); }
}''')
        self.write("src/main/java/example/commands/DispatchCommand.java", '''package example.commands;
public final class DispatchCommand extends AbstractDispatchCommand {
 DispatchCommand() { super(null, "dispatch", "help"); register(new AlphaSubcommand()); }
}''')
        self.write("src/main/java/example/commands/AlphaSubcommand.java", '''package example.commands;
public final class AlphaSubcommand implements Subcommand {
 public String name(){ return "alpha"; }
 public String description(){ return "Alpha action"; }
 public String usage(){ return "/dispatch alpha <player> [count]"; }
 public boolean execute(CommandSender sender, String[] args){ return true; }
 public java.util.List<String> tabComplete(CommandSender sender, String[] args){ return java.util.List.of("A"); }
}''')
        self.write("src/main/java/example/commands/OtherCommand.java", '''package example.commands;
public final class OtherCommand implements BasicCommand { public void execute(CommandSourceStack s, String[] a){} }''')
        self.write("src/main/java/example/commands/AbstractDispatchCommand.java", '''package example.commands;
public abstract class AbstractDispatchCommand implements BasicCommand { AbstractDispatchCommand(Object a,String b,String c){} protected void register(Subcommand s){} }''')
        self.write("src/main/java/example/commands/Subcommand.java", '''package example.commands; public interface Subcommand {}''')
        self.write("src/main/java/example/managers/RootManager.java", '''package example.managers;
public final class RootManager { boolean enabled(){ return configManager.getBoolean("root.enabled", true); } }''')
        self.write("src/main/java/example/listeners/RootListener.java", '''package example.listeners;
public final class RootListener { void event(Player p){ if(p.hasPermission("icesmp.root.bypass")){} } }''')
        self.write("src/main/java/example/listeners/MessageFallbackListener.java", '''package example.listeners;
public final class MessageFallbackListener { void event(Player p){ p.sendMessage(messageManager.get("root-show", "Different shown")); } }''')
        self.write("src/main/resources/config/root.yml", '''root:
  enabled: true
  limit: 5
messages:
  ignored: value
''')
        self.write("src/main/resources/messages.yml", '''root-show: Shown
root-set: Set
root-help: Help
unused-message: Unused
''')

    def test_root_alias_switch_dispatch_permission_config_message_and_features(self) -> None:
        self.base_sources()
        marker_ids = ["command.root", "command.root.show", "command.root.set", "command.root.dynamic",
                      "command.dispatch", "command.dispatch.alpha", "alias.root.tool", "alias.root.t", "alias.dispatch.d"]
        docs = "\n".join(f"<!-- icesmp-doc-id: {item} -->" for item in marker_ids)
        self.write("docs/commands.md", docs)
        command_manifest = {item: {"docs": ["docs/commands.md"]} for item in marker_ids}
        self.manifest({"commands": command_manifest,
                       "config-sections": {"config.root": {"docs": []}},
                       "permissions": {"permission.icesmp.admin.root": {"docs": []}, "permission.icesmp.root.bypass": {"docs": []}}})
        self.commit()
        inventory = generate_inventory(self.root)
        roots = {x["name"]: x for x in inventory["commands"]}
        self.assertEqual(roots["root"]["aliases"], ["t", "tool"])
        self.assertTrue(roots["root"]["player_only"])
        paths = {x["path"]: x for x in inventory["subcommands"]}
        self.assertIn("root.show", paths)
        self.assertIn("root.set", paths)
        self.assertIn("dispatch.alpha", paths)
        self.assertIn("root.nested", paths)
        self.assertEqual(paths["root.nested"]["confidence"], "REVIEW_REQUIRED")
        self.assertEqual(paths["dispatch.alpha"]["arguments"][0]["name"], "player")
        self.assertIn("icesmp.admin.root", paths["root.set"]["permission"])
        self.assertTrue(paths["root.show"]["tab_completion"])
        self.assertIn("icesmp.root.bypass", {x["node"] for x in inventory["permissions"]})
        self.assertIn("root.enabled", {x["path"] for x in inventory["config_keys"]})
        self.assertIn("root-show", {x["key"] for x in inventory["message_keys"]})
        codes = {x["code"] for x in inventory["findings"]}
        self.assertIn("MESSAGE_KEY_MISSING_DEFAULT", codes)
        self.assertIn("MESSAGE_FALLBACK_DRIFT", codes)
        self.assertIn("HARDCODED_PLAYER_MESSAGE", codes)
        self.assertIn("NESTED_PARENT_UNRESOLVED", codes)
        self.assertFalse(roots["other"]["player_only"])
        self.assertTrue(roots["other"]["console_compatible"])
        self.assertIn("feature.root", {x["id"] for x in inventory["features"]})

    def test_duplicate_alias_is_blocking(self) -> None:
        self.base_sources(duplicate_alias=True)
        self.manifest(); self.commit()
        inventory = generate_inventory(self.root)
        self.assertIn("COMMAND_OR_ALIAS_COLLISION", {x["code"] for x in inventory["findings"] if x["severity"] == "FAIL"})

    def test_dynamic_registration_and_missing_marker_are_visible(self) -> None:
        self.write("src/main/java/example/core/Core.java", '''package example.core;
public final class Core { void r(){ plugin.registerCommand(COMMAND_NAME, "x", aliases(), factory()); } }''')
        self.manifest({"commands": {"command.review-2": {"docs": ["docs/missing.md"]}}})
        self.commit()
        inventory = generate_inventory(self.root)
        codes = {x["code"] for x in inventory["findings"]}
        self.assertIn("DYNAMIC_COMMAND_NAME", codes)
        self.assertIn("COMMAND_IMPLEMENTATION_UNRESOLVED", codes)

    def test_stale_manifest_and_missing_marker_are_reported(self) -> None:
        self.base_sources()
        self.write("docs/commands.md", "# Commands\n")
        self.manifest({"commands": {
            "command.root": {"docs": ["docs/commands.md"]},
            "command.removed": {"docs": ["docs/commands.md"]}
        }})
        self.commit()
        inventory = generate_inventory(self.root)
        codes = {x["code"] for x in inventory["findings"]}
        self.assertIn("DOC_MARKER_MISSING", codes)
        self.assertIn("STALE_MANIFEST_ENTRY", codes)

    def test_repository_cli_writes_complete_artifact_set(self) -> None:
        self.base_sources(); self.manifest(); self.commit()
        output = self.root / "build/repository-inventory"
        completed = subprocess.run([sys.executable, str(SCRIPTS / "generate_repository_inventory.py"),
                                    "--root", str(self.root), "--output", str(output), "--mode", "report"],
                                   cwd=self.root, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self.assertIn(completed.returncode, (0, 1), completed.stderr)
        expected = {"repository-inventory.json", "commands.json", "commands.md", "permissions.json",
                    "permissions.md", "config-keys.json", "config-keys.md", "message-keys.json",
                    "message-keys.md", "features.json", "features.md", "components.json",
                    "documentation-coverage.json", "documentation-coverage.md", "review-required.md",
                    "summary.md"}
        self.assertTrue(expected.issubset({p.name for p in output.iterdir()}))

    def test_inventory_fingerprint_is_deterministic(self) -> None:
        self.base_sources(); self.manifest(); self.commit()
        one = generate_inventory(self.root)
        two = generate_inventory(self.root)
        self.assertEqual(one["deterministic_fingerprint"], two["deterministic_fingerprint"])

    def test_delta(self) -> None:
        base = {"commands": [{"id": "command.a", "name": "a"}], "subcommands": [], "permissions": [], "config_keys": [], "message_keys": [], "features": [], "components": []}
        head = {"commands": [{"id": "command.a", "name": "a", "aliases": ["x"]}, {"id": "command.b", "name": "b"}], "subcommands": [], "permissions": [], "config_keys": [], "message_keys": [], "features": [], "components": []}
        delta = compare_inventories(base, head)["command_delta"]
        self.assertEqual(delta["added"], ["command.b"])
        self.assertEqual(delta["modified"], ["command.a"])


if __name__ == "__main__":
    unittest.main()
