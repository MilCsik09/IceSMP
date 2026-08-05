#!/usr/bin/env python3
"""Register full-authority PlayerProfile regressions in Gradle check."""
from pathlib import Path

path = Path(__file__).resolve().parents[1] / "build.gradle.kts"
text = path.read_text(encoding="utf-8")
anchor = '''val playerProfileYamlRegressionTest by tasks.registering(JavaExec::class) {
'''
blocks = (
'''val playerProfileSectionExtensionsRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs immutable extension-copy regressions across every PlayerProfile section."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.playerprofile.domain.PlayerProfileSectionExtensionsRegressionSuite")
}

''',
'''val spellMasteryTransactionRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs exact-once spell mastery wallet/receipt recovery regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.managers.SpellMasteryTransactionRegressionSuite")
}

''',
'''val professionProfileStateRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs PlayerProfile profession slot, XP, level and recipe authority regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.playerprofile.domain.ProfessionProfileStateRegressionSuite")
}

''',
)
for block in blocks:
    if block in text:
        continue
    if text.count(anchor) != 1:
        raise SystemExit("PlayerProfile YAML task anchor not found exactly once")
    text = text.replace(anchor, block + anchor, 1)

old_variants = (
'''        classSpecLifecycleRegressionTest, playerProfileDomainRegressionTest,
        playerProfileSectionExtensionsRegressionTest, spellMasteryTransactionRegressionTest,
        playerProfileYamlRegressionTest, playerProfileTransactionRegressionTest, playerProfileApiRegressionTest,
''',
'''        classSpecLifecycleRegressionTest, playerProfileDomainRegressionTest,
        playerProfileSectionExtensionsRegressionTest, playerProfileYamlRegressionTest,
        playerProfileTransactionRegressionTest, playerProfileApiRegressionTest,
''',
'''        classSpecLifecycleRegressionTest, playerProfileDomainRegressionTest,
        playerProfileYamlRegressionTest, playerProfileTransactionRegressionTest, playerProfileApiRegressionTest,
''',
)
new = '''        classSpecLifecycleRegressionTest, playerProfileDomainRegressionTest,
        playerProfileSectionExtensionsRegressionTest, spellMasteryTransactionRegressionTest,
        professionProfileStateRegressionTest, playerProfileYamlRegressionTest,
        playerProfileTransactionRegressionTest, playerProfileApiRegressionTest,
'''
if new not in text:
    matches = [old for old in old_variants if text.count(old) == 1]
    if len(matches) != 1:
        raise SystemExit("Gradle check dependency anchor not found exactly once")
    text = text.replace(matches[0], new, 1)
path.write_text(text, encoding="utf-8")
print("PlayerProfile full-authority regression tasks registered.")
