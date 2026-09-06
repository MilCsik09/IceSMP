"""Static release fences complement, but do not replace, live Folia/client evidence."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
WEAVER = ROOT / "src/main/java/hu/taliann/icesmp/dev/weaver"


def violations(source: str, frontend: bool = False, provider: bool = False) -> list[str]:
    errors = []
    if re.search(r"Bukkit\s*\.\s*getScheduler\s*\(", source):
        errors.append("legacy scheduler")
    if re.search(r"\.\s*join\s*\(\s*\)|toCompletableFuture\s*\(\s*\)\s*\.\s*get\s*\(", source):
        errors.append("blocking future")
    if re.search(r"java\.lang\.reflect|Class\.forName|getDeclaredField|getDeclaredMethod", source):
        errors.append("reflection")
    if frontend and re.search(r"import\s+hu\.taliann\.icesmp\.dev\.weaver\.provider\.", source):
        errors.append("frontend depends on named adapter")
    if frontend and re.search(r"(?:if|switch)\s*\([^\n]*(?:providerId|\.providerId\(\))[^\n]*(?:equals|case)", source):
        errors.append("provider identity branching")
    if provider and re.search(r"import\s+hu\.taliann\.icesmp\.dev\.weaver\.gui\.", source):
        errors.append("provider owns frontend")
    return errors


class WorldWeaverArchitectureTest(unittest.TestCase):
    def test_native_owner_routes_and_generic_frontend(self):
        for path in WEAVER.rglob("*.java"):
            frontend = "gui" in path.parts or path.name in {"WorldWeaverKernel.java", "WorldWeaverRuntime.java"}
            self.assertEqual([], violations(path.read_text(), frontend, "provider" in path.parts), str(path.relative_to(ROOT)))

    def test_guards_reject_concrete_regressions(self):
        for source in ["Bukkit.getScheduler().runTask(plugin, task);", "future.join();",
                       "future.toCompletableFuture().get();", "Class.forName(name);",
                       "import hu.taliann.icesmp.dev.weaver.provider.PvEWeaverProvider;",
                       'if (providerId.equals("pve")) { specialGui(); }']:
            self.assertTrue(violations(source, frontend=True), source)
        self.assertTrue(violations("import hu.taliann.icesmp.dev.weaver.gui.WorldWeaverGUI;", provider=True))

    def test_no_public_feature_or_permission_documentation(self):
        paths = [ROOT / "README.md"] + [ROOT / "docs" / name for name in
                ["FEATURES.md", "LATEST_CHANGES.md", "PLAYER_GUIDE.md", "BUILDER_GUIDE.md", "ADMIN_GUIDE.md", "RESOURCE_PACK_CMD.md"]]
        for path in paths:
            self.assertNotRegex(path.read_text(), r"(?i)world[ _-]?weaver|világszövő|dev_world_weaver", str(path.relative_to(ROOT)))

    def test_native_handles_are_not_persistent_or_subject_fields(self):
        live = re.compile(r"\b(?:Player|Entity|World|Location|Block|ItemStack|Inventory)\b")
        for package in [WEAVER / "persistence", WEAVER / "subject"]:
            for path in package.glob("*.java"):
                source = path.read_text()
                for declaration in re.findall(r"(?:private|protected|public)\s+(?:final\s+|volatile\s+)*[^;{}]+;", source):
                    if "(" not in declaration and live.search(declaration):
                        self.fail(f"Live handle field in {path.name}: {declaration}")


if __name__ == "__main__":
    unittest.main()
